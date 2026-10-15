"""产物级断言：绿灯只证明零件接上了，这些断言看的是 **jar 里面**。

单测跑的是类路径，构建闸盯的是源码目录与 AP 输出 —— 两者都拦不住「打包这一步
少塞了一个嵌套 jar」或者「重定位只做了一半」。这些断言补的就是那一格：

  1. 内嵌的 FlatLaf 是**重定位后**的那一份（而且确实内嵌了）；
  2. 我们自己的类不再引用公共包 `com.formdev.flatlaf`（重定位的全部意义在这里）；
  3. 内嵌那份带着 JarJar 要求的 `Automatic-Module-Name`；
  4. Skija 共享库和指定平台的一个原生包都已内嵌；禁止混入其他系统的原生包。

用法：
    python3 tools/verify_jars.py --platform windows-x64 platforms/1.21.1-fabric
    python3 tools/verify_jars.py --platform linux-x64 platforms/1.21.1-fabric

取件规则与 CI 里那一步完全一致（不要 `-sources`、不要 `-slim`），并且**必须恰好一个**：
产物名有歧义时宁可红，也不要挑一个自己觉得对的。
（CI 是干净检出，不会有多余产物；本机反复构建会在 `build/libs` 里堆下旧版本号的 jar，
先清掉再跑 —— 这条规则是有意严格的。）
"""
import argparse
import io
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NESTED_PREFIXES = ("META-INF/jars/", "META-INF/jarjar/")
SHADED = "com/atom/chat/shaded/flatlaf/"
PLAIN = b"com/formdev/flatlaf"
MODULE_NAME = "Automatic-Module-Name: com.atom.chat.shaded.flatlaf"
PLATFORMS = ("windows-x64", "linux-x64")

failures = []


def check(name, ok, detail=""):
    print(f"[{'ok' if ok else 'FAIL'}] {name}{(' -- ' + detail) if detail else ''}")
    if not ok:
        failures.append(name)


def find_jar(project, platform):
    build = "build/linux-x64" if platform == "linux-x64" else "build"
    libs = ROOT / project / build / "libs"
    jars = [p for p in sorted(libs.glob("*.jar"))
            if "sources" not in p.name and "-slim" not in p.name]
    return jars


def verify(project, platform="windows-x64"):
    print(f"\n=== {project} ===")
    candidate = Path(project)
    jars = [candidate] if candidate.suffix == ".jar" and candidate.is_file() else find_jar(project, platform)
    if len(jars) != 1:
        check(f"{project}: 恰好一个发布 jar", False,
              f"找到 {len(jars)} 个: {[p.name for p in jars]}")
        return
    jar_path = jars[0]
    check(f"{project}: 恰好一个发布 jar", True,
          f"{jar_path.name} ({jar_path.stat().st_size} bytes)")

    check(f"{project}: 文件名与平台一致",
          ("-linux-x64" in jar_path.stem) == (platform == "linux-x64"), jar_path.name)

    with zipfile.ZipFile(jar_path) as jar:
        names = jar.namelist()
        nested = [n for n in names if n.startswith(NESTED_PREFIXES) and n.endswith(".jar")]
        flatlaf_nested = [n for n in nested if "flatlaf" in n.lower()]
        check(f"{project}: 重定位后的 FlatLaf 已内嵌", bool(flatlaf_nested),
              ", ".join(flatlaf_nested) or f"内嵌的有: {nested}")

        jarjar = any(n.startswith("META-INF/jarjar/") for n in nested)
        registered_nested = set(nested)
        if jarjar:
            metadata_path = "META-INF/jarjar/metadata.json"
            try:
                metadata = json.loads(jar.read(metadata_path))
                if not isinstance(metadata, dict) or not isinstance(metadata.get("jars"), list):
                    raise ValueError("invalid JarJar metadata structure")
                registered_nested = {
                    item["path"] for item in metadata.get("jars", [])
                    if isinstance(item, dict) and isinstance(item.get("path"), str)
                }
                check(f"{project}: JarJar metadata is valid", True)
            except (KeyError, json.JSONDecodeError, TypeError, ValueError):
                registered_nested = set()
                check(f"{project}: JarJar metadata is valid", False, metadata_path)

        for want in ("skija-shared", "skija-" + platform):
            matches = [n for n in nested if want in n]
            registered_matches = [n for n in matches if n in registered_nested]
            check(f"{project}: 内嵌并登记 {want}", len(registered_matches) == 1,
                  ", ".join(matches) or f"内嵌的有: {nested}")

        types_nested = [n for n in nested
                        if Path(n).name.startswith("types-") and n in registered_nested]
        has_types = False
        for nested_name in types_nested:
            with zipfile.ZipFile(io.BytesIO(jar.read(nested_name))) as types_jar:
                has_types |= "io/github/humbleui/types/Rect.class" in types_jar.namelist()
        check(f"{project}: 内嵌 HumbleUI types", has_types, ", ".join(types_nested))

        native_jars = [n for n in nested if "skija-" in n and "skija-shared" not in n]
        native_ok = (len(native_jars) == 1
                     and ("skija-" + platform) in native_jars[0]
                     and native_jars[0] in registered_nested)
        check(f"{project}: 指定平台原生包唯一且已登记", native_ok,
              f"嵌套: {', '.join(native_jars)}; 已登记: {', '.join(sorted(registered_nested))}")
        if len(native_jars) == 1 and native_jars[0] in registered_nested:
            with zipfile.ZipFile(io.BytesIO(jar.read(native_jars[0]))) as native_jar:
                extension = ".dll" if platform == "windows-x64" else ".so"
                check(f"{project}: 原生包含 {extension} 库",
                      any(n.endswith(extension) for n in native_jar.namelist()))

        # 我们自己的类不许再提公共包 —— 重定位就是为了不再引用它。
        own = [n for n in names if n.endswith(".class") and not n.startswith(NESTED_PREFIXES)]
        hits = [n for n in own if PLAIN in jar.read(n)]
        check(f"{project}: 自己的类不再引用 com.formdev.flatlaf", not hits,
              f"{len(hits)} 处: {hits[:5]}")

        if flatlaf_nested:
            with zipfile.ZipFile(io.BytesIO(jar.read(flatlaf_nested[0]))) as inner_jar:
                inner = inner_jar.namelist()
                check(f"{project}: 内嵌那份装着重定位后的类",
                      f"{SHADED}FlatLightLaf.class" in inner)
                check(f"{project}: 内嵌那份不含公共包",
                      not any(n.startswith("com/formdev/flatlaf") for n in inner))
                manifest = inner_jar.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
                check(f"{project}: 内嵌那份声明了 Automatic-Module-Name",
                      MODULE_NAME in manifest)


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--platform', choices=PLATFORMS, default='windows-x64')
    parser.add_argument('projects', nargs='+')
    args = parser.parse_args(argv[1:])
    failures.clear()
    for project in args.projects:
        verify(project, args.platform)
    print()
    if failures:
        print(f"产物断言未通过：{len(failures)} 条")
        for name in failures:
            print(f"  x {name}")
        return 1
    print("产物断言全部通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
