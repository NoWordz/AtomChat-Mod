package com.atom.chat.page;

import com.atom.chat.avatar.AvatarStore;
import com.atom.chat.banner.BannerImage;
import com.atom.chat.banner.BannerStore;
import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.PlayerAvatar;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.MenuPopup;
import com.atom.chat.ui.UiCards;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.PressScale;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiSpring;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatHandler;
import net.minecraft.stat.Stats;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Profile root page, QQ-home style, banner-card pass (2026-10): a hero banner
 * CARD inset from the panel edges — the custom banner photo cover-cropped,
 * the enlarged pixelated skin face on someone else's page, or the accent ->
 * panelBg gradient — melting into the panel through a bottom gradient, with
 * the circular avatar straddling the card's bottom edge behind a panelBg
 * divider ring plus an outer rim, the name and the signature line under it,
 * a row of three independent stat tiles (icon left, value + label right), the
 * info rows merged into ONE card with hairline dividers, and a bottom
 * identity card sized to its content (UUID row, plus the blocked marker row
 * when blocked) — the page ends where the card ends.
 *
 * <p>The signature line is the QQ personality signature: on the local
 * player's page it shows {@code config.playerSignature} and clicking it
 * borrows the shell's composer field as the IME carrier (same mechanism as
 * the quick-phrase editor) — Enter commits, Esc cancels. On someone else's
 * page it is a non-editable placeholder line.</p>
 *
 * <p>Avatar interactions keep the grilled decision: the edit badge always
 * opens the file picker; tapping the avatar itself opens a local
 * change/clear menu when a custom avatar is set, and the picker otherwise.
 * With no custom avatar the skin is displayed. The banner card (local player
 * only) opens the system image picker on click — the pick is copied to
 * {@code <config>/atomchat/banner.*} and hot-loads — and right-click clears
 * it back to the gradient.</p>
 */
public final class ProfilePage {
    /** Actions the shell performs on behalf of this page. */
    public interface Handler {
        void openAvatarPicker();

        void clearAvatar();

        /**
         * Opens the system image picker for the profile banner card (local
         * player only); the shell copies the pick into
         * {@code <config>/atomchat/banner.*} and the page hot-loads it.
         */
        void openBannerPicker();

        /**
         * Deletes the custom banner file — delete image = off; the banner
         * falls back to its accent gradient.
         */
        void clearBanner();

        void copyText(String text);

        /**
         * Borrows the composer field as the signature editor's IME carrier:
         * save the composer draft, load the current signature into the field
         * and focus it. The page flips into its editing draw afterwards.
         */
        void beginSignatureEdit();

        /**
         * Releases the borrowed field; {@code commit=true} saves the field
         * text back to {@code config.playerSignature} (and persists), either
         * way the composer draft is restored.
         */
        void endSignatureEdit(boolean commit);
    }

    /**
     * Operator lookup for the role row. Vanilla 1.21.1 only syncs the local
     * player's own permission level (via entity status 24-28 on join/op), so
     * the default resolver answers for self only and returns {@code null}
     * (unknown) for everyone else — the row is hidden rather than guessed.
     * A future server companion plugs in here to resolve every player.
     */
    public interface RoleResolver {
        Boolean isOperator(UUID uuid, String name);
    }

    private final Handler handler;
    private final AvatarStore avatarStore;
    /** Session start (client JOIN); 0 = unknown. */
    private static volatile long sessionStartMs;
    /** Cached stat totals; the synced-stat sum runs at most once a second. */
    private long statsCacheMs;
    private StatTotals statsCache;
    /**
     * Whose profile is shown; {@code null} means the local player. Reserved
     * for the planned "tap a player avatar to open their profile" navigation —
     * the data layer is already subject-parameterised.
     */
    private PlayerRef subject;
    private RoleResolver roleResolver;

    private long lastFrameMs = System.currentTimeMillis();
    private float avatarHover;
    private float badgeHover;
    /** Hover scrim on the banner card (local player's page only). */
    private float bannerHover;
    /**
     * Bounce for the avatar and its edit badge: both are discrete taps, so
     * they scale on their own width budget. Draw-only, like every other
     * scale on this page - the hit-tests keep using the unscaled rects.
     */
    private final PressScale avatarScale = PressScale.bounce();
    private final PressScale badgeScale = PressScale.bounce();
    /**
     * Per-row hover wash alpha, keyed by row index. Each row approaches its
     * own target every frame (the {@link #copyHover} state machine), so the
     * pointer leaving fades the wash out instead of cutting it hard to zero.
     * Since the info-card pass the rows share one card, so the wash is a
     * band inside it (clipped to the card's rounding) rather than a card of
     * its own.
     */
    private final java.util.Map<Integer, Float> rowHover = new java.util.HashMap<>();
    /** Per-tile hover wash alpha, keyed by tile index (draw-only language). */
    private final java.util.Map<Integer, Float> tileHover = new java.util.HashMap<>();
    /** Per-tile hover bounce, keyed by tile index (draw-only; hit tests keep unscaled geometry). */
    private final java.util.Map<Integer, PressScale> tileBounce = new java.util.HashMap<>();
    /**
     * The info card's ONE bounce: the whole card scales as a single surface
     * (the settings about-page's third-party card language). Draw-only —
     * hit tests keep the unscaled geometry.
     */
    private final PressScale infoCardScale = PressScale.bounce();
    private final float[] menuItemHover = new float[2];
    /** Per-item menu bounce, indexed like {@link #menuItemHover}. */
    private final PressScale[] menuItemScale = {PressScale.bounce(), PressScale.bounce()};

    private static int accentColor() {
        return com.atom.chat.config.AtomChatConfig.get().accentColor;
    }

    /** The configured panel background's RGB (alpha dropped for re-alphaing). */
    private static int panelRgb() {
        return com.atom.chat.config.AtomChatConfig.get().panelBgColor & 0xFFFFFF;
    }

    // ---- signature line -------------------------------------------------

    /**
     * Signature-edit state. The composer field is borrowed through the
     * {@link Handler} pair; the shell pushes the field's live text here every
     * frame via {@link #setSignatureDraft} so the line can draw it, and the
     * page itself never touches the config — the commit goes through
     * {@code Handler.endSignatureEdit(true)}.
     */
    private boolean signatureEditing;
    private String signatureDraft = "";
    private boolean signatureFieldFocused;
    /** Hover brighten for the signature line (self, not editing). */
    private float signatureHover;

    public boolean isSignatureEditing() {
        return signatureEditing;
    }

    /** Called by the shell right after it focused the borrowed field. */
    public void beginSignatureEdit() {
        signatureEditing = true;
        signatureFieldFocused = true;
    }

    /**
     * Ends the edit; {@code commit=true} hands the field text to the shell
     * for the config write-back. No-op when not editing (the shell's key
     * handler and the blur paths can both land here).
     */
    public void endSignatureEdit(boolean commit) {
        if (!signatureEditing) {
            return;
        }
        signatureEditing = false;
        handler.endSignatureEdit(commit);
    }

    /** Per-frame feed of the borrowed field's text and focus state. */
    public void setSignatureDraft(String text, boolean fieldFocused) {
        if (!signatureEditing) {
            return;
        }
        signatureDraft = text == null ? "" : text;
        signatureFieldFocused = fieldFocused;
    }

    /** The signature shown for the current subject; others get none. */
    private String signatureOfSubject() {
        if (!subjectIsSelf()) {
            return "";
        }
        String sig = com.atom.chat.config.AtomChatConfig.get().playerSignature;
        return sig == null ? "" : sig;
    }

    /** Per-row/tile copy-button hover alpha (key: row index, 100+i = tile index). */
    private final java.util.Map<Integer, Float> copyHover = new java.util.HashMap<>();
    /** Copy-button bounce, keyed exactly like {@link #copyHover}. */
    private final java.util.Map<Integer, PressScale> copyScale = new java.util.HashMap<>();
    /** Copy feedback: which button shows the check and until when. */
    private long copiedUntil;
    private int copiedKey = -1;
    /** Last frame's pointer + delta, reused by the copy-button hover draw. */
    private float lastVmx;
    private float lastVmy;
    private float lastDtMs;
    /**
     * The identity card's UUID row owns the one copy button now (the name and
     * UUID info rows moved into the card / off the page in the QQ-home pass).
     */
    private static final int UUID_COPY_KEY = 0;
    private static final long COPIED_FEEDBACK_MS = 1_200L;
    /**
     * Two overlapping rects, normalised to a 9x9 box so the drawn glyph size
     * is exact: scale = targetPx / 9, no empty viewBox margins shrinking it.
     */
    private static final io.github.humbleui.skija.Path ROW_COPY_ICON =
            io.github.humbleui.skija.Path.makeFromSVGString("M0 0 L6 0 L6 6 L0 6 Z M3 3 L9 3 L9 9 L3 9 Z");
    private static final float COPY_GLYPH_UNITS = 9.0F;

