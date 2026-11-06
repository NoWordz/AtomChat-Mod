# 2026-11-06 背景模糊 pre-pass 真机分段计时探针（prepass_probe）

**性质**：只加诊断 + 三端隔离快照构建 + 候选包产出。**不改任何性能逻辑、不改视觉、不部署、不启动游戏、不替换实例 JAR、不改配置/启动器、不 commit / push / 发布；未安排用户验收，由主代理统一部署与安排。**
**全部数值标注「离屏/受控场景」或「真机待验」，不把任何离屏/受控数值宣称成游戏 FPS 收益。**

前序（直接采信主代理真机分析，本轮不重做）：
`docs/verification/2026-11-06-truth3-results.md`（§5、§8）、`docs/verification/2026-11-06-p0c-gl-sync-elimination.md`、`docs/verification/2026-11-06-truth3-mode-experiment.md`。

---

## 0. 一句话

在 `PanelBlurRenderer.render()` 的 pre-pass 主路径上装 **10 个 `System.nanoTime()` 计时点**，把每帧拆成 `entry / capture / passes / state / composite` 五段 + 一条**独立于分段的「入口→出口总墙钟」**，用 `unaccounted = total − Σparts` 暴露**未被任何分段覆盖的隐式等待 / GL 同步 / 驱动排队**。一次启动按 F6 走 A→B→C 即可直接读出每段的真机耗时。P0-C 与本轮探针一起打成三端候选包，**未部署**。

### 为什么要做（采信，不重述分析）

| 项 | 值 |
| --- | --- |
| 真机三档 `frame_probe` p50 | A 完整 **2.914 ms / 343.2 FPS**；B 只跳背景模糊 pre-pass **2.6745 / 373.9**；C 整个面板旁路 **2.491 / 401.4**（撞 400 帧锁，上限非天花板） |
| **A−B** | **0.239 ms**，占 A−C 可回收 0.423 ms 的 **56%**，最大可回收块 |
| 决定性反证 | `gpu_panel` 对 A/B 完全不敏感（0.1256 / 0.1259 ms），只有 C 才降 → A−B 成本**不在 GPU 执行时间**，优化 shader / 降分辨率 / 减 pass 收不回且动视觉 |
| P0-C 已实现（未部署） | 削掉每帧冗余 `glGet*`（QUERY 24→9），但离屏 `query_roundtrips` wall 仅省 **0.0375 ms**；且 `errorCheckDue()` 在 `debug=true` 恒 true（CCB 实例正是 `debug=true`），该项收益为 0 |
| **结论** | **剩余约 0.20 ms 未归因**。用户拍板「先量清楚再改」：一次启动走 A→B→C，直接读出 pre-pass 每一段的真机耗时 |

---

## 1. 改动文件清单（file:line）

| 文件 | 行号 | 改动 |
| --- | --- | --- |
| `shared/src/main/java/com/atom/chat/diagnostics/PrepassProbe.java` | 全文（**新增**）：`:40` 类、`:44` `WINDOW=1024`、`:46` `REPORT_EVERY=600`、`:49` `PREPASS_LINE_RE`、`:82` `onFrame`、`:106` `record`、`:134` `resetWindow`、`:142` `report` | 纯 Java 独立探针（只 import `java.util.Locale`）：有界环形窗口 + 每 600 帧一行日志 + 切档重置 + B/C `skipped` + debug 门控 |
| `platforms/1.21.1-fabric/src/main/java/com/atom/chat/render/PanelBlurRenderer.java` | `:79-83` 声明 + `ppM0`；`:111` `ppM1`；`:130/:132` `ppM2/ppM3`；`:144/:147` `ppM4/ppM5`；`:151/:153/:156` `ppM6/ppM7`；`:170` `ppM8`；`:187` `ppM9`；`:188-195` `record`；`:256-264` `isAvailable()` 每帧 tick | 10 个计时点 + 灌样本 + 每帧 tick |
| `platforms/1.20.1-forge/.../PanelBlurRenderer.java` | `:96-100`、`:128`、`:147/:149`、`:161/:164`、`:168/:170/:173`、`:187`、`:204`、`:205-212`、`:268-276` | 同源 8 hunk（主路径整体 +17 行，`isAvailable` 段 +12 行）；`refreshBlur` 调用为 `fb.frameBufferId` |
| `platforms/1.21.1-neoforge/.../PanelBlurRenderer.java` | 同 Forge | 同源 8 hunk |
| `shared/src/test/java/com/atom/chat/diagnostics/PrepassProbeTest.java` | 全文（**新增**，8 个 `@Test`） | 分段不重不漏 / 切档重置 / B·C `skipped` / 日志可解析 / debug=off 静默 / unaccounted 正确 / 无界分配为 0 / 三端接线一致 + 红线 |
| `build/prepass-probe-20261106/**` | 新增脚本 / 探针 / 日志 / 样例 / 候选包 | 见 §11 产物清单 |

