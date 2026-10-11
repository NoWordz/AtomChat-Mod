#!/usr/bin/env python3
"""bare_scan.py 的自测：夹具仓库 + 变异，证明守卫红得对、绿得对。

为什么值得有：棘轮守卫唯一的失效方式是「太松」——基线一调就绿、正则一放宽就什么都扫不到。
所以这里逐条钉住：
  1. 基线一致 → 绿；
  2. 塞 `s(999)` → 红（且报文点名文件与增量）；
  3. 删掉 → 复绿；
  4. 塞 `radius(999)` → 不许红（词边界回归：审计实测朴素正则混入 22 处这类假阳性）；
  5. 塞带 `// bare-value: …` 的 `s(998)` → 不许红（显式豁免）；
  6. 整行注释里的 `s(997)` → 不许红（注释引用不该挡构建）；
  7. 基线里没有的新文件出现裸值 → 红（新文件默认 0）；
  8. 基线里多出一条对不上的条目（文件没了）→ 绿但必须点名提醒收紧。
变异全在临时夹具里做，不动工作区。

用法：python3 tools/test_bare_scan.py
"""
from __future__ import annotations

import json
import pathlib
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
TOOL = ROOT / "tools" / "bare_scan.py"
SAMPLE = "shared/src/main/java/com/atom/chat/ui/ActionToast.java"
failures: list[str] = []


def check(name: str, ok: bool, detail: str = "") -> None:
    print(f"  {'PASS' if ok else 'FAIL'}  {name}")
    if not ok:
        failures.append(f"{name}: {detail}")


def run(root: pathlib.Path, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run([sys.executable, str(TOOL), "--root", str(root), *args],
                          capture_output=True, text=True, encoding="utf-8", errors="replace")


def build_fixture(tmp: pathlib.Path) -> pathlib.Path:
    """最小夹具：两个在扫文件 + UiTokens（应被排除）+ 冻结基线。"""
    for rel in (SAMPLE, "shared/src/main/java/com/atom/chat/ui/UiTokens.java"):
        dst = tmp / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(ROOT / rel, dst)
    (tmp / "tools").mkdir(parents=True, exist_ok=True)
    r = run(tmp, "--update")
    assert r.returncode == 0, r.stdout + r.stderr
    return tmp


print("== 夹具 ==")
with tempfile.TemporaryDirectory() as td:
    fx = build_fixture(pathlib.Path(td))
    sample = fx / SAMPLE
    original = sample.read_text(encoding="utf-8")
    base = json.loads((fx / "tools/bare_baseline.json").read_text(encoding="utf-8"))
    print(f"  夹具基线：{base['total']} 处 / {len(base['files'])} 文件")

    check("基线一致 → 绿", run(fx).returncode == 0)

    print("\n== 变异 1：塞 s(999) → 必须红 ==")
    sample.write_text(original.replace("import ", "import ", 1).replace(
        "public final class", "    static float atomchatProbe = s(999);\n\npublic final class", 1),
        encoding="utf-8")
    r = run(fx)
    check("新增裸值被拦下", r.returncode != 0, "守卫居然绿了")
    check("报文点名文件", SAMPLE in r.stdout, r.stdout[-300:])
    check("报文写出增量", "新增 1 处" in r.stdout, r.stdout[-300:])

    print("\n== 变异 2：删掉 → 必须复绿 ==")
    sample.write_text(original, encoding="utf-8")
    check("还原后复绿", run(fx).returncode == 0)

    print("\n== 变异 3：radius(999) → 不许红（词边界） ==")
    sample.write_text(original.replace("public final class",
                                       "    static float atomchatProbe = radius(999);\n\npublic final class", 1),
                      encoding="utf-8")
    r = run(fx)
    check("假阳性不误报", r.returncode == 0, r.stdout[-300:])

    print("\n== 变异 4：s(998) + // bare-value → 不许红（显式豁免） ==")
    sample.write_text(original.replace("public final class",
                                       "    static float atomchatProbe = s(998); // bare-value: 自测夹具豁免\n\npublic final class", 1),
                      encoding="utf-8")
    r = run(fx)
    check("豁免生效", r.returncode == 0, r.stdout[-300:])

    print("\n== 变异 5：整行注释里的 s(997) → 不许红 ==")
    sample.write_text(original.replace("public final class",
                                       "    // 自测夹具：这里提到 s(997) 只是注释\npublic final class", 1),
                      encoding="utf-8")
    r = run(fx)
    check("注释引用不计数", r.returncode == 0, r.stdout[-300:])
    sample.write_text(original, encoding="utf-8")

    print("\n== 变异 6：新文件带裸值 → 必须红（基线里没有它） ==")
    newf = fx / "shared/src/main/java/com/atom/chat/ui/AtomchatNewProbe.java"
    newf.write_text("package com.atom.chat.ui;\npublic final class AtomchatNewProbe {\n"
                    "    static float p = s(5);\n}\n", encoding="utf-8")
    r = run(fx)
    check("新文件被默认 0 拦住", r.returncode != 0, "守卫居然绿了")
    newf.unlink()

    print("\n== 变异 7：基线的余量（文件没了）→ 绿但必须点名提醒收紧 ==")
    stale = json.loads((fx / "tools/bare_baseline.json").read_text(encoding="utf-8"))
    stale["files"]["shared/src/main/java/com/atom/chat/ui/GhostFile.java"] = 3
    stale["total"] = stale["total"] + 3
    (fx / "tools/bare_baseline.json").write_text(json.dumps(stale, ensure_ascii=False, indent=2),
                                                 encoding="utf-8")
    r = run(fx)
    check("余量不拦构建（基线条目是计数目标，不是豁免）", r.returncode == 0, r.stdout[-300:])
    check("但点名 GhostFile 提醒收紧", "GhostFile" in r.stdout, r.stdout[-300:])
    run(fx, "--update")

print("\n== 真实仓库：基线必须与现场一致 ==")
r = subprocess.run([sys.executable, str(TOOL), "--root", str(ROOT)],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
check("真实仓库绿", r.returncode == 0, r.stdout[-400:])

print()
if failures:
    print(f"✗ {len(failures)} 条失败：")
    for f in failures:
        print("  -", f)
    sys.exit(1)
print("✓ 全部变异与基线通过")