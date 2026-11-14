package com.atom.chat.render;

import com.atom.chat.chat.PlayerRef;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.nav.AppPage;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.ScrollSyncService;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SoftShadowTest {
    @TempDir static Path tmp;

    @BeforeAll static void install() {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return tmp; }
            public Path gameDir() { return tmp; }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    // Catches both a too-small fade layer and drawing a solid shadow over the fill.
    @Test void actualJumpButtonHasAContinuousOuterTailAndAnUnshadedFace() throws Exception {
        var primitives = new DrawPrimitives(new DrawPrimitives.Host() {
            public float frameDt() { return 0; }
            public boolean emojiPanelOpen() { return false; }
            public boolean phrasePanelOpen() { return false; }
        });
        var service = new ScrollSyncService(new ScrollSyncService.Host() {
            public long frameDt() { return 0; }
            public AppPage topPage() { return AppPage.WORLD_CHAT; }
            public PlayerRef activePrivateTarget() { return null; }
            public DrawPrimitives primitives() { return primitives; }
        });
        var scroll = service.worldScroll();
        scroll.setContent(2000,600);
        scroll.scrollTo(0,false);
        var fade = ScrollSyncService.class.getDeclaredField("jumpLatestAnim");
        fade.setAccessible(true);
        fade.setFloat(service,1F);
        UiLayout layout = UiLayout.of(70,50,420,600);
        float size = UiTokens.s(38);
        int left = Math.round(layout.list.right()-size-UiTokens.s(12));
        int top = Math.round(layout.list.bottom()-size-UiTokens.s(12));
        int cy = Math.round(top+size/2);
        String mode = System.getProperty("atomchat.shadow.evidence", "current");
        try(var surface=Surface.makeRasterN32Premul(650,760);var pixels=new Bitmap()) {
            surface.getCanvas().clear(0);
            service.drawJumpLatest(surface.getCanvas(),layout,-1,-1,scroll,false);
            pixels.allocN32Pixels(650,760);
            assertTrue(surface.readPixels(pixels,0,0));
            save(surface,"jump-"+mode+".png");
            int aNear = pixels.getColor(left-4,cy)>>>24;
            int aFar = pixels.getColor(left-9,cy)>>>24;
            int face = pixels.getColor(left+Math.round(size/2),top+8);
            try(var zoom=Surface.makeRasterN32Premul(420,420)) {
                var c=zoom.getCanvas();c.clear(0xFFE0E4EB);
                c.translate(100,100);c.scale(4,4);c.translate(-left,-top);
                service.drawJumpLatest(c,layout,-1,-1,scroll,false);
                save(zoom,"jump-"+mode+"-zoom.png");
            }
            System.out.printf("jump: left=%d top=%d outer-alpha(-4,-9)=%d,%d face=%08x%n",left,top,aNear,aFar,face);
            assertTrue(aFar>0,"shadow must extend beyond the old 5px fade boundary; near="+aNear+" far="+aFar);
            assertTrue((face>>>24)<=236,"shadow must not paint a dark mask over the translucent button face: "+Integer.toHexString(face));
        }
    }

    @Test void panelHasASoftTailWithoutDarkeningItsTransparentInterior() throws Exception {
        try(var surface=Surface.makeRasterN32Premul(720,860);var pixels=new Bitmap()) {
            var canvas=surface.getCanvas();
            canvas.clear(0);
            int saves=canvas.getSaveCount();
            SoftShadow.drawPanel(canvas,150,120,420,610,28);
            assertEquals(saves,canvas.getSaveCount(),"panel shadow must restore its difference clip");
            pixels.allocN32Pixels(720,860);
            assertTrue(surface.readPixels(pixels,0,0));
            save(surface,"panel-transparent.png");
            assertEquals(0,pixels.getColor(350,400),"transparent wallpaper area must stay unshaded");
            int near=pixels.getColor(580,400)>>>24;
            int middle=pixels.getColor(594,400)>>>24;
            int far=pixels.getColor(Math.round(570 + SoftShadow.panelPad() - 1),400)>>>24;
            assertTrue(near>middle && middle>far,"shadow must decay gradually: "+near+","+middle+","+far);
            assertTrue(middle>0,"a visible soft tail must extend beyond a thin rim");
            assertTrue(far<=1,"tail must die out before the layer edge");
        }
    }

    @Test void panelPreviewOnBrightAndDarkWallpaper() throws Exception {
        for(boolean dark:new boolean[]{false,true}) {
            try(var surface=Surface.makeRasterN32Premul(1200,850);var paint=new io.github.humbleui.skija.Paint()) {
                var c=surface.getCanvas();c.clear(dark?0xFF353B49:0xFFD3DCE6);
                // Both columns use the production panel primitive; left omits only the new shadow.
                for(int col=0;col<2;col++) {
                    float x=100+col*600;
                    if(col==1) SoftShadow.drawPanel(c,x,100,400,650,28);
                    c.save();
                    c.clipRRect(io.github.humbleui.types.RRect.makeXYWH(x,100,400,650,28),true);
                    c.drawRect(io.github.humbleui.types.Rect.makeXYWH(x,100,400,650),paint.setColor(dark?0xFF273E62:0xFFB2C7DA));
                    c.drawCircle(x+90,440,190,paint.setColor(dark?0xFF445F87:0xFFE0CBD6));
                    c.drawRect(io.github.humbleui.types.Rect.makeXYWH(x,100,400,650),paint.setColor(0x8009090B));
                    c.restore();
                }
                save(surface,"panel-before-after-"+(dark?"dark":"bright")+".png");
            }
        }
    }

    @Test void ninePatchHasTheSameProfileAsTheFullSizeGaussian() throws Exception {
        int w=850,h=950;
        try(var actual=Surface.makeRasterN32Premul(w,h);var expected=Surface.makeRasterN32Premul(w,h);
            var a=new Bitmap();var b=new Bitmap();var paint=new io.github.humbleui.skija.Paint().setAntiAlias(true);
            var wide=io.github.humbleui.skija.MaskFilter.makeBlur(io.github.humbleui.skija.FilterBlurMode.NORMAL,22.5F);
            var contact=io.github.humbleui.skija.MaskFilter.makeBlur(io.github.humbleui.skija.FilterBlurMode.NORMAL,5F)) {
            actual.getCanvas().clear(0);expected.getCanvas().clear(0);
            SoftShadow.drawPanel(actual.getCanvas(),160,130,510,690,28);
            var c=expected.getCanvas();c.save();
            c.clipRRect(io.github.humbleui.types.RRect.makeXYWH(160,130,510,690,28),io.github.humbleui.skija.ClipMode.DIFFERENCE,true);
            c.drawRRect(io.github.humbleui.types.RRect.makeXYWH(160,145F,510,690,28),paint.setColor(0x78000000).setMaskFilter(wide));
            c.drawRRect(io.github.humbleui.types.RRect.makeXYWH(160,133.75F,510,690,28),paint.setColor(0x44000000).setMaskFilter(contact));
            c.restore();
            a.allocN32Pixels(w,h);b.allocN32Pixels(w,h);assertTrue(actual.readPixels(a,0,0));assertTrue(expected.readPixels(b,0,0));
            int peakDifference=0;long error=0;int count=0;
            for(int y=40;y<910;y++) for(int x=60;x<780;x++) {
                int av=a.getColor(x,y)>>>24,bv=b.getColor(x,y)>>>24;
                peakDifference=Math.max(peakDifference,Math.abs(av-bv));
                error+=Math.abs(av-bv);count++;
            }
            System.out.printf("panel nine-patch vs full Gaussian: peak-alpha-difference=%d mean=%.4f%n",peakDifference,(double)error/count);
            assertTrue(peakDifference<=4,"nine-patch must not create corner seams or a distinct rim");
        }
    }

    @Test void panelFadeLayerContainsTheWholeTailAndHalfScreenKeepsItsExposedEdge() throws Exception {
        try(var full=Surface.makeRasterN32Premul(720,860);var faded=Surface.makeRasterN32Premul(720,860);
            var fullPixels=new Bitmap();var fadePixels=new Bitmap();var layer=new io.github.humbleui.skija.Paint().setAlphaf(.5F)) {
            full.getCanvas().clear(0);faded.getCanvas().clear(0);
            SoftShadow.drawPanel(full.getCanvas(),0,-30,420,920,0);
            float pad=SoftShadow.panelPad();
            faded.getCanvas().saveLayer(io.github.humbleui.types.Rect.makeXYWH(-pad,-30-pad,420+2*pad,920+2*pad),layer);
            SoftShadow.drawPanel(faded.getCanvas(),0,-30,420,920,0);
            faded.getCanvas().restore();
            fullPixels.allocN32Pixels(720,860);fadePixels.allocN32Pixels(720,860);
            assertTrue(full.readPixels(fullPixels,0,0));assertTrue(faded.readPixels(fadePixels,0,0));
            int tail=0;
            for(int x=421;x<520;x++) {
                int a=fullPixels.getColor(x,430)>>>24,b=fadePixels.getColor(x,430)>>>24;
                assertTrue(Math.abs(a*.5F-b)<=1,"fade must not truncate at "+x);
                if(a>0) tail++;
            }
            assertTrue(tail>20,"docked panel must cast a broad shadow on its visible right edge");
            assertEquals(0,fullPixels.getColor(419,430));
            save(full,"half-screen-edge.png");save(faded,"half-screen-fade.png");
        }
    }

    @Test void resizingThePanelReusesTheGaussianPatchInsteadOfBlurringEachFrame() throws Exception {
        try(var surface=Surface.makeRasterN32Premul(1000,1000)) {
            SoftShadow.release();
            SoftShadow.drawPanel(surface.getCanvas(),100,100,400,600,28);
            var field=SoftShadow.class.getDeclaredField("PANEL_PATCHES");field.setAccessible(true);
            var patches=(java.util.Map<?,?>)field.get(null);
            Object first=patches.get(28F);
            assertNotNull(first);
            for(int i=0;i<100;i++) SoftShadow.drawPanel(surface.getCanvas(),100,100,400+i,600-i,28);
            assertSame(first,patches.get(28F),"width/height must not be part of the panel patch cache key");
        } finally { SoftShadow.release(); }
    }

    @Test void cacheEvictionAndTeardownCloseTheirOwnedRasterImages() throws Exception {
        var field=SoftShadow.class.getDeclaredField("PANEL_PATCHES");field.setAccessible(true);
        var patches=(java.util.Map<?,?>)field.get(null);
        try(var surface=Surface.makeRasterN32Premul(800,900)) {
            SoftShadow.release();
            for(int i=0;i<32;i++) SoftShadow.drawPanel(surface.getCanvas(),100,100,400,600,i);
            assertTrue(patches.size()<=12,"radius slider must have bounded native memory");
            SoftShadow.release();
            assertTrue(patches.isEmpty(),"renderer teardown must discard all native image owners");
            SoftShadow.drawPanel(surface.getCanvas(),100,100,400,600,28);
            assertEquals(1,patches.size(),"renderer must safely reopen after teardown");
        } finally { SoftShadow.release(); }
    }

    @Test void existingPipelineWarmupPreparesPhoneAndDockedShadowBeforeFirstOpen() throws Exception {
        var field=SoftShadow.class.getDeclaredField("PANEL_PATCHES");field.setAccessible(true);
        var patches=(java.util.Map<?,?>)field.get(null);
        var pictures=new java.util.ArrayList<io.github.humbleui.skija.Picture>();
        SoftShadow.release();
        try(var surface=Surface.makeRasterN32Premul(100,100)) {
            var draw=SkiaPipelineWarmup.class.getDeclaredMethod("draw",io.github.humbleui.skija.Canvas.class,java.util.List.class);
            draw.setAccessible(true);draw.invoke(null,surface.getCanvas(),pictures);
            assertTrue(patches.containsKey(UiTokens.panelRadius()),"first phone frame must reuse a prepared Gaussian patch");
            assertTrue(patches.containsKey(0F),"first docked frame must reuse its square-edge Gaussian patch");
        } finally { pictures.forEach(io.github.humbleui.skija.Picture::close);SoftShadow.release(); }
    }

    static void save(Surface surface,String name) throws Exception {
        Path path=Path.of(System.getProperty("atomchat.projectDir",".")).resolve(".work/v0.7.0/shadow-evidence/"+name);
        Files.createDirectories(path.getParent());
        try(var image=surface.makeImageSnapshot();var data=image.encodeToData()) { Files.write(path,data.getBytes()); }
    }
}
