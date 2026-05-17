"""Plain Python dataclasses mirroring the ``sererr.v1`` proto schema.

These types have **no** protobuf dependency — use them directly when you
just want to gather diagnostics in-process (log it, hash it, attach it
to a tracing span). The proto adapter in :mod:`sererr.proto` converts
to and from the wire format when you need to serialize.

Every field has a sensible default so zero-value construction works
(matching proto3 ``default()`` semantics — "missing" and "zero" are the
same value).
"""

from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class StackFrame:
    """A single frame in a captured stack trace.

    Field-compatible with ``sererr.v1.StackFrame``. Proto3 zero-value =
    unknown semantics: an empty string or 0 means "not set", and is not
    written to the wire.
    """

    function: str = ""
    module: str = ""
    package: str = ""
    file: str = ""
    abs_path: str = ""
    line: int = 0
    context_line: str = ""
    pre_context: list[str] = field(default_factory=list)
    post_context: list[str] = field(default_factory=list)
    source_link: str = ""
    in_app: bool = False


@dataclass
class ExceptionMechanism:
    """Describes how an exception was captured / handled.

    Mirrors ``sererr.v1.ExceptionMechanism``. The ``data`` map is a plain
    :class:`dict` for ergonomic use; the proto adapter sorts the keys
    before encoding so the wire format is byte-deterministic (matches
    the Rust reference's ``BTreeMap``).
    """

    type: str = ""
    description: str = ""
    handled: bool = False
    synthetic: bool = False
    help_link: str = ""
    source: str = ""
    exception_id: int = 0
    parent_id: int = 0
    is_exception_group: bool = False
    data: dict[str, str] = field(default_factory=dict)


@dataclass
class CapturedError:
    """A captured error.

    Field-compatible with ``sererr.v1.CapturedError``. Cause chains are
    represented as a ``list[CapturedError]`` (most-causal-first; the
    originating caught error is the LAST element). Chain position is
    identified by ``mechanism.exception_id`` / ``mechanism.parent_id``.
    """

    type: str = ""
    message: str = ""
    frames: list[StackFrame] = field(default_factory=list)
    mechanism: ExceptionMechanism | None = None
    release: str = ""
    server_name: str = ""


@dataclass
class DebugInfo:
    """Adapter shape for the gRPC error model (``google.rpc.DebugInfo``).

    Wire-compatible with the upstream message. Use
    :func:`sererr.to_debug_info` to render a capture into this shape.
    """

    stack_entries: list[str] = field(default_factory=list)
    detail: str = ""
