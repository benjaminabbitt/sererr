package fyi.sererr;

import java.util.List;
import java.util.Optional;

/**
 * Capture-time helper that populates a {@link StackFrame}'s
 * {@code contextLine} / {@code preContext} / {@code postContext}
 * from a {@link SourceProvider}.
 *
 * <p>{@link StackFrame} is immutable; this helper returns a new frame
 * with the populated fields. It is a no-op when:
 * <ul>
 *   <li>{@link StackFrame#line()} is 0 (unknown), OR</li>
 *   <li>the provider returns {@link Optional#empty()} for the file, OR</li>
 *   <li>the source is shorter than {@code frame.line()}.</li>
 * </ul>
 *
 * <p>{@code surrounding} is the number of lines before / after the
 * target line to capture; 5 is a sensible default (matches Sentry's UI).
 */
public final class SourceContext {
    private SourceContext() {}

    /**
     * Populate the source-context fields of {@code frame} using
     * {@code provider}; returns a new {@link StackFrame} with the
     * fields populated, or {@code frame} unchanged when population is
     * not possible.
     */
    public static StackFrame populateContext(StackFrame frame, SourceProvider provider, int surrounding) {
        if (frame.line() == 0) {
            return frame;
        }
        Optional<String> source = provider.getSource(frame.file());
        if (source.isEmpty()) {
            return frame;
        }
        // Split on \n only (proto3 semantics: bytes are bytes). Use a
        // -1 limit so trailing empty lines are preserved.
        String[] lines = source.get().split("\\R", -1);
        int idx = Math.max(0, frame.line() - 1);
        if (idx >= lines.length) {
            return frame;
        }
        String contextLine = lines[idx];
        int start = Math.max(0, idx - surrounding);
        int end = Math.min(lines.length, idx + 1 + surrounding);
        List<String> pre = List.of(java.util.Arrays.copyOfRange(lines, start, idx));
        List<String> post = List.of(java.util.Arrays.copyOfRange(lines, idx + 1, end));
        return StackFrame.builder()
                .function(frame.function())
                .module(frame.module())
                .pkg(frame.pkg())
                .file(frame.file())
                .absPath(frame.absPath())
                .line(frame.line())
                .contextLine(contextLine)
                .preContext(pre)
                .postContext(post)
                .sourceLink(frame.sourceLink())
                .inApp(frame.inApp())
                .build();
    }
}
