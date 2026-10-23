# 0.3.3 物品消息与 Quark 兼容

## 范围与当前状态

- 基线：`main@34299d29485972425c354acd8ff52a1b3e2b873c`；本轮源码版本为 `0.3.3`。
- 用户确认保留 Quark 原有动作表情按钮、位置、样式和展开面板。
- 通用物品消息支持 Forge 1.20.1、NeoForge 1.21.1、Fabric 1.21.1；Quark 接入为可选，Fabric 不依赖 Quark。
- 当前没有提交、推送、部署或发布；游戏内组合验收仍待完成。

## 根因与实现

Quark 的物品图标由 `ChatComponentMixin` 包装原版聊天 HUD 的文字绘制调用来补画，而 AtomChat 打开时取消该 HUD 的绘制，因此原来的图标回调不执行。Quark 消息携带的原版 `SHOW_ITEM` 数据仍可用于物品渲染和 tooltip。

Quark 的动作面板在 `EmotesModule.Client.initGui` 中追加到屏幕的 `renderables`。AtomChat 的 `render` 不调用原版屏幕绘制，原先也不绘制这些附加项，导致面板消失；其 `NotButton` 在绘制时读取原始鼠标按下状态，单独恢复显示不足以解决面板外关闭和点击顺序。

- `RichTextLayout` 的视觉分段保留原始 UTF-16 索引，把同一连续物品悬停区间转换为一个图标槽；Quark 的三个空格由该槽占用，原消息不改写。
- 图标槽参加换行、点击命中、光标定位、选择背景和单行截断；引用胶囊、通知横幅共用同一截断方法。
- `ItemIconRenderer` 在 Skija 绘制前处理上一帧的可见请求，将 Minecraft 原生物品渲染到离屏纹理，再以借用纹理的 Skija Image 参加正常裁切、缩放、层透明度和遮挡；首帧允许先留槽、次帧显示图标。
- 请求与缓存各最多 128 项；纹理按不可变代际更新，只有 Skija 的释放回调确认不再使用后才删除；不进行逐帧 CPU 像素读回。
- 恢复附加聊天控件绘制时排除隐藏输入框；Quark 命中读取当前屏幕真实按钮边界，不复制布局常量，不缓存 resize 前的按钮实例。
- 输入在面板外关闭之前处理第三方控件；面板动画、根页面、图片预览、裁剪器、取色器和 AtomChat 菜单打开时不激活该层。
- 补全列表按真实窗口矩形优先命中；`ForeignChatPointer` 将被遮挡的主键按压屏蔽到释放，次键不会解除，resize 也不能重放同一次按压。原始按压/释放在动画门禁前观察，绘制时用屏外坐标阻止 Quark 自行轮询产生穿透。
- Fabric 使用测试专用 agent 执行 Fabric Loader 的包访问修正，Forge 使用测试专用 agent 执行其 EventSubclassTransformer，使真实原版注册表和物品数据可在普通 JUnit 中初始化；辅助 agent 不进入模组包。

## 源码依据

