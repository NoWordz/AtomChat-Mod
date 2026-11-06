# 2026-11-06 · D 档（Muonium 时序策略）实验包

**状态：已实现、三端构建通过、未部署、未真机验收、未 commit。** 本文所有数值标注「离屏 / 受控场景」或「真机待验」，**不等于游戏 FPS 收益**。

---

## 1. 为什么做这一档

你 13:39 的转向：**稳态帧率「稳不稳」（骤降 / 抖动）比平均值更重要**；Muonium 界面元素比 AC 多一个量级（大量文字、专辑封面）却稳态不抖；要求**照搬 Muonium 渲染思路做实验**，判据是「照抄后仍不稳 ⇒ 问题不在 AC/Lib 渲染」。

前两轮真机数据其实已经指向同一处，只是我之前一直盯 p50 漏掉了抖动：

| 档 | 语义 | 帧间隔 p50 | FPS | p50 全距 | >30ms 长帧 |
| --- | --- | --- | --- | --- | --- |
| A | 完整 | 2.914 ms | 343.2 | **1.240 ms** | 4 / 65 窗 |
| B | 只跳背景模糊 pre-pass | 2.6745 ms | 373.9 | 0.361 ms | 0 / 68 窗 |
| C | 整个面板旁路 | 2.491 ms | 401.4（撞 400 帧锁） | **0.014 ms** | 0 / 64 窗 |

去掉每帧背景采集，抖动收敛 3.4 倍；去掉整个面板再收敛 88 倍 ⇒ **不稳的源头几乎全在「每帧重算背景模糊」这条路径**，与 UI 元素多少无关。分段探针进一步把靶子缩到一点：`passes = 0.164ms` 占 pre-pass 总墙钟 0.214ms 的 **77%**，`unaccounted = 0.000ms`（无隐藏 GL 同步气泡），`capture ≈ 0`（P0-A 容量复用已生效）。

Muonium 的「稳」正是同一件事的另一种做法（`docs/verification/2026-11-05-agent-handoff/muonium-research-conversation.md:145-155`）：

| Muonium 做法 | 源码 |
| --- | --- |
| 背景采集默认最多 **30Hz**，blit 到缩小纹理，不开 CPU `readPixels` | `render/skija/GlassBackdrop.java:332` |
| 最多 4 个模糊半径槽、每采集代最多重建 2 档 | `render/skija/GlassBlurLevels.java` |
| 转场允许借用最多落后 **8 个采集代**的旧档 | `render/skija/GlassBackdrop.java` |
| 开合动画期间关闭液态玻璃改画实色 | `core/rendering/GlassLook.java:151` |

**它同样每帧清空重画，没有整页缓存**——稳来自「降低背景采集 / 模糊的时间分辨率」。

## 2. D 档是什么

同包第四档，只移植**时序策略**，不搬 UI 框架。F6 现在循环 **A → B → C → D → A**（备用 `Ctrl+Shift+F6`，`debug=off` 时恒 A 且静默，配置字段 `diagnosticRenderMode` 支持 `"D"`，默认仍 `"A"`）。

| 档 | 画面 | 背景采集 |
| --- | --- | --- |
| A | 完整（产品现状） | 每帧重算 |
| B | 无毛玻璃 | 不跑 pre-pass |
| C | 面板整个不画 | 不跑 |
| **D** | **完整（玻璃照画）** | **30Hz 重算，其余帧复用上一代结果纹理** |

**限频按单调时间**（`System.nanoTime()`，阈值 `BLUR_CADENCE_NS = 33_333_333L`），不是按帧数——帧率在 300–400 之间变化，按帧数会漂移。

本档**只做这一个因素**。另外三个 Muonium 子项（半径槽 / 旧代借用 / 动画期实色）保留为**默认关的配置桩**（`modeDBlurRadiusSlots` / `modeDStaleGenerationBorrow` / `modeDOpaqueDuringAnimation`），本轮未实现，也不假装实现。

### 2.1 三条安全边界

1. **动画期不复用**。复用门控是 `blurComposite && panelSettled && !blurRefreshDue(now)`（三端 `AtomChatScreen` 各一处）。开合动画期间 panel 矩形每帧都在变，复用旧尺寸的结果纹理会把背景拉伸错位；动画期照常每帧刷新，**D 与 A 的差别因此只落在稳态**——正是本实验要测的那一段。
2. **首次进入 D 强制刷新一次**。`Truth3Probe.blurReusable=false` → `blurRefreshDue` 返回 true；`PanelBlurRenderer.renderReuse` 内还有二级降级：`lastBlurTex == -1 || recreated` 时直接 `return render(...)`，绝不采样未初始化或已销毁的纹理。
3. **A 路径一字不动**。D 不成立时 `reusePrevFrame` 恒 false，走原来的 `PanelBlurRenderer.render`。`boolean full = true;` 原样保留，未恢复 `BlurMotionGate`。

### 2.2 一个必须说明的结构修正（中途接手时发现并修掉）

