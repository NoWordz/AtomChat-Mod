# AtomChat 当前状态

> 截至 2026-10-12。这里只维护当前快照；历史状态按日期归档，提交事实用 Git 查询，不逐轮追加日记。

## 项目

- 源码仓库：D:\AtomChat-Main，单仓多目标：Fabric 1.21.1、NeoForge 1.21.1、Forge 1.20.1。
- 现役分支：main；origin：NoWordz/AtomChat-Mod；版本：0.3.0-Preview。
- 用户已接受趣味特效、气泡反馈音和设置折叠组默认展开行为。

## 本轮资料同步交付

- Profile Sync 已部署到 Fabric 1.21.1-CCB、NeoForge Mechanomania-航空学、Forge 1.20.1-main；目标与构建产物 SHA-256 完全一致。
- 全量测试：Fabric 1204、NeoForge 1192、Forge 1192；各端均 0 失败、0 错误、2 跳过。三端构建、守卫、产物检查及部署核对通过。
- 独立复审最终无阻断；复审发现的问题已修正后完成全量回归。证据与精确哈希见 [2026-10-12 profile sync 验证记录](verification/2026-10-12-profile-sync.md)。

## 待验收与限制

- Profile Sync 尚未启动客户端做真实多人验收；多人资料读写和同步结果待用户实机复验。自动化、审查与部署通过不替代这项验收。
- 当前 Skija 原生资源仅支持 Windows x64；尚无游戏内性能剖析数据。

## 续写与资料

- 下轮先读本快照及相关方案：[Profile Sync](superpowers/plans/2026-10-11-profile-sync.md)、[特效与音效](superpowers/plans/2026-10-11-chat-effects-and-audio.md)、[界面设计说明](design.md)。
- 本文件只覆写为最新快照；旧版留在 docs/history/STATUS-before-2026-10-12.md。除长期适用的设计与方案外，不把历史日志常驻当前状态。
- 9 份历史 jar 备份已保存并逐份核对 SHA-256：D:\AtomChat-Backups\2026-10-12-closeout；目录内 backup-manifest.json 可用于核验。
