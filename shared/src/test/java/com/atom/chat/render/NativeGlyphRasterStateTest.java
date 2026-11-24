package com.atom.chat.render;

import com.atom.chat.util.GlTestEnvironment;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class NativeGlyphRasterStateTest {
    @Test void ownedRedChannelReadbackPreservesPixelsAndCallerFramebufferAndTransferState() {
        GlTestEnvironment.requireWindowContext();
        assertTrue(GLFW.glfwInit());
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=GLFW.glfwCreateWindow(32,32,"native glyph readback",0,0);
        assertNotEquals(0,window);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            if(!RenderSystem.isOnRenderThread())RenderSystem.initRenderThread();
            int framebuffer=GL30C.glGenFramebuffers(),pack=GL15C.glGenBuffers(),unpack=GL15C.glGenBuffers();
            int texture=GL11C.glGenTextures();
            try {
                GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER,framebuffer);
                GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER,pack);GL15C.glBufferData(GL21C.GL_PIXEL_PACK_BUFFER,128,GL15C.GL_STREAM_READ);
                GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER,unpack);GL15C.glBufferData(GL21C.GL_PIXEL_UNPACK_BUFFER,128,GL15C.GL_STREAM_DRAW);
                GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT,8);GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH,7);
                GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT,8);GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH,7);
                NativeGlyphUpload.bind(texture);
                byte[] alpha={0,51,102,(byte)153,(byte)204,(byte)255};
                var source=new NativeGlyphRaster.Source(){
                    public int width(){return 2;}public int height(){return 3;}public boolean colored(){return false;}
                    public float left(){return 0;}public float right(){return 2;}public float top(){return 0;}public float bottom(){return 3;}
                    public void upload(){
                        var bytes=MemoryUtil.memAlloc(alpha.length);
                        try{bytes.put(alpha).flip();GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D,0,0,0,2,3,GL11C.GL_RED,GL11C.GL_UNSIGNED_BYTE,bytes);}
                        finally{MemoryUtil.memFree(bytes);}
                    }
                };
                try(var raster=new NativeGlyphRaster(source);var surface=Surface.makeRasterN32Premul(4,4);var pixels=new Bitmap()){
                    raster.prepare();assertTrue(raster.prepared());
                    assertEquals(framebuffer,GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING));
                    assertEquals(0,GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING));
                    assertEquals(pack,GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING));
                    assertEquals(unpack,GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING));
                    assertEquals(texture,GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D));
                    assertEquals(8,GL11C.glGetInteger(GL11C.GL_PACK_ALIGNMENT));assertEquals(7,GL11C.glGetInteger(GL11C.GL_PACK_ROW_LENGTH));
                    assertEquals(8,GL11C.glGetInteger(GL11C.GL_UNPACK_ALIGNMENT));assertEquals(7,GL11C.glGetInteger(GL11C.GL_UNPACK_ROW_LENGTH));
                    surface.getCanvas().clear(0);raster.draw(surface.getCanvas(),0,7,1,-1,false,false);
                    pixels.allocN32Pixels(4,4);assertTrue(surface.readPixels(pixels,0,0));
                    for(int y=0;y<3;y++)for(int x=0;x<2;x++)assertEquals(alpha[y*2+x]&255,pixels.getColor(x,y)>>>24);
                    assertEquals(GL11C.GL_NO_ERROR,GL11C.glGetError());
                }
            }finally{
                GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER,0);GL30C.glDeleteFramebuffers(framebuffer);
                GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER,0);GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER,0);
                GL15C.glDeleteBuffers(pack);GL15C.glDeleteBuffers(unpack);NativeGlyphUpload.bind(0);GL11C.glDeleteTextures(texture);
                GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT,4);GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH,0);
                GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT,4);GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH,0);
            }
        }finally{GL.setCapabilities(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
}
