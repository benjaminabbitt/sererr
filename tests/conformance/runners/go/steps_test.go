package conformance

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"strconv"
	"strings"

	"github.com/cucumber/godog"
	"google.golang.org/protobuf/proto"

	sererr "sererr.fyi/sererr/packages/go"
	pb "sererr.fyi/sererr/packages/go/pb/sererr"
)

// ============================================================================
// Test fixtures (errors with synthetic chains)
// ============================================================================

// labeledError exposes a message and an optional cause via Unwrap, so
// sererr.Capture walks the chain via errors.Unwrap.
type labeledError struct {
	msg   string
	cause error
}

func (e *labeledError) Error() string { return e.msg }
func (e *labeledError) Unwrap() error { return e.cause }

// makeChain builds a chain from innermost-first messages: msgs[0] is
// the deepest cause; msgs[len-1] is the outermost. Returns the
// outermost.
func makeChain(msgs []string) error {
	var current error
	for _, m := range msgs {
		current = &labeledError{msg: m, cause: current}
	}
	return current
}

// ============================================================================
// JSON descriptor for fixtures
// ============================================================================

type fixtureDescriptor struct {
	CapturedError *fixtureCapturedError `json:"captured_error"`
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

func (f fixtureStackFrame) toPlain() sererr.StackFrame {
	return sererr.StackFrame{
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

func (m *fixtureMechanism) toPlain() *sererr.ExceptionMechanism {
	if m == nil {
		return nil
	}
	return &sererr.ExceptionMechanism{
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

func (c *fixtureCapturedError) toPlain() sererr.CapturedError {
	if c == nil {
		return sererr.CapturedError{}
	}
	var frames []sererr.StackFrame
	if len(c.Frames) > 0 {
		frames = make([]sererr.StackFrame, 0, len(c.Frames))
		for _, f := range c.Frames {
			frames = append(frames, f.toPlain())
		}
	}
	return sererr.CapturedError{
		Type:       c.Type,
		Message:    c.Message,
		Frames:     frames,
		Mechanism:  c.Mechanism.toPlain(),
		Release:    c.Release,
		ServerName: c.ServerName,
	}
}

// ============================================================================
// World (shared scenario state)
// ============================================================================

type world struct {
	chain        []sererr.CapturedError
	debugInfo    *sererr.DebugInfo
	encoded      []byte
	encodedSet   bool
	fixture      string
	fixtureInput *sererr.CapturedError
}

func fixturesDir() string {
	return os.Getenv("SERERR_FIXTURES_DIR")
}

func (w *world) loadFixtureInput() error {
	if w.fixture == "" {
		return errors.New("no fixture name set")
	}
	path := filepath.Join(fixturesDir(), w.fixture+".json")
	raw, err := os.ReadFile(path)
	if err != nil {
		return fmt.Errorf("read fixture json %s: %w", path, err)
	}
	var desc fixtureDescriptor
	if err := json.Unmarshal(raw, &desc); err != nil {
		return fmt.Errorf("parse fixture %s: %w", path, err)
	}
	if desc.CapturedError == nil {
		return fmt.Errorf("fixture %s missing captured_error key", path)
	}
	plain := desc.CapturedError.toPlain()
	w.fixtureInput = &plain
	return nil
}

// ============================================================================
// Step definitions
// ============================================================================

func InitializeScenario(ctx *godog.ScenarioContext) {
	w := &world{}

	// Reset world per scenario.
	ctx.Before(func(c context.Context, sc *godog.Scenario) (context.Context, error) {
		*w = world{}
		return c, nil
	})

	// ---------- background / generic ----------
	ctx.Step(`^the canonical sererr\.v1 proto schema$`, func() error {
		return nil
	})

	// ---------- encoding.feature ----------
	ctx.Step(`^a fixture "([^"]+)"$`, func(name string) error {
		w.fixture = name
		return nil
	})
	ctx.Step(`^a default-initialized CapturedError \(all zero values\)$`, func() error {
		empty := sererr.CapturedError{}
		w.fixtureInput = &empty
		return nil
	})
	ctx.Step(`^I construct the CapturedError per the fixture's JSON descriptor$`, func() error {
		return w.loadFixtureInput()
	})
	ctx.Step(`^I serialize it via the proto adapter$`, func() error {
		return serialize(w)
	})
	ctx.Step(`^I serialize it$`, func() error {
		return serialize(w)
	})
	ctx.Step(`^the encoded bytes match "fixtures/([^"]+)\.pb"$`, func(name string) error {
		path := filepath.Join(fixturesDir(), name+".pb")
		expected, err := os.ReadFile(path)
		if err != nil {
			return fmt.Errorf("read fixture pb %s: %w", path, err)
		}
		if !w.encodedSet {
			return errors.New("encoded bytes not set")
		}
		if !equalBytes(w.encoded, expected) {
			return fmt.Errorf("encoded bytes mismatch for fixture %s\n  got (%d): %x\n want (%d): %x",
				name, len(w.encoded), w.encoded, len(expected), expected)
		}
		return nil
	})
	ctx.Step(`^the encoded bytes are empty$`, func() error {
		if !w.encodedSet {
			return errors.New("encoded bytes not set")
		}
		if len(w.encoded) != 0 {
			return fmt.Errorf("expected empty bytes, got %d bytes", len(w.encoded))
		}
		return nil
	})
	ctx.Step(`^I deserialize the bytes back to a CapturedError$`, func() error {
		if !w.encodedSet {
			return errors.New("encoded bytes not set")
		}
		var msg pb.CapturedError
		if err := proto.Unmarshal(w.encoded, &msg); err != nil {
			return fmt.Errorf("decode: %w", err)
		}
		plain := sererr.FromProto(&msg)
		w.fixtureInput = &plain
		return nil
	})
	ctx.Step(`^the result equals the input field-by-field$`, func() error {
		if w.fixture == "" {
			return errors.New("no fixture name set")
		}
		path := filepath.Join(fixturesDir(), w.fixture+".json")
		raw, err := os.ReadFile(path)
		if err != nil {
			return fmt.Errorf("read fixture json %s: %w", path, err)
		}
		var desc fixtureDescriptor
		if err := json.Unmarshal(raw, &desc); err != nil {
			return fmt.Errorf("parse fixture: %w", err)
		}
		if desc.CapturedError == nil {
			return errors.New("fixture missing captured_error")
		}
		want := desc.CapturedError.toPlain()
		if w.fixtureInput == nil {
			return errors.New("decoded fixture_input not set")
		}
		if !reflect.DeepEqual(*w.fixtureInput, want) {
			return fmt.Errorf("round-trip mismatch:\n got: %+v\nwant: %+v", *w.fixtureInput, want)
		}
		return nil
	})

	// ---------- chain.feature ----------
	ctx.Step(`^an error with no source / cause$`, func() error {
		err := &labeledError{msg: "single"}
		w.chain = sererr.Capture(err, "labeledError", "test", "host", sererr.CaptureFrames())
		return nil
	})
	ctx.Step(`^a chain "([^"]+)" caused-by "([^"]+)"$`, func(inner, outer string) error {
		outermost := makeChain([]string{inner, outer})
		w.chain = sererr.Capture(outermost, "labeledError", "test", "host", sererr.CaptureFrames())
		return nil
	})
	ctx.Step(`^a chain "([^"]+)" caused-by "([^"]+)" caused-by "([^"]+)"$`, func(inner, middle, outer string) error {
		outermost := makeChain([]string{inner, middle, outer})
		w.chain = sererr.Capture(outermost, "labeledError", "test", "host", sererr.CaptureFrames())
		return nil
	})
	ctx.Step(`^a captured error with frames$`, func() error {
		err := &labeledError{msg: "x"}
		w.chain = sererr.Capture(err, "labeledError", "test", "host", sererr.CaptureFrames())
		return nil
	})
	ctx.Step(`^I capture it$`, func() error { return nil })
	ctx.Step(`^I capture the outermost error$`, func() error { return nil })
	ctx.Step(`^I read the frames$`, func() error { return nil })
	ctx.Step(`^the chain length is (\d+)$`, func(n int) error {
		if len(w.chain) != n {
			return fmt.Errorf("chain length: got %d, want %d", len(w.chain), n)
		}
		return nil
	})
	ctx.Step(`^entry (\d+) has exception_id (\d+) and parent_id (\d+)$`, func(idx, exceptId, parentId int) error {
		if idx < 0 || idx >= len(w.chain) {
			return fmt.Errorf("entry %d out of range (chain len %d)", idx, len(w.chain))
		}
		m := w.chain[idx].Mechanism
		if m == nil {
			return fmt.Errorf("entry %d mechanism is nil", idx)
		}
		if m.ExceptionId != uint32(exceptId) {
			return fmt.Errorf("entry %d exception_id: got %d, want %d", idx, m.ExceptionId, exceptId)
		}
		if m.ParentId != uint32(parentId) {
			return fmt.Errorf("entry %d parent_id: got %d, want %d", idx, m.ParentId, parentId)
		}
		return nil
	})
	ctx.Step(`^the entry's mechanism has exception_id (\d+)$`, func(n int) error {
		if len(w.chain) == 0 {
			return errors.New("empty chain")
		}
		m := w.chain[0].Mechanism
		if m == nil {
			return errors.New("mechanism nil")
		}
		if m.ExceptionId != uint32(n) {
			return fmt.Errorf("exception_id: got %d, want %d", m.ExceptionId, n)
		}
		return nil
	})
	ctx.Step(`^the entry's mechanism has parent_id (\d+)$`, func(n int) error {
		if len(w.chain) == 0 {
			return errors.New("empty chain")
		}
		m := w.chain[0].Mechanism
		if m == nil {
			return errors.New("mechanism nil")
		}
		if m.ParentId != uint32(n) {
			return fmt.Errorf("parent_id: got %d, want %d", m.ParentId, n)
		}
		return nil
	})
	ctx.Step(`^entry (\d+) has message "([^"]+)"$`, func(idx int, msg string) error {
		if idx < 0 || idx >= len(w.chain) {
			return fmt.Errorf("entry %d out of range", idx)
		}
		if w.chain[idx].Message != msg {
			return fmt.Errorf("entry %d message: got %q, want %q", idx, w.chain[idx].Message, msg)
		}
		return nil
	})
	ctx.Step(`^the last chain entry has message "([^"]+)"$`, func(msg string) error {
		if len(w.chain) == 0 {
			return errors.New("empty chain")
		}
		last := w.chain[len(w.chain)-1]
		if last.Message != msg {
			return fmt.Errorf("last entry message: got %q, want %q", last.Message, msg)
		}
		return nil
	})
	ctx.Step(`^the first frame is the most recent call$`, func() error {
		if len(w.chain) == 0 {
			return errors.New("empty chain")
		}
		if len(w.chain[0].Frames) == 0 {
			return errors.New("expected captured frames; got 0")
		}
		return nil
	})

	// ---------- debuginfo-adapter.feature ----------
	ctx.Step(`^an empty chain$`, func() error {
		w.chain = nil
		return nil
	})
	ctx.Step(`^a single CapturedError with type "([^"]+)" and message "([^"]+)"$`, func(t, msg string) error {
		w.chain = []sererr.CapturedError{{Type: t, Message: msg}}
		return nil
	})
	ctx.Step(`^a CapturedError with one frame:$`, func(table *godog.Table) error {
		// Expect a 2-row table with header: function | file | line.
		if len(table.Rows) < 2 {
			return fmt.Errorf("expected at least 2 rows in table; got %d", len(table.Rows))
		}
		row := table.Rows[1].Cells
		if len(row) < 3 {
			return fmt.Errorf("expected 3 cells in row; got %d", len(row))
		}
		line, err := strconv.ParseUint(row[2].Value, 10, 32)
		if err != nil {
			return fmt.Errorf("parse line: %w", err)
		}
		w.chain = []sererr.CapturedError{{
			Type:    "T",
			Message: "m",
			Frames: []sererr.StackFrame{{
				Function: row[0].Value,
				File:     row[1].Value,
				Line:     uint32(line),
			}},
		}}
		return nil
	})
	ctx.Step(`^I call to_debug_info$`, func() error {
		di := sererr.ToDebugInfo(w.chain)
		w.debugInfo = &di
		return nil
	})
	ctx.Step(`^stack_entries is empty$`, func() error {
		if w.debugInfo == nil {
			return errors.New("debug_info not set")
		}
		if len(w.debugInfo.StackEntries) != 0 {
			return fmt.Errorf("expected empty stack_entries, got %v", w.debugInfo.StackEntries)
		}
		return nil
	})
	ctx.Step(`^detail is empty$`, func() error {
		if w.debugInfo == nil {
			return errors.New("debug_info not set")
		}
		if w.debugInfo.Detail != "" {
			return fmt.Errorf("expected empty detail, got %q", w.debugInfo.Detail)
		}
		return nil
	})
	ctx.Step(`^detail equals "([^"]+)"$`, func(expected string) error {
		if w.debugInfo == nil {
			return errors.New("debug_info not set")
		}
		if w.debugInfo.Detail != expected {
			return fmt.Errorf("detail: got %q, want %q", w.debugInfo.Detail, expected)
		}
		return nil
	})
	ctx.Step(`^detail contains "([^"]+)"$`, func(needle string) error {
		if w.debugInfo == nil {
			return errors.New("debug_info not set")
		}
		if !strings.Contains(w.debugInfo.Detail, needle) {
			return fmt.Errorf("detail %q should contain %q", w.debugInfo.Detail, needle)
		}
		return nil
	})
	ctx.Step(`^stack_entries contains "([^"]+)"$`, func(needle string) error {
		if w.debugInfo == nil {
			return errors.New("debug_info not set")
		}
		for _, e := range w.debugInfo.StackEntries {
			if e == needle {
				return nil
			}
		}
		return fmt.Errorf("stack_entries %v should contain %q", w.debugInfo.StackEntries, needle)
	})
}

// serialize auto-constructs from a named fixture when the scenario goes
// from `Given a fixture` directly to `When I serialize it`, then encodes
// via the proto adapter deterministically.
func serialize(w *world) error {
	if w.fixtureInput == nil {
		if err := w.loadFixtureInput(); err != nil {
			return err
		}
	}
	if w.fixtureInput == nil {
		return errors.New("no input to serialize")
	}
	out, err := proto.MarshalOptions{Deterministic: true}.Marshal(w.fixtureInput.ToProto())
	if err != nil {
		return fmt.Errorf("marshal: %w", err)
	}
	w.encoded = out
	w.encodedSet = true
	return nil
}

func equalBytes(a, b []byte) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}
