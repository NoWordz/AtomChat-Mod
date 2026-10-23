# AtomChat 当前状态

> 截至 2026-10-23。这里只维护当前快照，提交及推送事实以 Git 查询为准。

## 0.4.0-preview：横幅专项已验收

- 用户于 2026-10-23 确认本轮验收通过，要求提交、推送、更新状态并将临时文件移入回收站。版本保持 `0.4.0-preview`，交付分支 `main`，推送目标 `origin/main`；提交号和远端同步事实以 Git 查询为准，不打 tag 或公开发布。
- HUD、面板与编辑器横幅复用卡片族材质，继承卡片 RGB/透明度，底色最低 50%，局部背景模糊；不透明底色跳过无效滤镜。
- 入退场跟随全局 zoom/slide，旧横幅始终滑动让位；保留通知出生时间，兼容面板开关和 HUD 九宫格。修复重叠误触旧回复按钮、锚点跳位及 dismiss 后切 HUD 暂时消失。
- 三端完整 `test build`：Forge/NeoForge 各 1476 项，Fabric 1488 项，失败/错误均为 0，各有 2 项原有跳过。Forge 新 JVM 事件加载顺序回归及真实 RTX 5060 局部模糊探针通过；独立初审两项 P2 已补失败回归并修复，独立复审因 429 未完成。
- 三个 Windows x64 包已备份并部署到 `1.20.1-main`、`1.21.1-CCB`、`Mechanomania-航空学`；其他顶层 mods 文件分别为 157/73/200 个，哈希、大小和修改时间均未变。本轮未重建 ARM64/Linux 包。
- 用户确认崩溃来自旧版 0.3.2 报告：直接崩溃点为 JVM C1 编译线程，字体日志不能证明因果；没有修改当前字体代码或 Java 配置，也不声称修复了 JVM。
- 详见 [横幅验证与验收](verification/2026-10-23-notification-banners.md)、[包指纹](verification/2026-10-23-notification-banners-packages.json)、[安装记录](verification/2026-10-23-notification-banners-deployment.json)。本轮临时证据收工时归档到 `D:/AtomChat-Backups/2026-10-23-notification-banners/closeout-evidence/` 后移入回收站；部署备份与长期视觉证据保留。用户整体验收不扩大为多人专项、不同 GPU 整合包帧耗或 ARM64/Linux 实机验收。

## 历史基线：0.4.0-preview 实时模糊与四组目录

- HUD 与面板横幅保留原有不透明浮动通知材质、下落动画和共享队列；仅修复跳转图标的强调色对比色。
- 面板背景模糊改为每帧刷新；步进箭头 hover 使用全局渐变。
- `atomchat/` 顶层收为 `client/`、`cache/`、`server/`、`backups/`；客户端个人目录首次启动创建，旧目录自动校验迁移。
- Forge/NeoForge 各 1467 项、Fabric 1479 项测试，0 失败/错误，各 2 原有跳过；九包完成构建与一致性校验。
- 已备份并部署三端 Windows x64，其他 193/432/215 个文件未变。改动已提交到 `main`；未推送或公开发布，部署代理未启动或关闭游戏。
- 见 [本轮验证](verification/2026-10-23-0.4.0-preview.md)、[包指纹](verification/2026-10-23-0.4.0-preview-packages.json)、[安装记录](verification/2026-10-23-0.4.0-preview-deployment.json)。

## 0.3.3 兼容修复与个人数据迁移

