package com.atom.chat.fun;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.settings.*;
import com.atom.chat.ui.SettingsGroupGlyphs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentalEffectsTest {
    private AtomChatConfig cfg;
    @BeforeEach void setup(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        com.atom.chat.platform.Platform.install(new com.atom.chat.platform.Platform.Provider() {
            public java.nio.file.Path configDir(){return root.resolve("config");}
            public java.nio.file.Path gameDir(){return root;}
            public boolean isModLoaded(String id){return false;}
        });
        cfg=new AtomChatConfig();
        var field=AtomChatConfig.class.getDeclaredField("instance");field.setAccessible(true);field.set(null,cfg);
    }
    @Test void defaultOffAndEffectsFoldHasOnlyOneSwitchImmediatelyAfterFun() throws Exception {
        assertFalse(AtomChatConfig.class.getField("experimentalEffectsEnabled").getBoolean(cfg));
        var page=new SettingsSectionPage();
        var chips=page.chips(SettingsSection.CHAT);
        int effects=java.util.stream.IntStream.range(0,chips.size()).filter(i -> chips.get(i).id().equals("effects")).findFirst().orElseThrow();
        page.selectChip(SettingsSection.CHAT,effects);
        var rows=page.rows(SettingsSection.CHAT);int fun=-1,experimental=-1;
        for(int i=0;i<rows.size();i++) {
            if("atomchat.settings.group.chat.fun".equals(rows.get(i).labelKey()))fun=i;
            if("atomchat.settings.group.chat.experimental".equals(rows.get(i).labelKey()))experimental=i;
        }
        assertTrue(fun>=0);assertEquals(fun+2,experimental);
        assertEquals("experimental_effects",rows.get(experimental+1).item().id());
        assertEquals(rows.size()-1,experimental+1);
        assertSame(SettingsGroupGlyphs.EXPERIMENTAL,SettingsGroupIcons.forLabel("atomchat.settings.group.chat.experimental"));
        var gson=new com.google.gson.Gson();
        assertFalse(gson.fromJson("{}",AtomChatConfig.class).experimentalEffectsEnabled,"old config files default off");
        var item=rows.get(experimental+1).item();item.set(true);
        var saved=com.atom.chat.platform.Platform.configDir().resolve("atomchat/atomchat-client.json");
        assertTrue(gson.fromJson(java.nio.file.Files.readString(saved),AtomChatConfig.class).experimentalEffectsEnabled);
    }
}
