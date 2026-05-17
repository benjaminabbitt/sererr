---
title: Source bundling
description: Embedding source so context_line is populated at capture time.
---

> *Stub.*

Each language has an idiomatic packaging mechanism:

| Language | Mechanism |
|---|---|
| Rust | `rust-embed` with `include-flate` feature |
| Python | `package_data` in `pyproject.toml`; `importlib.resources.files()` lookup |
| Go | `//go:embed src/**/*.go`; `embed.FS` lookup |
| Java | Sources JAR via `maven-source-plugin`; `ClassLoader.getResourceAsStream` |
| Kotlin | Same as Java; Kotlin sources alongside Java |
| C# | `<EmbeddedResource>` in csproj; `Assembly.GetManifestResourceStream` |

Sererr's per-language packages ship a small helper that wraps each
mechanism. Producers opt in by configuring the embed (file globs) at
build time, then the library populates `context_line` /
`pre_context` / `post_context` automatically on capture.
