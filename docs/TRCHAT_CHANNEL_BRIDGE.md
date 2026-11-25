# TrChat 2.5.2 频道桥接

AtomChat 的会话列表可以显示由服务端确认可读的 TrChat 频道、未读数和只读消息。发言继续使用服务器原有聊天规则、频道切换和命令。桥接只接受可复核的 TrChat 2.5.2 官方发行 JAR。本地协议验收覆盖 Paper 1.21.1 #133 和 Paper 1.20.1 #196。没有桥接协商时沿用普通聊天。

## 构建和启用

需要 JDK 21、Python、官方 TrChat 2.5.2 发行 JAR、解包后的 Paper 1.21.1 的 `libraries` 目录，以及同版 ASM 和 ASM Tree JAR。维护者用 Paper #133 和 ASM 9.9.1 复核。构建器只接受已经核对的发行制品 SHA-256，并检查每个补丁方法的签名及调用数。生成物用于 Bukkit 后端，Velocity 上继续使用原版 TrChat JAR。

```powershell
python tools/trchat-bridge/build_bridge.py --trchat <TrChat-2.5.2.jar> --paper-libraries <Paper/libraries> --asm <asm.jar> --asm-tree <asm-tree.jar> --jdk <JDK目录>
```

产物在 `build/trchat-bridge/TrChat-2.5.2-atomchat-bridge.jar`。在测试后端停服后，用它替换原 TrChat JAR，避免两个 JAR 同时加载。保留原 TrChat 数据与配置。其中 `plugins/TrChat/atomchat-channels.yml` 默认不启用任何频道。下面是单服示例，频道 ID 来自 TrChat 的频道配置文件名，统一使用小写。稳定的 `backend-id` 用来区分两台后端上同名的频道。

```yaml
backend-id: lobby
channels:
  - normal
  - team
velocity-peers: []
velocity-key: ''
```

配置后完整重启。管理者应确认 TrChat 已加载这些频道和所需 PlaceholderAPI 扩展。AtomChat 客户端在实际收到允许列表后显示相应的会话卡片。点击打开只读消息，返回后可以继续原有聊天。存储只在本次连接内存在，撤销允许列表中的频道会清除消息和未读。服务端每秒刷新允许列表，客户端五秒收不到刷新会清空频道。

## Velocity 双转发

1. 配置现代转发，并只向代理开放各后端。每个参与后端使用不同的 `backend-id`，配置需要互信的 `velocity-peers`。同一个 TrChat 频道 ID 必须在接收后端存在并在其桥接清单里。
2. 仅对参与后端生成并设置同一份至少 32 随机字节的 Base64 `velocity-key`。这是频道附件的后端间校验密钥，和 Velocity 登录转发密钥分别生成、分别保管。密钥能代表此互信组内的发送后端。
3. TrChat 频道需使用 `Proxy: true` 和 `Double-Transfer: true`。原版 TrChat 的 Velocity 插件保留双转发数组中的额外字段。直接由代理发包的模式、Redis 路径和未经认证的转发按普通 TrChat 路径处理。

跨服时，源后端为原始 TrChat 转发数组及附加的后端 ID、频道 ID、消息 ID 和时间签名。接收后端核对签名和时间，并在 TrChat 最终逐人发送的位置再判定该玩家当前是否订阅、是否有加入和收听权限。接收后端使用自己配置的权限规则。代理端权限和发送端声明的权限不能替代这个检查。

## 授权与消息边界

- 本地接缝在 `Channel.execute` 完成消息处理和逐收件人条件后，覆盖 `ALL`、`SINGLE_WORLD`、`DISTANCE` 和 `SELF` 的实际玩家发送。跨服接缝在 `BukkitProxyProcessor.execute` 的 `BroadcastRaw` 实际玩家发送。补丁不读取 `TrChatSendEvent` 作为授权，也不处理私聊、监听、静默禁言或控制台公告。
- 对配置启用且可确认的频道，桥接要求 TrChat 接收者名单、当前 Join 和 Listen 权限同时满足，并尊重完整聊天可见性。频道无法重新解析或已撤权时拒绝投递。尚未成功协商的接收者通过这些检查后，才可沿用 TrChat 普通投递。
- 协商成功时，用一份插件消息承载原 TrChat 组件、作者、稳定频道键和序号。它替换该收件人的这一份普通投递，统一公屏、HUD、私聊和搜索均无需再猜测或删除一份重名消息。验收时可通过协议探针比对原始组件及相邻普通聊天包。只读画面显示组件的可见文字，保留服务器发言入口，组件内嵌的命令按钮在此页没有动作。
- 未带桥接标记的消息沿用 TrChat，例如未启用的频道、未打补丁的源后端和代理直发。带桥接标记的跨服消息须通过认证，且来自互信后端、属于启用的频道，否则丢弃。已识别的受控频道在协商成功后只走频道投递，快照、编码或传输失败时丢弃。插件通道临时注销仍遵守频道权限，主动退出协商或断开连接会结束频道会话。
- 客户端把会话 ID、连接对象、应答 nonce 和每连接序号同时纳入检查。频道允许列表、消息和未读与普通聊天存储隔离，退出或换服后重新协商。频道和文案都不由消息标签、Tab 名单、命令补全或旧缓存推断。

