# Astra 全面性能审计（接手收官报告）

**日期**：2026-11-05
**范围**：AtomChat `D:/AtomChat-Main`、AtomLib `D:/AtomLib`、MuoniumPlayer `D:/MuoniumPlayer-multiversion`（只读对照）
**性质**：只读审计 + 复用已落盘的离屏受控验证。**本轮不堆生产补丁**，不改视觉、动画、玻璃效果，不 commit / push / 发布。

---

## 0. 给用户的一句话

审计已收敛并交付到本文件；**你现在不需要重录 JFR、不需要再录日志、不需要启动游戏**。前任在 17:35 停下的四条发现我都复核完了，其中两条成立、一条被证明是错误修复方向、一条是量级误判。下一步只等你决定是否按第 8 节的最小顺序开工（先加三行探针，不改效果）。

---

## 1. 本轮边界与纪律

| 项 | 状态 |
| --- | --- |
| 产品源码 / 实例 JAR / 配置 / 启动器 | **未触碰** |
| 游戏进程 | **未启动、未打断** |
| 两仓未提交 WIP | AC **77** 项、Lib **40** 项，全部原样保留 |
| 新增文件 | 仅本报告 |
| 复用证据 | `build/astra-performance-20261105/`、`build/deep-performance-20261105/` 全部已保全日志、探针、反编译、结构化产物 |
| 离屏探针 | 复核了 `AstraProbe.java` 源码（确实调用生产 `DotTexture` / `GlStateScope` / 生产 Kawase 着色器、带 `GL_TIME_ELAPSED`），**未重复执行同一探针**；本轮独立复算见 §4.3 |

证据双轨：环境事实（日志、JAR、SHA-256、字节码）与代码事实（现役源码、`git show HEAD`）分列，不互相代替。所有路径以 `D:/AtomChat-Main` 为基准，行号指向当前工作区文件；`D:/AtomLib` 路径单独标注。

---

## 2. 代码与文档覆盖清单（区分正本 / 历史证据 / 生成快照）

前任已建好结构化清单，本轮复核后直接采用，不重复扫荡几十份相同构建产物：

- `build/astra-performance-20261105/inventory.json`：**2,909** 个文件（source 2,741 / docs 168；production-reference 2,252、test 657）。
- `build/astra-performance-20261105/document-coverage.json`：**147** 份性能相关文档的 hash / 行数 / 命中词 / 标题（`docs/verification` 38、`docs/superpowers` 19、`docs/evidence` 21、其它 docs 59、根 10）。
- `hotspot-scan.txt`（461KB）：全量热点扫描，长输出已落盘。

**三类必须分开，这是本轮最重要的防误读约束：**

| 类别 | 路径 | 用途 |
| --- | --- | --- |
| **源码正本** | `shared/src/main/`、`platforms/*/src/main/`、`layers/mapping/official/src/main/`（孪生）、`D:/AtomLib/{ui,runtime,platforms}/` | 唯一可代表当前 WIP 的代码事实 |
| **历史证据** | `docs/verification/`、`docs/superpowers/plans/`、`build/deep-performance-20261105/`、`build/astra-performance-20261105/`、`docs/verification/2026-11-05-agent-handoff/` | 报告、会话、日志、探针，只作证据 |
| **生成源码快照** | `build/*/source/**`（如 `build/stage2-ac-20261105/source/`、`build/render-perf-20261104/*/source/`、`.work/font-panel-validation/source/`） | **构建时复制的旧源码，不是现役事实** |

实际例子：全仓搜 `BlurMotionGate` 会命中十几处，其中绝大多数来自 `build/*/source/` 快照；现役测试只有 `shared/src/test/java/com/atom/chat/notification/NotificationRenderWiringTest.java:46-52`。若不分类，很容易把旧快照的行号当现役代码。

**完整历史（已逐条读完，非只读 final）**：
- `docs/verification/2026-11-05-agent-handoff/index.json:1`；`euler-conversation.md` 45 条、`muonium-research-conversation.md` 13 条，均含原始 JSONL 路径与行号。
- `build/astra-performance-20261105/ampere-conversation.md`、`carson-conversation.md`（含 429 中断与恢复全过程）。
- 必读报告：`2026-11-04-panel-performance-intake.md`、`2026-11-04-panel-optimization-execution.md`、`2026-11-04-render-performance-final.md` / `followup.md`、`2026-11-05-panel-transition-performance.md`、`2026-11-05-settings-text-performance.md`、`2026-11-05-gpu-shadow-and-about-performance.md`、`2026-11-05-muonium-followthrough.md`、`2026-11-05-stage6-user-test-deeper-findings.md`、`docs/superpowers/plans/2026-11-05-muonium-performance-followthrough.md`（含 §65–88 三份补充）、`docs/HANDOFF-panel-performance-codex.md`；Lib 侧 `docs/evidence/render-performance-fix-20261105.md`、`gl-scope-sample-20261105-103433.md`、`perf-fix-20261105-implementation.md`。

**上游纳入但不照搬**：
- `NoWordz/AtomLib#1`：完整正文已读（`build/astra-performance-20261105/issue-1.json`，state=open，**comments=0**，无评论可补）。其"每帧 4–7 次 scope"与"线程优化导致 glGet 同步"两条被本机实测修正（§5.7）。
- Skija `0.143.18`：新旧 JAR 元数据均为同版（`jar-identities.json`），前任已核对 Skia `m143` 原生源码范围（Lib 报告 §17）。本轮未重复联网，结论沿用但标注为上游参考。
- MuoniumPlayer `2.3.1`（`main@c602189`）：13 条会话逐条读完。它的分代模糊、脏标记布局、按显示尺寸预算是**候选工具箱**；它明确**没有整页画面缓存**，且"借用最多落后 8 个采集代"会带来残影，不能直接搬。

