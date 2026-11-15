AtomChat 0.8.0 followup 集中独审（2026-11-15）

**结论：P1 0 项、P2 0 项，当前审查范围内无确认阻断。** 这是代码审查与既有验证证据的结论；CCB 实机的输入、退场观感及整帧性能仍未验收，不能据此宣布“性能无损已实测”或游戏 FPS 提升。

审查截至北京时间 2026-11-15 15:00 左右，工作区为 `main@bb1448c254f7380cb0fdfeb38082eeb19a2cbd52` 加现有未提交改动。由本审查会话独立完成，未派其他代理。先读本轮计划 `docs/superpowers/plans/2026-11-15-v080-followup.md` 与 `docs/UI_CONTRACT.md`，以用户本轮确认的范围为准。只写本报告；没有修改生产代码、运行测试/构建、启动 MC、部署、提交或回退。

**审查范围与调用链**

| 核对项 | 当前代码与结论 |
| --- | --- |
| 构造入口 | Forge/NeoForge `AtomChatScreen.java:1834`、Fabric `net/minecraft/client/gui/screen/AtomChatScreen.java:1843` 用 `Minecraft.getInstance()` / `MinecraftClient.getInstance()` 读取世界后调用 `takeProgress`，没有在构造器读取尚未由 `Screen.init` 赋值的继承字段 `minecraft` / `client`。随后 `snapTo`，首次 render 再设开屏目标。未确认构造 NPE。 |
| 关闭事件与输入 | `ScreenEscInput.onKey → requestClose → setScreen(null)`；发送成功尾部与面板外左键也走同一关闭入口。三端 `requestClose` 在 `finally` 移除 Screen；Esc、发送按键和面板外关闭点击的调用者均返回消费，未继续落入 `super`。对照本地 MC/加载器源码及 Fabric 字节码，键盘消费会结束该事件；鼠标用已捕获的旧 Screen 调用并检查消费结果。`setScreen(null)` 恢复原版鼠标捕获与键位状态。没有为余下 180 ms 注册输入处理器，未确认吞后续输入或使关闭点击触发世界攻击的调用链。 |
| 退出资源 | 三端 `requestClose`（Forge/Neo `2691–2722`，Fabric `2703–2734`）在桥绘制回调中录制 `Picture`，再交给 `PanelCloseOverlay.begin`。退出对象仅持 Picture、矩形、style、世界/上下文身份及时间线，不持有 Screen/page/callback。`PictureRecorder` 关闭后 immutable Picture 独立持有命令；Screen 的 `composite.close`、`bottomChrome.close` 等不直接关闭交给退出对象的 Picture。`begin` 替换、`takeProgress`、`clear` 均有释放；未确认悬空 Java/native 包装器调用。带 GPU 图片的实际退场仍需实机覆盖，现有退出单测只有矩形。 |
| 退场绘制与坐标 | 两映射 `NotificationHudOverlay` 先执行 raw-GL blur，再在同一桥回调内画退出 Picture、通知与快捷键 HUD。模糊沿用 `PanelOpenMotion.blurCapture`，把面板虚拟坐标乘 density / GUI scale；Picture 在 density 缩放的画布中播放，两者重新使用同一 progress/style。HUD hint 以 GUI scale / canvas density 抵消面板缩放。MC 的 HUD 在 Screen 前绘制，符合开屏时快捷键先画、面板后画的顺序。未确认坐标或图层接线错误。 |
| 生命周期边界 | `PanelCloseOverlay.java:37–48,51–70` 在世界、density、视口变化、动画关闭或到期时清理，播放前另检查 context；两映射 HUD 在出现其他 Screen、缺世界/玩家、配置关闭或 renderer 已失败时清理。F1 隐藏退出与 hint，正常 HUD 回调仍推进到期清理。三端断线入口均新增 `clear()`。快速重开取当前 progress 并释放旧 Picture。未确认无限叠加退出对象或跨世界重放。 |
| 时间线和切页边界 | `PanelTimeline.java:10–25` 按单调时间采样 cubic ease-out，240/180 ms 有界无回弹；重复 target 不重启，反向从当前值接续，动画关闭直接落点。Screen 与 HUD 使用该时间线；没有把新时长接入切页控制器。旧 `PanelOpenMotion.closeDue` 的 6% 分支仍保留，但正常 requestClose 已立即移除 Screen，HUD 退出不调用它，不会把正常退场截成 6%。旧注释不作为现役时序依据。 |
| 限宽与默认值 | `UiLayout.java:237–243` 内容列恢复为 panelW 减原有 padding；header/input/list 随面板伸展。两映射 `SettingsHomePage.tileSide/tileRect` 保留 `SETTINGS_TILE_MAX` 和整组居中。`LayoutTokens` 的 HUD icon/font/gap 为 13/8/3.2 GUI 单位，符合约缩小 20% 的方向。`AtomChatConfig.java:132` 默认 SLIDE；`sanitizePageNavStyle → Gson load` 保留有效已保存 ZOOM/SLIDE，`SettingsTabDefaults` 从 DEFAULT 恢复当前页签，没有强制改写已有选择的迁移。 |
| 点阵批量化 | `DotTexture.java:164–210` 保留原 glow、clip、半径、圆角边缘公式与 ARGB alpha 计算；完全不透明度衰减的列按行提交 ROUND points，边缘渐隐仍逐圆绘制。对照当前 Skija 0.143.18 本地源码，ROUND point 的直径为 stroke width，PaintMode 在该 API 中忽略，当前 `radius*2` 契约正确。没有发现批量几何、tint 或混合顺序造成的确认回归；GPU/CPU 证据范围见下文。 |

