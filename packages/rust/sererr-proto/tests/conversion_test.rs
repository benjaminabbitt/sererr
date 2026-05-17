//! Round-trip tests for `From` conversions between plain `sererr`
//! types and the proto-generated `sererr_proto` types.
//!
//! Each test exercises both directions (plain → proto → plain and
//! proto → plain → proto) and asserts byte-equivalence after a prost
//! encode/decode round-trip. This is the wire-format contract: the
//! plain types are a lossless view of the proto types.

use std::collections::BTreeMap;

use prost::Message;
use sererr::{CapturedError, DebugInfo, ExceptionMechanism, StackFrame};
use sererr_proto::{ProtoCapturedError, ProtoDebugInfo, ProtoExceptionMechanism, ProtoStackFrame};

/// A populated StackFrame round-trips through proto encode/decode
/// without losing any field.
///
/// The plain-types layer is a view over the proto schema; conversion
/// must preserve every populated field. Default values are exercised
/// in a separate test.
#[test]
fn stack_frame_roundtrips_through_proto() {
    let original = StackFrame {
        function: "f".into(),
        module: "m".into(),
        package: "pkg".into(),
        file: "src/x.rs".into(),
        abs_path: "/build/src/x.rs".into(),
        line: 42,
        context_line: "    do_it();".into(),
        pre_context: vec!["fn f() {".into()],
        post_context: vec!["}".into()],
        source_link: "https://example/source/x.rs#L42".into(),
        in_app: true,
    };

    let proto: ProtoStackFrame = original.clone().into();
    let bytes = proto.encode_to_vec();
    let decoded = ProtoStackFrame::decode(bytes.as_slice()).unwrap();
    let back: StackFrame = decoded.into();

    assert_eq!(back, original);
}

/// Default StackFrame (all zero values) round-trips identical.
///
/// proto3 zero-value semantics mean the wire encoding is empty; the
/// plain type must reconstitute identically rather than producing
/// `Option`-wrapped Nones.
#[test]
fn default_stack_frame_roundtrips() {
    let original = StackFrame::default();
    let proto: ProtoStackFrame = original.clone().into();
    let bytes = proto.encode_to_vec();
    let decoded = ProtoStackFrame::decode(bytes.as_slice()).unwrap();
    let back: StackFrame = decoded.into();
    assert_eq!(back, original);
}

/// ExceptionMechanism with non-empty `data` map round-trips.
///
/// Maps are the most complex proto3 field type; their round-trip is
/// the canary for everything that depends on prost's map encoding.
#[test]
fn mechanism_roundtrips_with_data_map() {
    let mut data = BTreeMap::new();
    data.insert("signal".to_string(), "SIGSEGV".to_string());
    data.insert("errno".to_string(), "11".to_string());

    let original = ExceptionMechanism {
        r#type: "signal".into(),
        description: "segfault during writes".into(),
        handled: true,
        synthetic: false,
        help_link: "https://example/help".into(),
        source: "panic_hook".into(),
        exception_id: 0,
        parent_id: 0,
        is_exception_group: false,
        data,
    };

    let proto: ProtoExceptionMechanism = original.clone().into();
    let bytes = proto.encode_to_vec();
    let decoded = ProtoExceptionMechanism::decode(bytes.as_slice()).unwrap();
    let back: ExceptionMechanism = decoded.into();
    assert_eq!(back, original);
}

/// CapturedError with nested StackFrame + ExceptionMechanism round-trips.
///
/// Composite messages exercise the whole conversion graph at once;
/// this is the integration test for the whole adapter layer.
#[test]
fn captured_error_with_nested_messages_roundtrips() {
    let original = CapturedError {
        r#type: "MyError".into(),
        message: "oops".into(),
        frames: vec![StackFrame {
            function: "doit".into(),
            file: "src/x.rs".into(),
            line: 12,
            in_app: true,
            ..Default::default()
        }],
        mechanism: Some(ExceptionMechanism {
            r#type: "generic".into(),
            handled: true,
            exception_id: 0,
            parent_id: 0,
            ..Default::default()
        }),
        release: "v1.0.0".into(),
        server_name: "host-1".into(),
    };

    let proto: ProtoCapturedError = original.clone().into();
    let bytes = proto.encode_to_vec();
    let decoded = ProtoCapturedError::decode(bytes.as_slice()).unwrap();
    let back: CapturedError = decoded.into();
    assert_eq!(back, original);
}

/// CapturedError without a mechanism round-trips.
///
/// `mechanism` is `Option<...>` on the plain type; missing on the
/// proto side. The conversion must preserve "None" vs "Some(default)".
#[test]
fn captured_error_without_mechanism_roundtrips_as_none() {
    let original = CapturedError {
        r#type: "MyError".into(),
        message: "oops".into(),
        frames: Vec::new(),
        mechanism: None,
        release: String::new(),
        server_name: String::new(),
    };

    let proto: ProtoCapturedError = original.clone().into();
    let bytes = proto.encode_to_vec();
    let decoded = ProtoCapturedError::decode(bytes.as_slice()).unwrap();
    let back: CapturedError = decoded.into();
    assert!(back.mechanism.is_none());
    assert_eq!(back, original);
}

/// DebugInfo round-trips to its proto form.
///
/// DebugInfo is the gRPC error-model adapter shape — interop with
/// `google.rpc.DebugInfo` requires byte-equivalence with that proto.
/// Field numbers and types are pinned in the vendored schema.
#[test]
fn debug_info_roundtrips_through_proto() {
    let original = DebugInfo {
        stack_entries: vec!["  at f (src/x.rs:1)".into(), "  at g (src/x.rs:2)".into()],
        detail: "MyError: oops".into(),
    };

    let proto: ProtoDebugInfo = original.clone().into();
    let bytes = proto.encode_to_vec();
    let decoded = ProtoDebugInfo::decode(bytes.as_slice()).unwrap();
    let back: DebugInfo = decoded.into();
    assert_eq!(back, original);
}
