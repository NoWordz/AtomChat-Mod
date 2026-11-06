# tuui / chatcore 渲染实现只读调研（2026-11-06）

> 只读调研。未修改 tuui / chatcore / ModernUI / AtomChat 任何源码，未启动游戏，未部署，未 commit。
> 反编译产物只写到 `D:/AtomChat-Main/build/tuui-research-20261106/`。
> 本文所有 `file:line` 指向**反编译产物**的行号（不是作者原始源码的行号），并在必要时给出 `类#方法` + javap 字节码偏移。

---

## 0. 结论摘要（先看这四行）

1. **聊天框不在 tuui 里。** tuui（`com.slientrain.tuui`，displayName "AllMusic Client Mod"）整个 mod 只有 709 个 class（反编译合并成 186 个 .java），零个 `ChatScreen` / `ChatComponent` / `EditBox` 引用；它接管的是 `ForgeGui#render` 的 HEAD，用来画歌词覆盖层和音乐/主菜单界面。单独装 tuui 看不到聊天框是**因为聊天框本体根本不在这个 jar 里**，不是缺资源也不是 config 开关。
2. **聊天框在 `chatcore-1.0.0 (1).jar`**（modId `chatcore` + 内嵌 `mintui`），package `org.buhuipofangle/mintui`，基于 **ModernUI 3.12.0.1**（`icyllis.modernui`，Arc3D/Granite 引擎），不是 Skija，也不走 MC 的 `GuiGraphics` 立即模式。证据：`mods.toml` 声明 `modId="modernui", versionRange="[3.11.0,)", mandatory=true`，且 mixin 直接打在 `icyllis.modernui.mc.forge.SimpleScreen` 上。
3. **两套完全不同的稳定策略**：
   - tuui：「往主 FBO 直画 + 每帧全量重绘 + 零 layout 缓存」。它稳是因为**画的东西少**（只有歌词覆盖层 / 打开着的一个界面），不是因为缓存聪明。
   - chatcore(ModernUI)：「保留式视图树 + 布局只算一次 + 脏区才重录 + **整页渲染到一张 GPU 层纹理，每帧只合成 1 个 quad**」+ 字形图集常驻。这才是「元素多一个量级还稳」的真正机制。
4. 对 AtomChat 最有价值的可移植点按收益排序：**整页/整面板烘成 GPU 层 → 脏区失效驱动重录 → 行级 layout 缓存 + TextBlob 复用 → 消息列表构建限流 → 模糊降到 2 pass 可分离高斯**。前 4 条不改视觉；第 5 条会改视觉（必须标注）。

---

## 1. 产物清单

### 1.1 输入 jar（原件，只读，未复制进任何实例目录）

| 用途 | 路径 | sha256 | 说明 |
|---|---|---|---|
| tuui | `D:/Myworld/TUMC/TUMC/.minecraft/mods/[Forge-1.20.1]Tuui-1.1-shadow (3).jar` | `c2779eca593a1e88f39cbd01e59cc7b953ca111e304fc7d98a1e9f951dfd4d94` | 36,117,878 B；Forge 1.20.1 + kotlinforforge；shaded Skija **0.116.8**（`META-INF/maven/io.github.humbleui/skija-shared/pom.properties`） |
| chatcore | `D:/Myworld/TUMC/TUMC/.minecraft/mods/chatcore-1.0.0 (1).jar` | `50ae6dffdd27b1209cbb3ca0bcf8ffc5afd6c2b4671d14b633c03a897e7915a7` | 含两个 modId：`chatcore`、`mintui`；98 个 class |
| ModernUI | `D:/Myworld/TUMC/TUMC/.minecraft/mods/[现代化 UI] ModernUI-Forge-1.20.1-3.12.0.1-universal.jar` | `4a0e402aef40dd6d8184d950ad76f8b52b4bf3d601f3abe4f7190139242f063e` | chatcore 的硬依赖；只做 `javap` 字节码阅读，未反编译全库 |

交叉核对：`/d/Myworld/.minecraft/versions/1.20.1-main/mods/[Forge-1.20.1]Tuui-1.1-shadow (3).jar` 的 sha256 与上表**完全相同**，即两个实例是同一份 tuui。

运行期旁证（只读日志）：`D:/Myworld/TUMC/TUMC/.minecraft/logs/debug.log` 记录 `Found valid mod file ... with {tuui} mods - versions {1.1}` 与 `Found valid mod file chatcore-1.0.0 (1).jar with {chatcore,mintui} mods`；`config/chatcore.properties` 内容为 `useVanillaChat=false`（即聊天键被 chatcore 接管）。

### 1.2 反编译工具与产物

