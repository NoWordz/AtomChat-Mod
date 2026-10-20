# AtomChat 当前状态

> 截至 2026-10-20。这里只维护当前快照；历史状态按日期归档，提交事实用 Git 查询，不逐轮追加日记。

## 项目

- 源码仓库：D:\AtomChat-Main，单仓多目标：Fabric 1.21.1、NeoForge 1.21.1、Forge 1.20.1。
- 现役分支：main；origin：NoWordz/AtomChat-Mod；源码版本：0.3.1，已按用户指定部署至 Windows 1.21.1-CCB（Fabric），用户验收通过并授权本地提交，未推送或发布。

## 0.3.1 气泡样式与点阵材质（已验收）

- 已部署：D:/Myworld/.minecraft/versions/1.21.1-CCB/mods/atomchat-Fabric-1.21.1-0.3.1.jar；SHA-256 180032a5075c8b31ffce3306db8253f089723b334505e46a689b562f4a42fb89。旧包备份 D:\AtomChat-Backups\2026-10-20-ccb-selector-accent-da71fd44\atomchat-Fabric-1.21.1-0.3.1.jar；其他实例未改动，未启动游戏。

- 外观 → 主题 → 气泡样式：经典圆角（默认）、大圆角、单角尖角，本地统一作用于所有玩家，尖角朝头像侧上方；文本、图片、下载中图片和禁用图片占位均已接线。
- 优先借鉴本机 Tuui `CanvasExtensionKt` 的四角 `RRect` 和图片同形裁切方式，复用现有 Skija 管线；独立绘制 helper 负责轮廓，测量、命中、字号、昵称区高度和消息间距保持原值。
- 三款气泡直接复用主题预设的缩略卡绘制及滚轮/拖动机制，各自保留偏移，窄屏横向滚动，气泡组位于圆角上方。选中底色使用原强调色，hover只改透明度，导航与设置Tab内层圆角按外栏内缩。点阵常开，无开关，不再写入配置/预设，旧字段忽略。输入栏/导航栏向上、顶栏向下的贴边点阵跟随真实圆角，叠加淡柔光；关于页 Logo 背后放射、设置和私聊可折叠标题后静态向右渐隐。局部高光/内阴影仅用于三种栏体，装饰画在内容之前，不占布局。
- 点阵规则参考本机 chatcore / MintUI，对应实现使用 ModernUI；它与 Tuui 的 Skija 框架是两个不同来源，没有引入额外渲染依赖。
- 核心光栅、真实消息列表样式/图片状态/命中滚动、三卡预览/窄宽选择/折叠命中、预设持久化与旧字段兼容测试已过。前轮两项及本轮跨条滚轮抢拖动归属 P2 均已红绿回归并通过独立复查；最新三端 1319/1331/1319 项测试均无失败/错误，各 2 项原有跳过。
- 变异验证：故意将实际列表四处 `CORNER` 接线改成 `ROUND`，三项真实文本/图片状态测试全部失败；随后生产源码逐字节恢复。
- 用户已确认 CCB 实机观感验收通过；GPU 量化性能及多人专项未覆盖。最终命令、三端统计及六包哈希见 [续轮验证](verification/2026-10-20-selector-accent.md)。

## 0.3.0 正式版增量

