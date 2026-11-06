# 2026-11-06 E0/E1 仪表能力：`measureOnly`（含 Shift+F6 运行时翻转）与屏幕无关的 `frame_pump`

**性质**：三端生产源码改动 + 共享诊断设施 + 三端隔离快照构建 + 候选包产出 + 单测/变异/拆包验证。
**未部署、未启动游戏、未真机验收、未 commit / push / 发布**；未替换实例 JAR、未改实例配置、
未动 `D:/Myworld/.minecraft/versions/*`；`versions/atomlib.json` 一字未动。
**本文所有数字均为「离屏/受控」或「构建/测试产物」，不是真机测量，也不构成任何 FPS 收益声明。**

对应计划：`docs/verification/2026-11-06-next-session-plan.md` §3.3（E0）、§4 E1、§6 实现要点、§9 红线。

---

## 0. 一句话

给 E0 加了 `diagnosticMeasureOnly`（帧探针照常，`FrameProfile` / `FrameSegments` / `PrepassProbe` 全关，
复用它们已有的 `enabled` / `probeOn` 门控，不改桶语义），并补了 **Shift+F6** 在同一会话内翻转它
（否则 ABAB 只能靠重启，统计上没有分辨力，见 §2.4）；给 E1 加了一个**与当前屏幕无关**的第二采样入口
`frame_pump`（自有窗口，三端各注册 1 处，G0–G3 共用同一点），并与 `frame_probe` 完全解耦、可交叉校准。
三端快照构建全绿、全部单测 0 失败、三处变异红灯→恢复绿灯、拆包标记齐全。

---

## 1. 改了什么

| 文件 | 行 | 内容 |
| --- | --- | --- |
| `shared/.../config/AtomChatConfig.java` | `:202` | 新增 `public boolean diagnosticMeasureOnly = false;`（字段风格/位置紧邻 `diagnosticRenderMode`） |
| `shared/.../diagnostics/Truth3Probe.java` | `:101` | 新增 `PUMP_LINE_RE`（`frame_pump` 行契约，字段形状与 `frame_probe` 完全一致） |
| 同上 | `:134` | 新增 pump **自有**环形窗口/排序缓冲/计数器（与 render 窗口零共享） |
| 同上 | `:221` | 新增 `instrumentEnabled(debug, measureOnly)` = `debug && !measureOnly`（E0 唯一门控式） |
| 同上 | `:318` | 新增 `onPumpFrame(nowNanos, debug, configMode)`（第二采样入口） |
| 同上 | `:419` | 新增 `pumpLine()`（与 `frameLine()` 同算法、只读 pump 窗口） |
| `shared/.../diagnostics/FramePump.java` | 新文件 | 宿主转发器：`AtomChatConfig.get()` → `onPumpFrame(...)` → `LOGGER.info(line)`；debug=off 只读一次字段即返回 |
| `shared/.../diagnostics/EnvironmentSummary.java` | `:96-97` | 先 `truth3.seedMeasureOnly(config.diagnosticMeasureOnly)`（配置只做初始值），再 `FrameProfile.setEnabled(Truth3Probe.instrumentEnabled(config.debug, truth3.measureOnly()))` |
| `platforms/*/.../render/PanelBlurRenderer.java` | fabric `:82/:231/:304`，forge `:99/:248/:316`，neoforge `:99/:248/:316` | 三处 `probeOn` / `PrepassProbe.onFrame` 门控改为 `instrumentEnabled(cfg.debug, Truth3Probe.get().measureOnly())`（运行时状态，不是配置字段） |
| `platforms/1.21.1-fabric/.../AtomChatClient.java` | `:54` | 新增**一处** `HudRenderCallback.EVENT.register((gui, ticks) -> FramePump.onFrame())` |
| `platforms/1.20.1-forge/.../AtomChatClient.java` | `:67` | 新增**一处** `MinecraftForge.EVENT_BUS.addListener(...::onRenderGuiPost)`，方法体转 `FramePump.onFrame()` |
| `platforms/1.21.1-neoforge/.../AtomChatClient.java` | `:62` | 新增**一处** `NeoForge.EVENT_BUS.addListener(...::onRenderGuiPost)`，方法体转 `FramePump.onFrame()` |
| `shared/.../Truth3Probe.java` | `:116` | 新增 `MEASURE_LINE_RE`（`measure_switch` 行契约，风格同 `SWITCH_LINE_RE`） |
| 同上 | `:243/:250/:263` | 新增 `measureOnly()` / `seedMeasureOnly(initial)`（一次性初始值）/ `toggleMeasureOnly()`（仅 debug=on，翻转并重置两个窗口） |
| `platforms/*/.../AtomChatScreen.java` | fabric `:3217`，forge `:3207`，neoforge `:3206` | `keyPressed` 新增 Shift+F6 分支（`(modifiers & 15) == 1`，仅 debug=on），三端逐字同源 |
| `shared/src/test/.../diagnostics/FramePumpProbeTest.java` | 新文件 | pump 行/窗口独立、debug 门控、切档重置、跨会话巨值、measureOnly 真值表、源码契约 |
| `shared/src/test/.../diagnostics/MeasureSwitchTest.java` | 新文件 | 5 个测试：Shift+F6 仅 debug=on、翻转重置两个窗口、配置只做初始值/不写回、默认行为不变、三端同源 |

