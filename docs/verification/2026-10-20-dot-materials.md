# 0.3.1 气泡预览与点阵材质续轮验证

> 后续 CCB 实测反馈已继续修正气泡选择器、强调色和内缩圆角；最新实现、验证和部署见 `2026-10-20-selector-accent.md`，下文保留本轮历史记录。

日期：2026-10-20。基线 main@418e22e；本轮与前轮 0.3.1 改动均留在工作区，未提交、推送、启动游戏或发布；后续部署见文末。此记录是本地候选最新状态，取代同日首轮气泡样式记录中的开关、切换交互和产物哈希。

## 已确认并实现

- 三款气泡采用当前主题的左右气泡预览卡直接选择，主题缩略卡也显示对应形状；窄窗口卡片换行，保留原有消息布局和字号。
- 选 C 画法：保留原点阵规则，沿真实栏体边界贴合并叠加淡柔光。输入栏、导航底栏朝上，顶栏朝下；支持实时圆角和输入栏高度。网点不是消息布局的一部分。
- 点阵常开，删除开关及配置/预设布尔字段，旧 JSON 中的字段忽略，旧预设保留其他外观值。
- 关于页 Logo 背后为局部放射点阵；设置和私聊可折叠标题后为静态向右渐隐拖尾，止于箭头之前。没有加入流动动画。
- 局部边缘高光和轻微内阴影仅用于顶栏、输入栏和导航栏。普通卡片、弹窗和空会话页不扩展。

## 实现与审查

- 共享 `DotTexture` / `ChromeMaterial` 沿用 Skija 直画；固定 Paint、MaskFilter、Shader 在渲染线程复用，没有逐点 native 分配、额外依赖或逐帧随机生成。
- 顶栏点阵由三端 `drawPanel` 在面板背景后、所有 composite/body 前统一绘制；顶栏本体保持原有层次。输入栏与导航栏的点阵在消息/页面内容之前绘制，正文在装饰之上。
- 独立强模型审查提出两处 P2：折叠中预览卡会抢后续标题点击，详情页顶栏点阵可能覆盖已绘正文。已先添加回归并实际观察 2/2 失败，再修复行内命中交集与背景绘制阶段，定向全过；独立限定复查通过，无新确证问题。
- 使用真实语言资源生成中文和英文预览，修复移除旧文案后的六份 JSON 尾逗号；补齐新测试的每用例 Platform 初始化，消除测试顺序依赖。
- 前轮实际文本/图片三状态尖角测试继续保留；本轮增加真实三卡光栅、窄宽选择、预设新格式/旧字段、栏体轮廓贴合、裁剪与透明 tint、表面细节和前景/背景分离检查。

## 命令与结果

三端分别执行 `./gradlew.bat test build --no-daemon`，退出码均为 0：

| 平台 | suites | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|---:|
| 1.21.1-neoforge | 169 | 1315 | 0 | 0 | 2 |
| 1.21.1-fabric | 172 | 1327 | 0 | 0 | 2 |
| 1.20.1-forge | 169 | 1315 | 0 | 0 | 2 |

两项跳过仍为原有 golden/benchmark 入口。完整日志 `.work/0.3.1-material-<平台>-verified.log`。红/绿审查回归日志分别为 `.work/0.3.1-material-review-red.log` 与 `...-green.log`。

静态门：`gen_seams.py --check`（124/71）、`verify_targets.py`、`twin_diff_report.py`（69 对，未归类/陈旧均 0）、`verify_log_prefixes.py`（262/0）、`bare_scan.py`（475/505）、`git diff --check` 全过；六份语言 JSON 解析通过。

Linux 分别构建 `jar`（NeoForge）、`remapJar`（Fabric）、`build -x test`（Forge，含 reobfJarJar），均带 `-Patomchat.nativePlatform=linux-x64 --no-daemon`。六包逐一通过 `verify_jars.py --platform <OS> <精确 jar>` 和聊天资源验证；新类齐全，旧 MessageEdgeDots、开关类字段及文案已消失，同 loader 两 OS 的应用 class/assets 逐字节聚合哈希相同。Linux 未另行运行游戏或测试。

## 本地候选包

| 目标 | OS | SHA-256 |
|---|---|---|
| 1.21.1-neoforge | windows-x64 | `0bfd6568f725d005fb5b475567e8f4429290255634631ba3cd0446d727bd0248` |
| 1.21.1-neoforge | linux-x64 | `b1cbf0a623f4cafd1d4f94f7524eb206eb0fd532b20f5bf95d2175904efedf7d` |
| 1.21.1-fabric | windows-x64 | `9e2b68bbba2975add08000c67e56267ec8fd582f396edf5c7fd283ca394fe9ee` |
| 1.21.1-fabric | linux-x64 | `b585bf5a88f7b6930c7692b7899129d8a757cb2349817ed5a2effcac07adedb1` |
| 1.20.1-forge | windows-x64 | `bb74819b1d31c286bee9521a54d8aabb1f463f2fe15229206b1434c1929150ba` |
| 1.20.1-forge | linux-x64 | `ac2d3b3b026f03914a56e0ac372af17c7418fda1169c8645c4c0a84700730a65` |

Windows 输出在各平台 `build/libs/`，Linux 输出在 `build/linux-x64/libs/`。文件名均为 0.3.1；旧候选哈希不再代表当前构建。完整路径/字节数及应用内容哈希见 `.work/0.3.1-packages.json`。

## 可视证据与边界

- [三款气泡与输入栏，深色](assets/2026-10-20-bubble-material-dark.png)
- [三款气泡与输入栏，浅色](assets/2026-10-20-bubble-material-light.png)
- [真实设置/关于页，中文/英文/窄窗口](assets/2026-10-20-material-surfaces.png)
- [三种栏体，深浅主题](assets/2026-10-20-material-bars.png)

以上均为真实生产 Skija 绘制路径的 CPU 离屏结果，外层并排排版、输入栏占位文案及头像占位属于测试展示；不是实机游戏截图。已目视核对深浅主题衔接、三款轮廓、文字层次、Logo 和标题拖尾、窄宽换行。没有验证实际 GPU 帧耗、壁纸上的全部配色、多人运行或最终游戏内观感，需用户实机反馈。验证完成后按用户指定部署至 Windows 1.21.1-CCB，详见文末；其他实例未改动。

## 用户指定实例部署

- 日期：2026-10-20；目标为用户明确指定的 Windows Fabric 1.21.1-CCB，替代先前默认部署位置。
- 安装：D:\Myworld\.minecraft\versions\1.21.1-CCB\mods\atomchat-Fabric-1.21.1-0.3.1.jar
- 安装 SHA-256：9e2b68bbba2975add08000c67e56267ec8fd582f396edf5c7fd283ca394fe9ee，与已验证 Fabric Windows 候选一致。
- 旧包 SHA-256：d228d2717f572f4f66bcb71d50319c6a965ac9a79f7b10f2001073a5eb401ca4。
- 已核验备份：D:\AtomChat-Backups\2026-10-20-ccb-dot-materials-05956c26\atomchat-Fabric-1.21.1-0.3.1.jar；同目录 deployment.json 保存收据。
- 命令：verify_jars.py --platform windows-x64 对候选检查后，备份旧包、校验临时副本并原子替换，再读回安装哈希；成功。
- 未改动其他实例、配置或其他模组，未启动/重启游戏。需要重新启动客户端加载新包，实机观感仍待用户确认。
