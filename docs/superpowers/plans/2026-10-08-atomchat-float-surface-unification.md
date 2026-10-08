# R32：浮动面（横幅 / Toast）统一 + 分类器误判 + 时长

> 触发：R31 实验命令真机验证的四条反馈。
> 1. 横幅还是硬编码白底 → 白色用户名压在白底上看不见；横幅与 Toast 退场太快。
> 2. 横幅尺寸小，和其他组件没有统一的尺寸语言。
> 3. Toast 背景阴影有明显硬边界，而且不知背景色/不透明度跟着谁走。
> 4. `/atomchat test banner whisper` 弹出**两个**横幅，一个头像是自己、一个是别人。

## 一、已核实证据（先探后写）

### 误判根因（#4，HIGH —— 这也是真实聊天的 bug）

`1.21.1-CCB/logs/latest.log` 执行 whisper 测试那一次：

```
[14:47:07] [capture] meta=false line=Sent a simulated whisper banner to your client
[14:47:07] [notify] fire type=WHISPER ...      ← 横幅 #1（我根本没发）
[14:47:07] [test] simulated WHISPER banner from E33EPUS
[14:47:07] [notify] onTestBanner type=WHISPER ...
[14:47:07] [notify] fire type=WHISPER ...      ← 横幅 #2（真正来自我的）
```

- 服务端回执被 `WhisperTextParser` 认成「收到的私聊」：`KEYWORD_IN` 的冒号是**可选**的
  （`shared/.../chat/WhisperTextParser.java:64-66`），句中出现 `whisper` 即命中，前缀被当成发件人
  → 解析不到在线玩家 → 那个陌生头像。
- 「只有一侧是本人」的保护**只写在箭头族** `arrow()`（`:127-135`），**关键词族没有**。
- `私聊` 也在中文关键词表里（`:169`），中文回执同样会被吞。

### 其余三条

| 症状 | 证据 |
|---|---|
| 硬编码白底 | `NotificationBanner.java:291` 写死 `0xFF000000 \| (config.cardColor & 0xFFFFFF)`，默认 `cardColor=0xFFFFFFFF` → 不透明纯白；标题却用 `config.textPrimaryColor`（白）→ 白字白底 |
| Toast 用另一套填充，所以「不知道跟谁走」 | `ActionToast.java` 用 `UiTokens.cardCutout()`（不透明，cardColor 按 cardTint 与面板色预混）——**两个浮动面各走各的** |
| 横幅叠盖顶栏 | 横幅 y = `panelY + HEADER_HEIGHT + s(6)` = `panelY+62.5`，顶栏底边 = `panelY+72.5`（`UiLayout.java:59-63`），漏算 `PANEL_BOTTOM_PAD` → 压顶栏 10px + 压列表顶 10px |
| 尺寸无 token | 横幅宽 `min(s(320), max(s(180), panelW-s(24)))`（600 面板 → 400 = 内容列 570 的 70%）、高 `s(58)`、头像 `s(28)`；而顶栏/输入栏 = 内容列全宽、顶栏高 `s(44)`、消息头像 `s(40)` |
| Toast 阴影硬边 | `ActionToast` 传 `drawCard(..., LAYER_PAD, ...)`，blur 与 `saveLayer` 外扩**都是** `LAYER_PAD = s(8)`；阴影偏移 +blur/2 且扩散远超 10px → 被自己的 layer 硬切。`NotificationBanner.java:280` 同样用 `s(8)` |
| 阴影族不一致 | 两者用单通道 `CHROME_SHADOW`；顶栏/输入栏/底栏走双通道 `SkiaDraw.drawChromeShadow` |

## 二、锁定决策

| # | 决策 |
|---|---|
| R32-1 | 误判**两层修**：命令回执文案去掉 whisper/私聊 字样；`WhisperTextParser` 关键词族**要求冒号 + 发件人不得含空白**（玩家名不可能含空格） |
| R32-2 | 横幅尺寸**并入内容列宽**（`panelW - 2*LIST_PAD_X`，与顶栏/输入栏/底栏同宽）+ **顶栏同高**；位置补上 `PANEL_BOTTOM_PAD` |
| R32-3 | 浮动面填充统一为**新增 token**（不透明、cardColor 按 cardTint 与面板色预混）；文字改为**由填充派生**（填充自身亮度决定深/浅）；**横幅 + Toast** 两个，返回最新按钮保持固定深色（自成一族，故意） |
| R32-4 | 阴影统一改双通道 `drawChromeShadow`；`saveLayer` 外扩按**实测**真实阴影半径（写测试量出来，不猜） |
| R32-5 | 时长：横幅 4s→6s；Toast 成功 2s→3s、失败 4s→5s |

## 三、TDD 步骤

1. **分类器**（先红）：`WhisperTextParserTest` 新增负例——英文/中文命令回执两行必须解析为 `null`；
   保留现有正例全绿。
2. **时长**：`ActionFeedbackTest` 现有断言随新常量走。
3. **阴影半径**：先跑一个探针测出 `drawChromeShadow` 的真实像素外扩，落成 token + 断言
   （layer 边界处与更远处的 alpha 差必须接近 0 = 没有硬切）。
4. **横幅几何**：`OffscreenRenderTest` 加断言——横幅左右与内容列对齐（顶部不进入顶栏区域）。

## 四、验收

- 三平台 BUILD SUCCESSFUL + `--offline test` 全绿 + `verify_targets.py` PASS + `_parity_check.py` PASS。
- 孪生 `diff -b` 只剩映射名。
- 真机：`/atomchat test banner whisper` 只弹**一个**横幅、发件人是自己、名字可读；
  横幅与 Toast 跟主题走、无硬边、6s/3s·5s。
