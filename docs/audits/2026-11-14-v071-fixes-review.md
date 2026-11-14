# 0.7.1 审计建议落实：集中独立审查

日期：2026-11-14（Asia/Shanghai）。审查对象为 `D:/AtomChat-Main` 当前未提交工作树；HEAD 为 `20df2ab83ef4dcc1ec7c673a3f1a9e42690ca252`。按符号和现有行号定位，不把上轮已审的 0.7.1 UI 差异重新算成本轮问题。

**当前结论（R2 准入窗口再次增量复核后）：R2 最后一个准入窗口已修，R1、R3、R4 沿用已关闭结论；本报告既定审查范围内未剩有证据的 P1/P2。此为静态复核结论，不等于三端专项、构建或实机检查已通过。** 以下时序均为源码可构造条件，未在游戏或真实网络中复现；它们也不能被当作用户“无法输入、薄暮锁定、白屏”的已确认根因。

已读项目 `AGENTS.md`、`docs/UI_CONTRACT.md`、共享记忆索引及最新 AtomChat 条目、`docs/superpowers/plans/2026-11-14-v071-audit-fixes.md` 和原审计报告。没有读取 `~/.agents/AGENTS.md`。本轮只写本文件，没有改源码、运行 Gradle/游戏/网络、启动原生测量、部署或再派代理。主流程的定向测试、CPU/GPU 测量和待执行的三端全量检查不作为本审查已运行的证据。

## 本次增量复核结论

先前增量已关闭 R1、R3、R4。本次仅再读 R2 准入窗口及其双映射新增回归，其他已审路径不扩审、直接沿用。**R2 的原队列取消问题与随后发现的准入窗口均已静态复核关闭，未确认剩余 P1/P2。** 本次没有运行 Gradle、Java 测试、游戏或网络。

### R1：已修；更正先前的用户意图归因

先前报告写“用户明确接受极长 URL 永久待用”不准确，现撤销此归因。那是主代理曾提出的实施判断，并非用户选择，不能作为不修的授权依据。

当前 [官方 Pipeline:235](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/image/ImageUploadPipeline.java:235) 与 [Fabric Pipeline:235](D:/AtomChat-Main/platforms/1.21.1-fabric/src/main/java/com/atom/chat/image/ImageUploadPipeline.java:235) 保留完整 URL，代码过长时依次省略可选 name、尺寸；仅 URL 的完整 CICode 仍放不进 256 字符时调用 `fail(image_code_too_long)`。[失败处理:212](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/image/ImageUploadPipeline.java:212) 幂等结束请求并取消票据，且在当前连接内提示失败。[PendingAttachments.complete:51](D:/AtomChat-Main/shared/src/main/java/com/atom/chat/chat/PendingAttachments.java:51) 另行拒绝 257+ 字符并移除票据，无法发送的项不再永久挡住后续附件。

已读 `PendingAttachmentsTest.unrepresentableCodesCannotBlockALaterSendableAttachment` 的 255/256/257 边界与后续 drain，以及双映射真实 PNG 长文件名、极长 URL 失败用例。未在本复核执行。现有实现已关闭 R1，不能继续保留“永久待用是用户选择”的旧结论。

### R2：已修；原 epoch 贯穿文件检查与客户端准入

原问题的修正已核对：

- [ImageUploader.java:78](D:/AtomChat-Main/shared/src/main/java/com/atom/chat/image/ImageUploader.java:78) 使用真实 `UploadJob` 与 valid 谓词，在 worker 执行前、读文件后和 HTTP 返回后检查有效性；`cancelInvalid` 对排队任务执行 remove，对运行中任务发中断，`finish` 用 CAS 保证通知与信号量归还最多一次。生产 executor 仍为 1 运行＋7 排队。
- [Pipeline.externalUploader:112](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/image/ImageUploadPipeline.java:112) 的生产实现确实覆写四参数方法，传递有效性谓词；[uploadToUguu:224](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/image/ImageUploadPipeline.java:224) 传入 `request::current`，没有落回只检查一次的默认重载。Fabric 同形实现已读。
- 三端 join/disconnect 六处 `cancelInvalidShared()` 均在 UPLOADS 锁外，避免取消回调再获取连接锁形成反向锁序。关闭面板本身不改变 epoch，保留同连接已开始上传的既有语义。
- 双映射 `Host.active()` 与三端 Screen 的真实 `currentScreen == this` 覆写已读；调用前已被替换的 Screen 会在读文件和预留票据之前被拒绝。新增 `latePickerResultCannotStartAnUploadForAReplacedScreen` 验证了这一前置情况。

