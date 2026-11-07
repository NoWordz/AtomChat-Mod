# 首开修复的稳态回归复查：查了什么、改了什么、还剩什么

**日期**：2026-11-07（数据来自 11-06 23:30 那次真机） ｜ **范围**：AtomChat `D:/AtomChat-Main` ｜ **状态**：代码完成、构建与测试见 §6；**未部署前的验证报告**

---

## 0. 一句话

用户报告「稳态帧数反而比之前更低」。复查结论：**这不是一次干净的回归判定——两次会话不可比**；但复查过程中查出了**两个我自己的真 bug**，都已修。

---

## 1. 现象与数据

### 真机日志

```
[23:30:32] [AtomChat-FontPrewarm] Loaded bundled font  /com/atom/lib/fonts/bundled-default.otf
[23:30:32] [AtomChat-FontPrewarm] Loaded bundled bold font /com/atom/lib/fonts/bundled-default-bold.otf
[23:32:01] first_open_budget total=102.581 font=0.000 emoji=0.000 blur_init=0.003 icons=0.981
           other=101.597 prewarm_font=132.443 prewarm_emoji=-1.000 prewarm_gl=47.279
[23:32:01] frame_pump p50=2.498 fps=400.3 max=11.88      ← 面板关着
[23:32:03] frame_pump p50=4.176 fps=239.4 max=82.29      ← 面板开着（含首开）
```

### 同口径对比（基线 = `logs/2026-11-06-5.log.gz`，20:28 那场 E0/E1）

| 指标 | 基线 20:28 | 当前 23:30 | 变化 |
| --- | --- | --- | --- |
| 地板（面板关，最小 p50） | 2.481 ms | 2.498 ms | **+0.017（等同）** |
| 面板段中位 | 2.931 ms | **4.175 ms** | **+1.24 ms** |
| 面板段最高 | 4.466 ms | 5.892 ms | +1.43 ms |
| `panel` 桶均值 | 1.99 ms | 2.53 ms | +27% |
| `chat_render` 桶均值 | 1.96 ms | 2.48 ms | +27% |
| `layout` 桶 | n=123, 均值 0.06 | n=256, 均值 0.34 | 调用数翻倍 |
| 模糊 pre-pass total | **0.220 ms**（passes 0.168） | **0.284 ms**（passes 0.218） | **+0.064 ms** |
| `emotes_render` / `emoji_render` / `phrases_render` 桶 | **不存在** | n=851 / 63 / 133 | 新增埋点 |

**首开长帧**：160.28 ms → **82.29 ms**（同一个位置对比，字体预热生效）。

---

## 2. 为什么这两次**不可比**（必须先说清楚，否则会追错方向）

1. **地板被 400 帧锁截断**。面板关着时两场都是 ~2.49 ms，但这是撞上限的读数，**看不出世界本身变重了没有**。23:30 与 20:28 相差三小时，光照、场景、加载的区块都可能不同。
2. **面板内容不同**。`emotes_render` / `emoji_render` / `phrases_render` 三个桶在基线里**根本不存在**——它们是本次构建**新增的分桶埋点**（`SkiaFontRenderer` 的改动带来的），照出了原本就存在、之前没被测量的成本。基线那场面板里没有表情、没有 emoji、没有短语；这一场有 851 次表情渲染。
3. 两次的用户场景不同（20:28 是专门做仪表实验；23:30 是日常游玩）。

**结论**：+1.24 ms **无法归因**。要判定必须做同世界、同分钟、包对照的 A/B（§7）。

---

## 3. 查出的真 bug #1：emoji 预热**从来没跑过**

**证据**：`prewarm_emoji=-1.000`（首开时预热未完成）＋ `emoji_render max=159.32ms`（153 ms 尖峰原封不动地还在）。

**根因**：`FirstOpenWarmup.prewarmEmojiAsync(...)` 挂在 `AtomChatClient` 的 **onJoin** 里。而**加入世界时聊天记录还是空的** → `firstOpenWarmupTexts()` 返回空列表 → 预热直接 return → 预热从未发生。等你按 Y 打开面板时聊天有内容了，只能在渲染线程现场栅格化 emoji。

