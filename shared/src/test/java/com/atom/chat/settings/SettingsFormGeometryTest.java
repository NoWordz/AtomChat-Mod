package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SettingsFormGeometryTest {
    @Test void offscreenRowHoverDecaysWithTheSameSettingsFrameUpdate(){
        var hover=new java.util.HashMap<String,Float>();hover.put("visible",0f);hover.put("offscreen",1f);
        SettingsGroupFrontend.stepHover(hover,"visible",16);assertTrue(hover.get("visible")>0);assertTrue(hover.get("offscreen")<1);
        for(int i=0;i<20;i++)SettingsGroupFrontend.stepHover(hover,null,16);assertEquals(0f,hover.get("offscreen"));assertEquals(0f,hover.get("visible"));
    }
    @TempDir Path temp;
    private Object oldConfig,oldProvider;private java.lang.reflect.Field configField,providerField;
    @BeforeEach void setup()throws Exception{
        configField=AtomChatConfig.class.getDeclaredField("instance");configField.setAccessible(true);oldConfig=configField.get(null);configField.set(null,new AtomChatConfig());
        providerField=Platform.class.getDeclaredField("provider");providerField.setAccessible(true);oldProvider=providerField.get(null);
        Platform.install(new Platform.Provider(){public Path configDir(){return temp;}public Path gameDir(){return temp;}public boolean isModLoaded(String id){return false;}});
    }
    @AfterEach void restore()throws Exception{configField.set(null,oldConfig);providerField.set(null,oldProvider);}
    @Test void oneToFourLinesGrowAndFifthLineStaysInsideTheSameField() throws Exception {
        var field=new SettingsFormField();field.configure(false,true,8192);
        float one=field.preferredHeight("a",300),two=field.preferredHeight("a\nb",300),four=field.preferredHeight("a\nb\nc\nd",300);
        assertEquals(SettingsFormField.BASE_H,one);assertTrue(two>one);assertTrue(four>two);
        assertEquals(four,field.preferredHeight("a\nb\nc\nd\ne",300));
        try(var surface=Surface.makeRasterN32Premul(350,200)){
            var box=new UiLayout.Rect(10,10,300,one);field.paint(surface.getCanvas(),box,"a",true,false,-1,-1,16);
            var top=SettingsFormField.class.getDeclaredField("textTop");top.setAccessible(true);
            var line=SettingsFormField.class.getDeclaredField("LINE_H");line.setAccessible(true);
            assertEquals(box.y()+box.h()/2,top.getFloat(field)+line.getFloat(null)/2,.001f,"single-line text and caret must center in the real field");
            long builds=field.layoutBuilds();for(int i=0;i<25;i++){field.preferredHeight("a",300);field.paint(surface.getCanvas(),box,"a",true,false,-1,-1,16);}
            assertEquals(builds,field.layoutBuilds(),"unchanged frames reuse the same measured line and advances");
            field.preferredHeight("中文😀 text",300);assertEquals(builds+1,field.layoutBuilds());field.preferredHeight("中文😀 text",180);assertEquals(builds+2,field.layoutBuilds());
        }
    }
    @Test void wrappedShortNamesGrowWithoutChangingEnterIntoANewline(){
        var field=new SettingsFormField();field.configure(false,false,48);field.wrap(true);field.focus(true);field.edit.set("A long display name for a narrow field");
        assertTrue(field.preferredHeight(field.edit.text(),110)>SettingsFormField.BASE_H);
        field.key(257,0,"",s->{});assertFalse(field.focused());assertFalse(field.edit.text().contains("\n"));
    }
    @Test void internalWheelScrollUsesTheSameLineModelAsPointerAndCursor() throws Exception {
        var field=new SettingsFormField();field.configure(false,true,8192);String text="zero\none\ntwo\nthree\nfour\nfive";
        float height=field.preferredHeight(text,300);field.focus(true);field.edit.set(text);field.edit.select(0,false);
        try(var surface=Surface.makeRasterN32Premul(350,200)){
            var box=new UiLayout.Rect(10,10,300,height);field.paint(surface.getCanvas(),box,text,true,false,-1,-1,16);
            assertTrue(field.scroll(-1));field.paint(surface.getCanvas(),box,text,true,false,-1,-1,16);
            field.pointer(23,18,false);assertEquals(text.indexOf("one"),field.edit.cursor());
            field.key(269,2,"",s->{});field.paint(surface.getCanvas(),box,text,true,false,-1,-1,16);
            var first=SettingsFormField.class.getDeclaredField("firstLine");first.setAccessible(true);assertEquals(2,first.getInt(field));
        }
    }
}
