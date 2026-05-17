"""sererr — Sentry-compat structured stack-trace + error-chain capture.

This package provides:

- Plain dataclasses (:mod:`sererr.types`) mirroring the ``sererr.v1``
  proto schema, with no protobuf dependency.
- :func:`capture` — walk a Python exception chain
  (``__cause__`` / ``__context__``) into a list of
  :class:`~sererr.types.CapturedError`.
- :func:`to_debug_info` — adapt a capture into the
  ``google.rpc.DebugInfo``-compatible :class:`~sererr.types.DebugInfo`
  shape for gRPC error-model interop.
- :class:`~sererr.source.SourceProvider` and
  :func:`~sererr.source.populate_source_context` for capture-time
  source-context population.
- :mod:`sererr.proto` — proto adapters (``to_proto`` / ``from_proto``)
  for serializing captures over the wire.
"""

from __future__ import annotations

from sererr._capture import capture
from sererr._debug_info import to_debug_info
from sererr.source import SourceProvider, populate_source_context
from sererr.types import (
    CapturedError,
    DebugInfo,
    ExceptionMechanism,
    StackFrame,
)

__all__ = [
    "CapturedError",
    "DebugInfo",
    "ExceptionMechanism",
    "SourceProvider",
    "StackFrame",
    "capture",
    "populate_source_context",
    "to_debug_info",
]
