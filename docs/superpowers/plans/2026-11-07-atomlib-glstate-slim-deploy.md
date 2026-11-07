# AtomLib GL 状态快照瘦身：部署验收实施方案

> 执行方式：本方案按任务顺序在当前会话执行。每个任务以「可验证交付物 + 一次本地提交」收尾，任务之间按判据表验收；**不 push、不发布公共版本**。
> 本文件是 Lib 专项的**唯一**方案文档。后续只更新本文的状态勾选与「结果」节；结论收敛进 `.workbuddy/memory/2026-11-07.md`，**不新建第二份方案 / 交接 / 报告**。

**Goal:** 把 `dd31c42` 的 GL 状态快照瘦身真正部署到 `1.21.1-CCB`，一次实机定量验收，目标「面板全程 ≥300 fps」，并补齐该补丁自身的验证缺口。

**Architecture:** 只替换运行时 AtomLib jar 的字节（文件名、版本锁、AC 包全部不动），使「唯一变量 = Lib 快照边界」。离线把变异红灯、三端 hiddenGlTest、`verify` 遗漏检查全部做完，真机只启动一次；用既有 JFR 事件与日志探针读绝对耗时分桶，不比地板帧率、不做跨会话对照。

**Tech Stack:** AtomLib（Gradle 8.8 / Java 17 编译，Fabric 1.21.1 / Forge 1.20.1 / NeoForge 1.21.1）、Skija 0.143.18、JFR（`com.atomlib.GlStateCapture` / `com.atomchat.PanelSegments`）、`1.21.1-CCB` 实例、Windows + Git Bash。

## Global Constraints

- 不改 `D:/AtomChat-Main/versions/atomlib.json`（锁 `0.1.0-beta` + 三端 SHA 硬校验，红线）。
- 不 push、不发布公共 jar、不进 `main`。
- **AC 包不动**：`atomchat-Fabric-1.21.1-v0.5.0.jar` 保持 `70d22a65…`；先做 API 兼容性核查，不兼容才走重构建分支。
- **只换 Lib jar 字节，文件名保持 `atomlib-Fabric-1.21.1-v0.3.1-shadowcache.jar` 不变**，保证单变量。
- 视觉红线不动：每帧新鲜毛玻璃、不静止降频、不冻结背景、不半分辨率；`BlurMotionGate` 不复辟。
- 部署纪律：0 游戏进程 → 备份 → 复制 → 哈希回读 → 校验 AC 与其它 jar 未变 → 写回执。
- 安全阀常备：`-Datomlib.glscope.slim=false` 立即退回全量边界；备份 jar 原地可回滚。
- 真机只启动一次；离线完成 `verify` 检查，避免「先带 verify 跑一次、再关掉跑一次」的双启动。
- 新增任何埋点必须 debug 门控、产品默认零开销；日志契约 `tools/verify_log_prefixes.py` 保持既有 23 条违规、不新增。
- 结论只写进本文件与当天工作日志，不追加新文档。

---

## 0. 事实基线：那个 issue 修了没有

**答案：代码修了、离线验过、但从没上真机；而且只修掉了 3 倍膨胀的一部分。**

| 事实 | 值 |
| --- | --- |
| 原始 issue | 0.3.2 的 `GlStateUtil` ≈**260 次**原生 GL 调用/帧，抽取成 AtomLib `GlStateScope` 后 ≈**854 次**（3 倍多） |
| `dd31c42` 的修复 | 桥自己的 Skija 通路走瘦身边界：**854 → 495 次**（capture 292 + restore 203，−42%）；通用宿主路径一字未动 |
| 相对旧基线 | **仍多 235 次**（495 vs 260，约 1.9 倍），恢复侧还有 203 次（纹理单元 32×4 类 + indexed blend） |
| 已验证 | 真实 GLSL + readback 逐字节像素等价（`SlimSnapshotPixelEquivalenceTest`）；AC × 瘦身版三端集成构建全绿 |
| **未验证** | 未部署真机；**变异红灯未做**；状态泄漏反证只补一条；NeoForge 的 slim 验证只有 BUILD SUCCESSFUL；真机收益未知 |

