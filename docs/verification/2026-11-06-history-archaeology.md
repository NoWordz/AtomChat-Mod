# 历史考古：AC 里「曾经更好的写法被换掉」的完整账

**写于** 2026-11-06 ｜ **分支** `perf/checkpoint-20261106`（`cebf7ccd`，工作区干净）｜ **状态**：只读分析，未改任何生产代码、未构建、未启动游戏、未 commit。

**范围**：`D:/AtomChat-Main` 全部 268 个提交（含 `--all`），热路径文件 10 个 + AtomLib 侧现役实现对照阅读。
**对照基准**：`D:/AtomChat-032`（worktree @ `34299d29`，0.3.2），未做任何写操作。
**口径**：本文只复用 `2026-11-06-mode-d-results.md` 的分段 p50 与 `2026-11-06-e0e1-results.md` 的 E1 差值；**不使用**已撤回的 `A−B=0.239 占 56%`、`非 AC 1.20ms 环境地板`、`gpu_panel 绝对值`、`关于页最低帧`、`debug 分配列跨页横比`。所有「推断」都标了推断。

---

## 1. 一页结论

**找到了 6 处「换完之后更贵」的地方**：1 处有真机相邻配对（#1），1 处有分段实测但耗时仍是折算（#2），其余 4 处是码上确证、量级未测（#3/#4/#5/#6）。

按**可能受益**从大到小：

| # | 换掉的东西 | 提交 | 为什么排这个位置 |
| --- | --- | --- | --- |
| **1** | **模糊刷新的限频（`BlurMotionGate`）被删，改成每帧 `full = true`** | `a1dff96b` | **唯一一条既被真机配对量到、又有现成可复用实现、又不碰跨 mod 契约的**。A/B 相邻配对均值 0.192ms，且 D 档（30Hz 复用）已证明 `D−B=+0.009ms` ⇒ 把节奏放回去几乎能收回整段 A−B |
| **2** | **`GlStateUtil`（AC 自带，约 260 次原生 GL 调用/帧）→ AtomLib `GlStateScope`（约 820–860 次）** | `dd635d15` | CPU 量级最大（`BRIDGE_ENTER+EXIT = 0.716ms` 的绝大部分），但修法落在 AtomLib 的公开状态恢复契约上，会波及其它消费者，风险高于 #1 |
| **3** | **物品图标的状态保存：18 次查询 → 每帧 2 次全量 `GlStateScope.capture()`** | `a1dff96b` 引入 → `dd635d15` 变 1 次 → `1dad4d48` 变 2 次 | 单次全量 capture 有 2.13ms 的实测（192 单元），**这是潜在大炸弹**，但只在「可见消息里有物品链接」时炸；本轮真机 `ICONS = 0.000ms` 就是因为场景里没有图标 |
| **4** | **HUD 通知横幅：不绘制 → 独立 Skija pass + 第二套 5-pass Kawase** | `a1dff96b` / `6f49262e` | 是**新功能**不是回归；横幅可见的帧要多付一次完整桥 + 一次 blur，且 `!screen` 判定保证它不会和面板叠加 |
| **5** | **列表高度缓存被上游的每帧 `new ArrayList` 打穿** | `2a25c5a3`（缓存）＋ `WorldFeedFilter`（分配） | 缓存本身是好东西，但 key 用**实例身份**，过滤档非 ALL 时每帧都是新 list ⇒ 缓存 0 命中。只影响开了过滤的用户，机制确证、耗时未测 |
| **6** | **DOTS Picture 缓存的 key 含绝对坐标，开合动画期每帧必 miss** | `b7df5657` | 稳态中性（E3 已证），开合动画期是「录制 + 回放」双份命令，属瞬时抖动不是稳态 |

**最值得先动的是 #1，而且不要 `git revert a1dff96b`**（那是 177 文件 / +6622 的巨大提交）。正确做法是**把「刷新节奏」作为一个独立的产品决策重新实现**：树里已经有验证过的实现（`PanelBlurRenderer.renderReuse` + `Truth3Probe.blurRefreshDue` 的 30Hz 策略），把它从 `debug=on && mode==D` 的档位里提出来即可；同时要注意三个源码级断言**明确禁止** `BlurMotionGate` 回归（`ModeDCadenceTest.java:186`、`PrepassProbeTest.java:196`、`FramePumpProbeTest.java:192`），直接抄回旧类名会红。

---

## 2. 主表

