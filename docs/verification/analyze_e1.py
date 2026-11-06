"""E1 split: pump samples with an AC panel open (frame_probe nearby) vs without.

The 400 FPS cap is a ceiling, not a measurement: samples at ~2.50 ms are censored.
Everything here is p50 of the frame interval, in ms.
"""
from __future__ import annotations

import json
import re
import statistics
from pathlib import Path

LOG = Path("D:/Myworld/.minecraft/versions/1.21.1-CCB/logs/latest.log")
OUT = Path("D:/AtomChat-Main/build/e0e1-results-20261106")

LINE = re.compile(
    r"^\[(?P<t>[0-9]{2}:[0-9]{2}:[0-9]{2})\] \[Render thread/INFO\]: \[AtomChat\]\[probe\] "
    r"(?P<kind>frame_pump|frame_probe) mode=(?P<mode>[ABCD]) n=(?P<n>[0-9]+) "
    r"p50=(?P<p50>[0-9.]+) p95=(?P<p95>[0-9.]+) fps_p50=(?P<fps>[0-9.]+) max=(?P<max>[0-9.]+)$"
)


def secs(t: str) -> int:
    h, m, s = (int(x) for x in t.split(":"))
    return h * 3600 + m * 60 + s


def main() -> None:
    rows = []
    for line in LOG.read_text(encoding="utf-8", errors="replace").splitlines():
        m = LINE.match(line)
        if m:
            rows.append({
                "t": m.group("t"), "ts": secs(m.group("t")),
                "kind": m.group("kind"), "p50": float(m.group("p50")),
                "p95": float(m.group("p95")), "fps": float(m.group("fps")),
                "max": float(m.group("max")),
            })

    probe_ts = [r["ts"] for r in rows if r["kind"] == "frame_probe"]
    pump = [r for r in rows if r["kind"] == "frame_pump"]

    # panel open <=> a frame_probe exists within +-2 s (both emit every ~600 frames)
    for r in pump:
        r["panelOpen"] = any(abs(r["ts"] - p) <= 2 for p in probe_ts)

    open_s = [r["p50"] for r in pump if r["panelOpen"]]
    shut_s = [r["p50"] for r in pump if not r["panelOpen"]]

    # adjacent pair across the last open->shut transition
    trans = []
    for a, b in zip(pump, pump[1:]):
        if a["panelOpen"] and not b["panelOpen"]:
            trans.append({"t": f'{a["t"]}->{b["t"]}', "open": a["p50"], "shut": b["p50"],
                          "delta": round(a["p50"] - b["p50"], 3)})
        if not a["panelOpen"] and b["panelOpen"]:
            trans.append({"t": f'{a["t"]}->{b["t"]}', "shut": a["p50"], "open": b["p50"],
                          "delta": round(b["p50"] - a["p50"], 3)})

    # pump vs probe agreement in the same second
    agree = []
    by_sec = {}
    for r in rows:
        by_sec.setdefault(r["t"], {})[r["kind"]] = r["p50"]
    for t, v in sorted(by_sec.items()):
        if "frame_pump" in v and "frame_probe" in v:
            agree.append(abs(v["frame_pump"] - v["frame_probe"]))

    capped = [r for r in pump if r["fps"] >= 395]

    out = {
        "pumpSamples": len(pump),
        "panelOpenSamples": len(open_s),
        "panelShutSamples": len(shut_s),
        "panelOpen": {
            "n": len(open_s),
            "median": round(statistics.median(open_s), 3) if open_s else None,
            "min": min(open_s) if open_s else None,
            "max": max(open_s) if open_s else None,
            "fps_median": round(1000 / statistics.median(open_s), 1) if open_s else None,
        },
        "panelShut": {
            "n": len(shut_s),
            "median": round(statistics.median(shut_s), 3) if shut_s else None,
            "min": min(shut_s) if shut_s else None,
            "max": max(shut_s) if shut_s else None,
            "fps_median": round(1000 / statistics.median(shut_s), 1) if shut_s else None,
        },
        "cappedSamples": len(capped),
        "cappedShare": round(len(capped) / len(shut_s), 3) if shut_s else None,
        "delta_median": round(statistics.median(open_s) - statistics.median(shut_s), 3)
        if open_s and shut_s else None,
        "transitions": trans,
        "pumpProbeAbsMedian": round(statistics.median(agree), 3) if agree else None,
        "pumpProbeAbsMax": round(max(agree), 3) if agree else None,
        "pumpProbePairs": len(agree),
    }
    OUT.joinpath("e1-split.json").write_text(json.dumps(out, indent=2), encoding="utf-8")
    print(json.dumps(out, indent=2))


if __name__ == "__main__":
    main()
