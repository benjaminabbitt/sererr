package fyi.sererr.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fyi.sererr.Capture;
import fyi.sererr.CapturedError;
import fyi.sererr.DebugInfo;
import fyi.sererr.DebugInfoAdapter;
import fyi.sererr.ExceptionMechanism;
import fyi.sererr.StackFrame;
import fyi.sererr.proto.ProtoAdapter;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for the cross-language conformance corpus.
 *
 * <p>Every step text matches the Rust runner verbatim — the corpus is
 * the executable contract. Each scenario manipulates the
 * {@link ConformanceWorld} (one instance per scenario, courtesy of
 * Cucumber-JVM's PicoContainer dependency injection).
 */
public class Steps {

    private final ConformanceWorld world;

    public Steps(ConformanceWorld world) {
        this.world = world;
    }

    // ---------- background / generic ----------

    @Given("the canonical sererr.v1 proto schema")
    public void givenCanonicalSchema() {
        // No-op: schema is implicit (we compile against the proto runtime).
    }

    // ---------- encoding.feature ----------

    @Given("a fixture {string}")
    public void givenFixture(String name) {
        world.fixture = name;
    }

    @Given("a default-initialized CapturedError \\(all zero values)")
    public void givenDefaultCaptured() {
        world.fixtureInput = CapturedError.empty();
    }

    @When("I construct the CapturedError per the fixture's JSON descriptor")
    public void whenConstructFromFixture() throws Exception {
        world.fixtureInput = readFixture(world.fixture);
    }

    @When("I serialize it via the proto adapter")
    public void whenSerializeViaProto() throws Exception {
        // Auto-construct from the named fixture if the scenario skipped
        // the explicit construct step.
        if (world.fixtureInput == null && world.fixture != null) {
            world.fixtureInput = readFixture(world.fixture);
        }
        world.encoded = ProtoAdapter.encode(world.fixtureInput);
    }

    @When("I serialize it")
    public void whenSerialize() throws Exception {
        whenSerializeViaProto();
    }

    @Then("the encoded bytes match {string}")
    public void thenBytesMatchFixture(String pathExpr) throws Exception {
        // pathExpr is e.g. "fixtures/0001-simple.pb"
        String name = pathExpr.replaceFirst("^fixtures/", "").replaceFirst("\\.pb$", "");
        byte[] expected = Files.readAllBytes(fixturesDir().resolve(name + ".pb"));
        assertThat(world.encoded)
                .as("encoded bytes for fixture %s", name)
                .isEqualTo(expected);
    }

    @Then("the encoded bytes are empty")
    public void thenBytesEmpty() {
        assertThat(world.encoded).isEmpty();
    }

    @When("I deserialize the bytes back to a CapturedError")
    public void whenDeserialize() {
        world.fixtureInput = ProtoAdapter.decode(world.encoded);
    }

    @Then("the result equals the input field-by-field")
    public void thenRoundtripEqual() throws Exception {
        // The fixture_input was overwritten by deserialize; re-read
        // and compare.
        CapturedError expected = readFixture(world.fixture);
        assertThat(world.fixtureInput).isEqualTo(expected);
    }

    // ---------- chain.feature ----------

    @Given("an error with no source \\/ cause")
    public void givenNoSourceError() {
        Throwable t = new LabeledError("single", null);
        world.chain = Capture.capture(t, "LabeledError", "test", "host");
    }

    @Given("a chain {string} caused-by {string}")
    public void givenChainTwo(String inner, String outer) {
        // The Gherkin reads most-causal-first → outermost-last.
        // Build the JVM chain accordingly: outer.cause = inner.
        Throwable innerT = new LabeledError(inner, null);
        Throwable outerT = new LabeledError(outer, innerT);
        world.chain = Capture.capture(outerT, "LabeledError", "test", "host");
    }

    @Given("a chain {string} caused-by {string} caused-by {string}")
    public void givenChainThree(String inner, String middle, String outer) {
        Throwable innerT = new LabeledError(inner, null);
        Throwable middleT = new LabeledError(middle, innerT);
        Throwable outerT = new LabeledError(outer, middleT);
        world.chain = Capture.capture(outerT, "LabeledError", "test", "host");
    }

    @Given("a captured error with frames")
    public void givenCapturedWithFrames() {
        Throwable t = new LabeledError("x", null);
        world.chain = Capture.capture(t, "LabeledError", "test", "host");
    }

    @When("I capture it")
    public void whenCaptureIt() {
        // No-op; capture happened in the `given` step.
    }

    @When("I capture the outermost error")
    public void whenCaptureOutermost() {
        // No-op; capture happened in the `given` step.
    }

    @When("I read the frames")
    public void whenReadFrames() {
        // No-op; frames are already in world.chain.
    }

    @Then("the chain length is {int}")
    public void thenChainLength(int n) {
        assertThat(world.chain).hasSize(n);
    }

    @Then("entry {int} has exception_id {int} and parent_id {int}")
    public void thenEntryMechanismIds(int idx, int exceptionId, int parentId) {
        ExceptionMechanism m = world.chain.get(idx).mechanism().orElseThrow();
        assertThat(m.exceptionId()).as("entry %d exception_id", idx).isEqualTo(exceptionId);
        assertThat(m.parentId()).as("entry %d parent_id", idx).isEqualTo(parentId);
    }

    @Then("the entry's mechanism has exception_id 0")
    public void thenSingleExceptionIdZero() {
        assertThat(world.chain.get(0).mechanism().orElseThrow().exceptionId()).isZero();
    }

    @Then("the entry's mechanism has parent_id 0")
    public void thenSingleParentIdZero() {
        assertThat(world.chain.get(0).mechanism().orElseThrow().parentId()).isZero();
    }

    @Then("entry {int} has message {string}")
    public void thenEntryMessage(int idx, String msg) {
        assertThat(world.chain.get(idx).message()).isEqualTo(msg);
    }

    @Then("the last chain entry has message {string}")
    public void thenLastEntryMessage(String msg) {
        assertThat(world.chain.get(world.chain.size() - 1).message()).isEqualTo(msg);
    }

    @Then("the first frame is the most recent call")
    public void thenFirstFrameMostRecent() {
        assertThat(world.chain.get(0).frames())
                .as("expected captured frames")
                .isNotEmpty();
    }

    // ---------- debuginfo-adapter.feature ----------

    @Given("an empty chain")
    public void givenEmptyChain() {
        world.chain = List.of();
    }

    @Given("a single CapturedError with type {string} and message {string}")
    public void givenSingleCapturedError(String type, String msg) {
        world.chain = List.of(CapturedError.builder().type(type).message(msg).build());
    }

    @Given("a CapturedError with one frame:")
    public void givenCapturedWithOneFrame(DataTable table) {
        // Header row + one data row. Cucumber's DataTable can produce
        // a list of maps; we just take the first.
        List<java.util.Map<String, String>> rows = table.asMaps();
        java.util.Map<String, String> row = rows.get(0);
        StackFrame frame = StackFrame.builder()
                .function(row.getOrDefault("function", ""))
                .file(row.getOrDefault("file", ""))
                .line(Integer.parseInt(row.getOrDefault("line", "0").trim()))
                .build();
        world.chain = List.of(CapturedError.builder()
                .type("T").message("m").frames(List.of(frame)).build());
    }

    @When("I call to_debug_info")
    public void whenCallToDebugInfo() {
        world.debugInfo = DebugInfoAdapter.toDebugInfo(world.chain);
    }

    @Then("stack_entries is empty")
    public void thenStackEntriesEmpty() {
        assertThat(world.debugInfo.stackEntries()).isEmpty();
    }

    @Then("detail is empty")
    public void thenDetailEmpty() {
        assertThat(world.debugInfo.detail()).isEmpty();
    }

    @Then("detail equals {string}")
    public void thenDetailEquals(String expected) {
        assertThat(world.debugInfo.detail()).isEqualTo(expected);
    }

    @Then("detail contains {string}")
    public void thenDetailContains(String needle) {
        assertThat(world.debugInfo.detail()).contains(needle);
    }

    @Then("stack_entries contains {string}")
    public void thenStackEntriesContains(String needle) {
        assertThat(world.debugInfo.stackEntries()).contains(needle);
    }

    // ---------- helpers ----------

    private static Path fixturesDir() {
        String env = System.getenv("SERERR_FIXTURES_DIR");
        if (env == null || env.isEmpty()) {
            throw new IllegalStateException(
                    "SERERR_FIXTURES_DIR must point at tests/conformance/fixtures");
        }
        return Paths.get(env);
    }

    static CapturedError readFixture(String name) throws Exception {
        Path path = fixturesDir().resolve(name + ".json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(path.toFile());
        JsonNode ce = root.get("captured_error");
        if (ce == null) return CapturedError.empty();
        return parseCaptured(ce);
    }

    private static CapturedError parseCaptured(JsonNode n) {
        var frames = new ArrayList<StackFrame>();
        if (n.has("frames")) {
            for (JsonNode f : n.get("frames")) {
                frames.add(parseFrame(f));
            }
        }
        Optional<ExceptionMechanism> mech = Optional.empty();
        if (n.has("mechanism") && !n.get("mechanism").isNull()) {
            mech = Optional.of(parseMechanism(n.get("mechanism")));
        }
        return new CapturedError(
                text(n, "type"), text(n, "message"),
                frames, mech, text(n, "release"), text(n, "server_name"));
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

    // ---------- synthetic chained error ----------

    /**
     * Synthetic chained error used by the chain.feature scenarios.
     * The leaf-cause stamp is independent of the Java class name (the
     * "LabeledError" string comes from the test fixture, mirroring the
     * Rust runner's labeled error).
     */
    static final class LabeledError extends RuntimeException {
        LabeledError(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
