package com.atom.chat.ui;

import com.atom.chat.render.CornerGeometry;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.render.SoftShadow;
import com.atom.lib.design.atomchat.Design;
import com.atom.lib.design.atomchat.DesignHost;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;

/** Appearance owned by AtomChat. Other mods can continue using the library's legacy defaults. */
public final class AtomChatAppearance {
    private static final Design.RippleSink RIPPLES = ClickRipples::region;
    private static volatile Theme current;

    private AtomChatAppearance() {}

    public static DesignHost theme() {
        AtomChatConfig config = AtomChatConfig.get();
        Theme theme = current;
        if (theme == null || !theme.matches(config)) {
            theme = new Theme(config.effectiveCornerRadius(), config.halfScreenMode, config.accentColor,
                    config.textSecondaryColor, config.cardTint, config.cardColor, config.panelBgColor);
            current = theme;
        }
        return theme;
    }

    public static void drawCard(Canvas canvas, float x, float y, float width, float height,
                                float radius, float hover) {
        DesignHost palette = theme();
        int fill = UiTokens.cardFill(palette);
        if (SoftShadow.enabled()) {
            if ((fill >>> 24) != 255) {
                com.atom.chat.render.CardBackdrop.draw(canvas, x, y, width, height, radius);
            }
            RIPPLES.region(x, y, width, height, radius, fill);
            SoftShadow.drawCard(canvas, x, y, width, height, radius);
        }
        drawFlatCard(canvas, x, y, width, height, radius, hover, fill, UiTokens.hairline(palette));
    }

    public static void drawPanelOverlayCard(Canvas canvas, float x, float y, float width, float height,
                                            float radius, float hover, int fill) {
        if (SoftShadow.enabled()) {
            RIPPLES.region(x, y, width, height, radius, fill);
            SoftShadow.drawFloating(canvas, x, y, width, height, radius);
        }
        drawFlatCard(canvas, x, y, width, height, radius, hover, fill, UiTokens.hairline(theme()));
    }

    public static void drawOverlayCard(Canvas canvas, float x, float y, float width, float height,
                                       float radius, float hover, int shadow, float blur, int fill) {
        if (SoftShadow.enabled()) {
            RIPPLES.region(x, y, width, height, radius, fill);
            SoftShadow.drawRounded(canvas, x, y, width, height, radius, blur, shadow);
        }
        drawFlatCard(canvas, x, y, width, height, radius, hover, fill, UiTokens.hairline(theme()));
    }

    static void drawFlatCard(Canvas canvas, float x, float y, float width, float height,
                             float radius, float hover, int fill, int outline) {
        CornerGeometry.drawRoundedRect(canvas, x, y, width, height, radius, fill);
        CornerGeometry.drawEdgeHighlight(canvas, x, y, width, height, radius, UiTokens.s(1F), outline); // bare-value: matches AtomLib's content card hairline
        if (hover > .01F) {
            CornerGeometry.drawRoundedRect(canvas, x, y, width, height, radius, UiTokens.cardHover(hover, theme()));
        }
    }

    private record Theme(float cornerRadius, boolean halfScreenMode, int accentColor,
                         int textSecondaryColor, float cardTint, int cardColor,
                         int panelBackgroundColor) implements DesignHost {
        boolean matches(AtomChatConfig config) {
            return Float.compare(cornerRadius, config.effectiveCornerRadius()) == 0
                    && halfScreenMode == config.halfScreenMode
                    && accentColor == config.accentColor
                    && textSecondaryColor == config.textSecondaryColor
                    && Float.compare(cardTint, config.cardTint) == 0
                    && cardColor == config.cardColor
                    && panelBackgroundColor == config.panelBgColor;
        }
    }
}
