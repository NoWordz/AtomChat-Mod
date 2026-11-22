package com.atom.chat.page;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.ChatHudGifFixture;
import com.atom.chat.image.MessageImage;
import com.atom.chat.platform.Platform;
import com.atom.chat.render.PreviewLanguage;
import com.atom.chat.text.RichText;
import com.atom.chat.ui.ScrollController;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MessageListShadowTest {
    private static final int WIDTH = 610, HEIGHT = 850, X = 25, Y = 54;
    private static final int BACKDROP = 0xFFE4D6B5, OPAQUE = 0xFF613C8A;
    private static final int PICTURE = 0xFF2B7B9E;

    @Test void listShadowsFollowNativeShapesAndLeaveTransparentPictureInkAlone(@TempDir Path root) throws Exception {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
        AtomChatConfig config = AtomChatConfig.get();
        boolean oldMode = config.performanceMode;
        boolean oldMotion = config.animationEnabled;
        boolean oldImages = config.imageMessagesEnabled;
        boolean oldCompact = config.compactMessagesEnabled;
        int oldInterval = config.timestampIntervalMinutes;
        var clock = new AtomicLong(1000L);
        var gif = ChatHudGifFixture.mixedAlphaFrames(clock);
        try (var locale = PreviewLanguage.install("en_us");
             var attachment = Surface.makeRasterN32Premul(180, 110);
             var opaqueAttachment = Surface.makeRasterN32Premul(148, 72); var paint = new Paint();
             var output = Surface.makeRasterN32Premul(WIDTH * 2, HEIGHT);
             var standard = new Bitmap(); var fast = new Bitmap(); var nextGif = new Bitmap()) {
            config.animationEnabled = false;
            config.imageMessagesEnabled = true;
            config.compactMessagesEnabled = false;
            config.timestampIntervalMinutes = 1;
            attachment.getCanvas().clear(0);
            attachment.getCanvas().drawRect(Rect.makeXYWH(12, 16, 70, 76), paint.setColor(PICTURE));
            attachment.getCanvas().drawRect(Rect.makeXYWH(123, 16, 45, 76), paint.setColor(0xFFE3A05B));
            opaqueAttachment.getCanvas().clear(OPAQUE);
            opaqueAttachment.getCanvas().drawRect(Rect.makeXYWH(32, 16, 84, 42), paint.setColor(0xFFE3A05B));
            try (Image transparent = attachment.makeImageSnapshot(); Image opaque = opaqueAttachment.makeImageSnapshot()) {
                MessageImage transparentImage = new MessageImage(transparent, true);
                MessageImage opaqueImage = new MessageImage(opaque, false);
                var view = new MessageListView(new MessageListView.Host() {
                    public UUID ownUuid() { return null; }
                    public String ownName() { return "Local"; }
                    public String senderName(ChatMessage message) { return "Lin"; }
                    public long openStart() { return Long.MAX_VALUE; }
                }, (url, visible) -> {
                    if (url.endsWith(".gif")) return gif.apply("http://test/hud.gif", visible);
                    if (url.contains("loading")) return null;
                    return url.contains("opaque") ? opaqueImage : transparentImage;
                });
                var picture = message("Lin", "[[CICode,url=http://test/transparent.png,name=image,w=180,h=110]]", false, false);
                var solid = message("Lin", "[[CICode,url=http://test/opaque.png,name=image,w=148,h=72]]", false, false);
                var motion = message("Lin", "[[CICode,url=http://test/animated.gif,name=image,w=72,h=32]]", true, false);
                var rows = List.of(message("Lin", "Folded history", false, false),
                        message("Lin", "Panel shadow sample", false, true)
                                .withDuplicateCount(3, System.currentTimeMillis() - 120_000L),
                        new ChatMessage(RichText.literal("System capsule").toText(), false, true),
                        picture, solid,
                        message("Lumi", "[[CICode,url=http://test/loading.png,name=image,w=150,h=55]]", false, false),
                        motion, message("Lumi", "[[CICode,name=unavailable]]", true, false));
                view.setVisibleStart(1);
                try {
                    config.performanceMode = false;
                    Canvas canvas = output.getCanvas();
                    canvas.clear(BACKDROP);
                    draw(view, canvas, rows, fromTop());
                    assertTrue(output.readPixels(allocate(standard), 0, 0));
                    var hit = hitFor(view, picture);
                    var solidHit = hitFor(view, solid);
                    var gifHit = hitFor(view, motion);
                    float loadCenter = X + (WIDTH - 2F * X) / 2F;
                    assertTrue(view.loadEarlierHitAt(loadCenter, Y + UiTokens.s(12)));

                    config.performanceMode = true;
                    canvas.clear(BACKDROP);
                    draw(view, canvas, rows, fromTop());
                    assertEquals(hit, hitFor(view, picture));
                    assertTrue(view.loadEarlierHitAt(loadCenter, Y + UiTokens.s(12)));
                    assertTrue(output.readPixels(allocate(fast), 0, 0));
                    int centerX = Math.round(hit.bubbleX() + .55F * hit.bubbleWidth());
                    int centerY = Math.round((hit.bubbleY() + hit.bubbleBottom()) / 2F);
                    assertEquals(BACKDROP, standard.getColor(centerX, centerY));
                    assertEquals(BACKDROP, fast.getColor(centerX, centerY));
                    assertEquals(PICTURE, standard.getColor(Math.round(hit.bubbleX() + .3F * hit.bubbleWidth()), centerY));
                    int outerX = (int) Math.ceil(hit.bubbleX() + hit.bubbleWidth() + 1);
                    assertEquals(BACKDROP, fast.getColor(outerX, centerY));
                    assertEquals(BACKDROP, standard.getColor(outerX, centerY));
                    int solidOuter = (int) Math.ceil(solidHit.bubbleX() + solidHit.bubbleWidth() + 1);
                    int solidY = Math.round((solidHit.bubbleY() + solidHit.bubbleBottom()) / 2F);
                    assertEquals(BACKDROP, fast.getColor(solidOuter, solidY));
                    assertNotEquals(BACKDROP, standard.getColor(solidOuter, solidY));
                    assertNotEquals(standard.getColor((int) loadCenter, (int) (Y + UiTokens.s(30) + 2)),
                            fast.getColor((int) loadCenter, (int) (Y + UiTokens.s(30) + 2)));

                    clock.addAndGet(120L);
                    config.performanceMode = false;
                    canvas.clear(BACKDROP);
                    draw(view, canvas, rows, fromTop());
                    assertTrue(output.readPixels(allocate(nextGif), 0, 0));
                    int gifX = Math.round(gifHit.bubbleX() + .5F * gifHit.bubbleWidth());
                    int gifY = Math.round((gifHit.bubbleY() + gifHit.bubbleBottom()) / 2F);
                    assertNotEquals(fast.getColor(gifX, gifY), nextGif.getColor(gifX, gifY));
                    int gifOuter = (int) Math.ceil(gifHit.bubbleX() + gifHit.bubbleWidth() + 1);
                    assertEquals(BACKDROP, standard.getColor(gifOuter, gifY));
                    assertEquals(BACKDROP, nextGif.getColor(gifOuter, gifY));

                    if ("1".equals(System.getenv("ATOMCHAT_MESSAGE_SHADOW_PREVIEW"))) {
                        config.performanceMode = false;
                        clock.set(1120L);
                        for (int side = 0; side < 2; side++) {
                            canvas.save();
                            canvas.translate(side * WIDTH, 0);
                            for (int y = 0; y < HEIGHT; y += 36) {
                                for (int x = 0; x < WIDTH; x += 36) {
                                    boolean light = ((x + y) / 36 & 1) == 0;
                                    int fill = side == 0 ? (light ? 0xFFEBDBBE : 0xFFD4C0A1)
                                            : (light ? 0xFF343E4B : 0xFF293440);
                                    canvas.drawRect(Rect.makeXYWH(x, y, Math.min(36, WIDTH - x), Math.min(36, HEIGHT - y)),
                                            paint.setColor(fill));
                                }
                            }
                            draw(view, canvas, rows, fromTop());
                            canvas.restore();
                        }
                        String stage = System.getenv("ATOMCHAT_VISUAL_FOLLOWUP");
                        var path = stage == null ? Path.of(System.getProperty("atomchat.repoRoot", "../.."),
                                "build/v093/performance-mode/panel-preview.png") : Path.of(stage, "panel-preview.png");
                        Files.createDirectories(path.getParent());
                        try (var snapshot = output.makeImageSnapshot(); var png = snapshot.encodeToData()) {
                            Files.write(path, png.getBytes());
                        }
                        measureScroll(view, canvas, config);
                    }
                } finally {
                    view.dispose();
                }
            }
        } finally {
            config.performanceMode = oldMode;
            config.animationEnabled = oldMotion;
            config.imageMessagesEnabled = oldImages;
            config.compactMessagesEnabled = oldCompact;
            config.timestampIntervalMinutes = oldInterval;
        }
    }

    private static Bitmap allocate(Bitmap bitmap) {
        assertTrue(bitmap.allocN32Pixels(WIDTH, HEIGHT));
        return bitmap;
    }

    private static void draw(MessageListView view, Canvas canvas, List<ChatMessage> rows, ScrollController scroll) {
        view.draw(canvas, X, Y, WIDTH - 2 * X, HEIGHT - 2 * Y, rows, scroll);
    }

    private static ScrollController fromTop() {
        ScrollController scroll = new ScrollController(false);
        scroll.setContent(10_000F, HEIGHT);
        return scroll;
    }

    private static void measureScroll(MessageListView view, Canvas canvas, AtomChatConfig config) {
        var feed = IntStream.range(0, 220).mapToObj(i -> message("Player " + i,
                i % 6 == 0 ? "[[CICode,url=http://test/transparent.png,name=image,w=180,h=110]]"
                        : "Chat history " + i + " with representative wrapping",
                (i & 1) != 0, i % 10 == 0)).toList();
        view.setVisibleStart(0);
        for (boolean fast : new boolean[]{false, true}) {
            config.performanceMode = fast;
            ScrollController scroll = fromTop();
            draw(view, canvas, feed, scroll);
            int maxVisible = 0;
            long[] times = new long[180];
            for (int i = -60; i < times.length; i++) {
                canvas.clear(BACKDROP);
                scroll.scrollTo(scroll.getMaxScroll() * ((i + 60) % 90) / 90F, false);
                long start = System.nanoTime();
                draw(view, canvas, feed, scroll);
                if (i >= 0) times[i] = System.nanoTime() - start;
                maxVisible = Math.max(maxVisible, view.hits().size());
            }
            Arrays.sort(times);
            assertTrue(maxVisible < 25);
            System.out.printf(Locale.ROOT, "PANEL_SCROLL cpu mode=%s rows=%d visited=%d p50=%.4f p95=%.4f%n",
                    fast ? "fast" : "standard", feed.size(), maxVisible, times[90] / 1e6, times[171] / 1e6);
        }
    }

    private static MessageListView.MessageHit hitFor(MessageListView view, ChatMessage message) {
        return view.hits().stream().filter(hit -> hit.message() == message).findFirst().orElseThrow();
    }

    private static ChatMessage message(String name, String label, boolean own, boolean quote) {
        var sender = RichText.literal(name).mapStyles(style -> style.withColor(0xF3BBFF));
        var body = RichText.literal(label);
        return new ChatMessage(body.toText(), own, false, quote ? "Quoted player" : null,
                quote ? "A single line reference" : null, null, name, "", body.getString(), sender, body);
    }
}
