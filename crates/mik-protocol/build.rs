use std::{env, io, process::Command};

fn main() -> io::Result<()> {
    let protoc = env::var_os("PROTOC").unwrap_or_else(|| "protoc".into());
    let output = Command::new(protoc)
        .arg("--version")
        .output()
        .map_err(|error| io::Error::other(format!("could not run protoc 36.1: {error}")))?;
    if !output.status.success() || output.stdout != b"libprotoc 36.1\n" {
        return Err(io::Error::other("protoc must be pinned to 36.1"));
    }
    println!("cargo:rerun-if-env-changed=PROTOC");
    // Watch imports as well as the entry points without keeping a second schema list.
    println!("cargo:rerun-if-changed=../../proto");
    prost_build::compile_protos(
        &[
            "../../proto/mik/transport.proto",
            "../../proto/mik/music.proto",
        ],
        &["../../proto"],
    )
}
