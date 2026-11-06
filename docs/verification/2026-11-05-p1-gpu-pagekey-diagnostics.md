# P1：GPU 计时与按页面归因接生产（诊断接线，为唯一一次真机验收服务）

**日期**：2026-11-05
**性质**：唯一执行子代理交付，接在已完成的 P0-A 之后。范围严格限定为 P1（把 `GpuTimerQuery` 接生产 + 给 `FrameProfile` 补 pageKey/phase/首开）。P0-A 的三端 `PanelBlurRenderer.java` 资源复用语义**一字未动**。
**依据**：主报告 `2026-11-05-astra-full-performance-audit.md` §6.4 / §7 F7,F12 / §8 P1-A；`docs/HANDOFF-panel-performance-codex.md` §6.2 / §6.4。
**所有耗时数值均为离屏 / 受控场景或既有日志量级，不等于游戏整帧 / FPS 收益。** 本轮只做诊断接线，不改视觉、动画、玻璃、模糊半径、采样位置、capture 坐标语义，不做静止降频 / 开合冻结 / 半分辨率 / 借旧模糊档。

---

## 0. 结论（一句话）

把已存在但未接生产的 `GpuTimerQuery` 以**跨帧非阻塞**方式接到 `FrameSegments` 帧边界（量「面板帧 GL 间隔」，含背景模糊 pre-pass，`config.debug=on` 时稳定可解析），并给 `FrameProfile` 补 **pageKey / 过渡 phase / 首开标志**，把混在 `OTHER` 的设置/关于/个人/导航拆成独立桶，**保持现有 `panel`/`layout`/`other_*` 桶逐字节不变**。三端隔离快照构建全绿、反证与变异红绿成立、`verify_targets.py` 通过、`NotificationRenderWiringTest` 保持绿。
**每帧额外开销上界 ≈ 0.026 ms/帧（离屏/受控、更重查询组口径）**，实际更轻。

**未部署、未真机验收、未 commit。**

---

## 1. 已证实、不重做的事实（行号已核对）

| 事实 | 位置 |
| --- | --- |
| `GpuTimerQuery` 类已存在、只有 `PanelDiagnosticsTest` 引用、**未接生产** | `shared/src/main/java/com/atom/chat/diagnostics/GpuTimerQuery.java:22`、注释 `:18-20` |
| `FrameProfile` 按 surface 分桶，设置/关于/个人/导航全落 `OTHER`；4096 滚窗不带 pageKey | `shared/.../diagnostics/FrameProfile.java:66`、`:46`、`:241-257`（改前） |
| 日志 `BLUR 0.14–0.24 ms` 只是 CPU 提交，缺游戏内 GPU 完成时间 | 主报告 §6.4 / F12 |
| 面板帧边界：`FrameSegments.begin` 在 `drawPanel` 开头、`finish` 在末尾；背景模糊在 `preUi` 内 | `platforms/1.21.1-fabric/.../render/SkiaGraphics.java:37-41`、`:52-54`、`:87`、`:90-91` |
| 背景模糊 `PanelBlurRenderer.render` 在 `AtomChatScreen` 的 `preUi` Runnable 内 | `platforms/1.21.1-fabric/.../AtomChatScreen.java:1980-1992`、`:2012` |
| `FrameSegments.navigation(action, page)` 已有导航钩子（push/pop/root） | `shared/.../diagnostics/FrameSegments.java:31-40`（改前）；调用点 `shared/.../nav/NavStackController.java:274,305,323,369` |
| `AppPage` / `SettingsSection.ABOUT("about")` | `shared/.../nav/AppPage.java:3-18`；`shared/.../settings/SettingsSection.java:12` |
| `FrameProfileTest.java:134` 断言 `summary()` 恰好 5 行 | `shared/src/test/java/com/atom/chat/diagnostics/FrameProfileTest.java:126-142` |
| 三端 `SkiaGraphics.java` 只有 2 份孪生（official + fabric）；`AtomChatScreen.java` 三端各 1 份 | Glob 实查 |

---

## 2. 改动文件（全部 shared + 1 个验证工具；未碰平台层 / 孪生 / shader / P0-A 渲染器）

