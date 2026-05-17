// Source-context population for captured frames.
//
// Mirrors the Rust `populate_source_context` in
// `packages/rust/sererr/src/lib.rs`. The 1-based / saturating-clamp /
// no-op-on-missing-data semantics are pinned by source.test.ts.

import type { StackFrame } from './types.js';

/**
 * Returns the contents of a source file by path, or `null` when the file
 * is not available (consumer doesn't embed it / lookup miss).
 */
export interface SourceProvider {
  getSource(file: string): string | null;
}

/**
 * Populate `context_line` / `pre_context` / `post_context` on `frame`
 * using `provider`. `surrounding` is the number of pre/post lines to
 * capture (5 matches the Sentry UI default).
 *
 * No-op when `frame.line` is 0 (unknown), the provider misses, or the
 * line is past the end of the file.
 */
export function populateSourceContext(
  frame: StackFrame,
  provider: SourceProvider,
  surrounding: number,
): void {
  if (frame.line === 0) return;
  const source = provider.getSource(frame.file);
  if (source === null) return;

  // `String.prototype.split('\n')` would synthesize a trailing empty
  // string if `source` ends with `\n`. Rust's `str::lines()` strips that
  // — replicate by trimming a single trailing newline before splitting.
  const trimmed = source.endsWith('\n') ? source.slice(0, -1) : source;
  const lines = trimmed.split('\n');
  const idx = frame.line - 1;
  if (idx >= lines.length || idx < 0) return;

  frame.context_line = lines[idx]!;
  const start = Math.max(0, idx - surrounding);
  frame.pre_context = lines.slice(start, idx);
  const end = Math.min(lines.length, idx + 1 + surrounding);
  frame.post_context = lines.slice(idx + 1, end);
}
