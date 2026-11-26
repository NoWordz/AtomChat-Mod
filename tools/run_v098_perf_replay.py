"""Replay AtomChat in the archived private-desktop CCB fixture on Windows."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "tools/probes/v098"
JFC = """<?xml version="1.0" encoding="UTF-8"?>
<configuration version="2.0" label="AtomChat isolated replay" description="Frame timing and GC" provider="AtomChat">
  <event name="com.atomchat.UiFrame"><setting name="enabled">true</setting></event>
  <event name="com.atomchat.PanelSegments"><setting name="enabled">true</setting></event>
  <event name="com.atomchat.Navigation"><setting name="enabled">true</setting></event>
  <event name="com.atomchat.ScreenLifecycle"><setting name="enabled">true</setting></event>
  <event name="bench.AtomChatReplayCpu"><setting name="enabled">true</setting></event>
  <event name="bench.AtomChatReplayGpu"><setting name="enabled">true</setting></event>
  <event name="bench.AtomChatReplayFrameInterval"><setting name="enabled">true</setting></event>
  <event name="jdk.GarbageCollection"><setting name="enabled">true</setting></event>
  <event name="jdk.ExecutionSample"><setting name="enabled">true</setting><setting name="period">10 ms</setting></event>
</configuration>
"""


def build_probe(retest, java, out, prior):
    classes = out / "probe-classes"
    classes.mkdir()
    with zipfile.ZipFile(prior / "mods/atomprobe.jar") as source:
        source.extractall(classes)
    libraries = retest.fixture / "libraries"
    minecraft, = (prior / ".fabric/remappedJars").glob("*/client-intermediary.jar")
    asm, = (p for p in libraries.glob("asm-*.jar") if p.name[4:5].isdigit())
    classpath = os.pathsep.join(map(str, (minecraft, out / "atomchat-reference.jar", libraries / "*")))
    subprocess.run([str(java / "javac.exe"), "-proc:none", "-cp", classpath, "-d", str(classes),
                    *map(str, SOURCES.glob("*.java"))], check=True)
    subprocess.run([str(java / "java.exe"), "-cp", os.pathsep.join(map(str, (classes, asm))),
                    "ExtendReplayRoute", str(classes / "bench/SteadyProbe.class")], check=True)
    mixin = classes / "cubic-probe.mixins.json"
    metadata = json.loads(mixin.read_text(encoding="utf-8"))
    metadata["client"] += ["ReplayMeasurements$Panel", "ReplayMeasurements$Queries", "ReplayMeasurements$ChatHud"]
    mixin.write_text(json.dumps(metadata), encoding="utf-8")
    probe = out / "atomprobe.jar"
    with zipfile.ZipFile(probe, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        for path in sorted(classes.rglob("*")):
            if path.is_file() and not path.name.startswith("ExtendReplayRoute"):
                jar.write(path, path.relative_to(classes).as_posix())
    return probe


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("label")
    cli.add_argument("jar", type=Path)
    cli.add_argument("--archive", required=True, type=Path, help="the v094 closeout replay/worktree.zip")
    cli.add_argument("--library", required=True, type=Path, help="pinned Fabric AtomLib 0.7.1 jar")
    cli.add_argument("--fixture", type=Path, default=Path(os.environ["TEMP"]) / "atomchat-v093-matrix")
    cli.add_argument("--compile-against", type=Path, help="released 0.9.7 jar if testing newer builds")
    cli.add_argument("--quality", choices=["ULTRA", "BALANCED", "PERFORMANCE"], default="BALANCED")
    cli.add_argument("--scale", choices=["2", "3", "5"], default="5")
    cli.add_argument("--matrix", action="store_true", help="save the 36 steady/scroll quality comparisons")
    cli.add_argument("--theme", choices=["dark", "light"], default="dark")
    cli.add_argument("--out", type=Path, default=ROOT / "build/v098-perf")
    args = cli.parse_args()
    java = Path(os.environ["JAVA_HOME"]) / "bin"
    release = args.jar.resolve(strict=True)
    library = args.library.resolve(strict=True)
    base = ROOT / "versions/atomlib.json"
    pin = json.loads(base.read_text(encoding="utf-8"))["sha256"]["1.21.1-fabric"]
    if hashlib.sha256(library.read_bytes()).hexdigest() != pin:
        cli.error("AtomLib does not match the Fabric digest in versions/atomlib.json")
    if not args.label.isascii() or not args.label.replace("-", "").isalnum():
        cli.error("label must contain only ASCII letters, digits and hyphens")
    out = (args.out / args.label).resolve()
    out.mkdir(parents=True)
    source = out / "retest.py"
    with zipfile.ZipFile(args.archive) as archive:
        source.write_bytes(archive.read("build/v094-retest/retest.py"))
    spec = importlib.util.spec_from_file_location("retest", source)
    retest = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(retest)
    retest.fixture = args.fixture.resolve(strict=True) / "full"
    prior = args.fixture.resolve(strict=True) / "continuous-final-a"
    retest.scratch = Path(os.environ["TEMP"]) / "atomchat-v098-perf-replay"
    retest.java = java / "java.exe"
    retest.archive = out / "reference"
    retest.archive.mkdir()
    config = json.loads((retest.fixture / "atomchat-client.json").read_text(encoding="utf-8-sig"))
    reference = json.loads((prior / "run.json").read_text(encoding="utf-8"))
    reference["options"]["guiScale"] = args.scale
    reference["config_overrides"].update(
        debug=True, graphicsQuality=args.quality, chatHudEnabled=True, chatHudTimedExit=False,
        historyEnabled=False, diagnosticMeasureOnly=False,
    )
    if args.theme == "light":
        reference["config_overrides"].update(panelBgColor=-1052177, cardColor=-1842205)
    reference.update(theme=args.theme, reverse=True, quality_matrix=args.matrix)
    config.update(reference["config_overrides"])
    config_bytes = json.dumps(config, ensure_ascii=False, indent=2).replace("\n", "\r\n").encode()
    reference["effective_config_sha256"] = hashlib.sha256(config_bytes).hexdigest()
    (retest.archive / "run.json").write_text(json.dumps(reference, ensure_ascii=False), encoding="utf-8")
    shutil.copyfile(prior / "hosts", retest.archive / "hosts")
    shutil.copytree(prior / ".fabric/remappedJars", retest.archive / ".fabric/remappedJars")
    shutil.copyfile(args.compile_against or release, out / "atomchat-reference.jar")
    shutil.copyfile(retest.fixture / "manifest.json", out / "fixture-manifest.json")
    probe = build_probe(retest, java, out, prior)
    home = retest.scratch / args.label
    jfc = out / "atomchat.jfc"
    jfc.write_text(JFC, encoding="utf-8")
    try:
        retest.run(args.label, release, probe, (
            "-Dsteady.reverse=true",
            f"-Datomchat.replay.matrix={str(args.matrix).lower()}",
            f"-XX:StartFlightRecording=settings={jfc},filename={home / 'frames.jfr'},dumponexit=true,maxsize=128m",
        ), library)
    finally:
        for name in ("run.json", "launcher.log", "frames.csv", "frames.jfr"):
            if (home / name).is_file():
                shutil.copyfile(home / name, out / name)
        effective = home / "config/atomchat/atomchat-client.json"
        if effective.is_file():
            shutil.copyfile(effective, out / "atomchat-client.json")
        if (home / "probe-screenshots").is_dir():
            shutil.copytree(home / "probe-screenshots", out / "screenshots")


if __name__ == "__main__":
    main()
