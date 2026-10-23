package com.atom.chat.render;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Captures and restores the OpenGL state around Skia rendering.
 * Ported from the Tuui mod's GlStateUtil (Kotlin -> Java).
 *
 * <p>Every value is written straight to the driver <em>and</em> mirrored through
 * {@link GlStateManager}, because Blaze3D keeps process-wide caches for blend /
 * depth / cull / scissor / colour mask / active texture unit / per-unit texture
 * bindings and its setters are cache-gated: an equal cached value means no
 * driver call at all. Skia and the raw-GL blur pass move the driver behind that
 * cache, so restoring only one side of the pair leaves the game believing in
 * state the driver does not have — the 1.20.1 "open the panel and the world
 * goes black" report.</p>
 */
public final class GlStateUtil {
    /**
     * The {@code GL_BLEND_COLOR} query enum. LWJGL 3.3.1 exposes
     * {@code glBlendColor} but not this enum (checked in GL11C / GL14 / GL14C),
     * so the value is spelled out. It has been 0x8005 since GL 1.4 and is not
     * going to move.
     */
    private static final int GL_BLEND_COLOR = 0x8005;

    private static State savedState;

    private GlStateUtil() {
    }

    public static void save() {
        savedState = State.capture();
    }

    public static void restore() {
        if (savedState == null) {
            throw new IllegalStateException("GlStateUtil.restore() called without save()");
        }
        savedState.restore();
    }

    /**
     * Mirrors the active texture unit into Blaze3D's cache. The setter is
     * cache-gated and its per-unit binding array only covers the first
     * {@link GlStateManager#TEXTURE_COUNT} units, so an out-of-range unit is left
     * to the raw GL writes rather than parked in the cache, where a later
     * {@code _bindTexture} would index past the array.
     */
    private static void mirrorActiveTexture(int texture) {
        if (texture >= GL13C.GL_TEXTURE0 && texture < GL13C.GL_TEXTURE0 + GlStateManager.TEXTURE_COUNT) {
            GlStateManager._activeTexture(texture);
        }
    }

    private static final class State {
        private final boolean blendEnabled;
        private final int blendSrcRgb;
        private final int blendDstRgb;
        private final int blendSrcAlpha;
        private final int blendDstAlpha;
        private final boolean depthTestEnabled;
        private final boolean depthMask;
        private final int depthFunc;
        private final boolean cullEnabled;
        private final int cullFace;
        private final int activeTexture;
        private final int[] textureBindings2D;
        private final int[] samplerBindings;
        private final int program;
        private final int vaoBinding;
        private final boolean colorMaskR;
        private final boolean colorMaskG;
        private final boolean colorMaskB;
        private final boolean colorMaskA;
        private final int unpackAlignment;
        private final int pixelUnpackBufferBinding;
        private final boolean scissorTestEnabled;
        private final int[] scissorBox;
        // Not cached by Blaze3D, but they still have to be put back: the blur
        // pre-pass binds panel-sized FBOs of its own, Skia flushes through the
        // main framebuffer, and glStorePixel() zeroes the UNPACK_ROW_LENGTH /
        // SKIP_* values before Skia runs (only UNPACK_ALIGNMENT used to be
        // restored).
        private final int framebuffer;
        private final int readFramebuffer;
        private final int[] viewport;
        private final int unpackRowLength;
        private final int unpackSkipPixels;
        private final int unpackSkipRows;
        // Blaze3D does not track these three, so there is no cache to mirror and
        // the raw call is the whole restore (same situation as the cull mode
        // above). Skia's GL backend does move them: Ganesh sets the blend
        // equation per blend mode, the blend colour for some modes, and flips
        // the front face when it renders into a flipped render target. A leaked
        // front face or blend equation shows up as "another mod's model renders
        // inside out / with the wrong alpha", never as an error.
        private final int blendEquationRgb;
        private final int blendEquationAlpha;
        private final float[] blendColor;
        private final int frontFace;
        // The stencil family gets its own holder: it is 15 values that must all
        // be saved or none of them, and inlining them would double the length of
        // an already long parameter list.
        private final Stencil stencil;

