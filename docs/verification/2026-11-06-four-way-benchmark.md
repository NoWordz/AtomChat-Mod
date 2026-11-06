# 四家渲染实现对标：Muonium / tuui / chatcore / ModernUI 3.12

**写于** 2026-11-06 ｜ **子代理** `four-way-benchmark` ｜ **状态**：只读分析，未改任何生产代码、未构建、未启动游戏、未 commit。

**产出物**：
- 反编译：`build/fourway-20261106/mu-decomp/`（ModernUI 3.12.0.1，Vineflower 1.10.1，90 个 class → 35 个 .java，`vf-mu.log` 无 error）
- 解包类：`build/fourway-20261106/mu-classes/`
- 沿用：`build/tuui-research-20261106/{tuui-decomp,chatcore-decomp,evidence}`（既有，未重做）

**口径声明**：全文所有耗时数字都来自既有报告，标注了来源；本文新增的数字只有**静态调用计数**（逐行数方法体得到），已逐条标明是计数不是测量。**没有任何 FPS 收益数字**。

---

## 0. 一页结论

按价值排序，只有三件事值得抄，其余都是「看懂了但别动」。

### ① 把每帧 GL 快照从「整份驱动状态」缩到「Skia 真的会动的那十几项」——**最高价值**

- **抄什么**：Muonium 的 `GlSnapshot`（12 个纹理单元 + 约 47 个标量状态／22 组，`SkijaHost.java:538-540,600-697,698+`）＋它末尾那句 `BufferUploader.reset()`（`:834`）；以及 ModernUI 的处置方式——**根本不做读回**，只在自己 pass 结束时用 `RenderSystem`/`GlStateManager` 与裸 GL **成对写一遍已知良好状态**（`UIManager.java:713-760`）。
- **省哪一段**：`BRIDGE_ENTER + BRIDGE_EXIT`（D 档真机 `0.447 + 0.311 = 0.716ms`，全仓最大单块 CPU）。AC 现在每次 `GlStateScope` 做 **490–526 次查询 + ≈330 次写**（`GlStateScope.java:135-188,225-276`）；Muonium 同功能约 **70 次查询 + ≈75 次写**；ModernUI 约 **2 次查询 + ≈30 次写**。
- **会不会动视觉**：**不动**。这项只改「保存/恢复的粒度」，不改任何一帧的绘制命令。

### ② 把面板的静态 chrome 烤成一张 GPU 纹理，静止帧只合成 1 个 quad——**第二价值**

- **抄什么**：ModernUI 的「整页层 + 脏区才重录」（`ViewRoot.java:189-275`、`UIManager.java:1055-1095`）与 chatcore 的「层不重录就只合成」（`UIManager.java:701-712,760-790`）。
- **省哪一段**：`DOTS 0.598ms` 里「每帧重放命令 + 重复填充」的那一部分（AC 的 `Picture` 只省了**录制**，没省**重放**），以及 chrome 上所有静态圆角/描边/发光的重复填充。能不能整段吃掉取决于 DOTS 内部是命令数主导还是填充率主导——**必须先 A/B**。
- **会不会动视觉**：**静止帧不动；开合动画期会动**。AC 的 `OPEN_MIN_SCALE = 0.94`（`PanelOpenMotion.java:130`）意味着动画期面板被缩放，此时 1:1 烘好的纹理会重采样。AC 已有现成门控 `PanelOpenMotion.layerSkippable(alpha, scale)`（`:208`），只在 `alpha≥0.999 && |scale-1|<0.001` 时用烘好的层，动画期照旧直画 ⇒ 视觉零差异。

### ③ 每帧分配清理——**第三价值，主作用是降抖动而非省段**

- **抄什么**：不是抄某家，而是三家都没有 AC 这几处：`ShellHeader.java:106-107` 每帧 `LocalTime.now()` + `String.format("%02d:%02d")`；`SkiaFontRenderer.java:652` 每帧每个字符串 `new ShapeKey(...)` 只为查表；`FramebufferTarget` 每帧 `new`（e3 §2 第 5 行）。
- **省哪一段**：不落在某个大段上，落在**帧时间方差**与 GC 上。
- **会不会动视觉**：**不动**。

> **模糊时序**：不作主答案。口径见文末附录「D 档口径说明」——`mode-d-results.md:38` 的原文是「D 把模糊的帧间隔成本收得几乎一干二净」（D≈B）；即便按最有利的读法，它也已经实现并在树里、且动画期本就不能复用，所以排不进前三。

---

## 1. 先给一张对照尺：每帧 GL 状态开销（静态调用计数）

> 全部是**逐行数方法体**得到的调用次数，不是测量。口径：一次 `glGetInteger`/`glIsEnabled`/`glGetIntegerv` 记 1 次查询，一次 `glBind*`/`glEnable`/`glUseProgram` 记 1 次写。

