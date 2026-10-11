# 趣味特效、音效反馈与日志规范实施方案

> 执行方式：主代理设计并产出完整代码；gpt-6-luna / max 子代理团队按明确文件清单落盘、测试和构建；独立 code-reviewer 复审。禁止子代理自行提交、推送、清理临时文件。

**目标：** 保持 0.3.0-Preview，增加聊天关键词趣味特效和操作反馈音效，统一所有 AtomChat 自有日志前缀。保留现有通知音效与智能去重。

**架构：** 关键词匹配、特效生命周期和声音决策放共享层；绘制复用现有 Skija 与 emoji 字体路径；Minecraft 注册、播放和消息入口留在对应平台或映射层。一个职责一个类、一个类一个 Java 文件，Screen 仅接线。

**技术栈：** 现有 Java、Skija、JUnit 5、Gradle；Fabric 1.21.1、NeoForge 1.21.1、Forge 1.20.1；不引入新 UI 框架。

## 约束与已定产品规则

- 仓库唯一源码位置：`D:/AtomChat-Main`，单仓库三平台，现役 main。版本来自 `gradle.properties:17`，保持 `0.3.0-Preview`。
- 本方案阶段不改生产代码。用户说“收工”前，不清临时文件、不提交、不更新项目状态、不推送、不合并。
- 实验性功能只有一个“实验性：趣味特效”开关，默认开启；放在“聊天与通知 → 高级”，与 TP/TPA 选项同一级；没有单项开关、预览按钮、自定义词库。所有设置折叠组默认展开，保留用户手动折叠。
- 只在聊天面板已打开时，由新收发的普通聊天消息触发。历史加载、滚动、旧引用正文、系统消息、命令均不触发。
- 特效只画在面板内部，不接收输入；关闭面板立即清除。每轮约 3 秒；按消息文字位置选择首个匹配；播放期间忽略新触发，不排队、不叠加。
- 三类无声 emoji 特效：❤️ 爱心雨、🎂 蛋糕雨、🎆 烟花。爱心/蛋糕从面板上部下落；烟花在面板内短暂绽放，粒子量有上限。
- 通知音效和反馈音效各有独立总开关及音量。通知保留现有类型开关；反馈有按钮、发送、打开面板三项开关，默认均开启。
- 反馈使用原创轻柔电子短音，三类可辨。只响应实际生效的 AtomChat 操作；鼠标和键盘路径一致；一次操作只播最具体的一个声音。
- 发送按钮/回车仅播发送音；打开聊天面板仅播打开音。内部页面切换与弹层开关按按钮反馈处理，禁止在 render 或重复 init 中播放。
- 通知不受聊天面板打开/关闭影响；测试通知与真实通知共用设置、类型规则及去重门。
- 既有通知两秒最小间隔保留，逐项听声测试间隔必须超过两秒。测试指令不是绕过静音设置的强制试听入口。
- 所有 AtomChat 自有 INFO/WARN/ERROR/DEBUG 输出统一为 `[AtomChat][链路] 消息`，保留级别、异常参数和原有调试门控。不修改 Minecraft、第三方或框架生成的日志；不新加追踪 ID 系统。

## 已核实的接入点

以下位置是当前源码证据，不代表运行期验证通过。

| 接入点 | 当前证据与含义 |
|---|---|
| 共享源集 | `gradle/atomchat-layers.gradle:136`、`:154`，共享 main/test 会接入三端 |
| 配置 | `shared/src/main/java/com/atom/chat/config/AtomChatConfig.java:280`，现有通知类型开关及 `notifyVolume` |
| 设置分区 | `shared/src/main/java/com/atom/chat/settings/SettingsSection.java:8`，保留现有四类；音效归入聊天的通知页，实验性开关归入高级页 |
| 设置目录 | `platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsCatalog.java:90`、`:100`、`:213`，通知开关/音量已有入口；官方映射目录有对应实现 |
| 设置首页 | `platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsHomePage.java:34`、`:172`，枚举驱动数量，但图标和标题 switch 需同步 |
| 消息捕获 | Fabric `ChatHudMixin.java:354`、`:624`，新公屏/私聊与反刷屏合并可区分 |
| 历史插入 | `shared/src/main/java/com/atom/chat/chat/ChatStore.java:49`，`addLoaded` 独立于新消息入口，不能在遍历消息列表时触发特效 |
| 发送 | Fabric `SendPipeline.java:87`、`:127`、`:177`，集中发送与私聊本地入库；官方映射有对应实现 |
| Screen 生命周期 | Fabric `AtomChatScreen.java:2003`、`:2158`，init 包含 resize，打开音不能跟着 resize 重播 |
| emoji 绘制 | `shared/src/main/java/com/atom/chat/render/SkiaFontRenderer.java`，复用现有字体能力，实施时先做三符号离屏和游戏内探针 |