- 工具：**Vineflower 1.10.1**（`C:/Users/NIUQU/.gradle/caches/modules-2/files-2.1/org.vineflower/vineflower/1.10.1/.../vineflower-1.10.1.jar`），JVM `21.0.11`。
- tuui 用 `--kt-decompile-kotlin=false`（Kotlin→Java），否则 Kotlin writer 会把 setter 写成 `<set-?>`（`/d/tmp/tuui` 里那份既有反编译产物就是这样坏掉的，故本次重做）。

| 产物 | 路径 | 规模 |
|---|---|---|
| tuui 反编译源码 | `build/tuui-research-20261106/tuui-decomp/` | 186 个 .java |
| chatcore 反编译源码 | `build/tuui-research-20261106/chatcore-decomp/` | 52 个 .java（含 1 个合成类） |
| Vineflower 日志 | `build/tuui-research-20261106/vf-tuui.log`、`vf-chatcore.log` | — |
| javap 证据 | `build/tuui-research-20261106/evidence/*.javap.txt` | 9 份（BlurHandler / UIManager / UIManager$ViewRootImpl / ViewRoot / SimpleScreen / GLFontAtlas / TextLayoutEngine / TextLine / TextView） |
| 解包类 | `build/tuui-research-20261106/work/` | `tuui-all/`（全 jar）、`chatcore-classes/`、`modernui-classes/`（仅关键类） |

> 区分正本与反编译：tuui 与 chatcore 是**反编译产物**（Vineflower 从字节码重建，方法体可信、变量名/注解可能失真）；ModernUI 是**字节码正本**（直接 `javap -p -c` 读，没有反编译中间层）。

---

## 2. 归属结论：聊天框在 chatcore，不在 tuui（问题 1）

### 2.1 tuui 里没有任何聊天 UI

- mixin 配置 `jar/mixins.tuui.json` 的全部条目：`MixinGui`、`MixinKeyboard`、`MixinMinecraft`、`MixinMouse`、`PostChainMixin`、`MixinCommentWorldRenderer`、`MixinKeyModifier`。
- `tuui-decomp/tuui/client/mixin/MixinGui.java:12-25`：唯一注入点是 `ForgeGui#render` 的 **HEAD**，只投递一个 `RenderEvent2D` 事件。
- 全库检索 `ChatScreen|ChatHud|EditBox|TextField|ChatComponent` → **0 命中**（命令：`grep -rl -E "ChatScreen|ChatHud|EditBox|TextField|ChatComponent" tuui-decomp`）。
- `ClientChannel.java:40-45` 里确实有字符串 `"chat_panel"`，但那是 Forge `SimpleChannel` 的**网络频道名**（传音乐数据分片），不是聊天界面。
- `MixinCommentWorldRenderer.java:5,13` 注入的是 `forge.cn.zbx1425.worldcomment.render.CommentWorldRenderer`（WorldComment 这个第三方 mod 的世界内评论气泡），与聊天框无关。

结论：tuui 的可见 UI = 音乐播放器 / 主菜单 / 每日奖励 / 歌词覆盖层；**没有聊天框本体，也没有聊天框资源**。单独安装看不到聊天框是必然。

### 2.2 聊天框在 chatcore（+ ModernUI）

- `chatcore-decomp/.../META-INF` 对应 `mods.toml`：`modId="chatcore"`（authors=nhwc）与 `modId="mintui"`，并声明
  `modId="modernui", mandatory=true, versionRange="[3.11.0,)", ordering="AFTER", side="CLIENT"`。
- `chatcore-decomp/com/nhwc/chatcore/mixin/MixinSimpleScreen.java:21-25`：`@Mixin(targets = {"icyllis.modernui.mc.forge.SimpleScreen"}, remap = false)` —— 直接改 ModernUI 的通用界面壳。
- `chatcore-decomp/com/nhwc/chatcore/client/KeyBindingHandler.java:56-60`：每 tick 判断 `!ChatCore.useVanillaChat && mc.screen == null && mc.options.keyChat.consumeClick()` → `ChatManager.ClientSideEvents.openChat()`。即**聊天键被接管**，打开的是 ModernUI 的 `SimpleScreen` + `ChatMenu` Fragment。
- `chatcore-decomp/org/buhuipofangle/mintui/fragment/ChatMenu.java:78-80`：`HEADER_TITLES = {"世界聊天窗","附近聊天窗","私信聊天窗","系统消息","黑名单"}`；`:572-582` 组装 `LinearLayout outer → leftHalf → contentArea → chatBoxWidget.build()`。
- 资源：`assets/mintui/textures/emoji.png`、`assets/mintui/sounds/red_packet/*.ogg`。聊天框的表情、红包都属于它。

