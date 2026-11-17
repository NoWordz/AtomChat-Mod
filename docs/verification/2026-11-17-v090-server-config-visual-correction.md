# 服务端资源与列表视觉纠偏，待主代理复审

> 后续常用语/模板逐页审查确认此批仍有偏稿和编辑器缺图；最新候选见 [逐页纠偏记录](2026-11-17-v090-list-editor-review.md)。最新门槛为主代理审查后先给用户看原生图，用户确认后才部署，不能按本页旧门槛自动部署。此处三实例旧包一致是本页当时只读结果，不代表后续实例状态。

2026-11-17。上一批 00:02 测试部署继续保留；本轮候选**未部署**，不启动游戏。主代理撤回上一批“视觉通过”的判断，本记录不能被解读为重新获得用户视觉验收。

## 依据与范围

直接对照已认可 `docs/evidence/2026-11-16-v090-server-config-review/sketch/02-resources.png`、`03-phrases.png`、`06-template-list.png` 和上一候选 `build/v090-revision/previews/1.21.1-fabric/details/zh_cn-280-dark-emotes.png`。

具体偏差是资源根页只剩摘要，需要再进管理页才能导入/删除；文件行标题在图上方，确认窗大胶囊挤压正文；常用语和模板列表也沿用了这种胶囊；非主操作的 danger 被确认窗配色逻辑忽略。此次只改页面和紧凑操作绘制、六份中英文文案及对应测试。协议、权限、资源事务、在线同步、模板解析及身份契约均未改。

- 资源根页“下发内容”恢复表情包卡头右侧导入、最多三个**有名称和状态**的缩略文件行、直接预览/移除。保留“管理全部表情包”入口，不以该入口替代根页操作；常用语是单独入口卡片。
- 文件行图在左、信息与动作在右。名称按实际字体宽度最多显示两行，体积/发布状态按语义换行；预览/移除以实测文字宽度决定并排或上下排列。体积不足 1 KiB 显示 `<1 KiB`，其余保留一位小数。英文单文件显示 `1 file`。
- 常用语整行进编辑，三个小图标负责上移/下移/移除；边界箭头禁用并消费自己的区域，不会误进编辑。模板使用紧凑编辑图标、开关、移除图标；窄面板将控制带放在文字下方。所有动作只改总草稿。
- 页面内容操作不再使用 ConfirmationSurface.Button；根保存/完成/返回底栏和破坏性确认仍用原组件。没有修改 ConfirmationSurface.actionColor 或全局确认窗。

## 原语来源

`shared/src/main/java/com/atom/chat/settings/SettingsListAction.java` 是此次缺少的紧凑行操作绘制原语，没有引入 UI 框架或修改 AtomLib：

- 采用真实 SettingsSectionPage.drawBlocked 的 `FONT_QUOTE`、`s(28)` 高、文字宽度加 `s(18)`、`radius(8)`。
- 表面、hover、文字、危险色来自现有 UiTokens/主题；图标复用 AppIcons 的 edit/close/back 路径，上下箭头由已有 back 路径旋转得到，不新增 SVG 皮肤。
- 复用 PressScale、UiMotion、反馈音和 ClickRipples。页面将 ripple 区域映射到实际卡片变换并与内容视口相交。静态截图不证明游戏里的动态手感。
- 合并卡片、点阵图标分组、折叠、入场和滚动条保留原共享路径；只是让常用语入口卡片不重复画一个空标题栏。

## 验证与产物

本轮相关三端 `test ... build`：Fabric **132**，Forge **127**，NeoForge **127**，全部 0 失败、0 错误、0 跳过。不是本轮重新跑全仓 2000 多项；上一批全量结果仍保存在原目录，仅适用于未变范围。

筛选为 `com.atom.chat.config.*`、TemplateParserTest、ServerTemplateRecognitionTest、FormTextTest、SettingsGroupIconsTest、SettingsResponsiveLayoutTest、`com.atom.chat.ui.Confirmation*`、`com.atom.chat.ui.QuickPhrase*`。包含实际点击根页导入/预览/移除、确认删除后不提交、根保存才提交、窄列表箭头命中与排序、跨条目 Undo 隔离、真实模板预览/冲突以及资源事务/协议既有用例。

首个根页回归在实现前实跑失败：缺命名资源行、根导入、独立常用语卡片。最初测试代码捕获非 final 局部变量导致的编译错误已先修正；保留的 `root-red.log` 是之后真正的三项断言失败，不冒充业务红灯。

