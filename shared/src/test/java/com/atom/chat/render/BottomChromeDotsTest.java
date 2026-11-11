package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.*;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BottomChromeDotsTest {
    @TempDir Path tmp;
    private Object oldConfig,oldProvider;
    private java.lang.reflect.Field instance,provider;
    private static final UiLayout.Rect CHAT=new UiLayout.Rect(30,420,480,110),NAV=new UiLayout.Rect(30,460,480,70);
    @BeforeEach void install()throws Exception {
        instance=AtomChatConfig.class.getDeclaredField("instance");instance.setAccessible(true);oldConfig=instance.get(null);
        provider=Platform.class.getDeclaredField("provider");provider.setAccessible(true);oldProvider=provider.get(null);
        Platform.install(new Platform.Provider(){public Path configDir(){return tmp.resolve("config");}public Path gameDir(){return tmp;}public boolean isModLoaded(String id){return false;}});
        instance.set(null,new AtomChatConfig());
    }
    @AfterEach void restore()throws Exception {instance.set(null,oldConfig);provider.set(null,oldProvider);}

    @Test void settledComposerAndNavigationKeepOriginalDotsUnderRealCards()throws Exception {
        for(var mode:new BottomChromeTransition.Mode[]{BottomChromeTransition.Mode.CHAT,BottomChromeTransition.Mode.NAV})
            try(var chrome=new BottomChromeTransition();var actual=Surface.makeRasterN32Premul(560,620);var reference=Surface.makeRasterN32Premul(560,620)) {
                chrome.beginFrame(mode,CHAT,NAV,1000,true);
                var bar=mode==BottomChromeTransition.Mode.CHAT?CHAT:NAV;
                // Old HEAD layering: chat shadow + dots -> body -> plate/details;
                // nav dots -> body -> nav shadow + plate/details.
                Canvas expected=reference.getCanvas();expected.clear(0xFF414851);
                if(mode==BottomChromeTransition.Mode.CHAT)shadow(expected,bar,UiTokens.shellChromeRadius());
                DotTexture.drawEdge(expected,bar,UiTokens.shellChromeRadius(),true,AtomChatConfig.get().textSecondaryColor);
                realBody(expected,bar);
                if(mode==BottomChromeTransition.Mode.NAV)shadow(expected,bar,UiTokens.shellChromeRadius());
                face(expected,bar,UiTokens.shellChromeRadius());
                actual.getCanvas().clear(0xFF414851);
                backdrop(chrome,actual.getCanvas());realBody(actual.getCanvas(),bar);chrome.draw(actual.getCanvas(),c->{});
                save(actual,mode+"-dots-restored.png");save(reference,mode+"-dots-old-reference.png");
                assertArrayEquals(pixels(reference),pixels(actual),mode+": old dots must keep their exact density/color/occlusion");
            }
    }

    @Test void animatedDotsFollowThePaintedRectAndHaveNoClippedAlphaLayerOrPictureChurn()throws Exception {
        AtomicInteger updates=new AtomicInteger();
        try(var chrome=new BottomChromeTransition();var actual=Surface.makeRasterN32Premul(560,620);var expected=Surface.makeRasterN32Premul(560,620)) {
            chrome.beginFrame(BottomChromeTransition.Mode.CHAT,CHAT,NAV,1000,true);
            chrome.beginFrame(BottomChromeTransition.Mode.NAV,CHAT,NAV,1010,true);
            long builds=DotTexture.cacheBuildCount();
            for(int i=0;i<100;i++) {
                long now=1011+i*3;
                chrome.beginFrame(BottomChromeTransition.Mode.NAV,CHAT,NAV,now,true);
                actual.getCanvas().clear(0xFF414851);backdrop(chrome,actual.getCanvas());chrome.draw(actual.getCanvas(),c->updates.incrementAndGet());
            }
            assertEquals(builds,DotTexture.cacheBuildCount(),"moving edge must not allocate a new cached Picture for every geometry");
            assertEquals(100,updates.get(),"only the target contents advance once per frame");
            chrome.beginFrame(BottomChromeTransition.Mode.HIDDEN,CHAT,NAV,1310,true);
            for(long now:new long[]{1310,1370,1450,1550}) {
                chrome.beginFrame(BottomChromeTransition.Mode.HIDDEN,CHAT,NAV,now,true);
                var shape=chrome.paintedShape();var bar=chrome.paintedRect();
                float radius=Math.min(UiTokens.shellChromeRadius(),Math.min(bar.w(),bar.h())/2);
                actual.getCanvas().clear(0xFF414851);backdrop(chrome,actual.getCanvas());chrome.draw(actual.getCanvas(),c->updates.incrementAndGet());
                expected.getCanvas().clear(0xFF414851);
                // Unbounded DOT layer catches clipping. Keep the shadow's device
                // bounds identical: fractional saveLayer bounds round shadow AA
                // by one channel level (isolated by BottomDotLayerProbeTest).
                try(var alpha=new Paint().setAlphaf(shape.alpha())) {
                    if(shape.alpha()<1)expected.getCanvas().saveLayer(null,alpha);else expected.getCanvas().save();
                    DotTexture.drawEdge(expected.getCanvas(),bar,radius,true,AtomChatConfig.get().textSecondaryColor);
                    expected.getCanvas().restore();
                    float pad=Math.max(UiTokens.floatSurfaceShadowPad(),UiTokens.MESSAGE_DOT_DEPTH);
                    if(shape.alpha()<1)expected.getCanvas().saveLayer(Rect.makeXYWH(shape.x()-pad,shape.y()-pad,
                            shape.w()+2*pad,shape.h()+2*pad),alpha);else expected.getCanvas().save();
                    shadow(expected.getCanvas(),bar,radius);face(expected.getCanvas(),bar,radius);
                    expected.getCanvas().restore();
                }
                assertArrayEquals(pixels(expected),pixels(actual),"moving dots/fade must not be clipped at "+now);
                save(actual,"dots-collapse-"+now+".png");
            }
            assertEquals(100,updates.get(),"retiring contents cannot be updated while hidden");
            chrome.beginFrame(BottomChromeTransition.Mode.NAV,CHAT,NAV,1550,true);
            chrome.beginFrame(BottomChromeTransition.Mode.NAV,CHAT,NAV,1660,true);
            actual.getCanvas().clear(0xFF414851);backdrop(chrome,actual.getCanvas());chrome.draw(actual.getCanvas(),c->{});
            save(actual,"dots-expand.png");
        }
    }
    private static void backdrop(BottomChromeTransition chrome,Canvas canvas)throws Exception {
        // The deployed regression has no backdrop pass: run its actual foreground
        // instead of stopping at a missing-method error, so RED compares pixels.
        try { BottomChromeTransition.class.getMethod("drawBackdrop",Canvas.class).invoke(chrome,canvas); }
        catch(NoSuchMethodException missingInDeployedVersion) { }
    }
    private static void realBody(Canvas canvas,UiLayout.Rect bar) {
        com.atom.lib.design.atomchat.UiCards.drawCard(canvas,65,bar.y()-UiTokens.MESSAGE_DOT_DEPTH,170,30,UiTokens.settingsRowRadius(),0);
        // A production chat bubble occupies part of the dot band too.
        com.atom.chat.render.ChatBubble.draw(canvas,270,bar.y()-30,180,22,AtomChatConfig.BubbleStyle.CORNER,false,0xFF67758A);
    }
    private static void shadow(Canvas canvas,UiLayout.Rect bar,float radius){SkiaDraw.drawChromeShadow(canvas,bar.x(),bar.y(),bar.w(),bar.h(),radius);}
    private static void face(Canvas canvas,UiLayout.Rect bar,float radius){SkiaDraw.drawRoundedRect(canvas,bar.x(),bar.y(),bar.w(),bar.h(),radius,UiTokens.cardFill());ChromeMaterial.drawDetails(canvas,bar,radius);}
    private static byte[] pixels(Surface surface){try(var bitmap=new Bitmap()){bitmap.allocN32Pixels(560,620);assertTrue(surface.readPixels(bitmap,0,0));return bitmap.readPixels();}}
    private static void save(Surface surface,String name)throws Exception{
        Path dir=Path.of(System.getProperty("atomchat.repoRoot","../.."),".work/v0.7.0-feedback/screenshots");Files.createDirectories(dir);
        try(var image=surface.makeImageSnapshot();var png=image.encodeToData()){Files.write(dir.resolve(name),png.getBytes());}
    }
}
