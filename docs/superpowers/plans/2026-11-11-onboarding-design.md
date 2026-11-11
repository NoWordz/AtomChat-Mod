# AtomChat v0.7.0 新手教程设计契约

状态：设计冻结，供主代理实现；本文件不代表已经过游戏内视觉或交互验收。工作区含未提交改动，本设计未修改 Java、未构建、未启动游戏、未提交。

## 1. 探针与基线

- 已读取 `~/.agents/skills/mc-ui/SKILL.md`、其 `references/handoff.md` 和 `~/.agents/memory/MEMORY.md`。
- 已实际查看 `docs/evidence/2026-11-11-v070-second-feedback/panel-gap.png`。可见内容为手机比例深色面板：顶部返回、标题“外观”、时间；下方三页签“主题 / 显示 / 调整”；内容按“显示效果 / 动效 / 壁纸”分组，带折叠标题、细横线、蓝色强调、圆角卡片、开关、滑条和预设缩略图。截图中面板外有 Minecraft 世界，面板内部使用现有像素字与点阵装饰。该图同时记录了外描边与背景之间的细缝，不属于本次教程交互结构，但教程不得再引入第二套面板、外描边或点阵菜单。
- 当前代码的警告/确认窗是 `ConfirmationDialog`，已经具备居中卡片、遮罩、标题图标、左右按钮和 110ms 弹入；其警告图标和右侧危险色目前硬编码。教程提示复用其几何与材质，增加轻量变体。
- 当前导航根页是 `CHAT_LIST / PROFILE / SETTINGS`，详情页是 `WORLD_CHAT / PRIVATE_CHAT / SETTINGS_SECTION / PROFILE_DETAIL`；新教程不得增加新框架或绕开现有导航栈。

## 2. 硬约束

1. 教程只在当前手机面板内部绘制，使用现有 Skija、`UiLayout`、`UiTokens`、`UiCards`、`PickerModalPaint`、`FontManager` 和 AtomLib 线图标体系。教程专用图标放 mod 本地 `com.atom.chat.onboarding.GuideIcon`，不改 AtomLib 依赖中的 `AppIcons`。
2. 不引入 Yoga、HTML、WebView、Compose 或第二套动画计时器；沿用现有 `UiMotion`、导航入场和 `Animations.enabled()`。
3. 不重新设计底栏、设置点阵、菜单、设置行或关于页；教程只加一次性提示、锚点高亮、批注和独立关于行。
4. 教程目标矩形必须来自页面的真实布局/命中几何；禁止在教程层另算一套坐标。
5. 教程期间的页面输入默认拦截。只放行当前目标区域、当前目标显式扩展区域和教程自己的四个操作。
6. 发送、屏蔽/取消屏蔽、传送命令、图片上传、除外观外的个人素材写入均只演示；不得写真实存储、聊天、网络或历史。外观页是唯一开放的修改区，用户主动修改按现有保存链路正常持久化。
7. 所有演示数据带可见“演示”标记，退出即丢弃；不得进入 `ChatStore`、`PrivateChatStore`、`BlockList`、历史、通知队列或缓存。

## 3. 首次提醒与本地状态

### 3.1 状态文件

新建独立的 `atomchat/client/onboarding.json`。路径基于现有个人数据根：`CacheDirs.clientRoot()`，不要放进 `config`、`cache` 或 `client-data.json`。现有 `clientRoot()` 与 `clientDataPath()` 见 `shared/src/main/java/com/atom/chat/util/CacheDirs.java:33`、`:45`。

建议 schema：

```json
{
  "schema": 1,
  "promptChoice": "watch",
  "promptDecidedAt": "2026-11-11T00:00:00Z",
  "tourOutcome": "completed",
  "lastTourStartedAt": "2026-11-11T00:00:00Z"
}
```

