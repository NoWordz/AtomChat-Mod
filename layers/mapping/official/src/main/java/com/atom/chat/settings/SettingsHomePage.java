package com.atom.chat.settings;

import com.atom.chat.api.ui.SettingsUi;
import com.atom.chat.api.ui.UiContributions;
import com.atom.chat.nav.NavPage;
import com.atom.chat.render.CornerGeometry;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.chat.font.FontManager;
import com.atom.chat.nav.PageNavAxis;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.render.OutlineIcons;
import com.atom.chat.render.CardShadowReach;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.SettingsIcons;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.PressScale;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiStagger;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.lib.design.atomchat.AppIcons;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
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
    private java.util.List<UiContributions.Entry<SettingsUi.HomeEntry>> sections = SettingsUi.resolvedHome();
    private float[] tileHover = new float[sections.size()];
    // Per-tile bounce scale, indexed like tileHover. One instance per tile, so no
    // two tiles can ever drive the same spring; the array fills lazily because
    // PressScale only comes out of its factory methods.
    private PressScale[] tileScale = new PressScale[sections.size()];
    /** Page-entrance stagger: tiles rise in one after another on a tab arrival. */
    private final UiStagger.Entrance entrance = new UiStagger.Entrance();
    private long lastFrameMs = System.currentTimeMillis();

    /** Arms the tile entrance — the screen calls this on a tab arrival. */
    public void markEntered() {
        markEntered(UiStagger.frameTimeMillis());
    }

    public void markEntered(long nowMs) {
        entrance.markEntered(nowMs);
        staggerHits.clear();
    }
    private final com.atom.chat.ui.StaggerHitMap staggerHits = new com.atom.chat.ui.StaggerHitMap();
    public com.atom.chat.ui.StaggerHitMap staggerHits() { return staggerHits; }


    private void refreshEntries() {
        var resolved = SettingsUi.resolvedHome();
        if (sections.equals(resolved)) return;
        sections = resolved;
        tileHover = new float[sections.size()];
        tileScale = new PressScale[sections.size()];
    }

    public float measureContent(UiLayout layout) {
        refreshEntries();
        if (sections.isEmpty()) return 0F;
        int rows = (sections.size() + UiTokens.SETTINGS_TILE_COLS - 1) / UiTokens.SETTINGS_TILE_COLS;
        float side = tileSide(layout);
        return UiTokens.ROOT_CONTENT_GAP
                + rows * side
                + (rows - 1) * SettingsUi.homeSpacing().tileGap()
                + UiTokens.ROW_CLIP_INSET;
    }

    /**
     * Tile width from the column grid; the tile is square, so this is also its
     * height. The grid is inset by {@link UiTokens#ROW_CLIP_INSET} on both
     * sides — the same clearance the settings rows use — because edge tiles
     * sit flush against the scroll clip and the hover bounce (plus its
     * overshoot) used to shear their outer rounded corners flat against it.
     * The clip itself stays full width: it is the scroll viewport, the tile
     * is what gets inset.
     */
    public static float tileSide(UiLayout layout) {
        var spacing = SettingsUi.homeSpacing();
        float gridW = layout.list.w() - spacing.sideInset() * 2F;
        float cell = (gridW - (UiTokens.SETTINGS_TILE_COLS - 1) * spacing.tileGap()) / UiTokens.SETTINGS_TILE_COLS;
        return Math.max(0F, Math.min(com.atom.chat.ui.LayoutTokens.SETTINGS_TILE_MAX, cell));
    }

    public static UiLayout.Rect tileRect(UiLayout layout, int index, float scrollY) {
        float side = tileSide(layout);
        int col = index % UiTokens.SETTINGS_TILE_COLS;
        int row = index / UiTokens.SETTINGS_TILE_COLS;
        return new UiLayout.Rect(
                layout.list.x() + (layout.list.w() - side * UiTokens.SETTINGS_TILE_COLS
                        - (UiTokens.SETTINGS_TILE_COLS - 1) * SettingsUi.homeSpacing().tileGap()) / 2F
                        + col * (side + SettingsUi.homeSpacing().tileGap()),
                layout.list.y() + UiTokens.ROOT_CONTENT_GAP
                        + row * (side + SettingsUi.homeSpacing().tileGap()) - scrollY,
                side, side);
    }

    public void render(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        refreshEntries();
        staggerHits.clear();
        long now = UiStagger.frameTimeMillis();
        float dt = Math.min(50.0F, Math.max(1.0F, now - lastFrameMs));
        lastFrameMs = now;

        // Pointer pass and state updates first, drawing second: computing the
        // hover/spring targets before the draw loop means this frame's pointer
        // drives this frame's drawing. The old draw-then-update order lagged
        // every state change one frame behind.
        int hovered = -1;
        for (int i = 0; i < sections.size(); i++) {
            UiLayout.Rect tile = tileRect(layout, i, scrollY);
            if (tile.bottom() < layout.list.y() || tile.y() > layout.list.bottom()) {
                continue;
            }
            if (vmx >= tile.x() && vmx <= tile.right()
                    && vmy >= tile.y() && vmy <= tile.bottom()) {
                hovered = i;
            }
        }
        for (int i = 0; i < tileHover.length; i++) {
            // Animated value only: forcing it to 1 while hovered is what
            // made the highlight snap in instead of fading in.
            tileHover[i] = UiMotion.approach(tileHover[i], i == hovered ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
        }
        // Hover is the only state this page keeps: the screen routes clicks
        // straight to hit() and never reports a press, so the pressed phase
        // stays false and the hover lift is the whole bounce.
        for (int i = 0; i < tileScale.length; i++) {
            if (tileScale[i] == null) {
                tileScale[i] = PressScale.bounce();
            }
            // The scaled shape is the tile square itself.
            tileScale[i].update(i == hovered, false, dt, Animations.enabled(), tileSide(layout));
        }

        UiStagger.Axis axis = PageNavAxis.current();
        canvas.save();
        try {
            // SLIDE starts every tile up to ITEM_STYLE_SLIDE_PX to the right,
            // so the viewport clip would shave the rounded right edge off the
            // tile's early frames. Widening only the right edge by exactly that
            // budget keeps the corner whole while travel remains, and the clip
            // drops back to the viewport once every slot has landed.
            float driftClipPad = axis == UiStagger.Axis.HORIZONTAL && !UiStagger.itemsSettled(entrance, now)
                    ? UiStagger.ITEM_STYLE_SLIDE_PX : 0.0F;
            CornerGeometry.clip(canvas, layout.list.x() - CardShadowReach.PAD, layout.list.y(),
                    layout.list.w() + driftClipPad + CardShadowReach.PAD * 2, layout.list.h(), 0.0F);
            for (int i = 0; i < sections.size(); i++) {
                UiLayout.Rect tile = tileRect(layout, i, scrollY);
                if (tile.bottom() < layout.list.y() || tile.y() > layout.list.bottom()) {
                    continue;
                }
                // Page-entrance stagger: the tile's fade, its lift and its
                // style channel all come from ONE progress value, and the axis
                // decides which channels may move — so a tile is never opaque
                // while shifted, and SLIDE never gives it a vertical leg.
                float tileAlpha = UiStagger.itemAlpha(entrance, i, now);
                float tileRise = UiStagger.itemRise(entrance, i, now, axis);
                staggerHits.add(tile, tileAlpha >= .995F ? 0 : UiStagger.itemStyleSlidePx(entrance, i, now, axis),
                        tileAlpha >= .995F ? 0 : tileRise,
                        tileAlpha >= .995F ? 1 : UiStagger.itemStyleZoomScale(entrance, i, now, axis));
                if (tileAlpha >= 0.995F) {
                    drawTile(canvas, tile, sections.get(i).value(), tileHover[i], tileScale[i]);
                } else {
                    float tileDx = UiStagger.itemStyleSlidePx(entrance, i, now, axis);
                    canvas.save();
                    float maskOpacity = com.atom.chat.render.CardBackdrop.pushMaskOpacity(tileAlpha);
                    try (Paint layer = new Paint().setAlphaf(tileAlpha)) {
                        // The layer is the tile's travel union plus the shadow's
                        // reach. saveLayer is a hard clip, so a layer sized to
                        // the slot alone sawed the soft shadow into a straight
                        // line along its top edge while the tile was still
                        // travelling.
                        canvas.saveLayer(CardShadowReach.layerRect(
                                tile.x(), tile.y(), tile.w(), tile.h(), tileRise, tileDx), layer);
                        canvas.translate(0.0F, tileRise);
                        if (axis == UiStagger.Axis.VERTICAL) {
                            com.atom.chat.ui.PanelOpenMotion.applyLayerScale(canvas, tile,
                                    UiStagger.itemStyleZoomScale(entrance, i, now, axis));
                        } else if (tileDx != 0.0F) {
                            canvas.translate(tileDx, 0.0F);
                        }
                        drawTile(canvas, tile, sections.get(i).value(), tileHover[i], tileScale[i]);
                        canvas.restore();
                    } finally {
                        com.atom.chat.render.CardBackdrop.restoreMaskOpacity(maskOpacity);
                        canvas.restore();
                    }
                }
            }
        } finally {
            canvas.restore();
        }
    }


    /** Returns the section under the pointer, or null. Uses the render geometry. */
    public SettingsSection hit(float vmx, float vmy, UiLayout layout, float scrollY) {
        NavPage page = hitEntry(vmx, vmy, layout, scrollY);
        return page == null ? null : page.section();
    }

    public NavPage hitEntry(float vmx, float vmy, UiLayout layout, float scrollY) {
        refreshEntries();
        if (!layout.list.contains(vmx, vmy)) return null;
        for (int i = 0; i < sections.size(); i++) {
            UiLayout.Rect tile = tileRect(layout, i, scrollY);
            if (tile.w() > 0 && tile.contains(vmx, vmy)) {
                return SettingsUi.destination(sections.get(i).value());
            }
        }
        return null;
    }

    private void drawTile(Canvas canvas, UiLayout.Rect tile, SettingsUi.HomeEntry entry, float hover,
                         PressScale scale) {
        // The bounce wraps the whole tile - background, glyph and label - around
        // the tile's own centre. begin() saves even at rest, so the matching
        // restore below is unconditional: the offscreen render test asserts the
        // canvas save stack balances on every frame.
        if (scale != null) {
            scale.begin(canvas, tile.x() + tile.w() / 2.0F, tile.y() + tile.h() / 2.0F);
        }
        try {
            AtomChatAppearance.drawCard(canvas, tile.x(), tile.y(), tile.w(), tile.h(),
                    UiTokens.settingsTileRadius(AtomChatAppearance.theme()), hover);

            Font titleFont = FontManager.boldFont(UiTokens.SETTINGS_TILE_TITLE);
            float maxW = Math.max(1F, tile.w() - UiTokens.s(16));
            String label = tr(entry.titleKey());
            var lines = SkiaFontRenderer.getStringWidth(titleFont, label) <= maxW
                    ? java.util.List.of(label) : SettingsRowText.wrapCopy(titleFont, label, maxW);
            float textH = SkiaFontRenderer.textHeight(titleFont);
            float lineH = SkiaFontRenderer.getHeight(titleFont);
            float labelH = textH + Math.max(0, lines.size() - 1) * lineH;
            float groupH = UiTokens.SETTINGS_TILE_ICON + UiTokens.SETTINGS_TILE_TEXT_GAP + labelH;
            float groupTop = tile.y() + (tile.h() - groupH) / 2.0F;
            float cx = tile.x() + tile.w() / 2.0F;
            int ink = com.atom.chat.config.AtomChatConfig.get().textPrimaryColor;

            drawIconCentered(canvas, entry.icon(), cx, groupTop + UiTokens.SETTINGS_TILE_ICON / 2.0F,
                    UiTokens.SETTINGS_TILE_ICON, ink);

            float labelCenterY = groupTop + UiTokens.SETTINGS_TILE_ICON
                    + UiTokens.SETTINGS_TILE_TEXT_GAP + textH / 2.0F;
            for (String line : lines) {
                SkiaFontRenderer.drawTextCentered(canvas, titleFont, line, cx, labelCenterY, ink);
                labelCenterY += lineH;
            }
        } finally {
            if (scale != null) {
                canvas.restore();
            }
        }
    }

    public static Path iconFor(SettingsSection section) {
        return switch (section) {
            case APPEARANCE -> SettingsIcons.PALETTE;
            case CHAT -> SettingsIcons.MESSAGES;
            case PRIVACY -> AppIcons.ICON_SETTINGS_PRIVACY_PATH;
            case ABOUT -> SettingsIcons.INFO_CIRCLE;
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
        OutlineIcons.draw(canvas, icon, cx, cy, size, color);
    }
}
