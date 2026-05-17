package sererr

import "strings"

// SourceProvider returns the source contents for a file by path.
//
// Implementors return (content, true) if known, or ("", false) when
// the file is unavailable. Consumers can embed their own source tree
// via go:embed and implement this trait.
type SourceProvider interface {
	GetSource(file string) (string, bool)
}

// PopulateSourceContext fills a StackFrame's ContextLine / PreContext /
// PostContext fields from a SourceProvider.
//
// `surrounding` is the number of lines of pre- and post-context to
// capture (5 is a sensible default — matches Sentry's UI).
//
// No-op when:
//   - frame.Line == 0 (unknown)
//   - provider returns (_, false) for frame.File
//   - frame.Line is beyond the file's last line
func PopulateSourceContext(frame *StackFrame, provider SourceProvider, surrounding int) {
	if frame.Line == 0 {
		return
	}
	source, ok := provider.GetSource(frame.File)
	if !ok {
		return
	}
	lines := splitLines(source)
	idx := int(frame.Line) - 1
	if idx < 0 || idx >= len(lines) {
		return
	}
	frame.ContextLine = lines[idx]
	start := idx - surrounding
	if start < 0 {
		start = 0
	}
	pre := make([]string, 0, idx-start)
	for i := start; i < idx; i++ {
		pre = append(pre, lines[i])
	}
	frame.PreContext = pre
	end := idx + 1 + surrounding
	if end > len(lines) {
		end = len(lines)
	}
	post := make([]string, 0, end-(idx+1))
	for i := idx + 1; i < end; i++ {
		post = append(post, lines[i])
	}
	frame.PostContext = post
}

// splitLines mirrors Rust's `str::lines()`: splits on '\n', strips
// trailing '\r', and excludes any trailing empty line produced by a
// final newline.
func splitLines(s string) []string {
	if s == "" {
		return nil
	}
	raw := strings.Split(s, "\n")
	// If the string ends with '\n', Split produces a trailing "". Drop it.
	if len(raw) > 0 && raw[len(raw)-1] == "" {
		raw = raw[:len(raw)-1]
	}
	for i, l := range raw {
		if strings.HasSuffix(l, "\r") {
			raw[i] = l[:len(l)-1]
		}
	}
	return raw
}
