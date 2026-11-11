package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.*;
import com.atom.lib.design.atomchat.*;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class BottomDotLayerProbeTest {
    @Test void isolateTheOneChannelDifferenceWithoutChangingTolerance()throws Exception {
        var instance=AtomChatConfig.class.getDeclaredField("instance");instance.setAccessible(true);Object old=instance.get(null);
        var provider=Platform.class.getDeclaredField("provider");provider.setAccessible(true);Object oldProvider=provider.get(null);
        Path tmp=Files.createTempDirectory("dot-layers");
        try {
            Platform.install(new Platform.Provider(){public Path configDir(){return tmp;}public Path gameDir(){return tmp;}public boolean isModLoaded(String id){return false;}});
            instance.set(null,new AtomChatConfig());
            var chat=new UiLayout.Rect(30,420,480,110);var nav=new UiLayout.Rect(30,460,480,70);
            try(var chrome=new BottomChromeTransition()) {
                chrome.beginFrame(BottomChromeTransition.Mode.CHAT,chat,nav,1000,true);
                chrome.beginFrame(BottomChromeTransition.Mode.NAV,chat,nav,1010,true);
                chrome.beginFrame(BottomChromeTransition.Mode.HIDDEN,chat,nav,1310,true);
                chrome.beginFrame(BottomChromeTransition.Mode.HIDDEN,chat,nav,1370,true);
                var shape=chrome.paintedShape();var bar=chrome.paintedRect();
                float radius=Math.min(UiTokens.shellChromeRadius(),Math.min(bar.w(),bar.h())/2);
                System.out.println("SHAPE "+shape+" pad="+UiTokens.floatSurfaceShadowPad()+" dots="+UiTokens.MESSAGE_DOT_DEPTH);
                byte[][] pixels=new byte[6][];
                for(int mode=0;mode<6;mode++)try(var surface=Surface.makeRasterN32Premul(560,620);var alpha=new Paint().setAlphaf(shape.alpha());var bitmap=new Bitmap()) {
                    var c=surface.getCanvas();c.clear(0xFF414851);
                    float pad=Math.max(UiTokens.floatSurfaceShadowPad(),UiTokens.MESSAGE_DOT_DEPTH);
                    c.saveLayer(mode%2==0?null:Rect.makeXYWH(shape.x()-pad,shape.y()-pad,shape.w()+2*pad,shape.h()+2*pad),alpha);
                    if(mode<2)DotTexture.drawEdge(c,bar,radius,true,AtomChatConfig.get().textSecondaryColor);
                    else if(mode<4)DotTexture.drawMovingEdge(c,bar,radius,true,AtomChatConfig.get().textSecondaryColor);
                    else {SkiaDraw.drawChromeShadow(c,bar.x(),bar.y(),bar.w(),bar.h(),radius);SkiaDraw.drawRoundedRect(c,bar.x(),bar.y(),bar.w(),bar.h(),radius,UiTokens.cardFill());ChromeMaterial.drawDetails(c,bar,radius);}
                    c.restore();bitmap.allocN32Pixels(560,620);surface.readPixels(bitmap,0,0);pixels[mode]=bitmap.readPixels();
                    Path out=Path.of(System.getProperty("atomchat.repoRoot","../.."),".work/v0.7.0-feedback/layer-probe");Files.createDirectories(out);
                    try(var image=surface.makeImageSnapshot();var data=image.encodeToData()){Files.write(out.resolve(mode+".png"),data.getBytes());}
                }
                for(int[] pair:new int[][]{{0,1},{2,3},{0,2},{1,3},{4,5}}) {
                    int max=0,count=0,first=-1;
                    for(int i=0;i<pixels[0].length;i++) {int d=Math.abs((pixels[pair[0]][i]&255)-(pixels[pair[1]][i]&255));if(d>0){count++;if(first<0)first=i;}max=Math.max(max,d);}
                    System.out.println("PAIR "+pair[0]+"/"+pair[1]+" max="+max+" bytes="+count+" first="+first);
                    if(pair[0]<4)assertArrayEquals(pixels[pair[0]],pixels[pair[1]],"dot primitives and clipping must remain exact");
                }
            }
        } finally {instance.set(null,old);provider.set(null,oldProvider);}
    }
}