**为什么「必须用 tumc 整合包」才看到**：因为 chatcore 只存在于 TUMC 的 mods 目录（该整合包内含 134 个 mod），而不在 `1.21.1-CCB` 实例里；tuui 与 chatcore **互不依赖**（tuui 的 `mods.toml` 只依赖 forge / minecraft）。所以不是「缺另一个 mod 的 API」，而是**两个 mod 各管各的：tuui 管音乐 UI，chatcore 管聊天**。

---

## 3. tuui 的渲染实现（问题 2–6、8）

### 3.1 渲染栈（问题 2）

- **Skija 0.116.8**（shaded 进 jar，`META-INF/maven/io.github.humbleui/skija-*/pom.properties`），Java 侧 Kotlin。
- 入口：`MixinGui`（`ForgeGui#render` HEAD）→ `EventManager.callEvent(RenderEvent2D)` → 监听者 `LyricRender`（`tuui/client/module/LyricRender.java:64-73`）与 `TexturePreloaded`（`:32-59`）。
- 打开界面时走 `SkijaScreen extends Screen`（`tuui/client/utils/screen/SkijaScreen.java:26,57-70`），`m_88315_` 里先 `graphics.checkFrameBufferId()`、必要时 `createSurface()`，可选全屏模糊，然后由子类 `graphics.draw(...)`。

### 3.2 关键：直接把 Skia 画进 MC 的主 FBO（无中间纹理、无回读）

`tuui/client/utils/rendersystem/Graphics.java`：

- `:112` `BackendRenderTarget.makeGL(width, height, 0, 8, mc.getMainRenderTarget().frameBufferId, GL_RGBA8)` —— **绑的就是 Minecraft 主帧缓冲的 FBO id**。
- `:113-120` `Surface.wrapBackendRenderTarget(...)`，`BOTTOM_LEFT` + `RGBA_8888` + `ColorSpace.getDisplayP3()`。
- `:130-164` `draw(Consumer<Canvas>)`：`save GL state → glStorePixel() → context.resetAll() → enableBlend → canvas.save() → canvas.scale(guiScale) → 业务绘制 → canvas.restore() → surface.flush() → restore GL state`。

这一段是「为什么稳」的第一层原因：**没有 offscreen surface、没有整幅纹理拷贝/回读、没有 blit 回主缓冲**，绘制命令直接落在 MC 正在用的 FBO 上。对比 AC 的 `BRIDGE_ENTER+EXIT 0.76ms`（AtomLib 的独立 DirectContext + surface 桥，`shared/src/main/java/com/atom/chat/render/SkiaSupport.java`、`.work/atomlib-src/com/atom/lib/client/render/SkijaRenderBridge.java`），tuui 把这层成本整个省掉了。

注意（必须说清）：`:143` 每帧也调了 `context.resetAll()`。Skia 的 `resetAll` 会 flush + 释放 GPU 缓存资源，**包括字形图集**。所以 tuui 并不是靠「图集常驻」赢的（详见 3.5）。

### 3.3 每帧做什么（问题 5）

`tuui/client/component/AbstractCanvas.java:107-153` 是每帧固定路径：

1. `canvas.save()`
2. 若 `clip`：`CanvasExtensionKt.clip(...)` 圆角裁剪（`:112-122`）
3. `layout.updateMouse(...)`（`:125`）
4. `layout.arrange(new Placement(...), horWheel, verWheel)`（`:126-131`）——**每帧重新摆位**
5. `viewport.update(...)`（`:132`）
6. `layout.render(canvas, onlyDrawVisible ? viewport : null, opacity)`（`:137`）
7. 滚轮命中/动画推进（`:142-151`）
8. `canvas.restore()`

`tuui/client/component/layout/SimpleLayout.java:90-122`：

- `:100-110` 每帧新建一个 `ArrayList` 过滤 `visibleCallback()`（**每帧分配**）；
- `:117` 只对落在 viewport 上下界内的子组件调 `render` —— **有视口剔除**，这是 tuui 唯一的「少画」手段。

**没有** 脏区，**没有** 图层缓存，**没有** 「静止不重绘」：只要这个界面开着，每帧全量重画（搜索 `Picture|saveLayer|Bitmap|makeImageSnapshot` 在 tuui 全库仅 1 处 `drawImageRect`，无 Picture/离屏烘焙）。

### 3.4 文字：无 layout 缓存，靠 Skia 每次都在算（问题 3 —— 本次最关心的对标点）

