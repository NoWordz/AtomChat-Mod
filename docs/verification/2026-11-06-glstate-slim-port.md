# GL 状态快照瘦身：把每帧 854 次原生调用降到 495 次

**日期**：2026-11-06（实现）～ 2026-11-07（收尾） ｜ **仓库**：`D:/AtomLib` 分支 `perf/checkpoint-20261106` ｜ **提交**：`dd31c42`
**状态**：代码完成、离屏等价性已验证、AC 侧集成构建全绿；**未部署真机、未真机验收**

---

## 0. 一句话

0.3.2 的 `GlStateUtil`（≈260 次原生 GL 调用/帧）在被抽取进 AtomLib 时变成了 `GlStateScope`（≈854 次，**3 倍多**）。现在把**桥自己的 Skija 通路**拆出来走一条瘦身边界：每次快照 **854 → 495 次原生调用（−42%）**，且用真实 shader + readback 证明**输出逐字节相同**。通用宿主的调用路径**一字未动**。

---

## 1. 背景：这个 3 倍是怎么来的

### 1.1 考古结论（`git log -L` / `git blame`）

| 多出来的三类 | 引入提交 | 0.3.2 里有吗 |
| --- | --- | --- |
| 逐 **draw-buffer 索引**状态（`glGetIntegeri`，8 个 draw buffer） | `cb612db`「Checkpoint AtomLib extraction with pending integration fixes」 | **没有**（`GL_MAX_DRAW_BUFFERS` 全 0 命中） |
| 逐 **uniform-buffer 绑定**（84 个 UBO × 3 个查询 ≈ 252 次） | 同上 | **没有**（`GL_UNIFORM_BUFFER_BINDING` 全 0 命中） |
| 纹理单元 × 10 种目标 + **`GL_TEXTURE_BINDING_RECTANGLE`** | 同上（`8724387` 再次触碰） | **没有**（全 0 命中） |

对照物：`D:/AtomChat-032`（0.3.2 基准，提交 `34299d29`）的 `shared/src/main/java/com/atom/chat/render/GlStateUtil.java`，422 行。

**关键判断**：这三类**不是后来修 bug 加的**。0.3.2 的 `GlStateUtil` 里一个都没有，它们是**抽取 AtomLib 那一刻一起进来的** —— 也就是用户说的「更优秀的实现被替换掉了」，而且是**搬家时变胖的**，不是有人恶意改的。

> 因此：删掉它们**不会让任何旧 bug 复发**（本来就没有旧 bug）。但逐 draw-buffer 索引状态是例外 —— 见 §3。

### 1.2 体量对比

| 实现 | 每次 snapshot 的原生 GL 调用 |
| --- | --- |
| ModernUI 3.12 | 2 读 + ≈30 写（只碰 VAO / program） |
| Muonium | ≈70 读 + ≈75 写 |
| tuui | ≥84 |
| **AtomLib 全量（改前）** | **490–526 读 + ≈330 写 ≈ 854** |
| **AtomLib 瘦身边界（改后）** | **495 次（capture 292 + restore 203）** |

落点：真机 D 档实测 `BRIDGE_ENTER + BRIDGE_EXIT = 0.716ms`，**全仓最大单块 CPU**。

---

## 2. 做法：两条边界，不动通用路径

```java
GlStateScope.capture()      // 通用宿主 / 任意 GL 调用方 —— 全量，契约不变
GlStateScope.captureSkia()  // 桥自己的 Skija-only 通路 —— 瘦身边界
```

`SkijaRenderBridge.drawFrame` 按「是否走 Skia-only 通路」选择边界（`fullState` 参数），**宿主侧 API 零变化**。

### 为什么「抄 ModernUI 的做法」不能直接照搬

ModernUI 是「**不保存、只归一化**」：它假定宿主不会在 Skia 之外设置那些状态。

**对 AC 不可直接照抄**：AC 的桥在 **Screen 阶段**（`AtomChatScreen:2050`）和 **HUD 阶段**（`NotificationHudOverlay:44`）都被调用，下面压着**原版 UI 和其它 mod 的渲染** —— 宿主确实可能设置了那些状态。归一化会把别人的状态冲掉。