- `promptChoice` 只允许 `watch` / `dismissed`。没有文件、没有该字段或未知 schema 都视为“尚未决定”，显示一次提示。
- `tourOutcome` 只允许 `completed` / `exited`，仅作记录；逐次“跳过此步”不结束教程，**不保存当前步骤，不做续看**。
- 使用临时文件 + 读回校验 + 原子替换，沿用 `PersonalDataStore` 的写入纪律：`shared/src/main/java/com/atom/chat/config/PersonalDataStore.java:50`、`:73`。写入前走现有 `DataMigration.noLinks/allowPersonalPath` 检查。
- 文件位于 `client/`，现有缓存清理目标只有图片/服务器包/诊断目录，不得被“清理图片缓存”删除。
- 点击“观看教程”或“不再提醒”时，先在内存提交决定，再同步尝试落盘；即使落盘失败，当前会话也不再弹提示，并记录一次可诊断告警。成功标准是重启后仍不弹。

### 3.2 提示窗

复用 `ConfirmationDialog` 的几何和材质（`shared/src/main/java/com/atom/chat/ui/ConfirmationDialog.java:26`、`:72`），新增 `DialogTone.TUTORIAL`：

- 标题图标改为 mod 本地新增的 20×20 开卷线图标 `com.atom.chat.onboarding.GuideIcon.PATH`（应为 Skija `Path`）；颜色用当前强调色 `UiTokens.accentFill()`，替代硬编码警告红和 `ICON_WARNING_PATH`（现状见同文件 `:87`）。图标继续采用现有圆头、圆角折线风格，不修改 AtomLib 的 `AppIcons`。
- 文案：标题“新手教程”；正文“了解聊天、玩家管理和主要设置入口。随时可从“关于”重播。”；左按钮“不再提醒”；右按钮“观看”。
- 右按钮使用强调色填充，文字用 `UiTokens.onAccent(accent)`；左按钮保持现有中性面。禁用关闭红和危险强调。
- 圆角、内边距、按钮高度、字号全部沿用 `ConfirmationLayout`、`UiTokens.DIALOG_*`、`UiTokens.BUTTON_H`、`FONT_BUTTON`，不新增尺寸常量。
- 教程提示不允许点击遮罩或按 Esc 关闭，避免产生第三种未记录状态；明确点任一按钮才结束提示。它仍通过现有 `ConfirmationInput` 吃掉所有输入。
- 提示在首次打开 AtomChat 面板且界面已可操作时出现，不限定页面类型；打开时避开图片预览、裁剪器、取色器、设置确认窗、输入工具浮层和迁移警告。多个屏幕重绘不得重复打开。

## 4. 关于页重播入口

在 `aboutRows()` 的 Hero 之后、作者组之前插入一条独立、全宽、自带图标的行动行（`platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsSectionPage.java:1059`）：

- ID：`onboarding_replay`
- 图标：同一 mod 本地 `GuideIcon.PATH`，绘制尺寸 `UiTokens.SETTINGS_TILE_ICON`，颜色跟随主文字色。
- 标题：“新手教程”；副标题：“从第一步重新播放”。
- 右侧动作词：“播放”。
- 行控件：现有 `UiCards` 卡面、`SETTINGS_ROW_PAD`、`SETTINGS_ROW_GAP`、`settingsRowRadius()`；不另造点阵或按钮样式。
- 点击行为：关闭当前设置/提示浮层，打开教程会话并强制 `stepIndex=0`；即使上次在中间退出也不能续看。
- 该行始终可见；它不重置 `promptChoice`，不会重新开启自动提醒。

## 5. 教程浮层

新增四个职责单元，避免教程继续膨胀 `AtomChatScreen`：

- `OnboardingStore`：纯本地 JSON 读写。
- `TutorialSession`：纯状态机，持有步骤、入口页、当前索引、演示数据；不画 Skija。
- `TutorialOverlay`：使用现有 Skija/AtomLib 绘制遮罩、目标环、批注和按钮；几何由屏幕传入。
- `TutorialDraftGuard`：在输入、发送、引用和快捷短语步骤前保存真实草稿、光标和引用槽，步骤期间只操作内存演示缓冲，退出时原样恢复；教程期间阻断普通草稿/引用槽回写。

