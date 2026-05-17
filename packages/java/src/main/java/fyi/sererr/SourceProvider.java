package fyi.sererr;

import java.util.Optional;

/**
 * Source provider for capture-time {@code contextLine} / {@code preContext} /
 * {@code postContext} population on {@link StackFrame}.
 *
 * <p>Implementors return the contents of a source file by path (the
 * {@link StackFrame#file()} value). Producers that ship embedded source
 * (e.g. JAR resources) should implement this against their packaging.
 * Returning {@link Optional#empty()} means "no source for this file" —
 * {@link SourceContext#populateContext(StackFrame, SourceProvider, int)}
 * leaves the frame untouched.
 */
@FunctionalInterface
public interface SourceProvider {
    /** Returns the source contents for {@code file}, or empty when unavailable. */
    Optional<String> getSource(String file);
}
