package com.atom.chat.config;

import com.atom.chat.settings.SettingsFormField;
import com.atom.chat.theme.ThemeService;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiStagger;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SettingsFormStatesTest {
    private Field configField;private Object previous;
    @BeforeEach void install()throws Exception{configField=AtomChatConfig.class.getDeclaredField("instance");configField.setAccessible(true);previous=configField.get(null);}
    @AfterEach void restore()throws Exception{configField.set(null,previous);}
    @Test void realNativeFieldStatesRemainDistinctInBothThemePolarities()throws Exception{
        Path out=Path.of("build/test-artifacts/v090-server-config/states");Files.createDirectories(out);
        for(boolean light:List.of(false,true)){
            var config=new AtomChatConfig();config.animationEnabled=false;if(light)ThemeService.MINIMAL.applyTo(config);configField.set(null,config);
            var states=new LinkedHashMap<String,byte[]>();
            for(String state:List.of("rest","hover","focus","error","disabled")){
                try(var surface=Surface.makeRasterN32Premul(260,90)){
                    surface.getCanvas().clear(config.panelBgColor);var field=new SettingsFormField();field.configure(true,false,10);field.focus(state.equals("focus"));
                    UiStagger.atFrame(1000,()->field.paint(surface.getCanvas(),new UiLayout.Rect(15,20,230,45),"2048",!state.equals("disabled"),state.equals("error"),state.equals("hover")?40:-1,state.equals("hover")?40:-1,1000));
                    try(var image=surface.makeImageSnapshot();var data=image.encodeToData()){
                        assertNotNull(data);byte[] bytes=data.getBytes();states.put(state,bytes);Files.write(out.resolve((light?"light-":"dark-")+state+".png"),bytes);
                    }
                }
            }
            for(var entry:states.entrySet())if(!entry.getKey().equals("rest"))assertFalse(Arrays.equals(states.get("rest"),entry.getValue()),entry.getKey());
        }
    }
}
