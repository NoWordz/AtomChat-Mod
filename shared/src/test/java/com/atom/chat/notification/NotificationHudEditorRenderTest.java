package com.atom.chat.notification;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class NotificationHudEditorRenderTest {
    @Test void realPreviewBalancesFramesAndEditsOnlyCommitOnSave(@TempDir Path dir) throws Exception {
        Platform.install(new Platform.Provider() {
            public Path configDir(){return dir.resolve("config");}
            public Path gameDir(){return dir;}
            public boolean isModLoaded(String id){return false;}
        });
        var field=AtomChatConfig.class.getDeclaredField("instance"); field.setAccessible(true);
        Object before=field.get(null); var config=new AtomChatConfig(); field.set(null,config);
        var existing=NotificationBanner.INSTANCE.newest();
        try(var surface=Surface.makeRasterN32Premul(1280,800);var pixels=new Bitmap()) {
            var canvas=surface.getCanvas(); int count=canvas.getSaveCount();
            var editor=new NotificationHudEditor(k->switch(k) {
                case "atomchat.notify.editor.title" -> "HUD banner layout";
                case "atomchat.notify.editor.hint" -> "Drag to move · Corner to resize · Guides snap to anchors";
                default -> k.substring(k.lastIndexOf('.')+1);
            });
            for(int i=0;i<4;i++) {
                canvas.clear(0xFF253044); editor.render(canvas,1280,800,-1,-1);
                assertEquals(count,canvas.getSaveCount());
                assertSame(existing,NotificationBanner.INSTANCE.newest(),"preview never creates or renews a notice");
            }
            float w=NotificationBanner.hudBaseWidth(1280),h=NotificationBanner.bannerHeight();
            editor.click(640,UiTokens.PANEL_TOP_GAP+h/2,1280,800,0);
            editor.drag(642,402,1280,800); editor.release();
            assertEquals(NotificationHudLayout.Anchor.TOP_CENTER,config.notificationHudPosition,"uncommitted edit stays private");
            var r=NotificationHudLayout.rect(1280,800,w,h,UiTokens.PANEL_TOP_GAP,NotificationHudLayout.Anchor.CENTER,0,0);
            var toolbar=NotificationHudEditorLayout.of(1280,800,r);
            var save=toolbar.save();
            assertEquals(NotificationHudEditor.Action.SAVE,editor.click(save.x()+save.w()/2,save.y()+save.h()/2,1280,800,0));
            editor.save();
            assertEquals(NotificationHudLayout.Anchor.CENTER,config.notificationHudPosition);
            assertEquals(0,config.notificationHudOffsetX); assertEquals(0,config.notificationHudOffsetY);
            assertTrue(Files.exists(dir.resolve("config/atomchat/atomchat-client.json")));
            canvas.clear(0xFF253044); editor.render(canvas,1280,800,-1,-1);
            pixels.allocN32Pixels(1280,800); assertTrue(surface.readPixels(pixels,0,0));
            var bytes=pixels.readPixels(); boolean varied=false;
            for(int i=4;i<bytes.length;i++) if(bytes[i]!=bytes[i%4]) {varied=true;break;}
            assertTrue(varied,"actual UI must paint more than the backdrop");
            Path output=Path.of("build/test-artifacts/hud-editor-preview.png"); Files.createDirectories(output.getParent());
            try(var image=surface.makeImageSnapshot();var data=image.encodeToData()) { Files.write(output,data.getBytes()); }
        } finally {field.set(null,before);}
    }
}