        private State(boolean blendEnabled, int blendSrcRgb, int blendDstRgb, int blendSrcAlpha, int blendDstAlpha,
                      boolean depthTestEnabled, boolean depthMask, int depthFunc, boolean cullEnabled, int cullFace,
                      int activeTexture, int[] textureBindings2D, int[] samplerBindings, int program, int vaoBinding,
                      boolean colorMaskR, boolean colorMaskG, boolean colorMaskB, boolean colorMaskA,
                      int unpackAlignment, int pixelUnpackBufferBinding, boolean scissorTestEnabled, int[] scissorBox,
                      int framebuffer, int readFramebuffer, int[] viewport, int unpackRowLength, int unpackSkipPixels, int unpackSkipRows,
                      int blendEquationRgb, int blendEquationAlpha, float[] blendColor, int frontFace,
                      Stencil stencil) {
            this.blendEnabled = blendEnabled;
            this.blendSrcRgb = blendSrcRgb;
            this.blendDstRgb = blendDstRgb;
            this.blendSrcAlpha = blendSrcAlpha;
            this.blendDstAlpha = blendDstAlpha;
            this.depthTestEnabled = depthTestEnabled;
            this.depthMask = depthMask;
            this.depthFunc = depthFunc;
            this.cullEnabled = cullEnabled;
            this.cullFace = cullFace;
            this.activeTexture = activeTexture;
            this.textureBindings2D = textureBindings2D;
            this.samplerBindings = samplerBindings;
            this.program = program;
            this.vaoBinding = vaoBinding;
            this.colorMaskR = colorMaskR;
            this.colorMaskG = colorMaskG;
            this.colorMaskB = colorMaskB;
            this.colorMaskA = colorMaskA;
            this.unpackAlignment = unpackAlignment;
            this.pixelUnpackBufferBinding = pixelUnpackBufferBinding;
            this.scissorTestEnabled = scissorTestEnabled;
            this.scissorBox = scissorBox;
            this.framebuffer = framebuffer;
            this.readFramebuffer = readFramebuffer;
            this.viewport = viewport;
            this.unpackRowLength = unpackRowLength;
            this.unpackSkipPixels = unpackSkipPixels;
            this.unpackSkipRows = unpackSkipRows;
            this.blendEquationRgb = blendEquationRgb;
            this.blendEquationAlpha = blendEquationAlpha;
            this.blendColor = blendColor;
            this.frontFace = frontFace;
            this.stencil = stencil;
        }

        void restore() {
            // Driver write first, cache-gated setter second. When the cache is
            // already right the setter no-ops and the raw write has corrected the
            // driver anyway; when the cache is stale the setter fires and puts the
            // cache back on the truth. Either path ends with both in sync, which
            // is the whole point: a cache left disagreeing with the driver makes
            // every later vanilla/Embeddium bind land on the wrong unit.

            if (blendEnabled) {
                GL11C.glEnable(GL11C.GL_BLEND);
                GlStateManager._enableBlend();
            } else {
                GL11C.glDisable(GL11C.GL_BLEND);
                GlStateManager._disableBlend();
            }
            GL14C.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            GlStateManager._blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);

            if (depthTestEnabled) {
                GL11C.glEnable(GL11C.GL_DEPTH_TEST);
                GlStateManager._enableDepthTest();
            } else {
                GL11C.glDisable(GL11C.GL_DEPTH_TEST);
                GlStateManager._disableDepthTest();
            }
            GL11C.glDepthMask(depthMask);
            GlStateManager._depthMask(depthMask);
            GL11C.glDepthFunc(depthFunc);
            GlStateManager._depthFunc(depthFunc);

            if (cullEnabled) {
                GL11C.glEnable(GL11C.GL_CULL_FACE);
                GlStateManager._enableCull();
            } else {
                GL11C.glDisable(GL11C.GL_CULL_FACE);
                GlStateManager._disableCull();
            }
            // 1.20.1 exposes no cached setter for the cull *mode* (GlStateManager
            // only tracks the capability), so the raw call is the full restore.
            GL11C.glCullFace(cullFace);

