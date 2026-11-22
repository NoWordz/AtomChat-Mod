package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.PanelBackground;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.IRect;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A shared blurred background. Retained pictures carry only card masks. */
public final class CardBackdrop implements AutoCloseable {
    private static final ThreadLocal<CardBackdrop> FRAME = new ThreadLocal<>();
    private static final ThreadLocal<MaskRecorder> RECORDING = new ThreadLocal<>();
    private static final CardBackdrop NONE = new CardBackdrop(null, null, false);
    private static boolean reportedFailure;
    private static RuntimeEffect worldRgb;
    private static Blender preserveTargetAlpha;

    private final Image blurred;
    private final Rect deviceBounds;
    private final Paint sample;
    private final Paint composite;
    private final Rect source;

    private CardBackdrop(Image blurred, Rect deviceBounds, boolean mainTarget) {
        this.blurred = blurred;
        this.deviceBounds = deviceBounds;
        this.sample = blurred == null ? null : new Paint().setBlendMode(BlendMode.SRC_IN);
        this.composite = blurred == null ? null : new Paint();
        if (blurred != null && mainTarget) this.composite.setBlender(preserveTargetAlpha);
        this.source = blurred == null ? null : Rect.makeWH(blurred.getWidth(), blurred.getHeight());
    }

    private static Blender alphaPreservingBlender() {
        if (preserveTargetAlpha == null) {
            try (RuntimeEffect effect = RuntimeEffect.makeForBlender("""
                    half4 main(half4 source, half4 target) {
                        return half4(source.rgb + target.rgb * (1. - source.a), target.a);
                    }
                    """)) {
                preserveTargetAlpha = effect.makeBlender(null);
            }
        }
        return preserveTargetAlpha;
    }

    /** The outer animation layer is where a private glass pass meets the real framebuffer. */
    public static boolean preserveFramebufferAlpha(Paint layer) {
        try {
            layer.setBlender(alphaPreservingBlender());
            return true;
        } catch (RuntimeException | LinkageError failure) {
            report(failure);
            return false;
        }
    }

    public static Image captureWorld(Supplier<Image> capture) {
        if (AtomChatConfig.performanceModeActive()) return null;
        try {
            return capture.get();
        } catch (RuntimeException | LinkageError failure) {
            report(failure);
            return null;
        }
    }

    public static CardBackdrop begin(Surface target, Canvas canvas, UiLayout.Rect panel,
                                     Image world, Image wallpaper, AtomChatConfig config) {
        return begin(target, canvas, panel, world, wallpaper, config, true);
    }

    public static CardBackdrop begin(Surface target, Canvas canvas, UiLayout.Rect panel,
                                     Image world, Image wallpaper, AtomChatConfig config, boolean mainTarget) {
        float strength = config.effectiveCardBlurStrength();
        if (strength <= 0F || (world == null && wallpaper == null)) return NONE;
        try {
            if (mainTarget) alphaPreservingBlender();
            Matrix33 panelMatrix = canvas.getLocalToDeviceAsMatrix33();
            float[] m = panelMatrix.getMat();
            // World glass has the same source crop as the GL panel pass.
            // Clamp at that edge so moving the strength past the panel value cannot pull in outside colour.
            float inset = wallpaper != null || config.halfScreenMode ? 0F : UiTokens.s(config.effectivePanelOutlineWidth());
            int originX = Math.round((panel.x() + inset) * m[0] + m[2]);
            int originY = Math.round((panel.y() + inset) * m[4] + m[5]);
            int left = Math.max(0, originX);
            int top = Math.max(0, originY);
            int right = Math.min(target.getWidth(), originX + Math.round((panel.w() - 2F * inset) * m[0]));
            int bottom = Math.min(target.getHeight(), originY + Math.round((panel.h() - 2F * inset) * m[4]));
            if (right <= left || bottom <= top) return NONE;
            Rect bounds = Rect.makeLTRB(left, top, right, bottom);

            // Blur at half resolution. Its radius is still expressed in physical pixels.
            int w = Math.max(1, (right - left + 1) / 2);
            int h = Math.max(1, (bottom - top + 1) / 2);
            try (Surface source = target.makeSurface(w, h);
                 Surface result = target.makeSurface(w, h)) {
                if (source == null || result == null) return NONE;
                Canvas input = source.getCanvas();
                input.scale(w / bounds.getWidth(), h / bounds.getHeight());
                input.translate(-left, -top);
                if (wallpaper != null) {
                    input.clear(PanelBackground.opaque(config.panelBgColor));
                    input.concat(panelMatrix);
                    drawWallpaper(input, wallpaper, panel, config.panelOpacity);
                } else {
                    drawWorld(input, world, bounds, config);
                }
                CardBackdrop frame = new CardBackdrop(KawaseBlur.blur(source, result, bounds, strength), bounds, mainTarget);
                FRAME.set(frame);
                return frame;
            }
        } catch (RuntimeException | LinkageError failure) {
            report(failure);
            return NONE;
        }
    }

    public static void drawWallpaper(Canvas canvas, Image wallpaper, UiLayout.Rect panel, float opacity) {
        float scale = Math.max(panel.w() / wallpaper.getWidth(), panel.h() / wallpaper.getHeight());
        float width = wallpaper.getWidth() * scale;
        float height = wallpaper.getHeight() * scale;
        Image fitted = ImageQuality.scaledForLogical(wallpaper, Math.max(width, height));
        Rect dst = Rect.makeXYWH(panel.x() + (panel.w() - width) / 2, panel.y() + (panel.h() - height) / 2,
                width, height);
        try (Paint paint = new Paint().setAlphaf(Math.max(0F, Math.min(1F, opacity)))) {
            canvas.drawImageRect(fitted, Rect.makeWH(fitted.getWidth(), fitted.getHeight()), dst,
                    SamplingMode.LINEAR, paint, false);
        }
    }