> 「真机段」取自 `2026-11-06-mode-d-results.md:99-107` 的 A/D 两档 p50（ms）：`PRE_UI 0.149/0.011`、`DRAW 0.893/0.827`、`DOTS 0.679/0.598`、`LIST 0.301/0.259`、`BRIDGE_ENTER 0.444/0.439`、`BRIDGE_EXIT 0.280/0.277`、`ICONS 0.000/0.000`。`PRE_UI` 就是 `BLUR`（pre-pass）段。

| # | 功能点 | 什么时候、被哪个提交换掉 | 旧写法（文件:行 + 机制） | 现写法（文件:行 + 机制） | 今天还在？ | 真机段 | 方向 | 可信度 | 回滚风险 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | **面板模糊的刷新节奏** | 2026-10-04 `af578252` 引入 → **2026-10-23 `a1dff96b` 删除**（`- boolean full = BlurMotionGate.allowFullRefresh(...)` → `+ boolean full = true`，同时删 `BlurMotionGate.java` 130 行与 94 行测试） | `a1dff96b^:PanelBlurRenderer.java:366-369` + `BlurMotionGate.java:1-130`：动起来每 2 帧一次、静景 ≤1 次/500ms；被拒的 slot 直接复用 `lastBlurTex` | `platforms/1.21.1-fabric/.../PanelBlurRenderer.java:570` `boolean full = true;`，每帧 `glBlitFramebuffer` 采集 + 5 个 Kawase pass（`:578-585`）；三端同一份 | **在**（`git grep "boolean full = true"` 三端各 1 处；`BlurMotionGate` 只剩测试里的禁令） | **BLUR / PRE_UI**（A 0.149 → D 0.011）；passes 段 | **更贵** | 调用数：确证；耗时：**真机配对已量**（A−B 相邻配对 0.233/0.196/0.147，均值 0.192ms；`D−B=+0.009`） | **会**：静景但背景自身在动（水/粒子/实体/天空）时，模糊最多滞后 500ms（30Hz 则 33ms）。开合动画期 gate 会放行，不受影响 |
| 2 | **每帧 Skija 绘制的 GL 状态保存/恢复** | 2026-10-30 `dd635d15`（`SkiaGraphics` 从自持 `DirectContext`+`GlStateUtil` 退化成 AtomLib 门面） | `34299d29:SkiaGraphics.java:122,159` → `GlStateUtil.java:161-323`：26 状态项 + 32 单元 × 2 查询 ≈ **260 次原生 GL 调用/帧** | `SkiaGraphics.java:58` → `SkijaRenderBridge.java:125` `GlStateScope.captureSkia()`：60+ 状态项 + 32 单元 × 3 查询 + 逐 draw buffer（8×8）+ 逐 UBO（72–96×3）≈ **820–860 次**（`GlStateScope.java:135-188` / `:225-276`） | **在** | **BRIDGE**（ENTER 0.444 + EXIT 0.280 = 0.724；D 档 0.716） | **更贵** | 调用数：确证；耗时：推断（E3 §4.2 折算 0.45–0.5ms CPU） | **不会**（视觉不变），但会改 AtomLib 的公开恢复契约，`GlStateScopeTest` 覆盖最高纹理单元与异常恢复 |
| 3 | **物品图标（RichText 里的 `[item]`）的 GL 状态保存** | `a1dff96b` 新增 `ItemIconRenderer`（`NativeState` + 纯字段 `requireContext`）→ `dd635d15` 改成 `withContext`（**1 次**全量 capture）→ `1dad4d48` 拆成 **2 次** `withContext` | `a1dff96b:ItemIconRenderer.java:80` `try (NativeState …)`（同一份 `NativeState` 现在位于 `:290-323`，仅 18 次查询）+ `a1dff96b:SkiaGraphics.java:193-198` `requireContext()` 是纯字段返回，**0 次 GL** | `platforms/1.21.1-fabric/.../ItemIconRenderer.java:90` 与 `:121` 两处 `bridge.withContext(...)` → `SkijaRenderBridge.java:97` `GlStateScope.capture()`（**全量**：`maxCombinedTextureImageUnits`，不是 `captureSkia` 的 32）+ 各一次 `normalizePixelTransfer()`（18 次 `glPixelStorei`） | **在** | **ICONS**（`AtomChatScreen.java:2057` 每帧调用 `prepare`）。本轮真机 0.000ms，因为没有可见物品图标 | **更贵**，且是「每帧 ×2 次全量 capture」 | 代码：确证（`prepare` 的早退条件是 `requests.isEmpty() && CACHE.isEmpty() && RETIRED.isEmpty()`，而 `IconCache.request` 每帧都会重新入队，见 `:258-261`）｜量级：推断（单次全量 capture 的 192 单元实测 p50 为 2.1335ms，见 `atomlib/docs/evidence/gl-scope-sample-20261105-103433.md:20`） | **不会**（视觉不变）；但这是 `withContext` 的公开契约，缩它要同时保证「不得在 Skija pass 内」的断言 |
| 4 | **HUD 通知横幅的绘制** | `a1dff96b` 新增独立 HUD Skija 面（0.3.2 只 `tick()` 不绘制）；`6f49262e` 把 Skia backdrop filter 换成 raw-GL Kawase | 0.3.2：**不绘制**（`git grep "INSTANCE.draw"` 在 `34299d29` 零命中）；`a1dff96b..6f49262e`：横幅在同一个 Skija pass 里用 `ImageFilter` 做 backdrop（1 次 saveLayer + 模糊） | `NotificationHudOverlay.java:47-56`：`GRAPHICS.draw(...)`（= 再一次完整 `drawFrame` → `captureSkia` + `GlStateScope.close()`），内部再调 `PanelBlurRenderer.render(...)`（banner 矩形上再跑 5 个 Kawase pass）；`!screen` 门控保证不与面板叠加（`NotificationHudLayout.java:24`） | **在** | 面板不参与（横幅只在无屏时画）；若横幅出现，相当于多一整套 **BRIDGE + BLUR** | **更贵**，但属新增功能 | 代码：确证；耗时：推断（横幅可见帧多一次桥，量级同 #2） | **等于删功能**。且 `ensureTextures` 的 capacity 只增不减：先开过面板再弹横幅，横幅会用 1024 级 viewport 跑 5 pass（`PanelBlurRenderer.java:425-455`） |
| 5 | **消息列表高度缓存** | 不是替换：`2a25c5a3` 新增缓存（本意是砍掉每帧整窗重测），但被上游每帧分配打穿 | 0.3.2：每帧全量测（`measureContentHeight` 逐行走 `messageHeight` 包装 + 文本宽度） | `MessageListView.java:1497-1519`：缓存，命中条件含 **`heightList == messages`（实例身份）**（`:1502`） | **在**，但命中条件被上游打穿 | **LIST**（A 0.301 / D 0.259） | **更贵**（缓存失效，退回每帧全测） | 机制：确证（`WorldFeedFilter.java:62` 每次 `filterFeed` 都 `new ArrayList`）；真机未测 | **不会**（只改身份判定） |
| 6 | **DOTS 的 Picture 缓存键** | `b7df5657` 新增 Picture/Image 缓存 | 0.3.2：直接逐圆 `drawCircle`（`DotTexture.java:24-53`），无缓存也无 key | `shared/.../DotTexture.java:79` 的 `Key` 含 **绝对 `x/y`**，`:123` / `:217` 每帧 `new Key(...)` 后查 `LinkedHashMap`；开合动画期面板矩形每帧变 ⇒ 每帧 miss ⇒ 录制 + 回放两份命令 | **在** | **DOTS**（A 0.679 / D 0.598，稳态中性） | 稳态**中性**；开合动画期**更贵** | 推断（E3 同结论） | **不会** |

