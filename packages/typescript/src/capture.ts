// Walk a JS error chain into a sererr capture.
//
// Mirrors the Rust `capture()` in `packages/rust/sererr/src/lib.rs`:
//   - Chain is most-causal-first; outermost (caught) error is the last
//     element.
//   - Each entry stamps `exception_id = i`, `parent_id = max(0, i-1)`
//     by default. AggregateError siblings override `parent_id` to share
//     the outermost's id (so consumers can rebuild the tree).
//   - Default mechanism is `{ type: 'generic', handled: true }`.
//   - Frame source is `err.stack` in V8 format (Node, our primary target).
//     SpiderMonkey / JavaScriptCore variants are parsed as a fallback.

import {
  type CapturedError,
  type ExceptionMechanism,
  type StackFrame,
  emptyExceptionMechanism,
  emptyStackFrame,
} from './types.js';

/**
 * Walk a JS error chain into a sererr capture.
 *
 * Returns an array **most-causal-first** — `chain[chain.length - 1]` is the
 * outermost (caught) error. `typeName` overrides the leaf entry's type;
 * if omitted, `err.constructor.name` is used.
 */
export function capture(
  err: unknown,
  options: { typeName?: string; release: string; serverName: string },
): CapturedError[] {
  // Walk the cause chain inward, then reverse to most-causal-first.
  const linear: CapturedError[] = [];
  collectLinear(err, options.typeName, linear);

  // Now reverse: linear was outer-to-inner (start with caught), we want
  // most-causal-first.
  linear.reverse();

  // Stamp default exception_id / parent_id (overridden later for AggregateError children).
  for (let i = 0; i < linear.length; i++) {
    const entry = linear[i]!;
    if (!entry.mechanism) entry.mechanism = defaultMechanism();
    entry.mechanism.exception_id = i;
    entry.mechanism.parent_id = i === 0 ? 0 : i - 1;
    entry.release = options.release;
    entry.server_name = options.serverName;
  }

  // Walk again to fix up AggregateError sibling linkage. Children of an
  // AggregateError share the same parent_id (the AggregateError's
  // exception_id). We mark this at collection time via _aggParent.
  for (const entry of linear) {
    const aggParentMsg = (entry as CapturedError & { _aggParent?: string })._aggParent;
    if (aggParentMsg !== undefined) {
      const parent = linear.find((c) => c.message === aggParentMsg && c.mechanism?.is_exception_group);
      if (parent && entry.mechanism) {
        entry.mechanism.parent_id = parent.mechanism!.exception_id;
      }
      delete (entry as CapturedError & { _aggParent?: string })._aggParent;
    }
  }

  return linear;
}

function defaultMechanism(): ExceptionMechanism {
  const m = emptyExceptionMechanism();
  m.type = 'generic';
  m.handled = true;
  return m;
}

/**
 * Recursively collects errors into `out`, in outer-to-inner order.
 *
 * For an AggregateError, the AggregateError itself is appended first,
 * then each of its `.errors` is appended (each potentially chained via
 * its own `.cause`).
 */
function collectLinear(
  err: unknown,
  leafTypeName: string | undefined,
  out: CapturedError[],
): void {
  const entry = makeEntry(err, leafTypeName);
  out.push(entry);

  // AggregateError: flatten siblings with the AggregateError as their parent.
  if (err instanceof AggregateError) {
    entry.mechanism = entry.mechanism ?? defaultMechanism();
    entry.mechanism.is_exception_group = true;
    for (const sib of err.errors) {
      const sibEntries: CapturedError[] = [];
      collectLinear(sib, undefined, sibEntries);
      // Mark the outermost of each sibling subchain as a child of the
      // AggregateError. After reversal + id stamping, the fixup loop in
      // `capture()` rewrites parent_id.
      const sibOuter = sibEntries[0]!;
      (sibOuter as CapturedError & { _aggParent?: string })._aggParent = entry.message;
      out.push(...sibEntries);
    }
    return;
  }

  // Standard ES2022 cause chain.
  if (err instanceof Error && err.cause !== undefined) {
    collectLinear(err.cause, undefined, out);
  }
}

