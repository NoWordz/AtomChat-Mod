# 平台接缝清单

> **这是生成物**，由 `tools/gen_seams.py` 扫描 `platforms/` 与 `shared/`、`layers/` 产出，
> CI 校验它与代码是否同步（`python3 tools/gen_seams.py --check`）。手改会在下一次校验里红。

`platforms/<目标>/` 下每个 java 文件属于下面两组之一，依据是**共用代码引不引用它**：

| 组 | 含义 | 新增一个目标时 |
|---|---|---|
| **甲：共用代码引用了的** | `shared/` 或某个共享层里出现了这个类型 | **必须提供同名类型**，否则共用层编不过 |
| **乙：平台内部的** | 只有该目标自己的代码用它 | 不必提供 |

分组是编译期事实：甲组少一个类型，那个目标当场编不过。判据用简单名的词边界匹配
（覆盖 import 全限定名与同包简单名两种写法），偏保守 —— 注释里的提及也算，宁可多列。

当前：甲组 **100** 个，乙组 **44** 个。

## 甲：共用代码引用了的（接缝）

| 类型 | 出现的目标 |
|---|---|
| `com.atom.chat.AtomChat` | 1.20.1-forge, 1.21.1-fabric, 1.21.1-neoforge |
| `com.atom.chat.avatar.ColorPickerOverlay` | 1.21.1-fabric |
| `com.atom.chat.avatar.ImageCropper` | 1.21.1-fabric |
| `com.atom.chat.chat.ChatClassifier` | 1.21.1-fabric |
| `com.atom.chat.chat.ChatClassifierTest` | 1.21.1-fabric |
| `com.atom.chat.chat.ChatMessage` | 1.21.1-fabric |
| `com.atom.chat.chat.ChatMessageTest` | 1.21.1-fabric |
| `com.atom.chat.chat.ChatPipeline` | 1.21.1-fabric |
| `com.atom.chat.chat.Cicodes` | 1.21.1-fabric |
| `com.atom.chat.chat.EasyBotParser` | 1.21.1-fabric |
| `com.atom.chat.chat.LocalEcho` | 1.21.1-fabric |
| `com.atom.chat.chat.MentionHighlighterTest` | 1.21.1-fabric |
| `com.atom.chat.chat.MessageFilterTest` | 1.21.1-fabric |
| `com.atom.chat.chat.MessageGroupingTest` | 1.21.1-fabric |
| `com.atom.chat.chat.MessageMergeTest` | 1.21.1-fabric |
| `com.atom.chat.chat.OwnIdentity` | 1.20.1-forge, 1.21.1-fabric, 1.21.1-neoforge |
| `com.atom.chat.chat.PrivateChatParser` | 1.21.1-fabric |
| `com.atom.chat.chat.PrivateChatStoreTest` | 1.21.1-fabric |
| `com.atom.chat.chat.RichChatPartsTest` | 1.21.1-fabric |
| `com.atom.chat.chat.SenderMeta` | 1.21.1-fabric |
| `com.atom.chat.chat.SenderMetaTest` | 1.21.1-fabric |
| `com.atom.chat.chat.TeleportCommands` | 1.21.1-fabric |
| `com.atom.chat.chat.TellClickDetector` | 1.21.1-fabric |
| `com.atom.chat.config.ServerConfigScreen` | 1.20.1-forge, 1.21.1-fabric, 1.21.1-neoforge |
| `com.atom.chat.history.ChatHistory` | 1.20.1-forge, 1.21.1-fabric, 1.21.1-neoforge |
| `com.atom.chat.image.AvatarRenderer` | 1.21.1-fabric |
| `com.atom.chat.image.OwnPlayerAvatarSource` | 1.21.1-fabric |
| `com.atom.chat.image.SkinResolver` | 1.20.1-forge, 1.21.1-fabric, 1.21.1-neoforge |
| `com.atom.chat.mixin.ChatHudMixin` | 1.20.1-forge, 1.21.1-fabric, 1.21.1-neoforge |
| `com.atom.chat.mixin.MessageHandlerMixin` | 1.21.1-fabric |
| `com.atom.chat.mixin.MinecraftClientMixin` | 1.21.1-fabric |
| `com.atom.chat.mixin.MouseHandlerAccessor` | 1.21.1-fabric |
| `com.atom.chat.mixin.SuggestionWindowAccessor` | 1.21.1-fabric |
| `com.atom.chat.net.AvatarCompanionClient` | 1.20.1-forge, 1.21.1-fabric, 1.21.1-neoforge |
| `com.atom.chat.net.AvatarCompanionServer` | 1.20.1-forge, 1.21.1-fabric, 1.21.1-neoforge |
| `com.atom.chat.net.ConfigPayloads` | 1.20.1-forge, 1.21.1-fabric, 1.21.1-neoforge |
| `com.atom.chat.net.ConfigPayloadsCodecTest` | 1.20.1-forge, 1.21.1-fabric, 1.21.1-neoforge |
| `com.atom.chat.net.ConfigScreenClient` | 1.21.1-fabric |
| `com.atom.chat.net.MediaCompanionServer` | 1.20.1-forge, 1.21.1-fabric, 1.21.1-neoforge |
| `com.atom.chat.net.PackPayloads` | 1.20.1-forge, 1.21.1-fabric, 1.21.1-neoforge |
| `com.atom.chat.net.PackPayloadsCodecTest` | 1.20.1-forge, 1.21.1-fabric, 1.21.1-neoforge |
| `com.atom.chat.net.PokeCompanionServer` | 1.20.1-forge, 1.21.1-fabric, 1.21.1-neoforge |
| `com.atom.chat.notification.NotificationBanner` | 1.21.1-fabric |
| `com.atom.chat.page.ConversationListPage` | 1.21.1-fabric |
| `com.atom.chat.page.MessageListView` | 1.21.1-fabric |
| `com.atom.chat.page.ProfilePage` | 1.21.1-fabric |
| `com.atom.chat.platform.NeoForgePlatform` | 1.21.1-neoforge |
| `com.atom.chat.render.ClickableSpan` | 1.21.1-fabric |
| `com.atom.chat.render.OffscreenRenderTest` | 1.21.1-fabric |
| `com.atom.chat.render.PanelBlurRenderer` | 1.20.1-forge, 1.21.1-fabric, 1.21.1-neoforge |
| `com.atom.chat.render.RichTextRenderer` | 1.21.1-fabric |
| `com.atom.chat.render.SkiaGraphics` | 1.21.1-fabric |
| `com.atom.chat.screen.AtomChatScreen` | 1.20.1-forge, 1.21.1-neoforge |
| `com.atom.chat.settings.SettingsCatalog` | 1.21.1-fabric |
| `com.atom.chat.settings.SettingsHomePage` | 1.21.1-fabric |
| `com.atom.chat.settings.SettingsSectionPage` | 1.21.1-fabric |
| `com.atom.chat.text.ChatTextRewriter` | 1.21.1-fabric |
| `com.atom.chat.text.ChatTextRewriterTest` | 1.21.1-fabric |
| `com.atom.chat.text.RichText` | 1.21.1-fabric |
| `com.atom.chat.text.RichTextLayoutTest` | 1.21.1-fabric |
| `com.atom.chat.text.RichTextTest` | 1.21.1-fabric |
| `com.atom.chat.text.RichTextTestExtra` | 1.21.1-fabric |
| `com.atom.chat.ui.EmojiPanel` | 1.21.1-fabric |
| `com.atom.chat.ui.QuickPhrasePanel` | 1.21.1-fabric |
| `com.atom.chat.util.FilePicker` | 1.21.1-fabric |
| `com.atom.chat.util.ImagePreview` | 1.21.1-fabric |
| `net.minecraft.client.gui.screen.AtomChatScreen` | 1.21.1-fabric |
| `net.minecraft.client.gui.screen.AtomChatSuggestor` | 1.21.1-fabric |
| `net.minecraft.client.gui.screen.atomchat.ChatInputSuggestorAccessor` | 1.21.1-fabric |

