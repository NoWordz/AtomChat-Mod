# P0-B：真实页面静态层（先量后改 → 判定「不建议改」）

**日期**：2026-11-05
**性质**：唯一执行子代理交付，接在已完成的 P0-A 之后。范围严格限定为 P0-B（真实页面静态图缓存收益核验）。P0-A 的三端 `PanelBlurRenderer.java` 改动**一字未动**。
**依据**：主报告 `2026-11-05-astra-full-performance-audit.md` §5.4 / §6.2 / §6.5 / §7 F2 / §8 P0-B；`docs/HANDOFF-panel-performance-codex.md` §6.2 / §6.4。
**所有数值均为离屏 / 受控场景**（RTX 5060 Laptop、隐藏 GL 3.3 core、合成背景、真实生产类与真实 `UiTokens` 常量）。**不等于游戏整帧 / FPS 收益，不得据此宣称性能达标。**

---

## 0. 结论（一句话）

**不建议改 `HALO_IMAGE_MIN_AREA`、不建议改任何预算、不建议改 `DotTexture.java`，停手。**
离屏探针遍历 6 个真实调用点后确认：真实关于页 hero（25,201.6）与空态 halo（32,400）都低于 40,000 门槛、当前 `image_cache=0`；降门槛让它们改走静态图路径虽能省约 0.38–0.47 ms **离屏 wall**（含 clear+flush），但 **GPU 完成时间仅省 0.008–0.012 ms**，且**透明/变换极端边界像素差达 255/255**，明确违反既有 8/255 边界。决策门第二条件不成立，按任务要求不为交差硬调门槛。

**未部署、未真机验收、未 commit。**

---

## 1. 已采信、不重做的关键事实（行号已核对）

| 事实 | 位置 |
| --- | --- |
| 门槛 `HALO_IMAGE_MIN_AREA = 40_000.0F`，判断 `box.w()*box.h() >= 40_000` | `shared/src/main/java/com/atom/chat/render/DotTexture.java:36`、`:200` |
| 缓存已有 LRU(8) / 32MB 字节预算联动、单图超预算回退、淘汰时 `Image.close()`、`Surface.close()` | `DotTexture.java:33-34`、`:250`、`:261-264`、`:271` |
| 小区域走逐字节一致的 Picture/direct；超采样 4× + LINEAR 缩放为静态图路径 | `DotTexture.java:216-234`、`:240-273`、`:206-209` |
| 真实关于页 hero：`iconSize=min(SETTINGS_HERO_ICON_SIZE, available)`、`haloPad=DOT_HERO_PAD`、被 rect 四边裁剪 | `platforms/1.21.1-fabric/.../settings/SettingsSectionPage.java:2538-2551` |
| 空态 halo：`w=min(area.w, DOT_EMPTY_W)`、`h=min(area.h, DOT_EMPTY_H)`，仅 `messages.isEmpty()` | `shared/src/main/java/com/atom/chat/ui/EmptyState.java:33-39` |
| 个人页 banner halo：`(banner.w()*0.65) × (banner.h()*0.72)`，`bannerRect` = `list.w()-2*ROW_CLIP_INSET` × `PROFILE_BANNER_H` | `platforms/1.21.1-fabric/.../page/ProfilePage.java:1153-1156`、`:724-730` |
| 3 个 `drawEdge` 调用点（走 CACHE/Picture，**不进** `HALO_IMAGES`） | `shared/.../ui/ShellHeader.java:143`；`platforms/1.21.1-fabric/.../AtomChatScreen.java:2623`、`:2894` |
| 真实 `UiTokens` 常量（AtomLib）：`SCALE=1.25`、`s(v)=v*1.25`、`DOT_HERO_PAD=45`、`SETTINGS_HERO_ICON_SIZE=68.75`、`DOT_EMPTY_W=240`、`DOT_EMPTY_H=135`、`PROFILE_BANNER_H=200`、`ROW_CLIP_INSET=8` | `D:/AtomLib/ui/.../UiTokens.java:9`、`:30-31`、`:578`、`:580-581`、`:700`、`:737`、`:781` |
| 前序 stage5 `0.910→0.072 ms` 用 320×215=68,800 受控区域，不代表真实页面 | 主报告 §5.4 / §7 F2 |
| `AstraProbe` 实测 `actual_hero_max_side=158.75 area=25201.562 image_cache=0`；`edge_cached_false 0.6223` vs `true 0.5773`（drawEdge 缓存几乎无收益） | `build/astra-performance-20261105/probe-results.txt:10-16`（不改这两个文件） |

