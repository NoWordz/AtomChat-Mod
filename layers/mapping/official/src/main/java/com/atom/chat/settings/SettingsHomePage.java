package com.atom.chat.settings;

import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiCards;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Rect;
import net.minecraft.network.chat.Component;

/**
 * Settings home: a 2x2 grid of Windows-11-style tiles. Each tile is a large
 * rounded rectangle with a line glyph in the top-left and a two-line caption
 * pinned to the bottom-left, so a long subtitle can never shift the glyph.
 *
 * <p>The grid never scrolls at the current section count, but geometry still
 * goes through {@link #tileRect(UiLayout, int, float)} so hit-testing and
 * rendering cannot drift apart.</p>
 */
public final class SettingsHomePage {
    private final SettingsSection[] sections = SettingsSection.values();
    private final float[] tileHover = new float[sections.length];
    private int hoveredIndex = -1;
    private long lastFrameMs = System.currentTimeMillis();

    public float measureContent(UiLayout layout) {
        int rows = (sections.length + UiTokens.SETTINGS_TILE_COLS - 1) / UiTokens.SETTINGS_TILE_COLS;
        float side = tileSide(layout);
        return UiTokens.ROOT_CONTENT_GAP
                + rows * side
                + (rows - 1) * UiTokens.SETTINGS_TILE_GAP;
    }

    /** Tile width from the column grid; the tile is square, so this is also its height. */
    public static float tileSide(UiLayout layout) {
        return (layout.list.w() - (UiTokens.SETTINGS_TILE_COLS - 1) * UiTokens.SETTINGS_TILE_GAP)
                / (float) UiTokens.SETTINGS_TILE_COLS;
    }

    public static UiLayout.Rect tileRect(UiLayout layout, int index, float scrollY) {
        float side = tileSide(layout);
        int col = index % UiTokens.SETTINGS_TILE_COLS;
        int row = index / UiTokens.SETTINGS_TILE_COLS;
        return new UiLayout.Rect(
                layout.list.x() + col * (side + UiTokens.SETTINGS_TILE_GAP),
                layout.list.y() + UiTokens.ROOT_CONTENT_GAP
                        + row * (side + UiTokens.SETTINGS_TILE_GAP) - scrollY,
                side, side);
    }

    public void render(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        long now = System.currentTimeMillis();
        float dt = Math.min(50.0F, Math.max(1.0F, now - lastFrameMs));
        lastFrameMs = now;

        int hovered = -1;
        canvas.save();
        try {
            SkiaDraw.clip(canvas, layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h(), 0.0F);
            for (int i = 0; i < sections.length; i++) {
                UiLayout.Rect tile = tileRect(layout, i, scrollY);
                if (tile.bottom() < layout.list.y() || tile.y() > layout.list.bottom()) {
                    continue;
                }
                boolean over = vmx >= tile.x() && vmx <= tile.right()
                        && vmy >= tile.y() && vmy <= tile.bottom();
                if (over) {
                    hovered = i;
                }
                // Animated value only: forcing it to 1 while hovered is what
                // made the highlight snap in instead of fading in.
                drawTile(canvas, tile, sections[i], tileHover[i]);
            }
        } finally {
            canvas.restore();
        }
        hoveredIndex = hovered;
        for (int i = 0; i < tileHover.length; i++) {
            tileHover[i] = UiMotion.approach(tileHover[i], i == hovered ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
        }
    }

    /** Returns the section under the pointer, or null. Uses the render geometry. */
    public SettingsSection hit(float vmx, float vmy, UiLayout layout, float scrollY) {
        for (int i = 0; i < sections.length; i++) {
            UiLayout.Rect tile = tileRect(layout, i, scrollY);
            if (vmx >= tile.x() && vmx <= tile.right() && vmy >= tile.y() && vmy <= tile.bottom()) {
                return sections[i];
            }
        }
        return null;
    }

    private void drawTile(Canvas canvas, UiLayout.Rect tile, SettingsSection section, float hover) {
        UiCards.drawCard(canvas, tile.x(), tile.y(), tile.w(), tile.h(),
                UiTokens.settingsTileRadius(), hover);

        // One vertically centred group: glyph above, single label below. Both
        // are horizontally centred, so nothing in the tile depends on text
        // length and every tile reads as the same shape.
        Font titleFont = FontManager.boldFont(UiTokens.SETTINGS_TILE_TITLE);
        float textH = SkiaFontRenderer.textHeight(titleFont);
        float groupH = UiTokens.SETTINGS_TILE_ICON + UiTokens.SETTINGS_TILE_TEXT_GAP + textH;
        float groupTop = tile.y() + (tile.h() - groupH) / 2.0F;
        float cx = tile.x() + tile.w() / 2.0F;

        drawIconCentered(canvas, iconFor(section), cx, groupTop + UiTokens.SETTINGS_TILE_ICON / 2.0F,
                UiTokens.SETTINGS_TILE_ICON, com.atom.chat.config.AtomChatConfig.get().textPrimaryColor);

        float labelCenterY = groupTop + UiTokens.SETTINGS_TILE_ICON
                + UiTokens.SETTINGS_TILE_TEXT_GAP + textH / 2.0F;
        float maxW = tile.w() - UiTokens.s(16);
        SkiaFontRenderer.drawTextCentered(canvas, titleFont,
                SkiaFontRenderer.truncate(titleFont, title(section), maxW), cx, labelCenterY,
                com.atom.chat.config.AtomChatConfig.get().textPrimaryColor);
    }

    public static Path iconFor(SettingsSection section) {
        return switch (section) {
            case APPEARANCE -> AppIcons.ICON_SETTINGS_APPEARANCE_PATH;
            case CHAT -> AppIcons.ICON_TAB_CHAT_PATH;
            case PRIVACY -> AppIcons.ICON_SETTINGS_PRIVACY_PATH;
            case ABOUT -> AppIcons.ICON_SETTINGS_ABOUT_PATH;
        };
    }

    public static String title(SettingsSection section) {
        return tr(key(section));
    }

    private static String key(SettingsSection section) {
        return switch (section) {
            case APPEARANCE -> "atomchat.settings.appearance";
            case CHAT -> "atomchat.settings.chat";
            case PRIVACY -> "atomchat.settings.privacy";
            case ABOUT -> "atomchat.settings.about";
        };
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
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
