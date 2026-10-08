package com.atom.chat.page;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which picture a click opens.
 *
 * <p>Only a bubble whose bitmap the frame actually found may answer: the image
 * download is asynchronous, so between arrival and completion the same bubble
 * is drawn as a "loading" plate, and with the receive switch off the very same
 * message is a green [图片] capsule that never fetches anything. A rule keyed on
 * "the message has an image code" would open an empty preview from either — the
 * hit therefore carries the url the draw resolved, and a bubble that resolved
 * nothing carries none.
 *
 * <p>Everything runs through the real drawing frame: the hit rows under test are
 * the ones {@code draw()} published, and the negative case at the bottom drives
 * the shared {@link com.atom.chat.image.ImageLoader} itself (a url it can neither
 * fetch nor has cached answers null, which is exactly the loading state).
 */
class MessageImageHitTest {

    private static final int SURFACE_W = 420;
    private static final int SURFACE_H = 820;
    private static final float PANEL_X = 10.0F;
    private static final float PANEL_Y = 10.0F;
    private static final float PANEL_W = 400.0F;
    private static final float PANEL_H = 780.0F;

    /**
     * A code with a real intrinsic size, and a scheme nothing can fetch — the
     * shared loader neither has it cached nor may it download it.
     */
    private static final String IMAGE_URL = "atomchat-test://preview-image";
    private static final String IMAGE_CODE = "[[CICode,url=" + IMAGE_URL + ",w=120,h=80]]";
    /** A code with no usable url: the [图片] placeholder capsule, never an image. */
    private static final String BROKEN_CODE = "[[CICode,name=broken]]";
    private static final String TEXT = "a plain line";

    @TempDir
    static Path platformRoot;

    @BeforeAll
    static void installTemporaryPlatform() {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return platformRoot.resolve("config");
            }

