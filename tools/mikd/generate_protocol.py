"""Generate the IPC contract with the same pinned compiler in both builds."""
import argparse
from functools import cache
from pathlib import Path
import subprocess
import shutil
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PROTO_DIR = ROOT / "proto"


@cache
def protoc_command():
    version = subprocess.check_output(["protoc", "--version"], text=True).strip()
    if version != "libprotoc 36.1":
        raise SystemExit(f"protoc 36.1 required, found {version}")
    return "protoc", "-I", str(PROTO_DIR)


def protoc(*args, schema=None, input_bytes=None):
    files = (PROTO_DIR / schema,) if schema else sorted(PROTO_DIR.rglob("*.proto"))
    return subprocess.check_output([*protoc_command(), *args, *map(str, files)], input=input_bytes)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--java-out", type=Path, required=True)
    options = parser.parse_args()
    options.java_out.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=options.java_out.parent) as temporary:
        protoc(f"--java_out=lite:{temporary}")
        # Generated Java is entirely owned by this task. Removed schema types
        # must not survive as stale classes in a later incremental build.
        for previous in options.java_out.rglob("*.java"):
            previous.unlink()
        shutil.copytree(temporary, options.java_out, dirs_exist_ok=True)
