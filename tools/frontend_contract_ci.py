#!/usr/bin/env python3
"""Route enrolled AtomChat targets through the Windows distribution build."""
import argparse
import json
import os
from pathlib import Path
from tempfile import TemporaryDirectory
import platform
import re
import shutil
import subprocess
import sys
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent.parent
CONTRACT_RESOURCES = "META-INF/atomlib/frontend/"
LOCAL_TARGETS = {"1.20.1-forge", "1.21.1-fabric", "1.21.1-neoforge"}


def git(root, *args):
    return subprocess.run(["git", "--no-replace-objects", "-C", str(root), *args],
                          text=True, capture_output=True)


def resolve(root, ref):
    if not ref:
        return ""
    result = git(root, "rev-parse", "--verify", "--end-of-options", f"{ref}^{{commit}}")
    if result.returncode:
        raise ValueError(f"MISSING_EVIDENCE reviewed Git revision is unavailable: {ref}")
    return result.stdout.strip()


def in_revision(root, ref, path):
    return bool(ref) and git(root, "cat-file", "-e", f"{ref}:{path}").returncode == 0


def route_jobs(root, targets, pr_base, reviewed, tag):
    base = resolve(root, pr_base)
    approved = resolve(root, reviewed)
    historically_before_approval = (tag and approved and resolve(root, "HEAD") != approved and
                                    git(root, "merge-base", "--is-ancestor", "HEAD", approved).returncode == 0)
    builds, distributors = [], []
    for target in targets:
        config = f"versions/frontend-contract/{target['target']}/contract.properties"
        candidate = (root / config).is_file()
        base_enrolled = in_revision(root, base, config)
        reviewed_enrolled = in_revision(root, approved, config)
        history = git(root, "log", "-1", "--format=%H", "HEAD", "--", config)
        if history.returncode:
            raise ValueError(f"Cannot read Git history for {config}: {history.stderr.strip()}")
        enrolled = candidate or base_enrolled or bool(history.stdout.strip()) or (
            reviewed_enrolled and not historically_before_approval)
        if enrolled:
            revision = base if base_enrolled else approved if reviewed_enrolled else ""
            if not candidate or not revision:
                raise ValueError(f"MISSING_EVIDENCE {config} and its reviewed Git revision are required")
            official = dict(target, mode="strict", runner="windows-2025", reviewed=revision)
            builds.extend([official, dict(target, mode="linux-verify", runner="ubuntu-latest", reviewed="")])
        else:
            official = dict(target, mode="legacy", runner="ubuntu-latest", reviewed="")
            builds.append(official)
        distributors.append(official)
    return builds, distributors


def command(project, maven, mode, revision, operation, system, machine):
    if operation == "publish" and mode == "linux-verify":
        raise ValueError("Linux verification builds cannot publish")
    if mode == "strict" and (system != "Windows" or machine.lower() not in {"amd64", "x86_64"}):
        raise ValueError("The approved frontend contract requires Windows x64")
    if mode != "strict" and system != "Linux":
        raise ValueError(f"{mode} CI requires Linux")
    wrapper = project / ("gradlew.bat" if system == "Windows" else "gradlew")
    args = [str(wrapper), *(["clean", "build"] if operation == "build" else ["publishMods"])]
    if mode == "strict":
        if not re.fullmatch(r"[0-9a-f]{40}|[0-9a-f]{64}", revision):
            raise ValueError("MISSING_EVIDENCE resolved reviewed Git revision required")
        relative = f"versions/frontend-contract/{project.name}/contract.properties"
        config = project.parents[1] / relative
        if not config.is_file():
            raise ValueError(f"MISSING_EVIDENCE {config}")
        if operation == "build":
            args.append("frontendContractCheck")
        args += [f"-PatomchatFrontendContract={config}", f"-PatomchatFrontendReviewedConfig={relative}",
                 f"-PatomchatFrontendReviewedRevision={revision}", "--no-configuration-cache"]
    elif mode == "linux-verify":
        args.append("-PatomchatFrontendLinuxVerification")
    return [*args, f"-PatomlibMavenRepo={maven}", "--no-daemon"]


def verify_product(project, mode, *, require_clean=True):
    build = project / "build/atomlib-consumer"
    products = [jar for jar in (build / "libs").glob("*.jar")
                if "sources" not in jar.name and "-slim" not in jar.name]
    if len(products) != 1:
        raise ValueError(f"Expected one AtomChat distribution JAR, found {products}")
    product = products[0]
    with ZipFile(product) as archive:
        stamp = archive.read("atomchat-build.properties").decode("ascii")
        print(f"Build stamp for {product.name}\n{stamp}", flush=True)
        if require_clean and "git_dirty=false" not in stamp.splitlines():
            raise ValueError("CI artifact requires a clean checkout")
        packaged = {name: archive.read(name) for name in archive.namelist()
                    if name.startswith(CONTRACT_RESOURCES) and name.endswith(".fcl")}
    if mode == "strict":
        generated_root = build / "generated/frontendContractResources"
        generated = {file.relative_to(generated_root).as_posix(): file.read_bytes()
                     for file in (generated_root / CONTRACT_RESOURCES).rglob("*.fcl")}
        names = {Path(name).name for name in generated}
        if names != {"design.fcl", "manifest.fcl"} or packaged != generated:
            raise ValueError("Windows artifact is missing the generated frontend admission")
    elif packaged:
        raise ValueError("Non-contracted build packaged frontend admission")
    subprocess.run([sys.executable, ROOT / "tools/verify_jars.py", "--profile", "atomlib-consumer", project],
                   check=True, cwd=ROOT)
    return product



