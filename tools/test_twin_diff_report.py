#!/usr/bin/env python3
"""twin_diff_report.py 的自测：夹具仓库 + 变异，逐条验证「红得对、绿得对」。

为什么值得有：这个工具的判据是「差异能不能被归类」，而归类数据是两份手写的 JSON。
**一份把什么都归类的表和一个永远绿的门是同义词** —— 所以这里必须证明：
  1. 纯映射改名 → 绿（不误报）；
  2. 往 fabric 孪生塞一行逻辑 → 红（这正是 verify_targets 拦不住的那一格）；
  3. 改动「已声明」的分歧块 → 红（指纹变了，声明不是永久豁免）；
  4. 已声明块消失 → 红（陈旧条目 = 比没有台账更危险）；
  5. --accept 不带 reason → 拒绝执行；
  6. 真实仓库当前是绿的（把数据文件和现场绑在一起）。

变异全部在**临时夹具仓库**里做（拷真文件 + 拷数据文件 + --root 指过去），
不动工作区里的任何被跟踪文件。

用法：python3 tools/test_twin_diff_report.py
"""
from __future__ import annotations

import json
import pathlib
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
TOOL = ROOT / "tools" / "twin_diff_report.py"
CLEAN_REL = "src/main/java/com/atom/chat/chat/ChatClassifier.java"
DECLARED_REL = "src/main/java/com/atom/chat/text/RichText.java"

failures: list[str] = []


def check(name: str, ok: bool, detail: str = "") -> None:
    print(f"  {'PASS' if ok else 'FAIL'}  {name}")
    if not ok:
        failures.append(f"{name}: {detail}")


def build_fixture(tmp: pathlib.Path, rels: list[str]) -> pathlib.Path:
    """造一个最小仓库：数据文件 + 指定孪生对（其余文件不存在 → 不参与比对）。"""
    (tmp / "versions").mkdir(parents=True, exist_ok=True)
    (tmp / "tools").mkdir(parents=True, exist_ok=True)
    shutil.copy(ROOT / "versions/mapping-aliases.json", tmp / "versions/mapping-aliases.json")
    for f in ("twin_residual_alias.json", "twin_residual_declared.json"):
        shutil.copy(ROOT / "tools" / f, tmp / "tools" / f)
    # 台账只保留夹具里出现的文件，否则其余条目会被判成陈旧
    dpath = tmp / "tools" / "twin_residual_declared.json"
    data = json.loads(dpath.read_text(encoding="utf-8"))
    data["declared"] = {k: v for k, v in data["declared"].items() if k in rels}
    dpath.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for rel in rels:
        for side in ("layers/mapping/official", "platforms/1.21.1-fabric"):
            src = ROOT / side / rel
            dst = tmp / side / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy(src, dst)
    return tmp


