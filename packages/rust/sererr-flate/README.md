# sererr-flate

Compressed source-bundle support for [sererr](https://sererr.fyi).
Adapts a `rust_embed::RustEmbed`-derived asset bundle into a
`sererr::SourceProvider` so producers can populate `context_line` /
`pre_context` / `post_context` on captured frames from their own
gzip-compressed source tree.

**Requires nightly Rust** — `rust-embed`'s `include-flate` feature
depends on the unstable `proc_macro_span` API. Stable users can
implement `sererr::SourceProvider` themselves against `include_str!`
content.

## License

Dual MIT / BSD-3-Clause.
