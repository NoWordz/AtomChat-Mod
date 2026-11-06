# 2026-11-06 P0-C：消除背景模糊 pre-pass 每帧 CPU 侧 GL 状态同步查询

**性质**：三端生产源码改动 + 三端隔离快照构建 + 候选包产出 + 离屏/受控验证。
**未部署、未启动游戏、未真机验收、未 commit / push / 发布**；未替换实例 JAR、未改实例配置/启动器、未改 `D:/Myworld/.minecraft/versions/*` 下任何文件；**未安排用户验收，由主代理统一处理**。
**全部数值标注「离屏/受控场景」或「真机采信/待验」；不把任何离屏/受控数值宣称成游戏 FPS 收益。**

前序（直接采信主代理真机分析，本轮不重做）：
`docs/verification/2026-11-06-truth3-results.md` §5、`docs/verification/2026-11-06-truth3-mode-experiment.md`、`docs/verification/2026-11-06-truth-test-log-analysis.md`。

---

## 0. 一句话

把 `PanelBlurRenderer` 每帧 `24 → 9` 次 GL 同步查询（稳态 debug=off，`−15/帧 / −62%`），并合并冗余的 FBO/viewport 状态往返（`99 → 66` 次调用/帧），**完全不动视觉与状态恢复语义**。离屏 golden frame 逐像素零差异、外部 mod 改状态/context 丢失/资源重建反证全通过。真机收益待主代理用内置三档对照验收（判据见 §7）。

---

## 1. 采信证据与靶子

| 项 | 值（真机，主代理采信，本轮未复测） |
| --- | --- |
| 三档 `frame_probe` p50 | A 完整 **2.914 ms / 343.2 FPS**；B 无 pre-pass **2.6745 / 373.9**；C 无面板 **2.491 / 401.4**（撞 400 帧锁，是上限非天花板） |
| A−B | **0.239 ms**，占 A−C 可回收 0.423 ms 的 **56%** |
| 决定性反证 | `gpu_panel` A/B 几乎相同（0.1256 / 0.1259 ms），只有 C 才降到 0.0946 → **A−B 成本不在 GPU 执行时间**，优化 shader / 降分辨率 / 减 pass 收不回且动视觉 |
| 上一轮离屏受控 | GL 状态保存/恢复固定约 **0.5 ms 纯 CPU，GPU 仅 0.0002 ms**（`build/astra-performance-20261105/probe-results.txt`：`current_scope_capture_restore wall_p50=0.5173 gpu_p50=0.0002`；`blur5_queries` wall 涨、gpu 不变） |
| 波动层级 | A 的 p50 全距 1.240 ms、B 0.361、C 0.014 —— 波动几乎全由毛玻璃路径引入 |

代码靶子（`platforms/1.21.1-fabric/.../PanelBlurRenderer.java` **改动前**行号）：`:102` `glGetInteger(FRAMEBUFFER_BINDING)`、`:104` `glGetIntegerv(VIEWPORT)`、`:105` `glIsEnabled(SCISSOR_TEST)`、`:109/:138` `glGetError`、`:450-455` 又一组查询、`:513/:515/:516` 每个 Kawase pass 一组查询、`:548-551` 每 pass 末尾 FBO/viewport 恢复（GL + `GlStateManager` 双写）。`glGet*` / `glGetError` 是管线同步点，纯 CPU 成本。

---

## 2. 有界探针（产物全部在 `build/p0c-gl-sync-20261106/`，不污染 `docs/`）

### 2.1 探针形态与「是否需要字节恢复」

| 探针 | 文件 | 做什么 | 是否临时改生产代码 |
| --- | --- | --- | --- |
| 静态有界计数 | `count_gl_sync.py` + `probe-static-before-*.txt/json`、`probe-static-after-*.txt/json` | 正则解析源码里的每个 `glGet*` / `glGetError` / `glBindFramebuffer` / `GlStateManager._*` / `glViewport` / `glEnable(Disable)` 调用点（带 `file:line`），乘显式声明的每帧调用链模型（A 档 / capacityMode 稳态 / `full=true` / 无重建；`kawasePass=5`、`extendEdge=6`、`drainGlErrors=2`），输出每帧次数并按 **capture/pre-pass 路径** 与 **合成/绘制路径** 归类 | **否**，只读源码 |
| 离屏 hidden-GL | `P0cGlSyncProbe.java` + `run_p0c_probe.py` + `probe-results.txt` | 纯 LWJGL+GLFW，加载**真实** `atomchat_kawase_blur` / `atomchat_panel_blur` shader，复刻整条帧序列；真实 GL 计数、AstraProbe `glFinish`+`GL_TIME_ELAPSED` 计时、逐像素 readback、状态镜像 `Mirror`（Blaze3D `GlStateManager` 缓存的替身） | **否**，独立重写逻辑，不编译生产代码 |
| 源码级变异 | `mutate_queries.py` | 仅在 `mutation/Mutated.java` **临时副本**上把查询装回 | **否**，副本在工作区外 |

