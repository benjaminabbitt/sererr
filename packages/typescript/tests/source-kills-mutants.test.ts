// @ts-nocheck
// Why: Targets Stryker survivors in src/source.ts. Specifically:
//   - Line 30 `if (frame.line === 0)` boundary
//   - Line 37 endsWith('\n') stripping (newline-trim semantics)
//   - Line 40 `idx >= lines.length || idx < 0` upper bound
//
import { describe, it, expect } from 'vitest';
import { populateSourceContext, type SourceProvider } from '../src/source.js';
import { emptyStackFrame } from '../src/types.js';

class StaticSource implements SourceProvider {
  constructor(private readonly map: Record<string, string | null>) {}
  getSource(file: string): string | null {
    const v = this.map[file];
    return v === undefined ? null : v;
  }
}

describe('populateSourceContext: newline-trim semantics', () => {
  it('source ending in "\\n" does not produce an empty trailing line', () => {
    // Why: line 37 `source.endsWith('\n') ? source.slice(0, -1) : source`.
    // Without the trim, split('\n') would create a synthetic trailing
    // empty string, so a frame at the "last" real line would behave wrong
    // and post_context would include that phantom ''.
    const src = 'a\nb\nc\n'; // trailing newline
    const frame = { ...emptyStackFrame(), file: 'x.ts', line: 3 };
    populateSourceContext(frame, new StaticSource({ 'x.ts': src }), 3);
    expect(frame.context_line).toBe('c');
    expect(frame.post_context).toEqual([]); // not [''] — trim worked
  });

  it('source NOT ending in "\\n" treats every line as content', () => {
    // Why: pins the negative branch of line 37 — without trailing \n,
    // we don't strip anything.
    const src = 'a\nb\nc'; // no trailing newline
    const frame = { ...emptyStackFrame(), file: 'x.ts', line: 3 };
    populateSourceContext(frame, new StaticSource({ 'x.ts': src }), 3);
    expect(frame.context_line).toBe('c');
    expect(frame.post_context).toEqual([]);
  });
});

describe('populateSourceContext: idx >= lines.length boundary', () => {
  it('line exactly past EOF (line = lines.length + 1) is a no-op', () => {
    // Why: pins line 40 `idx >= lines.length` boundary. With idx ==
    // lines.length, `>` would survive while `>=` is correct.
    const src = 'a\nb\nc';
    const frame = { ...emptyStackFrame(), file: 'x.ts', line: 4 }; // idx=3, len=3
    const before = { ...frame };
    populateSourceContext(frame, new StaticSource({ 'x.ts': src }), 1);
    expect(frame).toEqual(before);
  });

  it('line equal to lines.length (the last line) IS populated', () => {
    // Why: pins the same boundary — line.length should not over-trim.
    const src = 'a\nb\nc';
    const frame = { ...emptyStackFrame(), file: 'x.ts', line: 3 }; // idx=2, len=3, ok
    populateSourceContext(frame, new StaticSource({ 'x.ts': src }), 1);
    expect(frame.context_line).toBe('c');
  });
});

describe('populateSourceContext: idx < 0 boundary', () => {
  it('negative line (line = -1) is a no-op', () => {
    // Why: pins line 40 `idx < 0` — without this guard, a negative line
    // would underflow into negative slice indices.
    const src = 'a\nb';
    const frame = { ...emptyStackFrame(), file: 'x.ts', line: -1 };
    const before = { ...frame };
    populateSourceContext(frame, new StaticSource({ 'x.ts': src }), 1);
    expect(frame).toEqual(before);
  });
});

describe('populateSourceContext: early-return short-circuits provider lookup', () => {
  it('line=0 returns before calling provider.getSource', () => {
    // Why: pins line 30 `if (frame.line === 0) return;` as a non-equivalent
    // mutant — without the early return, getSource() WOULD be called,
    // observable here via a spy. Stryker's `if (false) return` mutation
    // is killed by this side-effect assertion.
    const calls: string[] = [];
    const provider: SourceProvider = {
      getSource(file) {
        calls.push(file);
        return 'a\nb\nc';
      },
    };
    const frame = { ...emptyStackFrame(), file: 'x.ts', line: 0 };
    populateSourceContext(frame, provider, 1);
    expect(calls).toEqual([]); // provider was NOT consulted
  });

  it('line!=0 DOES call provider.getSource (positive side of the gate)', () => {
    // Why: complements the above — without both, mutant "skip the if"
    // could survive on a coincidence.
    const calls: string[] = [];
    const provider: SourceProvider = {
      getSource(file) {
        calls.push(file);
        return 'a\nb\nc';
      },
    };
    const frame = { ...emptyStackFrame(), file: 'x.ts', line: 2 };
    populateSourceContext(frame, provider, 1);
    expect(calls).toEqual(['x.ts']);
  });
});