上次剩余 P2 是：picker worker 通过 active 后执行文件检查，期间换服，再读取新 epoch 创建请求。该旧时序现在被以下改动阻断：

- [官方 Pipeline.java:161](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/image/ImageUploadPipeline.java:161) 在文件检查前取得 `originEpoch`，只读这一次作为请求身份；[165](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/image/ImageUploadPipeline.java:165) 保留调用方的真实文件检查，检查成功后 [180](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/image/ImageUploadPipeline.java:180) 才通过 renderHop 交给 `admitUpload`。生产 renderHop 仍由 [构造入口:108](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/image/ImageUploadPipeline.java:108) 使用客户端 execute 提供。
- [admitUpload:184](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/image/ImageUploadPipeline.java:184) 先在客户端线程核对 `host.active()` 和原始 epoch；任一失效立即取消。其后才 captureDraft、创建请求、加入请求集合并选择托管/外部 transport。这段准入与 Screen 切换、连接事件处于客户端线程的串行顺序，未把仅有的第二次检查留在 worker 上。
- [请求创建:190](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/image/ImageUploadPipeline.java:190) 明确保存 `originEpoch`，不会读取新身份给旧操作重新贴标。检查期间换服、同连接替换 Screen，或 renderHop 排队期间换服，均在预留票据和选择 transport 前被拒绝。
- Inspector 为包内检查接口，[原构造重载:136](D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/image/ImageUploadPipeline.java:136) 默认仍接 `ImageBounds::inspect`。生产未改成跳过检查的 stub。两映射源码对比只剩 Minecraft 类名映射差异，以上结论同时覆盖 Fabric。

新增测试已静态读取，两份测试对比也只剩 MC 类型映射：

1. [switchDuringFileInspectionCannotReserveOrUploadAgainstTheNewDestination:310](D:/AtomChat-Main/layers/mapping/official/src/test/java/com/atom/chat/image/ImageUploadPipelineTest.java:310) 在真实 PNG 检查后、返回 Inspector 之前改变状态，分别覆盖换 epoch 与同连接 Screen 失活；验证 captureDraft 次数为零、上传集合为空，两个 transport 均为一经调用即失败的替身。
2. [switchWhileClientAdmissionIsQueuedDoesNotRecaptureTheNewEpoch:328](D:/AtomChat-Main/layers/mapping/official/src/test/java/com/atom/chat/image/ImageUploadPipelineTest.java:328) 使用受控客户端队列，文件检查后先不执行准入，再改变 epoch，随后排空队列；验证取消提示、无上传任务且 forbidden transport 未调用。

这是两种测试方法、三个后增准入场景；用例能发现旧实现错误保留/重取 epoch 或过早执行 transport，非仅比较内部 getter。它们不等于真实 MC/HTTP 运行证明。排队用例未单独计数 captureDraft，但当前源码的拒绝分支明确先于该调用。

**R2 当前关闭。** 本次未执行这些用例；此前主流程通报的 60 项、独立 JDK 17 六项和已保存的全量结果，不自动覆盖这三个后加场景。主流程表示将运行三端受影响专项及 build，并保留既有有效全量证据 `tests.json`；本报告不提前宣称新的专项或构建已通过。

运行中 HTTP 中断是逻辑取消和 best effort 传输取消，不等于能收回断线前已经送出的字节；本次未做真实 HTTP 取消验证，不将其另列为新的 P2。

### R3：已修；原 transport 在首次排队之前保留

