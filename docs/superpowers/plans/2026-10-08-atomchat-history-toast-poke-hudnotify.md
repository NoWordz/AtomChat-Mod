# AtomChat 0.3.1 四功能实施计划（历史折叠 / 操作反馈 / 戳一戳 / 屏幕顶横幅）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development 或 superpowers:executing-plans，按 task-by-task 执行。步骤用 `- [ ]` 跟踪。

**Goal:** 给 AtomChat 加四件：①消息列表 QQ 式「加载更早」视图折叠；②通用操作反馈 Toast；③戳一戳补反馈并升级为远程真戳；④把通知横幅从面板内搬到屏幕顶（Skija 同源笔）。

**Architecture:** 四条链互相独立，但共享两块新基建——`ActionToast`（浮层卡片 + 结果感知 API，被 ②③④ 复用）与 `NotificationQueue`（渲染无关的通知队列，被 ④ 的 HUD 渲染器与现有面板渲染器共用）。③ 依赖 ② 的 Toast，④ 依赖 ② 的卡片渲染器与 ④ 自己的队列抽取。渲染笔仍是 Skija：HUD 横幅新增一个常驻 SkijaGraphics owner，并补 `saveBase` 护栏外的生命周期管理。

**Tech Stack:** Java 21（fabric/neoforge）/ Java 17（forge 1.20.1）、Skija 0.116.8、JUnit 5、三端 Gradle 串行构建。

## Global Constraints

- **孪生纪律**：`layers/mapping/official/` 与 `platforms/1.21.1-fabric/` 下同名文件必须行级等价，**只允许映射名差异**（`net.minecraft.network.chat.Component/...` vs `net.minecraft.text.Text/...`）。改一处必须同步另一处，`tools/verify_targets.py` 会红。
- **三端同改**：`layers/mapping/official` 被 NeoForge 1.21.1 + Forge 1.20.1 挂载；任何被三端共用的逻辑改官方层一处即可，`platforms/1.20.1-forge` 与 `platforms/1.21.1-neoforge` 只在加载器专属处各写一份。
- **文字/间距/颜色只从 token 取**，几何只在 `UiLayout`/纯函数算，禁止散落 magic number；动画时长一律经 `Animations.ms()`（动效总开关关闭时 snapped 到位，但**功能反馈本体必须仍在**）。
- **所有新 UI 文案进 6 份 lang**（3 平台 × zh_cn/en_us）；lang key 用 `atomchat.` 前缀。
- **纯逻辑必须可离线 JUnit 测**（无 GL、无窗口、无游戏）；渲染层才进 `OffscreenRenderTest`。
- **版本号禁区**：本轮**不动** `gradle.properties` 的 `mod_version`（保持 `0.3.0`）；升版/发版/推送只有用户明示才做。
- 提交信息英文、无 emoji。既有 650/638/638 测试保持全绿。
- 构建：`bash /d/Claude_ds/_atomchat_build_all.sh`（**串行**，并发跑 gradle 会互相破坏）；部署：`bash /d/Claude_ds/_deploy_atomchat.sh`。

---

## 已锁定的产品决策（grill 结论，实施时不得偏离）

