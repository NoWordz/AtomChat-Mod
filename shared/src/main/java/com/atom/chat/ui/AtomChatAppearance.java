package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.lib.design.atomchat.Design;
import com.atom.lib.design.atomchat.DesignHost;
import com.atom.lib.design.atomchat.UiCards;
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
            theme = new Theme(config.cornerRadius, config.halfScreenMode, config.accentColor,
                    config.textSecondaryColor, config.cardTint, config.cardColor, config.panelBgColor);
            current = theme;
        }
        return theme;
    }

    public static void drawCard(Canvas canvas, float x, float y, float width, float height,
                                float radius, float hover) {
        if ((com.atom.lib.design.atomchat.UiTokens.cardFill(theme()) >>> 24) != 255) {
            com.atom.chat.render.CardBackdrop.draw(canvas, x, y, width, height, radius);
        }
        UiCards.drawCard(canvas, x, y, width, height, radius, hover, theme(), RIPPLES);
    }

    public static void drawOverlayCard(Canvas canvas, float x, float y, float width, float height,
                                       float radius, float hover, int shadow, float blur, int fill) {
        UiCards.drawCard(canvas, x, y, width, height, radius, hover, shadow, blur, fill, theme(), RIPPLES);
    }

    private record Theme(float cornerRadius, boolean halfScreenMode, int accentColor,
                         int textSecondaryColor, float cardTint, int cardColor,
                         int panelBackgroundColor) implements DesignHost {
        boolean matches(AtomChatConfig config) {
            return Float.compare(cornerRadius, config.cornerRadius) == 0
                    && halfScreenMode == config.halfScreenMode
                    && accentColor == config.accentColor
                    && textSecondaryColor == config.textSecondaryColor
                    && Float.compare(cardTint, config.cardTint) == 0
                    && cardColor == config.cardColor
                    && panelBackgroundColor == config.panelBgColor;
        }
    }
}
