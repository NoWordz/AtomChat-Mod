#!/usr/bin/env python3
"""棘轮裸值守卫：扫「裸值」（`s(数字)` 字面量），只许减不许增。

【为什么】`s(N)` 是缩放 token 入口，直接写数字 = 绕过 token 体系。2026-10-05 的
《AtomChat-UI-失误清单》里三个按钮家族三套数、11 条家族 mismatch 而测试全绿，
根因就是裸值没人拦。拆分期尤其危险：代码从 AtomChatScreen 搬进 layers/shared
时字面量会跟着搬进来，没有守卫就没人看得见「这一块又多了 12 处」。

【口径】（与阶段 0 审计 5-B 对齐，杜绝两套数字）
  * 正则：`(?<![A-Za-z0-9_])s\\(\\s*-?[0-9]` —— **必须带词边界**。朴素的 `s\\([0-9]`
    会混进 `radius(8)` / `ofSeconds(10)` 这类假阳性（审计实测 22 处）。
  * 范围（去重视图）：`shared/src/main/java` + `layers/mapping/official/src/main/java`。
    platforms/ 下的孪生镜像不扫（同一份逻辑重复计），测试源集不扫，
    `UiTokens.java` 自身不扫（它就是 token 的定义处，里面全是数字）。
  * 计数单位是**出现次数**（一行多处全计），不是行数。
  * 整行注释（首非空白为 `//`、`*`、`/*`）不计 —— 注释里引用 `s(8)` 不该挡构建。
    行内尾注释里的 `s(N)` **照计**：宁可多计（棘轮偏保守）也不漏计。
  * 同一行带 `// bare-value: <理由>` 的行整行豁免（显式声明「这个数字就该是字面量」）。

【棘轮】基线按文件冻结在 `tools/bare_baseline.json`：
  * 实测 > 基线 → 红（新增裸值即拦）
  * 实测 < 基线 → 绿，并提示可以收紧（跑 --update）
  * 基线里没有的新文件有裸值 → 红（新文件默认 0）
  * 基线里有、现在文件没了（或裸值清零）→ **绿**，但单独列出来提醒收紧。
    为什么不红：基线条目是「计数目标」不是「豁免」——文件被删/搬走本身不危险，
    真正危险的「搬到别处」会以新路径 +N 的形式立刻红。

用法：
  python3 tools/bare_scan.py            # 检查（CI/构建门走这条）
  python3 tools/bare_scan.py --list     # 连每个文件的明细一起打印
  python3 tools/bare_scan.py --update   # 重写基线（拆块迁移后允许，commit message 必须写明 +N 的来源）
"""
from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys

SCAN_DIRS = ["shared/src/main/java", "layers/mapping/official/src/main/java"]
EXCLUDE_FILES = {"shared/src/main/java/com/atom/chat/ui/UiTokens.java"}
PATTERN = re.compile(r"(?<![A-Za-z0-9_])s\(\s*-?[0-9]")
EXEMPT_MARK = "// bare-value:"


def is_comment_line(line: str) -> bool:
    s = line.strip()
    return s.startswith(("//", "*", "/*"))


def scan_file(path: pathlib.Path) -> tuple[int, list[str]]:
    """返回 (出现次数, 命中行列表)。"""
    hits = []
    total = 0
    for line in path.read_text(encoding="utf-8", errors="replace").replace("\r\n", "\n").split("\n"):
        if is_comment_line(line) or EXEMPT_MARK in line:
            continue
        n = len(PATTERN.findall(line))
        if n:
            total += n
            hits.append(line.strip())
    return total, hits


def scan(root: pathlib.Path) -> dict[str, int]:
    counts: dict[str, int] = {}
    for d in SCAN_DIRS:
        base = root / d
        if not base.is_dir():
            continue
        for p in sorted(base.rglob("*.java")):
            rel = p.relative_to(root).as_posix()
            if rel in EXCLUDE_FILES:
                continue
            n, _ = scan_file(p)
            if n:
                counts[rel] = n
    return counts


def main() -> int:
    ap = argparse.ArgumentParser(description="裸值（s(数字)）棘轮守卫")
    ap.add_argument("--root", default=None, help="仓库根（默认取本脚本的上一级）")
    ap.add_argument("--list", action="store_true", help="打印每个文件的计数")
    ap.add_argument("--update", action="store_true", help="把当前实测值写成新基线")
    args = ap.parse_args()

    root = pathlib.Path(args.root).resolve() if args.root else pathlib.Path(__file__).resolve().parent.parent
    baseline_path = root / "tools/bare_baseline.json"
    actual = scan(root)
    total = sum(actual.values())

    if args.update:
        data = {
            "_comment": [
                "裸值基线（棘轮）：每文件的 `s(数字)` 出现次数。只许减不许增。",
                "重写：python3 tools/bare_scan.py --update",
                "拆块迁移后允许 +N，但 commit message 必须写明「+N 处系迁移自 AtomChatScreen，非新造」，",
                "且 N 要与该块 git diff 里迁入的 `s(N)` 数逐一对得上。",
                "口径见 tools/bare_scan.py 顶部注释。"
            ],
            "total": total,
            "files": actual,
        }
        # newline='\n' 是硬要求：Windows 上 Python 默认会把 \n 翻成 \r\n，于是同一份基线
        # 在本机与 CI 上产出不同字节 —— 仓库的 blob 策略是 LF（.gitattributes），
        # 工具产物也必须与平台无关。
        baseline_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n",
                                 encoding="utf-8", newline="\n")
        print(f"基线已写入 {baseline_path.relative_to(root).as_posix()}：{len(actual)} 个文件，共 {total} 处")
        return 0

    if not baseline_path.is_file():
        print(f"缺基线文件 {baseline_path} —— 先跑 python3 tools/bare_scan.py --update 冻结一次")
        return 1
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    base_files: dict[str, int] = baseline.get("files", {})
    base_total = baseline.get("total", sum(base_files.values()))

    problems = []
    stale = []
    for rel in sorted(set(actual) | set(base_files)):
        now, was = actual.get(rel, 0), base_files.get(rel, 0)
        if now > was:
            problems.append(f"{rel}: {was} → {now}（新增 {now - was} 处裸值）")
        elif now < was:
            stale.append(f"{rel}: {was} → {now}")
            if args.list:
                print(f"  （可收紧）{rel}: {was} → {now}")

    if args.list:
        print(f"{'文件':70s} {'基线':>6s} {'实测':>6s}")
        for rel in sorted(set(actual) | set(base_files)):
            now, was = actual.get(rel, 0), base_files.get(rel, 0)
            flag = "  ← 增" if now > was else ("  ← 可收紧" if now < was else "")
            print(f"{rel:70s} {was:6d} {now:6d}{flag}")

    print(f"\n裸值：基线 {base_total} 处 / 实测 {total} 处（{len(actual)} 个文件）")
    if problems:
        print("\n✗ 裸值守卫失败：以下文件新增了 `s(数字)` 字面量")
        for p in problems:
            print("  -", p)
        print("\n该做的不是调基线，而是把数字换成 token（UiTokens/…）或写 // bare-value: <理由> 显式豁免。")
        return 1
    if total < base_total:
        print("✓ 通过；实测低于基线，可以跑 --update 收紧棘轮")
    else:
        print("✓ 通过")
    if stale:
        print("注意：以下文件的基线还有余量（文件被删/搬走或裸值已清）—— "
              "不拦构建，但该跑 --update 收紧，别让余量变成以后新增裸值的空间：")
        for s in stale:
            print("  ", s)
    return 0


if __name__ == "__main__":
    sys.exit(main())