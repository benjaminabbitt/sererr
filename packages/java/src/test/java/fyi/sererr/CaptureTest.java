package fyi.sererr;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Capture semantics: chain ordering, mechanism IDs, frame extraction. */
class CaptureTest {

    /** Single error → 1-entry chain, exception_id 0, parent_id 0. */
    @Test
    void singleErrorOneEntryChain() {
        var e = new RuntimeException("solo");
        List<CapturedError> chain = Capture.capture(e, "RuntimeException", "v1", "host");
        assertThat(chain).hasSize(1);
        var mech = chain.get(0).mechanism().orElseThrow();
        assertThat(mech.exceptionId()).isZero();
        assertThat(mech.parentId()).isZero();
        assertThat(mech.type()).isEqualTo("generic");
        assertThat(mech.handled()).isTrue();
    }

    /** Cause chain → most-causal-first; IDs stamped positionally. */
    @Test
    void threeDeepChain() {
        var inner = new RuntimeException("inner");
        var middle = new RuntimeException("middle", inner);
        var outer = new RuntimeException("outer", middle);
        List<CapturedError> chain = Capture.capture(outer, "RuntimeException", "v1", "host");
        assertThat(chain).hasSize(3);
        assertThat(chain.get(0).message()).isEqualTo("inner");
        assertThat(chain.get(1).message()).isEqualTo("middle");
        assertThat(chain.get(2).message()).isEqualTo("outer");
        assertThat(chain.get(0).mechanism().orElseThrow().exceptionId()).isZero();
        assertThat(chain.get(0).mechanism().orElseThrow().parentId()).isZero();
        assertThat(chain.get(1).mechanism().orElseThrow().exceptionId()).isEqualTo(1);
        assertThat(chain.get(1).mechanism().orElseThrow().parentId()).isZero();
        assertThat(chain.get(2).mechanism().orElseThrow().exceptionId()).isEqualTo(2);
        assertThat(chain.get(2).mechanism().orElseThrow().parentId()).isEqualTo(1);
    }

    /** Originating caught error (outermost) is the LAST entry. */
    @Test
    void outermostIsLast() {
        var inner = new RuntimeException("inner");
        var outer = new RuntimeException("outer", inner);
        List<CapturedError> chain = Capture.capture(outer, "RuntimeException", "v1", "host");
        assertThat(chain.get(chain.size() - 1).message()).isEqualTo("outer");
    }

    /** Frames are non-empty and most-recent-first (throw site is first). */
    @Test
    void framesMostRecentFirst() {
        var e = new RuntimeException("x");
        List<CapturedError> chain = Capture.capture(e, "RuntimeException", "v1", "host");
        var frames = chain.get(0).frames();
        assertThat(frames).isNotEmpty();
        // Top frame should be from this test class (throw site).
        assertThat(frames.get(0).module()).contains("fyi.sererr.CaptureTest");
    }

    /** Suppressed (try-with-resources) exceptions flatten as additional chain entries. */
    @Test
    void suppressedFlattens() {
        var primary = new RuntimeException("primary");
        var suppressed = new RuntimeException("from-close");
        primary.addSuppressed(suppressed);
        List<CapturedError> chain = Capture.capture(primary, "RuntimeException", "v1", "host");
        // Primary + one suppressed = 2 entries.
        assertThat(chain).hasSize(2);
        assertThat(chain).extracting(CapturedError::message)
                .containsExactlyInAnyOrder("primary", "from-close");
    }

    /** Self-referential cause is handled (visited-set breaks the cycle). */
    @Test
    void selfReferentialCauseDoesNotLoop() {
        var e = new RuntimeException("loopy");
        // JVM forbids initCause(self) at construction; we'd reflectively
        // do it but the simpler check: getCause() == this returns null
        // from the JDK. We verify cycles via two-node ring instead.
        var a = new RuntimeException("a");
        var b = new RuntimeException("b", a);
        try {
            a.initCause(b);
        } catch (IllegalStateException ignored) {
            // initCause may fail if cause already set; that's fine for
            // this test — the no-cycle case is sufficient.
            return;
        }
        List<CapturedError> chain = Capture.capture(b, "RuntimeException", "v1", "host");
        // a → b is a cycle; capture must terminate. We accept any
        // finite chain length so long as it doesn't loop.
        assertThat(chain).isNotEmpty();
        assertThat(chain.size()).isLessThanOrEqualTo(8);
    }

    /** captureFrames() works directly on a Throwable without building a chain. */
    @Test
    void captureFramesDirect() {
        var e = new RuntimeException("x");
        List<StackFrame> frames = Capture.captureFrames(e);
        assertThat(frames).isNotEmpty();
    }

    /** Null throwable returns an empty chain (no crash). */
    @Test
    void nullThrowableEmptyChain() {
        assertThat(Capture.capture(null, "T", "v1", "host")).isEmpty();
        assertThat(Capture.captureFrames(null)).isEmpty();
    }

    /** is_app heuristic: stdlib prefixes are non-app, user code is app. */
    @Test
    void isAppFrameHeuristic() {
        assertThat(Capture.isAppFrame("java.util.HashMap")).isFalse();
        assertThat(Capture.isAppFrame("kotlin.collections.MapsKt")).isFalse();
        assertThat(Capture.isAppFrame("com.example.MyService")).isTrue();
        assertThat(Capture.isAppFrame("")).isTrue();
    }
}
