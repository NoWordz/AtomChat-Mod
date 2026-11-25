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

    // Covers the actual FAB and its shared popup material, including the lit upper rim.
    @Test void actualJumpButtonHasAContinuousOuterTailAndThePopupMaterial() throws Exception {
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
        int left = Math.round(layout.jumpLatest().x());
        int top = Math.round(layout.jumpLatest().y());
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
            int upper=pixels.getColor(left+Math.round(size/2),top+3)&255;
            int lower=pixels.getColor(left+Math.round(size/2),top+Math.round(size)-4)&255;
            assertTrue(upper>lower+4,"the shared material must retain a lit upper rim");
            try (var reference=Surface.makeRasterN32Premul(650,760);var expected=new Bitmap()) {
                reference.getCanvas().clear(0);
                com.atom.chat.ui.MenuPopup.drawMaterial(reference.getCanvas(), left, top, size, size,
                        UiTokens.radius(size/(2F*UiTokens.SCALE)), com.atom.chat.ui.MenuPopup.SURFACE_COLOR);
                expected.allocN32Pixels(650,760);
                assertTrue(reference.readPixels(expected,0,0));
                assertEquals(expected.getColor(left+Math.round(size/2),top+8),face,
                        "FAB must retain the popup's interior shading and upper rim");
            }
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
            int near=pixels.getColor(576,400)>>>24;
            int middle=pixels.getColor(584,400)>>>24;
            int far=pixels.getColor(598,400)>>>24;
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

    @Test void settledShadowRetainsDeviceCoverageAndLeavesTheInteriorClear() {
        try (var surface = Surface.makeRasterN32Premul(500, 645);
             var before = new Bitmap(); var after = new Bitmap()) {
            var canvas = surface.getCanvas();
            canvas.translate(61F, 64F);
            canvas.scale(1.25F, 1.25F);
            before.allocN32Pixels(500, 645);
            after.allocN32Pixels(500, 645);
            for (float x : new float[]{0F, .4F, 8F}) {
                canvas.clear(0);
                SoftShadow.drawPanel(canvas, x, 0, 278.4F, 398.4F, 25F);
                assertTrue(surface.readPixels(before, 0, 0));
                for (int frame = 0; frame < 3; frame++) {
                    canvas.clear(0);
                    SoftShadow.drawPanel(canvas, x, 0, 278.4F, 398.4F, 25F, true);
                }
                assertTrue(surface.readPixels(after, 0, 0));
                int peak = 0;
                for (int y = 0; y < 645; y++) {
                    for (int px = 0; px < 500; px++) {
                        peak = Math.max(peak, Math.abs((before.getColor(px, y) >>> 24) - (after.getColor(px, y) >>> 24)));
                    }
                }
                assertTrue(peak <= 4, "settled shadow must preserve the contour, peak alpha difference " + peak);
                assertEquals(0, after.getColor(230, 300));
                assertTrue((after.getColor(430, 300) >>> 24) > 0, "the soft outside tail must remain");
            }
        } finally {
            SoftShadow.release();
        }
    }

    @Test void settledShadowReusesOneDeviceMaskAndSkipsHigherDensities() throws Exception {
        var field = SoftShadow.class.getDeclaredField("settledShadow");
        field.setAccessible(true);
        SoftShadow.release();
        try (var surface = Surface.makeRasterN32Premul(700, 850)) {
            var canvas = surface.getCanvas();
            canvas.translate(80F, 80F);
            canvas.scale(2.5F, 2.5F);
            for (int frame = 0; frame < 3; frame++) SoftShadow.drawPanel(canvas, 0, 0, 200, 250, 25, true);
            assertNull(field.get(null), "high-density clipping must continue to use vectors");

            canvas.resetMatrix();
            canvas.translate(80F, 80F);
            canvas.scale(2F, 2F);
            SoftShadow.drawPanel(canvas, 0, 0, 200, 250, 25, true);
            assertNull(field.get(null), "a new layout must settle before baking");
            SoftShadow.drawPanel(canvas, 0, 0, 200, 250, 25, true);
            Object mask = field.get(null);
            assertNotNull(mask, "aligned output at 2x uses the single native-resolution coverage mask");
            SoftShadow.drawPanel(canvas, 0, 0, 200, 250, 25, true);
            assertSame(mask, field.get(null));

            var imageAccessor = mask.getClass().getDeclaredMethod("image");
            imageAccessor.setAccessible(true);
            var image = (io.github.humbleui.skija.Image) imageAccessor.invoke(mask);
            for (int frame = 0; frame < 2; frame++) SoftShadow.drawPanel(canvas, 4, 0, 200, 250, 25, true);
            assertNotSame(mask, field.get(null), "a moved panel must use the matching coverage");
            assertTrue(image.isClosed(), "replacing the mask must close the old native image");
        } finally {
            SoftShadow.release();
        }
        assertNull(field.get(null));
    }

    @Test void ninePatchHasTheSameProfileAsTheFullSizeGaussian() throws Exception {
        int w=850,h=950;
        try(var actual=Surface.makeRasterN32Premul(w,h);var expected=Surface.makeRasterN32Premul(w,h);
            var a=new Bitmap();var b=new Bitmap();var paint=new io.github.humbleui.skija.Paint().setAntiAlias(true);
            var wide=io.github.humbleui.skija.MaskFilter.makeBlur(io.github.humbleui.skija.FilterBlurMode.NORMAL,12.5F);
            var contact=io.github.humbleui.skija.MaskFilter.makeBlur(io.github.humbleui.skija.FilterBlurMode.NORMAL,5.5F)) {
            actual.getCanvas().clear(0);expected.getCanvas().clear(0);
            SoftShadow.drawPanel(actual.getCanvas(),160,130,510,690,28);
            var c=expected.getCanvas();c.save();
            c.clipRRect(io.github.humbleui.types.RRect.makeXYWH(160,130,510,690,28),io.github.humbleui.skija.ClipMode.DIFFERENCE,true);
            c.drawRRect(io.github.humbleui.types.RRect.makeXYWH(160,135F,510,690,28),paint.setColor(0x24000000).setMaskFilter(wide));
            c.drawRRect(io.github.humbleui.types.RRect.makeXYWH(160,133.25F,510,690,28),paint.setColor(0x40000000).setMaskFilter(contact));
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

    @Test void shortFloatingShadowMatchesItsOwnOutlineAlongTheStraightEdges() {
        try (var actual = Surface.makeRasterN32Premul(340, 150);
             var expected = Surface.makeRasterN32Premul(340, 150);
             var actualPixels = new Bitmap(); var expectedPixels = new Bitmap();
             var paint = new io.github.humbleui.skija.Paint().setAntiAlias(true)) {
            var shape = io.github.humbleui.types.RRect.makeXYWH(60, 45, 220, 45, 14);
            actual.getCanvas().clear(0);
            expected.getCanvas().clear(0);
            SoftShadow.drawFloating(actual.getCanvas(), 60, 45, 220, 45, 14);
            var reference = expected.getCanvas();
            reference.clipRRect(shape, io.github.humbleui.skija.ClipMode.DIFFERENCE, true);
            HudShadow.paintLayer(reference, shape, paint, ShadowTokens.FLOAT.ambient());
            HudShadow.paintLayer(reference, shape, paint, ShadowTokens.FLOAT.contact());
            actualPixels.allocN32Pixels(340, 150);
            expectedPixels.allocN32Pixels(340, 150);
            assertTrue(actual.readPixels(actualPixels, 0, 0));
            assertTrue(expected.readPixels(expectedPixels, 0, 0));
            for (int d = 2; d <= 25; d++) {
                assertEquals(expectedPixels.getColor(170, 45 - d) >>> 24,
                        actualPixels.getColor(170, 45 - d) >>> 24, 4, "upper tail at " + d);
                assertEquals(expectedPixels.getColor(170, 89 + d) >>> 24,
                        actualPixels.getColor(170, 89 + d) >>> 24, 4, "lower tail at " + d);
            }
            assertEquals(0, actualPixels.getColor(170, 66) >>> 24, "the transparent face remains clear");
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
            assertTrue(tail>10 && tail<35,"docked edge retains a compact soft shadow: "+tail);
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
            var key=new SoftShadow.PanelPatchKey(28F,3);
            Object first=patches.get(key);
            assertNotNull(first);
            for(int i=0;i<100;i++) SoftShadow.drawPanel(surface.getCanvas(),100,100,400+i,600-i,28);
            assertSame(first,patches.get(key),"width/height must not be part of the panel patch cache key");
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
            assertTrue(patches.containsKey(new SoftShadow.PanelPatchKey(UiTokens.panelRadius(com.atom.chat.ui.AtomChatAppearance.theme()),3)),
                    "first phone frame must reuse a prepared Gaussian patch");
            assertTrue(patches.containsKey(new SoftShadow.PanelPatchKey(0F,3)),"first docked frame must reuse its square-edge Gaussian patch");
        } finally { pictures.forEach(io.github.humbleui.skija.Picture::close);SoftShadow.release(); }
    }

    static void save(Surface surface,String name) throws Exception {
        Path path=Path.of(System.getProperty("atomchat.projectDir",".")).resolve(".work/v0.7.0/shadow-evidence/"+name);
        Files.createDirectories(path.getParent());
        try(var image=surface.makeImageSnapshot();var data=image.encodeToData()) { Files.write(path,data.getBytes()); }
    }
}