| 文件 | 改动点（改后行号） |
| --- | --- |
| `shared/src/main/java/com/atom/chat/diagnostics/GlGpuTimer.java` | **新增**：生产 `GpuTimerQuery.Gl` 实现（LWJGL GL33C/GL15C）；`live()` 能力检查、全方法 `try/catch` 降级、`otherQueryActive()` 查 `GL_CURRENT_QUERY`、`resultNanos()` 只在 AVAILABLE 后读 |
| `shared/src/main/java/com/atom/chat/diagnostics/FrameSegments.java` | GPU 字段 `:13-19`；`begin(boolean)` 先 `pollGpu()` 再 `beginFrame(id)` `:27-34`；`finish` `endFrame()` `:42-45`；`summary()` 追加 `gpuSummary()+FrameProfile.pageSummary()` `:46-48`；`pollGpu` `:50-56`；`gpuSummary` `:59-75`；`resetDiagnostics` `:78-82`；`navigation` 调 `FrameProfile.page(page)` `:85-86` |
| `shared/src/main/java/com/atom/chat/diagnostics/FrameProfile.java` | `Page`/`Phase` 枚举与字段 `:73-87`；`page(String)` + `classify` `:96-120`；`frame()` 内按页面桶累加（不动 SURFACE_CPU）`:210-215`；`pageSummary()` `:284-300`；`reset()` 清页状态并 `FrameSegments.resetDiagnostics()` `:303-314` |
| `shared/src/main/java/com/atom/chat/nav/NavStackController.java` | push 导航带 settings section param `:274-275`（拆「关于」）；pop `:305,:323` / root `:369` 保持原样 |
| `shared/src/test/java/com/atom/chat/diagnostics/PageGpuDiagnosticsTest.java` | **新增**：5 个反证/归因/回归测试 |
| `tools/probes/verify_profile_wiring.py` | 隔离 stub 编译补 2 个 no-op 替身（`GpuTimerQuery`/`GlGpuTimer`），**harness 的 wiring 断言语义未动**（详见 §8） |

**未改**：任何平台层文件、`layers/mapping/official/` 孪生、`.fsh`/`.vsh`、工作区 `versions/atomlib.json`、`build/*/source/**` 历史快照、`docs/` 已有报告、三端 `PanelBlurRenderer.java`。`boolean full = true;` 三端原样、`BlurMotionGate` 0 处。

---

## 3. GPU 计时接生产（最小路径）

### 3.1 接线点选择：为何是 `FrameSegments.begin/finish` 而非 `PanelBlurRenderer.refreshBlur`

- `refreshBlur` 在平台层三端各一份且是 **P0-A 刚改过的文件**（红线：不改容量/blit/UV/延展逻辑），改它要动 3 份并可能牵动孪生，风险最大。
- `FrameSegments` 是 shared 诊断类，`begin`/`finish` 每帧各恰好一次，是天然的非阻塞 query 挂载点；`GpuTimerQuery` 类注释本就写明它量的是「a panel GPU interval」。
- 背景模糊发生在 `SkiaGraphics.drawPanel` 的 `preUi`（`AtomChatScreen.java:1982-1992` → `SkiaGraphics.java:52-54`），位于 `FrameSegments.begin`（`SkiaGraphics.java:40`）与 `finish`（`:87`）之间 → `GL_TIME_ELAPSED` query **覆盖**它。

**因此量到的是「面板帧 GL 间隔（含背景模糊 pre-pass）」，是背景模糊 GPU 完成时间的上界，不是纯模糊隔离值，也不是游戏整帧。** 要隔离纯模糊需把 query 挪进 `refreshBlur`（碰 P0-A 三端 + 孪生），本轮按「最小路径 + 不碰 P0-A」原则未做，列为遗留（§9）。全 shared 接线，平台层与孪生零改动。

### 3.2 硬要求逐条对照

