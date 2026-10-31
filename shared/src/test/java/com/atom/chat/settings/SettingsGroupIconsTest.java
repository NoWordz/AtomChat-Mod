package com.atom.chat.settings;

import com.atom.chat.platform.Platform;
import com.atom.lib.design.atomchat.AppIcons;
import io.github.humbleui.skija.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SettingsGroupIconsTest {
    @BeforeAll static void setup(@TempDir java.nio.file.Path root) {
        Platform.install(new Platform.Provider() {
            public java.nio.file.Path configDir(){return root.resolve("config");}
            public java.nio.file.Path gameDir(){return root;}
            public boolean isModLoaded(String id){return false;}
        });
    }
    static Map<String,Path> actualHeadings() {
        var icons=new LinkedHashMap<String,Path>();
        for(var section:SettingsSection.values()) {
            var page=new SettingsSectionPage();int count=Math.max(1,page.chips(section).size());
            for(int chip=0;chip<count;chip++) {
                page.selectChip(section,chip);
                for(var row:page.rows(section)) if(row.kind()==SettingsSectionPage.RowKind.LABEL)
                    icons.put(row.labelKey(),SettingsGroupIcons.forLabel(row.labelKey()));
            }
        }
        return icons;
    }
    @Test void namespaceCannotTurnUnrelatedSubheadingsIntoChatBubbles() {
        var icons=actualHeadings();assertTrue(icons.size()>=24,"enumerate all settings pages and chips");
        for(var entry:icons.entrySet()) {
            assertTrue(SettingsGroupIcons.labels().contains(entry.getKey()),"a new heading needs an explicit icon assignment: "+entry.getKey());
            assertNotNull(entry.getValue(),entry.getKey());assertFalse(entry.getValue().isEmpty(),entry.getKey());
            if(!entry.getKey().equals("atomchat.settings.group.appearance.bubbles"))
                assertNotSame(AppIcons.ICON_TAB_CHAT_PATH,entry.getValue(),entry.getKey());
        }
    }
    @Test void relatedButDifferentSettingsKeepDistinctVisualMeanings() {
        String prefix="atomchat.settings.group.";
        assertNotSame(SettingsGroupIcons.forLabel(prefix+"history.storage"),SettingsGroupIcons.forLabel(prefix+"history.cleanup"));
        assertNotSame(SettingsGroupIcons.forLabel(prefix+"appearance.message_colors"),SettingsGroupIcons.forLabel(prefix+"appearance.ui_colors"));
        assertNotSame(SettingsGroupIcons.forLabel(prefix+"appearance.effects"),SettingsGroupIcons.forLabel(prefix+"appearance.motion"));
        assertSame(AppIcons.ICON_TAB_CHAT_PATH,SettingsGroupIcons.forLabel(prefix+"appearance.bubbles"));
    }
    @Test void newGlyphsStayOnTheShellGridAndContainDrawablePaths() throws Exception {
        int count=0;
        for(var field:com.atom.chat.ui.SettingsGroupGlyphs.class.getFields()) if(field.getType()==Path.class) {
            var glyph=(Path)field.get(null);var b=glyph.getBounds();count++;
            assertFalse(glyph.isEmpty(),field.getName());
            assertTrue(b.getLeft()>=0&&b.getTop()>=0&&b.getRight()<=20&&b.getBottom()<=20,field.getName());
            assertTrue(b.getWidth()>0&&b.getHeight()>0,field.getName());
        }
        assertEquals(18,count);
    }
    @Test void exportTheActualHeadingAssignmentsAtSmallUiAndLargeSizes() throws Exception {
        com.google.gson.JsonObject lang;
        try(var in=getClass().getResourceAsStream("/assets/atomchat/lang/zh_cn.json")) {
            assertNotNull(in);
            lang=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        var headings=actualHeadings();
        var directory=java.nio.file.Path.of("").toAbsolutePath();
        var target=directory.getFileName().toString();
        while(!java.nio.file.Files.isDirectory(directory.resolve("shared"))) directory=directory.getParent();
        directory=directory.resolve(".work/2026-10-29-icons").resolve(target);
        java.nio.file.Files.createDirectories(directory);
        var inventory=new java.util.ArrayList<Map<String,String>>();
        for(var entry:headings.entrySet()) inventory.add(Map.of("key",entry.getKey(),"title",lang.get(entry.getKey()).getAsString(),"glyph",name(entry.getValue())));
        java.nio.file.Files.writeString(directory.resolve("assignments.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(inventory));
        for(boolean light:new boolean[]{false,true}) {
            int rows=(headings.size()+2)/3;
            try(var surface=io.github.humbleui.skija.Surface.makeRasterN32Premul(1080,rows*100)) {
                var canvas=surface.getCanvas();canvas.clear(light?0xFFF3F5F8:0xFF181B21);
                int color=light?0xFF23272F:0xFFF4F5F7,index=0;
                for(var entry:headings.entrySet()) {
                    float x=index%3*360,y=index/3*100;index++;
                    var glyph=entry.getValue();
                    com.atom.chat.render.DrawPrimitives.drawIconCentered(canvas,glyph,x+30,y+30,com.atom.lib.design.atomchat.UiTokens.COMPOSER_ICON_SIZE,color);
                    var font=com.atom.chat.font.FontManager.font(com.atom.lib.design.atomchat.UiTokens.SETTINGS_TILE_TITLE);
                    com.atom.chat.render.SkiaFontRenderer.drawText(canvas,font,lang.get(entry.getKey()).getAsString(),x+60,
                            com.atom.chat.render.SkiaFontRenderer.centerBaselineY(font,y+30),color);
                    float[] sizes={16,com.atom.lib.design.atomchat.UiTokens.COMPOSER_ICON_SIZE,48};
                    for(int i=0;i<sizes.length;i++)com.atom.chat.render.DrawPrimitives.drawIconCentered(canvas,glyph,x+85+i*45,y+70,sizes[i],color);
                    font=com.atom.chat.font.FontManager.font(com.atom.lib.design.atomchat.UiTokens.SETTINGS_TILE_SUB);
                    com.atom.chat.render.SkiaFontRenderer.drawText(canvas,font,name(glyph),x+215,
                            com.atom.chat.render.SkiaFontRenderer.centerBaselineY(font,y+70),color);
                }
                try(var snapshot=surface.makeImageSnapshot();var png=snapshot.encodeToData(io.github.humbleui.skija.EncodedImageFormat.PNG)) {
                    assertNotNull(png);java.nio.file.Files.write(directory.resolve(light?"headings-light.png":"headings-dark.png"),png.getBytes());
                }
            }
        }
    }
    private static String name(Path glyph)throws Exception {
        for(var type:new Class<?>[]{com.atom.chat.ui.SettingsGroupGlyphs.class,AppIcons.class,com.atom.chat.render.DrawPrimitives.class})
            for(var field:type.getFields())if(field.getType()==Path.class&&field.get(null)==glyph)
                return field.getName().replace("ICON_","").replace("_PATH","");
        throw new AssertionError("unregistered glyph");
    }
}