屏幕只负责把真实锚点、实际导航和输入闸门接到这些对象。教程每次启动都从头开始，不持久化步骤。

### 5.1 目标锚点

每个步骤声明 `page`、一个或多个 `anchor`、允许交互区域和副作用策略。锚点必须复用现有几何函数：

- 底栏：`rootLayout().tabBar`（`shared/src/main/java/com/atom/chat/ui/UiLayout.java:34`）。
- 会话列表视口：`listLayout().list`；演示行复用 `ConversationListPage.rowRect()` 的同一布局规则（`platforms/1.21.1-fabric/src/main/java/com/atom/chat/page/ConversationListPage.java:609`）。
- 玩家菜单屏蔽行：由 `ContextMenuController` 同一行高和菜单盒计算，行号固定 2；不得在教程层复制行几何（`shared/src/main/java/com/atom/chat/ui/ContextMenuController.java:294`）。
- 消息菜单：演示消息气泡的命中矩形；菜单打开后使用 `ContextMenuController` 自己的菜单盒和各行矩形。消息菜单的引用行是索引 1（`shared/src/main/java/com/atom/chat/ui/ContextMenuController.java:408`）。
- 输入与发送：`layout.inputBar`、`layout.sendBtn` 分别独立成步，不能合为一个“输入框/发送”步骤。
- 搜索：`layout.imageBtn` 打开工具浮层，工具第 0 行进入搜索；搜索状态条使用 `layout.searchStatus`，输入继续复用 `layout.inputBar`（`shared/src/main/java/com/atom/chat/tools/ComposerTools.java:37`、`:77`；`shared/src/main/java/com/atom/chat/ui/UiLayout.java:45`）。
- 世界消息过滤：世界聊天标题栏的 `filterButton()`；不另做教程按钮（`platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java:1748`）。
- 快捷短语：`layout.phraseBtn`；面板打开后用 `QuickPhrasePanel.overPanel()` 的实际面板矩形扩展允许区域（`platforms/1.21.1-fabric/src/main/java/com/atom/chat/ui/QuickPhrasePanel.java:180`、`:393`）。
- 个人档案主卡：档案页新增只读 `heroAnchor()`，返回 `bannerRect`、`avatarRect`、`signatureRect` 的并集（`platforms/1.21.1-fabric/src/main/java/com/atom/chat/page/ProfilePage.java:745`、`:757`、`:804`）。
- 表情按钮：`layout.emojiBtn`；面板打开后用面板自身的实际外框扩展为允许区域。
- 图片按钮：`layout.imageBtn`；图片工具浮层打开后用其实际外框扩展为允许区域。
- 设置首页：`SettingsHomePage.tileRect(...)`（`platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsHomePage.java:81`）。
- 设置组页签：`layout.chipBar`；具体行使用 `SettingsSectionPage.rowRect(...)` / `hit(...)` 的同一几何（`platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsSectionPage.java:1308`、`:3265`）。
- 关于重播行：由 `aboutRows()` 返回的真实 `RowIndex` 和 `rowRect(...)` 取得，不按固定 Y 写死。

### 5.2 视觉规格

