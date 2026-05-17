---
title: Lazy source resolver
description: Consumer-side fallback that fetches source from a git SHA on demand.
---

> *Stub.*

For captures that didn't embed source at capture time, consumers can
resolve `context_line` / `pre_context` / `post_context` on read by
fetching source from the repository at `CapturedError.release` (which
must be a git SHA or contain one).

Sererr's per-language packages ship a resolver client with:

- Pluggable backend (raw git / GitHub API / GitLab API / local mirror)
- Per-SHA cache (the source at a given commit never changes)
- Best-effort: missing SHA or unreachable repo returns the frame
  unchanged

Lazy resolution and embed-at-capture coexist: consumers prefer
producer-populated fields and only fall back to the resolver when
they're empty.
