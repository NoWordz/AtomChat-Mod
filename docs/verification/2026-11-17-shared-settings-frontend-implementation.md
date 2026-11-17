# 服务端配置共用真实设置前端：主审三项修正完成，待复审

2026-11-17。依据[实机否决记录](2026-11-17-server-config-rejection.md)、[本轮方案](../superpowers/plans/2026-11-17-server-config-shared-frontend.md)与[主代理首轮复审](2026-11-17-shared-frontend-main-review.md)整批实施并完成三项修正。生产代码及三包已冻结；用户随后明确要求部署，三端测试实例已安装并独立回读。**未启动、关闭或重启游戏。** 根版本仍0.8.0，离线AtomLib0.4.0编译输入未变。没有新代理、框架、美术、锁/门禁或提交发布。

此候选的实现、三项主审修正与离屏验证完成，**尚未经主代理复审通过**；用户明确部署构成测试实例安装授权，不代表用户实机观感验收通过。12:46旧候选的否决判断保留；旧草图只作信息结构参照。

## 主审三项修正闭环

1. **设置非数字行恢复原高度**：`SettingsSectionPage.compactText` 按本轮 `before-*-SettingsSectionPage.java` 恢复普通SLIDER/COLOR的第二个`SETTINGS_VALUE_BAND`，仅历史数字的紧凑框走新路径。`SettingsResponsiveLayoutTest` 以240/280宽、真实字体`wrap`/字高计算原高度并与`rowHeight`比较；修改前红为159.3对129.3，修后绿。没有用匹配源码字符串代替测量。
2. **server按词折行且测量绘制同源**：`SettingsRowText.measure(..., words)` 对server全部行置`true`，使用已有`SkiaFontRenderer.wrapWords`；设置仅历史数字置`true`，普通设置继续`wrap`。`SettingsRowText.draw`直接消费同一组行。280/420宽、中英、根页/编辑器逐行检查英文词完整；中英`MiB`和三处`1–65536 MiB`在实图中保持完整，`characters`/`message`等不在词内断开。没有缩字、硬换行改文案或改用户可见字号。
3. **方向统一到真实composite**：`ServerConfigPage` 新增方向回调；tab按新旧索引比较，进入/编辑下钻forward，内部返回backward；三端`AtomChatScreen.openServerConfig` 均绑定同一`composite::navigationDirection`。行为用例覆盖真实0→2→0、已定居2→1，以及常用语列表/单编/完成返回和模板编辑/返回；SLIDE检查`exitDx`方向，ZOOM检查composite方向与真实中间帧。新增TAB FORWARD、TAB 2 TO 1及内部forward/backward的PNG序列和GIF。

首轮修正前的真实失败保留在`review-red.log`、`review-red-words-direction.log`：普通滑条高度159.3→129.3，`MiB`拆成`Mi`/`B`，`characters`拆词，0→2→0反转仍forward。三项修后针对测试全绿。

## 五项投诉的闭环与边界

| 用户反馈 | 本次修正和证据 | 未证明的部分 |
|---|---|---|
| 普通输入就卡，历史数字正常 | 普通键不再求值系统剪贴板；仅Ctrl+V读取，读取抛异常时保留内容/选区。稳定文字只在文字/宽度/字体/换行模式改变时重测，布局、文字、光标、选区、命中共用缓存行及advance | 未跑Minecraft实机帧时间；不宣称卡顿全部由剪贴板造成或已完全消失 |
| 数字框不一致、单位在框内 | 设置历史与服务端数字共用紧凑框几何，数字字体/居中/焦点强调色一致，单位在框外；历史数字原键盘处理保留 | 没有为统一数字框改其他设置的产品行为；旧设置其它外观仅做等价提取，仍待主代理复核 |
| 间距、折叠、hover/tab缺离场 | 两者调用当前设置抽出的组几何、50ms折叠、stagger、合并卡片、描述/紧凑文本、chip及hover衰减；三端宿主接入服务端bodyVariant、统一arrival时间戳，固定chip/保存区移出body，并排除固定区的body逆变换 | 软件合成链/组件与像素连续测试已跑，实际窗口、世界背景、raw-GL及GPU路径未验收 |
| 格式文字偏下 | 单行首行中心直接由真实框高/可见行数求得；长文本从1至4行自适应，之后框内滚动。名字允许视觉折行但Enter仍结束编辑，不插入换行 | 中文/emoji字符事件与代理对已测；没有测试操作系统IME候选窗口/组合输入 |
| 旧图与实际候选不对应 | 当前图、动态帧、JAR、XML分开归档并关联当前源码；使用真实HeaderChrome、SettingsDetailPage/SettingsSectionPage、ServerConfigPage和PageTransitionComposite | 全部新图明确为软件离屏；不再称为游戏原生实机图，也不以静态图证明性能/动效 |

