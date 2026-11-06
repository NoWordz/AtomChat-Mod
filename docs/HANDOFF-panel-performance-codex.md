# 面板掉帧：交接给 Codex

**日期**：2026-11-05
**交接原因**：上一轮按「细粒度 GL reset + 点阵缓存」做的纯优化**未取得收益**；`NoWordz/AtomLib#1` 给出的静态审计指出了正确的刀口。本文件自包含，接手方不需要读此前的对话。
**当前状态**：三端实例已还原到本轮之前的包；临时构建产物已清理；未 commit、未启动游戏、未宣布任何修复。

---

## 0. 三十秒版本

开面板时帧率从 400 掉到 170–180，每帧固定多付约 **4.0ms**。这 4.0ms 里最大的一块是 `GlStateScope` 的**全量 GL 状态查询/恢复**：在 NVIDIA 上 `GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS = 192`，每个纹理单元查 10 种目标 + sampler ≈ **2300 次 `glGet*`**，关闭时再 ≈2300 次 bind；**单次 scope ≈ 2.3–2.4ms**，而一帧里可能嵌套调用 **4–7 次**。

上一轮把力气花在了「每帧 `resetGLAll()` 改成细粒度 reset」和「点阵预渲染」上 —— 前者被真实 GL 测试证明**不安全且补齐后无收益**，后者**不是主因**（两次独立数据证伪）。真正该做的是：**裁掉不必要查询的状态子集、消除嵌套 scope**，其次是 **Yoga 每帧全量重排** 与 **TextService 每帧重新塑形**。

---

## 1. 现场状态（接手前必读）

### 1.1 三端实例：已还原为本轮之前的包

| 实例 | AtomChat | AtomLib |
| --- | --- | --- |
| `D:/Myworld/.minecraft/versions/1.20.1-main/mods` | `b1e7679e91e5…` | `8dac402552b0…` |
| `D:/Myworld/.minecraft/versions/1.21.1-CCB/mods` | `d04e913270c1…` | `0d949b7949d4…` |
| `D:/Myworld/.minecraft/versions/Mechanomania-航空学/mods` | `2c903b24a7d3…` | `a42e5e2e920e…` |

- 还原自 `build/panel-opt-20261104/deployment-receipt.json` 的 `rounds[0]`（本轮部署前的原始包），逐文件 SHA-256 校验通过，回执已追加 `round 3 @ 20261105-095043`、`errors=[]`。
- 所有 `.bak-*` 备份保留（`versions/*/mods/*.bak-20261104-221327` 与 `.bak-20261104-230615`，共 12 个 jar 备份）。
- 注意：Fabric/NeoForge 的 AtomChat 是**带 JFR 诊断打点的包**（上一轮 handoff 交付），不是纯发布版；`config/atomchat/atomchat-client.json` 的 `debug` 可能仍为 `true`。

### 1.2 已清理（可再生产物，释放约 1.15 GB）

删除：`build/panel-opt-20261104/source`、`build/panel-perf-20261104/source`、`excluded-work-copy`、`events.json`、`exec-run3.json`、`artifacts`、`D:/AtomLib/build/panel-opt-20261104/maven`、`ab-classes`。均为 `.gitignore` 覆盖的构建产物，可由脚本与 JFR 重建。

**保留**：三份 JFR、分析 JSON、全部报告、候选 JAR、日志、diff、回执。

### 1.3 两个仓的工作区状态（未 commit）

- **AtomChat** HEAD `e92fdaf9`：用户未提交改动 **31 改 + 8 未跟踪**（字体复用、亮度钳制回退、JFR 诊断），**必须原样保留，不得清理/覆盖/回退**。本轮另加了：
  - `shared/src/main/java/com/atom/chat/render/DotTexture.java`（159+/44−，点阵 Picture 缓存，开关 `atomchat.dots.cached` 默认 false）
  - `shared/src/test/java/com/atom/chat/render/DotTextureCacheTest.java`、`DotTextureTintContrastTest.java`（新增）
