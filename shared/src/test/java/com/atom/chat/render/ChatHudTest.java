package com.atom.chat.render;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.ChatHudGifFixture;
import com.atom.chat.image.MessageImage;
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

    @Test void antiSpamCountsOnlyAdjacentHudMessagesAndPreservesStableFrameIdentity() throws Exception {
        var history = new ChatHudHistory<String, String, ChatMessage>();
        String older = "old";
        String separator = "separator";
        String first = "first";
        String second = "second";
        String third = "third";
        var rows = List.of(third, second, first, separator, older);
        for (int i = 0; i < rows.size(); i++) {
            String row = rows.get(i);
            var body = RichText.literal(row == separator ? "different" : "repeat");
            var message = new ChatMessage(body.toText(), false, false, null, null, null,
                    "Alice", "Alice", body.getString()).withDuplicateCount(1, 1_000L * (rows.size() - i));
            history.stage(row, message);
            history.bind(row, row, rows);
        }
        var hud = new ChatHudRenderer();
        var previous = com.atom.chat.chat.MessageMerge.antiSpamEnabledSupplier;
        var selection = ChatHudRenderer.class.getDeclaredField("recent");
        selection.setAccessible(true);
        try {
            com.atom.chat.chat.MessageMerge.antiSpamEnabledSupplier = () -> true;
            Object world = new Object();
            hud.select(world, history, rows, 2, key -> fail("missing bound row"), false);
            var selected = (List<?>) selection.get(hud);
            assertEquals(2, selected.size());
            var merged = (ChatMessage) selected.get(0);
            assertEquals(3, merged.getDuplicateCount());
            assertEquals(history.presentation(third, key -> fail()).getId(), merged.getId());
            assertEquals("different", ((ChatMessage) selected.get(1)).getDisplayText());
            hud.select(world, history, rows, 2, key -> fail("missing bound row"), false);
            assertSame(merged, selected.get(0));

            com.atom.chat.chat.MessageMerge.antiSpamEnabledSupplier = () -> false;
            hud.select(world, history, rows, 2, key -> fail("missing bound row"), false);
            assertEquals(1, ((ChatMessage) selected.get(0)).getDuplicateCount());
        } finally {
            hud.clear();
            com.atom.chat.chat.MessageMerge.antiSpamEnabledSupplier = previous;
        }
    }

    @Test void unstyledHudPlayerNameUsesWhiteEvenOnALightPanelTheme() {
        var config = new AtomChatConfig();
        config.panelBgColor = 0xFFF4F4F4;
        config.textPrimaryColor = 0xFF141414;
        config.otherBubbleColor = 0xFF282E41;
        config.otherBubbleTextColor = 0xFF88CCEE;
        var body = RichText.literal("HUD readability");
        var row = new ChatMessage(body.toText(), false, false, null, null, null,
                "HUD Tester", "HudTester", body.getString(), RichText.literal("HUD Tester"), body);
        var hud = new ChatHudRenderer();
        var view = new ChatHudRenderer.Viewport(8, 120, 240, 100, 1F, 1F);
        try (var surface = Surface.makeRasterN32Premul(285, 155); var pixels = new Bitmap()) {
            pixels.allocN32Pixels(285, 155);
            hud.paint(surface.getCanvas(), List.of(row), view, null, "Local", "Player",
                    config, 1, 255, 1F, false, 0);
            assertTrue(surface.readPixels(pixels, 0, 0));
            assertEquals(255, peakAlpha(pixels, pixel -> (pixel & 0xFFFFFF) == 0xFFFFFF));
        } finally {
            hud.clear();
        }
    }

    @Test void hudWidthUsesTheSpaceBeforeTheVanillaHotbarAtEveryGuiScale() {
        float previousWidth = Float.POSITIVE_INFINITY;
        for (int guiScale = 1; guiScale <= 4; guiScale++) {
            int guiWidth = (int)Math.ceil(1920D / guiScale);
            int guiHeight = (int)Math.ceil(1080D / guiScale);
            for (double chatScale : new double[]{.5D, 1D}) {
                var view = ChatHudRenderer.Viewport.of(new Matrix4f(), guiWidth, guiHeight,
                        guiScale, chatScale, guiWidth).fit(5);
                float rightGui = (view.x() + view.width()) * view.guiPerUnit();
                assertTrue(rightGui <= guiWidth / 2F - 91F - 6F,
                        "GUI " + guiScale + ", chat " + chatScale + ": right=" + rightGui);
                assertTrue(rightGui + UiTokens.MESSAGE_SLIDE * view.guiPerUnit() <= guiWidth / 2F - 91F,
                        "the full own-message entrance must remain outside the hotbar");
            }
            var view = ChatHudRenderer.Viewport.of(new Matrix4f(), guiWidth, guiHeight,
                    guiScale, 1D, guiWidth);
            assertTrue(view.width() < previousWidth, "lower GUI scale must reveal more text");
            previousWidth = view.width();
        }
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

    @Test void leftOffhandReservesItsRealSlotWithoutChangingTheHudScale() {
        var ordinary = ChatHudRenderer.Viewport.of(new Matrix4f(), 427, 240, 3, 1, 320);
        var offhand = ChatHudRenderer.Viewport.of(new Matrix4f(), 427, 240, 3, 1, 320, true);
        assertEquals(29F, (ordinary.width() - offhand.width()) * offhand.guiPerUnit(), .001F);
        assertEquals(ordinary.density(), offhand.density());
        assertEquals(ordinary.bottom(), offhand.bottom());
    }

    @Test void transformedGlobalHudKeepsTheChatReplacementAndItsLocalLayout() {
        var base = ChatHudRenderer.Viewport.of(new Matrix4f(), 480, 300, 3, 1, 320);
        var zoomPose = new Matrix4f().translate(-34, -21, 0).scale(1.15F, 1.15F, 1);
        var zoomed = ChatHudRenderer.Viewport.of(zoomPose, 480, 300, 3, 1, 320);
        assertNotNull(zoomed, "a scaled HUD must keep AtomChat's renderer");
        assertEquals(base.width(), zoomed.width());
        assertEquals(base.bottom(), zoomed.bottom());
        assertEquals(base.density(), zoomed.density(), "zoom must not change the cached raster density");
    }

    @Test void aForgeStyleChatOffsetKeepsTheBottomOfTheLatestMessageOnScreen() {
        var pos = new Matrix4f().translate(10, 35, 0);
        var view = ChatHudRenderer.Viewport.of(pos, 300, 180, 3, 1, 320);
        assertEquals(14F, view.x() * view.guiPerUnit() + view.transform().x(), .001F);
        assertEquals(180F, view.bottom() * view.guiPerUnit() + view.transform().y(), .001F);
    }

    @Test void zoomMovesTheChatAndShortcutInTheSameGuiCoordinates() {
        var config = new AtomChatConfig();
        config.secondaryCapsuleBg = 0xFF1655CC;
        var hud = new ChatHudRenderer();
        var row = new ChatMessage(RichText.literal("System message").toText(), false, true);
        Matrix4f pose = new Matrix4f().translate(22, 8, 0).scale(.75F, .75F, 1F);
        var view = ChatHudRenderer.Viewport.of(pose, 320, 240, 2, 1, 180);
        try (var surface = Surface.makeRasterN32Premul(640, 480); var pixels = new Bitmap()) {
            var canvas = surface.getCanvas();
            canvas.clear(0);
            canvas.save();
            canvas.scale(view.density(), view.density());
            hud.paint(canvas, List.of(row), view, null, "Local", "Player", config, 1, 255, 1, false, 0);
            canvas.restore();
            ChatShortcutHint.renderOnHud(canvas, 240, 2, 2, view.transform(), "Y", 1);
            pixels.allocN32Pixels(640, 480);
            assertTrue(surface.readPixels(pixels, 0, 0));
            assertEquals(53, leftmostPixel(pixels, value -> value == 0xFFFFFFFF), 2);
            assertEquals(50, leftmostPixel(pixels, value -> value == 0xFF1655CC), 2);
            int capsuleBottom = -1, shortcutTop = 480;
            for (int y = 0; y < 480; y++) {
                if (pixels.getColor(75, y) == 0xFF1655CC) capsuleBottom = y;
                if (pixels.getColor(61, y) == 0xFFFFFFFF) shortcutTop = Math.min(shortcutTop, y);
            }
            assertTrue(capsuleBottom >= 0 && shortcutTop < 480);
            assertEquals(9, shortcutTop - capsuleBottom - 1, 3, "the six GUI pixel gap scales with the HUD");
        }
    }

    @Test void newestThumbnailLeavesSixGuiPixelsAboveTheShortcut() {
        var config = new AtomChatConfig();
        var imageMessage = player(false, "[[CICode,url=http://test/thumb.png,name=thumb]]");
        // Minecraft caps GUI scaling at a minimum logical window of 320x240.
        int guiWidth = 320, guiHeight = 240;
        try (var source = Surface.makeRasterN32Premul(32, 32)) {
            source.getCanvas().clear(0xFFFF0000);
            try (var image = source.makeImageSnapshot()) {
                for (int scale : new int[]{2, 3, 5}) {
                    int width = guiWidth * scale, height = guiHeight * scale;
                    var viewport = ChatHudRenderer.Viewport.of(new Matrix4f(), guiWidth, guiHeight, scale, 1, 200).fit(1);
                    var hud = new ChatHudRenderer((url, visible) -> new MessageImage(image, false));
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
        var lines = MessagePaint.oneLine(text, font, 300);
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

    @Test void hudKeepsOpaqueImageShadowAndDropsTransparentImageOutline() throws Exception {
        int width = 340, height = 220, background = 0xFFE4D6B5, color = 0xFF216F9E;
        var config = new AtomChatConfig();
        var view = new ChatHudRenderer.Viewport(8, 200, 310, 190, 1, 1);
        var row = player(false, "[[CICode,url=http://test/preview.png,name=preview,w=104,h=66]]");
        try (var opaque = Surface.makeRasterN32Premul(104, 66);
             var visibleAlpha = Surface.makeRasterN32Premul(104, 66);
             var output = Surface.makeRasterN32Premul(width * 2, height);
             var paint = new io.github.humbleui.skija.Paint();
             var pixels = new Bitmap()) {
            opaque.getCanvas().clear(color);
            visibleAlpha.getCanvas().clear(0);
            visibleAlpha.getCanvas().drawRect(io.github.humbleui.types.Rect.makeXYWH(20, 8, 64, 48), paint.setColor(color));
            try (var solid = opaque.makeImageSnapshot(); var cutout = visibleAlpha.makeImageSnapshot()) {
                var current = new java.util.concurrent.atomic.AtomicReference<>(new MessageImage(solid, false));
                var hud = new ChatHudRenderer((url, visible) -> current.get());
                try {
                    var canvas = output.getCanvas();
                    canvas.clear(background);
                    hud.paint(canvas, List.of(row), view, null, "Local", "Player", config, 1, 255, 1F, false, 0);
                    assertTrue(pixels.allocN32Pixels(width, height));
                    assertTrue(output.readPixels(pixels, 0, 0));
                    int x = leftmostPixel(pixels, color);
                    assertTrue(x >= 0);
                    int firstY = 0, lastY = height - 1;
                    while (pixels.getColor(x + 8, firstY) != color) firstY++;
                    while (pixels.getColor(x + 8, lastY) != color) lastY--;
                    int y = (firstY + lastY) / 2, right = x;
                    while (pixels.getColor(right, y) == color) right++;
                    assertNotEquals(background, pixels.getColor(right + 1, y));

                    current.set(new MessageImage(cutout, true));
                    canvas.save();
                    canvas.translate(width, 0);
                    hud.paint(canvas, List.of(row), view, null, "Local", "Player", config, 1, 255, 1F, false, 0);
                    canvas.restore();
                    assertTrue(output.readPixels(pixels, width, 0));
                    assertEquals(background, pixels.getColor(right + 1, y));
                    assertEquals(color, pixels.getColor((x + right) / 2, y));
                    assertTrue(leftmostPixel(pixels, 0xFFFF00FF) >= 0, "the player label remains");
                    if ("1".equals(System.getenv("ATOMCHAT_MESSAGE_SHADOW_PREVIEW"))) {
                        var path = visualPath("hud-alpha-preview.png");
                        java.nio.file.Files.createDirectories(path.getParent());
                        try (var frame = output.makeImageSnapshot(); var png = frame.encodeToData()) {
                            java.nio.file.Files.write(path, png.getBytes());
                        }
                    }
                } finally {
                    hud.clear();
                }
            }
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
            long[] coldSamples = new long[40];
            for (int i = 0; i < coldSamples.length; i++) {
                HudShadow.release();
                var cold = new ChatHudRenderer();
                canvas.clear(0);
                long start = System.nanoTime();
                cold.paint(canvas, rows, viewport, null, "Local", "Player", config, 10, 255, 1F, false, 0);
                coldSamples[i] = System.nanoTime() - start;
                cold.clear();
            }
            Arrays.sort(coldSamples);
            System.out.printf(Locale.ROOT, "CHAT_HUD_COLD p50=%.4fms p95=%.4fms%n",
                    coldSamples[coldSamples.length / 2] / 1e6, coldSamples[coldSamples.length * 95 / 100] / 1e6);
            hud.clear();
        }
    }

    @Test void captureShadowPreview() throws Exception {
        assumeTrue("1".equals(System.getenv("ATOMCHAT_CHAT_HUD_BENCH")));
        var rows = List.of(quotedPlayer(true, "This bubble has an outer shadow"),
                player(false, "[[CICode,url=http://test/preview.png,name=preview]]"),
                new ChatMessage(RichText.literal("System message").toText(), false, true));
        var config = new AtomChatConfig();
        var view = ChatHudRenderer.Viewport.of(new Matrix4f(), 220, 185, 2, 1, 180).fit(3);
        try (var attachment = Surface.makeRasterN32Premul(160, 60);
             var output = Surface.makeRasterN32Premul(880, 370);
             var paint = new io.github.humbleui.skija.Paint()) {
            for (int x = 0; x < 160; x += 4) {
                attachment.getCanvas().drawRect(io.github.humbleui.types.Rect.makeXYWH(x, 0, 4, 60),
                        paint.setColor(0xFF245D84 + x * 0x00010100));
            }
            try (var thumbnail = attachment.makeImageSnapshot()) {
                var hud = new ChatHudRenderer((url, visible) -> new MessageImage(thumbnail, false));
                var canvas = output.getCanvas();
                for (int side = 0; side < 2; side++) {
                    canvas.save();
                    canvas.translate(440 * side, 0);
                    for (int y = 0; y < 370; y += 24) {
                        for (int x = 0; x < 440; x += 24) {
                            boolean lighter = (x / 24 + y / 24) % 2 == 0;
                            int color = side == 0 ? (lighter ? 0xFFE5D5AA : 0xFFCCBA8E)
                                    : (lighter ? 0xFF384E42 : 0xFF27392F);
                            canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(x, y, Math.min(24, 440 - x), 24),
                                    paint.setColor(color));
                        }
                    }
                    canvas.save();
                    canvas.scale(view.density(), view.density());
                    hud.paint(canvas, rows, view, null, "Local", "Player", config, 3, 255, 1, false, 0);
                    canvas.restore();
                    ChatShortcutHint.renderGuiScaled(canvas, 440, 370, 1, 2, "Y", 1);
                    canvas.restore();
                }
                var path = Path.of(System.getProperty("atomchat.repoRoot", "../.."), "build/v093/hud-shadows/preview.png");
                try (var image = output.makeImageSnapshot(); var png = image.encodeToData()) {
                    java.nio.file.Files.createDirectories(path.getParent());
                    java.nio.file.Files.write(path, png.getBytes());
                }
            }
        }
    }
    @Test void idleChatKeepsPaintedRichRowsBeyondVanillaLifetime() {
        var config = new AtomChatConfig();
        config.bubbleStyle = AtomChatConfig.BubbleStyle.CORNER;
        config.ownBubbleColor = 0xFF1222EE;
        config.otherBubbleColor = 0xFFEE2212;
        config.secondaryCapsuleBg = 0xFF00FF00;
        config.secondaryCapsuleText = 0xFFFFFFFF;
        var rows = List.of(
                quotedPlayer(true, "Short own body"), player(false, "A noticeably longer body"),
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
                IntPredicate yellow = pixel -> (pixel >> 16 & 255) > 220 && (pixel >> 8 & 255) > 220 && (pixel & 255) < 80;
                assertTrue(leftmostPixel(pixels, yellow) >= ownX, "the quote paints its styled text beside the avatar");
                assertTrue(leftmostPixel(pixels, 0xFFFF00FF) >= ownX, "decorated sender retains its explicit color");
            }
        }
    }

    @Test void translucentSystemCapsuleHasAnOpacityFloor() {
        var config = new AtomChatConfig();
        config.secondaryCapsuleBg = 0x4000FF00;
        var viewport = new ChatHudRenderer.Viewport(8, 180, 310, 164, 1, 1);
        var row = new ChatMessage(RichText.literal("A system message").toText(), false, true);
        try (var surface = Surface.makeRasterN32Premul(340, 190); var pixels = new Bitmap()) {
            pixels.allocN32Pixels(340, 190);
            new ChatHudRenderer().paint(surface.getCanvas(), List.of(row), viewport, null, "Local", "Player",
                    config, 1, 255, 1F, false, 0);
            assertTrue(surface.readPixels(pixels, 0, 0));
            assertEquals(179, peakAlpha(pixels, pixel -> (pixel & 0xFFFFFF) == 0x00FF00));
            assertEquals(0x4000FF00, config.secondaryCapsuleBg);
            config.secondaryCapsuleBg = 0xCC00FF00;
            assertEquals(0xCC00FF00, config.effectiveSecondaryCapsuleBg());
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

    @Test void zoomedHudGlyphsMatchNativeRenderingAtTheSamePhysicalDensity() throws Exception {
        var config = new AtomChatConfig();
        config.performanceMode = true;
        var message = player(false, "Sharp edges");
        var zoom = new com.atom.chat.ui.HudTransform(.25F, .375F, 1.25F, 1.25F);
        var viewport = new ChatHudRenderer.Viewport(8.25F, 95.25F, 144, 88, 2, 1F, zoom);
        byte[][] ink = new byte[2][];
        try (var surface = Surface.makeRasterN32Premul(420, 260); var bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(420, 260);
            var canvas = surface.getCanvas();
            for (int pass = 0; pass < 2; pass++) {
                canvas.clear(0);
                var hud = new ChatHudRenderer();
                int save = canvas.save();
                try {
                    canvas.scale(2, 2);
                    if (pass == 1) zoom.apply(canvas, 1F);
                    var view = pass == 0 ? viewport : new ChatHudRenderer.Viewport(
                            viewport.x(), viewport.bottom(), viewport.width(), viewport.height(), 2.5F, 1F);
                    hud.paint(canvas, List.of(message), view, null, "Local", "Player",
                            config, 1, 255, 1F, false, 0);
                    assertTrue(surface.readPixels(bitmap, 0, 0));
                    ink[pass] = bitmap.readPixels();
                    String stage = System.getenv("ATOMCHAT_VISUAL_FOLLOWUP");
                    if (stage != null) {
                        String run = System.getenv().getOrDefault("ATOMCHAT_VISUAL_RUN", "new");
                        try (var image = surface.makeImageSnapshot(); var png = image.encodeToData()) {
                            java.nio.file.Files.write(Path.of(stage, "hud-zoom-" + run + "-" + pass + ".png"), png.getBytes());
                        }
                    }
                } finally {
                    canvas.restoreToCount(save);
                    hud.clear();
                }
            }
        }
        // Isolate the magenta sender glyph coverage from the live avatar and bubble.
        int changed = 0;
        for (int i = 0; i < ink[0].length; i += 4) {
            int first = senderCoverage(ink[0], i);
            int second = senderCoverage(ink[1], i);
            if (Math.abs(first - second) > 10) changed++;
        }
        System.out.println("HUD zoom sender coverage differences >10: " + changed);
        assertTrue(changed < 12, "the same physical sender glyphs changed in " + changed + " pixels");
    }

    private static int senderCoverage(byte[] pixels, int offset) {
        return Math.max(0, Math.min(pixels[offset] & 255, pixels[offset + 2] & 255) - (pixels[offset + 1] & 255));
    }

    @Test void compactHudKeepsOneVisibleSenderAndPromotesTheOldestSurvivor() throws Exception {
        var config = new AtomChatConfig();
        var older = identifiedPlayer("First", 1_000L);
        var newer = identifiedPlayer("Second", 2_000L);
        var hud = new ChatHudRenderer();
        try (var surface = Surface.makeRasterN32Premul(320, 240); var bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(320, 240);
            var canvas = surface.getCanvas();
            var viewport = new ChatHudRenderer.Viewport(8, 220, 280, 200, 1F, 1F);
            for (int pass = 0; pass < 3; pass++) {
                config.compactMessagesEnabled = pass != 0;
                canvas.clear(0);
                hud.paint(canvas, pass == 2 ? List.of(newer) : List.of(newer, older), viewport, null,
                        "Local", "Player", config, 2, 255, 1F, false, pass);
                assertTrue(surface.readPixels(bitmap, 0, 0));
                assertEquals(pass == 0 ? 2 : 1, senderBands(bitmap),
                        "one label per visible group, including when its original head leaves");
                if (System.getenv("ATOMCHAT_VISUAL_FOLLOWUP") != null) {
                    try (var image = surface.makeImageSnapshot(); var png = image.encodeToData()) {
                        java.nio.file.Files.write(visualPath("hud-group-" + pass + ".png"), png.getBytes());
                    }
                }
            }
        } finally {
            hud.clear();
        }
    }

    @Test void hudReservesTheOldestBandBesideMediaAndReactsToAClockBoundary() {
        var config = new AtomChatConfig();
        config.compactMessagesEnabled = true;
        config.timestampIntervalMinutes = 0;
        var older = identifiedPlayer("First", 1_000L);
        var middle = identifiedPlayer("Second", 62_000L);
        var newest = identifiedPlayer("[[CICode,url=http://test/tall.png,name=tall,w=90,h=120]]", 62_500L);
        var hud = new ChatHudRenderer((url, visible) -> null);
        try (var surface = Surface.makeRasterN32Premul(320, 306); var bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(320, 306);
            var canvas = surface.getCanvas();
            for (int pass = 0; pass < 5; pass++) {
                canvas.clear(0);
                config.timestampIntervalMinutes = pass == 1 ? 1 : 0;
                boolean departing = pass >= 3;
                var viewport = new ChatHudRenderer.Viewport(8, 296, 280, pass < 2 ? 265 : 145, 1F, 1F);
                hud.paint(canvas, departing ? List.of(newest, middle) : List.of(newest, middle, older),
                        viewport, null, "Local", "Player", config, 3, 255, 1F, true, pass == 4 ? 400L : pass);
                assertTrue(surface.readPixels(bitmap, 0, 0));
                assertEquals(pass == 1 ? 2 : 1, senderBands(bitmap),
                        "the visible head and the existing time threshold both keep their captions");
            }
        } finally {
            hud.clear();
        }
    }

    private static Path visualPath(String name) {
        String stage = System.getenv("ATOMCHAT_VISUAL_FOLLOWUP");
        return stage == null ? Path.of(System.getProperty("atomchat.repoRoot", "../.."),
                "build/v093/performance-mode", name) : Path.of(stage, name);
    }

    private static int senderBands(Bitmap pixels) {
        int count = 0;
        boolean previous = false;
        for (int y = 0; y < pixels.getHeight(); y++) {
            boolean current = false;
            for (int x = 0; x < pixels.getWidth(); x++) {
                int argb = pixels.getColor(x, y);
                if ((argb >>> 24) > 40 && (argb & 0xFFFFFF) == 0xFF00FF) {
                    current = true;
                    break;
                }
            }
            if (current && !previous) count++;
            previous = current;
        }
        return count;
    }

    private static ChatMessage identifiedPlayer(String value, long timestamp) {
        var name = RichText.literal("Styled sender").mapStyles(style -> style.withColor(0xFF00FF));
        var body = RichText.literal(value);
        return new ChatMessage(body.toText(), false, false, null, null, null,
                "Styled sender", "StableProfile", value, name, body, timestamp);
    }

    private static ChatMessage player(boolean own, String value) {
        var name = RichText.literal("Styled sender").mapStyles(style -> style.withColor(0xFF00FF));
        var body = RichText.literal(value);
        // An unknown profile still has its captured rich label and the panel's avatar placeholder.
        return new ChatMessage(body.toText(), own, false, null, null, null,
                "Styled sender", "", body.getString(), name, body);
    }

    private static ChatMessage quotedPlayer(boolean own, String value) {
        var name = RichText.literal("Styled sender").mapStyles(style -> style.withColor(0xFF00FF));
        var body = RichText.literal(value);
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
