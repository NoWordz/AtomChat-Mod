# 0.4.0-preview 横幅专项优化

本轮基于 `a1dff96`，保持版本与 Skija 依赖不变。部署目标沿用 Forge `1.20.1-main`、Fabric `1.21.1-CCB`、NeoForge `Mechanomania-航空学`。

## 材质与动画

- 原因：横幅的 `floatSurfaceFill()` 返回预混合、不透明的 `cardCutout()`，滑块只能改变颜色。现在 `notificationFill()` 直接继承 `cardFill()` 的 RGB 和 alpha，底色最低 128/255；文字不受卡片透明度滑块影响。入退场和主机切换仍可将整张横幅淡出到零。
- HUD、面板和位置编辑器的横幅复用 `UiCards.drawCard`，圆角、阴影、细边与 hover 都来自现有卡片族。100% 不透明时跳过不可见的模糊工作。
- 透明横幅在自身 alpha 图层之前，以 `SaveLayerRec` backdrop filter 模糊当前画布的圆角区域。滤镜、Paint 和保存栈本帧释放；没有跨帧 GPU 快照或借用纹理跨越 `context.resetAll()`。
- 通知入退场使用全局 zoom/slide 设置、全局时间常数和 `PanelOpenMotion` 几何。旧横幅始终按 `PAGE_SLIDE_TAU_MS` 滑动让位。绘制与按钮/主体命中使用同一变换后的矩形。
- 保留六秒出生时间及共享队列；开关面板仍由屏幕外层动画携带横幅，不重播旧通知。关闭动画时首帧立即显示，到期立即移除。
- 新通知放在上方。居中/底部 HUD 锚点改变堆栈原点时先换算旧位置，避免瞬移。

## 近期改动检查与修正

独立初审确认两项 P2，均有修复前失败、修复后通过的实际渲染回归：

1. 重叠时新横幅主体没有挡住旧横幅回复按钮。现在按钮命中只接受最前方卡片。
2. dismiss 后立即关闭面板，目标 HUD 高度缩短，会整张跳过仍在让位中的横幅。现在目标堆栈高度只用于布局，由实际帧缓冲裁切 HUD。

独立复审因服务 429 未完成；主代理重新阅读相关绘制、命中、队列清理和主机切换代码并执行测试，未把它报告为独立复审通过。检查覆盖通知、面板开关、相邻 Skia/GL 与字体路径，没有审计最新提交的全部 177 个文件。

最终重跑另定位了 Forge 既有测试 agent 的加载顺序缺陷：它调用的 EventSubclassTransformer 会反射加载事件父类，而 JVM 不会对同一 transformer 内的递归加载再次回调，可能漏掉 NetworkEvent 的构造器转换。独立新 JVM 中先加载 ClientCustomPayloadEvent 可稳定复现 NoSuchMethodException。现在在注册 agent 后、不初始化静态状态地先加载 NetworkEvent，并把独立进程 `forgeEventAgentSmoke` 接入 check。该修复及探针均只在测试源集，未进入产品 JAR。

## 崩溃证据与范围

用户确认提供的是旧报告：2026-10-22「香草纪元：食旅纪行」，AtomChat **0.3.2**，Oracle Java **21.0.11+9**。

读取同次压缩游戏日志及 `hs_err_pid49280.log`：

- 15:37:41.886 普通内置字体加载成功，15:37:42.982 粗体加载成功。
- 原生崩溃帧 `V [jvm.dll+0x32bfb1]`。
- 崩溃线程为 `C1 CompilerThread0`，任务为 `Entity.updateFluidHeightAndDoFluidPushing` 的 C1 OSR 编译；读取地址为 `0x18`。
- Skija native 在运行约 124.949 秒时已载入，崩溃发生于约 378.533 秒；没有字体/Skija 的直接崩溃调用栈。

直接崩溃点属于 JVM 编译线程，字体日志与闪退相邻不能证明字体指针错误。尚无最小复现或原生转储能区分具体 JVM 缺陷与更早的内存破坏，不声称已修复 JVM；本轮没有据此改字体、Java 安装或启动器参数，也没有当前 preview 的同类新复现。原始日志可能含登录凭据，因此这里只保留脱敏事实，不复制完整报告。

