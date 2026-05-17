//! Sererr — structured stack-trace + error-chain capture.
//!
//! This crate provides **plain Rust types** for error capture: no
//! protobuf dependency, no compression. Use it directly when you want
//! to gather diagnostics info (frames, chain, mechanism, source
//! context) and process it in-process — log it, render it, attach it
//! to a tracing span, hash it for grouping, etc.
//!
//! To serialize captures over the wire as `sererr.v1` proto, use the
//! companion [`sererr-proto`](https://crates.io/crates/sererr-proto)
//! crate which provides `From` conversions between the types in this
//! crate and the proto-generated types.
//!
//! # Quick start
//!
//! ```
//! use sererr::capture;
//!
//! # #[derive(Debug)] struct MyError;
//! # impl std::fmt::Display for MyError { fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result { Ok(()) } }
//! # impl std::error::Error for MyError {}
//! let err = MyError;
//! let chain = capture(&err, "MyError", "v1.0.0", "host-a");
//! assert!(!chain.is_empty());
//! ```
//!
//! # The shape
//!
//! [`CapturedError`] is a single captured error: type, message, frames,
//! mechanism, plus inlined release / server_name metadata. A cause chain
//! is a `Vec<CapturedError>` **most-causal-first** (the originating
//! caught error is the LAST element — matches Sentry's
//! `exception.values` convention).
//!
//! See <https://sererr.fyi/spec/proto/> for the canonical spec.

#![forbid(unsafe_code)]
#![deny(missing_docs)]

use std::error::Error;

/// A single frame in a captured stack trace.
///
/// Field-compatible with the `sererr.v1.StackFrame` proto definition.
/// All fields use proto3 "zero value = unknown" semantics.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct StackFrame {
    /// Demangled function/method name.
    pub function: String,
    /// Containing module / class / namespace.
    pub module: String,
    /// Native library / crate / package name.
    pub package: String,
    /// Source file (Sentry: `filename`).
    pub file: String,
    /// Absolute path on the build machine.
    pub abs_path: String,
    /// 1-based line number; 0 = unknown.
    pub line: u32,
    /// The exact source line at `line`.
    pub context_line: String,
    /// Source lines preceding `line`.
    pub pre_context: Vec<String>,
    /// Source lines following `line`.
    pub post_context: Vec<String>,
    /// Deep link to a source viewer for this frame.
    pub source_link: String,
    /// Hint to UIs: false for framework/stdlib frames.
    pub in_app: bool,
}

/// Describes how an exception was captured / handled.
///
/// Field-compatible with `sererr.v1.ExceptionMechanism`. Mirrors
/// Sentry's `mechanism` object on the exception interface.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct ExceptionMechanism {
    /// Required: mechanism category — e.g. `"generic"`, `"panic"`,
    /// `"signal"`, `"timeout"`.
    pub r#type: String,
    /// Human-readable description.
    pub description: String,
    /// True when the exception was caught by user code.
    pub handled: bool,
    /// True if synthesized for context rather than raised by a real
    /// failure.
    pub synthetic: bool,
    /// Link to docs explaining this mechanism.
    pub help_link: String,
    /// What attached the mechanism.
    pub source: String,
    /// Identifier within the enclosing chain.
    pub exception_id: u32,
    /// Identifier of the causal parent. 0 = root.
    pub parent_id: u32,
    /// True if this is a Python-style ExceptionGroup.
    pub is_exception_group: bool,
    /// Mechanism-specific metadata. Stored in a `BTreeMap` (not
    /// `HashMap`) so encoded bytes are deterministic — sorted keys
    /// produce a canonical wire encoding that cross-language consumers
    /// can byte-compare for conformance.
    pub data: std::collections::BTreeMap<String, String>,
}

/// A captured error.
///
/// Field-compatible with `sererr.v1.CapturedError`. See the crate
/// documentation for the chain conventions.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct CapturedError {
    /// Error class/type name, e.g. `"sqlx::Error::PoolTimedOut"`.
    pub r#type: String,
    /// Short rendering of the error itself, NOT including the chain.
    pub message: String,
    /// Frames, most-recent-call-first.
    pub frames: Vec<StackFrame>,
    /// Mechanism describing how the exception was captured.
    pub mechanism: Option<ExceptionMechanism>,
    /// Build identifier (e.g. `"binary@semver"` or git SHA).
    pub release: String,
    /// Producing host / pod name.
    pub server_name: String,
}

/// Source provider for capture-time `context_line` / `pre_context` /
/// `post_context` population.
///
/// Implementors return the contents of a source file by path (the
/// `file` field on [`StackFrame`]). Stable consumers can implement
/// this trait against their own embedded-source layout
/// (`include_str!`, `include_bytes!`). Nightly consumers can use the
/// [`sererr-flate`](https://crates.io/crates/sererr-flate) crate to
/// derive an implementation that embeds gzip-compressed source via
/// `rust-embed`.
///
/// Returning `None` means "no source for this file" — the frame is
/// left untouched.
pub trait SourceProvider {
    /// Returns the source contents for `file`, or `None` when not
    /// available.
    fn get_source(&self, file: &str) -> Option<String>;
}

