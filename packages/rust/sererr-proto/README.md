# sererr-proto

Protobuf bindings + adapters for [sererr](https://sererr.fyi). Provides
prost-generated `sererr.v1` types and `From` conversions to/from the
plain types in the [`sererr`](https://crates.io/crates/sererr) crate.

## Quick start

```rust
use sererr_proto::ProtoCapturedError;
use sererr::capture;
use prost::Message;

let chain = capture(&err, "MyError", "v1.0.0", "host");
let proto_chain: Vec<ProtoCapturedError> =
    chain.into_iter().map(Into::into).collect();
let bytes = proto_chain[0].encode_to_vec();
```

## License

Dual MIT / BSD-3-Clause.
