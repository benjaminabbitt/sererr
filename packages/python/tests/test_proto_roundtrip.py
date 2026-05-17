"""Byte-equivalence and round-trip tests for the proto adapter.

The committed ``.pb`` fixtures in ``tests/conformance/fixtures`` are the
cross-language spec: every implementation's encoder must produce those
exact bytes for the matching ``.json`` descriptor.

These tests also verify that ``to_proto`` → encode → decode → ``from_proto``
round-trips the plain types losslessly.
"""

from __future__ import annotations

import json
import subprocess
from pathlib import Path

import pytest

from sererr.proto import encode, from_proto, to_proto
from sererr.types import (
    CapturedError,
    ExceptionMechanism,
    StackFrame,
)


FIXTURES = ["0001-simple", "0002-empty-chain", "0003-three-deep", "0004-source-context", "0005-mechanism-data"]


def _repo_root() -> Path:
    out = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True
    )
    return Path(out.stdout.strip())


def _fixtures_dir() -> Path:
    return _repo_root() / "tests" / "conformance" / "fixtures"


def _captured_from_json(d: dict) -> CapturedError:
    c = d["captured_error"]
    frames = [
        StackFrame(
            function=f.get("function", ""),
            module=f.get("module", ""),
            package=f.get("package", ""),
            file=f.get("file", ""),
            abs_path=f.get("abs_path", ""),
            line=f.get("line", 0),
            context_line=f.get("context_line", ""),
            pre_context=list(f.get("pre_context", [])),
            post_context=list(f.get("post_context", [])),
            source_link=f.get("source_link", ""),
            in_app=f.get("in_app", False),
        )
        for f in c.get("frames", [])
    ]
    mech: ExceptionMechanism | None = None
    if "mechanism" in c and c["mechanism"] is not None:
        m = c["mechanism"]
        mech = ExceptionMechanism(
            type=m.get("type", ""),
            description=m.get("description", ""),
            handled=m.get("handled", False),
            synthetic=m.get("synthetic", False),
            help_link=m.get("help_link", ""),
            source=m.get("source", ""),
            exception_id=m.get("exception_id", 0),
            parent_id=m.get("parent_id", 0),
            is_exception_group=m.get("is_exception_group", False),
            data=dict(m.get("data", {})),
        )
    return CapturedError(
        type=c.get("type", ""),
        message=c.get("message", ""),
        frames=frames,
        mechanism=mech,
        release=c.get("release", ""),
        server_name=c.get("server_name", ""),
    )


@pytest.mark.parametrize("name", FIXTURES)
def test_fixture_encodes_byte_identical(name: str):
    """Every fixture's encoded bytes must match the committed ``.pb`` exactly.

    The ``.pb`` files are the wire-format spec for cross-language
    conformance; any divergence is a producer bug.
    """
    fixtures = _fixtures_dir()
    descriptor = json.loads((fixtures / f"{name}.json").read_text())
    expected = (fixtures / f"{name}.pb").read_bytes()

    captured = _captured_from_json(descriptor)
    actual = encode(captured)

    assert actual == expected, f"fixture {name}: encoded bytes differ"


@pytest.mark.parametrize("name", FIXTURES)
def test_fixture_round_trips(name: str):
    """Encode then decode yields the original plain ``CapturedError``."""
    fixtures = _fixtures_dir()
    descriptor = json.loads((fixtures / f"{name}.json").read_text())
    captured = _captured_from_json(descriptor)

    from sererr.proto import sererr_pb2

    encoded_bytes = encode(captured)
    decoded_proto = sererr_pb2.CapturedError()
    decoded_proto.ParseFromString(encoded_bytes)
    decoded = from_proto(decoded_proto)

    assert decoded == captured


def test_default_captured_error_encodes_to_empty_bytes():
    """A default :class:`CapturedError` (all zero values, no mechanism) encodes to 0 bytes.

    Proto3 default values are not on the wire. This is the
    "Empty CapturedError encodes as empty bytes" scenario.
    """
    captured = CapturedError()
    assert encode(captured) == b""


def test_mechanism_data_sorted_keys_deterministic():
    """Mechanism ``data`` keys are written in sorted order — fixture 0005 has 3 keys.

    The Rust reference uses ``BTreeMap`` so encoded bytes are
    deterministic. Python's ``dict`` is insertion-ordered; we must sort.
    Encoding the same map in different insertion orders must yield the
    same bytes.
    """
    a = ExceptionMechanism(
        type="signal", handled=True, data={"errno": "11", "signal_name": "SIGSEGV", "signal_number": "11"}
    )
    b = ExceptionMechanism(
        type="signal", handled=True, data={"signal_number": "11", "errno": "11", "signal_name": "SIGSEGV"}
    )
    err_a = CapturedError(type="SignalError", message="segfault", mechanism=a)
    err_b = CapturedError(type="SignalError", message="segfault", mechanism=b)

    assert encode(err_a) == encode(err_b)
