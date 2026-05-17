namespace Sererr;

/// <summary>
/// A captured error: type, message, frames, mechanism, plus inlined
/// event-level metadata (release / server_name).
///
/// Cause chains are a flat <see cref="IReadOnlyList{T}"/> of
/// <see cref="CapturedError"/> on the enclosing collection
/// (most-causal-first; the originating caught error is the LAST
/// element — matches Sentry's <c>exception.values</c> convention).
/// </summary>
public sealed record CapturedError
{
    /// <summary>Error class/type name.</summary>
    public string Type { get; init; } = "";

    /// <summary>Short rendering of the error itself, NOT including the chain.</summary>
    public string Message { get; init; } = "";

    /// <summary>Frames, most-recent-call-first.</summary>
    public IReadOnlyList<StackFrame> Frames { get; init; } = Array.Empty<StackFrame>();

    /// <summary>Mechanism describing how the exception was captured.</summary>
    public ExceptionMechanism? Mechanism { get; init; }

    /// <summary>Build identifier (e.g. semver or git SHA).</summary>
    public string Release { get; init; } = "";

    /// <summary>Producing host / pod name.</summary>
    public string ServerName { get; init; } = "";

    public bool Equals(CapturedError? other)
    {
        if (ReferenceEquals(this, other)) return true;
        if (other is null) return false;
        return Type == other.Type
            && Message == other.Message
            && Frames.SequenceEqual(other.Frames)
            && Equals(Mechanism, other.Mechanism)
            && Release == other.Release
            && ServerName == other.ServerName;
    }

    public override int GetHashCode()
    {
        var hc = new HashCode();
        hc.Add(Type);
        hc.Add(Message);
        foreach (var f in Frames) hc.Add(f);
        hc.Add(Mechanism);
        hc.Add(Release);
        hc.Add(ServerName);
        return hc.ToHashCode();
    }
}