function makeEntry(err: unknown, typeName: string | undefined): CapturedError {
  let type: string;
  let message: string;
  let frames: StackFrame[] = [];

  if (err instanceof Error) {
    type = typeName ?? err.constructor.name ?? 'Error';
    message = err.message;
    frames = parseStack(err.stack ?? '');
  } else if (typeof err === 'string') {
    type = typeName ?? 'string';
    message = err;
  } else if (err === null) {
    type = typeName ?? 'null';
    message = 'null';
  } else if (err === undefined) {
    type = typeName ?? 'undefined';
    message = 'undefined';
  } else {
    type = typeName ?? typeof err;
    try {
      message = String(err);
    } catch {
      message = '<unprintable>';
    }
  }

  return {
    type,
    message,
    frames,
    mechanism: defaultMechanism(),
    release: '',
    server_name: '',
  };
}

// ---- Stack parsing ---------------------------------------------------------

/**
 * Parse an `Error.stack` string into structured frames.
 *
 * V8 (Node, Chrome) is the primary target:
 *   "Error: msg\n    at func (file:line:col)\n    at file:line:col\n..."
 *
 * SpiderMonkey (Firefox) / JavaScriptCore (Safari) use:
 *   "func@file:line:col\nfunc@file:line:col\n..."
 *
 * Returned frames are most-recent-call-first (matches the engine's
 * native order — no reversal needed).
 */
export function parseStack(stack: string): StackFrame[] {
  const out: StackFrame[] = [];
  const lines = stack.split('\n');
  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (line === '') continue;
    const v8 = parseV8Line(line);
    if (v8) {
      out.push(v8);
      continue;
    }
    const sm = parseSpiderMonkeyLine(line);
    if (sm) {
      out.push(sm);
      continue;
    }
    // First line is typically "ErrorClass: message" — skip.
  }
  return out;
}

const V8_FULL = /^at\s+(.+?)\s+\((.+):(\d+):(\d+)\)$/;
const V8_BARE = /^at\s+(.+):(\d+):(\d+)$/;
// Some V8 lines have no col: "at file:line"
const V8_NO_COL = /^at\s+(.+?)\s+\((.+):(\d+)\)$/;

function parseV8Line(line: string): StackFrame | null {
  if (!line.startsWith('at ')) return null;
  const m = V8_FULL.exec(line);
  if (m) {
    const frame = emptyStackFrame();
    frame.function = m[1]!;
    frame.file = m[2]!;
    frame.line = Number.parseInt(m[3]!, 10);
    frame.in_app = isAppFrame(frame.function, frame.file);
    return frame;
  }
  const m2 = V8_NO_COL.exec(line);
  if (m2) {
    const frame = emptyStackFrame();
    frame.function = m2[1]!;
    frame.file = m2[2]!;
    frame.line = Number.parseInt(m2[3]!, 10);
    frame.in_app = isAppFrame(frame.function, frame.file);
    return frame;
  }
  const m3 = V8_BARE.exec(line);
  if (m3) {
    const frame = emptyStackFrame();
    frame.file = m3[1]!;
    frame.line = Number.parseInt(m3[2]!, 10);
    frame.in_app = isAppFrame('', frame.file);
    return frame;
  }
  return null;
}

const SPIDERMONKEY = /^(.*?)@(.+?):(\d+):(\d+)$/;
const SPIDERMONKEY_NO_COL = /^(.*?)@(.+?):(\d+)$/;

function parseSpiderMonkeyLine(line: string): StackFrame | null {
  if (!line.includes('@')) return null;
  const m = SPIDERMONKEY.exec(line);
  if (m) {
    const frame = emptyStackFrame();
    frame.function = m[1]!;
    frame.file = m[2]!;
    frame.line = Number.parseInt(m[3]!, 10);
    frame.in_app = isAppFrame(frame.function, frame.file);
    return frame;
  }
  const m2 = SPIDERMONKEY_NO_COL.exec(line);
  if (m2) {
    const frame = emptyStackFrame();
    frame.function = m2[1]!;
    frame.file = m2[2]!;
    frame.line = Number.parseInt(m2[3]!, 10);
    frame.in_app = isAppFrame(frame.function, frame.file);
    return frame;
  }
  return null;
}

const NON_APP_FILE_PREFIXES = ['node:', 'internal/', 'node_modules/'];
const NON_APP_FUNCTION_PREFIXES = ['node:', 'Promise.', 'process.'];

/**
 * Default `in_app` heuristic. Node-stdlib and node_modules frames are flagged
 * non-app; everything else is app. Producers with a better signal (a bundler
 * manifest, e.g.) should override.
 */
export function isAppFrame(fn: string, file: string): boolean {
  for (const p of NON_APP_FILE_PREFIXES) {
    if (file.startsWith(p) || file.includes(`/${p}`)) return false;
  }
  for (const p of NON_APP_FUNCTION_PREFIXES) {
    if (fn.startsWith(p)) return false;
  }
  return true;
}
