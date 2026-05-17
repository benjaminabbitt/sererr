package sererr

import (
	"strings"
	"testing"
)

// Why: ToDebugInfo's output is part of the cross-language contract.
// Format must match Rust's to_debug_info character-for-character.

func TestToDebugInfoEmptyChain(t *testing.T) {
	// Why: empty chain → empty StackEntries and Detail.
	di := ToDebugInfo(nil)
	if len(di.StackEntries) != 0 {
		t.Errorf("StackEntries: got %v, want empty", di.StackEntries)
	}
	if di.Detail != "" {
		t.Errorf("Detail: got %q, want empty", di.Detail)
	}
}

func TestToDebugInfoSingleError(t *testing.T) {
	// Why: single error → detail = "<type>: <message>".
	chain := []CapturedError{{Type: "MyError", Message: "oops"}}
	di := ToDebugInfo(chain)
	if di.Detail != "MyError: oops" {
		t.Errorf("Detail: got %q, want %q", di.Detail, "MyError: oops")
	}
}

func TestToDebugInfoChainCausedBy(t *testing.T) {
	// Why: chained errors joined with "\nCaused by: " (most-causal-LAST
	// in detail string; Rust iterates rev for the rendering).
	chain := []CapturedError{
		{Type: "Inner", Message: "inner"}, // most-causal
		{Type: "Outer", Message: "outer"},
	}
	di := ToDebugInfo(chain)
	if !strings.Contains(di.Detail, "outer") {
		t.Errorf("Detail missing 'outer': %q", di.Detail)
	}
	if !strings.Contains(di.Detail, "Caused by") {
		t.Errorf("Detail missing 'Caused by': %q", di.Detail)
	}
	if !strings.Contains(di.Detail, "inner") {
		t.Errorf("Detail missing 'inner': %q", di.Detail)
	}
	// First in detail: outermost type/message (since we iterate rev).
	if !strings.HasPrefix(di.Detail, "Outer: outer") {
		t.Errorf("Detail prefix: got %q, want starts with 'Outer: outer'", di.Detail)
	}
}

func TestToDebugInfoFrameLineFormat(t *testing.T) {
	// Why: frame line format "  at <function> (<file>:<line>)".
	chain := []CapturedError{
		{
			Type:    "T",
			Message: "m",
			Frames: []StackFrame{
				{Function: "doit", File: "src/x.rs", Line: 12},
			},
		},
	}
	di := ToDebugInfo(chain)
	want := "  at doit (src/x.rs:12)"
	found := false
	for _, e := range di.StackEntries {
		if e == want {
			found = true
			break
		}
	}
	if !found {
		t.Errorf("expected stack entry %q in %v", want, di.StackEntries)
	}
}

func TestToDebugInfoFrameNoFile(t *testing.T) {
	// Why: empty file → no location suffix.
	chain := []CapturedError{
		{Type: "T", Message: "m", Frames: []StackFrame{{Function: "doit"}}},
	}
	di := ToDebugInfo(chain)
	want := "  at doit"
	found := false
	for _, e := range di.StackEntries {
		if e == want {
			found = true
			break
		}
	}
	if !found {
		t.Errorf("expected %q in %v", want, di.StackEntries)
	}
}

func TestToDebugInfoFrameUnknownFunction(t *testing.T) {
	// Why: empty function → "<unknown>".
	chain := []CapturedError{
		{Type: "T", Message: "m", Frames: []StackFrame{{File: "f.go", Line: 1}}},
	}
	di := ToDebugInfo(chain)
	want := "  at <unknown> (f.go:1)"
	found := false
	for _, e := range di.StackEntries {
		if e == want {
			found = true
			break
		}
	}
	if !found {
		t.Errorf("expected %q in %v", want, di.StackEntries)
	}
}
