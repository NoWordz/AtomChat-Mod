# 回退事故恢复计划

**目标**：恢复用户确认过的教程/警告浮窗/HUD底栏、点阵和阴影，修正真实面板编辑器与原生崩溃

**依据**：HEAD513f1942、stash d9b0ba6b、17:00 source-freeze、22:39错误包、用户截图
**约束**：最多一个代理；不恢复手机HUD/新开合动画；保留默认边框原色和玩家定制；不整体apply stash；不覆盖配置；不自动部署未完成审查的产物

## 顺序与验收

1. [x] 保存当前WIP补丁/新增文件、安装包、原始报告与截图、回退前tracked/untracked归档
2. [x] 核实17:00源码指纹及错误包Paint指针归零
3. [x] 先运行close→reset生命周期回归测试，再恢复PageTransitionComposite/BottomChromeTransition修复
4. [x] 从stash选择恢复教程及其真实控件/沙盒接线、共享警告材质和HUD底栏；合并22:29的Toast/主题hover/编辑器，不恢复旧Editor/模糊pass/手机动画候选
5. [x] 编辑器移除重复缩放滑条，设置页保留独立滑条；明确移动/尺寸入口、可见边中点手柄；输入用原屏幕坐标、外侧空白保活、模态优先；锚点合并重复点
6. [x] 查验主面板阴影绘制边界与点阵，恢复现役材质，增强阴影前先对照质量测试
7. [x] 唯一代理复用为code-reviewer进行集中独立审查；主代理修复，三端测试构建，离屏静态与逐帧证据
8. [x] 完成后再备份部署三实例并回读，报告实机限制、差异清单与未恢复项；不启动游戏/commit/push/release

## 文件职责

- shared/onboarding + 两映射SettingsSectionPage + 三端Screen：线性教程、真实聚焦与白名单
- shared/ui/ConfirmationSurface、PickerModalPaint、ConfirmationDialog + NotificationHudEditor/Screen：既有警告窗材质与动效复用
- PageTransitionComposite、BottomChromeTransition：可复用Screen的native资源生命周期
- PanelEditor/Layout/Input/Model：只编辑几何，内容交互保留，明确工具入口，绘制与命中同一坐标
- DotTexture/SoftShadow/ShadowTokens：渲染材质与有界缓存；按像素/帧证据验证

## 事实与范围

- 18:56 reset回到11-11检查点，stash保留回退前源码；不能仅据reflog判定操作者或动机
- 教程/HUD关键源码与17:00指纹一致，可以恢复；未匹配部分逐项核查，不声称全部找齐
- 22:39崩溃Paint._nGetColor4f，现场_ptr=0；当前reset未重建Paint，stash中已有修复
- 当前编辑器把外侧点击PASS给关闭逻辑，且共用正文坐标逆变换；单handler测试漏掉真实链

## 2026-11-14 grill 最终定案（后续实施正本）

用户明确：原意只回退DeepSeek自己的改动，不同意连带撤回此前认可内容。已完成恢复与三端构建，并于12:28备份部署；但旧编辑器上边框移动方案未被认可，不能当验收通过。

- 四角+四条边均缩放，编辑期间常显8个小方块，整条边都可拖
- 独立移动把手=顶部外侧正中的短胶囊，中央四向箭头；顶部空间不足时临时移内侧，不改变面板几何
- 锚点沿用HUD编辑器的显示/吸附规则，每处仅一个，不按接近满高的面板中心挤出3个点
- 内部真实面板可交互，外侧空白不关闭，Esc取消
- 悬浮栏说明“拖动把手移动 · 拖动边框改尺寸”，按钮只还原/保存；还原=进入本次编辑时的几何，主题/颜色/内容缩放不受影响
- 返回键旁增加回旋箭头恢复按钮，只恢复当前页签，包括折叠/未滚到项；非整个设置页
- 恢复按钮直接沿用聊天页返回+过滤副按钮的尺寸、间距、命中范围、hover/press/ripple和标题避让
- 确认弹窗复用真实警告窗美术、动效、阴影：红图标、红确认、灰取消；教程和正常编辑主操作保留强调色例外。明确当前页签名，取消不改，保存失败提示
- 使用统一默认值、仅重置该页签配置，不删除预设、图片、聊天等个人数据
- 先实现/行为回归，再最多1个code-reviewer集中审查，再三端构建和备份部署；不复活被排除的手机HUD/新开合动画

### Grill 实施进展与曾待答分支（历史；已由下节A定案覆盖）

八向缩放/8点/胶囊MOVE/贴顶内移/9独立锚点和实时吸附已实施；当前页签恢复默认65字段registry、真实警告窗、返回旁回旋箭头复用header副按钮已接3端。新定向和真实SettingsResetVisualTest通过；UI证明位于docs/evidence/2026-11-14-editor-grill/1.21.1-fabric。

独立同一代理新增1项P2：editor开着时恢复调整页签成功，旧取消快照可在Esc/removed时撤销已经保存的默认几何。当时向用户提问A成功退出/B继续并rebase；现用户已选A，代码已按下节修复。该段仅记录当时状态，不是待答或未实施的现役结论。12:28安装包仍属旧候选，不能当作本次新构建。

