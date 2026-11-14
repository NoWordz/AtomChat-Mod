package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.theme.AppearancePresetStore;
import com.atom.chat.ui.*;
import com.atom.chat.wallpaper.*;
import com.atom.lib.design.atomchat.*;
import io.github.humbleui.skija.*;
import java.nio.file.*;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Uses real locale, production preset shell and actual SkSL renders. */
class FeedbackVisualTest {
    @TempDir Path tmp;
    @Test void renderProductionPresetStripsAndPreviousMenuReference() throws Exception {
        var instance=AtomChatConfig.class.getDeclaredField("instance");instance.setAccessible(true);Object previous=instance.get(null);
        var provider=Platform.class.getDeclaredField("provider");provider.setAccessible(true);Object oldProvider=provider.get(null);
        try {
            Platform.install(new Platform.Provider(){public Path configDir(){return tmp.resolve("config");}public Path gameDir(){return tmp;}public boolean isModLoaded(String id){return false;}});
            var cfg=new AtomChatConfig();cfg.animationEnabled=false;instance.set(null,cfg);
            AppearancePresetStore.init(tmp.resolve("presets"));WallpaperStore.init(tmp.resolve("wallpaper"));
            assertTrue(WallpaperStore.setPng(WallpaperPresets.renderPng("cobalt",480,860)));
            Files.writeString(tmp.resolve("wallpaper/selected-preset.txt"),"cobalt");WallpaperStore.init(tmp.resolve("wallpaper"));
            for(var preset:WallpaperPresets.presets()) {
                WallpaperPresets.preview(preset.id());
                Files.write(output().resolve(preset.id()+".png"),WallpaperPresets.renderPng(preset.id(),480,860));
            }
            var field=WallpaperPresets.class.getDeclaredField("WORKER");field.setAccessible(true);
            ((java.util.concurrent.ExecutorService)field.get(null)).submit(()->{}).get(30,java.util.concurrent.TimeUnit.SECONDS);
            try(var locale=PreviewLanguage.install("zh_cn");var surface=Surface.makeRasterN32Premul(1080,900)) {
                var canvas=surface.getCanvas();canvas.clear(0xFF454B55);
                SettingsSectionPage.clearTranslationCache();
                SettingsSectionPage page=new SettingsSectionPage();
                var rect=new UiLayout.Rect(25,30,1030,330);
                UiCards.drawCard(canvas,rect.x(),rect.y(),rect.w(),rect.h(),UiTokens.settingsRowRadius(),0);
                var themes=SettingsSectionPage.class.getDeclaredMethod("drawThemes",Canvas.class,UiLayout.Rect.class,float.class);themes.setAccessible(true);
                themes.invoke(page,canvas,rect,16F);
                var wall=new UiLayout.Rect(25,390,1030,330);
                UiCards.drawCard(canvas,wall.x(),wall.y(),wall.w(),wall.h(),UiTokens.settingsRowRadius(),0);
                var wallpapers=SettingsSectionPage.class.getDeclaredMethod("drawWallpapers",Canvas.class,UiLayout.Rect.class,float.class);wallpapers.setAccessible(true);
                wallpapers.invoke(page,canvas,wall,16F);
                save(surface,"theme-wallpaper-shared-shell.png");
            }
            // The preserved 0.6 reference used radius 20; 0.7 keeps the user's newer radius policy.
            cfg.cornerRadius=20F;
            try(var expected=Surface.makeRasterN32Premul(450,260);var actual=Surface.makeRasterN32Premul(450,260)) {
                for(var surface:new Surface[]{expected,actual})surface.getCanvas().clear(0xFF454B55);
                Path reference=Path.of(System.getProperty("atomchat.repoRoot","../.."),
                        "docs/evidence/2026-11-11-v070-user-regressions/menu-old-head-reference.png");
                try(var image=Image.makeFromEncoded(Files.readAllBytes(reference))) {
                    assertEquals(450,image.getWidth());assertEquals(260,image.getHeight());
                    expected.getCanvas().drawImage(image,0,0);
                }
                MenuPopup.drawSurface(actual.getCanvas(),45,45,340,160);
                save(expected,"menu-old-head-reference.png");save(actual,"menu-restored.png");
                try(var a=new Bitmap();var b=new Bitmap()) {
                    a.allocN32Pixels(450,260);b.allocN32Pixels(450,260);
                    assertTrue(expected.readPixels(a,0,0));assertTrue(actual.readPixels(b,0,0));
                    byte[] expectedPixels=a.readPixels(),actualPixels=b.readPixels();int max=0;
                    for(int i=0;i<expectedPixels.length;i++)max=Math.max(max,Math.abs((expectedPixels[i]&255)-(actualPixels[i]&255)));
                    assertTrue(max<=7,"menu surface must match the independently preserved old material, max="+max);
                }
            }
        } finally {WallpaperPresets.cancelPending();instance.set(null,previous);provider.set(null,oldProvider);}
    }
    private static Path output()throws Exception {
        Path path=Path.of(System.getProperty("atomchat.repoRoot","../.."),".work/v0.7.0-feedback/screenshots");Files.createDirectories(path);return path;
    }
    private static void save(Surface surface,String name)throws Exception {
        try(var image=surface.makeImageSnapshot();var png=image.encodeToData()){Files.write(output().resolve(name),png.getBytes());}
    }
}
