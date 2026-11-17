# AtomChat 0.9.0：消息兼容性静态调研与实施草案

状态更新（2026-11-17）：用户已验收UI。核心识别/确认学习/回显与服务端下发模板已有实现；自动兼容≥90%、TrChat首次陌生跨服在线/离线完整身份及头像仍未完成或未证明。纯文本陌生消息缺资料时仍可识别失败；不能将实现有据身份提取的构造测试当成无资料冷启动已通过。具体实现与缺口见 [实现记录](../../verification/2026-11-16-v090-implementation.md)。没有实际故障群组服，不把源码或单测称为实机兼容验收，版本尚未升级。下方原阶段清单为最初规划，是否完成须区分代码实现和验收。

GUI 方案见 [服务端配置主面板方案](2026-11-16-v090-server-config-panel.md)。以下“代码事实”保留的是实施前调查依据；当前行为以实现记录和源码为准。

**目标：** 改善 Paper、Youer、NCR 与聊天插件环境的消息识别、身份和装饰保真、私聊及回显处理；以代表性场景自动通过率至少 90% 为后续验收目标。

**架构方向：** 保留收到的完整消息组件和投递上下文，优先使用协议及结构化语义，按服务器积累有明确依据的身份和格式关系，最后才使用文本启发式。识别消息类型、解析字段、解析身份与皮肤、关联发送回显分别产出结果，不让一处猜测决定整条处理链。

**技术栈与平台：** 现有 Java 及三端结构；Forge 1.20.1、Fabric 1.21.1、NeoForge 1.21.1。共用算法优先放入 shared，MC 命名差异沿用 official 映射层和 Fabric 对应实现。本版不新增服务端配套插件。

## 1. 已确认的产品约束

1. 长期采用客户端兼容与可选服务端增强双轨；**0.9.0 先做纯客户端**，配套插件留到后续版本。
2. 90% 按事先确定的代表性场景通过率统计；关键场景必须通过。它不等于已覆盖全球 90% 的插件或服务器。
3. 90% 要求自动识别；手工模板、引导配置的补救成绩单独统计。
4. TrChat 不同子服间的玩家聊天转发是明确的故障场景及必过项。
5. 清空 AtomChat 缓存后，首次收到从未在本服出现过的跨服玩家消息也必须纳入必过项；不以先同服见过一次替代。
6. 正版、离线玩家都要识别；不能用同名正版账号替代服内离线身份。
7. 玩家名、装饰名、称号、前缀和正确头像属于验收内容；不能仅因变成玩家气泡就算完整通过。头像可正常异步加载，最终须对应实际玩家。
8. 关键词、字符、分隔符猜测只能放在最后一层兜底，不能用扩大词表承担主要兼容性。
9. 允许自动学习并按服务器保存已确认的身份、装饰名及格式关系；服务器间隔离，可清除。弱识别结果不能自证并污染学习数据。
10. 发送时立即显示本地气泡，确认服务器回显后更新原气泡，采用最终正文和装饰。
11. 本地消息先待确认，超时变未确认；只有明确拒绝才标失败。缺少回显本身不能证明发送失败。
12. 插件的公共频道消息留在公共流，保留频道标签和装饰；私聊进入对应会话。本版不新增独立频道页。
13. 用户最终更正：没有实际故障服务器及配置可供核对，本轮只能静态分析。此前“能管理服务端”的回答不能继续作为环境可用性的依据。
14. 下一步优先优化守卫和服务端/插件兼容，不以增加更多私聊关键词、分隔符或插件格式样例代替架构调整。
15. 0.9.0 另整合现有模组服务端配置 GUI：`/atomchat gui` 直达 AtomChat 主面板内的服务端配置页，不增加设置首页入口卡片；顶部“托管 / 资源包 / 常用语”分类 chip，复用现有设置卡片、布局和输入框，不自建美术。
16. 服务端配置页编辑草稿，底部固定文字“保存”按钮统一提交；收到成功回执后留在当前分类，失败保留草稿。返回聊天页，离开时有未保存改动提示；SVG 优先复用，没有适合的才补画。

