# 渲染锐度与面板性能：交接文档

> 写给接下这项工作的人。所有结论都附了测量方式和数字，可以直接复算；
> 标 **[已测]** 的是我亲手跑出来的，标 **[待测]** 的是有代码依据但没实测的假设。

---

## 0. 先看这段（避免重复我踩过的坑）

| 坑 | 说明 |
|---|---|
| **别恢复 `BlurMotionGate`** | 它曾被删掉，`NotificationRenderWiringTest.panelBlurRefreshesEveryFrame...` 明确断言 `assertFalse(blur.contains("BlurMotionGate"))` + `assertTrue(blur.contains("boolean full = true;"))`，CHANGELOG 第 60 行写了原因：*"Refresh panel background blur from the current frame instead of the old two-frame/500 ms cadence"*。那个门禁会让毛玻璃显示过期帧（转视角拖影）。**`boolean full = true;` 是有意的。** 我一度"修"过它，被测试拦下后撤回。 |
| **Skija 的 `new Paint()` 默认 antiAlias = true** | 我实测验证过（`new Paint().isAntiAlias() == true`）。看到 `new Paint().setColor(x)` 就以为"忘了开抗锯齿"是误报。 |
| **行尾** | 仓库 `.gitattributes` 是 `* text=auto`，Windows 工作副本是 CRLF，blob 存 LF。用 Python `write_text()` 写文件会把 LF 变 CRLF，导致**按源码匹配的测试失败**。写完要和提交前都要归一化（见 §5）。 |
| **孪生副本** | `layers/mapping/official/` 下的文件在 `platforms/1.21.1-fabric/` 有 Yarn 命名的孪生，两个都要改，否则 `python tools/verify_targets.py` 失败。 |
| **不要凭注释判断行为** | 上面那个 blur 门禁的注释还在描述已被删除的行为。以测试和 CHANGELOG 为准。 |

---

## 1. 已经定论的事：不是抗锯齿的问题

### 1.1 Skija 的边缘比 GLSL SDF 更锐 [已测]

`tools/probes/EdgeProfileProbe.java`（运行：`python tools/run_edge_profile_probe.py`）
用**真实的** `ChatBubble` / `UiCards` 原语在真实密度（1.4815）下渲染，逐像素读亮度：

```
Skija 圆角填充:  30 → 41 → 82          1 个过渡像素
SDF smoothstep:  62 → 52 → 32 → 30     3 个过渡像素
```

原因：Skia 的 `drawRRect` 走 `FillRRectOp`，是**逐像素解析面积覆盖**；SDF 的
`1 - smoothstep(-fwidth, fwidth, dist)` 是 1–2 像素斜坡。数学上 Skija 更锐。

### 1.2 边缘质量与像素相位无关 [已测]

那些图元都画在分数相位上（120 逻辑 px × 1.4815 = 177.78 设备 px），边缘仍然是
"一个 0.223 覆盖率的过渡像素 + 全覆盖像素"——正是 0.78 相位应有的结果。
**所以"把填充对齐设备网格"没有意义**，别在这上面花时间。

### 1.3 两张截图的像素对照 [已测]

AC 1068×1599，Dango 2559×1599。先排除了"截图被缩放过"：AC 面板边框是
`27 → 152 → 175 → 27`，符合原生像素特征（虽然它本身就是缺陷，见 §2.2）。

归一化后（文字包围盒内）：

| 区域 | cap 高 | 对比度 | 半覆盖像素 | 笔画/字高 |
|---|---|---|---|---|
| AC 正文（灰） | 29 px | 104 | **13.3%** | 0.138 |
| AC 橙色标题 | 25 px | 133 | **12.9%** | 0.160 |
| AC 发送者名（白） | 23 px | **229** | **11.4%** | 0.174 |
| Dango 标题（白） | 44 px | 172 | **13.2%** | 0.136 |

