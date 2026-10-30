#!/usr/bin/env python3
"""Create the deterministic local Maven input expected by AC CI."""
from __future__ import annotations

import argparse, hashlib, json, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument("--maven", type=Path, required=True)
source = parser.parse_args().maven.resolve()
if not source.is_dir():
    raise SystemExit(f"AtomLib Maven directory does not exist: {source}")
version = json.loads((ROOT / "versions/atomlib.json").read_text(encoding="utf-8"))["version"]
output = ROOT / "build" / f"atomlib-input-{version}.zip"
output.parent.mkdir(parents=True, exist_ok=True)
files = sorted(p for p in source.rglob("*") if p.is_file())
with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for path in files:
        info = zipfile.ZipInfo(path.relative_to(source).as_posix(), (2020, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        archive.writestr(info, path.read_bytes())
digest = hashlib.sha256(output.read_bytes()).hexdigest()
(output.with_suffix(output.suffix + ".sha256")).write_text(f"{digest}  {output.name}\n", encoding="ascii")
print(output)
print(digest)
