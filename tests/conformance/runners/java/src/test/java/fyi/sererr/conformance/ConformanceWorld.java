package fyi.sererr.conformance;

import fyi.sererr.CapturedError;
import fyi.sererr.DebugInfo;

import java.util.List;

/**
 * Per-scenario state shared across step definitions.
 *
 * <p>Picked up by Cucumber-JVM's PicoContainer DI: any step class
 * with this as a constructor parameter receives a fresh instance per
 * scenario, mirroring the Rust runner's `World` struct.
 */
public class ConformanceWorld {
    /** Constructed captured-error chain under test. */
    public List<CapturedError> chain = List.of();
    /** Captured DebugInfo for the adapter scenarios. */
    public DebugInfo debugInfo;
    /** Encoded proto bytes (when serialization is exercised). */
    public byte[] encoded;
    /** Active fixture name. */
    public String fixture;
    /** Constructed CapturedError from a fixture (pre-encode). */
    public CapturedError fixtureInput;
}
