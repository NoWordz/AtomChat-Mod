package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.theme.AppearancePresetStore;
import com.atom.chat.theme.ThemeService;
import com.atom.chat.ui.BottomTabBar;
import com.atom.chat.ui.ShellHeader;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

/** Uses production settings, header and navigation renderers; only the four-column presentation is a fixture. */
class MaterialSurfacesPreviewTest {
    @TempDir Path tmp;

    @BeforeEach void installPlatform() {
        Platform.install(new Platform.Provider() {
            public Path configDir(){return tmp;}
            public Path gameDir(){return tmp;}
            public boolean isModLoaded(String id){return false;}
        });
        AppearancePresetStore.init(tmp.resolve("presets"));
    }

    @Test void headerSurfaceLeavesItsDotBandToTheBackdropPass() {
        var cfg=AtomChatConfig.get();int old=cfg.textSecondaryColor;
        try(Surface surface=Surface.makeRasterN32Premul(240,160);var pixels=new io.github.humbleui.skija.Bitmap()) {
            cfg.textSecondaryColor=0xFFFF0000;
            ShellHeader.render(surface.getCanvas(),new UiLayout.Rect(20,20,200,60),"标题",false,null,0F,0xFFFFFFFF);
            pixels.allocN32Pixels(240,160);assertTrue(surface.readPixels(pixels,0,0));
            int tinted=0;
            for(int y=96;y<120;y++)for(int x=30;x<210;x++)if((pixels.getColor(x,y)&0xFF0000)>0)tinted++;
            assertEquals(0,tinted,"foreground header pass must not scatter dots over an already painted page");
        } finally {cfg.textSecondaryColor=old;}
    }

    @Test void renderDarkLightAboutAndNarrowSurfaces() throws Exception {
        var cfg=AtomChatConfig.get();
        var saved=AppearancePresetStore.Snapshot.capture(cfg);
        try (Surface surface=Surface.makeRasterN32Premul(1830,1040)) {
            var canvas=surface.getCanvas();
            canvas.clear(0xFF33363C);
            String[] labels={"深色 · 预览与标题点阵","浅色 · 预览与标题点阵","关于 · Logo 背景","窄窗口 · 横向滚动"};
            float[] widths={490,490,490,240};
            float x=20;
            for(int i=0;i<4;i++) {
                ThemeService.apply(cfg,i==1 ? "minimal" : "raven");
                // Use a strong dark palette on three columns, matching the user's seam feedback.
                if(i!=1) { cfg.panelBgColor=0xFF0A0A0A;cfg.cardColor=0xFF1B1B1B;cfg.cardTint=1F; }
                cfg.bubbleStyle=AtomChatConfig.BubbleStyle.CORNER;
                var layout=UiLayout.ofDetail(x,65,widths[i],940);
                SkiaDraw.drawRoundedRect(canvas,layout.panelX,layout.panelY,layout.panelW,layout.panelH,UiTokens.panelRadius(),cfg.panelBgColor);
                SkiaFontRenderer.drawTextCentered(canvas,FontManager.font(UiTokens.FONT_NAME),labels[i],x+widths[i]/2,30,0xFFF5F5F5);
                ShellHeader.drawBackdrop(canvas,layout.header);
                try(var language=PreviewLanguage.install(i==1?"en_us":"zh_cn")) {
                    SettingsSectionPage page=new SettingsSectionPage();
                    page.render(canvas,layout,i==2?SettingsSection.ABOUT:SettingsSection.APPEARANCE,-1,-1,0,cfg.accentColor);
                }
                ShellHeader.render(canvas,layout.header,i==2?"关于":"外观",false,null,0,cfg.textPrimaryColor);
                x+=widths[i]+20;
            }
            var path=Path.of(System.getProperty("atomchat.projectDir",".")).resolve("build/preview/material-surfaces.png");
            Files.createDirectories(path.getParent());
            try(var image=surface.makeImageSnapshot();var data=image.encodeToData()) { Files.write(path,data.getBytes()); }
            assertTrue(Files.size(path)>1000);
        } finally {saved.applyTo(cfg);}
    }

