package com.atom.chat.fun;

import com.atom.chat.util.GlTestEnvironment;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import static org.junit.jupiter.api.Assertions.*;

/** Iris/Bliss writes display RGB with unspecified alpha; reproduce that through raw GL, not Skia clear. */
class ExperimentalFramebufferAlphaTest {
    @Test void actualFullscreenPlaybackDoesNotInterpretWorldAlphaAsImageCoverage() {
        GlTestEnvironment.requireWindowContext();
        assertTrue(GLFW.glfwInit());GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS,8);
        long window=GLFW.glfwCreateWindow(160,90,"AtomChat display alpha regression",0,0);assertNotEquals(0,window);
        GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
        try(var context=DirectContext.makeGL();var target=BackendRenderTarget.makeGL(160,90,0,8,0,GL11C.GL_RGBA8);
            var surface=Surface.wrapBackendRenderTarget(context,target,SurfaceOrigin.BOTTOM_LEFT,ColorType.RGBA_8888,ColorSpace.getDisplayP3())) {
            assertNotNull(surface);
            for(var type:ExperimentalEffectType.values())for(float progress:new float[]{.02f,.3f,.8f,.99f}) {
                int[] reference=frame(context,surface,type,progress,1);
                for(float alpha:new float[]{0,.2f,.5f}) {
                    int[] actual=frame(context,surface,type,progress,alpha);
                    for(int pixel=0;pixel<2;pixel++)for(int channel=0;channel<3;channel++)
                        assertEquals(reference[pixel*4+channel],actual[pixel*4+channel],1,
                                type+" t="+progress+" inputAlpha="+alpha+" pixel="+pixel+" channel="+channel);
                    assertEquals(255,actual[3],"resolved display RGB is opaque regardless of world alpha");
                    assertEquals(255,actual[7],"opaque UI must remain opaque");
                }
            }
            assertEquals(0,GL11C.glGetError());
        } finally {GL.setCapabilities(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    private static int[] frame(DirectContext context,Surface surface,ExperimentalEffectType type,float progress,float alpha) {
        context.resetAll();GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER,0);
        GL11C.glDisable(GL11C.GL_SCISSOR_TEST);GL11C.glColorMask(true,true,true,true);
        GL11C.glClearColor(.2f,.5f,.8f,alpha);GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
        GL11C.glEnable(GL11C.GL_SCISSOR_TEST);GL11C.glScissor(0,0,45,90);
        GL11C.glClearColor(.8f,.2f,.1f,1);GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
        try(var effects=new ExperimentalEffects(type!=ExperimentalEffectType.IGNITION)) {
            effects.advance(true,true,100);
            if(type!=ExperimentalEffectType.IGNITION)effects.onMessage(switch(type){case WAVE->"震惊";case FROST->"冷死了";default->"黑洞";},100);
            assertEquals(type,effects.type());
            int saves=surface.getCanvas().getSaveCount();effects.render(surface,100+Math.max(1,Math.round(progress*ExperimentalEffects.DURATION_MS)));
            assertEquals(saves,surface.getCanvas().getSaveCount());context.flushAndSubmit(surface);GL11C.glFinish();
        }
        int[] pixels=new int[8];
        try(var stack=MemoryStack.stackPush()) {
            var bytes=stack.malloc(4);
            GL11C.glReadPixels(120,65,1,1,GL11C.GL_RGBA,GL11C.GL_UNSIGNED_BYTE,bytes);
            for(int c=0;c<4;c++)pixels[c]=bytes.get(c)&255;
            GL11C.glReadPixels(15,20,1,1,GL11C.GL_RGBA,GL11C.GL_UNSIGNED_BYTE,bytes);
            for(int c=0;c<4;c++)pixels[4+c]=bytes.get(c)&255;
        }
        return pixels;
    }
}