用户指出了文本穷举的架构问题，但没有单独选择“保守／激进”全局模式；不将该回答记作对某一种模式的授权。

## 2. 本轮可确认的代码事实

### 2.1 AtomChat 当前实现

- official `ChatClassifier.classifyByKey` 对未知的翻译型根组件直接返回 SYSTEM；Fabric HUD 路径在文本私聊、tell-click 和模板前执行该短路。未知翻译键不等于已证明是系统通知，这个决策需要重审。
- `ChatPipeline.tryParsePlayerLine` 依赖在线及曾见过的名字。陌生跨服玩家不在集合内时，这条兜底不能提供发送者。
- `TellClickDetector` 即使提取到了私聊命令中的名字，也仍要求在线或 seen 身份；在线分支还要求可点击文字包含已知名字变体。不能把它当前的存在视为已解决任意昵称或陌生跨服玩家。
- shared `ChatTemplates` 已有类似 e33chat 的模板，仍以已知名字为门槛，且限制输入长度。单纯增加模板样例不能移除这些限制。
- `SeenPlayers` 按名字记录已见身份，断线清除；现状没有实现此次确认的按服持久学习。
- `SkinResolver` 读取在线玩家资料并维护 UUID／名字缓存，随后尝试皮肤 provider。向 provider 提供名字或空 UUID，不是其已取得真实跨服皮肤的证据。
- `PublicEchoTracker` 有正文匹配和整行后缀匹配，记录数量及时间有界；`PrivateEchoTracker` 当前只维护一个待匹配对象，匹配对象不包含正文。新回显模型不能只在这些判断外叠加更多文本规则。
- `PlayerRef.key()` 当前以名字生成键。需要检查会话容器的服务器隔离及身份升级行为，不能仅凭这个局部事实断言现有实现已经串号。

以上是静态阅读结论，不是已复现的服务器故障根因。工作区仍有既有未提交改动，本草案不回退或接管其渲染实验及用户资料。

### 2.2 上游 NCR 与原版组件结构

核对 NCR `1.21.1-Hotfix` 的 `MixinServerCommonPacketListenerImpl`：转换路径将装饰后的 Component 放入系统聊天包，没有单独保留原玩家聊天包的发送者字段。

同时通过 Minecraft Dev MCP 核对原版 1.21.1 的 `ChatTypeDecoration.decorate` 和 `ClientboundSystemChatPacket`：前者构造带参数的可翻译组件，后者编码完整 Component。

同版本 `ClientboundPlayerChatPacket` 的 signature 可以为空，sender UUID 仍是独立字段。因此不能用“有没有签名”代替消息包类型，也不能认为离线或无签名消息必然走 disguised/system 路径。

**修正旧记忆中的过度概括：系统消息通道不意味着组件已经被压成纯文本，NCR 转换也不必然丢掉翻译键和参数。** 实际还剩什么取决于上游装饰和后续插件处理。该结论不自动外推为所有版本、混合端和插件组合都保留同样结构。

