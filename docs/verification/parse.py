"""Parse the E0/E1 instrument log into block statistics + adjacent pairing.

Rules per the plan (§3.2): only adjacent pairs, never merge windows from
different moments. Timestamps have 1s resolution, so line order is the clock.
"""
from __future__ import annotations

import json
import re
import statistics
from pathlib import Path

LOG = Path("D:/Myworld/.minecraft/versions/1.21.1-CCB/logs/latest.log")
OUT = Path("D:/AtomChat-Main/build/e0e1-results-20261106")
OUT.mkdir(parents=True, exist_ok=True)

PUMP = re.compile(
    r"^\[(?P<t>[0-9:]{8})\] \[Render thread/INFO\]: \[AtomChat\]\[probe\] frame_pump "
    r"mode=(?P<mode>[ABCD]) n=(?P<n>[0-9]+) p50=(?P<p50>[0-9.]+) p95=(?P<p95>[0-9.]+) "
    r"fps_p50=(?P<fps>[0-9.]+) max=(?P<max>[0-9.]+)$"
)
PROBE = re.compile(
    r"^\[(?P<t>[0-9:]{8})\] \[Render thread/INFO\]: \[AtomChat\]\[probe\] frame_probe "
    r"mode=(?P<mode>[ABCD]) n=(?P<n>[0-9]+) p50=(?P<p50>[0-9.]+) p95=(?P<p95>[0-9.]+) "
    r"fps_p50=(?P<fps>[0-9.]+) max=(?P<max>[0-9.]+)$"
)
MEAS = re.compile(r"\[AtomChat\]\[probe\] measure_switch measure_only=(?P<v>true|false) t=(?P<nt>[0-9]+)")


def parse() -> list[dict]:
    rows = []
    state = False  # diagnosticMeasureOnly; config default false
    for idx, line in enumerate(LOG.read_text(encoding="utf-8", errors="replace").splitlines()):
        m = MEAS.search(line)
        if m:
            state = m.group("v") == "true"
            rows.append({"idx": idx, "kind": "switch", "measureOnly": state})
            continue
        for kind, rx in (("pump", PUMP), ("probe", PROBE)):
            m = rx.match(line)
            if m:
                rows.append({
                    "idx": idx,
                    "kind": kind,
                    "t": m.group("t"),
                    "mode": m.group("mode"),
                    "n": int(m.group("n")),
                    "p50": float(m.group("p50")),
                    "p95": float(m.group("p95")),
                    "max": float(m.group("max")),
                    "fps": float(m.group("fps")),
                    "measureOnly": state,
                })
                break
    return rows


def blocks(rows: list[dict]) -> list[dict]:
    """Split the pump/probe series into measureOnly-state blocks."""
    series = [r for r in rows if r["kind"] in ("pump", "probe")]
    out, cur = [], None
    for r in series:
        if cur is None or cur["measureOnly"] != r["measureOnly"]:
            cur = {"measureOnly": r["measureOnly"], "pump": [], "probe": []}
            out.append(cur)
        cur[r["kind"]].append(r)
    return out


def boundary_pairs(rows: list[dict]) -> list[dict]:
    """Adjacent pairs across a measure_switch: last sample before, first after."""
    pairs = []
    for i, r in enumerate(rows):
        if r["kind"] != "switch":
            continue
        before = next((x for x in reversed(rows[:i]) if x["kind"] == "pump"), None)
        after = next((x for x in rows[i + 1:] if x["kind"] == "pump"), None)
        if before and after:
            pairs.append({
                "from": before["measureOnly"],
                "to": after["measureOnly"],
                "before_p50": before["p50"],
                "after_p50": after["p50"],
                "delta": round(after["p50"] - before["p50"], 3),
            })
    return pairs


def main() -> None:
    rows = parse()
    blks = blocks(rows)
    summary = {"blocks": [], "boundaryPairs": [], "pumpProbeAgreement": []}
    for i, b in enumerate(blks):
        entry = {
            "block": i + 1,
            "measureOnly": b["measureOnly"],
            "pumpSamples": len(b["pump"]),
            "probeSamples": len(b["probe"]),
        }
        if b["pump"]:
            ps = [r["p50"] for r in b["pump"]]
            entry["pump_p50_median"] = round(statistics.median(ps), 3)
            entry["pump_p50_first"] = ps[0]
            entry["pump_p50_last"] = ps[-1]
            entry["pump_p50_min"] = min(ps)
            entry["pump_p50_max"] = max(ps)
            entry["fps_median"] = round(statistics.median([r["fps"] for r in b["pump"]]), 1)
            entry["t_from"] = b["pump"][0]["t"]
            entry["t_to"] = b["pump"][-1]["t"]
        if b["probe"]:
            qs = [r["p50"] for r in b["probe"]]
            entry["probe_p50_median"] = round(statistics.median(qs), 3)
        summary["blocks"].append(entry)

    summary["boundaryPairs"] = boundary_pairs(rows)

    # Cross-calibration: pump vs probe emitted in the same second (AC screen open)
    by_sec: dict[str, dict] = {}
    for r in rows:
        if r["kind"] in ("pump", "probe"):
            by_sec.setdefault(r["t"], {})[r["kind"]] = r["p50"]
    for t, v in sorted(by_sec.items()):
        if "pump" in v and "probe" in v:
            summary["pumpProbeAgreement"].append(
                {"t": t, "pump": v["pump"], "probe": v["probe"], "delta": round(v["pump"] - v["probe"], 3)}
            )

    agree = [abs(x["delta"]) for x in summary["pumpProbeAgreement"]]
    summary["pumpProbeAgreementAbsMedian"] = round(statistics.median(agree), 3) if agree else None
    summary["pumpProbeAgreementAbsMax"] = round(max(agree), 3) if agree else None

    (OUT / "parsed.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