结论先说：**tuui 没有按字符串/按行/按 glyph 的 layout 缓存，也没有 `Paragraph`/`TextLine` 复用，也没有异步栅格化。** 它每帧重新 measure、重新 `drawString`。

证据：

1. 字体对象有缓存，粒度是「字体名 + 字号」：
   - `tuui/client/autoregister/font/FontManager.java:43` `LinkedHashMap<String, Font> fontCache`；
   - `:136-169` 以 `name + ":" + size` 为 key 的 `getOrPut`，缓存的是 `io.github.humbleui.skija.Font`（内含 Typeface，不含任何 layout）。
2. 每次 `Text.render` 都重新排版：
   - `tuui/client/component/text/Text.java:165-276`；
   - `:171` 算 `maxWidth`；
   - `:189-198` `WRAP` + overflow 时调用 `textOverflow(maxWidth)`；
   - `:315-362` `textOverflow` 里**逐字符** `FontRendererKt.getStringWidth(font, char)`（`:334`）；
   - `:226-231` 每行再 `getStringWidth(font, text)` 一次；
   - `:282/286/301` 省略号分支同样逐字符 measure。
3. `FontRendererKt.java:204-217` `getStringWidth` = `parseColoredText` + `Font.measureTextWidth`；`:33-113` `parseColoredText` **每次调用**都 `new ArrayList` + `new TextSegment` + `new StringBuilder`（每帧、每行、每段都分配）。
4. 绘制：`FontRendererKt.java:175-202` `drawText`：
   - `:183` `canvas.scale(0.5f, 0.5f)`，`:196` 坐标先乘 2 —— **2× 超采样绘制**（视觉上更锐，代价是 4 倍填充率与 2× 尺寸的字形栅格）；
   - `:196` `canvas.drawString(...)`（每次调用 Skia 重新 shape）；`:197` 段间推进又 `measureTextWidth`。

所以 tuui 的文字路径在「元素多」时**不会**稳：它在菜单里只有几十个 `Text` 才没暴露问题。**不能用它来证明「Skia 文字可以每帧重排」**。

### 3.5 字形图集（问题 3 的补充）

- 代码里没有自建图集；用的是 Skia `DirectContext` 内部的 glyph atlas。
- 但 `Graphics.java:143` **每帧 `context.resetAll()`**。按 Skia 的语义，`resetAll` = flush + 释放缓存的 GPU 资源（字形图集属于其中之一）；如果该语义在 Skija 0.116.8 下成立，tuui 实际上每帧都在重建/重传字形，只是文字量小看不出来。
  （**推断，未实测**：要证实需在 tuui 菜单里统计每帧的纹理上传次数，或对比去掉 `resetAll` 前后的帧时。）
- 结论：**tuui 不是「字形图集复用」的正面样本**；正面样本是 ModernUI（见 4.3）。

### 3.6 背景 / 模糊 / 玻璃（问题 4）

- 模糊入口只有一处：`SkijaScreen.java:65-69`，`blur == true` 时调 `CanvasExtensionKt.drawRoundedBlur(new PoseStack(), 0,0,w,h,1.0f)`。
- `CanvasExtensionKt.java:55-77`：`Shaders.blur` 单 pass，uniform `radius / uLocation / uSize / Brightness / Quality / InputResolution`（`Shaders.java:147-157`）。
- `BlurProgram.java:61-105`：`beginBlur` 把主 FBO `glBlitFramebuffer` 到 temp（`:72-75`）；`endBlur` 再把主 FBO blit 到 input（`:90-92`）、把 temp blit 回主 FBO（`:94-95`），最后 `drawFullscreenQuad` 采样 input 覆盖上去（`:101`、`:122-132`）。
- 即 **1 个可分离单 pass + 3 次全屏 blit**，而且**没有背景采集降分辨率**：`TextureTarget(currentWidth, currentHeight, ...)`（`:48,54`）是 1:1 全分辨率。
- 只在「界面自己声明 `blur=true`」时用；游戏 HUD、歌词不模糊。
- 没有「玻璃／噪点／色散」等多层材质，只有纯色/渐变 + 圆角 + `MaskFilter.makeBlur` 做的发光（`Text.java:241-249`、`CanvasExtensionKt.drawCircleWithShadow`）。

### 3.7 帧率节奏（问题 6）

- 没有自建 vsync / 帧上限 / 固定步长；完全跟随 MC 的 `framerateLimit`。
- 动画时间基准：`MixinMinecraft.java:28-39` 在 `runTick` HEAD 用 `System.currentTimeMillis()` 差分写入 `AnimatorKt.deltaTime`（**墙钟**，不是固定步长）。
- 没有「静止不重绘」：界面开着就每帧重画。
- 有一个「首帧预热」：`TexturePreloaded.java:54-58` 在 `frameTick <= 1` 时把主菜单先渲染一次（`AdaptiveMenu.m_88315_`）并 `cancelEvent()`，用来把图片/字体提前烘进缓存。

