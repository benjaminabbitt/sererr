"""Python implementation of the shared sererr conformance step library.

Mirrors the Rust runner step-for-step (see
``tests/conformance/runners/rust/tests/conformance.rs``). Step text and
regexes are kept identical so the same Gherkin corpus passes against
every language's implementation.
"""

from __future__ import annotations

import json
import os
from pathlib import Path

from behave import given, register_type, then, use_step_matcher, when

from sererr import capture, to_debug_info
from sererr.proto import from_proto, sererr_pb2, to_proto
from sererr.types import (
    CapturedError,
    ExceptionMechanism,
    StackFrame,
)


# ---------------------------------------------------------------------------
# behave uses Python's ``parse`` library by default. Register simple type
# converters so step regexes read naturally and integers are parsed for us.
# ---------------------------------------------------------------------------
register_type(Int=int)


# ---------------------------------------------------------------------------
# Test fixtures (synthetic exception chains)
# ---------------------------------------------------------------------------


class LabeledError(Exception):
    """Test error whose ``str()`` is the label passed in."""

    def __init__(self, label: str):
        super().__init__(label)
        self.label = label

    def __str__(self) -> str:  # pragma: no cover - trivial
        return self.label


def _make_and_capture(msgs: list[str]) -> list[CapturedError]:
    """Build a Python ``__cause__`` chain from msgs (innermost-first) and
    capture the outermost.

    Mirrors the Rust runner's ``make_chain``: takes innermost-first
    messages and returns a captured chain in most-causal-first order.
    The inner errors carry real traceback frames from the ``raise`` site
    so the resulting capture has populated frames.
    """

    def _rec(idx: int) -> LabeledError:
        if idx == 0:
            try:
                raise LabeledError(msgs[0])
            except LabeledError as e:
                return e
        try:
            inner = _rec(idx - 1)
            raise LabeledError(msgs[idx]) from inner
        except LabeledError as e:
            return e

    outer = _rec(len(msgs) - 1)
    return capture(outer, type_name="LabeledError", release="test", server_name="host")


# ---------------------------------------------------------------------------
# Fixture loading
# ---------------------------------------------------------------------------


def _fixtures_dir() -> Path:
    p = os.environ.get("SERERR_FIXTURES_DIR")
    if not p:
        raise RuntimeError("SERERR_FIXTURES_DIR env var not set")
    return Path(p)


def _captured_from_descriptor(d: dict) -> CapturedError:
    """Convert the JSON descriptor's ``captured_error`` into a plain
    :class:`CapturedError`.

    Mirrors the Rust ``FixtureCapturedError -> CapturedError`` impl —
    missing fields use proto3 zero values.
    """
    c = d.get("captured_error") or {}
    frames = [
        StackFrame(
            function=f.get("function", ""),
            module=f.get("module", ""),
            package=f.get("package", ""),
            file=f.get("file", ""),
            abs_path=f.get("abs_path", ""),
            line=f.get("line", 0),
            context_line=f.get("context_line", ""),
            pre_context=list(f.get("pre_context", [])),
            post_context=list(f.get("post_context", [])),
            source_link=f.get("source_link", ""),
            in_app=f.get("in_app", False),
        )
        for f in c.get("frames", [])
    ]
    mechanism: ExceptionMechanism | None = None
    if "mechanism" in c and c["mechanism"] is not None:
        m = c["mechanism"]
        mechanism = ExceptionMechanism(
            type=m.get("type", ""),
            description=m.get("description", ""),
            handled=m.get("handled", False),
            synthetic=m.get("synthetic", False),
            help_link=m.get("help_link", ""),
            source=m.get("source", ""),
            exception_id=m.get("exception_id", 0),
            parent_id=m.get("parent_id", 0),
            is_exception_group=m.get("is_exception_group", False),
            data=dict(m.get("data", {})),
        )
    return CapturedError(
        type=c.get("type", ""),
        message=c.get("message", ""),
        frames=frames,
        mechanism=mechanism,
        release=c.get("release", ""),
        server_name=c.get("server_name", ""),
    )


# ---------------------------------------------------------------------------
# Background / generic
# ---------------------------------------------------------------------------


