package com.atom.chat.image;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.page.MessageListView;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real raster coverage: E33Chat's default 20px face has a 22px hat. */
class PlayerAvatarHatTest {
    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final UUID SUBJECT = UUID.fromString("33e00000-0000-0000-0000-000000000016");
    private static final String NAME = "HatFixture";
    @TempDir static Path runtime;

    @BeforeAll static void installPlatform() {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return runtime.resolve("config"); }
            public Path gameDir() { return runtime.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @Test void ordinaryHatScalesWithFortyPixelFace() throws Exception {
        // Catches copying E33Chat's absolute 1px outset onto a doubled face.
        assertOrdinaryExtent(40, 18, 61);
    }

    @Test void ordinaryHatScalesWithEightyPixelFace() throws Exception {
        // Catches an outset tied to one UI token instead of the supplied size.
        assertOrdinaryExtent(80, 16, 103);
    }

    private static void assertOrdinaryExtent(float size, int left, int right) throws Exception {
        try (Fixture ignored = new Fixture(); Surface surface = Surface.makeRasterN32Premul(140, 140)) {
            surface.getCanvas().clear(0);
            int saves = surface.getCanvas().getSaveCount();
            assertTrue(PlayerAvatar.drawE33Chat(surface.getCanvas(), SUBJECT, NAME, 20, 20, size));
            int middle = 20 + (int) size / 2;
            assertEquals(GREEN, pixel(surface, left, middle), "hat must reach the reference left edge");
            assertEquals(GREEN, pixel(surface, right, middle), "hat must reach the reference right edge");
            assertEquals(0, pixel(surface, left - 1, middle), "hat must not expand beyond the reference");
            assertEquals(0, pixel(surface, right + 1, middle), "hat must stay centered");
            assertEquals(RED, pixel(surface, middle, middle), "transparent hat center must reveal the face");
            assertEquals(saves, surface.getCanvas().getSaveCount());
        }
    }

    @Test void explicitProfileOutsetKeepsItsSeparateGeometry() throws Exception {
        try (Fixture ignored = new Fixture(); Surface surface = Surface.makeRasterN32Premul(100, 100)) {
            surface.getCanvas().clear(0);
            assertTrue(PlayerAvatar.drawE33Chat(surface.getCanvas(), SUBJECT, NAME, 20, 20, 40, 3));
            assertEquals(GREEN, pixel(surface, 17, 40));
            assertEquals(GREEN, pixel(surface, 62, 40));
            assertEquals(0, pixel(surface, 16, 40));
            assertEquals(0, pixel(surface, 63, 40));
            assertEquals(RED, pixel(surface, 40, 40));
        }
    }

    @Test void nativeStyleLeavesTheCallersDrawPathUntouched() throws Exception {
        try (Fixture ignored = new Fixture(); Surface surface = Surface.makeRasterN32Premul(100, 100)) {
            surface.getCanvas().clear(0);
            AtomChatConfig.get().avatarStyle = AtomChatConfig.AvatarStyle.ATOMCHAT;
            assertFalse(PlayerAvatar.drawE33Chat(surface.getCanvas(), SUBJECT, NAME, 20, 20, 40));
            assertEquals(0, pixel(surface, 40, 40));
        }
    }

    @Test void messageListReservesTheWholeHatButRetainsNativeRimSpacing() throws Exception {
        try (Fixture ignored = new Fixture()) {
            var inset = MessageListView.class.getDeclaredMethod("avatarEdgeInset");
            inset.setAccessible(true);
            // At the shipped 50px face, the reference needs 2.5px each side.
            assertTrue((float) inset.invoke(null) >= UiTokens.AVATAR_SIZE * 0.05F,
                    "list clip would cut off the enlarged hat");
            AtomChatConfig.get().avatarStyle = AtomChatConfig.AvatarStyle.ATOMCHAT;
            assertEquals(1.875F, (float) inset.invoke(null), 0.0001F);
        }
    }

    private static int pixel(Surface surface, int x, int y) {
        try (Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(surface.getWidth(), surface.getHeight());
            assertTrue(surface.readPixels(bitmap, 0, 0));
            byte[] data = bitmap.readPixels();
            int p = (y * surface.getWidth() + x) * 4;
            return (data[p + 3] & 255) << 24 | (data[p + 2] & 255) << 16
                    | (data[p + 1] & 255) << 8 | data[p] & 255;
        }
    }

    private static Image layer(boolean hat) {
        try (Surface surface = Surface.makeRasterN32Premul(8, 8); Paint paint = new Paint()) {
            surface.getCanvas().clear(hat ? 0 : RED);
            if (hat) {
                paint.setColor(GREEN);
                surface.getCanvas().drawRect(Rect.makeXYWH(0, 0, 8, 1), paint);
                surface.getCanvas().drawRect(Rect.makeXYWH(0, 7, 8, 1), paint);
                surface.getCanvas().drawRect(Rect.makeXYWH(0, 0, 1, 8), paint);
                surface.getCanvas().drawRect(Rect.makeXYWH(7, 0, 1, 8), paint);
            }
            return surface.makeImageSnapshot();
        }
    }

    /** Inject images at the existing cache boundary; no GL and no production hooks. */
    private static final class Fixture implements AutoCloseable {
        final AtomChatConfig.AvatarStyle previousStyle = AtomChatConfig.get().avatarStyle;
        final Image face = layer(false), hat = layer(true);
        final Object key = SkinResolver.getSkin(SUBJECT, NAME);
        final List<Map<Object, Image>> caches = new ArrayList<>();
        final List<Image> previous = new ArrayList<>();

        @SuppressWarnings("unchecked") Fixture() throws Exception {
            for (String name : List.of("FACE_CACHE", "SKIN_FACE_CACHE", "HAT_CACHE")) {
                Field field = AvatarRenderer.class.getDeclaredField(name);
                field.setAccessible(true);
                Map<Object, Image> cache = (Map<Object, Image>) field.get(null);
                caches.add(cache);
                previous.add(cache.put(key, name.equals("HAT_CACHE") ? hat : face));
            }
            AtomChatConfig.get().avatarStyle = AtomChatConfig.AvatarStyle.E33CHAT;
        }

        public void close() {
            for (int i = 0; i < caches.size(); i++) {
                if (previous.get(i) == null) caches.get(i).remove(key);
                else caches.get(i).put(key, previous.get(i));
            }
            AtomChatConfig.get().avatarStyle = previousStyle;
            face.close();
            hat.close();
        }
    }
}
