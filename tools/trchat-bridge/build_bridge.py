#!/usr/bin/env python3
"""Compile the narrow bridge and patch the exact TrChat 2.5.2 release locally."""
import argparse
import hashlib
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
EXPECTED = "8fe9bbe885fb97c4591f7d28e75f025e0e9eaa35010921c911ef07b19d62844f"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--trchat", type=Path, required=True)
    parser.add_argument("--paper-libraries", type=Path, required=True)
    parser.add_argument("--asm", type=Path, required=True)
    parser.add_argument("--asm-tree", type=Path, required=True)
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "build/trchat-bridge")
    args = parser.parse_args()
    if hashlib.sha256(args.trchat.read_bytes()).hexdigest() != EXPECTED:
        parser.error("Unsupported input: expected the official TrChat 2.5.2 release JAR")
    libraries = sorted(args.paper_libraries.rglob("*.jar"))
    if not libraries:
        parser.error("Point --paper-libraries at the libraries directory of an extracted Paper 1.21.1 distribution")
    runtime = args.output / "classes"
    patcher = args.output / "patcher"
    runtime.mkdir(parents=True, exist_ok=True)
    patcher.mkdir(parents=True, exist_ok=True)
    javac, java = (str(args.jdk / "bin" / cmd) for cmd in ("javac", "java"))
    runtime_sources = list((ROOT / "tools/trchat-bridge/src/main/java").rglob("*.java"))
    runtime_sources.append(ROOT / "shared/src/main/java/com/atom/chat/channel/ChannelWire.java")
    import os
    classpath = os.pathsep.join(map(str, [args.trchat, *libraries]))
    # Use an argument file on Windows, where the extracted Paper classpath is long.
    argfile = args.output / "runtime-javac.args"
    argfile.write_text("\n".join('"' + s.replace("\\", "/") + '"' for s in [
        "--release", "17", "-proc:none", "-encoding", "UTF-8", "-cp", classpath, "-d", str(runtime), *map(str, runtime_sources)
    ]), encoding="utf-8")
    subprocess.run([javac, f"@{argfile}"], check=True)
    cp = os.pathsep.join(map(str, (args.asm, args.asm_tree)))
    src = ROOT / "tools/trchat-bridge/src/patch/java/com/atom/chat/trchat/PatchTrChat.java"
    subprocess.run([javac, "--release", "17", "-cp", cp, "-d", str(patcher), str(src)], check=True)
    result = args.output / "TrChat-2.5.2-atomchat-bridge.jar"
    partial = result.with_suffix(".partial")
    subprocess.run([java, "-cp", os.pathsep.join([cp, str(patcher)]), "com.atom.chat.trchat.PatchTrChat",
                    str(args.trchat), str(runtime), str(partial)], check=True)
    partial.replace(result)
    print(result)


if __name__ == "__main__":
    main()
