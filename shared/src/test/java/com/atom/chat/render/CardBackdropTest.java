package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CardBackdropTest {
    private static final UiLayout.Rect PANEL = new UiLayout.Rect(0, 0, 180, 120);
    private static final Rect BOUNDS = Rect.makeWH(180, 120);

    @Test void cardsCannotBringWorldPixelsFromOutsideThePanelIntoItsEdges() {
        var cfg = new AtomChatConfig();
        cfg.panelOutline = false;
        cfg.cardBlurStrength = 3F;
        cfg.panelOpacity = 0F;
        try (var source = Surface.makeRasterN32Premul(180, 120);
             var result = Surface.makeRasterN32Premul(180, 120);
             var black = new Paint().setColor(0xFF000000)) {
            source.getCanvas().clear(0xFFFFFFFF);
            source.getCanvas().drawRect(Rect.makeLTRB(40, 0, 140, 120), black);
            Canvas canvas = result.getCanvas();
            canvas.clear(0xFFFFFFFF);
            try (var input = source.makeImageSnapshot();
                 var frame = CardBackdrop.begin(result, canvas, new UiLayout.Rect(40, 0, 100, 120), input, null, cfg)) {
                CardBackdrop.draw(canvas, 42, 20, 96, 80, 0);
            }
            assertEquals(0, value(pixels(result), 44, 60));
            assertEquals(0, value(pixels(result), 134, 60));
        }
    }

    @Test void reusesEqualOrStrongerPaintedPanelWithoutChangingTheSavedCardStrength() {
        var config = new AtomChatConfig();
        assertEquals(2F, config.effectiveCardBlurStrength());
        config.blurStrength = 2F;
        assertFalse(config.needsSeparateCardBlur(true));
        config.cardBlurStrength = .8F;
        assertFalse(config.needsSeparateCardBlur(true));
        assertTrue(config.needsSeparateCardBlur(false));
        assertEquals(.8F, config.cardBlurStrength);
        config.cardBlurStrength = 3F;
        assertTrue(config.needsSeparateCardBlur(true));
        config.cardBlurStrength = 0F;
        assertFalse(config.needsSeparateCardBlur(false));
    }

    @Test void zeroSkipsAndThreeHundredBlursOnlyInsideRoundedCards() {
        var cfg = new AtomChatConfig();
        cfg.cardBlurStrength = 0F;
        cfg.cardTint = 0F;
        cfg.panelOpacity = 0F;
        try (Surface source = Surface.makeRasterN32Premul(180, 120);
             Surface target = Surface.makeRasterN32Premul(180, 120);
             Paint paint = new Paint().setColor(0xFFFFFFFF)) {
            Canvas canvas = target.getCanvas();
            source.getCanvas().clear(0xFF000000);
            source.getCanvas().drawRect(Rect.makeLTRB(90, 0, 180, 120), paint);
            try (Image world = source.makeImageSnapshot()) {
                canvas.drawImage(world, 0, 0);
                byte[] before = pixels(target);
                try (var frame = CardBackdrop.begin(target, canvas, PANEL, world, null, cfg)) {
                    assertFalse(CardBackdrop.active());
                    CardBackdrop.draw(canvas, 60, 30, 60, 60, 12);
                }
                assertArrayEquals(before, pixels(target));

                cfg.cardBlurStrength = 3F;
                try (var frame = CardBackdrop.begin(target, canvas, PANEL, world, null, cfg)) {
                    assertTrue(CardBackdrop.active());
                    CardBackdrop.draw(canvas, 60, 30, 60, 60, 12);
                }
                byte[] result = pixels(target);
                assertEquals(value(before, 60, 30), value(result, 60, 30), "rounded corner");
                assertEquals(value(before, 88, 20), value(result, 88, 20), "outside card");
                assertTrue(value(result, 88, 60) > 40, "softened black side");
                assertTrue(value(result, 92, 60) < 215, "softened white side");
                assertTrue(value(result, 70, 60) > 40, "300% reaches the same physical distance as panel Kawase");
            }
        }
    }

    @Test void movingMaskSamplesTheSameScreenPositionAndKeepsLocalClip() {
        var cfg = new AtomChatConfig();
        cfg.cardBlurStrength = 3F;
        cfg.panelOpacity = 1F;
        try (Surface wallpaper = Surface.makeRasterN32Premul(180, 120);
             Surface target = Surface.makeRasterN32Premul(180, 120);
             Paint paint = new Paint().setColor(0xFFFFFFFF);
             PictureRecorder recorder = new PictureRecorder()) {
            wallpaper.getCanvas().clear(0xFF000000);
            wallpaper.getCanvas().drawRect(Rect.makeLTRB(90, 0, 180, 120), paint);
            try (Image image = wallpaper.makeImageSnapshot()) {
                Canvas canvas = target.getCanvas();
                canvas.clear(0xFF000000);
                try (var frame = CardBackdrop.begin(target, canvas, PANEL, null, image, cfg)) {
                    CardBackdrop.draw(canvas, 90, 30, 60, 50, 8);
                }
                int anchored = value(pixels(target), 120, 50);
                canvas.clear(0xFF000000);
                try (var frame = CardBackdrop.begin(target, canvas, PANEL, null, image, cfg)) {
                    Canvas body = recorder.beginRecording(BOUNDS);
                    body.clipRect(Rect.makeLTRB(0, 30, 180, 80));
                    try (Picture masks = CardBackdrop.recordMasks(BOUNDS,
                            () -> CardBackdrop.draw(body, 30, 20, 60, 60, 8))) {
                        assertNotNull(masks);
                        CardBackdrop.applyMasks(canvas, BOUNDS, layer -> {
                            layer.translate(60, 0);
                            masks.playback(layer);
                        });
                    }
                }
                byte[] result = pixels(target);
                assertTrue(anchored > 128, "the fixed mask samples the light side of the wallpaper");
                assertEquals(anchored, value(result, 120, 50), "a moving mask samples the same screen pixel as the fixed mask");
                assertEquals(0, value(result, 120, 25), "the scroll clip travels with the card");
                assertEquals(0, value(result, 155, 50), "the blur is confined to the mask");
            }
        }
    }

    @Test void gpuBackdropSamplesTheWorldBeforeUi() {
        org.junit.jupiter.api.Assumptions.assumeTrue("1".equals(System.getenv("ATOMCHAT_NAV_BENCH")));
        assertTrue(org.lwjgl.glfw.GLFW.glfwInit());
        org.lwjgl.glfw.GLFW.glfwDefaultWindowHints();
        org.lwjgl.glfw.GLFW.glfwWindowHint(org.lwjgl.glfw.GLFW.GLFW_VISIBLE, 0);
        org.lwjgl.glfw.GLFW.glfwWindowHint(org.lwjgl.glfw.GLFW.GLFW_STENCIL_BITS, 8);
        long window = org.lwjgl.glfw.GLFW.glfwCreateWindow(180, 120, "AtomChat card backdrop", 0, 0);
        assertNotEquals(0, window);
        try {
            org.lwjgl.glfw.GLFW.glfwMakeContextCurrent(window);
            org.lwjgl.opengl.GL.createCapabilities();
            try (DirectContext context = DirectContext.makeGL();
                 BackendRenderTarget target = BackendRenderTarget.makeGL(180, 120, 0, 8, 0, 32856);
                 Surface surface = Surface.wrapBackendRenderTarget(context, target, SurfaceOrigin.BOTTOM_LEFT,
                         ColorType.RGBA_8888, ColorSpace.getSRGB());
                 Paint white = new Paint().setColor(0xFFFFFFFF)) {
                var config = new AtomChatConfig();
                config.cardBlurStrength = 3;
                config.panelOpacity = 0;
                config.panelOutline = false;
                Canvas canvas = surface.getCanvas();
                org.lwjgl.opengl.GL11C.glClearColor(0F, 0F, 0F, .25F);
                org.lwjgl.opengl.GL11C.glClear(org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT);
                org.lwjgl.opengl.GL11C.glEnable(org.lwjgl.opengl.GL11C.GL_SCISSOR_TEST);
                org.lwjgl.opengl.GL11C.glScissor(90, 0, 90, 120);
                org.lwjgl.opengl.GL11C.glClearColor(1F, 1F, 1F, .25F);
                org.lwjgl.opengl.GL11C.glClear(org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT);
                org.lwjgl.opengl.GL11C.glDisable(org.lwjgl.opengl.GL11C.GL_SCISSOR_TEST);
                context.resetAll();
                byte[] before = pixels(surface);
                try (Image world = surface.makeImageSnapshot();
                     CardBackdrop frame = CardBackdrop.begin(surface, canvas, PANEL, world, null, config)) {
                    assertTrue(CardBackdrop.active());
                    canvas.drawRect(Rect.makeLTRB(60, 10, 120, 18), white);
                    CardBackdrop.draw(canvas, 60, 30, 60, 40, 12);
                    CardBackdrop.applyMasks(canvas, BOUNDS, mask -> mask.drawRect(Rect.makeLTRB(60, 70, 120, 106), white));
                }
                try (Image world = surface.makeImageSnapshot();
                     Paint fade = new Paint().setAlphaf(.5F)) {
                    assertTrue(CardBackdrop.preserveFramebufferAlpha(fade));
                    int save = canvas.saveLayer(BOUNDS, fade);
                    try (var offscreen = CardBackdrop.begin(surface, canvas, PANEL, world, null, config, false)) {
                        CardBackdrop.draw(canvas, 50, 112, 80, 8, 0);
                    } finally {
                        canvas.restoreToCount(save);
                    }
                }
                context.flushAndSubmit(surface, true);
                byte[] result = pixels(surface);
                assertTrue(value(result, 88, 50) > 40);
                assertTrue(value(result, 92, 50) < 215);
                assertTrue(value(result, 88, 90) > 40);
                assertTrue(value(result, 92, 90) < 215);
                assertEquals(64, before[(50 * 180 + 88) * 4 + 3] & 255);
                assertTrue(value(result, 88, 115) > 20, "a faded card still visibly blurs");
                for (int y = 24; y < 120; y++)
                    for (int x = 0; x < 180; x++) {
                        int alpha = (y * 180 + x) * 4 + 3;
                        assertEquals(before[alpha], result[alpha], "card painting preserves framebuffer alpha");
                    }
                assertEquals(255, value(result, 72, 14), "ui outside the card is unchanged");
            }
        } finally {
            org.lwjgl.glfw.GLFW.glfwMakeContextCurrent(0);
            org.lwjgl.glfw.GLFW.glfwDestroyWindow(window);
            org.lwjgl.glfw.GLFW.glfwTerminate();
        }
    }

    private static byte[] pixels(Surface surface) {
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(180, 120);
            assertTrue(surface.readPixels(bitmap, 0, 0));
            return bitmap.readPixels();
        }
    }

    private static int value(byte[] pixels, int x, int y) {
        return pixels[(y * 180 + x) * 4] & 255;
    }
}