- **个人外观预设（含壁纸）**：外观→主题条在内置主题后追加自动编号预设，末尾＋保存当前外观；点击应用，悬停整张卡片时右上角显示×，点击直接删除，不再有命名、覆盖或重命名。存储改用 `atomchat/appearance-presets` 与稳定 UUID，保存十二色、尺寸/缩放、透明度、卡片、圆角、模糊、外框、动画、切页及独立壁纸副本；新预设始终追加到末尾，图片头部/像素预算与配置失败回滚在保存、扫描和应用时均生效。
- **发送关闭与会话草稿**：聊天→消息新增“发送后关闭聊天框”（默认关）和“保留未发送草稿”（默认开）。公聊、私聊、命令成功发送后按开关走原关闭动画；空白、被拦截私聊和仅插入表情不关。草稿在本次进服会话内跨聊天框恢复，公聊与每个私聊分开，发送成功清空，退服清零，不跨重启落盘。
- **界面容器圆角与设置整卡**：发送按钮及 hover/press 层、按钮、输入框、弹窗、表情/短语面板、通知操作、未读 badge、历史/引用胶囊、滚动控件等容器统一跟随实时圆角；开关与滑条把手（含滑条强调描边）也跟随圆角，气泡、头像、色彩圆点和表情图案保持原形状。全部设置 tab 按确认表重新分组，默认展开，每组一张整卡并以细线分隔。详情页滚动内容在最后一张卡后保留与左右一致的 hover 安全区；设置、资料和设置首页均在滚动到底时保留尾部呼吸距离。
- **趣味特效**：原“实验性”文案改为“趣味特效”；新增“晚安 / good night”星星上浮效果，沿用总开关和现有三秒单次播放、忙时丢弃规则。
- **窄窗口与内容缩放适配**：拥挤的设置标题/说明换行，控件另占区域，滑条、数字输入和颜色网格同步测量与命中；顶栏为返回/动作预留文字区域，必要时隐藏时钟；输入按钮空间不足时换行。保留现有字体层级与美术，不保证极小窗口同时最大缩放仍能容纳完整聊天界面。
- **细外框**：三端外框由s(3)改为s(2)，背景内缩与模糊捕获共用宽度；内部卡片描边不改。
- **头像样式**：客户端本地配置，默认 AtomChat 原生；设置位置为“聊天 > 消息”，E33Chat 样式采用方形皮肤脸部与外扩帽层。10 月 16 日修正普通头像帽层比例：按 E33Chat 默认脸 20 / 帽 22 等比缩放，并同步列表边缘留白；Profile 的独立比例保留。E33Chat 样式显示玩家皮肤，已有自定义头像数据不删除。
- **档案头像交互**：10月16日恢复AtomChat原生主页头像的高亮、弹跳、编辑徽章与管理菜单；E33Chat及他人资料页保持只读，切换样式/玩家清理旧菜单。已有头像数据保留。
- **私聊列表折叠**：标题分割线可展开/收起，沿用设置页动画；面板关闭时状态重置为展开。
- **关于页作者磁贴**：并排展示 E33EPUS 与 NoWordz 的头像和名字。

## 前轮交付（消息反应 + 静态审计修正 + Linux x64 分包）

- **消息反应（内容键传输）**：右键气泡新增「反应」，气泡下方显示 emoji 与人数，点击切换、自己的项高亮、悬停显示参与者。身份 = `SHA-256("content|author|body")`，两端各自从可见文本算出，**不依赖服务端仲裁**；服务端只做白名单、限流（每连接 8 次/秒）与容量上限（每人每条 8 种、每条 20 种、单项 256 人）。私聊靠「没有该行消息的客户端渲染不出 chip」天然隔离——与引用气泡同一机制。
- **反应贴纸动画**：胶囊背景 easeOutBack + 表情符号延迟追入（160ms）；出场保留布局槽位到淡完（幽灵占位）；计数变化只做数字脉冲；悬停名单单实例跟随渐隐渐显；仅「新鲜到达」的 chip 播入场。
- **静态审计修正**：菜单底色↔阴影顺序回归修复；图片展示/保存字节上限 16 MiB、解码前像素上限 3200 万、失败 URL 缓存上限 512；头像三端复用预检与 256px 缩图、服务端用 PNG IHDR 无 native 检查；捕获层按原生 FilterMask 尊重过滤文本并保留私聊身份。
- **Linux x64 分包**：每个加载器分别构建 Windows x64 与 Linux x64 JAR；Windows 原有文件名/目录不变，Linux 文件名带 `-linux-x64` 并输出到 `build/linux-x64/libs/`，每个 JAR 只含对应 Skija native。
- **Linux 字体回退修复（0.3.0-Preview 续）**：Linux 上面板文字正常但表情/部分颜文字是方框。根因=字符回退字体清单全是 Windows 家族（`FALLBACK_FAMILIES` 无 Linux 名、emoji 分支写死 `"Segoe UI Emoji"`），而精简 Linux（含 WSL 默认镜像）只装 DejaVu/Ubuntu，补齐不了 emoji、半角假名、泰文、谚文与部分符号。修法：回退清单加 Linux/跨平台家族（Noto/WenQuanYi/DejaVu/Source Han/FreeSans/Ubuntu，Windows 名仍排最前以保 Windows 行为不变）；emoji 分支改为跨平台候选列表 `EMOJI_FAMILIES`（Segoe 仍第一，加入 Noto Color Emoji 等）并按码位挑选；无 emoji 字体时打一条一次性日志。WSL 实测：装 `fonts-noto-cjk fonts-noto-color-emoji` 前表情 122 码位 88 个方框、颜文字 ~10 个符号无字，装后分别降到 0 与 2（剩泰文 `ฅ ๑` 系统无字体覆盖）。排查文档两版同步补「Linux 缺字体」一节。

