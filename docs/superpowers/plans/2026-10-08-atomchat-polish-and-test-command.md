# R31：按钮动效补齐 + Toast 落点/曲线 + 服务端实验命令

> 触发：0.3.0 后四功能轮（T1-T3 已落地）的真机反馈。用户四条：
> 1. 加载更早胶囊 / 返回最新按钮 没有其他按钮同款的曲线 hover 与缩放动效。
> 2. 操作反馈 Toast 从面板顶部往下弹、且没有曲线淡入淡出；预期在**输入栏上方**弹出并跟随动效语汇。
> 3. 单人档无法触发戳一戳与横幅，需要**实验命令**模拟，避免双开客户端看日志。
> 4. grill 结论：命令做成**服务端**命令、**跟随调试模式**开关、横幅实验**只弹横幅**、
>    Toast 动效**跟随 zoom/slide 与全局动画开关**。

## 一、已核实的证据（先探后写）

| 事实 | 证据 |
|---|---|
| Toast 卡片本体根本没做透明度——只有文字/图标过 `withAlpha`，所以"没有淡入淡出"是真 bug | `shared/.../ui/ActionToast.java:69-77`（只有 `save()+translate`）；对照 `NotificationBanner.drawBanner` 的 `saveLayer(alpha)` `layers/.../notification/NotificationBanner.java:277-291` |
| Toast 目前锚在**面板顶**、往下走 | `platforms/*/.../AtomChatScreen.java:1825-1827`（`toastTop = panel.y()+s(8)+…`），`ActionToast.render` 内 `y += rowH + gap` |
| 加载更早胶囊：hover 是硬切、无 PressScale | `layers/.../page/MessageListView.java:388-393` |
| 返回最新 FAB：hover 硬切、无 PressScale | `platforms/*/.../AtomChatScreen.java:3276-3279`（`hover ? Color.makeARGB(...70,76,90) : …52,58,70`） |
| 全仓无任何客户端命令；服务端命令只有 `/atomchat gui`，权限判据 `singleplayer || OP2` | `platforms/*/.../net/ConfigScreenServer.java:40-61` |
| 戳一戳 S2C 载荷可直接复用做实验（只验客户端反应） | `platforms/*/.../net/PokePayloads.java`（`PokeS2CPayload(from)`）；接收点 `PokeCompanionClient.onPoked` → `AtomChatScreen.onPoked` |
| 横幅**无网络**，纯客户端捕获聊天推出 | `NotificationController.fire`（`enqueue` 在 `client.world != null` 时无条件入队） |
| 横幅与 Toast 只在**非 root 页**绘制（root 页 `drawPhone` 提前 return） | `platforms/*/.../AtomChatScreen.java:1944-1953` vs `1961/1991` |
| `verify_targets.py` 内含 `gen_seams.py --check`；本轮不新增共享顶层类 → 无需重生成 | `tools/verify_targets.py:546-556` |

## 二、锁定决策

| # | 决策 | 取值 |
|---|---|---|
| R31-1 | Toast 落点 | CHAT 页贴 `layout.inputBar.y()`（输入栏上沿）；DETAIL 页贴 `layout.list.bottom()`；ROOT 页维持现状（本就不画横幅/Toast） |
| R31-2 | Toast 堆叠方向 | 最新一条**最靠近锚点**（最下），旧的往上排；整栈向上长 |
| R31-3 | Toast 入场语汇 | 从**下方**升入 + `easeOutBack` 轻微过冲（横幅 drop-in 的镜像）；出场下沉 + 淡出。卡片整块走 `saveLayer(alpha)` |
| R31-4 | 动效总开关 | `Animations.enabled()` 关掉时：位移=0、透明度=1（直接落位，到期直接消失），不做淡入淡出 |
| R31-5 | Toast 裁剪 | 整栈裁剪在锚点之上（`SkiaDraw.clip`），入场时像从输入栏后面滑出，不压住输入栏 |
| R31-6 | 两个按钮的动效 | 统一用既有语汇：hover 走 `UiMotion.approach(..., HOVER_MS)` 渐变 + `PressScale.bounce()`（预算 4px/6% 与所有控件一致），并按 `Animations.enabled()` 总开关 |
| R31-7 | 实验命令落点 | **服务端**注册（`/atomchat test …`），权限 `singleplayer || OP2`，且 `AtomChatConfig.get().debug` 未开时拒绝并说明原因 |
| R31-8 | 戳一戳实验 | 复用 `PokeS2CPayload`，服务端直发执行者 → 走真实解码链路，零新增网络面 |
| R31-9 | 横幅实验 | 新增一个 S2C 载荷 `BannerTestS2CPayload(kind, senderUuid, senderName, text)` → 客户端构造合成 `ChatMessage` 后调 `NotificationController.onMention/onQuote/onWhisper`。**只弹横幅，不进聊天列表** |
| R31-10 | 已知不做 | 横幅实验绕过"捕获+识别"那一段（那是真实游玩路径），只验入队/音效/渲染/跳转；戳一戳反馈需面板打开（接收端 handler 只在面板打开时挂上） |

