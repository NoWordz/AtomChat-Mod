package com.atom.chat.probe;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * Cost probe for the image-preparation step: what does pre-decimating cost, and
 * does it make the per-frame draw cheaper?
 *
 * <p>Two numbers matter and they pull in opposite directions:</p>
 * <ul>
 *   <li><b>prepare</b> — the one-off CPU work (repeated MITCHELL halving). Paid
 *       once per (image, drawn size), then cached.</li>
 *   <li><b>draw</b> — per-frame rasterization. A smaller source means fewer
 *       source texels touched per destination pixel, so it should be no worse;
 *       on a GPU the real saving is texture-cache locality, which a CPU raster
 *       surface cannot show.</li>
 * </ul>
 *
 * <p>Also reports the derived bitmap's pixel count, which is the memory cost.</p>
 */
public final class ImagePrepCostProbe {
    static final float DENSITY = 1600.0F / 1080.0F;

    record Case(String name, int sourcePx, int logicalDrawnPx) { }

    public static void main(String[] args) throws Exception {
        System.out.printf(Locale.ROOT,
                "%-26s %8s %8s %10s %12s %10s %12s%n",
                "case", "src", "drawn", "prepared", "prepareMs", "drawSrcUs", "drawPrepUs");

        Case[] cases = {
                // avatar: decoded at 256, drawn at UiTokens.AVATAR_SIZE * density
                new Case("avatar 40px", 256, 40),
                // list avatar / small icon
                new Case("icon 24px", 128, 24),
                // sticker in emoji panel
                new Case("emote 44px", 128, 44),
                // chat image in a modest bubble
                new Case("chat img 240px", 768, 240),
                // chat image in a wide bubble (source is within 2x -> untouched)
                new Case("chat img 380px", 768, 380),
                // banner
                new Case("banner 600px", 1024, 600),
        };

        // Warm the raster path and the native MITCHELL code before timing:
        // the first resize in a JVM pays surface/native startup that would
        // otherwise be reported as the cost of the avatar case.
        for (int i = 0; i < 6; i++) {
            try (Image warm = solidNoise(256)) {
                com.atom.chat.render.ImageQuality.scaledFor(warm, 59);
                com.atom.chat.render.ImageQuality.release();
            }
        }

        for (Case c : cases) {
            int target = Math.round(c.logicalDrawnPx() * DENSITY);
            try (Image source = solidNoise(c.sourcePx())) {
                long prepareNanos = Long.MAX_VALUE;
                Image prepared = source;
                for (int rep = 0; rep < 5; rep++) {
                    com.atom.chat.render.ImageQuality.release();
                    long prepareStart = System.nanoTime();
                    prepared = com.atom.chat.render.ImageQuality.scaledFor(source, target);
                    prepareNanos = Math.min(prepareNanos, System.nanoTime() - prepareStart);
                }
                boolean untouched = prepared == source;
                int preparedPx = untouched ? c.sourcePx() : prepared.getWidth();

                double drawSrcUs = timeDraw(source, target, 300);
                double drawPrepUs = untouched ? drawSrcUs : timeDraw(prepared, target, 300);

                System.out.printf(Locale.ROOT,
                        "%-26s %8d %8d %10s %12.2f %10.1f %12s%n",
                        c.name(), c.sourcePx(), target,
                        untouched ? "none" : preparedPx + "px",
                        prepareNanos / 1e6, drawSrcUs,
                        untouched ? "same" : String.format(Locale.ROOT, "%.1f", drawPrepUs));
                com.atom.chat.render.ImageQuality.release();
            }
        }

        System.out.printf(Locale.ROOT, "%nbudget: %d prepared pixels total (%.1f MB at 4 B/px)%n",
                4L << 20, (4L << 20) * 4 / (1024.0 * 1024.0));
        System.out.println("draw*Us = mean microseconds per drawImageRect into the drawn box, 300 reps");
        System.out.println("A CPU raster surface cannot show the GPU texture-cache saving; treat draw as a lower bound on the gain.");
    }

    private static double timeDraw(Image image, int target, int reps) {
        try (Surface surface = Surface.makeRasterN32Premul(target + 64, target + 64);
             Paint paint = new Paint()) {
            Canvas canvas = surface.getCanvas();
            Rect src = Rect.makeWH(image.getWidth(), image.getHeight());
            Rect dst = Rect.makeWH(target, target);
            for (int i = 0; i < 30; i++) {
                canvas.clear(0);
                canvas.drawImageRect(image, src, dst, SamplingMode.LINEAR, paint, false);
            }
            long[] samples = new long[reps];
            for (int i = 0; i < reps; i++) {
                canvas.clear(0);
                long started = System.nanoTime();
                canvas.drawImageRect(image, src, dst, SamplingMode.LINEAR, paint, false);
                samples[i] = System.nanoTime() - started;
            }
            Arrays.sort(samples);
            return samples[reps / 2] / 1000.0;
        }
    }

    /** Deterministic 1-px noise: the worst case for any resampler, and it will not compress away. */
    private static Image solidNoise(int side) throws Exception {
        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        java.util.Random random = new java.util.Random(side);
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int v = random.nextInt(256);
                image.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return Image.makeFromEncoded(bytes.toByteArray());
    }

    private ImagePrepCostProbe() { }
}
