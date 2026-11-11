package com.atom.chat.nav;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** The production empty glyph, dot backdrop and caption must share the page transform. */
class EmptyStateTransitionTest {
    @TempDir Path tmp;
    private static final int W = 560, H = 960;
    private static final UiLayout LAYOUT = UiLayout.ofDetail(10, 10, 520, 900);
    private static final UiLayout.Rect AREA = new UiLayout.Rect(70, 250, 380, 280);

    private static final class EmptyHost implements PageTransitionComposite.Host {
        NavPage top = NavPage.of(AppPage.SETTINGS);
        String caption;
        public NavPage topNav() { return top; }
        // Empty lists used to bypass the whole-page envelope because their rows staggered.
        public boolean arrivalStaggersSurfaces() { return true; }
        public void drawArrivingBody(Canvas canvas, UiLayout layout, int x, int y) {
            if (caption != null) SettingsSectionPage.drawEmptyState(canvas, AREA, caption);
        }
        public void drawArrivingHeader(Canvas canvas, UiLayout layout, int x, int y, float vx, float vy) { }
    }

    @Test void onlineAndBlockedEmptyStatesEnterExitAndReverseWithThePage() throws Exception {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return tmp.resolve("config"); }
            public Path gameDir() { return tmp.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
        var config = AtomChatConfig.get();
        boolean animations = config.animationEnabled;
        var previousStyle = config.pageNavStyle;
        try {
            config.animationEnabled = true;
            for (var style : AtomChatConfig.PageNavStyle.values()) {
                config.pageNavStyle = style;
                EmptyHost host = new EmptyHost();
                PageTransitionComposite composite = new PageTransitionComposite(host);
                try (Surface surface = Surface.makeRasterN32Premul(W, H)) {
                    byte[] blank = frame(composite, surface, 1000);
                    host.top = NavPage.of(AppPage.CHAT_LIST);
                    host.caption = "atomchat.conversation.no_players";
                    assertArrayEquals(blank, frame(composite, surface, 1010),
                            "no-online-player glyph must start with the entering page, never pop in at rest");
                    byte[] entering = frame(composite, surface, 1090);
                    byte[] settled = frame(composite, surface, 1400);
                    assertFalse(Arrays.equals(blank, entering), "the moving empty illustration must be visible");
                    assertFalse(Arrays.equals(entering, settled), "glyph, caption and halo cannot remain stationary");
                    save(surface, style + "-online-settled.png");

                    host.top = NavPage.settingsSection(SettingsSection.PRIVACY);
                    host.caption = "atomchat.settings.privacy.empty";
                    assertArrayEquals(settled, frame(composite, surface, 1410),
                            "real outgoing empty-state pixels must survive the first exit frame");
                    byte[] beforeInterrupt = frame(composite, surface, 1490);
                    host.top = NavPage.of(AppPage.CHAT_LIST);
                    host.caption = "atomchat.conversation.no_players";
                    assertArrayEquals(beforeInterrupt, frame(composite, surface, 1490),
                            "reversing an empty page must continue from the displayed glyph and caption");
                    frame(composite, surface, 1800);
                    host.top = NavPage.settingsSection(SettingsSection.PRIVACY);
                    host.caption = "atomchat.settings.privacy.empty";
                    frame(composite, surface, 1810);
                    byte[] blocked = frame(composite, surface, 2200);
                    save(surface, style + "-blocked-settled.png");
                    host.top = NavPage.of(AppPage.SETTINGS);
                    host.caption = null;
                    assertArrayEquals(blocked, frame(composite, surface, 2210),
                            "the no-blocked-player illustration must animate out instead of disappearing");
                    assertArrayEquals(blank, frame(composite, surface, 2600));
                } finally {
                    if ((Object) composite instanceof AutoCloseable owned) owned.close();
                }
            }
        } finally { config.animationEnabled = animations; config.pageNavStyle = previousStyle; }
    }

    @Test void inverseHitsUseThePaintedFrameTime() throws Exception {
        var config = AtomChatConfig.get();
        boolean animations = config.animationEnabled;
        var style = config.pageNavStyle;
        EmptyHost host = new EmptyHost();
        try (PageTransitionComposite composite = new PageTransitionComposite(host);
             Surface surface = Surface.makeRasterN32Premul(W, H)) {
            config.animationEnabled = true;
            config.pageNavStyle = AtomChatConfig.PageNavStyle.SLIDE;
            frame(composite, surface, 1000);
            host.top = NavPage.of(AppPage.CHAT_LIST);
            composite.beginFrame(1010);
            composite.drawArrivingFrame(surface.getCanvas(), LAYOUT, 0, 0, 0, 0, 1050);
            float logicalX = 250F;
            float paintedX = logicalX + composite.overlaySlideDx(1050);
            assertEquals(logicalX, composite.bodyX(paintedX), .0001F,
                    "hit testing must invert the visible frame, not an earlier navigation sample");
        } finally { config.animationEnabled = animations; config.pageNavStyle = style; }
    }

    private static byte[] frame(PageTransitionComposite composite, Surface surface, long now) {
        composite.beginFrame(now);
        composite.consumeArrivalEvent();
        Canvas canvas = surface.getCanvas();
        canvas.clear(0);
        int saves = canvas.getSaveCount();
        composite.drawArrivingFrame(canvas, LAYOUT, -100, -100, -100, -100, now);
        assertEquals(saves, canvas.getSaveCount());
        try (Bitmap pixels = new Bitmap()) {
            pixels.allocN32Pixels(W, H);
            assertTrue(surface.readPixels(pixels, 0, 0));
            return pixels.readPixels();
        }
    }

    private static void save(Surface surface, String name) throws Exception {
        Path output = Path.of(System.getProperty("atomchat.repoRoot", "../.."), ".work/v0.7.0-feedback/empty-state-frames");
        Files.createDirectories(output);
        try (Image image = surface.makeImageSnapshot(); Data png = image.encodeToData()) {
            assertNotNull(png);
            Files.write(output.resolve(name), png.getBytes());
        }
    }
}
