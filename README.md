[简体中文](README.md) | [English](README_EN.md)

<h1 align="center"><img width="256" height="256" alt="logo" src="https://github.com/user-attachments/assets/e449c62e-644b-4c19-9fbc-431d7a899781" /></h1>

<h1 align="center">AtomChat</h1>

<p align="center">
  <em>为 MC 原版聊天框带来手机聊天 APP 版的体验</em>
</p>

<p align="center">
  <img alt="MC" src="https://img.shields.io/badge/MC-1.21.1%20%7C%201.20.1-green">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Fabric-orange">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-NeoForge-blue">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Forge-red">
  <img alt="Side" src="https://img.shields.io/badge/Side-Client%20%7C%20Server-blue">
  <img alt="OS" src="https://img.shields.io/badge/OS-Windows%20x64-lightgrey">
  <img alt="Java" src="https://img.shields.io/badge/Java-17%2B-yellow">
  <img alt="Version" src="https://img.shields.io/github/v/release/E33EPUS/AtomChat?sort=semver">
  <img alt="License" src="https://img.shields.io/badge/License-Apache--2.0-brightgreen">
</p>

<p align="center">
  <a href="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml"><img alt="Build" src="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml/badge.svg?branch=main"></a>
</p>

> **v0.2.9 已发布** · [下载 JAR](https://github.com/E33EPUS/AtomChat/releases) · [中文 Wiki](https://github.com/E33EPUS/AtomChat/wiki)

> [!WARNING]
> **目前只支持 Windows x64。** JAR 内只打包了 Skija 的 Windows x64 原生库，Linux / macOS 装上后聊天面板起不来。详见[已知限制](#已知限制)。

## 这是什么

AtomChat 把原版聊天屏改造成一个独立的「手机 App」风格聊天面板：圆角气泡、真实玩家名与圆形皮肤头像、表情 / 颜文字 / 表情包、图片与 GIF 动图消息、服务端媒体托管、复制与引用、多行输入，以及 QQ 式动效。

界面全部由 [Skija](https://github.com/HumbleUI/skija) 矢量绘制，不依赖原版聊天纹理。项目是基于 [E33Chat](https://github.com/E33EPUS/E33Chat) 理念的干净重写，**不是 E33Chat 的 fork**；图片消息沿用它的 `[[CICode]]` 协议，可与 E33Chat / ChatImage 系模组互通。

模组是**纯客户端**的：不装服务端也能用（图片走第三方图床）；服务端也装一份，才会解锁**服务端媒体托管**与**自定义头像同步**。

## 安装

| 依赖 | 要求 |
|---|---|
| Minecraft | 1.21.1（Fabric / NeoForge 版）或 1.20.1（Forge 版） |
| Fabric 版 | Fabric Loader 0.16.0+ 与 Fabric API |
| NeoForge 版 | NeoForge 21.1+ |
| Forge 版 | Forge 47+（1.20.1） |
| Java | Fabric / NeoForge 版需 21+；Forge 1.20.1 版需 17 |
| 系统 | Windows x64（macOS / Linux 尚不可用） |

1. 从 [Releases](https://github.com/E33EPUS/AtomChat/releases) 下载对应平台的 JAR（`atomchat-Fabric-1.21.1-*.jar` / `atomchat-NeoForge-1.21.1-*.jar` / `atomchat-Forge-1.20.1-*.jar`），或按[开发与构建](#开发与构建)自行构建
2. 放进 `.minecraft/mods/`；想让服务器托管图片 / GIF 并同步头像，把同一个 JAR 也放进服务端的 `mods/`
3. 启动游戏，按聊天键（默认 `T` / `/`）打开 AtomChat；也可以按 `Y`（默认按键，可在 按键绑定 → `AtomChat` 分类里改）直接打开面板并回到上次所在的页面

## 快速开始

1. 打开聊天就是手机面板：顶部「公屏」与系统时间，中间消息列表，底部输入栏
2. 打字回车发送；内容超过一行时输入栏自动长高，仍可用上下箭头在行间移动光标
3. 发图三种方式：点**图片按钮**选本地图片，或把图片**拖进窗口**，或复制图片后 **Ctrl+V**；上传完自动插入草稿
4. 点**表情按钮**打开面板：`表情` / `颜文字` / `表情包` 三个标签页；服务端有下发内容时，表情包页底部还有只读的「本服」分区
5. 右键任意消息可**复制**或**引用回复**；单击头像打开玩家档案，双击头像戳一戳，右键头像呼出菜单（@ 提及 / 私聊 / 传送 / 屏蔽）

## 主要功能

- 📱 **手机面板** — 打开聊天时原版聊天 HUD 隐藏，改为独立面板：毛玻璃背景（可换壁纸）、底部三个标签页（聊天 / 个人 / 设置）、页面级推入弹出转场
- 💬 **消息气泡** — 自己在右、他人在左；真实玩家名 + 圆形皮肤头像（正版 / 离线皮肤自动降级）；引用胶囊、时间戳分隔、系统消息胶囊
- 🖼️ **图片与 GIF 消息** — 原生渲染 `[[CICode]]`，按原图比例显示；GIF 在气泡内循环播放；透明底图直接透出面板背景；右键可保存原图；面板外的原版 HUD 只显示绿色 `[图片]` 与蓝色 `[引用]`
- 📤 **发送本地图片** — 文件选择器默认「详细信息」视图并显示缩略图；支持拖放与 Ctrl+V 粘贴；上传后自动生成 CICode
- 📦 **服务端媒体托管** — 服务端装了 AtomChat 时，图片 / GIF 直接存到服务器再分发给其他玩家，不依赖第三方图床：内容寻址去重、按需分块下发、只走游戏连接、**不开 HTTP 端口**；服务端未装 / 关掉托管 / 超限 / 上传失败时自动回退图床（同一个 `hostingEnabled` 总开关也管头像同步）
- 🎁 **本服下发** — 进服时自动同步服务端的表情包、常用语，以及 `server-icon.png` 与 MOTD；表情面板多出只读的「本服」分区，常用语面板多出只读的「本服」分组；逐文件 sha256 校验、只补差量（删掉一个表情，下次进服只补那一个）；设置 → 隐私里可整体关掉接收
- 😀 **表情 / 颜文字 / 表情包** — 三个标签页带滑动指示器与全宽推入动画；表情包存本地，默认不限数量（`atomchat-client.json` 的 `emoteMax` 可设上限）且格子可滚动，`+` 添加、悬停 `×` 删除（gif 在面板里只显示首帧，发出后正常播放）
- ⚡ **常用语与输入** — 输入栏闪电按钮打开常用语（点选插入、不直接发送，最多 20 条、单条 ≤256 字）；多行输入、@ 提及、IMBlocker 命令态桥接、WATUT「对方正在输入」；默认自带一条 `/atomchat gui` 帮助服主发现服务端配置入口
- 🔗 **富文本与右键菜单** — 玩家名 / 正文支持颜色、下划线、点击与悬停：`/tell`、坐标、FTB 接受 / 拒绝、外部链接均可点，裸 URL 自动转链接；右键菜单提供复制 / 引用 / 保存 / @ / 私聊 / 传送 / 屏蔽
- 👥 **私聊与会话列表** — 会话列表按「公屏 → 在线玩家 → 最近离线」排序，带真实 ID、皮肤头像、在线状态点与未读红点；私聊走 `/msg`，分会话保存草稿与滚动位置；另有玩家档案详情页
- 🔔 **通知** — 被 @、被引用、收到私聊时面板顶部弹出横幅（点击跳转原消息并高亮），配套原创提示音；开关与音量在 设置 → 聊天 →「通知」
- 🎨 **外观自定义** — 主题预设（毛玻璃 / 现代）一键切换；全部界面配色可调（面板 / 气泡 / 文字 / 卡片 / 描边 / 强调色），带实时预览；另有背景模糊、面板宽度 400–600、界面缩放 x0.75–x1.50、圆角风格、消息入场动画；自定义壁纸长边自动降采样到 1024
- 🧹 **聊天治理** — 公屏分类过滤（全部 / 仅系统 / 仅玩家，纯视图过滤）；跨多条消息拖选后 Ctrl+C 一次复制；连续相同消息合并计数；同一发送者 5 分钟内紧凑分组；时间戳按间隔显示；屏蔽名单可视化
- ⚙️ **设置与持久化** — 设置页为 Win11 风格 2×2 磁贴主页（外观 / 聊天 / 隐私与屏蔽 / 关于），所有选项**即时生效并立即写盘，无需重启**；可选把聊天记录按服务器 / 世界存盘，重进恢复

## 图片走的是哪条路

装了 AtomChat 的服务端会**优先**接管你发的图片：消息里写的是 `atomchat-media:<id>` 短链，其他玩家按需从服务器拉取。以下任一情况自动回退第三方图床 [uguu.se](https://uguu.se)（链接约 3 小时过期）：服务端没装 AtomChat、`hostingEnabled` 关掉了、文件超过 `maxFileKb`、上传失败。

想确认实际走了哪条路，看客户端日志：托管成功会有一行 `Stored hosted media`。

服务端托管的媒体默认保留 7 天（`retentionDays`，`0` = 永久），按容量上限自动修剪最旧的内容；具体数值见 [Wiki 服主手册](https://github.com/E33EPUS/AtomChat/wiki)。

## 兼容性

| 项 | 状态 |
|---|---|
| Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1 | ✅ 支持（三端同源实现） |
| 服务端 | ✅ 可选安装 —— 装了才启用媒体托管与头像同步；不装就是纯客户端 |
| `[[CICode]]` 图片协议 | ✅ 与 E33Chat / ChatImage 系互通 |
| 聊天头像（ChatHeads）/ 聊天动画类 Mod | ✅ 兼容 |
| 花名 / 昵称插件 | 🟡 尽力识别（点击私聊 / Tab 名 / 装饰名结构）；极端未知格式回退灰字 |
| EasyBot | 🟡 尽力识别（QQ 消息类型解析）；无法解析则回退灰字 |
| Quark | 🚫 不显示表情菜单，正在尝试兼容 |
| 其他加载器 / 版本 | ❌ 目前仅 Fabric / NeoForge 的 1.21.1 与 Forge 的 1.20.1 |

## 已知限制

1. **仅 Windows x64**：Skija 原生库只打包了 Windows x64，Linux / macOS 无法运行
2. **不支持服务端之外的图片托管**：服务端没装 AtomChat 时图片走第三方图床 uguu.se（约 3 小时过期）；超过 `maxFileKb`（默认 2 MB）的文件不会上传服务端，客户端也不会重新压缩
3. **玩家身份解析是尽力而为**：tell-click 结构捕获、离线 seen 缓存、ownDisplayName 多级降级；识别不了的会保守归为灰色系统消息
4. **独立服务器的端到端联调尚不完整**：目前只在「单人 / 局域网主机的服务端侧」实测过托管链路；独立服务器（尤其 Linux，无内置 Skija 原生库）与两个客户端互拉图片尚未实测
5. **功能面小于 E33Chat**：暂无私聊侧边栏、搜索等能力（服务端格式模板 `chatTemplates` / `whisperTemplates` 已支持，需手改配置）

## 隐私与数据

> [!WARNING]
> 你发送的本地图片会被上传到第三方图床（默认 uguu.se），或（服务端开启托管时）保存到你所在服务器的磁盘上，且他人可以保存 / 转发。你的消息也可能被开启了聊天记录功能的玩家保存在本地。**请勿发送敏感或私密内容。**

- 模组不上传任何遥测 / 个人信息；图片上传只在你主动选择 / 粘贴 / 拖入时发生
- 服务端托管只通过游戏连接传输字节，不开放 HTTP 端口，也不暴露给未连接的玩家
- 本地配置、头像、壁纸、表情包只存在 `.minecraft/config/atomchat/`，不会自动同步给别人
- 全套数据落点与保留策略见 [Wiki](https://github.com/E33EPUS/AtomChat/wiki)

## 常见问题

**需要装服务端吗？** 不需要，AtomChat 是纯客户端模组。服务端也装一份，才会激活服务端媒体托管与自定义头像同步。

**图片是走图床还是走服务器？** 服务端装了 AtomChat 且 `hostingEnabled=true` 时优先走服务器；否则回退 uguu.se 图床。判断方法见[上文](#图片走的是哪条路)。

**表情包存在哪里？** 自己的表情在 `.minecraft/config/atomchat/emotes/`（默认不限量，png / jpg / jpeg / gif；可在 `atomchat-client.json` 里用 `emoteMax` 设上限，重开聊天界面生效）；服务端下发的表情在只读的「本服」分区，单独计数。服的服主请把要下发的表情放进服务端的 `config/atomchat/server-emotes/`。

**为什么某条消息显示成灰色？** 客户端无法确定它是玩家消息时会保守归为系统灰字（常见于昵称插件使用了无法解析的格式）。

**可以放进整合包吗？** 可以。AtomChat 代码为 Apache-2.0，无需额外授权；若分发 JAR，请保留 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 中的第三方声明。

## 更多文档

| 去哪 | 看什么 |
|---|---|
| [Wiki](https://github.com/E33EPUS/AtomChat/wiki) | 快速上手、图片与传输路线、表情包、外观与配置全表、**服主手册**、疑难排查、开发者说明 |
| [Issues](https://github.com/E33EPUS/AtomChat/issues) | 报 bug、提建议（也可在百科 / 平台评论区留言） |
| [Releases](https://github.com/E33EPUS/AtomChat/releases) | 三个平台的 JAR 与每个版本的更新说明 |
| [CHANGELOG](https://github.com/E33EPUS/AtomChat/blob/main/CHANGELOG.md) | 完整变更历史（双语） |

## 开发与构建

```bash
./gradlew.bat build   # 构建 + 跑测试
./gradlew.bat test    # 仅跑 JUnit 测试
```

产物在 `build/libs/`：Fabric 与 NeoForge 版本需 JDK 21，Forge 1.20.1 版本需 JDK 17；Forge 另有一个未内嵌依赖的 `-slim` 版本，**发版请用不带 `-slim` 的那个**。

源码在**一个分支**里维护：能共用的代码在 `shared/`，按版本 / 加载器 / 映射家族分层的是 `layers/`，各目标自己的代码与构建脚本在 `platforms/<目标>/`。支持哪些目标写在 `versions/targets.json`；一次发版所有目标各出一个 jar，新增目标不必改构建脚本。模块划分、包结构与测试说明见 [Wiki 开发者页](https://github.com/E33EPUS/AtomChat/wiki)。

## 许可证

[AtomChat 自身代码](LICENSE)为 Apache-2.0。分发的 JAR 中捆绑了 Skija（Java 绑定）、HumbleUI types、FlatLaf 与 Skia 原生库，各自保留其许可证，完整文本见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

Copyright © 2026 E33EPUS