上一轮执行代理被限流打断，留下一个**结构性缺陷**：为加 D 分支，它把整块 `preUi` 构造复制了一份（169 行里出现两处 `PanelOpenMotion.PanelRect capture = PanelOpenMotion.blurCapture(`）。这违反了仓库既有契约 `PanelShellWiringSourceTest.theCaptureRectAndThePaintedRectShareTheOneTransform`——该断言要求 capture 只能有一处推导（`expected: <1> but was: <2>`，两端构建因此全红）。

已合并为**单一捕获 + 档位选择**：capture 推导只一处，D 在 `renderReuse` / `render` 之间选，A/B 走同一入口且 `reusePrevFrame` 恒 false。既满足老契约，也避免「两份推导悄悄漂移」这个老问题复发。旧结构与新结构的测试断言已同步（`ModeDCadenceTest` / `Truth3ModeTest` 是本轮新增未跟踪文件，服从既有契约而非放宽它），断言强度未降低，另加了 `capture == 1`、`render/renderReuse 各 1 调用点`、`settled 门控`三条更强的检查。

## 3. 改动文件

| 文件 | 改动 |
| --- | --- |
| `shared/src/main/java/com/atom/chat/diagnostics/Truth3Probe.java` | `Mode.D`、四档循环、`blurComposite()`、`blurRefreshDue(long)`、`cadenceLine()`、`blur_cadence` 行契约（`CADENCE_LINE_RE`）、切档重置 D 档时序状态 |
| `shared/src/main/java/com/atom/chat/config/AtomChatConfig.java` | `diagnosticRenderMode` 支持 `"D"`；三个默认关的子项桩 |
| `platforms/1.21.1-fabric/.../net/minecraft/client/gui/screen/AtomChatScreen.java` | 单一 capture 入口 + D 档分流（`reusePrevFrame`）+ `blur_cadence` 输出 |
| `platforms/1.20.1-forge/.../com/atom/chat/screen/AtomChatScreen.java` | 同上（`getGuiScale()` / `context.pose()`） |
| `platforms/1.21.1-neoforge/.../com/atom/chat/screen/AtomChatScreen.java` | 同上 |
| 三端 `.../render/PanelBlurRenderer.java` | `renderReuse(...)`：只做圆角合成，不重跑 blit + 5 pass；二级降级走完整 `render` |
| `shared/src/test/java/com/atom/chat/diagnostics/ModeDCadenceTest.java` | 新增 11 项（本轮） |
| `shared/src/test/java/com/atom/chat/diagnostics/Truth3ModeTest.java` | 12 项，同步到合并后结构 |
| `build/mode-d-20261106/{parse_truth3_log.py,parse_prepass_log.py}` | 四档版解析（`MODES = ('A','B','C','D')`） |
| `build/mode-d-20261106/{prepare_snapshot.py,build_three.sh,collect_candidates.py,verify_wiring.py,verify_candidates.py}` | 快照 / 构建 / 收集 / 校验 |

`versions/atomlib.json` 工作区**一字未动**（仍 `0.1.0-beta`，只有快照内改成 `0.3.1-shadowcache`）。

## 4. 验证结果

### 4.1 构建与测试（三端隔离快照，`--offline --no-daemon`）

| 端 | 结论 | 测试 |
| --- | --- | --- |
| Fabric 1.21.1（JDK21） | BUILD SUCCESSFUL 3m03s | 1712 tests / 0 failures / 0 errors |
| Forge 1.20.1（JDK17） | BUILD SUCCESSFUL 2m39s | 1697 / 0 / 0 |
| NeoForge 1.21.1（JDK21，`-x downloadAssets`） | BUILD SUCCESSFUL 2m00s | 1697 / 0 / 0 |

关键测试类：`ModeDCadenceTest` 11、`Truth3ModeTest` 12、`PrepassProbeTest` 8、**`PanelShellWiringSourceTest` 4（此前红，现已绿）**、`NotificationRenderWiringTest` 7（断言 `full = true`、禁止 `BlurMotionGate`）。

`python tools/verify_targets.py` 通过；`verify_wiring.py` `VERIFY_WIRING_PASS: True (errors=0)`。

### 4.2 离屏 GL 探针（真实 shader + readback，RTX 5060）

`build/mode-d-20261106/probe-results.txt`：

| 项 | 结果 |
| --- | --- |
| A 档 golden（7 个动画进度 × 无裁剪/裁剪，共 14 帧）改动前后 | **逐像素 diff=0, max=0** |
| D 刷新帧 vs A 同输入 | **diff=0, max=0**（D 的刷新路径就是 A 的路径） |
| D 复用帧 vs 逐帧强制刷新（静态背景） | **diff=0, max=0** |
| D 复用帧（背景变化） | 旧代纹理本身 diff≈全幅、max=46；合成后 **面板外 diff=0**、面板内 max=7–46 —— **预期的时间分辨率差异**，非黑块 / 闪烁 / 采错背景 |
| 首次进入 D 的降级 | 与 A **diff=0**，降级后 `blurReusable=true` |
| 面板关闭后 MC 后续帧 | diff=0（3 个进度） |
| 反证：外部改 FBO / viewport / scissor、纹理重建、context loss | 全部 diff=0、镜像一致、不崩 |
| 变异：把「每帧刷新」装回去 | `refresh=600 reuse=0` → **RED**；恢复 → `refresh=50 reuse=550` → **GREEN** |

