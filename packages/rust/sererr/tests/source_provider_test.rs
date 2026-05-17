//! Behavior tests for `SourceProvider` and `populate_source_context`.

use std::collections::BTreeMap;

use sererr::{populate_source_context, SourceProvider, StackFrame};

/// In-memory provider for tests — maps file paths to source content.
struct MapProvider(BTreeMap<&'static str, &'static str>);

impl SourceProvider for MapProvider {
    fn get_source(&self, file: &str) -> Option<String> {
        self.0.get(file).map(|s| s.to_string())
    }
}

const SAMPLE_SOURCE: &str = "\
fn one() {
    1
}

fn two() {
    let x = 2;
    return x;
}

fn three() {
    let y = three_inner();
    y
}
";

/// Populating a frame with a known line surrounds it with context.
///
/// `line` is 1-based; we expect `context_line` to be that line of
/// source, and `pre_context` / `post_context` to be the configured
/// number of surrounding lines.
#[test]
fn populates_context_around_line() {
    let mut provider = BTreeMap::new();
    provider.insert("src/sample.rs", SAMPLE_SOURCE);
    let provider = MapProvider(provider);

    let mut frame = StackFrame {
        file: "src/sample.rs".to_string(),
        line: 6, // "    let x = 2;"
        ..Default::default()
    };

    populate_source_context(&mut frame, &provider, 2);

    assert_eq!(frame.context_line, "    let x = 2;");
    assert_eq!(frame.pre_context, vec!["", "fn two() {"]);
    assert_eq!(frame.post_context, vec!["    return x;", "}"]);
}

/// Missing files leave the frame unchanged.
///
/// Producers may have partial source bundles; an unknown file is not
/// a failure mode — the frame retains its original context fields.
#[test]
fn missing_file_is_a_noop() {
    let provider = MapProvider(BTreeMap::new());
    let mut frame = StackFrame {
        file: "src/missing.rs".to_string(),
        line: 5,
        context_line: "preserved".to_string(),
        ..Default::default()
    };

    populate_source_context(&mut frame, &provider, 2);

    assert_eq!(frame.context_line, "preserved");
    assert!(frame.pre_context.is_empty());
    assert!(frame.post_context.is_empty());
}

/// Line 0 (unknown) is a no-op.
///
/// The proto encodes "unknown line number" as 0; we must not produce
/// out-of-bounds errors when the producer doesn't know the line.
#[test]
fn line_zero_is_a_noop() {
    let mut provider = BTreeMap::new();
    provider.insert("src/sample.rs", SAMPLE_SOURCE);
    let provider = MapProvider(provider);

    let mut frame = StackFrame {
        file: "src/sample.rs".to_string(),
        line: 0,
        ..Default::default()
    };

    populate_source_context(&mut frame, &provider, 2);

    assert!(frame.context_line.is_empty());
    assert!(frame.pre_context.is_empty());
    assert!(frame.post_context.is_empty());
}

/// Line beyond end-of-file is a no-op.
///
/// If the producer reports a line past the bundled source's length
/// (e.g. source mismatched the release), we should not panic or
/// produce garbage; leave the frame untouched.
#[test]
fn line_past_eof_is_a_noop() {
    let mut provider = BTreeMap::new();
    provider.insert("src/sample.rs", SAMPLE_SOURCE);
    let provider = MapProvider(provider);

    let mut frame = StackFrame {
        file: "src/sample.rs".to_string(),
        line: 999_999,
        ..Default::default()
    };

    populate_source_context(&mut frame, &provider, 2);

    assert!(frame.context_line.is_empty());
}

/// Pre-context near top of file is clamped, not over-fetched.
///
/// `pre_context.len()` may be fewer than `surrounding` when we're
/// near the start of the file. Same for post-context near EOF.
#[test]
fn context_clamps_at_file_boundaries() {
    let mut provider = BTreeMap::new();
    provider.insert("src/sample.rs", SAMPLE_SOURCE);
    let provider = MapProvider(provider);

    let mut frame = StackFrame {
        file: "src/sample.rs".to_string(),
        line: 1, // "fn one() {"
        ..Default::default()
    };

    populate_source_context(&mut frame, &provider, 5);

    assert_eq!(frame.context_line, "fn one() {");
    assert!(frame.pre_context.is_empty()); // no lines before line 1
    assert!(frame.post_context.len() <= 5);
}
