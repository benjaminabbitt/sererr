// Sample source used by sererr-flate's integration test.
// Embedded via rust_embed::Embed at compile time and accessed
// through the EmbedSourceProvider adapter.

pub fn one() {
    let x = 1;
    return_x(x);
}

pub fn return_x(x: i32) -> i32 {
    x
}
