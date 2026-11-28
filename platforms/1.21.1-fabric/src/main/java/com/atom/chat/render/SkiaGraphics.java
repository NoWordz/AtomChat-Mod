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
import net.minecraft.client.MinecraftClient;

/** Compatibility facade; AtomLib owns the single DirectContext and surface. */
public final class SkiaGraphics {
    private static int leakLogCount;
    private final ThreadLocal<Surface> borrowedSurface = new ThreadLocal<>();
    private final ThreadLocal<BackendRenderTarget> borrowedBackend = new ThreadLocal<>();
    private final ThreadLocal<Long> borrowedContextIdentity = new ThreadLocal<>();
    private java.lang.ref.WeakReference<Surface> lastInkSurface;
    private long inkGeneration;

    public void checkFrameBufferId() { }
    public void createSurface() { RenderSystem.assertOnRenderThread(); MinecraftRenderBridge.get().prepare(); }
    public void draw(java.util.function.BiConsumer<Canvas, Image> renderer) { draw(null, renderer); }
    public void draw(Runnable preUi, java.util.function.BiConsumer<Canvas, Image> renderer) { draw(preUi, 0.0F, renderer); }

    public void draw(Runnable preUi, float density, java.util.function.BiConsumer<Canvas, Image> renderer) {
        draw(preUi, density, renderer, false, false, () -> false);
    }

    /** Only the chat panel participates in the panel performance profile. */
    public void drawPanel(Runnable preUi, float density, java.util.function.BiConsumer<Canvas, Image> renderer) {
        drawPanel(preUi, density, renderer, false, () -> false);
    }

    public void drawPanel(Runnable preUi, float density, java.util.function.BiConsumer<Canvas, Image> renderer,
                          boolean panelBlurPlanned, java.util.function.BooleanSupplier panelBlurDrawn) {
        draw(preUi, density, renderer, true, panelBlurPlanned, panelBlurDrawn);
    }

    private void draw(Runnable preUi, float density, java.util.function.BiConsumer<Canvas, Image> renderer,
                      boolean panel, boolean panelBlurPlanned, java.util.function.BooleanSupplier panelBlurDrawn) {
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
                    : Math.max(1.0F, MinecraftClient.getInstance().getFramebuffer().textureHeight / 1080.0F);
            // The snapper measures in the canvas's own units, and this class is
            // the only writer of that transform: setting the frame density here
            // keeps device-grid snapping tied to the scale actually applied. It
            // runs before the pre-pass so a capture rect snapped in there reads
            // the same density the panel is about to be painted at.
            UiPixelSnap.beginFrame(scale);
            var config = panel ? com.atom.chat.config.AtomChatConfig.get() : null;
            boolean worldBehindCards = panel && !com.atom.chat.wallpaper.WallpaperStore.isSet();
            boolean captureBeforeBlur = worldBehindCards && config.needsSeparateCardBlur(panelBlurPlanned);
            Image world = captureBeforeBlur ? CardBackdrop.captureWorld(bridge::captureFrame) : null;
            try {
                if (preUi != null) {
                    long started = com.atom.chat.diagnostics.FrameSegments.start(segmentId);
                    try { bridge.vanillaPass(preUi); } finally { com.atom.chat.diagnostics.FrameSegments.end(segmentId, com.atom.chat.diagnostics.FrameSegments.Stage.PRE_UI, started); }
                }
                if (worldBehindCards && !captureBeforeBlur && config.needsSeparateCardBlur(panelBlurDrawn.getAsBoolean())) {
                    // The expected panel pre-pass failed. Sample the world for cards on this frame.
                    world = CardBackdrop.captureWorld(bridge::captureFrame);
                }
                Image cardWorld = world;
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
                        try { renderer.accept(canvas, cardWorld); } finally { com.atom.chat.diagnostics.FrameSegments.end(segmentId, com.atom.chat.diagnostics.FrameSegments.Stage.DRAW, started); }
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
                if (world != null) world.close();
            }
        } finally {
            borrowedSurface.remove();
            borrowedBackend.remove();
            NativeFontRenderer.endFrame();
            com.atom.chat.diagnostics.FrameSegments.finish(segmentId, segmentSuccess);
            if (outerSurface != null) FrameProfile.restoreSurface(outerSurface);
        }
        if (segmentId != 0 && FrameProfile.frame(profileStart, allocatedBefore, segmentId)) {
            com.atom.chat.diagnostics.AtomChatLog.diagnostic("render",
                    FrameProfile.summary() + com.atom.chat.diagnostics.FrameSegments.summary());
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
    /** The HUD owns the new surface. The bridge surface and context stay inside this paint. */
    Surface createInkSurface(int width, int height) {
        return createInkSurface(requireSurface(), width, height);
    }

    static Surface createInkSurface(Surface target, int width, int height) {
        try {
            Surface compatible = target.makeSurface(io.github.humbleui.skija.ImageInfo.makeN32Premul(width, height));
            return io.github.humbleui.skija.impl.Native.getPtr(compatible) == 0L ? null : compatible;
        } catch (AssertionError unavailableTarget) {
            // Skija wraps a native allocation failure in this assertion.
            if (!"Managed ptr is 0".equals(unavailableTarget.getMessage())) throw unavailableTarget;
            return null;
        }
    }

    /** A wrapper replacement invalidates HUD textures, including after a GL context rebuild. */
    long inkGeneration() {
        Surface surface = requireSurface();
        if (lastInkSurface == null || lastInkSurface.get() != surface) {
            lastInkSurface = new java.lang.ref.WeakReference<>(surface);
            inkGeneration++;
        }
        return inkGeneration;
    }

    public Surface requireSurface() {
        var surface = borrowedSurface.get();
        if (surface == null) throw new IllegalStateException("Surface is only borrowed during a Skia paint callback");
        return surface;
    }
    public void close() { }
    public void releaseWorldSnapshot() { }
}
