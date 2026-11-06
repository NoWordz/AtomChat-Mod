# E3 第一步：0.3.2 → 现在的纯静态回归对照

**写于** 2026-11-06 19:4x ｜ **子代理** `e3-static-regression` ｜ **状态**：只读分析，未改任何生产代码、未构建、未启动游戏、未 commit

**对照对象**：基准工作树 `D:/AtomChat-032`（detached `34299d29`，2026-10-21 12:53，`mod_version=0.3.2`） ↔ 当前 `D:/AtomChat-Main @ perf/checkpoint-20261106`（`a19ec8d0`）+ 运行时依赖 `D:/AtomLib @ perf/checkpoint-20261106`，并用实例内实际生效的 `atomlib-Fabric-1.21.1-v0.3.1-shadowcache.jar` 字节码交叉核对。

**数值口径**：全文每个数字都标了 `真机` / `离屏` / `静态推断`。没有一处把未测数字写成收益，没有据此改代码，也没有给出任何 FPS 结论。

---

## 1. 结论先行

**找到了，而且只有一处够大。**

> **每帧进入/退出 Skija 绘制时保存并恢复整份 OpenGL 驱动状态的那段代码，被整体换成了更贵的实现。**
> 0.3.2 用的是 AC 自己的 `GlStateUtil`（约 **260** 次原生 GL 调用/帧）；
> 现在是 AtomLib 的 `GlStateScope`（约 **820–860** 次原生 GL 调用/帧）。
> 差约 **3 倍**，落在 `BRIDGE_ENTER` + `BRIDGE_EXIT` 这两段上，也就是当前全仓最大的单块 CPU（D 档 `0.716ms`，真机）。

具体点名：

| | 0.3.2 | 现在 |
| --- | --- | --- |
| 保存 | `platforms/1.21.1-fabric/.../SkiaGraphics.java:122` → `GlStateUtil.java:256-323` | `SkijaRenderBridge.java:125` → `GlStateScope.java:135-188` |
| 恢复 | `SkiaGraphics.java:159` → `GlStateUtil.java:161-254` | `SkijaRenderBridge.java:125`（try-with-resources 退出）→ `GlStateScope.java:225-276` |

**为什么更贵**（机制，不是感觉）：

1. **纹理单元快照按 32 个单元 × 每个 4 次调用遍历**，恢复时再遍历一遍（`GlStateScope.java:179-186`、`:269-273`）。0.3.2 只查 `GL_TEXTURE_BINDING_2D` + `GL_SAMPLER_BINDING` 两个绑定（`GlStateUtil.java:263-271`）；现在 `captureSkia` 还查 `GL_TEXTURE_BINDING_RECTANGLE`（`:37-38`）。这一项从 **96** 涨到 **128**。
2. **逐 draw buffer 的索引状态**：`drawBuffers` 循环里每个 buffer 做 `glIsEnabledi` + `glGetIntegeri_v` + 6 次 `glGetIntegeri`（`:150-153`），8 个 draw buffer ≈ **64** 次。0.3.2 完全没有这一段。
3. **逐 uniform buffer 绑定**：`maxUniformBufferBindings` 次 × 3 次查询（`:169-173`）。NVIDIA/AMD 典型 72–96 → **216–288** 次。0.3.2 完全没有。
4. **恢复侧同步膨胀**：同样的循环在 `close()` 里再来一遍（`:245-273`），且多出 16 个 pixel store、7 个 buffer binding、逐 UBO `glBindBufferRange`（`:263-268`）。

**量级**（静态推断，见 §4.2 的推导）：当前 `BRIDGE` 合计 `0.716ms`（真机 D 档），按调用数线性折算，0.3.2 侧同功能约 `0.2–0.25ms` ⇒ **差值 0.4–0.5ms CPU/帧**。这**大于**我们要解释的 0.13–0.19ms 帧间隔差，方向自洽（CPU 有松弛，见 `2026-11-06-mode-d-results.md:107`）。

**第二可疑（数量级小得多，但确实变了）**：每帧的像素传输归一化从 5 次 GL 调用变成 18 次（`SkiaGraphics.java:182-188` → `SkijaRenderBridge.java:226-233`）。

