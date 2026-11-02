# Entrance-fix round：构建与三实例部署（2026-11-02，0.4.0 内，未提交）

## 范围与产物
- 本轮修复入场动画三个实机症状（卡片上跳、太快、无渐变）：
  - 删除 120ms fast 中断钟，`UiStagger` 统一 Dango 单钟：page 320ms、item 380ms、stagger 35ms（`shared/src/main/java/com/atom/chat/ui/UiStagger.java:38-55`）；
  - 条目淡入与升起共用同一进度：`itemAlpha` 直接返回 `itemProgress`（`UiStagger.java:115-125`），杜绝“不透明却位移”；
  - 中断语义改为 reset `pageAt` 重放同一入场，无 fast 分支（`UiStagger.java:18-27`）；
  - 去掉“页面平移 + 行级升起”双重运动：页面到达时行 `markSettled()`（`ConversationListPage.java:78-79`、`SettingsSectionPage.java:78-79`，三端 screen 在 `AtomChatScreen.java:2466/2480` 接线），仅页面信封在动；`PageTransitionComposite.drawArrivingFrame`（`PageTransitionComposite.java:191`）负责到达页叠加。
- 工作树另含未提交的 `UiPixelSnap`、`UiSpring`、blur `u_MaxLuma` 等前几轮成果，一并打进本轮包。
- 三包构建（Windows x64，先清 `platforms/<loader>/build/atomlib-consumer`，各平台产物落 `build/atomlib-consumer/libs/`）：NeoForge `jar`、Fabric `remapJar`、Forge `jarJar`（完整 reobfJarJar）。三个 `BUILD SUCCESSFUL`。
- 产物门：`python tools/verify_jars.py --platform windows-x64 --profile atomlib-consumer platforms/1.20.1-forge platforms/1.21.1-fabric platforms/1.21.1-neoforge` → 21 项 ok，`产物断言全部通过。`
- 内容级检查（python zipfile，三包 23 项/包，69/69 PASS）：
  - **本轮**：`com/atom/chat/nav/PageTransitionDirector.class` **不存在**（源码亦为 `D`）；`UiStagger.class` 含 `PAGE_SLIDE_PX`/`ZOOM_START_SCALE`/`ITEM_STAGGER_CAP`/`itemAlpha`，**不含** `entranceInFlight`；`UiStagger$Entrance.class` 含 `markSettled`；`PageTransitionComposite.class` 含 `drawArrivingFrame` 且**不含** `arrivalInterrupted`；`ConversationListPage.class`/`SettingsSectionPage.class` 均含 `markSettled`；
  - **沿用项**：`UiPixelSnap.class` 在包；`UiSpring.class` 含 `PANEL_MOTION_STIFFNESS`；`ToggleSwitch` 引用 `SpringAnim`；`atomchat_panel_blur.fsh`（forge/neoforge `assets/atomchat/`、fabric `assets/minecraft/`）含 `u_MaxLuma`；
  - 卫生：无 test-infra 类（junit/mockito/`src/test`/已知 probe 均 0；`TestCompanion*`、`TestPayloads` 为产品网络功能类，非测试产物）；版本串 0.4.0（`META-INF/mods.toml` / `META-INF/neoforge.mods.toml` / `fabric.mod.json`）。
- 断言/部署脚本：`build/entrance-content-assert.py`、`build/entrance-deploy.py`（python 哈希，避开 PowerShell 缺 `Get-FileHash`）。

## 部署（备份 → 替换 → 读回核验）
- 备份根：`D:\AtomChat-Backups\2026-11-02-entrance-fix\20261102-093955\<Loader>\`，备份读回哈希逐一吻合。
- deployment.json：`docs/verification/2026-11-02-entrance-fix-deployment.json`（完整新旧 SHA256/字节数/备份路径）。
- 被替换旧包哈希与上一轮（dango-nav）部署记录的「新包」哈希逐一吻合（3050240d / 8ee3a3ce / e9221872），链条完整。
- mods 目录 before/after：164→164（Forge）、74→74（Fabric）、206→206（NeoForge），每目录恰 1 份 atomchat 包，其余文件零变动。

| 实例 | 安装包 | 本轮新包 SHA256 | 被替换旧包 SHA256 |
|---|---|---|---|
| 1.20.1-main（Forge，8442944 B） | `atomchat-Forge-1.20.1-v0.4.0.jar` | `013738df259cc85fbe90fe33005d3b9d6ea0d5e16b2706469cf61b52da0a3737` | `3050240d81ef2a4507a5125c484c85fd3456fcf995e58059333d8a372f467e03` |
| 1.21.1-CCB（Fabric，8470932 B） | `atomchat-Fabric-1.21.1-v0.4.0.jar` | `4cb27ccd6acaf8f4c453d1ffc1d967767c861ccaffbf3c1f913b21d9121ea481` | `8ee3a3ce10643213923665cd054a9a832a863a13f3454fb3bbf99a0d9144c58a` |
| Mechanomania-航空学（NeoForge，8462197 B） | `atomchat-NeoForge-1.21.1-v0.4.0.jar` | `92c688e2fbf3be4581ad66d0fd857fbc7412b62f2ac148d126411977303cf495` | `e922187279b33825c3b4f92ef9ea4b34422400dcd684761bccce0d1dfe1789a6` |

## 实际命令摘录
- NeoForge：`cd platforms/1.21.1-neoforge && ./gradlew jar -PatomlibMavenRepo="D:\AtomChat-Main\build\audit-20261101\maven" --offline`
- Fabric：`cd platforms/1.21.1-fabric && ./gradlew remapJar -PatomlibMavenRepo="D:\AtomChat-Main\build\audit-20261101\maven" --offline`
- Forge：`cd platforms/1.20.1-forge && ./gradlew jarJar -PatomlibMavenRepo="D:\AtomChat-Main\build\audit-20261101\maven" --offline`
- 产物门：`python tools/verify_jars.py --platform windows-x64 --profile atomlib-consumer platforms/1.20.1-forge platforms/1.21.1-fabric platforms/1.21.1-neoforge`
- 内容级：`python build/entrance-content-assert.py` → `ALL PASS`（69/69）
- 部署：`python build/entrance-deploy.py` → `ALL DEPLOYED`；独立复核三实例安装包/备份哈希 → `PASS`

## 未验证项（归用户）
- 实机手感：卡片是否还上跳、入场速度是否合适、条目淡入是否可见、导航到达时是否只剩页面信封运动——全部待游戏内确认。
- 未启动/关闭游戏；未 commit/push/tag/发布；版本号未动（0.4.0）。
- 三端全量测试（Fabric 1577 / Forge 1562 / NeoForge 1562，0 失败）为本轮开工前既有结果，本轮未重跑。
