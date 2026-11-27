package com.atom.chat.page;

import com.atom.chat.api.ui.AppearanceUi;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.config.GraphicsQuality;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.MessageImage;
import com.atom.chat.pack.PublicChatAvatar;
import com.atom.chat.pack.ServerPack;
import com.atom.chat.pack.ServerPackStore;
import com.atom.chat.platform.Platform;
import com.atom.chat.render.PublicChatAvatarPaint;
import com.atom.chat.render.RichTextRenderer;
import com.atom.chat.text.RichText;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageListAppearanceTest {
    private static final String ID = "panel_appearance_test:cut";
    private static final String LABEL = "appearance.panel_appearance_test.cut";
    private static final String IMAGE = "[[CICode,url=atomchat-test://appearance,w=120,h=80]]";
    private static final int INK = 0xFFFF00C7;
    private static final int PHOTO = 0xFF11AB54;
    private static final UUID SENDER = new UUID(0, 37);
    private static final MessageListView.Host HOST = new MessageListView.Host() {
        public UUID ownUuid() { return SENDER; }
        public String ownName() { return "Self"; }
        public String senderName(ChatMessage message) { return "Player"; }
        public long openStart() { return Long.MAX_VALUE; }
    };

    @TempDir Path root;
    AtomChatConfig config;
    String previousBubble;
    String previousFrame;
    GraphicsQuality previousQuality;
    boolean previousMotion, previousCompact, previousImages, previousPacks;
    int previousInterval, previousCapsuleBg;

    @BeforeEach void installPlatform() {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root; }
            public boolean isModLoaded(String id) { return false; }
        });
        config = AtomChatConfig.get();
        previousBubble = config.bubbleAppearanceId;
        previousFrame = config.avatarFrameAppearanceId;
        previousQuality = config.graphicsQuality;
        previousMotion = config.animationEnabled;
        previousCompact = config.compactMessagesEnabled;
        previousImages = config.imageMessagesEnabled;
        previousPacks = config.serverPacksEnabled;
        previousInterval = config.timestampIntervalMinutes;
        previousCapsuleBg = config.secondaryCapsuleBg;
        config.bubbleAppearanceId = null;
        config.avatarFrameAppearanceId = null;
        config.graphicsQuality = GraphicsQuality.PERFORMANCE;
        config.animationEnabled = false;
        config.compactMessagesEnabled = true;
        config.imageMessagesEnabled = true;
        config.timestampIntervalMinutes = 0;
        config.secondaryCapsuleBg = 0xFF2C3E50;
    }

    @AfterEach void restoreAppearance() {
        AppearanceUi.unregister(AppearanceUi.Part.BUBBLE, ID);
        AppearanceUi.unregister(AppearanceUi.Part.AVATAR_FRAME, ID);
        config.bubbleAppearanceId = previousBubble;
        config.avatarFrameAppearanceId = previousFrame;
        config.graphicsQuality = previousQuality;
        config.animationEnabled = previousMotion;
        config.compactMessagesEnabled = previousCompact;
        config.imageMessagesEnabled = previousImages;
        config.serverPacksEnabled = previousPacks;
        config.timestampIntervalMinutes = previousInterval;
        config.secondaryCapsuleBg = previousCapsuleBg;
    }

    @Test void aGroupDeliversExistingBoundsOwnershipAndColorsForTextAndPlaceholders() {
        var seen = new ArrayList<AppearanceUi.BubbleContext>();
        AppearanceUi.registerBubble(ID, LABEL, context -> {
            seen.add(context);
            return leftHalf(context);
        }, null);
        var feed = List.of(message("First text", true, "Self"), message(IMAGE, true, "Self"),
                message("[[CICode,name=broken.gif]]", true, "Self"), message("Other", false, "Player"));
        var view = new MessageListView(HOST, (url, visible) -> null);
        try (var target = Surface.makeRasterN32Premul(430, 430)) {
            draw(view, target, feed, new ScrollController());
            var builtIn = List.copyOf(view.hits());
            config.bubbleAppearanceId = ID;
            draw(view, target, feed, new ScrollController());
            assertEquals(builtIn, view.hits(), "existing row, bubble, and avatar geometry stays in place");
            assertEquals(List.of(AppearanceUi.BubbleGroup.FIRST, AppearanceUi.BubbleGroup.MIDDLE,
                            AppearanceUi.BubbleGroup.LAST, AppearanceUi.BubbleGroup.SINGLE),
                    seen.stream().map(AppearanceUi.BubbleContext::group).toList());
            for (int i = 0; i < seen.size(); i++) {
                var context = seen.get(i);
                var hit = builtIn.get(i);
                assertEquals(hit.message().isOwn(), context.own());
                assertEquals(hit.bubbleWidth(), context.bounds().width());
                assertEquals(hit.bubbleBottom() - hit.bubbleY(), context.bounds().height(), 0.001F);
            }
            assertEquals(List.of(config.ownBubbleColor, config.otherBubbleColor,
                            config.effectiveSecondaryCapsuleBg(), config.otherBubbleColor),
                    seen.stream().map(AppearanceUi.BubbleContext::fillColor).toList());
            assertEquals(config.bubbleTextColor, seen.get(0).textColor());
            assertEquals(config.textSecondaryColor, seen.get(1).textColor());
            assertEquals(0xFF55FF55, seen.get(2).textColor());
            assertEquals(config.otherBubbleTextColor, seen.get(3).textColor());
            assertTrue(view.textLinesForHit(view.hits().get(0)).stream()
                    .anyMatch(line -> line.text().contains("First text")));
            assertNull(view.imageHitAt(builtIn.get(1).bubbleX() + 5, builtIn.get(1).bubbleY() + 5));
            config.imageMessagesEnabled = false;
            seen.clear();
            draw(view, target, feed, new ScrollController());
            assertEquals(4, seen.size(), "the disabled-image capsule also uses the selected bubble");
        } finally {
            view.dispose();
        }
    }

    @Test void selectionWashFollowsTheCutWhileCopyAndDuplicateBadgeKeepTheirPositions() {
        AppearanceUi.registerBubble(ID, LABEL, MessageListAppearanceTest::leftHalf, null);
        String body = "Clip this selection";
        var feed = List.of(message(body, false, "Player").withDuplicateCount(3, 1000L));
        var view = new MessageListView(HOST, (url, visible) -> null);
        try (var target = Surface.makeRasterN32Premul(430, 430);
             var defaultFrame = new Bitmap();
             var customFrame = new Bitmap();
             var selectedFrame = new Bitmap()) {
            draw(view, target, feed, new ScrollController());
            read(target, defaultFrame);
            config.bubbleAppearanceId = ID;
            draw(view, target, feed, new ScrollController());
            read(target, customFrame);

            var hit = view.hits().get(0);
            var line = view.textLinesForHit(hit).get(0);
            float midY = line.y() + line.height() / 2;
            float lineRight = line.x() + RichTextRenderer.width(FontManager.font(UiTokens.FONT_BODY), line.richLine());
            view.beginSelection(hit, line, line.x());
            view.dragSelection(Math.nextDown(lineRight), midY);
            assertEquals(body, view.copySelection());
            draw(view, target, feed, new ScrollController());
            read(target, selectedFrame);

            int split = (int) (hit.bubbleX() + hit.bubbleWidth() / 2);
            int rightEdge = (int) Math.ceil(hit.bubbleX() + hit.bubbleWidth());
            boolean selectionInk = false;
            boolean badgeInk = false;
            for (int y = (int) Math.ceil(line.y()); y < line.y() + line.height(); y++) {
                for (int x = (int) Math.ceil(line.x()); x < split - 1; x++) {
                    selectionInk |= customFrame.getColor(x, y) != selectedFrame.getColor(x, y);
                }
                for (int x = split + 1; x < rightEdge; x++) {
                    assertEquals(customFrame.getColor(x, y), selectedFrame.getColor(x, y),
                            "selection backing must stop at the painted contour");
                }
                for (int x = rightEdge + 1; x < hit.x() + hit.maxWidth(); x++) {
                    int original = defaultFrame.getColor(x, y);
                    badgeInk |= original != 0;
                    assertEquals(original, selectedFrame.getColor(x, y), "duplicate badge stays beside the bubble");
                }
            }
            assertTrue(selectionInk);
            assertTrue(badgeInk);
        } finally {
            view.dispose();
        }
    }

    @Test void thePaintedMediaContourControlsPreviewAndMenuHitsUntilTheNextFrame() {
        var paths = new ArrayList<io.github.humbleui.skija.Path>();
        AppearanceUi.registerBubble(ID, LABEL, context -> {
            var path = leftHalf(context);
            paths.add(path);
            return path;
        }, null);
        config.bubbleAppearanceId = ID;
        try (var target = Surface.makeRasterN32Premul(430, 430);
             var source = Surface.makeRasterN32Premul(120, 80)) {
            source.getCanvas().clear(PHOTO);
            try (var image = source.makeImageSnapshot()) {
                var view = new MessageListView(HOST, (url, visible) -> new MessageImage(image, true));
                var feed = List.of(message(IMAGE, false, ""));
                try {
                    draw(view, target, feed, new ScrollController());
                    var hit = view.hits().get(0);
                    float y = (hit.bubbleY() + hit.bubbleBottom()) / 2;
                    float inside = hit.bubbleX() + hit.bubbleWidth() / 4;
                    float cut = hit.bubbleX() + hit.bubbleWidth() * 3 / 4;
                    assertEquals(0, pixel(target, cut, y));
                    assertEquals(PHOTO, pixel(target, inside, y));
                    assertSame(hit, view.imageHitAt(inside, y));
                    assertNull(view.imageHitAt(cut, y));
                    assertTrue(view.bubbleContains(hit, inside, y));
                    assertFalse(view.bubbleContains(hit, cut, y), "the cut cannot open the message menu");
                    assertEquals(1, paths.size(), "hit testing reuses the contour from the paint");
                    assertTrue(paths.get(0).isClosed(), "the host closes the callback's original path after paint");

                    var overflowing = new ArrayList<>(feed);
                    for (int i = 0; i < 15; i++) {
                        overflowing.add(new ChatMessage(RichText.literal("System " + i).toText(), false, true));
                    }
                    var scroll = new ScrollController();
                    scroll.setContent(1000, 400);
                    scroll.scrollTo(24, false);
                    draw(view, target, overflowing, scroll);
                    var scrolled = view.hits().get(0);
                    assertEquals(hit.bubbleY() - 24, scrolled.bubbleY(), 0.001F);
                    assertEquals(PHOTO, pixel(target, inside, y - 24));
                    assertSame(scrolled, view.imageHitAt(inside, y - 24));
                    assertNull(view.imageHitAt(cut, y - 24));
                    assertTrue(view.bubbleContains(scrolled, inside, y - 24));
                    assertFalse(view.bubbleContains(scrolled, cut, y - 24));

                    config.bubbleAppearanceId = null;
                    assertNull(view.imageHitAt(cut, y - 24), "the last painted contour governs until repaint");
                    draw(view, target, feed, new ScrollController());
                    assertEquals(PHOTO, pixel(target, cut, y));
                    assertNotNull(view.imageHitAt(cut, y));
                    assertTrue(view.bubbleContains(view.hits().get(0), cut, y), "built-in images use the original rectangle");
                } finally {
                    view.dispose();
                }
            }
        }
    }

    @Test void aFailedFillRestoresTheExactBuiltInNativePixels() {
        config.graphicsQuality = GraphicsQuality.ULTRA;
        AppearanceUi.registerBubble(ID, LABEL, new AppearanceUi.Bubble() {
            @Override public io.github.humbleui.skija.Path outline(AppearanceUi.BubbleContext context) {
                return leftHalf(context);
            }
            @Override public void draw(Canvas canvas, AppearanceUi.BubbleContext context, io.github.humbleui.skija.Path path) {
                canvas.clear(INK);
                throw new IllegalStateException("test fill failure");
            }
        }, null);
        var view = new MessageListView(HOST, (url, visible) -> null);
        var feed = List.of(message("A failed fill", true, "Self"), message(IMAGE, false, "Player"),
                message("[[CICode,name=missing.gif]]", false, "Player"));
        try (var target = Surface.makeRasterN32Premul(430, 430)) {
            draw(view, target, feed, new ScrollController());
            byte[] builtIn = pixels(target);
            var loading = view.hits().get(1);
            float x = loading.bubbleX() + loading.bubbleWidth() + 2;
            float y = (loading.bubbleY() + loading.bubbleBottom()) / 2;
            assertNotEquals(0, pixel(target, x, y), "the original loading plate casts a shadow");
            config.bubbleAppearanceId = ID;
            draw(view, target, feed, new ScrollController());
            assertArrayEquals(builtIn, pixels(target));
        } finally {
            view.dispose();
        }
    }

    @Test void anUnknownSenderGetsAFrameOverTheExistingPlaceholderAndKeepsItsSlot() {
        var seen = new ArrayList<AppearanceUi.AvatarContext>();
        AppearanceUi.registerAvatarFrame(ID, LABEL, (canvas, context) -> {
            seen.add(context);
            markFrame(canvas, context);
        }, null);
        var view = new MessageListView(HOST, (url, visible) -> null);
        try (var target = Surface.makeRasterN32Premul(430, 430)) {
            var feed = List.of(message("Unknown sender", false, ""));
            draw(view, target, feed, new ScrollController());
            var hit = view.hits().get(0);
            float cx = hit.avatarX() + hit.avatarSize() / 2;
            float cy = hit.avatarY() + hit.avatarSize() / 2;
            assertEquals(0xFF788291, pixel(target, cx, cy));
            config.avatarFrameAppearanceId = ID;
            draw(view, target, feed, new ScrollController());
            assertEquals(hit, view.hits().get(0));
            assertEquals(List.of(new AppearanceUi.Bounds(hit.avatarX(), hit.avatarY(), hit.avatarSize(), hit.avatarSize())),
                    seen.stream().map(AppearanceUi.AvatarContext::avatarBounds).toList());
            assertEquals(INK, pixel(target, cx, cy));
        } finally {
            view.dispose();
        }
    }

    @Test void publicRowFramesBothTheGlobeAndTheAllowedServerPhoto() throws Exception {
        var seen = new ArrayList<AppearanceUi.AvatarContext>();
        AppearanceUi.registerAvatarFrame(ID, LABEL, (canvas, context) -> {
            seen.add(context);
            markFrame(canvas, context);
        }, null);
        config.avatarFrameAppearanceId = ID;
        var page = new ConversationListPage(null);
        page.markSettled();
        var layout = UiLayout.ofRoot(10, 10, 520, 900);
        try (var target = Surface.makeRasterN32Premul(560, 960);
             var source = Surface.makeRasterN32Premul(96, 96)) {
            source.getCanvas().clear(PHOTO);
            try (var image = source.makeImageSnapshot(); var png = image.encodeToData()) {
                Path published = root.resolve("public-chat-avatar.png");
                PublicChatAvatar.save(published, "", png.getBytes());
                byte[] bytes = PublicChatAvatar.read(published);
                assertTrue(ServerPackStore.installPublicAvatar(ServerPack.sha256Hex(bytes), bytes));
                for (boolean permitted : new boolean[]{false, true}) {
                    config.serverPacksEnabled = permitted;
                    seen.clear();
                    target.getCanvas().clear(0);
                    page.render(target.getCanvas(), layout, -100, -100, 0);
                    assertEquals(1, seen.size());
                    var bounds = seen.get(0).avatarBounds();
                    assertEquals(INK, pixel(target, bounds.x() + bounds.width() / 2, bounds.y() + bounds.height() / 2));
                    assertEquals(permitted, pixel(target, bounds.x() + bounds.width() / 2, bounds.y() + 5) == PHOTO);
                }

                config.avatarFrameAppearanceId = null;
                target.getCanvas().clear(0);
                page.render(target.getCanvas(), layout, -100, -100, 0);
                byte[] builtIn = pixels(target);

                AppearanceUi.unregister(AppearanceUi.Part.AVATAR_FRAME, ID);
                AppearanceUi.registerAvatarFrame(ID, LABEL, (canvas, context) -> {
                    markFrame(canvas, context);
                    throw new IllegalStateException("test public frame failure");
                }, null);
                config.avatarFrameAppearanceId = ID;
                target.getCanvas().clear(0);
                page.render(target.getCanvas(), layout, -100, -100, 0);
                assertArrayEquals(builtIn, pixels(target));
            }
        } finally {
            ServerPackStore.clearPublicAvatar();
            PublicChatAvatarPaint.clear();
        }
    }

    private static ChatMessage message(String body, boolean own, String profileName) {
        return new ChatMessage(RichText.literal(body).toText(), own, false, null, null,
                SENDER, "Player", profileName, body, 1000L);
    }

    private static io.github.humbleui.skija.Path leftHalf(AppearanceUi.BubbleContext context) {
        var b = context.bounds();
        try (var builder = new PathBuilder()) {
            return builder.addRect(Rect.makeXYWH(b.x(), b.y(), b.width() / 2, b.height())).build();
        }
    }

    private static void markFrame(Canvas canvas, AppearanceUi.AvatarContext context) {
        var b = context.avatarBounds();
        try (var paint = new Paint().setColor(INK)) {
            canvas.drawCircle(b.x() + b.width() / 2, b.y() + b.height() / 2, 3, paint);
        }
    }

    private static void draw(MessageListView view, Surface target, List<ChatMessage> feed, ScrollController scroll) {
        target.getCanvas().clear(0);
        view.draw(target.getCanvas(), 10, 10, 400, 400, feed, scroll);
    }

    private static int pixel(Surface target, float x, float y) {
        try (var pixels = new Bitmap()) {
            pixels.allocN32Pixels(1, 1);
            assertTrue(target.readPixels(pixels, (int) x, (int) y));
            return pixels.getColor(0, 0);
        }
    }

    private static byte[] pixels(Surface target) {
        try (var bitmap = new Bitmap()) {
            read(target, bitmap);
            return bitmap.readPixels();
        }
    }

    private static void read(Surface target, Bitmap bitmap) {
        bitmap.allocN32Pixels(target.getWidth(), target.getHeight());
        assertTrue(target.readPixels(bitmap, 0, 0));
    }
}
