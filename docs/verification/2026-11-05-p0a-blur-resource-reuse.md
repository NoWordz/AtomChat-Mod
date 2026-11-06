# P0-A：消除开合动画期间模糊目标纹理反复重建

**日期**：2026-11-05
**性质**：唯一执行子代理交付，范围严格限定为 P0-A（消除尺寸抖动导致的 3× texture/FBO 重建）。
**依据**：`docs/verification/2026-11-05-astra-full-performance-audit.md` §6.1 / §7 F1 / §8 P0-A、`docs/HANDOFF-panel-performance-codex.md` §6.2 / :190。
**所有数值均为离屏 / 受控场景（RTX 5060 Laptop、隐藏 GL 3.3 core、真实 shader、合成背景），不等于游戏 FPS 收益。一次只改一个因素，收益不相加。**

---

## 0. 结论（一句话）

开合动画（ZOOM `openScale` 0.94→1.0）期间 `ensureTextures` 每帧因 `w≠texW || h≠texH` 触发 `destroyTextures()` + 3× `makeTexture()`。本轮把「纹理分配容量」与「每帧实际采样子区域」解耦，用真实 shader 的离屏 A/B 证明 **重建次数 57→0、逐像素 0 差异（含动画中间帧、圆角边缘、半透明、以及面板底部被裁剪的边界）**，变异红灯成立，三端构建与 `verify_targets.py` / `NotificationRenderWiringTest` 全绿。

**未部署、未真机验收、未 commit。**

---

## 1. 根因（采信主报告，行号已核对）

- `AtomChatScreen.java:1988-1995`：`PanelOpenMotion.blurCapture(panelProgress, …)` 产出的 capture 宽高随动画逐帧变化，传给 `PanelBlurRenderer.render`。
- `PanelBlurRenderer.java`（Fabric :106 / Forge :123 / NeoForge :123）调 `ensureTextures(pw, ph)`；原实现（Fabric :279-300 / Forge :253-274 / NeoForge :252-273）在 `w==texW && h==texH` 才 return，否则 `destroyTextures()` + 3× `makeTexture()`（`glTexImage2D` + FBO 挂载 + `glGetInteger(GL_FRAMEBUFFER_BINDING)` + Blaze3d 状态镜像）+ 一次 `FrameSegments.count(BLUR_RESOURCE)`。
- stage6 日志 `build/deep-performance-20261105/2026-11-05-6.log`：首窗 `BLUR_RESOURCE=7`、稳定窗 `0`、第二次开合窗 `21`（计数单位=一次三目标重建）。
- 受控计时 `build/astra-performance-20261105/probe-results.txt`：`resize3_480x860 wall_p50=1.5767 gpu_p50=0.4528ms`、`resize3_960x1720 wall_p50=2.1282 gpu_p50=0.6487ms`；对比 `blur5_480x860 wall_p50=0.1126 gpu_p50=0.0620ms`、`blur5_960x1720 wall_p50=0.2677 gpu_p50=0.2156ms`。**一次重建 ≈ 5 pass 模糊的 7–14 倍墙钟、3–7 倍 GPU。**

---

## 2. 改动文件（仅三份现役正本 + 新增探针/脚本/报告）

三份 `PanelBlurRenderer.java` 语义一致，仅各端 API 名不同（Fabric 用 Yarn `ShaderProgram`/`GlUniform`/`Tessellator.begin`；Forge/NeoForge 用 `ShaderInstance`/`safeGetUniform`/`BufferUploader`；Forge `getBuilder().begin`、NeoForge `begin`+`buildOrThrow`）。

| 文件 | 改动点（行号为改后） |
| --- | --- |
| `platforms/1.21.1-fabric/src/main/java/com/atom/chat/render/PanelBlurRenderer.java` | 容量/逐帧参数字段 :48-53；`render()` 算 `hCrop`/`clipped` 并设 `uvX/uvY/vpW/vpH/tmx/tmy` :115-126；圆角 quad UV 归一 :288-293；`ensureTextures(w,h,clipped)` :324-354；`nextPow2` :356-362；`allocate` :364-374；`extendEdge` :383-394；`refreshBlur` 延展+新 kawasePass 调用 :464-482；`kawasePass` 新签名/缩放 quad :501-538 |
| `platforms/1.20.1-forge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java` | 同上语义：字段 :47；`render` :132-141；quad UV :261；`ensureTextures` :298-328；`nextPow2` :330；`allocate` :338；`extendEdge` :357-369；`refreshBlur` :438-452；`kawasePass` :480-500 区间 |
| `platforms/1.21.1-neoforge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java` | 同上语义：字段 :47；`render` :132-141；quad UV :261；`ensureTextures` :297-327；`nextPow2` :329；`allocate` :337；`extendEdge` :356-368；`refreshBlur` :437-451；`kawasePass` :479-499 区间 |

