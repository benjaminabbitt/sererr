package fyi.sererr;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** DebugInfo adapter: rendering rules for stack_entries and detail. */
class DebugInfoTest {

    /** Empty chain → empty DebugInfo. */
    @Test
    void emptyChain() {
        DebugInfo di = DebugInfoAdapter.toDebugInfo(List.of());
        assertThat(di.stackEntries()).isEmpty();
        assertThat(di.detail()).isEmpty();
    }

    /** Single entry → "type: message" in detail (no Caused-by). */
    @Test
    void singleEntryDetail() {
        var entry = CapturedError.builder().type("MyError").message("oops").build();
        DebugInfo di = DebugInfoAdapter.toDebugInfo(List.of(entry));
        assertThat(di.detail()).isEqualTo("MyError: oops");
        assertThat(di.stackEntries()).containsExactly("MyError: oops");
    }

    /** Chain → outermost first, separated by "Caused by:". */
    @Test
    void chainDetailJoinedByCausedBy() {
        var inner = CapturedError.builder().type("InnerError").message("inner").build();
        var outer = CapturedError.builder().type("OuterError").message("outer").build();
        // chain is most-causal-first: [inner, outer]
        DebugInfo di = DebugInfoAdapter.toDebugInfo(List.of(inner, outer));
        assertThat(di.detail()).contains("outer").contains("Caused by").contains("inner");
        // Outermost ("outer") appears before "Caused by".
        int outerIdx = di.detail().indexOf("outer");
        int causedByIdx = di.detail().indexOf("Caused by");
        assertThat(outerIdx).isLessThan(causedByIdx);
    }

    /** Frame line format: "  at function (file:line)". */
    @Test
    void frameLineFormat() {
        var frame = StackFrame.builder().function("doit").file("src/x.java").line(12).build();
        var entry = CapturedError.builder().type("T").message("m").frames(List.of(frame)).build();
        DebugInfo di = DebugInfoAdapter.toDebugInfo(List.of(entry));
        assertThat(di.stackEntries()).contains("  at doit (src/x.java:12)");
    }

    /** Frame with no file → just "  at function". */
    @Test
    void frameLineNoFile() {
        var frame = StackFrame.builder().function("doit").build();
        var entry = CapturedError.builder().type("T").message("m").frames(List.of(frame)).build();
        DebugInfo di = DebugInfoAdapter.toDebugInfo(List.of(entry));
        assertThat(di.stackEntries()).contains("  at doit");
    }

    /** Frame with file but no line → "  at function (file)". */
    @Test
    void frameLineNoLine() {
        var frame = StackFrame.builder().function("doit").file("a.java").build();
        var entry = CapturedError.builder().type("T").message("m").frames(List.of(frame)).build();
        DebugInfo di = DebugInfoAdapter.toDebugInfo(List.of(entry));
        assertThat(di.stackEntries()).contains("  at doit (a.java)");
    }

    /** Frame with empty function → "<unknown>". */
    @Test
    void frameLineUnknownFn() {
        var frame = StackFrame.builder().file("a.java").line(5).build();
        var entry = CapturedError.builder().type("T").message("m").frames(List.of(frame)).build();
        DebugInfo di = DebugInfoAdapter.toDebugInfo(List.of(entry));
        assertThat(di.stackEntries()).contains("  at <unknown> (a.java:5)");
    }
}