所以本方案不是「不保存」，而是「**只保存 Skeia 可证明会写的那部分，其余不保存也不需要**」。

---

## 3. 快照边界决策表

| 状态族 | 全量 | 瘦身 | 理由 |
| --- | --- | --- | --- |
| VAO / program / blend / depth / stencil / viewport / scissor | ✅ | ✅ | 宿主与 Skia 都会动 |
| 逐 draw-buffer 索引状态（`glBlendFuncSeparatei` 等） | ✅ | ✅ **保留** | **Skija 的全局 `glBlendFuncSeparate` 会污染 per-index 值**，不恢复会串色 |
| uniform-buffer 绑定 / start / size | ✅ | ❌ 丢 | Ganesh **不写**（证据见 §4） |
| clip distance | ✅ | ❌ 丢 | 同上 |
| sample mask | ✅ | ❌ 丢 | 同上 |
| 纹理单元：TEXTURE_2D / RECTANGLE / sampler 等 10 种目标 | ✅ | ✅ 保留 | Skia 会用；RECTANGLE 是 0.3.2 没有的新增目标 |
| 纹理单元数量 | 全扫 | 全扫（默认） | 见 §6「为什么不做 12 单元分档」 |

### 逐 draw-buffer 索引状态为什么必须留

证据测试显示 Skija 弄脏了 `indexedBlendFunc[0..7]` —— 也就是**它确实写了** per-index 的混合函数。丢掉就会串色。这是**唯一**「Skija 会写但被瘦身排除」的类别，所以它留了下来。

---

## 4. 状态泄漏反证：为什么丢掉的三类是安全的

**论证结构**：如果 Ganesh GL 后端**从不写**某状态，那它就**不可能泄漏**（泄漏 = 被改掉了，但没人改过）。

**证据测试 `SlimSnapshotEvidenceTest.skiaWritesStayInsideTheFamilySetTheSlimSnapshotKeeps`** 的做法：
1. 故意把宿主状态设成**非默认值**（UBO 84 个、clip 8 个、sample mask 2 个、draw buffer 8 个、纹理单元 32 个全填上可辨识的值）。
2. 在桥的作用域里跑**真实 Skija 绘制**。
3. **在作用域还原任何东西之前**，把同一批状态读回来。
4. 若 Ganesh 写过其中任何一项，差值会现形。

**实测输出**：

```
SLIM_EVIDENCE dirty=[indexedBlendFunc[0..7], texture2D[0] 2 -> 36, sampler[0] 1 -> 34,
                     texture2D[31] 35 -> 0]
           highest_texture_unit=31 units=32 ubo_bindings=84 draw_buffers=8
```

| 被丢的族 | 实测 | 结论 |
| --- | --- | --- |
| uniform-buffer 绑定 | **零脏** | 丢 → 安全 |
| clip distance | **零脏** | 丢 → 安全 |
| sample mask | **零脏** | 丢 → 安全 |

**另外两条独立证据**：
- `SlimSnapshotTest.verifyTripwireReportsNoOmittedWrites` —— 打开 verify 模式（照样抓一遍被丢掉的状态但不还原），报告「无遗漏写入」。
- `SlimSnapshotTest.slimRestoresEveryFamilyItKeepsAndLeavesTheDroppedOnesAlone` —— 保留的族全部正确还原，丢掉的族保持原值。

### 宿主侧的残余风险（诚实披露）

- 上述证明覆盖的是 **Skia 的写集**。若**别的 mod 或宿主代码**在桥的作用域内改 UBO / clip / sample mask，瘦身边界不会还原它们。
- 现实概率：**低**（这三类状态在 Minecraft 渲染路径里几乎无人使用），但**不为零**。
- 缓解：verify 模式 + 回退开关（§7）。

---

## 5. 等价性证据

