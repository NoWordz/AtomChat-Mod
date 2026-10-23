package com.atom.chat.notification;

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
    @Test void gpuBridgeCapturesBeforeSurfaceCreationAndRestoresEvenWhenPaintingFails() throws Exception {
        for (String path : List.of("layers/mapping/official/src/main/java/com/atom/chat/render/SkiaGraphics.java",
                "platforms/1.21.1-fabric/src/main/java/com/atom/chat/render/SkiaGraphics.java")) {
            String s = Files.readString(root().resolve(path));
            s = s.substring(s.indexOf("public void draw(Runnable preUi, float density,"));
            assertTrue(s.indexOf("GlStateUtil.save()") < s.indexOf("createSurface()"),path);
            assertTrue(s.contains("finally {\n            GlStateUtil.restore();"),path);
            assertFalse(s.contains("RenderSystem.disableBlend()"),"never overwrite the restored blend enable: "+path);
            assertTrue(s.contains("canvas.restoreToCount(saveBase)"),path);
        }
    }

    @Test void panelBlurRefreshesEveryFrameAndBannerKeepsOnlyTheIconContrastFix() throws Exception {
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
            assertFalse(banner.contains("UiTokens.cardFill("), path);
            assertTrue(banner.contains("UiTokens.floatSurfaceFill()"), path);
            assertTrue(banner.contains("UiTokens.onAccent(config.accentColor)"), path);
            assertFalse(banner.contains("AtomChatConfig.PageNavStyle.ZOOM"), path);
        }
    }
}
