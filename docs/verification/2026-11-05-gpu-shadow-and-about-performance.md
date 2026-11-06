# 2026-11-05 GPU 阴影与关于页性能续修

## 结论

本轮没有再发现 GlStateScope 回退。CCB 修复后日志中，聊天页 panel p50/p95 约 1.8–2.1 / 2.3–3.1 ms，设置内容 other_render p50 约 1.8–2.9 ms；因此页面之间数倍的 FPS 差不能用 AC 的 Java 绘制分段直接解释。

代码与实测把问题拆成两部分：

1. **AC 自身的确定性浪费**：关于页“清理图片缓存”卡片每帧调用 ImageLoader.diskCacheBytes()，后者每帧重新枚举目录并取每个文件大小；设置页无参数翻译也每帧重新构造 Minecraft 文本组件。
2. **AtomLib 的 GPU 图元浪费**：每张卡片每帧都重新执行一次 MaskFilter 高斯阴影。内容页卡片越多，重复模糊越多，与“聊天设置第一个 tab 慢、第二个 tab 回升、关于页最低”的现象一致。

本轮修复了这两类问题。三端新包已经备份部署；实际 FPS 仍需用户启动游戏验收。当前日志不能证明 Skija 本身有性能 bug，MuoniumPlayer 的只读对照也没有发现可解释 AtomChat 数倍差距的上游缺陷。

## 用户现象与证据

CCB latest.log SHA-256：A946303E05DEB2021FCEB6C334D6D5AF6C4D36C3EF2879C6139F9D9A8A6AEA24。

- 聊天页面板约 270 FPS；发送文本、表情或图片后约 330 FPS。
- 导航页聊天列表在单人模式只有一个公屏卡片时约 250 FPS。
- 设置导航四个卡片约 370 FPS。
- 聊天设置第一个内容多的 tab 约 200 FPS；第二个少内容 tab 回升到 300+ FPS。
- 关于页最低，部分时刻低于 100 FPS。

日志显示 GL capture 每面板帧仅一次。问题不在旧的 192 单元状态查询路径。页面 CPU 段不高而 FPS 明显下降，说明剩余成本发生在 Skia 提交、GPU 模糊/合成或页面外提交链。

## 修复

### 1. 图片缓存大小不再每帧扫盘

SettingsSectionPage.actionCopy() 在关于页每帧读取清理图片缓存卡片的副标题；旧实现直接调用目录枚举。

现在 ImageLoader.diskCacheBytes() 缓存结果 1 秒；加载器自身写入、裁剪和清理会立即刷新缓存。目录外部的变化最多延迟 1 秒显示，不改变清理动作。

- shared/src/main/java/com/atom/chat/image/ImageLoader.java:94
- shared/src/main/java/com/atom/chat/image/ImageLoader.java:516
- shared/src/test/java/com/atom/chat/image/ImageLoaderTest.java:238

### 2. 无参数翻译按资源代复用

设置页绘制路径反复调用 Text.translatable(key).getString()。现在无参数 key 使用有界页面缓存，资源重载时清空，语言包或资源包切换后不会留下旧文案。

- platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsSectionPage.java:572
- platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsSectionPage.java:585
- platforms/1.21.1-fabric/src/main/java/com/atom/chat/AtomChatClient.java:75
- 官方映射孪生同步：layers/mapping/official/src/main/java/com/atom/chat/settings/SettingsSectionPage.java

离屏同页 300 帧结果：

| 页面 | 修复前分配 | 修复后分配 |
|---|---:|---:|
| 外观 | 79.1 KB/帧 | 52.6 KB/帧 |
| 聊天 | 46.9 KB/帧 | 26.4 KB/帧 |
| 隐私 | 14.4 KB/帧 | 10.6 KB/帧 |
| 关于 | 30.2 KB/帧 | 18.1 KB/帧 |

### 3. 卡片阴影预烘焙后单次贴图

SkiaDraw.drawRoundedShadow() 供设置卡片、列表卡片、作者卡片和关于页 logo 共用。旧路径每帧对每个相同矩形重新执行 MaskFilter blur。

现在按宽、高、圆角、blur、颜色将阴影预烘焙成 2× 透明纹理，稳定帧只做一次 drawImageRect；最多缓存 48 个尺寸/颜色组合，LRU 淘汰时关闭原生 Image。系统属性 atomlib.shadow.direct=true 保留诊断直画路径。