命令及环境：各平台目录使用现有离线 AtomLib 0.4.0 输入 `-PatomlibMavenRepo=D:/AtomChat-Main/build/atomlib-slim-20261107/atomlib-maven-v040-final`，`--offline --no-daemon --console=plain`。Forge 用 JDK17 和既有证书探测例外；Fabric/Neo 用 JDK21，Neo 沿用 `-x downloadAssets`。不升级依赖，根版本仍 0.8.0。

证据集中在 `build/v090-visual-correction/`：

- `*-final.log`、`*-tests.zip`、`results.json`：最后相关测试、构建结果和候选 SHA256。
- `artifacts/*v0.8.0.jar`：此次待审三端候选，与 `build/v090-revision/artifacts/` 的已部署旧候选分开保留。
- `previews/<platform>/`：三端各89张原生 Skija PNG，共267张。中英文、280/420、深浅主题；包含非空资源根页 top/contents/full、两文件管理页、常用语列表及 hover/禁用边界、三类型模板列表及编辑。full 是加高离屏面板，紧凑实际高度请同时看 top/contents/管理页，不能只凭加高图判定响应式通过。
- `shared/src/test/resources/com/atom/chat/config/sample-emote.jpg`：从已批准草图夹具原样复制的可辨曼波图片，仅测试资源，不是实际下发包，也不读取/修改个人表情目录。列表同时展示已发布与待保存两种状态。
- `targets.log`、`bare.log`、`whitespace.log`、`jars.log`：targets、裸值（441基线/412实测）、diff whitespace、三包 CRC/atomlib-consumer 产物断言通过；没有更新基线或批量接受孪生差异。
- `pre-test-tracked-pngs.zip`：跑测试前保留现有158张跟踪 PNG 字节，测试后全部与测试前一致，不覆盖 CPU/GPU 及其他用户 WIP。
- `changed-files.txt`：本次纠偏的精确文件范围，不将当前工作区全部未提交差异算作本轮改动。
- `installed-unchanged.json`：只读回看三个实例的现有 AtomChat JAR，SHA256 均与上一批00:02部署回执一致，本轮未替换。

最终待审候选 SHA256：Fabric `ed56cc72ad8fea7b00aedc772cbac834b9b51257c920103a82c23f44fa27f709`；Forge `28dbd1c1b111689aebdb6caefa6d1913ea57dbb19597fcdd56e7c4eee1ac9b32`；NeoForge `d3903e0498e790d01e9d01150352f6ac59b780b65366e7bdb20e22de44414d2b`。最后一次单文件计数与提示文案修正后重新运行同一批测试和三端构建，随后再次收集截图/产物并核验JAR；之后没有修改运行代码。

已打开三端代表图核对真实图片、左右布局、宽版并排/窄版换行、危险色、紧凑列表、根页及底栏。新图未替代旧基线；草图继续作为布局参照，不冒充实际运行效果。

## 未收口与保留限制

用户最新要求覆盖常用语列表/单条编辑、模板列表/编辑/样例与提取预览，一并逐页对照认可草图。主代理复核发现：新增动作仍是整行，与稿中标题旁紧凑按钮不同；模板字段按钮固定最多两列；样例和结果没有按稿分卡；缺常用语编辑字数计数。当前证据也缺常用语单条编辑与模板下半部的实际高度滚动截图，不能凭顶部图认定整页符合。已交同一实施代理增量纠正和补图，候选未放行。肉眼曾怀疑模板前两行红移除图标消失，但原图三行对应窗口红像素各82，已撤回该不成立意见，没有据此要求修改绘制。

部署顺序更新为：完成修正与适用检查 → 主代理复核 → 先向用户展示原生渲染图 → 用户确认后才部署。既有部署授权和主代理审过均不替代本次用户看图确认。没有新建代理，没有启动游戏/服务端，没有提交、推送或发布。IME、真实动效/水波与窗口焦点、多客户端网络时序、Neo 旧 AtomLib 0.3.1-shadowcache 组合仍需实机验证；本轮 PNG 不构成这些项目的通过证据。

先前孪生台账33项未归类/8项陈旧、范围外日志31项遗留仍未处理，本轮没有重新扩审或宣称它们变绿。自动兼容90%和首次陌生离线跨服缺资料的身份/皮肤缺口仍未满足。普通异常的事务回滚不等同断电恢复。这些均未因视觉修正而被降级或隐去。