**2026-11-07 16:11 的重要更正**：此前判断「`8724387` 一整批优化从未上真机」是**错的**。逐类 `javap` 拆开 `v0.3.1-shadowcache.jar` 后确认，`TextService` 的 `shapes/cache/shaper`、`YogaLayoutEngine.sameLayout`、`PresentationFrame.paintingFrames`、`SkiaDraw` 的 `SHARED_PAINT/SHADOW_IMAGES`、`GlStateScope.captureSkia`、`GlScopeDiagnostics` **全部已在真机 jar 内**。旧 jar 里唯一缺席的是 `slimSnapshot` / `verifyDroppedFamilies` / `nativeCalls` 三个字段——两边界接口建好了，瘦身实现没交。这与 JFR `slim 字段存在 = False` 互证。

⇒ **本专项不是开荒，是交唯一一份没交的作业**。收益预期只能按 GL 快照一块算，不叠加那批已在跑的缓存优化。

## 1. 冻结判据（跑之前写死，测完不补测）

基线来自 2026-11-07 15:21 场（34374 帧 / JFR 181s / 全程 A 档 / 页面 steady / 无 warmup failed）：

| 指标 | 基线 | 300 fps 门槛 |
| --- | --- | --- |
| 稳态 40–120s frame interval | 3.018 ms = **331 fps** | 3.333 ms，余量 +0.315 ms |
| 开面板 0–10s | 3.474 ms = 288 fps | 差 −0.141 ms |
| 开面板 10–20s | 3.429 ms = 292 fps | 差 −0.096 ms |
| 开面板 20–40s | 3.013 ms = 332 fps | 已达标 |
| `GlStateCapture` p50 | 0.446 ms | 预期 0.26–0.34 ms |
| `BRIDGE_ENTER` p50 | 0.450 ms | 应同步下降 |
| render wall / 非 AC | 2.006 / 1.034 ms | 非 AC 不动 |
| `client_work` p50 | 0.046–0.060 ms | 不得变化 |
| 首开 `total` / `first_draw` | 71.975 / 45.121 ms | 不得变差 |

**验收判据（全部满足才算达标）**

1. 开面板 0–10s 与 10–20s 的 `frame_pump fps_p50` **均 ≥300**（预期 ≈304，余量仅 4 fps，偏紧）。
2. 稳态 `frame interval p50` ≤3.018 ms（不得退化），`GlStateCapture` p50 相对 0.446 ms 有可测下降。
3. `DRAW` / `DOTS` / `BLUR` / `PRE_UI` p50 与基线偏差 ≤0.05 ms（证明只动了快照边界）。
4. `first_open_budget.total` ≤71.975 ms，`client_work` 不变；无 `warmup failed`、无 `GlStateCapture success=false`。
5. 面板视觉正常、无黑屏 / 花屏 / 状态泄漏（用户现场确认一眼）。
6. 全程 `mode=A`、无 `measure_switch`、页面 `page_phase=steady`。

**不达标时的处理**：用备份 jar 原地回滚，记录数据，**不追加优化、不现场改代码**。目标 300 只需 0.14 ms，若瘦身连这点都拿不到，说明方向本身不成立，应当收手而不是继续加补丁。

## 2. 文件结构（会动什么）

| 路径 | 责任 | 变更性质 |
| --- | --- | --- |
| `D:/AtomLib/platforms/shared/src/main/java/com/atom/lib/client/render/GlStateScope.java:401` | 变异红灯的临时改点（改完必须还原） | 临时改，最终 `git diff` 为空 |
| `D:/AtomLib/platforms/shared/src/test/java/com/atom/lib/client/render/SlimSnapshotTest.java` | 复用现成的像素等价用例，不改 | 只读 |
| `D:/AtomChat-Main/docs/verification/2026-11-06-glstate-slim-port.md` §8 | 追加变异红灯证据 | 追加 ≤20 行 |
| `D:/AtomLib/platforms/1.21.1-fabric/build/libs/atomlib-1.21.1-fabric-<ver>.jar` | 构建产物 | 新产物 |
| `D:/AtomChat-Main/build/atomlib-slim-20261107/` | 候选 jar、哈希、javap 快照、JFR 分析输出 | 新建（不进 git） |
| `D:/Myworld/.minecraft/versions/1.21.1-CCB/mods/atomlib-Fabric-1.21.1-v0.3.1-shadowcache.jar` | 唯一被替换的运行时文件 | 字节替换，文件名不变 |
| `D:/AtomChat-Main/docs/superpowers/plans/2026-11-07-atomlib-glstate-slim-deploy.md` | 本方案，Results 节 | 就地更新 |
| `D:/AtomChat-Main/.workbuddy/memory/2026-11-07.md` | 结论收敛 | 追加 |

