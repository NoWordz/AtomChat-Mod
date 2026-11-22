<p align="left"><a href="README.md">English</a> · <strong>简体中文</strong></p>

<p align="center"><img src="https://github.com/user-attachments/assets/354cc7eb-4c1c-49f0-9759-bbe18bf11a14" width="320" alt="AtomChat 标志"></p>

<h1 align="center">AtomChat</h1>

<p align="center"><em>基于 Skija，为 Minecraft 带来手机应用风格的聊天体验。</em></p>

<p align="center">
  <a href="https://github.com/NoWordz/AtomChat-Mod/releases/latest"><strong>下载</strong></a> ·
  <a href="docs/wiki/快速上手.md">快速上手</a> ·
  <a href="docs/wiki/Home.md">使用文档</a> ·
  <a href="RELEASE_NOTES.md">更新说明</a>
</p>

## 安装

把 AtomChat 和对应的 [AtomLib](https://github.com/NoWordz/AtomLib-Mod/releases/latest) JAR 放入客户端的 `mods/`。两份 JAR 都要与 Minecraft 版本和加载器匹配。

| Minecraft | 加载器 | Java |
| --- | --- | --- |
| 1.21.1 | Fabric Loader 0.16+ 与 Fabric API | 21 |
| 1.21.1 | NeoForge 21.1+ | 21 |
| 1.20.1 | Forge 47+ | 17 |

进游戏后按 `T` 输入消息，或按 `Y` 打开面板。快捷键可在 Minecraft 的按键绑定里修改。

在服务器上也装 AtomChat，即可启用媒体托管、档案同步，以及表情包和常用语下发。专用服务器只需安装 AtomChat。 [服主手册](docs/wiki/服主手册.md)

## 主要功能

- **聊天面板**支持消息气泡、玩家头像、私聊会话、引用、提及和通知横幅。
- **图片与反应**支持粘贴或拖入图片、播放 GIF 和发送表情包，服务器也装 AtomChat 时可使用同步表情反应。
- **聊天 HUD**在游戏中显示最近的消息、头像和图片预览，可保留最多十条、设置到时退出，或切回原版聊天显示。
- **外观**可混搭主题、气泡和壁纸预设，也能使用自己的壁纸，单独调整不透明度、模糊和各项配色。
- **布局与动效**支持移动和缩放面板、调整通知横幅位置，以及配置转场与操作反馈。

## 兼容性

公开发布的 AtomChat 0.9.2 请配合 AtomLib 0.6.0 使用，包内的设置资源对应这个库版本。AtomChat 0.9.3 开发包在 Windows 上使用 AtomLib 0.7.0。 [AtomLib 桌面端支持](https://github.com/NoWordz/AtomLib-Mod/releases/tag/v0.7.0)

图片消息沿用 E33Chat、ChatImage 使用的 `[[CICode]]` 协议。替换聊天界面的模组或部分服务器消息格式可能需要额外配置。 [常见问题排查](docs/wiki/常见问题排查.md)

## 从源码构建

在仓库根目录获取固定版本的 AtomLib Maven 输入，再用 Java 21 构建 Fabric。构建 NeoForge 时进入 `platforms/1.21.1-neoforge`，构建 Forge 时进入 `platforms/1.20.1-forge` 并使用 Java 17。

```sh
python tools/fetch_atomlib_input.py --output build/atomlib-input-path
ATOMLIB_MAVEN_REPO="$(cat build/atomlib-input-path)"
cd platforms/1.21.1-fabric
./gradlew test build -PatomlibMavenRepo="$ATOMLIB_MAVEN_REPO" --no-daemon \
  -x frontendContractSourceCheck -x frontendContractCheck
```

本地测试包位于所选目标的 `build/atomlib-consumer/libs/`。Windows 可用 `gradlew.bat`，并传入 `build/atomlib-input-path` 中记录的路径。正式分发构建还会执行前端契约检查。 [项目状态](docs/STATUS.md) · [变更日志](CHANGELOG.md) · [问题反馈](https://github.com/NoWordz/AtomChat-Mod/issues)

## 许可证

AtomChat 采用 [MIT 许可证](LICENSE)。 [第三方声明](THIRD_PARTY_NOTICES.md)