| # | 项 | 决策 |
|---|---|---|
| 1 | 历史折叠档位 | **A 视图折叠**：内存仍 500 条，列表初始只展示最近一段，顶部「加载更早」按钮揭开下一批并做锚点补偿。不改持久化、不加协议。 |
| 1 | 触发形态 | **顶部居中胶囊按钮**（非滚动自动加载）。展开到顶后按钮消失。 |
| 1 | 窗口尺寸 | **初始 100 / 每批 100**。 |
| 1 | 作用范围 | **公屏 + 每个私聊各自独立**折叠深度。 |
| 1 | 状态记忆 | **不记忆**（只活在屏幕实例；关面板重开/断线/清历史回默认）。 |
| 2 | 反馈挂载层 | **面板顶浮层**（与现有通知横幅同层，只在面板打开时可见）。 |
| 2 | 颜色与派生 | 成功=**主题 `accentColor`**；失败=把 `SettingsSectionPage` 私藏的破坏红**提取成 `UiTokens` 共享语义 token**，破坏性确认与 Toast 共用。不引入硬编码绿。 |
| 2 | 动效 | **复用横幅运动家族**：进场 220ms `easeOutBack`、退场 150ms、停留成功 2s/失败 4s；同连续事件刷新不堆叠；队列上限 3、失败优先。 |
| 2 | 接线范围 | **点名项 + 同族兄弟**：复制族×4、设置三清（缓存/壁纸/历史）、存图成功与失败、移除族 bool 传播（头像/头图/壁纸/表情失败报错）。需把 API 改成**结果感知**。 |
| 3 | 戳一戳档位 | **B 但不要横幅**：远程真戳（服务端权威限流），对方反馈=音效 + 列表里你的头像摇 + 面板内 Toast；**不弹横幅**。 |
| 3 | 顺带修 | ①自己头像不许戳（现状能戳自己）②行序号定位改**消息 ID/UUID**。 |
| 3 | 音效 | **变调复用** `notification.ogg`（pitch≈1.25），用**独立时间戳门**（不抑制 mention/whisper 音，反之亦然），音量跟 `notifyVolume`。 |
| 4 | HUD 用笔 | **Skija 同源**（需用户显式授权——旧事故那条路；已有 `saveBase` 护栏）：常驻 HUD 面 + 借面板 SkijaGraphics 在屏幕顶画。 |
| 4 | 面板开着时 | 横幅**一直显示在屏幕顶**（面板开时经 `ScreenEvent.Render.Post` 画在面板之上），不回归。 |
| 4 | 可点击域 | **仅面板开着时可点**（点击=开面板并跳到该消息）；面板关着不可点。零新鼠标钩子。 |
| 4 | 抑制规则 | **一律弹不抑制**（不做“可见即抑制”）。 |
| 4 | 动效 | **保留现有 220ms 下落+过冲/150ms 淡出，额外加多条错峰**（同批第 2/3 条各晚 ~60ms 入场、退场逐条错峰）。 |
| 4 | 颜色 | 横幅底色改用 `UiTokens.cardFill()`（随主题极性，替代现在强制的白卡）+ 引入 accent 强调（类型标签/左侧竖条）。 |
| 4 | 面板内横幅 | **保留**（用户一度倾向删除，但“一直显示在屏幕顶”已覆盖其价值且不回归设置页提醒；且删除会再动三端调用点，本轮不做）。 |

---

## 文件地图

**新建（shared，纯逻辑/组件）**
- `shared/src/main/java/com/atom/chat/page/HistoryWindow.java` — 折叠窗口纯模型（批次算术、可见下限、还有多少条）
- `shared/src/main/java/com/atom/chat/ui/ActionFeedback.java` — 反馈队列纯模型（类型/去重键/时长/优先级/刷新）
- `shared/src/main/java/com/atom/chat/ui/ActionToast.java` — Toast 渲染器（Skija；复用 `UiCards.drawCard`）
- `shared/src/main/java/com/atom/chat/notification/NotificationQueue.java` — 渲染无关的通知队列（从 `NotificationBanner` 抽出计时/上限/快照）
- `shared/src/main/java/com/atom/chat/notification/NotificationEvent.java` — 队列条目（类型/发送者UUID/名/预览/跳转 token/入队时刻）
- `shared/src/main/java/com/atom/chat/poke/PokeGate.java` — 本地戳音/提示去重门（独立时间戳）
- 对应测试：`shared/src/test/java/com/atom/chat/page/HistoryWindowTest.java`、`.../ui/ActionFeedbackTest.java`、`.../notification/NotificationQueueTest.java`、`.../poke/PokeGateTest.java`

**新建（platform 层薄适配器，三端各一）**
- `platforms/1.21.1-fabric/.../render/HudSkiaOwner.java`（常驻 SkijaGraphics，懒建/释放）
- `platforms/1.20.1-forge/.../render/HudSkiaOwner.java`
- `platforms/1.21.1-neoforge/.../render/HudSkiaOwner.java`
- `platforms/*/.../notification/HudBannerRenderer.java`（画横幅卡，宿主接 HudSkiaOwner）
- `platforms/*/.../poke/PokePayloads.java` + `PokeCompanionServer.java` + `PokeClient.java`（三端网络）

