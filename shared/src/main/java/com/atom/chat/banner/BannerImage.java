package com.atom.chat.banner;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Decoded profile-banner cache. The same two rules as {@code WallpaperImage}
 * apply, because a banner is the same kind of input — a user photo that can
 * be several megapixels:
 *
 * <ul>
 *   <li>The decode never runs on the render thread — a background daemon does
 *       the work and the banner simply draws its gradient until it lands.</li>
 *   <li>The bitmap is downscaled once to at most {@link #MAX_DIM} on its long
 *       edge. At {@code MAX_DIM} even a 4K photo stays under ~2 MB and is
 *       still far sharper than the s(88)-high banner will ever display.</li>
 * </ul>
 *
 * <p>The cover crop itself happens at draw time (the banner's aspect depends
 * on the panel width), not here — this class only owns the decode.</p>
 */
public final class BannerImage {
    /** Longest allowed side of the cached bitmap, in pixels. */
    public static final int MAX_DIM = 1024;

    private static final AtomicInteger GENERATION = new AtomicInteger();

    /**
     * Guards the close/assign pair on {@code decoded}: the decode worker
     * closes the replaced bitmap while the render thread's {@link #release()}
     * may close the very same one — Skija's Managed.close() throws on an
     * already-closed object, so without this lock the narrow race crashes the
     * render frame instead of just wasting a bitmap.
     */
    private static final Object LOCK = new Object();

    private static volatile Image decoded;
    private static volatile Path decodedFrom;
    private static volatile boolean failed;

    private BannerImage() {
    }

    /**
     * Returns the decoded banner, kicking off a background decode the first
     * time a file appears (and again whenever the file changes). Returns null
     * while that is in flight or when the decode failed — callers keep
     * drawing the gradient fallback.
     */
    public static Image current(Path path) {
        if (path == null) {
            release();
            return null;
        }
        if (path.equals(decodedFrom)) {
            synchronized (LOCK) {
                return failed ? null : decoded;
            }
        }
        startLoad(path);
        return null;
    }

    /** Drops the cached bitmap; the next frame re-decodes from disk. */
    public static void release() {
        synchronized (LOCK) {
            decodedFrom = null;
            failed = false;
            if (decoded != null) {
                decoded.close();
                decoded = null;
            }
        }
    }

    private static void startLoad(Path path) {
        if (path.equals(decodedFrom)) {
            return;
        }
        // Claimed synchronously so the next frame cannot re-trigger the load
        // while the decode is still in flight; the generation counter makes
        // the latest request win if the user swaps files mid-decode.
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
            if (generation == GENERATION.get()) {
                // Close the bitmap being replaced, under the same lock as
                // release(): a set/clear during this decode can otherwise
                // close the same already-closed object (Skija throws on
                // double close, on the render thread).
                synchronized (LOCK) {
                    if (decoded != null) {
                        decoded.close();
                    }
                    decoded = result;
                    failed = !ok;
                }
            } else if (result != null) {
                result.close();
            }
        }, "AtomChat-BannerDecode");
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