## 通知音效：问题撤回与现有行为保护

用户于本轮重新测试 Fabric 后明确撤回无声报告：@、引用、私聊均有声音，连续触发时的智能去重符合预期。取消“通知无声修复”，不得为已正常的测试路径添加重复播放调用。其他两端静态审计已完成，但未由用户运行验证。

三端测试客户端均把通知交给 `onTestBanner`，不存在已确认的“测试入口完全跳过声音”缺陷。

- Fabric `TestCompanionClient.java:58` → `NotificationController.java:101` → `fire` → `playSound`（`:148`）。
- Forge / NeoForge `TestCompanionClient.java:51` → `NotificationController.java:108` → `fire` → `playSound`（`:155`）。
- 类型开关在 Fabric 及 `layers/mapping/official/.../NotificationBanner.java:677`；横幅开关和声音开关独立，因此可合法出现“横幅可见，声音关闭”。
- `shared/.../NotificationSoundGate.java:12`、`:24` 定义两秒去重及首声放行；当前已有 NEVER 防溢出逻辑，不能把旧缺陷当新根因。
- 三端 `sounds.json` 都声明 `atomchat:notification`，三份 notification.ogg 字节哈希一致。Fabric 通知音用户已听声验证，另外两端的实际输出未验证。

用户提供的运行证据：18:44:16 WHISPER、18:44:18 QUOTE、18:44:21 MENTION 均记录 `soundOn=true player=true world=true`、`allowed=true`、`volume=1.0`；用户确认听到三种提示。此处是测试通知验收，不替代真实多人 @/引用/私聊验收。

实现后的保护性回归，逐个执行下列指令，每次间隔超过两秒，再连续触发验证限流：

```text
/atomchat test banner mention
/atomchat test banner quote
/atomchat test banner whisper
```

比对新的 `[AtomChat][notify] fire` 和 `playSound` 日志，确认加总开关与整理设置未改变原来的智能去重。Forge/NeoForge执行相同矩阵；若运行环境不可用，明确交付范围为静态/自动验证，不宣称已完成另外两端听声验收。

## 文件职责与拟定自写接口

下表为新增代码的设计接口，尚未声称接口已经存在。外部 Minecraft/Skija 接口实施前需三行探针确认真实签名、字段和返回结构，探针结果记入实施证据。

| 新文件 | 唯一职责 / 接口 |
|---|---|
| `shared/.../fun/ChatEffectType.java` | 枚举 HEART、CAKE、FIREWORK |
| `shared/.../fun/ChatEffectMatcher.java` | `Optional<ChatEffectType> match(String body)`：只识别正文并选择最早匹配 |
| `shared/.../fun/ChatEffectSession.java` | `boolean trigger(ChatEffectType type, long nowMs)`、`void clear()`、`boolean active(long nowMs)`：三秒时钟、忙时丢弃与关闭清理 |
| `shared/.../fun/ChatEffectParticles.java` | 按随机种子、类型及时间生成有限粒子位置；不触及 Minecraft、配置或输入 |
| `shared/.../fun/ChatEffectRenderer.java` | 在调用方指定的面板矩形内 clip，绘制 emoji 并成对恢复画布状态 |
| `shared/.../audio/FeedbackCue.java` | 枚举 BUTTON、SEND、PANEL_OPEN |
| `shared/.../audio/FeedbackSoundPolicy.java` | 纯函数决定声音开关、音量和操作优先级；不播放、不读世界 |
| 三平台 `.../audio/FeedbackSoundController.java` | 只注册反馈音资源并将已决定的 cue 交给平台播放器 |
| `shared/.../diagnostics/AtomChatLog.java` | 集中构造 `[AtomChat][链路]` 前缀并调用现有日志器；保持占位符参数与异常堆栈，不参与业务状态 |

