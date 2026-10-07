package com.atom.chat.chat;

import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Font;
import net.minecraft.text.Text;

/**
 * Static helpers around CICode image messages, shared by the screen and the
 * message list view: URL extraction, metadata parsing, on-screen bubble
 * sizing, the image placeholder marker and width-aware truncation.
 *
 * <p>The grammar itself lives in {@link ImageCode}: this class only adapts it to
 * the Skia/Minecraft side. Keeping one grammar is what stops the panel and the
 * vanilla-HUD rewrite from disagreeing about whether a message is an image.
 */
public final class Cicodes {

    private Cicodes() {
    }

    /** url / name / intrinsic size carried by a CICode. width and height are 0 in codes written before they existed. */
    public record ImageMeta(String url, String name, int width, int height) {
    }

    /** The text's image code, or null when it has none — or has one with no usable url. */
    public static ImageMeta parseImageMeta(String text) {
        ImageCode.Meta meta = ImageCode.parse(text);
        return meta == null ? null
                : new ImageMeta(meta.url(), meta.name(), meta.width(), meta.height());
    }

    /**
     * On-screen size of an image bubble: the intrinsic size scaled down to fit
     * IMAGE_MAX_W x IMAGE_MAX_H, never upscaled, so a small picture is never
     * blown up into a blur. Messages with no usable size — codes written before
     * w/h existed, or images whose header ImageIO cannot read — fall back to the
     * placeholder box, which is also what is drawn until the image downloads.
     */
    public static float[] imageBubbleSize(ImageMeta meta, float maxWidth) {
        float maxW = Math.min(UiTokens.IMAGE_MAX_W, maxWidth - UiTokens.BUBBLE_RETRACT - UiTokens.s(30));
        float maxH = UiTokens.IMAGE_MAX_H;
        if (meta == null || meta.width() <= 0 || meta.height() <= 0) {
            return new float[]{maxW, maxH};
        }
        float scale = Math.min(1.0F, Math.min(maxW / meta.width(), maxH / meta.height()));
        return new float[]{Math.max(1.0F, meta.width() * scale), Math.max(1.0F, meta.height() * scale)};
    }

    /** The image url carried by the text, or null when there is no usable code. */
    public static String extractImageUrl(String text) {
        ImageCode.Meta meta = ImageCode.parse(text);
        return meta == null ? null : meta.url();
    }

    public static boolean isImagePlaceholder(String text) {
        return text != null && (text.equals(tr("atomchat.hud.image"))
                || text.equals("[图片]") || text.equalsIgnoreCase("[image]"));
    }

    public static String truncateToWidth(Font font, String text, float maxW) {
        if (text == null || SkiaFontRenderer.getStringWidth(font, text) <= maxW) {
            return text;
        }
        if (maxW <= 0.0F) {
            // Zero/negative budget: the old per-char walk still left one char
            // plus ellipsis, while SkiaFontRenderer.truncate hands back the
            // untouched text — keep the old answer so a squeezed quote pill
            // never regains its spill.
            return text.substring(0, 1) + "…";
        }
        // Binary search over the prefix (O(log n) measurements): the old walk
        // re-shaped a fresh "t + …" string per character trimmed, which both
        // allocated O(n) strings per call and flushed WIDTH_CACHE with keys
        // that could never hit.
        return SkiaFontRenderer.truncate(font, text, maxW);
    }

    /** Minecraft language lookup, same rule as the screen's tr(). */
    private static String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }
}
