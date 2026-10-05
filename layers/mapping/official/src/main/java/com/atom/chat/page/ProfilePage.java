package com.atom.chat.page;

import com.atom.chat.avatar.AvatarStore;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.PlayerAvatar;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.PressScale;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatsCounter;
import net.minecraft.stats.Stats;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Profile root page: a hero identity card (large circular avatar with a
 * persistent edit badge) above a card of copyable info rows — name, UUID,
 * latency and current server (grilled 2026-09-05: identity showcase only, no
 * social actions; rows copy their value).
 *
 * <p>Avatar interactions follow the grilled decision: the edit badge always
 * opens the file picker; tapping the avatar itself opens a local
 * change/clear menu when a custom avatar is set, and the picker otherwise.
 * With no custom avatar the skin is displayed.
 */
public final class ProfilePage {
    /** Actions the shell performs on behalf of this page. */
    public interface Handler {
        void openAvatarPicker();

        void clearAvatar();

        void copyText(String text);
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
    /** Cached stat totals; the statMap sum runs at most once a second. */
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
    /**
     * Bounce for the avatar and its edit badge: both are discrete taps, so
     * they take the full control lift. Draw-only, like every other scale on
     * this page - the hit-tests keep using the unscaled rects.
     */
    private final PressScale avatarScale = PressScale.control();
    private final PressScale badgeScale = PressScale.control();
    private float rowHover;
    /** Row the current {@link #rowHover} alpha belongs to; fades out on exit. */
    private int hoverRowIndex = -1;
    /**
     * Per-row and per-tile bounce, keyed by index. A full-width row that grew
     * on hover would read as jitter, so these take the press-only row spring
     * and their hover target stays 1.0. No pressed control is reported to this
     * page yet (the shell hands a press window only to the settings page), so
     * both springs rest at 1.0 until one is.
     */
    private final java.util.Map<Integer, PressScale> rowScale = new java.util.HashMap<>();
    private final float[] menuItemHover = new float[2];
    /** Per-item menu bounce, indexed like {@link #menuItemHover}. */
    private final PressScale[] menuItemScale = {PressScale.control(), PressScale.control()};
    private static int accentColor() {
        return com.atom.chat.config.AtomChatConfig.get().accentColor;
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
    /** Copy buttons live only on the name / UUID rows, docked after the label. */
    private static final int COPY_ROWS = 2;
    private static final long COPIED_FEEDBACK_MS = 1_200L;
    /**
     * Two overlapping rects, normalised to a 9x9 box so the drawn glyph size
     * is exact: scale = targetPx / 9, no empty viewBox margins shrinking it.
     */
    private static final io.github.humbleui.skija.Path ROW_COPY_ICON =
            io.github.humbleui.skija.Path.makeFromSVGString("M0 0 L6 0 L6 6 L0 6 Z M3 3 L9 3 L9 9 L3 9 Z");
    private static final float COPY_GLYPH_UNITS = 9.0F;

    /** Copy button docked right after the row label, back-button hover style. */
    private UiLayout.Rect copyButtonRect(UiLayout layout, int index, float scrollY) {
        UiLayout.Rect row = rowRect(layout, index, scrollY);
        Font labelFont = FontManager.font(UiTokens.PROFILE_ROW_FONT);
        float labelW = SkiaFontRenderer.getStringWidth(labelFont, infoRows().get(index).label());
        float size = s(22);
        return new UiLayout.Rect(
                row.x() + UiTokens.PROFILE_ROW_PAD + labelW + s(8),
                row.y() + (row.h() - size) / 2.0F,
                size, size);
    }

    /**
     * Copy button with hover language identical to the shell back button
     * (inset rounded-square wash). While the copied feedback window is up the
     * glyph flips to a check mark in the accent colour plus a "已复制" hint.
     */
    private void drawCopyButton(Canvas canvas, UiLayout layout, int index, float scrollY, float dtMs) {
        UiLayout.Rect button = copyButtonRect(layout, index, scrollY);
        boolean copied = copiedKey == index && System.currentTimeMillis() < copiedUntil;
        boolean hovered = !copied && button.contains(lastVmx, lastVmy);
        float alpha = copyHover.computeIfAbsent(index, k -> 0.0F);
        alpha = UiMotion.approach(alpha, hovered ? 1.0F : 0.0F, dtMs, UiMotion.HOVER_MS);
        copyHover.put(index, alpha);
        PressScale copyPress = copyScale.computeIfAbsent(index, k -> PressScale.control());
        copyPress.update(hovered, false, dtMs, Animations.enabled());
        copyPress.begin(canvas, button.x() + button.w() / 2.0F, button.y() + button.h() / 2.0F);
        try {
            if (alpha > 0.01F) {
                float inset = s(3);
                // Selection language (white 90) rather than the row-wash white 45:
                // the button sits inside a highlighted row, so a 45-on-45 wash is
                // invisible — the reason hover feedback looked missing entirely.
                SkiaDraw.drawRoundedRect(canvas, button.x() + inset, button.y() + inset,
                        button.w() - inset * 2.0F, button.h() - inset * 2.0F, UiTokens.radius(8),
                        Color.makeARGB((int) (90.0F * alpha), 255, 255, 255));
            }
            int iconColor = Color.makeARGB((int) (210.0F + 45.0F * alpha), 255, 255, 255);
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

    /**
     * True when the point sits on one of the name/UUID copy buttons
     * (click = copy + feedback). Other rows and the tiles are display-only.
     */
    private boolean onCopyButton(UiLayout layout, float scrollY, float vmx, float vmy) {
        for (int i = 0; i < COPY_ROWS; i++) {
            if (copyButtonRect(layout, i, scrollY).contains(vmx, vmy)) {
                handler.copyText(infoRows().get(i).value());
                copiedKey = i;
                copiedUntil = System.currentTimeMillis() + COPIED_FEEDBACK_MS;
                return true;
            }
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
    }

    /** Clears the injected subject; the page falls back to the local player. */
    public void resetSubject() {
        this.subject = null;
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
        Minecraft client = Minecraft.getInstance();
        return client.player != null ? client.player.getUUID() : null;
    }

    private String subjectName() {
        if (subject != null) {
            return subject.realName();
        }
        Minecraft client = Minecraft.getInstance();
        return client.player != null ? client.player.getName().getString() : "-";
    }

    private boolean subjectIsSelf() {
        UUID own = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getUUID() : null;
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
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (statsCache != null && now - statsCacheMs < 1000L) {
            return statsCache;
        }
        StatsCounter stats = client.player.getStats();
        long mined = 0L;
        long killed = 0L;
        for (Object2IntMap.Entry<Stat<?>> entry : stats.stats.object2IntEntrySet()) {
            Stat<?> stat = entry.getKey();
            if (stat.getType() == Stats.BLOCK_MINED) {
                mined += entry.getIntValue();
            } else if (stat.getType() == Stats.ENTITY_KILLED) {
                killed += entry.getIntValue();
            }
        }
        long walkCm = stats.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM));
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
        return Component.translatable(key).getString();
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /** One copyable info row. */
    public record InfoRow(String label, String value) {
    }

    /** One dashboard tile under the hero card: big value + small label. */
    public record StatTile(String label, String value, String copy) {
    }

    public float measureContent(UiLayout layout) {
        return UiTokens.ROOT_CONTENT_GAP
                + UiTokens.PROFILE_AVATAR_HERO_H
                + UiTokens.SETTINGS_ROW_GAP
                + UiTokens.PROFILE_TILE_H
                + UiTokens.SETTINGS_ROW_GAP
                + infoRows().size() * UiTokens.PROFILE_ROW_H
                + (infoRows().size() - 1) * UiTokens.SETTINGS_ROW_GAP;
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    /**
     * Dashboard tiles (0.1.11 redesign): ping / session / stats as one row of
     * three under the hero card, identity rows stay as grouped rows below.
     */
    private List<StatTile> statTiles() {
        Minecraft client = Minecraft.getInstance();
        List<StatTile> tiles = new ArrayList<>();
        UUID uuid = subjectUuid();
        PlayerInfo entry = uuid != null && client.player != null && client.player.connection != null
                ? client.player.connection.getPlayerInfo(uuid)
                : null;
        String ping = tr("atomchat.profile.ping.value", entry != null ? entry.getLatency() : -1);
        tiles.add(new StatTile(tr("atomchat.profile.ping"), ping, ping));
        // The session timer counts the LOCAL player's join time, so on someone
        // else's profile it was showing your own uptime under their name. Hide it
        // for other players instead of guessing (same rule as the OP row).
        if (subjectIsSelf()) {
            String session = sessionValue();
            if (session != null) {
                tiles.add(new StatTile(tr("atomchat.profile.session"), session, session));
            }
        }
        StatTotals totals = statTotals();
        if (totals != null) {
            tiles.add(new StatTile(tr("atomchat.profile.stats"),
                    tr("atomchat.profile.stats.mined", totals.mined()), statsValue()));
        } else {
            tiles.add(new StatTile(tr("atomchat.profile.stats"), "-", "-"));
        }
        return tiles;
    }

    private List<InfoRow> infoRows() {
        Minecraft client = Minecraft.getInstance();
        List<InfoRow> rows = new ArrayList<>();
        UUID uuid = subjectUuid();
        String name = subjectName();
        rows.add(new InfoRow(tr("atomchat.profile.name"), name));
        rows.add(new InfoRow(tr("atomchat.profile.uuid"), uuid != null ? uuid.toString() : "-"));
        Boolean operator = roleResolver != null ? roleResolver.isOperator(uuid, name) : null;
        if (operator != null) {
            // Unknown (null) hides the row instead of guessing a role.
            rows.add(new InfoRow(tr("atomchat.profile.role"),
                    tr(operator ? "atomchat.profile.role.op" : "atomchat.profile.role.member")));
        }
        String server;
        if (client.getCurrentServer() != null && client.getCurrentServer().ip != null
                && !client.getCurrentServer().ip.isBlank()) {
            server = client.getCurrentServer().ip;
        } else if (client.getSingleplayerServer() != null) {
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

    private UiLayout.Rect heroRect(UiLayout layout, float scrollY) {
        return new UiLayout.Rect(
                layout.list.x(),
                layout.list.y() + UiTokens.ROOT_CONTENT_GAP - scrollY,
                layout.list.w(),
                UiTokens.PROFILE_AVATAR_HERO_H);
    }

    private UiLayout.Rect avatarRect(UiLayout layout, float scrollY) {
        UiLayout.Rect hero = heroRect(layout, scrollY);
        float size = UiTokens.PROFILE_AVATAR;
        return new UiLayout.Rect(
                hero.x() + (hero.w() - size) / 2.0F,
                hero.y() + s(18),
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

    private UiLayout.Rect tileRect(UiLayout layout, int index, float scrollY, int tileCount) {
        float top = layout.list.y() + UiTokens.ROOT_CONTENT_GAP
                + UiTokens.PROFILE_AVATAR_HERO_H + UiTokens.SETTINGS_ROW_GAP - scrollY;
        int count = Math.max(1, tileCount);
        float w = (layout.list.w() - UiTokens.PROFILE_TILE_GAP * (count - 1)) / count;
        return new UiLayout.Rect(
                layout.list.x() + index * (w + UiTokens.PROFILE_TILE_GAP),
                top, w, UiTokens.PROFILE_TILE_H);
    }

    private UiLayout.Rect rowRect(UiLayout layout, int index, float scrollY) {
        float top = layout.list.y() + UiTokens.ROOT_CONTENT_GAP
                + UiTokens.PROFILE_AVATAR_HERO_H + UiTokens.SETTINGS_ROW_GAP
                + UiTokens.PROFILE_TILE_H + UiTokens.SETTINGS_ROW_GAP - scrollY;
        return new UiLayout.Rect(
                layout.list.x(),
                top + index * (UiTokens.PROFILE_ROW_H + UiTokens.SETTINGS_ROW_GAP),
                layout.list.w(),
                UiTokens.PROFILE_ROW_H);
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
     * action, avatar, badge or info row); false lets the shell keep it.
     */
    public boolean onClick(float vmx, float vmy, UiLayout layout, float scrollY) {
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
        // Copying happens on the dedicated right-edge button, not the whole row.
        return onCopyButton(layout, scrollY, vmx, vmy);
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
            drawHeroCard(canvas, layout, vmx, vmy, scrollY);
            drawStatTiles(canvas, layout, vmx, vmy, scrollY);
            drawInfoRows(canvas, layout, vmx, vmy, scrollY);
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
        // Every bounce below passes pressed = false: this page is never told
        // which control is held down, so the hover lift is the only live half
        // of the language here and the press dip stays dormant.
        avatarScale.update(overAvatar && subjectIsSelf(), false, dt, Animations.enabled());
        badgeScale.update(overBadge && subjectIsSelf(), false, dt, Animations.enabled());
        int hovered = -1;
        List<InfoRow> rows = infoRows();
        for (int i = 0; i < rows.size(); i++) {
            boolean over = rowRect(layout, i, scrollY).contains(vmx, vmy);
            if (over) {
                hovered = i;
            }
        }
        if (hovered != hoverRowIndex) {
            hoverRowIndex = hovered;
        }
        rowHover = UiMotion.approach(rowHover, hovered >= 0 ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
        for (int i = 0; i < menuItemHover.length; i++) {
            boolean enabled = i == 0 || avatarStore.isSet();
            boolean over = avatarMenuOpen && enabled
                    && menuItemRect(layout, scrollY, i).contains(vmx, vmy);
            menuItemHover[i] = UiMotion.approach(menuItemHover[i], over ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
            menuItemScale[i].update(over, false, dt, Animations.enabled());
        }
        // Popup fade respects the decorative-motion switch like every other
        // overlay: when animations are off the duration is 0 and the menu
        // simply appears/disappears.
        menuAnim = UiMotion.approach(menuAnim, avatarMenuOpen ? 1.0F : 0.0F, dt,
                Animations.ms(UiMotion.POPUP_MS));
    }

    private void drawHeroCard(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        UiLayout.Rect hero = heroRect(layout, scrollY);
        if (hero.bottom() < layout.list.y() || hero.y() > layout.list.bottom()) {
            return;
        }
        SkiaDraw.drawRoundedRect(canvas, hero.x(), hero.y(), hero.w(), hero.h(),
                UiTokens.settingsTileRadius(), UiTokens.cardFill());
        SkiaDraw.drawEdgeHighlight(canvas, hero.x(), hero.y(), hero.w(), hero.h(),
                UiTokens.settingsTileRadius(), s(1.2F), UiTokens.CARD_EDGE);

        // Avatar: the local custom avatar when the subject is self and one is
        // set (decoded off-thread; the skin shows while the decode is in
        // flight), the real skin otherwise — PlayerAvatar owns that chain.
        UiLayout.Rect avatar = avatarRect(layout, scrollY);
        avatarScale.begin(canvas, avatar.x() + avatar.w() / 2.0F, avatar.y() + avatar.h() / 2.0F);
        try {
            Image face = PlayerAvatar.face(subjectUuid(), subjectName());
            if (face != null) {
                SkiaDraw.drawRoundedImage(canvas, face, avatar.x(), avatar.y(), avatar.w(), avatar.h(),
                        avatar.w() / 2.0F, SamplingMode.LINEAR);
            } else {
                SkiaDraw.drawRoundedRect(canvas, avatar.x(), avatar.y(), avatar.w(), avatar.h(),
                        avatar.w() / 2.0F, Color.makeARGB(255, 120, 130, 145));
            }
            // Hover feedback regardless of whether an avatar is set: with no
            // custom avatar the tap opens the picker directly, so the affordance
            // must not vanish exactly when the avatar is clickable.
            if (avatarHover > 0.01F && subjectIsSelf()) {
                SkiaDraw.drawRoundedRect(canvas, avatar.x(), avatar.y(), avatar.w(), avatar.h(),
                        avatar.w() / 2.0F, Color.makeARGB((int) (40.0F * avatarHover), 255, 255, 255));
            }
        } finally {
            canvas.restore();
        }

        // Persistent edit badge at the avatar's bottom-right; only the local
        // player's avatar is editable.
        if (subjectIsSelf()) {
            UiLayout.Rect badge = badgeRect(layout, scrollY);
            badgeScale.begin(canvas, badge.x() + badge.w() / 2.0F, badge.y() + badge.h() / 2.0F);
            try {
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

        // Name under the avatar.
        Font nameFont = FontManager.boldFont(UiTokens.PROFILE_NAME_FONT);
        SkiaFontRenderer.drawTextCentered(canvas, nameFont, subjectName(),
                hero.x() + hero.w() / 2.0F,
                avatar.bottom() + s(26),
                textPrimary());
    }

    /** Dashboard tiles: big value centred, small label under it. */
    private void drawStatTiles(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        List<StatTile> tiles = statTiles();
        Font valueFont = FontManager.font(UiTokens.PROFILE_TILE_VALUE_FONT);
        Font labelFont = FontManager.font(UiTokens.PROFILE_TILE_LABEL_FONT);
        for (int i = 0; i < tiles.size(); i++) {
            UiLayout.Rect tile = tileRect(layout, i, scrollY, tiles.size());
            if (tile.bottom() < layout.list.y() || tile.y() > layout.list.bottom()) {
                continue;
            }
            // Tiles have no hit test of their own (onClick owns the avatar, the
            // badge, the menu and the copy buttons), so they carry no bounce: a
            // spring here would promise a press the page never handles.
            SkiaDraw.drawRoundedRect(canvas, tile.x(), tile.y(), tile.w(), tile.h(),
                    UiTokens.profileRowRadius(), UiTokens.cardFill());
            SkiaDraw.drawEdgeHighlight(canvas, tile.x(), tile.y(), tile.w(), tile.h(),
                    UiTokens.profileRowRadius(), s(1.2F), UiTokens.CARD_EDGE);
            float cx = tile.x() + tile.w() / 2.0F;
            String value = SkiaFontRenderer.truncate(valueFont, tiles.get(i).value(),
                    tile.w() - UiTokens.PROFILE_ROW_PAD);
            SkiaFontRenderer.drawTextCentered(canvas, valueFont, value,
                    cx, tile.y() + tile.h() / 2.0F - s(4), textPrimary());
            SkiaFontRenderer.drawTextCentered(canvas, labelFont, tiles.get(i).label(),
                    cx, tile.y() + tile.h() / 2.0F + s(15), sec(200));
        }
    }

    private void drawInfoRows(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        List<InfoRow> rows = infoRows();
        Font labelFont = FontManager.font(UiTokens.PROFILE_ROW_FONT);
        Font valueFont = FontManager.font(UiTokens.PROFILE_ROW_VALUE_FONT);
        for (int i = 0; i < rows.size(); i++) {
            UiLayout.Rect row = rowRect(layout, i, scrollY);
            if (row.bottom() < layout.list.y() || row.y() > layout.list.bottom()) {
                continue;
            }
            // Info rows are a full-width stack: the lift is compact (1.04) rather
            // than control's 1.08, which would close the gap to the row above and
            // below. row() would leave it at rest on hover, which is the motion a
            // list needs between stacked cards but the wrong answer for a row the
            // pointer is deliberately resting on.
            PressScale rowPress = rowScale.computeIfAbsent(i, k -> PressScale.compact());
            rowPress.update(rowHover > 0.01F && i == hoverRowIndex, false, lastDtMs,
                    Animations.enabled());
            rowPress.begin(canvas, row.x() + row.w() / 2.0F, row.y() + row.h() / 2.0F);
            try {
                SkiaDraw.drawRoundedRect(canvas, row.x(), row.y(), row.w(), row.h(),
                        UiTokens.profileRowRadius(), UiTokens.cardFill());
                SkiaDraw.drawEdgeHighlight(canvas, row.x(), row.y(), row.w(), row.h(),
                        UiTokens.profileRowRadius(), s(1.2F), UiTokens.CARD_EDGE);
                if (rowHover > 0.01F && i == hoverRowIndex) {
                    SkiaDraw.drawRoundedRect(canvas, row.x(), row.y(), row.w(), row.h(),
                            UiTokens.profileRowRadius(), UiTokens.cardHover(rowHover));
                }
                float cy = row.y() + row.h() / 2.0F;
                SkiaFontRenderer.drawText(canvas, labelFont, rows.get(i).label(),
                        row.x() + UiTokens.PROFILE_ROW_PAD,
                        SkiaFontRenderer.centerBaselineY(labelFont, cy),
                        textPrimary());
                String value = rows.get(i).value();
                float valueMaxW = row.w() - UiTokens.PROFILE_ROW_PAD * 2
                        - SkiaFontRenderer.getStringWidth(labelFont, rows.get(i).label());
                // drawTextRight already centres on cy internally — passing a
                // pre-converted baseline double-converts and draws the text high.
                SkiaFontRenderer.drawTextRight(canvas, valueFont,
                        SkiaFontRenderer.truncate(valueFont, value, Math.max(s(24), valueMaxW)),
                        row.right() - UiTokens.PROFILE_ROW_PAD,
                        cy,
                        textPrimary());
                if (i < COPY_ROWS) {
                    drawCopyButton(canvas, layout, i, scrollY, lastDtMs);
                }
            } finally {
                canvas.restore();
            }
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
            SkiaDraw.drawRoundedShadow(canvas, menu.x(), menu.y(), menu.w(), menu.h(),
                    s(10), s(8), Color.makeARGB(100, 0, 0, 0));
            SkiaDraw.drawRoundedRect(canvas, menu.x(), menu.y(), menu.w(), menu.h(),
                    s(10), Color.makeARGB(245, 35, 39, 47));
            for (int i = 0; i < labels.length; i++) {
                float rowY = menu.y() + i * rowH;
                float cy = rowY + rowH / 2.0F;
                // Each item bounces around its own rect centre, so the icon and
                // label travel with the capsule instead of drifting out of it.
                menuItemScale[i].begin(canvas, menu.x() + menu.w() / 2.0F, cy);
                try {
                    if (menuItemHover[i] > 0.01F) {
                        // Uniform s(4) inset on every side of the row capsule, the
                        // exact hover language of the bubble context menu.
                        SkiaDraw.drawRoundedRect(canvas, menu.x() + s(4), rowY + s(4),
                                menu.w() - s(8), rowH - s(8),
                                s(6), Color.makeARGB((int) (55.0F * menuItemHover[i]), 255, 255, 255));
                    }
                    boolean rowEnabled = i == 0 || clearEnabled;
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
                    canvas.restore();
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
                    .setStrokeWidth(1.5F / scale)
                    .setStrokeCap(PaintStrokeCap.ROUND)
                    .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally {
            canvas.restore();
        }
    }
}
