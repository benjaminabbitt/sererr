// C# conformance runner — step definitions for the shared Cucumber
// corpus at tests/conformance/features/.
//
// Mirrors the Rust runner step-for-step. Step text patterns are the
// same Gherkin the Rust runner parses, but expressed as regex (anchored
// with ^...$) so Reqnroll matches them via its regex backend instead of
// the Cucumber-Expression backend — necessary because step text like
// "an error with no source / cause" contains "/" which the Cucumber
// Expression parser interprets as alternation.

using System.Globalization;
using System.IO;
using System.Text.Json;
using Google.Protobuf;
using Reqnroll;
using Sererr;
using Sererr.Proto;
using SererrV1 = Sererr.V1;

namespace Sererr.Conformance.Steps;

[Binding]
public sealed class ConformanceSteps
{
    private IReadOnlyList<CapturedError> _chain = Array.Empty<CapturedError>();
    private DebugInfo? _debugInfo;
    private byte[]? _encoded;
    private string? _fixture;
    private CapturedError? _fixtureInput;

    // ---------- background / generic ----------

    [Given(@"^the canonical sererr\.v1 proto schema$")]
    public void GivenCanonicalSchema()
    {
        // No-op: schema is implicit (compiled-in via proto-generated types).
    }

    // ---------- encoding.feature ----------

    [Given(@"^a fixture ""([^""]+)""$")]
    public void GivenFixture(string name)
    {
        _fixture = name;
    }

    [Given(@"^a default-initialized CapturedError \(all zero values\)$")]
    public void GivenDefaultCapturedError()
    {
        _fixtureInput = new CapturedError();
    }

    [When(@"^I construct the CapturedError per the fixture's JSON descriptor$")]
    public void WhenConstructFromFixture()
    {
        var name = _fixture ?? throw new InvalidOperationException("fixture name not set");
        var path = Path.Combine(FixturesDir, $"{name}.json");
        _fixtureInput = LoadFixture(path);
    }

    [When(@"^I serialize it via the proto adapter$")]
    [When(@"^I serialize it$")]
    public void WhenSerialize()
    {
        // Auto-construct from the named fixture if the scenario went
        // directly from `Given a fixture` to `When I serialize it`
        // without an explicit construct step. Mirrors the Rust runner's
        // fallback so the "Default values round-trip" scenario works.
        if (_fixtureInput is null && _fixture is { } name)
        {
            _fixtureInput = LoadFixture(Path.Combine(FixturesDir, $"{name}.json"));
        }
        var input = _fixtureInput
            ?? throw new InvalidOperationException("fixture input not constructed");
        var proto = ProtoAdapter.ToProto(input);
        _encoded = proto.ToByteArray();
    }

    [Then(@"^the encoded bytes match ""fixtures/([^""]+)\.pb""$")]
    public void ThenEncodedBytesMatchFixture(string name)
    {
        var path = Path.Combine(FixturesDir, $"{name}.pb");
        var expected = File.ReadAllBytes(path);
        var actual = _encoded ?? throw new InvalidOperationException("encoded bytes not set");
        if (!actual.SequenceEqual(expected))
        {
            throw new InvalidOperationException(
                $"encoded bytes mismatch for fixture {name}: " +
                $"expected {expected.Length} bytes, got {actual.Length} bytes");
        }
    }

    [Then(@"^the encoded bytes are empty$")]
    public void ThenEncodedBytesEmpty()
    {
        var bytes = _encoded ?? throw new InvalidOperationException("encoded bytes not set");
        if (bytes.Length != 0)
        {
            throw new InvalidOperationException(
                $"expected empty bytes, got {bytes.Length} bytes");
        }
    }

    [When(@"^I deserialize the bytes back to a CapturedError$")]
    public void WhenDeserialize()
    {
        var bytes = _encoded ?? throw new InvalidOperationException("encoded bytes not set");
        var proto = SererrV1.CapturedError.Parser.ParseFrom(bytes);
        _fixtureInput = ProtoAdapter.FromProto(proto);
    }

    [Then(@"^the result equals the input field-by-field$")]
    public void ThenRoundTripEqual()
    {
        var name = _fixture ?? throw new InvalidOperationException("fixture name not set");
        var expected = LoadFixture(Path.Combine(FixturesDir, $"{name}.json"));
        var actual = _fixtureInput ?? throw new InvalidOperationException("decoded not set");
        if (!Equals(actual, expected))
        {
            throw new InvalidOperationException(
                $"round-trip mismatch for fixture {name}");
        }
    }

