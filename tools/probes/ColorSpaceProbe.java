package com.atom.chat.probe;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontEdging;
import io.github.humbleui.skija.FontHinting;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontVariation;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PixelGeometry;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceProps;
import io.github.humbleui.skija.Typeface;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Colour-space / blending evidence probe (read-only diagnostic).
 *
 * <p>The mod wraps the framebuffer with {@code ColorSpace.getSRGB()}. Skia then
 * blends antialiased coverage through the colour space, which changes how a
 * half-lit edge pixel composites: sRGB-typed blending converts coverage to
 * linear light, lerps, and converts back — a different curve from the plain
 * encoded-space lerp every native toolkit (DirectWrite, GDI, Qt) uses. On dark
 * panels with light text the linear path renders strokes visibly thinner and
 * lower-contrast, which reads as softness.</p>
 *
 * <p>This probe renders the same string on surfaces that differ only in the
 * destination colour space and pixel geometry, then reports the 10%→90% edge
 * transition width, the half-lit share, ink contrast (darkest pixel) and the
 * total ink coverage. If the encoded-space surfaces are measurably crisper, the
 * fix is where the surface is created, not in any layout or font setting.</p>
 */
public final class ColorSpaceProbe {
    static final float DENSITY = 1600.0F / 1080.0F;
    static final float FONT_SIZE = 25.0F;
    static final String SAMPLE = "AtomChat 消息列表 edit 12:34";
    static final int PAD = 8;