| 要求 | 实现 |
| --- | --- |
| 跨帧非阻塞 | 帧 N `begin` → `GPU.beginFrame(id)`（`glBeginQuery`）；帧 N `finish` → `GPU.endFrame()`（`glEndQuery`）；**帧 N+1 起** `begin` 内 `pollGpu()` 只读 `GL_QUERY_RESULT_AVAILABLE=true` 的结果。`resultNanos()` 仅在 `resultAvailable()` 为真后调用（GL 规范保证不阻塞）。**无 `glFinish`、无阻塞式 `glGetQueryObjectui64` 进正常帧循环** |
| 避让其它 mod | 每帧独立 `glGenQueries` 对象；`GlGpuTimer.otherQueryActive()` 用 `glGetQueryi(GL_TIME_ELAPSED, GL_CURRENT_QUERY)!=0` 检查同类 query 目标冲突；冲突/不支持/上下文丢失 → `beginFrame` 返回 false 并 `skipped++`；结果超时（`RESULT_TIMEOUT_NANOS=2s`，`GpuTimerQuery.java:25`）静默回收 `expired++`；GL 调用异常被 `GpuTimerQuery` 自身 try/catch 转为 skip/drop，绝不抛进帧循环 |
| 失败路径有测试 | `GlGpuTimer` 每个入口 `try/catch(Throwable)` 安全默认；见 §7 |
| 有界容量 / 有界轮询 | ring capacity=4（`FrameSegments.java:16`）、GPU 结果滚窗 4096（`:17-18`）、`poll()` 单次最多处理 `count` 个（≤capacity）；`TRANSITION_FRAMES=90`（`FrameProfile.java:80`）有界 |
| 不产生长帧 | 全部为非阻塞状态查询/对象操作；无 `glFinish`；无每帧日志（报告每 600 帧一次） |

### 3.3 为何安全（对 P0-A）

P1 **没有**改 `PanelBlurRenderer.java` 任何一行，也没有改 `SkiaGraphics.java` 的 blit/UV/容量/延展逻辑；只在 shared 诊断类的帧边界插入 query begin/end/poll。背景模糊的 capture 坐标、采样、半径、alpha 全部原样。

---

## 4. pageKey / 过渡 phase / 首开标志（保持现有桶语义）

- 新增 `enum Page { SETTINGS, ABOUT, PROFILE, NAVIGATION, CHAT, OTHER }`、`enum Phase { TRANSITION, STEADY }`（`FrameProfile.java:77-78`）。
- `FrameSegments.navigation` 在 push/pop/root 时调 `FrameProfile.page(raw)`；`NavStackController.java:274-275` 让 push 字符串带 `NavPage.param()`（`"SETTINGS_SECTION:about"`），`classify` 据此把「关于」拆成 `ABOUT`，其余归 SETTINGS/PROFILE/NAVIGATION/CHAT。
- **首开**：`seenPages` 集合按原始导航 key 去重，首次进入 `firstOpen=true`，重复打开 `false` → 输出 `_first` / `_repeat` 两组桶，首开与重复打开可区分。
- **过渡 phase**：导航后 90 帧（有界）为 `transition`，之后 `steady`（`FrameProfile.java:211-212`）。
- **保持现有桶语义不变**：`PANEL_BUCKET` / `DECODE_BUCKET` / `LAYOUT_BUCKET` / `SURFACE_CPU` / `SURFACE_INTERVAL` 的累加逻辑、`surface = Surface.OTHER`（`FrameProfile.java:225`）与 `UiFrameEvent` 字段**全部原样**；按页面数据只写入**新增的** `PAGE_RENDER`（TreeMap，固定 ≤6×2 个桶，有界）。`summary()` 仍是 5 行（`FrameProfileTest.java:134` 绿），新增输出走**独立** `pageSummary()`，由 `FrameSegments.summary()` 追加到日志。

---

## 5. 诊断输出（config.debug=on 稳定可解析）

`SkiaGraphics.java:90-91` 每 600 帧打印 `FrameProfile.summary() + FrameSegments.summary()`；后者现为 `GLOBAL.summary() + gpuSummary() + FrameProfile.pageSummary()`。

### 5.1 字段说明

| 行 / 字段 | 含义 |
| --- | --- |
| `gpu_panel n` | 已完成 GPU 样本数（4096 滚窗，有界） |
| `gpu_panel p50/p95 ms` | 面板帧 GL 间隔的 **GPU 完成时间**（含背景模糊 pre-pass）；**不是**整游戏帧、**不是** CPU 提交、**不是** FPS |
| `completed / dropped / expired / skipped / stalled / pending` | `GpuTimerQuery` ring 计数：完成 / ring 满丢弃 / 超时或坏 query 回收 / 不支持·冲突·上下文丢失跳过 / 嵌套 begin 保护 / 在途。`skipped`/`expired`/`dropped` 非零 = 静默降级，**不是**长帧 |
| `page=<page>_<first|repeat> n/p50/p95/max/mean/alloc` | 按页面 CPU 渲染耗时桶；`OTHER` surface 拆成 settings/about/profile/navigation/chat/other；`_first` 首开、`_repeat` 重复打开。现有 `*_render`/`*_interval` 桶不变 |
| `page_phase` | `transition`（导航后 90 帧窗口）/ `steady` |
| `page_firstopen` | 1=当前页首开，0=重复打开 |
| `page_current` | 当前页 key |

