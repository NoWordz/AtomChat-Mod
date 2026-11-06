# 首开长帧修复（AC 侧）：把「打开面板卡一下」的活挪出那一帧

**日期**：2026-11-06 ｜ **范围**：AtomChat `D:/AtomChat-Main` ｜ **状态**：代码完成、三端构建与测试全绿；**未部署、未启动游戏、未真机验收、未 commit（本文除外）**

**对应问题**：用户体感「打开面板会掉帧」，而稳态帧率只差 0.386ms（400→346 FPS，人眼基本看不出）。真正扎眼的是**首开那一帧**。本次日志的直接证据：

```
20:30:30  [AtomChat][resource] Loaded bundled font /com/atom/lib/fonts/bundled-default.otf
20:30:32  frame_pump ... max=160.28      ← 首开窗口，160ms ≈ 卡 8~10 帧
20:30:36  frame_probe ... max=458.25     ← 另一个尖峰，本次仍未归因
```

历史同场景的归因（`2026-11-06-truth-test-log-analysis.md`）：字体首次同步加载 **125.21ms**、emoji 首次栅格化 **153.15ms**、字多的设置页 **79.80ms**。

**本轮的核心判断**：这三个数不是「画得慢」，而是**本该提前做的活被放在了最敏感的那一刻**。别的 mod 的 UI 不掉帧，是因为它们的字体/图集在启动阶段就烘好了。

---

## 1. 结论先行

| 环节 | 首开代价（历史实测） | 本轮处置 | 落在哪 |
| --- | --- | --- | --- |
| bundled font 加载 + 解码 | 125.21ms | **挪走**：后台线程预热 | `FontManager` |
| bold typeface 查找 | 含在上面 | **挪走**：同上 | `FontManager` |
| emoji 字体回退解析 + 字形栅格化 | 153.15ms | **挪走**：后台线程预热，主线程只取成品 | `SkiaFontRenderer` |
| 模糊 shader 编译/链接 | 未单独测过 | **提前**：加入世界后预热 | `PanelBlurRenderer` |
| 模糊 FBO + 三张纹理分配 | 未单独测过 | **提前**：同上 | `PanelBlurRenderer` |
| Skija surface / BackendRenderTarget 首次创建 | 未单独测过 | **提前**：同上 | `MinecraftRenderBridge` |
| 物品图标 `prepare` | 未单独测过 | **未动** | `ItemIconRenderer` |
| 设置页作者图/徽标解码 | 79.80ms（那一帧还有别的东西） | **未动** | `SettingsSectionPage` |

「未单独测过」= 以前**没有分段预算**，无法单独归因；本轮补上了预算工具（§4），下次真机就能逐项读出来。

---

## 2. 首开关键路径全表（下次真机按这张表读）

| # | 环节 | 位置 | 能否离开渲染线程 | 本轮处置 | 首开时是否仍付 |
| --- | --- | --- | --- | --- | --- |
| 1 | bundled OTF 读盘 + `Typeface` 解码 | `shared/.../font/FontManager.java` | **能**（纯 IO + Skia CPU，无 GL） | 后台预热 | 否（预热命中时） |
| 2 | bold face 查找 | 同上 | **能** | 同上 | 否 |
| 3 | emoji 回退 face 解析 | `shared/.../render/SkiaFontRenderer.java` | **能** | 后台预热 | 否 |
| 4 | emoji 字形栅格化成位图 | `shared/.../render/EmojiGlyphCache.java` | **能**（CPU 光栅） | 后台预热 | 否 |
| 5 | 模糊 shader program 链接 | `platforms/*/.../render/PanelBlurRenderer.java` | **不能**（要 GL context） | 渲染线程**提前** | 否 |
| 6 | 模糊 FBO + 纹理分配 | 同上 | **不能** | 渲染线程**提前** | 否 |
| 7 | Skija surface / render target 首次创建 | `MinecraftRenderBridge.prepare()` | **不能** | 渲染线程**提前** | 否 |
| 8 | 物品图标解码 | `platforms/*/.../render/ItemIconRenderer.java` | 解码能，GL 上传不能 | **未动** | 是（只在有物品消息时） |
| 9 | 设置页图片解码 | `SettingsSectionPage.loadBundled` | 能 | **未动** | 是（进设置页时） |
| 10 | 聊天记录文本的 shape/wrap | `SkiaFontRenderer` 的三层缓存 | 不能（已缓存则能） | 已有缓存，未动 | 首次每个新文本 |

**「提前」不是「挪到别的线程」**——5/6/7 仍然只能在渲染线程做，只是从「用户按 Y 的那一帧」挪到「加入世界后」。

---

## 3. 线程边界论证（为什么这么挪是安全的）

### 3.1 字体（`FontManager`）

