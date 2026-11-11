# v0.7.0 教程与渲染反馈交付记录

状态：2026-11-11 19:45（+08:00）已备份部署三个实例，版本仍为v0.7.0；最终三端build和中英文离屏GL视觉终审通过。未启动Minecraft，真实游戏验收未运行。

后续状态：20:03用户实机报告原生崩溃且效果未达预期；以上19:45自动验证属于历史结果，不能作为实机验收通过。见 [错误包交接](../handoff/2026-11-11-v070-error-analysis.md)。

## 本轮结果与根因

- 面板细缝：背景按全描边宽度内缩，但原描边中心线圆角未同步内缩；背景和描边的圆角轮廓不一致，且相邻抗锯齿边没有重叠。现在背景画到外轮廓下方，最终描边的中心线半径为 `外半径 - 宽度/2`，三端共用一次绘制。依据：`shared/src/main/java/com/atom/chat/ui/PanelBackground.java:27`，三端 `AtomChatScreen.drawPanel/drawBezel`。
- 两行输入和表情面板上弹：原 `BottomChromeMotion.target` 对任何矩形变化都启动导航弹簧，叠加了聊天布局已有动画。现在只有 CHAT/NAV/HIDDEN 模式切换启动弹簧，同模式跟随布局差值，不重启计时。依据：`shared/src/main/java/com/atom/chat/ui/BottomChromeTransition.java:25`、`BottomChromeMotion.java:45`。
- 默认主题：采用已确认 CCB 快照的十二项颜色、`panelOpacity=0.6990596`、`cardTint=0.59866506`；默认圆角25逻辑px、描边3逻辑px。背景 `#101418`，卡片/描边 `#14161B`，强调蓝不变。快照保存在 `.work/v0.7.0-onboarding/approved-palette.json`；代码见 `shared/src/main/java/com/atom/chat/config/AtomChatConfig.java:163`、`:213`、`:277`、`:371`。不覆盖已有配置，应用默认主题仍保留独立圆角设置。
- 描边宽度：测试测得当前1.25缩放下，3逻辑px对应3.75物理px，完全覆盖的核心为3个光栅行，没有除以二。旧版重复描边会强化抗锯齿边缘，边框与背景的颜色对比也影响视觉粗细；未用重复描画补偿观感。依据：`shared/src/test/java/com/atom/chat/ui/PanelBezelContourTest.java` 及最终 XML 输出。

## 新手教程

- 新老用户在首次可操作的面板页看到二选一：“不再提醒”“观看”；复用确认弹窗，新增教学图标、强调色主按钮，普通危险确认窗默认保持原样。观看默认获得焦点，Tab可切换；不通过Esc或遮罩产生未记录的第三种选择。依据：`shared/src/main/java/com/atom/chat/ui/ConfirmationDialog.java:35`。
- 提示决定立即写入 `atomchat/client/onboarding.json`，与可分发 config、图片缓存分开；无步骤游标。损坏原文件保留备份；临时写入失败时进程内抑制重复提醒，重开/重播重试，并给出可见失败状态。依据：`shared/src/main/java/com/atom/chat/onboarding/OnboardingStore.java:11`、`:41`。
- 16步覆盖导航、会话、输入发送、消息菜单/引用、搜索过滤、玩家屏蔽、资料、表情/颜文字/表情包、图片预览、快捷短语、设置总览、聊天设置组、外观搭配、隐私、通知和关于。带计数、上一步、下一步、跳过此步、退出；重播从第1步开始。依据：`TutorialSession.java`、`TutorialController.java:35`。
- 目标以真实布局或演示控件的同一几何绘制和命中；非目标区域拦截。独立演示输入不覆盖真实草稿；发送、上传、屏蔽、引用槽和快捷短语的写入均隔离。复用真实 EmojiPanel、QuickPhrasePanel、ToolMenu、ContextMenuController、ImagePreviewOverlay；表情包和图片样例仅存在内存。外观步骤可跳过，主动修改正常保存。依据：`TutorialDemoPanels.java`，official/Fabric 的两个 sandbox 构造入口。
- 关于页新增独立带图标的重播行。Profile hero、设置首页四卡和重播行的锚点来自现有布局函数。依据：`layers/mapping/official/src/main/java/com/atom/chat/settings/SettingsSectionPage.java:1065`、三端 `AtomChatScreen` 的 tutorial host。

## 审查中另外发现并处理

- 演示卡只换文字不足以教会操作，改为真实控件的沙箱接线。
- 外观步骤全键盘放行会穿透隐藏聊天输入，改为取色器、数值编辑、确认窗等明确编辑状态白名单。
- 快捷短语服务器行原按固定三条本地示例计算，改为读取当前条数；新增/编辑的缓冲现在可见。
- 演示菜单边界原复制一套公式，改为读取 `ContextMenuController.surfaceRect()`。
- 教程正文令牌实际大于标题，截图审查后改为20标题/15正文/16次色计数、23行距（均为缩放前逻辑令牌），保留长文滚动和弹层避让。
- 用户要求首次打开任意面板即提醒；设计初稿自行加入“仅世界聊天页”已删除。未新增生产长按菜单；“跳过”明确为跳过此步。

