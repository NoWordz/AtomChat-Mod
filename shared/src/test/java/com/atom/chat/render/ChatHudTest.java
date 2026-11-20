package com.atom.chat.render;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.ChatHudGifFixture;
import com.atom.chat.platform.Platform;
import com.atom.chat.text.RichText;
import com.atom.chat.ui.ChatShortcutHint;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ChatHudTest {
    @BeforeAll static void setup(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @Test void identicalVanillaRowsKeepTheirOwnCapturedPresentation() {
        var history = new ChatHudHistory<String, Object, Object>();
        String first = new String("a vanilla row");
        String second = new String("a vanilla row");
        Object content = new Object();
        Object own = new Object();
        Object other = new Object();

        history.stage(content, own);
        history.bind(first, content, List.of(first));
        history.stage(content, other);
        history.bind(second, content, List.of(second, first));

        assertSame(own, history.presentation(first, key -> fail("first row lost capture")));
        assertSame(other, history.presentation(second, key -> fail("second row lost capture")));
        assertNull(history.presentation(new String("a vanilla row"), key -> null));
    }

    @Test void rewrittenContentBindsOnlyToTheCommittedEntryAndKeepsSuppression() {
        var history = new ChatHudHistory<Object, String, Object>();
        String rewritten = new String("rewritten");
        Object unrelated = new Object();
        Object filtered = new Object();
        Object nativeFallback = new Object();

        history.stage(rewritten, null);
        history.bind(unrelated, new String("rewritten"), List.of(unrelated));
        history.bind(filtered, rewritten, List.of(filtered, unrelated));

        assertSame(nativeFallback, history.presentation(unrelated, key -> nativeFallback));
        assertNull(history.presentation(filtered, key -> fail("filtered message reappeared")));
    }

    @Test void entriesEvictedOrClearedByVanillaCannotKeepOldPresentation() {
        var history = new ChatHudHistory<Object, Object, Object>();
        Object oldRow = new Object();
        Object newRow = new Object();
        Object content = new Object();
        Object old = new Object();
        Object recent = new Object();
        Object marker = new Object();

        history.stage(content, old);
        history.bind(oldRow, content, List.of(oldRow));
        history.stage(content, recent);
        history.bind(newRow, content, List.of(newRow));
        assertSame(marker, history.presentation(oldRow, key -> marker));

        history.clear();
        assertSame(marker, history.presentation(newRow, key -> marker));
    }

    @Test void logicalMessageLimitSkipsSuppressedRowsAndCanHideAllRows() {
        var history = new ChatHudHistory<String, String, String>();
        String filtered = "filtered";
        var entries = List.of(filtered, "newest\nsecond line", "older", "oldest");
        history.stage(filtered, null);
        history.bind(filtered, filtered, entries);
        var selected = new ArrayList<String>();

        history.copyRecent(entries, 2, entry -> entry, selected);
        assertEquals(List.of("newest\nsecond line", "older"), selected);
        history.copyRecent(entries, 0, entry -> fail("hidden HUD does not resolve rows"), selected);
        assertTrue(selected.isEmpty());
    }

    @Test void avatarUsesShortcutSizeAndScalesWithVanillaGuiAndChat() {
        var pose = new Matrix4f();
        var base = ChatHudRenderer.Viewport.of(pose, 480, 300, 3, 1, 320).fit(5);
        var guiScaled = ChatHudRenderer.Viewport.of(pose, 480, 300, 4, 1, 320).fit(5);
        var chatScaled = ChatHudRenderer.Viewport.of(pose, 480, 300, 4, 0.5, 320).fit(5);

        assertEquals(39F, base.density() * UiTokens.AVATAR_SIZE, 0.001F);
        assertEquals(52F, guiScaled.density() * UiTokens.AVATAR_SIZE, 0.001F);
        assertEquals(26F, chatScaled.density() * UiTokens.AVATAR_SIZE, 0.001F);
        assertEquals(25F, 300F - chatScaled.bottom() * chatScaled.density() / 4F, 0.001F);
    }

    @Test void newestThumbnailLeavesSixGuiPixelsAboveTheShortcut() {
        var config = new AtomChatConfig();
        var imageMessage = player(false, "[[CICode,url=http://test/thumb.png,name=thumb]]");
        int guiWidth = 240, guiHeight = 160;
        try (var source = Surface.makeRasterN32Premul(32, 32)) {
            source.getCanvas().clear(0xFFFF0000);
            try (var image = source.makeImageSnapshot()) {
                for (int scale : new int[]{2, 3, 5}) {
                    int width = guiWidth * scale, height = guiHeight * scale;
                    var viewport = ChatHudRenderer.Viewport.of(new Matrix4f(), guiWidth, guiHeight, scale, 1, 200).fit(1);
                    var hud = new ChatHudRenderer((url, visible) -> image);
                    try (var surface = Surface.makeRasterN32Premul(width, height); var pixels = new Bitmap()) {
                        var canvas = surface.getCanvas();
                        canvas.save();
                        canvas.scale(viewport.density(), viewport.density());
                        hud.paint(canvas, List.of(imageMessage), viewport, null, "Local", "Player",
                                config, 1, 255, 1F, false, 0);
                        canvas.restore();
                        ChatShortcutHint.renderGuiScaled(canvas, width, height, 1, scale, "Y", 1);
                        pixels.allocN32Pixels(width, height);
                        assertTrue(surface.readPixels(pixels, 0, 0));
                        int imageBottom = height - 1;
                        while (imageBottom >= 0 && pixels.getColor(36 * scale, imageBottom) != 0xFFFF0000) imageBottom--;
                        assertTrue(imageBottom >= 0);
                        int iconTop = imageBottom + 1;
                        while (iconTop < height && pixels.getColor(12 * scale, iconTop) != 0xFFFFFFFF) iconTop++;
                        assertTrue(iconTop < height);
                        assertEquals(6F * scale, iconTop - imageBottom - 1, 1F, "GUI scale " + scale);
                    }
                }
            }
        }
    }

    @Test void expiryUsesEachVanillaReceiptTickAndNeverRenewsAHiddenRow() {
        var history = new ChatHudHistory<Integer, Object, String>();
        var entries = List.of(95, 40, 10);
        var selected = new ArrayList<String>();
        history.copyRecent(entries, 10, Object::toString, Integer::intValue, 239, selected);
        assertEquals(List.of("95", "40"), selected);
        history.copyRecent(entries, 10, Object::toString, Integer::intValue, 240, selected);
        assertEquals(List.of("95"), selected);
        history.copyRecent(entries, 10, Object::toString, Integer::intValue, 295, selected);
        assertTrue(selected.isEmpty());
        history.copyRecent(entries, 10, Object::toString, selected);
        assertEquals(List.of("95", "40", "10"), selected, "disabling timed exit restores resident mode");
    }

    @Test void hardBreakEllipsizesSystemMessageEvenWhenItsFirstLineFits() {
        var first = RichText.literal("first").mapStyles(style -> style.withColor(0xFF00AA).withBold(true));
        var text = RichText.concat(first, RichText.literal("\nsecond"));
        var font = com.atom.chat.font.FontManager.font(com.atom.lib.design.atomchat.UiTokens.FONT_QUOTE);
        var lines = ChatHudRenderer.oneLine(text, font, 300);
        assertEquals(1, lines.size());
        assertEquals("first…", lines.get(0).getPlainText());
        assertEquals(first.runs().get(0).style(), lines.get(0).runs().get(0).style());
    }

    @Test void playerBodyHardBreakCannotPaintASecondRichLine() {
        var first = RichText.literal("First").mapStyles(style -> style.withColor(0xFF3311));
        var second = RichText.literal("\nSecond").mapStyles(style -> style.withColor(0x1133FF));
        var body = RichText.concat(first, second);
        var row = new ChatMessage(body.toText(), false, false, null, null, null,
                "Sender", "", body.getString(), RichText.literal("Sender"), body);
        var hud = new ChatHudRenderer();
        var viewport = new ChatHudRenderer.Viewport(8, 180, 310, 164, 1, 1);
        try (var surface = Surface.makeRasterN32Premul(340, 190); var pixels = new Bitmap()) {
            pixels.allocN32Pixels(340, 190);
            hud.paint(surface.getCanvas(), List.of(row), viewport, null, "Local", "Player",
                    new AtomChatConfig(), 5, 255, 1F, false, 0);
            assertTrue(surface.readPixels(pixels, 0, 0));
            assertTrue(leftmostPixel(pixels, 0xFFFF3311) >= 0);
            assertEquals(-1, leftmostPixel(pixels, 0xFF1133FF), "the second rich line must not be visible");
        }
    }

    @Test void gifAdvancesInAConstantHudSelectionAndRespectsImageToggle() throws Exception {
        var clock = new AtomicLong();
        var loader = ChatHudGifFixture.frames(clock);
        var config = new AtomChatConfig();
        var row = player(false, "[[CICode,url=http://test/hud.gif,name=thumb]]");
        var viewport = new ChatHudRenderer.Viewport(8, 180, 310, 164, 1, 1);
        var hud = new ChatHudRenderer(loader);
        try (var surface = Surface.makeRasterN32Premul(340, 190); var pixels = new Bitmap()) {
            pixels.allocN32Pixels(340, 190);
            for (long time : new long[]{0, 150}) {
                clock.set(time);
                surface.getCanvas().clear(0);
                hud.paint(surface.getCanvas(), List.of(row), viewport, null, "Local", "Player",
                        config, 1, 255, 1F, false, time);
                assertTrue(surface.readPixels(pixels, 0, 0));
                assertTrue(leftmostPixel(pixels, time == 0 ? 0xFFFF0000 : 0xFF00FF00) >= 0);
            }
            config.imageMessagesEnabled = false;
            surface.getCanvas().clear(0);
            hud.paint(surface.getCanvas(), List.of(row), viewport, null, "Local", "Player",
                    config, 1, 255, 1F, false, 150);
            assertTrue(surface.readPixels(pixels, 0, 0));
            assertEquals(-1, leftmostPixel(pixels, 0xFF00FF00));
        }
    }

    @Test void unavailableAndMalformedCodesRenderTheSamePlaceholderAsHiddenImages() {
        var config = new AtomChatConfig();
        var row = player(false, "[[CICode,url=http://test/unavailable.png]]");
        var malformed = player(false, "[[CICode,name=missing]]");
        var viewport = new ChatHudRenderer.Viewport(8, 190, 310, 170, 1, 1);
        var hud = new ChatHudRenderer((url, visible) -> null);
        try (var surface = Surface.makeRasterN32Premul(340, 200); var pixels = new Bitmap()) {
            pixels.allocN32Pixels(340, 200);
            var canvas = surface.getCanvas();
            hud.paint(canvas, List.of(row), viewport, null, "Local", "Player", config, 1, 255, 1F, false, 0);
            assertTrue(surface.readPixels(pixels, 0, 0));
            int unavailableWidth = inkWidth(pixels);
            canvas.clear(0);
            hud.paint(canvas, List.of(malformed), viewport, null, "Local", "Player", config, 1, 255, 1F, false, 0);
            assertTrue(surface.readPixels(pixels, 0, 0));
            assertEquals(unavailableWidth, inkWidth(pixels));

            config.imageMessagesEnabled = false;
            canvas.clear(0);
            hud.paint(canvas, List.of(row), viewport, null, "Local", "Player", config, 1, 255, 1F, false, 0);
            assertTrue(surface.readPixels(pixels, 0, 0));
            assertEquals(unavailableWidth, inkWidth(pixels));
        }
    }

    @Test void measureIdleHudRasterPaint() {
        assumeTrue("1".equals(System.getenv("ATOMCHAT_CHAT_HUD_BENCH")));
        var rows = new ArrayList<ChatMessage>();
        for (int i = 0; i < 10; i++) rows.add(player((i & 1) == 0, "Short rich message " + i + " ✨"));
        var config = new AtomChatConfig();
        var viewport = ChatHudRenderer.Viewport.of(new Matrix4f(), 640, 360, 3, 1, 320).fit(10);
        var hud = new ChatHudRenderer();
        try (var surface = Surface.makeRasterN32Premul(1050, 1080)) {
            var canvas = surface.getCanvas();
            canvas.scale(viewport.density(), viewport.density());
            for (int repeat = 0; repeat < 3; repeat++) {
                long[] samples = new long[1200];
                for (int i = -600; i < samples.length; i++) {
                    canvas.clear(0);
                    long start = System.nanoTime();
                    hud.paint(canvas, rows, viewport, null, "Local", "Player", config, 10, 255, 1F, true, i);
                    long elapsed = System.nanoTime() - start;
                    if (i >= 0) samples[i] = elapsed;
                }
                Arrays.sort(samples);
                System.out.printf(Locale.ROOT, "CHAT_HUD_RASTER p50=%.4fms p95=%.4fms%n",
                        samples[samples.length / 2] / 1e6, samples[samples.length * 95 / 100] / 1e6);
            }
        }
    }
    @Test void idleChatKeepsPaintedRichRowsBeyondVanillaLifetime() {
        var config = new AtomChatConfig();
        config.bubbleStyle = AtomChatConfig.BubbleStyle.CORNER;
        config.ownBubbleColor = 0xFF1222EE;
        config.otherBubbleColor = 0xFFEE2212;
        config.secondaryCapsuleBg = 0xFF00FF00;
        var rows = List.of(
                player(true, "Short own body"), player(false, "A noticeably longer body"),
                new ChatMessage(RichText.literal("A system message").toText(), false, true));
        var viewport = new ChatHudRenderer.Viewport(8, 380, 360, 370, 1, 1);
        var hud = new ChatHudRenderer();

        try (var surface = Surface.makeRasterN32Premul(390, 400); var pixels = new Bitmap()) {
            pixels.allocN32Pixels(390, 400);
            var canvas = surface.getCanvas();
            int saveCount = canvas.getSaveCount();
            for (long time : new long[]{0L, 20_000L}) {
                canvas.clear(0);
                hud.paint(canvas, rows, viewport, null, "Local", "Player", config, 5, 255, 1F, true, time);
                assertEquals(saveCount, canvas.getSaveCount());
                assertTrue(surface.readPixels(pixels, 0, 0));
                int ownX = leftmostPixel(pixels, config.ownBubbleColor);
                int otherX = leftmostPixel(pixels, config.otherBubbleColor);
                assertTrue(ownX >= 0 && ownX < 80, "own bubble should start beside the left avatar");
                assertEquals(ownX, otherX, "equal left edges for unequal bubble widths");
                IntPredicate green = pixel -> (pixel & 0x00FF00FF) == 0 && (pixel >> 8 & 0xFF) > 240;
                int capsuleX = leftmostPixel(pixels, green);
                assertTrue(capsuleX >= 0 && capsuleX < ownX, "the capsule starts in the left margin");
                assertEquals(255, peakAlpha(pixels, green), "idle chat does not fade with age");
                assertEquals(-1, leftmostPixel(pixels, 0xFFFFFF00), "quote capsule does not paint its styled text");
                assertTrue(leftmostPixel(pixels, 0xFFFF00FF) >= ownX, "decorated sender retains its explicit color");
            }
        }
    }

    @Test void wholeStackUsesShortcutSlideAndAlphaAndZeroLimitPaintsNothing() {
        var config = new AtomChatConfig();
        config.secondaryCapsuleBg = 0xFF00FF00;
        var rows = List.of(new ChatMessage(RichText.literal("System message").toText(), false, true));
        var viewport = new ChatHudRenderer.Viewport(30, 150, 200, 140, 1, 1);
        var hud = new ChatHudRenderer();
        IntPredicate green = pixel -> (pixel & 0x00FF00FF) == 0 && (pixel >> 8 & 0xFF) > 240;
        try (var surface = Surface.makeRasterN32Premul(280, 170); var pixels = new Bitmap()) {
            pixels.allocN32Pixels(280, 170);
            var canvas = surface.getCanvas();
            canvas.clear(0);
            hud.paint(canvas, rows, viewport, null, "Local", "Player", config, 5, 255, 1F, false, 0);
            assertTrue(surface.readPixels(pixels, 0, 0));
            int fullX = leftmostPixel(pixels, green);
            assertTrue(fullX >= 0, "the unshifted capsule paints");

            canvas.clear(0);
            hud.paint(canvas, rows, viewport, null, "Local", "Player", config, 5, 255, 0.5F, false, 100);
            assertTrue(surface.readPixels(pixels, 0, 0));
            assertEquals(fullX - 8, leftmostPixel(pixels, green));
            assertEquals(128, peakAlpha(pixels, green));

            canvas.clear(0);
            hud.paint(canvas, rows, viewport, null, "Local", "Player", config, 0, 255, 1F, false, 110);
            assertTrue(surface.readPixels(pixels, 0, 0));
            assertEquals(-1, leftmostPixel(pixels, green));
        }
    }

    @Test void tenPlayerBubblesFitAt720pEvenWhenTheNewestRequiresEllipsis() {
        var config = new AtomChatConfig();
        config.otherBubbleColor = 0xFF1222EE;
        var rows = new ArrayList<ChatMessage>();
        rows.add(player(false, "Long message with lots of words. ".repeat(25)));
        for (int i = 1; i < 10; i++) rows.add(player(false, "Message " + i));
        var viewport = ChatHudRenderer.Viewport.of(new Matrix4f(), 427, 240, 3, 1, 320).fit(10);
        var hud = new ChatHudRenderer();
        try (var surface = Surface.makeRasterN32Premul(520, 720); var pixels = new Bitmap()) {
            pixels.allocN32Pixels(520, 720);
            var canvas = surface.getCanvas();
            canvas.clear(0);
            canvas.scale(viewport.density(), viewport.density());
            hud.paint(canvas, rows, viewport, null, "Local", "Player", config, 10, 255, 1F, false, 0);
            assertTrue(surface.readPixels(pixels, 0, 0));
            int bands = 0;
            boolean previous = false;
            for (int y = 0; y < pixels.getHeight(); y++) {
                boolean filled = false;
                for (int x = 40; x < 180; x++) {
                    if (pixels.getColor(x, y) == config.otherBubbleColor) {
                        filled = true;
                        break;
                    }
                }
                if (filled && !previous) bands++;
                previous = filled;
            }
            assertEquals(10, bands, "ten player bubble rows fit above the original anchor");
        }
    }

    @Test void openingPanelDuringMessageSlideKeepsTheCurrentRowPositions() {
        var config = new AtomChatConfig();
        var first = player(false, "First");
        var second = player(false, "Second");
        var viewport = new ChatHudRenderer.Viewport(8, 380, 360, 370, 1, 1);
        var hud = new ChatHudRenderer();
        var history = new ChatHudHistory<ChatMessage, Object, ChatMessage>();
        var world = new Object();
        try (var surface = Surface.makeRasterN32Premul(390, 400); var pixels = new Bitmap()) {
            pixels.allocN32Pixels(390, 400);
            var canvas = surface.getCanvas();
            hud.select(world, history, List.of(first), 1, message -> message, false);
            hud.paint(canvas, List.of(first), viewport, null, "Local", "Player", config, 1, 255, 1F, true, 0);
            var messages = List.of(second);
            hud.select(world, history, messages, 1, message -> message, false);
            hud.paint(canvas, messages, viewport, null, "Local", "Player", config, 1, 255, 1F, true, 1000);
            canvas.clear(0);
            hud.paint(canvas, messages, viewport, null, "Local", "Player", config, 1, 255, 1F, true, 1050);
            assertTrue(surface.readPixels(pixels, 0, 0));
            byte[] before = pixels.readPixels();
            hud.select(world, history, messages, 1, message -> message, true);
            canvas.clear(0);
            hud.paint(canvas, messages, viewport, null, "Local", "Player", config, 1, 255, 1F, false, 1050);
            assertTrue(surface.readPixels(pixels, 0, 0));
            assertArrayEquals(before, pixels.readPixels());
        }
    }

    private static ChatMessage player(boolean own, String value) {
        var name = RichText.literal("Styled sender").mapStyles(style -> style.withColor(0xFF00FF));
        var body = RichText.literal(value);
        // An unknown profile still has its captured rich label and the panel's avatar placeholder.
        var quote = RichText.literal("「引用 @Quote sender: Quote」").mapStyles(style -> style.withColor(0xFFFF00));
        var wire = RichText.concat(quote, body);
        return new ChatMessage(wire.toText(), own, false, "Quote sender", "Quote", null,
                "Styled sender", "", body.getString(), name, body);
    }

    private static int leftmostPixel(Bitmap pixels, int argb) {
        return leftmostPixel(pixels, pixel -> pixel == argb);
    }

    private static int leftmostPixel(Bitmap pixels, IntPredicate color) {
        for (int x = 0; x < pixels.getWidth(); x++) {
            for (int y = 0; y < pixels.getHeight(); y++) {
                if (color.test(pixels.getColor(x, y))) return x;
            }
        }
        return -1;
    }

    private static int peakAlpha(Bitmap pixels, IntPredicate color) {
        int alpha = 0;
        for (int x = 0; x < pixels.getWidth(); x++) {
            for (int y = 0; y < pixels.getHeight(); y++) {
                int pixel = pixels.getColor(x, y);
                if (color.test(pixel)) alpha = Math.max(alpha, pixel >>> 24);
            }
        }
        return alpha;
    }

    private static int inkWidth(Bitmap pixels) {
        for (int x = pixels.getWidth() - 1; x >= 0; x--) {
            for (int y = 0; y < pixels.getHeight(); y++) {
                if ((pixels.getColor(x, y) >>> 24) != 0) return x + 1;
            }
        }
        return 0;
    }

}
