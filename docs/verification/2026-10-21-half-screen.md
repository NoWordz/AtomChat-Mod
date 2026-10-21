# 半屏模式与面板外关闭

基线 `main@5f082b7`。该提交为用户已验收的 0.3.2 图片预览/过滤改动。用户随后要求本轮两项功能，并明确现有宽度滑条随模式读写、两种宽度分别记忆；没有要求升级版本、推送或发布。

## 实施范围

- `OutsidePanelInput` 在关闭状态守卫后、弹层前处理新左键按下：GUI 坐标经既有 ScreenMetrics 转换，面板外触发正常关闭并消费；拖动、释放、右键及非法坐标不触发。InputGate 动画期间输入门保留。
- 面板外仍可命中长命令补全窗口，三端先调用可见聊天补全的原生命中处理，再决定关闭；弹层打开时不激活底层补全。
- `halfScreenMode` 默认关闭、半屏宽度默认 600，手机继续使用原 `panelWidth` 默认 480。宽度滑条原范围 400–600、步长 10；模式开关位于外观→调整→布局，三端中英文一致。
- 两份 ScreenMetrics 使用当前模式宽度，半屏 x/y=0、实际高度等于视口；UI/内容缩放继续沿用原公式，窄窗口宽度限制沿用原视口安全边距。
- UiLayout 的贴边参数显式传入，默认工厂仍是纯布局，无需安装游戏平台。顶栏与输入栏/底栏贴边，内容列保留阅读间距。壳体背景、壁纸、模糊与三弹层使用半屏的零内缩/直角轮廓，内部气泡和卡片保留主题样式。
- 半屏应用个人预设时保护手机宽度；手机应用预设时仍可恢复预设保存的手机宽度。半屏模式与半屏宽度不纳入主题预设。

## 失败回归与审查

- 配置/设置首次 9 项因缺失模式、宽度接口与真实设置接线失败，实现后通过；随后相关 125 项通过。
- 旧几何在新增字段到位后实际运行，5 项断言失败：屏幕起点/高度、独立宽度、顶底栏、画布边缘及模糊裁切；实现后定向通过。最初缺字段造成的失败单独记录，不当作几何证据。
- 纯布局不得读取全局配置的回归先失败，再改为显式参数工厂；一项初版测试误要求消息列表为引用栏让位，核对原有布局后改为验证输入栏边界，未修改既有引用占位规则。
- 三种弹层半屏角部实际像素回归 3/3 失败，原因是弹层自身仍用圆角 12 遮罩；新增对应半屏轮廓后修正，手机轮廓不变。
- 独立强模型审查指出两项 P2：预设覆盖手机宽度、面板外补全点击误关闭。前者补手机与半屏宽度及 reload 断言先红后绿（21 项/1 失败→全过），后者补实际输入处理器回归先红后绿。
- 旧输入接线守卫冻结七项，本轮有意更新为八项和新的优先级。人为只断开 Forge 的面板外关闭注册，两个守卫真实失败，随后按字节恢复。没有因为新增处理器而删除守卫。
- 新配置/设置测试逐例恢复全局 provider、config 与涉及的壁纸路径状态，避免污染后续测试。
- 独立只读复查确认两项 P2 消除，本次审查范围无剩余阻断；审查者未运行构建或客户端。

## 命令

在各平台目录运行，NeoForge/Fabric 使用 JDK 21.0.11，Forge 使用 JDK 17.0.18：

```bash
./gradlew test build --offline --no-daemon -I ../../.work/layout-local-mirrors.gradle
./gradlew build -x test -Patomchat.nativePlatform=linux-x64 --offline --no-daemon -I ../../.work/layout-local-mirrors.gradle -I ../../.work/0.3.2-linux-local.gradle
```

临时 init 继续使用前轮已验证的本地缓存恢复方式，不改项目依赖版本或仓库。首轮完整三端仅旧七项事件守卫 2 项失败，保留 `.work/half-screen-{target}-first.log`；最终结果与包指纹另见本节下方。

