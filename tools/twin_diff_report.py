#!/usr/bin/env python3
"""孪生残差报告：`layers/mapping/official/` ↔ `platforms/1.21.1-fabric/` 的内容一致性门。

【为什么需要它】`tools/verify_targets.py` 的孪生检查只验「副本在不在」和「有没有逐字相同」
（逐字相同的该进 shared/）。它**不验内容一致**：fabric 孪生多一行逻辑、改一个常量、
漏跟一次修复，照样全绿。这份脚本补的就是这一格。

【判据】两家族（official=NeoForge/Forge 用的官方名，fabric=Yarn 名）的同一份逻辑
必然因为映射名而字面不同。所以先把**已知映射改名**归一化掉（`tools/twin_residual_alias.json`），
剩下的差异就是「未被归类的东西」——要么是没登记过的映射差异，要么是**真漂移**。

【两类数据文件】
  1. `tools/twin_residual_alias.json`  —— 映射改名规则（yarn 名 → 官方名），两侧都套一遍。
     表是**手工清理**过的：机器归纳出的垃圾规则（例如把 `com` 当 token 的 `FabricLoader -> com`）
     一律不许进表。新增一条 = 声明「这处字面差异只是改名」，必须能被复核。
  2. `tools/twin_residual_declared.json` —— 已声明的结构分歧。**按残差块指纹**登记，每条必填 reason。
     它不是豁免：指纹一变即红、冒出新的残差块即红、台账里多出对不上的条目也红。

【退出码】未归类残差非空 → 1（fail loudly）。台账有陈旧条目 → 1。

用法：
  python3 tools/twin_diff_report.py                 # 检查（默认）
  python3 tools/twin_diff_report.py --verbose       # 连已声明的分歧一起打印细节
  python3 tools/twin_diff_report.py --root DIR      # 换仓库根（自测用）
  python3 tools/twin_diff_report.py --accept --reason "..."   # 登记当前未归类残差
"""
from __future__ import annotations

import argparse
import difflib
import hashlib
import json
import pathlib
import re
import sys

TOKEN = re.compile(r"[A-Za-z_][A-Za-z0-9_]*|\d+|.", re.S)
IDENT = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
LAYER = "layers/mapping/official"
FABRIC_PROJECT = "platforms/1.21.1-fabric"

errors: list[str] = []


def fail(msg: str) -> None:
    errors.append(msg)


def read_json(path: pathlib.Path) -> dict:
    if not path.is_file():
        raise SystemExit(f"缺数据文件：{path}")
    return json.loads(path.read_text(encoding="utf-8"))


def load_lines(path: pathlib.Path) -> list[str]:
    return path.read_text(encoding="utf-8").replace("\r\n", "\n").split("\n")


def is_comment(line: str) -> bool:
    return line.strip().startswith(("//", "*", "/*", "*/"))


def kept(lines: list[str]) -> list[str]:
    """import/package 行整段排除：映射差异本来就集中在 import 区，逐行比它们只会制造噪声。"""
    return [l for l in lines if not l.startswith(("import ", "package "))]


class Aliases:
    """映射改名规则：把两侧的 yarn 名都换成官方名，再逐行比。"""

    def __init__(self, rules: list):
        self.rules = []
        for r in rules:
            yarn, official = r["yarn"], r["official"]
            if not yarn or not official:
                raise SystemExit(f"alias 规则不完整：{r}")
            if yarn == official:
                raise SystemExit(f"alias 规则两侧同名（空转规则不该进表）：{r}")
            # 长规则先套，避免短规则把长名字切坏（TextRenderer 先于 Text）
            self.rules.append((yarn, official, bool(r.get("literal"))))
        self.rules.sort(key=lambda p: -len(p[0]))
        self.hits: dict[str, int] = {}

    def _pattern(self, yarn: str, literal: bool) -> str:
        esc = re.escape(yarn)
        if literal:
            return esc
        # 以 '.' 结尾的包前缀规则不能加尾边界（后面紧跟类名）
        if yarn.endswith("."):
            return r"(?<![A-Za-z0-9_])" + esc
        return r"(?<![A-Za-z0-9_])" + esc + r"(?![A-Za-z0-9_])"

    def normalize(self, text: str, count: bool = False) -> str:
        for yarn, official, literal in self.rules:
            pat = self._pattern(yarn, literal)
            if count:
                n = len(re.findall(pat, text))
                if n:
                    self.hits[f"{yarn} -> {official}"] = self.hits.get(f"{yarn} -> {official}", 0) + n
            text = re.sub(pat, official.replace("\\", "\\\\"), text)
        return text


