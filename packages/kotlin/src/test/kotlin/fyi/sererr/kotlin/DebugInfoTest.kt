package fyi.sererr.kotlin

import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import fyi.sererr.SourceProvider

/**
 * Kotlin surface contract for `toDebugInfo` (both the @JvmName
 * top-level variant and the [List] extension) and for
 * [populateContext]. The semantic guarantees are inherited from the
 * Java `DebugInfoAdapter` and `SourceContext` -- these tests pin the
 * Kotlin delegation and default-argument behaviour.
 */
class DebugInfoTest {

    private fun entry(type: String, message: String): CapturedError =
        fyi.sererr.CapturedError.builder()
            .type(type)
            .message(message)
            .build()

    // -- toDebugInfo (top-level @JvmName variant) -------------------------

    /**
     * The @JvmName-renamed top-level `toDebugInfo(chain)` delegates to
     * `DebugInfoAdapter.toDebugInfo` and produces the "type: message"
     * detail for a single entry.
     *
     * Kills the line-11 VoidMethodCallMutator (removed call to
     * `checkNotNullExpressionValue` on the result) only indirectly --
     * the meaningful kill comes from asserting the EXACT detail string
     * and stack-entries shape.
     */
    @Test
    fun topLevelToDebugInfoFromChain() {
        val chain = listOf(entry("E", "m"))
        val di = toDebugInfo(chain)
        assertEquals("E: m", di.detail())
        assertEquals(listOf("E: m"), di.stackEntries())
    }

    /**
     * Top-level `toDebugInfo(empty)` returns an empty DebugInfo.
     * Pins the empty-chain branch of the Java adapter through the
     * Kotlin delegate.
     */
    @Test
    fun topLevelToDebugInfoEmpty() {
        val di = toDebugInfo(emptyList())
        assertEquals("", di.detail())
        assertEquals(emptyList(), di.stackEntries())
    }

    /**
     * Top-level `toDebugInfo` for a multi-entry chain renders the
     * outermost-first "Caused by" cascade.
     */
    @Test
    fun topLevelToDebugInfoChainCascade() {
        val chain = listOf(entry("A", "a"), entry("B", "b"))
        val di = toDebugInfo(chain)
        // chain is most-causal-first; output is outermost-first.
        assertEquals("B: b\nCaused by: A: a", di.detail())
    }

    // -- List<CapturedError>.toDebugInfo() extension ----------------------

    /**
     * The extension form produces the same DebugInfo as the top-level
     * form. Kills the line-15 VoidMethodCallMutator via observable
     * output rather than the receiver-null check.
     */
    @Test
    fun extensionToDebugInfoParityWithTopLevel() {
        val chain = listOf(entry("E", "m"))
        val viaExt = chain.toDebugInfo()
        val viaTop = toDebugInfo(chain)
        assertEquals(viaTop.detail(), viaExt.detail())
        assertEquals(viaTop.stackEntries(), viaExt.stackEntries())
    }

    /** Empty receiver → empty DebugInfo. */
    @Test
    fun extensionToDebugInfoEmpty() {
        val di = emptyList<CapturedError>().toDebugInfo()
        assertEquals("", di.detail())
        assertEquals(emptyList(), di.stackEntries())
    }

    /**
     * Extension on a multi-entry chain renders the cascade verbatim.
     */
    @Test
    fun extensionToDebugInfoChainCascade() {
        val chain = listOf(entry("A", "a"), entry("B", "b"), entry("C", "c"))
        val di = chain.toDebugInfo()
        assertEquals("C: c\nCaused by: B: b\nCaused by: A: a", di.detail())
    }

    // -- populateContext --------------------------------------------------

    /**
     * `populateContext` on a frame with `line == 0` is a no-op:
     * returns the same frame unchanged.
     *
     * Pins the early-return branch of the Java helper through the
     * Kotlin delegate.
     */
    @Test
    fun populateContextLineZeroNoOp() {
        val frame = StackFrame.builder()
            .function("f")
            .file("src.kt")
            .line(0)
            .build()
        val provider = SourceProvider { Optional.of("line1\nline2\nline3") }
        val out = populateContext(frame, provider)
        assertEquals(0, out.line())
        assertEquals("", out.contextLine())
    }

    /**
     * `populateContext` with the default surrounding (5) pulls the
     * configured number of preceding lines into preContext.
     *
     * Kills mutants on the default-argument-default path: a mutant
     * that swapped the default value would change the size of
     * preContext/postContext.
     */
    @Test
    fun populateContextDefaultSurroundingIsFive() {
        val src = (1..20).joinToString("\n") { "line$it" }
        val frame = StackFrame.builder()
            .function("f")
            .file("src.kt")
            .line(10)
            .build()
        val provider = SourceProvider { Optional.of(src) }
        val out = populateContext(frame, provider)
        assertEquals("line10", out.contextLine())
        // 5 lines before line 10: line5..line9.
        assertEquals(5, out.preContext().size)
        assertEquals("line5", out.preContext()[0])
        assertEquals("line9", out.preContext()[4])
        // 5 lines after line 10: line11..line15.
        assertEquals(5, out.postContext().size)
        assertEquals("line11", out.postContext()[0])
        assertEquals("line15", out.postContext()[4])
    }

    /**
     * Explicit `surrounding` parameter overrides the default.
     */
    @Test
    fun populateContextExplicitSurrounding() {
        val src = (1..20).joinToString("\n") { "line$it" }
        val frame = StackFrame.builder()
            .function("f")
            .file("src.kt")
            .line(10)
            .build()
        val provider = SourceProvider { Optional.of(src) }
        val out = populateContext(frame, provider, surrounding = 2)
        assertEquals("line10", out.contextLine())
        assertEquals(2, out.preContext().size)
        assertEquals(2, out.postContext().size)
    }

    /**
     * Provider returning `Optional.empty()` → frame returned unchanged.
     */
    @Test
    fun populateContextNoSourceNoOp() {
        val frame = StackFrame.builder()
            .function("f")
            .file("missing.kt")
            .line(3)
            .build()
        val provider = SourceProvider { Optional.empty() }
        val out = populateContext(frame, provider)
        assertEquals("", out.contextLine())
        assertTrue(out.preContext().isEmpty())
        assertTrue(out.postContext().isEmpty())
    }
}
