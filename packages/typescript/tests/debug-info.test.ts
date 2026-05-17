// Why: debug-info-adapter.feature pins exact strings the cross-language
// adapter must produce. The Rust reference (packages/rust/sererr/src/lib.rs
// `to_debug_info`) is the contract — these tests mirror its output char
// for char.
import { describe, it, expect } from 'vitest';
import { toDebugInfo } from '../src/debug-info.js';
import { emptyCapturedError, emptyStackFrame } from '../src/types.js';

describe('toDebugInfo', () => {
  it('empty chain produces empty DebugInfo', () => {
    // Why: debuginfo-adapter.feature "Empty chain produces empty DebugInfo"
    const di = toDebugInfo([]);
    expect(di.stack_entries).toEqual([]);
    expect(di.detail).toBe('');
  });

  it('single CapturedError detail equals "<type>: <message>"', () => {
    // Why: debuginfo-adapter.feature "Single error produces one type/message in detail"
    const cap = { ...emptyCapturedError(), type: 'MyError', message: 'oops' };
    const di = toDebugInfo([cap]);
    expect(di.detail).toBe('MyError: oops');
  });

  it('chained errors join with "Caused by:" most-recent-first', () => {
    // Why: debuginfo-adapter.feature "Chained errors join with Caused by:"
    // Rust impl reverses the chain (most-recent-first in output) — match exactly.
    const inner = { ...emptyCapturedError(), type: 'A', message: 'inner' };
    const outer = { ...emptyCapturedError(), type: 'B', message: 'outer' };
    // chain is most-causal-first: [inner, outer]
    const di = toDebugInfo([inner, outer]);
    expect(di.detail).toContain('outer');
    expect(di.detail).toContain('Caused by');
    expect(di.detail).toContain('inner');
    // Specifically, outer comes first (most-recent), then "Caused by: inner".
    expect(di.detail).toBe('B: outer\nCaused by: A: inner');
  });

  it('frame lines are formatted "  at <function> (<file>:<line>)"', () => {
    // Why: debuginfo-adapter.feature "Frame lines are formatted..."
    const frame = { ...emptyStackFrame(), function: 'doit', file: 'src/x.rs', line: 12 };
    const cap = { ...emptyCapturedError(), type: 'T', message: 'm', frames: [frame] };
    const di = toDebugInfo([cap]);
    expect(di.stack_entries).toContain('  at doit (src/x.rs:12)');
  });

  it('frame without line uses just the file in parentheses', () => {
    // Why: matches Rust `format_frame_line` — line=0 means unknown.
    const frame = { ...emptyStackFrame(), function: 'doit', file: 'src/x.rs', line: 0 };
    const cap = { ...emptyCapturedError(), type: 'T', message: 'm', frames: [frame] };
    const di = toDebugInfo([cap]);
    expect(di.stack_entries).toContain('  at doit (src/x.rs)');
  });

  it('frame without file omits the location entirely', () => {
    // Why: matches Rust `format_frame_line` — empty file → no parens.
    const frame = { ...emptyStackFrame(), function: 'doit' };
    const cap = { ...emptyCapturedError(), type: 'T', message: 'm', frames: [frame] };
    const di = toDebugInfo([cap]);
    expect(di.stack_entries).toContain('  at doit');
  });

  it('frame without function name renders as <unknown>', () => {
    // Why: matches Rust `format_frame_line` fallback for empty function.
    const frame = { ...emptyStackFrame(), file: 'src/x.rs', line: 1 };
    const cap = { ...emptyCapturedError(), type: 'T', message: 'm', frames: [frame] };
    const di = toDebugInfo([cap]);
    expect(di.stack_entries).toContain('  at <unknown> (src/x.rs:1)');
  });

  it('chained errors emit "Caused by:" stack_entries headers', () => {
    // Why: Rust impl emits "Caused by: <type>: <message>" between frame groups.
    const inner = { ...emptyCapturedError(), type: 'A', message: 'inner' };
    const outer = { ...emptyCapturedError(), type: 'B', message: 'outer' };
    const di = toDebugInfo([inner, outer]);
    expect(di.stack_entries[0]).toBe('B: outer');
    expect(di.stack_entries).toContain('Caused by: A: inner');
  });
});
