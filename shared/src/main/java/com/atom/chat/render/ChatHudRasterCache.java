package com.atom.chat.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;

import java.util.IdentityHashMap;

/** Small CPU-backed snapshots of static message ink, independent of the current GL context. */
final class ChatHudRasterCache {
    private static final int MAX_ROWS = 32;
    private static final long MAX_ROW_PIXELS = 192_000L;
    private static final float PADDING = 3F;
    private final IdentityHashMap<Object, Image> rows = new IdentityHashMap<>();

    @FunctionalInterface
    interface Painter {
        void draw(Canvas canvas, float inset);
    }

    Image get(Object key) {
        return rows.get(key);
    }

    Image bake(Object key, float width, float height, float density, Painter painter) {
        int pixelsWide = (int) Math.ceil((width + 2F * PADDING) * density);
        int pixelsHigh = (int) Math.ceil((height + 2F * PADDING) * density);
        if (pixelsWide <= 0 || pixelsHigh <= 0 || (long) pixelsWide * pixelsHigh > MAX_ROW_PIXELS) return null;
        if (rows.size() >= MAX_ROWS) clear();
        try (Surface surface = Surface.makeRasterN32Premul(pixelsWide, pixelsHigh)) {
            if (surface == null) return null;
            Canvas canvas = surface.getCanvas();
            canvas.clear(0);
            canvas.scale(density, density);
            painter.draw(canvas, PADDING);
            Image raster = surface.makeImageSnapshot();
            rows.put(key, raster);
            return raster;
        }
    }

    void draw(Canvas canvas, Image image, float x, float y, float density) {
        canvas.drawImageRect(image, Rect.makeWH(image.getWidth(), image.getHeight()),
                Rect.makeXYWH(x - PADDING, y - PADDING, image.getWidth() / density, image.getHeight() / density),
                SamplingMode.LINEAR, null, false);
    }

    void clear() {
        for (Image image : rows.values()) image.close();
        rows.clear();
    }
}