**修改（孪生双份，逐字对应）**
- `.../page/MessageListView.java` — 顶部「加载更早」按钮绘制/命中；接收 `visibleStart`；锚点补偿入口
- `.../notification/NotificationBanner.java` — 退化为“队列渲染器”，消费 `NotificationQueue`；底色改 `cardFill()`；加 accent 强调；加错峰
- `.../settings/SettingsCatalog.java` + `SettingsSectionPage.java` — 破坏红提取；复制族结果感知；三清接 Toast
- `shared/.../ui/UiTokens.java` — 新增 `dangerColor()`；新增折叠按钮几何 token

**修改（三端各自一份，非孪生）**
- `platforms/*/.../screen/AtomChatScreen.java` — 折叠状态接线、Toast 宿主渲染、戳一戳接线、HUD 横幅屏幕顶渲染 + `ScreenEvent.Render.Post`
- `platforms/*/.../AtomChatClient.java` — HUD 事件注册、HUD owner 生命周期、断线清理
- `platforms/*/.../notification/NotificationController.java` — 改为写队列；戳音播放
- `platforms/*/.../net/*` — 戳 payload 注册
- 6 份 lang、`sounds.json`（若需要新 key，复用则不改）、CLI/README 相关描述

---

## 执行顺序（T1 → T2 → T3 → T4）

理由：T2 的 Toast 与卡片渲染器被 T3/T4 复用；T1 完全独立可先行降帧耗；T4 依赖 T2 渲染器与自身队列抽取。**每条 track 独立可交付、独立可验收**，可按需停在任何一条之后。

---

### Track 1：消息列表「加载更早」折叠

**Files:**
- Create: `shared/src/main/java/com/atom/chat/page/HistoryWindow.java`, `shared/src/test/java/com/atom/chat/page/HistoryWindowTest.java`
- Modify: `layers/mapping/official/src/main/java/com/atom/chat/page/MessageListView.java`（+ fabric 孪生）
- Modify: 三端 `AtomChatScreen.java`

**Interfaces:**
- Produces: `HistoryWindow(int total, int batch)` → `int visibleStart()`, `int visibleCount()`, `int remaining()`, `boolean canLoadEarlier()`, `void loadEarlier()`（`visibleStart -= batch`，夹到 0）, `void reset(int total)`。
- Consumes: `MessageListView.offsetOf(int)`（`:174-196`）算插入高度；`ScrollController.scrollTo(float, boolean)`（`:324-337`）做锚点补偿。

- [ ] **Step 1: 写失败测试** `HistoryWindowTest`

```java
@Test void startsAtNewestBatch() {
    HistoryWindow w = new HistoryWindow(500, 100);
    assertEquals(400, w.visibleStart());
    assertEquals(100, w.visibleCount());
    assertEquals(400, w.remaining());
    assertTrue(w.canLoadEarlier());
}
@Test void eachLoadRevealsOneBatch() {
    HistoryWindow w = new HistoryWindow(500, 100);
    w.loadEarlier();
    assertEquals(300, w.visibleStart());
    assertEquals(200, w.visibleCount());
}
@Test void clampsAtZeroAndStopsOffering() {
    HistoryWindow w = new HistoryWindow(120, 100);
    w.loadEarlier();                       // 20 -> 0
    assertEquals(0, w.visibleStart());
    assertEquals(120, w.visibleCount());
    assertFalse(w.canLoadEarlier());
}
@Test void shortConversationNeverOffers() {
    HistoryWindow w = new HistoryWindow(30, 100);
    assertEquals(0, w.visibleStart());
    assertFalse(w.canLoadEarlier());
}
@Test void resetFollowsNewTotal() {
    HistoryWindow w = new HistoryWindow(500, 100);
    w.loadEarlier();
    w.reset(80);
    assertEquals(0, w.visibleStart());
    assertFalse(w.canLoadEarlier());
}
```

- [ ] **Step 2: 跑测试确认红** `cd D:/AtomChat-Main/platforms/1.21.1-fabric && ./gradlew test --tests '*HistoryWindowTest*'`（预期：类不存在，编译失败。注意：`shared/src/test` 是挂进各平台 test 源集的，**没有 `:shared:test` 这个任务**，测试按平台跑）