## 共同实现的直接调用链

- `SettingsGroupFrontend`：从当前设置提取的行top/height/gap算法、FOLD_TAU_MS=50、合并卡片fold fade/lift/press/clip、局部阴影层和stagger motion。设置的paint与组卡hit使用同一次motion采样；服务端用同一个实现。可见性判断保留，表情包管理不会请求视口外200张图片。
- `SettingsGroupFrontend.stepHover`：两页使用当前设置的逐帧衰减规则，离屏行同样衰减，避免滚回残留高亮。设置不另加hover曲线。
- `SettingsRowText`：当前设置的compact测量、描述基线/折行与数字框几何。设置旧描述/compact绘制调用该实现，服务端普通行同源；数字紧凑化与外置单位是已授权的设置视觉变化。
- `SettingsChipBar`：由真实SettingsSectionPage原chip实现提取，保留其字体、截断、命中范围、选中胶囊、hover、press和TAB_MS时钟；替代服务端原独立近似版本。设置与服务端共同调用。
- `SettingsFormField`：缓存真实行/advance；preferredHeight与paint/hit共用同一份结果；1/2/4/5行、Unicode、内滚、选区和宽度失效均有回归。保留FormText原编辑语义。
- 三端`AtomChatScreen`：`drawArrivingBody -> server.renderBody`；`drawArrivingHeader -> HeaderChrome + server.renderFixedChrome`；`bodyVariant -> server.bodyVariant`；arrival事件将composite时间戳交给server.markEntered。服务端header/chip/footer的输入坐标不再继承移动body的逆变换。
- `PageTransitionComposite`本身未改写。不是只在page内部markEntered；原有冻结离场Picture、双向切换/快速反转和固定header链继续负责出入场。

没有改服务端配置/模板协议、权限、身份依据、资源暂存事务、在线同步或解析语义。表情包、常用语、模板列表/编辑仍共用总草稿；命令唯一入口、根保存、失败保稿、返回原聊天保状态继续保留。

## 修改前后输入与布局探针

同机Fabric/JDK21、相同离线Lib、相同两个测试筛选，在独立Gradle测试JVM里执行。字段宽240高80；32/512/4096 UTF-16单位内容，各预热5帧后采25个稳定帧。不是实际游戏/GPU帧时间，未用此数设FPS门槛。

| 场景 | 修改前 | 最终候选 |
|---|---:|---:|
| 12次普通方向键，剪贴板读取次数 | 12 | 0 |
| 事件耗时，测试provider每次读取模拟2ms延迟 | 37.025ms | 0.013ms |
| 32单位字段稳定绘制中位 | 0.1017ms | 0.0835ms |
| 512单位字段稳定绘制中位 | 0.2032ms | 0.0764ms |
| 4096单位字段稳定绘制中位 | 0.4388ms | 0.0826ms |

事件延迟是显式模拟，不是系统剪贴板实际耗时。修改前用例失败于普通按键读了3次剪贴板，未继续跑到失败粘贴分支；异常外抛风险来自源码，修后另以抛异常provider验证不丢草稿。此前进度将其概括为修改前已复现外抛不够准确，此处更正。

`input-before.log/xml`、`input-after-final.log/xml`、`probe-results.json`保留原始数据。最终after隔离探针在三端产物收集后运行，没有更改源码或覆盖归档的完整/相关测试XML。JVM和字形缓存/调度仍可能影响微基准；不能由这些数据量化整机掉帧改善。

## 行为、构建与检查