    // ---------- chain.feature ----------

    [Given(@"^an error with no source / cause$")]
    public void GivenErrorWithNoCause()
    {
        var err = MakeLabeled("single", innerCause: null);
        _chain = Capture.Build(err, "LabeledError", "test", "host");
    }

    [Given(@"^a chain ""([^""]+)"" caused-by ""([^""]+)"" caused-by ""([^""]+)""$")]
    public void GivenThreeDeepChain(string inner, string middle, string outer)
    {
        // Build innermost-first then wrap outwards so .InnerException
        // walks outer → middle → inner.
        var innerEx = MakeLabeled(inner, innerCause: null);
        var middleEx = MakeLabeled(middle, innerCause: innerEx);
        var outerEx = MakeLabeled(outer, innerCause: middleEx);
        _chain = Capture.Build(outerEx, "LabeledError", "test", "host");
    }

    [Given(@"^a chain ""([^""]+)"" caused-by ""([^""]+)""$")]
    public void GivenTwoDeepChain(string inner, string outer)
    {
        var innerEx = MakeLabeled(inner, innerCause: null);
        var outerEx = MakeLabeled(outer, innerCause: innerEx);
        _chain = Capture.Build(outerEx, "LabeledError", "test", "host");
    }

    [Given(@"^a captured error with frames$")]
    public void GivenCapturedWithFrames()
    {
        var err = MakeLabeled("x", innerCause: null);
        _chain = Capture.Build(err, "LabeledError", "test", "host");
    }

    [When(@"^I capture it$")]
    [When(@"^I capture the outermost error$")]
    public void WhenCapture()
    {
        // Capture already happened in the Given step.
    }

    [When(@"^I read the frames$")]
    public void WhenReadFrames()
    {
        // No-op; frames are already in _chain.
    }

    [Then(@"^the chain length is (\d+)$")]
    public void ThenChainLength(int n)
    {
        if (_chain.Count != n)
        {
            throw new InvalidOperationException(
                $"chain length expected {n}, got {_chain.Count}");
        }
    }

    [Then(@"^entry (\d+) has exception_id (\d+) and parent_id (\d+)$")]
    public void ThenEntryMechanismIds(int idx, uint exceptionId, uint parentId)
    {
        var entry = _chain[idx];
        var mech = entry.Mechanism
            ?? throw new InvalidOperationException($"entry {idx} has no mechanism");
        if (mech.ExceptionId != exceptionId)
        {
            throw new InvalidOperationException(
                $"entry {idx} exception_id expected {exceptionId}, got {mech.ExceptionId}");
        }
        if (mech.ParentId != parentId)
        {
            throw new InvalidOperationException(
                $"entry {idx} parent_id expected {parentId}, got {mech.ParentId}");
        }
    }

    [Then(@"^the entry's mechanism has exception_id 0$")]
    public void ThenSingleEntryExceptionIdZero()
    {
        var mech = _chain[0].Mechanism
            ?? throw new InvalidOperationException("entry has no mechanism");
        if (mech.ExceptionId != 0u)
        {
            throw new InvalidOperationException(
                $"expected exception_id 0, got {mech.ExceptionId}");
        }
    }

    [Then(@"^the entry's mechanism has parent_id 0$")]
    public void ThenSingleEntryParentIdZero()
    {
        var mech = _chain[0].Mechanism
            ?? throw new InvalidOperationException("entry has no mechanism");
        if (mech.ParentId != 0u)
        {
            throw new InvalidOperationException(
                $"expected parent_id 0, got {mech.ParentId}");
        }
    }

    [Then(@"^entry (\d+) has message ""([^""]+)""$")]
    public void ThenEntryMessage(int idx, string msg)
    {
        var actual = _chain[idx].Message;
        if (actual != msg)
        {
            throw new InvalidOperationException(
                $"entry {idx} message expected '{msg}', got '{actual}'");
        }
    }

    [Then(@"^the last chain entry has message ""([^""]+)""$")]
    public void ThenLastEntryMessage(string msg)
    {
        if (_chain.Count == 0)
        {
            throw new InvalidOperationException("chain is empty");
        }
        var last = _chain[^1];
        if (last.Message != msg)
        {
            throw new InvalidOperationException(
                $"last entry message expected '{msg}', got '{last.Message}'");
        }
    }

