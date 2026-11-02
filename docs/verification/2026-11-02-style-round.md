# Style 轮：曲线、样式区分、半屏宽度、头像贴合（2026-11-02，0.4.0 内，未提交）

## 四个问题的落点

| # | 现象 | 根因（file:line 为改前） | 修法 |
|---|---|---|---|
| 1 | zoom 动画没曲线、生硬（聊天页输入框区最明显） | 整页进度被我上轮改成 `easeInOutCubic`（`UiStagger.java:143`），起始速度为 0，前 ~50ms 几乎不动然后突加速；另外输入框高度用的是线性 `Animator(t -> t)`、110ms（`ComposerInput.java:111`） | 页面进度改 `easeOutQuad`（起步有速度、末端收），输入框高度改 `Easing::easeOutCubic` |
| 2 | 切半屏模式宽度不变 | `halfScreenWidth` 被宽度滑杆写成了 480（与手机宽度相同），`activePanelWidth()` 直接读它（`AtomChatConfig.java:455`） | 新增持久化标记 `halfScreenWidthCustomized`：只有“半屏模式下真的拖过滑杆”才置 true；否则半屏读数固定 600。加载时对旧配置做一次迁移 |
| 3 | 全局 zoom 和 slide 看起来一样 | 上轮把根页/设置子页/资料详情全改成逐项错峰并**跳过整页包络**（`PageTransitionComposite.java:215` 的 `arrivalStaggersSurfaces`），样式只剩聊天页在用；且幅度小（28px / 0.96）被同层淡入盖住 | 幅度放大到 44px / 0.92，并把样式分量下沉到逐项动画：SLIDE 额外横移 16px、ZOOM 额外从 0.97 缩放，与上浮/淡入共用同一条 item 进度 |
| 4 | 头像和头像框不贴合 | 0.3.0（commit `07e0a05c`，“unify card and border language”，2026-10-07）引入“面板色分隔环(s2) + 环外描边”，位图与描边之间净空 2.25 逻辑 px；本轮描边对齐像素中心后变锐利，缝隙才显形 | 新增 `render/AvatarRing.java`：分隔环 s2→s1，描边中心改为 `R + 0.5s`（跨在头像边缘上）。五处统一：个人档案、聊天消息、会话列表、通知横幅、设置页拉黑行 |

## 关键实现

- **一条时钟，两条曲线**：`pageProgress` = `easeOutQuad(rawT)`，`pageAlpha` = `easeOutQuad(rawT - 26ms)`（`PAGE_ALPHA_DELAY_MS`，约 8% 时钟）。两者都读同一个 rawT，所以位移与透明度永远不会互相错位，但页面先开始动、再变可见——这解决了“位移在看不见时走完”。上一版“位移 easeOut + 透明度 easeInOut 不同曲线”的做法已被帧图否决，理由写进 `UiStagger` 类注释。
- **样式分量下沉**：新增 `itemStyleSlidePx`（16px）与 `itemStyleZoomScale`（0.97→1），由四个列表页各自按 `AtomChatConfig.get().pageNavStyle` 分支应用；`UiStagger` 仍只依赖 `Easing`，不引配置耦合。ZOOM 用现成的 `PanelOpenMotion.applyLayerScale` 绕项中心缩放。
- **半屏宽度**：`activePanelWidth()` 改为 `halfScreenWidthCustomized ? halfScreenWidth : 600`；`migrateHalfScreenWidth(keyPresent)` 在 `load()` 里对旧文件做一次性判定（`halfScreenWidth == panelWidth` 视为未自定义）。三个实例的 live 配置也已按同一规则改好：CCB 由 480→（判定未自定义，读 600），另两台本就是 600 并标记为已自定义。
- **头像环**：`AvatarRing.separatorRadius(R) = R + s(1)`、`rimRadius(R) = R + 0.5*s(1)`，五处站点全部改用它，不再有手写 `PROFILE_AVATAR_RING`。
- **双源**：五个 layer 文件（ProfilePage / ConversationListPage / MessageListView / SettingsSectionPage / NotificationBanner）在 `layers/mapping/official` 与 `platforms/1.21.1-fabric` 逐行同改；`twin_diff_report.py` 汇总仍是“未归类块 10”，全部落在此前就存在的 `SkiaGraphics.java` / `NotificationHudOverlay.java`，本轮触碰的文件零新增残差。

