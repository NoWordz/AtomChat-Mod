package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
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

    private static ShaderInstance roundedShader;
    private static ShaderInstance kawaseShader;

    // Panel-sized GPU ping-pong buffers.
    private static int inputFbo = -1, inputTex = -1;   // 1:1 capture from main framebuffer
    private static int tempFbo = -1, tempTex = -1;     // Kawase ping-pong buffer A
    private static int resultFbo = -1, resultTex = -1; // Kawase ping-pong buffer B
    private static int texW;
    private static int texH;
    private static int capW, capH;          // allocated capacity (power of two) / exact size
    private static boolean capacityMode;    // true = capacity reuse, false = exact (clipped fallback)
    // Per-frame geometry derived from the capture rect + framebuffer height in render().
    private static int vpW, vpH;            // Kawase viewport
    private static float tmx, tmy;          // Kawase quad texCoord upper bound
    private static float uvX, uvY;          // rounded-quad UV normalisation (actual / capacity)
    private static int lastBlurTex = -1;
    private static boolean recreated = true;
    /** Set once by {@link #disable(String)}; see there for why it never resets. */
    private static volatile boolean disabled;

    private PanelBlurRenderer() {
    }

    /** NeoForge shader registration; fired from the mod event bus. */
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, ROUNDED_SHADER_NAME),
                            DefaultVertexFormat.POSITION_TEX_COLOR),
                    shader -> roundedShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, KAWASE_SHADER_NAME),
                            DefaultVertexFormat.POSITION),
                    shader -> kawaseShader = shader);
        } catch (Exception e) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render", "AtomChat panel blur shaders failed to load; blur disabled"), e);
            roundedShader = null;
            kawaseShader = null;
        }
    }

    /**
     * Blurs the panel region and draws it back with a rounded mask.
     *
     * @param pose   current GUI pose (identity for AtomChat's own UI)
     * @param x      panel left in GUI-scaled coordinates
     * @param y      panel top in GUI-scaled coordinates
     * @param width  panel width in GUI-scaled coordinates
     * @param height panel height in GUI-scaled coordinates
     * @param radius rounded-corner radius in GUI-scaled coordinates
     * @param alpha  overall fade alpha (0..1, follows the panel open animation)
     * @return true only if the rounded blur quad was actually submitted
     */
    public static boolean render(Matrix4f pose, float x, float y, float width, float height, float radius, float alpha) {
        return render(pose, x, y, width, height, radius, alpha, 1.0F);
    }

    public static boolean render(Matrix4f pose, float x, float y, float width, float height, float radius, float alpha, float strength) {
        // prepass_probe: 分段计时（诊断，只在 debug=on 且非 measureOnly 取时钟）。两者皆否则完全静默、产品行为不变。
        com.atom.chat.config.AtomChatConfig probeCfg = com.atom.chat.config.AtomChatConfig.get();
        final boolean probeOn = com.atom.chat.diagnostics.Truth3Probe.instrumentEnabled(
                probeCfg.debug, com.atom.chat.diagnostics.Truth3Probe.get().measureOnly());
        long ppM0 = probeOn ? System.nanoTime() : 0L;   // ⭐ 总墙钟起点 / entry 段起点
        long ppM1 = 0L, ppM2 = 0L, ppM3 = 0L, ppM4 = 0L, ppM5 = 0L;
        long ppM6 = 0L, ppM7 = 0L, ppM8 = 0L, ppM9 = 0L;
        if (alpha <= 0.003F || width <= 0.0F || height <= 0.0F) {
            return false;
        }
        if (disabled || kawaseShader == null || roundedShader == null) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        var fb = client.getMainRenderTarget();
        double scale = client.getWindow().getGuiScale();

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
        ppM1 = probeOn ? System.nanoTime() : 0L;        // entry 段止（入口状态捕获 + 准备）

        // P0-C: glGetError() is a pipeline sync point. Full per-frame detection
        // stays under debug=on; the product default samples 1 frame in 64
        // (errorCheckDue below). A driver-level GL error persists across frames,
        // so a sampled frame still trips the permanent disable() breaker:
        // detection is degraded, not removed.
        boolean checkErrors = errorCheckDue();

        // Drop errors other mods left in the queue so the ones seen below are
        // actually ours. Paired with the exit drain (same gate).
        if (checkErrors) {
            drainGlErrors();
        }

        try {
            int fbHeight = fb.height;
            int hCrop = Math.max(0, Math.min(ph, fbHeight - py));
            boolean clipped = hCrop < ph;
            ppM2 = probeOn ? System.nanoTime() : 0L;    // capture 段起
            ensureTextures(pw, ph, clipped);
            ppM3 = probeOn ? System.nanoTime() : 0L;    // capture 段止（ensureTextures + 容量逻辑）
            uvX = (float) pw / texW;
            uvY = (float) ph / texH;
            if (clipped) {
                // Exact path: keep the original viewport / UV / no extension so
                // an off-screen panel bottom keeps its existing behaviour.
                vpW = pw; vpH = hCrop; tmx = 1.0F; tmy = 1.0F;
            } else {
                // Capacity path: viewport = capacity, quad/UV limited to the
                // captured sub-region; the 1:1 texel mapping is preserved.
                vpW = texW; vpH = texH; tmx = uvX; tmy = uvY;
            }
            ppM4 = probeOn ? System.nanoTime() : 0L;    // passes 段起
            int blurTex = refreshBlur(fb.frameBufferId, fbHeight, px, py, pw, ph, strength,
                    restoreFbo, restoreViewport, restoreScissor);
            ppM5 = probeOn ? System.nanoTime() : 0L;    // passes 段止（refreshBlur 合计：blit+5pass+尾部恢复）
            if (blurTex == -1) {
                return false;
            }
            ppM6 = probeOn ? System.nanoTime() : 0L;    // composite 段起
            if (!drawRoundedQuad(pose, x, y, width, height, radius, alpha, blurTex)) {
                ppM7 = probeOn ? System.nanoTime() : 0L;
                return false;
            }
            ppM7 = probeOn ? System.nanoTime() : 0L;    // composite 段止（合成/上屏）
            // A silent driver-level failure would otherwise leave the panel
            // tinted as if the blur had landed. Report it once and answer "no
            // blur" so drawPanel keeps the solid background instead of showing
            // whatever the broken pass wrote. P0-C: gated, see checkErrors.
            if (checkErrors) {
                int glError = drainGlErrors();
                if (glError != 0) {
                    disable("GL error 0x" + Integer.toHexString(glError));
                    return false;
                }
            }
            return true;
        } finally {
            ppM8 = probeOn ? System.nanoTime() : 0L;    // state 段起
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, restoreFbo);
            GL30.glViewport(restoreViewport[0], restoreViewport[1], restoreViewport[2], restoreViewport[3]);
            // The raw writes above move the driver behind Blaze3D's cache-gated
            // setters; mirror every value the cache claims to own, driver
            // write first, cache setter second (the GlStateUtil rule). A cache
            // left disagreeing with the driver makes the next vanilla bind a
            // silent no-op on the wrong target.
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, restoreFbo);
            GlStateManager._viewport(restoreViewport[0], restoreViewport[1], restoreViewport[2], restoreViewport[3]);
            if (restoreScissor) {
                GL30.glEnable(GL30.GL_SCISSOR_TEST);
                GlStateManager._enableScissorTest();
            } else {
                GL30.glDisable(GL30.GL_SCISSOR_TEST);
                GlStateManager._disableScissorTest();
            }
            ppM9 = probeOn ? System.nanoTime() : 0L;    // state 段止 / ⭐ 总墙钟止
            if (probeOn) {
                // prepass_probe: 逐段 ns 差值灌入独立探针；total 从 render 第一行到本行。
                // unaccounted = total − Σ parts，由探针逐样本钳制 ≥0 后取均值。
                com.atom.chat.diagnostics.PrepassProbe.get().record(
                        com.atom.chat.diagnostics.Truth3Probe.get().mode().ordinal(),
                        ppM9 - ppM0, ppM1 - ppM0, ppM3 - ppM2,
                        ppM5 - ppM4, ppM9 - ppM8, ppM7 - ppM6);
            }
        }
    }

    /**
     * [Mode-D / Muonium cadence, 诊断专用] 非刷新帧复用上一代模糊结果纹理：
     * 不重跑 blit + 5 个 Kawase pass，只用 lastBlurTex 与上一代 UV / 几何把圆角玻璃
     * 再合成一帧，使 blurDrawnThisFrame=true、Skia 层保持半透明 tint 而不画实色 fallback。
     *
     * <p>产品默认 A 档从不调用本方法；只有 debug=on 且切到 D 档、且本帧
     * Truth3Probe.blurRefreshDue()==false 时才进入。
     *
     * <p>降级（双重保险）：若还没有任何一代结果（首次进入 D）、纹理被重建
     * （recreated）、或 lastBlurTex 未初始化，强制走一次完整 render() 产生第一代，
     * 绝不采样未初始化 / 已销毁纹理。
     *
     * <p>GL 状态：本方法不进 render() 的 try/finally，也不改 FBO / viewport /
     * scissor；drawRoundedQuad 只改 blend / shader texture / shader，与 A 档每帧一致。
     * 因此复用帧不画脏、不残留状态，面板关闭后 MC 后续帧不受污染。
     *
     * <p>prepass_probe：复用帧灌一个 passes=0、仅 composite 有值的样本（debug 门控），
     * 使 D 档每帧一个样本、skipped=0，且 passes 均值显著低于 A。
     */
    public static boolean renderReuse(Matrix4f pose, float x, float y, float width, float height,
                                      float radius, float alpha, float strength) {
        if (alpha <= 0.003F || width <= 0.0F || height <= 0.0F) {
            return false;
        }
        // 首次进入 D / 纹理重建 / 尚无有效旧档 → 强制刷新一次（不采未初始化纹理）。
        if (lastBlurTex == -1 || recreated) {
            return render(pose, x, y, width, height, radius, alpha, strength);
        }
        com.atom.chat.config.AtomChatConfig probeCfg = com.atom.chat.config.AtomChatConfig.get();
        final boolean probeOn = com.atom.chat.diagnostics.Truth3Probe.instrumentEnabled(
                probeCfg.debug, com.atom.chat.diagnostics.Truth3Probe.get().measureOnly());
        long prM0 = probeOn ? System.nanoTime() : 0L;
        boolean ok = drawRoundedQuad(pose, x, y, width, height, radius, alpha, lastBlurTex);
        long prM1 = probeOn ? System.nanoTime() : 0L;
        if (probeOn) {
            com.atom.chat.diagnostics.PrepassProbe.get().record(
                    com.atom.chat.diagnostics.Truth3Probe.get().mode().ordinal(),
                    prM1 - prM0, 0L, 0L, 0L, 0L, prM1 - prM0);
        }
        return ok;
    }

    // P0-C: glGetError() is a pipeline sync point (pure CPU cost, no GPU
    // payback). Full per-frame detection stays under debug=on; the product
    // default (debug=off) samples 1 frame in 64. A driver-level GL error is
    // persistent (same shader / FBO / texture state every frame), so a sampled
    // frame still trips the permanent disable() breaker. Detection is DEGRADED,
    // not silently removed; debug=on is lossless.
    private static int errorCheckFrame;
    private static boolean errorCheckDue() {
        if (com.atom.chat.config.AtomChatConfig.get().debug) {
            return true;
        }
        return (errorCheckFrame++ & 63) == 0;
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
        AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render", "AtomChat panel blur disabled for this session after the first failure "
                + "({}); the panel keeps its solid background"), reason);
    }

    public static boolean isAvailable() {
        // prepass_probe: 每帧（A/B/C 三档都在 drawPanel 门控之前调用本方法）推进帧计数、
        // 检测切档并重置窗口；debug=off / measureOnly 完全静默。日志行由宿主 LOGGER.info 输出。
        com.atom.chat.config.AtomChatConfig cfg = com.atom.chat.config.AtomChatConfig.get();
        if (com.atom.chat.diagnostics.Truth3Probe.instrumentEnabled(cfg.debug, com.atom.chat.diagnostics.Truth3Probe.get().measureOnly())) {
            String prepassLine = com.atom.chat.diagnostics.PrepassProbe.get().onFrame(
                    com.atom.chat.diagnostics.Truth3Probe.get().mode().ordinal(), true);
            if (prepassLine != null) AtomChat.LOGGER.info(prepassLine);
        }
        return !disabled && roundedShader != null && kawaseShader != null;
    }

    public static void resetShader() {
        // NeoForge re-registers shaders through RegisterShadersEvent; this hook
        // exists for parity with the Fabric reload listener and is intentionally
        // a no-op here (a null shader would not be lazily re-created).
    }

    private static boolean drawRoundedQuad(Matrix4f pose, float x, float y, float w, float h, float radius, float alpha, int texture) {
        ShaderInstance sh = roundedShader;
        if (sh == null) {
            return false;
        }

        var uRect = sh.safeGetUniform("u_Rect");
        var uRadius = sh.safeGetUniform("u_Radius");
        var uFlipV = sh.safeGetUniform("u_FlipV");

        float poseScale = Math.abs(pose.m00());
        Vector4f center = pose.transform(new Vector4f(x + w / 2.0F, y + h / 2.0F, 0.0F, 1.0F));
        uRect.set(center.x(), center.y(), w / 2.0F * poseScale, h / 2.0F * poseScale);
        uRadius.set(Math.min(radius, Math.min(w, h) / 2.0F) * poseScale);
        // GL textures are bottom-up; the shader flips V so GUI top-left is texture top.
        uFlipV.set(1.0F);

        // Hand the next consumer (Skia flush, then the world pass) the blend
        // bit this frame arrived with, on refresh frames and skipped frames
        // alike: an unconditional disable made the frames that ran Kawase
        // passes and the frames that reused the sticky texture disagree on a
        // global bit, alternating with the refresh cadence itself.
        boolean blendWasOn = GL11C.glIsEnabled(GL11C.GL_BLEND);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(() -> sh);

        // UV normalised by actual/capacity with V-flip compensation: the quad's
        // V is 1-(1-v)*uvY, so after u_FlipV=1 the sampled V is (1-v)*uvY, landing
        // exactly on the captured sub-region for both exact (uvX=uvY=1) and
        // capacity modes. Exact mode reproduces the original 0..1 UV verbatim.
        float vTop = 1.0F - uvY;
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.addVertex(pose, x, y, 0).setUv(0.0F, vTop).setColor(1.0F, 1.0F, 1.0F, alpha);
        bb.addVertex(pose, x, y + h, 0).setUv(0.0F, 1.0F).setColor(1.0F, 1.0F, 1.0F, alpha);
        bb.addVertex(pose, x + w, y + h, 0).setUv(uvX, 1.0F).setColor(1.0F, 1.0F, 1.0F, alpha);
        bb.addVertex(pose, x + w, y, 0).setUv(uvX, vTop).setColor(1.0F, 1.0F, 1.0F, alpha);
        BufferUploader.drawWithShader(bb.buildOrThrow());

        if (blendWasOn) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
        return true;
    }

    /**
     * Keep the three ping-pong textures alive across the open/close animation.
     *
     * <p>The capture rect's width/height change every frame while the ZOOM
     * animation runs (openScale 0.94 -> 1.0). Reallocating on every size change
     * costs far more than the five blur passes (offscreen probe: ~1.6-2.1 ms
     * wall / 0.45-0.65 ms GPU per rebuild vs ~0.11-0.27 ms for the whole blur).
     * Instead, allocate a power-of-two capacity once and grow it only when the
     * capture outgrows it. Each frame blits into the (0,0)-(w,h) sub-region and
     * the Kawase/rounded quads sample only that sub-region, so texel spacing,
     * sampling position, radius, clipping and alpha are unchanged.
     *
     * <p>When the panel bottom is clipped by the framebuffer (h_crop &lt; h),
     * the original renderer deliberately left an unwritten band and sampled it
     * through CLAMP_TO_EDGE over the full ph-high texture. That corner case is
     * behaviourally different from the capacity path, so it falls back to the
     * exact-size path: no capacity reuse, no extension, original viewport/UV.
     * It is rare and its size is stable, so it does not reintroduce the
     * per-frame rebuild this method exists to remove.
     */
    private static void ensureTextures(int w, int h, boolean clipped) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        boolean wantCapacity = !clipped;
        boolean wasCapacity = capacityMode;
        boolean need;
        int nw, nh;
        if (wantCapacity) {
            need = (inputFbo == -1) || !wasCapacity || (w > capW) || (h > capH);
            nw = Math.max(nextPow2(w), wasCapacity ? capW : 0);
            nh = Math.max(nextPow2(h), wasCapacity ? capH : 0);
        } else {
            need = (inputFbo == -1) || wasCapacity || (w != texW) || (h != texH);
            nw = w;
            nh = h;
        }
        if (!need) {
            return;
        }
        capacityMode = wantCapacity;
        destroyTextures();
        recreated = true;
        com.atom.chat.diagnostics.FrameSegments.count(com.atom.chat.diagnostics.FrameSegments.Counter.BLUR_RESOURCE);
        texW = nw;
        texH = nh;
        if (wantCapacity) {
            capW = nw;
            capH = nh;
        }
        allocate(nw, nh);
    }

    private static int nextPow2(int v) {
        int n = 1;
        while (n < v) {
            n <<= 1;
        }
        return n;
    }

    private static void allocate(int w, int h) {
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

    /**
     * CLAMP_TO_EDGE equivalent for the capacity margin: copy the captured
     * content's right column / bottom row out to the capacity border. Kawase
     * offsets that run past (w,h) then read the edge colour, exactly as the
     * original full-size texture's CLAMP_TO_EDGE did. Repeated after every pass
     * because each pass has its own edge pixels.
     */
    private static void extendEdge(int fbo, int w, int h, int capacityW, int capacityH) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
        if (capacityW > w) {
            GL30.glBlitFramebuffer(w - 1, 0, w, h, w, 0, capacityW, h,
                    GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
        }
        if (capacityH > h) {
            GL30.glBlitFramebuffer(0, h - 1, capacityW, h, 0, h, capacityW, capacityH,
                    GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
        }
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
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
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

    private static int refreshBlur(int mainFbo, int fbHeight, int x, int y, int w, int h, float strength,
                                   int restoreFbo, int[] restoreViewport, boolean restoreScissor) {
        int y2 = Math.min(y + h, fbHeight);
        if (y2 <= y) {
            return lastBlurTex;
        }
        h = y2 - y;

        // P0-C: render() already captured the external FBO / viewport / scissor
        // at the only true state boundary. From here to the tail every binding
        // is written by ourselves, so re-querying them here was a redundant
        // pipeline sync. Reuse the captured values; the tail restore (below)
        // is unchanged and still hands the external state back to MC.
        int oldFbo = restoreFbo;
        int[] viewport = restoreViewport;
        boolean scissor = restoreScissor;
        GL30.glDisable(GL30.GL_SCISSOR_TEST);
        GlStateManager._disableScissorTest();

        int glY0 = fbHeight - (y + h);
        int glY1 = fbHeight - y;

        // r15 on-demand blur: the frame slot stays, but the shared gate decides
        // whether a due slot actually runs. A still scene (camera + panel chrome)
        // drops to the <=500ms fallback cadence; any motion restores the
        // every-2-frames refresh. A denied slot simply reuses lastBlurTex.
        boolean full = true;
        if (full) {
            blit(mainFbo, x, glY0, x + w, glY1, inputFbo, 0, 0, w, h);
            if (capacityMode) {
                extendEdge(inputFbo, w, h, texW, texH);
            }

            int srcTex = inputTex;
            for (int i = 0; i < KAWASE_PASSES; i++) {
                int dstFbo = (i % 2 == 0) ? tempFbo : resultFbo;
                kawasePass(dstFbo, srcTex, vpW, vpH, capacityMode, tmx, tmy, (2.0F + i * 1.5F) * strength);
                if (capacityMode) {
                    extendEdge(dstFbo, w, h, texW, texH);
                }
                srcTex = (i % 2 == 0) ? tempTex : resultTex;
            }
            lastBlurTex = (KAWASE_PASSES % 2 == 0) ? resultTex : tempTex;
            recreated = false;
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFbo);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFbo);
        GL30.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        if (scissor) {
            GL30.glEnable(GL30.GL_SCISSOR_TEST);
            GlStateManager._enableScissorTest();
        } else {
            // Mirror the disable too: the raw disable at the top moved the
            // driver behind the cache, and a cache left claiming "enabled"
            // turns vanilla's next enableScissor into a silent no-op.
            GL30.glDisable(GL30.GL_SCISSOR_TEST);
            GlStateManager._disableScissorTest();
        }
        return lastBlurTex;
    }

    private static void kawasePass(int dstFbo, int srcTex, int viewportW, int viewportH,
                                   boolean scaled, float texMaxX, float texMaxY, float spacing) {
        ShaderInstance sh = kawaseShader;
        if (sh == null) {
            return;
        }

        var uSpacing = sh.safeGetUniform("u_Spacing");
        if (uSpacing == null) {
            return;
        }

        // P0-C: this pass binds its own FBO / viewport. The next pass, the
        // extendEdge() blit and the refreshBlur() tail all set or restore what
        // they need, so per-pass save of FBO / viewport was a redundant sync.
        // The blend query chain is KEPT (it hands the external blend bit back).
        boolean blendWasOn = GL11C.glIsEnabled(GL11C.GL_BLEND);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, dstFbo);
        GL30.glViewport(0, 0, viewportW, viewportH);

        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, srcTex);
        uSpacing.set(spacing);
        RenderSystem.setShader(() -> sh);

        // The vertex shader derives texCoord from Position (texCoord =
        // Position.xy*0.5+0.5), so scaling the quad's upper corner scales the
        // sampled UV by the same factor. With a capacity-sized viewport that
        // keeps dst pixel -> src texel 1:1 while sampling only the captured
        // sub-region. Exact mode uses the original -1..1 quad (texCoord 0..1).
        float x1 = scaled ? 2.0F * texMaxX - 1.0F : 1.0F;
        float y1 = scaled ? 2.0F * texMaxY - 1.0F : 1.0F;
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.addVertex(-1.0F, -1.0F, 0.0F);
        bb.addVertex(x1, -1.0F, 0.0F);
        bb.addVertex(x1, y1, 0.0F);
        bb.addVertex(-1.0F, y1, 0.0F);
        BufferUploader.drawWithShader(bb.buildOrThrow());

        // Same rule as drawRoundedQuad: each pass hands back the blend bit it
        // found, so five passes and zero passes leave identical global state.
        if (blendWasOn) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }

        // P0-C: no per-pass FBO / viewport restore. refreshBlur()'s tail and
        // render()'s finally restore the external state once (with the GL +
        // GlStateManager cache mirror), which is where it actually matters.
    }
}