纯粒子逻辑与渲染同一份共享实现；禁止为三端复制 matcher/session。Screen 不承载规则表、粒子循环或声音素材制作逻辑。

修改清单：共享 `AtomChatConfig`、`SettingsSection`；Fabric 与官方映射的 `SettingsCatalog`、`SettingsHomePage`；三平台 Screen、消息捕获入口、声音注册入口及 NotificationController；Fabric 与官方映射 SendPipeline；对应中英文 lang 和 sounds.json；反馈音 ogg；现有产物验证脚本按音频资产增补断言。

## 关键词契约

| 特效 | 中文 | 英文，大小写不敏感 |
|---|---|---|
| 爱心 | 我喜欢你、我爱你 | I like you、I like u、I love you、I love u、love you、love u |
| 蛋糕 | 生日快乐 | happy birthday、happy bday、HBD |
| 烟花 | 新年快乐 | happy new year、HNY |

- 英文完整单词边界；词间允许连续空白；不从网址、代码片段和更长的单词中截取缩写。不做机器翻译、模糊匹配或情绪理解。
- 中文允许祝福前缀，例如“祝你生日快乐！”；关键词后紧接其他汉字不匹配，例如“我爱你们”。这是一条明确的文字规则，不做中文语义分词。
- 正文中的当前文字可触发；结构化引用的旧正文不参与匹配。名字、时间戳及展示装饰不参与。
- 表情、图片、命令发送路径不会自动变成普通文字触发。
- 自发消息以成功进入本地发送管线为事件；服务器回显不能再次触发同一条特效。去重依据发送管线与捕获已有身份/抑制机制接线，不靠比较文本来吞掉别人相同的消息。
- 私聊新正文同样纳入；历史加载、反刷屏折叠、重复回显不可重新触发。

## 分阶段实施与测试

### 1. 通知行为基线与声音设置

- [x] 三端测试通知路径静态审计，确认已共用通知播放入口。
- [x] 用户确认 Fabric 三类测试通知有声，智能去重正常；撤回无声 bug。
- [ ] 保存有效行为作为回归参照；另外两端后续运行测试仍单独记录。
- [ ] 新增通知总开关，沿用 notifyVolume 和现有类型开关；关闭总开关不修改子开关。测试/真实通知共用最终声音决策。

### 2. 趣味特效纯逻辑

- [ ] 主代理给出 matcher/session/particles 的完整代码与对应测试，子代理落盘并先跑红灯，再跑实现后的绿灯。
- [ ] matcher 测试覆盖全部中英文白名单、大小写、多空白、我爱你们、祝你生日快乐、URL/缩写边界、多关键词最早位置、引用正文排除。
- [ ] session 测试用可控时钟验证 2999ms/3000ms 边界、忙时忽略、clear 后可再触发、关闭/重新打开没有旧特效。
- [ ] particles 测试验证数量上限、有限数值和固定种子可重复，不把审美参数写成“视觉合格”结论。

拟定匹配器测试示例，基于以上自写接口：

```java
@Test void matchesAliasesAndRejectsLongerWords() {
    var matcher = new ChatEffectMatcher();
    assertEquals(Optional.of(ChatEffectType.HEART), matcher.match("I LOVE U!"));
    assertEquals(Optional.of(ChatEffectType.CAKE), matcher.match("祝你生日快乐！"));
    assertEquals(Optional.of(ChatEffectType.FIREWORK), matcher.match("hny!"));
    assertTrue(matcher.match("我爱你们").isEmpty());
    assertTrue(matcher.match("https://example.com/hny").isEmpty());
    assertTrue(matcher.match("hnything").isEmpty());
}
```

### 3. 绘制、事件与设置接线

