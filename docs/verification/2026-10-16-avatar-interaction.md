# 2026-10-16 主页头像交互修复与三端审计

根因：`2afef46` 在两份 `ProfilePage` 中删除了原生头像高亮、弹跳、编辑徽章和管理菜单，头像点击只被消费。因此问题无须切换样式即可出现。

修复恢复 AtomChat 样式下自己头像的原有交互及两次清除确认；E33Chat 和他人头像保持只读。切换样式或玩家会清除旧菜单、确认和动画状态；菜单绘制与点击限制在可见区域，头像仍挡住背后的 banner 点击。上一轮帽层比例修正保留。独立强模型复审通过，聊天面板、会话列表、通知及黑名单未发现同形回归，未改三端 Screen 的事件分发。

## 验证

- 新增真实点击与 Skija 光栅回归：旧实现 7 项均为断言失败；最终扩为 9 项，覆盖高亮、弹跳、菜单、样式切换、玩家切换、清除确认及超时、可见区域、菜单连续绘制和动画关闭。
- 三个目标分别以对应 JDK 执行 `./gradlew build --offline --no-daemon`：Forge 1260、Fabric 1272、NeoForge 1260 项，均 0 失败、0 错误；每端跳过 `GoldenFrameTest` 和 `SkiaDrawBenchmark` 两项。
- 三端 `./gradlew build -x test -Patomchat.nativePlatform=linux-x64 --offline --no-daemon` 成功。六包的版本、资源、对应系统 Skija native 校验通过，同一加载器两种系统包内应用代码/素材哈希一致。
- `bare_scan.py`（505/505）、`gen_seams.py --check`、`verify_targets.py`、`twin_diff_report.py`、`git diff --check` 通过；构建前后 707 个输入文件哈希一致。完整 JUnit 在 Windows 宿主运行，未启动游戏，本轮游戏内观感由用户复验。

## 部署与备份

DS 已执行两处替换及独立读回核验，版本仍为 0.3.0：

| 实例 | 安装包 SHA-256 |
|---|---|
| Windows「Mechanomania-航空学」NeoForge | `2450b8ff1dd6b43f75ffa5cc3331ca9fc1f39ec4f0552e75bb74b6fe7028b82f` |
| WSL Prism「1.21.1」Fabric | `ddb29825abfd5624d1fa7b94ebfaba79730acce0531a44e3373d9d0ce5c5e9fc` |

旧包分别保留在 `D:/AtomChat-Backups/2026-10-16-avatar-interaction/NeoForge/` 和 `/home/niuqu/.local/share/PrismLauncher/instances/1.21.1/atomchat-backup/2026-10-16-avatar-interaction/`。部署包从 `4c8edf7` 加本轮未提交修复构建，构建戳 `git_dirty=true`；源码提交不会另行重建这些已验证包。

负责人确定方案与补丁，独立强模型复核；用户指定 GOAT DeepSeek 实际应用补丁、运行测试、构建和部署。关键审查、JUnit 汇总、产物清单、部署清单及日志已归档到 `D:/AtomChat-Backups/2026-10-16-closeout/evidence/`，临时工作目录在收尾时清理。本轮仅提交/推送源码，不升版、打 tag 或发布。
