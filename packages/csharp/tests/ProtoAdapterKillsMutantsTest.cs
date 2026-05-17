// Mutation-kill tests for ProtoAdapter surviving mutants:
//   - L133 Conditional (false) on `c.Mechanism is null ? null : FromProto(...)`
//
// The mutant `(false ? null : FromProto(c.Mechanism))` would dereference a
// null Mechanism (NRE or malformed). We pin that a CapturedError with
// Mechanism = null round-trips with Mechanism still null.

using FluentAssertions;
using Sererr;
using Sererr.Proto;
using Google.Protobuf;
using Xunit;

namespace Sererr.Tests;

public class ProtoAdapterKillsMutantsTest
{
    [Fact]
    // Why: kills L133 Conditional (false) mutation. With Mechanism = null
    // in the proto representation, the original FromProto returns
    // CapturedError with Mechanism = null; the mutant attempts
    // FromProto(null) which throws or yields a non-null mechanism.
    public void FromProto_NullMechanism_RoundTripsAsNull()
    {
        var input = new CapturedError
        {
            Type = "T", Message = "m",
            // No mechanism — exercise the null branch of the ternary.
            Mechanism = null,
            Release = "r", ServerName = "s",
        };

        var proto = ProtoAdapter.ToProto(input);
        var bytes = proto.ToByteArray();
        var decoded = Sererr.V1.CapturedError.Parser.ParseFrom(bytes);
        var roundtripped = ProtoAdapter.FromProto(decoded);

        roundtripped.Mechanism.Should().BeNull(
            "a captured error without mechanism must round-trip with Mechanism=null");
        roundtripped.Should().Be(input);
    }
}