- **AtomLib** HEAD `56f1b95`：本轮改了
  - `platforms/shared/src/main/java/com/atom/lib/client/render/SkijaRenderBridge.java`（11 位 reset 分支 + 开关 `atomlib.bridge.fastState` 默认 false）
  - `platforms/shared/src/main/java/com/atom/lib/client/render/GlStateScope.java`（5 个 `MAX_*` 缓存）
  - 新增 `platforms/shared/src/test/java/com/atom/lib/client/render/FastStateOptimizationTest.java`
  - 另有 **11 项 `hiddenGlTest` 自动生成的 evidence 残留**（`docs/evidence/appearance-replication-and-backdrop/gl/atomlib-1.21.1-fabric/` 下 2 个 M + `backdrop-stage-*` 4 项 + `item-icons/` 5 项），非用户改动，可删。
- **建议**：接手时先决定是否回退本轮实验代码。回退命令（**先与用户确认**）：
  ```
  git -C D:/AtomLib checkout -- platforms/shared/src/main/java/com/atom/lib/client/render/SkijaRenderBridge.java platforms/shared/src/main/java/com/atom/lib/client/render/GlStateScope.java
  rm D:/AtomLib/platforms/shared/src/test/java/com/atom/lib/client/render/FastStateOptimizationTest.java
  git -C D:/AtomChat-Main checkout -- shared/src/main/java/com/atom/chat/render/DotTexture.java
  rm D:/AtomChat-Main/shared/src/test/java/com/atom/chat/render/DotTextureCacheTest.java D:/AtomChat-Main/shared/src/test/java/com/atom/chat/render/DotTextureTintContrastTest.java
  ```
  不要动 AC 那 31 改 + 8 未跟踪。

---

## 2. 已确认的实测事实（可直接引用，不必重测）

三轮 JFR 采样，逐帧配对 **100%**（9699 对、3918 对，零缺失/零重复/零串号）。

### 2.1 稳定态同帧分位数（ms，两轮复现，差异 1–2%）

| 段 | run2 p50 / p95 / p99 | run3 p50 / p95 / p99 |
| --- | --- | --- |
| `panelNanos`（整段） | 3.989 / 7.758 / 11.079 | 4.032 / 6.310 / 9.934 |
| `BRIDGE_ENTER`（合并桶） | **2.123** / 2.447 / 2.703 | **2.149** / 2.493 / 2.756 |
| `DRAW` | 1.153 / 2.947 / 6.056 | 1.161 / 2.224 / 3.606 |
| ├ `DOTS` | 0.922 / 1.093 / 1.229 | 0.876 / 1.079 / 1.205 |
| └ `LIST` | 0.290 / 0.345 / 0.426 | — |
| `BRIDGE_EXIT` | 0.373 / 2.666 / 3.875 | 0.378 / 2.109 / 3.862 |
| `PRE_UI`（含 BLUR 0.196） | 0.200 / 0.381 / 0.507 | — |

段是**嵌套墙钟，不可相加**。`panelNanos` 4.0ms 与外部观测 400→170–180fps 的增量 3.1–3.4ms 同量级（跨时钟，仅一致性检查）。

### 2.2 长帧归因（已证伪项）

- 95 个长帧（>p99）：`DRAW` 主导 75、`BRIDGE_EXIT` 主导 18、`BRIDGE_ENTER` 主导仅 2 → **`BRIDGE_ENTER` 是常驻固定开销，不是长尾来源**。
- 长帧里 `DOTS` 稳定在 0.975ms（与稳定态 0.922 几乎一致）→ **点阵不是长帧原因**（已两次独立证伪）。
- 95 个长帧 ±50ms 内靠近 GC 的 3 帧、靠近 `JavaMonitorEnter` 的 0 帧 → **GC/锁等待不是原因**。
- 切页：`WORLD_CHAT ↔ CHAT_LIST` 连续 9 次 push 首帧仅 3.1–4.0ms；`SETTINGS_SECTION` 28.1→20.8→8.7ms、`PROFILE` 首次 57.7→后续 3.95ms → **只有特定页面首次渲染冷初始化**，不是「每次切页重建」。

### 2.3 单次 `GlStateScope` ≈ 2.3ms（仓库自带 A/B，关键）