三端渲染器由 `apply_prepass_patches.py` **同一套 8 个 anchor 字节级应用**（自适应 CRLF，每端每个 anchor 预检恰好命中 1 次，任一不满足即整体 ABORT、不写任何文件）。`verify_wiring.py` 逐行比对三端 23 行探针接线**完全一致**。

**未改动**：`AtomChatScreen.java`（三端）、`Truth3Probe.java`、`AtomChatConfig.java`、`FrameProfile.java` / `FrameSegments.java` / `GlGpuTimer.java` / `GpuTimerQuery.java`（P1 的 `gpuSummary` 本轮一次一个因素，**不碰**）、`SkiaGraphics.java` 孪生（official ↔ fabric，本轮一行未碰）、`NotificationHudOverlay.java` 孪生、shader、`boolean full = true;`、P0-C 查询削减、P0-A 容量复用语义（`capacityMode`/`nextPow2`/`extendEdge`/blit 子区域/采样 UV/圆角/alpha）、状态恢复语义、`versions/atomlib.json` 工作区（仍 `0.1.0-beta`）、`docs/` 已有报告、实例目录、`build/*/source/**` 历史生成快照、Truth3 三档功能（F6 / `diagnosticRenderMode` / `frame_probe` 全部保留）。

---

## 2. 分段定义（边界与为什么这样切）

计时点全部落在 `render()` 主路径（`System.nanoTime()`，单位 ns，最后除以 `n×1e6` 得 ms）。段与段之间**刻意留白**：留白时间落入 `unaccounted`，这正是探针要暴露的东西。

| 段 | 边界（fabric file:line） | 覆盖内容 | 为什么这样切 |
| --- | --- | --- | --- |
| **entry** | `ppM0`（`:81`，render 第一行）→ `ppM1`（`:111`，3 个 `glGet*` 之后） | 入口准备（`client`/`fb`/`scale`/坐标计算）+ P0-C **保留**的 3 个 `glGet*`（`GL_FRAMEBUFFER_BINDING` / `GL_VIEWPORT` / `GL_SCISSOR_TEST`） | 这是唯一真实的外部状态边界、也是 P0-C 唯一保留的同步查询；单列可直接读出"保留的那 3 个查询"在真机满队列下的代价 |
| **capture** | `ppM2`（`:130`）→ `ppM3`（`:132`），包住 `ensureTextures(pw, ph, clipped)` | P0-A 容量复用 / `nextPow2` / 重建判定 | 稳态 0 GL 调用、直接 `return`；单列可验证"资源复用是否真的零成本"，与 blit 上传分开 |
| **passes** | `ppM4`（`:144`）→ `ppM5`（`:147`），包住 `refreshBlur(...)` 整体 | **blit 上传**（主 framebuffer → `inputTex`）+ `extendEdge` + **5 个 Kawase pass** + `refreshBlur` 尾部状态恢复 | 任务允许「`refreshBlur` / `kawasePass` 合计」与「blit 如与 capture 分离则单列，否则合并并说明」。blit 与 5 pass 都在 `refreshBlur` 内部；若单列需改 `refreshBlur`/`kawasePass` 签名或在内部打点，会扩大三端同步面且「别为此增加同步」。**故合并进 `passes`，未逐 pass 拆分**（遗留风险 §10） |
| **composite** | `ppM6`（`:151`）→ `ppM7`（`:153` 失败分支 / `:156` 正常分支），包住 `drawRoundedQuad(...)` | 合成 / 上屏最终绘制 | 唯一一处上屏绘制，独立成段 |
| **state** | `ppM8`（`:170`，finally 第一行）→ `ppM9`（`:187`，finally 最后一行） | 最终状态恢复：FBO / viewport / scissor 的 GL 写 + `GlStateManager` 镜像双写 | 与 `refreshBlur` 尾部的内部恢复区分；这是 render 对外交接状态的边界，也是 P0-C「双写语义」保留处 |
| **⭐ total** | `ppM0`（`:81`）→ `ppM9`（`:187`） | **入口→出口总墙钟，独立于上述 5 段** | 关键设计：若各段紧密拼接，则 `Σparts ≈ total`、`unaccounted ≈ 0`，无法暴露同步气泡。total 首尾独立取值、段之间留白，差值才是「未被任何一段 CPU 提交覆盖的时间」 |
| **unaccounted** | `total − (entry+capture+passes+state+composite)` | 段外胶水 / 隐式等待 | **逐样本**计算后取均值，保证 `mean(unaccounted) = mean(total) − Σ mean(parts)` 严格成立；负值钳制到 0 |

