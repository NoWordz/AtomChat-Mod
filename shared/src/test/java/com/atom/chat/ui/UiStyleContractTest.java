package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.config.ConfigPreviewTransaction;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.lib.design.atomchat.AppIcons;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** User-approved UI invariants; changes require explicit review, never a golden re-record. */
class UiStyleContractTest {
    @BeforeAll static void setup(@TempDir Path dir) throws Exception { UiFeatureRenderTest.setup(dir); }

    private void assertWarning(ConfirmationDialog dialog) {
        try(var surface=Surface.makeRasterN32Premul(800,1000);var pixels=new Bitmap()) {
            var panel=new UiLayout.Rect(0,0,800,1000);
            dialog.render(surface.getCanvas(),panel,-1,-1,1000);
            pixels.allocN32Pixels(800,1000);assertTrue(surface.readPixels(pixels,0,0));
            var geometry=dialog.geometry();var button=geometry.confirm();
            int color=pixels.getColor((int)(button.x()+button.w()/2),(int)(button.y()+button.h()*.2F));
            assertEquals(UiTokens.dangerColor(),color,"warning confirm must use the established danger token");
            float x=geometry.surface().x()+geometry.padding(), cy=geometry.titleCenterY();
            int red=0;
            for(int py=(int)(cy-UiTokens.COMPOSER_ICON_SIZE/2);py<cy+UiTokens.COMPOSER_ICON_SIZE/2;py++) {
                for(int px=(int)x;px<x+UiTokens.COMPOSER_ICON_SIZE;px++) {
                    int pixel=pixels.getColor(px,py),r=(pixel>>>16)&255,g=(pixel>>>8)&255,b=pixel&255;
                    if(r>g*1.5F && r>b*1.5F && (pixel>>>24)>180)red++;
                }
            }
            assertTrue(red>10,"warning mark must be red, not theme accent");
        }
    }
    @Test void warningAndResetDialogsKeepRedWithAGreenTheme() {
        try(var preview=ConfigPreviewTransaction.begin(AtomChatConfig.get())) {
            AtomChatConfig.get().accentColor=0xFF35B893;
            var legacy=new ConfirmationDialog();legacy.open("Warning","Restore settings","Cancel","Restore",()->{});
            assertWarning(legacy);
        }
    }
    @Test void realTabResetUsesTheSameWarningColours() {
        try(var preview=ConfigPreviewTransaction.begin(AtomChatConfig.get())) {
            AtomChatConfig.get().accentColor=0xFF35B893;
            var page=new SettingsSectionPage();page.selectChip(SettingsSection.APPEARANCE,1);
            page.requestTabReset(SettingsSection.APPEARANCE,(key,error)->fail("must only open"));
            assertWarning(page.confirmation());page.confirmation().cancel();
        }
    }
    @Test void headerIconsUseTheSameSizeAndAreVisuallyCenteredInTheirHoverRect() {
        int samples=0;
        for(var icon:new io.github.humbleui.skija.Path[]{AppIcons.ICON_BACK_PATH,AppIcons.ICON_FILTER_PATH,
                AppIcons.ICON_ANNOUNCE_PATH,AppIcons.ICON_TAB_PROFILE_PATH,EditorActionIcons.RESTORE}) {
            for(float density:new float[]{1,1.5F,2}) for(int pose=0;pose<3;pose++) {
                assertHeaderIcon(icon,density,pose);samples++;
            }
        }
        assertEquals(45,samples,"all rest/hover/press density samples must execute");
    }
    private void assertHeaderIcon(io.github.humbleui.skija.Path icon,float density,int pose) {
        var header=new UiLayout.Rect(20,20,360,100);
        var back=new UiLayout.Rect(25,45,UiTokens.ACTION_BUTTON_SIZE,UiTokens.ACTION_BUTTON_SIZE);
        var action=new UiLayout.Rect(back.right()+UiTokens.EDGE_CONTROL_INSET,back.y(),back.w(),back.h());
        int width=(int)Math.ceil(420*density),height=(int)Math.ceil(160*density);
        try(var surface=Surface.makeRasterN32Premul(width,height);var pixels=new Bitmap()) {
            for(int frame=0;frame<20;frame++) {
                var canvas=surface.getCanvas();canvas.clear(0xFF101010);int saved=canvas.save();canvas.scale(density,density);
                if(pose==2)ShellHeader.armActionPress();
                ShellHeader.render(canvas,header,"",false,null,0,0xFFFFFFFF,null,
                        new ShellHeader.HeaderAction(action,pose==0?0:1,icon,0xFFFFFFFF));
                canvas.restoreToCount(saved);
            }
            pixels.allocN32Pixels(width,height);assertTrue(surface.readPixels(pixels,0,0));
            int left=10000,right=-1,top=10000,bottom=-1;
            for(int y=(int)Math.ceil(action.y()*density);y<action.bottom()*density;y++) {
                for(int x=(int)Math.ceil(action.x()*density);x<action.right()*density;x++) {
                    int pixel=pixels.getColor(x,y);
                    if(((pixel>>>16)&255)>220 && ((pixel>>>8)&255)>220 && (pixel&255)>220) {
                        left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
                    }
                }
            }
            assertTrue(right>=left,"icon must paint visible ink in every sample");
            float cx=(action.x()+action.w()/2)*density,cy=(action.y()+action.h()/2)*density;
            assertEquals(cx,(left+right+1)/2F,1F,"visible icon must center horizontally in hover");
            assertEquals(cy,(top+bottom+1)/2F,1F,"visible icon must center vertically in hover");
            float extent=Math.max(right-left+1,bottom-top+1);
            assertEquals((UiTokens.s(18)+UiTokens.iconStroke(UiTokens.s(18)))*density,extent,3*density,
                    "same header glyph token; extent allows existing press/hover scale and pixel quantization");
        }
    }
    @Test void cachedCurveBoundsFollowAPathThatChanges() {
        try(var path=io.github.humbleui.skija.Path.makeFromSVGString("M0 0 L10 10");
                var replacement=io.github.humbleui.skija.Path.makeFromSVGString("M0 0 L40 40")) {
            assertEquals(10,com.atom.chat.render.OutlineIconBounds.of(path).getRight(),.001F);
            path.swap(replacement);
            assertEquals(40,com.atom.chat.render.OutlineIconBounds.of(path).getRight(),.001F,
                    "a cached mutable path must never keep stale centering or size");
        }
    }
}