### 4.3 候选包拆包证明

`build/mode-d-20261106/candidates/`（文件名与现役一致，附 `sha256.txt`）：

```
58cbebbc…d38a6b1  atomchat-Fabric-1.21.1-v0.5.0.jar
14a0231c…bfc93a64  atomchat-Forge-1.20.1-v0.5.0.jar
cd131e6c…36741b17  atomchat-NeoForge-1.21.1-v0.5.0.jar
```

每份都含：P0-A（`capacityMode` / `nextPow2` / `extendEdge`）、P0-C（`errorCheckDue`；GL 查询 `glGetInteger 4→2`、`glGetIntegerv 3→1`、`glIsEnabled 4→3`）、P1（`GlGpuTimer` / `GpuTimerQuery` / `FrameProfile.page_phase`）、Truth3 四档（`Truth3Probe` / `frame_probe` / `mode_switch`）、`PrepassProbe`（`prepass_probe` / `unaccounted`）、本轮 D 档（`renderReuse` / `blurRefreshDue` / `blur_cadence`）。`verify_candidates.py` 全部 OK。

## 5. 每帧开销上界（静态推理 / 离屏，非真机测量）

- **D 档每帧**：`blurRefreshDue` = 2 次比较 + 1 次差值 + 几次自增，约十余条指令，**0 分配**；`cadenceLine()` 每帧 3 次比较即返回。
- **`debug` 每 600 帧一次**：环形窗口拷贝 + `Arrays.sort`（上界 6×10³ 次 long 比较）+ 6 次 `String.format` + 1 行日志（D 再多 1 行），摊还每帧 ≤10 次 long 比较、≤0.02 个短命对象、1/600 次日志 I/O。
- **内存**：固定 16 KiB + D 档 5 个标量，与帧数无关，无界增长为 0。
- **复用帧省下的**：整段 blit + 5 pass（真机 `passes = 0.164ms`，占 pre-pass 77%）。

## 6. 真机验收（一次启动，约 2 分钟）

1. 启动 `1.21.1-CCB`，进世界后**站着不动、保持同一视角**（唯一的环境控制）。
2. 打开面板，等它稳定（D 档只在面板 settled 时复用，动画期与 A 完全一致）。
3. 按 **F6** 循环 **A → B → C → D → A**，每档停 **≥20 秒**；建议至少走两轮。
4. B 无毛玻璃、C 面板消失、**D 与 A 看不出区别**（这是预期——静态场景下复用帧与刷新帧逐像素相同）。
5. 退出游戏说一声，我读同一份 `latest.log`。

**判定表**（`frame_probe` 帧间隔口径，真机待验）：

| 观察 | 结论 |
| --- | --- |
| D 的 FPS 接近 B、`>30ms` 长帧归零、p50 全距大幅收敛 | **每帧重算背景模糊就是稳态慢与骤降的共同根因**；Muonium 思路有效，可转产品方案 |
| D 介于 A 与 B 之间（约 355–371 FPS） | 限频生效但收益被「刷新帧仍很贵」吃掉 → 下一步砍 `passes` 本身（缩采 / 半径槽） |
| D ≈ A（无变化） | **30Hz 策略不是 Muonium 稳的原因** → 瓶颈另在他处，按你自己给的判据应当转向别处查 |
| D 比 A 更抖 | 复用路径本身有问题（纹理代际 / 状态残留），先查 `blur_cadence` 的 `reuse` 计数与 `passes` 是否归零 |

会同时验证 `blur_cadence mode=D hz=30.0 ...`：若 `eff_hz` 明显偏离 30、或 `reuse` 不占多数，说明限频没真正生效，先看这个再看帧率。

## 7. 遗留与未做的事

- **未部署、未真机验收、未 commit / push / 发布**；三端 JAR 仅在 `build/mode-d-20261106/candidates/`。
- 三个 Muonium 子项（半径槽 / 旧代借用 / 动画期实色）**只留配置桩，未实现**。
- 复用帧的「背景滞后」是**有意的时间分辨率取舍**（≤33ms），不是缺陷；但**动效背景（视频壁纸 / 场景大幅移动）下的观感本轮未验**，需你在 D 档里留意一眼。
- P1 的 `gpu_panel` 绝对值仍不可信（前轮已证伪），本档不依赖它。
- 长帧另有三处独立来源仍在，本档不修：首开字体同步 125ms、emoji 首次栅格化 153ms、字多设置 79.8ms。
