# AtomChat 项目状态

> 交接文档：给下一个会话（或未来的自己）快速定位「现在在哪、下一步做什么」。
> 更新于 **v0.3.0 发版后 + R31 修补轮**（tag `v0.3.0`，未推送）。

## 当前位置

- **版本**：`0.3.0`（`gradle.properties:17`）；tag `v0.3.0` 已打，**未 push 远端**（用户明说不推）。
- **构建**：`bash /d/Claude_ds/_atomchat_build_all.sh` → 三平台 BUILD SUCCESSFUL + 守卫 + `_parity_check.py` PASS。
- **部署**：五实例已铺 0.3.0（见下），md5 已配对（含 R31 修补轮）。
- **许可**：Apache-2.0（根 `LICENSE`、三平台 `src/main/resources/LICENSE`、`fabric.mod.json`、`gradle.properties` 的 `mod_license` 四处一致，版权行 `E33EPUS`）。

| 平台 | 实例目录 | 部署 jar md5 |
|---|---|---|
| Fabric 1.21.1 | `1.21.1-CCB` | `7f412dfb` |
| NeoForge 1.21.1 | `Mechanomania-航空学` | `b9eaeb9b` |
| Forge 1.20.1 | `1.20.1-main` / `Go Fishing` / `元素觉醒1.4.6` | `0bf86f49` |

上表是**0.3.0 之后四功能轮（plan `2026-10-08-atomchat-history-toast-poke-hudnotify.md`）的当前构建**：

- **T1 历史折叠** — `db87113`。内存仍 500 条，列表初始只展示最近 100，顶部「加载更早消息」胶囊每点一次揭 100 并做锚点补偿；公屏与每个私聊各自独立、关面板即忘。
- **T2 操作反馈 Toast** — `112295d`。清缓存/取消壁纸/清历史/复制/存图/头像头图移除 都有浮动反馈（成功=主题 accent、失败=共享危险红），底层 API 改为结果感知（剪贴板返回 bool、`clearDiskCache` 返回删除数）。
- **T3 戳一戳** — `ca82d39`。双击头像=变调音效+面板内 Toast+摇头像；定位改发送者 UUID；**拒自己**；服务端权威限流的远程真戳（不弹横幅，未协商通道则静默退回本地）。
- **T4 通知横幅搬屏幕顶（Skija 同源 HUD）** — **未开始**。需新增三端常驻 Skija HUD owner 并抽 `NotificationQueue`；这条是 0.2.4 事故（saveLayer 泄漏甩飞 UI）的旧路，用户已授权重开但要求逐条核对 `SkiaGraphics.draw` 的 saveBase 护栏。

**R31 修补轮**（plan `2026-10-08-atomchat-polish-and-test-command.md`，用户真机反馈）：

- **Toast 落点与动效** — `ActionFeedback.Entry` 新增 `offset/alpha(now, motion)`，栈锚点从面板顶改到**输入栏上沿**（详情页退回列表底），首条贴锚点、往上排；整行放进 `saveLayer(alpha)` —— 之前只有文字和图标带透明度、卡片本体是实画，所以「没有淡入淡出」是真 bug 不是观感。入场从下方 `easeOutBack` 升入、出场下沉淡出；关掉「装饰性动效」总开关则不淡不位移。
- **加载更早胶囊 / 返回最新 FAB** — 两处 hover 由硬切改为 `UiMotion.approach(HOVER_MS)` 渐变，并接上全站共用的 `PressScale` 回弹（4px/6% 预算），按下压、松开弹；FAB 的弹簧在淡出门之前步进，否则点它即滚到底、下一帧就隐藏，回弹永远看不到。命中判定仍用未缩放坐标（纯绘制变换）。
- **服务端实验命令 `/atomchat test`** — 单人档无法触发横幅与戳一戳，双开又会让 debug 日志互相干扰，所以由服务端扮演对方：`banner mention|quote|whisper [文本]`（走**生产** `fire()` 路径，关掉的横幅照样不弹、音效门与去重都生效）与 `poke [对方名]`（服务端发 `PokeS2CPayload` 的点名技巧，走完整解码链路；戳自己会被拒，所以指向另一名玩家）。权限 = 单机放行 / 否则 OP2，且**必须先开「调试模式」**。横幅**只弹横幅**，不写进 `ChatStore`（跳转无处可落，但不会污染聊天记录与存档）。