| 测试 | 证明了什么 |
| --- | --- |
| `slimAndFullProduceIdenticalPixelsAcrossAlternatingPasses` | 两条边界交替渲染，像素相同 |
| **`slimAndFullBoundariesProduceByteIdenticalShaderFrames`** | **真实 shader + readback**，两条边界输出**逐字节相同** |
| `slimAndFullBoundariesProduceByteIdenticalShaderFrames`（`platforms/1.20.1-forge` hiddenGlTest） | 同上（另一端复现） |

> 这个测试最初在 22:55 失败过（`the real shader produced no visible pixels` —— 测试自身没画出可见像素），22:55:40 修好测试后于 22:56 **PASSED**。失败原因是测试装置问题，不是实现问题。

---

## 6. 调用数（离屏实测，不是推算）

来自 `platforms/1.20.1-forge` 的 hiddenGlTest（`SlimSnapshotTest.slimSnapshotIssuesFewerNativeCallsThanTheFullBoundary`）：

```
SLIM_COUNTS full_capture=557 full_restore=297
            slim_capture=292 slim_restore=203
            ubo_bindings=84 clip_distances=8 sample_mask_words=2
```

| | capture | restore | 合计 |
| --- | --- | --- | --- |
| 全量 | 557 | 297 | **854** |
| 瘦身 | 292 | 203 | **495** |
| 差 | −265 | −94 | **−359（−42%）** |

**离屏耗时参考**（同一环境，`scope-full-skia.csv`）：FULL 中位 2.76ms vs SKIA 中位 0.338ms。
> ⚠️ **离屏的 glGet 代价与真机不同**（离屏是 headless 环境），这个比值**不能折算成真机收益**。真机收益要真机测（见 §9）。

### 为什么不做「只扫 12 个纹理单元」

ModernUI 抄来的方案里有一项是「纹理单元限 12 个 + 最后一个（Ganesh 的 scratch/upload 槽位，`GrGLGpu.cpp:3071-3080`）」。

**实测不支持**：Skija 弄脏的最高纹理单元是 **31**（`units=32`），不是 12 以内。虽然 31 正好是最后一个单元，但实现上意味着「12 个 + 最后一个」要写成特例，仍然要扫 13 个。

本实现保留了这个分档能力（`-Datomlib.glscope.units=N`），但**默认 0 = 全扫**（安全）。测试 `limitedUnitTierKeepsTheScratchUnitWatched` 钉住了「要么 <12，要么正好是最后一个」这条规则。

---

## 7. 安全阀（部署后仍可救，不用重编译）

| 开关 | 作用 |
| --- | --- |
| `-Datomlib.glscope.slim=false` | **退回全量边界**。出事时第一时间用它 |
| `-Datomlib.glscope.verify=true` | 照样抓一遍被丢掉的状态但不还原（对照用，能发现遗漏写入） |
| `-Datomlib.glscope.units=N` | 纹理单元分档（默认 0 = 全扫） |

另外：每次 snapshot 的**实际原生调用数**有计数器（`GlScopeDiagnostics.nativeCalls`）+ `lastCaptureCalls` / `lastRestoreCalls`，真机跑一次就能验证。

---

## 8. 构建与测试

| 平台 | 构建 | 测试 |
| --- | --- | --- |
| AtomLib Fabric 1.21.1 | BUILD SUCCESSFUL (14s) | hiddenGlTest 8 项全绿（含上面 4 条） |
| AtomLib Forge 1.20.1 | BUILD SUCCESSFUL (56s) | 同上（`SLIM_COUNTS` 与 `SLIM_EVIDENCE` 数字出自此端） |
| AtomLib NeoForge 1.21.1 | BUILD SUCCESSFUL (35s) | — |
| **AC × 瘦身版 AtomLib 集成** | Fabric / Forge / NeoForge **全绿**（22:05–22:10） | — |

> 22:33 那次 AC 集成构建失败**不是瘦身的问题**：是 AC 侧当时半成品的两个守护测试红（`AtomLibApiPreflightTest`、`PrepassProbeTest`），已由 AC 提交 `6a4230c9` / `8f5a88ac` 修掉。

**变异红灯：本轮尝试未达成（如实记录，2026-11-07 离线阶段）** ⬅ 仍为硬缺口

