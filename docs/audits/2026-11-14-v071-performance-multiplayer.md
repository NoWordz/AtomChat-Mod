# AtomChat 0.7.1 性能与多人服务器异常只读审计

日期：2026-11-14（Asia/Shanghai）。审计模型：gpt-6-astra。基线：`20df2ab83ef4dcc1ec7c673a3f1a9e42690ca252`，同时阅读当前未提交的 0.7.1 源码，尤其是新增设置页签 `bodyVariant`、浮窗输入接线及已有上传/渲染路径。行号按本轮工作树记录；主流程仍有并行 UI 工作，应以符号名辅助定位。

**结论：发现可行动的上传生命周期、静默失败和主线程 I/O 问题；没有证据把“多人服无法输入、无法发图、薄暮锁定、上下栏发白”认定为同一根因，也没有实机复现这一组异常。** 性能部分区分已确认的工作量与待测的瓶颈，下面的优先级是修复/验证顺序，不是已经测得的 FPS 排名。

本轮唯一写入是本报告。没有修改产品代码或依赖，没有构建、全量测试、启动游戏、部署、发布或派子代理。已读项目 AGENTS/UI_CONTRACT、共享记忆最新 AtomChat 节及 code-review/hunt 技能；没有读取 `~/.agents/AGENTS.md`。主流程报告 1–4 项及三端 test/build 通过，这是主流程验证，不作为本审计对多人服问题的运行时证明。描边、尺寸、G2、点阵视觉覆盖不在本报告范围。

## 证据等级

- **确定代码行为/缺陷**：有完整调用链或条件判断支持；不等于用户的故障现场触发了该分支。
- **可构造时序**：能给出具体任务交错和预期错误结果，但本轮没有运行游戏或调度器复现。
- **性能候选/风险假设**：工作确实存在，耗时、出现频率或与报告症状的关联尚未实测。

## 优先处理的正确性问题

### F1 / P1：上传任务没有连接代次，断线还会触发外部图床回退

**等级：确定的回退行为 + 可构造的跨连接时序；未复现用户故障。**

证据：

- [MediaCompanionClient.java:67](D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/net/MediaCompanionClient.java:67) 的 join/disconnect 清 `state` 并调用 `failAll("disconnected")`；[250](D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/net/MediaCompanionClient.java:250) 把该原因送入每项 `onError`。
- [ImageUploadPipeline.java:175](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/image/ImageUploadPipeline.java:175) 对所有服务端上传错误无条件调用 `uploadToUguu`，包含断线取消。
- [MediaCompanionClient.java:132](D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/net/MediaCompanionClient.java:132) 的 IO 任务读取文件之后才调用 `upload`；后者在 [142](D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/net/MediaCompanionClient.java:142) 重新读取当前静态 `state`，在 [289](D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/net/MediaCompanionClient.java:289) 投递到当前客户端连接发送，没有捕获连接身份或代次。
- Fabric 同形链位于 `platforms/1.21.1-fabric/src/main/java/com/atom/chat/net/MediaCompanionClient.java:71,126,136,162,253`；NeoForge 位于对应文件 `:69,124,134,160,252`。不是 Forge 独有。

触发时序：在服务器 A 发起上传 → 文件仍排队/读取 → 离开 A、进入支持托管的 B → 原 worker 读取完成 → `upload` 看到 B 的 state → 原文件被送到 B。另一条更直接的路径是 A 的上传已经入 `uploads`，断线后 `failAll` 直接开始外部上传。清理 map 无法取消尚未入 map 的任务和已投递的 sender。

影响：旧任务可能继续传输到不再对应用户操作的目的地；旧回调可能在关屏后执行。它可解释部分“换服后上传不正常”，不能解释薄暮或白屏。

建议：请求创建时捕获 connection epoch，在排队、读文件完成、真正发包、结果回调四个边界核对；离开服务器使旧代次失效。把取消/断线与可重试的服务端错误区分，取消不启动另一种上传。保留用户原本允许的正常失败回退策略。

验证：用可控制 executor 与假连接 A/B 暂停读取和发送，覆盖读前断线、读后发前换服、结果回调前关屏；断言旧任务不向 B 发包、不触发外部 uploader，且回调最多一次。三端做同一时序用例；再由授权实机验证一次真实换服。

### F2 / P2：上传结果追加到“回调当时”的草稿，且超长或失败没有可见反馈