---

## 3. 现场身份与环境核对（环境双轨）

| 项 | 旧版 0.3.2 | 新版 stage6 (0.5.0) |
| --- | --- | --- |
| 日志快照 | `build/astra-performance-20261105/3f66172b72d3-latest.log`，147,045 B，**75** 份 profile，末写 17:10:13 | `build/deep-performance-20261105/2026-11-05-6.log`，290,138 B，**94** 份 profile，SHA256 `4ba883375c9ea786ff009a4f71f13cf0f00ec115f376cbeba02a42c5727ceb5c` |
| 时间窗 | 17:06:5x–17:10:09 | 16:44:20–16:49:52 |
| JAR SHA-256 | `5bcd0e33…c238974`（`atomchat-…-0.3.2-….jar.disabled`） | `cad851ad…473136c2`（`atomchat-…-v0.5.0.jar`）；配对 `atomlib-…-0.3.1-shadowcache.jar` = `76c4ba40…f0f96` |
| **架构** | **内嵌** `skija-shared-0.143.18` + `skija-windows-x64-0.143.18`；`fabric.mod.json` **无 atomlib 依赖**；557 类 | 只内嵌 `flatlaf`；`suggests: atomlib` + `custom: {"atomlib:client-required":"*"}`；677 类；Skija 由 Lib 提供（同版 0.143.18） |
| 字体 | 自带 `assets/atomchat/font/bundled.otf` 4,082,228 B / `bundled-bold.otf` 3,513,652 B | 工作区三端 `bundled*.otf` 已删（`git status` 显示 D）；运行时读 Lib 的 `com/atom/lib/fonts/bundled-default.otf` 4,164,908 B。**四份字体 hash 两两不同**（`font-identities.json`） |
| 模组集合 | 130 项 | **132 项：新增 `atomlib`、`e33chat`** |
| GPU / 驱动 | RTX 5060 Laptop / OpenGL 4.6.0 NVIDIA 617.42 / `NVIDIA_THREADED_OPTIMIZATIONS` workaround | 同左，逐行一致 |
| 面板配置 | `blur=on (0.93) wallpaper=none imageMessages=on animations=on panel=480x860 @1.0x debug=on` | 同左，逐字一致（日志 668 行 vs 旧 719 行） |
| 诊断实现 | **3 个桶**（`decode/layout/panel`），无分段、无计数器 | **17 个桶 + 8 个分段 + 计数器** |

**架构结论（查证而非猜测）**：0.3.2 是**独立 Skija、不依赖 AtomLib** 的旧架构；0.5.0 是**依赖 AtomLib** 的新架构，字体、Skija 加载、GL 状态保存都改走 Lib。因此"新旧 FPS 差"至少混合了架构迁移、字体源更换、诊断实现变重、以及多出的两个模组，**不能简化为单个 AC 函数的回归**。

**未控制项（明确披露，不伪称严格 A/B）**：两版由用户自行换包、非同一次连续录制；世界/相机/时间/后台负载/窗口实际尺寸/guiScale 数值/光影开关状态未在日志中绑定成同一条件；资源包与光影仅核对到 ResourceManager 列表层级；新版诊断桶更多，debug 开销本身不等；`e33chat` 是另一个每帧可能有开销的聊天 mod。**因此本报告不给"新版回归了 X FPS"的定量断言**，只给可验证的机制差异。

---

## 4. 新旧指标（只比同名同口径的桶）

### 4.1 可比桶

| 桶 | 0.3.2（75 窗口） | stage6（94 窗口） | 判读 |
| --- | --- | --- | --- |
| `panel` p50 | 1.29–2.43，中位 **1.61** ms | 1.53–2.29，中位 **1.77** ms | 新版略高，但两版桶覆盖范围与诊断实现不同 |
| `panel` max | **160.69** ms | **218.23** ms | 两版都有首开长帧 |
| `panel` alloc | 18.8–99.4 KB/call | 27.6–47.4 KB/call | **累计平均，不可与耗时同批比较**（§5.3） |
| `layout` p50 | 0.01–0.54 | 0.03–0.56 | 基本一致 |
| `decode` | 有样本 | 有样本 | 都极少 |

### 4.2 新版独有分段（stage6 稳态 p50 区间，ms）

| 段 | 范围 | 末窗 |
| --- | --- | --- |
| `BRIDGE_ENTER` | 0.435–0.495 | 0.443 |
| `BRIDGE_EXIT` | 0.208–0.347 | 0.335 |
| `DRAW` | 0.708–1.320 | 1.020 |
| `DOTS` | 0.558–0.906 | 0.754 |
| `BLUR`（**仅 CPU 提交**） | 0.141–0.241 | 0.156 |
| `LIST` | 0.127–0.285 | 0.204 |
| `PRE_UI` | 0.002–0.247 | 0.158 |
| `ICONS` | ~0.000 | 0.000 |

用户体感 FPS（**不是 profile 数据**）：旧版热态聊天 380–390；新版聊天初 280–300，发消息后 >300，导航聊天 300–350 / 个人 300–330 / 设置导航 370 / 字多设置 250–300 / 关于 200–250。旧版同样"开面板/切页掉帧、发任意消息反而更快、关于页最低"。

