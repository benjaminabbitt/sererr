namespace Sererr;

/// <summary>
/// A single frame in a captured stack trace.
///
/// Field-compatible with the <c>sererr.v1.StackFrame</c> proto. All
/// fields use proto3 "zero value = unknown" semantics — defaults are
/// empty strings, empty lists, line=0, in_app=false.
/// </summary>
/// <remarks>
/// Property names use PascalCase per .NET convention; they map to the
/// proto's snake_case fields via the proto adapter.
/// </remarks>
public sealed record StackFrame
{
    /// <summary>Demangled function/method name.</summary>
    public string Function { get; init; } = "";

    /// <summary>Containing module / class / namespace.</summary>
    public string Module { get; init; } = "";

    /// <summary>Native library / crate / package name.</summary>
    public string Package { get; init; } = "";

    /// <summary>Source file (Sentry: <c>filename</c>).</summary>
    public string File { get; init; } = "";

    /// <summary>Absolute path on the build machine.</summary>
    public string AbsPath { get; init; } = "";

    /// <summary>1-based line number; 0 = unknown.</summary>
    public uint Line { get; init; }

    /// <summary>The exact source line at <see cref="Line"/>.</summary>
    public string ContextLine { get; init; } = "";

    /// <summary>Source lines preceding <see cref="Line"/>.</summary>
    public IReadOnlyList<string> PreContext { get; init; } = Array.Empty<string>();

    /// <summary>Source lines following <see cref="Line"/>.</summary>
    public IReadOnlyList<string> PostContext { get; init; } = Array.Empty<string>();

    /// <summary>Deep link to a source viewer for this frame.</summary>
    public string SourceLink { get; init; } = "";

    /// <summary>Hint to UIs: false for framework/stdlib frames.</summary>
    public bool InApp { get; init; }

    // Records' built-in Equals walks reference equality on collection
    // properties, which is wrong for IReadOnlyList. Override to use
    // sequence equality so two frames with equal field values compare
    // equal regardless of which list instance backs them.
    public bool Equals(StackFrame? other)
    {
        if (ReferenceEquals(this, other)) return true;
        if (other is null) return false;
        return Function == other.Function
            && Module == other.Module
            && Package == other.Package
            && File == other.File
            && AbsPath == other.AbsPath
            && Line == other.Line
            && ContextLine == other.ContextLine
            && PreContext.SequenceEqual(other.PreContext)
            && PostContext.SequenceEqual(other.PostContext)
            && SourceLink == other.SourceLink
            && InApp == other.InApp;
    }

    public override int GetHashCode()
    {
        var hc = new HashCode();
        hc.Add(Function);
        hc.Add(Module);
        hc.Add(Package);
        hc.Add(File);
        hc.Add(AbsPath);
        hc.Add(Line);
        hc.Add(ContextLine);
        foreach (var s in PreContext) hc.Add(s);
        foreach (var s in PostContext) hc.Add(s);
        hc.Add(SourceLink);
        hc.Add(InApp);
        return hc.ToHashCode();
    }
}
