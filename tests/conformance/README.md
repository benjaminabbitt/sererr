# sererr conformance corpus — Cucumber-driven

Cross-language conformance is **Gherkin-described** so the same
behavioral contract drives every language's runner. Each scenario in
`features/*.feature` says what to encode / decode / assert; per-
language step definitions implement the framework-specific I/O.

## Why Cucumber

Sererr lives or dies on cross-language byte-equivalence. A
hand-written runner per language would drift — the canonical contract
should be the executable specification, not seven copies of similar
test logic.

Cucumber gives us:
- One specification (`features/*.feature`), seven runners.
- Failures point at the offending scenario in plain English.
- Adding a language is mechanical: implement the step library, the
  features run unchanged.

## Layout

```
tests/conformance/
├── features/              # Gherkin scenarios, language-agnostic
│   ├── encoding.feature
│   ├── chain.feature
│   ├── source-context.feature
│   ├── mechanism.feature
│   └── debuginfo-adapter.feature
├── fixtures/              # Input descriptors (JSON) + expected wire bytes
│   ├── 0001-simple.json
│   ├── 0001-simple.pb
│   └── ...
                            # Orchestrator: `just conformance` (from repo root)
└── runners/
    ├── rust/              # `cucumber` crate; step defs in Rust
    ├── python/            # `behave`
    ├── go/                # `godog`
    ├── java/              # `cucumber-jvm`
    ├── kotlin/            # `cucumber-jvm` + Kotlin steps
    ├── csharp/            # `Reqnroll` (SpecFlow successor)
    └── typescript/        # `@cucumber/cucumber`
```

## Per-language Cucumber library

| Language | Library |
|---|---|
| Rust | [`cucumber`](https://crates.io/crates/cucumber) |
| Python | [`behave`](https://pypi.org/project/behave/) |
| Go | [`godog`](https://github.com/cucumber/godog) |
| Java | [`cucumber-jvm`](https://github.com/cucumber/cucumber-jvm) |
| Kotlin | `cucumber-jvm` (Kotlin step defs) |
| C# | [`Reqnroll`](https://reqnroll.net/) (SpecFlow's successor; SpecFlow is unmaintained) |
| TypeScript | [`@cucumber/cucumber`](https://www.npmjs.com/package/@cucumber/cucumber) |

## Example scenarios (sketch)

```gherkin
Feature: Encoding round-trip
  As a sererr consumer
  I want every language to serialize identical inputs to identical bytes
  So that producers and consumers in different languages interoperate

  Scenario: Single captured error encodes to canonical bytes
    Given a fixture "0001-simple"
    When I construct the CapturedError per the fixture
    And I serialize it via the proto adapter
    Then the encoded bytes match "fixtures/0001-simple.pb"

  Scenario: Empty chain produces empty repeated field
    Given a fixture "0002-empty-chain"
    When I construct an empty Vec / List of CapturedError
    Then the encoded bytes are empty
```

```gherkin
Feature: Chain semantics
  Sererr chains are flat, most-causal-first, mechanism-linked.

  Scenario: Three-deep chain has correct mechanism IDs
    Given a chain with three nested causes "inner" → "middle" → "outer"
    When I capture it
    Then the chain length is 3
    And entry 0 has exception_id 0 and parent_id 0
    And entry 1 has exception_id 1 and parent_id 0
    And entry 2 has exception_id 2 and parent_id 1
    And the last entry is the originating caught error
```

## Fixture format

Each fixture pairs a JSON descriptor (the input value) with `.pb` wire
bytes (the canonical encoding). Test scenarios reference fixtures by
name; runners load both and assert encode/decode round-trip
equivalence.

```json
// fixtures/0001-simple.json
{
  "captured_error": {
    "type": "MyError",
    "message": "oops",
    "frames": [
      {"function": "f", "file": "src/x.rs", "line": 12, "in_app": true}
    ],
    "mechanism": {"type": "generic", "handled": true},
    "release": "v1.0.0",
    "server_name": "host-1"
  }
}
```

The `.pb` file is the byte-canonical encoding of that descriptor
through the sererr-proto schema. Generating it: pick one language as
the reference (Rust), serialize, commit the output.

## Status

Bootstrap. README defines the contract; per-language runners and
features land per the language-implementation milestones.

## What to test (initial set)

- **encoding** — every fixture encodes byte-equivalent across languages
- **chain** — flat order, mechanism IDs, originating-error-is-last
- **source-context** — context_line / pre_context / post_context
  populated correctly when source is bundled; empty when not
- **mechanism** — handled/synthetic/type defaults, data map round-trip,
  exception_id / parent_id stamping
- **debuginfo-adapter** — `to_debug_info()` produces equivalent
  `stack_entries` and `detail` across languages
- **plain-vs-proto** — plain types and proto types round-trip without
  field drift (the "seam" contract: layers stay in sync)
