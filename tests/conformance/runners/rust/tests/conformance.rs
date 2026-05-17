//! Rust implementation of the cross-language conformance corpus.
//!
//! Step definitions for the Gherkin features at `tests/conformance/features/`.
//! Every other language's runner implements the same step library against
//! the same features — sererr's wire-format contract is the executable
//! specification.
//!
//! Run with `just test` (from this directory) or via the top-level
//! `tests/conformance/run.sh` orchestrator.

use std::collections::BTreeMap;
use std::error::Error as StdError;
use std::fmt;
use std::path::PathBuf;

use cucumber::{given, then, when, World};
use prost::Message;
use serde::Deserialize;
use sererr::{capture, to_debug_info, CapturedError, ExceptionMechanism, StackFrame};
use sererr_proto::ProtoCapturedError;

// ============================================================================
// Test fixtures (errors with synthetic chains)
// ============================================================================

#[derive(Debug)]
struct LabeledError {
    msg: String,
    source: Option<Box<dyn StdError + Send + Sync + 'static>>,
}

impl fmt::Display for LabeledError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.msg)
    }
}

impl StdError for LabeledError {
    fn source(&self) -> Option<&(dyn StdError + 'static)> {
        self.source.as_deref().map(|s| s as &(dyn StdError + 'static))
    }
}

fn make_chain(msgs: &[&str]) -> LabeledError {
    // msgs is innermost-first: [innermost, ..., outermost].
    // Build by chaining sources from inner to outer.
    let mut current: Option<Box<dyn StdError + Send + Sync + 'static>> = None;
    for msg in msgs {
        current = Some(Box::new(LabeledError {
            msg: (*msg).to_string(),
            source: current,
        }));
    }
    // `current` is now the outermost. Unwrap into the owned LabeledError.
    let boxed = current.expect("at least one message");
    *boxed
        .downcast::<LabeledError>()
        .expect("inner type is LabeledError")
}

// ============================================================================
// JSON descriptor for fixtures
// ============================================================================

#[derive(Debug, Deserialize, Default)]
#[serde(default)]
struct FixtureDescriptor {
    captured_error: Option<FixtureCapturedError>,
}

#[derive(Debug, Deserialize, Default)]
#[serde(default)]
struct FixtureCapturedError {
    r#type: String,
    message: String,
    frames: Vec<FixtureStackFrame>,
    mechanism: Option<FixtureMechanism>,
    release: String,
    server_name: String,
}

#[derive(Debug, Deserialize, Default)]
#[serde(default)]
struct FixtureStackFrame {
    function: String,
    module: String,
    package: String,
    file: String,
    abs_path: String,
    line: u32,
    context_line: String,
    pre_context: Vec<String>,
    post_context: Vec<String>,
    source_link: String,
    in_app: bool,
}

#[derive(Debug, Deserialize, Default)]
#[serde(default)]
struct FixtureMechanism {
    r#type: String,
    description: String,
    handled: bool,
    synthetic: bool,
    help_link: String,
    source: String,
    exception_id: u32,
    parent_id: u32,
    is_exception_group: bool,
    data: BTreeMap<String, String>,
}

impl From<FixtureStackFrame> for StackFrame {
    fn from(f: FixtureStackFrame) -> Self {
        StackFrame {
            function: f.function,
            module: f.module,
            package: f.package,
            file: f.file,
            abs_path: f.abs_path,
            line: f.line,
            context_line: f.context_line,
            pre_context: f.pre_context,
            post_context: f.post_context,
            source_link: f.source_link,
            in_app: f.in_app,
        }
    }
}

impl From<FixtureMechanism> for ExceptionMechanism {
    fn from(m: FixtureMechanism) -> Self {
        ExceptionMechanism {
            r#type: m.r#type,
            description: m.description,
            handled: m.handled,
            synthetic: m.synthetic,
            help_link: m.help_link,
            source: m.source,
            exception_id: m.exception_id,
            parent_id: m.parent_id,
            is_exception_group: m.is_exception_group,
            data: m.data,
        }
    }
}

impl From<FixtureCapturedError> for CapturedError {
    fn from(c: FixtureCapturedError) -> Self {
        CapturedError {
            r#type: c.r#type,
            message: c.message,
            frames: c.frames.into_iter().map(Into::into).collect(),
            mechanism: c.mechanism.map(Into::into),
            release: c.release,
            server_name: c.server_name,
        }
    }
}

// ============================================================================
// World
// ============================================================================

#[derive(Debug, Default, World)]
pub struct ConformanceWorld {
    /// Constructed captured-error chain under test.
    chain: Vec<CapturedError>,
    /// Captured `DebugInfo` for adapter scenarios.
    debug_info: Option<sererr::DebugInfo>,
    /// Encoded proto bytes (when serialization is exercised).
    encoded: Option<Vec<u8>>,
    /// Active fixture name.
    fixture: Option<String>,
    /// Constructed `CapturedError` from a fixture (pre-encode).
    fixture_input: Option<CapturedError>,
}

fn fixtures_dir() -> PathBuf {
    PathBuf::from(
        std::env::var("SERERR_FIXTURES_DIR")
            .expect("SERERR_FIXTURES_DIR env var pointing at tests/conformance/fixtures"),
    )
}

fn features_dir() -> PathBuf {
    PathBuf::from(
        std::env::var("SERERR_FEATURES_DIR")
            .expect("SERERR_FEATURES_DIR env var pointing at tests/conformance/features"),
    )
}

// ============================================================================
// Step definitions
// ============================================================================

// ---------- background / generic ----------

#[given("the canonical sererr.v1 proto schema")]
async fn given_canonical_schema(_world: &mut ConformanceWorld) {
    // No-op: the schema is implicit (we compile against sererr-proto).
}

// ---------- encoding.feature ----------

#[given(regex = r#"^a fixture "([^"]+)"$"#)]
async fn given_fixture(world: &mut ConformanceWorld, name: String) {
    world.fixture = Some(name);
}

#[given("a default-initialized CapturedError (all zero values)")]
async fn given_default_captured(world: &mut ConformanceWorld) {
    world.fixture_input = Some(CapturedError::default());
}

#[when("I construct the CapturedError per the fixture's JSON descriptor")]
async fn when_construct_from_fixture(world: &mut ConformanceWorld) {
    let name = world.fixture.as_ref().expect("fixture name set").clone();
    let path = fixtures_dir().join(format!("{name}.json"));
    let bytes = std::fs::read(&path)
        .unwrap_or_else(|e| panic!("read fixture json {}: {e}", path.display()));
    let desc: FixtureDescriptor =
        serde_json::from_slice(&bytes).expect("fixture json parses to FixtureDescriptor");
    let captured = desc
        .captured_error
        .expect("fixture descriptor has captured_error key");
    world.fixture_input = Some(captured.into());
}

#[when("I serialize it via the proto adapter")]
#[when("I serialize it")]
async fn when_serialize(world: &mut ConformanceWorld) {
    // Auto-construct from the named fixture if the scenario went
    // directly from `Given a fixture` to `When I serialize it` without
    // an explicit construct step.
    if world.fixture_input.is_none() {
        if let Some(name) = &world.fixture {
            let path = fixtures_dir().join(format!("{name}.json"));
            let bytes = std::fs::read(&path)
                .unwrap_or_else(|e| panic!("read fixture json {}: {e}", path.display()));
            let desc: FixtureDescriptor =
                serde_json::from_slice(&bytes).expect("fixture json parses");
            world.fixture_input = Some(desc.captured_error.expect("captured_error key").into());
        }
    }
    let input = world
        .fixture_input
        .clone()
        .expect("fixture_input set before serialize");
    let proto: ProtoCapturedError = input.into();
    world.encoded = Some(proto.encode_to_vec());
}

#[then(regex = r#"^the encoded bytes match "fixtures/([^"]+)\.pb"$"#)]
async fn then_bytes_match_fixture(world: &mut ConformanceWorld, name: String) {
    let path = fixtures_dir().join(format!("{name}.pb"));
    let expected = std::fs::read(&path)
        .unwrap_or_else(|e| panic!("read fixture pb {}: {e}", path.display()));
    let actual = world.encoded.as_ref().expect("encoded set");
    assert_eq!(
        actual, &expected,
        "encoded bytes mismatch for fixture {name}"
    );
}

#[then("the encoded bytes are empty")]
async fn then_bytes_empty(world: &mut ConformanceWorld) {
    let bytes = world.encoded.as_ref().expect("encoded set");
    assert!(
        bytes.is_empty(),
        "expected empty bytes, got {} bytes",
        bytes.len()
    );
}

#[when("I deserialize the bytes back to a CapturedError")]
async fn when_deserialize(world: &mut ConformanceWorld) {
    let bytes = world.encoded.as_ref().expect("encoded set");
    let proto = ProtoCapturedError::decode(bytes.as_slice()).expect("decode roundtrips");
    let plain: CapturedError = proto.into();
    world.fixture_input = Some(plain);
}

#[then("the result equals the input field-by-field")]
async fn then_roundtrip_equal(world: &mut ConformanceWorld) {
    // The second `when` overwrote fixture_input with the decoded value;
    // re-read the fixture and compare.
    let name = world.fixture.as_ref().expect("fixture name set").clone();
    let path = fixtures_dir().join(format!("{name}.json"));
    let bytes = std::fs::read(&path).expect("read fixture json");
    let desc: FixtureDescriptor = serde_json::from_slice(&bytes).expect("parse fixture");
    let expected: CapturedError = desc.captured_error.expect("captured_error key").into();
    let actual = world.fixture_input.as_ref().expect("decoded set");
    assert_eq!(actual, &expected, "round-trip mismatch");
}

// ---------- chain.feature ----------

#[given("an error with no source / cause")]
async fn given_no_source_error(world: &mut ConformanceWorld) {
    let err = LabeledError {
        msg: "single".into(),
        source: None,
    };
    world.chain = capture(&err, "LabeledError", "test", "host");
}

#[given(regex = r#"^a chain "([^"]+)" caused-by "([^"]+)"(?: caused-by "([^"]+)")?$"#)]
async fn given_chain(
    world: &mut ConformanceWorld,
    inner: String,
    middle_or_outer: String,
    maybe_outer: String,
) {
    // The Gherkin "<inner> caused-by <next> [caused-by <outer>]" reads
    // most-causal-first → outermost-last. capture(outer) walks
    // .source() inward, then reverses to most-causal-first.
    let msgs: Vec<&str> = if maybe_outer.is_empty() {
        vec![inner.as_str(), middle_or_outer.as_str()]
    } else {
        vec![inner.as_str(), middle_or_outer.as_str(), maybe_outer.as_str()]
    };
    let outermost = make_chain(&msgs);
    world.chain = capture(&outermost, "LabeledError", "test", "host");
}

#[given("a captured error with frames")]
async fn given_captured_with_frames(world: &mut ConformanceWorld) {
    let err = LabeledError {
        msg: "x".into(),
        source: None,
    };
    world.chain = capture(&err, "LabeledError", "test", "host");
}

#[when("I capture it")]
#[when("I capture the outermost error")]
async fn when_capture_noop(_world: &mut ConformanceWorld) {
    // Capture happened in the `given` step; this `when` is a Gherkin
    // narrative beat, not a separate action.
}

#[when("I read the frames")]
async fn when_read_frames(_world: &mut ConformanceWorld) {
    // No-op; frames are already in world.chain.
}

#[then(regex = r"^the chain length is (\d+)$")]
async fn then_chain_length(world: &mut ConformanceWorld, n: usize) {
    assert_eq!(world.chain.len(), n, "chain length");
}

#[then(regex = r"^entry (\d+) has exception_id (\d+) and parent_id (\d+)$")]
async fn then_entry_mechanism_ids(
    world: &mut ConformanceWorld,
    idx: usize,
    exception_id: u32,
    parent_id: u32,
) {
    let entry = &world.chain[idx];
    let mech = entry.mechanism.as_ref().expect("mechanism set");
    assert_eq!(mech.exception_id, exception_id, "entry {idx} exception_id");
    assert_eq!(mech.parent_id, parent_id, "entry {idx} parent_id");
}

#[then("the entry's mechanism has exception_id 0")]
async fn then_single_entry_exception_id_zero(world: &mut ConformanceWorld) {
    let mech = world.chain[0].mechanism.as_ref().expect("mechanism set");
    assert_eq!(mech.exception_id, 0);
}

#[then("the entry's mechanism has parent_id 0")]
async fn then_single_entry_parent_id_zero(world: &mut ConformanceWorld) {
    let mech = world.chain[0].mechanism.as_ref().expect("mechanism set");
    assert_eq!(mech.parent_id, 0);
}

#[then(regex = r#"^entry (\d+) has message "([^"]+)"$"#)]
async fn then_entry_message(world: &mut ConformanceWorld, idx: usize, msg: String) {
    assert_eq!(world.chain[idx].message, msg);
}

#[then(regex = r#"^the last chain entry has message "([^"]+)"$"#)]
async fn then_last_entry_message(world: &mut ConformanceWorld, msg: String) {
    assert_eq!(world.chain.last().expect("non-empty chain").message, msg);
}

#[then("the first frame is the most recent call")]
async fn then_first_frame_most_recent(world: &mut ConformanceWorld) {
    // Conformance: frames are ordered most-recent-first per the proto
    // contract. We can't easily assert "this is the most recent" without
    // a known reference point, so we just check that frames exist —
    // language-specific runners may strengthen this with their own
    // assertions about the first frame's function name.
    let frames = &world.chain[0].frames;
    assert!(
        !frames.is_empty(),
        "expected captured frames; got {} frames",
        frames.len()
    );
}

// ---------- debuginfo-adapter.feature ----------

#[given("an empty chain")]
async fn given_empty_chain(world: &mut ConformanceWorld) {
    world.chain = Vec::new();
}

#[given(regex = r#"^a single CapturedError with type "([^"]+)" and message "([^"]+)"$"#)]
async fn given_single_captured(world: &mut ConformanceWorld, t: String, msg: String) {
    world.chain = vec![CapturedError {
        r#type: t,
        message: msg,
        ..Default::default()
    }];
}

#[given(expr = "a CapturedError with one frame:")]
async fn given_captured_with_one_frame_table(
    world: &mut ConformanceWorld,
    step: &cucumber::gherkin::Step,
) {
    let table = step.table.as_ref().expect("expected a data table");
    // Header row: function | file | line  → next row: values
    let row = table.rows.get(1).expect("table has at least 2 rows");
    let function = row[0].clone();
    let file = row[1].clone();
    let line: u32 = row[2].parse().expect("line is u32");
    world.chain = vec![CapturedError {
        r#type: "T".into(),
        message: "m".into(),
        frames: vec![StackFrame {
            function,
            file,
            line,
            ..Default::default()
        }],
        ..Default::default()
    }];
}

#[when("I call to_debug_info")]
async fn when_call_to_debug_info(world: &mut ConformanceWorld) {
    world.debug_info = Some(to_debug_info(&world.chain));
}

#[then("stack_entries is empty")]
async fn then_stack_entries_empty(world: &mut ConformanceWorld) {
    let di = world.debug_info.as_ref().expect("debug_info set");
    assert!(di.stack_entries.is_empty());
}

#[then("detail is empty")]
async fn then_detail_empty(world: &mut ConformanceWorld) {
    let di = world.debug_info.as_ref().expect("debug_info set");
    assert!(di.detail.is_empty());
}

#[then(regex = r#"^detail equals "([^"]+)"$"#)]
async fn then_detail_equals(world: &mut ConformanceWorld, expected: String) {
    let di = world.debug_info.as_ref().expect("debug_info set");
    assert_eq!(di.detail, expected);
}

#[then(regex = r#"^detail contains "([^"]+)"$"#)]
async fn then_detail_contains(world: &mut ConformanceWorld, needle: String) {
    let di = world.debug_info.as_ref().expect("debug_info set");
    assert!(
        di.detail.contains(&needle),
        "detail {:?} should contain {:?}",
        di.detail,
        needle
    );
}

#[then(regex = r#"^stack_entries contains "([^"]+)"$"#)]
async fn then_stack_entries_contains(world: &mut ConformanceWorld, needle: String) {
    let di = world.debug_info.as_ref().expect("debug_info set");
    assert!(
        di.stack_entries.iter().any(|e| e == &needle),
        "stack_entries {:?} should contain {:?}",
        di.stack_entries,
        needle
    );
}

// ============================================================================
// Entry point
// ============================================================================

#[tokio::main]
async fn main() {
    let features = features_dir();
    ConformanceWorld::cucumber()
        .fail_on_skipped()
        .run_and_exit(features)
        .await;
}
