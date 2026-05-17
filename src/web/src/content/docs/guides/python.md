---
title: Python
description: Capture errors with the sererr Python package.
---

The `sererr` package ships the proto-generated types and a `capture`
function.

```python
from sererr import capture

chain = capture(exc, release=RELEASE, server_name=SERVER_NAME)
```

## Idiosyncracies

- Chain walks via `__cause__` (explicit `raise X from Y`), falling back
  to `__context__` (implicit during exception handling). PEP 3134.
- `__suppress_context__` skips the chain when set; library honors it.
- `traceback.TracebackException.from_exception(exc).stack` is a
  `StackSummary`, **oldest-first** — the library reverses on encode.
- `type(exc).__name__` is unqualified by convention; use
  `f"{type(exc).__module__}.{type(exc).__name__}"` if you want fully-
  qualified names.

## Source bundling

Add to `pyproject.toml`:

```toml
[tool.hatchling.build.targets.wheel]
packages = ["src/your_producer"]

[tool.hatchling.build.targets.wheel.shared-data]
"src/your_producer" = "your_producer"
```

Access via `importlib.resources.files("your_producer") / "module.py"`.
The library's source-context helper reads from this on capture.

## Manual recipe

```python
import os
import traceback
from sererr import CapturedError, StackFrame, ExceptionMechanism

FRAMEWORK_PREFIXES = ("traceback", "asyncio", "concurrent")

def capture(exc: BaseException, release: str, server_name: str) -> list[CapturedError]:
    chain: list[CapturedError] = []
    current: BaseException | None = exc

    while current is not None:
        te = traceback.TracebackException.from_exception(current)
        frames: list[StackFrame] = []
        # te.stack is oldest-first → reverse to most-recent-first.
        for fs in reversed(te.stack):
            frames.append(StackFrame(
                function=fs.name,
                file=fs.filename or "",
                line=fs.lineno or 0,
                context_line=fs.line or "",
                in_app="site-packages" not in (fs.filename or "")
                       and not any((fs.filename or "").startswith(p) for p in FRAMEWORK_PREFIXES),
            ))
        chain.append(CapturedError(
            type=type(current).__name__,
            message=str(current),
            frames=frames,
            mechanism=ExceptionMechanism(type="generic", handled=True),
            release=release,
            server_name=server_name,
        ))
        # __cause__ wins over __context__ per PEP 3134.
        current = current.__cause__ or current.__context__

    chain.reverse()  # most-causal-first
    for i, entry in enumerate(chain):
        entry.mechanism.exception_id = i
        entry.mechanism.parent_id = i - 1 if i > 0 else 0
    return chain
```
