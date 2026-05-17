---
title: Frame ordering
description: How each language's stdlib orders frames, and how sererr normalizes.
---

> *Stub.*

**On the wire:** sererr emits frames **most-recent-call-first** per
`CapturedError`, and chain entries **most-causal-first** per
`stack_trace` array.

**Per-language stdlib defaults:**

| Language | Stdlib frame order | Sererr action |
|---|---|---|
| Python `traceback.StackSummary` | oldest-first | reverse on encode |
| Rust `Backtrace::Display` | outermost-first | reverse on encode |
| Java `Throwable.getStackTrace()` | most-recent-first | no change |
| Kotlin (JVM) | most-recent-first | no change |
| Go `runtime.CallersFrames` | most-recent-first | no change |
| C# `StackTrace.GetFrames()` | most-recent-first | no change |

Producers MUST normalize on encode if their stdlib disagrees.
