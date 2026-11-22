# AtomChat 0.9.4 Skija 图形清晰度审计

审计对象是已验收的 Fabric CCB 候选 `733e704f` 及其源码。用户认可当前阴影档位。清晰度问题包括气泡曲边像素阶梯、描边内外缘锐度不一致、上下栏和头像边框偏软，以及新 Tabler 图标比旧自制 SVG 更易看见锯齿。

## 原生像素实验

同一份 Skija 0.143.18 矢量，分别在软件栅格和 RTX 5060 Laptop 的隐藏 OpenGL/Ganesh 上绘制。画布缩放 2.5 倍，描边原始宽 1.75 个物理像素。两行仅相差半个物理像素。以下是 GPU 结果，`flat alpha` 从上方边缘采样，圆角计数只取左上角的小范围。

| 画法 | 整相位直边 alpha | 半相位直边 alpha | 圆角半覆盖像素，整／半相位 |
| --- | --- | --- | ---: |
| 原 1.75 px，覆盖 AA | 255、191 | 127、255、64 | 54／56 |
| 原 1.75 px，关闭 AA | 255、255 | 255 | 0／0 |
| 同坐标的 `drawDRRect`，覆盖 AA | 255、191 | 127、255、64 | 54／56 |
| 两侧整像素、2 px 的 `drawDRRect`，覆盖 AA | 255、253 | 255、255 | 50／42 |
| 两侧整像素、2 px 的原生 stroke，覆盖 AA | 255、255 | 255、255 | 42／42 |