### 2.1 三条「看着像但不是」的，别再去查

- **`resetAll()` → `resetGLAll()`**：同一个 `_nReset(int)`，只差位掩码常量（E3 §2 第 7 行，字节码已核）。中性。
- **`SkiaDraw` 从 AC 搬到 AtomLib**：旧实现（`34299d29:shared/.../SkiaDraw.java:31-59`）只有 `MaskFilter` 缓存、每张卡每帧仍要 GPU 现做模糊；AtomLib 版（`ui/src/main/java/com/atom/lib/design/atomchat/SkiaDraw.java:36-53,84-118`）把阴影栅格化成缓存 `Image` 再 blit，**更便宜**。见 §3。
- **`ImageLoader.validateSource`**（`e69b2b3b`）：多了一次 `Codec.makeFromData` 读文件头，但走的是下载/解码线程，不在帧路径上。

---

## 3. 「反向清单」：现在比过去好的地方（防止后人乱回滚）

| # | 改了什么 | 提交 | 旧（0.3.2 / 更早） | 新 | 为什么别回滚 |
| --- | --- | --- | --- | --- | --- |
| R1 | **Kawase 每 pass 的状态查询** | `b7df5657`（P0-C） | `34299d29:PanelBlurRenderer.java:411-414`，`refreshBlur` 在 `:352-354`：每个 pass 都 `glGetInteger(FBO_BINDING)` + `glGetIntegerv(VIEWPORT)` + `new int[4]`，`refreshBlur` 再来一遍 ⇒ 每帧 **18 次查询 + 6 个 `int[4]`** | `PanelBlurRenderer.java:619-623`：每 pass 只留 1 次 `glIsEnabled(GL_BLEND)`；`refreshBlur` 复用 `render()` 入口已捕获的 FBO/viewport/scissor（`:552-559`） | 这是纯减法，回滚等于把 15 次同步点加回来 |
| R2 | **模糊纹理的容量复用** | `b7df5657`（P0-A） | `34299d29:PanelBlurRenderer.java:276-300`：`w != texW || h != texH` 就销毁重建 3 张纹理，开合动画期**每帧重建**（注释给的离屏数据是每次 1.6–2.1ms） | `PanelBlurRenderer.java:425-455`：pow2 capacity，只增不减；`extendEdge` 模拟 `CLAMP_TO_EDGE` | 回滚会在开合动画期重新引入 per-frame realloc |
| R3 | **文本 shaping / 解析 / 换行缓存** | `b7df5657` | `34299d29:SkiaFontRenderer.java`：每帧 `new Paint()`、每段文本现 shaping、`WIDTH_CACHE` 每串都要拼 key | `SkiaFontRenderer.java:21-56`：`SHAPE_CACHE`（`TextBlob`）+ `PARSE_CACHE` + `WRAP_CACHE`；`:23-24` 两个静态 `Paint`；`:30` `ShapedRun` 直接 `drawTextBlob` | 这是 0.5.0 真正的改进，删任何一层都会回到每帧 native shaping |
| R4 | **宽度缓存按字体分桶** | `07e0a05c` | 单张 `WIDTH_CACHE`，key 里塞 size + text | `SkiaFontRenderer.java:121-140`：`Map<Float, Map<String,Float>>`，命中不再拼 key | 修的是「同文本不同字号串味」+ 每帧拼 key |
| R5 | **消息列表的每帧重算** | `2a25c5a3` | 0.3.2：每帧两趟全窗走（先测高再画）；每个时间分隔符都 `DateTimeFormatter.ofPattern("HH:mm")`（`34299d29:MessageListView.java` 内联） | `MessageListView.java:198` 静态 `CLOCK`；`:1486-1489` `rowTop/rowHeight/dividerTop` 数组；`:1497-1519` 高度缓存；`:336-344` 视口上方按 offset 跳过 | 回滚 = 把每帧 formatter 构建和整窗重测加回来 |
| R6 | **头像采样** | `93f58a16` + `2a25c5a3` | `34299d29:AvatarRenderer.java:93-100`：每张皮肤 `glGetTexImage` 读 64×64 到堆，无上限 `HashMap` 永久持有 | `AvatarRenderer.java:31-53` 有界 LRU（256）+ `SkinTextureReader.readAsync`（`shared/.../SkinTextureReader.java:104-116`） | 回滚 = 无界内存 + 同步 readback |
| R7 | **卡片阴影** | `4f78ab7a`（迁到 AtomLib） | `34299d29:SkiaDraw.java:31-59`：每张卡每帧 `drawRRect` + `MaskFilter`（GPU 现做 blur） | AtomLib `SkiaDraw.java:36-53,84-118`：`MaskFilter` 缓存 + 2× 超采样栅格 `Image` 缓存（48 张），命中只 `drawImageRect` | 别「搬回 AC」——搬回来就丢掉 image 缓存 |
| R8 | **`glGetError` 的采样** | `b7df5657`（P0-C） | `34299d29:PanelBlurRenderer.java:100,115`：每帧无条件 `drainGlErrors()` 两次（debug 与否都跑） | `PanelBlurRenderer.java:249-255`：debug 时每帧、产品默认 1/64；`drainGlErrors` 仍是永久熔断 | 产品路径比 0.3.2 **更便宜**；但注意 debug=on 时等于没省 |
| R9 | **DOTS 的静态场栅格化** | `b7df5657` | 0.3.2：About 页 halo 每帧遍历 columns×rows 画几百个圆 | `DotTexture.java:195-245`：`box.w*box.h ≥ 40000` 时栅格化成 4× 超采样 `Image`，每帧 1 次 `drawImageRect`；小场仍走 Picture | 大场是纯收益，别为了「统一路径」删掉 |
| R10 | **仪表门控** | `b7df5657` / `abe809e7` | 0.3.2 无此设施 | `FrameSegments` / `PrepassProbe` / `Truth3Probe` 全部 `debug` 门控，`measureOnly` 可整段关；E0 实测整包约 0.05ms | 结论已撤回的那几条（gpu_panel 等）不是删仪表的理由 |