版本号保持 `0.3.0` 不动。

**R32 浮动面统一轮**（plan `2026-10-08-atomchat-float-surface-unification.md`，R31 实验命令真机验证的四条反馈）：

- **横幅白底白字**（根因）— `NotificationBanner` 硬编码 `0xFF000000 | cardColor`，默认 cardColor 是纯白 ⇒ 不透明白卡；标题却用面板的 `textPrimaryColor`（白）⇒ 发件人名字白压白。Toast 用的是 `cardCutout()`，两个浮动面各走各的。新增共享族 `UiTokens.floatSurfaceFill()` / `onFloatSurface(int)`（由填充自身亮度派生文字色）/ `onFloatSurfaceSecondary` / `onFloatSurfaceTint` + `UiCards.drawFloatSurface()`（双通道 `drawChromeShadow`、按调用方填充解析发丝线与 hover），横幅与 Toast 统一走它。**FAB（返回最新按钮）保持固定深色，本就自成一族。**
- **Toast 阴影硬边界**（根因）— `saveLayer` 外扩用 `s(8)`=10px，而阴影偏移+高斯尾巴实际走 ~38px ⇒ 被自己的 layer 硬切。光栅实测：卡片下方 alpha 41@6px/26@10/7@20/2@30/1@34/0@38，上方 0@24；落成 `floatSurfaceShadowPad() = s(32)`=40px，两个浮动面共用。
- **横幅尺寸无 token** — 原宽 `min(s(320), max(s(180), panelW-s(24)))` 在标准面板只有内容列的 70%、高 `s(58)`、头像 `s(28)`。改为内容列全宽（`panelW - 2*LIST_PAD_X`，与顶栏/输入栏/底栏同宽）+ 顶栏同高 `HEADER_HEIGHT` + 头像 `ACTION_BUTTON_SIZE`；y 补上 `PANEL_BOTTOM_PAD`，不再压住顶栏 10px。
- **`/atomchat test banner whisper` 弹两个横幅**（根因）— 服务端回执被 `WhisperTextParser` 当成收到的私聊。两层：`KEYWORD_IN` 冒号改为必需、发件人捕获不许含分隔符；**这是真实聊天也存在的 bug**（公屏里任何带 `whisper`/`私聊` 字样的话都会被抢进私聊面板）。另修 anchored 族：无分隔符的行不再被认领（否则关键字会连着正文一起被当消息体）。命令回执文案也去掉了 whisper/私聊 字样。
- **时长** — 横幅 4s→6s；Toast 成功 2s→3s、失败 4s→5s。

三端测试 682/670/670 全绿 + 守卫 PASS + `_parity_check.py` PASS；部署 fabric `7f412dfb`/neo `b9eaeb9b`/forge `0bf86f49`。**独立评审 2 MED 全修**：几何用例在 `PANEL_W=400` 时新旧宽度恰好相等（假绿）→ 改用配置默认 480 且探针移到顶栏下沿（并做了变异验证）；正则收紧会误伤多词/带装饰发件人 → 改为只禁分隔符不禁空白。**另修一个我自己引入的缺陷**：孪生同步脚本把 CRLF 二次转换产出 `
`。

> 前一轮（引用胶囊取色 + 胶囊族软阴影 + `[图片]` 占位绿）已随 commit `d8fc806` 入库并验收。

实例都在 `D:\Myworld\.minecraft\versions\<实例>\mods\`；配置文件在 `<实例>\config\atomchat\atomchat-client.json`。

## 代码结构

```
D:\AtomChat-Main\
  shared/                        平台无关逻辑（config / ui tokens / theme / banner / wallpaper / 字体…）
  layers/mapping/official/       官方映射层的公共源码（与 fabric 树行级等价，仅映射名不同）
  platforms/
    1.21.1-fabric/               Fabric 1.21.1（屏幕类在 net.minecraft.client.gui.screen.AtomChatScreen）
    1.21.1-neoforge/             NeoForge 1.21.1
    1.20.1-forge/                Forge 1.20.1（Java 17；另两平台要 Java 21）