**等级：确定代码行为；跨会话追加是可构造逻辑；超长路径由现有测试明确固定。**

- [ImageUploadPipeline.java:211](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/image/ImageUploadPipeline.java:211) 上传完成才组装 CICode，在 [226](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/image/ImageUploadPipeline.java:226) 读取当前 `composerEmpty` 并追加；没有记录最初导航目标或草稿 ID。
- [AtomChatScreen.java:1359](D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java:1359) 的 host 写入当前 parked draft 或当前 composer。导航会保存/切换草稿（同文件 `:1703,1725,2184`）。因此在公共频道上传、完成前切到私聊，结果可以进入当前私聊草稿。这里不自动发送，但容易后续误发。
- [ComposerInput.java:195](D:/AtomChat-Main/shared/src/main/java/com/atom/chat/ui/ComposerInput.java:195) 仅当总长度不超过 256 才 setText；否则仍获取焦点并静默返回，host 无返回值通知上传方失败。现有 `ComposerInputTest.appendingStopsAtTheChatLimit` 明确测试丢弃行为，上传测试只断言 host 收到 append，未组合验证容量。
- [ImageUploadPipeline.java:204](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/image/ImageUploadPipeline.java:204) 外部上传失败只记录 warn、清 `imageUploading`，不调用现有 toast/hint。普通用户会看到上传提示消失但没有图片。

只读算术探针：现有测试样例 `[[CICode,url=https://uguu.se/abc.png,name=shot.png,w=120,h=80]]` 长 63；已有 200 字符草稿加空格后共 264，必被当前条件丢弃。即使上传已成功也如此。此探针只计算条件，未执行 Java/网络。

建议：让上传持有原始会话/草稿身份，结果写回该草稿；关屏后保存为该草稿的待用附件结果。追加 API 返回成功/容量不足/失效等明确结果；容量不足保留结果并提示用户，不截断 CICode。外部失败显示一次可理解的失败反馈。并发上传用计数或请求集合表达 uploading，避免第一项完成就清除其他项状态。

验证：复用 `ImageUploadPipelineTest` 的可注入 uploader/renderHop，组合真实 ComposerInput，覆盖 256 边界、长文件名、上传中切频道、关屏、两个上传乱序完成及外部错误。不要把“上传回调执行过”当作“草稿中确实出现完整图片代码”。

### F3 / P2：媒体任务上限没有覆盖排队阶段，分片集中占用客户端线程

**等级：确定调度结构；造成卡顿的具体耗时待测。**

[MediaCompanionClient.java:128](D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/net/MediaCompanionClient.java:128) 在提交 IO 任务前检查 `uploads.size() >= 8`，但直到 worker 读取完才在 `:158` put。连续请求可在 map 尚空时全部通过，单线程 executor 的队列没有显式上限；worker 内也没有重新预留名额。[ImageUploader.java:50](D:/AtomChat-Main/shared/src/main/java/com/atom/chat/image/ImageUploader.java:50) 外部上传同样使用无显式队列上限的单线程 executor，每项 HTTP 请求可等待 60 秒。

服务端分片在一个客户端线程 Runnable 内全部发送（Forge `:160–168`，Fabric `:165–173`，NeoForge `:162–170`）。源码 `MediaIds.CHUNK_BYTES=24*1024`；允许的 16 MiB 文件需要 683 个 chunk，加 start/finish 共 685 次发送。这个数是只读算术结果，不是耗时实测。

影响：快速连续贴图时排队等待与 retained task 增长，集中编码/复制/发包可能形成帧尖峰；未证明它导致常驻不能输入。

建议：入口按请求预留有限名额和总字节预算，完成/取消释放；整合 F1 的取消机制。限制每 tick 的分片数/字节数，保持 start → 有序 chunk → finish，避免一次占满主线程。外部上传明确队列满/正在排队状态。

验证：假慢读取下连续提交超过上限，断言接受量受限；检查一次 tick 最大字节量与包顺序；测 1/8/16 MiB 文件发送时主线程 p95/p99，不用总上传时间代替帧尖峰。

## 性能确定工作量与候选

### F4 / P2：主题列表缓存命中仍重复访问文件系统

**等级：确定热路径 I/O；未测本轮耗时。**