---

## 2. 探针设计（先量；不测合成大矩形）

产物 `build/p0b-static-layer-20261105/P0bStaticLayerProbe.java` + `run_p0b_probe.py` + `probe-results.txt`。
沿用 `AstraProbe` 的测法：**真实生产类** `DotTexture`（实例 v0.5.0 JAR）+ **真实 `UiTokens` 常量**（AtomLib JAR）+ Skija 0.143.18 + LWJGL 隐藏 GL；`measure()` 用 `glFinish` + `GL_TIME_ELAPSED`（离屏探针允许，与 P1 生产非阻塞接线是两回事）。

- **遍历 6 个真实调用点**，每个 box 用真实 `UiTokens` 常量与源码逐字公式构造，不是只测一个合成大矩形（这正是前序报告的错误）：
  1. `about_hero`（`SettingsSectionPage.java:2543-2551`，无裁剪上界 `SETTINGS_HERO_ICON_SIZE + 2*DOT_HERO_PAD`）
  2. `empty_state`（`EmptyState.java:38`）
  3. `profile_banner`（`ProfilePage.java:1153-1156`；代表性面板逻辑宽 480，日志 `panel=480x860`，离屏标注）
  4. `shell_header_edge` / `inputbar_edge` / `tabbar_edge`（`drawEdge`，代表性行高，离屏标注）
- A = 当前生产路径（`cached=true`；area<门槛走 Picture 逐字节回放，area≥门槛时 DotTexture 自身已走静态图 → 作为「已缓存对照」）；B = 静态图路径（按 `DotTexture.haloImage` 逐字复刻：4× 超采样 raster → `fieldBody` → `makeImageSnapshot` → LINEAR `drawImageRect`）。
- 输出：宽/高/面积、`>=40_000` 判定、`HALO_IMAGES` 命中、每帧调用次数（源码静态分析）、Picture vs 静态图 wall/GPU p50、像素 A/B（CPU raster 逐像素 per-channel abs delta，分不透明面板样背景与透明/变换极端边界）。
- 反射读实际门槛/预算：`threshold=40000.0 supersample=4 max_halo_images=8 max_halo_bytes=33554432`（与源码 `:33-36` 一致）。

---

## 3. 探针数据（离屏 / 受控，两次运行一致，第二次 exit=0）

### 3.1 真实调用点 box 与缓存命中

| 调用点 | 宽×高 | 面积 | `>=40_000` | `image_cache` | 每帧次数 |
| --- | --- | --- | --- | --- | --- |
| `about_hero` | 158.75×158.75 | **25,201.6** | 否 | **0** | 1 |
| `empty_state` | 240×135 | **32,400.0** | 否 | **0** | 1 |
| `profile_banner` | 301.6×144 | **43,430.4** | 是 | **1** | 1（仅「自己 + 无自定义 banner」） |
| `shell_header_edge` | 464×56 | 25,984 | — | 走 CACHE/Picture | 1 |
| `inputbar_edge` | 464×48 | 22,272 | — | 走 CACHE/Picture | 1 |
| `tabbar_edge` | 464×48 | 22,272 | — | 走 CACHE/Picture | 1 |

复现主报告 F2：真实关于页 hero `image_cache=0`，缓存分支根本不进入。唯一当前就进入缓存的真实 halo 是 `profile_banner`（且仅特定状态绘制）。3 个 `drawEdge` 走 CACHE/Picture，与门槛无关，前序已证几乎无收益，不为它硬改。

### 3.2 Timing A/B（ms，wall 含 clear+flush；gpu=GL_TIME_ELAPSED p50）

