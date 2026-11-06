# 2026-11-06 「三档归零对照」诊断实验包（Truth3）

**性质**：实验包源码修改 + 三端隔离快照构建 + 候选包产出。
**未部署、未启动游戏、未真机验收、未 commit / push / 发布**；未替换实例 JAR、未改实例配置/启动器、未改实例目录 `D:/Myworld/.minecraft/versions/*` 下任何文件；**未安排用户验收，由主代理统一处理**。
**全部数值标注「离屏/受控场景」或「真机待验」；不把任何离屏/受控数值宣称成 FPS 收益。**

---

## 0. 一句话

在**同一个包**里加 `debug`-only 三档旁路开关 + 一个**与 `drawPanel` 完全解耦**的独立帧间隔采样器，让用户在**一次启动、同一场景、同一份 `latest.log`** 里切 A/B/C，直接测出「这个 216-mod 环境下 400 FPS 是否物理可达」，从而判定瓶颈在不在 AC/Lib 面板渲染。

设计依据直接采信主代理 `docs/verification/2026-11-06-truth-test-log-analysis.md` §7 / §8 / §9（本轮不重做日志分析）：

- `chat_interval p50 = 3.09 ms ≈ 323 FPS`；AC 面板 CPU `chat_render p50 = 1.89 ms`；**非 AC 约 1.20 ms 是环境地板**（真机历史日志，§8）。400 FPS 需 2.5 ms/帧，AC 要再降 31%。
- **P1 的 `gpu_panel` 绝对值不可信**（§7 的 1.46→0.13 ms 未解释阶跃、同窗 p95=9.55 ms、CPU 反升）。本实验**不依赖它、不试图修它、一次只动一个因素**。
- 用户拍板：用「同包三档旁路」而不是照抄 Muonium；**实验包内允许临时改变视觉，但产品默认必须仍是每帧新鲜毛玻璃，可一键回退**（§9）。

---

## 1. 三档语义（一次启动、同场景、同日志）

| 档 | 内容 | 回答的问题 |
| --- | --- | --- |
| **A** | 完整 = 当前产品行为，**视觉逐字节不变** | 基线 |
| **B** | **只跳过背景模糊 pre-pass**，其余 UI 绘制全部照跑 | 毛玻璃占多少 |
| **C** | **整个 `drawPanel` 调用旁路**，只留 MC 本体 | 这个 216-mod 环境下 400 FPS 是否物理可达 |

### 各档的精确实现（`Truth3Probe.drawPanel()` / `blurPrepass()`）

| 档 | `drawPanel()` | `blurPrepass()` | 实际执行 |
| --- | --- | --- | --- |
| A | `true` | `true` | 与现状完全一致：`preUi`（`PanelBlurRenderer.render` 背景模糊）构造并执行；`graphics.drawPanel` 照常调用（`FrameProfile` / `FrameSegments` / P1 GPU 计时照常出数）；`renderer.accept`（`drawPhone` + `chatEffects`）全部照跑 |
| B | `true` | `false` | `AtomChatScreen.java:1991`（Fabric；Forge/NeoForge 同源补丁见 §4）的 `if` 不构造 `preUi` → `blurPreUi == null` → `drawPanel` 的 preUi 回调里 `if (blurPreUi != null)` 跳过 **BLUR 段**；但 `ItemIconRenderer.prepare`（ICONS 段）、`graphics.drawPanel`、`renderer.accept` 全部照跑。`blurDrawnThisFrame=false` → `drawPhone` 用 solid fallback 背景。**不改模糊半径 / 采样 / 圆角 / alpha / capture 坐标语义**（那些代码一行未碰） |
| C | `false` | `false` | `if (Truth3Probe.get().drawPanel())` 包住的 `graphics.drawPanel(...)` 整块不执行 → 不画面板 Skia、不画背景模糊；`AtomChatScreen.java:2040` 之后的 vanilla pipeline 与 render 方法中 drawPanel 之前的幂等准备保留 |

