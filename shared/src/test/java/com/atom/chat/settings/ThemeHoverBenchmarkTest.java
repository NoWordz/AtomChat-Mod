package com.atom.chat.settings;

import com.atom.chat.platform.Platform;
import com.atom.chat.theme.AppearancePresetStore;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeHoverBenchmarkTest {
    @Test void reportActualThemeRowWorkInsideAndOutside(@TempDir Path dir) throws Exception {
        var provider=Platform.class.getDeclaredField("provider"); provider.setAccessible(true); Object old=provider.get(null);
        var store=AppearancePresetStore.class.getDeclaredField("shared"); store.setAccessible(true); Object oldStore=store.get(null);
        Platform.install(new Platform.Provider(){public Path configDir(){return dir;} public Path gameDir(){return dir;} public boolean isModLoaded(String id){return false;}});
        try {
            Files.createDirectories(dir.resolve("presets")); AppearancePresetStore.init(dir.resolve("presets"));
            var page=new SettingsSectionPage(); var px=SettingsSectionPage.class.getDeclaredField("pointerX"); px.setAccessible(true);
            var py=SettingsSectionPage.class.getDeclaredField("pointerY"); py.setAccessible(true);
            var draw=SettingsSectionPage.class.getDeclaredMethod("drawThemes",Canvas.class,UiLayout.Rect.class,float.class); draw.setAccessible(true);
            var rect=new UiLayout.Rect(10,10,500,200);
            try(var surface=Surface.makeRasterN32Premul(600,300)) {
                for(boolean inside:new boolean[]{false,true}) {
                    px.setFloat(page,inside?450:-100); py.setFloat(page,inside?80:-100);
                    for(int i=0;i<10;i++)draw.invoke(page,surface.getCanvas(),rect,16F);
                    long start=System.nanoTime();
                    for(int i=0;i<40;i++) {
                        if(inside && i%10==0) page.wheelThemeStrip(-1F,rect);
                        draw.invoke(page,surface.getCanvas(),rect,16F);
                    }
                    System.out.printf("theme-row inside=%s mean_ms=%.3f%n",inside,(System.nanoTime()-start)/40_000_000.0);
                }
            }
        } finally { store.set(null,oldStore); provider.set(null,old); }
    }
}
