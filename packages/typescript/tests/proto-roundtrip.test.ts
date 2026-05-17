// Why: This file pins the wire-format contract. Every fixture in
// tests/conformance/fixtures must encode to byte-identical bytes across
// languages. JS map insertion order + @bufbuild/protobuf's map encoding
// makes this non-trivial — sorted-key pre-insertion is the fix.
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { dirname } from 'node:path';

import { toProto, fromProto, encodeCapturedError, decodeCapturedError } from '../src/proto/adapter.js';
import { emptyCapturedError, type CapturedError } from '../src/types.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const FIXTURES = join(__dirname, '..', '..', '..', 'tests', 'conformance', 'fixtures');

interface FixtureDescriptor {
  captured_error?: {
    type?: string;
    message?: string;
    frames?: Array<Record<string, unknown>>;
    mechanism?: Record<string, unknown> | null;
    release?: string;
    server_name?: string;
  };
}

function loadFixture(name: string): CapturedError {
  const json = JSON.parse(readFileSync(join(FIXTURES, `${name}.json`), 'utf8')) as FixtureDescriptor;
  const c = json.captured_error ?? {};
  const base = emptyCapturedError();
  return {
    ...base,
    type: c.type ?? '',
    message: c.message ?? '',
    release: c.release ?? '',
    server_name: c.server_name ?? '',
    frames: (c.frames ?? []).map((f) => ({
      function: (f.function as string) ?? '',
      module: (f.module as string) ?? '',
      package: (f.package as string) ?? '',
      file: (f.file as string) ?? '',
      abs_path: (f.abs_path as string) ?? '',
      line: (f.line as number) ?? 0,
      context_line: (f.context_line as string) ?? '',
      pre_context: (f.pre_context as string[]) ?? [],
      post_context: (f.post_context as string[]) ?? [],
      source_link: (f.source_link as string) ?? '',
      in_app: (f.in_app as boolean) ?? false,
    })),
    mechanism: c.mechanism
      ? {
          type: (c.mechanism.type as string) ?? '',
          description: (c.mechanism.description as string) ?? '',
          handled: (c.mechanism.handled as boolean) ?? false,
          synthetic: (c.mechanism.synthetic as boolean) ?? false,
          help_link: (c.mechanism.help_link as string) ?? '',
          source: (c.mechanism.source as string) ?? '',
          exception_id: (c.mechanism.exception_id as number) ?? 0,
          parent_id: (c.mechanism.parent_id as number) ?? 0,
          is_exception_group: (c.mechanism.is_exception_group as boolean) ?? false,
          data: ((c.mechanism.data as Record<string, string>) ?? {}),
        }
      : undefined,
  };
}

const FIXTURE_NAMES = [
  '0001-simple',
  '0002-empty-chain',
  '0003-three-deep',
  '0004-source-context',
  '0005-mechanism-data',
];

describe('proto byte-equivalence', () => {
  for (const name of FIXTURE_NAMES) {
    it(`fixture ${name} encodes to canonical bytes`, () => {
      // Why: cross-language conformance — bytes must match Rust's prost output
      // for every fixture. Map encoding determinism is the load-bearing detail.
      const plain = loadFixture(name);
      const actual = encodeCapturedError(plain);
      const expected = readFileSync(join(FIXTURES, `${name}.pb`));
      expect(Buffer.from(actual)).toEqual(expected);
    });
  }

  it('default CapturedError encodes to empty bytes', () => {
    // Why: encoding.feature "Empty CapturedError encodes as empty bytes".
    const bytes = encodeCapturedError(emptyCapturedError());
    expect(bytes.length).toBe(0);
  });

  it('round-trips a fixture through encode + decode', () => {
    // Why: encoding.feature "Default values round-trip".
    const plain = loadFixture('0001-simple');
    const bytes = encodeCapturedError(plain);
    const decoded = decodeCapturedError(bytes);
    expect(decoded).toEqual(plain);
  });

  it('toProto preserves mechanism.data sort order at the wire', () => {
    // Why: JS objects preserve insertion order; @bufbuild/protobuf encodes
    // maps in iteration order. Pre-sorting keys yields the sorted-key wire
    // bytes Rust's BTreeMap produces. This test asserts the adapter does it.
    const plain: CapturedError = {
      ...emptyCapturedError(),
      mechanism: {
        type: '',
        description: '',
        handled: false,
        synthetic: false,
        help_link: '',
        source: '',
        exception_id: 0,
        parent_id: 0,
        is_exception_group: false,
        data: { z: '1', a: '2', m: '3' },
      },
    };
    const proto = toProto(plain);
    const keys = Object.keys(proto.mechanism!.data);
    expect(keys).toEqual(['a', 'm', 'z']);
  });

  it('fromProto round-trips a plain capture', () => {
    // Why: bidirectional fidelity — fromProto(toProto(x)) === x for valid inputs.
    const plain = loadFixture('0005-mechanism-data');
    const proto = toProto(plain);
    const back = fromProto(proto);
    expect(back).toEqual(plain);
  });
});
