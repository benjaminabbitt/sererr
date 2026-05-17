"""Adapt a sererr capture into the :class:`DebugInfo` shape.

Mirrors the Rust reference implementation in
``packages/rust/sererr/src/lib.rs`` byte-for-byte. The output is
wire-compatible with ``google.rpc.DebugInfo`` from the gRPC error
model — tooling that only knows that shape can render captures
without understanding sererr's structure.
"""

from __future__ import annotations

from sererr.types import CapturedError, DebugInfo, StackFrame


def _format_frame_line(frame: StackFrame) -> str:
    """Render one frame as ``"  at <function> (<file>:<line>)"``.

    Variants:

    - file + line>0   → ``"  at <function> (<file>:<line>)"``
    - file only       → ``"  at <function> (<file>)"``
    - neither         → ``"  at <function>"``
    - empty function  → renders as ``"<unknown>"``
    """
    if not frame.file:
        location = ""
    elif frame.line > 0:
        location = f" ({frame.file}:{frame.line})"
    else:
        location = f" ({frame.file})"
    function = frame.function if frame.function else "<unknown>"
    return f"  at {function}{location}"


def to_debug_info(chain: list[CapturedError]) -> DebugInfo:
    """Render a sererr chain into a :class:`DebugInfo`.

    ``stack_entries`` gets one line per frame, formatted as
    ``"  at <function> (<file>:<line>)"``, most-recent-first within each
    chain entry, separated by ``"Caused by: <type>: <message>"`` lines
    across the chain.

    ``detail`` joins ``"<type>: <message>"`` across the chain with
    ``"\\nCaused by: "`` separators.

    Iteration order is **outermost-first** for display (reverse of the
    chain's most-causal-first storage order).
    """
    if not chain:
        return DebugInfo()

    stack_entries: list[str] = []
    detail_parts: list[str] = []

    # chain is most-causal-first → reverse so outermost is first.
    for idx, entry in enumerate(reversed(chain)):
        header = f"{entry.type}: {entry.message}"
        if idx == 0:
            detail_parts.append(header)
            stack_entries.append(header)
        else:
            detail_parts.append(f"Caused by: {header}")
            stack_entries.append(f"Caused by: {header}")
        for frame in entry.frames:
            stack_entries.append(_format_frame_line(frame))

    return DebugInfo(stack_entries=stack_entries, detail="\n".join(detail_parts))
