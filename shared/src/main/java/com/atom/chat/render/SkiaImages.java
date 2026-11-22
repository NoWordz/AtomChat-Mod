package com.atom.chat.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.RRect;
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
    private static final Paint PAINT = new Paint().setAntiAlias(CornerGeometry.ANTIALIAS);

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
        Image source = ImageQuality.scaledForLogical(image, Math.max(width, height));
        Rect src = Rect.makeWH(source.getWidth(), source.getHeight());
        drawRounded(canvas, source, src, x, y, width, height, radius, mode);
    }

    public static void drawImageCover(Canvas canvas, Image image, float x, float y, float width, float height,
                                      float radius, SamplingMode mode) {
        if (image == null || width <= 0F || height <= 0F) return;
        float sourceWidth = Math.min(image.getWidth(), image.getHeight() * width / height);
        float sourceHeight = Math.min(image.getHeight(), image.getWidth() * height / width);
        Rect src = Rect.makeXYWH((image.getWidth() - sourceWidth) / 2F, (image.getHeight() - sourceHeight) / 2F,
                sourceWidth, sourceHeight);
        drawRounded(canvas, image, src, x, y, width, height, radius, mode);
    }

    private static void drawRounded(Canvas canvas, Image image, Rect src, float x, float y, float width, float height,
                                    float radius, SamplingMode mode) {
        if (radius <= 0F) {
            canvas.drawImageRect(image, src, Rect.makeXYWH(x, y, width, height), mode, PAINT, false);
        } else {
            drawShape(canvas, image, src, RRect.makeXYWH(x, y, width, height, radius), mode);
        }
    }

    static void drawShape(Canvas canvas, Image image, Rect src, RRect shape, SamplingMode mode) {
        // Give the shape sole ownership of edge coverage. An AA image rectangle
        // under an AA clip multiplies coverage where their edges coincide.
        float sx = shape.getWidth() / src.getWidth(), sy = shape.getHeight() / src.getHeight();
        var placement = new Matrix33(sx, 0F, shape.getLeft() - sx * src.getLeft(),
                0F, sy, shape.getTop() - sy * src.getTop(), 0F, 0F, 1F);
        try (var shader = image.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, mode, placement)) {
            try {
                CornerGeometry.drawRRect(canvas, shape, PAINT.setShader(shader), true);
            } finally {
                PAINT.setShader(null);
            }
        }
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
