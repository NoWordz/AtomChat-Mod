package com.atom.chat.screen;
import com.atom.chat.AtomChat;

import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.PublicEchoTracker;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.Cicodes;
import com.atom.chat.chat.MessageFilter;
import com.atom.chat.chat.TeleportCommands;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.emote.EmoteStore;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.image.ImageSaver;
import com.atom.chat.image.ImageUploader;
import com.atom.chat.net.MediaCompanionClient;
import com.atom.chat.chat.LocalEcho;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.chat.PrivateChatStore;
import com.atom.chat.chat.PrivateEchoTracker;
import com.atom.chat.chat.OwnIdentity;
import com.atom.chat.text.RichText;
import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.AtomChatState;
import com.atom.chat.nav.NavPage;
import com.atom.chat.nav.NavigationStack;
import com.atom.chat.notification.NotificationBanner;
import com.atom.chat.avatar.AvatarImage;
import com.atom.chat.avatar.AvatarStore;
import com.atom.chat.avatar.ColorPickerOverlay;
import com.atom.chat.avatar.ImageCropper;
import com.atom.chat.image.OwnPlayerAvatarSource;
import com.atom.chat.page.ConversationListPage;
import com.atom.chat.page.MessageListView;
import com.atom.chat.page.PageHost;
import com.atom.chat.page.ProfilePage;
import com.atom.chat.font.FontManager;
import com.atom.chat.mixin.MouseHandlerAccessor;
import com.atom.chat.render.Animator;
import com.atom.chat.render.BlurMotionGate;
import com.atom.chat.render.ClickableSpan;
import com.atom.chat.render.Easing;
import com.atom.chat.render.PanelBlurRenderer;
import com.atom.chat.render.RichTextRenderer;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.render.SkiaGraphics;
import com.atom.chat.settings.SettingsHomePage;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.BottomTabBar;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.ShellHeader;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.EmojiPanel;
import com.atom.chat.ui.PanelBackground;
import com.atom.chat.ui.QuickPhrasePanel;
import com.atom.chat.ui.SpringAnim;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiSpring;
import com.atom.chat.ui.UiTokens;
import com.atom.chat.ui.input.InputHandler;
import com.atom.chat.ui.input.InputRouter;
import com.atom.chat.wallpaper.WallpaperImage;
import com.atom.chat.wallpaper.WallpaperStore;
import com.atom.chat.util.ClipboardImages;
import com.atom.chat.util.FilePicker;
import com.atom.chat.util.ImageFileNames;
import com.atom.chat.util.ImageFiles;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;
import io.github.humbleui.skija.SamplingMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.StringUtils;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class AtomChatScreen extends ChatScreen implements PageHost {
    /** Which context menu is open: normal message bubble actions or player-avatar actions. */
    private enum ContextMenuMode { BUBBLE, AVATAR, PLAYER_CARD }

    /** How this screen was opened: from the vanilla chat box or from the AtomChat key. */
    public enum AtomChatOpenMode { DIRECT_WORLD, RESTORE }

    private final Minecraft client = Minecraft.getInstance();
    /** Command/mention suggestor anchored to the Skia input row. */
    private CommandSuggestions chatInputSuggestor;

    private final NavigationStack<NavPage> navigation;

    private final ConversationListPage conversationListPage = new ConversationListPage(this);
    /** Local custom avatar for the profile page and own bubbles. */
    private final AvatarStore avatarStore = new AvatarStore(
            net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("atomchat/avatar"));
    /** QQ-style crop overlay for avatar and wallpaper picks. */
    private final ImageCropper imageCropper = new ImageCropper(new ImageCropper.Callback() {
        @Override
        public void onConfirm(String targetId, byte[] pngBytes) {
            if ("avatar".equals(targetId)) {
                if (avatarStore.setPng(pngBytes)) {
                    AvatarImage.release();
                    // Companion push: skipped only when the server has no
                    // companion channels at all (negotiation, not a timeout).
                    if (client.player != null) {
                        com.atom.chat.net.AvatarCompanionClient.uploadOwnAvatar(
                                client.player.getUUID(), pngBytes);
                    }
                }
            } else if ("wallpaper".equals(targetId)) {
                if (WallpaperStore.setPng(pngBytes)) {
                    WallpaperImage.release();
                }
            }
        }

        @Override
        public void onCancel(String targetId) {
            // Nothing to clean up; the picked file is simply dropped.
        }
    });
    /** Modal HSV colour picker opened from the settings colour rows. */
    private final ColorPickerOverlay colorPicker = new ColorPickerOverlay(this::copyToClipboard);
    private final ProfilePage profilePage = new ProfilePage(new ProfilePage.Handler() {
        @Override
        public void openAvatarPicker() {
            pickAvatarFile();
        }

        @Override
        public void clearAvatar() {
            avatarStore.clear();
            AvatarImage.release();
        }

        @Override
        public void copyText(String text) {
            copyToClipboard(text);
        }
    }, avatarStore);
    private final SettingsHomePage settingsHomePage = new SettingsHomePage();
    private final SettingsSectionPage settingsSectionPage = new SettingsSectionPage();
    /** Per-chip scroll memory ("section:chip" -> offset); the detail
     *  controller is shared across chips. */
    private final java.util.HashMap<String, Float> chipScroll = new java.util.HashMap<>();
    private String chipScrollKey = "";

    {
        // Action cards (pick/clear wallpaper) need the shell's file picker,
        // which lives here rather than in the settings package.
        settingsSectionPage.setActionHandler(this::handleSettingsAction);
        // The custom-avatar source needs the store instance created above.
        OwnPlayerAvatarSource.attach(avatarStore);
        // Role row: vanilla 1.21.1 only syncs the local player's own permission
        // level; other players stay unknown (row hidden) until a server
        // companion can answer for them.
        profilePage.setRoleResolver((uuid, name) -> {
            if (uuid != null && this.client.player != null
                    && uuid.equals(this.client.player.getUUID())) {
                return this.client.player.hasPermissions(2);
            }
            return null;
        });
    }

    /** Settings action cards: the wallpaper pair and the teleport mode cycle. */
    private void handleSettingsAction(String actionId) {
        if ("wallpaper_pick".equals(actionId)) {
            pickWallpaperFile();
        } else if ("wallpaper_clear".equals(actionId)) {
            WallpaperStore.clear();
            WallpaperImage.release();
        } else if ("test_sound".equals(actionId)) {
            com.atom.chat.notification.NotificationController.playTestSound();
        } else if ("teleport_mode".equals(actionId)) {
            AtomChatConfig cfg = AtomChatConfig.get();
            String current = cfg.teleportCommandMode == null ? "auto" : cfg.teleportCommandMode;
            // auto -> tp -> tpa -> auto. The old cycle was tp -> tpa -> tp:
            // once you left auto there was no way back — reported and fixed.
            cfg.teleportCommandMode = switch (current) {
                case "tp" -> "tpa";
                case "tpa" -> "auto";
                default -> "tp";
            };
            TeleportCommands.reset();
            AtomChatConfig.save(cfg);
        } else if ("history_clear".equals(actionId)) {
            // Wipes the current session (and the world's saved file when
            // persistence is on). Generation bumped inside: a pending auto-save
            // cannot resurrect what was just deleted.
            com.atom.chat.history.ChatHistory.clearCurrent();
        } else if ("cache_clear".equals(actionId)) {
            // Image downloads are safe to drop; they are re-fetched on demand.
            ImageLoader.get().clearDiskCache();
        }
    }

    /** Picks a wallpaper image off-thread; the store mutation returns to render. */
    private void pickWallpaperFile() {
        KeyMapping.releaseAll();
        if (this.client.mouseHandler != null) {
            ((MouseHandlerAccessor) this.client.mouseHandler).atomchat$setActiveButton(0);
        }
        Thread worker = new Thread(() -> {
            Path file = FilePicker.pickImage(this::suppressAutoIconify, this::restoreAutoIconify,
                    WallpaperStore::isSupportedName);
            refocusWindow();
            if (file == null) {
                return;
            }
            this.client.execute(() -> {
                // Crop at the current panel aspect so what you frame is what
                // the panel shows (cover-fit has nothing left to do).
                UiLayout l = layout();
                imageCropper.open(file, false, l.rect().w(), l.rect().h(), "wallpaper");
            });
        }, "AtomChat-WallpaperPicker");
        worker.setDaemon(true);
        worker.start();
    }
    /** Picks a custom avatar off-thread; the cropper opens on the render side. */
    private void pickAvatarFile() {
        KeyMapping.releaseAll();
        if (this.client.mouseHandler != null) {
            ((MouseHandlerAccessor) this.client.mouseHandler).atomchat$setActiveButton(0);
        }
        Thread worker = new Thread(() -> {
            Path file = FilePicker.pickImage(this::suppressAutoIconify, this::restoreAutoIconify,
                    AvatarStore::isSupportedName);
            refocusWindow();
            if (file == null) {
                return;
            }
            this.client.execute(() -> imageCropper.open(file, true, 0.0F, 0.0F, "avatar"));
        }, "AtomChat-AvatarPicker");
        worker.setDaemon(true);
        worker.start();
    }

    /** Root-page mouse coordinates in virtual UI space, for card hover rendering. */
    private float rootMouseX;
    private float rootMouseY;
    /** When true, ShellHeader is skipped while moving page layers in a transition. */
    private boolean suppressHeader;

    private final SkiaGraphics graphics = new SkiaGraphics();
    private final String originalChatText;
    private final ImageUploader imageUploader = new ImageUploader();
    /**
     * Ordered input routing. Priority (first registered wins): closing guard,
     * modals (cropper/colour picker), pushed-subpage Esc, screen Esc, root
     * pages, world chat. New interactive features register a handler instead
     * of inserting branches into the former if-else chains.
     */
    private final InputRouter inputRouter = new InputRouter()
            .add(new ClosingStateInput())
            .add(new ModalInput())
            .add(new NumberEditInput())
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
    private final MessageListView messageListView = new MessageListView(new MessageListView.Host() {
        @Override
        public UUID ownUuid() {
            return client.player != null ? client.player.getUUID() : null;
        }

        @Override
        public String ownName() {
            return AtomChatScreen.this.ownName();
        }

        @Override
        public String senderName(ChatMessage message) {
            return AtomChatScreen.this.messageSenderName(message);
        }

        @Override
        public long openStart() {
            return AtomChatScreen.this.openStart;
        }
    });
    /**
     * Emoji / kaomoji / emote panel: state, geometry and rendering live in the
     * panel class; the screen only supplies composer side effects (insert
     * text, send sticker, open the picker).
     */
    private final EmojiPanel emojiPanel = new EmojiPanel(new EmojiPanel.Host() {
        @Override
        public void insert(String text) {
            inputAppend(text);
        }

        @Override
        public void sendSticker(Path file) {
            uploadAndAppend(file);
        }

        @Override
        public void pickEmoteFile() {
            AtomChatScreen.this.pickEmoteFile();
        }
    });

    /**
     * Quick phrases above the composer. Tapping one inserts it into the input
     * and closes the panel — the phrase can still be edited before sending.
     */
    private final QuickPhrasePanel quickPhrasePanel = new QuickPhrasePanel(this::inputAppend, this::accent);

    private boolean inputFocused = true;
    /** Scroll state for the world-chat message list. */
    private final ScrollController worldScroll = new ScrollController();
    /** Per-private-conversation scroll controllers; draft map keys are PlayerRef.key(). */
    private final Map<String, ScrollController> privateScrolls = new HashMap<>();
    private final Map<String, String> privateDrafts = new HashMap<>();
    /** Draft for the public world channel; kept separately because the hidden
     *  EditBox is shared by every chat page. */
    private String worldDraft = "";
    /** Scroll state for root pages; shared across the root tabs, opening at the top. */
    private final ScrollController rootScroll = new ScrollController(false);
    /** Scroll state for pushed detail pages (settings/profile), opening at the top. */
    private final ScrollController detailScroll = new ScrollController(false);
    private ChatMessage replyTarget;
    private ChatMessage contextMessage;
    private PlayerRef contextPlayer;
    private float contextX;
    private float contextY;
    private ContextMenuMode contextMenuMode = ContextMenuMode.BUBBLE;

    // Per-cell hover fade for the bubble context menu rows.
    private final float[] contextMenuHover = new float[4];

    // Root tab transition. The bottom bar owns the shared Animator; the screen
    // keeps the from/to slot indexes and reuses the same Animator for the root
    // page push so the capsule and content always move in lockstep.
    private final BottomTabBar bottomTabBar = new BottomTabBar();
    private final Animator rootTabAnim = bottomTabBar.indicatorAnimator();
    private int rootTabFrom = -1;
    private int rootTabTo = -1;

    // Page push/pop transition (root <-> world chat). The world page slides
    // horizontally over a static root page; the navigation entry changes only
    // after the animation settles so the active page stays consistent.
    private final Animator pageNavAnim = new Animator(Easing::easeInOutCubic);
    private NavPage pageNavFrom;
    private NavPage pageNavTo;
    private boolean pageNavPopPending;
    /** Melodify-style page enter: 0.94 -> 1 on a bounce spring, fade ~120ms. */
    private static final float PAGE_ENTER_SCALE_FROM = 0.94F;
    private static final long PAGE_ENTER_FADE_MS = 120L;
    private final SpringAnim pageNavScale = UiSpring.newBounceSpring();
    private long pageNavStartMs;

    /** Hover wash behind the unified header back arrow. */
    private float backButtonHover;

    /**
     * Public-feed view filter (all / system only / players only), cycled by the
     * header button. View-only: storage, unread counts and previews always see
     * the unfiltered feed. A fresh screen instance starts at ALL, so closing
     * and reopening the panel resets it by construction.
     */
    private MessageFilter worldFilter = MessageFilter.ALL;
    /** Hover wash behind the header filter button (world chat only). */
    private float filterButtonHover;
    /** Hover washes for the emoji panel tab strip (owned by {@link EmojiPanel}). */

    // Animation state — durations live in UiMotion so every transition is tuned
    // in one place and none of them can drift back to a sluggish value. The
    // panel slide is the one exception: it rides the spring language
    // (UiSpring/SpringAnim) so opening lands fast and dead — near-critically
    // damped, no bounce.
    // Toolbar icons are kept as inline SVG path data (not assets): three tiny
    // paths are cheaper than a resource pipeline, stay crisp at every scale,
    // and are trivial to recolour for hover/pressed/theme states. The paths use
    // a 20x20 logical space; drawIcon() fits them into the button bounds.
    // Landscape photo glyph: a wide rounded rect (14x9 on the 20 grid), sun
    // top-left, mountains inside — the old one was portrait and read as a
    // phone photo instead of an image.
    // Image glyph fills a ~14x12 box: fit-by-longest-edge scaling leaves the
    // old 13x9 glyph visibly shorter than the emoji/send neighbours.
    private static final String ICON_IMAGE_SVG = "M4.5 4 L15.5 4 A1.5 1.5 0 0 1 17 5.5 L17 14.5 A1.5 1.5 0 0 1 15.5 16 L4.5 16 A1.5 1.5 0 0 1 3 14.5 L3 5.5 A1.5 1.5 0 0 1 4.5 4 Z"
            + " M7 7.2 m-1.3 0 a1.3 1.3 0 1 0 2.6 0 a1.3 1.3 0 1 0 -2.6 0"
            + " M4 15 L8.6 10.9 L11.2 13.2 L13.6 10.6 L16 13.2";
    private static final String ICON_EMOJI_SVG = "M10 3 a7 7 0 1 0 0 14 a7 7 0 1 0 0 -14"
            + " M7 8.6 v1.3 M13 8.6 v1.3"
            + " M6.8 12.2 C8.5 14.3 11.5 14.3 13.2 12.2";
    // Feather-style send: one diagonal fold + the paper-plane outline.
    private static final String ICON_SEND_SVG = "M18 2.5 L9.5 11"
            + " M18 2.5 L13.5 18.5 L9.5 11 L2.5 7.5 Z";
    // Context-menu icons, same 20x20 line-icon language as the toolbar.
    private static final String ICON_COPY_SVG = "M5 3 L11 3 L11 9 L5 9 Z M8 7 L14 7 L14 13 L8 13 Z";
    private static final String ICON_QUOTE_SVG = "M3 3 L17 3 L17 12 L10 12 L6 16 L7 12 L3 12 Z"
            + " M7 6 L7 9 M10 6 L10 9";
    private static final String ICON_SAVE_SVG = "M10 3 L10 11 M7 8 L10 11 L13 8"
            + " M4 15 L4 17 L16 17 L16 15";
    // Avatar context-menu icons, same 20x20 line-icon language.
    // Lucide at-sign (ISC): a true "@" shape — inner circle plus the outer
    // ring/tail stroke. Coordinates are in Lucide's 24x24 space and are fitted
    // by drawIconCentered exactly like the other icon paths.
    private static final String ICON_MENTION_SVG = "M12 8 a4 4 0 1 0 0 8 a4 4 0 1 0 0 -8"
            + " M16 8 v5 a3 3 0 0 0 6 0 v-1 a10 10 0 1 0 -4 8";
    private static final String ICON_WHISPER_SVG = "M3 4 L17 4 L17 14 L10 14 L6 18 L7 14 L3 14 Z";
    private static final String ICON_TP_SVG = "M3 17 L17 3 M17 3 L10 3 M17 3 L17 10";
    private static final String ICON_BLOCK_SVG = "M10 3 a7 7 0 1 0 0 14 a7 7 0 1 0 0 -14"
            + " M4.5 4.5 L15.5 15.5";
    private static final io.github.humbleui.skija.Path ICON_IMAGE_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_IMAGE_SVG);
    private static final io.github.humbleui.skija.Path ICON_EMOJI_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_EMOJI_SVG);
    /** Quick-phrase button: a lightning bolt (Lucide zap). */
    private static final String ICON_PHRASE_SVG = "M13 2 3 14h9l-1 8 10-12h-9l1-8z";
    private static final io.github.humbleui.skija.Path ICON_PHRASE_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_PHRASE_SVG);
    private static final io.github.humbleui.skija.Path ICON_SEND_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_SEND_SVG);
    private static final io.github.humbleui.skija.Path ICON_COPY_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_COPY_SVG);
    private static final io.github.humbleui.skija.Path ICON_QUOTE_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_QUOTE_SVG);
    private static final io.github.humbleui.skija.Path ICON_SAVE_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_SAVE_SVG);
    private static final io.github.humbleui.skija.Path ICON_MENTION_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_MENTION_SVG);
    private static final io.github.humbleui.skija.Path ICON_WHISPER_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_WHISPER_SVG);
    private static final io.github.humbleui.skija.Path ICON_TP_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_TP_SVG);
    private static final io.github.humbleui.skija.Path ICON_BLOCK_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_BLOCK_SVG);
    private static final String ICON_JUMP_DOWN_SVG = "M4 8 L10 14 M10 14 L16 8 M10 3 L10 14";
    private static final io.github.humbleui.skija.Path ICON_JUMP_DOWN_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_JUMP_DOWN_SVG);

    private static final int GLFW_KEY_V = 86;
    /** Open-animation slide distance, in virtual px. The blur pre-pass offsets
     * its capture rect by the same amount, so the two must stay in step. */
    private static final float OPEN_SLIDE_PX = 10.0F;
    /** Slack around the panel in the fade layer, so bezel/shadow are not clipped. */
    private static final float LAYER_CHROME = 32.0F;
    private final long openStart = System.currentTimeMillis();
    private boolean closing;
    /** Panel open/close progress (0 hidden, 1 open); slightly overshoots both ends. */
    private float panelProgress = 1.0F;
    /** Spring behind panelProgress; the tuned feel lives in UiSpring.PANEL_*. */
    private final SpringAnim panelSpring = UiSpring.newPanelSpring();
    /** Wall-clock timestamp of the previous frame — the spring consumes real dt. */
    private long lastFrameNanos = System.nanoTime();
    /** Composer height seen by the previous blur-gate frame, for change detection. */
    private float lastBlurInputExtraH;
    private boolean blurDrawnThisFrame;
    private int pressedButton = -1;
    private long pressTime;

    // Per-frame animation state (smooth hover/popup transitions)
    private final float[] buttonHover = new float[4];
    private float contextAnim;
    private float jumpLatestAnim;
    private ChatMessage lastContextMessage;
    private PlayerRef lastContextPlayer;
    private ContextMenuMode lastContextMenuMode = ContextMenuMode.BUBBLE;
    private long frameDt = 16;
    private long lastFrameMs = System.currentTimeMillis();

    // Multi-line input: the bar grows upward by whole line heights, and once the
    // text passes INPUT_MAX_LINES it scrolls inside the fixed box.
    private float inputExtraH;
    /**
     * Tracks the height transition as start/end/time rather than per-frame lerp:
     * UiMotion.approach is unitless, so on a pixel-valued target it only covers
     * a fraction of the distance per frame and leaves a multi-hundred-ms tail.
     * Animator guarantees the bar reaches its target height within INPUT_GROW_MS.
     */
    private final Animator inputHeightAnim = new Animator(t -> t);
    private int inputScrollLine;
    private String inputWrapText;
    private float inputWrapWidth = -1.0F;
    private List<String> inputWrapCache;

    /** Set while an upload is in flight; the input placeholder reads it. */
    private volatile boolean imageUploading;
    /** Short-lived hint shown in the empty input placeholder (save errors etc). */
    private volatile String transientHint;
    private long transientHintSetAt;
    /** GLFW drop callback, installed while this screen is open (MC sets none). */
    private GLFWDropCallback dropCallback;

    private long lastAvatarClickTime;
    private int lastAvatarClickIndex = -1;
    /** Single-click→profile competition window (QQ standard), in ms. */
    private static final long AVATAR_CLICK_WINDOW_MS = 300;
    private long pendingAvatarClickTime;
    private int pendingAvatarClickIndex = -1;
    private ChatMessage pendingAvatarClickMessage;
    // Input mouse drag selection (0.1.11): the hidden EditBox is single-line
    // and its X shifts per caret (IME anchor), so its own hit-testing cannot
    // serve the multi-line Skia input — map virtual coords to an index.
    private boolean inputDragging;
    private int inputDragAnchor = -1;
    /** Last IME conversion state pushed to IMBlocker (English = typing a command). */
    private boolean imeEnglishState;

    // Message text drag-selection state lives in MessageListView; the screen
    // keeps only the click/drag coexistence flow (Task 8).
    /** Pending click candidate used by the click/drag coexistence flow (Task 8). */
    private ClickableSpan pendingClickSpan;
    private boolean pendingClickMoved;

    public AtomChatScreen(String originalChatText) {
        this(originalChatText, AtomChatOpenMode.DIRECT_WORLD);
    }

    public AtomChatScreen(String originalChatText, AtomChatOpenMode mode) {
        super(originalChatText);
        this.originalChatText = originalChatText;
        this.worldDraft = originalChatText;
        // Screen objects are constructed fresh on every open (init() re-runs on
        // resize), so this is the right hook for picking up hand-edited config
        // — chat/whisper templates take effect without a game restart.
        AtomChatConfig.reload();
        this.navigation = new NavigationStack<>(NavPage.of(AppPage.CHAT_LIST));
        if (mode == AtomChatOpenMode.DIRECT_WORLD) {
            navigation.replaceWithRoot(NavPage.of(AppPage.CHAT_LIST));
            navigation.push(NavPage.of(AppPage.WORLD_CHAT));
        } else {
            List<NavPage> saved = AtomChatState.snapshot();
            navigation.replaceWithRoot(saved.get(0));
            for (int i = 1; i < saved.size(); i++) {
                navigation.push(saved.get(i));
            }
        }
        if (topPage().isRoot()) {
            bottomTabBar.setSelectedImmediate(rootIndex(topPage()));
        }
        if (topPage() == AppPage.PRIVATE_CHAT) {
            PrivateChatStore.setActive(activePrivateTarget());
            ChatStore.setPublicActive(false);
        } else if (topPage() == AppPage.WORLD_CHAT) {
            ChatStore.setPublicActive(true);
        } else {
            ChatStore.setPublicActive(false);
        }
    }

    private NavPage topNav() {
        return navigation.peek();
    }

    private AppPage topPage() {
        return navigation.peek().page();
    }

    private boolean isPrivateReadOnly() {
        if (topPage() != AppPage.PRIVATE_CHAT) {
            return false;
        }
        PlayerRef target = activePrivateTarget();
        if (target == null) {
            return true;
        }
        return !isOnlinePlayer(target) || BlockList.isBlocked(target);
    }

    private PlayerRef activePrivateTarget() {
        NavPage top = navigation.peek();
        return top != null && top.page() == AppPage.PRIVATE_CHAT ? top.target() : null;
    }

    /**
     * Detail chat pages (public world or a private conversation) share the whole
     * composer/message-list UI. The method name is kept for historical call
     * sites but also covers PRIVATE_CHAT.
     */
    private boolean isWorldChatPage() {
        AppPage page = topPage();
        return page == AppPage.WORLD_CHAT || page == AppPage.PRIVATE_CHAT;
    }

    private boolean isVanillaChatKey(int keyCode, int scanCode) {
        return client != null && client.options.keyChat.matches(keyCode, scanCode);
    }

    /**
     * Shared hit rect for the world-chat header's SVG back button. It is fixed
     * to the left edge of the header card and vertically centered.
     */
    private UiLayout.Rect backButton() {
        float size = s(36);
        UiLayout.Rect header = layout().header;
        float y = header.y() + (header.h() - size) / 2.0F;
        return new UiLayout.Rect(header.x() + s(4), y, size, size);
    }

    /** Filter button: the back button's recipe, one slot to its right. */
    private UiLayout.Rect filterButton() {
        UiLayout.Rect back = backButton();
        return new UiLayout.Rect(back.x() + back.w() + s(4), back.y(), back.w(), back.h());
    }

    private boolean isFilterButtonHit(float vmx, float vmy) {
        return filterButton().contains(vmx, vmy);
    }

    /** Cycles the feed filter and drops a selection that may span hidden rows. */
    private void cycleWorldFilter() {
        worldFilter = worldFilter.next();
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
        if (page == null || page.page() != AppPage.WORLD_CHAT) {
            return null;
        }
        io.github.humbleui.skija.Path icon =
                worldFilter == MessageFilter.SYSTEM ? AppIcons.ICON_ANNOUNCE_PATH
                        : worldFilter == MessageFilter.PLAYERS ? AppIcons.ICON_TAB_PROFILE_PATH
                        : AppIcons.ICON_FILTER_PATH;
        int color = worldFilter.isFiltering() ? accent() : textPrimary();
        return new ShellHeader.HeaderAction(filterButton(), filterButtonHover, icon, color);
    }

    private boolean isBackButtonHit(float vmx, float vmy) {
        return backButton().contains(vmx, vmy);
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
        saveCurrentDraft();
        AppPage fromPage = topPage();
        if (page.page() == AppPage.WORLD_CHAT) {
            worldScroll.reset();
        }
        if (page.page() == AppPage.SETTINGS_SECTION) {
            resetSettingsUi();
        }
        if (page.page() == AppPage.PROFILE_DETAIL) {
            detailScroll.reset();
        }
        if (!page.isRoot() && fromPage.isRoot()) {
            rootTabAnim.setValue(rootIndex(fromPage));
            startPageNav(topNav(), page, false);
        } else if (!page.isRoot() && !fromPage.isRoot()) {
            // Public <-> private detail pages also use the full-width push/pop.
            startPageNav(topNav(), page, false);
        } else {
            pageNavFrom = null;
            pageNavTo = null;
            pageNavPopPending = false;
            pageNavAnim.setValue(0.0F);
        }
        navigation.push(page);
        ChatStore.setPublicActive(page.page() == AppPage.WORLD_CHAT);
        if (page.page() == AppPage.PRIVATE_CHAT) {
            PrivateChatStore.setActive(page.target());
            loadDraft(page.target());
        } else if (page.page() == AppPage.WORLD_CHAT) {
            loadWorldDraft();
        }
        if (!page.isRoot()) {
            clearRootTransition();
        }
    }

    @Override
    public void popPage() {
        if (navigation.size() <= 1) {
            return;
        }
        if (topPage().isRoot()) {
            navigation.pop();
            return;
        }
        // With decorative motion off there is no slide-out to wait for, so the
        // pop can be applied right here instead of in finishPageNav().
        if (!Animations.enabled()) {
            popNow();
            return;
        }
        NavPage from = topNav();
        NavPage previous = navigation.snapshot().get(navigation.size() - 2);
        // Keep the detail page on top while it slides out; the actual navigation
        // pop happens in finishPageNav(). Both root and detail targets animate.
        // The subject reset lives in popNow(): clearing it here would redraw the
        // sliding-out page as the local player's profile mid-animation.
        startPageNav(from, previous, true);
    }

    /** Applies a pending pop and restores the page underneath it. */
    private void popNow() {
        boolean leavingProfile = topPage() == AppPage.PROFILE_DETAIL;
        if (topPage() == AppPage.PRIVATE_CHAT) {
            savePrivateDraft(activePrivateTarget(), inputGetText());
            PrivateChatStore.clearActive();
        }
        resetTransientWorldUi();
        resetSettingsUi();
        navigation.pop();
        // Falling back to the local player only once the page is off the stack,
        // so the Profile root tab never shows a stale injected subject.
        if (leavingProfile) {
            profilePage.resetSubject();
        }
        ChatStore.setPublicActive(topPage() == AppPage.WORLD_CHAT);
        if (topPage() == AppPage.PRIVATE_CHAT) {
            PrivateChatStore.setActive(activePrivateTarget());
            loadDraft(activePrivateTarget());
        } else if (topPage() == AppPage.WORLD_CHAT) {
            worldScroll.reset();
            loadWorldDraft();
        }
    }

    private boolean pageNavActive() {
        return pageNavFrom != null && pageNavTo != null && !pageNavAnim.isDone();
    }

    private void startPageNav(NavPage from, NavPage to, boolean popPending) {
        saveCurrentDraft();
        pageNavFrom = from;
        pageNavTo = to;
        pageNavPopPending = popPending;
        pageNavAnim.setValue(0.0F);
        pageNavAnim.animateTo(Animations.ms(UiMotion.PAGE_NAV_MS), 1.0F);
        // Pushes enter with the scale+fade; pops keep the slide-out.
        if (!popPending) {
            startPageEnter();
        }
    }

    private void finishPageNav() {
        if (pageNavPopPending) {
            popNow();
        }
        pageNavFrom = null;
        pageNavTo = null;
        pageNavPopPending = false;
        pageNavAnim.setValue(0.0F);
    }

    /**
     * Arms the Melodify-style enter for a push: the page starts at 0.94 scale
     * on a bounce spring and fades in over ~120ms. With decorative motion off
     * both channels snap, so the switch lands immediately.
     */
    private void startPageEnter() {
        if (Animations.enabled()) {
            pageNavScale.snapTo(PAGE_ENTER_SCALE_FROM);
            pageNavScale.setTarget(1.0F);
            pageNavStartMs = System.currentTimeMillis();
        } else {
            pageNavScale.snapTo(1.0F);
            pageNavStartMs = System.currentTimeMillis() - PAGE_ENTER_FADE_MS;
        }
    }

    private void updatePageEnter(float frameDt) {
        pageNavScale.update(frameDt, Animations.enabled());
    }

    /** Enter fade: 0 -> 1 over {@link #PAGE_ENTER_FADE_MS}, ease-out. */
    private float pageEnterAlpha() {
        if (!Animations.enabled()) {
            return 1.0F;
        }
        float t = Math.min(1.0F, (System.currentTimeMillis() - pageNavStartMs)
                / (float) PAGE_ENTER_FADE_MS);
        return Easing.easeOutQuad(t);
    }

    /**
     * True once the enter transform has nothing left to show: the scale is
     * back at 1 and the fade is complete. A fully-opaque full-panel
     * saveLayer behind an identity transform is pure overdraw, so the enter
     * branch skips the layer entirely in that state (motion off lands here
     * from frame one; with motion on, every frame after the ~120ms fade).
     */
    private boolean pageEnterDone() {
        return Math.abs(pageNavScale.value() - 1.0F) < 1e-3F && pageEnterAlpha() >= 0.999F;
    }

    /** Centers the enter scale around the panel middle. Draw-only: hit-tests
     *  keep unscaled coordinates, like every other transform in this screen. */
    private void applyEnterScale(Canvas canvas, UiLayout.Rect panelRect) {
        float scale = pageNavScale.value();
        if (scale == 1.0F) {
            return;
        }
        float cx = panelRect.x() + panelRect.w() / 2.0F;
        float cy = panelRect.y() + panelRect.h() / 2.0F;
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);
        canvas.translate(-cx, -cy);
    }

    /** Clears the transient state of a settings sub-page when leaving it. */
    private void resetSettingsUi() {
        detailScroll.reset();
        settingsSectionPage.reset();
        chipScroll.clear();
        chipScrollKey = "";
    }

    /** Saves the outgoing chip's scroll offset and restores the incoming one,
     *  so each chip of a section page remembers where the user left it. */
    private void syncChipScroll(SettingsSection section) {
        String key = section.id() + ':' + settingsSectionPage.activeChipIndex(section);
        if (!key.equals(chipScrollKey)) {
            if (!chipScrollKey.isEmpty()) {
                chipScroll.put(chipScrollKey, detailScroll.getScrollY());
            }
            chipScrollKey = key;
            Float saved = chipScroll.get(key);
            detailScroll.scrollTo(saved == null ? 0.0F : saved, false);
        }
    }

    private float pageNavDx(float travel) {
        float progress = pageNavAnim.getValue();
        // Push: the incoming page enters from the right (travel -> 0).
        // Pop: the outgoing page leaves to the right (0 -> travel).
        // The old code decided by pageNavTo.isRoot(), which broke detail<->detail
        // pops (the destination WORLD_CHAT is also non-root).
        return pageNavPopPending ? travel * progress : travel * (1.0F - progress);
    }

    /** Opens another player's profile as a pushed detail page. */
    public void openProfileDetail(PlayerRef player) {
        if (player == null || player.realName() == null) {
            return;
        }
        profilePage.setSubject(player);
        pushPage(AppPage.PROFILE_DETAIL);
    }

    /** Opens the profile detail for a message's sender (avatar single click). */
    private void openProfileFor(ChatMessage msg) {
        if (msg == null || msg.isSystem() || msg.isOwn()) {
            return;
        }
        PlayerRef player = PlayerRef.of(msg.getSenderUuid(), msg.getProfileName());
        if (player == null || player.realName() == null) {
            return;
        }
        openProfileDetail(player);
    }

    @Override
    public void switchRoot(AppPage root) {
        if (!root.isRoot()) {
            return;
        }
        AppPage from = topPage();
        // Leaving the profile root falls back to the local player's page; the
        // injected subject (open-profile-for-other-player) is transient.
        if (from == AppPage.PROFILE && root != AppPage.PROFILE) {
            profilePage.resetSubject();
        }
        saveCurrentDraft();
        rootScroll.reset();
        // With decorative motion off the tab change is instant: no indicator
        // slide, no body push — the new root is simply on screen.
        if (!Animations.enabled()) {
            clearRootTransition();
            bottomTabBar.setSelectedImmediate(rootIndex(root));
            navigation.replaceWithRoot(NavPage.of(root));
            ChatStore.setPublicActive(false);
            return;
        }
        // If a previous root transition is still running, snap the shared
        // indicator to the current page before starting a new slide.
        if (!rootTabAnim.isDone() && from.isRoot()) {
            rootTabAnim.setValue(rootIndex(from));
        }
        navigation.replaceWithRoot(NavPage.of(root));
        ChatStore.setPublicActive(false);
        if (from.isRoot() && from != root) {
            rootTabFrom = rootIndex(from);
            rootTabTo = rootIndex(root);
            bottomTabBar.setSelectedIndex(rootTabTo);
        } else {
            clearRootTransition();
            bottomTabBar.setSelectedImmediate(rootIndex(root));
        }
    }

    private ScrollController currentScroll() {
        if (topPage() == AppPage.PRIVATE_CHAT) {
            PlayerRef target = activePrivateTarget();
            if (target == null) {
                return worldScroll;
            }
            return privateScrolls.computeIfAbsent(target.key(), k -> new ScrollController());
        }
        return worldScroll;
    }

    private List<ChatMessage> currentMessages() {
        if (topPage() == AppPage.WORLD_CHAT) {
            return ChatStore.get().snapshot();
        }
        if (topPage() == AppPage.PRIVATE_CHAT) {
            return PrivateChatStore.messages(activePrivateTarget());
        }
        return List.of();
    }

    private List<ChatMessage> messagesForNav(NavPage page) {
        if (page == null) {
            return List.of();
        }
        if (page.page() == AppPage.WORLD_CHAT) {
            List<ChatMessage> all = ChatStore.get().snapshot();
            if (worldFilter == MessageFilter.ALL) {
                return all;
            }
            // View filter only: the store, unread counts and previews always
            // see the full feed.
            List<ChatMessage> out = new ArrayList<>(all.size());
            for (ChatMessage message : all) {
                if (worldFilter.accepts(message)) {
                    out.add(message);
                }
            }
            return out;
        }
        if (page.page() == AppPage.PRIVATE_CHAT) {
            return PrivateChatStore.messages(page.target());
        }
        return List.of();
    }

    private ScrollController scrollForNav(NavPage page) {
        if (page != null && page.page() == AppPage.PRIVATE_CHAT && page.target() != null) {
            return privateScrolls.computeIfAbsent(page.target().key(), k -> new ScrollController());
        }
        return worldScroll;
    }

    private void drawMessageLayerForNav(Canvas canvas, UiLayout layout, NavPage page, float dx) {
        if (page == null) {
            return;
        }
        UiLayout.Rect list = layout.list;
        canvas.save();
        try {
            SkiaDraw.clip(canvas, list.x(), list.y(), list.w(), list.h(), 0.0F);
            canvas.translate(dx, 0.0F);
            messageListView.draw(canvas, list.x(), list.y(), list.w(), list.h(),
                    messagesForNav(page), scrollForNav(page));
        } finally {
            canvas.restore();
        }
    }

    private String currentPrivateKey() {
        PlayerRef target = activePrivateTarget();
        return target != null ? target.key() : null;
    }

    /** Persists the hidden EditBox draft to the page that is currently open. */
    private void saveCurrentDraft() {
        if (input == null) {
            return;
        }
        if (topPage() == AppPage.WORLD_CHAT) {
            worldDraft = input.getValue();
        } else if (topPage() == AppPage.PRIVATE_CHAT) {
            PlayerRef target = activePrivateTarget();
            if (target != null) {
                privateDrafts.put(target.key(), input.getValue());
            }
        }
    }

    private void savePrivateDraft(PlayerRef target, String text) {
        if (target != null) {
            privateDrafts.put(target.key(), text);
        }
    }

    private void loadDraft(PlayerRef target) {
        if (target == null) {
            return;
        }
        String draft = privateDrafts.getOrDefault(target.key(), "");
        if (input != null) {
            input.setValue(draft);
            input.moveCursorToStart(false);
        }
    }

    private void loadWorldDraft() {
        if (input != null) {
            input.setValue(worldDraft);
            input.moveCursorToStart(false);
        }
    }

    /**
     * Clears world-chat-only ephemeral UI before navigating back to a root page,
     * so it cannot reappear when the world page is opened again. The chat draft
     * is preserved, but the message-list scroll state is reset so reopening the
     * channel starts fresh at the bottom.
     */
    private void resetTransientWorldUi() {
        worldScroll.reset();
        closeContextMenu();
        lastContextMessage = null;
        lastContextPlayer = null;
        lastContextMenuMode = ContextMenuMode.BUBBLE;
        contextAnim = 0.0F;
        for (int i = 0; i < contextMenuHover.length; i++) {
            contextMenuHover[i] = 0.0F;
        }
        emojiPanel.resetTransient();
        endPhraseEdit(false);
        quickPhrasePanel.resetTransient();
        replyTarget = null;
        messageListView.clearSelection();
        pendingClickSpan = null;
        pendingClickMoved = false;
        dismissSuggestor();
    }

    public String getOriginalChatText() {
        return originalChatText;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Spring-driven panel progress. The dt clamp (<=50ms) lives inside the
        // spring; with decorative motion off update() snaps to the target, so
        // the disabled path and the close completion both go through isSettled().
        long nowNanos = System.nanoTime();
        float frameDtMs = Math.max(0.0F, Math.min(50.0F, (nowNanos - lastFrameNanos) / 1_000_000.0F));
        lastFrameNanos = nowNanos;
        panelSpring.setTarget(closing ? 0.0F : 1.0F);
        panelSpring.update(frameDtMs, Animations.enabled());
        panelProgress = panelSpring.value();
        if (closing && panelSpring.isSettled()) {
            this.client.setScreen(null);
            return;
        }
        // Feed the blur cadence gate: camera or panel-chrome motion keeps the
        // every-2-frames blur refresh; a still scene drops to the slow fallback
        // (see BlurMotionGate). The pre-pass below asks the gate per frame.
        boolean chromeAnimating = !panelSpring.isSettled()
                || Math.abs(inputExtraH - lastBlurInputExtraH) > 0.01F;
        BlurMotionGate.noteFrame(System.currentTimeMillis(),
                client.player != null ? client.player.getYRot() : 0.0F,
                client.player != null ? client.player.getXRot() : 0.0F,
                chromeAnimating);
        lastBlurInputExtraH = inputExtraH;

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
                    float strokeWidth = s(3);
                    float slide = (panelProgress - 1.0F) * OPEN_SLIDE_PX;
                    float vx = panelX() + strokeWidth + slide;
                    float vy = panelY() + strokeWidth;
                    float vw = panelWidth() - strokeWidth * 2.0F;
                    float vh = panelHeight() - strokeWidth * 2.0F;
                    float vRadius = UiTokens.panelRadius() - strokeWidth;
                    double density = uiDensity();
                    double scaleFactor = this.client.getWindow().getGuiScale();
                    float gx = (float) (vx * density / scaleFactor);
                    float gy = (float) (vy * density / scaleFactor);
                    float gw = (float) (vw * density / scaleFactor);
                    float gh = (float) (vh * density / scaleFactor);
                    float gr = (float) (vRadius * density / scaleFactor);
                    blurDrawnThisFrame = PanelBlurRenderer.render(
                            context.pose().last().pose(),
                            gx, gy, gw, gh, gr, panelProgress);
                } catch (Throwable t) {
                    // One line, one stack trace, then the breaker: isAvailable()
                    // goes false, so the pre-pass is not even built on the next
                    // frame. Before this, a deterministic failure logged a full
                    // stack trace every frame for as long as the panel stayed
                    // open, which is how a real one-line cause turns into an
                    // unreadable log.
                    AtomChat.LOGGER.warn("AtomChat panel blur pre-pass failed, using solid background", t);
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
        // show the composer, so none of this may run outside WORLD_CHAT.
        if (!closing && input != null && isWorldChatPage()) {
            positionInputField(layout());
            if (chatInputSuggestor != null) {
                chatInputSuggestor.render(context, mouseX, mouseY);
            }
        }

        // Hover tooltips are drawn through the vanilla pipeline after the Skia
        // panel and the suggestion popup so they stay readable on top of both.
        // Root pages keep their own hover model; stale world spans must not show.
        ClickableSpan hoveredSpan = isWorldChatPage()
                ? messageListView.clickableSpanAt(toVirtualX(mouseX), toVirtualY(mouseY)).orElse(null)
                : null;
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
    private void syncScrollMotion() {
        boolean on = Animations.enabled();
        worldScroll.setDecorativeMotion(on);
        rootScroll.setDecorativeMotion(on);
        for (ScrollController controller : privateScrolls.values()) {
            controller.setDecorativeMotion(on);
        }
    }

    /** Collapses the suggestion popup and clears the gray ghost suffix. */
    private void dismissSuggestor() {
        if (chatInputSuggestor != null) {
            chatInputSuggestor.hide();
            input.setSuggestion(null);
        }
    }

    /** GUI-space anchor for the suggestion window: bottom edge of the popup. */
    private int anchorInputTopY() {
        double density = uiDensity();
        double scaleFactor = this.client.getWindow().getGuiScale();
        // The popup must clear the whole input bar (button row + text row),
        // not just the caret line; otherwise it overlaps the bar's top half.
        return (int) Math.round((layout().inputBar.y() - s(4)) * density / scaleFactor);
    }

    private int anchorInputLeftX() {
        double density = uiDensity();
        double scaleFactor = this.client.getWindow().getGuiScale();
        return (int) Math.round((layout().inputBar.x() + UiTokens.INPUT_TEXT_X) * density / scaleFactor);
    }

    private void positionInputField(UiLayout layout) {
        double density = uiDensity();
        double scaleFactor = this.client.getWindow().getGuiScale();
        // The hidden EditBox is what anchors the native IME composition window.
        // EditBox computes its screen caret as fieldX + vanilla-font prefix
        // width, while AtomChat draws the committed text with Skia. Shift the
        // field's X so EditBox.getScreenX(caret) lands exactly on the Skia caret
        // (no visible gap before the IME pre-edit box).
        String current = inputGetText();
        int caret = caretIndex();
        String wholePrefix = current.substring(0, Math.min(caret, current.length()));
        String linePrefix = inputLinePrefix(layout, caret);
        Font inputFont = FontManager.font(UiTokens.FONT_INPUT);
        float skiaLinePrefixVirtual = SkiaFontRenderer.getStringWidth(inputFont, linePrefix);
        int desiredGuiX = (int) Math.round((layout.inputBar.x() + UiTokens.INPUT_TEXT_X + skiaLinePrefixVirtual)
                * density / scaleFactor);
        int vanillaWholePrefixGuiWidth = this.client.font.width(wholePrefix);
        input.setX(desiredGuiX - vanillaWholePrefixGuiWidth);
        // IMBlocker bridge: English IME while a command is being typed
        // (e33chat parity). Change-guarded — the reflection call only fires
        // when the command/native state actually flips.
        boolean commandMode = current.startsWith("/");
        if (commandMode != imeEnglishState) {
            imeEnglishState = commandMode;
            com.atom.chat.compat.IMBlockerCompat.setCommandMode(input, commandMode);
        }
        input.setY((int) Math.round(caretLineTopY() * density / scaleFactor));
        input.setWidth((int) Math.max(10.0F, Math.round((layout.inputBar.w() - UiTokens.INPUT_TEXT_X * 2.0F) * density / scaleFactor)));
        input.setHeight((int) Math.round(inputLineHeight() * density / scaleFactor));
    }

    /**
     * Maps a virtual point inside the input text area to a text index. Rows
     * are the Skia-wrapped lines; a point left of a character's midpoint
     * selects that character, past the end selects the line end.
     */
    private int inputCaretIndexAt(UiLayout layout, float vmx, float vmy) {
        List<String> lines = wrappedInput(layout.inputTextMaxWidth());
        if (lines.isEmpty()) {
            return 0;
        }
        int total = lines.size();
        int shown = Math.min(UiTokens.INPUT_MAX_LINES, total);
        int from = Math.min(inputScrollLine, Math.max(0, total - shown));
        float lineH = inputLineHeight();
        float firstTop = layout.inputTextCenterY - lineH / 2.0F;
        int row = Mth.clamp((int) Math.floor((vmy - firstTop) / lineH), 0, shown - 1);
        int line = Math.min(from + row, total - 1);
        int lineStart = 0;
        for (int i = 0; i < line; i++) {
            lineStart += lines.get(i).length();
        }
        String s = lines.get(line);
        float dx = vmx - (layout.inputBar.x() + UiTokens.INPUT_TEXT_X);
        Font inputFont = FontManager.font(UiTokens.FONT_INPUT);
        float acc = 0.0F;
        // Code-point steps: a per-char loop returns indices inside surrogate
        // pairs, and typing afterwards would split an emoji in half.
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            int chars = Character.charCount(cp);
            float cw = SkiaFontRenderer.getStringWidth(inputFont, s.substring(i, i + chars));
            if (dx < acc + cw / 2.0F) {
                return lineStart + i;
            }
            acc += cw;
            i += chars;
        }
        return lineStart + s.length();
    }

    /** True when the private-chat partner is composing a message (WATUT). */
    private boolean partnerTyping() {
        if (topPage() != AppPage.PRIVATE_CHAT || isPrivateReadOnly()) {
            return false;
        }
        PlayerRef partner = activePrivateTarget();
        return partner != null
                && com.atom.chat.watut.WatutBridge.isTyping(partner.uuid());
    }

    /** Skia width of the committed text before the caret on the caret's wrapped line. */
    private String inputLinePrefix(UiLayout layout, int caret) {
        List<String> lines = wrappedInput(layout.inputTextMaxWidth());
        if (lines.isEmpty()) {
            return "";
        }
        int line = caretLine(lines, caret);
        int lineStart = 0;
        for (int i = 0; i < line; i++) {
            lineStart += lines.get(i).length();
        }
        int col = Mth.clamp(caret - lineStart, 0, lines.get(line).length());
        return lines.get(line).substring(0, col);
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // World stays fully visible, same as vanilla chat; the panel provides its own background.
    }

    @Override
    protected void init() {
        super.init();
        // Swap the vanilla suggestor for our anchored one on the same chat field;
        // ChatScreen's changed listener drives whatever sits in chatInputSuggestor.
        this.chatInputSuggestor = new AtomChatSuggestor(this.client, this, this.input,
                this.client.font, false, false, 1, 10, true, -805306368,
                this::anchorInputTopY, this::anchorInputLeftX);
        this.chatInputSuggestor.setAllowHiding(false);
        this.chatInputSuggestor.hide();
        // Drive our anchored suggestor from the same EditBox responder vanilla
        // ChatScreen would use for its package-private commandSuggestions.
        this.input.setResponder(text -> {
            if (this.chatInputSuggestor != null) {
                this.chatInputSuggestor.setAllowSuggestions(!text.equals(originalChatText));
                this.chatInputSuggestor.updateCommandInfo();
            }
        });
        // init() also runs on every resize, hence the guard inside.
        installDropCallback();
    }

    /** Composer text saved while the quick-phrase panel borrows the field. */
    private String phraseSavedComposer = "";

    private String inputGetText() {
        return input != null ? input.getValue() : "";
    }

    private void inputSetText(String text) {
        if (input != null) {
            input.setValue(text == null ? "" : text);
        }
    }

    private void inputAppend(String text) {
        if (input == null) {
            return;
        }
        String current = input.getValue();
        if (current.length() + text.length() <= 256) {
            input.setValue(current + text);
        }
        inputFocused = true;
        setFocused(input);
        input.setFocused(true);
    }

    // ---- quick-phrase edit borrows the composer field (the IME carrier) ----

    /** Begins adding ({@link QuickPhrasePanel#ADD_NEW}) or editing a phrase. */
    private void beginPhraseEdit(int target) {
        if (isPrivateReadOnly()) {
            return;
        }
        if (quickPhrasePanel.isEditing()) {
            endPhraseEdit(true);
        }
        phraseSavedComposer = inputGetText();
        if (target == QuickPhrasePanel.ADD_NEW) {
            inputSetText("");
            quickPhrasePanel.startAdd();
        } else {
            inputSetText(quickPhrasePanel.phraseAt(target));
            quickPhrasePanel.startEdit(target);
        }
        inputFocused = true;
        setFocused(input);
        input.setFocused(true);
        setCaretAtEnd();
    }

    /** Ends a phrase add/edit; commit=true saves the field text as the phrase. */
    private void endPhraseEdit(boolean commit) {
        if (!quickPhrasePanel.isEditing()) {
            return;
        }
        if (commit) {
            quickPhrasePanel.commitText(inputGetText());
        } else {
            quickPhrasePanel.abortEdit();
        }
        inputSetText(phraseSavedComposer);
        setCaretAtEnd();
    }

    /** Closes the phrase panel; an in-progress edit commits (blur applies). */
    private void closePhrasePanel() {
        if (quickPhrasePanel.isEditing()) {
            endPhraseEdit(true);
        }
        quickPhrasePanel.close();
    }

    private void setCaretAtEnd() {
        if (input != null) {
            input.moveCursorTo(input.getValue().length(), false);
        }
    }

    /** Turns a panel click into the screen-level side effects. */
    private void handlePhraseAction(QuickPhrasePanel.Action action) {
        switch (action.type()) {
            case QuickPhrasePanel.Action.ADD -> beginPhraseEdit(QuickPhrasePanel.ADD_NEW);
            case QuickPhrasePanel.Action.EDIT -> beginPhraseEdit(action.index());
            case QuickPhrasePanel.Action.DELETE -> quickPhrasePanel.deleteAt(action.index());
            case QuickPhrasePanel.Action.INSERT -> {
                endPhraseEdit(true);
                quickPhrasePanel.pickRow(action.index());
            }
            case QuickPhrasePanel.Action.INSERT_SERVER -> {
                // The server's rows are read-only: they insert text and close,
                // with no edit or delete path behind them.
                endPhraseEdit(true);
                java.util.List<String> serverPhrases =
                        com.atom.chat.pack.ServerPackStore.current().phrases();
                if (action.index() >= 0 && action.index() < serverPhrases.size()) {
                    inputAppend(serverPhrases.get(action.index()));
                }
                quickPhrasePanel.close();
            }
            default -> {
            }
        }
    }

    /**
     * Notification banners, painted after every page body so they sit on top
     * regardless of which page is showing (the settings and profile branches
     * return early from drawPanel). They live inside the panel layer, so they
     * fade and slide with the panel instead of floating outside it.
     */
    private static int leakProbeCount;

    private static void probeCanvasLeak(String where, int before, Canvas canvas) {
        int leaked = canvas.getSaveCount() - before;
        if (leaked == 0 || !AtomChatConfig.get().debug) {
            return;
        }
        leakProbeCount++;
        if (leakProbeCount == 1 || leakProbeCount % 200 == 0) {
            AtomChat.LOGGER.warn("Leak probe: {} leaked {} stack entries (occurrence {})",
                    where, leaked, leakProbeCount);
        }
    }

    private void drawNotificationBanners(Canvas canvas, int mouseX, int mouseY) {
        // Modals own the top layer; a banner painted over them would be visible
        // but unclickable (ModalInput swallows the click), so hide it until the
        // modal closes. The 4s queue lifetime still lets it reappear.
        if (imageCropper.isActive() || colorPicker.isActive()) {
            return;
        }
        if (!NotificationBanner.INSTANCE.hasActive()) {
            return;
        }
        UiLayout.Rect panel = layout().rect();
        float vmx = toVirtualX(mouseX);
        float vmy = toVirtualY(mouseY);
        int probeBefore = canvas.getSaveCount();
        NotificationBanner.INSTANCE.renderInPanel(canvas, panel.x(), panel.y(), panel.w(), panel.h(),
                vmx, vmy);
        probeCanvasLeak("banner render", probeBefore, canvas);
    }

    /**
     * Banner clicks jump to the message behind the notification. Registered
     * after the modals, so a cropper or colour picker still swallows the click
     * while it is open.
     */
    private final class BannerInput implements InputHandler {
        @Override
        public boolean onClick(double mouseX, double mouseY, int button) {
            if (button != 0 || !NotificationBanner.INSTANCE.hasActive()) {
                return false;
            }
            float vmx = toVirtualX(mouseX);
            float vmy = toVirtualY(mouseY);
            // The send-style round button means "reply now": same jump plus
            // composer focus. The rest of the banner just navigates.
            NotificationBanner.Active sendButton = NotificationBanner.INSTANCE.buttonHit(vmx, vmy);
            if (sendButton != null) {
                NotificationBanner.INSTANCE.dismiss(sendButton);
                jumpToNotification(sendButton);
                focusComposerForReply();
                return true;
            }
            NotificationBanner.Active hit = NotificationBanner.INSTANCE.hitTest(vmx, vmy);
            if (hit == null) {
                return false;
            }
            NotificationBanner.INSTANCE.dismiss(hit);
            jumpToNotification(hit);
            return true;
        }
    }

    /** Composer focus for the banner send-button: one click from banner to typing. */
    private void focusComposerForReply() {
        if (input != null && !isPrivateReadOnly()) {
            inputFocused = true;
            setFocused(input);
            input.setFocused(true);
        }
    }

    /** Opens whatever page holds this notification and scrolls the message in. */
    private void jumpToNotification(NotificationBanner.Active banner) {
        ChatMessage msg = banner.message();
        if (msg == null) {
            return;
        }
        if (banner.type() == NotificationBanner.Type.WHISPER) {
            PlayerRef target = PlayerRef.of(msg.getSenderUuid(), msg.getProfileName());
            if (target != null && !target.equals(activePrivateTarget())) {
                openPrivateChat(target);
            }
        } else if (topPage() != AppPage.WORLD_CHAT) {
            openWorldChat();
        }
        scrollToMessage(msg);
    }

    /**
     * Scrolls the current page's list so the message sits a third of the way
     * down the viewport, then leaves a fading highlight on it. Anti-spam merges
     * mean the target can be a merged bubble — that one is still the right
     * destination, so merged messages are never un-merged here.
     */
    private void scrollToMessage(ChatMessage msg) {
        NavPage page = topNav();
        List<ChatMessage> messages = messagesForNav(page);
        int index = findMessageIndex(messages, msg);
        if (index < 0) {
            return;
        }
        ScrollController scroll = scrollForNav(page);
        UiLayout.Rect list = layout().list;
        float offset = messageListView.offsetOf(messages, index, list.w());
        // The page may have just been opened (fresh controller, maxScroll still
        // zero). Feed it the real content height before the jump or the clamp
        // inside scrollTo collapses the target to 0.
        scroll.setContent(messageListView.offsetOf(messages, messages.size(), list.w()), list.h());
        scroll.scrollTo(offset - list.h() / 3.0F, true);
        messageListView.highlight(messages.get(index));
    }

    /**
     * Resolves the banner's message to its current list slot. Anti-spam can
     * replace the original object with a merged {@code xN} copy after the banner
     * was queued, so fall back to the last same-content/sender row instead of
     * giving up on an identity miss.
     */
    private static int findMessageIndex(List<ChatMessage> messages, ChatMessage target) {
        int exact = messages.indexOf(target);
        if (exact >= 0) {
            return exact;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage candidate = messages.get(i);
            if (candidate.isOwn() != target.isOwn()
                    || candidate.isSystem() != target.isSystem()
                    || !java.util.Objects.equals(candidate.getQuoteName(), target.getQuoteName())
                    || !java.util.Objects.equals(candidate.getQuoteText(), target.getQuoteText())
                    || !java.util.Objects.equals(candidate.getContentText(), target.getContentText())) {
                continue;
            }
            UUID a = candidate.getSenderUuid();
            UUID b = target.getSenderUuid();
            boolean sameSender = a != null && b != null
                    ? a.equals(b)
                    : java.util.Objects.equals(candidate.getProfileName(), target.getProfileName());
            if (sameSender) {
                return i;
            }
        }
        return -1;
    }

    private void drawPhone(Canvas canvas, Image worldSnapshot, int mouseX, int mouseY, float delta) {
        float x = panelX();
        float y = panelY();
        float progress = panelProgress;
        // Same offset the blur pre-pass offsets its capture rect by (render()).
        float slide = (progress - 1.0F) * OPEN_SLIDE_PX;
        canvas.save();
        try (Paint layer = new Paint()) {
            // Spring overshoot pushes progress past [0,1]; the fade alpha must clamp.
            float fade = Math.max(0.0F, Math.min(1.0F, progress));
            layer.setColor(Color.makeARGB((int) (255.0F * fade), 0, 0, 0));
            // The layer must cover the panel at BOTH ends of the slide, otherwise
            // the fade layer clips the bezel while the panel is still OPEN_SLIDE_PX
            // to the left of its resting place.
            float layerX = Math.min(x, x + slide) - LAYER_CHROME;
            float layerW = panelWidth() + LAYER_CHROME * 2.0F + Math.abs(slide);
            canvas.saveLayer(Rect.makeXYWH(layerX, y - LAYER_CHROME, layerW, panelHeight() + LAYER_CHROME * 2.0F), layer);
            canvas.translate(slide, 0.0F);
            // The world snapshot sits inside the saveLayer/translate stack so it
            // fades in with the panel and slides with it — no special handling.
            int probeBefore = canvas.getSaveCount();
            drawPanel(canvas, x, y, worldSnapshot, mouseX, mouseY, delta);
            drawNotificationBanners(canvas, mouseX, mouseY);
            probeCanvasLeak("drawPanel+banners", probeBefore, canvas);
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
     */
    private void installDropCallback() {
        if (dropCallback != null) {
            return;
        }
        try {
            dropCallback = GLFW.glfwSetDropCallback(this.client.getWindow().getWindow(),
                    (win, count, names) -> onFilesDropped(count, names));
        } catch (Throwable t) {
            AtomChat.LOGGER.warn("Failed to install the file drop callback", t);
        }
    }

    private void uninstallDropCallback() {
        if (dropCallback == null) {
            return;
        }
        try {
            GLFW.glfwSetDropCallback(this.client.getWindow().getWindow(), null);
            dropCallback.free();
        } catch (Throwable t) {
            AtomChat.LOGGER.warn("Failed to remove the file drop callback", t);
        }
        dropCallback = null;
    }

    /** Window-wide: the drop event carries no cursor position we could hit-test with. */
    private void onFilesDropped(int count, long names) {
        // File drops only feed the world-chat composer; root pages must ignore them.
        if (!isWorldChatPage()) {
            return;
        }
        try {
            PointerBuffer buffer = MemoryUtil.memPointerBuffer(names, count);
            for (int i = 0; i < count; i++) {
                Path file = Path.of(MemoryUtil.memUTF8(buffer.get(i)));
                if (ImageFiles.isImage(file)) {
                    uploadAndAppend(file);
                    return;
                }
            }
            if (count > 0) {
                AtomChat.LOGGER.info("Ignored {} dropped file(s): none was an image", count);
            }
        } catch (Throwable t) {
            AtomChat.LOGGER.warn("Failed to handle dropped files", t);
        }
    }

    @Override
    public void removed() {
        saveCurrentDraft();
        if (topPage() == AppPage.PRIVATE_CHAT) {
            PrivateChatStore.clearActive();
        }
        ChatStore.setPublicActive(false);
        AtomChatState.save(navigation.snapshot());
        uninstallDropCallback();
        // Give back the GPU texture the panel blur was sampling.
        graphics.releaseWorldSnapshot();
        messageListView.dispose();
        super.removed();
    }

    private void drawPanel(Canvas canvas, float x, float y, Image worldSnapshot, int mouseX, int mouseY, float delta) {
        inputFocused = input != null && input.isFocused();
        if (isPrivateReadOnly() && input != null) {
            input.setFocused(false);
            inputFocused = false;
        }
        long nowMs = System.currentTimeMillis();
        frameDt = Math.min(50L, Math.max(1L, nowMs - lastFrameMs));
        lastFrameMs = nowMs;
        // Single-click→profile competition window: fire when no second click
        // arrived within the threshold.
        if (pendingAvatarClickMessage != null) {
            if (closing) {
                pendingAvatarClickMessage = null;
            } else if (nowMs - pendingAvatarClickTime >= AVATAR_CLICK_WINDOW_MS) {
                ChatMessage pending = pendingAvatarClickMessage;
                pendingAvatarClickMessage = null;
                pendingAvatarClickIndex = -1;
                openProfileFor(pending);
            }
        }
        syncScrollMotion();
        emojiPanel.update(frameDt);
        quickPhrasePanel.update(frameDt);
        UiLayout layout = layout();
        UiLayout.Rect panel = layout.rect();
        // Phone bezel: background is inset by the full stroke width so nothing can
        // bleed outside; the white ring itself is drawn LAST (see end of method)
        // so every component sits beneath a clean edge.
        float strokeWidth = s(3);
        // A custom wallpaper owns the panel background and beats the blur: one
        // shows the world through, the other covers it, so they never stack.
        Image wallpaper = WallpaperImage.current(WallpaperStore.current());
        float innerX = panel.x() + strokeWidth;
        float innerY = panel.y() + strokeWidth;
        float innerW = panel.w() - strokeWidth * 2.0F;
        float innerH = panel.h() - strokeWidth * 2.0F;
        float innerRadius = UiTokens.panelRadius() - strokeWidth;

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

        // One unified shell header is drawn before page content on every page.
        // Root pages add their body and the bottom tab bar; pushed pages run
        // through the navigation machinery below.
        if (topPage().isRoot()) {
            drawRootScreen(canvas, mouseX, mouseY, topPage());
            drawContextMenu(canvas, toVirtualX(mouseX), toVirtualY(mouseY));
            drawBezel(canvas, layout);
            return;
        }

        float vmx = toVirtualX(mouseX);
        float vmy = toVirtualY(mouseY);
        boolean navRunning = pageNavActive();
        if (navRunning) {
            pageNavAnim.update(frameDt);
            if (pageNavAnim.isDone()) {
                finishPageNav();
                if (topPage().isRoot()) {
                    drawRootScreen(canvas, mouseX, mouseY, topPage());
                    drawBezel(canvas, layout);
                    return;
                }
                // Push finished (or a detail-to-detail settle): the current
                // detail page is now the settled top page.
                navRunning = false;
            } else if (pageNavFrom != null && pageNavTo != null
                    && !pageNavFrom.isRoot() && !pageNavTo.isRoot()) {
                // Detail-to-detail (public <-> private) full-width push/pop.
                // The message lists slide; the header/input chrome is drawn
                // fixed once so the page change reads as a phone push.
                boolean popping = pageNavPopPending;
                NavPage fromPage = pageNavFrom;
                NavPage toPage = pageNavTo;
                float travel = layout.list.w();
                float progress = pageNavAnim.getValue();
                float fromDx = popping ? travel * progress : -travel * progress;
                float toDx = popping ? -travel * (1.0F - progress) : travel * (1.0F - progress);
                suppressHeader = true;
                if (fromPage.page() == AppPage.PROFILE_DETAIL
                        || toPage.page() == AppPage.PROFILE_DETAIL) {
                    // Chat <-> profile detail: full-width page push. The whole
                    // chat body (messages, reply bar, composer) slides as one
                    // piece under the incoming profile page — the lists-only
                    // slide below reads wrong when the two sides have different
                    // chrome (the detail page has no composer at all). The base
                    // page is whichever side is the chat: topPage() is already
                    // the destination on a push and still the detail on a pop,
                    // so messages must be resolved per page, not off the stack.
                    NavPage chatPage = fromPage.page() == AppPage.PROFILE_DETAIL ? toPage : fromPage;
                    float baseDx = popping ? -travel * (1.0F - progress) : -travel * progress;
                    UiLayout.Rect panelRect = layout.rect();
                    canvas.save();
                    SkiaDraw.clip(canvas, panelRect.x(), panelRect.y(), panelRect.w(), panelRect.h(), 0.0F);
                    canvas.translate(baseDx, 0.0F);
                    layout = updateInputLayout(layout);
                    drawChatPageBody(canvas, layout, mouseX, mouseY, chatPage);
                    canvas.restore();

                    canvas.save();
                    SkiaDraw.clip(canvas, panelRect.x(), panelRect.y(), panelRect.w(), panelRect.h(), 0.0F);
                    canvas.translate(pageNavDx(travel), 0.0F);
                    drawProfileDetail(canvas, mouseX, mouseY);
                    canvas.restore();
                } else {
                    // World <-> private: identical chrome on both sides, so the
                    // lists slide under a fixed header and composer.
                    drawMessageLayerForNav(canvas, layout, fromPage, fromDx);
                    drawMessageLayerForNav(canvas, layout, toPage, toDx);
                    UiLayout.Rect bar = layout.inputBar;
                    SkiaDraw.drawRoundedShadow(canvas, bar.x(), bar.y(), bar.w(), bar.h(),
                            UiTokens.radius(18), s(8), UiTokens.CHROME_SHADOW);
                    SkiaDraw.drawRoundedRect(canvas, bar.x(), bar.y(), bar.w(), bar.h(), UiTokens.radius(18),
                            UiTokens.cardFill());
                }
                suppressHeader = false;
                ShellHeader.render(canvas, layout.header, shellTitleFor(toPage), true,
                        backButton(), backButtonHover, textPrimary(),
                        toPage.page() == AppPage.PRIVATE_CHAT
                                ? isOnlinePlayer(toPage.target()) : null,
                        headerActionFor(toPage));
                drawBezel(canvas, layout);
                return;
            } else {
                // Full-width push/pop: root<->detail transitions. The header is
                // drawn separately as a fixed "status bar" after both layers.
                suppressHeader = true;
                NavPage navRoot = pageNavTo.isRoot() ? pageNavTo : pageNavFrom;
                NavPage moving = pageNavTo.isRoot() ? pageNavFrom : pageNavTo;
                float travel = layout.rect().w();
                float progress = pageNavAnim.getValue();
                boolean pushing = !pageNavTo.isRoot();
                // Melodify enter applies to the pages that own their whole
                // drawing lifecycle (settings section/category, profile); a
                // chat push keeps the paired slide into the shared tail. Pops
                // never enter: pageNavTo is then the destination (e.g. the
                // category page under a sliding-out section), and the stale
                // pageNavStartMs would hard-cut at alpha 1 behind a fully
                // opaque saveLayer.
                boolean enter = pushing && !pageNavPopPending
                        && (moving.page() == AppPage.SETTINGS_SECTION
                        || moving.page() == AppPage.PROFILE_DETAIL);
                // On an enter the root stays put beneath the scaling page.
                float rootDx = enter ? 0.0F
                        : pushing ? -travel * progress : -travel * (1.0F - progress);
                UiLayout.Rect panelRect = layout.rect();
                canvas.save();
                SkiaDraw.clip(canvas, panelRect.x(), panelRect.y(), panelRect.w(), panelRect.h(), 0.0F);
                canvas.translate(rootDx, 0.0F);
                drawRootScreen(canvas, mouseX, mouseY, navRoot.page());
                canvas.restore();

                Paint enterLayer = null;
                if (enter && !pageEnterDone()) {
                    // A translucent offscreen layer gives the page its fade
                    // while the spring drives the scale; the layer and its
                    // paint are closed by the branch that finishes drawing.
                    updatePageEnter(frameDt);
                    enterLayer = new Paint().setAlphaf(pageEnterAlpha());
                    canvas.saveLayer(Rect.makeXYWH(panelRect.x(), panelRect.y(),
                            panelRect.w(), panelRect.h()), enterLayer);
                    applyEnterScale(canvas, panelRect);
                } else if (!enter) {
                    canvas.save();
                    SkiaDraw.clip(canvas, panelRect.x(), panelRect.y(), panelRect.w(), panelRect.h(), 0.0F);
                    canvas.translate(pageNavDx(travel), 0.0F);
                }
                // A settings sub-page has no composer tail to draw into the
                // open layer, so it renders and closes its own layer here. The
                // chat page leaves the layer open for the shared tail below.
                if (moving.page() == AppPage.SETTINGS_SECTION) {
                    drawSettingsSection(canvas, mouseX, mouseY, moving.section());
                    if (enterLayer != null) {
                        canvas.restore();
                        enterLayer.close();
                    } else if (!enter) {
                        // The slide branch opened a save; the snapped enter
                        // opened nothing, so it must not pop past it.
                        canvas.restore();
                    }
                    suppressHeader = false;
                    // The header always names the destination, never the page
                    // that is sliding away — a pop flips the title on frame one
                    // too, which is what "click then title" expects.
                    drawPushedHeader(canvas, vmx, vmy, pushing ? moving : navRoot);
                    drawBezel(canvas, detailLayout());
                    return;
                }
                if (moving.page() == AppPage.PROFILE_DETAIL) {
                    drawProfileDetail(canvas, mouseX, mouseY);
                    if (enterLayer != null) {
                        canvas.restore();
                        enterLayer.close();
                    } else if (!enter) {
                        // Same pairing as the settings branch above.
                        canvas.restore();
                    }
                    suppressHeader = false;
                    drawPushedHeader(canvas, vmx, vmy, pushing ? moving : navRoot);
                    drawBezel(canvas, detailLayout());
                    return;
                }
            }
        }
        // A settled settings sub-page: header + list, nothing else.
        if (!navRunning && topPage() == AppPage.SETTINGS_SECTION) {
            drawSettingsSection(canvas, mouseX, mouseY, topNav().section());
            drawPushedHeader(canvas, vmx, vmy, topNav());
            drawBezel(canvas, detailLayout());
            return;
        }
        // A settled profile detail: same chrome, profile body.
        if (!navRunning && topPage() == AppPage.PROFILE_DETAIL) {
            drawProfileDetail(canvas, mouseX, mouseY);
            drawPushedHeader(canvas, vmx, vmy, topNav());
            drawBezel(canvas, detailLayout());
            return;
        }
        backButtonHover = UiMotion.approach(backButtonHover,
                isBackButtonHit(vmx, vmy) ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
        filterButtonHover = UiMotion.approach(filterButtonHover,
                topPage() == AppPage.WORLD_CHAT && isFilterButtonHit(vmx, vmy) ? 1.0F : 0.0F,
                frameDt, UiMotion.HOVER_MS);
        if (!suppressHeader) {
            ShellHeader.render(canvas, layout.header, shellTitleFor(topNav()), true,
                    backButton(), backButtonHover, textPrimary(),
                    topPage() == AppPage.PRIVATE_CHAT ? isOnlinePlayer(activePrivateTarget()) : null,
                    headerActionFor(topNav()));
        }

        // Grow the input bar before the list is measured, so the list loses
        // exactly the height the bar gains.
        layout = updateInputLayout(layout);

        drawChatPageBody(canvas, layout, mouseX, mouseY, topNav());

        emojiPanel.render(canvas, layout, toVirtualX(mouseX), toVirtualY(mouseY), frameDt);
        quickPhrasePanel.render(canvas, layout, toVirtualX(mouseX), toVirtualY(mouseY), frameDt);
        drawContextMenu(canvas, toVirtualX(mouseX), toVirtualY(mouseY));

        if (navRunning) {
            canvas.restore();
            suppressHeader = false;
            // Fixed status-bar header: it never slides with the page bodies.
            if (!pageNavTo.isRoot()) {
                ShellHeader.render(canvas, layout.header, shellTitleFor(pageNavTo), true,
                        backButton(), backButtonHover, textPrimary(),
                        pageNavTo.page() == AppPage.PRIVATE_CHAT
                                ? isOnlinePlayer(pageNavTo.target()) : null,
                        headerActionFor(pageNavTo));
            } else {
                UiLayout root = rootLayout();
                ShellHeader.render(canvas, root.header, shellTitleFor(pageNavTo), false, null, 0.0F,
                        textPrimary());
            }
        }

        // Bezel ring last: nothing at the panel edge can sit on top of it.
        drawBezel(canvas, layout);
    }

    /**
     * The whole chat page body — messages, reply bar, composer, scrollbar,
     * jump-to-latest — drawn for a specific nav page. Self-contained so the
     * full-width page push can slide it as one piece; the settled path calls
     * it with the top page. The header and the emoji/context overlays stay
     * with the caller: the header is a fixed status bar during pushes, and
     * the overlays belong to the screen, not the sliding page.
     */
    private void drawChatPageBody(Canvas canvas, UiLayout layout, int mouseX, int mouseY, NavPage page) {
        int probeBefore = canvas.getSaveCount();
        messageListView.draw(canvas, layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h(),
                messagesForNav(page), scrollForNav(page));
        probeCanvasLeak("messageListView.draw", probeBefore, canvas);

        // Reply bar floats above the input bar. It is drawn after the message
        // list so it always sits on top; the layout keeps an 8px gap below it.
        if (replyTarget != null) {
            UiLayout.Rect reply = layout.replyBar;
            float replyH = s(26);
            SkiaDraw.drawRoundedRect(canvas, reply.x(), reply.y(), reply.w(), replyH, UiTokens.radius(8), Color.makeARGB(90, 74, 144, 226));
            Font replyFont = FontManager.font(UiTokens.FONT_NAME);
            String replyLabel = tr("atomchat.reply.to", messageSenderName(replyTarget),
                    abbreviate(replyTarget.getContentText(), 26));
            SkiaFontRenderer.drawText(canvas, replyFont, replyLabel, reply.x() + UiTokens.QUOTE_PAD_X,
                    SkiaFontRenderer.centerBaselineY(replyFont, reply.y() + s(13)), textPrimary());
        }

        // Input bar: one button row (image / emoji … send), text row below.
        // The list layout already ends at this bar's top, so the translucent
        // card never has message content underneath it.
        UiLayout.Rect bar = layout.inputBar;
        boolean readOnly = isPrivateReadOnly();
        SkiaDraw.drawRoundedShadow(canvas, bar.x(), bar.y(), bar.w(), bar.h(), UiTokens.radius(18), s(8), UiTokens.CHROME_SHADOW);
        SkiaDraw.drawRoundedRect(canvas, bar.x(), bar.y(), bar.w(), bar.h(), UiTokens.radius(18), UiTokens.cardFill());
        if (quickPhrasePanel.isEditing()) {
            // The composer is borrowed as the phrase editor: ring it with the
            // accent so the mode is unmistakable.
            try (Paint border = new Paint().setMode(PaintMode.STROKE).setAntiAlias(true)
                    .setColor(accent()).setStrokeWidth(s(1.5F))) {
                canvas.drawRRect(io.github.humbleui.types.RRect.makeXYWH(
                        bar.x(), bar.y(), bar.w(), bar.h(), UiTokens.radius(18)), border);
            }
        }
        if (!readOnly) {
            drawIconButton(canvas, layout.imageBtn.x(), layout.imageBtn.y(), 0, mouseX, mouseY);
            drawIconButton(canvas, layout.phraseBtn.x(), layout.phraseBtn.y(), 3, mouseX, mouseY);
            drawIconButton(canvas, layout.emojiBtn.x(), layout.emojiBtn.y(), 1, mouseX, mouseY);
            drawSendButton(canvas, layout.sendBtn.x(), layout.sendBtn.y(), mouseX, mouseY);
        }

        // Input text: rendered by Skia at fixed density; the hidden EditBox is the
        // input backend (IME/keys) only. It wraps onto a second line (the bar has
        // already grown for it) and scrolls past INPUT_MAX_LINES.
        Font inputFont = FontManager.font(UiTokens.FONT_INPUT);
        float lineH = inputLineHeight();
        String current = inputGetText();
        float textX = bar.x() + UiTokens.INPUT_TEXT_X;
        float clipTop = layout.inputTextCenterY - lineH / 2.0F;
        float clipBottom = bar.bottom() - UiTokens.INPUT_ROW_PAD;
        canvas.save();
        SkiaDraw.clip(canvas, textX, clipTop, layout.inputTextMaxWidth(), Math.max(0.0F, clipBottom - clipTop), 0.0F);
        if (readOnly) {
            String hint = BlockList.isBlocked(activePrivateTarget())
                    ? tr("atomchat.private.blocked") : tr("atomchat.private.offline");
            SkiaFontRenderer.drawText(canvas, inputFont, hint, textX,
                    SkiaFontRenderer.centerBaselineY(inputFont, layout.inputTextCenterY), textSecondary());
            canvas.restore();
        } else {
            List<String> lines = wrappedInput(layout.inputTextMaxWidth());
            int total = lines.size();
            int caretRow = total == 0 ? 0 : caretLine(lines, caretIndex());
            scrollInputToCaret(caretRow, total);
            int shown = Math.min(UiTokens.INPUT_MAX_LINES, total);
            int from = total == 0 ? 0 : Math.min(inputScrollLine, total - shown);
            // Placeholder stays visible while the field is focused: ChatScreen
            // focuses the chat field the moment the screen opens, so a hint gated
            // on "not focused" was literally never on screen. It doubles as the
            // upload progress readout, which is the only feedback a file drop can
            // give — GLFW reports the drop itself but has no drag-enter to react to.
            if (current.isEmpty()) {
                String hintText;
                if (quickPhrasePanel.isEditing()) {
                    hintText = tr("atomchat.input.phrase_hint");
                } else if (imageUploading) {
                    hintText = tr("atomchat.input.uploading");
                } else if (partnerTyping()) {
                    // QQ-style: WATUT reports the partner composing a message
                    // (only the private page asks; silent no-op without WATUT).
                    hintText = tr("atomchat.private.typing");
                } else if (transientHint != null && System.currentTimeMillis() - transientHintSetAt < 4000L) {
                    hintText = transientHint;
                } else {
                    hintText = tr("atomchat.input.placeholder");
                }
                String hint = Cicodes.truncateToWidth(inputFont, hintText, layout.inputTextMaxWidth());
                SkiaFontRenderer.drawText(canvas, inputFont, hint, textX,
                        SkiaFontRenderer.centerBaselineY(inputFont, layout.inputTextCenterY), textSecondary());
            } else {
                drawInputSelection(canvas, inputFont, lines, from, shown, textX, layout.inputTextCenterY, lineH);
                for (int i = from; i < from + shown && i < total; i++) {
                    float cy = layout.inputTextCenterY + (i - from) * lineH;
                    SkiaFontRenderer.drawText(canvas, inputFont, lines.get(i), textX,
                            SkiaFontRenderer.centerBaselineY(inputFont, cy), textPrimary());
                }
            }
            if (inputFocused && input != null && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                int caret = caretIndex();
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
        drawScrollbar(canvas, layout, toVirtualX(mouseX), toVirtualY(mouseY), scrollForNav(page));
        drawJumpLatest(canvas, layout, toVirtualX(mouseX), toVirtualY(mouseY));
    }

    /**
     * White phone-style ring around the panel. Drawn last on both world-chat
     * and root pages so no component can sit on top of the clean edge. Part
     * of the frosted look — the opaque modern preset turns it off.
     */
    private void drawBezel(Canvas canvas, UiLayout layout) {
        if (!AtomChatConfig.get().panelOutline) {
            return;
        }
        UiLayout.Rect panel = layout.rect();
        float strokeWidth = s(3);
        try (Paint border = new Paint().setMode(PaintMode.STROKE).setStrokeWidth(strokeWidth)
                .setColor(AtomChatConfig.get().panelOutlineColor)) {
            canvas.drawRRect(RRect.makeXYWH(panel.x() + strokeWidth / 2.0F, panel.y() + strokeWidth / 2.0F,
                    panel.w() - strokeWidth, panel.h() - strokeWidth, UiTokens.panelRadius()), border);
        }
    }

    private UiLayout rootLayout() {
        return UiLayout.ofRoot(panelX(), panelY(), panelWidth(), panelHeight());
    }

    /** A pushed settings sub-page: no composer, no tab bar, tallest possible list. */
    private UiLayout detailLayout() {
        return UiLayout.ofDetail(panelX(), panelY(), panelWidth(), panelHeight());
    }

    /** Layout and scroll controller of whatever list the top page is showing. */
    private UiLayout listLayout() {
        return topPage() == AppPage.SETTINGS_SECTION || topPage() == AppPage.PROFILE_DETAIL
                ? detailLayout() : rootLayout();
    }

    private ScrollController listScroll() {
        return topPage() == AppPage.SETTINGS_SECTION || topPage() == AppPage.PROFILE_DETAIL
                ? detailScroll : rootScroll;
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
        if (section == null) {
            return;
        }
        UiLayout layout = detailLayout();
        float vmx = toVirtualX(mouseX);
        float vmy = toVirtualY(mouseY);
        detailScroll.setContent(settingsSectionPage.measureContent(layout, section), layout.list.h());
        syncChipScroll(section);
        detailScroll.updateAnimation(System.currentTimeMillis());
        settingsSectionPage.render(canvas, layout, section, vmx, vmy, detailScroll.getScrollY(), accent());
        drawScrollbar(canvas, layout, vmx, vmy, detailScroll);
        // Modal overlays on top of the whole panel: the cropper must render on
        // this path too — it is opened from here (wallpaper pick), and an
        // active-but-unrendered modal eats every click and freezes the screen.
        imageCropper.render(canvas, layout.rect(), vmx, vmy);
        colorPicker.render(canvas, layout.rect(), vmx, vmy, accent());
    }

    /** Body of a pushed profile detail page (another player's profile). */
    private void drawProfileDetail(Canvas canvas, int mouseX, int mouseY) {
        UiLayout layout = detailLayout();
        float vmx = toVirtualX(mouseX);
        float vmy = toVirtualY(mouseY);
        detailScroll.setContent(profilePage.measureContent(layout), layout.list.h());
        detailScroll.updateAnimation(System.currentTimeMillis());
        profilePage.render(canvas, layout, vmx, vmy, detailScroll.getScrollY());
        drawScrollbar(canvas, layout, vmx, vmy, detailScroll);
    }

    /** Fixed header (with back affordance) for a pushed settings sub-page. */
    private void drawPushedHeader(Canvas canvas, float vmx, float vmy, NavPage nav) {
        backButtonHover = UiMotion.approach(backButtonHover,
                isBackButtonHit(vmx, vmy) ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
        ShellHeader.render(canvas, detailLayout().header, shellTitleFor(nav), true,
                backButton(), backButtonHover, textPrimary(), null);
    }

    /** Renders the full root screen (header + page body + bottom tab bar). */
    private void drawRootScreen(Canvas canvas, int mouseX, int mouseY, AppPage rootPage) {
        UiLayout root = rootLayout();
        float vmx = toVirtualX(mouseX);
        float vmy = toVirtualY(mouseY);
        bottomTabBar.update(frameDt, vmx, vmy, root.tabBar);
        if (!suppressHeader) {
            ShellHeader.render(canvas, root.header, shellTitleFor(rootPage), false, null, 0.0F,
                    textPrimary());
        }
        rootMouseX = vmx;
        rootMouseY = vmy;
        rootScroll.setContent(measureRootContent(root, rootPage), root.list.h());
        rootScroll.updateAnimation(System.currentTimeMillis());
        drawRootPage(canvas, root, rootPage);
        drawScrollbar(canvas, root, vmx, vmy, rootScroll);
        drawBottomTabBar(canvas, root, rootPage);
        // Modal avatar cropper on top of everything inside the panel.
        imageCropper.render(canvas, root.rect(), vmx, vmy);
        colorPicker.render(canvas, root.rect(), vmx, vmy, accent());
    }

    private void drawRootPage(Canvas canvas, UiLayout layout, AppPage rootPage) {
        if (!rootTransitionActive()) {
            drawRootPageBody(canvas, layout, rootPage, 0.0F);
            return;
        }
        float progress = rootSlideProgress();
        if (progress >= 0.999F) {
            clearRootTransition();
            drawRootPageBody(canvas, layout, rootPage, 0.0F);
            return;
        }
        // Full-width opaque push, same language as the emoji tab content
        // transition: the outgoing page leaves in the direction of travel while
        // the incoming page enters from that side.
        canvas.save();
        try {
            SkiaDraw.clip(canvas, layout.list.x(), layout.list.y(),
                    layout.list.w(), layout.list.h(), 0.0F);
            float travel = layout.list.w();
            float sign = rootTabTo > rootTabFrom ? 1.0F : -1.0F;
            drawRootPageBody(canvas, layout, rootPageForIndex(rootTabFrom),
                    -sign * travel * progress);
            drawRootPageBody(canvas, layout, rootPage,
                    sign * travel * (1.0F - progress));
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
                case CHAT_LIST -> conversationListPage.render(canvas, layout, rootMouseX, rootMouseY, rootScroll.getScrollY());
                case PROFILE -> profilePage.render(canvas, layout, rootMouseX, rootMouseY, rootScroll.getScrollY());
                case SETTINGS -> settingsHomePage.render(canvas, layout, rootMouseX, rootMouseY,
                        rootScroll.getScrollY());
                case WORLD_CHAT, PRIVATE_CHAT ->
                        throw new IllegalStateException("Root page body cannot render chat/detail pages");
            }
        } finally {
            canvas.restore();
        }
    }

    private boolean rootTransitionActive() {
        return rootTabFrom >= 0 && rootTabTo >= 0
                && rootTabFrom != rootTabTo && !rootTabAnim.isDone();
    }

    private void clearRootTransition() {
        rootTabFrom = -1;
        rootTabTo = -1;
    }

    private float rootSlideProgress() {
        int from = rootTabFrom;
        int to = rootTabTo;
        if (from < 0 || to < 0 || from == to) {
            return 1.0F;
        }
        float pos = rootTabAnim.getValue();
        return Mth.clamp((from - pos) / (float) (from - to), 0.0F, 1.0F);
    }

    private AppPage rootPageForIndex(int index) {
        return switch (index) {
            case 0 -> AppPage.CHAT_LIST;
            case 1 -> AppPage.PROFILE;
            case 2 -> AppPage.SETTINGS;
            default -> throw new IllegalStateException("Unexpected root tab index " + index);
        };
    }

    private int rootIndex(AppPage page) {
        return switch (page) {
            case CHAT_LIST -> 0;
            case PROFILE -> 1;
            case SETTINGS -> 2;
            case WORLD_CHAT, PRIVATE_CHAT, SETTINGS_SECTION, PROFILE_DETAIL ->
                    throw new IllegalStateException("Pushed pages have no bottom tab index");
        };
    }

    private String shellTitle() {
        return shellTitleFor(topNav());
    }

    private String shellTitleFor(NavPage nav) {
        if (nav.page() == AppPage.PRIVATE_CHAT && nav.target() != null) {
            return nav.target().realName();
        }
        if (nav.page() == AppPage.SETTINGS_SECTION && nav.section() != null) {
            return settingsTitle(nav.section());
        }
        return shellTitleFor(nav.page());
    }

    private static String settingsTitle(SettingsSection section) {
        return switch (section) {
            case APPEARANCE -> tr("atomchat.settings.appearance");
            case CHAT -> tr("atomchat.settings.chat");
            case PRIVACY -> tr("atomchat.settings.privacy");
            case ABOUT -> tr("atomchat.settings.about");
        };
    }

    private String shellTitleFor(AppPage page) {
        return switch (page) {
            case CHAT_LIST -> tr("atomchat.tab.chat");
            case PROFILE -> tr("atomchat.page.profile.title");
            case SETTINGS -> tr("atomchat.tab.settings");
            case WORLD_CHAT -> tr("atomchat.channel.world");
            case PRIVATE_CHAT -> activePrivateTarget() != null
                    ? activePrivateTarget().realName() : tr("atomchat.channel.private");
            case SETTINGS_SECTION -> tr("atomchat.tab.settings");
            case PROFILE_DETAIL -> tr("atomchat.page.profile.title");
        };
    }

    private void drawBottomTabBar(Canvas canvas, UiLayout layout, AppPage rootPage) {
        int selectedIndex = rootIndex(rootPage);
        bottomTabBar.render(canvas, layout.tabBar, selectedIndex, textPrimary(), accent());
    }

    private void drawBottomTabBar(Canvas canvas, UiLayout layout) {
        drawBottomTabBar(canvas, layout, topPage());
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
        switchRoot(root);
        return true;
    }

    private static float s(float v) {
        return UiTokens.s(v);
    }

    /** Minecraft language lookup for all AtomChat UI copy. */
    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private void drawIconButton(Canvas canvas, float bx, float by, int id, int mouseX, int mouseY) {
        float vmx = toVirtualX(mouseX);
        float vmy = toVirtualY(mouseY);
        boolean hover = vmx >= bx && vmx <= bx + UiTokens.BUTTON_W && vmy >= by && vmy <= by + UiTokens.BUTTON_H;
        buttonHover[id] = UiMotion.approach(buttonHover[id], hover ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
        int fill = Math.min(255, (int) (70 + buttonHover[id] * 45.0F + (buttonPressed(id) ? 50 : 0)));
        SkiaDraw.drawRoundedRect(canvas, bx, by, UiTokens.BUTTON_W, UiTokens.BUTTON_H, UiTokens.BUTTON_RADIUS, Color.makeARGB(fill, 255, 255, 255));
        // Active states take the accent colour: the emoji button while its
        // panel is open (a toggle), any button for a moment after a press.
        boolean activeTint = (id == 1 && emojiPanel.isOpen()) || (id == 3 && quickPhrasePanel.isOpen())
                || buttonPressed(id);
        io.github.humbleui.skija.Path icon = switch (id) {
            case 0 -> ICON_IMAGE_PATH;
            case 3 -> ICON_PHRASE_PATH;
            default -> ICON_EMOJI_PATH;
        };
        drawIcon(canvas, icon, bx, by, activeTint ? accent() : textPrimary());
    }

    private void drawSendButton(Canvas canvas, float bx, float by, int mouseX, int mouseY) {
        float vmx = toVirtualX(mouseX);
        float vmy = toVirtualY(mouseY);
        boolean hover = vmx >= bx && vmx <= bx + UiTokens.BUTTON_W && vmy >= by && vmy <= by + UiTokens.BUTTON_H;
        buttonHover[2] = UiMotion.approach(buttonHover[2], hover ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
        SkiaDraw.drawRoundedRect(canvas, bx, by, UiTokens.BUTTON_W, UiTokens.BUTTON_H, UiTokens.BUTTON_RADIUS, accent());
        float overlay = buttonHover[2] * 55.0F + (buttonPressed(2) ? 90.0F : 0.0F);
        if (overlay > 0.5F) {
            SkiaDraw.drawRoundedRect(canvas, bx, by, UiTokens.BUTTON_W, UiTokens.BUTTON_H, UiTokens.BUTTON_RADIUS,
                    Color.makeARGB((int) Math.min(160, overlay), 255, 255, 255));
        }
        drawIcon(canvas, ICON_SEND_PATH, bx, by, textPrimary());
    }

    /**
     * Draws one of the inline SVG-path toolbar icons, centered in its button
     * and scaled to fit an {@code ICON_SIZE} box. Stroke width scales with the
     * rendered icon size so every icon keeps the same optical line weight.
     */
    private void drawIcon(Canvas canvas, io.github.humbleui.skija.Path icon, float bx, float by, int color) {
        drawIconCentered(canvas, icon, bx + UiTokens.BUTTON_W / 2.0F, by + UiTokens.BUTTON_H / 2.0F,
                s(18), color);
    }

    /** Draws an icon centered on an arbitrary point; used by toolbar and menus. */
    private void drawIconCentered(Canvas canvas, io.github.humbleui.skija.Path icon, float cx, float cy,
                                  float size, int color) {
        Rect b = icon.getBounds();
        if (b == null || b.isEmpty()) {
            return;
        }
        float scale = size / Math.max(b.getWidth(), b.getHeight());
        canvas.save();
        try {
            canvas.translate(cx - (b.getLeft() + b.getRight()) / 2.0F * scale,
                    cy - (b.getTop() + b.getBottom()) / 2.0F * scale);
            canvas.scale(scale, scale);
            try (Paint paint = new Paint().setColor(color).setAntiAlias(true)
                    .setMode(PaintMode.STROKE)
                    .setStrokeWidth(UiTokens.iconStroke(size) / scale)
                    .setStrokeCap(PaintStrokeCap.ROUND)
                    .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally {
            canvas.restore();
        }
    }

    /**
     * Draws the hidden EditBox's selection as Skia highlight blocks over the
     * wrapped input lines. AtomChat renders its own text, so the vanilla field's
     * selection highlight would otherwise be invisible.
     */
    private void drawInputSelection(Canvas canvas, Font font, List<String> lines, int from, int shown,
                                    float textX, float centerY, float lineH) {
        if (input == null || lines.isEmpty()) {
            return;
        }
        int len = inputGetText().length();
        int a = Mth.clamp(input.cursorPos, 0, len);
        int b = Mth.clamp(input.highlightPos, 0, len);
        int selStart = Math.min(a, b);
        int selEnd = Math.max(a, b);
        if (selStart >= selEnd) {
            return;
        }

        for (int i = from; i < from + shown && i < lines.size(); i++) {
            int lineStart = 0;
            for (int j = 0; j < i; j++) {
                lineStart += lines.get(j).length();
            }
            int lineLen = lines.get(i).length();
            int lineEnd = lineStart + lineLen;
            if (selEnd <= lineStart || selStart >= lineEnd) {
                continue;
            }
            int c0 = Math.max(0, Math.min(selStart - lineStart, lineLen));
            int c1 = Math.max(0, Math.min(selEnd - lineStart, lineLen));
            if (c0 >= c1) {
                continue;
            }
            String line = lines.get(i);
            float x0 = textX + SkiaFontRenderer.getStringWidth(font, line.substring(0, c0));
            float x1 = textX + SkiaFontRenderer.getStringWidth(font, line.substring(0, c1));
            float cy = centerY + (i - from) * lineH;
            SkiaDraw.drawRoundedRect(canvas, x0, cy - lineH / 2.0F, Math.max(1.0F, x1 - x0), lineH, s(2), 0xE02D6FD6);
        }
    }


    private boolean buttonPressed(int id) {
        return pressedButton == id && System.currentTimeMillis() - pressTime < 150L;
    }

    private void pressButton(int id) {
        pressedButton = id;
        pressTime = System.currentTimeMillis();
    }

    /**
     * Rounded scrollbar: fades in only while the pointer is near the track or
     * while dragged, and fades straight back out otherwise.
     * Thumb maps scrollY to the track; drag maps 1:1 with clamping.
     */
    private void drawScrollbar(Canvas canvas, UiLayout layout, float vmx, float vmy,
                               ScrollController controller) {
        long now = System.currentTimeMillis();

        UiLayout.Rect list = layout.list;
        float trackW = s(6);
        float trackH = list.h();
        float visibleRatio = Math.min(1.0F, trackH / (trackH + controller.getMaxScroll()));
        float thumbH = Math.max(s(30), trackH * visibleRatio);
        // Deliberately computed before drag updates below, matching the original
        // frame ordering: the thumb is drawn from the offset captured at frame start.
        float thumbY = list.y() + (trackH - thumbH) * (controller.getScrollY() / controller.getMaxScroll());

        if (controller.updateScrollbarFade(now, vmx, vmy, list, controller.isDragging(), trackW, frameDt) <= 0.0F) {
            return;
        }

        if (controller.isDragging()) {
            controller.dragTo(vmy, trackH);
        }

        float w = trackW + controller.getScrollEmphasis() * s(3);
        int ar = (accent() >> 16) & 0xFF;
        int ag = (accent() >> 8) & 0xFF;
        int ab = accent() & 0xFF;
        int r = (int) (255 + (ar - 255) * controller.getScrollActive());
        int g = (int) (255 + (ag - 255) * controller.getScrollActive());
        int bch = (int) (255 + (ab - 255) * controller.getScrollActive());
        int alpha = Mth.clamp((int) ((170 + 60 * controller.getScrollEmphasis())
                * controller.getScrollBarAlpha()), 0, 255);
        int color = (alpha << 24) | (r << 16) | (g << 8) | bch;
        float trackX = list.right() - trackW - s(2);
        SkiaDraw.drawRoundedRect(canvas, trackX - (w - trackW) / 2.0F, thumbY, w, thumbH, w / 2.0F, color);
    }

    private boolean overScrollbarTrack(UiLayout layout, float vmx, float vmy,
                                       ScrollController controller) {
        float trackW = s(6);
        float trackX = layout.list.right() - trackW - s(2);
        return controller.getMaxScroll() > 0.0F
                && vmx >= trackX - s(8) && vmx <= trackX + trackW + s(8)
                && vmy >= layout.list.y() && vmy <= layout.list.bottom();
    }


    private void drawJumpLatest(Canvas canvas, UiLayout layout, float vmx, float vmy) {
        ScrollController scroll = currentScroll();
        boolean show = scroll.getMaxScroll() > 0.0F && !scroll.isAtBottom();
        jumpLatestAnim = UiMotion.approach(jumpLatestAnim, show ? 1.0F : 0.0F, frameDt, UiMotion.POPUP_MS);
        if (jumpLatestAnim < 0.01F) {
            return;
        }
        float size = s(38);
        float x = layout.list.right() - size - s(12);
        float y = layout.list.bottom() - size - s(12);
        boolean hover = vmx >= x && vmx <= x + size && vmy >= y && vmy <= y + size;
        canvas.save();
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * jumpLatestAnim), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(x - s(4), y - s(4), size + s(8), size + s(8)), layer);
            int bg = hover ? Color.makeARGB(245, 70, 76, 90) : Color.makeARGB(235, 52, 58, 70);
            SkiaDraw.drawRoundedRect(canvas, x, y, size, size, size / 2.0F, bg);
            SkiaDraw.drawRoundedShadow(canvas, x, y, size, size, size / 2.0F, s(6), Color.makeARGB(80, 0, 0, 0));
            drawIconCentered(canvas, ICON_JUMP_DOWN_PATH, x + size / 2.0F, y + size / 2.0F, s(18), textPrimary());
            canvas.restore();
        } finally {
            canvas.restore();
        }
    }

    private boolean overJumpLatest(UiLayout layout, float vmx, float vmy) {
        ScrollController scroll = currentScroll();
        if (jumpLatestAnim < 0.01F || scroll.getMaxScroll() <= 0.0F || scroll.isAtBottom()) {
            return false;
        }
        float size = s(38);
        float x = layout.list.right() - size - s(12);
        float y = layout.list.bottom() - size - s(12);
        return vmx >= x && vmx <= x + size && vmy >= y && vmy <= y + size;
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
        Thread worker = new Thread(() -> {
            Path file = FilePicker.pickImage(this::suppressAutoIconify, this::restoreAutoIconify,
                    EmoteStore::isSupportedName);
            refocusWindow();
            if (file == null) {
                return;
            }
            this.client.execute(() -> emojiPanel.addEmote(file));
        }, "AtomChat-EmotePicker");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Row count for a context menu. Drawing and every click hit-test go through
     * this one helper so their geometry cannot drift apart: the player-card menu
     * grew a third row (block/unblock) while the root-page click test stayed at
     * two, which made "取消屏蔽" unclickable.
     */
    private static int contextMenuRows(ContextMenuMode mode, boolean imageMessage) {
        if (mode == ContextMenuMode.AVATAR) {
            return 4;
        }
        if (mode == ContextMenuMode.PLAYER_CARD) {
            return 3;
        }
        return imageMessage ? 3 : 2;
    }

    private void drawContextMenu(Canvas canvas, float vmx, float vmy) {
        boolean hasCurrent = contextMessage != null || contextPlayer != null;
        boolean hasLast = lastContextMessage != null || lastContextPlayer != null;
        if (!hasCurrent && !hasLast) {
            contextAnim = 0.0F;
            return;
        }
        float target = hasCurrent ? 1.0F : 0.0F;
        contextAnim = UiMotion.approach(contextAnim, target, frameDt, Animations.ms(UiMotion.POPUP_MS));
        if (contextAnim < 0.01F) {
            if (!hasCurrent) {
                lastContextMessage = null;
                lastContextPlayer = null;
                contextAnim = 0.0F;
            }
            return;
        }
        ContextMenuMode mode = hasCurrent ? contextMenuMode : lastContextMenuMode;
        boolean avatarMenu = mode == ContextMenuMode.AVATAR;
        boolean playerMenu = mode == ContextMenuMode.PLAYER_CARD;
        boolean bubbleMenu = !avatarMenu && !playerMenu;
        ChatMessage shown = contextMessage != null ? contextMessage : lastContextMessage;
        boolean imageMessage = bubbleMenu && shown != null && Cicodes.extractImageUrl(shown.getRawText()) != null;
        int rows = contextMenuRows(mode, imageMessage);
        float rowH = UiTokens.MENU_H / 2.0F;
        float menuH = rowH * rows;
        float menuW = UiTokens.MENU_W;
        float menuX = Math.min(contextX, panelX() + panelWidth() - menuW - s(8));
        float menuY = Math.min(contextY, panelY() + panelHeight() - menuH - s(8));
        for (int row = 0; row < rows; row++) {
            float rowY = menuY + row * rowH;
            boolean overRow = vmx >= menuX && vmx <= menuX + menuW
                    && vmy >= rowY && vmy <= rowY + rowH;
            contextMenuHover[row] = UiMotion.approach(contextMenuHover[row], overRow ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
        }
        canvas.save();
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * contextAnim), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(menuX - s(20), menuY - s(20), menuW + s(40), menuH + s(40)), layer);
            float sc = 0.94F + 0.06F * contextAnim;
            canvas.translate(menuX + menuW / 2.0F, menuY);
            canvas.scale(sc, sc);
            canvas.translate(-(menuX + menuW / 2.0F), -menuY);
            SkiaDraw.drawRoundedRect(canvas, menuX, menuY, menuW, menuH, UiTokens.radius(10), Color.makeARGB(245, 35, 39, 47));
            SkiaDraw.drawRoundedShadow(canvas, menuX, menuY, menuW, menuH, UiTokens.radius(10), s(8), Color.makeARGB(100, 0, 0, 0));
            Font menuFont = FontManager.font(UiTokens.FONT_BUTTON);
            for (int row = 0; row < rows; row++) {
                float rowY = menuY + row * rowH;
                float hov = contextMenuHover[row];
                if (hov > 0.01F) {
                    // Uniform s(4) inset on every side of the row capsule so it
                    // never looks top-heavy against the menu edges.
                    SkiaDraw.drawRoundedRect(canvas, menuX + s(4), rowY + s(4), menuW - s(8), rowH - s(8),
                            s(6), Color.makeARGB((int) (55.0F * hov), 255, 255, 255));
                }
                String label;
                io.github.humbleui.skija.Path icon;
                if (playerMenu) {
                    label = playerCardContextLabel(row);
                    icon = playerCardContextIcon(row);
                } else if (avatarMenu) {
                    label = avatarContextLabel(row);
                    icon = avatarContextIcon(row);
                } else {
                    label = bubbleContextLabel(row, imageMessage);
                    icon = bubbleContextIcon(row, imageMessage);
                }
                // Overlay surfaces keep the fixed overlay palette: they sit on
                // their own opaque dark cards and must not follow the
                // interface text colour setting.
                int textColor = Color.makeARGB(255, 255, 255, 255);
                if (playerMenu && row == 1 && !isOnlinePlayer(contextPlayer != null ? contextPlayer : lastContextPlayer)) {
                    textColor = Color.makeARGB(110, 255, 255, 255);
                } else if (avatarMenu && row == 2) {
                    ChatMessage avatarMsg = contextMessage != null ? contextMessage : lastContextMessage;
                    if (avatarMsg != null) {
                        PlayerRef avatarPlayer = PlayerRef.of(avatarMsg.getSenderUuid(), avatarMsg.getProfileName());
                        if (avatarPlayer != null && !isOnlinePlayer(avatarPlayer)) {
                            textColor = Color.makeARGB(110, 255, 255, 255);
                        }
                    }
                }
                drawIconCentered(canvas, icon, menuX + s(18), rowY + rowH / 2.0F, UiTokens.CONTEXT_ICON_SIZE, textColor);
                SkiaFontRenderer.drawText(canvas, menuFont, label, menuX + s(36),
                        SkiaFontRenderer.centerBaselineY(menuFont, rowY + rowH / 2.0F), textColor);
            }
            canvas.restore();
        }
        canvas.restore();
    }

    private static String bubbleContextLabel(int row, boolean imageMessage) {
        if (!imageMessage) {
            return switch (row) {
                case 0 -> tr("atomchat.context.copy");
                case 1 -> tr("atomchat.context.quote");
                default -> tr("atomchat.context.save");
            };
        }
        return switch (row) {
            case 0 -> tr("atomchat.context.copy");
            case 1 -> tr("atomchat.context.quote");
            case 2 -> tr("atomchat.context.save");
            default -> "";
        };
    }

    private static io.github.humbleui.skija.Path bubbleContextIcon(int row, boolean imageMessage) {
        if (!imageMessage) {
            return switch (row) {
                case 0 -> ICON_COPY_PATH;
                case 1 -> ICON_QUOTE_PATH;
                default -> ICON_SAVE_PATH;
            };
        }
        return switch (row) {
            case 0 -> ICON_COPY_PATH;
            case 1 -> ICON_QUOTE_PATH;
            case 2 -> ICON_SAVE_PATH;
            default -> ICON_SAVE_PATH;
        };
    }

    private String avatarContextLabel(int row) {
        ChatMessage target = contextMessage != null ? contextMessage : lastContextMessage;
        if (row == 3 && target != null) {
            PlayerRef p = PlayerRef.of(target.getSenderUuid(), target.getProfileName());
            return BlockList.isBlocked(p) ? tr("atomchat.context.unblock") : tr("atomchat.context.block");
        }
        return switch (row) {
            case 0 -> tr("atomchat.context.mention");
            case 1 -> tr("atomchat.context.whisper");
            case 2 -> tr("atomchat.context.tp");
            default -> "";
        };
    }

    private static io.github.humbleui.skija.Path avatarContextIcon(int row) {
        return switch (row) {
            case 0 -> ICON_MENTION_PATH;
            case 1 -> ICON_WHISPER_PATH;
            case 2 -> ICON_TP_PATH;
            case 3 -> ICON_BLOCK_PATH;
            default -> ICON_MENTION_PATH;
        };
    }

    private String playerCardContextLabel(int row) {
        return switch (row) {
            case 0 -> tr("atomchat.context.profile");
            case 1 -> tr("atomchat.context.tp");
            case 2 -> isBlockedContextPlayer() ? tr("atomchat.context.unblock") : tr("atomchat.context.block");
            default -> "";
        };
    }

    private static io.github.humbleui.skija.Path playerCardContextIcon(int row) {
        return switch (row) {
            case 0 -> com.atom.chat.ui.AppIcons.ICON_TAB_PROFILE_PATH;
            case 1 -> ICON_TP_PATH;
            case 2 -> ICON_BLOCK_PATH;
            default -> ICON_TP_PATH;
        };
    }

    private boolean isBlockedContextPlayer() {
        PlayerRef p = contextPlayer != null ? contextPlayer : lastContextPlayer;
        return p != null && BlockList.isBlocked(p);
    }

    private static boolean isOnlinePlayer(PlayerRef player) {
        if (player == null) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return false;
        }
        for (PlayerInfo entry : client.getConnection().getOnlinePlayers()) {
            if (player.uuid() != null && player.uuid().equals(entry.getProfile().getId())) {
                return true;
            }
            if (player.realName() != null && player.realName().equalsIgnoreCase(entry.getProfile().getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hands the open menu over to lastContextMessage so it can play the closing
     * animation instead of vanishing. Never recurse: it used to call itself,
     * which left contextMessage set forever and blew the stack on the caller.
     */
    private void closeContextMenu() {
        if (contextMessage != null) {
            lastContextMessage = contextMessage;
            lastContextMenuMode = contextMenuMode;
            contextMessage = null;
        }
        if (contextPlayer != null) {
            lastContextPlayer = contextPlayer;
            lastContextMenuMode = contextMenuMode;
            contextPlayer = null;
        }
    }

    /** Avatar menus only make sense for another player with a real sender identity. */
    private static boolean canOpenAvatarMenu(ChatMessage msg) {
        if (msg == null || msg.isSystem() || msg.isOwn()) {
            return false;
        }
        return msg.getSenderName() != null || msg.getProfileName() != null;
    }

    /**
     * Avatar context-menu actions. Row 0 (@ mention) is already wired because it
     * is the direct replacement for the old single-left-click behavior. Whisper /
     * Teleport / Block intentionally do nothing yet — the menu framework is the
     * current step, and each action is wired in its own feature pass.
     */
    private void performAvatarMenuAction(int row, ChatMessage message) {
        PlayerRef player = PlayerRef.of(message.getSenderUuid(), message.getProfileName());
        if (player == null || player.realName() == null) {
            return;
        }
        if (row == 0) {
            // Insert the real profile name, not the decorated label: the label
            // reaches other clients as uncoloured plain text (the title colour
            // cannot survive a vanilla chat string) and a decorated @token also
            // defeats mention detection that looks for @ProfileName.
            inputAppend("@" + player.realName() + " ");
        } else if (row == 1) {
            openPrivateChat(player);
        } else if (row == 2) {
            if (isOnlinePlayer(player)) {
                sendTeleportCommand(player);
            }
        } else if (row == 3) {
            toggleBlock(player);
        }
    }

    private void performPlayerCardAction(int row) {
        PlayerRef player = contextPlayer != null ? contextPlayer : lastContextPlayer;
        if (player == null) {
            return;
        }
        if (row == 0) {
            openProfileDetail(player);
        } else if (row == 1) {
            if (isOnlinePlayer(player)) {
                sendTeleportCommand(player);
            }
        } else if (row == 2) {
            toggleBlock(player);
        }
    }

    private void sendTeleportCommand(PlayerRef player) {
        if (this.client.player == null || player == null || player.realName() == null) {
            return;
        }
        // auto: probe the server command tree, fall back on failure replies.
        String command = TeleportCommands.commandFor(client, AtomChatConfig.get().teleportCommandMode);
        this.client.player.connection.sendCommand(command.substring(1) + " " + player.realName());
        TeleportCommands.noteTeleportSent();
    }

    private void toggleBlock(PlayerRef player) {
        if (player == null) {
            return;
        }
        boolean nowBlocked = BlockList.isBlocked(player);
        BlockList.setBlocked(player, !nowBlocked);
    }

    private void copyToClipboard(String text) {
        try {
            this.client.keyboardHandler.setClipboard(text);
        } catch (Throwable t) {
            // Never let a clipboard failure abort the click handler: it used to
            // leave the menu stuck open with no clue why.
            AtomChat.LOGGER.warn("Failed to copy message to clipboard", t);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inputRouter.scroll(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
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
            AtomChat.LOGGER.info("click: gui=({}, {}) virtual=({}, {}) button={}",
                    (int) mouseX, (int) mouseY,
                    (int) toVirtualX(mouseX), (int) toVirtualY(mouseY), button);
        }
        if (inputRouter.click(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (inputRouter.drag(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (inputRouter.release(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inputRouter.key(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (inputRouter.charTyped(chr, modifiers)) {
            return true;
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
     * Saves another player's CICode image to a local file. The save dialog runs
     * on the same worker-thread pattern as the open picker; the actual download
     * happens off-thread via {@link ImageSaver} so the raw bytes stay identical.
     */
    private void saveImage(ChatMessage message) {
        String url = Cicodes.extractImageUrl(message.getRawText());
        if (url == null) {
            return;
        }
        Thread worker = new Thread(() -> {
            suppressAutoIconify();
            Path target;
            try {
                target = FilePicker.pickSavePath(fileNameFromUrl(url));
            } finally {
                restoreAutoIconify();
            }
            refocusWindow();
            if (target == null) {
                return;
            }
            ImageSaver.save(url, target).whenComplete((path, throwable) -> {
                if (throwable != null) {
                    this.client.execute(() -> showTransientHint(tr("atomchat.input.save_failed")));
                } else {
                    AtomChat.LOGGER.info("Saved chat image to {}", path);
                }
            });
        }, "AtomChat-ImageSavePicker");
        worker.setDaemon(true);
        worker.start();
    }

    /** URL last path segment, stripped of query/fragment; falls back to image.png. */
    private static String fileNameFromUrl(String url) {
        return ImageFileNames.fromUrl(url);
    }

    private void showTransientHint(String text) {
        transientHint = text;
        transientHintSetAt = System.currentTimeMillis();
    }

    /**
     * Uploads an image and drops its CICode into the draft. Shared by the file
     * picker and by Ctrl+V pastes.
     */
    private void uploadAndAppend(Path file) {
        int[] size = ImageFiles.dimensions(file);
        imageUploading = true;
        // The server decides: when it hosts media, upload there first; any
        // failure (or a server without the companion) falls back to uguu.
        if (MediaCompanionClient.hostingEnabled()) {
            MediaCompanionClient.uploadFile(file,
                    url -> appendImageCode(url, file, size),
                    error -> {
                        AtomChat.LOGGER.warn("Server media upload failed ({}), falling back to uguu", error);
                        uploadToUguu(file, size);
                    });
        } else {
            if (AtomChatConfig.get().debug) {
                AtomChat.LOGGER.info("[media] server media hosting unavailable; using the external host");
            }
            uploadToUguu(file, size);
        }
    }

    private void uploadToUguu(Path file, int[] size) {
        imageUploader.upload(file,
                url -> {
                    // Keep the choice auditable: server-hosted uploads already
                    // log "Stored hosted media", so the external path logs too.
                    AtomChat.LOGGER.info("Uploaded chat image to the external host: {}", url);
                    appendImageCode(url, file, size);
                },
                error -> {
                    AtomChat.LOGGER.warn("Image upload failed: {}", error);
                    this.client.execute(() -> imageUploading = false);
                });
    }

    private void appendImageCode(String url, Path file, int[] size) {
        StringBuilder code = new StringBuilder("[[CICode,url=").append(url)
                .append(",name=").append(file.getFileName());
        // Carry the intrinsic size so any client receiving this can lay the
        // bubble out at the right aspect ratio before the download lands —
        // that is what keeps the height from jumping when it arrives.
        if (size != null) {
            code.append(",w=").append(size[0]).append(",h=").append(size[1]);
        }
        code.append("]]");
        String text = code.toString();
        // The upload callbacks run off the render thread; the chat field is
        // only safe to touch from the render thread.
        this.client.execute(() -> {
            imageUploading = false;
            inputAppend(inputGetText().isEmpty() ? text : " " + text);
        });
    }

    /**
     * Ctrl+V with a picture on the clipboard. The payload is read off the render
     * thread — a screenshot is several megabytes and converting it is not free —
     * then uploaded and appended.
     */
    private void pasteFromClipboard(DataFlavor flavor) {
        Thread worker = new Thread(() -> {
            Path file = ClipboardImages.read(flavor);
            if (file == null) {
                AtomChat.LOGGER.warn("Clipboard held no usable image; Ctrl+V fell back to a text paste");
                return;
            }
            uploadAndAppend(file);
        }, "AtomChat-Paste");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * GLFW iconifies a fullscreen window as soon as it loses focus
     * (GLFW_AUTO_ICONIFY defaults to true and Minecraft leaves it there), so a
     * picker that takes focus would minimise the game every time. Suspension
     * lasts exactly as long as the picker is open.
     */
    private void suppressAutoIconify() {
        setAutoIconify(false);
    }

    private void restoreAutoIconify() {
        setAutoIconify(true);
    }

    private void setAutoIconify(boolean value) {
        runOnRender(() -> {
            try {
                GLFW.glfwSetWindowAttrib(this.client.getWindow().getWindow(),
                        GLFW.GLFW_AUTO_ICONIFY, value ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            } catch (Throwable t) {
                AtomChat.LOGGER.warn("Failed to toggle GLFW_AUTO_ICONIFY for the image picker", t);
            }
        });
    }

    /** Runs a GLFW call on the render thread and waits for it to land. */
    private void runOnRender(Runnable task) {
        CountDownLatch done = new CountDownLatch(1);
        this.client.execute(() -> {
            try {
                task.run();
            } finally {
                done.countDown();
            }
        });
        try {
            done.await(2L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns OS focus to the game window after the native picker closes, and
     * clears any key/button state the dialog left behind. GLFW window calls are
     * not thread-safe, so the work hops onto the render thread.
     */
    private void refocusWindow() {
        this.client.execute(() -> {
            KeyMapping.releaseAll();
            if (this.client.mouseHandler != null) {
                ((MouseHandlerAccessor) this.client.mouseHandler).atomchat$setActiveButton(0);
            }
            try {
                GLFW.glfwFocusWindow(this.client.getWindow().getWindow());
            } catch (Throwable t) {
                AtomChat.LOGGER.warn("Failed to refocus the window after the image picker", t);
            }
        });
    }

    private static String quoteTextFor(ChatMessage message) {
        if (message == null) {
            return null;
        }
        String contentText = message.getContentText();
        if (Cicodes.extractImageUrl(message.getRawText()) != null
                || (contentText != null && Cicodes.extractImageUrl(contentText) != null)) {
            return tr("atomchat.hud.image");
        }
        return abbreviate(message.getContentText(), 30);
    }

    private void sendMessage(String text) {
        String normalized = normalizeInput(text);
        if (normalized.isEmpty()) {
            return;
        }
        if (this.client.player == null) {
            return;
        }
        String quoteName = null;
        String quoteText = null;
        // The typed body stays separate from the wire text: the quote prefix
        // travels with the message, but the local bubble must not show it.
        String body = normalized;
        if (replyTarget != null) {
            quoteName = messageSenderName(replyTarget);
            quoteText = quoteTextFor(replyTarget);
            // Quote travels with the message so other players can see it too.
            normalized = "「引用 @" + quoteName + ": " + quoteText + "」" + body;
        }

        boolean privateChat = topPage() == AppPage.PRIVATE_CHAT;
        PlayerRef privateTarget = privateChat ? activePrivateTarget() : null;
        if (privateChat) {
            sendPrivateMessage(normalized, body, privateTarget);
            return;
        }

        boolean command = normalized.startsWith("/");
        if (command) {
            this.client.player.connection.sendCommand(normalized.substring(1));
        } else {
            if (!normalized.startsWith("「引用")
                    && (normalized.startsWith("http://") || normalized.startsWith("https://"))
                    && !normalized.contains("CICode")
                    && ImageFiles.isImageUrl(normalized)) {
                normalized = "[[CICode,url=" + normalized + ",name=图片]]";
            }
            this.client.player.connection.sendChat(normalized);
            // Arm echo suppression: the server relays this line back through the
            // identity-less path on NCR-style servers (e33chat parity).
            PublicEchoTracker.markSent(normalized);
        }
        this.client.gui.getChat().addRecentChat(normalized);
        // Vanilla never echoes commands back into the chat feed as your own
        // message, so do not manufacture a local bubble for them either.
        // Non-command chat still gets an immediate local echo so the UI feels
        // like a phone messenger even before the server relays the message.
        if (!command) {
            UUID ownUuid = this.client.player.getUUID();
            String ownProfile = this.client.player.getName().getString();
            // Own bubbles show the decorated self name (titles/team prefix),
            // e33chat parity — the bare profile name stays for identity fields.
            ChatStore.get().add(LocalEcho.build(normalized, body, quoteName, quoteText,
                    ownUuid, ownProfile, OwnIdentity.displayNameRich()));
        }
        inputSetText("");
        replyTarget = null;
        inputFocused = true;
        currentScroll().stickToBottom();
    }

    private void sendPrivateMessage(String normalized, String body, PlayerRef target) {
        if (target == null || BlockList.isBlocked(target)) {
            return;
        }
        boolean command = normalized.startsWith("/");
        String historyText = normalized;
        String sendText = command ? normalized
                : "/msg " + target.realName() + " " + normalized;
        this.client.player.connection.sendCommand(sendText.startsWith("/")
                ? sendText.substring(1) : sendText);
        this.client.gui.getChat().addRecentChat(historyText);

        if (!command) {
            UUID ownUuid = this.client.player.getUUID();
            String ownProfile = this.client.player.getName().getString();
            // Decorated self name on the outgoing bubble, e33chat parity.
            PrivateChatStore.addOutgoing(target,
                    LocalEcho.build(historyText, body,
                            replyTarget != null ? messageSenderName(replyTarget) : null,
                            replyTarget != null ? quoteTextFor(replyTarget) : null,
                            ownUuid, ownProfile, OwnIdentity.displayNameRich()));
            PrivateEchoTracker.markOutgoing(target);
        }
        inputSetText("");
        replyTarget = null;
        inputFocused = true;
        currentScroll().stickToBottom();
    }

    private static String normalizeInput(String text) {
        return StringUtil.trimChatMessage(StringUtils.normalizeSpace(text.trim()));
    }


    private String ownName() {
        return this.client.player != null ? this.client.player.getName().getString() : tr("atomchat.sender.me");
    }

    /**
     * Display name for a message row. Own messages use the local player; other
     * messages prefer the structured sender name from the capture pipeline and
     * only fall back to the old hard-coded label when identity is unavailable.
     */
    private String messageSenderName(ChatMessage msg) {
        if (msg.isOwn()) {
            return ownName();
        }
        String name = msg.getSenderName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return tr("atomchat.sender.player");
    }

    private static String abbreviate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "…";
    }

    private int accent() {
        return AtomChatConfig.get().accentColor;
    }

    private int ownBubble() {
        return AtomChatConfig.get().ownBubbleColor;
    }

    private int otherBubble() {
        return AtomChatConfig.get().otherBubbleColor;
    }

    private int textPrimary() {
        return AtomChatConfig.get().textPrimaryColor;
    }

    private int textSecondary() {
        return AtomChatConfig.get().textSecondaryColor;
    }

    /** Component inside a chat bubble (body rich text and quoted text). */
    private int bubbleText(ChatMessage msg) {
        return msg != null && msg.isOwn()
                ? AtomChatConfig.get().bubbleTextColor
                : AtomChatConfig.get().otherBubbleTextColor;
    }

    private float panelWidth() {
        return Math.min(UiTokens.s(AtomChatConfig.get().panelWidth), vw() - 32.0F);
    }

    private float panelHeight() {
        return Math.min(UiTokens.s(AtomChatConfig.get().panelHeight), vh() - 32.0F);
    }

    // Virtual UI space: independent of vanilla GUI scale, anchored at 1080p.
    /**
     * Design density: the 1080p-anchored base times the AtomChat UI scale.
     * Every virtual-coordinate conversion in this screen funnels through here,
     * so scaling this one value scales the whole panel — fonts, icons, hit
     * rects and the blur pre-pass included — without touching UiTokens.
     */
    private float uiDensity() {
        var window = this.client.getWindow();
        float base = Math.max(1.0F, window.getHeight() / 1080.0F);
        return base * Math.max(0.5F, AtomChatConfig.get().uiScale);
    }

    private float vw() {
        return this.client.getWindow().getWidth() / uiDensity();
    }

    private float vh() {
        return this.client.getWindow().getHeight() / uiDensity();
    }

    private float panelX() {
        return UiTokens.PANEL_ANCHOR_X;
    }

    private float panelY() {
        return (vh() - panelHeight()) / 2.0F;
    }

    /**
     * The one and only way to build the layout. Rendering and every hit test go
     * through it so the input bar's animated height can never desync a click
     * from what was drawn.
     */
    private UiLayout layout() {
        float replyH = replyTarget != null ? s(34) : 0.0F;
        return UiLayout.of(panelX(), panelY(), panelWidth(), panelHeight(), inputExtraH, replyH);
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
    private UiLayout updateInputLayout(UiLayout current) {
        float lineH = inputLineHeight();
        List<String> lines = wrappedInput(current.inputTextMaxWidth());
        int targetLines = Math.min(UiTokens.INPUT_MAX_LINES, Math.max(1, lines.size()));
        float targetExtra = (targetLines - 1) * lineH;
        ScrollController activeScroll = currentScroll();
        if (Math.abs(targetExtra - inputExtraH) > 0.5F
                && activeScroll.getTarget() >= activeScroll.getMaxScroll() - 3.0F) {
            activeScroll.stickToBottom();
        }
        inputHeightAnim.animateTo(UiMotion.INPUT_GROW_MS, targetExtra);
        inputHeightAnim.update(frameDt);
        inputExtraH = inputHeightAnim.getValue();
        return layout();
    }

    private float inputLineHeight() {
        return SkiaFontRenderer.getHeight(FontManager.font(UiTokens.FONT_INPUT));
    }

    /** Wrapped input text, cached until the text or the available width changes. */
    private List<String> wrappedInput(float maxWidth) {
        String current = inputGetText();
        if (inputWrapCache == null || inputWrapWidth != maxWidth || !current.equals(inputWrapText)) {
            inputWrapText = current;
            inputWrapWidth = maxWidth;
            inputWrapCache = SkiaFontRenderer.wrap(FontManager.font(UiTokens.FONT_INPUT), current, maxWidth);
        }
        return inputWrapCache;
    }

    /**
     * Absolute index of the line holding the caret. wrap() drops the whitespace
     * at a break point, so line lengths can sum to slightly less than the full
     * text — the mapping is exact everywhere except right at a break.
     */
    private static int caretLine(List<String> lines, int caret) {
        int pos = 0;
        for (int i = 0; i < lines.size(); i++) {
            pos += lines.get(i).length();
            if (caret <= pos) {
                return i;
            }
        }
        return Math.max(0, lines.size() - 1);
    }

    private int caretIndex() {
        return input == null ? 0 : Mth.clamp(input.getCursorPosition(), 0, inputGetText().length());
    }

    /** Keeps the caret's line inside the visible window, clamping to the ends. */
    private void scrollInputToCaret(int caretLine, int totalLines) {
        int max = UiTokens.INPUT_MAX_LINES;
        if (totalLines <= max) {
            inputScrollLine = 0;
            return;
        }
        if (caretLine < inputScrollLine) {
            inputScrollLine = caretLine;
        } else if (caretLine > inputScrollLine + max - 1) {
            inputScrollLine = caretLine - max + 1;
        }
        inputScrollLine = Math.max(0, Math.min(inputScrollLine, totalLines - max));
    }

    /** Virtual-space top edge of the line the caret sits on, for IME anchoring. */
    private float caretLineTopY() {
        UiLayout l = layout();
        Font font = FontManager.font(UiTokens.FONT_INPUT);
        float lineH = SkiaFontRenderer.getHeight(font);
        List<String> lines = wrappedInput(l.inputTextMaxWidth());
        if (lines.isEmpty()) {
            return l.inputTextCenterY - lineH / 2.0F;
        }
        int line = caretLine(lines, caretIndex());
        int shown = Math.min(UiTokens.INPUT_MAX_LINES, lines.size());
        int from = Math.min(inputScrollLine, lines.size() - shown);
        int row = Math.max(0, Math.min(line - from, shown - 1));
        return l.inputTextCenterY + row * lineH - lineH / 2.0F;
    }

    private float toVirtualX(double guiX) {
        return (float) (guiX * this.client.getWindow().getGuiScale() / uiDensity());
    }

    private float toVirtualY(double guiY) {
        return (float) (guiY * this.client.getWindow().getGuiScale() / uiDensity());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }


    // ------------------------------------------------------------------ input handlers
    //
    // Ordered by the registration sequence in `inputRouter`. Each handler owns
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

    /** The avatar cropper and the colour picker are modals: while active, each owns every event. */
    private final class ModalInput implements InputHandler {
        @Override
        public boolean onClick(double mouseX, double mouseY, int button) {
            // The avatar cropper is modal: while it is open it owns every click.
            if (imageCropper.isActive()) {
                imageCropper.onClick(toVirtualX(mouseX), toVirtualY(mouseY), layout().rect());
                return true;
            }
            // So is the colour picker.
            if (colorPicker.isActive()) {
                colorPicker.onClick(toVirtualX(mouseX), toVirtualY(mouseY), layout().rect());
                return true;
            }
            return false;
        }

        @Override
        public boolean onDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
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
                        copyToClipboard(colorPicker.copyHex());
                    } else if (keyCode == GLFW_KEY_V && (modifiers & 2) != 0) {
                        colorPicker.pasteHex(AtomChatScreen.this.client.keyboardHandler.getClipboard());
                    }
                } else if (keyCode == 256) {
                    colorPicker.cancel();
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

    /** Inline numeric editor for integer sliders (retention days). */
    private final class NumberEditInput implements InputHandler {
        @Override
        public boolean onClick(double mouseX, double mouseY, int button) {
            if (!settingsSectionPage.isEditingNumber()) {
                return false;
            }
            float mx = toVirtualX(mouseX);
            float my = toVirtualY(mouseY);
            if (settingsSectionPage.isInsideNumberEditRow(mx, my)) {
                return true;
            }
            // Click outside the editor commits the typed value and lets the
            // click continue to whatever the user actually aimed at.
            settingsSectionPage.commitNumberEdit();
            return false;
        }

        @Override
        public boolean onKey(int keyCode, int scanCode, int modifiers) {
            if (!settingsSectionPage.isEditingNumber()) {
                return false;
            }
            if (keyCode == 256) { // Esc: cancel
                settingsSectionPage.cancelNumberEdit();
            } else if (keyCode == 257 || keyCode == 335) { // Enter
                settingsSectionPage.commitNumberEdit();
            } else if (keyCode == 259) { // Backspace
                settingsSectionPage.backspaceNumber();
            } else if (keyCode >= 48 && keyCode <= 57) { // top-row digits
                settingsSectionPage.appendNumberChar((char) ('0' + keyCode - 48));
            } else if (keyCode >= 320 && keyCode <= 329) { // numpad digits
                settingsSectionPage.appendNumberChar((char) ('0' + keyCode - 320));
            }
            return true;
        }

        @Override
        public boolean onChar(char chr, int modifiers) {
            // Digits are appended from onKey. Minecraft also delivers a char
            // callback for the same press; appending here would double every
            // digit (1 became 11). Swallow the char so it never reaches chat.
            return settingsSectionPage.isEditingNumber();
        }

        @Override
        public boolean onScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            return settingsSectionPage.isEditingNumber();
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
            if (contextPlayer != null || lastContextPlayer != null) {
                // Player-card menus are always the 3-row layout (profile / tp /
                // block), so the hit test must use the same row count as the
                // drawing. Two rows left "取消屏蔽" permanently unclickable.
                int rows = contextMenuRows(ContextMenuMode.PLAYER_CARD, false);
                float rowH = UiTokens.MENU_H / 2.0F;
                float menuH = rowH * rows;
                float menuW = UiTokens.MENU_W;
                float menuX = Math.min(contextX, panelX() + panelWidth() - menuW - s(8));
                float menuY = Math.min(contextY, panelY() + panelHeight() - menuH - s(8));
                boolean inside = mx >= menuX && mx <= menuX + menuW
                        && my >= menuY && my <= menuY + menuH;
                if (inside && button == 0) {
                    int row = (int) ((my - menuY) / rowH);
                    performPlayerCardAction(row);
                    closeContextMenu();
                    return true;
                }
                closeContextMenu();
            }
            if (button == 0 && overScrollbarTrack(pageLayout, mx, my, pageScroll)) {
                pageScroll.beginDrag(my);
                return true;
            }
            // A pushed settings sub-page: back arrow, then switch rows.
            if (topPage() == AppPage.SETTINGS_SECTION) {
                if (button == 0 && isBackButtonHit(mx, my)) {
                    popPage();
                    return true;
                }
                if (button == 0) {
                    int chip = settingsSectionPage.chipAt(topNav().section(), mx, my, pageLayout);
                    if (chip >= 0) {
                        settingsSectionPage.selectChip(topNav().section(), chip);
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
                        settingsSectionPage.setPressedSwatch(colorHit.plus() ? -1 : colorHit.swatchIndex());
                        if (colorHit.plus()) {
                            colorPicker.open(colorHit.color());
                        } else {
                            settingsSectionPage.applyColor(colorHit);
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
                            settingsSectionPage.perform(hit);
                            return true;
                        }
                    }
                    settingsSectionPage.disarmAction();
                }
                return true;
            }
            // A pushed profile detail: back arrow, then copyable rows/tiles.
            if (topPage() == AppPage.PROFILE_DETAIL) {
                if (button == 0 && isBackButtonHit(mx, my)) {
                    popPage();
                    return true;
                }
                if (button == 0) {
                    profilePage.onClick(mx, my, detailLayout(), detailScroll.getScrollY());
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
            if (topPage() == AppPage.CHAT_LIST) {
                ConversationListPage.RowHit hit = conversationListPage.hit(mx, my, pageLayout, pageScroll.getScrollY());
                if (hit != null) {
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
                            contextMenuMode = ContextMenuMode.PLAYER_CARD;
                            contextPlayer = hit.row().player();
                            contextX = mx;
                            contextY = my;
                            contextMessage = null;
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
            settingsSectionPage.setPressedSwatch(-1);
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
            if (chatInputSuggestor != null && chatInputSuggestor.mouseClicked((int) mouseX, (int) mouseY, button)) {
                return true;
            }

            // Back to the conversation list before any composer/emoji/message hit.
            // resetTransientWorldUi() runs inside popPage() after any slide-out
            // animation so the world page still looks intact while it leaves.
            if (button == 0 && isBackButtonHit(mx, my)) {
                popPage();
                return true;
            }

            // Feed filter cycle: only on the public page (the private header
            // has no filter slot even though the geometry exists).
            if (button == 0 && topPage() == AppPage.WORLD_CHAT && isFilterButtonHit(mx, my)) {
                cycleWorldFilter();
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
                pressButton(1);
                inputFocused = true;
                closePhrasePanel();
                emojiPanel.toggle();
                return true;
            }

            // Quick-phrase toggle: same pre-dismiss ordering as the emoji button.
            if (button == 0 && layout.phraseBtn.contains((float) mx, (float) my)) {
                pressButton(3);
                inputFocused = true;
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
                        inputAppend(inserted);
                    }
                    return true;
                }
                emojiPanel.close();
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
            ChatMessage menuBefore = contextMessage;
            ContextMenuMode menuBeforeMode = contextMenuMode;
            if (contextMessage != null) {
                float menuW = UiTokens.MENU_W;
                boolean avatarMenu = contextMenuMode == ContextMenuMode.AVATAR;
                boolean imageMessage = !avatarMenu && Cicodes.extractImageUrl(contextMessage.getRawText()) != null;
                int rows = contextMenuRows(contextMenuMode, imageMessage);
                float rowH = UiTokens.MENU_H / 2.0F;
                float menuH = rowH * rows;
                float menuX = Math.min(contextX, panelX + panelWidth() - menuW - s(8));
                float menuY = Math.min(contextY, panelY + panelHeight() - menuH - s(8));
                boolean inside = (float) mx >= menuX && (float) mx <= menuX + menuW
                        && (float) my >= menuY && (float) my <= menuY + menuH;
                if (inside && button == 0) {
                    int row = (int) ((my - menuY) / rowH);
                    if (avatarMenu) {
                        performAvatarMenuAction(row, contextMessage);
                    } else if (row == 0) {
                        copyToClipboard(contextMessage.getContentText());
                    } else if (row == 1) {
                        replyTarget = contextMessage;
                        inputFocused = true;
                        setFocused(input);
                        input.setFocused(true);
                    } else {
                        saveImage(contextMessage);
                    }
                    closeContextMenu();
                    return true;
                }
                // Any other click — including a right-click aimed at another bubble —
                // dismisses first; the handlers below may then open a new menu.
                closeContextMenu();
            }

            // Jump-to-latest bubble sits above the message list and takes priority
            // over ordinary message clicks when it is visible.
            if (button == 0 && overJumpLatest(layout, mx, my)) {
                currentScroll().scrollToBottom(true);
                return true;
            }

            // Button row: image / emoji / send share one row and one size,
            // geometry comes from UiLayout so hits can never drift from the drawing.
            if (button == 0 && layout.imageBtn.contains((float) mx, (float) my)) {
                pressButton(0);
                inputFocused = true;
                pickAndUploadImage();
                return true;
            }
            // Inserting an emoji must NOT close the panel: users often want to pick several
            // in a row. The panel still closes on any outside click or the toggle button.
            if (button == 0 && layout.sendBtn.contains((float) mx, (float) my)) {
                pressButton(2);
                if (quickPhrasePanel.isEditing()) {
                    // In phrase-edit mode the send button commits the phrase.
                    endPhraseEdit(true);
                    return true;
                }
                sendMessage(inputGetText());
                return true;
            }

            // Input box focus
            inputFocused = false;
            dismissSuggestor();
            if (button == 0 && layout.inputBar.contains((float) mx, (float) my)) {
                inputFocused = true;
                setFocused(input);
                input.setFocused(true);
                // Click-to-position / drag-select on the multi-line Skia input:
                // map the virtual point to a text index ourselves (the hidden
                // EditBox geometry is caret-shifted and single-line, useless here).
                int idx = inputCaretIndexAt(layout, (float) mx, (float) my);
                if (hasShiftDown() && inputDragAnchor >= 0) {
                    input.setHighlightPos(idx);
                } else {
                    inputDragAnchor = idx;
                    input.moveCursorTo(idx, false);
                }
                inputDragging = true;
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
                pendingClickSpan = messageListView.clickableSpanAt(mx, my).orElse(null);
                pendingClickMoved = false;
            }

            // Message interactions. Left avatar click only arms the double-click
            // poke; single-click @ is deliberately gone (use the right-click menu).
            // Right-click opens the bubble menu only when the pointer is actually on
            // the bubble; right-click on a real player's avatar opens the avatar menu.
            for (MessageListView.MessageHit hit : messageListView.hits()) {
                if (my < hit.y() || my > hit.bottom()) {
                    continue;
                }
                // Row press bounce: arm on a left press inside the bubble.
                if (button == 0
                        && mx >= hit.bubbleX() && mx <= hit.bubbleX() + hit.bubbleWidth()
                        && my >= hit.bubbleY() && my <= hit.bubbleBottom()) {
                    messageListView.setPressedIndex(hit.index());
                }
                if (button == 1 && hit.avatarSize() > 0F
                        && mx >= hit.avatarX() && mx <= hit.avatarX() + hit.avatarSize()
                        && my >= hit.avatarY() && my <= hit.avatarY() + hit.avatarSize()
                        && canOpenAvatarMenu(hit.message())) {
                    // Right-clicking the avatar the avatar menu is already on closes it.
                    if (menuBefore == hit.message() && menuBeforeMode == ContextMenuMode.AVATAR) {
                        return true;
                    }
                    contextMenuMode = ContextMenuMode.AVATAR;
                    contextMessage = hit.message();
                    contextX = mx;
                    contextY = my;
                    return true;
                }
                if (button == 1 && !hit.message().isSystem()
                        && mx >= hit.bubbleX() && mx <= hit.bubbleX() + hit.bubbleWidth()
                        && my >= hit.bubbleY() && my <= hit.bubbleBottom()) {
                    // Right-clicking the bubble the menu is already on closes it.
                    if (menuBefore == hit.message()) {
                        return true;
                    }
                    contextMenuMode = ContextMenuMode.BUBBLE;
                    contextMessage = hit.message();
                    contextX = mx;
                    contextY = my;
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
                            pendingClickSpan = messageListView.clickableSpanAt(mx, my).orElse(null);
                            pendingClickMoved = false;
                            messageListView.beginSelection(hit, line, mx);
                            return true;
                        }
                    }
                }
                if (button == 0 && hit.avatarSize() > 0F
                        && mx >= hit.avatarX() && mx <= hit.avatarX() + hit.avatarSize()
                        && my >= hit.avatarY() && my <= hit.avatarY() + hit.avatarSize()) {
                    long now = System.currentTimeMillis();
                    boolean pokeEnabled = Animations.avatarPoke() && Animations.enabled();
                    boolean doubleClick = pokeEnabled
                            && lastAvatarClickIndex == hit.index()
                            && now - lastAvatarClickTime < AVATAR_CLICK_WINDOW_MS;
                    if (doubleClick) {
                        // Double click → poke; cancels the pending single click.
                        lastAvatarClickTime = 0;
                        pendingAvatarClickMessage = null;
                        messageListView.poke(hit.index(), now);
                    } else if (pokeEnabled) {
                        // QQ-style competition window: the single click waits for
                        // the double-click threshold before opening the profile.
                        lastAvatarClickTime = now;
                        lastAvatarClickIndex = hit.index();
                        pendingAvatarClickTime = now;
                        pendingAvatarClickIndex = hit.index();
                        pendingAvatarClickMessage = hit.message();
                    } else {
                        // With poke (or decorative motion) off a double click does
                        // nothing, so there is nothing to compete with: jump at once.
                        lastAvatarClickTime = 0;
                        openProfileFor(hit.message());
                    }
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
            if (inputDragging && button == 0 && input != null) {
                int idx = inputCaretIndexAt(layout(), toVirtualX(mouseX), toVirtualY(mouseY));
                input.setHighlightPos(idx);
                return true;
            }
            // Any drag while a click is pending must suppress the click-on-release,
            // including drags that do not start a text selection (e.g. name bars).
            if (button == 0 && pendingClickSpan != null) {
                pendingClickMoved = true;
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
            if (button == 0) {
                messageListView.setPressedIndex(-1);
                float mx = toVirtualX(mouseX);
                float my = toVirtualY(mouseY);
                boolean wasSelecting = messageListView.isSelecting();
                ClickableSpan pending = pendingClickSpan;
                pendingClickSpan = null;
                ClickableSpan released = messageListView.clickableSpanAt(mx, my).orElse(null);
                boolean shouldClick = pending != null && !pendingClickMoved
                        && pending.style().getClickEvent() != null
                        && pending.equals(released);
                if (wasSelecting) {
                    messageListView.endSelection();
                }
                pendingClickMoved = false;
                if (inputDragging) {
                    inputDragging = false;
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
            if (chatInputSuggestor != null && chatInputSuggestor.mouseScrolled(verticalAmount)) {
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
                    client.keyboardHandler.setClipboard(copied);
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
            if (chatInputSuggestor != null && chatInputSuggestor.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter
                if (inputFocused) {
                    sendMessage(inputGetText());
                }
                return true;
            }
            // Up/Down become caret navigation as soon as the text wraps onto a second
            // line (>= INPUT_MAX_LINES). Once multiline, Up/Down never fall back to
            // vanilla chat history — that remains a single-line behaviour.
            if (inputFocused && input != null && (keyCode == 265 || keyCode == 264)) {
                List<String> lines = wrappedInput(layout().inputTextMaxWidth());
                if (lines.size() >= UiTokens.INPUT_MAX_LINES) {
                    int caret = caretIndex();
                    int row = caretLine(lines, caret);
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
            if (inputFocused && AtomChatConfig.get().debug) {
                AtomChat.LOGGER.info("keyPressed: {} (sc {}) mod {}", keyCode, scanCode, modifiers);
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
                AtomChat.LOGGER.info("charTyped: '{}' (U+{}) focused={} field={}",
                        chr, Integer.toHexString(chr), inputFocused, input != null && input.isFocused());
            }
            return false;
        }
    }
}
