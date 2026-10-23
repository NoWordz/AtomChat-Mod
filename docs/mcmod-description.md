[h1=概述]
AtomChat 将 Minecraft 原版聊天界面替换为手机应用风格的聊天面板，支持公屏、私聊、图片与 GIF、表情、消息回应和个性化资料。
界面由 Skija 矢量绘制，不依赖原版聊天纹理。客户端必需，服务端可选；服务端安装后可启用媒体托管、玩家资料同步、共享消息回应和服务端内容包。
支持 Minecraft 1.21.1（Fabric / NeoForge）与 1.20.1（Forge），Windows x64 与 Linux x64 分别提供 JAR；Linux 原生库加载与 CPU 光栅已通过 WSL 冒烟测试，游戏内 UI/GPU 尚待验收；1.21.1 需 Java 21+，1.20.1 需 Java 17+。
[h1=主要功能]
[h2=💬 聊天与私聊]
公屏与私聊集中在同一面板，消息以左右气泡显示，并附真实玩家名称和皮肤头像。
未读提示、按会话保存草稿，以及可选的按服务器 / 世界保存聊天记录，帮助整理对话。
支持引用回复、富文本、聊天过滤、屏蔽和多行输入。
[h2=🖼️ 图片与消息回应]
发送图片和循环播放的 GIF，使用表情、颜文字、贴纸，并可用标准表情回应消息。
可选择、粘贴或拖入图片。图片消息使用 [[CICode]] 协议，与 E33Chat / ChatImage 系列互通。
服务端开启媒体托管时图片由服务器保存；否则使用第三方图床。
[h2=👤 玩家资料与个性化]
自定义签名和横幅；已有自定义头像仍可在 AtomChat 样式下显示，E33Chat 样式使用玩家皮肤。在安装 AtomChat 的服务器上，资料可同步给其他 AtomChat 玩家，玩家资料图片按世界保存。
可选择主题、即时调整界面颜色并设置自定义壁纸。
[h2=✨ 特效与声音]
通知横幅可提示提及、引用和私聊；可选的短语触发特效会显示爱心、生日蛋糕或烟花。
通知音与界面音效可分别控制。
[h1=✅ 兼容性]
模组 / 协议 | 兼容情况；
[[CICode]] 图片协议 | 与 E33Chat（https://www.curseforge.com/minecraft/mc-mods/e33chat）/ ChatImage（https://www.curseforge.com/minecraft/mc-mods/chatimage）系列互通；
ChatHeads（https://www.curseforge.com/minecraft/mc-mods/chat-heads） | AtomChat 0.3.0 的兼容性尚未验证；
Chat Animation（https://www.curseforge.com/minecraft/mc-mods/chatanimation） | AtomChat 0.3.0 的兼容性尚未验证；
昵称 / 显示名插件 | 尽力识别；无法识别的格式可能显示为系统消息；
EasyBot-MCDR（https://mcdreforged.com/en/plugin/easybot_mcdr/introduction） | 可识别常见 QQ 转发格式；
Quark（https://www.curseforge.com/minecraft/mc-mods/quark） | 已知问题：表情菜单可能不显示；
[h1=🖥️ 服主配置]
服务端首次启动会生成 config/atomchat/atomchat-server.json，也可在游戏内通过 /atomchat gui 配置（需要权限等级 2）。
[h2=🗂️ 媒体与玩家资料托管]
hostingEnabled 默认开启。单个文件上限为 2 MB，媒体存储上限为 512 MB，默认保留 7 天；retentionDays 设为 0 表示永久保留。
自定义玩家资料图片另有每世界 64 MB 的存储上限，不受临时媒体清理影响。托管文件只通过 Minecraft 游戏连接传输，不开放 HTTP 端口。
[h2=🎁 服务端内容包]
packEnabled 默认开启。服务端最多提供 32 个贴纸文件、总计 8 MB，以及最多 20 条常用语（每条最多 200 字）。
将服务端贴纸放入 atomchat/server/emotes/。服务器图标和 MOTD 也可作为客户端显示的服务器资料。
[h1=配置与存储]
客户端路径相对于 Minecraft 游戏目录（通常为 .minecraft）；服务端路径相对于服务端游戏目录。
[h2=客户端配置与本地内容]
路径 | 内容；
config/atomchat/atomchat-client.json | 客户端主配置，首次启动时生成；
atomchat/client/avatar/avatar.* | 自定义玩家头像，支持 PNG / JPG / JPEG / WEBP；
atomchat/client/banner/banner.* | 自定义个人资料横幅，支持 PNG / JPG / JPEG / WEBP，文件存放在 atomchat/ 根目录；
atomchat/cache/debug/ | 调试模式输出的头像采样图片；
atomchat/client/emotes/ | 玩家自己的贴纸，支持 PNG / JPG / JPEG / GIF，默认不限数量，可用 emoteMax 设置上限；
atomchat/client/history/ | 可选聊天记录，按服务器或世界保存为 JSONL 文件，默认关闭；
atomchat/client/wallpaper/wallpaper.* | 自定义面板壁纸，支持 PNG / JPG / JPEG / WEBP；
[h2=游戏目录缓存]
路径 | 内容；
atomchat/cache/images/ | 聊天图片缓存，上限 500 个文件 / 100 MB，闲置 7 天后清理；可在 设置 → 关于 中查看或清空；
atomchat-data/packs/<server-key>/ | 从服务器下载的贴纸包；
atomchat-data/avatars/ | 从服务器接收的玩家头像；
[h2=服务端配置与数据]
路径 | 内容；
config/atomchat/atomchat-server.json | 服务端托管、容量和内容包配置；
atomchat/server/emotes/ | 服务端提供给玩家的贴纸源，与玩家自己的 emotes/ 目录分开；
atomchat-data/media/ | 服务端托管的聊天图片与 GIF；
atomchat-data/avatars/ | 服务端接收的玩家头像；
世界存档目录/atomchat/profiles/documents/ | 按玩家 UUID 保存的同步资料记录；
世界存档目录/atomchat/profiles/images/ | 按世界保存的同步资料图片副本；
[h1=❓ 常见问题]
需要安装服务端吗？不需要，客户端模组可独立使用。服务端安装 AtomChat 后，额外启用媒体托管、玩家资料同步、共享消息回应和服务端内容包。
图片会上传到哪里？服务端开启媒体托管时，图片保存在该服务器并按配置清理；否则上传至 uguu.se，链接约 3 小时后过期。只有主动选择、粘贴或拖入图片时才会上传；接收者可能保存或转发图片。开启聊天记录的玩家也可能在本地保存消息，请勿发送敏感内容。
可以放进整合包吗？可以，模组本体采用 MIT 许可。若分发 JAR，请一并保留第三方声明（https://github.com/NoWordz/AtomChat-Mod/blob/main/THIRD_PARTY_NOTICES.md）。
模组不会上传遥测或个人信息。
[h1=📱 问题反馈]
问题与建议：https://github.com/NoWordz/AtomChat-Mod/issues
完整文档：https://github.com/NoWordz/AtomChat-Mod/wiki
