# Muonium 对照落地与剩余 GPU 路径（2026-11-05）

## 结论

本轮把两条问题分开：

1. **持续低帧**：设置/关于页的主要已证实缺口不是跨帧背景缓存，而是 About hero 的大面积点阵每帧提交大量圆和一次模糊光晕。真实隐藏 GL 同场景中，改为静态点阵纹理后 p50 `0.910ms -> 0.072ms`，面板样不透明背景最大通道差 `1/255`。阴影缓存稳定命中，但同机 A/B 没有测到 GPU 墙钟收益；它仍保留，因为此前 CPU 光栅收益与分配下降成立。
2. **首次冷尖峰**：个人页首次同步 `glGetTexImage` 已改为有界 PBO + fence 异步读取。它只解决首次/缓存冷启动，不解释设置页持续 150 FPS。

用户已反馈“Euler 的修复项都有效”，但尚未把反馈绑定到具体最终包；因此本文不把它扩写成 stage5 固定场景验收。

## 基线与完整历史

- 完整会话正本：[index.json](2026-11-05-agent-handoff/index.json:1)、[Euler](2026-11-05-agent-handoff/euler-conversation.md:1)、[Muonium 调研](2026-11-05-agent-handoff/muonium-research-conversation.md:1)。两份文档保留 45/13 条用户与助手消息及原始 JSONL 路径。
- 现役部署基线是 stage5：AC 提交 `e92fdaf`、Lib 提交 `56f1b95`；回执 [deployment-receipt.json](../../build/stage5-ac-20261105/deployment-receipt.json:1)。部署前 WMI 无游戏进程，三端包与配对 Lib 已备份并验哈希。
- `latest.log` 已冻结为 [latest-pre-resume.log](../../build/muonium-followthrough-20261105/evidence/latest-pre-resume.log)，SHA-256 `A946303E05DEB2021FCEB6C334D6D5AF6C4D36C3EF2879C6139F9D9A8A6AEA24`，最后写入 `14:26:04`，早于 `15:19` 的 stage5 部署，只作修复前基线。
- 该日志滚窗：`panel p50/p95=1.84/2.72ms`、`other_render=1.85/2.48ms`、`BLUR` CPU 段 `0.153/0.284ms`、`DOTS=0.708/0.886ms`、`DRAW=0.962/1.168ms`。这些是 Java 提交墙钟，不是 GPU 完成时间。

## 七项判定

| 项目 | 判定 | 当前证据与处理 |
| --- | --- | --- |
| 业务脏标记、布局签名、转场延后补账 | 已落地，继续保护 | AC 设置 measure/draw 同帧复用、`rowRect` 已线性化；会话列表 250ms 行缓存；Lib Yoga 按 revision/snapshot 复用。本轮没有新增布局重构。 |
| 固定行虚拟化、视口剔除 | 仅候选 | 消息页已按视口绘制剔除；会话/设置规模与持续低帧场景没有证据支持继续加索引，避免为非等高或小页面硬加虚拟化。 |
| 分代背景模糊、半径槽、更新预算 | 需 GPU 实测，当前不改 | 设置页正常路径每帧只调用一次 blur；执行 1 次主画面 blit、5 个全分辨率 Kawase pass、1 次圆角合成，无 CPU 回读。没有发现同帧重复。新鲜背景排除跨帧旧图，但同帧共享/GL 原语优化仍是候选；缺少 AC 原方法的 GPU 完成时间，不能宣称预算优化有效。 |
| 按显示尺寸降采样、图片/上传预算 | 已存在 | `ImageLoader.MAX_DIM=768`、源像素上限、动画单图/总像素预算与条目 LRU 均在现役代码；本轮不扩大图片改造。 |
| 复用 Lib TextService/revision | 不迁移 | AC 的富文本、caret、Emoji/回退和自绘分隔段依赖现有 `SkiaFontRenderer`；现役已有 2,048 条/131,072 字符 TextBlob 缓存和换行 LRU。没有证据表明整条文字链迁移能抵过 caret/富文本风险。 |
| 静态层预生成 | 部分落地 | 卡片阴影缓存稳定命中：120 帧后仍只有 1 个 key。About hero 点阵改为大区域静态纹理，受 8 条 LRU 与 32MB 字节预算限制；小区域继续走逐字节一致的 Picture/direct。 |
| 皮肤首次读回 | 已修冷尖峰 | 新增有界 PBO/fence 读取，下一帧零超时轮询；8 个/96MB 上限、2 秒过期、map/unmap 失败丢弃、PENDING 不写永久失败。 |

## 真实隐藏 GL A/B

探针：[AboutGpuBenchmark.java](../../shared/src/test/java/com/atom/chat/render/AboutGpuBenchmark.java:1)，RTX 5060 Laptop，480x860，每档预热 20、采样 80，末尾 `glFinish`。这是受控 GPU 墙钟，不等同游戏总帧率。

| 场景 | p50 | p95 |
| --- | ---: | ---: |
| 10 卡阴影缓存，120 稳定帧 key 仍为 1 | 0.096ms | 0.198ms |
| 10 卡原 MaskFilter 直画 | 0.097ms | 0.129ms |
| About hero 点阵直画 + 静态卡片 | 0.910ms | 1.135ms |
| About hero 生产静态图 + 静态卡片 | 0.072ms | 0.144ms |

结论：阴影缓存在该场景没有可测 GPU 降时，不能把 stage5 的 CPU 光栅 8.4 倍收益当作 FPS 结论；保留缓存不伤语义。About 大点阵有明显收益，因此才改生产路径。静态图的稳定 key 为宽、高、tint、density；面板样不透明背景实测最大差 `1/255`。