**C 档「只留 MC 本体」的保守性（如实披露）**：C 档仍执行 `graphics.checkFrameBufferId()`（空方法，`SkiaGraphics.java:20`）、`PanelBlurRenderer.ensureLoaded()`（幂等 shader 加载）、以及 `drawPanel` 之后的 vanilla pipeline（第三方 widget / suggestor / hover tooltip）。这些**都不是面板 Skia 绘制**，静止验收场景下开销可忽略；因此 C 档测得的是**略保守**的环境天花板（略慢于纯 MC）。即便 C 档上不了 400，「瓶颈不在 AC 渲染」的结论只会更强。

**B/C 档的视觉变化只在实验包内、只在按键/改配置后、仅诊断用途**。产品默认（全新安装 / `debug=off` / 未按键）恒等于 A。

### 切回 A 无损（离屏证明）

- **C 档根本不进入 `SkiaGraphics.draw` 的任何一行**：不会 `canvas.save()` / `restoreToCount()`（无 save 栈不平衡）、不会 `borrowedSurface/borrowedBackend.set()`（无 ThreadLocal 残留）、不会 `PanelBlurRenderer.render()`（不碰纹理/FBO 状态）、不会 `FrameSegments.begin/finish`（不碰 P1 GPU query 区间）。`Truth3Probe` 是纯 Java，源码里没有任何非 `java.*` 的 import（测试 `switchingBackToAIsLossless` 逐行断言）。
- **切回 A 时** `drawPanel=true`、`blurPrepass=true`，`SkiaGraphics.drawPanel` 每帧自包含地从干净状态完整执行（save/restore 成对、finally 里 `ThreadLocal.remove`、`FrameSegments.finish`）；`blurDrawnThisFrame` 每帧在 `AtomChatScreen.java:1983` 重置为 false 后由 pre-pass 置 true。因此 A 档逐字节等同从未切过档。
- 档位循环 A→B→C→A 后 `mode()==A` 且两个决策点与初始 A 相同、采样窗口被重置、仍能正常出数 —— 离屏冒烟 `ProbeSmoke.java` 与单测 `switchingBackToAIsLossless()` 双重证明。

---

## 2. 热键与配置字段

| 项 | 值 |
| --- | --- |
| **主热键** | **`F6`**（GLFW `295`）裸按，循环 **A→B→C→A** |
| **备用热键** | **`Ctrl + Shift + F6`**（当某 mod 占用裸 F6 时） |
| 排除 | `Alt` / `Super` 组合不触发（留给第三方 mod，避免抢键）；`Shift+F6` 也触发 |
| **门控** | **仅 `config.debug = on` 生效**；`debug=off` 时热键完全不生效且档位恒 A |
| **配置兜底** | `atomchat/atomchat-client.json` 的 **`diagnosticRenderMode`**，默认 **`"A"`**，合法值 `A`/`B`/`C`（大小写严格），其它/缺失一律回退 `A`；**重开面板 / 重启即生效**（`AtomChatConfig.reload()` 每屏打开时重读） |
| 切档日志 | 每次切档（热键或配置变化）打一条 `mode_switch`，含 `from`/`to`/`t`，并**重置采样窗口** |

### 键位冲突静态核对（未启动游戏，无法动态枚举 mod 注册）

- MC 1.21.1 vanilla 默认键位实测（`D:/Myworld/.minecraft/options.txt`，**只读**）：`F2` 截图、`F5` 视角、`F11` 全屏已占；**`F6` 未出现在任何 vanilla 绑定**，MC 默认也不对 F6 触发行为。
- AC 自身源码全库检索：`GLFW_KEY_F6` / `key.f6` **无任何绑定**（`astra` 报告里的「F6」是发现编号 F1..F11，不是键位）。
- **遗留风险**：216 个 mod 在运行时注册的 `KeyBinding` 无法静态枚举，不能排除某个 mod 占用裸 F6 —— 故提供**备用键 Ctrl+Shift+F6** 与**不依赖按键的配置兜底字段**。真机首次验收时应先按一次 F6 确认日志出现 `mode_switch`，若无则改用备用键/配置字段。

