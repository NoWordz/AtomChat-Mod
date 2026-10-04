package com.atom.chat.page;

import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.chat.PrivateChatStore;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.PlayerAvatar;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.UiCards;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.ColorFilter;
import io.github.humbleui.skija.ColorMatrix;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * QQ-style conversation list root page: Public is always first, then every
 * online player (server contact book), then recently-chatted offline players.
 */
public final class ConversationListPage {
    private static final float ROW_H = UiTokens.s(64);
    private static final float ROW_GAP = UiTokens.s(8);
    private static final float AVATAR = UiTokens.s(44);
    private static final float AVATAR_RADIUS = UiTokens.s(12);
    private static final float ICON_INSET = UiTokens.s(10);
    private static final float DIVIDER_H = UiTokens.SETTINGS_LABEL_H;

    /**
     * Player-card order, hoisted out of {@link #rows()} so the comparator chain
     * is built once instead of being re-assembled (and re-allocated) every frame.
     */
    private static final Comparator<Row> PLAYER_ORDER = Comparator
            .comparing((Row r) -> r.online() ? 0 : 1)
            .thenComparing(r -> r.online() ? 0 : (r.unread() > 0 ? 0 : 1))
            .thenComparing(r -> r.online() && r.latest() == null ? 1 : 0)
            .thenComparing(r -> r.latest() != null ? -r.latest().getTimestamp() : 0L)
            .thenComparing(r -> r.latest() == null ? r.title().toLowerCase(java.util.Locale.ROOT) : "");

    private final PageHost host;
    private float rowHover;
    /** Row the current {@link #rowHover} alpha belongs to; fades out on exit. */
    private int hoverRowIndex = -1;
    private int hoveredIndex = -1;
    private long lastFrameMs = System.currentTimeMillis();

    public enum RowKind { PUBLIC, PLAYER, DIVIDER }

    public record Row(RowKind kind, PlayerRef player, ChatMessage latest,
                      int unread, boolean online, boolean blocked) {
        public String title() {
            if (kind == RowKind.PUBLIC) {
                return tr("atomchat.conversation.world");
            }
            return player != null ? player.realName() : "";
        }
    }

    public record RowHit(Row row, int index, float x, float y, float w, float h) {
        public boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    public ConversationListPage(PageHost host) {
        this.host = host;
    }

    /** Title/name colour — follows the interface text colour setting. */
    private static int textPrimary() {
        return com.atom.chat.config.AtomChatConfig.get().textPrimaryColor;
    }

    /** Preview/time/muted colour — follows the secondary text colour setting. */
    private static int textSecondary(int alpha) {
        int c = com.atom.chat.config.AtomChatConfig.get().textSecondaryColor;
        return Color.makeARGB(alpha, (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
    }

    private static String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }

    private static float s(float v) {
        return UiTokens.s(v);
    }

    /**
     * The row list rebuilds at most every 250ms: render/measure/hit ask for it
     * every frame, but the underlying data (unread counts, latest messages,
     * online set) changes at human speed. Cheap bound instead of change hooks
     * in three stores.
     */
    private static final long ROWS_REBUILD_MS = 250L;
    private List<Row> cachedRows = List.of();
    private long rowsBuiltAt = Long.MIN_VALUE / 2L;
    private boolean rowsDirty = true;

    /** Row list in display order (cached; see {@link #ROWS_REBUILD_MS}). */
    public List<Row> rows() {
        long now = System.currentTimeMillis();
        if (rowsDirty || now - rowsBuiltAt >= ROWS_REBUILD_MS) {
            cachedRows = buildRows();
            rowsBuiltAt = now;
            rowsDirty = false;
        }
        return cachedRows;
    }

    /** Forces the next {@link #rows()} call to rebuild (rarely needed — the throttle covers it). */
    public void invalidateRows() {
        rowsDirty = true;
    }

    private List<Row> buildRows() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<Row> all = new ArrayList<>();
        all.add(new Row(RowKind.PUBLIC, null, latestPublic(), ChatStore.publicUnread(), true, false));

        // Only online players get a conversation card. Offline partners keep
        // their history on disk (when persistence is on) and their card reappears
        // the moment they join — listing every former teammate forever cost a
        // skin resolve and a sort pass per rebuild for rows nobody can message.
        List<PlayerRef> online = onlinePlayers();
        for (PlayerRef p : online) {
            if (p.equals(ownRef(client))) {
                continue;
            }
            ChatMessage latest = PrivateChatStore.latest(p);
            boolean blocked = BlockList.isBlocked(p);
            all.add(new Row(RowKind.PLAYER, p, latest, PrivateChatStore.unread(p), true, blocked));
        }

        // Sort only player rows after Public by the agreed rules: dynamic chats
        // first (latest activity descending), static/no-history by name. Public
        // is always row 0.
        List<Row> sortedPlayers = new ArrayList<>(all.subList(1, all.size()));
        sortedPlayers.sort(PLAYER_ORDER);
        List<Row> result = new ArrayList<>();
        result.add(new Row(RowKind.PUBLIC, null, latestPublic(), ChatStore.publicUnread(), true, false));
        if (!sortedPlayers.isEmpty()) {
            result.add(divider());
        }
        result.addAll(sortedPlayers);
        return result;
    }

