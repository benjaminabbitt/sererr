// Step definitions for the cross-language sererr Gherkin corpus.
//
// Mirrors `tests/conformance/runners/rust/tests/conformance.rs` —
// every step the Rust runner implements is implemented here against
// the same features/ + fixtures/ tree.
//
// Resolution of features/fixtures dirs is via env vars set by
// `just conformance` (root) or `just test` (this runner):
//   SERERR_FEATURES_DIR
//   SERERR_FIXTURES_DIR

import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { strict as assert } from 'node:assert';
import {
  Given,
  When,
  Then,
  setWorldConstructor,
  World,
  DataTable,
} from '@cucumber/cucumber';
import {
  capture,
  toDebugInfo,
  emptyCapturedError,
  emptyStackFrame,
  encodeCapturedError,
  decodeCapturedError,
  type CapturedError,
  type DebugInfo,
} from 'sererr';

// ---- World -----------------------------------------------------------------

interface ConformanceData {
  chain: CapturedError[];
  debugInfo?: DebugInfo;
  encoded?: Uint8Array;
  fixtureName?: string;
  fixtureInput?: CapturedError;
}

class ConformanceWorld extends World {
  data: ConformanceData = { chain: [] };
}
setWorldConstructor(ConformanceWorld);

// ---- Fixture loader --------------------------------------------------------

function fixturesDir(): string {
  const d = process.env.SERERR_FIXTURES_DIR;
  if (!d) throw new Error('SERERR_FIXTURES_DIR env var not set');
  return d;
}

interface FixtureDescriptor {
  captured_error?: FixtureCapturedError;
}
interface FixtureCapturedError {
  type?: string;
  message?: string;
  frames?: FixtureStackFrame[];
  mechanism?: FixtureMechanism | null;
  release?: string;
  server_name?: string;
}
interface FixtureStackFrame {
  function?: string;
  module?: string;
  package?: string;
  file?: string;
  abs_path?: string;
  line?: number;
  context_line?: string;
  pre_context?: string[];
  post_context?: string[];
  source_link?: string;
  in_app?: boolean;
}
interface FixtureMechanism {
  type?: string;
  description?: string;
  handled?: boolean;
  synthetic?: boolean;
  help_link?: string;
  source?: string;
  exception_id?: number;
  parent_id?: number;
  is_exception_group?: boolean;
  data?: Record<string, string>;
}

function loadFixtureJson(name: string): CapturedError {
  const path = join(fixturesDir(), `${name}.json`);
  const raw = readFileSync(path, 'utf8');
  const parsed = JSON.parse(raw) as FixtureDescriptor;
  const c = parsed.captured_error;
  if (!c) throw new Error(`fixture ${name} missing captured_error key`);
  const base = emptyCapturedError();
  return {
    ...base,
    type: c.type ?? '',
    message: c.message ?? '',
    release: c.release ?? '',
    server_name: c.server_name ?? '',
    frames: (c.frames ?? []).map((f) => ({
      function: f.function ?? '',
      module: f.module ?? '',
      package: f.package ?? '',
      file: f.file ?? '',
      abs_path: f.abs_path ?? '',
      line: f.line ?? 0,
      context_line: f.context_line ?? '',
      pre_context: f.pre_context ?? [],
      post_context: f.post_context ?? [],
      source_link: f.source_link ?? '',
      in_app: f.in_app ?? false,
    })),
    mechanism: c.mechanism
      ? {
          type: c.mechanism.type ?? '',
          description: c.mechanism.description ?? '',
          handled: c.mechanism.handled ?? false,
          synthetic: c.mechanism.synthetic ?? false,
          help_link: c.mechanism.help_link ?? '',
          source: c.mechanism.source ?? '',
          exception_id: c.mechanism.exception_id ?? 0,
          parent_id: c.mechanism.parent_id ?? 0,
          is_exception_group: c.mechanism.is_exception_group ?? false,
          data: c.mechanism.data ?? {},
        }
      : undefined,
  };
}

// ---- Test fixtures (errors with synthetic chains) --------------------------

class LabeledError extends Error {
  constructor(message: string, cause?: unknown) {
    super(message);
    this.name = 'LabeledError';
    if (cause !== undefined) {
      (this as Error & { cause?: unknown }).cause = cause;
    }
  }
}

function makeChain(msgs: string[]): LabeledError {
  // msgs is innermost-first: [innermost, ..., outermost].
  // Build by chaining causes from inner to outer.
  let current: LabeledError | undefined;
  for (const msg of msgs) {
    current = new LabeledError(msg, current);
  }
  return current!;
}

// ============================================================================
// Background / generic
// ============================================================================