[AppearancePresetStore.java:138](D:/AtomChat-Main/shared/src/main/java/com/atom/chat/theme/AppearancePresetStore.java:138) 的 `list()` 每次先 `allowPersonalPath(dir)`，之后才返回缓存。[DataMigration.java:140](D:/AtomChat-Main/shared/src/main/java/com/atom/chat/util/DataMigration.java:140) 的 `noLinks` 遍历祖先，逐层 `Files.exists/readAttributes`。

主题行每帧 `advanceStrip → stripMaxScroll → themeCardCount`、`drawThemes` 读取 custom、绘制 add 卡计算 count 均会调用 list；鼠标位于卡片区域时 `stripCardAt` 又调用一次。相关入口：[SettingsSectionPage.java:2065](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/settings/SettingsSectionPage.java:2065)、`:2284,2379,2401,2446`。因此可见行通常至少 3 次，卡片命中路径 4 次目录祖先检查。先前已经消除“每卡重复 hover 检查”，但没有消除每帧 I/O。

建议：一帧只使用一个不可变 preset 列表快照和 count；将列表刷新/磁盘校验移出 paint，按显式保存/删除/刷新更新。真正读写个人路径时继续执行路径安全检查，不能简单删掉 `noLinks` 来换速度。

验证：复用 `ThemeHoverWorkTest/ThemeHoverBenchmarkTest`，增加文件属性查询计数或 JFR FileRead/文件系统调用证据，目标是稳定 repaint 不触发目录扫描/属性检查；保留写入时链接路径拒绝用例。比较鼠标在卡片内外、冷/热缓存和慢盘，报告 CPU 耗时而非直接推断 GPU/FPS。

### F5 / P2：打开面板、历史载入和缓存容量读数仍有同步 I/O

**等级：确定调用链；主线程阻塞量取决于文件/磁盘。**

1. 每次构造新面板同步 [AtomChatConfig.reload](D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java:1827)。[AtomChatConfig.java:699](D:/AtomChat-Main/shared/src/main/java/com/atom/chat/config/AtomChatConfig.java:699) 读配置、合并 personal data 后，只要 load 成功就 `save(config)`，即使内容没有变化。保存链 [PersonalDataStore.java:50](D:/AtomChat-Main/shared/src/main/java/com/atom/chat/config/PersonalDataStore.java:50) 重读文件、写临时 personal JSON、替换并回读；配置文件再临时写入/替换。因此“只是打开聊天”也有同步读写与序列化。
2. 开启历史记录后，[ChatHistory.java:78](D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/history/ChatHistory.java:78) 在 join 调用清理和加载；`:189–219` 使用 `readAllLines` 并完整解析后才截断公开消息到 500 条。保存时 `:146–174` 在投递 SAVE executor 前已遍历/序列化全部公开与私聊，再截断。写盘异步不代表整个持久化链异步。Fabric/NeoForge 同形逻辑在各自 `ChatHistory.java:79,147,191`。默认历史关闭，不能把它解释成所有用户必有的问题。
3. [SettingsSectionPage.java:835](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/settings/SettingsSectionPage.java:835) 读缓存容量；[ImageLoader.java:528](D:/AtomChat-Main/shared/src/main/java/com/atom/chat/image/ImageLoader.java:528) 的 1 秒 TTL 过期后同步枚举文件并逐个取 size。缓存容量页可能有周期性 I/O 尖峰。

建议顺序：先将配置持久化改为“迁移/内容变化才写”，保留重新打开读取手改配置的约定；然后后台刷新容量读数；历史文件做有界读取/后台解析，主线程仅发布结果，结果携带世界代次，合并期间新消息。不要把 Minecraft Component 等对象无约束地搬到 worker；先转成受控不可变 DTO。

验证：计数连续打开未变配置的写入次数；慢文件系统注入下记录 CONFIG_RELOAD/JFR；历史 500/5000 行、损坏尾行、载入中换服和清空历史；容量页在目录不可读时继续可交互。不能用“移到线程池后能编译”代替并发行为测试。

### F6 / 测量优先：导航每帧录制命令，切页时叠加离屏层；0.7.1 页签扩大了触发范围

**等级：确定渲染工作量；尚不能认定为首要瓶颈。**

