package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsHomePage;
import com.atom.chat.theme.ThemeService;
import com.atom.chat.ui.*;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Real component previews; these CPU images do not simulate Minecraft's world or backdrop blur. */
class V080VisualProbeTest {
    @Test void renderActualWideLayoutGlassMaterialsAndHud(@TempDir Path tmp) throws Exception {
        var provider=Platform.class.getDeclaredField("provider");provider.setAccessible(true);
        var instance=AtomChatConfig.class.getDeclaredField("instance");instance.setAccessible(true);
        Object oldProvider=provider.get(null), oldConfig=instance.get(null);
        Platform.install(new Platform.Provider() {
            public Path configDir(){return tmp;} public Path gameDir(){return tmp;}
            public boolean isModLoaded(String id){return false;}
        });
        instance.set(null,new AtomChatConfig());
        Path out=Path.of(System.getProperty("atomchat.repoRoot","../.."),"build/v080/visual");Files.createDirectories(out);
        try(var language=PreviewLanguage.install("zh_cn")) {
            var config=AtomChatConfig.get();
            try(var wide=Surface.makeRasterN32Premul(1200,940)) {
                drawSettings(wide.getCanvas(),UiLayout.ofRoot(30,20,1140,900),"宽屏设置",config);
                save(wide,out.resolve("wide-settings.png"));
            }
            try(var sheet=Surface.makeRasterN32Premul(1560,1920)) {
                sheet.getCanvas().clear(0xFF303642);
                var presets=ThemeService.presets();
                for(int i=0;i<presets.length;i++) {
                    ThemeService.apply(config,presets[i].id());
                    var canvas=sheet.getCanvas();int save=canvas.save();
                    canvas.translate((i%4)*390,(i/4)*640);
                    drawSettings(canvas,UiLayout.ofRoot(10,10,370,620),presets[i].id(),config);
                    canvas.restoreToCount(save);
                }
                save(sheet,out.resolve("theme-materials.png"));
            }
            try(var hud=Surface.makeRasterN32Premul(640,360);var paint=new Paint()) {
                var canvas=hud.getCanvas();canvas.clear(0xFF92A5B4);
                canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(0,180,640,180),paint.setColor(0xFF334C3D));
                ChatShortcutHint.render(canvas,640,360,"Y",1);save(hud,out.resolve("hud-shortcut.png"));
            }
            long previousInk=0;
            for(float visibility:new float[]{0,.25F,.5F,1}) {
                try(var hud=Surface.makeRasterN32Premul(640,120);var pixels=new Bitmap()) {
                    var canvas=hud.getCanvas();canvas.clear(0);int saves=canvas.getSaveCount();
                    ChatShortcutHint.render(canvas,640,120,"LEFT CONTROL",visibility);
                    assertEquals(saves,canvas.getSaveCount());
                    pixels.allocN32Pixels(640,120);assertTrue(hud.readPixels(pixels,0,0));
                    long ink=0;byte[] bytes=pixels.readPixels();
                    for(int at=3;at<bytes.length;at+=4) ink+=bytes[at]&255;
                    if(visibility==0) assertEquals(0,ink); else assertTrue(ink>previousInk);
                    previousInk=ink;
                }
            }
        } finally { instance.set(null,oldConfig);provider.set(null,oldProvider); }
    }
    private static void drawSettings(Canvas canvas,UiLayout layout,String title,AtomChatConfig config) {
        int save=canvas.save();
        try(var paint=new Paint()) {
            canvas.clipRRect(io.github.humbleui.types.RRect.makeXYWH(layout.panelX,layout.panelY,
                    layout.panelW,layout.panelH,UiTokens.panelRadius()),true);
            canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(layout.panelX,layout.panelY,layout.panelW,layout.panelH),paint.setColor(0xFF8A9EAE));
            canvas.drawCircle(layout.panelX+100,layout.panelY+300,200,paint.setColor(0xFFB5A188));
            SkiaDraw.drawRoundedRect(canvas,layout.panelX,layout.panelY,layout.panelW,layout.panelH,
                    UiTokens.panelRadius(),(Math.round(config.panelOpacity*255)<<24)|(config.panelBgColor&0xFFFFFF));
            ShellHeader.render(canvas,layout.header,title,false,null,0,config.textPrimaryColor);
            new SettingsHomePage().render(canvas,layout,-100,-100,0);
            var tabs=new BottomTabBar();tabs.indicatorAnimator().setValue(2);
            tabs.render(canvas,layout.tabBar,2,config.textPrimaryColor,config.accentColor);
            assertTrue(SettingsHomePage.tileRect(layout,0,0).right()<=SettingsHomePage.tileRect(layout,1,0).x());
        } finally { canvas.restoreToCount(save); }
    }
    private static void save(Surface surface,Path path) throws Exception {
        try(var image=surface.makeImageSnapshot();var data=image.encodeToData()) { Files.write(path,data.getBytes()); }
    }
}