- **后台线程做**：读 `/com/atom/lib/fonts/bundled-default.otf`、Skija `Typeface` 解码、bold face 查找。全部是文件 IO + Skia CPU，**不碰 GL，不碰 `DirectContext`**。
- **交接**：`defaultTypeface` / `boldTypeface` 是 `volatile`，双检锁（`LOAD_LOCK`）保证两个线程不会各解一份。渲染线程读到非空就直接用。
- **冷路径仍在**：`getDefaultTypeface()` 里保留了原来的同步加载分支，并注释说明「预热没赶上时行为与改动前完全一致」。预热是加速，不是替换。
- **风险点**：Skija 的 `FontMgr` 在多线程首次初始化时的安全性未由本仓证明。缓解方式是「后台线程先跑，渲染线程冷路径仍然兜底」，最坏情况退化为今天的行为。

### 3.2 emoji（`SkiaFontRenderer` / `EmojiGlyphCache`）

- **后台线程做**：按 `(size, codepoint)` 解析 face、栅格化成不可变位图。
- **交接**：预热产物放在带锁的 `PREWARMED_FONTS`（上限 256 项，防止病态积压钉住原生字体），渲染线程**取一次**并装进正常缓存，**所有权只转移一次**。
- **风险点**：`SkiaFontRenderer` 的常规缓存是普通 `HashMap`，后台线程只写预热表、不碰常规缓存；渲染线程取走预热表条目后写常规缓存。两侧的交界只有 `PREWARMED_FONTS`（带锁）与 `volatile` 标志位。

### 3.3 GL 资源（`PanelBlurRenderer.prewarm` / `MinecraftRenderBridge.prepare`）

- **仍在渲染线程**：由平台在 client tick 里调用（`FirstOpenWarmup.consumeGlWarmup()` 保证整个进程只做一次）。
- **只创建不画**：`prewarm(w, h)` 只做 `ensureLoaded()`（编译两个 shader）+ `ensureTextures(w, h, false)`（按面板尺寸分配三张 ping-pong 纹理），**不画任何东西**。纹理参数与尺寸取整（2 的幂容量）与正式路径完全一致，所以首开时命中容量复用，不会重建。
- **前提**：必须在渲染线程、持有当前 GL context 时调用 —— 已满足。

---

## 4. `first_open_budget`：下一次真机怎么读

新增探针（仅 debug=on 输出；debug=off 时只有一次 volatile 读）：

```
[AtomChat][probe] first_open_budget total=… font=… emoji=… blur_init=… icons=… other=…
    prewarm_font=… prewarm_emoji=… prewarm_gl=…
```

| 字段 | 含义 | 期望值（预热成功时） |
| --- | --- | --- |
| `total` | 从第一次构造面板到首帧画完的整段墙钟 | 明显下降（**待真机验证**） |
| `font` | 首开帧内同步加载字体的墙钟 | ≈ 0 |
| `emoji` | 首开帧内解析/栅格化 emoji 的墙钟 | ≈ 0 |
| `blur_init` | 首开帧内创建 shader/FBO/纹理的墙钟 | ≈ 0 |
| `icons` | 首开帧内物品图标 prepare 的墙钟 | **恒为 0 —— 本轮没接埋点**，见 §6 |
| `other` | `total − (font+emoji+blur_init+icons)`，钳制 ≥0 | 剩余未归因部分 |
| `prewarm_font` / `prewarm_emoji` / `prewarm_gl` | 三个预热各自的墙钟；`-1.000` = 首开时还没完成 | 都应为正数 |

**读法**：
- `font`/`emoji`/`blur_init` 都 ≈ 0 且 `prewarm_*` 都为正 ⇒ 三项都挪走了
- 某个 `prewarm_*` 是 `-1.000` ⇒ 预热开得太晚（比首开还晚），要把它再往前挪
- `other` 仍然很大 ⇒ 还有没归因的活（设置页图片、聊天文本 shape、物品图标），逐个补埋点

**注意**：`total` 是「按下键 → 第一帧画完」的**整段关键路径**，不是单帧耗时，因此它比 `frame_probe` 的相邻间隔大，**两者不可相减**。

---

## 5. 像素等价性证据

`shared/src/test/java/com/atom/chat/render/EmojiPrewarmPixelTest.java`：

1. 先走预热路径渲染 emoji 位图；
2. **断言预热确实被命中**（`emojiPrewarmHits()` 增加）——避免「冷路径等于自己」的假等价；
3. 清空缓存后走原来的现场解析 + 现场栅格路径再渲染一次；
4. 两次位图**逐字节比较**（`assertArrayEquals`）。

⇒ 预热产物与冷路径产物像素完全一致。

字体侧没有单独的像素 diff：字体走的是同一份 `Typeface`，后台只是把「解码」提前，渲染参数未变（`FontManager` 的解码结果被 `volatile` 发布，渲染端用法不变）。

---

## 6. 本轮我接手时修的三处（继承自被中断的代理）