| 目标 | 测试总数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| NeoForge 1.21.1 | 1375 | 0 | 0 | 2 |
| Fabric 1.21.1 | 1387 | 0 | 0 | 2 |
| Forge 1.20.1 | 1375 | 0 | 0 | 2 |

各端两项为原有 golden/benchmark 跳过。完整测试没有使用子任务首轮临时 test 排除；汇总 `.work/half-screen-build-summary.json`，日志 `.work/half-screen-{target}-full.log`。

静态检查：`gen_seams.py --check`（125/71）、`verify_targets.py`、`twin_diff_report.py`（70 对/未归类 0/陈旧 0）、`verify_log_prefixes.py`（262/违规 0）、`bare_scan.py`（471/505）及 `git diff --check` 均通过。

Windows/Linux 六包构建成功；逐包 native、0.3.2 元数据、语言/音频与新增模式/输入类核验通过。同加载器所有根 class 与 assets 的 SHA-256 一致：NeoForge 602、Fabric 587、Forge 590 项。报告 `.work/half-screen-packages.json`。

| 目标 | 系统 | SHA-256 |
| --- | --- | --- |
| 1.21.1-neoforge | windows-x64 | `cb3323665df304e09075dfdd97f4956200f5701282d00ca270b1b564c998be63` |
| 1.21.1-neoforge | linux-x64 | `5c849343fa5a7f61f0fc17b42871d33bf8db10244758d2e8e224f25ca89c84b5` |
| 1.21.1-fabric | windows-x64 | `a3c59c8baa0cac9c41a6c11ae24eddd82f4cce1020f66c79f39b21c71c01e244` |
| 1.21.1-fabric | linux-x64 | `70a357bf6ae9170d33efebc41e6becd52c00921a9c833569e794a1a9cd3d0de1` |
| 1.20.1-forge | windows-x64 | `ccc2d8bba8165c714ce6dcfcd98d31ec7385dd2eb0b62c3a60498f4fcb1ce75d` |
| 1.20.1-forge | linux-x64 | `25e76a4eccd5c3d0bb101505bd11f0f99fec173ac81d8350b1a141c1ef6a7caa` |

## 视觉与实机范围

参考图 `.work/half-screen-reference/reference.png`：用户要求的是左、上、下贴屏和右侧保留世界画面。本轮没有复制图中的频道侧栏或替换主题。

CPU 光栅使用真实 ShellHeader、MessageListView 与 ChromeMaterial，输出 [手机模式](assets/2026-10-21-phone.png) 与 [半屏模式](assets/2026-10-21-docked.png)，已逐图查看贴边及内容宽度。灰色区域仅表示世界画面位置；不是游戏截图，未覆盖真实头像、GPU 模糊与完整输入按钮，不能替代实机验收。

代理未启动游戏；用户随后授权并完成 CCB 部署，现已反馈本轮验收通过并要求提交、推送与清理临时文件。此前自动化未覆盖的 GPU 或输入场景仍以上述验证范围为界，不据整体验收推断逐项性能测量。

## CCB 部署（2026-10-21）

- 用户指定 `D:\Myworld\.minecraft\versions\1.21.1-CCB\mods\atomchat-Fabric-1.21.1-0.3.2.jar` 所在 mods 目录，安装 Windows Fabric 半屏候选，版本仍为 0.3.2。
- 旧包 SHA-256 `43ae7c6a568f407ebeaa2ceeed0d6ed341bb739b871beaf1dfcf3407f95fe772`，已按字节备份并核验到 `D:\AtomChat-Backups\2026-10-21-half-screen-CCB\111741-540159\atomchat-Fabric-1.21.1-0.3.2.jar`。
- 新安装 SHA-256 `a3c59c8baa0cac9c41a6c11ae24eddd82f4cce1020f66c79f39b21c71c01e244` 与已验证候选一致；同目录暂存核验后原子替换。扫描元数据确认只有一个 AtomChat，其他 73 个文件名称/大小/修改时间不变。
- 部署记录原路径 `.work/half-screen-ccb-deployment.json`；收工时临时证据归档到 `D:/AtomChat-Backups/2026-10-21-closeout/` 后清理工作副本，旧包备份保留。用户随后验收通过并授权提交、推送。
