#!/usr/bin/env python3
"""Fetch, authenticate and unpack the pinned AtomLib Maven input for CI."""
from __future__ import annotations

import argparse, hashlib, json, shutil, tempfile, urllib.parse, urllib.request, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def fail(message: str) -> None:
    raise SystemExit(f"AtomLib input error: {message}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url")
    parser.add_argument("--sha256")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    required = json.loads((ROOT / "versions/atomlib.json").read_text(encoding="utf-8"))
    pinned = required.get("input_archive", {})
    url = args.url or pinned.get("url")
    sha256 = args.sha256 or pinned.get("sha256")
    if not url or not sha256:
        fail("no URL/SHA-256 given and versions/atomlib.json has no input_archive pin")
    if not url.startswith("https://"):
        fail("--url must use https://")
    if len(sha256) != 64 or any(c not in "0123456789abcdefABCDEF" for c in sha256):
        fail("--sha256 must be a 64-character hexadecimal digest")

    version = required["version"]
    with urllib.request.urlopen(url, timeout=60) as response:
        payload = response.read()
    actual = hashlib.sha256(payload).hexdigest()
    if actual.lower() != sha256.lower():
        fail(f"SHA-256 mismatch: expected {sha256}, got {actual}")

    output = args.output.resolve()
    target = output.parent / "maven"
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True)
    archive_path = output.parent / "atomlib-input.zip"
    archive_path.write_bytes(payload)
    with zipfile.ZipFile(archive_path) as archive:
        root = target.resolve()
        for info in archive.infolist():
            path = (target / info.filename).resolve()
            if root != path and root not in path.parents:
                fail(f"unsafe archive path: {info.filename}")
        archive.extractall(target)

    for name, target_info in required["targets"].items():
        directory = target / "com/atom/lib" / target_info["artifact"] / version
        jar = directory / f"{target_info['artifact']}-{version}.jar"
        if not jar.is_file():
            fail(f"archive is missing {jar.relative_to(target)}")
        expected = required.get("sha256", {}).get(name)
        if not expected:
            fail(f"versions/atomlib.json has no SHA-256 pin for {name}")
        digest = hashlib.sha256(jar.read_bytes()).hexdigest()
        if digest != expected:
            fail(f"{name} jar SHA-256 mismatch: expected {expected}, got {digest}")
    output.write_text(str(target), encoding="utf-8")
    print(target)


if __name__ == "__main__":
    main()
