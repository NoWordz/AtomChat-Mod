# AtomChat / AtomLib 面板常驻掉帧：纯优化实施方案

**状态：已执行（2026-11-04 阶段 1–3 完成），停在 `WAITING_FOR_USER_IN_GAME_AB`。**
**执行结果见 `docs/verification/2026-11-04-panel-optimization-execution.md`（压缩后请先读它）**：
本轮候选**未取得性能收益**——真实 GL 证据否掉 P0-1 细粒度 reset（7 位不安全，补齐 11 位 ≈ `GL_ALL`，无可判定收益）；
P0-2 只省 5 次 `glGet*`（≈1%，未达方案意图）；仅剩 P0-3 点阵缓存，且 miss/滚动场景离屏更慢。
**Q2 本轮硬线（p50 ≤2.0ms / FPS ≥250）从机制上不可达**，建议按 §6 退回 Q2 重新分级、进入第二阶段。
两轮独立复核均「无阻塞问题」；三端候选已部署（默认关，12 个备份齐全），未宣布修复/达标。

本文件是**上下文压缩后的唯一恢复锚点**。压缩会丢掉对话里的推导与数字，但本文件自包含全部硬约束、路径与决策。
用户已明确：这里的「压缩」= 平台上下文压缩（compact），**不是精简方案文档**。

---

## 0. 压缩后恢复指令（写给压缩后的主 agent，先执行这一段）

1. 读本文件全文。
2. 读 `docs/verification/2026-11-04-panel-performance-intake.md` 第 7–11 节（证据、责任判定、Skija 用法）。
3. 读 `build/panel-perf-20261104/delivery-manifest.json`（构件/哈希/构建基线）。
4. **按 §1 决策表执行，不得重新询问用户**。Q1–Q7 全部已拍板。
5. 按 §7 生成两个**自包含** prompt（给绝对路径，不塞大段正文），顺序派子代理：
   实现 agent → 结束后另起独立 reviewer agent → 必要时 resume 实现 agent 修正 → reviewer 再复核。
   **同一时刻只活跃一个子代理。**
6. 主 agent 负责编排、数据对照、最终报告；**达标判定权归用户**。子代理不得宣布达标。
7. 不启动游戏、不 commit/push、不覆盖已发布版本、不改工作区 `versions/atomlib.json`。

---

## 1. 已锁定决策（Q1–Q7，全部用户拍板）

| # | 决策 | 结论 |
| --- | --- | --- |
| Q1 | 本轮性质 | **A：纯优化，画面逐像素不变**（golden frame 零差异）；一次只改一个因素；同场景交替 A/B。A 限制的是「画面」，不禁止架构改动，但本轮范围限第一级。 |
| Q2 | 验收分级 | **分两级**。本轮硬线：`panelNanos` p50 ≤**2.0ms**（现 4.0）、p95 ≤**4.0ms**（现 7.8）、外部游戏 FPS ≥**250**（现 170–180）、关面板基线 400 不变、golden frame 零差异、诊断开销可忽略。终极目标 ≥320–400（需 UI 层分层缓存）**单列第二阶段**，本轮不承诺。 |
| Q3 | lib 交付形态 | **A：内部实现级改动 + 同一个 JAR 内的开关**，公开 API 零变化；AC 对 P0-1/2 **零代码改动**；开关默认旧路径，确认收益后设新路径为默认，旧路径保留一版再移除；候选放**隔离 Maven 仓 + 明确锁文件**。 |
| Q4 | 子代理边界 | **B：允许把候选部署到三端既有实例**（先备份、验哈希、出部署回执），**仍不启动游戏**；不 commit/push、不覆盖已发布版本、不改工作区锁。子代理停在「产出候选 + 部署回执 + 等待实机 A/B」。 |
| Q5 | 统计口径 | **A：严格口径**。环境固定（关 Iris 光影、固定世界/视角/分辨率/GUI 缩放/VSync/帧率上限/后台负载），先确认关面板 400 基线；每支 3 轮、每轮每场景 ≥3000 稳定帧；A-B-A-B / B-A-B-A 交替，预热后丢前 100 帧；先测「对照-对照」噪声 σ，改动 p50 差异需 **>2σ** 才计收益；**同帧 p50/p95/p99 与外部 FPS 双指标 AND**；诊断开/关交替测整帧与分配；golden frame 逐像素 + 亮/暗截图；Fabric 必须达标，Forge/NeoForge 只要求不回归；两支独立判定，收益不相加。 |
| Q6 | 子代理编排 | **B：顺序起两个不同实例**（实现 → 独立 reviewer → 必要时 resume 修正 → 再复核），同一时刻只活跃一个；主 agent 编排/对照/报告；判定权归用户。 |
| Q7 | 「压缩」含义 | **平台上下文压缩**。方案落盘为本文件 = 唯一恢复锚点；压缩后主 agent 从文件恢复，不重新提问；子代理 prompt 自包含并指向绝对路径。 |