- 遮罩：只在面板内部，颜色复用现有弹窗底色 `Color.makeARGB(200, 8, 9, 14)`。用四个矩形围出目标孔洞，目标本身保持原亮度；不做全屏模糊。
- 高亮：目标外框用当前强调色画 1.5 逻辑像素圆角环，外部再留 3 逻辑像素透明间隙；圆角取目标自身卡片圆角，找不到时用 `settingsRowRadius()`。
- 批注卡：`PickerModalPaint.surface()` 或同一材质；最大宽度 `DIALOG_MAX_WIDTH`，左右至少 `PANEL_TOP_GAP`，内部 `SETTINGS_ROW_PAD`。
- 批注位置：目标中心在面板上半部则放下方，否则放上方；与目标至少隔 `SETTINGS_ROW_GAP`，四边夹在面板内。目标与批注确实放不下时缩小正文行数并减少间距，不覆盖目标。
- 标题：`FontManager.boldFont(UiTokens.SETTINGS_TILE_TITLE)`，主文字色。
- 正文：`FontManager.font(UiTokens.FONT_BODY)`，次文字色；最多 3 行，走现有 Skia 换行和测量，不在绘制时另算宽度。
- 计数：标题行右侧“3 / 16”，`FONT_QUOTE`，次文字色。
- 控制区：2×2 等宽按钮；按钮高 `BUTTON_H`，间距 `BUTTON_GAP`，圆角使用现有胶囊公式。第一行“上一步 / 下一步”，第二行“跳过此步 / 退出”。
- “下一步”用强调色；“上一步 / 跳过此步 / 退出”中性面。最后一步“下一步”文案改为“完成”。
- 图标与控件沿用现有线宽、圆角、hover 面；不新增阴影族、描边族或字体。
- 动效仅用 `UiMotion.POPUP_MS`、`HOVER_MS` 和现有 `Animations.enabled()`；动画关闭时直接切换。

### 5.3 输入与操作语义

- 教程浮层位于所有页面、菜单和演示横幅之上；目标区域外的页面点击、拖拽、滚动、滚轮和文本输入全部吞掉。
- 当前步骤声明的目标区域可以按真实控件行为点击；教程按钮永远可点。
- 多阶段步骤只在当前阶段放行对应锚点。例如工具菜单先放行“搜索”行，进入搜索后再放行状态条与输入框。
- 生产消息菜单保持右键入口；教程内允许普通点击打开同一个 `ContextMenuController.openBubble(...)`，用于教学演示。不新增长按手势，也不创建第二份菜单。
- “上一步”：回到前一步，并由步骤声明重新准备页面和滚动位置。
- “下一步”：前进一步；最后一步完成教程。
- “跳过此步”：不执行当前步骤的完成条件，直接进入下一步；教程继续，不记录结束状态。
- “退出”：立即结束整个教程，记录 `exited`，恢复进入教程前的导航页。显示确认时点“不再提醒”不算退出教程。
- Esc 等同“退出”；左右方向键可映射上一步/下一步，但不得触发页面快捷键。
- 步骤进入时调用现有滚动控制把锚点滚入可见区；用户在教程中不自行滚动。

### 5.4 副作用闸门

在教程会话存在时启用 fail-closed 白名单：

- 允许：教程按钮、导航切页、消息菜单与引用预览、搜索查询与结果跳转、设置页签/组标题展开、外观页所有现有主题/壁纸/颜色/滑杆/开关的主动修改。输入框、快捷短语选择和世界过滤只操作 `TutorialDraftGuard` 的内存预览。
- 演示：发送、屏蔽/取消屏蔽、传送、图片或表情上传、头像/横幅选择/签名编辑、快捷短语新增/编辑/删除、历史清理、缓存清理、测试通知声音；世界过滤只切换临时显示态，不写 config。
- 演示动作只产生带“演示”标记的局部反馈或临时视觉，不调用真实写入。尤其禁止进入 `sendMessage`、图片上传链、`BlockList.setBlocked`、命令发送和配置保存。实际写入点可核对：`AtomChatScreen.java:3364`、`:4205`、`ContextMenuActions.java:249`。
- 外观是唯一开放持久化写区：其命中区域覆盖 `chipBar + list`，用户选择主题、壁纸、颜色、透明度等后走现有 setter/save，关闭教程和重开面板仍保留。

### 5.5 演示浮层、层级与避让

