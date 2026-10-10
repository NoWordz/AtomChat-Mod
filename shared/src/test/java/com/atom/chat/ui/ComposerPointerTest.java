package com.atom.chat.ui;

import com.atom.chat.font.FontManager;
import com.atom.chat.page.ProfilePage;
import com.atom.chat.platform.Platform;
import com.atom.chat.render.SkiaFontRenderer;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ComposerPointer}, offline: the IME anchor arithmetic (the trick this
 * half exists for), the code-point hit test, the selection highlight and the
 * drag state.
 *
 * <p><b>The anchor case is driven by a stub vanilla width, not by a guess.</b>
 * The whole point of the trick is the identity
 * {@code fieldX + vanillaPrefixWidth == skiaCaretX}: the field's own screen caret
 * has to land exactly where Skia draws the caret. The test asserts that identity
 * with an exact expected {@code fieldX}, and the sign is what makes it
 * meaningful — flipping it in the production code moves the field by twice the
 * vanilla width and the case goes red (verified by running that mutation once).</p>
 *
 * <p><b>Scope, stated up front.</b> One fake field implements both the D21a and
 * D21b ports, so the composer's text/caret and the pointer's geometry come from
 * one place. The row/line widths are measured with the real Skia font. NOT
 * covered: the real IME window against a real input method, the real field's own
 * cursor fields, and the event routing that calls these (D23) — the D21 probe
 * list.</p>
 */
class ComposerPointerTest {
    private static final UiLayout LAYOUT = UiLayout.of(0.0F, 0.0F, 420.0F, 820.0F);
    private static final int SURFACE_W = 200;
    private static final int SURFACE_H = 120;

    @BeforeAll
    static void installTemporaryPlatform(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return root.resolve("config");
            }