**未改**：任何 `.fsh`/`.vsh`/`.json` shader；`versions/atomlib.json`（工作区）；`build/*/source/**` 历史快照；`docs/` 里已有报告；`layers/mapping/official/`（无孪生副本，已确认）。

### 设计要点（为什么能不改视觉）
1. **容量纹理**：`ensureTextures` 按 2 的幂一次性分配 `capW×capH`（480×860 → 512×1024），动画期尺寸抖动只要 `w≤capW && h≤capH` 就**不重建**；仅在容量不足或首次时扩容。
2. **1:1 子区域采样**：每帧仍把 capture `glBlitFramebuffer` 到 `(0,0)-(w,h)`；`kawasePass` 的 viewport 设为容量尺寸，quad 顶点缩放到 `texCoord∈[0,w/capW]×[0,h/capH]`（`atomchat_kawase_blur.vsh` 的 `texCoord = Position.xy*0.5+0.5` 与 `gl_Position` 同源，缩放顶点即缩放 UV），保持「dst 像素 ↔ src 纹素 1:1」。`textureSize` 变为容量后 `texel=1/cap`，`offset=texel*spacing` 仍等于 `spacing` 个纹素 = `spacing` 个 capture 像素，**模糊半径不变**。
3. **CLAMP_TO_EDGE 等价**：容量纹理的内容区外必须精确等于「该 pass 的边缘像素」，否则 Kawase 的 `±offset` 采样会读到未初始化区。`extendEdge` 用 `GL_NEAREST` 把内容右列/底行复制到容量边缘，并**在每个 pass 之后重做**（每个 pass 有自己的边缘像素）。离屏证明：只做一次（R2）残留 max 36/255，每 pass 做（R3）全 0。
4. **圆角 quad UV 归一**：`texture(u*uvX, 1-(1-v)*uvY)`，配合 `u_FlipV=1.0` 保持不变，采样恰好落在内容子区域；`uvX=uvY=1` 时逐字退化为原 quad。`u_Rect`/`u_Radius`/`alpha`/裁剪/`fwidth` 圆角 AA 全部不动。
5. **裁剪回退**：`refreshBlur` 原有 `h = min(y+h, fbHeight)-y`（面板底部超出屏幕下沿）会留下「未写底带」并经 CLAMP 采样。该边界的既有行为与容量路径不同，因此 `clipped` 时回退到**精确尺寸路径**（原 viewport、原 UV、无延展），视觉逐字节不变；该情形尺寸稳定，不会重新引入逐帧重建。
6. **红线保持**：`boolean full = true;` 原样（三端各 1 处），未出现 `BlurMotionGate`；`FrameSegments.Counter.BLUR_RESOURCE` 计数保留（仅在分配/扩容时 +1）；capture 坐标语义、采样位置、半径、alpha 不变。

---

## 3. 三行有界探针（修复前后重建次数）

探针为**独立离屏程序**，不污染生产代码，故无需"按字节恢复生产代码"。三行位于 `build/p0a-blur-20261105/P0aBlurProbe.java` 的 `Res.ensure()`（注释 `three-line bounded probe`）：

```java
generation++;                                                   // old->new / generation
probe.add(String.format(..., "gen=%d old=%dx%d new=%dx%d tex=%dx%d wall=%.4fms", ...));
totalAllocWallMs += wallMs;                                      // 分配墙钟
```

输出（`build/p0a-blur-20261105/probe-results.txt`，61 帧 ZOOM 0.94→1.0）：

| 策略 | allocations | rebuilds（尺寸变化触发） | 总分配墙钟 | 探针事件 |
| --- | ---: | ---: | ---: | --- |
| BASELINE（修复前） | 58 | **57** | 101.3542 ms | 58 |
| PROD（修复后） | 1 | **0** | 1.4459 ms | 1 |
| MUTATION（装回"尺寸变化即重建"） | 58 | **57** | 98.6233 ms | 58 |

