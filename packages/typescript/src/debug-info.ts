// google.rpc.DebugInfo adapter.
//
// Mirrors `to_debug_info` in `packages/rust/sererr/src/lib.rs`
// character-for-character — debuginfo-adapter.feature pins the strings.

import type { CapturedError, DebugInfo, StackFrame } from './types.js';
import { emptyDebugInfo } from './types.js';

export function toDebugInfo(chain: readonly CapturedError[]): DebugInfo {
  if (chain.length === 0) return emptyDebugInfo();

  const stackEntries: string[] = [];
  const detailParts: string[] = [];

  // Iterate the chain in reverse: outermost (most-recent) first.
  for (let i = chain.length - 1, idx = 0; i >= 0; i--, idx++) {
    const entry = chain[i]!;
    const header = `${entry.type}: ${entry.message}`;
    detailParts.push(idx === 0 ? header : `Caused by: ${header}`);
    stackEntries.push(idx === 0 ? header : `Caused by: ${header}`);
    for (const frame of entry.frames) {
      stackEntries.push(formatFrameLine(frame));
    }
  }

  return { stack_entries: stackEntries, detail: detailParts.join('\n') };
}

function formatFrameLine(frame: StackFrame): string {
  let location: string;
  if (frame.file === '') {
    location = '';
  } else if (frame.line > 0) {
    location = ` (${frame.file}:${frame.line})`;
  } else {
    location = ` (${frame.file})`;
  }
  const fn = frame.function === '' ? '<unknown>' : frame.function;
  return `  at ${fn}${location}`;
}
