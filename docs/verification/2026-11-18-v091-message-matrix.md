# AtomChat 0.9.1 消息兼容验收矩阵

> 后续方向已确认。通用验收优先检查守卫、模板和回显的正确性。以下 24 项保留为可复用样本，90% 自动识别率退出主验收。TrChat、Velocity＋Youer 按各自可复现的消息路径独立验证。

## 0.9.1 守卫与模板验收

- 协议身份与可核对的组件身份优先。系统公告、私聊监听、同名冲突留在正确路由。信息有冲突时保留原消息。
- 优先加固已有服务端模板编辑页和客户端配置文件。两处用同一套示例检验格式、冲突、样式切分及失败原因。
- 跨服格式明确时可显示服标、昵称及正文。缺身份时标为未知；带不出可信账号或 UUID 的消息不关联玩家头像、不进入可确认身份的私聊、不合并发送回显。
- 模板可以确定格式与方向。回显确认继续要求独立发送者与会话证据，并关联到具体待确认消息。

服务端模板页的预览会检查启用的服务端规则和本客户端的配置。正文按匹配区间提取，保留头尾空格、换行、样式与交互。重复正文的历史条目可存下当时已切分的富文本。旧客户端 `{sep}` 如果有多个正文边界，保留整行并提示改用固定分隔符。多条同文待确认消息会保持独立，未取到唯一对应项的回显也保留。

### TrChat 客户端组件夹具

| 可见路径 | 展示与回显 |
| --- | --- |
| 默认 Global，`[Lobby] Self: same`，两版固定源码可见格式 | 模板保留服标、昵称与正文。系统包缺独立身份，同名缓存也只显示「身份未知」，待发送气泡不确认。 |
| 默认 Private，`[Remote ➥ Self] same` 与 `[Self ➦ Remote] same`，2.5.2 固定源码格式 | 模板从 `➥`、`➦` 提取方向和正文。显示标签保留，缺身份时不写入可信私聊、不确认发送回显。 |
| 控制台 `Server: notice` 与 `[Spy] Alice -> Bob: hello` / `[监听]` | 用 Global 的专用模板时控制台原行保留，监听由守卫保留为系统消息。 |
| 单独的有证据对照夹具，两侧各有独立 UUID 和账号组件 | 可以确认本机发送者与接收者。还需唯一待确认发送及可关联正文。此分支是能力对照，固定发行版的默认跨服组件缺少该证据。 |

### Velocity＋Youer 客户端组件夹具

| 可见路径 | 展示与回显 |
| --- | --- |
| Youer `CHAT`，代理保留原生发送者 UUID 和原正文 | 同一公共会话的唯一待确认发送可以更新，展示渲染后的正文。 |
| Youer `PAPER_RAW`，代理保留原生发送者 UUID 和原正文 | 使用实际收到的渲染组件。回显有唯一关联时可以更新；渲染器省略的原文保持省略。 |
| 代理或插件只留下格式化系统组件 | 通过模板保留服标、昵称、换行、样式和正文交互。缺独立身份时标「身份未知」，待发送气泡不确认。 |
| 两个环境的私聊同文、他人同文、同名异 UUID 或队列淘汰 | 同文发送多解、接收者证据冲突或队列遗漏时，保留各条消息。原生发出私聊回执必须从接收者组件核对账号与 UUID。 |

两张表使用固定源码推导的客户端组件与合成对照夹具。插件实服发送、代理实机和客户端实际皮肤尚无环境验收数据。

## NeoForge 单人实测

2026-11-18 15:45–15:46，用户在 `Mechanomania-航空学` 实例使用 AtomChat 0.9.1 与 AtomLib 0.6.0 候选测试。截图、对应日志摘要及部署回执保存在本地收口归档。

