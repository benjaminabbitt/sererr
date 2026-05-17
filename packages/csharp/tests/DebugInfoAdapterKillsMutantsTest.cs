// Mutation-kill tests for DebugInfoAdapter surviving mutants:
//   - L40 Statement removal (`stackEntries.Add(...)` deleted)
//   - L40 Conditional (true/false) on `revIdx == 0` ternary
//   - L40 Equality mutation `revIdx == 0 → revIdx != 0`
//   - L40 String mutation `$"Caused by: {header}"` → `$""`
//
// The L25 chain.Count == 0 block-removal mutation is provably equivalent
// (an empty loop produces the same empty DebugInfo) — we leave it alone.

using FluentAssertions;
using Sererr;
using Xunit;

namespace Sererr.Tests;

public class DebugInfoAdapterKillsMutantsTest
{
    [Fact]
    // Why: kills L40 String mutation `$"Caused by: {header}" → $""`. The
    // chain's second entry (innermost, post-reverse) must appear in
    // stack_entries with a "Caused by: " prefix — not as an empty string.
    public void StackEntries_ChainEntriesHaveCausedByHeader()
    {
        var chain = new[]
        {
            new CapturedError { Type = "Inner", Message = "i" },
            new CapturedError { Type = "Outer", Message = "o" },
        };

        var di = DebugInfoAdapter.ToDebugInfo(chain);

        di.StackEntries.Should().Contain("Caused by: Inner: i",
            "chain entries past the outermost must be prefixed 'Caused by: '"
            + " in stack_entries");
    }

    [Fact]
    // Why: kills L40 Conditional (false) mutation — always picks the
    // "Caused by: " branch even for the outermost (revIdx==0) entry.
    // The outermost header must NOT have a "Caused by: " prefix.
    public void StackEntries_OutermostHeader_HasNoCausedByPrefix()
    {
        var chain = new[]
        {
            new CapturedError { Type = "Inner", Message = "i" },
            new CapturedError { Type = "Outer", Message = "o" },
        };

        var di = DebugInfoAdapter.ToDebugInfo(chain);

        di.StackEntries[0].Should().Be("Outer: o",
            "outermost header (revIdx == 0) must be bare, not 'Caused by: ...'");
        di.StackEntries[0].Should().NotStartWith("Caused by:");
    }

    [Fact]
    // Why: kills L40 Conditional (true) mutation — always picks `header`
    // (no "Caused by:" prefix), and L40 Equality `revIdx != 0`. With either
    // mutation, the inner entry's stack-entries header lacks "Caused by:"
    // and instead reads as a bare "Type: message". We pin the prefix on
    // the inner header.
    public void StackEntries_InnerHeader_StartsWithCausedBy()
    {
        var chain = new[]
        {
            new CapturedError { Type = "Inner", Message = "i" },
            new CapturedError { Type = "Outer", Message = "o" },
        };

        var di = DebugInfoAdapter.ToDebugInfo(chain);

        // Find the inner header in stack_entries — it's the second header
        // line after the outermost. With Conditional (true) mutation it
        // would be "Inner: i" (no prefix).
        di.StackEntries.Should().NotContain("Inner: i",
            "inner header must carry 'Caused by: ' prefix in stack_entries");
        di.StackEntries.Should().Contain("Caused by: Inner: i");
    }

    [Fact]
    // Why: kills L40 Statement mutation `stackEntries.Add(...) → ;`. With
    // the statement removed, headers are NEVER added to stack_entries —
    // only frame lines appear. We pin that the outermost header IS in
    // stack_entries.
    public void StackEntries_IncludeOutermostHeader()
    {
        var chain = new[]
        {
            new CapturedError { Type = "OnlyError", Message = "only" },
        };

        var di = DebugInfoAdapter.ToDebugInfo(chain);

        di.StackEntries.Should().Contain("OnlyError: only",
            "the outermost header must be appended to stack_entries");
    }

    [Fact]
    // Why: pins the exact order of stack_entries for a 3-deep chain:
    // header / frames / "Caused by: " header / frames / etc. Multiple
    // L40 mutations would scramble this ordering.
    public void StackEntries_OrderIsHeadersAndFramesInterleaved()
    {
        var chain = new[]
        {
            new CapturedError
            {
                Type = "A", Message = "a",
                Frames = new[] { new StackFrame { Function = "fa", File = "a.cs", Line = 1 } },
            },
            new CapturedError
            {
                Type = "B", Message = "b",
                Frames = new[] { new StackFrame { Function = "fb", File = "b.cs", Line = 2 } },
            },
            new CapturedError
            {
                Type = "C", Message = "c",
                Frames = new[] { new StackFrame { Function = "fc", File = "c.cs", Line = 3 } },
            },
        };

        var di = DebugInfoAdapter.ToDebugInfo(chain);

        // Outermost first: C, then B, then A. Each header immediately
        // followed by its frame line.
        di.StackEntries.Should().Equal(
            "C: c",
            "  at fc (c.cs:3)",
            "Caused by: B: b",
            "  at fb (b.cs:2)",
            "Caused by: A: a",
            "  at fa (a.cs:1)");
    }
}