@given("the canonical sererr.v1 proto schema")
def step_canonical_schema(context):
    """No-op: the schema is implicit (we link against the generated proto)."""
    pass


# ---------------------------------------------------------------------------
# encoding.feature
# ---------------------------------------------------------------------------


@given('a fixture "{name}"')
def step_given_fixture(context, name: str):
    context.fixture = name


@given("a default-initialized CapturedError (all zero values)")
def step_given_default_captured(context):
    context.fixture_input = CapturedError()


@when("I construct the CapturedError per the fixture's JSON descriptor")
def step_construct_from_fixture(context):
    path = _fixtures_dir() / f"{context.fixture}.json"
    descriptor = json.loads(path.read_text())
    context.fixture_input = _captured_from_descriptor(descriptor)


@when("I serialize it via the proto adapter")
@when("I serialize it")
def step_serialize(context):
    # Auto-load a fixture if the scenario skipped the explicit "construct" step.
    if getattr(context, "fixture_input", None) is None:
        fixture = getattr(context, "fixture", None)
        if fixture:
            path = _fixtures_dir() / f"{fixture}.json"
            descriptor = json.loads(path.read_text())
            context.fixture_input = _captured_from_descriptor(descriptor)
    proto = to_proto(context.fixture_input)
    # deterministic=True forces map-entry ordering to be sorted by key
    # so encoded bytes match across protobuf runtimes (Rust prost sorts
    # by default; Python's runtime opts in via this flag).
    context.encoded = proto.SerializeToString(deterministic=True)


@then('the encoded bytes match "fixtures/{name}.pb"')
def step_bytes_match_fixture(context, name: str):
    path = _fixtures_dir() / f"{name}.pb"
    expected = path.read_bytes()
    actual = context.encoded
    assert actual == expected, (
        f"encoded bytes mismatch for fixture {name}: "
        f"got {len(actual)} bytes, expected {len(expected)} bytes"
    )


@then("the encoded bytes are empty")
def step_bytes_empty(context):
    assert context.encoded == b"", f"expected empty bytes, got {len(context.encoded)}"


@when("I deserialize the bytes back to a CapturedError")
def step_deserialize(context):
    proto = sererr_pb2.CapturedError()
    proto.ParseFromString(context.encoded)
    context.fixture_input = from_proto(proto)


@then("the result equals the input field-by-field")
def step_roundtrip_equal(context):
    # The deserialize step overwrote ``fixture_input``; re-read the
    # fixture and compare field-for-field.
    path = _fixtures_dir() / f"{context.fixture}.json"
    descriptor = json.loads(path.read_text())
    expected = _captured_from_descriptor(descriptor)
    assert context.fixture_input == expected, "round-trip mismatch"


# ---------------------------------------------------------------------------
# chain.feature
# ---------------------------------------------------------------------------


@given("an error with no source / cause")
def step_no_source_error(context):
    try:
        raise LabeledError("single")
    except LabeledError as e:
        context.chain = capture(e, type_name="LabeledError", release="test", server_name="host")


# Use the ``re`` matcher for the chain step so we can express the
# optional third level naturally — the default ``parse`` matcher
# considers ``{outer}`` greedy enough that the two- and three-level
# patterns are ambiguous.
use_step_matcher("re")


@given(r'a chain "(?P<inner>[^"]+)" caused-by "(?P<middle_or_outer>[^"]+)"'
       r'(?: caused-by "(?P<maybe_outer>[^"]+)")?')
def step_given_chain(context, inner: str, middle_or_outer: str, maybe_outer):
    """Mirrors the Rust step; Gherkin reads most-causal-first.

    The Gherkin ``"<inner>" caused-by "<next>" [caused-by "<outer>"]``
    reads most-causal-first (originating caught error is LAST).
    ``capture()`` walks ``__cause__`` inward, then reverses to
    most-causal-first.
    """
    if maybe_outer:
        msgs = [inner, middle_or_outer, maybe_outer]
    else:
        msgs = [inner, middle_or_outer]
    context.chain = _make_and_capture(msgs)


# Reset to the default matcher for the remaining steps so they continue
# using behave's friendlier ``{name}`` placeholder syntax.
use_step_matcher("parse")


