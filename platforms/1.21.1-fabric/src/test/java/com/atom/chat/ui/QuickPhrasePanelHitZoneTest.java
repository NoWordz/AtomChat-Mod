package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The edit and delete keys of a quick-phrase row must not share a band.
 *
 * <p>Their hit rects used to be s(28) wide around centres s(24) apart, so they
 * overlapped by s(5), and delete was tested first: a click in that band deleted
 * the phrase even with the pointer visually on the edit glyph. The two glyphs
 * are only ~10px apart, which is why nobody noticed.
 *
 * <p>The geometry is asserted where it is computed, because this panel cannot be
 * built in a unit test without the whole Skija and Minecraft stack.
 */
class QuickPhrasePanelHitZoneTest {

    private static final float PANEL_X = 10.0F;
    private static final float PANEL_Y = 10.0F;
    private static final float PANEL_W = 400.0F;
    private static final float PANEL_H = 780.0F;
    /** Numeric equality of values derived from the same tokens. */
    private static final float EXACT = 0.0F;
    private static final int ACCENT = 0xFF3B82F6;

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
        List<String> phrases = AtomChatConfig.get().quickPhrases;
        if (phrases.isEmpty()) {
            phrases.add("hit-zone fixture");
        }
    }

    /** One row of the shipped layout, reached through the panel's own geometry. */
    private static Rect rowRect(UiLayout layout, int visibleIndex) throws Exception {
        Method rowRect = QuickPhrasePanel.class.getDeclaredMethod(
                "rowRect", UiLayout.class, int.class);
        rowRect.setAccessible(true);
        return (Rect) rowRect.invoke(null, layout, visibleIndex);
    }

    private static UiLayout layout() {
        return UiLayout.of(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
    }

    private static QuickPhrasePanel panel() {
        return new QuickPhrasePanel(text -> {
        }, () -> ACCENT);
    }

    @Test
    void theTwoHitZonesMeetAndDoNotOverlap() throws Exception {
        Rect row = rowRect(layout(), 0);
        float[] g = QuickPhrasePanel.iconHitGeometry(row);
        float editLeft = g[0];
        float editRight = g[1];
        float deleteLeft = g[2];
        float deleteRight = g[3];
        float editCx = g[4];
        float deleteCx = g[5];

        assertTrue(deleteLeft > row.getLeft() && deleteRight <= row.getRight(),
                "the delete hit zone must stay inside its row");
        assertEquals(editRight, deleteLeft, EXACT,
                "the two hit zones must meet, not overlap: this gap is the bug band");
        assertEquals((editCx + deleteCx) / 2.0F, editRight, EXACT,
                "the shared edge belongs halfway between the two glyphs");
        assertTrue(editCx > editLeft && editCx < editRight,
                "the edit glyph must sit inside the edit hit zone");
        assertTrue(deleteCx > deleteLeft && deleteCx < deleteRight,
                "the delete glyph must sit inside the delete hit zone");
        assertEquals(row.getBottom(), g[7], EXACT, "a hit zone spans its whole row");
        assertEquals(row.getTop(), g[8], EXACT, "a hit zone spans its whole row");
    }

    /**
     * Every point of a row resolves to exactly one key, and the two click actions
     * swap at the shared edge. The band that used to delete now edits: the edit
     * zone runs right up to where the delete zone starts.
     */
    @Test
    void everyPointOfTheRowPicksTheKeyUnderThePointer() throws Exception {
        UiLayout layout = layout();
        Rect row = rowRect(layout, 0);
        float[] g = QuickPhrasePanel.iconHitGeometry(row);
        QuickPhrasePanel panel = panel();
        float midY = row.getTop() + row.getHeight() / 2.0F;
        int editHits = 0;
        int deleteHits = 0;
        int inserts = 0;

        for (float x = row.getLeft(); x < row.getRight(); x += 0.25F) {
            int type = panel.click(layout, x, midY).type();
            if (x >= g[0] && x < g[2]) {
                // Inside the edit zone there is only one answer, and it is the one
                // the overlapping bands used to steal.
                assertEquals(QuickPhrasePanel.Action.EDIT, type,
                        "x=" + x + " sits in the edit zone, which must not delete");
                editHits++;
            } else if (x >= g[2] && x < g[3]) {
                assertEquals(QuickPhrasePanel.Action.DELETE, type,
                        "x=" + x + " is on or right of the shared edge, so it must delete");
                deleteHits++;
            } else {
                assertEquals(QuickPhrasePanel.Action.INSERT, type,
                        "x=" + x + " is the row's text area, which still inserts");
                inserts++;
            }
        }
        assertTrue(editHits > 0 && deleteHits > 0 && inserts > 0,
                "the walk must cover the text area and both icon zones: edit=" + editHits
                        + " delete=" + deleteHits + " insert=" + inserts);
        // A half-open hit test means the shared edge belongs to delete and only to
        // delete; the edit zone is the half open range that ends there.
        assertEquals(QuickPhrasePanel.Action.DELETE, panel.click(layout, g[2], midY).type(),
                "the shared edge itself belongs to the delete key");
    }

    /** The glyphs themselves: each one must answer with its own action. */
    @Test
    void theGlyphCentresPickTheirOwnAction() throws Exception {
        UiLayout layout = layout();
        Rect row = rowRect(layout, 0);
        float[] g = QuickPhrasePanel.iconHitGeometry(row);
        float midY = row.getTop() + row.getHeight() / 2.0F;
        QuickPhrasePanel panel = panel();

        assertEquals(QuickPhrasePanel.Action.EDIT, panel.click(layout, g[4], midY).type(),
                "the edit glyph must edit");
        assertEquals(QuickPhrasePanel.Action.DELETE, panel.click(layout, g[5], midY).type(),
                "the delete glyph must delete");
    }

    /**
     * The click path and the hover path must resolve the same point to the same
     * key. They used to be two hand-written copies of one hit rect, which is how
     * the delete key ended up winning on the edit glyph.
     */
    @Test
    void theClickPathAndTheHoverPathAgree() throws Exception {
        UiLayout layout = layout();
        Rect row = rowRect(layout, 0);
        float[] g = QuickPhrasePanel.iconHitGeometry(row);
        float midY = row.getTop() + row.getHeight() / 2.0F;
        QuickPhrasePanel panel = panel();
        panel.open();

        for (float x = g[0]; x < g[3]; x += 0.25F) {
            panel.hover(layout, x, midY);
            QuickPhrasePanel.Action action = panel.click(layout, x, midY);
            int clickedKey = switch (action.type()) {
                case QuickPhrasePanel.Action.EDIT -> 0;
                case QuickPhrasePanel.Action.DELETE -> 1;
                default -> -1;
            };
            assertEquals(clickedKey, panel.hoveredIconButton(),
                    "x=" + x + " resolves to different keys on the two paths");
        }
    }

    @Test
    void eachHitZoneIsWideEnoughToHit() throws Exception {
        Rect row = rowRect(layout(), 0);
        float[] g = QuickPhrasePanel.iconHitGeometry(row);
        assertTrue(g[1] - g[0] >= UiTokens.CONTEXT_ICON_SIZE,
                "the edit hit zone is narrower than the glyph it carries");
        assertTrue(g[3] - g[2] >= UiTokens.CONTEXT_ICON_SIZE,
                "the delete hit zone is narrower than the glyph it carries");
    }
}
