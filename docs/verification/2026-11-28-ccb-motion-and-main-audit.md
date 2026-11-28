# CCB 动画卡顿与 main 合并核对

2026-11-28，时间均为北京时间。两位 GPT‑6.2‑Astra High 分别审计渲染和点击回调。CCB 15:52 启动记录确认 AtomChat 为 `28f4e94c73b3`，对应构建源码 `8fad2a3c`。上一场 14:30 启动的是 `833b672aa515`。两个部署的 AtomLib 均为 `7dad7148d913`。本轮重新读回活动 JAR 并复算日志统计。 [本次启动日志](D:/Myworld/.minecraft/versions/1.21.1-CCB/logs/latest.log:696) · [上一场日志](D:/Myworld/.minecraft/versions/1.21.1-CCB/logs/2026-11-28-1.log.gz) · [部署回执](D:/AtomChat-Backups/2026-11-28-v100-preview-followup/deployment/20261128-154306-550532/deployment.json)

## CCB 帧间隔与现场动作

| 现场窗口 | 观察 |
| --- | --- |
| 15:53:53–58 | 暂停、Iris 关闭光影并重建管线、重开面板。整帧间隔峰值 895.59 ms，面板单次 875.54 ms 落在首次导航的稳态分组。将此时窗与常规切页分开测 |
| 15:54:17–19 | 公屏返回聊天列表。重复导航转场首次记录 **712.00 ms** 的面板绘制，面板入口和 HUD 回调间隔峰值分别为 **725.66、725.26 ms** |
| 15:54:37–41 | 切换设置页，HUD 回调间隔峰值 736.55 ms，随后才发生另一次点击并保存主题。15:54:41 的面板整体和非聊天绘制窗口最大均为 24.09 ms，滚动窗口无逐帧对应 |
| 15:54:42–47 | 设置确认框内点击后出现 TOAST，壁纸目录在 15:54:45.161 更新。两个间隔探针分别记录 848.24、848.54 ms。清壁纸是可单独复测的假设 |
| 15:54:49–57 | 公屏内点击，HUD 回调窗口 P50 4.918 ms、P95 7.435 ms、最大 **909.09 ms**，面板入口随后报 909.24 ms。同阶段面板绘制窗口的最大值为 215.34 ms。后续图片上传开始于 15:54:54 |
| 15:55:33–52 | 恢复 Bliss 后继续切设置页与页签。四个 HUD 窗口 P95 为 14.959–19.177 ms，最大 51.04–95.36 ms。15:55:39 的 AtomChat 客户端 tick P95 为 0.065 ms，最大 0.17 ms |

来源为 [关闭光影与重开面板](D:/Myworld/.minecraft/versions/1.21.1-CCB/logs/latest.log:1008)、[返回列表](D:/Myworld/.minecraft/versions/1.21.1-CCB/logs/latest.log:1399)、[切换设置](D:/Myworld/.minecraft/versions/1.21.1-CCB/logs/latest.log:1822)、[确认框](D:/Myworld/.minecraft/versions/1.21.1-CCB/logs/latest.log:1882)、[公屏点击](D:/Myworld/.minecraft/versions/1.21.1-CCB/logs/latest.log:2012)和[恢复光影](D:/Myworld/.minecraft/versions/1.21.1-CCB/logs/latest.log:2635)。

