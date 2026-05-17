// Mutation-kill tests for the record-typed value objects' Equals/GetHashCode
// implementations. Records auto-generate equality, but StackFrame,
// ExceptionMechanism, CapturedError, and DebugInfo override Equals to use
// SequenceEqual on collection properties. Stryker mutates:
//   - the ReferenceEquals fast-path (Negate expression)
//   - every && in the chained field comparison (Logical -> ||)
//   - the field defaults on record properties (String -> "Stryker was here!")
//
// To kill these, for EACH field we construct two records differing only in
// that field and assert inequality. We also pin ReferenceEquals(this, this)
// = true and the per-field defaults.

using FluentAssertions;
using Sererr;
using Xunit;

namespace Sererr.Tests;

public class RecordEqualityTest
{
    // ---------- StackFrame ----------

    [Fact]
    // Why: kills `ReferenceEquals(this, other)` negation on StackFrame.Equals.
    public void StackFrame_ReferenceEqualsItself()
    {
        var a = new StackFrame { Function = "f" };
        a.Equals(a).Should().BeTrue();
    }

    [Fact]
    // Why: kills the null-check branch on StackFrame.Equals.
    public void StackFrame_NotEqualToNull()
    {
        var a = new StackFrame { Function = "f" };
        a.Equals(null).Should().BeFalse();
    }

    [Fact]
    // Why: equal-field StackFrames must compare equal (both Equals and ==).
    public void StackFrame_EqualFieldsAreEqual()
    {
        var a = new StackFrame
        {
            Function = "f", Module = "m", Package = "p", File = "x.cs",
            AbsPath = "/a/x.cs", Line = 12, ContextLine = "ctx",
            PreContext = new[] { "pre1", "pre2" },
            PostContext = new[] { "post1" },
            SourceLink = "link", InApp = true,
        };
        var b = new StackFrame
        {
            Function = "f", Module = "m", Package = "p", File = "x.cs",
            AbsPath = "/a/x.cs", Line = 12, ContextLine = "ctx",
            PreContext = new[] { "pre1", "pre2" },
            PostContext = new[] { "post1" },
            SourceLink = "link", InApp = true,
        };
        a.Equals(b).Should().BeTrue();
        a.GetHashCode().Should().Be(b.GetHashCode());
    }