**段间留白具体包含**（全部落入 `unaccounted`）：`errorCheckDue()`、入口与出口 `drainGlErrors()`（**debug=on 时每帧的 `glGetError` 同步点，正是 P0-C 报告 §8 指出「CCB 实例 debug=true、该项收益为 0」的那一项**）、`uvX/uvY`/`clipped` 分支/`vpW…` 计算、try/finally 调度与 `return`、`if (checkErrors)`、以及 `System.nanoTime()` 自身开销 —— 还有任何 driver 在段外插入的隐式等待 / GL 同步 / 驱动排队。

**口径边界（如实披露）**：若 driver 阻塞发生在**某段内部**（例如 `glBlitFramebuffer` / `BufferRenderer.drawWithGlobalProgram` 内部等待），它会被计入该段，**不会**进 `unaccounted`。因此 `unaccounted` 是「段之外的守恒残差」，不是「全部同步时间」；判读时要把段内异常大的那一段与 `unaccounted` 一起看。

---

## 3. 探针架构与输出契约

### 3.1 每帧 tick 为什么挂在 `isAvailable()`

- `PrepassProbe.onFrame(mode, debug)` 需要**每游戏帧、A/B/C 三档都被调用一次**，才能做到「同节奏输出」「切档重置」「B/C `skipped`」。
- 红线禁止改 `AtomChatScreen.java`，因此 tick 必须挂在 `PanelBlurRenderer` 里三端共有、每帧且三档都执行的方法上。核对三端 `AtomChatScreen.render()`：`if (blurWanted && PanelBlurRenderer.isAvailable() && Truth3Probe.get().blurPrepass())` 位于 `if (Truth3Probe.get().drawPanel())` **之前**，A/B/C 三档每帧都执行（`&&` 短路下 `isAvailable()` 必被求值，只要 `blurWanted=true`）。三端 `isAvailable()` 方法体逐字相同。
- `render()` 只在 A 档 pre-pass 调用，故 `record()`（灌样本）只在 A 档发生；B/C 档窗口空 → 输出 `n=0 skipped=1`，**不伪造数据**。
- `mode` 由 `Truth3Probe.get().mode().ordinal()`（0=A,1=B,2=C）灌入，探针本身不引用任何 MC / Truth3 类型（纯 `java.*`）。

### 3.2 `PrepassProbe` 行为

- **有界环形窗口**：7 个 `long[1024]`（total/entry/capture/passes/state/composite/unaccounted）= 固定 **56 KiB**，与帧数/运行时长无关；`resetWindow()` 只置 `size/head/frameCount=0`（惰性覆盖，不清数组），零额外写。
- **统计口径：算术均值**（对窗口内最近 `n` 个样本）。**不用 p50/p95** —— 分位数不可线性相加；均值口径才能让 `unaccounted` 的守恒式严格成立。
- **每 600 帧输出一行**（与 `Truth3Probe.frame_probe` 同节奏）：`frameCount` 达 600 → 输出 → `resetWindow()`。
- **切档即重置**：`onFrame` 与 `record` 都检测 `mode != windowMode`（或 debug 状态变化）→ `resetWindow()`，语义与 `Truth3Probe.resetWindow()` 等价，保证不串档。
- **debug 门控**：`onFrame(mode, false)` 立即返回 null、清空窗口，不记录/不输出/不格式化；三端渲染器用 `final boolean probeOn = AtomChatConfig.get().debug` 把全部计时点写成 `probeOn ? System.nanoTime() : 0L`，并用 `if (probeOn)` 包住 `record()`。**debug=off：0 次 nanoTime、0 次数组写、0 分配、0 日志**，产品渲染输出与日志逐字节不变。
- **不引入** `glFinish` / 阻塞式 query / `System.currentTimeMillis` / 无界对象 / 每帧日志。