    /** Copy button docked right after the identity card's UUID label. */
    private UiLayout.Rect copyButtonRect(UiLayout layout, float scrollY) {
        UiLayout.Rect card = idCardRect(layout, scrollY);
        Font labelFont = FontManager.font(UiTokens.PROFILE_ROW_FONT);
        float labelW = SkiaFontRenderer.getStringWidth(labelFont, tr("atomchat.profile.uuid"));
        float size = s(22);
        float cy = card.y() + UiTokens.PROFILE_ROW_PAD + UiTokens.PROFILE_ROW_H / 2.0F;
        return new UiLayout.Rect(
                card.x() + UiTokens.PROFILE_ROW_PAD + labelW + s(8),
                cy - size / 2.0F,
                size, size);
    }

    /**
     * Copy button with hover language identical to the shell back button
     * (inset rounded-square wash). While the copied feedback window is up the
     * glyph flips to a check mark in the accent colour plus a "已复制" hint.
     */
    private void drawCopyButton(Canvas canvas, UiLayout layout, float scrollY, float dtMs) {
        UiLayout.Rect button = copyButtonRect(layout, scrollY);
        boolean copied = copiedKey == UUID_COPY_KEY && System.currentTimeMillis() < copiedUntil;
        boolean hovered = !copied && button.contains(lastVmx, lastVmy);
        float alpha = copyHover.computeIfAbsent(UUID_COPY_KEY, k -> 0.0F);
        alpha = UiMotion.approach(alpha, hovered ? 1.0F : 0.0F, dtMs, UiMotion.HOVER_MS);
        copyHover.put(UUID_COPY_KEY, alpha);
        PressScale copyPress = copyScale.computeIfAbsent(UUID_COPY_KEY, k -> PressScale.bounce());
        copyPress.update(hovered, false, dtMs, Animations.enabled(), button.w());
        copyPress.begin(canvas, button.x() + button.w() / 2.0F, button.y() + button.h() / 2.0F);
        try {
            if (alpha > 0.01F) {
                float inset = s(3);
                // Unified hover language: the accent-dyed, polarity-adaptive
                // cardHover wash (same family as the card it sits in). The old
                // white-90 wash was a light-theme-only habit that lost the theme
                // colour on hover.
                SkiaDraw.drawRoundedRect(canvas, button.x() + inset, button.y() + inset,
                        button.w() - inset * 2.0F, button.h() - inset * 2.0F, UiTokens.radius(8),
                        UiTokens.cardHover(alpha));
            }
            // The icon follows the secondary text ink (explicit user call): the
            // hue comes with the text colour setting, alpha only rebalances its
            // presence across the hover.
            int iconColor = UiTokens.iconSecondary(210.0F + 45.0F * alpha);
            float cx = button.x() + button.w() / 2.0F;
            float cy = button.y() + button.h() / 2.0F;
            if (copied) {
                // Check glyph centred, then the hint hugs it with a fixed 4px gap.
                io.github.humbleui.skija.Path check = com.atom.chat.ui.AppIcons.ICON_CHECK_PATH;
                Rect b = check.getBounds();
                float sc = s(12) / Math.max(b.getWidth(), b.getHeight());
                try (Paint paint = new Paint().setAntiAlias(true)
                        .setColor(accentColor())
                        .setMode(PaintMode.STROKE)
                        // The canvas scales after the paint is built: divide the
                        // stroke by the scale or the glyph draws ~1.4x too thick.
                        .setStrokeWidth(s(1.8F) / sc)
                        .setStrokeCap(PaintStrokeCap.ROUND)
                        .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                    canvas.save();
                    canvas.translate(cx - (b.getLeft() + b.getRight()) / 2.0F * sc,
                            cy - (b.getTop() + b.getBottom()) / 2.0F * sc);
                    canvas.scale(sc, sc);
                    canvas.drawPath(check, paint);
                    canvas.restore();
                }
                Font hintFont = FontManager.font(UiTokens.PROFILE_TILE_LABEL_FONT);
                SkiaFontRenderer.drawText(canvas, hintFont, tr("atomchat.settings.color.copied"),
                        cx + s(12) / 2.0F + s(4),
                        SkiaFontRenderer.centerBaselineY(hintFont, cy),
                        accentColor());
                return;
            }
            try (Paint paint = new Paint().setAntiAlias(true)
                    .setColor(iconColor)
                    .setMode(PaintMode.STROKE)
                    // Same scale compensation as the check glyph above: the canvas
                    // scale would otherwise fatten the 1.5-unit stroke to ~2.2.
                    .setStrokeWidth(s(1.5F) / (s(13) / COPY_GLYPH_UNITS))
                    .setStrokeCap(PaintStrokeCap.ROUND)
                    .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.save();
                float sc = s(13) / COPY_GLYPH_UNITS;
                canvas.translate(cx - COPY_GLYPH_UNITS / 2.0F * sc,
                        cy - COPY_GLYPH_UNITS / 2.0F * sc);
                canvas.scale(sc, sc);
                canvas.drawPath(ROW_COPY_ICON, paint);
                canvas.restore();
            }
        } finally {
            canvas.restore();
        }
    }

    /** True when the point sits on the identity card's UUID copy button. */
    private boolean onCopyButton(UiLayout layout, float scrollY, float vmx, float vmy) {
        if (copyButtonRect(layout, scrollY).contains(vmx, vmy)) {
            String uuid = subjectUuid() != null ? subjectUuid().toString() : "-";
            handler.copyText(uuid);
            copiedKey = UUID_COPY_KEY;
            copiedUntil = System.currentTimeMillis() + COPIED_FEEDBACK_MS;
            return true;
        }
        return false;
    }
    private boolean avatarMenuOpen;
    /**
     * Two-step confirm on the "restore skin" row: the first tap arms the row
     * red ("Restore skin?"), the second tap within the window really clears.
     * Same language as the settings page's destructive actions.
     */
    private static final long CLEAR_ARM_MS = 3000L;
    private int clearArmIndex = -1;
    private long clearArmedAt;
    /** Fade/scale progress of the avatar menu; keeps drawing while fading out. */
    private float menuAnim;

    public ProfilePage(Handler handler, AvatarStore avatarStore) {
        this.handler = handler;
        this.avatarStore = avatarStore;
    }

    /** Subject injection point for the future avatar-click profile navigation. */
    public void setSubject(PlayerRef newSubject) {
        this.subject = newSubject;
        clearRowMotion();
    }

    /** Clears the injected subject; the page falls back to the local player. */
    public void resetSubject() {
        this.subject = null;
        clearRowMotion();
    }

    /**
     * Drops the per-row hover alphas and the per-tile bounce states: they
     * belong to the previous subject's rows, and a stale alpha would flash
     * the new subject's first frame for one HOVER_MS window after a switch.
     * (The info card's bounce rides the page, like the avatar's.)
     */
    private void clearRowMotion() {
        rowHover.clear();
        tileBounce.clear();
    }

    public PlayerRef subject() {
        return subject;
    }

    public void setRoleResolver(RoleResolver newResolver) {
        this.roleResolver = newResolver;
    }

    /** Records the session start; called from the client JOIN event. */
    public static void noteJoin() {
        sessionStartMs = System.currentTimeMillis();
    }

