# 面板掉帧：接手基线、诊断增量与实机采样包

日期：2026-11-04。接手 `docs/superpowers/plans/2026-11-04-panel-performance-handoff.md`。
**状态：等待用户实机采样。根因未确定，未宣布任何性能改善。**
未启动游戏、未部署到实例、未 commit/push/tag/release。

## 1. 身份核对（改变了方案前提）

| 项 | 结果 |
| --- | --- |
| AC HEAD | `e92fdaf9370a4ed53c8edb56117eb97cbb18b8e1`，工作区 31 改 + 8 未跟踪，全部原地保留 |
| AtomLib HEAD | `56f1b9574f28ebacf4f9951064cdb8e0d3841752`，工作区干净 |
| AC 锁文件 | `versions/atomlib.json` 仍锁 `0.1.0-beta`，但用户未提交的字体改动调用 `FontSource.resource(String)`，该 API 只在 0.3.0 存在 → **工作区按原锁编译不过** |
| 实机 AtomLib JAR | `0d949b79…`，等于 `D:/AtomLib/build/rebuild-deploy-20261104` 的 0.3.0 候选；其 sources jar 内 `SkijaRenderBridge.java`/`GlStateScope.java` 哈希与当前 AtomLib 工作区源码**完全一致**（`002c648d…` / `e243ae7a…`） |
| 实机 AtomChat JAR | `2a038ec0…`，等于工作区 `platforms/1.21.1-fabric/build/libs` 产物 |
| handoff 提到的 final 候选 | AC `36fad462…` / AtomLib `a1da0ff0…`，**均未在实机运行**；final AtomLib 的 `SkijaRenderBridge.java` 哈希为 `59e4fb2b…`，与实机版不同 |

结论：分析桥接必须以 `rebuild-deploy-20261104` 版本为准，不能照搬 final 候选的实现事实。
`javap` 对实机 AtomLib JAR 的方法集与本地源码一致，桥接没有公开内部计时回调。

**构建前置**：AC 工作区用 beta3 输入编译失败（`FontManager.java:76` 找不到 `FontSource.resource`）。
因此建立隔离源码快照 `build/panel-perf-20261104/source/`（脚本 `prepare_source.py`），
仅改快照内 `versions/atomlib.json` 为 `0.3.0` + 三端真实输入哈希，工作区锁文件不动。
快照排除了 `.work/`（历史验证源码副本会让 `AvatarReadbackWiringTest` 源码守卫误判重复类）。
NeoForge 按既有做法补入 `minecraft_assets.properties`（SHA `015d0bc7…`）。

## 2. 本轮完成项

- **任务 1**：HEAD/工作区/锁/关键源码哈希、四个 JAR 身份、javap、`FrameProfileJfrTest`(3) + `FrameSegmentsJfrTest`(1) 基线全绿、`verify_profile_wiring.py` official+fabric PASS。探针的 bridge 是替身，不证明真实 GL 行为。
- **任务 2（部分）**：`PanelSegments` 事件扩展为单个 frameId 携带全部 8 个阶段 + `panelNanos` + 单调起止 + 资源计数；未测阶段显式 `-1`（不是 0）。`Navigation` 增加 `monotonicNanos`/`actionId`。新增 `com.atomchat.ScreenLifecycle`，`AtomChatConfig.reload()` 已接入真实生产调用点（三端屏幕打开时共用），覆盖同步配置读取。
- **任务 3（部分）**：LWJGL 3.3.3 timer-query 签名/常量探针保存于 `build/panel-perf-20261104/lwjgl-query-api.txt`（`ARBTimerQuery.GL_TIME_ELAPSED=35007`、`GL15C.glGenQueries/glDeleteQueries/glGetQueryObjecti`、`GL33C.glGetQueryObjectui64`、`GLCapabilities.GL_ARB_timer_query`）。`GpuTimerQuery` 实现有界非阻塞环形队列（ready/未 ready/环满/丢弃/回收/上下文失效/GL 异常/卡住队首超时回收），12 项单测覆盖。**未接入渲染路径**。
- **任务 4**：三端候选 + Fabric 配套 AtomLib、完整哈希与回退说明；`atomchat-panel.jfc`（自定义事件零阈值 + 执行栈/文件 IO/分配/GC）；`capture.ps1`；`scenario-script.md` 复现脚本。JFR 命令在测试 JVM 实测：`jcmd JFR.start/dump/stop` + `jfr print` 全 exit 0，30 个 `com.atomchat.UiFrame` 事件全部落盘，`RESULT=PASS`（证据 `jfr-probe-verification.txt`）。确认 `default`/`profile` 默认配置不启用自定义事件。
- **验证门**：wiring 探针 PASS；三端全量 `build` 全绿；`verify_targets.py` 6 项通过；三端 `verify_jars.py --profile atomlib-consumer` 全部通过。独立 code-reviewer 复核结论"无阻塞问题"，重要项已修正 GPU 异常/停滞、失败帧测试语义、nanoTime 断言稳定性并补测。

