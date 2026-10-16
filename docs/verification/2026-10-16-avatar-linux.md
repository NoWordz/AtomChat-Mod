# 2026-10-16 头像帽层比例修正与 Linux 追平

基线：`main@4c8edf73a42d1f617e72575f83b1a804c32841e1`，开工工作区干净。版本保留 `0.3.0`，未提交、推送或发布。

## 原因与修正

- 用户报告 E33Chat 样式帽层的立体感比 E33Chat 原生小。参考本机 E33Chat 源码 `D:/MDK/platforms/1.21.1-neoforge/src/main/java/com/niuqu/chatbubble/config/ChatBubbleConfig.java:269`（默认脸宽 20）及 `render/ChatMessageRenderer.java:203`（帽宽 `size + 2`）：默认每边外扩占脸宽 5%。AtomChat 原先固定 `s(1)=1.25`，消息脸宽为 `s(40)=50`，外扩比例只有 2.5%。
- 普通 `PlayerAvatar.drawE33Chat` 改为每边外扩 `size / 20`，随调用者的头像尺寸缩放。Profile 显式指定的 `3/40` 比例保留。两份 `MessageListView.avatarEdgeInset` 同步容纳放大后的帽层，原生 AtomChat 圆形头像的间距保持不变。
- WSL Prism 实例旧包实际为 `0.3.0-Preview`，SHA-256 `b0620c78cafd1faf94f0640a89e9d7894720c88cdef07888e7a6faf2c281209f`，包内没有 `AvatarStyle` 枚举和作者素材。Linux 本地产物也停在 10 月 14 日；不是另有一套 Linux 业务源码。三端两种系统变体均从当前源码重建。

## 实际验证

1. 在 NeoForge 旧实现上运行 `./gradlew test --tests com.atom.chat.image.PlayerAvatarHatTest --offline --no-daemon`：5 项中 3 项按预期失败（40/80 像素脸部的帽层范围不足、列表留白不足）；Profile 显式外扩与 AtomChat 原生分支通过。
2. 修正后在三个目标目录分别运行：

   ```sh
   ./gradlew test --tests com.atom.chat.image.PlayerAvatarHatTest \
     --tests com.atom.chat.render.OffscreenRenderTest \
     --tests com.atom.chat.settings.AboutHeroAssetTest build --offline --no-daemon
   ./gradlew build -x test -Patomchat.nativePlatform=linux-x64 --offline --no-daemon
   ```

   Forge 使用 JDK 17，Fabric/NeoForge 使用 JDK 21。每端 28 项定向测试，均 0 失败、0 错误、0 跳过。不是全量单测或游戏内验证。Linux 打包在 Windows 宿主执行，未在 Linux 上运行这组 JUnit。
3. `python tools/bare_scan.py`：505/505；`python tools/gen_seams.py --check`：甲 123 / 乙 71；`python tools/verify_targets.py` 通过；`python tools/twin_diff_report.py`：68 对，未归类差异 0、陈旧条目 0；`git diff --check` 通过。
4. 六个明确路径的发布 JAR 分别执行 `tools/verify_jars.py --platform windows-x64|linux-x64`，均通过；各包仅内嵌对应系统的 Skija native。`tools/verify_chat_assets.py` 检查六包资源及精确版本均通过。
5. 逐条比对每个加载器 Windows/Linux JAR 内的 `com/atom/chat/` 和 `assets/atomchat/`：内容哈希全部一致。作者两张 PNG 分别与各平台源码素材一致。所有本地候选包的构建戳为 `version=0.3.0, git_commit=4c8edf7, git_dirty=true`，不是已提交发布包。

第一次 NeoForge 构建被裸值守卫拦住（同一表达式新增一次 `s(1.5)`）。负责人改为计算帽层外扩后与原有间距取最大值，未放宽基线；最终三端均从修正后的源码完成测试与构建。

## 已授权部署

用户确认“两处都更新”。安装前确认未运行对应游戏；部署先备份，复制后核验 SHA-256，未启动客户端。

| 实例 | 新包 | SHA-256 |
|---|---|---|
| Windows「Mechanomania-航空学」NeoForge | `atomchat-NeoForge-1.21.1-0.3.0.jar` | `013da675ab453b59c00a2945f7b69989efa841e2693d4445d2a62ede4d9906a2` |
| WSL Prism「1.21.1」Fabric | `atomchat-Fabric-1.21.1-linux-x64-0.3.0.jar` | `f0a2ffcee60b570ff0c88e5c619be78cf818ca9803b6cc77f737b1de1011b01e` |

- Windows 旧包备份：`D:/AtomChat-Backups/2026-10-16-avatar-linux/NeoForge/atomchat-NeoForge-1.21.1-0.3.0.jar`，旧哈希 `e75c01af88665049b82c7a78cc775a87931f7c9c560952486547356f3f5da2a4`。
- WSL 旧包备份：`/home/niuqu/.local/share/PrismLauncher/instances/1.21.1/atomchat-backup/2026-10-16-avatar-linux/atomchat-Fabric-1.21.1-linux-x64-0.3.0-Preview.jar`。
- 两处 `mods` 中均仅保留一份 AtomChat。WSL 安装后再次读取 JAR，确认版本为 0.3.0，包含头像样式枚举及 `e33epus.png` / `nowordz.png`。

## 多模型分工及验收范围

负责人直接核实参考实现、定位原因、决定公式和边界，给出原始需求、文件白名单、实现顺序、验收及停止条件。执行者为用户确认的 Command Code GOAT / `deepseek/deepseek-v4.1-flash-fast`，reasoning `disabled`，经 ZCode 独立会话执行。

测试编写单元五分钟预算内未产出，关闭后由负责人编写并运行回归测试。第二个有限单元由执行者修改四个生产文件，交到 `ready_for_review`；负责人逐块复核、清除无关注释改写、修正裸值重复并完成最终测试、打包、部署。两次会话正常关闭，供应商配置哈希未变。

本机过程原在忽略目录 `.work/2026-10-16-avatar-linux/`，收尾时清理，关键结论与部署记录保留在本文，执行摘要归档到 `D:/AtomChat-Backups/2026-10-16-closeout/evidence/`。后续主页交互修复及最新安装哈希见[交互验证记录](2026-10-16-avatar-interaction.md)。游戏内观感仍由用户验收；本轮没有覆盖 Linux 性能、鼠标锁定或多人交互。
