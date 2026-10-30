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
            if (preUi != null) bridge.vanillaPass(preUi);
            float scale = density > 0.0F ? density
                    : Math.max(1.0F, MinecraftClient.getInstance().getFramebuffer().textureHeight / 1080.0F);
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

    public DirectContext requireContext() { return MinecraftRenderBridge.get().withContext(context -> context); }
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