**未改动**：三个 `AtomChatScreen` 的渲染路径（`PanelOpenMotion.PanelRect capture = PanelOpenMotion.blurCapture(` 仍各 1 处，只动了 `keyPressed`）、
`FrameProfile` / `FrameSegments` / `PrepassProbe` 的内部实现与桶语义、`SkiaGraphics`、
`boolean full = true;`、`errorCheckDue()`、视觉/动画/玻璃/模糊半径/采样 UV/capture 坐标、
`diagnosticRenderMode` 的 A/B/C/D 语义与 F6 循环、`versions/atomlib.json`、实例目录。

---

## 2. 为什么这么设计

### 2.1 `measureOnly`：走机制里已有的门控，不跳方法体

真实门控点（grep 后的实际赋值处，不是推测）：

| 仪表 | 真实门控 | 关掉后的行为 |
| --- | --- | --- |
| `FrameProfile` | `FrameProfile.setEnabled(...)`（`EnvironmentSummary.java:97`，每客户端 tick 同步一次） | `FrameProfile.start()` 返回 `0` → `frame()` 早退；panel/decode/layout 桶、surface/page 桶、JFR `UiFrame` / `ScreenLifecycle` 全部不记 |
| `FrameSegments` | **没有独立开关**：`SkiaGraphics.java:40` 的 `FrameSegments.begin(profileStart != 0L)` 由 `FrameProfile.start()` 派生；`FrameSegments.start(id)` 在 `current()==0` 时返回 0 | `begin(false)` → `id=0` → 无 GPU query、无 JFR `PanelSegments`、`summary()` 不会被打印 |
| `PrepassProbe` | `PanelBlurRenderer` 的 `probeOn`（render / renderReuse 两处）**与** `isAvailable()` 里的 `onFrame` 门控 | 不取 10 次分段 `nanoTime`、不 `record`、不推进 600 帧窗口、不打 `prepass_probe` |

三处宿主门控统一写成 `Truth3Probe.instrumentEnabled(debug, measureOnly)` 这一个纯函数，
避免「三端各写一遍 `debug && !measureOnly`」后漂移；单测直接覆盖真值表，
源码契约还断言每个渲染器恰好 3 次引用（`FramePumpProbeTest`）。第二个参数传的是
`Truth3Probe.get().measureOnly()`（**运行时**状态），配置字段只通过 `seedMeasureOnly` 提供首值。

**没有**用「跳过整个方法体」的粗暴写法：`FrameProfile` / `FrameSegments` 的桶语义、窗口、
采样口径一字未动，只是 `enabled=false` 这条既有路径。`measureOnly` 也不影响
`errorCheckDue()`（debug=on 时恒 true，既有行为，按要求未改），因此 E0 的差值里仍包含
每帧 `glGetError` —— 这一点必须在解读 E0 时写明（见 §5 风险）。

### 2.2 `frame_pump`：独立窗口、独立日志行、纯 Java

- **零共享**：pump 有自己的 `pumpSamples/pumpSortBuf/计数器/lastNanos`，`resetPumpWindow()` 只清自己。
  `onRenderFrame` 一行未改（diff 可核），历史 `frame_probe` 继续逐字节可比；单测用「先喂 300 帧 render、
  再喂 601 帧 pump、render 仍需再补 302 帧才出 600」证明两个窗口互不污染。
- **参数化 debug/mode**：`onPumpFrame(now, debug, configMode)` 不读 `AtomChatConfig`，因此
  `Truth3Probe` 仍满足「只 import `java.*`、不引用 MC/Skija/GL/SLF4J/FrameProfile」的纯 Java 契约
  （`Truth3ModeTest` 的 import 断言仍绿）。G0/G1/G2 没有 AC 屏幕，`configure()` 不会被调用，
  pump 直接按配置字段报档，不需要面板存在。
