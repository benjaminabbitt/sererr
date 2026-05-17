package fyi.sererr;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** SourceContext + SourceProvider: populate context_line / pre / post. */
class SourceProviderTest {

    private static final String SAMPLE =
            "line1\nline2\nline3\nline4\nline5\nline6\nline7";

    private static SourceProvider provider(String file, String body) {
        return req -> req.equals(file) ? Optional.of(body) : Optional.empty();
    }

    /** Populates context_line, pre_context, post_context from a known file. */
    @Test
    void populatesContext() {
        var frame = StackFrame.builder().file("a.java").line(4).build();
        StackFrame out = SourceContext.populateContext(frame, provider("a.java", SAMPLE), 2);
        assertThat(out.contextLine()).isEqualTo("line4");
        assertThat(out.preContext()).containsExactly("line2", "line3");
        assertThat(out.postContext()).containsExactly("line5", "line6");
    }

    /** No-op when line is 0 (unknown). */
    @Test
    void noopOnUnknownLine() {
        var frame = StackFrame.builder().file("a.java").line(0).build();
        StackFrame out = SourceContext.populateContext(frame, provider("a.java", SAMPLE), 2);
        assertThat(out).isEqualTo(frame);
    }

    /** No-op when provider returns empty. */
    @Test
    void noopWhenProviderEmpty() {
        var frame = StackFrame.builder().file("a.java").line(2).build();
        StackFrame out = SourceContext.populateContext(frame, req -> Optional.empty(), 2);
        assertThat(out).isEqualTo(frame);
    }

    /** No-op when line is past end of file. */
    @Test
    void noopWhenLinePastEnd() {
        var frame = StackFrame.builder().file("a.java").line(999).build();
        StackFrame out = SourceContext.populateContext(frame, provider("a.java", SAMPLE), 2);
        assertThat(out).isEqualTo(frame);
    }

    /** Saturating slice: line 1 → empty pre_context, populated post. */
    @Test
    void firstLineHasEmptyPre() {
        var frame = StackFrame.builder().file("a.java").line(1).build();
        StackFrame out = SourceContext.populateContext(frame, provider("a.java", SAMPLE), 2);
        assertThat(out.contextLine()).isEqualTo("line1");
        assertThat(out.preContext()).isEmpty();
        assertThat(out.postContext()).containsExactly("line2", "line3");
    }
}
