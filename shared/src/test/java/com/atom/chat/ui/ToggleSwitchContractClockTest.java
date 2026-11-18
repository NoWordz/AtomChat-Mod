package com.atom.chat.ui;

import com.atom.lib.design.atomchat.UiTokens;
import com.atom.lib.motion.FrameTime;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ToggleSwitchContractClockTest {
    private java.lang.reflect.Field config;private Object previous;
    @BeforeEach void isolatePreferences()throws Exception{
        config=com.atom.chat.config.AtomChatConfig.class.getDeclaredField("instance");config.setAccessible(true);previous=config.get(null);
        var value=new com.atom.chat.config.AtomChatConfig();value.animationEnabled=true;config.set(null,value);
    }
    @AfterEach void restorePreferences()throws Exception{config.set(null,previous);}
    @Test void existingSnapRegressionWithIsolatedPreferences(){new ToggleSwitchKnobMotionTest().snapAndSettledKnobBehaveDeterministically();}
    @Test void existingOriginalPixelRegressionWithIsolatedPreferences()throws Exception{new ToggleSwitchKnobMotionTest().offscreenFlipPlaysThroughMidFlightAndSettlesToTheSnappedFrame();}
    @Test void declaredAppearanceMatchesOriginalAndStaysIndependentOfAnotherViewsCornerPreference(){
        var toggle=new ToggleSwitch();var cfg=com.atom.chat.config.AtomChatConfig.get();cfg.cornerRadius=0;
        int accent=0xff4fa0f0;var appearance=new ToggleSwitch.Appearance(accent,true,com.atom.chat.theme.ThemeService.colorIsLight(UiTokens.cardCutout()),0);
        int width=(int)Math.ceil(UiTokens.SWITCH_W)+8,height=(int)Math.ceil(UiTokens.SWITCH_H)+8;
        try(var reference=io.github.humbleui.skija.Surface.makeRasterN32Premul(width,height);var actual=io.github.humbleui.skija.Surface.makeRasterN32Premul(width,height)){
            reference.getCanvas().clear(0);actual.getCanvas().clear(0);toggle.render(reference.getCanvas(),2,2,accent,true);
            cfg.cornerRadius=25;toggle.render(actual.getCanvas(),2,2,appearance);
            try(var left=reference.makeImageSnapshot();var right=actual.makeImageSnapshot();var expected=io.github.humbleui.skija.Bitmap.makeFromImage(left);var observed=io.github.humbleui.skija.Bitmap.makeFromImage(right)){
                for(int y=0;y<height;y++)for(int x=0;x<width;x++)assertEquals(expected.getColor(x,y),observed.getColor(x,y),"another view changed contracted pixels at "+x+","+y);
            }
        }
    }
    @Test void originalKnobRetargetAndStallUseTheSuppliedFrameRatherThanWallTime(){
        var toggle=new ToggleSwitch();var original=UiSpring.newToggleKnobSpring();
        long[] times={0,16,32,48,64,80,96,112,700,716,732};
        for(int i=0;i<times.length;i++){
            boolean on=i>0&&i<4||i>7;long nanos=times[i]*1_000_000;
            toggle.updateAt(new FrameTime(i,nanos,.016),on,true);
            assertEquals(original.tick(on?1:0,nanos,true),toggle.knobSpringForTest().value(),0,"same original algorithm at frame "+i);
        }
    }
    @Test void pressPulseExpiresOnTheSameFrameClockAndMotionPreferenceStaysLocal()throws Exception{
        var toggle=new ToggleSwitch();var reference=PressScale.bounce();
        var member=ToggleSwitch.class.getDeclaredField("scale");member.setAccessible(true);var actual=(PressScale)member.get(toggle);
        toggle.pulseAt(1000);
        for(int i=0;i<35;i++){
            long now=1000+i*16;toggle.updateAt(new FrameTime(i,now*1_000_000,.016),false,true);
            reference.update(false,now<1090,16,true,UiTokens.SWITCH_W);
            assertEquals(reference.scale(),actual.scale(),0,"pulse phase at "+now);
        }
        toggle.setInteraction(true,true);toggle.updateAt(new FrameTime(36,1560_000_000,.016),false,false);
        assertEquals(1,actual.scale(),0);
    }
}
