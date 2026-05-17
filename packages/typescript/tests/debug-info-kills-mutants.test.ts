// @ts-nocheck
// Why: Targets Stryker survivors in src/debug-info.ts:
//   - Line 10 `if (chain.length === 0)` early-return
//   - Line 16 `i--` decrement direction (reverse iteration)
//
import { describe, it, expect } from 'vitest';
import { toDebugInfo } from '../src/debug-info.js';
import { emptyCapturedError, emptyStackFrame } from '../src/types.js';

describe('toDebugInfo: empty-chain early return', () => {
  it('empty chain returns empty stack_entries (no spurious header)', () => {
    // Why: pins line 10 `if (chain.length === 0) return emptyDebugInfo()`.
    // If mutated to skip the early return, the for-loop would still
    // produce empty output by coincidence, but more importantly:
    const di = toDebugInfo([]);
    expect(di.stack_entries).toEqual([]);
    expect(di.detail).toBe('');
  });
});

describe('toDebugInfo: reverse iteration ordering', () => {
  it('three-deep chain renders outermost first, innermost last', () => {
    // Why: pins line 16 `for (let i = chain.length - 1; i >= 0; i--)`.
    // If `i--` becomes `i++`, the loop would never terminate or render
    // the wrong order. We assert the exact sequence.
    const a = { ...emptyCapturedError(), type: 'A', message: 'inner' };
    const b = { ...emptyCapturedError(), type: 'B', message: 'middle' };
    const c = { ...emptyCapturedError(), type: 'C', message: 'outer' };
    // chain is most-causal-first: [a (inner), b, c (outer)]
    const di = toDebugInfo([a, b, c]);
    expect(di.detail).toBe('C: outer\nCaused by: B: middle\nCaused by: A: inner');
    expect(di.stack_entries[0]).toBe('C: outer');
    expect(di.stack_entries[1]).toBe('Caused by: B: middle');
    expect(di.stack_entries[2]).toBe('Caused by: A: inner');
  });

  it('three-deep chain with frames interleaves frames after their header', () => {
    // Why: pins both the header-then-frames ordering and the inner for-loop
    // on line 21 `for (const frame of entry.frames)`.
    const innerFrame = { ...emptyStackFrame(), function: 'i', file: 'i.ts', line: 1 };
    const outerFrame = { ...emptyStackFrame(), function: 'o', file: 'o.ts', line: 2 };
    const inner = { ...emptyCapturedError(), type: 'A', message: 'in', frames: [innerFrame] };
    const outer = { ...emptyCapturedError(), type: 'B', message: 'out', frames: [outerFrame] };
    const di = toDebugInfo([inner, outer]);
    expect(di.stack_entries).toEqual([
      'B: out',
      '  at o (o.ts:2)',
      'Caused by: A: in',
      '  at i (i.ts:1)',
    ]);
  });
});

describe('toDebugInfo: idx=0 vs idx>0 first-vs-rest header', () => {
  it('first header (idx=0) omits "Caused by:" — single-entry detail', () => {
    // Why: pins the ternary on line 19 `idx === 0 ? header : 'Caused by: ' + header`.
    const cap = { ...emptyCapturedError(), type: 'T', message: 'm' };
    const di = toDebugInfo([cap]);
    expect(di.detail).toBe('T: m'); // no "Caused by:" prefix
    expect(di.stack_entries[0]).toBe('T: m');
  });
});
