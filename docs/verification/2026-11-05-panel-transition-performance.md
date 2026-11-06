# 面板切页掉帧审计与修复（2026-11-05）

## 结论

这不是单一的“动画帧贵”，也不是 Skija 已知 bug。现有证据把问题拆成三层：

1. **稳定态固定成本**：用户修复后的日志中，`panel` p50 为 `2.22–2.35 ms`、`chat_interval` p50 为 `3.61–3.77 ms`（约 265–277 FPS）；`BRIDGE_ENTER` 已从修复前的 `2.13 ms` 降到约 `0.46 ms`。
2. **切页尖峰**：修复前的 `panel` p95 为 `7.17–8.13 ms`、max 为 `33.95–50.55 ms`。旧 JFR 按导航时钟配对后，设置详情页入场窗口 p50 达 `10–12.6 ms`、外部帧率约 `72–100 FPS`。部署后的 CCB 滚动汇总从首个轻负载窗口 `panel p50 2.14 ms / p95 3.47 ms / alloc 29.0 KB`，到继续切页后的 `p50 2.49 ms / p95 5.43 ms / alloc 188.0 KB`；`chat_interval` 保持 `p50 3.32 / p95 4.73 ms`（约 301/211 FPS）。这是跨启动观测，场景内容不完全相同，但 p95 方向与离屏 A/B 一致，max `44.20 ms` 的冷路径尖峰仍存在。
3. **页面内容本身也有成本**：`saveLayer` 归因不能解释全部长帧。隐藏 GL 同场景中，10 张卡片逐张建层仅比直接绘制多 `0.28–0.32 ms`（p50）；真正同时暴露的是重复文字测量/塑形、同帧重复构建设置行、以及头像冷启动 GL 读回。

因此本轮保留动画和图层语义，修 AC 的真实热路径，并把 One-time 冷路径与持续热路径分开报告。

## 数据证据

旧 JFR：`D:/AtomLib/build/scope-diagnostics-20261105/samples/panel-auto-20261105-103433.jfr`。复算结果在 `D:/AtomLib/build/scope-diagnostics-20261105/analysis-103433/`：

- 23,370 个面板帧、19 次导航、23,370 个 `GlStateCapture`；每个完整帧恰有 1 次 capture、depth=0。没有证据支持 issue 中“当前每帧 4–7 个外层 scope”的数量估计。
- 全局 `panel` p50 `3.815 ms`，旧 `capture` p50 `2.1336 ms`；这是修复前基线。
- 设置详情入场 0–100ms 的示例：frame interval p50 `12.57 ms`，panel p50 `12.57 ms`，外部约 `71.6 FPS`；另一段 100–320ms 为约 `10.1–10.7 ms / 72–81 FPS`。
- `PROFILE` 首次入场有一帧 `52.88 ms`，采样栈落在 `SkinTextureReader.read -> AvatarRenderer.sample -> PlayerAvatar.face -> ProfilePage.drawAvatar`。后续访问命中缓存，不能把这次一次性读回算成每次切页成本。
- 修复前日志快照：`build/stage2-ac-20261105/latest-before-stage2.log`（SHA-256 `31C9BD2AF5BE276C218F0077534C0416CA90AA442CEA0536DC9FCF518CB6E706`），面板 p50 `2.31 ms`、p95 `8.13 ms`、分配 `93.2 KB/call`。
- 部署后日志快照：`build/stage2-ac-20261105/latest-after-stage2.log`（SHA-256 `30BA3D545D71F1574C7D10171D438861C45EF4668B86055BA29EF25652B904C8`）。滚动窗口依次显示：轻负载面板 p50/p95=`2.14/3.47ms`、分配 `29.0KB`；继续切页后 p50/p95=`2.49/5.43ms`、分配 `188.0KB`；`chat_interval` 始终 `3.32/4.73ms`。max `44.20ms` 仍在，属于尚未消除的冷路径/外部竞争长尾。

## 本轮修复

| 修复 | 位置 | 为什么 |
| --- | --- | --- |
| AC 文字 run 缓存 | `shared/src/main/java/com/atom/chat/render/SkiaFontRenderer.java:41`、`:619`、`:643` | 每帧重复的标签不再每次进 `Canvas.drawString` 重新塑形；缓存按 Font 实例和字符串分桶，限制 2,048 条 / 131,072 字符，淘汰时关闭原生 `TextBlob`。
| 文字绘制只测量一次 | `SkiaFontRenderer.java:368`、`:377` | `drawRuns` 直接返回 advance，删掉绘制后再次 `measureRuns` 的重复 native 测量。
| 复用文字 Paint | `SkiaFontRenderer.java:23` | 去掉每段文字、每个带阴影 pass 新建 native Paint 包装对象的开销；所有属性在每次绘制前重置。
| 缓存格式码解析 | `SkiaFontRenderer.java:867` | 重复标签不再每帧重新分配 segment 列表；仍保留原文本与颜色码语义。
| 同帧复用设置行模型 | `platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsSectionPage.java:1161`、`:1240`；官方映射孪生文件同位置 | `measureContent()` 与紧接的 `render()` 共用一次 `rows(section)`，不再每帧构造两遍控件行与 lambda。section 或 chip 改变时仍重建。
| 去掉头像名热路径正则 | `shared/src/main/java/com/atom/chat/image/SkinNameCacheKey.java:6`；三端 `SkinResolver.skinNameKey` 统一调用 | 原 `replaceAll("§.", "")` 会在每个可见头像每帧编译/执行正则；改为手写格式码剥离，保留 trim、空名和 `Locale.ROOT` 语义。

