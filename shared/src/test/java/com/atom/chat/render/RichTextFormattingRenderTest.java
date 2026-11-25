package com.atom.chat.render;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.platform.Platform;
import com.atom.chat.text.RichText;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RichTextFormattingRenderTest {
    @BeforeAll static void setup(@TempDir Path directory) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return directory; }
            public Path gameDir() { return directory; }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    private static RichText text(String prefix) {
        var text = RichText.of(RichText.literal(prefix + "MMMM World").toText());
        assertEquals("MMMM World", text.getString());
        return text;
    }

    private static byte[] draw(String prefix) {
        try (var surface = Surface.makeRasterN32Premul(340, 100); var pixels = new Bitmap()) {
            var font = FontManager.font(UiTokens.FONT_BODY);
            var lines = RichTextRenderer.wrapFor(text(prefix), font, 320);
            RichTextRenderer.drawLines(surface.getCanvas(), font, lines, 12, 48,
                    SkiaFontRenderer.getHeight(font), 0xFFFFFFFF, null, false);
            pixels.allocN32Pixels(340, 100);
            assertTrue(surface.readPixels(pixels, 0, 0));
            return pixels.readPixels();
        }
    }

    @Test void boldIsActuallyPaintedAndMeasuredWithTheBoldFace() {
        assertFalse(Arrays.equals(draw(""), draw("§l")), "bold spans must change painted glyphs");
        var font = FontManager.font(UiTokens.FONT_BODY);
        var line = RichTextRenderer.wrapFor(text("§l"), font, 320).get(0);
        assertEquals(FontManager.boldFont(font.getSize()).measureTextWidth("MMMM World"),
                RichTextRenderer.width(font, line), .001F);
    }

    @Test void italicIsActuallyPainted() {
        assertFalse(Arrays.equals(draw(""), draw("§o")), "italic spans must change painted glyphs");
    }

    @Test void strikethroughIsActuallyPainted() {
        assertFalse(Arrays.equals(draw(""), draw("§m")), "strikethrough spans must paint a line");
    }

    @Test void obfuscationIsActuallyPainted() {
        assertFalse(Arrays.equals(draw(""), draw("§k")), "obfuscated spans must not remain plain text");
    }

    @Test void hudKeepsTheServersExplicitUnderlineOnTheName() {
        assertFalse(Arrays.equals(name(""), name("§n")), "removing clicks must preserve visible server name formatting");
    }

    @Test void aStableHudSelectionStillAnimatesObfuscatedNamesAndBodies() {
        var previous = ObfuscatedText.clock;
        var phase = new java.util.concurrent.atomic.AtomicLong(100);
        ObfuscatedText.clock = phase::get;
        var sender = RichText.of(RichText.literal("§k1234567890").toText());
        var body = RichText.of(RichText.literal("§k0987654321").toText());
        var message = new ChatMessage(body.toText(), false, false, null, null, null,
                "1234567890", "Player", body.getString(), sender, body);
        var config = new AtomChatConfig();
        config.graphicsQuality = com.atom.chat.config.GraphicsQuality.ULTRA;
        var hud = new ChatHudRenderer();
        try (var surface = Surface.makeRasterN32Premul(340, 160); var pixels = new Bitmap()) {
            pixels.allocN32Pixels(340, 160);
            byte[][] frames = new byte[2][];
            for (int i = 0; i < 2; i++) {
                phase.set(100L + i);
                surface.getCanvas().clear(0);
                hud.paint(surface.getCanvas(), List.of(message), new ChatHudRenderer.Viewport(4, 150, 330, 145, 1, 1),
                        null, "Local", "Player", config, 1, 255, 1, false, 0);
                assertTrue(surface.readPixels(pixels, 0, 0));
                frames[i] = pixels.readPixels();
            }
            assertFalse(Arrays.equals(frames[0], frames[1]), "a static HUD ink cache must not freeze obfuscated runs");
        } finally {
            hud.clear();
            ObfuscatedText.clock = previous;
        }
    }

    @Test void anEllipsisMustNotExceedTheAvailableHudTextWidth() {
        var font = FontManager.font(UiTokens.FONT_BODY);
        var lines = MessagePaint.hudLine(text(""), font, .5F);
        assertTrue(lines.isEmpty() || RichTextRenderer.width(font, lines.get(0)) <= .5F);
    }

    private static byte[] name(String prefix) {
        var sender = text(prefix);
        var body = RichText.literal("body");
        var message = new ChatMessage(body.toText(), false, false, null, null, null,
                "MMMM World", "Player", "body", sender, body);
        var config = new AtomChatConfig();
        config.graphicsQuality = com.atom.chat.config.GraphicsQuality.PERFORMANCE;
        var hud = new ChatHudRenderer();
        try (var surface = Surface.makeRasterN32Premul(340, 160); var pixels = new Bitmap()) {
            hud.paint(surface.getCanvas(), List.of(message), new ChatHudRenderer.Viewport(4, 150, 330, 145, 1, 1),
                    null, "Local", "Player", config, 1, 255, 1, false, 0);
            pixels.allocN32Pixels(340, 160);
            assertTrue(surface.readPixels(pixels, 0, 0));
            return pixels.readPixels();
        } finally { hud.clear(); }
    }
}