def fingerprint(a_lines: list[str], b_lines: list[str]) -> str:
    payload = "\x1e".join(a_lines) + "\x00" + "\x1e".join(b_lines)
    return hashlib.sha1(payload.encode("utf-8")).hexdigest()[:12]


def twin_pairs(root: pathlib.Path) -> list[tuple[str, pathlib.Path, pathlib.Path]]:
    path_alias = read_json(root / "versions/mapping-aliases.json").get(LAYER, {})
    base = root / LAYER
    out = []
    for p in sorted(base.rglob("*.java")):
        rel = p.relative_to(base).as_posix()
        twin = root / FABRIC_PROJECT / path_alias.get(rel, rel)
        out.append((rel, p, twin))
    return out


def residual_blocks(rel: str, a: pathlib.Path, b: pathlib.Path, aliases: Aliases,
                    count: bool) -> list[dict]:
    if not b.is_file():
        fail(f"{rel}：fabric 侧没有孪生副本（这条本应由 verify_targets.py 先拦下）")
        return []
    na = [aliases.normalize(l, count) for l in kept(load_lines(a))]
    nb = [aliases.normalize(l, count) for l in kept(load_lines(b))]
    sm = difflib.SequenceMatcher(None, na, nb, autojunk=False)
    blocks = []
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == "equal":
            continue
        A, B = na[i1:i2], nb[j1:j2]
        code = sum(1 for l in A + B if l.strip() and not is_comment(l))
        comment = sum(1 for l in A + B if is_comment(l))
        blocks.append({
            "kind": tag, "fingerprint": fingerprint(A, B),
            "code_lines": code, "comment_lines": comment,
            "a": A, "b": B,
        })
    return blocks