**半覆盖像素占比 11–13% vs 13% —— 抗锯齿质量相同。笔画/字高 0.138 vs 0.136 —— 字重相同。**

那为什么看着 Dango 更锐？三个可量化的非 AA 因素：

1. **字号**：Dango 标题 cap 44 px，AC 正文 cap 23–29 px。小 1.5–1.9 倍，同样 AA 质量下必然显得软。
2. **对比度**：AC 次要文字 luma 跨度只有 104（灰压暗灰），Dango 172。低对比度读作"发灰/发糊"。
3. **AC 自己内部的差异**：AC 的白色发送者名（对比度 229）半覆盖只有 11.4%，是全场最好的；
   但正文灰色文字只有 104 对比度。**同一套渲染，对比度高的那段明显更"实"。**

这三条都是**设计 token** 问题，不是渲染 bug。

---

## 2. 真正的缺陷（按优先级）

### 2.1 `DotTexture.drawEdge` 每帧画 ~3200 个圆 ⚠ 性能最大嫌疑

**位置**：`shared/src/main/java/com/atom/chat/render/DotTexture.java:36-49`

```java
int columns = Math.min(512, (int) Math.ceil(bar.w() / UiTokens.MESSAGE_DOT_GAP));
for (int i = 0; i < columns; i++) {
    ...
    for (int row = 0; row < ALPHAS.length; row++) {   // ALPHAS.length == 9
        circle(canvas, ...);                          // 每次 canvas.drawCircle
    }
}
```

`MESSAGE_DOT_GAP = s(4) = 5` 逻辑 px。600 逻辑 px 宽的条 → **120 列 × 9 行 = 1080 次 `drawCircle`**。

每帧被调用的位置（最少三处，全在主面板上）：

- `shared/.../ui/ShellHeader.java:143`（头部）
- `platforms/*/.../AtomChatScreen.java:2603|2613`（输入栏）
- `platforms/*/.../AtomChatScreen.java:2870|2880`（底部标签栏）

**≈ 3240 次 drawCircle / 帧**，外加一次带 `MaskFilter` 模糊的 STROKE 圆角框。
`SettingsSectionPage:2448,2746`、`ConversationListPage:549`、`ProfilePage:1153` 在那些页面再加。

这是**与模糊完全无关**的固定开销 —— 正好对上你说的"关掉模糊还是掉帧"。

**建议**：每个 (条尺寸, tint) 组合渲染一次到 `Image` 缓存，之后一次 `drawImageRect`。
或者把所有圆合成一个 `Path` 一次 `drawPath`。前者更稳（圆有 9 种透明度，Path 没法逐圆设 alpha）。

**风险**：低。缓存 key 用 (宽, 高, radius, tint, above)，失效挂在主题/缩放变化上。

### 2.2 hairline 描边不是整数设备像素 ⚠ 锐度唯一实测缺陷

**位置**：`UiTokens.s(1.0F)`，被 `UiCards.drawCard` → `SkiaDraw.drawEdgeHighlight` 使用（AtomLib）。

`s(1.0) = 1.25` 逻辑 px × 1.4815 = **1.852 设备 px**。实测剖面：

```
当前实现:        30 48 110 99 82      涂抹 4 像素
1 设备像素+对中: 30 30  96 96 82      干净硬边
```

AC 截图里能直接看到：面板边框 `27 → 152 → 175 → 27`，正是 1.85 px 描边摊开的特征。

**波及面**：卡片、设置行、开关、Chrome 内侧线全部有这条线。
`ChromeMaterial.CHROME_HAIRLINE = s(0.75) = 0.9375`（更糟，0.94 px）、
`CHROME_INNER_SHADE = s(2) = 2.5`、`PAREL_BEZEL_WIDTH = s(2)`。

**建议**：把描边宽度改成 `Math.max(1, Math.round(v * density)) / density`（吸附到整数设备像素），
描边中心加 `halfDevicePx()` 偏移（`UiPixelSnap.halfDevicePx()` 已有）。