- [ ] **Step 3: 实现 `HistoryWindow`**（纯算术，无 GUI；`visibleCount()=total-visibleStart`；`loadEarlier` 用 `Math.max(0, visibleStart-batch)`）

- [ ] **Step 4: 跑测试确认绿**

- [ ] **Step 5: 接线 `MessageListView`（official 层，`render`/`draw` 入口）**
  - 新增字段 `int visibleStart`，`draw` 时以 `visibleStart` 作为遍历起点（`(total)` 只遍历 `visibleStart..total-1`，把每帧 O(n) 降到 O(窗口)）。
  - 顶部绘制「加载更早」胶囊：仅当 `visibleStart > 0`；文案 `atomchat.chat.loadEarlier`，带剩余数 `remaining()`（`atomchat.chat.loadEarlierCount`，带 `%s`）。
  - 命中区记录进既有 hit 结构（与 bubble/avatar 同法，复用 `MessageHit` 或并列新 hit 记录）；命中时返回“请求加载”信号给宿主。
  - 几何全走新 token（高度/内边距/圆角），进 `UiTokens`。

- [ ] **Step 6: 生 fabric 孪生**（同码换映射名）；`diff -b` 确认仅映射名差异

- [ ] **Step 7: 接线三端 `AtomChatScreen`**
  - 公屏一个 `HistoryWindow`；私聊每 partner 一个（与 `privateScrolls` 同构的 map，键 `PlayerRef.key()`）。
  - 每帧/每消息集变化时 `reset(total)`（total 变化才重置）。
  - 命中「加载更早」→ 先记 `offsetOf(visibleStart)` 的插入前高度 → `loadEarlier()` → 下一帧 `scroll.scrollTo(oldScrollY + insertedHeight, false)`。
  - 断线/清历史/切会话时释放对应窗口。

- [ ] **Step 8: 离屏/几何验证** 在 `OffscreenRenderTest` 加一例：500 条 fixture 下，`visibleStart=400` 时只画 ~100 条 + 一个按钮；断言既有 7 行 fixture 行为不变。

- [ ] **Step 9: 三端构建 + 全测试** `bash /d/Claude_ds/_atomchat_build_all.sh`（预期 BUILD SUCCESSFUL + guard PASS）

- [ ] **Step 10: Commit** `feat(chat): fold long histories behind a load-earlier button`

---

### Track 2：通用操作反馈 Toast（浮层卡片 + 结果感知 API）

**Files:**
- Create: `shared/src/main/java/com/atom/chat/ui/ActionFeedback.java` + `ActionToast.java` + tests
- Modify: `shared/src/main/java/com/atom/chat/ui/UiTokens.java`（`dangerColor()`、toast 几何 token）
- Modify: `.../settings/SettingsSectionPage.java`（破坏红改用共享 token；三清接 Toast）
- Modify: `.../settings/SettingsCatalog.java`（复制族结果感知）
- Modify: 三端 `AtomChatScreen.java`（Toast 宿主渲染；剪贴板/清缓存/清历史/存图 结果感知）

**Interfaces:**
- Produces: `ActionFeedback.show(String key, Outcome outcome, String... args)`；`Outcome{SUCCESS, ERROR}`；`ActionFeedback.snapshot(now)`；`ActionToast.render(Canvas, panelX, panelY, panelW, snapshot, now)`。
- Produces: `Clipboard.copy(String) : boolean`（三端各自实现，返回是否成功）；`ImageLoader.clearCache() : int`（返回删除文件数）；`ChatHistory.clearAsync(Consumer<Boolean>)`。
- Consumes: `UiCards.drawCard(canvas, x,y,w,h, radius, hover, shadowTier, blur, fill)`（`UiCards.java:34-70`）；`UiTokens.cardFill()`/`CHROME_SHADOW`/`cardRadius()`；`Easing.easeOutBack`；`Animations.ms()`。

- [ ] **Step 1: 写失败测试** `ActionFeedbackTest`