```

**孪生规则**：`layers/mapping/official/` 与 `platforms/1.21.1-fabric` 下的同名文件必须行级等价（只允许映射名差异）；改一处必须同步另一处，否则 `_parity_check.py` 会红。

## 这一版（0.3.0）做完的事

十二个验收轮次累计，详见 `CHANGELOG.md` 的 v0.3.0 段。要点：

- 动效整体换成指数趋近（帧率无关、起步快、落定稳）：页面切换、开屏、关屏；缩放为两阶段串行。
- 开屏动画跟随导航风格（zoom 0.94→1 / slide 横入）；关屏用短时长（τ40ms）+ 提前收尾。
- 个人档案页按 QQ 主页重做：自定义头图（带裁剪）、头像跨压、统计瓦片、信息大卡、身份卡。
- 「整卡」语言统一（关于页第三方 / mod 信息 / 档案信息区 / 身份卡）：发丝分割线 + 整卡缩放 + 逐行高亮。
- 双轴缩放：整机 `uiScale` + 内容 `contentScale`（手机框尺寸不变、内部随其缩放）。
- 悬停统一为强调色染色；卡片边框回发丝感；描边完全按所选颜色绘制（不再自动换灰）。
- 许可转 Apache-2.0。

## 已知限制 / 未做

1. **只支持 Windows x64**（Skia 原生库只打包 Windows）。
2. **未实机验证到「零问题」**：0.3.0 的观感（关屏手感、整卡、开屏 zoom）仍需用户真机验收。
3. **性能只有静态审计**：这一版做了 8 处低风险热路径优化（字体测量 / 阴影滤镜缓存等），但没有 in-game profiler 数据，帧率对比未测。
4. 半径迁移对「跑过 round3 那个未发布中间构建」的开发期配置会二次换算（删 `config/atomchat/atomchat-client.json` 即可，不影响正式发布版本）。
5. 历史 CHANGELOG 段（v0.1.8–v0.2.15）中英条目数不完全对等 —— 按规范历史段只统一骨架、不补译，有意保留。

## 下一会话的入手点

- **待用户真机验收后的反馈轮**：用户明说「下个会话还会继续优化」，预期是新一轮「投诉 → 审计 → grill → 工作流 → 构建部署」循环。
- **动工前先读**：`~/.agents/memory/project_atomchat-mod.md` 的 6e-15 / 6e-16 / 6e-17 三节（最近三轮的根因与教训）+ 本文件。
- **UI 改动纪律**（血泪教训，务必遵守）：
  - UI 动工前先在仓库里找现成同类控件照抄，不要自发明设计（已被推翻多次）。
  - 几何只在 `UiLayout` 算、间距只从 `UiTokens` 取；改 token 前 grep 全部调用点。
  - 设计敏感的页面先出 HTML 草稿（本地 http.server + ZCode IAB 预览）再实现。
  - 同 worktree 并发跑 gradle 会互相破坏，最终必须主代理串行跑一次全量构建。

## 工作流 / 发版注意

- 版本号 / 发版 / commit / push 都是**用户明示才动**；「修完」不等于「发版」。
- **子代理会把评审临时产物（`.review-*.diff`）留在工作区**，`git add -A` 前务必检查；`.review-*` 已加进 `.gitignore`。
- 工作流若因额度停摆（code 1005 / 1310），从 GUI 切模型会生成接力 run，无需手动 Resume；已完成的步骤走缓存。
- 发版必附双语 CHANGELOG（`CHANGELOG.md` 长账 + `RELEASE_NOTES.md` 浓缩），格式见 skill `changelog-format`；改完跑 `python /d/Claude_ds/_parity_check.py CHANGELOG.md`（**要传文件名**）。
