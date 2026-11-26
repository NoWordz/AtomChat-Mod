# AtomChat 0.9.8 模组兼容与联动调查

核对了 11 月 26 日 Fabric 1.21.1 CCB、Forge 1.20.1 主实例、NeoForge 1.21.1 航空学中启用的 JAR 名称和相关 Mixin 清单。以下风险来自这些实际组合、AtomChat 的生产入口和 0.9.7 的已知复现。静态接线说明两方会触及同一个功能，故障结论以指定版本的实测记录为准。已有插件服测试和复现环境见 [0.9.7 兼容验证](2026-11-24-v097-compatibility.md)。

## 优先复测的组合

| 组合与实际版本 | 风险与验证方式 |
| --- | --- |
| **Chat Heads** 0.14.2 Fabric、0.15.2 Forge/NeoForge；**ChatAnimation** 1.1.3 Fabric、1.3.0 Forge、1.3.1 NeoForge | 实装 JAR 都挂到原版聊天 HUD，ChatAnimation 同时挂接聊天输入。测试进服、公屏/私聊、切回原版聊天、AtomChat 面板叠进场动画、长记录滚动与同一条消息只捕获一次。原版 HUD 处理的头像和动效由各 Mod 自己管理。 [Chat Heads](https://github.com/dzwdz/chat_heads) · [ChatAnimation](https://github.com/Ezzenix/ChatAnimation) |
| **ModernUI** 3.11.1.9 Fabric、3.12.0.1 Forge、3.13.0.1 NeoForge；**IMBlocker** 5.4.6/5.4.6.1/5.5.4 | ModernUI 这些 JAR 声明了聊天界面、键盘、字体和语言相关 Mixin，和输入、emoji、委托字体回退有交集。定向测试中文候选框、选字、剪贴板、补全、切换焦点、退出输入法，核对私用区及真缺字的回退。0.9.6 字体故障已有独立 [复现与反例](2026-11-24-v096-font-regression.md)。 |
| **Chloride** 1.7.5 Forge | JAR 声明 `QuickLanguageReloadMixin`、`FontShadowMixin` 及 JEI 叠层钩子。0.9.7 已实测设置详情语言混杂，修复后按当前语言实例刷新。0.9.8 也要复测扩展页标题、菜单、列表布局及 JEI 返回聊天时的语言。 [语言复现](2026-11-24-v097-compatibility.md#语言切换与定稿音效) |
| **Iris** 1.8.0 Fabric、1.8.12 NeoForge，**Oculus** 1.8.0 与 **Embeddium** 0.3.31 Forge，另有历史回归 **Gnetum** 3.3.6 | 光影与 HUD 合成是重点。实装光影包检查面板模糊、重载或分辨率变化后的锐度、物品图标和离屏纹理。Gnetum 的缓存帧缓冲历史上确实导致 HUD 回退，0.9.5 有隔离实测，0.9.8 紧凑卡片需做对应核对。 [历史证据](../STATUS.md) |
| **No Chat Reports** 2.9.1 Fabric/NeoForge、2.2.2 Forge | 0.9.7 已证明 Fabric 的玩家包与系统包两种配置需要独立覆盖。开关转换时分别发坐标、物品与 TPA，核对发件人、屏蔽、去重、HUD 与历史记录，以及无 AtomChat 的接收端。 [NCR 实测](2026-11-24-v097-compatibility.md#11-月-25-日-ncr全体提醒与英文布局复核) |
| **Xaero 小地图** 25.2.10 Fabric、26.2.0 Forge、25.3.13 NeoForge，三端均有世界地图 | 两个 Mod 的 JAR 都有聊天监听入口，实际消息可能已转为本地化组件。验证发收一次、坐标与维度正确、原生路径点命令保留，特别是冒号转义与非原版维度。0.9.7 的相关推荐验证见 [卡片实现](https://github.com/NoWordz/AtomChat-Mod/pull/3)。 |
| **Chat Patches** 和其他接管原版聊天的 Mod | 圈外高风险候选。查重启后消息持久化、盖住彼此快捷键、用原版历史时的重复捕获、完整聊天渲染替换时的回退。三套实例的上述抽样不包含本组实机配对。 [Chat Patches 项目](https://github.com/mrbuilder1961/ChatPatches) |

## 联动机会

| 优先级 | 方向 | 能做到的事和条件 |
| --- | --- | --- |
| 首个示例 | JEI | 由客户端附属从物品卡的原生物品栈打开配方。基线 Fabric 19.21.2.313、NeoForge 19.27.0.340、Forge 15.20.0.112 三套实际 JEI API 已对照。配置了同版 JEI 与示例后，验证获取真实物品、打开配方并返回 AtomChat。 [JEI 源码与开发接口](https://github.com/mezz/JustEnoughItems) |
| 优先延续 | Xaero、JourneyMap、VoxelMap、FTB Chunks | 扩展现有坐标卡识别，明确地图提供的保存路径点、当前维度定位与复制行为。分别验证各自支持版本、文件格式及单服和跨维度能力。用现有坐标与原生动作比对即可独立判断兼容。 [现有卡片支持来源](https://github.com/NoWordz/AtomChat-Mod/pull/3) |
| 可选客户端 | Simple Voice Chat | 可评估在玩家资料和会话中显示该 Mod 公开的连接、静音或语音组状态，让语音工具负责音频。航空学装有 2.6.24，Forge 主实例的语音包处于禁用状态。跨服和房间状态随语音服务器生命周期核验。 [Simple Voice Chat API](https://modrepo.de/minecraft/voicechat/api/overview) |
| 可选服务端 | FTB Quests | 若目标版本允许按任务 ID 打开详情，可由服务端产生任务或组队邀请卡，接收端按已装任务 Mod 能力打开。任务参与权限由任务所在的服务端核对。 [FTB Quests 源码](https://github.com/FTBTeam/FTB-Quests) |
| 继续验证 | TrChat、Velocity | 已有可选 TrChat 只读频道桥接和权限证据。适合作为新增导航页签的后续消费者。先复测 0.9.8 页面注册、跨服切换及撤权后的消息与未读更新。 [0.9.7 桥接证据](2026-11-24-v097-compatibility.md#trchat-频道桥接) |

## Fabric 实机联动

在隔离 CCB 中加载 JEI 19.21.2.313 和单独构建的 AtomChat JEI 示例。以工作台作为物品卡，从真实渲染坐标右键打开菜单，再点查配方。实际游戏打开了 `mezz.jei.gui.recipes.RecipesGui`，按 Esc 回到同一个 AtomChat 面板。输入栏按钮与设置首页入口分别到达附属详情页，底栏新增页签到达附属根页。聊天设置中选中附属页签，确认出现其折叠组和开关。

该回放使用含卡片、头像、模糊和第一批 API 改动的首轮集成包。Forge 与 NeoForge 构建对应的独立示例，游戏内 JEI 画面仍以 Fabric 证据为准。运行日志和原尺寸截图存于 0.9.8 工作证据的 `jei-live/integrated-fabric-controls`。

## 复测记录要求

每个组合记录客户端与服务端版本、Mod 版本、具体配置、输入与结果。故障优先用单变量关掉一个 Mod 对照。三端全量测试和附属编译属于不同层级，需要实机的项目应提供事件日志或截图，把尚未运行的配对保留为候选。
