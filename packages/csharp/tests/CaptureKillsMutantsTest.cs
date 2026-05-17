// Mutation-kill tests focused on Capture.cs surviving mutants:
//   - Demystify() call removal (async state-machine frames)
//   - StackTrace(exception, fNeedFileInfo: true) — true vs false
//   - GetFrames null/empty branch
//   - lineNumber > 0 boundary
//   - FormatMethod string mutations (typeName fallbacks)
//   - IsAppFrame heuristic prefix branches and final return
//   - Nested aggregate parent_id arithmetic

using System;
using FluentAssertions;
using Sererr;
using Xunit;

namespace Sererr.Tests;

public class CaptureKillsMutantsTest
{
    // ---------- Frame field population (fNeedFileInfo, lineNumber > 0) ----------

    [Fact]
    // Why: kills `fNeedFileInfo: true → false` (L177) by asserting some frame
    // has a non-empty File path. Also kills L194 ternary mutations on
    // lineNumber > 0 (true/false/<0/>=0) by asserting some frame has Line > 0.
    public void Frames_HaveFileAndLineNumbersPopulated()
    {
        Exception ex;
        try { throw new InvalidOperationException("xyz"); }
        catch (Exception e) { ex = e; }

        var chain = Capture.Build(ex, "T", "r", "h");
        var frames = chain[0].Frames;

        frames.Should().NotBeEmpty();
        frames.Should().Contain(f => !string.IsNullOrEmpty(f.File),
            "fNeedFileInfo=true must populate File on at least one frame");
        frames.Should().Contain(f => f.Line > 0,
            "lineNumber > 0 ternary must yield a non-zero Line for at least one frame");
    }

    [Fact]
    // Why: kills the `raw is null || raw.Length == 0` mutation (L179) flipping
    // to `&&` — an unthrown exception has no captured trace. The mutant would
    // NRE/throw on Length access; the original returns empty frames safely.
    // Also kills frame-extraction null guards by exercising the empty path.
    public void UnthrownException_BuildsWithEmptyFrames_NoCrash()
    {
        var ex = new InvalidOperationException("never thrown");
        var chain = Capture.Build(ex, "T", "r", "h");

        chain.Should().HaveCount(1);
        chain[0].Frames.Should().BeEmpty(
            "unthrown exceptions have no stack trace; capture must return empty frames");
    }

    // ---------- FormatMethod / function-name format ----------

    [Fact]
    // Why: kills FormatMethod's ternary mutations (L215) and the
    // `declaring?.FullName ?? declaring?.Name ?? ""` null-coalesce (L214).
    // For a normal type, FullName has the namespace; the function name must
    // be of the shape "{Namespace.Type}.{Method}" — NOT just "{Method}" nor
    // ".{Method}".
    public void Frames_FunctionName_IncludesFullTypeName()
    {
        Exception ex;
        try { throw new InvalidOperationException("y"); }
        catch (Exception e) { ex = e; }

        var chain = Capture.Build(ex, "T", "r", "h");
        var frames = chain[0].Frames;

        // The throwing helper lives in this test class. Its function name
        // must contain the namespace-qualified type name and a dot before
        // the method name (kills L215 mutations).
        frames.Should().Contain(f =>
            f.Function.Contains("Sererr.Tests.CaptureKillsMutantsTest.")
            || f.Function.Contains("Sererr.Tests.CaptureKillsMutantsTest+"),
            "function names must be of the form '{FullTypeName}.{Method}'; "
            + "without that prefix the namespace got dropped or the type "
            + "qualifier got replaced with an empty string");

        // No frame's function should START with a '.' (which is what happens
        // if the ternary always picks the $"{typeName}.{method.Name}" branch
        // with typeName="" — the String mutation `typeName != null`).
        frames.Should().NotContain(f => f.Function.StartsWith("."),
            "function names must not start with '.' (empty typeName concat)");
    }

    [Fact]
    // Why: kills the Module null-coalesce mutation (L190) — Module must be
    // populated to the declaring type's FullName, not "".
    public void Frames_Module_IsPopulated()
    {
        Exception ex;
        try { throw new InvalidOperationException("z"); }
        catch (Exception e) { ex = e; }

        var chain = Capture.Build(ex, "T", "r", "h");
        chain[0].Frames.Should().Contain(f => !string.IsNullOrEmpty(f.Module),
            "Module must be populated (DeclaringType.FullName) on real frames");
    }

    [Fact]
    // Why: kills the Package null-coalesce mutation (L191) — Package is
    // the declaring type's assembly name. Real frames must have it.
    public void Frames_Package_IsPopulated()
    {
        Exception ex;
        try { throw new InvalidOperationException("z"); }
        catch (Exception e) { ex = e; }

        var chain = Capture.Build(ex, "T", "r", "h");
        chain[0].Frames.Should().Contain(f => !string.IsNullOrEmpty(f.Package),
            "Package must be populated (Assembly name) on real frames");
    }

    [Fact]
    // Why: kills AbsPath/File null-coalesce mutations (L192) — File and
    // AbsPath are populated from GetFileName(). Should be non-empty for
    // user-code frames (we ship embedded PDBs in test config).
    public void Frames_AbsPathAndFile_ArePopulated()
    {
        Exception ex;
        try { throw new InvalidOperationException("z"); }
        catch (Exception e) { ex = e; }

        var chain = Capture.Build(ex, "T", "r", "h");
        chain[0].Frames.Should().Contain(f =>
            !string.IsNullOrEmpty(f.AbsPath) && !string.IsNullOrEmpty(f.File),
            "AbsPath and File must be populated for user-code frames");
    }