- 从 AtomChat 公屏发送 `LOCAL-01 [队伍:1] hi`。收到原生玩家回包，截图仅显示一个气泡，括号、冒号和正文完整。
- 带金色称号的玩家消息保留 `[称号]E33EPUS` 与头像，账号仍为 `E33EPUS`。
- 服务端页面保存并启用了 `[{channel}] {display}: {message}`。玩家发送同形正文时，右侧气泡完整保留原文。系统消息没有玩家身份，在左侧显示服标、昵称、「身份未知」与占位头像，正文仍为 `[队伍:1] hi`。

本次实机覆盖单人公屏和一条已启用模板。模板冲突与富文本组合沿用夹具结果，TrChat 和 Velocity＋Youer 待对应环境回归。

## 实服回归步骤

发起回显测试时从 AtomChat 输入栏发送。公屏直接发普通消息，私聊从 AtomChat 的对应会话发送。每条使用独立编号，先发一条并观察旧气泡，再做同文干扰。记录插件版本、代理和所在子服，截下 AtomChat 原行、气泡及发送状态。

### TrChat

1. A、B 连接同一代理下的两个子服。启用默认 Global。用 `[{channel}] {sender}: {message}` 做公屏模板，若现场格式不同，以实际字面分隔符为准。A 在 AtomChat 公屏发 `TC-01 [队伍:1] hi`。B 核对服标、昵称、括号和正文都保留。默认跨服组件缺发送者 UUID 时，B 应看到「身份未知」。A 若收到同样的纯格式回包，原发送气泡应保持未确认，回包仍可见。
2. 用 `[{sender} ➥ {target}] {message}` 做收到私聊模板。若有服务端模板页，再用 `[{sender} ➦ {target}] {message}` 做发出私聊模板。A 从 AtomChat 私聊给 B 发 `TC-02`，B 回 `TC-03`。缺独立身份时，模板提取方向和正文，显示「身份未知」。如果有额外身份组件，核对 A 的发送者和 B 的接收者均能独立确认，再检查唯一 `TC-02` 气泡是否更新。
3. 若有第三个测试号可用监听模式，在 C 观察 `[Spy]` 或 `[监听]`。应保留系统原行，A 与 B 的回显状态不受 C 所见监听影响。B 在公屏发与 A 完全相同的正文，也不能确认 A 的待发送气泡。

### Velocity＋Youer

1. 通过 Velocity 进入 Youer 子服，先用默认 `CHAT`。A 从 AtomChat 公屏发 `VY-01 [队伍:1] hi`，B 收到后再发相同文本。若代理保留原生发送者与原正文，A 的原气泡只由自己的回包确认，B 的同文消息单独显示。
2. 如可切换到自定义 `PAPER_RAW` renderer，让服务端给正文加一个可见前缀，A 再发 `VY-02 hi`。原生身份与原文关联保留时，A 的原气泡应更新到实际渲染的正文。B 检查可见正文的样式和交互。若自定义 renderer 省略内容，客户端也应保持省略。
3. 单独测试只转发格式化系统组件的路径。以实际接收行填写公屏模板，例如 `[{channel}] {display}: {message}`，A 发 `VY-03 hi`。余下服标、昵称和正文时，应显示「身份未知」，没有真实玩家头像或私聊入口。A 的原发送气泡应保持未确认，回包另行显示。现场没有纯系统组件分支时，将这一项记为未测。

## 0.9.1 历史验收口径

本轮使用发行版源码和客户端消息夹具。生态验收按下表 24 个场景统计，每行一票，目标为至少 22/24，且 T01–T04 均须通过。通过需要目标版本的实际发送或该版本实际输出的回放，并逐项核对适用的消息归属、发送者账号和 UUID、实际皮肤、富文本与路由。手工模板单计补救效果。源码和手工夹具用于定位与验证客户端行为，生态分母保留待验证和受阻项。

## 固定输入

