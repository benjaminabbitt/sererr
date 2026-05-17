"""Tests for the :class:`SourceProvider` protocol and
:func:`populate_source_context` helper.

Mirrors ``packages/rust/sererr/tests/source_provider_test.rs``.
"""

from sererr.types import StackFrame
from sererr.source import SourceProvider, populate_source_context


class MapProvider:
    """In-memory provider — maps file paths to source content."""

    def __init__(self, mapping: dict[str, str]):
        self.mapping = mapping

    def get_source(self, file: str) -> str | None:
        return self.mapping.get(file)


SAMPLE_SOURCE = """fn one() {
    1
}

fn two() {
    let x = 2;
    return x;
}

fn three() {
    let y = three_inner();
    y
}
"""


def test_populates_context_around_line():
    """Frame at known line: context_line equals that line, pre/post capture surrounding."""
    provider = MapProvider({"src/sample.rs": SAMPLE_SOURCE})
    frame = StackFrame(file="src/sample.rs", line=6)  # "    let x = 2;"

    populate_source_context(frame, provider, 2)

    assert frame.context_line == "    let x = 2;"
    assert frame.pre_context == ["", "fn two() {"]
    assert frame.post_context == ["    return x;", "}"]


def test_missing_file_is_a_noop():
    """Provider returns None → frame is left unchanged."""
    provider = MapProvider({})
    frame = StackFrame(file="src/missing.rs", line=5, context_line="preserved")

    populate_source_context(frame, provider, 2)

    assert frame.context_line == "preserved"
    assert frame.pre_context == []
    assert frame.post_context == []


def test_line_zero_is_a_noop():
    """``line == 0`` (unknown) → no work, no panic."""
    provider = MapProvider({"src/sample.rs": SAMPLE_SOURCE})
    frame = StackFrame(file="src/sample.rs", line=0)

    populate_source_context(frame, provider, 2)

    assert frame.context_line == ""
    assert frame.pre_context == []
    assert frame.post_context == []


def test_line_past_eof_is_a_noop():
    """``line`` past EOF leaves the frame untouched (no panic, no garbage)."""
    provider = MapProvider({"src/sample.rs": SAMPLE_SOURCE})
    frame = StackFrame(file="src/sample.rs", line=999_999)

    populate_source_context(frame, provider, 2)

    assert frame.context_line == ""


def test_context_clamps_at_file_boundaries():
    """Near top: pre_context is empty (clamped); near EOF: post is short (clamped)."""
    provider = MapProvider({"src/sample.rs": SAMPLE_SOURCE})
    frame = StackFrame(file="src/sample.rs", line=1)  # "fn one() {"

    populate_source_context(frame, provider, 5)

    assert frame.context_line == "fn one() {"
    assert frame.pre_context == []  # no lines before line 1
    assert len(frame.post_context) <= 5


def test_source_provider_protocol_is_duck_typed():
    """:class:`SourceProvider` is a :class:`typing.Protocol` — any object with
    ``get_source`` works.
    """

    class AdHoc:
        def get_source(self, file):
            return "hello\nworld\n" if file == "x" else None

    provider = AdHoc()
    assert isinstance(provider, SourceProvider)  # runtime-checkable
