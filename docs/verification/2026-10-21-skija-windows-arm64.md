# Skija 0.143.18 与 Windows ARM64 候选验证

## 结论与版本选择

AtomChat 保持 0.3.2，Skija 从 0.116.8 迁移到 0.143.18，HumbleUI types 从 0.1.1 升到 0.2.0。0.143.18 是本次查询时的官方最新正式发布，不是经过 AtomChat 全场景认证的“最稳定版本”。0.143.16 首次加入 Windows ARM64，0.143.18 继续包含 ARM64，修复 Path.fillPath，并更新了原生编译工具链。

- [官方 0.143.18 发布](https://github.com/HumbleUI/Skija/releases/tag/0.143.18)
- [官方版本变更](https://github.com/HumbleUI/Skija/blob/0.143.18/CHANGELOG.md)

Skija 版本现在只定义在仓库根 gradle.properties；三端由 atomchat-layers.gradle 读取。Forge/NeoForge 的 JarJar 采用精确区间 `[0.143.18]`，避免 Java API 与 JNI native 被分别解析到不同版本。与携带旧 Skija 的其他模组混装未验收，精确区间可能拒绝不兼容的组合。

## 实现范围

- 每加载器三个发行包：Windows x64、Windows ARM64、Linux x64。文件名统一为 `atomchat-<loader>-<mc>-0.3.2-<os>-<arch>.jar`，每包只嵌入一个对应平台 native。
- 升级字体加载 API、PathBuilder 构造/释放、Surface ColorType 和 DirectContext.flush(surface)，保持原有布局与业务。
- 新版字体工厂拒绝数据时返回 null；decodeTypeface 将其转为 IOException，走已有系统字体/伪粗体回退，并及时释放 Data。新增两项实际 native 字体测试，损坏数据旧行为的 assertThrows 已先失败。
- 交叉构建的测试依赖按运行 Gradle 的 JVM 系统与架构选择，不按目标发行包选择。ARM64/Linux 输出目录独立。
- build.yml/release.yml 接入每加载器三包、总计九包和新文件名；两份 YAML 均成功解析。未触发远程 CI 或发布流程。

## 实际命令与结果

各平台目录分别运行；NeoForge/Fabric 使用 Windows JDK 21.0.11，Forge 使用 JDK 17.0.18：

```powershell
.\gradlew.bat test build --offline --no-daemon --console=plain -I ../../.work/skija-upgrade/local-recovery.gradle
.\gradlew.bat build -x test -Patomchat.nativePlatform=windows-arm64 --offline --no-daemon --console=plain -I ../../.work/skija-upgrade/local-recovery.gradle
.\gradlew.bat build -x test -Patomchat.nativePlatform=linux-x64 --offline --no-daemon --console=plain -I ../../.work/skija-upgrade/local-recovery.gradle
```

| 目标 | 测试总数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| NeoForge 1.21.1 | 1377 | 0 | 0 | 2 |
| Fabric 1.21.1 | 1389 | 0 | 0 | 2 |
| Forge 1.20.1 | 1377 | 0 | 0 | 2 |

两项跳过为原有 golden/benchmark。三个变体业务源码相同，因此在各加载器 Windows x64 构建中完整测试一次，再构建其他变体。

本机直接在线构建最初遇到 Fabric mapping-io 与 Forge gson 的既有 TLS 问题；临时 init 复用既有缓存恢复仓库，并为本轮从官方 Maven Central 下载的 Skija/types 原字节建立本地 Maven 目录。没有降级依赖或修改仓库正式下载源。NeoForge 首轮正式在线 test/build 已通过，最终统一重建使用同一 init 和离线方式。日志在 `.work/skija-upgrade/*-final-*.log`。

另实际执行：

```powershell
python tools/test_verify_jars.py
python tools/gen_seams.py --check
python tools/verify_targets.py
python tools/twin_diff_report.py
python tools/verify_log_prefixes.py
python tools/bare_scan.py
git diff --check
```

产物校验器 12 项通过；PE 架构、ELF 架构、版本配置、Fabric 登记四类变异均被测试检出。接缝 125/71、targets、孪生 70 对/未归类 0/陈旧 0、日志 262/违规 0、裸值 471/505 均通过。

## 九包与原生验证

- 最终九包分别执行 verify_jars.py 与 verify_chat_assets.py：精确文件名、唯一 native、Fabric/JarJar 登记、types、Java/native 内部版本、PE x64/ARM64 或 ELF x64、模组 0.3.2 与音频/语言资源均通过。
- 逐包确认包含最终字体失败处理。各加载器三个包全部应用 class 与 assets 的逐项 SHA-256 相同：NeoForge 594 项、Fabric 578 项、Forge 582 项（不统计目录条目）。679 个构建输入在收尾核验中未变。
- 完整指纹见 [九包清单](2026-10-21-skija-packages.json)。方便取用的候选副本在 `D:/AtomChat-Main/.work/skija-upgrade/dist/`；逐一核对原产物与副本 SHA-256。
- Windows 11 x64 与 WSL Linux x64（OpenJDK 21.0.12.1）分别真实加载 0.143.18 native，验证内嵌中文字体、宽度测量、PathBuilder 偶奇遮罩、CPU 光栅和 PNG 输出。日志输出分别为 `PASS ... Windows 11/amd64, 5986 PNG bytes` 与 `PASS ... Linux/amd64, 7479 PNG bytes`。已查看 Linux 冒烟图及新版真实聊天离屏图，字形和几何可见；没有据此判断 GPU 性能。
- 独立强模型审查首次提出四项 P2，修正后复查无新增可行动缺陷或代码阻断；最终九包由主代理统一核验。

## 验收边界

代理未启动 Minecraft；用户随后自行启动部署的 Windows x64 Fabric CCB 候选，反馈渲染均正常并授权三端提交、推送。此反馈不扩展为 ARM64、Linux 新版 GPU、其他加载器运行、输入专项、帧率测量或所有混装组合均已验收。

构建结束时未部署；后续部署见下一节。用户验收后已授权提交、推送源码，具体结果以 Git 为准；未打 tag 或发布。候选构建时的 git_dirty=true 符合当时未提交源码状态，不因后续提交而改变包内戳。旧构建产物 28 件已逐字节备份到 `.work/skija-upgrade/baseline/`。商店正文与正式发布说明留待确定发布候选后同步。

## Windows x64 CCB 部署（用户续轮授权）

- 目标：`D:/Myworld/.minecraft/versions/1.21.1-CCB/mods/atomchat-Fabric-1.21.1-0.3.2-windows-x64.jar`；安装后 SHA-256 `5bcd0e33cc13dd06e4ebd3c97114ff5b3a5e374d12aa39634b7f7a4d0c238974`，与九包清单和候选源完全一致。
- 旧包 `atomchat-Fabric-1.21.1-0.3.2.jar` 的 SHA-256 为 `a3c59c8baa0cac9c41a6c11ae24eddd82f4cce1020f66c79f39b21c71c01e244`，已按字节备份并核验到 `D:/AtomChat-Backups/2026-10-21-skija-x64-CCB/121944-673076/`。
- 同目录暂存核验后原子替换并改成新命名，扫描 Fabric 元数据确认只有一个 AtomChat。其他 73 个文件的 SHA-256、大小和修改时间均不变。用户自行启动游戏后已反馈 x64 渲染正常；ARM64 验收状态不变。
- 完整部署记录 `.work/skija-upgrade/ccb-deployment.json`；永久备份目录同时保存 deployment.json。