> ⚠️ `FrameProfile.java:167` 按 surface 分桶，设置 / 关于 / 个人 / 导航全部落 `OTHER`；4096 帧滚窗不带 `pageKey`（`:46`、`:241-257`）。**不存在"关于页 profile"能对应 200–250 FPS**，任何把某个 `OTHER` p50 命名为关于页实测的说法都不成立。

### 4.3 本轮独立复算：发送事件没有阶跃

从 `log-comparison.json` 重算 stage6 时间线（发送事件在日志 1142 行 `16:46:57`）：

| 行 / 时间 | panel p50 | chat_interval p50 | LIST | BLUR | BRIDGE_ENTER | DOTS |
| --- | --- | --- | --- | --- | --- | --- |
| 1049 / 16:46:41（首开后） | 2.29 | 3.70 | 0.285 | 0.241 | 0.495 | 0.906 |
| 1147 / 16:46:59（发送后 2s） | 2.04 | 3.28 | 0.269 | 0.187 | 0.458 | 0.879 |
| 1191 / 16:47:03 | 2.00 | 3.21 | 0.253 | 0.181 | 0.451 | 0.848 |
| 3571 / 16:49:47（末窗） | 2.00 | 3.02 | 0.204 | 0.156 | 0.443 | 0.754 |

六个指标**从首开到末窗单调缓降**，发送点处无阶跃；且在发送前 `16:46:55` 滚窗 `chat_interval` p50 已达 3.33 ms。这独立支持：发送后的提升是**连续预热 / 滚窗换血**，不是"有消息走了更便宜的绘制分支"。空态额外绘制真实存在但量级小（§6.2）。

---

## 5. 历史纠错（逐条，含前序报告与交接假设）

1. **218 ms / 139 ms 只是同窗，不是归因。** 首开 `16:46:38 PANEL_OPEN`、`16:46:39` 渲染线程首次同步加载 4.16 MB 字体（`FontManager.getDefaultTypeface` / `decodeTypeface`），同窗 `panel max=218.23 / interval max=232.50`；emoji 首窗 `max=139.08` 与同步 `EmojiGlyphCache.rasterize`、`EmojiPanel.toggle → refreshEmotes` 同窗。首次桥接、着色器、字形上传都在同窗，**不能说 218 全由字体、139 全由 emoji 造成**。
2. **"单个 218 ms 样本退出 4096 窗口导致发送后变快"= 否定。** 单样本退出主要影响 `max/mean`（`FrameProfile.java:278-288`），不解释 p50 整体提升，更不解释独立于 profile 的 Minecraft FPS。§4.3 时间线给出反证。
3. **分配列口径错误（已用代码 + 数据双证）。** `FrameProfile.java:245-260` 的 `allocatedTotal/allocatedSamples` 是**全期累计平均**，只有 `clear()`（`:264-269`）才重置；耗时列是 `CAPACITY=4096` 滚窗（`:46`、`:241-257`）。`row()` 在 `:289-295` 打印累计均值。探针 `allocation_window_probe` 喂 8192 个样本后输出 `n=4096 p50=2.00 … alloc=50.5 KB/call`（50.5 = (102400+1024)/2/1024）——证明两列不同批。旧版日志实测：`n=600 alloc=31.9` → `n=4096 alloc=24.1` → 之后窗口恒为 4096 而 `alloc` 升到 73.7 再回落到 71.5，同时 `p50` 从 1.83 降到 1.48、`max` 从 160.69 降到 20.64。**此前"分配随页面变化"的横比无效。**
4. **★ 关于页静态层优化的收益在真实页面不兑现。** 门槛 `DotTexture.java:36` `HALO_IMAGE_MIN_AREA=40_000F`，判断在 `:200 box.w()*box.h() >= 40_000`。真实关于页 hero：`SettingsSectionPage.java:2543-2551`，`iconSize=min(SETTINGS_HERO_ICON_SIZE, available)`、`haloPad=DOT_HERO_PAD`，且 halo 被 `Math.max(rect.x(),…)/Math.min(rect.right(),…)` 进一步裁剪。`UiTokens.java:578,737` + `s()` 缩放后探针实测最大边 **158.75**，面积 **25,201.6 < 40,000**；探针直接测到 `image_cache=0`（`HALO_IMAGES` 为空）。stage5 那份 `0.910 → 0.072 ms` 的 A/B 用的是 **320×215 = 68,800 面积**的受控区域，量级上更接近 `ProfilePage.java:1153-1156` 的 banner halo（`banner.w()*0.65 × banner.h()*0.72`），不是关于页 hero。**结论：测试证明了缓存机制有效，没有证明真实关于页会进入它。**
5. **`BLUR_RESOURCE=21` 是 21 次重建，不是 21 个资源对象。** `PanelBlurRenderer.java:279-287`：尺寸任一不同 → `destroyTextures()` → `FrameSegments.count(BLUR_RESOURCE)` 一次 → 新建三套 texture/FBO（`:291-299`）。一次 counter = 一次三目标重建。且 4096 滚窗可能包含关闭过程，不能把 21 全归给单次打开。
6. **`DOTS` 0.56–0.91 ms 是全局装饰段（含 `drawEdge`），不是"About 大 halo 已烘焙"。** 段之间嵌套，不能与 `DRAW` 相加。此前 `0.910→0.072 ms` 是受控场景，不是全游戏收益。
7. **AtomLib issue #1 的两条前提需修正。** ① "每帧 4–7 次 scope"：JFR 实测 23,368 个完整面板帧**每帧恰好 1 次** capture、depth 全 0（`D:/AtomLib/docs/evidence/gl-scope-sample-20261105-103433.md:10-11`）。② "glGet 因 NVIDIA 线程优化强制同步"：两版日志均记录 `Applying workaround: Prevent the NVIDIA OpenGL driver from using broken optimizations (NVIDIA_THREADED_OPTIMIZATIONS)`，该前提在本机不成立。**但 2.13 ms 的实测 scope 成本与 `BRIDGE_ENTER` 2.12 ms 吻合仍成立**，Lib 已修到 ~0.44 ms（`render-performance-fix-20261105.md:32-39` 的 FULL 2.5923 → SKIA 0.5948 ms 离屏 A/B）。修正的是机制解释，不是收益事实。
8. **★ "恢复旧版 500 ms 节流"是错误修复方向，不要做。** 事实层面旧版确有节流：日志 1100 行 `blur cadence: fallback (scene still, refresh at most every 500ms)`、1605 行 `fast (motion, refresh every 2 frames)`，旧包字节码 `old-PanelBlurRenderer.javap.txt:955-975` 调 `System.currentTimeMillis()` → `BlurMotionGate.allowFullRefresh(...)`，仅当 `allow=true` 才执行 blit + 5 pass（`:977-`）。新版 `PanelBlurRenderer.java:370` `boolean full = true;`（Fabric `:370` / Forge `:344` / NeoForge `:343` 三端一致），stage6 日志**无 cadence 行**。`git show HEAD` 与 `git log -S` 确认这行是 `a1dff96b`（0.4.0-preview 时期）引入的**有意决策**，本轮 WIP diff 完全没碰它。**但**：`NotificationRenderWiringTest.java:46-52` 明断 `assertFalse(blur.contains("BlurMotionGate"))` + `assertTrue(blur.contains("boolean full = true;"))`；`docs/HANDOFF-panel-performance-codex.md:190` 与 CHANGELOG 记载原因是"每帧取当前背景，否则转视角拖影"。**更关键的是量级**：离屏 GPU 计时显示 5 pass 仅 `0.0620 ms`(480×860) / `0.2156 ms`(960×1720)，回退节流的可得收益上限远小于一次纹理重建（§6.1）。**结论：这是真实的新旧机制差异，但不是可回退的 bug，也不是主要成本。**
9. **旧版不是"没有诊断"。** 75 份 `AtomChat render profile (debug)`，`debug=on`；只是桶少（3 个）、无 `FrameSegments` 分段与计数器。
10. **0.3.0-Preview 启动失败不能否定 0.3.2。** `zip END header not found` 属损坏下载；本轮有效旧版对照是 0.3.2。