### 5.2 解析脚本

`build/p1-gpu-pagekey-20261105/parse_diagnostics.py`：
```
python parse_diagnostics.py <latest.log> [--json out.json]
```
逐行正则解析 `gpu_panel` / `page=` / `page_phase` 三类行，输出汇总与可选 JSON。已用合成样例验证：
`sample-diagnostics.log` → 解析出 **gpu_panel 1 行 / page 5 行 / context 1 行**（`sample-parsed.json`）。真实格式中 `p95` 与 `completed` 间有 ` ms`、page 行空白为 `\s+`，正则均已兼容。

---

## 6. 每帧额外开销上界（离屏 / 受控量化，不许只说「很轻」）

正常帧循环（仅 `debug=on`，`SkiaGraphics.java:37,40` 的 `profileStart!=0` 门控）每帧新增的 GL 操作：

| 调用 | 次数 | 性质 |
| --- | --- | --- |
| `GL.getCapabilities()`（timerQuerySupported / contextStillValid） | 2 | 纯 CPU，0 GL 调用 |
| `glGetQueryi(GL_CURRENT_QUERY)`（冲突检查） | 1 | 非阻塞状态查询 |
| `glGenQueries` / `glBeginQuery` / `glEndQuery` | 3 | 对象/作用域操作 |
| `glGetQueryObjecti(QUERY_RESULT_AVAILABLE)`（poll，pending≤4） | ≤4 | 非阻塞可用性查询 |
| `glGetQueryObjectui64(QUERY_RESULT)` + `glDeleteQueries`（仅 AVAILABLE 后） | ≤4 | 非阻塞结果读 + 释放 |

合计约 **6–7 个轻量、非阻塞状态/对象操作/帧**，无 `glFinish`、无阻塞读。

**量级依据（离屏 / 受控）**：`build/astra-performance-20261105/probe-results.txt:2-7` —— 含查询的 `blur5_queries_480x860 wall_p50=0.1294` vs `blur5_480x860 0.1126`（差 **0.0168 ms**）、`960x1720 0.2936` vs `0.2677`（差 **0.0259 ms**）。那组每个 pass 多做 3 个 `glGet*`（5 pass ≈15 个），比本接线更重；主报告 §9 亦记「探针含查询的 blur5 仅多 0.017–0.026 ms wall」。

→ **每帧额外开销上界 ≈ 0.026 ms/帧（离屏/受控、更重查询组口径）**，本接线实际更轻。CPU 侧：`TreeMap.computeIfAbsent` 1 次、`Bucket.add` 1 次数组写、`poll()` ≤4 个结果、`seenPages`/phase 均 O(1)，无每帧日志。
**这是诊断接线开销，不等于游戏内每帧开销，更不等于 FPS 收益**；真机值需在实例 `latest.log` 确认。

---

## 7. 反证测试与变异红绿

### 7.1 反证（query 不可用 / 其它 mod 占用 → 降级、不崩、不长帧）

| 测试 | 结果 |
| --- | --- |
| `PanelDiagnosticsTest`（既有，三端 `tests=12 failures=0`）：FakeGl `conflict=true` / `supported=false` / `alive=false` → skip；`failBegin`/`failEnd`/`failResult` → 恢复不 stall；stuck head 超时回收不饿死；`close` 释放所有 query | **绿**（覆盖「其它 mod 占用 query」「结果不可用」） |
| `PageGpuDiagnosticsTest.productionGlDegradesWithoutContextAndNeverStalls`：无 GL context 时 `beginFrame` 返回 false、`poll` 空、`pending=0`、不抛 | **绿** |
| `PageGpuDiagnosticsTest.frameWiringSurvivesUnavailableQueries`：连续 8 帧 `begin/finish` 在无 context 下不崩，`summary` 含 `gpu_panel` 与降级计数 | **绿** |
| `PageGpuDiagnosticsTest.existingSummaryStaysFiveLines`：新增页输出不泄漏进 5 行 `summary()` | **绿** |