上述 Screen 路径以 Forge/Neo 的 `com/atom/chat/screen/AtomChatScreen.java` 与 Fabric 的 `net/minecraft/client/gui/screen/AtomChatScreen.java` 为准；映射公共文件位于 `layers/mapping/official/src/main/java/com/atom/chat/`，Fabric 对应文件位于该端 `src/main/java/com/atom/chat/`。引用行号来自本轮实际读取。

旧 0.8.0 独立反应、Toast/FAB、主题、固定 tab 均保留；只为本轮关闭/布局的上下游关系读取必要代码，没有重新做这些功能的全面审计。

**性能证据与结论边界**

1. 原 CCB 会话 `build/v080-followup/ccb-user-session.log:5785–5836` 是 followup 前的真实实例数据：AC 0.8.0、Lib 0.4.0；settings repeat steady p50/p95 为 2.05/4.03 ms，transition 为 5.52/15.11 ms。末个汇总的 DRAW 为 1.302/5.198 ms，DOTS 为 0.640/0.945 ms，BRIDGE_ENTER 为 0.255/0.378 ms，BRIDGE_EXIT 为 0.420/2.203 ms；gpu_panel 为 1.0433/2.8167 ms。分段嵌套且分位数不能相加；这些数据支持继续测绘制提交和桥尾部等待，不能把 BLUR 的 CPU 数字当五次模糊的全部 GPU 成本，也不能直接断定 AC 或 Lib 是整帧瓶颈。
2. 当前 `SkiaGraphics.drawPanel` 才启用 panel profile；常驻 HUD 和新关闭捕获使用 `draw`，所以旧 panel profile 没覆盖完整 HUD/关闭路径。新关闭事件同步多做一次桥进入与 `drawPhone` 录制，然后 `removed()`；录制期间 `drawPanel` 还会刷新自己的 frameDt 和页面状态，并非已有画面的零成本转交。180 ms 退出则每帧执行 blur（适用时）、Picture replay 和 fade layer。未发现因此造成的确认功能缺陷，但微测未覆盖这条完整生产路径，不能由点阵收益推出关闭成本下降。
3. 14:52 的显式隐藏 GL 运行，本审查先读取了当时 XML：`V080RenderCostTest` 1 项，0 skip/fail/error；源码 `:59–62` 在 1 / 1.3 / .94 scale 对完整 framebuffer 字节数组作相等断言。当前全量测试已覆盖这份 XML，并因没有 `ATOMCHAT_BENCH=1` 把该用例记为跳过。历史输出现单独保存在 `build/v080-followup/render-cost-gpu-parity.txt`，对应日志 `dots-gpu-parity.log`；不要用当前 skip XML 否认历史运行，也不要把全量本次 skip 说成再次执行 GL。
4. 当前基准先把参考圆录成 Picture，再与缓存批量 Picture 对比，已排除“每帧 Java 逐圆 vs native 回放”的不公平比较。参考/batched CPU p50 两轮为 3.3062/3.1297、3.6407/3.4180 ms（约 5.3% / 6.1%）。GPU p50 为 0.1350/0.1093、0.1316/0.1268 ms；第二轮 GPU p95 batched 为 0.8825 ms，高于参考 0.8147 ms，不支持所有分位均改善的说法。空桥 CPU p50 从 0.8650 漂移至 0.4555 ms，跨轮漂移明显。
5. `V080RenderCostTest.java:35–47,94–112` 是 2560×1600 自建 FBO、RTX 5060 Laptop GPU、每样本前 glFinish 的受控组件实验。CPU 计入桥及其提交/状态处理，GPU 用 elapsed query；不能把 CPU 标为纯绘制运算或 GPU 完成时间。它没有 MC 世界、光影、Screen/HUD 调度、生产 raw-GL blur、关闭录制或窗口交换，不能证明 CCB 整帧/FPS 收益。
6. 本轮只读比对了测试 Maven 输入与当前 CCB 安装 Lib：整个 JAR 的 SHA 不同，但 `SkijaRenderBridge`、`GlStateScope`、`FramebufferTarget`、`BorrowedFrame` 四个相关 class 字节一致。只据此确认这四类的对应关系，不声称整包一致或全部 Minecraft 适配层相同；也没有根据另一个 Lib 工作区源码宣布已定位/修复库内瓶颈。

