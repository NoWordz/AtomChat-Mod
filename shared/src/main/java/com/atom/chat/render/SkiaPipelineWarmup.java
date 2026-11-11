package com.atom.chat.render;

import com.atom.chat.font.FontManager;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Picture;
import io.github.humbleui.skija.PictureRecorder;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Moves the cold Skia pipeline work off the first panel frame.
 *
 * <p><b>What the 86 ms first frame actually was.</b> The 2026-11-07 JFR recording
 * showed a multi-frame decay ({@code 86.4 → 7.6 → 5.4… → 2.2 ms}) with DRAW 55.3,
 * BRIDGE_EXIT 23.3 and DOTS 16.4 on frame 1: pipeline/program compilation, glyph
 * atlas uploads and display-list setup, not a single slow Java method. The old
 * GL warmup only created the bridge surface and compiled the raw-GL blur shaders,
 * so it never generated a single GrOp for the Skia path.
 *
 * <p><b>Why a real, visible, offscreen draw.</b> This follows Flutter's
 * {@code ShaderWarmUp}: record a representative picture, rasterize it on a GPU
 * surface owned by the <em>same</em> {@link DirectContext}, then
 * {@code flushAndSubmit(surface, true)} so the CPU waits for compilation. A
 * 1x1 fully transparent draw would be useless here — {@code alpha == 0} hits
 * {@code SkPaint::nothingToDraw()} and a 1x1 target is {@code quickReject}ed,
 * so no GrOp (and therefore no program) is ever created.
 *
 * <p><b>Same program, not just "a" program.</b> A pipeline cache key depends on
 * the paint combination and the surface format. That is why the offscreen
 * surface uses the main framebuffer's exact format (N32 premultiplied / sRGB /
 * BOTTOM_LEFT / no MSAA) and why the draw calls are the very primitives the
 * first frame uses — {@link DotTexture} (whose PictureRecorder → drawPicture
 * path is 33.6% of native render samples), {@link ChromeMaterial} and
 * {@link SkiaFontRenderer} with the real bundled font. Hand-written "similar"
 * shapes would compile a different set of programs and warm nothing.
 *
 * <p><b>Safety.</b> Once per process, gated by
 * {@code -Datomchat.skia.prewarm=false}, and every failure is contained: a
 * failed warmup only means the first panel frame stays as cold as before.
 * Callers borrow the single bridge-owned context via
 * {@code MinecraftRenderBridge.withContext}, which already wraps the callback
 * in {@code GlStateScope.capture()}; binding this offscreen FBO therefore
 * restores all GL state automatically. No second DirectContext is created.
 *
 * <p>The wall clock is reported through the existing {@code prewarm_gl} field
 * of {@code first_open_budget} — no new log line and no format change.
 */
public final class SkiaPipelineWarmup {

    /** Set to {@code false} to restore the old cold first frame (A/B and rollback). */
    public static final String PROPERTY = "atomchat.skia.prewarm";

    /** Visible, but small enough that rasterizing it is not itself expensive. */
    private static final int SIDE = 100;

    private static final AtomicBoolean DONE = new AtomicBoolean();
    private static volatile Throwable lastFailure;

    private SkiaPipelineWarmup() {
    }

    /**
     * Runs {@code step} at most once per process on the render thread.
     *
     * @return true only when the warmup actually completed
     */
    public static boolean runOnce(Runnable step) {
        if ("false".equalsIgnoreCase(System.getProperty(PROPERTY, "true"))) {
            return false;
        }
        if (!DONE.compareAndSet(false, true)) {
            return false;
        }
        try {
            step.run();
            return true;
        } catch (Throwable failure) {
            lastFailure = failure;
            com.atom.chat.AtomChat.LOGGER.warn(
                    com.atom.chat.diagnostics.AtomChatLog.message("render",
                            "Skia pipeline warmup failed; the first panel frame stays cold"),
                    failure);
            return false;
        }
    }

    /** Last contained failure, for diagnosis; null after a successful warmup. */
    public static Throwable lastFailure() {
        return lastFailure;
    }