设计变更与逐项证据见 [2026-10-12 reactions 验证记录](verification/2026-10-12-reactions.md)；原方案中被替换的指纹/回执设计已在 [方案文档](superpowers/plans/2026-10-12-message-reactions.md) 顶部标注。

## 验证

### 设置整理、预设重写与聊天输入（2026-10-19）

- 旧预设存储及命名/覆盖/重命名界面撤除，新 `AppearancePresetStore` 与 `DraftSessionStore` 先用失败测试建立，再实现通过；真实 Skija 像素回归覆盖发送按钮圆角 0/默认、hover、press 和 save/restore。
- 独立强模型只读审查发现壁纸回滚、旧屏幕草稿回写、图片预算、追加顺序、合卡色点、资料页按钮圆角、删除键可发现性和英文词距等问题；负责人逐项修正并补测试。最终三端完整测试：NeoForge 1290、Fabric 1302、Forge 1290，均 0 失败 0 错误、各 2 项原有跳过；三端 Windows 与 Linux 六包重新构建通过，对应 native、0.3.0 版本、中英资源、新类/新方法校验通过，同加载器两 OS 的应用 class/assets 内容一致。
- seams 123/71、verify_targets、孪生 68 对未归类 0、日志出口 262 违规 0、bare_scan 477/505、`git diff --check` 均通过；664 个构建输入聚合哈希 `fe6c1c4fdc96431f7af16ae7ed06a0d958105edd3941dbb11b446ae99a27dac1` 在最终三端构建前后保持不变。详见证 [本轮验证记录](verification/2026-10-19-settings-and-chat-input.md)。
- 已备份并部署 Windows「Mechanomania-航空学」NeoForge（`b5d361eb…`）与 WSL Prism「1.21.1」Fabric（`882e018a…`）；部署哈希与候选包一致，两处 mods 目录各只有一个 AtomChat JAR。未启动游戏，实机观感与行为待用户验收。
- 实现已提交并推送（`37589c7`），已部署 Windows 日常 NeoForge 与 WSL Prism Fabric；未启动游戏、未发布正式版。GPU 实帧、输入焦点、关闭动画、真实网络发送和进退服边界仍待实机验收。

### 圆角尾巴与晚安星光（2026-10-19 后续）

- 设置详情页底部改为与左右相同的 23 布局单位外缘（12 外边距 + 6.4 卡内安全区）；合卡 hover 的位移预算同时按宽、高收束，滚动到底和按压释放不再裁住卡片下沿。设置首页、资料页同步补尾部安全区；消息列表保留“最新消息贴底”的独立语义。
- 开关把手、音量/滑条把手及强调描边跟随实时圆角；圆角 0 时为方角，默认仍为圆形。菜单行 hover 与输入选区底色补入实时圆角。
- “晚安 / Good night”在可见公聊或私聊的新消息中触发最多 12 颗两侧上浮星星；不排队、不补播、不新增声音或网络包。旧三种特效仍为 28 颗，默认强度未改。
- 三端完整测试：NeoForge 1294、Fabric 1306、Forge 1294，均 0 失败 0 错误、各 2 项原有跳过；静态守卫通过。六包重建并校验 Windows/Linux 内容一致，已备份并部署 Windows「Mechanomania-航空学」NeoForge 与 WSL Prism「1.21.1」Fabric。详见[验证记录](verification/2026-10-19-corner-spacing-and-goodnight.md)。

### 主题预设与窄窗口适配（2026-10-18）

