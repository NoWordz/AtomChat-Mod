# AtomChat 当前状态

> 截至 2026-10-21。这里只维护当前快照，提交及推送事实以 Git 查询为准。

## 项目与验收

- 仓库 `D:/AtomChat-Main`，分支 `main`，远端 `origin` 为 `NoWordz/AtomChat-Mod`；三端为 Fabric 1.21.1、NeoForge 1.21.1、Forge 1.20.1，源码版本保持 0.3.2。
- 用户已确认图片预览/过滤、半屏与面板外关闭均验收通过，授权提交、推送和清理旧轮次材料；本次不打 tag 或发布。
- 图片预览支持平滑淡入、鼠标锚点滚轮缩放与左键拖动；三种弹层统一裁切，保存预设与默认主题单一选中，公屏系统/玩家过滤持久化。见 [预览与过滤验证](verification/2026-10-20-preview-and-filter.md)。
- 半屏模式位于外观→调整→布局，左、上、下贴边，顶底栏贴边并去掉手机外环。原宽度滑条分别保存手机/半屏宽度，切换及重载后恢复；内部卡片、气泡和主题保留。
- 左键点击面板外世界画面正常关闭并消费事件；拖动移出面板不关闭。伸出面板的原生命令补全框仍可点击，弹层打开时不激活底层补全。
- 半屏应用个人预设不覆盖手机宽度；手机应用预设保留原宽度行为。详情见 [半屏方案](superpowers/plans/2026-10-20-half-screen.md) 和 [半屏验证](verification/2026-10-21-half-screen.md)。

## 最终验证与部署

- 三端完整 `test build --offline --no-daemon`：NeoForge 1375 / Fabric 1387 / Forge 1375，均 0 失败、0 错误，各 2 项原有 golden/benchmark 跳过；独立审查发现的两项 P2 已补失败回归并修复，复查无阻断。
- Windows/Linux 六包已构建；对应 native、版本、资源及同加载器 class/assets 一致性通过。收工前再次核验包内容、CCB 安装包和旧包备份哈希；没有重复游戏测试或性能测量。
- seams 125/71、targets、孪生 70 对/未归类 0/陈旧 0、日志 262/违规 0、bare 471/505 及 `git diff --check` 通过。
- CCB 安装：`D:/Myworld/.minecraft/versions/1.21.1-CCB/mods/atomchat-Fabric-1.21.1-0.3.2.jar`，SHA-256 `a3c59c8baa0cac9c41a6c11ae24eddd82f4cce1020f66c79f39b21c71c01e244`。
- 原已验收包 `43ae7c6a…` 保留在 `D:/AtomChat-Backups/2026-10-21-half-screen-CCB/111741-540159/`；部署时其他 73 个文件未改动，代理未启动游戏，随后用户反馈验收通过。

## 仍有效的约束与待办

- shared 为公共逻辑；official/Fabric 同名源码按映射等价维护，修改公共面须跑 seams、targets 与孪生检查。
- 玩家档案以服务端认证身份授权，按世界保存；头像图片先持久化再发布元数据。保留 [同步约束](superpowers/plans/2026-10-11-profile-sync.md)、[同步验证](verification/2026-10-12-profile-sync.md) 和 API 探针。
- 消息反应的实际内容键机制与边界以 [反应验证](verification/2026-10-12-reactions.md) 为准；过期的指纹/回执实施方案已清除。
- 多人同步、重连、延迟队列及 GPU 量化性能尚无逐项实机证据；[媒体性能审计](superpowers/plans/2026-10-12-media-performance-audit.md) 中两项真实传输/剖析仍未完成，不将整体验收当作性能测量。
- 延期：反应悬停名单超过 6 人仍只显示剩余人数。WSLg 性能及鼠标锁定未定因，已解决的字体/音效不在本轮重开。

## 资料与清理

- 长期设计见 [design.md](design.md)，玩家/服主/开发者手册见 `docs/wiki/`，商店正文仍由现有工作流引用，保留。
- 已完成旧轮次方案、过期验证图、历史 STATUS、旧审计及散落构建日志按用户要求移入回收站；当前两轮 0.3.2 方案/验收记录、有效协议与未完成审计保留。
- 临时证据已按逐文件哈希归档到 `D:/AtomChat-Backups/2026-10-21-closeout/`，历史 `.work` 路径从归档查阅；候选六包、已安装包和旧包备份保留。
