# AtomChat 0.5.0 字体、性能与背景还原实施方案

**目标：** 复用 AtomLib 同字节字体；还原亮背景的原始模糊观感；用可复现的逐帧证据定位并处理面板性能瓶颈。

**基线：** AtomChat `e92fdaf9`，AtomLib `56f1b957`；两仓开工前重新检查状态和 HEAD。三端 Forge 1.20.1、Fabric 1.21.1、NeoForge 1.21.1 均在范围内。

**约束：** 同时最多一个子代理；不启动游戏；不擅自发布或推送。若现有测试实例由用户明确授权部署，依据既有部署约定备份、替换并校验。不能用离屏结果冒充实机验收；不能将未经测量的差值当作根因。保留 Kawase 五次 pass、强度配置及主帧缓冲 alpha 安全约束。

## 1. 去除非用户要求的亮度钳制

- [ ] 比较 `222c7483^` 与当前三端 `PanelBlurRenderer.java`、`atomchat_panel_blur.fsh` 和 shader JSON，确认仅撤销亮度钳制，不撤销像素对齐、动画或后续修复。
- [ ] 将三端圆角合成 shader 的 RGB 输出恢复为 `texColor.rgb * vertexColor.rgb * ColorModulator.rgb`；删除 `u_MaxLuma`、uniform 设置及 `GLASS_MAX_LUMA`。移除 `AtomChatConfig.glassMaxLuma`，确认旧 JSON 中多余字段不影响读取；不覆盖用户配置文件。仍使用现有 mask/fade alpha，绝不引入主帧缓冲 alpha 的读写。
- [ ] 将 `shared/src/test/java/com/atom/chat/render/PanelBlurGlassTest.java` 和 `tools/probes/PanelBlurGlassProbe.java` 改成实际验证还原行为：亮、暗、彩色输入都保持原 RGB，alpha 保持不变；比较修改前 shader 或具明确预期的参考输出。先证实旧默认钳制在亮样本下失败，再让修复通过。测试故意重新启用钳制时必须失败。
- [ ] 使用 `python tools/run_panel_glass_probe.py` 执行真实离屏 OpenGL 验证；三端 shader 和 uniform 配套校验。测试强度 1.0 的实际五次间距仍为 2、3.5、5、6.5、8；非默认值继续乘同一系数。若实际游戏观感仍不符，记录截图和设置，不将本改动宣称解决全部质量问题。

## 2. 复用 AtomLib 字体

- [ ] 先核验 AC 实际锁定 AtomLib 构件的三个平台 JAR 内包含 regular/bold 默认字体，哈希分别为 `9e7f87529d1aaa047269f9096530ca6e3f929f78333d2d9f9a32dc1712a49b8e`、`4b65884fe3c75d9917b5fa8456c27eb568436cb1a882001e7187c217101052fe`；不能仅凭当前 AtomLib 源码推断锁定依赖。若锁定依赖不满足，先处理真实依赖兼容性，不伪造已发布版本。
- [ ] 核对当前二进制提供的 `FontSource.bundledRegular()`、`bundledBold()` 与 `read()` 的真实签名及三平台 classloader 可见性。修改 `shared/src/main/java/com/atom/chat/font/FontManager.java`，通过实际可用的 AtomLib API 获取字体字节，保留现有 `mediumWeight(...wght 500)`、粗体加载和字体缺失回退语义；删除 AC 三端重复 OTF。
- [ ] 加载测试必须用真实锁定依赖分别检查常规、粗体和中文字符，并核验 AC 的可变字重仍为 500；验证缺失或解码失败时系统字体回退及粗体 fallback。删除字体来源或改错误字重的变异须被测试捕获。
- [ ] 三个最终产物验包：AC JAR 无这两份字体，AtomLib JAR 各有一份；启动/资源加载的验收独立于离屏单测。

## 3. 性能：先建立可证伪的逐帧归因，再优化

- [ ] 分别记录闭面板、稳定聊天、各目标页面、重复打开及固定路径切页；从启动关闭光影，固定相机、分辨率、垂直同步、FPS 上限、壁纸和 GUI 尺寸；记录 AC/AtomLib JAR 哈希、Java、GPU/驱动、modpack。原日志切换过光影和模糊，不能作为纯净对照。
- [ ] 现有 `FrameSegments` 的 `DRAW` 不含桥接和 GPU 完成；在诊断候选中基于同一帧号记录 panel、`GlStateScope` 捕获、上下文准备、实际绘制、flush/submit、状态恢复以及导航事件。另测构造、配置读取与首帧。先确认当前使用的 AtomLib 源码与运行 JAR class 对应。使用真实 API 探针核实查询参数及返回值；不得同步 `glFinish` 污染日常帧耗，GPU 时间采用非阻塞查询并说明可用性与延迟。
- [ ] 给新增计时做边界、嵌套、异常与关闭时零开销路径测试；在有代表性的绘制路径自证事件与帧号对齐。使用已有 `com.atomchat.UiFrame` JFR，补充栈、分配和 GC，以时间对齐判断长帧；记录场景切换，不将不同滚动窗口的分位数相减。
- [ ] 同场景交替 A/B 点阵（保留画面对应关系）以及由分段结果指向的其他热点；报告整帧 p50/p95/p99、各段、GPU 与视觉差异。只在证据证明因果后修改对应根因；点阵约 0.9–1.3ms 的混合窗口中位数只说明它是热点，不证明它是主要瓶颈。每个改动重复同场景验证，并保留无效实验及限制。
- [ ] 若缺少用户实机样本，准备可审阅诊断包与明确操作，不把根因、帧率提升或游戏内效果标记为已完成。等待用户自行启动并提供采样，再继续归因和回归。

## 4. 全链验证与交付

- [ ] 逐项运行回归测试，再运行三端全量测试和构建、`python tools/verify_targets.py`、对应 `tools/verify_jars.py --profile atomlib-consumer`；完整日志落盘并报告测试数、失败、跳过、构件哈希及实际依赖来源。
- [ ] 真实图形离屏验证通过后，由用户自行在目标实例完成无光影性能、切页、亮暗背景和字体外观验收。保留旧构件备份与可回退路径。
- [ ] 非平凡修改完成后委派独立 code-reviewer 复审全部 diff，核对三端孪生、字体生命周期、GL 状态与视觉行为；修正并复核。报告哪些是已证实的根因、哪些仍待实机，以及所有变更和未完成事项；不凭构建通过宣称性能或视觉已修好。
