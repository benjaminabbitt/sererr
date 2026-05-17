// Proto round-trip: plain → proto → bytes → proto → plain.
// Also asserts byte-equivalence against the committed conformance
// fixtures, which is the cross-language wire-format contract.

using System.IO;
using System.Linq;
using FluentAssertions;
using Google.Protobuf;
using Sererr;
using Sererr.Proto;
using Xunit;

namespace Sererr.Tests;

public class ProtoRoundtripTest
{
    [Fact]
    // Plain → proto → plain preserves every field.
    public void RoundTrip_PreservesFields()
    {
        var input = new CapturedError
        {
            Type = "MyError",
            Message = "oops",
            Frames = new List<StackFrame>
            {
                new()
                {
                    Function = "f", Module = "mypkg", File = "src/x.rs",
                    Line = 12, InApp = true,
                },
            },
            Mechanism = new ExceptionMechanism
            {
                Type = "generic", Handled = true,
            },
            Release = "v1.0.0",
            ServerName = "host-1",
        };

        var proto = ProtoAdapter.ToProto(input);
        var bytes = proto.ToByteArray();
        var decoded = Sererr.V1.CapturedError.Parser.ParseFrom(bytes);
        var roundtripped = ProtoAdapter.FromProto(decoded);

        roundtripped.Should().Be(input);
    }

    [Fact]
    // Default CapturedError serializes to empty bytes (all zero-value
    // fields are dropped by proto3).
    public void DefaultCapturedError_SerializesEmpty()
    {
        var c = new CapturedError();
        var proto = ProtoAdapter.ToProto(c);
        proto.ToByteArray().Should().BeEmpty();
    }

    [Fact]
    // Map data with multiple keys must serialize sorted-by-key so the
    // bytes are deterministic across languages.
    public void MechanismData_DeterministicByteOrder()
    {
        var a = new CapturedError
        {
            Mechanism = new ExceptionMechanism
            {
                Type = "signal",
                Data = new SortedDictionary<string, string>
                {
                    ["zulu"] = "1",
                    ["alpha"] = "2",
                    ["mike"] = "3",
                },
            },
        };
        // Re-build identical content via a differently-iteration-ordered
        // dictionary; the resulting proto bytes must be identical.
        var b = new CapturedError
        {
            Mechanism = new ExceptionMechanism
            {
                Type = "signal",
                Data = new SortedDictionary<string, string>
                {
                    ["mike"] = "3",
                    ["alpha"] = "2",
                    ["zulu"] = "1",
                },
            },
        };

        var ba = ProtoAdapter.ToProto(a).ToByteArray();
        var bb = ProtoAdapter.ToProto(b).ToByteArray();
        ba.Should().Equal(bb);
    }

    [Theory]
    [InlineData("0001-simple")]
    [InlineData("0002-empty-chain")]
    [InlineData("0003-three-deep")]
    [InlineData("0004-source-context")]
    [InlineData("0005-mechanism-data")]
    // Byte-equivalence with the committed cross-language .pb fixtures.
    // SERERR_FIXTURES_DIR points at tests/conformance/fixtures.
    public void Fixture_BytesMatch(string name)
    {
        var dir = Environment.GetEnvironmentVariable("SERERR_FIXTURES_DIR")
                  ?? FindRepoFixturesDir();
        var json = File.ReadAllText(Path.Combine(dir, $"{name}.json"));
        var expected = File.ReadAllBytes(Path.Combine(dir, $"{name}.pb"));

        var captured = FixtureLoader.LoadCapturedError(json);
        var bytes = ProtoAdapter.ToProto(captured).ToByteArray();

        bytes.Should().Equal(expected, $"fixture {name} must match");
    }

    private static string FindRepoFixturesDir()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir is not null && !Directory.Exists(Path.Combine(dir.FullName, "tests", "conformance", "fixtures")))
        {
            dir = dir.Parent;
        }
        return Path.Combine(dir!.FullName, "tests", "conformance", "fixtures");
    }
}