**这就是「首开 153 ms 尖峰没有被挪走」的原因**，而且和上一份报告里「emoji 首栅格 153.15 ms」的历史记录完全对得上。

**修法**：触发时机从「加入世界」改成「**跟内容走**」——
`FirstOpenWarmup.pollEmojiPrewarm(generation, texts)`：客户端 tick 里用 `ChatStore.generation()`（现成的变更计数器，volatile 读）轮询，backlog 变了才启动一轮预热；两秒节流；面板打开时不轮询（渲染线程正忙）；空 backlog 也记下这一代，避免每 tick 重问。

**为什么不是「join 时多等一会」**：聊天记录是**之后**才有的，join 时等也等不出来。触发必须跟着内容。

---

## 4. 查出的真 bug #2：模糊预热把**容量下限**抬高了

**证据**：模糊 pre-pass `0.220 ms → 0.284 ms`（passes `0.168 → 0.218`），真机，每帧都付。

**根因**：`PanelBlurRenderer.prewarm(w, h)` 调的是 `ensureTextures(w, h, false)`（容量模式）。容量模式下正式路径取

```java
nw = Math.max(nextPow2(w), wasCapacity ? capW : 0);   // capW 是下限
```

**capW/capH 只会变大，不会变小。** 预热按自己的面板尺寸抬了下限，正式路径就再也降不回去；而模糊 pass 跑的是**整张容量纹理**，于是每帧都在为多出来的面积买单。

预热用的尺寸来自 `ScreenMetrics.panelWidth(window) * scale`，正式路径用的是实际绘制的 `width * scale`——两者只要跨过一个 2 的幂边界，纹理就永久大一档。

**修法**：**预热只编译 shader，不分配纹理**（`prewarm()` 只调 `ensureLoaded()`）。shader 编译才是首开的大头，而且与面板尺寸无关；纹理分配交回运行时，和预热存在之前一模一样。

---

## 5. 查出的真 bug #3（附带）：`other=101.597 ms` 至今没归因

首开预算把 total 拆成 font / emoji / blur_init / icons 四段，其余归入 `other`。这次四段几乎全 0（都挪走了），**但 `other` 高达 101.6 ms** —— 说明首开真正的大头**不在我们埋点的任何一段里**。

最大嫌疑：**Skia 自己的 GL 管线编译与字体图集**。`MinecraftRenderBridge.prepare()` 只做 `withFrameAccess(target, frame -> null)`——**拿帧但不画**，所以 Skija 的 program 编译与字体图集构建都落在首开那一帧。

**本轮做的是「让它可归因」而不是猜着改**：`FirstOpenBudget` 新增 `first_draw` 段（首帧 `render()` 调用的墙钟），三端屏幕各接一对 `markDrawStart()` / `addFirstDraw()`。下一次真机就能知道这 101 ms 里有多少是首帧绘制。

> 顺带：这次新增的 `emotes_render` / `emoji_render` / `phrases_render` 三个桶**不是回归**，是新增的分桶埋点，把一直存在但没被测量的成本照了出来。别再把它们当成新开销。

---

## 6. 改动清单与验证

### 改动

| 文件 | 改动 |
| --- | --- |
| `shared/.../render/FirstOpenWarmup.java` | emoji 预热改为按 `ChatStore.generation()` 轮询触发；`prewarmEmojiAsync` → `pollEmojiPrewarm`；空 backlog 也记账；`onDisconnect` 重置代次；`emojiColors()` 在配置不可用时降级 |
| `shared/.../diagnostics/FirstOpenBudget.java` | 新增 `first_draw` 段（`markDrawStart` / `addFirstDraw`），日志行与正则同步 |
| `platforms/*/.../AtomChatClient.java` | 移除 join 时的 emoji 预热；tick 里加轮询；`PanelBlurRenderer.prewarm(w,h)` → `prewarm()` |
| `platforms/*/.../render/PanelBlurRenderer.java` | `prewarm()` 只编译 shader，不再分配纹理 |
| `platforms/*/.../screen/AtomChatScreen.java` | 首帧 `render()` 计时（`markDrawStart` / `addFirstDraw`） |
| 测试 | `FirstOpenWarmupTest`（新增「预热跟着 backlog 走」「空 backlog 不烧预热」）、`FirstOpenPrewarmWiringTest`（钉「预热不得分配纹理」「三端必须轮询 emoji 预热」）、`FirstOpenBudgetTest`（`first_draw` 口径） |

