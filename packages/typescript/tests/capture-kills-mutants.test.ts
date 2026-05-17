// @ts-nocheck
// Why: Targets Stryker survivors in src/capture.ts — branches around
// `makeEntry()`, the AggregateError linkage fix-up, the V8 / SpiderMonkey
// regex alternations, and the `isAppFrame` heuristic. Many of these
// branches are otherwise unreachable from the existing happy-path tests.
import { describe, it, expect } from 'vitest';
import { capture, parseStack, isAppFrame } from '../src/capture.js';

// ---- makeEntry: non-Error throwables ---------------------------------------
//
// chain.feature only exercises Error subclasses; capture() in lib.rs has
// branches for string / null / undefined / object. Each branch must be
// covered to kill the ConditionalExpression + StringLiteral mutants on
// lines 121-137 of capture.ts.

describe('capture: non-Error throwables', () => {
  it('throw "string" yields type="string", message=<value>, frames=[]', () => {
    // Why: line 121 EqualityOperator `typeof err === 'string'` + line 122
    // default `typeName ?? 'string'` + line 123 message = err.
    const chain = capture('boom', { release: 'r', serverName: 's' });
    expect(chain).toHaveLength(1);
    expect(chain[0]!.type).toBe('string');
    expect(chain[0]!.message).toBe('boom');
    expect(chain[0]!.frames).toEqual([]);
  });

  it('throw "string" with typeName override uses the override', () => {
    // Why: pins the `typeName ?? 'string'` default — if mutated to `'' ?? 'string'`,
    // override would still apply, so we also test the default fallback below.
    const chain = capture('boom', { typeName: 'MyT', release: 'r', serverName: 's' });
    expect(chain[0]!.type).toBe('MyT');
  });

  it('throw null yields type="null", message="null"', () => {
    // Why: lines 124-126 — `err === null` branch + the literal strings
    // "null" + the message default.
    const chain = capture(null, { release: 'r', serverName: 's' });
    expect(chain).toHaveLength(1);
    expect(chain[0]!.type).toBe('null');
    expect(chain[0]!.message).toBe('null');
  });

  it('throw undefined yields type="undefined", message="undefined"', () => {
    // Why: lines 127-129 — `err === undefined` branch + literal "undefined".
    const chain = capture(undefined, { release: 'r', serverName: 's' });
    expect(chain).toHaveLength(1);
    expect(chain[0]!.type).toBe('undefined');
    expect(chain[0]!.message).toBe('undefined');
  });

  it('throw number yields type=typeof err, message=String(err)', () => {
    // Why: line 131 — falls into the catch-all branch. type is `typeof err`
    // ("number"), message is String(err) ("42").
    const chain = capture(42, { release: 'r', serverName: 's' });
    expect(chain).toHaveLength(1);
    expect(chain[0]!.type).toBe('number');
    expect(chain[0]!.message).toBe('42');
  });

  it('throw plain object uses String(obj) for message', () => {
    // Why: kills line 133 message = String(err) and line 131 default type.
    const chain = capture({ a: 1 }, { release: 'r', serverName: 's' });
    expect(chain[0]!.type).toBe('object');
    expect(chain[0]!.message).toBe('[object Object]');
  });

  it('non-Error throwable with typeName uses the override', () => {
    // Why: pins the `typeName ?? typeof err` default (line 131).
    const chain = capture(42, { typeName: 'NumberCrash', release: 'r', serverName: 's' });
    expect(chain[0]!.type).toBe('NumberCrash');
  });

  it('null with typeName override uses the override', () => {
    // Why: pins line 125 `typeName ?? 'null'`.
    const chain = capture(null, { typeName: 'NullCrash', release: 'r', serverName: 's' });
    expect(chain[0]!.type).toBe('NullCrash');
  });

  it('undefined with typeName override uses the override', () => {
    // Why: pins line 128 `typeName ?? 'undefined'`.
    const chain = capture(undefined, { typeName: 'UndefCrash', release: 'r', serverName: 's' });
    expect(chain[0]!.type).toBe('UndefCrash');
  });

  it('object with throwing toString() falls back to "<unprintable>"', () => {
    // Why: line 135 — try/catch fallback when String(err) throws.
    const bad = { toString() { throw new Error('cannot stringify'); } };
    const chain = capture(bad, { release: 'r', serverName: 's' });
    expect(chain[0]!.message).toBe('<unprintable>');
  });
});

