# AtomChat Java 界面扩展指南

从 0.9.8 起，Fabric 1.21.1、NeoForge 1.21.1 和 Forge 1.20.1 提供同一套公开预览接口。把对应的 AtomChat JAR 加入客户端附属 Mod 的编译依赖。Fabric 使用构建工具重映射正式 JAR。使用 Skija 图标或画布的示例以主模组所用的 Skija 版本编译，运行时由主模组提供。

## 注册时机和顺序

在客户端初始化期间注册界面扩展，供屏幕首次打开时使用。以 `your_mod:feature` 形式命名自己的项目和变更。每个界面区域独立注册，目标 ID 可以指向内置项或另一个附属的项。用 `add` 加入、`remove` 移除、`replace` 替换，或者用 `move` 调整顺序。替换保留目标 ID，便于其他附属继续把它当作顺序锚点。

同一个目标收到多份变更时按变更 ID 排出固定顺序。相对位置随其锚点一起移动，缺少锚点时保留当前位置，新加入项的当前位置是末尾。把多项相对位置写成相互依赖的循环会得到一个确定的排序，建议用实际目标组成单向顺序。需要更新已打开界面的附属按该界面的生命周期重建快照。

## 可用入口

| 要改的地方 | 入口 |
| --- | --- |
| 输入栏按钮 | `ComposerUi.buttons()`，内置位置 ID 为 `TOOLS_BUTTON`、`EMOJI_BUTTON`、`QUICK_PHRASES_BUTTON`、`SEND_BUTTON`。自定义按钮选择方形或胶囊外观并提供图标。回调通过 `ComposerUi.Context` 读取或编辑草稿、请求宿主发送和打开已注册页面。草稿权限随调用结束或离开原会话失效。 |
| 工具菜单 | `ComposerUi.menu()`。内置菜单项 ID 在 `ComposerUi` 中公开。用翻译键提供菜单文案。选择玩家后打开的子列表由宿主随在线玩家刷新。 |
| 底部导航和自定义页面 | 先 `NavigationUi.registerPage(id, page)`，再向 `NavigationUi.tabs()` 添加页签，或由已有入口打开该页面。页面拿到裁剪后的列表区、滚动位置和虚拟坐标。页面实例在所属 AtomChat 屏幕释放时调用 `close()`。内置根页的 ID 为 `NavigationUi.CHAT`、`PROFILE`、`SETTINGS`。 |
| 设置首页 | `SettingsUi.home()`。用 `SettingsUi.sectionRoute(section)` 定位内置入口，自定义入口使用已注册的页面 ID。 |
| 设置详情的页签和折叠组 | `SettingsUi.tabs(section)` 与 `SettingsUi.groups(section, tabId)`。添加的折叠组可绑定附属自己的开关和保存逻辑。`SettingsUi.rows(section, tabId, groupId)` 可移动、删除或替换组内的单行，也可加入附属开关或说明。无页签的页面通过空的 `tabId` 指定。内置页签和组 ID 用 `builtInTabId`、`builtInGroupId` 取得。 |
| 全局布局和图形 | `ComposerUi.styles()` 替换 `ComposerUi.SPACING` 可改工具键间距和左右留白。 `SettingsUi.homeStyles()` 替换 `SettingsUi.HOME_SPACING` 可改首页格距和边距。按钮、菜单、底部导航和设置入口接受附属提供的 Skija 图标。附属持有图标并在注册有效期间保持原生资源可用。 |
| 本地物品卡操作 | `ItemCardActions.register` 将客户端动作加入物品卡菜单，配合 `ItemCardItems.copyStack` 取得原生物品及其标签或组件。点击前会重新检查动作的可用状态。常规卡片网络动作沿用原协议，本地回调仅由客户端构造。 |

样例附属走真实的 AtomChat 输入、设置和导航入口，并使用已安装的 JEI 查看配方。 [三端示例与构建命令](../examples/jei/README.md)

## 逐项注册外观

客户端附属可在初始化时用 `AppearanceUi` 注册气泡、面板轮廓、头像框和动态壁纸。每项使用稳定的 `your_mod:feature` ID 和资源包翻译键。气泡和头像框可各给面板、HUD 一套实现，缺省的一侧使用内置样式。面板轮廓返回的路径由宿主接管，用于统一绘制、裁剪和命中。动态壁纸工厂每次启用创建独立会话，宿主提供帧时间和画布范围，停用或重载时关闭会话。

「设置 → 外观与交互 → 预设」可逐项选择，选项可以来自不同附属。自定义预设保存这些选择、外观参数和壁纸。附属缺失时沿用内置外观，并保留 ID 供附属恢复后继续使用。第一阶段接入注册、选择与预设。气泡、头像框、面板轮廓和动态壁纸的绘制接线随实施方案的后续阶段接入。

港湾示例注册四类部件，信号示例提供另一套气泡和头像框。 [外观附属示例与三端构建命令](../examples/appearance/README.md)

## 可提炼到 AtomLib 的部分

有序注册、按目标 ID 替换以及可共享的布局快照可复用于其他消费项目。先验证按钮、菜单、页签和设置组的共同用法，再考虑把注册容器与布局数据分离提取。Minecraft 的聊天输入、发送、设置持久化和选中页面路由由 AtomChat 的宿主实现。