---

## 4. 可执行建议（不写收益数字，只写方向与验证/退回）

> 统一前提：所有实验都用**相邻配对**（ABAB / ADAD，每档 10–12s、4–6 来回），不用全场合并中位；C 档在 400 锁下对漂移失明，不作为基准。

### 4.1 #1 模糊节奏（先做这一条）

- **怎么试（最小改动）**：不碰 `full = true` 的历史包袱，直接在 `PanelBlurRenderer.render` 里加一个**产品级** cadence 判定：复用 `Truth3Probe.blurRefreshDue` 的 30Hz 逻辑（或把 `af578252` 的 `BlurMotionGate` 以新名字搬回），被拒的 slot 走 `renderReuse`（`PanelBlurRenderer.java:220-241`，已存在且离屏验证过 diff=0）。
- **怎么验有效**：同一视角静止开面板，A（现状）↔ 新档 交替 4–6 来回；看 `prepass_probe` 的 `passes` 是否按预期下降、`frame_probe`/`frame_pump` 的相邻配对差是否稳定同向；同时截图对比面板区域像素（静态背景应 diff=0）。
- **失败怎么退**：保留开关（配置字段默认开/关各测一次）；若帧间隔没有稳定同向的变化，说明这段 CPU 被 GPU 松弛吸收，立即回到 `full = true` 并把预算转给 #2。**注意**：`ModeDCadenceTest.java:186`、`PrepassProbeTest.java:196`、`FramePumpProbeTest.java:192` 三处源码断言禁止 `BlurMotionGate` 名字回归，要么换实现名，要么连同断言一起改（这是有意决策，不是顺手改测试）。

