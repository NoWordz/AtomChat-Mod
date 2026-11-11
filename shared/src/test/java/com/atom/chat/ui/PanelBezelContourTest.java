package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.RRect;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class PanelBezelContourTest {
    @Test void backgroundAndBezelNeverExposeAnInteriorGapAndWidthIsNotHalved() throws Exception {
        var provider=Platform.class.getDeclaredField("provider");provider.setAccessible(true);var oldProvider=provider.get(null);
        var field=AtomChatConfig.class.getDeclaredField("instance");field.setAccessible(true);var oldConfig=field.get(null);
        var root=Files.createTempDirectory("bezel-contour");
        try {
            Platform.install(new Platform.Provider(){public java.nio.file.Path configDir(){return root;}public java.nio.file.Path gameDir(){return root;}public boolean isModLoaded(String id){return false;}});
            field.set(null,new AtomChatConfig());
            var config=AtomChatConfig.get();config.halfScreenMode=false;config.panelOutlineColor=0xFFFF0000;
            for(float width:new float[]{0,1,2,3,4}) {
                config.setPanelOutlineWidth(width);
                try(var actual=Surface.makeRasterN32Premul(180,220);var reference=Surface.makeRasterN32Premul(180,220);
                    var fill=new Paint().setColor(0x8000FF00).setAntiAlias(true)) {
                    var rect=new UiLayout.Rect(20,20,140,180);
                    var shape=RRect.makeXYWH(rect.x(),rect.y(),rect.w(),rect.h(),UiTokens.panelRadius());
                    actual.getCanvas().clear(0);reference.getCanvas().clear(0);
                    actual.getCanvas().drawRRect(shape,fill);reference.getCanvas().drawRRect(shape,fill);
                    PanelBackground.drawBezel(actual.getCanvas(),rect);
                    byte[] a=pixels(actual),r=pixels(reference);
                    for(int i=3;i<a.length;i+=4) if((r[i]&255)>=127) assertTrue((a[i]&255)>=126,"interior alpha gap at width "+width);
                    if(width==3) {
                        float physical=UiTokens.s(width);
                        int redPixels=0;
                        for(int y=20;y<35;y++) {int index=(y*180+90)*4; if((a[index+2]&255)>200)redPixels++;}
                        assertTrue(redPixels>=Math.floor(physical)&&redPixels<=Math.ceil(physical),"3 logical pixels must occupy scaled full stroke width, actual "+redPixels);
                        System.out.println("outline: 3 logical px = "+physical+" scaled px, opaque core = "+redPixels+" raster rows");
                    }
                }
            }
        } finally {provider.set(null,oldProvider);field.set(null,oldConfig);}
    }
    private static byte[] pixels(Surface surface) {
        try(var bitmap=new Bitmap()){bitmap.allocN32Pixels(180,220);assertTrue(surface.readPixels(bitmap,0,0));return bitmap.readPixels();}
    }
}
