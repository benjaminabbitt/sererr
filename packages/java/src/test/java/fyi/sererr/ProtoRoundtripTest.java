package fyi.sererr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fyi.sererr.proto.ProtoAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proto adapter: deterministic encoding must match cross-language
 * fixture bytes byte-for-byte.
 *
 * <p>Enabled only when {@code SERERR_FIXTURES_DIR} is set; otherwise
 * the test would have nothing to assert against. The conformance
 * runner sets this; the package-level test can also have it set
 * explicitly via Gradle.
 */
class ProtoRoundtripTest {

    /** Roundtrip: encode then decode a CapturedError; result equals input. */
    @Test
    void encodeDecodeRoundtrip() {
        TreeMap<String, String> data = new TreeMap<>();
        data.put("a", "1");
        data.put("b", "2");
        var mech = ExceptionMechanism.builder()
                .type("generic").handled(true).exceptionId(0).parentId(0).data(data).build();
        var frame = StackFrame.builder()
                .function("f").file("a.java").line(10).inApp(true).build();
        var input = CapturedError.builder()
                .type("MyError").message("oops")
                .frames(List.of(frame))
                .mechanism(mech)
                .release("v1.0.0").serverName("host-1")
                .build();
        byte[] bytes = ProtoAdapter.encode(input);
        CapturedError decoded = ProtoAdapter.decode(bytes);
        assertThat(decoded).isEqualTo(input);
    }

    /** All-zero CapturedError encodes to zero bytes. */
    @Test
    void zeroValueEncodesEmpty() {
        byte[] bytes = ProtoAdapter.encode(CapturedError.empty());
        assertThat(bytes).isEmpty();
    }

    /** Deterministic serialization: same input → same bytes every time. */
    @Test
    void deterministicSerialization() {
        TreeMap<String, String> data = new TreeMap<>();
        data.put("z", "1");
        data.put("a", "2");
        data.put("m", "3");
        var mech = ExceptionMechanism.builder().type("signal").data(data).build();
        var input = CapturedError.builder().type("E").message("m").mechanism(mech).build();
        byte[] one = ProtoAdapter.encode(input);
        byte[] two = ProtoAdapter.encode(input);
        assertThat(one).isEqualTo(two);
    }

    /** Byte-equivalence with the committed fixture .pb files. */
    @Test
    @EnabledIfEnvironmentVariable(named = "SERERR_FIXTURES_DIR", matches = ".+")
    void fixturesEncodeToCanonicalBytes() throws Exception {
        Path dir = Paths.get(System.getenv("SERERR_FIXTURES_DIR"));
        String[] names = {
                "0001-simple", "0002-empty-chain", "0003-three-deep",
                "0004-source-context", "0005-mechanism-data"
        };
        ObjectMapper mapper = new ObjectMapper();
        for (String name : names) {
            JsonNode root = mapper.readTree(dir.resolve(name + ".json").toFile());
            CapturedError input = parseFixture(root.get("captured_error"));
            byte[] actual = ProtoAdapter.encode(input);
            byte[] expected = Files.readAllBytes(dir.resolve(name + ".pb"));
            assertThat(actual).as("fixture %s", name).isEqualTo(expected);
        }
    }

    /** Build a CapturedError from a fixture JSON descriptor node. */
    static CapturedError parseFixture(JsonNode n) {
        if (n == null) return CapturedError.empty();
        var frames = new ArrayList<StackFrame>();
        JsonNode framesNode = n.get("frames");
        if (framesNode != null) {
            for (JsonNode f : framesNode) {
                frames.add(parseFrame(f));
            }
        }
        Optional<ExceptionMechanism> mech = Optional.empty();
        if (n.has("mechanism") && !n.get("mechanism").isNull()) {
            mech = Optional.of(parseMechanism(n.get("mechanism")));
        }
        return new CapturedError(
                text(n, "type"), text(n, "message"),
                frames, mech,
                text(n, "release"), text(n, "server_name"));
    }

    private static StackFrame parseFrame(JsonNode f) {
        List<String> pre = stringList(f.get("pre_context"));
        List<String> post = stringList(f.get("post_context"));
        return new StackFrame(
                text(f, "function"), text(f, "module"), text(f, "package"),
                text(f, "file"), text(f, "abs_path"),
                f.hasNonNull("line") ? f.get("line").asInt(0) : 0,
                text(f, "context_line"), pre, post,
                text(f, "source_link"),
                f.hasNonNull("in_app") && f.get("in_app").asBoolean(false));
    }

    private static ExceptionMechanism parseMechanism(JsonNode m) {
        TreeMap<String, String> data = new TreeMap<>();
        if (m.has("data")) {
            JsonNode d = m.get("data");
            d.fieldNames().forEachRemaining(k -> data.put(k, d.get(k).asText("")));
        }
        return new ExceptionMechanism(
                text(m, "type"), text(m, "description"),
                m.hasNonNull("handled") && m.get("handled").asBoolean(false),
                m.hasNonNull("synthetic") && m.get("synthetic").asBoolean(false),
                text(m, "help_link"), text(m, "source"),
                m.hasNonNull("exception_id") ? m.get("exception_id").asInt(0) : 0,
                m.hasNonNull("parent_id") ? m.get("parent_id").asInt(0) : 0,
                m.hasNonNull("is_exception_group") && m.get("is_exception_group").asBoolean(false),
                data);
    }

    private static String text(JsonNode n, String key) {
        if (n == null || !n.hasNonNull(key)) return "";
        return n.get(key).asText("");
    }

    private static List<String> stringList(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        var out = new ArrayList<String>(node.size());
        for (JsonNode el : node) out.add(el.asText(""));
        return out;
    }
}
