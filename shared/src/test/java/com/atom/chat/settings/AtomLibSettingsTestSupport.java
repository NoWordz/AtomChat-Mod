package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.lib.AtomLibRuntime;
import com.atom.lib.RuntimeHandle;
import com.atom.lib.font.*;
import com.atom.lib.text.TextService;
import com.atom.lib.style.*;
import com.atom.lib.view.HostContext;
import java.lang.reflect.Field;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Supplies AC's real licensed font and temporary configuration to real Lib components. */
abstract class AtomLibSettingsTestSupport {
    @TempDir Path directory;
    private Object oldConfig,oldPlatform;
    private Field configField,platformField;
    RuntimeHandle runtime;
    TextService text;
    HostContext host;
    @BeforeEach void hostServices() throws Exception {
        configField=AtomChatConfig.class.getDeclaredField("instance");configField.setAccessible(true);oldConfig=configField.get(null);configField.set(null,null);
        platformField=Platform.class.getDeclaredField("provider");platformField.setAccessible(true);oldPlatform=platformField.get(null);
        Platform.install(new Platform.Provider() {
            public Path configDir(){return directory;}
            public Path gameDir(){return directory;}
            public boolean isModLoaded(String id){return false;}
        });
        AtomChatConfig.reload();
        runtime=AtomLibRuntime.open("atomchat-settings-test");
        var fonts=new FontRegistry(runtime);var fontId=new FontId("atomchat.test.body");
        try(var in=getClass().getResourceAsStream("/assets/atomchat/font/bundled.otf")) {
            assertNotNull(in,"The test host supplies AC's font; AtomLib must not bundle it");
            fonts.register(fontId,FontSource.bytes(in.readAllBytes()));
        }
        text=new TextService(fonts);
        host=new HostContext(fonts,fontId,text,new ThemeSnapshot(new Palette(0,0xff202020,0xff404040,0xff4488cc,0xffffffff,0xffffffff,0xffaaaaaa,0xffcc4444),
            new TypeScale(18,22,15),new SpacingScale(4,10,16,36),new RadiusScale(12,8),new MotionPolicy(true)),()->{});
    }
    @AfterEach void releaseHost() throws Exception {
        if(text!=null)text.close();if(runtime!=null)runtime.close();
        if(configField!=null)configField.set(null,oldConfig);if(platformField!=null)platformField.set(null,oldPlatform);
    }
    SettingsSlider catalogWidth(){return SettingsCatalog.sliders(SettingsSection.APPEARANCE).stream().filter(s->s.id().equals("width")).findFirst().orElseThrow();}
}