### 3.8 有没有点阵/装饰纹理（问题 8）

- **没有点阵（dots）绘制**。全库检索不到 dot 网格/instancing/顶点缓存。
- 最接近的装饰是 `MaskFilter.makeBlur` 发光（`Text.java:243-245` 的 `GlowEffect` 分支**每帧新建 `Paint` 和 `MaskFilter`**）与 `drawRoundedShadow` 系列——这些是反面教材，不是可搬点。
- 对 AC 的 `DOTS 0.744ms`：tuui **给不出任何直接可搬的做法**；能借鉴的只有「把静态装饰烘进整页层」这个思路，而它的实现样本在 ModernUI（见 4.5）。

---

## 4. 聊天框的渲染实现（chatcore + ModernUI 3.12，问题 2–6）

> 这里是用户真正看到的「聊天框」。ModernUI 无源码，以下结论全部来自 jar 内字节码（`javap -p -c`，证据文件在 `build/tuui-research-20261106/evidence/`）。

### 4.1 渲染栈（问题 2）

- ModernUI 3.12.0.1，`icyllis.modernui` + `icyllis.arc3d`（Granite 渲染器）。
- 界面壳：`icyllis.modernui.mc.forge.SimpleScreen`（`Screen` 子类，实现 `MuiScreen`）。
- 每帧：`SimpleScreen#m_88315_`（`evidence/SimpleScreen.javap.txt`，offset 21–35 调 `BlurHandler.drawScreenBackground`，offset 56–65 调 `UIManager.render`）。
- 控件：`ScrollView` / `LinearLayout` / `TextView` / `EditText`（`ChatMenu.java:35, 582, 1105-1325`），而不是 MC 的 `GuiGraphics` 立即模式。

### 4.2 每帧做什么：整页一张 GPU 层 + 脏区才重录（问题 5，最重要）

证据链（按执行顺序）：

1. `UIManager$ViewRootImpl#endDrawLocked`（`evidence/UIManager$ViewRootImpl.javap.txt`）：`Canvas.restoreToCount` → `Core.requireUiRecordingContext().snap()` 得到 `Recording`（显示列表）→ 存入 `mLastFrameTask`（并关掉上一份）。
2. `UIManager#render`（`evidence/UIManager.javap.txt` offset 19–99）：`swapFrameTask()` 取出 `(Recording, ImageViewProxy)`；若 `Recording != null` 才 `ImmediateContext.addTask(recording)`，然后 `submit()`。**没有 Recording 时什么也不提交**。
3. `UIManager#render`（offset 331 起）：把 `ImageViewProxy.getImage()`（`GLTexture`，即 ModernUI 自己的离屏 surface）用 MC 的 blit shader 作为**一个全屏 quad** 贴到主帧缓冲。
4. `ViewRoot#performTraversal`（`evidence/ViewRoot.javap.txt` offset 385–495）：`if (mDirty.isEmpty() && !mFullRedrawNeeded) skip`；否则 `beginDrawLocked` → `canvas.clipRect(mDirty)` → `canvas.drawColor(0, CLEAR)`（**只清脏区**）→ `View.draw(canvas)` → `endDrawLocked`。

即：**静止时零重录、零绘制，只把上一帧的整页纹理再合成一次；有变化时只重录、只重画脏矩形。** 这就是「元素多一个量级但稳态不陡降」的核心。

### 4.3 文字：布局只算一次 + TextBlob + 字形图集（问题 3）

- `TextView` 有 `private Layout mLayout`、`mHintLayout`、`mSavedLayout`，`makeNewLayout(...)` 只在 `onMeasure` / 文本或宽度变化时调用；`onDraw` 直接 `mLayout.draw(canvas)`（`evidence/TextView.javap.txt` 字段与方法表）。
  → **聊天条目的换行/测量只发生一次**，之后每帧只是把缓存的 `Layout` 画出来。
- `TextLine` 持有 `icyllis.arc3d.sketch.TextBlob$Builder mTextBlobBuilder`，绘制走 `drawRun(...)` → `Canvas.drawTextBlob`（`evidence/TextLine.javap.txt`）。
  → 字形位置被烘成不可变 **TextBlob**，不是每帧重新 shape。
