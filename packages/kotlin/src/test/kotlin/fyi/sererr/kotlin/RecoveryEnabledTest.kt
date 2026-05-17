package fyi.sererr.kotlin

import org.junit.jupiter.api.Assumptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests that enable the kotlinx-coroutines stack-trace-recovery mode
 * BEFORE `kotlinx.coroutines.DebugKt` is first loaded.
 *
 * `kotlinx.coroutines.DebugKt.RECOVER_STACK_TRACES` is a `static final`
 * boolean computed in the class's static initializer from the system
 * properties `kotlinx.coroutines.debug` and
 * `kotlinx.coroutines.stacktrace.recovery`. Setting both BEFORE the
 * class is first referenced (by anyone in the JVM) causes
 * `recoverStackTrace` to actually copy the throwable and sanitize its
 * stack trace -- which gives us observable behaviour differences for
 * mutants on the reflective `recoverCoroutineFrames` path.
 *
 * We set the properties in a static initializer of this class and
 * tolerate -- via [Assumptions.assumeTrue] -- the case where DebugKt
 * was loaded too early to honour them.
 */
class RecoveryEnabledTest {

    companion object {
        @JvmStatic
        private val recoveryEnabled: Boolean = run {
            // Set BEFORE we touch any kotlinx.coroutines class.
            System.setProperty("kotlinx.coroutines.debug", "on")
            System.setProperty("kotlinx.coroutines.stacktrace.recovery", "true")
            try {
                // Touch DebugKt -- if our properties were set first,
                // this triggers the static initializer with recovery on.
                val cls = Class.forName("kotlinx.coroutines.DebugKt")
                val getter = cls.getMethod("getRECOVER_STACK_TRACES")
                getter.invoke(null) as Boolean
            } catch (e: Throwable) {
                false
            }
        }
    }

    private fun frame(className: String): StackTraceElement =
        StackTraceElement(className, "m", "F.kt", 1)

    private fun coroutineThrowable(): Throwable {
        val t = RuntimeException("recovered")
        t.stackTrace = arrayOf(
            frame("kotlinx.coroutines.DispatchedTask"),
            frame("kotlinx.coroutines.BaseContinuationImpl"),
            frame("com.example.App"),
        )
        return t
    }

    /**
     * With recovery enabled, calling `recoverCoroutineFrames` on a
     * coroutine-frames throwable returns a NEW throwable instance --
     * `tryCopyException` produces a copy of the RuntimeException with
     * a sanitized stack trace.
     *
     * Kills several mutants on the reflective path when active:
     *  - line 96 `as? Throwable ?: throwable`: original picks the
     *    copy returned by `invoke`; a negate-mutant would skip the
     *    cast and yield the same `throwable` (identity).
     *  - line 91 `it.name == "recoverStackTrace"`: a mutant picking
     *    a different declared method would either fail (caught) or
     *    return identity, both differing from the new instance.
     *  - line 92 `it.parameterCount == 1`: mutant picks the 2-arg
     *    overload; invocation throws; catch returns identity.
     *  - line 86 `!hasCoroutineFrames(...)`: mutant skips early
     *    return for plain throwables and enters the reflective path,
     *    which may produce a copy.
     */
    @Test
    fun recoveryProducesNewInstance() {
        Assumptions.assumeTrue(recoveryEnabled, "DebugKt was loaded before our properties; skipping")
        val t = coroutineThrowable()
        val recovered = recoverCoroutineFrames(t)
        // Recovery produced a different (copied) throwable.
        assertTrue(recovered !== t, "expected a new throwable instance from recovery")
        assertEquals("recovered", recovered.message)
    }

    /**
     * With recovery enabled, the recovered throwable's stack trace
     * differs from the original (coroutine-internal frames are
     * sanitized away or replaced).
     */
    @Test
    fun recoveredStackTraceIsSanitized() {
        Assumptions.assumeTrue(recoveryEnabled, "DebugKt was loaded before our properties; skipping")
        val t = coroutineThrowable()
        val originalFirstClass = t.stackTrace[0].className
        val recovered = recoverCoroutineFrames(t)
        // The original `t` is still the unmodified RuntimeException;
        // the recovered copy has a different stack trace head.
        // (The original keeps its synthetic stack; the copy is fresh.)
        // Either the recovered head differs or the recovered length differs.
        val differs = recovered.stackTrace.isEmpty() ||
            recovered.stackTrace[0].className != originalFirstClass ||
            recovered.stackTrace.size != t.stackTrace.size
        assertTrue(differs, "expected sanitized stack trace to differ from original")
    }

    /**
     * Plain throwable (no coroutine frames) short-circuits regardless
     * of the recovery flag. Pins the early-return identity guarantee
     * against the [recoveryProducesNewInstance] test for contrast --
     * with recovery enabled, the line-86 mutant would let the plain
     * throwable enter the reflective path, where `tryCopyException`
     * on a RuntimeException produces a copy. So this test, paired
     * with the recovery-enabled state, kills the line-86 mutant.
     */
    @Test
    fun plainThrowableStillShortCircuitsWithRecoveryFlagOn() {
        Assumptions.assumeTrue(recoveryEnabled, "DebugKt was loaded before our properties; skipping")
        val t = RuntimeException("plain")
        t.stackTrace = arrayOf(frame("com.example.App"))
        val recovered = recoverCoroutineFrames(t)
        assertTrue(recovered === t, "plain throwable must short-circuit before reflective path")
    }
}
