package com.atom.chat.screen;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.*;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Field;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class HalfScreenMetricsTest {
    @TempDir Path tmp;
    Field provider, instance;
    Object previousProvider, previousConfig;

    @BeforeEach void setup() throws Exception {
        provider = Platform.class.getDeclaredField("provider");
        instance = AtomChatConfig.class.getDeclaredField("instance");
        provider.setAccessible(true); instance.setAccessible(true);
        previousProvider = provider.get(null); previousConfig = instance.get(null);
        Platform.install(new Platform.Provider() {
            public Path configDir() { return tmp.resolve("config"); }
            public Path gameDir() { return tmp.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
        AtomChatConfig.reload();
    }

    @AfterEach void restore() throws Exception {
        provider.set(null, previousProvider); instance.set(null, previousConfig);
    }

    private void dock() throws Exception {
        AtomChatConfig.class.getField("halfScreenMode").setBoolean(AtomChatConfig.get(), true);
        AtomChatConfig.class.getField("halfScreenWidth").setFloat(AtomChatConfig.get(), 600F);
    }

    @Test void dockedFrameFillsHeightAndStartsAtTheScreenOriginAtEveryScale() throws Exception {
        dock();
        for (int height : new int[]{480, 1080, 2160}) {
            for (float ui : new float[]{0.75F, 1F, 1.5F}) {
                for (float content : new float[]{0.8F, 1F, 1.5F}) {
                    AtomChatConfig.get().uiScale = ui;
                    AtomChatConfig.get().contentScale = content;
                    assertEquals(0F, ScreenMetrics.panelX());
                    assertEquals(0F, ScreenMetrics.panelY(height));
                    assertEquals(height, ScreenMetrics.panelHeight(height) * ScreenMetrics.uiDensity(height), 0.001F);
                }
            }
        }
    }

    @Test void widthUsesTheDockedSettingAndKeepsWorldVisibleInSmallWindows() throws Exception {
        dock();
        AtomChatConfig.get().panelWidth = 400F;
        assertEquals(750F, ScreenMetrics.panelWidth(1920, 1080), 0.001F);
        assertEquals(288F, ScreenMetrics.panelWidth(320, 240), 0.001F);
        AtomChatConfig.get().contentScale = 1.5F;
        assertEquals(500F, ScreenMetrics.panelWidth(1920, 1080), 0.001F);
    }

    @Test void dockedChatHeaderAndComposerActuallyTouchAllThreeScreenEdges() throws Exception {
        dock();
        UiLayout chat = UiLayout.of(0, 0, 750, 1080, 26, 42, true);
        assertEquals(0F, chat.header.x()); assertEquals(0F, chat.header.y());
        assertEquals(750F, chat.header.w());
        assertEquals(0F, chat.inputBar.x()); assertEquals(750F, chat.inputBar.w());
        assertEquals(1080F, chat.inputBar.bottom());
        assertTrue(chat.list.bottom() <= chat.inputBar.y());
        assertEquals(chat.inputBar.y(), chat.replyBar.bottom());
        UiLayout root = UiLayout.ofRoot(0, 0, 750, 1080, true);
        assertEquals(0F, root.tabBar.x()); assertEquals(750F, root.tabBar.w());
        assertEquals(1080F, root.tabBar.bottom());
    }

    @Test void dockedOverlayClipPaintsAtTheActualScreenEdges() throws Exception {
        dock();
        try (Surface surface = Surface.makeRasterN32Premul(420, 800); Bitmap pixels = new Bitmap();
             Paint paint = new Paint().setColor(0xFFFFFFFF)) {
            var canvas = surface.getCanvas();
            canvas.clear(0); canvas.save();
            PanelBackground.clipInterior(canvas, new UiLayout.Rect(0, 0, 400, 780));
            canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(0, 0, 420, 800), paint);
            canvas.restore();
            pixels.allocN32Pixels(420, 800); assertTrue(surface.readPixels(pixels, 0, 0));
            byte[] bytes = pixels.readPixels();
            for (int x : new int[]{0, 200, 399}) for (int y : new int[]{0, 400, 779}) {
                assertEquals(255, bytes[(y * 420 + x) * 4 + 3] & 255, "unpainted screen edge");
            }
            assertEquals(0, bytes[(400 * 420 + 400) * 4 + 3] & 255);
        }
    }

    @Test void dockedBlurCaptureUsesTheSameFlushSquareContour() throws Exception {
        dock();
        var capture = PanelOpenMotion.blurCapture(1F, AtomChatConfig.PageNavStyle.SLIDE,
                0, 0, 750, 1080, UiTokens.PANEL_BEZEL_WIDTH, 1, 1);
        assertEquals(0F, capture.x()); assertEquals(0F, capture.y());
        assertEquals(750F, capture.w()); assertEquals(1080F, capture.h());
        assertEquals(0F, capture.radius());
    }

    @Test void basicLayoutRemainsUsableWithoutInstallingAPlatform() throws Exception {
        provider.set(null, null); instance.set(null, null);
        UiLayout layout = assertDoesNotThrow(() -> UiLayout.of(30, 20, 600, 900));
        assertEquals(45F, layout.header.x());
    }
}