### 3.3 日志格式（A/B/C 三档同格式，一行稳定可解析）

```
[AtomChat][probe] prepass_probe mode=A n=600 skipped=0 total=0.850 entry=0.060 capture=0.030 passes=0.420 state=0.050 composite=0.110 unaccounted=0.180
[AtomChat][probe] prepass_probe mode=B n=0 skipped=1 total=0.000 entry=0.000 capture=0.000 passes=0.000 state=0.000 composite=0.000 unaccounted=0.000
```

| 字段 | 含义 |
| --- | --- |
| `mode` | 当前档 `A`/`B`/`C`（**必须含**，解析脚本按它归档并校验串档） |
| `n` | 本次窗口内**实际**样本数（A 档稳态 600；切档后首窗因输出发生在帧首为 599，见 §10） |
| `skipped` | `1` = 本窗口 pre-pass 被旁路（B/C 档），所有数值字段为 0；`0` = 有实测样本 |
| `total` | ⭐ 入口→出口总墙钟（ms，3 位小数） |
| `entry` / `capture` / `passes` / `state` / `composite` | 五段墙钟（ms，均值口径） |
| `unaccounted` | `total − Σ5 段`（ms，逐样本钳制 ≥0） |

契约正则导出为 `PrepassProbe.PREPASS_LINE_RE`；解析脚本 `parse_prepass_log.py` 同形状。日志行由探针生成完整字符串，三端 `AtomChat.LOGGER.info(line)` 输出，因此三端格式完全一致。

---

## 4. 每帧新增开销上界（**静态推理 / 离屏，非真机测量**；不代表 FPS 收益）

| 项 | 上界 |
| --- | --- |
| `System.nanoTime()` | **10 次/帧**（运行时；字节码里 19 个调用点，是 javac 把 `finally` 块复制到各 `return` 路径所致，每帧只走一条路径）。Windows TSC 读保守按 100 ns/次 → ≤ **0.001 ms** |
| 数组写 / 逻辑 | `record()` 7 次 `long[1024]` 写 + head/size 自增 + 6 次 `Math.max` + 1 次加法/减法；`onFrame()` 帧计数/切档比较；`isAvailable()` 一次 `AtomChatConfig.get()` + `Truth3Probe.get().mode().ordinal()`（零分配）。合计几十条指令，≤ **0.0005 ms** |
| 对象分配 | 稳态每帧 **0 字节**（探针无 `new`；`String.format` 只在每 600 帧一次，短命 Formatter/StringBuilder/String） |
| 摊还（每 600 帧一次） | 7×600 = 4200 次 `long` 读求均值、1 次 `String.format`（7 个 double）、1 行日志 I/O → 摊还 ≤ 7 次读/帧 + 1/600 次格式化，≤ **0.001 ms/帧** |
| 内存 | 固定 7×1024×8 B = **56 KiB**，与运行时长无关；无界增长为 0 |
| `debug=off` | 0 次 nanoTime、0 次数组写、0 分配、0 日志、0 排序/格式化 |

**每帧新增开销上界 ≤ 0.005 ms**；即便取 2 倍保守余量 **≤ 0.01 ms/帧**。相对真机 A−B 的 **0.239 ms**，占比 ≤ **4.2%**，不会淹没待测信号；且该开销在 A/B/C 三档**共同**新增，做档位差时大部分相互抵消。**以上为静态推理 / 离屏受控，非真机测量，不宣称等于游戏 FPS 收益。**

---

## 5. ⭐ `unaccounted` 的判读方法（专门讨论）

`unaccounted = total − (entry+capture+passes+state+composite)`，是「总墙钟里没有任何一段 CPU 提交能解释掉的残差」。它把 §2 列出的段外胶水（尤其 **入口/出口 `glGetError` drain** 这一 CPU 同步点）与任何段外的隐式等待 / GL 同步 / 驱动排队全部收进来。