来源：[NCR 转换实现](https://github.com/Aizistral-Studios/No-Chat-Reports/blob/1.21.1-Hotfix/src/main/java/com/aizistral/nochatreports/common/mixins/server/MixinServerCommonPacketListenerImpl.java)。原版核对记录为本轮 MCP 源码读取，版本 1.21.1、mojmap、上述三个类；没有运行 Minecraft。

### 2.3 TrChat 的跨服身份断点

核对当前上游 `v2`：跨服广播处理内部读取发送者 UUID、序列化组件等信息，随后交给组件发送管理器；其接收事件也持有 sender 和 receiver。

但现代 NMS 的直接发包路径调用系统消息发送接口，没有使用传入的 sender UUID。混合端、Adventure 或配置切换还可能走另一发送分支，必须分别分析，不能把一条分支当成所有环境。

默认 Global 格式含服务器标签和玩家名，但未在该格式中声明玩家 UUID 或皮肤属性。由此只能判断：客户端不一定能从最终聊天组件拿回 TrChat 内部曾持有的所有信息；不能假定客户端可直接读取代理和子服之间的插件消息。

来源：[跨服处理](https://github.com/TrPlugins/TrChat/blob/v2/project/runtime-bukkit/src/main/kotlin/me/arasple/mc/trchat/module/internal/proxy/BukkitProxyProcessor.kt)、[发送管理器](https://github.com/TrPlugins/TrChat/blob/v2/project/runtime-bukkit/src/main/kotlin/me/arasple/mc/trchat/api/impl/BukkitComponentManager.kt)、[现代发送实现](https://github.com/TrPlugins/TrChat/blob/v2/project/module-nms/src/main/kotlin/me/arasple/mc/trchat/api/nms/NMSImpl12005.kt)、[Global 默认配置](https://github.com/TrPlugins/TrChat/blob/v2/project/runtime-bukkit/src/main/resources/channels/Global.yml)。本轮未取得用户故障服的确切 TrChat 版本。

### 2.4 生态划分依据

- Paper 的现代聊天接口把 source、sourceDisplayName、message、viewer 分开交给 renderer。应先调查最终走哪条消息路径，不应默认“加了前缀就丢失玩家身份”。[Paper 官方文档](https://docs.papermc.io/paper/dev/chat-events/)
- EssentialsX 2.21.0 的说明记录了默认转用 Paper 新聊天事件及可切回旧事件的配置。同一插件名可能对应两种处理链，矩阵必须标明版本和配置。[官方发布说明](https://github.com/EssentialsX/Essentials/releases)
- Youer 是 NeoForge 与 Paper/Purpur 生态的混合端；宣传的 API 支持不能代替具体聊天路径兼容测试。[官方仓库](https://github.com/MohistMC/Youer)
- Floodgate 的皮肤转换有自己的资料来源和异步流程，不能把 Java 正版账号按名查皮肤当作所有身份环境的统一答案。[官方说明](https://geysermc.org/wiki/floodgate/features/)

### 2.5 进一步核对的生态差异

- Youer `1.21.1` 的 `ChatProcessor` 同时桥接旧 Bukkit 事件与现代 Paper 事件，根据 renderer 选择 CHAT 或 PAPER_RAW，并有保留原 PlayerChatMessage、替换 unsigned 内容及逐接收者投递的分支。不能笼统归为“混合端没有 renderer，所以只能文本匹配”。其 PAPER_RAW 的具体装饰定义也必须按实际注册资料读取，不凭名称套用其他 Paper 构建的参数约定。[聊天处理器](https://github.com/MohistMC/Youer/blob/1.21.1/src/main/java/io/papermc/paper/adventure/ChatProcessor.java)、[ChatType 补丁](https://github.com/MohistMC/Youer/blob/1.21.1/patches/net/minecraft/network/chat/ChatType.java.patch)
- Carbon 当前 `trunk` 将组件渲染与发送分开；其通用发送器向 recipient 发送渲染后的 Component，没有在这个调用点使用含原生聊天身份的发送重载。这说明“上游渲染时有 sender”不能直接推出“客户端收到了该 sender”。这是机制样本，不能代替回溯 1.21.1 对应发行版本。[发送器](https://github.com/Hexaoxide/Carbon/blob/trunk/common/src/main/java/net/draycia/carbon/common/messages/CarbonMessageSender.java)
- CMI 官方资料分别列出公共/私聊/管理跨服同步、独立聊天房间，以及可配置的悬浮和点击信息。应分别分析这些路径，不按默认公聊格式推断其全部私聊和频道行为。[跨服说明](https://www.zrips.net/cmi/bungeecord/)、[聊天房间](https://www.zrips.net/cmi/extra/chat-rooms/)、[点击悬浮配置](https://www.zrips.net/cmi/hex-colors/)

用户最初还写到“techat”；目前已确认的具体故障插件是 TrChat，不将未确认的拼写擅自当作另一款已完成调查的插件。

### 2.6 当前守卫与 e33chat 的实际差异

续轮只读对照本地 `D:/MDK` 的 e33chat 2.5.0，HEAD `b25fe477`，相关读取文件未显示未提交修改。该比较是本地源码比较，不是服务器命中率对比。

- AtomChat 的判断分散在 `MessageHandlerMixin` 和 `ChatHudMixin`。系统通道接收阶段已可能通过已知玩家名文本解析生成 meta，HUD 后续只在缺少 meta 时运行其他识别；现状不满足严格的“结构证据先、文本猜测最后”。
- AtomChat 的未知根翻译键短路 SYSTEM；e33chat 对未知键继续后续识别。不能把前者的保守程度直接等同于兼容性更好。
- e33chat 系统通道的声明式模板在通用启发式前；AtomChat 的用户公聊模板在通用名字解析及 EasyBot 之后，私聊模板则嵌在文本私聊阶段，不能笼统说所有模板都最后运行。
- e33chat 2.5.0 的模板有 sender、target、external 字段，正文也支持非末尾位置；AtomChat 当前模板不是全面更强的替代。不能沿用旧注释认定当前 e33chat 不支持后缀式正文。
- AtomChat 对原版队伍聊天翻译键显式归玩家；e33chat 对系统通道中的这类键仍走广播分类。这是局部差异，不是整体架构领先的证据。
- 两者的普通文本/tell-click 路径仍依赖在线或曾见身份，e33chat 的普通玩家模板也有已知身份门槛；external 模板接受陌生外部昵称，不代表找回了陌生 Minecraft 玩家 UUID 或皮肤。

守卫实施据此调整为：采集入口只保留事实，避免弱文本解析提前抢占；有明确语义的组件/身份关系先解析，声明式模板及经确认的学习结果先于通用文本兜底；所有路径保留证据来源和冲突结果。

## 3. 架构提案与必须保留的边界

### 3.1 先保留事实，再决定归属

接收时保留完整组件、组件树及样式、原生发送者和 ChatType 上下文、接收连接和投递序列。避免先转字符串，再试图猜回已经丢弃的字段。

建议处理顺序：

1. 解析本条原生协议上下文，区分传输通道与语义类型；系统通道仍可承载玩家聊天。
2. 利用注册的 ChatType 装饰参数、已知协议语义和翻译组件参数定位发送者、目标及正文；只有能映射到具体语义定义时，才把某个参数当发送者或正文，不能把任意翻译组件的第一个参数都当姓名。陌生翻译键保持未确定，不能只因陌生就判系统。
3. 检查发送者区域的实体悬浮信息、点击目标、插入值、在线资料及相互一致的组件线索。组件分块、颜色变化、单个私聊按钮均不能独立证明发送者身份；正文里的按钮不能冒充姓名证据。
4. 使用本服务器已确认的身份关系和经结构化消息校准的格式；显式用户模板保留为另外统计的补救路径。
5. 最后才运行有界文本启发式。无法确定时保留原消息及未确定结果，不捏造 UUID、收件人或发送确认。

每一步分别记录消息类型、字段位置、身份来源及冲突；不要用一个 system 布尔值同时表示“系统通知”“没有 UUID”“不知道是谁”和“抑制回显”。

### 3.2 身份、昵称与皮肤

- 分开保存服内 UUID、账号名、显示组件、频道标签及皮肤来源；昵称相同不等于同一身份。
- 正版与离线环境都以当前服内资料为依据。纯昵称不能触发同名正版账号的确定绑定。
- 跨服、重连、昵称改变、UUID 冲突和皮肤更新要使受影响关系失效或重新校准。代理入口地址不足以证明其所有子服共享同一身份空间。
- 持久数据只保存有明确来源的关系及格式信息，不因某条启发式命中而将其升级为已确认资料；清除学习不应删除聊天历史。
- 完整保留原消息和富文本；正文提及他人、前缀包含名字、假私聊按钮都必须成为负样本。

**可行性缺口：** 冷启动、陌生离线跨服玩家、无全局玩家资料、消息无身份或皮肤线索，这些条件同时成立时，客户端没有足够数据恢复任意服内真实皮肤。自动学习不能解决第一次的数据缺失。这个条件下的完整头像要求仍保留，但目前不能证明可实现；不得默认头像算通过，也不得为了指标事后删掉失败场景。

### 3.3 回显与私聊

- 本地发送产生稳定气泡 ID 和独立待确认记录，记录原始发送内容、会话、目标、连接和顺序；连续同文发送不能互相覆盖。
- 收到服务器回显时，证据足以关联才更新原气泡；保留最终可见正文、装饰及样式。插件改写正文后，文本相似本身不是已确认的发送关联。
- 无法消除歧义时不吞掉另一条消息、不误标确认。超时标未确认；后到的确切回显仍可校正。明确拒绝要能对应本次发送，不能把无关错误提示套给最新气泡。
- 私聊方向、真实对方和显示名独立提取；管理监听、第三方之间的私聊转录、正文引用私聊格式不能进入自己的私聊会话。
- 不从昵称或显示文字猜一个可执行的收件人命令；发送目标需来自已确认的会话身份或用户明确选择。

## 4. 调研及未来验收矩阵

下面是调研候选维度，不是已验证的兼容清单。确切服务器构建、插件版本和配置必须在实际验收前确定。

| 维度 | 候选样本与差异 | 本轮证据 |
|---|---|---|
| 现代装饰 | Paper + EssentialsX Chat；新旧事件路径、昵称和称号 | 官方说明与本地代码阅读 |
| 跨服重发 | TrChat + Velocity/Bungee；Global、Private、Redis 分支 | 已阅读上游部分发送链及默认格式 |
| 混合端 | Youer 1.21.1；插件发送分支与模组/NCR 组合 | 已核对旧/现代事件桥接、renderer 和 CHAT/PAPER_RAW 分流；未实测 |
| 防举报转换 | NCR；保留翻译参数和仅剩 literal 的输入分别覆盖 | 已核对 1.21.1 转换及原版组件实现 |
| 频道生态 | Carbon、VentureChat、HuskChat 等不同实现 | Carbon 当前主分支已读通用组件发送点；其他仍是候选，不能按名字宣称兼容 |
| 商业生态 | CMI、ChatControl 的公聊、私聊、重写与监听 | 已阅读部分官方功能/配置资料；未取得目标发行产物，不宣称完整路径已核对 |
| 身份装饰 | LuckPerms/PAPI、昵称、全局 TAB、离线及可变皮肤 | 分别调查资料生产者、传输路径和客户端可见性 |

场景至少包括：冷/暖缓存、正版/离线、本服/跨服、首次陌生发送者、昵称与账号不一致、同名碰撞、前后缀和多色名字、自定义翻译键、嵌套组件、正文包含假格式、收发私聊、监听消息、插件改写回显、连续同文发送、延迟和缺失回显、拒绝及断线、系统通知与机器消息。

后续场景通过必须同时符合该场景的消息类型、真实身份、名字/装饰、正文/富文本、频道/私聊方向、头像及回显要求。分项结果可以帮助定位，但不能代替完整通过。

静态推断、人工构造夹具和真实服务器回放分别标记。没有真实样本或环境的项目记未测，不能当通过，也不能从总体报告中静默消失。90% 分母及关键场景清单需要在实测前明确，不能靠增加简单用例抬高比例。

## 5. 分阶段交付提案

- [ ] 阶段一：以已完成的首轮静态调查为基础补齐来源矩阵，画出每类消息从插件事件到客户端组件的路径，标注身份/皮肤何处保留或丢失；优先核对冷启动离线跨服资料可得性。本阶段不把没有环境的推断当实测，也不要求穷尽全生态之后才能修复已经定位的守卫缺陷。
- [ ] 阶段二，下一步主线：优化守卫并同步处理服务端/插件兼容。统一接收事实和解析结果，收敛 Handler/HUD 多入口判定，禁止早期通用文本匹配抢占后续结构证据；未知翻译键保留未确定，明确系统语义继续排除；声明式格式和已确认组件证据先于通用文本兜底。优先覆盖 NCR、TrChat、Paper/Youer、自定义翻译键、昵称及嵌套组件，复用现有回归并补真实行为反例。
- [ ] 阶段三：实现有依据的按服身份/格式学习及皮肤关联，覆盖冲突和失效；不能以阶段三的暖缓存替代冷启动必过项。
- [ ] 阶段四：实现待确认发送记录与服务器回显校正，接通公聊和私聊；保持气泡稳定 ID、历史与现有引用/反应关联。
- [ ] 阶段五：在具备环境及样本后进行三端、真实插件和群组回归。整批实现后集中独审一次，修正后只复核受影响代码和用例。实测条件未具备时保持未验收。
- [ ] 同版独立任务：按 [服务端配置主面板方案](2026-11-16-v090-server-config-panel.md) 替换旧配置界面入口，复用现有面板/设置组件，保留服务端权威配置和回执语义；不依赖守卫是否成功猜中聊天格式，不引入新的 Paper 配套插件。

阶段二起的实现方案须根据阶段一结果收敛；本草案没有把尚不可证明的完整兼容承诺写成既定实现事实。

## 6. 受影响文件地图

| 文件或目录 | 提议职责 |
|---|---|
| `shared/src/main/java/com/atom/chat/chat/` | 共用解析结果、学习数据、身份关系和发送状态；复用已有存储及配置约定 |
| `layers/mapping/official/src/main/java/com/atom/chat/chat/ChatClassifier.java`、`ChatPipeline.java`、`TellClickDetector.java`、`PrivateChatParser.java` | 原生事实适配、组件解析和既有兜底收敛 |
| `layers/mapping/official/src/main/java/com/atom/chat/mixin/MessageHandlerMixin.java` | 在实际投递上下文采集事实，不提前丢弃结构 |
| `platforms/1.21.1-fabric/src/main/java/com/atom/chat/chat/` 及对应 `mixin/MessageHandlerMixin.java` | 等价 Yarn 适配，避免各平台自行扩格式规则 |
| 各平台 `mixin/ChatHudMixin.java` | 统一解析结果接入消息存储、私聊和回显校正；保留原组件 |
| shared `ChatTemplates.java`、`SeenPlayers.java`、`PublicEchoTracker.java`、`PrivateEchoTracker.java`、`PlayerRef.java` | 按新契约逐步迁移，保持旧配置和历史可读性 |
| 各平台 `image/SkinResolver.java`、shared `image/PlayerAvatar.java` | 真实皮肤来源及身份关系，异步到达后的正确更新 |
| official 与 Fabric `ChatMessage.java`、现有 Store 和消息列表 | 稳定气泡 ID、发送确认状态、服务器校正后的失效和重排 |
| 对应 shared/official/Fabric 测试目录 | 复用现有测试，新增能发现真实误归属、丢样式或回显串号的行为用例 |
| `docs/STATUS.md`、本草案及后续验证记录 | 在进入实施时更新进展；如实区分静态分析、单测与实机结果 |
| 现有 `ConfigScreenClient`、各平台 `ServerConfigScreen`、`ConfigScreenServer`、`ConfigPayloads` 与面板导航 | GUI 整合的边界和文件细节见独立子方案，不把它混入消息识别规则 |

未来本地测试使用各平台自己的 Gradle wrapper；例如在 `platforms/1.21.1-fabric` 下执行 `./gradlew.bat test --tests 'com.atom.chat.chat.*'`。最终三端需要受影响专项、适用全量检查和构建；通过这些仍不等于真实插件环境验收。本轮没有运行这些命令。

## 7. 当前交付边界

方案已进入实施，详见 [实现记录](../../verification/2026-11-16-v090-implementation.md)。阶段二至四的主要代码已有候选，后续真实生态样本和关键资料缺口仍未完成；上面的阶段清单不能自动视为整项验收通过。

没有真实服务器通过率，也没有完成首次陌生跨服离线玩家缺失资料的恢复。具体插件/构建、真实输入、完整场景分母及头像来源仍须取得证据，不能宣称“已兼容 90%”。
