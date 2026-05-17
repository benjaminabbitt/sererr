// Plain types for sererr capture.
//
// These shapes carry the cross-language contract documented in
// `proto/sererr/sererr.proto`. Field names match the proto's snake_case
// to keep fixture descriptors (the canonical JSON) and runtime objects
// structurally identical — fewer rename hops between layers.
//
// The proto adapter (src/proto/adapter.ts) is the only place that bridges
// from these snake_case fields to @bufbuild/protobuf's generated camelCase
// types. Keep this file free of proto runtime imports.

/** A single frame in a captured stack trace. Mirrors `sererr.v1.StackFrame`. */
export interface StackFrame {
  function: string;
  module: string;
  package: string;
  file: string;
  abs_path: string;
  /** 1-based line number; 0 = unknown. */
  line: number;
  context_line: string;
  pre_context: string[];
  post_context: string[];
  source_link: string;
  in_app: boolean;
}

/** Describes how an exception was captured. Mirrors `sererr.v1.ExceptionMechanism`. */
export interface ExceptionMechanism {
  type: string;
  description: string;
  handled: boolean;
  synthetic: boolean;
  help_link: string;
  source: string;
  exception_id: number;
  parent_id: number;
  is_exception_group: boolean;
  /**
   * Mechanism-specific metadata. Insertion order matters: the proto adapter
   * sorts keys before encoding so the wire bytes are deterministic (matches
   * Rust's `BTreeMap` ordering for cross-language conformance).
   */
  data: Record<string, string>;
}

/** A captured error. Mirrors `sererr.v1.CapturedError`. */
export interface CapturedError {
  type: string;
  message: string;
  frames: StackFrame[];
  mechanism?: ExceptionMechanism;
  release: string;
  /**
   * Producing host / pod name (proto field `server_name`).
   *
   * We expose the snake_case form to keep parity with the cross-language
   * fixture descriptors. Callers using the `capture()` ergonomic
   * `serverName` option get this populated automatically.
   */
  server_name: string;
}

/** `google.rpc.DebugInfo`-shaped adapter output. */
export interface DebugInfo {
  stack_entries: string[];
  detail: string;
}

// ---- Factories (proto3 zero values) ----------------------------------------

export function emptyStackFrame(): StackFrame {
  return {
    function: '',
    module: '',
    package: '',
    file: '',
    abs_path: '',
    line: 0,
    context_line: '',
    pre_context: [],
    post_context: [],
    source_link: '',
    in_app: false,
  };
}

export function emptyExceptionMechanism(): ExceptionMechanism {
  return {
    type: '',
    description: '',
    handled: false,
    synthetic: false,
    help_link: '',
    source: '',
    exception_id: 0,
    parent_id: 0,
    is_exception_group: false,
    data: {},
  };
}

export function emptyCapturedError(): CapturedError {
  return {
    type: '',
    message: '',
    frames: [],
    mechanism: undefined,
    release: '',
    server_name: '',
  };
}

export function emptyDebugInfo(): DebugInfo {
  return { stack_entries: [], detail: '' };
}

// ---- Type guards -----------------------------------------------------------

function isObject(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null;
}

export function isStackFrame(v: unknown): v is StackFrame {
  if (!isObject(v)) return false;
  return (
    typeof v.function === 'string' &&
    typeof v.module === 'string' &&
    typeof v.package === 'string' &&
    typeof v.file === 'string' &&
    typeof v.abs_path === 'string' &&
    typeof v.line === 'number' &&
    typeof v.context_line === 'string' &&
    Array.isArray(v.pre_context) &&
    Array.isArray(v.post_context) &&
    typeof v.source_link === 'string' &&
    typeof v.in_app === 'boolean'
  );
}

export function isExceptionMechanism(v: unknown): v is ExceptionMechanism {
  if (!isObject(v)) return false;
  return (
    typeof v.type === 'string' &&
    typeof v.description === 'string' &&
    typeof v.handled === 'boolean' &&
    typeof v.synthetic === 'boolean' &&
    typeof v.help_link === 'string' &&
    typeof v.source === 'string' &&
    typeof v.exception_id === 'number' &&
    typeof v.parent_id === 'number' &&
    typeof v.is_exception_group === 'boolean' &&
    isObject(v.data)
  );
}

export function isCapturedError(v: unknown): v is CapturedError {
  if (!isObject(v)) return false;
  return (
    typeof v.type === 'string' &&
    typeof v.message === 'string' &&
    Array.isArray(v.frames) &&
    typeof v.release === 'string' &&
    typeof v.server_name === 'string' &&
    (v.mechanism === undefined || isExceptionMechanism(v.mechanism))
  );
}
