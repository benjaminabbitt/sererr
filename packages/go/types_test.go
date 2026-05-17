package sererr

import (
	"reflect"
	"testing"
)

// Why: pin the field layout of the plain types. Cross-language
// conformance relies on every language exposing the same shape; if a
// field is renamed or removed, downstream code breaks silently. These
// asserts make the contract explicit.

func TestStackFrameZeroValue(t *testing.T) {
	var f StackFrame
	if f.Function != "" || f.Module != "" || f.Package != "" || f.File != "" ||
		f.AbsPath != "" || f.Line != 0 || f.ContextLine != "" ||
		f.SourceLink != "" || f.InApp {
		t.Fatalf("zero StackFrame should be empty/false, got %+v", f)
	}
	if f.PreContext != nil || f.PostContext != nil {
		t.Fatalf("zero StackFrame slices should be nil")
	}
}

func TestStackFrameFields(t *testing.T) {
	// Why: exact field names + types are part of the public API.
	rt := reflect.TypeOf(StackFrame{})
	expected := map[string]reflect.Kind{
		"Function":    reflect.String,
		"Module":      reflect.String,
		"Package":     reflect.String,
		"File":        reflect.String,
		"AbsPath":     reflect.String,
		"Line":        reflect.Uint32,
		"ContextLine": reflect.String,
		"PreContext":  reflect.Slice,
		"PostContext": reflect.Slice,
		"SourceLink":  reflect.String,
		"InApp":       reflect.Bool,
	}
	for name, kind := range expected {
		f, ok := rt.FieldByName(name)
		if !ok {
			t.Errorf("StackFrame missing field %q", name)
			continue
		}
		if f.Type.Kind() != kind {
			t.Errorf("StackFrame.%s: kind = %v, want %v", name, f.Type.Kind(), kind)
		}
	}
}

func TestExceptionMechanismFields(t *testing.T) {
	// Why: mechanism shape mirrors Sentry's exception.mechanism object.
	rt := reflect.TypeOf(ExceptionMechanism{})
	expected := map[string]reflect.Kind{
		"Type":             reflect.String,
		"Description":      reflect.String,
		"Handled":          reflect.Bool,
		"Synthetic":        reflect.Bool,
		"HelpLink":         reflect.String,
		"Source":           reflect.String,
		"ExceptionId":      reflect.Uint32,
		"ParentId":         reflect.Uint32,
		"IsExceptionGroup": reflect.Bool,
		"Data":             reflect.Map,
	}
	for name, kind := range expected {
		f, ok := rt.FieldByName(name)
		if !ok {
			t.Errorf("ExceptionMechanism missing field %q", name)
			continue
		}
		if f.Type.Kind() != kind {
			t.Errorf("ExceptionMechanism.%s: kind = %v, want %v", name, f.Type.Kind(), kind)
		}
	}
}

func TestCapturedErrorFields(t *testing.T) {
	// Why: CapturedError exposes type, message, frames, mechanism (ptr),
	// release, server_name — one row in a Sentry-style exception.values.
	rt := reflect.TypeOf(CapturedError{})
	for _, n := range []string{"Type", "Message", "Frames", "Mechanism", "Release", "ServerName"} {
		if _, ok := rt.FieldByName(n); !ok {
			t.Errorf("CapturedError missing field %q", n)
		}
	}
	m, _ := rt.FieldByName("Mechanism")
	if m.Type.Kind() != reflect.Ptr {
		t.Errorf("CapturedError.Mechanism should be a pointer (optional), got %v", m.Type.Kind())
	}
}

func TestDebugInfoFields(t *testing.T) {
	// Why: DebugInfo mirrors google.rpc.DebugInfo for the adapter.
	rt := reflect.TypeOf(DebugInfo{})
	for _, n := range []string{"StackEntries", "Detail"} {
		if _, ok := rt.FieldByName(n); !ok {
			t.Errorf("DebugInfo missing field %q", n)
		}
	}
}
