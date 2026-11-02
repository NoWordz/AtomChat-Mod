package com.atom.chat.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Minification quality for the cached bitmaps (avatars, banners, chat images,
 * wallpapers).
 *
 * <p><b>Why this exists.</b> The image caches store bitmaps at a generous
 * resolution — an avatar is decoded up to 256 px, a chat photo up to 768 — while
 * the UI draws them at roughly 40-300 device px. A GPU {@code drawImageRect}
 * with LINEAR sampling takes a 2x2 tap, so a 3-8x minification reads 4 of every
 * 9-64 source texels: fine detail aliases and the picture reads as mush.</p>
 *
 * <p><b>The fix, and why it is two steps.</b> The probe
 * (tools/probes/ImageScaleProbe.java) measured every candidate against an exact
 * area average, on noise, at the ratios this UI actually uses:</p>
 * <pre>
 *   128 px source -> 40 device px (3.2x)     mean error
 *     draw the source, LINEAR                     23.96
 *     draw the source, MITCHELL                   25.58
 *     one MITCHELL resize straight to 40          25.58
 *     halve to 64, let the GPU finish             13.32
 *     halve to 80, let the GPU finish              3.06   &lt;- shipped
 * </pre>
 * <p>The last line is the point: the GPU's bilinear tap is <em>exact</em> at
 * exactly 2x, and only approximately right anywhere else. So the prepared bitmap
 * is resized to precisely <em>twice</em> the drawn size — halving with MITCHELL
 * (a tent filter: sharp, no ringing) while the image is still more than 4x
 * away, then one MITCHELL resize to land on 2x, and the GPU finishes the last
 * factor of two.</p>
 *
 * <p><b>Ownership.</b> A prepared bitmap is a raster snapshot this cache owns: it
 * stays valid after the caller closes its source, and it is closed when the entry
 * is evicted or {@link #release()} runs. Sources are held weakly, so this cache
 * can never extend an image loader's LRU.</p>
 *
 * <p>Render thread only, like the rest of the draw path.</p>
 */
public final class ImageQuality {
    /** Prepared copies held per source before the least recent one is dropped. */
    private static final int MAX_LEVELS_PER_SOURCE = 4;
    /** Total pixels the prepared copies may hold; ~16 MB at 4 bytes per pixel. */
    private static final long MAX_DERIVED_PIXELS = 4L << 20;

    /** One source and the copies prepared from it, keyed by target width. */
    private static final class Entry {
        final WeakReference<Image> source;
        /** Target width -> prepared image; access order drives eviction. */
        final java.util.LinkedHashMap<Integer, Image> prepared =
                new java.util.LinkedHashMap<>(4, 0.75F, true) {
                    @Override
                    protected boolean removeEldestEntry(java.util.Map.Entry<Integer, Image> eldest) {
                        if (size() <= MAX_LEVELS_PER_SOURCE) return false;
                        Image dropped = eldest.getValue();
                        derivedPixels -= (long) dropped.getWidth() * dropped.getHeight();
                        dropped.close();
                        return true;
                    }
                };

        Entry(Image source) {
            // A hit is decided by comparing the weak referent against the exact
            // instance passed in, so a recycled native pointer can never serve a
            // stranger's pixels. No strong reference is held, so this cache never
            // keeps a loader's image alive.
            this.source = new WeakReference<>(source);
        }
    }

    private static final java.util.Map<Long, List<Entry>> ENTRIES = new java.util.HashMap<>();
    private static long derivedPixels;

    private ImageQuality() {
    }

    /**
     * The best bitmap to draw {@code image} into a box that is
     * {@code targetDevicePx} wide on screen: the source itself when it is
     * already within 2x, otherwise a copy prepared to exactly 2x.
     */
    public static Image scaledFor(Image image, float targetDevicePx) {
        if (image == null) {
            return null;
        }
        int target = Math.max(1, Math.round(targetDevicePx));
        if (image.getWidth() <= target * 2 || image.getHeight() <= 0
                || image.getHeight() <= target * 2) {
            return image;
        }
        Entry entry = entryFor(image);
        Image cached = entry.prepared.get(target);
        if (cached != null) {
            return cached;
        }
        Image prepared = prepare(image, target * 2);
        if (prepared == null) {
            return image;
        }
        entry.prepared.put(target, prepared);
        derivedPixels += (long) prepared.getWidth() * prepared.getHeight();
        trimIfNeeded();
        return prepared;
    }

    /** Convenience for the draw sites: prepare for a logical box size using the frame density. */
    public static Image scaledForLogical(Image image, float logicalSize) {
        return scaledFor(image, logicalSize * UiPixelSnap.density());
    }

    /**
     * Halves with MITCHELL while the image is more than 4x the wanted width,
     * then makes one MITCHELL resize to exactly {@code wanted} — so the final,
     * GPU-side step is always a factor of two.
     */
    private static Image prepare(Image source, int wanted) {
        Image current = source;
        boolean owned = false;
        try {
            while (current.getWidth() > wanted * 4) {
                Image smaller = resize(current, current.getWidth() / 2);
                if (smaller == null) {
                    return owned ? current : null;
                }
                if (owned) {
                    current.close();
                }
                current = smaller;
                owned = true;
            }
            if (current.getWidth() == wanted) {
                // Already exactly 2x of the target.
                return owned ? current : resize(current, wanted);
            }
            Image exact = resize(current, wanted);
            if (exact == null) {
                return owned ? current : null;
            }
            if (owned) {
                current.close();
            }
            return exact;
        } catch (RuntimeException e) {
            return owned ? current : null;
        }
    }

    /** MITCHELL resize to {@code width}, keeping the source aspect ratio. */
    private static Image resize(Image source, int width) {
        int height = Math.max(1, Math.round(width * (source.getHeight() / (float) source.getWidth())));
        try (Surface surface = Surface.makeRaster(ImageInfo.makeN32Premul(width, height));
             Paint paint = new Paint()) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0);
            canvas.drawImageRect(source,
                    Rect.makeWH(source.getWidth(), source.getHeight()),
                    Rect.makeWH(width, height), SamplingMode.MITCHELL, paint, false);
            return surface.makeImageSnapshot();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Entry entryFor(Image image) {
        long fingerprint = System.identityHashCode(image);
        List<Entry> bucket = ENTRIES.computeIfAbsent(fingerprint, ignored -> new ArrayList<>(1));
        for (Iterator<Entry> it = bucket.iterator(); it.hasNext(); ) {
            Entry entry = it.next();
            if (entry.source.get() == image) {
                return entry;
            }
            // A dead or replaced source: its copies were derived from pixels no
            // one can draw any more.
            it.remove();
            closeEntry(entry);
        }
        Entry entry = new Entry(image);
        bucket.add(entry);
        return entry;
    }

    private static void closeEntry(Entry entry) {
        for (Image image : entry.prepared.values()) {
            derivedPixels -= (long) image.getWidth() * image.getHeight();
            image.close();
        }
        entry.prepared.clear();
    }

    private static void trimIfNeeded() {
        if (derivedPixels <= MAX_DERIVED_PIXELS) {
            return;
        }
        for (Iterator<List<Entry>> buckets = ENTRIES.values().iterator(); buckets.hasNext(); ) {
            List<Entry> bucket = buckets.next();
            for (Iterator<Entry> it = bucket.iterator(); it.hasNext(); ) {
                Entry entry = it.next();
                it.remove();
                closeEntry(entry);
                if (derivedPixels <= MAX_DERIVED_PIXELS) {
                    return;
                }
            }
            buckets.remove();
        }
    }

    /** Drops every prepared copy. The source images belong to their loaders. */
    public static void release() {
        for (List<Entry> bucket : ENTRIES.values()) {
            for (Entry entry : bucket) {
                closeEntry(entry);
            }
        }
        ENTRIES.clear();
        derivedPixels = 0L;
    }

    /** Number of live cache entries; for the wiring tests. */
    static int entryCount() {
        int count = 0;
        for (List<Entry> bucket : ENTRIES.values()) {
            count += bucket.size();
        }
        return count;
    }

    /** Prepared pixels currently held; for the wiring tests. */
    static long derivedPixels() {
        return derivedPixels;
    }
}