- `GLFontAtlas`：`public static final int CHUNK_SIZE = 512`；`Long2ObjectOpenHashMap<GLBakedGlyph> mGlyphs`；`stitch(...)`、`resize()`、`compact()`（`evidence/GLFontAtlas.javap.txt`）。
  → 真正的**字形图集**：按 `long` glyph key 缓存，512 一块分块增长、支持压实。这个图集跨帧常驻（ModernUI 不 reset context）。
- `TextLayoutEngine` 还有第二套（给 MC 原生 `Component`/`FormattedText` 用的）布局缓存：`mVanillaCache` / `mFormattedCache` / `mComponentCache`，配 `sCacheLifespan` 按 tick 淘汰（`evidence/TextLayoutEngine.javap.txt` 字段表 + `onEndClientTick` 的 `removeIf`）。
- 应用层也做了小缓存：`ChatItemWidget.java:78-100` 三个 `LinkedHashMap(accessOrder=true)` —— 表情解析结果（上限 200）、头像 URL（50）、头像 `Image`（50），`removeEldestEntry` 显式 LRU。

### 4.4 背景 / 模糊 / 玻璃（问题 4）

- 屏幕级模糊：`UIManager#onScreenChange` → `BlurHandler.blur(screen)`（**每次切界面一次**，`evidence/UIManager.javap.txt` offset 0–4）。
- 每帧只做两件事：`drawScreenBackground` 里若 `mBlurring` 则 `PostChain.process(0)`（`evidence/BlurHandler.javap.txt` offset 184–236），以及 `onRenderTick` 做半径淡入插值（不重采集）。
- 后处理是 **2 pass 可分离高斯**：`assets/minecraft/shaders/post/modern_gaussian_blur.json`（pass1 水平 `BlurDir=[1,0]`，pass2 垂直 `[0,1]`），全分辨率、target=swap/main。
- 面板本身**不做模糊**：`ChatMenu` 的底色全是 `ColorDrawable` / `GradientDrawable`（`:1108` 输入条 `rgb(245,240,237)`、`:590` 头部 `argb(135,58,58,58)`、`:1618` 红包面板 `argb(248,31,31,33)`），没有 backdrop-filter 那类逐面板采集。

### 4.5 列表与消息（问题 5 补充）

- `ChatBoxWidget.java:336-380`：新消息进 `pendingRenderItems`，用 `chatBox.post(...)` 调度，**每帧最多 `renderChatItem` 3 条**（`:341` `while (rendered < 3 ...)`），不足则下一帧继续。
  → 消息风暴时把「建视图 + 排版」的尖峰摊平到多帧，避免单帧长帧（这正好对应 AC 的 30ms 长帧问题）。
- `ChatMenu.java:532-543`：再次打开聊天时**复用整棵视图树**（`canReuseCachedView = viewActive && ChatCore.hidingHud`，条件满足直接 `return this.outerView`），窗口尺寸变化才重建。
- 列表**没有虚拟化**（`ScrollView` + 全量 `addView`），消息只增不减（`ChatManager` 未见历史上限）。所以它「稳」不是因为回收，而是因为**每帧不碰这些视图**（4.2 的层缓存）。

### 4.6 帧率节奏（问题 6）

- 没有自建 vsync/帧上限；重绘由 `ViewRoot` 的 `Choreographer` + `invalidate` 驱动，空闲时 `performTraversal` 不画（4.2）。
- `BlurHandler` 有 `sFramerateInactive` / `sFramerateMinimized` 两个「窗口失焦/最小化时降帧」的开关（`evidence/BlurHandler.javap.txt` 字段表），属于省电策略，不是稳态路径。

---

## 5. 与 AtomChat 的逐项对照（问题 7）

> AC 现状列只写我这次**读源码确认过**的事实；性能数字沿用主代理的真机审计，不重测。