> **结论**：本轮探针**从未临时修改任何生产代码**，因此不存在「验证后按字节恢复」的动作。三端渲染器的改动是**最终交付的产品改动**，不是探针插桩。为可核对，仍保存了三端改动前**逐字节副本** `baseline/*.PanelBlurRenderer.java` 与完整 `source-diff.txt`（三端各 71 行增删、完全一致），并核验改动后源码里 `P0cGlSyncProbe` / `count_gl_sync` / `System.out` / `PROBE_` 残留计数**全为 0**。

### 2.2 探针前（削减前，离屏/受控静态）

口径：`A档/capacityMode稳态/full=true/无重建`。**三端完全一致**。

| 调用类型 | 每帧次数 | 主要分布 |
| --- | --- | --- |
| `glGetInteger(FRAMEBUFFER_BINDING)` | **7** | 入口 1 + refreshBlur 1 + kawasePass×5 |
| `glGetIntegerv(VIEWPORT)` | **7** | 入口 1 + refreshBlur 1 + kawasePass×5 |
| `glIsEnabled` | **8** | scissor 2（入口+refreshBlur）+ blend 6（kawasePass×5 + drawRoundedQuad） |
| `glGetError` | **2** | `drainGlErrors` 入口+出口（门控前上界） |
| **QUERY 合计** | **24** | |
| `glBindFramebuffer` | 26 | blit / extendEdge / 每 pass 绑定+恢复 |
| `GlStateManager._*`（镜像双写） | 19 | kawasePass×5 + refreshBlur + finally |
| 全部状态类调用 | **99** | 其中 `capture/pre-pass` 85、`composite/draw(restore)` 8、`composite/draw` 1、`boundary(capture)` 3、`boundary(error-check)` 2、`resource(rebuild-only)` **0** |

### 2.3 探针后（削减后，离屏/受控静态）—— 与离屏 GL 探针精确吻合

| 调用类型 | 每帧次数（上界 / debug=off 稳态实际） |
| --- | --- |
| `glGetInteger(FRAMEBUFFER_BINDING)` | **1 / 1**（仅外部边界；`makeTexture` 稳态 0） |
| `glGetIntegerv(VIEWPORT)` | **1 / 1** |
| `glIsEnabled` | **7 / 7**（scissor 1 + blend 6，blend 链保留） |
| `glGetError` | **2 / 0**（门控：debug=off 稳态 0，1/64 抽帧帧 2） |
| **QUERY 合计** | **11 / 9** |
| `glBindFramebuffer` | 21 |
| `GlStateManager._*` | 9 |
| 全部状态类调用 | **66** |

**离屏 GL 探针实测 OPTIMIZED `QUERY=9 (getFbo=1 getVp=1 isEnabled=7 getError=0) BIND=21`，与静态探针逐项一致**；BASELINE `QUERY=24 (7/7/8/2) BIND=26`。

### 2.4 离屏计时（AstraProbe 方法：`glFinish` 包裹 + `GL_TIME_ELAPSED` query，全部离屏/受控）

| 序列 | wall_p50 | wall_p95 | gpu_p50 | gpu_p95 |
| --- | --- | --- | --- | --- |
| `BASELINE_full_sequence` | 0.2862 ms | 0.5584 | 0.2240 ms | 0.4534 |
| `OPTIMIZED_full_sequence` | **0.1699 ms** | **0.3729** | 0.1313 ms | 0.2822 |
| `BASELINE_query_roundtrips`（纯状态往返） | 0.0664 ms | 0.1494 | **0.0061 ms** | 0.0062 |
| `OPTIMIZED_query_roundtrips` | **0.0289 ms** | **0.0632** | **0.0012 ms** | 0.0013 |

