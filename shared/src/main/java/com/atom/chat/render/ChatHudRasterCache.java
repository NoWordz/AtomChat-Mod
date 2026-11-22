package com.atom.chat.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;

/** Bounded snapshots of static message ink. A backend target keeps production HUD uploads on the GPU. */
final class ChatHudRasterCache {
    private static final long MAX_ROW_PIXELS = 1_048_576L;
    private static final long PIXEL_BUDGET = 3_145_728L;
    private final IdentityHashMap<Object, Ink> rows = new IdentityHashMap<>();
    private final ArrayDeque<Object> insertionOrder = new ArrayDeque<>();
    private final int maxRows;
    private final float padding;
    private long pixels;

    ChatHudRasterCache(int maxRows) {
        this(maxRows, HudShadow.PAD);
    }

    ChatHudRasterCache(int maxRows, float padding) {
        this.maxRows = maxRows;
        this.padding = padding;
    }

    /** Scale and translation of a canvas whose target still matches the prepared HUD density. */
    record PixelGrid(float density, float translateX, float translateY) {
        static PixelGrid from(Canvas canvas, float density) {
            float[] m = canvas.getLocalToDeviceAsMatrix33()._mat;
            if (m[0] != density || m[4] != density || m[1] != 0F || m[3] != 0F
                    || m[6] != 0F || m[7] != 0F || m[8] != 1F) return null;
            return new PixelGrid(density, m[2], m[5]);
        }
    }

    private record Ink(Image image, float phaseX, float phaseY) { }

    @FunctionalInterface
    interface SurfaceFactory {
        Surface create(int width, int height);
    }

    @FunctionalInterface
    interface Painter {
        void draw(Canvas canvas, float inset);
    }

    @FunctionalInterface
    interface PositionedPainter {
        void draw(Canvas canvas, float x, float y);
    }

    Image get(Object key) {
        Ink ink = rows.get(key);
        return ink == null ? null : ink.image();
    }

    Image bake(Object key, float width, float height, float density, Painter painter) {
        return bake(key, width, height, density, null, painter);
    }

    Image bake(Object key, float width, float height, float density, SurfaceFactory factory, Painter painter) {
        return bake(key, width, height, density, factory, padding, padding, 0F, 0F,
                (canvas, x, y) -> painter.draw(canvas, x));
    }

    /** Rasterize at the destination glyph phase, then copy physical pixels without resampling. */
    boolean drawAligned(Canvas canvas, Object key, float x, float y, float width, float height,
                        PixelGrid grid, SurfaceFactory factory, PositionedPainter painter) {
        float density = grid.density();
        float deviceX = x * density + grid.translateX(), deviceY = y * density + grid.translateY();
        float phaseX = deviceX - (float) Math.floor(deviceX);
        float phaseY = deviceY - (float) Math.floor(deviceY);
        int pad = (int) Math.ceil(padding * density);
        float insetX = (pad + phaseX) / density, insetY = (pad + phaseY) / density;
        Ink ink = rows.get(key);
        Image image;
        if (ink == null || Math.abs(ink.phaseX() - phaseX) > .001F || Math.abs(ink.phaseY() - phaseY) > .001F) {
            image = bake(key, width, height, density, factory, insetX, insetY, phaseX, phaseY, painter);
        } else {
            image = ink.image();
        }
        if (image == null) return false;
        canvas.drawImageRect(image, Rect.makeWH(image.getWidth(), image.getHeight()),
                Rect.makeXYWH(x - insetX, y - insetY, image.getWidth() / density, image.getHeight() / density),
                SamplingMode.DEFAULT, null, false);
        return true;
    }

    private Image bake(Object key, float width, float height, float density, SurfaceFactory factory,
                       float insetX, float insetY, float phaseX, float phaseY, PositionedPainter painter) {
        int pixelsWide = (int) Math.ceil((width + insetX + padding) * density);
        int pixelsHigh = (int) Math.ceil((height + insetY + padding) * density);
        long area = (long) pixelsWide * pixelsHigh;
        if (pixelsWide <= 0 || pixelsHigh <= 0 || area > MAX_ROW_PIXELS) return null;
        if (rows.containsKey(key)) {
            insertionOrder.removeIf(stored -> stored == key);
            discard(key);
        }
        while (rows.size() >= maxRows || pixels + area > PIXEL_BUDGET) {
            discard(insertionOrder.removeFirst());
        }
        try (Surface surface = makeSurface(factory, pixelsWide, pixelsHigh)) {
            if (surface == null) return null;
            Canvas canvas = surface.getCanvas();
            canvas.clear(0);
            canvas.scale(density, density);
            painter.draw(canvas, insetX, insetY);
            Image raster = surface.makeImageSnapshot();
            rows.put(key, new Ink(raster, phaseX, phaseY));
            insertionOrder.addLast(key);
            pixels += area;
            return raster;
        }
    }

    private void discard(Object key) {
        Image image = rows.remove(key).image();
        pixels -= (long) image.getWidth() * image.getHeight();
        image.close();
    }

    private static Surface makeSurface(SurfaceFactory factory, int width, int height) {
        Surface compatible = factory == null ? null : factory.create(width, height);
        return compatible != null ? compatible : Surface.makeRasterN32Premul(width, height);
    }

    void draw(Canvas canvas, Image image, float x, float y, float density) {
        canvas.drawImageRect(image, Rect.makeWH(image.getWidth(), image.getHeight()),
                Rect.makeXYWH(x - padding, y - padding, image.getWidth() / density, image.getHeight() / density),
                SamplingMode.LINEAR, null, false);
    }

    void clear() {
        for (Ink ink : rows.values()) ink.image().close();
        rows.clear();
        insertionOrder.clear();
        pixels = 0L;
    }
}