[PageTransitionComposite.java:99](D:/AtomChat-Main/shared/src/main/java/com/atom/chat/nav/PageTransitionComposite.java:99) 即使稳定帧也创建 PictureRecorder、运行 arriving body、生成 Picture，再 `:131` playback；动画帧 `:112` 额外录制组合，在 `:141` 对离开/进入页面使用 saveLayer。按设计它保留当前精确画面供随时打断，不能直接认为整段“多余”。新 `bodyVariant`（`:47`）让设置内部 tab 也进入此路径，故审计包含了未提交 0.7.1 改动。

卡片入场还会按 alpha 创建各卡片层（[SettingsSectionPage.java:1531](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/settings/SettingsSectionPage.java:1531)、`:1552,1631,1649`）。因此 Tab 的页面级过渡与已有卡片入场在同一时段产生多层合成；不是两个页面都在重跑业务逻辑，离开页回放的是冻结命令。

建议：先分别测 steady、首次切换、快速连续打断的 command count、JVM/native 分配、GPU 时间。优先缓存稳定子树/绘制命令，保留动态 caret、hover、GIF；只有 A/B 证实稳态录制成本明显时，才设计按 dirty generation 更新的快照。不要把整页冻结后丢掉动态图像，也不要为了性能退回不可打断动画。已有 `compact` 仅解决打断命令超预算，不能当作稳态录制已消除。

验证：复用 `PageTransitionCompositeTest` 的中途打断逐像素连续性，加入有限次数快速切页后的 native 内存/命令数回落检查；两种导航样式、动画关闭、resize/context 切换都保留。此处推荐测量与试验，不承诺优化后的帧率。

**测量缺口**：[AtomChatScreen.java:4063](D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java:4063) 的 chip 切换没有 `FrameSegments.navigation` 事件，而该事件才更新 page 首次/重复标记；[FrameProfile.java:80](D:/AtomChat-Main/shared/src/main/java/com/atom/chat/diagnostics/FrameProfile.java:80) 使用固定 90 帧判定过渡窗口。当前 `PAGE_RENDER` 桶还合并了过渡和稳态，`page_phase` 只是摘要时的标签。不能拿现有 page 桶直接声称测到了“Tab 动画耗时”或“纯稳态耗时”。建议让真实 motion 状态/variant 驱动诊断标签，仍复用现有诊断机制。

### F7 / 测量优先：透明面板每帧五次全采样尺寸模糊，已有 GPU 计时应先复用

**等级：确定算法工作量；没有本轮 GPU 实测。**

[PanelBlurRenderer.java:36](D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java:36) 设置 5 次 Kawase；`:541–584` 在正常 render 中 `full=true`，一次采集 blit 后按捕获区域物理尺寸跑 5 pass。主屏 [AtomChatScreen.java:2274](D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java:2274) 只有诊断 D 路径在稳定时复用背景，产品默认 A。代码中的“on-demand/每两帧”旧注释不能当作目前存在的优化。

只读算术例：900×1500 捕获区域、144 FPS，五个 pass 合计约 9.72 亿输出像素/秒，未计纹理多次采样、blit、圆角合成和边缘扩展。这个数仅描述工作规模，不是 GPU 时间或丢帧证明。

建议：复用 [FrameSegments.java:16](D:/AtomChat-Main/shared/src/main/java/com/atom/chat/diagnostics/FrameSegments.java:16) 已有的非阻塞 GPU query 和 `gpu_panel` 行，结合 Truth3 的 A/B/C/D 做同场景对照，避免新增同步 `glFinish`。若 GPU 证据支持，再试降采样模糊或分级刷新；以移动背景、开合、光影和像素质量验证决定。不要仅凭 CPU 提交耗时很小就排除 GPU，也不要直接把诊断 D 变成默认产品行为。

点阵仅从性能角度补充：`DotTexture` 已有 Picture 缓存、大面积 4× raster 图缓存及数量/字节上限（`shared/src/main/java/com/atom/chat/render/DotTexture.java:34,202,247`）；缓存 miss 的烘焙仍同步发生在绘制路径，几何变化可能带来冷尖峰。这里没有重做标题区域覆盖/G2排查，也没有把“有缓存”说成所有页面已无成本。

### F8 / 后续候选：图片下载队列与处理后磁盘缓存有可避免的工作

**等级：确定实现行为；是否值得优先优化待测。**

[ImageLoader.java:117](D:/AtomChat-Main/shared/src/main/java/com/atom/chat/image/ImageLoader.java:117) 是固定 3 worker、没有显式排队容量的 executor；`:288–289` 只按 URL 去重。滚动跨过大量不同图片后，离开视口或服务器不会撤销已有 queued load。内存缓存有上限，不等于待处理任务也有上限。