    // ---------- IsAppFrame heuristic ----------

    [Fact]
    // Why: kills the IsAppFrame Negate-expression mutations on the
    // prefix checks (L238, L240) and the final `return true` boolean
    // mutation (L242). User code (this test class) must be in_app=true.
    public void Frames_UserCode_IsInApp()
    {
        Exception ex;
        try { throw new InvalidOperationException("user-code"); }
        catch (Exception e) { ex = e; }

        var chain = Capture.Build(ex, "T", "r", "h");
        chain[0].Frames.Should().Contain(f => f.InApp,
            "at least one user-code frame must be classified as in_app=true");
    }

    [Fact]
    // Why: kills the IsAppFrame String mutations on the prefix strings
    // (L232: "System." -> ""; L233: "Microsoft." -> ""; L234: "Internal." -> "").
    // If any prefix becomes "", every frame's module starts with "" so every
    // frame returns false (not in_app). Conversely, system frames (which
    // exist in any thrown exception's trace) must still be classified as
    // !in_app — kills the Negate mutations.
    public void Frames_SystemCode_IsNotInApp()
    {
        // ThrowHelper.ThrowInvalidOperationException is reachable in the
        // trace via runtime internals; alternatively just exercise the
        // path through a TPL await that produces System.* frames.
        Exception ex;
        try
        {
            // System.String.Substring throws ArgumentOutOfRangeException
            // from inside System.* — its frames have module="System.String".
            var s = "abc";
            _ = s.Substring(100);
            ex = new Exception("never");
        }
        catch (Exception e) { ex = e; }

        var chain = Capture.Build(ex, "T", "r", "h");

        // At least one frame should be a System.* frame and that frame
        // must be in_app=false (kills Negate mutations on prefix checks
        // and the final `return true`).
        chain[0].Frames.Should().Contain(f =>
            f.Module.StartsWith("System.", StringComparison.Ordinal) && !f.InApp,
            "frames whose module starts with 'System.' must be in_app=false");
    }

    // ---------- Nested aggregate index arithmetic ----------

    [Fact]
    // Why: kills the L58 arithmetic mutation `n - 1 - pre → n - 1 + pre`.
    // A nested aggregate sets pre != 0 for some sibling, so `+pre` and
    // `-pre` produce different post-reverse indices. We pin the exact
    // parent_id of the inner aggregate's child against the (correct)
    // formula.
    public void NestedAggregate_ParentIdsRespectIndexArithmetic()
    {
        // Build agg_outer(agg_inner(leaf), other_leaf).
        Exception aggInner;
        try
        {
            Exception leaf;
            try { throw new InvalidOperationException("leaf"); }
            catch (Exception e) { leaf = e; }
            throw new AggregateException("inner", leaf);
        }
        catch (Exception e) { aggInner = e; }

        Exception otherLeaf;
        try { throw new InvalidOperationException("other"); }
        catch (Exception e) { otherLeaf = e; }

        Exception aggOuter;
        try { throw new AggregateException("outer", aggInner, otherLeaf); }
        catch (Exception e) { aggOuter = e; }

        var chain = Capture.Build(aggOuter, "AggOuter", "r", "h");
        // pre-reverse walker order: [aggOuter(0), aggInner(1), leaf(2), otherLeaf(3)]
        // post-reverse order: [otherLeaf(0), leaf(1), aggInner(2), aggOuter(3)]
        // Sibling parents (post-reverse):
        //   aggInner's children: leaf (pre=1) → post = 4-1-1 = 2 (aggInner's
        //     post-reverse index) ✓
        //   aggOuter's children: aggInner (pre=0) → post = 4-1-0 = 3
        //                         otherLeaf (pre=0) → post = 4-1-0 = 3
        chain.Should().HaveCount(4);
        chain[0].Message.Should().Be("other");
        chain[1].Message.Should().Be("leaf");
        chain[2].Message.Should().StartWith("inner");
        chain[3].Message.Should().StartWith("outer");

        // leaf is a sibling of aggInner (which sits at post-reverse index 2).
        // With `n - 1 + pre` the formula would compute 4-1+1=4 (out of range)
        // for leaf's parent — observably wrong (would cast to uint as 4, not 2).
        chain[1].Mechanism!.ParentId.Should().Be(2u);

        // aggInner sits at index 2 and is itself a sibling of aggOuter (post=3).
        chain[2].Mechanism!.ParentId.Should().Be(3u);

        // otherLeaf sits at index 0; its aggregate parent is aggOuter (post=3).
        chain[0].Mechanism!.ParentId.Should().Be(3u);

        // aggOuter is the root.
        chain[3].Mechanism!.ParentId.Should().Be(0u);
        chain[3].Mechanism!.ExceptionId.Should().Be(3u);
    }

    [Fact]
    // Why: pins IsExceptionGroup=true on aggregates and false on plain
    // exceptions. Without this, the L76-78 branch (parentId = 0u for
    // aggregate root) and downstream proto-level introspection are
    // unverified.
    public void AggregateException_MarksIsExceptionGroup()
    {
        Exception inner;
        try { throw new InvalidOperationException("i"); }
        catch (Exception e) { inner = e; }
        Exception agg;
        try { throw new AggregateException("a", inner); }
        catch (Exception e) { agg = e; }

        var chain = Capture.Build(agg, "Agg", "r", "h");

        // chain order most-causal-first: [inner, agg]
        chain[0].Mechanism!.IsExceptionGroup.Should().BeFalse();
        chain[^1].Mechanism!.IsExceptionGroup.Should().BeTrue();
    }
}
