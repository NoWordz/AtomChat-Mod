# 面板纯优化 · 执行结果（阶段 1–3 收口）

**日期**：2026-11-04 23:0x GMT+8
**状态：`WAITING_FOR_USER_IN_GAME_AB`** —— 未宣布掉帧已修复、未宣布 FPS 提升、未宣布达标。达标判定权归用户。
**依据锚点**：`docs/superpowers/plans/2026-11-04-panel-optimization.md`（Q1–Q7 全部用户拍板）。

---

## 0. 一句话结论（给赶时间的人）

**这轮纯优化没能拿到性能收益，而且拿到了一个有价值的否定证据**：
AtomLib 桥接那条「细粒度 reset」的路在真实 GL 下被证明不安全，补齐位之后就等于没优化；
`GlStateScope` 的增量化因为安全约束只做到「省 5 次 `glGet*`」（约 1%），没碰到真正的成本；
只剩 AC 点阵缓存，但它在离屏「缓存不命中」场景反而更慢。
**因此本轮候选从机制上不具备达到 Q2 硬线（`panelNanos` p50 ≤2.0ms / 外部 FPS ≥250）的潜力。**
用户要的「开面板不骤降、符合 Skija 高性能」，需要方案 Q2 里单列的**第二阶段（UI 层分层缓存）**，或先做桥接内部细分后再做状态子集裁剪。

---

## 1. 执行链（Q6：顺序两个子代理实例，同一时刻只活跃一个）

| 阶段 | 实例 | 做了什么 | 结论 |
| --- | --- | --- | --- |
| 1 | 实现 agent | P0-1/2/3 + 两个默认关闭开关 + TDD + 隔离仓 + 三端全量 build + golden + 离屏 A/B + 三端部署回执 | 交付 6 个候选、状态 `WAITING_FOR_USER_IN_GAME_AB` |
| 2 | 独立 reviewer（新实例，不复用实现上下文） | 只读复核 9 个重点 | **无阻塞问题**，有条件进入实机 A/B；提 I-1~I-4 / II-1~II-6 |
| 3a | resume 实现 agent | 按清单修正、补真实 GL 证据、重跑全部验证链、重出候选、追加第二轮部署回执 | I-1 结论翻转（见 §2） |
| 3b | resume reviewer（第二次复核） | 复核 8 条声明 + 重跑链 + 遗留问题 | **无阻塞问题**；总体判断「有条件，只测 AC 点阵支」 |
| 3c | 主 agent | 归档真实 GREEN XML、修正 15 处报告措辞/证据引用、写本报告、更新锚点 | 文档与磁盘一致 |

主 agent 独立核实过：`BackendState` 有 **11 个有效位**，`FAST_RESET_STATES` 初版只有 7 位（漏 MSAA/Stencil/FixedFunction/Misc）；六个候选 JAR 与隔离仓哈希逐一吻合；AC 工作区 `versions/atomlib.json` `git diff` 为空；用户 31 改 + 8 未跟踪原样保留；`rebuild-deploy-20261104/maven` 未被覆盖。

---

## 2. 核心证据：P0-1 被证伪（本轮最重要的产出）

实现 agent 按 reviewer 要求补了**真实 GL 交替用例** `dirtyStateEvidenceAcrossVanillaAndSkijaPasses`：隐藏 GLFW core 上下文、单 Skia context 跨 6 轮 vanilla↔Skija，vanilla 侧 dirty stencil func/op、`GL_MULTISAMPLE`、`GL_DITHER`/`GL_FRAMEBUFFER_SRGB`、depth/cull/polygon/line 四类状态，Skia 侧用非矩形 `clipRRect` + 半透明叠加触发 stencil 路径。

| 位集合 | 结果 | 证据 |
| --- | --- | --- |
| 7 位（初版） | on 与 `GL_ALL` 像素不一致，断言 RED | `build/panel-opt-20261104/logs/i1-evidence-7bit.log`：`index [0] expected <16> but was <0>` |
| 11 位（补齐） | `differing_pixels=0 max_channel_delta=0` | `logs/i1-evidence-11bit.xml`（从 AtomLib hiddenGlTest XML 归档，含 `I1_EVIDENCE` 行）；console 9/9 PASSED |

**诚实标注**：初版报告写的 `9216 / 255` 是**按整帧字节数（48×48×4）与首字节差异推断，不是日志直接打印**；RED 版 XML 被后续 GREEN 运行覆盖、未落盘。已在报告与 manifest 中改为「推断值」并修正引用路径。

**结论**：7 位不安全；补齐 11 位（OR=2047）覆盖 Java 枚举全部有效位，与 `GL_ALL=65535` 的差集是 native 未暴露高位，严格等价未证明，但**无可判定性能收益**。从 7 位直接跳到 11 位是保守取舍，8/9/10 位白名单未逐个证伪。→ **lib 支建议实机跳过，`atomlib.bridge.fastState` 全程不要加。**

### 这对责任判定的意义（重要，别误读）

