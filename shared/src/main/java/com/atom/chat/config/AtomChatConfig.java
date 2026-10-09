package com.atom.chat.config;

import com.atom.chat.AtomChat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AtomChatConfig {
    public static final AtomChatConfig DEFAULT = new AtomChatConfig();

    public float panelWidth = 480.0F;
    public float panelHeight = 860.0F;
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
    /** Double-clicking another player's avatar performs the QQ-style poke shake. */
    public boolean avatarPokeEnabled = true;
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
    public float panelOpacity = 0.93F;
    /**
     * AtomChat-only UI scale multiplier on top of the vanilla GUI scale. It is
     * applied at the Skia canvas and in every coordinate conversion, never to
     * {@link com.atom.chat.ui.UiTokens} — those constants are class-init
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
    /** Dumps avatar sampling PNGs to {@code <config>/atomchat/debug/} for color debugging. */
    public boolean debug = false;
    public int accentColor = 0xFF4A90E2;
    public int bubbleTextColor = 0xFFFFFFFF;
    public int ownBubbleColor = 0xFF1E90FF;
    public int otherBubbleColor = 0xFF2C3E50;
    public int panelBgColor = 0xEE16191F;
    public int textPrimaryColor = 0xFFFFFFFF;
    public int textSecondaryColor = 0xDCAAAABA;
    /**
     * Card/chrome surface colour. The card-tint slider sets this colour's
     * alpha (60 at 0% → 255 at 100%); white keeps the shipped frosted look.
     */
    public int cardColor = 0xFFFFFFFF;
    /** Global blocked-player real names, persisted locally. */
    public java.util.List<String> blockedPlayers = new java.util.ArrayList<>();
    /**
     * This client's profile signature (QQ 个性签名), shown centred under the
     * name on the own profile page and edited inline there (Enter commits,
     * Esc cancels). Local-only: it is never synced to other clients, so other
     * players' profiles show the placeholder line instead. Empty = unset.
     */
    public String playerSignature = "";

    /**
     * Master switch for the custom profile banner
     * ({@code <config>/atomchat/banner.png}). When false the banner file, even
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
    public float cornerRadius = 20f;
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
                com.atom.chat.ui.UiTokens.s(20)));
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
     * Card/chrome surface tint, 0..1. At 0 the surfaces are the frosted
     * translucent white washes; at 1 they are opaque tints lifted from the
     * panel colour. In between they slide through a semi-transparent grey —
     * one axis, three visual stops.
     */
    public float cardTint = 0.235F;
    /** Panel outline (bezel ring) colour. */
    public int panelOutlineColor = 0xFFFFFFFF;
    /**
     * White phone-style bezel ring around the panel. Part of the frosted look;
     * the opaque modern preset turns it off.
     */
    public boolean panelOutline = true;
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
    public boolean antiSpamEnabled = true;
    /**
     * Render consecutive messages from the same sender within five minutes as a
     * compact group: only the first keeps the avatar/name row, later bubbles get
     * a tighter gap (Discord/Telegram style).
     */
    public boolean compactMessagesEnabled = false;
    /**
     * Skia banner when someone @mentions or quotes you. Banners are drawn inside
     * the AtomChat panel only, so with the panel closed this has no visual
     * effect and the sound is the only in-game signal.
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
     * {@code <config>/atomchat/history/} and restored when you rejoin that
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
        instance = load();
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

    private static AtomChatConfig load() {
        Path path = com.atom.chat.platform.Platform.configDir().resolve("atomchat/atomchat-client.json");
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                AtomChatConfig config = GSON.fromJson(sanitizePageNavStyle(json), AtomChatConfig.class);
                if (config != null) {
                    if (config.blockedPlayers == null) {
                        config.blockedPlayers = new java.util.ArrayList<>();
                    }
                    config.migrateCornerStyle();
                    config.migratePixelRadius();
                    config.clampContentScale();
                    // Write the merged instance straight back: options added in
                    // newer builds are absent from an older file, and Gson drops
                    // unknown keys — so without this a new option could never be
                    // switched on by editing the file, it simply would not be
                    // there. Existing values are preserved by the round trip.
                    seedGuiTip(config);
                    save(config);
                    return config;
                }
            } catch (Exception e) {
                AtomChat.LOGGER.error("Failed to load AtomChat config, using defaults", e);
            }
        }
        AtomChatConfig config = new AtomChatConfig();
        // A brand-new config is already on the 1080p-basis scale, so it must be
        // stamped as migrated before it is written — otherwise the field's own
        // "unset means legacy" default would make the next load() divide the
        // fresh 20 down to ~14.3.
        config.pixelRadiusMigrated = true;
        seedGuiTip(config);
        save(config);
        return config;
    }

    public static void save(AtomChatConfig config) {
        Path path = com.atom.chat.platform.Platform.configDir().resolve("atomchat/atomchat-client.json");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AtomChat.LOGGER.error("Failed to save AtomChat config", e);
        }
    }
}