最新版 36 个 600 帧 HUD 回调窗口中有 8 个出现超过 500 ms 的间隔。上一场为 0/61，HUD 回调间隔最大 184.01 ms，面板入口曾记录 920.63 ms。两场的操作序列和光影状态未配对。两种间隔探针采样点各自独立，且丢弃大于等于 1 秒的间隔。滚动统计只用于定位时窗。 [回调接线](../../platforms/1.21.1-fabric/src/main/java/com/atom/chat/AtomChatClient.java#L104) · [采样实现](../../shared/src/main/java/com/atom/chat/diagnostics/Truth3Probe.java#L88)

| 重复进入的面板耗时 P95 | 上一场稳态 → 转场 | 本次稳态 → 转场 |
| --- | ---: | ---: |
| 公屏 | 2.45 → 11.65 ms | 5.77 → 13.29 ms |
| 设置 | 4.01 → 8.52 ms | 5.29 → 9.94 ms |
| 导航 | 2.55 → 8.92 ms | 2.23 → 7.97 ms |

数据取上一场 14:39:18 与本次 15:55:46 的末次页面汇总。712 ms 尖峰附近的面板 GPU P95 为 0.638 ms、桥退出墙钟 P95 为 0.387 ms，末次为 2.294、2.405 ms。分位数无法解释单个长帧。15:55:22 有两条无调用栈的 OpenGL 缓冲错误，主要 500 ms 以上尖峰此前已出现。 [上一场，第 4240–4257 行](D:/Myworld/.minecraft/versions/1.21.1-CCB/logs/2026-11-28-1.log.gz) · [712 ms 时窗](D:/Myworld/.minecraft/versions/1.21.1-CCB/logs/latest.log:1412) · [末次汇总](D:/Myworld/.minecraft/versions/1.21.1-CCB/logs/latest.log:2870) · [OpenGL 记录](D:/Myworld/.minecraft/versions/1.21.1-CCB/logs/latest.log:2537) · [带原行号的两场时序节录](D:/AtomChat-Backups/2026-11-28-v100-preview-followup/evidence/ccb-motion-20261128.log)

## 路径与取证顺序

1. **公屏返回列表和动画中连续切页。** 两版都会重录到场页，并用两个群组透明层回放到场页和离场页的绘制命令。卡片玻璃可再执行一组遮罩。新版列表渐隐额外给溢出的整个视口建层，另有玻璃遮罩和靠边波纹的建层路径。连续打断时旧画面会重新录制，超过命令预算才做全尺寸 GPU 快照，快照准备在现有面板计时前。启动时面板模糊关闭，但壁纸卡片模糊有独立路径。先分别测录制、正文回放、遮罩、快照和提交，以无溢出视口作同版对照。若命中渐隐，优先缩紧实际需要的群组图层。若命中离场回放，再验证对已冻结画面的全精度复用，并核对缩放文字、透明混合和打断当帧。 [转场实现](../../shared/src/main/java/com/atom/chat/nav/PageTransitionComposite.java#L125) · [渐隐实现](../../shared/src/main/java/com/atom/chat/render/ScrollEdgeFade.java#L58) · [卡片玻璃](../../shared/src/main/java/com/atom/chat/render/CardBackdrop.java#L99) · [快照入口](../../platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java#L2431) · [Skia 群组混合语义](https://api.skia.org/classSkCanvas.html)
2. **确认壁纸操作和公屏工具点击。** 点击回调同步运行。壁纸清除涉及枚举、移动和清理文件，切贴纸标签会先同步扫描目录和读属性，主题保存也会在调用线程进入配置文件事务。应分别测命中测试、动作、文件事务、纹理释放、声音提交和第一次绘制。普通按钮音在 15:54:19–21 有同类快速样本。若文件事务命中，后台串行提交并保留原有成功反馈和失败语义。若贴纸扫描命中，验证复用已完成的列表并在后台更新，同时核对排序与手动放入文件的可见性。 [输入路由](../../shared/src/main/java/com/atom/chat/ui/input/InputRouter.java#L29) · [壁纸事务](../../shared/src/main/java/com/atom/chat/wallpaper/WallpaperStore.java#L178) · [贴纸标签](../../platforms/1.21.1-fabric/src/main/java/com/atom/chat/ui/EmojiPanel.java#L329) · [贴纸扫描](../../shared/src/main/java/com/atom/chat/emote/EmoteStore.java#L112) · [按钮声音](../../platforms/1.21.1-fabric/src/main/java/com/atom/chat/audio/FeedbackSoundController.java#L32)
3. **用同一时钟关联一次操作及后续长帧。** 在隔离夹具固定存档、壁纸、光影、画质、动画、列表与 AtomLib，串行跑同一序列。使用逐帧 JFR、动作时间戳、已有 GPU 计时及独立整帧间隔。另把 GC 暂停、文件 I/O、原生栈、诊断报告构造和面板外等待按时间对齐，保留超过 1 秒的事件。验收用已部署 Preview 对比，记录 P95、P99 和超过 33、50、100、500 ms 的帧数。用同环境停用 AtomChat 的匹配片段估计模组增量开销。资产及动画相位用对齐帧核对，保留原速度、动效、清晰度和交互。 [既有复现工具](../../tools/run_v098_perf_replay.py) · [现有逐帧事件](../../shared/src/main/java/com/atom/chat/diagnostics/FrameSegments.java#L85) · [JFR 排查文档](https://docs.oracle.com/en/java/javase/25/troubleshoot/troubleshoot-performance-issues-using-jfr.html)

本轮未在 CCB 找到该会话的逐帧记录，也未启动或接入游戏。运行 `python tools/probes/verify_profile_wiring.py`，Fabric 和 Official 映射下的 JFR 接线检查均通过。该探针用替身隔离 GL 层，只验证诊断入口和帧编号。精确到方法的长帧归因及候选优化的实机收益待受控复现。

## main 合并核对

- 仓库有 **5 个注册工作树**，含仓库本体、当前 Preview、两个分离头指针的 0.9.8 对照目录和 `D:/AtomChat-Main`。另有 57 个本地分支引用
- 本地及远端 `main` 均为 `2b4d3249`，它是 Preview `804f0899` 的祖先。左右独有提交数为 0 和 54。`git merge-tree --write-tree main HEAD` 的结果与 Preview 已提交树一致，支持快进且无冲突
- `git cherry` 验证三个 0.9.8 旁支的 1、5、2 个独有提交都已有等价补丁。两个分离头指针的对照目录也仅含已纳入的等价补丁。点查包括传送确认、头像裁剪时表单输入、均衡档阴影采样和聊天 HUD 外观。0.9.7 的旁支 TrChat 中继源码与 Preview 内容一致
- `D:/AtomChat-Main` 仍检出旧的 `codex/cpu-ui-experiment`，分支指向的 `d6f7b9b8` 早在 `main` 历史中。该目录另有 51 条已跟踪的工作区变动和 153 条未跟踪记录，应作为独立工作保留。当前 Preview 还有七个文件的交付路径修正，应先随本分支提交，快进才会带上它们
- 本轮仅模拟合并并新增本报告。快进可保留 Preview 的完整已提交快照，其他工作树独有的修改继续留在原处。远端 `main` 的 Build 与打 `v*` 标签触发的 Release 是两个工作流

## 隔离 CCB 逐帧续查

- 原版 `28f4e94c73b3` 与配套 AtomLib `7dad7148d913` 在私有桌面的隔离实例运行。完整夹具使用实际 CCB 的模组、画面设置、启用的资源包和本地存档副本，OpenGL 与现场同为 NVIDIA 4.6。使用归档的静态壁纸和现场贴纸。正常 CCB 的存档、配置和 JAR 未写入。测试覆盖极致档、真实返回键、表情与贴纸切换、贴纸选取和藏在动画中的快速设置切换。另从 Bliss 生效状态切到关光影，记录同一路线。
- 完整夹具的第一次表情打开，输入按下 **169.161 ms**，整帧间隔 **188.005 ms**。同一输入线程有一次 **168 ms** 的 **103 字节**写入，原生采样落在 `FileOutputStream.writeBytes → Log4j`。同一条点击日志连同换行恰为 103 字节，反馈音阶段为 0.248 ms。原版在真实输入加慢日志 appender 的回归测试因输出阻塞渲染线程而失败。三端将该坐标日志和同时触发的反馈音日志交给已有异步队列，实际输入和声音仍走原入口。 [动作与栈](D:/AtomChat-Backups/2026-11-28-ccb-motion-repro/ccb-full-baseline-2/motion-actions.json) · [原版回归红灯](D:/AtomChat-Backups/2026-11-28-ccb-motion-repro/ccb-click-regression-red/launcher.log:736)
- 一帧设置切换后面板耗时 **202.762 ms**，Java 绘制阶段 **18.925 ms**，桥接退出 **179.002 ms**。原生采样命中 `SkijaRenderBridge.drawFrame → DirectContext._nFlushAndSubmitSurface`。另一帧 **130.644 ms** 命中 `FrameSegments.pollGpu → GpuTimerQuery.poll → glDeleteQueries`，位置是已读取结果的常规删除。改为在相同 GL 上下文复用已消费查询名，仍等结果可用才读取，保留容量与错误处理。查询对象可重新用于同类型查询，旧值须在重启前消费。 [调用栈与时间](D:/AtomChat-Backups/2026-11-28-ccb-motion-repro/ccb-full-baseline-2/hot-panels.txt) · [OpenGL 4.6 §4.2](https://registry.khronos.org/OpenGL/specs/gl/glspec46.core.pdf)
- 模拟现场关闭光影后，公屏返回聊天列表窗口也抓到桥接退出 **19.323 ms**，该帧面板 **24.934 ms**。同场表情面板另有 **105.830 ms** 落在同一个 Skija 提交入口。两次提交尖峰关联的面板 GPU 查询为 **178.07 ms** 和 **105.74 ms**，时间窗包含两条查询标记之间的间隔。产品调用的是 Skija 的非强制 CPU 同步重载，JFR 能定位提交调用，调用内部的原生驱动延迟仍待进一步拆分。原现场的 712 ms 或 909 ms 帧没有逐帧栈，以上归因为可复现的同类卡顿。 [光影切换后逐帧结果](D:/AtomChat-Backups/2026-11-28-ccb-motion-repro/ccb-full-shader-flip-1/hot-panels.txt)
- 同一精简夹具给原版和候选注入 180 ms 慢日志输出，实际表情按钮按下的墙钟由 **181.99 ms** 降至 **0.44 ms**。候选仍收到该点击与按键反馈诊断，两条由后台输出。Fabric 初版候选通过普通视觉契约的 196 个原生离屏场景。 [回归绿灯](D:/AtomChat-Backups/2026-11-28-ccb-motion-repro/ccb-click-regression-green/launcher.log:736) · [Fabric 构建记录](D:/AtomChat-Backups/2026-11-28-ccb-motion-repro/fabric-motion-candidate-build.log)

### 双基线取样

相同完整 CCB 副本、同一存档和同一 AtomLib。停用组实际移除了 AtomChat JAR，并在 Fabric 加载器上断言该模组未加载。该组 12 秒记录 **4,679** 帧，末 6 秒 P95 **3.068 ms**。这组只用来估计关面板时的模组开销，AtomChat 界面动作仍与已部署的 Preview 比较。 [停用组逐帧记录](D:/AtomChat-Backups/2026-11-28-ccb-motion-repro/ccb-full-no-atomchat-5/frames.jfr)

第一对完整夹具记录中，快速切设置页的整帧 P95 为 **15.517 → 15.162 ms**，最长帧 **148.044 → 20.232 ms**。打开表情后 320 ms 以内的窗口，最长帧 **188.005 → 12.440 ms**。末 6 秒关面板 P95 **3.802 → 4.187 ms**，公屏返回列表的 P95 **10.849 → 11.520 ms**。一次最大值变好不能验收所有场景。另跑原版且诊断关闭的一组，快速切设置时最长帧 **29.940 ms**。该对照帮助区分诊断影响和正常产品开销。 [首对原始统计](D:/AtomChat-Backups/2026-11-28-ccb-motion-repro/first-comparison.txt) · [原版关诊断统计](D:/AtomChat-Backups/2026-11-28-ccb-motion-repro/ccb-full-debug-off-baseline/control-comparison.txt)

随后按现场全屏配置与 GUI 坐标比例，在私有桌面实际以 **2560×1600** 重放原版 `28f4e94c73b3` 和最终候选 `0c30728193ad`。两轮先使用 Bliss，再在同一动作位置关闭光影。两个停用对照分别实测了 Bliss 开启和关闭。 [原版与候选、两个停用对照](D:/AtomChat-Backups/2026-11-28-ccb-motion-repro/2560x1600-comparison.txt)

| 2560×1600 实测 | 原版 | 最终候选 | 无 AtomChat |
| --- | ---: | ---: | ---: |
| Bliss 开启时关面板帧间隔 P95 | 8.363 ms | 8.163 ms | 8.286 ms |
| 快速切设置页帧间隔 P95 | 18.698 ms | 18.789 ms | — |
| 快速切设置页帧间隔 P99 | 20.789 ms | 19.645 ms | — |
| 快速切设置页最长帧间隔 | 37.195 ms | 24.679 ms | — |
| 公屏返回列表后 320 ms 内最长帧间隔 | 21.864 ms | 16.260 ms | — |
| 有采样的面板绘制最长耗时，含首次开屏 | 113.627 ms | 79.078 ms | — |

### 构建与合并状态

三端运行严格 Windows 离线构建，各 **196** 个场景通过零容差比较。测试共 **7,524** 项通过、**59** 项跳过、零失败。Fabric 最终包的点击与反馈音字节码与慢日志回归绿例完全一致。成品已逐端写入指定 `D:/AtomChat-Main/platforms/<目标>/build/atomlib-consumer/libs/` 并读回 SHA-256。 [构建记录](D:/AtomChat-Backups/2026-11-28-ccb-motion-repro/three-platform-motion-build.log) · [产物回执](D:/AtomChat-Backups/2026-11-28-ccb-motion-repro/three-platform-delivery.json)

已证实并修复同步诊断写入和每次读取成功后立即删除 GL 查询名的路径。原始 712 ms 长帧缺少逐帧栈，现场分辨率回放未出现同等级停顿，Skija 原生提交内部的延迟来源尚未确认。回放覆盖内通过了功能和画面检查，并有切页长尾改善的样本。按用户本轮的合并条件，性能整体验收未完成，优化与三端交付路径修正保留在 Preview，本地 `main` 仍为 `2b4d3249`，正式实例继续使用已部署版。
