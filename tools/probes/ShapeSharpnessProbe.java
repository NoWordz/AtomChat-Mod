package com.atom.chat.probe;

import com.atom.chat.render.DotTexture;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiCards;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Shape edge sharpness evidence probe (read-only diagnostic, unit F).
 *
 * <p>Renders the four shape families the UI actually draws — a filled rounded
 * card, a 1px hairline rim ring, a shadowed card, and the dot material edge —
 * through the exact primitives the mod uses ({@link SkiaDraw},
 * {@link UiCards#drawCard}, {@link DotTexture#drawEdge}, {@link UiTokens}) at
 * densities 1.0 / 1.3333 / 1.5 and two device-pixel phases (offset 0 and 0.5
 * device px).</p>
 *
 * <p>Ground truth: the same scene rendered at 4x the density and
 * box-downsampled — the ideal an analytic (SDF+fwidth) renderer approximates.
 * Metrics per shape edge: the 10-90% transition width in device px and the
 * mean |dLuma/dx| over the transition band, real vs ground truth, so softness
 * is a ratio, not a vibe. Shadow seepage is the luma profile under the card;
 * dot alignment is the intermediate-tone share in the dots band.</p>
 *
 * <p>Outputs under the given dir: PNG per scene, 4x crops of the card corner
 * and the rim, metrics.csv, stdout summary. Nothing here changes product code —
 * this is the evidence base for the sharpness fix list.</p>
 */
public final class ShapeSharpnessProbe {
    static final int W = 420;
    static final int H = 500;
    static int SS = 4; // supersample factor for ground truth, fitted to the real framebuffer

    static final int BG = 0xFFF2F2F2;
    static final int ACCENT = 0xFF3D7BFF;

    // N32 on Windows is BGRA in memory; luma weights ordered accordingly.
    static int lumaAt(byte[] px, int stride, int x, int y) {
        int i = (y * stride) + x * 4;
        int b = px[i] & 255, g = px[i + 1] & 255, r = px[i + 2] & 255;
        return (r * 2126 + g * 7152 + b * 722) / 10000;
    }

    static int argbAt(byte[] px, int stride, int x, int y) {
        int i = (y * stride) + x * 4;
        return 0xFF000000 | ((px[i + 2] & 255) << 16) | ((px[i + 1] & 255) << 8) | (px[i] & 255);
    }

    public static void main(String[] args) throws Exception {
        Path outDir = Path.of(args.length > 0 ? args[0] : ".");
        Files.createDirectories(outDir);

        if (!GLFW.glfwInit()) throw new AssertionError("glfwInit failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, 8);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        long window = GLFW.glfwCreateWindow(W * SS, H * SS, "shape sharpness probe", 0, 0);
        if (window == 0) throw new AssertionError("GL 3.2 core window creation failed");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        int[] fw = new int[1], fh = new int[1];
        GLFW.glfwGetFramebufferSize(window, fw, fh);
        // The window manager can clamp the framebuffer below what was asked
        // (here 1840 -> 1570); the supersample factor must fit inside it or
        // the ground-truth readback runs past the drawable and reads black.
        SS = Math.max(2, Math.min(4, (int) Math.min(fw[0] / (double) W, fh[0] / (double) H / 1.5)));
        System.out.printf(Locale.ROOT, "window=%dx%d framebuffer=%dx%d ss=%d%n", W * SS, H * SS, fw[0], fh[0], SS);
        String renderer = GL11C.glGetString(GL11C.GL_RENDERER);

        double[] densities = {1.0, 4.0 / 3.0, 1.5};
        float[] offsets = {0.0F, 0.5F}; // device px
        StringBuilder csv = new StringBuilder();
        csv.append("renderer=").append(renderer).append('\n');
        csv.append("shape,density,offsetPx,metric,real,groundTruth,ratioRealOverGt\n");

        for (double density : densities) {
            for (float off : offsets) {
                String tag = String.format(Locale.ROOT, "d%.4f_off%.2f", density, off);
                Frame bm = render((float) density, off);
                Frame gt4 = render((float) density * SS, off);
                save(bm, outDir, "shape_gl_" + tag + ".png");
                downsampleAndSave(gt4, outDir, "shape_gt_" + tag + ".png");
                // Metrics run on the ground truth DOWNSAMPLED back to the
                // probe's density, so both frames share one coordinate system.
                Frame gt = downsample(gt4);

                float d = (float) density;
                int cardEdge = Math.round(360 * d + off);
                int ringEdge = Math.round(250 * d + off);
                int shadowX = Math.round(210 * d + off);
                int shadowEdge = Math.round(390 * d + off);

                // 1. card fill: right edge, horizontal scanline through mid-card.
                edge(csv, "card", density, off, bm, gt, cardEdge, Math.round(110 * d + off));
                // 2. hairline rim: right side of the ring, horizontal scanline.
                edge(csv, "rim", density, off, bm, gt, ringEdge, Math.round(210 * d + off));
                // 3. shadow seepage: luma 1..7 device px under the card's bottom edge.
                csv.append(String.format(Locale.ROOT, "shadow,%.4f,%.2f,luma1to7=%s,luma1to7=%s,%s%n",
                        density, off, profile(bm, shadowX, shadowEdge), profile(gt, shadowX, shadowEdge),
                        seepRatio(bm, gt, shadowX, shadowEdge)));
                // 4. dot material: intermediate-tone share inside the dots band.
                int ddx = Math.round(60 * d + off), ddy = Math.max(0, Math.round(430 * d) - Math.round(22 * d)), ddw = Math.round(300 * d), ddh = Math.round(24 * d);
                double realDepth = 242 - bandMin(bm, ddx, ddy, ddw, ddh);
                double gtDepth = 242 - bandMin(gt, ddx, ddy, ddw, ddh);
                csv.append(String.format(Locale.ROOT, "dots,%.4f,%.2f,edgeDepth,%.0f,%.0f,%.2f%n",
                        density, off, realDepth, gtDepth, realDepth / Math.max(1.0, gtDepth)));

                // Jaggies metric: intermediate-tone share in a 4px frame
                // around the card outline (corners included). A smooth
                // analytic arc keeps the edge tones graduated; a staircase
                // collapses them into hard steps, so the share drops.
                int fx0 = Math.round(60 * d + off) - 4, fy0 = Math.round(40 * d + off) - 4;
                int frmW = Math.round(300 * d) + 8, frmH = Math.round(140 * d) + 8;
                double realFrame = frameIntermediateShare(bm, fx0, fy0, frmW, frmH, Math.round(4 * d));
                double gtFrame = frameIntermediateShare(gt, fx0, fy0, frmW, frmH, Math.round(4 * d));
                csv.append(String.format(Locale.ROOT, "cardFrame,%.4f,%.2f,interShare,%.4f,%.4f,%.2f%n",
                        density, off, realFrame, gtFrame, realFrame / Math.max(1e-9, gtFrame)));
                crop(bm, cardEdge - 12, Math.round(40 * d + off) - 12, 60, 60, outDir, "crop_card_" + tag + ".png");
                crop(bm, ringEdge - 12, Math.round(210 * d + off) - 12, 60, 60, outDir, "crop_rim_" + tag + ".png");
                System.out.println("done " + tag);
            }
        }
        Files.writeString(outDir.resolve("metrics.csv"), csv.toString());
        System.out.print(csv);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        System.out.println("SHAPE_SHARPNESS_PROBE_DONE");
    }

    /** A rendered frame: pixel bytes plus stride. */
    record Frame(byte[] px, int stride, int width, int height) {
    }

    static Frame render(float scale, float deviceOffset) {
        int w = Math.round(W * scale), h = Math.round(H * scale);
        try (var context = DirectContext.makeGL();
             var target = BackendRenderTarget.makeGL(w, h, 0, 8, 0, GL11C.GL_RGBA8);
             var surface = Surface.wrapBackendRenderTarget(context, target, SurfaceOrigin.BOTTOM_LEFT,
                     ColorType.RGBA_8888, ColorSpace.getSRGB())) {
            if (surface == null) throw new AssertionError("GPU surface unavailable");
            context.resetGLAll();
            Canvas c = surface.getCanvas();
            c.clear(BG);
            c.scale(scale, scale);
            // deviceOffset is expressed in device px; in logical space it divides by the scale.
            float x = 60 + deviceOffset / scale;
            // 1. filled rounded card (the accent language).
            SkiaDraw.drawRoundedRect(c, x, 40, 300, 140, UiTokens.cardRadius(), ACCENT);
            // 2. hairline rim ring (the avatar/swatch language): 1 logical px
            // stroke. A dark backdrop sits behind it because rim() is
            // polarity-adaptive (white on dark) — on the bare light bg it
            // would be white-on-near-white and invisible by contrast, not by
            // sharpness.
            SkiaDraw.drawRoundedRect(c, x + 70, 160, 160, 100, 10, 0xFF2A2A2E);
            SkiaDraw.drawRing(c, x + 150, 210, 40, UiTokens.s(1.0F), UiTokens.rim());
            // 3. shadowed card (the card language: shadow + rim + fill in one call).
            UiCards.drawCard(c, x, 270, 300, 150, UiTokens.cardRadius(), 0,
                    UiTokens.CARD_SHADOW, UiTokens.NOTIFICATION_BLUR, 0xFFFFFFFF);
            // 4. dot material edge.
            DotTexture.drawEdge(c, new UiLayout.Rect(x, 430, 300, 50), 8, true, 0x66000000);
            context.flushAndSubmit(surface);
            GL11C.glFinish();
            Bitmap bm = new Bitmap();
            bm.allocN32Pixels(w, h);
            if (!surface.readPixels(bm, 0, 0)) throw new AssertionError("GL readback");
            byte[] px = bm.readPixels();
            if (px == null || px.length < w * h * 4) throw new AssertionError("empty readback at " + w + "x" + h);
            Frame f = new Frame(px, w * 4, w, h);
            bm.close();
            return f;
        }
    }

    /** 10-90% transition width and mean |grad| over the band, real vs ground truth. */
    static void edge(StringBuilder csv, String shape, double density, float off,
                     Frame bm, Frame gt, int xEdge, int y) {
        double[] a = transition(bm, y, xEdge);
        double[] b = transition(gt, y, xEdge);
        csv.append(String.format(Locale.ROOT, "%s,%.4f,%.2f,widthPx,%.2f,%.2f,%.2f%n",
                shape, density, off, a[0], b[0], a[0] / Math.max(0.01, b[0])));
        csv.append(String.format(Locale.ROOT, "%s,%.4f,%.2f,gradPerPx,%.1f,%.1f,%.2f%n",
                shape, density, off, a[1], b[1], a[1] / Math.max(0.01, b[1])));
        // Visibility depth: how far the darkest edge pixel dips below the
        // background. A hairline split across two fractional-density pixels
        // halves this — the "rim is invisible" metric.
        int depth = 242 - minIn(bm, y, xEdge);
        int gtDepth = 242 - minIn(gt, y, xEdge);
        csv.append(String.format(Locale.ROOT, "%s,%.4f,%.2f,edgeDepth,%.0f,%.0f,%.2f%n",
                shape, density, off, (double) depth, (double) gtDepth, depth / Math.max(1.0, gtDepth)));
        // Raw profiles for eyeballing (real first, ground truth second).
        csv.append(String.format(Locale.ROOT, "%s,%.4f,%.2f,profileReal,%s,,%n",
                shape, density, off, rawProfile(bm, y, xEdge)));
        csv.append(String.format(Locale.ROOT, "%s,%.4f,%.2f,profileGt,%s,,%n",
                shape, density, off, rawProfile(gt, y, xEdge)));
    }

    static int minIn(Frame f, int y, int xEdge) {
        int min = 255;
        for (int x = xEdge - 8; x <= xEdge + 8; x++) {
            min = Math.min(min, lumaAt(f.px(), f.stride(), x, y));
        }
        return min;
    }

    static String rawProfile(Frame f, int y, int xEdge) {
        StringBuilder sb = new StringBuilder();
        for (int x = xEdge - 6; x <= xEdge + 6; x++) {
            sb.append(lumaAt(f.px(), f.stride(), x, y)).append(x < xEdge + 6 ? "," : "");
        }
        return sb.toString();
    }

    /**
     * Transition metrics around the strongest step within +-10 px of the
     * expected edge: [0] = device-px width between the 10% and 90% levels,
     * [1] = mean |dLuma/dx| within +-2 px of the steepest point.
     */
    static double[] transition(Frame f, int y, int xEdge) {
        int x0 = Math.max(1, xEdge - 10), x1 = Math.min(f.width() - 1, xEdge + 10);
        int steep = x0, maxStep = 0;
        for (int x = x0; x < x1; x++) {
            int step = Math.abs(lumaAt(f.px(), f.stride(), x, y) - lumaAt(f.px(), f.stride(), x - 1, y));
            if (step > maxStep) {
                maxStep = step;
                steep = x;
            }
        }
        int hi = Math.max(lumaAt(f.px(), f.stride(), steep - 1, y), lumaAt(f.px(), f.stride(), steep, y));
        int lo = Math.min(lumaAt(f.px(), f.stride(), steep - 1, y), lumaAt(f.px(), f.stride(), steep, y));
        int span = Math.max(1, hi - lo);
        int t10 = lo + span / 10, t90 = lo + span * 9 / 10;
        int width = 0;
        for (int x = Math.max(0, steep - 8); x < Math.min(f.width(), steep + 8); x++) {
            int v = lumaAt(f.px(), f.stride(), x, y);
            if (v > Math.min(t10, t90) && v < Math.max(t10, t90)) width++;
        }
        double grad = 0;
        for (int x = Math.max(1, steep - 2); x < Math.min(f.width(), steep + 2); x++) {
            grad += Math.abs(lumaAt(f.px(), f.stride(), x, y) - lumaAt(f.px(), f.stride(), x - 1, y));
        }
        return new double[]{width, grad / 4.0};
    }

    static String profile(Frame f, int x, int yEdge) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 7; i++) {
            sb.append(lumaAt(f.px(), f.stride(), x, yEdge + i)).append(i < 7 ? "," : "");
        }
        return sb.toString();
    }

    /**
     * Real shadow luma sum over the first 7 px vs ground truth; >1 means the
     * MaskFilter shadow spreads further than the analytic ideal.
     */
    static String seepRatio(Frame bm, Frame gt, int x, int yEdge) {
        int r = 0, t = 0;
        for (int i = 1; i <= 7; i++) {
            r += lumaAt(bm.px(), bm.stride(), x, yEdge + i);
            t += lumaAt(gt.px(), gt.stride(), x, yEdge + i);
        }
        return String.format(Locale.ROOT, "%.2f", t == 0 ? 0 : (double) r / t);
    }

    static int bandMin(Frame f, int x0, int y0, int w, int h) {
        int min = 255;
        for (int y = y0; y < Math.min(y0 + h, f.height()); y++) {
            for (int x = x0; x < Math.min(x0 + w, f.width()); x++) {
                min = Math.min(min, lumaAt(f.px(), f.stride(), x, y));
            }
        }
        return min;
    }

    /**
     * Intermediate-tone share inside a frame band of {@code bandPx} width
     * around the given rect (the card outline, corners included).
     */
    static double frameIntermediateShare(Frame f, int x0, int y0, int w, int h, int bandPx) {
        long mid = 0, total = 0;
        for (int y = Math.max(0, y0); y < Math.min(y0 + h, f.height()); y++) {
            for (int x = Math.max(0, x0); x < Math.min(x0 + w, f.width()); x++) {
                boolean inner = x >= x0 + bandPx && x < x0 + w - bandPx && y >= y0 + bandPx && y < y0 + h - bandPx;
                if (inner) {
                    continue;
                }
                int l = lumaAt(f.px(), f.stride(), x, y);
                if (l > 140 && l < 235) {
                    mid++;
                }
                total++;
            }
        }
        return total == 0 ? 0 : (double) mid / total;
    }

    static double intermediateShare(Frame f, int x0, int y0, int w, int h) {
        long mid = 0, total = 0;
        for (int y = y0; y < Math.min(y0 + h, f.height()); y++) {
            for (int x = x0; x < Math.min(x0 + w, f.width()); x++) {
                int l = lumaAt(f.px(), f.stride(), x, y);
                // intermediate = clearly between the bg (242) and the dot ink
                if (l > 40 && l < 210) mid++;
                total++;
            }
        }
        return total == 0 ? 0 : (double) mid / total;
    }

    /** Box-downsamples the SSx ground truth back to the probe's density. */
    static Frame downsample(Frame gt4) {
        int w = gt4.width() / SS, h = gt4.height() / SS;
        byte[] out = new byte[w * h * 4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0;
                for (int sy = 0; sy < SS; sy++) {
                    for (int sx = 0; sx < SS; sx++) {
                        int i = ((y * SS + sy) * gt4.stride()) + (x * SS + sx) * 4;
                        r += gt4.px()[i + 2] & 255;
                        g += gt4.px()[i + 1] & 255;
                        b += gt4.px()[i] & 255;
                    }
                }
                r /= SS * SS;
                g /= SS * SS;
                b /= SS * SS;
                int o = (y * w + x) * 4;
                out[o] = (byte) b;
                out[o + 1] = (byte) g;
                out[o + 2] = (byte) r;
                out[o + 3] = (byte) 255;
            }
        }
        return new Frame(out, w * 4, w, h);
    }

    /** Saves the downsampled ground truth as PNG. */
    static void downsampleAndSave(Frame gt4, Path dir, String name) throws Exception {
        Frame gt = downsample(gt4);
        BufferedImage img = new BufferedImage(gt.width(), gt.height(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < gt.height(); y++) {
            for (int x = 0; x < gt.width(); x++) {
                img.setRGB(x, y, argbAt(gt.px(), gt.stride(), x, y));
            }
        }
        ImageIO.write(img, "png", dir.resolve(name).toFile());
    }

    static void crop(Frame f, int x0, int y0, int w, int h, Path dir, String name) throws Exception {
        BufferedImage img = new BufferedImage(w * 4, h * 4, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h * 4; y++) {
            for (int x = 0; x < w * 4; x++) {
                img.setRGB(x, y, argbAt(f.px(), f.stride(), x0 + x / 4, y0 + y / 4));
            }
        }
        ImageIO.write(img, "png", dir.resolve(name).toFile());
    }

    static void save(Frame f, Path dir, String name) throws Exception {
        BufferedImage img = new BufferedImage(f.width(), f.height(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < f.height(); y++) {
            for (int x = 0; x < f.width(); x++) {
                img.setRGB(x, y, argbAt(f.px(), f.stride(), x, y));
            }
        }
        ImageIO.write(img, "png", dir.resolve(name).toFile());
    }
}
