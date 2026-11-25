package com.atom.chat.page;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.ScrollController;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Pixel probes through the real list, including the three image states. */
class MessageBubbleStyleTest {
    @TempDir static Path directory;

    @BeforeAll static void platform() {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return directory; }
            public Path gameDir() { return directory; }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @Test void textUsesTheSelectedShapeAndMirrorsTheSharpCorner() { probe("text"); }
    @Test void loadedImageAndLoadingPlateUseTheSelectedShape() { probe("loaded"); probe("loading"); }
    @Test void disabledImageCapsuleUsesTheSelectedSharpCorner() { probe("disabled"); }


    @Test void compactSingleTipFollowsItsVisibleGroupForEveryAttachmentState() {
        AtomChatConfig cfg = AtomChatConfig.get();
        var snapshot = com.atom.chat.theme.AppearancePresetStore.Snapshot.capture(cfg);
        boolean oldImages = cfg.imageMessagesEnabled, oldPerformance = cfg.performanceModeEnabled();
        boolean oldCompact = cfg.compactMessagesEnabled;
        int oldInterval = cfg.timestampIntervalMinutes;
        try (Surface target = Surface.makeRasterN32Premul(420, 850);
             Surface source = Surface.makeRasterN32Premul(120, 80);
             Bitmap pixels = new Bitmap()) {
            pixels.allocN32Pixels(420, 850);
            source.getCanvas().clear(0xFFFF0000);
            cfg.graphicsQuality = com.atom.chat.config.GraphicsQuality.PERFORMANCE;
            cfg.timestampIntervalMinutes = 0;
            cfg.bubbleStyle = AtomChatConfig.BubbleStyle.CORNER;
            cfg.secondaryCapsuleBg = 0xFF2C3E50;
            try (Image image = source.makeImageSnapshot()) {
                for (boolean compact : new boolean[]{false, true})
                for (boolean own : new boolean[]{false, true})
                for (String kind : List.of("text", "loaded", "loading", "disabled"))
                for (int count = 1; count <= 3; count++) {
                    cfg.compactMessagesEnabled = compact;
                    cfg.imageMessagesEnabled = !kind.equals("disabled");
                    var rows = new java.util.ArrayList<ChatMessage>();
                    for (int i = 0; i < count; i++) {
                        String content = kind.equals("text") ? "A wider grouped message " + i
                                : "[[CICode,url=atomchat-test://group,w=120,h=80]]";
                        rows.add(new ChatMessage(com.atom.chat.text.RichText.literal(content).toText(), own,
                                false, null, null, new UUID(0, 1), "Player", "Player", content, 1000L + i));
                    }
                    MessageListView.Host host = new MessageListView.Host() {
                        public UUID ownUuid() { return new UUID(0, 1); }
                        public String ownName() { return "Self"; }
                        public String senderName(ChatMessage m) { return "Player"; }
                        public long openStart() { return System.currentTimeMillis(); }
                    };
                    var view = new MessageListView(host, (url, visible) ->
                            kind.equals("loaded") ? new com.atom.chat.image.MessageImage(image, false) : null);
                    try {
                        target.getCanvas().clear(0);
                        view.draw(target.getCanvas(), 10, 10, 400, 820, rows, new ScrollController());
                        assertTrue(target.readPixels(pixels, 0, 0));
                        assertEquals(count, view.hits().size());
                        for (int i = 0; i < count; i++) {
                            var hit = view.hits().get(i);
                            int sideX = own ? (int)Math.floor(hit.bubbleX() + hit.bubbleWidth()) - 2
                                    : (int)Math.ceil(hit.bubbleX()) + 1;
                            boolean middle = compact && i > 0 && i + 1 < count;
                            int cornerX = middle ? (own ? sideX + 1 : sideX - 1) : sideX;
                            int top = pixels.getColor(cornerX, (int)Math.ceil(hit.bubbleY()) + (middle ? 0 : 1)) >>> 24;
                            int bottom = pixels.getColor(cornerX, (int)Math.floor(hit.bubbleBottom()) - (middle ? 1 : 2)) >>> 24;
                            boolean upperTip = !compact || count == 1 || i == count - 1;
                            boolean lowerTip = compact && count > 1 && i == 0;
                            String state = kind + "/own=" + own + "/compact=" + compact + "/row=" + i + "/" + count;
                            if (upperTip) assertTrue(top > 200, state + ": upper tip");
                            else assertEquals(0, top, state + ": round upper corner");
                            if (lowerTip) assertTrue(bottom > 200, state + ": lower tip");
                            else assertEquals(0, bottom, state + ": round lower corner");
                            if (middle) {
                                int insideX = own ? (int)Math.floor(hit.bubbleX() + hit.bubbleWidth()) - 5
                                        : (int)Math.ceil(hit.bubbleX()) + 4;
                                assertTrue((pixels.getColor(insideX, (int)Math.ceil(hit.bubbleY()) + 4) >>> 24) > 200,
                                        state + ": the small connection corner must not become a full capsule");
                            }
                        }
                    } finally { view.dispose(); }
                }
            }
        } finally {
            snapshot.applyTo(cfg);
            cfg.imageMessagesEnabled = oldImages;
            cfg.graphicsQuality = (oldPerformance ? com.atom.chat.config.GraphicsQuality.PERFORMANCE : com.atom.chat.config.GraphicsQuality.ULTRA);
            cfg.compactMessagesEnabled = oldCompact;
            cfg.timestampIntervalMinutes = oldInterval;
        }
    }


