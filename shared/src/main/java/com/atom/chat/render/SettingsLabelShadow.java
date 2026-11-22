package com.atom.chat.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;

import java.util.LinkedHashMap;

/** Render-thread cache of settings label shadows. The glyphs and icons still draw as vectors. */
public final class SettingsLabelShadow {
    private static final float PAD = 8F; // Same support as SkiaFontRenderer's capsule shadow.
    private static final int MAX_ENTRIES = 48;
    private static final int MAX_PIXELS_PER_LABEL = 20_480;
    private static final float PHASE_TOLERANCE = .0001F;
    private static final Paint FILTER_PAINT = new Paint();
    private static final LinkedHashMap<Key, Entry> CACHE = new LinkedHashMap<>(32, .75F, true);
    private static Projection projection;

    private record Key(Font font, String text, Path icon, float iconX, float iconY, float iconSize, ImageFilter filter) {}
    private record Projection(Canvas recording, Matrix33 target) {}
    private record Grid(float density, float phaseX, float phaseY, float left, float top, int width, int height) {
        boolean matches(Grid other) {
            return density == other.density && width == other.width && height == other.height
                    && Math.abs(phaseX - other.phaseX) <= PHASE_TOLERANCE
                    && Math.abs(phaseY - other.phaseY) <= PHASE_TOLERANCE;
        }
    }

    private static final class Entry {
        Grid lastPlacement;
        Grid bakedPlacement;
        Image image;

        void closeImage() {
            if (image != null) image.close();
            image = null;
            bakedPlacement = null;
        }
    }

    private SettingsLabelShadow() {}

    /** A settled Picture records locally, but its playback canvas owns the actual device grid. */
    public static void onDeviceGrid(Canvas recording, Matrix33 target, Runnable draw) {
        Projection previous = projection;
        projection = new Projection(recording, target);
        try {
            draw.run();
        } finally {
            projection = previous;
        }
    }

    public static void draw(Canvas c, Font font, String text, float x, float y) {
        draw(c, font, text, x, y, null, x, y, 0F);
    }

    public static void draw(Canvas c, Font font, String text, float x, float y,
                            Path icon, float iconX, float iconY, float iconSize) {
        if (text.isEmpty() && icon == null) return;
        if (!SoftShadow.enabled()) {
            drawInk(c, font, text, x, y + .85F, icon, iconX, iconY + .85F, iconSize, 0x66000000);
            return;
        }

        ImageFilter filter = SkiaFontRenderer.backingShadow();
        var metrics = font.getMetrics();
        float left = x;
        float right = x + SkiaFontRenderer.getStringWidth(font, text);
        float top = y + metrics.getAscent();
        float bottom = y + metrics.getDescent();
        if (icon != null) {
            left = Math.min(left, iconX - iconSize / 2F);
            top = Math.min(top, iconY - iconSize / 2F);
            right = Math.max(right, iconX + iconSize / 2F);
            bottom = Math.max(bottom, iconY + iconSize / 2F);
        }
        Rect bounds = Rect.makeLTRB(left - PAD, top - PAD, right + PAD, bottom + PAD);
        Grid grid = grid(c, bounds);
        if (grid != null) {
            Key key = new Key(font, text, icon, iconX - x, iconY - y, iconSize, filter);
            if (drawCached(c, key, grid, bounds, x, y)) return;
        }
        drawFiltered(c, font, text, x, y, icon, iconX, iconY, iconSize, bounds, filter);
    }

    private static Grid grid(Canvas canvas, Rect bounds) {
        if (projection == null || projection.recording() != canvas) return null;
        Matrix33 local = canvas.getLocalToDeviceAsMatrix33();
        float[] m = projection.target() == null ? local._mat : projection.target().makeConcat(local)._mat;
        float density = m[0];
        if (!(density > 0F) || density != UiPixelSnap.density() || density != m[4]
                || m[1] != 0F || m[3] != 0F || m[6] != 0F || m[7] != 0F || m[8] != 1F) return null;
        float left = bounds.getLeft() * density + m[2], top = bounds.getTop() * density + m[5];
        float deviceLeft = (float) Math.floor(left), deviceTop = (float) Math.floor(top);
        int width = (int) (Math.ceil(bounds.getRight() * density + m[2]) - deviceLeft);
        int height = (int) (Math.ceil(bounds.getBottom() * density + m[5]) - deviceTop);
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS_PER_LABEL) return null;
        return new Grid(density, left - deviceLeft, top - deviceTop,
                bounds.getLeft() - (left - deviceLeft) / density,
                bounds.getTop() - (top - deviceTop) / density, width, height);
    }

    private static boolean drawCached(Canvas canvas, Key key, Grid grid, Rect bounds, float x, float y) {
        Entry entry = CACHE.get(key);
        if (entry == null) {
            if (CACHE.size() == MAX_ENTRIES) {
                var oldest = CACHE.values().iterator();
                oldest.next().closeImage();
                oldest.remove();
            }
            entry = new Entry();
            entry.lastPlacement = grid;
            CACHE.put(key, entry);
            return false;
        }
        boolean settled = grid.matches(entry.lastPlacement);
        entry.lastPlacement = grid;
        if (entry.bakedPlacement == null || !grid.matches(entry.bakedPlacement)) {
            // Avoid allocating a succession of new masks during fractional scroll or animation.
            if (!settled) return false;
            entry.closeImage();
            try (Surface surface = Surface.makeRasterN32Premul(grid.width(), grid.height())) {
                Canvas raster = surface.getCanvas();
                raster.scale(grid.density(), grid.density());
                raster.translate(-grid.left(), -grid.top());
                drawFiltered(raster, key.font(), key.text(), x, y, key.icon(), x + key.iconX(), y + key.iconY(),
                        key.iconSize(), bounds, key.filter());
                entry.image = surface.makeImageSnapshot();
                entry.bakedPlacement = grid;
            }
        }
        canvas.drawImageRect(entry.image, Rect.makeWH(grid.width(), grid.height()),
                Rect.makeXYWH(grid.left(), grid.top(), grid.width() / grid.density(), grid.height() / grid.density()),
                SamplingMode.DEFAULT, null, true);
        return true;
    }

    private static void drawFiltered(Canvas c, Font font, String text, float x, float y,
                                     Path icon, float iconX, float iconY, float iconSize, Rect bounds, ImageFilter filter) {
        int count = c.saveLayer(bounds, FILTER_PAINT.setImageFilter(filter));
        try {
            drawInk(c, font, text, x, y, icon, iconX, iconY, iconSize, 0xFF000000);
        } finally {
            c.restoreToCount(count);
        }
    }

    private static void drawInk(Canvas c, Font font, String text, float x, float y,
                                Path icon, float iconX, float iconY, float iconSize, int color) {
        if (icon != null) DrawPrimitives.drawIconCentered(c, icon, iconX, iconY, iconSize, color);
        if (!text.isEmpty()) SkiaFontRenderer.drawText(c, font, text, x, y, color);
    }
}
