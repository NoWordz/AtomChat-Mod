# Shadow / Rim / Axis 轮：阴影跟随、头像贴合、SLIDE 纯水平（2026-11-02，0.4.0 内，未提交）

## 三个问题的落点

| # | 现象 | 根因（file:line 为改前） | 修法 |
|---|---|---|---|
| 1 | 动画期间卡片背景软阴影不跟着动 | 入场 `saveLayer` 是硬裁切，矩形只给了「slot + 行程」，**没给阴影自身的外扩量**：`SettingsHomePage.java:113`、`ConversationListPage.java:378/429/460`、`SettingsSectionPage.java:1331`、`ProfilePage.java:202`。卡片上浮时阴影顶部被切成一条水平直线；层被跳过的那一帧阴影突然长出来。`ProfilePage` 更把顶边写成 `list.y() - rise`（内容往下推、层底不动），底部被裁掉整个 rise | 新增 `render/CardShadowReach.java`（`PAD = s(12)`，`layerRect(x,y,w,h,lift,drift)`）；五处 layer 全部改用它，四边外扩 PAD 再叠加本帧行程。`SettingsSectionPage` 的 about 卡改为按卡片自身 `cardBounds` 建层（原来覆盖整个视口，贴边卡被切） |
| 2 | 头像和头像框仍有缝隙 | 五站点 `drawRing` 圆心带 `+ UiPixelSnap.halfDevicePx()`（上一轮为清晰度加的半像素相位），但分隔圆是同心画的 → 描边一侧 `R+0.75`、另一侧 `R+1.75`，分隔环外沿在缩进那侧露出面板色细边 | 10 处（两份 × 5）删掉圆心偏移，描边与位图严格同心；`AvatarRing.SEPARATOR_PX` 改为 `= RIM_STROKE_PX`，让分隔环外径恒等于描边外径。取舍（清晰度 vs 同心）写进 `AvatarRing` 类注释：圆形描边本来就带 AA，半像素对角相位换来的锐利代价是环不正 |
| 3 | SLIDE 应该是水平移动而不是斜向上 | `itemRise`（垂直 18px）对所有样式无条件生效，SLIDE 再叠 `itemStyleSlidePx`（水平 16px）→ 斜向右上 | `UiStagger` 新增 `enum Axis { HORIZONTAL, VERTICAL }`；`itemRise`/`itemStyleSlidePx`/`itemStyleZoomScale`/`tabBarRise` 全部带轴、**不留旧签名**（漏改即编译失败）。HORIZONTAL：rise=0、scale=1、tabBarRise=0；VERTICAL：drift=0。`ITEM_STYLE_SLIDE_PX` 16 → **24**（去掉上浮后水平是唯一行程，16 读作「几乎没动」） |

## 关键实现

- **轴只有一个解析点**：子代理第一版在五个类各写了一份私有 `axis()`，与仓库「样式只在一处解析」的不变量冲突（旧缺陷的形状就是「全局 zoom / slide 没区别」）。本轮收敛到 `shared/.../nav/PageNavAxis.java`（`of(style)` / `current()`），并加源码守卫 `PageNavStyleSourceTest.theAxisIsResolvedOnceForEverySurface`：9 个 surface 不得出现 `UiStagger.Axis.VERTICAL : UiStagger.Axis.HORIZONTAL`、不得有私有 `axis()`。
- **横向漂移的裁切**：24px 漂移在入场早期会越过 `list` 右缘（右边距只有 `ROW_CLIP_INSET = s(6.4) = 8px`），圆角会被切掉。五个 surface 在 SLIDE 且 `!UiStagger.itemsSettled(...)` 时把 clip 右边扩 24px，落定后恢复。`UiStagger.itemsSettled` 是为此新增的判据。
- `EmojiPanel` 传 `Axis.VERTICAL`（emoji 网格本来就是上浮入场，不跟页面导航样式走），源码注释写明理由。
- 双源：7 个 layer 文件在 `layers/mapping/official` 与 `platforms/1.21.1-fabric` 同改。

## 验证

三端测试（`--offline -PatomlibMavenRepo=build/audit-20261101/maven`）：

| 目标 | tests | 失败 | skip |
|---|---|---|---|
| Fabric 1.21.1 | **1601** | 0 | 4 |
| Forge 1.20.1 | **1586** | 0 | 4 |
| NeoForge 1.21.1 | **1586** | 0 | 4 |

较上轮基线（1597/1582/1582）+4：`EntranceShadowClipTest` 1 条、`AvatarRingTest` 光栅同心 1 条、`PageTransitionFramesProbe` SLIDE 水平性 1 条、`PageNavStyleSourceTest` 轴收敛 1 条。

**本人独立复测（非子代理自报）**：