---

## 3. 独立帧时间采样器（最关键）

**类**：`shared/src/main/java/com/atom/chat/diagnostics/Truth3Probe.java`（纯 Java，只 import `java.util.*`）。
**位置**：三端 `AtomChatScreen.render(...)` 入口、`lastFrameNanos = nowNanos;` 之后、`drawPanel` 之前（见 §5 行号）。**复用入口已有的 `System.nanoTime()`（`nowNanos`），不额外取时钟**；与 `drawPanel` / `FrameProfile` / `FrameSegments` / `GlGpuTimer` 完全解耦，因此 **C 档（不调用 `drawPanel`）仍出数**。

- **有界环形窗口** `WINDOW = 1024`（`long[1024]`，8 KiB）+ 预分配排序缓冲 `long[1024]`（8 KiB），固定 **16 KiB**，与帧数/运行时长无关，**无界增长为 0**。
- 每 `REPORT_EVERY = 600` 帧输出**一行**稳定可解析日志；取窗口内最近 600 个样本算 `p50/p95/max`。`String.format(Locale.ROOT, ...)` 保证小数分隔符不随系统 Locale 漂移。
- 相邻间隔 `> 1 s`（`MAX_FRAME_INTERVAL_NS`）视为非连续帧（屏幕关闭/重开、会话切换、系统暂停）丢弃，避免污染 `max`；真机已记录长帧峰（125–153 ms / ~80 ms，§4）远低于 1 s。
- **debug 门控**：`debug=off` 时采样器仍记录（与渲染路径解耦）但**不输出任何日志、不排序、不格式化** → 产品默认行为逐字节不变；`debug=on` 时三档都出数。
- **不用 `System.currentTimeMillis`**、不每帧打日志、不分配无界对象。

### 日志格式（三档同一采样器、同一代码位置、同一输出格式）

```
[AtomChat][probe] mode_switch from=A to=B t=2547927836800
[AtomChat][probe] frame_probe mode=A n=600 p50=3.092 p95=4.510 fps_p50=323.4 max=15.28
```

| 字段 | 含义 |
| --- | --- |
| `mode` | 当前档 `A`/`B`/`C`（**必须含**，解析脚本按它归档并校验串档） |
| `n` | 本次统计的样本数（稳态 `600`） |
| `p50` / `p95` / `max` | **帧间隔毫秒**（屏幕 render 调用间隔），p50/p95 3 位小数、max 2 位小数 |
| `fps_p50` | `1000 / p50`，1 位小数（离屏/受控换算，真机待验） |
| `from` / `to` | 切档前后档位，供主代理按档切分日志 |
| `t` | `System.nanoTime()` **单调时钟值（非墙钟）**，仅供排序/去重；解析按 `from/to` 切段，不依赖其语义 |

切档时 `resetWindow()` 清空 `size/head/sinceReport` 并把 `lastNanos=-1`（断开上一帧时间戳），**保证每档数据不串**。契约正则导出为 `Truth3Probe.FRAME_LINE_RE` / `SWITCH_LINE_RE`，与解析脚本 `parse_truth3_log.py` 同形状。

### 采样器每帧开销上界（静态推理 / 离屏，非真机测量，不代表 FPS 收益）

| 项 | 上界 |
| --- | --- |
| 稳态每帧 `onRenderFrame` | 1 次 long 减法、2 次比较、1 次 `long[]` 写、1 次取模/条件自增、4 个计数器自增 ≈ 十余条指令；**0 字节分配** |
| `debug=on` 每 600 帧额外 | 至多 600 个 long 拷贝 + `Arrays.sort`（上界 `600·⌈log2 600⌉ ≈ 6×10³` 次 long 比较/交换）+ 6 次 `String.format`（各 1 个 `Formatter`+`StringBuilder`+若干 `String`，全短命）+ 1 行日志 |
| 摊还到每帧 | ≤ 10 次 long 比较、≤ 0.02 个短命对象量级、`1/600` 次日志 I/O |
| `debug=off` | 无日志 I/O / 无格式化 / 无排序，只有稳态那十余条指令与 0 分配 |
| 内存 | 固定 16 KiB，与帧数无关 |