| `unaccounted` 占 A−B 比例 | 判读 | 下一步 |
| --- | --- | --- |
| **大**（接近 0.239 ms，或 >30%） | A−B 的**大头不是任何一段 CPU 提交**，而是段外的隐式等待 / GL 同步 / 驱动排队。**逐段优化 shader / blit / pass 是白做**，应转向同步气泡定位（`glGetError` drain、GL 状态往返、buffer 排队、队列深度）。**这比逐段优化更重要** | 先查同步点，再谈逐段 |
| **小** | 成本落在显式分段内 | 按占比最大的那一段定点排查（仍需真机复测） |

**必须的三个口径提醒**：
1. 段内发生的 driver 阻塞计入该段、不进 `unaccounted`（§2 口径边界）；`unaccounted` 大或小都要与「哪一段异常大」一起读。
2. `prepass_probe` 的分段是 **CPU 侧墙钟**（提交 + 隐式等待），`frame_probe` 是**游戏帧间隔**，`gpu_panel` 是 **P1 GPU query**（绝对值已判定不可信，本轮完全不用）。**三者口径不同，不可混用、不可相减**；「每段占 A−B 的比例」只是把 CPU 墙钟与帧间隔可回收预算放在一起定位量级，**不宣称等于游戏 FPS 收益**。
3. 解析脚本在 `unaccounted` 占比 ≥30% 时自动打印「大头在段外、应转向同步气泡」的提示；样例自测输出见 §8。

---

## 6. 解析脚本

```
python build/prepass-probe-20261106/parse_prepass_log.py <latest.log> [--json out.json]
```

按 `mode_switch` 切段；把段内 `prepass_probe` 与 `frame_probe` 分别归档；输出每档每段的 **p50 中位数**（跨 600 帧窗口取中位）、与同一份日志 `frame_probe` 的 A/B/C 帧间隔 p50 中位对齐、**每段占 A−B 的比例**、`unaccounted` 判读提示；`--json` 另写结构化结果。串档（行内 mode 与所属段不符）会告警。

合成样例自测：`build/prepass-probe-20261106/sample-prepass.log`（带 MC 日志前缀的三档合成日志，数值为**离屏构造、非真机**）→ `sample-parsed.json`。

### 样例输出（离屏合成，节选）

```
== 每档每段 p50 中位（ms；跨 600 帧窗口取中位；skipped 行不计入 measured） ==
 档   pp窗  skip  measured        total        entry      capture       passes        state    composite  unaccounted   frame_p50
 A     3     0         3        0.850        0.060        0.030        0.420        0.050        0.110        0.180       2.914
 B     2     2         0          n/a          n/a          n/a          n/a          n/a          n/a          n/a       2.675
 C     2     2         0          n/a          n/a          n/a          n/a          n/a          n/a          n/a       2.490

== 与 frame_probe 对齐（帧间隔 p50 中位，ms） ==
  A=2.914  B=2.675  C=2.490  →  A−B=0.240 ms（帧间隔口径，非 GPU 时间）

== 每段占 A−B 的比例（prepass 墙钟 / 帧间隔差；口径不同，仅供定位） ==
  entry 25%  capture 13%  passes 175%  state 21%  composite 46%  unaccounted 75%

== unaccounted 判读 ==
  unaccounted 占 A−B 的 75% —— 大头【不在任何一段 CPU 提交】，而在段外的隐式等待 /
  GL 同步 / 驱动排队（含入口/出口 glGetError drain、状态往返气泡）。逐段优化
  shader/blit/pass 收不回，应转向同步气泡定位；这比逐段优化更重要。
```

> 样例数值为**离屏构造**，用于证明脚本可解析、切段、对齐、给出比例与提示；**不是真机测量、不代表任何 FPS 收益**。

---

## 7. 红绿与构建结果

