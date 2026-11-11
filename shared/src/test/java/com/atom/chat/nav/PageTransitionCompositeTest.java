package com.atom.chat.nav;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsHomePage;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises production settings draws, including real text/icons/controls, not placeholder cards. */
class PageTransitionCompositeTest {
    @TempDir Path tmp;
    private static final int W=560,H=960,BG=0xFF17151D;
    private final UiLayout layout=UiLayout.ofDetail(10,10,520,900);
    private final List<NavPage> draws=new ArrayList<>();
    private NavPage top=NavPage.of(AppPage.SETTINGS);
    private final SettingsHomePage home=new SettingsHomePage();
    private final SettingsSectionPage detail=new SettingsSectionPage();
    private PageTransitionComposite composite;
    private final List<NavPage> headers=new ArrayList<>();
    private boolean oldAnimations;
    private AtomChatConfig.PageNavStyle oldStyle;
    @BeforeEach void setup() {
        Platform.install(new Platform.Provider() {
            public Path configDir(){return tmp.resolve("config");}
            public Path gameDir(){return tmp.resolve("game");}
            public boolean isModLoaded(String id){return false;}
        });
        var cfg=AtomChatConfig.get();oldAnimations=cfg.animationEnabled;oldStyle=cfg.pageNavStyle;cfg.animationEnabled=true;
        composite=new PageTransitionComposite(new PageTransitionComposite.Host() {
            public NavPage topNav(){return top;}
            public boolean arrivalStaggersSurfaces(){return false;}
            public void drawArrivingBody(Canvas c,UiLayout l,int mx,int my) {
                draws.add(top);
                if(top.isRoot())home.render(c,l,-100,-100,0);
                else detail.render(c,l,top.section(),-100,-100,0,AtomChatConfig.get().accentColor);
            }
            public void drawArrivingHeader(Canvas c,UiLayout l,int mx,int my,float x,float y) {
                headers.add(top);
                float[] matrix=c.getLocalToDeviceAsMatrix33().getMat();
                assertEquals(1,matrix[0],.0001);assertEquals(0,matrix[2],.0001);assertEquals(0,matrix[5],.0001);
                com.atom.chat.ui.ShellHeader.render(c,l.header,top.page().name()+":"+top.param(),!top.isRoot(),
                        new UiLayout.Rect(l.header.x()+10,l.header.y()+10,32,32),0,AtomChatConfig.get().textPrimaryColor);
            }
        });
    }
    @AfterEach void teardown(){composite.close();var c=AtomChatConfig.get();c.animationEnabled=oldAnimations;c.pageNavStyle=oldStyle;}
    private byte[] frame(Surface s,long now) {
        composite.beginFrame(now);composite.consumeArrivalEvent();
        Canvas c=s.getCanvas();c.clear(BG);int save=c.getSaveCount();
        composite.drawArrivingFrame(c,layout,-100,-100,-100,-100,now);
        assertEquals(save,c.getSaveCount(),"snapshot rendering is canvas neutral");
        try(Bitmap b=new Bitmap()){b.allocN32Pixels(W,H);assertTrue(s.readPixels(b,0,0));return b.readPixels();}
    }
    @Test void firstFrameIsSettledAndKeyChangeIsOneShot(){
        composite.beginFrame(1000);assertTrue(composite.overlaySettled(1000));assertFalse(composite.consumeArrivalEvent());
        top=NavPage.settingsSection(SettingsSection.CHAT);composite.beginFrame(1010);
        assertTrue(composite.consumeArrivalEvent());assertFalse(composite.consumeArrivalEvent());
    }
    @Test void realProductionPagesRemainPixelContinuousAcrossTwoInterrupts() throws Exception {
        Path out=Path.of(System.getProperty("atomchat.repoRoot","../.."),".work/v0.7.0/animation-frames");Files.createDirectories(out);
        for(var style:AtomChatConfig.PageNavStyle.values()) {
            AtomChatConfig.get().pageNavStyle=style;top=NavPage.of(AppPage.SETTINGS);composite.reset();
            try(Surface surface=Surface.makeRasterN32Premul(W,H)) {
                frame(surface,1000);
                top=NavPage.settingsSection(SettingsSection.CHAT);frame(surface,1010);
                byte[] before=frame(surface,1070);write(surface,out.resolve(style+"-before-interrupt.png"));
                draws.clear();headers.clear();top=NavPage.settingsSection(SettingsSection.PRIVACY);
                byte[] after=frame(surface,1070);write(surface,out.resolve(style+"-interrupt.png"));
                assertBodyEquals(before,after,"retarget "+style);
                assertEquals(List.of(top),headers,"only target chrome is rendered immediately");
                assertEquals(List.of(top),draws,"departing business draw must never execute");
                before=frame(surface,1120);top=NavPage.of(AppPage.SETTINGS);composite.navigationDirection(true);
                after=frame(surface,1120);assertBodyEquals(before,after,"reverse");
                frame(surface,1160);write(surface,out.resolve(style+"-reverse.png"));
                frame(surface,1400);assertTrue(composite.overlaySettled(1400));
            }
        }
    }
    @Test void disablingMotionImmediatelyShowsTheTargetProductionPage(){
        try(Surface s=Surface.makeRasterN32Premul(W,H);Surface reference=Surface.makeRasterN32Premul(W,H)){
            frame(s,1000);top=NavPage.settingsSection(SettingsSection.CHAT);frame(s,1010);
            AtomChatConfig.get().animationEnabled=false;
            byte[] actual=frame(s,1020);
            reference.getCanvas().clear(BG);detail.render(reference.getCanvas(),layout,top.section(),-100,-100,0,AtomChatConfig.get().accentColor);
            try(Bitmap b=new Bitmap()){b.allocN32Pixels(W,H);assertTrue(reference.readPixels(b,0,0));// Header is an independent live chrome layer and may cast its own shadow.
                int from=(int)(layout.header.bottom()+com.atom.lib.design.atomchat.UiTokens.floatSurfaceShadowPad()+2)*W*4;
                assertArrayEquals(Arrays.copyOfRange(b.readPixels(),from,b.readPixels().length),Arrays.copyOfRange(actual,from,actual.length));}
            assertTrue(composite.overlaySettled(1020));
        }
    }
    @Test void pureModelsCoverDirectionInverseHitsAndTenThousandRetargets(){InterruptiblePageMotionProbe.main(new String[0]);com.atom.chat.ui.BottomChromeMotionProbe.main(new String[0]);}
    private void assertBodyEquals(byte[] before,byte[] after,String reason) {
        int from=((int)Math.ceil(layout.header.bottom())+1)*W*4;
        assertArrayEquals(Arrays.copyOfRange(before,from,before.length),Arrays.copyOfRange(after,from,after.length),reason);
    }
    private static void write(Surface s,Path p)throws Exception{try(Image i=s.makeImageSnapshot();Data d=i.encodeToData()){Files.write(p,d.getBytes());}}
}
