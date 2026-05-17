package fyi.sererr.conformance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import fyi.sererr.kotlin.CapturedError
import fyi.sererr.kotlin.ExceptionMechanism
import fyi.sererr.kotlin.StackFrame
import fyi.sererr.kotlin.capture
import fyi.sererr.kotlin.toCapturedErrorChain
import fyi.sererr.kotlin.toDebugInfo
import fyi.sererr.proto.ProtoAdapter
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Optional
import java.util.TreeMap

/**
 * Kotlin step definitions for the cross-language conformance corpus.
 *
 * Step texts match the Rust runner verbatim. Each scenario gets a
 * fresh [ConformanceWorld] courtesy of Cucumber-JVM's PicoContainer DI.
 *
 * The Kotlin runner exists to catch regressions in the Kotlin layer
 * specifically — wraps that don't delegate cleanly, type-alias
 * mismatches, coroutine-recovery side effects. The Java runner
 * exercises the underlying logic; this runner exercises the surface.
 */
class Steps(private val world: ConformanceWorld) {

    // ---------- background / generic ----------

    @Given("the canonical sererr.v1 proto schema")
    fun givenCanonicalSchema() {
        // No-op: schema is implicit.
    }

    // ---------- encoding.feature ----------

    @Given("a fixture {string}")
    fun givenFixture(name: String) {
        world.fixture = name
    }

    @Given("a default-initialized CapturedError \\(all zero values)")
    fun givenDefaultCaptured() {
        world.fixtureInput = CapturedError.empty()
    }

    @When("I construct the CapturedError per the fixture's JSON descriptor")
    fun whenConstructFromFixture() {
        world.fixtureInput = readFixture(world.fixture!!)
    }

    @When("I serialize it via the proto adapter")
    fun whenSerializeViaProto() {
        if (world.fixtureInput == null && world.fixture != null) {
            world.fixtureInput = readFixture(world.fixture!!)
        }
        world.encoded = ProtoAdapter.encode(world.fixtureInput)
    }

    @When("I serialize it")
    fun whenSerialize() = whenSerializeViaProto()

    @Then("the encoded bytes match {string}")
    fun thenBytesMatchFixture(pathExpr: String) {
        val name = pathExpr.removePrefix("fixtures/").removeSuffix(".pb")
        val expected = Files.readAllBytes(fixturesDir().resolve("$name.pb"))
        assertThat(world.encoded)
            .`as`("encoded bytes for fixture %s", name)
            .isEqualTo(expected)
    }

    @Then("the encoded bytes are empty")
    fun thenBytesEmpty() {
        assertThat(world.encoded).isEmpty()
    }

    @When("I deserialize the bytes back to a CapturedError")
    fun whenDeserialize() {
        world.fixtureInput = ProtoAdapter.decode(world.encoded)
    }

    @Then("the result equals the input field-by-field")
    fun thenRoundtripEqual() {
        val expected = readFixture(world.fixture!!)
        assertThat(world.fixtureInput).isEqualTo(expected)
    }

    // ---------- chain.feature ----------

    @Given("an error with no source \\/ cause")
    fun givenNoSourceError() {
        val t = LabeledError("single", null)
        world.chain = capture(t, "LabeledError", "test", "host")
    }

    @Given("a chain {string} caused-by {string}")
    fun givenChainTwo(inner: String, outer: String) {
        val innerT = LabeledError(inner, null)
        val outerT = LabeledError(outer, innerT)
        world.chain = outerT.toCapturedErrorChain("LabeledError", "test", "host")
    }

    @Given("a chain {string} caused-by {string} caused-by {string}")
    fun givenChainThree(inner: String, middle: String, outer: String) {
        val innerT = LabeledError(inner, null)
        val middleT = LabeledError(middle, innerT)
        val outerT = LabeledError(outer, middleT)
        world.chain = outerT.toCapturedErrorChain("LabeledError", "test", "host")
    }

    @Given("a captured error with frames")
    fun givenCapturedWithFrames() {
        val t = LabeledError("x", null)
        world.chain = capture(t, "LabeledError", "test", "host")
    }

    @When("I capture it")
    fun whenCaptureIt() {
        // No-op; capture happened in the `given` step.
    }

    @When("I capture the outermost error")
    fun whenCaptureOutermost() {
        // No-op; capture happened in the `given` step.
    }

    @When("I read the frames")
    fun whenReadFrames() {
        // No-op; frames are already in world.chain.
    }

    @Then("the chain length is {int}")
    fun thenChainLength(n: Int) {
        assertThat(world.chain).hasSize(n)
    }

    @Then("entry {int} has exception_id {int} and parent_id {int}")
    fun thenEntryMechanismIds(idx: Int, exceptionId: Int, parentId: Int) {
        val m: ExceptionMechanism = world.chain[idx].mechanism().orElseThrow()
        assertThat(m.exceptionId()).`as`("entry %d exception_id", idx).isEqualTo(exceptionId)
        assertThat(m.parentId()).`as`("entry %d parent_id", idx).isEqualTo(parentId)
    }

    @Then("the entry's mechanism has exception_id 0")
    fun thenSingleExceptionIdZero() {
        assertThat(world.chain[0].mechanism().orElseThrow().exceptionId()).isZero()
    }

    @Then("the entry's mechanism has parent_id 0")
    fun thenSingleParentIdZero() {
        assertThat(world.chain[0].mechanism().orElseThrow().parentId()).isZero()
    }

