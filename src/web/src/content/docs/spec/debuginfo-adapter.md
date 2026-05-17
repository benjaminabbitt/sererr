---
title: DebugInfo adapter
description: Convert sererr CapturedError to google.rpc.DebugInfo for gRPC error-model tooling.
---

`google.rpc.DebugInfo` (from `google/rpc/error_details.proto`, part of
the gRPC error model / AIP-193) is the universal protobuf shape that
existing gRPC tooling reads when looking for stack-trace info in
`google.rpc.Status.details[]`:

```protobuf
message DebugInfo {
  repeated string stack_entries = 1;  // pre-rendered frame lines
  string detail = 2;                  // human-readable description
}
```

Sererr is **strictly richer** — structured frames, error type,
mechanism, chain linkage, release, server. To stay interoperable with
DebugInfo-aware tooling, every sererr language library ships a
`.ToDebugInfo()` adapter that flattens a `CapturedError` into a
`DebugInfo`:

| Language | Adapter call |
|---|---|
| Rust | `let di: google_rpc::DebugInfo = err.to_debug_info();` |
| Python | `di = captured.to_debug_info()` |
| Go | `di := captured.ToDebugInfo()` |
| Java | `DebugInfo di = captured.toDebugInfo();` |
| Kotlin | `val di = captured.toDebugInfo()` |
| C# | `var di = captured.ToDebugInfo();` |
| TypeScript | `const di = toDebugInfo(captured);` |

## Mapping

Each adapter implements this mapping:

| `DebugInfo` field | Source |
|---|---|
| `stack_entries[i]` | one entry per `CapturedError.frames[i]`, rendered as `"  at <function> (<file>:<line>)"` — most-recent-call-first |
| `detail` | `"<type>: <message>"` joined across the chain with `"\nCaused by: "`, most-causal-last |

For a `repeated CapturedError` chain (sererr's wire shape), the
adapter walks the array; `stack_entries` concatenates frames from each
chain entry separated by a `"Caused by: <type>: <message>"` separator
line so the resulting flat list reads top-to-bottom like a familiar
language-formatted exception trace.

## Why an adapter, not an embed

Two alternatives were considered:

- **Embed `DebugInfo` inside `CapturedError`** (`google.rpc.DebugInfo debug_info = 1;`) so the proto IS a strict superset. Rejected: forces producers to populate the same data twice on every encode; adds a dependency from sererr's proto to `google/rpc/error_details.proto`.
- **Attach both as `Status::details[]`** (sender includes a `DebugInfo` Any AND a `CapturedError` Any). Workable, but requires every producer to know about both types and serialize twice.

The adapter approach keeps sererr's proto clean and self-contained.
Producers serialize `CapturedError`. Consumers that prefer
`DebugInfo` call `.ToDebugInfo()` either at the producer (before
attaching to a `Status`) or at the consumer (when reading a
`CapturedError` they want to forward to gRPC-error-aware tooling).

## Attaching to `google.rpc.Status`

For producers wiring sererr captures into gRPC errors, the recommended
pattern is to attach the `CapturedError` directly to
`Status::details[]` as `google.protobuf.Any` — clients walking
`Status::details` unpack whichever types they recognize:

```rust
let mut status = google_rpc::Status { code: code as i32, message, details: vec![] };
for entry in captured_chain {
    let mut any = google::protobuf::Any::default();
    any.pack(&entry).unwrap();
    status.details.push(any);
}
// AND, for legacy interop, also attach a DebugInfo:
let mut any = google::protobuf::Any::default();
any.pack(&captured_chain.last().unwrap().to_debug_info()).unwrap();
status.details.push(any);
```

Future direction: register `sererr.v1.CapturedError` as a
well-known detail type in
[`grpc/proposal`](https://github.com/grpc/proposal) so SDK-level
tooling recognizes it natively.
