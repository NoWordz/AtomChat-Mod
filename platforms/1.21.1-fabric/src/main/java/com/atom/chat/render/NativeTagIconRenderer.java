package com.atom.chat.render;
import com.atom.chat.chat.NativeMessageMeta;
import com.atom.chat.chat.NativeMessageBridge;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.VertexSorter;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import java.util.*;
/** Native Icon.draw in preUi, copied to an independent immutable raster. Never samples the GUI atlas from Skia. */
public final class NativeTagIconRenderer {
    private static final Map<NativeMessageMeta.Icon,Image> CACHE=new LinkedHashMap<>(8,.75F,true);
    private static final Set<NativeMessageMeta.Icon> REQUESTS=new LinkedHashSet<>();
    private static long epoch=-1;
    private NativeTagIconRenderer(){}
    public static void request(NativeMessageMeta.Icon icon){if(icon!=null)REQUESTS.add(icon);}
    public static boolean hasRequests(){return !REQUESTS.isEmpty();}
    public static void prepare(DrawContext caller,SkiaGraphics bridge){
        if(epoch!=NativeFontRenderer.epoch()) {CACHE.values().forEach(Image::close);CACHE.clear();epoch=NativeFontRenderer.epoch();}
        var requested=List.copyOf(REQUESTS);REQUESTS.clear();if(requested.isEmpty())return;
        caller.draw();
        bridge.withContext(context->{context.resetGLAll();context.flushAndSubmit(false);return null;});
        for(var descriptor:requested)if(!CACHE.containsKey(descriptor)) {
            var icon=NativeMessageBridge.icon(descriptor);if(icon==null)continue;
            int width=Math.max(1,icon.width)*4,height=Math.max(1,icon.height)*4;
            int texture=0,framebuffer=0;
            var pixels=MemoryUtil.memAlloc(width*height*4);
            int pack=GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
            int[] fields={GL11C.GL_PACK_ALIGNMENT,GL11C.GL_PACK_ROW_LENGTH,GL11C.GL_PACK_SKIP_ROWS,GL11C.GL_PACK_SKIP_PIXELS};
            int[] previous=new int[fields.length];for(int i=0;i<fields.length;i++)previous[i]=GL11C.glGetInteger(fields[i]);
            try(ItemIconRenderer.NativeState state=new ItemIconRenderer.NativeState()) {
                texture=GL11C.glGenTextures();framebuffer=GL30C.glGenFramebuffers();
                RenderSystem.activeTexture(GL13C.GL_TEXTURE0);GlStateManager._bindTexture(texture);
                GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER,0);
                GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D,0,GL11C.GL_RGBA8,width,height,0,GL11C.GL_RGBA,GL11C.GL_UNSIGNED_BYTE,0L);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MIN_FILTER,GL11C.GL_NEAREST);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MAG_FILTER,GL11C.GL_NEAREST);
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER,framebuffer);
                GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER,GL30C.GL_COLOR_ATTACHMENT0,GL11C.GL_TEXTURE_2D,texture,0);
                if(GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER)!=GL30C.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete native tag framebuffer");
                RenderSystem.viewport(0,0,width,height);RenderSystem.disableScissor();RenderSystem.disableDepthTest();
                GL11C.glDisable(GL11C.GL_STENCIL_TEST);RenderSystem.colorMask(true,true,true,true);
                GL11C.glClearColor(0,0,0,0);GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
                RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
                ItemIconModelView.pushIdentity();
                try {
                    RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,icon.width,icon.height,0,-1000,1000),VertexSorter.BY_Z);
                    RenderSystem.resetTextureMatrix();RenderSystem.setShaderColor(1,1,1,1);
                    var client=MinecraftClient.getInstance();var gui=new DrawContext(client,client.getBufferBuilders().getEntityVertexConsumers());
                    icon.draw(gui,0,0);gui.draw();
                } finally {ItemIconModelView.pop();}
                GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER,0);
                for(int i=0;i<fields.length;i++)GL11C.glPixelStorei(fields[i],i==0?1:0);
                GL11C.glReadPixels(0,0,width,height,GL11C.GL_RGBA,GL11C.GL_UNSIGNED_BYTE,pixels);
                byte[] rgba=new byte[width*height*4];
                for(int row=0;row<height;row++)for(int x=0;x<width*4;x++)rgba[row*width*4+x]=pixels.get((height-1-row)*width*4+x);
                var image=Image.makeRaster(new ImageInfo(width,height,ColorType.RGBA_8888,ColorAlphaType.PREMUL),rgba,width*4);
                if(CACHE.size()>=16){var first=CACHE.entrySet().iterator();first.next().getValue().close();first.remove();}
                CACHE.put(descriptor,image);
            } finally {
                if(framebuffer!=0)GL30C.glDeleteFramebuffers(framebuffer);
                if(texture!=0)GlStateManager._deleteTexture(texture);
                GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER,pack);
                for(int i=0;i<fields.length;i++)GL11C.glPixelStorei(fields[i],previous[i]);
                MemoryUtil.memFree(pixels);
            }
        }
    }
    public static void draw(Canvas canvas,NativeMessageMeta.Icon icon,float x,float y,float size){
        Image image=CACHE.get(icon);if(image==null)return;
        canvas.drawImageRect(image,Rect.makeWH(image.getWidth(),image.getHeight()),Rect.makeXYWH(x,y,size,size),SamplingMode.DEFAULT,null,true);
    }
}