同文件 `:351` 对静态图即使命中磁盘也重新 encode 并写回，`:439` 随即 trim 扫目录。已有处理后 WebP 的正常读命中也会经过此链，虽然 worker 中执行，仍增加 I/O、编码和其他图片等待。

建议：为请求加有界队列和可见优先级，离开服务器使过期托管请求失效；区分需要迁移的旧磁盘格式与现役处理后格式，后者只读/更新必要元数据，批量/合并 trim。先测高图片量和连续滚动场景，不以静态列表理论上限替代任务队列指标。

验证：用现有 `ImageLoaderTest` 注入阻塞 fetcher，断言 queued/pending 上限及过期请求不挤压当前图片；处理后 disk hit 不发生重新编码写入；静态图、GIF、旧缓存迁移和失败 TTL 保持兼容。

## 对多人服四个症状的关联判断

| 症状 | 本轮证据与关联强弱 | 下一次最小验证 |
| --- | --- | --- |
| 无法输入文字 | **未定位。** `InputGate` 对 key/char 不做面板动画门禁，故“页面动画把所有键盘锁住”不符合当前代码。真正可消费字符的是 ClosingState、教程/确认、模态框、根页及原生输入焦点链。私聊离线/屏蔽会主动 blur，但不适用于公共聊天全局失效。 | 同一故障帧记录当前 Screen/NavPage、首个消费事件的 handler、composer focus、EditBox focus、只读原因、modal/tutorial active。分别测试 ASCII 与 IME；不记录聊天正文/逐字输入内容。若事件未到 AC，再查输入/IME/第三方 ChatScreen 接入。 |
| 无法发送图片 | **有强代码候选，现场关联未验证。** F1/F2 可以分别造成跨连接任务或“成功但草稿无图”；外部失败无 toast 可伪装成无反应。 | 上传 request ID 串联起点/目标会话、hosting state、每个 transport 结果、追加结果。区分未开始、正在排队、失败、成功但追加拒绝。 |
| 薄暮卡片锁住 | **未定位、目前关联弱。** `ThemeService.apply` 是本地一次性赋值，已检查的客户端网络路径没有强制写 dusk 的通路。当前名称也可能只是最后应用的 preset 标签。保存失败可在重新打开时恢复旧配置，但不足以解释同屏始终不可切。 | 对一次点击记录 press/drag/release 是否完成、card index、apply 前后 themeName/颜色、saveChecked 结果、preview transaction 是否 active；若值变化但画面不变则转渲染路径，若 release 未到则转输入链。 |
| 上下栏和内容全白/不渲染 | **未定位，渲染/依赖/环境假设。** AC 通过 AtomLib `MinecraftRenderBridge` 画帧，自己的 blur 失败会关闭 blur 并用实色 fallback，不能由此推出所有白色问题都来自 blur。 | 留存首次异常/环境摘要、实际加载类来源、GL renderer、context/FBO 身份、画面症状时间；受控 A/B 关闭 blur 和关闭实验特效，检查世界画面是否也异常。单纯 Skija natives 探测成功不证明后续 GPU 帧正确。 |

定位入口：`shared/src/main/java/com/atom/chat/ui/input/InputGate.java:100`；`InputRouter.java:65`；Forge `AtomChatScreen.java:942,3533,3971,4280,4304`；`TutorialController.java:136,148`；`NavStackController.java:179`；`ThemeService.java:301`；`SettingsSectionPage.java:2209`；`layers/mapping/official/src/main/java/com/atom/chat/render/SkiaGraphics.java:54`。这些入口应增加短时、有界、按请求/帧关联的诊断；本轮没有改入诊断代码。

教程的全局 sandbox 确实会截获发送/上传，但当前正常退出和 HUD 子屏放弃已有 reset/leave 接线，**不能仅看到 static active 就认定存在泄漏**。同理，仓内旧 `GlStateUtil` 的复杂 GL 查询不是当前 SkiaGraphics draw 的实际状态保护实现，不能把未被调用的旧代码当作现役热点。

## AtomLib 版本差异：事实明确，因果未建立

主流程补充的只读环境事实（本审计没有再次扫描或替换实例 JAR）：

