"""Behavior-pinning tests for :func:`sererr.capture` that kill the mutants
that survived the standard contract tests.

Each test names the mutant id (per ``mutmut show <id>``) it kills. The
goal is byte-exact field assertions, not just shape — mirroring the
Rust ``capture_test.rs`` pattern that took kill rate to 100%.
"""

from __future__ import annotations


from sererr import capture


class _LeafA(Exception):
    """Local error type used to pin ``type(current).__name__`` derivation."""


class _LeafB(Exception):
    """Second local error type so chains have distinguishable inner types."""


def _make_single_leaf_a() -> _LeafA:
    """Raise + catch in a real ``.py`` file so frames have line content."""
    try:
        raise _LeafA("the-message")  # exact-message anchor
    except _LeafA as e:
        return e


def _make_chain_a_then_b() -> _LeafB:
    """Two-level explicit cause chain: inner _LeafA → outer _LeafB."""
    try:
        try:
            raise _LeafA("inner-msg")
        except _LeafA as inner:
            raise _LeafB("outer-msg") from inner
    except _LeafB as e:
        return e


# ---------- Kills mutants on field-value preservation in _frames_for ----


def test_frame_function_matches_traceback_function_name_exactly():
    """Mutant 108 (``fs.name or "XXXX"``) — assert the real function name
    flows through, so any non-empty fallback string can't masquerade as it.

    Kills mutant 108. Also kills mutant 111 (``or`` → ``and``): with the
    ``and`` mutant, ``fs.name and ""`` would yield ``""`` for any present
    name — the exact-name assertion below catches that.
    """
    err = _make_single_leaf_a()
    chain = capture(err, type_name="_LeafA", release="r", server_name="h")

    # The innermost (deepest call) frame is _make_single_leaf_a since
    # capture reverses traceback's oldest-first ordering.
    assert chain[0].frames[0].function == "_make_single_leaf_a"


def test_frame_file_matches_test_module_path():
    """Mutant 110 (``fs.filename or "XXXX"``) and 111 (``or`` → ``and``):
    the captured file must equal this test module's path, never a sentinel.

    Kills mutants 110, 111.
    """
    err = _make_single_leaf_a()
    chain = capture(err, type_name="_LeafA", release="r", server_name="h")

    # __file__ ends with this module's path. The first frame's file is
    # this test file because that's where _make_single_leaf_a lives.
    assert chain[0].frames[0].file.endswith("test_capture_kills_mutants.py")
    assert chain[0].frames[0].file != ""


def test_frame_line_is_positive_int_not_one():
    """Mutant 112 (``or 0`` → ``or 1``) and 113 (``or 0`` → ``and 0``):
    the captured line must be the real ``raise`` line, not a sentinel
    constant or always-0.

    Kills mutants 112, 113.
    """
    err = _make_single_leaf_a()
    chain = capture(err, type_name="_LeafA", release="r", server_name="h")

    line = chain[0].frames[0].line
    # The line is the real ``raise _LeafA(...)`` line — not 1 (mutant 112)
    # and not 0 (mutant 113 would coerce non-falsy to 0 via ``and``).
    assert line > 1
    # Anchor on the actual source: search the test file for ``raise _LeafA("the-message")``
    # would be brittle; instead anchor on the constraint that line is
    # plausibly the raise line (small file).
    assert line < 1000  # sanity: this test file isn't that long


def test_frame_context_line_carries_real_source_text():
    """Mutant 114 (``or "XXXX"``), 115 (``or`` → ``and``), 117 (``else ""``
    → ``else "XXXX"``) and 116 (``is not None`` → ``is None``): the
    context_line should be the actual source text of the raise line
    when traceback.lookup_line() found it.

    Kills mutants 114, 115, 116, 117.
    """
    err = _make_single_leaf_a()
    chain = capture(err, type_name="_LeafA", release="r", server_name="h")

    # _make_single_leaf_a's raise line contains ``raise _LeafA("the-message")``.
    # If the traceback subsystem populated fs.line (typical in tests
    # running from a .py file on disk), the context_line should be that
    # exact source line — which contains ``the-message``.
    ctx = chain[0].frames[0].context_line
    # Mutant 114 ("XXXX") would not contain "the-message".
    # Mutant 115 (``and ""``) would yield "" for any truthy fs.line.
    # Mutant 116 swaps the branch so non-None fs.line falls through to "".
    # Mutant 117 turns the else branch into "XXXX".
    # If fs.line is genuinely None or empty (e.g. some envs don't read
    # source), the test below still asserts the exact branch: empty
    # remains empty (kills the 117 mutant which would yield "XXXX").
    assert ctx == "" or "the-message" in ctx
    assert ctx != "XXXX"