def main() -> int:
    ap = argparse.ArgumentParser(description="孪生残差报告（official ↔ fabric）")
    ap.add_argument("--root", default=None, help="仓库根（默认取本脚本的上一级）")
    ap.add_argument("--verbose", action="store_true", help="连已声明的分歧也打印细节")
    ap.add_argument("--accept", action="store_true", help="把当前未归类残差登记进台账")
    ap.add_argument("--reason", default="", help="--accept 时写入的 reason（必填，且要写清谁拍的板/依据）")
    ap.add_argument("--kind", default="code",
                    choices=["code", "comment", "loader-seam", "test-code", "mixin-annotations"],
                    help="--accept 时写入的分歧类别")
    ap.add_argument("--only", default=None, help="--accept 只登记这一个文件（层内相对路径）")
    ap.add_argument("--summary", action="store_true", help="只打印汇总")
    args = ap.parse_args()

    root = pathlib.Path(args.root).resolve() if args.root else pathlib.Path(__file__).resolve().parent.parent
    alias_data = read_json(root / "tools/twin_residual_alias.json")
    declared_path = root / "tools/twin_residual_declared.json"
    declared_data = read_json(declared_path)
    declared = declared_data.get("declared", {})

    aliases = Aliases(alias_data.get("rules", []))
    pairs = twin_pairs(root)
    if not pairs:
        raise SystemExit(f"{root} 下没有找到 {LAYER} 的孪生对")

    undeclared: list[tuple[str, dict]] = []
    matched: set[tuple[str, str]] = set()
    declared_code = 0
    per_file = []
    for rel, a, b in pairs:
        blocks = residual_blocks(rel, a, b, aliases, count=not args.accept)
        if not blocks:
            continue
        known = {d["fingerprint"]: d for d in declared.get(rel, [])}
        per_file.append((rel, blocks, known))
        for blk in blocks:
            d = known.get(blk["fingerprint"])
            if d is None:
                undeclared.append((rel, blk))
            else:
                matched.add((rel, blk["fingerprint"]))
                if d.get("kind") == "code":
                    declared_code += 1

    stale = [(rel, d) for rel, entries in declared.items() for d in entries
             if (rel, d["fingerprint"]) not in matched]

    if args.accept:
        if not args.reason.strip():
            raise SystemExit("--accept 必须带 --reason「为什么这处差异不是漂移」——台账不接受无理由条目")
        added = 0
        for rel, blk in undeclared:
            if args.only and rel != args.only:
                continue
            entries = declared.setdefault(rel, [])
            entries.append({"fingerprint": blk["fingerprint"], "kind": args.kind,
                            "reason": args.reason.strip(),
                            "sample_a": (blk["a"][0][:120] if blk["a"] else ""),
                            "sample_b": (blk["b"][0][:120] if blk["b"] else "")})
            added += 1
        # newline='\n'：台账的字节不能随平台变（Windows 上 Python 默认写 \r\n，
        # 而仓库 blob 策略是 LF）—— 否则本机一次 --accept 就会让整个文件变成 CRLF 差异。
        declared_path.write_text(json.dumps(declared_data, ensure_ascii=False, indent=2) + "\n",
                                 encoding="utf-8", newline="\n")
        print(f"已登记 {added} 条残差块（kind={args.kind}）；"
              f"陈旧条目 {len(stale)} 条需另行清理（--prune）")
        for rel, d in stale:
            print(f"  陈旧：{rel} {d['fingerprint']} —— {d.get('reason','')[:60]}")
        return 0

    if not args.summary:
        print(f"孪生对：{len(pairs)} 对（{LAYER} ↔ {FABRIC_PROJECT}）")
        print(f"alias 规则：{len(aliases.rules)} 条")
        if per_file:
            print(f"\n有残差的文件：{len(per_file)} 对")
            for rel, blocks, known in per_file:
                code = sum(b["code_lines"] for b in blocks)
                cmt = sum(b["comment_lines"] for b in blocks)
                unk = sum(1 for b in blocks if b["fingerprint"] not in known)
                print(f"  {rel}: 块={len(blocks)} 代码行={code} 注释行={cmt} 未归类={unk}")
        else:
            print("\n有残差的文件：0 对")

        if per_file:
            verdict = "未归类残差" if undeclared else ""
            show = undeclared if not args.verbose else [(r, b) for r, bs, _ in per_file for b in bs]
            for rel, blk in show:
                mark = "未归类" if (rel, blk["fingerprint"]) in [(r, b["fingerprint"]) for r, b in undeclared] else "已声明"
                print(f"\n--- [{mark}] {rel}  {blk['kind']}  指纹={blk['fingerprint']}"
                      f"  代码={blk['code_lines']} 注释={blk['comment_lines']}")
                for l in blk["a"]:
                    print(f"    official| {l}")
                for l in blk["b"]:
                    print(f"    fabric  | {l}")
            if verdict:
                print(f"\n✗ {len(undeclared)} 个残差块未被归类 —— 要么加 alias（纯改名），"
                      f"要么用 --accept --reason 登记（结构分歧）")

    print(f"\n汇总：孪生对 {len(pairs)}，有残差 {len(per_file)}，"
          f"已声明块 {len(matched)}（其中 kind=code {declared_code}），"
          f"未归类块 {len(undeclared)}，陈旧台账条目 {len(stale)}")
    if aliases.hits and args.verbose:
        print("\nalias 规则命中次数（0 命中的规则可以考虑删掉）：")
        for k, v in sorted(aliases.hits.items(), key=lambda kv: -kv[1]):
            print(f"  {v:5d}x  {k}")

    for rel, blk in undeclared:
        fail(f"{rel}：未归类残差块（指纹 {blk['fingerprint']}）")
    for rel, d in stale:
        fail(f"{rel}：台账条目 {d['fingerprint']} 已对不上任何残差 —— 太宽松的台账和没有台账一样危险")

    if errors:
        print("\n=== 失败 ===")
        for e in errors:
            print(" -", e)
        return 1
    print("\n孪生内容一致（差异全部可归类）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())