# Changelog

## v0.2.17

> 本版把悬停高亮统一成「强调色染色 + 明暗自适应」的一种语言，边框回到发丝感，并修掉 0.2.16 引入的几处视觉回归。

**更改**

- **悬停高亮统一为强调色染色**：亮色界面下是带主题色的加深、暗色界面下是带主题色的提亮，强度全主题一致，不再出现灰色悬停。
- **选中态悬停不再变色，只保留弹跳**（底栏标签、分段标签、表情标签三处行为从此一致）。
- **卡片边框回到发丝感**（浓度较 0.2.16 明显调淡），卡片投影恢复柔和。
- **表情面板回到固定深灰皮肤**：选中标签恢复白色胶囊，不随主题（有意例外）。
- 滑条未填充轨道与内嵌数字输入框改为明暗自适应，浅色主题下可见。
- 档案页复制图标跟随文字色；发送按钮图标按强调色底自动取墨色。
- 色板选中环、主题卡描边、通知横幅悬停并入统一颜色语言。

**修复**

- 发送按钮与输入栏边框的间距恢复（0.2.16 被误减半）。
- 夏日、典雅等浅色主题下悬停高亮偏灰、看不清的问题。

----

> This release unifies hover highlights into one accent-tinted, polarity-adaptive language, returns the borders to a hairline feel, and fixes several visual regressions introduced by 0.2.16.

**Changed**

- **Hover highlights are accent-tinted across the UI**: a theme-coloured deepen on light surfaces and a theme-coloured lift on dark ones, at one strength on every theme — no more grey washes.
- **Hovering a selected chip no longer changes its colour**; the bounce is the feedback (bottom tabs, section chips and emoji tabs now behave identically).
- **Card borders are back to a hairline** (noticeably lighter than 0.2.16), and the card shadow is soft again.
- **The emoji panel returns to its fixed dark-grey skin**: the selected tab pill is translucent white again, not theme-following (a deliberate exception).
- Slider unfilled tracks and inline number fields are polarity-adaptive, so they stay visible on light themes.
- The profile copy glyph follows the text colour; the Send glyph picks its ink from the accent fill.
- Swatch selection rings, theme-card edges and the notification banner hover join the unified colour language.

**Fixed**

- The Send button's clearance to the composer border is restored (0.2.16 had halved it).
- Hover highlights turning grey and hard to read on light themes such as summer and elegant.

## v0.2.16

> 本版重做了输入栏按钮，把全界面的悬停 / 按压弹跳统一成「每边 4 像素」的位移预算，并修掉 0.2.15 留下的一批可见缺陷。

**更改**

- **输入栏按钮整组重做**：实心底色、等宽方角、发丝分隔线，悬停与选中色从强调色派生。
- **输入栏三键、发送键与顶栏按钮统一尺寸**。
- **分段标签的选中胶囊锚定在当前格子上**，悬停 / 按压时整个格子一起缩放。
- **悬停 / 按压缩放统一为每边 4 像素的位移预算**：小控件最多放大 6%，宽卡片约 1.6%，按压对称缩小。
- **卡片在滚动区两侧的内缩从 15 像素收窄到 8**，卡片投影同步收紧。
- **输入栏按键边距统一为 5 像素**，输入栏整体变矮 10 像素。
- **设置首页导航方块加内缩**，悬停放大不再切到圆角。
- **表情面板标签条获得与底部标签一致的悬停 / 按压弹跳**。
- **颜色「+」格与可折叠标签行获得悬停反馈**。
- 档案页信息行按住时下沉。

**修复**

- 小控件的悬停没有回弹（弹簧在到达目标的瞬间就锁定）。
- 浅色主题下悬停高亮完全看不见：高亮色现在按底色对比度自动翻转。
- 开关在浅色 / 深色面板下都可辨：开启带轮廓、关闭加深、禁用态整体变淡。
- 会话列表拖动滚动条时内容不动、点击不跟随滚动位置。
- 设置首页的悬停判定滞后一帧。

----

> This release reworks the composer buttons, unifies hover / press bounce into a 4-pixel-per-side travel budget across the UI, and fixes a batch of visible defects left over from 0.2.15.

**Changed**

- **Composer buttons reworked**: solid fills, equal square keys and hairline separators; hover and selection derive from the accent colour.
- **The composer keys, Send and the header buttons share one size**.
- **The section chip's selected pill is anchored to its cell**, and hover / press scales the whole cell.
- **Hover / press scaling unified to a 4-pixel-per-side travel budget**: small controls grow at most 6%, wide cards about 1.6%, presses mirror inward.
- **Card inset inside scroll areas narrowed from 15 to 8 pixels** per side, with the card shadow tightened to match.
- **Composer key inset unified to 5 pixels**; the composer bar is 10 pixels shorter.
- **Settings home tiles gained an inset**, so the hover lift no longer shears their rounded corners.
- **The emoji panel tab strip gets the bottom bar's hover / press bounce**.
- **The colour "+" cell and foldable label rows get hover feedback**.
- Profile info rows now dip while held.

**Fixed**

- Small controls had no hover bounce (the spring settled the instant it reached its target).
- Hover highlights were invisible on light themes; the wash now flips by contrast against the surface.
- Switches read on both light and dark panels: rim when on, darker track when off, dimmed when disabled.
- Dragging the conversation-list scrollbar moved nothing, and clicks ignored the scroll offset.
- Settings-home hover lagged one frame.

## v0.2.15

> 本版是界面改版版：文字、动效、主题与整个设置页都重做了一遍，末尾两条修掉改版过程中自己引入的毛病。

**新增**

- **主题颜色预设**：外观设置新增六个配色（极简 / 夏日 / 典雅 / 黑鸦 / 枢纽 / 活力），每个预设写入完整的十二色快照，选中即生效。
- **主题预览卡**：主题选择从圆点改为横向滚动的迷你面板卡，每张按各自主题的配色与圆角绘制面板底、标题条、气泡与输入条；滚轮滑动，点击选中，选中的一张带强调色描边。
- 调试开关下，面板模糊在场景静止时改为最多每 500ms 刷新一次（镜头移动或面板动画期间维持原有频率）。

**更改**

- **正文字号提升**：正文与输入框 20、发送者名 17、引用 16（原 16 / 14 / 13），2K / 4K 面板上经缩放的文字仍清晰。
- **文字阴影改为柔和投影**，深浅跟随面板明暗（按亮度判定），替换原先在浅色主题下发脏的 1px 硬投影。
- **面板开合改用弹簧动画**：约 0.18s 落定、无可见过冲；消息入场改为轻微回弹的缓动。
- **可点控件加上悬停与按下缩放**（悬停 1.08、按下 0.92），含开关、表情格、表情包格、底部标签与颜色色板；设置行按 0.95 的按下反馈。
- **设置页重组**：设置首页先进分类，分区内容改用页内分段标签切换；圆角从三档改为连续半径滑条。
- **卡片装饰统一为一套**：柔和投影、卡片底色、按面板明暗选择的描边与悬停覆盖。
- **「磨砂」主题卡改名为「默认」**，并真正执行恢复出厂配色（十二色与面板参数一起复位）。
- 滑块旋钮外圈改为强调色描边，色板与「+」格加上跟随主题的细描边。
- **取消消息行的按下弹跳**：表情 / 图标 / 常用语 / 发送这一排按钮保留与底部标签一致的按压与悬停缩放。
- **滑块旋钮去掉裁切环**，只留外圈强调色描边；默认主题下描边加粗，消除细线上的锯齿感。

**修复**

- 切换设置分区标签时，行区域的裁剪泄漏会遮住标签胶囊条。
- 滑块旋钮的描边过细，在默认主题下呈锯齿状。

----

**Added**

- **Colour theme presets**: six palettes (minimal / summer / elegant / raven / hub / vivid) in the appearance settings, each writing a full twelve-colour snapshot on selection.
- **Theme preview cards**: the theme row is now a horizontally scrolling strip of mini panel cards, each drawn in its own palette and corner scale (panel ground, title bar, bubbles, composer strip); wheel to scroll, tap to select, accent hairline on the selected one.
- Under the debug switch, panel blur refreshes at most once per 500ms while the scene is still (the every-2-frames cadence stays during camera movement or panel animation).

**Changed**

- **Larger body text**: body and input 20, sender name 17, quote 16 (was 16 / 14 / 13), so text survives uiDensity dilution on 2K and 4K panels.
- **Text backing is now a soft drop shadow** whose strength follows the panel polarity (WCAG luminance), replacing the 1px hard under-copy that smeared on light themes.
- **Panel open and close ride a spring**: settled in about 0.18s with no visible overshoot; message entrance uses a gentle easeOutBack.
- **Hover and press scale on every interactive control** (hover 1.08, press 0.92): toggles, emoji and emote cells, bottom tabs and colour swatches; settings rows press to 0.95.
- **Settings rework**: the home tile opens a category menu, sections switch through in-page segmented chips, and the corner style is a continuous radius slider instead of three presets.
- **One shared card treatment**: soft shadow, card fill, polarity-picked border and hover wash.
- **The "frosted" theme tile is now "Default"** and really resets the factory colours and panel values.
- Slider knobs trace an accent outline, and colour swatches and the "+" cell gained a theme-adaptive hairline.
- **Message rows no longer bounce on press**; the image / emoji / phrase / send row keeps the same press and hover scale as the bottom tabs.
- **The slider knob's cut-out ring is gone**, leaving only the accent trace, which is thicker so it no longer looks jagged on the default theme.

**Fixed**

- A row-clip leak that hid the chip bar while a settings section switched tabs.
- The slider knob's hairline being too thin, which read as aliasing on the default theme.

## v0.2.14

> 本版放开本地表情的数量限制，并给想限量的玩家一个配置项。三个发布目标（Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1）同步。

**新增**

- **客户端配置 `emoteMax`**（`atomchat-client.json`，默认 `0` = 不限量）：设为正整数即为本地表情的上限，重开聊天界面后生效；加满时表情面板的「+」号槽变灰。

**更改**

- **本地表情不再默认限 20 个**：表情面板里「+」号添加的本地表情现在默认不限量（想限多少用 `emoteMax` 自己设）。
- 服务端下发的表情不变：仍是只读的本服分区，客户端最多显示 32 个，与本地名额分开计数。

**说明**

- README（中英）、Modrinth / CurseForge / MC 百科三份商店描述、双语 wiki 中所有「最多 20 个」的说法已同步改为「默认不限量 + `emoteMax` 可设上限」。

----

> This release lifts the default cap on local emotes and gives players who want a cap a config option. The three release targets (Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1) ship it together.

**Added**

- **A client config `emoteMax`** (`atomchat-client.json`, default `0` = unlimited): setting it to a positive integer caps local emotes, taking effect when the chat UI is reopened; the "+" slot greys out once the cap is reached.

**Changed**

- **Local emotes are no longer capped at 20 by default**: local emotes added through the "+" slot in the emote panel are unlimited unless you set a cap with `emoteMax`.
- Server-delivered emotes are unchanged: still a read-only server-side section, showing at most 32 in the client, counted separately from the local allowance.

**Notes**

- All "up to 20" mentions in the READMEs (Chinese and English), the three store descriptions (Modrinth / CurseForge / MC Encyclopedia) and the bilingual wiki have been updated to "unlimited by default, cap via `emoteMax`".

## v0.2.13

> 本版修掉 issue #19：专用服务端被客户端的 AWT「抢跑」拖下水。三个发布目标（Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1）同步。

**修复**

- **专用服务端不再触碰 AWT（issue #19）**：shared 的 mixin 插件过去无条件调用 `AwtDisplay.claim()`，在无 X 显示的专用服务器上会强行拉起 X11 toolkit；失败后 `GraphicsEnvironment$LocalGE` 初始化永久损坏，此后服务端任何图片处理（实测 WorldComment）都抛 `NoClassDefFoundError`。抢跑点现在仍是 mixin 插件期（必须早于游戏 `Main`，否则 headless 结果已被缓存锁死），但拆成每个平台一份、且只在客户端生效：NeoForge / Forge 的插件用 `FMLEnvironment.dist.isClient()` 守护，Fabric 继续靠 `fabric.mod.json` 的 `environment: "client"`。专用服务端不再加载 AWT。
- 客户端启动日志新增一行 `AWT toolkit: <结果>`，图片选择器出问题时能直接区分「没抢到」和「这台机器没有显示」。

**说明**

- 曾尝试把抢跑点移到 `AtomChatClient` 的 mod 构造期；真实客户端 + TFC 实测该时机返回 `locked-headless`（`Main`/`Minecraft` 在 mod 构造前已缓存 headless），选择器不可用，故保留插件期抢跑、只收窄到客户端。

----

> This release fixes issue #19: a dedicated server was dragged down by the client's AWT head start. The three release targets (Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1) ship it together.

**Fixed**