**口径声明（重要，避免误读）**：
- 决定性证据是 `query_roundtrips`：GPU 时间仅 **0.001–0.006 ms（几乎为零）**，而 wall 0.029–0.066 ms → 成本是 **CPU 侧提交/同步**，与 §1 真机结论方向一致。
- `full_sequence` 的 `gpu_*` 数值**不是 shader 执行成本**：本离屏 `GL_TIME_ELAPSED` query 区间**覆盖了 `glGet*` 查询本身**，driver 为返回状态插入的同步/气泡被计入 GPU timeline，故随查询削减而下降。这与游戏内 P1 `gpu_panel` 的 query 区间（主代理已判定未覆盖 pre-pass、绝对值不可信）**口径不同，不可混用、不可相减**，也不据此宣称任何 GPU 收益。
- 离屏数值的量级（0.1 ms 级）与真机 A−B 0.239 ms **不可直接横比**（环境、队列深度、216 mod、帧率锁均不同），**不代表 FPS 收益**。

### 2.5 每个查询是否在每帧热路径上的判定与处置

| 改前 `file:line` | 调用 | 每帧 | 路径 | 热路径 | 处置 | 理由 |
| --- | --- | --- | --- | --- | --- | --- |
| `:102` | `glGetInteger(FRAMEBUFFER_BINDING)` | 1 | 边界 capture | **是** | **保留** | 唯一外部状态边界；外部 mod 可能在 pre-pass 前改 FBO，每帧读真实到达状态 |
| `:104` | `glGetIntegerv(VIEWPORT)` | 1 | 边界 capture | **是** | **保留** | 同上（viewport） |
| `:105` | `glIsEnabled(SCISSOR_TEST)` | 1 | 边界 capture | **是** | **保留** | 同上（scissor） |
| `:109/:138` | `glGetError`（`drainGlErrors`×2） | 2 | 错误检测 | **是** | **门控（降级）** | 见 §3.3 降级策略；debug=on 无损，debug=off 抽帧 |
| `:450` | `refreshBlur` `glGetInteger(FBO)` | 1 | capture/pre-pass | **是** | **削除** | 与入口值逐帧等价，复用（§3.2） |
| `:452` | `refreshBlur` `glGetIntegerv(VIEWPORT)` | 1 | capture/pre-pass | **是** | **削除** | 同上 |
| `:453` | `refreshBlur` `glIsEnabled(SCISSOR)` | 1 | capture/pre-pass | **是** | **削除** | 复用入口 `restoreScissor` |
| `:513` | `kawasePass` `glGetInteger(FBO)` | 5 | capture/pre-pass | **是** | **削除** | 保存的是我们自己上一步写入的值，非外部状态（§3.2） |
| `:515` | `kawasePass` `glGetIntegerv(VIEWPORT)` | 5 | capture/pre-pass | **是** | **削除** | 同上 |
| `:516` | `kawasePass` `glIsEnabled(GL_BLEND)` | 5 | capture/pre-pass | **是** | **保留** | blend 状态传递链；删除会让最后一个 pass 的 disable 泄漏到后续 MC 帧，风险高于收益 |
| `:278` | `drawRoundedQuad` `glIsEnabled(GL_BLEND)` | 1 | 合成/绘制 | **是** | **保留** | 外部 blend 位的最终消费者与恢复点 |
| `:403`（改后 `:429`） | `makeTexture` `glGetInteger(FBO)` | **0 稳态** | 资源创建 | **否** | **不建议改，停手** | 探针证明稳态 0 次/帧（仅重建帧 3 次）；且它是 `makeTexture` 内部改了 FBO 绑定后的正确恢复依据，保留 |
| `:548-551` | `kawasePass` 末尾 FBO/viewport 恢复（GL+镜像） | 20 | capture/pre-pass | **是** | **削除（合并往返，非删双写）** | 每个后续操作自设绑定，尾部统一恢复；见 §3.2 |
| `:145-159` | `render` finally 双写 | 8 | 合成/绘制(恢复) | **是** | **保留** | 状态恢复语义红线，异常路径兜底 |
| `:484-497` | `refreshBlur` 末尾双写 | 8 | capture/pre-pass | **是** | **保留** | `drawRoundedQuad` 依赖它先恢复到主 framebuffer/viewport |

