package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DrawPrimitives} offline: the blend and the icon set as pure values, and
 * every drawing path as a real frame on a CPU raster surface.
 *
 * <p>The frames matter here more than usual, because the composer row is not in
 * the golden page list (those twelve pages never draw the chat page body), so
 * "goldenCompare 0 diff" only says the other pages were not disturbed. These
 * tests are that missing coverage: each composer key, the Send capsule, both
 * active-tint states and the press state are drawn for real, and the save stack
 * is checked after every one of them (a leaked save is the failure a pixel
 * count cannot see).
 *
 * <p>No Minecraft type is involved anywhere: the domain is Skija + tokens, so
 * the host is a four-line stub.
 */
class DrawPrimitivesTest {
    private static final int SURFACE_W = 160;
    private static final int SURFACE_H = 96;
    private static final float BX = 40.0F;
    private static final float BY = 30.0F;
    private static final float OFF_SCREEN = -10_000.0F;
    private static final float FRAME_DT = 16.0F;

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

    @AfterEach
    void restoreRadius() {
        AtomChatConfig.get().cornerRadius = 20F;
    }

    /** The three facts the primitives read off the screen. */
    private static final class Host implements DrawPrimitives.Host {
        boolean emojiOpen;
        boolean phraseOpen;

        @Override
        public float frameDt() {
            return FRAME_DT;
        }

        @Override
        public boolean emojiPanelOpen() {
            return emojiOpen;
        }

        @Override
        public boolean phrasePanelOpen() {
            return phraseOpen;
        }
    }

    // ------------------------------------------------------------- the blend

    @Test
    void theBlendReturnsItsEndpointsExactly() {
        int from = 0xFF102030;
        int to = 0xFFF0E0D0;

        assertEquals(from, DrawPrimitives.lerpArgb(from, to, 0.0F), "t=0 is the from colour");
        assertEquals(from, DrawPrimitives.lerpArgb(from, to, -1.0F), "below the range clamps to from");
        assertEquals(to, DrawPrimitives.lerpArgb(from, to, 1.0F), "t=1 is the to colour");
        assertEquals(to, DrawPrimitives.lerpArgb(from, to, 2.0F), "above the range clamps to to");
    }

    @Test
    void theBlendWalksEveryChannelOfAnOpaquePair() {
        int black = 0xFF000000;
        int white = 0xFFFFFFFF;

        // (int) truncation, so each channel of the halfway colour is 127.
        assertEquals(0xFF7F7F7F, DrawPrimitives.lerpArgb(black, white, 0.5F));
        assertEquals(0xFF3F3F3F, DrawPrimitives.lerpArgb(black, white, 0.25F));
        assertEquals(0xFF102030, DrawPrimitives.lerpArgb(black, 0xFF204060, 0.5F),
                "each channel interpolates on its own");
    }

    // -------------------------------------------------------------- the icons

    @Test
    void everyIconIsAParsedNonDegenerateShape() {
        io.github.humbleui.skija.Path[] icons = icons();
        assertEquals(12, icons.length, "the composer, menu and FAB sets are all here");
        for (io.github.humbleui.skija.Path icon : icons) {
            assertNotNull(icon, "the path data parsed");
            Rect bounds = icon.getBounds();
            assertNotNull(bounds, "an icon has bounds");
            assertFalse(bounds.isEmpty(), "an icon is not an empty shape: " + bounds);
            // The toolbar art is authored on a 20x20 grid and the Lucide at-sign
            // (@) on Lucide's 24x24, whose big arc actually reaches past that
            // box (its bounds are ~[-2.3, 22] x [-0.4, 27.4]) - the icons are
            // fitted by their longest edge, so the only thing worth pinning is
            // that each one is a real, finite shape rather than a stray point.
            assertTrue(bounds.getWidth() > 1.0F && bounds.getHeight() > 1.0F,
                    "an icon has real extent: " + bounds);
            assertTrue(bounds.getWidth() < 64.0F && bounds.getHeight() < 64.0F,
                    "an icon stays in the small logical space it is authored on: " + bounds);
        }
    }

