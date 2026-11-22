package com.atom.chat.render;

import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;

import java.util.LinkedHashMap;

/** Outline painter with a small, phase-aligned coverage tile for common vector icons. */
public final class OutlineIcons {
    private static final boolean COVERAGE = Boolean.parseBoolean(System.getProperty("atomchat.localEdgeCoverage", "true"));
    private static final int MAX_TILES = 256;
    private static final int MAX_SIDE = 96;
    private static final int SUBPIXEL_PHASES = 16;
    private static final int SAMPLING = 2;
    private static final Paint STROKE = new Paint().setAntiAlias(CornerGeometry.ANTIALIAS).setMode(PaintMode.STROKE)
            .setStrokeCap(PaintStrokeCap.ROUND).setStrokeJoin(PaintStrokeJoin.ROUND);
    private static final Paint TINT = new Paint();
    private static final LinkedHashMap<Key, Image> TILES = new LinkedHashMap<>(32, .75F, true);

    private OutlineIcons() { }

    public static void draw(Canvas canvas, Path icon, float centerX, float centerY, float size, int color) {
        draw(canvas, icon, centerX, centerY, size, UiTokens.iconStroke(size), color);
    }

    public static void draw(Canvas canvas, Path icon, float centerX, float centerY, float size, float stroke, int color) {
        draw(canvas, icon, OutlineIconBounds.of(icon), centerX, centerY, size, stroke, color);
    }

    public static void draw(Canvas canvas, Path icon, Rect bounds, float centerX, float centerY,
                            float size, float stroke, int color) {
        draw(canvas, icon, bounds, centerX, centerY, size, stroke, color, COVERAGE);
    }

    public static void drawNative(Canvas canvas, Path icon, float x, float y, float size, int color) {
        draw(canvas, icon, OutlineIconBounds.of(icon), x, y, size, UiTokens.iconStroke(size), color, false);
    }

    private static void draw(Canvas canvas, Path icon, Rect bounds, float centerX, float centerY,
                             float size, float stroke, int color, boolean useCoverage) {
        if (bounds == null || bounds.isEmpty() || size <= 0F || stroke <= 0F) return;
        float fit = size / Math.max(bounds.getWidth(), bounds.getHeight());
        if (useCoverage && CornerGeometry.ANTIALIAS && (color >>> 24) != 0
                && drawCoverage(canvas, icon, bounds, centerX, centerY, fit, stroke, color)) return;
        drawPath(canvas, icon, bounds, centerX, centerY, fit, fit, stroke / fit, color);
    }

    private static boolean drawCoverage(Canvas canvas, Path path, Rect bounds, float x, float y,
                                        float fit, float stroke, int color) {
        var grid = EdgePixelGrid.from(canvas, false);
        if (grid == null) return false;
        float sx = grid.sx(), sy = grid.sy(), dx = grid.x(), dy = grid.y();
        float width = (bounds.getWidth() * fit + stroke) * sx;
        float height = (bounds.getHeight() * fit + stroke) * sy;
        if (Math.min(width, height) < 3F || Math.max(width, height) > MAX_SIDE - 4) return false;
        float px = Math.round((x * sx + dx) * SUBPIXEL_PHASES) / (float) SUBPIXEL_PHASES;
        float py = Math.round((y * sy + dy) * SUBPIXEL_PHASES) / (float) SUBPIXEL_PHASES;
        int left = (int) Math.floor(px - width / 2F) - 1, top = (int) Math.floor(py - height / 2F) - 1;
        var key = new Key(path, bounds, (int) Math.ceil(width) + 3, (int) Math.ceil(height) + 3,
                px - left, py - top, fit * sx, fit * sy, stroke / fit);
        var image = TILES.get(key);
        if (image == null) {
            try (var surface = Surface.makeRaster(ImageInfo.makeA8(key.width() * SAMPLING, key.height() * SAMPLING))) {
                if (surface == null) return false;
                var tile = surface.getCanvas();
                tile.clear(0);
                tile.scale(SAMPLING, SAMPLING);
                drawPath(tile, path, bounds, key.centerX(), key.centerY(),
                        key.fitX(), key.fitY(), key.stroke(), 0xFFFFFFFF);
                image = surface.makeImageSnapshot();
            }
            if (TILES.size() == MAX_TILES) {
                var oldest = TILES.values().iterator();
                oldest.next().close();
                oldest.remove();
            }
            TILES.put(key, image);
        }
        canvas.drawImageRect(image, Rect.makeWH(image.getWidth(), image.getHeight()),
                Rect.makeXYWH((left - dx) / sx, (top - dy) / sy, key.width() / sx, key.height() / sy),
                SamplingMode.LINEAR, TINT.setColor(color), true);
        return true;
    }

    private static void drawPath(Canvas canvas, Path path, Rect bounds, float x, float y,
                                 float sx, float sy, float stroke, int color) {
        int save = canvas.save();
        try {
            canvas.translate(x - (bounds.getLeft() + bounds.getRight()) / 2F * sx,
                    y - (bounds.getTop() + bounds.getBottom()) / 2F * sy);
            canvas.scale(sx, sy);
            canvas.drawPath(path, STROKE.setColor(color).setStrokeWidth(stroke));
        } finally {
            canvas.restoreToCount(save);
        }
    }

    static void release() {
        TILES.values().forEach(Image::close);
        TILES.clear();
    }

    private record Key(Path path, Rect bounds, int width, int height, float centerX, float centerY,
                       float fitX, float fitY, float stroke) {}
}
