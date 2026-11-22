package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.RRect;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PanelBezelContourTest {
    @Test void bezelRetainsFractionalInnerEdgeAndCornerCoverage() throws Exception {
        var field = AtomChatConfig.class.getDeclaredField("instance");
        field.setAccessible(true);
        var previousConfig = field.get(null);
        float previousDensity = com.atom.chat.render.UiPixelSnap.density();
        var config = new AtomChatConfig();
        field.set(null, config);
        try (var surface = Surface.makeRasterN32Premul(204, 160); var bitmap = new Bitmap()) {
            config.halfScreenMode = false;
            config.panelOutlineColor = 0xFF496683;
            config.setPanelOutlineWidth(4F);
            com.atom.chat.render.UiPixelSnap.beginFrame(1.25F);
            var canvas = surface.getCanvas();
            canvas.clear(0);
            canvas.scale(1.25F, 1.25F);
            PanelBackground.drawBezel(canvas, new UiLayout.Rect(16, 16, 128, 96));
            bitmap.allocN32Pixels(204, 160);
            assertTrue(surface.readPixels(bitmap, 0, 0));
            for (int y = 20; y < 26; y++) assertEquals(255, bitmap.getColor(100, y) >>> 24, "top stroke row " + y);
            assertEquals(0, bitmap.getColor(100, 19) >>> 24, "outside the straight edge");
            int innerEdge = bitmap.getColor(100, 26) >>> 24;
            assertTrue(innerEdge > 0 && innerEdge < 255, "inner edge has fractional coverage");
            assertEquals(0, bitmap.getColor(100, 27) >>> 24, "inside the straight edge");
            int mixed = 0;
            for (int y = 20; y < 56; y++) for (int x = 20; x < 56; x++) {
                int alpha = bitmap.getColor(x, y) >>> 24;
                if (alpha > 0 && alpha < 255) mixed++;
            }
            assertTrue(mixed > 0, "the curved edge retains fractional coverage");
        } finally {
            com.atom.chat.render.UiPixelSnap.beginFrame(previousDensity);
            field.set(null, previousConfig);
        }
    }

    @Test void ivoryTintDoesNotLeakIntoTheBezelOuterAntialiasing() throws Exception {
        var field = AtomChatConfig.class.getDeclaredField("instance");
        field.setAccessible(true);
        var previousConfig = field.get(null);
        var config = new AtomChatConfig();
        field.set(null, config);
        try {
            config.halfScreenMode = false;
            config.panelOutlineColor = 0xFF806744;
            for (float width : new float[]{4, 6}) {
                config.setPanelOutlineWidth(width);
                var panel = new UiLayout.Rect(17.25F, 15.5F, 180.25F, 170.25F);
                try (var actual = Surface.makeRasterN32Premul(450, 410);
                     var reference = Surface.makeRasterN32Premul(450, 410);
                     var rim = new Bitmap(); var composite = new Bitmap()) {
                    var canvas = actual.getCanvas();
                    canvas.clear(0xFF4778B5);
                    canvas.scale(2, 2);
                    com.atom.chat.render.SoftShadow.drawPanel(canvas, panel.x(), panel.y(), panel.w(), panel.h(),
                            UiTokens.panelRadius(AtomChatAppearance.theme()));
                    paintIvory(canvas, panel);
                    PanelBackground.drawBezel(canvas, panel);
                    reference.getCanvas().scale(2, 2);
                    reference.getCanvas().clear(0);
                    PanelBackground.drawBezel(reference.getCanvas(), panel);
                    rim.allocN32Pixels(450, 410);
                    composite.allocN32Pixels(450, 410);
                    assertTrue(reference.readPixels(rim, 0, 0));
                    assertTrue(actual.readPixels(composite, 0, 0));
                    int brightest = 0;
                    for (int y = 30; y < 370; y++) {
                        for (int x = 34; x < 396; x++) {
                            int alpha = rim.getColor(x, y) >>> 24;
                            // Restrict to the outside side of the stroke's corner arc.
                            float r = UiTokens.panelRadius(AtomChatAppearance.theme()) * 2;
                            float dx = Math.max(panel.x() * 2 + r - (x + .5F), x + .5F - (panel.right() * 2 - r));
                            float dy = Math.max(panel.y() * 2 + r - (y + .5F), y + .5F - (panel.bottom() * 2 - r));
                            if (dx <= 0F || dy <= 0F || Math.hypot(dx, dy) < r - 1F || alpha == 0 || alpha == 255) continue;
                            brightest = Math.max(brightest, (composite.getColor(x, y) >> 16) & 255);
                        }
                    }
                    String stage = System.getenv("ATOMCHAT_VISUAL_FOLLOWUP");
                    if (stage != null) {
                        savePng(actual, Path.of(stage, "bezel-new-" + (int) width + ".png"));
                        try (Surface legacy = Surface.makeRasterN32Premul(450, 410);
                             Paint tint = new Paint().setColor(0xFFF4EFE5)) {
                            Canvas before = legacy.getCanvas();
                            float radius = UiTokens.panelRadius(AtomChatAppearance.theme());
                            before.clear(0xFF4778B5);
                            before.scale(2, 2);
                            com.atom.chat.render.SoftShadow.drawPanel(before,
                                    panel.x(), panel.y(), panel.w(), panel.h(), radius);
                            before.drawRRect(RRect.makeXYWH(panel.x(), panel.y(), panel.w(), panel.h(), radius), tint);
                            PanelBackground.drawBezel(before, panel);
                            savePng(legacy, Path.of(stage, "bezel-old-" + (int) width + ".png"));
                        }
                    }
                    System.out.println("bezel width=" + width + " corner red peak=" + brightest);
                    assertTrue(brightest > 65 && brightest <= 138,
                            "blue world and brown bezel cannot produce an ivory fringe: red=" + brightest);
                }
            }
        } finally {
            field.set(null, previousConfig);
        }
    }

    @Test void subpixelAndTranslucentOutlinesKeepTheAttachedPanelFace() throws Exception {
        var field = AtomChatConfig.class.getDeclaredField("instance");
        field.setAccessible(true);
        var previousConfig = field.get(null);
        var config = new AtomChatConfig();
        field.set(null, config);
        try {
            for (int alpha : new int[]{128, 255}) for (float width : new float[]{0F, .25F, .5F, 4F, 6F}) {
                config.panelOutlineColor = alpha << 24 | 0x806744;
                config.setPanelOutlineWidth(width);
                var panel = new UiLayout.Rect(10.25F, 10.5F, 90, 100);
                try (var surface = Surface.makeRasterN32Premul(240, 260); var bitmap = new Bitmap()) {
                    var canvas = surface.getCanvas();
                    bitmap.allocN32Pixels(240, 260);
                    for (float scale : new float[]{1, 1.5F, 2}) {
                        canvas.clear(0xFF4778B5);
                        canvas.save();
                        canvas.scale(scale, scale);
                        paintIvory(canvas, panel);
                        PanelBackground.drawBezel(canvas, panel);
                        canvas.restore();
                        assertTrue(surface.readPixels(bitmap, 0, 0));
                        int previous = 71;
                        for (int y = (int) (panel.y() * scale); y < (panel.y() + 10) * scale; y++) {
                            int red = bitmap.getColor((int) ((panel.x() + panel.w() / 2F) * scale), y) >> 16 & 255;
                            assertTrue(red + 2 >= previous, "rim alpha=" + alpha + ", width=" + width + ", scale=" + scale
                                    + ", y=" + y + ": " + previous + " -> " + red);
                            previous = red;
                        }
                        assertEquals(244, previous);
                    }
                    config.halfScreenMode = true;
                    canvas.clear(0xFF4778B5);
                    paintIvory(canvas, panel);
                    PanelBackground.drawBezel(canvas, panel);
                    assertTrue(surface.readPixels(bitmap, 0, 0));
                    assertEquals(0xFFF4EFE5, bitmap.getColor(11, 11));
                    config.halfScreenMode = false;
                }
            }
        } finally {
            field.set(null, previousConfig);
        }
    }

    private static void savePng(Surface surface, Path output) throws Exception {
        try (Image image = surface.makeImageSnapshot(); Data png = image.encodeToData()) {
            Files.write(output, png.getBytes());
        }
    }

    private static void paintIvory(Canvas canvas, UiLayout.Rect panel) {
        PanelBackground.drawTint(canvas, panel, 0xFFF4EFE5);
    }

    @Test void backgroundAndBezelNeverExposeAnInteriorGapAndWidthIsNotHalved() throws Exception {
        var provider=Platform.class.getDeclaredField("provider");provider.setAccessible(true);var oldProvider=provider.get(null);
        var field=AtomChatConfig.class.getDeclaredField("instance");field.setAccessible(true);var oldConfig=field.get(null);
        var root=Files.createTempDirectory("bezel-contour");
        try {
            Platform.install(new Platform.Provider(){public java.nio.file.Path configDir(){return root;}public java.nio.file.Path gameDir(){return root;}public boolean isModLoaded(String id){return false;}});
            field.set(null,new AtomChatConfig());
            var config=AtomChatConfig.get();config.halfScreenMode=false;config.panelOutlineColor=0xFFFF0000;
            for(float width:new float[]{0,1,2,3,4}) {
                config.setPanelOutlineWidth(width);
                try(var actual=Surface.makeRasterN32Premul(180,220);var reference=Surface.makeRasterN32Premul(180,220);
                    var fill=new Paint().setColor(0x8000FF00).setAntiAlias(true)) {
                    var rect=new UiLayout.Rect(20,20,140,180);
                    var shape=RRect.makeXYWH(rect.x(),rect.y(),rect.w(),rect.h(),UiTokens.panelRadius(AtomChatAppearance.theme()));
                    actual.getCanvas().clear(0);reference.getCanvas().clear(0);
                    PanelBackground.drawTint(actual.getCanvas(),rect,0x8000FF00);
                    reference.getCanvas().drawRRect(shape,fill);
                    PanelBackground.drawBezel(actual.getCanvas(),rect);
                    byte[] a=pixels(actual),r=pixels(reference);
                    for(int i=3;i<a.length;i+=4) if((r[i]&255)>=127) assertTrue((a[i]&255)>=126,"interior alpha gap at width "+width);
                    if(width==3) {
                        float physical=UiTokens.s(width);
                        int redPixels=0;
                        for(int y=20;y<35;y++) {int index=(y*180+90)*4; if((a[index+2]&255)>200)redPixels++;}
                        assertTrue(redPixels>=Math.floor(physical)&&redPixels<=Math.ceil(physical),"3 logical pixels must occupy scaled full stroke width, actual "+redPixels);
                        System.out.println("outline: 3 logical px = "+physical+" scaled px, opaque core = "+redPixels+" raster rows");
                    }
                }
            }
        } finally {provider.set(null,oldProvider);field.set(null,oldConfig);}
    }
    private static byte[] pixels(Surface surface) {
        try(var bitmap=new Bitmap()){bitmap.allocN32Pixels(180,220);assertTrue(surface.readPixels(bitmap,0,0));return bitmap.readPixels();}
    }
}
