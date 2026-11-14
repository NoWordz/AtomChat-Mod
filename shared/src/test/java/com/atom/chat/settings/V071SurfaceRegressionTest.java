package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.ui.PanelBackground;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiStagger;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class V071SurfaceRegressionTest {
    @TempDir Path root;
    private Object old;
    @BeforeEach void setup() throws Exception {
        Platform.install(new Platform.Provider(){
            public Path configDir(){return root.resolve("config");}
            public Path gameDir(){return root;}
            public boolean isModLoaded(String id){return false;}
        });
        var field=AtomChatConfig.class.getDeclaredField("instance");field.setAccessible(true);
        old=field.get(null);field.set(null,new AtomChatConfig());
    }
    @AfterEach void restore() throws Exception {
        var field=AtomChatConfig.class.getDeclaredField("instance");field.setAccessible(true);field.set(null,old);
    }
    @Test void bezelLightingChangesAlongTheRingWithoutPaintingItsInterior() {
        var cfg=AtomChatConfig.get();cfg.panelOutlineColor=0xFF405060;cfg.setPanelOutlineWidth(4);
        try(var surface=Surface.makeRasterN32Premul(200,260);var b=new Bitmap()) {
            surface.getCanvas().clear(0);
            PanelBackground.drawBezel(surface.getCanvas(),new UiLayout.Rect(20,20,160,220));
            b.allocN32Pixels(200,260);assertTrue(surface.readPixels(b,0,0));
            int top=b.getColor(100,22),bottom=b.getColor(100,237);
            assertTrue(((top>>>16)&255)>((bottom>>>16)&255)+12,"the selected dimensional ring needs a visible top-to-bottom light change");
            assertEquals(0,b.getColor(100,30),"ring lighting cannot wash over the panel interior");
        }
    }
    @Test void bezelReflectionKeepsDarkOutlinesDarkAndRetainsTheSelectedColour() {
        var cfg=AtomChatConfig.get();cfg.setPanelOutlineWidth(4);
        for(int colour:new int[]{0xFF000000,0xFF14161B,0xFF405060,0xFFEEEEEE,0xFFFF0000}) {
            cfg.panelOutlineColor=colour;
            try(var surface=Surface.makeRasterN32Premul(200,260);var b=new Bitmap()) {
                surface.getCanvas().clear(0);
                PanelBackground.drawBezel(surface.getCanvas(),new UiLayout.Rect(20,20,160,220));
                b.allocN32Pixels(200,260);assertTrue(surface.readPixels(b,0,0));
                int top=b.getColor(100,22),middle=b.getColor(22,130),bottom=b.getColor(100,237);
                for(int shift:new int[]{0,8,16}) {
                    int base=(colour>>>shift)&255;
                    assertTrue(((top>>>shift)&255)<=base+12,"top reflection must not turn a black outline grey");
                    assertTrue(((bottom>>>shift)&255)>=base-16,"bottom shade must remain subtle");
                    assertEquals(base,(middle>>>shift)&255,1,"the centre of the ring keeps the user's colour");
                }
                assertEquals(255,top>>>24);
                assertEquals(0,b.getColor(100,30),"reflection is confined to the outline");
            }
        }
    }
    @Test void hoveringOneAuthorCardOnlyAnimatesThatCard() {
        var page=new SettingsSectionPage();
        var layout=UiLayout.ofDetail(20,20,600,900);
        var rows=page.rows(SettingsSection.ABOUT);
        int index=0;while(rows.get(index).kind()!=SettingsSectionPage.RowKind.AUTHORS)index++;
        var initial=page.rowRect(SettingsSection.ABOUT,rows,index,0,layout);
        float scroll=Math.max(0,initial.y()-layout.list.y()-80);
        var row=page.rowRect(SettingsSection.ABOUT,rows,index,scroll,layout);
        try(var surface=Surface.makeRasterN32Premul(640,960)) {
            draw(page,surface,layout,scroll,-1,-1,1000);
            byte[] rest=pixels(surface,row);
            for(int frame=1;frame<=30;frame++)draw(page,surface,layout,scroll,row.x()+row.w()/4,row.y()+row.h()/2,1000+frame*16);
            assertFalse(Arrays.equals(rest,pixels(surface,row)),"an author card must respond to hover through the real page renderer");
        }
    }
    @Test void listViewportDoesNotCutOffTheRightCardShadow() {
        var page=new SettingsSectionPage();var layout=UiLayout.ofDetail(20,20,600,900);
        var rows=page.rows(SettingsSection.ABOUT);
        int index=0;while(rows.get(index).kind()!=SettingsSectionPage.RowKind.AUTHORS)index++;
        var initial=page.rowRect(SettingsSection.ABOUT,rows,index,0,layout);
        float scroll=Math.max(0,initial.y()-layout.list.y()-80);
        var row=page.rowRect(SettingsSection.ABOUT,rows,index,scroll,layout);
        try(var actual=Surface.makeRasterN32Premul(660,960);var reference=Surface.makeRasterN32Premul(660,960);
            var a=new Bitmap();var r=new Bitmap()) {
            draw(page,actual,layout,scroll,-1,-1,1000);
            float gap=com.atom.lib.design.atomchat.UiTokens.SETTINGS_TILE_GAP;
            float width=(row.w()-gap)/2;
            com.atom.lib.design.atomchat.UiCards.drawCard(reference.getCanvas(),row.x()+width+gap,row.y(),width,row.h(),
                    com.atom.lib.design.atomchat.UiTokens.settingsTileRadius(),0);
            a.allocN32Pixels(660,960);r.allocN32Pixels(660,960);
            assertTrue(actual.readPixels(a,0,0));assertTrue(reference.readPixels(r,0,0));
            int x=(int)layout.list.right()+3,y=(int)(row.y()+row.h()/2);
            assertTrue((r.getColor(x,y)>>>24)>0,"the independent card renderer must have a visible shadow at the probe");
            assertEquals(r.getColor(x,y)>>>24,a.getColor(x,y)>>>24,"the page clip must preserve that shadow tail");
        }
    }
    private static void draw(SettingsSectionPage page,Surface surface,UiLayout layout,float scroll,float x,float y,long now) {
        surface.getCanvas().clear(0);
        UiStagger.atFrame(now,()->page.render(surface.getCanvas(),layout,SettingsSection.ABOUT,x,y,scroll,0xFF4488CC));
    }
    @Test void writeProductionSurfaceEvidence() throws Exception {
        try(var locale=com.atom.chat.render.PreviewLanguage.install("zh_cn")) {
        SettingsSectionPage.clearTranslationCache();
        for(String theme:new String[]{"frosted","raven","minimal"}) {
            var cfg=AtomChatConfig.get();com.atom.chat.theme.ThemeService.apply(cfg,theme);
            cfg.panelOutlineColor=0xFF14161B;cfg.setPanelOutlineWidth(4);
            var page=new SettingsSectionPage();page.selectChip(SettingsSection.APPEARANCE,2);
            var layout=UiLayout.ofDetail(28,28,580,900);
            try(var surface=Surface.makeRasterN32Premul(1130,960)) {
                var c=surface.getCanvas();c.clear(theme.equals("raven")?0xFF40536A:0xFF829DAF);
                com.atom.chat.render.SoftShadow.drawPanel(c,28,28,580,900,com.atom.lib.design.atomchat.UiTokens.panelRadius());
                float inset=PanelBackground.outlineInset();
                com.atom.lib.design.atomchat.SkiaDraw.drawRoundedRect(c,28+inset,28+inset,580-inset*2,900-inset*2,
                        com.atom.lib.design.atomchat.UiTokens.panelRadius()-inset,PanelBackground.withOpacity(cfg.panelBgColor,cfg.panelOpacity));
                long now=System.currentTimeMillis()+2000;
                UiStagger.atFrame(now,()->page.render(c,layout,SettingsSection.APPEARANCE,-1,-1,0,cfg.accentColor));
                com.atom.chat.ui.ShellHeader.render(c,layout.header,"外观",false,null,0F,cfg.textPrimaryColor);
                PanelBackground.drawBezel(c,layout.rect());
                com.atom.chat.ui.MenuPopup.drawSurface(c,680,70,360,180);
                com.atom.chat.ui.MenuPopup.drawPickerSurface(c,680,310,360,300);
                var path=Path.of(System.getProperty("atomchat.repoRoot","../.."),"docs/evidence/2026-11-14-v0.7.1-followup/candidate",
                        System.getProperty("atomchat.golden.target","unknown"),theme+".png");
                java.nio.file.Files.createDirectories(path.getParent());
                try(var image=surface.makeImageSnapshot();var png=image.encodeToData()){java.nio.file.Files.write(path,png.getBytes());}
            }
        }
        } finally { SettingsSectionPage.clearTranslationCache(); }
    }
    private static byte[] pixels(Surface surface,UiLayout.Rect row) {
        try(var b=new Bitmap()) {
            b.allocN32Pixels((int)row.w(),(int)row.h());
            assertTrue(surface.readPixels(b,(int)row.x(),(int)row.y()));return b.readPixels();
        }
    }
}
