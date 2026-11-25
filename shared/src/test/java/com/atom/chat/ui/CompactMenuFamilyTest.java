package com.atom.chat.ui;

import com.atom.chat.render.FormattedLine;
import com.atom.chat.tools.ToolMenu;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Compare actual new controls to the accepted context-menu paint, including spring excursions. */
class CompactMenuFamilyTest {
    @BeforeAll static void setup(@TempDir Path root) throws Exception { UiFeatureRenderTest.setup(root); }

    @Test void toolsKeepTheContextMenuContourAndWashThroughHoverBounce() {
        for (float width : new float[]{160, 420}) for (int hovered : new int[]{0, 2}) {
            var layout = UiLayout.of(15, 15, width, 780);
            var menu = new ToolMenu(); menu.toggle();
            var box = menu.bounds(layout);
            try (var actual = Surface.makeRasterN32Premul(700, 820);
                 var expected = Surface.makeRasterN32Premul(700, 820)) {
                menu.render(actual.getCanvas(), layout, -1, -1, 200, key -> "");
                checkFrames(actual, expected, box, 5, hovered, (mx, my, dt) ->
                        menu.render(actual.getCanvas(), layout, mx, my, dt, key -> ""));
            }
        }
    }

    @Test void completionsKeepTheContextMenuContourAndWashThroughHoverBounce() {
        for (float width : new float[]{160, 420}) for (int hovered : new int[]{0, 2}) {
            var layout = UiLayout.of(15, 15, width, 780);
            var geometry = SuggestionGeometry.of(layout, 3, 140);
            var labels = List.of(FormattedLine.plain(""), FormattedLine.plain(""), FormattedLine.plain(""));
            var popup = new SuggestionPopup();
            try (var actual = Surface.makeRasterN32Premul(700, 820);
                 var expected = Surface.makeRasterN32Premul(700, 820)) {
                popup.draw(actual.getCanvas(), geometry, labels, 0, -1, -1, -1, 200);
                checkFrames(actual, expected, geometry.bounds(), 3, hovered, (mx, my, dt) ->
                        popup.draw(actual.getCanvas(), geometry, labels, 0, -1, mx, my, dt));
            }
        }
    }

    private interface Frame { void draw(float mx, float my, float dt); }

    @Test void longCandidateTextCannotEscapeTheMenuDuringHover() {
        var layout = UiLayout.of(15,15,160,780);
        var geometry = SuggestionGeometry.of(layout,1,1000);
        var full = List.of(FormattedLine.plain("namespace:very_long_completion_".repeat(8)));
        var blank = List.of(FormattedLine.plain(""));
        var textPopup = new SuggestionPopup(); var emptyPopup = new SuggestionPopup();
        var box = geometry.bounds();
        try (var actual = Surface.makeRasterN32Premul(700,820);
             var expected = Surface.makeRasterN32Premul(700,820)) {
            for (int i=0;i<32;i++) {
                actual.getCanvas().clear(0);expected.getCanvas().clear(0);
                textPopup.draw(actual.getCanvas(),geometry,full,0,0,box.x()+30,box.y()+15,i==0?200:16);
                emptyPopup.draw(expected.getCanvas(),geometry,blank,0,0,box.x()+30,box.y()+15,i==0?200:16);
                int x=(int)Math.ceil(box.right()), y=(int)box.y();
                assertArrayEquals(pixels(expected,x,y,300,(int)box.h()),pixels(actual,x,y,300,(int)box.h()),
                        "long native label must not paint outside the popup at frame "+i);
            }
        }
    }

    private static void checkFrames(Surface actual, Surface expected, UiLayout.Rect box, int rows,
                                    int hovered, Frame frame) {
        var springs = new PressScale[rows]; var washes = new float[rows];
        for (int i = 0; i < rows; i++) springs[i] = PressScale.bounce();
        float rowH = box.h() / rows;
        for (int tick = 0; tick < 32; tick++) {
            boolean inside = tick < 20;
            actual.getCanvas().clear(0); expected.getCanvas().clear(0);
            frame.draw(inside ? box.x() + box.w()/2 : -1,
                    inside ? box.y() + (hovered + .5F)*rowH : -1, 16);
            var canvas = expected.getCanvas();
            // These are the actual calls used by ContextMenuController, without a compensating clip.
            MenuPopup.drawSurface(canvas, box.x(), box.y(), box.w(), box.h());
            for (int row = 0; row < rows; row++) {
                boolean over = inside && row == hovered;
                washes[row] = UiMotion.approach(washes[row], over ? 1 : 0, 16, UiMotion.HOVER_MS);
                MenuPopup.updateRowBounce(springs[row], over, 16, box.w());
                MenuPopup.beginRowBounce(canvas, springs[row], box.x()+box.w()/2, box.y()+(row+.5F)*rowH);
                MenuPopup.drawRowWash(canvas, box.x(), box.y()+row*rowH, box.w(), rowH, washes[row]);
                MenuPopup.endRowBounce(canvas);
            }
            // Right contour includes surface corners and the full wash edge, excluding labels/icons/connector.
            int x = (int)(box.right()-32), y = (int)box.y(), h = (int)box.h();
            assertArrayEquals(pixels(expected,x,y,32,h), pixels(actual,x,y,32,h),
                    "accepted context-menu contour differs at hover frame " + tick);
        }
    }

    private static byte[] pixels(Surface surface, int x, int y, int w, int h) {
        try (var bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(w,h); assertTrue(surface.readPixels(bitmap,x,y));
            return bitmap.readPixels();
        }
    }
}