- **日志行**（与 `frame_probe` 同字段，只换行名，便于逐字段交叉校准）：
  `[AtomChat][probe] frame_pump mode=<A|B|C|D> n=<n> p50=<ms> p95=<ms> fps_p50=<fps> max=<ms>`
- **debug=off 静默**：`FramePump.onFrame()` 先读 `config.debug`，false 直接返回 ——
  不取时钟、不采样、不格式化、不打日志。产品默认每帧只多一次字段读取。
- **切档重置**：配置档位变化时只重置 pump 窗口（不出 `mode_switch`；该行仍由屏幕
  `configure()` 输出，语义未改），保证 A/B/C/D 数据不串。

### 2.3 每帧采样点的选型（已核实 jar，不是凭印象）

| 平台 | 事件 | 每帧次数证据 | 位置 |
| --- | --- | --- | --- |
| Fabric 1.21.1 | `HudRenderCallback.EVENT` | 项目已在用（通知 HUD），每帧一次 | `InGameHud.render` 尾部 |
| Forge 1.20.1 | `RenderGuiEvent.Post` | `ForgeGui.m_280421_` 在每个 overlay 循环之后 post 一次（`ForgeGui.java:136`） | `Gui.render` 内 |
| NeoForge 1.21.1 | `RenderGuiEvent.Post` | `GuiLayerManager.render` 在 `renderInner` 之后 post 一次（字节码 `GuiLayerManager.class` 已核） | `Gui.render` 内 |

三端各**只注册 1 处**（`FramePumpProbeTest` 断言 `FramePump.onFrame()` 在每个客户端源码里恰好 1 次，
且快照脚本再断言一次），不存在双计数。

### 2.4 为什么 `measureOnly` 必须能在同一会话内切换（Shift+F6）

E0 要测的是「全仪表 vs 仅帧探针」的差，量级估计只有 **0.02–0.05ms**；而同一台机器的整场时间漂移是
**0.4ms 量级**（`2026-11-06-mode-d-results.md` 的方法学纠错）。计划 §3.2 又强制 ABAB 交替、
每档 10–12 秒、4–6 个来回、只用相邻配对。若切换必须重启，两件事同时发生：

- 每次重启都把 0.4ms 级漂移重新洗一遍，0.02–0.05ms 的效应会被完全淹没；
- 重启后要重新站位、重新对齐视角，配对也不再是「相邻」的。

**更正一处前提（已核实）**：`AtomChatConfig.reload()` 并不是没有生产调用点 —— 三端
`AtomChatScreen` 的构造函数各调用一次（fabric `:1571`、forge `:1562`、neoforge `:1562`），
所以「改配置 → 关掉再开面板」确实可以不重启生效。但它仍然不适合 E0：

1. 重开面板意味着离开测量状态数秒，10–12s 的档被切成碎片，配对时间戳很难对齐；
2. 新 Screen 会重走 `init` 并重建页面状态，`frame_probe` 的 render 入口序列中断，
   `frame_pump` 的窗口会跨过这段空档（只能靠时间戳裁剪）；
3. `reload()` 整体替换配置实例，会把同一文件里其它字段的改动一起吸进来，
   「一次只变一个因素」不再成立。

因此加 **Shift+F6**：一次按键完成翻转，`frame_probe` 与 `frame_pump` 两个窗口同时重置，
日志里留下 `measure_switch` 时间戳，切换本身只占一帧。配置字段退化为「启动初始值」，
热键结果不写回文件（实验开关不该污染产品配置）。

选键理由：不新增物理键位；现有 F6 分支只接受「无修饰」或「Ctrl+Shift」，**单独 Shift 不与它冲突**；
Alt/Super 组合继续留给第三方 mod。

---

## 3. 新采样点的相位与风险

### 3.1 相位差（已用字节码核实）

HUD 阶段**先于** `Screen.render`：

- 1.20.1 `GameRenderer.render`：`Gui.m_280421_`（HUD）在 bci 613，`ForgeHooksClient.drawScreen` 在 bci 750。
- 1.21.1 `GameRenderer.render`：`Gui.render` 在 bci 606，`ClientHooks.drawScreen` 在 bci 745。

