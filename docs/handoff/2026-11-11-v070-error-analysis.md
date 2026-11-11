# v0.7.0 用户错误包分析交接（2026-11-11）

## 结论与边界

- **实机事实：本次确有 JVM 原生崩溃**，不是仅有视觉不满意，也不是“未发现异常”。20:03:30，Render thread 在 `skija.dll+0x1eb54` 触发 `EXCEPTION_ACCESS_VIOLATION`；Java 栈首为 `Paint._nGetColor4f`，业务首帧为 `PageTransitionComposite.drawLayer`。证据：`hs_err_pid38464.log:4`、`:9`、`:24`、`:28`、`:38`、`:39`。
- **强证据指向 Paint 生命周期问题**：崩溃现场 Paint 对象的 `_ptr=0`、`_cleanable=null`，访问地址为 `0x30`。当前源码的 `alphaPaint` 只初始化一次，`close()` 关闭它，`reset()` 不重建；屏幕移除时 close，重新 init 时只 reset。证据：`hs_err_pid38464.log:61`、`:98`、`:102`、`:103`；`shared/src/main/java/com/atom/chat/nav/PageTransitionComposite.java:25`、`:129`、`:176`、`:177`；`platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java:2359`、`:2559`。
- **推断而非已复现根因**：同一聊天 Screen 进入通知 HUD 编辑器再返回，可能复用已关闭的 Paint，随后切页崩溃。这条路径在当前生产源码中存在；包内没有记录 Screen removed/init 和教程步骤，不能确认用户此次恰好走了它。证据见下文生命周期链。
- **实机性能有长尾，平均值不能代表顺滑**：末段 panel p50=2.17ms、p95=6.74ms、max=53.57ms；整帧探针末段 p95=16.583ms，全会话某窗口 max=353.02ms。两种探针采样入口不同，353.02ms 不能直接当成单帧渲染耗时。证据：`latest.log:4776`、`:4774`、`:3648`；`shared/src/main/java/com/atom/chat/diagnostics/Truth3Probe.java:40`。
- **教程、输入和动画观感未验收**：包内没有图片/视频、教程步骤事件或输入消费链日志。只能证明点击到达屏幕和部分反馈播放，不能证明每次点击命中正确控件、教程完成或过渡连续。证据：ZIP 目录；`latest.log:4816`、`:4817`；当前教程消费逻辑 `shared/src/main/java/com/atom/chat/onboarding/TutorialController.java:72`、`:113`、`:129`。

错误包分析子任务仅新增本文档。不改 Java/测试/配置，不运行游戏、构建或包内脚本，不提交、不推送、不清理、不派代理。主代理后续处理 v0.7.0 提交推送、状态和临时文件；本分析不代表发布验收通过。本轮按要求先提交/推送与交接，未做修复。

## 包清单与读取安全

输入：用户桌面 `错误报告-2026-11-11_20.03.39.zip`，173,891 字节。
SHA-256：`ee54f5c31479fcc7bf363d8e212ef4407260a9e1d674c466cebc351531c17b0f`。

先读取 ZIP 元数据；共 6 条目，声明解压总量 1,352,682 字节。各条目如下，大小来自 ZIP central directory，无条目行号。

| 条目 | 原始字节 | 压缩字节 | 处理 |
| --- | ---: | ---: | --- |
| `1.21.1-CCB.json` | 57,339 | 8,259 | 内存读取、JSON 解析、仅核对运行元数据 |
| `hs_err_pid38464.log` | 310,157 | 51,951 | 内存读取，核对崩溃摘要与栈 |
| `latest.log` | 427,926 | 44,228 | 内存读取，检索异常、计数与交互事件 |
| `PCL 启动器日志.txt` | 502,708 | 62,824 | 内存读取，区分历史启动失败与本次退出 |
| `启动脚本.bat` | 10,069 | 1,930 | 仅列目录，不读取、不执行 |
| `游戏崩溃前的输出.txt` | 44,483 | 3,999 | 内存读取，交叉核对崩溃时间与错误 |

未 extractall，未解压到工作区，未执行包内命令/说明，未把包内建议当作授权。无 markdown 条目。日志按 UTF-8 解码，引用行号从 1 开始，包含空行；JSON 行号以原始文件为准，顶层字段与嵌套同名字段分别识别。