### 三种口径不可混（明确声明）

本采样器量的是**屏幕 `render` 入口相邻两次调用的 `System.nanoTime()` 差值 = 游戏帧间隔**。
它**不是 GPU 完成时间**（P1 `GL_TIME_ELAPSED` query，§7 已判定绝对值不可信）、**不是 CPU 渲染耗时**（`FrameProfile` 的 `panel`/`chat_render` 桶）、**也不是 F3 的 FPS 时钟**。三者口径不同，**不可混用、不可相减**。本实验只用帧间隔 p50/p95 做 A/B/C 相对比较，不用 `gpu_panel`，不宣称任何 FPS 收益。

### 档位不影响 `FrameProfile` 原有桶语义

`FrameProfile.java` 一行未改（`CAPACITY=4096`、`REPORT_EVERY_FRAMES=600`、`PANEL/DECODE/LAYOUT`、`Surface`/`Page` 枚举、`page_phase` 全保留）。单测 `modesLeaveFrameProfileBucketsUntouched()` 断言这些常量原样，且 `Truth3Probe` 不 `import` / 不调用 `FrameProfile.frame(` / `FrameSegments.*` / `GlGpuTimer.*`。B 档 `drawPanel` 仍调用 → 原有桶照常出数；C 档不调用 → 原有桶按设计不出数（这正是独立采样器存在的原因）。

---

## 4. 改动文件清单（file:line）

| 文件 | 行号 | 改动 |
| --- | --- | --- |
| `shared/src/main/java/com/atom/chat/diagnostics/Truth3Probe.java` | 全文（新增）；`:40` 类、`:46` `WINDOW=1024`、`:48` `REPORT_EVERY=600`、`:99` `configure`、`:115` `cycle`、`:130/:132` 两个决策点、`:141` `onRenderFrame`、`:162` `resetWindow`、`:169` `frameLine` | 独立帧间隔采样器 + 档位状态机 + 日志格式化（纯 Java） |
| `shared/src/main/java/com/atom/chat/config/AtomChatConfig.java` | `:188`（新增）；原 `:181` `debug` 未动 | 新增 `public String diagnosticRenderMode = "A";` |
| `shared/src/test/java/com/atom/chat/diagnostics/Truth3ModeTest.java` | 全文（新增，11 个 `@Test`） | 默认 A / 日志可解析 / 切档重置 / C 出数 / B 只跳 pre-pass / 切回 A 无损 / 桶不变 / 巨值过滤 |
| `platforms/1.21.1-fabric/.../AtomChatScreen.java` | `:1417-1418` 常量、`:1956-1963` render 入口、`:1992` blur 条件、`:2024-2025` drawPanel 包裹、`:3181-3188` keyPressed | 三档旁路 |
| `platforms/1.20.1-forge/.../AtomChatScreen.java` | `:1408-1409`、`:1947-1954`、`:1983`、`:2015-2016`、`:3171-3178` | 同上（**同源补丁**） |
| `platforms/1.21.1-neoforge/.../AtomChatScreen.java` | `:1408-1409`、`:1947-1954`、`:1983`、`:2015-2016`、`:3170-3177` | 同上（**同源补丁**） |

