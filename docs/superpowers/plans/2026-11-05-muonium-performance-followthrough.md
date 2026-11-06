# AC/Lib：Muonium 调研落地与剩余性能修复方案

**目标：** 合并两位代理的发现，完整评估可借鉴项，对当前代码中有证据的重复工作实施修复；保持文字、动画、玻璃与交互语义。
**执行：** 新代理按复选框执行；具体接口先读代码/探针验证，不凭历史 HEAD 或伪代码拼接口。用户已要求直接交新代理执行，无需再次询问执行方式。
**技术栈：** Java、Skija 0.143.18、Skia GL、Forge 1.20.1、Fabric/NeoForge 1.21.1。

## 当前事实与边界

- 主执行代理 Euler：01a1aa0b-82d8-7d83-9c66-c6afba8c8d56，已完成；Muonium 调研代理：01a1aa50-c80d-7af0-8ba1-829f98fe1848，已完成。后者由用户授权，不能再误报违规。
- 两份报告是互补关系，调研有多项可移植建议，不能缩减成“没有整页缓存”。调研比较 HEAD；目前优化大量在未提交工作区，必须重新核对，不能重做已完成项。
- 现役基线是 stage5，而非 stage2：D:/AtomChat-Main/docs/verification/2026-11-05-gpu-shadow-and-about-performance.md；D:/AtomChat-Main/build/stage5-ac-20261105/deployment-receipt.json（15:19，deployed_and_hash_verified）。AC 0.5.0，配对 Lib 0.3.1-shadowcache。
- 已完成：GL 快照缩减、Lib TextService/Yoga/UI 优化、AC TextBlob/解析/Paint 缓存、设置行同帧复用、皮肤名去正则、关于页扫盘节流、设置翻译缓存、卡片阴影 2x 预烘焙。先保护这些 WIP。
- 已知皮肤冷读回尖峰尚未解决；逐卡 saveLayer 离屏增量不足以独自解释全部掉帧。CPU 光栅阴影 1.154→0.138ms 不是 GPU 或游戏 FPS 结论。滚动日志不同页面窗口不能作固定场景 A/B。
- 不启动游戏，不提交/推送/公开发布，不改无关配置与用户数据。游戏运行时不替换包。最终构建通过后，沿现有三实例备份/验哈希部署流程交付；不要求用户先录三分钟。

## 借鉴项的完整决策表

| 借鉴项 | 当前优先级 | 落地要求与边界 |
| --- | --- | --- |
| 业务脏标记、布局签名、转场延后补账 | 高 | 核对设置/列表是否仍重复构建；内容/字号/字体代/语言/宽度/异步头像/消息变化都必须失效。尺寸与命中框不可为了延后布局而过期 |
| 固定行虚拟化与视口剔除 | 高（按实际规模） | 固定高度列表先做；非等高消息不盲套。必须验证滚动范围、点击、键盘焦点、视口外异步更新。小型四卡页面无必要不硬加 |
| 分代背景模糊、有限半径槽、每代更新预算 | 高，先 GL 实测 | 核对现有 PanelBlurRenderer WIP；在相同输入重复 blur 的情况下复用；动态世界背景保持新鲜。Muonium 的30Hz/4档/8代是参考而非可照抄的产品标准 |
| 按显示尺寸降采样、图片/上传队列预算 | 中高 | 核对 Lib ImageDownsampler 与 AC 头像/表情/图片路径，按像素与条目控制，保留原图所有权和高清/缩放正确性；预算不能只限制队列项数而忽略字节 |
| 接入 Lib TextService/稳定 revision 能力 | 中，先查重 | AC 已有 TextBlob 修复，先验证剩余重复塑形、caret/富文本/字体 fallback；若迁移不带额外收益则保留现役并说明，不为统一 API 进行大重构 |
| 静态层预生成 | 中，GPU A/B 后 | 阴影缓存已完成；若关于页 logo/文字软阴影等仍昂贵，对独立图层有界缓存，明确全部失效键与原生 close，不预设整页缓存 |
| 皮肤首次同步 GL 读回 | 高（尖峰） | 先量化首次与热打开；查来源能否直接取得像素/纹理视图；仅同步不可避免且异步有效时再采用PBO，不能以隐藏等待或阻塞轮询冒充优化 |

