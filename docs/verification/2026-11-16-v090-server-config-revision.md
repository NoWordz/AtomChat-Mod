# 服务端配置修订：实现、验证与测试部署

> 后续视觉复核撤回了本批资源/列表视觉通过判断。2026-11-17 已另做[限定视觉纠偏](2026-11-17-v090-server-config-visual-correction.md)，新候选等待主代理复审，尚未部署；本页的00:02部署是仍保留的上一批历史事实。

2026-11-16 开工，2026-11-17 收口。根版本仍为 **0.8.0**。本轮按已确认 grill 与审查后的草图实施，属于 0.9.0 开发测试候选，不是正式发版，也不是聊天插件自动兼容率 90% 的验收结果。

## 已交付的行为

- 命令仍是唯一入口：/atomchat gui 进入现有主面板，分类为托管、资源包、消息模板；没有新增设置首页卡片或根导航 tab。
- 共享现有设置分组栏、SVG、点阵、折叠、合并卡片、行 hover、PressScale、UiStagger 与滚动条。卡片、行、字段绘制与命中使用同一份几何，动画中的命中反算到原坐标。
- 设置页旧数值输入和服务端表单共用轻底细边表面。新增文本模型支持光标点击、拖选、剪贴板、撤销/重做、Unicode 代理对、逻辑及软换行移动、Tab 焦点和焦点滚动。数值与操作区按实际文字宽度换行，单位单列。
- 底部固定保存区采用导航底栏外部和内部留白；停靠布局跟随现有壳层。字段错误参与行高，名称未填不会把正确格式框一起标红。
- 托管页补 maxProfileTotalMb，实际写回服务端个人资料库额度。资源包名称字段正确解释为服务器显示名，留空取 MOTD。
- 常用语有逐条新增、编辑、删除、上下排序；允许条目内部换行，不再用一个多行框拆成若干条目。玩家点击常用语仍是填入聊天输入栏，不改成直接发送。
- 表情包有真实服务端清单、本机文件选择、缩略图、独立预览、导入和删除。导入数据先留在总草稿，保存时才交给服务端；放弃本地草稿不发布这些数据。只管理 CacheDirs.serverEmotesDir()，不借用个人表情目录。
- 常用语和模板的单条“完成”及返回/Esc 只回列表并保留总草稿。重新进入单条编辑会重置本次编辑的撤销历史与滚动，避免相同文本的不同条目串用编辑状态。根页保存统一提交；成功留当前分类并以权威快照更新版本和基准，错误保留草稿。离开整个配置页才确认放弃，返回原聊天并沿用既有聊天状态保留路径。

## 模板契约与身份边界

持久化位置仍是服务端 AtomChat 配置文件，新增 templates 列表；旧客户端手改 chatTemplates / whisperTemplates 的入口保持不变，本轮没有新增客户端本地模板 GUI。

| 类型 | 必需字段 | 方向 |
|---|---|---|
| PUBLIC | sender、message | 公共消息流 |
| INCOMING | sender、message | 对应发送者的私聊；若提供 target，必须与本地账号相符 |
| OUTGOING | target、message | 本地玩家为发送者；若提供 sender / uuid，必须与本地身份相符 |

可选字段为 display、channel、uuid；uuid 描述发送者，不能单独放在没有 sender 的格式里。sender / target 为 1–32 位账号 token（A–Z、a–z、数字、下划线和点），装饰性或中文显示名应放在 display。非标准账号字符的模板覆盖不在这次静态结果中冒充通过。

例如：

~~~text
[{channel}] {sender}: {message}
[全服] Alice: 你好
~~~

