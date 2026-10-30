# AtomLib / AtomChat 检查点（2026-10-30）

结论：保存可继续开发的本地检查点，**未通过最终验收，不可据此发版**。Hubble 的交付已落盘；主代理复核发现一个已实测复现的交互问题和三个待收口边界。用户要求剩余工作留到下个会话。本轮只审查、验证、修整归档与更新状态，不继续修产品行为，不推送、部署、启动游戏或发布。

## 当前成果与验证范围

- 独立库位于 `D:/AtomLib`，分支 `codex/atomlib-extraction`，检查点提交 `cb612db74ebc8efe7fdba6179b0911b8e159b287`；AC 位于 `D:/AtomChat-Main`，分支 `main`，拆分前基线为 `28fd6de0caf1133ffba97c63315d55bdf21aef48`。本轮分别提交；AC 检查点提交号查 Git。
- 自有库 Apache-2.0，AC 保持 MIT，Tuui ARR 内容不复制。Lib 不带字体，Windows x64/ARM64 双 native，MC 自带 LWJGL 不升级。版本仍为 Lib `0.1.0-local` / AC `0.4.0`。
- Skija/Yoga、资源和组件、浮层、可选壳、HUD/裁剪 API、资产准入、独立消费者、AC 外观布局接线及锁定输入已实现。AC HUD/壳仍用宿主原实现；不能把 Lib API 可用说成 AC 全面迁移完成。
- 主代理本轮重跑 Lib 标准四模块测试，使用 `--rerun-tasks`：core 9、runtime 23、ui 82、devtools 2，共 **116，0 失败/错误/跳过**。另外的 Yoga 探针 6 项不计入这 116 项。
- 主代理本轮在三端分别运行 `gradlew.bat test build -PatomlibMavenRepo=D:/AtomLib/build/test-maven --no-daemon --console=plain`：Forge 1561、Fabric 1573、NeoForge 1561，均 0 失败/错误、各 4 既有跳过；增量任务会显示 UP-TO-DATE，不声称全部强制重跑。临时红测移除后再次恢复 Forge 标准套件。
- 本轮重新执行目标矩阵、AC jar 校验器自测 16、Lib artifact 自测 29、consumer 自测 2、UI/example guard 3，并对三个 AC 实际 JAR 运行 `--profile atomlib-consumer`，均通过。
- 检查既有 XML：三端 hidden GL 合计 33，0 失败/错误/跳过；检查六个独立消费者构建回执。它们是 Hubble 交付证据，本轮未重新执行 GL 或六次构建。
- 配套目录 `D:/AtomChat-Main/build/atomlib-delivery-20261030-final`。本轮逐项重算 SHA256SUMS，与交付字节一致；归档整理后更新源码 ZIP、报告副本及清单。JAR 是本次提交前的本地候选包，修复下述问题后必须重新构建/锁定/导出，不作已验收安装包。

## 下个会话按此顺序处理

### 1. P1：切换半屏后，布局区输入永久暂停（已实测）

定位：official 与 Fabric 的 `settings/AtomLibAppearanceSection.java:43,120`，共享 `ui/atomlib/AcModalAdapter.java:24`，Lib `view/UiView.java:54,225`。

半屏 Toggle 的 preview 调用 `cancel()`；`cancel()` 直接令 `view.suspendInput(true)`，但没有同步 `AcModalAdapter.blocking`。此时 blocking=false、没有确认窗、hostActive=true，`modal.sync()` 计算 next=false，因与 blocking 相同而不恢复输入。后续 pointer/key/wheel 被 UiView 吃掉，不再操作控件。外部模式变更、SettingsSectionPage.reset 也会调用同一路径。

本轮临时运行 `AtomLibCheckpointProbeTest.halfScreenToggleMustLeaveWidthSliderInteractive`：通过真实 section 点击半屏开关，确认模式已改变，再对真实宽度 track 发 PRESS/RELEASE；期望持久化 1 次，实际 **0 次**，测试确实失败。探针已移除，未把失败测试混入标准套件；修复会话应将此序列做成永久回归，同时验证 cancel、切页再返回、模态打开关闭及关闭视图。

优先统一暂停状态的所有权，避免只追加一次无条件 resume 而破坏真实模态拦截。official/Fabric 孪生同步处理。

### 2. P2：原生 context 仍逃逸同步租借回调

定位：两份 `render/SkiaGraphics.java:81` 的 requireContext 返回 `withContext(context -> context)`；两份 `render/ItemIconRenderer.java:82` 在回调外 reset/flush，并将 context 保存到静态 owner，随后将原版物品绘制和 Skija texture 导入放在同一准备流程。

