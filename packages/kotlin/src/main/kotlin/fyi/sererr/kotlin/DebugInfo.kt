// sererr-kotlin: DebugInfo adapter + SourceContext re-exports.
package fyi.sererr.kotlin

import fyi.sererr.DebugInfoAdapter as JavaDebugInfoAdapter
import fyi.sererr.SourceContext as JavaSourceContext
import fyi.sererr.SourceProvider

/** Adapt a captured-error chain into a [DebugInfo]. */
@JvmName("toDebugInfoFromChain")
fun toDebugInfo(chain: List<CapturedError>): DebugInfo =
    JavaDebugInfoAdapter.toDebugInfo(chain)

/** Extension: `chain.toDebugInfo()`. */
fun List<CapturedError>.toDebugInfo(): DebugInfo =
    JavaDebugInfoAdapter.toDebugInfo(this)

/**
 * Populate source-context fields on [frame] using [provider]. Returns
 * a new frame with the populated fields; see [JavaSourceContext] for
 * the no-op cases.
 */
fun populateContext(frame: StackFrame, provider: SourceProvider, surrounding: Int = 5): StackFrame =
    JavaSourceContext.populateContext(frame, provider, surrounding)