这个对照复现了整相位下外缘锐、内缘只有部分覆盖的情况。关闭 AA 也会令半相位的实际描边从两行降成一行。两侧落在物理像素边界时，现有 stroke 可以保持直段饱和与曲段过渡。同样的分数坐标换成 `drawDRRect` 得到同样的直段覆盖。Skia 对普通线宽应用画布变换，对零宽 hairline 使用独立的设备像素语义。两侧应当连同样式一起对齐，避免把所有低对比的材质细线都四舍五入成高对比轮廓。 [Skia Paint](https://api.skia.org/classSkPaint.html) · [Skia Canvas](https://api.skia.org/classSkCanvas.html) · [Skija 0.143.18 Canvas 接口](https://github.com/HumbleUI/Skija/blob/0.143.18/shared/java/Canvas.java#L381)

另做两次隐藏 OpenGL 微基准。每次画 80 个预构造圆角矩形，预热后每种方式各 120 组，轮换顺序。原 stroke 的 CPU 提交 p50 为 0.055／0.057 ms，对齐后为 0.060／0.061 ms，两者 GPU 查询 p50 均为 0.005 ms。对齐的 `drawDRRect` 在本夹具 CPU p50 为 0.150／0.154 ms，GPU p50 为 0.007 ms。数字包含批次录制和 flush，未计入生成布局坐标的成本。GPU 尾部分布波动较大。该实验给出可优先尝试的低开销画法；生产面板的裁剪、材质、缓存和帧时需在原调用点验证。 [探针源码](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-audit/EdgeCoverageProbe.java) · [第一次](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-audit/edge-coverage-1.txt) · [第二次](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-audit/edge-coverage-2.txt) · [原始 GPU 像素图](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-audit/ganesh-gl.png)

## 生产路径审计

| 位置 | 已确认 | 仍需验证 |
| --- | --- | --- |
| 面板外框 | 外缘由 `UiPixelSnap` 对齐，线宽仍取逻辑值。历史 CCB 夹具实际密度为 1×，顶边 5 个物理像素全覆盖。相同配置到 1.25× 后变为 6.25 px，内侧落入分数像素，符合原生对照。 | 用相同材质在问题密度看两侧剖面。 |
| 气泡 | 两版文字气泡保留同一 AA `drawRRect`，0.9.4 改了底下的阴影。固定场景五张原图里，抽样蓝色气泡的 24,478 个实色像素位置完全一致。 | 实色覆盖无法判定圆角锯齿。固定同一填充，用旧影、当前影、诊断旁路分离对比度与覆盖，再扫静止和滚动相位。 |
| 上下栏 | AA 裁切叠加低透明渐变细描边，内高光的逻辑线宽在实际密度下可小于一像素。它的低对比与边界覆盖同时影响观感。 | 先对拍结构轮廓，再分别看渐变与裁切的贡献。 |
| 圆头像 | AA 位图裁切和 AA 圆环分别画，几何在内缘相接。现有检查覆盖同心性。 | 保持共同圆心和取图方式，分别读环的外缘、接缝和头像内容。 |

稳定面板直接绘制到 Minecraft 的主 framebuffer。世界模糊、阴影贴图、头像位图、HUD 缓存及特定动画层有各自采样路径。归档里的 Minecraft GUI 5 对应 AtomChat 的实际 1×，复测应记录最终 CTM、framebuffer 像素尺寸与样本数。Fabric 实际调用栈、配套 AtomLib 0.7.0 源码比对、原图像素和旧版本证据见子代理记录。 [逐项审计](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-agent/clarity-audit.md) · [面板外框](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/shared/src/main/java/com/atom/chat/ui/PanelBackground.java:52) · [气泡](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/shared/src/main/java/com/atom/chat/render/ChatBubble.java:15) · [上下栏](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/shared/src/main/java/com/atom/chat/render/ChromeMaterial.java:43) · [头像环](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/shared/src/main/java/com/atom/chat/render/AvatarRing.java:43)

## SVG 图标对照

从 `437a610a` 和 `733e704f` 两个实际 JAR 调用 `drawIconCentered`。两版分别绘制完全相同的旧 Send 路径作为控制，再绘制各自的 Send、Copy 和 Quote。固定按钮内的图标 token，取 1×、1.25×、2.5× 三种密度和每种四个像素相位。软件和隐藏 GL 各 12 组，控制路径在各自后端的两版间 alpha 逐像素一致。换造型的三枚图标都有部分覆盖像素，每个密度、相位和后端都如此。这个对照确认该入口的新版抗锯齿仍生效，换造型后的视觉差异还需按具体尺寸查看短线、窄缝和斜线；部分覆盖像素个数不能用来给平滑质量排名。 [像素统计](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-audit/icon-compare.txt) · [旧包 GPU 原图](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-audit/icons-old/ganesh-gl.png) · [新包 GPU 原图](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-audit/icons-candidate/ganesh-gl.png) · [探针](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-audit/IconPathProbe.java)

旧自制与新 Tabler 路径经过同类 `Path.makeFromSVGString`、tight bounds 拟合、线宽补偿与圆头圆角描边。Tabler 原规范为 24×24 画板、2 px 描边，应用当前按各路径的 tight bounds 填充图标槽，再用 App 的统一线宽。两种规则会给轮廓、留白和笔画相位带来差异。优先比较统一的实际墨迹尺寸和光学线宽，再为确有问题的小尺寸图形选择更清楚的间距、留白或专门绘制的小尺寸轮廓。短线点与曲线继续由矢量覆盖 AA 处理。 [应用图标 painter](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/shared/src/main/java/com/atom/chat/render/DrawPrimitives.java:170) · [Tabler 规范](https://github.com/tabler/tabler-icons/tree/v3.34.1#tabler-icons)

## 实施顺序与验收

1. 先把已对齐的静止面板外缘转成物理坐标，按样式允许的整数物理宽度定位两侧直边，反推描边中心和宽度。复用现有 `drawRRect` stroke，圆角保持解析覆盖；填充、裁切和描边从同一套几何派生。对齐只作用于绘制，滚动和缩放动画保留连续位置。刻意柔和的渐变细线保持原有视觉强度。
2. 上下栏先比较同形状的直边与圆角，头像先检查相接的两个覆盖边。气泡先在同一原图并排比较阴影与填充，确定位移、裁剪或贴图采样是否参与问题。按确认的实际原因局部改 painter。
3. 图标先拿 Send、Copy、Quote 和有短线点的图标做原尺寸对照。复用现有矢量路径和已缓存的曲线边界，固定尺寸下优先校正整体的对齐与光学线宽。对复杂路径的预计算、画笔复用或小尺寸缓存，在实际测到提交、分配或路径渲染开销后再选。
4. 生产验收用 framebuffer 原图的 100% 尺寸与整数倍放大，覆盖 1×、两档非整数密度、四种相位、浅深主题及静止／运动。量水平边覆盖、对角过渡、有效线宽和位移抖动；用相同场景对照热态 CPU 提交、GPU 时间及首次缓存开销。当前原生实验与归档确认了可复验的机制，现场圆角与图标观感、整页渲染成本仍待生产对照。

## 外框试做与 CCB 实机对照

面板外框继续调用一次原生 `drawRRect` 描边。已经落在设备像素边界的外框矩形照用。线宽达到一物理像素后取最近整数，圆角保留 AA，细于一物理像素的低覆盖描边延续原宽。面板底板、边框、内部裁切和三端世界模糊预捕获复用同一绘制宽度。开合变换在原坐标里连续执行。此次没有增加帧缓冲、二次绘制或位图缓存。 [绘制入口](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/shared/src/main/java/com/atom/chat/ui/PanelBackground.java:33)

离屏测试在 1.25×、原宽 6.25 物理像素时先观察到内侧额外一行 alpha 64。改后六行满覆盖，内侧下一行 alpha 0，圆角有部分覆盖像素。隔离 CCB 用 RTX 5060 Laptop、1600×900、UI 1.25×、Bliss、双 200% 模糊和同一组消息；确认原包 `733e704f` 与外框试做 `943263ca` 的夹具签名一致。原图顶部 `x=280` 处同有六行深色描边，紧邻的第七行由 `(39,49,65)` 收敛到 `(42,56,75)`，后一行均为 `(43,56,75)`。抽样图片消息区域 21,691 个原图像素 RGB 逐个一致。 [像素测试](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/shared/src/test/java/com/atom/chat/ui/PanelBezelContourTest.java:14) · [1:1 局部](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/captures/bezel-1x.png) · [8× 最近邻查看](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/captures/bezel-inner-edge-8x.png) · [改前原图](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/captures/clarity-before-a/screenshots/text.png) · [改后原图](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/captures/clarity-aligned-a/screenshots/text.png) · [图片像素读数](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/media-pixels.json)

两 JAR 首次同场景对照中，文字 p50 从 2.538 到 2.822 ms，图片从 2.504 到 2.765 ms，空聊天从 2.934 到 3.007 ms。对齐版文字样本有一簇长帧，p99 达 51.834 ms；同版本其余场景 p99 约在 3.256–5.324 ms。 [两 JAR 数据](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/paired-frames.json)

再在对齐版单一进程、单一文字页只切换绘制宽度公式，按 ABBA 两轮采 14,241 帧。每段 7 秒，采后 5 秒。对齐减原线宽的四对整帧 p50 分别是 −0.204、+0.139、−0.012、+0.011 ms；后两对稳定在约 ±0.012 ms。p95 双向波动，八段均未重现首次长帧簇。这里能确认视觉变化和测试范围内可测的整帧开销，没有独立拆出全页 GPU 或 CPU 提交时间。 [同进程统计](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/bezel-switch-frames.json) · [同进程截图](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/captures/bezel-same-process.png) · [同进程原图像素剖面](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/bezel-edge-rgb.json)

### 其余组件的 1:1 单变量截图

以下在同一游戏进程、同一固定文字页逐项切换，使用不含世界／卡片模糊的诊断夹具。各行都是测试用替换，正式候选的部件 painter 沿用验收版本。

| 位置 | 变化与读数 | 原尺寸对照 |
| --- | --- | --- |
| 气泡 | 只关文字气泡轮廓影。蓝气泡 42,028 个实色像素位置完全一致。看到的变化是底缘外的阴影，当前夹具没复现报告中的锯齿。 | [气泡](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/captures/bubble-shadow.png) |
| 上下栏 | 只旁路渐变描边自己的裁切。原尺寸未见明显改善，材质渐变和填充边缘仍需分别隔离。 | [上栏](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/captures/header-clip.png) · [下栏](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/captures/footer-clip.png) |
| 头像 | 使用固定 96×96 本地测试脸和现行头像路径。只旁路圆环后边缘观感变化，中心 36×36 的 1,296 个像素一致。 | [头像](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/captures/avatar-ring.png) |
| 图标 | 只替换 Send 与短语图标的路径，使用同一绘制入口、大小 token、线宽与背景。原图可见折线、留白等造型差异。 | [旧路径与 Tabler](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/captures/icon-paths.png) |

[诊断调用命中与截图顺序](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/captures/diagnostic/scene-log.txt) · [组件像素读数](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/diagnostic-pixels.json)

### 连续曲率实机试样

隔离探针把符合条件的 Skija 圆角填充、描边与裁切替换成两段镜像三次曲线，直边相接处曲率回到零。每个原始绘制调用至多替换一次。只复用矢量路径，头像圆形和满圆胶囊仍用原有几何。世界和卡片模糊在两侧都关闭，便于隔离轮廓形状。连续曲率两段 p50 是 3.151／2.962 ms，较相邻热态圆弧段增加约 0.54–0.69 ms。该数值涵盖多处矢量路径和裁切替换，也包含诊断入口成本。连续曲率仅供看图，外框候选继续使用原生圆弧路径。 [连续曲率实机对照](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/captures/continuous-corners.png) · [原图](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/captures/diagnostic/continuous-a.png) · [数据](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/diagnostic-frames.json) · [Skia 路径裁切](https://api.skia.org/classSkCanvas.html)

Fabric、Forge、NeoForge 各运行 30 项外框、面板接线、裁切、缩放和回归测试，全部通过。Fabric 试验包另通过 `remapJar` 和 `verifyAtomLibInput`。本轮采用开发构建，跳过两项正式设置契约任务。实机只跑 Fabric 的隔离 CCB。主 CCB 回读仍为用户已验收的 `733e704f`。组件探针第一轮少写了一个浮点参数描述符，修正后复跑取得全部截图与帧表。 [Fabric 日志](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/fabric.log) · [Forge 日志](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/1.20.1-forge.log) · [NeoForge 日志](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/clarity-trial/1.21.1-neoforge.log)

## 连续曲率构建候选

- 面板、卡片、上下栏、消息、浮层与裁剪器采用同一连续曲率几何。Skija 在物理像素上直接绘制，缓存上限为 256 条矢量路径。三端的模糊遮罩在逻辑坐标中选定轮廓，随面板变换一起投影，无新增纹理采样。保持原生圆和完整胶囊；阴影烘焙记录胶囊类型，避免缓存取整后切换到不同角形。 [矢量轮廓](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/shared/src/main/java/com/atom/chat/render/CornerGeometry.java) · [模糊遮罩](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/platforms/1.21.1-fabric/src/main/resources/assets/minecraft/shaders/core/atomchat_panel_blur.fsh)
- 外框双边像素对齐继续生效。上下栏常规半径的光边去掉冗余裁切，极小圆角保留外轮廓裁切。头像圆环向内搭接半个物理像素，保持共同圆心和外径。图标使用同一个原生矢量画笔，保留真实曲线 bounds、几何和覆盖抗锯齿。图片圆角走新裁切，位图来源和采样品质规则沿用既有路径。 [材质](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/shared/src/main/java/com/atom/chat/render/ChromeMaterial.java) · [头像](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/shared/src/main/java/com/atom/chat/render/AvatarRing.java) · [图标](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/shared/src/main/java/com/atom/chat/render/OutlineIcons.java)
- HUD 通知的矢量绘制和 GL 遮罩共用规范化位置，旧卡不会因入列改变像素相位。测试保留原来的逐像素限制。底部栏的折叠淡出跨到满半径时会切换为原生胶囊。独立审查发现并复核了胶囊烘焙、关闭遮罩、裁剪暗幕和小尺寸边缘等修正。
- 三端全量执行 6,687 项含 56 跳过。档案保留 5 项初次失败。修正探针的测试替身和入场像素用例的时钟、配置夹具后，三端各补跑 13 项，零失败。这次补跑与全量之间的生产源码相同，三端均完成 `build`、`verifyAtomLibInput` 和 JAR CRC 核对，开发构建跳过正式设置契约两项。30 张生成对照入档，原参考按字节校验后恢复。 [产物及验证清单](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/continuous/artifacts.json) · [参考图恢复记录](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/continuous/reference-restore.json)
- 最终候选 `ea121f9b` 先于 `733e704f` 跑同一 RTX 5060 Laptop 隔离 CCB，1600×900、UI 1.25×、Bliss、双 200% 模糊、同一份预置文字与图片。本组 11,891 帧。新旧文字 p50 为 6.212／5.263 ms，图片 5.647／5.435 ms，关面板的前后窗口中位数分别相差 +0.314、+0.546 ms。简单减去关面板差值平均数后，文字约 +0.520 ms，图片约 −0.217 ms。一次顺序配对可报告实际差，无法把差值全归给某一个渲染改动。图片内容的 18,042 个 RGB 取样一致。 [实机 1:1 对照](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/continuous/panel-compare.png) · [完整帧时统计](C:/Users/NIUQU/.codex/worktrees/atomchat-v093/AtomChat-Main/build/v094/continuous/game-results.json)
- 14:16 将同一份 `ea121f9b` 备份部署至主 `1.21.1-CCB`。已回读两个活动包和四份备份；AtomLib 继续为 0.7.0。主实例在换包时关闭，功能与外观实跑使用隔离 CCB。 [部署回执](D:/AtomChat-Backups/2026-11-21-v094-continuous-ccb/20261121-141614-920684/deployment.json)

## 连续曲率的稳态帧耗时

在 `ea121f9b` 的同进程诊断里，热态继续命中 Skija 矢量轮廓缓存，后几段没有轮廓缓存未命中。将连续曲率裁切暂时替换为原生圆角裁切，后段文字页 p50 比相邻基线均值低 0.392 ms。仅换填充和描边的对应差值是 0.154 ms。普通圆角可命中 Skia 的解析裁切，含三次曲线的路径会进入通用裁切，其中面板外影的差集每帧覆盖大范围。试样在同一 GPU 上单测一次面板阴影，原路径与像素对齐的 A8 试样 CPU 提交 p50 为 0.177／0.038 ms，GL 时间戳区间 p50 为 0.044／0.007 ms。整帧证据取自游戏。 [同进程诊断](../../build/v094/curve-perf/curve-perf-d1/stats.json) · [原生阴影读数](../../build/v094/curve-perf/full-shadow-original-matrix.log) · [Skia Ganesh 裁切实现](https://skia.googlesource.com/skia/+/d2b9e48baf1697760afc1dc8ea3ad40110b8cacc/src/gpu/ganesh/ClipStack.cpp)

固定 3 倍九宫格直接预镂空时，分数相位下直边 alpha 可偏移 16。最终实现对静止、已像素对齐且不高于 2 倍设备密度的活动面板，以之前的高斯阴影和连续曲率裁切生成一张设备像素尺寸的 A8 遮罩。每帧在设备坐标按 1:1 绘制一次。缓存为一个槽，遮罩上限 1 MiB，编辑、开合、录制、超界和非对齐情形走原实时裁切。轮廓、线宽、软影参数、文字与源图片采样保持原设置。独立审查促成了三端面板编辑器的回退准入，回归覆盖 1.25 倍阴影像素、2 倍缓存复用、2.5 倍回退和原生对象替换释放。 [生产入口](../../shared/src/main/java/com/atom/chat/render/SoftShadow.java#L109) · [原生像素测试](../../shared/src/test/java/com/atom/chat/render/SoftShadowTest.java#L127)

三端全量与最终受影响用例合并统计为 6,693 项，56 跳过，零未解决失败。Forge 单独选中既有编辑器测试时出现生命周期外的 Minecraft 注册表未初始化，同一测试在全量通过，最终 Forge 重跑与本改动直接相关的 31 项，Fabric 和 NeoForge 各 50 项。三端开发包通过构建、AtomLib 输入、JAR CRC、目标表和裸值校验，开发构建继续跳过两项正式设置契约。已归档 30 张新生成的历史位置输出，并按字节恢复原参考。 [构建和用例统计](../../build/v094/curve-perf/artifacts.json)

最终 JAR `3d541f82` 用独立夹具在同一 CCB 进程只切换阴影快路径。RTX 5060 Laptop、1600×900、UI 1.25 倍、Bliss，双 200% 模糊，9,953 帧。相邻基线配对的两段文字 p50 分别减少 0.413、0.291 ms，图片分别减少 0.342、0.819 ms。靠后的文字段 p50 从 5.776 降至 5.485 ms，p95 从 8.298 降至 7.985 ms。图片内容的 18,042 个 RGB 通道组逐像素一致。此前一次夹具启动把辅助类误放在 Mixin 专用包，移到独立包后，同一个未改动的生产 JAR 完整跑完。15:04 已备份替换主 CCB，并回读活动包、备份和 AtomLib 0.7.0。 [最终帧时](../../build/v094/curve-perf/curve-perf-release/summary.json) · [改前截图](../../build/v094/curve-perf/curve-perf-release/base-2.png) · [改后截图](../../build/v094/curve-perf/curve-perf-release/exact-2.png) · [部署回执](D:/AtomChat-Backups/2026-11-21-v094-shadow-ccb/20261121-150458-326560/deployment.json)

## 点阵降耗与像素对齐

- 复测比较已验收的连续曲率前包 `733e704f` 和当时的 CCB 包 `3d541f82`。顺序为旧、新、新、旧，四次使用同一探针、1600×900、1.25× UI、同一 Bliss 光影、面板与卡片均 200% 模糊、同一视角和 32 条预置消息。两对文字 p50 增量分别为 0.613 和 0.014 ms，图片为 0.302 和 0.260 ms，前后关面板 p50 差在 −0.006 至 +0.021 ms 内。 [反序对照](D:/AtomChat-Backups/2026-11-21-v094-dots-ccb/evidence/paired.json)
- 面板点带和小标题原本回放 Skija 矢量命令。原生 RTX 5060 隔离夹具中，两条面板点带加一条标题的提交 p50 为 0.566 ms，去掉点、保留发光后是 0.045 ms。面板稳态将同一发光及裁切同小圆点一起缓存为设备像素 A8，8 槽每槽至多 128 KiB。回贴使用原染色，每帧每带一次，微小圆点向像素中心对齐，大圆点保留原相位。动效、面板编辑、录制和高于 2× 密度延用原矢量绘制。折叠标题保留矢量，在页面静止时将录制矩阵和实际回放矩阵合成，只对齐列方向。滚动方向连续，冻结的退出页面继续播放同一份矢量快照。 [原生分项](D:/AtomChat-Backups/2026-11-21-v094-dots-ccb/evidence/dot-probe.log) · [原尺寸截图](D:/AtomChat-Backups/2026-11-21-v094-dots-ccb/evidence/dots-1x.png)
- 同进程只开关点阵改动的诊断共 16,379 帧，文字 p50 降 0.437–0.701 ms，图片降 0.422–0.574 ms。最终包跟连续曲率前包同场景直接对照共 17,347 帧。文字 p50 / p95 从 3.302 / 5.110 降至 2.672 / 4.375 ms，图片从 2.869 / 4.439 降至 2.567 / 3.882 ms。关面板两段 p50 分别变化 −0.012 / +0.008 ms。图片区域 18,042 像素 RGB 一致。最终包的 1.25× 外观页实际命中两个吸附标题，合并切换面板点带和标题处理的 p50 少 0.212 ms。 [同进程诊断](D:/AtomChat-Backups/2026-11-21-v094-dots-ccb/evidence/dot-retune-live/summary.json) · [最终包直接对照](D:/AtomChat-Backups/2026-11-21-v094-dots-ccb/evidence/final-pair.json) · [标题实机命中](D:/AtomChat-Backups/2026-11-21-v094-dots-ccb/evidence/title-live.json)
- 三端全量和最终受影响复测合并 6,699 项，56 项跳过，零未解决失败。全量一次发现旧调用签名的点带层序断言，更新签名后仍检查相同顺序。专项像素夹具改为从真实 A8 texel 核对合成颜色与遮罩 alpha，裁切测试继续独立执行。最终三端各 63 项专项、AtomLib 输入和 JAR CRC 校验通过。开发构建跳过两项正式设置契约。18:12 CCB 备份部署 `bf40ad1f`，原配置与 AtomLib 0.7.0 保留。 [构建记录](D:/AtomChat-Backups/2026-11-21-v094-dots-ccb/evidence/artifacts.json) · [部署回执](D:/AtomChat-Backups/2026-11-21-v094-dots-ccb/20261121-181204-166273/deployment.json)
