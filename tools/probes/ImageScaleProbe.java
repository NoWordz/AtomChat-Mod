package com.atom.chat.probe;

import io.github.humbleui.skija.Bitmap;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Image downscale quality probe (read-only diagnostic).
 *
 * <p>The UI caches decoded images at a generous {@code MAX_DIM} (avatar 256,
 * banner/wallpaper 1024) but draws them at 32-96 device px. Skia's GPU
 * {@code drawImageRect} with LINEAR sampling evaluates a 2x2 tap, so a 5x
 * downscale reads 4 of every 25 source texels: high-frequency detail aliases
 * and the result reads as mush. Pre-scaling on the CPU (area-weighted, once per
 * cache build) leaves the GPU a 1-2x minification where bilinear is exact.</p>
 *
 * <p>Reports, for each strategy at a realistic ratio, the mean absolute error
 * against an exact area-average ground truth and the retained high-frequency
 * energy. This is the evidence for pre-scaling at cache-build time rather than
 * changing the sampling mode at draw time.</p>
 */
public final class ImageScaleProbe {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : ".work/image-scale");
        Files.createDirectories(out);

        for (int src : new int[]{256, 128}) {
            for (int dst : new int[]{48, 40, 88, 24}) {
                if (src <= dst) continue;
                verdict(out, src, dst);
            }
        }
    }

    private static void verdict(Path out, int src, int dst) throws IOException {
        BufferedImage photo = photo(src);
        BufferedImage truth = areaAverage(photo, dst);
        ImageIO.write(truth, "png", out.resolve("truth-" + src + "-" + dst + "x.png").toFile());
        System.out.printf(Locale.ROOT, "%nsource=%d -> %d px (%.2fx)%n", src, dst, src / (double) dst);
        System.out.printf(Locale.ROOT, "%-34s %9s %10s %10s%n", "strategy", "error", "detail%", "peak%");

        record Strategy(String name, java.util.function.BiFunction<Image, Integer, Image> prepare,
                        SamplingMode mode, int preScale) { }

        java.util.List<Strategy> strategies = java.util.List.of(
                new Strategy("draw LINEAR (current)", (img, target) -> img, SamplingMode.LINEAR, 0),
                new Strategy("draw MITCHELL", (img, target) -> img, SamplingMode.MITCHELL, 0),
                new Strategy("pre-scale MITCHELL -> dst, draw LINEAR", ImageScaleProbe::scaled, SamplingMode.LINEAR, dst),
                new Strategy("pre-scale CATMULL_ROM -> dst", ImageScaleProbe::scaledCatmull, SamplingMode.LINEAR, dst),
                new Strategy("pre-scale MITCHELL -> 2*dst", ImageScaleProbe::scaledDouble, SamplingMode.LINEAR, dst * 2),
                new Strategy("mipmap halving -> 2*dst", ImageScaleProbe::mipToDouble, SamplingMode.LINEAR, dst * 2),
                new Strategy("mipmap halving -> dst", ImageScaleProbe::mipTo, SamplingMode.LINEAR, dst),
                new Strategy("SHIPPED: halve to <=2x, draw LINEAR", ImageScaleProbe::mipToDouble,
                        SamplingMode.LINEAR, dst * 2),
                new Strategy("SHIPPED (within 2x of dst)", ImageScaleProbe::mipTo2x, SamplingMode.LINEAR, dst));

        for (Strategy s : strategies) {
            try (Image source = imageOf(photo)) {
                Image prepared = s.prepare().apply(source, dst);
                BufferedImage rendered = draw(prepared, dst, s.mode());
                if (prepared != source) prepared.close();
                ImageIO.write(rendered, "png", out.resolve("s" + src + "-" + dst + "-" + slug(s.name()) + ".png").toFile());
                double[] stats = stats(rendered, truth);
                System.out.printf(Locale.ROOT, "%-34s %9.3f %10.2f %10.2f%n",
                        s.name(), meanError(rendered, truth), stats[0], stats[1]);
            }
        }
    }

    private static Image scaled(Image source, int target) {
        return source.makeScaled(ImageInfo.makeN32Premul(target, target), SamplingMode.MITCHELL);
    }

    private static Image scaledCatmull(Image source, int target) {
        return source.makeScaled(ImageInfo.makeN32Premul(target, target), SamplingMode.CATMULL_ROM);
    }

    /** Repeated <=2x MITCHELL halving to {@code 2*target}: what mipmap generation does. */
    private static Image mipToDouble(Image source, int target) {
        return mip(source, target * 2);
    }

    /** Repeated <=2x MITCHELL halving straight to the target. */
    private static Image mipTo(Image source, int target) {
        return mip(source, target);
    }

    /**
     * Exactly what {@code ImageQuality.scaledFor} does: halve with MITCHELL
     * while the image is more than 4x the wanted width, then one MITCHELL
     * resize to land on exactly 2x, leaving the last factor of two to the GPU's
     * bilinear tap (exact at 2x).
     */
    private static Image mipTo2x(Image source, int target) {
        Image current = source;
        boolean owned = false;
        int wanted = target * 2;
        while (current.getWidth() > wanted * 4) {
            int next = current.getWidth() / 2;
            Image smaller = current.makeScaled(ImageInfo.makeN32Premul(next, next), SamplingMode.MITCHELL);
            if (owned) current.close();
            current = smaller;
            owned = true;
        }
        if (current.getWidth() == wanted) return current;
        Image exact = current.makeScaled(ImageInfo.makeN32Premul(wanted, wanted), SamplingMode.MITCHELL);
        if (owned) current.close();
        return exact;
    }

    private static Image mip(Image source, int target) {
        Image current = source;
        boolean owned = false;
        while (current.getWidth() > target * 2) {
            int next = Math.max(target, current.getWidth() / 2);
            Image smaller = current.makeScaled(ImageInfo.makeN32Premul(next, next), SamplingMode.MITCHELL);
            if (owned) current.close();
            current = smaller;
            owned = true;
        }
        return current;
    }

    private static Image scaledDouble(Image source, int target) {
        return source.makeScaled(ImageInfo.makeN32Premul(target * 2, target * 2), SamplingMode.MITCHELL);
    }

    private static BufferedImage draw(Image image, int dst, SamplingMode mode) {
        try (Surface surface = Surface.makeRasterN32Premul(dst, dst)) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0xFF000000);
            try (Paint paint = new Paint()) {
                canvas.drawImageRect(image,
                        Rect.makeWH(image.getWidth(), image.getHeight()),
                        Rect.makeWH(dst, dst), mode, paint, false);
            }
            return read(surface, dst, dst);
        }
    }

    /** Siemens star + hard checker + gradient: high frequency detail the eye uses to judge sharpness. */
    private static BufferedImage photo(int n) {
        BufferedImage image = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        int c = n / 2;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = x - c, dy = y - c;
                double r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
                int v;
                if (r < n * 0.30) v = Math.cos(a * 24.0) > 0.0 ? 240 : 12;
                else if (r < n * 0.44) v = ((x / 4) + (y / 4)) % 2 == 0 ? 245 : 10;
                else v = (int) Math.round(((x + y) / (double) (2 * n)) * 255.0);
                image.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return image;
    }

    /** Exact area average over fractional boxes: the ground truth for any downscale ratio. */
    private static BufferedImage areaAverage(BufferedImage source, int size) {
        int fx = source.getWidth(), fy = source.getHeight();
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        double sx = fx / (double) size, sy = fy / (double) size;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double total = 0.0, weight = 0.0;
                int y0 = (int) Math.floor(y * sy), y1 = Math.min(fy, (int) Math.ceil((y + 1) * sy));
                int x0 = (int) Math.floor(x * sx), x1 = Math.min(fx, (int) Math.ceil((x + 1) * sx));
                for (int yy = y0; yy < y1; yy++) {
                    double wy = Math.min((yy + 1) * 1.0, (y + 1) * sy) - Math.max(yy * 1.0, y * sy);
                    for (int xx = x0; xx < x1; xx++) {
                        double wx = Math.min((xx + 1) * 1.0, (x + 1) * sx) - Math.max(xx * 1.0, x * sx);
                        if (wx <= 0 || wy <= 0) continue;
                        total += (source.getRGB(xx, yy) & 255) * wx * wy;
                        weight += wx * wy;
                    }
                }
                int v = (int) Math.round(weight <= 0 ? 0 : total / weight);
                out.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return out;
    }

    private static BufferedImage areaAverageOld(BufferedImage source, int size) {
        int fx = source.getWidth() / size, fy = source.getHeight() / size;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                long sum = 0;
                for (int sy = 0; sy < fy; sy++)
                    for (int sx = 0; sx < fx; sx++)
                        sum += source.getRGB(x * fx + sx, y * fy + sy) & 255;
                int v = (int) (sum / (fx * fy));
                out.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return out;
    }

    /** [retained high-frequency energy vs truth %, peak gradient vs truth %] */
    private static double[] stats(BufferedImage scaled, BufferedImage truth) {
        double got = 0.0, want = 0.0, peakGot = 0.0, peakWant = 0.0;
        for (int y = 1; y < truth.getHeight() - 1; y++) {
            for (int x = 1; x < truth.getWidth() - 1; x++) {
                double g = grad(truth, x, y), s = grad(scaled, x, y);
                want += g; got += s;
                peakWant = Math.max(peakWant, g);
                peakGot = Math.max(peakGot, s);
            }
        }
        return new double[]{100.0 * got / Math.max(1e-9, want), 100.0 * peakGot / Math.max(1e-9, peakWant)};
    }

    private static double grad(BufferedImage i, int x, int y) {
        double gx = ((i.getRGB(x + 1, y) & 255) - (i.getRGB(x - 1, y) & 255)) / 2.0;
        double gy = ((i.getRGB(x, y + 1) & 255) - (i.getRGB(x, y - 1) & 255)) / 2.0;
        return Math.hypot(gx, gy);
    }

    private static double meanError(BufferedImage a, BufferedImage b) {
        double total = 0.0;
        for (int y = 0; y < b.getHeight(); y++)
            for (int x = 0; x < b.getWidth(); x++)
                total += Math.abs((a.getRGB(x, y) & 255) - (b.getRGB(x, y) & 255));
        return total / (b.getWidth() * (double) b.getHeight());
    }

    private static Image imageOf(BufferedImage source) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        return Image.makeFromEncoded(bytes.toByteArray());
    }

    private static BufferedImage read(Surface surface, int w, int h) {
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(w, h);
            if (!surface.readPixels(bitmap, 0, 0)) throw new IllegalStateException("readPixels failed");
            byte[] px = bitmap.readPixels();
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int i = (y * w + x) * 4;
                    image.setRGB(x, y, ((px[i + 2] & 255) << 16) | ((px[i + 1] & 255) << 8) | (px[i] & 255));
                }
            return image;
        }
    }

    private static String slug(String name) {
        return name.replaceAll("[^a-z0-9]+", "-");
    }

    private ImageScaleProbe() { }
}
