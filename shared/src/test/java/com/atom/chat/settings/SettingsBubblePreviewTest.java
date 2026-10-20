package com.atom.chat.settings;

import com.atom.chat.platform.Platform;
import com.atom.chat.theme.AppearancePresetStore;
import com.atom.chat.ui.UiLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.HashSet;
import com.atom.chat.config.AtomChatConfig;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import static org.junit.jupiter.api.Assertions.*;

class SettingsBubblePreviewTest {
    @TempDir Path tmp;

    @Test void wheelOverTheOtherStripCannotStealAnActiveDrag() throws Exception {
        var page = new SettingsSectionPage();
        var layout = UiLayout.ofDetail(0,0,230,1800);
        var theme = page.themesRowRect(layout,SettingsSection.APPEARANCE,0);
        var bubble = bubbleRow(page,layout);
        float x=bubble.x()+45, y=bubble.y()+65;
        var selections = new java.util.ArrayList<String>();
        page.setActionHandler(selections::add);
        assertTrue(page.themesPress(x,y,layout,SettingsSection.APPEARANCE,0));
        page.dragThemeStrip(x-70,y,bubble);
        assertTrue(page.themesUnderPointer(theme.x()+45,theme.y()+65,layout,SettingsSection.APPEARANCE,0));
        page.wheelThemeStrip(-1,page.themesRowRect(layout,SettingsSection.APPEARANCE,0));
        page.endThemeStrip();
        assertTrue(selections.isEmpty());
        // Returning to the original strip after release must not find a stuck press.
        assertTrue(page.themesUnderPointer(x,y,layout,SettingsSection.APPEARANCE,0));
        assertFalse(page.isDraggingThemeStrip(),"wheel events cannot strand the drag owner's pressed state");
    }

    @Test void verticalIntentReturnsThePointerToPageScrolling() throws Exception {
        var page = new SettingsSectionPage();
        var layout = UiLayout.ofDetail(0,0,230,1800);
        var row = bubbleRow(page,layout);
        var selections = new java.util.ArrayList<String>();
        page.setActionHandler(selections::add);
        float x=row.x()+45, y=row.y()+65;
        assertTrue(page.themesPress(x,y,layout,SettingsSection.APPEARANCE,0));
        page.dragThemeStrip(x+2,y+70,row);
        assertFalse(page.isDraggingThemeStrip());
        page.endThemeStrip();
        assertTrue(selections.isEmpty(),"a vertical page-scroll gesture cannot select a bubble style");
    }

    @Test void bubbleStripPrecedesCornersAndDragsWithoutSelecting() throws Exception {
        var page = new SettingsSectionPage();
        var layout = UiLayout.ofDetail(0,0,230,1800);
        var rows = page.rows(SettingsSection.APPEARANCE);
        int bubble = java.util.stream.IntStream.range(0,rows.size()).filter(i -> rows.get(i).kind() == SettingsSectionPage.RowKind.BUBBLES).findFirst().orElseThrow();
        int corners = java.util.stream.IntStream.range(0,rows.size()).filter(i -> "atomchat.settings.group.appearance.corners".equals(rows.get(i).labelKey())).findFirst().orElseThrow();
        assertTrue(bubble < corners,"bubble style belongs above the corner-radius group");
        var method=SettingsSectionPage.class.getDeclaredMethod("rowRect",SettingsSection.class,java.util.List.class,int.class,float.class,UiLayout.class);
        method.setAccessible(true);
        var row=(UiLayout.Rect)method.invoke(page,SettingsSection.APPEARANCE,rows,bubble,0F,layout);
        var selections = new java.util.ArrayList<String>();
        page.setActionHandler(selections::add);
        float x=row.x()+35,y=row.y()+65;
        assertTrue(page.themesPress(x,y,layout,SettingsSection.APPEARANCE,0F),"preview strips share pointer routing");
        page.dragThemeStrip(x-500,y,row);
        page.endThemeStrip();
        assertTrue(selections.isEmpty(),"dragging must never select a style");
        String last="bubble_style:CORNER";
        boolean found=false;
        for(float px=row.x();px<row.right();px+=3) {
            var hit=page.hit(px,y,layout,SettingsSection.APPEARANCE,0F);
            if(hit!=null && last.equals(hit.row().actionId())) {found=true;break;}
        }
        assertTrue(found,"horizontal drag reveals the final card at narrow width");
    }

    private static UiLayout.Rect bubbleRow(SettingsSectionPage page, UiLayout layout) throws Exception {
        var rows = page.rows(SettingsSection.APPEARANCE);
        int index = java.util.stream.IntStream.range(0, rows.size())
                .filter(i -> rows.get(i).kind() == SettingsSectionPage.RowKind.BUBBLES).findFirst().orElseThrow();
        var method = SettingsSectionPage.class.getDeclaredMethod("rowRect", SettingsSection.class,
                java.util.List.class, int.class, float.class, UiLayout.class);
        method.setAccessible(true);
        return (UiLayout.Rect) method.invoke(page, SettingsSection.APPEARANCE, rows, index, 0F, layout);
    }

    @BeforeEach void installPlatform() {
        Platform.install(new Platform.Provider() {
            public Path configDir(){return tmp;}
            public Path gameDir(){return tmp;}
            public boolean isModLoaded(String id){return false;}
        });
        AppearancePresetStore.init(tmp.resolve("presets"));
    }