| 维度 | AtomChat 现状（证据） | tuui 做法（证据） | chatcore/ModernUI 做法（证据） | 可移植性判定 |
|---|---|---|---|---|
| 绘制目标 | AtomLib 持有唯一 `DirectContext` + `Surface`，AC 通过桥绘制（`shared/.../SkiaSupport.java`、`.work/atomlib-src/.../SkijaRenderBridge.java`）；桥有 ENTER/EXIT CPU 成本 0.76ms | 直接 `wrapBackendRenderTarget` 到 MC 主 FBO，零中间层（`Graphics.java:112-120`） | 画到自己的离屏 `GraniteSurface`，再作为 1 个 quad 合成（`UIManager#render` offset 331+） | **需前置条件**（改桥的架构，不是小改）；风险高，收益中 |
| 每帧重绘范围 | 全量重绘（面板旁路实验 C 档才归零） | 全量重绘 + 视口剔除（`AbstractCanvas:107-153`、`SimpleLayout:90-122`） | 脏矩形 + 显示列表；静止时只合成层（`ViewRoot#performTraversal` 385-495） | **可直接搬（分阶段）**：先做「静态层烘一次」，再做脏区；不改视觉 |
| 文字 layout | 已有 string→width 缓存与 `ShapedPlan`/TextBlob 缓存（`SkiaFontRenderer.java:105-164, 652-691`），但每次绘制仍走自己的 draw 路径 | 无 layout 缓存，每帧逐字符 measure（`Text.java:315-362`） | `TextView.mLayout` 只算一次；`TextLine` 复用 `TextBlob`（`TextView`/`TextLine` 字节码） | **可直接搬**：按 (text,width,style) 缓存**行切分结果**（AC 现在缓存的是宽度/形状，不是换行结果）；不改视觉 |
| 字形图集 | 依赖 Skia 内部图集，跨帧保留（AC 未每帧 resetAll；`grep resetAll` 仅测试与注释命中） | 每帧 `resetAll()` 丢弃图集（`Graphics.java:143`） | 自建 `GLFontAtlas`（512 chunk、`stitch/resize/compact`）跨帧常驻 | **不适用/无需搬**：AC 已具备；反而要避免引入 resetAll |
| 背景模糊 | 5 pass Kawase + 全屏 blit，`passes 0.164ms`（`PanelBlurRenderer.java:31-33` `KAWASE_PASSES = 5`） | 1 pass + 3 次全屏 blit，1:1 分辨率（`BlurProgram.java:61-105`） | **2 pass 可分离高斯**（`modern_gaussian_blur.json`），每次开界面加载一次 | **必须改视觉**（5→2 pass 的画面会有差异，需出对比图确认可接受） |
| 面板自身材质 | 自定义 GL 玻璃面板（多 pass） | 纯色/渐变/圆角 + MaskFilter 发光 | 纯 `ColorDrawable`/`GradientDrawable`，无逐面板模糊 | **不适用**（会推翻 AC 的视觉定位） |
| 消息/列表构建 | 一次性构建 | 每次渲染数组过滤 + 视口剔除 | 每帧最多建 3 条视图（`ChatBoxWidget:341`）+ 整树复用（`ChatMenu:532-543`） | **可直接搬**：消息入库限流 + 重开复用视图树；不改视觉 |
| 帧率节奏 | 跟 MC 限帧；长帧 4/65 >30ms | 无自建节奏 | Choreographer 失效驱动；失焦降帧 | **可直接搬**（失效驱动是脏区方案的一部分） |
| 装饰纹理（DOTS） | `DotTexture` 已有 `Picture` LRU（上限 64）+ halo `Image` 缓存（上限 8）（`DotTexture.java:32-64, 242`） | 无点阵 | 无点阵；靠整层缓存让静态装饰「不重画」 | **只能搬思路**：把静态 halo 融进层缓存；不改视觉 |

补充：AC 的图标/上传路径也有跨帧资源（`ItemIconRenderer` 用 `WeakReference<DirectContext>` 且注释明确「bridge 的 resetAll 会销毁 Skia GL 状态缓存」），说明桥上确实存在会打断缓存的动作——但当前主线没有每帧 `resetAll`，**不要**把它当成 DOTS 的已证原因。

---

## 6. 下一步可选动作（按收益/风险排序）

> 每条都写「验证方法」，全部是**建议**，本轮未执行。

1. **【高收益 / 低风险 / 不改视觉】把面板的静态层烘成一张 GPU 纹理**
   - 做法：面板打开时把「底板 + 圆角 + 边缘点阵 + 静态文字」录进一个 `Picture` 或渲染到一张与面板等大的 `Surface`（1:1，DPR 对齐），每帧只 `drawImageRect` 一次 + 画动态层（新消息、打字光标、动画）。
   - 前置：确认面板尺寸变化/主题切换/字号变化时有显式失效；确认层纹理与主缓冲同一色彩空间（`RGBA_8888` + 同 `ColorSpace`）。
   - 验证：三档归零实验同法（A 完整 / B 只跳静态层重建 / C 旁路），看 `DRAW` 与 `DOTS` 是否从 0.968/0.744 掉到 <0.3；再截图像素 diff，要求 0 差异。

2. **【高收益 / 中风险 / 不改视觉】脏区 + 失效驱动重绘**
   - 做法：给动画/滚动/新消息打 `invalidate(rect)`，无脏区帧只合成层；把「每帧无条件 layout」改成「布局脏了才算」。
   - 前置：所有动画（气泡入场、光标闪烁、滚动惯性）必须自己标脏，漏标会卡帧。
   - 验证：静止 10s 抓帧间隔 p50/长帧数（目标向 B 档 0.361ms/0 长帧靠拢）；逐项开关动画确认无卡死。