**已查清、直接定、不再讨论**：
- Skija **锁 0.143.18**，不升级、不换后端（保持 Ganesh）。Q1 零像素差异下升级版本必然改变 AA/字形/滤镜输出；0.143.18 已提供 `reset(BackendState...)`、`flushAndSubmit(surface, syncCpu=false)`、`Picture`/`Image`/`SamplingMode` 等所需 API。0.116.8 只在缓存里，实机没跑。
- 文字栅格化、颜色空间、图片降采样三条路径已有探针定因且**当前最优**，本轮一律不碰。hairline 1.85 设备像素锐度缺陷归第二阶段。
- 富文本/字体宽度**已有脏标记与缓存**（`SkiaFontRenderer.WIDTH_CACHE`、`MessageListView.layoutCache`/`kindCache`、按 `ChatStore` 不可变快照判脏、`rowTop` 按偏移跳过），`LIST` 实测 p50 仅 **0.29ms**（约 7% 面板成本）。**不是瓶颈**，不要再往这里使劲。
- `boolean full = true`（三端 `PanelBlurRenderer`）有测试断言，毛玻璃必须每帧刷新，**不能整帧缓存**；第二阶段只能缓存 UI 层。

---

## 2. 事实与责任判定（压缩后不可丢）

关面板 400fps（2.5ms）→ 开面板 170–180fps（5.6–5.9ms）；两轮 JFR 同帧实测 `panelNanos` p50 3.989 / 4.032ms，帧间隔 p50 5.633 / 5.608ms（≈177fps，与外部读数吻合）。

| 归属 | 段 | p50 (ms) |
| --- | --- | --- |
| **AtomLib 桥接** | `BRIDGE_ENTER`（`GlStateScope.capture` + `normalizePixelTransfer` + `ensureContext` + `resetGLAll` + `rebuild` 检查 + `session.paint` 进入） | **2.123 / 2.149** |
| **AtomLib 桥接** | `BRIDGE_EXIT`（`flushAndSubmit(surface)` + `GlStateScope.close`） | 0.373 / 0.378 |
| AC | `DRAW`（含 `DOTS` 0.922/0.876、`LIST` 0.29） | 1.153 / 1.161 |
| AC | `PRE_UI`（含 BLUR 0.196） | 0.200 |

**主要是 lib 桥接状态管理（约 2/3 常驻成本），AC 绘制内容约 1/3（点阵是大头）。不是 Skija 本身慢。**
段数值是嵌套墙钟，**不可相加**；`BRIDGE_ENTER` 是合并桶，内部占比尚未运行时细分。

代码层依据：`GlStateScope.capture()` 71 个静态 GL 调用点（44 `glGetInteger`）+11 循环，`close()` 41 个 +9 循环；按典型 GL 上限展开每帧约 **550–600 次同步状态查询 + 400+ 次恢复**；`SkijaRenderBridge.drawFrame:92` 每帧 `context.resetGLAll()` = `reset(GL_ALL=65535)`。Skia 官方明确「频繁清状态缓存相当昂贵」。`DotTexture.drawField` 最多 512×128 = 65536 次 `drawCircle`、`drawEdge` 512×9；典型每帧几千~上万条。

**微优化乐观上限 ≈1.2ms → ≈270fps**，仍达不到 400；要 ≥320 需第二阶段分层缓存。这点已向用户说明并接受分级。

---

## 3. 改动清单（本轮范围；每支独立 A/B）