> 「若某处查询经探针证明根本不在每帧热路径，如实写『不建议改』并停手」：本轮命中 `:403` `makeTexture` 的 `glGetInteger`（稳态 0 次/帧），**未改**，理由如上。

---

## 3. 改动与安全论证（三端语义一致）

### 3.1 改动清单（`file:line` 为改动后 fabric 行号；Forge/NeoForge 同源，行号略有偏移）

| 文件 | 行号 | 改动 |
| --- | --- | --- |
| `platforms/1.21.1-fabric/src/main/java/com/atom/chat/render/PanelBlurRenderer.java` | `:107-118` | 新增 `boolean checkErrors = errorCheckDue();`，入口 `drainGlErrors()` 门控 |
| 同上 | `:136-137` | `refreshBlur(...)` 调用增传 `restoreFbo, restoreViewport, restoreScissor` |
| 同上 | `:148-154` | 出口 `drainGlErrors()` + `disable()` 门控（同一 `checkErrors`） |
| 同上 | `:176-188` | 新增 `errorCheckFrame` + `errorCheckDue()`（debug 门控 + 1/64 抽帧） |
| 同上 | `:469-470` | `refreshBlur` 签名增 3 个参数 |
| 同上 | `:477-486` | refreshBlur 入口删 3 个 `glGet*`，改用 `restoreFbo/restoreViewport/restoreScissor` 复用值（变量名 `oldFbo/viewport/scissor` 保留，**末尾恢复代码一字未改**） |
| 同上 | `:544-551` | `kawasePass` 入口删 `glGetInteger(FBO)` + `glGetIntegerv(VIEWPORT)`，保留 `glIsEnabled(GL_BLEND)` |
| 同上 | `:580-582` | `kawasePass` 末尾删 4 行 FBO/viewport 恢复（GL + 镜像），保留 blend 恢复 |
| `platforms/1.20.1-forge/.../PanelBlurRenderer.java` | 同源 8 hunk | 同上（仅 `refreshBlur` 调用为 `fb.frameBufferId`） |
| `platforms/1.21.1-neoforge/.../PanelBlurRenderer.java` | 同源 8 hunk | 同上 |

三端由 `build/p0c-gl-sync-20261106/apply_p0c_patches.py` **同一套 anchor** 字节级应用（自适应各端 CRLF，8 个 anchor 每端各命中 1 次，预检失败即整体 ABORT）。`verify_sync.py` 逐行比对三端 9 行 P0-C 注释/门控代码**完全一致**。

**未改动**：`boolean full = true;`（`:495`）、`capacityMode`/`nextPow2`/`extendEdge`（P0-A）、blit 子区域、采样 UV 延展、圆角/半径/alpha、`GlStateManager` 双写语义、shader、`versions/atomlib.json` 工作区、`docs/` 已有报告、实例目录、`build/*/source/**` 历史快照、Truth3 三档（`Truth3Probe` / `diagnosticRenderMode` / F6 热键 / `frame_probe`）、P1 GPU 计时。

### 3.2 安全论证：外部 mod 在我们之前/之间改了 FBO / viewport / scissor 时不会画错

1. **外部状态边界原样保留，且是唯一边界**。`render()` 入口 `:102/:104/:105` **每帧**用真实 `glGet*` 读取「本帧到达时的」FBO / viewport / scissor，供 finally `:156-173` 恢复。216 个 mod 的渲染 hook 完全可能在 pre-pass 之前把绑定切到别的 FBO；因为**不缓存、不假设、每帧重读**，无论外部改成什么值，`restoreFbo/restoreViewport/restoreScissor` 都是真实到达值，finally 与 refreshBlur 尾部都恢复到它 → **不会画错背景、不会污染后续 MC 帧**。这是「禁止简单删除入口查询」的落点。

2. **refreshBlur 入口 3 查询与入口捕获逐帧等价**。render 入口 → refreshBlur 入口之间只有 `ensureTextures()` 和纯 Java 计算：
   - 稳态 `ensureTextures` 直接 return（`need=false`），零 GL 调用；
   - 重建帧 `allocate`→`makeTexture` 内部 `:438` 会临时绑定新 FBO，但其 finally `:442-443` 用 `glBindFramebuffer` + `GlStateManager._glBindFramebuffer` **双写恢复到 `previousFbo`**，且不改 viewport / scissor。
   故 refreshBlur 入口能读到的 FBO / viewport / scissor **恒等于** render 入口捕获值（含重建帧）。复用入口值 `oldFbo=restoreFbo` 等与逐帧重新查询**逐帧等价**，末尾恢复代码一字未动。