- 先复现缺壁纸/损坏manifest/Unicode粘贴、长标题和输入按钮重叠等失败用例，再修正。新增18项测试；三端完整测试Forge1278、Fabric1290、NeoForge1278，均0失败0错误、各2项原有跳过。CPU离屏多帧设置页与画布状态检查通过，不替代游戏内验收。
- 六份Windows/Linux产物的native、资源、版本、预设类与对应OS应用内容一致性校验通过；bare_scan492/505、seams123/71、孪生68对、目标及日志守卫通过。Fabric初次编译暴露候选代码鼠标字段未转映射，修正为mouse后完整重建通过。
- 已备份部署Windows「Mechanomania-航空学」NeoForge与WSL Prism「1.21.1」Fabric，核验安装SHA-256。未启动游戏、未提交推送或发布；正式版待用户实机验收。详见[验证记录](verification/2026-10-18-theme-presets-responsive.md)。

### 主页头像交互回归与三端审计（2026-10-16）

- 根因：2afef46删除了两份ProfilePage原生头像交互，并非仅切换后的状态残留；现恢复Native自己的高亮、弹跳、徽章和两步清除菜单，保留E33绘制/只读及banner遮挡，并限制菜单可见区域命中。独立强模型复审通过，聊天面板及其他头像入口未发现同形回归。
- 新增9项实际点击/Skija绘制回归；旧实现7项全部断言失败，修复后扩为9项全过。三端完整测试：Forge1260、Fabric1272、NeoForge1260，均0失败0错误，各2项跳过（golden/benchmark）。六包、native、资源和两OS应用内容一致性通过，707输入哈希未变。
- DS按负责人给定补丁实际落地、测试构建并部署Windows日常NeoForge和WSL Fabric，两处旧包已备份、安装哈希已核验。PowerShell命令缺失导致首次部署在复制前退出，后改用Python执行成功。证据摘要见[本轮验证记录](verification/2026-10-16-avatar-interaction.md)，关键原始证据已归档到 `D:/AtomChat-Backups/2026-10-16-closeout/evidence/`。未启动游戏，实际观感待用户验收；源码按用户要求提交/推送，不另行发版。


### 帽层比例与 Linux 追平（2026-10-16）

- 新增真实 Skija 光栅回归：旧实现 5 项中 3 项按预期失败；修正后三端各 28 项定向测试全过，0 失败、0 错误、0 跳过。守卫与孪生检查通过。
- 当前源码重建三端 Windows/Linux 六包；对应系统 native、版本、作者素材通过校验。同一加载器的 Windows/Linux 应用代码与素材内容哈希完全一致。
- 用户授权后已备份并更新 Windows「Mechanomania-航空学」NeoForge 与 WSL Prism「1.21.1」Fabric。WSL 原包确为缺少头像样式及作者素材的 Preview，现已安装最新 0.3.0。部署哈希、备份位置和实际命令见 [本轮验证记录](verification/2026-10-16-avatar-linux.md)。
- 本轮未启动游戏，帽层与作者磁贴观感待用户验收；未提交、推送或发布。候选包构建戳为 `4c8edf7 / git_dirty=true`。

### WSL 环境排障（2026-10-15，继续使用 0.3.0-Preview）

- 用户截图四个方框对应泰文 `๑`（U+0E51，2 处）和 `ฅ`（U+0E05，2 处）。WSL 已有 Noto CJK / Color Emoji，但没有覆盖这两个码位的字体；安装 `fonts-tlwg-garuda-ttf` 后，当前 Preview JAR 的真实 `fontFor` / `drawRuns` 离屏检查中，29 条内置颜文字缺失码位数由 2 降为 0（隔离字体配置对照复现原四框）。未改模组代码或 JAR。
- 原游戏日志为 `llvmpipe` 软件渲染；独立 EGL 探针复现，并验证 D3D12 可用。Prism 实例 `Env` 原值不是有效 JSON，备份并修正序列化后重启实例，23:09 新游戏日志确认 `D3D12 (NVIDIA GeForce RTX 5060 Laptop GPU)` / OpenGL 4.6。
- 用户已实机确认**音效延迟与颜文字缺字解决，Linux 可运行性验证目标达成**。帧率仅略有提升、仍不到 100 FPS，鼠标不锁定准星仍存在；两者未定因，本轮暂缓。日志确认音频引擎正常启动，不能据此判断音效延迟消失的单一原因。
- 多模型团队：负责人直接排障和验收，Command Code GOAT / `deepseek/deepseek-v4.1-flash-fast` 经 ZCode 独立会话收集证据、修改双语排查文档；负责人复核并纠正文档过强归因和引用。用户已授权本轮文档本地提交；未推送、升版或更新 Linux JAR。详见 [WSL 验证记录](verification/2026-10-15-wsl.md)。