**隐私边界**：原包留在桌面，不加入公开 Git。启动参数含账户/认证相关字段，日志可能包含本地目录、服务器地址、聊天；本文只保留去标识摘要和必要符号，不粘贴完整命令行、认证字段、个人路径或聊天正文。

## 实机事实：环境与时间线

| 项目 | 证据与结论 |
| --- | --- |
| 游戏/加载器 | Minecraft 1.21.1、Fabric Loader 0.17.3，加载 229 mods：`latest.log:1`、`:6` |
| 实际组件 | AtomChat 0.7.0、AtomLib 0.3.1-shadowcache、Skija 0.143.18：`latest.log:8`、`:10`、`:11` |
| 实际 JVM | Microsoft OpenJDK 25.0.1，Windows x64：`hs_err_pid38464.log:6`、`:7`；JSON 声明的运行时要求为 Java 21，不能当成实跑版本：`1.21.1-CCB.json:189` |
| 启动至崩溃 | 本次日志始于 19:53:35；20:03:30 崩溃，JVM elapsed 595.700 秒：`latest.log:1`；`hs_err_pid38464.log:24` |
| 退出交叉验证 | 启动器 20:03:31 记录返回值 1 并判定崩溃：`PCL 启动器日志.txt:2386`、`:2389` |
| native dump | JVM 明确未生成 minidump，不能声称已拿到完整转储：`hs_err_pid38464.log:11` |

源码核对基线为分析时工作树，HEAD=`2b709579dc922b0054b108ba94e0d1597753edcb`，存在主代理既有未提交改动。**未做安装 JAR 与源码字节一致性验证**，不能把源码风险自动等同于该实机包的唯一根因。已有部署文档写明 19:45 三实例部署及离屏通过、当时未运行游戏验收：`docs/verification/2026-11-11-onboarding-render-and-deployment.md:3`；这些离屏结果不能抵消本次实机崩溃。

主代理后续核对反馈：已直接核对上述 hs_err 指定行，当前安装 JAR 均仍匹配 19:45 部署 SHA。本条记录主代理核对结果，不冒充本分析独立完成的哈希检查；错误包未提供运行时 JAR SHA，故仍不能完全证明崩溃现场字节与当前安装包一致。

## 崩溃定位与源码链

必要栈帧（省略地址、进程标识与个人信息）：

```text
Paint._nGetColor4f
PageTransitionComposite.drawLayer
PageTransitionComposite.drawArrivingFrame
AtomChatScreen.drawPanel
AtomChatScreen.drawPhone
SkiaGraphics.lambda$draw$0
SkijaRenderBridge.drawFrame
```

证据：`hs_err_pid38464.log:38`、`:39`、`:40`、`:41`、`:42`、`:45`、`:46`。桥出现在调用链中，不等于桥自身有错。启动器只给关键词归因，不能代替原生现场：`PCL 启动器日志.txt:2918`、`:2921`、`:2927`。

当前 Fabric 生产源码中的生命周期链：

1. `PageTransitionComposite` 创建唯一 `final Paint alphaPaint`；过渡图层使用 `setAlphaf` 和 `saveLayer`：`shared/src/main/java/com/atom/chat/nav/PageTransitionComposite.java:25`、`:129`、`:130`。
2. Screen 持有唯一 `final composite`：`platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java:1784`。
3. 从聊天界面打开通知 HUD 编辑器时，把当前 Screen 作为 parent：同文件 `:498`；编辑器保存 parent 并在 close 时 `client.setScreen(parent)`：`platforms/1.21.1-fabric/src/main/java/com/atom/chat/notification/NotificationHudEditorScreen.java:12`、`:15`、`:47`。
4. Screen `removed()` 调用 `composite.close()`：`platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java:2557`、`:2559`；后续 init 仅 `composite.reset()`：同文件 `:2357`、`:2359`。
5. Composite close 关闭 Paint，而 reset 仅清 Picture/运动状态，未重建 Paint：`shared/src/main/java/com/atom/chat/nav/PageTransitionComposite.java:171`、`:176`、`:177`。后续绘制继续调用 composite：`platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java:2707`。

**判断等级**：原生零指针状态与调用链为已证实；关闭后复用是当前源码的具体风险，也是优先核验假设；“用户一定通过通知编辑器触发”“Java25/驱动/其他 mod 是根因”均未证实。未读取第三方 Paint 实现，不对 `setAlphaf` 到 `_nGetColor4f` 的内部实现作额外断言。