| 支 | 仓 / 位置 | 改动 | 开关 |
| --- | --- | --- | --- |
| **P0-1** | `D:/AtomLib/platforms/shared/src/main/java/com/atom/lib/client/render/SkijaRenderBridge.java:92` | `resetGLAll()` → `reset(BackendState.GL_TEXTURE_BINDING, GL_RENDER_TARGET, GL_VIEW, GL_BLEND, GL_PROGRAM, GL_PIXEL_STORE, GL_VERTEX…)`，只重置外部真正改动的位；进一步用离屏 GL 验证「能否完全不 reset」 | `atomlib.bridge.fastState`（默认 false = 旧路径） |
| **P0-2** | `D:/AtomLib/.../GlStateScope.java` | 增量化：缓存不变的 `MAX_*` 查询、只在 GL 上下文/尺寸变化时重读、只捕获恢复 Skia 会改的子集；不得破坏 GL 状态/alpha 安全约束与资源生命周期 | 同上（同一 lib 支，与 P0-1 合并为一次 A/B） |
| **P0-3** | `D:/AtomChat-Main/shared/src/main/java/com/atom/chat/render/DotTexture.java` | 点阵预渲染成 `Image` 缓存（key: 条宽/高/radius/tint/above/主题/密度），尺寸或 tint 变化才重建，每帧一次 `drawImageRect`；9 种 alpha 已烘进纹理。参考 `docs/HANDOFF-render-sharpness-and-panel-perf.md` §2.1（该方案早已有，未实施） | `atomchat.dots.cached`（默认 false） |

**实验顺序（一次一个因素，不叠加计收益）**：① lib 开关 A/B（AC 点阵保持旧）→ ② ①达标并把新路径设默认后，再做 AC 点阵开关 A/B → ③ 合并复测。每步按 §5 口径。

**不在本轮范围**（第二阶段）：UI 层纹理缓存/分层合成、几何对象缓存、`notifyContentWillChange` 条件化、冷页面预热、hairline 吸附、Skija 升级。

**验收必须保留**：字体粗细、背景、点阵观感、导航打断、资源生命周期、既有主帧缓冲 alpha 安全约束。不得靠改画质/去动画/移除效果冒充性能修复。

---

## 4. 两仓边界、隔离与交付物

- 实机 AtomLib = `0d949b7949d4…`（`D:/AtomLib/build/rebuild-deploy-20261104`），**不是** final 候选 `a1da0ff0…`；当前 AtomLib 工作区源码与该实机版一致。AC HEAD `e92fdaf9…`，工作区 31 改 + 8 未跟踪（用户工作：字体复用、亮度钳制回退、JFR 诊断），**原地保护，不清理/不覆盖/不回退**。
- AC 工作区 `versions/atomlib.json` 仍锁 `0.1.0-beta`，但代码依赖 0.3.0 的 `FontSource.resource` → 按原锁编译必失败。**必须**用隔离源码快照（`build/panel-perf-20261104/prepare_source.py` 的模式）改快照内锁为 0.3.0 + 真实输入哈希，工作区锁不动。
- 快照必须排除 `.work/`（历史源码副本会让 `AvatarReadbackWiringTest` 源码守卫误判重复类）；NeoForge 需补 `minecraft_assets.properties`（SHA `015d0bc7…`）。
- 候选坐标沿用真实 0.3.0 但只放**隔离 Maven 仓**，绝不覆盖 `rebuild-deploy-20261104` 或实机已装包；不伪造坐标。
- 交付物：一个含开关的候选 JAR（三端）、SHA-256、隔离仓路径与锁文件、golden frame 报告、离屏 A/B 报告、三端部署回执（备份路径 + 原/新哈希 + 时间戳 + 未改动其他模组/配置的声明）、回退步骤、`delivery-manifest.json`。
- 构建命令基线见 `build/panel-perf-20261104/` 下 `final-*.log` 与 `delivery-manifest.json`；验证门：`python tools/verify_targets.py`、三端 `python tools/verify_jars.py --platform windows-x64 --profile atomlib-consumer platforms/<target>`、三端全量 test/build、wiring 探针。

---

## 5. 坑清单（历史踩过，必须写进子代理 prompt）

