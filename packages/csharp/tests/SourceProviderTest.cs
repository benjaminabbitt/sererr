// SourceContext.PopulateContext populates context_line, pre_context, and
// post_context on a StackFrame given an ISourceProvider that knows how
// to resolve file → source bytes. Mirrors Rust's populate_source_context.

using FluentAssertions;
using Sererr;
using Xunit;

namespace Sererr.Tests;

public class SourceProviderTest
{
    private sealed class InMemoryProvider : ISourceProvider
    {
        private readonly Dictionary<string, string> _files;
        public InMemoryProvider(Dictionary<string, string> files) => _files = files;
        public string? GetSource(string file) => _files.GetValueOrDefault(file);
    }

    [Fact]
    // 5 lines of source, frame.line = 3 (1-based). With surrounding=1
    // we get pre=[line2], context=line3, post=[line4].
    public void PopulateContext_HappyPath()
    {
        var src = "a\nb\nc\nd\ne";
        var provider = new InMemoryProvider(new() { ["x.cs"] = src });

        var frame = new StackFrame { File = "x.cs", Line = 3 };
        frame = SourceContext.PopulateContext(frame, provider, 1);

        frame.ContextLine.Should().Be("c");
        frame.PreContext.Should().Equal("b");
        frame.PostContext.Should().Equal("d");
    }

    [Fact]
    // Line = 0 means unknown → no-op (returns frame unchanged).
    public void PopulateContext_UnknownLine_NoOp()
    {
        var provider = new InMemoryProvider(new() { ["x.cs"] = "a\nb\nc" });
        var frame = new StackFrame { File = "x.cs", Line = 0 };
        var result = SourceContext.PopulateContext(frame, provider, 2);
        result.ContextLine.Should().BeEmpty();
        result.PreContext.Should().BeEmpty();
        result.PostContext.Should().BeEmpty();
    }

    [Fact]
    // Provider returns null → no-op.
    public void PopulateContext_NoSource_NoOp()
    {
        var provider = new InMemoryProvider(new());
        var frame = new StackFrame { File = "missing.cs", Line = 3 };
        var result = SourceContext.PopulateContext(frame, provider, 2);
        result.ContextLine.Should().BeEmpty();
    }

    [Fact]
    // Line beyond file length → no-op (frame unchanged).
    public void PopulateContext_LineOutOfRange_NoOp()
    {
        var provider = new InMemoryProvider(new() { ["x.cs"] = "a\nb\nc" });
        var frame = new StackFrame { File = "x.cs", Line = 99 };
        var result = SourceContext.PopulateContext(frame, provider, 2);
        result.ContextLine.Should().BeEmpty();
    }

    [Fact]
    // At line 1 with surrounding=5 → pre_context is empty (saturates).
    public void PopulateContext_FirstLine_PreContextEmpty()
    {
        var provider = new InMemoryProvider(new() { ["x.cs"] = "first\nsecond\nthird" });
        var frame = new StackFrame { File = "x.cs", Line = 1 };
        var result = SourceContext.PopulateContext(frame, provider, 5);
        result.ContextLine.Should().Be("first");
        result.PreContext.Should().BeEmpty();
        result.PostContext.Should().Equal("second", "third");
    }
}