---

## 6. 根因链（按"能解释多少现象"排序）

### 6.1 【已证实 · 长期共因 · 解释力最强】开合动画期间模糊目标尺寸变化 → 每帧重建三套纹理/FBO

- **链**：`AtomChatScreen.java:1987-1995` 把 `panelProgress` 送入 `PanelOpenMotion.blurCapture`（`:1988`），再把 capture 宽高交给 `PanelBlurRenderer.render`（`:1993`）→ `ensureTextures(pw, ph)`（`PanelBlurRenderer.java:106`，实现在 `:279-300`）→ 宽高任一变化即 `destroyTextures()`（`:327-339`）+ 3× `makeTexture()`（`:302-325`，`glTexImage2D` + FBO 挂载）。
- **日志**：stage6 首窗 `BLUR_RESOURCE=7`（1066–1164 行），稳定窗口 `0`（1182 行起），第二次开合窗口 `21`。旧包字节码同样有 `ensureTextures`（`old-PanelBlurRenderer.javap.txt:712`），所以**新旧共有**——与用户"旧版也开面板/切页掉帧"一致。
- **受控 GPU 计时**（`probe-results.txt`，RTX 5060，真实 Kawase fsh，`GL_TIME_ELAPSED`，120 样本）：

| 场景 | wall p50 | wall p95 | GPU p50 | GPU p95 |
| --- | ---: | ---: | ---: | ---: |
| blur5 480×860 | 0.1126 | 0.2044 | 0.0620 | 0.0623 |
| blur5 960×1720 | 0.2677 | 0.3107 | 0.2156 | 0.2170 |
| **resize3 480×860** | **1.5767** | 1.8235 | **0.4528** | 0.5618 |
| **resize3 960×1720** | **2.1282** | 2.4470 | **0.6487** | 0.7598 |

  **一次重建 ≈ 5 pass 模糊的 7–14 倍墙钟、3–7 倍 GPU。** 这直接推翻"5 pass 全分辨率模糊是主要成本"的假设，也说明优化重点应从"少做 pass"移到"别反复分配目标"。
- **解释**：打开面板骤降、切页掉帧、导航切换小幅掉帧、首窗长帧。
- **不解释**：稳态持续低帧、关于页最低。
- **适用范围**：离屏、隐藏 GL、同机、面板尺寸量级；不等于游戏整帧收益，须真机验收。

### 6.2 【强候选 · 混合】每帧固定 CPU 基线 = GL scope + 装饰点阵 + 空态额外 halo

