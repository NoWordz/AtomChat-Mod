package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.onboarding.*;
import com.atom.chat.render.PreviewLanguage;
import com.atom.chat.render.SoftShadow;
import com.atom.chat.settings.*;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Production settings rows, scroll geometry and guide paint, without a parallel demo panel. */
class TutorialVisualSmokeTest {
    @BeforeAll static void setup(@TempDir Path root)throws Exception{UiFeatureRenderTest.setup(root);}
    @Test void realGroupsHaveReadableExternalAnnotationsInBothLocales() throws Exception {
        Path out=Path.of(System.getProperty("atomchat.repoRoot","../.."),"docs/evidence/2026-11-13-ui-refinement",
                System.getProperty("atomchat.golden.target","local"));Files.createDirectories(out);
        try (var preview = com.atom.chat.config.ConfigPreviewTransaction.begin(com.atom.chat.config.AtomChatConfig.get())) {
        com.atom.chat.theme.ThemeService.applyFactoryDefaults(com.atom.chat.config.AtomChatConfig.get());
        for(String locale:new String[]{"zh_cn","en_us"})try(var language=PreviewLanguage.install(locale)) {
            SettingsSectionPage.clearTranslationCache();
            var page=new SettingsSectionPage();
            com.google.gson.JsonObject copy;
            try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/assets/atomchat/lang/"+locale+".json"),java.nio.charset.StandardCharsets.UTF_8)) {
                copy=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            }
            java.util.function.Function<String,String> tr=key->copy.has(key)?copy.get(key).getAsString():key;
            var guide=TutorialGuide.build(page,tr);
            for(var section:new SettingsSection[]{SettingsSection.APPEARANCE,SettingsSection.CHAT}) {
                var entry=guide.stream().filter(e->e.section()==section&&e.chip()==1).findFirst().orElseThrow();
                var session=new TutorialSession();session.itinerary(guide);session.start();
                while(session.entry()!=entry)session.next();
                page.selectChip(section,entry.chip());
                var layout=UiLayout.ofDetail(40,40,600,1000);var scroll=new ScrollController();
                page.tutorialAnchor(section,entry.row(),layout,scroll);
                try(var surface=Surface.makeRasterN32Premul(1600,1080)) {
                    var canvas=surface.getCanvas();canvas.clear(0xFF718092);int saves=canvas.getSaveCount();
                    long start=System.currentTimeMillis();
                    for(int frame=0;frame<32;frame++) {
                        canvas.clear(0xFF718092);
                        SoftShadow.drawPanel(canvas,40,40,600,1000,UiTokens.panelRadius());
                        SkiaDraw.drawRoundedRect(canvas,40,40,600,1000,UiTokens.panelRadius(),AtomChatConfig.get().panelBgColor);
                        final long time=start+frame*16;
                        UiStagger.atFrame(time,()->page.render(canvas,layout,section,-1,-1,scroll.getScrollY(),AtomChatConfig.get().accentColor));
                    }
                    PanelBackground.drawBezel(canvas,layout.rect());
                    var target=page.tutorialGroupAnchor(section,entry.row(),entry.lastRow(),layout,scroll.getScrollY());
                    var overlay=new TutorialOverlay();overlay.viewport(new UiLayout.Rect(0,0,1600,1080));
                    overlay.shape(new TutorialAnchor(target,UiTokens.settingsRowRadius()));
                    overlay.render(canvas,session,layout,layout,layout.tabBar,tr,-1,-1,1000);
                    var card=overlay.geometry().card();
                    assertTrue(card.x()>=layout.rect().right(),"desktop annotation must stay outside the phone");
                    assertTrue(new UiLayout.Rect(0,0,1600,1080).contains(card));
                    assertEquals(saves,canvas.getSaveCount());
                    try(var image=surface.makeImageSnapshot();var data=image.encodeToData(EncodedImageFormat.PNG)) {
                        Files.write(out.resolve("tutorial-"+locale+"-"+section.name().toLowerCase()+".png"),data.getBytes());
                    }
                    overlay.close();
                }
            }
        }
        SettingsSectionPage.clearTranslationCache();
        }
    }
}