## 3. 数字

| 目标 | tests | failures | errors | skipped |
| --- | ---: | ---: | ---: | ---: |
| Forge 1.20.1 | 1636 | 0 | 0 | 5 |
| Fabric 1.21.1 | 1651 | 0 | 0 | 5 |
| NeoForge 1.21.1 | 1636 | 0 | 0 | 5 |

定向诊断测试 16 项全绿（TDD：先 `compileTestJava FAILED` 红，实现后绿）。
完整清单 `build/panel-perf-20261104/delivery-manifest.json`；日志在 `build/panel-perf-20261104/*.log`。

最终产物（`build/panel-perf-20261104/artifacts/`）：

| 文件 | SHA-256 |
| --- | --- |
| `atomchat-Fabric-1.21.1-v0.5.0.jar` | `d04e913270c124b804672bfddd21e44503d22585a84c5d0a3f3e97528a995114` |
| `atomchat-Forge-1.20.1-v0.5.0.jar` | `053376090deb7a28ea6a09669af5c3c1ed8679250d135c7d802b1e60ea164d8b` |
| `atomchat-NeoForge-1.21.1-v0.5.0.jar` | `a7ba3abaa4d77f9477b43bf9c70b0eb341b76f07eef229e7d5d398b9ae791b4a` |
| `atomlib-Fabric-1.21.1-v0.3.0.jar` | `0d949b7949d4a221e47068719ea8c8e3b36193b56db2c78689efd76c63aed5f2`（= 实机现有，无需更换） |

## 4. 未完成项（逐条如实列出）

1. **无实机采样**：关面板基线、逐帧切页、CPU 等待、GPU 完成时间均未确认。根因、对照组、样本量、帧时间变化、诊断开销全部**待采样**。
2. **AtomLib 桥接内部未分解**：`GlStateScope.capture`、上下文/surface 准备、`resetGLAll`、`flushAndSubmit`、scope 恢复仍是 `BRIDGE_ENTER`/`BRIDGE_EXIT` 两个合并桶。按 handoff 兜底条款保留并标注"未分解"；不得相减或据此归因。需要替换运行库时再改 AtomLib + 生成单独诊断候选。
3. **GPU 计时未接线**：只有纯逻辑队列 + 单测，无真实离屏 GL 测试、无游戏内可用性证据。本包不产出 GPU 耗时。
4. **屏幕生命周期只接入 `CONFIG_RELOAD`**：打开触发、构造、init、首帧只有事件 API，未接真实调用点；用户操作到首帧的延迟仍不可算。
5. **完整游戏帧时间**需外部帧时间工具；JFR 内是渲染线程墙钟与面板帧间隔，不是游戏整帧/FPS。
6. **code-reviewer 剩余重要项**：Recorder 状态机跨线程无显式守卫/计数；`ScreenLifecycle.stage` token 未做配对校验；`reload()` 的 `success` 因 `load()` 内部吞异常而恒 true（配置损坏回退默认值不可见）。均为加固项，不影响本轮已验证的配对与关闭路径。
7. 未做字体/亮度/点阵观感的实机验收、未做 A/B 交替测量、未做修复实现。

## 5. 下一步

用户按 `docs/verification/2026-11-04-panel-sampling-kit/scenario-script.md` 自行启动游戏并采样；
agent 读回 JFR/日志/帧时间后执行 handoff 任务 5 的校验、假设排序、A/B 与修复。
在此之前停在"等待采样"，不宣布掉帧已修复。

## 6. 第一次实机采样（JFR 丢失，日志数据保留）

- 身份已确认：`[20:05:00] AtomChat build 0.5.0 | Fabric 0.16.9 / minecraft 1.21.1 | atomchat-Fabric-1.21.1-v0.5.0.jar | sha256:d04e913270c1 | git e92fdaf+dirty`，`environment summary (debug)` 存在。新诊断包与 debug 均生效。
- **JFR 丢失**：我用 JDK 21 的 `jcmd 30592 JFR.start` 启动录制时未带 `filename`/`dumponexit`；用户结束并关闭游戏后进程消失，内存中的录制未落盘，随后 dump/stop 均报 `AttachNotSupportedException: 拒绝访问`。**这是 agent 的操作失误**。修复：`one-click.ps1` 的 start 现在带 `filename=` + `dumponexit=true`，结束时只做 `JFR.stop`；游戏意外退出也会自动写盘。
- 附带发现：PCL 用自带 JDK 25.0.1 且带 `-XX:+PerfDisableSharedMem`，`jcmd -l` 看不到游戏进程；脚本改为用 WMI 找进程、并优先用游戏自带 JDK 目录的 `jcmd.exe`/`jfr.exe`。
- 日志救回 31 个 600 帧滚动窗口（20:07:15–20:11:33），解析结果 `build/panel-perf-20261104/log-profile.json`（脚本 `parse_log_profile.py`）。稳定态最后一块（ms）：

