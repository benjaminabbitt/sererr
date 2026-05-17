package fyi.sererr;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain-type contract: records implement value equality and reject nulls. */
class TypesTest {

    /** Two StackFrames with identical field values compare equal (record contract). */
    @Test
    void stackFrameValueEquality() {
        StackFrame a = StackFrame.builder().function("f").file("a.java").line(1).build();
        StackFrame b = StackFrame.builder().function("f").file("a.java").line(1).build();
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    /** StackFrame rejects null collections via the canonical constructor. */
    @Test
    void stackFrameRejectsNullLists() {
        assertThatThrownBy(() -> new StackFrame("f", "", "", "", "", 0, "",
                null, java.util.List.of(), "", false))
                .isInstanceOf(NullPointerException.class);
    }

    /** ExceptionMechanism.data is always sorted (TreeMap) — required for determinism. */
    @Test
    void exceptionMechanismDataIsSorted() {
        TreeMap<String, String> input = new TreeMap<>();
        input.put("z", "1");
        input.put("a", "2");
        input.put("m", "3");
        ExceptionMechanism m = ExceptionMechanism.builder().data(input).build();
        assertThat(m.data().firstKey()).isEqualTo("a");
        assertThat(m.data().lastKey()).isEqualTo("z");
    }

    /** ExceptionMechanism.generic() emits the producer-default mechanism shape. */
    @Test
    void exceptionMechanismGenericIsHandledType() {
        ExceptionMechanism m = ExceptionMechanism.generic();
        assertThat(m.type()).isEqualTo("generic");
        assertThat(m.handled()).isTrue();
        assertThat(m.exceptionId()).isZero();
        assertThat(m.parentId()).isZero();
    }

    /** CapturedError preserves Optional.empty() for missing mechanism. */
    @Test
    void capturedErrorMechanismOptional() {
        CapturedError empty = CapturedError.empty();
        assertThat(empty.mechanism()).isEqualTo(Optional.empty());
        assertThat(empty.type()).isEmpty();
        assertThat(empty.message()).isEmpty();
        assertThat(empty.frames()).isEmpty();
        assertThat(empty.release()).isEmpty();
        assertThat(empty.serverName()).isEmpty();
    }

    /** CapturedError.frames is defensively copied (mutations to the input don't leak). */
    @Test
    void capturedErrorFramesIsImmutable() {
        var src = new java.util.ArrayList<StackFrame>();
        src.add(StackFrame.builder().function("x").build());
        CapturedError e = CapturedError.builder().frames(src).build();
        src.clear();
        assertThat(e.frames()).hasSize(1).first()
                .extracting(StackFrame::function).isEqualTo("x");
    }

    /** DebugInfo carries entries and detail as plain fields. */
    @Test
    void debugInfoEmpty() {
        DebugInfo di = DebugInfo.empty();
        assertThat(di.stackEntries()).isEmpty();
        assertThat(di.detail()).isEmpty();
    }
}
