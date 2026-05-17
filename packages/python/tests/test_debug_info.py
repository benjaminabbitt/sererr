"""Tests for the ``to_debug_info`` adapter.

Mirrors the contract in the Rust reference (``packages/rust/sererr/src/lib.rs``)
and the cucumber feature ``tests/conformance/features/debuginfo-adapter.feature``.
"""

from sererr import to_debug_info
from sererr.types import CapturedError, StackFrame


def test_empty_chain_produces_empty_debug_info():
    """Empty input yields an empty DebugInfo (no panic, no stray header)."""
    di = to_debug_info([])
    assert di.stack_entries == []
    assert di.detail == ""


def test_single_error_detail_is_type_colon_message():
    """One entry → ``"<type>: <message>"`` is the entire detail."""
    chain = [CapturedError(type="MyError", message="oops")]
    di = to_debug_info(chain)
    assert di.detail == "MyError: oops"


def test_chain_joins_with_caused_by():
    """Chain renders outer-first, with ``Caused by:`` separators on subsequent entries."""
    chain = [
        CapturedError(type="Inner", message="inner"),  # most-causal
        CapturedError(type="Outer", message="outer"),  # caught
    ]
    di = to_debug_info(chain)
    # Outermost is rendered first (no "Caused by"), then the inner with "Caused by".
    assert di.detail == "Outer: outer\nCaused by: Inner: inner"


def test_frame_with_file_and_line_formats_canonically():
    """Frames with file+line render ``"  at <function> (<file>:<line>)"``."""
    chain = [
        CapturedError(
            type="T",
            message="m",
            frames=[StackFrame(function="doit", file="src/x.rs", line=12)],
        )
    ]
    di = to_debug_info(chain)
    assert "  at doit (src/x.rs:12)" in di.stack_entries


def test_frame_with_file_only_omits_line():
    """When line is 0, render ``"  at <function> (<file>)"`` (no ``:0``)."""
    chain = [
        CapturedError(
            type="T",
            message="m",
            frames=[StackFrame(function="doit", file="x.py", line=0)],
        )
    ]
    di = to_debug_info(chain)
    assert "  at doit (x.py)" in di.stack_entries


def test_frame_with_neither_file_nor_line_renders_function_only():
    """No file, no line → ``"  at <function>"``."""
    chain = [
        CapturedError(
            type="T",
            message="m",
            frames=[StackFrame(function="doit")],
        )
    ]
    di = to_debug_info(chain)
    assert "  at doit" in di.stack_entries


def test_unknown_function_uses_placeholder():
    """Empty function name renders as ``<unknown>`` (matches Rust adapter)."""
    chain = [
        CapturedError(
            type="T",
            message="m",
            frames=[StackFrame(function="", file="x.py", line=3)],
        )
    ]
    di = to_debug_info(chain)
    assert "  at <unknown> (x.py:3)" in di.stack_entries