- TrChat [2.2.2（0867365）](https://github.com/TrPlugins/TrChat/releases/tag/v2.2.2) 与 [2.5.2（ad9d73b）](https://github.com/TrPlugins/TrChat/releases/tag/v2.5.2)。默认 Global 开启 Proxy、Force-Proxy、Double-Transfer，默认 Private 开启 Proxy。主要覆盖 Minecraft 1.21.1 接收端，1.20.1 同跑组件夹具。
- 代理分支使用 [Velocity 3.x 源码 843a47e](https://github.com/PaperMC/Velocity/commit/843a47e2a38325309cd66133149fc9a984f76bb8) 对照 TrChat 2.2.2，使用 [Velocity 4.x 源码 a9e6a01](https://github.com/PaperMC/Velocity/commit/a9e6a01735f4655169c61ae4913e9a9d26beecc0) 对照 TrChat 2.5.2 直发，BungeeCord 使用 [17d5d8c](https://github.com/SpigotMC/BungeeCord/commit/17d5d8cd5e6284d4b611bbeaa9f00f1bc2b41f20)。
- Paper 1.21.1 build 133 与 [EssentialsX Chat 2.21.0](https://github.com/EssentialsX/Essentials/releases/tag/2.21.0)。现代事件和旧版事件分别使用 chat.paper-chat-events=true 与 false。
- Youer 1.21.1 源码快照 [c31a5ca](https://github.com/MohistMC/Youer/commit/c31a5cac8a9c7a75aa5acd0012a98ecebce758c0) 分查 CHAT 和 PAPER_RAW。NCR 1.21.1 源码快照 [179da2e](https://github.com/Aizistral-Studios/No-Chat-Reports/commit/179da2e0ee754201ea080f644baee368801f9c70) 检查服务端将玩家消息重新编码为系统消息的分支。
- Carbon Chat [3.0.0-beta.28](https://github.com/Hexaoxide/Carbon/releases/tag/v3.0.0-beta.28) 使用 Paper 1.21.1 公共频道样本。CMI、ChatControl、VentureChat、HuskChat 尚缺具体版本的发送实例，本表适用于所列版本与分支。

## 场景

| 编号 | 环境及发送分支 | 通过条件 | 生态验收 |
| --- | --- | --- | --- |
| T01 | 2.2.2、Velocity、默认 Global、冷缓存正版 | 首次跨服归玩家，账号、UUID、皮肤、服标和正文均正确 | 默认消息缺资料 |
| T02 | 2.2.2、Velocity、默认 Global、冷缓存离线 | 同 T01，必须对应离线服实际身份与皮肤 | 默认消息缺资料 |
| T03 | 2.5.2、Bungee、默认 Global、冷缓存正版 | 同 T01 | 默认消息缺资料 |
| T04 | 2.5.2、Bungee、默认 Global、冷缓存离线 | 同 T02 | 默认消息缺资料 |
| T05 | 2.2.2、Velocity、默认 Global、暖缓存 | 曾从该身份空间观察到 UUID 和实际皮肤，复用该资料并保留频道装饰 | 待实际回放 |
| T06 | 2.5.2、Bungee、默认 Global、暖缓存 | 同 T05 | 待实际回放 |
| T07 | 2.5.2、Redis、默认 Global、暖缓存 | 同 T05，接收子服不同且服标正确 | 待实际回放 |
| T08 | 2.5.2、Velocity、关闭 Double-Transfer | 核对代理直发的账号、UUID、皮肤与正文 | 待实际回放 |
| T09 | 2.5.2、Bungee、关闭 Double-Transfer | 同 T08 | 直发系统包缺 UUID |
| T10 | 两个 TrChat 发行版的 Global 控制台格式 | 归系统，无玩家身份、头像或回显合并 | 待实际回放 |
| T11 | 2.5.2、Private、连发两条和重写 | 入向与出向正确，出向更新原气泡，无相邻串线 | 待实际回放 |
| T12 | 2.5.2、Private、监听转发 | 第三人私聊保留监听语义，无冒认和自回显 | 待实际回放 |
| P01 | Paper 原版玩家消息，保留发送者 UUID | 玩家归属、正文样式、原生发送者正确 | 待实际回放 |
| P02 | Paper + EssentialsX Chat 现代事件 | 前缀、昵称、正文样式及真实 UUID 正确 | 待实际回放 |
| P03 | Paper + EssentialsX Chat 旧事件 | 同 P02 | 待实际回放 |
| P04 | Youer CHAT | 结构化发送者与嵌套装饰正确 | 待实际回放 |
| P05 | Youer PAPER_RAW | 原生身份正确，保留渲染器传来的实际显示内容 | 待实际回放 |
| P06 | NCR 1.21.1 系统包转发 | 保留组件结构，按可见证据恢复正确身份 | 待实际回放 |
| P07 | Paper + Carbon 公共频道 | 频道标和样式保留，账号及对应 UUID 正确 | 待实际回放 |
| N01 | 系统公告、死亡、命令回执各一条 | 均归系统，无私聊与自回显误认 | 待实际回放 |
| N02 | 玩家正文嵌套翻译文本、括号及身份按钮 | 保留正文和交互，外层发送者正确 | 待实际回放 |
| N03 | 伪私聊、未知翻译、正文按钮各一条 | 无额外依据时无私聊或玩家身份误认 | 待实际回放 |
| N04 | 切子服后同名不同 UUID | 无同名错误身份或皮肤，无错误学习资料 | 待实际回放 |
| N05 | 两人同文、延迟、重写的自身回显 | 仅校正有匹配证据的旧气泡，其他消息保留 | 待实际回放 |

## TrChat 默认路径的资料边界

1. 两个版本的 Global 默认格式都把服名、玩家名和正文渲染为组件。服名和正文使用文本悬浮提示，玩家槽没有身份点击事件、实体悬浮、UUID 和皮肤属性。控制台有单独格式。 [2.2.2 默认格式](https://github.com/TrPlugins/TrChat/blob/v2.2.2/project/runtime-bukkit/src/main/resources/channels/Global.yml) · [2.5.2 默认格式](https://github.com/TrPlugins/TrChat/blob/v2.5.2/project/runtime-bukkit/src/main/resources/channels/Global.yml)
2. Global 的双重转发将 BroadcastRaw 发至代理再转交接收子服。接收端从原始组件构造消息并把内部 UUID 传给 BukkitComponentManager。UpdateAllNames 的账号与 UUID 进入子服内存，供插件查找。 [Velocity 转发](https://github.com/TrPlugins/TrChat/blob/v2.5.2/project/runtime-velocity/src/main/kotlin/me/arasple/mc/trchat/module/internal/listener/ListenerVelocityTransfer.kt) · [Bungee 转发](https://github.com/TrPlugins/TrChat/blob/v2.5.2/project/runtime-bungee/src/main/kotlin/me/arasple/mc/trchat/module/internal/listener/ListenerBungeeTransfer.kt) · [接收子服](https://github.com/TrPlugins/TrChat/blob/v2.5.2/project/runtime-bukkit/src/main/kotlin/me/arasple/mc/trchat/module/internal/proxy/BukkitProxyProcessor.kt)
3. 接收子服的 NMSImpl12005 直发分支调用 sendSystemMessage(component)。替代分支调用 component.sendTo，Adventure 实现调用 Audience.sendMessage(component)，普通实现调用 sendRawMessage(message)。这些调用都没有继续传入内部 UUID。Redis 也由接收子服处理 BroadcastRaw。1.20.1 的 NMSImpl 在现代协议分支同样发系统消息。 [2.2.2 发送](https://github.com/TrPlugins/TrChat/blob/v2.2.2/project/module-nms/src/main/kotlin/me/arasple/mc/trchat/api/nms/NMSImpl12005.kt) · [2.5.2 发送](https://github.com/TrPlugins/TrChat/blob/v2.5.2/project/module-nms/src/main/kotlin/me/arasple/mc/trchat/api/nms/NMSImpl12005.kt) · [组件发送实现](https://github.com/TrPlugins/TrChat/tree/v2.5.2/project/module-chat/src/main/kotlin/taboolib/module/chat/impl)
4. 关闭 Double-Transfer 时，TrChat 调用 Bungee 的 sendMessage 或自身的 Velocity sendIdentifiedMessage。固定 Bungee 快照对 Minecraft 1.19 以上发送 SystemChat，未将 UUID 写入该包。Velocity 路径有旧版 Adventure 身份重载时调用它，缺少该重载时只发送组件。旧版重载对应的最终直发包仍待核对。Private 跨服的 SendPrivateRaw 在接收子服以 null sender 发送，from 名称用于回信及监听。 [Bungee 发送](https://github.com/SpigotMC/BungeeCord/blob/17d5d8cd5e6284d4b611bbeaa9f00f1bc2b41f20/proxy/src/main/java/net/md_5/bungee/UserConnection.java) · [Velocity 发送](https://github.com/TrPlugins/TrChat/blob/v2.5.2/project/runtime-velocity/src/main/kotlin/me/arasple/mc/trchat/module/internal/listener/ListenerVelocityTransfer.kt) · [2.5.2 接收子服](https://github.com/TrPlugins/TrChat/blob/v2.5.2/project/runtime-bukkit/src/main/kotlin/me/arasple/mc/trchat/module/internal/proxy/BukkitProxyProcessor.kt)

由此可推断，默认 Global 路径只凭接收组件无法区分同名的正版和离线玩家，也无法得到首次陌生离线玩家的实际皮肤。若现场另有跨服玩家列表或能提供明确身份的组件，需用实际数据另验。手工带身份的夹具不计入 T01–T04。缺实际插件回放，生态通过率未测定。

## 其他发送分支和客户端夹具

- NCR 开启 convertToGameMessage 时先装饰原玩家包中的未签名内容或原文，再发系统包。外层发送者字段随原玩家包消失，标准聊天翻译组件的发送者和正文参数仍可参与识别。 [NCR 实现](https://github.com/Aizistral-Studios/No-Chat-Reports/blob/179da2e0ee754201ea080f644baee368801f9c70/src/main/java/com/aizistral/nochatreports/common/mixins/server/MixinServerCommonPacketListenerImpl.java)
- Youer 默认 renderer 使用 CHAT，自定义 renderer 使用 PAPER_RAW，可按接收者替换 unsigned content。固定快照的 RAW 注册使用 withSender，客户端以实际收到的装饰结果为准。 [Youer ChatProcessor](https://raw.githubusercontent.com/MohistMC/Youer/c31a5cac8a9c7a75aa5acd0012a98ecebce758c0/src/main/java/io/papermc/paper/adventure/ChatProcessor.java) · [ChatType 补丁](https://raw.githubusercontent.com/MohistMC/Youer/c31a5cac8a9c7a75aa5acd0012a98ecebce758c0/patches/net/minecraft/network/chat/ChatType.java.patch)
- 两套映射的 `ComponentIdentityTest` 复现 Global 的文字悬浮、服标、玩家名和正文区。冷缓存缺身份时无归属，暖缓存恢复 UUID 与正文。扩展夹具覆盖明确点击与实体证据、带冒号的名称区、账号冲突及正文中的括号和标点。
- `StructuredPlayerMessageTest`、`ChatClassifierTest`、`PrivateMessageRecognitionTest`、`OutgoingMessagesTest` 和 `RecognitionMemoryTest` 覆盖结构化及未知翻译、系统负例、私聊方向、回显和按服务器地址复用。代理入口地址相同的两个子服仍需实际身份依据。
- 独审及增量复核通过。最终夹具覆盖空身份的默认 Global、暖缓存身份、名称组件内的冒号、独立分隔符交互、旧名称与已观测账号的冲突，以及带旧式颜色码的引用。包含这些夹具的三端聊天专项各 350 项，通过且无跳过。
