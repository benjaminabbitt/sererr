// Public API for the `sererr` npm package.
//
// Two layers:
//   - Plain types (StackFrame / ExceptionMechanism / CapturedError / DebugInfo)
//     and the producer entry points (capture, toDebugInfo,
//     populateSourceContext). Zero protobuf runtime dependency at this
//     boundary.
//   - Proto adapter (`sererr/proto`) for callers that need wire bytes.

export type {
  StackFrame,
  ExceptionMechanism,
  CapturedError,
  DebugInfo,
} from './types.js';
export {
  emptyStackFrame,
  emptyExceptionMechanism,
  emptyCapturedError,
  emptyDebugInfo,
  isStackFrame,
  isExceptionMechanism,
  isCapturedError,
} from './types.js';

export { capture, parseStack, isAppFrame } from './capture.js';
export { toDebugInfo } from './debug-info.js';
export { populateSourceContext, type SourceProvider } from './source.js';

export {
  toProto,
  fromProto,
  encodeCapturedError,
  decodeCapturedError,
} from './proto/adapter.js';