```java
@Test void successHoldsTwoSecondsThenExpires() {
    ActionFeedback f = new ActionFeedback();
    f.show("k", SUCCESS, 1000L);
    assertTrue(f.snapshot(1500L).hasVisible());     // within hold
    assertFalse(f.snapshot(3500L).hasVisible());    // past 2s + exit
}
@Test void errorHoldsFourSeconds() {
    ActionFeedback f = new ActionFeedback();
    f.show("k", ERROR, 1000L);
    assertTrue(f.snapshot(4500L).hasVisible());
    assertFalse(f.snapshot(5600L).hasVisible());
}
@Test void identicalKeyRefreshesNotStacks() {
    ActionFeedback f = new ActionFeedback();
    f.show("cache", SUCCESS, 1000L);
    f.show("cache", SUCCESS, 1500L);
    assertEquals(1, f.snapshot(1600L).size());
}
@Test void errorsTakePriorityInQueue() {
    ActionFeedback f = new ActionFeedback();
    for (int i = 0; i < 5; i++) f.show("s" + i, SUCCESS, 1000L + i);
    f.show("boom", ERROR, 1010L);
    assertEquals("boom", f.snapshot(1020L).entries().get(0).key());
}
@Test void queueCapsAtThree() { /* show 5 -> snapshot size <= 3 */ }
```

- [ ] **Step 2: 跑测试确认红**

- [ ] **Step 3: 实现 `ActionFeedback`**（纯模型：键去重刷新、优先级插入、上限 3、alpha 由 age 派生，SUCCESS 2s / ERROR 4s + 150ms 退场）

- [ ] **Step 4: 跑测试确认绿**

- [ ] **Step 5: `UiTokens` 加 `dangerColor()`**（从 `SettingsSectionPage.java:127-132` 提出；破坏性确认与 Toast 共用）

- [ ] **Step 6: 实现 `ActionToast`**（画不透明卡片：`UiCards.drawCard` + `cardFill()` + `CHROME_SHADOW` + `cardRadius()`；成功=accent 勾图标，失败=danger 图标；主文字 `textPrimaryColor`、副文字 `textSecondaryColor`；进场 220ms `easeOutBack` + 位移 `s(10)`，退场 150ms；**无头像/预览/回复按钮**）

- [ ] **Step 7: 结果感知 API（三端逐一，先 fabric）**
  - 剪贴板 helper 改返回 `boolean`（现在 `AtomChatScreen.java:3536-3543` 只记日志）。
  - `ImageLoader.clearCache()` 改返回删除数（现在 `ImageLoader.java:483-503` 返回 void、失败仅日志）。
  - `ChatHistory.clearAsync(Consumer<Boolean>)`（现在 `ChatHistory.java:121-143` 磁盘删除异步、失败仅日志）。
  - 同步改另两端同名实现。

- [ ] **Step 8: 接线调用点（点名项 + 同族兄弟）**
  - 复制族×4：右键复制（`AtomChatScreen.java:4812-4842`）、Ctrl+C 选区（`:5107-5117`）、取色器 Ctrl+C（`:4282-4294`）、档案复制改结果感知（`ProfilePage.java:351-358` + 屏幕回调改返回 bool，`:189-191`）。
  - 设置三清：清缓存（`:289-292`）、清壁纸（`:255-262`）、清历史（`:284-288`）→ 按结果弹 Toast（成功 2s / 失败 4s）。
  - 存图：成功弹 Toast（现在只日志，`:3670-3676`），失败保留现有 composer hint 或改 Toast（二选一，实施时取 Toast 以统一）。
  - 移除族 bool 传播：头像/头图/壁纸/表情移除失败 → 错误 Toast（现在 bool 全被丢弃，见审计清单）。

- [ ] **Step 9: 三端+孪生同步，构建 + 全测试 + guard**

- [ ] **Step 10: Commit** `feat(ui): add an action feedback toast with result-aware actions`

---

### Track 3：戳一戳补反馈 + 远程真戳（不弹横幅）

**Files:**
- Create: `shared/src/main/java/com/atom/chat/poke/PokeGate.java` + test
- Create: 三端 `.../poke/PokePayloads.java`、`PokeCompanionServer.java`、`PokeClient.java`
- Modify: 三端 `AtomChatScreen.java`（双击分支：拒自己、按消息 ID/UUID 定位、弹 Toast、播音）；
- Modify: 三端 `NotificationController.java`（戳音播放，独立时间戳）；三端 `net` 注册。
- Modify: `MessageListView.java`（+孪生）：`poke()` 改为按消息 ID 定位；接收方摇对应行（行不可见则不摇）。