这与 Lib `docs/api-contracts.md` 和 `docs/render-bridge.md` 的借用合同不符。`withContext` 的 GL/PBO 保护已经退出，无法保护后续 raw Skija 调用。现有 GL 测试只测桥本身，不证明这个 AC 调用链安全；本轮没有证明发生崩溃，不把它写成已复现崩溃。

应把原版绘制、资源导入和提交分成真实受保护阶段，禁止用取出句柄代替租借。覆盖物品图标、异常、污染 PACK/UNPACK/PBO 与 Blaze3D 状态缓存。不要恢复旧 world snapshot（现设计由 AC raw-GL blur 预处理）。

### 3. P2：共享 bridge 的最终释放没有平台事件接线

定位：三个平台 `client/render/MinecraftRenderBridge.java:44` 提供 close，但全库生产调用扫描未找到最终关闭调用。Forge/NF common 入口为空，Fabric client 入口也为空；AC 页面 close 已正确不再销毁全局 bridge。

需要在 GL context 仍有效的客户端退出阶段显式释放，同时保持专服不加载 client/native。不能将页面 close 改回关闭共享 context；避免退出时调用 get() 新建一个从未使用的 bridge。现有测试手工 try-with-resources，不覆盖 MC 生命周期。

### 4. P2：Lib Linux CI 与测试 native 配置冲突

定位：Lib `.github/workflows/build.yml` 的 source job 使用 ubuntu-latest，但 `ui/build.gradle:7`、`devtools/build.gradle:5` 仅提供 Windows Yoga/LWJGL native 和 Windows x64 Skija。源码中的 runtime 模块虽按 OS 选 Skija，不能替代另外两个模块测试/预览的 runtimeClasspath。

Linux source job 的 Yoga 初始化/图形测试没有对应 native，会加载失败。应明确测试宿主平台（例如该 job 改 Windows，或只为测试按宿主引入 native）；玩家发行仍只允许两个 Windows 架构。此项为配置/依赖链审查结论，未运行远端 Actions。

## 后续验收与操作

先完成上述四项及永久回归，再三端构建、产物扫描、锁文件更新与配对打包，然后由用户实机验收。Forge 已重混淆 Lib 的开发依赖接法也需要实际开发客户端加载验证；仅编译通过不证明映射环境兼容。

仍未覆盖 MC/专服启动、缺库诊断实际显示、ARM64 硬件、IME、完整 bidi/CJK 断行、Iris/OptiFine、其他 GPU、性能和长会话。CI 显式输入尚无远端托管 URL/SHA 配置，不应直接发版。

主代理已对新增接线、生命周期和 CI 做与 Hubble 实施独立的限定审查；未把它扩大为所有库 API 的完整独立审计。已有 foundation/UiView/render 审查继续按各自范围使用。

本轮另修正源码归档器，改用 Git 文件清单与 ignore 规则，防止把 ignored 日志/缓存/探针依赖打入源码 ZIP。清理只针对已确认的过程产物；最终配套目录、本地 Maven 输入、构建缓存和必要基线保留。

## 本轮修复（2026-10-30 后半场）

上面四项已在本会话处理；结论仍是**未最终验收、不可据此发版**。本轮没有启动客户端或专服，没有运行远端 Actions。

### 1. 半屏切换后输入永久暂停（已修，红→绿）

- 根因确认：`UiView.suspendInput` 有两个写入者，`AcModalAdapter.blocking` 不知道 section 自己调过 `suspendInput(true)`，`sync()` 算出的 `next` 与 `blocking` 相同便不再恢复。
- 修法：暂停状态只由 `AcModalAdapter` 写。新增 `cancelGesture()`（抬旗→立即按 `!hostActive || dialog.isActive` 重算），`AtomLibAppearanceSection.cancel()` 改为走它；official 与 Fabric 孪生同步。
- 永久回归：`AtomLibAppearanceSectionTest.halfScreenToggleLeavesTheWidthSliderInteractive`（两份）。用真实 section 点击半屏开关 → 断言模式已提交 → `cancel()` → 真实宽度 track 的 PRESS/RELEASE 持久化 1 次；再点确认行打开 AC 弹窗，断言弹窗期间滑条仍被拦（仍 1 次）；`dialog.reset()` 后同一手势恢复到 2 次。
- 证据：修复前同一测试在第 55 行失败（模式已改、persist 为 0），日志 `build/atomlib-red-1.log`；修复后 Forge/Fabric 各 2 条用例通过。`AcModalAdapterTest` 3 条（弹窗拦截、焦点保留、离开页面不恢复）继续通过。