- 绿灯基线 / 红灯命令（同一条）：`cd /d/AtomLib/platforms/1.21.1-fabric && JAVA_HOME=<JDK21> ./gradlew hiddenGlTest --tests "com.atom.lib.client.render.SlimSnapshotTest.slimAndFullProduceIdenticalPixelsAcrossAlternatingPasses" --offline --console=plain` → `PASSED` / `BUILD SUCCESSFUL in 12s`。
- 按方案依次只改一处并用 `git diff` 确认：`:401` GL40C `glBlendFuncSeparatei`→`;`、`:403` ARB `glBlendFuncSeparateiARB`→`;`、`:395` `glEnablei`→`;`（均保留 `glBlendEquationSeparatei` 侧）。
- 三次红灯输出原文：`SlimSnapshotTest > slimAndFullProduceIdenticalPixelsAcrossAlternatingPasses() PASSED`、`BUILD SUCCESSFUL in 12/12/14s`，`12 actionable tasks: 2 executed`。**均未出现 `FAILED`、无失败断言**（该用例 `:103` 断言为 `assertArrayEquals(fullPixels, slimPixels, "slim must not change a single pixel")`）。
- 卡在哪：test-results XML 显示 GL 上下文 `version=3.3.0`（`capabilities.OpenGL40=false`，走 :403 ARB）；且 :395/:401/:403 全在 `close()` 的 `if(!indexedUniform)` 块（`GlStateScope.java:393`）内，本用例宿主 per-buffer 状态 uniform ⇒ 块被跳过。Fabric 端 `GlStateScope.class` 反编译核验 `glEnablei=0` 证明变异已编译生效，非「编译未生效」，而是变异点为运行时 no-op。
- **实际命中行：无**（方案给定三个候选点均未触发红灯）；未新增/改动任何测试与配置，未绕过。
- 还原：Edit 精确改回三处原文；`git diff --exit-code -- .../GlStateScope.java` 退出码 0（无输出）；`git -C /d/AtomLib status --short` 为空，源码与 `dd31c42` 字节一致。
- 还原后绿灯：同一命令 → `PASSED` / `BUILD SUCCESSFUL in 15s`。
- 遗留：需另选会被该用例执行的变异点（`if(!indexedUniform)` 块外 / slim 专属分支），或先让宿主产生 per-buffer 差异再变异；由主代理复核，本轮不自行扩大范围。

---

## 9. 未解风险 / 未做

1. **未部署真机**：真机上的 atomlib jar 仍是 `v0.3.1-shadowcache`，**这套逻辑当前不在真机上跑**。发布新 jar 就等于改变真机行为。
2. **默认是开的**（`atomlib.glscope.slim` 默认 true）→ 建议先带 `-Datomlib.glscope.verify=true` 跑一次，确认无遗漏写入，再关掉 verify 正常跑。
3. **未做变异红灯**（§8）。
4. **宿主侧残余风险**（§4）：别的 mod 在桥作用域内改 UBO/clip/sample mask 不会被还原。
5. **真机收益未知**：−359 次调用理论上对应真机 `BRIDGE_ENTER` 的下降，但**离屏耗时比值不可折算**，必须真机测。
6. **恢复侧仍有 203 次调用**：剩下的主要是纹理单元（32 个 × 4 类）与 indexed blend。若要再降，方向是纹理单元分档（默认全扫，安全优先）。

---

## 附：文件清单

**新增**
- `platforms/shared/src/test/java/com/atom/lib/client/render/SlimSnapshotTest.java`
- `platforms/shared/src/test/java/com/atom/lib/client/render/SlimSnapshotEvidenceTest.java`
- `platforms/shared/src/test/java/com/atom/lib/client/render/SlimSnapshotPixelEquivalenceTest.java`
- `docs/evidence/.../scope-full-skia.csv`、`scope-pairing.jfr`

**修改**
- `platforms/shared/src/main/java/com/atom/lib/client/render/GlStateScope.java`（+285 / −75）
- `platforms/shared/src/main/java/com/atom/lib/client/render/GlScopeDiagnostics.java`（+3：新增 `nativeCalls` / `slim`）
