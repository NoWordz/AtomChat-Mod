# 0.9.1 性能候选离屏探针

## 审计附件逐项核对

- [x] AC-01 页面录制。已比较直接绘制与合成，并验证打断和切页连续性。样本未支持改实现。
- [x] AC-02 引用。复用富文本布局，保留内联物品、样式与 Unicode 截断边界。
- [x] AC-03 发送者名称。复用布局，覆盖字体与发送状态变化。
- [x] AC-04 反应。复用投影并验证动画，离屏对照确认 Java 分配下降。
- [x] AC-05 图片任务。已有任务先于缓存路径计算复用，保留可见性更新。
- [x] AC-06 图片解码。已验证的后台静态图片少做一次校验，公共解码入口保留校验。
- [x] AC-07 移动点阵。复用坐标缓冲，离屏核对像素及分配。
- [x] AC-08 基线测量。完成隔离 OpenGL 五段模糊的 GPU 分位数测量。
- [x] AC-09 消费端取证。核对候选库实现，并测 AtomChat 的图元 CPU 光栅对照。
- [ ] AC-08 实机对照。游戏整帧、移动背景及降采样画质仍待测。
- [x] AC-09 库内联审。AtomLib 性能分支 `5e91ab1` 的联审记录逐项核对审计附件第 7 节。另修复旧票据重复转交误关资源。
- [ ] AC-09 实机对照。原生对象长期占用、真实页面开销和多模组整帧仍待测。双向反哺迁移留到下一轮。

2026-11-18，在隔离 worktree、Windows、Fabric 1.21.1、JDK 21.0.11 和 AtomLib 0.6.0 candidate Maven 输入下执行。探针使用 Skija CPU raster，计时为线程墙钟，分配为 ThreadMXBean 的 Java 堆分配。它们不代表游戏帧时间、GPU 耗时、Native 内存或 FPS。

## 命令

在 `platforms/1.21.1-fabric` 目录执行，`ATOMCHAT_DOT_IMAGE` 分别指向隔离 worktree 下的 before/after PNG。修改前源码为 `bb43b9db`，测试源相同，修改后执行相同命令。页面录制探针及 Reaction 基线先一起执行，点阵基线随后单独执行。

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
$env:ATOMCHAT_BENCH='1'
$env:ATOMCHAT_DOT_IMAGE='<隔离worktree>/build/dot-before.png'
.\gradlew.bat --offline --no-daemon --console=plain -PatomlibMavenRepo=D:/AtomChat-Main/build/v091/atomlib-maven test --tests com.atom.chat.ui.ReactionProjectionTest --tests com.atom.chat.render.DotTextureMovingProbeTest --tests com.atom.chat.nav.PageTransitionCompositeTest
```

探针环境变量只在需要测量时设置。Gradle 可能将 test 判为 up-to-date，复测时先执行 `cleanTest` 或使用 `--rerun-tasks`。

## 样本

- Reaction 使用真实客户端快照与投影路径，100 行、每行 5 种 reaction，预热 30 帧后采 100 帧。修改前 33,695,064 bytes / 47,676,900 ns，修改后 13,135,576 bytes / 35,839,600 ns。单次样本含反射调用和动画采样，不用于估计游戏收益。
- 移动点阵预热 30 帧后采 200 帧，修改前 546,240 bytes / 67,929,500 ns，修改后 24,000 bytes / 77,083,500 ns。两个单次耗时不可判定回归或改善。两张样本 PNG 的 SHA-256 均为 `6a042e630b8f22538326925dc9c361e8682bc1be0c4a3e3fd18f4e02d5f2b10f`，是该场景的字节对照。缩放 1、1.5、2，点阵朝上/朝下的现行稳定和移动路径另有逐像素断言。
- 页面录制原有 opt-in 探针每种路径各采 100 帧，交替两轮。修改前直接绘制 p50/p95 为 1.7178/2.6336、1.5226/2.2495 ms，合成路径为 1.6104/2.3795、1.5397/2.2424 ms。实页 tab 双向切换与两次中途打断的像素连续性回归通过。该样本不能证明稳态录制是显著瓶颈，因此保留动态内容与现有快照路径。

修改前探针 XML 被后续 Gradle test 覆盖，上述数值是执行时读取的输出记录。修改前后 PNG 和最新 XML 收录于本地 0.9.1 收口归档的 `performance/`。复测使用同一测试源，在上述基线及 `f6f5332e` 分别执行。

## 适用范围

- Reaction 的投影按客户端 revision 与本人 UUID 失效，有界缓存不改变逐帧动画采样；动画的进入、退出、计数 pulse 与离屏重现由现有测试覆盖。
- 点阵仅复用 render-thread 点坐标缓冲，保留命令顺序、渐隐与现有图片缓存。像素样本和离屏对照不等于全部 GPU/GUI scale 实机验收。
- AC-08 已用 `PanelBlurGpuCostTest` 在 RTX 5060 Laptop 的隐藏 OpenGL 上下文测五次生产 Kawase shader。采样每个尺寸预热 25 次、测 100 次，排除 Minecraft 截图、合成和整帧耗时。
- AC-09 已用同组图元的 `SkiaDrawBenchmark` 测候选包 CPU 光栅，原 helper 约 2.053/2.277 ms（p50/p95），改用外提 Paint 的对照约 2.180/2.458 ms。候选 AtomLib 已复用部分 Paint 和滤镜，本轮样本未支持继续调整 helper。Native wrapper 和进程内存尚未实测。

| 模糊 FBO 尺寸 | GPU p50 | GPU p95 | GPU p99 |
| --- | ---: | ---: | ---: |
| 480 × 860 | 0.0624 ms | 0.0637 ms | 0.0640 ms |
| 900 × 1500 | 0.1792 ms | 0.1819 ms | 0.1829 ms |
| 1440 × 2160 | 0.3963 ms | 0.4009 ms | 0.4020 ms |

模糊和图元复测于 2026-11-18 13:45 执行。Fabric 1.21.1 上设置 `ATOMCHAT_BENCH=1`，沿用同一 Maven 输入运行 `test --tests '*PanelBlurGpuCostTest' --tests '*SkiaDrawBenchmark'`。模糊降采样或降频仍需移动背景、视觉和游戏整帧证据。

候选 AtomLib 的 `SkiaDraw` 源码显示圆角矩形、描边和部分图片路径已有渲染线程专用 Paint，阴影还复用 MaskFilter 和缓存图。渐变仍按调用构造 Shader 与 Paint，背景模糊辅助函数仍构造 ImageFilter。后续可用实际页面逐 helper 采样来判断对象成本，区分 Java wrapper、原生资源和帧时间。引用、发送者、表情反应与图片队列的策略留在 AtomChat。
