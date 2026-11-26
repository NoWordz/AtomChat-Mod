"""Summarize raw isolate JFR and render-loop CSV without mixing CPU and GPU scopes."""
import argparse
from collections import defaultdict
import csv
import json
import math
import os
from pathlib import Path
import subprocess


def stats(rows, unit):
    if not rows:
        return {"n": 0}
    rows = sorted(rows)
    result = {"n": len(rows), "unit": unit, "max": round(rows[-1], 3)}
    result.update({f"p{p}": round(rows[math.ceil(len(rows) * p / 100) - 1], 3) for p in (50, 95, 99)})
    return result


def export_jfr(folder, jfr):
    events = ("bench.AtomChatReplayCpu,bench.AtomChatReplayGpu,bench.AtomChatReplayFrameInterval,"
              "com.atomchat.PanelSegments,com.atomchat.ScreenLifecycle,com.atomchat.Navigation")
    raw = subprocess.check_output([str(jfr), "print", "--json", "--events", events, str(folder / "frames.jfr")])
    (folder / "events.json").write_bytes(raw)
    return json.loads(raw)["recording"]["events"]


def phase(row):
    scenario, elapsed = row["scene"], row["sceneNanos"]
    if "-" in scenario:
        # Leave out tier/scale population and the screenshot readback at 1.1 s.
        if elapsed < 450_000_000:
            return scenario + "-shift"
        return scenario if elapsed < 1_100_000_000 else None
    if scenario == "startup":
        return None
    if elapsed < 1_000_000_000 and scenario in {"appearance", "media", "text", "empty"}:
        return scenario + "-open"
    if scenario == "appearance":
        if 2_000_000_000 <= elapsed < 4_400_000_000:
            return "appearance-steady"
        if elapsed >= 4_500_000_000:
            return "appearance-navigation"
    if scenario == "empty" and 2_000_000_000 <= elapsed < 4_000_000_000:
        return "empty-steady"
    if elapsed >= 6_000_000_000:
        return scenario
    return None


def iso_ms(value):
    return float(value[2:-1]) * 1_000 if value.endswith("S") and value.startswith("PT") else None


def summarize(folder, jfr):
    summary = {}
    frames = defaultdict(list)
    with (folder / "frames.csv").open(newline="", encoding="utf-8") as file:
        for row in csv.DictReader(file):
            frames[row["scene"]].append(int(row["interval_ns"]) / 1e6)
    for scene, intervals in frames.items():
        summary[scene] = {"render_loop_ms": stats(intervals, "ms"),
                          "intervals_over_16_67ms": sum(t > 16.67 for t in intervals),
                          "intervals_over_33_33ms": sum(t > 33.33 for t in intervals)}
    events = export_jfr(folder, jfr)
    gpu = {(v["scope"], v["frameId"]): v["elapsedNanos"] for event in events
           if event["type"] == "bench.AtomChatReplayGpu" for v in (event["values"],)}
    segments = {event["values"]["frameId"]: event["values"] for event in events
                if event["type"] == "com.atomchat.PanelSegments"}
    by_scene = defaultdict(lambda: defaultdict(list))
    lifecycle = []
    for event in events:
        row = event["values"]
        if event["type"] == "com.atomchat.ScreenLifecycle":
            lifecycle.append({"stage": row["stage"], "ms": iso_ms(row["elapsedNanos"]), "success": row["success"]})
        if event["type"] == "bench.AtomChatReplayFrameInterval":
            scene = phase(row)
            if scene is not None:
                by_scene[scene]["render_loop_ms"].append(row["elapsedNanos"] / 1e6)
        if event["type"] != "bench.AtomChatReplayCpu" or not row["rendered"]:
            continue
        scene = phase(row)
        if scene is None:
            continue
        scope, frame_id = row["scope"], row["frameId"]
        metrics = by_scene[scene]
        metrics[f"{scope}_cpu"].append(row["elapsedNanos"] / 1e6)
        if row["allocatedBytes"] >= 0:
            metrics[f"{scope}_alloc"].append(row["allocatedBytes"] / 1024)
        gpu_ns = gpu.get((scope, frame_id))
        if gpu_ns is not None:
            metrics[f"{scope}_gpu"].append(gpu_ns / 1e6)
        if scope == "panel" and frame_id in segments:
            for key in ("blurNanos", "drawNanos", "listNanos", "bridgeEnterNanos", "bridgeExitNanos"):
                duration = segments[frame_id].get(key)
                if duration is not None:
                    duration_ms = iso_ms(duration)
                    if duration_ms is not None and duration_ms >= 0:
                        metrics[key].append(duration_ms)
    for scene, metrics in by_scene.items():
        record = summary.setdefault(scene, {})
        if "render_loop_ms" in metrics:
            record["intervals_over_16_67ms"] = sum(t > 16.67 for t in metrics["render_loop_ms"])
            record["intervals_over_33_33ms"] = sum(t > 33.33 for t in metrics["render_loop_ms"])
        record.update({
            key: stats(values, "KiB" if key.endswith("_alloc") else "ms")
            for key, values in sorted(metrics.items())
        })
    summary["screen_lifecycle"] = lifecycle
    (folder / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folders", nargs="+", type=Path)
    args = parser.parse_args()
    jfr = Path(os.environ["JAVA_HOME"]) / "bin/jfr.exe"
    for folder in args.folders:
        summarize(folder, jfr)


if __name__ == "__main__":
    main()