**风险**：中。**主战场在 AtomLib**（`UiTokens` / `SkiaDraw` / `UiCards`），本仓库只能改
`ProfilePage:1195`、`SettingsSectionPage:2273,2888` 三个自接调用点。需要两个仓库同时动，
或者先在 AtomLib 加一个"设备像素描边"的 helper 让调用点复用。

### 2.3 文字阴影层：既费性能又让文字发虚 ⚠ [待测]

**位置**：`shared/.../render/SkiaFontRenderer.java:259-262`

```java
SHADOW_ON_DARK = ImageFilter.makeDropShadowOnly(0.0F, 1.0F, 1.5F, 1.5F, 0x59000000);
```

sigma = 1.5 **逻辑** px × 1.4815 = **2.22 设备 px**，透明度 0x59 (35%)。
`TextSurface.CAPSULE(true)` 和 `NAME_BAND(true)` 的文字都吃这层，实现方式是
`drawTextPass` 里的 `canvas.saveLayer(...)`（`SkiaFontRenderer.java:335`，
`RichTextRenderer.java:199` 同理）。

两个后果：

1. **锐度**：每个字形周围有一圈 35% 的模糊暗晕，铺开约 4–5 设备 px。小字号下这会让字看起来发虚。
2. **性能**：`saveLayer` + `ImageFilter` = 每个文本块一次离屏渲染 + 一次模糊。
   一屏 20 条消息，每条有发送者名的就有 20 个模糊层 — 每帧。

**建议**：先在真机上量这个假设。方法见 §4。如果确认，两条路：
- 给 CAPSULE/NAME_BAND 去掉阴影（胶囊底色本来就和文字有足够对比度），
- 或把 sigma 从 1.5 降到 ~0.8 并只在对比度不足时启用。

**风险**：中。阴影是当初为"胶囊底色可能贴近文字色"加的，去掉要确认各主题下仍可读。

### 2.4 静止面板整窗重绘（结构性）

没有任何 dirty-region / 结果图缓存。每帧重画背景、头部、页面体、滚动条、边框。
缓存整幅结果图是最大剩余收益，但失效条件复杂（主题/缩放/滚动/悬停/动画/unicode 输入），
**风险很高，建议最后做**。

---

## 3. 最新改动一览（别推翻，也别重复）

已推送 `origin/main`：

| commit | 内容 |
|---|---|
| `2a25c5a3` | 图片预处理 `ImageQuality` + `SkiaImages`（14 个绘制点）、消息列表内容高度缓存+行偏移、反应键 SHA-256 缓存、formatter 静态化、缓存封顶、上传器单例、屏幕 teardown 释放 |
| `7f9aa424` | `ImageQuality` 门槛 2× → **3×**（只预处理缩得狠的图）；加 `ImagePrepCostProbe`、`EdgeProfileProbe` |
| `97d57817` | `EdgeProfileProbe` 定案：AA 不是问题 |

**已部署**到三实例（`2a25c5a3` 构建）：Forge `d1fd473e…` / Fabric `abe683d88f4a…` / NeoForge `4d1d54ddefdc…`。
备份 `D:/AtomChat-Backups/2026-11-02-render-quality/20261102-172814/`。
注意：**`7f9aa424` 和 `97d57817` 还没部署**，实例上跑的仍是 2× 门槛的版本。

### 图片预处理当前状态（已收窄，不要再扩大）

| 场景 | 倍数 | 是否预处理 | 一次性成本 |
|---|---|---|---|
| 聊天图（普通气泡） | 2.2× | 否 | 0 |
| 聊天图（宽气泡） | 1.4× | 否 | 0 |
| 壁纸 / 横幅 | 1.15× | 否 | 0 |
| 表情贴纸 | 2.0× | 否 | 0 |
| 自定义头像 | 4.3× | 是，缩到 2× 目标 | 0.40 ms + 55 KB |
| 小图标 | 3.5× | 是 | 0.16 ms + 20 KB |