- `GlStateScope.captureSkia()` 探针 `current_scope_capture_restore` wall p50 **0.5173** / p95 0.7492 ms，GPU **0.0002 ms** → 纯 CPU 查询，与日志 `BRIDGE_ENTER` 0.435–0.495 ms 吻合；占 stage6 `panel` p50 1.77 ms 的约 25%。旧版用自带 `GlStateUtil.save()/restore()`（`old-GlStateUtil.javap.txt`、`old-SkiaGraphics.javap.txt:207,369`），`State` 同样保存全量纹理绑定/采样器/UBO 等字段，**所以这不是新版独有回归**，只是实现不同、量级未单独测。
- `DOTS` 0.558–0.906 ms 是全页面装饰段；`edge_cached_false 0.6223` vs `edge_cached_true 0.5773`（GPU 0.0161 vs 0.0160）说明点阵缓存的真实 GL 收益很小，与 stage5 阴影缓存 A/B（0.096 vs 0.097 ms）一致——**CPU 光栅收益 ≠ GPU 墙钟收益**。
- **空态分支**：`EmptyState.java:38` 仅在 `messages.isEmpty()` 时 `DotTexture.drawHalo`。探针 `empty_halo_present` 0.5038 vs `empty_halo_absent_control` 0.0379 ms（离屏含 clear/flush），差约 **0.47 ms**。这是"发任意类型消息（文字/表情/图片）后都变快"的最具体机制，因为它与消息类型无关、只与空/非空有关。
- **但**：跨窗 `LIST 0.285→0.204` 不是隔离 A/B；§4.3 显示发送点无阶跃、发送前已升温。故只能定为**强候选**，不能定为主因。
- **候选复现/反证**：同热态、同世界镜头下 `空 → 有消息 → 再清空`，记录 `page/empty/glyph/cache/CPU+GPU`；若恢复空态不变慢 ⇒ 预热主导；若可重复变慢 ⇒ 空态分支成立。

### 6.3 【已证实 · 长期共因 · 量级有限】首次同步冷加载

- 字体：两版都在 `PANEL_OPEN` 后 0–1 s 于渲染线程同步加载（旧 17:07:32 `Loaded bundled font /assets/atomchat/font/bundled.otf`；新 16:46:39 `/com/atom/lib/fonts/bundled-default.otf`）。字体文件与 hash 都换了，但**机制新旧共有**。
- emoji：stage6 首窗 `max=139.08 ms`，之后固定样本 980 帧 p50 2.13 ms；旧版无分段桶，不可比，**不能说旧版没有这个尖峰**。
- **解释**：首开长帧、首次展开表情掉帧（1 s 内回升）。**不解释**稳态。

### 6.4 【真实差异但被误读】每帧 5 pass 全分辨率模糊

- 事实：新版 `full=true` 每帧执行 1 次 capture blit + 5 个全分辨率 Kawase pass + 1 次圆角合成；旧版静止时最多 500 ms / 运动时每 2 帧刷新一次。
- **但** GPU 实测 5 pass 仅 0.062–0.216 ms，且有意的防拖影约束 + 现役测试断言（§5.8）。**它解释"新旧机制不同"，不解释"新旧 FPS 差"**。
- **观测缺口**：AC 原方法在**游戏内**的 GPU 完成时间仍缺。`GpuTimerQuery.java:22` 类已存在，全仓只有 `PanelDiagnosticsTest.java` 的测试引用，**未接生产**；日志 `BLUR 0.14–0.24 ms` 只是 CPU 提交，不能宣称"已测 GPU 排除模糊"。

### 6.5 【否定 / 降级】

| 假设 | 判定 | 依据 |
| --- | --- | --- |
| Skija 全局性能缺陷 | 否定 | 两版同版 Skija 0.143.18；Muonium 也无整页缓存；AC 直接画宿主 FBO，显示链更短 |
| 动画逐卡 `saveLayer` 是主因 | 否定 | 10 卡仅多 0.28–0.32 ms 离屏；旧 JFR 显示设置详情 608 ms 后仍低帧 |
| Yoga 每帧全量重排（issue #1 第 2 条） | 不适用于 AC 设置页 | AC 用纯 `UiLayout` 几何，不走 AtomLib Yoga（euler 消息 32）；Lib 侧已修 `YogaLayoutEngine` |
| 阴影缓存带来 GPU 收益 | 降级为"保留但不宣称" | 稳定命中（120 帧仍 1 key），但同机 A/B 0.096 vs 0.097 ms 无降时；CPU 光栅 8.4× 收益不等于 FPS |
| Profile 首次皮肤读回解释持续低帧 | 否定（只解释冷尖峰） | PBO 异步已修，仅首次/缓存冷启动 |
| 关于页每帧扫缓存目录 | 已修且非主因 | `ImageLoader.diskCacheBytes()` 已做 1 s 时效缓存 |

---

## 7. 发现分级（每条附复现 / 反证 / 耗时 / 适用范围）