            // Blaze3D only tracks the first TEXTURE_COUNT units (12 in 1.20.1)
            // while the driver usually exposes 32, so the units past that get the
            // raw write only: mirroring them would walk its binding array out of
            // bounds on the next _bindTexture.
            int mirroredUnits = Math.min(textureBindings2D.length, GlStateManager.TEXTURE_COUNT);
            for (int i = 0; i < textureBindings2D.length; i++) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + i);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textureBindings2D[i]);
                GL33C.glBindSampler(i, samplerBindings[i]);
                if (i < mirroredUnits) {
                    // Re-point the caches at what the driver now holds. The unit
                    // has to be mirrored before the binding: _bindTexture indexes
                    // TEXTURES[] by the cached active unit.
                    GlStateManager._activeTexture(GL13C.GL_TEXTURE0 + i);
                    GlStateManager._bindTexture(textureBindings2D[i]);
                }
            }
            GL13C.glActiveTexture(activeTexture);
            mirrorActiveTexture(activeTexture);

            GL20C.glUseProgram(program);
            GL30C.glBindVertexArray(vaoBinding);
            GL11C.glColorMask(colorMaskR, colorMaskG, colorMaskB, colorMaskA);
            GlStateManager._colorMask(colorMaskR, colorMaskG, colorMaskB, colorMaskA);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, unpackAlignment);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, unpackRowLength);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, unpackSkipRows);
            GL21C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, pixelUnpackBufferBinding);

            if (scissorTestEnabled) {
                GL11C.glEnable(GL11C.GL_SCISSOR_TEST);
                GlStateManager._enableScissorTest();
            } else {
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                GlStateManager._disableScissorTest();
            }
            GL11C.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);

            GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
            GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);

            GL20C.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
            // Same rule as the texture units below: Blaze3D's setter takes one
            // mode for both channels, so mirroring a split equation would plant a
            // value that is wrong for one of them.
            if (blendEquationRgb == blendEquationAlpha) {
                GlStateManager._blendEquation(blendEquationRgb);
            }
            GL14C.glBlendColor(blendColor[0], blendColor[1], blendColor[2], blendColor[3]);
            GL11C.glFrontFace(frontFace);
            stencil.restore();
        }

        static State capture() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                int maxTextureUnits = GL11C.glGetInteger(GL20C.GL_MAX_TEXTURE_IMAGE_UNITS);
                int[] textureBindings = new int[maxTextureUnits];
                int[] samplerBindings = new int[maxTextureUnits];
                int activeTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);

                for (int i = 0; i < maxTextureUnits; i++) {
                    // Raw switch on purpose: the cached active unit can already be
                    // wrong (an earlier frame may have left it that way), and
                    // sampling through it would read the wrong unit's binding.
                    GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + i);
                    textureBindings[i] = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
                    samplerBindings[i] = GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
                }
                GL13C.glActiveTexture(activeTexture);
                mirrorActiveTexture(activeTexture);

                ByteBuffer colorMask = stack.malloc(4);
                GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, colorMask);

                IntBuffer scissorBox = stack.mallocInt(4);
                GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, scissorBox);
                IntBuffer viewport = stack.mallocInt(4);
                GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);

                boolean blendEnabled = GL11C.glIsEnabled(GL11C.GL_BLEND);
                int blendSrcRgb = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB);
                int blendDstRgb = GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB);
                int blendSrcAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA);
                int blendDstAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA);

                boolean depthTestEnabled = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
                boolean depthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
                int depthFunc = GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC);
                boolean cullEnabled = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
                int cullFace = GL11C.glGetInteger(GL11C.GL_CULL_FACE_MODE);

                int program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
                int vaoBinding = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
                int unpackAlignment = GL11C.glGetInteger(GL11C.GL_UNPACK_ALIGNMENT);
                int unpackRowLength = GL11C.glGetInteger(GL11C.GL_UNPACK_ROW_LENGTH);
                int unpackSkipPixels = GL11C.glGetInteger(GL11C.GL_UNPACK_SKIP_PIXELS);
                int unpackSkipRows = GL11C.glGetInteger(GL11C.GL_UNPACK_SKIP_ROWS);
                int pixelUnpackBufferBinding = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
                boolean scissorTestEnabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
                int framebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
                int readFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
                int blendEquationRgb = GL11C.glGetInteger(GL20C.GL_BLEND_EQUATION_RGB);
                int blendEquationAlpha = GL11C.glGetInteger(GL20C.GL_BLEND_EQUATION_ALPHA);
                FloatBuffer blendColor = stack.mallocFloat(4);
                GL11C.glGetFloatv(GL_BLEND_COLOR, blendColor);
                int frontFace = GL11C.glGetInteger(GL11C.GL_FRONT_FACE);

                return new State(
                        blendEnabled, blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha,
                        depthTestEnabled, depthMask, depthFunc, cullEnabled, cullFace,
                        activeTexture, textureBindings, samplerBindings, program, vaoBinding,
                        colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0,
                        unpackAlignment, pixelUnpackBufferBinding, scissorTestEnabled,
                        new int[]{scissorBox.get(0), scissorBox.get(1), scissorBox.get(2), scissorBox.get(3)},
                        framebuffer, readFramebuffer, new int[]{viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3)},
                        unpackRowLength, unpackSkipPixels, unpackSkipRows,
                        blendEquationRgb, blendEquationAlpha,
                        new float[]{blendColor.get(0), blendColor.get(1), blendColor.get(2), blendColor.get(3)},
                        frontFace, Stencil.capture()
                );
            }
        }
    }

    /**
     * The stencil buffer's state, saved and restored as one family.
     *
     * <p>Why it is worth restoring: Ganesh reaches for the stencil buffer on
     * clip shapes a scissor rectangle cannot express, and it is not obliged to
     * hand {@code GL_STENCIL_TEST} back the way it found it. A leak here does
     * not raise an error — it makes everything drawn afterwards obey someone
     * else's mask, which in a modded client means ModelEngine/Embeddium passes
     * losing pixels for no visible reason.
     *
     * <p>Why front and back are read separately: GL allows two-sided stencil,
     * while Blaze3D's setters take a single value. The cache is therefore only
     * mirrored when both faces agree — the same trade-off {@link #mirrorActiveTexture}
     * makes for texture units past {@code TEXTURE_COUNT}.
     */
    private static final class Stencil {
        private final boolean enabled;
        private final int funcFront;
        private final int refFront;
        private final int valueMaskFront;
        private final int writeMaskFront;
        private final int sfailFront;
        private final int dpfailFront;
        private final int dppassFront;
        private final int funcBack;
        private final int refBack;
        private final int valueMaskBack;
        private final int writeMaskBack;
        private final int sfailBack;
        private final int dpfailBack;
        private final int dppassBack;

        private Stencil(boolean enabled,
                        int funcFront, int refFront, int valueMaskFront, int writeMaskFront,
                        int sfailFront, int dpfailFront, int dppassFront,
                        int funcBack, int refBack, int valueMaskBack, int writeMaskBack,
                        int sfailBack, int dpfailBack, int dppassBack) {
            this.enabled = enabled;
            this.funcFront = funcFront;
            this.refFront = refFront;
            this.valueMaskFront = valueMaskFront;
            this.writeMaskFront = writeMaskFront;
            this.sfailFront = sfailFront;
            this.dpfailFront = dpfailFront;
            this.dppassFront = dppassFront;
            this.funcBack = funcBack;
            this.refBack = refBack;
            this.valueMaskBack = valueMaskBack;
            this.writeMaskBack = writeMaskBack;
            this.sfailBack = sfailBack;
            this.dpfailBack = dpfailBack;
            this.dppassBack = dppassBack;
        }

        void restore() {
            if (enabled) {
                GL11C.glEnable(GL11C.GL_STENCIL_TEST);
            } else {
                GL11C.glDisable(GL11C.GL_STENCIL_TEST);
            }
            GL20C.glStencilFuncSeparate(GL11C.GL_FRONT, funcFront, refFront, valueMaskFront);
            GL20C.glStencilFuncSeparate(GL11C.GL_BACK, funcBack, refBack, valueMaskBack);
            GL20C.glStencilOpSeparate(GL11C.GL_FRONT, sfailFront, dpfailFront, dppassFront);
            GL20C.glStencilOpSeparate(GL11C.GL_BACK, sfailBack, dpfailBack, dppassBack);
            GL20C.glStencilMaskSeparate(GL11C.GL_FRONT, writeMaskFront);
            GL20C.glStencilMaskSeparate(GL11C.GL_BACK, writeMaskBack);
            if (funcFront == funcBack && refFront == refBack && valueMaskFront == valueMaskBack) {
                GlStateManager._stencilFunc(funcFront, refFront, valueMaskFront);
            }
            if (sfailFront == sfailBack && dpfailFront == dpfailBack && dppassFront == dppassBack) {
                GlStateManager._stencilOp(sfailFront, dpfailFront, dppassFront);
            }
            if (writeMaskFront == writeMaskBack) {
                GlStateManager._stencilMask(writeMaskFront);
            }
        }

        static Stencil capture() {
            return new Stencil(
                    GL11C.glIsEnabled(GL11C.GL_STENCIL_TEST),
                    GL11C.glGetInteger(GL11C.GL_STENCIL_FUNC),
                    GL11C.glGetInteger(GL11C.GL_STENCIL_REF),
                    GL11C.glGetInteger(GL11C.GL_STENCIL_VALUE_MASK),
                    GL11C.glGetInteger(GL11C.GL_STENCIL_WRITEMASK),
                    GL11C.glGetInteger(GL11C.GL_STENCIL_FAIL),
                    GL11C.glGetInteger(GL11C.GL_STENCIL_PASS_DEPTH_FAIL),
                    GL11C.glGetInteger(GL11C.GL_STENCIL_PASS_DEPTH_PASS),
                    GL11C.glGetInteger(GL20C.GL_STENCIL_BACK_FUNC),
                    GL11C.glGetInteger(GL20C.GL_STENCIL_BACK_REF),
                    GL11C.glGetInteger(GL20C.GL_STENCIL_BACK_VALUE_MASK),
                    GL11C.glGetInteger(GL20C.GL_STENCIL_BACK_WRITEMASK),
                    GL11C.glGetInteger(GL20C.GL_STENCIL_BACK_FAIL),
                    GL11C.glGetInteger(GL20C.GL_STENCIL_BACK_PASS_DEPTH_FAIL),
                    GL11C.glGetInteger(GL20C.GL_STENCIL_BACK_PASS_DEPTH_PASS));
        }
    }
}
