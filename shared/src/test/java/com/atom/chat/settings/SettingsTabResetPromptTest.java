package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.ConfirmationDialog;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTabResetPromptTest {
    private Object previousProvider;
    @org.junit.jupiter.api.BeforeEach void install(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var field=com.atom.chat.platform.Platform.class.getDeclaredField("provider");field.setAccessible(true);
        previousProvider=field.get(null);
        com.atom.chat.platform.Platform.install(new com.atom.chat.platform.Platform.Provider() {
            public java.nio.file.Path configDir(){return dir;}
            public java.nio.file.Path gameDir(){return dir;}
            public boolean isModLoaded(String id){return false;}
        });
    }
    @org.junit.jupiter.api.AfterEach void restore() throws Exception {
        var field=com.atom.chat.platform.Platform.class.getDeclaredField("provider");field.setAccessible(true);field.set(null,previousProvider);
    }
    private void settle(ConfirmationDialog dialog) {
        try(var surface=io.github.humbleui.skija.Surface.makeRasterN32Premul(600,900)) {
            dialog.render(surface.getCanvas(),new com.atom.chat.ui.UiLayout.Rect(0,0,600,900),-1,-1,1000);
        }
    }
    @Test void confirmationIsRequiredAndUsesTheCapturedTabExactlyOnce() {
        var config = new AtomChatConfig(); config.blurStrength=.5F; config.accentColor=123;
        var dialog = new ConfirmationDialog(); var saves=new AtomicInteger();
        var feedback = new ArrayList<String>();
        var prompt = new SettingsTabResetPrompt(dialog,()->config,c->{saves.incrementAndGet();return true;},
                key->key.equals("atomchat.settings.reset.body")?"Restore %s":key,(key,failed)->feedback.add(key),()->{});
        assertTrue(prompt.request(SettingsSection.APPEARANCE,"display","Appearance · Display"));
        assertEquals(.5F,config.blurStrength); assertEquals(0,saves.get());
        dialog.cancel(); assertEquals(.5F,config.blurStrength); assertEquals(0,saves.get());
        prompt.request(SettingsSection.APPEARANCE,"display","Appearance · Display");
        settle(dialog); dialog.key(258); dialog.key(257); dialog.key(257);
        assertEquals(AtomChatConfig.DEFAULT.blurStrength,config.blurStrength);
        assertEquals(123,config.accentColor); assertEquals(1,saves.get());
        assertTrue(feedback.contains("atomchat.settings.reset.success"));
    }
    @Test void failedPersistenceRollsBackAndReportsAnError() {
        var config=new AtomChatConfig();config.blurStrength=.5F;
        var dialog=new ConfirmationDialog();var errors=new AtomicInteger();
        var prompt=new SettingsTabResetPrompt(dialog,()->config,c->false,key->key,(key,failed)->{if(failed)errors.incrementAndGet();},()->fail("no success callback"));
        prompt.request(SettingsSection.APPEARANCE,"display","Display");settle(dialog);dialog.key(258);dialog.key(257);
        assertEquals(.5F,config.blurStrength);assertEquals(1,errors.get());
    }
}