`D:/AtomLib/docs/evidence/render-performance-20261104-followup.md`：空图标批次仅跳过**一次外层 `GlStateScope`**，Fabric 上 `prepare` p50/p95 从 **4.7115 / 5.7308 ms → 2.2662 / 2.6683 ms**（3 轮 × 64 样本、16 预热）。差额 **≈ 2.45ms**，与我们实测的 `BRIDGE_ENTER` 2.12–2.15ms 精确吻合。

**这就是主证据：一次 scope ≈ 2.3–2.4ms，面板每帧至少一次，背景模糊/图标路径还会再叠。**

---

## 3. `NoWordz/AtomLib#1` 的审计结论（按影响排序）

> 行号与当前 HEAD 有偏差（issue 写 `GlStateScope.java:287-402`，但 HEAD 版本只有 183 行，工作区版本 233 行），**按内容定位，不要按行号**。

### 🔴 严重

**3.1 `GlStateScope` 全量查询/恢复（头号问题）**
- `GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS`（NVIDIA = **192**）× 10 种纹理目标 + sampler ≈ **2300 次 `glGet*`**，恢复再 ≈2300 次 bind；UBO 约 84 × 3 次查询；8 个 draw buffer 的 indexed blend/mask。
- `glGet*` 在开启线程优化的驱动上强制同步，是最贵的一类调用。
- **一帧内约 4–7 次**：`drawFrame` 1（`SkijaRenderBridge:97`）、背景模糊 `capture`+`draw`（`BackdropRenderer:22/41`）、物品图标 `prepare`/`release`（`ItemIconAtlas:36/53`）、`withContext`（`SkijaRenderBridge:120/246`）。
- 建议：① 纹理单元限制到 `GL_MAX_TEXTURE_IMAGE_UNITS`（或 Skia 实际使用数）；② 目标只保留 `TEXTURE_2D` / `TEXTURE_RECTANGLE`，跳过 1D/3D/Array/Cube/Buffer/MS；③ 跳过 UBO；④ draw buffer 按当前 FBO 实际绑定数；⑤ **消除嵌套**：背景、图标、绘制合并到同一个 scope。

**3.2 Yoga 每帧全量重新布局**
- `ui/src/main/java/com/atom/lib/layout/internal/YogaLayoutEngine.java`（约 133 行）`setMeasure` 用 `measure == node.measure()` 做引用比较；`ui/.../design/atomchat/SettingsRows.java` 的 `label()` **每帧新建 lambda** → 所有文字叶子每帧 `YGNodeMarkDirty`；上一帧树比较 `previous.root().equals(tree.root())` 因 lambda 不同永不命中。
- 建议：只比较 `measureRevision`（已含 request 哈希与字体代次）。

**3.3 文字每次绘制都重新塑形**
- `runtime/src/main/java/com/atom/lib/text/TextService.java`：**`:70` 与 `:95` 处每次 `draw`/`layout` 都 `Shaper.makeShapeDontWrapOrReorder()`**（原生对象），每个 run 再 `openFont` + `shapeLine`；`shape()` 还在二分查找里被反复调用（`:105`）。布局有缓存，**塑形结果没有** → 每帧整页重新 shaping。
- 另：`layout()` 即使命中缓存也要先遍历最多 256 条缓存做代次清理，而 Yoga 每帧对每个叶子都调用。
- 建议：`TextLine`/`TextBlob` 缓存在 `TextLayout` 中、淘汰时关闭；服务内复用一个 `Shaper`；只在 font generation 变化时清理。

### 🟠 中等

**3.4 物品图标**：图集每次 prepare 全部重画；每物品单独 `graphics.flush()`；每次 prepare 反射遍历 `RenderSystem`/`GlStateManager` 字段做快照；每个 `ItemStack` 被 copy 3 次。建议按 key+stack 哈希缓存静态格子、整批一次 flush、反射字段列表静态缓存。

**3.5 背景模糊**：尺寸变化就重分配 3 纹理 + 3 FBO（动画期每帧分配）；4 遍 Kawase 全分辨率（未降采样）；`validate()` 每次 `glCheckFramebufferStatus` + attachment 查询；`glGetUniformLocation` 每帧。建议按 2 的幂扩容复用、先降到 1/2 或 1/4、缓存 uniform location、仅目标变化时校验。

