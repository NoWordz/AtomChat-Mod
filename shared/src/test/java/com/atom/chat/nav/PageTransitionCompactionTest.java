package com.atom.chat.nav;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PageTransitionCompactionTest {
    private static java.lang.reflect.Field providerField,instanceField;
    private static Object oldProvider,oldInstance;
    @org.junit.jupiter.api.BeforeAll static void installPlatform()throws Exception {
        providerField=Platform.class.getDeclaredField("provider");providerField.setAccessible(true);oldProvider=providerField.get(null);
        instanceField=AtomChatConfig.class.getDeclaredField("instance");instanceField.setAccessible(true);oldInstance=instanceField.get(null);
        Path dir=Files.createTempDirectory("atomchat-compaction-platform");
        Platform.install(new Platform.Provider(){public Path configDir(){return dir;}public Path gameDir(){return dir;}public boolean isModLoaded(String id){return false;}});
    }
    @org.junit.jupiter.api.AfterAll static void restorePlatform()throws Exception {
        providerField.set(null,oldProvider);instanceField.set(null,oldInstance);
    }
    private static final class DenseHost implements PageTransitionComposite.Host {
        NavPage top=NavPage.of(AppPage.SETTINGS);
        public NavPage topNav(){return top;}
        public boolean arrivalStaggersSurfaces(){return false;}
        public void drawArrivingHeader(Canvas c,UiLayout l,int x,int y,float vx,float vy) { }
        public void drawArrivingBody(Canvas c,UiLayout l,int x,int y) {
            try(Paint paint=new Paint()){
                for(int i=0;i<13050;i++)c.drawRect(Rect.makeXYWH(50+i%200,150+(i/200)*2,1,1),
                        paint.setColor(i%2==0?0xFF2167AB:0xFFAA9944));
            }
        }
    }
    @Test void stableDensePageNeverRequestsOrAllocatesACompactionSurface()throws Exception {
        Path dir=Files.createTempDirectory("atomchat-compaction-test");
        Platform.install(new Platform.Provider(){public Path configDir(){return dir;}public Path gameDir(){return dir;}public boolean isModLoaded(String id){return false;}});
        var host=new DenseHost();
        try(var composite=new PageTransitionComposite(host);var surface=Surface.makeRasterN32Premul(400,700)) {
            UiLayout l=UiLayout.ofDetail(0,0,400,700);
            for(int i=0;i<60;i++) {
                composite.beginFrame(1000+i*16);composite.drawArrivingFrame(surface.getCanvas(),l,0,0,0,0,1000+i*16);
                assertFalse(composite.needsCompaction(),"a complex settled page cannot allocate another FBO every frame");
                composite.compact(null); // A settled call must never touch a GPU/context at all.
            }
        }
    }
    @Test void onlyInterruptedLayerGrowthRequestsCompaction()throws Exception {
        var cfg=AtomChatConfig.get();boolean enabled=cfg.animationEnabled;cfg.animationEnabled=true;
        var host=new DenseHost();
        try(var composite=new PageTransitionComposite(host);var surface=Surface.makeRasterN32Premul(400,700)) {
            UiLayout l=UiLayout.ofDetail(0,0,400,700);
            composite.beginFrame(1000);composite.drawArrivingFrame(surface.getCanvas(),l,0,0,0,0,1000);
            host.top=NavPage.settingsSection(SettingsSection.CHAT);
            composite.beginFrame(1010);composite.drawArrivingFrame(surface.getCanvas(),l,0,0,0,0,1010);
            assertFalse(composite.needsCompaction(),"normal arrival is not an interrupt overflow");
            composite.beginFrame(1060);composite.drawArrivingFrame(surface.getCanvas(),l,0,0,0,0,1060);
            host.top=NavPage.settingsSection(SettingsSection.PRIVACY);
            composite.beginFrame(1070);composite.drawArrivingFrame(surface.getCanvas(),l,0,0,0,0,1070);
            assertTrue(composite.needsCompaction(),"the interrupted real command stream must be compacted");
            composite.reset();assertFalse(composite.needsCompaction());
        } finally {cfg.animationEnabled=enabled;}
    }
}