**未改动**：`layers/mapping/official/.../SkiaGraphics.java` 与 `platforms/1.21.1-fabric/.../SkiaGraphics.java`（孪生，本轮一行未碰；`verify_targets.py` 的 twin 检查通过、两份 sha1 不同且都在）、三端 `PanelBlurRenderer.java`（P0-A 资源复用语义、`boolean full = true;`、无 `BlurMotionGate`）、`FrameProfile.java` / `FrameSegments.java` / `GlGpuTimer.java` / `GpuTimerQuery.java`（P1 GPU 计时）、`versions/atomlib.json` 工作区（仍 `0.1.0-beta`）、`docs/` 已有报告、shader、实例目录、`build/*/source/**` 历史快照。

三端屏幕的 5 处补丁由 `build/truth3-mode-20261106/apply_source_patches.py` **同一套 anchor** 字节级应用（保持各自原有行尾：三端 LF、config CRLF），`verify_sync.py` 逐行比对三端 13 行 Truth3 代码**完全一致**。

---

## 5. 验证结果（红绿 + 构建）

| 门 | 结果 |
| --- | --- |
| `python tools/verify_targets.py` | **通过**（6 目标，含 mapping twins / shared Java 级别 / 第三方依赖 / seams 文档） |
| `python build/truth3-mode-20261106/verify_sync.py` | **通过**：三端同源、`boolean full = true;` 三端各 1 处 / `BlurMotionGate` 0 处、official↔fabric SkiaGraphics 两份都在且 sha1 不同、工作区锁仍 `0.1.0-beta`、采样器仅 `java.*` import |
| 三端隔离快照构建 | Fabric **BUILD SUCCESSFUL 3m8s**（JDK21）、Forge **2m55s**（JDK17 + certs=false）、NeoForge **2m4s**（JDK21 + `-x downloadAssets`）。快照 `copytree` 排除 `.git/build/.gradle/.work/.workbuddy/*.log/__pycache__`；**只改快照内** `versions/atomlib.json` → `0.3.1-shadowcache`；NeoForge 已知坑 `build/atomlib-consumer/moddev/minecraft_assets.properties` 已从 P1 快照预置，SHA `015d0bc7…` 校验一致。工作区锁一字未动（`git status --short versions/atomlib.json` 为空） |
| `Truth3ModeTest` | **三端各 11/11、0 failures、0 errors** |
| `NotificationRenderWiringTest.java:46-52` | **三端各 7/7、0 failures**（`boolean full = true;` / 禁 `BlurMotionGate` 仍绿） |
| **变异红灯** | 把「默认 A」装回缺失（快照内 `diagnosticRenderMode = "C"`）：`RED_RC=1`，`Truth3ModeTest > freshConfigDefaultsToAAndDebugOffForcesAAndIsSilent() FAILED`（`11 tests completed, 1 failed`）；恢复 `"A"` 后 `GREEN_RC=0`（BUILD SUCCESSFUL）。恢复后快照 config 与工作区**逐字节一致**。日志 `mutation-red.log` / `mutation-green.log` |
| 离屏冒烟 | `ProbeSmoke.java` + JDK21 javac/java：三档日志格式/数值、C 出数、mode_switch、debug=off 静默、切回 A 无损全部符合预期（离屏/受控） |
| 解析脚本 | `parse_truth3_log.py` 用带 MC 前缀的模拟日志验证：按 `mode_switch` 切 4 段、每档汇总、串档告警、判定文本均正确（离屏模拟） |

### 候选包含前两批改动的拆包证明（`verify_candidates.py`，`zipfile` 验 class 与常量池字节）

三个候选包**全部**同时含：

| 批次 | 证据（class 路径 → 常量池字节） |
| --- | --- |
| 本轮 | `com/atom/chat/diagnostics/Truth3Probe.class` → `frame_probe` / `mode_switch` / `REPORT_EVERY`；`com/atom/chat/config/AtomChatConfig.class` → `diagnosticRenderMode` |
| **P0-A** | `com/atom/chat/render/PanelBlurRenderer.class` → `capacityMode` / `nextPow2` / `extendEdge` |
| **P1** | `com/atom/chat/diagnostics/GlGpuTimer.class` → `GlGpuTimer`；`GpuTimerQuery.class` → `GpuTimerQuery`；`FrameProfile.class` → `page_phase` / `PAGE_RENDER` / `firstOpen` |

