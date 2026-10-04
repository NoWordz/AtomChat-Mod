package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30;

/**
 * Rounded panel background blur rendered outside Skia.
 *
 * <p>The world behind the panel is captured straight from Minecraft's main
 * framebuffer with {@code glBlitFramebuffer}, then blurred with several Kawase
 * passes on raw GL FBOs, and finally drawn back through an AtomChat-owned
 * rounded-rect core shader so only the phone panel's rounded area is replaced
 * by the blurred image.</p>
 *
 * <p>Everything here is raw GL + Minecraft shaders: no Skia GPU resource is
 * kept across frames, so the per-frame {@code DirectContext.resetAll()} cannot
 * destroy it.</p>
 */
public final class PanelBlurRenderer {
    private static final String ROUNDED_SHADER_NAME = "atomchat_panel_blur";
    private static final String KAWASE_SHADER_NAME = "atomchat_kawase_blur";
    private static final int KAWASE_PASSES = 5;

    private static ShaderProgram roundedShader;
    private static boolean roundedLoadAttempted;
    private static ShaderProgram kawaseShader;
    private static boolean kawaseLoadAttempted;

    // Panel-sized GPU ping-pong buffers.
    private static int inputFbo = -1, inputTex = -1;   // 1:1 capture from main framebuffer
    private static int tempFbo = -1, tempTex = -1;     // Kawase ping-pong buffer A
    private static int resultFbo = -1, resultTex = -1; // Kawase ping-pong buffer B
    private static int texW;
    private static int texH;
    private static int lastBlurTex = -1;
    private static boolean nextFullRefresh = true;
    private static boolean recreated = true;
    /** Set once by {@link #disable(String)}; see there for why it never resets. */
    private static volatile boolean disabled;

    private PanelBlurRenderer() {
    }

