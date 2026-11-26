# AtomChat JEI 示例

这个独立客户端附属 Mod 为物品卡右键菜单增加「查配方」。它还注册了输入栏按钮、配方页签、工具菜单入口、设置首页入口，以及聊天设置中的「配方」页签和可折叠的开关组。会话开关影响当次启动中的查配方菜单。配方页显示本次会话最近查询的物品。示例同时加大输入栏按钮间距，演示全局布局覆盖。

先构建对应加载器的 AtomChat 0.9.8 JAR。从仓库根目录，用该平台的 Gradle wrapper 单独构建示例。以下以 Fabric 为例。

```powershell
.\platforms\1.21.1-fabric\gradlew.bat -p .\examples\jei\1.21.1-fabric `
    '-PatomchatJar=C:\absolute\path\atomchat-Fabric-1.21.1-v0.9.8.jar' build
```

NeoForge 将两处平台目录换成 `1.21.1-neoforge`，Forge 换成 `1.20.1-forge`，并传入对应的 AtomChat JAR。成品位于各示例项目的 `build/libs`。把示例、对应版本的 AtomChat、AtomLib、JEI 装在客户端，进世界后分享手持物品。从物品卡右键菜单选择「查配方」，按返回键回 AtomChat。选择可制作物品时容易看到 JEI 的结果。

| 版本 | 编译时使用的 JEI | 最低 Java |
| --- | --- | --- |
| Fabric 1.21.1 | 19.21.2.313 | 21 |
| NeoForge 1.21.1 | 19.27.0.340 | 21 |
| Forge 1.20.1 | 15.20.0.112 | 17 |

开发自己的附属时从 [Java 扩展指南](../../docs/JAVA_UI_API.md) 开始。Forge 与 NeoForge 样例共用 Official 映射实现。Fabric 样例使用 Yarn 和 JEI 的 `jei_mod_plugin` 入口。
