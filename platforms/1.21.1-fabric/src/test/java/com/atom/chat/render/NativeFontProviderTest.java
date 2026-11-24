package com.atom.chat.render;
import net.minecraft.client.font.FontLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.Resource;
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
    // JUnit has no Mixin transformer. These two test subclasses implement only the generated accessors;
    // the native FontStorage setFonts/filter/cache code and all AC routing/layout/paint remain real.
    private static class Storage extends net.minecraft.client.font.FontStorage implements com.atom.chat.mixin.NativeFontStorageAccess {
        Storage(net.minecraft.client.texture.TextureManager textures){super(textures,net.minecraft.util.Identifier.of("minecraft:default"));}
        @SuppressWarnings("unchecked") private <T>T field(String name){try{var f=net.minecraft.client.font.FontStorage.class.getDeclaredField(name);f.setAccessible(true);return (T)f.get(this);}catch(Exception e){throw new AssertionError(e);}}
        public net.minecraft.util.Identifier atomchat$fontId(){return getId();}
        public List<net.minecraft.client.font.Font.FontFilterPair> atomchat$allProviders(){return field("allFonts");}
        public List<net.minecraft.client.font.Font> atomchat$providers(){return field("availableFonts");}
        public it.unimi.dsi.fastutil.ints.Int2ObjectMap<it.unimi.dsi.fastutil.ints.IntList> atomchat$byWidth(){return field("charactersByWidth");}
    }
    private static final class Renderer extends net.minecraft.client.font.TextRenderer implements com.atom.chat.mixin.NativeFontAccess {
        private final Storage storage;
        int lookups;
        Renderer(Storage storage){super(id->storage,false);this.storage=storage;}
        public net.minecraft.client.font.FontStorage atomchat$storage(net.minecraft.util.Identifier id){lookups++;return storage;}
    }
    private static void realMixedRender(ResourceManager resources, net.minecraft.client.font.Font bitmap) throws Exception {
        var singleton=Arrays.stream(net.minecraft.client.MinecraftClient.class.getDeclaredFields())
                .filter(f->java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType()==net.minecraft.client.MinecraftClient.class).findFirst().orElseThrow();
        singleton.setAccessible(true);Object previous=singleton.get(null);
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        var client=(net.minecraft.client.MinecraftClient)unsafe.allocateInstance(net.minecraft.client.MinecraftClient.class);
        try(var textures=new net.minecraft.client.texture.TextureManager(resources);var storage=new Storage(textures)) {
            var textureField=Arrays.stream(net.minecraft.client.MinecraftClient.class.getDeclaredFields())
                    .filter(f->f.getType()==net.minecraft.client.texture.TextureManager.class).findFirst().orElseThrow();
            textureField.setAccessible(true);textureField.set(client,textures);
            var renderer=new Renderer(storage);var fontField=net.minecraft.client.MinecraftClient.class.getDeclaredField("textRenderer");fontField.setAccessible(true);fontField.set(client,renderer);singleton.set(null,client);
            storage.setFonts(List.of(new net.minecraft.client.font.Font.FontFilterPair(bitmap,net.minecraft.client.font.FontFilterType.FilterMap.NO_FILTER)),Set.of());
            Object key=new Object();var definition=JsonParser.parseString("{\"type\":\"bitmap\",\"file\":\"test:glyph.png\",\"height\":32,\"ascent\":28,\"chars\":[\"A\"]}").getAsJsonObject();
            NativeFontProvenance.definition(key,"minecraft:default",definition);
            NativeFontProvenance.loaded(NativeFontProvenance.take(key),bitmap,resources,id->null,id->null,json->json);
            NativeFontRenderer.invalidate();
            var text=com.atom.chat.text.RichText.literal("A xyz");var font=FontManager.font(18);
            NativeFontRenderer.beginFrame();NativeFontRenderer.request(text);
            var lines=RichTextRenderer.wrapFor(text,font,300);
            assertEquals(bitmap.getGlyph('A').getAdvance(false)*2+PlainText.width(font," xyz"),RichTextRenderer.width(font,lines.get(0)),.001F);
            NativeFontRenderer.prepare();
            try(var surface=Surface.makeRasterN32Premul(340,120);var pixels=new Bitmap()) {
                RichTextRenderer.drawLines(surface.getCanvas(),font,lines,5,65,22,-1,null,false);
                pixels.allocN32Pixels(340,120);assertTrue(surface.readPixels(pixels,0,0));
                byte[] first=pixels.readPixels();assertTrue(java.util.stream.IntStream.range(0,first.length/4).anyMatch(i->(first[i*4+3]&255)>0));
                var ordinary=com.atom.chat.text.RichText.literal("ordinary unchanged history text ".repeat(8));
                NativeFontRenderer.request(ordinary);
                RichTextRenderer.wrapFor(ordinary,font,Float.MAX_VALUE);
                renderer.lookups=0;
                for(int repeat=0;repeat<100;repeat++) {
                    NativeFontRenderer.request(ordinary);
                    RichTextRenderer.width(font,RichTextRenderer.wrapFor(ordinary,font,Float.MAX_VALUE).get(0));
                }
                assertEquals(0,renderer.lookups,"warm ordinary text must reuse its routing decision");
                var storages=NativeFontRenderer.class.getDeclaredField("STORAGES");storages.setAccessible(true);
                for(int id=0;id<600;id++)NativeFontRenderer.width(font,"A",net.minecraft.text.Style.EMPTY.withFont(net.minecraft.util.Identifier.of("test:unknown_"+id)));
                int retained=((Map<?,?>)storages.get(null)).size();
                for(int id=600;id<1200;id++)NativeFontRenderer.width(font,"A",net.minecraft.text.Style.EMPTY.withFont(net.minecraft.util.Identifier.of("test:unknown_"+id)));
                assertTrue(((Map<?,?>)storages.get(null)).size()<=retained,"unknown server font IDs must not make the storage lookup cache grow indefinitely");
                firstStatusGlyph(resources,storage,font);
                evictionAndObfuscation(resources,storage,bitmap,font);
                storage.setFonts(List.of(new net.minecraft.client.font.Font.FontFilterPair(bitmap,net.minecraft.client.font.FontFilterType.FilterMap.NO_FILTER)),Set.of());
                NativeFontRenderer.invalidate();
                var before=RichTextRenderer.width(font,lines.get(0));
                try(var spaces=load("{\"type\":\"space\",\"advances\":{\"A\":-4.0}}",resources)) {
                    NativeFontRenderer.invalidate();
                    var conditional=new net.minecraft.client.font.Font.FontFilterPair(spaces,new net.minecraft.client.font.FontFilterType.FilterMap(Map.of(net.minecraft.client.font.FontFilterType.JAPANESE_VARIANTS,true)));
                    var unchanged=new net.minecraft.client.font.Font.FontFilterPair(bitmap,net.minecraft.client.font.FontFilterType.FilterMap.NO_FILTER);
                    storage.setFonts(List.of(conditional,unchanged),Set.of());
                    assertEquals(before,RichTextRenderer.width(font,RichTextRenderer.wrapFor(text,font,300).get(0)),.001F);
                    storage.setActiveFilters(Set.of(net.minecraft.client.font.FontFilterType.JAPANESE_VARIANTS));
                    NativeFontRenderer.invalidate();
                    Object replacement=new Object();var json=JsonParser.parseString("{\"type\":\"space\",\"advances\":{\"A\":-4.0}}").getAsJsonObject();
                    NativeFontProvenance.definition(replacement,"minecraft:default",json);NativeFontProvenance.loaded(NativeFontProvenance.take(replacement),spaces,resources,id->null,id->null,value->value);
                    NativeFontRenderer.beginFrame();NativeFontRenderer.request(text);NativeFontRenderer.prepare();
                    assertEquals(-8+PlainText.width(font," xyz"),RichTextRenderer.width(font,RichTextRenderer.wrapFor(text,font,300).get(0)),.001F);
                    assertNotEquals(before,RichTextRenderer.width(font,RichTextRenderer.wrapFor(text,font,300).get(0)));
                    storage.setFonts(List.of(),Set.of());
                    String stock="{\"providers\":[{\"type\":\"bitmap\",\"file\":\"test:glyph.png\",\"height\":32,\"ascent\":28,\"chars\":[\"A\"]}]}";
                    NativeFontProvenance.fontStorage(storage,"minecraft:default",List.of(),Map.of(),
                            id->id.equals("minecraft:font/default.json")?new ByteArrayInputStream(stock.getBytes(java.nio.charset.StandardCharsets.UTF_8)):null,value->value);
                    NativeFontRenderer.invalidate();NativeFontRenderer.beginFrame();NativeFontRenderer.request(com.atom.chat.text.RichText.literal("A"));NativeFontRenderer.prepare();
                    assertEquals(net.minecraft.client.font.BuiltinEmptyGlyph.MISSING.getAdvance(false)*2,
                            RichTextRenderer.width(font,RichTextRenderer.wrapFor(com.atom.chat.text.RichText.literal("A"),font,300).get(0)),.001F);
                }
            }
        } finally {NativeFontRenderer.invalidate();singleton.set(null,previous);}
    }

    private static void evictionAndObfuscation(ResourceManager resources,Storage storage,net.minecraft.client.font.Font bitmap,io.github.humbleui.skija.Font font) throws Exception {
        float width=bitmap.getGlyph('A').getAdvance(false);
        String json="{\"type\":\"space\",\"advances\":{\"B\":"+width+"}}";
        try(var space=load(json,resources)) {
            storage.setFonts(List.of(new net.minecraft.client.font.Font.FontFilterPair(bitmap,net.minecraft.client.font.FontFilterType.FilterMap.NO_FILTER),
                    new net.minecraft.client.font.Font.FontFilterPair(space,net.minecraft.client.font.FontFilterType.FilterMap.NO_FILTER)),Set.of());
            var style=net.minecraft.text.Style.EMPTY.withObfuscated(true);
            var text=com.atom.chat.text.RichText.of(net.minecraft.text.Text.literal("A").setStyle(style));
            var oldClock=ObfuscatedText.clock;
            try(var surface=Surface.makeRasterN32Premul(100,100);var pixels=new Bitmap()) {
                pixels.allocN32Pixels(100,100);byte[] previous=null;
                for(int phase=0;phase<2;phase++) {
                    final int selectedPhase=phase;ObfuscatedText.clock=()->selectedPhase;
                    NativeFontRenderer.invalidate();NativeFontRenderer.beginFrame();
                    // Simulates the HUD's true paint collection, including a departing line.
                    var lines=RichTextRenderer.wrapFor(text,font,Float.MAX_VALUE);
                    NativeFontRenderer.requestLines(lines);NativeFontRenderer.prepare();
                    // Layout-only glyph metadata churn must not discard a requested obfuscation choice.
                    var explicit=net.minecraft.text.Style.EMPTY.withFont(net.minecraft.util.Identifier.of("test:eviction"));
                    for(int cp=0x4000;cp<0x4000+4100;cp++)NativeFontRenderer.width(font,new String(Character.toChars(cp)),explicit);
                    surface.getCanvas().clear(0);NativeFontRenderer.draw(surface.getCanvas(),font,"A",4,60,-1,style,selectedPhase);
                    assertTrue(surface.readPixels(pixels,0,0));var current=pixels.readPixels();
                    if(previous!=null)assertFalse(Arrays.equals(previous,current),"new native obfuscation phase must have prepared its actual choice");
                    previous=current;NativeFontRenderer.endFrame();
                }
                // Keep a run route hot while its actual raster leaves the bounded image cache.
                NativeFontRenderer.invalidate();NativeFontRenderer.beginFrame();
                var plain=com.atom.chat.text.RichText.literal("A");NativeFontRenderer.request(plain);NativeFontRenderer.prepare();NativeFontRenderer.endFrame();
                var unknown=net.minecraft.text.Style.EMPTY.withFont(net.minecraft.util.Identifier.of("test:eviction"));
                for(int cp=0x4000;cp<0x4000+4100;cp++) {
                    NativeFontRenderer.beginFrame();
                    NativeFontRenderer.request(com.atom.chat.text.RichText.of(net.minecraft.text.Text.literal(new String(Character.toChars(cp))).setStyle(unknown)));
                    NativeFontRenderer.width(font,"A",net.minecraft.text.Style.EMPTY);NativeFontRenderer.endFrame();
                }
                NativeFontRenderer.beginFrame();NativeFontRenderer.request(plain);NativeFontRenderer.prepare();
                surface.getCanvas().clear(0);NativeFontRenderer.draw(surface.getCanvas(),font,"A",4,60,-1,net.minecraft.text.Style.EMPTY,0);
                assertTrue(surface.readPixels(pixels,0,0));assertTrue(Arrays.stream(toInts(pixels.readPixels())).anyMatch(value->value!=0),"evicted native raster must reappear on the same requested frame");
                NativeFontRenderer.endFrame();
            } finally {ObfuscatedText.clock=oldClock;NativeFontRenderer.invalidate();}
        }
    }
    private static int[] toInts(byte[] bytes) {int[] values=new int[bytes.length];for(int i=0;i<bytes.length;i++)values[i]=bytes[i]&255;return values;}

    private static void firstStatusGlyph(ResourceManager resources,Storage storage,io.github.humbleui.skija.Font font) throws Exception {
        try(var dot=load("{\"type\":\"bitmap\",\"file\":\"test:glyph.png\",\"height\":32,\"ascent\":28,\"chars\":[\"·\"]}",resources)) {
            storage.setFonts(List.of(new net.minecraft.client.font.Font.FontFilterPair(dot,net.minecraft.client.font.FontFilterType.FilterMap.NO_FILTER)),Set.of());
            Object token=new Object();var definition=JsonParser.parseString("{\"type\":\"bitmap\",\"file\":\"test:glyph.png\",\"height\":32,\"ascent\":28,\"chars\":[\"·\"]}").getAsJsonObject();
            NativeFontProvenance.definition(token,"minecraft:default",definition);
            NativeFontProvenance.loaded(NativeFontProvenance.take(token),dot,resources,id->null,id->null,json->json);
            var view=new com.atom.chat.page.MessageListView(new com.atom.chat.page.MessageListView.Host(){
                public UUID ownUuid(){return null;}public String ownName(){return "u";}
                public String senderName(com.atom.chat.chat.ChatMessage message){return "u";}public long openStart(){return 0;}
            });
            var message=new com.atom.chat.chat.ChatMessage(net.minecraft.text.Text.literal("hello"),true,false);
            var draw=com.atom.chat.page.MessageListView.class.getDeclaredMethod("drawMessageName",Canvas.class,com.atom.chat.chat.ChatMessage.class,float.class,float.class,float.class);draw.setAccessible(true);
            var scroll=new com.atom.chat.ui.ScrollController();
            try(var surface=Surface.makeRasterN32Premul(360,150);var pixels=new Bitmap()) {
                pixels.allocN32Pixels(360,150);
                for(var locale:List.of("en_us","zh_cn"))try(var language=PreviewLanguage.install(locale)) {
                for(var state:List.of(com.atom.chat.chat.OutgoingMessages.State.PENDING,com.atom.chat.chat.OutgoingMessages.State.UNCONFIRMED,com.atom.chat.chat.OutgoingMessages.State.FAILED)) {
                    message.delivery(state);NativeFontRenderer.invalidate();
                    String statusKey=com.atom.chat.render.MessagePaint.deliveryStatusKey(message);
                    String status=net.minecraft.text.Text.translatable(statusKey).getString();
                    assertNotEquals(statusKey,status,"the visual fixture must load the actual mod locale");
                    byte[] first=null;
                    for(int frame=0;frame<2;frame++) {
                        surface.getCanvas().clear(0);NativeFontRenderer.beginFrame();
                        view.requestFonts(320,140,List.of(message),scroll);NativeFontRenderer.prepare();
                        draw.invoke(view,surface.getCanvas(),message,70F,5F,340F);
                        assertTrue(surface.readPixels(pixels,0,0));
                        int colored=0;
                        for(int y=0;y<150;y++)for(int x=0;x<360;x++) {
                            int color=pixels.getColor(x,y),red=(color>>>16)&255,green=(color>>>8)&255,blue=color&255;
                            if((color>>>24)>0 && green>red*2 && blue>green)colored++;
                        }
                        assertTrue(colored>0,"the newly native middle dot must appear on the first status frame: "+locale+" / "+state);
                        if(first==null)first=pixels.readPixels();else assertArrayEquals(first,pixels.readPixels());
                        NativeFontRenderer.endFrame();
                    }
                }
                }
            } finally {view.dispose();NativeFontRenderer.invalidate();}
        }
    }

    @org.junit.jupiter.api.BeforeAll static void bootstrap(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir){
        com.atom.chat.platform.Platform.install(new com.atom.chat.platform.Platform.Provider(){
            public java.nio.file.Path configDir(){return dir;}
            public java.nio.file.Path gameDir(){return dir;}
            public boolean isModLoaded(String id){return false;}
        });net.minecraft.SharedConstants.createGameVersion(); net.minecraft.Bootstrap.initialize();}

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
    private static net.minecraft.client.font.Font load(String json,ResourceManager resources) throws Exception {
        var definition=FontLoader.CODEC.codec().parse(JsonOps.INSTANCE,JsonParser.parseString(json)).result().orElseThrow();
        return definition.build().left().orElseThrow().load(resources);
    }
    @Test void delegatedFontStorageWithEmptyVanillaProvidersKeepsChatTextLegible() throws Exception {
        GlTestEnvironment.requireWindowContext();assertTrue(GLFW.glfwInit());
        GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=GLFW.glfwCreateWindow(32,64,"AC delegated font storage regression",0,0);assertNotEquals(0,window);
        var singleton=Arrays.stream(net.minecraft.client.MinecraftClient.class.getDeclaredFields())
                .filter(f->java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType()==net.minecraft.client.MinecraftClient.class).findFirst().orElseThrow();
        singleton.setAccessible(true);Object previous=singleton.get(null);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            if(!RenderSystem.isOnRenderThread())RenderSystem.initRenderThread();
            var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
            var client=(net.minecraft.client.MinecraftClient)unsafe.allocateInstance(net.minecraft.client.MinecraftClient.class);
            boolean[] delegated={true};
            try(var textures=new net.minecraft.client.texture.TextureManager(resources(Map.of()));var storage=new Storage(textures) {
                // Modern UI's StandardFontSet also keeps the superclass provider lists empty.
                // Its public glyph lookup is backed by a separate font collection, without a vanilla raster recipe.
                @Override public net.minecraft.client.font.Glyph getGlyph(int cp,boolean validate) {
                    if(!delegated[0])return super.getGlyph(cp,validate);
                    return new net.minecraft.client.font.Glyph() {
                        public float getAdvance(){return cp=='-'?0:cp=='+'?-4:6.75F;}
                        public net.minecraft.client.font.GlyphRenderer bake(java.util.function.Function<net.minecraft.client.font.RenderableGlyph,net.minecraft.client.font.GlyphRenderer> baker) {
                            throw new AssertionError("A delegated glyph has no vanilla upload recipe");
                        }
                    };
                }
            }) {
                storage.setFonts(List.of(),Set.of());
                assertTrue(storage.atomchat$providers().isEmpty());
                var stock="{\"providers\":[{\"type\":\"space\",\"advances\":{\" \":4}}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                NativeFontProvenance.fontStorage(storage,"minecraft:default",List.of(),Map.of(),
                        id->id.equals("minecraft:font/default.json")?new ByteArrayInputStream(stock):null,value->value);
                var renderer=new Renderer(storage);var field=net.minecraft.client.MinecraftClient.class.getDeclaredField("textRenderer");field.setAccessible(true);field.set(client,renderer);singleton.set(null,client);
                var font=FontManager.font(20);String text="聊天测试 E33EPUS 0123456789 +-";
                for(var style:List.of(net.minecraft.text.Style.EMPTY,net.minecraft.text.Style.EMPTY.withFont(net.minecraft.util.Identifier.of("test:delegated")))) {
                    NativeFontRenderer.invalidate();NativeFontRenderer.beginFrame();
                    for(int cp:text.codePoints().toArray())assertFalse(NativeFontRenderer.hasNative(style,cp),"empty private provider lists must not fabricate missing glyphs: "+cp);
                    var rich=com.atom.chat.text.RichText.of(net.minecraft.text.Text.literal(text).setStyle(style));
                    NativeFontRenderer.request(rich);NativeFontRenderer.prepare();
                    assertEquals(PlainText.width(font,text),NativeFontRenderer.width(font,text,style),.001F);
                    try(var actual=Surface.makeRasterN32Premul(640,80);var expected=Surface.makeRasterN32Premul(640,80);var pixels=new Bitmap();var oracle=new Bitmap()) {
                        NativeFontRenderer.draw(actual.getCanvas(),font,text,8,50,-1,style,0);
                        PlainText.draw(expected.getCanvas(),font,text,8,50,-1);
                        pixels.allocN32Pixels(640,80);oracle.allocN32Pixels(640,80);
                        assertTrue(actual.readPixels(pixels,0,0));assertTrue(expected.readPixels(oracle,0,0));
                        assertArrayEquals(oracle.readPixels(),pixels.readPixels(),"delegated storage must preserve the original AC text pixels");
                        try(var snapshot=actual.makeImageSnapshot();var png=snapshot.encodeToData(EncodedImageFormat.PNG)) {
                            var evidence=java.nio.file.Path.of("../../build/v096-font-tofu/delegated-text-after.png");java.nio.file.Files.createDirectories(evidence.getParent());java.nio.file.Files.write(evidence,png.getBytes());
                        }
                    }
                    NativeFontRenderer.endFrame();
                }
                // The same empty superclass storage must still retain true resource-removal semantics.
                delegated[0]=false;NativeFontRenderer.invalidate();
                assertTrue(NativeFontRenderer.hasNative(net.minecraft.text.Style.EMPTY,'A'));
                assertEquals(net.minecraft.client.font.BuiltinEmptyGlyph.MISSING.getAdvance(false)*font.getSize()/9F,
                        NativeFontRenderer.width(font,"A",net.minecraft.text.Style.EMPTY),.001F);
                delegated[0]=true;NativeFontRenderer.invalidate();
                assertFalse(NativeFontRenderer.hasNative(net.minecraft.text.Style.EMPTY,'A'));
                assertEquals(PlainText.width(font,"A"),NativeFontRenderer.width(font,"A",net.minecraft.text.Style.EMPTY),.001F);
                try(var space=load("{\"type\":\"space\",\"advances\":{\"A\":6.75}}",resources(Map.of()))) {
                    storage.setFonts(List.of(new net.minecraft.client.font.Font.FontFilterPair(space,net.minecraft.client.font.FontFilterType.FilterMap.NO_FILTER)),Set.of());
                    var definition=JsonParser.parseString("{\"type\":\"space\",\"advances\":{\"A\":6.75}}").getAsJsonObject();Object key=new Object();
                    NativeFontProvenance.definition(key,"minecraft:default",definition);
                    NativeFontProvenance.loaded(NativeFontProvenance.take(key),space,resources(Map.of()),id->null,id->null,value->value);
                    storage.atomchat$byWidth().put(7,new it.unimi.dsi.fastutil.ints.IntArrayList(new int[]{'B'}));
                    NativeFontRenderer.invalidate();NativeFontRenderer.beginFrame();
                    var obfuscated=net.minecraft.text.Style.EMPTY.withObfuscated(true);
                    var rich=com.atom.chat.text.RichText.of(net.minecraft.text.Text.literal("A").setStyle(obfuscated));
                    assertTrue(NativeFontRenderer.hasNative(obfuscated,'A'));
                    NativeFontRenderer.request(rich);NativeFontRenderer.prepare();
                    try(var surface=Surface.makeRasterN32Premul(80,80)) {
                        NativeFontRenderer.draw(surface.getCanvas(),font,"A",8,50,-1,obfuscated,0);
                    }
                    NativeFontRenderer.endFrame();
                }
            }
        } finally {NativeFontRenderer.invalidate();singleton.set(null,previous);GL.setCapabilities(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
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
                realMixedRender(resources(files),bitmap);
                var glyph=bitmap.getGlyph('A');assertNotNull(glyph);
                assertEquals(-4F,space.getGlyph('-').getAdvance(false));assertEquals(0F,space.getGlyph('0').getAdvance(false));
                assertNull(NativeGlyphSource.capture(space.getGlyph('-')));
                try(var raster=NativeGlyphSource.capture(glyph);var surface=Surface.makeRasterN32Premul(32,64);var pixels=new Bitmap()) {
                    assertFalse(raster.prepared());raster.prepare();assertTrue(raster.prepared());
                    raster.draw(surface.getCanvas(),2,36,1,-1,false,false);
                    pixels.allocN32Pixels(32,64);assertTrue(surface.readPixels(pixels,0,0));
                    long colored=0;for(int i=3;i<pixels.readPixels().length;i+=4)if((pixels.readPixels()[i]&255)>0)colored++;
                    assertEquals(0xFF28B4E6,pixels.getColor(4,12));
                    try(var snapshot=surface.makeImageSnapshot();var pngData=snapshot.encodeToData(EncodedImageFormat.PNG)) {
                        java.nio.file.Path evidence=java.nio.file.Path.of("../../build/v096/worker-font-final/native-1.21.1-fabric-first.png");
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
