package com.atom.chat.ui;


import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.avatar.ColorPickerOverlay;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.settings.*;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConfirmationPickerFamilyTest {
    @BeforeAll static void setup(@TempDir Path root)throws Exception{UiFeatureRenderTest.setup(root);}
    @Test void modalPlateMatchesTheActualColourPickerMaterial() throws Exception {
        var panel=new UiLayout.Rect(10,10,430,800);
        var picker=new ColorPickerOverlay(text->{});
        picker.open(SettingsCatalog.colors(SettingsSection.APPEARANCE).get(0));
        var anim=ColorPickerOverlay.class.getDeclaredField("anim");anim.setAccessible(true);anim.setFloat(picker,1);
        var cardX=ColorPickerOverlay.class.getDeclaredMethod("cardX",UiLayout.Rect.class);cardX.setAccessible(true);
        var cardY=ColorPickerOverlay.class.getDeclaredMethod("cardY",UiLayout.Rect.class);cardY.setAccessible(true);
        var cardH=ColorPickerOverlay.class.getDeclaredMethod("cardH");cardH.setAccessible(true);
        try(var expected=Surface.makeRasterN32Premul(460,820);var actual=Surface.makeRasterN32Premul(460,820)) {
            expected.getCanvas().clear(0xFF343D4B);actual.getCanvas().clear(0xFF343D4B);
            picker.render(expected.getCanvas(),panel,-1,-1,0);
            var dialog=new ConfirmationDialog();dialog.openDangerous("清空记录","无法恢复","取消","确认清除",()->{});
            dialog.render(actual.getCanvas(),panel,-1,-1,200);
            var box=dialog.geometry().surface();
            assertEquals(pixel(expected,(int)(float)cardX.invoke(null,panel)+5,(int)((float)cardY.invoke(null,panel)+(float)cardH.invoke(null)/2)),
                    pixel(actual,(int)box.x()+5,(int)(box.y()+box.h()/2)),"confirmation must use the existing picker plate");
        }
    }
    @Test void destructiveConfirmAndWarningAreRedWhileCancelHasNoAccentRing() {
        try(var surface=Surface.makeRasterN32Premul(460,820)) {
            var dialog=new ConfirmationDialog();dialog.openDangerous("清空记录","无法恢复","取消","确认清除",()->{});
            surface.getCanvas().clear(0);dialog.render(surface.getCanvas(),new UiLayout.Rect(10,10,430,800),-1,-1,200);
            var g=dialog.geometry();var b=g.confirm();
            int confirm=pixel(surface,(int)b.x()+8,(int)(b.y()+b.h()/2));
            assertTrue(red(confirm),"destructive confirm must use danger red");
            b=g.cancel();int edge=pixel(surface,(int)b.x()+2,(int)(b.y()+b.h()/2));
            assertTrue((edge&255)<=((edge>>>16)&255)+15,"cancel has no blue accent ring");
            int count=0;var box=g.surface();int x=(int)(box.x()+g.padding()),y=(int)(box.y()+g.padding());
            for(int iy=y;iy<y+UiTokens.HEADER_HEIGHT;iy++)for(int ix=x;ix<x+UiTokens.COMPOSER_ICON_SIZE;ix++)if(red(pixel(surface,ix,iy)))count++;
            assertTrue(count>8,"title needs the red warning triangle");
        }
    }
    private static boolean red(int c){return ((c>>>16)&255)>((c>>>8)&255)*2&&((c>>>16)&255)>(c&255)*2;}
    @Test void confirmedHistoryCopyFitsTheNarrowPanelOnAnyFont() {
        var panel=new UiLayout.Rect(15,15,260,310);
        try(var surface=Surface.makeRasterN32Premul(460,820)) {
            var dialog=new ConfirmationDialog();
            dialog.openDangerous("清空聊天记录",UiFeatureRenderTest.tr("atomchat.confirm.history_clear"),"取消","确认清除",()->{});
            dialog.render(surface.getCanvas(),panel,-1,-1,200);
            var g=dialog.geometry();
            // CJK fallback metrics differ per OS: the same copy wraps to two lines
            // with the Windows fallback and three on Linux. Allow one extra line,
            // but never let the approved copy push the dialog out of the panel.
            assertTrue(g.surface().h()<=ConfirmationLayout.of(panel,3).surface().h()+0.5F,
                    "the approved history copy must stay within three visual lines");
            assertTrue(g.surface().contains(g.cancel())&&g.surface().contains(g.confirm()),
                    "both buttons stay inside the dialog at the narrow width");
        }
    }
    @Test void cancelAndConfirmStaySideBySideInNormalAndNarrowPanels() {
        for(float width:new float[]{260,430}) {
            try(var surface=Surface.makeRasterN32Premul(460,820)) {
                var dialog=new ConfirmationDialog();
                dialog.openDangerous("清空聊天记录",UiFeatureRenderTest.tr("atomchat.confirm.history_clear"),"取消","确认清除",()->{});
                dialog.render(surface.getCanvas(),new UiLayout.Rect(15,15,width,310),-1,-1,200);
                var g=dialog.geometry();
                assertEquals(g.cancel().y(),g.confirm().y(),"cancel left, confirm right at every supported width");
                assertTrue(g.cancel().right()<g.confirm().x());
                assertTrue(g.surface().contains(g.cancel()));assertTrue(g.surface().contains(g.confirm()));
                var font=com.atom.chat.font.FontManager.font(UiTokens.FONT_BUTTON);
                assertTrue(com.atom.chat.render.SkiaFontRenderer.getStringWidth(font,"确认清除")<g.confirm().w());
            }
        }
    }
    @Test void realChineseAndEnglishCopyKeepsAllContentInsideNarrowAndNormalWidths() throws Exception {
        for(String locale:new String[]{"zh_cn","en_us"}) {
            try(var in=getClass().getResourceAsStream("/assets/atomchat/lang/"+locale+".json")) {
                assertNotNull(in);
                var lang=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for(String key:new String[]{"history_clear","history_clear.memory","wallpaper_clear","cache_clear"}) {
                    String copy=lang.get("atomchat.confirm."+key).getAsString();
                    for(float width:new float[]{220,350}) {
                        var text=ConfirmationText.fit(copy,width,UiTokens.SETTINGS_TILE_SUB);
                        var font=com.atom.chat.font.FontManager.font(text.size());
                        var panel=width==220?new UiLayout.Rect(15,15,260,310):new UiLayout.Rect(15,15,420,780);
                        var geometry=ConfirmationLayout.of(panel,text.lines().size(),text.lineHeight());
                        float bodyHeight=geometry.cancel().y()-geometry.surface().y()-geometry.padding()*1.5F-UiTokens.HEADER_HEIGHT;
                        assertTrue(text.lines().size()*text.lineHeight()<=bodyHeight,"all copy must remain above the buttons: "+locale+" "+key);
                        assertTrue(text.size()>=UiTokens.DIALOG_MIN_TEXT_SIZE);
                        assertEquals(copy.replaceAll("\\s",""),String.join("",text.lines()).replaceAll("\\s",""));
                        for(String line:text.lines()) {
                            assertTrue(com.atom.chat.render.SkiaFontRenderer.getStringWidth(font,line)<=width+.01F,line);
                            assertFalse(line.codePointCount(0,line.length())==1&&Character.UnicodeScript.of(line.codePointAt(0))==Character.UnicodeScript.HAN,line);
                        }
                    }
                }
            }
        }
    }
    private static int pixel(Surface s,int x,int y){try(var b=new Bitmap()){b.allocN32Pixels(1,1);assertTrue(s.readPixels(b,x,y));return b.getColor(0,0);}}
}
