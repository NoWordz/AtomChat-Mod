package net.minecraft.client.gui.screen;

import com.atom.chat.platform.Platform;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The suggestion window's anchors, offline: they are two pure functions of the
 * frame's layout, the UI density and the window's GUI scale, so they can be
 * pinned here without a client (the rest of {@link SuggestorWiring} builds a
 * {@code CommandSuggestions} and is therefore a live-probe item).
 *
 * <p>What the anchors promise, and what these tests hold them to: the popup's
 * bottom edge sits on the input bar's top edge minus the 4px that clears the
 * button row, its left edge on the composer's text column, and both are handed
 * to vanilla in GUI space — panel-space px × density ÷ GUI scale, rounded to
 * whole pixels, because vanilla positions the window in GUI coordinates and the
 * panel is drawn in its own space on top of them.
 */
class SuggestorWiringTest {
    private static final float PANEL_X = 10.0F;
    private static final float PANEL_Y = 10.0F;
    private static final float PANEL_W = 400.0F;
    private static final float PANEL_H = 780.0F;

    @TempDir
    static Path platformRoot;

    /** {@link UiTokens} resolves its scale through the config, which goes through Platform. */
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

    private static UiLayout layout() {
        return UiLayout.of(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
    }

    /** The two insets the anchors exist for; both are panel-space lengths. */
    @Test
    void theInsetsTheAnchorsApplyAreReal() {
        assertTrue(UiTokens.s(4) > 0.0F, "the popup clears the input bar's button row by 4 panel px");
        assertTrue(UiTokens.INPUT_TEXT_X > 0.0F, "the text column is inset from the bar's left edge");
    }

    @Test
    void theTopAnchorIsTheBarTopMinusTheInsetInGuiSpace() {
        UiLayout layout = layout();

        int anchored = SuggestorWiring.anchorTopY(layout, 2.0, 4.0);

        assertEquals((int) Math.round((layout.inputBar.y() - UiTokens.s(4)) * 2.0 / 4.0), anchored,
                "the popup's bottom edge clears the whole input bar, converted to GUI space");
    }

    @Test
    void theLeftAnchorIsTheTextColumnsLeftEdgeInGuiSpace() {
        UiLayout layout = layout();

        int anchored = SuggestorWiring.anchorLeftX(layout, 2.0, 4.0);

        assertEquals((int) Math.round((layout.inputBar.x() + UiTokens.INPUT_TEXT_X) * 2.0 / 4.0), anchored,
                "the popup opens on the composer's text column, not on the bar's left edge");
    }

    @Test
    void theAnchorsFollowDensityAndGuiScale() {
        UiLayout layout = layout();
        int base = SuggestorWiring.anchorTopY(layout, 1.0, 2.0);

        assertEquals(base * 2.0, SuggestorWiring.anchorTopY(layout, 2.0, 2.0), 1.0,
                "twice the density is twice as many GUI pixels");
        assertEquals(base / 2.0, SuggestorWiring.anchorTopY(layout, 1.0, 4.0), 1.0,
                "twice the window scale is half as many GUI pixels");
        assertEquals(base, SuggestorWiring.anchorTopY(layout, 2.0, 4.0), 1.0,
                "the two cancel out: the anchor tracks the panel, not the screen");
    }

    @Test
    void theAnchorsLandOnWholeGuiPixels() {
        UiLayout layout = layout();
        double exact = (layout.inputBar.y() - UiTokens.s(4)) * 1.5 / 3.0;

        int anchored = SuggestorWiring.anchorTopY(layout, 1.5, 3.0);

        assertEquals((int) Math.round(exact), anchored);
        assertTrue(Math.abs(anchored - exact) <= 0.5,
                "a fractional panel coordinate is rounded to the GUI pixel vanilla can use");
    }
}