**3.6 `UiSession` O(n²)**：`byId` 每次 stream 线性查找；`paintOrder().contains` 在 List 上；`uiScale ≠ 1` 时 `forPainting` 每控件复制整页 box Map。建议 `byId` 用 Map、`paintOrder` 配 Set、`forPainting` 按 scale 缓存。

### 🟡 次要
弹窗 opacity=1 仍每帧开全屏 `saveLayerAlpha`（`UiSession:209/224`）；入场动画逐行开 alpha 图层（`SettingsGroup:25`）；`UiCards.drawNotificationBackdrop` 回读式 `saveLayer`；展开三角每帧 `Path.makeFromSVGString`（`AtomChatControlSkin:60`）；`drawBlurredBackground` 每次新建 `ImageFilter`。

**建议优先级**：3.1 → 3.2、3.3 → 3.4、3.5 → 其余。
**验证方式**：spark / NSight 看渲染线程 `glGetInteger*` 占比；或在 NVIDIA 控制面板切换「线程优化」对比 —— 若掉帧明显变化，基本可确认 3.1。

---

## 4. 上一轮走错的路线（不要重走）

| 已尝试 | 结论 | 证据 |
| --- | --- | --- |
| 把每帧 `resetGLAll()` 改成 `reset(7 位)` 细粒度 | **不安全**：真实 GL 交替用例（dirty stencil/MSAA/dither/depth-cull + `clipRRect` 半透明）出现整帧像素差异；补齐 11 位后 OR=2047 ≈ `GL_ALL`，**无可判定收益** | `build/panel-opt-20261104/logs/i1-evidence-7bit.log`（RED）、`logs/i1-evidence-11bit.xml`（GREEN） |
| 只缓存 5 个 `MAX_*` 查询（P0-2） | 550–600 次里只省 5 次 ≈ **1%**，且「550–600」本身是按 32 个纹理单元估的，**低估近 6 倍** | 同上 + `GlStateScope` 源码 |
| 点阵预渲染成缓存（`Picture`） | 离屏全命中 −0.053ms（噪声 0.027ms）；**几何连续变化 / LRU>64 miss 场景反而更慢**（p50 +0.012~0.035ms、分配增量 8.5–9 倍） | `build/panel-opt-20261104/offscreen-ab-report.md` |
| 富文本/字体宽度脏标记 | 早就做了（`WIDTH_CACHE`、`MessageListView.layoutCache`），`LIST` 仅 0.29ms，**不是瓶颈** | 上一轮 A/B：p50 2.191→1.313ms |

**根因误判的教训**：把 `resetGLAll()` 当成主因，实际它只是「让 Skia 缓存失效」的一环；真正贵的是**查询本身**（2300 次同步 `glGet*`）和**嵌套多次 scope**。裁剪状态子集才是主刀口，而这需要「宿主到底改了哪些状态」的证据 —— issue 已经给出了可行裁剪范围（只留 2D/RECTANGLE、跳过 UBO、按实际 draw buffer 数）。

---

## 5. 建议的执行顺序（每步单独 A/B，一次只改一个因素）

1. **`GlStateScope` 裁剪**（收益最大、改动集中）
   - 纹理单元上限改为 `GL_MAX_TEXTURE_IMAGE_UNITS` 或 Skia 实际用到的数量；
   - `TEXTURE_BINDINGS` 只留 `TEXTURE_2D`/`TEXTURE_RECTANGLE`；
   - 跳过 UBO 绑定查询与恢复；
   - draw buffer 按当前 FBO 实际绑定数；
   - 合并嵌套 scope（背景/图标/绘制共享一个 scope，注意 `close()` 的恢复语义与异常安全）。
2. **Yoga `setMeasure` 改比较 `measureRevision`**（去掉每帧 `YGNodeMarkDirty`）。
3. **`TextService` 复用 `Shaper` + 缓存 `TextLine`**（font generation 变化才失效）。
4. 物品图标缓存与批量 flush。
5. 背景模糊复用/降采样/uniform 缓存。
6. `UiSession` 容器类型与 scale 缓存。