| 门 | 结果 |
| --- | --- |
| `python tools/verify_targets.py` | **通过**（6 目标，含 mapping twins / shared Java 级别 / 第三方依赖 / seams 文档同步） |
| `python build/prepass-probe-20261106/verify_wiring.py` | **通过**：三端 23 行探针接线逐行一致；每端 nanoTime 调用点 11（静态）、6 个段表达式、`record`/`onFrame` 各 1、`final boolean probeOn` 门控；红线（`boolean full = true;` 各 1、无 `BlurMotionGate`、P0-C `errorCheckDue`、P0-A `capacityMode`/`nextPow2`/`extendEdge`、入口 `glGetInteger` 调用点恰 2、无 `glFinish`/`currentTimeMillis`/`GpuTimerQuery`）；探针纯 `java.*`；工作区锁仍 `0.1.0-beta`；孪生两对均在且未逐字相同 |
| **新增单测** `PrepassProbeTest`（8 个 `@Test`） | **三端各 8/8、0 failures、0 errors**：分段不重不漏、切档重置、B/C `skipped`、日志格式可解析、debug=off 静默、`unaccounted` 计算正确（含逐样本钳制）、不产生无界分配、三端接线一致 + 红线 |
| `shared/src/test/java/com/atom/chat/notification/NotificationRenderWiringTest.java:46-52` | **三端各 7/7、0 failures**（`boolean full = true;` 各 1 / 禁 `BlurMotionGate` 仍绿） |
| `Truth3ModeTest` | **三端各 11/11、0 failures**（Truth3 三档功能全部保留） |
| 离屏纯 JDK 冒烟 `ProbeSmoke` | **PROBE_SMOKE_PASS: true（21 checks）**：A 档输出 `n=599 total=1.000 unaccounted=0.250`、B/C `skipped`、切档重置、debug=off 静默、环形窗口 5000 次 record 后引用不变 |
| **变异红灯** | `mutate_prepass.py red`：在**隔离副本**把 `ppM9 - ppM0`（⭐ total 统计）改成 `0L` → `verify_wiring.py` **rc=1（RED）**，三端各报「total 统计缺失」；`green` 从工作区重建干净副本 → **rc=0（GREEN）**。日志 `mutation-red.log` / `mutation-green.log`。**工作区三端渲染器一字未动** |
| 三端隔离快照构建 | 见下表，**全部 BUILD SUCCESSFUL** |

### 三端隔离构建（HANDOFF §6.2 / `prepare_snapshot.py`）

- `copytree` 排除 `.git`/`build`/`.gradle`/`.work`/`.workbuddy`/`*.log`/`__pycache__`；**只改快照内** `versions/atomlib.json` → `0.3.1-shadowcache`；工作区锁**一字未动**（仍 `0.1.0-beta`，`git status --short versions/atomlib.json` 为空）。
- NeoForge 已知坑：从 P1 快照预置 `build/atomlib-consumer/moddev/minecraft_assets.properties`，SHA `015d0bc702eff9f9e7bc7d1a514c8c554118407635c5d1c56470d62a38f04560` 校验一致。
- 快照内无 `build/` 泄漏；三端渲染器均含 `PrepassProbe.get().record(` / `onFrame(` / nanoTime=11 / `errorCheckDue` / `full=true`×1 / `glGetInteger`×2。

| 端 | 命令要点（全部 `--offline --no-daemon --console=plain -PatomlibMavenRepo=<maven>`） | 结果 |
| --- | --- | --- |
| Fabric | JDK21 | **BUILD SUCCESSFUL 4m16s**（16 tasks，`:test` 执行） |
| Forge | JDK17 + `-Dnet.minecraftforge.gradle.check.certs=false` | **BUILD SUCCESSFUL 3m38s**（26 tasks） |
| NeoForge | JDK21 + `-x downloadAssets` | **BUILD SUCCESSFUL 2m37s**（16 tasks） |

---

## 8. 拆包证明（三端候选 JAR 同时含五批改动）

`verify_candidates.py`：`zipfile` 读 class 常量池 token + `javap -c` 字节码。

| 批次 | 证据（三端一致） |
| --- | --- |
| **本轮 `prepass_probe`** | `PrepassProbe.class` 存在，常量池含 `prepass_probe` / `unaccounted` / `PREPASS_LINE_RE` / `WINDOW` / `REPORT_EVERY`；`PanelBlurRenderer.class` 含 `PrepassProbe` / `nanoTime`；`javap -c` 可见 `System.nanoTime:()J` / `PrepassProbe.record:` / `PrepassProbe.onFrame:` 调用点 |
| **P0-C**（本次一起部署） | `PanelBlurRenderer.class` 含 `errorCheckDue` / `AtomChatConfig` / `restoreViewport` / `restoreScissor` / `checkErrors`；`javap -c` GL 查询调用点 **`glGetInteger=2, glGetIntegerv=1, glIsEnabled=3, glGetError=1`**（改动前 truth3 基线 `4/3/4/1`，证明削减生效） |
| **P0-A** | `PanelBlurRenderer.class` 含 `capacityMode` / `nextPow2` / `extendEdge` |
| **P1** | `GlGpuTimer.class` / `GpuTimerQuery.class` 存在；`FrameProfile.class` 含 `page_phase` / `PAGE_RENDER` / `firstOpen` |
| **Truth3** | `Truth3Probe.class` 含 `frame_probe` / `mode_switch` / `REPORT_EVERY`；`AtomChatConfig.class` 含 `diagnosticRenderMode` |