复用、不新增的分析脚本：`build/allchain-20261107/analyze_jfr.py`（已支持 `argv[1]` 指定 JFR）、`analyze_warmup_window.py`、`build/first-open-20261106/deploy.py`（部署模板）、`AtomLib/build/scope-diagnostics-20261105/auto-record-scopes.ps1` + `scope-profile.jfc`。

## 3. 任务

### Task 1 — 补变异红灯（报告 §8 唯一硬缺口）

**Files:** 临时修改 `GlStateScope.java:401`；追加 `docs/verification/2026-11-06-glstate-slim-port.md` §8。

**Interfaces:** 复用 `SlimSnapshotTest.slimAndFullProduceIdenticalPixelsAcrossAlternatingPasses()`（已存在，`HiddenGlFixture` 真实 GL 上下文，无 skip 路径）。

- [ ] **Step 1: 确认绿灯基线**

```bash
cd /d/AtomLib/platforms/1.21.1-fabric && JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" \
  ./gradlew hiddenGlTest --tests "com.atom.lib.client.render.SlimSnapshotTest.slimAndFullProduceIdenticalPixelsAcrossAlternatingPasses" \
  --offline --console=plain 2>&1 | tail -12
```

Expected: `BUILD SUCCESSFUL`，测试 passed。

- [ ] **Step 2: 制造变异**

`GlStateScope.java:401` 当前为 `org.lwjgl.opengl.GL40C.glBlendFuncSeparatei(i,b[0],b[1],b[2],b[3]);org.lwjgl.opengl.GL40C.glBlendEquationSeparatei(i,b[4],b[5]);`。只把其中一个 `glBlendFuncSeparatei(...)` 调用临时改成 `;`（保留 `glBlendEquationSeparatei`），使「必须恢复的 indexed blend」漏一项。用 `git diff -- GlStateScope.java` 确认只改了这一个调用。

- [ ] **Step 3: 跑红灯**

重跑 Step 1 的命令。Expected: `FAILED`，`assertArrayEquals` 像素 diff 报错。若仍绿，说明该族不是必须恢复项，改选 `:403` 的 ARB 分支或 `:395` 的 `glEnablei` 重试，并在 §8 记录实际命中的那一行。

- [ ] **Step 4: 还原并复跑绿灯**

用 Edit 把 Step 2 改过的那一处精确改回原文（**不要用 `git checkout --`，避免连带丢弃同文件其它改动**）：

```bash
cd /d/AtomLib && git diff --exit-code -- platforms/shared/src/main/java/com/atom/lib/client/render/GlStateScope.java
```

Expected: 无输出、退出码 0（源码与 `dd31c42` 完全一致）。重跑 Step 1 命令 → `BUILD SUCCESSFUL`。

- [ ] **Step 5: 记录证据并提交**

在 `2026-11-06-glstate-slim-port.md` §8 的「变异红灯（尚未做）」下改为已完成，写清变异行、红灯命令、失败断言、还原后的绿灯。提交：

```bash
cd /d/AtomChat-Main && git add docs/verification/2026-11-06-glstate-slim-port.md && \
  git commit -m "docs: 补齐 GL 状态瘦身变异红灯 —— 漏恢复 indexed blend 时像素等价测试变红"
```

### Task 2 — 三端验证 + 离线 `verify` 遗漏检查

**Files:** 无源码改动；证据落 `build/atomlib-slim-20261107/verification.log`。

- [ ] **Step 1: 三端 hiddenGlTest（补 NeoForge 缺口）**

```bash
J21="C:/Program Files/Java/jdk-21.0.11"; J17="C:/Program Files/Java/jdk-17.0.18"
cd /d/AtomLib/platforms/1.21.1-fabric   && JAVA_HOME="$J21" ./gradlew hiddenGlTest --offline --console=plain 2>&1 | tail -8
cd /d/AtomLib/platforms/1.20.1-forge   && JAVA_HOME="$J17" ./gradlew hiddenGlTest --offline --console=plain -Dnet.minecraftforge.gradle.check.certs=false 2>&1 | tail -8
cd /d/AtomLib/platforms/1.21.1-neoforge && JAVA_HOME="$J21" ./gradlew hiddenGlTest -x downloadAssets --offline --console=plain 2>&1 | tail -8
```