/// Populate a `StackFrame`'s `context_line` / `pre_context` /
/// `post_context` fields from a `SourceProvider`.
///
/// `surrounding` is the number of lines of pre- and post-context to
/// capture (5 is a sensible default — matches Sentry's UI).
///
/// No-op when the provider returns `None`, when the frame's `line` is
/// 0 (unknown), or when the file content is shorter than `frame.line`.
pub fn populate_source_context<P: SourceProvider>(
    frame: &mut StackFrame,
    provider: &P,
    surrounding: usize,
) {
    if frame.line == 0 {
        return;
    }
    let Some(source) = provider.get_source(&frame.file) else {
        return;
    };
    let lines: Vec<&str> = source.lines().collect();
    let idx = (frame.line as usize).saturating_sub(1);
    if idx >= lines.len() {
        return;
    }
    frame.context_line = lines[idx].to_string();
    let start = idx.saturating_sub(surrounding);
    frame.pre_context = lines[start..idx].iter().map(|s| s.to_string()).collect();
    let end = (idx + 1 + surrounding).min(lines.len());
    frame.post_context = lines[idx + 1..end].iter().map(|s| s.to_string()).collect();
}

/// Adapter shape for the gRPC error model (`google.rpc.DebugInfo`).
///
/// Plain Rust struct, wire-compatible with `google.rpc.DebugInfo`. Use
/// [`to_debug_info`] to render a capture into this shape. The
/// `sererr-proto` crate provides a `From<DebugInfo> for
/// google_rpc::DebugInfo` conversion when you need the actual proto
/// message.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct DebugInfo {
    /// Stack-trace entries, one line per frame, most-recent-call-first
    /// across the entire chain (separated by `Caused by:` lines).
    pub stack_entries: Vec<String>,
    /// Joined `"<type>: <message>"` rendering of the chain.
    pub detail: String,
}

/// Walk a Rust error chain into a sererr capture.
///
/// The returned `Vec` is **most-causal-first** — the originating caught
/// error is the last element. Each entry carries the same frames
/// (captured at the call site); chain position is identified by
/// `mechanism.exception_id`.
///
/// `type_name` labels the leaf error since Rust cannot recover the
/// runtime type of a `&dyn Error` — producers supply it at the catch
/// site.
pub fn capture(
    err: &(dyn Error + 'static),
    type_name: &str,
    release: &str,
    server_name: &str,
) -> Vec<CapturedError> {
    let frames = capture_frames();

    let mut chain: Vec<CapturedError> = Vec::new();
    let mut current: Option<&(dyn Error + 'static)> = Some(err);
    let mut leaf_type = type_name.to_string();
    while let Some(e) = current {
        chain.push(CapturedError {
            r#type: std::mem::take(&mut leaf_type),
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

/// Capture frames from the calling stack.
///
/// Frames are returned most-recent-call-first. Runtime / framework
/// frames are marked `in_app = false` via a prefix heuristic.
pub fn capture_frames() -> Vec<StackFrame> {
    let bt = backtrace::Backtrace::new();

    let mut out: Vec<StackFrame> = Vec::new();
    for frame in bt.frames() {
        for symbol in frame.symbols() {
            let function = symbol.name().map(|n| n.to_string()).unwrap_or_default();
            let file = symbol
                .filename()
                .map(|p| p.display().to_string())
                .unwrap_or_default();
            let line = symbol.lineno().unwrap_or(0);
            let in_app = is_app_frame(&function);

            out.push(StackFrame {
                function,
                file,
                line,
                in_app,
                ..Default::default()
            });
        }
    }
    out
}

/// Default `in_app` heuristic. Producers with a better signal should
/// not rely on this.
pub fn is_app_frame(function: &str) -> bool {
    const NON_APP_PREFIXES: &[&str] = &[
        "std::",
        "core::",
        "alloc::",
        "<core::",
        "<std::",
        "<alloc::",
        "<unknown>",
        "rustc_",
        "__rust_",
        "_start",
        "main",
        "tokio::",
        "backtrace::",
        "sererr::",
    ];
    !NON_APP_PREFIXES.iter().any(|p| function.starts_with(p))
}

/// Adapt a sererr capture into the [`DebugInfo`] shape.
///
/// `stack_entries` gets one line per frame, formatted as
/// `"  at <function> (<file>:<line>)"`, most-recent-call-first within
/// each chain entry, separated by `"Caused by: <type>: <message>"`
/// lines across the chain.
///
/// `detail` joins `"<type>: <message>"` across the chain with
/// `"\nCaused by: "` separators.
pub fn to_debug_info(chain: &[CapturedError]) -> DebugInfo {
    if chain.is_empty() {
        return DebugInfo::default();
    }

    let mut stack_entries: Vec<String> = Vec::new();
    let mut detail_parts: Vec<String> = Vec::new();

    for (idx, entry) in chain.iter().rev().enumerate() {
        let header = format!("{}: {}", entry.r#type, entry.message);
        detail_parts.push(if idx == 0 {
            header.clone()
        } else {
            format!("Caused by: {header}")
        });

        if idx == 0 {
            stack_entries.push(header);
        } else {
            stack_entries.push(format!("Caused by: {header}"));
        }

        for frame in &entry.frames {
            stack_entries.push(format_frame_line(frame));
        }
    }

    DebugInfo {
        stack_entries,
        detail: detail_parts.join("\n"),
    }
}

fn format_frame_line(frame: &StackFrame) -> String {
    let location = if frame.file.is_empty() {
        String::new()
    } else if frame.line > 0 {
        format!(" ({}:{})", frame.file, frame.line)
    } else {
        format!(" ({})", frame.file)
    };
    let function = if frame.function.is_empty() {
        "<unknown>"
    } else {
        &frame.function
    };
    format!("  at {function}{location}")
}
