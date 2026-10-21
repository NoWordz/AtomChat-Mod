package com.atom.chat.screen;
import com.atom.chat.AtomChat;

import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.PublicEchoTracker;
import com.atom.chat.chat.DraftStore;
import com.atom.chat.chat.DraftSessionStore;
import com.atom.chat.chat.ReplySlotStore;
import com.atom.chat.chat.SendPipeline;
import com.atom.chat.chat.WorldFeedFilter;
import com.atom.chat.history.HistoryFoldStore;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.Cicodes;
import com.atom.chat.chat.TeleportCommands;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.emote.EmoteStore;
import com.atom.chat.image.DragDropPasteUpload;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.image.ImagePreviewOverlay;
import com.atom.chat.image.ImageUploadPipeline;
import com.atom.chat.image.NativeDialogBridge;
import com.atom.chat.net.PokeCompanionClient;
import com.atom.chat.chat.LocalEcho;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.chat.PrivateChatStore;
import com.atom.chat.chat.PrivateEchoTracker;
import com.atom.chat.chat.OwnIdentity;
import com.atom.chat.text.RichText;
import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.AtomChatState;
import com.atom.chat.nav.NavPage;
import com.atom.chat.nav.NavStackController;
import com.atom.chat.nav.PageTransitionComposite;
import com.atom.chat.nav.PageTransitionDirector;
import com.atom.chat.notification.NotificationJumpService;
import com.atom.chat.avatar.AvatarImage;
import com.atom.chat.avatar.AvatarStore;
import com.atom.chat.avatar.ColorPickerOverlay;
import com.atom.chat.avatar.ImageCropper;
import com.atom.chat.banner.BannerImage;
import com.atom.chat.banner.BannerStore;
import com.atom.chat.page.ConversationListPage;
import com.atom.chat.page.HistoryWindow;
import com.atom.chat.page.MessageListView;
import com.atom.chat.page.PageHost;
import com.atom.chat.page.ProfilePage;
import com.atom.chat.font.FontManager;
import com.atom.chat.mixin.MouseHandlerAccessor;
import com.atom.chat.render.BlurMotionGate;
import com.atom.chat.render.ClickableSpan;
import com.atom.chat.render.DrawPrimitives;
import com.atom.chat.render.PanelBlurRenderer;
import com.atom.chat.render.RichTextRenderer;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.render.SkiaGraphics;
import com.atom.chat.settings.AvatarClickWiring;
import com.atom.chat.settings.ProfilePageWiring;
import com.atom.chat.settings.SettingsActions;
import com.atom.chat.settings.SettingsDetailPage;
import com.atom.chat.settings.SettingsHomePage;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.BottomTabBar;
import com.atom.chat.ui.CanvasLeakWatch;
import com.atom.chat.ui.ComposerInput;
import com.atom.chat.ui.ComposerPointer;
import com.atom.chat.ui.ContextMenuActions;
import com.atom.chat.ui.ContextMenuController;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.ScrollSyncService;
import com.atom.chat.ui.ShellHeader;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.EmojiPanel;
import com.atom.chat.ui.MessageListWiring;
import com.atom.chat.ui.HeaderChrome;
import com.atom.chat.ui.EmojiWiring;
import com.atom.chat.ui.PanelBackground;
import com.atom.chat.ui.PanelOpenMotion;
import com.atom.chat.ui.PressScale;
import com.atom.chat.ui.QuickPhrasePanel;
import com.atom.chat.ui.QuickPhraseWiring;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import com.atom.chat.ui.input.InputGate;
import com.atom.chat.ui.input.InputHandler;
import com.atom.chat.wallpaper.WallpaperImage;
import com.atom.chat.wallpaper.WallpaperStore;
import com.atom.chat.watut.TypingIndicatorSource;
import com.atom.chat.util.ClipboardImages;
import com.atom.chat.util.FilePicker;
import com.atom.chat.util.ImageFiles;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;
import io.github.humbleui.skija.SamplingMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;


