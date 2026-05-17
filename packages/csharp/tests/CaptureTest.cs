// Pins the Capture.Build semantics:
//  - single exception → one chain entry
//  - InnerException walked → most-causal-first array
//  - mechanism IDs stamped (exception_id, parent_id)
//  - default mechanism (type="generic", handled=true)
//  - AggregateException flattening
//  - frames captured from the exception's stack trace,
//    most-recent-call-first.

using FluentAssertions;
using Sererr;
using Xunit;

namespace Sererr.Tests;

public class CaptureTest
{
    private static Exception ThrowMe(string msg)
    {
        try { throw new InvalidOperationException(msg); }
        catch (Exception e) { return e; }
    }

    [Fact]
    // A single thrown exception captures to a one-entry chain whose
    // mechanism.exception_id == 0 and parent_id == 0.
    public void SingleException_ProducesOneEntryChain()
    {
        var ex = ThrowMe("oops");
        var chain = Capture.Build(ex, "InvalidOperationException", "v1", "host");

        chain.Should().HaveCount(1);
        chain[0].Type.Should().Be("InvalidOperationException");
        chain[0].Message.Should().Be("oops");
        chain[0].Mechanism.Should().NotBeNull();
        chain[0].Mechanism!.ExceptionId.Should().Be(0u);
        chain[0].Mechanism!.ParentId.Should().Be(0u);
    }

    [Fact]
    // Default mechanism: Type="generic", Handled=true.
    public void DefaultMechanism_IsGenericHandled()
    {
        var ex = ThrowMe("x");
        var chain = Capture.Build(ex, "T", "r", "s");
        chain[0].Mechanism!.Type.Should().Be("generic");
        chain[0].Mechanism!.Handled.Should().BeTrue();
    }

    [Fact]
    // Three-deep InnerException chain → 3-entry chain, most-causal-first
    // (innermost is element 0). Mechanism IDs stamped i / max(0,i-1).
    public void InnerExceptionChain_FlattenedMostCausalFirst()
    {
        Exception inner;
        try { throw new InvalidOperationException("inner"); }
        catch (Exception e) { inner = e; }

        Exception middle;
        try { throw new InvalidOperationException("middle", inner); }
        catch (Exception e) { middle = e; }

        Exception outer;
        try { throw new InvalidOperationException("outer", middle); }
        catch (Exception e) { outer = e; }

        var chain = Capture.Build(outer, "OuterError", "r", "s");

        chain.Should().HaveCount(3);
        chain[0].Message.Should().Be("inner");
        chain[2].Message.Should().Be("outer");
        chain[0].Mechanism!.ExceptionId.Should().Be(0u);
        chain[0].Mechanism!.ParentId.Should().Be(0u);
        chain[1].Mechanism!.ExceptionId.Should().Be(1u);
        chain[1].Mechanism!.ParentId.Should().Be(0u);
        chain[2].Mechanism!.ExceptionId.Should().Be(2u);
        chain[2].Mechanism!.ParentId.Should().Be(1u);
    }

    [Fact]
    // The leaf chain entry (the originating caught error) gets the
    // user-supplied type_name; other entries get GetType().FullName.
    public void TypeName_AppliedToLeaf()
    {
        Exception inner;
        try { throw new InvalidOperationException("inner"); }
        catch (Exception e) { inner = e; }

        Exception outer;
        try { throw new InvalidOperationException("outer", inner); }
        catch (Exception e) { outer = e; }

        var chain = Capture.Build(outer, "MyCustomLabel", "r", "s");

        // The outermost (caught) error is last; that's the one carrying
        // the operator-supplied label.
        chain[^1].Type.Should().Be("MyCustomLabel");
        chain[0].Type.Should().Be(typeof(InvalidOperationException).FullName);
    }

    [Fact]
    // AggregateException.InnerExceptions is plural: each inner is a
    // sibling sharing the AggregateException as parent. Flattened in
    // order, all addressing the AggregateException's parent_id.
    public void AggregateException_FlattensSiblings()
    {
        Exception a;
        try { throw new InvalidOperationException("a"); }
        catch (Exception e) { a = e; }
        Exception b;
        try { throw new InvalidOperationException("b"); }
        catch (Exception e) { b = e; }

        Exception agg;
        try { throw new AggregateException("agg", a, b); }
        catch (Exception e) { agg = e; }

        var chain = Capture.Build(agg, "AggregateException", "r", "s");

        // 3 entries: a, b (siblings, most-causal-first), then agg.
        chain.Should().HaveCount(3);
        // The two siblings come first; aggregate is last.
        chain[^1].Message.Should().StartWith("agg");
        // Both siblings parent the aggregate (which sits at index 2).
        chain[0].Mechanism!.ParentId.Should().Be(2u);
        chain[1].Mechanism!.ParentId.Should().Be(2u);
        chain[2].Mechanism!.ParentId.Should().Be(0u);
        chain[2].Mechanism!.ExceptionId.Should().Be(2u);
    }

    [Fact]
    // Frames are captured most-recent-first; the throwing frame is at
    // index 0. We assert the first frame's function name contains the
    // helper that threw.
    public void Frames_MostRecentFirst()
    {
        var ex = ThrowMe("x");
        var chain = Capture.Build(ex, "T", "r", "s");
        var frames = chain[0].Frames;
        frames.Should().NotBeEmpty();
        // Top frame is the throwing helper.
        frames[0].Function.Should().Contain("ThrowMe");
    }

    [Fact]
    // Release / server_name flow through to every entry in the chain.
    public void ReleaseAndServerName_AppliedToEveryEntry()
    {
        Exception inner;
        try { throw new InvalidOperationException("inner"); }
        catch (Exception e) { inner = e; }
        Exception outer;
        try { throw new InvalidOperationException("outer", inner); }
        catch (Exception e) { outer = e; }

        var chain = Capture.Build(outer, "T", "v1.0.0", "host-a");
        chain.Should().AllSatisfy(c =>
        {
            c.Release.Should().Be("v1.0.0");
            c.ServerName.Should().Be("host-a");
        });
    }
}