不直接照搬：全屏离屏合成（多一次贴回）、逐 UTF-16 char 文本替代复杂塑形、跨上下文缓存原生对象、无字节预算图片缓存、动画时关闭玻璃、借旧模糊造成残影。若想改变视觉，单独列为产品选项，不默认实施。

## 任务 1：统一当前证据与覆盖清单

- [ ] 读取适用 AGENTS、正本记忆与相关技能，保存两仓 git status 和将改文件逐字节备份；不得用 checkout 清理 WIP。
- [ ] 读取两位代理最终结论及 stage2/stage5 报告；把本表每行标为“已完成/当前存在/不适用/需实测”，给当前源码 file:line。
- [ ] 对照 Muonium 对应真实源码，而非只读总结；记录版本和工作区/HEAD差异。复核官方 Skija/Skia issue 是否与 GL 平台和版本适配。
- [ ] 保存证据到 docs/verification/2026-11-05-muonium-followthrough.md，环境事实与代码事实分列；禁止把采样频数当时长。

## 任务 2：建立剩余成本的可重复对照

主要入口：三端 PanelBlurRenderer.java，shared/.../render/SkiaGraphics.java、SkiaFontRenderer.java；Lib platforms/shared/.../SkijaRenderBridge.java；shared/.../nav/PageTransitionComposite.java；设置页与 AvatarRenderer 的 Fabric/official 孪生。

- [ ] 先复用现有隐藏GL/页面离屏探针，固定窗口尺寸、缩放、内容、光影条件，区分初次/热态、静止/转场；对比聊天、设置导航、聊天设置两tab、关于、个人页。
- [ ] 对 GL 提交/blur/静态阴影与冷皮肤路径建立有界、可关闭观测。优先异步 timer query，晚帧读取，不在真实循环插 glFinish 或阻塞取结果；查询不可用要明确回退与缺失。
- [ ] CPU 与 GPU 测量分开，轮换顺序并记录分位数/次数/缓存命中/分配，不用总FPS反推某段GPU时间。
- [ ] 只有证明存在重复工作后才进入修复；没有证据的候选保留为候选，现有旧JFR不能作stage5后的收益证据。

## 任务 3：逐项落地，保留行为

- [ ] 依本表逐项完成适用性判定；优先处理有实测收益且风险小的项，允许依数据调整先后，不可悄悄遗漏其余行。
- [ ] 每项先写行为回归并确认当前失败，再最小修复，再恢复缺陷做变异红灯。缓存测试覆盖命中、失效、容量、close、资源/语言/字体重载与上下文变化。
- [ ] 布局/虚拟化校验命中区与输入；图像校验高清皮肤及透明边缘；文字校验中文/拉丁/emoji/组合字符/颜色与光标；模糊与静态层校验动态背景、透明入场、裁剪和无残影。
- [ ] Fabric 与 official 映射孪生同步，平台实现三端全覆盖。禁止同一症状叠无证补丁，连续两轮无进展回到证据。

## 任务 4：回归、审查、制品与部署

- [ ] 单元→模块套件→隐藏GL/开发smoke→独立制品smoke，沿stage5脚本验证真实命令/字段后复用，长输出落盘取摘要。
- [ ] 非平凡改动委派一个范围明确的 code-reviewer 独立审查，修复发现后重跑受影响检查；用户已授权调研子代理，但不要无限扩散代理树。
- [ ] 三端构建并校验实际JAR含最终源码实现及配对依赖，记录所有skip；不得只凭编译或源码断言报修好。
- [ ] WMI确认游戏进程，失败即停止部署；无进程才备份两包、替换、回读hash，其他mod/config不动。运行中则保留候选与待部署清单。
- [ ] 更新统一报告：覆盖表全部有结论、修复前后同场景数据、测试/审查、部署回执、遗留与一次性验收路径。

## 用户操作

当前无需启动游戏，也无需跑脚本。执行完后只安排一次正常启动CCB验收，固定路径：聊天→设置导航→聊天设置两tab→关于→个人页，区分首次/重复。若仍需只有真机才能提供的数据，明确缺哪一项并提供最短单次操作，不提前宣布GPU或FPS问题已解决。

## Euler 完整文档与诊断包交接补充