    @Test @SuppressWarnings("unchecked")
    void foldingPreviewsCannotStealTheFollowingHeading() throws Exception {
        var page=new SettingsSectionPage();
        var expansion=SettingsSectionPage.class.getDeclaredField("groupExpansion");
        expansion.setAccessible(true);
        ((java.util.Map<String,Float>)expansion.get(page)).put("label:atomchat.settings.group.appearance.bubbles",0.5F);
        var layout=UiLayout.ofDetail(0,0,520,1800);
        var rows=page.rows(SettingsSection.APPEARANCE);
        int next=java.util.stream.IntStream.range(0,rows.size()).filter(i->
                "atomchat.settings.group.appearance.corners".equals(rows.get(i).labelKey())).findFirst().orElseThrow();
        var method=SettingsSectionPage.class.getDeclaredMethod("rowRect",SettingsSection.class,java.util.List.class,int.class,float.class,UiLayout.class);
        method.setAccessible(true);
        var rect=(UiLayout.Rect)method.invoke(page,SettingsSection.APPEARANCE,rows,next,0F,layout);
        var hit=page.hit(rect.x()+rect.w()/2,rect.y()+rect.h()/2,layout,SettingsSection.APPEARANCE,0F);
        assertNotNull(hit);
        assertEquals("atomchat.settings.group.appearance.corners",hit.row().labelKey(),"hidden preview area cannot cover the next heading");
    }

    @Test void threeExplicitSelectionsAreReachableAtRegularAndNarrowWidths() throws Exception {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return tmp; }
            public Path gameDir() { return tmp; }
            public boolean isModLoaded(String id) { return false; }
        });
        AppearancePresetStore.init(tmp.resolve("presets"));
        for(float width : new float[]{520, 230}) {
            var page = new SettingsSectionPage();
            var selections = new HashSet<String>();
            page.setActionHandler(selections::add);
            var layout = UiLayout.ofDetail(0,0,width,1800);
            assertTrue(page.rows(SettingsSection.APPEARANCE).stream()
                    .anyMatch(r -> r.kind().name().equals("BUBBLES")), "three visible style cards replace the cycle row");
            var row = bubbleRow(page,layout);
            float y = row.y() + 65;
            for (int step=0; step<4; step++) {
                for(float x=row.x(); x<row.right(); x+=5) {
                    var hit=page.hit(x,y,layout,SettingsSection.APPEARANCE,0);
                    if(hit!=null && hit.row().actionId()!=null && hit.row().actionId().startsWith("bubble_style:")) page.perform(hit);
                }
                float x = row.x() + row.w()/2;
                assertTrue(page.themesPress(x,y,layout,SettingsSection.APPEARANCE,0));
                page.dragThemeStrip(x-90,y,row);
                page.endThemeStrip();
            }
            assertEquals(java.util.Set.of("bubble_style:CLASSIC","bubble_style:ROUND","bubble_style:CORNER"),selections);
            assertTrue(page.rows(SettingsSection.APPEARANCE).stream().noneMatch(r -> r.item()!=null && "message_edge_dots".equals(r.item().id())));
        }
    }

    @Test void realPreviewCardsShowBothSidesAndDistinctCornerGeometry() throws Exception {
        var cfg = AtomChatConfig.get();
        var saved = AppearancePresetStore.Snapshot.capture(cfg);
        try {
            cfg.otherBubbleColor = 0xFF00FF00;
            cfg.ownBubbleColor = 0xFFFF0000;
            cfg.panelBgColor = 0xFF080808;
            cfg.cardColor = 0xFF181818;
            var page = new SettingsSectionPage();
            var layout = UiLayout.ofDetail(0,0,520,1800);
            var row = bubbleRow(page,layout);
            try (Surface surface=Surface.makeRasterN32Premul(520,1800); Bitmap pixels=new Bitmap()) {
                page.render(surface.getCanvas(),layout,SettingsSection.APPEARANCE,-1,-1,0,cfg.accentColor);
                pixels.allocN32Pixels(520,1800); assertTrue(surface.readPixels(pixels,0,0));
                int[] other = new int[3], own = new int[3];
                for(int i=0;i<3;i++) {
                    String id="bubble_style:"+AtomChatConfig.BubbleStyle.values()[i].name();
                    SettingsSectionPage.RowHit card=null;
                    search: for(float y=row.y()+60;y<row.y()+65;y+=4) for(float x=row.x();x<row.right();x+=7) {
                        var hit=page.hit(x,y,layout,SettingsSection.APPEARANCE,0);
                        if(hit!=null && id.equals(hit.row().actionId())) {card=hit;break search;}
                    }
                    assertNotNull(card);
                    for(int y=(int)Math.ceil(card.y());y<card.y()+card.h();y++) for(int x=(int)Math.ceil(card.x());x<card.x()+card.w();x++) {
                        int color=pixels.getColor(x,y);
                        if(color==cfg.otherBubbleColor) other[i]++;
                        if(color==cfg.ownBubbleColor) own[i]++;
                    }
                    assertTrue(other[i]>100 && own[i]>100, "both directions appear in each real preview");
                }
                assertTrue(other[0]>other[1] && own[0]>own[1], "classic and round remain distinguishable at preview scale");
                assertTrue(other[2]>other[1] && own[2]>own[1], "sharp avatar corner is visible on both sides");
            }
        } finally { saved.applyTo(cfg); }
    }
}
