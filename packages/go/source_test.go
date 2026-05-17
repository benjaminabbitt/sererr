package sererr

import (
	"reflect"
	"testing"
)

type mapSource map[string]string

func (m mapSource) GetSource(file string) (string, bool) {
	s, ok := m[file]
	return s, ok
}

// Why: source population is part of the per-frame contract; the Rust
// reference is the source of truth for surrounding-line math.

func TestPopulateSourceContextHappy(t *testing.T) {
	// Why: file present, line within range → context populated.
	src := "line1\nline2\nline3\nline4\nline5\n"
	provider := mapSource{"src/x.go": src}
	f := StackFrame{File: "src/x.go", Line: 3}
	PopulateSourceContext(&f, provider, 1)
	if f.ContextLine != "line3" {
		t.Errorf("ContextLine: got %q, want %q", f.ContextLine, "line3")
	}
	if !reflect.DeepEqual(f.PreContext, []string{"line2"}) {
		t.Errorf("PreContext: got %v, want [line2]", f.PreContext)
	}
	if !reflect.DeepEqual(f.PostContext, []string{"line4"}) {
		t.Errorf("PostContext: got %v, want [line4]", f.PostContext)
	}
}

func TestPopulateSourceContextSurrounding5(t *testing.T) {
	// Why: surrounding=5 captures up to 5 lines pre/post.
	src := "a\nb\nc\nd\ne\nf\ng\nh\ni\nj\n"
	provider := mapSource{"f.go": src}
	f := StackFrame{File: "f.go", Line: 5}
	PopulateSourceContext(&f, provider, 5)
	if f.ContextLine != "e" {
		t.Errorf("ContextLine: got %q, want e", f.ContextLine)
	}
	// pre: a, b, c, d (4 lines — saturating from index 0)
	if !reflect.DeepEqual(f.PreContext, []string{"a", "b", "c", "d"}) {
		t.Errorf("PreContext: got %v, want [a b c d]", f.PreContext)
	}
	// post: f, g, h, i, j (5 lines)
	if !reflect.DeepEqual(f.PostContext, []string{"f", "g", "h", "i", "j"}) {
		t.Errorf("PostContext: got %v, want [f g h i j]", f.PostContext)
	}
}

func TestPopulateSourceContextLineZero(t *testing.T) {
	// Why: line=0 (unknown) → no-op.
	provider := mapSource{"f.go": "a\nb\n"}
	f := StackFrame{File: "f.go", Line: 0}
	PopulateSourceContext(&f, provider, 5)
	if f.ContextLine != "" || len(f.PreContext) != 0 || len(f.PostContext) != 0 {
		t.Errorf("expected no-op for Line=0; got %+v", f)
	}
}

func TestPopulateSourceContextMissingFile(t *testing.T) {
	// Why: file not in provider → no-op.
	provider := mapSource{}
	f := StackFrame{File: "missing.go", Line: 1}
	PopulateSourceContext(&f, provider, 5)
	if f.ContextLine != "" || len(f.PreContext) != 0 || len(f.PostContext) != 0 {
		t.Errorf("expected no-op for missing file; got %+v", f)
	}
}

func TestPopulateSourceContextLineBeyondEOF(t *testing.T) {
	// Why: line > len(lines) → no-op (out of range).
	provider := mapSource{"f.go": "a\nb\n"}
	f := StackFrame{File: "f.go", Line: 100}
	PopulateSourceContext(&f, provider, 5)
	if f.ContextLine != "" || len(f.PreContext) != 0 || len(f.PostContext) != 0 {
		t.Errorf("expected no-op for OOB line; got %+v", f)
	}
}