3. **【中高收益 / 低风险 / 不改视觉】行级 layout 缓存**
   - 做法：AC 已有宽度缓存和 `ShapedPlan`，但换行结果仍每次算；加一层 `(text, maxWidth, style, font) → List<Line>` 的有界 LRU。
   - 前置：文本/宽度/主题变更要失效；缓存按字符预算限容（ModernUI 用 lifespan，AC 可用条数+字符数双上限）。
   - 验证：`SkiaFontRenderer` 打点统计 `wrapCacheBuilds`（参照其现有 `shapeCacheBuilds` 计数器），面板稳态应为 0 次/帧；截图 0 diff。

4. **【中收益 / 低风险 / 不改视觉】新消息入库限流 + 视图/布局复用**
   - 做法：照 `ChatBoxWidget` 每帧最多 N 条；重开面板复用已有布局对象（ModernUI 直接复用整棵视图树）。
   - 验证：一次性灌 200 条消息，测最大帧间隔（目标消除 >30ms 长帧）；滚动到底部行为不变。

5. **【中收益 / 视觉会变】模糊 5 pass → 2 pass 可分离高斯**
   - 做法：`PanelBlurRenderer.KAWASE_PASSES` 从 5 降到 2（或改 separable），复用 ping-pong buffer 不重建。
   - 风险：模糊观感变化（Kawase 与高斯的尾部不同），**必须改视觉**，需 A/B 截图并由用户判定。
   - 验证：`passes` 分段计时（当前 0.164ms）与截图对比。

6. **【实验性 / 需评估】面板直画主 FBO（学 tuui）**
   - 不推荐直接照搬：AC 的桥是 AtomLib 的核心抽象，拆掉会影响图标导入、模糊、多平台。只有在 1/2/3 都做完仍不达标时再评估。

**不要照搬的**：tuui 的每帧 `resetAll()`（会丢图集）、tuui 的逐字符 measure（`Text.textOverflow`）、tuui 的每帧新建 `MaskFilter`（`Text.java:243-245`）、tuui 的 2× 超采样文字（`FontRendererKt.java:183,196`，会改视觉且费填充率）。

---

## 7. 未证实 / 查不到的部分

| 项 | 状态 | 怎么才能证实 |
|---|---|---|
| tuui 在真机上的实际帧成本（是否真的比 AC 低） | **未证实**。本轮只读代码，未运行 | 在 1.20.1 实例装 Spark/JFR 采样，或按主代理的三档法在同一机器测 tuui 菜单打开时的帧间隔 |
| ModernUI 的 `GraniteSurface` 是否每帧都重绘（还是只在脏区时局部更新） | 部分证实：`mDirty` 为空时 `performTraversal` 不画；但 `UIManager#render` 每帧都会合成层。局部更新只覆盖脏矩形 | 断点/字节码跟踪 `beginDrawLocked` 调用频率；或开 ModernUI debug 布局看重绘次数 |
| chatcore 是否给消息数量设了上限 | **未找到**（`ChatManager` 未见 `MAX/subList/remove(0)`） | 读 `ChatManager.java` 全文或在游戏内灌 1000 条看内存/帧时 |
| `useVanillaChat` 之外的开关（是否有隐藏 config 能只开聊天框不开其他） | **未证实**：`chatcore.properties` 只有 `useVanillaChat` 一项（`ChatCore.java:44-52`） | 读 `ModernUI/config` 目录下 ModernUI 自身配置 |
| tuui 每帧 `resetAll()` 是否真的导致字形图集重建/重传 | **未证实**（按 Skia API 语义推断成立） | 在 tuui 菜单内统计每帧纹理上传，或临时去掉 `resetAll` 做 A/B 帧时对比（需改 jar，本轮禁止） |
| tuui 与 chatcore 是否有作者层面的复用（同一 UI 框架） | **否**：包名、渲染栈、依赖完全不同（Skija vs ModernUI） | 已用依赖表与 imports 证实 |

---

## 8. 纪律声明

- 未修改 tuui / tumc / Muonium / AtomChat 任何源码或资源。
- 反编译产物只写入 `D:/AtomChat-Main/build/tuui-research-20261106/`；未向 `D:/Myworld/.minecraft/versions/*` 写入或复制任何文件（只读 `unzip -l/-p`、`sha256sum`、`cat`）。
- 未启动游戏、未部署、未 commit / push / 发布。
- `docs/verification/` 下只新增本文件，未改动既有报告。
