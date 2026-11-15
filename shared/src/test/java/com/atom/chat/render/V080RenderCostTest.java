package com.atom.chat.render;

import com.atom.chat.ui.ChatShortcutHint;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.AtomLibRuntime;
import com.atom.lib.client.render.FramebufferTarget;
import com.atom.lib.client.render.SkijaRenderBridge;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.PictureRecorder;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL33C.*;

/** Production AC components through the installed AtomLib API; excludes Minecraft/world work. */
class V080RenderCostTest {
    @Test void separatesBridgeHudAndDots() {
        assumeTrue("1".equals(System.getenv("ATOMCHAT_BENCH")),"explicit hidden GL measurement");
        assertTrue(GLFW.glfwInit());
        GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(64,64,"AC/Lib render cost",0,0);
        assertNotEquals(0,window);
        GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
        int texture=glGenTextures(),depth=glGenRenderbuffers(),fbo=glGenFramebuffers();
        try {
            int w=2560,h=1600;
            glBindTexture(GL_TEXTURE_2D,texture);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
            glBindRenderbuffer(GL_RENDERBUFFER,depth);glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH24_STENCIL8,w,h);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_STENCIL_ATTACHMENT,GL_RENDERBUFFER,depth);
            assertEquals(GL_FRAMEBUFFER_COMPLETE,glCheckFramebufferStatus(GL_FRAMEBUFFER));
            var target=new FramebufferTarget(new Object(),fbo,texture,w,h);
            System.out.println("V080 GL renderer="+glGetString(GL_RENDERER));
            try(var runtime=AtomLibRuntime.open("ac-v080-render-cost");var bridge=new SkijaRenderBridge(runtime)) {
                Consumer<Canvas> empty=c->{};
                Consumer<Canvas> hud=c->ChatShortcutHint.renderGuiScaled(c,w,h,1,5,"Enter",1);
                Consumer<Canvas> dots=c->{
                    DotTexture.drawEdge(c,new UiLayout.Rect(25,100,700,70),30,true,0xFFFFFFFF);
                    DotTexture.drawEdge(c,new UiLayout.Rect(25,1400,700,100),30,false,0xFFFFFFFF);
                };
                // The production reference is recorded once too; do not compare Java circles
                // every frame with native Picture replay and call that a renderer gain.
                DotTexture.setDotsCached(false);
                try(var recorder=new PictureRecorder()) {
                    dots.accept(recorder.beginRecording(Rect.makeWH(w,h)));
                    try(var reference=recorder.finishRecordingAsPicture()) {
                DotTexture.setDotsCached(true);DotTexture.clearCache();
                for(float scale:new float[]{1F,1.3F,.94F}) {
                    Consumer<Canvas> before=c->{c.save();c.scale(scale,scale);reference.playback(c);c.restore();};
                    Consumer<Canvas> after=c->{c.save();c.scale(scale,scale);dots.accept(c);c.restore();};
                    assertArrayEquals(pixels(bridge,target,before),pixels(bridge,target,after),"GPU pixels at scale "+scale);
                }
                for(int round=0;round<2;round++) {
                    measure(bridge,target,"bridge",empty);
                    measure(bridge,target,"hud",hud);
                    if(round==0) {
                        measure(bridge,target,"dots-picture-reference",reference::playback);
                        measure(bridge,target,"dots-batched",dots);
                    } else {
                        measure(bridge,target,"dots-batched",dots);
                        measure(bridge,target,"dots-picture-reference",reference::playback);
                    }
                }
                    }
                }
            }
            assertEquals(GL_NO_ERROR,glGetError());
        } finally {
            glDeleteFramebuffers(fbo);glDeleteRenderbuffers(depth);glDeleteTextures(texture);
            GL.setCapabilities(null);GLFW.glfwMakeContextCurrent(0);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
    }

    private static byte[] pixels(SkijaRenderBridge bridge,FramebufferTarget target,Consumer<Canvas> paint) {
        bridge.drawFrame(target,(canvas,frame)->{canvas.clear(0xFF365446);paint.accept(canvas);});
        glBindFramebuffer(GL_READ_FRAMEBUFFER,target.framebuffer());glBindBuffer(GL_PIXEL_PACK_BUFFER,0);
        glPixelStorei(GL_PACK_ALIGNMENT,1);glPixelStorei(GL_PACK_ROW_LENGTH,0);
        var bytes=org.lwjgl.BufferUtils.createByteBuffer(target.width()*target.height()*4);
        glReadPixels(0,0,target.width(),target.height(),GL_RGBA,GL_UNSIGNED_BYTE,bytes);
        byte[] result=new byte[bytes.capacity()];bytes.get(result);return result;
    }

    private static void measure(SkijaRenderBridge bridge,FramebufferTarget target,String name,Consumer<Canvas> paint) {
        long[] cpu=new long[120],gpu=new long[120];
        int query=glGenQueries();
        try {
            for(int i=-32;i<cpu.length;i++) {
                glBindFramebuffer(GL_FRAMEBUFFER,target.framebuffer());glViewport(0,0,target.width(),target.height());
                glDisable(GL_SCISSOR_TEST);glClearColor(.2f,.3f,.25f,1);glClear(GL_COLOR_BUFFER_BIT);glFinish();
                glBeginQuery(GL_TIME_ELAPSED,query);
                long start=System.nanoTime();
                bridge.drawFrame(target,(canvas,frame)->paint.accept(canvas));
                long elapsed=System.nanoTime()-start;
                glEndQuery(GL_TIME_ELAPSED);
                long completed=glGetQueryObjecti64(query,GL_QUERY_RESULT);
                if(i>=0){cpu[i]=elapsed;gpu[i]=completed;}
            }
        } finally {glDeleteQueries(query);}
        Arrays.sort(cpu);Arrays.sort(gpu);
        System.out.printf(Locale.ROOT,"V080 cost=%s cpu_p50=%.4f cpu_p95=%.4f gpu_p50=%.4f gpu_p95=%.4f ms%n",
                name,cpu[59]/1e6,cpu[113]/1e6,gpu[59]/1e6,gpu[113]/1e6);
    }
}