## 三、改动文件

### A. Toast 落点 + 曲线
- `shared/src/main/java/com/atom/chat/ui/ActionFeedback.java`
  - `Entry` 新增 `alpha(long now, boolean motion)`、`offset(long now, boolean motion)`（纯模型，可单测）。
  - 新增常量 `ENTER_TRAVEL = UiTokens.s(14)`。
- `shared/src/main/java/com/atom/chat/ui/ActionToast.java`
  - `render(...)` 签名改为 `(canvas, feedback, panelX, panelW, clipTop, bottom, now, translator)`；
  - 读 `Animations.enabled()`；整栈裁剪；卡片 `saveLayer(alpha)`；行位置自锚点向上。
- `platforms/*/.../AtomChatScreen.java`：调用点传锚点。

### B. 加载更早胶囊
- `layers/mapping/official/.../page/MessageListView.java` + fabric 孪生
  - 字段 `loadEarlierHover`、`loadEarlierScale`、`loadEarlierPressUntil`；`dtMs` 提升为 `lastDtMs` 字段；
  - `requestLoadEarlier()` 顺带按下压计时；`drawLoadEarlier` 走 hover 渐变 + PressScale 变换。
- `layers/mapping/official/.../render/OffscreenRenderTest.java` + fabric 孪生（若有必要）加一条静息断言。

### C. 返回最新 FAB
- `platforms/*/.../AtomChatScreen.java`
  - 字段 `jumpLatestHover`、`jumpLatestScale`、`jumpLatestHeld`；`drawJumpLatest` 走 hover 渐变 + PressScale；背景灰按 hover 渐变插值；点击处置 held，`onRelease` 清 held。

### D. 实验命令（三平台）
- 每平台新增 `net/TestPayloads.java`（S2C 横幅触发）+ `net/TestCompanionServer.java`（命令）+ `net/TestCompanionClient.java`（接收→`NotificationController.onTestBanner`）。
- 每平台 `NotificationController` 新增 `onTestBanner(kind, uuid, name, text)`。
- 注册点：fabric `AtomChat.java`/`AtomChatClient.java`；neo `AtomChat.java`（复用同一个 `PayloadRegistrar`）+ 命令事件；forge `AtomChat.java` + 独立 channel/命令事件。
- lang：6 份文件同步新增 `atomchat.command.test.*` 与 `atomchat.test.text`。

## 四、TDD 步骤

1. **A**：先写 `ActionFeedbackTest` 的 `offsetRisesFromBelowThenSinks` / `motionOffPinsPositionAndOpacity`（红）→ 实现 → 绿。
2. **B**：`MessageListViewTest` 加 `loadEarlierHoverFadesTowardTarget`（红）→ 实现 → 绿。
3. **C**：FAB 无宿主单测（在 Screen 内），靠 offscreen 冒烟 + 真机；只做编译级与视觉验收。
4. **D**：`TestPayloads` 编解码往返单测（fabric/neo 有 codec 可测；forge 走 encode/decode 静态法）→ 命令注册编译级 → 真机。

## 五、验收标准

- `bash /d/Claude_ds/_atomchat_build_all.sh` 三平台 BUILD SUCCESSFUL + `verify_targets.py` PASS。
- 三平台 `--offline test` 全绿。
- fabric/official 孪生 `diff -b` 只剩映射名差异。
- 真机（用户单人档）：`/atomchat test banner mention|quote|whisper`、`/atomchat test poke` 在调试模式打开时可复现；调试模式关闭时命令被拒。
- Toast 在输入栏上方升入、有淡入淡出；动效总开关关掉后不淡、不位移。
- 加载更早与返回最新两个按钮的 hover 有渐变、按下有回弹。

## 六、遗留（如实披露）

- 横幅实验不覆盖"捕获 + 识别"链路；戳一戳反馈需面板打开。
- 服务端命令读的是**服务端视角**的 `AtomChatConfig`（单人档与客户端同一份文件；独立服务器上是服务器自己那份）。
- ROOT 页仍不画横幅/Toast（本轮不动，T4 搬 HUD 时一并处理）。