当前唯一代理Plato已resume，作为只读调查+独立code-reviewer，已写build/editor-incident-20261113/settings-tab-map.json、notes.md、grill-review.md；无新spawn。测试日志eight-way-red/green、tab-reset-red/green、grill-targeted-final、grill-visual。一次registry拆行少括号已修；Prompt测试最初忘记渲染至progress=1导致确认被开场门禁拦，修测试按真实渲染流程后通过，不修改警告窗门禁。

### 用户最终纠正与硬门禁（11-14）

用户已选A：成功恢复调整默认后退出editor。另明确：警告一律红图标/红确认/灰取消；此前教程开始/正常编辑操作accent是特例，不得套到警告。SVG必须在hover中居中，同类尺寸/间距复用。

已用原生UiStyleContractTest复现3项红灯：普通open与真实tab reset错误主题色、RESTORE可见墨迹偏2像素。两入口现在共享tight bounds，按Path generation失效有界cache；45个密度/rest/hover/press样本，warning红色检查与原教程例外均绿。A组合回归先红560覆盖480，后以committed标记+成功回调follow新几何再退出修复。

曾准备安装UI硬门禁（下列为撤回前历史，已取消）：docs/UI_CONTRACT.md+根AGENTS，保护组件/调用点/检查/Gradle与hook摘要，无自动更新入口；本机.git独立信任摘要，CI用提交HEAD，precommit先验verifier自身；最后Windows只读。完整写权限仍能显式绕过，不假称绝对权限隔离。当前初始清单尚待唯一代理复审后激活，不能把准备阶段当最终锁定状态。


### 唯一实施代理接手与候选审查顺序（2026-11-14）

本轮已由原独立审查代理接手剩余实施与验证，不再新增代理。主代理在候选完成后独立复审。现役行为为A：调整页签恢复保存成功后退出编辑器，失败不结束；警告open默认红图标/红确认/灰取消，教程例外仍强调色。SVG统一tight bounds和generation失效缓存。三项工具P2已做代码修正，正在以真实变异与完整构建收口。

交付顺序以用户最新补充为准：完成门禁初始化、变异验证、三端test build、源冻结和精确JAR检查、候选报告及部署准备 → **暂停在实际部署和Windows readonly激活之前** → 主代理code-review放行后，仍由同一实施代理执行备份部署、独立回读及readonly激活。不得拿旧12:28receipt充当新部署。未启动游戏、不提交/推送/发布。

阶段证据与候选报告见build/editor-grill-20261114/subagent-progress.md及docs/verification/2026-11-14-editor-grill.md；该进度/验证文档为可更新交付记录，固定规则正本AGENTS.md和docs/UI_CONTRACT.md将进入首次保护清单。


### 最新：用户撤回全部门控（2026-11-14）

用户明确“那个门控是气话，不要继续做了，把前面要求的功能实现完就行”。已立即停止guard变异进程，精确备份并移除本轮checker/manifest/trust/仓库与安装hook、Gradle uiContractCheck/bootstrap/dependency及仅guard用attributes；从未设readonly，未动原有hook/属性。移除回执build/editor-grill-20261114/guard-removal.json。AGENTS和UI_CONTRACT改为普通功能/视觉回归约定，不再强制锁/禁解锁。

仅继续既有功能、三端完整test build、targets/JAR/bare和native像素回归；不继续SHA门禁变异或建立保护清单。构建后正常记录source freeze/JAR摘要是交付身份记录，不是锁。完成候选让主代理独立复审，放行后同一代理才部署并独立回读，不激活readonly。


### 本轮候选完成，等待主代理复审（2026-11-14）

三端完整test build：Fabric1894、Forge1879、Neo1879，均0失败/错误，各7个既有skip；每端UiStyleContractTest4项无skip、45实际raster样本。targets/精确JAR/chat assets/bare433<=441/diff通过，562生产源冻结仅为交付身份记录。普通额外log-prefix扫描还剩HEAD原有25项，新增Prompt日志格式已修，不扩历史范围。

已按用户撤回门控清除本轮所有SHA/trust/hook/Gradle guard/readonly计划。当前候选未部署，先交主代理独立审查；CCB准备时仍在运行，放行后再fresh检测与备份部署/独立回读。不再readonly。完整证据docs/verification/2026-11-14-editor-grill.md与build/editor-grill-20261114。


### 主代理复审放行及本次实际部署完成

2026-11-14主代理独立复审无新可操作缺陷，独立回读候选身份和计数后明确放行。全新备份`D:\AtomChat-Backups\2026-11-14-editor-grill\20261114-152929-912953`，fresh进程检测无游戏，逐目标只替换AtomChat；另进程回读安装/候选/旧备份、全部其他217/434/239项hash/size/mtime及562源全部一致。新receipt为build/editor-grill-20261114/deployment-receipt.json，简明回执deployment-summary.md。

CCB新基线434而非旧462：两次观察之间28个旧备份/旧版本条目已从目录消失，未由本代理删除。第一次尝试在替换前停止保留receipt；第二次重新建完整当前基线后部署成功。没有部署运行中的游戏、修改其他mod、启动游戏、生产改动或提交发布；门控已取消，不设readonly。旧准备/暂停段仅为历史，实际状态以本节及最终verification为准。
