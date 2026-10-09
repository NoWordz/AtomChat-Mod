#!/usr/bin/env python3
"""生成并校验平台接缝清单 shared/PLATFORM-SEAMS.md。

【这份清单是什么】`platforms/<目标>/` 下的每一个 java 文件，按**共用代码引不引用它**
分成两组：

  甲、共用代码引用了的 —— 必须在每个目标上提供同名类型，否则共用层编不过（这是接缝本身）
  乙、平台内部的 —— 只有该目标自己的代码用它

分组的依据是「`shared/` 或 `layers/` 里有没有出现这个名字」。这是**编译期事实**，
不是判断：甲组少一个类型，那个目标就编不过。清单写出来是为了让人一眼看到接缝在哪，
以及**新增目标时要实现哪些东西**。

【判据的实现】用简单名的词边界匹配，而不是解析 import —— 共用层引用平台类型有两
种写法：`import` 全限定名，或同包下的简单名。两者都表现为「共用层某处的文本里出现了
这个类的简单名」，所以词边界匹配同时覆盖，且不需要编译器。代价是可能把注释里的提及
也算进来（偏保守：宁可多列，不可漏列 —— 漏列的后果是新增目标时少实现一个类型）。

用法：
  python3 tools/gen_seams.py            # 重新生成 shared/PLATFORM-SEAMS.md
  python3 tools/gen_seams.py --check    # 只校验：与磁盘上的清单不一致就红（CI 用）
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOC = ROOT / "shared/PLATFORM-SEAMS.md"
SRC_ROOTS = ["platforms"]
SHARED_ROOTS = ["shared/src", "layers"]


def java_files(base: pathlib.Path):
    """只认源码根 `src/<...>/java` 下的 .java。

    不这样过滤的话，`platforms/<目标>/build/` 里生成出来的 .java（ForgeGradle 会写、
    本机有而 CI 全新检出时没有）也会进清单 —— 表现是「本地过、CI 红」。
    """
    out = []
    for p in base.rglob("*.java"):
        parts = p.parts
        if any(parts[i] == "src" and parts[i + 2] == "java" for i in range(len(parts) - 2)):
            out.append(p)
    return out


def fqn_of(path: pathlib.Path) -> str:
    text = path.read_text(encoding="utf-8", errors="replace")
    m = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
    pkg = m.group(1) if m else ""
    return f"{pkg}.{path.stem}" if pkg else path.stem


def root_of(path: pathlib.Path) -> str:
    """取相对某个源根的路径（src/main/java 或 src/test/java）。"""
    parts = path.parts
    for i in range(len(parts) - 2):
        if parts[i] == "src" and parts[i + 2] == "java":
            return "/".join(parts[i:i + 3])
    return "src/main/java"


def collect_platform_classes():
    out = []
    for root in SRC_ROOTS:
        base = ROOT / root
        if not base.is_dir():
            continue
        for target_dir in sorted(p for p in base.iterdir() if p.is_dir()):
            for f in java_files(target_dir):
                out.append((target_dir.name, root_of(f), fqn_of(f), f))
    return out


def shared_corpus() -> str:
    chunks = []
    for root in SHARED_ROOTS:
        base = ROOT / root
        if base.is_dir():
            for f in java_files(base):
                chunks.append(f.read_text(encoding="utf-8", errors="replace"))
    return "\n".join(chunks)


def classify(classes, corpus: str):
    referenced, internal = [], []
    for target, src_root, fqn, path in classes:
        simple = fqn.rsplit(".", 1)[-1]
        # 词边界匹配：避开 FooBar 里的 Foo，也避开字符串里的偶然子串
        hit = re.search(rf"(?<![\w.]){re.escape(simple)}(?![\w])", corpus) is not None
        (referenced if hit else internal).append((target, src_root, fqn))
    return referenced, internal


def render(referenced, internal) -> str:
    lines = []
    lines.append("# 平台接缝清单")
    lines.append("")
    lines.append("> **这是生成物**，由 `tools/gen_seams.py` 扫描 `platforms/` 与 `shared/`、`layers/` 产出，")
    lines.append("> CI 校验它与代码是否同步（`python3 tools/gen_seams.py --check`）。手改会在下一次校验里红。")
    lines.append("")
    lines.append("`platforms/<目标>/` 下每个 java 文件属于下面两组之一，依据是**共用代码引不引用它**：")
    lines.append("")
    lines.append("| 组 | 含义 | 新增一个目标时 |")
    lines.append("|---|---|---|")
    lines.append("| **甲：共用代码引用了的** | `shared/` 或某个共享层里出现了这个类型 | **必须提供同名类型**，否则共用层编不过 |")
    lines.append("| **乙：平台内部的** | 只有该目标自己的代码用它 | 不必提供 |")
    lines.append("")
    lines.append("分组是编译期事实：甲组少一个类型，那个目标当场编不过。判据用简单名的词边界匹配")
    lines.append("（覆盖 import 全限定名与同包简单名两种写法），偏保守 —— 注释里的提及也算，宁可多列。")
    lines.append("")
    lines.append(f"当前：甲组 **{len(referenced)}** 个，乙组 **{len(internal)}** 个。")
    lines.append("")
    lines.append("## 甲：共用代码引用了的（接缝）")
    lines.append("")
    lines.append("| 类型 | 出现的目标 |")
    lines.append("|---|---|")
    for fqn in sorted({f for _, _, f in referenced}):
        targets = sorted({t for t, _, f in referenced if f == fqn})
        lines.append(f"| `{fqn}` | {', '.join(targets)} |")
    lines.append("")
    lines.append("## 乙：平台内部的")
    lines.append("")
    lines.append("| 目标 | 类型 |")
    lines.append("|---|---|")
    for target, _, fqn in sorted(internal):
        lines.append(f"| {target} | `{fqn}` |")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    referenced, internal = classify(collect_platform_classes(), shared_corpus())
    text = render(referenced, internal)
    check = "--check" in sys.argv
    if check:
        current = DOC.read_text(encoding="utf-8", errors="replace") if DOC.is_file() else ""
        if current.replace("\r\n", "\n") != text:
            print("✘ shared/PLATFORM-SEAMS.md 与代码不同步 —— 跑 python3 tools/gen_seams.py 重新生成",
                  file=sys.stderr)
            return 1
        print(f"接缝清单与代码同步（甲 {len(referenced)} / 乙 {len(internal)}）")
        return 0
    DOC.parent.mkdir(parents=True, exist_ok=True)
    # newline='\n'：产物行尾必须与平台无关。以前这里把 \n 换成 \r\n 再以 newline="" 写盘，
    # 于是本机（Windows）生成一次就把这份文档变成 CRLF 工作区态，而仓库的 blob 策略是 LF
    # （.gitattributes）—— 同一个文件在不同机器上呈现两种行尾，读 diff 的人只会困惑。
    # --check 那一侧本来就做了 \r\n -> \n 归一化，所以它两种行尾都认，不必动。
    DOC.write_text(text, encoding="utf-8", newline="\n")
    print(f"已生成 {DOC.relative_to(ROOT)}：甲 {len(referenced)} 个，乙 {len(internal)} 个")
    return 0


if __name__ == "__main__":
    sys.exit(main())