## 代码事实

- 阴影 key 为宽、高、圆角、blur、颜色；入场透明度在外层 `saveLayer`，不污染 key。见 [SkiaDraw.java](../../../AtomLib/ui/src/main/java/com/atom/lib/design/atomchat/SkiaDraw.java:62)。
- About halo 大于 40,000 逻辑平方像素才烘焙；4x 静态图 LRU 最多 8 条，淘汰和清理关闭原生 Image。见 [DotTexture.java](../../shared/src/main/java/com/atom/chat/render/DotTexture.java:33)、[分支](../../shared/src/main/java/com/atom/chat/render/DotTexture.java:199)、[构建](../../shared/src/main/java/com/atom/chat/render/DotTexture.java:239)。
- 模糊入口每帧一次，pass 循环与全分辨率缓冲见 [AtomChatScreen.java](../../platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java:1981)、[PanelBlurRenderer.java](../../platforms/1.21.1-fabric/src/main/java/com/atom/chat/render/PanelBlurRenderer.java:349)。`full=true` 不是遗留空逻辑，而是当前“每帧新鲜背景”的产品约束；同帧重复调用并未在设置页路径出现。
- PBO 入口、零等待 fence、过期清理与状态恢复见 [SkinTextureReader.java](../../shared/src/main/java/com/atom/chat/image/SkinTextureReader.java:48)、[poll](../../shared/src/main/java/com/atom/chat/image/SkinTextureReader.java:166)、[expire](../../shared/src/main/java/com/atom/chat/image/SkinTextureReader.java:247)；Fabric 与官方映射的调用点分别见 [AvatarRenderer.java](../../platforms/1.21.1-fabric/src/main/java/com/atom/chat/image/AvatarRenderer.java:129)、[官方映射](../../layers/mapping/official/src/main/java/com/atom/chat/image/AvatarRenderer.java:127)。

## 验证

- `AvatarReadbackWiringTest`：8/8；覆盖首调 PENDING、下一帧 READY、标准/HD 皮肤、污染 PACK/PBO 状态、9 张满载不饥饿、旧请求不再绘制后的过期回收。
- `DotTextureCacheTest` + `DotTextureTintContrastTest`：10/10；小区域维持逐字节一致，大 halo 静态层限制 8 LSB 内，重复帧只构建一次并关闭缓存。
- About GPU A/B：`gpu-about-ab-final.log` 与本目录 XML；同机、同窗口、同内容、同顺序。
- PBO reviewer：首位 reviewer 抓到容量饥饿、时钟超时和 unmap 失败，均已修正；最终增量复核关闭 P1。DotTexture 初次复核因 429 中断，重试后指出单图可突破 32MB；已在分配前拒绝超预算单图并补测试，未再声称该最后一行经过第二遍独审。
- `tools/verify_targets.py` 通过；使用 stage5 隔离锁并临时移开旧 `v0.4.0` 构建产物后，三端 `verify_jars.py --platform windows-x64 --profile atomlib-consumer` 全部通过，随后工作区 `versions/atomlib.json` 已按字节恢复为原 SHA-256 `41C8A95B…0216`。

最终三端完整 `build` 均成功：Fabric `1m17s`、Forge `1m45s`、NeoForge `1m27s`。当前各目标源集统计分别为 Fabric `1563/0/0/4`、Forge consumer 套件 `1661/0/0/7`、NeoForge `1551/0/0/4`（测试/失败/错误/skip）；计数差异来自各加载器当前 test source set，不能与 stage5 隔离快照的旧总数直接横比。

## 部署

`2026-11-05 16:31:21 +08:00`，WMI 确认目标 Minecraft 进程为空后，只替换 AtomChat；配对 AtomLib `0.3.1-shadowcache` 未变。每端旧包均备份、候选与安装包回读哈希一致；Forge/NeoForge 其他 mods 条目哈希/大小/时间不变。

- Forge：`38d171e8a4d5f7849de1f06b7a4ca93f545d3856dffa761eceebad5374bee082`
- Fabric：`cad851ad32386d3a58e3f08e9ba3aa88bb90437bf18363e8bb4091df473136c2`
- NeoForge：`38b8f346396ae277e2b6b555dfeae523a356c51ef10d4ae58fc76bc8c82db47e`

回执：[deployment-receipt.json](../../build/muonium-followthrough-20261105/deployment-receipt.json:1)。三端 JAR 均确认包含 `SkinTextureReader$State`、`SkinTextureReader$Result`、`DotTexture$HaloKey`。CCB 目录另有一个外部下载器当时锁定的 `未确认 498523.crdownload`，未触碰，回执没有把它伪报为“未变”；AtomChat 与配对 AtomLib 哈希已验证。未启动游戏、commit、push 或发布。

## 仍缺

- AC `PanelBlurRenderer` 原方法的 GPU 完成时间仍缺；现有日志的 BLUR 只是 CPU 提交。要验证 5 pass、state 查询或半分辨率候选，需要下一次统一验收日志中的 GPU timer 或等价受控探针。未测前不改 blur。
- 用户反馈“Euler 修复有效”，但具体最终包未绑定；stage5 固定页面验收仍不能宣称完成。
- 统一验收只走一次：聊天 -> 设置导航 -> 聊天设置两 tab -> 关于 -> 个人页，区分首次/重复；先确认加载包身份，再读同一 `latest.log`。