    [Fact]
    // Why: kills logical-mutation on `Function == other.Function && ...`.
    public void StackFrame_DifferentFunction_NotEqual()
    {
        var a = new StackFrame { Function = "f1", Module = "m" };
        var b = new StackFrame { Function = "f2", Module = "m" };
        a.Equals(b).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on Module.
    public void StackFrame_DifferentModule_NotEqual()
    {
        var a = new StackFrame { Module = "m1" };
        var b = new StackFrame { Module = "m2" };
        a.Equals(b).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on Package.
    public void StackFrame_DifferentPackage_NotEqual()
    {
        var a = new StackFrame { Package = "p1" };
        var b = new StackFrame { Package = "p2" };
        a.Equals(b).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on File.
    public void StackFrame_DifferentFile_NotEqual()
    {
        var a = new StackFrame { File = "a.cs" };
        var b = new StackFrame { File = "b.cs" };
        a.Equals(b).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on AbsPath.
    public void StackFrame_DifferentAbsPath_NotEqual()
    {
        var a = new StackFrame { AbsPath = "/a" };
        var b = new StackFrame { AbsPath = "/b" };
        a.Equals(b).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on Line.
    public void StackFrame_DifferentLine_NotEqual()
    {
        var a = new StackFrame { Line = 1 };
        var b = new StackFrame { Line = 2 };
        a.Equals(b).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on ContextLine.
    public void StackFrame_DifferentContextLine_NotEqual()
    {
        var a = new StackFrame { ContextLine = "a" };
        var b = new StackFrame { ContextLine = "b" };
        a.Equals(b).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on PreContext SequenceEqual.
    public void StackFrame_DifferentPreContext_NotEqual()
    {
        var a = new StackFrame { PreContext = new[] { "x" } };
        var b = new StackFrame { PreContext = new[] { "y" } };
        a.Equals(b).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on PostContext SequenceEqual.
    public void StackFrame_DifferentPostContext_NotEqual()
    {
        var a = new StackFrame { PostContext = new[] { "x" } };
        var b = new StackFrame { PostContext = new[] { "y" } };
        a.Equals(b).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on SourceLink.
    public void StackFrame_DifferentSourceLink_NotEqual()
    {
        var a = new StackFrame { SourceLink = "a" };
        var b = new StackFrame { SourceLink = "b" };
        a.Equals(b).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on InApp (the final &&).
    public void StackFrame_DifferentInApp_NotEqual()
    {
        var a = new StackFrame { InApp = true };
        var b = new StackFrame { InApp = false };
        a.Equals(b).Should().BeFalse();
    }

    // ---------- ExceptionMechanism ----------

    [Fact]
    // Why: kills `ReferenceEquals(this, other)` negation on ExceptionMechanism.
    public void ExceptionMechanism_ReferenceEqualsItself()
    {
        var a = new ExceptionMechanism { Type = "generic" };
        a.Equals(a).Should().BeTrue();
    }

    [Fact]
    // Why: kills null-check on ExceptionMechanism.Equals.
    public void ExceptionMechanism_NotEqualToNull()
    {
        new ExceptionMechanism().Equals(null).Should().BeFalse();
    }

    [Fact]
    // Why: pins ExceptionMechanism.Type default to "" (kills String mutation).
    public void ExceptionMechanism_DefaultType_IsEmptyString()
    {
        new ExceptionMechanism().Type.Should().Be("");
    }

    [Fact]
    // Why: pins ExceptionMechanism.Description default to "".
    public void ExceptionMechanism_DefaultDescription_IsEmptyString()
    {
        new ExceptionMechanism().Description.Should().Be("");
    }

    [Fact]
    // Why: pins ExceptionMechanism.HelpLink default to "".
    public void ExceptionMechanism_DefaultHelpLink_IsEmptyString()
    {
        new ExceptionMechanism().HelpLink.Should().Be("");
    }

    [Fact]
    // Why: pins ExceptionMechanism.Source default to "".
    public void ExceptionMechanism_DefaultSource_IsEmptyString()
    {
        new ExceptionMechanism().Source.Should().Be("");
    }

    [Fact]
    // Why: equal-field ExceptionMechanisms must compare equal.
    public void ExceptionMechanism_EqualFieldsAreEqual()
    {
        var data = new SortedDictionary<string, string>(StringComparer.Ordinal)
        {
            ["k1"] = "v1", ["k2"] = "v2",
        };
        var a = new ExceptionMechanism
        {
            Type = "generic", Description = "d", Handled = true, Synthetic = true,
            HelpLink = "h", Source = "s", ExceptionId = 1u, ParentId = 0u,
            IsExceptionGroup = true, Data = data,
        };
        var b = new ExceptionMechanism
        {
            Type = "generic", Description = "d", Handled = true, Synthetic = true,
            HelpLink = "h", Source = "s", ExceptionId = 1u, ParentId = 0u,
            IsExceptionGroup = true,
            Data = new SortedDictionary<string, string>(StringComparer.Ordinal)
            {
                ["k1"] = "v1", ["k2"] = "v2",
            },
        };
        a.Equals(b).Should().BeTrue();
        a.GetHashCode().Should().Be(b.GetHashCode());
    }

    [Fact]
    // Why: kills logical-mutation on Type field.
    public void ExceptionMechanism_DifferentType_NotEqual()
    {
        new ExceptionMechanism { Type = "a" }
            .Equals(new ExceptionMechanism { Type = "b" }).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on Description.
    public void ExceptionMechanism_DifferentDescription_NotEqual()
    {
        new ExceptionMechanism { Description = "a" }
            .Equals(new ExceptionMechanism { Description = "b" }).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on Handled.
    public void ExceptionMechanism_DifferentHandled_NotEqual()
    {
        new ExceptionMechanism { Handled = true }
            .Equals(new ExceptionMechanism { Handled = false }).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on Synthetic.
    public void ExceptionMechanism_DifferentSynthetic_NotEqual()
    {
        new ExceptionMechanism { Synthetic = true }
            .Equals(new ExceptionMechanism { Synthetic = false }).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on HelpLink.
    public void ExceptionMechanism_DifferentHelpLink_NotEqual()
    {
        new ExceptionMechanism { HelpLink = "a" }
            .Equals(new ExceptionMechanism { HelpLink = "b" }).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on Source.
    public void ExceptionMechanism_DifferentSource_NotEqual()
    {
        new ExceptionMechanism { Source = "a" }
            .Equals(new ExceptionMechanism { Source = "b" }).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on ExceptionId.
    public void ExceptionMechanism_DifferentExceptionId_NotEqual()
    {
        new ExceptionMechanism { ExceptionId = 1u }
            .Equals(new ExceptionMechanism { ExceptionId = 2u }).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on ParentId.
    public void ExceptionMechanism_DifferentParentId_NotEqual()
    {
        new ExceptionMechanism { ParentId = 1u }
            .Equals(new ExceptionMechanism { ParentId = 2u }).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on IsExceptionGroup.
    public void ExceptionMechanism_DifferentIsExceptionGroup_NotEqual()
    {
        new ExceptionMechanism { IsExceptionGroup = true }
            .Equals(new ExceptionMechanism { IsExceptionGroup = false }).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on Data SequenceEqual (final &&).
    public void ExceptionMechanism_DifferentData_NotEqual()
    {
        var a = new ExceptionMechanism
        {
            Data = new SortedDictionary<string, string>(StringComparer.Ordinal) { ["k"] = "v1" },
        };
        var b = new ExceptionMechanism
        {
            Data = new SortedDictionary<string, string>(StringComparer.Ordinal) { ["k"] = "v2" },
        };
        a.Equals(b).Should().BeFalse();
    }

    // ---------- CapturedError ----------

    [Fact]
    // Why: kills `ReferenceEquals(this, other)` negation on CapturedError.
    public void CapturedError_ReferenceEqualsItself()
    {
        var a = new CapturedError { Type = "T" };
        a.Equals(a).Should().BeTrue();
    }

    [Fact]
    // Why: kills null-check on CapturedError.Equals.
    public void CapturedError_NotEqualToNull()
    {
        new CapturedError().Equals(null).Should().BeFalse();
    }

    [Fact]
    // Why: equal-field CapturedErrors must compare equal incl. Mechanism instance equality.
    public void CapturedError_EqualFieldsAreEqual()
    {
        var a = new CapturedError
        {
            Type = "T", Message = "m",
            Frames = new[] { new StackFrame { Function = "f", Line = 1 } },
            Mechanism = new ExceptionMechanism { Type = "generic", Handled = true },
            Release = "r", ServerName = "s",
        };
        var b = new CapturedError
        {
            Type = "T", Message = "m",
            Frames = new[] { new StackFrame { Function = "f", Line = 1 } },
            Mechanism = new ExceptionMechanism { Type = "generic", Handled = true },
            Release = "r", ServerName = "s",
        };
        a.Equals(b).Should().BeTrue();
        a.GetHashCode().Should().Be(b.GetHashCode());
    }

    [Fact]
    // Why: kills logical-mutation on Type.
    public void CapturedError_DifferentType_NotEqual()
    {
        new CapturedError { Type = "a" }
            .Equals(new CapturedError { Type = "b" }).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on Message.
    public void CapturedError_DifferentMessage_NotEqual()
    {
        new CapturedError { Message = "a" }
            .Equals(new CapturedError { Message = "b" }).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on Frames SequenceEqual.
    public void CapturedError_DifferentFrames_NotEqual()
    {
        var a = new CapturedError
        {
            Frames = new[] { new StackFrame { Function = "a" } },
        };
        var b = new CapturedError
        {
            Frames = new[] { new StackFrame { Function = "b" } },
        };
        a.Equals(b).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on Mechanism Equals.
    public void CapturedError_DifferentMechanism_NotEqual()
    {
        var a = new CapturedError
        {
            Mechanism = new ExceptionMechanism { Type = "x" },
        };
        var b = new CapturedError
        {
            Mechanism = new ExceptionMechanism { Type = "y" },
        };
        a.Equals(b).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on Mechanism null vs non-null.
    public void CapturedError_NullMechanismVsNonNull_NotEqual()
    {
        var a = new CapturedError { Mechanism = null };
        var b = new CapturedError { Mechanism = new ExceptionMechanism() };
        a.Equals(b).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on Release.
    public void CapturedError_DifferentRelease_NotEqual()
    {
        new CapturedError { Release = "a" }
            .Equals(new CapturedError { Release = "b" }).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on ServerName (final &&).
    public void CapturedError_DifferentServerName_NotEqual()
    {
        new CapturedError { ServerName = "a" }
            .Equals(new CapturedError { ServerName = "b" }).Should().BeFalse();
    }

    // ---------- DebugInfo ----------

    [Fact]
    // Why: kills `ReferenceEquals(this, other)` negation on DebugInfo.Equals.
    public void DebugInfo_ReferenceEqualsItself()
    {
        var a = new DebugInfo { Detail = "x" };
        a.Equals(a).Should().BeTrue();
    }

    [Fact]
    // Why: kills null-check on DebugInfo.Equals.
    public void DebugInfo_NotEqualToNull()
    {
        new DebugInfo().Equals(null).Should().BeFalse();
    }

    [Fact]
    // Why: equal-field DebugInfos must compare equal.
    public void DebugInfo_EqualFieldsAreEqual()
    {
        var a = new DebugInfo
        {
            Detail = "d",
            StackEntries = new[] { "a", "b" },
        };
        var b = new DebugInfo
        {
            Detail = "d",
            StackEntries = new[] { "a", "b" },
        };
        a.Equals(b).Should().BeTrue();
        a.GetHashCode().Should().Be(b.GetHashCode());
    }

    [Fact]
    // Why: kills logical-mutation on StackEntries SequenceEqual.
    public void DebugInfo_DifferentStackEntries_NotEqual()
    {
        var a = new DebugInfo { StackEntries = new[] { "x" } };
        var b = new DebugInfo { StackEntries = new[] { "y" } };
        a.Equals(b).Should().BeFalse();
    }

    [Fact]
    // Why: kills logical-mutation on Detail (final &&).
    public void DebugInfo_DifferentDetail_NotEqual()
    {
        var a = new DebugInfo { Detail = "x" };
        var b = new DebugInfo { Detail = "y" };
        a.Equals(b).Should().BeFalse();
    }

    // ---------- GetHashCode per-field statement-removal kills ----------
    //
    // Each `hc.Add(X)` in GetHashCode is a separate Stryker statement
    // mutation. Removing one means hashes that should be unique become
    // identical. We assert that two records differing ONLY in field X
    // produce DIFFERENT hash codes — this would fail if hc.Add(X) was
    // dropped (both hashes would then ignore X and collide).
    //
    // Hash collisions are theoretically possible but vanishingly unlikely
    // with HashCode.Combine for these concrete inputs; the test values
    // are stable byte patterns so the assertion is deterministic.

    [Fact]
    // Why: kills hc.Add(Function) statement mutation.
    public void StackFrame_HashCode_DependsOnFunction()
    {
        new StackFrame { Function = "f1" }.GetHashCode()
            .Should().NotBe(new StackFrame { Function = "f2" }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(Module) statement mutation.
    public void StackFrame_HashCode_DependsOnModule()
    {
        new StackFrame { Module = "m1" }.GetHashCode()
            .Should().NotBe(new StackFrame { Module = "m2" }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(Package) statement mutation.
    public void StackFrame_HashCode_DependsOnPackage()
    {
        new StackFrame { Package = "p1" }.GetHashCode()
            .Should().NotBe(new StackFrame { Package = "p2" }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(File) statement mutation.
    public void StackFrame_HashCode_DependsOnFile()
    {
        new StackFrame { File = "a.cs" }.GetHashCode()
            .Should().NotBe(new StackFrame { File = "b.cs" }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(AbsPath) statement mutation.
    public void StackFrame_HashCode_DependsOnAbsPath()
    {
        new StackFrame { AbsPath = "/a" }.GetHashCode()
            .Should().NotBe(new StackFrame { AbsPath = "/b" }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(Line) statement mutation.
    public void StackFrame_HashCode_DependsOnLine()
    {
        new StackFrame { Line = 1u }.GetHashCode()
            .Should().NotBe(new StackFrame { Line = 2u }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(ContextLine) statement mutation.
    public void StackFrame_HashCode_DependsOnContextLine()
    {
        new StackFrame { ContextLine = "a" }.GetHashCode()
            .Should().NotBe(new StackFrame { ContextLine = "b" }.GetHashCode());
    }

    [Fact]
    // Why: kills the `foreach (var s in PreContext) hc.Add(s)` loop body removal.
    public void StackFrame_HashCode_DependsOnPreContext()
    {
        new StackFrame { PreContext = new[] { "a" } }.GetHashCode()
            .Should().NotBe(new StackFrame { PreContext = new[] { "b" } }.GetHashCode());
    }

    [Fact]
    // Why: kills the `foreach (var s in PostContext) hc.Add(s)` loop body removal.
    public void StackFrame_HashCode_DependsOnPostContext()
    {
        new StackFrame { PostContext = new[] { "a" } }.GetHashCode()
            .Should().NotBe(new StackFrame { PostContext = new[] { "b" } }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(SourceLink) statement mutation.
    public void StackFrame_HashCode_DependsOnSourceLink()
    {
        new StackFrame { SourceLink = "a" }.GetHashCode()
            .Should().NotBe(new StackFrame { SourceLink = "b" }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(InApp) statement mutation.
    public void StackFrame_HashCode_DependsOnInApp()
    {
        new StackFrame { InApp = true }.GetHashCode()
            .Should().NotBe(new StackFrame { InApp = false }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(Type) statement mutation on ExceptionMechanism.
    public void ExceptionMechanism_HashCode_DependsOnType()
    {
        new ExceptionMechanism { Type = "a" }.GetHashCode()
            .Should().NotBe(new ExceptionMechanism { Type = "b" }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(Description) statement mutation.
    public void ExceptionMechanism_HashCode_DependsOnDescription()
    {
        new ExceptionMechanism { Description = "a" }.GetHashCode()
            .Should().NotBe(new ExceptionMechanism { Description = "b" }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(Handled) statement mutation.
    public void ExceptionMechanism_HashCode_DependsOnHandled()
    {
        new ExceptionMechanism { Handled = true }.GetHashCode()
            .Should().NotBe(new ExceptionMechanism { Handled = false }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(Synthetic) statement mutation.
    public void ExceptionMechanism_HashCode_DependsOnSynthetic()
    {
        new ExceptionMechanism { Synthetic = true }.GetHashCode()
            .Should().NotBe(new ExceptionMechanism { Synthetic = false }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(HelpLink) statement mutation.
    public void ExceptionMechanism_HashCode_DependsOnHelpLink()
    {
        new ExceptionMechanism { HelpLink = "a" }.GetHashCode()
            .Should().NotBe(new ExceptionMechanism { HelpLink = "b" }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(Source) statement mutation.
    public void ExceptionMechanism_HashCode_DependsOnSource()
    {
        new ExceptionMechanism { Source = "a" }.GetHashCode()
            .Should().NotBe(new ExceptionMechanism { Source = "b" }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(ExceptionId) statement mutation.
    public void ExceptionMechanism_HashCode_DependsOnExceptionId()
    {
        new ExceptionMechanism { ExceptionId = 1u }.GetHashCode()
            .Should().NotBe(new ExceptionMechanism { ExceptionId = 2u }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(ParentId) statement mutation.
    public void ExceptionMechanism_HashCode_DependsOnParentId()
    {
        new ExceptionMechanism { ParentId = 1u }.GetHashCode()
            .Should().NotBe(new ExceptionMechanism { ParentId = 2u }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(IsExceptionGroup) statement mutation.
    public void ExceptionMechanism_HashCode_DependsOnIsExceptionGroup()
    {
        new ExceptionMechanism { IsExceptionGroup = true }.GetHashCode()
            .Should().NotBe(new ExceptionMechanism { IsExceptionGroup = false }.GetHashCode());
    }

    [Fact]
    // Why: kills the Data foreach-key statement mutation (hc.Add(kv.Key)).
    public void ExceptionMechanism_HashCode_DependsOnDataKey()
    {
        var a = new ExceptionMechanism
        {
            Data = new SortedDictionary<string, string>(StringComparer.Ordinal)
                { ["k1"] = "v" },
        };
        var b = new ExceptionMechanism
        {
            Data = new SortedDictionary<string, string>(StringComparer.Ordinal)
                { ["k2"] = "v" },
        };
        a.GetHashCode().Should().NotBe(b.GetHashCode());
    }

    [Fact]
    // Why: kills the Data foreach-value statement mutation (hc.Add(kv.Value)).
    public void ExceptionMechanism_HashCode_DependsOnDataValue()
    {
        var a = new ExceptionMechanism
        {
            Data = new SortedDictionary<string, string>(StringComparer.Ordinal)
                { ["k"] = "v1" },
        };
        var b = new ExceptionMechanism
        {
            Data = new SortedDictionary<string, string>(StringComparer.Ordinal)
                { ["k"] = "v2" },
        };
        a.GetHashCode().Should().NotBe(b.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(Type) for CapturedError.
    public void CapturedError_HashCode_DependsOnType()
    {
        new CapturedError { Type = "a" }.GetHashCode()
            .Should().NotBe(new CapturedError { Type = "b" }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(Message) for CapturedError.
    public void CapturedError_HashCode_DependsOnMessage()
    {
        new CapturedError { Message = "a" }.GetHashCode()
            .Should().NotBe(new CapturedError { Message = "b" }.GetHashCode());
    }

    [Fact]
    // Why: kills the Frames foreach (hc.Add(f)) statement mutation.
    public void CapturedError_HashCode_DependsOnFrames()
    {
        var a = new CapturedError
        {
            Frames = new[] { new StackFrame { Function = "fa" } },
        };
        var b = new CapturedError
        {
            Frames = new[] { new StackFrame { Function = "fb" } },
        };
        a.GetHashCode().Should().NotBe(b.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(Mechanism) for CapturedError.
    public void CapturedError_HashCode_DependsOnMechanism()
    {
        var a = new CapturedError { Mechanism = new ExceptionMechanism { Type = "x" } };
        var b = new CapturedError { Mechanism = new ExceptionMechanism { Type = "y" } };
        a.GetHashCode().Should().NotBe(b.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(Release) for CapturedError.
    public void CapturedError_HashCode_DependsOnRelease()
    {
        new CapturedError { Release = "a" }.GetHashCode()
            .Should().NotBe(new CapturedError { Release = "b" }.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(ServerName) for CapturedError.
    public void CapturedError_HashCode_DependsOnServerName()
    {
        new CapturedError { ServerName = "a" }.GetHashCode()
            .Should().NotBe(new CapturedError { ServerName = "b" }.GetHashCode());
    }

    [Fact]
    // Why: kills the StackEntries foreach (hc.Add(s)) statement mutation
    // on DebugInfo.GetHashCode.
    public void DebugInfo_HashCode_DependsOnStackEntries()
    {
        var a = new DebugInfo { StackEntries = new[] { "a" } };
        var b = new DebugInfo { StackEntries = new[] { "b" } };
        a.GetHashCode().Should().NotBe(b.GetHashCode());
    }

    [Fact]
    // Why: kills hc.Add(Detail) statement mutation on DebugInfo.
    public void DebugInfo_HashCode_DependsOnDetail()
    {
        new DebugInfo { Detail = "a" }.GetHashCode()
            .Should().NotBe(new DebugInfo { Detail = "b" }.GetHashCode());
    }
}
