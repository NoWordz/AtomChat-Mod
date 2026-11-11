package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.atom.chat.diagnostics.FrameProfile;
import com.atom.lib.client.render.MinecraftRenderBridge;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import net.minecraft.client.Minecraft;

/**
 * Compatibility facade for AtomChat's existing screens. AtomLib owns the one
 * DirectContext and framebuffer surface for this GL context; this object only
 * scopes host drawing and UI painting into that bridge.
 */
public final class SkiaGraphics {
    private static int leakLogCount;
    private final ThreadLocal<Surface> borrowedSurface = new ThreadLocal<>();
    private final ThreadLocal<BackendRenderTarget> borrowedBackend = new ThreadLocal<>();
    private final ThreadLocal<Long> borrowedContextIdentity = new ThreadLocal<>();

    /** Kept for callers from the former standalone renderer; the bridge checks size on every pass. */
    public void checkFrameBufferId() { }

    /** Kept for source compatibility. Surface creation is bridge-owned and lazy. */
    public void createSurface() { RenderSystem.assertOnRenderThread(); MinecraftRenderBridge.get().prepare(); }

    public void draw(java.util.function.BiConsumer<Canvas, Image> renderer) { draw(null, renderer); }

    public void draw(Runnable preUi, java.util.function.BiConsumer<Canvas, Image> renderer) {
        draw(preUi, 0.0F, renderer);
    }

    /**
     * Runs host drawing in a separate state-scoped phase, then paints Skia in
     * the AtomLib bridge. No second Skija context or framebuffer is created.
     */
    public void draw(Runnable preUi, float density, java.util.function.BiConsumer<Canvas, Image> renderer) {
        draw(preUi, density, renderer, false);
    }

    /** Only the chat panel participates in the panel performance profile. */
    public void drawPanel(Runnable preUi, float density, java.util.function.BiConsumer<Canvas, Image> renderer) {
        draw(preUi, density, renderer, true);
    }

    private void draw(Runnable preUi, float density, java.util.function.BiConsumer<Canvas, Image> renderer, boolean panel) {
        RenderSystem.assertOnRenderThread();
        java.util.Objects.requireNonNull(renderer, "renderer");
        long profileStart = panel ? FrameProfile.start() : 0L;
        long allocatedBefore = profileStart == 0L ? -1L : FrameProfile.allocatedBytes();
        MinecraftRenderBridge bridge = MinecraftRenderBridge.get();
        long segmentId = com.atom.chat.diagnostics.FrameSegments.begin(profileStart != 0L);
        FrameProfile.Surface outerSurface = profileStart != 0L && segmentId == 0 ? FrameProfile.currentSurface() : null;
        boolean segmentSuccess = false;
        try {
            float scale = density > 0.0F ? density
                    : Math.max(1.0F, Minecraft.getInstance().getMainRenderTarget().height / 1080.0F);
            // The snapper measures in the canvas's own units, and this class is
            // the only writer of that transform: setting the frame density here
            // keeps device-grid snapping tied to the scale actually applied. It
            // runs before the pre-pass so a capture rect snapped in there reads
            // the same density the panel is about to be painted at.
            UiPixelSnap.beginFrame(scale);
            if (preUi != null) {
                long started = com.atom.chat.diagnostics.FrameSegments.start(segmentId);
                try { bridge.vanillaPass(preUi); } finally { com.atom.chat.diagnostics.FrameSegments.end(segmentId, com.atom.chat.diagnostics.FrameSegments.Stage.PRE_UI, started); }
            }
            long bridgeStarted = com.atom.chat.diagnostics.FrameSegments.start(segmentId);
            long[] bridgeExitStarted = segmentId == 0 ? null : new long[1];
            bridge.drawFrame((canvas, frame) -> {
                com.atom.chat.diagnostics.FrameSegments.end(segmentId, com.atom.chat.diagnostics.FrameSegments.Stage.BRIDGE_ENTER, bridgeStarted);
                borrowedSurface.set(frame.surface());
                borrowedBackend.set(frame.backendRenderTarget());
                int base = canvas.getSaveCount();
                canvas.save();
                canvas.scale(scale, scale);
                try {
                    borrowedContextIdentity.set(io.github.humbleui.skija.impl.Native.getPtr(frame.context()));
                    long started = com.atom.chat.diagnostics.FrameSegments.start(segmentId);
                    try { renderer.accept(canvas, null); } finally { com.atom.chat.diagnostics.FrameSegments.end(segmentId, com.atom.chat.diagnostics.FrameSegments.Stage.DRAW, started); }
                } finally {
                    borrowedContextIdentity.remove();
                    int leaked = canvas.getSaveCount() - (base + 1);
                    if (leaked != 0 && com.atom.chat.config.AtomChatConfig.get().debug) {
                        leakLogCount++;
                        if (leakLogCount == 1 || leakLogCount % 200 == 0) {
                            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render",
                                    "Skia canvas stack leak: {} unbalanced save/saveLayer (occurrence {})"),
                                    leaked, leakLogCount);
                        }
                    }
                    canvas.restoreToCount(base);
                    if (bridgeExitStarted != null) bridgeExitStarted[0] = com.atom.chat.diagnostics.FrameSegments.start(segmentId);
                }
            });
            if (bridgeExitStarted != null) com.atom.chat.diagnostics.FrameSegments.end(segmentId, com.atom.chat.diagnostics.FrameSegments.Stage.BRIDGE_EXIT, bridgeExitStarted[0]);
            segmentSuccess = true;
        } finally {
            borrowedSurface.remove();
            borrowedBackend.remove();
            com.atom.chat.diagnostics.FrameSegments.finish(segmentId, segmentSuccess);
            if (outerSurface != null) FrameProfile.restoreSurface(outerSurface);
        }
        if (segmentId != 0 && FrameProfile.frame(profileStart, allocatedBefore, segmentId)) {
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("render", FrameProfile.summary() + com.atom.chat.diagnostics.FrameSegments.summary()));
        }
    }

    /**
     * Runs one raw Skija preparation step inside the bridge's GL state and
     * pixel-transfer guard. The context is borrowed for this callback only:
     * never retain, close or reuse it after the callback returns.
     */
    public <T> T withContext(java.util.function.Function<DirectContext,T> action) {
        RenderSystem.assertOnRenderThread();
        return MinecraftRenderBridge.get().withContext(java.util.Objects.requireNonNull(action));
    }

    /** Identity only, valid on this thread inside the current paint callback. */
    public long contextIdentity() {
        Long identity = borrowedContextIdentity.get();
        if (identity == null || identity == 0) throw new IllegalStateException("Context identity is only borrowed during a Skia paint callback");
        return identity;
    }

    public BackendRenderTarget requireRenderTarget() {
        var target = borrowedBackend.get();
        if (target == null) throw new IllegalStateException("Render target is only borrowed during a Skia paint callback");
        return target;
    }

    public Surface requireSurface() {
        var surface = borrowedSurface.get();
        if (surface == null) throw new IllegalStateException("Surface is only borrowed during a Skia paint callback");
        return surface;
    }

    /** A screen never owns the bridge context; shutdown is handled by the platform lifecycle. */
    public void close() { }

    /** Legacy no-op; AC's blur remains a raw-GL host pass. */
    public void releaseWorldSnapshot() { }
}
