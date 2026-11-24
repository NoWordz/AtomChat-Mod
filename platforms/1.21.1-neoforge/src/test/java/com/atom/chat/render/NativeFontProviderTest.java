package com.atom.chat.render;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.Resource;
import com.atom.chat.font.*;
import com.atom.chat.util.GlTestEnvironment;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class NativeFontProviderTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir){
        com.atom.chat.platform.Platform.install(new com.atom.chat.platform.Platform.Provider(){
            public java.nio.file.Path configDir(){return dir;}
            public java.nio.file.Path gameDir(){return dir;}
            public boolean isModLoaded(String id){return false;}
        });net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();}

    private static ResourceManager resources(Map<String,byte[]> files) {
        return (ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class<?>[]{ResourceManager.class},(p,m,a)->{
            String name=m.getName();
            if(name.equals("hashCode"))return System.identityHashCode(p);
            if(name.equals("equals"))return p==a[0];
            if(name.equals("toString"))return "native-provider-fixture";
            byte[] bytes=a!=null && a.length>0 ? files.get(a[0].toString()) : null;
            if(name.equals("open")) {if(bytes==null)throw new FileNotFoundException(a[0].toString());return new ByteArrayInputStream(bytes);}
            if(name.equals("getResource") || name.equals("getResourceOrThrow")) {
                var resource=bytes==null ? null : new Resource(null,()->new ByteArrayInputStream(bytes));
                if(name.equals("getResourceOrThrow"))return Objects.requireNonNull(resource);
                return Optional.ofNullable(resource);
            }
            throw new UnsupportedOperationException(name);
        });
    }
    private static com.mojang.blaze3d.font.GlyphProvider load(String json,ResourceManager resources) throws Exception {
        var definition=GlyphProviderDefinition.MAP_CODEC.codec().parse(JsonOps.INSTANCE,JsonParser.parseString(json)).result().orElseThrow();
        return definition.unpack().left().orElseThrow().load(resources);
    }
    @Test void actualProvidersUploadTheirOwnPixelsOnFirstPrepareAndAfterResourceReplacement() throws Exception {
        GlTestEnvironment.requireWindowContext();assertTrue(GLFW.glfwInit());
        GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=GLFW.glfwCreateWindow(32,64,"AC native font provider verification",0,0);assertNotEquals(0,window);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            if(!RenderSystem.isOnRenderThread())RenderSystem.initRenderThread();
            var files=new HashMap<String,byte[]>();
            var png=new BufferedImage(16,32,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<32;y++)for(int x=0;x<16;x++)png.setRGB(x,y,0xFF28B4E6);
            var output=new ByteArrayOutputStream();ImageIO.write(png,"png",output);files.put("test:textures/glyph.png",output.toByteArray());
            try(var bitmap=load("{\"type\":\"bitmap\",\"file\":\"test:glyph.png\",\"height\":32,\"ascent\":28,\"chars\":[\"A\"]}",resources(files));
                var space=load("{\"type\":\"space\",\"advances\":{\"-\":-4.0,\"0\":0.0}}",resources(files))) {
                var glyph=bitmap.getGlyph('A');assertNotNull(glyph);
                assertEquals(-4F,space.getGlyph('-').getAdvance(false));assertEquals(0F,space.getGlyph('0').getAdvance(false));
                assertNull(NativeGlyphSource.capture(space.getGlyph('-')));
                try(var raster=NativeGlyphSource.capture(glyph);var surface=Surface.makeRasterN32Premul(32,64);var pixels=new Bitmap()) {
                    assertFalse(raster.prepared());raster.prepare();assertTrue(raster.prepared());
                    raster.draw(surface.getCanvas(),2,36,1,-1,false,false);
                    pixels.allocN32Pixels(32,64);assertTrue(surface.readPixels(pixels,0,0));
                    long colored=0;for(int i=3;i<pixels.readPixels().length;i+=4)if((pixels.readPixels()[i]&255)>0)colored++;
                    try(var snapshot=surface.makeImageSnapshot();var pngData=snapshot.encodeToData(EncodedImageFormat.PNG)) {
                        java.nio.file.Path evidence=java.nio.file.Path.of("../../build/v096/worker-font-final/native-1.21.1-neoforge-first.png");
                        java.nio.file.Files.createDirectories(evidence.getParent());java.nio.file.Files.write(evidence,pngData.getBytes());
                    }
                    assertEquals(16*32,colored,"tall bitmap must retain all ink on the first frame");
                    assertEquals(-28F,raster.source().top()-7F,.001F);
                }
            }
            var zipBytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(zipBytes)){zip.putNextEntry(new ZipEntry("glyph.hex"));zip.write(("0042:"+"FF".repeat(16)+"\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));zip.closeEntry();}
            files.put("test:font/glyph.zip",zipBytes.toByteArray());
            try(var unihex=load("{\"type\":\"unihex\",\"hex_file\":\"test:font/glyph.zip\",\"size_overrides\":[]}",resources(files));var raster=NativeGlyphSource.capture(unihex.getGlyph('B'))) {
                assertNotNull(raster);raster.prepare();assertTrue(raster.prepared());assertEquals(.5F,unihex.getGlyph('B').getBoldOffset(),.001F);
            }
            files.put("test:font/native.otf",com.atom.lib.font.FontSource.resource("/com/atom/lib/fonts/bundled-default.otf").read());
            try(var ttf=load("{\"type\":\"ttf\",\"file\":\"test:native.otf\",\"size\":13,\"oversample\":2,\"shift\":[1,2],\"skip\":\"Z\"}",resources(files))) {
                assertNull(ttf.getGlyph('Z'));assertNotNull(ttf.getGlyph('A'));
                try(var raster=NativeGlyphSource.capture(ttf.getGlyph('A'))){raster.prepare();assertTrue(raster.prepared());}
            }
        } finally {GL.setCapabilities(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
}
