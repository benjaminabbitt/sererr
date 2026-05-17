"""Capture-time source-context population.

Producers that embed their own source tree can implement
:class:`SourceProvider` (one method, ``get_source(file) -> str | None``)
and pass it to :func:`populate_source_context` to fill in a frame's
``context_line`` / ``pre_context`` / ``post_context`` fields.

Mirrors the Rust reference (``packages/rust/sererr/src/lib.rs``).
"""

from __future__ import annotations

from typing import Protocol, runtime_checkable

from sererr.types import StackFrame


@runtime_checkable
class SourceProvider(Protocol):
    """Returns the contents of a source file by path.

    Implementors return ``None`` when the file is unknown — that signals
    :func:`populate_source_context` to leave the frame untouched.
    Producers commonly back this with :mod:`importlib.resources` or a
    file-system lookup, but anything that maps a path to a string works.
    """

    def get_source(self, file: str) -> str | None: ...


def populate_source_context(
    frame: StackFrame,
    provider: SourceProvider,
    surrounding: int,
) -> None:
    """Fill in ``frame.context_line`` / ``pre_context`` / ``post_context``.

    ``surrounding`` is the number of lines of pre- and post-context to
    capture (5 matches Sentry's UI). Mutates ``frame`` in place.

    No-op when:

    - ``frame.line == 0`` (line unknown — proto3 zero value),
    - the provider returns ``None`` (no source for this file), or
    - ``frame.line`` exceeds the source file's line count.
    """
    if frame.line == 0:
        return
    source = provider.get_source(frame.file)
    if source is None:
        return
    # `str.splitlines()` mirrors Rust's `str::lines()` (drops trailing newline).
    lines = source.splitlines()
    idx = frame.line - 1  # 1-based → 0-based
    if idx < 0 or idx >= len(lines):
        return
    frame.context_line = lines[idx]
    start = max(0, idx - surrounding)
    frame.pre_context = list(lines[start:idx])
    end = min(len(lines), idx + 1 + surrounding)
    frame.post_context = list(lines[idx + 1 : end])
