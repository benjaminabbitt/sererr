package fyi.sererr.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Kotlin surface contract for `capture` / `toCapturedErrorChain`.
 *
 * The semantic guarantees are inherited from the Java implementation
 * — these tests pin the Kotlin-idiomatic shape and the type-alias
 * re-exports.
 */
class CaptureTest {

    /** Top-level `capture(throwable, ...)` delegates to the Java impl. */
    @Test
    fun topLevelCapture() {
        val e = RuntimeException("solo")
        val chain = capture(e, "RuntimeException", "v1", "host")
        assertEquals(1, chain.size)
        val mech = chain[0].mechanism().orElseThrow()
        assertEquals(0, mech.exceptionId())
        assertEquals(0, mech.parentId())
    }

    /** Extension `Throwable.toCapturedErrorChain` produces the same shape. */
    @Test
    fun extensionFunction() {
        val inner = RuntimeException("inner")
        val outer = RuntimeException("outer", inner)
        val chain = outer.toCapturedErrorChain("RuntimeException", "v1", "host")
        assertEquals(2, chain.size)
        // most-causal-first: inner is at [0], outer at [1]
        assertEquals("inner", chain[0].message())
        assertEquals("outer", chain[1].message())
    }

    /** Frames from a captured error survive coroutine sanitization. */
    @Test
    fun framesNonEmpty() {
        val e = RuntimeException("with-frames")
        val chain = capture(e, "RuntimeException", "v1", "host")
        assertTrue(chain[0].frames().isNotEmpty(), "expected non-empty frames")
    }

    /** Type aliases point at the Java records (object identity check). */
    @Test
    fun typeAliasesPointAtJavaRecords() {
        val ce: CapturedError = fyi.sererr.CapturedError.empty()
        assertNotNull(ce)
        val sf: StackFrame = fyi.sererr.StackFrame.empty()
        assertNotNull(sf)
        val em: ExceptionMechanism = fyi.sererr.ExceptionMechanism.empty()
        assertNotNull(em)
    }

    /** Coroutine frame recovery is a safe no-op on plain Throwables. */
    @Test
    fun coroutineRecoveryNoOp() {
        val e = RuntimeException("plain")
        val recovered = recoverCoroutineFrames(e)
        // Same throwable returned (recovery sanitizes in place).
        assertTrue(recovered === e, "expected same throwable instance")
    }

    /** Null throwable in Java capture is empty list; Kotlin function is non-null Throwable so we just test via java. */
    @Test
    fun emptyChainOnNullThrowable() {
        // Kotlin's `capture` requires non-null, so we test the Java path
        // directly to confirm parity.
        val chain = fyi.sererr.Capture.capture(null, "T", "v", "h")
        assertEquals(0, chain.size)
    }

    /** DebugInfo extension matches the Java adapter output. */
    @Test
    fun debugInfoExtension() {
        val entry = fyi.sererr.CapturedError.builder()
            .type("E").message("m").build()
        val di = listOf(entry).toDebugInfo()
        assertEquals("E: m", di.detail())
    }

    /** Capture on a throwable with explicit cause chain. */
    @Test
    fun chainedThrowable() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        val c = RuntimeException("c", b)
        val chain = capture(c, "RuntimeException", "v1", "host")
        assertEquals(3, chain.size)
        assertEquals("a", chain[0].message())
        assertEquals("c", chain[2].message())
    }

    /** Java's IllegalStateException can be initCaused — Kotlin smoke test. */
    @Test
    fun initCauseAllowedAtConstruction() {
        // Kotlin doesn't impose extra restrictions; we just confirm the
        // capture surface accepts JVM-standard chained throwables.
        assertFailsWith<IllegalStateException> {
            error("by-design")
        }.also {
            val chain = it.toCapturedErrorChain("Test", "v1", "host")
            assertEquals(1, chain.size)
            assertEquals("by-design", chain[0].message())
        }
    }
}
