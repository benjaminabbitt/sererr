# sererr

Plain Rust types for [Sentry-compatible structured error
capture](https://sererr.fyi) — no proto dependency.

Use this crate when you want to gather diagnostics (frames, chain,
mechanism, source context) and process them in-process. To serialize
captures over the wire as `sererr.v1` proto, see the companion
[`sererr-proto`](https://crates.io/crates/sererr-proto) crate.

## Quick start

```rust
use sererr::capture;

let chain = capture(&err, "MyError", "v1.0.0", "host-a");
```

See [sererr.fyi/guides/rust](https://sererr.fyi/guides/rust/) for the
full guide.

## License

Dual MIT / BSD-3-Clause. See the repository root for the license texts.
