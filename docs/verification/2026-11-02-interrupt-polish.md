# Interrupt polish round：E2+V 构建与三实例部署（2026-11-02，0.4.0 内，未提交）

## 范围与产物
- 本轮 jar = round 2 内容 + 单元 E2（打断彻底化：chip 门禁移除、fast 中断语义、面板开合弹簧 k=900/zeta 0.8）+ 单元 V（`UiPixelSnap` 设备像素对齐原语 + panel 几何输入侧 snap、rim 14 处像素中心对齐、`UiStagger` 错峰原语 + 导航列表/设置/表情网格接线）。
- 三包构建（Windows x64，产物清旧，`build/atomlib-consumer/libs/` 布局）：NeoForge `jar`、Fabric `remapJar`、Forge `jarJar`。
- 产物门：`python tools/verify_jars.py --platform windows-x64 --profile atomlib-consumer` 三平台全部通过。
- 内容级检查（三包逐项 PASS）：
  - **E2**：screen `InputGate.Host` 匿名实现类含 `pageNavLive`/`pageNavPopPending`（沿用 G 的门禁断言）；`PageTransitionDirector.class` 含 `pageNavFast` + `rootZoomExitAlpha`；`UiSpring.class` 含 `PANEL_MOTION_STIFFNESS`；
  - **V**：`UiPixelSnap.class`、`UiStagger.class` + `UiStagger$Entrance.class` 在包内；screen class 引用 `UiPixelSnap`（panel snap 接线）；`ConversationListPage`/`SettingsSectionPage`/`EmojiPanel` class 各引用 `UiStagger`（三表面接线在包）；
  - 沿用项：`atomchat_panel_blur.fsh` 含 maxLuma 钳制、kawase 无、`ToggleSwitch` 弹簧无 Animator、无 testing agent。
- 回归背景：三端全量 test 于部署前跑过——NeoForge `1606/0/4skip`、Fabric `1621/0/4skip`、Forge `1606/0/4skip`（`BUILD SUCCESSFUL`）。

## 部署（备份 → 替换 → 读回核验）
- 备份根：`D:\AtomChat-Backups\2026-11-02-interrupt-polish\<timestamp>\`；round-2 的 `removed-wrong-loader\` 未动。
- deployment.json：`docs/verification/2026-11-02-interrupt-polish-deployment.json`。

| 实例 | 安装包 | 本轮新包 SHA256 | 被替换 round-2 旧包 SHA256 |
|---|---|---|---|
| 1.20.1-main（Forge） | `atomchat-Forge-1.20.1-v0.4.0.jar` | `a5e50513975e879151b941fbbcdb30cf53a6e14ef402d4ff0d162f70bc6ea2b6` | `524e8a0d8fda15dff83be3b6232d0c413c0cbb241d93376aaea4af1b4ddbf661` |
| 1.21.1-CCB（Fabric） | `atomchat-Fabric-1.21.1-v0.4.0.jar` | `7549f3a45be9cd7f186e272947e62aa29e7af05ed17c1a92ca69bc8cf53b4b82` | `ba5064640658b86e1e1330b22b579bd6c904562680a27772e8a62c1c9d397c52` |
| Mechanomania-航空学（NeoForge） | `atomchat-NeoForge-1.21.1-v0.4.0.jar` | `cde9732f1eb8fd59600d205f7d1df408ff54d0823d6d24f1c890d36aeb063312` | `41b3857fa5d937318a1365b8d3c3fb23c12487b92e707185a915d1818cf4f3b4` |

- 被替换旧包哈希与 round-2 部署记录逐一吻合（链条完整）；其余 mods 文件逐一断言未动；每目录恰 1 份 AtomChat（独立二次读回 PASS×3）。

## 实际命令摘录
- 构建：`./gradlew jar|remapJar|jarJar -PatomlibMavenRepo="D:\AtomChat-Main\build\audit-20261101\maven"`，三个 `BUILD SUCCESSFUL`。
- 产物门：`python tools/verify_jars.py --platform windows-x64 --profile atomlib-consumer platforms/1.21.1-neoforge platforms/1.21.1-fabric platforms/1.20.1-forge` → `产物断言全部通过。`
- 读回：独立脚本 sha256+字节数+atomchat 包数断言 PASS×3。

## 未验证项（归用户）
- 实机：打断彻底化（返回→点外观、返回箭头即时消失、SLIDE/ZOOM 打断加速入场）、面板弹簧观感、面板边缘对齐、列表/设置/表情入场错峰——全部待游戏内确认。
- 上一轮遗留（maxLuma 亮背景可读性、knob 过冲手感）继续携带，未复测。
- 未启动/关闭游戏；未 commit/push/tag/发布；版本号未动（0.4.0）。