| 实现 | 查询/帧 | 写/帧 | 快照范围 | 与宿主缓存（`GlStateManager`）的关系 |
| --- | --- | --- | --- | --- |
| **ModernUI 3.12** | **2**（且仅当本帧有新录制） | ≈30 | 不快照。只读 VAO + program 两项，其余全部**写**成已知良好值 | 走 `RenderSystem`/`GlStateManager` **并**同时发裸 GL，缓存与真实状态一起被改对（`UIManager.java:726-757`） |
| **Muonium 1.20.1** | ≈70 | ≈75 | 12 单元 × 2 + 约 47 个标量（22 组）；**不含** UBO、索引 draw buffer、clip distance、sample mask、program pipeline | 纹理单元**故意走 `GlStateManager._bindTexture`**，其余走裸 GL 还原成快照值（`SkijaHost.java:511-536` 有完整论证） |
| **tuui 1.1** | ≥ 2×`GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS` + ≈20（桌面常见 32 → ≈84）**每次 `Graphics.draw`** | ≈60 | 纹理单元按 **COMBINED** 上限循环 + 20 项 | 纯裸 GL，不碰缓存 |
| **AtomChat 现在** | **490–526** | **≈330** | 18 enables + 4 clip distance + 16 sample mask + 32 单元 × 3 + 每 draw buffer 8 项 + 每 UBO 3 项 + ≈50 标量 | 纯裸 GL，注释明确「不使用 Blaze3D 状态变更器或缓存失效」（`GlStateScope.java:12-15`） |

**这张表就是本报告最重要的一句话**：AC 比同行贵 6–250 倍，贵的不是「要不要还原」，而是**「还原谁」的边界选得太宽**。

---

## 2. 逐家专章

### 2.1 Muonium Player（`D:/MuoniumPlayer-multiversion`，`main@c602189`，v2.3.1）

#### 2.1.1 它解决什么问题

Skija 在 MC 里画东西时，**裸 GL 改坏了 MC 的状态缓存**——MC 的 `GlStateManager` 以为状态没变，后续绘制就省略切换，结果是天空/云变错色块、主菜单全景糊掉、手持物贴图错乱（这段是作者写在注释里的实测症状，`SkijaHost.java:517-527`）。它要的是「画完把现场还原得像没来过」。

#### 2.1.2 具体做法

**A. 快照 + 原样还原（不是「设成一套已知好值」）**：`SkijaHost.java:538` 起

```java
// SkijaHost.java:600-616（节选）
void capture() {
    program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    elementArrayBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
    activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    for (int unit = 0; unit < UNITS; unit++) {          // UNITS = 12（:540）
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        boundTextures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boundSamplers[unit] = readSampler();
    }
    GL13.glActiveTexture(activeTexture);
    blend = GL11.glIsEnabled(GL11.GL_BLEND);            // 然后 blend/depth/cull/scissor/stencil/
    ...                                                 // colormask/polygon/logicop/srgb/unpack
}
```

**边界**：`program / VAO / array buffer / element buffer / 12×(2D 绑定 + sampler) / blend(6+1) / depth(4) / cull(3) / scissor(1+4) / stencil(1+7) / colorMask(1) / polygon(3) / logicOp(2) / SRGB / primitiveRestart / unpack(4)`。
**不含**：uniform buffer 绑定、`GL_DRAW_BUFFER0..N` 索引态、`GL_CLIP_DISTANCE*`、`GL_SAMPLE_MASK*`、program pipeline、blend color、clear color。

**B. 还原时故意混用缓存**：纹理单元走 `GlStateManager._bindTexture`，因为 Skia 帧内 MC 的纹理上传链路会改 `GlStateManager` 的缓存，只用裸 GL 还原会制造「缓存说 A、真实是 B」的新分歧（`:528-536`）。

**C. 末了让 MC 自己的旁路缓存失效，而不是去还原它**：

```java
// SkijaHost.java:828-834
SNAPSHOT.restore();
// BufferUploader 单独缓存「当前绑定的 VertexBuffer」，不经过 GlStateManager。
// 漏掉它的后果是驱动直接报 "GL_INVALID_OBJECT: Array object is not active"（首次实测复现过 12 条）。
com.mojang.blaze3d.vertex.BufferUploader.reset();
```

**D. 每帧开头先让 Skia 忘掉自己的缓存**：`SNAPSHOT.capture()` → `context.resetAll()` → `resetPixelUnpackLayout()`（`:234-236`），顺序在注释里写死了理由。

**E. 帧末的「欠账收干净」**：上层若少 pop 一层，`restoreDepthTo(0)` 收栈（`SkijaGfx.java:96-99`），不让栈错位传染下一帧。

#### 2.1.3 它为什么快

