# Dango 采纳 round 2：E 单元构建与三实例部署（2026-11-01，0.4.0 内，未提交）

## 范围与产物
- 本轮 jar = round 1 内容 + 单元 E（动效可打断：InputGate 门禁解除、页面/tab 转换中途点击落地、ZOOM 重臂 exit alpha 播种）。单元 F 仅产证据（tools/probes + .work），无产品改动。
- 三包构建（Windows x64，产物清旧后重建，`build/atomlib-consumer/libs/` 布局）：NeoForge `jar`、Fabric `remapJar`、Forge `jarJar`，产物 `atomchat-<Loader>-<mc>-v0.4.0.jar`。
- 产物门：`python tools/verify_jars.py --platform windows-x64 --profile atomlib-consumer` 三平台全部通过。
- 内容级检查（三包逐项 PASS）：
  - `atomchat_panel_blur.fsh` 含 `col *= u_MaxLuma / luma;`、json 含 `u_MaxLuma`、`atomchat_kawase_blur.fsh` 无（B，Kawase 零污染）；
  - `ToggleSwitch.class` 引用 `SpringAnim` + `knobSpringForTest`、无 `Animator` 残留；`UiSpring.class` 含 `TOGGLE_KNOB_STIFFNESS`（C）；
  - **E 门禁解除的包内标记**：screen 的 `InputGate.Host` 匿名实现类（`AtomChatScreen$29.class`，Fabric 命名空间下同名）同时含 `pageNavLive`/`pageNavPopPending`——新 host 面板读取在位（旧 panelAnimating 两条 director 子句已随源码删除，无残留可断言点）；`NavStackController.class` 含 `pageNavPopPending`、`PageTransitionDirector.class` 含 `rootZoomExitAlpha`（重臂播种）；
  - 无 `com/atom/chat/testing/` payload。

## 部署（备份 → 替换 → 读回核验）
- 备份根：`D:\AtomChat-Backups\2026-11-01-dango-adoption-round2\<timestamp>\`；上一轮的 `removed-wrong-loader\` 隔离件未动。
- deployment.json：`docs/verification/2026-11-01-dango-adoption-round2-deployment.json`。

| 实例 | 安装包 | round2 新包 SHA256 | 被替换 round1 旧包 SHA256 |
|---|---|---|---|
| 1.20.1-main（Forge） | `atomchat-Forge-1.20.1-v0.4.0.jar` | `524e8a0d8fda15dff83be3b6232d0c413c0cbb241d93376aaea4af1b4ddbf661` | `ed62fc2108c85d82a2b8f2fbdc16374819567accd3d8beec875b4891758886bd` |
| 1.21.1-CCB（Fabric） | `atomchat-Fabric-1.21.1-v0.4.0.jar` | `ba5064640658b86e1e1330b22b579bd6c904562680a27772e8a62c1c9d397c52` | `5792c50222013b72bb0e85936705b06839e39f2a1dff17d575b81d2b9dcf9da0` |
| Mechanomania-航空学（NeoForge） | `atomchat-NeoForge-1.21.1-v0.4.0.jar` | `41b3857fa5d937318a1365b8d3c3fb23c12487b92e707185a915d1818cf4f3b4` | `b31d29cf66ad3566b092cbc047ca98e9eda90ef614616843fa559746823946ef` |

- 被替换旧包哈希与 round 1 部署记录逐一吻合（链条完整）；其余 mods 文件逐一断言未动；每目录恰 1 份 AtomChat（读回独立核验 PASS×3）。

## 实际命令摘录
- 构建：`./gradlew jar|remapJar|jarJar -PatomlibMavenRepo="D:\AtomChat-Main\build\audit-20261101\maven"`（各自平台目录），三个 `BUILD SUCCESSFUL`。
- 产物门：`python tools/verify_jars.py --platform windows-x64 --profile atomlib-consumer platforms/1.21.1-neoforge platforms/1.21.1-fabric platforms/1.20.1-forge` → `产物断言全部通过。`
- 读回：独立脚本逐实例 sha256+字节数+atomchat 包数断言 PASS×3。

## 未验证项（归用户）
- 实机手感：底栏 tab / 页面切换动画中点击的即时性（本轮部署的核心变更）需游戏内确认。
- maxLuma 亮背景可读性、knob 过冲手感：round 1 遗留项，本包继续携带。
- 未启动/关闭游戏；未 commit/push/tag/发布；版本号未动（0.4.0）。