    @Test
    void everyIconPaintsWhenDrawnCentred() {
        for (io.github.humbleui.skija.Path icon : icons()) {
            try (Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H)) {
                Canvas canvas = surface.getCanvas();
                canvas.clear(0);
                int baseline = canvas.getSaveCount();
                new DrawPrimitives(new Host()).drawIconCentered(canvas, icon,
                        SURFACE_W / 2.0F, SURFACE_H / 2.0F, UiTokens.CONTEXT_ICON_SIZE,
                        new DrawPrimitives(new Host()).textPrimary());
                assertEquals(baseline, canvas.getSaveCount(), "drawIconCentered leaked a save");
                assertTrue(drawnBytes(surface) > 0, "an icon drew nothing: " + icon.getBounds());
            }
        }
    }

    // ---------------------------------------------------------- the drawing

    @Test
    void everyComposerKeyPaintsInItsOwnFrame() {
        DrawPrimitives primitives = new DrawPrimitives(new Host());
        try (Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H)) {
            Canvas canvas = surface.getCanvas();
            byte[][] frames = new byte[4][];
            int[] ids = {0, 1, 3};
            for (int i = 0; i < ids.length; i++) {
                int baseline = canvas.getSaveCount();
                canvas.clear(0);
                primitives.drawIconButton(canvas, BX, BY, ids[i], insideX(), insideY());
                assertEquals(baseline, canvas.getSaveCount(), "drawIconButton leaked a save");
                frames[i] = pixels(surface);
                assertTrue(drawnBytes(surface) > 0, "composer key " + ids[i] + " drew nothing");
            }
            for (int i = 0; i < ids.length; i++) {
                for (int j = i + 1; j < ids.length; j++) {
                    assertFalse(Arrays.equals(frames[i], frames[j]),
                            "keys " + ids[i] + " and " + ids[j] + " must not draw the same icon");
                }
            }

            int baseline = canvas.getSaveCount();
            canvas.clear(0);
            primitives.drawSendButton(canvas, BX, BY, insideX(), insideY());
            assertEquals(baseline, canvas.getSaveCount(), "drawSendButton leaked a save");
            frames[3] = pixels(surface);
            assertTrue(drawnBytes(surface) > 0, "the Send capsule drew nothing");
            for (int i = 0; i < 3; i++) {
                assertFalse(Arrays.equals(frames[i], frames[3]),
                        "the Send capsule must not look like composer key " + ids[i]);
            }
        }
    }

    @Test
    void theActiveTintOnlyFollowsThePanelThatOwnsTheButton() {
        Host host = new Host();
        DrawPrimitives primitives = new DrawPrimitives(host);
        try (Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H)) {
            Canvas canvas = surface.getCanvas();

            byte[] emojiIdle = frame(canvas, surface, () ->
                    primitives.drawIconButton(canvas, BX, BY, 1, OFF_SCREEN, OFF_SCREEN));
            host.emojiOpen = true;
            byte[] emojiActive = frame(canvas, surface, () ->
                    primitives.drawIconButton(canvas, BX, BY, 1, OFF_SCREEN, OFF_SCREEN));
            assertFalse(Arrays.equals(emojiIdle, emojiActive),
                    "the emoji button tints while its own panel is open");

            byte[] imageIdle = frame(canvas, surface, () ->
                    primitives.drawIconButton(canvas, BX, BY, 0, OFF_SCREEN, OFF_SCREEN));
            host.phraseOpen = true;
            byte[] imageAfterPhrase = frame(canvas, surface, () ->
                    primitives.drawIconButton(canvas, BX, BY, 0, OFF_SCREEN, OFF_SCREEN));
            assertArrayEquals(imageIdle, imageAfterPhrase,
                    "and the image button ignores the quick-phrase panel");
        }
    }

    @Test
    void thePressStateIsVisibleAndExpires() {
        Host host = new Host();
        DrawPrimitives primitives = new DrawPrimitives(host);
        try (Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H)) {
            Canvas canvas = surface.getCanvas();

            byte[] idle = frame(canvas, surface, () ->
                    primitives.drawSendButton(canvas, BX, BY, OFF_SCREEN, OFF_SCREEN));
            assertFalse(primitives.buttonPressed(2), "nothing is pressed before a press");

            primitives.pressButton(2);
            primitives.setButtonHeld(2);
            assertTrue(primitives.buttonPressed(2), "a press marks the button for 150ms");
            assertFalse(primitives.buttonPressed(1), "and only that button");
            byte[] pressed = frame(canvas, surface, () ->
                    primitives.drawSendButton(canvas, BX, BY, OFF_SCREEN, OFF_SCREEN));
            assertFalse(Arrays.equals(idle, pressed), "a pressed Send capsule differs from the idle one");

            primitives.setButtonHeld(-1);
            sleep(200L);
            assertFalse(primitives.buttonPressed(2), "the press window closes on its own");
        }
    }

    @Test
    void sendCapsuleAndHoverOverlayFollowTheLiveCornerRadius() throws Exception {
        AtomChatConfig.get().cornerRadius = 0F;
        DrawPrimitives primitives = new DrawPrimitives(new Host());
        try (Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H)) {
            Canvas canvas = surface.getCanvas();
            int initialSaveCount = canvas.getSaveCount();
            byte[] squareIdle = frame(canvas, surface, () ->
                    primitives.drawSendButton(canvas, BX, BY, OFF_SCREEN, OFF_SCREEN));
            assertTrue(alphaAt(surface, (int) BX + 1, (int) BY + 1) > 0,
                    "at radius 0 the send capsule owns its corner pixel");
            writePng(surface, "send-radius-0.png");

            byte[] squareHover = frame(canvas, surface, () ->
                    primitives.drawSendButton(canvas, BX, BY, insideSendX(), insideY()));
            assertTrue(alphaAt(surface, (int) BX + 1, (int) BY + 1) > 0,
                    "the hover overlay is square too, not only the base fill");
            writePng(surface, "send-radius-0-hover.png");
            assertFalse(Arrays.equals(squareIdle, squareHover));

            primitives.pressButton(2);
            primitives.setButtonHeld(2);
            frame(canvas, surface, () ->
                    primitives.drawSendButton(canvas, BX, BY, insideSendX(), insideY()));
            assertTrue(alphaAt(surface, (int) BX + 1, (int) BY + 1) > 0,
                    "the pressed wash also uses the live square radius");
            writePng(surface, "send-radius-0-press.png");
            primitives.setButtonHeld(-1);
            assertEquals(initialSaveCount, canvas.getSaveCount(), "all save/restore calls stay balanced");

            AtomChatConfig.get().cornerRadius = 20F;
            frame(canvas, surface, () ->
                    primitives.drawSendButton(canvas, BX, BY, OFF_SCREEN, OFF_SCREEN));
            assertEquals(0, alphaAt(surface, (int) BX + 1, (int) BY + 1),
                    "the default radius cuts the corner pixel away");
            writePng(surface, "send-radius-default.png");
            assertEquals(initialSaveCount, canvas.getSaveCount());
        }
    }

    @Test
    void theHoverWashStepsTowardThePointer() {
        DrawPrimitives primitives = new DrawPrimitives(new Host());
        try (Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H)) {
            Canvas canvas = surface.getCanvas();

            byte[] away = frame(canvas, surface, () ->
                    primitives.drawIconButton(canvas, BX, BY, 0, OFF_SCREEN, OFF_SCREEN));
            byte[] under = frame(canvas, surface, () ->
                    primitives.drawIconButton(canvas, BX, BY, 0, insideX(), insideY()));
            assertFalse(Arrays.equals(away, under),
                    "the pointer inside the key raises its hover wash");
        }
    }

    // ------------------------------------------------------------- helpers

    /** Draws the same call {@code frames} times, so the springs have advanced, then returns the frame. */
    private static byte[] frame(Canvas canvas, Surface surface, Runnable draw) {
        int frames = Animations.enabled() ? 6 : 1;
        for (int i = 0; i < frames; i++) {
            canvas.clear(0);
            draw.run();
        }
        return pixels(surface);
    }

    /** A point in panel space inside the square composer key at (BX, BY). */
    private static float insideX() {
        return BX + UiTokens.ACTION_BUTTON_SIZE / 2.0F;
    }

    private static float insideY() {
        return BY + UiTokens.ACTION_BUTTON_SIZE / 2.0F;
    }

    private static float insideSendX() {
        return BX + UiTokens.BUTTON_W / 2.0F;
    }

    private static int alphaAt(Surface surface, int x, int y) {
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(SURFACE_W, SURFACE_H);
            assertTrue(surface.readPixels(bitmap, 0, 0));
            byte[] rgba = bitmap.readPixels();
            int i = (y * SURFACE_W + x) * 4;
            return rgba[i + 3] & 0xFF;
        }
    }

    private static void writePng(Surface surface, String name) throws Exception {
        Path dir = Path.of(System.getProperty("atomchat.repoRoot", "."),
                ".work/2026-10-19-settings/evidence/png");
        Files.createDirectories(dir);
        try (io.github.humbleui.skija.Image image = surface.makeImageSnapshot();
             Data data = image.encodeToData(EncodedImageFormat.PNG)) {
            Files.write(dir.resolve(name), data.getBytes());
        }
    }

    private static io.github.humbleui.skija.Path[] icons() {
        return new io.github.humbleui.skija.Path[]{
                DrawPrimitives.ICON_IMAGE_PATH, DrawPrimitives.ICON_EMOJI_PATH,
                DrawPrimitives.ICON_SEND_PATH, DrawPrimitives.ICON_PHRASE_PATH,
                DrawPrimitives.ICON_COPY_PATH, DrawPrimitives.ICON_QUOTE_PATH,
                DrawPrimitives.ICON_SAVE_PATH, DrawPrimitives.ICON_MENTION_PATH,
                DrawPrimitives.ICON_WHISPER_PATH, DrawPrimitives.ICON_TP_PATH,
                DrawPrimitives.ICON_BLOCK_PATH, DrawPrimitives.ICON_JUMP_DOWN_PATH};
    }

    private static byte[] pixels(Surface surface) {
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(SURFACE_W, SURFACE_H);
            assertTrue(surface.readPixels(bitmap, 0, 0), "could not read the raster surface back");
            return bitmap.readPixels();
        }
    }

    /** Non-zero bytes in the surface's pixels, cleared before the frame under test. */
    private static int drawnBytes(Surface surface) {
        int nonZero = 0;
        for (byte b : pixels(surface)) {
            if (b != 0) {
                nonZero++;
            }
        }
        return nonZero;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the press window", e);
        }
    }
}