- TemplateParser 使用有界的字面量/字段匹配，能枚举到歧义，不靠懒惰正则取第一个拆分。未知字段、重复字段、缺字段、无匹配和多种拆分都有明确结果。
- 编辑器真实调用解析器。预览当前候选规则，同时与其他已启用规则检查重叠；不会只因单条规则能提取就忽略整组歧义。缓存按不可变规则列表、当前规则和样例失效。
- 服务端下发模板通过已允许接收聊天资源的客户端按连接生效，断开即清除。新通道还校验连接来源与操作 ID，迟到的快照、保存或图片回执不会写进另一份草稿。
- ServerTemplateRecognition 在原生/结构身份与已知系统键之后、弱文本推断之前参与；冲突为终止结果。正文按实际 Component/Text 范围切片，保留富文本和装饰。
- 缺 UUID 不制造身份；UUID/账号与既有明确身份冲突时拒绝认领。新 SERVER_TEMPLATE 证据不会单凭同名吞掉本地回显。发私聊时区分本地发送者与组件明确指出的远端接收者。
- 这些都是人工模板补救，不计入纯客户端自动识别 90%。首次陌生离线跨服消息若没有身份/皮肤资料，仍无法完整恢复实际皮肤，原验收缺口没有被 UI 工作消除。

## 保存、文件与在线同步

新增可探测的 ConfigAdminPayloads 管理通道，三端都保留旧配置/资源通道的原布局。客户端传输 API 独立在 ConfigAdminClientTransport，公共载荷注册不直接引用客户端网络 API。JSON 消息分块、限长、按序重组和过期释放；分块不切断 Unicode 代理对。

所有管理请求在服务端线程复核实际玩家与管理权限。保存同时校验共享配置版本及资源目录指纹，拒绝其他 GUI 管理员保存或资源目录修改造成的过期提交。文件名、重名、校验值、数量、总大小与图片有效性均在服务端复核。

- 新增图片只进入暂存目录；完成整份候选后替换目录，再持久化配置。正常保存失败会恢复旧目录且不发布新配置。事务回滚有真实临时目录用例。
- 有效资源包上限统一为 **16 MiB / 200 文件**，与已有客户端接收能力一致。管理清单可读取旧 64 MiB 范围，让管理员删减；不会把“服务端能保存64MiB”当成客户端能接收。
- 保存后主动更新已允许接收的在线客户端。模板确认带配置版本，资源确认带实际包 hash，避免上一轮回执被当作本轮完成。
- 界面区分配置已保存、等待客户端和同步未完成，提供重试。客户端安装完整包才替换旧内容；取消提前删旧文件，并修复最终目录改名失败时丢失旧缓存的路径。
- 旧客户端没有新管理能力，仍通过原协议进服接收资源；其空闲状态可能不处理主动 manifest，需重新进服。不会将这类情况伪报为已经热更新成功。

本轮没有启动 Minecraft 客户端、专用服务器或代理服，没有真实联机全链路验收。目录与配置的正常异常回滚已测；不把这些结果扩大成断电/强杀下的跨文件崩溃恢复保证。管理页 GIF 缩略图和预览显示首帧，聊天中的原有动画链路未改。

## 验证与产物

| 平台 | 完整回归 | 最后专项 | 失败/错误 |
|---|---:|---:|---:|
| 1.21.1-fabric | 2072，跳过12 | 92，跳过0 | 0 / 0 |
| 1.20.1-forge | 2055，跳过10 | 92，跳过0 | 0 / 0 |
| 1.21.1-neoforge | 2055，跳过10 | 92，跳过0 | 0 / 0 |

完整回归之后，最后的修正集中在服务端页的紧凑布局、字段错误提示、预览冲突、单条编辑的撤销/滚动作用域及文案；最新专项与 build 对应当前行为。其余完整回归按相关条件未变复用。三端的完整 XML 和最新专项 XML 分别归档，不能把专项数量冒充完整测试数量。

完整回归的 12 / 10 / 10 个跳过属于既有条件项：替代 AtomLib JAR 预检、GPU/GL 黄金图与生命周期/成本基准、生产页录制成本、表情渲染基准等。确切方法名见 results.json 和 XML。没有把跳过计为通过。最新专项没有跳过。

验证入口：