    @Test void detailsRespectTheSurfaceAndTransparentCards() {
        var cfg=AtomChatConfig.get();float old=cfg.cardTint;
        try(Surface surface=Surface.makeRasterN32Premul(200,150);var b=new io.github.humbleui.skija.Bitmap()) {
            var c=surface.getCanvas();var bar=new UiLayout.Rect(20,20,160,100);
            int saves=c.getSaveCount();cfg.cardTint=1F;
            ChromeMaterial.drawDetails(c,bar,25);
            assertEquals(saves,c.getSaveCount());
            b.allocN32Pixels(200,150);assertTrue(surface.readPixels(b,0,0));
            assertTrue((b.getColor(90,20)>>>24)>0,"upper rim receives light");
            assertTrue((b.getColor(90,118)>>>24)>0,"lower interior receives shade");
            assertEquals(0,b.getColor(20,20),"rounded outer corner stays empty");
            c.clear(0);cfg.cardTint=0F;ChromeMaterial.drawDetails(c,bar,0);
            assertTrue(surface.readPixels(b,0,0));
            for(int y=0;y<150;y++)for(int x=0;x<200;x++)assertEquals(0,b.getColor(x,y));
        } finally {cfg.cardTint=old;}
    }

    @Test void renderTheThreeBarsInDarkAndLightThemes() throws Exception {
        var cfg=AtomChatConfig.get();var saved=AppearancePresetStore.Snapshot.capture(cfg);
        try(Surface surface=Surface.makeRasterN32Premul(1200,540)) {
            var canvas=surface.getCanvas();canvas.clear(0xFF33363C);
            for(int row=0;row<2;row++) {
                ThemeService.apply(cfg,row==0?"raven":"minimal");
                if(row==0){cfg.panelBgColor=0xFF0A0A0A;cfg.cardColor=0xFF1B1B1B;cfg.cardTint=1F;}
                for(int col=0;col<3;col++) {
                    float x=20+col*395,y=20+row*260;
                    SkiaDraw.drawRoundedRect(canvas,x,y,375,240,UiTokens.cardRadius(),cfg.panelBgColor);
                    String label=col==0?"顶栏 · 向下渐隐":col==1?"输入栏 · 向上渐隐":"导航栏 · 向上渐隐";
                    SkiaFontRenderer.drawTextCentered(canvas,FontManager.font(UiTokens.FONT_NAME),label,x+187.5F,y+29,cfg.textPrimaryColor);
                    var bar=new UiLayout.Rect(x+15,y+(col==0?65:110),345,col==1?90:55);
                    if(col==0) {
                        ShellHeader.drawBackdrop(canvas,bar);
                        ShellHeader.render(canvas,bar,"世界聊天",false,null,0F,cfg.textPrimaryColor);
                    } else {
                        DotTexture.drawEdge(canvas,bar,UiTokens.chromeRadius(),true,cfg.textSecondaryColor);
                        if(col==2) {
                            var tabs=new BottomTabBar();tabs.setSelectedImmediate(2);
                            tabs.render(canvas,bar,2,cfg.textPrimaryColor,cfg.accentColor);
                        } else {
                            SkiaDraw.drawChromeShadow(canvas,bar.x(),bar.y(),bar.w(),bar.h(),UiTokens.chromeRadius());
                            SkiaDraw.drawRoundedRect(canvas,bar.x(),bar.y(),bar.w(),bar.h(),UiTokens.chromeRadius(),UiTokens.cardFill());
                            ChromeMaterial.drawDetails(canvas,bar,UiTokens.chromeRadius());
                            SkiaFontRenderer.drawTextCentered(canvas,FontManager.font(UiTokens.FONT_NAME),"输入消息…",bar.x()+bar.w()/2,bar.y()+bar.h()/2,cfg.textSecondaryColor);
                        }
                    }
                }
            }
            var path=Path.of(System.getProperty("atomchat.projectDir",".")).resolve("build/preview/material-bars.png");
            Files.createDirectories(path.getParent());
            try(var image=surface.makeImageSnapshot();var data=image.encodeToData()){Files.write(path,data.getBytes());}
        } finally {saved.applyTo(cfg);}
    }
}