| # | 分级 | 结论 | 证据 | 复现 / 反证 | 耗时或范围 |
| --- | --- | --- | --- | --- | --- |
| F1 | **已证实** | 开合期尺寸抖动导致三套纹理反复重建，比 5 pass 贵一个量级 | `PanelBlurRenderer.java:279-300`；stage6 日志 1066/1182/3600；`probe-results.txt:4,7` | 记录旧/新尺寸序列 + generation + 每次分配耗时；反证=尺寸分桶后 `BLUR_RESOURCE` 应降到 ≤1 | 1.58–2.13 ms wall / 0.45–0.65 ms GPU **每次**；离屏 |
| F2 | **已证实** | 真实关于页 hero 面积 25,202 < 40,000 门槛，静态缓存分支不进入 | `DotTexture.java:36,200`；`SettingsSectionPage.java:2543-2551`；`UiTokens.java:578,737`；探针 `image_cache=0` | 打印真实 `drawHalo` 的 box 与 `HALO_IMAGES.size()`；反证=门槛下调后应出现 key | 候选收益 0.40→0.05 ms 离屏，**游戏内未验** |
| F3 | **已证实** | 分配列是累计平均、耗时是 4096 滚窗，历次横比无效 | `FrameProfile.java:46,241-260,289-295`；探针 `allocation_window_probe` | 喂两批不同 alloc 的 4096 样本看 alloc 是否复位 | 影响所有历史"分配下降"结论 |
| F4 | **已证实** | `full=true` 是有意防拖影，有现役测试断言；回退节流既违规又低收益 | `PanelBlurRenderer.java:370`；`NotificationRenderWiringTest.java:46-52`；`HANDOFF:190`；`git log -S` → `a1dff96b` | 试图改回 `BlurMotionGate` 会让该测试变红 | 可得 GPU 收益上限 0.06–0.22 ms/帧 |
| F5 | **已证实** | 发送前后各段单调缓降、无阶跃，提升来自连续预热 | §4.3 独立复算；`log-comparison.json` | 同热态空↔有消息可逆对照 | 全程 16:46:41–16:49:47 |
| F6 | **强候选** | 空态额外 halo 是"发任意消息更快"的具体分支，但非主因 | `EmptyState.java:38`；探针 `empty_halo_present/absent` | 见 §6.2 可逆对照 | ~0.47 ms 离屏差；跨窗 LIST 差 0.08–0.12 ms |
| F7 | **强候选** | 每帧固定 CPU 基线（scope 0.44–0.52 + DOTS 0.56–0.91）压住稳态 FPS 上限 | `probe-results.txt:9`；stage6 分段 | 页面级 GPU timer + pageKey 后重算 | 每帧固定；**新旧都有同类成本** |
| F8 | **强候选** | 首次字体/emoji 同步冷加载造成首开长帧 | 两版日志；`FontManager` / `EmojiGlyphCache` | 加有界一次性 stage 计时分别测字体/桥接/着色器/字形上传 | 同窗 218 / 139 ms，**未单独归因** |
| F9 | **否定** | 218 ms 退出窗口解释发送后提升 | §4.3；§5.2 | 单样本只改 max/mean | — |
| F10 | **否定** | 5 pass 全分辨率模糊是主要持续成本 | `probe-results.txt:2,5` | 恢复节流不降 FPS 即为反证 | GPU 0.06–0.22 ms |
| F11 | **否定** | 旧版无诊断日志 / 0.3.0-Preview 可代表旧版 | `log-snapshots.json`；旧日志 1101 行 | — | — |
| F12 | **待真机** | AC 原方法在游戏内的 GPU 完成时间；按页面拆分的持续低帧归因 | `GpuTimerQuery.java:22` 未接生产；`FrameProfile.java:168` 无 pageKey | 接有界跨帧非阻塞 query + `pageKey/phase/首开标志`，一次统一验收即可，不重录 JFR | 必须用已接线的诊断版包 |
| F13 | **待真机** | 新版多出的 `e33chat`、诊断桶变重、字体源更换对 FPS 的贡献 | §3 环境表 | 在同一次启动内逐项开关对照 | 未控制，**禁止归因** |

---

## 8. 最小修复顺序与验收（本轮不实施，供拍板）

> 原则：先量后改、一次一个因素、收益不相加、不改视觉/动画/玻璃、不恢复 `BlurMotionGate`。

| 优先 | 动作 | 前置三行探针 | 验收 |
| --- | --- | --- | --- |
| **P0-A** | 消除开合期资源重建：`ensureTextures` 改尺寸分桶 / 按 2 的幂扩容复用，保持 capture 坐标、采样 UV、半径、裁剪不变 | 每次重建记录 `oldW×oldH → newW×newH`、generation、分配墙钟 | `BLUR_RESOURCE` 21 → ≤1；开面板 `panel max` 下降；golden frame 逐像素一致；三端 build + 隐藏 GL |
| **P0-B** | 让静态层真正覆盖真实页面：先量真实 About / 空态 / Profile 各 halo 的 box 与每帧调用次数，再定门槛与预算；**不是直接把 40,000 调小** | 打印 `drawHalo` box 宽高、面积、`HALO_IMAGES.size()`、命中/未命中计数 | 真实页面出现稳定命中 key；像素 A/B ≤ 8/255；不闪烁、不缺图；有界 LRU 与字节预算仍生效 |
| **P1-A** | 把 `GpuTimerQuery` 接入生产（有界、跨帧非阻塞、避让其他 mod 的 query），并给 `FrameProfile` 补 `pageKey` / 过渡 phase / 首开标志（复用 `FrameSegments`） | 先确认 query 结果不阻塞、不 `glFinish` 污染帧循环 | 能给出按页面拆分的 GPU 完成时间；不再用 `OTHER` 桶命名页面 |
| **P1-B** | 字体 / emoji 冷加载移出交互绘制线程（有界预热，覆盖下一次资源代与总字节预算） | 一次性 stage 计时分别量资源读取、Data 拷贝、FontMgr 解码、variable clone、glyph 栅格化 | 首开长帧下降且没有把阻塞挪到别的交互时刻 |
| **P2** | 仅当 P1-A 证明 5 pass 在游戏内确实贵时，考虑同帧共享 / 半分辨率 / 容量分桶等**不改变新鲜背景**的方案 | 必须带 A/B 与截图验收 | 不引入跨帧陈旧背景、无转视角拖影；`NotificationRenderWiringTest` 保持绿 |

