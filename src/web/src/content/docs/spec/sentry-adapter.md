---
title: Sentry JSON adapter
description: Convert sererr CapturedError to a Sentry-ingestible JSON payload.
---

> *Stub.*

| Sentry path | Source |
|---|---|
| `exception.values[i].type` | `CapturedError.type` |
| `exception.values[i].value` | `CapturedError.message` |
| `exception.values[i].stacktrace.frames[j].filename` | `StackFrame.file` (rename) |
| `exception.values[i].stacktrace.frames[j].*` (everything else) | same-named `StackFrame` field |
| `exception.values[i].mechanism.*` | same-named `ExceptionMechanism` field |
| event-level `release` | `CapturedError.release` |
| event-level `server_name` | `CapturedError.server_name` |

Each language package ships an adapter that produces this payload from
the native `CapturedError` value.
