package com.atom.chat.tools;



import com.atom.lib.design.atomchat.AppIcons;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.DrawPrimitives;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.*;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Rect;
import java.util.*;
import java.util.function.Function;

/** Compact action menu with the composer entrance and the complete context-menu surface/row language. */
public final class ToolMenu {
    private static final Path SEARCH = Path.makeFromSVGString("M8.5 3a5.5 5.5 0 1 0 0 11a5.5 5.5 0 1 0 0-11 M12.5 12.5L18 18");
    private boolean open, playersOpen;
    private float anim, scroll;
    private List<PlayerRef> players = List.of();
    private final Map<Integer, Float> hover = new HashMap<>();
    private final Map<Integer, PressScale> bounce = new HashMap<>();
    public boolean isOpen() { return open; }
    public boolean playersOpen() { return playersOpen; }
    public void toggle() { if (open) close(); else { open = true; back(); } }
    public void close() { open = false; }
    public void back() { playersOpen = false; scroll = 0; hover.clear(); bounce.clear(); }
    public void showPlayers(List<PlayerRef> online) { playersOpen = true; scroll = 0; refreshPlayers(online); hover.clear(); bounce.clear(); }
    public void refreshPlayers(List<PlayerRef> online) {
        if (playersOpen) players = online.stream().filter(p -> p.realName() != null)
                .sorted(Comparator.comparing(PlayerRef::realName, String.CASE_INSENSITIVE_ORDER)).toList();
    }
    public PlayerRef playerAt(int row) { return playersOpen && row > 0 && row <= players.size() ? players.get(row - 1) : null; }
    private int rows() { return playersOpen ? Math.max(2, players.size() + 1) : 3; }
    public UiLayout.Rect paintedBounds(UiLayout source){return ComposerPopupTransform.of(bounds(source),anim).paintedBounds(bounds(source));}
    public UiLayout.Rect paintedRow(UiLayout source, int row) {
        if (!open || playersOpen || row < 0 || row >= rows()) return null;
        var box = bounds(source);
        var rect = new UiLayout.Rect(box.x(), box.y() + row * ToolLayout.MENU_ROW_H - scroll,
                box.w(), ToolLayout.MENU_ROW_H);
        return ComposerPopupTransform.of(box, anim).paintedBounds(rect);
    }
    public UiLayout.Rect bounds(UiLayout source) { return ToolLayout.menu(source, rows()); }
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
            float cx = box.x() + box.w() / 2, cy = box.y() + box.h() / 2;
            ComposerPopupTransform.of(box,anim).apply(canvas);
            MenuPopup.drawSurface(canvas, box.x(), box.y(), box.w(), box.h());
            com.atom.chat.render.DotTexture.drawConnector(canvas, box, UiTokens.radius(MenuPopup.SURFACE_RADIUS),
                    source.inputBar, source.imageBtn, com.atom.chat.config.AtomChatConfig.get().accentColor);
            int hovered = hit(source, mx, my);
            canvas.save();
            try {
                // Like context menus, complete rows fit by their bounce budget, without a contour clip.
                // Only a scrolling player viewport needs to cut partially visible rows.
                if (rows() * ToolLayout.MENU_ROW_H > box.h())
                    SkiaDraw.clip(canvas, box.x(), box.y(), box.w(), box.h(), UiTokens.radius(MenuPopup.SURFACE_RADIUS));
                var font = FontManager.font(UiTokens.FONT_BUTTON);
                int first = Math.max(0, (int) (scroll / ToolLayout.MENU_ROW_H));
                int end = Math.min(rows(), first + (int) Math.ceil(box.h() / ToolLayout.MENU_ROW_H) + 1);
                for (int i = first; i < end; i++) {
                    boolean disabled = playersOpen && i == 1 && players.isEmpty();
                    float h = UiMotion.approach(hover.getOrDefault(i, 0F), !disabled && i == hovered ? 1 : 0, dt, UiMotion.HOVER_MS);
                    hover.put(i, h);
                    var spring = bounce.computeIfAbsent(i, key -> PressScale.bounce());
                    MenuPopup.updateRowBounce(spring, !disabled && i == hovered, dt, box.w());
                    float y = box.y() + i * ToolLayout.MENU_ROW_H - scroll, rowCy = y + ToolLayout.MENU_ROW_H / 2;
                    MenuPopup.beginRowBounce(canvas, spring, cx, rowCy);
                    try {
                        MenuPopup.drawRowWash(canvas, box.x(), y, box.w(), ToolLayout.MENU_ROW_H, h);
                        int color = disabled ? 0xFF999DA6 : 0xFFF4F5F7;
                        Path icon = playersOpen ? i == 0 ? AppIcons.ICON_BACK_PATH : AppIcons.ICON_TAB_PROFILE_PATH
                                : i == 0 ? SEARCH : i == 1 ? AppIcons.ICON_TAB_PROFILE_PATH : DrawPrimitives.ICON_IMAGE_PATH;
                        DrawPrimitives.drawIconCentered(canvas, icon, box.x() + ToolLayout.MENU_ROW_H / 2, rowCy,
                                UiTokens.COMPOSER_ICON_SIZE, color);
                        String label = playersOpen ? i == 0 ? tr.apply("atomchat.tools.back")
                                : players.isEmpty() ? tr.apply("atomchat.tools.players.empty") : players.get(i - 1).displayName()
                                : tr.apply("atomchat.tools." + new String[]{"search", "players", "image"}[i]);
                        SkiaFontRenderer.drawText(canvas, font, SkiaFontRenderer.truncate(font, label,
                                        box.w() - ToolLayout.MENU_ROW_H - MenuPopup.rowInset(box.w())),
                                box.x() + ToolLayout.MENU_ROW_H, SkiaFontRenderer.centerBaselineY(font, rowCy), color);
                    } finally { MenuPopup.endRowBounce(canvas); }
                }
            } finally { canvas.restore(); }
            if (fading) canvas.restore();
        } finally { canvas.restore(); }
    }
}
