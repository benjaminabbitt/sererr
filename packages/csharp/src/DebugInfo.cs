namespace Sererr;

/// <summary>
/// Adapter shape for the gRPC error model (<c>google.rpc.DebugInfo</c>).
/// Plain C# type, wire-compatible with the upstream
/// <c>google.rpc.DebugInfo</c> proto. Produced by
/// <see cref="DebugInfoAdapter.ToDebugInfo(IReadOnlyList{CapturedError})"/>.
/// </summary>
public sealed record DebugInfo
{
    /// <summary>
    /// Stack-trace entries, one line per frame, most-recent-call-first
    /// across the entire chain (separated by "Caused by:" lines).
    /// </summary>
    public IReadOnlyList<string> StackEntries { get; init; } = Array.Empty<string>();

    /// <summary>Joined "&lt;type&gt;: &lt;message&gt;" rendering of the chain.</summary>
    public string Detail { get; init; } = "";

    public bool Equals(DebugInfo? other)
    {
        if (ReferenceEquals(this, other)) return true;
        if (other is null) return false;
        return StackEntries.SequenceEqual(other.StackEntries) && Detail == other.Detail;
    }

    public override int GetHashCode()
    {
        var hc = new HashCode();
        foreach (var s in StackEntries) hc.Add(s);
        hc.Add(Detail);
        return hc.ToHashCode();
    }
}