- build/v090-revision/results.json：当前统计。
- build/v090-revision/*-broad-tests.zip：完整回归 XML。
- build/v090-revision/*-latest-tests.zip：最后专项 XML。
- build/v090-revision/previews/：三端各 59 张原生 Skija 离屏图，共177张，包括中英文、280/420宽、深浅主题、列表/编辑/资源、字段五态和错误态；已打开代表图检查。这不是 SVG 草图，也不是 Minecraft 实机截图。
- build/v090-revision/artifacts/：最终三端 v0.8.0 JAR，CRC、新类与旧独立屏幕移除检查通过；verify_jars 的 atomlib-consumer 断言通过。
- 158 张跟踪 PNG 已按测试前备份恢复字节，保留既有 CPU/GPU 及其他用户 WIP。没有 reset/switch/clean/stash，没有建立 SHA 门禁、trust、hook 或只读锁。

使用离线 AtomLib 0.4.0 输入 build/atomlib-slim-20261107/atomlib-maven-v040-final。Fabric/Neo JDK21，Forge JDK17；Neo 沿用跳过 downloadAssets，Forge 沿用禁用证书探测的既有离线例外，未改依赖版本。

~~~powershell
# 各自平台目录；Forge 另设 JAVA_TOOL_OPTIONS=-Dnet.minecraftforge.gradle.check.certs=false
./gradlew.bat test build --offline --no-daemon --console=plain '-PatomlibMavenRepo=D:/AtomChat-Main/build/atomlib-slim-20261107/atomlib-maven-v040-final'
# 最后一轮使用以下筛选后接 build；Neo 另加 -x downloadAssets
./gradlew.bat test --tests 'com.atom.chat.config.*' --tests 'com.atom.chat.chat.TemplateParserTest' --tests 'com.atom.chat.chat.ServerTemplateRecognitionTest' --tests 'com.atom.chat.settings.FormTextTest' build --offline --no-daemon --console=plain '-PatomlibMavenRepo=D:/AtomChat-Main/build/atomlib-slim-20261107/atomlib-maven-v040-final'
~~~

中间失败记录保留：16 MiB 限额、UTF-8 分块与跨模板预览及跨条目撤销用例曾先失败后修正；识别测试初次未装内存配置夹具、个别缺 import、Forge 证书参数的 PowerShell 传递等已修正。最后一次格式字段修正后重新跑了相关专项和构建，不沿用修正前截图作通过证据。

其他检查：

- targets、生成接缝清单、JAR profile 与 diff whitespace 检查通过。
- bare：基线441，当前412，没有抬高基线。
- 孪生残差检查仍红：86对，21个文件有残差，33个未归类块、8个陈旧条目。比先前32块多出的管理错误提示调用是 official displayClientMessage / Fabric sendMessage 的映射差异；已限定核对，未批量accept台账，也未声称全仓孪生检查通过。
- 额外日志出口检查仍有31处范围外/既有出口，未将它改成绿色或扩修 CPU/GPU 诊断工作。新管理服务使用现有日志前缀。
- 保持唯一实施代理，未再创建子代理。已做实施自审，主代理集中独审仍待执行，不能写成“独审通过”。

## 部署与回读

最后部署时间：20261117-000201（Asia/Shanghai）。

| 实例 | 最终 JAR SHA256 前12位 | 其他 mods/config 文件保持不变 |
|---|---|---:|
| 1.20.1-main | c8553d0f11de | 1625 |
| 1.21.1-CCB | 86af23d90d91 | 744 |
| Mechanomania-航空学 | c0d8fde3de0c | 817 |

最后一轮替换前备份：D:/AtomChat-Backups/2026-11-16-v090-server-config-revision/20261117-000201。

首次替换前的原候选备份保留在 D:/AtomChat-Backups/2026-11-16-v090-server-config-revision/20261116-231017/。独立进程已核对最终 SHA256、CRC、唯一有效 AtomChat JAR，以及1625/744/817个其他模组与配置文件的路径、大小、mtime和SHA256。AtomLib包含在未变清单中。

回执：build/v090-revision/deployment-{prepared,receipt,readback}.json。

部署采用备份和同盘原子替换，不回退成原地覆盖正在使用的 JAR。航空学当时正在运行，原子替换及回读仍成功；未关闭或重启游戏。当前运行中的进程仍需退出重启后才能加载候选。

AtomLib 没有替换：Forge/Fabric 保留现有版本，航空学保留 0.3.1-shadowcache。对航空学旧库核对了本轮新使用的 Design / UiTokens / SkiaDraw 入口存在，不将这些静态结果冒充真实 GPU/整合包兼容验收。

## 主代理复审入口与实机待验

### 2026-11-17 主代理复审与用户视觉纠偏

主代理已读资源草稿/目录事务、管理分块/关联回执/权限桥、模板解析与接收路由、共享字段和页面绘制命中等关键实现，复核最终测试 XML 汇总及构建日志，并重跑 targets、三端 JAR 断言和 diff whitespace；三个实例当前安装包 SHA/CRC 与最终回执相符。业务测试沿用上文最后批次，不冒称本轮新跑。尚未发现这些已读路径的新功能阻断，但不是全生态或实机验收。

用户随后对照原生 `zh_cn-280-dark-emotes.png` 与认可草图指出观感偏差。直接对照确认：同一套服务端表情资源在草图中是资源根页带命名缩略图、右侧导入与紧凑操作，实际却增加独立管理层级，根页只有摘要/匿名缩略，文件行竖排并套用确认弹窗大胶囊。底层组件复用不能证明页面编排符合已认可草图。主代理撤回视觉通过判断，已退回同一个 Franklin 纠正资源根页/文件行及同类列表操作；蓝色方块夹具改用可辨真实图，补非空根页和窄宽/主题/语言原生证据。修订尚在进行，当前已部署包未因这次反馈改变。

流程偏差也需说明：上一批实际先于主代理独审部署，与 UI_CONTRACT 中的先独审后部署顺序不符。后续修订明确先交主代理复审，放行后再按既有授权部署，不以“已授权部署”替代顺序要求。

本轮触及的78个文件见 build/v090-revision/implementation-files.txt。主要文件：
- shared/config：ServerConfigPage、ServerConfigDraft、ServerConfigValues、AdminProtocol、AdminAsset、ServerResources、ServerConfigLayout/Tokens、AtomChatServerConfig。
- shared/settings/ui：SettingsFormField、FormText、SettingsFieldPaint、SettingsGroupPaint、SettingsScrollbar、ScrollSyncService。
- shared/net/chat/util：ConfigAdminClient/Server、ConfigRevision、PackSyncClient/Server、MessageTemplate、TemplateParser、ServerTemplateStore、IdentityEvidence、DirectorySwap、TextLimits。
- official / Fabric：ConfigScreenClient、SettingsSectionPage、ServerTemplateRecognition 及测试。
- 三端：ConfigAdminPayloads、ConfigAdminClientTransport、ConfigPayloads、ConfigScreenServer、AtomChatClient、实际 AtomChatScreen、ChatHudMixin、本地化。

建议重启测试实例后依次检查：单人 /atomchat gui → 修改一项托管值和个人资料额度 → 常用语新增/排序/编辑并返回 → 选择图片、预览、删除、放弃/保存 → 模板三方向样例与冲突 → 两客户端在线同步/失败重试 → 返回原世界/私聊并确认输入、引用、历史和滚动保留。

未覆盖的实机项包括中文 IME 候选窗/焦点、文件选择窗口层级、真实鼠标拖选、完整动效和按压观感、实际多人权限变更与网络时序、TrChat跨服/Paper/Youer/NCR插件组合，以及航空学旧 AtomLib 的运行组合。原自动90%和缺资料冷启动离线身份/皮肤缺口仍未验收，不扩大本轮结论。
