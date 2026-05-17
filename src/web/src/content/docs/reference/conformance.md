---
title: Conformance corpus
description: Cross-language byte-equivalence tests.
---

> *Stub.*

A shared corpus of fixtures + expected wire bytes lives in
[`tests/conformance/`](https://github.com/sererr/sererr/tree/main/tests/conformance).

Every language runs both directions per fixture:

1. **Encode**: language X serializes `fixtures/N.json` → asserts
   byte-equivalent to `expected/N.pb`
2. **Decode**: language X deserializes `expected/N.pb` → asserts
   equivalent to the JSON in `fixtures/N.json`

The conformance corpus IS the wire-format spec — packages that fail
conformance can't release.

See the [tests README](https://github.com/sererr/sererr/tree/main/tests/conformance#readme)
for the fixture format and runner conventions.