因此同一帧里 `frame_pump` 的时间戳早于 `frame_probe`，两者相差「HUD 结束 → 屏幕 render 入口」这一段
**固定相位**。帧间隔 = 相邻两次 `nanoTime` 之差，常相位在相减时抵消，所以两者的 **p50 应几乎相同**
（只受两段之间工作量逐帧波动影响）。AC 面板打开时两条线同时存在，可交叉校准：
若 `frame_pump` 的 p50 与 `frame_probe` 相差 > 0.05ms 且稳定偏向一侧，说明采样点本身选错了
（例如事件不是每帧一次，或时钟取在了屏幕内部）。

注意两者窗口**不对齐**：pump 从 debug=on 起一直采样（含无屏幕帧），probe 只在面板打开时采样；
校准应比较**同一时间段的 p50 分布**，不要拿两条独立的 600 帧行逐字段强配。

### 3.2 已知风险（如实列出）

1. **HUD 可能不渲染**：`hideGui`（F1）、部分全屏/加载状态、极少数 UI 覆盖层可能让 HUD 回调不执行。
   此时 `frame_pump` 的相邻间隔会变成 2 帧甚至更长 → `p50` 上跳、`fps_p50` 低于 F3 读数；
   若整段都不执行，则**一行都不出**。判据：每段 10–12 秒在 350 FPS 下应有约
   `10×350/600 ≈ 6` 行；行数或 `fps_p50` 明显对不上 F3 时，该段作废。
2. **口径一致性**：`n` 是窗口内**相邻采样点间隔**的个数（与 `frame_probe` 同一口径，600 个间隔 ≈ 600 帧）。
   回调漏帧时，一个样本覆盖多帧，`n` 仍是 600 但时间跨度变长 —— 由于 `fps_p50` 会同步下降，
   这一点是可发现的；E1 以 F3 的 FPS 为旁证，不要只看 `n`。
3. **泵自身开销**：debug=on 时每帧多一次 `nanoTime` + 十几次比较/写，量级远低于 pre-pass 分段；
   对所有对照组相同，不改变组间差。debug=off 时只有一次字段读取。
4. **G1 需要关闭 AC**：模组会拦截 `openChatScreen`（`MinecraftClientMixin`），`enabled=true` 时按 T
   打开的是 AC 面板。要测原版 `ChatScreen` 必须 `enabled=false`（见 §6.2），因此 G1 与 G3
   不能在同一配置状态下交错；G0 需要在两个配置块里各自重测，**禁止跨块合并**。
5. **`measureOnly` 不关 `glGetError`**：`errorCheckDue()` 在 debug=on 时恒 true，这是既有行为，未改。
   所以 E0 测的是「FrameProfile/FrameSegments/PrepassProbe 三件套」的成本，不含 GL 同步查询。
6. **窗口相位不对齐**：见 §3.1 末段。
7. **翻转有 1 tick 的传播延迟**：`PrepassProbe` 的 `probeOn` 下一帧就生效；`FrameProfile` 由
   `EnvironmentSummary.logOnce` 每 tick 同步，最多晚 50ms。两个窗口在按键当帧就被重置，
   所以最坏情况是「新窗口的前 1 tick 仍带着旧仪表」。E0 用 `measure_switch` 的时间戳裁掉
   按键后 1 秒再计数，即可完全避开。
8. **按键重复**：长按 Shift+F6 会随系统按键重复多次翻转（裸 F6 的循环本来也是这个行为）。
   验收时按一下即可；日志里连续多条 `measure_switch` 说明按久了，按最后一条切段。

---

## 4. 默认值等价性（`diagnosticMeasureOnly=false` + `debug=off`）

代码路径论证：

1. 门控式 `instrumentEnabled(debug, measureOnly) = debug && !measureOnly`；默认 `measureOnly=false`
   时与改动前的 `debug` **完全相同**，且它只出现在 4 个开关赋值点（`EnvironmentSummary` 1 处、
   渲染器各 3 处），没有新增分支改变任何渲染/日志路径。
2. `debug=off`：`FrameProfile.setEnabled(false)`（与改动前一致）→ `FrameProfile.start()=0` →
   `FrameSegments.begin(false)`；`PrepassProbe` 的 `probeOn=false`、`onFrame` 不调用；
   `Truth3Probe` 静默。**没有一条日志行新增/删除**。