Expected: 三端均 `BUILD SUCCESSFUL`；Fabric 与 Forge 的 slim 用例全绿，**NeoForge 这次要有实际测试数**（此前只有构建成功）。

- [ ] **Step 2: 离线 verify 模式**

用环境变量把开关透传给 gradle fork 出的测试 JVM（比改 build.gradle 更轻，且不留下源码改动）：

```bash
cd /d/AtomLib/platforms/1.21.1-fabric && \
  JAVA_TOOL_OPTIONS="-Datomlib.glscope.verify=true" JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" \
  ./gradlew hiddenGlTest --tests "com.atom.lib.client.render.SlimSnapshotTest" --offline --console=plain 2>&1 | tail -20
```

Expected: 全绿且日志中**无 `verifyDroppedFamilies` 的遗漏写入报告**；有则停，回到 `GlStateScope` 补回该族，不继续部署。

- [ ] **Step 3: 记录**

把三端输出汇总到 `build/atomlib-slim-20261107/verification.log`，不进 git、不写新报告。

### Task 3 — 构建可部署 jar 并体检

- [ ] **Step 1: 构建 Fabric 端**

```bash
cd /d/AtomLib/platforms/1.21.1-fabric && JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" \
  ./gradlew build --offline --console=plain 2>&1 | tail -12
```

产物：`platforms/1.21.1-fabric/build/libs/atomlib-1.21.1-fabric-<atomlib_version>.jar`（`gradle.properties` 默认 `0.3.0`）。用 `ls -t build/libs/*.jar | head -1` 取实际路径。

- [ ] **Step 2: 体检（三项全过才可用）**

```bash
J=$(ls -t /d/AtomLib/platforms/1.21.1-fabric/build/libs/atomlib-1.21.1-fabric-*.jar | grep -v -E "sources|javadoc|dev" | head -1)
javap -classpath "$J" -p com.atom.lib.client.render.GlStateScope | grep -E "slimSnapshot|verifyDroppedFamilies"   # 必须命中
unzip -l "$J" | grep -q "com/atom/lib/client/render/GlScopeDiagnostics.class" && echo "JFR 事件在"
unzip -p "$J" com/atom/lib/client/render/GlStateScope.class | strings | grep -q "atomlib.glscope.slim" && echo "安全阀在"
```

Expected: `slimSnapshot` 与 `verifyDroppedFamilies` 命中、JFR 事件在、安全阀字符串在。（旧 jar 里这三个字段全无，这是「新 jar 真的带瘦身」的判据。）

注：`PanelBlurRenderer` / `BlurMotionGate` 属 **AC 包**，Lib jar 里本来就没有，不要拿它当体检项；AC 包本轮不动，红线由「AC 包 sha 不变」保证。

- [ ] **Step 3: 归档候选**

```bash
mkdir -p /d/AtomChat-Main/build/atomlib-slim-20261107/candidates
cp "$J" /d/AtomChat-Main/build/atomlib-slim-20261107/candidates/atomlib-slim.jar
sha256sum /d/AtomChat-Main/build/atomlib-slim-20261107/candidates/atomlib-slim.jar
```

记录 sha，后续部署只认这颗候选。

### Task 4 — 静态 API 兼容性核查（决定 AC 包能否不动）

- [ ] **Step 1: 对比公开签名**

```bash
OLD=/d/Myworld/.minecraft/versions/1.21.1-CCB/mods/atomlib-Fabric-1.21.1-v0.3.1-shadowcache.jar
NEW=/d/AtomChat-Main/build/atomlib-slim-20261107/candidates/atomlib-slim.jar
for c in com.atom.lib.client.render.GlStateScope com.atom.lib.client.render.SkijaRenderBridge \
         com.atom.lib.client.render.MinecraftRenderBridge com.atom.lib.text.TextService \
         com.atom.lib.design.atomchat.SkiaDraw; do
  javap -classpath "$OLD" -public "$c" | sed '1d' > /tmp/old.txt
  javap -classpath "$NEW" -public "$c" | sed '1d' > /tmp/new.txt
  echo "--- $c ---"; diff /tmp/old.txt /tmp/new.txt || true
done
```