`PageTransitionComposite.java:211–223` 与设置/个人页的逐项 `saveLayer` 保留。新隐藏 GL A/B 证明它确实有成本，但量级不足以单独解释 50ms 长帧；关闭或删除它会改变用户要求的动画呈现。

## 验证

- 红绿门：同一份源码依次装回“关闭文字缓存 / 关闭同帧行复用 / 让头像名不剥格式码”，三个测试分别失败；按字节恢复后全部通过。日志在 `build/stage2-ac-20261105/red-*.log` 与 `red-green-summary.txt`。
- 文字像素：缓存 `TextBlob` 与旧 `Canvas.drawString` 在 Latin 和 bundled CJK 文本上逐像素一致；缓存重建也逐像素一致。
- 隔离源码三端全量构建：Forge `1651`、Fabric `1666`、NeoForge `1651` 项，失败 `0`、错误 `0`，各 `6` 个明确跳过项（AtomLib alternate、Golden、两个自动基准等）。
- Lib 本轮未再改生产代码；根 `test` 使用 `-PatomlibTestFont=C:\Windows\Fonts\msyh.ttc` 后 core 24、runtime 74、UI 142、Yoga 6，全部 0 失败/错误/跳过。
- `python tools/verify_targets.py` 通过；三端 `verify_jars.py --profile atomlib-consumer` 全通过。
- 产物中的 `SkiaFontRenderer`、`SkiaFontRenderer$ShapedPlan`、`SkinNameCacheKey`、`SettingsSectionPage` 均已检查存在；编译入包的 AtomLib 版本为 `0.3.0`。

构建入口使用隔离源码快照 `build/stage2-ac-20261105/source/`，工作区 `versions/atomlib.json` 始终恢复为原始字节；改动源码与快照逐文件 SHA-256 一致。

隐藏 GL 基准（RTX 5060 Laptop，10 张卡片、20 段文字）两轮：

| 路径 | p50 |
| --- | ---: |
| 缓存 TextBlob，直接绘制 | 0.434 / 0.367 ms |
| 旧 drawString + 二次测量，直接绘制 | 0.514 / 0.394 ms |
| 缓存 TextBlob，10 张卡片各自 saveLayer | 0.718 / 0.690 ms |

详细原始输出：`build/stage2-ac-20261105/source/platforms/1.21.1-fabric/build/atomlib-consumer/test-results/test/TEST-com.atom.chat.render.PageEntranceGpuBenchmark.xml`。

## 部署

2026-11-05 13:33（上海时间）已部署三端 AtomChat：

| 实例 | JAR SHA-256 |
| --- | --- |
| `D:/Myworld/.minecraft/versions/1.20.1-main/mods` | `6c907d9b84d217153c80df6b670086039ae5c657202a39b786d517282e064dc9` |
| `D:/Myworld/.minecraft/versions/1.21.1-CCB/mods` | `d7140fb907a820b79eea163a05f6e40fdd037d7c462619545be25d178fedc3b7` |
| `D:/Myworld/.minecraft/versions/Mechanomania-航空学/mods` | `56da97b4ffbbca8e12b6a957cfd2865204f386af7520f948e15c28be54873d23` |

部署前 WMI 游戏进程为空；三份旧包均备份并回读 SHA-256，其他模组条目元数据及当前 AtomLib JAR 哈希未变。回执：`build/stage2-ac-20261105/deployment-receipt.json`。用户随后自行启动了 CCB；执行代理没有启动游戏。未 commit、未 push、未发布。

## Skija / Skia 调研

项目锁定 Skija `0.143.18`（`gradle.properties:20`），对应当前 `0.143.18` tag，commit `44c99d7a92fe68a29b3233f8ca55b0d29d8f6f68`。核对该 tag 的原生桥：

- `Canvas.drawString` 直接调用 `SkCanvas::drawString`；`drawTextBlob` 直接调用 `SkCanvas::drawTextBlob`。
- 上游问题检索没有与 Ganesh `saveLayer` 性能相符的开放 issue。相关文本 issue 是 [TextLine 与 String 宽度差异 #81](https://github.com/HumbleUI/Skija/issues/81) 和 [字体实例导致文字裁切 #77](https://github.com/HumbleUI/Skija/issues/77)，它们不是本次帧率下降的直接解释。
- 因此本轮没有把问题上报为 Skija bug，也没有依赖升级来“碰运气”。

## 剩余风险

- 头像首次读回仍是同步 GL 路径（`AvatarRenderer.java:117–123`）。冷启动 `PROFILE` 可能保留几十毫秒首帧；改成 PBO 异步会扩大原生资源生命周期，本轮没有为了掩盖一次冷启动而引入。
- 外部模组仍明显占帧时间；旧 JFR 的完整线程栈包含大量 EMF 动画与 Sodium/Iris 工作。该部分不属于 AC/Lib，不能由面板优化消灭。
- `twin_diff_report.py --summary` 仍有 12 个未归类残差块，集中在既有 `ChatClassifier`、`NotificationHudOverlay`、`SkiaGraphics` 及其测试；本轮未擅自登记它们。`SettingsSectionPage` 不在残差列表中。
- 部署后的首份实时日志已出现明确改善，但它仍是滚动窗口、跨启动对比；其他页面、复杂世界和长时间切页仍需用户继续正常试用。
- 按要求未派其他 reviewer；本文所述复核均由本轮执行代理完成。
