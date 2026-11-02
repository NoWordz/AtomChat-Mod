package com.atom.chat.probe;

import com.atom.chat.render.ChatBubble;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiCards;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Edge profile probe for the REAL UI primitives (read-only diagnostic).
 *
 * <p><b>The question.</b> Our rounded shapes read softer than a GLSL-SDF UI.
 * Skia's {@code drawRRect} uses per-fragment analytic area coverage, which is a
 * 1-device-pixel ramp at any subpixel phase — the theoretical optimum — so the
 * softness has to come from what surrounds the edge, not from the edge itself.
 * This probe renders the actual primitives ({@link UiCards#drawCard},
 * {@link ChatBubble}) at the session's real density and prints the luma profile
 * walking from outside the shape into its fill, then reports three widths:</p>
 * <ul>
 *   <li><b>halo</b> — how many device pixels of shadow gradient sit outside the
 *       edge. A wide halo is what the eye reads as "soft" even when the edge
 *       contrast itself is perfect.</li>
 *   <li><b>edge</b> — the 10%→90% transition width of the fill boundary.</li>
 *   <li><b>line</b> — the width of the hairline/rim stroke, and whether it lands
 *       on the device grid (a stroke that is not an integer number of device
 *       pixels cannot be a crisp line).</li>
 * </ul>
 *
 * <p>Also prints the token arithmetic that produces those numbers, so the fix
 * target is a number, not a feeling.</p>
 *
 * <p><b>What it settles.</b> Every shape here is drawn at a <em>fractional</em>
 * device phase (120 logical px at density 1.4815 is device 177.78), and the
 * fill edge still comes out as one partial pixel plus fully covered pixels —
 * coverage 0.223 at that phase, which is exactly what an edge at 0.78 of the
 * way through a pixel should produce. So antialiasing quality does not depend
 * on where the shape lands: Skia computes analytic area coverage per fragment.
 * Snapping a <em>fill</em> to the device grid therefore buys nothing, and the
 * only geometry worth quantizing is stroke <em>width</em> (a 1.85-device-px
 * stroke cannot be drawn crisply at any phase) and the phase of strokes that
 * should cover whole pixels.</p>
 */
public final class EdgeProfileProbe {
    static final float DENSITY = 1600.0F / 1080.0F;
    static final int W = 640, H = 420;
    static final int SAMPLE_ROW = 210;

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : ".work/edge-profile");
        Files.createDirectories(out);

        System.out.printf(Locale.ROOT, "density=%.4f  SCALE=%.2f  device px per logical px=%.4f%n",
                DENSITY, UiTokens.SCALE, DENSITY);

        System.out.printf(Locale.ROOT, "%n%-22s %8s %8s %8s  %s%n",
                "primitive", "halo", "edge", "line", "note");

        // --- a card, drawn exactly the way the list draws it ---
        BufferedImage card = render(out, "card", canvas ->
                UiCards.drawCard(canvas, 120.0F, 120.0F, 400.0F, 200.0F, UiTokens.radius(14), 0.0F));
        report(card, 120.0F, 120.0F, 400.0F, 200.0F, "card (shadow+fill+hairline)");

        // --- same rect, fill only: isolates the edge from the halo ---
        BufferedImage fillOnly = render(out, "fill-only", canvas ->
                SkiaDraw.drawRoundedRect(canvas, 120.0F, 120.0F, 400.0F, 200.0F,
                        UiTokens.radius(14), UiTokens.cardFill()));
        report(fillOnly, 120.0F, 120.0F, 400.0F, 200.0F, "card fill only");

        // --- fill + hairline, no shadow: isolates the stroke ---
        BufferedImage withHairline = render(out, "fill+hairline", canvas -> {
            SkiaDraw.drawRoundedRect(canvas, 120.0F, 120.0F, 400.0F, 200.0F,
                    UiTokens.radius(14), UiTokens.cardFill());
            SkiaDraw.drawEdgeHighlight(canvas, 120.0F, 120.0F, 400.0F, 200.0F,
                    UiTokens.radius(14), UiTokens.s(1.0F), UiTokens.hairline());
        });
        report(withHairline, 120.0F, 120.0F, 400.0F, 200.0F, "card fill + hairline");

        // --- hairline at one DEVICE pixel, centred on a pixel centre ---
        BufferedImage crisp = render(out, "hairline-device-px", canvas -> {
            float d = DENSITY;
            float stroke = 1.0F / d;                 // exactly 1 device px
            float cx = snap(120.0F, d), cy = snap(120.0F, d);
            float w = snap(120.0F + 400.0F, d) - cx;
            float h = snap(120.0F + 200.0F, d) - cy;
            SkiaDraw.drawRoundedRect(canvas, cx, cy, w, h, UiTokens.radius(14), UiTokens.cardFill());
            // Half a device pixel in: puts the stroke's CENTRE on a pixel centre.
            SkiaDraw.drawEdgeHighlight(canvas, cx + 0.5F / d, cy + 0.5F / d, w - 1.0F / d, h - 1.0F / d,
                    UiTokens.radius(14), stroke, UiTokens.hairline());
        });
        report(crisp, 120.0F, 120.0F, 400.0F, 200.0F, "hairline @1 device px");

        // --- shadow bleed: a translucent card lets its own shadow show through ---
        BufferedImage shadowOnly = render(out, "shadow-only", canvas ->
                SkiaDraw.drawRoundedShadow(canvas, 120.0F, 120.0F, 400.0F, 200.0F,
                        UiTokens.radius(14), UiTokens.CONTENT_SHADOW_BLUR, UiTokens.CARD_SHADOW));
        System.out.printf(Locale.ROOT, "%n--- inside-the-card luma (translucent fill shows the shadow) ---%n");
        System.out.println("  x from left edge -> interior, every 20 device px:");
        System.out.print("  card stack : ");
        for (int step = 1; step <= 16; step++) {
            int x = Math.min(W - 1, Math.round(120.0F * DENSITY) + step * 20);
            System.out.print(lumaAt(card, x, SAMPLE_ROW) + " ");
        }
        System.out.println();
        System.out.print("  fill only  : ");
        for (int step = 1; step <= 16; step++) {
            int x = Math.min(W - 1, Math.round(120.0F * DENSITY) + step * 20);
            System.out.print(lumaAt(fillOnly, x, SAMPLE_ROW) + " ");
        }
        System.out.println();
        System.out.print("  shadow only: ");
        for (int step = 1; step <= 16; step++) {
            int x = Math.min(W - 1, Math.round(120.0F * DENSITY) + step * 20);
            System.out.print(lumaAt(shadowOnly, x, SAMPLE_ROW) + " ");
        }
        System.out.println();

        // --- a chat bubble, the shape the user is looking at ---
        BufferedImage bubble = render(out, "bubble", canvas ->
                ChatBubble.draw(canvas, 120.0F, 120.0F, 400.0F, 160.0F,
                        com.atom.chat.config.AtomChatConfig.BubbleStyle.CORNER, false, 0xFF2B3947));
        report(bubble, 120.0F, 120.0F, 400.0F, 160.0F, "chat bubble");

        // --- token arithmetic: what the stroke width becomes in device px ---
        System.out.printf(Locale.ROOT, "%n--- stroke arithmetic ---%n");
        line("hairline stroke s(1.0)", UiTokens.s(1.0F));
        line("rim stroke s(1.0)", UiTokens.s(1.0F));
        line("panel inset", UiTokens.panelInset());
        line("CONTENT_SHADOW_BLUR s(6)", UiTokens.CONTENT_SHADOW_BLUR);
        line("CARD_SHADOW alpha", -1);

        System.out.printf(Locale.ROOT, "CARD_SHADOW = 0x%08X (alpha %d/255)%n",
                UiTokens.CARD_SHADOW, (UiTokens.CARD_SHADOW >>> 24) & 0xFF);

        // --- the panel edge, drawn the only two ways it is drawn in this UI ---
        // 1. the GLSL pre-pass: coverage = 1 - smoothstep(-fwidth, fwidth, dist)
        //    which is a ~2-device-px ramp;
        // 2. the Skia tint on top of it: analytic area coverage, a 1-px ramp.
        System.out.printf(Locale.ROOT, "%n--- panel edge: shader ramp vs skia coverage ---%n");
        int[] shader = new int[24];
        int[] skia = new int[24];
        int edge = Math.round(120.0F * DENSITY);
        BufferedImage shaderBand = render(out, "panel-shader-edge", canvas ->
                drawSdfEdge(canvas, 120.0F, 120.0F, 400.0F, 200.0F));
        BufferedImage skiaBand = render(out, "panel-skia-edge", canvas ->
                SkiaDraw.drawRoundedRect(canvas, 120.0F, 120.0F, 400.0F, 200.0F,
                        UiTokens.radius(14), UiTokens.cardFill()));
        for (int i = 0; i < 24; i++) {
            int x = Math.min(W - 1, edge - 12 + i);
            shader[i] = lumaAt(shaderBand, x, SAMPLE_ROW);
            skia[i] = lumaAt(skiaBand, x, SAMPLE_ROW);
        }
        System.out.println("  shader(fwidth) " + java.util.Arrays.toString(shader));
        System.out.println("  skia(analytic) " + java.util.Arrays.toString(skia));
        System.out.println("\nhalo = device px of shadow gradient outside the fill edge");
        System.out.println("edge = 10-90% fill-edge transition width in device px");
        System.out.println("line = hairline stroke thickness in device px (integer = crisp)");
    }

    private static void line(String label, float logical) {
        float device = logical * DENSITY;
        System.out.printf(Locale.ROOT, "%-28s %7.3f logical = %7.3f device px%s%n",
                label, logical, device,
                Math.abs(device - Math.round(device)) < 0.02F ? "" : "  <- NOT on the device grid");
    }

    private static BufferedImage render(Path out, String name,
                                        java.util.function.Consumer<Canvas> painter) throws IOException {
        try (Surface surface = Surface.makeRasterN32Premul(W, H)) {
            Canvas canvas = surface.getCanvas();
            // The real backdrop: a mid grey panel, so the edge contrast matches
            // the game rather than a white test sheet.
            canvas.clear(0xFF1B1F26);
            canvas.scale(DENSITY, DENSITY);
            painter.accept(canvas);
            BufferedImage image = read(surface, W, H);
            ImageIO.write(image, "png", out.resolve(name + ".png").toFile());
            return image;
        }
    }

    private static BufferedImage read(Surface surface, int w, int h) {
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(w, h);
            if (!surface.readPixels(bitmap, 0, 0)) throw new IllegalStateException("readPixels failed");
            byte[] px = bitmap.readPixels();
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = (y * w + x) * 4;
                    image.setRGB(x, y, ((px[i + 2] & 255) << 16) | ((px[i + 1] & 255) << 8) | (px[i] & 255));
                }
            }
            return image;
        }
    }

    /**
     * Walks the middle row from outside the left edge into the fill and reports
     * halo / edge / line widths in device px.
     */
    private static void report(BufferedImage image, float lx, float ly, float lw, float lh, String note) {
        int row = SAMPLE_ROW;
        int edgeX = Math.round(lx * DENSITY);
        int[] luma = new int[W];
        for (int x = 0; x < W; x++) {
            int p = image.getRGB(x, row);
            luma[x] = (((p >> 16) & 255) * 2126 + ((p >> 8) & 255) * 7152 + (p & 255) * 722) / 10000;
        }
        int bg = luma[2];
        int fill = luma[Math.min(W - 1, edgeX + 40)];

        // halo: contiguous pixels outside the edge whose value sits between bg
        // and fill (a shadow gradient), starting from the first deviation.
        int firstDev = -1;
        for (int x = 4; x < edgeX; x++) {
            if (Math.abs(luma[x] - bg) > 1) { firstDev = x; break; }
        }
        int halo = firstDev < 0 ? 0 : Math.max(0, edgeX - firstDev);

        // edge: 10-90% crossing around the fill boundary.
        double lo = bg + (fill - bg) * 0.1, hi = bg + (fill - bg) * 0.9;
        int c10 = -1, c90 = -1;
        for (int x = Math.max(1, edgeX - 8); x < Math.min(W - 1, edgeX + 8); x++) {
            if (c10 < 0 && crossing(luma, x, lo)) c10 = x;
            if (c90 < 0 && crossing(luma, x, hi)) c90 = x;
        }
        double edgeW = c10 >= 0 && c90 >= 0 ? Math.max(0, c90 - c10) : 0;

        // line: a local maximum just inside the edge (the hairline highlight),
        // measured as the pixels whose value exceeds both neighbours' baseline.
        int peak = -1, peakV = Integer.MIN_VALUE;
        for (int x = edgeX; x < Math.min(W - 2, edgeX + 10); x++) {
            if (luma[x] > peakV) { peakV = luma[x]; peak = x; }
        }
        int lineW = 0;
        if (peak > 0 && peakV > fill + 2) {
            int base = fill;
            int x = peak;
            while (x > 0 && luma[x] > base + 1) { lineW++; x--; }
            x = peak + 1;
            while (x < W && luma[x] > base + 1) { lineW++; x++; }
        }

        System.out.printf(Locale.ROOT, "%-22s %8d %8.1f %8d  %s%n", note, halo, edgeW, lineW,
                halo == 0 && lineW == 0 ? "edge only" : "");
        // The raw profile is the unambiguous evidence: 18 device px either side.
        StringBuilder sb = new StringBuilder("    profile ");
        for (int x = Math.max(0, edgeX - 18); x < Math.min(W, edgeX + 18); x++) {
            sb.append(luma[x]).append(x == edgeX ? "*" : " ");
        }
        System.out.println(sb);
        sb = new StringBuilder("    delta   ");
        for (int x = Math.max(1, edgeX - 18); x < Math.min(W, edgeX + 18); x++) {
            int d = luma[x] - luma[x - 1];
            sb.append(d > 0 ? "+" : "").append(d).append(' ');
        }
        System.out.println(sb);
    }

    /**
     * The panel blur shader's own edge: `float aa = fwidth(dist); alpha = 1 -
     * smoothstep(-aa, aa, dist)`. Reproduced on the CPU so the two AA curves can
     * be compared pixel for pixel.
     */
    private static void drawSdfEdge(Canvas canvas, float x, float y, float w, float h) {
        int fill = 0xFF3A3F48;
        int bg = 0xFF1B1F26;
        for (int i = 0; i < W; i++) {
            float fx = (i + 0.5F) / DENSITY;
            float dist = fx - x;            // signed distance to the left edge
            float aa = 1.0F / DENSITY;      // fwidth(dist) for a straight edge
            float cov = 1.0F - smoothstep(-aa, aa, dist);
            int c = lerpColor(bg, fill, cov);
            try (io.github.humbleui.skija.Paint p = new io.github.humbleui.skija.Paint().setColor(c)) {
                canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(
                        i / DENSITY, 0.0F, 1.0F / DENSITY, H / DENSITY), p);
            }
        }
    }

    private static float snap(float value, float density) {
        return Math.round(value * density) / density;
    }

    private static float smoothstep(float e0, float e1, float v) {
        float t = Math.max(0.0F, Math.min(1.0F, (v - e0) / (e1 - e0)));
        return t * t * (3.0F - 2.0F * t);
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg2 = (b >> 8) & 255, bb = b & 255;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg2 - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int lumaAt(BufferedImage image, int x, int y) {
        int p = image.getRGB(x, y);
        return (((p >> 16) & 255) * 2126 + ((p >> 8) & 255) * 7152 + (p & 255) * 722) / 10000;
    }

    private static boolean crossing(int[] luma, int x, double level) {
        int a = luma[x], b = luma[x + 1];
        if (a == b) return false;
        return (a < level) != (b < level);
    }

    private EdgeProfileProbe() { }
}