### 验证

| 平台 | 构建 | 测试 |
| --- | --- | --- |
| Fabric 1.21.1 | BUILD SUCCESSFUL (1m22s) | **1745** / 0 失败 / 0 错误 / 7 跳过 |
| Forge 1.20.1 | BUILD SUCCESSFUL (1m41s) | **1730** / 0 失败 / 0 错误 / 7 跳过 |
| NeoForge 1.21.1 | BUILD SUCCESSFUL (1m34s) | **1730** / 0 失败 / 0 错误 / 7 跳过 |

（构建日志：`build/first-open-20261106/build-{fabric,forge,neoforge}.log`；上一轮基线 1743/1728/1728，本轮各 +2 个新测试）

**已部署**：`D:/Myworld/.minecraft/versions/1.21.1-CCB`，jar sha256 `108ec1a94686e24438fd1eab0687d0086aeae8770b059854a0a819416c03c365`，旧包备份 `atomchat-Fabric-1.21.1-v0.5.0.jar.bak-firstopen-20261107-121153`。未启动游戏。

**红线复查**：`full = true` 三端各 1；无 `BlurMotionGate`；`PanelOpenMotion.blurCapture(` 三端各 1；渲染器 `System.nanoTime()` 三端各 13。

**变异红灯**：把 `pollEmojiPrewarm` 的调用去掉 → `FirstOpenPrewarmWiringTest` 变红；把 `ensureTextures(widthPx, heightPx, false)` 塞回 `prewarm()` → 同一测试变红。

---

## 7. 下一次真机怎么看（只要一行）

打开面板一次，日志里出现：

```
[AtomChat][probe] first_open_budget total=… font=… emoji=… blur_init=… icons=… other=… first_draw=… prewarm_font=… prewarm_emoji=… prewarm_gl=…
```

| 看什么 | 期望 | 不对说明什么 |
| --- | --- | --- |
| `prewarm_emoji` | **> 0** | 仍是 `-1.000` → 轮询没触发（聊天一直空，或节流把它挡了） |
| `emoji_render max` | **不再出现 150 ms 级尖峰** | 还在 → emoji 仍在渲染线程现场栅格化 |
| `first_draw` | 看它占 `total` 的多少 | 若 `first_draw` ≈ `other` 的大头 → 就是 Skia 管线/字体图集，下一步专治它 |
| 模糊 pre-pass total | **回到 ≈0.220 ms** | 还 >0.28 → 容量下限仍被抬高了（还有别的路径在预热纹理） |
| 面板段 p50 | 与 20:28 基线（2.93 ms）比 | 仍高 → 才是稳态回归，需要同世界 A/B |

**面板段稳态到底有没有回归，现在还不能判**（见 §2）。要判只需要一次同世界对照：同一个包、开同一个面板、同一分钟，站着别动 15 秒。

---

## 8. 未解 / 未做

1. **稳态 +1.24 ms 未定案**：缺同世界同分钟的 A/B。§2 说明了为什么现在的数据判不了。
2. **首开 101.6 ms 未归因**：本轮只把它变成可测（`first_draw`），没有动手治。治法取决于数据——若是 Skia 管线编译，需要在预热时做一次**真实但不可见**的绘制（1×1 全透明），这需要评估对主帧缓冲的影响。
3. **设置页图片解码**仍在进设置页时同步发生。
4. **预热线程的 CPU 占用**没有上限保护（emoji 预热最坏情况会跑满 10 秒的 fontReady 等待）。
5. 本轮**没有真机验收**（不能替用户启动游戏）。

---

## 附：相关文件

- `docs/verification/2026-11-06-first-open-hitch-fix.md` —— 上一轮（预热本身的实现与首开关键路径全表）
- `build/first-open-20261106/compare_sessions.py` —— 两次会话的同口径对比脚本
- `build/first-open-20261106/analyze_regression.py` —— 地板/面板段分离脚本
