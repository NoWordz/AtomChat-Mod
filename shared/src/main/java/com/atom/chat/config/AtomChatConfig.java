package com.atom.chat.config;

import com.atom.chat.AtomChat;
import com.atom.chat.chat.MessageFilter;
import com.atom.chat.ui.ClickRipples;
import com.atom.lib.design.atomchat.Design;
import com.atom.lib.design.atomchat.DesignHost;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AtomChatConfig {
    public static final AtomChatConfig DEFAULT = new AtomChatConfig();

    /**
     * Feeds this mod's live appearance settings to the shared AtomChat design
     * package, so the extracted tokens keep painting exactly what they did
     * before they moved out.
     */
    static {
        Design.host(new DesignHost() {
            @Override
            public float cornerRadius() {
                return AtomChatConfig.get().cornerRadius;
            }

            @Override
            public boolean halfScreenMode() {
                return AtomChatConfig.get().halfScreenMode;
            }

            @Override
            public int accentColor() {
                return AtomChatConfig.get().accentColor;
            }

            @Override
            public int textSecondaryColor() {
                return AtomChatConfig.get().textSecondaryColor;
            }

            @Override
            public float cardTint() {
                return AtomChatConfig.get().cardTint;
            }

            @Override
            public int cardColor() {
                return AtomChatConfig.get().cardColor;
            }

            @Override
            public int panelBackgroundColor() {
                return AtomChatConfig.get().panelBgColor;
            }
        });
        Design.rippleSink(ClickRipples::region);
    }

    /** Whether AtomChat replaces the vanilla chat screen. */
    public boolean enabled = true;
    /** Multiplier for the panel's existing Kawase sampling offsets. */
    public float blurStrength = 2.0F;

    public float effectiveBlurStrength() {
        return Float.isFinite(blurStrength) ? Math.max(0.25F, Math.min(3.0F, blurStrength)) : 2.0F;
    }

    public float panelWidth = 480.0F;
    /** Local layout preferences, independent of appearance presets. */
    public boolean halfScreenMode = false;
    public float halfScreenWidth = 600.0F;
    /**
     * Whether {@link #halfScreenWidth} holds a width the player actually picked.
     * Only then does half-screen mode read it; until then
     * {@link #activePanelWidth()} answers the documented 600 default. Live files
     * carried the phone width in this field even when half-screen was never
     * customised, so switching modes drew the exact same 480px panel — the
     * marker is what tells "chosen" apart from "left over from the phone mode".
     */
    public boolean halfScreenWidthCustomized = false;
    public float panelHeight = 860.0F;
    /** Relative position inside the available viewport; old configurations keep their docked anchor. */
    public boolean panelPositionCustom = false;
    public float panelPositionX = 0F;
    public float panelPositionY = 0.5F;
    /** Rounded panel background blur (raw GL + core shader, outside Skia). */
    public boolean blurEnabled = true;
    /**
     * Master switch for decorative motion: message entrance, page/tab push,
     * the panel open slide, the avatar poke shake and scroll snapping.
     * Functional feedback (hover tint, wheel glide) is deliberately kept even
     * when this is off, so the UI never stops responding to the pointer.
     */
    public boolean animationEnabled = true;
    /** QQ-style horizontal slide + fade when a message first enters the viewport. */
    public boolean messageEntryAnimation = true;
    /** Close the panel through its normal exit animation after a successful
     *  public/private/command send. Blank, blocked or image-only composer
     *  actions never reach the successful cleanup path, so they stay open.
     *  Defaults off: sending repeatedly is the common case. */
    public boolean closeAfterSend = false;
    /** Retain unsent composer text per public feed/private conversation while
     *  moving between chat screens in the current connection. Memory only:
     *  JOIN/DISCONNECT clears it. Disabling stops cross-screen restoration;
     *  the composer currently on screen keeps its text. */
    public boolean retainDraftOnClose = true;
    /**
     * Page transition style for the nav push/pop. {@code SLIDE} keeps the
     * horizontal push, geometry-symmetric with every pop. {@code ZOOM} runs
     * Melodify's serial two-phase zoom: the leaving page fades out while
     * shrinking 1.0 -> 0.9, then the arriving page fades in while settling
     * 1.1 -> 1.0 — the phases never overlap into a double-exposure, and a
     * pop swaps the two pages' roles while keeping the same phase order, so
     * a return matches its push (single clock, see
     * {@code UiMotion.PAGE_NAV_TAU_MS}). Every hop follows this setting — the
     * root tabs, root<->detail and detail<->detail (public<->private,
     * chat<->profile) alike, in both directions — and no page kind carries an
     * animation rule of its own. Gson persists the enum
     * by name; the retired boolean toggle's key is simply dropped by the
     * next save, no migration needed.
     */
    public PageNavStyle pageNavStyle = PageNavStyle.ZOOM;

    /** Nav transition styles, read by {@link #pageNavStyle}. */
    public enum PageNavStyle { SLIDE, ZOOM }

    /** Local view preference for the public feed; never changes stored messages or unread counts. */
    public MessageFilter worldFeedFilter = MessageFilter.ALL;

    public MessageFilter effectiveWorldFeedFilter() {
        return worldFeedFilter == null ? MessageFilter.ALL : worldFeedFilter;
    }

    /** Double-clicking another player's avatar performs the QQ-style poke shake. */
    public boolean avatarPokeEnabled = true;
    /** Local display preference; custom-avatar data is never changed by this choice. */
    public enum AvatarStyle { ATOMCHAT, E33CHAT }
    public AvatarStyle avatarStyle = AvatarStyle.ATOMCHAT;

    public AvatarStyle effectiveAvatarStyle() {
        return avatarStyle == null ? AvatarStyle.ATOMCHAT : avatarStyle;
    }

    public enum BubbleStyle { CLASSIC, ROUND, CORNER }
    public BubbleStyle bubbleStyle = BubbleStyle.CORNER;

    public BubbleStyle effectiveBubbleStyle() {
        return bubbleStyle == null ? BubbleStyle.CORNER : bubbleStyle;
    }
    /**
     * When true, public messages from blocked players are dropped at capture
     * time. When false they are shown normally — the conversation card stays
     * greyed out and private chat stays read-only either way, so a block
     * always silences direct contact.
     */
    public boolean hideBlockedMessages = true;
    /**
     * Panel background opacity, 0..1. Applied to the solid fallback colour and
     * to the blur tint alike, so one slider governs how much world shows
     * through regardless of whether blur is on.
     */
    public float panelOpacity = 0.6990596F;
    /**
     * AtomChat-only UI scale multiplier on top of the vanilla GUI scale. It is
     * applied at the Skia canvas and in every coordinate conversion, never to
     * {@link com.atom.lib.design.atomchat.UiTokens} — those constants are class-init
     * statics and could not change at runtime.
     */
    public float uiScale = 1.0F;
    /**
     * Content-only zoom for the two-axis panel sizing: multiplies the UI
     * density inside the panel (text, rows, paddings) while the panel's outer
     * frame keeps its pixel size — the shell divides the panel's virtual size
     * by this factor to compensate, so bigger content never grows the frame.
     * Unlike {@link #uiScale} it never touches the canvas transform; it is
     * clamped to the slider span on load (see {@link #clampContentScale()}).
     */
    public float contentScale = 1.0F;
    /** Dumps avatar sampling PNGs to {@code <gameDir>/atomchat/cache/debug/} for color debugging. */
    public boolean debug = false;
    /**
     * 「四档归零对照」诊断实验兜底档位："A"=完整（产品默认，每帧新鲜毛玻璃）、
     * "B"=只跳背景模糊 pre-pass、"C"=整个 drawPanel 旁路、
     * "D"=Muonium 时序策略实验档（背景采集/模糊 30Hz 限频、非刷新帧复用上一代结果纹理）。
     * 仅当 debug=on 时生效；debug=off / 全新安装 / 未按键时恒等于 A，产品视觉逐字节不变。
     * 非法值回退 A（见 Truth3Probe.parseMode）。
     */
    public String diagnosticRenderMode = "A";
    /**
     * E0 仪表标定：{@code debug=on && diagnosticMeasureOnly=true} 时 {@code Truth3Probe}
     * 的帧探针（{@code frame_probe} / {@code frame_pump}）照常输出，但
     * {@code FrameProfile}（面板/解码/塑形分桶 + JFR）、{@code FrameSegments}
     * 与 {@code PrepassProbe}（背景模糊 pre-pass 分段）全部关闭，用来确认
     * 0.1–0.3ms 级差异不是诊断代码自身造成的。
     *
     * <p>默认 <b>false</b>：门控式子是 {@code debug && !diagnosticMeasureOnly}，
     * 因此默认值下 debug=off / debug=on 的既有行为都逐字节不变。只在 debug=on 时
     * 有意义；不加热键，仅配置文件字段（与 {@link #diagnosticRenderMode} 同一做法）。
     * 该开关<strong>不</strong>改 {@code errorCheckDue()} 在 debug=on 时恒 true 的既有行为。
     */
    public boolean diagnosticMeasureOnly = false;
    /** [Mode-D 子项桩，默认关、本轮未实现] 半径槽（Muonium GlassBlurLevels 对应）。 */
    public boolean modeDBlurRadiusSlots = false;
    /** [Mode-D 子项桩，默认关、本轮未实现] 旧代借用（GlassBackdrop 落后 8 个采集代对应）。 */
    public boolean modeDStaleGenerationBorrow = false;
    /** [Mode-D 子项桩，默认关、本轮未实现] 开合动画期实色（GlassLook.java:151 对应）。 */
    public boolean modeDOpaqueDuringAnimation = false;
    public int accentColor = 0xFF4A90E2;
    public int bubbleTextColor = 0xFFFFFFFF;
    public int ownBubbleColor = 0xFF1E90FF;
    public int otherBubbleColor = 0xFF1E293B;
    public int panelBgColor = 0xFF101418;
    public int textPrimaryColor = 0xFFFFFFFF;
    public int textSecondaryColor = 0xE8B7BDCA;
    /**
     * Card/chrome surface colour. Card tint multiplies its alpha from 0 to 255;
     * the factory slate surface uses a 50% tint over the cool dark panel.
     */
    public int cardColor = 0xFF14161B;
    /** Global blocked-player real names, persisted locally. */
    public java.util.List<String> blockedPlayers = new java.util.ArrayList<>();
    /**
     * This client's profile signature (QQ 个性签名), shown centred under the
     * name on the own profile page and edited inline there (Enter commits,
     * Esc cancels). Local-only: it is never synced to other clients, so other
     * players' profiles show the placeholder line instead. Empty = unset.
     */
    public String playerSignature = "";
    /** Explicit empty edits must sync; a fresh client should not erase server data. */
    public boolean profileSignatureEdited;
    public boolean profileAvatarCleared;
    public boolean profileBannerCleared;
    public boolean profileSignatureMigrationDone;

    /**
     * Master switch for the custom profile banner
     * ({@code <gameDir>/atomchat/banner.png}). When false the banner file, even
     * if present, is ignored and the profile falls back to the accent
     * gradient. The file itself is the everyday on/off — deleting it turns the
     * custom banner off the same way; this flag exists so the image can be
     * parked without losing it.
     */
    public boolean customBannerEnabled = true;

    /**
     * Client-side chat templates (e33chat parity), e.g. {@code "<{name}> {content}"}.
     * Empty = disabled; the guards keep their current behaviour. Placeholders:
     * {name} {display_name} {prefix} {suffix} {sep} {content} (exactly one
     * {content}, any position). Hand-edit this file; templates are re-read when
     * a chat screen opens.
     */
    public java.util.List<String> chatTemplates = new java.util.ArrayList<>();
    /**
     * Same syntax as {@link #chatTemplates} but claims incoming private-message
     * lines (plugin-reformatted {@code /msg}) into the private panel.
     */
    public java.util.List<String> whisperTemplates = new java.util.ArrayList<>();
    /**
     * Teleport command for the avatar/player menu: {@code auto} (probe the
     * server command tree for a tpa-family command, fall back on failure),
     * {@code tp} or {@code tpa}.
     */
    public String teleportCommandMode = "auto";
    /**
     * Corner radius for the surrounding chrome (panel, cards, pills, popups —
     * chat bubbles excluded on purpose), in 1080p-basis pixels: the value
     * scales with the whole UI density (vanilla scale × uiScale ×
     * contentScale), so it is not pinned to physical pixels. {@code 20} is
     * the shipped default (factor 1, cards round at s(16)), {@code 0} means
     * square corners on every surface, the slider tops out at {@code s(20)}.
     * Replaces the old three-step {@code cornerStyle}, which lives on only
     * for the one-time file migration below and is never read or written
     * afterwards. Presets and the factory-default reset deliberately leave
     * this knob alone.
     */
    public float cornerRadius = 25f;
    /**
     * Legacy three-step corner style ({@code large}/{@code medium}/
     * {@code small}). Migration source only: {@link #load()} folds it into
     * {@link #cornerRadius} once and nulls it, so it is neither read nor
     * written again (Gson omits nulls from the saved file).
     */
    public String cornerStyle = null;
    /**
     * Whether {@code cornerRadius} is already on the 1080p-basis scale. A file
     * written before that scale existed carries no such key, so Gson leaves
     * this at {@code false} — the marker for "legacy, still needs
     * {@link #migratePixelRadius()}". A fresh config is stamped true on its way
     * out of {@link #load()}, so only a pre-1080p file can arrive here unset.
     * Value-based detection cannot do the job: the old scale's span overlaps
     * the new one, so a stored 20 or 10 is a legal value under both readings.
     */
    public boolean pixelRadiusMigrated = false;

    /**
     * One-time migration of an old config file: the three-step style maps
     * onto the continuous radius (large=28 / medium=14 / small=10), then the
     * legacy field is dropped. Idempotent by the null check — a config that
     * already migrated (or was never legacy) is untouched.
     */
    void migrateCornerStyle() {
        if (cornerStyle == null) {
            return;
        }
        cornerRadius = switch (cornerStyle) {
            case "medium" -> 14f;
            case "small" -> 10f;
            default -> 28f;
        };
        cornerStyle = null;
    }

    /**
     * Migration of the radius semantics: the knob used to be on the
     * reference-px scale (old default {@code 28}, factor radius/28) and is now
     * in 1080p-basis pixels (factor radius/20). The scales differ by
     * 28 / 20 = 1.4, so the stored value is divided by it — that preserves the
     * drawn FACTOR for every stored value, not just the default: 28 lands on
     * 20, the legacy "medium" 14 on 10 and "small" 10 on ~7.14, and the old
     * slider's whole span 0..s(28)=35 lands exactly on the new 0..s(20)=25.
     * Rescaling only the 28 default would silently thicken every hand-dragged
     * radius by 40% (a dragged 14 would read factor 0.7 instead of 0.5).
     *
     * <p>Guarded by {@link #pixelRadiusMigrated}, not by the value: dividing
     * is not idempotent over the span the two scales share, and {@link #load()}
     * runs this again on every screen open. Runs after
     * {@link #migrateCornerStyle()} so a legacy "large" file is on the old
     * scale by the time it gets here. Values past the new slider span (0..s(20))
     * still clamp into it, for hand-edited files.</p>
     */
    void migratePixelRadius() {
        if (!pixelRadiusMigrated) {
            cornerRadius /= 1.4F;
            pixelRadiusMigrated = true;
        }
        cornerRadius = Math.max(0.0F, Math.min(cornerRadius,
                com.atom.lib.design.atomchat.UiTokens.s(20)));
    }
    /**
     * Clamps {@link #contentScale} into the slider span [0.8, 1.5], the same
     * range the shell's virtual-size compensation assumes — a hand-edited
     * config file must not push the content zoom outside it. Runs in
     * {@link #load()} right after {@link #migratePixelRadius()}.
     */
    void clampContentScale() {
        contentScale = Math.max(0.8F, Math.min(contentScale, 1.5F));
    }
    /**
     * Migration for files written before {@link #halfScreenWidthCustomized}
     * existed: they carry no key, Gson leaves the marker false, and a genuine
     * custom half-screen width would silently drop back to 600. A stored
     * half-screen width that differs from the phone width is such a choice; one
     * equal to the phone width is the old defect (both modes drew the same
     * panel) and stays uncustomised so the 600 default applies. Idempotent by
     * the key test {@link #load()} takes from the raw JSON — value-based
     * detection alone cannot tell a chosen 600 from the seeded default.
     */
    void migrateHalfScreenWidth(boolean keyPresent) {
        if (keyPresent) {
            return;
        }
        halfScreenWidthCustomized = Math.abs(halfScreenWidth - panelWidth) > 0.01F;
    }
    /**
     * Card/chrome surface tint, 0..1. At 0 the surfaces are the frosted
     * translucent white washes; at 1 they are opaque tints lifted from the
     * panel colour. In between they slide through a semi-transparent grey —
     * one axis, three visual stops.
     */
    public float cardTint = 0.59866506F;
    /** Panel outline (bezel ring) colour. */
    public int panelOutlineColor = 0xFF14161B;
    /**
     * White phone-style bezel ring around the panel. Part of the frosted look;
     * the opaque modern preset turns it off.
     */
    public boolean panelOutline = true;
    /** Width in unscaled UI logical pixels. Zero disables the outline. */
    public float panelOutlineWidth = 4.0F;
    /** Only the genuinely missing-config path arms the first wallpaper. */
    public boolean wallpaperFirstUsePending = false;

    public float effectivePanelOutlineWidth() {
        if (!panelOutline) return 0F;
        return Float.isFinite(panelOutlineWidth) ? Math.max(0F, Math.min(6F, panelOutlineWidth)) : 4F;
    }

    public void setPanelOutlineWidth(float width) {
        panelOutlineWidth = Float.isFinite(width) ? Math.max(0F, Math.min(6F, width)) : 4F;
        panelOutline = panelOutlineWidth > 0F;
    }

    public void migrateOutlineWidth(boolean widthKeyPresent) {
        if (!widthKeyPresent) panelOutlineWidth = panelOutline ? 2F : 0F;
        setPanelOutlineWidth(effectivePanelOutlineWidth());
    }
    /**
     * Body/quote text colour inside <em>other</em> players' bubbles. Own
     * bubbles keep {@link #bubbleTextColor}.
     */
    public int otherBubbleTextColor = 0xFFFFFFFF;
    /**
     * Shared capsule family for system messages, time dividers and the quote
     * pill. Defaults to the shipped translucent dark capsule look; the alpha
     * lives in the value itself so each consumer needs no hidden multiplier.
     */
    public int secondaryCapsuleBg = 0x962C3E50;
    public int secondaryCapsuleText = 0xDCAAAABA;
    /**
     * Last applied built-in theme ({@code frosted} / {@code modern}); empty
     * until the user picks one. Display only — the single knobs stay editable
     * after a preset lands, so this never gates any behaviour.
     */
    public String themeName = "";
    /**
     * When true only an explicit {@code @Name} counts as a mention; when
     * false the bare name as a standalone token counts too (e33chat default).
     */
    public boolean mentionRequireAt = false;
    /**
     * Whether incoming image messages are fetched and rendered. Off: every
     * image message shows the green [图片] placeholder instead — nothing is
     * downloaded or cached (saving one by hand still fetches on demand).
     */
    public boolean imageMessagesEnabled = true;
    /**
     * Time divider between messages, in minutes; 0 disables the divider. A
     * pill with the clock time is drawn above a message when this many minutes
     * have passed since the previous one.
     */
    public int timestampIntervalMinutes = 5;
    /**
     * Merge consecutive identical messages from the same sender into one bubble
     * with a repeat counter (e33chat anti-spam). System lines never merge.
     */
    public boolean antiSpamEnabled = false;
    /**
     * Render consecutive messages from the same sender within five minutes as a
     * compact group: only the first keeps the avatar/name row, later bubbles get
     * a tighter gap (Discord/Telegram style).
     */
    public boolean compactMessagesEnabled = true;
    /** Same notification queue on HUD and in the panel; only the HUD anchor is configurable. */
    public com.atom.chat.notification.NotificationHudLayout.Anchor notificationHudPosition =
            com.atom.chat.notification.NotificationHudLayout.Anchor.TOP_CENTER;
    public float notificationHudOffsetX = 0;
    public float notificationHudOffsetY = 0;
    /** Uniform HUD-only size multiplier; the panel keeps its own geometry. */
    public float notificationHudScale = 1;
    /**
     * Skia banner when someone @mentions or quotes you, shared between
     * the game HUD and the open AtomChat panel with one expiry time.
     */
    public boolean mentionBannerEnabled = true;
    /** Sound when someone @mentions or quotes you. */
    public boolean mentionSoundEnabled = true;
    /** Skia banner when someone sends you a private / whisper message. */
    public boolean whisperBannerEnabled = true;
    /** Sound when someone sends you a private / whisper message. */
    public boolean whisperSoundEnabled = true;
    /** Master volume multiplier for all AtomChat notification sounds (0..1). */
    public float notifyVolume = 1.0F;
    public boolean notificationSoundEnabled = true;
    public boolean feedbackSoundEnabled = true;
    public float feedbackVolume = 1.0F;
    public boolean feedbackButtonEnabled = true;
    public boolean feedbackSendEnabled = true;
    public boolean feedbackPanelEnabled = true;
    public boolean funEffectsEnabled = true;
    public boolean experimentalEffectsEnabled = false;
    /**
     * User-defined quick phrases, shown in the chat composer's phrase panel.
     * Clicking one inserts it into the input box (never sends it straight
     * away), so it can still be edited before hitting enter. Managed in-game
     * from the panel; hand-editing this array works too.
     */
    public java.util.List<String> quickPhrases = new java.util.ArrayList<>();
    /**
     * Whether the one-time {@link #GUI_TIP} entry has been seeded into
     * {@link #quickPhrases}. Seeded once so a host finds the server screen
     * without reading the readme, and never again once they delete it.
     */
    public boolean guiTipSeeded = false;
    /** Default first quick phrase: the command that opens the server settings. */
    public static final String GUI_TIP = "/atomchat gui";
    /** Hard cap for {@link #quickPhrases}; the panel refuses additions past it. */
    public static final int MAX_QUICK_PHRASES = 20;
    /** Matches Minecraft's chat limit — a longer phrase could never be sent. */
    public static final int MAX_QUICK_PHRASE_LENGTH = 256;

    /**
     * Whether this client accepts the emote pack, phrases and server identity a
     * server offers on join (0.2.9). Off means no request is ever sent and
     * nothing is written; the Settings -> Privacy switch controls this.
     */
    public boolean serverPacksEnabled = true;
    /**
     * How many stickers the player may keep in their own emote folder (the
     * "+" slot in the emote panel). 0 — the default — keeps every sticker
     * they add; a positive value caps the folder and greys the "+" slot out
     * once it is full. Server-pack emotes are a read-only mirror with their
     * own cap and never count against this.
     */
    public int emoteMax = 0;
    /**
     * Whether chat history is kept per world on disk under
     * {@code <gameDir>/atomchat/client/history/} and restored when you rejoin that
     * world. Off by default: chat (including private conversations) is written
     * as plain JSONL, so this is an explicit opt-in rather than a surprise.
     */
    public boolean chatHistoryEnabled = false;
    /**
     * Deletes history files older than this many days when a world is joined.
     * 0 keeps them forever. Defaults to a week — history is an opt-in feature,
     * and when it is on the JSONL files should not grow unbounded.
     * The Settings → Chat slider controls this.
     */
    public int historyRetentionDays = 7;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AtomChatConfig instance;

    /**
     * The width the panel actually opens at, per mode. Half-screen only honours
     * {@link #halfScreenWidth} once the player customised it (see
     * {@link #halfScreenWidthCustomized}); otherwise it is the 600 default the
     * lang file documents, so switching modes always changes something.
     * Malformed values use that mode's own default.
     */
    public float activePanelWidth() {
        if (halfScreenMode) {
            float width = halfScreenWidthCustomized ? halfScreenWidth : 600.0F;
            return Float.isFinite(width) && width > 0.0F ? width : 600.0F;
        }
        float width = panelWidth;
        return Float.isFinite(width) && width > 0.0F ? width : 480.0F;
    }

    /**
     * Writes the active mode's width. In half-screen mode this is a deliberate
     * choice, so it also marks the half-screen width customised — a slider drag
     * is exactly the evidence that separates it from the phone width.
     */
    public void setActivePanelWidth(float width) {
        if (halfScreenMode) {
            halfScreenWidth = width;
            halfScreenWidthCustomized = true;
        } else {
            panelWidth = width;
        }
    }

    public static AtomChatConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /**
     * Re-reads the config file. Called when a chat screen opens so hand-edited
     * templates take effect without a game restart.
     */
    public static void reload() {
        long token = com.atom.chat.diagnostics.FrameProfile.screenStage("CONFIG_RELOAD");
        boolean ok = false;
        try {
            instance = load();
            ok = true;
        } finally {
            // Covers the synchronous config file read on every screen open, on all three loaders.
            com.atom.chat.diagnostics.FrameProfile.screenStageEnd("CONFIG_RELOAD", token, ok);
        }
    }

    /**
     * Puts {@link #GUI_TIP} into a config that has never seen it, exactly once:
     * after that the flag is saved, so a player who deletes the entry keeps it
     * deleted across restarts.
     */
    private static void seedGuiTip(AtomChatConfig config) {
        if (config.guiTipSeeded) {
            return;
        }
        config.guiTipSeeded = true;
        if (config.quickPhrases == null) {
            config.quickPhrases = new java.util.ArrayList<>();
        }
        if (!config.quickPhrases.contains(GUI_TIP)) {
            config.quickPhrases.add(GUI_TIP);
        }
    }

    /**
     * pageNavStyle is the config's first enum field, and Gson throws on an
     * unknown enum name — one hand-edited "slide" (lowercase) would fail the
     * whole load(), fall back to a fresh default instance and immediately
     * overwrite the file, wiping every saved option with it. Normalizing the
     * value in the JSON tree first keeps a bad edit scoped to the one key.
     */
    /**
     * Whether the raw file already carries {@code key}. The "absent means
     * legacy" migrations need this because Gson cannot tell an absent boolean
     * from a stored {@code false} — and a stored false is a real answer.
     */
    private static boolean hasKey(String json, String key) {
        try {
            com.google.gson.JsonObject obj = GSON.fromJson(json, com.google.gson.JsonObject.class);
            return obj != null && obj.has(key);
        } catch (RuntimeException e) {
            // Unparseable JSON is the caller's own failure path; treat it as
            // "no key" so a legacy-shaped migration still runs.
            return false;
        }
    }

    private static String sanitizePageNavStyle(String json) {
        try {
            com.google.gson.JsonObject obj = GSON.fromJson(json, com.google.gson.JsonObject.class);
            if (obj == null || !obj.has("pageNavStyle")
                    || !obj.get("pageNavStyle").isJsonPrimitive()) {
                return json;
            }
            String raw = obj.get("pageNavStyle").getAsString();
            obj.addProperty("pageNavStyle", PageNavStyle.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)).name());
            return obj.toString();
        } catch (Exception e) {
            // Not JSON, or an unrecognizable style: drop the key so the load
            // proceeds with the field default instead of failing entirely.
            try {
                com.google.gson.JsonObject obj = GSON.fromJson(json, com.google.gson.JsonObject.class);
                if (obj != null) {
                    obj.remove("pageNavStyle");
                    return obj.toString();
                }
            } catch (Exception ignored) {
                // Fall through: the caller's catch logs it and uses defaults.
            }
            return json;
        }
    }

    /** Keep malformed avatar-style values from discarding the rest of the config. */
    private static String sanitizeAvatarStyle(String json) {
        com.google.gson.JsonObject obj = GSON.fromJson(json, com.google.gson.JsonObject.class);
        if (obj != null && obj.has("avatarStyle")) {
            try {
                String raw = obj.get("avatarStyle").getAsString();
                obj.addProperty("avatarStyle",
                        AvatarStyle.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)).name());
            } catch (RuntimeException ignored) {
                obj.remove("avatarStyle");
            }
            return obj.toString();
        }
        return json;
    }

    private static String sanitizeBubbleStyle(String json) {
        com.google.gson.JsonObject obj = GSON.fromJson(json, com.google.gson.JsonObject.class);
        if (obj != null && obj.has("bubbleStyle")) {
            try {
                String raw = obj.get("bubbleStyle").getAsString();
                obj.addProperty("bubbleStyle", BubbleStyle.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)).name());
            } catch (RuntimeException ignored) {
                obj.remove("bubbleStyle");
            }
            return obj.toString();
        }
        return json;
    }

    private static String sanitizeWorldFeedFilter(String json) {
        com.google.gson.JsonObject obj = GSON.fromJson(json, com.google.gson.JsonObject.class);
        if (obj != null && obj.has("worldFeedFilter")) {
            try {
                String raw = obj.get("worldFeedFilter").getAsJsonPrimitive().getAsString();
                obj.addProperty("worldFeedFilter",
                        MessageFilter.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)).name());
            } catch (RuntimeException ignored) {
                obj.remove("worldFeedFilter");
            }
            return obj.toString();
        }
        return json;
    }

    private static AtomChatConfig load() {
        com.atom.chat.util.CacheDirs.migrateFromOldConfigPaths();
        Path path = com.atom.chat.platform.Platform.configDir().resolve("atomchat/atomchat-client.json");
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                boolean halfWidthKeyPresent = hasKey(json, "halfScreenWidthCustomized");
                AtomChatConfig config = GSON.fromJson(sanitizePageNavStyle(sanitizeAvatarStyle(
                        sanitizeBubbleStyle(sanitizeWorldFeedFilter(json)))), AtomChatConfig.class);
                if (config != null) {
                    boolean personalLoaded = PersonalDataStore.load(config);
                    if (config.blockedPlayers == null) {
                        config.blockedPlayers = new java.util.ArrayList<>();
                    }
                    config.avatarStyle = config.effectiveAvatarStyle();
                    config.bubbleStyle = config.effectiveBubbleStyle();
                    config.worldFeedFilter = config.effectiveWorldFeedFilter();
                    config.blurStrength = config.effectiveBlurStrength();
                    config.migrateOutlineWidth(hasKey(json, "panelOutlineWidth"));
                    config.migrateCornerStyle();
                    config.migratePixelRadius();
                    config.clampContentScale();
                    config.migrateHalfScreenWidth(halfWidthKeyPresent);
                    config.notifyVolume = com.atom.chat.audio.FeedbackSoundPolicy.volume(true,true,config.notifyVolume);
                    config.feedbackVolume = com.atom.chat.audio.FeedbackSoundPolicy.volume(true,true,config.feedbackVolume);
                    // Write the merged instance straight back: options added in
                    // newer builds are absent from an older file, and Gson drops
                    // unknown keys — so without this a new option could never be
                    // switched on by editing the file, it simply would not be
                    // there. Existing values are preserved by the round trip.
                    seedGuiTip(config);
                    if (personalLoaded) save(config);
                    return config;
                }
            } catch (Exception e) {
                AtomChat.LOGGER.error(com.atom.chat.diagnostics.AtomChatLog.message("config", "Failed to load AtomChat config, using defaults"), e);
            }
        }
        AtomChatConfig config = new AtomChatConfig();
        // A brand-new config is already on the 1080p-basis scale, so it must be
        // stamped as migrated before it is written — otherwise the field's own
        // "unset means legacy" default would make the next load() divide the
        // fresh 20 down to ~14.3.
        config.pixelRadiusMigrated = true;
        config.wallpaperFirstUsePending = false;
        boolean personalLoaded = PersonalDataStore.load(config);
        seedGuiTip(config);
        if (personalLoaded) save(config);
        return config;
    }

    public static void save(AtomChatConfig config) {
        saveChecked(config);
    }

    /** Reports persistence failure to actions that must roll back a live preview. */
    public static boolean saveChecked(AtomChatConfig config) {
        if (ConfigPreviewTransaction.interceptSave(config)) return true;
        Path path = com.atom.chat.platform.Platform.configDir().resolve("atomchat/atomchat-client.json");
        Path staged = null;
        try {
            com.google.gson.JsonObject settings = PersonalDataStore.saveAndStrip(config, path);
            Files.createDirectories(path.getParent());
            staged = Files.createTempFile(path.getParent(), "atomchat-config-", ".tmp");
            Files.writeString(staged, GSON.toJson(settings), StandardCharsets.UTF_8);
            try {
                Files.move(staged, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(staged, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            com.atom.chat.util.DataMigration.noteWarning();
            AtomChat.LOGGER.error(com.atom.chat.diagnostics.AtomChatLog.message("config", "Failed to save AtomChat config or personal data; original config retained"), e);
            return false;
        } finally {
            if (staged != null) {
                try { Files.deleteIfExists(staged); } catch (IOException ignored) { }
            }
        }
    }
}