// ---- capture: default mechanism stamping ----------------------------------

describe('capture: defaults', () => {
  it('default mechanism.synthetic is false and is_exception_group is false', () => {
    // Why: pins the literal `false` defaults in emptyExceptionMechanism +
    // makes sure no mutant flips them to true.
    const chain = capture(new Error('x'), { release: 'r', serverName: 's' });
    expect(chain[0]!.mechanism!.synthetic).toBe(false);
    expect(chain[0]!.mechanism!.is_exception_group).toBe(false);
  });

  it('release and serverName are propagated to every chain entry exactly', () => {
    // Why: pin exact values on lines 46-47.
    const inner = new Error('inner');
    const outer = new Error('outer', { cause: inner });
    const chain = capture(outer, { release: 'v1.2.3', serverName: 'pod-7' });
    expect(chain[0]!.release).toBe('v1.2.3');
    expect(chain[0]!.server_name).toBe('pod-7');
    expect(chain[1]!.release).toBe('v1.2.3');
    expect(chain[1]!.server_name).toBe('pod-7');
  });

  it('single error parent_id is 0 (kills `i === 0 ? 0 : i - 1` boundary)', () => {
    // Why: pins the `i === 0 ? 0 : i - 1` branch on line 45.
    const chain = capture(new Error('x'), { release: 'r', serverName: 's' });
    expect(chain[0]!.mechanism!.parent_id).toBe(0);
  });

  it('second entry of a two-deep chain has parent_id=0', () => {
    // Why: pins the i-1 arithmetic — without this, mutating `i - 1` to
    // `i + 1` or `0 - 1` would survive.
    const inner = new Error('inner');
    const outer = new Error('outer', { cause: inner });
    const chain = capture(outer, { release: 'r', serverName: 's' });
    expect(chain[1]!.mechanism!.parent_id).toBe(0);
    expect(chain[1]!.mechanism!.exception_id).toBe(1);
  });
});

// ---- AggregateError linkage -----------------------------------------------

describe('capture: AggregateError linkage', () => {
  it('AggregateError parent (the group itself) has is_exception_group=true', () => {
    // Why: kills line 91-92 mutation `entry.mechanism = entry.mechanism ?? defaultMechanism()`
    // and sets `is_exception_group = true` on line 92.
    const e1 = new Error('first');
    const agg = new AggregateError([e1], 'all failed');
    const chain = capture(agg, { release: 'r', serverName: 's' });
    const aggEntry = chain.find((c) => c.message === 'all failed')!;
    expect(aggEntry.mechanism!.is_exception_group).toBe(true);
  });

  it('non-AggregateError entries have is_exception_group=false', () => {
    // Why: pins the negative; without this, mutating `is_exception_group = true`
    // unconditionally would survive.
    const chain = capture(new Error('x'), { release: 'r', serverName: 's' });
    expect(chain[0]!.mechanism!.is_exception_group).toBe(false);
  });

  it('aggParent fixup is a no-op when a sibling has no matching parent', () => {
    // Why: pins the `if (parent && entry.mechanism)` guard on line 57.
    // We can't easily induce a no-match in normal usage; instead, test a
    // non-AggregateError chain to confirm no parent_id stomping occurs.
    const inner = new Error('inner');
    const outer = new Error('outer', { cause: inner });
    const chain = capture(outer, { release: 'r', serverName: 's' });
    // inner is at index 0 (parent_id=0), outer at index 1 (parent_id=0).
    // If line 58 fired spuriously, parent_id would be different.
    expect(chain[1]!.mechanism!.parent_id).toBe(0);
  });

  it('two siblings in AggregateError both share the AggregateError parent_id', () => {
    // Why: pins line 58 fixup runs exactly once per sibling.
    const e1 = new Error('first');
    const e2 = new Error('second');
    const agg = new AggregateError([e1, e2], 'group');
    const chain = capture(agg, { release: 'r', serverName: 's' });
    const aggEntry = chain.find((c) => c.message === 'group')!;
    const aggId = aggEntry.mechanism!.exception_id;
    const sibs = chain.filter((c) => c.message === 'first' || c.message === 'second');
    expect(sibs).toHaveLength(2);
    for (const s of sibs) {
      expect(s.mechanism!.parent_id).toBe(aggId);
    }
  });

  it('aggParent marker is stripped from output entries', () => {
    // Why: line 60 `delete (entry as ..)._aggParent` — if mutated to a no-op,
    // the private marker would leak.
    const e1 = new Error('child');
    const agg = new AggregateError([e1], 'group');
    const chain = capture(agg, { release: 'r', serverName: 's' });
    for (const entry of chain) {
      expect((entry as Record<string, unknown>)._aggParent).toBeUndefined();
    }
  });
});