| 段 | p50 | p95 |
| --- | ---: | ---: |
| panel（整段） | 3.880 | 5.490 |
| PRE_UI（含 BLUR 0.168） | 0.169 | 0.346 |
| DRAW | 1.105 | 1.506 |
| ├ DOTS（嵌套） | 0.943 | 1.134 |
| └ LIST（嵌套） | 0.283 | 0.384 |
| BRIDGE_ENTER（合并桶） | 2.170 | 2.501 |
| BRIDGE_EXIT（合并桶） | 0.337 | 1.166 |
| chat_interval（面板帧间隔） | 5.290 | 6.530 |

- 时间序列：`20:09:30 → 20:10:12` panel p50 由 4.06 升到 5.65、p95 由 7.63 升到 13.32，之后回落到 3.8 / 5.5；同段 `BRIDGE_ENTER` p50 几乎不变（2.166 → 2.144）。窗口内出现 302.93 ms 一次性长帧，随后随窗口滚出消失。

**观察（不是根因）**：稳定态下 `BRIDGE_ENTER` 是数值最大的合并桶；中段整体劣化时它几乎不动，提示该次劣化更可能来自绘制回调/页面内容或面板外因素，而非桥接状态捕获。但这是 600 帧混合窗口的独立分位数，**不能相减、不能当帧内耗时、不能证明 CPU 忙**，也不能把 DOTS 当作已证实主因。无 JFR 逐帧事件、无关面板基线、无外部帧时间、无 GPU 计时，故不宣布任何根因。

**需要重录一次 JFR**：重跑已修好的 `one-click.ps1`（会自动跳过已装好的包与 debug），进游戏按同样场景操作约 2 分钟，回来按回车即可；即使直接关游戏，`dumponexit` 也会保存 `.jfr`。拿到逐帧事件后才能做任务 5 的配对校验、动作对齐、假设排序与 A/B。

## 7. 第二次实机采样：逐帧数据（有效）

`build/panel-perf-20261104/samples/panel-auto-20261104-202119.jfr`，24,152,112 字节，146 秒，JDK 25.0.1。
无人值守脚本 `auto-record.ps1` 用 `filename=` + `dumponexit=true` 落盘，游戏退出后文件完整。
导出 `events.json`（`jfr print --json`）、`gc-events.json`；解析脚本 `analyze_jfr.py` / `analyze_supplement.py`；结果 `analysis.json` / `supplement.json`。

**量具自证通过**：9699 `UiFrame` + 9699 `PanelSegments`，全部非零 frameId，一一配对，0 缺失、0 重复、0 串号；37 `Navigation` 全部 `frameId=0`（帧外），11 `ScreenLifecycle` 全部 `frameId=0`。surface 分布 CHAT 5307 / OTHER 4392。

**稳定态同帧分位数**（去掉前 100 帧，9599 帧，单位 ms；嵌套段不可相加）：

| 段 | p50 | p95 | p99 | max |
| --- | ---: | ---: | ---: | ---: |
| panel（整段） | 3.989 | 7.758 | 11.079 | 64.455 |
| PRE_UI（含 BLUR 0.196） | 0.200 | 0.381 | 0.507 | 1.576 |
| BRIDGE_ENTER（合并桶） | 2.123 | 2.447 | 2.703 | 14.084 |
| DRAW | 1.153 | 2.947 | 6.056 | 46.919 |
| ├ DOTS | 0.922 | 1.093 | 1.229 | 6.761 |
| └ LIST | 0.290 | 0.345 | 0.426 | 1.180 |
| BRIDGE_EXIT（合并桶） | 0.373 | 2.666 | 3.875 | 56.006 |

`UiFrame.renderNanos` p50/p95/p99 = 4.000 / 7.743 / 11.086 ms；`frameIntervalNanos` = 5.635 / 10.550 / 14.494 ms（**面板帧间隔，不是游戏整帧时间**）。

