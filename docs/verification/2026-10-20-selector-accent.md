# 0.3.1 气泡选择器与强调色续修

日期：2026-10-20。基线 main@418e22e；用户已验收通过并授权将本轮 0.3.1 改动提交本地。源截图与观察保存在 `docs/design-references/0.3.1-selector-feedback/`。本记录接续同日点阵材质验证，旧记录的网格换行行为与产物指纹是历史状态。

## 修正

- 气泡选择器直接复用主题预设的 `drawPreviewCard`，包括缩略面板、上下栏、左右气泡、阴影、选中描边、卡下标签与按压反馈；整行也使用主题预设的标题和容器设计。
- 两条缩略条共用 `CardStrip` 的滚轮、水平拖动和垂直手势转交机制，各自保留偏移与动画状态；样式数量取自枚举，不依赖固定三列，窄屏保持横向滚动。顺序为主题预设、气泡样式、圆角。
- 选中导航和设置 Tab 使用不透明原强调色。共享悬停颜色不再依据底色向黑或白混色，只通过透明度表达淡入；半透明悬停与背景合成后仍会受底色影响。
- 导航选中/悬停及设置 Tab 使用同一内缩圆角计算；外轮廓半径减去内缩，并限制在当前形状短边的一半以内；界面圆角为零时也为零。
- 普通卡片的 hover 原本已由同一轮廓裁切，保留其几何；固定暗色表情面板没有使用主题派生颜色，本轮未改其独立皮肤。

## 回归与独立审查

- 初始真实回归 3/3 失败，覆盖原强调色 RGB、真实导航选中色与曲线、气泡组顺序和横向拖动。
- 更新原有预览测试，使其通过滚动验证三款选择可达；保持真实 Skija 像素检查，验证左右气泡与三种形状区别、折叠命中边界。
- 新增纵向拖动不选择卡片的回归；独立强模型指出跨缩略条滚轮可能抢走拖动归属，已固定按下期间的所属条。
- 故意移除归属保护后，`wheelOverTheOtherStripCannotStealAnActiveDrag` 1/1 失败；随后按字节恢复修复代码，完整构建包含该回归。

## 验证范围

Skija CPU 预览检查深色、浅色和窄面板，包括卡片同源、组顺序与形状。用户提供的截图是真机基准；CPU 图不代表游戏 GPU 或最终实机观感验收。未启动或重启客户端。

## 最终检查

- 三端均执行 `./gradlew.bat test build --no-daemon` 并通过。
- NeoForge 1319、Fabric 1331、Forge 1319 项测试，均 0 失败、0 错误、各 2 项原有跳过。
- 日志：`.work/0.3.1-selector-{target}.log`；最初三项红测 `.work/0.3.1-selector-red.log`，审查问题的变异红测 `.work/0.3.1-selector-review-red.log`。
- 独立强模型已复查确认拖动归属问题消除；未重复运行其自己的构建。
- seams 124/71、targets、twin 69 对/0 未归类/0 陈旧、日志 262/0、bare 480/505、diff-check 均通过。
- Linux 变体分别执行 NeoForge `jar`、Fabric `remapJar`、Forge `build -x test`（含重混淆），均传入 `-Patomchat.nativePlatform=linux-x64`；全部通过。
- 六包已逐一验证目标 native、版本、素材/文案与新 `SettingsSectionPage$CardStrip` 类；同 loader 的 Windows/Linux 应用 class 和 assets 哈希相同。
- 实际 Skija 预览：[设置卡片](assets/2026-10-20-selector-surfaces.png)、[导航选中底色](assets/2026-10-20-selector-bars.png)。

| 目标 | 平台 | SHA-256 |
| --- | --- | --- |
| 1.21.1-neoforge | windows-x64 | `4cfc363731e9d8ba891598ceef78793c82296a360053560744849b9d7f98cf91` |
| 1.21.1-neoforge | linux-x64 | `d885ca6e28036848eb3685dc410f28556403ee637484e0f7ffba543e8ecdf2a3` |
| 1.21.1-fabric | windows-x64 | `180032a5075c8b31ffce3306db8253f089723b334505e46a689b562f4a42fb89` |
| 1.21.1-fabric | linux-x64 | `fbbf6c234d44586cb3542055913e963e1f316b19e238e36a9323b01f692d5c59` |
| 1.20.1-forge | windows-x64 | `53d2a6934e483551f624cadcfb02305056cc67a1b6c0635be6f5105a532d7099` |
| 1.20.1-forge | linux-x64 | `39382d224b5838a8b4bee85905cc09a2fa55be7ae33735a6bf6fa5e04d7a4151` |

## CCB 部署

- 用户本轮属于同一 0.3.1 实测修复，沿用“做完就部署”与明确 CCB 路径授权；仅替换该实例 AtomChat jar，其他实例、配置及模组未动。
- 目标：`D:\Myworld\.minecraft\versions\1.21.1-CCB\mods\atomchat-Fabric-1.21.1-0.3.1.jar`。
- 安装 SHA-256：`180032a5075c8b31ffce3306db8253f089723b334505e46a689b562f4a42fb89`。
- 原包 SHA-256：`9e2b68bbba2975add08000c67e56267ec8fd582f396edf5c7fd283ca394fe9ee`。
- 备份：`D:\AtomChat-Backups\2026-10-20-ccb-selector-accent-da71fd44\atomchat-Fabric-1.21.1-0.3.1.jar`，同目录有 `deployment.json`。
- 备份核验、临时文件核验、原目标未变化检查、原子替换与安装读回哈希均通过。未启动客户端，重启后生效；未提交、推送、打 tag 或发布。

## 用户验收与归档

- 用户在部署后明确反馈“验收通过，提交”，本轮界面观感验收完成。
- 将完整 0.3.1 实现、回归测试、双语日志、设计参考与验证记录一并提交本地；不推送、打 tag 或发布。
- 本次只更新验收记录并提交，沿用上轮已通过的三端完整构建与六包核验；提交前另执行差异空白检查。