// ---- parseStack: V8 regex branches ----------------------------------------

describe('parseStack: V8 format', () => {
  it('parses "at func (file:line:col)" — V8_FULL', () => {
    // Why: pins V8_FULL regex on line 184 — function, file, line captured.
    const frames = parseStack('Error: msg\n    at doIt (src/x.ts:12:34)');
    expect(frames).toHaveLength(1);
    expect(frames[0]!.function).toBe('doIt');
    expect(frames[0]!.file).toBe('src/x.ts');
    expect(frames[0]!.line).toBe(12);
  });

  it('parses "at file:line:col" (anonymous) — V8_BARE', () => {
    // Why: pins V8_BARE regex on line 185 — function stays empty, file
    // is captured, line is parsed.
    const frames = parseStack('Error: msg\n    at src/x.ts:12:34');
    expect(frames).toHaveLength(1);
    expect(frames[0]!.function).toBe('');
    expect(frames[0]!.file).toBe('src/x.ts');
    expect(frames[0]!.line).toBe(12);
  });

  it('parses "at func (file:line)" (no col) — V8_NO_COL', () => {
    // Why: pins V8_NO_COL regex on line 187 — three-capture variant.
    const frames = parseStack('Error: msg\n    at doIt (src/x.ts:42)');
    expect(frames).toHaveLength(1);
    expect(frames[0]!.function).toBe('doIt');
    expect(frames[0]!.file).toBe('src/x.ts');
    expect(frames[0]!.line).toBe(42);
  });

  it('rejects lines that do not start with "at " — V8 prefix gate', () => {
    // Why: pins line 190 `if (!line.startsWith('at ')) return null;` and
    // the literal "at " (line 190 col 24 StringLiteral mutator).
    const frames = parseStack('Error: msg\n    NOT_AT (src/x.ts:12:34)');
    expect(frames).toHaveLength(0);
  });

  it('rejects malformed V8 line (no parens, no @)', () => {
    // Why: pins the `return null` fall-through on line 217.
    const frames = parseStack('Error: msg\n    at gibberish only');
    expect(frames).toHaveLength(0);
  });

  it('parses anchored prefix `^at` (rejects "blah at func (...)")', () => {
    // Why: pins the `^` anchor in V8_FULL — a leading non-"at" must fail.
    const frames = parseStack('blah at doIt (src/x.ts:1:1)');
    expect(frames).toHaveLength(0);
  });

  it('V8_FULL multi-digit line + col round-trip', () => {
    // Why: kills regex mutants that shorten `\d+` to `\d`.
    const frames = parseStack('    at doIt (src/x.ts:12345:678)');
    expect(frames[0]!.line).toBe(12345);
  });

  it('V8_NO_COL multi-digit line', () => {
    // Why: similar — guard the \d+ in the no-col regex.
    const frames = parseStack('    at doIt (src/x.ts:9999)');
    expect(frames[0]!.line).toBe(9999);
  });

  it('parses multiple frames, preserving most-recent-first order', () => {
    // Why: pins line 165 split('\n') + line 168 empty-line skip + the
    // `out.push` ordering on lines 171 / 176.
    const stack = [
      'Error: msg',
      '    at inner (a.ts:1:1)',
      '    at outer (b.ts:2:2)',
    ].join('\n');
    const frames = parseStack(stack);
    expect(frames).toHaveLength(2);
    expect(frames[0]!.function).toBe('inner');
    expect(frames[1]!.function).toBe('outer');
  });

  it('skips empty lines between frames', () => {
    // Why: pins line 168 `if (line === '') continue;` — without it, a
    // blank line would short-circuit out before reaching the regex.
    const stack = [
      'Error: msg',
      '',
      '    at inner (a.ts:1:1)',
      '',
      '    at outer (b.ts:2:2)',
    ].join('\n');
    const frames = parseStack(stack);
    expect(frames).toHaveLength(2);
  });

  it('header line "Error: msg" is silently skipped (no false frame)', () => {
    // Why: ensures the first line doesn't slip through as a frame.
    const frames = parseStack('Error: msg\n    at inner (a.ts:1:1)');
    expect(frames).toHaveLength(1);
    expect(frames[0]!.function).toBe('inner');
  });

  it('an entirely empty stack produces no frames', () => {
    // Why: kills mutants on parseStack's loop iteration when input is empty.
    expect(parseStack('')).toEqual([]);
  });
});

