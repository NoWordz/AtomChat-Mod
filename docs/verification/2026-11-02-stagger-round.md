# Stagger 轮：入场动画分层 + 顶栏固定 + 底栏返回入场（2026-11-02，0.4.0 内，未提交）

## 用户报的四个问题与落点

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | 切换页面时上栏会动，以前不会 | 推入页把 header 也放进了入场包络（`PageTransitionComposite.drawArrivingFrame` 在 layer 内调 `drawArrivingHeader`） | header 移到包络外、最后绘制、不做任何变换（`PageTransitionComposite.java:206-233`） |
| 2 | slide 幅度小、透明度渐变太快看不出 | 12px 太窄；320ms 上 easeOutCubic 在 80ms 就到 0.58 | 位移 12→28px（`UiStagger.PAGE_SLIDE_PX`）；整页进度改 easeInOutCubic，透明度与位移共用这一个值（`UiStagger.java:137-161`） |
| 3 | 要 Dango 的错峰入场 | 上轮为了消除"卡片上跳"把行入场 `markSettled` 掐掉了，只剩页面信封 | 分层：列表页（会话/设置主页/设置子页/资料）取消整页包络，改逐项 18px 上浮 + 淡入 + 25ms 错峰（上限 10 槽，按可见行号）；聊天页/资料详情保留整页包络 |
| 4 | 聊天页返回导航页时底栏没动画 | 底栏只在根页绘制，返回那一刻"凭空出现"；`popNow` 不触发指示器 | `BottomTabBar` 自带入场：淡入 + 上浮 10px，只在 pushed→root 这一条路 arm（`composite.arrivalFromPushedToRoot()`） |

## 关键实现点

- **一条曲线**：页面 alpha 与位移/缩放读同一个 `pageProgress`（easeInOutCubic）。先做成"位移 easeOut + 透明度 easeInOut"的版本被自己的帧图否决——128ms 时位移只剩 6px、亮度只有 12%，等于看不见滑动。记录在 `UiStagger.java:16-27`。
- **一个动作**：`PageTransitionComposite.Host` 新增 `arrivalStaggersSurfaces()`；设置子页与资料详情返回 true，包络整段跳过（`PageTransitionComposite.java:215`），杜绝"页面平移 + 行上浮"双重运动。
- **错峰索引按可见行**：三个列表页各自维护 `staggerIndex`，只对真正画出来的行自增，滚动不会重放已可见行的入场。
- **chip 切换改为只播入场**：删掉 `chipSwitchFromId`/`chipSwitchAtMs`/`CHIP_SWITCH_MS`/`chipTransitionActive`（`SettingsSectionPage.java:383-397`），切换即 `entrance.markEntered()`，与导航到达同一套错峰。用户原话："目前设置页 tab 的动画就是略微上跳和极快渐变"——那正是被删的 110ms 交叉淡入。
- **双源同步**：`layers/mapping/official` 与 `platforms/1.21.1-fabric` 的 ConversationListPage / SettingsSectionPage / SettingsHomePage / ProfilePage 逐行一致（差异仅 1.20.1↔1.21.1 平台映射）。三端 screen 的改动由脚本一次性套用。

## 验证

- 三端测试（`--offline -PatomlibMavenRepo=build/audit-20261101/maven`）：
  - Fabric **1586**、Forge **1571**、NeoForge **1571**，**0 失败**，各 4 skip（既有）。
  - 新增/重写：`UiStaggerTest`（28px、25ms×10、单曲线、10px 底栏、itemAlpha≡itemProgress）、`PageTransitionCompositeTest`（header 矩阵恒等、错峰面跳过包络、mid 帧位移≈半程）、`PageTransitionCompositeWiringSourceTest`（四方法端口、header 在 layer 之后）、`NavInterruptWiringSourceTest`（到达 arm 各页 stagger、底栏返回入场）、`SettingsTransitionTest`（chip 切换重放入场）。
- 帧图自检（`.work/2026-11-02-entrance-frames/`，`PageTransitionFramesProbe`）：slide 逐帧左缘 61→54→46→41→40、亮度 69→120→180→215→224；stagger 逐行落地顺序正确；zoom 同步。均为本人肉眼确认。
- 产物门：`verify_jars.py --profile atomlib-consumer` → 全部 ok。
- 内容级：三包各 15 项符号断言全中（`UiStagger` 含 `PAGE_SLIDE_PX`/`TAB_BAR_RISE_PX`/`itemAlpha`；`PageTransitionComposite` 含 `arrivalStaggersSurfaces`/`arrivalFromPushedToRoot`；四个页面类含 `markEntered`/`drawStaggered`）。
- 孪生守卫：`twin_diff_report.py` 仍报 10 个未归类残差，全部在 `SkiaGraphics.java`（8 项）与 `NotificationHudOverlay.java`（1 项）——**本轮未触碰这些文件**，属既有遗留；本轮新增的 4 个孪生对已归零，并删掉一条因改动而失效的陈旧台账（`924c0a7f5fe1`）。

## 部署（备份 → 替换 → 读回哈希）

备份根：`D:\AtomChat-Backups\2026-11-02-stagger-round\`，两代：
- `20261102-104132\<loader>\` —— **本轮开播前的原始包**（即上一轮 entrance-fix 的产物，哈希与 `2026-11-02-entrance-fix-deployment.json` 的「新包」逐一吻合）。
- `20261102-104910\<loader>\` —— 收尾微调（绘制顺序与注释）后重部署时自动备份的中间包。

记录：`docs/verification/2026-11-02-stagger-deployment.json`（对应 104910 这次）。

| 实例 | 最终安装包 SHA256 | 字节数 | 中间包（104910 备份） | 原始包（104132 备份） |
|---|---|---|---|---|
| 1.20.1-main（Forge） | `7b7c8c7c483f6de9…` | 8445474 | `ddf1a2b13cb01b93…` | `013738df259cc85f…` |
| 1.21.1-CCB（Fabric） | `39afbb7d43d8fb8b…` | 8473475 | `b3e753d243c1ef9b…` | `4cb27ccd6acaf8f4…` |
| Mechanomania-航空学（NeoForge） | `87a9dd6f6cc7b631…` | 8464732 | `59e00375b2620763…` | `92c688e2fbf3be45…` |

mods 目录各只有 1 份 atomchat 包，其余文件零变动（脚本断言的 before/after 集合差）。独立复核：三实例安装包哈希与记录一致，两代备份文件均存在且哈希吻合。要回滚到本轮之前，用 104132 那一代的三个包。

## 收尾微调（在首轮部署之后）
- 把 `BottomTabBar` 与 `SettingsSectionPage` 新的 `saveLayer` 清理顺序改成与文件既有写法一致（先 `close()` 再 `restore()`），不改行为。
- 修 `SettingsSectionPage` 主渲染里一处多余空行导致的孪生残差；补回 fabric 侧 `drawStaggered` 的 javadoc；删掉因本轮改动失效的台账条目。
- 改完重跑三端测试（1586 / 1571 / 1571，0 失败）并重建重部署，最终哈希即上表。

## 未验证 / 归用户

- 实机手感：顶栏是否彻底不动、slide 的 28px 与渐变是否够明显、四类列表的错峰观感、返回时底栏淡入上浮是否自然。
- 未启动/关闭游戏；未 commit/push/tag/发布；版本号仍 0.4.0。
