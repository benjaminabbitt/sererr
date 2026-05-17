package sererr

import (
	"bytes"
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"google.golang.org/protobuf/proto"
	pb "sererr.fyi/sererr/packages/go/pb/sererr"
)

// Why: cross-language conformance hinges on byte-equivalent encodings.
// The Rust producer wrote the committed .pb fixtures using sorted-key
// map encoding (Deterministic: true). Our Go encoder must produce the
// exact same bytes for the same input.

// fixtureDescriptor mirrors the FixtureDescriptor in the Rust runner.
type fixtureDescriptor struct {
	CapturedError fixtureCapturedError `json:"captured_error"`
}

type fixtureCapturedError struct {
	Type       string              `json:"type"`
	Message    string              `json:"message"`
	Frames     []fixtureStackFrame `json:"frames"`
	Mechanism  *fixtureMechanism   `json:"mechanism"`
	Release    string              `json:"release"`
	ServerName string              `json:"server_name"`
}

type fixtureStackFrame struct {
	Function    string   `json:"function"`
	Module      string   `json:"module"`
	Package     string   `json:"package"`
	File        string   `json:"file"`
	AbsPath     string   `json:"abs_path"`
	Line        uint32   `json:"line"`
	ContextLine string   `json:"context_line"`
	PreContext  []string `json:"pre_context"`
	PostContext []string `json:"post_context"`
	SourceLink  string   `json:"source_link"`
	InApp       bool     `json:"in_app"`
}

type fixtureMechanism struct {
	Type             string            `json:"type"`
	Description      string            `json:"description"`
	Handled          bool              `json:"handled"`
	Synthetic        bool              `json:"synthetic"`
	HelpLink         string            `json:"help_link"`
	Source           string            `json:"source"`
	ExceptionId      uint32            `json:"exception_id"`
	ParentId         uint32            `json:"parent_id"`
	IsExceptionGroup bool              `json:"is_exception_group"`
	Data             map[string]string `json:"data"`
}

func (f fixtureStackFrame) toPlain() StackFrame {
	return StackFrame{
		Function:    f.Function,
		Module:      f.Module,
		Package:     f.Package,
		File:        f.File,
		AbsPath:     f.AbsPath,
		Line:        f.Line,
		ContextLine: f.ContextLine,
		PreContext:  f.PreContext,
		PostContext: f.PostContext,
		SourceLink:  f.SourceLink,
		InApp:       f.InApp,
	}
}

func (m *fixtureMechanism) toPlain() *ExceptionMechanism {
	if m == nil {
		return nil
	}
	return &ExceptionMechanism{
		Type:             m.Type,
		Description:      m.Description,
		Handled:          m.Handled,
		Synthetic:        m.Synthetic,
		HelpLink:         m.HelpLink,
		Source:           m.Source,
		ExceptionId:      m.ExceptionId,
		ParentId:         m.ParentId,
		IsExceptionGroup: m.IsExceptionGroup,
		Data:             m.Data,
	}
}

func (c fixtureCapturedError) toPlain() CapturedError {
	var frames []StackFrame
	if len(c.Frames) > 0 {
		frames = make([]StackFrame, 0, len(c.Frames))
		for _, f := range c.Frames {
			frames = append(frames, f.toPlain())
		}
	}
	return CapturedError{
		Type:       c.Type,
		Message:    c.Message,
		Frames:     frames,
		Mechanism:  c.Mechanism.toPlain(),
		Release:    c.Release,
		ServerName: c.ServerName,
	}
}

func fixturesDir(t *testing.T) string {
	t.Helper()
	out, err := exec.Command("git", "rev-parse", "--show-toplevel").Output()
	if err != nil {
		t.Fatalf("git rev-parse: %v", err)
	}
	root := strings.TrimSpace(string(out))
	return filepath.Join(root, "tests", "conformance", "fixtures")
}

func loadFixture(t *testing.T, name string) CapturedError {
	t.Helper()
	path := filepath.Join(fixturesDir(t), name+".json")
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	var desc fixtureDescriptor
	if err := json.Unmarshal(raw, &desc); err != nil {
		t.Fatalf("parse %s: %v", path, err)
	}
	return desc.CapturedError.toPlain()
}

func loadFixturePB(t *testing.T, name string) []byte {
	t.Helper()
	path := filepath.Join(fixturesDir(t), name+".pb")
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	return raw
}

func encodeDeterministic(t *testing.T, c CapturedError) []byte {
	t.Helper()
	out, err := proto.MarshalOptions{Deterministic: true}.Marshal(c.ToProto())
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	return out
}

func TestFixtureByteEquivalence(t *testing.T) {
	// Why: every Go encoder must produce byte-identical output to the
	// committed fixtures. If this fails, our wire format diverges from
	// the cross-language contract.
	for _, name := range []string{
		"0001-simple",
		"0002-empty-chain",
		"0003-three-deep",
		"0004-source-context",
		"0005-mechanism-data",
	} {
		t.Run(name, func(t *testing.T) {
			input := loadFixture(t, name)
			got := encodeDeterministic(t, input)
			want := loadFixturePB(t, name)
			if !bytes.Equal(got, want) {
				t.Errorf("byte mismatch for %s:\n got (%d): %x\nwant (%d): %x", name, len(got), got, len(want), want)
			}
		})
	}
}

func TestEmptyCapturedErrorEncodesEmpty(t *testing.T) {
	// Why: default-initialized CapturedError → empty bytes (all default
	// values are skipped in proto3).
	got, err := proto.MarshalOptions{Deterministic: true}.Marshal((CapturedError{}).ToProto())
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	if len(got) != 0 {
		t.Errorf("got %d bytes, want 0: %x", len(got), got)
	}
}

func TestProtoRoundTrip(t *testing.T) {
	// Why: encode then decode preserves the captured error.
	original := loadFixture(t, "0001-simple")
	encoded := encodeDeterministic(t, original)
	var msg pb.CapturedError
	if err := proto.Unmarshal(encoded, &msg); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	got := FromProto(&msg)
	if !reflect.DeepEqual(got, original) {
		t.Errorf("round-trip mismatch:\ngot:  %+v\nwant: %+v", got, original)
	}
}

func TestProtoFromNil(t *testing.T) {
	// Why: defensive zero handling.
	if got := FromProto(nil); !reflect.DeepEqual(got, CapturedError{}) {
		t.Errorf("FromProto(nil) = %+v, want zero", got)
	}
	if got := FromProtoStackFrame(nil); !reflect.DeepEqual(got, StackFrame{}) {
		t.Errorf("FromProtoStackFrame(nil) = %+v, want zero", got)
	}
	if got := FromProtoExceptionMechanism(nil); got != nil {
		t.Errorf("FromProtoExceptionMechanism(nil) = %v, want nil", got)
	}
}
