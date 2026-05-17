package sererr

import (
	"fmt"
	"strings"
)

// ToDebugInfo adapts a sererr capture into the DebugInfo shape.
//
// StackEntries get one line per frame, formatted as
// "  at <function> (<file>:<line>)", most-recent-call-first within
// each chain entry, separated by "Caused by: <type>: <message>" lines
// across the chain.
//
// Detail joins "<type>: <message>" across the chain with
// "\nCaused by: " separators (outermost first, since the chain is
// most-causal-first and we iterate it in reverse).
func ToDebugInfo(chain []CapturedError) DebugInfo {
	if len(chain) == 0 {
		return DebugInfo{}
	}

	var stackEntries []string
	var detailParts []string

	// Iterate most-recent-first (i.e. reverse, since chain is
	// most-causal-first).
	for idx := 0; idx < len(chain); idx++ {
		entry := chain[len(chain)-1-idx]
		header := fmt.Sprintf("%s: %s", entry.Type, entry.Message)
		if idx == 0 {
			detailParts = append(detailParts, header)
			stackEntries = append(stackEntries, header)
		} else {
			withPrefix := "Caused by: " + header
			detailParts = append(detailParts, withPrefix)
			stackEntries = append(stackEntries, withPrefix)
		}
		for _, f := range entry.Frames {
			stackEntries = append(stackEntries, formatFrameLine(f))
		}
	}

	return DebugInfo{
		StackEntries: stackEntries,
		Detail:       strings.Join(detailParts, "\n"),
	}
}

func formatFrameLine(f StackFrame) string {
	var location string
	if f.File == "" {
		location = ""
	} else if f.Line > 0 {
		location = fmt.Sprintf(" (%s:%d)", f.File, f.Line)
	} else {
		location = fmt.Sprintf(" (%s)", f.File)
	}
	function := f.Function
	if function == "" {
		function = "<unknown>"
	}
	return fmt.Sprintf("  at %s%s", function, location)
}