**长帧归因**：95 帧超过 p99（11.079 ms）。按同帧最大段分类：DRAW 主导 75 帧、BRIDGE_EXIT 主导 18 帧、BRIDGE_ENTER 主导 2 帧。全部 9599 帧里 BRIDGE_ENTER 是 8322 帧（87%）的最大段且极稳定 —— 它是**常驻固定开销**，不是长尾来源。最长帧 105.9 ms 是首帧（bridgeEnter 19.1 / draw 61.5 / bridgeExit 21.4），属冷启动，已从稳定态剔除。

**点阵假设被证伪**：top-20 长帧中 DOTS p50 = 0.975 ms、max = 1.678 ms，与稳定态 p50 0.922 几乎一致。点阵既不是常驻主因，也不是长帧主因。

**切页动作对齐**（动作后第一个面板帧，ms）：

| 动作 | n | p50 | p95 | max |
| --- | ---: | ---: | ---: | ---: |
| pop | 10 | 3.816 | 13.640 | 13.640 |
| root | 17 | 5.031 | 50.219 | 50.219 |
| push | 10 | 7.612 | 34.968 | 34.968 |
| 基线（全部稳定帧） | 9599 | 3.989 | 7.758 | — |

`push` 后首帧 p50 约为基线 2 倍；`root` 有 50.2 ms 极值；`pop` 基本无影响。重复 push 到同一 SETTINGS_SECTION 的首帧为 24.2 / 16.8 / 8.5 / 35.0 ms，不呈单调衰减 —— 不支持"仅首次冷加载"，更像**每次切页重建页面内容**。

**GC / 锁等待不是原因**：95 个长帧中，±50 ms 内靠近 GC 的只有 3 帧，靠近 `JavaMonitorEnter` 的 0 帧（±200 ms 也仅 11 帧）。
**CONFIG_RELOAD**：11 次，每次 6.7–11.3 ms 同步配置读取，属于"打开面板一次性延迟"，不参与逐帧掉帧。

**可证伪假设排序（尚未验证，不是根因结论）**：

| # | 假设 | 支持证据 | 证伪方法 |
| --- | --- | --- | --- |
| H1 | 切页 push/root 后首帧的 DRAW 尖峰来自页面内容重建（布局/字形/列表） | 75/95 长帧 DRAW 主导；push 首帧 2× 基线；DOTS 不变；重复 push 不衰减 | 同页面交替 A/B：预构建/缓存页面内容后，若首帧尖峰消失则成立；若不变则证伪 |
| H2 | BRIDGE_EXIT 尖峰是 `flushAndSubmit` / `GlStateScope.close` 的驱动等待或状态恢复 | 18/95 长帧主导，max 56 ms；CPU 墙钟高不等于 GPU 忙 | 需 GPU timer query 与桥接内部细分；若 GPU 时间不随该段增长则证伪 |
| H3 | 稳定掉帧的 2.12 ms BRIDGE_ENTER 来自 `GlStateScope.capture` 的大量同步 `glGet*` | 87% 帧最大段、p99 仅 2.703 ms，形态极稳定 | 需替换 AtomLib 运行库细分 capture/准备；若 capture 单独 <0.5 ms 则证伪 |
| H4 | 点阵是瓶颈 | 无 | **已证伪**（长帧中 DOTS 不变） |
| H5 | GC / 锁等待导致长帧 | 无 | **已证伪**（3/95、0/95） |

**仍未完成**：关面板基线、外部游戏帧时间、GPU 计时、桥接内部阶段细分、同场景交替 A/B、多轮预热、修复实现与回归。按 handoff「仅对已证实根因实现修复」，本轮**不改产品代码**，不宣布掉帧已修复。

**下一步（用户）**：复用 `auto-record.ps1` 再采一轮，重点补 ① 关面板 30 秒基线 ② 固定两页交替 20 次并记录动作时刻 ③ 同一页面连续 push 20 次看首帧是否衰减 ④ 外部帧时间工具记录完整游戏帧。拿到后即可对 H1 做交替 A/B。

## 8. 用户外部 FPS 观测与 JFR 的对照

用户补充（游戏内 FPS 显示）：**关面板约 400 fps；一打开面板掉到 150–170 fps；切页掉更多**。
JFR 里**没有游戏整帧时间**（未接入外部帧时间工具），所以 400 fps 那段没有被直接记录；
但 `UiFrame.frameIntervalNanos`（同一页面相邻面板帧的开始间隔）可间接对照。

`analyze_density.py` 结果：9699 个 UiFrame 分布在 82 秒内，平均 118.3 帧/秒，
每秒计数 0–195，中位 138.5；**8 秒为 0 帧**（面板关闭/无面板绘制），
安静段 `+44–45s`、`+67–68s`。高密度段按秒换算：