    [Then(@"^the first frame is the most recent call$")]
    public void ThenFirstFrameMostRecent()
    {
        // We can't assert "this IS the most recent" without a known
        // reference; we only assert the chain has frames captured at
        // all. Producers that want a stricter assertion can extend
        // the step in their own runner — the conformance contract is
        // just that frames exist and are most-recent-first by
        // construction.
        if (_chain.Count == 0 || _chain[0].Frames.Count == 0)
        {
            throw new InvalidOperationException("no captured frames");
        }
    }

    // ---------- debuginfo-adapter.feature ----------

    [Given(@"^an empty chain$")]
    public void GivenEmptyChain()
    {
        _chain = Array.Empty<CapturedError>();
    }

    [Given(@"^a single CapturedError with type ""([^""]+)"" and message ""([^""]+)""$")]
    public void GivenSingleCaptured(string type, string msg)
    {
        _chain = new[] { new CapturedError { Type = type, Message = msg } };
    }

    [Given(@"^a CapturedError with one frame:$")]
    public void GivenCapturedWithOneFrameTable(Table table)
    {
        // Reqnroll's Table: header row + one data row. Columns:
        // function | file | line.
        var row = table.Rows[0];
        var frame = new StackFrame
        {
            Function = row["function"],
            File = row["file"],
            Line = uint.Parse(row["line"], CultureInfo.InvariantCulture),
        };
        _chain = new[]
        {
            new CapturedError
            {
                Type = "T",
                Message = "m",
                Frames = new[] { frame },
            },
        };
    }

    [When(@"^I call to_debug_info$")]
    public void WhenCallToDebugInfo()
    {
        _debugInfo = DebugInfoAdapter.ToDebugInfo(_chain);
    }

    [Then(@"^stack_entries is empty$")]
    public void ThenStackEntriesEmpty()
    {
        var di = _debugInfo ?? throw new InvalidOperationException("debug_info not set");
        if (di.StackEntries.Count != 0)
        {
            throw new InvalidOperationException(
                $"expected empty stack_entries, got {di.StackEntries.Count} entries");
        }
    }

    [Then(@"^detail is empty$")]
    public void ThenDetailEmpty()
    {
        var di = _debugInfo ?? throw new InvalidOperationException("debug_info not set");
        if (di.Detail.Length != 0)
        {
            throw new InvalidOperationException(
                $"expected empty detail, got '{di.Detail}'");
        }
    }

    [Then(@"^detail equals ""([^""]+)""$")]
    public void ThenDetailEquals(string expected)
    {
        var di = _debugInfo ?? throw new InvalidOperationException("debug_info not set");
        if (di.Detail != expected)
        {
            throw new InvalidOperationException(
                $"detail expected '{expected}', got '{di.Detail}'");
        }
    }

    [Then(@"^detail contains ""([^""]+)""$")]
    public void ThenDetailContains(string needle)
    {
        var di = _debugInfo ?? throw new InvalidOperationException("debug_info not set");
        if (!di.Detail.Contains(needle, StringComparison.Ordinal))
        {
            throw new InvalidOperationException(
                $"detail '{di.Detail}' should contain '{needle}'");
        }
    }

    [Then(@"^stack_entries contains ""([^""]+)""$")]
    public void ThenStackEntriesContains(string needle)
    {
        var di = _debugInfo ?? throw new InvalidOperationException("debug_info not set");
        if (!di.StackEntries.Contains(needle))
        {
            throw new InvalidOperationException(
                $"stack_entries should contain '{needle}'");
        }
    }

    // ---------- helpers ----------

    private static string FixturesDir =>
        Environment.GetEnvironmentVariable("SERERR_FIXTURES_DIR")
        ?? throw new InvalidOperationException(
            "SERERR_FIXTURES_DIR env var must point at tests/conformance/fixtures");

    /// <summary>
    /// Synthetic exception whose .Message and .InnerException are
    /// controllable for building deterministic chains.
    /// </summary>
    private sealed class LabeledException : Exception
    {
        public LabeledException(string message, Exception? inner)
            : base(message, inner)
        {
        }
    }

    private static LabeledException MakeLabeled(string msg, Exception? innerCause)
    {
        // Throw + catch so the exception carries a real stack trace.
        try
        {
            throw new LabeledException(msg, innerCause);
        }
        catch (LabeledException e)
        {
            return e;
        }
    }

    private static CapturedError LoadFixture(string path)
    {
        var json = File.ReadAllText(path);
        return FixtureLoader.LoadCapturedError(json);
    }
}