- [ ] 先验证既有 emoji 渲染 API，再实现 renderer；所有 save/clip/transform 必须 restore，不新建常驻 HUD 渲染上下文。
- [ ] 接新收发事件，不在逐帧消息扫描、历史 addLoaded 或列表滚动触发；Screen removed 清理；调整窗口大小不重开特效。
- [ ] 每帧按当前面板几何裁剪，避免密度/GUI scale 双重缩放；切到设置等页面不遮挡设置操作。
- [ ] 原传送标签页改为“高级”；放置 TP/TPA 和一个带实验性前缀的总开关；中英文标题/描述同步；开关关闭立即清除当前特效；不新增设置首页卡片。
- [ ] 用离屏 smoke 检查三符号不缺字及画布状态恢复；真实游戏内验收运动、遮挡和清理。

### 4. 反馈声音与声音设置

- [ ] 主代理制作可复现的原创短音与完整播放代码，子代理落盘并验证三份 ogg 的可解码性、时长、非静音、hash 一致。
- [ ] 新增反馈总开关、反馈音量、BUTTON/SEND/PANEL_OPEN 三个子开关；在现有“聊天与通知 → 通知”页放默认展开、可手动折叠的通知音效、反馈音效两组设置。

验收修订（2026-10-11）：反馈音采用 DuffyBro 的 CC0 气泡 Pop 公共 HQ 预览作为统一母音；在 `tools/audio-source/` 保留单声道素材、原始公开 URL、许可和 SHA-256。通过 `tools/generate_feedback_sounds.py` 离线提取单次气泡，柔化高频，再以音调、时长、滤波及音量派生三种反馈，不再使用前一版电子合成音。代码审查、三端测试与构建及产物校验通过后覆盖实例；不再启动客户端。
- [ ] 旧配置缺新增字段时使用默认值；原有通知静音选择与 notifyVolume 不被重置；音量输入钳制到 0..1，拒绝非有限值。
- [ ] 成功动作出口发声；空消息、被阻止的操作不播成功音；本地提交到网络发送管线视为发送成功，不能宣称已获服务端送达确认。
- [ ] 发送音替代按钮音，打开音替代按钮音；把去重写在动作编排处，不让全局时间限流吞掉不同独立操作。
- [ ] 改 init 不得导致 resize 重响；回车/鼠标发送、公屏/私聊都经过同一发送反馈出口。
- [ ] 声音 policy 单测覆盖总开关、子开关、音量零与上限、动作失败、优先级和一次操作一次发声；平台 smoke 覆盖注册与资源重载。

### 5. 统一自有日志

- [ ] 盘点 shared、layers、三平台中 LOGGER/logger 调用、标准输出、直接异常打印与多行汇总；不扫描编译产物、第三方 vendor 或依赖源码作为改写目标。
- [ ] 主代理产出统一前缀工具及逐文件替换代码；子代理按互斥文件清单落盘，避免正则全仓替换破坏格式化参数或重复前缀。
- [ ] 稳定链路标签按职责使用：`notify`、`feedback`、`fun`、`test`、`capture`、`render`、`resource`、`config`、`network`、`history`、`startup` 等；同一链路在三端同名。
- [ ] 例如旧 `[notify] playSound ...` 变为 `[AtomChat][notify] playSound ...`，旧 `[test] ...` 变为 `[AtomChat][test] ...`；不能变为 `[AtomChat][notify] [notify] ...`。
- [ ] 多行性能汇总每个内容行带同一前缀；异常保留 logger 的 Throwable 参数，不能将堆栈压成字符串，堆栈本身保持日志器原样格式。
- [ ] 新旧 debug 输出继续受现有 debug 开关控制；不把所有调试消息升级为常驻 INFO。不记录聊天正文、凭据等新的敏感内容。
- [ ] 增加有正反样例的日志覆盖守卫：识别未迁移自有出口、动态模板、缺链路和重复前缀；含命中数量下限。用缺前缀调用与重复前缀做变异确认红灯，不能只查工具本身而漏掉调用点。
- [ ] 运行 smoke 覆盖启动、资源加载、通知测试、消息捕获、操作反馈和特效触发，核对真实产物日志格式。

### 6. 验证金字塔和团队交付

