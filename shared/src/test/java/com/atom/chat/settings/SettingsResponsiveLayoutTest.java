package com.atom.chat.settings;

import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.platform.Platform;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SettingsResponsiveLayoutTest {
    @Test void crowdedOrdinarySlidersAndColorsKeepTheOriginalValueBand() {
        var page=new SettingsSectionPage();
        String title="Crowded ordinary control title";
        var slider=new SettingsSlider("ordinary",title,0,100,1,()->50,v->{},v->"50%");
        var color=new SettingsColor("ordinary",title,"ui",new int[]{0xff334455},()->0xff334455,v->{});
        for(float width:new float[]{240,280}){
            var layout=UiLayout.ofDetail(0,0,width,600);
            float inner=layout.list.w()-UiTokens.ROW_CLIP_INSET*2-UiTokens.SETTINGS_ROW_PAD*2;
            var main=com.atom.chat.font.FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
            var sub=com.atom.chat.font.FontManager.font(UiTokens.SETTINGS_TILE_SUB);
            var titles=com.atom.chat.render.SkiaFontRenderer.wrap(main,title,inner);
            assertTrue(titles.size()>1,"fixture must really trigger compact title flow");
            float original=UiTokens.SETTINGS_ROW_PAD*2+titles.size()*com.atom.chat.render.SkiaFontRenderer.getHeight(main)
                    +UiTokens.SETTINGS_VALUE_BAND*2+UiTokens.SETTINGS_COMPACT_GAP*2;
            original=Math.max(SettingsSectionPage.rowHeight(SettingsSectionPage.RowKind.SLIDER),original);
            assertEquals(original,page.rowHeight(SettingsSectionPage.Row.ofSlider(slider),layout),.001f,"ordinary slider lost its lower value/track band");
            assertEquals(original,page.rowHeight(SettingsSectionPage.Row.ofColor(color),layout),.001f,"color lost its lower value/swatch band");
        }
    }
    @BeforeAll static void setup(@TempDir Path dir) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return dir; }
            public Path gameDir() { return dir; }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @Test void longTitleGetsItsOwnSpaceWithoutReducingTheFont() {
        SettingsSectionPage page = new SettingsSectionPage();
        SettingsItem item = new SettingsItem("long", "A long setting title that must remain readable", ".", () -> true, value -> {});
        SettingsSectionPage.Row row = SettingsSectionPage.Row.ofSwitch(item);
        float narrow = page.rowHeight(row, UiLayout.ofDetail(0, 0, 240, 600));
        float wide = page.rowHeight(row, UiLayout.ofDetail(0, 0, 1000, 600));
        assertTrue(narrow > wide, "the title must wrap and move the switch below it");
    }

    @Test void approvedNumericTitleUsesWordBoundariesWithoutChangingOrdinaryTitles()throws Exception{
        var page=new SettingsSectionPage();var layout=UiLayout.ofDetail(0,0,240,600);String title="Keep chat history days";
        var slider=new SettingsSlider("history_retention",title,0,100,1,()->7,v->{},v->"7 days");
        var method=SettingsSectionPage.class.getDeclaredMethod("compactText",SettingsSectionPage.Row.class,float.class);method.setAccessible(true);
        float width=layout.list.w()-UiTokens.ROW_CLIP_INSET*2;
        Object compact=method.invoke(page,SettingsSectionPage.Row.ofSlider(slider),width);assertNotNull(compact);
        var titles=compact.getClass().getDeclaredMethod("title");titles.setAccessible(true);
        @SuppressWarnings("unchecked") var lines=(java.util.List<String>)titles.invoke(compact);
        for(String word:title.split(" "))assertTrue(lines.stream().anyMatch(line->line.contains(word)),word+" split in "+lines);
    }

    @Test void narrowSettingsRenderMultipleFramesAndStayScrollable() {
        SettingsSectionPage page = new SettingsSectionPage();
        try (Surface surface = Surface.makeRasterN32Premul(400, 600)) {
            for (float width : new float[]{210, 240, 320, 600}) {
                UiLayout layout = UiLayout.ofDetail(0, 0, width, 300);
                for (SettingsSection section : SettingsSection.values()) {
                    for (int frame = 0; frame < 3; frame++) {
                        int save = surface.getCanvas().getSaveCount();
                        page.render(surface.getCanvas(), layout, section, -1, -1, 0, 0xFF4488CC);
                        assertEquals(save, surface.getCanvas().getSaveCount());
                        assertTrue(Float.isFinite(page.measureContent(layout, section)));
                    }
                }
            }
        }
    }

    @Test void scrollEndLeavesTheSameClearanceAsTheCardSides() {
        SettingsSectionPage page = new SettingsSectionPage();
        UiLayout layout = UiLayout.ofDetail(0, 0, 420, 180);
        var rows = page.rows(SettingsSection.CHAT);
        float content = page.measureContent(layout, SettingsSection.CHAT);
        float scrollY = Math.max(0.0F, content - layout.list.h());
        assertTrue(scrollY > 0.0F, "this fixture must actually overflow and scroll");
        var last = page.rowRect(SettingsSection.CHAT, rows, rows.size() - 1, scrollY, layout);
        assertEquals(UiTokens.ROW_CLIP_INSET, layout.list.bottom() - last.bottom(), 0.01F,
                "the footer clearance must survive the hover spring instead of parking on the clip");
        assertEquals(UiTokens.LIST_PAD_X + UiTokens.ROW_CLIP_INSET,
                last.x() - layout.panelX, 0.01F,
                "the bottom and side breathing rooms share the same 23-unit outer margin");
    }
}
