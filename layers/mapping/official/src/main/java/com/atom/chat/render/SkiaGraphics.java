package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.atom.chat.diagnostics.FrameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.PixelGeometry;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.skija.SurfaceProps;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL33C;

/**
 * Skia rendering bridge that draws directly onto Minecraft's main framebuffer.
 * Pattern ported from Tuui's Graphics class.
 */
public class SkiaGraphics {
    private static int leakLogCount;
    private Canvas canvas;
    private DirectContext context;
    private Surface surface;
    private BackendRenderTarget renderTarget;
    private int lastFrameBufferId = -1;
    private int lastFramebufferWidth = -1;
    private int lastFramebufferHeight = -1;
    private boolean surfaceDirty;
    /**
     * Reserved for the panel background blur. Currently always null — see
     * snapshotWorld() for why the blur is disabled.
     */

    public void checkFrameBufferId() {
        var fb = Minecraft.getInstance().getMainRenderTarget();
        int current = fb.frameBufferId;
        int width = fb.width;
        int height = fb.height;
        // Minecraft resizes the main framebuffer in place when the window
        // changes (F11 fullscreen toggle, dragging a windowed border), keeping
        // the same FBO id. The Skia surface must be recreated on size changes
        // too, otherwise its coordinate system goes stale while the raw-GL blur
        // pre-pass keeps using the new size — that is the small-window
        // misalignment/blur-offset bug.
        if (lastFrameBufferId != -1
                && (lastFrameBufferId != current
                || lastFramebufferWidth != width
                || lastFramebufferHeight != height)) {
            surfaceDirty = true;
        }
    }

    public void createSurface() {
        if (context == null) {
            context = DirectContext.makeGL();
        }
        if (surface != null) {
            surface.close();
        }
        if (renderTarget != null) {
            renderTarget.close();
        }

        var fb = Minecraft.getInstance().getMainRenderTarget();
        int width = fb.width;
        int height = fb.height;
        int fbo = fb.frameBufferId;
        lastFramebufferWidth = width;
        lastFramebufferHeight = height;

        renderTarget = BackendRenderTarget.makeGL(width, height, 0, 8, fbo, 32856);
        surface = Surface.wrapBackendRenderTarget(
                context,
                renderTarget,
                SurfaceOrigin.BOTTOM_LEFT,
                ColorType.RGBA_8888,
                ColorSpace.getDisplayP3(),
                new SurfaceProps(PixelGeometry.RGB_H)
        );
        lastFrameBufferId = fbo;
        surfaceDirty = false;
        canvas = surface != null ? surface.getCanvas() : null;
    }

    public void draw(java.util.function.BiConsumer<Canvas, Image> renderer) {
        draw(null, renderer);
    }

    public void draw(Runnable preUi, java.util.function.BiConsumer<Canvas, Image> renderer) {
        draw(preUi, 0.0F, renderer);
    }

    /**
     * Draws the Skia UI after an optional raw-GL pre-pass, with an explicit
     * design density. AtomChat passes {@code baseDensity * uiScale} so its own
     * scale option scales the whole panel uniformly — UiTokens constants stay
     * untouched. The pre-pass runs after {@link GlStateUtil#save()} and before
     * Skia paints anything; {@link GlStateUtil#restore()} cleans up afterwards.
     *
     * @param density {@code <= 0} falls back to the 1080p-anchored default.
     */
    public void draw(Runnable preUi, float density, java.util.function.BiConsumer<Canvas, Image> renderer) {
        RenderSystem.assertOnRenderThread();
        // 尺子：整帧计时 + 这一帧在渲染线程上新增的对象分配。开关在 FrameProfile 自己手里，
        // 关掉时 start() 返回 0，下面两个调用点什么都不做。
        long profileStart = FrameProfile.start();
        long allocatedBefore = profileStart == 0L ? -1L : FrameProfile.allocatedBytes();
        GlStateUtil.save();
        try {
            if (context == null || surface == null || canvas == null || surfaceDirty) {
                createSurface();
            }
            if (canvas == null) {
                AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render", "Skia canvas is null after createSurface, skipping frame"));
                return;
            }
            if (density <= 0.0F) {
                density = Math.max(1.0F, Minecraft.getInstance().getMainRenderTarget().height / 1080.0F);
            }
            glStorePixel();
            if (preUi != null) {
                preUi.run();
            }
            // Capture the world before Skia resets its resource tracking: resetAll()
            // can drop the bookkeeping for our adopted texture-backed image.
            Image snapshot = snapshotWorld();
            context.resetAll();
            RenderSystem.enableBlend();

            // Guard rail: remember the stack baseline. A renderer that forgets a
            // matching restore (typically saveLayer) used to leak a matrix per
            // frame, and because the density scale is applied every frame the leak
            // compounded into an exploding transform that flung the whole UI off
            // screen — the 0.2.4 notification banner rollback. Rewinding to the
            // baseline caps any future leak at a single frame.
            int saveBase = canvas.getSaveCount();
            canvas.save();
            // Decouple from vanilla GUI scale: design density anchored at 1080p.
            canvas.scale(density, density);
            try {
                renderer.accept(canvas, snapshot);
            } finally {
                int leaked = canvas.getSaveCount() - (saveBase + 1);
                if (leaked != 0 && com.atom.chat.config.AtomChatConfig.get().debug) {
                    leakLogCount++;
                    // Throttled: a per-frame leak used to spam the log line-by-line.
                    if (leakLogCount == 1 || leakLogCount % 200 == 0) {
                        AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render", "Skia canvas stack leak: {} unbalanced save/saveLayer (occurrence {})"),
                                leaked, leakLogCount);
                    }
                }
                canvas.restoreToCount(saveBase);
            }

            context.flush(surface);
        } finally {
            GlStateUtil.restore();
        }
        // 到报告点就打一份分布（六十帧一次，约十秒）。这个类自己不打日志：格式留在 FrameProfile
        // 里，于是它的每一行都能在单测里拼出来 —— 一个要装全局渲染状态才能测的诊断类没人测。
        if (FrameProfile.frame(profileStart, allocatedBefore)) {
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("render", FrameProfile.summary()));
        }
    }

    /**
     * Legacy Skia-side snapshot path. Intentionally returns null: the panel
     * blur is now implemented by {@link PanelBlurRenderer} with raw GL and a
     * core shader outside Skia, so no Skia GPU resource crosses
     * {@code context.resetAll()}. Do not re-introduce Skia snapshots here.
     */
    private Image snapshotWorld() {
        return null;
    }

    /** No-op; kept so callers need no change. */
    public void releaseWorldSnapshot() {
    }

    private void glStorePixel() {
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
    }

    public DirectContext requireContext() {
        if (context == null) {
            throw new IllegalStateException("Skia context not initialized");
        }
        return context;
    }

    public BackendRenderTarget requireRenderTarget() {
        if (renderTarget == null) {
            throw new IllegalStateException("Render target not created");
        }
        return renderTarget;
    }

    public Surface requireSurface() {
        if (surface == null) {
            throw new IllegalStateException("Surface not created");
        }
        return surface;
    }

    /** Screen-owned bridges release GPU resources when their screen is removed. */
    public void close() {
        if (surface != null) { surface.close(); surface = null; }
        if (renderTarget != null) { renderTarget.close(); renderTarget = null; }
        if (context != null) { context.close(); context = null; }
        canvas = null;
    }
}