| 调用点 | Picture wall | 静态图 wall | wall 差 | Picture gpu | 静态图 gpu | **GPU 差** |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `about_hero` | 0.4174 | 0.0359 | 0.3815 | 0.0109 | 0.0024 | **0.0085** |
| `empty_state` | 0.5054 | 0.0343 | 0.4711 | 0.0148 | 0.0026 | **0.0122** |
| `profile_banner`（已缓存对照） | 0.0381 | 0.0342 | 0.0039 | 0.0031 | 0.0031 | **0.0000** |

**读法**：wall 差是 CPU 光栅/回放 + flush 的离屏墙钟；GPU 完成时间差仅 0.008–0.012 ms，与主报告 §6.2「CPU 光栅收益 ≠ GPU 墙钟收益」、§6.5「阴影缓存稳定命中但同机 A/B 0.096 vs 0.097 ms 无降时」一致。`profile_banner` 因当前已走静态图，A=B，证明缓存机制本身有效且复刻正确。

### 3.3 像素 A/B（CPU raster，per-channel abs delta）

| 调用点 | 场景 | maxDelta | 差异像素 | ≤1/255 | ≤8/255 |
| --- | --- | ---: | --- | --- | --- |
| `about_hero` | 不透明面板样背景 | **7** | 3435/101124 | 否 | 是 |
| `about_hero` | 透明/变换极端 | **255** | 7411/101124 | 否 | **否** |
| `empty_state` | 不透明面板样背景 | **7** | 4388/129600 | 否 | 是 |
| `empty_state` | 透明/变换极端 | **255** | 10018/129600 | 否 | **否** |
| `profile_banner` | 两种 | **0** | 0 | 是 | 是（A=B） |

**关键**：降门槛让 hero / 空态改走静态图（4× 超采样 premul raster + LINEAR 下采样）会在透明/变换极端边界引入最高 **255/255** 的像素差；不透明背景也有 7/255（>1）。这直接反证了 `DotTexture` 注释中「bounded at eight LSB per channel」在极端边界不成立。

---

## 4. 决策门判定

任务门为两个条件 AND：

1. **「真实页面确实存在可测收益」**：离屏 wall 有 0.38–0.47 ms 差，但 GPU 完成时间仅 0.008–0.012 ms（几乎为零），且 wall 差含 clear+flush、不与游戏整帧挂钩；按主报告口径不能算真实页面 GPU 侧可测收益。
2. **「像素差可控制在既有 8/255 边界内」**：**明确不成立**（透明/变换极端 255/255；不透明背景 7/255 > 1/255 验证项）。

→ **判定「不建议改」，停手。** 不改 `HALO_IMAGE_MIN_AREA`、不改 `MAX_HALO_IMAGES` / `MAX_HALO_BYTES`、不改 `DotTexture.java`，**不为交差硬调门槛**。任务 A 的「若改」分支（门槛联动验证、超预算回退、淘汰关闭、变异红灯）因判定不改而**不执行**；探针输出的「门槛=40000 时 hero/empty `image_cache=0`」即为该变异循环的基线红侧。

---

## 5. 验证与红绿

| 项 | 结果 |
| --- | --- |
| 离屏探针编译运行 | **两次成功 exit=0**，数据一致（wall p50 波动 <0.005 ms）；产物 `probe-results.txt` |
| 探针用真实生产类 / 真实 UiTokens | 反射门槛 40000.0、supersample 4、LRU 8、32MB 与源码一致；6 个真实调用点全部遍历 |
| 决策门 | **红 → 不建议改**（像素条件不成立），按任务停手，无生产改动 |
| P0-B 专属构建 | 不适用（未改任何生产代码）。共用的三端隔离源码快照构建用于验证 P1 接线不回归，结果见下与 P1 报告 §8 |
| `python tools/verify_targets.py` | **校验通过**（6 条目标，含映射孪生/资源路径/seams 同步） |
| 三端隔离快照构建 | Fabric / Forge / NeoForge 全 **BUILD SUCCESSFUL**（详见 P1 报告 §8，同一份快照编译了本轮全部 shared 改动） |
| `NotificationRenderWiringTest.java:46-52` | 三端 XML `tests="7" failures="0" errors="0"`；`boolean full = true;` 三端各 1、`BlurMotionGate` 0 |
| 红线 | 未改视觉/动画/玻璃/模糊半径/capture 坐标；未恢复 `BlurMotionGate`；未碰 P0-A 三端渲染器 |