依次进行，不跳级。构建和长测试输出落证据日志，主会话只接收结论、失败定位和日志路径。

1. **unit**：新增 matcher/session/particles/policy、日志格式与覆盖守卫测试；保留通知去重回归；以旧行为或变异确认关键断言确实会红。
2. **suite**：三平台完整 test，并跑现有 bare、目标挂载和孪生差异守卫。每端各自计数，不能用共享源码推断另外两端已通过。
3. **dev smoke**：已进行一次开发客户端初始化与音频/资源加载检查。用户最终明确后续不再启动客户端；后续改动完成代码审查、测试和构建、产物校验后直接部署。
4. **artifact smoke**：三端 assemble；检查 jar 版本仍 0.3.0-Preview、音频登记/资源齐全、无缺失嵌套依赖；以构建出的 jar 复验，不拿开发类路径代替产物。
5. **acceptance**：用户真机听声和观察效果；记录实际验收平台、实例、产物 hash。另两端未运行则如实标记，不宣称三端运行期全通过。

拟执行的现有 Gradle 入口，在各平台目录使用：

```bash
./gradlew --no-daemon test --tests 'com.atom.chat.fun.*' --tests 'com.atom.chat.audio.*'
./gradlew --no-daemon test
./gradlew --no-daemon runClient
./gradlew --no-daemon assemble
```

客户端任务先执行 `./gradlew --no-daemon tasks --all` 探针确认真实入口；Java/Gradle版本、离线依赖与启动配置按目标分别确认，不凭宿主环境猜测。所有命令保留退出码；不在流水线里清理用户临时文件。

产物检查复用 `python tools/verify_jars.py platforms/1.21.1-fabric platforms/1.21.1-neoforge platforms/1.20.1-forge`；具体 Python 路径先探针确认。若旧产物导致歧义，明确选择并校验本轮目标文件，不擅自删旧文件，也不绕过验证。

子代理分工：纯逻辑与单测、平台接线与声音资源、串行最终测试/构建。三名均 gpt-6-luna / max；文件所有权互斥，主代理产出代码后才派落盘任务。独立 code-reviewer 再检查生命周期、事件去重、画布状态、配置兼容和产物；阻断问题修复后重跑受影响门。

## 真机验收清单

- 新旧配置都能打开聊天的通知/高级设置；两类声音独立静音/调音量；反馈三个子开关生效；设置首页保持四张卡片。
- 面板打开时，自己/别人发中英文关键词各触发一次；关闭面板、历史滚动、命令和引用旧文不触发。
- 每轮约三秒，播放期间新词被忽略；重开面板不续播；关总开关立即停止。
- emoji 正常显示；特效位于面板内部，不遮掉输入交互；不同 GUI scale、窗口尺寸下没有出界或双重缩放。
- 点发送与回车各只播发送声；空消息不响；打开面板只响一次，resize 不响；普通按钮与弹层操作有轻点反馈。
- mention/quote/whisper 测试指令面板开/关均按相同规则发声；类型关闭、通知总开关关闭、音量零及两秒限流都正确。
- 所有采样到的 AtomChat 自有日志有 `[AtomChat][链路]` 前缀；多行汇总可归属、错误堆栈保留、调试关闭后没有原本受控的调试输出。
- 开通知不等于已验收真实多人触发：补真实 @、引用、私聊检查，分别记录结果。

## 收工后的文档收口

用户说“收工”后才执行：清理本轮临时产物；提交英文无 emoji 信息；更新状态、记忆索引与证据引用；推送并完成 main 收口。版本/tag/Release 附件不擅自改动。

文档优化纳入此时收尾：`docs/STATUS.md` 仅留当前版本、仓库/分支、有效待办、验证范围与证据链接；外部项目记忆开头修为 monorepo 三端，2342 行旧轮次移到历史归档，保留索引链接，不删除历史证据。不在本方案阶段改项目状态正本。

当前限制：主代理本轮仅完成需求确认、静态审计和方案，未运行构建或自动测试。Fabric 三类测试通知与智能去重由用户实测确认正常；其他两端未运行验收。新增特效、反馈声音和日志规范尚未实现。