CCB 原日志 `:5840` 另有非 render thread 的 GL_INVALID_OPERATION，紧邻旧关闭事件；没有调用栈或同场景 A/B，暂不能归因到这次 followup 或 AC/Lib 的具体方法，不列为确认 P1/P2，也不宣称已修复。

**已读取的验证记录**

这些运行由实施流程完成，本审查没有重新执行。汇总命令在本审查只读解析现有 `TEST-*.xml`；历史 Gradle 日志没有完整命令行，不能冒充已核定其全部参数。

| 记录/时间（北京时间） | 实际范围与结果 |
| --- | --- |
| `build/v080-followup/fabric-full.log`，14:55:45 完成 | `test`、`build` 等任务 BUILD SUCCESSFUL；Fabric 当前 347 份 test XML 合计 2010 项、0 failure、0 error、10 skipped。包含 `V080RenderCostTest` 的显式 opt-in 跳过。 |
| 同次 XML，14:55:14 | `PanelTimelineTest` 3/3、`PanelCloseOverlayTest` 3/3，均零跳过。覆盖有界/反向时间线、基本录制播放/到期、世界/尺寸/context 清理、快速重开；不是 MC 输入或 GPU 图片销毁集成测试。 |
| 同次 XML，14:54:54–14:55:19 | `DotTextureCacheTest` 11、`DotTextureBoundedRendererTest` 10、`DotTextureTest` 2、`DotTextureTintContrastTest` 1、`UiLayoutTest` 17、HUD hint 两类各 1，均零失败/错误/跳过。既有大 halo 8 LSB 容差不用于声称本次 edge GPU 容差放宽；edge GPU 对比仍全字节相等。 |
| 同次 XML，14:54:17 | `ProfileWiringIntegrationTest` 2 项全通过；此前 `panel-wiring.log` 的缺 stub 编译失败是历史探针接线失败，不能当生产编译仍失败，也没有隐藏这条失败记录。 |
| 14:52 显式 GL | 当时 XML 1/1、无跳过；日志与留存输出如上。 |

没有将旧 Forge/Neo 全量结果当作本轮 followup 的构建通过；本报告落笔时未核验本轮这两端最终完整构建结果。既有测试通过不代替 CCB 实机。

**后续验证建议（非确认缺陷）**

1. CCB 按同一场景检查 Esc、发送成功自动关闭、面板外左键：关闭事件只消费一次；紧随其后的移动、鼠标转向、攻击/使用、滚轮快捷栏立即可用；画面退场期间不再响应任何面板操作。关闭后 30/90/150 ms 快速重开，核对连续位置和 hint 互补。
2. 在带壁纸、图片/动态图、表情及切页过渡的实际页面关闭，覆盖 Picture 所引用资源在 Screen `removed()` 后的播放；结合 F1、换 Screen、换世界、resize/GUI scale、资源重载核对没有残留模糊、双重面板或 native 异常。若有问题，限定复核 `requestClose → Picture → HUD` 及对应资源所有者。
3. 性能以 CCB 相同位置/光影/分辨率的前后对照为主，分别采 HUD 常驻、开屏、稳态、关闭触发帧、180 ms 退场、原有切页。把生产 HUD、关闭捕获与整帧时间纳入观测，再区分桥 CPU/等待、GPU 与 MC 其他工作；不要把受控点阵约 5–6% 推广为整帧收益。

无需因本报告新增锁、hook、SHA 门禁或重新扩审旧 WIP。若随后只修上述链路中的具体问题，复核改动及相关用例即可。

**15:09 限定增量复核：HUD CPU 桶、退出捕获 JFR 与通知清理**

结论仍为 **P1 0 项、P2 0 项，本次小增量无确认阻断**。仅复核 `FrameProfile` 新 HUD 接口、两映射 `NotificationHudOverlay`、三端 `requestClose` 的新增计时、`verify_profile_wiring.py` 与新补的 `FrameProfileTest`；没有扩大旧功能范围，仍只写本报告、未运行测试或构建。以下补充取代首审中“HUD 尚无独立计时”的现状描述；历史性能数据及其限制不变。

