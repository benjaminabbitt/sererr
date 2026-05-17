// Why: capture() is the central producer entry point. Its outputs feed every
// downstream consumer; these tests pin the chain semantics from
// `tests/conformance/features/chain.feature` plus the JS-specific quirks
// (`Error.cause`, `AggregateError.errors`) the Rust reference can't exercise.
import { describe, it, expect } from 'vitest';
import { capture } from '../src/capture.js';

describe('capture: chain semantics', () => {
  it('single error produces a one-entry chain with exception_id=0, parent_id=0', () => {
    // Why: chain.feature "Single error produces a one-entry chain"
    const chain = capture(new Error('boom'), { release: 'r', serverName: 's' });
    expect(chain).toHaveLength(1);
    const mech = chain[0]!.mechanism!;
    expect(mech.exception_id).toBe(0);
    expect(mech.parent_id).toBe(0);
  });

  it('three-deep chain via Error.cause has correct mechanism IDs', () => {
    // Why: chain.feature "Three-deep chain has correct mechanism IDs" —
    // exception_id is positional, parent_id stamps the causal link.
    const inner = new Error('inner');
    const middle = new Error('middle', { cause: inner });
    const outer = new Error('outer', { cause: middle });
    const chain = capture(outer, { release: 'r', serverName: 's' });
    expect(chain).toHaveLength(3);
    expect(chain[0]!.mechanism!.exception_id).toBe(0);
    expect(chain[0]!.mechanism!.parent_id).toBe(0);
    expect(chain[1]!.mechanism!.exception_id).toBe(1);
    expect(chain[1]!.mechanism!.parent_id).toBe(0);
    expect(chain[2]!.mechanism!.exception_id).toBe(2);
    expect(chain[2]!.mechanism!.parent_id).toBe(1);
    // most-causal-first → innermost is index 0, outermost is last
    expect(chain[0]!.message).toBe('inner');
    expect(chain[2]!.message).toBe('outer');
  });

  it('last chain entry is the originating caught (outermost) error', () => {
    // Why: chain.feature "Originating caught error is the last element" —
    // mirrors Sentry's `exception.values` convention.
    const inner = new Error('inner');
    const outer = new Error('outer', { cause: inner });
    const chain = capture(outer, { release: 'r', serverName: 's' });
    expect(chain[chain.length - 1]!.message).toBe('outer');
  });

  it('default mechanism is generic + handled=true', () => {
    // Why: matches Rust reference impl (sererr/src/lib.rs `capture()`).
    const chain = capture(new Error('x'), { release: 'r', serverName: 's' });
    expect(chain[0]!.mechanism!.type).toBe('generic');
    expect(chain[0]!.mechanism!.handled).toBe(true);
  });

  it('leaf type falls back to err.constructor.name', () => {
    // Why: with no typeName option, use the runtime class name (TypeError, etc.)
    const chain = capture(new TypeError('bad'), { release: 'r', serverName: 's' });
    expect(chain[chain.length - 1]!.type).toBe('TypeError');
  });

  it('leaf typeName option overrides err.constructor.name', () => {
    // Why: producers at the catch site may know a more specific type
    // (matches Rust signature `capture(&err, type_name, ...)`).
    const chain = capture(new Error('x'), {
      typeName: 'MyDomainError',
      release: 'r',
      serverName: 's',
    });
    expect(chain[chain.length - 1]!.type).toBe('MyDomainError');
  });

  it('release and serverName are stamped on every chain entry', () => {
    // Why: every CapturedError is self-contained per the proto spec
    // (release / server_name are inlined event-level fields).
    const inner = new Error('inner');
    const outer = new Error('outer', { cause: inner });
    const chain = capture(outer, { release: 'v1.2.3', serverName: 'pod-7' });
    for (const entry of chain) {
      expect(entry.release).toBe('v1.2.3');
      expect(entry.serverName ?? entry.server_name).toBeDefined();
      expect(entry.server_name).toBe('pod-7');
    }
  });

  it('non-Error throwables capture as a single entry', () => {
    // Why: `throw "string"` and `throw 42` are legal in JS; capture must
    // not crash and must produce a one-entry chain with a sensible message.
    const chain = capture('plain string failure', { release: 'r', serverName: 's' });
    expect(chain).toHaveLength(1);
    expect(chain[0]!.message).toBe('plain string failure');
  });

  it('AggregateError flattens errors as siblings sharing parent_id', () => {
    // Why: AggregateError carries plural `.errors` from Promise.any / allSettled.
    // Idiosyncrasy noted in the task spec — siblings must share the outermost's
    // parent_id so consumers can reconstruct the tree.
    const e1 = new Error('first');
    const e2 = new Error('second');
    const agg = new AggregateError([e1, e2], 'all failed');
    const chain = capture(agg, { release: 'r', serverName: 's' });
    // Outermost is the AggregateError; siblings are flattened in.
    expect(chain.length).toBeGreaterThanOrEqual(3);
    // The AggregateError itself is the outermost (last).
    expect(chain[chain.length - 1]!.message).toBe('all failed');
    // The siblings share the same parent_id (the AggregateError's exception_id).
    const aggId = chain[chain.length - 1]!.mechanism!.exception_id;
    const sibs = chain.filter((c) => c.message === 'first' || c.message === 'second');
    expect(sibs.length).toBe(2);
    for (const s of sibs) {
      expect(s.mechanism!.parent_id).toBe(aggId);
    }
  });
});

describe('capture: frames', () => {
  it('captures at least one frame from a thrown Error', () => {
    // Why: chain.feature "Frame ordering is most-recent-first" — at minimum,
    // assert frames exist (cross-language contract; specific function names
    // are runtime-dependent).
    let caught: Error | undefined;
    function deep(): void {
      throw new Error('boom');
    }
    try {
      deep();
    } catch (e) {
      caught = e as Error;
    }
    const chain = capture(caught!, { release: 'r', serverName: 's' });
    expect(chain[0]!.frames.length).toBeGreaterThan(0);
  });

  it('first frame is the most recent call', () => {
    // Why: chain.feature pins frame ordering. V8 stack format already lists
    // the throw site first; we must not reverse it.
    let caught: Error | undefined;
    function inner(): void {
      throw new Error('boom');
    }
    function outer(): void {
      inner();
    }
    try {
      outer();
    } catch (e) {
      caught = e as Error;
    }
    const chain = capture(caught!, { release: 'r', serverName: 's' });
    const frames = chain[0]!.frames;
    // The throwing function (inner) should appear before its caller (outer).
    const innerIdx = frames.findIndex((f) => f.function.includes('inner'));
    const outerIdx = frames.findIndex((f) => f.function.includes('outer'));
    expect(innerIdx).toBeGreaterThanOrEqual(0);
    expect(outerIdx).toBeGreaterThanOrEqual(0);
    expect(innerIdx).toBeLessThan(outerIdx);
  });
});
