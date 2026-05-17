// Mutation-kill tests for SourceContext.PopulateContext surviving mutants:
//   - L29 Block removal on the `if (frame.Line == 0) return frame` early
//     return (detect via spy provider)
//   - L40 Remove `checked` cast (Line > int.MaxValue triggers overflow)
//   - L41 Equality boundary `idx >= lines.Count` vs `idx > lines.Count`
//   - L68 SplitLines `\r\n` stripping (\r at start vs after content)
//   - L73 Trailing-newline emission `start < source.Length`
//
// The SplitLines mutations are exercised THROUGH PopulateContext since the
// helper is private; the test inputs are crafted so the line-splitting
// difference is visible in ContextLine / PreContext / PostContext output.

using System;
using System.Collections.Generic;
using FluentAssertions;
using Sererr;
using Xunit;

namespace Sererr.Tests;

public class SourceContextKillsMutantsTest
{
    private sealed class MapProvider : ISourceProvider
    {
        private readonly Dictionary<string, string> _files;
        public MapProvider(Dictionary<string, string> files) => _files = files;
        public string? GetSource(string file) => _files.GetValueOrDefault(file);
    }

    private sealed class ThrowingProvider : ISourceProvider
    {
        public int CallCount;
        public string? GetSource(string file)
        {
            CallCount++;
            throw new InvalidOperationException(
                "GetSource should not be called when frame.Line == 0");
        }
    }

    [Fact]
    // Why: kills the L29 block-removal mutation. The Line == 0 branch must
    // RETURN before calling the provider; mutant removes the return so the
    // provider is invoked. A throwing provider makes that observable.
    public void PopulateContext_LineZero_DoesNotCallProvider()
    {
        var provider = new ThrowingProvider();
        var frame = new StackFrame { File = "x.cs", Line = 0 };

        var result = SourceContext.PopulateContext(frame, provider, 2);

        provider.CallCount.Should().Be(0,
            "Line == 0 must short-circuit before calling provider.GetSource");
        result.Should().BeSameAs(frame, "Line == 0 returns the input frame unchanged");
    }

    [Fact]
    // Why: kills the L40 `Remove checked expression` mutation. With `checked`,
    // casting Line > int.MaxValue overflows and throws OverflowException;
    // without `checked`, it silently wraps to a negative int and the method
    // returns the frame unchanged. We pin the throwing behavior.
    public void PopulateContext_LineExceedsInt32_ThrowsOverflow()
    {
        var provider = new MapProvider(new() { ["x.cs"] = "a\nb\nc" });
        // uint value > int.MaxValue triggers OverflowException in checked
        // cast. The casted line number is intentionally absurd; it only
        // exercises the `checked` semantics.
        var frame = new StackFrame { File = "x.cs", Line = (uint)int.MaxValue + 1u };

        Action act = () => SourceContext.PopulateContext(frame, provider, 2);

        act.Should().Throw<OverflowException>(
            "checked((int)frame.Line) must overflow for Line > int.MaxValue");
    }

    [Fact]
    // Why: kills the L41 boundary mutation `idx >= lines.Count` →
    // `idx > lines.Count`. With Line = lines.Count + 1, idx == lines.Count
    // and the original returns frame unchanged. The mutant proceeds and
    // accesses lines[idx], which throws IndexOutOfRange. We pin the
    // no-throw, no-mutation behavior.
    public void PopulateContext_LineExactlyOnePastEnd_ReturnsUnchanged()
    {
        // 3 lines → idx range [0, 2]. Line=4 → idx=3 == lines.Count.
        var provider = new MapProvider(new() { ["x.cs"] = "a\nb\nc" });
        var frame = new StackFrame { File = "x.cs", Line = 4 };

        StackFrame? result = null;
        Action act = () => result = SourceContext.PopulateContext(frame, provider, 2);

        act.Should().NotThrow();
        result!.ContextLine.Should().BeEmpty("frame must be unchanged when idx == lines.Count");
        result.PreContext.Should().BeEmpty();
        result.PostContext.Should().BeEmpty();
    }

