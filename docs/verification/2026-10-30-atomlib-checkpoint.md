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
