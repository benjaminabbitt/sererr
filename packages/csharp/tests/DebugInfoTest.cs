// DebugInfoAdapter.ToDebugInfo formats a captured chain into the
// google.rpc.DebugInfo shape: a `detail` joined with "\nCaused by: " and
// `stack_entries` interleaving header lines with "  at <fn> (<file>:<line>)"
// frame lines. Must match Rust's text output byte-for-byte.

using FluentAssertions;
using Sererr;
using Xunit;

namespace Sererr.Tests;

public class DebugInfoTest
{
    [Fact]
    // Empty chain → both DebugInfo fields empty.
    public void EmptyChain_ProducesEmptyDebugInfo()
    {
        var di = DebugInfoAdapter.ToDebugInfo(Array.Empty<CapturedError>());
        di.StackEntries.Should().BeEmpty();
        di.Detail.Should().BeEmpty();
    }

    [Fact]
    // Single entry → detail is "<type>: <message>".
    public void SingleEntry_DetailIsTypeColonMessage()
    {
        var chain = new[] { new CapturedError { Type = "MyError", Message = "oops" } };
        var di = DebugInfoAdapter.ToDebugInfo(chain);
        di.Detail.Should().Be("MyError: oops");
    }

    [Fact]
    // Chain renders most-recent-first (outermost first): the leaf
    // header is plain, subsequent entries prefix "Caused by: ".
    public void Chain_RendersCausedByJoined()
    {
        var chain = new[]
        {
            new CapturedError { Type = "Inner", Message = "inner" },
            new CapturedError { Type = "Outer", Message = "outer" },
        };
        var di = DebugInfoAdapter.ToDebugInfo(chain);
        di.Detail.Should().Be("Outer: outer\nCaused by: Inner: inner");
    }

    [Fact]
    // Frame lines format: "  at <function> (<file>:<line>)".
    public void FrameLine_FormattedAtFunctionFileLine()
    {
        var chain = new[]
        {
            new CapturedError
            {
                Type = "T",
                Message = "m",
                Frames = new List<StackFrame>
                {
                    new() { Function = "doit", File = "src/x.rs", Line = 12 },
                },
            },
        };
        var di = DebugInfoAdapter.ToDebugInfo(chain);
        di.StackEntries.Should().Contain("  at doit (src/x.rs:12)");
    }

    [Fact]
    // Frame with no file → no parenthesized location segment.
    public void FrameLine_NoFile_NoLocation()
    {
        var chain = new[]
        {
            new CapturedError
            {
                Type = "T",
                Message = "m",
                Frames = new List<StackFrame> { new() { Function = "doit" } },
            },
        };
        var di = DebugInfoAdapter.ToDebugInfo(chain);
        di.StackEntries.Should().Contain("  at doit");
    }

    [Fact]
    // Frame with file but no line → "(file)" only.
    public void FrameLine_FileNoLine_NoColon()
    {
        var chain = new[]
        {
            new CapturedError
            {
                Type = "T",
                Message = "m",
                Frames = new List<StackFrame>
                {
                    new() { Function = "doit", File = "src/x.rs", Line = 0 },
                },
            },
        };
        var di = DebugInfoAdapter.ToDebugInfo(chain);
        di.StackEntries.Should().Contain("  at doit (src/x.rs)");
    }

    [Fact]
    // Empty function name renders as "<unknown>".
    public void FrameLine_EmptyFunction_RendersAsUnknown()
    {
        var chain = new[]
        {
            new CapturedError
            {
                Type = "T",
                Message = "m",
                Frames = new List<StackFrame>
                {
                    new() { Function = "", File = "src/x.rs", Line = 5 },
                },
            },
        };
        var di = DebugInfoAdapter.ToDebugInfo(chain);
        di.StackEntries.Should().Contain("  at <unknown> (src/x.rs:5)");
    }
}