| 实例 | 主流程观察到的独立库 JAR |
| --- | --- |
| Forge 1.20.1-main | `atomlib-Forge-1.20.1-v0.4.0.jar` |
| Fabric 1.21.1-CCB | `atomlib-Fabric-1.21.1-v0.4.0.jar` |
| NeoForge Mechanomania-航空学 | `atomlib-NeoForge-1.21.1-v0.3.1-shadowcache.jar` |

仓内 [versions/atomlib.json:4](D:/AtomChat-Main/versions/atomlib.json:4) 的编译输入为 0.4.0，`compatibility_range="*"`。这表示加载器的版本范围不会替我们排除旧版本；**不等于已经证明旧库 API/ABI 不兼容**，也不能仅凭文件名断定实际加载了哪份同名类。构建所用 0.4.0 的测试通过，不能自动覆盖 Neo 实际旧库组合。

关联判断：对 NeoForge 的白屏、原生资源生命周期和样式差异是**中等排查价值的环境风险，因果证据仍弱**；对无法输入/发图目前没有直接调用链证明；对“薄暮锁定”没有特定关联。如果相同现象也在确认加载 0.4.0 的 Forge/Fabric 出现，Neo 的这一个版本差异无法解释所有实例。

建议先从启动日志/类来源确认实际加载版本，再在单独授权的复现阶段比较相同 AC、相同配置、相同服务器下的库组合。可按实际依赖 API 明确支持区间并给出不兼容提示，但没有兼容性证据前不要随意把范围锁成一个版本。本轮不扩审库源码、不替换库、不部署。

## 建议执行顺序与验收

1. **先修 F1/F2**：连接/草稿身份、取消语义、追加返回值与失败可见反馈。收益明确，并能让后续“发图失败”有可区分的结果。
2. **同时建立最小故障观测**：首消费 handler、上传 request ID、主题 apply/保存结果、实际 AtomLib 来源。用于用户下一次自然复现；不要求用户先反复换包试方向。
3. **修 F3/F4/F5 的确定结构问题**：有限排队、分片预算、paint 不触盘、未变配置不重写、历史与容量后台处理。每项只运行受影响测试，再统一实机验证。
4. **最后依据数据选择 F6/F7/F8**：同场景测冷/热、过渡/稳态、CPU/GPU/分配/队列；达到帧时间改善且视觉/输入无回归后再保留方案。

实机性能矩阵建议固定场景与镜头，记录实际分辨率、GUI/UI 缩放、加载器、库来源、光影、刷新率：聊天 0/500 条、含图列表、设置主题行鼠标内外、连续 Tab 打断、进服/换服、上传。收集帧间隔 p50/p95/p99、CPU draw、现有 gpu_panel 的有效样本及 dropped/expired、JVM 分配与 native 占用；单次首开与稳定段分开。60/144 FPS 预算约为 16.67/6.94 ms，应比较整帧间隔，不能用局部离屏 CPU 测试宣称 FPS 提升。

## 本轮验证边界

- 执行了只读 Git 状态/差异检查、源码与现有测试阅读、三端关键媒体/历史路径比对，以及内存中的长度/分片/像素工作量算术探针；没有产生测试输出文件。
- 没有运行 JUnit、Gradle、GPU 探针、网络上传、游戏或多人服务器；上述“验证方法”均是下一轮建议，不是已通过结果。
- 现有测试支持“当前有意丢弃超长 append”等代码事实，但本轮未重跑，不能报告新的通过数。原有 0.7.1 三端结果和主流程 1–4 新结果均不代表多人服问题已复现或修复。
- 未证明薄暮、输入失效、白屏之间存在共同因果；未将旧 AtomLib、教程状态、第三方渲染器或服务器插件判为元凶。


## 实施后状态（2026-11-14 21:56）

用户随后授权落实建议。F1/F2/F3/F4/F5/F8的明确缺陷与结构问题已实施并验证，F6/F7完成限定CPU/GPU测量与motion诊断分桶，未据无显著证据改变动画/画质。集中独审与后续有限复核的R1–R4均关闭；最终三端候选已备份部署，详见[落实验证](../verification/2026-11-14-v071-audit-fixes.md)。原审计正文是实施前的代码证据与建议，不能将其中旧行号和缺陷状态直接当作当前实现。输入失效、薄暮锁定、白屏仍未实机定位；没有将确定修复伪称为它们的共同根因。