**第三可疑（不是代码回归，是量具）**：`debug=on` 时每帧多了一对 `glBeginQuery/glEndQuery(GL_TIME_ELAPSED)` + 一次 `glGetQueryObjecti`（`FrameSegments.java:27-45,50-56`）。**0.3.2 时代没有这套仪表**，而"380–390 FPS"那个数字的来源会话是否 `debug=on` 无从查证。这条必须在 E0 里先扣掉，否则上面所有归因都可能被量具污染。

**明确排除的（不要再花预算）**：

- **版式（Yoga）不在热路径**。AC 全仓 `grep` 只用 `UiTokens / SkiaDraw / AppIcons / UiCards / FontSource / DesignHost`，**没有任何一处**引用 `PresentationFrame` / `YogaLayoutEngine`（证据：`grep -rln "com.atom.lib.ui\|PresentationFrame\|YogaLayoutEngine"` 在 `platforms/ shared/ layers/` 只命中一个测试类）。你担心的"每帧重算布局"在当前面板里不存在。
- **面板模糊没有下沉到 AtomLib 的 backdrop**。`PanelBlurRenderer` 仍在 AC 内、仍是自己的 raw GL + Kawase，`MinecraftRenderBridge.drawBackdrop/BackdropRenderer` 在 AC 里**零调用**。
- **DOTS 稳态不是回归源**。见 §2 第 11 行：稳态是 Picture 回放，命令数与 0.3.2 相同，Java 侧还少了一圈浮点运算。
- **文本/字体不是回归源**。字体只加载一次；文本路径在 0.3.2 之上**多了** Shape/Parse/Wrap 三层缓存（更便宜）。
- **`resetAll()` → `resetGLAll()` 是假差异**。两者都只是 `DirectContext.reset(BackendState)` 的语法糖，`ALL=-1`、`GL_ALL=65535`，对 GL 后端覆盖同一批位，实际只传一个 int 进 `_nReset`（Skija 0.143.18 字节码核对）。**中性，不是回归**。

---

## 2. 对照表

> 「是否在每帧热路径」的证据统一写在最后一列的依据里；`D` 指真机 D 档（30Hz 复用）实测段。