> `javap -c` 显示 19 个 `nanoTime` / 5 个 `record` 调用点：这是 **javac 把 `finally` 块复制到各 `return` 路径** 的结果，不是多份探针；运行时每帧仍只走一条路径（10 次 nanoTime / 1 次 record）。源码级调用点数由 `verify_wiring.py` 精确断言为 11/1/1。
> `pageKey` 沿用 truth3 口径：编译后常量池无此字面量，以 `FrameProfile` 的 `page_phase` 等实际字段证明在包内。本轮**完整保留 Truth3 三档功能**（下次真机验收仍按 F6 走 A→B→C）。

---

## 9. 候选包与哈希

目录 `D:/AtomChat-Main/build/prepass-probe-20261106/candidates/`（文件名与现役一致，便于主代理直接部署）；`sha256sum -c sha256.txt` 三端 **OK**。

| 文件 | sha256 |
| --- | --- |
| `atomchat-Fabric-1.21.1-v0.5.0.jar` | `3991aa127ebcf12db2591f6d8aad362bda8f648ec424d64b989c74ce0b7e2dcf` |
| `atomchat-Forge-1.20.1-v0.5.0.jar` | `7b6a18aae4db56ddfd7cc7e96db513e375f9e7ce71189fec94a3dbef95606833` |
| `atomchat-NeoForge-1.21.1-v0.5.0.jar` | `11ecada9205ff81570be6faf9e0dbf16df010555d73a0ea11a9fc2fcb2e61356` |

---

## 10. 用户一次启动的极简验收步骤（**由主代理统一安排；本子代理未部署、未安排验收**）

1. 主代理把 `candidates/` 里对应加载器的 jar 部署到实例（**本子代理未部署、未替换实例 JAR**）。
2. 确认 `atomchat/atomchat-client.json` 的 `debug = true`（CCB 实例历史配置已是 `true`）；`diagnosticRenderMode` 留默认 `"A"`。
3. 启动游戏 → 进固定场景（固定世界/视角/分辨率/GUI 缩放/VSync/帧率上限/后台负载）→ **打开聊天面板并保持静止**。
4. **每档停留 ≥ 20 秒**（600 帧 @343 FPS ≈ 1.75 s/窗口，20 s ≈ 11 个窗口），按 **F6** 依次 **A→B→C→A**；每按一次确认日志出现 `mode_switch from=X to=Y`。若 F6 被 mod 占用，改按 **Ctrl+Shift+F6**，或改配置字段重启。
5. 退出，取 `latest.log`，跑：
   ```
   python build/prepass-probe-20261106/parse_prepass_log.py <latest.log> --json out.json
   ```
6. **怎么读**：看 A 档 `entry/capture/passes/state/composite/total/unaccounted` 各段 ms 与「每段占 A−B 的比例」；B/C 档应为 `n=0 skipped=1`；重点看 `unaccounted` 占 A−B 的比例与判读提示（§5）。判定权归用户/主代理，**本包不宣布达标、不宣称任何 FPS 收益**。

---

## 11. 遗留风险与口径限制（如实披露）