之前报告 §11 把 BRIDGE_ENTER 2.12ms 归给「每帧全量状态捕获/恢复 + `resetGLAll`」。本轮实验只否掉了**「细粒度 reset 能省时间」**这条捷径，**没有**否定「550–600 次 `glGet*` + 400+ 次恢复是常驻成本」这个主判断。恰恰相反：因为 P0-2 出于 GL 状态/alpha 安全不敢裁剪捕获集合（只缓存了 5 个 `MAX_*`），**真正的成本还没被动到**。

所以下一步的正确顺序是：① 在真实 GL 下给 `capture` / 上下文准备 / `resetGLAll` / `flush` / `close` 各打同一 frameId 的事件，拿到 2.12ms 的内部分布；② 用「宿主实际改了哪些状态」的证据做**状态子集裁剪**（这才是 P0-2 的原意）；③ 或者直接走第二阶段分层缓存架构。

---

## 3. 三支的实际结果

| 支 | 实现 | 结果 |
| --- | --- | --- |
| P0-1 | `resetGLAll()` → `reset(11 位)`（开关内） | **无可判定收益**，实机建议禁用该开关 |
| P0-2 | `GlStateScope` 缓存 5 个 `MAX_*` 查询，按 (context, capabilities) / `rebuild()` 失效；完整 capture/restore 集合未动 | **约 1%**（550–600 次里省 5 次）；报告已明确标注「**未达方案意图**」，状态子集裁剪单列第二阶段 |
| P0-3 | 点阵用 Skija `Picture` 命令录制/回放缓存（非方案写的 `Image`+`drawImageRect`，后者实测 1 LSB 差异违反 Q1 零差异），key 含 kind/x/y/w/h/radius/tint/above/density，LRU 64 | 离屏 CPU 光栅全命中 −0.053ms（噪声 0.027ms）；**几何连续变化 / LRU 淘汰两个 miss 场景 on 更慢**（p50 +0.012~0.035ms、p95 +0.271ms、分配增量 8.5–9 倍）。像素逐字节一致 |

公开 API 零变化（javap 两类 diff 为空）；AC 对 P0-1/2 零代码改动；两个开关默认 false，off 路径零额外 GL 操作 / 分配 / JFR 事件 / 每帧日志（仍有开关固有的 volatile 读与分支，报告已改掉「逐字等价」的夸大措辞）。

---

## 4. 验证门（重跑后）

| 项 | 结果 |
| --- | --- |
| lib TDD / 全量真实 GL | 9/9 PASSED；off 58 passed、on 57 passed + 1 skipped，0 failed |
| 三端全量 build | Fabric 251 suites / 1660 tests、Forge 247 / 1645、NeoForge 247 / 1645，各 **0 failures / 0 errors / 5 skipped** |
| `verify_targets.py` / 三端 `verify_jars.py` / `verify_profile_wiring.py` | 全 PASS |
| golden frame | fabric + official/forge 各 **12/12 identical**，两端 off 对照 PASS；亮/暗 tint 5 × 三端 15 条 identical（**只在暗背景 `0xFF1F2937` 上测，亮背景/主题切换未覆盖**） |
| 离屏 A/B | HIT/MISS 均 `PIXEL_IDENTICAL=true`；CPU 光栅，**不可外推到 GL 后端/实机** |
| 公开 API | 两类 IDENTICAL |

> 证据可复核性保留意见：Forge/Fabric 全量 `TEST-*.xml` 被后续 `goldenCompare --rerun-tasks` 覆盖为只剩 1 个 XML，三端测试计数目前只能依赖报告自述（NeoForge 可实测复核）。

### 候选 SHA-256（`build/panel-opt-20261104/candidates/`）

```
AC  Forge      74a8c3785c8a93981a7d3dbddf3601c1c9adb3d05c8a29d897a40393c422e8d6
AC  Fabric     87128277484697c79df7e6a537559b977e021a399ecefe197131f582ab8f205e
AC  NeoForge   f8bd6307030355bbd5866f8b46b00d791d1452aebd1abfac2b7772db37da298a
Lib Forge      a3b214eeaf425f7a688c18208d39160964488e3eb6e5abb8c7ab51003b1d7118
Lib Fabric     08fbc486e9a803595c2311d3458d87dc9f817eb5718cb62adddfb632050bebf7
Lib NeoForge   9234ed8a5f663a41e6560537e14bf28e15688f99bd249088779375b3360dae23
```

AC Fabric / NeoForge 与上一轮哈希相同（consumer jar 不含 lib class、点阵实现未变、无时间戳漂移）；AC Forge 唯一差异是 `META-INF/MANIFEST.MF` 的 `Implementation-Timestamp`（700 条目中 699 一致）。

---

## 5. 现场状态与回退