1. **不猜「GUI 阶段应该是什么样」，只记录「此刻实际是什么」**——所以不需要「先设反值再设回」的技巧，也不需要覆盖 MC 不管的那批状态（论证见 `:511-527`）。
2. **快照边界按「Skia 会动什么」定，不按「GL 有什么」定**。Skia 的 GL 后端不用 UBO、不写 draw buffer > 0、不碰 clip distance / sample mask —— 这三类正是 AC 里最贵的三段（e3 §4.2：UBO 216–288 次、draw buffer ≈64 次）。
3. **纹理单元只扫 12 个**而不是「实现上限」。AC 扫 32 个，tuui 扫 `GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS`（通常 32，某些驱动 96）。

#### 2.1.4 对 AC 是否适用

**适用，而且是全场最该抄的一项**，但要拆成两步、分两次落地：

- **第一步（低风险）**：保留 `GlStateScope` 的对外契约，只把**它能证明 Skia 不会动**的那几类循环删掉（UBO / 索引 draw buffer / clip distance / sample mask），并把纹理单元从 32 降到 12（AC 的 Skia 版本 0.143.18 的 `kMaxSaneSamplers` 上限是 32，但实际使用远小于此；Muonium 用 12 是同类论证）。
- **第二步（中风险）**：把「原样还原」改成 Muonium 的「快照 + 还原 + 失效旁路缓存」，但**不要学 ModernUI 的「不保存只归一化」**——见 §2.4.4 的理由（AC 的钩子不止一处，ModernUI 只在 Screen 一个点）。

#### 2.1.5 前置条件

- 必须能**证明** Skia 0.143.18 不碰这些状态。Muonium 的结论来自它自己那次「7-bit 白名单 → 整帧 9216 像素全错」的实验（反过来证明了「不能只重置一部分」），AC 侧对应的是 `SkijaRenderBridge.java:33-54` 那段注释——**注意这两件事不是一回事**：那段说的是「让 Skia 重置自己的缓存不能只重置 7 位」，本报告说的是「还原宿主状态不必记全 60 项」。两者容易混，落地方案里必须写清。
- 需要 `GlScopeDiagnostics`（已有）做前后对照。
- 需要一个 debug 开关做 A/B（`SkijaRenderBridge.fastState` 已经是这个形状，但目前只用来切 limits 缓存）。

#### 2.1.6 会不会改视觉

**不改。** 唯一可能改画面的是「漏还原某一项」导致 MC 后续绘制错乱——这正是 `GlStateScopeTest` 已覆盖的部分，也是分两步走的理由（先删「证明没人用」的，再动「有争议」的）。

#### 2.1.7 方向（不写数字）

- 省哪一段：`BRIDGE_ENTER`（快照）为主、`BRIDGE_EXIT`（还原）为辅。**方向：正。**
- 怎么验证：`GlScopeDiagnostics` 直接量 capture/close；然后再看 `FrameSegments` 的 `BRIDGE_ENTER/EXIT` 两段是否跟着降。判据用「这两段是否显著下降」，不看 FPS。

---

### 2.2 tuui（`com.slientrain.tuui`，1.1，Skija 0.116.8）

#### 2.2.1 它解决什么问题

把音乐 UI / 歌词覆盖层画进 MC 的主帧缓冲，**不引入第二套合成链**。

#### 2.2.2 具体做法

```java
// tuui/client/utils/rendersystem/Graphics.java:138-158（节选）
GlStateUtil.INSTANCE.save();
this.glStorePixel();
this.requireContext().resetAll();     // 每帧丢弃 Skia 图集/缓存
RenderSystem.enableBlend();
cvs.save(); cvs.scale(guiScale, guiScale);
runnable.accept(cvs);
cvs.restore();
this.requireSurface().flush();
GlStateUtil.INSTANCE.restore();
RenderSystem.disableBlend();
```

- 直画：`BackendRenderTarget.makeGL(..., mc.getMainRenderTarget().frameBufferId, GL_RGBA8)`（`:112`），**没有中间纹理、没有回读、没有 blit 回主缓冲**。
- 快照：`GlStateUtil.java:299-310` 用 **`GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS`** 当循环上限，逐单元 `glActiveTexture` + 2 次查询。
- 每帧全量重绘、每帧 `layout.arrange(...)`（`AbstractCanvas.java:126-131`），只有视口剔除（`SimpleLayout.java:117`），零 layout 缓存、逐字 measure（`Text.java:315-362`）。

#### 2.2.3 它为什么快

**它没有更快。** 它是「画得少 + 链短」：一条链省掉了 offscreen + blit 回主缓冲；一份画面只有几十个 text。文字路径和状态路径都比 AC 更糙：

- 状态：每次 `draw` 都要扫 `COMBINED` 个纹理单元（桌面 32 → 64 次查询，某些驱动更多），比 Muonium 的 12 个贵；
- 文字：每帧逐字符 measure、每次 `parseColoredText` 都 `new ArrayList`（`FontRendererKt.java:33-113`）；
- 图集：每帧 `resetAll()` 丢字形图集（`:143`）。

