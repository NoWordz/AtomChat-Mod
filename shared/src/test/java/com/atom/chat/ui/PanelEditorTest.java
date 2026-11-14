package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import io.github.humbleui.skija.Surface;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.atom.lib.design.atomchat.UiTokens;
import static org.junit.jupiter.api.Assertions.*;

class PanelEditorTest {
    private Object previousProvider;
    @org.junit.jupiter.api.BeforeEach void installPlatform(@TempDir Path dir) throws Exception {
        var field = Platform.class.getDeclaredField("provider");
        field.setAccessible(true);
        previousProvider = field.get(null);
        Platform.install(new Platform.Provider() {
            public Path configDir() { return dir; }
            public Path gameDir() { return dir; }
            public boolean isModLoaded(String id) { return false; }
        });
    }
    @org.junit.jupiter.api.AfterEach void restorePlatform() throws Exception {
        var field = Platform.class.getDeclaredField("provider");
        field.setAccessible(true);
        field.set(null, previousProvider);
    }
    @Test void onlyHandlesInterceptAndCancelRestoresGeometry(@TempDir Path dir) throws Exception {
        var provider=Platform.class.getDeclaredField("provider"); provider.setAccessible(true); Object previous=provider.get(null);
        Platform.install(new Platform.Provider(){public Path configDir(){return dir;} public Path gameDir(){return dir;} public boolean isModLoaded(String id){return false;}});
        try {
            var cfg=new AtomChatConfig(); float oldWidth=cfg.panelWidth, oldHeight=cfg.panelHeight;
            var editor=new PanelEditor(cfg, key -> key, 100,100,300,250,900,700);
            assertEquals(PanelEditor.Result.PASS,editor.click(200,200,0,900,700));
            assertEquals(PanelEditor.Result.CONSUMED,editor.click(250,100-UiTokens.EDGE_CONTROL_INSET*3-UiTokens.COMPOSER_ICON_SIZE/2,0,900,700));
            assertTrue(editor.drag(350,150,900,700)); assertTrue(editor.release(900,700));
            assertTrue(cfg.panelPositionCustom); assertTrue(cfg.panelPositionX>0);
            editor.cancel();
            assertFalse(cfg.panelPositionCustom); assertEquals(oldWidth,cfg.panelWidth); assertEquals(oldHeight,cfg.panelHeight);
        } finally { provider.set(null,previous); }
    }



    @Test void writesEditorPreview(@TempDir Path dir) throws Exception {
        var provider=Platform.class.getDeclaredField("provider"); provider.setAccessible(true); Object previous=provider.get(null);
        Platform.install(new Platform.Provider(){public Path configDir(){return dir;} public Path gameDir(){return dir;} public boolean isModLoaded(String id){return false;}});
        try {
        var cfg=AtomChatConfig.get(); var saved=com.atom.chat.theme.AppearancePresetStore.Snapshot.capture(cfg);
        boolean oldHalf=cfg.halfScreenMode, oldCustom=cfg.panelPositionCustom;
        float oldX=cfg.panelPositionX,oldY=cfg.panelPositionY;
        com.google.gson.JsonObject language;
        try (var reader = new java.io.InputStreamReader(getClass().getResourceAsStream("/assets/atomchat/lang/zh_cn.json"), java.nio.charset.StandardCharsets.UTF_8)) {
            language = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        }
        var editor=new PanelEditor(cfg,key -> language.has(key) ? language.get(key).getAsString() : key,80,60,480,520,1100,760);
        try(var locale=com.atom.chat.render.PreviewLanguage.install("zh_cn"); var surface=Surface.makeRasterN32Premul(1100,760)) {
            surface.getCanvas().clear(0xFF454B55);
            com.atom.lib.design.atomchat.UiCards.drawCard(surface.getCanvas(),80,60,480,520,UiTokens.cardRadius(),0);
            var layout=UiLayout.ofDetail(80,60,480,520);
            new com.atom.chat.settings.SettingsSectionPage().render(surface.getCanvas(),layout,com.atom.chat.settings.SettingsSection.APPEARANCE,-1,-1,0,cfg.accentColor);
            editor.render(surface.getCanvas(),1100,760,-1,-1,1000);
            Path out=Path.of(System.getProperty("atomchat.repoRoot","../.."),".work/evidence/2026-11-13-followup/panel-editor.png");
            Files.createDirectories(out.getParent());
            try(var image=surface.makeImageSnapshot();var png=image.encodeToData()){Files.write(out,png.getBytes());}
        } finally { cfg.halfScreenMode=false; saved.applyTo(cfg); cfg.halfScreenMode=oldHalf;
            cfg.panelPositionCustom=oldCustom; cfg.panelPositionX=oldX; cfg.panelPositionY=oldY; }
        } finally { provider.set(null,previous); }
    }