3. **kawasePass 内部查询/恢复是「我们自己写入的值」，不是外部状态**。每个 pass 自己 `glBindFramebuffer(GL_FRAMEBUFFER, dstFbo)` + `glViewport(0,0,vpW,vpH)`；其后的 `extendEdge`（READ/DRAW blit）、下一个 pass、`drawRoundedQuad` 之前的 refreshBlur 尾部恢复都**各自设置所需绑定**。保存/恢复的 `oldFbo/viewport` 只是上一步自己写的内部值，最终都被 refreshBlur 尾部 + render finally 覆盖。删除后净效果一致。

4. **`GlStateManager` 双写是必要的缓存镜像，只合并重复往返、不删语义**。现状在 `kawasePass×5` + `refreshBlur` + `render finally` 共 **7 处**做「driver 写 + 缓存写」；本轮把 5 个 pass 的冗余往返合并，只保留 **2 处**（refreshBlur 尾部、render finally）。raw GL 写在 pass 循环期间让 Blaze3D 缓存滞后于 driver，但循环内只有 `RenderSystem.disableBlend/setShaderTexture/setShader` 与 `BufferRenderer.drawWithGlobalProgram`（**不依赖 FBO/viewport 缓存**），且在 `drawRoundedQuad` 之前 refreshBlur 尾部的 `_glBindFramebuffer` / `_viewport` 已把 GL 与缓存对齐到外部状态。离屏探针的 `Mirror.verify()` 用**真实 `glGet*`** 校验缓存与 driver 一致，全部场景通过（§4）。

5. **blend 链保留**。6 次 `glIsEnabled(GL_BLEND)`（5 pass + `drawRoundedQuad`）承担「外部 blend 位逐消费者原样传递」；`drawRoundedQuad` 的注释也记录了历史上「无条件 disable 导致帧间不一致」的 bug。删除会让最后一个 pass 的 disable 泄漏到后续 MC 帧，风险不对等，本轮保留。

6. **pre-pass 执行「期间」无外部 hook 点**。MC 的 GUI 渲染在单线程上同步进行，`render()` 是 `drawPanel` 回调内的一次连续调用，中间不会插入第三方 mod 的渲染回调；外部能改状态的边界只有「调用之前」（§3.2 第 1 点覆盖）与「调用之后」（finally 已恢复，后续帧从干净状态开始）。离屏反证把「之前」的 FBO / viewport / scissor / 四者同时改都跑过（§4）。

### 3.3 `glGetError` 降级策略（错误检测能力不静默丢失）

- `debug=on`：**每帧**执行「入口 drain 清外部错误 → 出口 drain 检测自身错误 → 有错 `disable()` 永久断路」全流程，诊断能力**零损失**。
- `debug=off`（产品默认）：`errorCheckDue()` 用 `(errorCheckFrame++ & 63) == 0` 每 **64 帧抽 1 帧**（2 的幂位与，无除法、无分配）；入口/出口两个 drain **用同一 `checkErrors` 成对门控**，保证「清外部错误 → 检测自身错误」语义不被拆散。稳态帧 **0 次 `glGetError`**。
- **为何不算丢失**：driver 级 GL 错误具有**持续性**（同一 shader / FBO / 纹理配置每帧复现），抽帧帧仍会读到错误并触发 `disable()`（一次捕获即永久关闭模糊、面板回退实色背景）。最迟 64 帧内生效，不存在「漏掉后永远不报」。
- **残余风险（如实披露）**：**一次性、非持续**的 GL 错误若恰落在未抽帧的帧上，可能漏检，该帧会画 tinted panel 且不触发 `disable()`。判为可接受的降级；`debug=on` 可完全覆盖。此降级在代码注释与本报告显式说明，**不是静默移除**。

---

## 4. 视觉一致性（离屏/受控，非真机）