### 4.2 #2 GL 状态范围

- **怎么试**：先在 AtomLib 里给 `captureSkia` 的构造与 `close()` 各插一对 `nanoTime`（JFR 事件 `GlScopeDiagnostics` 已存在），**只测量**，确认 `capture+close` 与 `BRIDGE_ENTER+EXIT` 对得上（Nov-5 的等式 `BRIDGE_ENTER − capture = 0.0041ms` 需要用当前 `captureSkia` 重测一次）。
- **怎么验有效**：加一个 debug-only 的 `captureSkiaMinimal()`（只快照 0.3.2 那 26 项 + 32×2 纹理查询），配置开关切换，ADAD 交替；判据是相邻配对同向且像素 diff=0。
- **失败怎么退**：若 `capture+close` 占不到 `BRIDGE_ENTER+EXIT` 的主体，说明 E3 的线性折算不成立，**放弃这条**，不要去改 `GlStateScope`（它是公开契约，`GlStateScopeTest` 覆盖最高纹理单元与异常恢复）。

### 4.3 #3 物品图标

- **怎么试**：先不改契约，只改 `ItemIconRenderer.prepare` 的**调用条件**：把「每帧重渲染」改成「仅当 `CACHE` 里没有该 key，或该 key 被标记为动画物品」时才 `takeRequests()`。判据：日志里 `ICONS` 段从每帧非零变成只在首帧/动画帧非零。
- **怎么验有效**：造一条含物品链接的消息放进列表，`debug=on` 看 `ICONS` p50 是否从「每帧都有」变成「偶发」；同时确认图标仍随附魔/动画刷新。
- **失败怎么退**：保留 `IconCache.request` 的原语义，只在 `prepare` 外面加一层「同一帧集合不变则跳过」的判定；不行就退回每帧重渲（功能优先）。
- **次选（要动 AtomLib）**：把 `withContext` 换成 `captureSkia` 级别的窄快照。若做，必须先证明 `resetGLAll`/`importInto` 只碰 Skia 自己那批状态——这是契约改动，单独评审。

### 4.4 #4 HUD 横幅

- **怎么试**：不改视觉，先只测「横幅可见时」与无横幅窗口的相邻配对差，确认它是否值得动。若要动，最安全的一步是**给横幅单独的纹理组**（或允许 capacity 在请求矩形远小于当前容量时缩容），避免先开过面板之后，横幅在小矩形上跑 1024 级 viewport 的 5 个 pass；这一步不改任何像素。
- **怎么验有效**：横幅可见 10s 的 `frame_probe` p50 与无横幅窗口配对；截图 diff=0。
- **失败怎么退**：把横幅的 backdrop 退回 Skia `ImageFilter`（`6f49262e` 的旧路径）——但那会复活 Iris 下的闪烁 bug，只有在确认用户不用 shader pack 时才允许。