- ui/src/main/java/com/atom/lib/design/atomchat/SkiaDraw.java:42
- ui/src/main/java/com/atom/lib/design/atomchat/SkiaDraw.java:87
- ui/src/main/java/com/atom/lib/design/atomchat/SkiaDraw.java:120
- ui/src/test/java/com/atom/lib/design/atomchat/SkiaDrawShadowCacheTest.java

离屏 420×820 画布、60 个同尺寸卡片阴影、80 帧：

| 路径 | p50 |
|---|---:|
| 每帧 MaskFilter 直画 | 1.154 ms |
| 预烘焙后贴图 | 0.138 ms |

这是 CPU 光栅对照，不是玩家 GPU 帧时间。

缓存与直画在 180×100 测试画布上的最大通道差为 1/255；带透明度的页面入场阴影测试仍通过，尾影没有改变读数范围。1× 版本在最低入场 alpha 下会把测试阈值读数从 9px 推到 6px；2× 预烘焙后恢复稳定。

## MuoniumPlayer 对照

只读审计对象：D:/MuoniumPlayer-multiversion，main@c602189862d36a45965839009454172eafad60d6，版本 2.3.1。

- Muonium 复用 Surface，但每帧仍清空并重画组件；没有整页像素缓存。
- 布局由 dirty 状态触发，转场期间延后补账。
- 背景模糊有 30Hz 采集、4 档半径和有限重建预算；开合动画期间会退回实色表面。
- 普通卡片没有跨帧投影纹理缓存；封面 bloom 仍走 saveLayer + blur。
- 文字路径逐 UTF-16 char 构建 TextBlob，带 256 条 LRU；不是 AtomChat 现役复杂文字塑形的等价替代。

结论：Muonium 更快并不能归因于某个尚未发现的 Skija 全局 bug。可借鉴的是“静态层预生成、动画期间限制昂贵层”的原则；本轮把该原则用在卡片阴影，并保持现有视觉与动画语义。

完整子代理对照结论见线程 01a1aa50-c80d-7af0-8ba1-829f98fe1848。

## 验证

干净隔离源码：D:/AtomChat-Main/build/stage5-ac-20261105/source。

| 平台 | 测试数 | 失败 | 错误 | Skip |
|---|---:|---:|---:|---:|
| Forge 1.20.1 | 1654 | 0 | 0 | 6 |
| Fabric 1.21.1 | 1669 | 0 | 0 | 6 |
| NeoForge 1.21.1 | 1654 | 0 | 0 | 6 |

通过：

- 三端 build
- python tools/verify_targets.py
- 三端 verify_jars.py --platform windows-x64 --profile atomlib-consumer
- git diff --check
- 候选包含新阴影缓存、图片缓存和设置翻译缓存 class

## 部署

候选目录：D:/AtomChat-Main/build/stage5-ac-20261105/candidates/。

AtomChat SHA-256：

- Forge：6386108a13d9c4d4b3b37ea5716f400e0752c5c864b7e8780e8f619741c953bd
- Fabric / CCB：c110bd0dc72d43b66d50ef4594b0c6200207507cba1693d0d65025bb6ee248db
- NeoForge：05a1a16069a9be8303a3534c804592ebe0e115a11883e68c6fe41c5ad818ca6c

配对 AtomLib 0.3.1-shadowcache SHA-256：

- Forge：1ef1aae132572e2888b2aa1ad5ed27f91c4a6438f41eefe62a391b5ed9b843bc
- Fabric：76c4ba402332cdd3e61243166ef2098cb8db27e1a9a31a9e8193182df7ce0f96
- NeoForge：8875736a009bc756456d1ab7c55f50822cdfa23a5fefeadfceeaf73a5752b663

回执：build/stage5-ac-20261105/deployment-receipt.json。

部署前三个目标实例均未运行；没有启动游戏。旧 AtomChat 和旧 AtomLib 均已备份，其他 mods 条目哈希未变。未 commit、push 或 publish。

## 下一轮验收

用户只需正常启动 CCB 并重复同一条路径：聊天页 -> 设置导航 -> 聊天设置两个 tab -> 关于页。随后由代理直接读取 latest.log 对比 panel、other_render、DRAW、DOTS 和分配量，不需要再跑脚本或 JFR。

若关于页仍低于 100 FPS，而 other_render CPU 段继续低于 3ms，下一刀应转为 GPU 计时和关于页静态层 A/B（logo 阴影、文字软阴影、静态卡片层），不再继续优化换行或 GL state 查询。