1. **别恢复 `BlurMotionGate`**；`boolean full = true;` 是有意的（每帧刷新毛玻璃，否则拖影），有测试断言。
2. `new Paint()` 默认 `antiAlias=true`，不是漏开抗锯齿。
3. `.gitattributes` `* text=auto`；Windows 工作副本 CRLF，Python `write_text()` 改行尾会让**按源码匹配的测试假失败**。写完归一化行尾。
4. `layers/mapping/official/` 与 `platforms/1.21.1-fabric/` 是**孪生**，两边同改，否则 `verify_targets.py` 失败；三端屏幕也要同改。
5. **注释可能过时**，以测试和 CHANGELOG 为准。特别是 `FrameProfile` 类注释「SkiaDraw 每次调用现造原生对象」**已过时**（现有 `SHARED_PAINT`/`BLUR_FILTERS`/静态 `DotTexture.DOT/GLOW/BLUR` 缓存），别照它改。
6. 已经做对、不要再"优化"：单 `DirectContext` 复用、Surface/BackendRenderTarget 尺寸不变不重建、Paint/MaskFilter 缓存、每帧一次 `flushAndSubmit(surface)` 且 `syncCpu=false`（非阻塞，已核实源码）。
7. 实机采样：PCL 用自带 JDK 25 + `-XX:+PerfDisableSharedMem`，`jcmd -l` 看不到游戏；用 WMI 找 PID + 游戏自带 JDK 的 `jcmd/jfr`；`JFR.start` 必须带 `filename=` + `dumponexit=true`；用 `docs/verification/2026-11-04-panel-sampling-kit/auto-record.ps1`（零交互）。
8. 面板帧间隔 ≠ 游戏整帧时间/FPS；独立分位数不可相减；CPU 墙钟高不等于 CPU 忙；GPU 查询未接线，本轮不产出 GPU 耗时。

---

## 6. 回退

- **开关默认旧路径**（两级开关各自独立），任何一支关掉即回现状。
- 三端原 JAR 与 config 均有 `.bak-<时间戳>` 备份；按部署回执逐文件还原并复验哈希。
- 隔离 Maven 仓 + 快照目录整体可弃用，工作区与实机已发布包不受影响。
- 任一支未达 §1 硬线或出现像素差异 → **该支整体回退**，不合并、不降画质凑数、不把多支收益相加充数；只保留独立可复现且有收益的支，方案退回 Q2 重新分级。

---

## 7. 子代理 prompt 模板（压缩后由主 agent 生成，自包含）

### 7.1 实现 agent（阶段 1）
必须写入 prompt：本文件绝对路径 + §1 决策表 + §3 改动清单 + §4 两仓边界 + §5 坑清单 + §6 回退；要求按 §3 顺序实现、先写可失败测试、加两个默认关闭的开关、跑 §4 验证门、出 §4 交付物与三端部署回执、停在等待实机 A/B；明确禁止：启动游戏、commit/push、覆盖已发布版本、改工作区锁、改公开 API、改画质/动画/效果、宣布达标。

### 7.2 独立 reviewer agent（阶段 2）
必须写入 prompt：本文件绝对路径 + 阶段 1 实际改动文件清单与 diff 位置；要求**只读复核**、输出阻塞/重要/次要/观察四级问题（含文件行号、原因、建议），重点：GL 状态与 alpha 安全、资源生命周期、开关关闭路径零额外 GL 操作、孪生一致性、golden frame 像素不变、点阵缓存 key 与失效、离屏 A/B 是否真的只改一个因素；禁止改文件、禁止运行构建。修正后需再复核一次。

---

## 8. 证据文件绝对路径（prompt 里只给路径，不塞正文）

