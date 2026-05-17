using System.Globalization;
using System.Text;

namespace Sererr;

/// <summary>
/// Renders a captured chain into the <see cref="DebugInfo"/> shape
/// (wire-compatible with <c>google.rpc.DebugInfo</c>).
///
/// Output format matches the Rust reference implementation byte-for-byte:
/// <list type="bullet">
///   <item><c>StackEntries</c>: one <c>"  at &lt;fn&gt; (&lt;file&gt;:&lt;line&gt;)"</c>
///     line per frame, most-recent-first, separated by
///     <c>"Caused by: &lt;type&gt;: &lt;message&gt;"</c> headers.</item>
///   <item><c>Detail</c>: joined <c>"&lt;type&gt;: &lt;message&gt;"</c>
///     across the chain with <c>"\nCaused by: "</c> separators.</item>
/// </list>
/// </summary>
public static class DebugInfoAdapter
{
    /// <summary>Build a <see cref="DebugInfo"/> from a captured chain.</summary>
    public static DebugInfo ToDebugInfo(IReadOnlyList<CapturedError> chain)
    {
        if (chain.Count == 0)
        {
            return new DebugInfo();
        }

        var stackEntries = new List<string>();
        var detailParts = new List<string>();

        // chain is stored most-causal-first. The user-facing rendering
        // is most-recent-first — walk in reverse.
        for (var revIdx = 0; revIdx < chain.Count; revIdx++)
        {
            var entry = chain[chain.Count - 1 - revIdx];
            var header = $"{entry.Type}: {entry.Message}";
            detailParts.Add(revIdx == 0 ? header : $"Caused by: {header}");

            stackEntries.Add(revIdx == 0 ? header : $"Caused by: {header}");

            foreach (var frame in entry.Frames)
            {
                stackEntries.Add(FormatFrameLine(frame));
            }
        }

        return new DebugInfo
        {
            StackEntries = stackEntries,
            Detail = string.Join("\n", detailParts),
        };
    }

    private static string FormatFrameLine(StackFrame frame)
    {
        string location;
        if (string.IsNullOrEmpty(frame.File))
        {
            location = "";
        }
        else if (frame.Line > 0)
        {
            location = $" ({frame.File}:{frame.Line.ToString(CultureInfo.InvariantCulture)})";
        }
        else
        {
            location = $" ({frame.File})";
        }

        var function = string.IsNullOrEmpty(frame.Function) ? "<unknown>" : frame.Function;
        return $"  at {function}{location}";
    }
}