            @Override
            public Path gameDir() {
                return platformRoot.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    /**
     * The happy path: a message whose image is in hand opens the preview from
     * the picture itself, and only from the picture.
     */
    @Test
    void aBubbleShowingARealImageIsClickableOnThePictureItself() {
        Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        MessageListView view = null;
        try (Surface bitmapSurface = Surface.makeRasterN32Premul(40, 30)) {
            Image bitmap = bitmapSurface.makeImageSnapshot();
            AtomicReference<String> asked = new AtomicReference<>();
            AtomicReference<Boolean> askedVisible = new AtomicReference<>();
            List<ChatMessage> feed = new ArrayList<>();
            feed.add(new ChatMessage(Text.literal(IMAGE_CODE), false));
            feed.add(new ChatMessage(Text.literal(TEXT), false));
            view = new MessageListView(new OffscreenHost(openAfter(feed)),
                    (url, visible) -> {
                        asked.set(url);
                        askedVisible.set(visible);
                        return bitmap;
                    });
            draw(view, surface.getCanvas(), feed);

            assertEquals(IMAGE_URL, asked.get(),
                    "the draw must ask the loader for the code's own url");
            assertTrue(askedVisible.get(),
                    "the message list's draw is the viewport-gated call that may start a download");

            MessageListView.MessageHit hit = view.hits().get(0);
            assertEquals(IMAGE_URL, hit.imageUrl(),
                    "a bubble whose bitmap was found must publish that url on its hit");
            // The bubble is the picture and nothing else: probes read the hit's own geometry.
            float cx = hit.bubbleX() + hit.bubbleWidth() / 2.0F;
            float cy = hit.bubbleY() + (hit.bubbleBottom() - hit.bubbleY()) / 2.0F;
            assertNotNull(view.imageHitAt(cx, cy), "the picture itself opens the preview");
            assertEquals(hit.index(), view.imageHitAt(cx, cy).index(), "the hit is that row's own");
            assertNull(view.imageHitAt(cx, hit.bubbleY() - 2.0F),
                    "the name band above the picture is not the picture");
            assertNull(view.imageHitAt(hit.bubbleX() - 4.0F, cy),
                    "left of the picture is not the picture");
            assertNull(view.imageHitAt(hit.bubbleX() + hit.bubbleWidth() + 4.0F, cy),
                    "right of the picture is not the picture");
            assertNull(view.imageHitAt(cx, hit.bubbleBottom() + 4.0F),
                    "below the picture is not the picture");

            // A text bubble next to it has no picture, so it never answers — the
            // rule keys on the resolved bitmap, not on "a bubble was drawn here".
            MessageListView.MessageHit text = view.hits().get(1);
            float tx = text.bubbleX() + text.bubbleWidth() / 2.0F;
            float ty = text.bubbleY() + (text.bubbleBottom() - text.bubbleY()) / 2.0F;
            assertNull(view.imageHitAt(tx, ty), "a text bubble has no picture to open");
        } finally {
            if (view != null) {
                view.dispose();
            }
            surface.close();
        }
    }

    /**
     * The loading state, driven through the real loader: nothing is cached for
     * this url and its scheme cannot be fetched, so the frame paints the loading
     * plate — and the plate must stay inert.
     */
    @Test
    void aBubbleStillLoadingItsImageIsNotClickable() {
        Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        MessageListView view = null;
        try {
            List<ChatMessage> feed = List.of(new ChatMessage(Text.literal(IMAGE_CODE), false));
            view = new MessageListView(new OffscreenHost(openAfter(feed)));
            draw(view, surface.getCanvas(), feed);

            MessageListView.MessageHit hit = view.hits().get(0);
            assertNull(hit.imageUrl(), "a bubble with no bitmap yet publishes no url");
            float cx = hit.bubbleX() + hit.bubbleWidth() / 2.0F;
            float cy = hit.bubbleY() + (hit.bubbleBottom() - hit.bubbleY()) / 2.0F;
            assertNull(view.imageHitAt(cx, cy),
                    "clicking the loading plate must not open an empty preview");
        } finally {
            if (view != null) {
                view.dispose();
            }
            surface.close();
        }
    }

    /** The [图片] capsule of a code we cannot turn into an image: inert. */
    @Test
    void thePlaceholderCapsuleIsNotClickable() {
        Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        MessageListView view = null;
        try {
            List<ChatMessage> feed = List.of(new ChatMessage(Text.literal(BROKEN_CODE), false));
            view = new MessageListView(new OffscreenHost(openAfter(feed)));
            draw(view, surface.getCanvas(), feed);

            MessageListView.MessageHit hit = view.hits().get(0);
            assertNull(hit.imageUrl(), "the capsule is not an image");
            float cx = hit.bubbleX() + hit.bubbleWidth() / 2.0F;
            float cy = hit.bubbleY() + (hit.bubbleBottom() - hit.bubbleY()) / 2.0F;
            assertNull(view.imageHitAt(cx, cy), "the [图片] capsule never opens a preview");
        } finally {
            if (view != null) {
                view.dispose();
            }
            surface.close();
        }
    }

    /**
     * The same message with the receive switch off: a valid code, an image the
     * loader could hand over, and still nothing clickable — the capsule is what
     * is on screen, so the capsule is what a click hits.
     */
    @Test
    void theCapsuleWithImageReceivingOffIsNotClickable() {
        Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        MessageListView view = null;
        boolean previous = AtomChatConfig.get().imageMessagesEnabled;
        try (Surface bitmapSurface = Surface.makeRasterN32Premul(40, 30)) {
            Image bitmap = bitmapSurface.makeImageSnapshot();
            AtomChatConfig.get().imageMessagesEnabled = false;
            List<ChatMessage> feed = List.of(new ChatMessage(Text.literal(IMAGE_CODE), false));
            view = new MessageListView(new OffscreenHost(openAfter(feed)), (url, visible) -> bitmap);
            draw(view, surface.getCanvas(), feed);

            MessageListView.MessageHit hit = view.hits().get(0);
            assertNull(hit.imageUrl(), "the capsule draws no image, so its hit carries none");
            float cx = hit.bubbleX() + hit.bubbleWidth() / 2.0F;
            float cy = hit.bubbleY() + (hit.bubbleBottom() - hit.bubbleY()) / 2.0F;
            assertNull(view.imageHitAt(cx, cy), "the capsule never opens a preview");
        } finally {
            AtomChatConfig.get().imageMessagesEnabled = previous;
            if (view != null) {
                view.dispose();
            }
            surface.close();
        }
    }

    private static void draw(MessageListView view, Canvas canvas, List<ChatMessage> feed) {
        UiLayout layout = UiLayout.of(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        view.draw(canvas, layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h(),
                feed, new ScrollController());
    }

    /**
     * A screen-open stamp strictly after the feed, so the rows count as already
     * delivered and the draw path publishes them without waiting out the
     * entrance animation.
     */
    private static long openAfter(List<ChatMessage> messages) {
        return messages.stream().mapToLong(ChatMessage::getTimestamp).max().orElse(0L) + 1L;
    }

    /** The screen-provided answers the message list needs while drawing. */
    private static final class OffscreenHost implements MessageListView.Host {
        private final UUID own = UUID.nameUUIDFromBytes(
                "image-hit-own".getBytes(StandardCharsets.UTF_8));
        private final long openStart;

        private OffscreenHost(long openStart) {
            this.openStart = openStart;
        }

        @Override
        public UUID ownUuid() {
            return own;
        }

        @Override
        public String ownName() {
            return "Offscreen";
        }

        @Override
        public String senderName(ChatMessage message) {
            return message.isOwn() ? "Offscreen" : "Alice";
        }

        @Override
        public long openStart() {
            return openStart;
        }
    }
}