3. 新增的 pump：`FramePump.onFrame()` 在 `!config.debug` 时立刻返回 —— 不取时钟、不写状态、不打日志。
4. `Truth3Probe` 原有字段/方法语义未改；新增的 pump 字段只在 `onPumpFrame` 里读写，
   `measureOnly` 运行时字段默认 false、只由 `seedMeasureOnly` / `toggleMeasureOnly` 改。
   默认配置 + 不按 Shift+F6 时，`measureOnly()` 恒 false，门控退化为原来的 `debug`。
5. 唯一可见差异：配置文件下次保存会多出 `"diagnosticMeasureOnly": false` 这个键（Gson 默认行为），
   不影响游戏内行为；热键结果**不写回**该键。

测试覆盖：`FramePumpProbeTest` 的默认值/真值表/静默断言、`MeasureSwitchTest` 的默认值与
「配置只做初始值」断言、既有 `Truth3ModeTest.freshConfigDefaultsToAAndDebugOffForcesAAndIsSilent`
（debug=off 1300 帧无输出）、`PrepassProbeTest`（debug=off 静默）全绿。

---

## 5. E0 的真实门控点（复核清单）

| 位置 | 改动前 | 改动后 |
| --- | --- | --- |
| `shared/.../EnvironmentSummary.java:96-97` | `FrameProfile.setEnabled(config.debug);` | `truth3.seedMeasureOnly(config.diagnosticMeasureOnly);`<br>`FrameProfile.setEnabled(Truth3Probe.instrumentEnabled(config.debug, truth3.measureOnly()));` |
| `platforms/1.21.1-fabric/.../PanelBlurRenderer.java:82` | `final boolean probeOn = AtomChatConfig.get().debug;` | `instrumentEnabled(probeCfg.debug, Truth3Probe.get().measureOnly())` |
| 同上 `:231`（`renderReuse`） | 同上 | 同上 |
| 同上 `:304`（`isAvailable`） | `if (cfg.debug)` | `if (instrumentEnabled(cfg.debug, Truth3Probe.get().measureOnly()))` |
| Forge / NeoForge | 同源三处（`:99/:248/:316`） | 同源三处，逐行一致（构建脚本 + `PrepassProbeTest` 的三端逐行比对仍绿） |

`FrameSegments` 的关闭是**连带**的：`SkiaGraphics` 用 `FrameProfile.start()` 的返回值决定
`begin(true/false)`，`FrameSegments.start()` 又要求 `current()==id`。这条链路既有的
`FrameProfile.enabled` 门控就是我们要的，不需要也不应该给 `FrameSegments` 加第二个开关。

---

## 6. 单次启动验收流程（E0 + E1 合并，可直接转述用户）

### 6.0 前置（一次性设置，之后整场不再改）

- Fabric 实例 `D:/Myworld/.minecraft/versions/1.21.1-CCB`（**本轮不部署**；等主代理统一投放候选包）。
- `atomchat/atomchat-client.json`：`debug = true`、`diagnosticRenderMode = "A"`、
  `enabled = true`、`diagnosticMeasureOnly = false`。
- 窗口/界面固定：窗口大小与全屏状态不变，GUI 缩放不变；
  面板 `panelWidth=480`、`panelHeight=860`、`uiScale=1.0`、`contentScale=1.0`、`halfScreenMode=false`。
- **同一站位、同一视角、完全静止**（相机不动、不转视角、不移动），同一维度同一时间（避免昼夜/天气变化）。
- 关闭 F3；无未读通知、无聊天滚动、无输入法；不要长按任何键。
- **不要按裸 F6**（仍可用，A→B→C→D→A）：它会切档并重置两个窗口，还会改渲染路径。全程保持 A 档。
- 每档 **10–12 秒**，每组 **4–6 个来回**，只用**相邻配对**求差；日志行自带时间戳，按时间切段。

### 6.1 E0：仪表标定（AC 面板打开，Shift+F6 交替）

1. 按 **Y** 打开 AC 面板，站定。此时 `measureOnly=false`（全仪表）。
2. **Shift+F6** 翻转 → 日志出现
   `[AtomChat][probe] measure_switch measure_only=true t=…`。
   等 **1 秒**（让 `FrameProfile` 的 tick 同步落地），再静止 **10–12 秒**。
3. 再按 **Shift+F6** → `measure_only=false`，等 1 秒，静止 **10–12 秒**。
4. 重复 **4–6 个来回**（全仪表 ↔ measureOnly），每次 10–12 秒。
5. 判据（计划 §3.3）：用**相邻配对**比较 `frame_probe.p50`（两档都有）与 `frame_pump.p50`。
   - 差 ≤ 0.05ms → 历史绝对值可用，继续。
   - 差 > 0.05ms → 此后所有绝对值先减该偏移，历史结论降级为「相对可比」。
   - 该差值**不含** `glGetError`（见 §3.2 第 5 条），解读时单列。