---

## 6. 探针输出字段说明（稳定可解析）

`build/p0b-static-layer-20261105/probe-results.txt` 每行一类：

- `threshold=.. supersample=.. max_halo_images=.. max_halo_bytes=..`：反射自 `DotTexture` 的当前常量。
- `site=<name> kind=<halo|edge> src=<file:line> w=.. h=.. area=.. ge_threshold=<true|false> image_cache=<0|1> calls_per_frame=<n> (source-static)`：`image_cache` = 画一次后 `HALO_IMAGES.size()`；`calls_per_frame` 为源码静态分析（非运行时计数）；edge 行标 `path=CACHE/Picture(not HALO_IMAGES)`。
- `picture_<name> / static_<name> wall_p50=.. wall_p95=.. gpu_p50=.. gpu_p95=.. ms`：120 样本，wall 含 `clear`+`flushAndSubmit`，gpu=`GL_TIME_ELAPSED` 结果（离屏阻塞读，仅探针）。
- `pixel <name> <opaque|extreme> maxDelta=.. differingPixels=../.. le1=.. le8=..`：CPU raster 逐像素 4 通道绝对差。

全部为**离屏 / 受控场景**；`calls_per_frame` 取自源码公式，非游戏内实测。

---

## 7. 遗留风险（如实披露）

1. 全部为离屏 / 受控数值，合成背景、单机单卡、代表性面板宽 480 与典型行高；**不得宣称等于游戏 FPS 收益**。真实极端窗口 / guiScale / 密度组合未覆盖。
2. 探针 A 路径在 `cached=true` 下对 area≥门槛的 `profile_banner` 由 DotTexture 自身走静态图（作为「已缓存对照」）；hero / empty 为 Picture 回放。静态图 B 路径按 `haloImage` 逐字复刻，非直接调用私有方法。
3. `extreme` 像素场景为透明背景 + `scale(1.3,1.7)` + `translate(7,-5)`；255/255 差异源于 premul alpha / LINEAR 下采样与直接覆盖合成的边缘不一致。真实游戏是否出现同等变换未经实例确认，但边界已被探针覆盖为「不满足」。
4. 未真机验收、未读取任何游戏内 GPU 完成时间（属 P1 / F12 范畴，见 P1 报告）。
5. 工作区存在大量未提交 WIP（AC `git status --short` 81 项）；本轮未使用 `git checkout` / `reset` / `stash` / `clean` 或任何覆盖式回退，WIP 原样保留。

---

## 8. 边界声明

- **未部署、未真机验收、未 commit / push / 发布。**
- 未启动游戏、未替换实例 JAR、未改配置/启动器；未改工作区 `versions/atomlib.json`（仍锁 0.1.0-beta）；未改 `docs/` 已有报告；未碰 `build/*/source/**` 历史快照；未改任何 shader 文件。
- **P0-B 未改任何生产源码**：`DotTexture.java`、`UiTokens.java`、三端页面类、三端 `PanelBlurRenderer.java` 全部原样。仅新增 `build/p0b-static-layer-20261105/` 探针/脚本/结果与本报告。
- 实例 JAR（`D:/Myworld/.minecraft/versions/1.21.1-CCB/mods/*.jar`）仅**只读**用于编译探针（复用 `run_probes.py` 既有做法），未写入。
- 判定权归主代理 / 用户；本子代理不安排验收或部署。

---

## 9. 产物清单

```
docs/verification/2026-11-05-p0b-static-layer.md      （本报告）
build/p0b-static-layer-20261105/P0bStaticLayerProbe.java
build/p0b-static-layer-20261105/run_p0b_probe.py
build/p0b-static-layer-20261105/probe-results.txt
build/p0b-static-layer-20261105/probe-classes/
```
