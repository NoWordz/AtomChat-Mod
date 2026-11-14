package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.config.ConfigPreviewTransaction;
import com.atom.chat.nav.NavPage;
import com.atom.chat.nav.AppPage;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.render.SoftShadow;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.lib.design.atomchat.SkiaDraw;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SettingsResetVisualTest {
    @BeforeAll static void setup(@TempDir Path dir) throws Exception { UiFeatureRenderTest.setup(dir); }
    @Test void actualSettingsTabUsesTheSharedHeaderActionAndConfirmation() throws Exception {
        com.google.gson.JsonObject language;
        try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/assets/atomchat/lang/zh_cn.json"),java.nio.charset.StandardCharsets.UTF_8)) {
            language=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        }
        java.util.function.Function<String,String> tr=key->language.has(key)?language.get(key).getAsString():key;
        var out=Path.of(System.getProperty("atomchat.repoRoot","../.."),"docs/evidence/2026-11-14-editor-grill",System.getProperty("atomchat.golden.target","local"));
        Files.createDirectories(out);
        try(var preview=ConfigPreviewTransaction.begin(AtomChatConfig.get());
                var locale=com.atom.chat.render.PreviewLanguage.install("zh_cn");
                var surface=Surface.makeRasterN32Premul(1300,1050)) {
            var cfg=AtomChatConfig.get();var layout=UiLayout.ofDetail(80,60,600,920);
            SettingsSectionPage.clearTranslationCache();
            var page=new SettingsSectionPage();page.selectChip(SettingsSection.APPEARANCE,1);
            var chrome=new HeaderChrome(new HeaderChrome.Host() {
                public String label(String key){return tr.apply(key);}
                public String activePrivateName(){return null;}
                public UiLayout.Rect headerRect(){return layout.header;}
                public UiLayout.Rect pushedHeaderRect(){return layout.header;}
                public float frameDt(){return 16;}
                public int textPrimary(){return cfg.textPrimaryColor;}
            });
            var action=new ShellHeader.HeaderAction(chrome.adjacentButton(),1,EditorActionIcons.RESTORE,cfg.textPrimaryColor);
            var nav=NavPage.settingsSection(SettingsSection.APPEARANCE);
            for(boolean dialog:new boolean[]{false,true}) {
                var c=surface.getCanvas();c.clear(0xFF64798B);int saves=c.getSaveCount();
                SoftShadow.drawPanel(c,80,60,600,920,UiTokens.panelRadius());
                SkiaDraw.drawRoundedRect(c,80,60,600,920,UiTokens.panelRadius(),cfg.panelBgColor);
                UiStagger.atFrame(System.currentTimeMillis()+3000,()->page.render(c,layout,SettingsSection.APPEARANCE,-1,-1,0,cfg.accentColor));
                chrome.drawPushedHeader(c,-1,-1,nav,action);PanelBackground.drawBezel(c,layout.rect());
                if(dialog) {
                    page.requestTabReset(SettingsSection.APPEARANCE,(key,error)->fail("opening confirmation must not reset or report"));
                    assertTrue(page.confirmation().hasPendingAction());
                    page.confirmation().render(c,layout.rect(),-1,-1,1000);
                }
                assertEquals(saves,c.getSaveCount());
                try(var image=surface.makeImageSnapshot();var data=image.encodeToData()) {
                    Files.write(out.resolve(dialog?"settings-reset-confirm.png":"settings-reset.png"),data.getBytes());
                }
                if(dialog)page.confirmation().cancel();
            }
            var editor=new PanelEditor(cfg,tr,80,60,600,920,1300,1050);
            var c=surface.getCanvas();c.clear(0xFF64798B);
            SoftShadow.drawPanel(c,80,60,600,920,UiTokens.panelRadius());
            SkiaDraw.drawRoundedRect(c,80,60,600,920,UiTokens.panelRadius(),cfg.panelBgColor);
            UiStagger.atFrame(System.currentTimeMillis()+3000,()->page.render(c,layout,SettingsSection.APPEARANCE,-1,-1,0,cfg.accentColor));
            chrome.drawPushedHeader(c,-1,-1,nav,action);PanelBackground.drawBezel(c,layout.rect());
            editor.render(c,1300,1050,-1,-1,1000);
            try(var image=surface.makeImageSnapshot();var data=image.encodeToData()) {Files.write(out.resolve("panel-eight-way.png"),data.getBytes());}
        }
    }
}