> 如实说明：`pageKey` 是 P1 **源码注释里的术语**，编译后 class 常量池无此字面量；以 `FrameProfile.class` 的 `page_phase` / `PAGE_RENDER` / `firstOpen`（P1 page-level 归因的实际字段）证明 pageKey 拆分在包内。P1 的 GPU 计时实现原样保留（虽不可信，本轮不动）。

---

## 6. 候选包与哈希

目录：`D:/AtomChat-Main/build/truth3-mode-20261106/candidates/`（文件名与现役一致，便于主代理部署）

| 文件 | sha256 |
| --- | --- |
| `atomchat-Fabric-1.21.1-v0.5.0.jar` | `4d9713d513767a15e757af3121bc77a92d7b9e5deb68672f19bd3773269e42a5` |
| `atomchat-Forge-1.20.1-v0.5.0.jar` | `506b44ca2f949f8c2e5df9766a47afa8ef69d8bc95e5e5c7d08a1ea7600e2ffc` |
| `atomchat-NeoForge-1.21.1-v0.5.0.jar` | `3d6cc2e0e16fd60ed9ddbdf9aaaf96891d56a0389613ee9cd16a6f63f2a8d751` |

清单：`candidates/sha256.txt`。

---

## 7. 主代理可直接用的解析脚本

`build/truth3-mode-20261106/parse_truth3_log.py`

```
python build/truth3-mode-20261106/parse_truth3_log.py <latest.log> [--json out.json]
```

按 `mode_switch` 切段，输出每段的档位/窗口数/p50 中位/p95 中位/fps_p50 中位/max，再按 A/B/C 汇总并给判定文本；`--json` 另写结构化结果。脚本内已声明三种口径不可混。

---

## 8. 用户验收时的极简操作步骤（供主代理统一安排，本子代理未安排验收）

1. 主代理把 `candidates/` 里对应加载器的 jar 部署到实例（**本子代理未部署**）。
2. 确认 `atomchat-client.json` 的 `debug = true`（真机历史配置已是 `true`）；`diagnosticRenderMode` 留默认 `"A"`。
3. 启动游戏 → 进固定场景（固定世界/视角/分辨率/GUI 缩放/VSync/帧率上限/后台负载）→ 打开聊天面板。
4. **每档停留 ≥ 20 秒**（600 帧 @323 FPS ≈ 1.9 s/窗口，20 s ≈ 10 个 `frame_probe` 窗口），按 **F6** 依次 A→B→C→A；每按一次确认日志出现 `mode_switch from=X to=Y`。若 F6 被 mod 占用，改按 **Ctrl+Shift+F6**，或改配置字段重启。
5. 退出，取 `latest.log`，跑 §7 的解析脚本。

### 每档数据怎么读（真机待验）

- 看**每档 `fps_p50` 中位数**（= `1000 / p50`，帧间隔越小越快）；`p95` / `max` 看长尾，不作为 400 FPS 判据。
- **C 档 `fps_p50 < 400`** → 这个 216-mod 环境下 400 FPS 物理不可达，**瓶颈不在 AC/Lib 面板渲染**，后续不必再堆渲染补丁。
- **C ≥ 400 而 A < 400** → 差值即 AC 面板渲染的可回收预算，按 §5 段开销定点修，不再猜。
- A/B 差 = 背景模糊 pre-pass（毛玻璃）占比；B/C 差 = 其余面板绘制占比。
- 判定权归用户，不在实验包内宣布达标。

---

## 9. 遗留风险

