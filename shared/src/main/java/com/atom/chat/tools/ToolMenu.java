package com.atom.chat.tools;



import com.atom.chat.api.ui.ComposerUi;
import com.atom.chat.api.ui.UiContributions;
import com.atom.chat.render.CornerGeometry;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.lib.design.atomchat.AppIcons;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.DrawPrimitives;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.*;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** Compact action menu with the composer entrance and the complete context-menu surface/row language. */
public final class ToolMenu {
    private static final Path SEARCH = Path.makeFromSVGString("M8.5 3a5.5 5.5 0 1 0 0 11a5.5 5.5 0 1 0 0-11 M12.5 12.5L18 18");
    static final int ACTION_COUNT = 5;
    private static final Path PIN_ICON = Path.makeFromSVGString(
            "M10 2a5.5 5.5 0 0 0-5.5 5.5c0 4.2 5.5 10.5 5.5 10.5s5.5-6.3 5.5-10.5A5.5 5.5 0 0 0 10 2z M10 5.5a2 2 0 1 1 0 4a2 2 0 0 1 0-4z");
    private static final Path ITEM_ICON = Path.makeFromSVGString(
            "M10 2L17 6v8l-7 4l-7-4V6l7-4z M10 10L17 6 M10 10L3 6 M10 10v8");
    private List<UiContributions.Entry<ComposerUi.MenuItem>> entries;

    public ToolMenu() {
        this(defaults(id -> {}));
    }

    ToolMenu(List<UiContributions.Entry<ComposerUi.MenuItem>> entries) {
        this.entries = entries;
    }

    static List<UiContributions.Entry<ComposerUi.MenuItem>> defaults(Consumer<String> activate) {
        return List.of(
                entry(ComposerUi.SEARCH, "search", SEARCH, activate),
                entry(ComposerUi.PLAYERS, "players", AppIcons.ICON_TAB_PROFILE_PATH, activate),
                entry(ComposerUi.IMAGE, "image", TablerIcons.PHOTO, activate),
                entry(ComposerUi.SHARE_LOCATION, "share_location", PIN_ICON, activate),
                entry(ComposerUi.SHARE_ITEM, "share_item", ITEM_ICON, activate));
    }

    private static UiContributions.Entry<ComposerUi.MenuItem> entry(
            String id, String label, Path icon, Consumer<String> activate) {
        return new UiContributions.Entry<>(id, new ComposerUi.MenuItem(
                "atomchat.tools." + label, icon, context -> activate.accept(id), id.equals(ComposerUi.PLAYERS)));
    }

    void setEntries(List<UiContributions.Entry<ComposerUi.MenuItem>> entries) {
        this.entries = entries;
    }

    UiContributions.Entry<ComposerUi.MenuItem> actionAt(int row) {
        return !playersOpen && row >= 0 && row < entries.size() ? entries.get(row) : null;
    }

    int indexOfAction(String id) {
        for (int row = 0; row < entries.size(); row++) {
            if (entries.get(row).id().equals(id)) return row;
        }
        return -1;
    }