[Forge MediaPayloads.java:100](D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/net/MediaPayloads.java:100) 的四个 S2C 接收入口改为 network-thread consumer，在首次 enqueue 前取得 `context.getNetworkManager()`；[NeoForge MediaPayloads.java:216](D:/AtomChat-Main/platforms/1.21.1-neoforge/src/main/java/com/atom/chat/net/MediaPayloads.java:216) 单独使用 NETWORK registrar，提前取得 `ctx.connection()`。服务端 handler 的既有注册线程未随之改动。

两端 [receive:207](D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/net/MediaCompanionClient.java:207) / [207](D:/AtomChat-Main/platforms/1.21.1-neoforge/src/main/java/com/atom/chat/net/MediaCompanionClient.java:207) 将原 Connection 交给 [MediaReceiver:10](D:/AtomChat-Main/shared/src/main/java/com/atom/chat/net/MediaReceiver.java:10)，主线程执行时在 session gate 内检查 epoch、原 transport、活动 transport 和当前 transport。即使旧 handler 本身已被延迟到 B 连接后才进入 helper，origin 仍为 A，因此不会仅凭当前 epoch 把旧包归入 B。

已读 `MediaReceiverTest`：延迟 State/Data/Missing/Result、同 ID 新 fetch、旧部分数据不污染新缓冲、helper 晚进入、同 transport 重置 epoch、当前 transport 不匹配和 null origin。首审 R3 可关闭；实际三端联网交付仍未由本复核运行。

### R4：已修；删除与 join/clear 的线性化边界成立

[HistoryLifecycle.java:175](D:/AtomChat-Main/shared/src/main/java/com/atom/chat/history/HistoryLifecycle.java:175) 的 `CleanupPermission.active()` 和 [184](D:/AtomChat-Main/shared/src/main/java/com/atom/chat/history/HistoryLifecycle.java:184) 的 `delete()` 通过 `onMain` 执行最终检查；`delete` 在 `lifecycleLock` 内核对 ticket、enabled、actualWorld，再保护实际当前世界的文件。join、disconnect、clear 同用这把锁更新 epoch/clear generation。其顺序可明确说明：

- join/clear 先完成：旧 ticket 无效，尚未执行的删除被拒绝；即使尚未调用 history.join，只要实际世界已改变，valid 也拒绝旧任务。
- 单次删除先进入临界区：该删除属于切换前的状态，join/clear 排在它之后，不会出现“新世界已经完成 join，旧 permit 又删除它”的首审时序。

三端实际 `cleanupOldHistory` 都逐项调用 permit；枚举、mtime/attrs 和 noLinks 验证仍在 IO worker，只有最终单次 `Files.deleteIfExists` 进入 MC 线程。Forge [153](D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/history/ChatHistory.java:153)、Fabric [156](D:/AtomChat-Main/platforms/1.21.1-fabric/src/main/java/com/atom/chat/history/ChatHistory.java:156)、NeoForge [156](D:/AtomChat-Main/platforms/1.21.1-neoforge/src/main/java/com/atom/chat/history/ChatHistory.java:156) 的调用均已核对。

新增 `HistoryCleanupLifecycleTest` 使用真实文件和受控队列/暂停点，覆盖验证后换服、关闭功能、clear、实际世界变化、删除先执行时 transition 必须等待，以及把当前文件直接作为候选仍不删。它测试了实际副作用与两种排序，非只比较 helper 返回值。本次只读用例，未执行。

**限制：**“一个文件”与“短临界区”不保证磁盘 unlink 实际耗时短。慢盘/杀毒软件/网络目录导致的单文件删除主线程延迟尚未实机测量；不能把当前实现描述为全部文件 I/O 已移出主线程。该限制不推翻上述生命周期顺序，也没有本轮测量证据将它另列为阻断 P2。

## 其余已审范围与测试质量

以下是静态审查结论，不等同于检查已运行或实机验收通过：

