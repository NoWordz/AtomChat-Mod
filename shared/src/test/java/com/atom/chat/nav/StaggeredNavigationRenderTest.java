package com.atom.chat.nav;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsHomePage;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.ui.*;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.*;
import java.nio.file.*;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real production tiles and rows, not coloured stand-ins for the page body. */
class StaggeredNavigationRenderTest {
    @TempDir Path tmp;
    private Object oldConfig, oldProvider;
    private java.lang.reflect.Field configField, providerField;
    private final UiLayout layout=UiLayout.ofDetail(10,10,520,900);
    @BeforeEach void install() throws Exception {
        configField=AtomChatConfig.class.getDeclaredField("instance");configField.setAccessible(true);oldConfig=configField.get(null);
        providerField=Platform.class.getDeclaredField("provider");providerField.setAccessible(true);oldProvider=providerField.get(null);
        Platform.install(new Platform.Provider(){public Path configDir(){return tmp.resolve("config");}public Path gameDir(){return tmp;}public boolean isModLoaded(String id){return false;}});
        configField.set(null,new AtomChatConfig());AtomChatConfig.get().animationEnabled=true;
    }
    @AfterEach void restore() throws Exception { configField.set(null,oldConfig);providerField.set(null,oldProvider); }

    private static final class Host implements PageTransitionComposite.Host {
        final SettingsHomePage home=new SettingsHomePage();
        final SettingsSectionPage detail=new SettingsSectionPage();
        NavPage top=NavPage.of(AppPage.WORLD_CHAT);
        public NavPage topNav(){return top;}
        public boolean arrivalStaggersSurfaces(){return true;}
        public void drawArrivingHeader(Canvas c,UiLayout l,int x,int y,float vx,float vy){}
        public void drawArrivingBody(Canvas c,UiLayout l,int x,int y) {
            if(top.page()==AppPage.SETTINGS)home.render(c,l,-100,-100,0);
            else if(top.page()==AppPage.SETTINGS_SECTION)detail.render(c,l,SettingsSection.APPEARANCE,-100,-100,0,AtomChatConfig.get().accentColor);
        }
    }
    private byte[] frame(Host host, PageTransitionComposite composite, Surface surface,long now) throws Exception {
        composite.beginFrame(now);
        if(composite.consumeArrivalEvent()) {
            if(host.top.page()==AppPage.SETTINGS)host.home.markEntered(composite.entrance().enteredAtMs());
            if(host.top.page()==AppPage.SETTINGS_SECTION)host.detail.markEntered(composite.entrance().enteredAtMs());
        }
        surface.getCanvas().clear(0);
        composite.drawArrivingFrame(surface.getCanvas(),layout,-100,-100,-100,-100,now);
        try(Bitmap bitmap=new Bitmap()){bitmap.allocN32Pixels(560,960);assertTrue(surface.readPixels(bitmap,0,0));return bitmap.readPixels();}
    }
    @Test void realTilesStaggerAfterEnvelopeLandsAndReverseWithoutAnyPixelJump() throws Exception {
        for(var style:AtomChatConfig.PageNavStyle.values()) {
            AtomChatConfig.get().pageNavStyle=style;
            Host host=new Host();
            try(var surface=Surface.makeRasterN32Premul(560,960);var composite=new PageTransitionComposite(host)) {
                byte[] blank=frame(host,composite,surface,1000);
                host.top=NavPage.of(AppPage.SETTINGS);
                assertArrayEquals(blank,frame(host,composite,surface,1100));
                frame(host,composite,surface,1200); save(surface,style+"-tiles-100ms.png");
                // At 240ms the envelope is done; late tiles still owe their own travel.
                byte[] late=frame(host,composite,surface,1340);save(surface,style+"-tiles-240ms.png");
                byte[] rest=frame(host,composite,surface,1800);save(surface,style+"-tiles-rest.png");
                assertFalse(Arrays.equals(late,rest),"restoring only the page envelope loses the stagger");
                host.top=NavPage.settingsSection(SettingsSection.APPEARANCE);
                assertArrayEquals(rest,frame(host,composite,surface,1810));
                byte[] mid=frame(host,composite,surface,1920);save(surface,style+"-rows-110ms.png");
                host.top=NavPage.of(AppPage.SETTINGS);
                assertArrayEquals(mid,frame(host,composite,surface,1920),"frozen rows must survive interruption exactly");
                frame(host,composite,surface,2020);
                // Invert page then item using the ACTUAL painted tile bounds.
                UiLayout.Rect tile=SettingsHomePage.tileRect(layout,0,0);
                UiStagger.Entrance entrance=new UiStagger.Entrance();entrance.markEntered(1920);
                var axis=PageNavAxis.current();
                float cx=tile.x()+tile.w()/2,cy=tile.y()+tile.h()/2;
                float px=cx+UiStagger.itemStyleSlidePx(entrance,0,2020,axis);
                float py=cy+UiStagger.itemRise(entrance,0,2020,axis);
                float pageScale=composite.overlayZoomScale(2020),pageCx=layout.rect().x()+layout.rect().w()/2,pageCy=layout.rect().y()+layout.rect().h()/2;
                px=pageCx+(px-pageCx)*pageScale+composite.overlaySlideDx(2020);
                py=pageCy+(py-pageCy)*pageScale;
                var point=host.home.staggerHits().inverse(composite.bodyX(px),composite.bodyY(py));
                assertEquals(cx,point.x(),.0001F);assertEquals(cy,point.y(),.0001F);
                assertEquals(SettingsSection.values()[0],host.home.hit(point.x(),point.y(),layout,0));
            }
        }
    }
    private static void save(Surface surface,String name)throws Exception {
        Path dir=Path.of(System.getProperty("atomchat.repoRoot","../.."),".work/v0.7.0-feedback/screenshots");Files.createDirectories(dir);
        try(var image=surface.makeImageSnapshot();var png=image.encodeToData()){Files.write(dir.resolve(name),png.getBytes());}
    }
}