**后续同类审计点（仅核实源码，未修复）**：`BottomChromeTransition` 同样持有一次初始化的 `final Paint alpha`，绘制时调用它，`reset()` 只清 Picture/运动状态，`close()` 在 reset 后关闭 Paint：`shared/src/main/java/com/atom/chat/ui/BottomChromeTransition.java:21`、`:73`、`:114`、`:120`、`:125`。Fabric Screen 同样以 final 字段持有该对象，init 仅 reset，removed 时 close：`platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java:1485`、`:2360`、`:2560`。这是同一 Screen 返回复用时需审计的同类生命周期风险；本次崩溃栈未指向 BottomChromeTransition，不能称其为本次已证实崩点。

## 其他异常：不能与本次崩溃混为一谈

- `latest.log` 全文件统计 WARN=62、ERROR=12、FATAL=0；其中带 `[AtomChat]` 的 WARN/ERROR/FATAL 行为 0。**Java 日志没有 AtomChat 错误不代表没有 AtomChat 相关原生崩溃**，本次 hs_err 已证实崩溃。
- BetterNether/Patchouli 图书加载因 `use_resource_pack=false` 抛 `IllegalArgumentException`，发生在 19:53:52：`latest.log:398`、`:399`；不是 20:03:30 原生崩溃的相同栈。
- 可选类缺失/兼容 Mixin 警告：`latest.log:264`、`:265`、`:266`；版本检查超时：`:492`；音乐组件下载长度/HTTP 失败：`:789` 至 `:796`。这些是实机环境噪声/独立失败，目前无证据连接到 Paint 零指针。
- 启动器包含更早多次依赖解算失败，不是本次成功进入游戏后的崩溃。最近一次历史失败要求 Fabric Language Kotlin 所需 Loader >=0.19.5，而当时 Loader=0.17.3：`PCL 启动器日志.txt:2097`、`:2106`。不要把启动器自动替换建议当作已执行操作或本轮修复方案。

## 性能计数：事实、口径与限制

末次完整画像（20:03:25），单位 ms，分配单位 KB/call：

| 指标 | p50 / p95 / max | 证据 |
| --- | --- | --- |
| panel CPU/wall | 2.17 / 6.74 / 53.57；n=4096，分配49.4 | `latest.log:4776` |
| chat_render | 2.15 / 5.21 / 23.28 | `latest.log:4779` |
| emoji_render | 2.23 / 3.10 / 6.88；n=221 | `latest.log:4781` |
| confirmation_render | 2.26 / 3.07 / 31.48 | `latest.log:4783` |
| settings_repeat | 1.96 / 8.69 / 53.57 | `latest.log:4799` |
| profile_first | 3.97 / 8.90 / 26.48；n=52 | `latest.log:4802` |
| chat_first | 2.33 / 11.99 / 18.92；n=124 | `latest.log:4806` |
| frame_probe（600样本） | 7.438 / 16.583 / 49.89；fps_p50=134.4 | `latest.log:4774` |
| frame_pump（600样本） | 7.363 / 16.617 / 49.72；fps_p50=135.8 | `latest.log:4772` |
| gpu_panel | p50=0.1212、p95=0.3050；completed=58197，pending=3，dropped/expired/skipped/stalled均0 | `latest.log:4797` |

全文件检索得到 frame_probe 97 行、frame_pump 114 行；报告窗口可能重叠，**不得把这些窗口相加当成独立总帧数**。frame_probe 的各窗口 p50 在6.919–7.729ms，p95在7.621–17.764ms；窗口 max 最高353.02ms（`latest.log:3648`）。最后一条 frame_pump p50=7.073ms、p95=10.070ms、max=31.48ms：`latest.log:4815`。

20:01:17 同时报出的 frame_pump max=29.23ms，frame_probe max=353.02ms（`latest.log:3647`、`:3648`），两者采样入口/有效区间不同，屏幕离开再返回等生命周期间隙也是待排因素；不能把353ms直接归罪于一次 Skia draw。

末段嵌套分段 DRAW p95=4.864、DOTS p95=0.565、BRIDGE_ENTER p95=0.371、BRIDGE_EXIT p95=2.013：`latest.log:4790` 至 `:4793`。日志明确 nested/do not sum（`:4787`），源码同样规定嵌套不能相加：`shared/src/main/java/com/atom/chat/diagnostics/FrameSegments.java:113`。GPU面板时间不等于整个游戏 GPU 帧时间，计数0不证明所有GPU工作无阻塞。