            @Override
            public Path gameDir() {
                return root.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    @TempDir
    Path tmp;

    /** One fake field behind both ports: the composer's text/caret and the geometry. */
    private static final class FakeField implements ComposerInput.Field, ComposerPointer.Field {
        final List<String> calls = new ArrayList<>();
        boolean present = true;
        String text = "";
        int caret;
        int highlight;
        boolean fieldFocused;
        Integer fieldX;
        Integer fieldY;
        Integer fieldWidth;
        Integer fieldHeight;

        // ---- ComposerInput.Field ----
        @Override
        public boolean present() {
            return present;
        }

        @Override
        public String text() {
            return text;
        }

        @Override
        public void setText(String value) {
            text = value;
        }

        @Override
        public int caretPosition() {
            return caret;
        }

        @Override
        public boolean focused() {
            return fieldFocused;
        }

        @Override
        public void setScreenFocus() {
        }

        @Override
        public void fieldFocus() {
        }

        @Override
        public void fieldFocusGuarded() {
        }

        @Override
        public void fieldBlur() {
        }

        @Override
        public void moveCaretTo(int pos) {
            caret = pos;
        }

        // ---- ComposerPointer.Field ----
        @Override
        public void setFieldX(int x) {
            calls.add("x:" + x);
            fieldX = x;
        }

        @Override
        public void setFieldY(int y) {
            calls.add("y:" + y);
            fieldY = y;
        }

        @Override
        public void setFieldWidth(int w) {
            calls.add("w:" + w);
            fieldWidth = w;
        }

        @Override
        public void setFieldHeight(int h) {
            calls.add("h:" + h);
            fieldHeight = h;
        }

        @Override
        public int cursorPosition() {
            return caret;
        }

        @Override
        public int highlightPosition() {
            return highlight;
        }

        @Override
        public void setCommandMode(boolean commandMode) {
            calls.add("ime:" + commandMode);
        }
    }

    private static final class StubHost implements ComposerPointer.Host {
        double density = 1.0;
        double guiScale = 1.0;
        int vanillaWidth = 40;
        ProfilePage.SignatureAnchor anchor;

        @Override
        public double density() {
            return density;
        }

        @Override
        public double guiScale() {
            return guiScale;
        }

        @Override
        public int vanillaPrefixWidth(String prefix) {
            return vanillaWidth;
        }

        @Override
        public ProfilePage.SignatureAnchor signatureAnchor() {
            return anchor;
        }

        @Override
        public int clampInt(int value, int lo, int hi) {
            return Math.max(lo, Math.min(hi, value));
        }
    }

    private FakeField field;
    private StubHost host;
    private ComposerInput composer;
    private ComposerPointer pointer;

    private void setUp() {
        field = new FakeField();
        host = new StubHost();
        composer = new ComposerInput(new ComposerInput.Host() {
            @Override
            public UiLayout layout() {
                return LAYOUT;
            }

            @Override
            public ScrollController currentScroll() {
                return new ScrollController();
            }

            @Override
            public float frameDt() {
                return 16.0F;
            }

            @Override
            public int clampInt(int value, int lo, int hi) {
                return Math.max(lo, Math.min(hi, value));
            }
        }, field);
        pointer = new ComposerPointer(composer, host, field);
    }

    private static Font inputFont() {
        return FontManager.font(UiTokens.FONT_INPUT);
    }

    // ------------------------------------------------------- 1. the IME anchor

    @Test
    void theFieldIsShiftedSoItsOwnCaretLandsOnTheSkiaCaret() {
        setUp();
        field.text = "hello world";
        field.caret = 5;
        pointer.positionField(LAYOUT);

        int skiaCaretX = (int) Math.round(LAYOUT.inputBar.x() + UiTokens.INPUT_TEXT_X
                + SkiaFontRenderer.getStringWidth(inputFont(), pointer.linePrefixAt(LAYOUT, 5)));
        assertEquals(skiaCaretX - host.vanillaWidth, field.fieldX,
                "the field's X is the Skia caret position minus the vanilla prefix width");
        assertEquals(skiaCaretX, field.fieldX + host.vanillaWidth,
                "so the field's own screen caret (fieldX + vanilla width) matches the Skia caret exactly");
    }

    @Test
    void theAnchorMovesWithTheCaret() {
        setUp();
        field.text = "hello world";
        field.caret = 0;
        pointer.positionField(LAYOUT);
        int atStart = field.fieldX;

        field.caret = 11;
        pointer.positionField(LAYOUT);
        int atEnd = field.fieldX;

        assertTrue(atEnd > atStart, "a later caret sits further right, so the field shifts right with it");
        assertEquals(SkiaFontRenderer.getStringWidth(inputFont(), "hello world"),
                atEnd - atStart, 1.0F,
                "and the shift is exactly the width of the text the caret moved over");
    }

    @Test
    void theAnchorRespectsTheDensityAndGuiScale() {
        setUp();
        host.density = 2.0;
        host.guiScale = 1.0;
        field.text = "hello";
        field.caret = 5;
        pointer.positionField(LAYOUT);

        int skiaCaretX = (int) Math.round((LAYOUT.inputBar.x() + UiTokens.INPUT_TEXT_X
                + SkiaFontRenderer.getStringWidth(inputFont(), "hello")) * 2.0 / 1.0);
        assertEquals(skiaCaretX - host.vanillaWidth, field.fieldX,
                "the virtual Skia position is converted to GUI space before the shift");
    }

    @Test
    void theFieldGeometryFollowsTheBar() {
        setUp();
        host.density = 1.5;
        host.guiScale = 1.0;
        field.text = "hi";
        field.caret = 2;
        pointer.positionField(LAYOUT);

        assertEquals((int) Math.round(composer.caretLineTopY() * 1.5), field.fieldY,
                "the field's top follows the caret's line");
        assertEquals((int) Math.max(10.0F, Math.round((LAYOUT.inputBar.w() - UiTokens.INPUT_TEXT_X * 2.0F) * 1.5)),
                field.fieldWidth, "the width is the bar's text box, in GUI space");
        assertEquals((int) Math.round(composer.lineHeight() * 1.5), field.fieldHeight,
                "and the height is one text line");
    }

    @Test
    void theSignatureEditorParksTheAnchorOnItsOwnRow() {
        setUp();
        ProfilePage.SignatureAnchor anchor = new ProfilePage.SignatureAnchor(90.0F, 300.0F, 250.0F, 22.0F);
        host.anchor = anchor;
        field.text = "sig";
        field.caret = 3;
        pointer.positionField(LAYOUT);

        int skiaCaretX = (int) Math.round((anchor.textX()
                + SkiaFontRenderer.getStringWidth(FontManager.font(UiTokens.PROFILE_SIGN_FONT), "sig")) * 1.0 / 1.0);
        assertEquals(skiaCaretX - host.vanillaWidth, field.fieldX,
                "the same prefix compensation, measured in the signature font");
        assertEquals((int) Math.round(anchor.baselineY() - anchor.lineH()), field.fieldY,
                "y comes from the edit row: the line's top, not the bar's");
        assertEquals((int) Math.round(anchor.textMaxW()), field.fieldWidth);
        assertEquals((int) Math.round(anchor.lineH()), field.fieldHeight);
    }

    @Test
    void theSignatureBranchNeverTouchesTheCommandModeBridge() {
        setUp();
        host.anchor = new ProfilePage.SignatureAnchor(90.0F, 300.0F, 250.0F, 22.0F);
        field.text = "/sig";
        field.caret = 4;
        pointer.positionField(LAYOUT);
        assertFalse(field.calls.stream().anyMatch(c -> c.startsWith("ime:")),
                "the bridge is the composer path's business only");
    }

    @Test
    void theCommandModeBridgeOnlyFiresWhenTheStateFlips() {
        setUp();
        field.text = "/tp Bob";
        field.caret = 0;

        pointer.positionField(LAYOUT);
        assertEquals(List.of("ime:true"), field.calls.stream().filter(c -> c.startsWith("ime:")).toList(),
                "typing a command turns the English IME on once");

        field.calls.clear();
        pointer.positionField(LAYOUT);
        pointer.positionField(LAYOUT);
        assertTrue(field.calls.stream().noneMatch(c -> c.startsWith("ime:")),
                "and stays quiet while the state does not change");

        field.text = "hello";
        pointer.positionField(LAYOUT);
        assertEquals(List.of("ime:false"), field.calls.stream().filter(c -> c.startsWith("ime:")).toList(),
                "leaving command mode turns it back off, once");
    }

    // ------------------------------------------------------ 2. the hit test

    /** The x of a code-point boundary on the first line, in virtual space. */
    private float boundaryX(String text, int chars) {
        return LAYOUT.inputBar.x() + UiTokens.INPUT_TEXT_X
                + SkiaFontRenderer.getStringWidth(inputFont(), text.substring(0, chars));
    }

    @Test
    void aPointLeftOfACharacterMidpointSelectsThatCharacter() {
        setUp();
        field.text = "abcdef";
        float lineCentreY = LAYOUT.inputTextCenterY;

        float charWidth = SkiaFontRenderer.getStringWidth(inputFont(), "c");
        float xOfC = boundaryX("abcdef", 2);
        assertEquals(2, pointer.caretIndexAt(LAYOUT, xOfC + charWidth / 4.0F, lineCentreY),
                "left of c's midpoint: the caret goes before c");
        assertEquals(3, pointer.caretIndexAt(LAYOUT, xOfC + charWidth * 3.0F / 4.0F, lineCentreY),
                "right of it: after c, i.e. before d");
    }

    @Test
    void aPointPastTheEndSelectsTheLineEnd() {
        setUp();
        field.text = "abc";
        assertEquals(3, pointer.caretIndexAt(LAYOUT, boundaryX("abc", 3) + 200.0F, LAYOUT.inputTextCenterY));
    }

    @Test
    void anEmptyDraftHasNoIndexOfItsOwn() {
        setUp();
        field.text = "";
        assertEquals(0, pointer.caretIndexAt(LAYOUT, 100.0F, LAYOUT.inputTextCenterY));
    }

    @Test
    void theHitTestNeverLandsInsideAnEmojiSurrogatePair() {
        setUp();
        String text = "a\uD83D\uDE00b";
        field.text = text;
        assertEquals(4, text.length(), "the emoji is one code point in two chars");
        int emojiStart = 1;

        float from = boundaryX(text, emojiStart);
        float to = boundaryX(text, emojiStart + 2);
        assertTrue(to > from, "the emoji has a width of its own");

        for (float x = from - 2.0F; x <= to + 2.0F; x += 0.5F) {
            int index = pointer.caretIndexAt(LAYOUT, x, LAYOUT.inputTextCenterY);
            assertFalse(index == emojiStart + 1,
                    "index " + index + " would split the surrogate pair at x=" + x);
            assertTrue(index == emojiStart || index == emojiStart + 2,
                    "a point over the emoji selects before or after it (got " + index + ")");
        }
        assertEquals(text.indexOf('b'), pointer.caretIndexAt(LAYOUT,
                boundaryX(text, emojiStart + 2) + 1.0F, LAYOUT.inputTextCenterY),
                "and the text after the emoji keeps its own indices");
    }

    @Test
    void theHitTestWalksTheVisibleRows() {
        setUp();
        field.text = "a".repeat(400);
        composer.updateLayout(LAYOUT);
        composer.updateLayout(LAYOUT);
        List<String> lines = composer.wrappedInput(LAYOUT.inputTextMaxWidth());
        int shown = Math.min(UiTokens.INPUT_MAX_LINES, lines.size());
        assertTrue(shown > 1, "this fixture must wrap for the row walk to mean anything");

        float lineH = composer.lineHeight();
        int secondRowStart = lines.get(0).length();
        int index = pointer.caretIndexAt(LAYOUT, LAYOUT.inputBar.x() + UiTokens.INPUT_TEXT_X,
                LAYOUT.inputTextCenterY + lineH);
        assertEquals(secondRowStart, index, "the second visible row starts after the first row's text");
    }

    @Test
    void theLinePrefixIsClampedToTheLine() {
        setUp();
        field.text = "abcdef";
        assertEquals("", pointer.linePrefixAt(LAYOUT, 0));
        assertEquals("abc", pointer.linePrefixAt(LAYOUT, 3));
        assertEquals("abcdef", pointer.linePrefixAt(LAYOUT, 99), "past the end: the whole line");
    }

    // ---------------------------------------------------- 3. the drag state

    @Test
    void theDragStateIsCarried() {
        setUp();
        assertFalse(pointer.dragging(), "nothing is being dragged at first");
        assertEquals(-1, pointer.dragAnchor(), "and there is no press anchor yet");
        pointer.setDragging(true);
        pointer.setDragAnchor(7);
        assertTrue(pointer.dragging());
        assertEquals(7, pointer.dragAnchor());
        pointer.setDragging(false);
        assertFalse(pointer.dragging());
    }

    // -------------------------------------------------- 4. the selection paint

    @Test
    void aSelectionPaintsOneBandPerCoveredLine() {
        setUp();
        Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        try {
            field.text = "abcdef";
            field.caret = 1;
            field.highlight = 4;
            float textX = 10.0F;
            float centreY = 40.0F;
            float lineH = 20.0F;
            pointer.drawSelection(surface.getCanvas(), inputFont(), List.of("abcdef"), 0, 1, textX, centreY, lineH);

            int[] bounds = paintedBounds(surface);
            assertNotNull(bounds, "a selection must paint");
            float expectedLeft = textX + SkiaFontRenderer.getStringWidth(inputFont(), "a");
            float expectedRight = textX + SkiaFontRenderer.getStringWidth(inputFont(), "abcd");
            assertTrue(Math.abs(bounds[0] - expectedLeft) <= 3, "the band starts where the selection does ("
                    + bounds[0] + " vs " + expectedLeft + ")");
            assertTrue(Math.abs(bounds[2] - expectedRight) <= 3, "and ends where it does ("
                    + bounds[2] + " vs " + expectedRight + ")");
            assertTrue(Math.abs((bounds[1] + bounds[3]) / 2.0F - centreY) <= 2,
                    "and it is centred on the row");
        } finally {
            surface.close();
        }
    }

    @Test
    void aSelectionAcrossLinesPaintsOnBoth() {
        setUp();
        Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        try {
            List<String> lines = List.of("abc", "def");
            field.text = "abcdef";
            field.caret = 1;
            field.highlight = 5;
            float lineH = 20.0F;
            float centreY = 60.0F;
            pointer.drawSelection(surface.getCanvas(), inputFont(), lines, 0, 2, 10.0F, centreY, lineH);

            int[] bounds = paintedBounds(surface);
            assertNotNull(bounds);
            assertTrue(bounds[1] <= centreY - lineH / 2.0F + 1, "the first covered row is painted");
            assertTrue(bounds[3] >= centreY + lineH / 2.0F - 1, "and so is the second");
        } finally {
            surface.close();
        }
    }

    @Test
    void aCollapsedSelectionPaintsNothing() {
        setUp();
        Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        try {
            field.text = "abcdef";
            field.caret = 3;
            field.highlight = 3;
            pointer.drawSelection(surface.getCanvas(), inputFont(), List.of("abcdef"), 0, 1, 10.0F, 40.0F, 20.0F);
            assertNull(paintedBounds(surface), "an empty selection must not paint a band");

            field.caret = 2;
            field.highlight = 5;
            field.present = false;
            pointer.drawSelection(surface.getCanvas(), inputFont(), List.of("abcdef"), 0, 1, 10.0F, 40.0F, 20.0F);
            assertNull(paintedBounds(surface), "and neither does one with no field behind it");
        } finally {
            surface.close();
        }
    }

    private static int[] paintedBounds(Surface surface) {
        Bitmap bitmap = new Bitmap();
        try {
            bitmap.allocN32Pixels(SURFACE_W, SURFACE_H);
            assertTrue(surface.readPixels(bitmap, 0, 0), "could not read the raster surface back");
            byte[] px = bitmap.readPixels();
            int x1 = Integer.MAX_VALUE;
            int y1 = Integer.MAX_VALUE;
            int x2 = -1;
            int y2 = -1;
            for (int y = 0; y < SURFACE_H; y++) {
                for (int x = 0; x < SURFACE_W; x++) {
                    if ((px[(y * SURFACE_W + x) * 4 + 3] & 0xFF) != 0) {
                        x1 = Math.min(x1, x);
                        x2 = Math.max(x2, x);
                        y1 = Math.min(y1, y);
                        y2 = Math.max(y2, y);
                    }
                }
            }
            return x2 < 0 ? null : new int[]{x1, y1, x2, y2};
        } finally {
            bitmap.close();
        }
    }
}
