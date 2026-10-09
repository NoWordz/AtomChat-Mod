# AtomChat

<em>把 Minecraft 原版聊天框变成手机聊天 APP 的面板。</em>

## 这是什么

一个纯客户端 mod：打开聊天键，原版聊天屏换成一块 Skija 矢量自绘的手机面板——圆角气泡、真实皮肤头像、私聊会话、引用回复、戳一戳、图片与 GIF 消息。图片沿用 `[[CICode]]` 协议，与 E33Chat / ChatImage 系互通。服务端可选安装：装了才启用服务端媒体托管与自定义头像同步，不装就是纯客户端。

## 功能

- **手机面板** — 毛玻璃或壁纸背景、底部三标签（聊天 / 个人 / 设置）、slide / zoom 页面转场，全局可切。
- **气泡与身份** — 自己在右他人在左，真实玩家名 + 圆形皮肤头像；引用胶囊、@ 高亮、刷屏合并计数、历史折叠（每批 100 条）。
- **图片消息** — 选择器 / 拖放 / Ctrl+V 上传；GIF 在气泡内循环；左键大图预览（暗背景 + 缩放淡入）；右键存原图。
- **私聊与通知** — `/msg` 会话各自存草稿与滚动位置；被 @、被引用、收私聊弹横幅，点击跳转原消息；双击头像戳一戳。
- **外观** — 九套主题（含半透明「薄暮」「薄荷」）、全配色可调带实时预览、自定义壁纸 / 头图 / 头像、内容缩放滑条。
- **治理与设置** — 公屏视图过滤、跨消息拖选复制、屏蔽名单；设置即时生效写盘，聊天记录可按服 / 世界存盘。

## 兼容性

| 目标 | 需要 |
| --- | --- |
| Fabric 1.21.1 | Fabric Loader 0.16+、Fabric API |
| NeoForge 1.21.1 | NeoForge 21.1+ |
| Forge 1.20.1 | Forge 47+ |

Java 21（两个 1.21.1 目标）/ 17（Forge 1.20.1）。**仅 Windows x64**：jar 只打包了 Skija 的 Windows x64 原生库。三端都是客户端可用；服务端也装一份，别人才能拿到你托管的图片与头像。

## 配置

客户端配置在 `config/atomchat/atomchat-client.json`，所有选项即时生效并写盘。常用键：`panelWidth` / `uiScale` / `contentScale` / `pageNavStyle`（slide 或 zoom）/ `animationEnabled` / `blurEnabled` / `hostingEnabled` / `retentionDays` / `maxFileKb` / `emoteMax` / `debug`。本地头像、壁纸、表情包同在 `config/atomchat/` 下。

## 已知限制

- 仅 Windows x64，Linux / macOS 面板起不来。
- 服务端没装 AtomChat 时图片走第三方图床（约 3 小时过期）；超过 `maxFileKb` 的文件不上传。
- 玩家身份解析是尽力而为：昵称插件的极端格式会保守归为灰色系统消息。
- 独立服务器的托管链路只在单人 / 局域网主机侧实测过。

## FAQ

**需要装服务端吗？** — 自己用不需要；装了才有媒体托管与头像同步。

**图片走图床还是服务器？** — 服务端装了且 `hostingEnabled=true` 走服务器，否则回退图床；客户端日志里 `Stored hosted media` 表示走了托管。

**表情包存哪？** — 自己的在 `config/atomchat/emotes/`；服务端下发的在只读「本服」分区。

## 安装

1. 从 [Releases](https://github.com/NoWordz/AtomChat-Mod/releases) 下载对应加载器的 jar（Forge 用不带 `-slim` 的）。
2. 丢进 `mods/`；要托管图片就把同一个 jar 也放进服务端 `mods/`。
3. 游戏里按聊天键或 `Y` 打开面板。

## 从源码构建

单分支多目标：`shared/` 平台无关逻辑，`layers/mapping/official/` 官方映射层，`platforms/<目标>/` 每目标一个 Gradle 工程。

```bash
cd platforms/1.21.1-fabric && ./gradlew build    # JDK 21，其余两目标换目录；Forge 1.20.1 用 JDK 17
```

更多说明见 [Wiki（英文）](https://github.com/NoWordz/AtomChat-Mod/wiki)。

## 许可

MIT，见 [LICENSE](LICENSE)。第三方组件声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