    private boolean open, playersOpen, mentionAll;
    private int pressedRow = -1;
    private float anim, scroll;
    private float preferredWidth = UiTokens.TOOL_MENU_WIDTH;
    private List<PlayerRef> players = List.of();
    private final Map<Integer, Float> hover = new HashMap<>();
    private final Map<Integer, PressScale> bounce = new HashMap<>();
    public boolean isOpen() { return open; }
    public boolean playersOpen() { return playersOpen; }
    public void toggle() {
        if (open) close();
        else if (!entries.isEmpty()) {
            open = true;
            back();
        }
    }
    public void close() {
        open = false;
        pressedRow = -1;
    }
    public void back() { playersOpen = false; scroll = 0; hover.clear(); bounce.clear(); }
    public void showPlayers(List<PlayerRef> online, boolean canMentionAll) {
        playersOpen = true; scroll = 0; refreshPlayers(online, canMentionAll); hover.clear(); bounce.clear();
    }
    void pressRow(int row) { pressedRow = row; }
    public void refreshPlayers(List<PlayerRef> online, boolean canMentionAll) {
        if (!playersOpen) return;
        mentionAll = canMentionAll;
        players = online.stream().filter(p -> p.realName() != null)
                .sorted(Comparator.comparing(PlayerRef::realName, String.CASE_INSENSITIVE_ORDER)).toList();
    }
    boolean isMentionAll(int row) { return playersOpen && mentionAll && row == 1; }
    public PlayerRef playerAt(int row) {
        int index = row - (mentionAll ? 2 : 1);
        return playersOpen && index >= 0 && index < players.size() ? players.get(index) : null;
    }
    private int rows() { return playersOpen ? 1 + (mentionAll ? 1 : 0) + Math.max(1, players.size()) : entries.size(); }

    void measure(Function<String, String> tr) {
        var font = FontManager.font(UiTokens.FONT_BUTTON);
        float widest = 0;
        for (int i = 0; i < rows(); i++) {
            widest = Math.max(widest, SkiaFontRenderer.literalWidth(font, label(i, tr)));
        }
        preferredWidth = Math.max(UiTokens.TOOL_MENU_WIDTH,
                (float) Math.ceil(widest + ToolLayout.MENU_ROW_H + UiTokens.LIST_PAD_X));
    }
    public UiLayout.Rect paintedBounds(UiLayout source){return ComposerPopupTransform.of(bounds(source),anim).paintedBounds(bounds(source));}
    public UiLayout.Rect paintedRow(UiLayout source, int row) {
        if (!open || playersOpen || row < 0 || row >= rows()) return null;
        var box = bounds(source);
        var rect = new UiLayout.Rect(box.x(), box.y() + row * ToolLayout.MENU_ROW_H - scroll,
                box.w(), ToolLayout.MENU_ROW_H);
        return ComposerPopupTransform.of(box, anim).paintedBounds(rect);
    }
    public UiLayout.Rect bounds(UiLayout source) { return ToolLayout.menu(source, rows(), preferredWidth); }
    public int hit(UiLayout source, float x, float y) {
        var box = bounds(source);
        var point = ComposerPopupTransform.of(box,anim).toLocal(x,y); x = point.x(); y = point.y();
        return open && box.contains(x, y) ? Math.min(rows() - 1, (int) ((y - box.y() + scroll) / ToolLayout.MENU_ROW_H)) : -1;
    }
    public void scroll(UiLayout source, double amount) {
        scroll = Math.max(0, Math.min(Math.max(0, rows() * ToolLayout.MENU_ROW_H - bounds(source).h()),
                scroll - (float) amount * ToolLayout.MENU_ROW_H));
    }
    public void render(Canvas canvas, UiLayout source, float mx, float my, float dt, Function<String, String> tr) {
        anim = UiMotion.approach(anim, open ? 1 : 0, dt, Animations.ms(UiMotion.POPUP_MS));
        if (anim < .01F) return;
        var box = bounds(source);
        scroll(source, 0);
        canvas.save();
        // Same fade + .92 scale + downward offset as EmojiPanel/QuickPhrasePanel.
        boolean fading = anim < 1;
        try (Paint layer = new Paint().setColor(((int) (255 * anim)) << 24)) {
            float pad = UiTokens.COMPOSER_POPUP_LAYER_PAD;
            if (fading) canvas.saveLayer(Rect.makeXYWH(box.x() - pad, box.y() - pad,
                    box.w() + pad * 2, box.h() + pad * 2), layer);
            ComposerPopupTransform.of(box,anim).apply(canvas);
            MenuPopup.drawSurface(canvas, box.x(), box.y(), box.w(), box.h());
            com.atom.chat.render.DotTexture.drawConnector(canvas, box, UiTokens.radius(MenuPopup.SURFACE_RADIUS, AtomChatAppearance.theme()),
                    source.inputBar, source.imageBtn, com.atom.chat.config.AtomChatConfig.get().accentColor);
            int hovered = hit(source, mx, my);
            // Only the scrolling player viewport needs to crop partial rows.
            if (rows() * ToolLayout.MENU_ROW_H > box.h()) {
                float radius = UiTokens.radius(MenuPopup.SURFACE_RADIUS, AtomChatAppearance.theme());
                var outline = RRect.makeXYWH(box.x(), box.y(), box.w(), box.h(), radius);
                CornerGeometry.drawClipped(canvas, outline, () -> drawRows(canvas, box, hovered, dt, tr));
            } else {
                drawRows(canvas, box, hovered, dt, tr);
            }
            if (fading) canvas.restore();
        } finally { canvas.restore(); }
    }