#### 2.2.4 对 AC 是否适用

- **「直画主 FBO」不适用**：AC 的桥是 AtomLib 的核心抽象，拆掉会影响图标导入、模糊、多端；既有调研已判「风险高、收益中」，本报告不改这个结论。
- **「每次 draw 都 save/restore」不适用**：方向相反，它比 AC 现在的边界还宽（按 COMBINED 上限扫）。
- **唯一可借的是「链短」这个事实**：AC 的 `BRIDGE_ENTER` 里做的事情比 tuui 多得多（AC 还多一层 bridge 的 5 个前置断言 + ThreadLocal + record 分配）。这属于桥的常数开销，不是本报告重点。

#### 2.2.5 前置条件

若要评估直画：需要同时解决「图标上传/模糊/多端」三处对 `DirectContext` 所有权的依赖。代价远超本报告范围。

#### 2.2.6 会不会改视觉

直画本身**不改**（tuui 与 AC 都是 CG 绘制）；但 tuui 的 `resetAll()` 每帧丢图集会导致字形重建，**观感可能变成「偶尔缺字」**（既有调研已把它列为不可搬项）。**判定：不改，但不值得抄。**

#### 2.2.7 方向

- 省哪一段：若能直画，省的是整条 offscreen 合成链（AC 现在其实**已经**是借用宿主 FBO，没有这条链，见 `SkijaRenderBridge.java:222`）⇒ **对 AC 无增量，方向：零。**
- 验证：无。这条只作为「AC 的架构并不比 tuui 差」的反证。

---

### 2.3 chatcore（`org.buhuipofangle.mintui` + `com.nhwc.chatcore`，1.0.0）

#### 2.3.1 它解决什么问题

在 ModernUI 上搭一个聊天框：**消息会一直涨，但帧不能抖**。

#### 2.3.2 具体做法

```java
// ChatBoxWidget.java:336-360（节选）
private void drainRenderQueue(int generation) {
   int rendered = 0;
   while (rendered < 3 && !this.pendingRenderItems.isEmpty()) {   // 每帧最多建 3 条
      ChatBoxWidget.ChatItem item = this.pendingRenderItems.poll();
      if (item != null) { this.renderChatItem(item); rendered++; }
   }
   ...
   if (!this.pendingRenderItems.isEmpty()) this.scheduleRenderQueue();   // 剩下的下一帧
}
```

- **建视图限流**：每帧最多 3 条（`ChatBoxWidget.java:341`），把「消息风暴」的尖峰摊到多帧。
- **整棵视图树复用**：重开聊天时 `canReuseCachedView = viewActive && ChatCore.hidingHud`，满足就 `return this.outerView`（`ChatMenu.java:532-543`），窗口尺寸变了才重建。
- **小 LRU**：表情解析 200 / 头像 URL 50 / 头像 Image 50，`removeEldestEntry` 显式淘汰（`ChatItemWidget.java:78-100`）。
- **列表不虚拟化**：`ScrollView` 全量 `addView`，消息只增不减。它稳**不是因为回收**。

#### 2.3.3 它为什么快

它自己几乎不省——**它把「每帧要碰的东西」降到了零**：视图不动就不重录，重录了也只提交脏矩形，剩下的都交给 ModernUI 的层（§2.4）。这是「把成本转移给框架」的正面案例：应用层只负责**不要每帧制造新东西**。

#### 2.3.4 对 AC 是否适用

**适用两条，都是低风险**：

1. **新消息入库限流**（每帧最多 N 条）——AC 的 `LIST 0.259ms` 与 30ms 长帧的直接对策；不改视觉（只是消息出现晚 1–2 帧）。
2. **重开面板复用视图/布局对象**——AC 每次重开是否重建布局需确认（**推测**：AC 是立即模式，大部分对象本来就不重建，收益可能小于 chatcore 的保留式框架；此项价值存疑，验证前不排优先级）。

#### 2.3.5 前置条件

限流需要一条「待渲染队列」；AC 目前是同步绘制，改造点在消息入库与滚动定位之间，要保证「滚到底」的行为不变。

#### 2.3.6 会不会改视觉

**限流：逐像素不改**（只影响新消息出现的帧序号）。**视图复用：不改**。

#### 2.3.7 方向

- 省哪一段：`LIST` 的尖峰（长帧），不是稳态。**方向：正（对 p99，不对 p50）。**
- 验证：一次性灌 200 条，看 `frame_probe` 的 max 与 >30ms 计数；滚动到底行为逐项回归。

---

### 2.4 ModernUI 3.12.0.1（`icyllis.modernui` + `icyllis.arc3d`）

> 本次已完整反编译关键类（`build/fourway-20261106/mu-decomp/`），下面的行号指向**反编译产物**，与字节码一一对应（`vf-mu.log` 无 error）。

