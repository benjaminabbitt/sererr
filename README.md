# sererr

Sentry-compat structured stack-trace + error-chain capture, as a portable
protobuf schema with hand-tuned producer libraries in six languages.

## Why

There is no widely-adopted protobuf standard for structured stack frames.
The closest options are all flat text:

- `google.rpc.DebugInfo` (gRPC error model, AIP-193) — `repeated string`
- OpenTelemetry exception semantic conventions — language-formatted string
- Sentry's frame/exception schemas — the right shape, but a JSON schema
  in the Sentry product, not a published `.proto`

`sererr` provides one, **Sentry-naming-compatible** at the field level so
existing Sentry-aware tooling can ingest captures with a thin proto→JSON
adapter. The shape is documented in
[`docs/spec.md`](docs/spec.md); the canonical proto lives in
[`proto/sererr/sererr.proto`](proto/sererr/sererr.proto).

## What's in here

Seven language packages, each shipping the same capabilities:

| Package | Distribution | Path |
|---|---|---|
| Rust | crates.io `sererr` | [`packages/rust`](packages/rust) |
| Python | PyPI `sererr` | [`packages/python`](packages/python) |
| Go | `sererr.fyi/sererr` | [`packages/go`](packages/go) |
| Java | Maven Central `fyi.sererr:sererr` | [`packages/java`](packages/java) |
| Kotlin | Maven Central `fyi.sererr:sererr-kotlin` | [`packages/kotlin`](packages/kotlin) |
| C# | NuGet `Sererr` | [`packages/csharp`](packages/csharp) |
| TypeScript / JavaScript | npm `sererr` | [`packages/typescript`](packages/typescript) |

Each package ships:

- **Capture** — `capture(error, release, server_name) → [CapturedError]`
  walks the error chain, normalizes frame ordering (most-recent-call-first
  per frame; most-causal-first per chain entry), stamps `exception_id` /
  `parent_id` linkage, and applies a sensible `in_app` heuristic.
- **Source-bundle helpers** — idiomatic per language (rust-embed,
  `importlib.resources`, `//go:embed`, JAR resources, .NET embedded
  resources). Lets the producer populate `context_line` / `pre_context` /
  `post_context` at capture time.
- **Lazy source resolver** — consumer-side fetcher that resolves source
  from a git SHA on demand for captures that didn't embed.
- **Sentry JSON adapter** — converts `CapturedError` to a
  Sentry-compatible JSON payload for forwarding.

A cross-language conformance test corpus lives in
[`tests/conformance`](tests/conformance) — every language encodes a
shared fixture set; every language decodes; outputs are asserted
equivalent.

## Development policy

**Test-driven.** Every language package follows TDD: write the test
first, watch it fail, implement until it passes. No exceptions —
including the conformance fixtures, where the fixture and expected wire
bytes are committed alongside the runner that asserts equivalence.

**Mutation-tested.** Each language's CI runs the standard mutation
tester for that ecosystem; tests must kill mutants to be considered
meaningful.

| Language | Mutation tool |
|---|---|
| Rust | `cargo-mutants` |
| Python | `mutmut` |
| Go | `gremlins` |
| Java / Kotlin | `pitest` (Gradle plugin) |
| C# | `dotnet-stryker` |
| TypeScript | `@stryker-mutator/core` |

Per-package layout:

```
packages/<lang>/
├── src/            # implementation
├── tests/          # written first, never empty
└── <build config>
```

A package without tests for any committed feature is broken. A package
whose tests don't kill mutants is shallow — the fix is real tests, not
suppressed mutants.

## Toolchain containers

Each language ships a hermetic toolchain container in
[`containers/<lang>/Containerfile`](containers). The host `justfile`
runs all per-language tasks inside those containers via a
**just-in-just overlay** pattern (mounting `justfile.container` over
the in-container `/workspace/justfile`). You don't need rustc, uv, go,
gradle, or .NET on your host — only `just` + a container runtime
(docker or podman).

```bash
just build-images           # build all 7 toolchain images
just build rust             # build the Rust package inside its container
just test python            # test Python inside its container
just mutants go             # mutation tests for Go
just test-all               # every language, end to end
```

Override the container runtime with `CONTAINER_CMD=docker just …`.

## Versioning

All seven packages release **synchronized**: a single version number, a
single release commit. `sererr@1.2.0` means the same thing in every
language.

## Status

`sererr` is bootstrapping. The proto and package skeletons exist; the
capture implementations are landing per-language. Track release progress
in the milestones.

## Development

```bash
just                  # list tasks
just build rust       # build one language
just test rust
just test-all         # all six
just conformance      # cross-language equivalence
just proto-gen all    # regenerate bindings from proto/
```

Each language package is self-contained: open `packages/rust/` in
RustRover, `packages/java/` in IntelliJ, etc.

## License

Dual-licensed under MIT and BSD 3-Clause. See [LICENSE-MIT](LICENSE-MIT)
and [LICENSE-BSD-3-Clause](LICENSE-BSD-3-Clause).