| # | 组件 | 0.3.2 写法（文件:行） | 现在写法（文件:行） | 差异类型 | 每帧热路径？ | 方向 | 可信度 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | **GL 状态保存** | `GlStateUtil.java:256-323`（≈137 次查询） | `GlStateScope.java:135-188`（≈490–526 次查询） | 多一次调用 ×N，且换了实现 | **是**：`SkiaGraphics.java:58` 每帧 `bridge.drawFrame` → `SkijaRenderBridge.java:125` | **更贵** | 调用数：确证；耗时：推断 |
| 2 | **GL 状态恢复** | `GlStateUtil.java:161-254`（≈125 次原生写） | `GlStateScope.java:225-276`（≈330 次） | 同上 | **是**：try-with-resources 退出即执行，位于 `BRIDGE_EXIT` 窗口内 | **更贵** | 调用数：确证 |
| 3 | 像素传输归一 | `SkiaGraphics.java:182-188`（5 次：1 bind + 4 pixelStorei） | `SkijaRenderBridge.java:226-233`（18 次：2 bind + 14 pixelStorei + 2 alignment） | 多一次调用 ×13 | **是**：`SkijaRenderBridge.java:128`，每帧 | 略贵（µs 级） | 确证 |
| 4 | DirectContext / Surface 所有权 | AC 内自持（`SkiaGraphics.java:27-29,57-86`） | AtomLib 单例桥（`SkijaRenderBridge.java:65-74,182-196`） | 结构迁移 | **是** | 中性偏贵（每帧多 `glfwGetCurrentContext()` + `GL.getCapabilities()`，`SkijaRenderBridge.java:178-180`） | 代码：确证；耗时：推断 |
| 5 | 每帧目标对象 | 直接读 `fb.fbo/textureWidth` | `MinecraftRenderBridge.target()` 每帧 `new FramebufferTarget(...)`，record 紧凑构造里 5 条校验（`FramebufferTarget.java:9-15`） | 多一次分配 | **是** | 略贵（分配 + 校验，µs 级） | 确证（代码） |
| 6 | 绘制会话包装 | 无 | `RenderSession.paint`：多一层 `canvas.save/restoreToCount` + `ThreadLocal` set/get/remove（`RenderSession.java:27-41`） | 多一层间接 | **是** | 中性（µs 级） | 确证 |
| 7 | `resetAll` / `resetGLAll` | `context.resetAll()`（`SkiaGraphics.java:130`） | `context.resetGLAll()`（`SkijaRenderBridge.java:136`） | 写法替换 | **是** | **中性**（同一 `_nReset(int)`，只差一个位掩码常量） | 确证（字节码） |
| 8 | flush | `context.flush(surface)`（`SkiaGraphics.java:158`） | `context.flushAndSubmit(surface)`（`SkijaRenderBridge.java:150`） | 多一次 submit | **是** | 中性偏贵（`syncCpu=false`，无 `glFinish`） | 推断 |
| 9 | 表面内容保留声明 | 无 | `surface.notifyContentWillChange(RETAIN)`（`SkijaRenderBridge.java:139`） | 多一次调用 | **是** | 中性 | 推断 |
| 10 | **每帧 GPU 计时查询** | 无（`FrameSegments` 不存在） | `FrameSegments.begin/finish` → `GPU.beginFrame/endFrame` + 每帧 `pollGpu`（`FrameSegments.java:27-45,50-56`；`GlGpuTimer.java:77,82,87`） | 多一次同步类调用 | **是，但仅 `debug=on`** | **更贵**（量级未测） | 代码：确证；耗时：**未测** |
| 11 | 分段时钟 | 无 | 每帧 4–8 次 `System.nanoTime()` + 环形缓冲写入（`FrameSegments.java:36-39`；`DotTexture.java:118,142,197,236`） | 多一次调用 | **是，仅 `debug=on`** | 略贵（亚 µs 级） | 确证 |
| 12 | DOTS 点阵 | 逐圆直接绘制（`DotTexture.java:24-53`） | Picture 缓存 + 每帧 `new Key(...)` + accessOrder `LinkedHashMap.get`（`DotTexture.java:94-111,116-144`） | 少一次缓存 ↔ 多一次哈希/分配 | **是**：`drawEdge` 每次面板帧 1–2 次 | **稳态中性**；**开合动画期更贵**（Key 含绝对 x/y，动画期每帧重建 → 录制+回放两次命令） | 推断 |
| 13 | 卡片阴影 | 每帧 `MaskFilter` 复用（0.3.2 `SkiaDraw.java:51-59`） | 缓存成 raster Image + `ShadowKey` 查表（AtomLib `SkiaDraw.java:42-53,87-118,120`） | 多缓存 | **是**（每张卡片） | **更便宜** | 确证（代码） |
| 14 | 文本塑形 | `WIDTH_CACHE` + `PRIMARY_GLYPH_HITS`（0.3.2 已有，`SkiaFontRenderer.java:38,60-70`） | 再加 `SHAPE_CACHE / PARSE_CACHE / WRAP_CACHE` 三层（当前 `SkiaFontRenderer.java:34-56`） | 多缓存 | **是** | **更便宜** | 确证 |
| 15 | 字体来源 | 包内 `/assets/atomchat/font/bundled.otf` | AtomLib `/com/atom/lib/fonts/bundled-default.otf`（`FontManager.java:18-19,74-78`） | 结构迁移 | **否**，一次性加载并缓存（`FontManager.java:106-116`） | 中性 | 确证 |
| 16 | Yoga / PresentationFrame | 不存在 | 存在但 **AC 零引用** | 结构迁移 | **否** | 中性 | 确证（grep） |
| 17 | 面板模糊归属 | AC 内 raw GL + Kawase（`PanelBlurRenderer.java:69`） | **仍在 AC 内**（`PanelBlurRenderer.java:74`）；AtomLib `BackdropRenderer` 零调用 | 结构迁移（未发生） | D 档复用帧只走 composite | 中性 | 确证 |
| 18 | `isAvailable()` | 纯字段判空（`PanelBlurRenderer.java:184-186`） | 加 `AtomChatConfig.get()` + `PrepassProbe.onFrame(...)`（当前 `:300-309`） | 多一次调用 | **是，仅 `debug=on`** | 略贵（每帧十余条指令） | 确证 |
| 19 | 像素对齐 | 无 | `UiPixelSnap.beginFrame` 每帧 1 次 ThreadLocal set（`SkiaGraphics.java:51`），全仓仅 9 个读取点 | 多一层 | **是（极轻）** | 略贵（µs 级） | 确证 |
| 20 | 混合开关 | `RenderSystem.enableBlend()/disableBlend()` 每帧各一次（`SkiaGraphics.java:131,160`） | 无 | 少一次调用 | **是** | **更便宜** | 确证 |
| 21 | 面板绘制入口 | `graphics.draw(...)`（`AtomChatScreen.java:1917`） | `graphics.drawPanel(...)`，仅面板参与 profile（`AtomChatScreen.java:2050`；`SkiaGraphics.java:30`） | 结构性 | **是** | 中性 | 确证 |
| 22 | `UiTokens` / `SkiaDraw` 位置 | AC 包内（`shared/.../chat/render/SkiaDraw.java`） | AtomLib `ui` 模块（`com/atom/lib/design/atomchat/`） | 结构迁移（跨 jar） | **是**（每帧几十次调用） | 中性（同一类加载器、同一 JIT，无证据表明白盒边界失效） | 推断 |