- 本步复用 `TutorialDemoPanels` 的真实控件沙箱；`reset()` 清空内存状态，`render(...)` 绘制 demo 表面，`click/scroll/key/type(...)` 只更新内存演示状态，`close()` 释放本地图像与面板资源。
- 绘制顺序固定为：页面及原控件 → 教程遮罩、聚光孔和目标环 → `TutorialDemoPanels.render(...)` → 批注卡及教程控制按钮。真实 demo popup 必须高于遮罩，批注卡位于它之上，但不得覆盖由 `bounds(...)` 报出的 popup。
- demo popup 的顶层优先级从高到低为：图片预览、消息/玩家菜单、工具菜单、快捷短语、表情面板、搜索/引用演示层。绘制按相反顺序落层，点击、滚轮、Esc 和文本输入按同一优先级派发。
- `bounds(...)` 返回当前实际 demo 表面：图片预览使用聊天列表视口，搜索使用列表顶部的结果区，引用使用输入框上方的引用条，菜单使用 `ContextMenuController` 的真实菜单盒，其余已打开面板使用各自外框。Overlay 以该矩形避让批注；空间不足时缩短或滚动批注，禁止覆盖目标 popup。
- 图片预览、搜索结果和引用条的按钮标题、状态提示均走语言表；演示消息正文、玩家名和短语是固定示例内容。新增键：`atomchat.tutorial.demo.filter.system`、`atomchat.tutorial.demo.filter.players`、`atomchat.tutorial.demo.quote`。
- 生产消息菜单仍只用右键；教程内点击演示消息可调用同一个 `ContextMenuController.openBubble(...)`。不增加长按手势，不复制第二套菜单或命中几何。

## 6. 16 步教程

采用 16 个顶层步骤；输入/发送、菜单/引用、搜索/过滤这类相关能力在同一顶层步骤内分阶段高亮，不通过增加顶层步数掩盖交互缺失。

