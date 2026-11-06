"""Steady-state window only (drop the world-load warm-up), plus cap coverage."""
from __future__ import annotations

import re
import statistics
from pathlib import Path

LOG = Path("D:/Myworld/.minecraft/versions/1.21.1-CCB/logs/latest.log")
RX = re.compile(
    r"^\[(?P<t>[0-9]{2}:[0-9]{2}:[0-9]{2})\] .*?frame_pump mode=(?P<m>[ABCD]) "
    r"n=(?P<n>[0-9]+) p50=(?P<p50>[0-9.]+) p95=(?P<p95>[0-9.]+) fps_p50=(?P<fps>[0-9.]+)"
)


def secs(t: str) -> int:
    h, m, s = (int(x) for x in t.split(":"))
    return h * 3600 + m * 60 + s


def main() -> None:
    rows = []
    for line in LOG.read_text(encoding="utf-8", errors="replace").splitlines():
        m = RX.match(line)
        if m:
            rows.append({"t": m.group("t"), "ts": secs(m.group("t")),
                         "p50": float(m.group("p50")), "fps": float(m.group("fps"))})

    warm = secs("20:30:53")   # world finished loading; before this the panel-open samples are warm-up
    end = secs("20:31:41")
    open_ss = [r["p50"] for r in rows if warm <= r["ts"] <= end]
    shut = [r["p50"] for r in rows if secs("20:31:43") <= r["ts"] and r["fps"] >= 390]
    shut_all = [r["p50"] for r in rows if secs("20:31:43") <= r["ts"]]

    def blk(name: str, xs: list[float]) -> str:
        if not xs:
            return f"{name}: (empty)"
        md = statistics.median(xs)
        return (f"{name}: n={len(xs)} median={md:.3f}ms fps={1000 / md:.1f} "
                f"min={min(xs):.3f} max={max(xs):.3f} spread={max(xs) - min(xs):.3f}")

    print(blk("稳态·面板开(全仪表)", open_ss))
    print(blk("稳态·面板关(全部)", shut_all))
    print(blk("稳态·面板关(仅未撞锁)", shut))
    if open_ss and shut_all:
        print(f"差值(相邻时段中位) = {statistics.median(open_ss) - statistics.median(shut_all):.3f} ms")
    capped = [r for r in rows if r["fps"] >= 395]
    print(f"撞 400 锁样本 = {len(capped)} / {len(rows)}")
    open_capped = [r for r in rows if r["fps"] >= 395 and r["ts"] <= end]
    print(f"面板开时撞锁 = {len(open_capped)}")


if __name__ == "__main__":
    main()