## 乙：平台内部的

| 目标 | 类型 |
|---|---|
| 1.20.1-forge | `com.atom.chat.AtomChatClient` |
| 1.20.1-forge | `com.atom.chat.mixin.AtomChatMixinPlugin` |
| 1.20.1-forge | `com.atom.chat.net.AvatarPayloads` |
| 1.20.1-forge | `com.atom.chat.net.ConfigScreenServer` |
| 1.20.1-forge | `com.atom.chat.net.ForgeWireIo` |
| 1.20.1-forge | `com.atom.chat.net.MediaCompanionClient` |
| 1.20.1-forge | `com.atom.chat.net.MediaPayloads` |
| 1.20.1-forge | `com.atom.chat.net.PackNetClient` |
| 1.20.1-forge | `com.atom.chat.net.PackNetServer` |
| 1.20.1-forge | `com.atom.chat.net.PokeCompanionClient` |
| 1.20.1-forge | `com.atom.chat.net.PokePayloads` |
| 1.20.1-forge | `com.atom.chat.notification.NotificationController` |
| 1.20.1-forge | `com.atom.chat.platform.ForgePlatform` |
| 1.20.1-forge | `com.atom.chat.net.AvatarCompanionServerTest` |
| 1.21.1-fabric | `com.atom.chat.AtomChatClient` |
| 1.21.1-fabric | `com.atom.chat.mixin.AtomChatMixinPlugin` |
| 1.21.1-fabric | `com.atom.chat.net.AvatarPayloads` |
| 1.21.1-fabric | `com.atom.chat.net.ConfigScreenServer` |
| 1.21.1-fabric | `com.atom.chat.net.FabricWireIo` |
| 1.21.1-fabric | `com.atom.chat.net.MediaCompanionClient` |
| 1.21.1-fabric | `com.atom.chat.net.MediaPayloads` |
| 1.21.1-fabric | `com.atom.chat.net.PackNetClient` |
| 1.21.1-fabric | `com.atom.chat.net.PackNetServer` |
| 1.21.1-fabric | `com.atom.chat.net.PokeCompanionClient` |
| 1.21.1-fabric | `com.atom.chat.net.PokePayloads` |
| 1.21.1-fabric | `com.atom.chat.notification.NotificationController` |
| 1.21.1-fabric | `com.atom.chat.platform.FabricPlatform` |
| 1.21.1-fabric | `com.atom.chat.net.AvatarCompanionServerTest` |
| 1.21.1-fabric | `com.atom.chat.page.MessageListViewSelectionCopyTest` |
| 1.21.1-fabric | `com.atom.chat.text.RichTextSectionCodeTest` |
| 1.21.1-fabric | `com.atom.chat.ui.QuickPhrasePanelHitZoneTest` |
| 1.21.1-neoforge | `com.atom.chat.AtomChatClient` |
| 1.21.1-neoforge | `com.atom.chat.mixin.AtomChatMixinPlugin` |
| 1.21.1-neoforge | `com.atom.chat.net.AvatarPayloads` |
| 1.21.1-neoforge | `com.atom.chat.net.ConfigScreenServer` |
| 1.21.1-neoforge | `com.atom.chat.net.MediaCompanionClient` |
| 1.21.1-neoforge | `com.atom.chat.net.MediaPayloads` |
| 1.21.1-neoforge | `com.atom.chat.net.NeoForgeWireIo` |
| 1.21.1-neoforge | `com.atom.chat.net.PackNetClient` |
| 1.21.1-neoforge | `com.atom.chat.net.PackNetServer` |
| 1.21.1-neoforge | `com.atom.chat.net.PokeCompanionClient` |
| 1.21.1-neoforge | `com.atom.chat.net.PokePayloads` |
| 1.21.1-neoforge | `com.atom.chat.notification.NotificationController` |
| 1.21.1-neoforge | `com.atom.chat.net.AvatarCompanionServerTest` |