    private static void drawWorld(Canvas input, Image world, Rect bounds, AtomChatConfig config) {
        if (worldRgb == null) {
            worldRgb = RuntimeEffect.makeForShader(
                    "uniform shader world; half4 main(float2 point) { return half4(world.eval(point).rgb, 1.); }");
        }
        // Shader packs may leave undefined alpha. Normalize only the private blur input.
        try (Shader child = world.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR, null);
             Shader rgb = worldRgb.makeShader(null, new Shader[]{child});
             Paint paint = new Paint().setShader(rgb)) {
            input.drawRect(bounds, paint);
            paint.setShader(null).setColor(PanelBackground.withOpacity(config.panelBgColor, config.panelOpacity));
            input.drawRect(bounds, paint);
        }
    }

    public static void draw(Canvas canvas, float x, float y, float width, float height, float radius) {
        draw(canvas, x, y, width, height, radius, 1F);
    }

    public static void draw(Canvas canvas, float x, float y, float width, float height, float radius, float opacity) {
        MaskRecorder recorder = RECORDING.get();
        CardBackdrop frame = FRAME.get();
        if ((recorder == null && frame == null) || width <= 0F || height <= 0F || opacity <= 0F) return;
        RRect shape = RRect.makeXYWH(x, y, width, height, radius);
        if (recorder != null) {
            float previous = pushMaskOpacity(opacity);
            try {
                recorder.add(canvas, (mask, paint) -> CornerGeometry.drawRRect(mask, shape, paint));
            } finally {
                restoreMaskOpacity(previous);
            }
        } else {
            int save = canvas.save();
            try {
                CornerGeometry.clipRRect(canvas, shape, true);
                frame.drawDeviceImage(canvas, frame.composite.setAlphaf(opacity));
            } finally {
                canvas.restoreToCount(save);
            }
        }
    }

    public static boolean active() {
        return FRAME.get() != null;
    }

    /** Capture device geometry in the picture recorder's local space. */
    public static Picture recordMasks(Rect cull, Runnable body) {
        return recordMasks(cull, false, body);
    }

    public static Picture recordMasks(Rect cull, boolean force, Runnable body) {
        MaskRecorder previous = RECORDING.get();
        if (!force && !active() && previous == null) {
            body.run();
            return null;
        }
        MaskRecorder masks = new MaskRecorder();
        RECORDING.set(masks);
        try {
            body.run();
        } finally {
            if (previous == null) RECORDING.remove();
            else RECORDING.set(previous);
        }
        if (masks.commands.isEmpty()) return null;
        try (PictureRecorder recorder = new PictureRecorder(); Paint paint = new Paint().setAntiAlias(true)) {
            Canvas mask = recorder.beginRecording(cull);
            for (MaskCommand command : masks.commands) command.draw(mask, paint);
            return recorder.finishRecordingAsPicture();
        }
    }

    public static float pushMaskOpacity(float opacity) {
        MaskRecorder masks = RECORDING.get();
        if (masks == null) return 1F;
        float previous = masks.opacity;
        masks.opacity *= opacity;
        return previous;
    }

    public static void restoreMaskOpacity(float opacity) {
        MaskRecorder masks = RECORDING.get();
        if (masks != null) masks.opacity = opacity;
    }

    public static void applyMasks(Canvas canvas, Rect bounds, Consumer<Canvas> drawMasks) {
        MaskRecorder recorder = RECORDING.get();
        if (recorder != null) {
            recorder.add(canvas, (mask, paint) -> {
                mask.clipRect(bounds);
                if (paint.getAlpha() != 255) mask.saveLayer(bounds, paint);
                drawMasks.accept(mask);
            });
            return;
        }

        CardBackdrop frame = FRAME.get();
        if (frame == null) return;
        int save = canvas.save();
        try {
            canvas.clipRect(bounds);
            canvas.saveLayer(bounds, frame.composite.setAlphaf(1F));
            drawMasks.accept(canvas);
            frame.drawDeviceImage(canvas, frame.sample);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private void drawDeviceImage(Canvas canvas, Paint paint) {
        canvas.resetMatrix();
        canvas.drawImageRect(blurred, source, deviceBounds, SamplingMode.LINEAR, paint, true);
    }

    private static void report(Throwable failure) {
        if (!reportedFailure) {
            reportedFailure = true;
            AtomChat.LOGGER.warn("[AtomChat/render] Card backdrop unavailable", failure);
        }
    }

    @Override public void close() {
        if (blurred == null) return;
        FRAME.remove();
        composite.close();
        sample.close();
        blurred.close();
    }

    @FunctionalInterface
    private interface MaskCommand {
        void draw(Canvas canvas, Paint paint);
    }

    private static final class MaskRecorder {
        final ArrayList<MaskCommand> commands = new ArrayList<>();
        float opacity = 1F;

        void add(Canvas canvas, MaskCommand command) {
            IRect clip = canvas.getDeviceClipBounds();
            if (clip == null || opacity <= 0F) return;
            Matrix33 matrix = canvas.getLocalToDeviceAsMatrix33();
            Rect outerClip = Rect.makeLTRB(clip.getLeft(), clip.getTop(), clip.getRight(), clip.getBottom());
            float alpha = opacity;
            commands.add((mask, paint) -> {
                int save = mask.save();
                try {
                    mask.clipRect(outerClip);
                    mask.concat(matrix);
                    command.draw(mask, paint.setAlphaf(alpha));
                } finally {
                    mask.restoreToCount(save);
                }
            });
        }
    }
}