前 4 条样本：
```
BASELINE gen=1 old=0x0 new=451x808 tex=451x808 wall=2.8554ms
         gen=2 old=451x808 new=452x809 tex=452x809 wall=1.6768ms
         gen=3 old=452x809 new=452x810 tex=452x810 wall=1.6268ms
         gen=4 old=452x810 new=453x811 tex=453x811 wall=1.4853ms
PROD     gen=1 old=0x0 new=451x808 tex=512x1024 wall=1.4459ms
```

**修复前 57 次重建 → 修复后 0 次**（首次 1 次分配为必要开销，对应验收「BLUR_RESOURCE 21 → ≤1」）。

---

## 4. 离屏 A/B 与 golden frame（真实 shader、隐藏 GL）

程序 `P0aBlurProbe.java` 复用 `AstraProbe.java` 思路，纯 LWJGL + GLFW 隐藏窗口，加载**真实** `atomchat_kawase_blur.{vsh,fsh}`、`atomchat_panel_blur.{vsh,fsh}`，复刻 `refreshBlur` + `drawRoundedQuad` 的完整 GL 链路（含 `glY0=fbHeight-(y+h)` 翻转、5 pass ping-pong、`u_Rect/u_Radius/u_FlipV=1`、blend、圆角 AA、半透明 alpha），逐像素 `glReadPixels` 对比。**分层诊断**：blur 纹理内容子区域（Kawase 阶段）与最终圆角合成（panel-quad 阶段）。

### 4.1 golden frame，无裁剪（7 帧：p=0/.15/.30/.50/.70/.85/1.0，覆盖动画中间帧、圆角边缘、半透明 alpha 0.15→1.0）

| 策略 | quad 差异 | blur 差异 | 判定 |
| --- | --- | --- | --- |
| R1（容量+子区域，**无延展**） | 79623–98279 通道、max 20–123 | 92875–99097、max 160–164 | **红**（CLAMP 读到未初始化区） |
| R2（容量+整张 Kawase+**一次性延展**） | 5134–26442、max 1–13 | 18939–32417、max 5–36 | **红**（延展未随 pass 更新） |
| R3（容量+子区域+**每 pass 延展**） | **0 / max 0**（全 7 帧） | **0 / max 0**（全 7 帧） | **绿** |
| PROD（交付逻辑，含裁剪回退） | **0 / max 0**（全 7 帧） | **0 / max 0**（全 7 帧） | **绿** |

### 4.2 golden frame，裁剪边界（面板底部出屏，cropH≈h/2，7 帧）

| 策略 | quad 差异 | blur 差异 | 判定 |
| --- | --- | --- | --- |
| PROD（回退精确路径） | **0 / max 0** | **0 / max 0** | **绿** |

### 4.3 变异红灯 / 恢复绿灯

```
REUSE rebuilds==0 (GREEN)  : true
MUTATION rebuilds>0 (RED)   : true     （把"尺寸变化即重建"装回 → 57 次，红）
R1 / R2 PIXEL_IDENTICAL     : false    （maxDelta 164 / 36）
R3 / PROD PIXEL_IDENTICAL   : true     （maxDelta 0，含裁剪）
```

---

## 5. 验证门（缺一不可，全部实跑）

| 项 | 结果 |
| --- | --- |
| `python tools/verify_targets.py` | **校验通过**（6 条目标，含映射孪生/资源路径/seams 同步） |
| 三端构建（**隔离源码快照**） | 全 **BUILD SUCCESSFUL** |
| — `1.21.1-fabric` | `build-fabric.log` 3m7s，`:test` `:verifyAtomLibInput` `:build` 全过 |
| — `1.20.1-forge` | `build-forge.log` RC=0 |
| — `1.21.1-neoforge` | 首次在 `prepareNeoForgeTestFiles` 配置期因缺 `moddev/minecraft_assets.properties` 失败（HANDOFF §6.2 已知坑，**发生在编译之前、与代码无关**）；从历史快照补 SHA `015d0bc7…` 后重跑 `build-neoforge2.log` 1m38s **BUILD SUCCESSFUL** |
| 三端产物 | 各端 `build/atomlib-consumer/libs/atomchat-*-v0.5.0.jar` 均生成 |
| `NotificationRenderWiringTest.java:46-52` | 三端 XML 均 `tests="7" skipped="0" failures="0" errors="0"`（fabric/forge/neoforge）；全部测试类 fabric 256 / forge 252 无失败 |
| `boolean full = true;` / 禁 `BlurMotionGate` | 三份渲染器各 1 处 `boolean full = true;`、`BlurMotionGate` 0 处（grep + 测试双重确认） |