def run(root: pathlib.Path, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run([sys.executable, str(TOOL), "--root", str(root), *args],
                          capture_output=True, text=True, encoding="utf-8", errors="replace")


def fabric_copy(fixture: pathlib.Path, rel: str) -> pathlib.Path:
    return fixture / "platforms/1.21.1-fabric" / rel


print("== 夹具：一个干净对（ChatClassifier）+ 一个有已声明分歧的对（RichText） ==")
with tempfile.TemporaryDirectory() as td:
    fx = build_fixture(pathlib.Path(td), [CLEAN_REL, DECLARED_REL])

    r = run(fx)
    check("基线（夹具）绿", r.returncode == 0, r.stdout[-500:] + r.stderr[-300:])

    print("\n== 变异 1：纯映射改名（fabric 侧写成官方名 token）→ 必须仍然绿 ==")
    target = fabric_copy(fx, CLEAN_REL)
    original = target.read_text(encoding="utf-8")
    # yarn 侧写的是 getDisplayName()（官方名 getTabListDisplayName()）；把它换成官方名，
    # 在「同一段逻辑的两套名字」这个判据下不算差异 → alias 表应当把它归一掉。
    mutated = original.replace("info.getDisplayName()", "info.getTabListDisplayName()", 1)
    check("变异前提：确实改到了字节", mutated != original, "没找到 info.getDisplayName()")
    target.write_text(mutated, encoding="utf-8")
    r = run(fx)
    check("映射改名不误报", r.returncode == 0, r.stdout[-600:])
    target.write_text(original, encoding="utf-8")

    print("\n== 变异 2：往 fabric 孪生塞一行逻辑 → 必须红 ==")
    lines = original.split("\n")
    idx = next(i for i, l in enumerate(lines) if l.startswith("public final class"))
    lines.insert(idx + 1, "    static int atomchatInjectedProbe = 1;")
    target.write_text("\n".join(lines), encoding="utf-8")
    r = run(fx)
    check("多一行逻辑被拦下", r.returncode != 0, "工具居然绿了")
    check("报文点名了文件", CLEAN_REL in r.stdout, r.stdout[-300:])
    check("报文说了「未归类」", "未归类" in r.stdout, r.stdout[-300:])
    target.write_text(original, encoding="utf-8")
    check("还原后复绿", run(fx).returncode == 0, "还原失败")

    print("\n== 变异 3：改动「已声明」的分歧块 → 必须红（声明不是永久豁免） ==")
    declared_target = fabric_copy(fx, DECLARED_REL)
    dorig = declared_target.read_text(encoding="utf-8")
    dmut = dorig.replace("return new RichText(out, rootStyle);",
                         "return new RichText(mergeRuns(out), rootStyle);", 1)
    check("变异前提：确实改到了字节", dmut != dorig, "没找到 splice 的 return")
    declared_target.write_text(dmut, encoding="utf-8")
    r = run(fx)
    check("已声明块被改动 → 红", r.returncode != 0, "工具居然绿了")
    check("并报出陈旧台账条目", "陈旧" in r.stdout or "对不上" in r.stdout, r.stdout[-400:])
    declared_target.write_text(dorig, encoding="utf-8")

    print("\n== 变异 4：--accept 不带 reason → 拒绝 ==")
    lines = dorig.split("\n")
    idx = next(i for i, l in enumerate(lines) if l.startswith("public final class"))
    lines.insert(idx + 1, "    static int atomchatInjectedProbe = 2;")
    declared_target.write_text("\n".join(lines), encoding="utf-8")
    r = run(fx, "--accept")
    check("无理由登记被拒", r.returncode != 0 and "reason" in (r.stdout + r.stderr), r.stdout[-300:])

    print("\n== 变异 5：--accept --reason 之后复绿，且台账写入理由 ==")
    r = run(fx, "--accept", "--only", DECLARED_REL, "--kind", "code", "--reason", "自测夹具：故意注入的一行")
    check("登记成功", r.returncode == 0, r.stdout[-300:])
    r = run(fx)
    check("登记后复绿", r.returncode == 0, r.stdout[-400:])
    data = json.loads((fx / "tools/twin_residual_declared.json").read_text(encoding="utf-8"))
    reasons = [e["reason"] for e in data["declared"].get(DECLARED_REL, [])]
    check("台账里有带理由的新条目", any("自测夹具" in x for x in reasons), str(reasons)[:300])

print("\n== 真实仓库：数据文件与现场绑定，当前必须绿 ==")
r = subprocess.run([sys.executable, str(TOOL), "--summary"], capture_output=True,
                   text=True, encoding="utf-8", errors="replace", cwd=ROOT)
check("真实仓库绿", r.returncode == 0, r.stdout[-600:])
check("未归类块为 0", "未归类块 0" in r.stdout, r.stdout[-300:])

print()
if failures:
    print(f"✗ {len(failures)} 条失败：")
    for f in failures:
        print("  -", f)
    sys.exit(1)
print("✓ 全部变异与基线通过")