## 验证

- 三端测试（`--offline -PatomlibMavenRepo=build/audit-20261101/maven`）：
  - Fabric **1597**、Forge **1582**、NeoForge **1582**，**0 失败**，各 4 skip（既有）。
  - 新增/重写：`UiStaggerTest`（44px / 0.92 / 16px / 0.97 / 26ms 延迟语义 / alpha 不领先 motion）、`PageTransitionCompositeTest`（mid 帧位移 = 0.25×44）、`PageTransitionFramesProbe`（新增 `styleStaggerFrames`，两种样式各 9 帧）、`HalfScreenConfigTest`（4 个新用例：旧文件等宽→600、不同宽保留、半屏写入打标记、手机写入不串）、`AvatarRingTest`（新，含 10 个站点的源码守卫）、`HalfScreenSettingsTest` 补标记。
- **帧图自检（本人逐帧）**：
  - slide 逐帧左缘 `68→61→56→51→46→42→40`、亮度 `67→103→135→162→189→212→224`——位移和淡入同时可见，无“空走”窗口。
  - zoom 逐帧宽度 `380→384→390→392→396→400`——缩放可见。
  - header 白色条横跨 x=40..219 在全部 7 帧完全不动（顶栏固定未被破坏）。
  - `style_slide_*` 与 `style_zoom_*` 两组列表帧逐帧不同（横移 vs 收缩）。
- 产物门：`verify_jars.py --profile atomlib-consumer` 全 ok；内容级每包 20 项符号断言全中（`UiStagger` 含 4 个新常量与两个 item style 方法、`AvatarRing` 含半径函数、`AtomChatConfig` 含迁移方法、四个页面类含 style 方法）。
- 裸值棘轮：442 处 / 基线 442 ✓（改动净减 12 处裸值后子代理已收紧基线）。

## 部署（备份 → 替换 → 读回哈希）

备份根：`D:\AtomChat-Backups\2026-11-02-style-round\20261102-115811\<loader>\`
记录：`docs/verification/2026-11-02-style-deployment.json`

| 实例 | 新包 SHA256 | 字节数 | 被替换（上一轮） |
|---|---|---|---|
| 1.20.1-main（Forge） | `1f95a52abb66d3e2…` | 8448423 | `7b7c8c7c483f6de9…` |
| 1.21.1-CCB（Fabric） | `7b3716fc64d1e584…` | 8476523 | `39afbb7d43d8fb8b…` |
| Mechanomania-航空学（NeoForge） | `9a005b72559003fe…` | 8467667 | `87a9dd6f6cc7b631…` |

链条完整（被替换哈希 = `2026-11-02-stagger-deployment.json` 的新包）。mods 目录各 1 份 atomchat 包，其余文件零变动。独立复核：三实例安装包与备份哈希逐一吻合。

**回滚目标**：上一轮（stagger 轮）的三个包在 `D:\AtomChat-Backups\2026-11-02-style-round\20261102-115811\`，直接的“本轮之前”原始包在 `D:\AtomChat-Backups\2026-11-02-stagger-round\20261102-104132\`（即 entrance-fix 那版）。

## 配置文件改动披露

为让 #2 立刻生效，三个实例的 `config/atomchat/atomchat-client.json` 各加了一个 `halfScreenWidthCustomized` 键（代码迁移逻辑同样会在下次加载时写它）。原值记录：

| 实例 | panelWidth | halfScreenWidth（改前） | 判定 |
|---|---|---|---|
| 1.21.1-CCB | 480.0 | 480.0 | 未自定义 → 半屏读 600 |
| 1.20.1-main | 507.29355 | 600.0 | 已自定义 → 保持 600 |
| Mechanomania | 480.0 | 600.0 | 已自定义 → 保持 600 |

写盘前未备份这三个 JSON；如需完全回退，删掉 `halfScreenWidthCustomized` 键即可恢复旧行为。

## 未验证 / 归用户

- 实机手感：zoom 是否还生硬、slide 的 44px 是否过头、两种样式在切 tab 时是否真的能区分、半屏是否跳到 600、头像与框是否贴合。
- 未启动/关闭游戏；未 commit/push/tag；版本号仍 0.4.0。
- 已知遗留（本轮未动）：`ConversationListPage` / `SettingsSectionPage` / `EmojiPanel` 里有过时的 "20ms apart""240ms item clock" 注释。
