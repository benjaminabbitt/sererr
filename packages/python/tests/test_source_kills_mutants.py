"""Behavior-pinning tests for :func:`sererr.source.populate_source_context`
that kill the boundary mutants the existing tests don't catch.

Each test names the mutant id (per ``mutmut show <id>``) it kills.
"""

from __future__ import annotations

from sererr.source import populate_source_context
from sererr.types import StackFrame


class _MapProvider:
    """Minimal in-memory SourceProvider used across these tests."""

    def __init__(self, mapping: dict[str, str]) -> None:
        self.mapping = mapping

    def get_source(self, file: str) -> str | None:  # noqa: D401
        return self.mapping.get(file)


# 6-line source. ``splitlines()`` produces exactly 6 entries.
_SIX_LINE_SOURCE = "a\nb\nc\nd\ne\nf\n"


def test_line_equal_to_lines_plus_one_is_a_noop_not_indexerror():
    """Mutant 69 (``idx >= len(lines)`` → ``idx > len(lines)``): asking
    for the FIRST line past EOF (``idx == len(lines)``) must be a no-op,
    not an ``IndexError``.

    With the mutant the guard is too loose — ``idx > len(lines)`` allows
    ``idx == len(lines)`` to fall through to ``lines[idx]``, raising
    ``IndexError``.

    Kills mutant 69.
    """
    provider = _MapProvider({"f": _SIX_LINE_SOURCE})  # 6 lines
    frame = StackFrame(file="f", line=7)  # line=7 → idx=6 == len(lines)

    # Must not raise; must leave the frame untouched.
    populate_source_context(frame, provider, 2)

    assert frame.context_line == ""
    assert frame.pre_context == []
    assert frame.post_context == []


def test_last_line_works_at_exact_eof_boundary():
    """The LAST line (``idx == len(lines) - 1``) must populate normally.

    Companion to the off-by-one test above: makes sure the guard isn't
    too strict either.
    """
    provider = _MapProvider({"f": _SIX_LINE_SOURCE})  # 6 lines
    frame = StackFrame(file="f", line=6)  # last line: "f"

    populate_source_context(frame, provider, 2)

    assert frame.context_line == "f"
    assert frame.pre_context == ["d", "e"]
    assert frame.post_context == []  # nothing after


def test_pre_context_includes_line_before_when_within_window():
    """Mutant 72 (``max(0, idx - surrounding)`` → ``max(1, idx - surrounding)``):
    when ``idx == 1`` (line 2) and ``surrounding == 5``, the original
    yields ``start = max(0, -4) = 0`` so ``pre_context == lines[0:1]``
    (the first line). With the mutant: ``start = max(1, -4) = 1`` so
    ``pre_context == lines[1:1] == []`` — wrong.

    Kills mutant 72.
    """
    provider = _MapProvider({"f": _SIX_LINE_SOURCE})  # 6 lines
    frame = StackFrame(file="f", line=2)  # second line: "b"; idx == 1

    populate_source_context(frame, provider, 5)

    assert frame.context_line == "b"
    # The very first line (index 0) must be included as pre_context.
    assert frame.pre_context == ["a"]


def test_pre_context_top_of_file_is_empty():
    """``idx == 0`` (line 1): with surrounding > 0, ``max(0, -surrounding)``
    is 0 — ``pre_context == lines[0:0] == []``. The mutant 72 form
    ``max(1, ...)`` also yields ``[]`` here (``lines[1:0] == []``), so
    this test alone doesn't kill mutant 72 — but it pins the top-of-file
    boundary, complementing the line=2 test above.
    """
    provider = _MapProvider({"f": _SIX_LINE_SOURCE})
    frame = StackFrame(file="f", line=1)

    populate_source_context(frame, provider, 3)

    assert frame.context_line == "a"
    assert frame.pre_context == []
    assert frame.post_context == ["b", "c", "d"]


def test_zero_surrounding_yields_empty_pre_and_post():
    """With ``surrounding == 0`` the slices should be empty even though
    the context_line is populated. Pins ``end = min(len, idx + 1 + 0)``
    and ``start = max(0, idx - 0) = idx``.
    """
    provider = _MapProvider({"f": _SIX_LINE_SOURCE})
    frame = StackFrame(file="f", line=3)  # "c"

    populate_source_context(frame, provider, 0)

    assert frame.context_line == "c"
    assert frame.pre_context == []
    assert frame.post_context == []