| 用途 | 路径 |
| --- | --- |
| 证据/责任判定/Skija 用法 | `D:/AtomChat-Main/docs/verification/2026-11-04-panel-performance-intake.md`（第 7–11 节） |
| 锐度定因 + 点阵既有方案 | `D:/AtomChat-Main/docs/HANDOFF-render-sharpness-and-panel-perf.md` |
| 清晰度/缓存历史与 A/B | `D:/AtomChat-Main/docs/verification/2026-11-02-render-quality-and-caching.md` |
| 原 handoff（硬约束来源） | `D:/AtomChat-Main/docs/superpowers/plans/2026-11-04-panel-performance-handoff.md` |
| 构件/哈希/构建/测试基线 | `D:/AtomChat-Main/build/panel-perf-20261104/delivery-manifest.json` |
| 逐帧分析 | `build/panel-perf-20261104/analysis.json`、`compare-runs.json`、`supplement.json`、`log-profile.json` |
| 三份 JFR | `build/panel-perf-20261104/samples/panel-auto-20261104-202119.jfr`、`panel-auto-20261104-203101.jfr` |
| 采样包（零交互录制） | `docs/verification/2026-11-04-panel-sampling-kit/auto-record.ps1`、`atomchat-panel.jfc`、`scenario-script.md` |
| 像素基线 | `D:/AtomChat-Main/golden/`、`layers/mapping/official/src/test/java/com/atom/chat/render/GoldenFrameTest.java`、`platforms/1.21.1-fabric/src/test/java/com/atom/chat/render/GoldenFrameTest.java` |
| 隔离快照脚本 | `build/panel-perf-20261104/prepare_source.py` |
| AtomLib 桥接源码 | `D:/AtomLib/platforms/shared/src/main/java/com/atom/lib/client/render/SkijaRenderBridge.java`、`GlStateScope.java` |
| AC 点阵源码 | `D:/AtomChat-Main/shared/src/main/java/com/atom/chat/render/DotTexture.java` |
| Skija API 参照 | `io.github.humbleui:skija-shared:0.143.18` sources jar（`BackendState.java`、`DirectContext.java`） |

---

## 9. 完成定义

实现 agent 交付 §4 全部产物 + 部署回执即算阶段 1 完成；**不达标判定不在子代理**。
主 agent 出对照报告，用户在实机按 §5 口径验收并拍板；达标后主 agent 写最终报告（根因、证据、样本量、帧时间变化、诊断开销、AC/AtomLib 哈希、回归与实机结果、未完成项）。
**取得用户实机 A/B 前，不宣布掉帧已修复、不宣布 FPS 提升。**

---

## 10. 执行结果（2026-11-04，压缩后必读）

完整版：`docs/verification/2026-11-04-panel-optimization-execution.md`。产物 `build/panel-opt-20261104/`。

1. 阶段 1–3 已按 Q6 顺序跑完，两轮独立复核均「无阻塞问题」。三端全量 build 全绿（Fabric 1660 / Forge 1645 / NeoForge 1645 tests，0 failures/errors），验证门全 PASS，golden frame 逐像素 12/12 × 2 端。
2. **本轮未取得性能收益**。真实 GL 证据证明 P0-1 的 7 位 `BackendState` 白名单不安全（漏 4 位），补齐 11 位后 ≈ `GL_ALL`、**无可判定收益**；P0-2 因 GL/alpha 安全只省 5 次 `glGet*`（≈1%，未达方案意图）；P0-3 改用 `Picture`（`Image` 方案 1 LSB 差异违反 Q1），离屏全命中仅 −0.053ms，miss/LRU 场景反而更慢。
3. **Q2 本轮硬线（p50 ≤2.0ms / FPS ≥250）从机制上不可达**；§2 的「微优化乐观上限 ≈1.2ms」原本依赖 lib 支，现已不成立。责任判定未被推翻：BRIDGE_ENTER 2.12ms 的主成本是 `GlStateScope` 全量 capture/restore，本轮没动到。
4. 按 §6，建议**退回 Q2 重新分级**：先做 AtomLib 桥接内部细分诊断（同 frameId 打 capture/准备/reset/flush/close）→ 基于「宿主实际改哪些状态」的证据做状态子集裁剪 → 再进第二阶段 UI 层分层缓存（模糊每帧刷新、UI 层静止帧合成缓存纹理）。
5. 三端候选已部署（默认关）、两轮 12 个备份齐全；`atomlib.bridge.fastState` **禁止实机开启**；若仍要实机，只测 `atomchat.dots.cached` 且必须覆盖滚动/动画 miss 场景。
6. 未宣布修复/达标/FPS 提升；判定权归用户。用户可选：A 只测点阵支拿实机数据；B 直接进第二阶段（推荐，符合「帧率不能骤降」的预期）。