- Quark Forge 1.20.1 源码分支 `1.20@c1bb06230edad49552296444a7b5706463e82252`：`ItemSharingModule`、`ChatComponentMixin`、`EmotesModule`、`NotButton`。分支 `build.properties` 为 4.0 build 463；这是源码核对，不等于该二进制版本实机测试。
- Quark NeoForge 1.21.1 源码 `master@f8650cf6e1337fec26ffc2114c24fd882358a760` 的同名实现；分支元数据为 4.1 build 488，同样未作组合实测。
- [Quark 源码](https://github.com/VazkiiMods/Quark)、[Showcase Item 源码](https://github.com/starforcraft/Showcase-Item)、[Emojiful 源码](https://github.com/InnovativeOnlineIndustries/Emojiful)、[Chat Patches 源码](https://github.com/mrbuilder1961/ChatPatches)、[Chat Heads 源码](https://github.com/dzwdz/chat_heads)、[ChatAnimation 源码](https://github.com/Ezzenix/ChatAnimation)。
- 调查用源码与构建过程日志位于 `.work/0.3.3-compat/`，不随模组发布。

## 验证证据

- 初始 `InlineItemLayoutTest` 3 项在旧实现上全部失败，接入图标槽后通过；之后扩展为 9 项，使用真实钻石物品 payload。
- `QuarkEmotesCompatTest` 15 项覆盖当前屏幕识别、收起/展开、按钮边界、空隙、resize、继承字段、缺失或不兼容 schema、失败不重复探查。
- `ItemIconCacheTest` 4 项覆盖每帧请求去重、容量上限、近期使用淘汰、替换与清理时资源恰好释放一次。
- 通知横幅的图标截断新增回归在修复前于 NeoForge/Fabric 均失败；改为按视觉宽度截断后进入完整回归。
- 原始红灯、定向验证和完整构建分别记录在 `layout-red.log`、`quark-wiring-red.log`、`truncate-red-*.log`、`final-targeted-*.log`、`full-*.log`；最终完整结果见下表；完整测试 XML 已归档在 `.work/0.3.3-compat/final-full-test-results/`。

### 最终完整回归

| 目标 | 测试总数 | 失败 / 错误 | 原有跳过 |
| --- | ---: | ---: | ---: |
| Forge 1.20.1 | 1412 | 0 / 0 | 2 |
| NeoForge 1.21.1 | 1412 | 0 / 0 | 2 |
| Fabric 1.21.1 | 1424 | 0 / 0 | 2 |

- 三目标均执行 `test build --offline --no-daemon --console=plain -I ../../.work/skija-upgrade/local-recovery.gradle`；Forge 使用 JDK 17.0.18，另两端 JDK 21.0.11。
- ForgeGradle 的联网证书预检在当前网络失败，离线构建附加 `-Dnet.minecraftforge.gradle.check.certs=false` 跳过该预检；依赖仍完全使用既有缓存，没有关闭实际依赖下载的 TLS 校验或改变依赖版本。
- 最终完整日志为 `final-forge.log`、`final-neoforge.log`、`final-fabric.log`，汇总为 `final-test-counts.json`。
- 独立强模型首次复核发现补全框抢点击和遮挡结束后同次按压穿透两项 P2；修复后复核无剩余已确认 P1/P2。复核为源码与缓存 Minecraft 字节码核对，不代表游戏内验收。
- `ForeignChatPointerTest` 新增 5 项按压生命周期回归，全部先红后绿；新增屏幕接线守卫先证明动画门禁前缺少观察会失败。

### 最终产物与守卫

- 三加载器 × Windows x64 / Windows ARM64 / Linux x64 共 9 包，集中于 `.work/0.3.3-compat/dist/`；[完整文件名与 SHA-256](2026-10-22-mod-compatibility-packages.json)。
- 九包均通过 `tools/verify_jars.py --platform <目标平台> <明确指定的jar>` 和 `tools/verify_chat_assets.py`；native 系统/架构、Skija 版本、嵌套登记、资源与版本信息均通过。
- 九包均包含原生物品桥、平台 ModelView 接缝、Quark 可选兼容和按压屏蔽类；不包含测试 agent 或测试类；每个加载器三个变体的全部应用 class/assets 字节一致。
- 额外变体分别执行 `build -x test -Patomchat.nativePlatform=windows-arm64|linux-x64 --offline --no-daemon --console=plain`，沿用同一初始化脚本；Forge 仍执行完整 `build`，包括重混淆。
- seams 130/73、目标守卫、孪生 72 对/未归类 0/陈旧 0、日志 264/违规 0、裸值 471/505、`git diff --check` 均通过。
- 变异验证刻意移除 Forge 屏幕的原始释放观察，NeoForge 接线守卫按预期报“animation must not hide the release”；随后按原始字节恢复，重新运行全部 6 项 InputWiringSourceTest 通过。完整回归结果提前归档，未将变异产生的红灯混作最终结果。
- 最后读回校验 9 个候选包 SHA-256 均与清单一致。本轮没有启动游戏、改动游戏实例、提交、推送、打 tag 或发布。

## 其他模组的兼容边界

| 模组或机制 | 源码调查结果与边界 |
| --- | --- |
| Showcase Item、标准物品链接 | 标准 `SHOW_ITEM` 可进入通用显示路径；发送仍由原分享模组及其服务端支持负责，未逐版本组合实测。 |
| Emojiful | Forge 1.20 源码会将 ChatScreen 子类替换为自己的聊天屏，另有独立 emoji 字体渲染；本轮没有加入专用适配。 |
| Chat Heads、ChatAnimation | 修改原版聊天 HUD 的头像和动画，不会自动作用于 AtomChat 的消息面板；不据此认定一定崩溃。 |
| Chat Patches | 可改写消息格式、时间戳、重复计数以及原版聊天 UI，需要独立检查身份解析与界面功能；本轮没有承诺完整兼容。 |
| 自定义字体图标和其他整屏聊天替换 | 不会因通用物品图标接入而自动兼容；仍是独立适配项。 |

## 游戏内验收清单

自动化测试不代表以下项目已经实机通过：

1. Forge/NeoForge 与 Quark 共装，打开聊天，原按钮位置/样式、展开/收起、每个动作执行及服务端同步。
2. 点击 Quark 面板外按钮不关闭聊天；拖动、窗口缩放、GUI scale、手机/半屏模式均无错位。
3. 普通物品、附魔物品、自定义模型、时钟/指南针、多个物品混排，以及物品名称 tooltip。
4. 消息滚动至边缘时图标随消息裁切；开关动画、图片预览、裁剪器和菜单的遮挡无穿透。
5. 悬停、跨消息拖选、复制、引用和重连后加载历史，文字与原始消息一致。
6. Fabric 未装 Quark 时启动、聊天和标准物品链接正常；分享模组的发送能力另作组合验证。
7. GPU 帧耗、长期滚动纹理占用、资源重载、不同显卡与原生平台组合仍需真实客户端验证。


## 2026-10-23 Forge 实测反馈续修

- Quark 原表情层的显示与交互分开：入场时保留绘制；入场、页面切换、弹层和补全遮挡时屏蔽原始鼠标位置，按压到释放不重放。
- 原公屏去重把“自己发的”当成“本地已有回声”，导致 Quark 从原版 HUD 直接投递的自己的分享消失；现只有命中待确认的本地发送记录才去重。原版 `chat.type.text` / team 参数直接保留 sender/body Component 与 SHOW_ITEM，避免把有结构的信息重新猜成纯文本。
- FTB Teams 1.20.1 源码 `AbstractTeam.sendMessage` 使用 literal `<sender @team>` 包装其 `ftbteams.message.*` 通知；原守卫只看根 translation key。现对未知外壳的 siblings 检查明确系统键，保留真正玩家/私聊根键的优先级，玩家引用系统文本仍算玩家发言。
- root/detail/chip 切换期间统一屏蔽 click/drag/scroll；被门禁消费的 release 调用纯取消回调，清理 held、chip、slider、strip、selection、pending link、scroll drag，不执行卡片或链接动作。关于页作者使用现有折叠点阵标题。
- 用户批准将数据放在 `<game>/atomchat/`（与 config 同级），包括个人常用语、签名、屏蔽名单、头像/头图、壁纸、表情、个人外观预设、历史、图像缓存/服务器下载包、诊断 PNG、服务器托管缓存及专用 server-emotes。服务端认证档案仍在每个世界的 `atomchat/profiles/`，客户端/服务端配置与解析模板仍在 `config/atomchat/`。
- `atomchat/client/client-data.json` 存个人字段；迁移旧目录和 `atomchat-data/` 时先备份、读回比较，再退休旧文件。不同目标已存在时保留当前文件，旧字节进 `atomchat/backups/migration/`。失败不删原文件，日志和面板提示。旧目录每进程每组路径只尝试一次，重启才重试；旧客户端配置备份按 SHA-256 去重。损坏个人 JSON、链接/NTFS junction 路径不允许覆写。旧备份和临时配置文件也迁出 config。
- 专服不再从任何个人 `emotes/` 猜测分发内容，唯一分发源为 `atomchat/server/emotes/`；明确的旧 `atomchat/server/emotes/` 才能自动迁移。迁移备份也属于个人数据，整合包导出必须排除整个 `atomchat/`。
- 根因及回归失败/修复日志集中 `.work/0.3.3-feedback/`。初始功能回归 5 项按预期失败；结构化消息 stub 有 2 项按预期失败；迁移旧实现回归失败并按字节恢复当前代码。独立强模型发现 5 项 P2（重复迁移备份、JSON 独立重复备份、release 残留、专服个人表情晋升、正常读写链接绕过），新增回归后集中修正；链接回归在 Windows 优先使用符号链接，权限不足则用无管理员权限的 NTFS junction。

### 与本地 E33Chat 的对比范围

比对对象是 `D:/MDK` 当前源码中的 ChatClassifier、ChatPipeline、MessagePresentation 和相关捕获路径，并非所有历史发行版。AtomChat 的文字守卫、在线/已知名字匹配、tell-click、模板兜底主体是移植沿用；TTL 数值仍为 2 秒。AtomChat 在签名消息实际 HUD 投递时捕获、过滤后正文保留、逐线程携带元数据时间戳等方面有局部改进，不能称为整体识别全面增强。此次新增的是嵌套 FTB 系统通知保护、结构化直投消息保留和只按实际待确认本地发送去重。

### 源码证据

- [Quark ShareItemS2CMessage (1.20)](https://github.com/VazkiiMods/Quark/blob/1.20/src/main/java/org/violetmoon/quark/base/network/message/ShareItemS2CMessage.java) — 直接向 ChatComponent.addMessage 投递结构化 chat.type.text。
- [FTB Teams PartyTeam (1.20.1/main)](https://github.com/FTBTeam/FTB-Teams/blob/1.20.1/main/common/src/main/java/dev/ftb/mods/ftbteams/data/PartyTeam.java) 与 [AbstractTeam](https://github.com/FTBTeam/FTB-Teams/blob/1.20.1/main/common/src/main/java/dev/ftb/mods/ftbteams/data/AbstractTeam.java) — 明确的通知 key 与 literal 外壳。

当前仍需用户在真实 Forge + Quark + FTB Teams 组合中复验；自动测试不替代游戏内观感或 GPU 验收。部署将只替换用户明确指定实例的 AtomChat jar，不启动游戏，也不修改玩家数据；数据迁移在下次启动新版时发生。


### 续修最终自动验证

- 三端最终 `test build --offline --no-daemon --console=plain`：Forge 1435、NeoForge 1435、Fabric 1447 项，均 0 failures / 0 errors，各 2 个原有 golden/benchmark 跳过；Windows 符号链接权限不足时的 NTFS junction 隔离回归实际通过，未增加跳过项。
- 六个额外 native 构建使用 `build -x test -Patomchat.nativePlatform=<windows-arm64|linux-x64>`；Forge 的离线构建仍仅关闭 ForgeGradle 在线证书预检查。所有九包由真实分平台 build 目录提取，非改名重打包，native/版本/资源登记、测试代码未入包、同加载器三包 class/assets 一致性校验通过。
- seams 131/73、targets、孪生 73 对/未归类 0/陈旧 0、日志 262/违规 0、裸值 471/505、`git diff --check` 通过。最终完整 XML 和计数已保存在 `.work/0.3.3-feedback/final-full-test-results/` 与 `final-test-counts.json`。
- 初次独立强模型审查完成并发现 5 项 P2，主代理集中补红绿回归并修正；后续独立复核遭遇 429 重试耗尽，不能声称已由第二次独立审查关闭，现有结论来自修正后的自动验证与主代理源码复核。未作真实游戏/GPU 验收。
- 九包目录 `.work/0.3.3-feedback/dist/`；当前指纹见 [2026-10-23 包清单](2026-10-23-mod-compatibility-packages.json)。上文 2026-10-22 的旧测试数、旧包清单与部署前状态保留为初始候选历史，不能作为本次构建的当前状态。


### 已执行的部署与变异验证

- 将 Forge 公屏捕获中的条件临时恢复为 `if (ownIdentity || ownEchoText)` 后，`CompatibilityRegressionSourceTest.ownPublicIdentityAloneNeverSuppressesAnExternalSend` 按预期失败；finally 按原始字节与 SHA-256 还原，随后 4 项兼容接线测试全部通过。日志 `own-echo-mutation.log` / `own-echo-restored.log`；完整测试 XML 在变异前另存，未用局部运行覆盖最终统计。
- 2026-10-23 已部署到 `D:/Myworld/.minecraft/versions/1.20.1-main/mods/`，本地启动配置验证为 Forge 1.20.1 / 47.4.1。安装 jar SHA-256 `a3acee0b1a0d8f49166210b0675a95fd25188db0a35e482e3ecf43fbbc11555e`，旧包 SHA-256 `6213407c68082b065ee65818fff09fcc06c779fd074ebdbd137608ee7852d258`，备份 `D:/AtomChat-Backups/2026-10-23-forge-compat/20261023-004808-462887/atomchat-Forge-1.20.1-0.3.3-windows-x64.jar`。
- 实际核对其余 193 文件的 SHA-256、字节数和修改时间不变，AtomChat modId 只有一份；安装包读回哈希等于候选清单。部署未启动游戏、未执行针对真实玩家目录的数据迁移。


关键实现定位：`ChatPipeline.structuredPlayerLine` 保留原版结构化正文；`ChatClassifier.classifyByKey` 检查未知外壳的系统 siblings；三端 `AtomChatScreen.canRenderForeignChatUi/cancelTransitionPointer` 分开显示与交互并清理按下态；`InputGate.release` 在门禁中执行取消；`CacheDirs/DataMigration/PersonalDataStore` 负责迁移边界与字段拆分。最后移除了迁移后不再使用的 Platform import，重新编译三端，确认其 ProfileSync 字节码与打包前的同平台编译结果一致，交付包与已安装包哈希未改变。