`P0cGlSyncProbe` 复刻「削减前 BASELINE / 削减后 OPTIMIZED」两条帧序列，**绘制命令完全相同**（同样 blit、extendEdge、5 个 Kawase pass、圆角合成；真实 shader），只差查询与冗余状态往返。逐像素 readback 比对：

| 场景 | 覆盖 | 结果 |
| --- | --- | --- |
| golden frame 开合动画中间帧 | `p=0/0.15/0.30/0.50/0.70/0.85/1.0`（ZOOM 0.94→1.0，w 451→480、alpha 0.15→1.0，含圆角边缘与半透明） | **7/7 全 `diff=0 max=0`** |
| golden frame 裁切（面板底部出屏，exact 路径） | 同上 7 个 p，`cropH` 404→430 | **7/7 全 `diff=0 max=0`** |
| 面板关闭后 MC 主场景（后续帧不污染） | `p=0/0.5/1.0`，序列后用**恢复后的状态**画一帧 vanilla clear+scissor | **3/3 `diff=0 max=0`** |
| 反证：外部 mod 改状态 | FBO / viewport / scissor / 四者同时（含 blend） | **4/4 `diff=0`**，`mirrorBASE=true mirrorOPT=true finalFboOK=true` |
| 反证：资源重建 | ping-pong 纹理重建 512×1024 → 1024×2048 | `diff=0`、`mirrorOK=true`、不崩 |
| 反证：真实 GL context 丢失 | `glfwDestroyWindow`+`glfwTerminate`+`initGl()` 重建真 shader / 全部 FBO | `diff=0`、`mirrorOK=true`、`steady2frames_wall=4ms`、`noLongFrame=true`、不崩 |

> 覆盖口径说明：本离屏 A/B 是「**削减前 vs 削减后**的完整 pre-pass 逻辑」，与 Truth3 的「A 完整 / B 无 pre-pass / C 无面板」三档旁路**不是同一概念**，不要混用数值。

---

## 5. 红绿

| 门 | 结果 |
| --- | --- |
| **源码级变异红灯**（`mutate_queries.py`，仅 `mutation/Mutated.java` 副本） | 把 5 组每帧查询装回 → 静态探针 `QUERY/帧 上界=24 > 12` → 断言**失败（RED，`RED_RC=0` 表示红灯成立）**；削减后源码 `QUERY=11（稳态 9）<= 12` → 断言**通过（GREEN，`GREEN_RC=0`）**。日志 `mutation-red.log` / `mutation-green.log` |
| **离屏探针变异红灯** | `BASELINE(re-installed queries) QUERY=24 > 12 → mutation RED: true`；`OPTIMIZED QUERY=9 <= 12 → restored GREEN: true` |
| **离屏探针总判定** | `PROBE_PASS: true`（golden/clipped/follow-up/counter-evidence/rebuild/context-loss/red-green 全 true） |
| `python tools/verify_targets.py` | **通过**（6 目标） |
| `python build/p0c-gl-sync-20261106/verify_sync.py` | **通过**：三端 P0-C 逐行一致、削减生效、红线、锁、孪生、资源存在性 |

---

## 6. 三端构建与拆包证明

### 6.1 隔离快照构建（`prepare_snapshot.py`，遵循 HANDOFF §6.2）

- `copytree` 排除 `.git`/`build`/`.gradle`/`.work`/`.workbuddy`/`*.log`/`__pycache__`；**只改快照内** `versions/atomlib.json` → `0.3.1-shadowcache`；工作区锁**一字未动**（仍 `0.1.0-beta`，`git status --short versions/atomlib.json` 为空）。
- NeoForge 已知坑：从 P1 快照预置 `build/atomlib-consumer/moddev/minecraft_assets.properties`，SHA `015d0bc702eff9f9e7bc7d1a514c8c554118407635c5d1c56470d62a38f04560` 校验一致。
- 快照内无 `build/` 泄漏；三端渲染器均含 `errorCheckDue`、`boolean full = true;` ×1、`glGetInteger` 调用点 2。

| 端 | 命令要点（全部 `--offline --no-daemon --console=plain -PatomlibMavenRepo=<maven>`） | 结果 |
| --- | --- | --- |
| Fabric | JDK21 | **BUILD SUCCESSFUL 3m10s**（16 tasks，`:test` 执行） |
| Forge | JDK17 + `-Dnet.minecraftforge.gradle.check.certs=false` | **BUILD SUCCESSFUL 2m58s**（26 tasks） |
| NeoForge | JDK21 + `-x downloadAssets` | **BUILD SUCCESSFUL 1m48s**（16 tasks） |