- 已按 Forge 实测反馈修复 Quark 入场延迟显示、外部模组自己的物品分享被错误丢弃、FTB Teams 嵌套系统通知误识别；root/detail/chip 动画期间锁定指针，并在释放时纯取消按下态，作者信息使用折叠点阵标题。
- 用户批准个人数据根目录为与 config 同级的 `atomchat/`。常用语、签名、屏蔽名单拆至 `client-data.json`，素材/历史/预设/缓存/诊断图及旧 atomchat-data 迁入同根；校验备份后退休旧副本，冲突/失败提示、原文件保留、备份去重、拒绝链接/NTFS junction。服务端只分发明确的 server-emotes。
- 最终三端 `test build --offline --no-daemon`：Forge/NeoForge 各 1435 项，Fabric 1447 项，0 失败/错误，各 2 项原有跳过；Windows junction 隔离回归实际通过。独立审查发现 5 项 P2，已补回归并修正；后续独立复核因 429 重试耗尽未完成，不声称二次复核通过。
- 三加载器 × Windows x64 / Windows ARM64 / Linux x64 九包通过 native/版本/资源、测试代码未入包及同加载器应用 class/assets 一致性校验；构建临时目录已在收尾时移入回收站，见 [指纹](verification/2026-10-23-mod-compatibility-packages.json) 和 [验证记录](verification/2026-10-22-mod-compatibility.md)。
- 已依用户自动部署授权替换 `D:/Myworld/.minecraft/versions/1.20.1-main/mods/atomchat-Forge-1.20.1-0.3.3-windows-x64.jar`，SHA-256 `a3acee0b1a0d8f49166210b0675a95fd25188db0a35e482e3ecf43fbbc11555e`；旧包备份为 `D:/AtomChat-Backups/2026-10-23-forge-compat/20261023-004808-462887/atomchat-Forge-1.20.1-0.3.3-windows-x64.jar`（旧 SHA-256 `6213407c68082b065ee65818fff09fcc06c779fd074ebdbd137608ee7852d258`），其他 193 文件哈希/大小/修改时间不变，modId=atomchat 只有一份。
- 代理未启动游戏、未修改玩家图片或聊天记录；迁移在下次启动新版时发生。Quark/FTB 共装实机与 GPU 验收仍待用户复测。该轮记录形成时未提交、推送或发布，相关源码已随当前 `0.4.0-preview` 提交归档；2026-10-22 初始候选和 0.3.2 基线材料不代表当前续修已实机验收。

## Skija / Windows ARM64 迁移

- 用户授权尝试升级以支持 Windows ARM64；Skija 现钉 0.143.18（仓库根单一定义），types 0.2.0，模组版本仍为 0.3.2。已适配字体、PathBuilder、ColorType 与 GPU flush 接口。
- 三加载器 × Windows x64 / Windows ARM64 / Linux x64 九包均构建，文件名统一以系统与架构结尾；完整测试 NeoForge 1377 / Fabric 1389 / Forge 1377，均 0 失败、0 错误，各 2 原有跳过。
- 九包 native/登记/版本/资源与同加载器应用内容一致性通过，Windows/WSL Linux x64 native CPU 冒烟通过；独立审查四项 P2 已修复并复查通过。详见 [迁移验证](verification/2026-10-21-skija-windows-arm64.md) 与 [包指纹](verification/2026-10-21-skija-packages.json)。
- 候选包集中于 `.work/skija-upgrade/dist/`。用户已反馈所部署 Windows x64 候选渲染正常，并授权三加载器改动提交、推送。该反馈不代表 Windows ARM64 实机、Linux 新版 GPU、其他加载器运行或量化性能已逐项验收。
- 已按用户授权部署到 Windows `1.21.1-CCB` Fabric 实例：`atomchat-Fabric-1.21.1-0.3.2-windows-x64.jar`，SHA-256 `5bcd0e33cc13dd06e4ebd3c97114ff5b3a5e374d12aa39634b7f7a4d0c238974`。旧包已备份，其他 73 个文件哈希/大小/修改时间不变，只有一个 AtomChat；由用户启动并完成上述 x64 渲染验收。
- 本轮仅提交、推送源码，不打 tag 或发布；具体完成状态以 Git 查询为准。下文旧六包与旧 CCB 哈希是升级前已验收基线。商店正文与正式发布说明未同步此次候选。

## 项目与验收