| 增量 | 核对结果 |
| --- | --- |
| debug 关闭时的开销 | `FrameProfile.java:184–194` 的 `startHud()` 仅读已有 volatile 开关，关闭时返回 0；`hudFrame(0)` 首条件返回，不再取时钟、更新桶、排序、拼字符串或输出日志。三端关闭入口复用 `screenStage/screenStageEnd`：debug 关闭时在 JFR EventType 检查及 event 分配之前短路。不是绝对零成本：有方法调用/分支及一个固定新增的 4096×long 样本数组（约 32 KiB，类初始化一次），没有这批诊断引入的每帧分配或计时。HUD 原先用于动画的 `System.nanoTime()` 仍存在，不属于新增采样开销。 |
| CPU 桶与报告隔离 | `HUD_BUCKET`、`hudFramesSinceReport` 独立于 panel/page/surface；`hudSummary()` 仅输出 HUD 行，不改原 `summary()`。600 次实际采样才触发 HUD 报告，`reset()` 同时清空该窗口和 cadence。`hudFrame` 不调用 `frame`、`FrameSegments` 或 JFR；两映射仍走 `SkiaGraphics.draw` 的 panel=false 路径，没有新增 panel ID、GPU query 或 UiFrame/PanelSegments JFR 事件。 |
| 采样边界 | official `NotificationHudOverlay.java:42–84`、Fabric `:44–86` 在可绘制判断之后开始，在 finally 采样：包含 host flush、raw-GL blur、桥进入/退出和绘制的墙钟耗时；不包含前面的 notification tick、可见性/几何判定、无绘制早退及正常汇总日志。异常帧也记入，且包含 catch 中的清理/告警开销。它不是纯 CPU 运算计数，也不是全体 HUD 回调成本或整帧耗时；GPU/等待可能体现在该墙钟中，不能与 panel/GPU 分位直接相加。 |
| 关闭捕获 JFR | Forge/Neo `AtomChatScreen.java:2697–2725`、Fabric `:2709–2737` 成对记录 `PANEL_EXIT_CAPTURE`。沿用现有 ScreenLifecycle event，新增 stage 字符串，不改原 frame/GPU/JFR 桶。时间覆盖捕获及异常处理，截止于 `screenStageEnd`，不包含后面的 `FirstOpenBudget.abort` / `setScreen(null)` teardown。`captured` 只有录制桥调用成功返回才为 true；动画关闭、进度为零或无世界的正常跳过也会产生 success=false 的 stage（若诊断/JFR 均启用），分析时不能把这些条目全部算为捕获异常。 |
| 去重与清理 | official `NotificationHudOverlay.java:36,77–84`、Fabric `:38,79–86` 的 `!exiting` 同时抑制新 HUD banner 的模糊、图标准备及绘制，冻结 Picture 中已有通知继续随退场播放；hint/退出画面本身不被该条件阻止。到期或其他清理令 exiting=false 后才恢复普通 HUD banner。失败 catch 先 `exit.clear()` 再置 failed，finally 的采样不重新持有或播放 Picture。没有确认资源延迟到下一帧才释放的新增缺口。通知 tick 仍照常推进，所称“延迟”是延迟显示，未暂停通知的生命周期。 |

`tools/probes/verify_profile_wiring.py:58–84` 新增 `hud n=1` 断言，并保留 panel 无样本、segments 无 HUD、嵌套排除及 UiFrame/PanelSegments 配对检查；不是仅靠另一个 HUD stub 自报计数。实际编译的 `FrameProfile` 与两映射 Overlay 来自工作区，HUD 图形/MC/GL 仍是 stub，不能验证退出画面、GPU 或真实输入。`FrameProfileTest.java:43–55` 在复核过程中已补入：disabled 下不记 HUD、600 次独立报告、HUD 不污染 panel 及 panel cadence；代码已读，尚未看到覆盖这些新增断言的执行结果。可在其定向运行中顺带覆盖 reset 后 HUD 无样本/重新计数；无需重审旧功能。

验证状态：Fabric 14:55 全量 2010/0 fail/10 skip 与 build 成功发生在增量之前，继续只作为原范围证据。Forge `forge-full.log` 15:07 显示 BUILD SUCCESSFUL，但本轮只读看到其 `FrameProfile.class` / Overlay class 时间为 15:04:52 / 15:04:54，早于新增源码的 15:05:58 / 15:06:30；`FrameProfileTest` XML 仍为旧 9 项，也早于 15:08:25 补入的新测试。因此不能把该次 Forge 全量成功升级为这批新计数/清理已构建验过。后续只需补当前增量的定向验证及相关平台构建结果，不因报告更新而重复全量扩审。
