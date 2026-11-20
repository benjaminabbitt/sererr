---
title: Proto schema
description: The canonical sererr proto definitions.
---

The canonical schema lives in
[`proto/sererr/sererr.proto`](https://github.com/sererr/sererr/blob/main/proto/sererr/sererr.proto).
Three messages: `StackFrame`, `ExceptionMechanism`, `CapturedError`.

Cause chains are a **flat array** carried by the enclosing message
(most-causal-first; the originating caught error is the LAST element).
Matches Sentry's `exception.values`. Linkage via
`mechanism.exception_id` / `mechanism.parent_id`.

See the [conventions](/spec/conventions/) page for the ordering /
required-field rules and the [Sentry adapter](/spec/sentry-adapter/)
page for the JSON conversion mapping.

> *Stub. Full doc inlined here once Phase A completes.*