6. 观测签名：`measure_only=true` 的段里 `prepass_probe`、`AtomChat render profile …`、`gpu_panel` 停止；
   `frame_probe` / `frame_pump` 继续。若前者仍出现，说明翻转没生效（检查是否在 debug=on 下按的键）。

### 6.2 E1：外部尺子（G0–G3，同一启动内完成）

**块 A（`enabled=true`，AC 可用）：G0 / G2 / G3**

| 组 | 进入方式 | 该段应出现的探针行 |
| --- | --- | --- |
| G0 | Esc 关闭所有屏幕，站在世界里 | 只有 `frame_pump` |
| G2 | 按 **E** 打开原版物品栏 | 只有 `frame_pump` |
| G3 | 按 **Y** 打开 AC 面板（A 档） | `frame_pump` **和** `frame_probe` 同时出现 |

顺序：
1. `G0 12s → G3 12s → G0 → G3 …` **4–6 对**；
2. 再 `G0 12s → G2 12s → G0 → G2 …` **4–6 对**。
   （G3 段保持 `measureOnly=false`，即全仪表；E0 已经量过仪表成本，E1 不需要再切。）

**块 B（`enabled=false`，测 G1 原版聊天）：同一启动内切过去**

1. 关闭面板。把配置文件里的 `"enabled"` 改成 `false`（其余字段不动）。
2. 按 **Y**：AC 面板会打开并在首帧自动关闭 —— 这一步只为触发
   `AtomChatConfig.reload()`（构造函数里调用）。
3. 现在按 **T** 打开的是**原版 `ChatScreen`**。
4. `G0 12s → G1 12s → G0 → G1 …` **4–6 对**。
   该块只有 `frame_pump` 出数（没有 AC 屏幕，`frame_probe` 不存在）——这正是新采样点的用途。
5. 比较 `G1−G0` 必须用**块 B 内部的 G0 配对**，不得用块 A 的 G0：跨配置/跨时段的 0.4ms 级漂移
   会吃掉 0.1–0.3ms 的效应。
6. 结束后若要恢复：把 `"enabled"` 改回 `true`，按 **Y** 重开面板（reload 后面板保持打开）。

> G1 不能与 G3 同时存在，因为模组会拦截 `openChatScreen`（`MinecraftClientMixin`）：
> `enabled=true` 时按 T 打开的就是 AC 面板。块 A / 块 B 的切换只改 `enabled` 这一个因素。

### 6.3 每阶段该看到什么 / 怎么判废

| 检查 | 期望 | 无效信号 |
| --- | --- | --- |
| `measure_switch` | 每次 Shift+F6 恰好 1 行；`measure_only` 在 true/false 之间交替 | 连续多行（长按重复）；按了没行（debug=off 或不在面板里） |
| `mode_switch` | 只在首次打开面板 / 档位或 debug 真变化时出现 | E0/E1 过程中反复出现（说明误按 F6 或配置被改） |
| `frame_probe`（仅 G3） | 每段 10–12 秒约 `FPS×秒/600` 行，`n=600` | 该出现却没有；`n` 长期 < 600 |
| `frame_pump`（所有组） | 每段同样约 `FPS×秒/600` 行，`n=600`；G3 段与 `frame_probe` 行数大致 1:1 | 行数明显偏少 / 完全没有（HUD 回调漏帧，该段作废） |
| 交叉校准（G3） | `frame_pump.p50 ≈ frame_probe.p50`（差 < 0.05ms） | 稳定偏向一侧且 > 0.05ms → 先怀疑采样点，不是游戏性能 |
| `fps_p50` | 与 F3 的 FPS 同量级（允许小差） | 差一个数量级 → 该段作废 |
| `measure_only=true` 段 | `prepass_probe` / `gpu_panel` / render profile 块消失 | 仍出现 → 翻转未生效 |

报告里给**配对散点**（每个来回一个点），不要只给均值；不要把未测的数字写成收益。

---

## 7. 构建（三端隔离快照，`--offline --no-daemon`）

快照脚本：`build/e0e1-20261106/prepare_snapshot.py`（复用 mode-d 脚本，改路径 + 新增本轮标记断言）。
只改**快照内** `versions/atomlib.json` → `0.3.1-shadowcache`；工作区锁仍 `0.1.0-beta`。
NeoForge 资产预置 SHA `015d0bc7…` 校验一致；快照无 `build/` 泄漏。

