# AtomChat 当前状态

> 截至 2026-10-15。这里只维护当前快照；历史状态按日期归档，提交事实用 Git 查询，不逐轮追加日记。

## 项目

- 源码仓库：D:\AtomChat-Main，单仓多目标：Fabric 1.21.1、NeoForge 1.21.1、Forge 1.20.1。
- 现役分支：main；origin：NoWordz/AtomChat-Mod；版本：0.3.0。

## 0.3.0 正式版增量

- **头像样式**：客户端本地配置，默认 AtomChat 原生；设置位置为“聊天 > 消息”，E33Chat 样式采用方形皮肤脸部与外扩帽层。E33Chat 样式显示玩家皮肤，已有自定义头像数据不删除。
- **档案头像只读**：关闭换头像与清除入口，已有头像仍可在 AtomChat 样式下显示。
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

### 0.3.0 本轮本地验证（2026-10-15）

- compileJava --no-daemon：Forge 1.20.1、Fabric 1.21.1、NeoForge 1.21.1 均成功。
- 静态守卫：tools/gen_seams.py --check（甲 123 / 乙 71）与 tools/verify_targets.py 通过；三端六份中英语言 JSON、作者头像素材副本哈希与根版本 0.3.0 检查通过。
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
- 尚无游戏内 CPU/GPU 性能剖析数据；Skija 原生资源按 Windows x64 / Linux x64 分包。Linux x64 JAR 已在 WSL 成功加载 Skija 原生库并完成 16×16 CPU 光栅 PNG 冒烟；Linux / WSLg 游戏启动、主帧缓冲/GPU、文件选择、拖放与剪贴板仍待实机验收。**Linux 字体回退修复尚未在游戏内实机验收**：WSL 上系统只装 DejaVu/Ubuntu，需先 `apt install fonts-noto-cjk fonts-noto-color-emoji` 再重启游戏确认方框消失。
- 小本本（延期）：悬停名单只显示前 6 人及剩余人数，未补完整列表。

## 续写与资料

- 下轮先读本快照及相关资料：[消息反应方案](superpowers/plans/2026-10-12-message-reactions.md)、[本轮验证记录](verification/2026-10-12-reactions.md)、[界面设计说明](design.md)。
- 本文件只覆写为最新快照；上一版留在 docs/history/STATUS-before-2026-10-12-reactions.md，更早版本见 docs/history/ 其余归档。