| 批次 | Fabric | Forge | NeoForge |
|---|---:|---:|---:|
| 本轮完整test build，交互收尾前 | 2090，跳12 | 2073，跳10 | 2073，跳10 |
| **三项修正后最终完整test build** | **2097，跳12** | **2080，跳10** | **2080，跳10** |
| 补帧前受影响四类窄筛选 | 31，跳0 | — | — |
| 测试Bootstrap修正后SharedSettings窄筛选 | 完整build覆盖 | 6，跳0 | 完整build覆盖 |

最终三端完整build均0失败/0错误。受影响类为`SharedSettingsFrontendTest`6、`SettingsResponsiveLayoutTest`5、`PageTransitionCompositeWiringSourceTest`5、`ServerConfigPageTest`15。Fabric在补动态帧前曾筛选这四类31/31通过；Forge在测试Bootstrap夹具修正后单独筛选`SharedSettingsFrontendTest`6/6通过。NeoForge没有另跑窄筛选，最终三端均以完整build覆盖，不能把窄筛选数冒充2097/2080/2080。

补充动态帧后，Forge一次完整build曾出现`2075`执行、`27`失败，根因是`SharedSettingsFrontendTest`先点击真实按钮并初始化`FeedbackSoundController`，而单测JVM尚未Bootstrap，随后`Registries`类初始化异常连锁污染；首次只筛该类的Forge运行也以同样原因失败1项。测试`@BeforeAll`现按平台反射执行`SharedConstants`和Minecraft Bootstrap，仅修测试夹具；Forge窄筛选6/6通过，最终完整build恢复0失败。产品代码没有为此改动。

Fabric跳过12项：原有录制成本1项、GPU/GL/基准/历史像素/寿命/表情性能等11项。Forge/Neo各跳过10项；具体类名见`*-review-tests.zip`和`results.json`，均未算通过。Fabric额外两项是`MessageGpuBaselineTest.fixedChatThroughAtomLibBridge()`与`GpuImageBridgeLifetimeTest.ownedImageSurvivesBridgeResetInvalidateAndFramebufferRebuild()`。没有把skip计作成功。

重要回归：

- 普通键不读剪贴板、失败粘贴保稿；1至4行增高/第5行同高；单行文字与光标中心；稳定帧不重测、内容/宽度改变才重测；中文/emoji、逻辑/软换行、内滚、指针与光标一致。
- 真实server body经过实际composite：tab首帧body像素与离场图一致；快速二次切换保持连续；点击当前tab不重播；过渡中固定保存仍命中。三端源码接线断言仅作补充，未替代这些行为测试。
- 正常设置与服务端的折叠进度相同；窗口外row hover衰减；可见缩略图请求。媒体用例先失败为200次请求，修正后通过。
- 既有统一草稿/连续保存/模板冲突/资源事务/配置与网络相关回归继续通过。

修正的中间失败记录：普通键读取断言、媒体视口断言；另有测试代码Path导入歧义、未装Platform夹具，以及内滚用例未显式设置初始光标的夹具错误。未删用例或放宽既有像素阈值来掩盖问题。

最终`targets-review-final.log`、`seams-review-final.log`（甲156/乙92）、`bare-review-final.log`（441基线/405实测）、`diffcheck-review-final.log`通过；三端build内`bareValueCheck`、`verifyAtomLibInput`也通过。`verify_jars.py`对三份最终JAR显式路径验证ZIP/嵌套依赖/AtomLib锁/FlatLaf重定位全过；按目录扫描会因三端`build/atomlib-consumer/libs`还留有历史0.8.1包而不唯一，该结果保留为歧义，未清理用户旧产物。没有抬基线、批量accept孪生、清理无关WIP或建立保护锁。158张跟踪PNG按本轮测试前字节保留/恢复；最终跑测写回24张后逐字节恢复，旧测试写回的图没有作为新基线提交。

Forge JDK17；Fabric/Neo JDK21。均`--offline --no-daemon --console=plain -PatomlibMavenRepo=D:/AtomChat-Main/build/atomlib-slim-20261107/atomlib-maven-v040-final`；Forge沿用证书探测例外。Neo最终命令未额外写`-x downloadAssets`，日志显示该任务`UP-TO-DATE`；不升级Lib或根版本。

## 图与候选的对应

