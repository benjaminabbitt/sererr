"""Tests for the plain-type dataclasses.

These pin the proto-compatible shape of :class:`StackFrame`,
:class:`ExceptionMechanism`, :class:`CapturedError`, and
:class:`DebugInfo`. Every field must have a sensible default so
zero-value construction (mirroring proto3 ``default()``) works.
"""

from sererr.types import (
    CapturedError,
    DebugInfo,
    ExceptionMechanism,
    StackFrame,
)


def test_stack_frame_zero_value_construction():
    """Zero-arg StackFrame works and every field is proto3-default."""
    f = StackFrame()
    assert f.function == ""
    assert f.module == ""
    assert f.package == ""
    assert f.file == ""
    assert f.abs_path == ""
    assert f.line == 0
    assert f.context_line == ""
    assert f.pre_context == []
    assert f.post_context == []
    assert f.source_link == ""
    assert f.in_app is False


def test_stack_frame_kwargs():
    """Pin the proto-named keyword surface, including the ``type`` field name on others."""
    f = StackFrame(function="f", file="x.py", line=12, in_app=True)
    assert f.function == "f"
    assert f.file == "x.py"
    assert f.line == 12
    assert f.in_app is True


def test_exception_mechanism_zero_value():
    """ExceptionMechanism defaults match proto3 zero values, including empty data dict."""
    m = ExceptionMechanism()
    assert m.type == ""
    assert m.description == ""
    assert m.handled is False
    assert m.synthetic is False
    assert m.help_link == ""
    assert m.source == ""
    assert m.exception_id == 0
    assert m.parent_id == 0
    assert m.is_exception_group is False
    assert m.data == {}


def test_exception_mechanism_type_kwarg():
    """The reserved-like ``type`` kwarg must be assignable (mirrors proto field name)."""
    m = ExceptionMechanism(type="generic", handled=True)
    assert m.type == "generic"
    assert m.handled is True


def test_captured_error_zero_value():
    """CapturedError defaults: zero strings, empty frames, no mechanism."""
    e = CapturedError()
    assert e.type == ""
    assert e.message == ""
    assert e.frames == []
    assert e.mechanism is None
    assert e.release == ""
    assert e.server_name == ""


def test_captured_error_with_mechanism():
    """Mechanism is set when explicitly supplied."""
    e = CapturedError(type="X", message="m", mechanism=ExceptionMechanism(type="generic"))
    assert e.type == "X"
    assert e.message == "m"
    assert e.mechanism is not None
    assert e.mechanism.type == "generic"


def test_debug_info_zero_value():
    """DebugInfo defaults: empty list, empty string."""
    di = DebugInfo()
    assert di.stack_entries == []
    assert di.detail == ""