---

## 3. 结构迁移专章：AtomLib 依赖引入带来的间接层

0.3.2 **完全没有 AtomLib 依赖**（`gradle.properties` / `build.gradle` / `settings.gradle` 零命中；字体自带、`SkiaGraphics` 自持 `DirectContext` + `Surface`）。
现在的结构：AC 只保留"面板语义"，**GL 上下文所有权、状态契约、设计令牌、字体资源**全部搬到 AtomLib。逐条判断是否在每帧热路径上：

### 3.1 在每帧热路径上（需要正视）

| 间接层 | 0.3.2 | 现在 | 每帧增量（静态推断） | 判断 |
| --- | --- | --- | --- | --- |
| **Skija 上下文桥** `SkijaRenderBridge.drawFrame` | 无此层，`SkiaGraphics.draw` 直接 `GlStateUtil.save()` → 画 → `flush` → `restore` | `MinecraftRenderBridge.get()` → `bridge.drawFrame(target, painter)` → 5 个前置断言 → `GlStateScope` → `session.paint` → `flushAndSubmit`（`SkijaRenderBridge.java:120-156`） | 4 次方法调用 + 2 次 `Objects.requireNonNull` + 2 个 `ThreadLocal` set/remove + 1 个 record 分配 + `glfwGetCurrentContext` + `GL.getCapabilities()` | **这是本报告的核心候选**。函数调用本身可忽略，**但绑在它上面的 `GlStateScope` 契约不可忽略**（§2 第 1、2 行） |
| **GL 状态契约** `GlStateScope` | 26 个状态项，32 个纹理单元 × 2 查询 | 60+ 状态项，32 纹理单元 × 3 查询 + 逐 draw buffer + 逐 UBO | **约 820 vs 260 次原生 GL 调用** | **确证更贵**，量级最大 |
| **像素传输归一** `normalizePixelTransfer` | `glStorePixel()` 只清 UNPACK 侧 4 项 | PACK + UNPACK 两侧 16 项全清 | 13 次额外 `glPixelStorei` | 确证更贵，µs 级 |
| **设计令牌/绘制原语跨 jar** `UiTokens` / `SkiaDraw` / `UiCards` | AC 包内 | AtomLib `ui` 模块，经 JiJ/依赖 jar | 无调用点形态变化（静态方法，同 JIT） | 中性；`SkiaDraw` 内容反而变便宜（阴影图片缓存） |
| **会话包装** `RenderSession.paint` | 无 | 多一层 `canvas.save()` + 计数校验 + `restoreToCount` | 3 次 JNI + 1 次 ThreadLocal | 中性（µs 级） |

### 3.2 不在每帧热路径上（可以划掉）

| 间接层 | 证据 | 判断 |
| --- | --- | --- |
| **Yoga 版式** `YogaLayoutEngine` / `PresentationFrame` | AC 全仓零引用（只命中一个测试类 `SettingsHomeReplicationTest`） | **不在路径上**。面板仍是手写 `UiLayout`，`layout()` 每帧只是坐标算术，与 0.3.2 相同 |
| **文本服务** `TextService` / `ParagraphTextService` | AC 的 `SkiaFontRenderer` 不引用 `com.atom.lib.text`（grep 零命中）；文本仍走 AC 自己的 `SkiaFontRenderer` + `FontManager` | **不在路径上** |
| **字体资源** `FontSource` | 只在 `FontManager` 首次加载时调用一次，之后 `CACHE` 命中 | **不在路径上**（但首开同步加载仍是长帧源，见 mode-d-results §3.1） |
| **Backdrop（模糊下沉）** `MinecraftRenderBridge.drawBackdrop` / `BackdropRenderer` | AC 零调用；`PanelBlurRenderer` 仍在 AC，仍是 raw GL | **迁移没有发生**，模糊路径与 0.3.2 同源（D 档已验证 `D−B=0.009ms`） |
| **物品图标** `MinecraftItemIcons` / `ItemIconRenderer.withContext` | 仅在图标缓存未命中时调用；实测 `ICONS = 0.000ms` | 冷路径。⚠️ 但 `withContext` 用的是 **`GlStateScope.capture()` 全量路径**（`SkijaRenderBridge.java:97`），单次约与 Nov-5 的 2.13ms 同量级——**这是冷路径的隐藏地雷**，不是稳态回归 |