| 时段（相对秒） | 面板帧间隔 p50 | 换算面板 fps | p95 换算 |
| --- | ---: | ---: | ---: |
| 0–14（刚开面板） | 6.813 ms | 147 | 90 |
| 17–20 | 7.352 ms | 136 | 77 |
| 25–36 | 5.650 ms | 177 | 110 |
| 38–39 / 42–43 / 46–48 | 5.06–5.28 ms | 189–198 | 142–153 |

与用户外部读数 150–170 fps **量级吻合**；且呈现"刚开面板 136–147 fps → 稳定期 177–198 fps"的预热趋势，
切页密集秒（如 `+11s`）掉到 104 帧/秒，与"切页掉更多"一致。

**量级对照（跨来源，仅一致性检查，不是因果证明）**：

| 来源 | 值 |
| --- | --- |
| 关面板（用户外部） | 400 fps ≈ 2.50 ms/帧 |
| 开面板（用户外部） | 150–170 fps ≈ 5.88–6.67 ms/帧 |
| 增量 | ≈ 3.38–4.17 ms |
| 本包同帧 `panelNanos` p50 | 3.989 ms |
| 本包 `frameIntervalNanos` p50 | 5.635 ms ≈ 177 面板 fps |

增量与面板整段墙钟 p50 几乎 1:1，支持"面板绘制成本基本转嫁到帧时间"这一**待验证**判断。
但两个时钟不同源、面板帧间隔不等于游戏整帧时间，且缺关面板基线与交替 A/B，
不得据此宣布根因或性能改善。关面板时段因为根本不产生 `UiFrame` 事件，量具天然测不到 ——
这正是下一轮必须补「关面板 30 秒 + 外部帧时间」的原因。

## 9. 第三轮采样：两轮复现与切页假设的修正

`samples/panel-auto-20261104-203101.jfr`，22,388,444 字节，181 秒。3918 `UiFrame` + 3918 `PanelSegments`
（全配对、零缺失、零重复），30 `Navigation`，2 `ScreenLifecycle`。事件导出 `events-run3.json`、
`gc-run3.json`、`exec-run3.json`；分析脚本 `compare_runs.py`（结果 `compare-runs.json`）、`analyze_stacks.py`。

**关面板基线**：86 秒跨度里 59 秒为 0 面板帧、仅 23 秒高密度，用户确实做了长时间关面板静置。
但 JFR 只在 `drawPanel` 时发事件，**关面板的游戏帧时间测不到**；用户口述 400 fps 未附外部帧时间文件，
故该数字仍只是外部参照，未进入统计。

**两轮复现（同帧，ms）**：

| 段 | run2 p50 / p95 / p99 | run3 p50 / p95 / p99 |
| --- | --- | --- |
| panel | 3.989 / 7.758 / 11.079 | 4.032 / 6.310 / 9.934 |
| BRIDGE_ENTER | 2.123 / 2.447 / 2.703 | 2.149 / 2.493 / 2.756 |
| DRAW | 1.153 / 2.947 / 6.056 | 1.161 / 2.224 / 3.606 |
| DOTS | 0.922 / 1.093 / 1.229 | 0.876 / 1.079 / 1.205 |
| BRIDGE_EXIT | 0.373 / 2.666 / 3.875 | 0.378 / 2.109 / 3.862 |
| panel 帧间隔 p50 | 5.633 | 5.608 |

差异均在 1–2% 内，量具与读数稳定。长帧：run2 95 帧（DRAW 75 / BRIDGE_EXIT 18 / BRIDGE_ENTER 2），
run3 38 帧（DRAW 22 / BRIDGE_EXIT 16 / BRIDGE_ENTER 0），**再次确认 BRIDGE_ENTER 不是长尾来源**。

**H1 被修正（本轮最重要证据）** —— 同一页面连续切页的首帧（ms）：

| 页面 | 连续 push/root 的首帧序列 |
| --- | --- |
| `WORLD_CHAT`（run3，9 次） | 4.03, 3.23, 3.33, 3.66, 3.14, 3.37, 3.47, 3.55, 3.59 |
| `SETTINGS_SECTION`（run3，3 次） | 28.08 → 20.75 → 8.66 |
| `PROFILE`（run3，首次 root） | 57.69 → 后续 3.95 |
| `SETTINGS_SECTION`（run2，7 次） | 24.25, 16.83, 8.29, 34.97, 7.61, 12.41, 7.18 |

结论：切页慢**不是"每次切页重建页面内容"**，而是**特定页面首次渲染的冷初始化**；
`WORLD_CHAT ↔ CHAT_LIST` 交替 9 次几乎无尖峰（3.1–4.0 ms），
`SETTINGS_SECTION` / `PROFILE` 首次极贵且重复访问显著衰减。
run3 的 `Navigation` 分组也印证：`push` p50 3.57（多为便宜的 WORLD_CHAT）vs run2 的 7.95（多为 SETTINGS_SECTION）。

