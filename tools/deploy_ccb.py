#!/usr/bin/env python3
"""Build the Fabric CCB candidate with the reviewed frontend contract, then install it."""

import argparse
from datetime import datetime
from hashlib import file_digest
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
from tempfile import TemporaryDirectory

from frontend_contract_ci import ROOT, command, publish_local_product, resolve, verify_product


TARGET = "1.21.1-fabric"


def digest(path):
    with path.open("rb") as stream:
        return file_digest(stream, "sha256").hexdigest()


def require_closed(instance):
    # Pass the path as data to PowerShell, including if the launcher quotes it.
    environment = dict(os.environ, ATOMCHAT_CCB_INSTANCE=str(instance))
    process_check = r"""
$ErrorActionPreference = 'Stop'
$path = $env:ATOMCHAT_CCB_INSTANCE.Replace('\', '/')
$clients = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'"
foreach ($client in $clients) {
    $command = $client.CommandLine
    if ($command -match 'KnotClient|net.minecraft.client.main.Main' -and
        $command.Replace('\', '/').IndexOf($path, [StringComparison]::OrdinalIgnoreCase) -ge 0) { exit 9 }
}
"""
    result = subprocess.run(["powershell.exe", "-NoProfile", "-Command", process_check], env=environment)
    if result.returncode == 9:
        raise ValueError("Close the CCB client before deployment")
    result.check_returncode()


def build_candidate(project, maven, reviewed):
    environment = os.environ.copy()
    environment.pop("FCL_REVIEWED_REVISION", None)
    tasks = command(project, maven, "strict", reviewed, "build", platform.system(), platform.machine())
    subprocess.run(tasks, cwd=project, env=environment, check=True)
    return verify_product(project, "strict", require_clean=False)


def install(instance, backup_root, product, atomlib_digest, reviewed):
    mods = (instance / "mods").resolve(strict=True)
    if mods.parent != instance:
        raise ValueError("CCB mods directory must be inside the instance")
    libraries = list(mods.glob("atomlib-Fabric-1.21.1-*.jar"))
    old_products = list(mods.glob("atomchat-Fabric-1.21.1-*.jar"))
    if len(libraries) != 1 or digest(libraries[0]) != atomlib_digest:
        raise ValueError("CCB must have the same AtomLib input used by the strict build")
    if len(old_products) != 1:
        raise ValueError("Expected one active AtomChat jar in CCB")
    previous, library = old_products[0], libraries[0]
    active = mods / product.name
    artifact_digest = digest(product)
    if active != previous and active.exists():
        raise ValueError("Destination would overwrite another mod")
    require_closed(instance)

    now = datetime.now().astimezone()
    backup = backup_root / now.strftime("%Y%m%d-%H%M%S-%f")
    backup.mkdir(parents=True)
    backed_up = {}
    for source in (previous, library, *sorted((instance / "config/atomchat").glob("*.json"))):
        relative = source.relative_to(instance)
        destination = backup / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        saved = digest(destination)
        if saved != digest(source):
            raise ValueError(f"Backup verification failed for {relative}")
        backed_up[relative.as_posix()] = saved

    # Stage on the same filesystem so replacement and rollback are renames.
    with TemporaryDirectory(prefix=".atomchat-deploy-", dir=mods) as staging:
        stage = Path(staging) / product.name
        retired = Path(staging) / "previous.jar"
        shutil.copy2(product, stage)
        if digest(stage) != artifact_digest:
            raise ValueError("Staged AtomChat jar differs from the strict build")
        require_closed(instance)
        if any(digest(instance / relative) != saved for relative, saved in backed_up.items()):
            raise ValueError("CCB files changed during deployment")
        try:
            # Publish first, so interruption always leaves at least one usable jar.
            os.replace(stage, active)
            if active != previous:
                os.replace(previous, retired)
            if digest(active) != artifact_digest or digest(library) != atomlib_digest:
                raise ValueError("Installed jars differ from the verified pair")
            receipt = {
                "installed_at": now.isoformat(),
                "instance": instance.as_posix(),
                "reviewed_frontend_revision": reviewed,
                "installed": {active.name: artifact_digest, library.name: atomlib_digest},
                "backup": backed_up,
            }
            (backup / "deployment.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
        except BaseException:
            if active == previous and not stage.exists():
                shutil.copy2(backup / "mods" / previous.name, retired)
                os.replace(retired, previous)
            elif active != previous:
                if retired.exists():
                    os.replace(retired, previous)
                if active.exists():
                    os.replace(active, stage)
            raise
    return backup / "deployment.json"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("instance", type=Path)
    parser.add_argument("maven", type=Path)
    parser.add_argument("backup", type=Path)
    parser.add_argument("--reviewed", required=True)
    parser.add_argument("--delivery-root", type=Path, help="checkout whose Fabric libs should receive the JAR")
    args = parser.parse_args()
    instance = args.instance.resolve(strict=True)
    if instance.name != "1.21.1-CCB":
        parser.error("the selected instance must be 1.21.1-CCB")
    require_closed(instance)
    reviewed = resolve(ROOT, args.reviewed)
    lock = json.loads((ROOT / "versions/atomlib.json").read_text(encoding="utf-8"))
    delivery_project = ((args.delivery_root / "platforms" / TARGET).resolve(strict=True)
                        if args.delivery_root is not None else None)
    product = build_candidate(ROOT / "platforms" / TARGET, args.maven.resolve(strict=True), reviewed)
    if delivery_project is not None:
        print(publish_local_product(product, delivery_project))
    print(install(instance, args.backup.resolve(), product, lock["sha256"][TARGET], reviewed))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error)) from error
