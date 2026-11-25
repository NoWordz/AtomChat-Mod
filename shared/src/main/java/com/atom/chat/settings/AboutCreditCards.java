package com.atom.chat.settings;

import com.atom.chat.config.GraphicsQuality;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.CornerGeometry;
import com.atom.chat.ui.PressScale;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.render.SkiaImages;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.RRect;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.atom.lib.design.atomchat.UiTokens.s;

/** Compact project links backed by bundled artwork from the project's own published assets. */
final class AboutCreditCards {
    static final float WIDTH = s(80); // bare-value: narrower than a preset preview
    static final float LOGO = s(52); // bare-value: square target for the project mark
    static final float TOP = UiTokens.SETTINGS_ROW_PAD;
    static final float ROW_HEIGHT = TOP + LOGO + s(34); // bare-value: up to two caption lines and bottom spacing
    private static final float LOGO_RADIUS = 8F;
    private static final float TWO_LINE_FONT = s(10); // bare-value: retain the full name on a narrow tile
    private static final Map<String, Optional<Image>> LOGOS = new HashMap<>();

    private AboutCreditCards() {}

    static UiLayout.Rect logoRect(float tileX, float rowY) {
        return new UiLayout.Rect(tileX + (WIDTH - LOGO) / 2F, rowY + TOP, LOGO, LOGO);
    }

    static void draw(Canvas canvas, String key, String title, float tileX, float rowY, int ink) {
        draw(canvas, key, title, tileX, rowY, ink, null);
    }

    static void draw(Canvas canvas, String key, String title, float tileX, float rowY, int ink, PressScale motion) {
        String asset = key.substring(key.lastIndexOf('.') + 1);
        String filename = asset.equals("thirdparty") ? "skija" : asset;
        String initials = switch (asset) {
            case "flatlaf" -> "FL";
            case "nfde" -> "NFD";
            default -> title.substring(0, 1);
        };
        drawTile(canvas, "/assets/atomchat/creditmarks/" + filename + ".png", title, initials,
                tileX, rowY, ink, motion, SamplingMode.LINEAR, 0, false);
    }

    static void drawQuality(Canvas canvas, GraphicsQuality quality, String title,
                            float tileX, float rowY, int ink, PressScale motion, int selection) {
        String asset = quality == GraphicsQuality.BALANCED ? "/assets/atomchat/mod_logo.png"
                : "/assets/atomchat/quality/" + quality.id() + ".png";
        SamplingMode sampling = quality == GraphicsQuality.PERFORMANCE ? SamplingMode.DEFAULT : SamplingMode.LINEAR;
        drawTile(canvas, asset, title, title.substring(0, 1), tileX, rowY, ink, motion, sampling, selection, true);
    }

    private static void drawTile(Canvas canvas, String resource, String title, String initials,
                                 float tileX, float rowY, int ink, PressScale motion, SamplingMode sampling,
                                 int selection, boolean settingsContour) {
        var box = logoRect(tileX, rowY);
        float centerX = box.x() + box.w() / 2F;
        float centerY = box.y() + box.h() / 2F;
        var theme = AtomChatAppearance.theme();
        float radius = settingsContour ? UiTokens.settingsTileRadius(theme) : UiTokens.radius(LOGO_RADIUS, theme);
        var logo = LOGOS.computeIfAbsent(resource, AboutCreditCards::loadLogo).orElse(null);
        if (motion != null) motion.begin(canvas, centerX, centerY);
        try {
            if (logo != null) {
                if (settingsContour) {
                    SkiaImages.drawContourImage(canvas, logo,
                            RRect.makeXYWH(box.x(), box.y(), box.w(), box.h(), radius), sampling);
                } else {
                    SkiaImages.drawRoundedImage(canvas, logo, box.x(), box.y(), box.w(), box.h(), radius, sampling);
                }
            } else {
                var font = FontManager.boldFont(UiTokens.FONT_TITLE);
                SkiaFontRenderer.drawTextCentered(canvas, font, initials, centerX, centerY, ink);
            }
            if (selection != 0) {
                CornerGeometry.drawEdgeHighlight(canvas, box.x(), box.y(), box.w(), box.h(), radius,
                        UiTokens.SCALE * 2F, selection);
            }
        } finally {
            if (motion != null) canvas.restore();
        }
        var font = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        var lines = SkiaFontRenderer.wrapWords(font, title, WIDTH);
        if (lines.size() > 1) {
            font = FontManager.font(TWO_LINE_FONT);
            lines = SkiaFontRenderer.wrapWords(font, title, WIDTH);
        }
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float labelY = SettingsPreviewCard.captionY(box.y(), box.h());
        for (String line : lines) {
            SkiaFontRenderer.drawTextCentered(canvas, font, line, centerX, labelY, ink);
            labelY += lineHeight;
        }
    }

    private static Optional<Image> loadLogo(String resource) {
        try (var stream = AboutCreditCards.class.getResourceAsStream(resource)) {
            if (stream != null) return Optional.ofNullable(Image.makeFromEncoded(stream.readAllBytes()));
        } catch (IOException | IllegalArgumentException ignored) {
            // Missing bundled artwork still has a clickable text label.
        }
        return Optional.empty();
    }
}