**CPU 栈（`jdk.ExecutionSample`，10 ms 采样，3195 个样本）**：面板开 459 / 关 2736 样本。
面板开时段栈顶主要是 `Arrays.fill`、遮挡剔除、EMF 动画、Minecraft 类；
`SkijaRenderBridge` 6、`GlStateScope` 4、`glGet` 3、`GL15C` 1 次命中，**没有 AtomChat/Skija 独占热点**。
首次 `SETTINGS_SECTION` push / `PROFILE` root 的 ±1.5 s 窗口各只有 50 个样本，分辨率不足。
因此**无法判定 BRIDGE_ENTER 是 CPU 忙还是同步 GL 查询等待**，也无法定位冷初始化读了什么；
这是墙钟采样而非纯 CPU 时间，不得据此断言。需要 AtomLib 桥接内部细分与更细的分配/文件 I/O 对齐。

**修正后的假设排序**：

| # | 假设 | 状态 |
| --- | --- | --- |
| H1' | 切页长帧 = 特定页面（SETTINGS_SECTION / PROFILE）**首次渲染的冷初始化**，重复访问衰减 | 两轮数据强支持；优化方向为预热/缓存布局与资源，尚未做代码 A/B |
| H2 | 常驻 2.1 ms BRIDGE_ENTER = `GlStateScope.capture` 的 `glGet*` / 上下文准备 / `resetGLAll` | 数值稳定但内部未分解，栈采样不足以判定 CPU 忙还是驱动同步；需替换运行库细分 |
| H3 | BRIDGE_EXIT 偶发尖峰（run2 max 56 ms，run3 max 9.4 ms）与 flush/驱动同步相关 | 待 GPU timer query |
| H4 | 点阵主因 / GC / 锁等待 / "所有切页都慢" | **已证伪** |

**仍是等待状态**：未做代码 A/B、未实现修复、未做回归、未取得外部游戏帧时间与 GPU 计时，
故不宣布掉帧已修复。下一轮不需要用户再采样：可从现有 JFR 的分配/文件 I/O 事件按首次切页时间窗口
定位冷加载内容，再设计只改一个因素的预热 A/B。

## 10. 用户澄清与优先级修正：主问题是常驻降帧

用户明确：**打开面板进的就是聊天页，照样掉帧；只要面板开着，FPS 上限就卡在 170–180；
关掉面板才能回到 400（本机锁帧上限）**。因此之前把重点放在"切页尖峰"是偏了：
切页尖峰只是叠加在常驻降帧之上的偶发更低值，**用户实际感受的主问题是开面板本身**。

**外部读数与量具完全对上**：两轮 `frameIntervalNanos` p50 = 5.633 / 5.608 ms
→ 1000/5.63 ≈ **177.6 面板 fps**，正好落在用户看到的 170–180 上限；
关面板 400 fps ≈ 2.50 ms/帧，增量 3.1–3.4 ms，与同帧 `panelNanos` p50 ≈ 4.0 ms 同量级
（跨时钟，仅一致性检查）。面板开着时帧率被这 4 ms 封顶，与页面内容无关。

**这 4 ms 的同帧构成（嵌套，不可相加）**：

| 段 | run2 / run3 p50 (ms) | 说明 |
| --- | --- | --- |
| BRIDGE_ENTER | 2.123 / 2.149 | 桥接前段合并桶，每帧必做，极稳定 |
| DRAW | 1.153 / 1.161 | 绘制回调，其中 DOTS 0.922 / 0.876 |
| BRIDGE_EXIT | 0.373 / 0.378 | flush/submit + 状态恢复 |
| PRE_UI | 0.200 | 含 BLUR 0.196 |

**两处表述澄清**：
1. 「聊天页切页不卡」只表示 `WORLD_CHAT ↔ CHAT_LIST` 的**切换动作那一帧**没有额外尖峰（3.1–4.0 ms），
   不代表面板开着不卡；面板开着期间一直是 170–180。
2. 「点阵排除」只表示点阵不是**切页长帧**的原因；它仍占常驻 4 ms 中约 0.9 ms（约 1/4），
   属于稳定成本，不是可以无视的项。