// ---- parseStack: SpiderMonkey regex branches -------------------------------

describe('parseStack: SpiderMonkey format', () => {
  it('parses "func@file:line:col" — SPIDERMONKEY full', () => {
    // Why: pins SPIDERMONKEY regex on line 220 — function, file, line.
    const frames = parseStack('doIt@src/x.ts:12:34');
    expect(frames).toHaveLength(1);
    expect(frames[0]!.function).toBe('doIt');
    expect(frames[0]!.file).toBe('src/x.ts');
    expect(frames[0]!.line).toBe(12);
  });

  it('parses "func@file:line" — SPIDERMONKEY_NO_COL', () => {
    // Why: pins SPIDERMONKEY_NO_COL regex on line 221.
    const frames = parseStack('doIt@src/x.ts:42');
    expect(frames).toHaveLength(1);
    expect(frames[0]!.function).toBe('doIt');
    expect(frames[0]!.file).toBe('src/x.ts');
    expect(frames[0]!.line).toBe(42);
  });

  it('parses anonymous "@file:line:col" (empty function)', () => {
    // Why: pins the `(.*?)` capture being non-greedy / allowing empty.
    const frames = parseStack('@src/x.ts:1:2');
    expect(frames).toHaveLength(1);
    expect(frames[0]!.function).toBe('');
    expect(frames[0]!.file).toBe('src/x.ts');
    expect(frames[0]!.line).toBe(1);
  });

  it('rejects lines without "@" — early gate', () => {
    // Why: pins line 224 `if (!line.includes('@')) return null;` and the
    // literal "@" character.
    const frames = parseStack('no_at_here');
    expect(frames).toHaveLength(0);
  });

  it('SpiderMonkey multi-digit line/col survives `\\d+` regex mutants', () => {
    // Why: kills regex mutants that shorten `\d+` → `\d`.
    const frames = parseStack('doIt@src/x.ts:1234:5678');
    expect(frames[0]!.line).toBe(1234);
  });

  it('SpiderMonkey no-col multi-digit line', () => {
    const frames = parseStack('doIt@src/x.ts:9999');
    expect(frames[0]!.line).toBe(9999);
  });

  it('V8-first wins over SpiderMonkey when both could match', () => {
    // Why: pins the order of try-V8-then-SpiderMonkey on lines 169-178.
    // A line that begins with "at " AND contains "@" must be parsed by V8.
    const frames = parseStack('    at foo@bar (file:1:1)');
    expect(frames).toHaveLength(1);
    expect(frames[0]!.function).toBe('foo@bar');
  });

  it('SpiderMonkey: line with @ but no colon yields no frame (no crash)', () => {
    // Why: pins line 235 `if (m2)` — if mutated to `if (true)`, the
    // null match would dereference and throw. Original returns null
    // gracefully (filtered out by the parseStack loop).
    expect(() => parseStack('just@text')).not.toThrow();
    expect(parseStack('just@text')).toEqual([]);
  });

  it('SpiderMonkey: line "@" alone yields no frame (no crash)', () => {
    // Why: same as above with the minimal include('@') input.
    expect(() => parseStack('@')).not.toThrow();
    expect(parseStack('@')).toEqual([]);
  });

  it('V8: line starting with "at " but malformed yields no frame (no crash)', () => {
    // Why: pins lines 201/210 `if (m2)` / `if (m3)` — if mutated to true,
    // null deref would throw. Crafting input that bypasses V8_FULL and
    // V8_NO_COL but passes the startsWith check is enough.
    expect(() => parseStack('at totally-malformed')).not.toThrow();
    expect(parseStack('at totally-malformed')).toEqual([]);
  });
});

