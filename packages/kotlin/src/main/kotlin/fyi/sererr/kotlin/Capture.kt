// sererr-kotlin: Kotlin-idiomatic surface over the Java capture API.
//
// This module is a thin layer: every fact about chain semantics,
// determinism, and the proto-wire contract lives in the Java
// implementation. The Kotlin layer only adds:
//   - top-level functions that look natural in Kotlin call sites
//   - a `Throwable.toCapturedErrorChain` extension
//   - coroutine stack-trace recovery via kotlinx-coroutines-debug
//     before delegating to the Java capture
//
// Type aliases re-export the Java records as Kotlin types so
// downstream code can `import fyi.sererr.kotlin.CapturedError` and
// avoid touching the Java symbol names directly.
package fyi.sererr.kotlin

import fyi.sererr.Capture as JavaCapture

/** Re-export of the Java [fyi.sererr.CapturedError] record. */
typealias CapturedError = fyi.sererr.CapturedError

/** Re-export of the Java [fyi.sererr.StackFrame] record. */
typealias StackFrame = fyi.sererr.StackFrame

/** Re-export of the Java [fyi.sererr.ExceptionMechanism] record. */
typealias ExceptionMechanism = fyi.sererr.ExceptionMechanism

/** Re-export of the Java [fyi.sererr.DebugInfo] record. */
typealias DebugInfo = fyi.sererr.DebugInfo

/**
 * Walk a [Throwable] chain into a sererr capture.
 *
 * Before delegating to [fyi.sererr.Capture.capture], this function asks
 * `kotlinx-coroutines-debug` to recover coroutine state-machine frames
 * (`invokeSuspend`, `resumeWith`, `BaseContinuationImpl`) on the
 * throwable, so the captured stack trace shows the user's suspending
 * call sites rather than the runtime scaffolding. Recovery is a no-op
 * when the debug probes are not installed (default for production).
 */
fun capture(
    throwable: Throwable,
    typeName: String,
    release: String,
    serverName: String,
): List<CapturedError> {
    val recovered = recoverCoroutineFrames(throwable)
    return JavaCapture.capture(recovered, typeName, release, serverName)
}

/**
 * Idiomatic extension: `myThrowable.toCapturedErrorChain(...)`.
 *
 * See [capture] for the recovery contract.
 */
fun Throwable.toCapturedErrorChain(
    typeName: String,
    release: String,
    serverName: String,
): List<CapturedError> = capture(this, typeName, release, serverName)

/**
 * Demystify coroutine state-machine frames on [throwable] before
 * capture.
 *
 * Strategy:
 *  1. If [throwable] has no coroutine-machinery frames
 *     (no class starting with `kotlin.coroutines.` or
 *     `kotlinx.coroutines.`), return it unchanged. Recovery is a
 *     non-trivial copy operation that would corrupt the chain shape
 *     by wrapping a non-coroutine throwable in a recovered clone.
 *  2. Otherwise reflectively invoke
 *     `kotlinx.coroutines.internal.StackTraceRecoveryKt.recoverStackTrace`.
 *     That function is marked `internal` in Kotlin source (invisible
 *     to the Kotlin compiler) but is `public` at the JVM bytecode
 *     level and ships in every recent `kotlinx-coroutines-core`.
 *
 * Combined with an installed [kotlinx.coroutines.debug.DebugProbes]
 * (optional; users opt in at test/dev time), the recovered stack
 * trace shows suspending call sites instead of the JVM-level
 * `invokeSuspend` / `resumeWith` scaffolding.
 *
 * Defensive: any failure in the recovery layer falls back to the
 * unmodified throwable. Recovery must not break capture.
 */
internal fun recoverCoroutineFrames(throwable: Throwable): Throwable {
    if (!hasCoroutineFrames(throwable)) {
        return throwable
    }
    return try {
        val cls = Class.forName("kotlinx.coroutines.internal.StackTraceRecoveryKt")
        val method = cls.declaredMethods.firstOrNull {
            it.name == "recoverStackTrace" && it.parameterCount == 1
        } ?: return throwable
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (method.invoke(null, throwable) as? Throwable) ?: throwable
    } catch (e: Throwable) {
        throwable
    }
}

private fun hasCoroutineFrames(t: Throwable): Boolean =
    t.stackTrace.any {
        val cn = it.className
        cn.startsWith("kotlinx.coroutines.") || cn.startsWith("kotlin.coroutines.")
    }