### 4.5 #5 列表高度缓存

- **怎么试**：把 `heightList == messages` 换成「快照身份 + 过滤器版本号」：`WorldFeedFilter` 增加一个 `revision()`（每次过滤配置变更 +1），缓存 key 用 `(世界快照身份, filterRevision, width, from, config)`；或让 `filterFeed` 在 `ALL` 时返回原 list（已经是），非 ALL 时把结果缓存在 filter 自己身上、按快照身份失效。
- **怎么验有效**：开「仅玩家」过滤、滚动长列表；`LIST` 段 p50 应下降，且滚动/新消息到达时不错位（内容高度必须仍随新消息变化）。
- **失败怎么退**：删掉缓存分支，回到 `2a25c5a3` 之前的每帧重测（功能正确，只是慢）。

### 4.6 #6 DOTS 键

- **怎么试**：把 `Key` 的 `x/y` 换成「相对 strip 原点的局部坐标」，绘制时 `canvas.translate` 到 strip 位置；或在开合动画期直接走非缓存路径（`dotsCached=false` 的等价分支）。
- **怎么验有效**：开合动画录 30 帧，看 `cacheBuildCount` 是否从「每帧 +N」变成「每个尺寸 +1」；`DOTS` 段在动画期的 p95 应下降，稳态不变。
- **失败怎么退**：保留缓存但把 `Key` 还原为绝对坐标（现状），只是动画期多付录制成本——不会画错。

---

## 5. 做不到的部分（如实说明）

1. **0.3.2 的 `git_dirty=true` 不可还原**。`atomchat-build.properties` 写的是 `git_commit=3f324e3`、`git_dirty=true`；基准用的是 `34299d29` 的源码，不是那个 jar 的逐字节副本（见 `2026-11-06-baseline-0.3.2-identity.md`）。若打包时工作区里恰好改过 `GlStateUtil` / `PanelBlurRenderer`，本文「0.3.2 侧」的结论会有小偏差。
2. **2026-11-04 之后到 checkpoint 之前的全部改动被压成一个代码提交**（`b7df5657`，71 文件 / +5766 −622；连同 docs 与工作日志共 3 个提交、102 个文件）。所以「P0-A / P0-C / P1 / Truth3 / PrepassProbe / Mode D 分别是哪一天进的」在 git 里**已经不可考**，只剩工作日志（`.workbuddy/memory/2026-11-06.md`）与 `docs/verification/` 里的报告。本文对这几项的「提交号」只能统一写 `b7df5657`。
3. **已删除的分支/未被提交的实验**（工作日志提到的「15:51 被限流打断的子代理」「复制两份 preUi」等中间态）没有 git 对象，无法核对「当时是不是更好」。
4. **#3 / #4 / #5 没有真机数据**。本轮 `latest.log` 的场景里没有物品图标、没有开过滤、没有弹通知，`ICONS = 0.000ms`、`LIST` 也没被过滤路径污染，所以这三条只有代码确证 + 机制推断，没有「实测段」可以对账。
5. **GPU 侧不可归因**。`gpu_panel` 的绝对值已被撤回（同一会话内 1.46–2.60ms 骤降到 0.13–0.15ms），本文所有「更贵」都只指 CPU/驱动调用侧，不能换算成 GPU 时间。
6. **0.2.x 时代只抽样、没有全审计**。`layers/mapping/official/` 是另一套镜像源码，热路径文件在其中是复制的副本；本文以 `platforms/1.21.1-fabric/` 为准，镜像未逐文件比对（结构上应由 `tools/verify_targets.py` 保证一致）。
7. **`D:/AtomLib` 的工作树与实例里的 jar 不是同一 version 字符串**（E3 §5.2）。本文的 AtomLib 行号取自 `D:/AtomLib @ perf/checkpoint-20261106`；实例跑的是 `atomlib-Fabric-1.21.1-v0.3.1-shadowcache.jar`，E3 已用字节码交叉核对过关键类，但**行号可能对不上**。引用 AtomLib 时请以类/方法名为准。

---

**一句话：先动 #1（把模糊刷新的节奏找回来），别动 `a1dff96b` 那个提交，用树里已经验证过的 30Hz 复用路径重做。**