- **A dedicated server no longer touches AWT (issue #19)**: the shared mixin plugin called `AwtDisplay.claim()` unconditionally, which on a display-less dedicated server forced the X11 toolkit up; after the failure `GraphicsEnvironment$LocalGE` was permanently poisoned, and every later server-side image operation (WorldComment in the report) threw `NoClassDefFoundError`. The claim still happens in the mixin-plugin phase (it has to run before the game's `Main`, or the headless answer is already cached), but it is now one copy per platform and client-only: the NeoForge / Forge plugin is guarded by `FMLEnvironment.dist.isClient()`, while Fabric keeps relying on `fabric.mod.json`'s `environment: "client"`. A dedicated server no longer loads AWT.
- The client startup log gained an `AWT toolkit: <outcome>` line, so image-picker trouble can be told apart as "lost the race" versus "this machine has no display".

**Notes**

- We first tried moving the claim into `AtomChatClient`'s mod constructor. On a real client with TFC that timing returned `locked-headless` (`Main`/`Minecraft` had already cached the headless answer before mod construction), leaving the picker unusable, so the claim stays in the mixin-plugin phase and is merely narrowed to the client.

## v0.2.12

> 本版是「加固」版：没有新界面、也不改玩法。做的是三件事 —— 让诊断不再说假话、让渲染这一层出问题时**构建会红**而不是靠人看、以及给性能装一把尺子。四个目标（Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1）同步。

**修复**

- **构建产物自称"来自未提交的源码"**：每个从 CI 出来的 jar，构建身份那一行都写着 `git <提交号>+dirty`，而它其实是从干净的检出构建的。根因是三个 `gradlew` 在 git 索引里没有可执行位，工作流那句 `chmod +x` 于是在 Linux 上构成一次"已跟踪文件被改动"—— 本机是 Windows（`core.fileMode=false`），所以**本地永远看不到**，只有下载产物的人看得到这个假标记。现在索引里带上了可执行位，身份行说的是真话；并且 CI 每次构建都会自己断言这一条，它再也活不过一次构建。
- **非 Windows 上开面板会把游戏带走**：打包的原生库只有 Windows x64 一份，其他平台上第一次绘制就落在原生层 —— 那不是异常，是 JVM 直接死，`try/catch` 连看一眼的机会都没有。现在启动时**正面探测**一次（真的去加载，不看平台自述），拿不到就不让面板打开：原版聊天照常接管，日志里留一行说明平台与原因。**本版不承诺非 Windows 能用**，只承诺不崩。

**新增**

- **渲染耗时尺子**（调试开关下）：面板开着时约每十秒打一份分布 —— 整帧耗时与**每帧的对象分配**、图片解码耗时、文字塑形耗时，都是 p50/p95/max/均值。**本版只交付基线，不做优化**：先量，再决定有没有东西值得改。

**更改**

- **渲染这一层有了回归网**：把整个面板（四个设置子页、设置首页、消息列表、表情面板、常用语面板、通知横幅）画在**离屏光栅面**上跑，不碰 GPU、不开窗口。任何一次"释放了原生句柄还去用"都会让**测试进程直接死、构建变红** —— 那正是 0.2.11 之前那次 `hs_err` 的形状（`Path.close()` 是释放原生对象，不是闭合轮廓）。
- 三端单测 **569 项**（0.2.11 是 542）；另有一个离屏微基准默认跳过，需要时用 `ATOMCHAT_BENCH=1` 手动跑。
- CI 增加**产物级断言**：看 jar 里面的重定位 FlatLaf、公共包引用、以及两个 Skija 嵌套库在不在。

----

> A hardening release: no new UI, no gameplay changes. Three things — make the diagnostics stop lying, make the draw layer turn the **build red** when it breaks instead of relying on someone noticing, and give performance a ruler. All four targets (Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1) ship together.

**Fixed**

- **Build artifacts claiming to come from uncommitted source**: every CI-built jar's identity line read `git <sha>+dirty` while it was actually built from a pristine checkout. The root cause was that the three `gradlew` wrappers had no executable bit in the git index, so the workflow's `chmod +x` counted as a change to a tracked file on Linux — and since this machine is Windows (`core.fileMode=false`), **you could never see it locally**; only whoever downloaded the artifact ever saw the false flag. The bit is in the index now and the identity line tells the truth; every CI build also asserts this itself, so it cannot survive a single build.
- **Opening the panel on non-Windows took the game down**: the bundled native library exists only for Windows x64, and on any other platform the first draw lands in the native layer — that is not an exception, it is the JVM dying outright, with no chance for a `try/catch` to even look at it. The native is now **probed head-on** once at startup (actually loaded, not trusting the platform to describe itself), and without it the panel refuses to open: vanilla chat takes over as usual and one log line names the platform and the reason. **This release does not promise that non-Windows works**; it promises it will not crash.

**Added**

- **A render-cost ruler** (under the debug switch): while the panel is open it prints a distribution about every ten seconds — whole-frame time and **per-frame object allocation**, image decode time and text shaping time, all as p50/p95/max/mean. **This release ships the baseline only and optimises nothing**: measure first, then decide whether anything is worth changing.

**Changed**

- **The draw layer gained a regression net**: the whole panel (four settings sub-pages, the settings home, the message list, the emote panel, the phrase panel, the notification banner) now renders onto an **offscreen raster surface** in tests — no GPU, no window. Any "released a native handle and then used it" makes the **test process die outright and the build turn red** — exactly the shape of that `hs_err` from before 0.2.11 (`Path.close()` releases a native object, it does not close a contour).
- **569** unit tests across the three targets (542 in 0.2.11); there is also an offscreen micro-benchmark, skipped by default and run by hand with `ATOMCHAT_BENCH=1` when needed.
- CI gained **artifact-level assertions**: whether the relocated FlatLaf, references to the public package, and both nested Skija libraries are inside the jar.

## v0.2.11

> 本版清掉的都是「外部环境」的毛病：别人给的消息格式、别人内嵌的同名库、别人的光影包，以及"报 bug 时说不清是哪个构建"。四个目标（Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1）同步实现。

> **2026-09-13 重新上传**：修了下面第一条 —— NeoForge 上的环境摘要里有一行说错了话。原生库一直是好的，错的是诊断自己。商店里的同名文件已替换；两个 0.2.11 的产物用各自日志里的 `sha256:` 就能分清。

**修复**

- **NeoForge 上的环境摘要自相矛盾**：那一行前半句写着"没打包原生库"、末尾却又写着"原生库已加载 181 ms"。原生库一直是好的 —— 错的是诊断自己：版本号靠加载器的资源枚举去找，而枚举在三端并不一致（Forge 的 JarJar 看得到嵌套 jar 里的资源、NeoForge 的 jarjar union 看不到、Fabric 的 `include` 干脆把它摊平成独立文件）。现在**先问加载结果、再写版本**：只有加载确实失败才许说"没打包"，其余情况直接从我们自己的产物里把真版本读出来。这条规矩配了 6 个用例守着，**也是本次同名重新上传的原因**。

- **图片码写法稍有不同就整条露馅**：图片消息的协议码只要写成小写 `cicode`、或者参数换了个顺序，解析器就认不出来，于是整条消息当普通文字显示 —— 协议原文直接画在气泡里，末尾还被识别成蓝色链接，**点一下就报错**（玩家日志里的 `Can't open url … URISyntaxException`）。根因是同一套语法在三个地方各写了一遍、容忍度还不一致。现在统一到一份规则（大小写不敏感、参数顺序随意、值两边允许空格），并且**认不出的码显示占位符而不是原样上屏**；链接识别也不再碰协议码区间。
- **装了另一个也内嵌 FlatLaf 的模组时，图片选择器直接崩**：`com.formdev.flatlaf` 是个公共包名，另一个模组（实测 Mcpatch）也内嵌了同一份，谁先被类加载器解析到谁赢 —— 结果我们的选择器跑在它的副本上，撞上一个它没有的类，抛 `ClassNotFoundException`。现在我们把 FlatLaf 收进私有包名 `com.atom.chat.shaded.flatlaf` 再打包，别人抢不走了。
- **构建产物偶发"启动即崩"**：增量编译有时会清掉 Mixin 注解处理器的输出目录却不重跑它，refmap 于是静默消失（负责搬运的那个 Gradle 任务在源缺失时是静默空操作），打出来的 jar 一启动就报 `No refMap loaded`。现在有构建闸盯着，缺了直接让构建失败。
- **模糊渲染失败时会刷屏**：以前每帧重试、每帧打一条日志（调用方还会再补一条完整堆栈），一个确定的错误能把日志刷满、自己把自己的根因埋掉。现在**首次失败就熔断**，面板退成实色继续用，下次启动重新开始。

**新增**

- **启动时打一行构建身份**：模组版本、加载器与 MC 版本、**实际加载的 jar 文件名**、**该文件内容的 SHA-256**、构建时的提交号。文件名可以被改得认不出来，内容哈希不会 —— 这一行就是为了让一份日志能唯一定位到是哪一个构建。
- **调试开关打开后打一整块环境摘要**：构建身份、产物路径与哈希、Java 与系统、**当前用的 FlatLaf 是从哪个 jar 加载的**、**Skija 原生库有没有加载成功、花了多少毫秒**、GPU 与驱动、以及模糊 / 壁纸 / 图片接收等关键开关。整块是**一条**日志记录，连着复制即可，不会被别的模组插进来。
- **输入也进日志了**（调试开关下）：按了什么键、输入了什么字符、点了屏幕上哪个点、换算到面板坐标是哪儿。界面是 Skia 自绘的，原版那套控件调试手段不适用，这几行是判断"这个按钮点了没反应"唯一可靠的办法。
- **降级不再静默**：服务端关掉媒体托管时提示"图片会走公共图床"，图片接收被关时提示一次，模糊回退实色也有提示。

**更改**

- **补上 Skia 会改动、而我们此前没有保存的 OpenGL 状态**：混合方程、混合常量色、正面朝向、以及整族模板状态。这几种状态被漏掉时的表现不是报错，而是**别人画的东西少像素或者正反面反了** —— 正是跨模组共存最容易踩的一类问题。
- 排查文档（中英）新增「怎么开调试、该把哪几行交上来」。

----

> Everything this release clears out is an **outside-world** problem: someone else's message format, someone else's identically-named embedded library, someone else's shader pack, and "when you report a bug you cannot say which build it was". All four targets (Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1) implement it together.

> **Re-uploaded 2026-09-13**: fixed the first item below — one line of the environment summary on NeoForge was saying the wrong thing. The native library was always fine; the diagnostic was what was wrong. The same-named file on the store has been replaced; tell the two 0.2.11 builds apart by the `sha256:` in their own logs.

**Fixed**

- **The environment summary contradicted itself on NeoForge**: one line's first half said "no native library bundled" while its end said "native loaded in 181 ms". The native library was always fine — the diagnostic was what was wrong: the version was looked up through the loader's resource enumeration, and that enumeration is not consistent across the three targets (Forge's JarJar can see resources inside a nested jar, NeoForge's jarjar union cannot, and Fabric's `include` simply flattens it into a separate file). The **load result is now read first and the version written after**: only a genuine load failure may say "not bundled", and every other case reads the real version straight out of our own artifact. The rule is guarded by 6 test cases, and **it is also why this release is a same-name re-upload**.
- **An image code spelled slightly differently gave the whole thing away**: as soon as an image message's protocol code was written with a lower-case `cicode`, or with its parameters in a different order, the parser failed to recognise it and the entire message rendered as plain text — the raw protocol drawn right in the bubble, its tail recognised as a blue link, and **one click threw** (players' logs show `Can't open url … URISyntaxException`). The root cause was the same grammar written out three times in three places, with three different tolerances. It is now one rule (case-insensitive, any parameter order, whitespace allowed around values), and **a code we cannot read shows a placeholder instead of going on screen as-is**; link detection no longer touches protocol-code ranges either.
- **With another mod that also embeds FlatLaf installed, the image picker crashed outright**: `com.formdev.flatlaf` is a public package name and another mod (Mcpatch, in our test) embeds the same copy — whichever the class loader resolves first wins, so our picker ended up running on its copy and hit a class that copy does not have, throwing `ClassNotFoundException`. FlatLaf is now packed under the private package name `com.atom.chat.shaded.flatlaf`, so it can no longer be taken from us.
- **Build artifacts occasionally "died at startup"**: incremental compilation sometimes wiped the Mixin annotation processor's output directory without re-running it, so the refmap vanished silently (the Gradle task responsible for moving it is a silent no-op when the source is missing), and the resulting jar died at launch with `No refMap loaded`. A build gate now watches for it and fails the build outright when it is missing.
- **A failed blur pass flooded the log**: it used to retry every frame and log a line every frame (the caller adding a full stack trace on top), so one deterministic error could fill the log and bury its own root cause. It now **trips on the first failure**: the panel falls back to a solid colour and keeps working, and the next launch starts fresh.

**Added**

- **One line of build identity at startup**: mod version, loader and MC version, **the jar file name actually loaded**, **the SHA-256 of that file's contents**, and the commit it was built from. A file name can be changed beyond recognition; a content hash cannot — this one line exists so a single log can pin down exactly which build it was.
- **A whole block of environment summary under the debug switch**: build identity, artifact path and hash, Java and OS, **which jar the FlatLaf in use was loaded from**, **whether the Skija native library loaded and how many milliseconds it took**, GPU and driver, and the key switches for blur / wallpaper / image receiving. The block is **one** log record, so copying it in one go works and no other mod can cut into it.
- **Input goes to the log too** (under the debug switch): which key was pressed, which character was typed, which point on screen was clicked and where that maps in panel coordinates. The UI is drawn by Skia, so the vanilla widget-debugging toolbox does not apply, and these lines are the only reliable way to judge "I clicked this button and nothing happened".
- **Fallbacks are no longer silent**: turning media hosting off on the server says "images will go through the public image host", turning image receiving off says so once, and a blur falling back to a solid colour says so too.

**Changed**

- **Restoring the OpenGL state Skia moves that we never saved**: blend equation, blend constant colour, front face, and the whole stencil family. When those are missed the symptom is not an error but **someone else's drawing losing pixels or having its faces inverted** — precisely the class of problem cross-mod coexistence trips over most easily.
- The troubleshooting docs (Chinese and English) gained a **how to turn debug on and which lines to send us** section.

## v0.2.10

> 编号说明：开发期内部号一度写到 0.2.91（未发版），商店线上一次发布是 0.2.9。本版回到 0.2.10，让商店版本号保持递增；下面是 0.2.9 之后的全部修复。三个目标（Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1）自此由同一份源码产出。

**修复**

- **玩家卡片菜单的「取消屏蔽」点不到**：绘制按 3 行（资料 / 传送 / 屏蔽），而根页面的点击判定写死 2 行，第三行永远落在判定区外。现在绘制与根页面、世界聊天页三个点击入口共用 `contextMenuRows()`，玩家卡片固定 3 行。
- **@ 提及丢失称号颜色**：NeoForge 1.21.1 / Forge 1.20.1 的签名聊天捕获只取 `signedContent()` 再包成字面组件，服务器（插件）下发的 unsigned 富文本被丢掉（Fabric 侧本来就取 `SignedMessage.getContent()`）。现在两端改取 `decoratedContent()`；@ 提及动作从「插入装饰名」改为插入真实 profile 名，渲染时再用本地已知的装饰名给本地玩家的 mention 重新着色（`@真实名` 与 `@[称号]名` 两种写法都支持，链接内的 @ 名与邮箱形状不误伤）。
- **引用回复正文样式丢失**：引用前缀 `「引用 @名: 内容」正文` 的正文此前用解析出的纯文本重建，正文里的颜色与富文本全丢。现在新增 `ChatPipeline.quoteBodyRich()`：从富文本行里 slice 闭合括号之后的正文，保留原 run 样式与链接；可见文本与解析结果不一致（前缀被翻译、空白漂移等）时回退旧的 literal 行为。公屏两条路径与私聊路径均已接入。
- **设置页与个人资料页的列表打开时停在底部**：滚动控制器默认「首帧贴底」——公屏要的正是这个（进面板直接看到最新消息），可这两页也照做，于是打开就停在底部。现在控制器带上下锚定策略（`ScrollController(boolean)`），设置页与个人资料页改为顶部锚定，公屏行为不变；内容尺寸变化时也会重新钳制滚动位置。抽成纯类并配了单测。
- **Forge 1.20.1 点服务端下发的常用语毫无反应**：共享的 `QuickPhrasePanel` 会给服务端来源的行发 `Action.INSERT_SERVER`，1.21.1 两端都处理了，唯独 1.20.1 这端没有这一支，switch 落到空的 `default` 上。现在补齐（服务端行是只读的：插入文本后关闭，没有编辑/删除路径）。
- 三端同源：以上修复在 Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1 同步实现，单测三端各 505 项全绿。

----

> A note on numbering: the internal development number once reached 0.2.91 (never released) while the last store release was 0.2.9. This release goes back to 0.2.10 so the store version keeps increasing; everything below covers both that batch of fixes and the ones that landed on main afterwards. The three targets (Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1) have been built from the same source ever since.

**Fixed**

- **The player card menu's "unblock" row could not be clicked**: the drawing used 3 rows (profile / teleport / block) while the root page's hit test was hard-wired to 2, so the third row always fell outside the hit area. Drawing and the three click entry points (root page and world chat page) now share `contextMenuRows()`, and a player card is fixed at 3 rows.
- **@ mentions lost their title colour**: NeoForge 1.21.1 / Forge 1.20.1's signed-chat capture took only `signedContent()` and wrapped it in a literal component, dropping the unsigned rich text the server (or a plugin) had sent (Fabric was already taking `SignedMessage.getContent()`). Both now take `decoratedContent()`; the mention action inserts the real profile name instead of the decorated name, and at render time local mentions are re-coloured from the locally known decoration (both `@realname` and `@[title]name` are supported, while an `@` name inside a link and email-shaped text are left alone).
- **Quoted reply bodies lost their styling**: the body after the `「引用 @名: 内容」正文` prefix used to be rebuilt from the parsed plain text, losing every colour and rich-text run in it. There is now `ChatPipeline.quoteBodyRich()`: it slices the body out of the rich line after the closing bracket, keeping the original runs and links, and falls back to the old literal behaviour when the visible text and the parse disagree (a translated prefix, whitespace drift, and so on). Both public-channel paths and the private-chat path are wired in.
- **The settings and profile lists opened at the bottom**: the scroll controller defaulted to "snap to the bottom on the first frame" — which is exactly what the public feed wants (open the panel and see the newest message) — but those two pages did it too, so they opened stuck at the bottom. The controller now carries an anchor policy (`ScrollController(boolean)`), the settings and profile pages pass top, and the public feed is unchanged; the scroll position is also re-clamped when the content size changes. Extracted into a plain class with unit tests.
- **Server-offered quick phrases did nothing at all when clicked on Forge 1.20.1**: the shared `QuickPhrasePanel` sends `Action.INSERT_SERVER` for server-sourced rows, and both 1.21.1 targets handle it, but the 1.20.1 target had no such branch and the switch fell through to an empty `default`. Now added (server rows are read-only: the text is inserted and the panel closes, with no edit or delete path).
- Shared across all three targets: the fixes above are implemented together on Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1, with 505 unit tests green on each.

## v0.2.9

**新增**

- **服务端下发（表情包 / 常用语 / 服务器标识）**：同服务端的客户端进服时会拿到一份「本服内容」——管理员放在 `<服务端>/config/atomchat/emotes/` 的表情包、写进 `atomchat-server.json` 的常用语（最多 20 条），以及自动读取的 `server-icon.png` 与 MOTD。表情面板多出一个只读的「本服」分区（带服务器图标、名称与同步状态字），常用语面板多出一个只读的「本服」分组，点一下即可发送 / 插入。传输走新的 `atomchat-dist` 通道：客户端先算好自己已有哪些文件（逐文件 sha256），服务端只补差量、按 24 KiB 分块下发，每玩家每 tick 最多 4 块；客户端逐文件校验后整目录原子换入，所以删掉一个表情下次进服只补那一个。客户端有硬上限（200 个文件 / 16 MB）与两个看门狗（清单 15 秒、分块进展 30 秒），任何失败都会回一条原因写进服务端日志。
- **服务端配置屏 `/atomchat gui`**：OP 2 级（单人存档自动放行）在游戏内打开一个原版控件的配置屏，可改总开关、7 个数值上限与面板显示名，并增删改服务端常用语。保存时服务端会重新校验每个值域，并用 `configVersion` 拒绝过期编辑（两个管理员同时改不会互相覆盖）。控制台与主机商面板没有可绘制的界面，会明确提示需在游戏内执行。
- **服务端托管数据默认 7 天清理**：媒体库与头像库的保留期默认从 30 天改为 **7 天**（`retentionDays`，`0` 仍是永久），头像库新增独立容量上限 `maxAvatarTotalMb`（默认 64 MB）；清理时机从「只有上传时才顺手修剪」改为**开服时 + 每 5 分钟 + 每次上传后**，玩家掉线时也会清掉其未完成的上传缓冲。
- **客户端图片缓存 7 天未用自动清理**：在原有 500 文件 / 100 MB 上限之外新增「7 天没再用过就删」（读取会刷新时间戳），可在 设置 → 关于 查看与手动清空。
- **表情包上限放宽**：本地表情上限从 10 个提到 20 个，表情格改为可滚动（原先写死「10 个刚好两行、永不滚动」）。

**更改**

- 服务端配置新增键：`packEnabled`（默认开）、`packMaxFiles`（32）、`packMaxMb`（8）、`packName`（空 = 用 MOTD）、`phrases`（常用语数组）。
- 客户端新增「接收本服下发内容」隐私开关（默认开）：关掉后不再请求任何服务端内容，并立即清空已加载的视图。
- 细调：服务端下发的表情格与本地格一样有悬停高亮（只是没有删除按钮）；服务端常用语与本地同色、同样有悬停高亮（只靠「本服」分组标题与缺失的按钮区分只读）；服务器没有任何可下发内容时，两个面板都不出现服务端相关行。
- **修复增量补差**：客户端删过或改过已下发文件时，之前会因为「清单里没下载的文件缺字节」整包失败（`no content for pack file ...`）。现在会先把保留的文件重新校验后补进包里，真机上验过：首装 3 个 → 删 1 改 1 只补 2 个 → 无变化零下载。
- 细调：服务端配置屏重排成「服务端托管 / 本服下发」两段（开关就在各自小节标题行），单位写进标签（KB / MB / 毫秒 / 「0 = 永久」），毫秒冷却旁边实时显示换算的秒数，面板显示名的标签不再被输入框压掉；数值越界会在客户端先被拦下并**指名道姓**（例如「表情包体积上限（MB）」只能是 1–64）。
- 修复：删掉一条服务端常用语并保存后，面板里那条还在（磁盘其实已保存成功）——常用语与服名是随清单下发的，而面板读的是进服时的缓存。现在保存成功后客户端会重新握手一次，**改配置的那个人立刻看到结果**；其他在线玩家下次进服生效。
- 客户端常用语默认自带一条 `/atomchat gui`：首次生成配置时种入一次，让不看文档的服主也能发现服务端配置入口；**删掉之后不会再回来**（种子标记会记住）。
- 修复：**装了抢先初始化 AWT 的模组后，图片 / 头像 / 壁纸的文件选择窗口打不开**（实测触发者是 TFC：它的枚举字段用 `java.awt.Color` 存树叶颜色）。根因不在对方模组——`GraphicsEnvironment` 的 headless 判定是一次性静态缓存，而 Minecraft 客户端主类 `Main` 的静态初始化会把 `java.awt.headless` 写成 true，谁先碰 AWT 谁就把它锁死，AtomChat 后再改属性完全无效，`new JFrame` 抛出的 `HeadlessException` 又被日志吞掉，玩家只看到「点了没反应」。现在由 Mixin 插件在 Mixin 准备阶段（早于所有模组构造、也早于 `Main`）先把 AWT 认领下来；万一仍然拿不到，会在日志与游戏内明确提示原因，不再静默。
- 修复：**保存服务端托管的图片必定失败**——`atomchat-media:<sha>` 是伪协议，却被直接丢给 HTTP 客户端，抛 `invalid URI scheme`；就算能过，建议文件名里还带一个 Windows 非法的冒号。现在按 scheme 选字节来源（托管走 companion 通道，与渲染完全同源），文件名统一由 `ImageFileNames` 清洗（去 scheme / query / fragment 取叶子名 + 替换非法字符）。
- 修复：**玩家自己的表情会漏进服务端下发包**——下发源与客户端本地表情目录原本是同一个 `config/atomchat/emotes/`，在单机 / LAN 主机上等于把主人的收藏推给每一个访客。现在下发源独立为 `config/atomchat/server-emotes/`（与 `atomchat-server.json` 同级）；专用服务器首次构建下发包时会把旧目录里的表情**一次性拷贝**过去（只拷贝不删除，回滚安全），单机 / LAN 主机不做迁移、默认不再外推。
- 清理：0.2.4 之前那个扁平旧配置 `config/atomchat.json` 现在会在新配置存在时改名为 `atomchat.json.migrated-bak` 并记一条日志。
- 三端同源：Forge 1.20.1 / Fabric 1.21.1 / NeoForge 1.21.1 同步实现，单测三端各 472 项。

----

**Added**

- **Server-offered content (emote pack / quick phrases / server identity)**: a client joining the same server receives a bundle of "this server's content" — the emote pack the admin put in `<server>/config/atomchat/emotes/`, the quick phrases written into `atomchat-server.json` (at most 20), and the automatically read `server-icon.png` and MOTD. The emote panel gains a read-only "this server" section (with the server icon, its name and a sync-status line) and the phrase panel a read-only "this server" group, one tap to send or insert. The transfer runs over the new `atomchat-dist` channel: the client first works out which files it already has (per-file sha256), and the server sends only the difference in 24 KiB chunks, at most 4 chunks per player per tick; the client verifies file by file and swaps the whole directory in atomically, so deleting one emote means only that one is fetched on the next join. The client has a hard cap (200 files / 16 MB) and two watchdogs (15 s for the manifest, 30 s for chunk progress), and any failure writes a reason back into the server log.
- **The server config screen `/atomchat gui`**: OP level 2 (automatically allowed in single-player) opens an in-game screen built from vanilla widgets for the master switches, the 7 numeric limits and the panel display name, plus adding, editing and deleting the server quick phrases. On save the server re-validates every value range and rejects stale edits by `configVersion` (two admins editing at once cannot overwrite each other). A console or a host's control panel has no drawable screen, and is told plainly that this must be run in game.
- **Hosted data now defaults to a 7-day sweep**: the media store and the avatar store drop from a 30-day default to **7 days** (`retentionDays`, `0` still means forever), and the avatar store gains its own size cap `maxAvatarTotalMb` (64 MB by default); the sweep moves from "only trimmed as a side effect of an upload" to **at server start + every 5 minutes + after every upload**, and a player disconnecting clears their unfinished upload buffers too.
- **The client image cache expires after 7 days unused**: on top of the existing 500 files / 100 MB cap there is now "delete it once it has gone 7 days without use" (a read refreshes the timestamp), viewable and manually clearable in Settings → About.
- **The emote cap is raised**: the local emote limit goes from 10 to 20 and the emote grid becomes scrollable (it used to be hard-wired as "10 fits exactly two rows, never scroll").

**Changed**

- New server config keys: `packEnabled` (on by default), `packMaxFiles` (32), `packMaxMb` (8), `packName` (empty = use the MOTD), `phrases` (the quick-phrase array).
- New client privacy switch "accept server-offered content" (on by default): turning it off stops requesting anything from the server and immediately clears the loaded view.
- Polish: server-offered emote cells highlight on hover like local ones (they just have no delete button); server phrases use the same colour and the same hover highlight as local ones (only the "this server" group title and the missing buttons mark them read-only); and when the server has nothing to offer, neither panel shows any server-related row.
- **Fixed incremental sync**: when the client had deleted or edited an already-offered file, the whole pack used to fail with "the manifest files it did not download have no bytes" (`no content for pack file ...`). Kept files are now re-verified first and folded back into the pack; verified on a real instance: first install 3 → delete 1 edit 1 fetches only 2 → no change fetches nothing.
- Polish: the server config screen is regrouped into two sections, "server hosting / this-server distribution" (each switch sits on its section-title row), units are spelled into the labels (KB / MB / milliseconds / "0 = forever"), the millisecond cooldown shows its converted seconds live beside it, and the panel-name label is no longer crushed by its input box; out-of-range values are caught client-side and **named explicitly** (for example "emote pack size limit (MB)" accepts only 1–64).
- Fixed: after deleting a server quick phrase and saving, the row was still in the panel (the disk had actually saved correctly) — phrases and the server name come down with the manifest, and the panel was reading the cache taken at join. A successful save now makes the client re-handshake once, so **whoever changed the config sees it immediately**; other online players pick it up on their next join.
- A fresh client config ships with one quick phrase, `/atomchat gui`: seeded once when the config is first generated, so a server owner who never reads the docs still finds the server-config entry point; **deleting it does not bring it back** (a seed flag remembers).
- Fixed: **with a mod that claims AWT first installed, the image / avatar / wallpaper file picker would not open** (TFC is the trigger we measured: one of its enum fields stores leaf colours with `java.awt.Color`). The root cause is not that mod — `GraphicsEnvironment`'s headless answer is a one-shot static cache, and the Minecraft client's `Main` class writes `java.awt.headless` to true during static initialisation, so whoever touches AWT first locks it in; AtomChat changing the property afterwards has no effect at all, and the `HeadlessException` from `new JFrame` was swallowed by the log, leaving the player with "I clicked and nothing happened". A Mixin plugin now claims AWT during the Mixin preparation phase (before any mod is constructed and before `Main`); if it still cannot be had, the reason is stated plainly in the log and in game instead of being silent.
- Fixed: **saving a server-hosted image always failed** — `atomchat-media:<sha>` is a pseudo-scheme, but it was handed straight to the HTTP client and rejected with `invalid URI scheme`; even if it had got through, the suggested file name carried a colon, which Windows forbids. The byte source is now chosen by scheme (hosted images go through the companion channel, exactly the same source the renderer uses) and file names are sanitised by `ImageFileNames` (strip the scheme / query / fragment down to the leaf name, then replace illegal characters).
- Fixed: **a player's own emotes leaked into the server-offered pack** — the pack source and the client's local emote folder were the same `config/atomchat/emotes/`, which on a single-player or LAN host meant pushing the host's own collection to every visitor. The pack source is now separate, `config/atomchat/server-emotes/` (beside `atomchat-server.json`); when a dedicated server builds its pack for the first time it **copies** the old directory's emotes across once (copy only, nothing deleted, so a rollback is safe), while single-player and LAN hosts migrate nothing and no longer push anything out by default.
- Cleanup: the flat pre-0.2.4 config `config/atomchat.json` is now renamed to `atomchat.json.migrated-bak` and logged once, when a new config exists.
- Shared across all three targets: implemented together on Forge 1.20.1 / Fabric 1.21.1 / NeoForge 1.21.1, with 472 unit tests on each.

## v0.2.8

**修复**

- **Forge 1.20.1 打开面板后整屏变黑**：面板短暂可见后画面变黑，游戏不崩溃、仍能打字、消息与图片照常工作（用户实机反馈）。根因是模糊 pre-pass 的 raw GL 状态写回：`GlStateUtil` 只用裸 GL 还原，而 1.20.1 的 `GlStateManager` setter 是「缓存门控」的——缓存值相同就直接返回、根本不碰驱动。Skia 与模糊改了驱动、游戏缓存没跟上，两边对「当前活动纹理单元 / 各单元绑定」的认知从此不一致，之后原版与 Embeddium 的 `setShaderTexture` / `_bindTexture` 都绑到错误的单元，世界整片采样错纹理。现在每个状态都「先写驱动、再镜像缓存」：缓存已对时裸写已修正驱动，缓存陈旧时 setter 把缓存拉回真实值。同时把帧缓冲、视口与 `UNPACK_ROW_LENGTH` / `SKIP_PIXELS` / `SKIP_ROWS` 纳入保存/还原（原先只还了 `UNPACK_ALIGNMENT`），逐单元镜像也钳制到 Blaze3D 的 `TEXTURE_COUNT`（12，而驱动通常暴露 32 个单元）。
- **面板尺寸离屏 FBO 泄漏**：`makeTexture()` 建完模糊用的 FBO 后把它留在绑定状态，而 `ensureTextures()` 又跑在 `refreshBlur()` 记录 `oldFbo` 之前——任何重建模糊目标的帧里，圆角模糊被画进面板尺寸的小 FBO，该帧其余绘制也跟着进去。现在 `render()` 先快照帧缓冲/视口/裁剪并在 `finally` 归还，`makeTexture()` 局部还绑定。
- **模糊失败时面板看起来是黑块**：模糊被请求但这一帧没落地（着色器缺失、GL 报错、pass 提前返回）时，面板仍按「模糊成功」只叠半透明底色；在 93% 不透明度 + 近黑底色、面板又在窗口里几乎铺满时，观感就是整屏黑。现在这种情况回退**不透明**面板，只有「配置里主动关闭模糊」才保持半透明；判定抽成纯类 `PanelBackground` 并配了单测。
- **开面板滑入动画裁掉自己的左边缘**：面板淡入用的 `saveLayer` 边界是面板外扩 32px，而滑入起点在左侧 36px 处，动画前段面板左缘落在图层外被裁掉。现在图层按滑动距离取并集，滑动距离与图层余量提成常量（模糊 pre-pass 的 capture 矩形与 canvas translate 必须同步）。
- **头像取色读皮肤纹理**：`AvatarRenderer` 读取皮肤像素时的裸纹理绑定同样镜像 Blaze3D 缓存，消除同一类不一致。
- **模糊失败不再静默**：模糊 pass 结束后检查 GL 错误，有错就打一条节流警告（日志关键词 `AtomChat panel blur hit GL error 0x...`）并回退实色面板，不再假装成功。

**更改**

- 上述 GL 状态修复三端同源：Fabric 1.21.1 与 NeoForge 1.21.1 一并带上（1.21.1 的 `GlStateManager` 是同一套缓存门控 setter，属同一类隐患）。

----

**Fixed**

- **Opening the panel on Forge 1.20.1 turned the whole screen black**: the panel was visible for an instant, then the screen went black; the game did not crash and typing, messages and images kept working (a player's report from a real instance). The root cause was the blur pre-pass's raw-GL state write-back: `GlStateUtil` restored state with raw GL only, while 1.20.1's `GlStateManager` setters are cache-gated — an equal cache value returns immediately and never touches the driver. Skia and the blur changed the driver and the game's cache did not follow, so the two sides disagreed about the active texture unit and the per-unit bindings, and every later vanilla and Embeddium `setShaderTexture` / `_bindTexture` bound the wrong unit, sampling the whole world against the wrong texture. Every state is now written to the driver first and mirrored into the cache: when the cache is already right the raw write fixes the driver, and when the cache is stale the setter pulls it back to the real value. The framebuffer, viewport and `UNPACK_ROW_LENGTH` / `SKIP_PIXELS` / `SKIP_ROWS` are saved and restored as well (previously only `UNPACK_ALIGNMENT` was), and the per-unit mirror is clamped to Blaze3D's `TEXTURE_COUNT` (12, while drivers usually expose 32 units).
- **An offscreen FBO the size of the panel leaked**: `makeTexture()` left the blur FBO bound after creating it, and `ensureTextures()` runs before `refreshBlur()` records `oldFbo` — so on any frame that rebuilds the blur target, the rounded blur was drawn into the small panel-sized FBO and the rest of that frame's drawing went in with it. `render()` now snapshots the framebuffer / viewport / scissor first and restores them in a `finally`, and `makeTexture()` restores its own binding locally.
- **A failed blur made the panel look like a black block**: when the blur was requested but did not land that frame (missing shader, a GL error, the pass returning early), the panel still composited as if the blur had succeeded and only overlaid a translucent tint; at 93% opacity over a near-black base, with the panel almost filling the window, that reads as a black screen. It now falls back to an **opaque** panel, and only "blur deliberately turned off in the config" keeps the translucent one; the decision is extracted into a plain class `PanelBackground` with unit tests.
- **The open animation clipped its own left edge**: the `saveLayer` bounds for the panel's fade were the panel plus 32px, while the slide starts 36px to the left, so during the first part of the animation the panel's left edge fell outside the layer and was clipped. The layer now takes the union with the slide distance, and the slide distance and the layer slack are hoisted into constants (the blur pre-pass's capture rectangle and the canvas translate have to stay in step).
- **Avatar colour reads the skin texture**: the raw texture bind `AvatarRenderer` uses to read skin pixels now mirrors the Blaze3D cache as well, removing the same class of inconsistency.
- **A failed blur is no longer silent**: the blur pass checks for GL errors when it finishes, and on error logs one throttled warning (log keyword `AtomChat panel blur hit GL error 0x...`) and falls back to the solid panel instead of pretending it succeeded.

**Changed**

- The GL-state fixes above are shared across all three targets: Fabric 1.21.1 and NeoForge 1.21.1 get them as well (1.21.1's `GlStateManager` is the same family of cache-gated setters and carries the same latent hazard).

## v0.2.7

**新增**

- **服务端媒体托管**：聊天图片与 GIF 可以托管在服务器上，不再依赖外部图床。服务端读 `<gameDir>/config/atomchat/atomchat-server.json`（首次启动自动生成，`hostingEnabled` 默认开）决定是否托管；客户端发送前先探测服务端能力，开启则把文件分块上传到服务端，服务端按内容寻址（sha256）存进 `<gameDir>/atomchat-data/media/`，消息里写入 `atomchat-media:<id>` 短链，接收方按需分块拉取。全程走游戏连接的自定义包，**不开 HTTP 端口、不暴露公网**。服务端做了单文件大小上限、图片魔数校验、按玩家上传限速与总量/时效修剪；任何一步失败（服务端没装 AtomChat、托管关闭、超限、超时）都自动回退到原来的外部图床，行为与旧版一致。同一张图重复发送按内容去重，只存一份；头像同步并入同一个总开关。
- **GIF 动图**：聊天图片支持动图播放（此前只会停在第一帧）。解码时按 `requiredFrame` / disposal 逐帧合成，播放由帧时长时间驱动；为控制开销，视口内按最长边 384px、最多 120 帧、单图 800 万像素预算解码，超出则截断或降级为静态首帧。表情包同时支持 `.gif`。

**修复**

- **引用自己的消息时气泡里多出「引用 @名字: 文本」前缀**：引用内容本来要随消息上行（`「引用 @名: 文本」正文`），但本地回声把整串当成了自己的正文。现在气泡只渲染正文，引用前缀仍照常发送。
- **透明底图片 / 动图背后是灰底板**：图片气泡在加载完成后仍先铺了一层不透明气泡底色，透明区域因此透出灰板。现在底板只在「加载中」占位时绘制，加载完成后直接贴图（圆角仍由裁剪保证）。
- **点击面板下半区会碰到看不见的原版聊天**：面板打开时隐藏了原版聊天栏，但没有屏蔽它的点击命中测试——`ChatScreen` 仍认为当前处于「聊天聚焦」状态，会拿面板下方那几行原版聊天做命中判定。于是在面板下半区点空白处可能触发某条看不见消息的点击事件（打开链接、补全 `/tell`），或者被吞掉用于刷新未读消息；滚轮也会滚动隐藏的聊天历史。现已在这三条输入通路上屏蔽原版聊天，且仅限 AtomChat 面板打开期间。

**更改**

- **表情面板性能**：表情格子不再逐帧播放动图、也不再按原图尺寸解码，统一降采样到 128px 并只显示首帧；发送出去的表情仍按动图播放。

----

**Added**

- **Server-side media hosting**: chat images and GIFs can live on the server instead of depending on an external image host. The server reads `<gameDir>/config/atomchat/atomchat-server.json` (generated on first start, `hostingEnabled` on by default) to decide whether to host; the client probes the server's capability before sending and, if it is on, uploads the file in chunks, the server stores it content-addressed (sha256) under `<gameDir>/atomchat-data/media/`, messages carry an `atomchat-media:<id>` short link, and receivers fetch it in chunks on demand. All of it rides custom packets over the game connection — **no HTTP port is opened, nothing is exposed publicly**. The server enforces a per-file size cap, image magic-number checking, a per-player upload rate limit and a total-size/age prune; any step failing (the server has no AtomChat, hosting is off, a limit is exceeded, a timeout) falls back automatically to the external image host, behaving exactly as before. Re-sending the same image deduplicates by content and stores one copy; avatar sync is folded into the same master switch.
- **Animated GIFs**: chat images can animate (they used to stop on the first frame). Decoding composites frame by frame following `requiredFrame` / disposal, and playback is driven by the frames' durations; to bound the cost, decoding targets a 384px longest edge, at most 120 frames and an 8-million-pixel-per-image budget inside the viewport, truncating or degrading to the static first frame beyond that. The emote pack takes `.gif` too.

**Fixed**

- **Quoting your own message added a「引用 @名字: 文本」prefix inside the bubble**: the quote content is meant to travel with the message (`「引用 @名: 文本」正文`), but the local echo treated the whole string as your own body. The bubble now renders the body only, while the quote prefix is still sent as usual.
- **Transparent images and GIFs sat on a grey plate**: the image bubble still laid down an opaque bubble background after loading finished, so transparent areas showed the grey plate through. The background is now painted only for the "loading" placeholder, and once loaded the image is drawn directly (the rounded corners still come from the clip).
- **Clicking the lower half of the panel reached the invisible vanilla chat**: the panel hides the vanilla chat bar, but its click hit-testing was not blocked — `ChatScreen` still believed it was in the "chat focused" state and hit-tested the vanilla chat lines behind the panel. So clicking empty space in the panel's lower half could fire a click event on an invisible message (opening a link, completing `/tell`), or be swallowed to flush unread messages; the wheel scrolled the hidden chat history as well. All three input paths are now blocked from the vanilla chat, and only while the AtomChat panel is open.

**Changed**

- **Emote panel performance**: emote cells no longer animate frame by frame and no longer decode at the source image's size — everything is downsampled to 128px and shows the first frame only; emotes you send still play as animations.

## v0.2.6.1

**新增**

- **Forge 1.20.1 构建**：第三个平台版本，功能与 Fabric / NeoForge 1.21.1 对齐（公屏过滤、图片与头像、音效、面板交互），Skija 与 FlatLaf 通过 JarJar 内嵌，安装方式相同。

**修复**

- **带名字前缀时自己发的消息回显成两条**：服务器给玩家名加队伍 / 称号前缀、且消息经过无身份中继（NCR 类，聊天包不带发送者身份）时，自己发的那条会同时走"有身份"和"无身份"两条判定路径，表现为自己看到两条。三端现在在发送时记录内容（`PublicEchoTracker`），收到回显后按内容匹配、10 秒时效、新消息优先，命中即丢弃自己那一条；同时新增"这一行里出现了别人名字"的守卫，确保不会误吞他人消息。Forge 端另有一套按身份（UUID / 名字候选）判定的 `OwnNameMatcher`，两种判定互为兜底。

----

**Added**

- **A Forge 1.20.1 build**: the third platform target, feature-matched with Fabric / NeoForge 1.21.1 (public-feed filter, images and avatars, sound cues, panel interactions), with Skija and FlatLaf embedded through JarJar; it installs the same way, one jar into mods.

**Fixed**

- **Own messages echoed twice when a name prefix is present**: when the server decorates player names (team or title prefix) and the message travels through an identity-stripping relay (NoChatReports-style, whose chat packets carry no sender identity), your own message went down both the "identified" and the "unidentified" judging path and you saw it twice. All three targets now record what they sent (`PublicEchoTracker`), and on the echo match by content with a 10-second window and newest-first, discarding the matching line; a new guard for "another player's name appears in this line" makes sure nobody else's message is swallowed. The Forge target also keeps `OwnNameMatcher`, which judges by identity (UUID / name candidates); the two act as each other's fallback.

## v0.2.6-hotfix

**修复**

- **Fabric 端渲染自己发送的消息时崩溃退出**（本次仅涉及 Fabric 构建；NeoForge 版功能与 0.2.6 完全一致，版本号同步仅为让两个 jar 在同一 Release 中对齐）：聊天面板里 `MessageListView.Host` 匿名内部类直接读取了父类 `Screen` 的 `protected MinecraftClient client` 字段。这是 Fabric 端长期潜伏的隐患——开发时 `Screen` 的 Yarn 包名与面板类同包（`net.minecraft.client.gui.screen`），`javac` 按"同包访问 protected"放行、直发 `getfield` 且不生成桥接方法；而 Fabric 运行时把 `Screen` 重映射到 `net.minecraft.class_437`（另一个包），JVM 便以 `IllegalAccessError` 拒绝这条访问。触发条件是列表里出现任意一条自己发送的消息——文字走 `drawMessage`、图片走 `drawImageMessage`，两条路径都会调到 `drawAvatar → ownUuid`，所以表现为"一打开面板看到自己的消息、或一发图就闪退"。修复与 NeoForge 端对齐：面板持有自己的 `client` 句柄（`MinecraftClient.getInstance()`），不再继承读取父类字段。

----

**Fixed**

- **Crash on the Fabric build when rendering your own messages** (Fabric-only change; the NeoForge build is functionally identical to 0.2.6 and only carries the matching version number so both jars ship in one release): the `MessageListView.Host` anonymous inner class read the inherited `protected MinecraftClient client` field of `Screen`. This was a latent Fabric-only trap — in dev the Yarn package of `Screen` matches the panel's own package (`net.minecraft.client.gui.screen`), so `javac` allowed the same-package protected access, emitted a direct `getfield` and skipped the synthetic accessor; at runtime Fabric remaps `Screen` to `net.minecraft.class_437` in a different package and the JVM rejects the read with `IllegalAccessError`. Any own message in the list triggered it — text through `drawMessage`, images through `drawImageMessage`, both reaching `drawAvatar → ownUuid` — which is why opening the panel onto your own messages or sending an image crashed instantly. The fix mirrors the NeoForge build: the screen keeps its own `client` handle (`MinecraftClient.getInstance()`) instead of reading the inherited field.

## v0.2.6

**新增**

- **公屏消息分类过滤**：世界频道标题栏返回键旁新增过滤按钮，点击在三个视图间循环：全部 → 仅系统 → 仅玩家 → 全部。图标随状态切换（漏斗 = 全部，白色；喇叭 = 仅系统；人形 = 仅玩家），过滤生效时呈强调色。纯视图过滤：未读角标、会话列表预览与历史保存始终统计全部消息；关闭聊天面板重新打开后回到"全部"。按钮只在公屏页出现。

**修复**

- **NCR 等中继服务器上自己的气泡显示裸名**：这类服务器把玩家聊天经系统通道中继回来，捕获层拿不到发送者组件，自名装饰缓存永远不会命中。现在会从中继行切片出装饰标签（如 `[称号]名字`）并缓存，自己视角也能看到称号与颜色。
- **发送者名字行继承服务器的下划线与点击事件**：服务器聊天格式给玩家名挂的点击交互被富文本切片原样保留，公屏名字行因此带下划线且可点。名字行现在剥离点击/悬停/下划线但保留颜色；系统消息胶囊里的名字文本不受影响，服务端交互照常。
- **防刷屏合并没有时间窗**：几小时前的相同消息也会被新消息折叠计数，且时间戳被改写。现在只合并 5 分钟内的复读（与紧凑分组同窗），更早的重复消息开新行。
- **复读合并会让拖选与高亮失效**：合并以替换行对象的方式实现，而按对象引用记录的文本选区锚点、横幅跳转高亮会随之丢失。消息现在携带稳定 id，合并副本继承同一 id，上述状态不再被合并打断。
- **拖选中滚动后 Ctrl+C 复制为空**：跨消息拖选期间用滚轮滚动，端点滚出绘制窗口后复制会静默返回空串并吞掉按键。现在回退为从完整消息流复制范围内的内容；完全无可复制内容时按键放行给输入框。
- **emoji 被从中间劈开**：消息文字选取的字符定位与输入框光标定位按 UTF-16 单字符步进，点击 emoji 附近会得到代理对中间的索引，复制或续写会产生乱码。两处均已改为按码点步进。

**更改**

- **消息折行布局缓存**：绘制、高度测量与拖选命中三条路径此前每帧对整个历史列表重复做富文本折行（上限 500 条时每帧上千次测量）。折行结果现在按消息与宽度缓存（LRU 512），长列表的滚动与打开面板明显更流畅。

----

**Fixed**

- **Own bubbles showed a bare name on relay servers (NCR-style)**: these servers relay player chat through the system channel, so the capture layer never sees a sender component and the decorated self-name cache never hits. The styled label (e.g. `[Title]Name`) is now sliced off the relayed line and cached, so own bubbles show titles and colours too.
- **Sender name rows inherited server click events and underlines**: click/hover interactions the server attaches to player names survived the rich-text slice, leaving public-chat name rows underlined and clickable. Name rows now strip interactions while keeping colours; names inside system capsules are untouched and keep their server behaviour.
- **Anti-spam merging had no time window**: identical messages hours apart were folded into one counter with a rewritten timestamp. Merging now only applies within 5 minutes (the compact-grouping window); older repeats open a fresh row.
- **Merging invalidated selections and highlights**: a merge replaces the stored row object, which silently dropped text-selection anchors and notification-jump highlights held by reference. Messages now carry a stable id that merge copies inherit, so those states survive.
- **Ctrl+C copied nothing after scrolling during a selection**: scrolling with the wheel while dragging a cross-message selection moved an endpoint out of the drawn window, making the copy return an empty string and swallowing the key. It now falls back to copying whole messages from the feed, and a truly empty result no longer blocks the input field's own copy.
- **Emoji could be split in half**: character mapping in message text selection and input caret placement stepped per UTF-16 char, so clicking near an emoji produced an index inside a surrogate pair and later copy/typing corrupted it. Both now step by code point.

**Changed**

- **Layout cache for message wrapping**: drawing, height measurement and drag hit-testing each re-wrapped the entire history every frame (over a thousand measurements per frame at the 500-message cap). Wraps are now cached per message and width (LRU 512), making long-list scrolling and panel opening noticeably smoother.

## v0.2.5-hotfix

**修复**

- **NeoForge 端在部分整合包里整个 mod 加载失败**（本次仅涉及 NeoForge 构建；Fabric 版功能与 0.2.5 完全一致，版本号同步仅为让两个 jar 在同一 Release 中对齐）：通知音效原本在客户端构造函数里直接调用 `Registry.register` 写入原版 `sound_event` 注册表。某些整合包（实测 NeoForge 21.1.243 + Forgified Fabric API）里 mod 构造时注册表已经冻结，这一步会抛 `Registry is already frozen`，导致 AtomChat 整个客户端初始化失败；更严重的是 FML 会因此进入 broken mod state，跳过所有 mod 的配置加载并向全包拒绝派发事件，表现为游戏内其它 mod（如 MineMenu、Sus_InstantSwap）读取配置时崩溃。NeoForge 端已改为通过 mod 事件总线的 `RegisterEvent` 注册（NeoForge 官方路径，跑在注册表解冻窗口内），并额外捕获异常降级为警告——即使音效注册失败也只损失提示音，面板与消息功能不受影响。

----

**Fixed**

- **The NeoForge build failed to load entirely in some modpacks** (NeoForge-only change; the Fabric build is functionally identical to 0.2.5 and only carries the matching version number so both jars ship in one release): the notification cue registered itself by calling `Registry.register` on vanilla's `sound_event` registry straight from the client constructor. In some packs (observed with NeoForge 21.1.243 + Forgified Fabric API) the registries are already frozen by the time the mod is constructed, so this threw `Registry is already frozen` and took AtomChat's entire client init down. Worse, FML then entered a broken mod state: config loading was skipped for every mod and all subsequent events were refused, which surfaced as *other* mods (MineMenu, Sus_InstantSwap) crashing when they read their own config in-game. The NeoForge side now registers through the mod event bus `RegisterEvent` (NeoForge's official path, which runs inside the registry unfreeze window), with the failure additionally caught and downgraded to a warning — a missing cue now costs only the sound, never the panel.

## v0.2.5

**新增**

- **通知横幅**：被 @、被回复、收到私聊时，若 AtomChat 面板处于打开状态（或 4 秒内打开），面板顶部会弹出横幅（发送者头像 + 类型 + 名字 + 内容预览）。点击横幅跳转到那条消息并短暂高亮；最多同时显示 3 条，4 秒后淡出。面板关闭时不绘制 HUD，仅播放音效并累积 `@N` 未读角标。
- **通知音效**：上述三类事件播放同一个内置气泡提示音；面板关闭时同样生效（2 秒去重，避免刷屏时连续响）。
- **通知设置**：设置 → 聊天 →「通知」分组，含提及横幅、提及音效、私聊横幅、私聊音效四个开关、通知音量滑条与「测试音效」卡片。

**修复**

- **部分会话头像完全无法同步**：头像请求 3 秒无人应答——恰恰是对方刚加入、内置服务器最忙的时候——客户端就把整个会话判死为「服务器无伴侣」，之后再也不同步；房主侧最容易中招（对方一出现，会话卡立刻发起首个请求）。另外会话内首次头像上传在没有收到过任何应答前会被静默丢弃。现在超时只进入 5 秒冷却重试、不再判死；只有真实的通道协商结果才能标记无伴侣；协商允许后上传立即放行；并为每一步加了 debug 开关控制的 `[avatar]` 日志。
- **发送者名字显示为纯白**：尖括号行的发送者标签是合成的纯文本，丢掉了行内自带的颜色——其他玩家的名字一律按"界面文字颜色"渲染。已改为按运行段切片保留原样式；当发送者完全不带颜色时，再用记分板队伍颜色兜底（debug 模式下会打 `[sender]` 日志）。
- **面板背景颜色不可改**：开启模糊时面板叠加色是硬编码（`0xFF16191F`），改配置里的背景色完全无效。现在模糊/非模糊两条路径都用配置的背景色，并在 外观设置 里新增「背景颜色」调色行。
- **装饰名在公屏气泡里泄漏尖括号**：服务器把整个装饰名包进尖括号时（如 FTB Teams 称号 `<[称号]E33EPUS> 453`），移植自 e33chat 的解析器只会剥掉名字前的 `<`，别人看到的发送者成了 `<[称号]E33EPUS` 且带着服务器的下划线/点击样式。已补回 e33chat `cleanNameArea` 的整块剥离分支：字符串标签与富文本切片都会去掉外层 `<>`，并保留内层称号颜色。
- **自己的气泡显示裸名**：本地回显只用真实资料名，看不到自己的称号/队伍前缀。移植了 e33chat 的 `ownDisplayName()`：标签列表名 → 聊天回显中缓存到的装饰名 → 队伍前缀/颜色/后缀 → 裸名，公屏与私聊的自 bubbles 均生效；加入/断开时清空缓存。
- **会话内换头像对方看不到**：头像缓存以会话为生命周期，上传成功后没有任何通知——已缓存旧图的（或刚拿到"无头像"负缓存的）客户端要等重进世界才能刷新。现在上传成功后服务器会向所有客户端广播 `avatar_changed`，各端丢弃该 uuid 的缓存并在下一帧重新拉取；通道为可选注册，旧客户端自动忽略。
- **通知设置分组无法折叠**：0.2.5 新增的「通知」分组漏了折叠白名单登记，和其它分组不同，它一直保持展开无法收起。
- **0.2.4 的通知横幅会把整个界面推出屏幕**（回退项重做）：横幅绘制时 `saveLayer` 缺少配对的 `restore`，每帧泄漏一层画布变换；叠加逐帧的密度缩放后变换指数级放大，面板向右下飞出屏幕并表现为"无法再打开面板/按键无响应"。已改为两段 restore，并移除 HUD 绘制路径——横幅只在面板画布内绘制。

**更改**

- **通知音效换为原创合成音**：横幅提示音替换为本仓库原创合成的气泡音（正弦滑频 + 噪声瞬态），不再使用来源受限的素材，发版无版权负担。
- **聊天记录自动清理默认 7 天**：`historyRetentionDays` 默认值从 0（永久）改为 7；「保留聊天记录」开关本身仍默认关闭，开启后过期记录按周清理，不再无限增长。
- **Skia 画布护栏**：每帧绘制结束后强制回退到进入时的画布栈基线。今后任何地方再漏 restore，后果最多影响一帧，不可能再累积成界面飞出。
- **会话列表只保留在线玩家**：离线私聊卡不再常驻列表，避免每次重建都做皮肤解析和排序；离线期间该会话不在列表里（无法从列表打开），对方重新上线时卡片自动恢复，磁盘里的私聊记录不受影响。
- **别人资料页不再显示会话时长**：本地会话计时只对自己有意义，不再把它显示在对方资料下。
- **标题与标签使用真实粗体**：新增 `bundled-bold.otf`（Noto Sans CJK SC Bold），不再用描边伪粗体。

----

**Added**

- **Notification banners**: when you are @mentioned, quoted or sent a private message while the AtomChat panel is open (or within the next 4 seconds), a banner drops in at the top of the panel (sender avatar + type + name + content preview). Clicking it jumps to that message and highlights it briefly; up to three stack at once and each fades out after 4 seconds. With the panel closed no HUD is drawn — only the sound plays and the `@N` unread badge accumulates.
- **Notification sounds**: the three events above share one bundled pop cue; it also fires while the panel is closed (2s de-duplication keeps spam quiet).
- **Notification settings**: Settings → Chat → Notifications, with four switches (mention banner/sound, whisper banner/sound), a notification volume slider, and a Test sound card.

**Fixed**

- **Custom avatars failed to sync in some sessions**: if an avatar request went unanswered for 3 seconds — exactly what happens when the other player joins and the integrated server is busiest — the client latched "server has no companion" for the whole session and never synced again. The host side was the most exposed, since conversation cards fire the first request the moment the other player appears. On top of that, the first avatar upload of a session was silently dropped unless a response had already come back. Timeouts now simply retry after a 5s cooldown instead of latching; only the real channel negotiation can mark the server companion-less; uploads pass as soon as negotiation allows; and a debug-gated `[avatar]` log line was added for each step.
- **Sender names rendered pure white**: the angle-bracket slice branch synthesised a plain literal label, dropping the line's own run colours — every other player's name displayed in the fallback interface text colour. The branch now slices around the brackets run-wise (styles kept), and when a sender carries no colour at all the scoreboard team colour is applied as a fallback (debug builds log a `[sender]` line).
- **Panel background colour could not be changed**: with blur enabled the panel tint was hardcoded (`0xFF16191F`), so `panelBgColor` had no effect at all. Both the blurred and unblurred paths now tint with the configured background colour, and a "Panel background color" row was added to the appearance settings.
- **Decorated names leaked the angle brackets on public bubbles**: when the server wraps the whole decorated name in angle brackets (FTB Teams titles, `<[称号]E33EPUS> 453`), the e33chat-derived parser only stripped the `<` before the name, so other players saw `<[称号]E33EPUS` with the server's underline/click styling still attached. The `cleanNameArea` whole-wrap branch that was dropped in the port is restored: both the string label and the rich-text slice drop the wrapping pair while keeping the inner title colours.
- **Own bubbles showed a bare name**: the local echo only used the real profile name, so your own title/team prefix never showed. Ported e33chat's `ownDisplayName()`: tab-list name → decorated name cached from own chat echoes → team prefix/colour/suffix → bare name, applied to both public and private own bubbles; the cache clears on join/disconnect.
- **Avatar changes never reached the other side mid-session**: the avatar cache lives for one session and uploads triggered no notification, so a client that had already cached the old image (or negative-cached "no avatar") kept it until the next join. A successful upload now broadcasts `avatar_changed` to all clients, which drop that uuid's cache and re-request on the next frame; the channel is optional, so older clients simply ignore it.
- **The notification settings group could not be folded**: the 0.2.5 "Notifications" group was missing from the foldable-group whitelist, so unlike every other settings group it stayed permanently expanded.
- **0.2.4 banners flung the whole UI off screen** (the rolled-back feature, rebuilt): `drawBanner` pushed a `saveLayer` without a matching `restore`, leaking one canvas transform per frame; combined with the per-frame density scale the transform compounded exponentially until the panel flew off screen, which also surfaced as "cannot reopen the panel" and unresponsive keys. Fixed with paired restores, and the HUD draw path is gone — banners render on the panel canvas only.

**Changed**

- **Notification cue replaced with an original synthesized pop**: the banner sound is now a synthesized bubble pop (sine glide + noise transient) original to this repository, replacing an asset of restricted provenance — releases carry no copyright baggage.
- **Chat history auto-clean defaults to 7 days**: `historyRetentionDays` changed from 0 (forever) to 7. The "Keep chat history" switch itself stays off by default; when enabled, expired history is pruned weekly instead of growing unbounded.
- **Skia canvas guard rail**: every frame now rewinds to the canvas stack baseline it entered with. A future unbalanced save/restore can corrupt at most one frame instead of compounding into a runaway transform.
- **Online-only conversation cards**: offline private-chat cards no longer stay in the list (no per-rebuild skin resolve/sort for players you cannot message); while they are offline the conversation cannot be opened from the list, the card returns when they join, and the on-disk history is untouched.
- **Other players' profiles no longer show the session timer**: the local session timer is only meaningful for yourself, so it is hidden on other profiles.
- **Real bold headings and labels**: added `bundled-bold.otf` (Noto Sans CJK SC Bold) instead of stroke-smeared faux bold.

## v0.2.4

**新增**

- **防刷屏**：同一发送者连续发送完全相同的内容时合并为一条消息，名字旁显示 `xN` 次数；开关在设置 → 聊天。
- **紧凑消息分组**：同一发送者 5 分钟内的连续消息只保留第一条的头像与名字，后续气泡间距收紧（Discord/Telegram 式）；开关在设置 → 聊天，默认关闭。
- **跨消息文字拖选**：文字选择不再限制在单条消息内，可跨多条消息拖选，Ctrl+C 一次复制多段。
- **历史保留天数**：设置 → 聊天可精确输入保留天数（0 = 永久，1–365 天），到期历史文件在进世界时自动清理；输入框支持 Enter 提交、Esc 取消、点击其他区域自动提交。
- **数据目录治理与图片缓存压缩**：自动下载的聊天图片缓存与 companion 他人头像数据从 `config/atomchat/` 迁到 `<游戏目录>/atomchat-data/`；聊天图片磁盘缓存改为 768px WebP/PNG 压缩缓存，启动和写入时自动清理旧文件（上限 500 文件 / 100 MB），旧版迁移遗留目录自动删除；「关于」页新增「清除图片缓存」卡片（带二次确认），显示当前缓存占用。
- **设置页可折叠分组与左对齐粗体组标题**：所有设置子页的现有分区标题可折叠；组标题改为左对齐粗体并带细分隔线。

**更改**

- **历史保留天数由滑条改为右侧输入框**：避免滑条无法精确停在 1、3 等小数值；输入框常驻显示，点击即编辑。
- **紧凑消息分组默认关闭**。
- **恢复原版 bundled 字体**：撤回 MiSans/Inter 实验，避免部分中文文本粗细不一。

**修复**

- **打开聊天屏崩溃**：紧凑分组在计算最后一条消息的下一行间距时越界（`IndexOutOfBoundsException`）；边界探针现在在消息列表末尾直接返回 false。
- **档案退出动画错乱**：详情页互推返回时，`pageNavDx` 把“返回中”的档案页误判成“新页进入”，方向反/叠层；现按 push/pop 标志决定滑动方向。
- **重复消息计数角标位置与强调色**：计数移到气泡外侧垂直居中并跟随界面强调色，不再压住气泡。
- **头像 companion 协议加固**：客户端发送前检查通道是否协商，避免向无 companion 的服务器发未知包。
- **历史保留天数输入框问题**：修复点击后无光标、点其他区域不失焦、单次按键数字翻倍（1 变 11）等问题。
- **中文文本粗细不一**：回退到原 bundled 字体，消除部分字符由 MiSans/Inter 混排造成的字重差异。

----

**Added**

- **Anti-spam**: consecutive identical messages from the same sender merge into one bubble with an `xN` counter. Toggle in Settings → Chat.
- **Compact message groups**: within a same-sender five-minute run, only the first message keeps the avatar/name row and later bubbles use a tighter gap. Toggle in Settings → Chat, off by default.
- **Cross-message text selection**: drag-select text across multiple messages and copy it all with Ctrl+C.
- **History retention days**: Settings → Chat now lets you type the exact retention count (0 = forever, 1–365 days); expired files are pruned on world join. The field commits on Enter, cancels on Esc, and commits on outside click.
- **Data-dir cleanup and compressed image cache**: auto-downloaded chat-image caches and companion avatar uploads moved from `config/atomchat/` to `<gameDir>/atomchat-data/`; chat images are cached on disk as 768px WebP/PNG, trimmed at startup and after writes (500 files / 100 MB cap), and old marked migration directories are deleted automatically. About page has a confirm-guarded “Clear image cache” card showing current usage.
- **Collapsible settings groups with left-aligned bold headings**: every settings sub-page now treats section headings as foldable groups; headings are bold and left-aligned with a divider.

**Changed**

- **Retention control is an exact input field instead of a slider**: it sits on the right side of the row and always shows the current value.
- **Compact message groups default off**.
- **Restored the bundled font**: the MiSans/Inter experiment was reverted to fix uneven CJK stroke weights.

**Fixed**

- **Crash when opening the chat screen**: compact-group lookahead indexed one past the last message; the edge probe now returns false at the end of the list.
- **Profile exit animation was reversed/overlapped**: detail-to-detail pops used destination-rootness instead of the push/pop flag, so the profile page slid the wrong way.
- **Duplicate counter placement/accent**: moved beside the bubble, vertically centred, using the accent colour.
- **Avatar companion protocol hardening**: the client checks negotiated channels before sending requests/uploads.
- **Retention input issues**: no caret, no blur on outside click, and doubled digits (1 became 11) are fixed.
- **Mixed CJK font weights**: reverted to the original bundled font.

## v0.2.3

**新增**

- **常用语**：输入栏新增第四个按钮（闪电图标），点开输入栏上方浮层。点选一条即插入输入框并聚焦（**不直接发送**，改完再发）；列表底部「添加常用语」行与每行铅笔进入编辑时会**借用聊天输入框**承载输入——中文输入、IME 组字、光标全部走原生链路，回车保存、Esc 放弃、点面板外自动提交，期间输入栏以强调色描边与专属占位提示；点行右侧 × 直接删除。单条 ≤256 字符（MC 消息硬上限）、上限 20 条（到顶拒绝新增）。数据存 `atomchat-client.json` 的 `quickPhrases`。与表情面板互斥。
- **保留聊天记录（默认关）**：设置 → 聊天新增「保留聊天记录」开关。开启后按服务器/世界分别保存到 `config/atomchat/history/<键>_<哈希>.jsonl`：单人世界与局域网主机按世界名、多人按服务器列表显示名作键——**键里不含端口**，重开局域网（端口每次都变）记录不丢；同一服务器列表条目改名会换新文件（旧文件保留）。公屏与私聊都存（私聊按对象分流）；重进同一服务器自动恢复，气泡颜色与可点击事件（/tell、Xaero 坐标、FTB 按钮、链接）完整保留；写盘走 30 秒节流的单线程队列，退服/切世界立即保存。`historyRetentionDays`（默认 0 = 永久）可手改配置，进世界时清理过期文件。
- **清空聊天记录**：设置 → 聊天「保留聊天记录」下方的卡片，复用通用两段确认（红字「确定清除？」3 秒超时）。未开启保存时只清当前会话显示；开启后同时删除本机对应文件——清空后等待中的自动保存不会把记录写回（代次守卫）。

**修复**

- **常用语面板整体错位**：面板矩形误用 LTRB 语义构建，导致整块面板反转、各元素散落到不同位置；现按 XYWH 构建。
- **档案「恢复皮肤」无确认且易误触**：改为两段确认（首击行变红「确定恢复？」，3 秒内再击才执行）；无自定义头像时该行灰显不可点。
- **自定义头像在「使用皮肤」后仍显示（重启也在）**：本地清除后 companion 又把服务端残留的已上传副本端了回来；现在自己的头像只由本机文件决定。
- **档案头像 hover / 点击反馈缺失**：无自定义头像时头像直接开选择器却没有可点的视觉提示；现在 hover 恒有渐变高亮、点头像恒弹管理菜单、编辑角标直达选择器。
- **关闭常用语面板时界面飞出屏幕**：saveLayer 未与 save 成对 restore，每帧泄漏一层矩阵栈；已补齐成对恢复。

----

**Added**

- **Quick phrases**: a fourth composer button (lightning) opens a list above the input bar. Tapping a phrase inserts it into the composer and focuses it — **it never sends on its own**. Adding (the "Add a phrase" row) or editing (the pencil) <em>borrows the composer field</em> for input, so Chinese IME composition, the caret and history all work natively — Enter saves the phrase, Esc discards it, clicking elsewhere commits; the bar shows an accent ring and a dedicated hint while borrowed. Rows delete with the ×. Single phrases are capped at 256 characters and the list at 20. Stored as `quickPhrases` in `atomchat-client.json`. Mutually exclusive with the emoji panel.

**Fixed**

- **Phrase panel drawn inverted/off-screen**: the panel rect used the LTRB constructor instead of `makeXYWH`, scattering every element; rebuilt as XYWH.
- **"Use skin" ran instantly with no confirmation**: it now arms first (row turns red "Use skin?" for 3s) and only clears on the second tap; without a custom avatar the row is greyed out.
- **Custom avatar survived "use skin" (even across restarts)**: the server companion kept serving the uploaded copy after the local clear; the local player's avatar is now decided by the local file alone.
- **Avatar affordance on the profile page**: hover always glows, the avatar tap always opens the change/use-skin menu (greyed row excepted), and the edit badge stays the shortcut to the picker.
- **UI flew off-screen while the phrase panel closed**: an unpaired `saveLayer` leaked one matrix level per frame; the layer now restores before the outer save.
- **Chat history persistence (off by default)**: a "Keep chat history" switch in Settings → Chat. When on, history is saved per world to `config/atomchat/history/<key>_<hash>.jsonl`: singleplayer and LAN hosts key by world name, multiplayer by the server-list entry name — **no port in the key**, so re-opening a LAN world on a new port keeps its history; renaming a server-list entry starts a new file (the old one stays). Both public and private conversations are stored (private lines carry their partner); rejoining a world restores the feed with bubble colours and clickable spans (/tell, Xaero coordinates, FTB buttons, links) intact. Writes go through a 30-second throttled single-thread queue and flush on disconnect/world change. `historyRetentionDays` (default 0 = forever) can be hand-edited and prunes old files on world join.
- **Clear chat history**: a card under the persistence switch, using the generic two-step confirm (red "confirm?" verb, 3s timeout). With saving off it wipes the current view; with saving on it also deletes the world's file, and an in-flight auto-save cannot resurrect it (generation guard).

## v0.2.2

**新增**

- **次要胶囊配色**：系统消息、时间戳、引用胶囊的底色与文字色收拢为「次要胶囊底色 / 次要胶囊文字色」两个配置项（外观 → 气泡颜色组）。引用胶囊底色默认值随统一变更为半透明深色（原为半透明白）。
- **配色组折叠与组级重置**：外观页「气泡颜色 / 界面颜色」两个分组标题可点击折叠展开；展开时右侧「重置本组」一键恢复该组出厂配色（带二次确认）。
- **颜色项实时预览**：每个颜色配置项标题行按目标元素形状（气泡 / 胶囊 / 方块）渲染当前值小样，改色效果一眼可见。
- **通用二次确认按钮**：破坏性操作（清除壁纸等）改为右侧按钮变红「确定清除？」的确认形态，3 秒超时或点击其他位置自动取消。

**修复**

- **色板色号输入框**：色板打开时键盘事件被模态拦截，现在 hex 输入框支持 Ctrl+C 复制、Ctrl+V 粘贴（自动只保留 hex 字符，`#4A90E2` 整段可用）。
- **时间戳从未渲染**：首条消息上方现在始终显示时间戳分隔（对齐 e33chat 逻辑；「时间戳间隔」关闭时整体不显示）。
- **档案返回动画错乱**：从他人档案页返回时，滑出动画期间页面不再瞬间变回自己的档案（注入对象在页面真正离开导航栈后才重置）。

----

**Added**

- **Secondary capsule colours**: the backgrounds and text of system messages, time dividers and quote pills are now driven by two new settings (secondary capsule background / text, under the bubble colour group). The quote pill's default background moves to the shared translucent dark tone.
- **Collapsible colour groups with per-group preview**: the two colour groups on the appearance page fold via their headings, and every colour row shows a live preview square of the current value.
- **Generic two-step confirm button**: destructive actions (clear wallpaper, etc.) turn the right-hand verb into a red "confirm?" state with a 3-second timeout and click-outside cancel.

**Fixed**

- **Colour picker hex input**: keyboard events were swallowed by the modal; the hex field now supports Ctrl+C copy and Ctrl+V paste (non-hex characters are stripped, so pasting `#4A90E2` works as-is).
- **Timestamps never rendered**: the first message in a list now always carries a time divider (matching e33chat; turning the interval off disables them entirely).
- **Profile pop animation**: returning from another player's profile no longer snaps the sliding-out page back to the local player's profile.

## v0.2.0

**新增**

- **主题系统（第一档）**：设置 → 外观新增「主题」卡（当前版本预留，点击无效，显示"敬请期待"）——预设机制与配置已就位（毛玻璃 = 出厂默认 / 现代简约 = 不透明 + 无模糊 + 小圆角 + 实心卡片），等卡片可调语言稳定后回归。
- **卡片不透明度滑条**：外观 → 调整。一根轴从毛玻璃白（0%）滑到从面板色派生的不透明深灰（100%），中间自然经过半透明深灰；hover 保持统一白叠加，全轴可用。
- **边框颜色**：颜色组新增「边框颜色」（白 / 银灰 / 蓝 / 青 / 粉 / 近黑 + 调色盘）。
- **他人气泡文字色**：颜色组新增，与「自身气泡文字色」（原"气泡文字颜色"更名）分开；作用范围含对方气泡内的引用胶囊文字。
- **时间戳分隔**：聊天 → 新滑块 0–60 分钟（默认 5，0 = 关）。相邻消息间隔超过阈值时显示时钟胶囊。
- **接收图片开关**：聊天 → 默认开。关闭后图片消息显示绿色 `[图片]` 占位（不下载不缓存；手动保存仍按需拉取）。

**修复**

- **tp/tpa 三档循环切不回 auto**：旧循环 `tpa → tp` 且默认落 `tp`，一旦离开 auto 就回不去；现 auto → tp → tpa → auto。
- **个人档案复制按钮**：图标线条被 canvas 缩放放大约 1.4 倍（stroke 未除以 scale）；hover 高亮从白 45 改为选中语言白 90（旧值叠在行高亮上不可见），图标随 hover 提亮。
- **单击头像跳档案"无动画"**：从聊天页推入档案详情时走了世界↔私聊的"固定 chrome 只滑列表"分支，档案页被画成空层。现聊天 ↔ 档案详情使用整页推入（气泡 / 头像 / 输入栏整体滑动），头栏仍为固定状态栏。
- **档案复制按钮线宽 / hover、时间戳与图片占位的高度对齐**等小项随上并入。

**更改**

- **架构**：输入事件改为有序路由器（`InputRouter`，六个 handler：关闭守卫 → 模态 → 屏幕 Esc → 根页 → 聊天页），新交互不再往 if-else 链里插分支；表情 / 颜文字 / 表情包面板整体拆出为独立 `EmojiPanel` 类（约 560 行离开主屏类）。
- **质感语言分层**：浮层（菜单 / 表情面板）与悬浮件（头栏 / 输入栏 / 底栏）加菜单同款投影；内容卡加 1px 内侧受光边（白 30，随卡片不透明度自然显现）。
- **性能**：会话列表行缓存（250ms 节流，替代每帧重建 + 排序）；离线会话卡上限 30 个。
- **元信息**：作者署名 `E33EPUS`（模组列表可读）；简介统一为 "A phone-app style chat experience for Minecraft, powered by Skia."（模组列表 / README / 发布页）。

----

**Added**

- **Theme system (first tier)**: Settings → Appearance gains a Theme card (reserved in this version — clicking does nothing and it says "coming soon"); the preset mechanism and the config are in place (frosted glass = the factory default / modern minimal = opaque + no blur + small radii + solid cards), and it comes back once the card's adjustable language settles.
- **Card opacity slider**: Appearance → Tuning. One axis runs from frosted white (0%) to an opaque dark grey derived from the panel colour (100%), passing naturally through translucent dark grey; hover keeps a uniform white overlay and the whole axis is usable.
- **Border colour**: the colour group gains "Border colour" (white / silver grey / blue / cyan / pink / near-black, plus a palette).
- **Other players' bubble text colour**: added to the colour group, separate from "your own bubble text colour" (renamed from "bubble text colour"); it also covers the quote capsule's text inside their bubbles.
- **Timestamp separator**: Chat → a new slider 0–60 minutes (default 5, 0 = off). A clock capsule appears when the gap between neighbouring messages exceeds the threshold.
- **Receive-images switch**: Chat → on by default. When off, image messages show a green `[图片]` placeholder (nothing downloaded or cached; a manual save still fetches on demand).

**Fixed**

- **The tp/tpa three-way cycle could not get back to auto**: the old cycle was `tpa → tp` and defaulted to `tp`, so once you left auto you could not return; it is now auto → tp → tpa → auto.
- **The profile copy button**: its icon strokes were magnified about 1.4× by the canvas scale (the stroke was not divided by the scale); the hover highlight moved from white 45 to the selected-language white 90 (the old value was invisible on top of the row highlight), and the icon brightens on hover.
- **Clicking an avatar to open a profile had "no animation"**: pushing the profile detail from the chat page took the world ↔ private-chat branch of "fixed chrome, slide the list only", so the profile page was drawn as an empty layer. Chat ↔ profile detail now uses a whole-page push (bubbles / avatars / input bar slide together), while the header stays a fixed status bar.
- **The profile copy button's stroke width / hover and the height alignment of timestamps and the image placeholder**, plus other small items, were folded in with the above.

**Changed**

- **Architecture**: input events go through an ordered router (`InputRouter`, six handlers: close guard → modal → screen Esc → root page → chat page), so new interactions no longer insert branches into an if-else chain; the emoji / kaomoji / emote panels are split out wholesale into a separate `EmojiPanel` class (about 560 lines leaving the main screen class).
- **Layered material language**: overlays (menus / emoji panel) and floating pieces (header / input bar / bottom bar) get the same drop shadow as the menu; content cards get a 1px inner lit edge (white 30, appearing naturally with the card's opacity).
- **Performance**: conversation-list row caching (throttled at 250ms, replacing a per-frame rebuild plus sort); offline conversation cards are capped at 30.
- **Metadata**: author credited as `E33EPUS` (readable in the mod list); the description is unified to "A phone-app style chat experience for Minecraft, powered by Skia." (mod list / README / release page).

## v0.1.11

**新增**

- **tpa/tp 命令自适应**：右键菜单的「传送」不再写死 `/tp`（在装了 tpa 插件的服务器上原版 /tp 常被权限屏蔽）。三招互补：①进服探测服务器命令树（有 `tpa`/`tpaccept`/`tpahere` 即用 `/tpa`）②发出后收到"未知命令/没有权限"回包自动切换并记住 ③设置页「聊天」新增三档手动覆盖（auto/tp/tpa）。
- **TellClickDetector 归因移植**（e33chat 2.3.14 层 2）：发送者名带 `/tell`/`/msg` SUGGEST_COMMAND 点击事件时，命令值携带真实档案名——昵称服务器上的确定性归因，不再依赖文本匹配。
- **EasyBotParser 移植**：内置识别 EasyBot QQ 群转发消息（`[群名] <昵称(QQ号)> 内容` 等四种实战形状，QQ 号解析 + 广播标签守卫 + 已知玩家让路）。无服务器环境，逻辑与母本一致并附单测。
- **@ 提及检测**（MentionDetector 移植）：`@名字` 或（可配置的）裸名字提及自己时，公屏卡片显示琥珀色 `@n` 未读提及角标；预留 `MentionObserver` 通知接口，横幅/音效后续版本接入。设置页「聊天」新增 `mentionRequireAt` 配置。

**修复**

- **输入框鼠标点选与拖选**：点击输入栏此前只设焦点、从不把事件映射到文本，EditBox 的原生点选/拖选从未生效。现在自建"虚拟坐标→字符索引"映射（多行 Skia 换行布局，按字符中点取位），支持单击定位、拖动选区、Shift 点击扩展选区（渲染沿用 EditBox 选区状态）。

**更改**

- **WATUT 集成：「对方正在输入…」**：纯反射读取已安装 WATUT 的客户端状态（CHAT_TYPING），私聊输入框空置时占位符切换为「对方正在输入…」。不装 WATUT 静默降级；无 payload 接收器注册、不与 WATUT 自身冲突。聊天区域的原版提示若被面板盖住，面板内的这个显示即替代。
- **个人档案页重设计**：延迟 / 在线时长 / 统计从三张等权信息卡改为 hero 卡下方的一行三格仪表盘磁贴（大数值 + 小标签，点击复制完整值），身份项（名字 / UUID / 身份 / 服务器）保留为分组行——卡片数量 8→5，信息层级分明。
- **图片按钮图标重绘**：字形占满 ~14×12 viewBox（旧版 13×9 按最长边缩放后高度比表情/发送矮 30%），渲染尺寸从 18×12.5 提升到 18×15.4。
- **私聊文本兜底新增关键词锚定族**（e33chat WhisperDetector/WhisperSignal 移植）：已知玩家名 + 冒号前私聊关键词（悄悄/私聊/密语/私信/密谈/whisper/pm/msg/tell，词边界防误判）即认领为私聊，覆盖结构族表达不了的装饰形状（如 `[VIP] Steve 私聊说: hi`）。

**评估（未实施）**

- **ChatStore 持久化**：维持内存单例的现状。上 keyed storage（按服务器/世界隔离 + JSONL 落盘 + join 清理）与 0.1.10 的头像缓存策略、跨服隔离语义有耦合，需要单独一轮设计；当前行为在 CHANGELOG/README 中如实标注为已知限制。
- 跨消息文字拖选、IME 组字窗内联定位：前者是中等特性（选区状态需消息级化），后者受 vanilla 无公开 IME 定位 API 限制（e33chat 同样未解），均顺延 0.1.12。

----

**Added**

- **tpa/tp command adaptation**: the context menu's "Teleport" no longer hard-wires `/tp` (on servers running a tpa plugin, vanilla /tp is usually blocked by permissions). Three complementary moves: ① probe the server's command tree on join (if `tpa`/`tpaccept`/`tpahere` exist, use `/tpa`) ② on receiving an "unknown command / no permission" reply after sending, switch automatically and remember it ③ Settings → Chat gains a three-way manual override (auto/tp/tpa).
- **TellClickDetector attribution ported** (e33chat 2.3.14 layer 2): when the sender's name carries a `/tell`/`/msg` SUGGEST_COMMAND click event, the command value carries the real profile name — deterministic attribution on nickname servers, no longer dependent on text matching.
- **EasyBotParser ported**: built-in recognition of EasyBot QQ group relay messages (four shapes seen in the field, such as `[群名] <昵称(QQ号)> 内容`; QQ-number parsing + a broadcast-label guard + yielding to known players). There is no server component, the logic matches the upstream and unit tests come with it.
- **@ mention detection** (MentionDetector ported): when `@name` or a (configurable) bare name mentions you, the public-feed card shows an amber `@n` unread-mention badge; the `MentionObserver` notification interface is reserved for banners and sound cues in a later version. Settings → Chat gains the `mentionRequireAt` option.

**Fixed**

- **Mouse click and drag selection in the input box**: clicking the input bar only ever set focus and never mapped the event to text, so the EditBox's native click and drag selection never worked. There is now a hand-built "virtual coordinate → character index" mapping (multi-line Skia wrap layout, picking the position by each character's midpoint), supporting click to place the caret, drag to select and Shift-click to extend the selection (rendering reuses the EditBox's selection state).

**Changed**

- **WATUT integration: "the other player is typing…"**: pure reflection reads an installed WATUT's client state (CHAT_TYPING) and, when the private-chat input is empty, the placeholder switches to "the other player is typing…". Without WATUT it degrades silently; no payload receiver is registered and it does not conflict with WATUT itself. If the vanilla hint in the chat area is covered by the panel, this in-panel display replaces it.
- **Profile page redesigned**: latency / online time / statistics go from three equal-weight info cards to a single row of three gauge tiles below the hero card (large value + small label, click to copy the full value), while the identity items (name / UUID / identity / server) stay as grouped rows — card count 8→5, with a clear information hierarchy.
- **Image button icon redrawn**: the glyph now fills a ~14×12 viewBox (the old 13×9, scaled by its longest edge, came out 30% shorter than the emoji and send icons), and the render size goes from 18×12.5 to 18×15.4.
- **A keyword-anchored family added to the private-chat text fallback** (e33chat's WhisperDetector/WhisperSignal ported): a known player name plus a private-chat keyword before the colon (悄悄/私聊/密语/私信/密谈/whisper/pm/msg/tell, with word boundaries to prevent false hits) claims the line as a whisper, covering decorated shapes the structural family cannot express (such as `[VIP] Steve 私聊说: hi`).

**Assessed (not implemented)**

- **ChatStore persistence**: keeping the in-memory singleton as it is. Moving to keyed storage (isolated per server/world + JSONL on disk + cleanup on join) is coupled to 0.1.10's avatar-cache strategy and to the cross-server isolation semantics, and needs a design round of its own; the current behaviour is documented honestly as a known limitation in the CHANGELOG/README.
- Cross-message text drag selection and inline positioning of the IME composition window: the former is a medium-sized feature (selection state has to become message-level), the latter is limited by vanilla having no public IME positioning API (e33chat has not solved it either); both are pushed to 0.1.12.

## v0.1.10

**新增**

- **服务端 companion：自定义头像跨端可见**（同 jar 双端入口，e33chat「装了才生效」理念）：双开联机（内置服务端）或独立 Fabric 服装 AtomChat 后，玩家可互相看到自定义头像。协议三条：本机设置头像后自动上传（≤256KB PNG、限频 60s、校验 uuid 防伪造 + PNG 魔数）；他人头像懒加载（渲染遇到未缓存的 uuid 才请求，去重 + 3s 探测超时 → 无 companion 服务器本会话静默降级为皮肤）；无头像负缓存 30s。缓存按会话存活（进服清空重拉），头像变更重进后生效，服务端零状态。
- **单击对方头像跳转其个人档案**：QQ 式竞争窗口——单击后 300ms 内无第二次点击 → 跳对方档案页（身份卡/信息行按对方数据渲染）；双击仍是戳一戳。戳一戳或界面动画关闭时双击本就无动作，单击免窗口立即跳。离开档案页自动回到自己的档案。
- **调色盘与裁剪器对称淡出**：HSV 调色盘、图片裁剪器补齐关闭动画（倒放打开曲线，110ms；淡出期间输入仍被吞掉，动画播完才真正关闭）。至此全部浮层淡入淡出对称。

**更改**

- **ImageLoader 四件套**（图片性能）：①只有滚进可视区的图片才发起下载（刷屏不再被动并发全下）②解码降采样到长边 ≤768（20MP 照片从 ~80MB 常驻内存降到 ~2MB，画质仍远超气泡显示尺寸）③内存 LRU 48 张 + 失败负缓存 60s（坏图不再每帧重试）④磁盘缓存 `config/atomchat/image-cache/`（重进不重下）。头像侧本就按皮肤永久缓存（每皮肤 64×64 只读一次 GL），未动。
- **对方气泡默认色**改为 `#2C3E50` 深蓝灰（与配置文件已持久化值一致；新装用户直接生效）。

----

**Change**

- **Server companion: cross-client custom avatars** (same-jar dual entrypoint, the e33chat "works only where installed" philosophy): with AtomChat on both ends of a double-open LAN session (integrated server) or on a dedicated Fabric server, players see each other's custom avatars. Three payloads: auto-upload on set (≤256KB PNG, 60s rate limit, sender-uuid anti-spoof + PNG magic check), lazy per-uuid requests with dedup and a 3s probe timeout (silent skin degradation on servers without the companion), and a 30s negative cache for no-avatar answers. Cache lives for one session (wiped on join); an avatar change shows up after re-entering; the server keeps zero state.
- **Single click opens another player's profile**: QQ-style 300ms competition window — the single click opens the profile only if no second click arrives; double click still pokes. With poke (or decorative motion) off, the click jumps immediately.
- **Symmetric fade-outs for the HSV colour picker and image cropper** (reverse of the open curve, 110ms; input stays swallowed until the fade finishes). Every overlay now fades both ways.

- **ImageLoader hardening**: ①images download only when scrolled into view (a spam burst no longer queues the whole scrollback) ②decoded bitmaps downscale to a ≤768px long edge (~80MB → ~2MB for a 20MP photo) ③48-entry memory LRU + 60s negative cache for failures ④disk cache under `config/atomchat/image-cache/`. Avatar rendering already cached per skin (one GL readback each) and was left as is.
- **Others' bubble default colour** is now `#2C3E50`.

## v0.1.9

**新增**

- **服务器格式模板**（e33chat 同款结构解，纯客户端配置）：守卫解析不出的系统通道消息可由用户自定义模板认领。在 `config/atomchat/atomchat-client.json` 手工配置 `chatTemplates` / `whisperTemplates`（打开聊天屏时生效，免重启）。占位符：`{name}`（玩家名，锚定在线/已知玩家）/ `{display_name}`（装饰名）/ `{prefix}` / `{suffix}` / `{sep}` / `{content}`（恰好一个、任意位置，支持后缀式）。多模板首匹配胜出。配置文件注释内含 EssentialsX / CMI / DeluxeChat / VentureChat 默认格式示例。
- **插件私聊文本兜底**（e33chat G1 移植 + 扩展）：vanilla 翻译 key 之外的文本形态私聊（插件改写 /msg、机器人中继）现在能识别并进入私聊面板 + 未读红点。支持箭头系（EssentialsX `[Steve -> 我] hi`、CMI `[/msg from [Steve]]`、DeluxeChat `Steve -> 我 : hi`）与关键词系（`悄悄地对你说` / `whispers to you` / 你发出的 `你对X悄悄地说`）；只有当一端是自己（或 `我`/`me` 字面量）才认领，其余留给公屏守卫；发送方向按 PrivateEchoTracker 既有语义去重。对端经在线/离线记忆解析身份。
- **解析失败诊断**（e33chat G4 同款）：`debug=true` 时整条认领链（权威 key → 文本私聊 → 守卫 → 模板）全部失手会在日志记一行原始消息，真实服务器上的未知格式可凭日志回修。

**修复**

- **多色 § 码嵌名失明**（e33chat G3 补全）：服务器把名字用色码拆开时（`S§6t§beve`），原实现用裸名对原文 `indexOf` 必然失配 → 消息掉灰字。现在名字匹配允许中间夹 § 码对，偏移保持原文坐标，富文本切片与装饰标签不受影响；尖括号路径的合成标签同步剥码。新增 4 项回归测试。

**更改**

- 模板编译全链路防御：占位符重复（`{name}{name}`）、缺 `{content}`、正则编译失败一律拒绝该模板并记日志，不再影响其它模板（e33chat 2.2.7 崩溃穿透教训）。
- ⚠️ 环境坑存档：**JDK 21.0.11（2026-04 LTS）的 `java.util.regex` 不再接受命名捕获组名中的 `_` 与 `-`**（报 "named capturing group is missing trailing '>'"）。模板正则的组名全部改为无下划线（`gname`/`gdisp`/`gprefix`/`gsuffix`/`gcontent`）。

**新增测试**

- MessagePresentation +4（多色嵌名）/ WhisperTextParserTest 15 项 / ChatTemplatesTest 14 项，全量 210 项绿。

----

**Change**

- **Server-format templates** (e33chat parity, client-side): user-defined templates can now claim system-channel lines the guards cannot parse. Hand-edit `chatTemplates` / `whisperTemplates` in `config/atomchat/atomchat-client.json` (effective when a chat screen opens, no restart). Placeholders: `{name}` (anchored to known players) / `{display_name}` / `{prefix}` / `{suffix}` / `{sep}` / `{content}` (exactly one, any position — suffix style supported). First match wins. Real plugin default formats (EssentialsX / CMI / DeluxeChat / VentureChat) ship as config comment examples.
- **Plugin whisper text fallback** (e33chat G1 port + extension): text-shaped private messages beyond vanilla translation keys (plugin-reformatted /msg, bot relays) now enter the private panel with the unread badge. Arrow family (EssentialsX / CMI / DeluxeChat) and keyword family (`whispers to you`, Chinese variants) are supported; a line is only claimed when one side is the local player; outgoing echoes follow the existing PrivateEchoTracker semantics.
- **Parse-miss diagnostics** (e33chat G4 parity): with `debug=true`, a line the whole claim chain fails on is logged verbatim so unknown real-server formats can be fixed from the log alone.

**Fix**

- **Color-code split names went blind** (e33chat G3 completion): when a server splits a name with § pairs (`S§6t§beve`), the old `indexOf(cleanName)` on the raw line always failed and the message degraded to a gray capsule. Name matching now tolerates interleaved § pairs while keeping raw-line offsets for rich-text slicing; 4 regression tests added.
- Template compilation is fully defensive: duplicate placeholders, a missing `{content}` or a broken regex reject just that template with a log line (the e33chat 2.2.7 crash-through lesson).
- ⚠️ Environment note: **JDK 21.0.11 (2026-04 LTS) no longer accepts `_` or `-` in named capturing groups**; template group names are underscore-free accordingly.

## v0.1.8

**新增**

- **个人档案页**：底部「个人」标签从占位页换成真实页面——顶部身份卡（大号圆形头像 + 常驻「编辑」角标 + 玩家名），下方信息卡逐行展示名字 / UUID / 延迟 / 身份（是否 OP）/ 在线时长 / 游戏统计（挖掘·击杀·里程）/ 服务器地址，**点任意一行即复制该值**。延迟与身份取自玩家列表（原版协议只同步自己的权限等级，其他玩家隐藏该行）；单人世界显示「单人世界」。
- **本机自定义头像**：点头像「编辑」角标选图，选完进入 **QQ 式裁剪界面**（面板内模态：居中圆圈固定、图片拖动平移、滚轮以圆心为锚缩放、双击重置，图片永远盖住圆圈不露边；底部对勾/叉双圆按钮，Esc 取消），确认后按可视区域裁剪为 256px PNG 存 `config/atomchat/avatar/`，清除恢复皮肤。生效范围为本机（档案页 + 自己的气泡）；跨端互通规划为服务端 companion 功能，companion 缺席时静默降级为皮肤（e33chat 优雅降级理念）。
- **颜色配置**：外观页新增「颜色」分组——气泡文字颜色 / 自身气泡颜色 / 对方气泡颜色 / 界面文字颜色 / 强调色。每行为一排预设色板（点即生效并写盘），尾部「+」格打开 **HSV 调色盘**（饱和度×亮度方块 + 色相条，实时预览圆点 + 可复制的 hex 蓝链 + hex 输入框：实时应用、失焦自动应用合法值、Esc 先退聚焦）。手改配置文件中的自定义色会追加显示且可选。
- **离线玩家识别**（移植自 e33chat）：记忆曾在聊天中出现的玩家（名字↔UUID，LRU 512）。机器人桥/中继转发的已下线玩家消息仍能解析为真实气泡与头像，而不是系统灰字；皮肤沿用名字键缓存的上次已知头像。
- **Mod 图标与链接**：修复 ModMenu/PCL 读不到图标（补 `icon` 字段），补 `contact` 主页/议题/源码链接使 mod 列表的 website 与 issues 按钮可跳转。

**更改**

- **次要文字颜色不再单独配置**：从「界面文字颜色」自动派生（同色相、降饱和降亮度），两组文字永远协调；配置文件中的旧值将被忽略。
- **底栏与输入按钮状态色**：底栏选中 tab 的图标变强调色（点击态）；表情按钮在面板展开期间保持强调色，图片/表情按钮按压瞬间高亮。
- **图片按钮图标重绘**：从竖版照片改为横版图片字形。
- 滑块数值（90% / x1.00 等）与色板色号文案改为纯白。

**修复**

- **颜文字消息被识别为系统灰字**：整条消息是一个平衡括号组时（如 `(￣▽￣)`、`(≧▽≦)`、`【滑稽】`），`MessagePresentation` 的分隔符跳过逻辑会把它当作名字后缀装饰（本意是解析 `[AFK]`/`(VIP)`）整段吞掉，导致解析失败。在 NCR 服务器上（玩家聊天经系统通道广播），回声捕获失败 → 再次解析仍失败 → 被当作系统消息渲染成灰色胶囊。现在括号跳过吞掉全部剩余文本时，自动回退为「括号组属于内容」，发送者标签收回为裸名字。
- 新增 3 项 `MessagePresentation` 回归测试（尖括号/冒号/全角括号三种格式）。

----

**Change**

- **Profile page**: the "Profile" tab now shows a real page — a hero identity card (large circular avatar with a persistent edit badge and the player name) above copyable info rows: name / UUID / ping / role (OP) / session time / stats (mined · kills · walked) / server address.
- **Local custom avatar**: pick an image via the avatar's edit badge, crop it in a QQ-style modal (fixed centred circle, drag to pan, wheel zooms around the circle centre, double-click resets, the image always covers the frame), and the visible region is stored as a 256px PNG under `config/atomchat/avatar/`. Local-only for now; cross-client sync is planned as a server-companion feature and silently degrades to the skin when it is absent (the e33chat philosophy).
- **Colour settings**: a new Colors group in Appearance — bubble text / your bubble / others' bubble / interface text / accent. Each row is a preset swatch strip (applies and saves instantly) with a trailing "+" opening an HSV picker (saturation×brightness square + hue bar, live preview dot, a copyable hex link and a hex input that applies live). Custom colours from a hand-edited config still render and stay selectable.
- **Offline player memory** (ported from e33chat): players once seen in chat are remembered (name ↔ UUID, LRU 512) so relayed lines from offline players parse as real bubbles with their last-known skin instead of gray system capsules.
- **Mod metadata**: fixed the icon not loading in ModMenu/PCL and made the website/issues buttons open the GitHub repository.
- **Changed**: the secondary text colour is now derived from the interface text colour (same hue, desaturated and darkened) and is no longer a separate setting; the selected bottom-tab icon and the emoji button (while its panel is open) take the accent colour; the image button glyph was redrawn as a landscape photo; slider/swatch values are pure white.
- **Fixed**: bracket-only kaomoji no longer render as gray system bubbles on NCR servers.

## v0.1.7

**更改/修复**

- **设置磁贴改方形**：四个磁贴从横矩形改为正方形，SVG 图标与文案作为一组垂直居中（图标在上、单行文案在下），移除副标题描述。
- **关于页重排**：每个条目改为「标题上 / 值下」两行，修复第三方组件名与值互相覆盖。
- **关于页蓝链**：GitHub 仓库、MIT 许可证，以及 Skija / Skia / FlatLaf 三个组件全部可点击跳转（蓝色 + 下划线，用 MC 自带能力打开浏览器；全屏下浏览器会开在游戏后面）。
- **新增滑块**：外观页新增三个连续值设置——背景不透明度（30–100%）、面板宽度（320–600）、界面缩放（x0.75–x1.50）。拖动手柄改值并即时写盘；点轨道一次 ±一个步进；拖动期间列表滚动被屏蔽。
- **界面缩放为真实即时缩放**：在 Skia canvas 的设计密度上乘以系数，并同步作用到全部坐标换算（`uiDensity()` 单点收敛，鼠标、模糊 pre-pass、输入法锚点自动跟随）。UiTokens 常量保持类加载期求值，不做运行时重建。
- **背景不透明度同时作用于模糊底与实色底**：一个滑块统一控制面板透出世界的程度，与「背景模糊」开关正交。
- **返回时标题立即切换**：页面推入/弹出的顶栏标题改为始终显示目标页，返回动画第一帧就切到上一级标题，不再等滑出结束。
- **面板宽度安全性确认**：Skia surface 按整个帧缓冲创建，面板只是每帧重算的矩形，改动宽度即时重排，不存在拉伸问题。
- **关于页新增 Hero 卡**：顶部一张更高的卡片，左侧白色圆角底板内嵌 Mod logo，右侧 AtomChat 字标（图片接口已预留，未来可整体替换为艺术字 logo）；logo 已降采样到 256px（681KB → 33KB）。
- **链接卡片提示重做**：「点击跳转」改为主字号纯白、垂直居中，作为卡片的主动作而非脚注。
- **外观图标重绘**：从调色板改为三段调节滑杆（与「调整」分组语义一致，原图案点在磁贴尺寸下发虚）。
- **壁纸卡片动词同款**：「选择图片 / 清除」与链接卡片的「点击跳转」一致，改为主字号纯白、右侧垂直居中。
- **修复空态误判**：聊天页会话列表滚动后如果所有卡片都滚出视野，会错误地画出「无在线玩家」空态；现在改为按数据（是否存在玩家行）判断，而不是按屏幕上可见的卡片。
- **性能优化**：文字测量新增 4096 条 LRU 缓存（文字整形是 UI 层最热的 CPU 路径，同一标签每帧都在重复测量）；截断算法从 O(n²) 次测量改为二分查找；设置目录（开关/滑块定义）与玩家卡排序比较器改为只构建一次，不再每帧重建。

----

**Change/Fix**

- **Square settings tiles**: the four tiles changed from wide rectangles to squares, with the SVG glyph and a single label grouped and vertically centred (icon above, one text line below); the subtitle captions are gone.
- **About page relayout**: each entry is now two lines (title above, value below), fixing the third-party component names overlapping their values.
- **About page links**: the GitHub repository, the MIT license and the Skija / Skia / FlatLaf components are all clickable (blue + underline, opened through MC's browser hook; in fullscreen the browser opens behind the game).
- **New sliders**: the Appearance section gains three continuous settings — background opacity (30–100%), panel width (320–600) and interface scale (x0.75–x1.50). Dragging the handle writes through to the config live; clicking the track nudges by one step; list scrolling is suppressed while dragging.
- **Interface scale is really live**: the factor multiplies the Skia design density and every coordinate conversion through the single `uiDensity()` funnel (mouse, blur pre-pass and IME anchoring follow automatically). UiTokens constants stay class-initialised and are never rebuilt at runtime.
- **Background opacity covers both backgrounds**: one slider governs how much world shows through, applied to the blurred tint and the solid fallback alike, orthogonal to the blur switch.
- **Back titles switch immediately**: the pushed-page header now always names the destination, so a pop flips to the parent title on the first frame instead of waiting for the slide-out.
- **Panel width is safe**: the Skia surface spans the whole framebuffer and the panel is just a per-frame rect, so changing width re-lays out instantly — no stretching involved.
- **About-page hero card**: a taller card on top with the mod logo on a white rounded plate and the AtomChat wordmark beside it (the image slot is an interface, ready for an art-text logo later). The logo is downsampled to 256px (681KB → 33KB).
- **Link-card hint reworked**: the "Open" cue is now title-sized, pure white and vertically centred — a call to action, not a footnote.
- **Appearance icon redrawn**: palette replaced by three adjustment sliders, matching the Adjustments group; the old dots went fuzzy at tile size.
- **Wallpaper card verbs match**: "Choose" / "Clear" now use the same title-sized, white, vertically-centred style as the link cards' "Open" cue.
- **Fixed a false empty state**: scrolling the conversation list until every card left the viewport wrongly showed the "No players online" state; it is now decided from the data (whether player rows exist), not from what is visible.
- **Performance**: text measurement now goes through a 4096-entry LRU cache (text shaping is the hottest CPU path in the UI — identical labels were being re-shaped every frame); truncation switched from O(n²) measurements to a binary search; the settings catalog (switch/slider definitions) and the player-card sort comparator are built once instead of per frame.

## v0.1.6

**更改/修复**

- **设置页上线**：底栏「设置」不再是占位页，改为 Win11 风格的 2×2 磁贴主页（外观 / 聊天 / 隐私与屏蔽 / 关于），每个磁贴带自绘线性 SVG 图标、标题与副标题说明。
- **设置子页**：点击磁贴进入对应设置界面，复用与公屏/私聊一致的全宽 push/pop 转场；左上角返回箭头（或 Esc）返回，底部不再绘制 tab 栏，列表吃满面板高度。
- **开关控件**：每个配置项卡片右侧带 iOS 比例开关（140ms easeOutCubic 滑块，开=主题蓝、关=半透明白、旋钮纯白）。
- **配置热更新**：任意开关切换立即写回 `atomchat-client.json`，全部选项即时生效，无需重启游戏。
- **新开关**：背景模糊、界面动画（装饰动效总闸）、消息入场动画、双击头像戳一戳、隐藏被屏蔽玩家的消息、调试模式。
- **「界面动画」为真实开关**：此前 `animationEnabled` 字段从未被任何代码读取；现已接入消息入场、页面转场、面板开合、头像 poke 抖动与滚动吸底。hover 反馈与滚轮惯性保留，UI 不会失去响应感。
- **隐私语义可选**：屏蔽玩家原本会直接丢弃其公屏消息；现可关闭「隐藏被屏蔽玩家的消息」让公屏仍可见，但会话卡片仍灰化、私聊仍只读。
- **布局**：`UiLayout` 新增 `DETAIL` 模式（无输入栏、无 tab 栏），并补充几何单测锁定列表恰好回收 tab 栏高度。

- **私聊名字去交互**：私聊页里对方名字不再有下划线/可点击，保留颜色与装饰；公屏名字点击行为不变。
- **远端引用解析**：收到带 `「引用 @名字: 内容」` 前缀的公屏/私聊消息时，接收端会解析成引用胶囊 + 正文，不再把整段前缀当正文显示。
- **原版私聊行改写**：vanilla `/msg` 系统消息改为 e33chat 同款 `<名字>[私聊] 正文`（紫色标签），不再显示 “whispers to you” 系统句。
- **卡片时间与状态点**：根列表卡片时间改为纯白；玩家卡名字右侧新增在线绿点 / 离线红点。
- **@ 图标重绘**：头像右键菜单的 @ 图标改为 Lucide at-sign 风格的真 @ 线性图标。
- **菜单淡出文案修复**：公屏头像右键菜单淡出时，“屏蔽/取消屏蔽”不再因目标被清空而显示异常。
- **私聊引用预览补 [引用]**：私聊里引用消息时，原版聊天框预览会显示 `[引用]`/`[Quote]` 占位，不再露出整段 `「引用...」` 前缀。
- **引用图片不刷 URL**：引用图片消息时，引用内容改为绿色 `[图片]`/`[Image]`，不会把图片 URL/CICode 放进引用胶囊。
- **公屏↔私聊切屏动画**：从公屏头像右键“私聊”进入/返回私聊时，消息列表现在使用与根页一致的全宽 push/pop 动画。

----

**Change/Fix**

- **Settings page ships**: the Settings tab is no longer a placeholder but a Windows-11-style 2x2 tile grid (Appearance / Chat / Privacy & blocking / About), each tile with a hand-drawn line icon, title and caption.
- **Settings sub-pages**: tapping a tile opens the section, reusing the same full-width push/pop transition as the public/private pages; back arrow (or Esc) returns, the tab bar is gone and the list uses the full panel height.
- **Toggle switch**: every option card carries an iOS-proportioned switch (140ms easeOutCubic knob, accent blue when on, translucent white when off, pure white knob).
- **Live config**: any toggle writes `atomchat-client.json` immediately; every option takes effect at once, no restart needed.
- **New switches**: background blur, interface animations (decorative-motion master), message entrance, double-tap avatar poke, hide blocked players' messages, debug mode.
- **"Interface animations" is a real switch**: the `animationEnabled` field was previously never read by any code; it now gates message entrances, page transitions, the panel open slide, the poke shake and scroll snapping. Hover feedback and wheel glide are kept so the UI never feels unresponsive.
- **Optional privacy semantics**: blocking a player used to drop their public messages outright; turning off "Hide blocked players' messages" keeps them visible in public chat while the card stays greyed out and private chat stays read-only.
- **Layout**: `UiLayout` gains a `DETAIL` mode (no composer, no tab bar) with unit tests asserting the list reclaims exactly the tab bar height.

- **Private sender names are no longer interactive**: in private chats the other player's name no longer shows as an underlined clickable link; public chat names keep their click behaviour.
- **Remote quote parsing**: incoming public/private messages with a `「引用 @name: text」` prefix now reconstruct the quote capsule and body instead of showing the raw prefix as bubble text.
- **Vanilla private line rewrite**: vanilla /msg system lines become e33chat-style `<name>[Whisper] body` with a purple tag instead of “whispers to you”.
- **Card time and status dot**: conversation-card time is now pure white; player cards show an online green / offline red dot right after the name.
- **@ icon redraw**: the avatar context-menu mention icon is now a true linear @ in the Lucide at-sign style.
- **Context-menu fade fix**: the Block/Unblock label no longer misbehaves while the public avatar menu is fading out.
- **Private quote preview shows [Quote]**: quoting inside private chat now renders a `[Quote]`/`[引用]` placeholder in the vanilla chat line instead of the raw `「引用...」` prefix.
- **Image quotes no longer leak URLs**: quoting an image message puts a green `[Image]`/`[图片]` placeholder in the quote capsule instead of the URL/CICode.
- **Public ↔ private page animation**: entering/leaving a private conversation from the public avatar menu now uses the same full-width push/pop animation as the root pages.

## v0.1.5

**更改/修复**

- **根页会话列表重写（QQ 同款）**：Public 固定置顶，随后按规则列出当前服务器全部在线玩家，再补“最近私聊过但现在离线”的玩家；无分组标题，列表支持滚动。
- **玩家卡片**：显示真实 ID、皮肤圆形头像（预留 `PlayerAvatarSource` 接口，后续可接自定义头像）、最近消息预览、时间；右上角未读红点计数（>99 显示 99+），屏蔽玩家整卡黑白滤镜。
- **私聊页面**：新增带目标的 PRIVATE_CHAT 页面；根列表左键玩家卡进入私聊，公屏头像右键“私聊”也进入；每个私聊会话独立保存消息历史、草稿与滚动位置；Header 显示真实 ID + 在线/离线小圆点。
- **私聊捕获**：按 vanilla `/msg` 翻译键（incoming/outgoing）分流到 `PrivateChatStore`，不混入公屏；发送时本地立即上屏，并移植 e33chat 的 pending echo 抑制，避免服务器回显双份。
- **发送语义**：私聊页普通文本自动拼 `/msg <真实ID>`，以 `/` 开头原样作为命令；图片/表情/引用/拖放/消息右键菜单随会话视图完整复用。
- **会话视图复用**：公屏与私聊共用消息渲染/输入栏/滚动/回复/表情体系；新增“回到最新”右下圆形向下箭头按钮，不在吸底状态时浮出，点击平滑回底（公屏与私聊都有）。
- **右键玩家菜单**：根列表玩家卡右键 = 传送（在线可点/离线置灰）+ 屏蔽/取消屏蔽；公屏头像菜单 = @提及 / 私聊 / 传送（在线可点/离线置灰）/ 屏蔽/取消屏蔽；传送固定 `/tp <真实ID>`。
- **屏蔽系统**：名单全局持久化到 `atomchat-client.json`；保留旧消息只挡新消息；已屏蔽玩家卡片黑白滤镜，仍可进入私聊查看历史但输入栏只读。
- **离线与只读**：离线最近会话可进入查看历史，输入栏显示“对方不在线，无法发送”；已屏蔽同理。
- **导航状态恢复**：Y 键重开会恢复上次页面栈，包括正在浏览的私聊会话目标。
- **未读体系**：Public 与每个私聊会话都维护未读数，进入会话动画开始时清零；根列表实时从 Tab 名单刷新上下线。

----

**Change/Fix**

- **Root conversation list rewrite (QQ style)**: Public stays pinned at the top, then every online player on the current server is listed by rule, followed by recently-chatted players who are now offline; no group headers, scrolling supported.
- **Player cards**: real profile ID, circular skin avatar (behind a `PlayerAvatarSource` interface ready for custom avatars), latest message preview and time; unread red badge on the right (>99 becomes 99+); blocked players get a full-card grayscale filter.
- **Private chat pages**: a target-aware PRIVATE_CHAT page; left-clicking a player card enters it, and the public-chat avatar right-click Whisper item also enters it. Each conversation keeps its own history, draft and scroll position; the header shows the real ID with an online/offline dot.
- **Private capture**: vanilla `/msg` translation keys (incoming/outgoing) are routed into `PrivateChatStore`, never into the public feed; sends appear locally immediately and use the e33chat-style pending echo suppression so the server echo cannot duplicate bubbles.
- **Send semantics**: in a private page plain text is auto-prefixed with `/msg <real ID>`; slash input is sent as-is as a command. Images/emotes/quotes/drop/context menus are inherited from the shared chat view.
- **Shared chat view**: public and private channels reuse the same message rendering/input/scroll/reply/emoji stack. A new circular “jump to latest” down-arrow button floats bottom-right when the view is not at the bottom and scrolls smoothly back down (both public and private).
- **Player right-click menus**: root player card = Teleport (enabled online / greyed offline) + Block/Unblock; public avatar menu = Mention / Whisper / Teleport (enabled online / greyed offline) / Block/Unblock; teleport uses `/tp <real ID>`.
- **Block system**: the global list persists to `atomchat-client.json`; old messages are kept and only new ones are filtered; blocked cards are grayscale and still open read-only history.
- **Offline/read-only**: offline recent chats open read-only with a “player is offline” composer; blocked conversations behave the same.
- **Navigation restore**: the Y hotkey restores the previous page stack, including the private conversation that was open.
- **Unread system**: Public and each private conversation track unread counts; entering a conversation clears its badge at animation start; the root list refreshes online/offline status every frame from the tab list.

## v0.1.4

**更改/修复**

- **头像右键菜单框架**：右键真实玩家头像弹出菜单（@ 提及 / 私聊 / 传送 / 屏蔽）；@ 已接入，其余动作预留；左键单击头像不再插入 @，双击仍为 QQ poke。
- **导航壳**：AtomChat 升级为同面板页面栈；根页为会话列表，底部 `聊天 / 个人 / 设置` tab；默认聊天键（T）直接打开公屏，新键位（默认 Y）打开上次所在页面；世界频道详情页带 SVG 返回箭头。
- **统一壳级 Header**：所有页面右上角统一显示时间；Header/标题/返回由壳统一绘制，页面类不再重复画。
- **底部 Tab 重绘**：采用 Apple 风格紧凑公式布局；三个 tab SVG 重绘为细线 + 选中填充/高亮。
- **公屏命名**：用户可见的 “World Channel / 世界频道” 统一改为 `Public / 公屏`。
- **可复用滚动系统**：新增纯 `ScrollController`，世界频道消息列表与根页共用滚动条/滚轮/拖动逻辑；为后续长列表（私聊/设置）铺路。
- **架构拆分**：抽出 `AppIcons` / `ShellHeader` / `BottomTabBar` / `ScrollController` 等壳级组件，减少 `AtomChatScreen` 膨胀。
- **Emoji 视觉居中修正**：`U+FE0F` 表情变体选择符不再参与 Skia 的文字宽度/换行测量，带 `❤️/✌️` 等字符在表情格子、消息和输入框里的横向偏移与多余空隙消除；发送内容保持原字符不变。
- **图标-only 底栏**：去掉 `Chat / Profile / Settings` 文字，底栏高度改为 `图标尺寸(s28) + 2×胶囊内留白(s4) + 2×边缘留白(s8)` 的公式布局，图标垂直居中，胶囊与底栏四边等距。
- **图标线宽按尺寸比例统一**：所有 20×20 SVG 线性图标的描边随渲染尺寸等比缩放（参考：s16 图标 = 1.5 线宽），底栏大图标不再显得比右键菜单/工具栏细。
- **SVG 图标重绘**：使用 svg-design 方法论重绘底部三个 tab 图标——圆角聊天气泡去掉过粗内线、人物、设置改为 Lucide 真齿轮（ISC 无版权）；Public 地球恢复历史 Lucide 风格椭圆经线版本；图标尺寸回落到 s24 并采用光学渐变线宽（大图标不再等比变粗）。
- **IME 组字贴合修复**：隐藏 EditBox 的 X 坐标按“Skia 已上屏前缀宽度 − 原版字体前缀宽度”补偿，中文输入法组字窗不再与已输入文字之间出现间距。
- **根页卡片 hover**：公屏卡片复用全局 45/255 白高亮 + 90ms 淡入淡出语言。
- **会话卡片时间**：公屏卡片右上角显示最新消息时间——今天 HH:mm，跨天显示 昨天/前天/M月d日，后续私聊卡片可沿用。
- **页面切换动画**：进入/返回公屏采用 200ms easeInOutCubic 双向全宽 push/pop——根页与详情页主体同时左右移动；上栏作为固定“状态栏”不随动画移动，只切换标题与返回键可见性。
- **公屏图标放大**：卡片图标容器 s36→s44、内层地球 SVG s20→s26，四周统一 s10 留白，与圆角卡片间距按公式计算。

----

**Change/Fix**

- **Avatar context-menu framework**: right-click a real player avatar opens a menu (Mention / Whisper / Teleport / Block); Mention is wired, the rest are placeholders for upcoming features. Left single-click no longer inserts @; double-click still triggers the QQ-style poke.
- **Navigation shell**: AtomChat now has an in-panel page stack. The root is a conversation list with Chat / Profile / Settings bottom tabs; the normal chat key (T) opens Public directly, and a new key (default Y) restores the last opened page. The Public detail page has an SVG back arrow.
- **Unified shell header**: time is shown on every page's top-right; header/title/back are drawn once by the shell instead of per page.
- **Redesigned bottom tab bar**: Apple-style compact formula layout; the three tab SVGs were redrawn with line style plus selected fill/highlight.
- **Public naming**: all user-visible “World Channel / 世界频道” copy is now `Public / 公屏`.
- **Reusable scroll system**: a pure `ScrollController` now powers both the world-chat message list and root pages, sharing scrollbar/wheel/drag behavior for future long lists.
- **Architecture cleanup**: extracted shell-level `AppIcons`, `ShellHeader`, `BottomTabBar`, and `ScrollController` components to keep `AtomChatScreen` from growing further.
- **Emoji visual centring fix**: `U+FE0F` emoji presentation selectors no longer contribute to Skia text/measure/line-wrap width, so `❤️/✌️` and similar glyphs no longer sit off-centre or leave phantom gaps in the emoji grid, messages, or the input box; the original sent text is unchanged.
- **Icon-only bottom tab bar**: Chat / Profile / Settings text labels are gone; the bar height is now `icon size (s28) + 2 × capsule padding (s4) + 2 × edge padding (s8)`, with the icon vertically centred and the selected capsule keeping equal breathing room from every bar edge.
- **Size-proportional icon strokes**: all 20×20 SVG line icons now scale their stroke with rendered size (reference: s16 icon = 1.5 stroke), so the larger bottom-tab icons no longer look thinner than context-menu/toolbar icons.
- **Redrawn shell icons**: bottom-tab icons (rounded chat bubble without heavy inner lines, user, Lucide proper gear under ISC) were redrawn with the svg-design methodology; the Public globe restores the earlier Lucide-style elliptical-meridian version. Icon size is back to s24 and strokes use an optical taper so larger icons no longer become proportionally heavier.
- **IME composition alignment**: the hidden EditBox X is offset by the difference between Skia and vanilla prefix widths, so the Chinese IME pre-edit window no longer floats away from the committed Skia text.
- **Root card hover**: the Public conversation card now reuses the global 45/255 white highlight with the 90ms fade language.
- **Conversation time**: the Public card shows the latest message time at the top right — HH:mm for today, Yesterday / 2 days ago / M/d across days — ready for future private-chat cards.
- **Page transition**: entering/leaving Public uses a 200ms easeInOutCubic full-width push/pop where root/detail bodies move together while the top header stays fixed like a status bar, only swapping title/back visibility.
- **Larger Public icon**: the card icon container grows from s36 to s44 and the inner globe from s20 to s26, with uniform s10 spacing calculated against the rounded card.

## v0.1.3

**更改/修复**

- **富文本聊天渲染**：玩家名/正文支持颜色、下划线、点击与悬停；可点击 `/tell`、Xaero 坐标、FTB 接受/拒绝、外部链接；裸 `http(s)` 自动识别为可点击链接并在悬停时显示 URL。
- **原版 HUD 占位**：`[[CICode,...]]` 图片代码在原版聊天栏显示为绿色 `[图片]`，不再刷一长串 URL；引用消息显示为蓝色 `[引用]`，保留发送者前缀。
- **图片消息右键保存**：右键图片气泡新增“保存”，通过 FlatLaf 另存为对话框选择位置，后台下载原始 URL 字节（GIF/WebP/PNG 原样保留）。
- **右键菜单图标**：复制 / 引用 / 保存均绘制 20×20 白色线性 SVG 图标。
- **文件选择器改进**：默认“详细信息”视图，图片文件在列表中直接显示内联缩略图，不再依赖右侧预览区。
- **气泡/UI 修复**：多行文本气泡宽度按最长行计算；引用胶囊与气泡外缘对齐；系统消息胶囊颜色与图片加载占位一致并保留半透明；普通网页链接不再被误当成图片消息。
- **指令不再本地弹气泡**：输入 `/` 指令不再制造自己的聊天气泡，与原版行为一致。
- **稳定性修复**：修复 AWT headless 导致图片选择器打不开；修复 0.1.2 中 HUD 重写未声明 cancellable 导致发送图片后被踢出单人游戏的问题。
- **消息不被误吞**：Xaero waypoint/路径分析等机器协议消息强制走系统通道；无频道身份、仅文本像“自己”的消息不再被 own-echo 误杀，遵循 e33chat“宁可不杀不可错杀”原则。

----

**Change/Fix**

- **Rich-text chat rendering**: player names/bodies support colors, underlines, click actions and hover tooltips; clickable `/tell`, Xaero coordinates, FTB accept/deny and external links work; bare `http(s)` URLs become clickable links with a hover URL tooltip.
- **Compact vanilla HUD placeholders**: `[[CICode,...]]` image codes now show as green `[Image]` instead of a long URL, and quote replies show as blue `[Quote]` while keeping the sender prefix.
- **Save images from the context menu**: right-click an image bubble → Save, pick a destination in the FlatLaf save dialog, and the original URL bytes are downloaded in the background (GIF/WebP/PNG preserved).
- **Context menu icons**: Copy / Quote / Save now use 20×20 white line-style SVG icons.
- **File chooser improvements**: defaults to Details view and shows inline thumbnails directly in the file list.
- **Bubble/UI fixes**: multi-line bubble width hugs the longest line; quote capsule aligns with the bubble edge; system capsule uses the image-loading placeholder colour while staying translucent; ordinary web links are no longer mistaken for image messages.
- **No local bubble for commands**: slash commands no longer manufacture a local chat bubble, matching vanilla behaviour.
- **Stability fixes**: fixed the AWT headless issue that prevented the image picker from opening; fixed 0.1.2 being kicked from single-player when sending an image because the HUD-rewrite mixin was not declared cancellable.
- **Messages are never wrongly swallowed**: Xaero waypoint/path-analysis machine protocols are forced to the system channel; meta-less messages that merely look like your own echo are no longer dropped, following e33chat's "rather show than kill" principle.

## Unreleased

**新增**

- **SVG 工具条图标**：图片 / 表情 / 发送按钮改为内联 SVG 路径图标，不再用中文文字标签。图标是线条风格、恒定 1.5px 描边，在每个按钮里居中并跟随主题着色，所以任何 UI 缩放下都清晰，且不需要任何图片资源。
- **界面文案本地化**：AtomChat 的所有界面文案改走 Minecraft 语言文件。`en_us.json` / `zh_cn.json` 新增的键覆盖世界频道标题、回复横幅、输入框占位与上传状态、图片加载文案、表情页签标签、右键菜单、发送者兜底名，以及 Swing 图片选择器的标题 / 过滤器 / 预览文案。
- **消息捕获加固**：结构化聊天身份改为在真正调用 `ChatHud.addMessage` 之前那一刻捕获（在 MessageHandler 内部），不再在公共频道方法的 HEAD 处取。这样在原版因无障碍聊天延迟而排队消息时，单槽交接仍然正确；被过滤 / 被屏蔽的消息也不会把自己的身份漏到下一条 HUD 行上。MessageCapture 的时间戳现在随每条按线程的记录一起传递，`SenderMeta` 里的 nil UUID 归一为 null，无档案兜底改为解析装饰后的那一行而不是原始正文。
- **多行输入框**：草稿文本折行后，输入栏按一行行高向上长，110ms 缓动，最多两行；更长的草稿在固定高度的框内纵向滚动并跟随光标。输入栏底部锚定，所以消息列表让出的正好是输入栏长高的那一份，并在长高过程中保持钉在最新消息上。
- **上下键光标导航**：草稿折到第二行后，上 / 下键在行间移动光标（上 = 目标行行尾，下 = 目标行行首）。在第一行按上键、或在最后一行按下键，回退到原版的聊天历史循环；单行草稿行为不变。
- **表情包页签**：表情 / 颜文字旁边新增第三个「表情包」页签。点一个表情会上传本地图片并把它的 CICode 放进草稿，然后关闭面板（一次点一个）。末尾的「+」格打开 FlatLaf 选择器添加图片；悬停的格子显示一个 × 用于删除。以文件副本形式存在 `<config>/atomchat/emotes/`（png/jpg/jpeg，按名称排序，上限 10 个；满了之后「+」格变灰）。表情按适应尺寸绘制、绝不放大，6 列网格、永不滚动。
- **统一的悬停反馈**：表情 / 颜文字 / 表情包格子与右键菜单的 复制/引用 行现在共用同一套按钮语言 —— 90ms 淡入淡出的半透明白高亮。表情包格子先画图片，再把悬停底色与 × 删除按钮画在上面，所以删除控件永远不会被图片盖住。
- **表情页签转场**：在 表情 / 颜文字 / 表情包 之间切换是一次不透明的全宽推入，像从一个屏走到下一个屏 —— 退出的页签被推走，进入的页签从同一方向滑入，活动指示胶囊滑到新页签上。两者都是 200ms、easeInOutCubic，走 `UiMotion.TAB_MS`。
- **算出来的高亮间距**：表情页签条现在按 `EMOJI_PANEL_PAD` 内缩，与内容网格对齐；活动胶囊保留 s(4) 的左右边距、上方 s(6)、下方 s(2) —— 下方多出的长度让标签在胶囊里居中。它不再挤到面板圆角边框上。右键菜单的行胶囊保持统一 s(4)，表情 / 颜文字格子的胶囊保持统一 s(2) 外边距。表情字形在自己的胶囊里居中；颜文字行保留 s(6) 的内部左内边距，文字永远不会碰到胶囊边缘。
- **Ctrl+V 粘贴图片**：截图与复制来的图片通过 AWT 从系统剪贴板读取（Minecraft 的剪贴板 API 只暴露字符串，看不到图片），写成临时 PNG 后上传；复制的是图片文件时直接就地读取上传。纯文本仍然走原版粘贴。
- **把图片文件拖到游戏窗口上即可上传**：聊天界面打开期间安装一个 GLFW 拖放回调，关闭时移除。Minecraft 自己一个都没注册 —— Win32 后端本来就会调用 `DragAcceptFiles`，所以事件一直在到达，只是被丢掉了。GLFW 没有 drag-enter 回调，因此没有悬停反馈；输入框占位符兼作「上传中…」的读数。整窗有效：拖放事件不带光标位置，无法用来做命中测试。
- **`minimizeWhilePicking` 配置（默认 `false`）**：原生图片选择器打开时最小化游戏窗口。GLFW 会把全屏窗口钉在 `HWND_TOPMOST` 上，在没有 AWT z 序技巧能赢的地方，这是唯一与模式无关的保证 —— 默认关闭，因为它会把游戏藏起来。

**修复**

- **聊天身份可能挂到错误的 HUD 行上**：频道级的捕获此前在 `MessageHandler.onChatMessage` / `onProfilelessMessage` / `onGameMessage` 开头设置一个待用 meta。在原版无障碍聊天延迟下，两条排队的消息会互相覆盖；而被过滤 / 被屏蔽、从未到达 HUD 的消息，其陈旧 meta 会漏到下一条行上。现在捕获改在真正调用 `ChatHud.addMessage` 之前立刻触发，位于原版延迟与跳过 / 过滤分支之后。
- **捕获的正文丢掉了字面量 `<name> ` 前缀**：`ChatMessage` 会剥掉原版发送者前缀，即使正文在装饰之前就已被捕获，于是真正以 `<Alice> hi` 开头的消息被显示成 `hi`。前缀剥离现在只在「原始 HUD 兜底」那条路上执行。
- **发送者名解析接受了词中匹配**：候选名 `Steve` 会匹配上 `Steve-Master`，或者 `<Notch>` 里的 `tch`。MessagePresentation 现在拒绝字母 / 连字符续写，以及尖括号内的后缀匹配。
- **列表跟不上长高的输入栏**：草稿折到第二行时输入栏向上长、列表随之缩短，但贴底滚动用一个每帧都重启的缓动动画去追移动中的 `maxScroll` —— 于是"变长"看起来脱节，而"变短"（一次朴素的钳制）手感正常。现在列表视口高度变化且视图处于贴底状态时，`scrollY` 与输入栏步调一致地直接锁到 `maxScroll`；新消息到达仍走平滑缓动跟随。
- **图片透过长高后的输入栏显影**：根因不是 z 序 —— 消息列表把内容一直画到单行输入栏的顶边，半透明的长高输入栏叠在它上面，所以列表里的图片透了出来。现在列表的可见区域止于当前输入栏顶边（它让出的正好是输入栏长高的那一份），半透明输入栏下面永远不会再画东西，它的透明也就保住了。
- **滚历史时消息入场动画会重播**：一个已经播完入场动画的气泡，一旦离开视口就被取消标记，于是往回滚会重播。现在入场一旦落定，界面开着期间就不再重播；已落定集合由一条 5 秒的时间闸兜底（更早的消息仅按时间判定为已落定），所以滚历史是安静的，内存也是有界的。
- **颜文字渲染成方框**：内置的 GB2312 字体子集缺大部分颜文字字符，而 Skia 的兜底只搜了一小撮系统字族。兜底列表现在加入 DengXian、Segoe UI Symbol、MS Gothic / Yu Gothic UI、Malgun Gothic、Leelawadee UI、Cambria 与 Calibri；emoji 区段的码位还会先确认 Segoe UI Emoji 真的含该字形才用它，否则落回符号字体搜索（修掉 ✧ U+2727 / ✪ U+272A 的豆腐块，它们与 emoji 同区段但不在 Segoe UI Emoji 里）。
- **表情包的删除按钮被图片盖住**：网格先画 × 再画图片，于是一张填满格子的贴图盖住了删除控件。现在先画图片，悬停底色与 × 画在上面。
- **右键菜单永远关不掉**：`closeContextMenu()` 调用的是它自己，而不是清掉 `contextMessage`，所以菜单永远留在屏幕上，随之而来的栈溢出又中断了 `mouseClicked` 的剩余部分 —— 这就是右键另一个气泡打不开新菜单的原因。现在关闭会播放既有的对称淡出 / 缩小动画（110ms）。
- **右键菜单的复制像是没反应**：`keyboard.setClipboard` 抛异常会在菜单关闭之前中断。现在它被包住并记日志，剪贴板失败不会再让菜单卡住。
- **表情面板：下面几行点不动、一点就关**：点击处理用的是它自己的 12 项数组，而面板画的是全部 24 个 `EMOJIS`，于是前两行之后的每一格都落到「关闭面板」上。现在它读 `EMOJIS` 并对格子做边界检查。表情按钮也可以再次点击把面板关掉（以前是外部点击关掉它、同一次点击里按钮又把它打开）。
- **滚动条一悬停就变蓝**：现在要按住左键才显示强调色；悬停只让滑块变粗。
- **转场迟钝**：每个逐帧补间都用 `v += (target - v) * dt / D`，这种渐近衰减的尾巴比标称时长长约 4 倍。面板开合 220ms → 150ms，消息入场 250ms → 140ms，滚轮滚动 400ms → 180ms，弹层 140ms → 110ms。滚轮滚动还把 `easeOutExpo`（半程就完成 96%，然后开始爬）换成了 `easeOutCubic`。
- **悬停高亮卡住**：同样的渐近衰减意味着指针离开半秒后按钮底色还亮着约 8%。现在转场按固定时长推进已流逝的时间并直接落到目标值，所以指针一离开，高亮一定在 90ms 内降到恰好 0。
- **过大的头栏**：头栏高度 56 → 44、标题字号 23 → 19，频道名在卡片内两个轴上都居中（时钟保持右对齐）。
- **面板背景模糊不再卡死 GPU**：旧的 Skia 侧帧缓冲快照与 `context.resetAll()` 不兼容，已删除。模糊现在是一次裸 GL 预处理，在 Skia 绘制之前跑：`glBlitFramebuffer` 把面板区域拷进一张离屏缓冲，五次 Kawase pass 平滑它，再由一个 AtomChat 自有的圆角核心着色器只把手机面板的圆角区域画回去。模糊每 2 帧刷新一次。`blurEnabled` 现在默认 `true`；着色器资源在 F3+T 下干净重载。
- **头像白边 / 锯齿边**：头像不再往皮肤贴图里烘第二张圆形 alpha 遮罩。脸保持为不透明方块，`drawRoundedImage` 的圆角裁剪是圆形的唯一出处，用 `SamplingMode.LINEAR` 绘制；灰色占位只在没有可用皮肤时绘制，所以它不会从头像边缘透出来。
- **在气泡外右键不再打开气泡菜单**：右键菜单的命中现在限制在真正的气泡矩形内，所以右键玩家名或头像不会打开消息菜单。
- **多行输入下上下键改为在同一视觉列上垂直移动光标**，不再跳到目标行的行尾 / 行首。原版聊天历史的上 / 下键只在草稿为单行时生效。
- **回复横幅改为画在输入栏之上的浮条**：它在消息列表之后绘制，所以气泡盖不住它；输入栏长高时消息列表也不再移动。
- **输入选区高亮**：隐藏的 EditBox 的选中范围现在会作为 Skia 选区块画在折行后的输入文本上（Shift+方向键 / Ctrl+A 的选区可见了）。
- **列表增长时最新消息不再向上漂**：修了两个原因。「原本在底部」的判定现在会在新消息把 `maxScroll` 撑大之前先取快照；绘制循环现在按滚动高度计算所用的同一个 `LIST_GAP` 记号推进 —— 它此前硬编码 `10.0`，而 `LIST_GAP` 是 `s(10) = 12.5`，于是每条消息都静默多加 2.5px 的幽灵底部空间，历史越积越多、最新气泡看起来离下栏越远。
- **回复横幅的 @ 前缀**：浮动的回复横幅现在显示 `@玩家`。
- **消息文字拖选**：在消息文字上拖动会在 Skia 里高亮选中范围；Ctrl+C 复制选中的消息文字。普通文字气泡与系统胶囊都适用（同一消息内多行选择）。
- **引用胶囊更大**：引用胶囊的高度 / 字号 / 内边距都调大了。
- **面板模糊底色更不透明**，默认面板宽度更窄（480 → 420），更像手机的宽高比。
- **气泡略微收紧**：气泡横向内边距 14 → 12，纵向 14 → 11，系统胶囊内边距 8 → 6，气泡最小宽度 28 → 24。
- **引用胶囊改用与头栏 / 输入栏相同的浅灰白填充**（半透明白）配白色引用文字，不再用旧的深蓝灰胶囊。
- **QQ 式消息入场**：新气泡横向滑入同时淡入 —— 自己的消息从右往左，别人的从左往右。居中的系统胶囊原地淡入。
- **气泡里显示真实发送者名**：消息现在在 MessageHandler 的频道层级捕获（signed / unsigned / system），不再只在 `ChatHud.addMessage` 处取，所以结构化的发送者 UUID / 档案能活到 `ChatMessage` 里。其他玩家不再显示为写死的「玩家」；气泡名字、头像、回复横幅与引用目标都用解析出的发送者。一个短 TTL 的交接防止陈旧元数据给后面的行贴错标签，系统频道的 NCR / 插件玩家行在当作系统文本之前，会先落回一个「结构化的名字 + 分隔符」解析器。
- **新消息入场从第一次可见时开始**：成串到达的消息不再在自动滚动追赶期间把动画大部分耗在屏幕外；每个气泡从进入视口的第一帧起播完整的淡入 / 滑入。
- **全屏下图片选择器改用原生 AWT 文件对话框**：Swing 选择器一直藏在 Minecraft 的独占全屏窗口后面。对话框持有输入期间，MC 按住的键 / 按钮会被释放。*已被取代 —— 见「修复」下的选择器条目。*
- **表情面板更紧凑、可滚动**：24 个过大的格子换成 e33chat 更大的表情集合外加一个颜文字页签，两者都是可滚动网格与更小的格子。
- **表情面板不再插入错误的表情**：点击处理从不钳制列号，于是在左侧空隙点击算出 `col = -1`、插入了上一行最后一格，右侧空隙插入下一行第一格，网格下方点击会命中一个已滚出视野的项。现在内容矩形之外的点击直接拒绝，列号也会被钳制。
- **指令补全弹窗不再压住输入栏**：弹窗锚在整个输入栏之上，而不是光标所在文本行之上，所以它盖不住图片 / 表情 / 发送按钮。
- **消息入场动画不再循环**：动画一播完就删掉自己的起始时间戳，但消息仍留在视口里，于是 `entranceEase()` 把缺失的记录读成「第一次可见的那一帧」并重新开始 —— 永远循环，而且加入循环的消息越多，看起来越快。重新打开界面之所以能止住，只是因为 `openStart` 越过了那些消息。现在播完的消息被标记为已落定并保留时间戳；这个状态只在消息真正离开视口时丢弃（滚回来时也会重新触发入场）。
- **原生图片选择器在全屏下能开到游戏之上了**：AWT 文件对话框此前以 `null` 为 owner 创建，于是 Windows 把它放在 z 序最底，Minecraft 的无边框全屏窗口画在了它上面。现在它归一个复用的、不可见的 1x1 置顶框所有，从而把这个模态对话框抬进同一个置顶 z 带。关闭后焦点交还游戏窗口（并释放按住的键 / 按钮）。
- **图片气泡不再被拉伸**：气泡框此前是固定 275x175，高度是被钳制而不是按比例缩放，所以任何比 1.57:1 更方的图都被压进这个框里。上传的码现在携带图片固有的 `w=`/`h=`，接收方就能在下载落地之前按正确宽高比排好气泡（到达时不会跳高度）。气泡贴合缩放后的图片，背景不再画信箱边框，而没有尺寸的码 —— 更老的消息，或 ImageIO 量不出尺寸的格式 —— 回退到占位框。图片绝不被放大。
- **图片气泡的名字位置错了**：名字此前锚在消息行上而不是气泡边缘，而图片气泡总比短文字气泡宽，于是名字飘得离它很远。现在它用与文字气泡相同的贴边锚定规则。
- **图片选择器改用 FlatLaf 皮肤的 Swing 选择器**：六次尝试把原生 `GetOpenFileName` 对话框抬到 Minecraft 之上全部失败，包括从一个看门狗线程直接往对话框窗口写 `WS_EX_TOPMOST`。Windows 的 z 序分两个带 —— 所有置顶窗口都在所有非置顶窗口之上 —— 而 owner 只在同带内部排序，所以无论 owner 是谁，原生对话框都在置顶全屏游戏之下。现在选择器是一个放在普通 `JFrame` 里的 `JFileChooser`，可以被置顶并确实浮在游戏之上，并且用 FlatLaf 上皮肤（内嵌，Apache 2.0），因为 Swing 默认的 Metal 外观正是它当初难看的原因。它从「图片」文件夹打开。
- **选择图片时切全屏不再最小化游戏**：GLFW 会在全屏窗口一失去焦点就把它图标化（`GLFW_AUTO_ICONIFY` 默认为 true，而 Minecraft 保持了这个默认值）。现在选择器打开期间恰好挂起这个标志。`minimizeWhilePicking` 配置已删除 —— 它绕开的是 z 序 bug，而不是原因。
- `UiMotion`：转场时长的唯一出处，外加保证转场精确落到目标值的 `approach()` 辅助函数。

**更改**

- **更大的名字与头像**：头像 34 → 40，名字字号 14 → 16，名字带 22 → 26（这条带同时充当名字与气泡之间的间距），头像 / 气泡间距 6 → 8。
- **更紧的气泡**：最小宽度 36 → 28，纵向内边距 18 → 14（系统胶囊 10 → 8）。气泡内边距现在是一个由绘制与 `messageHeight()` 共用的记号，所以布局、裁剪与滚动不会再各走各的。
- **去掉头像的金属圈。**
- **纯白标签**：图片按钮、表情按钮与头栏时钟。
- **玩家名为纯白**：气泡与图片消息里都是。
- **配置文件移到 AtomChat 数据目录下**：JSON 设置现在住在 `<config>/atomchat/atomchat-client.json`（与 `emotes/` 同级）；调试用的头像 PNG 移到 `<config>/atomchat/debug/`。不做迁移 —— 还没有已发布的用户，所以旧的 `atomchat.json` 直接忽略。
- **表情面板更大**：格子 26 → 34，可见行 4 → 5，页签条 30 → 34，面板内边距 10 → 12。颜文字行获得自己的记号（`EMOJI_KAOMOJI_ROW_H`、`FONT_KAOMOJI`）而不是内联数值。
- **表情字形不再填满格子**：字号退回到 22，这样 34 宽的格子在相邻之间还留得下可见的间隙 —— 28 的时候字形挤在一起，很难看清自己瞄的是哪一格。
- **消息入场变慢，以淡入为主而不是飞入**：140ms → 220ms，滑动距离从 32 降到 14，因为 40px 的位移主导了整个动画，眼睛根本读不到淡入。现在淡入与滑动在同一条时间轴上用各自的曲线（不透明度用 `easeOutQuad`，位移用 `easeOutCubic`），不再共用一个 `easeOutCubic` —— 后者把约 88% 的不透明度变化都花在时长的前一半。
- **输入框占位符始终可见**：它不再要求输入框处于未聚焦状态 —— 而 ChatScreen 一打开就聚焦该框，导致它从未显示过。现在草稿为空时它就出现，用次级灰。

----

**Added**

- **SVG toolbar icons**: the image / emoji / send buttons now draw inline SVG
  path icons instead of Chinese text labels. The icons are line-style at a
  constant 1.5px stroke, centered in each button and recoloured with the theme,
  so they stay crisp at every UI scale with no image assets.
- **Localized UI copy**: all AtomChat surface text now goes through Minecraft's
  language files. New keys in `en_us.json` / `zh_cn.json` cover the world
  channel title, reply banner, input placeholder/upload status, image loading
  text, emoji tab labels, context menu, sender fallbacks, and the Swing image
  picker title/filter/preview strings.
- **Message capture hardening**: structured chat identity is captured right
  before the real `ChatHud.addMessage` call (inside MessageHandler) instead of
  at the public channel method's HEAD. This keeps the single-slot handoff
  correct when vanilla queues messages via accessibility chat delay, and
  prevents filtered/blocked messages from leaking their identity onto the next
  HUD line. MessageCapture timestamps now travel with each per-thread entry,
  nil UUIDs are normalized to null in `SenderMeta`, and the profileless fallback
  parses the decorated line rather than the raw body.
- **Multi-line input box**: the bar grows upward by one line height once the
  draft text wraps, eased over 110ms, and caps at two lines. Longer drafts
  scroll vertically inside the fixed box, following the caret. The bar is
  bottom-anchored, so the message list gives back exactly the height the bar
  takes and stays pinned to the newest message while it grows.
- **Up/Down caret navigation**: once the draft wraps onto a second line,
  Up/Down move the caret between lines (Up = end of target line, Down = start
  of target line). Pressing Up on the first line or Down on the last line
  falls back to vanilla chat-history cycling; a single-line draft is
  unchanged.
- **Emote pack tab**: a third "表情包" tab beside 表情 / 颜文字. Tapping an
  emote uploads the local image and drops its CICode into the draft, then
  closes the panel (one sticker per tap). The trailing "+" cell opens the
  FlatLaf picker to add images; hovered cells show a × to delete. Persisted as
  copied files in `<config>/atomchat/emotes/` (png/jpg/jpeg, name-sorted, cap
  of 10; the add cell greys out when full). Emotes render fitted, never
  upscaled, in a 6-column grid that never scrolls.
- **Unified hover feedback**: emoji / kaomoji / emote cells and the context
  menu's 复制/引用 rows now share the button language — a translucent white
  highlight that fades in and out over 90ms. Emote cells draw the image first
  and the hover wash + × remove button on top, so the delete control can never
  be buried under a picture.
- **Emoji tab transition**: switching between 表情 / 颜文字 / 表情包 is an
  opaque full-width push, like moving from one screen to the next — the
  outgoing tab is pushed out as the incoming tab slides in from the same
  direction, and the active pill glides to the new tab. Both run at 200ms with
  easeInOutCubic, via `UiMotion.TAB_MS`.
- **Calculated highlight spacing**: the emoji tab strip is now inset by
  `EMOJI_PANEL_PAD` so it aligns with the content grid; the active pill keeps
  s(4) side margins, s(6) above and s(2) below — the extra bottom length
  centres the label inside the pill. It no longer crowds the panel's rounded
  border. The context menu row capsule keeps a uniform s(4), and the
  emoji/kaomoji cell capsules keep a uniform s(2) outer margin. Emoji glyphs
  are centred in their capsule; kaomoji rows keep s(6) of internal left padding
  so text never touches the capsule edge.

- **Ctrl+V pastes images**: screenshots and copied pictures are read off the
  system clipboard through AWT (Minecraft's clipboard API only exposes strings,
  so it cannot see them), written to a temp PNG and uploaded; copied image files
  are uploaded in place. Plain text still falls through to the vanilla paste.
- **Dragging an image file onto the game window uploads it**: a GLFW drop
  callback is installed while the chat screen is open and removed on close.
  Minecraft registers none — the Win32 backend already calls `DragAcceptFiles`,
  so the events were arriving and being discarded. GLFW has no drag-enter
  callback, so there is no hover feedback; the input placeholder doubles as the
  "uploading…" readout instead. Window-wide: the drop event carries no cursor
  position to hit-test with.
- `minimizeWhilePicking` config (default `false`): minimizes the game window
  while the native image picker is open. GLFW pins a fullscreen window to
  `HWND_TOPMOST`, and where no AWT z-order trick wins, this is the only
  mode-independent guarantee — off by default because it hides the game.

**Fixed**

- **Chat identity could be attached to the wrong HUD line**: the channel-level
  capture previously set a single pending meta at the start of
  `MessageHandler.onChatMessage` / `onProfilelessMessage` / `onGameMessage`.
  With vanilla accessibility chat delay, two queued messages overwrote each
  other; with a filtered/blocked message that never reached the HUD, the stale
  meta could leak onto the next line. Capture now fires immediately before the
  real `ChatHud.addMessage` call, after vanilla's delay and skip/filter paths.
- **Captured body text lost a literal `<name> ` prefix**: `ChatMessage` stripped
  the vanilla sender prefix even when the body had already been captured before
  decoration, so a message that really started with `<Alice> hi` was shown as
  `hi`. The prefix stripper now only runs on the raw-HUD fallback.
- **Sender-name parsing accepted mid-word matches**: a candidate `Steve` could
  match `Steve-Master` or the `tch` inside `<Notch>`. MessagePresentation now
  rejects letter/hyphen continuations and suffix matches inside angle brackets.
- **List lagged behind the growing input bar**: when the draft wrapped to a
  second line the input bar grew upward and the list shrank with it, but the
  bottom-pinned scroll chased the moving `maxScroll` with an eased animation
  that restarted every frame — so growing looked desynced while shrinking (a
  plain clamp) felt fine. When the list viewport height changes and the view is
  pinned to the bottom, `scrollY` is now locked straight to `maxScroll` in
  lockstep with the bar; new-message arrivals still use the smooth eased
  follow.
- **Images ghosted through the grown input bar**: the root cause was not a
  z-order issue — the message list painted content down to the one-line bar
  top, and the translucent grown bar sat on top of it, so list images showed
  through. The list's visible area now ends at the current input bar top (it
  yields exactly the height the bar gains), so nothing is ever painted
  underneath the translucent composer and its transparency is preserved.
- **Message entrance replayed when scrolling through history**: a bubble that
  had finished its entrance animation was unmarked as soon as it left the
  viewport, so scrolling back up replayed it. Once an entrance settles it now
  never replays while the screen is open; the settled set is bounded by a 5s
  time guard (older messages are settled by time alone), so scrolling through
  history is silent and memory stays bounded.
- **Kaomoji rendered as boxes**: the bundled GB2312 font subset lacks most
  kaomoji characters, and the Skia fallback only searched a narrow set of
  system families. The fallback list now includes DengXian, Segoe UI Symbol,
  MS Gothic / Yu Gothic UI, Malgun Gothic, Leelawadee UI, Cambria and Calibri;
  emoji-range codepoints also verify that Segoe UI Emoji actually contains the
  glyph before using it, otherwise they fall through to the symbol-font search
  (fixes tofu on ✧ U+2727 / ✪ U+272A, which share the emoji block but are not
  in Segoe UI Emoji).
- **Emote remove button was hidden under the picture**: the grid painted the ×
  before the image, so a sticker filling its cell covered the delete control.
  The image now draws first and the hover wash + × render on top.
- **Context menu could never be dismissed**: `closeContextMenu()` called itself
  instead of clearing `contextMessage`, so the menu stayed on screen forever and
  the resulting stack overflow aborted the rest of `mouseClicked` — which is why
  right-clicking another bubble could not open a new menu. Closing now plays the
  existing symmetric fade/scale-out (110ms).
- **Context menu copy appeared dead**: an exception from
  `keyboard.setClipboard` would abort before the menu closed. It is now wrapped
  and logged so a clipboard failure can't strand the menu.
- **Emoji panel: lower rows were dead and the panel closed on click**: the click
  handler used its own 12-entry array while the panel draws all 24 `EMOJIS`, so
  every cell past the first two rows fell through to "close the panel". Now it
  reads `EMOJIS` and bounds-checks the cell. The emoji button can also toggle
  the panel off again (an outside-click used to close it and the button reopened
  it in the same click).
- **Scrollbar turned blue on hover**: accent colour now requires a held left
  button; hovering only thickens the thumb.

- **Sluggish transitions**: every per-frame tween used `v += (target - v) * dt / D`,
  an asymptotic decay whose tail ran ~4x longer than the stated duration. Panel
  open/close 220ms -> 150ms, message entry 250ms -> 140ms, wheel scroll
  400ms -> 180ms, popups 140ms -> 110ms. Wheel scroll also swaps `easeOutExpo`
  (96% done at half time, then crawls) for `easeOutCubic`.
- **Stuck hover highlight**: same asymptotic decay meant the button tint was
  still ~8% lit half a second after the pointer left. Transitions now advance
  by elapsed time over a fixed duration and snap to the target, so a highlight
  always reaches exactly 0 within 90ms of the pointer leaving.
- **Oversized header**: header height 56 -> 44 and title font 23 -> 19, with the
  channel name centered on both axes inside the card (the clock stays
  right-aligned).

- **Panel background blur no longer hangs the GPU**: the old Skia-side
  framebuffer snapshot was incompatible with `context.resetAll()` and has been
  removed. The blur is now a raw-GL pre-pass that runs before Skia paints:
  `glBlitFramebuffer` copies the panel region into an offscreen buffer, five
  Kawase passes smooth it, and an AtomChat-owned rounded core shader draws only
  the phone panel's rounded area back. The blur is refreshed every 2 frames.
  `blurEnabled` now defaults to `true`; shader resources reload cleanly with
  F3+T.
- **White/jagged avatar rim**: the avatar no longer bakes a second circular
  alpha mask into the skin image. The face is kept as an opaque square and
  `drawRoundedImage`'s rounded clip is the single source of the circle, drawn
  with `SamplingMode.LINEAR`; the gray placeholder is only painted when no
  skin is available, so it cannot bleed through the avatar's edge.
- **Right-click outside the bubble no longer opens the bubble menu**: context
  menu hits are now limited to the actual bubble rectangle, so right-clicking
  a player name or avatar does not open the message menu.
- **Up/Down on multi-line input now move the caret straight up/down at the
  same visual column** instead of jumping to the target line's end/start.
  Vanilla chat-history Up/Down only applies while the draft is single-line.
- **Reply banner is drawn above the input as an overlay strip**: it is painted
  after the message list so bubbles cannot cover it, and the message list no
  longer moves when the composer grows.
- **Input selection highlight**: the hidden EditBox's selected range is now
  drawn as a Skia selection block over wrapped input lines (Shift+arrows /
  Ctrl+A selections are visible).
- **Latest message no longer drifts upward as the list grows**: two causes
  were fixed. The “was at bottom” check now snapshots before new messages grow
  `maxScroll`, and the draw loop now advances by the same `LIST_GAP` token used
  by the scroll-height calculation — it previously hard-coded `10.0` while
  `LIST_GAP` is `s(10) = 12.5`, so every message silently added 2.5px of
  phantom bottom space and the newest bubble looked farther from the lower bar
  as history accumulated.
- **Reply banner @ prefix**: the floating reply banner now shows `@玩家`.
- **Message text drag selection**: drag across message text highlights the
  selected range in Skia; Ctrl+C copies the selected message text. Works for
  normal text bubbles and system capsules (same-message multi-line selection).
- **Quote pill is larger**: quote capsule height/font/padding increased.
- **Panel blur tint is more opaque** and the default panel width is narrower
  (480 → 420) for a more phone-like aspect.
- **Slightly more compact bubbles**: horizontal bubble padding 14 → 12,
  vertical bubble padding 14 → 11, system capsule padding 8 → 6, and the
  minimum bubble width 28 → 24.
- **Quote pills now use the same light gray-white fill as the header/input
  bars** (translucent white) with white quote text, instead of the old dark
  blue-gray capsule.
- **QQ-style message entrance**: new bubbles slide in horizontally while
  fading — own messages from the right toward the left, others from the left
  toward the right. Centered system capsules fade in place.
- **Real sender names in bubbles**: messages are now captured at the
  MessageHandler channel level (signed / unsigned / system) instead of only at
  `ChatHud.addMessage`, so the structured sender UUID/profile survives into
  `ChatMessage`. Other players no longer show as the hard-coded “玩家”; the
  bubble name, avatar, reply banner and quote target use the resolved sender.
  A short TTL handoff prevents stale metadata from mislabelling later lines,
  and system-channel NCR/plugin player lines fall back to a structural
  name+separator parser before being treated as system text.
- **New-message entrance starts when first visible**: messages arriving in a
  burst no longer spend most of their animation off-screen while auto-scroll
  catches up; each bubble plays its full fade/slide from the first frame it
  enters the viewport.
- **Fullscreen image picker uses the native AWT file dialog**: the Swing
  chooser stayed hidden behind Minecraft's exclusive-fullscreen window. MC's
  held keys/buttons are released while the dialog owns input. *Superseded — see
  the picker entry under Fixed.*
- **Emoji panel is more compact and scrollable**: the 24 oversized cells are
  replaced by e33chat's larger emoji set plus a kaomoji tab, both with
  scrollable grids and smaller cells.
- **Emoji panel no longer inserts the wrong emoji**: the click handler never
  clamped the column, so a click in the left gutter computed `col = -1` and
  inserted the previous row's last entry, the right gutter inserted the next
  row's first, and a click below the grid hit an item that was scrolled out of
  view. Clicks outside the content rectangle are now rejected outright and the
  column is clamped.
- **Command suggestion popup no longer overlaps the input bar**: the popup
  anchors above the whole input bar instead of above the caret text line, so
  it cannot cover the image/emoji/send buttons.
- **Message entrance animation no longer loops**: the animation deleted its
  start timestamp as soon as it finished, but the message stayed in the
  viewport, so `entranceEase()` read the missing entry as "first visible
  frame" and restarted it — forever, and visibly faster as more messages
  joined the loop. Reopening the screen only stopped it because `openStart`
  moved past those messages. A finished message is now marked as settled and
  keeps its timestamp; the state is discarded only when the message actually
  leaves the viewport (which also re-arms the entrance if it scrolls back in).
- **Native image picker now opens above the game in fullscreen**: the AWT
  file dialog was created with a `null` owner, so Windows placed it at the
  bottom of the z-order and Minecraft's borderless fullscreen window painted
  over it. It is now owned by a reused, invisible 1x1 always-on-top frame,
  which lifts the modal dialog into the same topmost z-band. Focus is handed
  back to the game window (and held keys/buttons released) once it closes.

- **Image bubbles no longer stretch**: the box used to be a fixed 275x175 with
  the height clamped rather than scaled, so anything more square than 1.57:1
  was squashed into it. Uploaded codes now carry the intrinsic `w=`/`h=` so the
  receiver can lay the bubble out at the right aspect ratio before the download
  lands (no height jump on arrival). The bubble hugs the scaled image, the
  background no longer draws a letterbox frame, and codes without a size —
  older messages, or formats ImageIO cannot size — fall back to the placeholder
  box. Images are never upscaled.
- **Image bubble names sat in the wrong place**: the name was anchored to the
  message row instead of the bubble edge, and because an image bubble is always
  wider than a short text bubble the name ended up floating away from it. It
  now uses the same edge-anchoring rule as text bubbles.
- **The image picker is a FlatLaf-skinned Swing chooser**: six attempts to lift
  the native `GetOpenFileName` dialog above Minecraft all failed, including
  writing `WS_EX_TOPMOST` straight onto the dialog window from a watchdog
  thread. Windows orders the z-order in two bands — every topmost window above
  every non-topmost one — and ownership only ranks windows inside a band, so a
  native dialog is below a topmost fullscreen game no matter whose owner it is.
  The picker is now a `JFileChooser` in a plain `JFrame`, which can be made
  topmost and does float above the game, and it is skinned with FlatLaf
  (bundled, Apache 2.0) because Swing's default Metal look was the reason it
  was ugly in the first place. It opens in the Pictures folder.
- **Going fullscreen no longer minimises the game while picking**: GLFW
  iconifies a fullscreen window as soon as it loses focus
  (`GLFW_AUTO_ICONIFY` defaults to true and Minecraft leaves it there).
  The flag is suspended for exactly as long as the picker is open. The
  `minimizeWhilePicking` config is gone — it worked around the z-order bug
  rather than the cause.

- `UiMotion`: single source of truth for transition durations plus the
  `approach()` helper that guarantees a transition lands exactly on its target.

**Changed**

- **Bigger names and avatars**: avatar 34 -> 40, name font 14 -> 16, name band
  22 -> 26 (the band doubles as the name/bubble gap), avatar/bubble gap 6 -> 8.
- **Tighter bubbles**: minimum width 36 -> 28, vertical padding 18 -> 14
  (system capsule 10 -> 8). Bubble padding is now a token shared by drawing and
  `messageHeight()`, so layout, clipping and scrolling cannot drift apart.
- **Removed the avatar bezel ring.**
- **Pure white labels** for the image button, emoji button and header clock.
- **Player names are pure white** in bubbles and image messages.
- **Config file moved under the AtomChat data folder**: JSON settings now live
  at `<config>/atomchat/atomchat-client.json` (next to `emotes/`); debug avatar
  PNGs move to `<config>/atomchat/debug/`. No migration is performed — there
  are no released users yet, so an old `atomchat.json` is simply ignored.

- **Emoji panel is larger**: cell 26 → 34, visible rows 4 → 5, tab bar 30 →
  34, panel padding 10 → 12. Kaomoji rows get their own tokens
  (`EMOJI_KAOMOJI_ROW_H`, `FONT_KAOMOJI`) instead of inline values.
- **Emoji glyphs no longer fill their cell**: the font dropped back to 22 so a
  34-wide cell keeps a visible gutter between neighbours — at 28 the glyphs
  crowded together and made it hard to tell which cell you were aiming at.
- **Message entrance is slower and fades instead of flying**: 140ms → 220ms and
  the slide distance drops from 32 to 14, because a 40px travel dominated the
  animation and the eye never read the fade at all. The fade and the slide now
  use separate curves on one timeline (`easeOutQuad` for opacity,
  `easeOutCubic` for the travel) instead of sharing `easeOutCubic`, which spent
  ~88% of the opacity ramp in the first half of the duration.
- **Input placeholder is always visible**: it no longer requires the field to be
  unfocused, which — ChatScreen focusing the field on open — meant it never
  showed. It now appears whenever the draft is empty, in secondary grey.

## v0.1.0 (MVP)

**新增**

- 用 Skija 渲染搭起 Fabric 1.21.1 工程骨架。
- 用手机风格的 AtomChat 界面替换原版聊天界面。
- AtomChat 打开时隐藏原版聊天 HUD。
- 用 Skia 绘制圆角气泡、头像、名字与时间戳。
- 加入 @/戳一戳、复制/引用右键菜单与表情面板。
- 加入 JSON 配置（`config/atomchat.json`）。
- 为动画器与缓动函数补单测。

----

**Added**

- Scaffold Fabric 1.21.1 project with Skija rendering.
- Replace vanilla chat screen with a phone-style AtomChat screen.
- Hide vanilla chat HUD while AtomChat is open.
- Render rounded bubbles, avatars, names, timestamps via Skia.
- Add @/poke, copy/quote context menu, emoji panel.
- Add JSON config (`config/atomchat.json`).
- Add unit tests for animator/easing.

