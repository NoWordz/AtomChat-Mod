package com.atom.chat.probe;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.render.UiPixelSnap;
import com.atom.chat.ui.PanelBackground;
import com.atom.chat.ui.PickerModalPaint;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Replays the modal, panel bezel and picture recording at the reported pixel phase. */
public final class ModalBackdropEdgeProbe {
    private static final int W = 1029, H = 1599;
    private static final int PANEL_BASE = 0xFFB6A6AD;
    private static int failures;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        AtomChatConfig config = new AtomChatConfig();
        var singleton = AtomChatConfig.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        singleton.set(null, config);
        try (var raster = Surface.makeRasterN32Premul(W, H)) {
            check(raster, null, output, "raster", config);
        }
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW initialization failed");
        long window = 0;
        try {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, 8);
            GLFW.glfwWindowHint(GLFW.GLFW_ALPHA_BITS, 8);
            window = GLFW.glfwCreateWindow(W, H, "AtomChat isolated modal probe", 0, 0);
            if (window == 0) throw new AssertionError("OpenGL window creation failed");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("GL_RENDERER=" + GL11C.glGetString(GL11C.GL_RENDERER));
            try (var context = DirectContext.makeGL();
                 var backend = BackendRenderTarget.makeGL(W, H, 0, 8, 0, GL11C.GL_RGBA8);
                 var surface = Surface.wrapBackendRenderTarget(context, backend, SurfaceOrigin.BOTTOM_LEFT,
                         ColorType.RGBA_8888, ColorSpace.getSRGB())) {
                if (surface == null) throw new AssertionError("OpenGL surface unavailable");
                check(surface, context, output, "opengl", config);
                if (GL11C.glGetError() != 0) throw new AssertionError("OpenGL error");
            }
        } finally {
            if (window != 0) GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
        if (failures > 0) throw new AssertionError(failures + " backdrop edge checks failed");
    }

    private static void check(Surface surface, DirectContext context, Path output, String backend,
                              AtomChatConfig config) throws Exception {
        var canvas = surface.getCanvas();
        for (boolean docked : new boolean[]{false, true}) {
            config.halfScreenMode = docked;
            for (float scale : new float[]{H / 1080F, 1F, 1.25F, 2F}) {
                UiPixelSnap.beginFrame(scale);
                float rawX = docked ? 0 : UiTokens.PANEL_ANCHOR_X * scale;
                float rawEnd = rawX + (scale > 1.5F ? 440F : 600F) * scale;
                var detail = new UiLayout.Rect(rawX / scale, 24F / scale,
                        (rawEnd - rawX) / scale, 1552F / scale);
                var shell = new UiLayout.Rect(Math.round(rawX) / scale, 24F / scale,
                        (Math.round(rawEnd) - Math.round(rawX)) / scale, 1552F / scale);
                draw(canvas, scale, shell, null);
                writeAndRead(surface, context, null, backend, "no-modal", scale, shell);
                draw(canvas, scale, shell, shell);
                writeAndRead(surface, context, output, backend, docked ? "docked-shell" : "shell", scale, shell);
                draw(canvas, scale, shell, detail);
                writeAndRead(surface, context, output, backend, docked ? "docked-detail" : "detail", scale, shell);
            }
        }
    }

    private static void draw(Canvas canvas, float scale, UiLayout.Rect shell, UiLayout.Rect modal) {
        canvas.clear(0);
        int outer = canvas.save();
        try {
            canvas.scale(scale, scale);
            PanelBackground.drawTint(canvas, shell, PANEL_BASE);
            if (modal != null) {
                Rect cull = Rect.makeXYWH(shell.x(), shell.y(), shell.w(), shell.h());
                try (var recorder = new PictureRecorder()) {
                    var recording = recorder.beginRecording(cull);
                    recording.save();
                    PanelBackground.clipInterior(recording, modal);
                    PickerModalPaint.backdrop(recording, modal, 1F);
                    recording.restore();
                    try (var picture = recorder.finishRecordingAsPicture()) {
                        picture.playback(canvas);
                    }
                }
            }
            PanelBackground.drawBezel(canvas, shell);
        } finally {
            canvas.restoreToCount(outer);
        }
    }

    private static void writeAndRead(Surface surface, DirectContext context, Path output, String backend,
                                     String scene, float scale, UiLayout.Rect shell) throws Exception {
        if (context != null) {
            context.flushAndSubmit(surface);
            GL11C.glFinish();
        }
        try (var bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(W, H);
            if (!surface.readPixels(bitmap, 0, 0)) throw new AssertionError("readPixels failed");
            int left = Math.round(shell.x() * scale);
            int right = Math.round(shell.right() * scale);
            System.out.printf(Locale.ROOT, "%s %-13s %.6f outline=%.3f left=%d [%s] right=%d [%s]%n",
                    backend, scene, scale, PanelBackground.outlineInset() * scale, left, row(bitmap, left, left + 12),
                    right, row(bitmap, right - 12, right));
            if (output != null && scene.contains("detail")) checkEdges(bitmap, backend, scene, scale, left, right);
            if (output != null && scale == H / 1080F && !scene.startsWith("docked")) {
                try (var img = Image.makeRasterFromBitmap(bitmap);
                     var data = img.encodeToData(EncodedImageFormat.PNG)) {
                    Files.write(output.resolve(backend + "-" + scene + ".png"), data.getBytes());
                }
            }
        }
    }

    private static void checkEdges(Bitmap bitmap, String backend, String scene, float scale, int left, int right) {
        int inset = (int) (PanelBackground.outlineInset() * scale);
        int darkestFace = bitmap.getColor((left + right) / 2, 500) >>> 16 & 255;
        int nearLeft = bitmap.getColor(left + inset, 500) >>> 16 & 255;
        int nearRight = bitmap.getColor(right - inset - 1, 500) >>> 16 & 255;
        if (nearLeft > darkestFace || nearRight > darkestFace) {
            failures++;
            System.out.printf(Locale.ROOT, "FAIL %s %s %.6f: edge red %d/%d, content %d%n",
                    backend, scene, scale, nearLeft, nearRight, darkestFace);
        }
        if ((left > 0 && bitmap.getColor(left - 1, 500) != 0) || bitmap.getColor(right, 500) != 0) {
            failures++;
            System.out.printf(Locale.ROOT, "FAIL %s %s %.6f: backdrop escaped outer contour%n", backend, scene, scale);
        }
        if (inset > 0 && (bitmap.getColor(left, 24) != 0 || bitmap.getColor(right - 1, 24) != 0)) {
            throw new AssertionError("Rounded corner is no longer transparent");
        }
        if (inset > 0 && bitmap.getColor(left + inset - 1, 500) != bitmap.getColor(right - inset, 500)) {
            throw new AssertionError("Opaque bezel is asymmetric");
        }
    }

    private static String row(Bitmap bitmap, int start, int end) {
        var result = new java.util.ArrayList<Integer>();
        for (int x = Math.max(0, start); x < Math.min(W, end); x++) {
            result.add((bitmap.getColor(x, 500) >>> 16) & 255);
        }
        return result.toString();
    }
}