唯一 DeepSeek Flash 子代理承担设计、主代理接线审查和有界演示组件实现；主代理独立复核其组件实现与证据。没有新增第二个代理。审查记录：`.work/v0.7.0-onboarding/code-review-final.md`、`visual-review.md`、`visual-review-final.md`。

## 验证

| 最终完整 build | 测试数（含跳过） | 失败/错误 | 既有跳过 |
| --- | ---: | ---: | ---: |
| Fabric 1.21.1 / Java21 | 1812 | 0/0 | 7 |
| Forge 1.20.1 / Java17 | 1797 | 0/0 | 7 |
| NeoForge 1.21.1 / Java21 | 1797 | 0/0 | 7 |

共5406项，实际执行5385项、跳过21项。单元→套件→GL组件烟测→产物烟测已经执行；真实游戏acceptance未执行。初始红灯、整合失败及修正过程日志保留在 `.work/v0.7.0-onboarding/`，未放宽裸值基线。

- 838份源码/资源/版本输入在构建前冻结，构建后哈希一致；manifest SHA256：`01ea326da8c26eddb7b92f0314ff6feeed7260fc6377f22e104a5fc147085a17`。
- 中英文各176帧真实GL教程检查，覆盖16步、真实表情/工具/短语弹层、图片预览、搜索结果、批注与弹层无重叠、canvas保存栈平衡；截图为组件烟测，并非游戏截图。
- SLIDE/ZOOM真实设置页面中断前后内容像素差0；密集页面120稳定帧无多余压平，30次快速中断有界；1.5物理密度压平最大像素差1。
- 底栏真实GL点阵/卡片遮挡与旧参考逐像素一致；80运动帧、8次反向首帧连续，点阵Picture缓存无增长。
- 裸值实测433/基线441通过；targets/seams、diff检查通过。孪生检查仍有14个既有未分类残差，逐项指纹与本轮前基线一致，未新增；不声称全仓孪生绿。
- 三个JAR的CRC、教程类、屏幕接线、包内中英文键、版本、依赖锁与嵌套payload检查通过。构建输入 AtomLib 未变，不增加第三方框架。

## 渲染性能审计范围与限制

实际GL组件采样：中文稳定帧138个，绘制提交p50约0.70ms/p95约1.99ms/max约2.41ms；英文p50约0.70ms/p95约2.41ms/max约3.17ms。稳定统计排除每个视图前两帧，不含GPU等待和读回。

同时保留全样本：中文176帧p95约12.81ms/max约442.81ms；英文p95约7.85ms/max约390.74ms。它们包含独立JVM的冷字体/控件/管线初始化，不经过游戏客户端现有的预热生命周期，不能用稳定值掩盖，也不能直接当游戏首帧或FPS。旧设置页独立冷探针最高约606ms同理。真实游戏首次打开、模糊世界捕获、GPU争用、长时间内存走势仍需客户端采样。

本轮新增绘制不创建整屏离屏遮罩，使用差集裁剪；文字布局按文本/宽度/字号缓存，60个不变帧仅创建1次文本布局；演示图片只有有界内存资源并在屏幕移除时关闭。现有底栏逐帧Picture记录仍是后续可进一步剖析的热点，当前不能据静态代码断言其造成游戏卡顿。

第三方方案比较见 [手机组件比较](2026-11-11-phone-components-comparison.md)：本轮未引入；Modern UI应单页原型后再决定，Yoga仅补布局，Scene2D有第二套事件/绘制桥成本，AndroidX动效是Android组件。

## 部署与验收

部署时目标游戏进程为0；只替换AtomChat包，AtomLib、其他mod和配置不变。安装与候选SHA256、旧包备份指纹均已独立复查。

| 实例 | 安装SHA256 | 其他mod文件哈希确认数 |
| --- | --- | ---: |
| 1.20.1-main | `4abf538dee94fe424ea3e43756c337732585865302d1ec84c5cf32a6488c226e` | 181 |
| 1.21.1-CCB | `4ef31d0c97a7a74300e02499435ff6cdcb0ab9021566bd86f22fbd62bdcf226f` | 103 |
| Mechanomania-航空学 | `976c3a48124c0ead5aed6f0f70e55ee2fe041fad4e4c3eded80777b0627bc731` | 224 |

备份：`D:/AtomChat-Backups/2026-11-11-v070-20261111-194553/`。主回执：`.work/v0.7.0-onboarding/deployment-receipt.json`；备份目录内回执副本哈希一致；独立复查：`installed-independent-verification.json`。

最终图例：`docs/evidence/2026-11-11-v070-second-feedback/`，包含用户原图、提示窗、表情弹层、会话示例、图片预览、外观、关于和英文对照。

未启动游戏、未commit/push/tag、未公开发布。实机请检查：面板四角细缝/0–4描边与UI缩放，两行输入和表情包开关的旧手感，首次教程二选一/重开不再提示/关于重播/退出恢复草稿，自主外观修改与跳过保留旧搭配，以及游戏内首次和连续切页的帧耗。