    @Test void aFoldedGroupStartsWithItsOwnAvatarAndDownwardTip() {
        var cfg = AtomChatConfig.get();
        var snapshot = com.atom.chat.theme.AppearancePresetStore.Snapshot.capture(cfg);
        boolean oldCompact = cfg.compactMessagesEnabled, oldImages = cfg.imageMessagesEnabled, oldPerformance = cfg.performanceModeEnabled();
        int oldInterval = cfg.timestampIntervalMinutes;
        try (var target = Surface.makeRasterN32Premul(420, 850);
             var source = Surface.makeRasterN32Premul(120, 80);
             var pixels = new Bitmap()) {
            cfg.compactMessagesEnabled = true;
            cfg.bubbleStyle = AtomChatConfig.BubbleStyle.CORNER;
            cfg.graphicsQuality = com.atom.chat.config.GraphicsQuality.PERFORMANCE;
            cfg.timestampIntervalMinutes = 0;
            cfg.secondaryCapsuleBg = 0xFF2C3E50;
            pixels.allocN32Pixels(420, 850);
            source.getCanvas().clear(0xFFFF0000);
            try (var image = source.makeImageSnapshot()) {
                for (boolean own : new boolean[]{false, true})
                for (String kind : List.of("text", "loaded", "loading", "disabled")) {
                    cfg.imageMessagesEnabled = !kind.equals("disabled");
                    var rows = new java.util.ArrayList<ChatMessage>();
                    for (int i = 0; i < 3; i++) {
                        String body = kind.equals("text") ? "A folded group message " + i
                                : "[[CICode,url=atomchat-test://fold,w=120,h=80]]";
                        rows.add(new ChatMessage(com.atom.chat.text.RichText.literal(body).toText(), own, false,
                                null, null, new UUID(0, 1), "Player", "Player", body, 1000L + i));
                    }
                    var host = new MessageListView.Host() {
                        public UUID ownUuid() { return new UUID(0, 1); }
                        public String ownName() { return "Self"; }
                        public String senderName(ChatMessage m) { return "Player"; }
                        public long openStart() { return System.currentTimeMillis(); }
                    };
                    var view = new MessageListView(host, (url, visible) ->
                            kind.equals("loaded") ? new com.atom.chat.image.MessageImage(image, false) : null);
                    try {
                        view.setVisibleStart(1);
                        target.getCanvas().clear(0);
                        view.draw(target.getCanvas(), 10, 10, 400, 820, rows, new ScrollController());
                        assertEquals(2, view.hits().size());
                        var first = view.hits().get(0);
                        assertSame(rows.get(1), first.message());
                        assertTrue(first.avatarSize() > 0, kind + ": folded head keeps its sender");
                        assertTrue(target.readPixels(pixels, 0, 0));
                        int side = own ? (int)Math.floor(first.bubbleX() + first.bubbleWidth()) - 2
                                : (int)Math.ceil(first.bubbleX()) + 1;
                        assertTrue((pixels.getColor(side, (int)Math.floor(first.bubbleBottom()) - 2) >>> 24) > 200,
                                kind + ": the visible head has the first bubble's lower tip");
                        view.setVisibleStart(0);
                        target.getCanvas().clear(0);
                        view.draw(target.getCanvas(), 10, 10, 400, 820, rows, new ScrollController());
                        assertEquals(3, view.hits().size());
                        assertEquals(0F, view.hits().get(1).avatarSize(), "revealing the previous row restores continuation");
                    } finally { view.dispose(); }
                }
            }
        } finally {
            snapshot.applyTo(cfg);
            cfg.compactMessagesEnabled = oldCompact;
            cfg.imageMessagesEnabled = oldImages;
            cfg.graphicsQuality = (oldPerformance ? com.atom.chat.config.GraphicsQuality.PERFORMANCE : com.atom.chat.config.GraphicsQuality.ULTRA);
            cfg.timestampIntervalMinutes = oldInterval;
        }
    }