构建脚本：`build/e0e1-20261106/build_three.sh`。最终一轮（源码与工作区逐字节一致）：

| 端 | 命令要点 | 结果 | 耗时 |
| --- | --- | --- | --- |
| Fabric 1.21.1 | JDK21 | **BUILD SUCCESSFUL**（`:test` 执行） | 90s（Gradle 报 1m28s） |
| Forge 1.20.1 | JDK17 + `-Dnet.minecraftforge.gradle.check.certs=false` | **BUILD SUCCESSFUL** | 101s（Gradle 报 1m39s） |
| NeoForge 1.21.1 | JDK21 + `-x downloadAssets` | **BUILD SUCCESSFUL** | 92s（Gradle 报 1m30s） |

> 上一轮（加 Shift+F6 之前）同一脚本：97s / 103s / 87s，同样全绿。
> 日志：`build/e0e1-20261106/build-{fabric,forge,neoforge}.log`。

### 7.1 单测（三端各自执行）

| 平台 | 套件 | 用例 | 失败 | 错误 | 跳过 |
| --- | --- | --- | --- | --- | --- |
| Fabric | 262 | 1724 | 0 | 0 | 7 |
| Forge | 258 | 1709 | 0 | 0 | 7 |
| NeoForge | 258 | 1709 | 0 | 0 | 7 |

指定必绿项（三端一致）：`Truth3ModeTest 12/0`、`ModeDCadenceTest 11/0`、
`PanelShellWiringSourceTest 4/0`、`NotificationRenderWiringTest 7/0`、`PrepassProbeTest 8/0`、
`FramePumpProbeTest 7/0`、新增 `MeasureSwitchTest 5/0`。
测试 XML：`.../build/atomlib-consumer/test-results/test/`。

---

## 8. 变异红灯（故意改坏 → 变红 → 恢复 → 变绿）

均在**快照**里做，工作区不动；恢复用工作区文件 `cp` 回去并 `cmp` 校验逐字节一致。
#1 / #2 在加 Shift+F6 之前的代码上执行（Fabric 1719 用例），#3 在本轮代码上执行（1724 用例）。

| # | 变异 | 结果 |
| --- | --- | --- |
| 1 | 删除 Fabric 的 `HudRenderCallback ... FramePump.onFrame()` 注册行 | `RED_RC=1`，`1719 tests completed, 1 failed, 7 skipped`，失败项 `FramePumpProbeTest > measureOnlyIsOffByDefaultAndWiredAtTheRealGatePoints`（注册数 0 ≠ 1） |
| 2 | `instrumentEnabled` 改成 `return debug;`（忽略 measureOnly） | `RED_RC=1`，`1 failed`，失败项 `FramePumpProbeTest > measureOnlyTurnsOffHeavyInstrumentsButNeverTheFrameProbes` |
| 3 | `toggleMeasureOnly()` 保留翻转、删掉 `resetWindow()` 与 `resetPumpWindow()` | `RED3_RC=1`，`1724 tests completed, 1 failed, 7 skipped`，失败项 `MeasureSwitchTest > toggleResetsBothFrameProbeAndFramePumpWindows` |
| — | 每次恢复后重跑 | `GREEN_RC=0` / `GREEN3_RC=0`，`BUILD SUCCESSFUL` |

日志：`mutation-red.log` / `mutation-red2.log` / `mutation-red3.log` / `mutation-green.log` / `mutation-green3.log`。

> **方法学披露**：第一次尝试是把注册行**注释掉**，测试仍绿 —— 因为源码契约测试按字符串计数，
> 注释里的 `FramePump.onFrame()` 也会被数到。真正的红灯改用「删除调用」完成，并在 §9 记录这一局限
> （源码契约测试对注释不敏感，这是既有测试族的共同特性）。

---

## 9. 校验脚本

| 脚本 | 结果 |
| --- | --- |
| `python tools/verify_targets.py` | **校验通过**（6 条目标，身份键/矩阵/工程一致） |
| `tools/probes/verify_profile_wiring.py`（由 `ProfileWiringIntegrationTest` 在三端各跑一次） | 三端测试均绿 → 脚本通过 |
| `python tools/verify_log_prefixes.py` | 改动前基线 **287 outlets / 16 violations**（`git archive HEAD` 树），本轮后 **291 / 20**：多出的 4 个出口全部是探针行（3 端 `measure_switch` 的 `LOGGER.info(line)` + `FramePump` 的 `frame_pump`），与既有 `frame_probe` / `prepass_probe` 同样「探针类生成整行、宿主原样输出」，不走 `AtomChatLog.message` 前缀。**该脚本在改动前就不是绿的**，本轮未新增前缀违规类别，如实记录。 |

