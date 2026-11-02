package com.atom.chat.probe;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontEdging;
import io.github.humbleui.skija.FontHinting;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.FontVariation;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.types.Rect;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sharpness evidence probe for the two raster stages that decide whether the
 * Skija UI reads crisp (read-only diagnostic; nothing here touches product
 * code).
 *
 * <p><b>Section 1 — text.</b> Renders one body-size string through each
 * glyph-rasterization configuration Skia exposes, at the session's real
 * density, and reports the 10%→90% edge transition width, the half-lit pixel
 * share and the error against a 4x-supersampled ground truth. A density sweep
 * follows, because the design px → device px ratio is the one input the UI
 * chooses for itself.</p>
 *
 * <p><b>Section 2 — images.</b> Downscales a synthetic photo-like source
 * (Siemens star + hard-edge checker + gradients) from 512 to 64 with each
 * sampling mode and compares against an exact area-average ground truth. This
 * is the avatar/emoji/banner path: a 256-px avatar drawn at 48 px is a 5x
 * downscale, where bilinear reads only 4 of every 16 source texels.</p>
 */
public final class TextSharpnessProbe {
    static final float DENSITY = 1600.0F / 1080.0F;
    static final float FONT_SIZE = 25.0F; // UiTokens.FONT_BODY
    static final String SAMPLE = "AtomChat 消息列表 edit 12:34";
    static final int PAD = 6;
    static int SS = 4;