### 2. 原生 context 逃逸租借回调（已修）

- `SkiaGraphics.requireContext()` 删除，换成唯一入口 `withContext(Function)`，context 只在回调内有效。
- `ItemIconRenderer.prepare` 拆成三段：Skija 租借（`resetGLAll` + `flushAndSubmit`，并只在 `WeakReference` 里记住 context 身份）→ `vanillaPass` 内 `drawOffscreen`（宿主 FBO/纹理、原版物品绘制、无任何 Skija 调用）→ 第二次租借内 `Prepared.importInto`（`Image.borrowTextureFrom`）。异常时未导入的纹理在 `finally` 释放。
- 回归：Lib `hiddenGlTest` 新增两条——宿主 FBO 渲染 + 污染 PACK/UNPACK PBO、row length、skip rows、alignment 后，在租借内导入并读回像素，退出后逐项断言宿主状态原样；租借回调抛异常后借用过期、状态恢复、后续绘制正常。三端各 13 条（原 11）全绿。
- 静态守护：`NativeBorrowContractTest`（AC shared）断言 facade 不再暴露 `requireContext`、导入调用夹在两次租借之间、无强引用静态 context。已做变异测试：把 `requireContext` 装回去后该测试变红，随后按字节备份还原（SHA-256 `DB6BC99B…3B2B40` 校验一致）。

### 3. 共享 bridge 退出未释放（已接线，未实机验证）

- `MinecraftRenderBridge.releaseIfOpen()`：只在实例已存在时 close，绝不 `get()` 新建；关闭异常记 WARNING 不打断退出。
- Forge/NeoForge：`AtomLibMod` 用 `FMLEnvironment.dist.isClient()` 守卫，交给 client-only `AtomLibClient`，监听 `GameShuttingDownEvent`（`Minecraft#stop()`，渲染线程，窗口销毁前）。
- Fabric：无对应 loader 事件，新增 client mixin `MinecraftClient#close()` HEAD；`atomlib.mixins.json` + `fabric.mod.json` 接线，`defaultRefmapName=atomlib.refmap.json`。实测生成的 refmap 为 `Lnet/minecraft/class_310;close()V`，三个 jar 都含 mixin json 与 refmap。
- 未覆盖：真实客户端退出路径、专服启动、mixin 在 intermediary 运行时是否命中（只有 refmap 与编译证据）。

### 4. Linux CI 与测试 native 冲突（已改配置，未运行远端）

- `source`（ubuntu）只留纯 Java `core:test` 与三个 Python 校验脚本。
- 新增 `source-native`（windows-latest）跑 `:runtime:test :ui:test :devtools:test`，字体取 `C:/Windows/Fonts/msyh.ttc`、缺失时回退 `arial.ttf`；与 `hidden-gl`、`package` 一样只在 Windows 上跑 native 相关任务。YAML 已用 PyYAML 解析确认四个 job 结构有效。

### 本轮实际执行的验证

- Lib 先重新构建、发布到 `build/test-maven` 并 `verifyArtifact` 通过（23:08，`run_packaging.py platforms`）；随后把 `versions/atomlib.json` 的三端 SHA-256 更新为新包，三端 `verifyAtomLibInput` 均通过。
- AC 三端在**新 Lib 包**上重跑 `gradlew.bat test build -PatomlibMavenRepo=D:/AtomLib/build/test-maven`：Forge **1564**、Fabric **1576**、NeoForge **1564**，全部 BUILD SUCCESSFUL（较检查点各 +3：外观回归 1 + 借用契约 2）。三端日志都显示 `:compileJava` 与 `:test` 实际执行，不是 UP-TO-DATE。
- Lib `:core:test :runtime:test :ui:test :devtools:test`：9 / 23 / 82 / 2 = **116**，0 失败/错误/跳过。
- Lib 三端 `hiddenGlTest`：各 **13**，合计 39，0 失败/错误/跳过（新增 2×3）。
- Lib 校验脚本自测：artifact 29、consumer 2、UI/example 3 全部 OK；`run_packaging.py consumers` 六个独立消费者（examples a/b × 三端）全部 PASS。

### 本轮发现的一个验证陷阱

