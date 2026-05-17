namespace Sererr;

/// <summary>
/// Describes how an exception was captured / handled. Mirrors Sentry's
/// <c>mechanism</c> object on the exception interface.
///
/// Field-compatible with <c>sererr.v1.ExceptionMechanism</c>. Carries
/// the cross-entry chain linkage (<see cref="ExceptionId"/>,
/// <see cref="ParentId"/>) Sentry uses for flat <c>exception.values</c>
/// arrays.
/// </summary>
public sealed record ExceptionMechanism
{
    /// <summary>Required: mechanism category — e.g. <c>"generic"</c>.</summary>
    public string Type { get; init; } = "";

    /// <summary>Human-readable description.</summary>
    public string Description { get; init; } = "";

    /// <summary>True when the exception was caught by user code.</summary>
    public bool Handled { get; init; }

    /// <summary>True if synthesized for context rather than raised by a real failure.</summary>
    public bool Synthetic { get; init; }

    /// <summary>Link to docs explaining this mechanism.</summary>
    public string HelpLink { get; init; } = "";

    /// <summary>What attached the mechanism.</summary>
    public string Source { get; init; } = "";

    /// <summary>Identifier within the enclosing chain.</summary>
    public uint ExceptionId { get; init; }

    /// <summary>Identifier of the causal parent. 0 = root cause.</summary>
    public uint ParentId { get; init; }

    /// <summary>True if this is an aggregate/exception-group container.</summary>
    public bool IsExceptionGroup { get; init; }

    /// <summary>
    /// Mechanism-specific metadata. Stored in a
    /// <see cref="SortedDictionary{TKey,TValue}"/> so encoded bytes are
    /// deterministic — sorted keys produce a canonical wire encoding
    /// that cross-language consumers can byte-compare for conformance.
    /// </summary>
    public SortedDictionary<string, string> Data { get; init; } =
        new(StringComparer.Ordinal);

    public bool Equals(ExceptionMechanism? other)
    {
        if (ReferenceEquals(this, other)) return true;
        if (other is null) return false;
        return Type == other.Type
            && Description == other.Description
            && Handled == other.Handled
            && Synthetic == other.Synthetic
            && HelpLink == other.HelpLink
            && Source == other.Source
            && ExceptionId == other.ExceptionId
            && ParentId == other.ParentId
            && IsExceptionGroup == other.IsExceptionGroup
            && Data.SequenceEqual(other.Data);
    }

    public override int GetHashCode()
    {
        var hc = new HashCode();
        hc.Add(Type);
        hc.Add(Description);
        hc.Add(Handled);
        hc.Add(Synthetic);
        hc.Add(HelpLink);
        hc.Add(Source);
        hc.Add(ExceptionId);
        hc.Add(ParentId);
        hc.Add(IsExceptionGroup);
        foreach (var kv in Data)
        {
            hc.Add(kv.Key);
            hc.Add(kv.Value);
        }
        return hc.ToHashCode();
    }
}
