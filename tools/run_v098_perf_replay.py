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
  <event name="bench.AtomChatReplayOpenStage"><setting name="enabled">true</setting></event>
  <event name="jdk.GarbageCollection"><setting name="enabled">true</setting></event>
  <event name="jdk.GCHeapSummary"><setting name="enabled">true</setting></event>
  <event name="jdk.ExecutionSample"><setting name="enabled">true</setting><setting name="period">10 ms</setting></event>
  <event name="jdk.NativeMethodSample"><setting name="enabled">true</setting><setting name="period">10 ms</setting></event>
  <event name="jdk.FileRead"><setting name="enabled">true</setting><setting name="threshold">2 ms</setting></event>
  <event name="jdk.FileWrite"><setting name="enabled">true</setting><setting name="threshold">2 ms</setting></event>
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
    metadata["client"] += ["ReplayMeasurements$Panel", "ReplayMeasurements$PanelScreen",
                           "ReplayMeasurements$Queries", "ReplayMeasurements$ChatHud"]
    mixin.write_text(json.dumps(metadata), encoding="utf-8")
    probe = out / "atomprobe.jar"
    with zipfile.ZipFile(probe, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        for path in sorted(classes.rglob("*")):
            if path.is_file() and not path.name.startswith("ExtendReplayRoute"):
                jar.write(path, path.relative_to(classes).as_posix())
    return probe


def with_inputs(launch, mods, history):
    def staged(args, home):
        for jar in mods:
            destination = home / "mods" / jar.name
            if destination.exists():
                raise FileExistsError(f"duplicate fixture mod filename: {jar.name}")
            shutil.copyfile(jar, destination)
        if history is not None:
            destination = home / "atomchat/client/history" / history.name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(history, destination)
        return launch(args, home)
    return staged


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("label")
    cli.add_argument("jar", type=Path)
    cli.add_argument("--archive", required=True, type=Path, help="the v094 closeout replay/worktree.zip")
    cli.add_argument("--library", required=True, type=Path, help="pinned Fabric AtomLib 0.7.1 jar")
    cli.add_argument("--library-sha256", help="digest of an alternate AtomLib candidate for an isolated A/B replay")
    cli.add_argument("--fixture", type=Path, default=Path(os.environ["TEMP"]) / "atomchat-v093-matrix")
    cli.add_argument("--compile-against", type=Path, help="released 0.9.7 jar if testing newer builds")
    cli.add_argument("--quality", choices=["ULTRA", "BALANCED", "PERFORMANCE"], default="BALANCED")
    cli.add_argument("--scale", choices=["2", "3", "5"], default="5")
    cli.add_argument("--driver", type=Path, help="replace the performance route with a remapped Fabric probe")
    cli.add_argument("--extra-mod", action="append", type=Path, default=[], help="copy this JAR only into the isolated mods directory")
    cli.add_argument("--expect-marker", default="STEADY_PROBE_COMPLETE", help="required completion line in the private client log")
    cli.add_argument("--matrix", action="store_true", help="save the 36 steady/scroll quality comparisons")
    cli.add_argument("--forward", action="store_true", help="run text before media to check scene-order sensitivity")
    cli.add_argument("--interrupt-navigation", action="store_true",
                     help="alternate settings pages before the previous transition settles")
    cli.add_argument("--item-card", action="store_true", help="seed a rendered fishing rod card for the text scene")
    cli.add_argument("--reload-check", action="store_true",
                     help="verify three game resource reloads with the AtomChat settings screen open")
    cli.add_argument("--endurance", action="store_true",
                     help="hold the settings navigation route for nine minutes with three resource reloads")
    cli.add_argument("--steady-settings", action="store_true",
                     help="hold the appearance settings page open without navigation for 27 seconds")
    cli.add_argument("--history-fixture", type=Path,
                     help="load an integrated-world history manifest and open the chat on first entry")
    cli.add_argument("--check-addon-settings", choices=("absent", "present"),
                     help="verify the real settings rows while retaining previous add-on selections")
    cli.add_argument("--measure-panel", action="store_true",
                     help="time the outer AtomChat screen render with diagnostics either on or off")
    diagnostics = cli.add_mutually_exclusive_group()
    diagnostics.add_argument("--debug-off", action="store_true", help="measure product mode with diagnostics disabled")
    diagnostics.add_argument("--check-chat-capture", action="store_true",
                             help="exercise the diagnostic capture hotkey and emit three synthetic lines")
    cli.add_argument("--theme", choices=["dark", "light"], default="dark")
    cli.add_argument("--out", type=Path, default=ROOT / "build/v098-perf")
    args = cli.parse_args()
    reload_check = args.reload_check or args.endurance
    if (reload_check or args.check_addon_settings or args.steady_settings) and (args.forward or args.driver):
        cli.error("settings checks require the built-in reverse route with an appearance scene")
    if args.steady_settings and (reload_check or args.interrupt_navigation):
        cli.error("--steady-settings requires an appearance scene without reloads or navigation")
    if args.history_fixture and (args.forward or args.driver or reload_check or args.check_addon_settings
                                 or args.check_chat_capture or args.interrupt_navigation):
        cli.error("--history-fixture replaces the first settings scene with a historical chat view")
    if args.matrix and args.driver:
        cli.error("--matrix uses the built-in performance route")
    if not args.expect_marker.strip():
        cli.error("--expect-marker must contain the driver's success text")
    addons = [path.resolve(strict=True) for path in args.extra_mod]
    java = Path(os.environ["JAVA_HOME"]) / "bin"
    release = args.jar.resolve(strict=True)
    library = args.library.resolve(strict=True)
    base = ROOT / "versions/atomlib.json"
    pin = args.library_sha256 or json.loads(base.read_text(encoding="utf-8"))["sha256"]["1.21.1-fabric"]
    library_digest = hashlib.sha256(library.read_bytes()).hexdigest()
    if library_digest != pin:
        cli.error("AtomLib does not match the expected Fabric digest")
    if not args.label.isascii() or not args.label.replace("-", "").isalnum():
        cli.error("label must contain only ASCII letters, digits and hyphens")
    out = (args.out / args.label).resolve()
    out.mkdir(parents=True)
    source = out / "retest.py"
    with zipfile.ZipFile(args.archive) as archive:
        historical = archive.read("build/v094-retest/retest.py").decode("utf-8")
    completion = "'STEADY_PROBE_COMPLETE' in log"
    if historical.count(completion) != 1:
        raise ValueError("the archived CCB launcher completion check was not found")
    historical = historical.replace(completion, f"{args.expect_marker!r} in log")
    if args.endurance:
        wait = "WaitForSingleObject(info.process, 300_000)"
        if historical.count(wait) != 1:
            raise ValueError("the archived CCB launcher wait was not found")
        historical = historical.replace(wait, "WaitForSingleObject(info.process, 900_000)")
    source.write_text(historical, encoding="utf-8")
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
    history = None
    history_count = 0
    history_digest = None
    if args.history_fixture:
        manifest_path = args.history_fixture.resolve(strict=True)
        manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
        name = Path(manifest["fixtureFile"])
        if name.name != str(name) or name.suffix != ".jsonl" or not name.name.startswith("SP_"):
            cli.error("history manifest must name a single-world .jsonl file")
        history = manifest_path.parent / name
        history_digest = retest.sha(history)
        history_count = manifest["count"]
        if (manifest["levelDatSha256"] != reference["fixture"] or history_digest != manifest["fixtureSha256"]
                or not isinstance(history_count, int) or history_count <= 0
                or len(history.read_bytes().splitlines()) != history_count):
            cli.error("history fixture does not match the archived world or its declared rows")
    retest.launch_private = with_inputs(retest.launch_private, addons, history)
    reference["options"]["guiScale"] = args.scale
    reference["config_overrides"].update(
        debug=not args.debug_off, graphicsQuality=args.quality, chatHudEnabled=True, chatHudTimedExit=False,
        chatHistoryEnabled=history is not None, diagnosticMeasureOnly=False,
    )
    if args.theme == "light":
        reference["config_overrides"].update(panelBgColor=-1052177, cardColor=-1842205)
    if args.check_addon_settings:
        reference["config_overrides"].update(bubbleAppearanceId="atomchat_signal:capsule",
                                             avatarFrameAppearanceId="atomchat_harbor:tide_ring")
    reference.update(theme=args.theme, reverse=not args.forward, quality_matrix=args.matrix,
                     item_card=args.item_card,
                     capture_check=args.check_chat_capture,
                     reload_check=reload_check,
                     endurance=args.endurance,
                     steady_settings=args.steady_settings,
                     history_fixture_sha256=history_digest,
                     addon_settings_check=args.check_addon_settings,
                     measure_panel=args.measure_panel,
                     interrupt_navigation=args.interrupt_navigation,
                     atomlib_sha256=library_digest,
                     extra_mods={jar.name: retest.sha(jar) for jar in addons})
    config.update(reference["config_overrides"])
    config_bytes = json.dumps(config, ensure_ascii=False, indent=2).replace("\n", "\r\n").encode()
    reference["effective_config_sha256"] = hashlib.sha256(config_bytes).hexdigest()
    (retest.archive / "run.json").write_text(json.dumps(reference, ensure_ascii=False), encoding="utf-8")
    shutil.copyfile(prior / "hosts", retest.archive / "hosts")
    shutil.copytree(prior / ".fabric/remappedJars", retest.archive / ".fabric/remappedJars")
    shutil.copyfile(args.compile_against or release, out / "atomchat-reference.jar")
    shutil.copyfile(retest.fixture / "manifest.json", out / "fixture-manifest.json")
    probe = args.driver.resolve(strict=True) if args.driver else build_probe(retest, java, out, prior)
    home = retest.scratch / args.label
    jfc = out / "atomchat.jfc"
    jfc.write_text(JFC, encoding="utf-8")
    try:
        retest.run(args.label, release, probe, (
            f"-Dsteady.reverse={str(not args.forward).lower()}",
            f"-Datomchat.replay.matrix={str(args.matrix).lower()}",
            f"-Datomchat.replay.interruptNavigation={str(args.interrupt_navigation).lower()}",
            f"-Datomchat.replay.itemCard={str(args.item_card).lower()}",
            f"-Datomchat.replay.captureCheck={str(args.check_chat_capture).lower()}",
            f"-Datomchat.replay.reloadCheck={str(reload_check).lower()}",
            f"-Datomchat.replay.endurance={str(args.endurance).lower()}",
            f"-Datomchat.replay.steadySettings={str(args.steady_settings).lower()}",
            f"-Datomchat.replay.historyCount={history_count}",
            f"-Datomchat.replay.addonSettings={args.check_addon_settings or ''}",
            f"-Datomchat.replay.measurePanel={str(args.measure_panel).lower()}",
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

    if reload_check or args.check_addon_settings:
        log = (out / "launcher.log").read_text(encoding="utf-8", errors="replace")
        if args.check_addon_settings and f"V098_REPLAY_ADDON_SETTINGS_COMPLETE {args.check_addon_settings}" not in log:
            raise AssertionError("the client's add-on settings check did not complete")
        if args.check_addon_settings == "present" and "V098_REPLAY_ADDON_TOGGLES_COMPLETE" not in log:
            raise AssertionError("the installed add-on settings did not survive a reload")
    if reload_check:
        completed = [line.rsplit(" ", 1)[-1] for line in log.splitlines()
                     if "V098_REPLAY_RESOURCE_RELOAD_COMPLETE " in line]
        if completed != ["1", "2", "3"]:
            raise AssertionError(f"expected three completed resource reloads, got {completed}")

    if args.check_chat_capture:
        log = (out / "launcher.log").read_text(encoding="utf-8", errors="replace")
        diagnostics = [line for line in log.splitlines() if "[AtomChat][" in line]
        markers = (
            "Preview diagnostic before capture [11, 22, 33]",
            "Preview diagnostic during capture [44, 55, 66]",
            "Preview diagnostic after capture [77, 88, 99]",
        )
        if not all(marker in log for marker in markers):
            raise AssertionError("capture probe did not submit all three chat lines")
        captured = [index for index, marker in enumerate(markers)
                    if any(marker in line for line in diagnostics)]
        if captured != [1] or not any("[AtomChat][chat.raw]" in line and markers[1] in line for line in diagnostics):
            raise AssertionError(f"diagnostic capture must contain only the opted-in chat line, got {captured}")
        print("Diagnostic capture: default redaction, hotkey opt-in and manual stop passed")

    if history_count:
        log = (out / "launcher.log").read_text(encoding="utf-8", errors="replace")
        if f"V098_REPLAY_HISTORY_FIRST_OPEN {history_count}" not in log:
            raise AssertionError("the first chat view did not receive the saved fixture rows")


if __name__ == "__main__":
    main()