**统一验收（一次，不需重录 JFR）**：先确认加载包 SHA-256 → 聊天 → 设置导航 → 聊天设置两 tab → 关于 → 个人页，首次/重复打开分开记录 → 读同一份 `latest.log` 的分段与 GPU 计时 → 三端 build + golden frame + 隐藏 GL。**判定权归用户**，不宣布达标。

---

## 9. 线索覆盖登记（有登记，不平均用力）

| 线索 | 状态 | 处置依据 |
| --- | --- | --- |
| 缓存命中 / alpha 尺寸导致 miss | 已查 | 阴影缓存 120 帧仍 1 key（stage5）；点阵缓存真实页面**不进入**（F2）；探针 `edge_cached` 两种几乎同耗时 |
| 布局 / 视口虚拟化 / O(n²) | 已查 | 设置页 `rowRect` O(n²) 已修（3,240→80）；消息列表已有视口剔除；Muonium 也只是绘制裁剪非真虚拟列表 |
| 过多 JNI / GL 查询 | 已查 | `GlStateScope` 已收窄到 0.44 ms；旧版 `GlStateUtil` 同类但未单独计时；每帧 1 次 capture 已由 JFR 钉死 |
| CPU–GPU 同步 / 读回 | 已查 | PBO 异步已修首次皮肤读回；`glFinish` 只在离屏探针用；生产 GPU timer 未接（F12） |
| 资源抖动 | **主结论** | F1，三套纹理/FBO 反复重建 |
| GL 状态冗余 / 恢复 | 已查 | `refreshBlur` 保存/恢复 viewport、scissor、FBO 并对 Blaze3d 缓存做镜像（`:356-397`）；探针含查询的 blur5 仅多 0.017–0.026 ms wall |
| 无界分配 / 泄漏 | 已查 | `ImageStore` 128 条、`TextBlob` 2,048 条/131,072 字符、`DotTexture` 8 条/32 MB 均有预算；分配列历史比较失效（F3） |
| 调试 / 诊断日志开销 | **未控制** | 新版 17 桶 + 8 段 + counter vs 旧版 3 桶；两版 `debug=on`；未量化，列入 F13 |
| 帧外渲染 / 外部 mod 交互 | 部分 | Gnetum HUD 兼容已定案（记忆 `reference_skija-blur-in-mc.md`）；Iris 在资源列表；**新版多 `e33chat`** 未测（F13）；`NVIDIA_THREADED_OPTIMIZATIONS` workaround 两版一致 |
| 上游 Skija / Skia issue | 纳入未照搬 | 同版 0.143.18；无对应上游 bug；Muonium 也无整页缓存 |
| AtomLib issue #1 全 6 条 | 逐条判过 | 1 已修但机制解释修正；2/3 Lib 侧已修；4/5 部分适用（尺寸重建=本轮 F1）；6 已修 |
| 首开 / 切页 / 发送 / 关于 四现象 | 全部有归属 | §6.1–6.4；**关于页"最低"仍无 profile 级归因**（OTHER 混页），列为 F12 首要目标 |

---

## 10. 证据索引

| 用途 | 路径 |
| --- | --- |
| 本轮全部结构化产物 | `build/astra-performance-20261105/`（`log-comparison.json`、`log-windows.csv`、`inventory.json`、`document-coverage.json`、`jar-identities.json`、`font-identities.json`、`issue-1.json`、`hotspot-scan.txt`） |
| 离屏探针源码 / 结果 | `build/astra-performance-20261105/AstraProbe.java`、`probe-results.txt`、`probe-run.log` |
| 旧版反编译 | `build/astra-performance-20261105/old-PanelBlurRenderer.javap.txt`（节流在 `:913-1000`）、`old-SkiaGraphics.javap.txt`、`old-GlStateUtil*.javap.txt` |
| 旧版 / 新版日志 | `build/astra-performance-20261105/3f66172b72d3-latest.log`、`build/deep-performance-20261105/2026-11-05-6.log` |
| 用户反馈 / 快照索引 | `build/astra-performance-20261105/user-latest-feedback.md`、`log-snapshots.json` |
| 空/有消息旁路报告 | `build/deep-performance-20261105/empty-vs-populated-code.md`、`profile-reports.json` |
| 完整会话 | `docs/verification/2026-11-05-agent-handoff/` + `build/astra-performance-20261105/{ampere,carson}-conversation.md` |
| Lib 证据 | `D:/AtomLib/docs/evidence/{render-performance-fix-20261105.md,gl-scope-sample-20261105-103433.md}` |

---

**审计轮（21:4x）未做的事（如实披露）**：没有修改任何生产代码，没有重跑离屏 GL 探针，没有跑三端构建，没有部署，没有启动游戏，没有 commit / push / 发布；没有把任何离屏数值宣称成游戏 FPS 收益；没有重录 JFR。前任全部 WIP 与已保真对话原样保留。**以下 §11 是同日 22:0x 用户开工授权后的执行记录，边界已变化。**

---

## 11. 开工执行记录（同日 22:0x–23:5x）

用户两项 grill 决策：**① 先不动视觉**（每帧新鲜毛玻璃保留，排除静止降频 / 开合冻结 / 半分辨率 / 借旧模糊档）；**② 攒一批只验一次**（先性能优化线，Muonium 借鉴线后置）。

### 11.1 P0-A 消除开合期纹理重建 —— 已实施
`docs/verification/2026-11-05-p0a-blur-resource-reuse.md`；产物 `build/p0a-blur-20261105/`。只改三端 `PanelBlurRenderer.java`（370 插入 / 82 删除）。方案：2 的幂容量纹理（仅容量不足时扩容）+ 每帧 blit 子区域 + Kawase quad 顶点缩放保持 1:1 纹素 + 每 pass `GL_NEAREST` 延展右列/底行复现 CLAMP_TO_EDGE + 圆角 quad UV 归一；面板底部出屏回退精确路径。不改 shader / capture 坐标 / 半径 / 裁剪 / alpha。