1. **未真机验证**：全部帧间隔/FPS 数值待真机验收；本包只保证「能出数、能切档、格式可解析」。离屏冒烟与静态推理不代表 FPS 收益。
2. **F6 冲突**：216 mod 运行时注册无法静态枚举；已给备用键与配置兜底，首次验收需确认 `mode_switch` 出现。
3. **C 档保守天花板**：仍执行 `drawPanel` 之外的幂等准备与 vanilla pipeline（§1），略慢于纯 MC；不影响「瓶颈在不在 AC 渲染」的方向性结论。
4. **B/C 档视觉变化仅诊断用途**：B 档无毛玻璃、C 档无面板；产品默认恒 A、一键回退（`debug=off` 或按 F6 回到 A）。
5. **P1 `gpu_panel` 仍不可信**：本轮不修、不依赖；按 §7 复核 query 区间后再用。
6. **采样窗口口径**：`n=600` 是最近 600 帧的窗口统计，不是 4096 全期滚窗，与 `FrameProfile` 的 `chat_interval` 不可直接相减（口径不同）。
7. **`max` 过滤**：>1 s 的非连续帧被丢弃（防屏幕关闭/重开污染），极端的 >1 s 真实卡顿不会进 `max`；真机已知长帧峰均 < 250 ms，不构成遮蔽。
8. **窗焦点/相机/资源包/光影**未在本包控制，由主代理验收时固定（§8 步骤 3）。

---

## 10. 本轮未做的事（红线，如实披露）

**未部署、未启动游戏、未真机验收、未 commit / push / 发布**；未替换实例 JAR、未改实例配置/启动器、未改 `D:/Myworld/.minecraft/versions/*` 下任何文件（仅**只读**过 `options.txt` 做键位核对）；未安排用户验收。
未改 `versions/atomlib.json` 工作区、未改 `docs/` 已有报告、未改 shader、未改 P0-A 三端 `PanelBlurRenderer.java`、未改 P1 GPU 计时实现、未动 `build/*/source/**` 历史快照、未恢复 `BlurMotionGate`、未做静止降频/半分辨率/借旧模糊档等任何产品侧改变。
未使用 `git checkout` / `reset` / `stash` / `clean` 或任何覆盖式回退；两仓大量未提交 WIP（AC ≈79 / Lib ≈40）原样保留，本轮仅触碰 6 个源码/测试文件（§4）与本轮 `build/truth3-mode-20261106/` 输出目录。
未把任何离屏/受控数值宣称成游戏 FPS 收益；未把 `gpu_panel` 绝对值当结论。

---

## 附：本轮产物

| 文件 | 内容 |
| --- | --- |
| `build/truth3-mode-20261106/candidates/*.jar` + `sha256.txt` | 三端候选包与哈希清单 |
| `build/truth3-mode-20261106/apply_source_patches.py` | 三端同源字节级补丁（保持行尾、anchor 唯一性预检） |
| `build/truth3-mode-20261106/prepare_snapshot.py` | 隔离源码快照（含 NeoForge 资产坑处理、锁文件纪律） |
| `build/truth3-mode-20261106/verify_sync.py` | 三端同步 / 孪生 / 产品默认 / 锁文件校验 |
| `build/truth3-mode-20261106/verify_candidates.py` | zipfile 拆包证明（P0-A / P1 / Truth3）+ sha256 清单 |
| `build/truth3-mode-20261106/collect_candidates.py` | 候选包收集 |
| `build/truth3-mode-20261106/mutate_default.py` | 变异红灯开关（仅快照内、字节级、CRLF 保持） |
| `build/truth3-mode-20261106/ProbeSmoke.java` + `probe-classes/` | 离屏逻辑冒烟 |
| `build/truth3-mode-20261106/parse_truth3_log.py` + `sample-truth3.log` + `sample-parsed.json` | 主代理可直接用的解析脚本与模拟样例 |
| `build/truth3-mode-20261106/build-{fabric,forge,neoforge}.log`、`mutation-{red,green}.log` | 构建与红绿证据 |
| `build/truth3-mode-20261106/snapshot/` | 隔离构建快照（工作区源码的完整副本，锁改 0.3.1-shadowcache） |
| `docs/verification/2026-11-06-truth3-mode-experiment.md` | 本报告 |
