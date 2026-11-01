package com.atom.chat.notification;



import com.atom.lib.design.atomchat.UiCards;
import com.atom.lib.design.atomchat.UiTokens;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationRenderWiringTest {
    @Test void readAndDrawFramebufferBindingsAreCapturedAndRestoredIndependently() throws Exception {
        String s = Files.readString(root().resolve("shared/src/main/java/com/atom/chat/render/GlStateUtil.java"));
        assertTrue(s.contains("GL_READ_FRAMEBUFFER_BINDING"));
        assertTrue(s.contains("GL_DRAW_FRAMEBUFFER_BINDING"));
        assertTrue(s.contains("_glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFramebuffer)"));
        assertTrue(s.contains("_glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer)"));
    }
    private Path root() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("platforms"))) p = p.getParent();
        return java.util.Objects.requireNonNull(p);
    }
    @Test void eachLoaderHooksTheDedicatedOverlayAndClearsPrivatePreviewsOnConnectionChanges() throws Exception {
        for (String v : List.of("1.20.1-forge","1.21.1-neoforge","1.21.1-fabric")) {
            String s = Files.readString(root().resolve("platforms/"+v+"/src/main/java/com/atom/chat/AtomChatClient.java"));
            assertTrue(s.contains("NotificationHudOverlay.render("),v);
            assertEquals(2, s.split("NotificationBanner.INSTANCE.reset\\(\\)",-1).length - 1,v);
        }
    }
    @Test void gpuFacadeDelegatesSingleOwnershipAndKeepsHostAndSkijaPhasesSeparate() throws Exception {
        for (String path : List.of("layers/mapping/official/src/main/java/com/atom/chat/render/SkiaGraphics.java",
                "platforms/1.21.1-fabric/src/main/java/com/atom/chat/render/SkiaGraphics.java")) {
            String s = Files.readString(root().resolve(path));
            s = s.substring(s.indexOf("public void draw(Runnable preUi, float density,"));
            assertTrue(s.contains("bridge.vanillaPass(preUi)"),path);
            assertTrue(s.indexOf("bridge.vanillaPass(preUi)") < s.indexOf("bridge.drawFrame"),path);
            assertTrue(s.contains("canvas.restoreToCount(base)"),path);
            assertFalse(s.contains("DirectContext.makeGL()"),"AC must not create a second Skija context: "+path);
            assertFalse(s.contains("Surface.wrapBackendRenderTarget"),"AC must not own the framebuffer surface: "+path);
        }
    }

    @Test void panelBlurRefreshesEveryFrameAndBannersUseCardAndGlobalMotionPrimitives() throws Exception {
        Path root = root();
        for (String target : List.of("1.20.1-forge", "1.21.1-neoforge", "1.21.1-fabric")) {
            Path renderer = root.resolve("platforms/" + target + "/src/main/java/com/atom/chat/render/PanelBlurRenderer.java");
            String blur = Files.readString(renderer);
            assertFalse(blur.contains("BlurMotionGate"), target);
            assertTrue(blur.contains("boolean full = true;"), target);
        }
        for (String path : List.of("layers/mapping/official/src/main/java/com/atom/chat/notification/NotificationBanner.java",
                "platforms/1.21.1-fabric/src/main/java/com/atom/chat/notification/NotificationBanner.java")) {
            String banner = Files.readString(root.resolve(path));
            assertFalse(banner.contains("NotificationBackdropBlur"), path);
            assertTrue(banner.contains("UiTokens.notificationFill()"), path);
            assertTrue(banner.contains("UiCards.drawCard("), path);
            assertTrue(banner.contains("UiCards.drawNotificationBackdrop("), path);
            assertTrue(banner.contains("UiTokens.onAccent(config.accentColor)"), path);
            assertTrue(banner.contains("PanelOpenMotion.openScale(progress, style)"), path);
            assertTrue(banner.contains("PanelOpenMotion.openSlidePx(progress, style)"), path);
            assertFalse(banner.contains("easeOutBack"), path);
        }
    }

    @Test
    void hudBackdropIsRawGlPrePassNotSkiaBackdropFilter() throws Exception {
        // Both mapping twins must carry the wiring: Fabric keeps its own
        // Yarn-named copy of NotificationHudOverlay and does not mount the
        // official layer. A Skia backdrop filter in the HUD reads the
        // framebuffer as premul; shader packs leave undefined alpha there, and
        // every framebuffer-alpha write (whole-viewport and region-scoped)
        // blackened the display. Only the panel-blur-style raw GL pre-pass —
        // which never reads or writes framebuffer alpha — is allowed.
        assertFalse(Files.exists(root().resolve("shared/src/main/java/com/atom/chat/render/HudBackdropAlpha.java")),
                "the framebuffer-alpha normalization must stay deleted");
        for (String[] twin : new String[][]{
                {"layers/mapping/official/src/main/java/com/atom/chat/notification/NotificationHudOverlay.java", "gui.flush()"},
                {"platforms/1.21.1-fabric/src/main/java/com/atom/chat/notification/NotificationHudOverlay.java", "gui.draw()"}}) {
            String s = Files.readString(root().resolve(twin[0]));
            assertFalse(s.contains("drawNotificationBackdrop"), twin[0]);
            assertFalse(s.contains("HudBackdropAlpha"), twin[0]);
            assertTrue(s.contains("PanelBlurRenderer.render(new Matrix4f()"), twin[0]);
            assertTrue(s.contains("NotificationBanner.hudPlacement(width, height).rect()"),
                    "the blur rect must reuse the banner's own placement: " + twin[0]);
            assertTrue(s.contains("NotificationBanner.hudBackdropPreDrawn()"), twin[0]);
            int flush = s.indexOf(twin[1]);
            int blur = s.indexOf("PanelBlurRenderer.render(");
            int draw = s.indexOf("GRAPHICS.draw(");
            int paint = s.indexOf("renderInHud(canvas, width, height)");
            assertTrue(flush >= 0 && flush < draw && draw < blur,
                    "the raw-GL backdrop must run inside the pre-pass, after the vanilla flush: " + twin[0]);
            assertTrue(blur < s.indexOf("ItemIconRenderer.prepare") && s.indexOf("ItemIconRenderer.prepare") < paint,
                    "the blur must precede the Skia paint lambda in the pre-pass: " + twin[0]);
        }
        // Both banner twins must skip the Skia backdrop when the pre-pass drew.
        for (String path : List.of("layers/mapping/official/src/main/java/com/atom/chat/notification/NotificationBanner.java",
                "platforms/1.21.1-fabric/src/main/java/com/atom/chat/notification/NotificationBanner.java")) {
            String s = Files.readString(root().resolve(path));
            assertTrue(s.contains("boolean backdropPreDrawn"), path);
            assertTrue(s.contains("!backdropPreDrawn && (fill >>> 24) < 255"), path);
            assertTrue(s.contains("public static void hudBackdropPreDrawn()"), path);
            assertTrue(s.contains("public static float hudBackdropAlpha()"), path);
        }
    }

    @Test
    void gnetumCompatPinsTheHudLayerToItsUncachedPathInBothTwins() throws Exception {
        // Gnetum re-routes HUD layers into its own multi-pass cache; inside a
        // cached pass the raw-GL frosted backdrop lands in Gnetum's cache
        // framebuffer while the Skia card goes to the main framebuffer (the
        // AtomLib bridge pins its backend target), so the banner flickers at
        // the pass cadence. Both overlay twins must pin the layer to Gnetum's
        // uncached path; the compat itself is pure reflection and must not
        // reference Gnetum classes at compile time.
        for (String path : List.of(
                "layers/mapping/official/src/main/java/com/atom/chat/notification/GnetumHudCompat.java",
                "platforms/1.21.1-fabric/src/main/java/com/atom/chat/notification/GnetumHudCompat.java")) {
            String s = Files.readString(root().resolve(path));
            assertTrue(s.contains("Class.forName(\"me.decce.gnetum.Gnetum\")"), path);
            assertTrue(s.contains("Platform.isModLoaded(\"gnetum\")"), path);
            assertTrue(s.contains("\"atomchat:notifications\""), path);
            assertFalse(s.contains("import me.decce"), "no compile-time Gnetum dependency: " + path);
        }
        for (String path : List.of(
                "layers/mapping/official/src/main/java/com/atom/chat/notification/NotificationHudOverlay.java",
                "platforms/1.21.1-fabric/src/main/java/com/atom/chat/notification/NotificationHudOverlay.java")) {
            String s = Files.readString(root().resolve(path));
            assertTrue(s.contains("GnetumHudCompat.keepOutOfHudCache()"), path);
        }
        // The pinned id must be the id NeoForge actually registers (Fabric's
        // HudRenderCallback carries no layer id).
        String neoforgeClient = Files.readString(root().resolve("platforms/1.21.1-neoforge/src/main/java/com/atom/chat/AtomChatClient.java"));
        assertTrue(neoforgeClient.contains("\"atomchat\"") && neoforgeClient.contains("\"notifications\""), "1.21.1-neoforge");
    }

    @Test
    void hudStackPlacementIsSharedBetweenOverlayAndBanner() throws Exception {
        for (String path : List.of("layers/mapping/official/src/main/java/com/atom/chat/notification/NotificationBanner.java",
                "platforms/1.21.1-fabric/src/main/java/com/atom/chat/notification/NotificationBanner.java")) {
            String s = Files.readString(root().resolve(path));
            assertTrue(s.contains("public static NotificationHudLayout.Placement hudPlacement(float width, float height)"), path);
            assertTrue(s.contains("var placement = hudPlacement(width, height);"), path);
            assertEquals(1, s.split("NotificationHudLayout\\.scaled\\(", -1).length - 1,
                    "the scaled() expression must exist exactly once (in hudPlacement): " + path);
        }
    }
}