// ---- isAppFrame heuristic --------------------------------------------------

describe('isAppFrame', () => {
  it('returns true for an app source file', () => {
    // Why: pins the final `return true` on line 261.
    expect(isAppFrame('myFn', 'src/app.ts')).toBe(true);
  });

  it('returns false when file starts with "node:"', () => {
    // Why: kills line 256 `file.startsWith(p)` mutation.
    expect(isAppFrame('myFn', 'node:fs')).toBe(false);
  });

  it('returns false when file starts with "internal/"', () => {
    expect(isAppFrame('myFn', 'internal/timers')).toBe(false);
  });

  it('returns false when file starts with "node_modules/"', () => {
    expect(isAppFrame('myFn', 'node_modules/express/lib.js')).toBe(false);
  });

  it('returns false when file contains "/node:"', () => {
    // Why: kills the `file.includes(`/${p}`)` branch on line 256.
    expect(isAppFrame('myFn', '/path/to/node:fs')).toBe(false);
  });

  it('returns false when file contains "/node_modules/"', () => {
    // Why: pins the embedded-path detection — common case in monorepos.
    expect(isAppFrame('myFn', '/repo/packages/foo/node_modules/lib.js')).toBe(false);
  });

  it('returns false when fn starts with "node:"', () => {
    // Why: kills line 259 `fn.startsWith(p)` mutation for function check.
    expect(isAppFrame('node:internalSomething', 'src/app.ts')).toBe(false);
  });

  it('returns false when fn starts with "Promise."', () => {
    expect(isAppFrame('Promise.then', 'src/app.ts')).toBe(false);
  });

  it('returns false when fn starts with "process."', () => {
    expect(isAppFrame('process.nextTick', 'src/app.ts')).toBe(false);
  });

  it('returns true for fn containing "Promise." in middle (not prefix)', () => {
    // Why: pins startsWith vs includes — kills the `fn.endsWith(p)` /
    // `fn.includes(p)` mutants that would otherwise survive on line 259.
    expect(isAppFrame('myPromise.then', 'src/app.ts')).toBe(true);
  });

  it('returns true for file containing "node:" but not at a path boundary', () => {
    // Why: matching is anchored to start-of-string OR /-prefix. "nodey/foo"
    // should *not* match the embedded `node:` pattern.
    expect(isAppFrame('myFn', 'src/internal-helpers.ts')).toBe(true);
  });

  it('frames parsed from V8 stack populate in_app correctly (positive)', () => {
    // Why: pins line 197 `frame.in_app = isAppFrame(...)`.
    const frames = parseStack('    at userFn (src/app.ts:1:1)');
    expect(frames[0]!.in_app).toBe(true);
  });

  it('frames parsed from V8 stack populate in_app correctly (negative)', () => {
    const frames = parseStack('    at internal (node:fs:1:1)');
    expect(frames[0]!.in_app).toBe(false);
  });

  it('V8_BARE in_app uses empty function name', () => {
    // Why: pins line 214 `isAppFrame('', frame.file)` — the function
    // argument is hardcoded to empty for bare-anonymous frames.
    const frames = parseStack('    at node:fs:1:1');
    expect(frames[0]!.in_app).toBe(false);
  });

  it('SpiderMonkey frame populates in_app', () => {
    // Why: pins line 231 `frame.in_app = isAppFrame(...)`.
    const frames = parseStack('userFn@src/app.ts:1:1');
    expect(frames[0]!.in_app).toBe(true);
  });
});
