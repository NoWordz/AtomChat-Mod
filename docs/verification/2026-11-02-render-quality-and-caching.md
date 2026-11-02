# 渲染锐度定因 + 性能/内存审计（2026-11-02）

版本仍为 `0.4.0`。本轮回答"为什么看起来糊"，并把审计出的重复计算与内存增长修掉。

## 一、"糊"的定因：三把尺子，两个结论

三把探针都在离屏表面上跑，不改产品代码：

| 探针 | 量什么 | 运行方式 |
|---|---|---|
| `tools/probes/TextSharpnessProbe.java` | 文字：10%→90% 边缘过渡宽度、半覆盖像素占比、与 4× 超采样真值的误差；含密度扫描 | `python tools/run_text_sharpness_probe.py` |
| `tools/probes/ColorSpaceProbe.java` | 颜色空间 / 像素几何 / 是否 device-independent 对 AA 混合的影响 | `python tools/run_colorspace_probe.py` |
| `tools/probes/ImageScaleProbe.java` | 图片降采样：各策略与精确面积平均的误差、高频能量保留 | `python tools/run_image_scale_probe.py` |

### 1.1 文字已在物理底线，调 hinting 会更差

会话真实密度 1.4815（1600 高 / 1080），字号 `FONT_BODY = 25px` → 37 设备像素。

```
text variant               transition     mid%    error
4x 超采样真值（控制组）          2.086    44.94        —      <- 理想下限
subpixel+none（现状）           2.112    38.77    2.097
no-subpixel+none                2.085    38.63    4.860
no-subpixel+slight/normal/full  2.085    38.63    4.861
```

- 现状 2.112 与理想 2.086 只差 1.2%，说明**文字栅格化没有可回收的锐度**。
- 关掉亚像素定位 + 开 hinting 只把过渡宽度压到 2.085（0.03px，肉眼不可见），却让字形误差**翻倍**（2.10 → 4.86）——hinting 会把字形往整像素网格上掰，形状失真。
- 密度扫描单调改善（d=1.0 时 mid% 52.1 → d=2.0 时 30.0），但这是「分辨率越高越锐」的常识，不是可调参数。

**结论：文字侧不改任何设置。** 这排除了"光栅/字体设置"这个假设。

### 1.2 颜色空间与 AA 混合：无收益，且亚像素 AA 更差

```
=== 深底浅字 ===
case                           transition     mid%      ink
sRGB surface (current)              2.815   100.00     84.8
legacy (null space)                 2.815   100.00     84.8
sRGB + RGB-H（亚像素 AA）            2.940   100.00     85.5

=== 浅底深字 ===
sRGB surface (current)              2.095    38.36     12.3
legacy (null space)                 2.095    38.36     12.3
sRGB + RGB-H（亚像素 AA）            2.281    54.60     12.4
```

换成 legacy/无色彩空间**完全无差异**（Skia 的 sRGB surface 与非管理 surface 在这里给出同一组像素）；打开亚像素 AA 反而更糊（2.095 → 2.281，半覆盖像素 38% → 55%）。**结论：不改颜色空间，不开亚像素 AA。**

### 1.3 图片降采样是真凶

头像解码上限 256px、聊天图 768px、横幅/壁纸 1024px，而实际绘制在 40–90 设备像素。GPU 的 `drawImageRect` 用 LINEAR 只取 2×2 个纹素。

```
128px 源 -> 40 设备像素（3.20x）              误差    高频保留
画源图，LINEAR（改前）                       23.96     146%
画源图，MITCHELL                             25.58     152%
一次 MITCHELL 直接缩到 40                     25.58     152%
缩到 64 再交给 GPU                            13.32     124%
缩到 80（=2×目标）再交给 GPU（改后）             3.06     106%

256px 源 -> 48 设备像素（5.33x）
画源图，LINEAR（改前）                       26.11     196%
缩到 96（=2×目标）再交给 GPU（改后）             3.64     106%
```

关键机制：**GPU 的双线性采样在「恰好 2 倍」时是精确的，其他比例只是近似**。所以正确做法不是"换个更好的采样模式"（MITCHELL 在 GPU 端几乎无用），而是 CPU 端先降到恰好 2×目标尺寸。

## 二、改了什么

### 2.1 锐度：`ImageQuality` + `SkiaImages`

- `shared/.../render/ImageQuality.java`：先用 MITCHELL 反复对半（>`4×` 目标时），再一次 MITCHELL 精确落到 `2×目标`，最后一次交给 GPU。派生副本按「源图（弱引用）+ 目标宽度」缓存，有总像素上限（4M px ≈ 16MB），淘汰与 `release()` 都 `close()` 原生图。
- `shared/.../render/SkiaImages.java`：`drawRoundedImage` / `drawImage` 两个包装，内部走 `ImageQuality`。**14 个绘制点全部迁移**（头像、横幅、壁纸、聊天图、表情、应用图标、字标、通知横幅、`PlayerAvatar`、`ImagePreviewOverlay`）。
- 仓库级清理时机：三个加载器的资源重载监听器都加 `ImageQuality.release()`。
- 守卫：`ImageQualityTest.everyImageDrawSiteGoesThroughTheDecimatingWrappers` 扫全树，任何新的裸 `SkiaDraw.drawRoundedImage(` 调用点都会失败；`thePreparedBitmapIsExactlyTwiceTheDrawnSize` 把「恰好 2×」钉成行为契约（12 组源尺寸×目标尺寸）。