#### 2.4.1 它解决什么问题

一个真正的保留式 UI 框架（View 树 + 布局 + 显示列表）跑在 MC 里：元素多一个量级，稳态不能随元素数线性变差。

#### 2.4.2 具体做法

**A. 每帧最多合成 1 个 quad；没有新录制就只合成**

```java
// UIManager.java:701-712
ImmediateContext context = Core.requireImmediateContext();
Pair<Recording, ImageViewProxy> frameTask = this.mRoot.swapFrameTask();
Recording recording = frameTask.getLeft();
ImageViewProxy surface = frameTask.getRight();
if (recording != null) {
   oldVertexArray = GL33.glGetInteger(GL_VERTEX_ARRAY_BINDING);   // 34229
   oldProgram   = GL33.glGetInteger(GL_CURRENT_PROGRAM);          // 35725
   context.addTask(recording); recording.close();
}
```

**B. 状态处理：不读回，直接把「下一个使用者需要的样子」写回去（缓存与真实状态成对写）**

```java
// UIManager.java:726-757（节选）——注意每一行都是 RenderSystem/GlStateManager 与裸 GL 成对
GL33.glBindSampler(i, 0);                       // 4 个采样器槽
GL33.glDisable(GL_STENCIL_TEST);                 // 2960
RenderSystem.disableScissor(); GL33.glDisable(GL_SCISSOR_TEST);
RenderSystem.defaultBlendFunc(); GL33.glBlendFuncSeparate(770, 771, 1, 0);
RenderSystem.enableBlend();      GL33.glEnable(GL_BLEND);
RenderSystem.disableDepthTest(); GL33.glDisable(GL_DEPTH_TEST);
RenderSystem.depthFunc(515);     GL33.glDepthFunc(515);
RenderSystem.depthMask(true);    GL33.glDepthMask(true);
for (int i = 3; i >= 0; i--) { RenderSystem.activeTexture(33984 + i); RenderSystem.bindTexture(0); }
RenderSystem.viewport(0, 0, width, height);
```

**C. 脏区才重录；不脏就一行都不画**

```java
// ViewRoot.java:253-272
Rect dirty = this.mDirty;
if (this.mFullRedrawNeeded) dirty.set(0, 0, this.mWidth, this.mHeight);
this.mFullRedrawNeeded = false;
if (!dirty.isEmpty()) {
   Canvas canvas = this.beginDrawLocked(width, height);
   canvas.save(); canvas.clipRect(dirty);
   canvas.drawColor(0, BlendMode.CLEAR);        // 只清脏区，靠表面保留其余像素
   dirty.setEmpty(); host.draw(canvas);
   canvas.restore(); this.endDrawLocked(canvas);
}
```

**D. 层纹理只在尺寸变化时重建**

```java
// UIManager.java:1055-1066
if ((this.mSurface == null || this.mSurface.getWidth() != width || this.mSurface.getHeight() != height)
        && width > 0 && height > 0) {
   this.mSurface = GraniteSurface.makeRenderTarget(..., false, 1, null);
}
```

**E. 文本：`TextView` 持有 `Layout`，只在需要时 `makeNewLayout`；`TextLine` 持有 `TextBlob`**

- `TextView.java:133` `private Layout mLayout;`，`:1162-1165` 只有宽/文本变化才重建；绘制直接 `mLayout.draw(canvas)`。
- `TextBlob` / `GLFontAtlas`（512 分块、`Long2ObjectOpenHashMap`）证据见既有 javap：`evidence/TextLine.javap.txt`、`evidence/GLFontAtlas.javap.txt`。

**F. 模糊：整屏 2 pass 高斯，面板自身不做 backdrop**

- 打开 Screen 时装一次 post-chain（`BlurHandler.java:87-104`）；每帧只 `PostChain.process(0)` 跑一遍（`:256-262`），只改 radius uniform（`:204-216`）。
- 面板底色是 `ColorDrawable`/`GradientDrawable`（chatcore `ChatMenu.java:1108,590`），**没有逐面板采集**。

#### 2.4.3 它为什么快

1. **稳态零重录**：没有脏区就不产生任何绘制命令，只合成 1 个 quad。元素数只影响「变化时」的那一帧，不影响稳态。
2. **状态不读回**：省掉读回的一半成本，而且读回是不可缓存、驱动内部同步的重活。它敢这么做，是因为它**在自己的 pass 结束时是这段渲染的最后一个人**——写一套已知良好状态出去，下一个使用者（MC GUI）恰好就要这个。
3. **层纹理常驻**：`GraniteSurface` 只在尺寸变化时重建，`drawColor(CLEAR)` 只清脏矩形，靠「表面内容保留」这一 GL 语义省掉全屏清屏。
4. **布局只在 `mLayoutRequested` 时跑**：`ViewRoot.java:200-244`。

