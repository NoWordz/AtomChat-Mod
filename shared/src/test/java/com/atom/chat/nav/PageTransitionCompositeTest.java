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
    private PageTransitionComposite.Host drawHost;
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
        drawHost=new PageTransitionComposite.Host() {
            public NavPage topNav(){return top;}
            public int bodyVariant(){return top.page()==AppPage.SETTINGS_SECTION?detail.activeChipIndex(top.section()):0;}
            public boolean arrivalStaggersSurfaces(){return false;}
            public float bodyClipTop(UiLayout l){return top.isRoot()?l.header.bottom():l.chipBar.bottom();}
            public void drawArrivingBody(Canvas c,UiLayout l,int mx,int my) {
                draws.add(top);
                if(top.isRoot())home.render(c,l,-100,-100,0);
                else detail.renderBody(c,l,top.section(),-100,-100,0,AtomChatConfig.get().accentColor);
            }
            public void drawArrivingHeader(Canvas c,UiLayout l,int mx,int my,float x,float y) {
                headers.add(top);
                if(!top.isRoot()) detail.renderFixedChipBar(c,l,top.section(),-100,-100,AtomChatConfig.get().accentColor);
                float[] matrix=c.getLocalToDeviceAsMatrix33().getMat();
                assertEquals(1,matrix[0],.0001);assertEquals(0,matrix[2],.0001);assertEquals(0,matrix[5],.0001);
                com.atom.chat.ui.ShellHeader.render(c,l.header,top.page().name()+":"+top.param(),!top.isRoot(),
                        new UiLayout.Rect(l.header.x()+10,l.header.y()+10,32,32),0,AtomChatConfig.get().textPrimaryColor);
            }
        };
        composite=new PageTransitionComposite(drawHost);
    }
    @AfterEach void teardown(){composite.close();var c=AtomChatConfig.get();c.animationEnabled=oldAnimations;c.pageNavStyle=oldStyle;}
    private byte[] frame(Surface s,long now) {
        composite.beginFrame(now);composite.consumeArrivalEvent();
        Canvas c=s.getCanvas();c.clear(BG);int save=c.getSaveCount();
        composite.drawArrivingFrame(c,layout,-100,-100,-100,-100,now);
        assertEquals(save,c.getSaveCount(),"snapshot rendering is canvas neutral");
        try(Bitmap b=new Bitmap()){b.allocN32Pixels(W,H);assertTrue(s.readPixels(b,0,0));return b.readPixels();}
    }
    /** Uses production page bodies on an identical raster target for 0.9.1/0.9.2 A/B. */
    @Test void measureSettingsPageTransitions() {
        Assumptions.assumeTrue("1".equals(System.getenv("ATOMCHAT_NAV_BENCH")));
        long[] moving = new long[24 * 20 * 2];
        long[] steady = new long[24 * 2];
        try (Surface surface = Surface.makeRasterN32Premul(W, H)) {
            var canvas = surface.getCanvas();
            long now = 10_000;
            for (int repeat = -8; repeat < 24; repeat++) {
                top = NavPage.of(AppPage.SETTINGS);
                composite.reset();
                paintBenchmarkFrame(canvas, now);
                for (int pass = 0; pass < 2; pass++) {
                    top = pass == 0 ? NavPage.settingsSection(SettingsSection.APPEARANCE) : NavPage.of(AppPage.SETTINGS);
                    for (int frame = 0; frame < 20; frame++) {
                        now += 16;
                        long elapsed = paintBenchmarkFrame(canvas, now);
                        if (repeat >= 0) moving[(repeat * 2 + pass) * 20 + frame] = elapsed;
                    }
                    now += 32;
                    long elapsed = paintBenchmarkFrame(canvas, now);
                    if (repeat >= 0) steady[repeat * 2 + pass] = elapsed;
                }
            }
        }
        reportBenchmark("transition", moving);
        reportBenchmark("settled", steady);
    }

    private long paintBenchmarkFrame(Canvas canvas, long now) {
        draws.clear();
        headers.clear();
        canvas.clear(BG);
        long start = System.nanoTime();
        composite.beginFrame(now);
        if (composite.consumeArrivalEvent()) {
            if (top.isRoot()) home.markEntered(now);
            else detail.markEntered(now);
        }
        composite.drawArrivingFrame(canvas, layout, -100, -100, -100, -100, now);
        return System.nanoTime() - start;
    }

    private static void reportBenchmark(String name, long[] samples) {
        Arrays.sort(samples);
        long longFrames = Arrays.stream(samples).filter(ns -> ns > 16_666_667).count();
        System.out.printf(Locale.ROOT, "Page transition raster %s n=%d p50=%.3fms p95=%.3fms p99=%.3fms >16.67ms=%d%n",
                name, samples.length, samples[samples.length / 2] / 1e6,
                samples[(int) (samples.length * .95)] / 1e6, samples[(int) (samples.length * .99)] / 1e6, longFrames);
    }

    @Test void aSecondTapDoesNotJumpTheBlurredBackground() {
        var config = new AtomChatConfig();
        config.cardBlurStrength = 3F;
        config.panelOpacity = 0F;
        try (Surface target = Surface.makeRasterN32Premul(W, H);
             Surface wallpaper = Surface.makeRasterN32Premul(W, H);
             Paint sampleColor = new Paint().setColor(0xFFE6A243);
             PageTransitionComposite glass = new PageTransitionComposite(new PageTransitionComposite.Host() {
                 public NavPage topNav() { return top; }
                 public boolean arrivalStaggersSurfaces() { return false; }
                 public void drawArrivingBody(Canvas canvas, UiLayout layout, int mx, int my) {
                     float x = top.isRoot() ? 120 : 210;
                     com.atom.chat.render.CardBackdrop.draw(canvas, x, 300, 240, 260, 20);
                     try (Paint tint = new Paint().setColor(0x88402030)) {
                         canvas.drawRRect(io.github.humbleui.types.RRect.makeXYWH(x, 300, 240, 260, 20), tint);
                     }
                 }
                 public void drawArrivingHeader(Canvas c, UiLayout l, int mx, int my, float x, float y) {}
             })) {
            wallpaper.getCanvas().clear(0xFF243C77);
            wallpaper.getCanvas().drawRect(io.github.humbleui.types.Rect.makeLTRB(280, 0, W, H), sampleColor);
            try (Image image = wallpaper.makeImageSnapshot()) {
                top = NavPage.of(AppPage.SETTINGS);
                paintGlassFrame(glass, target, image, config, 1000);
                top = NavPage.settingsSection(SettingsSection.APPEARANCE);
                paintGlassFrame(glass, target, image, config, 1010);
                byte[] before = paintGlassFrame(glass, target, image, config, 1170);
                top = NavPage.settingsSection(SettingsSection.CHAT);
                assertArrayEquals(before, paintGlassFrame(glass, target, image, config, 1170),
                        "immediately interrupting must preserve both the visible body and the glass");
            }
        }
    }

    private byte[] paintGlassFrame(PageTransitionComposite glass, Surface target, Image wallpaper,
                                   AtomChatConfig config, long now) {
        Canvas canvas = target.getCanvas();
        canvas.drawImage(wallpaper, 0, 0);
        glass.beginFrame(now);
        try (var frame = com.atom.chat.render.CardBackdrop.begin(target, canvas, layout.rect(), null, wallpaper, config)) {
            glass.drawArrivingFrame(canvas, layout, -100, -100, -100, -100, now);
        }
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(W, H);
            assertTrue(target.readPixels(bitmap, 0, 0));
            return bitmap.readPixels();
        }
    }

    @Test void settingsTabLabelsStayStillWhileOnlyTheirContentMoves() {
        for(var style:AtomChatConfig.PageNavStyle.values()) {
            AtomChatConfig.get().pageNavStyle=style;
            top=NavPage.settingsSection(SettingsSection.APPEARANCE);composite.reset();
            detail.selectChip(SettingsSection.APPEARANCE,0);
            try(var surface=Surface.makeRasterN32Premul(W,H)) {
                byte[] before=frame(surface,1000);
                detail.selectChip(SettingsSection.APPEARANCE,1);
                frame(surface,1010);
                byte[] moving=frame(surface,1090);
                var label=layout.chipRect(2,detail.chips(SettingsSection.APPEARANCE).size());
                for(int y=(int)label.y()+4;y<(int)label.bottom()-4;y++) {
                    int from=(y*W+(int)label.x()+5)*4,to=(y*W+(int)label.right()-5)*4;
                    assertArrayEquals(Arrays.copyOfRange(before,from,to),Arrays.copyOfRange(moving,from,to),style+": fixed tab label moved");
                }
            }
        }
    }
    @Test void firstFrameIsSettledAndKeyChangeIsOneShot(){
        composite.beginFrame(1000);assertTrue(composite.overlaySettled(1000));assertFalse(composite.consumeArrivalEvent());
        top=NavPage.settingsSection(SettingsSection.CHAT);composite.beginFrame(1010);
        assertTrue(composite.consumeArrivalEvent());assertFalse(composite.consumeArrivalEvent());
    }
    @Test void aLocalSettingsTabGetsTheSameInterruptibleTransitionAsANavigation() {
        top=NavPage.settingsSection(SettingsSection.APPEARANCE);
        try(var surface=Surface.makeRasterN32Premul(W,H)) {
            byte[] old=frame(surface,1000);
            detail.selectChip(SettingsSection.APPEARANCE,1);
            assertBodyEquals(old,frame(surface,1010),"a local tab must start from its departing pixels");
            assertFalse(composite.overlaySettled(1010),"the unchanged navigation page must not hide its local tab transition");
            byte[] middle=frame(surface,1100);
            detail.selectChip(SettingsSection.APPEARANCE,2);
            assertBodyEquals(middle,frame(surface,1100),"a second tab can interrupt without a visual jump");
        }
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
        int from=((int)Math.ceil(top.isRoot()?layout.header.bottom():layout.chipBar.bottom())+1)*W*4;
        assertArrayEquals(Arrays.copyOfRange(before,from,before.length),Arrays.copyOfRange(after,from,after.length),reason);
    }
    private static void write(Surface s,Path p)throws Exception{try(Image i=s.makeImageSnapshot();Data d=i.encodeToData()){Files.write(p,d.getBytes());}}
    @Test void measureProductionPageRecordingCost() {
        Assumptions.assumeTrue("1".equals(System.getenv("ATOMCHAT_BENCH")));
        try(var surface=Surface.makeRasterN32Premul(W,H)) {
            for(boolean throughComposite:new boolean[]{false,true,false,true}) {
                long[] samples=new long[100];
                for(int i=0;i<125;i++) {
                    long now=System.currentTimeMillis()+10_000L+i*16;
                    var c=surface.getCanvas();c.clear(BG);
                    long start=System.nanoTime();
                    if(throughComposite) {
                        composite.beginFrame(now);
                        composite.drawArrivingFrame(c,layout,-100,-100,-100,-100,now);
                    } else {
                        com.atom.chat.ui.UiStagger.atFrame(now,()->drawHost.drawArrivingBody(c,layout,-100,-100));
                        drawHost.drawArrivingHeader(c,layout,-100,-100,-100,-100);
                    }
                    if(i>=25)samples[i-25]=System.nanoTime()-start;
                    draws.clear();headers.clear();
                }
                Arrays.sort(samples);
                System.out.printf(Locale.ROOT,"PAGE_RECORDING_CPU composite=%s p50=%.4fms p95=%.4fms p99=%.4fms%n",
                        throughComposite,samples[50]/1e6,samples[95]/1e6,samples[99]/1e6);
            }
        }
    }
}