### 3.3 这一章的一句话结论

> 依赖结构变化确实引入了新的间接层，但**其中只有一个层是"被换成了更贵的版本"**：GL 状态快照。其余要么是常量级（µs），要么根本没被调用（Yoga / TextService / Backdrop）。

---

## 4. 与真机测量数据的对齐

### 4.1 候选 → 实测段

真机数据（`2026-11-06-mode-d-results.md:99-107`，D 档，`debug=on`）：

| 实测段 | 值（ms） | 谁在里面 | 本报告候选 | 能否解释 |
| --- | --- | --- | --- | --- |
| `BRIDGE_ENTER` | **0.447** | `bridge.drawFrame` 从进入到回调第一行 | **#1 GlStateScope.captureSkia**（+ #3 #4 #5 #9 #19、#10 GPU 计时开始） | **能，且是主嫌疑**。Nov-5 的 JFR 实测证明 `BRIDGE_ENTER − capture = 0.0041ms`（`atomlib/docs/evidence/gl-scope-sample-20261105-103433.md`），即 enter 桶 ≈ capture |
| `BRIDGE_EXIT` | **0.311** | 画完 → `flushAndSubmit` → `GlStateScope.close()` → `PAINTING.remove` | **#2 GlStateScope 恢复**（+ #8 flushAndSubmit） | 能（恢复侧） |
| `DRAW` | 0.827 | 面板回调内 | #21 #18 #12 #13 #14 | 结构相同，最多 µs 级 |
| `DOTS` | 0.598（嵌套在 DRAW 内） | `DotTexture.drawEdge/drawField` | #12 | 稳态命令数不变；**不能解释回归** |
| `LIST` | 0.259 | 消息列表 saveLayer + 文本 | #14（更便宜） | 不能解释 |

**关键结构事实**：`BRIDGE_ENTER` 的起止点由 `SkiaGraphics.java:56-59` 钉死——起点在 `bridge.drawFrame(...)` 之前，终点在回调体第一行。因此**桥内部在主回调之前做的一切（含 `GlStateScope` 构造）全部落在 `BRIDGE_ENTER` 里**；`BRIDGE_EXIT` 同理包住 `close()`。这两段合计 `0.716ms`，是 D 档里唯一"与画面内容无关、纯固定开销"的大块。

### 4.2 量级推导（静态推断，逐步可复核）

1. Nov-5 JFR 实测：`capture`（当时走的是全量 `capture()`，纹理单元 **192**、目标 **10**）p50 = **2.1335ms**（离屏/采样，非本轮真机）。
   该次调用数 ≈ 192×(1 次 `glActiveTexture` + 10 次绑定查询 + 1 次 sampler) ≈ 2 304，加其余 ≈ 480 ⇒ ≈ 2 780 次。
   ⇒ **单次原生 GL 查询 ≈ 0.77µs**（静态推断，由上面两个真机/采样数相除得到）。
2. 现在走 `captureSkia`，单元 **32**、目标 **2**（`GlStateScope.java:37-40`）：128 次 + draw buffer 64 次 + UBO 3×N（N=72–96 ⇒ 216–288）+ 其余 ≈ 60 ⇒ **490–526 次**。
   ⇒ capture ≈ **0.38–0.44ms**，与今天真机 `BRIDGE_ENTER 0.447ms` 吻合（残差 0.004–0.07ms）。
3. 恢复侧写入单价低于查询（`glEnable`/`glBindTexture` 无回读），实测 `BRIDGE_EXIT 0.311ms` 对应 ≈ 330 次写 + `flushAndSubmit`，自洽。
4. 0.3.2 侧同功能 ≈ 137 次查询 + 125 次写 ⇒ ≈ **0.20–0.25ms**（按同一单价折算）。
5. ⇒ **CPU 增量 ≈ 0.45–0.5ms/帧**。