    private void drawRows(Canvas canvas, UiLayout.Rect box, int hovered, float dt, Function<String, String> tr) {
        float cx = box.x() + box.w() / 2;
        var font = FontManager.font(UiTokens.FONT_BUTTON);
        int first = Math.max(0, (int) (scroll / ToolLayout.MENU_ROW_H));
        int end = Math.min(rows(), first + (int) Math.ceil(box.h() / ToolLayout.MENU_ROW_H) + 1);
        for (int i = first; i < end; i++) {
            boolean disabled = playersOpen && i > 0 && !isMentionAll(i) && playerAt(i) == null;
            float h = UiMotion.approach(hover.getOrDefault(i, 0F), !disabled && i == hovered ? 1 : 0, dt, UiMotion.HOVER_MS);
            hover.put(i, h);
            var spring = bounce.computeIfAbsent(i, key -> PressScale.bounce());
            spring.update(!disabled && i == hovered, !disabled && i == pressedRow, dt, Animations.enabled(), box.w());
            float y = box.y() + i * ToolLayout.MENU_ROW_H - scroll, rowCy = y + ToolLayout.MENU_ROW_H / 2;
            MenuPopup.beginRowBounce(canvas, spring, cx, rowCy);
            try {
                MenuPopup.drawRowWash(canvas, box.x(), y, box.w(), ToolLayout.MENU_ROW_H, h);
                int color = disabled ? 0xFF999DA6 : 0xFFF4F5F7;
                Path icon = playersOpen ? i == 0 ? AppIcons.ICON_BACK_PATH : AppIcons.ICON_TAB_PROFILE_PATH
                        : entries.get(i).value().icon();
                DrawPrimitives.drawIconCentered(canvas, icon, box.x() + ToolLayout.MENU_ROW_H / 2, rowCy,
                        UiTokens.COMPOSER_ICON_SIZE, color);
                String label = label(i, tr);
                float available = box.w() - ToolLayout.MENU_ROW_H - UiTokens.LIST_PAD_X;
                float measured = SkiaFontRenderer.literalWidth(font, label);
                var labelFont = measured > available && available > 0 && playerAt(i) == null
                        ? FontManager.font(UiTokens.FONT_BUTTON * available / measured) : font;
                String shown = labelFont == font ? SkiaFontRenderer.truncate(font, label, available) : label;
                SkiaFontRenderer.drawText(canvas, labelFont, shown, box.x() + ToolLayout.MENU_ROW_H,
                        SkiaFontRenderer.centerBaselineY(labelFont, rowCy), color);
            } finally { MenuPopup.endRowBounce(canvas); }
        }
    }

    private String label(int row, Function<String, String> tr) {
        if (!playersOpen) return tr.apply(entries.get(row).value().labelKey());
        if (row == 0) return tr.apply("atomchat.tools.back");
        if (isMentionAll(row)) return tr.apply("atomchat.tools.mention_all");
        PlayerRef player = playerAt(row);
        return player == null ? tr.apply("atomchat.tools.players.empty") : player.displayName();
    }
}
