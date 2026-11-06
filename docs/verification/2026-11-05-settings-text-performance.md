# 设置页与文字密集页性能审计（2026-11-05）

## 结论

设置页的反差可以由内容规模直接解释一部分：同一轮 CCB 日志里，轻负载 `panel` 为 p50/p95 `2.12/2.68 ms`、分配 `24.7 KB/帧`；进入设置内容后，`other_render` 的 p50 最高到约 `2.6 ms`，滚动窗口分配一度升到 `300+ KB/帧`。这不是单一 Skija bug，也不是 AtomLib 的 Yoga：AtomChat 当前设置页使用手写 `UiLayout`，没有实例化 AtomLib 的 `YogaLayoutEngine` 或 `TextService`。

本轮抓到并修复两条确定性热路径：

1. `SettingsSectionPage.rowRect(index)` 每次都从第 0 行重走前缀，而每行走一次 `rowAdvance -> rowHeight -> compactText`。80 行测试在旧实现上实测 `3,240` 次文字块测量，正好是 `80×81/2`；线性几何缓存后降为 `80`。
2. `SkiaFontRenderer.wrap/wrapWords` 每帧对相同文本和宽度重新分配行列表。现在使用有界 LRU，与已有宽度/塑形缓存相同口径；形状缓存超过 131,072 字符时也不再创建无法回收的 `TextBlob`。

这解释了文字越多分配越高的部分，但不能把全部掉帧都归因于文字。每张设置卡仍独立绘制一个高斯阴影；离线 CPU 光栅微基准中，60 个阴影耗时 p50 `1.105 ms`，40 段文本仅 `0.045 ms`。剩余 GPU 侧成本仍应以新包实测日志区分，不能从 CPU 分段直接推断。

## 用户日志证据

原始日志快照：`build/stage3-ac-20261105/latest-user-stage2.log`，SHA-256 `08062C420191D4813EAA77FBF42DB6247109EF9532B86DFA14127359E053626B`。

- `13:45:28` 发送消息后：`panel p50/p95 2.12/2.68 ms`、分配 `24.7 KB/帧`，`DRAW 1.161/1.567 ms`、`BRIDGE_ENTER 0.443/0.535 ms`。
- `13:46:04` 设置内容开始出现独立 `other_render` 样本：p50 `1.94 ms`、p95 `4.09 ms`、分配 `23.1 KB/帧`；紧接的交互窗口分配升至 `170.4 KB/帧`，随后一窗口为 `302.4 KB/帧`。
- 稳定设置窗口大多保持 `other_render p50 1.9–2.6 ms`，但分配长期在 `140–320 KB/帧` 波动，与“卡片/文字数量越多分配越大”一致。
- 用户同场体感：空聊天约 300 FPS，发送图文消息后略升；表情面板短时下降后恢复；设置文字多的 tab 约 150 FPS，两张卡片的 tab 可到 330 FPS。CPU 分段没有显示同等比例的持续增长，因此 GPU 阴影/混合和帧外成本仍不能排除。

## 代码审计

| 优先级 | 位置 | 事实 | 本轮处理 |
| --- | --- | --- | --- |
| P0 | `platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsSectionPage.java:1090`、`:1199`、`:1283` | 行矩形和折叠组归属都曾按行号重扫前缀；绘制、卡片 union、命中测试重复触发 | 一次构建 group/height/top 数组，后续 O(1) 取矩形；组展开值变化时失效重建 |
| P0 | 同文件 `:614`、`:1242` | 同一行在同一帧的 measure/draw 多次进入 `compactText`，重复宽度判断和换行 | 行几何保存 `CompactText`；同帧同宽度直接复用 |
| P1 | `shared/src/main/java/com/atom/chat/render/SkiaFontRenderer.java:747`、`:797`、`:804` | `wrap`/`wrapWords` 每次调用都重新分配字符串和列表 | 有界 LRU，键含 Font 身份、文本、宽度和算法；同帧及未变帧复用 |
| P1 | 同文件 `:674` | 超过塑形缓存字符上限的文本仍创建原生 `TextBlob`，但因不缓存而每帧泄漏一个 blob | 超限文本走 `drawString` 兼容路径，不创建不可回收 blob |
| P2 | `shared/src/main/java/com/atom/chat/render/SkiaFontRenderer.java:41` | 文本塑形缓存上一轮已存在 | 保持 2,048 条 / 131,072 字符上限，淘汰关闭 `TextBlob` |
| P2 | `platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsSectionPage.java:1329` 附近 | 入场时逐行 `saveLayer` | 保留；动画结束后不建层，视觉语义不删 |
| P2 | `D:/AtomLib/ui/src/main/java/com/atom/lib/design/atomchat/UiCards.java:92` | 每张设置卡独立高斯阴影 | 保留；离线 CPU 成本可测，但本轮没有足够 GPU 证据支持移除或近似 |