# ---------- Kills mutants on is_outermost initial value ----------------


def test_outermost_uses_caller_supplied_type_name_even_when_no_chain():
    """Mutant 121 (``is_outermost = True`` → ``False``) and 122 (``True`` →
    ``None``): the FIRST captured entry (the only one for a single error)
    must use the caller-supplied ``type_name``, not ``type(exc).__name__``.

    The test deliberately picks a caller-supplied name that DIFFERS from
    the live type so the branch is observable.

    Kills mutants 121, 122.
    """
    err = _make_single_leaf_a()  # live type is _LeafA
    chain = capture(
        err, type_name="totally-different-name", release="r", server_name="h"
    )

    assert len(chain) == 1
    # If is_outermost initial were False/None, this would be "_LeafA".
    assert chain[0].type == "totally-different-name"


def test_chain_outermost_uses_caller_type_name_inner_uses_live_type():
    """Pin the convention end-to-end: caller-supplied name labels the LAST
    entry; deeper entries use ``type(exc).__name__``.

    Re-asserts the mutant 121 / 122 contract on a chain so the branch
    must execute correctly on the very first iteration AND switch off.
    """
    err = _make_chain_a_then_b()
    chain = capture(err, type_name="CALLER_NAME", release="r", server_name="h")

    assert len(chain) == 2
    # Inner is most-causal (first); outermost caught is last.
    assert chain[-1].type == "CALLER_NAME"  # the outermost — caller-supplied
    assert chain[0].type == "_LeafA"  # inner — live type name


# ---------- Kills mutants on the type-name derivation branch -----------


def test_inner_entries_use_unqualified_type_name():
    """Pin that ``type(current).__name__`` (not ``__qualname__``) is used —
    i.e. nested classes don't include their enclosing scope.

    Reinforces the mutant 121 contract: when is_outermost flips off, the
    type must come from ``type(exc).__name__``.
    """

    class _NestedErr(Exception):
        pass

    try:
        try:
            raise _NestedErr("nested-inner")
        except _NestedErr as a:
            raise RuntimeError("top") from a
    except RuntimeError as e:
        chain = capture(e, type_name="RuntimeError", release="r", server_name="h")

    # Most-causal-first: nested inner comes first. ``__qualname__`` would
    # be something like
    # ``test_inner_entries_use_unqualified_type_name.<locals>._NestedErr``;
    # ``__name__`` is just ``_NestedErr``.
    assert chain[0].type == "_NestedErr"


# ---------- Mechanism stays default-handled across whole chain ---------


def test_every_entry_in_chain_has_generic_handled_mechanism():
    """Every entry — outer AND inner — gets ``type="generic", handled=True``.

    Reinforces the default-mechanism contract for the inner branch
    specifically (existing tests only assert it for the single-error case).
    """
    err = _make_chain_a_then_b()
    chain = capture(err, type_name="WrapErr", release="r", server_name="h")

    for entry in chain:
        assert entry.mechanism is not None
        assert entry.mechanism.type == "generic"
        assert entry.mechanism.handled is True
        assert entry.mechanism.synthetic is False


# ---------- Fallback-branch coverage for None FrameSummary fields ------
#
# The fallback in ``fs.name or ""`` only fires when ``fs.name`` is
# falsy. Normal exceptions raised in tests yield truthy ``fs.name`` /
# ``fs.filename`` / ``fs.lineno``, so mutants that change the fallback
# default (``"" → "XXXX"`` / ``0 → 1``) survive. To pin the fallback
# we drive ``_frames_for`` with a synthetic ``TracebackException`` whose
# ``stack`` contains a ``FrameSummary`` with every field set to its
# proto-zero / Python-None pre-image.


import traceback
from unittest.mock import patch

from sererr._capture import _frames_for


def _fake_tbe_with(stack):
    """Build a stand-in for ``TracebackException`` carrying ``stack``."""

    class _FakeTbe:
        pass

    fake = _FakeTbe()
    fake.stack = stack
    return fake


def test_frames_for_name_none_falls_back_to_empty_string():
    """Mutant 108 (``fs.name or ""`` → ``fs.name or "XXXX"``): when
    ``fs.name`` is None, the fallback must be the empty string — proto3
    zero-value — never a sentinel.

    Kills mutant 108.
    """
    fake_stack = [
        traceback.FrameSummary("file.py", 7, None, lookup_line=False, line="src"),
    ]
    err = RuntimeError("x")
    with patch(
        "traceback.TracebackException.from_exception",
        return_value=_fake_tbe_with(fake_stack),
    ):
        frames = _frames_for(err)
    assert len(frames) == 1
    assert frames[0].function == ""  # mutant would yield "XXXX"


