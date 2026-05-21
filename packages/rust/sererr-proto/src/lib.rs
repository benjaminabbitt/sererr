//! Protobuf bindings for sererr.
//!
//! This crate provides prost-generated types for the `sererr.v1`
//! schema and the vendored `google.rpc.DebugInfo`, plus
//! [`From`]/[`Into`] conversions between those proto types and the
//! plain types from the [`sererr`](https://crates.io/crates/sererr)
//! crate.
//!
//! # Two-layer design
//!
//! The plain types live in the `sererr` crate and have no proto
//! dependency. Use them when you want to gather diagnostics without
//! pulling in protobuf — for in-process logging, tracing-span
//! attachment, hash-based grouping, etc.
//!
//! This crate provides the wire-format layer: serialize captures over
//! gRPC, store them in a DLQ, attach them to a `google.rpc.Status`,
//! etc. The conversion is lossless in both directions.
//!
//! # Example
//!
//! ```no_run
//! use sererr_proto::ProtoCapturedError;
//! use sererr::capture;
//! use prost::Message;
//!
//! # #[derive(Debug)] struct MyError;
//! # impl std::fmt::Display for MyError { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { Ok(()) } }
//! # impl std::error::Error for MyError {}
//! let chain = capture(&MyError, "MyError", "v1.0.0", "host");
//! let proto_chain: Vec<ProtoCapturedError> =
//!     chain.into_iter().map(Into::into).collect();
//! let bytes = proto_chain[0].encode_to_vec();
//! ```
//!
//! # Code generation
//!
//! Proto types are generated via [`buf`](https://buf.build) using the
//! `protoc-gen-prost` plugin and committed under `src/gen/`. Regenerate
//! after changing the proto schema:
//!
//! ```bash
//! just proto-gen   # (from this crate)
//! ```

#![forbid(unsafe_code)]
#![deny(missing_docs)]

// The generated module tree contains:
//   gen::google::rpc::DebugInfo
//   gen::sererr::v1::{CapturedError, StackFrame, ExceptionMechanism}
//
// We expose them under stable public paths (`google_rpc`, `sererr_v1`)
// so downstream code doesn't depend on the buf-plugin output layout.
#[allow(missing_docs)]
mod gen;

/// Generated proto types for `sererr.v1`. Mirrors the schema in
/// `proto/sererr/v1/sererr.proto`.
pub mod sererr_v1 {
    pub use crate::gen::sererr::v1::*;
}

/// Generated proto types for the vendored `google.rpc.DebugInfo`.
///
/// Wire-compatible with the upstream `google.rpc.DebugInfo` from
/// `google/rpc/error_details.proto`.
pub mod google_rpc {
    pub use crate::gen::google::rpc::*;
}

pub use google_rpc::DebugInfo as ProtoDebugInfo;
pub use sererr_v1::{
    CapturedError as ProtoCapturedError, ExceptionMechanism as ProtoExceptionMechanism,
    StackFrame as ProtoStackFrame,
};

// ---------- plain → proto ----------

impl From<sererr::StackFrame> for ProtoStackFrame {
    fn from(f: sererr::StackFrame) -> Self {
        ProtoStackFrame {
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

impl From<sererr::ExceptionMechanism> for ProtoExceptionMechanism {
    fn from(m: sererr::ExceptionMechanism) -> Self {
        ProtoExceptionMechanism {
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

impl From<sererr::CapturedError> for ProtoCapturedError {
    fn from(e: sererr::CapturedError) -> Self {
        ProtoCapturedError {
            r#type: e.r#type,
            message: e.message,
            frames: e.frames.into_iter().map(Into::into).collect(),
            mechanism: e.mechanism.map(Into::into),
            release: e.release,
            server_name: e.server_name,
        }
    }
}

impl From<sererr::DebugInfo> for ProtoDebugInfo {
    fn from(di: sererr::DebugInfo) -> Self {
        ProtoDebugInfo {
            stack_entries: di.stack_entries,
            detail: di.detail,
        }
    }
}

// ---------- proto → plain ----------

impl From<ProtoStackFrame> for sererr::StackFrame {
    fn from(f: ProtoStackFrame) -> Self {
        sererr::StackFrame {
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

impl From<ProtoExceptionMechanism> for sererr::ExceptionMechanism {
    fn from(m: ProtoExceptionMechanism) -> Self {
        sererr::ExceptionMechanism {
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

impl From<ProtoCapturedError> for sererr::CapturedError {
    fn from(e: ProtoCapturedError) -> Self {
        sererr::CapturedError {
            r#type: e.r#type,
            message: e.message,
            frames: e.frames.into_iter().map(Into::into).collect(),
            mechanism: e.mechanism.map(Into::into),
            release: e.release,
            server_name: e.server_name,
        }
    }
}

impl From<ProtoDebugInfo> for sererr::DebugInfo {
    fn from(di: ProtoDebugInfo) -> Self {
        sererr::DebugInfo {
            stack_entries: di.stack_entries,
            detail: di.detail,
        }
    }
}