- 仓库 `D:/AtomChat-Main`，分支 `main`，远端 `origin` 为 `NoWordz/AtomChat-Mod`；三端为 Fabric 1.21.1、NeoForge 1.21.1、Forge 1.20.1，源码版本为 0.4.0-preview，下文旧版本记录为历史基线。
- 用户已确认图片预览/过滤、半屏与面板外关闭均验收通过，授权提交、推送和清理旧轮次材料；本次不打 tag 或发布。
- 图片预览支持平滑淡入、鼠标锚点滚轮缩放与左键拖动；三种弹层统一裁切，保存预设与默认主题单一选中，公屏系统/玩家过滤持久化。见 [预览与过滤验证](verification/2026-10-20-preview-and-filter.md)。
- 半屏模式位于外观→调整→布局，左、上、下贴边，顶底栏贴边并去掉手机外环。原宽度滑条分别保存手机/半屏宽度，切换及重载后恢复；内部卡片、气泡和主题保留。
- 左键点击面板外世界画面正常关闭并消费事件；拖动移出面板不关闭。伸出面板的原生命令补全框仍可点击，弹层打开时不激活底层补全。
- 半屏应用个人预设不覆盖手机宽度；手机应用预设保留原宽度行为。详情见 [半屏方案](superpowers/plans/2026-10-20-half-screen.md) 和 [半屏验证](verification/2026-10-21-half-screen.md)。

## 最终验证与部署

- 三端完整 `test build --offline --no-daemon`：NeoForge 1375 / Fabric 1387 / Forge 1375，均 0 失败、0 错误，各 2 项原有 golden/benchmark 跳过；独立审查发现的两项 P2 已补失败回归并修复，复查无阻断。
- Windows/Linux 六包已构建；对应 native、版本、资源及同加载器 class/assets 一致性通过。收工前再次核验包内容、CCB 安装包和旧包备份哈希；没有重复游戏测试或性能测量。
- seams 125/71、targets、孪生 70 对/未归类 0/陈旧 0、日志 262/违规 0、bare 471/505 及 `git diff --check` 通过。
- CCB 安装：`D:/Myworld/.minecraft/versions/1.21.1-CCB/mods/atomchat-Fabric-1.21.1-0.3.2.jar`，SHA-256 `a3c59c8baa0cac9c41a6c11ae24eddd82f4cce1020f66c79f39b21c71c01e244`。
- 原已验收包 `43ae7c6a…` 保留在 `D:/AtomChat-Backups/2026-10-21-half-screen-CCB/111741-540159/`；部署时其他 73 个文件未改动，代理未启动游戏，随后用户反馈验收通过。

## 仍有效的约束与待办

- shared 为公共逻辑；official/Fabric 同名源码按映射等价维护，修改公共面须跑 seams、targets 与孪生检查。
- 玩家档案以服务端认证身份授权，按世界保存；头像图片先持久化再发布元数据。保留 [同步约束](superpowers/plans/2026-10-11-profile-sync.md)、[同步验证](verification/2026-10-12-profile-sync.md) 和 API 探针。
- 消息反应的实际内容键机制与边界以 [反应验证](verification/2026-10-12-reactions.md) 为准；过期的指纹/回执实施方案已清除。
- 多人同步、重连、延迟队列及 GPU 量化性能尚无逐项实机证据；[媒体性能审计](superpowers/plans/2026-10-12-media-performance-audit.md) 中两项真实传输/剖析仍未完成，不将整体验收当作性能测量。
- 延期：反应悬停名单超过 6 人仍只显示剩余人数。WSLg 性能及鼠标锁定未定因，已解决的字体/音效不在本轮重开。

## 资料与清理

- 长期设计见 [design.md](design.md)，玩家/服主/开发者手册见 `docs/wiki/`，商店正文仍由现有工作流引用，保留。
- 已完成旧轮次方案、过期验证图、历史 STATUS、旧审计及散落构建日志按用户要求移入回收站；当前两轮 0.3.2 方案/验收记录、有效协议与未完成审计保留。
- 临时证据已按逐文件哈希归档到 `D:/AtomChat-Backups/2026-10-21-closeout/`，历史 `.work` 路径从归档查阅；候选六包、已安装包和旧包备份保留。
- 本轮 `.work/` 构建与部署临时目录、`logs/` 运行日志已移入回收站；长期验证文档、视觉证据、包指纹和部署记录保留在仓库。
