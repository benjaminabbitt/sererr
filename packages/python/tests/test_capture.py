"""Behavior tests for :func:`sererr.capture`.

Mirrors the contract pinned by the Rust tests in
``packages/rust/sererr/tests/capture_test.rs`` — adapted for Python's
exception-chain semantics (``__cause__`` / ``__context__`` /
``__suppress_context__``).
"""

import pytest

from sererr import capture


class LeafError(Exception):
    """Simple test error type."""


class WrapError(Exception):
    """Wrapping error type used for chain tests."""


def _raise_single() -> Exception:
    """Catch and return a single error with no chain."""
    try:
        raise LeafError("leaf")
    except LeafError as e:
        return e


def _raise_chain_two() -> Exception:
    """Catch a two-level chain: outer ``raise ... from inner``."""
    try:
        try:
            raise LeafError("inner")
        except LeafError as inner:
            raise WrapError("outer") from inner
    except WrapError as e:
        return e


def _raise_chain_three() -> Exception:
    """Catch a three-level cause chain."""
    try:
        try:
            try:
                raise LeafError("inner")
            except LeafError as a:
                raise WrapError("middle") from a
        except WrapError as b:
            raise WrapError("outer") from b
    except WrapError as e:
        return e


def test_single_error_produces_one_entry():
    """Single error with no cause/context yields a one-entry chain."""
    err = _raise_single()
    chain = capture(err, type_name="LeafError", release="rel", server_name="host")

    assert len(chain) == 1
    assert chain[0].type == "LeafError"
    assert chain[0].message == "leaf"
    assert chain[0].release == "rel"
    assert chain[0].server_name == "host"


def test_chain_walks_cause_most_causal_first():
    """Two-level chain: inner cause first, outer (caught) last."""
    err = _raise_chain_two()
    chain = capture(err, type_name="WrapError", release="rel", server_name="host")

    assert len(chain) == 2
    assert chain[0].message == "inner"
    assert chain[1].message == "outer"
    # Caller-supplied type_name labels the LAST element (the caught/originating).
    assert chain[-1].type == "WrapError"
    # Inner's type is derived from the live Python exception (unqualified).
    assert chain[0].type == "LeafError"


def test_mechanism_ids_stamped_correctly():
    """Three-deep chain stamps exception_id sequentially with prior-parent linkage."""
    err = _raise_chain_three()
    chain = capture(err, type_name="WrapError", release="rel", server_name="host")
    assert len(chain) == 3

    assert chain[0].mechanism is not None
    assert chain[0].mechanism.exception_id == 0
    assert chain[0].mechanism.parent_id == 0

    assert chain[1].mechanism.exception_id == 1
    assert chain[1].mechanism.parent_id == 0

    assert chain[2].mechanism.exception_id == 2
    assert chain[2].mechanism.parent_id == 1


def test_default_mechanism_is_generic_handled():
    """Each entry gets a default ``type="generic"``, ``handled=True`` mechanism."""
    err = _raise_single()
    chain = capture(err, type_name="LeafError", release="rel", server_name="host")

    mech = chain[0].mechanism
    assert mech is not None
    assert mech.type == "generic"
    assert mech.handled is True
    assert mech.synthetic is False


def test_frames_are_populated_most_recent_first():
    """``traceback.StackSummary`` is oldest-first; capture must reverse to most-recent-first."""
    err = _raise_single()
    chain = capture(err, type_name="LeafError", release="rel", server_name="host")

    frames = chain[0].frames
    assert len(frames) > 0
    # The innermost frame (where the ``raise`` happened) is _raise_single.
    # Python's traceback walks oldest-first; we reverse on encode.
    assert frames[0].function == "_raise_single"


def test_empty_release_and_server_are_stored():
    """Empty release / server are stored verbatim — valid producer state."""
    err = _raise_single()
    chain = capture(err, type_name="LeafError", release="", server_name="")
    assert chain[0].release == ""
    assert chain[0].server_name == ""


def test_implicit_context_is_walked():
    """``__context__`` (implicit ``raise`` inside ``except``) is included in the chain."""
    try:
        try:
            raise LeafError("inner")
        except LeafError:
            raise WrapError("outer")  # noqa: B904 — intentional implicit context
    except WrapError as e:
        err = e

    chain = capture(err, type_name="WrapError", release="r", server_name="h")
    assert len(chain) == 2
    assert chain[0].message == "inner"
    assert chain[1].message == "outer"


def test_suppress_context_breaks_chain():
    """``raise ... from None`` sets ``__suppress_context__`` — chain stops at the suppressor."""
    try:
        try:
            raise LeafError("inner")
        except LeafError:
            raise WrapError("outer") from None
    except WrapError as e:
        err = e

    chain = capture(err, type_name="WrapError", release="r", server_name="h")
    assert len(chain) == 1
    assert chain[0].message == "outer"


def test_cause_wins_over_context():
    """PEP 3134: explicit ``__cause__`` takes precedence over implicit ``__context__``."""
    try:
        try:
            raise LeafError("real-inner")
        except LeafError as real_inner:
            try:
                raise LeafError("noise")  # noqa: B904 — sets __context__
            except LeafError:
                raise WrapError("outer") from real_inner
    except WrapError as e:
        err = e

    chain = capture(err, type_name="WrapError", release="r", server_name="h")
    assert len(chain) == 2
    assert chain[0].message == "real-inner"
    assert chain[1].message == "outer"


def test_capture_uses_unqualified_typename_for_inner():
    """Inner entries derive ``type`` from ``type(exc).__name__`` (unqualified)."""
    err = _raise_chain_two()
    chain = capture(err, type_name="WrapError", release="r", server_name="h")
    # Both classes live in this test module; we want bare class names.
    assert chain[0].type == "LeafError"
    assert chain[1].type == "WrapError"
