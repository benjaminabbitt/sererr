using V1 = Sererr.V1;

namespace Sererr.Proto;

/// <summary>
/// Lossless conversions between the plain types in <see cref="Sererr"/>
/// and the proto-generated types in <c>Sererr.V1</c>.
///
/// The proto encoding produced via these conversions is byte-equivalent
/// to every other sererr language implementation for the committed
/// conformance fixtures.
/// </summary>
public static class ProtoAdapter
{
    // ---------- plain → proto ----------

    /// <summary>Converts a plain <see cref="StackFrame"/> to its proto twin.</summary>
    public static V1.StackFrame ToProto(StackFrame f)
    {
        var pf = new V1.StackFrame
        {
            Function = f.Function,
            Module = f.Module,
            Package = f.Package,
            File = f.File,
            AbsPath = f.AbsPath,
            Line = f.Line,
            ContextLine = f.ContextLine,
            SourceLink = f.SourceLink,
            InApp = f.InApp,
        };
        foreach (var s in f.PreContext) pf.PreContext.Add(s);
        foreach (var s in f.PostContext) pf.PostContext.Add(s);
        return pf;
    }

    /// <summary>Converts a plain <see cref="ExceptionMechanism"/> to its proto twin.</summary>
    public static V1.ExceptionMechanism ToProto(ExceptionMechanism m)
    {
        var pm = new V1.ExceptionMechanism
        {
            Type = m.Type,
            Description = m.Description,
            Handled = m.Handled,
            Synthetic = m.Synthetic,
            HelpLink = m.HelpLink,
            Source = m.Source,
            ExceptionId = m.ExceptionId,
            ParentId = m.ParentId,
            IsExceptionGroup = m.IsExceptionGroup,
        };
        // SortedDictionary already iterates keys in sorted order, but
        // be explicit so a hostile consumer subclass can't perturb
        // byte-equivalence.
        foreach (var (k, v) in m.Data.OrderBy(kv => kv.Key, StringComparer.Ordinal))
        {
            pm.Data.Add(k, v);
        }
        return pm;
    }

    /// <summary>Converts a plain <see cref="CapturedError"/> to its proto twin.</summary>
    public static V1.CapturedError ToProto(CapturedError c)
    {
        var pc = new V1.CapturedError
        {
            Type = c.Type,
            Message = c.Message,
            Release = c.Release,
            ServerName = c.ServerName,
        };
        foreach (var f in c.Frames) pc.Frames.Add(ToProto(f));
        if (c.Mechanism is not null) pc.Mechanism = ToProto(c.Mechanism);
        return pc;
    }

    /// <summary>Converts a plain <see cref="DebugInfo"/> to its proto twin
    /// (<c>Google.Rpc.DebugInfo</c>).</summary>
    public static Google.Rpc.DebugInfo ToProto(DebugInfo di)
    {
        var p = new Google.Rpc.DebugInfo
        {
            Detail = di.Detail,
        };
        foreach (var s in di.StackEntries) p.StackEntries.Add(s);
        return p;
    }

    // ---------- proto → plain ----------

    /// <summary>Converts a proto <c>StackFrame</c> to the plain type.</summary>
    public static StackFrame FromProto(V1.StackFrame f) => new()
    {
        Function = f.Function,
        Module = f.Module,
        Package = f.Package,
        File = f.File,
        AbsPath = f.AbsPath,
        Line = f.Line,
        ContextLine = f.ContextLine,
        PreContext = f.PreContext.ToList(),
        PostContext = f.PostContext.ToList(),
        SourceLink = f.SourceLink,
        InApp = f.InApp,
    };

    /// <summary>Converts a proto <c>ExceptionMechanism</c> to the plain type.</summary>
    public static ExceptionMechanism FromProto(V1.ExceptionMechanism m)
    {
        var data = new SortedDictionary<string, string>(StringComparer.Ordinal);
        foreach (var kv in m.Data) data[kv.Key] = kv.Value;
        return new ExceptionMechanism
        {
            Type = m.Type,
            Description = m.Description,
            Handled = m.Handled,
            Synthetic = m.Synthetic,
            HelpLink = m.HelpLink,
            Source = m.Source,
            ExceptionId = m.ExceptionId,
            ParentId = m.ParentId,
            IsExceptionGroup = m.IsExceptionGroup,
            Data = data,
        };
    }

    /// <summary>Converts a proto <c>CapturedError</c> to the plain type.</summary>
    public static CapturedError FromProto(V1.CapturedError c) => new()
    {
        Type = c.Type,
        Message = c.Message,
        Frames = c.Frames.Select(FromProto).ToList(),
        Mechanism = c.Mechanism is null ? null : FromProto(c.Mechanism),
        Release = c.Release,
        ServerName = c.ServerName,
    };

    /// <summary>Converts a proto <c>Google.Rpc.DebugInfo</c> to the plain type.</summary>
    public static DebugInfo FromProto(Google.Rpc.DebugInfo di) => new()
    {
        StackEntries = di.StackEntries.ToList(),
        Detail = di.Detail,
    };
}