AC Fabric 的 Loom `remapped_mods` 缓存按坐标而非内容哈希命中：Lib 用同一个 `0.1.0-local` 版本重新发布后，AC Fabric 仍在用 17:01 重映射的旧包（缓存里没有 mixin），`test` 也就被判成 UP-TO-DATE。清掉该缓存条目后重跑，`:compileJava`/`:test` 才真正重跑，重映射包也确认包含 `MinecraftClientShutdownMixin` 与 refmap。以后 Lib 换包必须清这个目录，否则 Fabric 侧一直是假绿。

### 仍未覆盖 / 下一步

- 未启动客户端：半屏切换的真实观感、物品图标在真实 tooltip 中的显示、退出时的实际释放顺序都需要实机确认。
- Forge 重混淆开发客户端加载新 Lib（`cb612db` 之后新增的事件接线与 mixin 不进入 Forge 路径，但 Lib jar 已变）。
- 远端 Actions 未运行；Fabric mixin 的运行时命中只有 refmap 证据。
- 本地 Maven 输入已刷新，之前的候选 JAR 与配对归档需要重新导出，不能沿用旧包。

## 1.0.0-beta 测试部署（2026-10-30 收尾）

用户要求把本体版本号改为 `1.0.0-beta` 并部署到测试实例，用于本次实机验收。三个目标都以新版本重新构建（`build`），部署前对实际 JAR 运行 `tools/verify_jars.py --platform windows-x64 --profile atomlib-consumer`，全部通过。

注意产物形态已变：拆库后 AC 每个目标只出一个无 OS 后缀的包，并且**必须同时安装同目标的 AtomLib**，以前的单 jar 安装方式不再适用。

| 加载器 | 实例 | 安装 | 随装 Lib |
|---|---|---|---|
| Forge 1.20.1 | `1.20.1-main` | `atomchat-Forge-1.20.1-1.0.0-beta.jar` | `atomlib-1.20.1-forge-0.1.0-local.jar` |
| Fabric 1.21.1 | `1.21.1-CCB` | `atomchat-Fabric-1.21.1-1.0.0-beta.jar` | `atomlib-1.21.1-fabric-0.1.0-local.jar` |
| NeoForge 1.21.1 | `Mechanomania-航空学` | `atomchat-NeoForge-1.21.1-1.0.0-beta.jar` | `atomlib-1.21.1-neoforge-0.1.0-local.jar` |

旧包已备份到 `D:/AtomChat-Backups/2026-10-30-1.0.0-beta/20261030-234714/<加载器>/` 并逐个校验 SHA-256；替换后回读哈希一致，每个 `mods` 目录恰好一个 `atomchat*.jar` 与一个 `atomlib*.jar`，其他模组数量未变。完整记录见 `2026-10-30-1.0.0-beta-deployment.json`。

本次没有代启游戏；实机结论待用户反馈。验收重点：设置→外观→调整里的半屏开关与宽度滑条、物品图标显示、关屏与退出时无崩溃。

### 首次实机反馈后的修正（2026-10-31 00:10 重装）

用户实测三点，其中两条是缺陷：

1. **Forge/NeoForge 进不去**：AtomLib 声明 `forge [47.4.10,48)`、`neoforge [21.1.235,21.2)`，而测试实例是 Forge 47.4.1、NeoForge 21.1.228。已放宽为 `forge [47,)`、`neoforge [21.1,)`（AC 自己的范围本来就是这两个），重新发布 Lib 并更新锁哈希。`GameShuttingDownEvent` 在 47.3.0 的 jar 里已存在，47.4.1 可用；仍未在 47.4.1 实机验证退出路径。
2. **少了两项设置**：老页面「调整」chip 的透明度组（`opacity`、`cardtint`）在试点里没搬过来，属真 bug。已补回（带「透明度」分组标题），并把两组 slider 的筛选提成 `layoutSliders`/`transparencySliders`，新增回归断言两组 id，防止再次静默丢失。
3. **美术变化**：不是 Skija 剥离导致。原因有两层——`SettingRow` 只有「标题+副标题+控件竖堆进一张卡」这一种版式，表达不了 AC 旧的「行内控件 + 合并卡 + 分隔线」；主题 token 也只有四档间距/三档字号/两个圆角，没有卡片内边距、行高、分隔线、滑条粗细的独立旋钮。也就是说组件层缺一个 compact 行原语，加上试点没有做视觉对齐。**尚未修复，属于方案层面的取舍待定**（见下条）。
4. **「重置布局」是试点新增**：`dd635d15` 才引入，旧页面没有。保留待用户决定。

重装回执见 `2026-10-30-1.0.0-beta-deployment.json`（`backupRoot` 指向本次时间戳目录），仍然没有代启游戏。
