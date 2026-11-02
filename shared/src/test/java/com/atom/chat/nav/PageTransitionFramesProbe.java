package com.atom.chat.nav;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.UiStagger;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.platform.Platform;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders the page-entrance frame sequence off an offscreen raster surface
 * through the real {@link PageTransitionComposite} draw path and writes PNGs,
 * so a human can eyeball the motion. The assertions in
 * {@link PageTransitionCompositeTest} pin the numbers; this class exists
 * because numbers alone said "correct" while the field said "it jumps".
 *
 * <p>Output: {@code .work/2026-11-02-entrance-frames/}.</p>
 */
class PageTransitionFramesProbe {

    private static final int W = 480;
    private static final int H = 860;
    private static final Path OUT = Path.of(".work", "2026-11-02-entrance-frames");

    @TempDir
    Path tmp;

    private boolean animations;
    private AtomChatConfig.PageNavStyle style;

    @BeforeAll
    static void installTemporaryPlatform(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String modId) { return false; }
        });
    }

    @BeforeEach
    void setUp() throws Exception {
        AtomChatConfig config = AtomChatConfig.get();
        animations = config.animationEnabled;
        style = config.pageNavStyle;
        config.animationEnabled = true;
        Files.createDirectories(OUT);
        UiLayout layout = UiLayout.ofDetail(0, 0, W, H);
        host.layout = layout;
    }

    @AfterEach
    void tearDown() {
        AtomChatConfig config = AtomChatConfig.get();
        config.animationEnabled = animations;
        config.pageNavStyle = style;
    }

    private static final class Host implements PageTransitionComposite.Host {
        NavPage top = NavPage.of(AppPage.WORLD_CHAT);
        UiLayout layout;
        boolean staggersSurfaces;
        public NavPage topNav() { return top; }
        public void drawArrivingBody(Canvas c, UiLayout l, int mx, int my) {
            try (Paint p = new Paint().setColor(0xFFE0405E)) {
                c.drawRect(io.github.humbleui.types.Rect.makeXYWH(40, 120, W - 80, 200), p);
            }
            try (Paint p = new Paint().setColor(0xFF2E3A47)) {
                c.drawRect(io.github.humbleui.types.Rect.makeXYWH(40, 360, W - 80, 120), p);
                c.drawRect(io.github.humbleui.types.Rect.makeXYWH(40, 520, W - 80, 120), p);
            }
        }
        public void drawArrivingHeader(Canvas c, UiLayout l, int mx, int my, float vmx, float vmy) {
            try (Paint p = new Paint().setColor(0xFFFFFFFF)) {
                c.drawRect(io.github.humbleui.types.Rect.makeXYWH(40, 40, 180, 44), p);
            }
        }
        public boolean arrivalStaggersSurfaces() { return staggersSurfaces; }
    }

    private final Host host = new Host();
    private PageTransitionComposite composite;

    private void shoot(Surface s, long nowMs, String name) throws Exception {
        try (Canvas c = s.getCanvas()) {
            c.clear(0xFF10151C);
            composite.drawArrivingFrame(c, host.layout, 0, 0, 0, 0, nowMs);
        }
        try (Image img = s.makeImageSnapshot()) {
            byte[] png = img.encodeToData(EncodedImageFormat.PNG).getBytes();
            Files.write(OUT.resolve(name + ".png"), png);
            System.out.println("frame " + name + "  alpha=" + String.format("%.3f", composite.overlayAlpha(nowMs)));
        }
    }

    private void runSequence(String styleName, long stride) throws Exception {
        AtomChatConfig.get().pageNavStyle = AtomChatConfig.PageNavStyle.valueOf(styleName);
        composite = new PageTransitionComposite(host);
        try (Surface s = Surface.makeRasterN32Premul(W, H)) {
            long t0 = 10_000_000L;
            composite.beginFrame(t0);           // establish WORLD
            composite.consumeArrivalEvent();
            host.top = NavPage.of(AppPage.PROFILE_DETAIL);
            long arrive = t0 + 16;
            composite.beginFrame(arrive);
            composite.consumeArrivalEvent();
            long[] stamps = {0, 32, 64, 96, 128, 160, 200, 250, 320, 400};
            for (long d : stamps) {
                shoot(s, arrive + d, styleName.toLowerCase() + "_t" + d);
            }
            // interrupt: re-stamp 150ms in, replay from the top
            host.top = NavPage.of(AppPage.WORLD_CHAT);
            long interrupt = arrive + 150;
            composite.beginFrame(interrupt);
            composite.consumeArrivalEvent();
            for (long d : new long[]{0, 32, 64, 128, 200, 320}) {
                shoot(s, interrupt + d, styleName.toLowerCase() + "_interrupt_t" + d);
            }
        }
    }

    @Test
    void slideFrames() throws Exception {
        runSequence("SLIDE", 0);
    }

    @Test
    void zoomFrames() throws Exception {
        runSequence("ZOOM", 0);
    }

    /**
     * The list-page stagger with its style channel under BOTH global styles, so
     * a human can see they are not the same gesture: SLIDE drifts each row in
     * from the right (and only sideways), ZOOM settles each row from 0.97 around
     * its own centre while it lifts. Both read the SAME item progress — the
     * frames are the evidence that one clock can carry the channels safely.
     */
    @Test
    void styleStaggerFrames() throws Exception {
        for (AtomChatConfig.PageNavStyle style : AtomChatConfig.PageNavStyle.values()) {
            AtomChatConfig.get().pageNavStyle = style;
            UiStagger.Axis axis = axisFor(style);
            UiStagger.Entrance e = new UiStagger.Entrance();
            long t0 = 30_000_000L;
            e.markEntered(t0);
            String prefix = "style_" + style.name().toLowerCase() + "_t";
            try (Surface s = Surface.makeRasterN32Premul(W, H)) {
                for (long d : new long[]{0, 40, 80, 120, 160, 240, 320, 420, 620}) {
                    try (Canvas c = s.getCanvas()) {
                        c.clear(0xFF10151C);
                        for (int i = 0; i < 8; i++) {
                            float alpha = UiStagger.itemAlpha(e, i, t0 + d);
                            float rise = UiStagger.itemRise(e, i, t0 + d, axis);
                            float dx = UiStagger.itemStyleSlidePx(e, i, t0 + d, axis);
                            float scale = UiStagger.itemStyleZoomScale(e, i, t0 + d, axis);
                            float y = 90 + i * 90 + rise;
                            io.github.humbleui.types.Rect row =
                                    io.github.humbleui.types.Rect.makeXYWH(40, y, W - 80, 70);
                            c.save();
                            try (Paint p = new Paint().setAlphaf(alpha)) {
                                // The layer carries the drift so it cannot clip.
                                c.saveLayer(io.github.humbleui.types.Rect.makeXYWH(
                                        40, y, W - 80 + dx, 70), p);
                                c.translate(dx, 0.0F);
                                if (axis == UiStagger.Axis.VERTICAL) {
                                    float cx = row.getLeft() + row.getWidth() / 2.0F;
                                    float cy = row.getTop() + row.getHeight() / 2.0F;
                                    c.translate(cx, cy);
                                    c.scale(scale, scale);
                                    c.translate(-cx, -cy);
                                }
                                try (Paint fill = new Paint().setColor(0xFF2E3A47)) {
                                    c.drawRRect(io.github.humbleui.types.RRect.makeXYWH(
                                            40, y, W - 80, 70, 12), fill);
                                }
                                c.restore();
                            } finally {
                                c.restore();
                            }
                        }
                    }
                    try (Image img = s.makeImageSnapshot()) {
                        Files.write(OUT.resolve(prefix + d + ".png"),
                                img.encodeToData(EncodedImageFormat.PNG).getBytes());
                        System.out.println("frame " + prefix + d
                                + "  row0 alpha=" + String.format("%.3f", UiStagger.itemAlpha(e, 0, t0 + d))
                                + "  row0 dx=" + String.format("%.2f",
                                        UiStagger.itemStyleSlidePx(e, 0, t0 + d, axis))
                                + "  row0 rise=" + String.format("%.2f",
                                        UiStagger.itemRise(e, 0, t0 + d, axis))
                                + "  row0 scale=" + String.format("%.3f",
                                        UiStagger.itemStyleZoomScale(e, 0, t0 + d, axis)));
                    }
                }
            }
        }
    }

    /**
     * SLIDE is a horizontal entrance and nothing else: on every frame of the
     * stagger the row's vertical travel must be exactly zero while its drift is
     * still positive. The rendered half of the pin also checks that the row's
     * right edge stays clear of the widened clip the list surfaces use — a row
     * whose rounded corner was shaved at the viewport edge reads as a squared
     * end, which is what the drift-in used to produce.
     */
    @Test
    void slideFramesAreHorizontalOnly() throws Exception {
        AtomChatConfig.get().pageNavStyle = AtomChatConfig.PageNavStyle.SLIDE;
        UiStagger.Entrance e = new UiStagger.Entrance();
        long t0 = 40_000_000L;
        e.markEntered(t0);
        // The geometry the list surfaces use: rows inset from the viewport by
        // ROW_CLIP_INSET, and the clip widened past the viewport by the drift
        // budget. The row's right edge must stay strictly inside that clip, or
        // its rounded corner is shaved flat on the way in.
        float rowInset = com.atom.lib.design.atomchat.UiTokens.ROW_CLIP_INSET;
        float listRight = W - 40.0F;
        float rowLeft = 40.0F + rowInset;
        float rowW = listRight - rowLeft - rowInset;
        float clipRight = listRight + UiStagger.ITEM_STYLE_SLIDE_PX;
        boolean anyDrift = false;
        try (Surface s = Surface.makeRasterN32Premul(W, H)) {
            for (long d : new long[]{0, 40, 80, 120, 160, 240, 320, 420}) {
                float rise = UiStagger.itemRise(e, 0, t0 + d, UiStagger.Axis.HORIZONTAL);
                float drift = UiStagger.itemStyleSlidePx(e, 0, t0 + d, UiStagger.Axis.HORIZONTAL);
                float scale = UiStagger.itemStyleZoomScale(e, 0, t0 + d, UiStagger.Axis.HORIZONTAL);
                assertEquals(0.0F, rise, 0.0F, "SLIDE row 0 lifted at t=" + d);
                assertEquals(1.0F, scale, 0.0F, "SLIDE row 0 scaled at t=" + d);
                assertTrue(drift >= 0.0F, "SLIDE drift is a non-negative travel");
                anyDrift |= drift > 0.0F;
                float rowRight = rowLeft + rowW + drift;
                assertTrue(rowRight < clipRight,
                        "t=" + d + "ms: row right edge " + rowRight + " reached the clip " + clipRight);
                // Drawn exactly like the list: slot + drift, layer widened by it.
                float y = 90.0F;
                try (Canvas c = s.getCanvas()) {
                    c.clear(0xFF10151C);
                    try (Paint p = new Paint().setAlphaf(UiStagger.itemAlpha(e, 0, t0 + d))) {
                        c.saveLayer(io.github.humbleui.types.Rect.makeXYWH(
                                rowLeft, y, rowW + drift, 70), p);
                        c.translate(drift, 0.0F);
                        try (Paint fill = new Paint().setColor(0xFF2E3A47)) {
                            c.drawRRect(io.github.humbleui.types.RRect.makeXYWH(
                                    rowLeft, y, rowW, 70, 12), fill);
                        }
                        c.restore();
                    }
                }
                try (Image img = s.makeImageSnapshot()) {
                    Files.write(OUT.resolve("slide_horizontal_t" + d + ".png"),
                            img.encodeToData(EncodedImageFormat.PNG).getBytes());
                    System.out.println("frame slide_horizontal_t" + d
                            + "  row0 rise=" + String.format("%.2f", rise)
                            + "  row0 drift=" + String.format("%.2f", drift)
                            + "  row0 right=" + String.format("%.2f", rowRight)
                            + "  clip=" + String.format("%.2f", clipRight));
                }
            }
        }
        assertTrue(anyDrift, "SLIDE never moved the row at all");
    }

    /** The nav style as the item axis — the same mapping the four surfaces use. */
    private static UiStagger.Axis axisFor(AtomChatConfig.PageNavStyle style) {
        return style == AtomChatConfig.PageNavStyle.ZOOM
                ? UiStagger.Axis.VERTICAL : UiStagger.Axis.HORIZONTAL;
    }

    /**
     * The list-page stagger (what a root tab arrival and a settings section now
     * play): eight rows, each fading and rising from ONE progress value, on the
     * 25ms step. Rendered straight, without the shell envelope.
     */
    @Test
    void staggerFrames() throws Exception {
        UiStagger.Entrance e = new UiStagger.Entrance();
        long t0 = 20_000_000L;
        e.markEntered(t0);
        try (Surface s = Surface.makeRasterN32Premul(W, H)) {
            for (long d : new long[]{0, 40, 80, 120, 160, 240, 320, 420, 620}) {
                try (Canvas c = s.getCanvas()) {
                    c.clear(0xFF10151C);
                    for (int i = 0; i < 8; i++) {
                        float alpha = UiStagger.itemAlpha(e, i, t0 + d);
                        float rise = UiStagger.itemRise(e, i, t0 + d, UiStagger.Axis.VERTICAL);
                        float y = 90 + i * 90 + rise;
                        c.save();
                        try (Paint p = new Paint().setAlphaf(alpha)) {
                            c.saveLayer(io.github.humbleui.types.Rect.makeXYWH(
                                    40, y, W - 80, 70), p);
                            try (Paint fill = new Paint().setColor(0xFF2E3A47)) {
                                c.drawRRect(io.github.humbleui.types.RRect.makeXYWH(
                                        40, y, W - 80, 70, 12), fill);
                            }
                            c.restore();
                        } finally {
                            c.restore();
                        }
                    }
                }
                try (Image img = s.makeImageSnapshot()) {
                    Files.write(OUT.resolve("stagger_t" + d + ".png"),
                            img.encodeToData(EncodedImageFormat.PNG).getBytes());
                    System.out.println("frame stagger_t" + d
                            + "  row0 alpha=" + String.format("%.3f", UiStagger.itemAlpha(e, 0, t0 + d))
                            + "  row7 alpha=" + String.format("%.3f", UiStagger.itemAlpha(e, 7, t0 + d)));
                }
            }
        }
    }
}
