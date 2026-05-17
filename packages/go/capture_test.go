package sererr

import (
	"errors"
	"fmt"
	"strings"
	"testing"
)

// labeledError is a minimal error type with source(). The Go errors
// package walks chains via Unwrap; we use that.
type labeledError struct {
	msg   string
	cause error
}

func (e *labeledError) Error() string { return e.msg }
func (e *labeledError) Unwrap() error { return e.cause }

func makeChain(msgs ...string) error {
	// msgs is innermost-first: [innermost, ..., outermost].
	var current error
	for _, m := range msgs {
		current = &labeledError{msg: m, cause: current}
	}
	return current
}

func TestCaptureSingleError(t *testing.T) {
	// Why: single error → chain length 1, exception_id=0, parent_id=0.
	err := &labeledError{msg: "boom"}
	frames := CaptureFrames()
	chain := Capture(err, "labeledError", "v1", "host-a", frames)
	if len(chain) != 1 {
		t.Fatalf("chain length: got %d, want 1", len(chain))
	}
	if chain[0].Mechanism == nil {
		t.Fatalf("default mechanism must be populated")
	}
	if chain[0].Mechanism.ExceptionId != 0 {
		t.Errorf("exception_id: got %d, want 0", chain[0].Mechanism.ExceptionId)
	}
	if chain[0].Mechanism.ParentId != 0 {
		t.Errorf("parent_id: got %d, want 0", chain[0].Mechanism.ParentId)
	}
	if chain[0].Mechanism.Type != "generic" {
		t.Errorf("default mechanism type: got %q, want generic", chain[0].Mechanism.Type)
	}
	if !chain[0].Mechanism.Handled {
		t.Errorf("default mechanism handled: got false, want true")
	}
	if chain[0].Message != "boom" {
		t.Errorf("message: got %q, want boom", chain[0].Message)
	}
	if chain[0].Type != "labeledError" {
		t.Errorf("type: got %q, want labeledError", chain[0].Type)
	}
	if chain[0].Release != "v1" {
		t.Errorf("release: got %q, want v1", chain[0].Release)
	}
	if chain[0].ServerName != "host-a" {
		t.Errorf("server_name: got %q, want host-a", chain[0].ServerName)
	}
}

func TestCaptureThreeDeepChain(t *testing.T) {
	// Why: chain ordered most-causal-first; exception_id increments;
	// parent_id = max(0, i-1).
	err := makeChain("inner", "middle", "outer") // outer.Unwrap() = middle, etc.
	frames := CaptureFrames()
	chain := Capture(err, "labeledError", "test", "host", frames)
	if len(chain) != 3 {
		t.Fatalf("chain length: got %d, want 3", len(chain))
	}
	cases := []struct {
		idx              int
		message          string
		expectedExceptId uint32
		expectedParentId uint32
	}{
		{0, "inner", 0, 0},
		{1, "middle", 1, 0},
		{2, "outer", 2, 1},
	}
	for _, c := range cases {
		e := chain[c.idx]
		if e.Message != c.message {
			t.Errorf("entry %d message: got %q, want %q", c.idx, e.Message, c.message)
		}
		if e.Mechanism == nil {
			t.Fatalf("entry %d mechanism nil", c.idx)
		}
		if e.Mechanism.ExceptionId != c.expectedExceptId {
			t.Errorf("entry %d exception_id: got %d, want %d", c.idx, e.Mechanism.ExceptionId, c.expectedExceptId)
		}
		if e.Mechanism.ParentId != c.expectedParentId {
			t.Errorf("entry %d parent_id: got %d, want %d", c.idx, e.Mechanism.ParentId, c.expectedParentId)
		}
	}
	if chain[len(chain)-1].Message != "outer" {
		t.Errorf("last entry: got %q, want outer (outermost is last? No — outer is most causal? Let me re-check the contract)", chain[len(chain)-1].Message)
	}
}

func TestCaptureFmtErrorfWrapping(t *testing.T) {
	// Why: chain walks via errors.Unwrap; must support fmt.Errorf %w.
	inner := errors.New("io: closed")
	outer := fmt.Errorf("read failed: %w", inner)
	chain := Capture(outer, "wrap", "v1", "h", nil)
	if len(chain) != 2 {
		t.Fatalf("chain length: got %d, want 2", len(chain))
	}
	// Most-causal-first; the inner error is the most causal.
	if chain[0].Message != "io: closed" {
		t.Errorf("chain[0] message: got %q, want %q", chain[0].Message, "io: closed")
	}
	if !strings.Contains(chain[1].Message, "read failed") {
		t.Errorf("chain[1] message: got %q, want contains 'read failed'", chain[1].Message)
	}
}

func TestCaptureFramesAreNotEmpty(t *testing.T) {
	// Why: CaptureFrames must return real frames captured at call site.
	frames := CaptureFrames()
	if len(frames) == 0 {
		t.Fatalf("expected captured frames; got 0")
	}
	// The first frame should be the test function (most-recent-call-first).
	foundCaller := false
	for _, f := range frames {
		if strings.Contains(f.Function, "TestCaptureFramesAreNotEmpty") {
			foundCaller = true
			break
		}
	}
	if !foundCaller {
		t.Errorf("expected test func in frames; got: %+v", frames)
	}
}

func TestCaptureFramesMostRecentFirst(t *testing.T) {
	// Why: frames must be ordered most-recent-call-first per the proto
	// contract. The caller of CaptureFrames is more recent than its
	// caller — so a direct call inside an inner helper should appear
	// before the test func.
	frames := innerHelper()
	idxInner := -1
	idxOuter := -1
	for i, f := range frames {
		if strings.Contains(f.Function, "innerHelper") && idxInner == -1 {
			idxInner = i
		}
		if strings.Contains(f.Function, "TestCaptureFramesMostRecentFirst") && idxOuter == -1 {
			idxOuter = i
		}
	}
	if idxInner == -1 || idxOuter == -1 {
		t.Fatalf("expected both helper and test in frames: inner=%d outer=%d; frames=%+v", idxInner, idxOuter, frames)
	}
	if idxInner > idxOuter {
		t.Errorf("frames not most-recent-first: innerHelper at %d, test at %d", idxInner, idxOuter)
	}
}

func innerHelper() []StackFrame {
	return CaptureFrames()
}

func TestCaptureInAppHeuristic(t *testing.T) {
	// Why: runtime / reflect / sererr frames are marked InApp=false.
	frames := []StackFrame{
		{Function: "runtime.foo"},
		{Function: "reflect.bar"},
		{Function: "sererr.baz"},
		{Function: "myapp.handler"},
	}
	for _, f := range frames {
		got := isAppFrame(f.Function)
		want := f.Function == "myapp.handler"
		if got != want {
			t.Errorf("isAppFrame(%q) = %v, want %v", f.Function, got, want)
		}
	}
}
