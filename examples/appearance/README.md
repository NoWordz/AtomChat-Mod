# AtomChat 外观附属示例

港湾（A）和信号（B）是可以单独安装的客户端 Mod。每个部件独立注册，两套可以混搭。

| 附属 | 气泡 | 面板轮廓 | 头像框 | 动态壁纸 |
| --- | --- | --- | --- | --- |
| 港湾 `atomchat_harbor` | 港湾切角气泡 | 港湾切角面板 | 潮汐圆环 | 月下港湾 |
| 信号 `atomchat_signal` | 信号胶囊气泡 | — | 琥珀角标 | — |

安装对应平台的 AtomChat 外观 API 集成包、该宿主要求的 AtomLib，以及想试的附属 JAR。打开 AtomChat 的「设置 → 外观与交互 → 预设」，分别选中港湾的四项，再把气泡或头像框换成信号的选项。在 AtomChat 里保存外观预设，重启后检查各项选择是否保留，也可以单独切回「内置」。选项名称随客户端语言切换。

聊天面板和 HUD 已显示港湾切角气泡和两套头像框。信号胶囊气泡在聊天面板显示，HUD 使用内置气泡。切角面板和动态壁纸分别在方案第 3、4 节接入。月下港湾的示例实现用宿主提供的帧时间绘制漂移的月亮、闪烁的星点和三层海浪。

同为 0.9.8 的宿主构建可能没有 `AppearanceUi`。遇到这样的旧包，两个示例各在客户端日志中记录一条带附属 ID 的警告，然后跳过外观注册。若日志出现 `Skipping appearance registration`，换用包含外观 API 的 AtomChat 集成包即可启用这些选项。

## 构建

用 JDK 21 和仓库已有的 Gradle 8.8 wrapper。从仓库根目录执行，给每一端传对应的 AtomChat JAR。JAR 必须包含 `AppearanceUi`。开发期间也可以传入 API 工作树里已经编译好的 `classes/java/main` 目录，无需先打宿主包。

```powershell
.\platforms\1.21.1-fabric\gradlew.bat --no-daemon -p .\examples\appearance -Pplatform=fabric '-PatomchatApi=C:\path\to\fabric\atomchat.jar' build
.\platforms\1.21.1-fabric\gradlew.bat --no-daemon -p .\examples\appearance -Pplatform=neoforge '-PatomchatApi=C:\path\to\neoforge\atomchat.jar' build
.\platforms\1.21.1-fabric\gradlew.bat --no-daemon -p .\examples\appearance -Pplatform=forge '-PatomchatApi=C:\path\to\forge\atomchat.jar' build
```

在 Bash 中将 `.\platforms\1.21.1-fabric\gradlew.bat` 换成 `./platforms/1.21.1-fabric/gradlew`，并使用当前系统的路径分隔符。

每条命令在 `build/<platform>/libs` 生成两个 JAR。独立构建只打包示例自身的代码和资源。Fabric 与 NeoForge 的目标为 Minecraft 1.21.1 / Java 21，Forge 为 Minecraft 1.20.1 / Java 17。

## English

Harbor (`atomchat_harbor`) registers chamfered bubbles and panel, a teal avatar ring, and a procedural Moonlit Harbor wallpaper. Signal (`atomchat_signal`) registers capsule bubbles and amber avatar brackets. Install either or both client add-ons with an AtomChat build containing `AppearanceUi` and its matching AtomLib.

Select each part independently under Appearance and Interaction and save it in a preset. The panel and HUD display Harbor's chamfered bubble and both avatar frames. Signal provides its capsule bubble on the chat panel, with the host bubble on the HUD. The custom panel outline and dynamic wallpaper have their own integration stages.

On older AtomChat 0.9.8 builds without `AppearanceUi`, each add-on logs a warning with its mod ID and skips appearance registration. If the client log reports `Skipping appearance registration`, use an AtomChat build that includes the Appearance API.

Use the commands above with JDK 21 and the matching AtomChat JAR or compiled API classes directory. Each build produces two mod JARs in `build/<platform>/libs`.
