package com.atom.chat.probe;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.PanelOpenMotion;
import com.atom.chat.ui.UiCards;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Hidden-window native probe; runs separately from headless unit tests. */
public final class NotificationBackdropProbe {
    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, 8);
        GLFW.glfwWindowHint(GLFW.GLFW_ALPHA_BITS, 8);
        long window = GLFW.glfwCreateWindow(960, 540, "AtomChat backdrop probe", 0, 0);
        if (window == 0) throw new AssertionError("OpenGL window creation failed");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        var config = new AtomChatConfig();
        config.cardColor = 0xFFFFFFFF; config.cardTint = .5F;
        var field = AtomChatConfig.class.getDeclaredField("instance"); field.setAccessible(true); field.set(null, config);
        try (var context = DirectContext.makeGL();
             var target = BackendRenderTarget.makeGL(960, 540, 0, 8, 0, GL11C.GL_RGBA8);
             var surface = Surface.wrapBackendRenderTarget(context, target, SurfaceOrigin.BOTTOM_LEFT,
                     ColorType.RGBA_8888, ColorSpace.getSRGB());
             var paint = new Paint(); var bitmap = new Bitmap()) {
            if (surface == null) throw new AssertionError("GPU surface unavailable");
            var canvas = surface.getCanvas();
            long[] costs = new long[60];
            for (int frame = 0; frame < 120; frame++) {
                context.resetAll();
                long started = System.nanoTime();
                int saves = canvas.getSaveCount();
                canvas.clear(0);
                float progress = frame < 60 ? (frame % 30 + 1) / 30F : 1;
                canvas.saveLayerAlpha(null, frame < 60 ? Math.round(progress * 255) : 255);
                float scale = PanelOpenMotion.openScale(progress, AtomChatConfig.PageNavStyle.ZOOM);
                canvas.translate(480, 270); canvas.scale(scale, scale); canvas.translate(-480, -270);
                for (int x = 0; x < 960; x += 4) {
                    paint.setColor(((x / 4 + frame) % 2 == 0) ? 0xFF000000 : 0xFFFFFFFF);
                    canvas.drawRect(Rect.makeXYWH(x, 0, 4, 540), paint);
                }
                for (int card = 0; card < 3; card++) {
                    float y = 40 + card * 120;
                    UiCards.drawNotificationBackdrop(canvas, 100, y, 760, 90, 20, 1);
                    UiCards.drawCard(canvas, 100, y, 760, 90, 20, 0,
                            UiTokens.CARD_SHADOW, UiTokens.NOTIFICATION_BLUR, UiTokens.notificationFill());
                }
                canvas.restore();
                if (saves != canvas.getSaveCount()) throw new AssertionError("Unbalanced canvas stack");
                context.flushAndSubmit(surface);
                GL11C.glFinish();
                if (frame >= 60) costs[frame - 60] = System.nanoTime() - started;
                if (frame >= 60) {
                    bitmap.allocN32Pixels(960, 540);
                    if (!surface.readPixels(bitmap, 0, 0)) throw new AssertionError("GPU readback failed");
                    for (int y : new int[]{85, 205, 325}) {
                        int red = (bitmap.getColor(650, y) >> 16) & 255;
                        if (red <= 165 || red >= 220) throw new AssertionError("Backdrop did not blur: " + red);
                    }
                }
                int error = GL11C.glGetError();
                if (error != 0) throw new AssertionError("GL error " + error);
            }
            if (args.length > 0) {
                Path output = Path.of(args[0]); Files.createDirectories(output.toAbsolutePath().getParent());
                try (var image = Image.makeFromBitmap(bitmap); var data = image.encodeToData()) { Files.write(output, data.getBytes()); }
            }
            Arrays.sort(costs);
            System.out.printf("GPU=%s; 120 frames, 3 backdrops/frame, nested panel layers, reset/readback/GL checks passed; " +
                    "scene submit+finish ms p50=%.3f p95=%.3f max=%.3f%n", GL11C.glGetString(GL11C.GL_RENDERER),
                    costs[30] / 1e6, costs[57] / 1e6, costs[59] / 1e6);
        } finally {
            GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate();
        }
    }
}
