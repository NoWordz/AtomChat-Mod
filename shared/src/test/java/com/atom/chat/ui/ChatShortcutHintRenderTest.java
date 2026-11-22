package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ChatShortcutHintRenderTest {
    @Test void performanceModeLeavesOnlyTheWhiteIconAndItsThreeHoles() throws Exception {
        var field = AtomChatConfig.class.getDeclaredField("instance");
        field.setAccessible(true);
        var previous = field.get(null);
        var config = new AtomChatConfig();
        config.performanceMode = true;
        field.set(null, config);
        try (var surface = Surface.makeRasterN32Premul(180, 180); var pixels = new Bitmap()) {
            var canvas = surface.getCanvas();
            canvas.clear(0);
            ChatShortcutHint.renderGuiScaled(canvas, 180, 180, 1, 3, "Y", 1);
            pixels.allocN32Pixels(180, 180);
            assertTrue(surface.readPixels(pixels, 0, 0));
            assertEquals(0xFFFFFFFF, pixels.getColor(34, 139));
            for (int dot : new int[]{30, 38, 45}) assertEquals(0, pixels.getColor(dot, 139));
            for (int y = 120; y < 166; y++) {
                for (int x = 13; x < 59; x++) {
                    int pixel = pixels.getColor(x, y);
                    assertTrue((pixel >>> 24) == 0 || (pixel & 0xFFFFFF) == 0xFFFFFF,
                            "dark pixel around the white shortcut icon at " + x + "," + y);
                }
            }
        } finally {
            ChatShortcutHint.release();
            field.set(null, previous);
        }
    }

    @Test void solidWhiteIconHasThreeRealHolesAndUsesOnlyVanillaScale(@TempDir Path tmp) throws Exception {
        var provider=Platform.class.getDeclaredField("provider");provider.setAccessible(true);
        Object previous=provider.get(null);
        Platform.install(new Platform.Provider() {
            public Path configDir(){return tmp;} public Path gameDir(){return tmp;}
            public boolean isModLoaded(String id){return false;}
        });
        try {
            byte[] reference=null;
            for(float density:new float[]{1,2}) {
                try(var surface=Surface.makeRasterN32Premul(640,180);var pixels=new Bitmap()) {
                    var canvas=surface.getCanvas();canvas.clear(0);canvas.scale(density,density);
                    ChatShortcutHint.renderGuiScaled(canvas,640/density,180/density,density,3,"Y",1);
                    pixels.allocN32Pixels(640,180);assertTrue(surface.readPixels(pixels,0,0));
                    assertEquals(0,pixels.getColor(30,139)>>>24);
                    assertEquals(0,pixels.getColor(38,139)>>>24);
                    assertEquals(0,pixels.getColor(45,139)>>>24);
                    assertEquals(0xFFFFFFFF,pixels.getColor(34,139),"the face between holes is solid white");
                    assertTrue((pixels.getColor(15,139)>>>24)>0,"a soft shadow must extend outside the icon");
                    if(reference==null) reference=pixels.readPixels();
                    else assertArrayEquals(reference,pixels.readPixels(),"panel density must not resize the HUD");
                }
            }
            Path out=Path.of(System.getProperty("atomchat.repoRoot","../.."),"build/v080-followup/visual");Files.createDirectories(out);
            for(int scale:new int[]{1,2,3,5}) {
                try(var surface=Surface.makeRasterN32Premul(640,180);var pixels=new Bitmap()) {
                    var canvas=surface.getCanvas();canvas.clear(0);
                    ChatShortcutHint.renderGuiScaled(canvas,640,180,1,scale,"Y",1);
                    pixels.allocN32Pixels(640,180);assertTrue(surface.readPixels(pixels,0,0));
                    int minX=640;byte[] bytes=pixels.readPixels();
                    for(int y=0;y<180;y++) for(int x=0;x<640;x++) if((bytes[(y*640+x)*4+3]&255)>=128) minX=Math.min(minX,x);
                    assertEquals(6*scale,minX,"vanilla GUI scale controls the HUD margin");
                    canvas.clear(0xFF365446);
                    ChatShortcutHint.renderGuiScaled(canvas,640,180,1,scale,"Y",1);
                    try(var image=surface.makeImageSnapshot();var png=image.encodeToData()) {
                        Files.write(out.resolve("hud-gui-scale-"+scale+".png"),png.getBytes());
                    }
                }
            }
        } finally { provider.set(null,previous); }
    }
}