## 验证

三端执行 `gradlew.bat test build --offline --no-daemon --console=plain`，Forge 使用 JDK 17 和 `-Dnet.minecraftforge.gradle.check.certs=false` 跳过离线构建不需要的联网预检，Fabric/NeoForge 使用 JDK 21。Fabric 首次离线构建缺 mapping-io，正常联网解析官方依赖后再次离线构建；未修改仓库或依赖版本。

| 目标 | 测试总数 | 失败/错误 | 原有跳过 |
| --- | ---: | ---: | ---: |
| Forge 1.20.1 | 1476 | 0 / 0 | 2 |
| Fabric 1.21.1 | 1488 | 0 / 0 | 2 |
| NeoForge 1.21.1 | 1476 | 0 / 0 | 2 |

新增九项 JUnit 覆盖透明度下限、真实局部模糊、多帧栈平衡、九宫格插入、zoom/slide 绘制命中、关闭动画、旧横幅让位、前景遮挡和 dismiss/主机切换，含一项实际材质预览。原有跨主机、到期、富文本和屏幕离屏测试继续执行。完整 XML 与过程日志原位于 `.work/banners/`，收工归档到 `D:/AtomChat-Backups/2026-10-23-notification-banners/closeout-evidence/work/banners/` 后将工作副本移入回收站。

变异验证：将 backdrop blur 改为零，像素检查失败；移除 HUD 原点换算，居中锚点检查得到最大通道差 255、265369 个通道变化。原文件均按字节恢复。等价浮点矩阵允许一个色阶的舍入误差，未用宽松阈值掩盖位置跳变。

`python tools/run_notification_backdrop_probe.py` 使用隐藏 GLFW 窗口和真实 RTX 5060 Laptop GPU，120 帧、每帧三张卡片，覆盖嵌套面板 alpha/缩放图层、context reset、像素读回、栈配平和 GL error。完整小场景 submit+finish 耗时 p50 **0.617ms**、p95 **0.813ms**、最大 **0.827ms**，不含读回耗时，不能代替游戏整帧/整合包性能测量。运行前设置 Windows JDK 21 的 JAVA_HOME，并先构建 NeoForge Windows x64。

静态门禁：seams 139/71；目标矩阵通过；twins 75 对，未归类/陈旧均 0；日志 269 条无违规；裸值 469/505；`git diff --check`。三个 Windows x64 包均通过 `tools/verify_jars.py`，核对原生唯一性、PE 架构、内嵌登记、版本、资源和重定位；额外核对无测试 agent/probe 类进入包。本轮未重建 ARM64/Linux 变体。

视觉证据：[真实横幅材质预览](assets/2026-10-23-notification-banners/materials.png)、[GPU 局部模糊](assets/2026-10-23-notification-banners/gpu-backdrop.png)。预览图是实际 CPU 渲染器，GPU 图是生产材质原语探针，均不是游戏内截屏。

## 构建与部署

三个 Windows x64 包及 SHA-256 见 [packages](2026-10-23-notification-banners-packages.json)。安装读回、备份哈希和其他 mods 文件一致性见 [deployment](2026-10-23-notification-banners-deployment.json)。仅替换明确的三个同名 AtomChat JAR，未启动或关闭游戏、未改配置或玩家资料；首次重启才加载新包。

用户于 2026-10-23 确认本轮验收通过，并要求提交、推送、更新状态与清理临时文件。源码、测试和本记录纳入 `main`，推送目标为 `origin/main`，实际提交号及同步状态以 Git 查询为准；不打 tag 或公开发布。保留部署备份、包指纹及长期视觉证据，本轮临时计划、构建/调试工作副本和空运行日志归档后移入回收站。

用户整体验收通过不等同于多人专项、不同 GPU 整合包帧耗或 ARM64/Linux 实机逐项验收；这些仍按前述验证范围记录。