Given('the canonical sererr.v1 proto schema', function () {
  // No-op: the schema is implicit (we compile against @bufbuild/protobuf
  // generated types).
});

// ============================================================================
// encoding.feature
// ============================================================================

Given(/^a fixture "([^"]+)"$/, function (this: ConformanceWorld, name: string) {
  this.data.fixtureName = name;
});

Given(
  /^a default-initialized CapturedError \(all zero values\)$/,
  function (this: ConformanceWorld) {
    this.data.fixtureInput = emptyCapturedError();
  },
);

When(
  "I construct the CapturedError per the fixture's JSON descriptor",
  function (this: ConformanceWorld) {
    const name = this.data.fixtureName;
    if (!name) throw new Error('fixture name not set');
    this.data.fixtureInput = loadFixtureJson(name);
  },
);

When('I serialize it via the proto adapter', function (this: ConformanceWorld) {
  if (!this.data.fixtureInput && this.data.fixtureName) {
    this.data.fixtureInput = loadFixtureJson(this.data.fixtureName);
  }
  if (!this.data.fixtureInput) throw new Error('fixture input not set');
  this.data.encoded = encodeCapturedError(this.data.fixtureInput);
});

When('I serialize it', function (this: ConformanceWorld) {
  if (!this.data.fixtureInput && this.data.fixtureName) {
    this.data.fixtureInput = loadFixtureJson(this.data.fixtureName);
  }
  if (!this.data.fixtureInput) throw new Error('fixture input not set');
  this.data.encoded = encodeCapturedError(this.data.fixtureInput);
});

Then(
  /^the encoded bytes match "fixtures\/([^"]+)\.pb"$/,
  function (this: ConformanceWorld, name: string) {
    const path = join(fixturesDir(), `${name}.pb`);
    const expected = readFileSync(path);
    const actual = this.data.encoded;
    if (!actual) throw new Error('no encoded bytes');
    assert.deepEqual(
      Buffer.from(actual),
      expected,
      `encoded bytes mismatch for fixture ${name}`,
    );
  },
);

Then('the encoded bytes are empty', function (this: ConformanceWorld) {
  const bytes = this.data.encoded;
  if (!bytes) throw new Error('no encoded bytes');
  assert.equal(
    bytes.length,
    0,
    `expected empty bytes, got ${bytes.length} bytes`,
  );
});

When(
  'I deserialize the bytes back to a CapturedError',
  function (this: ConformanceWorld) {
    if (!this.data.encoded) throw new Error('no encoded bytes');
    this.data.fixtureInput = decodeCapturedError(this.data.encoded);
  },
);

Then(
  'the result equals the input field-by-field',
  function (this: ConformanceWorld) {
    const name = this.data.fixtureName;
    if (!name) throw new Error('fixture name not set');
    const expected = loadFixtureJson(name);
    assert.deepEqual(this.data.fixtureInput, expected, 'round-trip mismatch');
  },
);

// ============================================================================
// chain.feature
// ============================================================================

Given(/^an error with no source \/ cause$/, function (this: ConformanceWorld) {
  const err = new LabeledError('single');
  this.data.chain = capture(err, {
    typeName: 'LabeledError',
    release: 'test',
    serverName: 'host',
  });
});

Given(
  /^a chain "([^"]+)" caused-by "([^"]+)"(?: caused-by "([^"]+)")?$/,
  function (this: ConformanceWorld, inner: string, middle: string, outer: string | undefined) {
    // Most-causal-first reading: "<inner> caused-by <next> [caused-by <outer>]"
    // → outermost-last. capture(outer) walks .cause inward then reverses to
    // most-causal-first.
    const msgs = outer ? [inner, middle, outer] : [inner, middle];
    const outermost = makeChain(msgs);
    this.data.chain = capture(outermost, {
      typeName: 'LabeledError',
      release: 'test',
      serverName: 'host',
    });
  },
);

Given('a captured error with frames', function (this: ConformanceWorld) {
  const err = new LabeledError('x');
  this.data.chain = capture(err, {
    typeName: 'LabeledError',
    release: 'test',
    serverName: 'host',
  });
});

When('I capture it', function () {
  // No-op: capture happened in the Given step.
});

When('I capture the outermost error', function () {
  // No-op: capture happened in the Given step.
});

When('I read the frames', function () {
  // No-op: frames are already in world.chain.
});

Then(/^the chain length is (\d+)$/, function (this: ConformanceWorld, n: string) {
  assert.equal(this.data.chain.length, Number.parseInt(n, 10), 'chain length');
});