| # | ID | 页面/锚点 | 说明与允许交互 | 演示/写入策略 |
|---|---|---|---|---|
| 1 | `NAVIGATION` | `CHAT_LIST`；底栏 `tabBar` | 说明底部“聊天 / 个人 / 设置”三入口，顶部是当前页。允许点底栏切换；下一步重新对齐页面。 | 正常导航；不改配置。 |
| 2 | `CONVERSATIONS` | `CHAT_LIST`；列表视口 | 说明“公共频道”与玩家卡：公共卡进大厅，玩家卡进私聊，右键玩家卡打开菜单。只在真实列表无玩家时插入临时玩家卡。 | 临时卡和缩略图带“演示”标记；不写聊天/未读/历史。 |
| 3 | `COMPOSE` | `WORLD_CHAT`；分阶段 `inputBar → sendBtn` | 同一顶层步骤先讲输入：聚焦、光标、多行自动换行和选词，文字进入内存演示草稿；再讲发送：点击发送或 Enter 只生成带“演示”的临时气泡。 | 保存真实草稿/光标/引用并隔离；不调用 `sendMessage`/`SendPipeline`，不发网络包，不写 ChatStore/历史/未读或 DraftStore。 |
| 4 | `MESSAGE_ACTIONS` | `WORLD_CHAT`；演示消息 → 真实消息菜单盒 → 引用行 → 引用条 | 生产使用右键；教程内点击演示消息即可打开同一消息菜单，再分阶段高亮复制、引用与图片保存。本步实际打开菜单并展示引用条，危险动作仍锁定。 | 使用演示消息；复制/保存不落盘；引用槽只作内存演示，退出恢复原槽。 |
| 5 | `SEARCH_FILTER` | `WORLD_CHAT`；工具菜单搜索行 → `searchStatus + inputBar`；随后标题栏 `filterButton()` | 同一顶层步骤分两阶段：先介绍并实际进入当前页搜索，键入查询、上下条跳转、Enter 下一条、Esc 退出；再介绍公共频道“全部 → 系统 → 玩家”过滤。 | 搜索只读；过滤只切临时显示态，不写 config；退出恢复草稿、光标和原过滤。 |
| 6 | `PLAYERS_BLOCK` | `CHAT_LIST`；玩家卡 → 菜单第 3 行（索引 2） | 展示玩家卡的“资料 / 传送 / 屏蔽或取消屏蔽”。实际打开菜单，只演示例行；不得用静态文字替代菜单。 | 不调用 `BlockList.setBlocked`，不写 config，不发送传送。 |
| 7 | `PROFILE` | `PROFILE`；头像、横幅、签名并集 | 说明点头像换头像/恢复皮肤，点横幅换横幅，点签名编辑；其他玩家资料只读。 | 头像/横幅选择器和签名编辑只显示演示态；若已存在图片可静态预览但不写入。 |
| 8 | `EXPRESSIONS` | `WORLD_CHAT`；`emojiBtn → 面板` | 实际打开现有表情面板，示例切换表情、颜文字、自定义表情三页；选择只进入演示草稿。 | 禁止发送和上传；退出丢弃演示选择。 |
| 9 | `IMAGES` | `WORLD_CHAT`；`imageBtn → 工具浮层`，加一张演示图片气泡 | 实际打开图片工具入口，并展示图片消息预览；教程不进入原生文件选择或上传链。 | 不上传、不下载、不写缓存；演示气泡固定带“演示”。 |
| 10 | `QUICK_PHRASES` | `WORLD_CHAT`；`phraseBtn → 快捷短语面板` | 实际打开快捷短语面板，展示本地与服务器短语；选入演示草稿，展示新增/编辑/删除入口。 | 插入只进演示缓冲；新增/编辑/删除不落盘，不改 `client-data.json`；不发送。 |
| 11 | `SETTINGS_HOME` | `SETTINGS`；四个 `tileRect` | 说明聊天、外观、隐私、关于四组。允许点卡片进入查看；下一步重新定位。 | 正常导航；不改设置。 |
| 12 | `CHAT_SETTINGS` | `CHAT` 设置页；`chipBar` | 说明“消息 / 历史 / 通知 / 高级”四组；允许切组浏览，不执行组内破坏性行。 | 组切换仅会话状态。 |
| 13 | `APPEARANCE` | `APPEARANCE` 设置页；`chipBar + list` | 说明“主题 / 显示 / 调整”：主题预设、壁纸、边框/模糊、布局/透明度和配色。局部开放实际交互。 | 主题、壁纸、颜色、滑杆、开关按正常保存；这是唯一开放持久化写区，可随时跳过此步。 |
| 14 | `PRIVACY` | `PRIVACY` 设置页；消息过滤组与已屏蔽组 | 说明隐藏屏蔽消息、查看/取消屏蔽入口；真实屏蔽仍从玩家菜单发起。 | 只查看组；取消屏蔽/名单写入均演示。 |
| 15 | `NOTIFICATIONS` | `CHAT` 设置页的 `notify` 页签（已核对 `CHAT_CHIPS` 索引 2）；通知组 | 说明提及/私聊横幅、声音、反馈音、HUD 位置和“测试通知”。 | 演示横幅不入真实队列、不响铃、不写 HUD 配置。 |
| 16 | `ABOUT` | `ABOUT`；重播行及关于组 | 说明重播入口、作者、Mod 信息、第三方组件、高级；允许点独立重播行。 | 重播从 1 开始；不重新开启自动提示，不保留步骤。 |

## 7. 空列表演示

真实玩家列表为空时，只在教程第 2 步渲染一张 `TutorialDemoConversationRow`，插在公共卡之后、原“无在线玩家”空态之前：