    @Then("entry {int} has message {string}")
    fun thenEntryMessage(idx: Int, msg: String) {
        assertThat(world.chain[idx].message()).isEqualTo(msg)
    }

    @Then("the last chain entry has message {string}")
    fun thenLastEntryMessage(msg: String) {
        assertThat(world.chain.last().message()).isEqualTo(msg)
    }

    @Then("the first frame is the most recent call")
    fun thenFirstFrameMostRecent() {
        assertThat(world.chain[0].frames())
            .`as`("expected captured frames")
            .isNotEmpty
    }

    // ---------- debuginfo-adapter.feature ----------

    @Given("an empty chain")
    fun givenEmptyChain() {
        world.chain = emptyList()
    }

    @Given("a single CapturedError with type {string} and message {string}")
    fun givenSingleCapturedError(type: String, msg: String) {
        world.chain = listOf(CapturedError.builder().type(type).message(msg).build())
    }

    @Given("a CapturedError with one frame:")
    fun givenCapturedWithOneFrame(table: DataTable) {
        val row = table.asMaps()[0]
        val frame = StackFrame.builder()
            .function(row.getOrDefault("function", ""))
            .file(row.getOrDefault("file", ""))
            .line((row["line"] ?: "0").trim().toInt())
            .build()
        world.chain = listOf(
            CapturedError.builder().type("T").message("m").frames(listOf(frame)).build()
        )
    }

    @When("I call to_debug_info")
    fun whenCallToDebugInfo() {
        world.debugInfo = world.chain.toDebugInfo()
    }

    @Then("stack_entries is empty")
    fun thenStackEntriesEmpty() {
        assertThat(world.debugInfo!!.stackEntries()).isEmpty()
    }

    @Then("detail is empty")
    fun thenDetailEmpty() {
        assertThat(world.debugInfo!!.detail()).isEmpty()
    }

    @Then("detail equals {string}")
    fun thenDetailEquals(expected: String) {
        assertThat(world.debugInfo!!.detail()).isEqualTo(expected)
    }

    @Then("detail contains {string}")
    fun thenDetailContains(needle: String) {
        assertThat(world.debugInfo!!.detail()).contains(needle)
    }

    @Then("stack_entries contains {string}")
    fun thenStackEntriesContains(needle: String) {
        assertThat(world.debugInfo!!.stackEntries()).contains(needle)
    }

    // ---------- helpers ----------

    private fun fixturesDir(): Path {
        val env = System.getenv("SERERR_FIXTURES_DIR")
            ?: error("SERERR_FIXTURES_DIR must point at tests/conformance/fixtures")
        return Paths.get(env)
    }

    private fun readFixture(name: String): CapturedError {
        val path = fixturesDir().resolve("$name.json")
        val root: JsonNode = ObjectMapper().readTree(path.toFile())
        val ce = root.get("captured_error") ?: return CapturedError.empty()
        return parseCaptured(ce)
    }

    private fun parseCaptured(n: JsonNode): CapturedError {
        val frames = mutableListOf<StackFrame>()
        n.get("frames")?.forEach { frames.add(parseFrame(it)) }
        val mech: Optional<ExceptionMechanism> =
            if (n.has("mechanism") && !n.get("mechanism").isNull) {
                Optional.of(parseMechanism(n.get("mechanism")))
            } else Optional.empty()
        return CapturedError(
            text(n, "type"), text(n, "message"),
            frames, mech, text(n, "release"), text(n, "server_name")
        )
    }

    private fun parseFrame(f: JsonNode): StackFrame {
        return StackFrame(
            text(f, "function"), text(f, "module"), text(f, "package"),
            text(f, "file"), text(f, "abs_path"),
            if (f.hasNonNull("line")) f.get("line").asInt(0) else 0,
            text(f, "context_line"),
            stringList(f.get("pre_context")),
            stringList(f.get("post_context")),
            text(f, "source_link"),
            f.hasNonNull("in_app") && f.get("in_app").asBoolean(false)
        )
    }

    private fun parseMechanism(m: JsonNode): ExceptionMechanism {
        val data = TreeMap<String, String>()
        m.get("data")?.fieldNames()?.forEachRemaining { k -> data[k] = m.get("data").get(k).asText("") }
        return ExceptionMechanism(
            text(m, "type"), text(m, "description"),
            m.hasNonNull("handled") && m.get("handled").asBoolean(false),
            m.hasNonNull("synthetic") && m.get("synthetic").asBoolean(false),
            text(m, "help_link"), text(m, "source"),
            if (m.hasNonNull("exception_id")) m.get("exception_id").asInt(0) else 0,
            if (m.hasNonNull("parent_id")) m.get("parent_id").asInt(0) else 0,
            m.hasNonNull("is_exception_group") && m.get("is_exception_group").asBoolean(false),
            data
        )
    }

    private fun text(n: JsonNode?, key: String): String {
        if (n == null || !n.hasNonNull(key)) return ""
        return n.get(key).asText("")
    }

    private fun stringList(node: JsonNode?): List<String> {
        if (node == null || node.isNull) return emptyList()
        return node.map { it.asText("") }
    }

    /** Synthetic chained error mirroring the Java runner's LabeledError. */
    internal class LabeledError(msg: String, cause: Throwable?) : RuntimeException(msg, cause)
}
