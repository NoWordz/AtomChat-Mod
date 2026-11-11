# v0.7.0 复核与三实例部署

2026-11-11。范围为已确认的动画、主题、壁纸、边框及阴影专项，追加空状态动画回归。按用户要求，仅保留一个实现子代理，由主代理复核和部署。未提交、推送、公开发布或启动游戏。

## 复核结论

- 页面使用真实生产内容的 Picture，同步入退场；退场只回放指令，不再运行旧页业务。中断起始帧保留当前内容像素，标题及返回状态立即更新。依据：`shared/src/main/java/com/atom/chat/nav/PageTransitionComposite.java:40`。
- 主代理补齐占位回归：`EmptyStateTransitionTest` 使用真实 `SettingsSectionPage.drawEmptyState`，覆盖在线／屏蔽空列表的图标、文字、点阵入场、退场、反向中断。旧 HEAD 转场实现复现失败，当前实现通过。证据：`.work/v0.7.0/empty-state-red.log`、`empty-state-green.log`。
- 绘制时钟和命中时钟统一：新测试先复现实际点 250 被逆算为 231.46，再让 `drawArrivingFrame` 采用实际绘制时刻，回归通过。导航只在压平预算检查前采样一次，防止连续切换时预算检测落后。证据：`.work/v0.7.0/review-order-paint-clock-red.log`、`review-order-paint-clock-green.log`。
- 移除正常帧额外 `withContext` 状态快照；在既有 paint 借用内取得数值身份，仅溢出压平另借上下文。60 个真实桥接帧及 6 次 Surface 重建验证通过，仍保留上下文变化清理。证据：`.work/v0.7.0/paint-context-identity-probe.log`；实现：两映射 `render/SkiaGraphics.java`、三端 `screen/AtomChatScreen.java`。
- 适配诊断探针的 `Native.getPtr`／`Frame.context` 夹具；原测试超时实际由夹具编译错误造成，未放宽超时或跳过测试。证据：`.work/v0.7.0/profile-wiring-direct.log`、`profile-wiring-fixed.log`。
- 稳定复杂页不做 GPU 压平；只处理中断叠层超预算的事件。真实 GL 压力测试覆盖 120 个稳定复杂帧和 30 次中断，1.5 密度快照像素差至多 1/255。证据：`.work/v0.7.0/dense-navigation-gpu.log`。
- 壁纸已改为七套独立色盘和可辨曲面；静态 SkSL 后台生成，稳定尺寸按实际密度适配，不逐动画帧重生成。热路径不等待存储锁。依据：`shared/src/main/java/com/atom/chat/wallpaper/WallpaperPresets.java:95`、`WallpaperViewport.java:10`。
- 新配置黑色卡片为 50% 不透明度，蓝色强调不变；边框新默认 3、旧开启保留 2、旧关闭保留 0。三实例现有 AtomLib 的接口、`BorrowedFrame.context()` 及 50% 黑卡片行为已独立预检，无需更换 Lib。证据：`.work/v0.7.0/runtime-api-*.log`。

## 验证与制品

最终三端完整构建及测试结果写入 `.work/v0.7.0/build-results.json`；制品检查写入 `.work/v0.7.0/artifact-verification.log`。下方部署回执为最终安装事实，不以编译成功代替安装验证。

| 平台 | 测试数（含跳过） | 失败／错误 | 既有跳过 | build |
| --- | ---: | ---: | ---: | --- |
| Fabric 1.21.1 | 1780 | 0／0 | 7 | 通过 |
| Forge 1.20.1 | 1765 | 0／0 | 7 | 通过 |
| NeoForge 1.21.1 | 1765 | 0／0 | 7 | 通过 |

三端制品 CRC、v0.7.0 元数据、加载器、运行期 AtomLib 范围、嵌套包注册及新实现类断言全部通过。配对 Skija 的 JPMS 描述确认导出 `io.github.humbleui.skija.impl`。

- 真实生产设置页隐藏 GL smoke：SLIDE／ZOOM 中断 body 像素差均为 0，每帧重建 Surface 后仍正确。证据：`.work/v0.7.0/page-gpu-smoke.log`。
- 借用的不可变图像与 owned snapshot 各跨 60 次 reset 验证；不保留 borrowed context／Surface 本体。证据：`.work/v0.7.0/picture-borrow-probe.log`。
- 孪生检查的既有 14 个未归类残差与 HEAD 基线相同，无新增；不把该既有红灯写成全仓孪生全绿。证据：`.work/v0.7.0/twin-baseline.log`、`twin-current.log`。
- 既有 Python 工具自测另有过时 UiTokens 夹具及 ChatClassifier 孪生基线失败，本轮未扩大范围修工具；项目 Gradle 全量测试与制品检查单独报告。证据：`.work/v0.7.0/tool-tests.log`。

## 部署

状态：已于 2026-11-11 12:28（Asia/Shanghai）部署完成，三端安装包均与候选 SHA-256 一致，每实例仅一份启用的 AtomChat。部署前目标游戏进程为 0，AtomLib 及其余 181／102／224 个文件哈希保持不变。

目标：Forge `1.20.1-main`、Fabric `1.21.1-CCB`、NeoForge `Mechanomania-航空学`，均在 `D:/Myworld/.minecraft/versions/<实例>/mods`。

部署脚本仅替换经元数据确认的 AtomChat；逐实例检查未运行、完整备份旧包、读回 SHA-256，并核对 AtomLib 及其他文件哈希、仅一份启用的 AtomChat。执行证据写入 `.work/v0.7.0/deployment-receipt.json`。

旧包备份根目录：`D:/AtomChat-Backups/2026-11-11-v070-20261111-122827/`；同目录 `deployment-receipt.json` 保存独立回执。

| 实例 | 新 AtomChat SHA-256 |
| --- | --- |
| 1.20.1-main | `8ce44952f7d4a44a5dfd75b99197ea178761a39abe8b13190db73d73b823aac7` |
| 1.21.1-CCB | `8aa50b5f0c46801677b80a87f75a5e70e854000125243399ed0cc97369ef2159` |
| Mechanomania-航空学 | `c01db98a9e6cc16b7fe7b6f96f67cf2999f6a7fb97ae8ed1ee074bedd8fe8804` |

## 适用范围与未验收项

- 未运行 Minecraft；实际整合包中首次开屏、连点、返回、壁纸亮暗场景和游戏 FPS 仍待用户验收。隐藏 GL 是开发 smoke，不是游戏实机验收。
- 隐藏 GL smoke 冷启动还包含字体加载和首次管线成本，不能用其 p50 或冷峰值直接推断游戏表现。已沿既有预热路径提前绘制新阴影。
- 极端快照超单边 8192／16M 像素时结束该次过渡；新预设壁纸超单边 4096／16M 像素按预算缩放。自定义壁纸保留此前的 1024 长边解码策略。不能承诺无限尺寸清晰或任意压力下不掉帧。
- 已有用户配色和壁纸不被自动覆盖；查看新默认配色需在外观页选择默认主题，壁纸独立选择。