**Interfaces:**
- Produces: `PokeGate(long intervalMs)` → `boolean allow(long now)`；`PokeClient.send(UUID target)`；`PokePayloads`（C2S `PokeC2S(uuid target)` / S2C `PokeS2C(uuid from, String fromName)`）；`PokeCompanionServer`（per-sender 时间戳限流 + 断线清理）。
- Consumes: `AvatarPayloads` 的可选通道协商模式（Fabric `PayloadTypeRegistry`、NeoForge `PayloadRegistrar`、Forge `SimpleChannel` + protocol version）；`MediaCompanionServer` 的 per-player 时间戳限流样板；`MessageListView.poke`；Track 2 的 `ActionFeedback`。

- [ ] **Step 1: 写失败测试** `PokeGateTest`（首击必过；`intervalMs` 内拒绝、边界恰好放行；与 `NotificationSoundGate` 语义一致但**独立实例**）

- [ ] **Step 2: 跑测试确认红 → Step 3 实现 `PokeGate` → Step 4 确认绿**

- [ ] **Step 5: 本地反馈（三端 `AtomChatScreen`）**
  - 双击分支先 `if (msg.isOwn()) return;`（修自己戳自己）。
  - 定位改 `msg` 的消息 ID（`ChatMessage.sameAs`，`:237-244`），落 `MessageListView.poke(messageId, now)`；`poke` 内按 ID 匹配。
  - 命中后：摇头像（现有）+ `PokeGate.allow(now)` 内播戳音（`notification.ogg` pitch 1.25、音量 `notifyVolume`）+ `ActionFeedback.show("poke", SUCCESS, senderName)`。
  - `装饰动效` 关掉时：Toast + 音效仍在（不随动效消失）。

- [ ] **Step 6: 网络（三端各一份）**
  - payload 定义 + 注册（透传 `NotificationController` 的注册/协商模式），服务端 per-sender 限流（默认 3s）+ 断线清理；客户端收到 S2C：按 UUID 找可见行摇 + Toast「XX 戳了你」+ 音效（**不弹横幅**），行不可见则只 Toast+音效。

- [ ] **Step 7: 降级** 未协商通道（原版/无 companion 服务器）→ 保持现状本地反馈，静默不报错。

- [ ] **Step 8: 三端构建 + 全测试 + guard**

- [ ] **Step 9: Commit** `feat(poke): give the poke audible and visible feedback and make it remote`

---

### Track 4：通知横幅搬到屏幕顶（Skija 同源，含颜色/动效修）

**Files:**
- Create: `shared/.../notification/NotificationQueue.java`、`NotificationEvent.java` + tests
- Create: 三端 `.../render/HudSkiaOwner.java`、`.../notification/HudBannerRenderer.java`
- Modify: `.../notification/NotificationBanner.java`（+孪生）：退化为队列渲染器；底色改 `cardFill()`；accent 强调；错峰
- Modify: 三端 `AtomChatClient.java`：HUD 事件注册 + owner 生命周期 + 断线清理队列
- Modify: 三端 `AtomChatScreen.java`：`ScreenEvent.Render.Post` 屏幕顶渲染 + 点击命中（仅面板开着时）

**Interfaces:**
- Produces: `NotificationQueue.enqueue(NotificationEvent, long now)`、`snapshot(now)`、`clear()`；`NotificationEvent.type/senderUuid/senderName/preview/messageId/born`。
- Produces: `HudSkiaOwner.draw(density, renderer)`（懒建常驻 surface；`release()`）；`HudBannerRenderer.render(Canvas, queueSnapshot, now, width)`。
- Consumes: `NotificationController`（三端：改为写队列 + 播音）；`SkiaGraphics` 的 `saveBase`/`restoreToCount` 护栏模式；`UiCards.drawCard` + `UiTokens.cardFill()`/`accent()`/`hairline()`/`CHROME_SHADOW`；`PlayerAvatar.face`（Skija Image，HUD 同源可直接画）。