    record Case(String name, ColorSpace space, PixelGeometry geometry, boolean deviceIndependent) { }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : ".work/colorspace");
        Files.createDirectories(out);
        Typeface face = bundledFace();

        List<Case> cases = List.of(
                new Case("sRGB surface (current)", ColorSpace.getSRGB(), PixelGeometry.UNKNOWN, false),
                new Case("sRGB + RGB-H", ColorSpace.getSRGB(), PixelGeometry.RGB_H, false),
                new Case("legacy (null space)", null, PixelGeometry.UNKNOWN, false),
                new Case("legacy + RGB-H", null, PixelGeometry.RGB_H, false),
                new Case("legacy + BGR-H", null, PixelGeometry.BGR_H, false));

        // Two polarities: the UI is light-on-dark (dark panel, light text).
        for (boolean lightOnDark : new boolean[]{true, false}) {
            System.out.printf(Locale.ROOT, "%n=== %s ===%n", lightOnDark ? "light text on dark panel" : "dark text on light panel");
            System.out.printf(Locale.ROOT, "%-30s %10s %8s %8s %8s %8s%n",
                    "case", "transition", "mid%", "ink", "minLuma", "coverage");
            for (Case c : cases) {
                float[] buf = render(face, c, lightOnDark);
                int w = (int) buf[0], h = (int) buf[1];
                int[] luma = luma(buf, w, h);
                ImageIO.write(toImage(buf, w, h), "png",
                        out.resolve(slug(c.name() + (lightOnDark ? "-light" : "-dark")) + ".png").toFile());
                double[] t = transitions(luma, w, h);
                System.out.printf(Locale.ROOT, "%-30s %10.3f %8.2f %8.1f %8.0f %8.1f%n",
                        c.name(), t[0], midShare(luma, w, h) * 100.0, inkShare(luma, w, h) * 100.0,
                        minLuma(luma, lightOnDark), coverage(luma, w, h, lightOnDark) * 100.0);
            }
        }
        System.out.println("\nSharper = wider ink contrast and narrower transition at the same coverage.");
    }

    private static float[] render(Typeface face, Case c, boolean lightOnDark) {
        Font font = new Font(face, FONT_SIZE);
        font.setSubpixel(true);
        font.setHinting(FontHinting.NONE);
        font.setEdging(c.geometry() == PixelGeometry.UNKNOWN
                ? FontEdging.ANTI_ALIAS : FontEdging.SUBPIXEL_ANTI_ALIAS);
        float width = font.measureTextWidth(SAMPLE) * DENSITY;
        int w = (int) Math.ceil(width) + PAD * 2;
        int h = (int) Math.ceil(FONT_SIZE * 1.6F * DENSITY) + PAD * 2;
        int fg = lightOnDark ? 0xFFFFFFFF : 0xFF000000;
        int bg = lightOnDark ? 0xFF14161A : 0xFFFFFFFF;

        ImageInfo info = new ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.PREMUL, c.space());
        SurfaceProps props = new SurfaceProps(c.deviceIndependent(), c.geometry());
        try (Surface surface = Surface.makeRaster(info, 0, props);
             Paint paint = new Paint().setColor(fg)) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(bg);
            canvas.scale(DENSITY, DENSITY);
            canvas.drawString(SAMPLE, PAD / DENSITY, PAD / DENSITY + FONT_SIZE, font, paint);
            try (Bitmap bitmap = new Bitmap()) {
                bitmap.allocN32Pixels(w, h);
                surface.readPixels(bitmap, 0, 0);
                byte[] px = bitmap.readPixels();
                float[] result = new float[2 + w * h * 3];
                result[0] = w;
                result[1] = h;
                for (int i = 0; i < w * h; i++) {
                    int b = px[i * 4] & 255, g = px[i * 4 + 1] & 255, r = px[i * 4 + 2] & 255;
                    result[2 + i * 3] = r;
                    result[3 + i * 3] = g;
                    result[4 + i * 3] = b;
                }
                return result;
            }
        }
    }

    private static Typeface bundledFace() throws IOException {
        Path bundled = Path.of("platforms/1.21.1-neoforge/build/resources/main/assets/atomchat/font/bundled.otf");
        Data data = Data.makeFromBytes(Files.readAllBytes(bundled));
        Typeface face = FontMgr.getDefault().makeFromData(data);
        return face.getVariations().length > 0 ? face.makeClone(new FontVariation("wght", 500)) : face;
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

    /** Mean 10%→90% transition width across every ink→background run. */
    private static double[] transitions(int[] luma, int w, int h) {
        double total = 0.0;
        int edges = 0;
        int[] line = new int[w];
        for (int y = 0; y < h; y++) {
            System.arraycopy(luma, y * w, line, 0, w);
            int lo = 255, hi = 0;
            for (int v : line) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            if (hi - lo < 120) continue;
            double low = lo + (hi - lo) * 0.1, high = lo + (hi - lo) * 0.9;
            int i = 0;
            while (i < w) {
                if (line[i] >= low) { i++; continue; }
                int inkStart = i;
                while (i < w && line[i] < low) i++;
                int inkEnd = i - 1;
                double in = Math.abs(crossing(line, inkStart, -1, high) - crossing(line, inkStart, -1, low));
                double outW = Math.abs(crossing(line, inkEnd, 1, high) - crossing(line, inkEnd, 1, low));
                if (in > 0 || outW > 0) { total += (in + outW) / 2.0; edges++; }
            }
        }
        return new double[]{edges == 0 ? 0.0 : total / edges, edges};
    }

    private static double crossing(int[] line, int from, int dir, double level) {
        for (int j = from; j + dir >= 0 && j + dir < line.length; j += dir) {
            int a = line[j], b = line[j + dir];
            if (a != b && (a < level) != (b < level)) return j + dir * (level - a) / (double) (b - a);
        }
        return from;
    }

    private static double midShare(int[] luma, int w, int h) {
        int mid = 0, ink = 0;
        for (int i = 0; i < w * h; i++) {
            int v = luma[i];
            if (v > 247) continue;
            ink++;
            if (v > 8) mid++;
        }
        return ink == 0 ? 0.0 : mid / (double) ink;
    }

    private static double inkShare(int[] luma, int w, int h) {
        int ink = 0;
        for (int v : luma) if (v < 128) ink++;
        return ink / (double) (w * h);
    }

    private static double minLuma(int[] luma, boolean lightOnDark) {
        int best = lightOnDark ? 255 : 0;
        for (int v : luma) best = lightOnDark ? Math.min(best, 255 - v) : Math.max(best, v);
        return lightOnDark ? 255 - best : best;
    }

    /** Sum of ink coverage: 1 - luma/255 normalised against the background. */
    private static double coverage(int[] luma, int w, int h, boolean lightOnDark) {
        double total = 0.0;
        for (int v : luma) total += lightOnDark ? v / 255.0 : 1.0 - v / 255.0;
        return total / (w * (double) h);
    }

    private static String slug(String name) {
        return name.replaceAll("[^a-z0-9]+", "-");
    }

    private ColorSpaceProbe() { }
}
