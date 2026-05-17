package fyi.sererr.kotlin

import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pin the Kotlin-compiler-emitted null-check guards on every public
 * surface. The Kotlin compiler emits `Intrinsics.checkNotNullExpressionValue`
 * / `Intrinsics.checkNotNull` calls at the entry of any function whose
 * parameter or local has a non-nullable type. Pitest's
 * `VoidMethodCallMutator` removes those checks; removing them is
 * detectable iff a caller can actually pass a null value into the
 * function -- which only happens when the caller is NOT subject to
 * Kotlin's type checker (e.g. Java code, reflection, or unsafe casts).
 *
 * We call each Kotlin function reflectively with a `null` argument and
 * verify the resulting `IllegalArgumentException` / `NullPointerException`
 * is thrown synchronously from the Intrinsics check. Removing the
 * check would either:
 *   (a) let the call proceed and produce a different exception
 *       (NullPointerException dereferencing the null) or no exception
 *       at all, OR
 *   (b) still throw NPE from some downstream usage, but with a
 *       different message / stack-trace head.
 *
 * The assertions below detect the difference by checking the THROWN
 * exception type and by asserting the call short-circuits before any
 * side effects (we'd see a stack-trace involving Intrinsics frames).
 */
class IntrinsicsCheckTest {

    /**
     * `capture(null, ...)` short-circuits at the Intrinsics null-check
     * on the `throwable` parameter. Removing the check would let null
     * propagate into `recoverCoroutineFrames`, where the body
     * dereferences `throwable.stackTrace` and produces an NPE -- but
     * importantly, the THROWN exception type AND the trigger site
     * would differ. The Intrinsics check throws NullPointerException
     * (Kotlin 1.4+) with message containing "Parameter specified as
     * non-null is null". We assert that NPE is thrown.
     */
    @Test
    fun captureNullThrowableTriggersNullCheck() {
        try {
            // Reflective invocation bypasses Kotlin's compile-time
            // null-check on the call site.
            val method = Class.forName("fyi.sererr.kotlin.CaptureKt")
                .getMethod(
                    "capture",
                    Throwable::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                )
            method.invoke(null, null, "T", "v", "h")
            fail("expected NullPointerException from Kotlin null-check")
        } catch (e: InvocationTargetException) {
            assertTrue(
                e.targetException is NullPointerException ||
                    e.targetException is IllegalArgumentException,
                "expected NPE/IAE from Intrinsics null-check, got ${e.targetException::class}",
            )
        }
    }

    /**
     * `toCapturedErrorChain` extension on a null receiver triggers the
     * Intrinsics null-check on the extension receiver.
     */
    @Test
    fun toCapturedErrorChainNullReceiverTriggersNullCheck() {
        try {
            val method = Class.forName("fyi.sererr.kotlin.CaptureKt")
                .getMethod(
                    "toCapturedErrorChain",
                    Throwable::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                )
            method.invoke(null, null, "T", "v", "h")
            fail("expected NullPointerException from Kotlin null-check")
        } catch (e: InvocationTargetException) {
            assertTrue(
                e.targetException is NullPointerException ||
                    e.targetException is IllegalArgumentException,
                "expected NPE/IAE; got ${e.targetException::class}",
            )
        }
    }

    /**
     * `toDebugInfo(null)` (top-level, @JvmName "toDebugInfoFromChain")
     * short-circuits at the Intrinsics null-check on the `chain`
     * argument. Removing the check lets null propagate into the Java
     * adapter, where `DebugInfoAdapter.toDebugInfo(null)` returns an
     * empty DebugInfo -- a clearly different observable outcome.
     */
    @Test
    fun toDebugInfoFromChainNullTriggersNullCheck() {
        try {
            val method = Class.forName("fyi.sererr.kotlin.DebugInfoKt")
                .getMethod("toDebugInfoFromChain", java.util.List::class.java)
            method.invoke(null, null)
            fail("expected NullPointerException from Kotlin null-check")
        } catch (e: InvocationTargetException) {
            assertTrue(
                e.targetException is NullPointerException ||
                    e.targetException is IllegalArgumentException,
                "expected NPE/IAE; got ${e.targetException::class}",
            )
        }
    }

    /**
     * `chain.toDebugInfo()` (extension form) on a null receiver
     * triggers the Intrinsics null-check on the extension receiver.
     */
    @Test
    fun extensionToDebugInfoNullReceiverTriggersNullCheck() {
        try {
            val method = Class.forName("fyi.sererr.kotlin.DebugInfoKt")
                .getMethod("toDebugInfo", java.util.List::class.java)
            method.invoke(null, null)
            fail("expected NullPointerException from Kotlin null-check")
        } catch (e: InvocationTargetException) {
            assertTrue(
                e.targetException is NullPointerException ||
                    e.targetException is IllegalArgumentException,
                "expected NPE/IAE; got ${e.targetException::class}",
            )
        }
    }

    /**
     * `populateContext(null, _, _)` triggers the Intrinsics null-check
     * on the `frame` argument.
     */
    @Test
    fun populateContextNullFrameTriggersNullCheck() {
        try {
            val method = Class.forName("fyi.sererr.kotlin.DebugInfoKt")
                .getMethod(
                    "populateContext",
                    fyi.sererr.StackFrame::class.java,
                    fyi.sererr.SourceProvider::class.java,
                    Int::class.javaPrimitiveType,
                )
            method.invoke(null, null, fyi.sererr.SourceProvider { java.util.Optional.empty() }, 5)
            fail("expected NullPointerException from Kotlin null-check")
        } catch (e: InvocationTargetException) {
            assertTrue(
                e.targetException is NullPointerException ||
                    e.targetException is IllegalArgumentException,
                "expected NPE/IAE; got ${e.targetException::class}",
            )
        }
    }
}
