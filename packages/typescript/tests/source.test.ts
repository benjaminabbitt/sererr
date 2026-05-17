// Why: populateSourceContext mutates a frame to add `context_line` /
// `pre_context` / `post_context`. The Rust reference impl pins the
// off-by-one behavior (line is 1-based) and the boundary cases — match exactly.
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

describe('populateSourceContext', () => {
  it('populates context_line at 1-based line index', () => {
    // Why: line is 1-based per the proto spec. lib.rs uses `(line - 1)` index.
    const src = 'a\nb\nc\nd\ne';
    const frame = { ...emptyStackFrame(), file: 'x.ts', line: 3 };
    populateSourceContext(frame, new StaticSource({ 'x.ts': src }), 1);
    expect(frame.context_line).toBe('c');
    expect(frame.pre_context).toEqual(['b']);
    expect(frame.post_context).toEqual(['d']);
  });

  it('captures `surrounding` lines on each side', () => {
    // Why: surrounding=5 matches Sentry UI default and Rust impl signature.
    const src = '0\n1\n2\n3\n4\n5\n6\n7\n8\n9';
    const frame = { ...emptyStackFrame(), file: 'x.ts', line: 5 }; // "4"
    populateSourceContext(frame, new StaticSource({ 'x.ts': src }), 2);
    expect(frame.context_line).toBe('4');
    expect(frame.pre_context).toEqual(['2', '3']);
    expect(frame.post_context).toEqual(['5', '6']);
  });

  it('clamps pre_context at file start', () => {
    // Why: saturating_sub in Rust — at line 1, pre_context is empty.
    const src = 'a\nb\nc';
    const frame = { ...emptyStackFrame(), file: 'x.ts', line: 1 };
    populateSourceContext(frame, new StaticSource({ 'x.ts': src }), 3);
    expect(frame.context_line).toBe('a');
    expect(frame.pre_context).toEqual([]);
    expect(frame.post_context).toEqual(['b', 'c']);
  });

  it('clamps post_context at file end', () => {
    // Why: min(lines.len) in Rust — at last line, post_context is empty.
    const src = 'a\nb\nc';
    const frame = { ...emptyStackFrame(), file: 'x.ts', line: 3 };
    populateSourceContext(frame, new StaticSource({ 'x.ts': src }), 3);
    expect(frame.context_line).toBe('c');
    expect(frame.pre_context).toEqual(['a', 'b']);
    expect(frame.post_context).toEqual([]);
  });

  it('no-op when line is 0 (unknown)', () => {
    // Why: line=0 means unknown per proto3 zero-value semantics.
    const frame = { ...emptyStackFrame(), file: 'x.ts', line: 0 };
    const before = { ...frame };
    populateSourceContext(frame, new StaticSource({ 'x.ts': 'a\nb' }), 1);
    expect(frame).toEqual(before);
  });

  it('no-op when provider returns null', () => {
    // Why: provider returns None/null when file not embedded — leave frame alone.
    const frame = { ...emptyStackFrame(), file: 'missing.ts', line: 1 };
    const before = { ...frame };
    populateSourceContext(frame, new StaticSource({}), 1);
    expect(frame).toEqual(before);
  });

  it('no-op when line is past end of file', () => {
    // Why: `idx >= lines.len()` in Rust — frame is left untouched.
    const frame = { ...emptyStackFrame(), file: 'x.ts', line: 99 };
    const before = { ...frame };
    populateSourceContext(frame, new StaticSource({ 'x.ts': 'a\nb' }), 1);
    expect(frame).toEqual(before);
  });
});
