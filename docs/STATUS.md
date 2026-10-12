# AtomChat 当前状态

> 截至 2026-10-12。这里只维护当前快照；历史状态按日期归档，提交事实用 Git 查询，不逐轮追加日记。

## 项目

- 源码仓库：D:\AtomChat-Main，单仓多目标：Fabric 1.21.1、NeoForge 1.21.1、Forge 1.20.1。
- 现役分支：main；origin：NoWordz/AtomChat-Mod；版本：0.3.0-Preview。

## 本轮交付（消息反应 + 静态审计修正）

- **消息反应（内容键传输）**：右键气泡新增「反应」，气泡下方显示 emoji 与人数，点击切换、自己的项高亮、悬停显示参与者。身份 = `SHA-256("content|author|body")`，两端各自从可见文本算出，**不依赖服务端仲裁**；服务端只做白名单、限流（每连接 8 次/秒）与容量上限（每人每条 8 种、每条 20 种、单项 256 人）。私聊靠「没有该行消息的客户端渲染不出 chip」天然隔离——与引用气泡同一机制。
- **反应贴纸动画**：胶囊背景 easeOutBack + 表情符号延迟追入（160ms）；出场保留布局槽位到淡完（幽灵占位）；计数变化只做数字脉冲；悬停名单单实例跟随渐隐渐显；仅「新鲜到达」的 chip 播入场。
- **静态审计修正**：菜单底色↔阴影顺序回归修复；图片展示/保存字节上限 16 MiB、解码前像素上限 3200 万、失败 URL 缓存上限 512；头像三端复用预检与 256px 缩图、服务端用 PNG IHDR 无 native 检查；捕获层按原生 FilterMask 尊重过滤文本并保留私聊身份。

设计变更与逐项证据见 [2026-10-12 reactions 验证记录](verification/2026-10-12-reactions.md)；原方案中被替换的指纹/回执设计已在 [方案文档](superpowers/plans/2026-10-12-message-reactions.md) 顶部标注。

## 验证

- 全量测试：Fabric 1230、Forge 1218、NeoForge 1218；三端均 0 失败、0 错误、2 跳过。
- 守卫通过：`tools/gen_seams.py --check`（甲 123 / 乙 71）、`tools/verify_targets.py`、`tools/verify_log_prefixes.py`。
- 三端部署于 Fabric 1.21.1-CCB、Forge 1.20.1-main、NeoForge Mechanomania-航空学；产物 SHA-256 与构建一致，已核验含新类且不含临时诊断类。
- 独立静态复审无阻断；复审发现项（seams 失同步、逐帧重复哈希、过期出场重播、自持色闪烁、悬浮窗抑制范围）已修正后回归。

## 待验收与限制

- 反应入口与气泡贴纸观感**已由用户实机验收通过**。多人同步、私聊隔离、重连、延迟队列等更强场景尚未逐项实机覆盖。
- 尚无游戏内 CPU/GPU 性能剖析数据；当前 Skija 原生资源仅支持 Windows x64。
- 小本本（延期）：悬停名单只显示前 6 人及剩余人数，未补完整列表。

## 续写与资料

- 下轮先读本快照及相关资料：[消息反应方案](superpowers/plans/2026-10-12-message-reactions.md)、[本轮验证记录](verification/2026-10-12-reactions.md)、[界面设计说明](design.md)。
- 本文件只覆写为最新快照；上一版留在 docs/history/STATUS-before-2026-10-12-reactions.md，更早版本见 docs/history/ 其余归档。
