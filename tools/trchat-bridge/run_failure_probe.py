#!/usr/bin/env python3
"""Run the guarded delivery regression on disposable Paper, with no graphical client."""
import argparse
import os
from pathlib import Path
from shutil import copy2, copytree
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]


def wait_for(path, process, token=None, timeout=80):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline and process.poll() is None:
        if path.exists() and (token is None or token in path.read_text(encoding="utf-8", errors="replace")):
            return
        time.sleep(.2)
    raise RuntimeError(f"Missing fixture event: {path}")


def prepare(args):
    server = args.output / "paper"
    (server / "plugins").mkdir(parents=True)
    for relative in ("cache", "versions", "libraries", "plugins/TrChat/channels",
                     "plugins/PlaceholderAPI/expansions"):
        existing = args.fixture_base / relative
        if existing.exists():
            copytree(existing, server / relative)
    for relative in ("eula.txt", "plugins/TrChat/atomchat-channels.yml"):
        copy2(args.fixture_base / relative, server / relative)
    props = args.fixture_base / "server.properties"
    (server / "server.properties").write_text(
        "\n".join(line for line in props.read_text().splitlines()
                  if not line.startswith(("server-ip=", "server-port=", "enable-rcon=", "enable-query=")))
        + f"\nserver-ip=127.0.0.1\nserver-port={args.port}\nenable-rcon=false\nenable-query=false\n",
        encoding="ascii")
    for plugin in args.fixture_base.joinpath("plugins").glob("*.jar"):
        if plugin.name.startswith(("PlaceholderAPI-", "LuckPerms-")):
            copy2(plugin, server / "plugins" / plugin.name)
    copy2(args.bridge, server / "plugins" / "TrChat-2.5.2-atomchat-bridge.jar")
    classes = args.output / "fixture-classes"
    classes.mkdir()
    deps = os.pathsep.join(map(str, [args.bridge, *server.joinpath("libraries").rglob("*.jar")]))
    argfile = args.output / "probe-javac.args"
    source = ROOT / "tools/trchat-bridge/src/test/java/com/atom/chat/trchat/test/FailureFixture.java"
    argfile.write_text("\n".join('"' + arg.replace("\\", "/") + '"' for arg in
                                 ["--release", "17", "-proc:none", "-cp", deps, "-d", str(classes), str(source)]),
                       encoding="utf-8")
    subprocess.run([args.jdk / "bin/javac", f"@{argfile}"], check=True)
    copy2(ROOT / "tools/trchat-bridge/src/test/resources/plugin.yml", classes / "plugin.yml")
    jar = server / "plugins/AtomChatBridgeFailureProbe.jar"
    subprocess.run([args.jdk / "bin/jar", "cf", jar, "-C", classes, "."], check=True)
    return server


def run(args):
    server = prepare(args)
    evidence = args.output
    no_window = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    with (evidence / "server.log").open("wb") as server_log, (evidence / "probe.log").open("wb") as probe_log:
        proc = subprocess.Popen([args.jdk / "bin/java", "-Dfile.encoding=UTF-8", "-XX:ActiveProcessorCount=3",
                                 "-Xmx1024M", "-jar", args.paper, "nogui"], cwd=server, stdin=subprocess.PIPE,
                                stdout=server_log, stderr=subprocess.STDOUT, creationflags=no_window)
        probe = None

        def console(text):
            proc.stdin.write((text + "\n").encode("utf-8"))
            proc.stdin.flush()

        try:
            wait_for(evidence / "server.log", proc, "Done (", 150)
            env = dict(os.environ, ATOMCHAT_CHANNEL_EVIDENCE=str(evidence), ATOMCHAT_CHANNEL_PORT=str(args.port),
                       ATOMCHAT_CHANNEL_SCENARIO="failures", ATOMCHAT_CHANNEL_MC="1.21.1")
            probe = subprocess.Popen(["node", ROOT / "tools/trchat-bridge/protocol_smoke.cjs"], env=env,
                                     stdout=probe_log, stderr=subprocess.STDOUT, creationflags=no_window)
            wait_for(evidence / "protocol-joined", probe)
            console("lp group default permission set trchat.command.channel true")
            for actor in ("BridgeA", "BridgeB", "BridgeD"):
                for channel in ("alpha", "beta"):
                    for flag in ("join", "listen"):
                        console(f"lp user {actor} permission set lab.{channel}.{flag} true")
            time.sleep(1.3)
            console("channel join alpha BridgeD")
            time.sleep(.6)
            (evidence / "protocol-granted").touch()
            wait_for(evidence / "failure-fixture-ready", probe)
            for scenario in ("missing", "oversized", "snapshot", "unregister"):
                console(f"bridge-regression {scenario} BridgeB")
            console("bridge-regression ordinary BridgeD")
            # Keep the final control independent of the tested registration interruption.
            time.sleep(1.5)
            console("bridge-regression healthy BridgeB")
            result = probe.wait(timeout=32)
            print(f"Delivery regression exited {result}: {evidence}")
            return result
        finally:
            if probe is not None and probe.poll() is None:
                probe.terminate()
            if proc.poll() is None:
                console("stop")
                try:
                    proc.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    proc.terminate()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("bridge", "paper", "jdk", "fixture-base", "output"):
        parser.add_argument(f"--{name}", required=True, type=lambda path: Path(path).resolve())
    parser.add_argument("--port", default=25732, type=int)
    raise SystemExit(run(parser.parse_args()))
