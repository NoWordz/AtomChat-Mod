#!/usr/bin/env python3
"""Fetch, authenticate and unpack the pinned AtomLib Maven input for CI."""
from __future__ import annotations

import argparse, hashlib, json, tempfile, urllib.request, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def fail(message: str) -> None:
    raise SystemExit(f"AtomLib input error: {message}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url")
    parser.add_argument("--archive",type=Path,help="Explicit local unreleased Maven candidate archive; still requires a SHA-256")
    parser.add_argument("--sha256")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    required = json.loads((ROOT / "versions/atomlib.json").read_text(encoding="utf-8"))
    pinned = required.get("input_archive", {})
    if args.archive and args.url:
        fail("choose --archive or --url, not both")
    url = None if args.archive else args.url or pinned.get("url")
    sha256 = args.sha256 or pinned.get("sha256")
    if (not url and not args.archive) or not sha256:
        fail("explicit candidate archive or HTTPS URL and SHA-256 required; no released input pin is available")
    if url and not url.startswith("https://"):
        fail("--url must use https://")
    if len(sha256) != 64 or any(c not in "0123456789abcdefABCDEF" for c in sha256):
        fail("--sha256 must be a 64-character hexadecimal digest")

    version = required["version"]
    if args.archive:
        if not args.archive.is_file():fail(f"candidate archive missing: {args.archive}")
        payload=args.archive.read_bytes()
    else:
        with urllib.request.urlopen(url, timeout=60) as response:
            payload = response.read()
    actual = hashlib.sha256(payload).hexdigest()
    if actual.lower() != sha256.lower():
        fail(f"SHA-256 mismatch: expected {sha256}, got {actual}")

    output = args.output.resolve()
    output.parent.mkdir(parents=True,exist_ok=True)
    target = output.parent / "maven"
    if target.exists():
        fail(f"Maven input already exists, choose a fresh output directory: {target}")
    with tempfile.TemporaryDirectory(prefix='atomlib-candidate-',dir=output.parent) as temporary:
        stage=Path(temporary);archive_path=stage/'input.zip';archive_path.write_bytes(payload);unpacked=stage/'maven';unpacked.mkdir()
        with zipfile.ZipFile(archive_path) as archive:
            seen=set()
            for info in archive.infolist():
                path = (unpacked / info.filename).resolve()
                if not path.is_relative_to(unpacked.resolve()) or '\\' in info.filename or info.filename.startswith('/') or ':' in info.filename or (info.external_attr>>16)&0o170000==0o120000:
                    fail(f"unsafe archive path: {info.filename}")
                if str(path).casefold() in seen:fail(f"duplicate archive path: {info.filename}")
                seen.add(str(path).casefold())
            archive.extractall(unpacked)

        for name, target_info in required["targets"].items():
            directory = unpacked / "com/atom/lib" / target_info["artifact"] / version
            jar = directory / f"{target_info['artifact']}-{version}.jar"
            if not jar.is_file():
                fail(f"archive is missing {jar.relative_to(unpacked)}")
            expected = required.get("sha256", {}).get(name)
            if not expected:
                fail(f"versions/atomlib.json has no SHA-256 pin for {name}")
            digest = hashlib.sha256(jar.read_bytes()).hexdigest()
            if digest != expected:
                fail(f"{name} jar SHA-256 mismatch: expected {expected}, got {digest}")
        unpacked.rename(target)
    output.write_text(str(target), encoding="utf-8")
    print(target)


if __name__ == "__main__":
    main()