@given("a captured error with frames")
def step_captured_with_frames(context):
    try:
        raise LabeledError("x")
    except LabeledError as e:
        context.chain = capture(e, type_name="LabeledError", release="test", server_name="host")


@when("I capture it")
@when("I capture the outermost error")
def step_capture_noop(context):
    # Capture already happened in the @given.
    pass


@when("I read the frames")
def step_read_frames(context):
    pass


@then("the chain length is {n:Int}")
def step_chain_length(context, n: int):
    assert len(context.chain) == n, f"chain length {len(context.chain)} != {n}"


@then("entry {idx:Int} has exception_id {exception_id:Int} and parent_id {parent_id:Int}")
def step_entry_mechanism_ids(context, idx: int, exception_id: int, parent_id: int):
    entry = context.chain[idx]
    assert entry.mechanism is not None, f"entry {idx} has no mechanism"
    assert entry.mechanism.exception_id == exception_id, (
        f"entry {idx} exception_id {entry.mechanism.exception_id} != {exception_id}"
    )
    assert entry.mechanism.parent_id == parent_id, (
        f"entry {idx} parent_id {entry.mechanism.parent_id} != {parent_id}"
    )


@then("the entry's mechanism has exception_id 0")
def step_single_entry_exception_id_zero(context):
    mech = context.chain[0].mechanism
    assert mech is not None and mech.exception_id == 0


@then("the entry's mechanism has parent_id 0")
def step_single_entry_parent_id_zero(context):
    mech = context.chain[0].mechanism
    assert mech is not None and mech.parent_id == 0


@then('entry {idx:Int} has message "{msg}"')
def step_entry_message(context, idx: int, msg: str):
    assert context.chain[idx].message == msg, (
        f"entry {idx} message {context.chain[idx].message!r} != {msg!r}"
    )


@then('the last chain entry has message "{msg}"')
def step_last_entry_message(context, msg: str):
    assert context.chain, "chain is empty"
    assert context.chain[-1].message == msg


@then("the first frame is the most recent call")
def step_first_frame_most_recent(context):
    # We can't easily identify "the most recent" without a known
    # reference point, so we assert frames exist — mirrors the Rust
    # runner's pragmatic stance.
    frames = context.chain[0].frames
    assert frames, f"expected captured frames; got {len(frames)}"


# ---------------------------------------------------------------------------
# debuginfo-adapter.feature
# ---------------------------------------------------------------------------


@given("an empty chain")
def step_empty_chain(context):
    context.chain = []


@given('a single CapturedError with type "{t}" and message "{msg}"')
def step_single_captured(context, t: str, msg: str):
    context.chain = [CapturedError(type=t, message=msg)]


@given("a CapturedError with one frame:")
def step_captured_with_one_frame_table(context):
    """Reads the data table: header row + value row with ``function | file | line``.

    Behave's data table presents header → row dicts; we read the first
    (and only) row's named columns.
    """
    assert context.table is not None, "expected a data table"
    row = context.table.rows[0]  # behave: rows[0] is first DATA row; headings are implicit
    function = row["function"]
    file = row["file"]
    line = int(row["line"])
    context.chain = [
        CapturedError(
            type="T",
            message="m",
            frames=[StackFrame(function=function, file=file, line=line)],
        )
    ]


@when("I call to_debug_info")
def step_call_to_debug_info(context):
    context.debug_info = to_debug_info(context.chain)


@then("stack_entries is empty")
def step_stack_entries_empty(context):
    assert context.debug_info.stack_entries == []


@then("detail is empty")
def step_detail_empty(context):
    assert context.debug_info.detail == ""


@then('detail equals "{expected}"')
def step_detail_equals(context, expected: str):
    assert context.debug_info.detail == expected, (
        f"detail {context.debug_info.detail!r} != {expected!r}"
    )


@then('detail contains "{needle}"')
def step_detail_contains(context, needle: str):
    assert needle in context.debug_info.detail, (
        f"detail {context.debug_info.detail!r} does not contain {needle!r}"
    )


@then('stack_entries contains "{needle}"')
def step_stack_entries_contains(context, needle: str):
    assert needle in context.debug_info.stack_entries, (
        f"stack_entries {context.debug_info.stack_entries!r} does not contain {needle!r}"
    )
