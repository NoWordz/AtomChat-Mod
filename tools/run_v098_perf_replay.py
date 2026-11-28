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


def build_probe(retest, java, out, prior, library, motion_trace, without_atomchat):
    classes = out / "probe-classes"
    classes.mkdir()
    with zipfile.ZipFile(prior / "mods/atomprobe.jar") as source:
        source.extractall(classes)
    libraries = retest.fixture / "libraries"
    minecraft, = (prior / ".fabric/remappedJars").glob("*/client-intermediary.jar")
    asm, = (p for p in libraries.glob("asm-*.jar") if p.name[4:5].isdigit())
    classpath = os.pathsep.join(map(str, (minecraft, out / "atomchat-reference.jar", library, libraries / "*")))
    subprocess.run([str(java / "javac.exe"), "-proc:none", "-cp", classpath, "-d", str(classes),
                    *map(str, SOURCES.glob("*.java"))], check=True)
    mixin = classes / "cubic-probe.mixins.json"
    metadata = json.loads(mixin.read_text(encoding="utf-8"))
    if without_atomchat:
        metadata["client"] = ["ReplayControl"]
    else:
        subprocess.run([str(java / "java.exe"), "-cp", os.pathsep.join(map(str, (classes, asm))),
                        "ExtendReplayRoute", str(classes / "bench/SteadyProbe.class")], check=True)
        metadata["client"] += ["ReplayMeasurements$Panel", "ReplayMeasurements$PanelScreen",
                               "ReplayMeasurements$Queries", "ReplayMeasurements$ChatHud"]
        if motion_trace:
            metadata["client"] += [
                "ReplayMotionMeasurements$Page", "ReplayMotionMeasurements$Fades",
                "ReplayMotionMeasurements$Glass", "ReplayMotionMeasurements$Stickers",
                "ReplayMotionMeasurements$Wallpaper", "ReplayMotionMeasurements$Sound",
            ]
    mixin.write_text(json.dumps(metadata), encoding="utf-8")
    probe = out / "atomprobe.jar"
    with zipfile.ZipFile(probe, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        for path in sorted(classes.rglob("*")):
            if path.is_file() and not path.name.startswith("ExtendReplayRoute"):
                jar.write(path, path.relative_to(classes).as_posix())
    return probe


def with_inputs(launch, mods, history, wallpaper, stickers, shaders_off, ccb_profile, world):
    def staged(args, home):
        if ccb_profile is not None:
            stage_ccb_profile(ccb_profile, home, world)
        for jar in mods:
            destination = home / "mods" / jar.name
            if destination.exists():
                raise FileExistsError(f"duplicate fixture mod filename: {jar.name}")
            shutil.copyfile(jar, destination)
        if history is not None:
            destination = home / "atomchat/client/history" / history.name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(history, destination)
        if wallpaper is not None:
            destination = home / "atomchat/client/wallpaper" / wallpaper.name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(wallpaper, destination)
        if stickers is not None:
            destination = home / "atomchat/client/emotes"
            destination.mkdir(parents=True, exist_ok=True)
            for sticker in sorted(stickers.iterdir()):
                if sticker.is_file():
                    shutil.copyfile(sticker, destination / sticker.name)
        if shaders_off:
            iris = home / "config/iris.properties"
            iris.write_text(iris.read_text(encoding="ascii").replace("enableShaders=true", "enableShaders=false"),
                            encoding="ascii")
        return launch(args, home)
    return staged


def stage_ccb_profile(profile, home, world):
    mods = home / "mods"
    for mod in sorted((profile / "mods").glob("*.jar")):
        if not mod.name.lower().startswith(("atomchat-", "atomlib-")):
            shutil.copyfile(mod, mods / mod.name)

    graphics = ("sodium-options.json", "sodium-mixins.properties", "sodium-extra-options.json",
                "sodium-extra.properties", "immediatelyfast.json", "iris.properties",
                "iris-excluded.json", "entity_model_features.json", "entity_texture_features.json")
    for name in graphics:
        config = profile / "config" / name
        if config.is_file():
            shutil.copyfile(config, home / "config" / name)

    # Reuse installed resource packs and rendering options in the throwaway instance.
    profile_options = {}
    for line in (profile / "options.txt").read_text(encoding="utf-8").splitlines():
        key, separator, value = line.partition(":")
        if separator:
            profile_options[key] = value
    replay_options = home / "options.txt"
    options = dict(line.split(":", 1) for line in replay_options.read_text(encoding="utf-8").splitlines())
    for key in ("renderDistance", "simulationDistance", "mipmapLevels", "graphicsMode", "maxFps",
                "enableVsync", "resourcePacks", "incompatibleResourcePacks", "screenEffectScale"):
        if key in profile_options:
            options[key] = profile_options[key]
    replay_options.write_text("".join(f"{key}:{value}\n" for key, value in options.items()), encoding="utf-8")
    for pack in json.loads(options.get("resourcePacks", "[]")):
        if not pack.startswith("file/"):
            continue
        name = pack.removeprefix("file/")
        if name in {"", ".", ".."} or Path(name).name != name:
            raise ValueError("resource pack must be an immediate member of the installed profile")
        source = profile / "resourcepacks" / name
        if source.is_file():
            destination = home / "resourcepacks" / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
        elif source.is_dir():
            shutil.copytree(source, home / "resourcepacks" / name)
    iris_config = home / "config" / "iris.properties"
    if iris_config.is_file():
        shader = next((line.split("=", 1)[1] for line in iris_config.read_text(encoding="utf-8").splitlines()
                       if line.startswith("shaderPack=")), "")
        if shader and Path(shader).name == shader:
            for file in (profile / "shaderpacks" / shader, profile / "shaderpacks" / (shader + ".txt")):
                if file.is_file():
                    shutil.copyfile(file, home / "shaderpacks" / file.name)
    if world is not None:
        shutil.copytree(world, home / "saves" / "route-world", dirs_exist_ok=True,
                        ignore=shutil.ignore_patterns("session.lock"))


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
    cli.add_argument("--motion-trace", action="store_true", help="replay public chat back and sticker tab clicks with JFR call spans")
    cli.add_argument("--client-config", type=Path, help="seed the isolated client config from this file")
    cli.add_argument("--wallpaper", type=Path, help="seed a static wallpaper in the isolated instance")
    cli.add_argument("--stickers", type=Path, help="copy the sticker fixture into the isolated instance")
    cli.add_argument("--shaders-off", action="store_true", help="disable Iris shaders in the isolated instance")
    cli.add_argument("--ccb-profile", type=Path, help="copy installed CCB mods and graphics settings into the isolate")
    cli.add_argument("--world", type=Path, help="copy this local world into the isolate instead of the flat fixture")
    cli.add_argument("--without-atomchat", action="store_true", help="record a matched closed-world control without the AtomChat mod")
    cli.add_argument("--slow-click-log", action="store_true", help="exercise the real click path with a slow diagnostic appender")
    cli.add_argument("--toggle-shaders-during-replay", action="store_true", help="start with Iris shaders on and disable them in world")
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
    if args.without_atomchat:
        if args.motion_trace or args.driver or args.matrix or args.history_fixture or reload_check or args.steady_settings:
            cli.error("--without-atomchat records the closed-world control only")
        args.expect_marker = "ATOMCHAT_CONTROL_COMPLETE"
    if args.slow_click_log and not args.motion_trace:
        cli.error("--slow-click-log requires --motion-trace")
    if args.toggle_shaders_during_replay and (not args.motion_trace or args.shaders_off):
        cli.error("--toggle-shaders-during-replay requires a motion trace starting with shaders on")
    addons = [path.resolve(strict=True) for path in args.extra_mod]
    wallpaper = args.wallpaper.resolve(strict=True) if args.wallpaper else None
    stickers = args.stickers.resolve(strict=True) if args.stickers else None
    ccb_profile = args.ccb_profile.resolve(strict=True) if args.ccb_profile else None
    world = args.world.resolve(strict=True) if args.world else None
    if world is not None and (ccb_profile is None or not (world / "level.dat").is_file()):
        cli.error("--world requires --ccb-profile and a local save with level.dat")
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
    if args.without_atomchat:
        copy_atomchat = "    shutil.copyfile(jar, home/'mods/atomchat.jar')\n"
        if historical.count(copy_atomchat) != 1:
            raise ValueError("the archived AtomChat copier was not found")
        historical = historical.replace(copy_atomchat, "")
    if ccb_profile is not None:
        fixture_mods = "    shutil.copytree(fixture/'mods', home/'mods', dirs_exist_ok=True)\n"
        if historical.count(fixture_mods) != 1:
            raise ValueError("the archived CCB mod copier was not found")
        historical = historical.replace(fixture_mods, "")
    if args.client_config:
        fixture_reader = "(fixture/'atomchat-client.json')"
        if historical.count(fixture_reader) != 1:
            raise ValueError("the archived CCB config reader was not found")
        historical = historical.replace(fixture_reader, f"Path({str(args.client_config.resolve(strict=True))!r})")
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
    config_path = args.client_config or retest.fixture / "atomchat-client.json"
    config = json.loads(config_path.read_text(encoding="utf-8-sig"))
    reference = json.loads((prior / "run.json").read_text(encoding="utf-8"))
    if args.client_config:
        for key in reference["config_overrides"].keys() & config.keys():
            reference["config_overrides"][key] = config[key]
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
    retest.launch_private = with_inputs(retest.launch_private, addons, history, wallpaper, stickers,
                                       args.shaders_off, ccb_profile, world)
    reference["options"]["guiScale"] = args.scale
    if args.motion_trace or args.without_atomchat:
        reference["options"].update(soundCategory_master="1.0", soundCategory_music="0.0")
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
                     motion_trace=args.motion_trace, shaders_off=args.shaders_off,
                     without_atomchat=args.without_atomchat,
                     slow_click_log=args.slow_click_log,
                     toggle_shaders=args.toggle_shaders_during_replay,
                     ccb_profile=str(ccb_profile) if ccb_profile else None,
                     world_sha256=retest.sha(world / "level.dat") if world else None,
                     wallpaper_sha256=retest.sha(wallpaper) if wallpaper else None,
                     sticker_fixture={p.name: retest.sha(p) for p in sorted(stickers.iterdir()) if p.is_file()} if stickers else {},
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
    probe = args.driver.resolve(strict=True) if args.driver else build_probe(
        retest, java, out, prior, library, args.motion_trace, args.without_atomchat)
    home = retest.scratch / args.label
    jfc = out / "atomchat.jfc"
    jfc_contents = JFC
    if args.motion_trace:
        jfc_contents = JFC.replace("</configuration>", """
  <event name="bench.AtomChatMotionCall"><setting name="enabled">true</setting></event>
  <event name="bench.AtomChatMotionAction"><setting name="enabled">true</setting></event>
  <event name="jdk.GCPhasePause"><setting name="enabled">true</setting></event>
  <event name="jdk.ThreadPark"><setting name="enabled">true</setting><setting name="threshold">2 ms</setting></event>
  <event name="jdk.JavaMonitorEnter"><setting name="enabled">true</setting><setting name="threshold">2 ms</setting></event>
</configuration>""").replace('name="period">10 ms', 'name="period">5 ms')
    jfc.write_text(jfc_contents, encoding="utf-8")
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
            f"-Datomchat.replay.motionTrace={str(args.motion_trace).lower()}",
            f"-Datomchat.replay.copiedWorld={str(world is not None).lower()}",
            f"-Datomchat.replay.slowClickLog={str(args.slow_click_log).lower()}",
            f"-Datomchat.replay.toggleShaders={str(args.toggle_shaders_during_replay).lower()}",
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

    if args.motion_trace:
        log = (out / "launcher.log").read_text(encoding="utf-8", errors="replace")
        if "ATOMCHAT_MOTION_ROUTE_COMPLETE" not in log:
            raise AssertionError("the targeted public chat and sticker route did not finish")
        if args.slow_click_log and "ATOMCHAT_ASYNC_CLICK_VERIFIED" not in log:
            raise AssertionError("opening the emoji panel did not bypass the slow click diagnostic")


if __name__ == "__main__":
    main()