| 问题 | 现象 | 根因 | 修法 |
| --- | --- | --- | --- |
| `AtomLibApiPreflightTest` 红 | 「1.21.1-fabric must check before reading client configuration」 | 新增的 `warmupGl()` 方法**定义在文件前部**，里面出现 `AtomChatConfig.get()`，源码级 grep 把「定义」误当成「运行时读取」 | 把标志位改成在**调用点**读取（调用点在依赖检查之后），参数传入 |
| `PrepassProbeTest` 红 | 三端渲染器 `System.nanoTime()` 计数 15 ≠ 13 | 首开预算在渲染器里加了 2 个计时点，而该测试钉死 13（探针结构不许变） | 把时钟搬进 `FirstOpenBudget.timeBlurInit(Runnable)`，渲染器**一个不多**，仍是 13 |
| NeoForge/Forge 编译失败 | `Window.getScaleFactor()` 找不到符号 | 那两个平台用**官方映射**，窗口缩放叫 `getGuiScale()`（yarn 才叫 `getScaleFactor()`） | Forge/NeoForge 改用 `getGuiScale()`，Fabric 保持 `getScaleFactor()` |

另修：快照脚本 `prepare_snapshot.py` 里代理自己写的断言（`nanoTime == 15`、`addBlurInit(`）同步更新为新的等价格式；快照目录改为**新建 `snapshot-b` 不做递归删除**（批量删除要人工确认，快照是可再生产物）。

---

## 7. 构建与测试

| 平台 | 构建 | 测试 |
| --- | --- | --- |
| Fabric 1.21.1 | BUILD SUCCESSFUL (4m04s) | **1743** / 0 失败 / 7 跳过 |
| Forge 1.20.1 | BUILD SUCCESSFUL (3m31s) | **1728** / 0 失败 / 7 跳过 |
| NeoForge 1.21.1 | BUILD SUCCESSFUL (2m36s) | **1728** / 0 失败 / 7 跳过 |

（上一轮基线 1724/1709/1709，本轮各 +19 个新测试。）

**红线复查**（全过）：

| 项 | 期望 | 实际 |
| --- | --- | --- |
| `boolean full = true;` | 三端各 1 | 1 / 1 / 1 |
| `BlurMotionGate` | 产品代码 0 命中 | 0 / 0 / 0 |
| one-transform：`PanelOpenMotion.blurCapture(` | 三端各 1 | 1 / 1 / 1 |
| 渲染器 `System.nanoTime()` | 三端各 13 | 13 / 13 / 13 |

**新增测试**：`FirstOpenBudgetTest`、`FirstOpenPrewarmWiringTest`、`EmojiPrewarmScannerTest`、`EmojiPrewarmPixelTest`、`FirstOpenOffscreenMeasureTest`。

---

## 8. 未解风险 / 本轮没做的

1. **没有任何真机数据**。本轮的「挪走了多少」是**静态论证 + 离屏像素等价**，不是实测。首开到底降了多少，只有下一次真机跑出来的 `first_open_budget` 说了算。
2. **`icons` 字段没有埋点**，恒为 0；物品图标的成本目前混在 `other` 里。
3. **设置页图片解码（79.80ms 那一帧的另一半）没动**，仍在进设置页时同步解码。
4. **Skija `FontMgr` 的多线程首次初始化安全性未证明**；靠「冷路径兜底」兜着。
5. **预热时机的风险**：如果预热线程在首开**之后**才完成，首开仍然会付全额（此时 `prewarm_*` 显示 `-1.000` 或很大的数，可据此判断）。加世界时的 emoji 预热依赖聊天记录已就绪，可能天然偏晚。
6. **Emoji 预热有上限**（192 个 spec、200 条消息、256 个预热字体），超出的部分仍然首开现做。
7. 本轮**没有做变异红灯**（被中断的代理来不及做）。补做时建议：故意把预热开关关掉 → `FirstOpenPrewarmWiringTest` / `EmojiPrewarmPixelTest` 应变红。

---

## 9. 下一步建议

1. 部署本轮包到 `1.21.1-CCB`，开一次游戏，**打开面板一次**，读日志里那行 `first_open_budget`。
2. 按 §4 的读法判断三处预热是否都赶上了；哪个没赶上就把它再往前挪。
3. 如果 `other` 仍然很大，按 §2 的表逐项补埋点（下一个嫌疑是设置页图片与聊天文本 shape）。
4. **首开的 160ms 里可能还有一整块我们没归因**（本次日志里还有一个 458.25ms 的尖峰）。预算工具能覆盖到的只是我们自己埋的点。

---

## 附：文件清单

**新增**：`shared/.../diagnostics/FirstOpenBudget.java`、`shared/.../render/FirstOpenWarmup.java`、`shared/.../render/EmojiPrewarmScanner.java`、`shared/.../render/EmojiPrewarmSpec.java`、5 个测试。

**修改**：`shared/.../font/FontManager.java`、`shared/.../render/SkiaFontRenderer.java`、`shared/.../render/EmojiGlyphCache.java`、三端 `AtomChatClient.java`、三端 `PanelBlurRenderer.java`。
