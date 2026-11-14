package com.atom.chat.settings;

import com.atom.chat.platform.Platform;
import com.atom.chat.theme.AppearancePresetStore;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ThemeHoverWorkTest {
    @Test void themeRowComputesHoverOncePerFrame(@TempDir Path dir) throws Exception {
        var provider=Platform.class.getDeclaredField("provider"); provider.setAccessible(true);
        Object old=provider.get(null);
        var store=AppearancePresetStore.class.getDeclaredField("shared"); store.setAccessible(true); Object oldStore=store.get(null);
        Platform.install(new Platform.Provider(){public Path configDir(){return dir;} public Path gameDir(){return dir;} public boolean isModLoaded(String id){return false;}});
        try {
            AppearancePresetStore.init(dir.resolve("presets"));
            var page=new SettingsSectionPage();
            var px=SettingsSectionPage.class.getDeclaredField("pointerX"); px.setAccessible(true); px.setFloat(page,200F);
            var py=SettingsSectionPage.class.getDeclaredField("pointerY"); py.setAccessible(true); py.setFloat(page,80F);
            var count=SettingsSectionPage.class.getDeclaredField("stripHitTests"); count.setAccessible(true);
            var method=SettingsSectionPage.class.getDeclaredMethod("drawThemes",Canvas.class,UiLayout.Rect.class,float.class); method.setAccessible(true);
            try(var surface=Surface.makeRasterN32Premul(600,300)) {
                method.invoke(page,surface.getCanvas(),new UiLayout.Rect(10,10,500,200),16F);
                assertEquals(1L,count.getLong(page));
            }
        } finally { store.set(null,oldStore); provider.set(null,old); }
    }
}
