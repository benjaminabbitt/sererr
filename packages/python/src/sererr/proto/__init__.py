"""Protobuf adapters for sererr.

The generated ``*_pb2.py`` modules live under ``_gen/`` and are loaded
via :mod:`importlib` so they do not need to be installed on
``sys.path`` (which would otherwise shadow the system ``google.protobuf``
package via the ``_gen/google/`` directory the buf generator creates).

Two adapter functions are exposed:

- :func:`to_proto` — convert a plain :class:`sererr.types.CapturedError`
  to a wire-format ``sererr.v1.CapturedError`` proto message. The
  mechanism's ``data`` map is written with sorted keys so encoded bytes
  are deterministic across runs (matches the Rust reference, which uses
  ``BTreeMap``).
- :func:`from_proto` — round-trip a wire proto back to the plain type.
"""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from sererr.types import CapturedError, DebugInfo

_GEN_ROOT = Path(__file__).resolve().parent / "_gen"


def _load(module_name: str, relative_path: str):
    """Load a generated proto module without polluting ``sys.path``.

    The ``_gen`` directory deliberately contains ``google/`` and
    ``sererr/`` subdirectories matching the proto package layout; adding
    that directory to ``sys.path`` would shadow the real
    ``google.protobuf`` package the runtime needs. Loading via
    :class:`importlib.util.spec_from_file_location` registers the module
    under our private name (``_sererr_pb2`` / ``_debug_info_pb2``)
    instead.
    """
    if module_name in sys.modules:
        return sys.modules[module_name]
    spec = importlib.util.spec_from_file_location(module_name, _GEN_ROOT / relative_path)
    if spec is None or spec.loader is None:
        raise ImportError(f"cannot load generated proto module {module_name}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


sererr_pb2 = _load("sererr._gen_sererr_pb2", "sererr/sererr_pb2.py")
debug_info_pb2 = _load("sererr._gen_debug_info_pb2", "google/rpc/debug_info_pb2.py")


def to_proto(captured: "CapturedError"):
    """Convert a plain :class:`CapturedError` to the wire-format proto.

    Mirrors the ``From<sererr::CapturedError> for ProtoCapturedError``
    impl in the Rust reference (``packages/rust/sererr-proto``). The
    mechanism's ``data`` map is written in sorted-key order so the
    encoded bytes are deterministic — cross-language conformance tests
    byte-compare encoded fixtures.
    """
    proto = sererr_pb2.CapturedError(
        type=captured.type,
        message=captured.message,
        release=captured.release,
        server_name=captured.server_name,
    )
    for f in captured.frames:
        frame = proto.frames.add()
        frame.function = f.function
        frame.module = f.module
        frame.package = f.package
        frame.file = f.file
        frame.abs_path = f.abs_path
        frame.line = f.line
        frame.context_line = f.context_line
        for line in f.pre_context:
            frame.pre_context.append(line)
        for line in f.post_context:
            frame.post_context.append(line)
        frame.source_link = f.source_link
        frame.in_app = f.in_app
    if captured.mechanism is not None:
        m = captured.mechanism
        proto.mechanism.type = m.type
        proto.mechanism.description = m.description
        proto.mechanism.handled = m.handled
        proto.mechanism.synthetic = m.synthetic
        proto.mechanism.help_link = m.help_link
        proto.mechanism.source = m.source
        proto.mechanism.exception_id = m.exception_id
        proto.mechanism.parent_id = m.parent_id
        proto.mechanism.is_exception_group = m.is_exception_group
        # Sort keys: Python dict is insertion-ordered; the Rust reference
        # uses BTreeMap. Encoding with sorted keys yields byte-identical
        # output across implementations.
        for k in sorted(m.data):
            proto.mechanism.data[k] = m.data[k]
    return proto


def from_proto(proto) -> "CapturedError":
    """Convert a wire-format proto back to a plain :class:`CapturedError`."""
    from sererr.types import CapturedError, ExceptionMechanism, StackFrame

    frames = [
        StackFrame(
            function=f.function,
            module=f.module,
            package=f.package,
            file=f.file,
            abs_path=f.abs_path,
            line=f.line,
            context_line=f.context_line,
            pre_context=list(f.pre_context),
            post_context=list(f.post_context),
            source_link=f.source_link,
            in_app=f.in_app,
        )
        for f in proto.frames
    ]
    mechanism: ExceptionMechanism | None = None
    if proto.HasField("mechanism"):
        m = proto.mechanism
        mechanism = ExceptionMechanism(
            type=m.type,
            description=m.description,
            handled=m.handled,
            synthetic=m.synthetic,
            help_link=m.help_link,
            source=m.source,
            exception_id=m.exception_id,
            parent_id=m.parent_id,
            is_exception_group=m.is_exception_group,
            data=dict(m.data),
        )
    return CapturedError(
        type=proto.type,
        message=proto.message,
        frames=frames,
        mechanism=mechanism,
        release=proto.release,
        server_name=proto.server_name,
    )


def debug_info_to_proto(di: "DebugInfo"):
    """Convert a plain :class:`DebugInfo` to the ``google.rpc.DebugInfo`` proto."""
    return debug_info_pb2.DebugInfo(
        stack_entries=list(di.stack_entries),
        detail=di.detail,
    )


def encode(captured: "CapturedError") -> bytes:
    """Convert and serialize to deterministic, byte-identical wire bytes.

    Calls :func:`to_proto` then ``SerializeToString(deterministic=True)``.
    The deterministic flag makes the CPython protobuf runtime emit map
    entries in sorted-key order — matching the Rust reference
    (``BTreeMap``) so encoded fixtures byte-compare across languages.
    """
    return to_proto(captured).SerializeToString(deterministic=True)


__all__ = [
    "sererr_pb2",
    "debug_info_pb2",
    "to_proto",
    "from_proto",
    "debug_info_to_proto",
    "encode",
]