    static Row divider() {
        return new Row(RowKind.DIVIDER, null, null, 0, false, false);
    }

    private static List<PlayerRef> onlinePlayers() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return List.of();
        }
        List<PlayerRef> out = new ArrayList<>();
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            out.add(PlayerRef.of(entry.getProfile().getId(), name));
        }
        return out;
    }

    private static boolean isOnline(PlayerRef player) {
        if (player == null) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return false;
        }
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            if (player.uuid() != null && player.uuid().equals(entry.getProfile().getId())) {
                return true;
            }
            if (player.realName() != null && player.realName().equalsIgnoreCase(entry.getProfile().getName())) {
                return true;
            }
        }
        return false;
    }

    private static PlayerRef ownRef(MinecraftClient client) {
        if (client == null || client.player == null) {
            return null;
        }
        return PlayerRef.of(client.player.getUuid(), client.player.getName().getString());
    }

    public float measureContent(UiLayout layout) {
        List<Row> rows = rows();
        if (rows.isEmpty()) {
            return UiTokens.ROOT_CONTENT_GAP;
        }
        float total = UiTokens.ROOT_CONTENT_GAP;
        int cards = 0;
        for (Row row : rows) {
            if (row.kind() == RowKind.DIVIDER) {
                total += DIVIDER_H;
            } else {
                total += ROW_H;
                cards++;
            }
        }
        total += (rows.size() - 1) * ROW_GAP;
        return total;
    }

    public void render(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        long now = System.currentTimeMillis();
        float dt = Math.min(50.0F, Math.max(1.0F, now - lastFrameMs));
        lastFrameMs = now;
        List<Row> rows = rows();
        float listTop = layout.list.y() + UiTokens.ROOT_CONTENT_GAP;
        int hovered = -1;
        float emptyTop = 0.0F;
        // Decided from the data, not from what happens to be on screen — a
        // scrolled view can hide every card without the list being empty.
        boolean hasPlayers = rows.stream().anyMatch(r -> r.kind() == RowKind.PLAYER);
        canvas.save();
        try {
            SkiaDraw.clip(canvas, layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h(), 0.0F);
            float y = listTop;
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                float h = row.kind() == RowKind.DIVIDER ? DIVIDER_H : ROW_H;
                if (y + h >= layout.list.y() && y <= layout.list.bottom()) {
                    if (row.kind() == RowKind.DIVIDER) {
                        drawDivider(canvas, layout.list.x(), y, layout.list.w(), h);
                    } else {
                        boolean over = vmx >= layout.list.x() && vmx <= layout.list.right()
                                && vmy >= y && vmy <= y + ROW_H;
                        if (over) {
                            hovered = i;
                        }
                        // Animated value only — see drawRow; forcing 1 while hovered
                        // is what made the highlight snap in instead of fading.
                        drawRow(canvas, row, layout.list.x(), y, layout.list.w(),
                                i == hoverRowIndex ? rowHover : 0.0F, i);
                        if (row.kind() == RowKind.PUBLIC) {
                            emptyTop = y + h + ROW_GAP;
                        }
                    }
                }
                y += h + ROW_GAP;
            }
        } finally {
            canvas.restore();
        }
        hoveredIndex = hovered;
        // One shared alpha plus the row it belongs to. Moving between adjacent
        // cards keeps it at 1 (no blink); entering fades in and leaving fades
        // out over the same 90ms the toolbar buttons use.
        rowHover = UiMotion.approach(rowHover, hovered >= 0 ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
        if (hovered >= 0) {
            hoverRowIndex = hovered;
        } else if (rowHover <= 0.0F) {
            hoverRowIndex = -1;
        }

        // No conversation partner at all: an illustrated empty state instead of
        // a bare list with one card and a lot of nothing.
        if (!hasPlayers) {
            UiLayout.Rect area = new UiLayout.Rect(layout.list.x(), emptyTop,
                    layout.list.w(), Math.max(s(120), layout.list.bottom() - emptyTop));
            SettingsSectionPage.drawEmptyState(canvas, area, "atomchat.conversation.no_players");
        }
    }

    /** Left-aligned bold section heading with a rule underneath (Plan A). */
    private void drawDivider(Canvas canvas, float x, float y, float w, float h) {
        Font font = FontManager.boldFont(UiTokens.SETTINGS_TILE_TITLE);
        int lineColor = textSecondary(120);
        String text = tr("atomchat.conversation.private_group");
        float padX = UiTokens.s(4);
        float textX = x + padX;
        float rightX = x + w - padX;
        float cy = y + h / 2.0F;
        SkiaFontRenderer.drawText(canvas, font, text, textX,
                SkiaFontRenderer.centerBaselineY(font, cy), textPrimary());
        float lineY = y + h - UiTokens.s(5);
        SkiaDraw.drawRoundedRect(canvas, textX, lineY,
                Math.max(0.0F, rightX - textX), UiTokens.s(1), UiTokens.s(0.5F), lineColor);
    }

    /** Hit-tests rows using the same geometry as render. */
    public RowHit hit(float vmx, float vmy, UiLayout layout, float scrollY) {
        List<Row> rows = rows();
        float listTop = layout.list.y() + UiTokens.ROOT_CONTENT_GAP;
        float y = listTop;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float h = row.kind() == RowKind.DIVIDER ? DIVIDER_H : ROW_H;
            if (row.kind() != RowKind.DIVIDER) {
                RowHit hit = new RowHit(row, i, layout.list.x(), y, layout.list.w(), ROW_H);
                if (hit.contains(vmx, vmy)) {
                    return hit;
                }
            }
            y += h + ROW_GAP;
        }
        return null;
    }

    /** Draws one row; {@code hoverAlpha} is the animated 0..1 highlight. */
    private void drawRow(Canvas canvas, Row row, float x, float y, float w, float hoverAlpha, int index) {
        if (row.blocked()) {
            drawBlockedRow(canvas, row, x, y, w, hoverAlpha);
        } else {
            drawNormalRow(canvas, row, x, y, w, hoverAlpha);
        }
    }

    private void drawNormalRow(Canvas canvas, Row row, float x, float y, float w, float hoverAlpha) {
        // Settings-page card stack: soft shadow, fill, polarity hairline, hover wash.
        UiCards.drawCard(canvas, x, y, w, ROW_H, UiTokens.settingsRowRadius(), hoverAlpha);
        drawRowContent(canvas, row, x, y, w);
    }

    private void drawBlockedRow(Canvas canvas, Row row, float x, float y, float w, float hoverAlpha) {
        float[] matrix = {
                0.2126F, 0.7152F, 0.0722F, 0, 0,
                0.2126F, 0.7152F, 0.0722F, 0, 0,
                0.2126F, 0.7152F, 0.0722F, 0, 0,
                0, 0, 0, 1, 0
        };
        canvas.save();
        try (Paint layer = new Paint().setColorFilter(ColorFilter.makeMatrix(new ColorMatrix(matrix)))) {
            canvas.saveLayer(Rect.makeXYWH(x - 1, y - 1, w + 2, ROW_H + 2), layer);
            UiCards.drawCard(canvas, x, y, w, ROW_H, UiTokens.settingsRowRadius(), hoverAlpha);
            drawRowContent(canvas, row, x, y, w);
            canvas.restore();
        } finally {
            canvas.restore();
        }
    }

    private void drawRowContent(Canvas canvas, Row row, float x, float y, float w) {
        float iconSize = AVATAR;
        float iconRadius = AVATAR_RADIUS;
        float iconInset = ICON_INSET;
        float iconX = x + iconInset;
        float iconY = y + (ROW_H - iconSize) / 2.0F;

        if (row.kind == RowKind.PUBLIC) {
            SkiaDraw.drawRoundedRect(canvas, iconX, iconY, iconSize, iconSize, iconRadius,
                    Color.makeARGB(60, 255, 255, 255));
            drawIconCentered(canvas, AppIcons.ICON_GLOBE_PATH,
                    iconX + iconSize / 2.0F, iconY + iconSize / 2.0F, s(26),
                    textPrimary());
        } else if (row.player() != null) {
            drawPlayerAvatar(canvas, row.player(), iconX, iconY, iconSize);
        }

        Font nameFont = FontManager.font(UiTokens.FONT_NAME);
        Font subFont = FontManager.font(UiTokens.FONT_QUOTE);
        Font timeFont = FontManager.font(UiTokens.FONT_QUOTE);
        float textX = iconX + iconSize + s(12);
        float nameCenterY = y + ROW_H / 2.0F - s(9);
        float previewCenterY = y + ROW_H / 2.0F + s(12);

        String time = row.kind == RowKind.PUBLIC ? formatMessageTime(latestPublic()) : "";
        if (row.kind == RowKind.PLAYER && row.latest() != null) {
            time = formatMessageTime(row.latest());
        }
        float timeW = time.isEmpty() ? 0.0F : SkiaFontRenderer.getStringWidth(timeFont, time);
        float timeX = x + w - s(10) - timeW;

        float badgeW = 0.0F;
        String badgeText = "";
        boolean mentionBadge = false;
        int mentions = row.kind == RowKind.PUBLIC ? ChatStore.mentionUnread() : 0;
        if (mentions > 0) {
            // Amber @-mention badge, distinct from the plain unread counter.
            mentionBadge = true;
            badgeText = "@" + (mentions > 99 ? "99+" : mentions);
            badgeW = Math.max(s(18), SkiaFontRenderer.getStringWidth(timeFont, badgeText) + s(9));
        } else if (row.unread() > 0) {
            badgeText = row.unread() > 99 ? "99+" : String.valueOf(row.unread());
            badgeW = Math.max(s(18), SkiaFontRenderer.getStringWidth(timeFont, badgeText) + s(9));
        }
        // Status dot sits immediately after the player's name. Public has none.
        float dotSpace = row.kind == RowKind.PLAYER ? s(14) : 0.0F;
        float nameMaxW = Math.max(0.0F, timeX - textX - s(8) - dotSpace);
        String name = truncateToWidth(nameFont, row.title(), nameMaxW);
        SkiaFontRenderer.drawText(canvas, nameFont, name, textX,
                SkiaFontRenderer.centerBaselineY(nameFont, nameCenterY),
                textPrimary());
        if (row.kind == RowKind.PLAYER) {
            float drawnNameW = SkiaFontRenderer.getStringWidth(nameFont, name);
            float dotR = s(3);
            float dotX = textX + drawnNameW + s(6);
            float dotY = nameCenterY;
            int dotColor = row.online()
                    ? Color.makeARGB(255, 82, 196, 110)
                    : Color.makeARGB(255, 224, 82, 82);
            SkiaDraw.drawRoundedRect(canvas, dotX - dotR, dotY - dotR, dotR * 2, dotR * 2, dotR, dotColor);
        }
        if (!time.isEmpty()) {
            SkiaFontRenderer.drawText(canvas, timeFont, time, timeX,
                    SkiaFontRenderer.centerBaselineY(timeFont, nameCenterY),
                    textSecondary(255));
        }

        float maxPreviewW = Math.max(0.0F, x + w - textX - s(8) - (badgeW > 0 ? badgeW + s(8) : 0.0F));
        String preview = previewText(row);
        if (preview == null || preview.isBlank()) {
            preview = tr("atomchat.conversation.start");
        }
        preview = truncateToWidth(subFont, preview, maxPreviewW);
        SkiaFontRenderer.drawText(canvas, subFont, preview, textX,
                SkiaFontRenderer.centerBaselineY(subFont, previewCenterY),
                textSecondary(220));

        if (badgeW > 0) {
            Font badgeFont = FontManager.font(UiTokens.FONT_QUOTE);
            float bh = s(18);
            float bx = x + w - s(10) - badgeW;
            float by = y + ROW_H / 2.0F + s(12) - bh / 2.0F;
            int badgeColor = mentionBadge ? Color.makeARGB(255, 245, 158, 11) : Color.makeARGB(255, 244, 67, 54);
            SkiaDraw.drawRoundedRect(canvas, bx, by, badgeW, bh, bh / 2.0F, badgeColor);
            SkiaFontRenderer.drawTextCentered(canvas, badgeFont, badgeText,
                    bx + badgeW / 2.0F, by + bh / 2.0F, Color.makeARGB(255, 255, 255, 255));
        }
    }

    private static void drawPlayerAvatar(Canvas canvas, PlayerRef player, float x, float y, float size) {
        Image face = PlayerAvatar.face(player.uuid(), player.realName());
        if (face != null) {
            SkiaDraw.drawRoundedImage(canvas, face, x, y, size, size, size / 2.0F, SamplingMode.LINEAR);
        } else {
            SkiaDraw.drawRoundedRect(canvas, x, y, size, size, size / 2.0F, Color.makeARGB(255, 120, 130, 145));
        }
    }

    private static String previewText(Row row) {
        if (row.kind == RowKind.PUBLIC) {
            ChatMessage latest = latestPublic();
            if (latest == null) {
                return tr("atomchat.conversation.empty");
            }
            return previewForPublic(latest);
        }
        ChatMessage latest = row.latest();
        if (latest == null) {
            return tr("atomchat.conversation.start");
        }
        if (latest.isOwn()) {
            return tr("atomchat.conversation.me") + ": " + friendlyContent(latest);
        }
        return friendlyContent(latest);
    }

    private static ChatMessage latestPublic() {
        List<ChatMessage> messages = ChatStore.get().snapshot();
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    private static String previewForPublic(ChatMessage msg) {
        if (msg.isSystem()) {
            String content = friendlyContent(msg);
            return content.isEmpty() ? tr("atomchat.conversation.empty") : content;
        }
        String name = msg.getSenderName();
        if (name == null || name.isBlank()) {
            name = tr("atomchat.sender.player");
        }
        String content = friendlyContent(msg);
        return name + ": " + (content.isEmpty() ? tr("atomchat.conversation.empty") : content);
    }

    private static String friendlyContent(ChatMessage msg) {
        String raw = msg.getRawText();
        if (hasImageCode(raw)) {
            return tr("atomchat.hud.image");
        }
        String text = msg.getContentText();
        return text == null ? "" : text.trim();
    }

    private static boolean hasImageCode(String text) {
        return text != null
                && (text.contains("[[CICode,url=") || text.contains("[CICode,url="));
    }

    private static String formatMessageTime(ChatMessage msg) {
        if (msg == null) {
            return "";
        }
        ZonedDateTime dt = Instant.ofEpochMilli(msg.getTimestamp()).atZone(ZoneId.systemDefault());
        LocalDate date = dt.toLocalDate();
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return dt.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        if (date.equals(today.minusDays(1))) {
            return tr("atomchat.time.yesterday");
        }
        if (date.equals(today.minusDays(2))) {
            return tr("atomchat.time.beforeYesterday");
        }
        return tr("atomchat.time.date", dt.getMonthValue(), dt.getDayOfMonth());
    }

    private static String truncateToWidth(Font font, String text, float maxW) {
        if (text.isEmpty() || maxW <= 0.0F || SkiaFontRenderer.getStringWidth(font, text) <= maxW) {
            return text;
        }
        String t = text;
        while (t.length() > 1 && SkiaFontRenderer.getStringWidth(font, t + "…") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    private static void drawIconCentered(Canvas canvas, Path icon, float cx, float cy,
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
}