import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AtomChatScreen extends ChatScreen implements PageHost, NotificationJumpService.NavPort {
    /** How this screen was opened: from the vanilla chat box or from the AtomChat key. */
    public enum AtomChatOpenMode { DIRECT_WORLD, RESTORE }

    private final Minecraft client = Minecraft.getInstance();

    private final ConversationListPage conversationListPage = new ConversationListPage(this);
    /** Local custom avatar for the profile page and own bubbles. */
    private final AvatarStore avatarStore = new AvatarStore(
            net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("atomchat/avatar"));
    /** QQ-style crop overlay for avatar and wallpaper picks. */
    private final ImageCropper imageCropper = new ImageCropper(SettingsActions.cropperCallback(avatarStore, client));
    /** Modal HSV colour picker opened from the settings colour rows. */
    private final ColorPickerOverlay colorPicker = new ColorPickerOverlay(this::copyToClipboard);
    /**
     * Full-panel preview of a chat picture, opened by clicking an image bubble
     * whose bitmap is already in hand. The third modal: while it is open,
     * {@link ModalInput} hands it every event.
     */
    private final ImagePreviewOverlay imagePreview = new ImagePreviewOverlay();

    /**
     * The profile page's avatar-and-banner half, the avatar click competition
     * window and poking (D18b). The window's verdict is reached inside a
     * rendered frame, from this screen's {@code nowMs} (see {@link #drawPanel}),
     * and the click's three branches live in the domain; this screen only hands
     * over the page, the poke config and client, and its own labels and sinks.
     * A message crosses as {@link AvatarClickWiring.MessageFacts}, because the
     * message type itself is spelled differently per mapping. The signature
     * borrow still belongs to {@link ProfilePageWiring} — the page's handler is
     * one object, so the two handlers are chained here.
     */
    private final AvatarClickWiring avatarWiring = new AvatarClickWiring(new AvatarClickWiring.Host() {
        @Override
        public ProfilePage profilePage() {
            return profilePage;
        }

        @Override
        public void openProfileDetailPage() {
            pushPage(AppPage.PROFILE_DETAIL);
        }

        @Override
        public java.util.UUID localPlayerId() {
            return client.player == null ? null : client.player.getUUID();
        }

        @Override
        public boolean pokeConfigEnabled() {
            return Animations.avatarPoke();
        }

        @Override
        public boolean animationsEnabled() {
            return Animations.enabled();
        }

        @Override
        public boolean clearAvatar() {
            return avatarStore.clear();
        }

        @Override
        public boolean pokeSend(java.util.UUID target) {
            return PokeCompanionClient.send(target);
        }

        @Override
        public void installPokeHandler(java.util.function.Consumer<java.util.UUID> handler) {
            PokeCompanionClient.setHandler(handler);
        }

        @Override
        public String displayNameFor(java.util.UUID uuid) {
            return AtomChatScreen.this.displayNameFor(uuid);
        }

        @Override
        public boolean isBlocked(String name) {
            return com.atom.chat.chat.BlockList.isBlocked(name);
        }

        @Override
        public void pokeWobble(java.util.UUID uuid, long nowMs) {
            messageListView.poke(uuid, nowMs);
        }

        @Override
        public void playPokeSound() {
            com.atom.chat.notification.NotificationController.playPokeSound();
        }

        @Override
        public void feedback(String key, boolean success) {
            AtomChatScreen.this.feedback(key, success);
        }

        @Override
        public String label(String key, String arg) {
            return tr(key, arg);
        }

        @Override
        public void feedback(String key, String label, boolean success) {
            AtomChatScreen.this.feedback(key, label, success);
        }

        @Override
        public void pickAvatarFile() {
            AtomChatScreen.this.pickAvatarFile();
        }

        @Override
        public void pickBannerFile() {
            AtomChatScreen.this.pickBannerFile();
        }

        @Override
        public boolean clearBanner() {
            return BannerStore.clear();
        }

        @Override
        public boolean copyToClipboard(String text) {
            return AtomChatScreen.this.copyToClipboard(text);
        }

        @Override
        public void beginSignatureEdit() {
            profileWiring.beginSignatureEdit();
        }

        @Override
        public void endSignatureEdit(boolean commit) {
            profileWiring.endSignatureEdit(commit);
        }
    });

    private final ProfilePage profilePage = new ProfilePage(new ProfilePage.Handler() {
        @Override
        public void openAvatarPicker() {
            avatarWiring.handler().openAvatarPicker();
        }

        @Override
        public void clearAvatar() {
            avatarWiring.handler().clearAvatar();
        }

        @Override
        public void openBannerPicker() {
            avatarWiring.handler().openBannerPicker();
        }

        @Override
        public void clearBanner() {
            avatarWiring.handler().clearBanner();
        }

        @Override
        public boolean copyText(String text) {
            return avatarWiring.handler().copyText(text);
        }

        @Override
        public void beginSignatureEdit() {
            profileWiring.beginSignatureEdit();
        }

        @Override
        public void endSignatureEdit(boolean commit) {
            profileWiring.endSignatureEdit(commit);
        }
    }, avatarStore);

    /**
     * The profile page's signature half (D18a): the composer borrow behind the
     * signature editor, the per-frame draft feed, the page's draw path and the
     * role row's resolver. The parking-ticket order and the "abort, never commit"
     * rule for the four navigation paths live in {@link ProfilePageWiring}; this
     * screen hands it the page, D21's composer, D10/D11's layout and scroll, the
     * two coordinate conversions and the two local-player facts.
     */
    private final ProfilePageWiring profileWiring = new ProfilePageWiring(new ProfilePageWiring.Host() {
        @Override
        public ProfilePage profilePage() {
            return profilePage;
        }

        @Override
        public String composerText() {
            return composer.text();
        }

        @Override
        public void setComposerText(String text) {
            composer.setText(text);
        }

        @Override
        public void focusComposer() {
            composer.focusGuarded();
        }

        @Override
        public void caretToEnd() {
            composer.caretToEnd();
        }

        @Override
        public UiLayout detailLayout() {
            return AtomChatScreen.this.detailLayout();
        }

        @Override
        public ScrollSyncService scrollSync() {
            return scrollSync;
        }

        @Override
        public float toVirtualX(double guiX) {
            return AtomChatScreen.this.toVirtualX(guiX);
        }

        @Override
        public float toVirtualY(double guiY) {
            return AtomChatScreen.this.toVirtualY(guiY);
        }

        @Override
        public java.util.UUID localPlayerId() {
            return client.player == null ? null : client.player.getUUID();
        }

        @Override
        public boolean localPlayerHasPermissions(int level) {
            return client.player.hasPermissions(level);
        }

        @Override
        public boolean composerFocused() {
            return composer.focused() && composer.fieldFocused();
        }
    });
    private final SettingsHomePage settingsHomePage = new SettingsHomePage();
    private final SettingsSectionPage settingsSectionPage = new SettingsSectionPage();

    /**
     * The settings screen's actions (D15a): the seven action cards, the three
     * native pickers behind them and the crop each picked file is framed with.
     * The domain owns the pickers and the cropper's confirm half; this screen
     * hands it the avatar store it built from this platform's config directory
     * (the domain never resolves one), the cropper and the section page it owns,
     * its own client handle — never Minecraft.getInstance(), D3's
     * precedent — D14's dialog bridge, D7's feedback, D4's history windows and
     * D10's two layouts.
     */
    private final SettingsActions settingsActions = new SettingsActions(new SettingsActions.Host() {
        @Override
        public AvatarStore avatarStore() {
            return avatarStore;
        }

        @Override
        public ImageCropper imageCropper() {
            return imageCropper;
        }

        @Override
        public SettingsSectionPage sectionPage() {
            return settingsSectionPage;
        }

        @Override
        public Minecraft client() {
            return client;
        }

        @Override
        public NativeDialogBridge dialogs() {
            return dialogBridge;
        }

        @Override
        public void feedback(String key, boolean success) {
            AtomChatScreen.this.feedback(key, success);
        }

        @Override
        public void resetHistoryWindows() {
            AtomChatScreen.this.resetHistoryWindows();
        }

        @Override
        public UiLayout layout() {
            return AtomChatScreen.this.layout();
        }

        @Override
        public UiLayout listLayout() {
            return AtomChatScreen.this.listLayout();
        }
    });

    /**
     * The pushed settings sub-page (D15b): its scroll state, the per-chip memory
     * it keeps, its frame and the inline numeric editor of its integer sliders.
     * The domain keeps the modal contract — the cropper and the colour picker
     * must be rendered on this page's draw path, or an active-but-unrendered
     * modal eats every click and freezes the screen. This screen hands it D16's
     * section page, D9's two modals and accent, D11's scroll service, D10's
     * detail layout and its own two coordinate conversions.
     */
    private final SettingsDetailPage settingsDetailPage = new SettingsDetailPage(new SettingsDetailPage.Host() {
        @Override
        public SettingsSectionPage sectionPage() {
            return settingsSectionPage;
        }

        @Override
        public ColorPickerOverlay colorPicker() {
            return colorPicker;
        }

        @Override
        public ImageCropper imageCropper() {
            return imageCropper;
        }

        @Override
        public ScrollSyncService scrollSync() {
            return scrollSync;
        }

        @Override
        public UiLayout detailLayout() {
            return AtomChatScreen.this.detailLayout();
        }

        @Override
        public int accent() {
            return AtomChatScreen.this.accent();
        }

        @Override
        public float toVirtualX(double guiX) {
            return AtomChatScreen.this.toVirtualX(guiX);
        }

        @Override
        public float toVirtualY(double guiY) {
            return AtomChatScreen.this.toVirtualY(guiY);
        }

    });

    {
        // Role row: vanilla 1.21.1 only syncs the local player's own permission
        // level; other players stay unknown (row hidden) until a server
        // companion can answer for them.
        profilePage.setRoleResolver(profileWiring.roleResolver());
    }

    /** Picks a custom avatar off-thread; the cropper opens on the render side. */
    private void pickAvatarFile() {
        settingsActions.pickAvatarFile();
    }

    /**
     * Picks a profile banner off-thread and crops it to the banner card's
     * aspect before storing; the crop result lands via
     * {@link ImageCropper.Callback#onConfirm} on the render thread, like every
     * other store write.
     */
    private void pickBannerFile() {
        settingsActions.pickBannerFile();
    }

    /** Root-page mouse coordinates in virtual UI space, for card hover rendering. */
    private float rootMouseX;
    private float rootMouseY;
    /** When true, ShellHeader is skipped while moving page layers in a transition. */
    private boolean suppressHeader;

    private final SkiaGraphics graphics = new SkiaGraphics();
    private boolean feedbackOpened;
    private final com.atom.chat.fun.ChatEffectPresentation chatEffects =
            new com.atom.chat.fun.ChatEffectPresentation((message, partner) -> !this.closing &&
                    (partner == null ? topPage() == AppPage.WORLD_CHAT && this.worldFeed.accepts(message)
                            : topPage() == AppPage.PRIVATE_CHAT && partner.equals(activePrivateTarget())));
    private final String originalChatText;

    private InputHandler outsidePanelInput() {
        return new com.atom.chat.ui.input.OutsidePanelInput(new com.atom.chat.ui.input.OutsidePanelInput.Host() {
            public UiLayout.Rect panelRect() { return layout().rect(); }
            public float toVirtualX(double x) { return AtomChatScreen.this.toVirtualX(x); }
            public float toVirtualY(double y) { return AtomChatScreen.this.toVirtualY(y); }
            public void requestClose() { AtomChatScreen.this.requestClose(); }
            public boolean clickFloatingUi(double x, double y, int button) {
                return isWorldChatPage() && !imagePreview.isActive() && !imageCropper.isActive()
                        && !colorPicker.isActive() && suggestorWiring.mouseClicked((int) x, (int) y, button);
            }
        });
    }
    /**
     * Ordered input routing (D23a). Priority (first registered wins): closing
     * guard, outside-panel close, modals (cropper/colour picker), pushed-subpage Esc, screen Esc,
     * root pages, world chat. New interactive features register a handler
     * instead of inserting branches into the former if-else chains.
     *
     * <p>The gate holds this chain and owns the one rule that decides whether a
     * pointer event reaches it at all — swallowed whole while the panel
     * animates, because hit tests run on the settled layout while the panel is
     * drawn mid-transform. It also owns the deliberate asymmetry (the keyboard
     * is not gated); see InputGate. The registration table stays here, on the
     * gate itself: its entries are this screen's own handler classes, and a
     * second chain would leave the gate's own one empty.</p>
     */
    private final InputGate gate = new InputGate(new InputGate.Host() {
        @Override
        public boolean panelAnimating() {
            return PanelOpenMotion.animating(panelProgress, closing);
        }
    })
            .add(new ClosingStateInput())
            .add(outsidePanelInput())
            .add(new ModalInput())
            .add(settingsDetailPage.numberEditInput())
            .add(new BannerInput())
            .add(new ScreenEscInput())
            .add(new RootPageInput())
            .add(new WorldChatInput());
    /**
     * Message list presentation: rendering, entrance animation, text selection
     * and hit geometry. The screen keeps the navigation-level scroll
     * controllers and all input-side interaction state; every value the view
     * needs from the screen is wired through the Host below.
     */
    /**
     * The message list's wiring (D22): what the list asks the screen for, the
     * messages a page owns, the click/drag coexistence flow and the hover span.
     * The span type stays a parameter — the mapping layer's record carries a
     * Minecraft style, and keeping it out of the domain is what lets the flow be
     * tested without one. Nothing here reads the navigation stack's top page: each
     * side of a transition asks for its own list, exactly like the scroll
     * controllers.
     */
    private final MessageListWiring<ClickableSpan> messageWiring =
            new MessageListWiring<>(new MessageListWiring.Host<ClickableSpan>() {
        @Override
        public java.util.UUID ownUuid() {
            return client.player != null ? client.player.getUUID() : null;
        }

        @Override
        public String ownName() {
            return AtomChatScreen.this.ownName();
        }

        @Override
        public String messageSenderName(ChatMessage message) {
            return AtomChatScreen.this.messageSenderName(message);
        }

        @Override
        public long openStart() {
            return AtomChatScreen.this.openStart;
        }

        @Override
        public java.util.List<ChatMessage> worldSnapshot() {
            return ChatStore.get().snapshot();
        }

        @Override
        public java.util.List<ChatMessage> privateMessages(PlayerRef target) {
            return PrivateChatStore.messages(target);
        }

        @Override
        public WorldFeedFilter worldFeed() {
            return worldFeed;
        }

        @Override
        public ClickableSpan clickableSpanAt(float vmx, float vmy) {
            return messageListView.clickableSpanAt(vmx, vmy).orElse(null);
        }

        @Override
        public boolean hasClickEvent(ClickableSpan span) {
            return span.style().getClickEvent() != null;
        }
    });

    private final MessageListView messageListView = new MessageListView(messageWiring.viewHost());
    /**
     * Emoji / kaomoji / emote panel: state, geometry and rendering live in the
     * panel class; the screen only supplies composer side effects (insert
     * text, send sticker, open the picker).
     */

    /**
     * The emoji panel's wiring (D17): the three screen-side effects its host needs,
     * plus the add slot's picker. This screen builds the panel with the wiring's
     * host and hands the panel back through the wiring's own port; the two
     * press-clearing lines the picker opens with stay here (they run synchronously
     * and without touching window focus, while D14's bridge is a hop plus a focus —
     * a different shape; see the D17 report).
     */
    private final EmojiWiring emojiWiring = new EmojiWiring(new EmojiWiring.Host() {
        @Override
        public EmojiPanel panel() {
            return emojiPanel;
        }

        @Override
        public void appendToComposer(String text) {
            composer.append(text);
        }

        @Override
        public void uploadSticker(Path file) {
            uploadAndAppend(file);
        }

        @Override
        public void execute(Runnable task) {
            client.execute(task);
        }

        @Override
        public void suspendAutoIconify() {
            dialogBridge.suppressAutoIconify();
        }

        @Override
        public void restoreAutoIconify() {
            dialogBridge.restoreAutoIconify();
        }

        @Override
        public void refocusWindow() {
            dialogBridge.refocusWindow();
        }
    });
    private ChatMessage reactionTarget;
    private final EmojiPanel emojiPanel = new EmojiPanel(emojiWiring.panelHost());

    /**
     * Quick phrases above the composer. Tapping one inserts it into the input
     * and closes the panel — the phrase can still be edited before sending.
     */
    /**
     * The composer's own surface (D21a): the text the shared field holds, the focus
     * flag, the caret and the wrap/height geometry. Every call it makes on the
     * hidden field is one method of {@link ComposerInput.Field} — that port is what
     * makes the 256 cap, the focus order, the wrap cache and the caret mapping
     * testable without a game, and this screen stays its only implementation. The
     * per-frame focus read-back and the read-only blur stay separate calls, because
     * they sit in the frame where they always did.
     */
    private final ComposerInput composer = new ComposerInput(new ComposerInput.Host() {
        @Override
        public UiLayout layout() {
            return AtomChatScreen.this.layout();
        }

        @Override
        public ScrollController currentScroll() {
            return AtomChatScreen.this.currentScroll();
        }

        @Override
        public float frameDt() {
            return frameDt;
        }

        @Override
        public int clampInt(int value, int lo, int hi) {
            return Mth.clamp(value, lo, hi);
        }
    }, new ComposerInput.Field() {
        @Override
        public boolean present() {
            return input != null;
        }

        @Override
        public String text() {
            return input.getValue();
        }

        @Override
        public void setText(String text) {
            input.setValue(text);
        }

        @Override
        public int caretPosition() {
            return input.getCursorPosition();
        }

        @Override
        public boolean focused() {
            return input.isFocused();
        }

        @Override
        public void setScreenFocus() {
            setFocused(input);
        }

        @Override
        public void fieldFocus() {
            input.setFocused(true);
        }

        @Override
        public void fieldFocusGuarded() {
            if (input != null) {
                input.setFocused(true);
            }
        }

        @Override
        public void fieldBlur() {
            input.setFocused(false);
        }

        @Override
        public void moveCaretTo(int pos) {
            input.moveCursorTo(pos, false);
        }
    });

    private final QuickPhrasePanel quickPhrasePanel = new QuickPhrasePanel(composer::append, this::accent);

    /**
     * The quick-phrase borrow (D16): the parked composer text, the add/edit
     * lifecycle and what each panel action does to the screen. The composer is
     * shared (D21 owns it, D18 borrows it too), so the domain keeps the parking
     * ticket and this screen hands it the panel, D21's reads/writes, the focus
     * and caret steps, and D24's read-only test.
     */
    private final QuickPhraseWiring quickPhraseWiring = new QuickPhraseWiring(new QuickPhraseWiring.Host() {
        @Override
        public QuickPhrasePanel panel() {
            return quickPhrasePanel;
        }

        @Override
        public String composerText() {
            return composer.text();
        }

        @Override
        public void setComposerText(String text) {
            composer.setText(text);
        }

        @Override
        public void appendToComposer(String text) {
            composer.append(text);
        }

        @Override
        public void focusComposer() {
            composer.focus();
        }

        @Override
        public void caretToEnd() {
            composer.caretToEnd();
        }

        @Override
        public boolean privateReadOnly() {
            return isPrivateReadOnly();
        }
    });

    /**
     * Per-conversation history fold depth (world="", private=PlayerRef.key()).
     * Not remembered across screen instances: closing the panel forgets how far
     * a conversation was unfolded, matching the public scroll reset. The state
     * and the reveal compensation live in {@link HistoryFoldStore}; the wrappers
     * below are the only entry points this shell keeps.
     */
    private final HistoryFoldStore historyFold = new HistoryFoldStore();
    /**
     * Notification banners, the jump behind their clicks and the action-feedback
     * queue (D7). Every fact and side effect it needs from another domain goes
     * through a port: this screen is itself D24's nav port (its own
     * {@link #topPage}, {@link #activePrivateTarget}, {@link #openPrivateChat}
     * and {@link #openWorldChat}), while D11's per-page scroll controllers (over
     * D22's feed) and D21's composer are the two ports wired below. The layout,
     * the message list view, the modal and read-only facts and the panel-space
     * pointer are passed in per call; the leak probe and the language lookup go
     * in as this screen's own functions, so the service keeps the exact
     * measurement and copy the old code used.
     */
    private final NotificationJumpService notificationJump = new NotificationJumpService(
            this,
            new NotificationJumpService.ScrollPort() {
                @Override
                public List<ChatMessage> currentMessages() {
                    return messageWiring.messagesForNav(topNav());
                }

                @Override
                public ScrollController currentScroll() {
                    return scrollForNav(topNav());
                }
            },
            new NotificationJumpService.ComposerFocusPort() {
                @Override
                public boolean composerPresent() {
                    return input != null;
                }

                @Override
                public void focusComposer() {
                    composer.focus();
                }
            },
            CanvasLeakWatch::probe,
            AtomChatScreen::tr);
    /**
     * The command/mention suggestion popup (D8): the anchored suggestor, the
     * anchors it opens at, its render hook and the three input priorities it
     * owns. The wiring holds the suggestor; this screen hands it the facts it
     * reads — D21's one composer field, D10's layout and density, and the
     * window's GUI scale.
     */
    private final SuggestorWiring suggestorWiring = new SuggestorWiring(new SuggestorWiring.Host() {
        @Override
        public EditBox input() {
            return input;
        }

        @Override
        public UiLayout layout() {
            return AtomChatScreen.this.layout();
        }

        @Override
        public double density() {
            return uiDensity();
        }

        @Override
        public double scaleFactor() {
            return client.getWindow().getGuiScale();
        }
    });
    /**
     * Drawing primitives and theme tokens (D9): the composer's icon row, the
     * inline SVG icon set, the ARGB blend the hover washes use and the
     * press/hover state that row is drawn from. The buttons read three facts
     * off this screen — the frame clock and whether the emoji / quick-phrase
     * panel is open — and nothing else; geometry, the pointer and its
     * conversion stay with the shell.
     */
    private final DrawPrimitives primitives = new DrawPrimitives(new DrawPrimitives.Host() {
        @Override
        public float frameDt() {
            return frameDt;
        }

        @Override
        public boolean emojiPanelOpen() {
            return emojiPanel.isOpen();
        }

        @Override
        public boolean phrasePanelOpen() {
            return quickPhrasePanel.isOpen();
        }
    });
    /**
     * Scroll state, its scrollbar and the jump-to-latest pill (D11). The service
     * owns the four controllers and the pill's spring/hover/fade; this screen
     * hands it the frame clock, where the nav stack is (D24) and the drawing
     * primitives (D9). The wrappers below are the only entry points this shell
     * keeps, and the controllers it hands out are the same instances the pages
     * draw with.
     */
    private final ScrollSyncService scrollSync = new ScrollSyncService(new ScrollSyncService.Host() {
        @Override
        public long frameDt() {
            return frameDt;
        }

        @Override
        public AppPage topPage() {
            return AtomChatScreen.this.topPage();
        }

        @Override
        public PlayerRef activePrivateTarget() {
            return AtomChatScreen.this.activePrivateTarget();
        }

        @Override
        public DrawPrimitives primitives() {
            return primitives;
        }
    });
    /**
     * The image upload chain and the save-image path (D12): server-hosted media
     * with the external host behind it, the CICode it drops into the draft, and
     * the placeholder-hint state that goes with it. The pipeline owns the
     * uploader and the hint (D21 reads the hint back every frame); this screen
     * hands it the render-thread hop — its own client handle, never
     * Minecraft.getInstance() (D3's precedent) — D21's two composer
     * calls, D7's toast and D14's save dialog.
     */
    private final ImageUploadPipeline uploadPipeline =
            new ImageUploadPipeline(new ImageUploadPipeline.Host() {
                @Override
                public Minecraft client() {
                    return client;
                }

                @Override
                public boolean composerEmpty() {
                    return composer.text().isEmpty();
                }

                @Override
                public void appendToComposer(String text) {
                    composer.append(text);
                }

                @Override
                public void toast(String key, boolean success) {
                    feedback(key, success);
                }

                @Override
                public Path pickSavePath(String suggestedName) {
                    // D14's dialog plumbing, in the order the save path always
                    // spelled out: suspend auto-iconify around the picker, hand
                    // OS focus back, and only then let the caller download.
                    suppressAutoIconify();
                    Path target;
                    try {
                        target = FilePicker.pickSavePath(suggestedName);
                    } finally {
                        restoreAutoIconify();
                    }
                    refocusWindow();
                    return target;
                }
            }, AtomChatScreen::tr);

    /**
     * File drops and clipboard pastes (D13): the window-wide drop callback's
     * lifetime and the two entry points that turn external bytes into an upload.
     * The domain owns the native slot and the clipboard read (both replaceable
     * offline); this screen hands it its own client handle — the window the
     * callback is installed on, never Minecraft.getInstance(), D3's
     * precedent — D24's page test and D12's uploader.
     */
    private final DragDropPasteUpload dragDropPaste =
            new DragDropPasteUpload(new DragDropPasteUpload.Host() {
                @Override
                public Minecraft client() {
                    return client;
                }

                @Override
                public boolean worldChatPage() {
                    return isWorldChatPage();
                }

                @Override
                public ImageUploadPipeline uploads() {
                    return uploadPipeline;
                }
            });

    /**
     * The window plumbing every native picker needs (D14): suspend GLFW's
     * auto-iconify while a dialog owns input, hand focus back when it closes,
     * and the clipboard write the menus report with. The bridge owns the
     * pairing, the clear-then-focus order and the "false, never throw" clipboard
     * contract; this screen hands it its own client handle — never
     * Minecraft.getInstance(), D3's precedent.
     */
    private final NativeDialogBridge dialogBridge = new NativeDialogBridge(new NativeDialogBridge.Host() {
        @Override
        public Minecraft client() {
            return client;
        }
    });
    /**
     * Composer drafts per page, and the shared field they are loaded into. The
     * draft text lives in {@link DraftStore}; the port below is this shell's half
     * of it (the EditBox itself belongs to D21) — see {@link DraftStore.ComposerTextPort}
     * for why a missing composer is passed through as null rather than "".
     */
    private DraftStore draftStore;
    /** Connection-session token captured with the shared draft store. */
    private long draftGeneration;
    private final DraftStore.ComposerTextPort composerText = new DraftStore.ComposerTextPort() {
        @Override
        public String get() {
            return composer.text();
        }

        @Override
        public void set(String text) {
            if (input != null) {
                input.setValue(text);
            }
        }

        @Override
        public void moveCursorToStart() {
            if (input != null) {
                input.moveCursorToStart(false);
            }
        }
    };
    /**
     * Reply bar state per conversation ("" = public feed), keyed like
     * {@link HistoryFoldStore}'s windows. The bar belongs to a page, not to the screen, so the
     * state lives in {@link ReplySlotStore} (moved out of the screen); the four
     * wrappers below are the only entry points this shell keeps.
     */
    private final ReplySlotStore replySlots = new ReplySlotStore();
    /**
     * The send path (D3). Every fact and every side effect it needs from another
     * domain goes through this Host: D24's stack top, D1's reply slots, D21's
     * composer, D11's scroll controller, and the screen's own client handle —
     * the last one deliberately as a getter, so the pipeline sees the same
     * pre-{@code init()} null the old code saw.
     */
    private final SendPipeline sendPipeline = new SendPipeline(new SendPipeline.Host() {
        @Override
        public Minecraft client() {
            return client;
        }

        @Override
        public NavPage topNav() {
            return navStack.topNav();
        }

        @Override
        public ReplySlotStore replySlots() {
            return replySlots;
        }

        @Override
        public void clearComposer() {
            composer.setText("");
        }

        @Override
        public void focusComposer() {
            composer.setFocused(true);
        }

        @Override
        public void stickToBottom() {
            currentScroll().stickToBottom();
        }

        @Override
        public boolean closeAfterSend() {
            return AtomChatConfig.get().closeAfterSend;
        }

        @Override
        public void requestClose() {
            AtomChatScreen.this.requestClose();
        }
    });

    /**
     * The right-click menu (D19a): its three modes, its animation state and its
     * drawing. The whole geometry — row count included — lives in
     * {@link ContextMenuController}, so this screen's two hit tests and the two
     * draw sites below all measure the same box; {@code M} is this screen's row
     * identity, carried so the "same row again closes" toggle still compares the
     * message itself. The menu's target crosses as a neutral subject, because the
     * message type's chat component is spelled differently per mapping.
     */
    private final ContextMenuController<ChatMessage> contextMenu =
            new ContextMenuController<>(new ContextMenuController.Host() {
        @Override public boolean canReact(Object row) {
            return row instanceof ChatMessage message && com.atom.chat.reaction.ReactionClient.available(message);
        }
        @Override
        public String label(String key) {
            return tr(key);
        }

        @Override
        public boolean isPlayerOnline(PlayerRef player) {
            return contextActions.isOnlinePlayer(player);
        }

        @Override
        public UiLayout.Rect panelRect() {
            return layout().rect();
        }

        @Override
        public float frameDt() {
            return frameDt;
        }

        @Override
        public void drawIcon(Canvas canvas, io.github.humbleui.skija.Path icon, float cx, float cy,
                             float size, int color) {
            drawIconCentered(canvas, icon, cx, cy, size, color);
        }
    });

    /** The menu's neutral view of a message row: sender, image row, identity. */
    private static ContextMenuController.Subject<ChatMessage> menuSubjectOf(ChatMessage msg) {
        return msg == null ? null
                : ContextMenuController.Subject.of(
                        PlayerRef.of(msg.getSenderUuid(), msg.getProfileName()),
                        Cicodes.extractImageUrl(msg.getRawText()) != null, msg);
    }

    /**
     * What a menu row does (D19b): the avatar menu's four actions, the player
     * card's three, the teleport command and the shared online test. The tab list
     * crosses as neutral facts (so the matching rule is testable and this screen
     * stays the only place that reads the tab list), the teleport probe stays here
     * because it needs the client object, and a message crosses as
     * {@link ContextMenuActions.MessageFacts} — the message type's chat component
     * is spelled differently per mapping.
     */
    private final ContextMenuActions contextActions = new ContextMenuActions(new ContextMenuActions.Host() {
        @Override
        public java.util.List<ContextMenuActions.OnlinePlayer> onlinePlayers() {
            if (client == null || client.getConnection() == null) {
                return java.util.List.of();
            }
            java.util.List<ContextMenuActions.OnlinePlayer> out = new java.util.ArrayList<>();
            for (PlayerInfo entry : client.getConnection().getOnlinePlayers()) {
                out.add(ContextMenuActions.OnlinePlayer.of(entry.getProfile().getId(),
                        entry.getProfile().getName()));
            }
            return out;
        }

        @Override
        public boolean hasLocalPlayer() {
            return client.player != null;
        }

        @Override
        public String teleportCommand() {
            // auto: probe the server command tree, fall back on failure replies.
            return TeleportCommands.commandFor(client, AtomChatConfig.get().teleportCommandMode);
        }

        @Override
        public void sendCommand(String command) {
            client.player.connection.sendCommand(command);
        }

        @Override
        public void appendToComposer(String text) {
            composer.append(text);
        }

        @Override
        public void privateChatWith(PlayerRef player) {
            AtomChatScreen.this.openPrivateChat(player);
        }

        @Override
        public void profileDetailFor(PlayerRef player) {
            AtomChatScreen.this.openProfileDetail(player);
        }

        @Override
        public PlayerRef currentOrLastPlayer() {
            return contextMenu.currentOrLastPlayer();
        }
    });

    /** The actions' neutral view of a message row (D19b): both names resolved here. */
    private static ContextMenuActions.MessageFacts menuMessageOf(ChatMessage msg) {
        return msg == null ? null
                : ContextMenuActions.MessageFacts.of(msg.getSenderUuid(), msg.getProfileName(),
                        msg.getSenderName(), msg.isOwn(), msg.isSystem());
    }
    // Root tab transition (D24b-1). The bottom bar is the screen's: it draws,
    // hit-tests and holds the shared animator the capsule rides. Both transition
    // state machines and their clocks live in the director, which is handed that
    // animator; every read point below goes through it.
    private final BottomTabBar bottomTabBar = new BottomTabBar();
    /**
     * The two transition state machines (D24b-1): the page push/pop clock and the
     * root-tab clock, the one {@code pageNavStyle()} resolution they share, and
     * the nav-layer geometry. Built here, before the nav port below, because that
     * port forwards its ten transition steps to it. Its own port is the three
     * steps this screen owns: the draft save and reply capture a nav starts with,
     * and the pop a finished pop transition applies.
     */
    private final PageTransitionDirector director = new PageTransitionDirector(
            new PageTransitionDirector.Host() {
                @Override
                public void persistCurrentDraft() {
                    saveCurrentDraft();
                }

                @Override
                public void captureReplySlot() {
                    syncReplySlot();
                }

                @Override
                public void applyPendingPop() {
                    popNow();
                }
            },
            bottomTabBar.indicatorAnimator());

    /**
     * Public-feed view filter (all / system only / players only), cycled by the
     * header button. View-only: storage, unread counts and previews always see
     * the unfiltered feed. A fresh screen instance starts at ALL, so closing
     * and reopening the panel resets it by construction.
     */
    private final WorldFeedFilter worldFeed = new WorldFeedFilter();
    /** Hover wash behind the header filter button (world chat only). */
    private float filterButtonHover;
    /** Hover washes for the emoji panel tab strip (owned by {@link EmojiPanel}). */

    // Animation state — durations live in UiMotion so every transition is tuned
    // in one place and none of them can drift back to a sluggish value. The
    // panel open/close rides the same exponential clock (PANEL_OPEN_TAU_MS on
    // the way in, the shorter PANEL_CLOSE_TAU_MS on the way out), so its shape
    // and fade resolve on one timeline per direction.
    private static final int GLFW_KEY_V = 86;
    /** Slack around the panel in the fade layer, so bezel/shadow are not clipped. */
    private static final float LAYER_CHROME = 32.0F;
    private final long openStart = System.currentTimeMillis();
    private boolean closing;
    /**
     * Panel open/close progress (0 hidden, 1 open). Rides
     * {@link UiMotion#expApproach} at {@link UiMotion#PANEL_OPEN_TAU_MS}
     * opening and the shorter {@link UiMotion#PANEL_CLOSE_TAU_MS} closing —
     * the same exponential family as the page nav, not the old spring:
     * full-speed start, decaying tail, exact snap at the target, never
     * overshoots.
     */
    private float panelProgress = 0.0F;
    /** Wall-clock timestamp of the previous frame — the progress clock consumes real dt. */
    private long lastFrameNanos = System.nanoTime();
    /** Composer height seen by the previous blur-gate frame, for change detection. */
    private float lastBlurInputExtraH;
    private boolean blurDrawnThisFrame;

    // Per-frame animation state (smooth hover/popup transitions)
    private long frameDt = 16;
    private long lastFrameMs = System.currentTimeMillis();

    /**
     * The nav stack's port (D24a). One method per step the controller cannot take
     * itself, each a one-line call into the domain that already owns it: D1 reply
     * slots, D2 drafts, D11 scroll, D15 settings, D18 signature and subject, D19
     * online check, D21 composer, D25 transient bus — plus the two transition
     * state machines the shell still owns (D24b), asked for rather than moved.
     *
     * <p>The names are deliberately unlike this screen's own method names: the
     * source guards locate members by first textual occurrence, so a port that
     * borrowed one of them would capture the guard's anchor.
     */
    private final NavStackController.Host navHost = new NavStackController.Host() {
        @Override
        public boolean signatureEditLive() {
            return profilePage.isSignatureEditing();
        }

        @Override
        public void abortSignatureEdit() {
            profilePage.endSignatureEdit(false);
        }

        @Override
        public void persistCurrentDraft() {
            saveCurrentDraft();
        }

        @Override
        public void persistPrivateDraft(PlayerRef target, String text) {
            savePrivateDraft(target, text);
        }

        @Override
        public void adoptDraft(PlayerRef target) {
            loadDraft(target);
        }

        @Override
        public void adoptWorldDraft() {
            loadWorldDraft();
        }

        @Override
        public String composerTextNow() {
            return composer.text();
        }

        @Override
        public void armReplyFor(NavPage page) {
            loadReplyFor(page);
        }

        @Override
        public void clearWorldScroll() {
            scrollSync.worldScroll().reset();
        }

        @Override
        public void clearDetailScroll() {
            scrollSync.detailScroll().reset();
        }

        @Override
        public void clearRootScroll() {
            scrollSync.rootScroll().reset();
        }

        @Override
        public void clearSettingsPageState() {
            resetSettingsUi();
        }

        @Override
        public void clearTransientWorldUi() {
            resetTransientWorldUi();
        }

        @Override
        public boolean isPlayerOnline(PlayerRef target) {
            return contextActions.isOnlinePlayer(target);
        }

        @Override
        public void dropProfileSubject() {
            profilePage.resetSubject();
        }

        @Override
        public String copy(String key) {
            return tr(key);
        }

        @Override
        public void beginPageNav(NavPage from, NavPage to, boolean popPending) {
            director.startPageNav(from, to, popPending);
        }

        @Override
        public void abandonPageNav() {
            director.abandonPageNav();
        }

        @Override
        public void abandonRootTransition() {
            director.clearRootTransition();
        }

        @Override
        public boolean rootTransitionLive() {
            return director.rootTransitionActive();
        }

        @Override
        public boolean navStyleIsZoom() {
            return director.navStyleIsZoom();
        }

        @Override
        public void pinRootTabCapsule(AppPage from) {
            director.pinTabCapsule(from);
        }

        @Override
        public void markRootTabSpan(AppPage from, AppPage to) {
            director.markTabSpan(from, to);
        }

        @Override
        public void applyRootTabMotion(boolean zoom) {
            director.applyTabMotion(zoom);
        }

        @Override
        public void resetRootTabCapsule() {
            director.resetTabCapsule();
        }

        @Override
        public void selectTabNow(AppPage root) {
            bottomTabBar.setSelectedImmediate(director.rootIndex(root));
        }
    };
    /**
     * The one holder of the nav stack. Built in the constructor, where every field
     * the port above reads is already initialised; the screen keeps one delegating
     * method per query and per cascade, so {@code PageHost} and
     * {@code NotificationJumpService.NavPort} stay implemented here and every
     * existing call site (this screen's own, and the pages handed {@code this})
     * is untouched.
     */
    private final NavStackController navStack;

    public AtomChatScreen(String originalChatText) {
        this(originalChatText, AtomChatOpenMode.DIRECT_WORLD);
    }

    public AtomChatScreen(String originalChatText, AtomChatOpenMode mode) {
        super(originalChatText);
        this.originalChatText = originalChatText;
        // Screen objects are constructed fresh on every open (init() re-runs on
        // resize), so this is the right hook for picking up hand-edited config
        // — chat/whisper templates take effect without a game restart.
        AtomChatConfig.reload();
        this.draftGeneration = DraftSessionStore.generation();
        this.draftStore = AtomChatConfig.get().retainDraftOnClose
                ? DraftSessionStore.current() : new DraftStore();
        draftStore.seedWorld(originalChatText);
        // The stack, its queries and the three cascades live in the controller
        // (D24a), loading included: one screen opening replays the saved stack
        // (the AtomChat key) or pushes the public feed (the vanilla chat key),
        // and lands the tab bar and the two active stores on the top page.
        this.navStack = new NavStackController(navHost, mode == AtomChatOpenMode.RESTORE);
    }

    private NavPage topNav() {
        return navStack.topNav();
    }

    @Override
    public AppPage topPage() {
        return navStack.topPage();
    }

    private boolean isPrivateReadOnly() {
        return navStack.isPrivateReadOnly();
    }

    /**
     * {@link #isPrivateReadOnly()} for one nav page. A transition composites two
     * pages in the same frame, so the check must never read whatever happens to
     * sit on top of the stack while the other page is drawn.
     */
    private boolean isPrivateReadOnlyFor(NavPage page) {
        return navStack.isPrivateReadOnlyFor(page);
    }

    /** Composer hint of a read-only private page, from that page's partner. */
    private String readOnlyHintForNav(NavPage page) {
        return navStack.readOnlyHintForNav(page);
    }

    @Override
    public PlayerRef activePrivateTarget() {
        return navStack.activePrivateTarget();
    }

    /**
     * Detail chat pages (public world or a private conversation) share the whole
     * composer/message-list UI. The method name is kept for historical call
     * sites but also covers PRIVATE_CHAT.
     */
    private boolean isWorldChatPage() {
        return navStack.isWorldChatPage();
    }

    private boolean isVanillaChatKey(int keyCode, int scanCode) {
        return client != null && client.options.keyChat.matches(keyCode, scanCode);
    }

    /** Filter button: the back button's recipe, one slot to its right. */

    /**
     * The header's own chrome (D20): the back button's rect and hover, the fixed
     * header a pushed page draws, and every title the bar can carry. The back
     * button's rect is read back from here by the shell's header renders, and the
     * hover spring is stepped here by both the settled frame and the pushed
     * header — one value, the same positions as before. {@code suppressHeader} is
     * deliberately NOT here: the shell reads it in two of its own gates (the
     * settled header and the root header) and writes it in six transition spots,
     * so it stays where both sides can see it.
     */
    private final HeaderChrome headerChrome = new HeaderChrome(new HeaderChrome.Host() {
        @Override
        public String label(String key) {
            return tr(key);
        }

        @Override
        public String activePrivateName() {
            return activePrivateTarget() == null ? null : activePrivateTarget().realName();
        }

        @Override
        public UiLayout.Rect headerRect() {
            return layout().header;
        }

        @Override
        public UiLayout.Rect pushedHeaderRect() {
            return detailLayout().header;
        }

        @Override
        public float frameDt() {
            return frameDt;
        }

        @Override
        public int textPrimary() {
            return AtomChatScreen.this.textPrimary();
        }
    });
    /**
     * The transition frame compositor (D24b-2a). Nine port steps, each a one-line
     * forward to something this screen owns -- the page body (by ruling), the root
     * orchestration (until 24b-2b), the bezel, the two layouts, D20's header
     * action, D25's suppressHeader field and the E group's view.
     */
    private final PageTransitionComposite composite = new PageTransitionComposite(
            new PageTransitionComposite.Host() {
                @Override
                public AppPage topPage() {
                    return AtomChatScreen.this.topPage();
                }

                @Override
                public void suppressHeader(boolean value) {
                    suppressHeader = value;
                }

                @Override
                public PageTransitionComposite.ChatPageView viewFor(NavPage page) {
                    return chatPageViewFor(page);
                }

                @Override
                public void drawChatPageBody(Canvas canvas, UiLayout layout, int mouseX, int mouseY,
                                             PageTransitionComposite.ChatPageView view) {
                    AtomChatScreen.this.drawChatPageBody(canvas, layout, mouseX, mouseY, view);
                }

                @Override
                public void drawRootScreen(Canvas canvas, int mouseX, int mouseY, AppPage rootPage) {
                    AtomChatScreen.this.drawRootScreen(canvas, mouseX, mouseY, rootPage);
                }

                @Override
                public void drawBezel(Canvas canvas, UiLayout layout) {
                    AtomChatScreen.this.drawBezel(canvas, layout);
                }

                @Override
                public UiLayout rootLayout() {
                    return AtomChatScreen.this.rootLayout();
                }

                @Override
                public UiLayout detailLayout() {
                    return AtomChatScreen.this.detailLayout();
                }

                @Override
                public ShellHeader.HeaderAction headerActionFor(NavPage page) {
                    return AtomChatScreen.this.headerActionFor(page);
                }
            },
            director, composer, headerChrome, primitives, contextMenu, contextActions,
            profileWiring, settingsDetailPage);
    private UiLayout.Rect filterButton() {
        UiLayout.Rect back = headerChrome.backButton();
        return new UiLayout.Rect(back.x() + back.w() + UiTokens.EDGE_CONTROL_INSET, back.y(), back.w(), back.h());
    }

    private boolean isFilterButtonHit(float vmx, float vmy) {
        return filterButton().contains(vmx, vmy);
    }

    /** Cycles the feed filter and drops a selection that may span hidden rows. */
    private void cycleWorldFilter() {
        worldFeed.cycle();
        messageListView.clearSelection();
    }

    /**
     * Header action for the page a header is titled with: the feed filter on
     * the world chat only. Funnel = nothing filtered (plain text colour);
     * speaker/person = system/players only, tinted accent so the active filter
     * reads at a glance (same language as the emoji button when its panel is
     * open).
     */
    private ShellHeader.HeaderAction headerActionFor(NavPage page) {
        // The filter's state, icons and tint live in WorldFeedFilter; the button's
        // rect and hover wash are header chrome and stay here (D20).
        return worldFeed.headerActionFor(page, filterButton(), filterButtonHover);
    }

    @Override
    public void pushPage(AppPage page) {
        pushNav(NavPage.of(page));
    }

    /** Pushes the public world chat (used by the root Public card). */
    public void openWorldChat() {
        pushPage(AppPage.WORLD_CHAT);
    }

    /** Pushes a private conversation page from a root player card. */
    public void openPrivateChat(PlayerRef target) {
        pushNav(NavPage.privateChat(target));
    }

    /** Pushes a settings sub-page from the settings home tile grid. */
    @Override
    public void openSettingsSection(SettingsSection section) {
        pushNav(NavPage.settingsSection(section));
    }

    private void pushNav(NavPage page) {
        navStack.pushNav(page);
                com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
    }

    @Override
    public void popPage() {
        navStack.popPage();
                com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
    }

    /** Applies a pending pop and restores the page underneath it. */
    private void popNow() {
        navStack.popNow();
    }

    /**
     * Clears the transient state of a settings sub-page when leaving it. The
     * three-part cleanup and the chip memory live in {@link SettingsDetailPage}
     * (D15b); this shell keeps the entry point the navigation paths call.
     */
    private void resetSettingsUi() {
        settingsDetailPage.resetSettingsUi();
    }

        /** Opens another player's profile as a pushed detail page. */
    public void openProfileDetail(PlayerRef player) {
        avatarWiring.openProfileDetail(player);
    }

    /** Opens the profile detail for a message's sender (avatar single click). */
    private void openProfileFor(ChatMessage msg) {
        avatarWiring.openProfileFor(factsOf(msg));
    }

    /**
     * The domain's neutral view of a message row. The message type itself is
     * spelled differently per mapping ({@code Component} vs {@code Text}), so the
     * shared layer never sees it — this screen converts, per click.
     */
    private static AvatarClickWiring.MessageFacts factsOf(ChatMessage msg) {
        return msg == null ? null
                : AvatarClickWiring.MessageFacts.of(msg.getSenderUuid(), msg.getProfileName(),
                        msg.getSenderName(), msg.isOwn(), msg.isSystem());
    }

    /** Opens the profile detail for a message's sender (avatar single click). */

    @Override
    public void switchRoot(AppPage root) {
        navStack.switchRoot(root);
                com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
    }

    private ScrollController currentScroll() {
        return scrollSync.currentScroll();
    }

    private ScrollController scrollForNav(NavPage page) {
        return scrollSync.scrollForNav(page);
    }

    /**
     * Composer text of one page. The hidden EditBox is a single instance, so
     * only the page that currently owns it ({@link #ownsInputField}) may read it
     * live; every other page draws its own saved draft. This is what lets a
     * transition draw the leaving and the arriving composer side by side without
     * showing the same text twice.
     */
    private String draftForNav(NavPage page) {
        return draftStore.draftForNav(page, ownsInputField(page), composerText);
    }

    /**
     * True while the shared EditBox holds {@code page}'s draft. Push and pop load
     * the arriving page's draft into the field, so during a nav the field belongs
     * to the page on top of the stack — the destination on a push, the page being
     * left on a pop — and to the arriving page once the nav settles.
     */
    private boolean ownsInputField(NavPage page) {
        NavPage top = navStack.topNav();
        return top != null && top.equals(page);
    }

    /** Persists the armed reply to the page that is currently open. A page that
     *  is not a chat page has no reply bar to persist. */
    private void syncReplySlot() {
        replySlots.syncReplySlot(topPage(), topNav());
    }

    /** Arms {@code page}'s saved reply, so the live field belongs to that page. */
    private void loadReplyFor(NavPage page) {
        replySlots.loadReplyFor(page);
    }

    /** Armed reply of a page: the live field when it owns the composer, else its slot. */
    private ChatMessage replyForNav(NavPage page) {
        return replySlots.replyForNav(page, ownsInputField(page));
    }

    /**
     * True while a reply bar is on screen. Both pages of a nav share one layout,
     * so either side's armed reply must reserve the row — otherwise the other
     * side's bar would be painted into the zero-height rect
     * {@code UiLayout.of} leaves at the panel origin when {@code replyH == 0}.
     */
    private boolean replyBarVisible() {
        return replySlots.replyBarVisible(director.pageNavFrom(), director.pageNavTo());
    }

    /** Fold window for a conversation, created on first view and kept across in-place growth. */
    private HistoryWindow historyWindowForNav(NavPage page, int total) {
        return historyFold.historyWindowForNav(page, total);
    }

    /** Forgets every conversation's fold depth (world switch, history clear, disconnect). */
    private void resetHistoryWindows() {
        historyFold.resetHistoryWindows();
    }

    /**
     * Reveals one more batch when the button was clicked this frame, then
     * scrolls by exactly the height the reveal inserted above the row that was
     * topmost, so the previously visible content does not jump.
     */
    private void applyLoadEarlierRequest(NavPage page, List<ChatMessage> messages, HistoryWindow window,
                                         UiLayout.Rect list, ScrollController scroll) {
        historyFold.applyLoadEarlierRequest(page, messages, window, list, scroll, messageListView);
    }

    /** Persists the hidden EditBox draft to the page that is currently open. */
    private void saveCurrentDraft() {
        boolean retain = AtomChatConfig.get().retainDraftOnClose;
        long savedGeneration = DraftSessionStore.saveForScreen(draftStore, topPage(),
                activePrivateTarget(), composerText, retain, draftGeneration);
        if (savedGeneration >= 0L) {
            draftGeneration = savedGeneration;
        }
    }

    private void savePrivateDraft(PlayerRef target, String text) {
        draftStore.savePrivateDraft(target, text);
    }

    private void loadDraft(PlayerRef target) {
        draftStore.loadDraft(target, composerText);
    }

    private void loadWorldDraft() {
        draftStore.loadWorldDraft(composerText);
    }

    /**
     * Clears world-chat-only ephemeral UI before navigating back to a root page,
     * so it cannot reappear when the world page is opened again. The chat draft
     * is preserved, but the message-list scroll state is reset so reopening the
     * channel starts fresh at the bottom.
     */
    private void resetTransientWorldUi() {
        scrollSync.worldScroll().reset();
        resetHistoryWindows();
        contextMenu.resetTransient();
        reactionTarget = null;
        emojiPanel.resetTransient();
        endPhraseEdit(false);
        quickPhrasePanel.resetTransient();
        replySlots.clearAll();
        messageListView.clearSelection();
        messageWiring.clearClick();
        dismissSuggestor();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Panel progress on the exponential clock (same family as the page
        // nav): expApproach clamps dt to 50ms internally and snaps exactly to
        // the target, so the equality below is the settle test — with
        // decorative motion off tau=0 lands this frame, closing included.
        long nowNanos = System.nanoTime();
        float frameDtMs = PanelOpenMotion.frameDtMs(nowNanos, lastFrameNanos);
        lastFrameNanos = nowNanos;
        panelProgress = PanelOpenMotion.stepProgress(panelProgress, closing, frameDtMs,
                Animations.enabled());
        boolean panelSettled = PanelOpenMotion.settled(panelProgress, closing);
        // Closing is cut at 6% left: past that the panel is a subpixel sliver
        // and the exponential tail would only delay setScreen(null) (see
        // PANEL_CLOSE_TAU_MS for why the close runs a shorter tau at all).
        if (PanelOpenMotion.closeDue(panelProgress, closing)) {
            this.client.setScreen(null);
            return;
        }
        // Feed the blur cadence gate: camera or panel-chrome motion keeps the
        // every-2-frames blur refresh; a still scene drops to the slow fallback
        // (see BlurMotionGate). The pre-pass below asks the gate per frame.
        boolean chromeAnimating = PanelOpenMotion.chromeAnimating(panelSettled,
                composer.extraH(), lastBlurInputExtraH);
        BlurMotionGate.noteFrame(System.currentTimeMillis(),
                client.player != null ? client.player.getYRot() : 0.0F,
                client.player != null ? client.player.getXRot() : 0.0F,
                chromeAnimating);
        lastBlurInputExtraH = composer.extraH();

        // The blur pre-pass is raw GL and must run before Skia paints the panel.
        // Load the shader first so drawPanel knows whether it may use the
        // translucent tint or must keep the solid fallback. The actual draw
        // result is tracked in blurDrawnThisFrame so a silent shader no-op can
        // never strip the solid background again.
        blurDrawnThisFrame = false;
        boolean blurWanted = AtomChatConfig.get().blurEnabled && !WallpaperStore.isSet();
        if (blurWanted) {
            // Shaders are registered through RegisterShadersEvent.
        }

        graphics.checkFrameBufferId();
        Runnable preUi = null;
        if (blurWanted && PanelBlurRenderer.isAvailable()) {
            preUi = () -> {
                try {
                    float strokeWidth = UiTokens.panelInset();
                    // The capture rect must be the rect drawPhone paints: the
                    // inset panel rect carried through the same open transform
                    // (zoom around the panel centre, then the slide offset).
                    PanelOpenMotion.PanelRect capture = PanelOpenMotion.blurCapture(
                            panelProgress, AtomChatConfig.get().pageNavStyle,
                            panelX(), panelY(), panelWidth(), panelHeight(), strokeWidth,
                            uiDensity(), this.client.getWindow().getGuiScale());
                    blurDrawnThisFrame = PanelBlurRenderer.render(
                            context.pose().last().pose(),
                            capture.x(), capture.y(), capture.w(), capture.h(), capture.radius(),
                            panelProgress);
                } catch (Throwable t) {
                    // One line, one stack trace, then the breaker: isAvailable()
                    // goes false, so the pre-pass is not even built on the next
                    // frame. Before this, a deterministic failure logged a full
                    // stack trace every frame for as long as the panel stayed
                    // open, which is how a real one-line cause turns into an
                    // unreadable log.
                    AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("ui", "AtomChat panel blur pre-pass failed, using solid background"), t);
                    PanelBlurRenderer.disable(t.getClass().getSimpleName());
                    blurDrawnThisFrame = false;
                }
            };
        }

        // No super.render: ChatScreen/Screen would draw the vanilla input box and
        // widget chrome; our UI is fully Skia-drawn, the suggestor renders explicitly.
        graphics.draw(preUi, uiDensity(),
                (canvas, worldSnapshot) -> drawPhone(canvas, worldSnapshot, mouseX, mouseY, delta));
        // The hidden EditBox stays positioned so the IME floating window anchors
        // correctly; its text/cursor are drawn by Skia above. The suggestion popup
        // still renders through the vanilla pipeline on top. Root pages do not
        // show the composer, so none of this may run outside WORLD_CHAT — the
        // one exception is the signature editor, which borrows the field on the
        // profile page and needs the anchor to follow it (the suggestor stays
        // composer-only).
        if (!closing && input != null
                && (isWorldChatPage() || profilePage.isSignatureEditing())) {
            composerPointer.positionField(layout());
            suggestorWiring.render(context, mouseX, mouseY, isWorldChatPage());
        }

        // Hover tooltips are drawn through the vanilla pipeline after the Skia
        // panel and the suggestion popup so they stay readable on top of both.
        // Whether a span is hovered at all is the domain's answer (D22), and it
        // answers null on a root page so a stale world span cannot show there.
        ClickableSpan hoveredSpan = messageWiring.hoverSpan(isWorldChatPage(),
                toVirtualX(mouseX), toVirtualY(mouseY));
        Style hovered = hoveredSpan != null ? hoveredSpan.style() : null;
        if (hovered != null && hovered.getHoverEvent() != null) {
            context.renderComponentHoverEffect(this.font, hovered, mouseX, mouseY);
        }
    }

    /**
     * Pushes the decorative-motion flag into every live scroll controller once
     * per frame. The controllers stay pure (no config reads of their own), so
     * the flag has to be handed to them; private controllers are created on
     * demand and therefore have to be refreshed too.
     */
    /**
     * The composer's hidden field and the pointer around it (D21b): the IME
     * anchor, the virtual-point→index hit test, the selection highlight and the
     * drag state. It reads D21a's composer surface and writes nothing back, so the
     * dependency runs one way; every {@code EditBox} call and the IMBlocker bridge
     * sit behind {@link ComposerPointer.Field}, which keeps the anchor arithmetic
     * testable without a game and this screen the only place that knows the field.
     */
    private final ComposerPointer composerPointer = new ComposerPointer(composer,
            new ComposerPointer.Host() {
        @Override
        public double density() {
            return uiDensity();
        }

        @Override
        public double guiScale() {
            return client.getWindow().getGuiScale();
        }

        @Override
        public int vanillaPrefixWidth(String prefix) {
            return client.font.width(prefix);
        }

        @Override
        public ProfilePage.SignatureAnchor signatureAnchor() {
            return profilePage.signatureImeAnchor(listLayout(), listScroll().getScrollY());
        }

        @Override
        public int clampInt(int value, int lo, int hi) {
            return Mth.clamp(value, lo, hi);
        }
    }, new ComposerPointer.Field() {
        @Override
        public boolean present() {
            return input != null;
        }

        @Override
        public void setFieldX(int x) {
            input.setX(x);
        }

        @Override
        public void setFieldY(int y) {
            input.setY(y);
        }

        @Override
        public void setFieldWidth(int w) {
            input.setWidth(w);
        }

        @Override
        public void setFieldHeight(int h) {
            input.setHeight(h);
        }

        @Override
        public int cursorPosition() {
            return input.cursorPos;
        }

        @Override
        public int highlightPosition() {
            return input.highlightPos;
        }

        @Override
        public void setCommandMode(boolean commandMode) {
            com.atom.chat.compat.IMBlockerCompat.setCommandMode(input, commandMode);
        }
    });

    private void syncScrollMotion() {
        scrollSync.syncScrollMotion();
    }

    /** Collapses the suggestion popup and clears the gray ghost suffix. */
    private void dismissSuggestor() {
        suggestorWiring.dismiss();
    }

    /**
     * The typing hint for one nav page: the hint belongs to the page
     * whose composer is drawn, not to whatever is on top of the stack. The
     * decision and the WATUT query live in {@link TypingIndicatorSource};
     * whether the page is read-only is D24's rule
     * ({@link #isPrivateReadOnlyFor}) and goes in as a fact.
     */
    private boolean partnerTypingFor(NavPage page) {
        return TypingIndicatorSource.partnerTypingFor(page, isPrivateReadOnlyFor(page));
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // World stays fully visible, same as vanilla chat; the panel provides its own background.
    }

    @Override
    protected void init() {
        super.init();
        chatEffects.install();
        if (!feedbackOpened) {
            feedbackOpened = true;
            com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.PANEL_OPEN);
        }
        // Route incoming remote pokes to this screen while it is open.
        avatarWiring.poke().install();
        // Swap the vanilla suggestor for our anchored one on the same chat field.
        // The popup, its anchors and its responder live in the D8 wiring; the
        // "may this popup be hidden" switch does not exist on 1.20.1, so the
        // versions that have it set it here.
        suggestorWiring.install(this.client, this, this.client.font, originalChatText)
                .setAllowHiding(false);
        // init() also runs on every resize, hence the guard inside.
        installDropCallback();
    }

    // ---- quick-phrase edit borrows the composer field (the IME carrier) ----

    /**
     * Begins adding or editing a phrase. The read-only guard, committing whatever
     * edit was already running, the parking ticket and the load-focus-caret
     * sequence live in {@link QuickPhraseWiring} (D16); this shell keeps the
     * entry point the panel's own callbacks use.
     */
    private void beginPhraseEdit(int target) {
        quickPhraseWiring.beginPhraseEdit(target);
    }

    /** Ends a phrase add/edit; commit=true saves the field text as the phrase. */
    private void endPhraseEdit(boolean commit) {
        quickPhraseWiring.endPhraseEdit(commit);
    }

    /** Closes the phrase panel; an in-progress edit commits (blur applies). */
    private void closePhrasePanel() {
        quickPhraseWiring.closePhrasePanel();
    }

    /**
     * Turns a panel click into the screen-level side effects. The five action types —
     * including the server rows' read-only insert-then-close — live in
     * {@link QuickPhraseWiring} (D16).
     */
    private void handlePhraseAction(QuickPhrasePanel.Action action) {
        quickPhraseWiring.handlePhraseAction(action);
        if (action.type() != QuickPhrasePanel.Action.NONE) com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
    }

    // The canvas save-stack leak probe -- its debug gate, its leak gate and its
    // log throttle -- lives in CanvasLeakWatch (D25); this screen is one of its
    // two callers.

    /**
     * Banners and action toasts (D7). The draw order — banners painted after the
     * page body, the toast last, both inside the panel layer — lives with
     * {@link NotificationJumpService#drawNotificationBanners}; the modal flag,
     * the layout and the panel-space pointer are this shell's facts.
     */
    private void drawNotificationBanners(Canvas canvas, int mouseX, int mouseY) {
        notificationJump.drawNotificationBanners(canvas,
                imageCropper.isActive() || colorPicker.isActive() || imagePreview.isActive(),
                layout(), toVirtualX(mouseX), toVirtualY(mouseY));
    }

    /**
     * Banner clicks jump to the message behind the notification. Registered
     * after the modals, so a cropper or colour picker still swallows the click
     * while it is open.
     */
    private final class BannerInput implements InputHandler {
        @Override
        public boolean onClick(double mouseX, double mouseY, int button) {
            // The click rules live in NotificationJumpService; the GUI->panel
            // conversion, the layout, the list view and the read-only flag are
            // this shell's facts.
            return notificationJump.onBannerClick(button, toVirtualX(mouseX), toVirtualY(mouseY),
                    isPrivateReadOnly(), layout(), messageListView);
        }
    }

    private void drawPhone(Canvas canvas, Image worldSnapshot, int mouseX, int mouseY, float delta) {
        float x = panelX();
        float y = panelY();
        float progress = panelProgress;
        // The one open transform, shared with the blur pre-pass's capture rect
        // (PanelOpenMotion.openScale/openSlidePx): ZOOM grows around the panel
        // centre, SLIDE pushes in from the left. Same mapping on both sides or
        // the blurred region drifts off the panel mid-flight.
        float scale = PanelOpenMotion.openScale(progress, AtomChatConfig.get().pageNavStyle);
        float slide = PanelOpenMotion.openSlidePx(progress, AtomChatConfig.get().pageNavStyle);
        // Settled state: expApproach snaps to its target, so progress never
        // lingers in [0.999, 1) — past this point the fade is fully opaque and
        // scale/slide are exactly 1/0, so the full-panel layer is pure
        // overdraw and the panel draws directly (same call as the nav branch).
        if (PageTransitionDirector.navLayerSkippable(PanelOpenMotion.fade(progress), scale)) {
            int probeBefore = canvas.getSaveCount();
            drawPanel(canvas, x, y, worldSnapshot, mouseX, mouseY, delta);
            drawNotificationBanners(canvas, mouseX, mouseY);
            CanvasLeakWatch.probe("drawPanel+banners", probeBefore, canvas);
            return;
        }
        canvas.save();
        try (Paint layer = new Paint()) {
            // expApproach never overshoots; the clamp keeps the fade safe anyway.
            float fade = PanelOpenMotion.fade(progress);
            layer.setColor(Color.makeARGB((int) (255.0F * fade), 0, 0, 0));
            // The layer must cover the scaled panel plus its slide excursion
            // (LAYER_CHROME slack for the bezel/shadow), otherwise the fade
            // clips the bezel while the panel is still in flight.
            float cx = x + panelWidth() / 2.0F;
            float cy = y + panelHeight() / 2.0F;
            PanelOpenMotion.LayerRect layerRect = PanelOpenMotion.fadeLayer(progress,
                    AtomChatConfig.get().pageNavStyle, x, y, panelWidth(), panelHeight(), LAYER_CHROME);
            canvas.saveLayer(Rect.makeXYWH(layerRect.x(), layerRect.y(), layerRect.w(), layerRect.h()),
                    layer);
            // Scale around the panel centre, then slide: the exact transform
            // the capture-rect math above mirrors.
            canvas.translate(cx + slide, cy);
            canvas.scale(scale, scale);
            canvas.translate(-cx, -cy);
            // The world snapshot sits inside the saveLayer/transform stack so it
            // fades in with the panel and rides the same motion — no special handling.
            int probeBefore = canvas.getSaveCount();
            drawPanel(canvas, x, y, worldSnapshot, mouseX, mouseY, delta);
            drawNotificationBanners(canvas, mouseX, mouseY);
            CanvasLeakWatch.probe("drawPanel+banners", probeBefore, canvas);
            canvas.restore();
        }
        canvas.restore();
    }

    private void requestClose() {
        closing = true;
    }

    /**
     * GLFW only hands file drops to whoever is listening, and Minecraft
     * registers no drop callback at all — the Win32 backend already calls
     * DragAcceptFiles, so the events have been arriving and being discarded.
     * The callback fires on the render thread, from inside glfwPollEvents.
     * The slot, the guard and the free live in {@link DragDropPasteUpload}
     * (D13); this shell keeps the lifecycle entry points init/removed call.
     */
    private void installDropCallback() {
        dragDropPaste.installDropCallback();
    }

    private void uninstallDropCallback() {
        dragDropPaste.uninstallDropCallback();
    }

    /** Window-wide: the drop event carries no cursor position we could hit-test with. */
    private void onFilesDropped(int count, long names) {
        dragDropPaste.onFilesDropped(count, names);
    }

    @Override
    public void removed() {
        chatEffects.close();
        avatarWiring.poke().uninstall();
        // Hygiene on screen teardown: release the borrowed field so the parked
        // composer draft state cannot outlive the page that borrowed it.
        if (profilePage.isSignatureEditing()) {
            profilePage.endSignatureEdit(false);
        }
        saveCurrentDraft();
        if (topPage() == AppPage.PRIVATE_CHAT) {
            PrivateChatStore.clearActive();
        }
        ChatStore.setPublicActive(false);
        AtomChatState.save(navStack.snapshot());
        uninstallDropCallback();
        // Give back the GPU texture the panel blur was sampling.
        graphics.releaseWorldSnapshot();
        messageListView.dispose();
        super.removed();
    }

    private void drawPanel(Canvas canvas, float x, float y, Image worldSnapshot, int mouseX, int mouseY, float delta) {
        composer.syncFocusedFromField();
        composer.blurOnReadOnlyPage(isPrivateReadOnly());
        chatEffects.session();
        long nowMs = System.currentTimeMillis();
        frameDt = PanelOpenMotion.domainFrameDtMs(nowMs, lastFrameMs);
        lastFrameMs = nowMs;
        // Single-click→profile competition window: fire when no second click
        // arrived within the threshold. The verdict stays inside this frame —
        // the domain only decides, the frame order is this method's.
        avatarWiring.settlePendingAvatarClick(nowMs, closing);
        syncScrollMotion();
        String reactionFailure = com.atom.chat.reaction.ReactionClient.takeError();
        if (reactionFailure != null) feedback("atomchat.reaction." + reactionFailure, false);
        emojiPanel.update(frameDt);
        quickPhrasePanel.update(frameDt);
        UiLayout layout = layout();
        UiLayout.Rect panel = layout.rect();
        // Phone bezel: background is inset by the full stroke width so nothing can
        // bleed outside; the white ring itself is drawn LAST (see end of method)
        // so every component sits beneath a clean edge.
        float strokeWidth = UiTokens.panelInset();
        // A custom wallpaper owns the panel background and beats the blur: one
        // shows the world through, the other covers it, so they never stack.
        Image wallpaper = WallpaperImage.current(WallpaperStore.current());
        float innerX = panel.x() + strokeWidth;
        float innerY = panel.y() + strokeWidth;
        float innerW = panel.w() - strokeWidth * 2.0F;
        float innerH = panel.h() - strokeWidth * 2.0F;
        float innerRadius = Math.max(0F, UiTokens.panelRadius() - strokeWidth);

        if (wallpaper != null) {
            // Solid base first, then the image on top at the configured
            // opacity — so the dark panel colour always shows through a little
            // and text stays readable over a bright photo.
            try (Paint bg = new Paint().setColor(PanelBackground.opaque(AtomChatConfig.get().panelBgColor))) {
                canvas.drawRRect(RRect.makeXYWH(innerX, innerY, innerW, innerH, innerRadius), bg);
            }
            canvas.save();
            try {
                SkiaDraw.clip(canvas, innerX, innerY, innerW, innerH, innerRadius);
                float scale = Math.max(innerW / wallpaper.getWidth(), innerH / wallpaper.getHeight());
                float drawW = wallpaper.getWidth() * scale;
                float drawH = wallpaper.getHeight() * scale;
                float dx = innerX + (innerW - drawW) / 2.0F;
                float dy = innerY + (innerH - drawH) / 2.0F;
                try (Paint paint = new Paint().setAlphaf(
                        Math.max(0.0F, Math.min(1.0F, AtomChatConfig.get().panelOpacity)))) {
                    canvas.drawImageRect(wallpaper,
                            io.github.humbleui.types.Rect.makeXYWH(0, 0, wallpaper.getWidth(), wallpaper.getHeight()),
                            io.github.humbleui.types.Rect.makeXYWH(dx, dy, drawW, drawH),
                            SamplingMode.LINEAR, paint, false);
                }
            } finally {
                canvas.restore();
            }
        } else {
            // The raw-GL blur pre-pass already painted the rounded blurred image
            // on the main framebuffer, so this layer is only the tint over it.
            // Both paths use the configured panel colour — the blur branch used
            // to hardcode 0xFF16191F, which made the background impossible to
            // recolour with blur enabled (0.2.5 report). A blur that was asked
            // for but did not land this frame falls back to an OPAQUE panel; see
            // PanelBackground for why the translucent tint is only safe when the
            // blur is deliberately off.
            int tint = PanelBackground.tintFor(AtomChatConfig.get().blurEnabled, blurDrawnThisFrame,
                    AtomChatConfig.get().panelBgColor, AtomChatConfig.get().panelOpacity);
            try (Paint bg = new Paint().setColor(tint)) {
                canvas.drawRRect(RRect.makeXYWH(innerX, innerY, innerW, innerH, innerRadius), bg);
            }
        }

        ShellHeader.drawBackdrop(canvas, layout.header);

        // One unified shell header is drawn before page content on every page.
        // Root pages add their body and the bottom tab bar; pushed pages run
        // through the navigation machinery below.
        // The pointer is mapped here rather than inside the composite: the
        // settled path below needs the same two values anyway.
        float vmx = toVirtualX(mouseX);
        float vmy = toVirtualY(mouseY);
        if (composite.drawRootFrame(canvas, layout, mouseX, mouseY, vmx, vmy)) {
            return;
        }
        PageTransitionComposite.NavFrame navFrame = composite.begin(canvas, layout, mouseX, mouseY,
                vmx, vmy, frameDt);
        if (navFrame.finished()) {
            return;
        }
        // A settled settings sub-page: header + list, nothing else.
        if (!navFrame.running() && topPage() == AppPage.SETTINGS_SECTION) {
            drawSettingsSection(canvas, mouseX, mouseY, topNav().section());
            headerChrome.drawPushedHeader(canvas, vmx, vmy, topNav());
            drawBezel(canvas, detailLayout());
            return;
        }
        // A settled profile detail: same chrome, profile body.
        if (!navFrame.running() && topPage() == AppPage.PROFILE_DETAIL) {
            drawProfileDetail(canvas, mouseX, mouseY);
            headerChrome.drawPushedHeader(canvas, vmx, vmy, topNav());
            drawBezel(canvas, detailLayout());
            return;
        }
        headerChrome.stepBackButtonHover(vmx, vmy);
        filterButtonHover = UiMotion.approach(filterButtonHover,
                topPage() == AppPage.WORLD_CHAT && isFilterButtonHit(vmx, vmy) ? 1.0F : 0.0F,
                frameDt, UiMotion.HOVER_MS);
        if (!suppressHeader) {
            ShellHeader.render(canvas, layout.header, headerChrome.shellTitleFor(topNav()), true,
                    headerChrome.backButton(), headerChrome.backButtonHover(), textPrimary(),
                    topPage() == AppPage.PRIVATE_CHAT
                            ? contextActions.isOnlinePlayer(activePrivateTarget()) : null,
                    headerActionFor(topNav()));
        }

        // Grow the input bar before the list is measured, so the list loses
        // exactly the height the bar gains.
        layout = composer.updateLayout(layout);

        if (!navFrame.movingHidden()) {
            drawChatPageBody(canvas, layout, mouseX, mouseY, chatPageViewFor(topNav()));

            messageListView.drawReactionHover(canvas, toVirtualX(mouseX), toVirtualY(mouseY),
                    com.atom.chat.ui.ReactionChips.tooltipSuppressed(
                            emojiPanel.isOpen(), quickPhrasePanel.isOpen(),
                            contextMenu.hasMessageMenu(), contextMenu.hasPlayerCardMenu(),
                            imagePreview.isActive()));
            emojiPanel.render(canvas, layout, toVirtualX(mouseX), toVirtualY(mouseY), frameDt);
            quickPhrasePanel.render(canvas, layout, toVirtualX(mouseX), toVirtualY(mouseY), frameDt);
            contextMenu.draw(canvas, toVirtualX(mouseX), toVirtualY(mouseY));
            // Last inside the panel: a modal over the page it was opened from.
            imagePreview.render(canvas, layout.rect(), toVirtualX(mouseX), toVirtualY(mouseY));
        }

        if (navFrame.running()) {
            composite.end(canvas, layout, navFrame);
        }

        com.atom.chat.fun.ChatEffectRenderer.draw(canvas, layout, chatEffects.session(),
                com.atom.chat.fun.ChatEffectPresentation.nowMs());
        // Bezel ring last: nothing at the panel edge can sit on top of it.
        drawBezel(canvas, layout);
    }

    /**
     * Everything {@link #drawChatPageBody} needs that the single hidden EditBox
     * makes shared: the composer text, the read-only and typing hints, the reply
     * bar and the scroll controller all belong to one {@link NavPage}. A
     * transition composites two pages in the same frame, so each side is
     * resolved into its own immutable view instead of both re-reading the
     * top-page helpers — which is exactly how the arriving page's draft used to
     * be painted onto the leaving page.
     */
    /** Render state of a chat page, or null for a page that has no composer. */
    private PageTransitionComposite.ChatPageView chatPageViewFor(NavPage page) {
        if (page == null || (page.page() != AppPage.WORLD_CHAT && page.page() != AppPage.PRIVATE_CHAT)) {
            return null;
        }
        boolean readOnly = isPrivateReadOnlyFor(page);
        return new PageTransitionComposite.ChatPageView(page, draftForNav(page), readOnly,
                readOnly ? readOnlyHintForNav(page) : "",
                partnerTypingFor(page), replyForNav(page), scrollForNav(page),
                ownsInputField(page));
    }

    /**
     * The whole chat page body — messages, reply bar, composer, scrollbar,
     * jump-to-latest — drawn for one page view. Self-contained so the full-width
     * page push can slide it as one piece; the settled path calls it with the
     * top page's view. The header and the emoji/context overlays stay with the
     * caller: the header is a fixed status bar during pushes, and the overlays
     * belong to the screen, not the sliding page.
     */
    private void drawChatPageBody(Canvas canvas, UiLayout layout, int mouseX, int mouseY,
                                  PageTransitionComposite.ChatPageView view) {
        if (view == null) {
            return;
        }
        NavPage page = view.page();
        int probeBefore = canvas.getSaveCount();
        List<ChatMessage> messages = messageWiring.messagesForNav(page);
        HistoryWindow window = historyWindowForNav(page, messages.size());
        messageListView.setVisibleStart(window.visibleStart());
        messageListView.setLoadEarlierHovered(toVirtualX(mouseX), toVirtualY(mouseY));
        SkiaDraw.drawChromeShadow(canvas, layout.inputBar.x(), layout.inputBar.y(), layout.inputBar.w(),
                layout.inputBar.h(), UiTokens.shellChromeRadius());
        com.atom.chat.render.DotTexture.drawEdge(canvas, layout.inputBar, UiTokens.shellChromeRadius(), true,
                AtomChatConfig.get().textSecondaryColor);
        messageListView.draw(canvas, layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h(),
                messages, view.scroll());
        CanvasLeakWatch.probe("messageListView.draw", probeBefore, canvas);
        applyLoadEarlierRequest(page, messages, window, layout.list, view.scroll());

        // Reply bar floats above the input bar. It is drawn after the message
        // list so it always sits on top; the layout keeps an 8px gap below it.
        ChatMessage reply = view.reply();
        if (reply != null) {
            UiLayout.Rect replyRect = layout.replyBar;
            float replyH = s(26);
            SkiaDraw.drawRoundedRect(canvas, replyRect.x(), replyRect.y(), replyRect.w(), replyH,
                    UiTokens.radius(8), UiTokens.quoteAccent(accent()));
            Font replyFont = FontManager.font(UiTokens.FONT_NAME);
            String replyLabel = tr("atomchat.reply.to", messageSenderName(reply),
                    abbreviate(reply.getContentText(), 26));
            SkiaFontRenderer.drawText(canvas, replyFont, replyLabel, replyRect.x() + UiTokens.QUOTE_PAD_X,
                    SkiaFontRenderer.centerBaselineY(replyFont, replyRect.y() + s(13)), textPrimary());
        }

        // Input bar: one button row (image / emoji … send), text row below.
        // The list layout already ends at this bar's top, so the translucent
        // card never has message content underneath it.
        UiLayout.Rect bar = layout.inputBar;
        boolean readOnly = view.readOnly();
        SkiaDraw.drawRoundedRect(canvas, bar.x(), bar.y(), bar.w(), bar.h(), UiTokens.shellChromeRadius(), UiTokens.cardFill());
        com.atom.chat.render.ChromeMaterial.drawDetails(canvas, bar, UiTokens.shellChromeRadius());
        if (quickPhrasePanel.isEditing()) {
            // The composer is borrowed as the phrase editor: ring it with the
            // accent so the mode is unmistakable.
            try (Paint border = new Paint().setMode(PaintMode.STROKE).setAntiAlias(true)
                    .setColor(accent()).setStrokeWidth(s(1.5F))) {
                canvas.drawRRect(io.github.humbleui.types.RRect.makeXYWH(
                        bar.x(), bar.y(), bar.w(), bar.h(), UiTokens.shellChromeRadius()), border);
            }
        }
        if (!readOnly) {
            // Draw the pointer's button last: its scale-up then reads as growth
            // instead of being overlapped by the next button in the row.
            int[] order = {0, 3, 1, 2};
            int last = -1;
            if (inputButtonHovered(layout.imageBtn.x(), layout.imageBtn.y(),
                    UiTokens.ACTION_BUTTON_SIZE, UiTokens.ACTION_BUTTON_SIZE, mouseX, mouseY)) {
                last = 0;
            } else if (inputButtonHovered(layout.phraseBtn.x(), layout.phraseBtn.y(),
                    UiTokens.ACTION_BUTTON_SIZE, UiTokens.ACTION_BUTTON_SIZE, mouseX, mouseY)) {
                last = 3;
            } else if (inputButtonHovered(layout.emojiBtn.x(), layout.emojiBtn.y(),
                    UiTokens.ACTION_BUTTON_SIZE, UiTokens.ACTION_BUTTON_SIZE, mouseX, mouseY)) {
                last = 1;
            } else if (inputButtonHovered(layout.sendBtn.x(), layout.sendBtn.y(),
                    UiTokens.BUTTON_W, UiTokens.BUTTON_H, mouseX, mouseY)) {
                last = 2;
            }
            if (last >= 0) {
                int at = 0;
                for (int i = 0; i < order.length; i++) {
                    if (order[i] == last) {
                        at = i;
                        break;
                    }
                }
                int tmp = order[at];
                for (int i = at; i < order.length - 1; i++) {
                    order[i] = order[i + 1];
                }
                order[order.length - 1] = tmp;
            }
            for (int id : order) {
                switch (id) {
                    case 0 -> drawIconButton(canvas, layout.imageBtn.x(), layout.imageBtn.y(), 0, mouseX, mouseY);
                    case 3 -> drawIconButton(canvas, layout.phraseBtn.x(), layout.phraseBtn.y(), 3, mouseX, mouseY);
                    case 1 -> drawIconButton(canvas, layout.emojiBtn.x(), layout.emojiBtn.y(), 1, mouseX, mouseY);
                    default -> drawSendButton(canvas, layout.sendBtn.x(), layout.sendBtn.y(), mouseX, mouseY);
                }
            }
        }

        // Input text: rendered by Skia at fixed density; the hidden EditBox is the
        // input backend (IME/keys) only. It wraps onto a second line (the bar has
        // already grown for it) and scrolls past INPUT_MAX_LINES. The text comes
        // from the page's own view: the field is live for one page at a time, so
        // the leaving page keeps its own draft while the arriving page's sits in
        // the shared EditBox.
        Font inputFont = FontManager.font(UiTokens.FONT_INPUT);
        float lineH = composer.lineHeight();
        String current = view.draft();
        float textX = bar.x() + UiTokens.INPUT_TEXT_X;
        float clipTop = layout.inputTextCenterY - lineH / 2.0F;
        float clipBottom = bar.bottom() - UiTokens.INPUT_ROW_PAD;
        canvas.save();
        SkiaDraw.clip(canvas, textX, clipTop, layout.inputTextMaxWidth(), Math.max(0.0F, clipBottom - clipTop), 0.0F);
        if (readOnly) {
            SkiaFontRenderer.drawText(canvas, inputFont, view.readOnlyHint(), textX,
                    SkiaFontRenderer.centerBaselineY(inputFont, layout.inputTextCenterY), textSecondary());
            canvas.restore();
        } else {
            List<String> lines = composer.wrappedInput(current, layout.inputTextMaxWidth());
            int total = lines.size();
            // Caret and selection belong to the page that owns the field; the
            // other side draws its text without them instead of borrowing the
            // live caret position.
            boolean ownComposer = view.ownsField();
            int caretRow = ownComposer && total > 0 ? ComposerInput.caretLine(lines, composer.caretIndex()) : 0;
            if (ownComposer) {
                composer.scrollToCaret(caretRow, total);
            }
            int shown = Math.min(UiTokens.INPUT_MAX_LINES, total);
            int from = total == 0 ? 0
                    : Math.min(ownComposer ? composer.scrollLine() : 0, Math.max(0, total - shown));
            // Placeholder stays visible while the field is focused: ChatScreen
            // focuses the chat field the moment the screen opens, so a hint gated
            // on "not focused" was literally never on screen. It doubles as the
            // upload progress readout, which is the only feedback a file drop can
            // give — GLFW reports the drop itself but has no drag-enter to react to.
            if (current.isEmpty()) {
                String hintText;
                if (quickPhrasePanel.isEditing()) {
                    hintText = tr("atomchat.input.phrase_hint");
                } else if (uploadPipeline.uploading()) {
                    hintText = tr("atomchat.input.uploading");
                } else if (view.partnerTyping()) {
                    // QQ-style: WATUT reports the partner composing a message
                    // (only the private page asks; silent no-op without WATUT).
                    hintText = tr("atomchat.private.typing");
                } else if (uploadPipeline.transientHint() != null
                        && System.currentTimeMillis() - uploadPipeline.transientHintSetAt() < 4000L) {
                    hintText = uploadPipeline.transientHint();
                } else {
                    hintText = tr("atomchat.input.placeholder");
                }
                String hint = Cicodes.truncateToWidth(inputFont, hintText, layout.inputTextMaxWidth());
                SkiaFontRenderer.drawText(canvas, inputFont, hint, textX,
                        SkiaFontRenderer.centerBaselineY(inputFont, layout.inputTextCenterY), textSecondary());
            } else {
                if (ownComposer) {
                    composerPointer.drawSelection(canvas, inputFont, lines, from, shown, textX, layout.inputTextCenterY, lineH);
                }
                for (int i = from; i < from + shown && i < total; i++) {
                    float cy = layout.inputTextCenterY + (i - from) * lineH;
                    SkiaFontRenderer.drawText(canvas, inputFont, lines.get(i), textX,
                            SkiaFontRenderer.centerBaselineY(inputFont, cy), textPrimary());
                }
            }
            if (ownComposer && composer.focused() && composer.fieldFocused()
                    && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                int caret = composer.caretIndex();
                float cursorY;
                String measure;
                if (total == 0) {
                    // Empty draft: caret sits at the start of the first visible line.
                    cursorY = layout.inputTextCenterY;
                    measure = "";
                } else {
                    int lineStart = 0;
                    for (int i = 0; i < caretRow; i++) {
                        lineStart += lines.get(i).length();
                    }
                    int col = Mth.clamp(caret - lineStart, 0, lines.get(caretRow).length());
                    cursorY = layout.inputTextCenterY + (caretRow - from) * lineH;
                    measure = lines.get(caretRow).substring(0, col);
                }
                float cursorX = textX + SkiaFontRenderer.getStringWidth(inputFont, measure) + 2.0F;
                float cursorH = SkiaFontRenderer.textHeight(inputFont);
                SkiaDraw.drawRoundedRect(canvas, cursorX, cursorY - cursorH / 2.0F, 2.0F, cursorH, 1.0F, textPrimary());
            }
            canvas.restore();
        }

        // Scrollbar (e33chat style): fades in near/hinting scroll, draggable, highlights.
        drawScrollbar(canvas, layout, toVirtualX(mouseX), toVirtualY(mouseY), view.scroll());
        drawJumpLatest(canvas, layout, toVirtualX(mouseX), toVirtualY(mouseY), view.scroll(), view.ownsField());
    }

    /**
     * Ring around the panel. Drawn last on both world-chat and root pages so
     * no component can sit on top of the clean edge. Painted with the user's
     * {@code panelOutlineColor} verbatim — a hand-picked ring is always
     * respected, even when its contrast against the panel is low.
     */
    private void drawBezel(Canvas canvas, UiLayout layout) {
        if (AtomChatConfig.get().halfScreenMode || !AtomChatConfig.get().panelOutline) {
            return;
        }
        UiLayout.Rect panel = layout.rect();
        float strokeWidth = UiTokens.panelInset();
        try (Paint border = new Paint().setMode(PaintMode.STROKE).setStrokeWidth(strokeWidth)
                .setColor(AtomChatConfig.get().panelOutlineColor)) {
            canvas.drawRRect(RRect.makeXYWH(panel.x() + strokeWidth / 2.0F, panel.y() + strokeWidth / 2.0F,
                    panel.w() - strokeWidth, panel.h() - strokeWidth, UiTokens.panelRadius()), border);
        }
    }

    private UiLayout rootLayout() {
        return ScreenMetrics.rootLayout(client.getWindow());
    }

    /** A pushed settings sub-page: no composer, no tab bar, tallest possible list. */
    private UiLayout detailLayout() {
        return ScreenMetrics.detailLayout(client.getWindow());
    }

    /** Layout and scroll controller of whatever list the top page is showing. */
    private UiLayout listLayout() {
        return ScreenMetrics.listLayout(client.getWindow(), topPage());
    }

    private ScrollController listScroll() {
        return scrollSync.listScroll();
    }

    private float measureRootContent(UiLayout root, AppPage page) {
        return switch (page) {
            case CHAT_LIST -> conversationListPage.measureContent(root);
            case PROFILE -> profilePage.measureContent(root);
            case SETTINGS -> settingsHomePage.measureContent(root);
            default -> 0.0F;
        };
    }

    /**
     * Body of a pushed settings sub-page: scroll state, then the card list, then
     * the scrollbar. Deliberately header-free — the shell header (with the back
     * affordance) is drawn fixed on top so it never slides with the list.
     */
    private void drawSettingsSection(Canvas canvas, int mouseX, int mouseY, SettingsSection section) {
        settingsDetailPage.drawSettingsSection(canvas, mouseX, mouseY, section);
    }

    /**
     * Feeds the signature editor the borrowed field's live text and focus
     * state, once per profile-page frame — the page draws the line but the
     * field (and the IME behind it) lives here.
     */
    private void syncProfileSignatureDraft() {
        profileWiring.syncProfileSignatureDraft();
    }

    /** Body of a pushed profile detail page (another player's profile). */
    private void drawProfileDetail(Canvas canvas, int mouseX, int mouseY) {
        profileWiring.drawProfileDetail(canvas, mouseX, mouseY);
    }

    /** Renders the full root screen (header + page body + bottom tab bar). */
    private void drawRootScreen(Canvas canvas, int mouseX, int mouseY, AppPage rootPage) {
        UiLayout root = rootLayout();
        float vmx = toVirtualX(mouseX);
        float vmy = toVirtualY(mouseY);
        bottomTabBar.update(frameDt, vmx, vmy, root.tabBar);
        director.stepRootNav(frameDt);
        if (!suppressHeader) {
            ShellHeader.render(canvas, root.header, headerChrome.shellTitleFor(rootPage), false, null, 0.0F,
                    textPrimary());
        }
        rootMouseX = vmx;
        rootMouseY = vmy;
        scrollSync.rootScroll().setContent(measureRootContent(root, rootPage), root.list.h());
        scrollSync.rootScroll().updateAnimation(System.currentTimeMillis());
        com.atom.chat.render.DotTexture.drawEdge(canvas, root.tabBar, UiTokens.shellChromeRadius(), true,
                AtomChatConfig.get().textSecondaryColor);
        drawRootPage(canvas, root, rootPage);
        drawScrollbar(canvas, root, vmx, vmy, scrollSync.rootScroll());
        drawBottomTabBar(canvas, root, rootPage);
        // Modal avatar cropper on top of everything inside the panel.
        imageCropper.render(canvas, root.rect(), vmx, vmy);
        colorPicker.render(canvas, root.rect(), vmx, vmy, accent());
    }

    private void drawRootPage(Canvas canvas, UiLayout layout, AppPage rootPage) {
        if (!director.rootTransitionActive()) {
            drawRootPageBody(canvas, layout, rootPage, 0.0F);
            return;
        }
        if (director.rootTabZoom()) {
            drawRootPageZoom(canvas, layout, rootPage);
            return;
        }
        // Full-width opaque push, same language as the emoji tab content
        // transition: the outgoing page leaves in the direction of travel while
        // the incoming page enters from that side. The progress is the same
        // exponential clock that positions the capsule (stepRootNav), so pill
        // and pages land together without the eased tween's notch.
        float progress = director.rootTabProgress();
        canvas.save();
        try {
            SkiaDraw.clip(canvas, layout.list.x(), layout.list.y(),
                    layout.list.w(), layout.list.h(), 0.0F);
            float travel = layout.list.w();
            float sign = director.rootTabTo() > director.rootTabFrom() ? 1.0F : -1.0F;
            drawRootPageBody(canvas, layout, director.rootPageForIndex(director.rootTabFrom()),
                    -sign * travel * progress);
            drawRootPageBody(canvas, layout, rootPage,
                    sign * travel * (1.0F - progress));
        } finally {
            canvas.restore();
        }
    }

    /**
     * Melodify serial zoom between root tabs, staged inside the list area:
     * the leaving tab shrinks out (scale 1.0 -> 0.9 while fading), then the
     * arriving tab settles (1.1 -> 1.0 while fading in) — one page composited
     * at a time, never two half-transparent pages stacked. The clip is square
     * on purpose: the list sits inside the panel's rounded corners, so the
     * hard edges never show; a rounded clip here would bite into the first
     * and last rows.
     */
    private void drawRootPageZoom(Canvas canvas, UiLayout layout, AppPage rootPage) {
        UiLayout.Rect list = layout.list;
        boolean exiting = director.rootZoomExiting();
        float alpha = exiting ? director.rootZoomExitAlpha() : director.rootZoomEnterAlpha();
        float scale = exiting ? 0.9F + alpha * 0.1F : 1.1F - alpha * 0.1F;
        AppPage page = exiting ? director.rootPageForIndex(director.rootTabFrom()) : rootPage;
        if (alpha <= 0.001F || PageTransitionDirector.navLayerSkippable(alpha, scale)) {
            return;
        }
        canvas.save();
        try (Paint layer = new Paint().setAlphaf(alpha)) {
            canvas.saveLayer(Rect.makeXYWH(list.x(), list.y(), list.w(), list.h()), layer);
            SkiaDraw.clip(canvas, list.x(), list.y(), list.w(), list.h(), 0.0F);
            PageTransitionDirector.applyNavScale(canvas, list, scale);
            drawRootPageBody(canvas, layout, page, 0.0F);
            // save() and saveLayer() pushed two entries; both must be popped.
            // This restore pairs with the layer, the finally's with the plain
            // save — one restore short leaked the outer save every frame (the
            // 0.2.4 banner leak's shape, capped only by the frame-end guard).
            canvas.restore();
        } finally {
            canvas.restore();
        }
    }

    /** Renders any root page body at an optional horizontal offset inside the root layout. */
    private void drawRootPageBody(Canvas canvas, UiLayout layout, AppPage page, float dx) {
        canvas.save();
        canvas.translate(dx, 0.0F);
        try {
            switch (page) {
                case CHAT_LIST -> conversationListPage.render(canvas, layout, rootMouseX, rootMouseY, scrollSync.rootScroll().getScrollY());
                case PROFILE -> {
                    syncProfileSignatureDraft();
                    profilePage.render(canvas, layout, rootMouseX, rootMouseY, scrollSync.rootScroll().getScrollY());
                }
                case SETTINGS -> settingsHomePage.render(canvas, layout, rootMouseX, rootMouseY,
                        scrollSync.rootScroll().getScrollY());
                case WORLD_CHAT, PRIVATE_CHAT ->
                        throw new IllegalStateException("Root page body cannot render chat/detail pages");
            }
        } finally {
            canvas.restore();
        }
    }

            private void drawBottomTabBar(Canvas canvas, UiLayout layout, AppPage rootPage) {
        int selectedIndex = director.rootIndex(rootPage);
        bottomTabBar.render(canvas, layout.tabBar, selectedIndex, textPrimary(), accent());
    }

    /** Routes a root-page click on one of the three bottom tab cells. */
    private boolean handleBottomTabClick(float vmx, float vmy) {
        int index = BottomTabBar.hitTest(vmx, vmy, rootLayout().tabBar);
        if (index < 0) {
            return false;
        }
        bottomTabBar.setPressedTab(index);
        AppPage root = switch (index) {
            case 0 -> AppPage.CHAT_LIST;
            case 1 -> AppPage.PROFILE;
            case 2 -> AppPage.SETTINGS;
            default -> throw new IllegalStateException("Unexpected bottom tab index " + index);
        };
        boolean changed = topPage() != root;
        switchRoot(root);
        if (changed) com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
        return true;
    }

    private static float s(float v) {
        return UiTokens.s(v);
    }

    /** Minecraft language lookup for all AtomChat UI copy. */
    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /**
     * Composer button drawing (D9). The geometry comes from the layout and the
     * pointer is converted here — both belong to the screen — while the
     * primitives, their press/hover state and the icon set live in
     * {@link DrawPrimitives}.
     */
    private void drawIconButton(Canvas canvas, float bx, float by, int id, int mouseX, int mouseY) {
        primitives.drawIconButton(canvas, bx, by, id, toVirtualX(mouseX), toVirtualY(mouseY));
    }

    /**
     * True when the pointer is inside a composer button rect (virtual coords).
     * The box is passed in because the row carries two shapes: the square
     * composer keys and the wider Send capsule.
     */
    private boolean inputButtonHovered(float bx, float by, float w, float h,
                                       int mouseX, int mouseY) {
        return primitives.inputButtonHovered(bx, by, w, h, toVirtualX(mouseX), toVirtualY(mouseY));
    }

    private void drawSendButton(Canvas canvas, float bx, float by, int mouseX, int mouseY) {
        primitives.drawSendButton(canvas, bx, by, toVirtualX(mouseX), toVirtualY(mouseY));
    }

    /** Draws an icon centered on an arbitrary point; used by toolbar and menus. */
    private void drawIconCentered(Canvas canvas, io.github.humbleui.skija.Path icon, float cx, float cy,
                                  float size, int color) {
        primitives.drawIconCentered(canvas, icon, cx, cy, size, color);
    }

    /**
     * Draws the hidden EditBox's selection as Skia highlight blocks over the
     * wrapped input lines. AtomChat renders its own text, so the vanilla field's
     * selection highlight would otherwise be invisible.
     */
    /**
     * Rounded scrollbar: fades in only while the pointer is near the track or
     * while dragged, and fades straight back out otherwise.
     * Thumb maps scrollY to the track; drag maps 1:1 with clamping.
     */
    private void drawScrollbar(Canvas canvas, UiLayout layout, float vmx, float vmy,
                               ScrollController controller) {
        scrollSync.drawScrollbar(canvas, layout, vmx, vmy, controller);
    }

    private boolean overScrollbarTrack(UiLayout layout, float vmx, float vmy,
                                       ScrollController controller) {
        return scrollSync.overScrollbarTrack(layout, vmx, vmy, controller);
    }


    /**
     * The floating jump-to-latest pill.
     *
     * @param scroll  the drawn page's own scroll controller — the pill belongs to
     *                its page and must never read another page's scroll while a
     *                transition composites both
     * @param advance true for the page that owns the composer field: the fade,
     *                hover and spring values are shared screen state, so they are
     *                stepped once per frame and the second page of a transition
     *                only re-draws them
     */
    private void drawJumpLatest(Canvas canvas, UiLayout layout, float vmx, float vmy,
                                ScrollController scroll, boolean advance) {
        scrollSync.drawJumpLatest(canvas, layout, vmx, vmy, scroll, advance);
    }

    /**
     * Straight sRGB blend of two opaque ARGB colours, {@code t} in [0,1] — the
     * hover wash's own interpolation, so a fade can be drawn without stacking a
     * translucent overlay on a fixed grey.
     */
    private static int lerpArgb(int from, int to, float t) {
        return DrawPrimitives.lerpArgb(from, to, t);
    }

    private boolean overJumpLatest(UiLayout layout, float vmx, float vmy) {
        return scrollSync.overJumpLatest(layout, vmx, vmy);
    }

    /**
     * Opens the FlatLaf image picker (same one as the image button) and copies
     * the chosen file into the emote store. The picker blocks its own worker
     * thread; the store mutation hops back to the render thread.
     */
    private void pickEmoteFile() {
        KeyMapping.releaseAll();
        if (this.client.mouseHandler != null) {
            ((MouseHandlerAccessor) this.client.mouseHandler).atomchat$setActiveButton(0);
        }
        emojiWiring.pickEmoteFile();
    }

    /**
     * Puts a message's text on the clipboard. The failure contract — false
     * instead of a throw, so a refused clipboard cannot leave the context menu
     * stuck open — lives in {@link NativeDialogBridge} (D14); this shell keeps
     * the entry point the menu handlers and the colour picker call.
     */
    private boolean copyToClipboard(String text) {
        return dialogBridge.copyToClipboard(text);
    }

    /** Raises a transient action acknowledgement in the panel's floating layer. */
    private void feedback(String key, boolean success) {
        notificationJump.feedback(key, success);
    }

    /** Raises an acknowledgement whose text carries an argument (a player name). */
    private void feedback(String key, String label, boolean success) {
        notificationJump.feedback(key, label, success);
    }

    /** The test-poke command's door: {@code /atomchat test poke} lands here. */
    public void onTestPoked(UUID target, String targetName) {
        avatarWiring.poke().tested(target, targetName);
    }

    /** A player's display name from the tab list, falling back to the raw uuid. */
    private String displayNameFor(UUID uuid) {
        if (client.getConnection() != null) {
            net.minecraft.client.multiplayer.PlayerInfo entry = client.getConnection().getPlayerInfo(uuid);
            if (entry != null) {
                return entry.getProfile().getName();
            }
        }
        return uuid.toString();
    }

    /**
     * True while the open/close progress is still in flight. The panel is
     * drawn through the open transform (zoom/slide) but hit tests run on the
     * settled virtual layout, so a click mid-animation would land somewhere
     * other than where it looks — swallowed whole until the panel settles,
     * same rule as the page nav's hidden-side skip. The gate owns that rule;
     * this answers it.
     */

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Boolean direct = gate.scroll(mouseX, mouseY, horizontalAmount, verticalAmount);
        if (direct != null) {
            return direct;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Completes the input trio next to keyPressed:/charTyped:. The whole UI is
        // Skia-drawn, so without this line a "the button does nothing" report has
        // no way to tell "the click never arrived" from "it arrived somewhere
        // else" — and the virtual pair is what decides which element was under
        // the pointer. Debug-gated: one line per click is fine to opt into.
        if (AtomChatConfig.get().debug) {
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("ui", "click: gui=({}, {}) virtual=({}, {}) button={}"),
                    (int) mouseX, (int) mouseY,
                    (int) toVirtualX(mouseX), (int) toVirtualY(mouseY), button);
        }
        com.atom.chat.audio.FeedbackSoundController.beginAction();
        try {
            Boolean direct = gate.click(mouseX, mouseY, button);
            if (direct != null) return direct;
            return super.mouseClicked(mouseX, mouseY, button);
        } finally {
            com.atom.chat.audio.FeedbackSoundController.endAction();
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        Boolean direct = gate.drag(mouseX, mouseY, button, dragX, dragY);
        if (direct != null) {
            return direct;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Boolean direct = gate.release(mouseX, mouseY, button);
        if (direct != null) {
            return direct;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Boolean direct = gate.key(keyCode, scanCode, modifiers);
        if (direct != null) {
            return direct;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        Boolean direct = gate.charTyped(chr, modifiers);
        if (direct != null) {
            return direct;
        }
        return super.charTyped(chr, modifiers);
    }


    private void pickAndUploadImage() {
        // The native AWT dialog grabs OS input; release MC's held keys/button so
        // the UI does not think the image button is still pressed when it returns.
        KeyMapping.releaseAll();
        if (this.client.mouseHandler != null) {
            ((MouseHandlerAccessor) this.client.mouseHandler).atomchat$setActiveButton(0);
        }
        Thread worker = new Thread(() -> {
            Path file = FilePicker.pickImage(this::suppressAutoIconify, this::restoreAutoIconify);
            // The dialog owned OS focus while it was open; hand it back to the
            // game or the first click after picking lands on nothing.
            refocusWindow();
            if (file == null) {
                return;
            }
            uploadAndAppend(file);
        }, "AtomChat-ImagePicker");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Saves another player's CICode image to a local file. Url extraction, the
     * dialog, the download and both failure surfaces live in
     * {@link ImageUploadPipeline} (D12); this shell keeps the entry point the
     * context menu calls.
     */
    private void saveImage(ChatMessage message) {
        uploadPipeline.saveImage(message);
    }

    /**
     * Uploads an image and drops its CICode into the draft. Shared by the file
     * picker and by Ctrl+V pastes; the chain itself (server media → uguu
     * fallback, CICode assembly, the hop back to the render thread) lives in
     * {@link ImageUploadPipeline} (D12).
     */
    private void uploadAndAppend(Path file) {
        uploadPipeline.uploadAndAppend(file);
    }

    /**
     * Ctrl+V with a picture on the clipboard. The payload is read off the render
     * thread — a screenshot is several megabytes and converting it is not free —
     * then uploaded and appended. The worker, the read and the D12 hand-off live
     * in {@link DragDropPasteUpload} (D13); this shell keeps the entry point the
     * Ctrl+V interception calls.
     */
    private void pasteFromClipboard(DataFlavor flavor) {
        dragDropPaste.pasteFromClipboard(flavor);
    }

    /**
     * GLFW iconifies a fullscreen window as soon as it loses focus
     * (GLFW_AUTO_ICONIFY defaults to true and Minecraft leaves it there), so a
     * picker that takes focus would minimise the game every time. Suspension
     * lasts exactly as long as the picker is open. The pairing (and the hop onto
     * the render thread) lives in {@link NativeDialogBridge} (D14); this shell
     * keeps the two halves the picker workers pass as method references.
     */
    private void suppressAutoIconify() {
        dialogBridge.suppressAutoIconify();
    }

    private void restoreAutoIconify() {
        dialogBridge.restoreAutoIconify();
    }

    /**
     * Returns OS focus to the game window after the native picker closes, and
     * clears any key/button state the dialog left behind. The clear-then-focus
     * order and the hop onto the render thread live in
     * {@link NativeDialogBridge} (D14); this shell keeps the entry point the
     * picker workers call.
     */
    private void refocusWindow() {
        dialogBridge.refocusWindow();
    }

    /** Sends whatever the composer holds; the pipeline owns the whole order. */
    private void sendMessage(String text) {
        sendPipeline.sendMessage(text);
    }

    /** The local player's name, or the "(me)" stand-in outside a world. */
    private String ownName() {
        return sendPipeline.ownName();
    }
        /**
     * Display name for a message row. Own messages use the local player; other
     * messages prefer the structured sender name from the capture pipeline and
     * only fall back to the old hard-coded label when identity is unavailable.
     */
    private String messageSenderName(ChatMessage msg) {
        return sendPipeline.messageSenderName(msg);
    }
    /** Head-truncates with an ellipsis — the reply bar formats its quote with it. */
    private static String abbreviate(String text, int maxChars) {
        return SendPipeline.abbreviate(text, maxChars);
    }
    private int accent() {
        return primitives.accent();
    }

    private int textPrimary() {
        return primitives.textPrimary();
    }

    private int textSecondary() {
        return primitives.textSecondary();
    }

    private float panelWidth() {
        return ScreenMetrics.panelWidth(client.getWindow());
    }

    private float panelHeight() {
        return ScreenMetrics.panelHeight(client.getWindow());
    }

    /** Content zoom (D10): see {@link ScreenMetrics#contentScale()} for the clamp and why it cancels on the frame. */
    private float contentScale() {
        return ScreenMetrics.contentScale();
    }

    /** Design density (D10): the 1080p-anchored funnel every conversion goes through. */
    private float uiDensity() {
        return ScreenMetrics.uiDensity(client.getWindow());
    }

    private float vw() {
        return ScreenMetrics.vw(client.getWindow());
    }

    private float vh() {
        return ScreenMetrics.vh(client.getWindow());
    }

    /** Panel anchor (D10): contentScale-invariant, see {@link ScreenMetrics#panelX()}. */
    private float panelX() {
        return ScreenMetrics.panelX();
    }

    private float panelY() {
        return ScreenMetrics.panelY(client.getWindow());
    }

    // The one progress->shape mapping of the open/close animation, and the
    // capture rect that mirrors it, live in PanelOpenMotion (D25): one
    // derivation for both sides, so the blurred region cannot drift off the
    // panel mid-flight.

    /**
     * The one and only way to build the layout. Rendering and every hit test go
     * through it so the input bar's animated height can never desync a click
     * from what was drawn. The frame itself comes from {@link ScreenMetrics};
     * the two facts it adds here — the animated bar height (D21) and whether a
     * reply bar is reserved (D1) — stay in this shell, so the assembly is
     * unchanged.
     */
    private UiLayout layout() {
        float replyH = replyBarVisible() ? s(34) : 0.0F;
        return UiLayout.of(panelX(), panelY(), panelWidth(), panelHeight(), composer.extraH(), replyH, AtomChatConfig.get().halfScreenMode);
    }

    // ---------------------------------------------------------------- input box

    /**
     * Wraps the draft text, then eases the bar's extra height toward what that
     * wrap needs (0 for one line, one line height for two — never more).
     *
     * <p>Must run before the message list is measured: the bar is bottom-anchored,
     * so whatever it gains the list gives up. When the list shrinks under a view
     * that was pinned to the bottom, re-stick it, otherwise the newest message
     * would slide out of sight.</p>
     *
     * @return a layout rebuilt with the updated height.
     */
    /** Extra input-bar height a page's own draft needs (read-only pages need none). */
    private float toVirtualX(double guiX) {
        return ScreenMetrics.toVirtualX(client.getWindow(), guiX);
    }

    private float toVirtualY(double guiY) {
        return ScreenMetrics.toVirtualY(client.getWindow(), guiY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }


    // ------------------------------------------------------------------ input handlers
    //
    // Ordered by the registration sequence in `gate`. Each handler owns
    // one interaction domain and answers events with consume/pass; nothing may
    // ever be inserted "into the middle of a chain" again.

    /** Consumes input while the close animation runs; clears message selection on any non-closing click. */
    private final class ClosingStateInput implements InputHandler {
        @Override
        public boolean onClick(double mouseX, double mouseY, int button) {
            if (closing) {
                return true;
            }
            // Any non-modal click clears message text selection. This used to
            // run after the modal checks; a modal can never be open together
            // with a message selection, so checking it first is equivalent.
            if (messageListView.hasSelection() || messageListView.isSelecting()) {
                messageListView.clearSelection();
            }
            return false;
        }

        @Override
        public boolean onKey(int keyCode, int scanCode, int modifiers) {
            return closing;
        }

        @Override
        public boolean onChar(char chr, int modifiers) {
            return closing;
        }
    }

    /**
     * The picture preview, the avatar cropper and the colour picker are modals:
     * while active, each owns every event.
     */
    private final class ModalInput implements InputHandler {
        @Override
        public boolean onClick(double mouseX, double mouseY, int button) {
            // The picture preview: a click on the picture keeps it open, a click
            // on the dim backdrop or the close key dismisses it.
            if (imagePreview.isActive()) {
                if (button == 0) imagePreview.onClick(toVirtualX(mouseX), toVirtualY(mouseY), layout().rect());
                if (!imagePreview.isActive()) com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                return true;
            }
            // The avatar cropper is modal: while it is open it owns every click.
            if (imageCropper.isActive()) {
                imageCropper.onClick(toVirtualX(mouseX), toVirtualY(mouseY), layout().rect());
                if (!imageCropper.isActive()) com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                return true;
            }
            // So is the colour picker.
            if (colorPicker.isActive()) {
                colorPicker.onClick(toVirtualX(mouseX), toVirtualY(mouseY), layout().rect());
                if (!colorPicker.isActive()) com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                return true;
            }
            return false;
        }

        @Override
        public boolean onDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
            // The preview owns the gesture; the list underneath stays still.
            if (imagePreview.isActive()) {
                if (button == 0) imagePreview.onDrag(toVirtualX(mouseX), toVirtualY(mouseY), layout().rect());
                return true;
            }
            // Pan the avatar-crop image while the modal cropper is open.
            if (imageCropper.isActive()) {
                imageCropper.onDrag(toVirtualX(mouseX), toVirtualY(mouseY), layout().rect());
                return true;
            }
            if (colorPicker.isActive()) {
                colorPicker.onDrag(toVirtualX(mouseX), toVirtualY(mouseY), layout().rect());
                return true;
            }
            return false;
        }

        @Override
        public boolean onRelease(double mouseX, double mouseY, int button) {
            if (imagePreview.isActive()) {
                if (button == 0) imagePreview.endDrag();
                return true;
            }
            if (imageCropper.isActive()) {
                imageCropper.endDrag();
                return true;
            }
            if (colorPicker.isActive()) {
                colorPicker.endDrag();
                return true;
            }
            return false;
        }

        @Override
        public boolean onScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            // Wheel zoom belongs exclusively to the preview while it is open.
            if (imagePreview.isActive()) {
                imagePreview.onScroll(toVirtualX(mouseX), toVirtualY(mouseY), layout().rect(), verticalAmount);
                return true;
            }
            // Wheel zooms the crop image around the circle centre while active.
            if (imageCropper.isActive()) {
                imageCropper.onScroll(layout().rect(), verticalAmount);
                return true;
            }
            if (colorPicker.isActive()) {
                colorPicker.onScroll(layout().rect(), verticalAmount);
                return true;
            }
            return false;
        }

        @Override
        public boolean onKey(int keyCode, int scanCode, int modifiers) {
            // The preview is modal: Esc dismisses it, every other key is swallowed.
            if (imagePreview.isActive()) {
                if (keyCode == 256) {
                    imagePreview.close();
                }
                return true;
            }
            // The cropper is modal: Esc cancels it, every other key is swallowed.
            if (imageCropper.isActive()) {
                if (keyCode == 256) {
                    imageCropper.cancel();
                }
                return true;
            }
            if (colorPicker.isActive()) {
                if (colorPicker.isInputFocused()) {
                    // Focused hex input: Esc leaves the input (auto-applying a
                    // legal buffer), Backspace deletes, Ctrl+C/V copy and paste
                    // the hex; every other key stays swallowed by the modal.
                    if (keyCode == 256) {
                        colorPicker.blurInput();
                    } else if (keyCode == 259) {
                        colorPicker.onBackspace();
                    } else if (keyCode == 67 && (modifiers & 2) != 0) {
                        feedback("atomchat.feedback.copied", copyToClipboard(colorPicker.copyHex()));
                    } else if (keyCode == GLFW_KEY_V && (modifiers & 2) != 0) {
                        colorPicker.pasteHex(AtomChatScreen.this.client.keyboardHandler.getClipboard());
                    }
                } else if (keyCode == 256) {
                    colorPicker.cancel();
                }
                return true;
            }
            // The profile signature editor borrows the composer field exactly
            // like the quick-phrase editor: Esc cancels, Enter commits,
            // everything else falls through to the normal chain (typing, IME
            // and backspace keep working on the borrowed field).
            if (profilePage.isSignatureEditing()) {
                if (keyCode == 256) {
                    profilePage.endSignatureEdit(false);
                } else if (keyCode == 257 || keyCode == 335) {
                    profilePage.endSignatureEdit(true);
                } else {
                    return false;
                }
                return true;
            }
            // The quick-phrase panel borrows the composer field for text input,
            // so its key handling is narrow: while editing, Esc cancels and
            // Enter commits; everything else falls through to the normal
            // composer chain (typing, IME, backspace and history keep working).
            // Open but not editing, only Esc (close) is intercepted.
            if (quickPhrasePanel.isOpen()) {
                if (quickPhrasePanel.isEditing()) {
                    if (keyCode == 256) {
                        endPhraseEdit(false);
                    } else if (keyCode == 257 || keyCode == 335) {
                        endPhraseEdit(true);
                    } else {
                        return false;
                    }
                    return true;
                }
                if (keyCode == 256) {
                    closePhrasePanel();
                }
                return keyCode == 256;
            }
            return false;
        }

        @Override
        public boolean onChar(char chr, int modifiers) {
            // Nothing typed reaches the composer while the preview is up.
            if (imagePreview.isActive()) {
                return true;
            }
            // The colour picker's hex input swallows typed characters while active.
            if (colorPicker.isActive()) {
                colorPicker.onChar(chr);
                return true;
            }
            if (imageCropper.isActive()) {
                return true;
            }
            return false;
        }
    }

    /**
     * Esc closes the whole AtomChat screen with its animated close — from any
     * page, including pushed sub-pages. Closing everything at once is the
     * intended design (confirmed by the author 2026-09-06); do not "fix" it
     * into a per-page pop.
     */
    private final class ScreenEscInput implements InputHandler {
        @Override
        public boolean onKey(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 256) { // Esc: animated close
                dismissSuggestor();
                requestClose();
                return true;
            }
            return false;
        }
    }

    /** Root pages (conversation list / profile / settings): no composer, own scroll and rows. */
    private final class RootPageInput implements InputHandler {
        @Override
        public boolean onClick(double mouseX, double mouseY, int button) {
            // Root pages have no world-chat composer/message interactions. Route
            // them before the hidden chat field/suggestion layer can see the click,
            // and consume root clicks so ChatScreen's composer never gets focus.
            if (isWorldChatPage()) {
                return false;
            }
            float mx = toVirtualX(mouseX);
            float my = toVirtualY(mouseY);
            UiLayout pageLayout = listLayout();
            ScrollController pageScroll = listScroll();
            // Root-page player-card context menu gets priority over row clicks.
            // The box (row count included) comes from the menu itself — the same
            // source its drawing measures with: two rows here once left
            // "取消屏蔽" permanently unclickable.
            if (contextMenu.hasPlayerCardMenu()) {
                int row = contextMenu.hitPlayerCardRow(mx, my);
                if (row >= 0 && button == 0) {
                    contextActions.performPlayerCardAction(row);
                com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                    contextMenu.close();
                    return true;
                }
                contextMenu.close();
            }
            if (button == 0 && overScrollbarTrack(pageLayout, mx, my, pageScroll)) {
                pageScroll.beginDrag(my);
                return true;
            }
            // A pushed settings sub-page: back arrow, then switch rows.
            if (topPage() == AppPage.SETTINGS_SECTION) {
                if (button == 0 && headerChrome.isBackButtonHit(mx, my)) {
                    popPage();
                    return true;
                }
                if (button == 0) {
                    int chip = settingsSectionPage.chipAt(topNav().section(), mx, my, pageLayout);
                    if (chip >= 0) {
                        // Same press-bounce arming the bottom tab bar uses.
                        settingsSectionPage.setPressedChip(chip);
                        settingsSectionPage.selectChip(topNav().section(), chip);
                com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                        return true;
                    }
                }
                if (button == 0 && settingsSectionPage.themesPress(mx, my, pageLayout,
                        topNav().section(), pageScroll.getScrollY())) {
                    // The theme strip owns clicks on its row: a settled press
                    // selects a card, a moving one scrolls it.
                    return true;
                }
                if (button == 0) {
                    SettingsSection section = topNav().section();
                    // A slider press starts a drag or nudges by one step; it
                    // must never fall through to the row's other actions.
                    SettingsSectionPage.SliderHit slider = settingsSectionPage.sliderHit(
                            mx, my, pageLayout, section, pageScroll.getScrollY());
                    if (slider != null) {
                        // Any click that is not the armed button disarms it.
                        settingsSectionPage.disarmAction();
                        // The retention-days row is an inline numeric input, not a
                        // linear 0..365 slider that cannot land on exact values.
                        if ("history_retention".equals(slider.row().slider().id())) {
                            settingsSectionPage.beginNumberEdit(slider.row().slider());
                            return true;
                        }
                        float normalized = slider.row().slider()
                                .normalize(slider.row().slider().value());
                        if (slider.onKnob(mx, my, normalized)) {
                            settingsSectionPage.beginSliderDrag(slider.index(), slider.row().slider(),
                                    slider.rowRect(), mx);
                        } else {
                            settingsSectionPage.nudgeSlider(slider,
                                    mx < slider.track().x() + slider.track().w() / 2.0F ? -1 : 1);
                        }
                        return true;
                    }
                    SettingsSectionPage.ColorHit colorHit = settingsSectionPage.colorHit(
                            mx, my, pageLayout, section, pageScroll.getScrollY());
                    if (colorHit != null) {
                        settingsSectionPage.disarmAction();
                        // Arm the swatch bounce; the press visual holds until release.
                        settingsSectionPage.setPressedSwatch(
                                colorHit.plus() ? -1 : colorHit.rowIndex(),
                                colorHit.plus() ? -1 : colorHit.swatchIndex());
                        if (colorHit.plus()) {
                            colorPicker.open(colorHit.color());
                            com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                        } else {
                            settingsSectionPage.applyColor(colorHit);
                com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                        }
                        return true;
                    }
                    SettingsSectionPage.RowHit hit = settingsSectionPage.hit(mx, my, pageLayout,
                            section, pageScroll.getScrollY());
                    if (hit != null) {
                        // Arm the row press bounce wherever the press landed;
                        // hit-tests stay unscaled.
                        settingsSectionPage.setPressedRow(hit.index());
                        if (hit.onAction(mx, my)) {
                            if (settingsSectionPage.perform(hit)) {
                                com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                            }
                            return true;
                        }
                    }
                    settingsSectionPage.disarmAction();
                }
                return true;
            }
            // A pushed profile detail: back arrow, then copyable rows/tiles.
            if (topPage() == AppPage.PROFILE_DETAIL) {
                if (button == 0 && headerChrome.isBackButtonHit(mx, my)) {
                    popPage();
                    return true;
                }
                if (button == 0) {
                    profilePage.onClick(mx, my, detailLayout(), scrollSync.detailScroll().getScrollY());
                } else if (button == 1) {
                    // Right-click on the banner card clears the custom banner.
                    profilePage.onRightClick(mx, my, detailLayout(), scrollSync.detailScroll().getScrollY());
                }
                return true;
            }
            if (button == 0 && handleBottomTabClick(mx, my)) {
                return true;
            }
            if (topPage() == AppPage.SETTINGS && button == 0) {
                SettingsSection section = settingsHomePage.hit(mx, my, pageLayout, pageScroll.getScrollY());
                if (section != null) {
                    // Tiles open their section directly; the section page
                    // groups its rows into in-page chips instead.
                    openSettingsSection(section);
                    return true;
                }
            }
            if (topPage() == AppPage.PROFILE && button == 0) {
                profilePage.onClick(mx, my, pageLayout, pageScroll.getScrollY());
            }
            if (topPage() == AppPage.PROFILE && button == 1) {
                // Right-click on the banner card clears the custom banner.
                profilePage.onRightClick(mx, my, pageLayout, pageScroll.getScrollY());
            }
            if (topPage() == AppPage.CHAT_LIST) {
                ConversationListPage.RowHit hit = conversationListPage.hit(mx, my, pageLayout, pageScroll.getScrollY());
                if (hit != null) {
                    // Arm the pressed dip on the row that was actually hit; the
                    // page times the pulse itself because opening the chat ends
                    // the interaction before any release could arrive.
                    conversationListPage.pulseRow(hit.index());
                    if (hit.row().kind() == ConversationListPage.RowKind.DIVIDER) {
                        if (button == 0) {
                            conversationListPage.togglePrivateGroup();
                            com.atom.chat.audio.FeedbackSoundController.play(
                                    com.atom.chat.audio.FeedbackCue.BUTTON);
                        }
                        return true;
                    }
                    if (hit.row().kind() == ConversationListPage.RowKind.PUBLIC && button == 0) {
                        openWorldChat();
                        return true;
                    }
                    if (hit.row().kind() == ConversationListPage.RowKind.PLAYER) {
                        if (button == 0) {
                            openPrivateChat(hit.row().player());
                            return true;
                        }
                        if (button == 1) {
                            contextMenu.openPlayerCard(hit.row().player(), mx, my);
                    com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                            return true;
                        }
                    }
                }
            }
            return true;
        }

        @Override
        public boolean onDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (isWorldChatPage()) {
                return false;
            }
            // Root pages have no world-chat selection state, but they do share the
            // scrollbar drag model; do not forward drags to the hidden composer.
            // An active slider drag owns the pointer until release; the value
            // follows the pointer's X even outside the row.
            if (settingsSectionPage.isDraggingThemeStrip()) {
                settingsSectionPage.dragThemeStrip(toVirtualX(mouseX), toVirtualY(mouseY),
                        settingsSectionPage.themesRowRect(listLayout(), topNav().section(),
                                listScroll().getScrollY()));
                if (!settingsSectionPage.isDraggingThemeStrip()) {
                    // The strip released a vertical flick: hand the gesture to
                    // the page list, starting from the current pointer.
                    listScroll().beginDrag(toVirtualY(mouseY));
                }
                return true;
            }
            if (settingsSectionPage.isDraggingSlider()) {
                UiLayout layout = listLayout();
                settingsSectionPage.dragSlider(layout, topNav().section(),
                        listScroll().getScrollY(), toVirtualX(mouseX));
                return true;
            }
            if (button == 0 && listScroll().isDragging()) {
                listScroll().dragTo(toVirtualY(mouseY), listLayout().list.h());
                return true;
            }
            return false;
        }

        @Override
        public boolean onRelease(double mouseX, double mouseY, int button) {
            if (isWorldChatPage()) {
                return false;
            }
            settingsSectionPage.endThemeStrip();
            settingsSectionPage.endSliderDrag();
            settingsSectionPage.setPressedRow(-1);
            settingsSectionPage.setPressedChip(-1);
            settingsSectionPage.setPressedSwatch(-1, -1);
            bottomTabBar.setPressedTab(-1);
            if (button == 0 && listScroll().isDragging()) {
                listScroll().endDrag();
                return true;
            }
            return false;
        }

        @Override
        public boolean onScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (isWorldChatPage()) {
                return false;
            }
            float mx = toVirtualX(mouseX);
            float my = toVirtualY(mouseY);
            // Dragging a slider must not scroll the list underneath it.
            if (settingsSectionPage.isDraggingSlider()) {
                return true;
            }
            // The theme strip consumes the wheel while the pointer is over
            // it: it scrolls horizontally, not the list underneath.
            if (topPage() == AppPage.SETTINGS_SECTION
                    && settingsSectionPage.themesUnderPointer(mx, my, listLayout(),
                            topNav().section(), listScroll().getScrollY())) {
                settingsSectionPage.wheelThemeStrip((float) verticalAmount,
                        settingsSectionPage.themesRowRect(listLayout(), topNav().section(),
                                listScroll().getScrollY()));
                return true;
            }
            UiLayout.Rect pageList = listLayout().list;
            if (pageList.contains(mx, my)) {
                listScroll().wheel((float) verticalAmount);
                return listScroll().getMaxScroll() > 0.0F;
            }
            return false;
        }

        @Override
        public boolean onKey(int keyCode, int scanCode, int modifiers) {
            if (isWorldChatPage()) {
                return false;
            }
            // The profile signature editor borrows the composer field: while it
            // is active, key events fall through to the focused field (Esc and
            // Enter are handled earlier by ModalInput, so they never get here).
            if (profilePage.isSignatureEditing()) {
                return false;
            }
            // Root pages have no world-chat composer keyboard handling yet. The
            // vanilla chat key still opens the World Chat page from any root page:
            // replace with CHAT_LIST then push WORLD_CHAT so back returns to the
            // conversation list. All other keys are consumed so they never reach the
            // hidden ChatScreen chat field/history. (Esc is handled earlier by
            // ScreenEscInput: it closes the whole screen from any page by design.)
            if (isVanillaChatKey(keyCode, scanCode)) {
                switchRoot(AppPage.CHAT_LIST);
                pushPage(AppPage.WORLD_CHAT);
            }
            return true;
        }

        @Override
        public boolean onChar(char chr, int modifiers) {
            // The signature editor borrows the composer field: typed characters
            // must reach it. Only possible on a root page, where the default
            // behaviour below is to swallow them.
            if (profilePage.isSignatureEditing()) {
                return false;
            }
            // Root pages do not own the composer, so typing must not reach the
            // hidden ChatScreen chat field.
            return !isWorldChatPage();
        }
    }

    /** World chat and private chats: composer, emoji panel, message list, context menus. */
    private final class WorldChatInput implements InputHandler {
        @Override
        public boolean onClick(double mouseX, double mouseY, int button) {
            if (!isWorldChatPage()) {
                return false;
            }
            float mx = toVirtualX(mouseX);
            float my = toVirtualY(mouseY);
            float panelX = panelX();
            float panelY = panelY();
            UiLayout layout = layout();

            // Vanilla suggestion layer gets first pick on clicks too (prevents click-through).
            if (suggestorWiring.mouseClicked((int) mouseX, (int) mouseY, button)) {
                return true;
            }

            // Back to the conversation list before any composer/emoji/message hit.
            // resetTransientWorldUi() runs inside popPage() after any slide-out
            // animation so the world page still looks intact while it leaves.
            if (button == 0 && headerChrome.isBackButtonHit(mx, my)) {
                ShellHeader.armBackPress();
                popPage();
                return true;
            }

            // Feed filter cycle: only on the public page (the private header
            // has no filter slot even though the geometry exists).
            if (button == 0 && topPage() == AppPage.WORLD_CHAT && isFilterButtonHit(mx, my)) {
                ShellHeader.armActionPress();
                cycleWorldFilter();
                com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                return true;
            }

            // Private read-only pages (offline/blocked) must not let the composer
            // buttons or the text field take focus.
            if (isPrivateReadOnly()
                    && (layout.inputBar.contains((float) mx, (float) my)
                    || layout.imageBtn.contains((float) mx, (float) my)
                    || layout.emojiBtn.contains((float) mx, (float) my)
                    || layout.phraseBtn.contains((float) mx, (float) my)
                    || layout.sendBtn.contains((float) mx, (float) my))) {
                return true;
            }

            // The emoji toggle button is tested before the panel's own "click outside
            // dismisses" rule, otherwise closing and reopening in the same click nets
            // back to open and the button can never toggle the panel off.
            if (button == 0 && layout.emojiBtn.contains((float) mx, (float) my)) {
                primitives.pressButton(1);
                com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                primitives.setButtonHeld(1);
                composer.setFocused(true);
                closePhrasePanel();
                reactionTarget = null;
                emojiPanel.toggle();
                com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                return true;
            }

            // Quick-phrase toggle: same pre-dismiss ordering as the emoji button.
            if (button == 0 && layout.phraseBtn.contains((float) mx, (float) my)) {
                primitives.pressButton(3);
                com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                primitives.setButtonHeld(3);
                composer.setFocused(true);
                emojiPanel.close();
                if (quickPhrasePanel.isOpen()) {
                    closePhrasePanel();
                } else {
                    quickPhrasePanel.open();
                }
                return true;
            }

            // Emoji panel click: tabs first, then the currently visible grid.
            if (emojiPanel.isOpen()) {
                if (emojiPanel.overPanel(layout, mx, my)) {
                    String inserted = emojiPanel.click(layout, mx, my);
                    if (inserted != null && !inserted.isEmpty()) {
                        if (reactionTarget != null) {
                            com.atom.chat.reaction.ReactionClient.toggle(reactionTarget, inserted);
                            emojiPanel.close(); reactionTarget = null;
                        } else composer.append(inserted);
                        com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                    }
                    return true;
                }
                emojiPanel.close();
                reactionTarget = null;
            }

            // Quick-phrase panel: every action is settled by the screen so an
            // in-progress composer edit is committed/cancelled in the right
            // order; an outside click closes (and thus commits) the panel.
            if (quickPhrasePanel.isOpen()) {
                if (quickPhrasePanel.overPanel(layout, mx, my)) {
                    handlePhraseAction(quickPhrasePanel.click(layout, mx, my));
                    return true;
                }
                closePhrasePanel();
            }

            // Context menu click. Remember the target before dismissing so a
            // right-click on the same bubble/avatar toggles instead of reopening.
            ChatMessage menuBefore = contextMenu.currentRow();
            boolean menuBeforeAvatar = contextMenu.isAvatarMode();
            if (contextMenu.hasMessageMenu()) {
                int row = contextMenu.hitRow((float) mx, (float) my);
                if (row >= 0 && button == 0) {
                    if (menuBeforeAvatar) {
                        contextActions.performAvatarMenuAction(row, menuMessageOf(menuBefore));
                        com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                    } else if (row == 0) {
                        boolean copied = copyToClipboard(menuBefore.getContentText());
                        feedback("atomchat.feedback.copied", copied);
                        if (copied) com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                    } else if (row == 1) {
                        replySlots.arm(menuBefore);
                        com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                        composer.setFocused(true);
                        setFocused(input);
                        input.setFocused(true);
                    } else if (row == ContextMenuController.rows(ContextMenuController.ContextMenuMode.BUBBLE,
                            Cicodes.extractImageUrl(menuBefore.getRawText()) != null)
                            && com.atom.chat.reaction.ReactionClient.available(menuBefore)) {
                        reactionTarget = menuBefore;
                        closePhrasePanel();
                        emojiPanel.openReaction();
                        com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                    } else {
                        saveImage(menuBefore);
                        com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                    }
                    contextMenu.close();
                    return true;
                }
                // Any other click — including a right-click aimed at another bubble —
                // dismisses first; the handlers below may then open a new menu.
                contextMenu.close();
            }

            // Jump-to-latest bubble sits above the message list and takes priority
            // over ordinary message clicks when it is visible.
            if (button == 0 && overJumpLatest(layout, mx, my)) {
                scrollSync.pressJumpLatest();
                currentScroll().scrollToBottom(true);
                com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                return true;
            }

            if (button == 0 && messageListView.clickReaction(mx, my)) {
                com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                return true;
            }

            // Button row: image / emoji / send share one row and one size,
            // geometry comes from UiLayout so hits can never drift from the drawing.
            if (button == 0 && layout.imageBtn.contains((float) mx, (float) my)) {
                primitives.pressButton(0);
                com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                primitives.setButtonHeld(0);
                composer.setFocused(true);
                pickAndUploadImage();
                return true;
            }
            // Inserting an emoji must NOT close the panel: users often want to pick several
            // in a row. The panel still closes on any outside click or the toggle button.
            if (button == 0 && layout.sendBtn.contains((float) mx, (float) my)) {
                primitives.pressButton(2);
                primitives.setButtonHeld(2);
                if (quickPhrasePanel.isEditing()) {
                    // In phrase-edit mode the send button commits the phrase.
                    endPhraseEdit(true);
                    return true;
                }
                sendMessage(composer.text());
                return true;
            }

            // Input box focus
            composer.setFocused(false);
            dismissSuggestor();
            if (button == 0 && layout.inputBar.contains((float) mx, (float) my)) {
                composer.setFocused(true);
                setFocused(input);
                input.setFocused(true);
                // Click-to-position / drag-select on the multi-line Skia input:
                // map the virtual point to a text index ourselves (the hidden
                // EditBox geometry is caret-shifted and single-line, useless here).
                int idx = composerPointer.caretIndexAt(layout, (float) mx, (float) my);
                if (hasShiftDown() && composerPointer.dragAnchor() >= 0) {
                    input.setHighlightPos(idx);
                } else {
                    composerPointer.setDragAnchor(idx);
                    input.moveCursorTo(idx, false);
                }
                composerPointer.setDragging(true);
                return true;
            }
            setFocused(null);
            input.setFocused(false);

            // Scrollbar drag start
            if (button == 0 && overScrollbarTrack(layout, mx, my, currentScroll())) {
                currentScroll().beginDrag(my);
                return true;
            }

            // Arm a click candidate for every left press before message interactions.
            // This covers clickable sender names and any clickable span outside bubble
            // text; the text-line branch below may overwrite it with the same result.
            if (button == 0) {
                messageWiring.armClickAt(mx, my);
                // The folded-history button floats over the top of the list, so it
                // claims the press before any message row beneath it can.
                if (messageListView.loadEarlierHitAt(mx, my)) {
                    messageListView.requestLoadEarlier();
                    return true;
                }
                // A press on a picture whose bitmap is already loaded opens the
                // preview. imageHitAt answers only for a bubble the last frame
                // resolved an image for, so the loading plate and the [图片]
                // capsule fall through to the ordinary row handling below.
                MessageListView.MessageHit imageHit = messageListView.imageHitAt(mx, my);
                if (imageHit != null) {
                    // The bitmap comes from the cache the bubble drew from, so
                    // opening the preview downloads nothing a second time.
                    Image bitmap = ImageLoader.get().get(imageHit.imageUrl(), true);
                    if (bitmap != null) {
                        imagePreview.open(bitmap);
                        // This press belongs to the modal now: nothing was armed
                        // for a clickable span or a selection on the way here.
                        messageWiring.clearClick();
                        return true;
                    }
                }
            }

            // Message interactions. Left avatar click only arms the double-click
            // poke; single-click @ is deliberately gone (use the right-click menu).
            // Right-click opens the bubble menu only when the pointer is actually on
            // the bubble; right-click on a real player's avatar opens the avatar menu.
            for (MessageListView.MessageHit hit : messageListView.hits()) {
                if (my < hit.y() || my > hit.bottom()) {
                    continue;
                }
                if (button == 1 && hit.avatarSize() > 0F
                        && mx >= hit.avatarX() && mx <= hit.avatarX() + hit.avatarSize()
                        && my >= hit.avatarY() && my <= hit.avatarY() + hit.avatarSize()
                        && ContextMenuActions.canOpenAvatarMenu(menuMessageOf(hit.message()))) {
                    // Right-clicking the avatar the avatar menu is already on closes it.
                    if (menuBefore == hit.message() && menuBeforeAvatar) {
                        com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                        return true;
                    }
                    contextMenu.openAvatar(menuSubjectOf(hit.message()), mx, my);
                    com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                    return true;
                }
                if (button == 1 && !hit.message().isSystem()
                        && mx >= hit.bubbleX() && mx <= hit.bubbleX() + hit.bubbleWidth()
                        && my >= hit.bubbleY() && my <= hit.bubbleBottom()) {
                    // Right-clicking the bubble the menu is already on closes it.
                    if (menuBefore == hit.message()) {
                        com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                        return true;
                    }
                    contextMenu.openBubble(menuSubjectOf(hit.message()), mx, my);
                    com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
                    return true;
                }
                if (button == 0) {
                    List<MessageListView.MessageTextLine> textLines = messageListView.textLinesForHit(hit);
                    for (MessageListView.MessageTextLine line : textLines) {
                        float lineRight = line.x() + SkiaFontRenderer.getStringWidth(
                                FontManager.font(line.message().isSystem() ? UiTokens.FONT_QUOTE : UiTokens.FONT_BODY),
                                line.text());
                        if (mx >= line.x() && mx <= lineRight && my >= line.y() && my <= line.y() + line.height()) {
                            // Remember any clickable run under the mouse before the
                            // selection anchor is armed. Dragging from this point will
                            // select text instead of firing the click; a clean click
                            // (no drag) will fire it on mouse release.
                            messageWiring.armClickAt(mx, my);
                            messageListView.beginSelection(hit, line, mx);
                            return true;
                        }
                    }
                }
                if (button == 0 && hit.avatarSize() > 0F
                        && mx >= hit.avatarX() && mx <= hit.avatarX() + hit.avatarSize()
                        && my >= hit.avatarY() && my <= hit.avatarY() + hit.avatarSize()) {
                    // The competition window's three branches and their order
                    // are the domain's (D18b); the frame's clock is this click's.
                    avatarWiring.handleAvatarClick(System.currentTimeMillis(),
                            factsOf(hit.message()), hit.index());
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean onDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (!isWorldChatPage()) {
                return false;
            }
            // Input drag selection: extend from the press anchor to the pointer.
            if (composerPointer.dragging() && button == 0 && input != null) {
                int idx = composerPointer.caretIndexAt(layout(), toVirtualX(mouseX), toVirtualY(mouseY));
                input.setHighlightPos(idx);
                return true;
            }
            // Any drag while a click is pending must suppress the click-on-release,
            // including drags that do not start a text selection (e.g. name bars).
            if (button == 0 && messageWiring.hasPendingClick()) {
                messageWiring.markMoved();
            }
            if (button == 0 && messageListView.isSelecting()) {
                float mx = toVirtualX(mouseX);
                float my = toVirtualY(mouseY);
                // Consumed on every drag while selecting (matching the original
                // block, which always returned true); the view keeps the focus
                // update and selectionMoved bookkeeping internally.
                if (messageListView.dragSelection(mx, my)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean onRelease(double mouseX, double mouseY, int button) {
            if (!isWorldChatPage()) {
                return false;
            }
            // The composer buttons live in THIS handler, so their held-press state
            // is cleared here: parked in RootPageInput.onRelease it never ran (the two
            // guards are opposites) and the button stayed squashed at 0.92.
            primitives.setButtonHeld(-1);
            // Same for the folded-history capsule and the jump-to-latest FAB: both
            // are armed on press and must be released here, or the bounce never
            // gets its release half.
            messageListView.setLoadEarlierPressed(false);
            scrollSync.releaseJumpLatest();
            if (button == 0) {
                float mx = toVirtualX(mouseX);
                float my = toVirtualY(mouseY);
                boolean wasSelecting = messageListView.isSelecting();
                ClickableSpan pending = messageWiring.takePending();
                boolean shouldClick = messageWiring.shouldFireClick(pending, mx, my);
                if (wasSelecting) {
                    messageListView.endSelection();
                }
                messageWiring.clearMoved();
                if (composerPointer.dragging()) {
                    composerPointer.setDragging(false);
                }
                if (shouldClick) {
                    handleComponentClicked(pending.style());
                    return true;
                }
                if (wasSelecting) {
                    return true;
                }
            }
            if (currentScroll().isDragging() && button == 0) {
                currentScroll().endDrag();
                return true;
            }
            return false;
        }

        @Override
        public boolean onScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (!isWorldChatPage()) {
                return false;
            }
            float mx = toVirtualX(mouseX);
            float my = toVirtualY(mouseY);
            // Suggestion popup scrolls first when open.
            if (suggestorWiring.mouseScrolled(verticalAmount)) {
                return true;
            }
            if (emojiPanel.isOpen() && emojiPanel.overPanel(layout(), mx, my)) {
                emojiPanel.scroll(layout(), verticalAmount);
                return true;
            }
            if (quickPhrasePanel.isOpen() && quickPhrasePanel.overPanel(layout(), mx, my)) {
                quickPhrasePanel.scroll(layout(), verticalAmount);
                return true;
            }
            UiLayout.Rect list = layout().list;
            if (list.contains(mx, my)) {
                currentScroll().wheel((float) verticalAmount);
                return true;
            }
            return false;
        }

        @Override
        public boolean onKey(int keyCode, int scanCode, int modifiers) {
            if (!isWorldChatPage()) {
                return false;
            }
            if (isPrivateReadOnly()) {
                return true;
            }
            // Copy selected message text before the vanilla field/suggestion layer
            // consumes Ctrl+C. An empty result (endpoints scrolled out of the
            // drawn window and nothing in the feed) falls through to the input
            // field's own copy instead of swallowing the key.
            if (keyCode == 67 && (modifiers & 2) != 0 && messageListView.hasSelection()) {
                String copied = messageListView.copySelection();
                if (!copied.isEmpty()) {
                    feedback("atomchat.feedback.copied", copyToClipboard(copied));
                    return true;
                }
                return false;
            }
            // Ctrl+V with a picture on the clipboard. MC's clipboard API only hands
            // out strings, so the vanilla field would paste nothing at all; that
            // flavour has to be intercepted before it gets that far.
            if (keyCode == GLFW_KEY_V && (modifiers & 2) != 0) {
                DataFlavor flavor = ClipboardImages.peek();
                if (flavor != null) {
                    pasteFromClipboard(flavor);
                    return true;
                }
            }
            // Vanilla suggestion layer gets first pick (Tab/arrows over the popup).
            if (suggestorWiring.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter
                if (composer.focused()) {
                    sendMessage(composer.text());
                }
                return true;
            }
            // Up/Down become caret navigation as soon as the text wraps onto a second
            // line (>= INPUT_MAX_LINES). Once multiline, Up/Down never fall back to
            // vanilla chat history — that remains a single-line behaviour.
            if (composer.focused() && composer.fieldFocused() && (keyCode == 265 || keyCode == 264)) {
                List<String> lines = composer.wrappedInput(layout().inputTextMaxWidth());
                if (lines.size() >= UiTokens.INPUT_MAX_LINES) {
                    int caret = composer.caretIndex();
                    int row = ComposerInput.caretLine(lines, caret);
                    int target = (keyCode == 265) ? row - 1 : row + 1;
                    if (target >= 0 && target < lines.size()) {
                        int rowStart = 0;
                        for (int i = 0; i < row; i++) {
                            rowStart += lines.get(i).length();
                        }
                        int targetStart = 0;
                        for (int i = 0; i < target; i++) {
                            targetStart += lines.get(i).length();
                        }
                        // Move straight up/down at the same visual column, clamped to
                        // the target line's length (standard text-editor behaviour).
                        int col = Mth.clamp(caret - rowStart, 0, lines.get(row).length());
                        int pos = targetStart + Math.min(col, lines.get(target).length());
                        input.moveCursorTo(pos, false);
                    }
                    return true;
                }
            }
            if (composer.focused() && AtomChatConfig.get().debug) {
                AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("ui", "keyPressed: {} (sc {}) mod {}"), keyCode, scanCode, modifiers);
            }
            // Falls through to super (ChatScreen): the focused input consumes
            // backspace/ctrl+v/arrows/IME input; up/down drive vanilla chat history.
            return false;
        }

        @Override
        public boolean onChar(char chr, int modifiers) {
            if (!isWorldChatPage()) {
                return false;
            }
            if (AtomChatConfig.get().debug) {
                AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("ui", "charTyped: '{}' (U+{}) focused={} field={}"),
                        chr, Integer.toHexString(chr), composer.focused(), composer.fieldFocused());
            }
            return false;
        }
    }
}