**为什么 0.45–0.5ms 的 CPU 只换来 0.13–0.19ms 的帧间隔差**：D 档 AC 自身 CPU ≈ 1.55ms，而帧间隔 2.766ms（C 档 2.496ms），说明有约 1ms 的 CPU 松弛被 GPU/管线吸收（`mode-d-results.md:107` 已写明）。把 0.5ms 塞回松弛里，只有一部分会变成帧间隔——**方向与量级都自洽，但这最后一步是推断，不是测量**。

### 4.3 怎么证伪（按代价从低到高）

| 步骤 | 做法 | 判据 |
| --- | --- | --- |
| **V1（半小时，零风险）** | 只在 AtomLib 里给 `GlStateScope` 构造器与 `close()` 各加一对 `System.nanoTime()`（JFR 已有 `GlScopeDiagnostics`，可直接开 `-XX:StartFlightRecording` 或加 `jdk.jfr` 事件），跑一次 D 档 | 若 `capture+close ≥ 0.6ms` 且 `capture ≈ 0.44`，**结构性质疑坐实**；若远小于此，本报告主结论作废 |
| **V2（1 小时）** | 在同一版里加一个 **debug-only** 的 `captureSkiaMinimal()`（只快照 0.3.2 的 26 项 + 32×2 纹理查询），配置开关切换，`ADAD` 交替 5 个来回 | 若帧间隔改善 **0.13–0.19ms**，**直接闭环**；若改善 <0.05ms，说明这 0.5ms CPU 被完全吸收，**E3 的答案不在桥** |
| **V3（半天，要独立构建）** | 在 `D:/AtomChat-032` 里给 `GlStateUtil.save/restore` 打同一对 `nanoTime`，构建一版候选包，同协议对比 | 得到 0.3.2 侧的真实单价，替掉 §4.2 第 4 步的折算。**这是唯一能把"推断"升级成"确证"的路径** |
| **V4（与 V1 同批做）** | E0 仪表标定：`debug=on 全仪表` vs `measureOnly` | 若差 >0.05ms，**#10（GPU 计时查询）就是独立的第三个来源**，且此前所有绝对值都要先减偏移 |

**必须一起验的反假设**：
- **"0.3.2 那 380–390 是在 `debug=off` 下测的"** —— 若成立，则 `FrameSegments`+`PrepassProbe`+`Truth3Probe`+`GL_TIME_ELAPSED` 这套仪表本身就是 0.05–0.1ms 级差异，**E3 的预算要从桥里分一块给量具**。E0 之前不能排除。
- **"0.3.2 的 `GlStateUtil` 也很贵"** —— 如果 V3 测出 0.3.2 侧 save+restore 就有 0.4ms，那本报告主结论的量级要砍半。
- **"0.3.2 是 2Hz 静景 + 旧场景/旧 mod 表"** —— 跨会话数字本就不可直接相减（计划 §5.1 口径警告），本报告只把它当量级提示。

---

## 5. 不确定性（如实标注）