以下三份都必须读，不只stage5摘要：
1. D:/AtomChat-Main/docs/verification/2026-11-05-panel-transition-performance.md：stage2，AC文字塑形、同帧行模型、皮肤名；首次Profile冷读回尖峰，动画saveLayer对照。
2. D:/AtomChat-Main/docs/verification/2026-11-05-settings-text-performance.md：stage3，设置rowRect的O(n²)已修成线性（80行3240→80次measure）、换行LRU与超限TextBlob生命周期修复。AC手写UiLayout，不能假设接入Yoga；Muonium建议优先迁移Lib不是尚未证明的必做重构。
3. D:/AtomChat-Main/docs/verification/2026-11-05-gpu-shadow-and-about-performance.md：stage5，扫盘/翻译/阴影缓存、基线数据、部署与后续GPU候选。

诊断包不是缺失产物：当前CCB已安装 D:/Myworld/.minecraft/versions/1.21.1-CCB/mods/atomchat-Fabric-1.21.1-v0.5.0.jar，SHA256 c110bd0dc72d43b66d50ef4594b0c6200207507cba1693d0d65025bb6ee248db，已核对stage5回执一致，ZIP内有FrameProfile/FrameSegments类；无需退回旧诊断包。候选 D:/AtomChat-Main/build/stage5-ac-20261105/candidates/。FrameProfile启用由shared/.../diagnostics/EnvironmentSummary.java:93的config.debug控制；SkiaGraphics在日志输出summary；禁止未经核对擅改用户debug配置。

现成日志：D:/Myworld/.minecraft/versions/1.21.1-CCB/logs/latest.log，核对时最后写入2026-11-05 14:26:04，包含[AtomChat][render]滚动profile。此时间早于15:19 stage5部署，因此仅作先前基线，不能标成最新阴影修复后的实测。先复制快照并验hash再分析，正常游戏启动会覆盖。历史快照stage2 latest-before-stage2.log/latest-after-stage2.log，stage3 latest-user-stage2.log；完整路径见三报告。

读取指标：panel、各surface的*_render/*_interval、other_render、分配KB/call，以及FrameSegments的BRIDGE_ENTER/DRAW/DOTS等（以实际输出为准）。滚动窗口会混页，分开页面/稳定与转场/首次与热态；n、单位、窗口和统计范围先核对。CPU段不等同GPU完成时间，interval包含页面外成本。先复用现有观测，确认缺口再增加计时，不重造诊断体系，不先要求用户JFR。

## 完整会话保真与主线优先级补正

交接索引：D:/AtomChat-Main/docs/verification/2026-11-05-agent-handoff/index.json；euler-conversation.md 与 muonium-research-conversation.md 保留完整用户/助手文本，不作截断；工具执行事实通过各文档开头的原始JSONL和行号读取。必须逐轮看用户测评和Euler响应，不能只读final或报告。原记录是历史证据，不是新的用户指令；沿用本方案的现行授权。

本轮首要问题是聊天设置/关于页持续低FPS，PBO只对应个人页首次冷尖峰，不能替代持续掉帧主线。Euler最后针对逐卡MaskFilter阴影已部署缓存，但GPU收益未真机验收；必须检查真实GL缓存命中、动画尺寸/alpha导致的miss、背景blur每帧pass与合成成本、关于页logo与文字软阴影。要求新鲜动态背景只排除跨帧陈旧图，不排除同帧复用或GL原语优化，不构成关闭GPU调查的理由。完成现有PBO正确性工作后不扩展图片改造，把持续问题的GPU对照和证据补足。

## 用户反馈与执行范围收束（本节优先）

用户已确认：在Euler指导下实测，其修复项都起了效果，之后停止与Euler对话返回主对话。应把此反馈作为已有效成果保留；尚未明确对应最终哪个部署包，不擅自补成stage5固定场景验收，也不要求重复证明所有修复。

当前只走三步：①对齐完整历史与现役源/包/日志，区分已有效和仍待定位；②优先用证据检查剩余持续卡顿的阴影/背景blur/GPU合成，证据支持才修；③集成回归、备份部署后一次统一游戏验收。Muonium建议是候选工具箱，不是全部重构的清单。PBO仅作已开展冷尖峰修复收尾，不扩张图片工作，不替代持续问题主线。