**代码层依据（非耗时测量）**：`D:/AtomLib/.../GlStateScope.java` 的 `capture()` 有 71 个静态 GL 调用点
（其中 44 个 `glGetInteger`）和 11 个循环；`close()` 有 41 个调用点、9 个循环。
按典型 GL 上限展开 —— `MAX_COMBINED_TEXTURE_IMAGE_UNITS≈32` × `TEXTURE_BINDINGS=10`、
`MAX_UNIFORM_BUFFER_BINDINGS≈36`、`MAX_DRAW_BUFFERS≈8`、`ENABLES=18`、`PIXEL_STORES=16` ——
**每帧约 550–600 次同步 GL 状态查询 + 400 次以上状态恢复调用**，且 `SkijaRenderBridge.drawFrame`
每帧还调用 `context.resetGLAll()`。这是 2.1 ms 固定成本的可解释来源，
但**尚未经运行时细分证实其内部占比**；栈采样（10 ms）分辨率不足，不能据此断言是 CPU 忙还是驱动同步等待。

**修正后的优先级**：

| 级别 | 现象 | 疑似来源 | 状态 |
| --- | --- | --- | --- |
| P0 | 开面板常驻 400→170–180 | 每帧 4 ms，其中桥接状态全量捕获/恢复 2.1 ms、点阵 0.9 ms | 数值确证，内部未细分 |
| P1 | 切页偶发更低 | 特定页面（SETTINGS_SECTION / PROFILE）首次渲染冷初始化 | 两轮数据强支持 |
| P2 | 偶发 BRIDGE_EXIT 尖峰 | flush / 驱动同步 | 待 GPU 计时 |

**下一步（不需要用户再采样）**：做 AtomLib 桥接内部细分诊断（给 `capture` / 上下文准备 /
`resetGLAll` / `flushAndSubmit` / `close` 各打一个带同一 frameId 的 JFR 事件，生成独立诊断候选，
不替换生产资源），确认 2.1 ms 的分布后再设计只改一个因素的优化 A/B（状态缓存/增量捕获）。
在此之前不改产品代码、不宣布掉帧已修复。

## 11. 责任归属、Skija 高性能前提与改动清单

### 11.1 是 AC 还是 Lib

按同帧段归属（段数值不可相加；`panelNanos` 4.0 ms 是完整区间）：

| 归属 | 段 | run2 / run3 p50 (ms) |
| --- | --- | --- |
| **AtomLib 桥接** | `BRIDGE_ENTER`（`GlStateScope.capture` + `normalizePixelTransfer` + `ensureContext` + `resetGLAll` + `rebuild` 检查 + `session.paint` 进入） | 2.123 / 2.149 |
| **AtomLib 桥接** | `BRIDGE_EXIT`（`flushAndSubmit(surface)` + `GlStateScope.close`） | 0.373 / 0.378 |
| AC 绘制内容 | `DRAW`（含 `DOTS` 0.922 / 0.876、`LIST` 0.29） | 1.153 / 1.161 |
| AC 预通道 | `PRE_UI`（含 BLUR 0.196） | 0.200 |

**结论：主要是 AtomLib 桥接的状态管理问题（约 2/3 常驻成本），AC 绘制内容占约 1/3（点阵是大头）。**
不是 Skija/Skia 本身慢，也不是 GPU 算力不够 —— 是当前用法违反了 Skia GPU 后端的高性能前提。
`BRIDGE_ENTER` 仍是合并桶，内部各步占比未经运行时细分，故以上是**归属判断**，不是耗时分解结论。

### 11.2 Skija 为什么能"既精细又高性能"

精细来自 GPU 光栅化：抗锯齿、子像素字形、路径/RRect、MaskFilter/ImageFilter 模糊、渐变与混合都在 GPU 上算。
高性能来自六条工程前提（Skia 官方 skia-discuss：Brian Salomon / Brian Osman，2021-04；
Skiko/Skija `DirectContext` 源码 0.143.18）：

1. **复用**：`GrDirectContext` 生命周期绑定 GL 上下文，只建一次（"You'll definitely want to reuse the GrDirectContext across frames"）；Surface/BackendRenderTarget 尺寸不变不重建；Paint/Typeface/纹理/GPU 资源缓存常驻。
2. **批处理**：Canvas 命令先记录，flush 时合并成少量 GPU draw；**每帧只 flush 一次**，多次 `flushAndSubmit` 会打断命令合并。
3. **不打断状态缓存**：只有外部确实改了 GL 状态时才 `resetContext`，且用细粒度状态位；官方明确 "clearing the cache frequently is relatively expensive"。
4. **零 CPU↔GPU 往返**：不 `glGet*`、不 `readPixels`、不 `glFinish`、不 `syncCpu`（GL 上 submit 几乎是 no-op）。
5. **合并重复图元**：小图元用纹理/atlas/Picture 一次画完，不要每帧发几万条 draw 命令。
6. **预热**：首次着色器编译/纹理上传放到启动期，避免首次显示尖峰。

### 11.3 我们违反了什么（代码实证）