### 7.2 变异红灯（把 pageKey 装回缺失应让分页面断言变红）

在快照的 `FrameSegments.java` 上用 `mutate_page.py` 字节级注释掉 `FrameProfile.page(page);`（保留 CRLF）：

| 步骤 | 结果 |
| --- | --- |
| 变异 off（pageKey 缺失）→ `PageGpuDiagnosticsTest` | **红**：`navigationSplitsOtherIntoPagesAndDistinguishesFirstVsRepeat()` FAILED，`5 tests completed, 1 failed`，RC=1，`page_current=other`（无 `page=about_first`）。日志 `mutation-red.log` |
| 恢复 on → 同一测试类 | **绿**：`BUILD SUCCESSFUL`，RC=0。日志 `mutation-green.log` |
| 恢复后快照 `FrameSegments.java` vs 工作区 | `cmp` **IDENTICAL（逐字节一致）** |

### 7.3 A/B 数值汇总（红绿对照）

| 场景 | 分页面断言 | RC | 结论 |
| --- | --- | --- | --- |
| pageKey 接线在（生产） | about/settings/profile/navigation 四类 + `_first`/`_repeat` 均在 | 0 | 绿 |
| pageKey 装回缺失（变异） | 全部落 `other_*`，无 `page=about_first` | 1 | **红** |
| GPU 无 context / 冲突 / 结果不可用 | skip/expired 计数、无悬空 query、无异常 | 0 | 绿 |

---

## 8. 构建与验证门（缺一不可，全部实跑）

| 项 | 结果 |
| --- | --- |
| `python tools/verify_targets.py` | **校验通过**（改工具 stub 后重跑仍通过；6 条目标，含映射孪生/资源路径/seams 同步、shared 第三方 import、Java 17 级语法） |
| 三端隔离源码快照 | `build/p1-gpu-pagekey-20261105/prepare_snapshot.py`：`copytree` 排除 `.git`/`build`/`.gradle`/`.work`/`.workbuddy`/`*.log`/`__pycache__`；**只在快照内**把 `versions/atomlib.json` 改为 0.3.1-shadowcache，**工作区锁一字未动**（`git status --short versions/atomlib.json` 为空，仍 0.1.0-beta）；NeoForge `minecraft_assets.properties` 补 SHA `015d0bc702eff9f9e7bc7d1a514c8c554118407635c5d1c56470d62a38f04560`（HANDOFF §6.2 已知坑） |
| — `1.21.1-fabric`（JDK 21） | `cleanTest build` **BUILD SUCCESSFUL 1m19s**，RC=0 |
| — `1.20.1-forge`（JDK 17） | `cleanTest build` **BUILD SUCCESSFUL 1m30s**，RC=0 |
| — `1.21.1-neoforge`（JDK 21，`-x downloadAssets`） | `cleanTest build` **BUILD SUCCESSFUL 1m25s**，RC=0 |
| 关键测试 XML（三端） | `PageGpuDiagnosticsTest` `tests=5 failures=0 errors=0`；`NotificationRenderWiringTest.java:46-52` `tests=7 failures=0 errors=0`；`PanelDiagnosticsTest` `tests=12 failures=0`；Fabric 恢复后全量 test `BUILD SUCCESSFUL 1m17s`，257 个测试类 XML、0 失败 |
| 三端同步 | 本轮**未改任何平台相关文件**，故 Forge/Fabric/NeoForge 三份无语义差异需同步，`layers/mapping/official/` 与 fabric 孪生无需成对改动；`verify_targets.py` twins/seams 检查通过 |
| 红线保护 | `boolean full = true;` 三端各 1、`BlurMotionGate` 0（grep + 测试双确认）；未恢复 `BlurMotionGate`、未做静止降频/开合冻结/半分辨率/借旧模糊档 |
| WIP 保护 | `git status --short` AC **81** 项（只读统计）；未用 `git checkout`/`reset`/`stash`/`clean` 或任何覆盖式回退 |

> 首次三端 `build` 曾因 2 个测试变红：① 我新增测试的桶断言笔误（`reset()` 后无 navigation 落 `other_repeat` 而非 `other_first`）——已修；② `tools/probes/verify_profile_wiring.py` 的隔离 stub 编译缺新增的 `GpuTimerQuery`/`GlGpuTimer` 两个类——已补 2 个 **no-op stub**（只为让隔离编译跟上新增依赖，harness 的 wiring 断言语义未动，真实 GPU 逻辑由单测覆盖）。修复后手动跑该探针 `official passed / fabric passed` RC=0，重跑三端全绿。

