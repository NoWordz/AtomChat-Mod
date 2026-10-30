# AtomLib 拆分前基线

2026-10-29；AC `main@28fd6de0caf1133ffba97c63315d55bdf21aef48`，版本 0.4.0。实施前工作区只有已交付方案文档；本次基线不修改产品源码或配置。

三个工程各执行 `gradlew.bat test build --no-daemon --console=plain`，均通过。Forge 用 JDK17.0.18，Fabric／NF 用 JDK21.0.11。XML 汇总保存于 `atomlib-baseline/counts.json`：Forge 1551、Fabric 1563、NF 1551 项，各 4 skipped、0 failures/errors。默认跳过的测试不算运行通过。

Forge 另分两个独立进程执行 `goldenCapture`、`goldenDeterminism`，两项通过；之后重新执行普通 `test` 恢复完整 JUnit 报告。此处尚未执行拆分后的 goldenCompare。捕获图保存在 `atomlib-baseline/golden-forge/`，由真实 AC GoldenFrameTest 产生；不用于替代真人交互验收。

根工具 `python tools/verify_targets.py`、`python tools/test_verify_jars.py`（12 项）通过。对三端精确文件名 `atomchat-<Loader>-<MC>-0.4.0-windows-x64.jar` 执行 `tools/verify_jars.py` 均通过，避免同目录残留 preview 产物造成取件歧义。未删除旧产物。

本次尚未部署或启动游戏；拖动、模态、IME、ARM64、专服与 shader 共存均无新的实机证明。此记录是迁移对照起点，不是拆库后验收。

2026-10-30 检查点整理：逐字节核对 determinism/pass1 和 pass2 共 24 张图均与根目录 12 张基线一致，删除重复副本；对应文件名与 SHA-256 保存在 `atomlib-baseline/determinism.json`。根目录原始基线未重拍或覆盖。