- 名称：“演示玩家”；紧邻一个胶囊标签“演示”。
- 预览：固定演示图片缩略图，外加一行“图片消息 · 仅演示”。
- 头像：由现有 Skia 渐变/占位头像绘制，不查询真实 UUID、不读网络、不落盘。
- 该行使用真实 `ROW_H`、圆角、头像尺寸和缩略图裁切；不改变列表布局算法。
- 点击该行进入第 3 步的屏蔽示意，或直接高亮屏蔽行；绝不打开真实私聊。
- 世界聊天没有消息时，可在第 9 步插入一条同样带“演示”徽标的图片气泡；退出教程即移除。

## 8. 关键源码锚点

| 领域 | 真实入口 |
|---|---|
| 警告/确认弹窗 | `shared/src/main/java/com/atom/chat/ui/ConfirmationDialog.java:14`、`:26`、`:41`、`:87` |
| 弹窗输入吃全量 | `shared/src/main/java/com/atom/chat/ui/ConfirmationInput.java:11` |
| 个人数据根 | `shared/src/main/java/com/atom/chat/util/CacheDirs.java:29`、`:33`、`:37` |
| 原子个人数据写入 | `shared/src/main/java/com/atom/chat/config/PersonalDataStore.java:50`、`:73` |
| 导航页枚举 | `shared/src/main/java/com/atom/chat/nav/AppPage.java:3` |
| 设置页签 | `platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsSectionPage.java:311`、`:315` |
| 设置四页 | `shared/src/main/java/com/atom/chat/settings/SettingsSection.java:8` |
| 设置首页命中 | `platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsHomePage.java:81`、`:186` |
| 设置行与空态 | `platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsSectionPage.java:872`、`:3265`、`:2973` |
| 关于行 | `platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsSectionPage.java:1059` |
| 会话卡点击 | `platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java:3904` |
| 玩家菜单执行 | `shared/src/main/java/com/atom/chat/ui/ContextMenuController.java:159`、`:294`；`ContextMenuActions.java:223`、`:249` |
| 个人档案入口 | `platforms/1.21.1-fabric/src/main/java/com/atom/chat/page/ProfilePage.java:923` |
| 消息右键菜单与引用 | `AtomChatScreen.java:4307`、`:4315`、`:4165`；`ReplySlotStore.java:116` |
| 输入与发送 | `UiLayout.java:43`、`:48`；`AtomChatScreen.java:3364`、`:4215`、`:4223`；`SendPipeline.java:96` |
| 搜索工具 | `ComposerTools.java:37`、`:77`；`ComposerSearch.java:36`、`:47`、`:64` |
| 世界消息过滤 | `WorldFeedFilter.java:36`、`:42`、`:83`；`AtomChatScreen.java:1748`、`:4074` |
| 快捷短语 | `QuickPhrasePanel.java:299`、`:393`、`:486`；`QuickPhraseWiring.java:120` |
| 表情按钮 | `AtomChatScreen.java:4107` |
| 图片按钮 | `AtomChatScreen.java:4205` |
| 通知跳转 | `shared/src/main/java/com/atom/chat/notification/NotificationJumpService.java:139`、`:187` |

## 9. 验收场景

### 状态与入口

1. 删除 `atomchat/client/onboarding.json` 后首次打开任意 AtomChat 面板页：提示只出现一次，不要求先进入公共世界聊天；左右按钮文案正确，警告三角消失，教学图标和强调色正确。
2. 点“观看”：文件立即出现 `promptChoice=watch`，教程从第 1 步开始；重开客户端不再自动提示。
3. 点“不再提醒”：文件立即出现 `promptChoice=dismissed`，教程不启动；重开客户端不再自动提示。
4. 清理图片缓存/服务器包/诊断缓存后，上述字段仍保留；`config` 内不出现教程字段。
5. 状态 JSON 损坏或 schema 未知：备份或保留原文件并只显示一次提示；不得崩溃、不得反复弹出。
6. 旧用户升级、新用户首次启动、单一世界和多人世界三种入口都走同一缺失状态分支。