- [ ] **Step 1: 写失败测试** `NotificationQueueTest`（入队计时、上限 3、过期剔除、`clear()` 断开清理、快照不可变）

- [ ] **Step 2: 跑测试确认红 → Step 3 实现 `NotificationQueue`/`NotificationEvent` → Step 4 确认绿**

- [ ] **Step 5: `NotificationBanner` 退化为渲染器**（把计时/上限/队列搬走，只留绘制；现有 `renderInPanel` 语义与点击行为不变）；孪生同步。

- [ ] **Step 6: 修颜色与强调** 底色 `0xFF000000|cardColor` → `UiTokens.cardFill()`（随主题极性，修默认主题白卡）；类型标签/左侧竖条用 `accent()`；孪生同步。

- [ ] **Step 7: 加错峰** 同批第 2/3 条入场各晚 ~60ms、退场逐条错峰（时长全经 `Animations.ms()`；动效关则 snapped）。

- [ ] **Step 8: 三端 HUD 渲染**
  - `HudSkiaOwner`：常驻懒建、断线/退出 `release()`；每帧 `GlStateUtil.save/restore` + `saveBase` 护栏照 `SkiaGraphics.draw` 抄（**这是被授权重开的旧路，必须逐条核对护栏**）。
  - 三端薄适配器：Fabric `HudRenderCallback.EVENT`；NeoForge `RenderGuiEvent.Post`（`NeoForge.EVENT_BUS`）；Forge `RenderGuiEvent.Post`（`MinecraftForge.EVENT_BUS`）。绘制条件：世界里 + 有活动通知。
  - 面板开着时：三端 `ScreenEvent.Render.Post` 在面板之上画同一份卡；点击命中仅在此时接线（点击=开面板并跳到该消息，复用现有 jump+高亮）。
  - 面板关着：只画不可点（零新鼠标钩子）。

- [ ] **Step 9: 生命周期** 断线清队列（现无 `clear()`，`AtomChatClient` 断开处补）。

- [ ] **Step 10: 文案与描述** `lang` 6 份：新增/修订通知描述（现有 UI 文案明说“面板打开才显示”，要改）；`CHANGELOG`/`docs/STATUS.md` 更新。

- [ ] **Step 11: 三端构建 + 全测试 + guard + 离屏冒烟**（`OffscreenRenderTest` 现有 Skia 配平回归 `:419-430` 必须仍绿）

- [ ] **Step 12: Commit** `feat(notify): move banners to the screen top and theme their colour`

---

## 验收（每条 track 独立）

| Track | 自动 | 真机 |
|---|---|---|
| 1 | `HistoryWindowTest`；`OffscreenRenderTest` 500 条窗口；三端构建 | 500 条会话：初始只见最近 100，点按钮逐批揭开且视口不跳；私聊各自独立 |
| 2 | `ActionFeedbackTest`；结果感知 API 单测 | 清缓存/取消壁纸/复制文本/存图 有浮动反馈且跟主题；失败能看见 |
| 3 | `PokeGateTest`；payload codec 测试 | 双击对方头像=摇+音+Toast；戳自己无效；对方收到音+Toast+（可见时）摇；聊天无横幅 |
| 4 | `NotificationQueueTest`；Skia 配平回归 | 关面板时屏幕顶弹横幅（含自定义头像）、颜色跟主题、多条错峰；面板开时也弹且可点击跳转 |

## Self-Review 记录

- **Spec 覆盖**：4 条需求 × 各自决策表全部落到 track 步骤；审计发现的 F 类缺陷（自己戳自己、行序号定位、颜色不跟主题、bool 被丢弃、剪贴板失败静默）均已排入。
- **占位扫描**：无 TBD/“适当处理”；每条实现步骤都指明文件与既有行号锚点。
- **类型一致性**：`HistoryWindow`/`ActionFeedback`/`NotificationQueue`/`PokeGate` 的构造与方法签名在“Interfaces”块内前后一致。
- **已知开口（实施时若卡住需回问用户）**：①HUD Skija owner 的释放时机（退出世界 vs 退出游戏）；②存图失败走 Toast 还是保留 composer hint（本计划取 Toast）；③戳音 pitch 1.25 的实数需真机听感微调。
