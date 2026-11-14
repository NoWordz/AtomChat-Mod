package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConfirmationPrimaryColorTest {
    @TempDir Path tmp;
    @Test void secondaryActionsHaveAVisibleNeutralGreyFace() {
        try(var surface=Surface.makeRasterN32Premul(300,160);var bitmap=new Bitmap()) {
            surface.getCanvas().clear(0xFF20242E);
            var rect=new UiLayout.Rect(20,20,200,80);
            new ConfirmationSurface.Button().draw(surface.getCanvas(),rect,"",false,false,false,false,1000,1);
            bitmap.allocN32Pixels(300,160);assertTrue(surface.readPixels(bitmap,0,0));
            int pixel=bitmap.getColor(120,45),red=(pixel>>>16)&255,green=(pixel>>>8)&255,blue=pixel&255;
            assertTrue(red>=65&&red<=100,"secondary face needs a distinguishable grey, not the almost-black plate");
            assertTrue(Math.abs(red-green)<12&&Math.abs(green-blue)<12);
        }
    }
    @Test void tutorialPrimaryFollowsThemeAccent() {
        Platform.install(new Platform.Provider(){public Path configDir(){return tmp.resolve("config");}public Path gameDir(){return tmp;}public boolean isModLoaded(String id){return false;}});
        int old=AtomChatConfig.get().accentColor;AtomChatConfig.get().accentColor=0xFF35B893;
        try(var surface=Surface.makeRasterN32Premul(800,1000);var bitmap=new Bitmap()) {
            var dialog=new ConfirmationDialog();dialog.openTutorial("Continue","Preview only","Cancel","Keep",()->{},()->{});
            dialog.render(surface.getCanvas(),new UiLayout.Rect(0,0,800,1000),-100,-100,1000);
            var r=dialog.geometry().confirm();bitmap.allocN32Pixels(800,1000);assertTrue(surface.readPixels(bitmap,0,0));
            int color=bitmap.getColor((int)(r.x()+r.w()/2),(int)(r.y()+r.h()*.2f));
            assertTrue(((color>>8)&255)>((color>>16)&255),"tutorial primary must retain the approved theme accent: "+Integer.toHexString(color));
        } finally {AtomChatConfig.get().accentColor=old;}
    }
}