    private UUID subjectUuid() {
        if (subject != null) {
            return subject.uuid();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null ? client.player.getUuid() : null;
    }

    private String subjectName() {
        if (subject != null) {
            return subject.realName();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null ? client.player.getName().getString() : "-";
    }

    private boolean subjectIsSelf() {
        UUID own = MinecraftClient.getInstance().player != null
                ? MinecraftClient.getInstance().player.getUuid() : null;
        return subject == null || (subjectUuid() != null && subjectUuid().equals(own));
    }

    private static String sessionValue() {
        long start = sessionStartMs;
        if (start <= 0) {
            return null;
        }
        long secs = Math.max(0L, (System.currentTimeMillis() - start) / 1000L);
        long h = secs / 3600L;
        long m = (secs % 3600L) / 60L;
        long s = secs % 60L;
        if (h > 0) {
            return tr("atomchat.profile.session.h", h, m);
        }
        if (m > 0) {
            return tr("atomchat.profile.session.m", m, s);
        }
        return tr("atomchat.profile.session.s", s);
    }

    /**
     * Totals for mined blocks and kills need the whole synced stat map, so the
     * combined value is cached for a second; the walk distance is a single
     * custom-stat lookup anyway. Null until the player exists.
     */
    private String statsValue() {
        StatTotals t = statTotals();
        return t == null ? null : tr("atomchat.profile.stats.value", t.mined(), t.killed(), distance(t.walkCm()));
    }

    private record StatTotals(long mined, long killed, long walkCm) {
    }

    private StatTotals statTotals() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (statsCache != null && now - statsCacheMs < 1000L) {
            return statsCache;
        }
        StatHandler stats = client.player.getStatHandler();
        long mined = 0L;
        long killed = 0L;
        for (Object2IntMap.Entry<Stat<?>> entry : stats.statMap.object2IntEntrySet()) {
            Stat<?> stat = entry.getKey();
            if (stat.getType() == Stats.MINED) {
                mined += entry.getIntValue();
            } else if (stat.getType() == Stats.KILLED) {
                killed += entry.getIntValue();
            }
        }
        long walkCm = stats.getStat(Stats.CUSTOM.getOrCreateStat(Stats.WALK_ONE_CM));
        statsCache = new StatTotals(mined, killed, walkCm);
        statsCacheMs = now;
        return statsCache;
    }

    private static String distance(long cm) {
        if (cm >= 100_000L) {
            return String.format(java.util.Locale.ROOT, "%.1f km", cm / 100_000.0);
        }
        return (cm / 100L) + " m";
    }

    private static float s(float v) {
        return UiTokens.s(v);
    }

    /** Title/value colour — follows the interface text colour setting. */
    private static int textPrimary() {
        return com.atom.chat.config.AtomChatConfig.get().textPrimaryColor;
    }

    /** Muted label colour — follows the secondary text colour setting. */
    private static int sec(int alpha) {
        int c = com.atom.chat.config.AtomChatConfig.get().textSecondaryColor;
        return Color.makeARGB(alpha, (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
    }

    private static String tr(String key) {
        return Text.translatable(key).getString();
    }

    private static String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }

    /** One copyable info row. */
    public record InfoRow(String label, String value) {
    }

    /**
     * One dashboard tile under the banner card: icon at the left edge, the
     * big value and the small label stacked to its right.
     */
    public record StatTile(String label, String value, String copy, Path icon) {
    }

    /**
     * Height of everything stacked above the bottom identity card, measured
     * from the list's top edge (no scroll). Shared by {@link #measureContent}
     * and {@link #idCardRect} so the card's content-sized height can
     * never disagree with the measured total. The info rows live in ONE card,
     * so they contribute contiguous row heights with no inter-row gaps.
     */
    private static float contentAboveCard(int rowCount) {
        return UiTokens.PROFILE_BANNER_MARGIN_TOP
                + UiTokens.PROFILE_BANNER_H
                + UiTokens.PROFILE_HERO_BELOW_H
                + UiTokens.PROFILE_TILE_H
                + UiTokens.SETTINGS_ROW_GAP
                + rowCount * UiTokens.PROFILE_ROW_H
                + UiTokens.SETTINGS_ROW_GAP;
    }

    /**
     * Height of the bottom identity card: the {@link UiTokens#PROFILE_IDCARD_MIN_H}
     * base (pad above and below ONE UUID row) plus the blocked marker row
     * (row gap + row) when the subject is on the block list. Shared by
     * {@link #measureContent} and {@link #idCardRect} so hit geometry and
     * scroll extent cannot disagree.
     */
    private float idCardH() {
        float cardH = UiTokens.PROFILE_IDCARD_MIN_H;
        if (BlockList.isBlocked(subjectName())) {
            cardH += UiTokens.SETTINGS_ROW_GAP + UiTokens.PROFILE_ROW_H;
        }
        return cardH;
    }

    /**
     * The identity card is content-sized — the {@link #idCardH} base plus the
     * blocked row when blocked — so the page ends where the card ends and the
     * tail below is quiet list background, by the owner's decision. Overflow
     * scrolls, as before.
     */
    public float measureContent(UiLayout layout) {
        return contentAboveCard(infoRows().size()) + idCardH();
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    /**
     * Dashboard tiles (0.1.11 redesign): ping / session / stats as one row of
     * three under the hero banner, identity rows stay as grouped rows below.
     */
    private List<StatTile> statTiles() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<StatTile> tiles = new ArrayList<>();
        UUID uuid = subjectUuid();
        PlayerListEntry entry = uuid != null && client.player != null && client.player.networkHandler != null
                ? client.player.networkHandler.getPlayerListEntry(uuid)
                : null;
        String ping = tr("atomchat.profile.ping.value", entry != null ? entry.getLatency() : -1);
        tiles.add(new StatTile(tr("atomchat.profile.ping"), ping, ping, AppIcons.ICON_SIGNAL_PATH));
        // The session timer counts the LOCAL player's join time, so on someone
        // else's profile it was showing your own uptime under their name. Hide it
        // for other players instead of guessing (same rule as the OP row).
        if (subjectIsSelf()) {
            String session = sessionValue();
            if (session != null) {
                tiles.add(new StatTile(tr("atomchat.profile.session"), session, session,
                        AppIcons.ICON_CLOCK_PATH));
            }
        }
        StatTotals totals = statTotals();
        if (totals != null) {
            tiles.add(new StatTile(tr("atomchat.profile.stats"),
                    tr("atomchat.profile.stats.mined", totals.mined()), statsValue(),
                    AppIcons.ICON_STATS_PATH));
        } else {
            tiles.add(new StatTile(tr("atomchat.profile.stats"), "-", "-",
                    AppIcons.ICON_STATS_PATH));
        }
        return tiles;
    }

    /**
     * The compact identity rows. The QQ-home pass moved the nickname and the
     * UUID out of here — the name lives once under the avatar, the UUID lives
     * in the bottom identity card — so what remains is the role (when
     * resolvable) and the current server.
     */
    private List<InfoRow> infoRows() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<InfoRow> rows = new ArrayList<>();
        UUID uuid = subjectUuid();
        String name = subjectName();
        Boolean operator = roleResolver != null ? roleResolver.isOperator(uuid, name) : null;
        if (operator != null) {
            // Unknown (null) hides the row instead of guessing a role.
            rows.add(new InfoRow(tr("atomchat.profile.role"),
                    tr(operator ? "atomchat.profile.role.op" : "atomchat.profile.role.member")));
        }
        String server;
        if (client.getCurrentServerEntry() != null && client.getCurrentServerEntry().address != null
                && !client.getCurrentServerEntry().address.isBlank()) {
            server = client.getCurrentServerEntry().address;
        } else if (client.getServer() != null) {
            server = tr("atomchat.profile.server.singleplayer");
        } else {
            server = "-";
        }
        rows.add(new InfoRow(tr("atomchat.profile.server"), server));
        return rows;
    }

    // ------------------------------------------------------------------
    // Geometry (single source for render / hit / menu)
    // ------------------------------------------------------------------

    /**
     * The banner CARD, inset from the list edges by UiTokens.ROW_CLIP_INSET
     * (it must not touch the panel sides) and from the top by its own margin.
     */
    private UiLayout.Rect bannerRect(UiLayout layout, float scrollY) {
        return new UiLayout.Rect(
                layout.list.x() + UiTokens.ROW_CLIP_INSET,
                layout.list.y() + UiTokens.PROFILE_BANNER_MARGIN_TOP - scrollY,
                layout.list.w() - UiTokens.ROW_CLIP_INSET * 2.0F,
                UiTokens.PROFILE_BANNER_H);
    }

    /**
     * The avatar straddles the banner's bottom edge: its centre sits exactly
     * on that edge (half over the banner, half in the content area).
     */
    private UiLayout.Rect avatarRect(UiLayout layout, float scrollY) {
        UiLayout.Rect banner = bannerRect(layout, scrollY);
        float size = UiTokens.PROFILE_AVATAR;
        return new UiLayout.Rect(
                banner.x() + (banner.w() - size) / 2.0F,
                banner.bottom() - size / 2.0F,
                size, size);
    }

    private UiLayout.Rect badgeRect(UiLayout layout, float scrollY) {
        UiLayout.Rect avatar = avatarRect(layout, scrollY);
        float badge = UiTokens.PROFILE_EDIT_BADGE;
        return new UiLayout.Rect(
                avatar.right() - badge + s(2),
                avatar.bottom() - badge + s(2),
                badge, badge);
    }

    private float nameBaseline(UiLayout layout, float scrollY) {
        return avatarRect(layout, scrollY).bottom() + UiTokens.PROFILE_NAME_BAND;
    }

    private float signatureBaseline(UiLayout layout, float scrollY) {
        return nameBaseline(layout, scrollY) + UiTokens.PROFILE_SIGN_BAND;
    }

    /**
     * Tap target of the signature line (and, while editing, its input row):
     * the full list width so a centred short line is still an easy hit, with
     * a little touch slop past the glyph box on both sides.
     */
    private UiLayout.Rect signatureRect(UiLayout layout, float scrollY) {
        float top = nameBaseline(layout, scrollY) + s(6);
        return new UiLayout.Rect(
                layout.list.x(), top, layout.list.w(),
                UiTokens.PROFILE_SIGN_BAND + s(10));
    }

    private UiLayout.Rect tileRect(UiLayout layout, int index, float scrollY, int tileCount) {
        float top = layout.list.y() + UiTokens.PROFILE_BANNER_MARGIN_TOP
                + UiTokens.PROFILE_BANNER_H + UiTokens.PROFILE_HERO_BELOW_H - scrollY;
        int count = Math.max(1, tileCount);
        float columnW = layout.list.w() - UiTokens.ROW_CLIP_INSET * 2.0F;
        float w = (columnW - UiTokens.PROFILE_TILE_GAP * (count - 1)) / count;
        return new UiLayout.Rect(
                layout.list.x() + UiTokens.ROW_CLIP_INSET
                        + index * (w + UiTokens.PROFILE_TILE_GAP),
                top, w, UiTokens.PROFILE_TILE_H);
    }

    /**
     * The info card: ONE card holding every info row contiguously (the
     * banner-card pass merged the old per-row cards), inset by the profile
     * ROW_CLIP_INSET like the rest of the stack.
     */
    private UiLayout.Rect infoCardRect(UiLayout layout, int rowCount, float scrollY) {
        return new UiLayout.Rect(
                layout.list.x() + UiTokens.ROW_CLIP_INSET,
                layout.list.y() + UiTokens.PROFILE_BANNER_MARGIN_TOP
                        + UiTokens.PROFILE_BANNER_H + UiTokens.PROFILE_HERO_BELOW_H
                        + UiTokens.PROFILE_TILE_H + UiTokens.SETTINGS_ROW_GAP - scrollY,
                layout.list.w() - UiTokens.ROW_CLIP_INSET * 2.0F,
                rowCount * UiTokens.PROFILE_ROW_H);
    }

    /** Row {@code index} inside the info card: full card width, contiguous. */
    private UiLayout.Rect rowRect(UiLayout layout, int index, float scrollY) {
        UiLayout.Rect card = infoCardRect(layout, infoRows().size(), scrollY);
        return new UiLayout.Rect(
                card.x(),
                card.y() + index * UiTokens.PROFILE_ROW_H,
                card.w(),
                UiTokens.PROFILE_ROW_H);
    }

    /**
     * The bottom identity card: UUID (copyable) plus the blocked marker when
     * the subject is blocked. Its height is content-sized — the same number
     * {@link #measureContent} reports via {@link #idCardH}, so hit geometry
     * and scroll extent cannot disagree. Inset by ROW_CLIP_INSET
     * like every other card on the page.
     */
    private UiLayout.Rect idCardRect(UiLayout layout, float scrollY) {
        float cardTop = layout.list.y() + contentAboveCard(infoRows().size()) - scrollY;
        return new UiLayout.Rect(
                layout.list.x() + UiTokens.ROW_CLIP_INSET, cardTop,
                layout.list.w() - UiTokens.ROW_CLIP_INSET * 2.0F, idCardH());
    }

    /**
     * The "click to change" chip docked at the banner card's bottom-right
     * corner (local player's page only). A pill inset s(8) from the card's
     * edge — that clears the cardRadius corner curve, so the chip never hangs
     * over the card's rounded-off area.
     */
    private UiLayout.Rect bannerBadgeRect(UiLayout layout, float scrollY) {
        UiLayout.Rect banner = bannerRect(layout, scrollY);
        Font font = FontManager.font(UiTokens.PROFILE_TILE_LABEL_FONT);
        float h = s(20);
        float w = SkiaFontRenderer.getStringWidth(font, tr("atomchat.profile.banner.change")) + s(16);
        return new UiLayout.Rect(
                banner.right() - s(8) - w,
                banner.bottom() - s(8) - h,
                w, h);
    }

    /**
     * The local avatar menu (change/clear); anchored under the avatar itself
     * and wide enough for its longest label — the fixed conversation-menu
     * width truncates "Change avatar".
     */
    private UiLayout.Rect menuRect(UiLayout layout, float scrollY) {
        UiLayout.Rect avatar = avatarRect(layout, scrollY);
        float w = menuWidth();
        float h = UiTokens.MENU_H;
        float x = Math.max(layout.list.x() + s(8),
                Math.min(avatar.x() + avatar.w() / 2.0F - w / 2.0F,
                        layout.list.right() - w - s(8)));
        return new UiLayout.Rect(x, avatar.bottom() + s(6), w, h);
    }

    private float menuWidth() {
        Font font = FontManager.font(UiTokens.FONT_BUTTON);
        float w = UiTokens.MENU_W;
        w = Math.max(w, s(36) + SkiaFontRenderer.getStringWidth(font,
                tr("atomchat.profile.avatar.change")) + s(14));
        w = Math.max(w, s(36) + SkiaFontRenderer.getStringWidth(font,
                tr("atomchat.profile.avatar.clear")) + s(14));
        return w;
    }

    private UiLayout.Rect menuItemRect(UiLayout layout, float scrollY, int index) {
        UiLayout.Rect menu = menuRect(layout, scrollY);
        float rowH = UiTokens.MENU_H / 2.0F;
        return new UiLayout.Rect(menu.x(), menu.y() + index * rowH, menu.w(), rowH);
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    /**
     * Handles a left click. Returns true when the click was consumed (menu
     * action, avatar, badge, signature line or copy button); false lets the
     * shell keep it.
     */
    public boolean onClick(float vmx, float vmy, UiLayout layout, float scrollY) {
        if (signatureEditing) {
            // Blur-commit, the quick-phrase editor's rule: any click ends the
            // edit and commits. A click on the line itself is consumed so it
            // cannot immediately re-open the editor it just closed.
            boolean onLine = signatureRect(layout, scrollY).contains(vmx, vmy);
            endSignatureEdit(true);
            if (onLine) {
                return true;
            }
        }
        if (avatarMenuOpen) {
            for (int i = 0; i < 2; i++) {
                if (menuItemRect(layout, scrollY, i).contains(vmx, vmy)) {
                    if (i == 0) {
                        clearArmIndex = -1;
                        avatarMenuOpen = false;
                        handler.openAvatarPicker();
                        return true;
                    }
                    if (!avatarStore.isSet()) {
                        // Disabled row: consume the click, keep the menu open.
                        return true;
                    }
                    long now = System.currentTimeMillis();
                    if (clearArmIndex != i || now - clearArmedAt > CLEAR_ARM_MS) {
                        // Arm: the row turns red and the menu stays open.
                        clearArmIndex = i;
                        clearArmedAt = now;
                        return true;
                    }
                    clearArmIndex = -1;
                    avatarMenuOpen = false;
                    handler.clearAvatar();
                    return true;
                }
            }
            // Any click outside the menu dismisses it; the click is consumed
            // so it cannot fall through onto the rows underneath.
            avatarMenuOpen = false;
            clearArmIndex = -1;
            return true;
        }
        if (subjectIsSelf() && badgeRect(layout, scrollY).contains(vmx, vmy)) {
            handler.openAvatarPicker();
            return true;
        }
        if (subjectIsSelf() && avatarRect(layout, scrollY).contains(vmx, vmy)) {
            // The avatar tap is always the management entry: the menu opens
            // with or without a custom avatar ("use skin" disabled without
            // one). The badge is the shortcut straight to the picker.
            clearArmIndex = -1;
            avatarMenuOpen = true;
            return true;
        }
        if (subjectIsSelf() && bannerRect(layout, scrollY).contains(vmx, vmy)) {
            // The banner card is the custom-banner entry (the chip names it):
            // the pick lands in <config>/atomchat/banner.* and hot-loads. The
            // avatar's top half overlaps this card but the avatar check above
            // already consumed those points, so its menu stays authoritative.
            handler.openBannerPicker();
            return true;
        }
        if (subjectIsSelf() && signatureRect(layout, scrollY).contains(vmx, vmy)) {
            // Own signature: click to edit (the placeholder line is the same
            // entry point when nothing is set yet). Others' lines are inert.
            handler.beginSignatureEdit();
            return true;
        }
        // Copying happens on the dedicated button after the card's UUID label.
        return onCopyButton(layout, scrollY, vmx, vmy);
    }

    /**
     * Handles a right click on the banner card: with a custom banner set it
     * is cleared (the file is deleted, the accent gradient comes back).
     * Returns true when consumed. The right side is a deliberate plain
     * gesture, not the armed two-step: a cleared banner costs one click to
     * re-pick, unlike the avatar's restore-skin which also discards a cropped
     * image.
     */
    public boolean onRightClick(float vmx, float vmy, UiLayout layout, float scrollY) {
        if (subjectIsSelf() && customBannerActive()
                && bannerRect(layout, scrollY).contains(vmx, vmy)) {
            handler.clearBanner();
            return true;
        }
        return false;
    }

    /**
     * Whether the custom banner layer is live for the current page: local
     * player, config master switch on, and a banner file on disk. Delete
     * image = off — {@code BannerStore.isSet()} is the everyday half of the
     * switch.
     */
    private static boolean customBannerActive() {
        return com.atom.chat.config.AtomChatConfig.get().customBannerEnabled
                && BannerStore.isSet();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public void render(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        long now = System.currentTimeMillis();
        float dt = Math.min(50.0F, Math.max(1.0F, now - lastFrameMs));
        lastFrameMs = now;
        lastVmx = vmx;
        lastVmy = vmy;
        lastDtMs = dt;

        canvas.save();
        try {
            SkiaDraw.clip(canvas, layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h(), 0.0F);
            drawBanner(canvas, layout, scrollY);
            drawAvatar(canvas, layout, vmx, vmy, scrollY);
            drawNameSignature(canvas, layout, vmx, vmy, scrollY);
            drawStatTiles(canvas, layout, vmx, vmy, scrollY);
            drawInfoRows(canvas, layout, vmx, vmy, scrollY);
            drawIdentityCard(canvas, layout, scrollY);
            drawAvatarMenu(canvas, layout, vmx, vmy, scrollY);
        } finally {
            canvas.restore();
        }

        UiLayout.Rect avatar = avatarRect(layout, scrollY);
        boolean overAvatar = avatar.contains(vmx, vmy);
        UiLayout.Rect badge = badgeRect(layout, scrollY);
        boolean overBadge = badge.contains(vmx, vmy);
        avatarHover = UiMotion.approach(avatarHover, overAvatar ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
        badgeHover = UiMotion.approach(badgeHover, overBadge ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
        boolean overSign = subjectIsSelf() && !signatureEditing
                && signatureRect(layout, scrollY).contains(vmx, vmy);
        signatureHover = UiMotion.approach(signatureHover, overSign ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
        boolean overBanner = subjectIsSelf() && !signatureEditing
                && bannerRect(layout, scrollY).contains(vmx, vmy);
        bannerHover = UiMotion.approach(bannerHover, overBanner ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
        // The avatar, badge and menu bounces below pass pressed = false: the
        // page is never told which of them is held down, so the hover lift is
        // the only live half of their language and the press dip stays dormant.
        // Since the info-card merge the rows carry hover wash only — no press
        // half of their own.
        avatarScale.update(overAvatar && subjectIsSelf(), false, dt, Animations.enabled(), avatar.w());
        badgeScale.update(overBadge && subjectIsSelf(), false, dt, Animations.enabled(), badge.w());
        List<InfoRow> rows = infoRows();
        for (int i = 0; i < rows.size(); i++) {
            boolean over = rowRect(layout, i, scrollY).contains(vmx, vmy);
            float alpha = rowHover.computeIfAbsent(i, k -> 0.0F);
            rowHover.put(i, UiMotion.approach(alpha, over ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS));
        }
        // The info card rides ONE bounce (the settings about-page's
        // third-party card language): the pointer anywhere in the card lifts
        // the whole surface, pressed stays dormant (the rows carry no action
        // of their own). Hit tests keep the unscaled geometry, the scale is
        // draw-only.
        UiLayout.Rect infoCard = infoCardRect(layout, rows.size(), scrollY);
        infoCardScale.update(infoCard.contains(vmx, vmy), false, dt,
                Animations.enabled(), infoCard.w());
        for (int i = 0; i < menuItemHover.length; i++) {
            boolean enabled = i == 0 || avatarStore.isSet();
            boolean over = avatarMenuOpen && enabled
                    && menuItemRect(layout, scrollY, i).contains(vmx, vmy);
            menuItemHover[i] = UiMotion.approach(menuItemHover[i], over ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
            MenuPopup.updateRowBounce(menuItemScale[i], over, dt,
                    menuItemRect(layout, scrollY, i).w());
        }
        // Popup fade respects the decorative-motion switch like every other
        // overlay: when animations are off the duration is 0 and the menu
        // simply appears/disappears.
        menuAnim = UiMotion.approach(menuAnim, avatarMenuOpen ? 1.0F : 0.0F, dt,
                Animations.ms(UiMotion.POPUP_MS));
    }

    /**
     * The hero banner CARD, in three priority layers:
     * <ol>
     *   <li>Local player with a custom banner set: the image cover-cropped
     *       (centred scale-crop, never stretched) and left full bleed — the
     *       old melt gradient over it washed the photo's bottom into a band
     *       of panel colour.</li>
     *   <li>Local player with no banner: the accent -> panelBg gradient.</li>
     *   <li>Someone else's profile: their skin render enlarged — the skin
     *       pipeline samples only the 8x8 face, so that face is drawn
     *       height-fitting and centred with nearest-neighbour sampling (the
     *       crisp Minecraft-pixel enlargement) over the accent ground, then
     *       the same darkening gradient.</li>
     * </ol>
     * On the gradient layers the darkening gradient melts the card's bottom
     * edge into the content ground, so the avatar ring below reads on a
     * quiet surface; the photo keeps its own colours edge to edge. The local
     * player's page carries the "click to change" chip at the bottom-right
     * and a hover scrim.
     */
    private void drawBanner(Canvas canvas, UiLayout layout, float scrollY) {
        UiLayout.Rect banner = bannerRect(layout, scrollY);
        if (banner.bottom() < layout.list.y() || banner.y() > layout.list.bottom()) {
            return;
        }
        float radius = UiTokens.settingsTileRadius();
        SkiaDraw.drawRoundedShadow(canvas, banner.x(), banner.y(), banner.w(), banner.h(),
                radius, UiTokens.s(6), UiTokens.CARD_SHADOW);
        canvas.save();
        try {
            SkiaDraw.clip(canvas, banner.x(), banner.y(), banner.w(), banner.h(), radius);
            Image custom = subjectIsSelf() && customBannerActive()
                    ? BannerImage.current(BannerStore.current()) : null;
            if (custom != null) {
                // Cover crop at draw time: the banner's aspect follows the
                // panel width, which the decode cache cannot know about. No
                // melt gradient on top — the photo stays full bleed.
                SkiaDraw.drawImageCover(canvas, custom,
                        banner.x(), banner.y(), banner.w(), banner.h(), 0.0F, SamplingMode.LINEAR);
            } else if (subjectIsSelf()) {
                // Fallback: plain accent -> panelBg gradient, same melt.
                SkiaDraw.drawVerticalGradient(canvas, banner.x(), banner.y(), banner.w(), banner.h(), 0.0F,
                        accentColor(), UiTokens.withAlpha(panelRgb(), 255));
            } else {
                // Someone else: their skin render enlarged over the accent
                // ground. The pipeline samples only the 8x8 face, so the
                // enlargement is the honest ceiling of the current skin
                // pipeline — nearest-neighbour keeps the pixels crisp instead
                // of smearing them.
                SkiaDraw.drawVerticalGradient(canvas, banner.x(), banner.y(), banner.w(), banner.h(), 0.0F,
                        accentColor(), UiTokens.withAlpha(panelRgb(), 255));
                Image face = PlayerAvatar.face(subjectUuid(), subjectName());
                if (face != null) {
                    float side = Math.min(banner.w(), banner.h());
                    // SamplingMode.DEFAULT is the nearest-neighbour mode in this
                    // Skija (0.116.8): it packs FilterMipmap(FilterMode.NEAREST,
                    // MipmapMode.NONE) — the enlargement stays crisp pixels.
                    SkiaDraw.drawRoundedImage(canvas, face,
                            banner.x() + (banner.w() - side) / 2.0F,
                            banner.y(), side, side, 0.0F, SamplingMode.DEFAULT);
                }
                SkiaDraw.drawVerticalGradient(canvas, banner.x(), banner.y(), banner.w(), banner.h(), 0.0F,
                        UiTokens.withAlpha(panelRgb(), 140), UiTokens.withAlpha(panelRgb(), 255));
            }
            if (subjectIsSelf()) {
                if (bannerHover > 0.01F) {
                    // White image scrim, the avatar's hover language: a photo
                    // bitmap's polarity is unknown to the theme, so the lift
                    // is a literal white wash, weighted by the fade.
                    SkiaDraw.drawRoundedRect(canvas, banner.x(), banner.y(), banner.w(), banner.h(),
                            0.0F, Color.makeARGB((int) (24.0F * bannerHover), 255, 255, 255));
                }
                drawBannerBadge(canvas, layout, scrollY);
            }
        } finally {
            canvas.restore();
        }
        SkiaDraw.drawEdgeHighlight(canvas, banner.x(), banner.y(), banner.w(), banner.h(),
                radius, UiTokens.s(1.0F), UiTokens.hairline());
    }

    /**
     * The "click to change" chip: a fixed dark glass pill over an arbitrary
     * banner surface (photo or gradient) — the polarity-independent scrim
     * language the avatar's edit badge uses, so it stays literal.
     */
    private void drawBannerBadge(Canvas canvas, UiLayout layout, float scrollY) {
        UiLayout.Rect chip = bannerBadgeRect(layout, scrollY);
        SkiaDraw.drawRoundedRect(canvas, chip.x(), chip.y(), chip.w(), chip.h(),
                chip.h() / 2.0F, Color.makeARGB(215, 20, 22, 30));
        if (bannerHover > 0.01F) {
            SkiaDraw.drawRoundedRect(canvas, chip.x(), chip.y(), chip.w(), chip.h(),
                    chip.h() / 2.0F, Color.makeARGB((int) (60.0F * bannerHover), 255, 255, 255));
        }
        Font font = FontManager.font(UiTokens.PROFILE_TILE_LABEL_FONT);
        SkiaFontRenderer.drawText(canvas, font, tr("atomchat.profile.banner.change"),
                chip.x() + s(8), SkiaFontRenderer.centerBaselineY(font, chip.y() + chip.h() / 2.0F),
                Color.makeARGB(255, 255, 255, 255));
    }

    private void drawAvatar(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        UiLayout.Rect avatar = avatarRect(layout, scrollY);
        if (avatar.bottom() < layout.list.y() || avatar.y() > layout.list.bottom()) {
            return;
        }
        avatarScale.begin(canvas, avatar.x() + avatar.w() / 2.0F, avatar.y() + avatar.h() / 2.0F);
        try {
            // The panelBg ring under the bitmap is the QQ separator: an opaque
            // panel-coloured circle one ring wider than the avatar, so the
            // bitmap never sits directly on the busy banner behind it.
            float ring = UiTokens.PROFILE_AVATAR_RING;
            SkiaDraw.drawRoundedRect(canvas, avatar.x() - ring, avatar.y() - ring,
                    avatar.w() + ring * 2.0F, avatar.h() + ring * 2.0F,
                    (avatar.w() + ring * 2.0F) / 2.0F, UiTokens.withAlpha(panelRgb(), 255));
            // The local custom avatar when the subject is self and one is
            // set (decoded off-thread; the skin shows while the decode is in
            // flight), the real skin otherwise — PlayerAvatar owns that chain.
            Image face = PlayerAvatar.face(subjectUuid(), subjectName());
            if (face != null) {
                SkiaDraw.drawRoundedImage(canvas, face, avatar.x(), avatar.y(), avatar.w(), avatar.h(),
                        avatar.w() / 2.0F, SamplingMode.LINEAR);
            } else {
                // Fixed neutral placeholder for an absent face (content colour,
                // not chrome): reads on both polarities, so it stays literal.
                SkiaDraw.drawRoundedRect(canvas, avatar.x(), avatar.y(), avatar.w(), avatar.h(),
                        avatar.w() / 2.0F, Color.makeARGB(255, 120, 130, 145));
            }
            // Hover feedback regardless of whether an avatar is set: with no
            // custom avatar the tap opens the picker directly, so the affordance
            // must not vanish exactly when the avatar is clickable.
            if (avatarHover > 0.01F && subjectIsSelf()) {
                // Image scrim, not a card wash: the white lift sits on the avatar
                // bitmap itself, whose polarity no theme token knows about.
                SkiaDraw.drawRoundedRect(canvas, avatar.x(), avatar.y(), avatar.w(), avatar.h(),
                        avatar.w() / 2.0F, Color.makeARGB((int) (40.0F * avatarHover), 255, 255, 255));
            }
            // Hairline rim OUTSIDE the panelBg separator ring: it hugs the
            // separator's outer edge (avatar radius + PROFILE_AVATAR_RING),
            // polarity-adaptive UiTokens.rim, so the avatar assembly reads
            // against the busy banner without touching the bitmap or the
            // separator itself. Same ring language as the swatches.
            SkiaDraw.drawRing(canvas, avatar.x() + avatar.w() / 2.0F, avatar.y() + avatar.h() / 2.0F,
                    avatar.w() / 2.0F + ring + UiTokens.s(0.75F), UiTokens.s(1.0F), UiTokens.rim());
        } finally {
            canvas.restore();
        }

        // Persistent edit badge at the avatar's bottom-right; only the local
        // player's avatar is editable.
        if (subjectIsSelf()) {
            UiLayout.Rect badge = badgeRect(layout, scrollY);
            badgeScale.begin(canvas, badge.x() + badge.w() / 2.0F, badge.y() + badge.h() / 2.0F);
            try {
                // Badge is a fixed dark glass chip over an arbitrary avatar
                // bitmap: the fixed dark base plus white glyph/overlay is the
                // polarity-independent scrim language, so it stays literal.
                SkiaDraw.drawRoundedRect(canvas, badge.x(), badge.y(), badge.w(), badge.h(),
                        badge.w() / 2.0F, Color.makeARGB(215, 20, 22, 30));
                if (badgeHover > 0.01F) {
                    SkiaDraw.drawRoundedRect(canvas, badge.x(), badge.y(), badge.w(), badge.h(),
                            badge.w() / 2.0F, Color.makeARGB((int) (60.0F * badgeHover), 255, 255, 255));
                }
                drawIconCentered(canvas, AppIcons.ICON_EDIT_PATH,
                        badge.x() + badge.w() / 2.0F, badge.y() + badge.h() / 2.0F,
                        UiTokens.PROFILE_EDIT_BADGE * 0.55F, Color.makeARGB(255, 255, 255, 255));
            } finally {
                canvas.restore();
            }
        }
    }

    /** Name centred under the avatar, signature line under the name. */
    private void drawNameSignature(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        float nameBaseline = nameBaseline(layout, scrollY);
        float signBaseline = signatureBaseline(layout, scrollY);
        if (nameBaseline - UiTokens.PROFILE_NAME_FONT > layout.list.bottom()
                || signBaseline < layout.list.y()) {
            return;
        }
        float cx = layout.list.x() + layout.list.w() / 2.0F;
        Font nameFont = FontManager.boldFont(UiTokens.PROFILE_NAME_FONT);
        SkiaFontRenderer.drawTextCentered(canvas, nameFont, subjectName(), cx, nameBaseline, textPrimary());

        Font signFont = FontManager.font(UiTokens.PROFILE_SIGN_FONT);
        if (signatureEditing) {
            drawSignatureEditor(canvas, layout, signFont, scrollY);
            return;
        }
        String sig = signatureOfSubject();
        // Small pencil after the text on the owner's line only — the visible
        // "this is editable" affordance, riding the same brightness the line
        // itself shows under the pointer. Other players' placeholder stays
        // pen-free. The signature hit rect already spans the full list width,
        // so the pen needs no hit geometry of its own.
        float penSize = s(11);
        boolean own = subjectIsSelf();
        String text;
        int alpha;
        if (own && sig.isEmpty()) {
            // Unset own signature: the placeholder doubles as the tap-me
            // affordance, brightening under the pointer like the set line.
            text = tr("atomchat.profile.signature.placeholder");
            alpha = (int) (170 + 60 * signatureHover);
        } else if (!own) {
            // Other players' line is display-only: the QQ lazy placeholder.
            text = tr("atomchat.profile.signature.placeholder");
            alpha = 200;
        } else {
            text = sig;
            alpha = (int) (220 + 35 * signatureHover);
        }
        // Reserve the pen's room up front so a near-max-width line never
        // pushes it past the list edge.
        float maxW = layout.list.w() - UiTokens.PROFILE_ROW_PAD * 2.0F;
        if (own) {
            maxW -= penSize + s(6);
        }
        String shown = SkiaFontRenderer.truncate(signFont, text, maxW);
        SkiaFontRenderer.drawTextCentered(canvas, signFont, shown, cx, signBaseline, sec(alpha));
        if (own) {
            float textW = SkiaFontRenderer.getStringWidth(signFont, shown);
            // drawTextCentered re-anchors the line with the cap-height
            // baseline, so signBaseline already IS the text's visual midline
            // — the pen rides it directly.
            drawIconCentered(canvas, AppIcons.ICON_EDIT_PATH,
                    cx + textW / 2.0F + s(6) + penSize / 2.0F,
                    signBaseline,
                    penSize, sec(alpha));
        }
    }

    /**
     * The inline signature editor: a form row on the signature band — the
     * label anchors what is being edited, the borrowed field's text draws
     * after it with a blinking accent caret, the key hint hugs the right edge
     * and an accent underline closes the row. Nothing here writes the config;
     * the commit path is {@link #endSignatureEdit} via the shell.
     */
    private void drawSignatureEditor(Canvas canvas, UiLayout layout, Font signFont, float scrollY) {
        // The editor row's geometry comes from the shared anchor computation,
        // so the drawn row and the shell-facing IME anchor can never drift.
        SignatureAnchor anchor = signatureEditorAnchor(layout, scrollY);
        float signBaseline = anchor.baselineY();
        float pad = UiTokens.PROFILE_ROW_PAD;
        String label = tr("atomchat.profile.signature.label");
        Font labelFont = FontManager.font(UiTokens.PROFILE_ROW_FONT);
        SkiaFontRenderer.drawText(canvas, labelFont, label, layout.list.x() + pad, signBaseline, textPrimary());
        String hint = tr("atomchat.profile.signature.hint");
        Font hintFont = FontManager.font(UiTokens.PROFILE_TILE_LABEL_FONT);
        float hintW = SkiaFontRenderer.getStringWidth(hintFont, hint);
        String shown = SkiaFontRenderer.truncate(signFont, signatureDraft, anchor.textMaxW());
        SkiaFontRenderer.drawText(canvas, signFont, shown, anchor.textX(), signBaseline, textPrimary());
        // Caret: the borrowed field is focused, so blink at the shown text's
        // end (the composer's own blink cadence, 500ms half-period).
        if (signatureFieldFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caretX = anchor.textX() + SkiaFontRenderer.getStringWidth(signFont, shown);
            float h = SkiaFontRenderer.textHeight(signFont);
            SkiaDraw.drawRoundedRect(canvas, caretX, signBaseline - h * 0.85F, s(1.5F), h, s(0.75F),
                    accentColor());
        }
        SkiaFontRenderer.drawText(canvas, hintFont, hint,
                layout.list.right() - pad - hintW, signBaseline, sec(170));
        SkiaDraw.drawRoundedRect(canvas, layout.list.x() + pad, signBaseline + s(4),
                layout.list.w() - pad * 2.0F, s(1.5F), s(0.75F), accentColor());
    }

    /** Text anchor geometry of the signature edit row, in virtual coordinates. */
    public record SignatureAnchor(float textX, float baselineY, float textMaxW, float lineH) {
    }

    /**
     * The signature editor's text anchor — the ONE computation both the draw
     * ({@link #drawSignatureEditor}) and the shell-facing {@link
     * #signatureImeAnchor} read: the draft's left edge, its baseline, its
     * width budget and the band's line height. No second set of constants.
     */
    private SignatureAnchor signatureEditorAnchor(UiLayout layout, float scrollY) {
        float pad = UiTokens.PROFILE_ROW_PAD;
        String label = tr("atomchat.profile.signature.label");
        Font labelFont = FontManager.font(UiTokens.PROFILE_ROW_FONT);
        float textX = layout.list.x() + pad
                + SkiaFontRenderer.getStringWidth(labelFont, label) + s(10);
        String hint = tr("atomchat.profile.signature.hint");
        Font hintFont = FontManager.font(UiTokens.PROFILE_TILE_LABEL_FONT);
        float hintW = SkiaFontRenderer.getStringWidth(hintFont, hint);
        float textMaxW = layout.list.right() - pad - textX - hintW - s(12);
        return new SignatureAnchor(textX, signatureBaseline(layout, scrollY),
                Math.max(s(24), textMaxW), UiTokens.PROFILE_SIGN_BAND);
    }

    /**
     * IME anchor for the shell: while the signature is being edited, the
     * shell parks a hidden EditBox's composing anchor on the signature band
     * instead of the docked input bar, and these are the geometry to feed
     * it — the draft text's start x and baseline y in this mod's virtual
     * coordinates, its max width and the band's line height. The values come
     * from the same {@link #signatureEditorAnchor} the draw uses, so the
     * anchor cannot drift from the drawn row. Not editing: {@code null} —
     * the shell keeps the EditBox parked on the docked input bar.
     */
    public SignatureAnchor signatureImeAnchor(UiLayout layout, float scrollY) {
        return signatureEditing ? signatureEditorAnchor(layout, scrollY) : null;
    }

    /**
     * Dashboard tiles: three independent cards, each a horizontal row — the
     * glyph at the left edge, the big value and the small label stacked to
     * its right. The tiles have no hit test of their own (onClick owns the
     * banner, the avatar, the badge, the menu and the copy buttons), so the
     * hover wash plus the PressScale bounce are pointer-language only — the
     * bounce is draw-only around the tile's centre, and the press half stays
     * dormant because the page is never told a tile is held down.
     */
    private void drawStatTiles(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        List<StatTile> tiles = statTiles();
        Font valueFont = FontManager.font(UiTokens.PROFILE_TILE_VALUE_FONT);
        Font labelFont = FontManager.font(UiTokens.PROFILE_TILE_LABEL_FONT);
        float iconPad = s(10);
        float iconSize = s(16);
        for (int i = 0; i < tiles.size(); i++) {
            UiLayout.Rect tile = tileRect(layout, i, scrollY, tiles.size());
            if (tile.bottom() < layout.list.y() || tile.y() > layout.list.bottom()) {
                continue;
            }
            float hover = tileHover.computeIfAbsent(i, k -> 0.0F);
            boolean over = tile.contains(vmx, vmy);
            hover = UiMotion.approach(hover, over ? 1.0F : 0.0F,
                    lastDtMs, UiMotion.HOVER_MS);
            tileHover.put(i, hover);
            // Same one-bounce-per-control language as the settings rows: hit
            // tests keep using the unscaled rect, the scale is draw-only.
            PressScale bounce = tileBounce.computeIfAbsent(i, k -> PressScale.bounce());
            bounce.update(over, false, lastDtMs, Animations.enabled(), tile.w());
            bounce.begin(canvas, tile.x() + tile.w() / 2.0F, tile.y() + tile.h() / 2.0F);
            try {
                UiCards.drawCard(canvas, tile.x(), tile.y(), tile.w(), tile.h(),
                        UiTokens.profileRowRadius(), hover);
                float cy = tile.y() + tile.h() / 2.0F;
                drawIconCentered(canvas, tiles.get(i).icon(),
                        tile.x() + iconPad + iconSize / 2.0F, cy, iconSize,
                        UiTokens.iconSecondary(180.0F + 45.0F * hover));
                float textX = tile.x() + iconPad + iconSize + s(8);
                float valueMaxW = tile.right() - s(10) - textX;
                String value = SkiaFontRenderer.truncate(valueFont, tiles.get(i).value(),
                        Math.max(s(16), valueMaxW));
                SkiaFontRenderer.drawText(canvas, valueFont, value, textX,
                        SkiaFontRenderer.centerBaselineY(valueFont, cy - s(5)), textPrimary());
                SkiaFontRenderer.drawText(canvas, labelFont, tiles.get(i).label(), textX,
                        SkiaFontRenderer.centerBaselineY(labelFont, cy + s(12)), sec(200));
            } finally {
                canvas.restore();
            }
        }
    }

    /**
     * The info card: ONE card holding every info row (the banner-card pass
     * merged the old per-row cards — the owner's intent was always one
     * surface). Rows sit contiguously at {@link UiTokens#PROFILE_ROW_H} each;
     * between them a hairline divider, indented to the row pad so it starts
     * where the label starts. The card bounces as ONE surface (the settings
     * about-page's third-party card language) and each row washes on hover;
     * there is no press half — the rows carry no action of their own, the
     * hover is the pointer language the design calls for. The bounce keeps
     * the card-level clip as its frame: the wash already ends flush at the
     * card's edges (clipped to its rounding), and at this card width the 4px
     * per-side budget compresses to ~1.016, which moves the label/value text
     * at most ~4px — well inside the row pad — so the clip never cuts a
     * visible hard edge into a scaled card.
     */
    private void drawInfoRows(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        List<InfoRow> rows = infoRows();
        if (rows.isEmpty()) {
            return;
        }
        Font labelFont = FontManager.font(UiTokens.PROFILE_ROW_FONT);
        Font valueFont = FontManager.font(UiTokens.PROFILE_ROW_VALUE_FONT);
        UiLayout.Rect card = infoCardRect(layout, rows.size(), scrollY);
        if (card.bottom() < layout.list.y() || card.y() > layout.list.bottom()) {
            return;
        }
        // One bounce around the card's centre, wrapped around the whole draw
        // (draw-only; hit tests keep the unscaled rect). The card-level clip
        // inside stays: the wash ends flush at the card's edges anyway, and
        // the ~1.016 scale at this width keeps the text inside the pad.
        infoCardScale.begin(canvas, card.x() + card.w() / 2.0F, card.y() + card.h() / 2.0F);
        try {
            UiCards.drawCard(canvas, card.x(), card.y(), card.w(), card.h(),
                    UiTokens.profileRowRadius(), 0.0F);
            canvas.save();
            try {
                // Hover washes and dividers clip to the card's rounding, so the
                // first and last rows' bands never bleed past the corners.
                SkiaDraw.clip(canvas, card.x(), card.y(), card.w(), card.h(),
                        UiTokens.profileRowRadius());
                float pad = UiTokens.PROFILE_ROW_PAD;
                for (int i = 0; i < rows.size(); i++) {
                    UiLayout.Rect row = rowRect(layout, i, scrollY);
                    if (row.bottom() < layout.list.y() || row.y() > layout.list.bottom()) {
                        continue;
                    }
                    float rowAlpha = rowHover.computeIfAbsent(i, k -> 0.0F);
                    if (rowAlpha > 0.01F) {
                        // Whole-row hover wash, the accent-dyed cardHover family.
                        SkiaDraw.drawRoundedRect(canvas, row.x(), row.y(), row.w(), row.h(),
                                0.0F, UiTokens.cardHover(rowAlpha));
                    }
                    if (i > 0) {
                        // Hairline divider, 1px at the card-edge alpha, indented
                        // to the row pad on both sides.
                        SkiaDraw.drawRoundedRect(canvas, card.x() + pad, row.y(),
                                card.w() - pad * 2.0F, UiTokens.s(1.0F), 0.0F, UiTokens.hairline());
                    }
                    float cy = row.y() + row.h() / 2.0F;
                    SkiaFontRenderer.drawText(canvas, labelFont, rows.get(i).label(),
                            row.x() + pad,
                            SkiaFontRenderer.centerBaselineY(labelFont, cy),
                            textPrimary());
                    String value = rows.get(i).value();
                    float valueMaxW = row.w() - pad * 2
                            - SkiaFontRenderer.getStringWidth(labelFont, rows.get(i).label());
                    // drawTextRight already centres on cy internally — passing a
                    // pre-converted baseline double-converts and draws the text high.
                    SkiaFontRenderer.drawTextRight(canvas, valueFont,
                            SkiaFontRenderer.truncate(valueFont, value, Math.max(s(24), valueMaxW)),
                            row.right() - pad,
                            cy,
                            textPrimary());
                }
            } finally {
                canvas.restore();
            }
        } finally {
            canvas.restore();
        }
    }

    /**
     * The bottom identity card: one big card filling the remaining list
     * height. It carries the UUID (copy-full-value via the docked button —
     * long values truncate with the ellipsis and still copy whole) and, when
     * the subject is on the block list, the blocked marker row. There is no
     * "first met" row: the mod keeps no such field anywhere.
     */
    private void drawIdentityCard(Canvas canvas, UiLayout layout, float scrollY) {
        UiLayout.Rect card = idCardRect(layout, scrollY);
        if (card.bottom() < layout.list.y() || card.y() > layout.list.bottom()) {
            return;
        }
        UiCards.drawCard(canvas, card.x(), card.y(), card.w(), card.h(),
                UiTokens.profileRowRadius(), 0.0F);
        float pad = UiTokens.PROFILE_ROW_PAD;
        Font labelFont = FontManager.font(UiTokens.PROFILE_ROW_FONT);
        Font valueFont = FontManager.font(UiTokens.PROFILE_ROW_VALUE_FONT);
        // UUID row: label left, truncated value right, copy button after the
        // label (the same docking language the info rows used to carry).
        float rowY = card.y() + pad;
        float cy = rowY + UiTokens.PROFILE_ROW_H / 2.0F;
        String uuidLabel = tr("atomchat.profile.uuid");
        SkiaFontRenderer.drawText(canvas, labelFont, uuidLabel, card.x() + pad,
                SkiaFontRenderer.centerBaselineY(labelFont, cy), textPrimary());
        String uuid = subjectUuid() != null ? subjectUuid().toString() : "-";
        float copySize = s(22);
        float valueMaxW = card.w() - pad * 2
                - SkiaFontRenderer.getStringWidth(labelFont, uuidLabel) - s(8) - copySize - s(10);
        SkiaFontRenderer.drawTextRight(canvas, valueFont,
                SkiaFontRenderer.truncate(valueFont, uuid, Math.max(s(24), valueMaxW)),
                card.right() - pad, cy, textPrimary());
        drawCopyButton(canvas, layout, scrollY, lastDtMs);
        // Blocked marker row, only when the subject is actually blocked — the
        // marker red is the destructive-confirm literal the clear-skin row
        // uses, on purpose: this is a warning, not a theme colour.
        if (BlockList.isBlocked(subjectName())) {
            float row2Y = rowY + UiTokens.PROFILE_ROW_H + UiTokens.SETTINGS_ROW_GAP;
            float cy2 = row2Y + UiTokens.PROFILE_ROW_H / 2.0F;
            SkiaFontRenderer.drawText(canvas, labelFont, tr("atomchat.profile.blockstate"),
                    card.x() + pad, SkiaFontRenderer.centerBaselineY(labelFont, cy2), textPrimary());
            SkiaFontRenderer.drawTextRight(canvas, valueFont, tr("atomchat.profile.blocked.yes"),
                    card.right() - pad, cy2, Color.makeARGB(255, 235, 64, 52));
        }
    }

    private void drawAvatarMenu(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        if (menuAnim < 0.01F) {
            return;
        }
        UiLayout.Rect menu = menuRect(layout, scrollY);
        float rowH = UiTokens.MENU_H / 2.0F;
        boolean clearEnabled = avatarStore.isSet();
        boolean clearArmed = clearEnabled && clearArmIndex == 1
                && System.currentTimeMillis() - clearArmedAt <= CLEAR_ARM_MS;
        String[] labels = {
                tr("atomchat.profile.avatar.change"),
                tr(clearArmed ? "atomchat.profile.avatar.clear.confirm" : "atomchat.profile.avatar.clear")
        };
        Path[] icons = {AppIcons.ICON_EDIT_PATH, AppIcons.ICON_TAB_PROFILE_PATH};
        Font font = FontManager.font(UiTokens.FONT_BUTTON);
        canvas.save();
        try (Paint layer = new Paint()) {
            // Same entrance language as the bubble context menu: layer alpha
            // fade plus a subtle scale from the menu's top centre.
            layer.setColor(Color.makeARGB((int) (255.0F * menuAnim), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(menu.x() - s(20), menu.y() - s(20),
                    menu.w() + s(40), menu.h() + s(40)), layer);
            float sc = 0.94F + 0.06F * menuAnim;
            canvas.translate(menu.x() + menu.w() / 2.0F, menu.y());
            canvas.scale(sc, sc);
            canvas.translate(-(menu.x() + menu.w() / 2.0F), -menu.y());
            // The one right-click-menu language (MenuPopup owns the plate for
            // every menu in the mod): fixed dark surface (35,39,47), radius
            // and shadow tokens. Popups keep their own fixed polarity, so no
            // theme token applies.
            MenuPopup.drawSurface(canvas, menu.x(), menu.y(), menu.w(), menu.h());
            for (int i = 0; i < labels.length; i++) {
                float rowY = menu.y() + i * rowH;
                float cy = rowY + rowH / 2.0F;
                // Each item bounces around its own rect centre, so the icon and
                // label travel with the capsule instead of drifting out of it.
                MenuPopup.beginRowBounce(canvas, menuItemScale[i], menu.x() + menu.w() / 2.0F, cy);
                try {
                    // The wash, inset and bounce budget all live in MenuPopup —
                    // the shared language of every right-click menu.
                    MenuPopup.drawRowWash(canvas, menu.x(), rowY, menu.w(), rowH, menuItemHover[i]);
                    boolean rowEnabled = i == 0 || clearEnabled;
                    // Literal ink colours on the fixed dark menu: armed red is the
                    // destructive-confirm language, grey the disabled state, white
                    // the resting label — none of them follow the theme.
                    int labelColor;
                    if (i == 1 && clearArmed) {
                        labelColor = Color.makeARGB(255, 235, 64, 52);
                    } else if (!rowEnabled) {
                        labelColor = Color.makeARGB(255, 130, 140, 155);
                    } else {
                        labelColor = Color.makeARGB(255, 255, 255, 255);
                    }
                    drawIconCentered(canvas, icons[i], menu.x() + s(18), cy,
                            UiTokens.CONTEXT_ICON_SIZE, labelColor);
                    SkiaFontRenderer.drawText(canvas, font, labels[i], menu.x() + s(36),
                            SkiaFontRenderer.centerBaselineY(font, cy), labelColor);
                } finally {
                    MenuPopup.endRowBounce(canvas);
                }
            }
            // Close the saveLayer; the finally below closes the outer save().
            // A saveLayer per frame with no matching restore leaks one matrix
            // level per frame and drags the whole UI off-screen.
            canvas.restore();
        } finally {
            canvas.restore();
        }
    }

    private static void drawIconCentered(Canvas canvas, Path icon, float cx, float cy, float size, int color) {
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
}
