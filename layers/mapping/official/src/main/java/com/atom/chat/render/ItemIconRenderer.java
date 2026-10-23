package com.atom.chat.render;

import com.atom.chat.AtomChat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.ShaderInstance;
import com.atom.chat.text.RichText;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.humbleui.skija.BackendTexture;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.GLTextureInfo;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.HoverEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GLCapabilities;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Native item renderer -> offscreen texture -> borrowed Skija image.
 * Prepare in SkiaGraphics.preUi after blur; draw only during Skia paint.
 * Render-thread only. First paint queues the icon and leaves its slot blank.
 */
public final class ItemIconRenderer {
    private static final int PIXELS = 64;
    private static final IconCache<HoverEvent.ItemStackInfo, Image> CACHE = new IconCache<>(128, Image::close);
    private static final ConcurrentLinkedQueue<RetiredTexture> RETIRED = new ConcurrentLinkedQueue<>();
    private static DirectContext owner;
    private static GLCapabilities ownerGl;
    private static boolean failed;

    private ItemIconRenderer() {}

    /**
     * Refreshes last frame's requested icons, including animations/custom models.
     * Textures are immutable generations: a borrowed image's texture is never
     * overwritten or deleted until Skia invokes its release callback. Borrowing,
     * unlike adoption, is safe across the bridge's following resetAll().
     * Never nest GlStateUtil.save(): its outer snapshot is not a stack.
     */
    public static void prepare(GuiGraphics gui, SkiaGraphics bridge) {
        if (gui == null || bridge == null) return;
        GLCapabilities gl;
        try {
            gl = GL.getCapabilities();
        } catch (IllegalStateException noContext) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) return;
        if (failed) {
            // Closing a borrowed image is safe even if Skia still retains it:
            // only its eventual release callback authorizes texture deletion.
            // Do not retry the failed render/state probe every frame.
            CACHE.clear();
            releaseRetired(gl);
            return;
        }
        List<HoverEvent.ItemStackInfo> requests = CACHE.takeRequests();
        if (requests.isEmpty() && CACHE.isEmpty() && RETIRED.isEmpty()) return;
        try (NativeState ignored = new NativeState()) {
            gui.flush();
            DirectContext context = bridge.requireContext();
            if (owner != null && owner != context && ownerGl == gl && !owner.isClosed()) {
                owner.resetGLAll();
                owner.flushAndSubmit(false);
            }
            // Submit all prior Skia sampling before closing/evicting images.
            // No readback, glFinish, or synchronous CPU/GPU wait.
            context.resetGLAll();
            context.flushAndSubmit(false);
            if (owner != context || ownerGl != gl || requests.isEmpty()) CACHE.clear();
            owner = context;
            ownerGl = gl;
            releaseRetired(gl);
            if (requests.isEmpty()) return;
            var minecraft = Minecraft.getInstance();
            // Independent pose/scissor bookkeeping; caller's GUI pose stays intact.
            var items = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            ItemIconModelView.pushIdentity();
            try {
                RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 16, 16, 0, -1000, 1000),
                        VertexSorting.ORTHOGRAPHIC_Z);
                RenderSystem.resetTextureMatrix();
                RenderSystem.setShaderColor(1, 1, 1, 1);
                for (HoverEvent.ItemStackInfo info : requests) {
                    var stack = info.getItemStack();
                    if (stack != null && !stack.isEmpty()) CACHE.put(info, render(items, stack.copy(), context, gl));
                }
            } finally {
                ItemIconModelView.pop();
            }
            releaseRetired(gl);
        } catch (RuntimeException | LinkageError error) {
            disable(error);
        }
    }

    /** No GL changes; current Canvas transforms, rounded clips and saveLayer alpha apply. */
    public static void draw(Canvas canvas, RichText.RichRun source, float x, float y, float size) {
        if (failed || canvas == null || source == null || source.style() == null
                || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(size) || size <= 0) return;
        var hover = source.style().getHoverEvent();
        if (hover == null) return;
        var info = hover.getValue(HoverEvent.Action.SHOW_ITEM);
        if (info == null) return;
        try {
            Image image = CACHE.request(info);
            if (image != null) canvas.drawImageRect(image, Rect.makeXYWH(x, y, size, size));
        } catch (RuntimeException | LinkageError error) {
            disable(error);
        }
    }

    private static Image render(GuiGraphics gui, ItemStack stack,
                                DirectContext context, GLCapabilities gl) {
        int texture = GL11C.glGenTextures();
        int framebuffer = 0;
        int depth = 0;
        boolean borrowed = false;
        try {
            RenderSystem.activeTexture(GL13C.GL_TEXTURE0);
            GlStateManager._bindTexture(texture);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
            org.lwjgl.opengl.GL15C.glBindBuffer(org.lwjgl.opengl.GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8,
                    PIXELS, PIXELS, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, 0L);
            framebuffer = GL30C.glGenFramebuffers();
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
            GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                    GL11C.GL_TEXTURE_2D, texture, 0);
            depth = GL30C.glGenRenderbuffers();
            GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, depth);
            GL30C.glRenderbufferStorage(GL30C.GL_RENDERBUFFER, GL14C.GL_DEPTH_COMPONENT24, PIXELS, PIXELS);
            GL30C.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT,
                    GL30C.GL_RENDERBUFFER, depth);
            if (GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Incomplete item icon framebuffer");
            }
            RenderSystem.viewport(0, 0, PIXELS, PIXELS);
            RenderSystem.disableScissor();
            GL11C.glDisable(GL11C.GL_STENCIL_TEST);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            GL11C.glClearColor(0, 0, 0, 0);
            GL11C.glClearDepth(1);
            GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT | GL11C.GL_DEPTH_BUFFER_BIT);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11C.GL_LEQUAL);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // Vanilla resolves model overrides and native custom renderers, applies
            // GUI lighting, fullbright and foil, then flushes the native buffers.
            gui.renderItem(stack, 0, 0);
            gui.flush();
            try (BackendTexture backend = BackendTexture.makeGL(PIXELS, PIXELS, false,
                    new GLTextureInfo(GL11C.GL_TEXTURE_2D, texture, GL11C.GL_RGBA8))) {
                int ownedTexture = texture;
                Image image = Image.borrowTextureFrom(context, backend, SurfaceOrigin.BOTTOM_LEFT,
                        ColorType.RGBA_8888, ColorAlphaType.PREMUL, null,
                        () -> RETIRED.add(new RetiredTexture(gl, ownedTexture)));
                borrowed = true;
                return image;
            }
        } finally {
            // Only the color texture crosses into Skia, never the FBO/depth buffer.
            if (framebuffer != 0) GL30C.glDeleteFramebuffers(framebuffer);
            if (depth != 0) GL30C.glDeleteRenderbuffers(depth);
            if (!borrowed) GlStateManager._deleteTexture(texture);
        }
    }

    private static void releaseRetired(GLCapabilities gl) {
        RetiredTexture texture;
        while ((texture = RETIRED.poll()) != null) {
            // Old context names may have been recycled in a replacement context.
            if (texture.gl == gl) GlStateManager._deleteTexture(texture.id);
        }
    }

    private static void disable(Throwable error) {
        if (failed) return;
        failed = true;
        AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render",
                "Native item icon bridge disabled after a rendering failure; inline slots remain blank"), error);
    }

    private record RetiredTexture(GLCapabilities gl, int id) {}

    /** Pure request/LRU policy; tests require neither Minecraft bootstrap nor GL. */
    static final class IconCache<K, V> {
        private final int limit;
        private final Consumer<V> release;
        private final LinkedHashMap<K, V> images = new LinkedHashMap<>(16, 0.75F, true);
        private final LinkedHashSet<K> requested = new LinkedHashSet<>();

        IconCache(int limit, Consumer<V> release) {
            if (limit < 1) throw new IllegalArgumentException("limit must be positive");
            this.limit = limit;
            this.release = release;
        }

        V request(K key) {
            if (requested.size() < limit) requested.add(key);
            return images.get(key);
        }

        List<K> takeRequests() {
            List<K> result = new ArrayList<>(requested);
            requested.clear();
            return result;
        }

        void put(K key, V value) {
            V previous = images.put(key, value);
            if (previous != null && previous != value) release.accept(previous);
            if (images.size() > limit) {
                var eldest = images.entrySet().iterator();
                V removed = eldest.next().getValue();
                eldest.remove();
                release.accept(removed);
            }
        }

        boolean isEmpty() { return images.isEmpty(); }

        void clear() {
            requested.clear();
            images.values().forEach(release);
            images.clear();
        }
    }

    /** Local snapshot; the outer GlStateUtil snapshot remains untouched. */
    private static final class NativeState implements AutoCloseable {
        private final int drawFbo = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int readFbo = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        private final int renderbuffer = GL11C.glGetInteger(GL30C.GL_RENDERBUFFER_BINDING);
        private final int[] viewport = new int[4];
        private final int[] scissor = new int[4];
        private final int[] colorMask = new int[4];
        private final boolean scissorEnabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        private final boolean stencilEnabled = GL11C.glIsEnabled(GL11C.GL_STENCIL_TEST);
        private final boolean depthEnabled = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        private final boolean blendEnabled = GL11C.glIsEnabled(GL11C.GL_BLEND);
        private final boolean cullEnabled = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        private final boolean depthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
        private final int depthFunc = GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC);
        private final int srcRgb = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB);
        private final int dstRgb = GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA);
        private final int dstAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA);
        private final float[] clearColor = new float[4];
        private final double clearDepth = GL11C.glGetDouble(GL11C.GL_DEPTH_CLEAR_VALUE);
        private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        private final VertexSorting sorting = RenderSystem.getVertexSorting();
        private final Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        private final Matrix4f textureMatrix = new Matrix4f(RenderSystem.getTextureMatrix());
        private final float[] color = RenderSystem.getShaderColor().clone();
        private final ShaderInstance shader = RenderSystem.getShader();
        private final int[] shaderTextures = new int[12];
        private final Vector3f[] lights;

        NativeState() {
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
            GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, scissor);
            GL11C.glGetIntegerv(GL11C.GL_COLOR_WRITEMASK, colorMask);
            GL11C.glGetFloatv(GL11C.GL_COLOR_CLEAR_VALUE, clearColor);
            for (int i = 0; i < shaderTextures.length; i++) shaderTextures[i] = RenderSystem.getShaderTexture(i);
            lights = copyLights();
        }

        @Override public void close() {
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.getModelViewMatrix().set(modelView);
            RenderSystem.setTextureMatrix(textureMatrix);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setShaderLights(lights[0], lights[1]);
            RenderSystem.setShader(() -> shader);
            for (int i = 0; i < shaderTextures.length; i++) RenderSystem.setShaderTexture(i, shaderTextures[i]);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawFbo);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFbo);
            GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, renderbuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11C.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            GL11C.glClearDepth(clearDepth);
            if (scissorEnabled) RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            else RenderSystem.disableScissor();
            if (stencilEnabled) GL11C.glEnable(GL11C.GL_STENCIL_TEST);
            else GL11C.glDisable(GL11C.GL_STENCIL_TEST);
            if (depthEnabled) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blendEnabled) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cullEnabled) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthMask(depthMask);
            RenderSystem.depthFunc(depthFunc);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            RenderSystem.colorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
            // The complete GL driver state is restored by the surrounding bridge;
            // its following context.resetAll invalidates Skia's GL state caches.
        }

        // MC exposes setShaderLights without a getter. Structural lookup avoids
        // development-only names which break in SRG/intermediary production jars.
        private static Vector3f[] copyLights() {
            try {
                Vector3f[] value = (Vector3f[]) LightField.FIELD.get(null);
                return new Vector3f[]{new Vector3f(value[0]), new Vector3f(value[1])};
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("Cannot preserve Minecraft GUI lighting", error);
            }
        }
    }

    private static final class LightField {
        private static final Field FIELD = find();

        private static Field find() {
            Field result = null;
            for (Field field : RenderSystem.class.getDeclaredFields()) {
                if (field.getType() != Vector3f[].class) continue;
                if (result != null) throw new IllegalStateException("Ambiguous Minecraft shader light state");
                result = field;
            }
            if (result == null || !result.trySetAccessible()) {
                throw new IllegalStateException("Minecraft shader light state unavailable");
            }
            return result;
        }
    }
}