1. **未真机验证**：本包只保证「能出数、能切档、格式可解析、三端构建全绿」。全部帧间隔/分段数值待主代理按 §10 验收；**离屏/受控数值不等于游戏内收益**。
2. **tick 口径**：每帧 tick 挂 `isAvailable()`。验收场景（打开聊天面板、静止、`blurEnabled=true` 且无壁纸、无活跃通知横幅）下每游戏帧恰好 1 次；若同时存在活跃通知 HUD，`isAvailable()` 一帧可能 2 次 → 帧计数偏快、窗口 `n` 偏小；若有活跃通知横幅，其 `render()` 也会灌样本（B/C 档窗口可能非空）。**已知口径限制，不伪造数据**；静止验收场景不触发。
3. **首窗 `n=599`**：输出发生在帧首（`isAvailable` tick），统计截至上一帧末，切档后首窗会少 1 个样本，后续窗口稳定 600。`n` 如实为窗口内实际样本数。
4. **`blit` 与 5 pass 合并进 `passes`**：为不改 `refreshBlur`/`kawasePass` 签名、不增加同步、不扩大三端同步面而整体计时，**未逐 pass 拆分**（任务允许「合计」与「合并并说明」）。
5. **段内阻塞计入该段**：`unaccounted` 只暴露段外残差，不是「全部同步时间」（§5）。
6. **三种口径不可混**：prepass 墙钟 / frame_probe 帧间隔 / P1 `gpu_panel` 不可相减；「每段占 A−B 比例」仅供定位。本探针不使用、不修改 P1 的 `GpuTimerQuery` / `GlGpuTimer` / `FrameSegments.gpuSummary`。
7. **C 档 400 帧锁、`gpu_panel` 绝对值不可信、4096 帧滚窗不可做档位差**等沿用主代理 §1/§3/§5 结论。
8. **debug=on 本身改变被测对象**：CCB 实例 `debug=true`，入口/出口 `glGetError` drain 每帧执行（P0-C 报告 §8 已指出该项收益为 0）；这些同步点**刻意落入 `unaccounted`**，正是要暴露的目标之一。产品默认 `debug=off` 时探针完全静默、行为逐字节不变。
9. 三端 `PanelBlurRenderer.java` 本就是未提交 WIP；本轮仅在其上叠加 8 个同源 hunk，`apply_prepass_patches.py` 与 `verify_wiring.py` 可完整核对差异。
10. **未部署、未启动游戏、未真机验收、未 commit / push / 发布**；未改 `docs/` 已有报告（仅新增本报告）、未改 shader、未改 `versions/atomlib.json` 工作区、未动 `build/*/source/**` 历史快照、未恢复 `BlurMotionGate`、未改 `boolean full = true;`、未做静止降频 / 开合冻结 / 半分辨率 / 借旧模糊档等任何产品侧改变、未改 P0-A 容量复用语义与 P1 GPU 计时实现。一次一个因素，收益不相加。

---

## 12. 本轮未做的事（红线声明）

**未部署、未启动游戏、未真机验收、未 commit / push / 发布**；未替换实例 JAR、未改实例配置/启动器、未改 `D:/Myworld/.minecraft/versions/*` 下任何文件；**未安排用户验收**。
未使用 `git checkout` / `reset` / `stash` / `clean` 或任何覆盖式回退；两仓大量未提交 WIP（AC ≈92 项 / Lib ≈40 项）原样保留。除**三份 `PanelBlurRenderer.java`** + 新增 `PrepassProbe.java` / `PrepassProbeTest.java` + `build/prepass-probe-20261106/` 下探针/脚本/日志/样例/候选包 + **新增本报告** 外，不碰任何其它文件。

---

## 附：本轮产物（`build/prepass-probe-20261106/`）

| 文件 | 内容 |
| --- | --- |
| `candidates/*.jar` + `sha256.txt` | 三端候选包与哈希清单 |
| `apply_prepass_patches.py` | 三端同源字节级补丁（8 anchor、CRLF 自适应、预检 ABORT） |
| `verify_wiring.py` | 三端同步 / 分段不重不漏 / 红线 / 锁 / 孪生 / 探针纯 Java 静态校验 |
| `PrepassProbe.java`（shared）、`PrepassProbeTest.java`（shared test） | 探针与 8 项单测 |
| `ProbeSmoke.java` + `probe-classes/` | 纯 JDK 离屏冒烟（21 checks） |
| `parse_prepass_log.py` + `sample-prepass.log` + `sample-parsed.json` | 主代理可直接用的解析脚本与合成样例 |
| `mutate_prepass.py` + `mutation-red.log` / `mutation-green.log` + `mutation/` | 变异红灯（隔离副本，工作区不动） |
| `prepare_snapshot.py` + `snapshot/` | 三端隔离构建快照（锁 0.3.1-shadowcache、NeoForge 资产坑处理） |
| `collect_candidates.py` + `verify_candidates.py` | 候选包收集 + 拆包证明（含改动前基线 javap 对比） |
| `build-{fabric,forge,neoforge}.log`、`javap-fabric.txt` | 三端构建日志与 javap 证据 |
| `docs/verification/2026-11-06-prepass-segment-probe.md` | 本报告 |
