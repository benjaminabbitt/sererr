package fyi.sererr.conformance

import fyi.sererr.kotlin.CapturedError
import fyi.sererr.kotlin.DebugInfo

/**
 * Per-scenario state shared across step definitions.
 *
 * Cucumber-JVM's PicoContainer DI injects a fresh instance per
 * scenario when a step class accepts this in its constructor —
 * mirroring the Java runner's [fyi.sererr.conformance.ConformanceWorld].
 */
class ConformanceWorld {
    var chain: List<CapturedError> = emptyList()
    var debugInfo: DebugInfo? = null
    var encoded: ByteArray? = null
    var fixture: String? = null
    var fixtureInput: CapturedError? = null
}