**隔离快照纪律**：`build/p0a-blur-20261105/prepare_snapshot.py` 用 `copytree` 排除 `.git`/`build`/`.gradle`/`.work`/`.workbuddy`/`*.log`/`__pycache__`；只在**快照内**把 `versions/atomlib.json` 改为 0.3.1-shadowcache（其 SHA 与 `atomlib-maven-stage5` 三端 jar 实测一致），`-PatomlibMavenRepo` 指向该 maven。**工作区 `versions/atomlib.json` 一字未动**（`git status --short versions/atomlib.json` 为空、仍 0.1.0-beta）。三端 shader `fsh/vsh` 逐字节一致已 diff 确认。

---

## 6. 新增文件清单

```
docs/verification/2026-11-05-p0a-blur-resource-reuse.md   （本报告）
build/p0a-blur-20261105/P0aBlurProbe.java                  （离屏探针：三行探针+A/B+golden frame+变异红灯）
build/p0a-blur-20261105/run_p0a_probe.py                   （编译运行探针）
build/p0a-blur-20261105/probe-results.txt                  （探针原始输出）
build/p0a-blur-20261105/prepare_snapshot.py                （隔离源码快照）
build/p0a-blur-20261105/snapshot/                          （隔离构建快照，含改后三份渲染器）
build/p0a-blur-20261105/build-{fabric,forge,neoforge,neoforge2}.log
```

---

## 7. 遗留风险（如实披露）

1. **全部为离屏/受控数值**，面板尺寸 480×860 量级、合成结构化背景、单机单卡；**不得宣称等于游戏 FPS 收益**。游戏内 GPU 完成时间仍未接生产诊断（主报告 F12），开面板 `panel max` 是否下降需真机验收。
2. **未真机验收**：golden frame 逐像素 0 差异只覆盖离屏；HUD 通知 backdrop 复用同一 `PanelBlurRenderer` 的路径、与其它 mod（Iris/Gnetum 等）的 FBO/状态交互、亮暗背景目视截图均未在实例确认。
3. **每帧新增 GL 操作**：容量模式每帧 1 次初始延展 + 5 次每 pass 延展 = 6 次 `glBlitFramebuffer`，外加 5 pass 的 viewport 改为容量尺寸（但 quad 缩放使实际光栅化仍为 w×h）。这些是消除重建的**必要配套开销**，本轮 A/B 只量了「重建次数 / 分配墙钟 / 像素一致」，**未单独量化修复后每帧总耗时**，收益不相加。
4. **显存**：容量按 2 的幂（512×1024 vs 480×860），3 张 RGBA8 纹理略增；未量化。
5. **裁剪边界**：面板底部超出 framebuffer 时走精确回退路径（离屏 0 差异已证），但离屏窗口/密度/guiScale 组合与真实极端窗口不同，真实极端尺寸未覆盖。
6. **shader 语义耦合**：UV 归一公式依赖 `atomchat_kawase_blur.vsh` 的 `texCoord=Position.xy*0.5+0.5` 与 `atomchat_panel_blur.fsh` 的 `u_FlipV=1` 翻转；三端 shader 现已逐字节一致，但若未来改动这两处语义需重新验证。（本轮未改任何 shader。）
7. **构建链**：NeoForge 首次失败为已知环境缺文件（已补 SHA 015d0bc7 后成功）；工作区锁仍是旧版 0.1.0-beta，正式出包仍需主代理按既定流程处理。
8. `recreated` / `lastBlurTex` 等遗留字段保持原样，未清理（不在 P0-A 范围）。

---

## 8. 边界声明

- **未部署、未真机验收、未 commit / push / 发布**。
- 未启动游戏、未替换实例 JAR、未改任何配置/启动器、未改 `versions/atomlib.json`（工作区）、未改 `docs/` 已有报告、未碰 `build/*/source/**` 历史快照、未碰 `layers/mapping/official/`。
- 未使用 `git checkout` / `reset` / `stash` / `clean` 或任何覆盖式回退；两仓大量未提交 WIP 原样保留（本次仅 `git status` 只读）。
- 除三份 `PanelBlurRenderer.java` 与新增探针/脚本/快照/日志/本报告外，未触碰任何其它文件。
- 判定权归主代理 / 用户；本子代理不安排验收或部署。
