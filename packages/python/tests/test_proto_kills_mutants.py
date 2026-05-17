"""Behavior-pinning tests for :mod:`sererr.proto` that kill surviving
mutants from the round-trip suite.

Each test names the mutant id (per ``mutmut show <id>``) it kills.
"""

from __future__ import annotations

import sys
import pytest

import sererr.proto as proto_mod
from sererr.proto import (
    _GEN_ROOT,
    _load,
    debug_info_pb2,
    debug_info_to_proto,
    from_proto,
    sererr_pb2,
)
from sererr.types import DebugInfo


# ---------- _load() behavior ------------------------------------------


def test_load_raises_importerror_when_spec_is_none(tmp_path):
    """Mutant 147 (``spec is None or spec.loader is None`` → ``and``)
    and 148 (error message text → ``"XX...XX"``):
    ``_load`` must raise ``ImportError`` with the exact message format
    when ``spec_from_file_location`` returns ``None`` (unknown suffix).

    With the ``and`` mutant: ``None and X`` short-circuits to None
    (falsy) → no raise → next call raises ``AttributeError`` instead.

    With the message-text mutant: the assertion on the message content
    fails.

    Kills mutants 147, 148.
    """
    # Create a file with a suffix Python's importer doesn't recognise so
    # ``spec_from_file_location`` returns None.
    bogus = tmp_path / "not_a_module.xyz"
    bogus.write_text("nope")

    with pytest.raises(ImportError) as excinfo:
        _load("sererr._test_bogus_module", str(bogus))

    # The exact prefix from the source must be preserved.
    assert "cannot load generated proto module" in str(excinfo.value)
    # Mutant 148 wraps in "XX...XX".
    assert "XX" not in str(excinfo.value)


def test_load_returns_cached_module_on_repeat_call():
    """Mutant 150 (``sys.modules[module_name] = module`` → ``= None``):
    after the first successful load, repeated calls must return the
    SAME non-None module via the ``sys.modules`` cache fast-path.

    With the mutant, the cache entry is ``None``, so the early-return
    branch yields ``None``.

    Kills mutant 150.
    """
    # The proto package is already imported, so sererr_pb2 is loaded.
    # Calling _load again with the same name must return the same module.
    again = _load("sererr._gen_sererr_pb2", "sererr/sererr_pb2.py")
    assert again is not None
    assert again is sererr_pb2


def test_sys_modules_has_canonical_load_names():
    """Mutants 151 (``"sererr._gen_sererr_pb2"`` → ``"XX...XX"``) and
    154 (``"sererr._gen_debug_info_pb2"`` → ``"XX...XX"``): the
    canonical private module names must be registered in ``sys.modules``.

    Kills mutants 151, 154.
    """
    assert "sererr._gen_sererr_pb2" in sys.modules
    assert "sererr._gen_debug_info_pb2" in sys.modules
    assert sys.modules["sererr._gen_sererr_pb2"] is sererr_pb2
    assert sys.modules["sererr._gen_debug_info_pb2"] is debug_info_pb2


def test_debug_info_pb2_is_loaded_module_not_none():
    """Mutant 156 (``debug_info_pb2 = None``): the module-level binding
    must be the real generated proto module.

    Kills mutant 156.
    """
    assert debug_info_pb2 is not None
    # And it must expose the ``DebugInfo`` message class.
    assert hasattr(debug_info_pb2, "DebugInfo")


def test_debug_info_to_proto_round_trips_through_real_module():
    """``debug_info_to_proto`` must produce a real proto message — not
    ``None``. Reinforces mutant 156 (``debug_info_pb2 = None`` would
    cause an ``AttributeError`` here).
    """
    di = DebugInfo(stack_entries=["a", "b"], detail="d")
    p = debug_info_to_proto(di)
    assert list(p.stack_entries) == ["a", "b"]
    assert p.detail == "d"


# ---------- from_proto: mechanism default = None ----------------------


def test_from_proto_with_no_mechanism_yields_mechanism_is_none():
    """Mutant 182 (``mechanism: ExceptionMechanism | None = None`` →
    ``= ""``): when the proto has no mechanism field, the decoded
    :class:`CapturedError` must have ``mechanism is None`` — never a
    sentinel string.

    Kills mutant 182.
    """
    p = sererr_pb2.CapturedError(type="T", message="m")
    # No mechanism set on the proto.
    assert not p.HasField("mechanism")

    decoded = from_proto(p)
    # ``is None`` (NOT ``== None``) — distinguishes None from "" since
    # `"" == None` is False but `not "" ` is True; the strict identity
    # check distinguishes the default-value mutation.
    assert decoded.mechanism is None


def test_from_proto_with_mechanism_yields_real_mechanism():
    """Companion to the no-mechanism test: when the proto HAS a
    mechanism, the decoded mechanism is the populated
    :class:`ExceptionMechanism`, not None.

    Pins the ``if proto.HasField("mechanism"):`` true branch.
    """
    p = sererr_pb2.CapturedError(type="T", message="m")
    p.mechanism.type = "generic"
    p.mechanism.handled = True
    assert p.HasField("mechanism")

    decoded = from_proto(p)
    assert decoded.mechanism is not None
    assert decoded.mechanism.type == "generic"
    assert decoded.mechanism.handled is True
