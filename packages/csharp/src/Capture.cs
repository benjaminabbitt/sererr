using System.Diagnostics;
using System.Reflection;

namespace Sererr;

/// <summary>
/// Top-level entry point: walks an <see cref="Exception"/> into a
/// <see cref="CapturedError"/> chain.
///
/// The returned list is <b>most-causal-first</b> — the originating
/// caught error is the LAST element. Each entry carries the same
/// frames captured from the supplied exception; chain position is
/// identified by <c>mechanism.exception_id</c>.
/// </summary>
public static class Capture
{
    /// <summary>
    /// Walk an <see cref="Exception"/> into a sererr capture chain.
    /// </summary>
    /// <param name="exception">The caught exception.</param>
    /// <param name="typeName">User-supplied label for the outermost
    /// (caught) exception; subsequent entries use their runtime type's
    /// fully-qualified name.</param>
    /// <param name="release">Build identifier (semver / git SHA).</param>
    /// <param name="serverName">Producing host / pod name.</param>
    public static IReadOnlyList<CapturedError> Build(
        Exception exception,
        string typeName,
        string release,
        string serverName)
    {
        // Capture frames from the leaf exception's stack trace. .NET's
        // StackTrace(exception, fNeedFileInfo: true) returns frames
        // most-recent-call-first; no reversal needed.
        var frames = ExtractFrames(exception);

        // Walk the exception tree into a flat most-causal-first list.
        // For each entry we record its (post-reverse) parent index in
        // the proto sense:
        //   - linear chain: parent = i-1 (the inner cause)
        //   - aggregate siblings: parent = the aggregate's index
        //
        // We accumulate raw entries first, then reverse to get
        // most-causal-first ordering, and stamp parent_id in a second
        // pass using the simple `i-1` formula except where the original
        // walker recorded a sibling-of-aggregate relationship.
        var raw = new List<RawEntry>();
        WalkOutermostFirst(exception, typeName, raw, aggregateParent: -1);

        // raw is outermost-first; reverse to most-causal-first.
        raw.Reverse();
        var n = raw.Count;
        // Remap the recorded aggregateParent (a pre-reverse index) to a
        // post-reverse index. -1 stays -1.
        for (var i = 0; i < n; i++)
        {
            var pre = raw[i].AggregateParentPreReverse;
            raw[i].AggregateParentPostReverse = pre < 0 ? -1 : (n - 1 - pre);
        }

        var chain = new List<CapturedError>(n);
        for (var i = 0; i < n; i++)
        {
            var entry = raw[i];
            // parent_id rules in most-causal-first order:
            //   1. Aggregate sibling → parent = aggregate's index.
            //   2. Aggregate itself → parent = 0 (no single causal parent
            //      across multiple children).
            //   3. Linear chain → parent = i-1 (the inner cause we wrap),
            //      clamped to 0 at the root.
            uint parentId;
            if (entry.AggregateParentPostReverse >= 0)
            {
                parentId = (uint)entry.AggregateParentPostReverse;
            }
            else if (entry.IsExceptionGroup)
            {
                parentId = 0u;
            }
            else
            {
                parentId = i == 0 ? 0u : (uint)(i - 1);
            }

            chain.Add(new CapturedError
            {
                Type = entry.TypeName,
                Message = entry.Message,
                Frames = frames,
                Mechanism = new ExceptionMechanism
                {
                    Type = "generic",
                    Handled = true,
                    ExceptionId = (uint)i,
                    ParentId = parentId,
                    IsExceptionGroup = entry.IsExceptionGroup,
                },
                Release = release,
                ServerName = serverName,
            });
        }

        return chain;
    }

    // ---- Internal walker --------------------------------------------------

    private sealed class RawEntry
    {
        public required string TypeName { get; init; }
        public required string Message { get; init; }
        public bool IsExceptionGroup { get; init; }
        /// <summary>
        /// Pre-reverse index of the aggregate parent (if this entry is
        /// a sibling under an AggregateException); -1 otherwise.
        /// </summary>
        public int AggregateParentPreReverse { get; init; } = -1;
        public int AggregateParentPostReverse { get; set; } = -1;
    }

