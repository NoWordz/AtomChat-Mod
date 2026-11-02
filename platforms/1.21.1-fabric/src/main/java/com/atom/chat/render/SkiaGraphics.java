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

    public void checkFrameBufferId() { }
    public void createSurface() { RenderSystem.assertOnRenderThread(); MinecraftRenderBridge.get().prepare(); }
    public void draw(java.util.function.BiConsumer<Canvas, Image> renderer) { draw(null, renderer); }
    public void draw(Runnable preUi, java.util.function.BiConsumer<Canvas, Image> renderer) { draw(preUi, 0.0F, renderer); }

    public void draw(Runnable preUi, float density, java.util.function.BiConsumer<Canvas, Image> renderer) {
        RenderSystem.assertOnRenderThread();
        java.util.Objects.requireNonNull(renderer, "renderer");
        long profileStart = FrameProfile.start();
        long allocatedBefore = profileStart == 0L ? -1L : FrameProfile.allocatedBytes();
        MinecraftRenderBridge bridge = MinecraftRenderBridge.get();
        try {
            float scale = density > 0.0F ? density
                    : Math.max(1.0F, MinecraftClient.getInstance().getFramebuffer().textureHeight / 1080.0F);
            // The snapper measures in the canvas's own units, and this class is
            // the only writer of that transform: setting the frame density here
            // keeps device-grid snapping tied to the scale actually applied. It
            // runs before the pre-pass so a capture rect snapped in there reads
            // the same density the panel is about to be painted at.
            UiPixelSnap.beginFrame(scale);
            if (preUi != null) bridge.vanillaPass(preUi);
            bridge.drawFrame((canvas, frame) -> {
                borrowedSurface.set(frame.surface());
                borrowedBackend.set(frame.backendRenderTarget());
                int base = canvas.getSaveCount();
                canvas.save();
                canvas.scale(scale, scale);
                try {
                    renderer.accept(canvas, null);
                } finally {
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
                }
            });
        } finally { borrowedSurface.remove(); borrowedBackend.remove(); }
        if (FrameProfile.frame(profileStart, allocatedBefore)) {
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("render", FrameProfile.summary()));
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
    public void close() { }
    public void releaseWorldSnapshot() { }
}