### 教程导航

7. 16 个顶层步骤编号连续，计数为 `n / 16`；上一步、下一步不越界；最后一步显示“完成”。
8. 从关于页重播时固定从第 1 步开始；中途退出或关闭客户端后再次重播仍从第 1 步开始。
9. “跳过此步”进入下一步且教程继续；“退出”回到进入教程前的页面；两者都不会让自动提示复活。
10. 目标外点击、文本输入、拖拽、滚轮均被吞；目标内部和教程按钮可操作；窗口 resize 后高亮和命中仍一致。
11. 动画关闭时切换即时、无残留遮罩；反复开关教程不泄漏 Skia save/saveLayer。

### 主功能覆盖

12. 第 2 步无真实玩家时只出现一张“演示玩家”卡和演示缩略图；真实列表非空时不伪造新玩家。
13. 第 3 步点屏蔽示例前后，`blockedPlayers`、`client-data.json`、屏蔽界面状态、网络命令均不变。
14. 第 4 步点头像/横幅/签名不会打开真实文件选择器或提交签名；退出后档案资源不变。
15. 第 5 步能切换表情/颜文字/自定义表情视图；不出现发送，不写上传/表情目录。
16. 第 6 步图片入口只出现演示菜单或演示气泡；`ImageLoader` 缓存、上传目录、聊天内容不变。
17. 第 3 步分阶段命中 `inputBar` 与 `sendBtn`；教程输入保存/恢复原草稿、光标和焦点；发送示例不调用 `SendPipeline`，提示数量、网络、历史和未读均不变。
18. 生产仍由右键打开消息菜单；第 4 步内可点击演示消息打开同一份菜单。点“引用”只出现引用条和发送者/摘要，真实引用槽在退出后恢复。
19. 第 5 步能从工具菜单进入搜索，键入查询、上下条跳转、Enter 下一条、Esc 退出；随后能循环三种过滤显示。退出后原草稿、光标和过滤恢复，`worldFeedFilter` 文件值不变。
20. 第 6 步实际打开玩家菜单并只演示屏蔽行；点击前后 `blockedPlayers`、`client-data.json`、屏蔽界面状态、网络命令均不变。
21. 第 7 步点头像/横幅/签名不会打开真实文件选择器或提交签名；退出后档案资源不变。
22. 第 8、9 步能实际打开表情面板与图片工具入口；不上传、不下载、不写表情目录或图片缓存。
23. 第 10 步能打开快捷短语、滚动、选中并插入演示草稿；新增/编辑/删除不写 `client-data.json`，选中项不发送。
24. 第 11、12、14、16 步中设置分组名称与真实源码一致；页签切换正常，破坏性行不会执行。
25. 第 13 步主动更换主题、壁纸、颜色或滑杆后，关闭设置、重开面板仍保留；退出教程不撤销用户修改。
26. 第 15 步使用已核实的通知页签索引 2；演示通知不回灌真实队列、不响铃、不写 HUD 位置。
27. 第 16 步能点击独立重播行；关于页原有 Hero、作者、Mod 信息、第三方、高级组仍可折叠且布局未漂移。

### 工程验证顺序

28. 单测：状态缺失/读取/损坏/原子写、提示二选一、从头重播、步骤边界、输入白名单、草稿/引用恢复、分阶段锚点、演示动作零写。
29. 套件：三端现有测试与设置页回归，确认 `ConfirmationDialog` 原警告样式不变。
30. 离屏 smoke：手机参考宽度、窄窗口、浅色/深色主题、动画开/关，检查遮罩孔洞、卡片不越界和文字换行。
31. 制品 smoke：资源/语言键/Demo 图只进入产品包所需位置且无测试代码。
32. 游戏内 acceptance：真机实际点击、生产右键、重开、缩放、缓存清理和外观持久化由用户验收；在此之前只能写“待实机验收”。