- **三端实例已部署候选**（Q4-B 授权）。`deployment-receipt.json` 含 `rounds` 两轮：round 1 `20261104-221327`（6 个**原始包备份**，最终回退锚点，全部保留且哈希匹配）、round 2 `20261104-230615`（6 个新备份，6 文件 `verified=True`、`errors=[]`）。两轮 WMI 均 `NO_JAVA_PROCESSES`、未启动游戏。
- **回退三层**：① 去掉 `-D` 开关（默认就是旧路径）；② 按 `rounds[0].*.bak-20261104-221327` 逐文件还原并复验 SHA-256；③ 弃用隔离仓 `D:/AtomLib/build/panel-opt-20261104/maven` 与快照 `build/panel-opt-20261104/source/`。
- AC 工作区锁未动、用户 31 改 + 8 未跟踪未动、已发布 Maven 仓未覆盖、未 commit/push。
- **AtomLib 工作区副作用（保留，未清理）**：`docs/evidence/appearance-replication-and-backdrop/gl/atomlib-1.21.1-fabric/` 下 2 个 M（`timing.json`、`independent-warm-timing.json`）+ 9 个未跟踪（`backdrop-stage-metadata.txt`、3 个 `backdrop-stage-round-*.csv`、`item-icons/` 内 5 项），共 11 项，均为第 1 轮 `hiddenGlTest` 自动产物、非用户改动。第 2 轮已把产物指向隔离目录，未新增污染。按「不清理/不回退」约束保持原样，用户若要纯净工作区可自行删除。
- 源码中 `SkijaRenderBridge.java:46,195` 两处「equivalent to resetGLAll / byte-for-byte identical」措辞略夸大（详见 §2/§3）。**未改源码注释**：改了就要重跑构建与部署，零技术收益；以本报告为准。

---

## 6. 实机 A/B 建议（判定权归用户）

reviewer 第二次复核总体判断：**有条件，且只测 AC 点阵支**。

1. 只开/关 `atomchat.dots.cached`；**`atomlib.bridge.fastState` 全程不加**（lib 支无可判定收益，且 11 位掩码未验证 native 高位路径）。
2. 先确认关面板 400fps 基线 → 对照-对照测噪声 σ → A-B-A-B / B-A-B-A 交替，3 轮、每场景 ≥3000 稳定帧、预热丢前 100 帧、>2σ 才计收益；`panelNanos` p50/p95/p99 与外部 FPS **双指标 AND**。
3. **必须同时覆盖静止（全命中）与滚动/动画/多条目（miss/LRU）** —— 后者离屏已知更慢，实机需确认是否负收益。
4. 实机首帧与亮/暗背景截图人工确认点阵观感、字体粗细、背景、导航打断、主帧缓冲 alpha 安全（离屏证据全是 CPU 光栅）。
5. 采样用 `docs/verification/2026-11-04-panel-sampling-kit/auto-record.ps1`（零交互、`filename=`+`dumponexit=true`）。

**但技术判断必须说清楚**：lib 支归零 + P0-2 约 1% + P0-3 miss 更慢 ⇒ Q2 硬线（4.0→2.0ms、170→250fps）**从机制上不可达**；方案 §2 原本估算的微优化乐观上限 ≈1.2ms 主要依赖 lib 支，现已不成立。**若用户目标是「帧率不骤降 / 回到 400」，不建议消耗实机采样，应直接按方案 §6 退回 Q2 重新分级，进入第二阶段。**

---

## 7. 下一步（二选一，由用户拍板）

| 选项 | 内容 | 适用 |
| --- | --- | --- |
| **A：先拿数据**（低成本，约几分钟） | 只测点阵支一次实机 A/B，确认 GL 后端零回归与 P0-3 正/负收益，同时验证采样链仍可用 | 想先确认「这版包不会把画面搞坏」、并为第二阶段留基线 |
| **B：直接第二阶段**（推荐，符合「帧率不能骤降」的预期） | 写新方案：① AtomLib 桥接内部细分诊断（同一 frameId 打 capture/准备/reset/flush/close）→ ② 基于证据做状态子集裁剪 → ③ UI 层分层缓存（模糊层每帧刷新、UI 层静止帧合成一张缓存纹理；动画帧降级策略、缓存失效判据）→ ④ 重新做像素基线与 A/B | 目标是把 4ms 常驻成本真正消掉，而不是继续微优化 |

无论选哪个：本轮候选与备份保持现状，不宣布修复、不 commit、不改工作区锁。

---

## 8. 产物索引

| 用途 | 路径 |
| --- | --- |
| 实现报告（已修正 15 处措辞/引用） | `build/panel-opt-20261104/IMPLEMENTATION-REPORT.md` |
| golden / 离屏 A/B 报告 | `build/panel-opt-20261104/golden-frame-report.md`、`offscreen-ab-report.md` |
| 交付清单（状态字段） | `build/panel-opt-20261104/delivery-manifest.json` |
| 部署回执（两轮） | `build/panel-opt-20261104/deployment-receipt.json` |
| 六个候选 JAR | `build/panel-opt-20261104/candidates/` |
| 全部日志（含真实 GREEN XML） | `build/panel-opt-20261104/logs/` |
| 完整 diff | `build/panel-opt-20261104/atomlib-changes.diff`、`atomchat-changes.diff` |
| 文档修正脚本（可复现） | `build/panel-opt-20261104/fix_report_wording.py` |
| 隔离仓 / 隔离快照 | `D:/AtomLib/build/panel-opt-20261104/maven`、`build/panel-opt-20261104/source/` |
