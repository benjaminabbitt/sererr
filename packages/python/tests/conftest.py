"""Pytest session bootstrap.

Force the protobuf runtime to its pure-Python implementation so the
buf-generated ``*_pb2.py`` modules' ``if not _descriptor._USE_C_DESCRIPTORS:``
block is actually live during tests. Under the default CPython
protobuf (C descriptors), that block is dead code and mutations to
descriptor-registration statements survive un-killably.

The env var must be set BEFORE :mod:`google.protobuf.descriptor` is
imported — :mod:`conftest` runs before any test imports it, which is
early enough.
"""

import os

os.environ.setdefault("PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION", "python")