此前为什么不能直接上线频道，见 [0.9.7 兼容验证](verification/2026-11-24-v097-compatibility.md#频道上线条件的结论)。开发验证可用 `tools/trchat-bridge/protocol_smoke.cjs`，依赖独立的本地协议客户端。脚本的 `protocol-joined` 和 `protocol-beta-ready` 分别提示管理员授予和撤销测试权限，完成后写入同目录的 `protocol-granted`、`protocol-revoked`。它只连接环回地址。真实游戏客户端验收应分别走三端，检查频道行、未读数及公屏/HUD/搜索是否有重复。

## 本地验证回执

以下为开发验证记录。测试使用本地离线身份，表格对应初始桥接版本，后续拒投修订及发版时的认证检查见下文。

| 场景 | 可复核证据 |
|---|---|
| Paper #133 | `local-r4/protocol-smoke.ndjson`。两个同显示名频道的同文各只产生一份对应频道包，带作者和会话。协商接收者没有普通副本。未加入者既没有频道包也没有普通副本，未协商且有权限者仍收到普通文本。私聊保留原路径。撤销 Join 和 Listen 后清除接收资格，重连会话变化且权限未恢复。 |
| Paper #196 | `paper20-r1/protocol-smoke.ndjson`。同一桥接 JAR 和同一套直连断言在 1.20.1 协议执行通过。 |
| Velocity #609 双 Paper #133 | `velocity-test/protocol-smoke.ndjson`。接收方切服后会话变化，按 west 的权限收到了 east 的 global 消息。无权 C 没有收到频道包或普通副本，west 撤权后也没有继续投递。 |
| 认证负例 | `proxy-envelope-check.log`。单独在已产出的 JAR 上验证错误密钥、缺少认证、过期、超前时间以及作者、正文、路由被修改的情况。 |
| 三端客户端自动化 | 各目标 `*-final.log` 与 `client-*/test-results/test/TEST-com.atom.chat.channel.*.xml`。每端 4 项通过，覆盖同显示名和同文、权限快照清理、连接切换、重复投递、隐藏作者、过期及编码边界。 |

此外用隐藏 Fabric 1.21.1 客户端直连 Paper #133。收到 alpha 消息时卡片未读为 1，真实点击后打开只读消息流并清零。普通聊天存储中该消息出现次数为 0。撤销 Listen 后，alpha 从允许列表和客户端会话中移除。Forge 与 NeoForge 的客户端部分由编译、自动化及源码复核覆盖。

### 拒投修订复测

- 在 Paper #133 隔离服上，从同一个真实投递入口分别制造频道缺失、组件超长、快照发送不可用和插件通道注销。旧包四种都向已协商客户端发出普通原文，见 `acl-red/protocol-smoke.ndjson`。修订包四种都没有普通包或频道包，并通过未协商有权接收原文、协商正常接收频道包这两项正对照，见 `acl-green/protocol-smoke.ndjson`。
- `velocity-acl-r2/protocol-smoke.ndjson` 使用修订包实测双转发。west 有权接收者正常收到 east 的频道包。撤权后用 east 的收包确认该条消息确实发出，west 的被撤权账号没有收到两类包中的任何一种。代理直发的无标记消息沿用 TrChat 的发送规则。
- Paper #196 使用修订包重新通过 8 项直连断言，见 `paper20-acl/protocol-smoke.ndjson`。本修订只改服务端桥接和探针，三端客户端代码沿用已冻结版本。

发版前复核补充了跨服标记检查。带标记的消息只有在签名、时间窗、互信后端和启用频道均通过时才进入接收权限检查。独立探针在重新构建的后端 JAR 上覆盖了普通帧、正常签名、错误密钥、过期和不完整帧。
