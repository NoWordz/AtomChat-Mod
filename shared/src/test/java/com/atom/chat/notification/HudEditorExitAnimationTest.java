package com.atom.chat.notification;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class HudEditorExitAnimationTest {
    @TempDir Path tmp;
    @Test void closeWaitsForFadeAndBlocksFurtherEdits() throws Exception {
        Platform.install(new Platform.Provider(){public Path configDir(){return tmp.resolve("config");}public Path gameDir(){return tmp;}public boolean isModLoaded(String id){return false;}});
        var editor=new NotificationHudEditor(k->k);
        try(var surface=Surface.makeRasterN32Premul(1000,800)) {
            editor.render(surface.getCanvas(),1000,800,-1,-1);
            var close=NotificationHudEditor.class.getMethod("requestClose");var closed=NotificationHudEditor.class.getMethod("closed");
            close.invoke(editor);assertFalse((boolean)closed.invoke(editor),"the native screen must survive the first exit frame");
            var render=NotificationHudEditor.class.getMethod("render",io.github.humbleui.skija.Canvas.class,float.class,float.class,float.class,float.class,float.class);
            for(int i=0;i<30;i++)render.invoke(editor,surface.getCanvas(),1000f,800f,-1f,-1f,16f);
            assertTrue((boolean)closed.invoke(editor));
        }
    }
}