    /**
     * Blurs the panel region and draws it back with a rounded mask.
     *
     * @param pose   current DrawContext pose (identity for AtomChat's own UI)
     * @param x      panel left in GUI-scaled coordinates
     * @param y      panel top in GUI-scaled coordinates
     * @param width  panel width in GUI-scaled coordinates
     * @param height panel height in GUI-scaled coordinates
     * @param radius rounded-corner radius in GUI-scaled coordinates
     * @param alpha  overall fade alpha (0..1, follows the panel open animation)
     * @return true only if the rounded blur quad was actually submitted
     */
    public static boolean render(Matrix4f pose, float x, float y, float width, float height, float radius, float alpha) {
        if (alpha <= 0.003F || width <= 0.0F || height <= 0.0F) {
            return false;
        }
        if (disabled || getKawaseShader() == null || getRoundedShader() == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        var fb = client.getFramebuffer();
        double scale = client.getWindow().getScaleFactor();

        int px = (int) Math.round(x * scale);
        int py = (int) Math.round(y * scale);
        int pw = (int) Math.round(width * scale);
        int ph = (int) Math.round(height * scale);
        if (pw <= 0 || ph <= 0) {
            return false;
        }

        // Snapshot the bindings this call is allowed to move, before anything
        // touches them: the blur has to hand the frame's real render target back
        // even when a pass bails out half-way. Nothing else restores the
        // framebuffer for us (GlStateUtil does not own it).
        int restoreFbo = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] restoreViewport = new int[4];
        GL30.glGetIntegerv(GL30.GL_VIEWPORT, restoreViewport);
        boolean restoreScissor = GL30.glIsEnabled(GL30.GL_SCISSOR_TEST);

        // Drop errors other mods left in the queue so the ones seen below are
        // actually ours.
        drainGlErrors();

        try {
            ensureTextures(pw, ph);
            int blurTex = refreshBlur(fb.fbo, fb.textureHeight, px, py, pw, ph);
            if (blurTex == -1) {
                return false;
            }
            if (!drawRoundedQuad(pose, x, y, width, height, radius, alpha, blurTex)) {
                return false;
            }
            // A silent driver-level failure would otherwise leave the panel
            // tinted as if the blur had landed. Report it once and answer "no
            // blur" so drawPanel keeps the solid background instead of showing
            // whatever the broken pass wrote.
            int glError = drainGlErrors();
            if (glError != 0) {
                disable("GL error 0x" + Integer.toHexString(glError));
                return false;
            }
            return true;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, restoreFbo);
            GL30.glViewport(restoreViewport[0], restoreViewport[1], restoreViewport[2], restoreViewport[3]);
            if (restoreScissor) {
                GL30.glEnable(GL30.GL_SCISSOR_TEST);
            } else {
                GL30.glDisable(GL30.GL_SCISSOR_TEST);
            }
        }
    }

    /** Empties the GL error queue, returning the first error found (0 = clean). */
    private static int drainGlErrors() {
        int first = 0;
        for (int i = 0; i < 16; i++) {
            int error = GL11C.glGetError();
            if (error == 0) {
                break;
            }
            if (first == 0) {
                first = error;
            }
        }
        return first;
    }

    /**
     * Trips the breaker: one failure and the blur is off for the rest of the
     * session.
     *
     * <p>Why permanent instead of retried: a pass that produced a driver error
     * once will produce it again on the next frame — nothing it depends on has
     * changed in between. Retrying buys a half-drawn panel and a log flood, and
     * it buries the one thing worth reading (the first error, and the frame it
     * happened on). The panel stays fully usable on its solid background, and
     * the next launch starts from a clean slate.
     *
     * <p>Both failure paths come through here — a GL error inside the pass, and
     * a throwable from the caller's wrapper — so there is exactly one breaker.
     */
    public static void disable(String reason) {
        if (disabled) {
            return;
        }
        disabled = true;
        AtomChat.LOGGER.warn("AtomChat panel blur disabled for this session after the first failure "
                + "({}); the panel keeps its solid background", reason);
    }

    public static void ensureLoaded() {
        getRoundedShader();
        getKawaseShader();
    }

    public static boolean isAvailable() {
        return !disabled && roundedShader != null && kawaseShader != null;
    }

    public static void resetShader() {
        roundedLoadAttempted = false;
        roundedShader = null;
        kawaseLoadAttempted = false;
        kawaseShader = null;
    }

    private static ShaderProgram getRoundedShader() {
        if (!roundedLoadAttempted) {
            roundedLoadAttempted = true;
            try {
                roundedShader = new ShaderProgram(
                        MinecraftClient.getInstance().getResourceManager(),
                        ROUNDED_SHADER_NAME,
                        VertexFormats.POSITION_TEXTURE_COLOR);
            } catch (Throwable t) {
                AtomChat.LOGGER.warn("AtomChat panel blur shader failed to load; blur disabled", t);
                roundedShader = null;
            }
        }
        return roundedShader;
    }

    private static ShaderProgram getKawaseShader() {
        if (!kawaseLoadAttempted) {
            kawaseLoadAttempted = true;
            try {
                kawaseShader = new ShaderProgram(
                        MinecraftClient.getInstance().getResourceManager(),
                        KAWASE_SHADER_NAME,
                        VertexFormats.POSITION);
            } catch (Throwable t) {
                AtomChat.LOGGER.warn("AtomChat Kawase blur shader failed to load; blur disabled", t);
                kawaseShader = null;
            }
        }
        return kawaseShader;
    }

    private static boolean drawRoundedQuad(Matrix4f pose, float x, float y, float w, float h, float radius, float alpha, int texture) {
        ShaderProgram sh = roundedShader;
        if (sh == null) {
            return false;
        }

        GlUniform uRect = sh.getUniform("u_Rect");
        GlUniform uRadius = sh.getUniform("u_Radius");
        GlUniform uFlipV = sh.getUniform("u_FlipV");
        if (uRect == null || uRadius == null || uFlipV == null) {
            return false;
        }

        float poseScale = Math.abs(pose.m00());
        Vector4f center = pose.transform(new Vector4f(x + w / 2.0F, y + h / 2.0F, 0.0F, 1.0F));
        uRect.set(0, center.x);
        uRect.set(1, center.y);
        uRect.set(2, w / 2.0F * poseScale);
        uRect.set(3, h / 2.0F * poseScale);
        uRadius.set(0, Math.min(radius, Math.min(w, h) / 2.0F) * poseScale);
        // GL textures are bottom-up; the shader flips V so GUI top-left is texture top.
        uFlipV.set(0, 1.0F);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(() -> sh);

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        bb.vertex(pose, x, y, 0).texture(0.0F, 0.0F).color(1.0F, 1.0F, 1.0F, alpha);
        bb.vertex(pose, x, y + h, 0).texture(0.0F, 1.0F).color(1.0F, 1.0F, 1.0F, alpha);
        bb.vertex(pose, x + w, y + h, 0).texture(1.0F, 1.0F).color(1.0F, 1.0F, 1.0F, alpha);
        bb.vertex(pose, x + w, y, 0).texture(1.0F, 0.0F).color(1.0F, 1.0F, 1.0F, alpha);
        BufferRenderer.drawWithGlobalProgram(bb.end());

        RenderSystem.disableBlend();
        return true;
    }

    private static void ensureTextures(int w, int h) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        if (w == texW && h == texH) {
            return;
        }
        destroyTextures();
        recreated = true;
        texW = w;
        texH = h;

        int[] a = makeTexture(w, h);
        inputFbo = a[0];
        inputTex = a[1];
        int[] b = makeTexture(w, h);
        tempFbo = b[0];
        tempTex = b[1];
        int[] c = makeTexture(w, h);
        resultFbo = c[0];
        resultTex = c[1];
    }

    private static int[] makeTexture(int w, int h) {
        int fbo = GL30.glGenFramebuffers();
        int tex = GlStateManager._genTexture();
        // This runs mid-frame, so the framebuffer binding has to be handed back:
        // leaving the new (panel-sized) FBO bound sent every later draw of that
        // frame into it, and refreshBlur() then recorded the wrong target as
        // "old" while restoring.
        int previousFbo = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        try {
            GlStateManager._bindTexture(tex);
            GlStateManager._texParameter(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);
            GlStateManager._texParameter(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);
            GlStateManager._texParameter(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
            GlStateManager._texImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA8, w, h, 0,
                    GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_TEXTURE_2D, tex, 0);
            return new int[]{fbo, tex};
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
        }
    }

    private static void destroyTextures() {
        if (inputFbo != -1) {
            GL30.glDeleteFramebuffers(inputFbo);
            GlStateManager._deleteTexture(inputTex);
            GL30.glDeleteFramebuffers(tempFbo);
            GlStateManager._deleteTexture(tempTex);
            GL30.glDeleteFramebuffers(resultFbo);
            GlStateManager._deleteTexture(resultTex);
            inputFbo = tempFbo = resultFbo = -1;
            inputTex = tempTex = resultTex = -1;
            texW = texH = 0;
        }
    }

    private static void blit(int srcFbo, int sx0, int sy0, int sx1, int sy1,
                             int dstFbo, int dx0, int dy0, int dw, int dh) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dstFbo);
        GL30.glBlitFramebuffer(sx0, sy0, sx1, sy1, dx0, dy0, dx0 + dw, dy0 + dh,
                GL30.GL_COLOR_BUFFER_BIT, GL30.GL_LINEAR);
    }

    private static int refreshBlur(int mainFbo, int fbHeight, int x, int y, int w, int h) {
        int y2 = Math.min(y + h, fbHeight);
        if (y2 <= y) {
            return lastBlurTex;
        }
        h = y2 - y;

        int oldFbo = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL30.glGetIntegerv(GL30.GL_VIEWPORT, viewport);
        boolean scissor = GL30.glIsEnabled(GL30.GL_SCISSOR_TEST);
        GL30.glDisable(GL30.GL_SCISSOR_TEST);

        int glY0 = fbHeight - (y + h);
        int glY1 = fbHeight - y;

        // r15 on-demand blur: the frame slot stays, but the shared gate decides
        // whether a due slot actually runs. A still scene (camera + panel chrome)
        // drops to the <=500ms fallback cadence; any motion restores the
        // every-2-frames refresh. A denied slot simply reuses lastBlurTex.
        long nowMs = System.currentTimeMillis();
        boolean slotDue = nextFullRefresh || recreated;
        boolean full = BlurMotionGate.allowFullRefresh(nowMs, slotDue, recreated);
        nextFullRefresh = !full;
        if (full) {
            blit(mainFbo, x, glY0, x + w, glY1, inputFbo, 0, 0, w, h);

            int srcTex = inputTex;
            for (int i = 0; i < KAWASE_PASSES; i++) {
                int dstFbo = (i % 2 == 0) ? tempFbo : resultFbo;
                kawasePass(dstFbo, srcTex, w, h, 2.0F + i * 1.5F);
                srcTex = (i % 2 == 0) ? tempTex : resultTex;
            }
            lastBlurTex = (KAWASE_PASSES % 2 == 0) ? resultTex : tempTex;
            recreated = false;
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFbo);
        GL30.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        if (scissor) {
            GL30.glEnable(GL30.GL_SCISSOR_TEST);
        }
        return lastBlurTex;
    }

    private static void kawasePass(int dstFbo, int srcTex, int w, int h, float spacing) {
        ShaderProgram sh = kawaseShader;
        if (sh == null) {
            return;
        }

        GlUniform uSpacing = sh.getUniform("u_Spacing");
        if (uSpacing == null) {
            return;
        }

        int oldFbo = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL30.glGetIntegerv(GL30.GL_VIEWPORT, viewport);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, dstFbo);
        GL30.glViewport(0, 0, w, h);

        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, srcTex);
        uSpacing.set(0, spacing);
        RenderSystem.setShader(() -> sh);

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        bb.vertex(-1.0F, -1.0F, 0.0F);
        bb.vertex(1.0F, -1.0F, 0.0F);
        bb.vertex(1.0F, 1.0F, 0.0F);
        bb.vertex(-1.0F, 1.0F, 0.0F);
        BufferRenderer.drawWithGlobalProgram(bb.end());

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFbo);
        GL30.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }
}