    [Fact]
    // Why: kills L68 mutations on `\r\n` stripping logic.
    //   - `lineEnd > start` → `lineEnd < start` / `lineEnd >= start`:
    //     for source "\r\n" the `\r` is at index 0, `\n` at 1; original
    //     strips the leading `\r` to yield [""], mutants either skip the
    //     strip (yielding ["\r"]) or out-of-range access.
    // We pin: line 1 of "\r\n" has ContextLine == "" (the `\r` was stripped).
    public void PopulateContext_CrLfFirstLine_StripsCarriageReturn()
    {
        var provider = new MapProvider(new() { ["x.cs"] = "\r\nsecond" });
        var frame = new StackFrame { File = "x.cs", Line = 1 };

        var result = SourceContext.PopulateContext(frame, provider, 0);

        result.ContextLine.Should().Be("",
            "the leading `\\r` before `\\n` must be stripped, leaving an empty line");
    }

    [Fact]
    // Why: kills L68 `lineEnd - 1 → lineEnd + 1` arithmetic mutation. The
    // check `source[lineEnd - 1] == '\r'` looks BACK one char to detect
    // CRLF; mutant looks FORWARD which would either read past the next
    // line's first char (silently wrong) or crash. We pin a multi-line
    // CRLF source: line 2's ContextLine must be exactly "two" — not
    // "two\r" (no decrement) and not throwing.
    public void PopulateContext_CrLfMidStream_StripsCarriageReturn()
    {
        // "one\r\ntwo\r\nthree" → lines ["one", "two", "three"].
        var provider = new MapProvider(new() { ["x.cs"] = "one\r\ntwo\r\nthree" });
        var frame = new StackFrame { File = "x.cs", Line = 2 };

        var result = SourceContext.PopulateContext(frame, provider, 1);

        result.ContextLine.Should().Be("two", "CRLF stripping must produce 'two', not 'two\\r'");
        result.PreContext.Should().Equal("one");
        result.PostContext.Should().Equal("three");
    }

    [Fact]
    // Why: kills L73 `start < source.Length → start <= source.Length`. A
    // trailing newline must NOT produce a final empty line. Rust's
    // `str::lines()` returns ["a"] for "a\n"; the mutant would return
    // ["a", ""] and PopulateContext at Line=2 would then yield
    // ContextLine="" instead of returning the frame unchanged.
    public void PopulateContext_TrailingNewline_NoExtraEmptyLine()
    {
        var provider = new MapProvider(new() { ["x.cs"] = "only\n" });
        // Original: lines.Count == 1, so Line=2 is out of range → unchanged.
        // Mutant: lines.Count == 2 (extra ""), Line=2 → ContextLine=""
        // and PreContext=["only"].
        var frame = new StackFrame { File = "x.cs", Line = 2 };

        var result = SourceContext.PopulateContext(frame, provider, 1);

        result.PreContext.Should().BeEmpty(
            "trailing-newline must not synthesize a phantom line 2");
        result.ContextLine.Should().BeEmpty();
        result.PostContext.Should().BeEmpty();
    }

    [Fact]
    // Why: complements the trailing-newline test by pinning the positive
    // case — "a\n" splits to ["a"], so Line=1 gives ContextLine="a".
    public void PopulateContext_TrailingNewline_LineOneStillWorks()
    {
        var provider = new MapProvider(new() { ["x.cs"] = "a\n" });
        var frame = new StackFrame { File = "x.cs", Line = 1 };

        var result = SourceContext.PopulateContext(frame, provider, 1);

        result.ContextLine.Should().Be("a");
        result.PreContext.Should().BeEmpty();
        result.PostContext.Should().BeEmpty();
    }

    [Fact]
    // Why: kills L68 mutation `lineEnd > start → lineEnd >= start` for the
    // case lineEnd == start (a `\n` at index 0 of the source). Original
    // `>` is false → no decrement → safe. Mutant `>=` is true → accesses
    // source[-1] → IndexOutOfRange. Pin no-throw and the correct empty
    // first line.
    public void PopulateContext_LeadingNewline_NoCrashOnLineEndEqualsStart()
    {
        var provider = new MapProvider(new() { ["x.cs"] = "\nfoo" });
        // Line 1 is the empty leading line; Line 2 is "foo".
        var frame = new StackFrame { File = "x.cs", Line = 1 };

        StackFrame? result = null;
        Action act = () => result = SourceContext.PopulateContext(frame, provider, 0);
        act.Should().NotThrow();
        result!.ContextLine.Should().Be("",
            "leading `\\n` yields an empty first line, not a crash");

        var frame2 = new StackFrame { File = "x.cs", Line = 2 };
        var r2 = SourceContext.PopulateContext(frame2, provider, 0);
        r2.ContextLine.Should().Be("foo");
    }
}