Expected: 无 `-` 开头的行（无删除 / 无签名变更）；允许 `+` 新增。

- [ ] **Step 2: 分支判定**

无删除 → **AC 包不动**，继续 Task 5。出现删除或签名变更 → 用新 Lib 重新构建 AC 三端（`-PatomlibMavenRepo=<新隔离 maven>`），并把「AC 包字节变化」记入变量清单，判据 3 的容忍度收紧到 0.03 ms；**不默认走这条**。

### Task 5 — 部署（单变量）

- [ ] **Step 1: 精确安全门**（此前 `tasklist` 会误伤无关 Gradle 进程）

用 PowerShell 工具执行：

```powershell
$game = @(Get-CimInstance Win32_Process -Filter "Name like 'java%'" | Where-Object { $_.CommandLine -match 'KnotClient|minecraft|1\.21\.1-CCB|fabricmc|net\.minecraft' }); "游戏进程数=$($game.Count)"
```

Expected: `0`。同时 `latest.log` 静止 >60s（`mv` 本身兼作文件占用锁）。

- [ ] **Step 2: 备份 + 替换 + 回读**（复制上轮已验证的部署纪律，文件名保持不变）

```bash
set -euo pipefail
SRC=/d/AtomChat-Main/build/atomlib-slim-20261107/candidates/atomlib-slim.jar
MODS=/d/Myworld/.minecraft/versions/1.21.1-CCB/mods
DST=$MODS/atomlib-Fabric-1.21.1-v0.3.1-shadowcache.jar
AC=$MODS/atomchat-Fabric-1.21.1-v0.5.0.jar
BEFORE=$(sha256sum "$DST" | cut -d' ' -f1); ACH=$(sha256sum "$AC" | cut -d' ' -f1)
OTHERS=$(find "$MODS" -maxdepth 1 -type f -name '*.jar' ! -name "$(basename "$DST")" -print0 | sort -z | xargs -0 sha256sum | sha256sum | cut -d' ' -f1)
mv "$DST" "$DST.bak-slim-$(date +%Y%m%d-%H%M%S)"; cp "$SRC" "$DST"
[ "$(sha256sum "$DST" | cut -d' ' -f1)" = "$(sha256sum "$SRC" | cut -d' ' -f1)" ] || { echo "REJECT 新包哈希"; exit 1; }
[ "$(sha256sum "$AC" | cut -d' ' -f1)" = "$ACH" ] || { echo "REJECT AC 包变了"; exit 1; }
[ "$(find "$MODS" -maxdepth 1 -type f -name '*.jar' ! -name "$(basename "$DST")" -print0 | sort -z | xargs -0 sha256sum | sha256sum | cut -d' ' -f1)" = "$OTHERS" ] || { echo "REJECT 其它 jar 变了"; exit 1; }
echo "before=$BEFORE after=$(sha256sum "$DST" | cut -d' ' -f1) ac=$ACH"
```

Expected: 三项校验全过，打印新 sha。把 before / after / 备份名 / 时间写进 `build/atomlib-slim-20261107/deployment-receipt.json`。

- [ ] **Step 3: 本地提交回执**（可选，若回执入库）

```bash
cd /d/AtomChat-Main && git add build/atomlib-slim-20261107/deployment-receipt.json 2>/dev/null || true
```

`build/` 通常被忽略，回执只留本地即可，不强求入库。

### Task 6 — 实机一次采全

- [ ] **Step 1: 用户侧协议（与 15:21 场完全一致，不新增操作）**

1. PowerShell 粘贴并**别关窗口**：`powershell -NoProfile -ExecutionPolicy Bypass -File "D:\AtomLib\build\scope-diagnostics-20261105\auto-record-scopes.ps1"`
2. 启动 `1.21.1-CCB`，进世界
3. 全程**别按 F6**（锁 A 档）
4. 按一次 `Y` 打开面板，保持开着约 90 秒
5. 关游戏，说一声

