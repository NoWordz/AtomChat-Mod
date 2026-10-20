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

    private void probe(String kind) {
        AtomChatConfig cfg = AtomChatConfig.get();
        var snapshot = com.atom.chat.theme.AppearancePresetStore.Snapshot.capture(cfg);
        boolean oldImages = cfg.imageMessagesEnabled;
        int oldInterval = cfg.timestampIntervalMinutes;
        try (Surface target = Surface.makeRasterN32Premul(420, 640);
             Surface source = Surface.makeRasterN32Premul(120, 80)) {
            com.atom.chat.theme.AppearancePresetStore.Snapshot.capture(new AtomChatConfig()).applyTo(cfg);
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
                                (url, visible) -> kind.equals("loaded") ? image : null);
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
            snapshot.applyTo(cfg);
            cfg.imageMessagesEnabled = oldImages;
            cfg.timestampIntervalMinutes = oldInterval;
        }
    }
}