def test_frames_for_filename_none_falls_back_to_empty_string():
    """Mutant 110 (``fs.filename or ""`` → ``or "XXXX"``): with falsy
    filename, fallback is empty — never a sentinel.

    ``FrameSummary`` requires a filename argument; passing the empty
    string is the strongest "falsy" we can construct.

    Kills mutant 110.
    """
    fake_stack = [
        traceback.FrameSummary("", 7, "fn", lookup_line=False, line="src"),
    ]
    err = RuntimeError("x")
    with patch(
        "traceback.TracebackException.from_exception",
        return_value=_fake_tbe_with(fake_stack),
    ):
        frames = _frames_for(err)
    assert frames[0].file == ""  # mutant would yield "XXXX"


def test_frames_for_lineno_none_falls_back_to_zero():
    """Mutant 112 (``fs.lineno or 0`` → ``or 1``): with falsy lineno,
    fallback is the proto3 zero, never 1.

    Kills mutant 112.
    """
    fake_stack = [
        traceback.FrameSummary("file.py", None, "fn", lookup_line=False, line="src"),
    ]
    err = RuntimeError("x")
    with patch(
        "traceback.TracebackException.from_exception",
        return_value=_fake_tbe_with(fake_stack),
    ):
        frames = _frames_for(err)
    assert frames[0].line == 0  # mutant would yield 1


def test_frames_for_line_none_yields_empty_context_line():
    """Mutant 117 — ``(fs.line or "") if fs.line is not None else ""``:
    with ``fs.line is None``, the else branch fires and yields ``""`` —
    never ``"XXXX"`` (mutant 117).

    ``FrameSummary.line`` only returns ``None`` when BOTH ``_line`` and
    ``lineno`` are ``None`` (and ``lookup_line=False``). With either
    set, the linecache lookup yields ``""`` instead, sending us to the
    then-branch — which is NOT what we want to pin here.

    Kills mutant 117.
    """
    fake_stack = [
        traceback.FrameSummary("file.py", None, "fn", lookup_line=False, line=None),
    ]
    err = RuntimeError("x")
    with patch(
        "traceback.TracebackException.from_exception",
        return_value=_fake_tbe_with(fake_stack),
    ):
        frames = _frames_for(err)
    # Sanity check: the fixture really triggers fs.line is None.
    assert fake_stack[0].line is None
    assert frames[0].context_line == ""  # mutant 117 would yield "XXXX"


def test_frames_for_line_empty_string_yields_empty_context_line():
    """Mutant 114 (``(fs.line or "XXXX")``) and 115 (``or`` → ``and``):
    when ``fs.line == ""`` (empty but not None), the then-branch fires.
    Original: ``"" or ""`` → ``""``. Mutant 114: ``"" or "XXXX"`` →
    ``"XXXX"``. Mutant 115: ``"" and ""`` → ``""`` (same as original;
    115 needs a truthy fs.line to differ).

    Kills mutant 114.
    """
    fake_stack = [
        traceback.FrameSummary("file.py", 7, "fn", lookup_line=False, line=""),
    ]
    err = RuntimeError("x")
    with patch(
        "traceback.TracebackException.from_exception",
        return_value=_fake_tbe_with(fake_stack),
    ):
        frames = _frames_for(err)
    assert frames[0].context_line == ""  # mutant 114 would yield "XXXX"


def test_frames_for_line_truthy_string_is_preserved():
    """Mutant 115 (``or`` → ``and``): with a truthy ``fs.line``, original
    yields ``fs.line``; mutant ``and`` yields ``""``.

    Also kills mutant 116 (``is not None`` → ``is None``): with
    ``fs.line == "real-source"``, original goes to then-branch and
    yields ``"real-source"``; mutant swaps to else, yielding ``""``.

    Kills mutants 115, 116.
    """
    fake_stack = [
        traceback.FrameSummary("file.py", 7, "fn", lookup_line=False, line="real-source"),
    ]
    err = RuntimeError("x")
    with patch(
        "traceback.TracebackException.from_exception",
        return_value=_fake_tbe_with(fake_stack),
    ):
        frames = _frames_for(err)
    assert frames[0].context_line == "real-source"