网络/磁盘/`MAX_DIM`/服务器负担**零变化**。派生副本有 4M 像素（≈16 MB）上限，淘汰即 `close()`。

**性能上这是"画质换一点 CPU/内存"，GPU 侧收益未证实**（CPU 光栅下头像绘制 12.1 → 19.2 µs）。
如果目标只是省资源，把 `ImageQuality.MIN_RATIO` 提到 `Float.MAX_VALUE` 就等于关掉它；
`ImageQualityTest` 里有对应断言，一起改。

---

## 4. 怎么量（复现命令）

```bash
# 需要先构建一次，探针读编译产物
cd platforms/1.21.1-neoforge && ./gradlew classes
cd ../..

export JAVA_HOME="/c/Program Files/Java/jdk-21.0.11"

python tools/run_edge_profile_probe.py      # 边缘剖面：halo / edge / line
python tools/run_image_prep_cost_probe.py   # 图片预处理成本
python tools/run_text_sharpness_probe.py    # 文字：hinting / 密度 / 边缘宽度
python tools/run_image_scale_probe.py       # 图片降采样策略对比
python tools/run_colorspace_probe.py        # 色彩空间 / 亚像素 AA
python tools/run_shape_sharpness_probe.py   # 形状 vs 4× 超采样真值
```

**面板降帧量化**：让用户开 `debug=true` 进游戏开面板待 30 秒，日志里会有
`AtomChat render profile (debug)` 块，含 `panel p50/p95/max/alloc`。
参考基线（用户的机器，1.21.1-CCB）：`panel p50=4.6ms p95=9.1ms`。
120 fps 的预算是 8.3 ms/帧，p95 已经超了。

**验证金字塔**（缺一不可）：
```bash
python tools/verify_targets.py                       # 孪生一致性
cd platforms/<target> && ./gradlew test               # 全量测试
cd ../.. && python tools/test_verify_jars.py          # 产物门禁 16 项
python tools/verify_jars.py --profile atomlib-consumer platforms/<target>
```
Forge 需要加 `-Dnet.minecraftforge.gradle.check.certs=false`（本机证书链问题）。
基线：Fabric **1610** / NeoForge **1596** / Forge **1596**，各 4 skip，0 失败。

---

## 5. 提交前检查清单

```bash
# 1) 行尾归一化（Python 写过文件就必须做）
python3 -c "
import pathlib, subprocess
files = subprocess.run(['git','diff','--name-only'],capture_output=True,text=True).stdout.split('\n') \
      + subprocess.run(['git','ls-files','--others','--exclude-standard'],capture_output=True,text=True).stdout.split('\n')
for f in files:
    if not f.strip(): continue
    p=pathlib.Path(f)
    if not p.is_file() or p.suffix in ('.png','.jpg','.jar','.otf','.zip'): continue
    d=p.read_bytes()
    if b'\r\n' in d: p.write_bytes(d.replace(b'\r\n',b'\n'))
"

# 2) 孪生 + 测试 + 产物门禁（见 §4）
# 3) 三目标 clean build
# 4) 提交信息英文、无 emoji
```

别忘了：`platforms/1.20.1-forge/build/classes/java/test` 里可能有**已删源码的陈旧 class**
（`PanelWidthGestureTest` 就是），会让测试报假失败。`rm -rf` 掉再跑。

---

## 6. 我需要你判断的

1. **2.3 文字阴影**是假设还是事实？需要一台真机 + 一次 `panel p50` 对比（开/关阴影）。
   我可以做一个开关或探针，但"去掉阴影后各主题还读不读得清"要人眼看。
2. **2.2 hairline** 要动 AtomLib，跨仓库。谁改？
3. **§1.3 的三个设计因素**（字号、对比度、字重）要不要调？
   这是最可能让"看起来更锐"的一条，而且不涉及任何渲染风险 —— 只是改 token。
   但会改变观感/品牌，属于你的决定。