### 0.3.0 本轮本地验证（2026-10-15）

- compileJava --no-daemon：Forge 1.20.1、Fabric 1.21.1、NeoForge 1.21.1 均成功。
- 静态守卫：tools/bare_scan.py（505 / 505）、tools/gen_seams.py --check（甲 123 / 乙 71）与 tools/verify_targets.py 通过；三端六份中英语言 JSON、作者头像素材副本哈希与根版本 0.3.0 检查通过。
- NeoForge 1.21.1 Windows x64 发布 JAR 由 jar --no-daemon 生成；tools/verify_chat_assets.py 与 tools/verify_jars.py --platform windows-x64 均通过，产物只内嵌 Windows x64 native。
- 已部署到日常实例“Mechanomania-航空学”；旧 Preview JAR 备份在 atomchat-backup/2026-10-15-0.3.0，实例 mods 目录现只有 atomchat-NeoForge-1.21.1-0.3.0.jar。SHA-256：e75c01af88665049b82c7a78cc775a87931f7c9c560952486547356f3f5da2a4。
- 本轮未运行测试或启动游戏，头像间距、折叠动画点击与作者磁贴观感仍待实机验收；Forge/Fabric JAR 未在本轮打包或部署。

- Windows 变体全量测试：Fabric 1257、Forge 1245、NeoForge 1245（含新增 `SkiaFontFallbackTest` 5 项）；三端均 0 失败、0 错误、2 跳过。
- 守卫通过：`tools/gen_seams.py --check`（甲 123 / 乙 71）、`tools/verify_targets.py`、`tools/verify_log_prefixes.py`（出口 262）。
- Linux 变体：三端 `build`（含 Windows/Linux 六份 JAR）产物校验通过（`tools/verify_jars.py` + 自测 5 项）；WSL2 Ubuntu 24.04.5 x86_64 用真实 Skija native + Noto Sans SC 子集做字符回退渲染探针，结果见上。WSL JUnit 未能开始，离线缓存缺少 Minecraft/LWJGL Linux 原生依赖。
- 三端部署于 Fabric 1.21.1-CCB、Forge 1.20.1-main、NeoForge Mechanomania-航空学；产物 SHA-256 与构建一致，已核验含新类且不含临时诊断类。
- 独立静态复审无阻断；复审发现项（seams 失同步、逐帧重复哈希、过期出场重播、自持色闪烁、悬浮窗抑制范围）已修正后回归。

## 待验收与限制

- 反应入口与气泡贴纸观感**已由用户实机验收通过**。多人同步、私聊隔离、重连、延迟队列等更强场景尚未逐项实机覆盖。
- 尚无游戏内 CPU/GPU 耗时剖析与 FPS 对照数据；Skija 原生资源按 Windows x64 / Linux x64 分包。WSLg 游戏启动和独显选择已通过日志核实，颜文字及音效已由用户实机确认正常，Preview 的 Linux 可运行性目标达成；性能和鼠标问题暂缓，文件选择、拖放与剪贴板仍待逐项实机验收。当前 WSL 已装 Noto CJK / Color Emoji 和 Garuda 泰文字体；10 月 16 日已安装最新 0.3.0 Linux JAR，本轮未启动游戏，仍待该包实机复验。
- 小本本（延期）：悬停名单只显示前 6 人及剩余人数，未补完整列表。

## 续写与资料

- 下轮先读本快照及相关资料：[消息反应方案](superpowers/plans/2026-10-12-message-reactions.md)、[本轮验证记录](verification/2026-10-12-reactions.md)、[界面设计说明](design.md)。
- 本文件只覆写为最新快照；上一版留在 docs/history/STATUS-before-2026-10-12-reactions.md，更早版本见 docs/history/ 其余归档。