### 2.2 性能：每帧重复计算

真机基线（用户日志 `2026-11-02`，120fps 目标）：`panel p50=4.6ms p95=9.1ms alloc=95–190 KB/call`。

| 问题 | 位置 | 修法 |
|---|---|---|
| 每帧把折叠窗口到列表末尾**全部行测一遍**（最多 500 行，每行重新换行+算表情条） | `MessageListView.measureContentHeight` | 按「快照实例 + 尺寸 + 折叠点 + 宽度 + 3 个布局位 + 表情版本」缓存结果；快照在两次变更之间是同一实例，因此判断是 O(1) |
| 绘制循环**再走一遍**窗口来找第一可见行 | 同上，draw 循环 | 测量时顺带记录每行的 `rowTop`/`rowHeight`/`dividerTop`，绘制改按偏移跳过 |
| 每行每帧算 **SHA-256**（表情 chip 的 key），一行算三次 | `ReactionClient.key` | 按 `message.id` 缓存（内容不可变，合并副本共享 id）；null 也缓存；上界 8192 / 4096 |
| 每个时间分隔线每帧 `DateTimeFormatter.ofPattern("HH:mm")` + `ZoneId` 查询 | `MessageListView.drawTimeDivider`、`ConversationListPage.formatMessageTime` | 静态 formatter |
| 换行缓存 key 只含 `(id,width)`，**不含字号** → 换字号复用旧换行（正确性 bug） | `MessageListView.wrappedLines` | key 加入字号，并修正 id 位移避免碰撞 |
| `ChatStore.snapshot()` 每帧复制最多 500 个引用；会话列表还多调两次 | `ChatStore`、`PrivateChatStore` | 发布不可变快照，变更时失效；顺带加 `generation()` |

**A/B 实测**（同一台机器，同一离屏夹具，500 条消息中段滚动，200 次采样）：

```
                     p50      p95      alloc/frame
改前                2.191ms  2.553ms   200.7 KB
改后                1.313ms  1.860ms    33.1 KB
                   -40%     -27%        -84%
```
50 条消息：p50 1.459→1.389ms（噪声范围内），alloc 49.5→36.1 KB（-27%）。收益随列表长度增长，符合"消掉了 O(n) 每帧工作"的预期。

### 2.3 内存

| 问题 | 位置 | 修法 |
|---|---|---|
| 回退字体缓存无上界（每个 exotic 码位 × 字号一个原生 `Font`+`Typeface`，永不释放） | `SkiaFontRenderer.FALLBACK_FONT_CACHE` | 2048 项 LRU |
| 表情图缓存无上界（500 张 128×128 贴纸 ≈ 32MB/屏幕），`clear()` 不 close | `EmoteImageCache` | 256 项 LRU + 淘汰/清空时 `close()` |
| 皮肤缓存三张表都无上界，键是每服务器纹理 id | `AvatarRenderer` | 各 256 项 LRU + `close()`；新增 `release()` |
| 每次开屏新建 `ImageUploader`（自带 `HttpClient` selector 线程 + 连接池 + executor），屏幕在每次打开**和每次改窗口大小**时重建 | `ImageUploader` | 进程级单例 `ImageUploader.shared()` |
| 屏幕关闭时裁剪器已解码位图（≤4MB）与表情包图标交给 finalizer | `ImageCropper`、`EmojiPanel` | 新增 `release()` / `dispose()`，三个屏幕的 `removed()` 调用 |

## 三、验证

- `python tools/verify_targets.py`：通过（孪生一致性）。
- 三端全量 test：**Fabric 1610 / NeoForge 1596 / Forge 1596，0 失败，各 4 skip**（基线 1601/1586/1586；+9 为 `ImageQualityTest` 的 8 条 + 其他）。
- `ImageQualityTest` 8 条：降采样误差优于直绘（噪声图，非棋盘——棋盘会让两条路径都精确）、恰好 2×、2×内不动源、同源只派生一次、`release()` 关派生不关源、不产生退化尺寸、全树绘制点守卫、密度 1.0 时几何不变。
- 顺带修掉一个既存测试缺陷：`CompatibilityRegressionSourceTest`、`NavInterruptWiringSourceTest`、`InputWiringSourceTest` 读源码后直接匹配含 `\n` 的多行片段，在 CRLF 工作副本上必然失败（`git stash` 验证过 HEAD 上同样失败）。三处改为读后归一化行尾 —— 这类守卫应该关于代码，不该关于检出方式。

## 四、未做 / 需人判断

- **实机观感未验证**：锐度改动要在游戏里看。三个客户端必须完全重启。
- **`docs/STATUS.md` 里"每帧没有 dirty-region"这个结构性事实没变**：静止面板仍然整窗重绘。缓存结果图是最大的剩余结构性收益，但风险高（需要正确失效：主题、缩放、滚动、悬停、动画），本轮未做。
- **AtomLib 侧未改**（不在本仓库）：`UiTokens.hairline()/cardFill()` 每次调用重算 `relativeLuminance`（3×`Math.pow`），`SkiaDraw.BLUR_FILTERS` 清空时不 close。要处理得在 AtomLib 仓库做。
- **未发版**：版本号仍是 `0.4.0`。
