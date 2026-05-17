---
title: Rust
description: Capture errors with the sererr Rust crate.
---

The `sererr` crate ships the proto-generated types (`CapturedError`,
`StackFrame`, `ExceptionMechanism`) and a `capture` function that does
the chain walk, frame normalization, and mechanism-ID stamping.

```rust
use sererr::capture;

let chain = capture(&err, type_name, release, server_name);
```

## Idiosyncracies

- `std::backtrace::Backtrace::frames()` is unstable. The crate uses the
  `backtrace` crate for structured frames; fall back to parsing
  `Backtrace::to_string()` if you don't want the extra dep.
- Rust cannot recover the runtime type of a `&dyn Error` — producers
  pass the leaf type name at the catch site.
- **Frame order from `Backtrace`'s default `Display` is outermost-
  first** — the opposite of our convention. The crate reverses on
  encode; if you build frames yourself, do the same.
- For `anyhow` / `eyre`, swap the `Error::source()` walk for
  `err.chain()` which already yields innermost-last.

## Source bundling

Enable the `source-embed` feature, then derive `rust_embed::Embed`:

```rust
#[derive(rust_embed::Embed)]
#[folder = "src/"]
#[include = "*.rs"]
struct Source;
```

The capture function looks up each frame's `file` against this embed
and populates `context_line` / `pre_context` / `post_context` if found.
~500 KB per binary at ~80% zstd compression for ~3 MB of `src/`.

## Manual recipe

If you can't take the `sererr` crate, hand-roll capture:

```rust
use std::backtrace::Backtrace;
use std::error::Error;
use sererr::{CapturedError, ExceptionMechanism, StackFrame};

/// Walk an error chain into a most-causal-first Vec<CapturedError>.
/// `type_name` is producer-supplied since std::any cannot recover the
/// runtime type of a `&dyn Error`.
pub fn capture(
    err: &(dyn Error + 'static),
    type_name: &str,
    release: &str,
    server_name: &str,
) -> Vec<CapturedError> {
    let bt = Backtrace::force_capture();
    let frames = parse_backtrace(&bt); // your helper

    let mut chain: Vec<CapturedError> = Vec::new();
    let mut current: Option<&(dyn Error + 'static)> = Some(err);
    let mut first_type = type_name.to_string();
    while let Some(e) = current {
        chain.push(CapturedError {
            r#type: std::mem::take(&mut first_type),
            message: format!("{e}"),
            frames: frames.clone(),
            mechanism: Some(ExceptionMechanism {
                r#type: "generic".to_string(),
                handled: true,
                ..Default::default()
            }),
            release: release.to_string(),
            server_name: server_name.to_string(),
        });
        current = e.source();
    }
    chain.reverse();

    for (i, entry) in chain.iter_mut().enumerate() {
        if let Some(m) = entry.mechanism.as_mut() {
            m.exception_id = i as u32;
            m.parent_id = if i == 0 { 0 } else { (i - 1) as u32 };
        }
    }
    chain
}
```