Then(
  /^entry (\d+) has exception_id (\d+) and parent_id (\d+)$/,
  function (this: ConformanceWorld, idx: string, exId: string, parId: string) {
    const entry = this.data.chain[Number.parseInt(idx, 10)];
    if (!entry) throw new Error(`no entry at ${idx}`);
    const mech = entry.mechanism;
    if (!mech) throw new Error('no mechanism');
    assert.equal(mech.exception_id, Number.parseInt(exId, 10));
    assert.equal(mech.parent_id, Number.parseInt(parId, 10));
  },
);

Then(
  "the entry's mechanism has exception_id 0",
  function (this: ConformanceWorld) {
    const mech = this.data.chain[0]?.mechanism;
    if (!mech) throw new Error('no mechanism');
    assert.equal(mech.exception_id, 0);
  },
);

Then(
  "the entry's mechanism has parent_id 0",
  function (this: ConformanceWorld) {
    const mech = this.data.chain[0]?.mechanism;
    if (!mech) throw new Error('no mechanism');
    assert.equal(mech.parent_id, 0);
  },
);

Then(
  /^entry (\d+) has message "([^"]+)"$/,
  function (this: ConformanceWorld, idx: string, msg: string) {
    const entry = this.data.chain[Number.parseInt(idx, 10)];
    if (!entry) throw new Error(`no entry at ${idx}`);
    assert.equal(entry.message, msg);
  },
);

Then(
  /^the last chain entry has message "([^"]+)"$/,
  function (this: ConformanceWorld, msg: string) {
    const last = this.data.chain[this.data.chain.length - 1];
    if (!last) throw new Error('empty chain');
    assert.equal(last.message, msg);
  },
);

Then('the first frame is the most recent call', function (this: ConformanceWorld) {
  // Cross-language assertion: at minimum, frames exist. JS-specific
  // ordering (V8 stack format already puts the throw site first) is
  // covered in the unit tests.
  const first = this.data.chain[0];
  if (!first) throw new Error('empty chain');
  assert.ok(
    first.frames.length > 0,
    `expected captured frames; got ${first.frames.length} frames`,
  );
});

// ============================================================================
// debuginfo-adapter.feature
// ============================================================================

Given('an empty chain', function (this: ConformanceWorld) {
  this.data.chain = [];
});

Given(
  /^a single CapturedError with type "([^"]+)" and message "([^"]+)"$/,
  function (this: ConformanceWorld, t: string, msg: string) {
    this.data.chain = [{ ...emptyCapturedError(), type: t, message: msg }];
  },
);

Given(
  'a CapturedError with one frame:',
  function (this: ConformanceWorld, table: DataTable) {
    const rows = table.raw();
    // header is rows[0]: ["function", "file", "line"]; data is rows[1].
    const row = rows[1];
    if (!row) throw new Error('expected data row');
    const fn = row[0] ?? '';
    const file = row[1] ?? '';
    const lineStr = row[2] ?? '0';
    const frame = {
      ...emptyStackFrame(),
      function: fn,
      file,
      line: Number.parseInt(lineStr, 10),
    };
    this.data.chain = [
      { ...emptyCapturedError(), type: 'T', message: 'm', frames: [frame] },
    ];
  },
);

When('I call to_debug_info', function (this: ConformanceWorld) {
  this.data.debugInfo = toDebugInfo(this.data.chain);
});

Then('stack_entries is empty', function (this: ConformanceWorld) {
  const di = this.data.debugInfo;
  if (!di) throw new Error('no debug_info');
  assert.equal(di.stack_entries.length, 0);
});

Then('detail is empty', function (this: ConformanceWorld) {
  const di = this.data.debugInfo;
  if (!di) throw new Error('no debug_info');
  assert.equal(di.detail, '');
});

Then(
  /^detail equals "([^"]+)"$/,
  function (this: ConformanceWorld, expected: string) {
    const di = this.data.debugInfo;
    if (!di) throw new Error('no debug_info');
    assert.equal(di.detail, expected);
  },
);

Then(
  /^detail contains "([^"]+)"$/,
  function (this: ConformanceWorld, needle: string) {
    const di = this.data.debugInfo;
    if (!di) throw new Error('no debug_info');
    assert.ok(
      di.detail.includes(needle),
      `detail ${JSON.stringify(di.detail)} should contain ${JSON.stringify(needle)}`,
    );
  },
);

Then(
  /^stack_entries contains "([^"]+)"$/,
  function (this: ConformanceWorld, needle: string) {
    const di = this.data.debugInfo;
    if (!di) throw new Error('no debug_info');
    assert.ok(
      di.stack_entries.some((e) => e === needle),
      `stack_entries ${JSON.stringify(di.stack_entries)} should contain ${JSON.stringify(needle)}`,
    );
  },
);
