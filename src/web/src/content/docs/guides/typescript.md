---
title: TypeScript / JavaScript
description: Capture errors with the sererr npm package.
---

```typescript
import { capture } from 'sererr';

const chain = capture(err, { release: RELEASE, serverName: SERVER_NAME });
```

## Idiosyncracies

- **`Error.stack` is non-standard.** V8 (Node/Chrome), SpiderMonkey
  (Firefox), and JavaScriptCore (Safari) all emit different formats.
  The library detects the engine and parses appropriately.
- Frame order in `Error.stack` is **most-recent-first** across all
  engines — matches our convention.
- Chain walks via `Error.cause` (ES2022). The library also handles
  `AggregateError.errors` (plural — multiple causes from
  `Promise.any` / `Promise.allSettled`).
- **Async stack traces** require V8 with `--async-stack-traces` (Node
  12.10+, default in modern Node). Older runtimes lose the await
  boundary frames.
- **Production bundles** ship minified — `Error.stack` shows mangled
  names. Ship source maps (Webpack/Vite/Rollup `devtool: 'source-map'`
  or equivalent) and the library's source-map consumer resolves frame
  locations on demand.

## Source bundling

For Node-based producers, embed source files at build time and pass
the embed table to the capture options:

```typescript
import sourceFS from './generated-source-fs'; // produced at build time

const chain = capture(err, { release: RELEASE, sourceFS });
```

A build-time helper (`sererr-build`) walks your `src/` and generates
the `sourceFS` module.

## Manual recipe

```typescript
import { CapturedError, StackFrame, ExceptionMechanism } from 'sererr';

const FRAMEWORK_PREFIXES = ['node:', 'internal/', '<anonymous>'];

interface CaptureOptions {
  release: string;
  serverName: string;
}

export function capture(err: unknown, { release, serverName }: CaptureOptions): CapturedError[] {
  const chain: CapturedError[] = [];
  let current = err;

  while (current instanceof Error) {
    chain.push({
      type: current.constructor.name,
      message: current.message ?? '',
      frames: parseStack(current.stack ?? ''),
      mechanism: {
        type: 'generic',
        handled: true,
        synthetic: false,
        helpLink: '',
        source: '',
        exceptionId: 0,
        parentId: 0,
        isExceptionGroup: false,
        data: {},
      },
      release,
      serverName,
    });

    // ES2022 Error.cause
    current = (current as Error & { cause?: unknown }).cause;
  }

  chain.reverse(); // most-causal-first
  chain.forEach((entry, i) => {
    entry.mechanism!.exceptionId = i;
    entry.mechanism!.parentId = i === 0 ? 0 : i - 1;
  });
  return chain;
}

function parseStack(stack: string): StackFrame[] {
  // V8 format: "    at function (file:line:col)"
  const re = /^\s*at\s+(?:(.+?)\s+\()?(.+?):(\d+):(\d+)\)?$/;
  return stack
    .split('\n')
    .slice(1) // drop the "Error: message" header line
    .map((line) => {
      const m = line.match(re);
      if (!m) return null;
      const [, fn, file, lineNo] = m;
      return {
        function: fn ?? '<anonymous>',
        module: '',
        package: '',
        file: file ?? '',
        absPath: '',
        line: Number(lineNo),
        contextLine: '',
        preContext: [],
        postContext: [],
        sourceLink: '',
        inApp: !FRAMEWORK_PREFIXES.some((p) => (file ?? '').startsWith(p)),
      };
    })
    .filter((f): f is StackFrame => f !== null);
}
```
