// Package sererr is the plain-types layer of the Sentry-compat
// structured error-capture library. Use it directly when you want
// diagnostics info (frames, chain, mechanism, source context) in
// process — log it, render it, attach it to a tracing span, hash for
// grouping. For wire serialization, see the proto conversions in
// proto.go.
package sererr

// StackFrame is a single frame in a captured stack trace.
//
// Field-compatible with sererr.v1.StackFrame. All fields use proto3
// "zero value = unknown" semantics.
type StackFrame struct {
	// Function is the demangled function/method name.
	Function string
	// Module is the containing module / class / namespace.
	Module string
	// Package is the native library / crate / package name.
	Package string
	// File is the source file (Sentry: filename).
	File string
	// AbsPath is the absolute path on the build machine.
	AbsPath string
	// Line is the 1-based line number; 0 = unknown.
	Line uint32
	// ContextLine is the exact source line at Line.
	ContextLine string
	// PreContext is source lines preceding Line.
	PreContext []string
	// PostContext is source lines following Line.
	PostContext []string
	// SourceLink is a deep link to a source viewer for this frame.
	SourceLink string
	// InApp is a hint to UIs: false for framework/stdlib frames.
	InApp bool
}

// ExceptionMechanism describes how an exception was captured / handled.
//
// Field-compatible with sererr.v1.ExceptionMechanism. Mirrors Sentry's
// mechanism object on the exception interface.
type ExceptionMechanism struct {
	// Type is the mechanism category (e.g. "generic", "panic", "signal").
	Type string
	// Description is a human-readable description.
	Description string
	// Handled is true when the exception was caught by user code.
	Handled bool
	// Synthetic is true if synthesized for context.
	Synthetic bool
	// HelpLink is a docs link explaining this mechanism.
	HelpLink string
	// Source is what attached the mechanism.
	Source string
	// ExceptionId identifies this entry within the enclosing chain.
	ExceptionId uint32
	// ParentId identifies the causal parent. 0 = root cause.
	ParentId uint32
	// IsExceptionGroup is true if this is a Python-style ExceptionGroup.
	IsExceptionGroup bool
	// Data carries mechanism-specific metadata. Encoded with sorted keys
	// (Deterministic: true) so wire bytes are stable across producers.
	Data map[string]string
}

// CapturedError is a single captured error: type, message, frames,
// mechanism, plus inlined release / server_name metadata. A cause chain
// is a []CapturedError most-causal-first.
//
// Field-compatible with sererr.v1.CapturedError.
type CapturedError struct {
	// Type is the error class/type name (e.g. "MyError").
	Type string
	// Message is a short rendering of the error itself (no chain).
	Message string
	// Frames are stack frames, most-recent-call-first.
	Frames []StackFrame
	// Mechanism describes how the exception was captured. Pointer so
	// "absent" round-trips losslessly.
	Mechanism *ExceptionMechanism
	// Release is the build identifier ("binary@semver" or git SHA).
	Release string
	// ServerName is the producing host / pod name.
	ServerName string
}

// DebugInfo is the adapter shape for the gRPC error model
// (google.rpc.DebugInfo). Plain Go struct, wire-compatible with
// google.rpc.DebugInfo. Use ToDebugInfo to render a capture into this
// shape.
type DebugInfo struct {
	// StackEntries are one-line-per-frame stack lines,
	// most-recent-call-first across the whole chain, separated by
	// "Caused by:" lines.
	StackEntries []string
	// Detail is the joined "<type>: <message>" rendering of the chain.
	Detail string
}
