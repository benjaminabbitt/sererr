// Why: The plain types are the cross-language contract surface. They must
// default to proto3 zero values so callers can construct partial fixtures
// without ceremony, and any byte-equivalence test would break if defaults
// differ from Rust's `Default::default()`.
import { describe, it, expect } from 'vitest';
import {
  emptyStackFrame,
  emptyExceptionMechanism,
  emptyCapturedError,
  emptyDebugInfo,
  isCapturedError,
  isStackFrame,
} from '../src/types.js';

describe('default factories', () => {
  it('emptyStackFrame matches proto3 zero values', () => {
    // Why: pin proto3 defaults so encoded bytes match Rust's `StackFrame::default()`.
    const f = emptyStackFrame();
    expect(f.function).toBe('');
    expect(f.module).toBe('');
    expect(f.package).toBe('');
    expect(f.file).toBe('');
    expect(f.abs_path).toBe('');
    expect(f.line).toBe(0);
    expect(f.context_line).toBe('');
    expect(f.pre_context).toEqual([]);
    expect(f.post_context).toEqual([]);
    expect(f.source_link).toBe('');
    expect(f.in_app).toBe(false);
  });

  it('emptyExceptionMechanism matches proto3 zero values', () => {
    // Why: mechanism defaults must round-trip through proto encode/decode
    // without spurious non-default bytes.
    const m = emptyExceptionMechanism();
    expect(m.type).toBe('');
    expect(m.description).toBe('');
    expect(m.handled).toBe(false);
    expect(m.synthetic).toBe(false);
    expect(m.help_link).toBe('');
    expect(m.source).toBe('');
    expect(m.exception_id).toBe(0);
    expect(m.parent_id).toBe(0);
    expect(m.is_exception_group).toBe(false);
    expect(m.data).toEqual({});
  });

  it('emptyCapturedError matches proto3 zero values (no mechanism)', () => {
    // Why: default capture has no mechanism so an empty CapturedError
    // encodes to 0 bytes (encoding.feature "Empty CapturedError ... empty bytes").
    const e = emptyCapturedError();
    expect(e.type).toBe('');
    expect(e.message).toBe('');
    expect(e.frames).toEqual([]);
    expect(e.mechanism).toBeUndefined();
    expect(e.release).toBe('');
    expect(e.server_name).toBe('');
  });

  it('emptyDebugInfo matches proto3 zero values', () => {
    // Why: DebugInfo zero-value should produce empty wire bytes.
    const d = emptyDebugInfo();
    expect(d.stack_entries).toEqual([]);
    expect(d.detail).toBe('');
  });
});

describe('type guards', () => {
  it('isStackFrame accepts a valid frame', () => {
    // Why: guards let callers receiving `unknown` (e.g. JSON parse) refine safely.
    expect(isStackFrame(emptyStackFrame())).toBe(true);
  });

  it('isStackFrame rejects non-objects and wrong-shape objects', () => {
    // Why: defensive parsing — reject malformed fixtures rather than silently coerce.
    expect(isStackFrame(null)).toBe(false);
    expect(isStackFrame(undefined)).toBe(false);
    expect(isStackFrame(42)).toBe(false);
    expect(isStackFrame('frame')).toBe(false);
    expect(isStackFrame({})).toBe(false);
    expect(isStackFrame({ function: 'f' })).toBe(false);
  });

  it('isCapturedError accepts a valid capture', () => {
    // Why: same as above for CapturedError; used by fixture loaders.
    expect(isCapturedError(emptyCapturedError())).toBe(true);
  });

  it('isCapturedError rejects bad shapes', () => {
    // Why: malformed JSON descriptors should fail loudly, not silently.
    expect(isCapturedError({})).toBe(false);
    expect(isCapturedError({ type: 'T' })).toBe(false);
  });
});
