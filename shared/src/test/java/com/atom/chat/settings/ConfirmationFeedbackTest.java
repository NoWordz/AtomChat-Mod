package com.atom.chat.settings;



import com.atom.lib.design.atomchat.AppIcons;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.*;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConfirmationFeedbackTest {
    @BeforeAll static void setup(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir(){return root.resolve("config");}
            public Path gameDir(){return root;}
            public boolean isModLoaded(String id){return false;}
        });
    }
    private static SettingsSectionPage history() throws Exception {
        var page=new SettingsSectionPage();
        var chips=page.chips(SettingsSection.CHAT);
        for(int i=0;i<chips.size();i++) if(chips.get(i).id().equals("history")) page.selectChip(SettingsSection.CHAT,i);
        // The chip switch replays the page entrance; settling it is what "the
        // switch has finished" means now (there is no crossfade clock left).
        var entrance=SettingsSectionPage.class.getDeclaredField("entrance");entrance.setAccessible(true);
        ((com.atom.chat.ui.UiStagger.Entrance) entrance.get(page)).markSettled();
        return page;
    }
    @Test void modalConsumesReleaseWithoutLeavingTheOriginCardPermanentlyPressed() throws Exception {
        var page=history();page.setActionHandler(id->fail("cancel cannot execute cleanup"));
        var rows=page.rows(SettingsSection.CHAT);
        int index=0;while(!"history_clear".equals(rows.get(index).actionId()))index++;
        var layout=UiLayout.ofDetail(15,15,420,780);
        var rect=page.rowRect(SettingsSection.CHAT,rows,index,0,layout);
        page.setPressedRow(index);
        assertTrue(page.perform(new SettingsSectionPage.RowHit(rows.get(index),index,rect.x(),rect.y(),rect.w(),rect.h(),rect.x())));
        var modal=new ConfirmationInput(page::confirmation,x->(float)x,y->(float)y);
        assertTrue(modal.onRelease(rect.x(),rect.y(),0),"the modal owns the original mouse-up");
        page.confirmation().cancel();
        var clock=SettingsSectionPage.class.getDeclaredField("lastFrameMs");clock.setAccessible(true);
        try(var surface=Surface.makeRasterN32Premul(460,820)) {
            page.confirmation().render(surface.getCanvas(),layout.rect(),-1,-1,200);
            for(int i=0;i<20;i++) {
                clock.setLong(page,System.currentTimeMillis()-16);
                page.render(surface.getCanvas(),layout,SettingsSection.CHAT,rect.x()+20,rect.y()+rect.h()/2,0,0xFF4488FF);
            }
        }
        var field=SettingsSectionPage.class.getDeclaredField("aboutCardPress");field.setAccessible(true);
        var spring=(PressScale)((Map<?,?>)field.get(page)).get(index);
        assertNotNull(spring);
        assertTrue(spring.scale()>1,"hover must recover after cancelling, actual scale="+spring.scale());
    }
    @Test void foldedHeadingReservesItsLeftColumnForTheExistingClockGlyph() throws Exception {
        var page=history();var row=page.rows(SettingsSection.CHAT).stream()
                .filter(r->"atomchat.settings.group.history.storage".equals(r.labelKey())).findFirst().orElseThrow();
        var rect=new UiLayout.Rect(20,20,400,UiTokens.SETTINGS_LABEL_H);
        var draw=SettingsSectionPage.class.getDeclaredMethod("drawLabel",Canvas.class,SettingsSectionPage.Row.class,UiLayout.Rect.class);
        draw.setAccessible(true);
        float size=UiTokens.COMPOSER_ICON_SIZE,x=rect.x()+UiTokens.SETTINGS_ROW_PAD,cy=rect.y()+rect.h()/2;
        try(var actual=Surface.makeRasterN32Premul(460,100);var expected=Surface.makeRasterN32Premul(460,100)) {
            actual.getCanvas().clear(0);expected.getCanvas().clear(0);
            draw.invoke(page,actual.getCanvas(),row,rect);
            com.atom.chat.render.DrawPrimitives.drawIconCentered(expected.getCanvas(),AppIcons.ICON_CLOCK_PATH,x+size/2,cy,size,AtomChatConfig.get().textPrimaryColor);
            assertArrayEquals(pixels(expected,(int)x,(int)(cy-size/2),(int)size),pixels(actual,(int)x,(int)(cy-size/2),(int)size));
        }
    }
    private static byte[] pixels(Surface surface,int x,int y,int size){
        try(var b=new Bitmap()){b.allocN32Pixels(size,size);assertTrue(surface.readPixels(b,x,y));return b.readPixels();}
    }
}
