package com.atom.chat.settings;

import com.atom.chat.render.CornerGeometry;
import com.atom.chat.render.SoftShadow;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;

/** The media surface and caption spacing shared by settings preview cards. */
final class SettingsPreviewCard {
    private static final float TITLE_OFFSET = UiTokens.s(11); // bare-value: preview card caption center below its thumbnail

    private SettingsPreviewCard() {}

    static void drawSurface(Canvas canvas, float x, float y, float width, float height, float radius, int ink) {
        SoftShadow.drawCard(canvas, x, y, width, height, radius);
        CornerGeometry.drawRoundedRect(canvas, x, y, width, height, radius, ink);
    }

    static float captionY(float top, float height) {
        return top + height + TITLE_OFFSET;
    }
}
