// @ts-nocheck
// Why: Targets Stryker survivors in src/proto/adapter.ts:
//   - Lines 98-99 `[...f.preContext]` / `[...f.postContext]` (array spread
//     must produce a real copy, not an empty array)
//   - Line 119 `Object.keys(m.data).sort()` (the sort is load-bearing)
//
import { describe, it, expect } from 'vitest';
import { create } from '@bufbuild/protobuf';
import { toProto, fromProto, encodeCapturedError, decodeCapturedError } from '../src/proto/adapter.js';
import { CapturedErrorSchema, ExceptionMechanismSchema } from '../src/proto/gen/sererr/sererr_pb.js';
import { emptyCapturedError, emptyExceptionMechanism, emptyStackFrame } from '../src/types.js';

describe('adapter: stackFrameFromProto preserves pre/post context arrays', () => {
  it('decode preserves pre_context contents (non-empty)', () => {
    // Why: pins line 98 `out.pre_context = [...f.preContext]`. If mutated
    // to `[]`, the round-trip would drop the context lines.
    const plain = {
      ...emptyCapturedError(),
      frames: [
        {
          ...emptyStackFrame(),
          file: 'x.ts',
          line: 5,
          context_line: 'middle',
          pre_context: ['a', 'b'],
          post_context: ['c', 'd'],
        },
      ],
    };
    const encoded = encodeCapturedError(plain);
    const decoded = decodeCapturedError(encoded);
    expect(decoded.frames[0]!.pre_context).toEqual(['a', 'b']);
  });

  it('decode preserves post_context contents (non-empty)', () => {
    // Why: pins line 99 `out.post_context = [...f.postContext]`.
    const plain = {
      ...emptyCapturedError(),
      frames: [
        {
          ...emptyStackFrame(),
          file: 'x.ts',
          line: 5,
          context_line: 'middle',
          pre_context: ['a', 'b'],
          post_context: ['c', 'd'],
        },
      ],
    };
    const decoded = decodeCapturedError(encodeCapturedError(plain));
    expect(decoded.frames[0]!.post_context).toEqual(['c', 'd']);
  });
});

describe('adapter: mechanismFromProto sorts data keys on decode', () => {
  it('decode produces sorted key iteration order', () => {
    // Why: pins line 119 `Object.keys(m.data).sort()`. Without the sort,
    // the decoded order would mirror the protobuf runtime's wire-order
    // delivery — which is sorted on encode but not guaranteed identical
    // on decode.
    const plain = {
      ...emptyCapturedError(),
      mechanism: {
        ...emptyExceptionMechanism(),
        data: { z: '1', a: '2', m: '3' },
      },
    };
    const decoded = decodeCapturedError(encodeCapturedError(plain));
    expect(Object.keys(decoded.mechanism!.data)).toEqual(['a', 'm', 'z']);
  });

  it('toProto + fromProto round-trips data with sorted keys', () => {
    // Why: same as above via the toProto/fromProto path.
    const plain = {
      ...emptyCapturedError(),
      mechanism: {
        ...emptyExceptionMechanism(),
        data: { z: '1', a: '2' },
      },
    };
    const back = fromProto(toProto(plain));
    expect(Object.keys(back.mechanism!.data)).toEqual(['a', 'z']);
  });

  it('fromProto on an unsorted-data proto returns sorted keys', () => {
    // Why: pins line 119 `Object.keys(m.data).sort()` against a proto
    // whose `data` insertion order is NOT alphabetical. Without the sort,
    // decoded `data` would retain the unsorted order. Constructing the
    // proto directly (rather than going through encode → decode, which
    // already sorts on encode) is what makes this assertion bite.
    const protoMech = create(ExceptionMechanismSchema, {
      data: { z: '1', a: '2', m: '3' },
    });
    const protoCap = create(CapturedErrorSchema, { mechanism: protoMech });
    const decoded = fromProto(protoCap);
    expect(Object.keys(decoded.mechanism!.data)).toEqual(['a', 'm', 'z']);
  });
});