    record Variant(String name, boolean subpixel, FontHinting hinting, boolean baselineSnapped) { }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : ".work/text-sharpness");
        Files.createDirectories(out);
        Typeface face = bundledFace();
        System.out.println("face=" + face.getFamilyName() + "  density=" + DENSITY + "  size=" + FONT_SIZE
                + "  device px=" + (FONT_SIZE * DENSITY));

        textVerdict(out, face);
        densitySweep(out, face);
        imageVerdict(out);
    }

    // ------------------------------------------------------------- section 1

    private static void textVerdict(Path out, Typeface face) throws IOException {
        List<Variant> variants = List.of(
                new Variant("subpixel+none (current)", true, FontHinting.NONE, false),
                new Variant("nosubpixel+none", false, FontHinting.NONE, false),
                new Variant("nosubpixel+slight", false, FontHinting.SLIGHT, false),
                new Variant("nosubpixel+full", false, FontHinting.FULL, true));

        BufferedImage truth = groundTruth(face, DENSITY);
        ImageIO.write(truth, "png", out.resolve("text-truth-4x.png").toFile());
        int[] truthLuma = lumaOf(truth);
        double[] t0 = transitions(truthLuma, truth.getWidth(), truth.getHeight(), true);
        System.out.printf(Locale.ROOT, "control: 4x-supersampled ground truth  transition=%.3f  mid%%=%.2f%n",
                t0[0], midShare(truthLuma, truth.getWidth(), truth.getHeight()) * 100.0);

        System.out.printf(Locale.ROOT, "%n%-26s %10s %8s %8s %8s %7s%n",
                "text variant", "transition", "mid%", "p90mid%", "error", "edges");
        for (Variant v : variants) {
            float[] buf = renderText(face, v, DENSITY, 1);
            int w = (int) buf[0], h = (int) buf[1];
            int[] luma = luma(buf, w, h);
            ImageIO.write(toImage(buf, w, h), "png", out.resolve("text-" + slug(v.name()) + ".png").toFile());
            double[] t = transitions(luma, w, h, true);
            ImageIO.write(toImage(buf, w, h), "png", out.resolve("text-" + slug(v.name()) + ".png").toFile());
            System.out.printf(Locale.ROOT, "%-26s %10.3f %8.2f %8.2f %8.3f %7.0f%n",
                    v.name(), t[0], midShare(luma, w, h) * 100.0, midP90(luma, w, h) * 100.0,
                    meanError(luma, w, h, truth), t[1]);
        }
    }

    private static void densitySweep(Path out, Typeface face) throws IOException {
        System.out.printf(Locale.ROOT, "%n%-10s %10s %8s %8s %8s%n", "density", "transition", "mid%", "error", "devicePx");
        Variant v = new Variant("subpixel+none", true, FontHinting.NONE, false);
        for (float d : new float[]{1.0F, 1.25F, 4.0F / 3.0F, 1.4F, DENSITY, 1.5F, 2.0F}) {
            BufferedImage truth = groundTruth(face, d);
            float[] buf = renderText(face, v, d, 1);
            int w = (int) buf[0], h = (int) buf[1];
            int[] luma = luma(buf, w, h);
            double[] t = transitions(luma, w, h, true);
            System.out.printf(Locale.ROOT, "%-10.4f %10.3f %8.2f %8.3f %8.1f%n",
                    d, t[0], midShare(luma, w, h) * 100.0, meanError(luma, w, h, truth), FONT_SIZE * d);
        }
    }

    // ------------------------------------------------------------- section 2

    private static void imageVerdict(Path out) throws IOException {
        final int src = 512, dst = 64;
        BufferedImage photo = photo(src);
        ImageIO.write(photo, "png", out.resolve("image-source.png").toFile());
        BufferedImage truth = areaAverage(photo, dst);

        System.out.printf(Locale.ROOT, "%n%-14s %9s %9s %9s%n", "sampling", "error", "detail%", "alias%");
        for (String name : new String[]{"DEFAULT", "LINEAR", "MITCHELL", "CATMULL_ROM"}) {
            SamplingMode mode = switch (name) {
                case "LINEAR" -> SamplingMode.LINEAR;
                case "MITCHELL" -> SamplingMode.MITCHELL;
                case "CATMULL_ROM" -> SamplingMode.CATMULL_ROM;
                default -> SamplingMode.DEFAULT;
            };
            BufferedImage scaled = scale(photo, dst, mode);
            ImageIO.write(scaled, "png", out.resolve("image-" + name.toLowerCase(Locale.ROOT) + ".png").toFile());
            double[] detail = detailStats(scaled, truth);
            System.out.printf(Locale.ROOT, "%-14s %9.3f %9.2f %9.2f%n", name,
                    meanErrorImage(scaled, truth), detail[0], detail[1]);
        }
    }

    /** A synthetic "photo": Siemens star for high frequency, a checker for hard edges, smooth gradients. */
    private static BufferedImage photo(int n) {
        BufferedImage image = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        int c = n / 2;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = x - c, dy = y - c;
                double r = Math.hypot(dx, dy);
                double a = Math.atan2(dy, dx);
                int v;
                if (r < n * 0.30) {
                    v = (Math.cos(a * 18.0) > 0.0) ? 235 : 20;
                } else if (r < n * 0.42) {
                    v = ((x / 8) + (y / 8)) % 2 == 0 ? 240 : 15;
                } else {
                    v = (int) Math.round(((x + y) / (double) (2 * n)) * 255.0);
                }
                image.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return image;
    }

    private static BufferedImage scale(BufferedImage source, int size, SamplingMode mode) throws IOException {
        try (Image image = imageOf(source);
             Surface surface = Surface.makeRasterN32Premul(size, size)) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0xFF000000);
            try (Paint paint = new Paint()) {
                canvas.drawImageRect(image,
                        Rect.makeWH(source.getWidth(), source.getHeight()),
                        Rect.makeWH(size, size), mode, paint, false);
            }
            return read(surface, size, size);
        }
    }

    /** Exact area average: the ground truth for a box downscale. */
    private static BufferedImage areaAverage(BufferedImage source, int size) {
        int factor = source.getWidth() / size;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                long sum = 0;
                for (int sy = 0; sy < factor; sy++) {
                    for (int sx = 0; sx < factor; sx++) {
                        sum += source.getRGB(x * factor + sx, y * factor + sy) & 255;
                    }
                }
                int v = (int) (sum / (factor * factor));
                out.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return out;
    }

    /** [mean |gradient| vs truth %, misfit energy in high-frequency bands as % of truth] */
    private static double[] detailStats(BufferedImage scaled, BufferedImage truth) {
        double centre = 0.0, energy = 0.0;
        for (int y = 1; y < truth.getHeight() - 1; y++) {
            for (int x = 1; x < truth.getWidth() - 1; x++) {
                centre += Math.abs(grad(scaled, x, y) - grad(truth, x, y));
                energy += Math.abs(grad(truth, x, y));
            }
        }
        double n = (truth.getHeight() - 2.0) * (truth.getWidth() - 2.0);
        return new double[]{100.0 * (1.0 - centre / Math.max(1e-9, energy)), 100.0 * centre / Math.max(1e-9, energy)};
    }

    private static double grad(BufferedImage i, int x, int y) {
        double gx = ((i.getRGB(x + 1, y) & 255) - (i.getRGB(x - 1, y) & 255)) / 2.0;
        double gy = ((i.getRGB(x, y + 1) & 255) - (i.getRGB(x, y - 1) & 255)) / 2.0;
        return Math.hypot(gx, gy);
    }

    private static double meanErrorImage(BufferedImage a, BufferedImage b) {
        double total = 0.0;
        for (int y = 0; y < b.getHeight(); y++) {
            for (int x = 0; x < b.getWidth(); x++) {
                total += Math.abs((a.getRGB(x, y) & 255) - (b.getRGB(x, y) & 255));
            }
        }
        return total / (b.getWidth() * (double) b.getHeight());
    }

    private static Image imageOf(BufferedImage source) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        return Image.makeFromEncoded(bytes.toByteArray());
    }

    private static BufferedImage read(Surface surface, int w, int h) {
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(w, h);
            if (!surface.readPixels(bitmap, 0, 0)) {
                throw new IllegalStateException("readPixels failed");
            }
            byte[] px = bitmap.readPixels();
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = (y * w + x) * 4;
                    int b = px[i] & 255, g = px[i + 1] & 255, r = px[i + 2] & 255;
                    image.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
            return image;
        }
    }

    // ------------------------------------------------------------ text core

    private static Typeface bundledFace() throws IOException {
        Path bundled = Path.of("platforms/1.21.1-neoforge/build/resources/main/assets/atomchat/font/bundled.otf");
        if (Files.isRegularFile(bundled)) {
            Data data = Data.makeFromBytes(Files.readAllBytes(bundled));
            Typeface face = FontMgr.getDefault().makeFromData(data);
            if (face != null) {
                // Same instance FontManager renders with: variable fonts are
                // cloned at Medium, otherwise the default instance is Thin.
                if (face.getVariations().length > 0) {
                    return face.makeClone(new FontVariation("wght", 500));
                }
                return face;
            }
        }
        return FontMgr.getDefault().matchFamilyStyle("Segoe UI", FontStyle.NORMAL);
    }

    private static float[] renderText(Typeface face, Variant v, float density, int rowScale) {
        Font font = new Font(face, FONT_SIZE);
        font.setSubpixel(v.subpixel());
        font.setHinting(v.hinting());
        font.setBaselineSnapped(v.baselineSnapped());
        font.setEdging(FontEdging.ANTI_ALIAS);
        float width = font.measureTextWidth(SAMPLE) * density;
        int w = (int) Math.ceil(width) + PAD * 2;
        int h = (int) Math.ceil(FONT_SIZE * 1.6F * density) + PAD * 2;
        try (Surface surface = Surface.makeRasterN32Premul(w * rowScale, h * rowScale);
             Paint paint = new Paint().setColor(0xFF000000)) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0xFFFFFFFF);
            canvas.scale(density * rowScale, density * rowScale);
            canvas.drawString(SAMPLE, PAD / (float) rowScale, PAD / (float) rowScale + FONT_SIZE, font, paint);
            BufferedImage image = read(surface, w * rowScale, h * rowScale);
            float[] result = new float[2 + w * rowScale * h * rowScale * 3];
            result[0] = w * rowScale;
            result[1] = h * rowScale;
            for (int y = 0; y < h * rowScale; y++) {
                for (int x = 0; x < w * rowScale; x++) {
                    int p = image.getRGB(x, y);
                    int i = 2 + (y * w * rowScale + x) * 3;
                    result[i] = (p >> 16) & 255;
                    result[i + 1] = (p >> 8) & 255;
                    result[i + 2] = p & 255;
                }
            }
            return result;
        }
    }

    private static BufferedImage groundTruth(Typeface face, float density) throws IOException {
        float[] hi = renderText(face, new Variant("truth", true, FontHinting.NONE, false), density * SS, 1);
        int w = (int) hi[0], h = (int) hi[1];
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = 2 + (y * w + x) * 3;
                image.setRGB(x, y, ((int) hi[i] << 16) | ((int) hi[i + 1] << 8) | (int) hi[i + 2]);
            }
        }
        BufferedImage down = new BufferedImage(w / SS, h / SS, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < down.getHeight(); y++) {
            for (int x = 0; x < down.getWidth(); x++) {
                int r = 0, g = 0, b = 0;
                for (int sy = 0; sy < SS; sy++) {
                    for (int sx = 0; sx < SS; sx++) {
                        int p = image.getRGB(x * SS + sx, y * SS + sy);
                        r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255;
                    }
                }
                int n = SS * SS;
                down.setRGB(x, y, ((r / n) << 16) | ((g / n) << 8) | (b / n));
            }
        }
        return down;
    }

    private static int[] luma(float[] buf, int w, int h) {
        int[] out = new int[w * h];
        for (int i = 0; i < w * h; i++) {
            int r = (int) buf[2 + i * 3], g = (int) buf[3 + i * 3], b = (int) buf[4 + i * 3];
            out[i] = (r * 2126 + g * 7152 + b * 722) / 10000;
        }
        return out;
    }

    private static BufferedImage toImage(float[] buf, int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = 2 + (y * w + x) * 3;
                image.setRGB(x, y, ((int) buf[i] << 16) | ((int) buf[i + 1] << 8) | (int) buf[i + 2]);
            }
        }
        return image;
    }

    private static int[] lumaOf(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = image.getRGB(x, y);
                out[y * w + x] = (((p >> 16) & 255) * 2126 + ((p >> 8) & 255) * 7152 + (p & 255) * 722) / 10000;
            }
        }
        return out;
    }

    private static double[] transitions(int[] luma, int w, int h, boolean rows) {
        double total = 0.0;
        int edges = 0;
        int outer = rows ? h : w;
        int inner = rows ? w : h;
        int[] line = new int[inner];
        for (int o = 0; o < outer; o++) {
            for (int i = 0; i < inner; i++) line[i] = rows ? luma[o * w + i] : luma[i * w + o];
            int lo = 255, hi = 0;
            for (int v : line) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            if (hi - lo < 120) continue;
            double low = lo + (hi - lo) * 0.1, high = lo + (hi - lo) * 0.9;
            int i = 0;
            while (i < inner) {
                if (line[i] >= low) { i++; continue; }
                int inkStart = i;
                while (i < inner && line[i] < low) i++;
                int inkEnd = i - 1;
                double in = Math.abs(crossingFrom(line, inkStart, -1, high)
                        - crossingFrom(line, inkStart, -1, low));
                double outW = Math.abs(crossingFrom(line, inkEnd, 1, high)
                        - crossingFrom(line, inkEnd, 1, low));
                if (in > 0 || outW > 0) { total += (in + outW) / 2.0; edges++; }
            }
        }
        return new double[]{edges == 0 ? 0.0 : total / edges, edges};
    }

    private static double crossingFrom(int[] line, int from, int dir, double level) {
        for (int j = from; j + dir >= 0 && j + dir < line.length; j += dir) {
            int a = line[j], b = line[j + dir];
            if (a != b && (a < level) != (b < level)) {
                return j + dir * (level - a) / (double) (b - a);
            }
        }
        return from;
    }

    /** Share of ink-adjacent pixels that are strictly partial coverage. */
    private static double midShare(int[] luma, int w, int h) {
        int mid = 0, ink = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int v = luma[y * w + x];
                if (v > 247) continue;
                boolean nearInk = luma[y * w + x - 1] < 248 || luma[y * w + x + 1] < 248
                        || luma[(y - 1) * w + x] < 248 || luma[(y + 1) * w + x] < 248;
                if (!nearInk) continue;
                ink++;
                if (v > 8) mid++;
            }
        }
        return ink == 0 ? 0.0 : mid / (double) ink;
    }

    /** The 90th percentile of edge-transition widths: the worst-looking edges, not the average. */
    private static double midP90(int[] luma, int w, int h) {
        List<Double> widths = new ArrayList<>();
        for (int y = 1; y < h - 1; y++) {
            int prev = luma[y * w];
            boolean inInk = prev < 60;
            for (int x = 1; x < w; x++) {
                boolean now = luma[y * w + x] < 60;
                if (!now && inInk) {
                    int j = x;
                    while (j < w && luma[y * w + j] >= 60) j++;
                    if (j < w && luma[y * w + j] < 60) widths.add((double) (j - x));
                }
                inInk = now;
            }
        }
        if (widths.isEmpty()) return 0.0;
        widths.sort(Double::compare);
        return widths.get(Math.min(widths.size() - 1, (int) Math.ceil(widths.size() * 0.9) - 1));
    }

    private static double meanError(int[] luma, int w, int h, BufferedImage truth) {
        double total = 0.0;
        int count = 0;
        for (int y = 0; y < Math.min(h, truth.getHeight()); y++) {
            for (int x = 0; x < Math.min(w, truth.getWidth()); x++) {
                int p = truth.getRGB(x, y);
                int t = (((p >> 16) & 255) * 2126 + ((p >> 8) & 255) * 7152 + (p & 255) * 722) / 10000;
                total += Math.abs(luma[y * w + x] - t);
                count++;
            }
        }
        return count == 0 ? 0.0 : total / count;
    }

    private static String slug(String name) {
        return name.replaceAll("[^a-z0-9+]+", "-");
    }

    private TextSharpnessProbe() { }
}