| # | 位置 | 问题 | 证据 |
| --- | --- | --- | --- |
| V1 | `SkijaRenderBridge.drawFrame:92` | **每帧 `context.resetGLAll()`** = `reset(BackendState.GL_ALL=65535)`，强制 Skia 丢弃全部缓存 GL 状态 | Skija 源码 `resetGLAll(){return reset(GL_ALL);}`；官方称频繁清缓存昂贵 |
| V2 | `GlStateScope.capture()/close()` | **每帧全量状态快照与恢复**：静态 71/41 个 GL 调用点、11/9 个循环；按 `MAX_COMBINED_TEXTURE_IMAGE_UNITS≈32 × TEXTURE_BINDINGS=10`、`MAX_UNIFORM_BUFFER_BINDINGS≈36`、`MAX_DRAW_BUFFERS≈8` 展开约 **550–600 次 `glGet*` + 400+ 次恢复调用** | 源码静态统计，非运行时计数 |
| V3 | `DotTexture.drawField/drawEdge` | **点阵逐点 `canvas.drawCircle`**：`gap=s(4)`，`drawField` 最多 `512×128=65536` 次、`drawEdge` 最多 `512×9=4608` 次；典型面板每帧几千~上万次 | `shared/.../DotTexture.java:38-49, 89-105` |
| V4 | `SkiaDraw` + `DotTexture` | `RRect.makeXYWH`/`Rect.makeXYWH` 每次新建（types 0.1.1 字段 final 不可复用）；AC 有 **212 处** `SkiaDraw.drawRoundedRect` 调用点 | 源码注释 + `grep` 统计 |
| V5 | `SkijaRenderBridge.drawFrame:94` | 每帧 `surface.notifyContentWillChange(RETAIN)`，可能使表面内容缓存失效 | 待验证 |

**已经做对的**（不要再"优化"）：一个 DirectContext 复用；Surface/BackendRenderTarget 尺寸不变不重建；
`SkiaDraw` 已有 `SHARED_PAINT` / `SHADOW_PAINT` / `BLUR_FILTERS` 缓存、`DotTexture` 已有静态 `DOT`/`GLOW`/`BLUR`；
每帧一次 `flushAndSubmit(surface)` 且 **`syncCpu=false`（非阻塞，已核实源码 121-129 行）**；字形/图标资源缓存。
注意 `FrameProfile` 类注释里"SkiaDraw 每个 helper 每次调用现造原生对象"是**过时描述**，与当前源码不符，应更正，别照它去改。

### 11.4 改动清单（一次只改一个因素，全部需 A/B）

| 优先级 | 仓 | 改动 | 预期 | 验证 |
| --- | --- | --- | --- | --- |
| P0-1 | AtomLib | `resetGLAll()` → `reset(GL_TEXTURE_BINDING, GL_RENDER_TARGET, GL_VIEW, GL_BLEND, GL_PROGRAM, GL_PIXEL_STORE, GL_VERTEX…)`，只重置外部真正改动的位；进一步验证能否完全不 reset | 去掉每帧全量状态缓存失效 | golden frame 像素对比 + 同场景交替 A/B 的 `panelNanos` p50/p95/p99 |
| P0-2 | AtomLib | `GlStateScope` 增量化：缓存不变的 `MAX_*` 查询、只在上下文/尺寸变化时重读；只捕获/恢复 Skia 会改的子集 | 550–600 次查询降到几十次 | 同上 + GL 状态/资源生命周期断言 |
| P0-3 | AC | `DotTexture` 点阵预渲染为一张小 Image/Picture/atlas，尺寸变化时重建，每帧一次 `drawImage` | `DOTS` 0.9 ms → 接近 0 | golden frame + 亮/暗背景截图逐像素比对 |
| P1 | AC | 缓存常用 `RRect`/`Rect` 几何（或升级 types）；收敛 212 处 helper 的每帧分配 | 降低 DRAW 与 `alloc` | 分配计数 + A/B |
| P1 | AtomLib | 评估 `notifyContentWillChange(RETAIN)` 是否只在尺寸变化时调用 | 减少表面内容失效 | golden frame + A/B |
| P2 | AC | 预热 `SETTINGS_SECTION` / `PROFILE` 首次渲染资源 | 削切页尖峰 | 交替 A/B（H1'） |

硬约束：保留字体粗细、背景、点阵观感、导航打断与资源生命周期；不改变画质/动画/效果来冒充性能修复；
每次只改一个因素，同场景交替次序、预热、多轮，报告整段 p50/p95/p99 与分配；
未取得用户实机复测前不宣称 FPS 提升。P0-1/P0-2 属跨仓改动，需按 handoff 用隔离 Maven 仓与明确锁文件生成
独立诊断/对照候选，不覆盖已发布版本、不替换生产资源。
