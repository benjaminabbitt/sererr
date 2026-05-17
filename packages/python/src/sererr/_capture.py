"""Walk a Python exception chain into a list of :class:`CapturedError`.

Python's exception-chain semantics (PEP 3134):

- ``__cause__`` is set by ``raise X from Y`` (explicit).
- ``__context__`` is set automatically when ``raise`` happens inside an
  ``except`` handler (implicit).
- ``__suppress_context__`` is set by ``raise X from None`` — meaning the
  user explicitly silenced the implicit context.

The standard precedence (matching how ``traceback.format_exception``
walks the chain) is: prefer ``__cause__``; otherwise use
``__context__`` unless ``__suppress_context__`` is set.

Frame ordering: :class:`traceback.StackSummary` is oldest-first (the
outermost caller first, the actual ``raise`` site last). The sererr
contract requires **most-recent-first**, so we reverse on encode.
"""

from __future__ import annotations

import traceback

from sererr.types import CapturedError, ExceptionMechanism, StackFrame


def _next_in_chain(exc: BaseException) -> BaseException | None:
    """Walk one step deeper into the cause/context chain.

    Returns ``None`` when the chain terminates. ``__cause__`` wins over
    ``__context__``; ``__suppress_context__`` (set by
    ``raise ... from None``) terminates the chain entirely if there is
    no explicit cause.
    """
    if exc.__cause__ is not None:
        return exc.__cause__
    if exc.__suppress_context__:
        return None
    return exc.__context__


def _frames_for(exc: BaseException) -> list[StackFrame]:
    """Extract frames from an exception's traceback as most-recent-first
    :class:`StackFrame` instances.

    ``traceback.TracebackException.from_exception(exc).stack`` returns a
    :class:`traceback.StackSummary` ordered oldest-first; the sererr
    contract is most-recent-first, so we reverse.
    """
    tbe = traceback.TracebackException.from_exception(exc)
    frames: list[StackFrame] = []
    # StackSummary is iterable of FrameSummary; oldest-first.
    for fs in tbe.stack:
        frames.append(
            StackFrame(
                function=fs.name or "",
                file=fs.filename or "",
                line=fs.lineno or 0,
                context_line=(fs.line or "") if fs.line is not None else "",
            )
        )
    frames.reverse()
    return frames


def capture(
    exc: BaseException,
    type_name: str,
    release: str,
    server_name: str,
) -> list[CapturedError]:
    """Walk a Python exception chain into a sererr capture.

    The returned list is **most-causal-first** — the originating caught
    error is the last element. Each entry carries its own frames
    (captured from that exception's own ``__traceback__``).

    Mechanism IDs are stamped sequentially: ``exception_id = index``,
    ``parent_id = max(0, index - 1)``. The root cause has
    ``parent_id == 0`` (itself).

    ``type_name`` labels the *originating caught* error (the LAST entry
    in the returned chain). Inner entries derive their type from
    ``type(e).__name__`` since the live Python exception object carries
    its own type information.
    """
    # Walk outward → in: start at the caught exception, follow
    # __cause__/__context__ inward. Collect outer-first, then reverse so
    # the originating caught error is LAST (most-causal-first order).
    chain_outer_first: list[CapturedError] = []
    current: BaseException | None = exc
    is_outermost = True
    while current is not None:
        # Outermost entry uses caller-supplied type_name (Rust convention);
        # deeper entries use the live exception type.
        t = type_name if is_outermost else type(current).__name__
        chain_outer_first.append(
            CapturedError(
                type=t,
                message=str(current),
                frames=_frames_for(current),
                mechanism=ExceptionMechanism(type="generic", handled=True),
                release=release,
                server_name=server_name,
            )
        )
        is_outermost = False
        current = _next_in_chain(current)

    # Reverse to most-causal-first; the originating caught error is last.
    chain = list(reversed(chain_outer_first))

    for i, entry in enumerate(chain):
        if entry.mechanism is not None:
            entry.mechanism.exception_id = i
            entry.mechanism.parent_id = 0 if i == 0 else i - 1
    return chain
