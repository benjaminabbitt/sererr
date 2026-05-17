// Pins the shape of the plain types exposed at the `Sererr` namespace
// root: record semantics, sensible defaults, and required fields per
// the sererr.v1 proto schema. These types are the in-process API
// surface; the proto/wire layer adapts to them.

using FluentAssertions;
using Sererr;
using Xunit;

namespace Sererr.Tests;

public class TypesTest
{
    [Fact]
    // StackFrame must be constructable with no required arguments, and
    // its zero-value defaults must match the proto3 "0 = unknown"
    // contract: empty strings, empty lists, false flags.
    public void StackFrame_HasDefaults()
    {
        var f = new StackFrame();
        f.Function.Should().BeEmpty();
        f.Module.Should().BeEmpty();
        f.Package.Should().BeEmpty();
        f.File.Should().BeEmpty();
        f.AbsPath.Should().BeEmpty();
        f.Line.Should().Be(0u);
        f.ContextLine.Should().BeEmpty();
        f.PreContext.Should().BeEmpty();
        f.PostContext.Should().BeEmpty();
        f.SourceLink.Should().BeEmpty();
        f.InApp.Should().BeFalse();
    }

    [Fact]
    // Records implement value equality automatically. Two StackFrames
    // with the same field values must be equal.
    public void StackFrame_ValueEquality()
    {
        var a = new StackFrame { Function = "f", Line = 1u };
        var b = new StackFrame { Function = "f", Line = 1u };
        a.Should().Be(b);
    }

    [Fact]
    // ExceptionMechanism.Data uses SortedDictionary so the proto-encoded
    // map bytes are deterministic across runs and languages (proto's
    // map<string,string> sorts by key on encode).
    public void ExceptionMechanism_DataIsSortedDictionary()
    {
        var m = new ExceptionMechanism();
        m.Data.Should().BeOfType<SortedDictionary<string, string>>();
    }

    [Fact]
    // CapturedError.Mechanism is nullable (absent = no mechanism).
    public void CapturedError_MechanismIsNullable()
    {
        var c = new CapturedError();
        c.Mechanism.Should().BeNull();
    }

    [Fact]
    // DebugInfo's two-field shape mirrors google.rpc.DebugInfo.
    public void DebugInfo_HasDefaults()
    {
        var di = new DebugInfo();
        di.StackEntries.Should().BeEmpty();
        di.Detail.Should().BeEmpty();
    }
}
