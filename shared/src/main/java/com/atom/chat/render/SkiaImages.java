package com.atom.chat.render;

import com.atom.lib.design.atomchat.SkiaDraw;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;

/**
 * The image blits this UI uses, with minification quality handled in one place.
 *
 * <p>Every cached bitmap here is decoded larger than it is drawn — avatars up
 * to 256 px shown at 40-74 device px, banners and wallpapers at 1024 shown in a
 * 600 px panel. A GPU {@code drawImageRect} only takes a 2x2 tap, so it reads a
 * handful of the source texels and the picture reads soft. {@link ImageQuality}
 * pre-halves the bitmap to within 2x of the drawn size; these wrappers are the
 * single place a caller has to go through to get that, so a new draw site
 * cannot silently reintroduce the mush.</p>
 *
 * <p>Buckets: shared, no Minecraft names. Render thread only.</p>
 */
public final class SkiaImages {
    private static final Paint PAINT = new Paint().setAntiAlias(true);

    private SkiaImages() {
    }

    /** Rounded-clipped image at its natural proportions (square unless the box is not). */
    public static void drawRoundedImage(Canvas canvas, Image image, float x, float y,
                                        float width, float height, float radius) {
        drawRoundedImage(canvas, image, x, y, width, height, radius, SamplingMode.LINEAR);
    }

    public static void drawRoundedImage(Canvas canvas, Image image, float x, float y,
                                        float width, float height, float radius, SamplingMode mode) {
        if (image == null || width <= 0.0F || height <= 0.0F) {
            return;
        }
        SkiaDraw.drawRoundedImage(canvas, ImageQuality.scaledForLogical(image, Math.max(width, height)),
                x, y, width, height, radius, mode);
    }

    /** Plain image blit with the same decimation policy as the rounded variant. */
    public static void drawImage(Canvas canvas, Image image, float x, float y, float width, float height) {
        drawImage(canvas, image, x, y, width, height, SamplingMode.LINEAR);
    }

    public static void drawImage(Canvas canvas, Image image, float x, float y, float width, float height,
                                 SamplingMode mode) {
        if (image == null || width <= 0.0F || height <= 0.0F) {
            return;
        }
        Image source = ImageQuality.scaledForLogical(image, Math.max(width, height));
        canvas.drawImageRect(source, Rect.makeWH(source.getWidth(), source.getHeight()),
                Rect.makeXYWH(x, y, width, height), mode, PAINT.setColor(0xFF000000), false);
    }
}