#### 2.4.4 对 AC 是否适用

**拆成三条，只有前两条适用：**

1. **层缓存 / 脏区（B、C、D）— 适用，但只能「部分页面」**。AC 的面板中央是**活的毛玻璃**（背后世界在动），整页烘死会改视觉。可烘的是「chrome」：底板圆角、边缘点阵、头部/标签栏的静止部分。见 §1 ②与 §3 表。
2. **文本 layout 缓存 — 已基本具备，不必再抄**。AC 现在有 `WIDTH / PARSE / SHAPE / WRAP` 四层（`SkiaFontRenderer.java:34-56`），已经覆盖 ModernUI 的 `mLayout` + `TextBlob` 两件事。剩下的只是「查表时不要分配 key」。
3. **「不读回、只归一化」（B）— 不适用，风险高于收益**。理由：
   - ModernUI 的 pass 位于 Screen 渲染末尾，写一套 GUI 状态出去正好是下一个使用者要的；
   - AC 的桥至少有两个**宿主阶段不同**的调用点：面板（`AtomChatScreen.java:2050` `graphics.drawPanel(...)`，Screen 阶段）与通知条（`NotificationHudOverlay.java:44` `GRAPHICS.draw(...)`，HUD 阶段）。Muonium 的注释明确写了这一类形状的风险：「钩子不止在 GUI 阶段触发时，『GUI 阶段应该的样子』并不总是调用点原本的样子」（`SkijaHost.java:519-523`），而且它踩过一次——天空/云错色、主菜单糊掉、手持物贴图错乱。
   - 所以 AC 应该走「缩小快照边界」（Muonium 路线），不是「取消快照」（ModernUI 路线）。

#### 2.4.5 前置条件

- 层缓存：`Surface`（或 `Picture`+`Image`）与面板同尺寸、同色彩空间；尺寸/字号/主题/密度变化要显式失效；合成必须是 1:1（缩放会重采样）。
- 面板中央的毛玻璃必须留在层外（否则冻结背景）。

#### 2.4.6 会不会改视觉

- 层缓存：**静止帧不改**（1:1 合成，逐像素等价）；**开合动画期会改**，必须用 `layerSkippable` 门控。
- 状态归一化：**如果照抄会改**（其他 mod 的 HUD 状态被覆盖）⇒ 列为不可移植。

#### 2.4.7 方向

- 省哪一段：`DOTS` 的重放部分 + `DRAW` 里的静态填充。**方向：正（仅静止帧）。**
- 验证：三档法（A 完整 / B 只跳静态层重建 / C 旁路）+ 截图 0 diff（静止帧）；动画期单独抓 7 个进度帧对比。

---

## 3. 可移植清单

> 「适用性」高 = 收益/风险比最好且不碰视觉红线；中 = 需要前置条件或只影响某类帧；低 = 只能借思路。