- 61 帧动画：BASELINE 重建 **57 次 / 101.35ms** → PROD **1 次 / 1.45ms**；变异装回 57 次 / 98.62ms（红）。
- golden frame 7 个动画进度 ×（无裁剪 + 裁剪边界）共 14 组，blur 与 quad 两阶段**逐像素 0 差异**；R1 无延展、R2 一次性延展都先红过。
- `verify_targets.py` 通过；三端隔离快照 build 全绿；`NotificationRenderWiringTest` 三端 7/7 绿；工作区 `versions/atomlib.json` 未动。

### 11.2 P0-B 真实页面静态层 —— **判定「不建议改」，未改生产代码**
`docs/verification/2026-11-05-p0b-static-layer.md`；探针 `build/p0b-static-layer-20261105/`。遍历 6 个真实调用点：about hero `158.75²=25,201.6`、empty `240×135=32,400` 均 <40,000 门槛 → `image_cache=0`；profile banner `301.6×144=43,430.4` 已命中缓存。

| 依据 | 数值 |
| --- | --- |
| 静态图 wall 收益 | 0.38–0.47 ms（离屏含 clear+flush） |
| **静态图 GPU 收益** | **仅 0.008–0.012 ms** |
| 像素 A/B 不透明 | `maxDelta=7/255`（≤8 通过） |
| **像素 A/B 透明/变换极端** | **`maxDelta=255/255`（爆边界）** |

决策门「像素差 ≤8/255」不成立 → 停手，未调 `HALO_IMAGE_MIN_AREA`、未动预算与 `DotTexture.java`。**这是有依据的否定结论，不是遗漏。**

### 11.3 P1 GPU 计时 + 按页面归因 —— 已接生产
`docs/verification/2026-11-05-p1-gpu-pagekey-diagnostics.md`；产物 `build/p1-gpu-pagekey-20261105/`（含 `parse_diagnostics.py`）。新增 `GlGpuTimer`；改 `FrameSegments`（GPU 字段/轮询/降级计数）、`FrameProfile`（pageKey / phase / 首开标志，保持原有桶语义不变）、`NavStackController:274-275`（push 带 section 拆出「关于」）、`PageGpuDiagnosticsTest`；`verify_profile_wiring.py` 的两个 no-op stub 是编译替身，且**同时新增 3 组真断言**（bridge 边界 / 异常路径 / JFR `UiFrame`↔`PanelSegments` 配对），是加强不是假绿。

- 跨帧非阻塞（`RESULT_AVAILABLE` 轮询）、无 `glFinish`、query 冲突/超时静默降级并计数；仅 `debug=on` 生效。
- 每帧额外开销上界 ≈ **0.026 ms**（离屏，取更重的 15×`glGet*` 对照），不等于游戏内值。
- 输出：`gpu_panel n/p50/p95`（面板 GL 区间的 GPU 完成时间，含背景模糊 pre-pass，是纯模糊**上界**，非整帧/非 FPS）、`page=<settings|about|profile|navigation|chat|other>_<first|repeat> n/p50/p95/max/mean/alloc` —— 终于能把 `OTHER` 拆开、区分首开与重复。
- 三端 build 全绿；变异红绿（移除 pageKey → 红 / 恢复 → 绿）；反证（无 context / query 冲突 / 结果不可用 / 其它 mod 占用）全绿。

### 11.4 部署
回执 `build/p0a-p1-deploy-20261105/deployment-receipt.json`，状态 `deployed_and_hash_verified`，`gameProcesses=[]`、`errors=[]`。三端候选 JAR 均独立验证同时含 P0-A（`capacityMode/nextPow2/extendEdge`）与 P1（`GlGpuTimer/GpuTimerQuery/pageKey`）；旧包逐端备份、候选与安装回读 SHA-256 一致、其它 jar 与 AtomLib 哈希未变。CCB 配置 `debug=true` 已开，无需改配置。

| 实例 | 安装包 SHA-256 |
| --- | --- |
| `1.20.1-main` (Forge) | `a109e76b…3d1d` |
| `1.21.1-CCB` (Fabric) | `5b0e14c6…6aef0` |
| `Mechanomania-航空学` (NeoForge) | `2db784e0…37d7a84` |

未启动游戏、未 commit / push / 发布。

### 11.5 唯一一次验收怎么做
启动 `1.21.1-CCB` → 打开面板（记下**开面板那一下**有没有骤降）→ 聊天 → 设置导航 → 聊天设置两个 tab（字多的那个多停几秒）→ 关于 → 个人页，各停约 10 秒；关于页、个人页各再打开一次以区分首开/重复。**不用脚本、不用 JFR、不用换包**，玩完告诉我"好了"，我读同一份 `latest.log` 的 `gpu_panel` 与 `page_*` 行。

**诚实边界（重要）**：这一批**不承诺稳态 400 帧**。P0-A 只修开合/切页瞬间的长帧与资源抖动；P0-B 经量化判定不改；P1 只提供观测。稳态 280–300 的持续低帧尚未有针对性修复，这次验收的核心产出是**第一份按页面拆分的游戏内 GPU 时间**，用来判定下一步该修哪里、以及在「不动视觉」约束下 400 帧是否可达。全部离屏数值不得当作 FPS 收益。
