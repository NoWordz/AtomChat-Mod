package com.atom.chat.render;

import com.atom.chat.api.ui.AppearanceUi;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.config.GraphicsQuality;
import com.atom.chat.image.AvatarRenderer;
import com.atom.chat.image.MessageImage;
import com.atom.chat.image.SkinResolver;
import com.atom.chat.platform.Platform;
import com.atom.chat.text.RichText;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.atom.chat.api.ui.AppearanceUi.BubbleGroup.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatHudAppearanceTest {
    private static final String BUBBLE = "test:hud_bubble";
    private static final String FRAME = "test:hud_frame";
    private static final String PANEL_ONLY = "test:hud_panel_only";
    private static final int OWN_FILL = 0xFF743218;
    private static final int OTHER_FILL = 0xFF246850;
    private static final int OWN_TEXT = 0xFFF9DEA1;
    private static final int OTHER_TEXT = 0xFFC9FACE;
    private static final int IMAGE_INK = 0xFFD9339C;
    private static final int FRAME_INK = 0xFF4900DF;
    private static final int FACE_INK = 0xFFE81C24;
    private static final int HAT_INK = 0xFF1BAD19;
    private static final int PLACEHOLDER = 0xFF788291;
    private static final ChatHudRenderer.Viewport VIEW = new ChatHudRenderer.Viewport(8, 220, 290, 200, 1, 1);

    private final AtomChatConfig ink = new AtomChatConfig();

    @BeforeAll static void setup(@TempDir java.nio.file.Path root) {
        Platform.install(new Platform.Provider() {
            public java.nio.file.Path configDir() { return root.resolve("config"); }
            public java.nio.file.Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @BeforeEach void setUpInk() {
        ink.graphicsQuality = GraphicsQuality.PERFORMANCE;
        ink.ownBubbleColor = OWN_FILL;
        ink.otherBubbleColor = OTHER_FILL;
        ink.bubbleTextColor = OWN_TEXT;
        ink.otherBubbleTextColor = OTHER_TEXT;
        ink.compactMessagesEnabled = true;
        ink.timestampIntervalMinutes = 0;
    }

    @AfterEach void unregisterAppearances() {
        AppearanceUi.unregister(AppearanceUi.Part.BUBBLE, BUBBLE);
        AppearanceUi.unregister(AppearanceUi.Part.BUBBLE, PANEL_ONLY);
        AppearanceUi.unregister(AppearanceUi.Part.AVATAR_FRAME, FRAME);
        AppearanceUi.unregister(AppearanceUi.Part.AVATAR_FRAME, PANEL_ONLY);
    }

    @Test void textBakesAtLiveBoundsWithGroupOwnershipAndThemeContext() {
        var contexts = new ArrayList<AppearanceUi.BubbleContext>();
        AppearanceUi.registerBubble(BUBBLE, "test.bubble", null, context -> {
            contexts.add(context);
            return leftHalf(context);
        });
        ink.bubbleAppearanceId = BUBBLE;
        var rows = List.of(player(false, "Incoming", "", 4),
                player(true, "Last", "Local", 3), player(true, "Middle", "Local", 2),
                player(true, "First", "Local", 1));
        var hud = new ChatHudRenderer();
        var live = new ChatHudRenderer();
        var viewport = new ChatHudRenderer.Viewport(8, 430, 290, 410, 1, 1);
        // A different density makes the same unscaled canvas ineligible for aligned raster baking.
        var direct = new ChatHudRenderer.Viewport(8, 430, 290, 410, 2, 1);
        try (var output = Surface.makeRasterN32Premul(340, 450); var pixels = new Bitmap()) {
            assertTrue(pixels.allocN32Pixels(340, 450));
            paint(live, output, rows, direct, null, 1);
            var liveContexts = List.copyOf(contexts);
            assertEquals(List.of(FIRST, MIDDLE, LAST, SINGLE),
                    liveContexts.stream().map(AppearanceUi.BubbleContext::group).toList());
            assertTrue(output.readPixels(pixels, 0, 0));
            assertEquals(OWN_FILL, insetPixel(pixels, liveContexts.get(0).bounds()));
            assertEquals(OTHER_FILL, insetPixel(pixels, liveContexts.get(3).bounds()));
            assertEquals(List.of(true, true, true, false),
                    liveContexts.stream().map(AppearanceUi.BubbleContext::own).toList());
            assertEquals(List.of(OWN_FILL, OWN_FILL, OWN_FILL, OTHER_FILL),
                    liveContexts.stream().map(AppearanceUi.BubbleContext::fillColor).toList());
            assertEquals(List.of(OWN_TEXT, OWN_TEXT, OWN_TEXT, OTHER_TEXT),
                    liveContexts.stream().map(AppearanceUi.BubbleContext::textColor).toList());

            contexts.clear();
            paint(hud, output, rows, viewport, Surface::makeRasterN32Premul, 1);
            assertEquals(liveContexts, contexts);
            assertTrue(output.readPixels(pixels, 0, 0));
            assertEquals(OWN_FILL, insetPixel(pixels, contexts.get(0).bounds()));
            assertEquals(OTHER_FILL, insetPixel(pixels, contexts.get(3).bounds()));
            byte[] settled = pixels.readPixels();
            contexts.clear();
            paint(hud, output, rows, viewport, Surface::makeRasterN32Premul, 1);
            assertTrue(contexts.isEmpty(), "stable text uses the baked outline");
            assertTrue(output.readPixels(pixels, 0, 0));
            assertArrayEquals(settled, pixels.readPixels());

            var moved = new ChatHudRenderer.Viewport(viewport.x() + 5, viewport.bottom(),
                    viewport.width(), viewport.height(), viewport.density(), viewport.guiPerUnit());
            paint(hud, output, rows, moved, Surface::makeRasterN32Premul, 1);
            assertEquals(liveContexts.get(0).bounds().x() + 5, contexts.get(0).bounds().x());
            contexts.clear();
            ink.ownBubbleColor = 0xFF1763A0;
            paint(hud, output, rows, moved, Surface::makeRasterN32Premul, 1);
            assertEquals(0xFF1763A0, contexts.get(0).fillColor());
            contexts.clear();
            paint(hud, output, rows, moved, Surface::makeRasterN32Premul, 2);
            assertEquals(4, contexts.size(), "a replacement ink surface generation rebakes");

            var canvas = output.getCanvas();
            int save = canvas.save();
            try {
                contexts.clear();
                canvas.scale(.75F, .75F);
                var scaled = new ChatHudRenderer.Viewport(moved.x(), moved.bottom(),
                        moved.width(), moved.height(), .75F, moved.guiPerUnit());
                paint(hud, output, rows, scaled, Surface::makeRasterN32Premul, 2);
                assertEquals(4, contexts.size(), "a new density rebakes at the new pixel scale");
            } finally {
                canvas.restoreToCount(save);
            }
        } finally {
            live.clear();
            hud.clear();
        }
    }

    @Test void pictureClipsToHudOutlineAndAvatarFrameOverlaysMissingFace() {
        ink.bubbleAppearanceId = BUBBLE;
        ink.avatarFrameAppearanceId = FRAME;
        var contexts = new ArrayList<AppearanceUi.BubbleContext>();
        var avatars = new ArrayList<AppearanceUi.AvatarContext>();
        AppearanceUi.registerBubble(BUBBLE, "test.bubble", null, context -> {
            contexts.add(context);
            return leftHalf(context);
        });
        registerFrame(avatars);
        var attachment = new AtomicReference<MessageImage>();
        var hud = new ChatHudRenderer((url, visible) -> attachment.get());
        var rows = List.of(player(false, "[[CICode,url=https://test/asset.png,w=120,h=60]]", "", 1));
        try (var output = Surface.makeRasterN32Premul(340, 240); var pixels = new Bitmap();
             var source = Surface.makeRasterN32Premul(120, 60)) {
            assertTrue(pixels.allocN32Pixels(340, 240));
            paint(hud, output, rows, VIEW, Surface::makeRasterN32Premul, 1);
            assertEquals(1, contexts.size());
            assertEquals(ink.effectiveSecondaryCapsuleBg(), contexts.get(0).fillColor());
            assertEquals(ink.secondaryCapsuleText, contexts.get(0).textColor());

            source.getCanvas().clear(IMAGE_INK);
            try (var image = source.makeImageSnapshot()) {
                contexts.clear();
                attachment.set(new MessageImage(image, false));
                paint(hud, output, rows, VIEW, Surface::makeRasterN32Premul, 1);
                assertTrue(output.readPixels(pixels, 0, 0));
                assertEquals(1, contexts.size(), "only the visible media uses an outline");
                var media = contexts.get(0);
                assertEquals(SINGLE, media.group());
                assertEquals(OTHER_FILL, media.fillColor());
                assertEquals(OTHER_TEXT, media.textColor());
                assertEquals(IMAGE_INK, at(pixels, media.bounds(), .25F, .5F));
                assertEquals(0, at(pixels, media.bounds(), .75F, .5F));
                assertEquals(2, avatars.size());
                var face = avatars.get(1).avatarBounds();
                assertFrameMark(pixels, face);
                assertEquals(PLACEHOLDER, at(pixels, face, .5F, .5F));
            }
        } finally {
            hud.clear();
        }
    }

    @Test void addonFramePreservesTheHostFaceAndE33Hat() throws ReflectiveOperationException {
        ink.avatarFrameAppearanceId = FRAME;
        var avatars = new ArrayList<AppearanceUi.AvatarContext>();
        registerFrame(avatars);
        var hud = new ChatHudRenderer();
        var rows = List.of(player(false, "Known face", "HudFrameFixture", 1));
        var skin = SkinResolver.getSkin(null, "HudFrameFixture");
        var faces = avatarCache("FACE_CACHE");
        var skinFaces = avatarCache("SKIN_FACE_CACHE");
        var hats = avatarCache("HAT_CACHE");
        var config = AtomChatConfig.get();
        var originalStyle = config.avatarStyle;
        try (var output = Surface.makeRasterN32Premul(340, 240); var pixels = new Bitmap();
             var skinSurface = Surface.makeRasterN32Premul(8, 8); var paint = new Paint().setColor(HAT_INK)) {
            assertTrue(pixels.allocN32Pixels(340, 240));
            skinSurface.getCanvas().clear(FACE_INK);
            try (var face = skinSurface.makeImageSnapshot()) {
                skinSurface.getCanvas().clear(0);
                skinSurface.getCanvas().drawRect(Rect.makeXYWH(0, 0, 1, 8), paint);
                try (var hat = skinSurface.makeImageSnapshot()) {
                    var savedFace = faces.put(skin, face);
                    var savedSkinFace = skinFaces.put(skin, face);
                    var savedHat = hats.put(skin, hat);
                    try {
                        config.avatarStyle = AtomChatConfig.AvatarStyle.ATOMCHAT;
                        frame(hud, output, pixels, rows);
                        assertEquals(1, avatars.size());
                        var bounds = avatars.get(0).avatarBounds();
                        assertFrameMark(pixels, bounds);
                        assertEquals(FACE_INK, at(pixels, bounds, .5F, .5F));

                        config.avatarStyle = AtomChatConfig.AvatarStyle.E33CHAT;
                        frame(hud, output, pixels, rows);
                        assertEquals(2, avatars.size());
                        bounds = avatars.get(1).avatarBounds();
                        assertFrameMark(pixels, bounds);
                        assertEquals(FACE_INK, at(pixels, bounds, .5F, .5F));
                        assertEquals(HAT_INK, pixels.getColor((int) bounds.x() - 1, (int) (bounds.y() + bounds.height() / 2)));
                    } finally {
                        restoreImage(faces, skin, savedFace);
                        restoreImage(skinFaces, skin, savedSkinFace);
                        restoreImage(hats, skin, savedHat);
                    }
                }
            }
        } finally {
            config.avatarStyle = originalStyle;
            hud.clear();
        }
    }

    @Test void registryAndSelectionChangesReplaceSettledInkWithNativeFallback() {
        ink.graphicsQuality = GraphicsQuality.ULTRA;
        ink.bubbleStyle = AtomChatConfig.BubbleStyle.CORNER;
        ink.bubbleAppearanceId = BUBBLE;
        ink.avatarFrameAppearanceId = FRAME;
        var rows = List.of(player(true, "Own bubble corner stays on the HUD side", "", 1));
        var hud = new ChatHudRenderer();
        try (var output = Surface.makeRasterN32Premul(340, 240); var pixels = new Bitmap()) {
            assertTrue(pixels.allocN32Pixels(340, 240));
            var missing = frame(hud, output, pixels, rows);
            registerFrame(new ArrayList<>());
            assertFalse(Arrays.equals(missing, frame(hud, output, pixels, rows)));
            AppearanceUi.unregister(AppearanceUi.Part.AVATAR_FRAME, FRAME);
            assertArrayEquals(missing, frame(hud, output, pixels, rows));

            AppearanceUi.registerBubble(BUBBLE, "test.bubble", null, ChatHudAppearanceTest::leftHalf);
            assertFalse(Arrays.equals(missing, frame(hud, output, pixels, rows)));

            AppearanceUi.registerBubble(PANEL_ONLY, "test.panel.bubble",
                    context -> fail("The panel bubble should never draw on the HUD"), null);
            AppearanceUi.registerAvatarFrame(PANEL_ONLY, "test.panel.frame",
                    (canvas, context) -> fail("The panel frame should never draw on the HUD"), null);
            ink.bubbleAppearanceId = PANEL_ONLY;
            ink.avatarFrameAppearanceId = PANEL_ONLY;
            assertArrayEquals(missing, frame(hud, output, pixels, rows));

            ink.bubbleAppearanceId = BUBBLE;
            ink.avatarFrameAppearanceId = FRAME;
            assertFalse(Arrays.equals(missing, frame(hud, output, pixels, rows)));
            AppearanceUi.unregister(AppearanceUi.Part.BUBBLE, BUBBLE);
            assertArrayEquals(missing, frame(hud, output, pixels, rows));

            AppearanceUi.registerBubble(BUBBLE, "test.bubble", null, new AppearanceUi.Bubble() {
                public Path outline(AppearanceUi.BubbleContext context) { return leftHalf(context); }
                public void draw(Canvas canvas, AppearanceUi.BubbleContext context, Path outline) {
                    throw new IllegalStateException("broken extension");
                }
            });
            assertArrayEquals(missing, frame(hud, output, pixels, rows),
                    "failed custom background also falls back to the native shadow");
        } finally {
            hud.clear();
        }
    }

    private byte[] frame(ChatHudRenderer hud, Surface output, Bitmap pixels, List<ChatMessage> rows) {
        paint(hud, output, rows, VIEW, Surface::makeRasterN32Premul, 1);
        assertTrue(output.readPixels(pixels, 0, 0));
        return pixels.readPixels();
    }

    private void paint(ChatHudRenderer hud, Surface output, List<ChatMessage> rows, ChatHudRenderer.Viewport viewport,
                       ChatHudRasterCache.SurfaceFactory factory, long generation) {
        output.getCanvas().clear(0);
        hud.paint(output.getCanvas(), rows, viewport, null, "Local", "Player", ink, rows.size(),
                255, 1F, false, 0L, factory, generation);
    }

    private static Path leftHalf(AppearanceUi.BubbleContext context) {
        var b = context.bounds();
        try (var path = new PathBuilder()) {
            return path.addRect(Rect.makeXYWH(b.x(), b.y(), b.width() / 2, b.height())).detach();
        }
    }

    private static void registerFrame(List<AppearanceUi.AvatarContext> avatars) {
        AppearanceUi.registerAvatarFrame(FRAME, "test.frame", null, (canvas, context) -> {
            avatars.add(context);
            var b = context.avatarBounds();
            try (var paint = new Paint().setColor(FRAME_INK)) {
                canvas.drawRect(Rect.makeXYWH(b.x() + 2, b.y() + b.height() / 2 - 2, 4, 4), paint);
            }
        });
    }

    private static void assertFrameMark(Bitmap pixels, AppearanceUi.Bounds bounds) {
        assertEquals(FRAME_INK, pixels.getColor((int) bounds.x() + 3, (int) (bounds.y() + bounds.height() / 2)));
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Image> avatarCache(String name) throws ReflectiveOperationException {
        var field = AvatarRenderer.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<Object, Image>) field.get(null);
    }

    private static void restoreImage(Map<Object, Image> cache, Object skin, Image previous) {
        if (previous == null) cache.remove(skin);
        else cache.put(skin, previous);
    }

    private static int insetPixel(Bitmap pixels, AppearanceUi.Bounds b) {
        return pixels.getColor((int) b.x() + 1, (int) b.y() + 1);
    }

    private static int at(Bitmap pixels, AppearanceUi.Bounds b, float x, float y) {
        return pixels.getColor((int) (b.x() + x * b.width()), (int) (b.y() + y * b.height()));
    }

    private static ChatMessage player(boolean own, String value, String profile, long time) {
        var body = RichText.literal(value);
        return new ChatMessage(body.toText(), own, false, null, null, null, "Sender", profile, value,
                RichText.literal("Sender"), body, time);
    }
}
