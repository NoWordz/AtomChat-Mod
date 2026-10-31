package com.atom.chat.ui;


import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.render.DrawPrimitives;
import com.atom.chat.tools.ToolLayout;
import com.atom.chat.tools.ToolMenu;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ComposerPopupFamilyTest {
    @BeforeAll static void setup(@TempDir Path root) throws Exception { UiFeatureRenderTest.setup(root); }
    private interface Contains { boolean at(UiLayout layout, float x, float y); }
    private static float bottom(UiLayout layout, Contains contains) {
        float x = layout.panelX + UiTokens.LIST_PAD_X + UiTokens.EMOJI_PANEL_PAD;
        float inside = layout.inputBar.y() - UiTokens.EMOJI_CELL * 2, outside = layout.inputBar.y();
        assertTrue(contains.at(layout, x, inside)); assertFalse(contains.at(layout, x, outside));
        for (int i = 0; i < 24; i++) { float mid = (inside + outside) / 2; if (contains.at(layout, x, mid)) inside = mid; else outside = mid; }
        return inside;
    }
    @Test void toolsUseTheActualEmojiAndPhraseComposerGap() {
        var emoji = new EmojiPanel(new EmojiPanel.Host() {
            public void insert(String text) { }
            public void sendSticker(Path file) { }
            public void pickEmoteFile() { }
        });
        var phrase = new QuickPhrasePanel(text -> {}, () -> 0xFF3B82F6);
        emoji.toggle(); emoji.update(200);
        phrase.toggle();
        phrase.update(200);
        for (float extra : new float[]{0, 42}) {
            var layout = UiLayout.of(15, 15, 420, 780, extra);
            float reference = bottom(layout, emoji::overPanel);
            assertEquals(reference, bottom(layout, (l,x,y) -> phrase.overPanel(l,x,y)), .001F);
            assertEquals(reference, ToolLayout.menu(layout).bottom(), .001F);
            assertEquals(reference, SuggestionGeometry.of(layout, 12, 220).bounds().bottom(), .001F);
        }
    }
    @Test void imageMenuIconUsesTheComposerGlyphSizeAndStroke() {
        var layout = UiLayout.of(15, 15, 420, 780); var box = ToolLayout.menu(layout);
        var menu = new ToolMenu(); menu.toggle();
        var primitives = new DrawPrimitives(new DrawPrimitives.Host() {
            public float frameDt() { return 16; }
            public boolean emojiPanelOpen() { return false; }
            public boolean phrasePanelOpen() { return false; }
        });
        try (var expected = Surface.makeRasterN32Premul(460, 820); var actual = Surface.makeRasterN32Premul(460, 820)) {
            expected.getCanvas().clear(0); actual.getCanvas().clear(0);
            MenuPopup.drawSurface(expected.getCanvas(), box.x(), box.y(), box.w(), box.h());
            expected.getCanvas().save();
            primitives.drawIcon(expected.getCanvas(), DrawPrimitives.ICON_IMAGE_PATH, box.x(), box.y() + ToolLayout.MENU_ROW_H * 2,
                    ToolLayout.MENU_ROW_H, ToolLayout.MENU_ROW_H, 0xFFF4F5F7);
            expected.getCanvas().restore();
            menu.render(actual.getCanvas(), layout, -1, -1, 200, key -> key);
            try (var a = new Bitmap(); var b = new Bitmap()) {
                int w = (int) ToolLayout.MENU_ROW_H, h = (int) ToolLayout.MENU_ROW_H;
                a.allocN32Pixels(w,h); b.allocN32Pixels(w,h);
                assertTrue(expected.readPixels(a,(int)box.x(),(int)(box.y()+2*ToolLayout.MENU_ROW_H)));
                assertTrue(actual.readPixels(b,(int)box.x(),(int)(box.y()+2*ToolLayout.MENU_ROW_H)));
                assertArrayEquals(a.readPixels(), b.readPixels());
            }
        }
    }
}