### MuoniumPlayer 对照

对照 clone：`D:/MuoniumPlayer-multiversion`，commit `c602189862d36a45965839009454172eafad60d6`。其实现与本轮方向一致：

- `muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:79`、`:645`、`:722` 使用按文本缓存 `TextBlob`，并把字形累积成一个 blob，避免逐字符 draw 调用。
- `muonium-common/src/main/java/com/muoniumplayer/render/skija/SkijaImages.java:76` 将 MC 已上传的 GL 纹理借给 Skia 为 `Image`，按纹理 id/尺寸复用视图；封面更新不需要重新解码或重建 Skia 纹理。
- `muonium-common/src/main/java/com/muoniumplayer/render/skija/SkijaEffects.java:283` 将背景模糊做成“每次采集预糊一张”，逐元素玻璃只贴预糊图；逐元素现挂高斯 filter 被明确列为昂贵路径。
- `muonium-common/src/main/java/com/muoniumplayer/core/screens/ncm/NCMScreen.java:205`、`:253` 用 dirty 标记控制整棵控件树布局，转场期间延迟补建；这只证明布局惰性化值得借鉴，不能说明它的绘制是静态缓存。

这些证据支持两条后续方向：高成本模糊/阴影必须整层预生成而不是逐元素现场生成；封面/头像类资源应继续复用已上传纹视图。AtomChat 的图片缓存和 TextBlob 缓存已经接近这一路线，当前最明显的差异仍是设置页逐卡高斯阴影与文本密集卡片数量。

## 验证

- 红灯：同一份源码、同一测试，80 行连续 `rowRect` 触发 `3,240` 次 `compactText`，断言期望 80，失败。
- 绿灯：改为每帧线性几何后，80 行触发 80 次并全部通过。
- 换行测试：相同 Font、文本、宽度连续调用返回同一缓存列表，塑形计数不再增长；原有逐像素 Latin/CJK 测试继续通过。
- 隔离源码三端全量构建与测试：Forge `1653`、Fabric `1668`、NeoForge `1653`，失败 `0`、错误 `0`，各 `6` 个既有 skip。
- `python tools/verify_targets.py` 通过；三端 `verify_jars.py --profile atomlib-consumer` 全部通过；`git diff --check` 通过。
- 离线 Skia 光栅基准（420×820，80 帧）：全套 p50 `2.257 ms`，其中 60 阴影 p50 `1.105 ms`、40 文本 p50 `0.045 ms`；说明重复文字塑形/换行可先修，但不能据此断言 GPU 阴影是唯一剩余原因。

## 部署

三端候选与回执：

| 实例 | JAR SHA-256 |
| --- | --- |
| `D:/Myworld/.minecraft/versions/1.20.1-main/mods` | `85699cf36e9e74b5be176ddfbc2c2ffa57e78a753b7dfc8e4f5acab2e9c52e45` |
| `D:/Myworld/.minecraft/versions/1.21.1-CCB/mods` | `2a038ec074b147b66f450079be960e5fc4c1e9cb5b3e743ac3e275e408cb657b` |
| `D:/Myworld/.minecraft/versions/Mechanomania-航空学/mods` | `efb9b853fe10d2b4820eeb40df3db89c26d85eaed308a6c3f9284bcf4e103da3` |

回执：`build/stage3-ac-20261105/deployment-receipt.json`。三份旧包均备份并回读哈希；其他 mod 条目未变。检查时另一个无关 MC 实例仍在运行，但三个目标实例均未被任何进程加载，因此只替换了非运行中的目标包。未启动目标游戏、未 commit、未 push、未发布。

## 剩余风险

- GPU 侧仍没有修复后的实机数据。下一步只用正常启动后的 `latest.log` 对比同一组页面的 `other_render` p50、分配和帧率；若分配下降但 FPS 仍按卡片数量下降，重点转向逐卡阴影/混合，而不是继续加文字缓存。
- 首次头像皮肤 GL 读回仍可能产生单次几十毫秒尖峰；它不是设置页持续 150 FPS 的解释。
- 外部 EMF/Sodium/Iris 或全景背景仍可能改变 GPU 结果，不能把整帧 FPS 全部归到 AtomChat。