    private void probe(String kind) {
        AtomChatConfig cfg = AtomChatConfig.get();
        var snapshot = com.atom.chat.theme.AppearancePresetStore.Snapshot.capture(cfg);
        boolean oldImages = cfg.imageMessagesEnabled;
        boolean oldPerformance = cfg.performanceModeEnabled();
        int oldInterval = cfg.timestampIntervalMinutes;
        try (Surface target = Surface.makeRasterN32Premul(420, 640);
             Surface source = Surface.makeRasterN32Premul(120, 80)) {
            com.atom.chat.theme.AppearancePresetStore.Snapshot.capture(new AtomChatConfig()).applyTo(cfg);
            // Isolate the exact bubble and image clip pixels from the outer shadow.
            cfg.graphicsQuality = com.atom.chat.config.GraphicsQuality.PERFORMANCE;
            cfg.timestampIntervalMinutes = 0;
            cfg.imageMessagesEnabled = !kind.equals("disabled");
            cfg.secondaryCapsuleBg = 0xFF2C3E50;
            source.getCanvas().clear(0xFFFF0000);
            try (Image image = source.makeImageSnapshot(); Bitmap pixels = new Bitmap()) {
                pixels.allocN32Pixels(420, 640);
                for (boolean own : new boolean[]{false, true}) {
                    Integer classicCornerInk = null;
                    for (var style : AtomChatConfig.BubbleStyle.values()) {
                        cfg.bubbleStyle = style;
                        String content = kind.equals("text") ? "a wider test message"
                                : "[[CICode,url=atomchat-test://style,w=120,h=80]]";
                        ChatMessage msg = new ChatMessage(Component.literal(content), own);
                        MessageListView.Host host = new MessageListView.Host() {
                            public UUID ownUuid() { return new UUID(0, 1); }
                            public String ownName() { return "Self"; }
                            public String senderName(ChatMessage m) { return "Player"; }
                            public long openStart() { return msg.getTimestamp() + 1; }
                        };
                        MessageListView view = new MessageListView(host,
                                (url, visible) -> kind.equals("loaded") ? new com.atom.chat.image.MessageImage(image, false) : null);
                        try {
                            target.getCanvas().clear(0);
                            view.draw(target.getCanvas(), 10, 10, 400, 600, List.of(msg), new ScrollController());
                            assertTrue(target.readPixels(pixels, 0, 0));
                            var hit = view.hits().get(0);
                            int left = (int) Math.ceil(hit.bubbleX());
                            int right = (int) Math.floor(hit.bubbleX() + hit.bubbleWidth()) - 1;
                            int top = (int) Math.ceil(hit.bubbleY());
                            int avatarCorner = pixels.getColor(own ? right - 1 : left + 1, top + 1) >>> 24;
                            int farCorner = pixels.getColor(own ? left + 1 : right - 1, top + 1) >>> 24;
                            assertEquals(0, farCorner, kind + "/" + style + ": opposite upper corner stays round");
                            if (style == AtomChatConfig.BubbleStyle.CORNER) {
                                assertTrue(avatarCorner > 200, kind + ": sharp corner must face avatar");
                            } else {
                                assertEquals(0, avatarCorner, kind + ": rounded styles have no sharp corner");
                            }
                            int cornerInk = 0;
                            for (int dy = 0; dy < 12; dy++) for (int dx = 0; dx < 12; dx++) {
                                cornerInk += pixels.getColor(left + dx, top + dy) >>> 24;
                            }
                            if (style == AtomChatConfig.BubbleStyle.CLASSIC) classicCornerInk = cornerInk;
                            if (style == AtomChatConfig.BubbleStyle.ROUND && !kind.equals("disabled")) {
                                assertTrue(cornerInk < classicCornerInk, kind + ": round must remove more corner area");
                            }
                        } finally { view.dispose(); }
                    }
                }
            }
        } finally {
            cfg.graphicsQuality = (oldPerformance ? com.atom.chat.config.GraphicsQuality.PERFORMANCE : com.atom.chat.config.GraphicsQuality.ULTRA);
            snapshot.applyTo(cfg);
            cfg.imageMessagesEnabled = oldImages;
            cfg.timestampIntervalMinutes = oldInterval;
        }
    }
}
