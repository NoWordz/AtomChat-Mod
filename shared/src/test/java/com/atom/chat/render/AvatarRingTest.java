package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The avatar ring's geometry, the raster proof that it is concentric, and the
 * source guards that keep all five avatar sites on it.
 *
 * <p>Why all three: the numbers are trivial but the <em>coupling</em> is not.
 * The reported defect ("the avatar and its rim must touch") was five
 * hand-derived radii drifting apart — one site moving back to a pad-plus-gap
 * would look right in its own file and wrong on screen. The helper pins the
 * arithmetic, the raster pass pins that the rendered ring is actually centred
 * (a half-device-pixel centre nudge is invisible to any arithmetic check), and
 * the source guards pin that nobody re-inlines the radius or re-adds the
 * nudge.</p>
 */
class AvatarRingTest {

    private static final List<String> SITES = List.of(
            "layers/mapping/official/src/main/java/com/atom/chat/page/ProfilePage.java",
            "layers/mapping/official/src/main/java/com/atom/chat/page/MessageListView.java",
            "layers/mapping/official/src/main/java/com/atom/chat/page/ConversationListPage.java",
            "layers/mapping/official/src/main/java/com/atom/chat/notification/NotificationBanner.java",
            "layers/mapping/official/src/main/java/com/atom/chat/settings/SettingsSectionPage.java",
            "platforms/1.21.1-fabric/src/main/java/com/atom/chat/page/ProfilePage.java",
            "platforms/1.21.1-fabric/src/main/java/com/atom/chat/page/MessageListView.java",
            "platforms/1.21.1-fabric/src/main/java/com/atom/chat/page/ConversationListPage.java",
            "platforms/1.21.1-fabric/src/main/java/com/atom/chat/notification/NotificationBanner.java",
            "platforms/1.21.1-fabric/src/main/java/com/atom/chat/settings/SettingsSectionPage.java");

    @TempDir
    static Path tmp;

