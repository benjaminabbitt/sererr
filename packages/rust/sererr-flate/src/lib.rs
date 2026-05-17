//! Compressed source-bundle support for [`sererr`].
//!
//! Adapts a [`rust_embed::RustEmbed`]-derived asset bundle into a
//! [`sererr::SourceProvider`], so producers can populate
//! `context_line` / `pre_context` / `post_context` on captured frames
//! from their own gzip-compressed source tree.
//!
//! **Requires nightly Rust** — `rust-embed`'s `include-flate` feature
//! depends on the unstable `proc_macro_span` API (tracked at
//! <https://github.com/rust-lang/rust/issues/54725>). Stable users can
//! implement [`sererr::SourceProvider`] themselves against
//! `include_str!` / `include_bytes!` content.
//!
//! # Quick start
//!
//! ```ignore
//! use rust_embed::RustEmbed;
//! use sererr::{capture, populate_source_context};
//! use sererr_flate::EmbedSourceProvider;
//!
//! #[derive(RustEmbed)]
//! #[folder = "src/"]
//! #[include = "*.rs"]
//! struct MySource;
//!
//! // Inside a catch arm:
//! let mut chain = capture(&err, "MyError", "v1.0.0", "host");
//! let source = EmbedSourceProvider::<MySource>::new();
//! for entry in &mut chain {
//!     for frame in &mut entry.frames {
//!         populate_source_context(frame, &source, 5);
//!     }
//! }
//! ```
//!
//! Frames are now populated with their source context, ready to ship
//! over the wire via `sererr-proto`.

#![forbid(unsafe_code)]
#![deny(missing_docs)]

use std::marker::PhantomData;

use rust_embed::RustEmbed;

/// Adapter that bridges any [`rust_embed::RustEmbed`]-derived type into
/// [`sererr::SourceProvider`].
///
/// `T` is a unit struct decorated with `#[derive(RustEmbed)]` and the
/// `#[folder = "..."]` attribute. Construct via [`Self::new`] and
/// pass by reference to [`sererr::populate_source_context`].
pub struct EmbedSourceProvider<T: RustEmbed>(PhantomData<T>);

impl<T: RustEmbed> EmbedSourceProvider<T> {
    /// Construct a new adapter. Zero-sized; cheap to make on every
    /// capture.
    pub fn new() -> Self {
        EmbedSourceProvider(PhantomData)
    }
}

impl<T: RustEmbed> Default for EmbedSourceProvider<T> {
    fn default() -> Self {
        Self::new()
    }
}

impl<T: RustEmbed> sererr::SourceProvider for EmbedSourceProvider<T> {
    fn get_source(&self, file: &str) -> Option<String> {
        let asset = T::get(file)?;
        // rust-embed returns bytes; UTF-8 lossily so non-UTF-8 source
        // (rare) doesn't crash the capture path.
        Some(String::from_utf8_lossy(&asset.data).into_owned())
    }
}