正本证据根：`build/v090-shared-frontend/`。

- `results.json`、`*-review-tests.zip`、`*-review-final.log`：三项修正后的最终完整build，2097/2080/2080。`*-review-full-tests.zip`保留补动态帧前的中间完整XML；`full-results.json`、`*-full-tests.zip`是更早批次。不能混用这些计数。
- `artifacts/`：本轮三包；根版0.8.0。SHA256 Fabric `274f5f33faf65aaab3efe2cec438588eeb1f8fda117b8e3dfe70d951f68beab4`，Forge `9cc911125ce126f29054dae9b97fdd201c85afba586f37e3375b9046e77b8a25`，NeoForge `a34e79f9c760c5071ba520d2affff1da688c023b608e8054cc40e650a3dcdb6f`。
- `previews/<platform>/pairs/`：同一代码候选、相同280/420宽×820高、locale、主题、动画、背景条件下的设置历史/服务端托管并排图，以及数字焦点并排。每对都有`.conditions.txt`。
- `previews/<platform>/pages/`：资源/模板根页、表情包管理、常用语列表与单编、模板顶部/字段/真实样例预览。全部经过production组件和composite，不是手画header。
- `previews/<platform>/input/`：中文/emoji、1/2/3/4/5行和选区；第5行不再增高，焦点内滚至最后4行。
- `previews/<platform>/motion/`：原有SLIDE/ZOOM反转向右离场各45帧、折叠30帧；另新增`tab-forward-<style>`（0→2）、`tab-reverse-<style>`（2→1）、`internal-<style>/forward`与`internal-<style>/backward`（模板编辑与内部返回）各序列。`slide.gif`、`zoom.gif`、`fold.gif`、`tab-forward-*.gif`、`tab-reverse-*.gif`、`internal-*-forward/backward.gif`均由最终PNG导出，明确`OFFSCREEN / NOT IN-GAME`，0.5倍审阅速度。GIF会合并相同帧、量化颜色，原PNG是逐像素证据。
- 每端548张PNG是最终证据库存，来自同一候选；不作为统一前端验收分数。并排图/动态集成均使用相同配置，两者动画开启；背景是固定渐变输入，blur关闭，使用真实PanelBackground透明度规则、阴影/边框、HeaderChrome和composite。没有执行世界绘制或raw-GL模糊，不能当作用户存档配置的实机背景复现。
- 集成夹具通过测试Host注入生产组件/composite端口，没有实例化运行实际Minecraft窗口中的AtomChatScreen；三端实际宿主绑定另由接线断言、编译及产物检查覆盖。实际窗口输入路由仍需实机验证。
- `changed-files.txt`列本轮代码/测试/文档范围；`before-*-SettingsSectionPage.java`仅供本轮提取来源复核，不是trust副本/保护清单。

## 冻结交接与未验收

### 测试实例部署与独立回读

部署批次 `20261117-160428`，用户明确“部署”后执行。Forge `1.20.1-main`、Fabric `1.21.1-CCB`、NeoForge `Mechanomania-航空学` 均先核对旧 SHA 与上一批记录一致、确认只有一个有效 AtomChat，再备份旧包和 `config/atomchat` 下 2 个配置文件，随后同盘原子替换。备份根目录 `D:/AtomChat-Backups/2026-11-17-v090-shared-frontend/20261117-160428/`。

| 实例 | 安装 SHA256 | 旧包 SHA256 | 其他 mods/config 保持不变 |
|---|---|---|---:|
| 1.20.1-main | `9cc911125ce126f29054dae9b97fdd201c85afba586f37e3375b9046e77b8a25` | `519aff0a925cde63154ea1f7234bc0cca63f95e2c1a45b154595472ed5e1d3b4` | 1625 |
| 1.21.1-CCB | `274f5f33faf65aaab3efe2cec438588eeb1f8fda117b8e3dfe70d951f68beab4` | `80b855080c75ac92e6274c4778a1541a80e67283ef5e66d6a67f34f065fe1eca` | 744 |
| Mechanomania-航空学 | `a34e79f9c760c5071ba520d2affff1da688c023b608e8054cc40e650a3dcdb6f` | `bc0735b6183c404a9c65adedbbd021eb119b498428b6b07bcae3ee7b196bc20d` | 817 |

