"""Behavior-pinning tests for :mod:`sererr._debug_info` that kill the
surviving mutants from the basic contract tests.

Each test names the mutant id (per ``mutmut show <id>``) it kills.
"""

from __future__ import annotations

from sererr import to_debug_info
from sererr.types import CapturedError, StackFrame


def test_frame_with_line_equals_one_renders_with_colon_one():
    """Mutant 87 (``frame.line > 0`` → ``frame.line > 1``): a frame with
    ``line == 1`` must still render ``(file:1)`` — the boundary case.

    With the mutant, ``1 > 1`` is False, so the formatter would fall
    through to the no-line branch and render ``(file)`` instead.

    Kills mutant 87.
    """
    chain = [
        CapturedError(
            type="T",
            message="m",
            frames=[StackFrame(function="boundary_fn", file="src/b.py", line=1)],
        )
    ]
    di = to_debug_info(chain)
    assert "  at boundary_fn (src/b.py:1)" in di.stack_entries
    # And the no-line form must NOT be present (mutant would produce it).
    assert "  at boundary_fn (src/b.py)" not in di.stack_entries


def test_stack_entries_caused_by_prefix_is_exact_string():
    """Mutant 103 (``"Caused by: {header}"`` → ``"XXCaused by: {header}XX"``
    in ``stack_entries``): the chain separator inside ``stack_entries``
    must be the canonical ``"Caused by: <type>: <message>"`` — not a
    sentinel-wrapped form.

    Kills mutant 103.
    """
    chain = [
        # most-causal-first
        CapturedError(type="Inner", message="i"),
        CapturedError(type="Outer", message="o"),
    ]
    di = to_debug_info(chain)
    # Iteration is reversed → outer is first (no Caused by); inner gets
    # the Caused by prefix on BOTH detail and stack_entries.
    assert "Caused by: Inner: i" in di.stack_entries
    # Mutant 103 would emit "XXCaused by: Inner: iXX"
    assert not any(s.startswith("XX") for s in di.stack_entries)
    assert not any(s.endswith("XX") for s in di.stack_entries)


def test_outermost_stack_header_has_no_caused_by_prefix():
    """Reinforces the ``idx == 0`` branch: the outermost entry's header
    in ``stack_entries`` must NOT carry the ``Caused by:`` prefix.

    Mirrors the Rust test ``to_debug_info_outermost_has_no_caused_by_prefix``.
    """
    chain = [
        CapturedError(type="Inner", message="inner-msg"),
        CapturedError(type="Outer", message="outer-msg"),
    ]
    di = to_debug_info(chain)

    assert di.stack_entries[0] == "Outer: outer-msg"
    # And nowhere does the outermost get wrapped in "Caused by".
    assert "Caused by: Outer" not in di.detail
    assert "Caused by: Outer: outer-msg" not in di.stack_entries


def test_detail_first_line_is_outermost_header_exactly():
    """The first line of ``detail`` must be ``"<type>: <message>"`` of the
    OUTERMOST (last-in-chain) entry — no ``Caused by:`` prefix.
    """
    chain = [
        CapturedError(type="Inner", message="i"),
        CapturedError(type="Middle", message="m"),
        CapturedError(type="Outer", message="o"),
    ]
    di = to_debug_info(chain)
    lines = di.detail.split("\n")
    assert lines[0] == "Outer: o"
    assert lines[1] == "Caused by: Middle: m"
    assert lines[2] == "Caused by: Inner: i"


def test_frame_line_two_renders_with_colon_two():
    """An additional boundary check around ``frame.line > 0``: the very
    common ``line == 2`` case must render the ``:line`` suffix.
    """
    chain = [
        CapturedError(
            type="T",
            message="m",
            frames=[StackFrame(function="f", file="a.py", line=2)],
        )
    ]
    di = to_debug_info(chain)
    assert "  at f (a.py:2)" in di.stack_entries
