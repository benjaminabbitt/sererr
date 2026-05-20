//! Behavior tests for the `sererr::capture` function and the
//! `to_debug_info` adapter. Written test-first per the project's TDD
//! policy.

use std::error::Error;
use std::fmt;

use sererr::{capture, to_debug_info, CapturedError, StackFrame};

// ---------- test fixtures ----------

#[derive(Debug)]
struct LeafError(String);

impl fmt::Display for LeafError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}
impl Error for LeafError {}

#[derive(Debug)]
struct WrapError {
    msg: String,
    source: Box<dyn Error + Send + Sync + 'static>,
}

impl fmt::Display for WrapError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.msg)
    }
}
impl Error for WrapError {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        Some(&*self.source)
    }
}

// ---------- tests ----------

/// A single error with no source produces a one-entry chain.
///
/// Verifies the fundamental contract: capture returns a Vec
/// containing one CapturedError per chain link, here the leaf.
#[test]
fn single_error_produces_one_entry() {
    let err = LeafError("leaf".to_string());
    let chain = capture(&err, "LeafError", "test-release", "test-host");

    assert_eq!(chain.len(), 1);
    assert_eq!(chain[0].r#type, "LeafError");
    assert_eq!(chain[0].message, "leaf");
    assert_eq!(chain[0].release, "test-release");
    assert_eq!(chain[0].server_name, "test-host");
}

/// A two-level error chain produces a two-entry capture in
/// most-causal-first order.
///
/// The outer error is the caught one; the inner is its `.source()`.
/// Sererr's convention is most-causal-first, so the inner (leaf cause)
/// appears at index 0 and the outer (originating caught) appears at
/// index 1 (the LAST element).
#[test]
fn chain_walks_source_most_causal_first() {
    let inner = LeafError("inner".to_string());
    let outer = WrapError {
        msg: "outer".to_string(),
        source: Box::new(inner),
    };

    let chain = capture(&outer, "WrapError", "rel", "host");

    assert_eq!(chain.len(), 2);
    // Most-causal-first: inner is first, outer (originating) is last.
    assert_eq!(chain[0].message, "inner");
    assert_eq!(chain[1].message, "outer");
    // Originating caught error is the LAST element per sererr's convention.
    assert_eq!(chain.last().unwrap().r#type, "WrapError");
}

/// Mechanism IDs are stamped sequentially with the parent linkage
/// pointing to the previous entry.
///
/// Sentry's chain-flatten convention: `exception_id` is the entry's
/// position in the array; `parent_id` is the previous entry's id.
/// The root cause has `parent_id == 0` (itself).
#[test]
fn mechanism_ids_stamped_correctly() {
    let inner = LeafError("inner".to_string());
    let middle = WrapError {
        msg: "middle".to_string(),
        source: Box::new(inner),
    };
    let outer = WrapError {
        msg: "outer".to_string(),
        source: Box::new(middle),
    };

    let chain = capture(&outer, "WrapError", "rel", "host");
    assert_eq!(chain.len(), 3);

    let mech0 = chain[0].mechanism.as_ref().expect("mechanism set");
    assert_eq!(mech0.exception_id, 0);
    assert_eq!(mech0.parent_id, 0);

    let mech1 = chain[1].mechanism.as_ref().expect("mechanism set");
    assert_eq!(mech1.exception_id, 1);
    assert_eq!(mech1.parent_id, 0);

    let mech2 = chain[2].mechanism.as_ref().expect("mechanism set");
    assert_eq!(mech2.exception_id, 2);
    assert_eq!(mech2.parent_id, 1);
}

/// Every captured entry gets a mechanism with handled=true and
/// type="generic" by default.
///
/// DLQ-routed errors are always caught (the framework caught them);
/// "generic" is the catch-all category from Sentry's vocabulary.
#[test]
fn default_mechanism_is_generic_handled() {
    let err = LeafError("leaf".to_string());
    let chain = capture(&err, "LeafError", "rel", "host");

    let mech = chain[0].mechanism.as_ref().expect("mechanism set");
    assert_eq!(mech.r#type, "generic");
    assert!(mech.handled);
    assert!(!mech.synthetic);
}

/// Frames are populated and ordered most-recent-call-first.
///
/// The capture helper grabs a `Backtrace` at the call site and parses
/// it. Every CapturedError in the chain receives the same backtrace
/// (frames at the originating capture point) — chain-position is
/// distinguished by `mechanism.exception_id`.
#[test]
fn frames_are_populated_most_recent_first() {
    let err = LeafError("leaf".to_string());
    let chain = capture(&err, "LeafError", "rel", "host");

    // At minimum, the test function's frame should be present.
    // (Backtrace may be empty when RUST_BACKTRACE / RUST_LIB_BACKTRACE
    // are unset; the capture helper uses force_capture so this is safe.)
    let frames = &chain[0].frames;
    assert!(
        !frames.is_empty(),
        "capture should populate at least one frame; got {} frames",
        frames.len()
    );
}

/// Empty release / server fields are accepted and stored verbatim.
///
/// Producers may legitimately have no release identifier (e.g. local
/// dev). Empty strings are valid per the proto contract.
#[test]
fn empty_release_and_server_are_stored() {
    let err = LeafError("leaf".to_string());
    let chain = capture(&err, "LeafError", "", "");

    assert_eq!(chain[0].release, "");
    assert_eq!(chain[0].server_name, "");
}

// ---------- to_debug_info adapter ----------

/// Adapter flattens the chain to a single google.rpc.DebugInfo with
/// rendered stack lines and a "type: message" detail.
///
/// The point of the adapter is gRPC error-model interop. Tooling that
/// only knows DebugInfo can read the rendered text without
/// understanding sererr's structure.
#[test]
fn to_debug_info_flattens_chain() {
    let inner = LeafError("inner".to_string());
    let outer = WrapError {
        msg: "outer".to_string(),
        source: Box::new(inner),
    };

    let chain = capture(&outer, "WrapError", "rel", "host");
    let di = to_debug_info(&chain);

    // detail joins messages with "Caused by:" separators.
    assert!(di.detail.contains("WrapError"));
    assert!(di.detail.contains("outer"));
    assert!(di.detail.contains("Caused by"));
    assert!(di.detail.contains("inner"));

    // stack_entries: one line per frame across the chain. At minimum,
    // we have the originating frames (this test's stack).
    assert!(!di.stack_entries.is_empty());
}

/// Empty chain produces an empty DebugInfo (not a panic).
///
/// A defensive contract: callers may pass an empty Vec (e.g. capture
/// returned nothing) and expect a benign empty DebugInfo back.
#[test]
fn to_debug_info_handles_empty_chain() {
    let chain: Vec<CapturedError> = Vec::new();
    let di = to_debug_info(&chain);
    assert!(di.stack_entries.is_empty());
    assert!(di.detail.is_empty());
}

// ---------- field-population pins on capture_frames (kill mutation
//             "delete field X from struct StackFrame expression") ------

/// Captured frames carry a populated `function` name.
#[test]
fn captured_frames_have_function() {
    let chain = capture(&LeafError("x".into()), "LeafError", "r", "h");
    assert!(
        chain[0].frames.iter().any(|f| !f.function.is_empty()),
        "expected at least one frame with a function name"
    );
}

/// Captured frames carry a populated `file` path (at least one frame —
/// stdlib frames may not have source info but the user's frame does).
#[test]
fn captured_frames_have_file() {
    let chain = capture(&LeafError("x".into()), "LeafError", "r", "h");
    assert!(
        chain[0].frames.iter().any(|f| !f.file.is_empty()),
        "expected at least one frame with a file path"
    );
}

/// Captured frames carry a populated `line` (non-zero) somewhere.
#[test]
fn captured_frames_have_line() {
    let chain = capture(&LeafError("x".into()), "LeafError", "r", "h");
    assert!(
        chain[0].frames.iter().any(|f| f.line > 0),
        "expected at least one frame with line > 0"
    );
}

/// Captured frames mark this test's own frame as `in_app = true`.
///
/// Pins: capture_frames must compute and write `in_app`, AND
/// `is_app_frame` must return true for non-std prefixes.
#[test]
fn captured_frames_mark_user_code_in_app() {
    let chain = capture(&LeafError("x".into()), "LeafError", "r", "h");
    let any_in_app = chain[0].frames.iter().any(|f| f.in_app);
    assert!(
        any_in_app,
        "expected at least one frame to be in_app=true; got {:?}",
        chain[0]
            .frames
            .iter()
            .map(|f| (&f.function, f.in_app))
            .collect::<Vec<_>>()
    );
}

// ---------- is_app_frame heuristic (pins return value + ! negation) ---

/// Application code (any function name not on the stdlib/runtime
/// prefix list) is classified as in_app.
#[test]
fn is_app_frame_classifies_user_code_as_in_app() {
    assert!(sererr::is_app_frame("my_crate::handler"));
    assert!(sererr::is_app_frame("orders::saga::process"));
}

/// Stdlib frames are classified as !in_app.
#[test]
fn is_app_frame_classifies_stdlib_as_not_in_app() {
    assert!(!sererr::is_app_frame("std::panicking::panic"));
    assert!(!sererr::is_app_frame("core::ptr::drop_in_place"));
    assert!(!sererr::is_app_frame("alloc::vec::Vec::new"));
}

/// Tokio / backtrace / sererr's own frames are framework, !in_app.
#[test]
fn is_app_frame_classifies_runtime_libs_as_not_in_app() {
    assert!(!sererr::is_app_frame("tokio::runtime::context::enter"));
    assert!(!sererr::is_app_frame("backtrace::capture::Backtrace::new"));
    assert!(!sererr::is_app_frame("sererr::capture"));
}

// ---------- to_debug_info prefix branches (kill idx == 0 mutations) ---

/// The outermost (last-in-chain) entry's header has NO "Caused by:"
/// prefix; subsequent entries DO. Kills the idx == 0 vs != 0 branch.
///
/// Asserted on BOTH `detail` and `stack_entries` because they have
/// independent `idx == 0` branches (lines 309 and 315 of lib.rs).
#[test]
fn to_debug_info_outermost_has_no_caused_by_prefix() {
    let inner = LeafError("the_inner_msg".into());
    let outer = WrapError {
        msg: "the_outer_msg".into(),
        source: Box::new(inner),
    };
    let chain = capture(&outer, "OuterT", "r", "h");
    let di = to_debug_info(&chain);

    // Detail first line is the outermost (no "Caused by:").
    let first_detail = di.detail.lines().next().unwrap();
    assert!(first_detail.starts_with("OuterT:"));
    assert!(!first_detail.contains("Caused by"));

    // Subsequent line is the inner (has "Caused by:").
    let second_detail = di.detail.lines().nth(1).unwrap();
    assert!(second_detail.starts_with("Caused by:"));

    // stack_entries: outermost header is first, no "Caused by:".
    assert!(
        di.stack_entries[0].starts_with("OuterT:"),
        "stack_entries[0] should be outermost header; got {:?}",
        di.stack_entries[0]
    );
    assert!(
        !di.stack_entries[0].contains("Caused by"),
        "outermost stack_entries header should NOT have 'Caused by:'; got {:?}",
        di.stack_entries[0]
    );
    // Somewhere later in stack_entries we should see the inner "Caused by:".
    assert!(
        di.stack_entries.iter().any(|s| s.starts_with("Caused by:")),
        "expected a 'Caused by:' line in stack_entries; got {:?}",
        di.stack_entries
    );
}

/// A single-entry chain has NO "Caused by:" anywhere in detail.
#[test]
fn to_debug_info_single_entry_has_no_caused_by() {
    let chain = capture(&LeafError("only".into()), "Solo", "r", "h");
    let di = to_debug_info(&chain);
    assert!(!di.detail.contains("Caused by"));
}

// ---------- format_frame_line via to_debug_info output ---------------
// (format_frame_line is private; we drive it through to_debug_info)

/// A frame with file + line > 0 renders as `"  at <fn> (file:line)"`.
/// Kills the `frame.line > 0` mutation (== / </ >=).
#[test]
fn frame_with_file_and_line_renders_with_parens_and_colon() {
    let captured = CapturedError {
        r#type: "T".into(),
        message: "m".into(),
        frames: vec![StackFrame {
            function: "fn_a".into(),
            file: "src/x.rs".into(),
            line: 42,
            ..Default::default()
        }],
        ..Default::default()
    };
    let di = to_debug_info(&[captured]);
    assert!(
        di.stack_entries
            .contains(&"  at fn_a (src/x.rs:42)".to_string()),
        "expected '  at fn_a (src/x.rs:42)' in stack_entries; got {:?}",
        di.stack_entries
    );
}

/// A frame with file but line == 0 renders as `"  at <fn> (file)"` —
/// no colon, no zero. Kills `>` → `==`/`>=` (which would format `0`).
#[test]
fn frame_with_file_but_no_line_renders_without_colon() {
    let captured = CapturedError {
        r#type: "T".into(),
        message: "m".into(),
        frames: vec![StackFrame {
            function: "fn_b".into(),
            file: "src/y.rs".into(),
            line: 0,
            ..Default::default()
        }],
        ..Default::default()
    };
    let di = to_debug_info(&[captured]);
    assert!(
        di.stack_entries
            .contains(&"  at fn_b (src/y.rs)".to_string()),
        "expected '  at fn_b (src/y.rs)' (no line); got {:?}",
        di.stack_entries
    );
}

/// A frame with NO file renders as `"  at <fn>"` — no parens at all.
/// Kills the `frame.file.is_empty()` branch returning a parenthesized
/// empty location.
#[test]
fn frame_with_no_file_renders_bare() {
    let captured = CapturedError {
        r#type: "T".into(),
        message: "m".into(),
        frames: vec![StackFrame {
            function: "fn_c".into(),
            file: "".into(),
            line: 0,
            ..Default::default()
        }],
        ..Default::default()
    };
    let di = to_debug_info(&[captured]);
    assert!(
        di.stack_entries.contains(&"  at fn_c".to_string()),
        "expected '  at fn_c' (no parens); got {:?}",
        di.stack_entries
    );
}

/// A frame with empty function renders as `"  at <unknown>"`.
#[test]
fn frame_with_no_function_renders_unknown() {
    let captured = CapturedError {
        r#type: "T".into(),
        message: "m".into(),
        frames: vec![StackFrame {
            function: "".into(),
            file: "src/z.rs".into(),
            line: 5,
            ..Default::default()
        }],
        ..Default::default()
    };
    let di = to_debug_info(&[captured]);
    assert!(
        di.stack_entries
            .contains(&"  at <unknown> (src/z.rs:5)".to_string()),
        "expected unknown placeholder; got {:?}",
        di.stack_entries
    );
}