**每步都要**：先写可失败测试（旧路径红、新路径绿）；golden frame 逐像素零差异；同场景交替 A/B（3 轮、每场景 ≥3000 稳定帧、预热丢前 100、对照-对照 2σ 噪声门限、`panelNanos` p50/p95/p99 与外部 FPS 双指标 AND）。

---

## 6. 环境、构建与坑

### 6.1 工具链
- JDK：`C:/Program Files/Java/jdk-21.0.11`、`jdk-17.0.18`（Forge 构建用 17）。
- Gradle：各平台自带 `gradlew.bat`；离线模式 `--offline --no-daemon --console=plain`。
- 三端：`platforms/1.20.1-forge`、`platforms/1.21.1-fabric`、`platforms/1.21.1-neoforge`。

### 6.2 AC 构建（锁文件陷阱）
- 工作区 `versions/atomlib.json` 仍锁 `0.1.0-beta`，但代码依赖 0.3.0 的 `FontSource.resource(String)` → **按原锁编译必失败**。
- 正确做法：隔离源码快照（参考已删除的 `build/panel-opt-20261104/prepare_source.py` 模式，或 `build/panel-perf-20261104/prepare_source.py`），只改快照内锁为 `0.3.0` + 真实 jar sha256；**工作区锁一个字都不许动**。
- 快照 `copytree` 必须排除：`.git`、`build`、`.gradle`、`.work`、`.workbuddy`、`*.log`、`__pycache__`（`.work` 会让 `AvatarReadbackWiringTest` 源码守卫误判重复类）。
- NeoForge 需补 `build/atomlib-consumer/moddev/minecraft_assets.properties`（SHA `015d0bc7…`），并从旧快照复制。
- 构建命令：
  ```
  # Fabric
  ./gradlew.bat build --offline --no-daemon --console=plain -Dorg.gradle.java.home="C:/Program Files/Java/jdk-21.0.11" -PatomlibMavenRepo=<maven>
  # Forge
  ./gradlew.bat build --offline --no-daemon --console=plain -Dnet.minecraftforge.gradle.check.certs=false -Dorg.gradle.java.home="C:/Program Files/Java/jdk-17.0.18" -PatomlibMavenRepo=<maven>
  # NeoForge
  ./gradlew.bat build --offline --no-daemon --console=plain -x downloadAssets -Dorg.gradle.java.home="C:/Program Files/Java/jdk-21.0.11" -PatomlibMavenRepo=<maven>
  ```
- 验证门：`python tools/verify_targets.py`；`python tools/verify_jars.py --platform windows-x64 --profile atomlib-consumer platforms/<target>`；`python tools/probes/verify_profile_wiring.py`。

### 6.3 AtomLib 构建
- `./gradlew.bat publishToTestRepository --offline --no-daemon --console=plain` → `build/test-maven`；同坐标重发需清 loom 缓存（`tools/run_packaging.py` 里有 `clear_loom_cache` 逻辑）。
- 真实 GL 测试：`./gradlew.bat hiddenGlTest --tests <class> ... -PatomlibRenderEvidence=<隔离目录>`，**产物目录一定要指到隔离目录**，否则会污染 `docs/evidence/`。

### 6.4 已知坑
1. `.gitattributes` `* text=auto`，工作副本 CRLF；Python `write_text()` 写 LF 会让**按源码匹配的测试**假失败（`verify_targets.py` 已折叠行尾，但别依赖）。
2. `layers/mapping/official/` 与 `platforms/1.21.1-fabric/` 是**孪生**，两边同改；三端屏幕同步改，否则 `verify_targets.py` 失败。
3. `new Paint()` 默认 `antiAlias=true`，不是漏开。
4. **别恢复 `BlurMotionGate`**；`boolean full = true` 是有意的（毛玻璃每帧刷新，否则拖影），有测试断言。
5. `FrameProfile` 类注释「SkiaDraw 每次调用现造原生对象」**已过时**，别照它改。
6. 已经做对、不要「优化」：单 `DirectContext` 复用、Surface 尺寸不变不重建、Paint/MaskFilter 缓存、每帧一次 `flushAndSubmit(surface)` 且 `syncCpu=false`。
7. `Boolean.getBoolean` 只在类初始化读一次系统属性，不是每帧读。
8. 面板帧间隔 ≠ 游戏整帧时间；独立分位数不可相减；CPU 墙钟高不等于 CPU 忙。