    @BeforeAll
    static void installPlatform() {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return tmp.resolve("config"); }
            public Path gameDir() { return tmp.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    private static Path root() {
        // Shared tests run inside a platform project; the repo root is handed in
        // by the layers script (the same property the golden frames read).
        String repo = System.getProperty("atomchat.repoRoot");
        return repo == null ? Path.of("../..") : Path.of(repo);
    }

    @Test
    void theSeparatorOuterEdgeIsTheRimOuterEdge() {
        // The invariant, not just the numbers: both circles are drawn about the
        // same centre, so the separator's radius has to BE the stroke's outer
        // radius. A written-down separator pad is what let them drift apart.
        assertEquals(AvatarRing.RIM_STROKE_PX, AvatarRing.SEPARATOR_PX, 0.0F,
                "the separator pad is the rim stroke, so the two outer edges coincide");
        assertEquals(UiTokens.s(1.0F), AvatarRing.RIM_STROKE_PX, 0.0F);
        for (float r : new float[]{UiTokens.s(22), UiTokens.s(38), UiTokens.s(18)}) {
            assertEquals(r + UiTokens.s(1.0F), AvatarRing.separatorRadius(r), 1e-4F);
            assertEquals(r + 0.5F * UiTokens.s(1.0F), AvatarRing.rimRadius(r), 1e-4F);
            // The rim's inner edge lands exactly on the bitmap edge: touching,
            // with only the separator pad behind it.
            assertEquals(r, AvatarRing.rimRadius(r) - 0.5F * AvatarRing.RIM_STROKE_PX, 1e-4F,
                    "the rim's inner edge IS the avatar's outer edge");
            assertEquals(AvatarRing.separatorRadius(r),
                    AvatarRing.rimRadius(r) + 0.5F * AvatarRing.RIM_STROKE_PX, 1e-4F,
                    "separator outer edge == rim outer edge, or a panel-colour ring shows");
        }
    }

    /**
     * The rendered guard the arithmetic cannot give: draw the exact avatar-site
     * recipe (separator, bitmap, rim) on a raster surface at a whole and a
     * fractional density, then walk four cardinal scanlines outward from the
     * bitmap's edge and measure where the rim stroke's centre falls.
     *
     * <p>With the stroke concentric the four distances agree (AA spread
     * included). A half-device-pixel centre nudge moved the ring off centre
     * instead: one side reached the stroke a pixel sooner and the opposite side
     * showed a deeper stroke two pixels out — the "seam" reported in the field.
     * The pin is therefore the <em>spread</em> between the four distances, not
     * any particular value.</p>
     */
    @Test
    void theRenderedRimIsConcentricAtWholeAndFractionalDensities() throws Exception {
        for (float density : new float[]{1.0F, 4.0F / 3.0F}) {
            float[] reach = measureReach(density);
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            for (float v : reach) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            System.out.printf("[avatar-ring] d=%.4f stroke leading edge from the bitmap edge, device px: "
                            + "left=%.2f right=%.2f top=%.2f bottom=%.2f spread=%.2f%n",
                    density, reach[0], reach[1], reach[2], reach[3], max - min);
            assertTrue(max - min <= 0.25F,
                    "d=" + density + " ring is not concentric: the stroke's leading edge sits "
                            + "left=" + reach[0] + " right=" + reach[1]
                            + " top=" + reach[2] + " bottom=" + reach[3]);
        }
    }

    /**
     * Renders the profile hero's avatar recipe and returns, for each of the four
     * cardinal directions, how many pixels past the bitmap edge it takes to stop
     * reading the bitmap colour.
     */
    private float[] measureReach(float density) throws Exception {
        UiPixelSnap.beginFrame(density);
        float size = UiTokens.PROFILE_AVATAR;
        float ox = 60.0F;
        float oy = 60.0F;
        int w = Math.round(size + 120.0F);
        int h = Math.round(size + 120.0F);
        int faceColor = 0xFFE0A040;
        try (Surface surface = Surface.makeRasterN32Premul(w, h);
             Surface faceSurface = Surface.makeRasterN32Premul(16, 16)) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(AtomChatConfig.get().panelBgColor);
            faceSurface.getCanvas().clear(faceColor);
            Image face = faceSurface.makeImageSnapshot();
            try {
                float ring = AvatarRing.SEPARATOR_PX;
                SkiaDraw.drawRoundedRect(canvas, ox - ring, oy - ring,
                        size + ring * 2.0F, size + ring * 2.0F,
                        (size + ring * 2.0F) / 2.0F,
                        UiTokens.withAlpha(AtomChatConfig.get().panelBgColor & 0xFFFFFF, 255));
                SkiaDraw.drawRoundedImage(canvas, face, ox, oy, size, size, size / 2.0F,
                        SamplingMode.LINEAR);
                SkiaDraw.drawRing(canvas, ox + size / 2.0F, oy + size / 2.0F,
                        AvatarRing.rimRadius(size / 2.0F), AvatarRing.RIM_STROKE_PX, UiTokens.rim());
            } finally {
                face.close();
            }
            try (Bitmap bmp = new Bitmap()) {
                bmp.allocN32Pixels(w, h);
                assertTrue(surface.readPixels(bmp, 0, 0));
                int midY = Math.round(oy + size / 2.0F);
                int midX = Math.round(ox + size / 2.0F);
                // A pure bitmap pixel, sampled well inside the circle: the
                // reference the stroke's AA is measured against.
                float faceLum = luminance(bmp.getColor(midX, midY));
                // Each scan starts at the bitmap's OUTERMOST pixel (the rounded
                // image spans [ox, ox+size)), so all four start from the same
                // kind of point.
                return new float[]{
                        strokeEdge(bmp, Math.round(ox), midY, -1, 0, faceLum),
                        strokeEdge(bmp, Math.round(ox + size) - 1, midY, +1, 0, faceLum),
                        strokeEdge(bmp, midX, Math.round(oy), 0, -1, faceLum),
                        strokeEdge(bmp, midX, Math.round(oy + size) - 1, 0, +1, faceLum)};
            }
        } finally {
            UiPixelSnap.beginFrame(1.0F);
        }
    }