- `shared/src/test/java/com/atom/chat/notification/NotificationRenderWiringTest.java:46-52`：三端 **7/7、0 failures、0 errors**（`panelBlurRefreshesEveryFrameAndBannersUseCardAndGlobalMotionPrimitives` 断三端 `boolean full = true;` 各 1、禁 `BlurMotionGate`，仍绿）。
- 三端 `TEST-com.atom.chat.notification.NotificationRenderWiringTest.xml` 已核验。日志 `build-{fabric,forge,neoforge}.log`。

### 6.2 拆包证明（`verify_candidates.py`，`zipfile` 常量池 + `javap -c` 字节码）

三个候选包**全部同时含**：

| 批次 | 证据（class → 常量池 token / 字节码） |
| --- | --- |
| **本轮 P0-C** | `PanelBlurRenderer.class` → `errorCheckDue` / `AtomChatConfig` / `restoreViewport` / `restoreScissor` / `checkErrors`；`javap -c` 数出 GL 查询调用点 `glGetInteger=2, glGetIntegerv=1, glIsEnabled=3, glGetError=1` |
| **改动前基线对比**（truth3 三档包） | 同法 `4 / 3 / 4 / 1` → **前后 `4→2, 3→1, 4→3, 1→1`**（`glGetError` 源码点不变，运行时门控） |
| **P0-A** | `PanelBlurRenderer.class` → `capacityMode` / `nextPow2` / `extendEdge` |
| **P1** | `GlGpuTimer.class` / `GpuTimerQuery.class` 存在；`FrameProfile.class` → `page_phase` / `PAGE_RENDER` / `firstOpen` |
| **Truth3** | `Truth3Probe.class` → `frame_probe` / `mode_switch` / `REPORT_EVERY`；`AtomChatConfig.class` → `diagnosticRenderMode` |

> 如实说明：`pageKey` 是 P1 源码注释术语，编译后常量池无此字面量，以 `FrameProfile` 的 `page_phase` 等实际字段证明在包内（沿用 truth3 口径）。本轮候选包**完整保留 Truth3 三档功能**，未删 `Truth3Probe` / `diagnosticRenderMode` / F6 热键 / `frame_probe`。

### 6.3 候选包与哈希

目录 `build/p0c-gl-sync-20261106/candidates/`（文件名与现役一致，`sha256sum -c sha256.txt` 三端 OK）：

| 文件 | sha256 |
| --- | --- |
| `atomchat-Fabric-1.21.1-v0.5.0.jar` | `f2a1063fff1a7cee054e1ff3650ff926ffc455b03bacd39b486e1d4b9a9dd67a` |
| `atomchat-Forge-1.20.1-v0.5.0.jar` | `d7ae35c5e3c6bb4f880ef86d7ed016d3efd2e642a322c49845683424b226c2cc` |
| `atomchat-NeoForge-1.21.1-v0.5.0.jar` | `2d17fb5ee292367df9fa577fc5029e5425720a8a6bd9b690d11d249973b05d04` |

---

## 7. 内置 A/B 对照判据（由主代理使用，本子代理不部署、不安排验收）

当前实例部署的是三档实验包；本轮构建的新包**完整保留 Truth3 三档**（`Truth3Probe` / `diagnosticRenderMode` / F6 热键 / `frame_probe`）。真机验收时：

- **修复有效 ⇒ A 档 `frame_probe` p50 向 B 档靠拢，而 B 档基本不变**。B 本就跳过 pre-pass，是天然对照组；P0-C 只作用在 pre-pass 的 CPU 同步查询上，对 B 无对象可作用，故 B 应稳定。
- 预期方向：A−B 从 **0.239 ms** 收窄；B−C 的 0.184 ms 不变；C 仍撞 400 帧锁（上限非天花板）。
- 反例：若 A 不变或 B 也变，则说明本轮改动未命中真机热路径或引入了额外每帧成本，需回查。
- 判定权归用户/主代理；本报告**不宣布达标、不宣称任何 FPS 收益**。

---

## 8. 遗留风险