    @Test void editingTheFrameNeverChangesContentZoom() {
        var cfg = new AtomChatConfig();
        cfg.contentScale = 1.25F;
        var editor = new PanelEditor(cfg, key -> key, 80, 80, 240, 200, 720, 560);
        editor.click(320, 180, 0, 720, 560);
        assertTrue(editor.drag(360, 180, 720, 560));
        assertEquals(1.25F, cfg.contentScale, .0001F);
        assertTrue(editor.release(720, 560));
    }


    @Test void aRealSettingsSliderIsNotOverwrittenByTheNextEditorFrame() {
        var cfg = new AtomChatConfig();
        var editor = new PanelEditor(cfg, key -> key, 100, 100, 300, 250, 900, 700);
        cfg.panelWidth = 360;
        cfg.panelHeight = 350;
        try (var surface = Surface.makeRasterN32Premul(900, 700)) {
            editor.render(surface.getCanvas(), 900, 700, -1, -1, 1000);
        }
        assertEquals(360, cfg.panelWidth, .001F);
        assertEquals(350, cfg.panelHeight, .001F);
        editor.followPanel(100, 100, UiTokens.s(cfg.panelWidth), UiTokens.s(cfg.panelHeight), 900, 700);
        float edge = 100 + UiTokens.s(cfg.panelWidth);
        float middle = 100 + UiTokens.s(cfg.panelHeight) / 2;
        editor.click(edge, middle, 0, 900, 700);
        assertTrue(editor.drag(edge + UiTokens.s(20), middle, 900, 700));
        assertEquals(380, cfg.panelWidth, .001F, "the next grip starts at the new real settings width");
    }


    @Test void theTopBorderResizesUpwardAndNeverMovesTheWholePanel() {
        var cfg = new AtomChatConfig();
        var editor = new PanelEditor(cfg, key -> key, 100, 100, 300, 250, 900, 700);
        float width = cfg.panelWidth, height = cfg.panelHeight;
        assertEquals(PanelEditor.Result.CONSUMED, editor.click(180, 100, 0, 900, 700));
        assertTrue(editor.drag(180, 70, 900, 700));
        assertTrue(editor.release(900, 700));
        assertEquals(width, cfg.panelWidth, .001F);
        assertTrue(cfg.panelHeight > height, "top border owns north resize, not move");
    }
    @Test void anyPointOnTheSideBorderResizesWidthWithoutMovingHeight() {
        var cfg = new AtomChatConfig();
        var editor = new PanelEditor(cfg, key -> key, 100, 100, 300, 250, 900, 700);
        float width = cfg.panelWidth, height = cfg.panelHeight;
        assertEquals(PanelEditor.Result.CONSUMED, editor.click(400, 180, 0, 900, 700));
        assertTrue(editor.drag(450, 180, 900, 700));
        assertTrue(editor.release(900, 700));
        assertTrue(cfg.panelWidth > width);
        assertEquals(height, cfg.panelHeight, .001F);
    }
    @Test void pressingAndReleasingTheToolbarTitleDoesNotTeleportIt() {
        var cfg = new AtomChatConfig();
        var editor = new PanelEditor(cfg, key -> key, 100, 100, 300, 250, 900, 700);
        var title = PanelEditorLayout.of(900, 700, new UiLayout.Rect(100, 100, 300, 250)).title();
        float x = title.x() + title.w()/2, y = title.y() + title.h()/2;
        editor.click(x, y, 0, 900, 700);
        try (var surface = Surface.makeRasterN32Premul(900, 700)) {
            editor.render(surface.getCanvas(), 900, 700, -1, -1, 16);
        }
        assertTrue(editor.overToolbar(x, y, 900, 700), "title press must preserve its painted location");
        assertTrue(editor.release(900, 700));
        assertTrue(editor.overToolbar(x, y, 900, 700));
    }
    @Test void confirmedAdjustResetCannotBeUndoneByTheOldEditorCancelSnapshot() {
        var cfg=new AtomChatConfig(); cfg.panelWidth=560;
        var editor=new PanelEditor(cfg,key->key,100,100,UiTokens.s(560),400,1100,800);
        var dialog=new ConfirmationDialog();var saves=new java.util.concurrent.atomic.AtomicInteger();
        var prompt=new com.atom.chat.settings.SettingsTabResetPrompt(dialog,()->cfg,
                c->{saves.incrementAndGet();return true;},key->key,(key,error)->{},editor::finishAfterSettingsReset);
        prompt.request(com.atom.chat.settings.SettingsSection.APPEARANCE,"adjust","Adjust");
        try(var surface=Surface.makeRasterN32Premul(1100,800)) {
            dialog.render(surface.getCanvas(),new UiLayout.Rect(0,0,1100,800),-1,-1,1000);
        }
        dialog.key(258);dialog.key(257);
        assertTrue(editor.closing(),"approved reset exits the editor");
        editor.cancel();
        assertEquals(AtomChatConfig.DEFAULT.panelWidth,cfg.panelWidth,.001F,"removed/Escape cannot undo an already confirmed reset");
        assertEquals(1,saves.get(),"finishing after reset must not save twice");
    }
}