---

## 7. 实机采样与验收口径

- **采样脚本**：`docs/verification/2026-11-04-panel-sampling-kit/auto-record.ps1`（零交互、自动等进程、`filename=` + `dumponexit=true`）。PCL 用自带 JDK 25 + `-XX:+PerfDisableSharedMem`，`jcmd -l` 看不到游戏，需 WMI 找 PID + 游戏自带 JDK 的 `jcmd/jfr`。
- **严格口径**：环境固定（关 Iris、固定世界/视角/分辨率/GUI 缩放/VSync/帧率上限/后台负载）→ 先确认关面板 400fps 基线 → 每支 3 轮、每场景 ≥3000 稳定帧、A-B-A-B/B-A-B-A 交替、预热丢前 100 帧 → 先测对照-对照噪声 σ，改动 p50 差异需 **>2σ** 才算收益 → `panelNanos` p50/p95/p99 与外部 FPS **双指标 AND**。
- **画面**：golden frame 逐像素零差异（`golden/` + 两端 `GoldenFrameTest`）+ 亮/暗背景截图人工确认。
- **判定权归用户**，不宣布达标。

---

## 8. 边界（务必遵守）

- **不启动游戏**；采样由用户执行。
- **不 commit / 不 push**；不改工作区 `versions/atomlib.json`。
- **不覆盖已发布版本**（`D:/AtomLib/build/rebuild-deploy-20261104/maven` 等）。
- **不动 AC 用户改动**（31 改 + 8 未跟踪）。
- 不改画质/动画/效果来冒充性能修复；一次只改一个因素，收益不相加。
- 部署到实例前必须：WMI 确认无游戏进程 → 备份 → 验哈希 → 出回执。

---

## 9. 交付物索引（保留）

| 用途 | 路径 |
| --- | --- |
| 三轮 JFR（核心证据） | `build/panel-perf-20261104/samples/panel-auto-20261104-202119.jfr`、`panel-auto-20261104-203101.jfr`、`probe.jfr` |
| 逐帧分析 | `build/panel-perf-20261104/analysis.json`、`compare-runs.json`、`supplement.json`、`log-profile.json` |
| 诊断/职责判定报告 | `docs/verification/2026-11-04-panel-performance-intake.md`（第 7–11 节） |
| 上一轮纯优化执行报告 | `docs/verification/2026-11-04-panel-optimization-execution.md` |
| 上一轮方案（Q1–Q7 决策） | `docs/superpowers/plans/2026-11-04-panel-optimization.md` |
| 实现/验证/离屏报告 | `build/panel-opt-20261104/IMPLEMENTATION-REPORT.md`、`golden-frame-report.md`、`offscreen-ab-report.md` |
| 六个已废候选 JAR（仅供对照） | `build/panel-opt-20261104/candidates/` |
| 部署回执（3 轮，含还原） | `build/panel-opt-20261104/deployment-receipt.json` |
| 仓库自带 scope A/B 证据 | `D:/AtomLib/docs/evidence/render-performance-20261104-followup.md` |
| issue #1 | `https://github.com/NoWordz/AtomLib/issues/1` |

---

## 10. 仍未验证 / 风险

1. **issue 是静态审计，未在游戏中实测**（作者自述）。唯一有实测支撑的是 3.1 的量级（单次 scope ≈2.3ms）与我们的 `BRIDGE_ENTER` 2.12ms 吻合；3.2/3.3/3.4/3.5/3.6 的收益**尚未测量**，需逐项 A/B。
2. 一帧内 scope 的**实际次数**未在 Minecraft 内计数（issue 估 4–7 次）；建议先加一个每帧计数器（零成本），确认后再决定「合并 scope」的收益上限。
3. GPU 侧完全未测（timer query 未接线）；`BRIDGE_EXIT` 偶发尖峰（run2 max 56ms）仍可能是驱动同步。
4. 三端仅 Fabric 是用户实际反馈环境；Forge/NeoForge 只要求不回归。
5. 上一轮的 `Picture` 点阵实现仍在工作区（默认关）；若不做点阵方向，建议按 §1.3 回退，减少干扰。
