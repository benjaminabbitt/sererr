// @ts-nocheck
// Why: Targets Stryker survivors in src/types.ts type guards
// (isStackFrame / isExceptionMechanism / isCapturedError). For each field,
// the guard must return `false` when only that field is wrong-typed —
// otherwise a conditional/logical mutant flipping that check to `true`
// would survive. We also exercise `isExceptionMechanism` directly so the
// mutants currently flagged "no coverage" become reachable.
import { describe, it, expect } from 'vitest';
import {
  emptyStackFrame,
  emptyExceptionMechanism,
  emptyCapturedError,
  isStackFrame,
  isExceptionMechanism,
  isCapturedError,
} from '../src/types.js';

describe('isStackFrame: each field guards independently', () => {
  // Why: kills ConditionalExpression / LogicalOperator mutants on lines
  // 127-137 in src/types.ts. If any single typeof check is mutated to
  // `true` (always pass), a frame with that one field wrong-typed must
  // still be rejected.
  const valid = emptyStackFrame();

  it('rejects when `function` is not a string', () => {
    expect(isStackFrame({ ...valid, function: 0 })).toBe(false);
  });
  it('rejects when `module` is not a string', () => {
    expect(isStackFrame({ ...valid, module: 0 })).toBe(false);
  });
  it('rejects when `package` is not a string', () => {
    expect(isStackFrame({ ...valid, package: 0 })).toBe(false);
  });
  it('rejects when `file` is not a string', () => {
    expect(isStackFrame({ ...valid, file: 0 })).toBe(false);
  });
  it('rejects when `abs_path` is not a string', () => {
    expect(isStackFrame({ ...valid, abs_path: 0 })).toBe(false);
  });
  it('rejects when `line` is not a number', () => {
    expect(isStackFrame({ ...valid, line: 'x' })).toBe(false);
  });
  it('rejects when `context_line` is not a string', () => {
    expect(isStackFrame({ ...valid, context_line: 0 })).toBe(false);
  });
  it('rejects when `pre_context` is not an array', () => {
    expect(isStackFrame({ ...valid, pre_context: 'x' })).toBe(false);
  });
  it('rejects when `post_context` is not an array', () => {
    expect(isStackFrame({ ...valid, post_context: 'x' })).toBe(false);
  });
  it('rejects when `source_link` is not a string', () => {
    expect(isStackFrame({ ...valid, source_link: 0 })).toBe(false);
  });
  it('rejects when `in_app` is not a boolean', () => {
    expect(isStackFrame({ ...valid, in_app: 'true' })).toBe(false);
  });
});

describe('isExceptionMechanism: positive + per-field negatives', () => {
  // Why: lines 141-155 had every check at no-coverage; calling
  // isExceptionMechanism on each malformed shape activates and kills the
  // ConditionalExpression/EqualityOperator/LogicalOperator mutants.
  const valid = emptyExceptionMechanism();

  it('accepts a valid mechanism', () => {
    expect(isExceptionMechanism(valid)).toBe(true);
  });
  it('rejects null', () => {
    expect(isExceptionMechanism(null)).toBe(false);
  });
  it('rejects a non-object (number)', () => {
    expect(isExceptionMechanism(42)).toBe(false);
  });
  it('rejects when `type` is not a string', () => {
    expect(isExceptionMechanism({ ...valid, type: 0 })).toBe(false);
  });
  it('rejects when `description` is not a string', () => {
    expect(isExceptionMechanism({ ...valid, description: 0 })).toBe(false);
  });
  it('rejects when `handled` is not a boolean', () => {
    expect(isExceptionMechanism({ ...valid, handled: 'x' })).toBe(false);
  });
  it('rejects when `synthetic` is not a boolean', () => {
    expect(isExceptionMechanism({ ...valid, synthetic: 'x' })).toBe(false);
  });
  it('rejects when `help_link` is not a string', () => {
    expect(isExceptionMechanism({ ...valid, help_link: 0 })).toBe(false);
  });
  it('rejects when `source` is not a string', () => {
    expect(isExceptionMechanism({ ...valid, source: 0 })).toBe(false);
  });
  it('rejects when `exception_id` is not a number', () => {
    expect(isExceptionMechanism({ ...valid, exception_id: 'x' })).toBe(false);
  });
  it('rejects when `parent_id` is not a number', () => {
    expect(isExceptionMechanism({ ...valid, parent_id: 'x' })).toBe(false);
  });
  it('rejects when `is_exception_group` is not a boolean', () => {
    expect(isExceptionMechanism({ ...valid, is_exception_group: 'x' })).toBe(false);
  });
  it('rejects when `data` is not an object (null / number / string)', () => {
    expect(isExceptionMechanism({ ...valid, data: null })).toBe(false);
    expect(isExceptionMechanism({ ...valid, data: 0 })).toBe(false);
    expect(isExceptionMechanism({ ...valid, data: 'x' })).toBe(false);
  });
});

describe('isCapturedError: each field guards independently', () => {
  // Why: lines 158-166 — each && in the conjunction must materially
  // affect the return value. Per-field negative cases pin each link.
  const valid = emptyCapturedError();

  it('isObject() early-return guards null without crashing', () => {
    // Why: pins line 158 `if (!isObject(v)) return false;` — without
    // the early return, `v.type` would dereference null and throw.
    // This both kills BooleanLiteral(`true`) and ConditionalExpression
    // (`if (false) return false`) by asserting no-throw + false return.
    expect(() => isCapturedError(null)).not.toThrow();
    expect(isCapturedError(null)).toBe(false);
    expect(() => isCapturedError(undefined)).not.toThrow();
    expect(isCapturedError(undefined)).toBe(false);
    expect(() => isCapturedError(42)).not.toThrow();
    expect(isCapturedError(42)).toBe(false);
  });

  it('accepts a valid capture', () => {
    expect(isCapturedError(valid)).toBe(true);
  });
  it('rejects when `type` is not a string', () => {
    expect(isCapturedError({ ...valid, type: 0 })).toBe(false);
  });
  it('rejects when `message` is not a string', () => {
    expect(isCapturedError({ ...valid, message: 0 })).toBe(false);
  });
  it('rejects when `frames` is not an array', () => {
    expect(isCapturedError({ ...valid, frames: 'x' })).toBe(false);
  });
  it('rejects when `release` is not a string', () => {
    expect(isCapturedError({ ...valid, release: 0 })).toBe(false);
  });
  it('rejects when `server_name` is not a string', () => {
    expect(isCapturedError({ ...valid, server_name: 0 })).toBe(false);
  });
  it('rejects when `mechanism` is present but malformed', () => {
    // Why: pins `(v.mechanism === undefined || isExceptionMechanism(v.mechanism))`
    // — if a non-undefined mechanism is invalid, the capture is invalid.
    expect(isCapturedError({ ...valid, mechanism: { type: 42 } })).toBe(false);
  });
  it('accepts when `mechanism` is a valid mechanism', () => {
    expect(isCapturedError({ ...valid, mechanism: emptyExceptionMechanism() })).toBe(true);
  });
});
