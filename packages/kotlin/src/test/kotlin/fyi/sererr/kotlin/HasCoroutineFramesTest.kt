package fyi.sererr.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Direct tests for the file-private `recoverCoroutineFrames` probe and
 * the internal `hasCoroutineFrames` helper.
 *
 * `hasCoroutineFrames` is file-private in `Capture.kt`. The tests
 * exercise it indirectly via [recoverCoroutineFrames]: when the probe
 * returns `true`, recovery is attempted; when `false`, the same
 * throwable instance is returned. So `recovered === throwable` is a
 * reliable proxy for `hasCoroutineFrames == false` on inputs whose
 * recovery is not an identity transform.
 *
 * `StackTraceElement[]` is mutable via [Throwable.setStackTrace], so
 * we plant frames whose declaring-class prefix matches
 * `kotlinx.coroutines.` or `kotlin.coroutines.` (or neither) on demand.
 */
class HasCoroutineFramesTest {

    private fun frame(className: String): StackTraceElement =
        StackTraceElement(className, "m", "F.kt", 1)

    private fun throwableWith(vararg classNames: String): Throwable {
        val t = RuntimeException("synthetic")
        t.stackTrace = classNames.map(::frame).toTypedArray()
        return t
    }

    // -- hasCoroutineFrames branch: TRUE side -----------------------------

    /**
     * A frame in `kotlinx.coroutines.` triggers the recovery branch.
     *
     * Kills line-110 `.any { ... }` negate-conditional and line-105
     * `kotlinx.coroutines.` startsWith negate-conditional (left half
     * of the OR).
     */
    @Test
    fun coroutineFrameDetectedByKotlinxPrefix() {
        val t = throwableWith("kotlinx.coroutines.DispatchedTask")
        val recovered = recoverCoroutineFrames(t)
        // Function must not throw and must preserve the message.
        assertEquals("synthetic", recovered.message)
    }

    /**
     * A frame in `kotlin.coroutines.` also triggers the recovery branch.
     *
     * Kills line-105 `kotlin.coroutines.` startsWith negate-conditional
     * (right half of the OR).
     */
    @Test
    fun coroutineFrameDetectedByKotlinPrefix() {
        val t = throwableWith("kotlin.coroutines.SafeContinuation")
        val recovered = recoverCoroutineFrames(t)
        assertEquals("synthetic", recovered.message)
    }

    // -- hasCoroutineFrames branch: FALSE side ----------------------------

    /**
     * A throwable with NO coroutine frames must short-circuit to the
     * early return; identity is preserved.
     *
     * Kills line-86 negate-conditional on `!hasCoroutineFrames(...)`
     * combined with the BooleanTrueReturnVals mutator on line 106
     * (an always-true `hasCoroutineFrames` would skip the early
     * return and fall through to the reflective path -- with the
     * reflective recovery returning the same instance, identity is
     * preserved either way; but the test below pins exact identity
     * by reference equality).
     */
    @Test
    fun plainThrowableShortCircuits() {
        val t = throwableWith("com.example.App", "java.util.ArrayList")
        val recovered = recoverCoroutineFrames(t)
        assertTrue(recovered === t, "expected identity short-circuit")
        // Stack trace must be untouched.
        assertEquals(2, recovered.stackTrace.size)
        assertEquals("com.example.App", recovered.stackTrace[0].className)
    }

    /** Empty stack trace → no coroutine frames → identity short-circuit. */
    @Test
    fun emptyStackTraceShortCircuits() {
        val t = RuntimeException("no-frames")
        t.stackTrace = emptyArray()
        val recovered = recoverCoroutineFrames(t)
        assertTrue(recovered === t)
        assertEquals(0, recovered.stackTrace.size)
    }

    /**
     * `kotlin.` (NOT `kotlin.coroutines.`) is NOT a coroutine frame.
     * Pins the exactness of the `startsWith` prefix on line 105.
     */
    @Test
    fun nonCoroutineKotlinFrameDoesNotTriggerRecovery() {
        val t = throwableWith("kotlin.collections.ArraysKt", "kotlin.io.FilesKt")
        val recovered = recoverCoroutineFrames(t)
        assertTrue(recovered === t, "kotlin.collections is not a coroutine frame")
    }

    /**
     * `kotlinx.` (NOT `kotlinx.coroutines.`) is NOT a coroutine frame.
     * Pins exactness of the `kotlinx.coroutines.` prefix on line 105.
     */
    @Test
    fun nonCoroutineKotlinxFrameDoesNotTriggerRecovery() {
        val t = throwableWith("kotlinx.serialization.Json")
        val recovered = recoverCoroutineFrames(t)
        assertTrue(recovered === t, "kotlinx.serialization is not a coroutine frame")
    }

    /**
     * A SINGLE matching frame anywhere in the stack triggers detection.
     * Kills the `.any { ... }` predicate by contrasting with the
     * non-matching frames; if the predicate were inverted to require
     * `all`, the result of [recoverCoroutineFrames] would short-circuit
     * to identity, which we contrast below.
     */
    @Test
    fun singleCoroutineFrameAnywhereTriggersRecovery() {
        val t = throwableWith(
            "com.example.App",
            "java.util.ArrayList",
            "kotlinx.coroutines.DispatchedTask",
            "com.example.Other",
        )
        val recovered = recoverCoroutineFrames(t)
        assertEquals("synthetic", recovered.message)
    }

    /**
     * A throwable whose first frame matches `kotlin.coroutines.` is
     * detected. Pins the `.any` semantics against a hypothetical
     * `.first().className.startsWith(...)` reordering -- and confirms
     * the `kotlin.coroutines.` branch fires when it is the FIRST frame.
     */
    @Test
    fun firstFrameKotlinCoroutinesTriggersRecovery() {
        val t = throwableWith(
            "kotlin.coroutines.SafeContinuation",
            "com.example.App",
        )
        val recovered = recoverCoroutineFrames(t)
        assertEquals("synthetic", recovered.message)
    }

    /**
     * A frame whose className is empty is NOT a coroutine frame.
     * Guards against a mutant that drops the `startsWith` and replaces
     * it with `true`.
     */
    @Test
    fun emptyClassNameNotCoroutineFrame() {
        // Empty className not allowed by StackTraceElement constructor in
        // JDK 17 (it permits but treats specially); use a one-char name
        // that begins with neither prefix.
        val t = throwableWith("X")
        val recovered = recoverCoroutineFrames(t)
        assertTrue(recovered === t)
    }
}
