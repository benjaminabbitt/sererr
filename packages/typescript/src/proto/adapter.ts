// Plain ↔ proto conversions for sererr.v1.
//
// @bufbuild/protobuf's generated types use camelCase field names; the plain
// shapes in src/types.ts use snake_case (matching the cross-language
// fixture descriptors). This file is the only place the two name spaces
// touch.
//
// **Determinism.** `@bufbuild/protobuf` encodes map fields in iteration
// order. JS objects iterate in insertion order (per the language spec for
// string keys). To match Rust's `BTreeMap` byte output, we pre-sort the
// `data` map's keys before constructing the proto message. The
// proto-roundtrip.test.ts byte-equivalence assertions are the gatekeeper.

import { create, toBinary, fromBinary } from '@bufbuild/protobuf';
import type {
  StackFrame as ProtoStackFrame,
  ExceptionMechanism as ProtoExceptionMechanism,
  CapturedError as ProtoCapturedError,
} from './gen/sererr/sererr_pb.js';
import {
  StackFrameSchema,
  ExceptionMechanismSchema,
  CapturedErrorSchema,
} from './gen/sererr/sererr_pb.js';
import type {
  CapturedError,
  ExceptionMechanism,
  StackFrame,
} from '../types.js';
import {
  emptyCapturedError,
  emptyExceptionMechanism,
  emptyStackFrame,
} from '../types.js';

// ---- plain → proto ---------------------------------------------------------

function stackFrameToProto(f: StackFrame): ProtoStackFrame {
  return create(StackFrameSchema, {
    function: f.function,
    module: f.module,
    package: f.package,
    file: f.file,
    absPath: f.abs_path,
    line: f.line,
    contextLine: f.context_line,
    preContext: f.pre_context,
    postContext: f.post_context,
    sourceLink: f.source_link,
    inApp: f.in_app,
  });
}

function mechanismToProto(m: ExceptionMechanism): ProtoExceptionMechanism {
  // Pre-sort the data map keys: @bufbuild/protobuf encodes maps in
  // iteration order, and JS objects iterate string keys in insertion
  // order. Sorting here yields the same wire bytes as Rust's BTreeMap.
  const sortedData: Record<string, string> = {};
  for (const k of Object.keys(m.data).sort()) {
    sortedData[k] = m.data[k]!;
  }
  return create(ExceptionMechanismSchema, {
    type: m.type,
    description: m.description,
    handled: m.handled,
    synthetic: m.synthetic,
    helpLink: m.help_link,
    source: m.source,
    exceptionId: m.exception_id,
    parentId: m.parent_id,
    isExceptionGroup: m.is_exception_group,
    data: sortedData,
  });
}

export function toProto(e: CapturedError): ProtoCapturedError {
  return create(CapturedErrorSchema, {
    type: e.type,
    message: e.message,
    frames: e.frames.map(stackFrameToProto),
    mechanism: e.mechanism ? mechanismToProto(e.mechanism) : undefined,
    release: e.release,
    serverName: e.server_name,
  });
}

// ---- proto → plain ---------------------------------------------------------

function stackFrameFromProto(f: ProtoStackFrame): StackFrame {
  const out = emptyStackFrame();
  out.function = f.function;
  out.module = f.module;
  out.package = f.package;
  out.file = f.file;
  out.abs_path = f.absPath;
  out.line = f.line;
  out.context_line = f.contextLine;
  out.pre_context = [...f.preContext];
  out.post_context = [...f.postContext];
  out.source_link = f.sourceLink;
  out.in_app = f.inApp;
  return out;
}

function mechanismFromProto(m: ProtoExceptionMechanism): ExceptionMechanism {
  const out = emptyExceptionMechanism();
  out.type = m.type;
  out.description = m.description;
  out.handled = m.handled;
  out.synthetic = m.synthetic;
  out.help_link = m.helpLink;
  out.source = m.source;
  out.exception_id = m.exceptionId;
  out.parent_id = m.parentId;
  out.is_exception_group = m.isExceptionGroup;
  // Sort keys on decode too, so round-trip equality holds regardless of
  // the protobuf runtime's wire-order delivery.
  out.data = {};
  for (const k of Object.keys(m.data).sort()) {
    out.data[k] = m.data[k]!;
  }
  return out;
}

export function fromProto(p: ProtoCapturedError): CapturedError {
  const out = emptyCapturedError();
  out.type = p.type;
  out.message = p.message;
  out.frames = p.frames.map(stackFrameFromProto);
  out.mechanism = p.mechanism ? mechanismFromProto(p.mechanism) : undefined;
  out.release = p.release;
  out.server_name = p.serverName;
  return out;
}

// ---- bytes ↔ plain (convenience) -------------------------------------------

export function encodeCapturedError(e: CapturedError): Uint8Array {
  return toBinary(CapturedErrorSchema, toProto(e));
}

export function decodeCapturedError(bytes: Uint8Array): CapturedError {
  return fromProto(fromBinary(CapturedErrorSchema, bytes));
}