1. **`git_dirty=true` 无法还原**。本报告的对照对象是**提交 `34299d29` 的源码**，不是那个 jar 的逐字节复制（见 `2026-11-06-baseline-0.3.2-identity.md:107`）。若 0.3.2 打包时工作区里恰好改了 `GlStateUtil` 的方法体，本报告的"0.3.2 侧 137 次查询"可能不准。**0.02ms 级结论需考虑此不确定度**，0.5ms 级结论不受影响（量级差 20 倍）。
2. **`versions/atomlib.json` 与实例内实际 AtomLib 不是同一个 version 字符串**：构建清单写 `0.1.0-beta`（`atomlib-input-0.1.0-beta.zip`，sha256 `51a95d0…`），实例里跑的是 `atomlib-Fabric-1.21.1-v0.3.1-shadowcache.jar`（Nov 5 15:03）。本报告的所有"现在"侧结论**已用实例内 jar 的字节码交叉核对**（`GlStateScope` 含 `captureSkia/SkiaTextures/Limits`；`SkijaRenderBridge` 含 `fastState=false` → `resetGLAll` → `captureSkia` → `normalizePixelTransfer` → `flushAndSubmit`），而不是只信 `D:/AtomLib` 工作树。
3. **`BRIDGE_ENTER ≈ capture` 的证据来自 Nov-5 的一次 JFR 采样**（当时是 192 单元的全量 `capture()`，p50 2.13ms）。今天走 `captureSkia`，**这个等式本身没有被重新测过**，只是"调用数按比例缩小后与 0.447ms 自洽"。**V1 就是为了补这一刀。**
4. **单次 GL 调用 0.77µs 是折算值，不是标定值**。它把"2 133 500ns ÷ 2 780 次"当成了线性关系；实际上 `glGetIntegerv`（标量）、`glGetIntegeri_v`（索引）、`glActiveTexture`（状态切换）单价不同，且驱动可能缓存。**区间给的是 0.38–0.44ms，不是点估计。**
5. **`maxUniformBufferBindings` 的实际值未知**（本机 GPU/driver 未采集）。UBO 循环是 capture 里唯一的"未知长度"项，N 从 72 到 96 会让总数在 490–526 之间浮动。
6. **`notifyContentWillChange(RETAIN)` / `flushAndSubmit` 的真实成本未测**，本报告按"中性偏贵、µs 级"处理，若 V1 发现二者之一异常昂贵，需要单列。
7. **`debug=on` 的仪表开销未标定**（E0 未做）。本报告把它列为**独立候选 #10**，不并入桥的账。
8. 本报告**没有**回答"0.3.2 到底是 380 还是 361"这类 FPS 问题，也没有给出任何优化收益预估。§4.2 的"0.45–0.5ms"是**有待 V2/V3 检验的假说**，不是收益。
9. **分析期间工作区被并行代理改动过**：`Truth3Probe.java`（+131 行）、`AtomChatConfig.java`（+13 行）、`PanelBlurRenderer.java`（+14 行）等在 19:29–19:30 出现改动，内容是 E0 的 `measureOnly` 与 `instrumentEnabled` 门控（正是 §4.3 V4 需要的东西）。这些改动**不触碰** Skija 桥 / `GlStateScope` / `SkiaGraphics`，故不影响本报告主结论；但 `PanelBlurRenderer` 的行号已顺移约 4 行，本报告引用的是 19:25 前后读到的版本（`isAvailable` 现位于 `:300`）。本子代理未改任何生产文件，只在 `docs/verification/` 下新增了这份报告。

---

## 6. 一段话总结

**找到 3 处可疑写法，最可疑的是第 1 处。**

三处分别是：① 每帧保存/恢复整份 GL 驱动状态的实现从 AC 自带的 `GlStateUtil`（约 260 次原生 GL 调用/帧）换成了 AtomLib 的 `GlStateScope`（约 820–860 次，多出逐 draw-buffer、逐 uniform-buffer、逐纹理单元矩形目标三类遍历），这一处新增的 CPU 约 0.45–0.5ms/帧，正好压在 `BRIDGE_ENTER 0.447 + BRIDGE_EXIT 0.311` 这 0.716ms 里；② 每帧像素传输归一化从 5 次 GL 调用涨到 18 次（µs 级，顺手可修但不是答案）；③ 不是代码而是量具——`debug=on` 时每帧多了一对 `GL_TIME_ELAPSED` 查询，而 0.3.2 的 380–390 是在没有这套仪表的版本上取得的，"当时的 debug 状态"无从查证，这条会污染所有归因。

**建议下一步按 V1 → V4 → V2 → V3 走**：先花半小时在 AtomLib 里给 `GlStateScope` 构造器和 `close()` 各插一对 `nanoTime`（JFR 事件已经现成），跑一次 D 档，看 `capture` 是否真的 ≈0.44ms、`capture+close` 是否 ≥0.6ms——这一步只量不改，能把本报告从"静态推断"推到"真机确证"；同一次启动里顺手做 E0（关掉 `FrameSegments`/`PrepassProbe` 只留帧探针），把量具的账单独结清；两项都成立后再做 V2（debug-only 的最小状态快照，`ADAD` 交替验证能否收回 0.13–0.19ms），最后才考虑 V3 去独立构建 0.3.2 打点版把折算换成实测。**在此之前不要动 `GlStateScope` 的产品默认路径**——它是 AtomLib 的公开 GL 恢复契约，缩它有画面/其它 mod 状态污染的风险（`GlStateScopeTest` 已覆盖最高纹理单元与异常恢复），必须先用 V2 的开关证明收益再谈落地。