随后用另一个Python进程重新读取安装包、CRC、唯一AtomChat、备份JAR、配置备份及全部受保护文件清单，均与部署前一致。安装包内版本0.8.0、`git_commit=720d20d`、`git_dirty=true`，包含`ServerConfigPage`与`SettingsRowText`新类。`deployment-{prepared,receipt,readback}.json`及`deployment-independent-readback.json`在证据根；没有启动、关闭或重启游戏。

现在代码与产物保持冻结，等待主代理复审与用户实机验收。没有提交、推送或发布。

实机严重卡顿是否完全消失、操作系统IME、实际窗口焦点、世界背景/raw-GL/GPU、多人网络时序、Neo旧Lib组合未验证。原孪生/日志等WIP遗留未扩修，也不声称全仓所有检查绿；用户尚未确认本轮前端观感。90%自动聊天兼容及缺资料首次陌生离线跨服身份/皮肤问题仍未满足，不算作本轮UI完成。

## 追加修正：服务端点击音效（2026-11-17）

用户指出服务端页点击音效同属前端。核对原实现后，明确的缺口是 tab 切换、内部返回/“完成”、开关提交三处；`SettingsListAction` 驱动的消息类型、插入字段、编辑/删除/排序、添加和根页有效保存原本已有 `BUTTON` cue。

- `ServerConfigPage` 只在 tab 真正变化时发声；`navigateBack()` 覆盖子页“完成”、头栏返回和 Esc 返回；`toggleRow()` 与模板启用开关各发一次 cue。文本/数字输入聚焦仍静音。
- 三端 `FeedbackSoundController` 增加仅测试安装的 cue 观察器，位置在音量策略之后、Minecraft客户端判空之前；生产不安装观察器，播放路径不变。
- `SharedSettingsFrontendTest` 新增真实点击测试：切换tab、重复点当前tab不计、内部“完成”、托管总开关、模板启用开关，均同时断言 cue 次数和状态变化。
- 复核 `ClickRipples.region()` 后确认它已对传入 surface 取 `UiTokens.onAccent(surface)`，因此消息类型等次要胶囊的波纹本来就是对比色派生，无需改色；本轮未改 press/ripple 逆变换。

受影响类三端均为7/7通过。追加后的完整 build：Fabric 2098（skip12）、Forge 2081（skip10）、NeoForge 2081（skip10），失败/错误均0；`bareValueCheck` 仍为基线441/实测405，`verifyAtomLibInput`通过。三份新JAR用 `verify_jars.py --profile atomlib-consumer` 显式路径验证通过。

追加部署批次 `20261117-163726`；备份根 `D:/AtomChat-Backups/2026-11-17-v090-shared-frontend/20261117-163726/`。

| 实例 | 安装 SHA256 | 被替换的上一包 SHA256 | 其他 mods/config 保持不变 |
|---|---|---|---:|
| 1.20.1-main | `63675827e781388c397de55b5102254874363a33ffa50620fcb6b32227e88ce8` | `9cc911125ce126f29054dae9b97fdd201c85afba586f37e3375b9046e77b8a25` | 1625 |
| 1.21.1-CCB | `a0d035a08d7d72a122a60074f60739f0cfe03f668e561c8732e7071ceea8f8b5` | `274f5f33faf65aaab3efe2cec438588eeb1f8fda117b8e3dfe70d951f68beab4` | 744 |
| Mechanomania-航空学 | `57ce89258d8df9a392a9bed39490d9b41d7f30049cc559a7f67bef9c87292ca8` | `a34e79f9c760c5071ba520d2affff1da688c023b608e8054cc40e650a3dcdb6f` | 817 |

部署前旧包及各自2个AtomChat配置已备份；部署后由同一脚本和另一个Python进程分别回读唯一AtomChat、CRC、SHA、备份及受保护文件清单，均通过。测试运行写回的24张跟踪PNG已按`pre-test-tracked-pngs.zip`恢复，158张均与测试前WIP字节一致。没有启动、关闭或重启游戏；实际音频、音量、游戏内声场与OS音频设备仍未试听验证。追加修正尚未经主代理复审。