| # | 技术点 | 出处（文件:行） | 对 AC 适用性 | 前置条件 | 改不改视觉 | 实施风险 | 验证方法 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 快照边界按「Skia 会动什么」裁：去掉 UBO / 索引 draw buffer / clip distance / sample mask 四类循环 | Muonium `SkijaHost.java:538-540,600-697`；AC `GlStateScope.java:145-173` | **高** | 需证明 Skia 0.143.18 不碰这四类（可用 `GlScopeDiagnostics` + 一次「漏还原」回归） | 不改 | 中（漏还原会让别的 mod 花屏；`GlStateScopeTest` 已覆盖异常路径） | `GlScopeDiagnostics` 量 capture/close；`BRIDGE_ENTER/EXIT` 是否同步下降 |
| 2 | 纹理单元扫描从 32 → 12 | Muonium `UNITS = 12`（`:540`）；AC `GlStateScope.java:179-186` | **高** | 确认 AC 的绘制最多用到几个单元（Skia 用 1–2 个 + 一个 scratch） | 不改 | 低（超限才错，可在 debug 下断言最高单元 < 12） | 同 #1 |
| 3 | 用「失效宿主旁路缓存」代替「还原它」 | Muonium `BufferUploader.reset()`（`:834`） | **中** | 需要确认 AC 侧对应的缓存（`BufferUploader` 已有 static `reset`） | 不改 | 低 | 连续 300 帧后切回原版 GUI，看有无 `GL_INVALID_OPERATION` |
| 4 | 静态 chrome 烘成 GPU 纹理（层缓存） | ModernUI `ViewRoot.java:189-275`、`UIManager.java:1055-1095,701-712`；AC 已有 `DotTexture.HALO_IMAGES`（`:44-64`）作为同构先例 | **高** | 层与面板同尺寸/同色彩空间；`layerSkippable` 门控；主题/密度/字号显式失效 | **静止帧不改；动画期需门控** | 中（失效漏一处就是「花屏」；内存 +数 MB） | 三档法 + 静止帧截图 0 diff + 动画 7 进度帧对比 |
| 5 | 只重录脏区、不脏只合成 | ModernUI `ViewRoot.java:253-272` | **低**（AC 是立即模式，没有「重录」概念，只有「不画」） | 需要把整帧绘制拆成「静态层 + 动态层」两段，等价于 #4 的延伸 | 不改（若能保证漏标脏就退化为全画） | 高（漏标脏 = 画面卡住） | 静止 10s 抓帧间隔分布 + 逐项开关动画 |
| 6 | 消息入库限流（每帧最多 N 条） | chatcore `ChatBoxWidget.java:336-360` | **中** | 需要待渲染队列；滚动到底语义不变 | 不改 | 低 | 灌 200 条看 max/长帧；滚动回归 |
| 7 | 文本 layout/TextBlob 缓存 | ModernUI `TextView.java:133,1162-1165`；AC 已有四层（`SkiaFontRenderer.java:34-56`） | **低（已具备）** | — | 不改 | — | `shapedPlanBuilds` / `wrapCacheBuilds` 稳态应为 0/帧 |
| 8 | 跨帧字形图集 | ModernUI `GLFontAtlas`（512 chunk） | **低（已具备）** | AC 依赖 Skia 内部图集且不 `resetAll` | 不改 | 低（**不要**引入 `resetAll`） | 文本密集页的纹理上传计数 |
| 9 | 每帧分配清理：时钟 `String.format`、`ShapeKey`、`FramebufferTarget` | AC `ShellHeader.java:106-107`、`SkiaFontRenderer.java:652`、`FramebufferTarget.java:9-15` | **中** | 缓存「每分钟」的时钟字符串；查表用无分配 key | 不改 | 低 | JFR allocation profile（已有 `appearance-alloc-*` 样本格式） |
| 10 | 模糊采集限频 30Hz（D 档） | Muonium `GlassBackdrop.java:180-186,332-360`；AC 已实现 `PanelBlurRenderer.renderReuse:220` | **中（已实现）** | 已具备；动画期禁复用 | 静止帧不改；相机移动时有 ≤33ms 滞后 | 中 | `blur_cadence` 行（已有） |
| 11 | 模糊多档槽 + 每代重建预算 | Muonium `GlassBlurLevels.java:26-37` | **低** | AC 只有一个模糊半径 | 不改 | 低 | 不适用（AC 无多档需求） |
| 12 | 视图树复用（重开面板） | chatcore `ChatMenu.java:532-543` | **低** | AC 是立即模式，对象本来就不重建 | 不改 | 低 | 首开/重开的长帧对比 |

---

## 4. 明确不可移植清单

| 技术点 | 出处 | 抄了会怎样 |
| --- | --- | --- |
| **开合动画期关掉液态玻璃、改画实色** | Muonium `GlassLook.java:151-154`（`PlayerIslandTransition.isRunning()` → `return false`，调用方画「假玻璃」） | **直接改视觉**。AC 的面板在 0.94→1.0 缩放期间会从「磨砂玻璃」变成「实色板」，肉眼可见的闪变。与「视觉不动」红线冲突。 |
| **借用落后 8 个采集代的旧模糊** | Muonium `GlassBackdrop.java:592-595`、`STALE_BORROW_CAPTURES = 8`（`:626`），作者自述落后约 264ms | **相机移动时可见**：玻璃后面的世界会「定格 1/4 秒」。Muonium 能接受是因为玻璃只是装饰；AC 的毛玻璃是视觉主体。 |
| **冻结背景 / 暂停时冻结采集** | Muonium `SkijaHost.java:128-142`（用 `partialTick` 当帧号，暂停时背景跟着冻结） | AC 暂停时背景本来就静止，此项**无收益**；但若误用在非暂停场景就是「背景不动」。 |
| **每帧 `context.resetAll()`** | tuui `Graphics.java:143` | 丢字形图集 → 文字重建/上传，**可能是「偶发缺字」**；AC 现在不 reset，别引入。 |
| **按 `GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS` 扫纹理单元** | tuui `GlStateUtil.java:299-310` | 状态开销反而变大（桌面 32、某些驱动 96），与目标相反。 |
| **「不保存、只把状态设成已知良好」（ModernUI 路线）** | ModernUI `UIManager.java:713-760` | AC 的桥有多个调用点（面板 / 通知 / 图标 `withContext`），把宿主状态强设成 GUI 值会**污染其他 mod 的 HUD**，Muonium 的注释已经把这条踩过的坑写死了（`SkijaHost.java:517-527`）。 |
| **逐面板 backdrop 模糊**（反面：ModernUI 不做） | ModernUI 面板用 `ColorDrawable`（chatcore `ChatMenu.java:1108`） | 抄「不做」= 关掉毛玻璃 = 改视觉；抄「做」= AC 现状。此项只作为**成本解释**，不是建议。 |
| **每帧全量 `layout.arrange` + 逐字符 measure** | tuui `AbstractCanvas.java:107-153`、`Text.java:315-362` | 文字路径变贵一个量级，且会改排版结果（逐字符 measure 与整段 shaping 在连字/组合字符上不一致）。 |
| **2× 超采样文字** | tuui `FontRendererKt.java:183,196` | 视觉更锐（= 改了），填充率 ×4。 |
| **恢复 `BlurMotionGate`** | 仓库红线（`NotificationRenderWiringTest` 断言禁止） | 测试直接红。**本报告任何条目都不依赖它。** |

