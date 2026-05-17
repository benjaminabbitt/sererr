//! Integration test for `EmbedSourceProvider`.
//!
//! Embeds a small sample-source tree at build time via
//! `rust_embed::Embed`, wraps it in `EmbedSourceProvider`, and
//! verifies `populate_source_context` populates frame context fields
//! from the compressed bundle.
//!
//! Requires nightly Rust.

use rust_embed::RustEmbed;
use sererr::{populate_source_context, SourceProvider, StackFrame};
use sererr_flate::EmbedSourceProvider;

#[derive(RustEmbed)]
#[folder = "tests/sample-source"]
#[include = "*.rs"]
struct TestSource;

/// The provider returns content for embedded files.
///
/// Smoke test for the adapter layer — the rust-embed asset must come
/// out of the compressed bundle as readable UTF-8 source.
#[test]
fn provider_returns_embedded_content() {
    let provider = EmbedSourceProvider::<TestSource>::new();
    let content = provider
        .get_source("sample.rs")
        .expect("sample.rs was embedded");
    assert!(content.contains("pub fn one()"));
    assert!(content.contains("pub fn return_x(x: i32)"));
}

/// Missing files return None without panicking.
///
/// rust-embed's `get` returns `Option`; the adapter must propagate
/// that as None so populate_source_context's no-op path triggers.
#[test]
fn provider_returns_none_for_missing_files() {
    let provider = EmbedSourceProvider::<TestSource>::new();
    assert!(provider.get_source("does/not/exist.rs").is_none());
}

/// End-to-end: a StackFrame referencing an embedded file gets its
/// context fields populated.
///
/// This is the operator-facing contract: producers wire EmbedSource
/// into their capture pipeline, frames arrive at the consumer with
/// readable source context.
#[test]
fn populate_source_context_works_with_embed_provider() {
    let provider = EmbedSourceProvider::<TestSource>::new();
    let mut frame = StackFrame {
        file: "sample.rs".to_string(),
        line: 6, // "    let x = 1;"
        ..Default::default()
    };

    populate_source_context(&mut frame, &provider, 2);

    assert_eq!(frame.context_line, "    let x = 1;");
    assert!(!frame.pre_context.is_empty());
    assert!(!frame.post_context.is_empty());
}