def check_local_directory(directory):
    if directory.is_symlink() or directory.exists() and (
        not directory.is_dir()
        or any(item.is_symlink() or not item.is_file() or
               not item.name.startswith("atomchat-") or item.suffix != ".jar" for item in directory.iterdir())
    ):
        raise ValueError(f"Local delivery directory contains files outside the AtomChat builds: {directory}")


def publish_local_products(products, output):
    output.parent.mkdir(parents=True, exist_ok=True)
    previous = output.with_name(f".{output.name}-previous")
    check_local_directory(output)
    check_local_directory(previous)
    if previous.exists() and not output.exists():
        previous.rename(output)

    with TemporaryDirectory(prefix=".atomchat-delivery-", dir=output.parent) as directory:
        staging = Path(directory)
        incoming = staging / "incoming"
        incoming.mkdir()
        for product in products:
            shutil.copy2(product, incoming / product.name)

        # Keep the old batch outside automatic cleanup until the swap succeeds.
        if previous.exists():
            previous.rename(staging / "retired")
        if output.exists():
            output.rename(previous)
        try:
            incoming.rename(output)
        except BaseException:
            if previous.exists() and not output.exists():
                previous.rename(output)
            raise
        if previous.exists():
            previous.rename(staging / "previous")


def build_local(root, maven, reviewed, output, java_homes, offline):
    targets = json.loads((root / "versions/targets.json").read_text(encoding="utf-8"))
    enabled = {key: target for key, target in targets.items()
               if isinstance(target, dict) and target.get("buildable")}
    if enabled.keys() != LOCAL_TARGETS:
        raise ValueError(f"Expected local builds for {sorted(LOCAL_TARGETS)}, found {sorted(enabled)}")
    revision = resolve(root, reviewed)
    environment = os.environ.copy()
    environment.pop("FCL_REVIEWED_REVISION", None)
    products = []
    for target in enabled.values():
        project = root / target["project"]
        java = java_homes[target["java"]]
        tasks = command(project, maven, "strict", revision, "build", platform.system(), platform.machine())
        if offline:
            tasks.append("--offline")
        print(subprocess.list2cmdline(tasks), flush=True)
        subprocess.run(tasks, cwd=project, env=dict(environment, JAVA_HOME=str(java)), check=True)
        products.append(verify_product(project, "strict", require_clean=False))

    if len({product.name for product in products}) != len(LOCAL_TARGETS):
        raise ValueError(f"Expected a unique distribution JAR for every enabled target, found {products}")
    publish_local_products(products, output)
    for product in products:
        print(output / product.name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="operation", required=True)
    sub.add_parser("matrix").add_argument("targets", type=Path)
    for operation in ("build", "publish"):
        job = sub.add_parser(operation)
        job.add_argument("project", type=Path)
        job.add_argument("maven", type=Path)
        job.add_argument("--mode", choices=("legacy", "strict", "linux-verify"), required=True)
        job.add_argument("--reviewed", default="")
        if operation == "build":
            job.add_argument("--dist", type=Path)
    local = sub.add_parser("build-local", help="build all enabled Windows targets and collect only AtomChat JARs")
    local.add_argument("maven", type=Path)
    local.add_argument("--reviewed", required=True)
    local.add_argument("--java17", type=Path, required=True)
    local.add_argument("--java21", type=Path, required=True)
    local.add_argument("--output", type=Path, required=True)
    local.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    if args.operation == "build-local":
        build_local(ROOT, args.maven.resolve(strict=True), args.reviewed, args.output.absolute(),
                    {17: args.java17.resolve(strict=True), 21: args.java21.resolve(strict=True)}, args.offline)
        return
    if args.operation == "matrix":
        builds, distributors = route_jobs(ROOT, json.loads(args.targets.read_text(encoding="utf-8")),
                                          os.environ.get("CONTRACT_PR_BASE", ""),
                                          os.environ.get("CONTRACT_REVIEWED", ""),
                                          os.environ.get("GITHUB_REF", "").startswith("refs/tags/"))
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8", newline="\n") as output:
            print(f"build={json.dumps(builds, separators=(',', ':'))}", file=output)
            print(f"distribution={json.dumps(distributors, separators=(',', ':'))}", file=output)
        return
    project, maven = args.project.resolve(), args.maven.resolve()
    dist = getattr(args, "dist", None)
    if dist and args.mode == "linux-verify":
        raise ValueError("Linux verification builds cannot stage distribution artifacts")
    environment = os.environ.copy()
    # The reviewed SHA is passed to Gradle as a property resolved by the matrix.
    environment.pop("FCL_REVIEWED_REVISION", None)
    tasks = command(project, maven, args.mode, args.reviewed, args.operation, platform.system(), platform.machine())
    if args.operation == "publish":
        verify_product(project, args.mode)
    print(subprocess.list2cmdline(tasks), flush=True)
    subprocess.run(tasks, cwd=project, env=environment, check=True)
    if args.operation == "build":
        product = verify_product(project, args.mode)
        if dist:
            lock = json.loads((ROOT / "versions/atomlib.json").read_text(encoding="utf-8"))
            artifact, version = lock["targets"][project.name]["artifact"], lock["version"]
            library = maven / "com/atom/lib" / artifact / version / f"{artifact}-{version}.jar"
            dist.mkdir(parents=True, exist_ok=True)
            shutil.copy2(product, dist)
            shutil.copy2(library, dist)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error)) from error