1. **未真机验证**：全部帧间隔 / FPS 结论待主代理按 §7 三档对照验收；离屏/受控数值不等于游戏内收益。
2. **`glGetError` 抽帧降级**：一次性非持续错误可能漏检（§3.3）；`debug=on` 无损。
3. **离屏计时口径**：`GL_TIME_ELAPSED` 区间覆盖了查询本身，`gpu_*` 差异含 driver 同步气泡，与游戏内 P1 `gpu_panel`（绝对值本就不可信）口径不同。
4. **`GlStateManager` 缓存滞后窗口**：pass 循环内缓存滞后于 driver，已由 `Mirror.verify()`（真实 `glGet*`）与三端 `:test` 覆盖；但离屏 `Mirror` 只模拟 FBO/viewport/scissor/blend 四个字段，真实 Blaze3D 缓存字段更多，需真机确认。
5. **外部 mod 在 pre-pass 执行期间穿插改状态**：MC GUI 渲染单线程、无 hook 点（§3.2 第 6 点论证）；离屏反证覆盖的是「之前」与「之后」。
6. **`errorCheckDue()` 每帧读一次 `AtomChatConfig.get().debug`**：与 Truth3 每帧读 config 同量级、无新增分配，但仍是每帧一次 Java 调用（非 GL 同步）。
7. C 档 400 帧锁、`gpu_panel` 绝对值不可信、4096 帧滚窗不可做档位差等沿用主代理 §1/§3 结论。
8. 三端 `PanelBlurRenderer.java` 本就是未提交 WIP；本轮仅在其上叠加 8 个同源 hunk，`baseline/` 副本与 `source-diff.txt` 可完整还原差异。

---

## 9. 本轮未做的事（红线，如实披露）

**未部署、未启动游戏、未真机验收、未 commit / push / 发布**；未替换实例 JAR、未改实例配置/启动器、未改 `D:/Myworld/.minecraft/versions/*` 下任何文件；**未安排用户验收**。
未改 `docs/` 已有报告（仅**新增**本报告）、未改 shader、未改 `versions/atomlib.json` 工作区、未动 `build/*/source/**` 历史快照、未恢复 `BlurMotionGate`、未改 `boolean full = true;`、未做静止降频 / 开合冻结 / 半分辨率 / 借旧模糊档等任何产品侧改变、未改 P0-A 容量复用语义与 P1 GPU 计时实现。
未使用 `git checkout` / `reset` / `stash` / `clean` 或任何覆盖式回退；两仓大量未提交 WIP（AC ≈90 项 / Lib ≈40 项）原样保留。
除**三份 `PanelBlurRenderer.java`** 与 `build/p0c-gl-sync-20261106/` 下新增探针/脚本/报告产物外，不碰任何其它文件。一次一个因素，收益不相加。

---

## 附：本轮产物（`build/p0c-gl-sync-20261106/`）

| 文件 | 内容 |
| --- | --- |
| `candidates/*.jar` + `sha256.txt` | 三端候选包与哈希清单 |
| `baseline/*.PanelBlurRenderer.java` | 三端改动前逐字节副本 |
| `source-diff.txt` | 三端完整 unified diff（各 71 行增删、一致） |
| `count_gl_sync.py` + `probe-static-before-*.txt/json` + `probe-static-after-*.txt/json` | 静态有界计数探针（前/后，三端） |
| `P0cGlSyncProbe.java` + `run_p0c_probe.py` + `probe-classes/` + `probe-results.txt` + `probe-run.log` | 离屏 hidden-GL 探针（计时/golden/反证/红绿） |
| `apply_p0c_patches.py` | 三端同源字节级补丁（8 anchor、行尾自适应、预检） |
| `verify_sync.py` | 三端同步 / 削减生效 / 红线 / 锁 / 孪生 校验 |
| `mutate_queries.py` + `mutation-red.log` / `mutation-green.log` + `mutation/` | 源码级变异红灯（仅副本） |
| `prepare_snapshot.py` + `snapshot/` | 三端隔离源码快照（锁 0.3.1-shadowcache、NeoForge 资产坑处理） |
| `collect_candidates.py` + `verify_candidates.py` | 候选包收集 + 拆包证明（含改动前基线 javap 对比） |
| `build-{fabric,forge,neoforge}.log` | 三端构建日志 |
| `docs/verification/2026-11-06-p0c-gl-sync-elimination.md` | 本报告 |
