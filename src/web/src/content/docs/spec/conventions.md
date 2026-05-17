---
title: Conventions
description: Frame ordering, chain ordering, required fields, in_app heuristic.
---

> *Stub.*

- **Frame ordering**: most-recent-call-first (matches Python; opposite
  of Rust `Backtrace` default — producers normalize).
- **Chain ordering**: most-causal-first; originating caught error is
  LAST element (matches Sentry `exception.values`).
- **`frames` is required**; no rendered-text fallback.
- **`in_app`** is a producer hint; consumers may collapse `false`
  frames by default.
- All fields are optional in the proto3 sense; zero values = "unknown."
- `release` is inherited by chain siblings when empty.
