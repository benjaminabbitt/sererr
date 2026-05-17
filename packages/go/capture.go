package sererr

import (
	"errors"
	"runtime"
	"strings"
)

// CaptureFrames captures stack frames at the calling site.
//
// MUST be called at the originating error site — runtime.Callers reads
// the calling goroutine's stack. Frames are returned
// most-recent-call-first to match the sererr.v1 contract.
//
// The first frame returned is the caller of CaptureFrames (this
// function and the runtime trampoline are skipped).
func CaptureFrames() []StackFrame {
	const maxDepth = 64
	pcs := make([]uintptr, maxDepth)
	// skip = 2: skip runtime.Callers itself and CaptureFrames itself,
	// so the first frame in the result is the caller of CaptureFrames.
	n := runtime.Callers(2, pcs)
	if n == 0 {
		return nil
	}
	pcs = pcs[:n]

	cf := runtime.CallersFrames(pcs)
	var out []StackFrame
	for {
		frame, more := cf.Next()
		out = append(out, StackFrame{
			Function: frame.Function,
			File:     frame.File,
			Line:     uint32(frame.Line),
			InApp:    isAppFrame(frame.Function),
		})
		if !more {
			break
		}
	}
	return out
}

// Capture walks an error chain into a most-causal-first []CapturedError.
//
// `frames` are captured by the caller (typically via CaptureFrames at
// the originating site) and shared across all chain entries.
// `typeName` labels the leaf since Go's `error` interface doesn't
// expose the underlying concrete type.
//
// Each entry receives a default mechanism (Type: "generic", Handled:
// true) with ExceptionId = i and ParentId = max(0, i-1).
func Capture(err error, typeName, release, serverName string, frames []StackFrame) []CapturedError {
	if err == nil {
		return nil
	}

	// Walk most-recent-first (outer → cause), accumulating leaf-first.
	var reversed []CapturedError
	current := err
	leafType := typeName
	for current != nil {
		reversed = append(reversed, CapturedError{
			Type:    leafType,
			Message: current.Error(),
			Frames:  cloneFrames(frames),
			Mechanism: &ExceptionMechanism{
				Type:    "generic",
				Handled: true,
			},
			Release:    release,
			ServerName: serverName,
		})
		leafType = "" // Only the leaf gets the user-supplied type label.
		current = errors.Unwrap(current)
	}

	// Reverse → most-causal-first.
	chain := make([]CapturedError, len(reversed))
	for i, e := range reversed {
		chain[len(reversed)-1-i] = e
	}

	// Stamp chain linkage.
	for i := range chain {
		m := chain[i].Mechanism
		m.ExceptionId = uint32(i)
		if i == 0 {
			m.ParentId = 0
		} else {
			m.ParentId = uint32(i - 1)
		}
	}
	return chain
}

func cloneFrames(in []StackFrame) []StackFrame {
	if in == nil {
		return nil
	}
	out := make([]StackFrame, len(in))
	copy(out, in)
	return out
}

// isAppFrame is the default in_app heuristic. Producers with a better
// signal should not rely on this.
func isAppFrame(function string) bool {
	nonAppPrefixes := []string{
		"runtime.",
		"reflect.",
		"sererr.",
		"sererr.fyi/",
		"testing.",
	}
	for _, p := range nonAppPrefixes {
		if strings.HasPrefix(function, p) {
			return false
		}
	}
	return true
}