    /**
     * Draws one visible frame on an offscreen GPU surface and waits for the
     * resulting programs to be compiled. Must be called inside the bridge's
     * {@code withContext} callback, on the render thread.
     */
    public static void drawWarmupFrame(DirectContext context) {
        ImageInfo info = ImageInfo.makeN32Premul(SIDE, SIDE, ColorSpace.getSRGB());
        Surface surface = Surface.makeRenderTarget(
                context, false, info, 0, SurfaceOrigin.BOTTOM_LEFT, null);
        if (surface == null) {
            throw new IllegalStateException("Skia warmup render target unavailable");
        }
        java.util.List<Picture> pending = new java.util.ArrayList<>(1);
        try {
            draw(surface.getCanvas(), pending);
        } finally {
            // Even a partially failed draw has already emitted GrOps; still
            // flush them so the programs get compiled before we release the
            // surface. syncCpu=true is the whole point of this warmup.
            try {
                context.flushAndSubmit(surface, true);
            } finally {
                try {
                    surface.close();
                } finally {
                    // Native Pictures must outlive the flush: drawPicture can
                    // leave deferred ops referencing them until submit.
                    for (Picture picture : pending) picture.close();
                    // Warmup-sized Pictures must not sit in the real LRU: the
                    // first panel frame still builds its own geometry, so any
                    // drop in first_draw is attributable to compilation alone.
                    DotTexture.clearCache();
                }
            }
        }
    }

    private static void draw(Canvas canvas, java.util.List<Picture> pending) {
        try (PictureRecorder recorder = new PictureRecorder();
             Paint fill = new Paint().setAntiAlias(true);
             Paint layer = new Paint();
             Shader gradient = Shader.makeLinearGradient(
                     0F, 0F, SIDE, 0F, new int[]{0xFF335577, 0xFF88BBDD});
             ImageFilter blur = ImageFilter.makeBlur(4F, 4F, FilterTileMode.CLAMP)) {
            // Opaque background: never let the whole frame collapse into
            // nothingToDraw, and never draw into the host framebuffer.
            canvas.clear(0xFF101418);

            // (1) The recorded-picture path — DOTS / _nDrawPicture, the single
            //     largest native hotspot (33.6% of render-thread samples).
            Canvas recording = recorder.beginRecording(Rect.makeWH(SIDE, SIDE));
            recording.drawRRect(RRect.makeXYWH(4F, 4F, SIDE - 8F, SIDE - 8F, 12F),
                    fill.setColor(0xFF223244));
            recording.drawCircle(SIDE / 2F, 28F, 14F, fill.setColor(0xFFCC8844));
            Picture picture = recorder.finishRecordingAsPicture();
            pending.add(picture);
            canvas.drawPicture(picture);

            // (2) Gradient shader + antialiased translucent geometry.
            canvas.drawRect(Rect.makeXYWH(8F, 8F, SIDE - 16F, 24F),
                    fill.setShader(gradient));
            fill.setShader(null);

            // (3) The exact primitives of a cold first frame. FrameSegments is
            //     a no-op here: outside a render frame current() == 0, so these
            //     calls cannot pollute the JFR segment accounting.
            UiLayout.Rect bar = new UiLayout.Rect(6F, 40F, SIDE - 12F, 30F);
            DotTexture.drawEdge(canvas, bar, 10F, true, 0xFF99CCFF);
            DotTexture.drawHalo(canvas, new UiLayout.Rect(10F, 62F, 60F, 26F), 0xFF99CCFF);
            ChromeMaterial.drawDetails(canvas, bar, 10F);
            // Precompile the outside clip + nine-patch path and build both radius variants off the first panel frame.
            SoftShadow.drawPanel(canvas, 12F, 12F, 420F, 620F, UiTokens.panelRadius());
            SoftShadow.drawPanel(canvas, 48F, 36F, 420F, 620F, 0F);
            SkiaFontRenderer.drawText(canvas, FontManager.font(UiTokens.FONT_BODY),
                    "AtomChat 预热 123", 12F, 56F, 0xFFFFFFFF);

            // (4) saveLayer + ImageFilter, the frosted-card path.
            canvas.saveLayer(Rect.makeXYWH(6F, 6F, SIDE - 12F, SIDE - 12F),
                    layer.setImageFilter(blur));
            canvas.drawRRect(RRect.makeXYWH(10F, 70F, SIDE - 20F, 20F, 8F),
                    fill.setColor(0x80FFFFFF));
            canvas.restore();
        }
    }

    /** Test seam; never called in production. */
    static void resetForTest() {
        DONE.set(false);
        lastFailure = null;
    }
}