- `MediaUploadScheduler` 在排队读盘前预留名额/最坏文件字节，实际读取完成后缩减预算；运行中读盘取消仍保留预算至返回。每 tick 总分片上限与轮转、迟到结果的 ID/epoch、幂等释放均有直接实现与有意义的受控 executor 测试。未另外确认到 P1/P2。
- 三端 Screen 的草稿票据按公共/私聊会话隔离，只有当前 Screen 才会 flush；关屏、同对象 resize、导航切换及搜索停放草稿路径已读。`DraftSessionStore.reset` 会让旧票据失效。当前未确认其他草稿归属缺陷。
- `AppearancePresetStore.list/refresh` 的已初始化暖缓存不再执行路径属性查询；实际刷新、读取、写入仍经过已有路径检查。相关暖缓存测试用拒绝任何调用的 Path 替换目录，能发现回到磁盘查询的回归。未把移除热路径检查等同于取消写入校验。
- `PersonalDataStore` 与 `AtomChatConfig.saveChecked` 当前采用序列化后再解析的 JSON 比较，已读到处理 Gson 浮点表示差异的改动。`ConfigReloadIoTest` 用内容和预设 mtime 区分未变重载与真实设置变更；本审查没有重复运行它，也没有以早先失败宣称当前实现仍失败。
- `ImageLoader` 的 pending token、有限队列、执行前过期检查、连接 invalidate、解码后的发布检查、静态缓存命中不重编码及合并后台维护均已读。保留已有“不立即关闭可能仍被本帧引用的缓存 Image”规则；丢弃尚未发布的静态 Image 有显式 close。新增排队测试覆盖开始前失效，未覆盖全部运行中 interleaving；本轮没有 native 内存测量，不能据此宣称无原生泄漏。
- 历史 DTO 字段不可变，MC Component 转换保留在客户端线程，后台只做有限 JSONL 工作；加载结果按代次/清空代次发布，`prependLoaded` 保留期间新消息、未读和活动会话。测试对字节窗口、坏尾行、Unicode、主线程发布、同 key 重连、清空、合并及实际 store 副作用有行为断言，非仅镜像内部 helper。首审时 R4 的删除边界缺口，已由上节新增 permit 和测试覆盖；其余历史路径沿用首审，不在本次扩审。
- `FaultTrace` 只接收事件类别/消费方/状态，有限流；InputRouter 新调用未改变事件消费顺序。FrameProfile 以真实 motion 状态补充阶段桶；EnvironmentSummary 读取实际桥类来源。没有因为这些观测代码存在就认定输入失效、主题锁定或白屏已经定位。
- Pipeline 的官方/Fabric 两份测试现已同步。其 stub 使用默认 `captureDraft`，与真实三端 Screen 票据集成仍是覆盖范围限制；不能仅凭回调 stub 通过宣称 Screen 全生命周期已实测。此处不额外列为产品 P2。

## 未覆盖及交付边界

未运行任何 Gradle、Java 产品/测试执行、Minecraft、真实 HTTP/服务器、多实例、原生 CPU/GPU 或像素检查；仅做源码/测试/本地依赖源码读取和无 I/O 的字符串算术。没有审计不相关历史遗留、重审上轮 UI、改依赖、写入记忆或其他报告。

主流程在审查期间通报：已完成 370 项定向检查与独立 CPU/GPU 测量；完整首轮 Fabric 已通过；Forge 唯一失败为官方测试遗漏失败 toast 新期望，已同步测试，最终三端将统一重跑。本审查没有执行或独立核验这些运行记录，不把故意反例测试的 warning 当成产品故障。测量未提供显著稳态录制额外成本的证据，因此按用户决定不追加 dirty 缓存或降采样。

本次增量期间主流程另通报：R1–R4 对应 Fabric 定向 60 项全通过、0 skip（`audit-review-fixes.log`）；R2 独立 JDK 17 离线六项实际通过，无 HTTP。上述结果仅记为主流程报告来源，本复核没有执行或独立核验运行日志；其执行范围不自动包含之后新增的 late-picker 和三个准入场景。后续主流程说明：已有有效全量记录保存在 `tests.json`，最后准入修正将跑三端受影响专项＋build，不冒充后增用例已在旧全量执行。本复核未读取这些执行证据或运行检查，等待主流程记录最终结果。

最终三端全量结果、实际配置 no-op 验证、运行中断线与取消、慢盘单文件删除延迟、native 生命周期及性能收益由主流程记录。主流程正在编辑工作树，修正后应仅复核上述受影响路径与新用例；无需据本报告再次扩成全仓审查。本文不提供部署/发布放行结论。