    /**
     * Distance, in device px, from the bitmap's outermost pixel to the half-way
     * point of the rim stroke's leading edge, along one cardinal direction.
     *
     * <p>Sub-pixel on purpose. The defect was a half-device-pixel centre nudge,
     * which at a whole-pixel scan granularity moves the "first stroke pixel"
     * between two adjacent indices on each side — the same 0/2 split the field
     * probe reported. Interpolating the 50% crossing of the stroke's AA ramp
     * turns that into a continuous 0.5px difference, so the assertion can be
     * tighter than one pixel and still be about geometry rather than about
     * which pixel an integer happened to land on.</p>
     */
    private static float strokeEdge(Bitmap bmp, int x0, int y0, int dx, int dy, float faceLum) {
        float[] lum = new float[6];
        for (int i = 0; i < lum.length; i++) {
            int x = x0 + dx * i;
            int y = y0 + dy * i;
            if (x < 0 || y < 0 || x >= bmp.getWidth() || y >= bmp.getHeight()) {
                throw new AssertionError("the scan left the surface along " + dx + "," + dy);
            }
            lum[i] = luminance(bmp.getColor(x, y));
        }
        // The stroke's own level: the darkest of the first three samples outside
        // the bitmap, which is the stroke body (the panel further out is darker
        // still and must not define the ramp).
        float strokeLum = Math.min(lum[1], lum[2]);
        float mid = (faceLum + strokeLum) / 2.0F;
        for (int i = 1; i < lum.length; i++) {
            if (lum[i] <= mid) {
                float span = lum[i - 1] - lum[i];
                return (i - 1) + (span == 0.0F ? 0.5F : (lum[i - 1] - mid) / span);
            }
        }
        throw new AssertionError("the rim stroke was never reached along " + dx + "," + dy);
    }

    private static float luminance(int color) {
        return ((color >> 16) & 0xFF) + ((color >> 8) & 0xFF) + (color & 0xFF);
    }

    private static boolean near(int a, int b, int tol) {
        return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) <= tol
                && Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)) <= tol
                && Math.abs((a & 0xFF) - (b & 0xFF)) <= tol;
    }

    @Test
    void everyAvatarSiteUsesTheOneRingGeometry() throws IOException {
        for (String site : SITES) {
            Path file = root().resolve(site);
            assertTrue(Files.isRegularFile(file), "missing avatar site: " + site);
            String src = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue(src.contains("com.atom.chat.render.AvatarRing.rimRadius("),
                    site + ": the rim radius must come from AvatarRing");
            assertTrue(src.contains("com.atom.chat.render.AvatarRing.RIM_STROKE_PX"),
                    site + ": the rim stroke must come from AvatarRing");
            assertFalse(src.contains("UiTokens.PROFILE_AVATAR_RING"),
                    site + ": the old s(2) separator pad must not come back");
            // Every drawRing in these files is either an avatar rim (AvatarRing,
            // UiTokens.rim) or an unrelated swatch/plus ring (UiTokens.hairline).
            // A hand-derived avatar radius has neither — and an avatar rim must
            // not be centre-nudged: the nudge moves the ring off the bitmap and
            // opens the one-side seam this whole file exists to prevent.
            int from = 0;
            while (true) {
                int at = src.indexOf("drawRing(", from);
                if (at < 0) {
                    break;
                }
                String call = src.substring(at, Math.min(src.length(), at + 320));
                boolean avatarRim = call.contains("AvatarRing.rimRadius(");
                assertTrue(avatarRim || call.contains("UiTokens.hairline()"),
                        site + ": a hand-derived ring radius came back: "
                                + call.lines().findFirst().orElse(""));
                if (avatarRim) {
                    String statement = call.substring(0, call.indexOf(");") + 2);
                    assertFalse(statement.contains("halfDevicePx"),
                            site + ": an avatar rim must stay concentric with the bitmap, "
                                    + "not centre-nudged by halfDevicePx: "
                                    + statement.replace('\n', ' '));
                }
                from = at + "drawRing(".length();
            }
        }
    }
}