- 阴影跟随（真实 `SettingsHomePage`，沿 tile 中心列扫 `shadowTop` → `cardTop` 的 lead）：

  | t | 40ms | 80ms | 120ms | 160ms | 200ms | 240ms | 280ms | 320ms | 400ms |
  |---|---|---|---|---|---|---|---|---|---|
  | lead | 7 | 11 | 11 | 11 | 11 | 11 | 11 | 13 | 13 |

  改前同一测量在 t=80..280 卡在 4..2px 且 `shadowTop` 恒等于 slot 顶边 95；现在是 7→13 单调收敛到 settled 值，极差 2px。

- 头像四向对称（位图边缘外侧三像素；`0:` 是位图内最后一像素）：

  | d | 左 | 右 | 上 | 下 |
  |---|---|---|---|---|
  | 1.0 | `DA9D42` / `7A7C7F` | `7A7C7F` / `2A2C32` | `D69B42` / `7A7C7F` | `7A7C7F` / `2A2D33` |
  | 4/3 | 同上 | 同上 | 同上 | 同上 |
  | 1.5 | 同上 | 同上 | 同上 | 同上 |

  四向完全一致（位图 → 描边混合像素 → 描边芯 → 面板色）。改前一侧是 `A97930`（被压暗的位图色）、另一侧两像素深色描边。

- SLIDE 水平性（`PageTransitionFramesProbe`）：`row0 rise` 在 t=0/40/80/120/160/240/320/420 全部为 `0.00`，`drift` = `24.00 → 17.19 → 11.81 → 7.69 → 4.66 → 1.20 → 0.09 → 0.00`；row0 右缘最大 456 < clip 464，圆角未被切。

- 守卫：`twin_diff_report.py` 未归类块 **10**（与改前相同，全部既存于 `SkiaGraphics.java` / `NotificationHudOverlay.java`）、陈旧台账 0；`bare_scan.py` 已 `--update` 收紧到 **441/441**（本轮净减 1 处裸值）；改动文件 CRLF 扫描零命中。

- 产物门：`verify_jars.py --platform windows-x64 --profile atomlib-consumer` 三端全部 ok；`javap` 级符号断言 —— `UiStagger$Axis{HORIZONTAL,VERTICAL}`、`ITEM_STYLE_SLIDE_PX = 24.0f`、四个方法的新 `(…, Axis)` 签名、`itemsSettled`、`PageNavAxis.of/current`、`CardShadowReach.PAD/layerRect`、`AvatarRing.SEPARATOR_PX/RIM_STROKE_PX`；五个头像站点 class 的反编译输出里 `halfDevicePx` 引用数为 **0**。

## 部署（备份 → 替换 → 读回哈希）

备份根：`D:\AtomChat-Backups\2026-11-02-shadow-rim-axis\20261102-134853\<loader>\`
记录：`docs/verification/2026-11-02-shadow-rim-axis-deployment.json`

| 实例 | 新包 SHA256 | 字节 | 被替换（上一轮） | 备份 SHA256 |
|---|---|---|---|---|
| 1.20.1-main（Forge） | `9747498fe6d0a125…` | 8451401 | `1f95a52abb66d3e2…` | `1f95a52abb66d3e2…` |
| 1.21.1-CCB（Fabric） | `57ee79663744bee5…` | 8479594 | `7b3716fc64d1e584…` | `7b3716fc64d1e584…` |
| Mechanomania-航空学（NeoForge） | `3bcd1ac7fd32ee1e…` | 8470690 | `9a005b72559003fe…` | `9a005b72559003fe…` |

链条完整（被替换哈希 = `2026-11-02-style-deployment.json` 的新包）。三实例 mods 目录各 1 份 atomchat 包，其余文件零变动。独立复核：安装包与备份哈希逐一吻合。

**回滚目标**：`D:\AtomChat-Backups\2026-11-02-shadow-rim-axis\20261102-134853\`（本轮之前），再往前是 `2026-11-02-style-round\20261102-115811\`。

## 未验证 / 归用户

1. **真机未启动**（按约束禁启停游戏）—— 三个结论全部来自 offscreen raster 测量，不是实机截图。
2. 三项待用户目验：卡片阴影是否跟着卡片一起移动；个人档案 / 聊天页 / 横幅的头像是否贴住框；SLIDE 下切页是否纯水平（没有斜向上的感觉）。
3. 未 commit / push / tag。
4. 既存守卫红（本轮未碰、未清理）：`twin_diff_report.py` 的 10 个未归类块；`verify_targets.py` 的 GnetumHudCompat placement。
5. `SettingsSectionPage` 里剩余两处 `halfDevicePx` 是色块与「+」格的描边（非头像框），不在本轮范围。