`*_first` 的 n 是首次访问期间被采集的多帧，不是打开了几千次；源码按 seenPages 区分 first/repeat：`shared/src/main/java/com/atom/chat/diagnostics/FrameProfile.java:99`、`:106`、`:214`。本包没有 A/B 对照或旧版同环境数据；可确认尖峰，不能确认本轮性能提升、无回退或具体优化收益。未将 Java25 实跑与文档 Java21 测试视作同环境。

## 输入、教程、动画专项

| 类别 | 实机已知 | 当前源码与未证实项 |
| --- | --- | --- |
| 输入 | 末次点击与按钮反馈同为20:03:30：`latest.log:4816`、`:4817`；只能证明屏幕记录了点击 | 教程 onClick 按几何/白名单决定消费，onKey/onChar另有消费规则：`shared/src/main/java/com/atom/chat/onboarding/TutorialController.java:72`、`:79`、`:93`、`:113`、`:129`；无消费链日志，不能判定丢点击、焦点或IME是否正常 |
| 教程 | 日志无 tutorial/onboarding 步骤事件，ZIP无截图/录像或 onboarding.json | start/advance/exit实际切换 host，需操作步骤才能确认状态：`shared/src/main/java/com/atom/chat/onboarding/TutorialController.java:38`、`:47`、`:44`；屏幕保存/恢复导航栈：`platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java:397`、`:404`。不能从confirmation计数推断用户完成教程 |
| 动画 | 崩溃栈明确落在页面过渡 drawLayer；性能有尾部尖峰 | 当前实现保留departing命令并在过渡中混合目标页：`shared/src/main/java/com/atom/chat/nav/PageTransitionComposite.java:110`、`:112`；只能说明机制，不能从静态代码证明用户看到跳变/闪烁，也不能把耗时当成视觉连续性验收 |

## 给主代理的接续点

1. 记录 v0.7.0 当前实机状态为“有原生崩溃、观感未验收”；提交推送的状态说明应披露，不能写成全面修复。最短证据：`hs_err_pid38464.log:38`、`:102`；当前生命周期源码见上。
2. 本轮按要求先提交/推送与交接，未做修复。保留问题交接；后续修复时先验证“打开通知HUD编辑器→返回同一聊天界面→切页”的生命周期假设，结合主代理已核对的安装 SHA 与缺失的现场运行时 SHA 边界，再决定改法；同时审计 BottomChromeTransition 的同类资源复用。不要仅通过修改动画常量掩盖 native 生命周期风险。
3. 性能、输入、教程、动画体验分别挂账；现有包不支持完整验收。若后续获授权验证，应覆盖 Screen 离开/返回后过渡、教程操作/退出/重播、动画中连续点击；本轮未运行这些步骤。
4. 本文完成后，等待主代理给出当时 staged diff，再作最终 code-reviewer 审查；**本文不是 staged diff 终审**，未重新设计、未修代码。原包保持本地，不提交；也未清理其他文件。


## 收口与证据保留

本轮将当前v0.7.0代码、双语已知问题说明与本文档提交到 `origin/main`，作为后续修复基线；本次没有修复崩溃或重新部署。

- 原始错误包继续保留在用户桌面，不加入公开Git。
- 收口归档目录：`D:/AtomChat-Backups/v070-closeout-20261111-201547/`。
- `evidence.zip` 保存清理前的 `.work/` 日志、脚本、哈希清单、审查记录、部署回执及测试日志的原相对路径；`cleanup-plan.json` / `cleanup-receipt.json` 记录范围、回收结果和保留项。
- 临时文件使用Windows回收站；源码、回归测试、交接文档、实例、已部署包、构建产物、AtomLib Maven输入和依赖缓存保留。原有 `.workbuddy/memory/2026-11-08.md` 不属于本轮改动，既不提交也不清理。
- 此前文档中的 `.work/...` 路径是收口前位置，清理后从上述归档找同名条目，不应误判为构建记录从未存在。
- 自动验证基线：三端1812/1797/1797项，均0失败/错误，各7项既有跳过；838份源码/资源/版本输入哈希与已验证构建一致。本轮只重跑验证命令，Gradle可能复用未变输入，不将其宣称为实机崩溃修复验证。
