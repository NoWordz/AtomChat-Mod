# 0.9.6 图片上传静态分析

范围：以 0.9.5 main `6789b16b` 的真实上传与草稿链路为准。用户反馈为部分服务器玩家使用任意入口上传后，“图片上传中”很快恢复默认占位符。没有故障玩家日志和复现环境，本报告不把候选原因认定为实际根因；本轮没有向外部图床上传文件，也没有修改上传行为。

## 已确认的数据流

选择文件、拖入图片和剪贴板图片最终汇入同一个 `ImageUploadPipeline.uploadAndAppend`。文件检查通过后，主线程捕获原会话草稿票据并记录连接 epoch；有可用服务端托管时先尝试托管，其他情况直接走 Uguu 外部图床。普通托管错误会回退 Uguu，取消/断线错误不回退。

外部上传使用单例 Java HttpClient，目标固定为 `https://uguu.se/upload`。一次运行最多接受 8 个任务，单工作线程顺序发送，连接超时 15 秒、单请求超时 60 秒。只接受 HTTP 200 且 JSON 中存在 `files[0].url` 的响应。

上传成功后生成完整 CICode，优先删去可选文件名来控制 256 字符上限，再通过原草稿票据写入。票据允许跨面板实例保留，避免把图片放进另一个私聊。当前草稿无法立即接收时返回 DEFERRED，后续绘制面板时再次 drain。

来源：`shared/.../image/ImageUploader.java:31,59,108`，`layers/mapping/official/.../image/ImageUploadPipeline.java:159,184,224,235`；Fabric 有对应映射副本。真正的草稿接线位于三个 AtomChatScreen 的 `captureDraft` 与 `flushUploadedAttachments`，共享票据实现在 `PendingAttachments`。

## 候选原因与判别证据

| 分支 | 为什么符合或不完全符合现象 | 最小判别证据 |
| --- | --- | --- |
| 外部图床网络、DNS、TLS、代理或风控响应 | 所有上传入口共用此路径；同服不同玩家的 JVM 与网络条件可以不同。HTTP 拒绝或立即连接失败会很快结束“上传中”。但同 epoch 的普通失败按代码应先有 4 秒失败提示，不能仅凭默认占位符就认定网络错误。 | 故障前后客户端日志中 `Failed to upload image` 的异常类型，或 `Image upload failed` 后的 HTTP 状态。不能只拿浏览器能访问作为 JVM 可访问的证据。 |
| 服务端不支持/关闭托管，实际上全部依赖 Uguu | `hostingEnabled` 未获肯定状态就返回 false；未安装配套服务端也走外部图床。“在这个服发生”不代表上传字节一定发到了服务器。 | 当前服务器媒体通道支持状态与下发状态；调试 trace 的 `external` 阶段。 |
| 服务端托管错误后回退图床仍失败 | 普通 `hosting_disabled`、超限或其他托管错误可触发一次外部回退。若图床也不可达，所有入口仍失败。取消/断线则不会回退。 | 托管回调错误与 `external` 是否出现；区分普通错误和 `disconnected/cancelled`。 |
| 连接 epoch 改变或草稿票据失效 | 存在能直接回到默认占位符的确定代码路径：请求先 finish，之后发现 epoch 不同就取消并返回；或者 append 返回 STALE，而当前实现只给 DEFERRED 提示，STALE 没有提示。此处是静默结束分支，不足以证明故障玩家一定命中。 | `FaultTrace` 同一 upload id 的阶段和 epoch；是否有 JOIN/DISCONNECT；是否出现 `STALE`。追查票据为何被清理，而不是取消连接隔离保护。 |
| 面板实例/页面改变，结果还在原会话等待接收 | 上传进度属于原屏幕实例，重开屏幕后新实例不持有原请求集合；成功结果仍可能在共享票据中。搜索、常用语/签名编辑和不同私聊会影响即时 drain。代码设计会 DEFERRED 或后续 drain，不能等同上传字节丢失。 | 上传时是否切页、搜索、关面板或换会话；重新回原会话后是否出现 CICode；票据 complete/drain 状态。 |
| 队列忙、图片限制、编码过长 | 队列 busy 和编码过长都会结束进度并有错误提示；无效/超限图片通常在出现“上传中”之前即被拒绝，因此与每种入口均先进入上传中的吻合度较低。 | busy/图片检查/编码过长的提示与回调；换小 PNG 是否仍然失败。 |

## 已发现的维护缺口

1. `AppendResult.STALE` 没有用户可见提示；`appendImageCode` 在 finish 后的 epoch 分支也刻意静默。前者值得单独补充错误反馈，后者须保持跨连接隔离，不能通过取消 epoch 检查“修好上传”。
2. 网络错误归并为“图片上传失败，请重试”，默认占位符的失败提示只保留 4 秒。对“很快恢复请输入文本”的描述，现有 UI 无法区分 HTTP 拒绝、TLS、队列忙和会话取消。
3. 调试 trace 已经存在，受 `config.debug` 驱动，记录 upload id、阶段与 epoch，不接受图片 URL 或消息内容。无需为本次分析另外建立遥测系统。

## Java 网络前提

HttpClient 的配置在构建时确定；本项目没有显式选择代理。Java 的系统代理开关 `java.net.useSystemProxies` 默认 false，并只在 JVM 启动时检查。因此“浏览器走代理能上传”与“游戏 JVM 能上传”不是等价条件。这里是由配置和官方契约推出的候选环境差异，没有实测故障玩家配置，也不建议未经核对就替玩家改代理。

权威依据：[Java 21 HttpClient](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html)、[Java 网络属性](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/doc-files/net-properties.html)。

## 当前结论

静态分析已覆盖入口、托管、图床、回调、连接代际、草稿票据和占位符。优先用一段故障时刻日志区分“外部上传失败”和“成功后的会话/草稿静默结束”。当前证据不支持把问题归因于某个服务器插件、某一种粘贴方式或某个具体网络故障，也不能报告图片上传已修复。