---

## 5. 给主代理的建议

**如果只做一件事：做 #1（缩小 `GlStateScope` 的快照边界）。**

理由按权重排：

1. **它是唯一「段大 + 不改视觉 + 边界可证明」的交集**。`BRIDGE_ENTER+EXIT = 0.716ms` 是全仓最大单块 CPU（e3 §4.1），而这两段的起点终点被 `SkiaGraphics.java:56-59` 钉死，改动的影响面完全可控。
2. **同行给的边界是现成的**：Muonium 用同一套 Skia（0.116/0.143 同代）在同一类宿主里跑出来的 22+24 项清单，可以直接当作「候选裁剪集」，再用 `GlScopeDiagnostics` 一条条证伪。
3. **它的失败模式是「可观测」的**：漏还原 → MC 画面错色/花屏，立刻能看见；不像层缓存漏失效那样「偶尔卡住一帧」。

**具体落地顺序（建议）**：

1. 先做 **#2（纹理单元 32 → 12）**：改动最小，且能立刻验证「快照耗时是否随调用数线性变化」这个前提。如果这一步 `BRIDGE_ENTER` 没动，说明成本不在调用数上，后面所有推论都要推翻。
2. 再做 **#1 的四类循环裁剪**（UBO / 索引 draw buffer / clip distance / sample mask），每次只删一类，删完跑 `GlStateScopeTest` + 一次长时对局。
3. **#3（`BufferUploader.reset`）** 跟着 #1 一起做，因为删掉部分还原项之后，旁路缓存失配的概率会上升。
4. 第二步（若还有预算）才做 **#4 静态 chrome 烘层**——它收益可能更大，但需要新的失效契约与截图回归，周期长。

**不要做的**：任何形式的「模糊时序列为主答案」（已实现且动画期不能用）、任何「动画期关玻璃」（红线）、任何「取消快照只归一化」（多调用点）。

---

## 6. 一句话

> 最值得抄的那一件：**Muonium 的 `GlSnapshot` —— 把每帧的 GL 快照从「整份驱动状态」缩到「Skia 真的会动的那 22+24 项」，并用 `BufferUploader.reset()` 代替去还原宿主自己的旁路缓存。**

---

## 附：不确定性

| 项 | 状态 | 怎么证实 |
| --- | --- | --- |
| Skia 0.143.18 是否真的不碰 UBO / 索引 draw buffer / clip distance / sample mask | **推测**（依据 Muonium 同代 Skia 的实测清单 + Skia GL 后端结构） | 在 `GlStateScope` 里逐类打「还原前后是否相等」的断言，跑一局长时对局 |
| ModernUI 层缓存的真实稳态收益 | **未测**（只读代码） | 需要真机 A/B，本轮禁止 |
| AC 的通知条是否也走同一个桥 | **已确认**：`NotificationHudOverlay.java:44` 调 `GRAPHICS.draw(...)`，与面板的 Screen 阶段不同 | — |
| `DOTS 0.598ms` 里命令数 vs 填充率的占比 | **未知** | 三档法：只跳过 dot 的 Picture 回放 / 只跳过 halo Image / 全画 |
| 反编译产物与字节码的等价性 | 方法体可信；变量名/注解可能失真 | 关键结论已用 javap 交叉核对（既有 `evidence/`） |

---

## 附：D 档口径说明（避免误读）

`docs/verification/2026-11-06-mode-d-results.md:38` 原文：**「D−B = +0.009 ms：D 把模糊的帧间隔成本收得几乎一干二净」**，即 **D ≈ B**（30Hz 复用把每帧模糊的成本收到接近零），抖动全距也从 1.594 收敛到 0.546（`:40`）。

也就是说：**限频在帧间隔上是有效的**，与「限频几乎不省」相反。本报告仍然不把它当主答案，理由改为三条，与收益无关：

1. 它已经在树里（`PanelBlurRenderer.renderReuse:220`），不是新增空间；
2. `BRIDGE 0.716ms` 比 `A−B ≈ 0.19ms` 大一个量级；
3. 它的复用条件里「动画期不复用」是硬约束（`2026-11-06-mode-d-muonium-cadence.md:49`），AC 的面板开合本身就在动画期。
