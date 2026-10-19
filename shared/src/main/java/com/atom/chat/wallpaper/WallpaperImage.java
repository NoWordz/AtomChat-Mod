package com.atom.chat.wallpaper;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decoded wallpaper cache. Decoding a user photo can be several megapixels, so
 * two rules apply:
 *
 * <ul>
 *   <li>The decode never runs on the render thread — a background daemon does
 *       the work and the panel simply draws the fallback until it lands.</li>
 *   <li>The bitmap is downscaled once to at most {@link #MAX_DIM} on its long
 *       edge. A 4K photo decoded raw would sit in memory at ~33 MB forever;
 *       at {@code MAX_DIM} it is under 2 MB and still far sharper than the
 *       panel will ever display.</li>
 * </ul>
 */
public final class WallpaperImage {
    /** Longest allowed side of the cached bitmap, in pixels. */
    public static final int MAX_DIM = 1024;

    private static final java.util.concurrent.atomic.AtomicInteger GENERATION =
            new java.util.concurrent.atomic.AtomicInteger();

    private static volatile Image decoded;
    private static volatile Path decodedFrom;
    private static volatile boolean failed;

    private WallpaperImage() {
    }

    /**
     * Returns the decoded wallpaper, kicking off a background decode the first
     * time a file appears (and again whenever the file changes). Returns null
     * while that is in flight — callers keep drawing the plain background.
     */
    public static Image current(Path path) {
        if (path == null) {
            release();
            return null;
        }
        if (path.equals(decodedFrom)) {
            return decoded;
        }
        startLoad(path);
        return null;
    }

    /** Drops the cached bitmap; the next frame re-decodes from disk. */
    public static synchronized void release() {
        GENERATION.incrementAndGet();
        decodedFrom = null;
        failed = false;
        if (decoded != null) {
            decoded.close();
            decoded = null;
        }
    }

    private static synchronized void startLoad(Path path) {
        if (path.equals(decodedFrom)) {
            return;
        }
        release();
        // Claimed synchronously so the next frame cannot re-trigger the load
        // while the decode is still in flight. Decodes are rare (user action),
        // so one thread per change is fine; the generation counter makes the
        // latest request win if the user swaps files mid-decode.
        decodedFrom = path;
        failed = false;
        int generation = GENERATION.incrementAndGet();
        Thread worker = new Thread(() -> {
            Image result = null;
            boolean ok = false;
            try {
                result = downscale(Image.makeFromEncoded(Files.readAllBytes(path)));
                ok = result != null;
            } catch (Throwable ignored) {
                ok = false;
            }
            synchronized (WallpaperImage.class) {
                if (generation == GENERATION.get()) {
                    decoded = result;
                    failed = !ok;
                } else if (result != null) {
                    result.close();
                }
            }
        }, "AtomChat-WallpaperDecode");
        worker.setDaemon(true);
        worker.start();
    }

    /** Fits the image inside {@link #MAX_DIM} without ever upscaling it. */
    private static Image downscale(Image source) {
        if (source == null) {
            return null;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        float scale = Math.min(1.0F, Math.min((float) MAX_DIM / w, (float) MAX_DIM / h));
        if (scale >= 0.999F) {
            return source;
        }
        int tw = Math.max(1, Math.round(w * scale));
        int th = Math.max(1, Math.round(h * scale));
        try (Surface surface = Surface.makeRasterN32Premul(tw, th)) {
            Canvas canvas = surface.getCanvas();
            try (Paint paint = new Paint().setAntiAlias(true)) {
                canvas.drawImageRect(source,
                        Rect.makeXYWH(0, 0, w, h),
                        Rect.makeXYWH(0, 0, tw, th),
                        SamplingMode.LINEAR, paint, false);
            }
            source.close();
            return surface.makeImageSnapshot();
        }
    }
}