---

## 9. 遗留风险（如实披露）

1. **GPU 计时口径**：量到的是「面板帧 GL 间隔（含背景模糊 pre-pass）」，是背景模糊 GPU 完成时间的**上界**，不是纯模糊隔离值、不是游戏整帧。要隔离纯模糊需把 query 挪进 `refreshBlur`（碰 P0-A 三端渲染器 + 孪生），本轮按最小路径未做。
2. **过渡 phase 是诊断近似**：`transition` = 导航后 90 帧有界窗口，非逐帧开合动画 stage（`panelProgress`）。开合动画真实 phase 需平台层 `AtomChatScreen` 传值（改三端），未做。
3. **关于页归因粒度**：依赖 `NavStackController` push 带 `NavPage.param()`；pop 回退时 pageKey 归到 `SETTINGS`（非具体 section）。「关于」在停留期间正确。
4. **诊断开销数值**：§6 的 0.026 ms/帧 为离屏/受控更重查询组口径的**上界**，不是游戏内实测，不等于 FPS 收益；真机 `latest.log` 未读。
5. **其它 mod 真实交互**：`GlGpuTimer.otherQueryActive()` 用 `GL_CURRENT_QUERY` 避让，但 Iris/Gnetum 等在实例里对 query 目标的实际占用未确认；无 context 的降级路径已在 JVM 单测覆盖。
6. **wiring 工具 stub**：`verify_profile_wiring.py` 补的是 no-op 替身，隔离编译不测真实 GPU 时序；真实 ring 逻辑由 `PanelDiagnosticsTest`(12) 与 `PageGpuDiagnosticsTest`(5) 覆盖。
7. **未真机验收**：本接线就是为了唯一一次统一验收能真读出数据；判定权归主代理/用户，本子代理不安排验收、不宣布达标。

---

## 10. 边界声明

- **未部署、未真机验收、未 commit / push / 发布。**
- 未启动游戏、未替换实例 JAR、未改配置/启动器；未改工作区 `versions/atomlib.json`（仍锁 0.1.0-beta）；未改 `docs/` 已有报告；未碰 `build/*/source/**` 历史快照；未改任何 shader 文件。
- 未改 P0-A 三端 `PanelBlurRenderer.java` 的资源复用语义；`boolean full = true;` 原样、`BlurMotionGate` 0 处。
- 未使用 `git checkout` / `reset` / `stash` / `clean` 或任何覆盖式回退；两仓大量未提交 WIP 原样保留（AC `git status --short` 81 项，只读）。
- P0-B 与 P1 分别独立报告、独立可失败：P0-B 判「不建议改」且未改生产；P1 的接线改动集中在 shared 诊断层，二者收益不相加、数值均为离屏/受控。

---

## 11. 产物清单

```
docs/verification/2026-11-05-p1-gpu-pagekey-diagnostics.md   （本报告）
shared/src/main/java/com/atom/chat/diagnostics/GlGpuTimer.java
shared/src/main/java/com/atom/chat/diagnostics/FrameSegments.java   （改）
shared/src/main/java/com/atom/chat/diagnostics/FrameProfile.java     （改）
shared/src/main/java/com/atom/chat/nav/NavStackController.java       （改）
shared/src/test/java/com/atom/chat/diagnostics/PageGpuDiagnosticsTest.java
tools/probes/verify_profile_wiring.py                                （改 stub）
build/p1-gpu-pagekey-20261105/parse_diagnostics.py
build/p1-gpu-pagekey-20261105/sample-diagnostics.log
build/p1-gpu-pagekey-20261105/sample-parsed.json
build/p1-gpu-pagekey-20261105/prepare_snapshot.py
build/p1-gpu-pagekey-20261105/snapshot/                             （隔离构建快照）
build/p1-gpu-pagekey-20261105/mutate_page.py
build/p1-gpu-pagekey-20261105/build-{fabric,forge,neoforge}.log
build/p1-gpu-pagekey-20261105/mutation-{red,green}.log
build/p1-gpu-pagekey-20261105/final-fabric-test.log
```