**不额外加 JVM 参数**：`verify` 已在 Task 2 离线做完，避免双启动。若现场出现疑似状态泄漏，才用 `-Datomlib.glscope.slim=false` 回滚复验。

- [ ] **Step 2: 读数据（一次判完，不补测）**

```bash
LOG=/d/Myworld/.minecraft/versions/1.21.1-CCB/logs/latest.log
grep "first_open_budget\|mode_switch\|warmup failed" "$LOG"
grep "frame_pump" "$LOG" | head -18
grep "client_work" "$LOG" | head -3
NEW=$(ls -t /d/AtomLib/build/scope-diagnostics-20261105/samples/*.jfr | head -1)
cd /d/AtomChat-Main && python build/allchain-20261107/analyze_jfr.py "$NEW" | head -30
python build/allchain-20261107/analyze_warmup_window.py "$NEW" | tail -12
```

- [ ] **Step 3: 逐项对 §1 判据表**，特别核对 `slim 字段存在 = True`（这次应该从 False 变 True，是新 jar 生效的硬证据）与 `GlStateCapture` p50 是否真的下降。

### Task 7 — 收敛结论与决策

- [ ] **Step 1: 就地更新本文**的「Results」节：判据表逐项填实测值、达标 / 未达标、新 jar sha、JFR 样本名。不改文件数量。
- [ ] **Step 2: 追加当天工作日志**，本地提交：

```bash
cd /d/AtomChat-Main && git add .workbuddy/memory/2026-11-07.md docs/superpowers/plans/2026-11-07-atomlib-glstate-slim-deploy.md && \
  git commit -m "docs: AtomLib 瘦身部署验收结论（一份方案就地更新）"
```

- [ ] **Step 3: 三选一决策，不自行扩大范围**
  - **达标** → 收工。AC 与 Lib 都停手，不发布、不 push；保留分支与备份 jar。
  - **未达标但无异常** → 用备份 jar 原地回滚，记录「瘦身收益不足以覆盖 0.14ms」，**不追加补丁**；回到「接受稳态 331 / 前 20s 288」或重新谈视觉红线。
  - **出现状态泄漏 / 崩溃** → 立刻 `-Datomlib.glscope.slim=false` 并回滚 jar，回 Task 1 补该族的反证。

## 4. 回退路径（任何时候可用）

| 情况 | 动作 |
| --- | --- |
| 视觉异常 / 崩溃 / 状态泄漏 | 启动参数加 `-Datomlib.glscope.slim=false`（不重编译、不换 jar） |
| 想彻底回到测量前 | 用 `*.bak-slim-<时间戳>` 覆盖回 `atomlib-Fabric-1.21.1-v0.3.1-shadowcache.jar`，校验 sha == `76c4ba40…` |
| AC 侧连带异常 | `atomchat-Fabric-1.21.1-v0.5.0.jar` 未动；若怀疑，用 `.bak-prewarm-20261107-151028` 回滚到 `c236ec7b…` |

## 5. 结果（执行后填写，不新建文档）

| 判据 | 基线 | 实测 | 结论 |
| --- | --- | --- | --- |
| 开面板 0–10s fps | 288 | 待填 | 待判 |
| 开面板 10–20s fps | 292 | 待填 | 待判 |
| 稳态 frame interval | 3.018 ms | 待填 | 待判 |
| `GlStateCapture` p50 | 0.446 ms | 待填 | 待判 |
| `slim 字段存在` | False | 待填 | 待判 |
| 稳态帕累托 / 首开 / `client_work` | 见 §1 | 待填 | 待判 |
| 视觉与异常 | — | 待填 | 待判 |

**新 jar sha：** 待填　**备份名：** 待填　**JFR 样本：** 待填　**最终决策：** 待填

---

## 执行交接

方案已保存至 `docs/superpowers/plans/2026-11-07-atomlib-glstate-slim-deploy.md`。两种执行方式：

1. **按任务顺序在当前会话执行（推荐）** — 我逐任务跑、每个任务结束报一次结果，你在 Task 5 部署前有一次确认机会。
2. **分阶段执行** — 先只做 Task 1–4（离线全绿 + 构建 + 兼容性），把候选 jar 和结论交给你，部署与实机另起一轮。

本次只写方案，**未改任何源码、未构建、未部署**。你说「开工」或选一种方式，我就从 Task 1 开始。
