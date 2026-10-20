package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.theme.AppearancePresetStore;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.QuickPhrasePanel;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SecondaryDotSurfacesTest {
    @TempDir Path directory;
    @BeforeEach void install(){
        Platform.install(new Platform.Provider(){
            public Path configDir(){return directory;}
            public Path gameDir(){return directory;}
            public boolean isModLoaded(String id){return false;}
        });
    }
    @Test void emptyStateHasABoundedAccentHaloBehindTheExistingContent(){
        var cfg=AtomChatConfig.get();var saved=AppearancePresetStore.Snapshot.capture(cfg);
        try(var surface=Surface.makeRasterN32Premul(400,260);var b=new Bitmap()){
            cfg.accentColor=0xFF00FF00;cfg.textSecondaryColor=0xFFFFFFFF;
            int depth=surface.getCanvas().getSaveCount();
            SettingsSectionPage.drawEmptyState(surface.getCanvas(),new UiLayout.Rect(50,40,300,180),"atomchat.conversation.empty");
            assertEquals(depth,surface.getCanvas().getSaveCount());
            b.allocN32Pixels(400,260);assertTrue(surface.readPixels(b,0,0));
            int dots=0;
            for(int y=0;y<260;y++)for(int x=0;x<400;x++){
                int c=b.getColor(x,y);
                if(x<50 || x>=350 || y<40 || y>=220)assertEquals(0,c);
                if((c>>>24)>0 && ((c>>>8)&255)>(c&255)+20 && ((c>>>8)&255)>((c>>>16)&255)+20)dots++;
            }
            assertTrue(dots>30,"the original empty glyph now has a local accent-dot backdrop");
        } finally{saved.applyTo(cfg);}
    }

    @Test void shortConversationListCannotDecorateTheBottomBar() {
        var layout=UiLayout.ofRoot(0,0,500,260);
        try(var surface=Surface.makeRasterN32Premul(500,400);var bitmap=new Bitmap()) {
            var page=new com.atom.chat.page.ConversationListPage(new com.atom.chat.page.PageHost(){
                public void pushPage(com.atom.chat.nav.AppPage page){}
                public void openWorldChat(){}
                public void openPrivateChat(com.atom.chat.chat.PlayerRef target){}
                public void openSettingsSection(com.atom.chat.settings.SettingsSection section){}
                public void popPage(){}
                public void switchRoot(com.atom.chat.nav.AppPage root){}
            });
            page.render(surface.getCanvas(),layout,-1,-1,0);
            bitmap.allocN32Pixels(500,400);assertTrue(surface.readPixels(bitmap,0,0));
            for(int y=(int)Math.ceil(layout.list.bottom());y<400;y++)for(int x=0;x<500;x++)
                assertEquals(0,bitmap.getColor(x,y),"empty decorations cannot extend below the list: "+x+","+y);
        }
    }

    @Test void popupConnectorStaysBetweenThePopupAndComposer() throws Exception {
        var popup=new QuickPhrasePanel(text->{},()->0xFFFF0000);
        var anim=QuickPhrasePanel.class.getDeclaredField("anim");anim.setAccessible(true);anim.setFloat(popup,1F);
        var layout=UiLayout.of(10,10,500,800);
        var rect=QuickPhrasePanel.class.getDeclaredMethod("panelRect",UiLayout.class);rect.setAccessible(true);
        var box=(io.github.humbleui.types.Rect)rect.invoke(null,layout);
        try(var surface=Surface.makeRasterN32Premul(540,850);var b=new Bitmap()){
            popup.render(surface.getCanvas(),layout,-1,-1,0);
            b.allocN32Pixels(540,850);assertTrue(surface.readPixels(b,0,0));
            int red=0;
            for(int y=(int)Math.ceil(box.getBottom());y<(int)layout.inputBar.y();y++)for(int x=10;x<510;x++){
                int c=b.getColor(x,y);
                if(((c>>>16)&255)>((c>>>8)&255)+20 && ((c>>>16)&255)>(c&255)+20)red++;
            }
            assertTrue(red>5,"the popup should visually connect toward its composer key");
            for(int y=(int)Math.ceil(layout.inputBar.y()+2);y<layout.inputBar.y()+8;y++)for(int x=10;x<510;x++){
                int c=b.getColor(x,y);assertFalse(((c>>>16)&255)>((c>>>8)&255)+20 && ((c>>>16)&255)>(c&255)+20,"connector cannot paint into the composer");
            }
        }
    }
}