    /// <summary>
    /// Walk <paramref name="ex"/> into <paramref name="nodes"/>,
    /// outermost first. Aggregate children record their aggregate's
    /// pre-reverse index so we can stamp parent_id later; linear chain
    /// links are reconstructed positionally.
    /// </summary>
    private static void WalkOutermostFirst(
        Exception ex,
        string? typeName,
        List<RawEntry> nodes,
        int aggregateParent)
    {
        var isGroup = ex is AggregateException;
        nodes.Add(new RawEntry
        {
            TypeName = typeName ?? ex.GetType().FullName ?? ex.GetType().Name,
            Message = ex.Message,
            IsExceptionGroup = isGroup,
            AggregateParentPreReverse = aggregateParent,
        });
        var myIdx = nodes.Count - 1;

        if (ex is AggregateException agg)
        {
            // Aggregate siblings record THIS aggregate as their parent.
            // The aggregate itself uses default linear parenting (or
            // -1 if it has none).
            foreach (var inner in agg.InnerExceptions)
            {
                WalkOutermostFirst(inner, typeName: null, nodes, aggregateParent: myIdx);
            }
        }
        else if (ex.InnerException is { } inner)
        {
            // Linear chain — no aggregate context.
            WalkOutermostFirst(inner, typeName: null, nodes, aggregateParent: -1);
        }
    }

    // ---- Frame extraction --------------------------------------------------

    private static IReadOnlyList<StackFrame> ExtractFrames(Exception exception)
    {
        // Ben.Demystifier cleans up async state-machine MoveNext frames
        // and similar compiler detritus. The Demystify() call mutates
        // exception state that affects later ToString() rendering and
        // can also trim the StackTrace output. Tolerate failure — it
        // must never break capture.
        try
        {
            exception.Demystify();
        }
        catch
        {
        }

        var trace = new StackTrace(exception, fNeedFileInfo: true);
        var raw = trace.GetFrames();
        if (raw is null || raw.Length == 0)
        {
            return Array.Empty<StackFrame>();
        }

        var frames = new List<StackFrame>(raw.Length);
        foreach (var sf in raw)
        {
            if (sf is null) continue;
            var method = sf.GetMethod();
            var function = FormatMethod(method);
            var module = method?.DeclaringType?.FullName ?? "";
            var package = method?.DeclaringType?.Assembly.GetName().Name ?? "";
            var file = sf.GetFileName() ?? "";
            var lineNumber = sf.GetFileLineNumber();
            var line = lineNumber > 0 ? (uint)lineNumber : 0u;

            frames.Add(new StackFrame
            {
                Function = function,
                Module = module,
                Package = package,
                File = file,
                AbsPath = file,
                Line = line,
                InApp = IsAppFrame(module, package),
            });
        }
        return frames;
    }

    private static string FormatMethod(MethodBase? method)
    {
        if (method is null) return "<unknown>";
        var declaring = method.DeclaringType;
        var typeName = declaring?.FullName ?? declaring?.Name ?? "";
        return string.IsNullOrEmpty(typeName)
            ? method.Name
            : $"{typeName}.{method.Name}";
    }

    /// <summary>
    /// Default <c>in_app</c> heuristic. Frames whose module/package
    /// looks like .NET runtime / framework code are flagged false.
    /// </summary>
    private static bool IsAppFrame(string module, string package)
    {
        if (string.IsNullOrEmpty(module) && string.IsNullOrEmpty(package))
        {
            return false;
        }
        ReadOnlySpan<string> nonAppPrefixes = new[]
        {
            "System.",
            "Microsoft.",
            "Internal.",
        };
        foreach (var prefix in nonAppPrefixes)
        {
            if (module.StartsWith(prefix, StringComparison.Ordinal)) return false;
            var trimmed = prefix.TrimEnd('.');
            if (package.StartsWith(trimmed, StringComparison.Ordinal)) return false;
        }
        return true;
    }
}