---

## 10. 拆包证明

`build/e0e1-20261106/verify_jars.py`（纯 JAVA class 常量池扫描；扫描**全部** class 条目，不只 `com/`）。

| 目标 | 新增标记 | 既有不变量 | sha256 |
| --- | --- | --- | --- |
| Fabric | `frame_pump` / `measure_switch` / `diagnosticMeasureOnly` / `FramePump` | `Truth3Probe` / `PrepassProbe` / `frame_probe` / `mode_switch` / `errorCheckDue` | `3161fbdbd3eb93bdd4df8a2e55e819e6bb98fc42f8f670ed678605737059838a` |
| Forge | 同上 | 同上 | `8345db27b072ecae0ec00bb59da1561861cfb4dcb0d6251bd45922e1c8db6352` |
| NeoForge | 同上 | 同上 | `32d677dd8be2597a6a4a9fddbfd6d21acd0fa76d7ea4ec125c2a09b188b51842` |

- `BlurMotionGate`：**包内每个 class 均不存在**（脚本逐条扫描断言）。
- `boolean full = true;`：源码级常量，编译后是 `iconst_1`，无法作为字符串从常量池读出。
  它由 `prepare_snapshot.py`（每个渲染器恰好 1 处）、`Truth3ModeTest`、`PrepassProbeTest`
  对**同一份快照源码**断言 —— 本轮不伪称能从字节码读出它。
- 候选包：`build/e0e1-20261106/candidates/`；哈希清单：`build/e0e1-20261106/sha256.txt`。

---

## 11. 未做的事 / 未解风险

1. **没有真机数据**：本文不含任何真机 p50/FPS；E0/E1 的实际读数必须由用户按 §6 采集。
2. `measureOnly` 不含 `glGetError` 的调试成本（`errorCheckDue` 既有行为，按要求未改）。
3. HUD 回调在 `hideGui` 等状态可能漏帧；只能靠行数/`fps_p50` 与 F3 对账发现，无法在代码里绝对保证。
4. `frame_pump` 与 `frame_probe` 的窗口不对齐；交叉校准只能比同一时间段的分布。
5. G1 必须 `enabled=false`，因此与 G3 分属不同配置块；`G0` 需在两块各测一次，跨块不可合并。
6. 源码契约测试对注释不敏感（§8 披露）；这沿用了既有测试族的做法，未在本轮重构。
7. 三个 `AtomChatScreen` 只改了 `keyPressed`；one-transform 契约（capture 只 1 处）仍由构建脚本与
   `MeasureSwitchTest` 双重断言。
8. 配置新增键会在下次保存时写入 JSON；没有迁移逻辑（默认 false 即目标值）。
9. **运行时 measureOnly 不持久化**：重启后回到配置字段的初始值，这是刻意的实验语义；
   若用户想「重启后仍是 measureOnly」，需要改配置文件本身。
10. **Shift+F6 与 F6 的修饰位判断**：`(modifiers & 15) == 1` 只认「仅 Shift」；若某 mod 在 F6 上
    额外加了 Shift 且先吞掉按键，本热键可能收不到 —— 未在真机验证键位冲突。
11. **`EnvironmentSummary.logOnce` 的捕获兜底**：seed 与 `FrameProfile.setEnabled` 都在它的
    `try/catch (Throwable)` 内；极端情况下（如配置加载抛错）measureOnly 不同步，属既有失败模式。

---

## 12. 结论

本轮交付了两把可用的尺子：`diagnosticMeasureOnly` 让 E0 能在「全仪表 / 仅帧探针」之间用**现有门控**
切换，且默认值与 debug=off 路径逐字节等价；**Shift+F6** 让这个切换在同一会话内以一次按键完成，
并把配置字段降级为「启动初始值」、热键结果不写回；`frame_pump` 提供了一个与屏幕无关、三端同源、
自有窗口的每帧采样点，使 E1 的 G0–G3 第一次能用同一把尺子比较，并能在 G3 与 `frame_probe` 交叉校准。
三端构建与全部单测通过，三处变异红灯证明新断言真的会失败，拆包标记齐全；未部署、未启动游戏、未提交。
