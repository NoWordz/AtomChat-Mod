package com.atom.chat.ui;

import com.atom.chat.platform.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ComposerInput}, offline: the text triple with its cap and focus order,
 * the wrap cache's keys, the caret mapping, the bar's height animation and the
 * re-stick rule.
 *
 * <p><b>The field is a stub, and that is the point.</b> An {@code EditBox} cannot
 * be constructed without a running game, so the port is what makes these rules
 * testable at all: the stub records every call in order, which is how "the focus
 * flag, then the screen focus, then the field focus" and "256 characters is the
 * cap" are asserted as facts rather than read off the source.</p>
 *
 * <p><b>Scope, stated up front.</b> The wrap and caret tests run against the real
 * {@code SkiaFontRenderer}/{@code FontManager}, so their expectations come from
 * measured widths, not from guessed numbers. NOT covered: the real field's own
 * behaviour, the IME anchor and the hit/selection half (D21b), and the shell's
 * own call sites — those are the D21 probe list.</p>
 */
class ComposerInputTest {
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

    private static final UiLayout LAYOUT = UiLayout.of(0.0F, 0.0F, 420.0F, 820.0F);

    /** The hidden field, standing in for the real one. */
    private static final class StubField implements ComposerInput.Field {
        final List<String> calls = new ArrayList<>();
        boolean present = true;
        String value = "";
        int caret;
        boolean fieldFocused;

        @Override
        public boolean present() {
            calls.add("present");
            return present;
        }

        @Override
        public String text() {
            calls.add("text");
            return value;
        }

        @Override
        public void setText(String text) {
            calls.add("setText:" + text);
            value = text;
        }

        @Override
        public int caretPosition() {
            calls.add("caretPosition");
            return caret;
        }

        @Override
        public boolean focused() {
            calls.add("focused");
            return fieldFocused;
        }

        @Override
        public void setScreenFocus() {
            calls.add("setScreenFocus");
        }

        @Override
        public void fieldFocus() {
            calls.add("fieldFocus");
        }

        @Override
        public void fieldFocusGuarded() {
            calls.add("fieldFocusGuarded");
        }

        @Override
        public void fieldBlur() {
            calls.add("fieldBlur");
        }

        @Override
        public void moveCaretTo(int pos) {
            calls.add("moveCaretTo:" + pos);
            caret = pos;
        }
    }

    private static final class StubHost implements ComposerInput.Host {
        UiLayout layout = LAYOUT;
        ScrollController scroll = new ScrollController();
        float dt = 16.0F;

        @Override
        public UiLayout layout() {
            return layout;
        }

        @Override
        public ScrollController currentScroll() {
            return scroll;
        }

        @Override
        public float frameDt() {
            return dt;
        }

        @Override
        public int clampInt(int value, int lo, int hi) {
            return Math.max(lo, Math.min(hi, value));
        }
    }

    private StubField field;
    private StubHost host;
    private ComposerInput composer;

    private void setUp() {
        field = new StubField();
        host = new StubHost();
        composer = new ComposerInput(host, field);
    }

    // ------------------------------------------------------------- 1. the text

    @Test
    void theTextFieldIsEmptyWhenThereIsNoField() {
        setUp();
        field.present = false;
        assertEquals("", composer.text(), "no field, no text");
        composer.setText("hello");
        assertEquals("", field.value, "and a write goes nowhere");
        assertFalse(field.calls.contains("setText:hello"));
    }

    @Test
    void writingNullWritesAnEmptyDraft() {
        setUp();
        composer.setText(null);
        assertEquals("", field.value);
        composer.setText("hi");
        assertEquals("hi", field.value);
    }

    @Test
    void appendingStopsAtTheChatLimit() {
        setUp();
        field.value = "x".repeat(253);
        composer.append("12345");
        assertEquals(253, field.value.length(), "253 + 5 would be 258: past the 256 cap, so it is dropped");

        composer.append("123");
        assertEquals(256, field.value.length(), "253 + 3 lands exactly on the cap and is applied");

        composer.append("y");
        assertEquals(256, field.value.length(), "one more character would break the chat limit");
    }

    @Test
    void appendingTakesFocusInTheBorrowOrder() {
        setUp();
        composer.append("@Alice ");
        assertEquals(1, field.calls.stream().filter("setScreenFocus"::equals).count());
        assertTrue(composer.focused(), "the composer is focused after an insert");
        List<String> tail = field.calls.subList(field.calls.indexOf("setScreenFocus"), field.calls.size());
        assertEquals(List.of("setScreenFocus", "fieldFocus"), tail,
                "screen focus, then the field's own focus — the order the borrows use");
    }

    @Test
    void appendingToAnAbsentFieldDoesNothingAtAll() {
        setUp();
        field.present = false;
        composer.setFocused(false);
        composer.append("@Bob ");
        assertEquals(List.of("present"), field.calls, "the guard returns before touching anything");
        assertFalse(composer.focused(), "and it does not steal focus either");
    }

    @Test
    void theCaretGoesToTheEndOfTheDraft() {
        setUp();
        field.value = "hello";
        composer.caretToEnd();
        assertEquals(5, field.caret);
        assertTrue(field.calls.contains("moveCaretTo:5"));
    }

    // --------------------------------------------------------------- 2. focus

    @Test
    void theFocusStepIsFlagThenScreenThenField() {
        setUp();
        composer.setFocused(false);
        composer.focus();
        assertTrue(composer.focused());
        assertEquals(List.of("setScreenFocus", "fieldFocus"), field.calls,
                "the flag is this class's own, then the screen focus, then the field focus");
    }

    @Test
    void theGuardedFocusStepIsItsOwnShape() {
        setUp();
        composer.focusGuarded();
        assertTrue(composer.focused());
        assertEquals(List.of("setScreenFocus", "fieldFocusGuarded"), field.calls,
                "the signature borrow's guarded form is kept apart from the plain one");
    }

    @Test
    void thePerFrameReadBackMirrorsTheField() {
        setUp();
        field.fieldFocused = true;
        composer.syncFocusedFromField();
        assertTrue(composer.focused());
        assertTrue(composer.fieldFocused());

        field.fieldFocused = false;
        composer.syncFocusedFromField();
        assertFalse(composer.focused());
        assertFalse(composer.fieldFocused());

        field.present = false;
        composer.setFocused(true);
        composer.syncFocusedFromField();
        assertFalse(composer.focused(), "no field means nothing is focused");
        assertFalse(composer.fieldFocused());
    }

    @Test
    void aReadOnlyPageBlursTheField() {
        setUp();
        composer.setFocused(true);
        composer.blurOnReadOnlyPage(true);
        assertFalse(composer.focused());
        assertTrue(field.calls.contains("fieldBlur"));

        setUp();
        composer.setFocused(true);
        composer.blurOnReadOnlyPage(false);
        assertTrue(composer.focused(), "a writable page keeps the composer focused");
        assertFalse(field.calls.contains("fieldBlur"));
    }

    // ------------------------------------------------------------ 3. the caret

    @Test
    void theCaretIndexIsClampedIntoTheDraft() {
        setUp();
        field.value = "hello";
        field.caret = 3;
        assertEquals(3, composer.caretIndex());

        field.caret = 99;
        assertEquals(5, composer.caretIndex(), "a stale field caret cannot point past the draft");

        field.caret = -5;
        assertEquals(0, composer.caretIndex());

        field.present = false;
        assertEquals(0, composer.caretIndex(), "no field, no caret");
    }

    @Test
    void theCaretLineMapsByAccumulatedLineLengths() {
        List<String> lines = List.of("abc", "de", "f");
        assertEquals(0, ComposerInput.caretLine(lines, 0), "the caret at the very start");
        assertEquals(0, ComposerInput.caretLine(lines, 3), "a caret at a line's end stays on that line");
        assertEquals(1, ComposerInput.caretLine(lines, 4), "one past it moves to the next");
        assertEquals(1, ComposerInput.caretLine(lines, 5));
        assertEquals(2, ComposerInput.caretLine(lines, 6));
        assertEquals(2, ComposerInput.caretLine(lines, 99), "past the end clamps to the last line");
    }

    @Test
    void theCaretLineHandlesEmptyInputAndEmptyLines() {
        assertEquals(0, ComposerInput.caretLine(List.of(), 0), "no lines at all");
        assertEquals(0, ComposerInput.caretLine(List.of(""), 0), "one empty line");
        List<String> withEmpty = List.of("ab", "", "cd");
        assertEquals(0, ComposerInput.caretLine(withEmpty, 2));
        // An empty line adds no length, so the index right after "ab" belongs to
        // the line following it — the accumulation's own behaviour, kept as-is.
        assertEquals(2, ComposerInput.caretLine(withEmpty, 3));
        assertEquals(2, ComposerInput.caretLine(withEmpty, 4));
    }

    @Test
    void theCaretScrollWindowFollowsTheCaretAndClampsToTheEnds() {
        setUp();
        int max = UiTokens.INPUT_MAX_LINES;
        composer.scrollToCaret(max - 1, max);
        assertEquals(0, composer.scrollLine(), "text that fits never scrolls");

        composer.scrollToCaret(max + 4, max + 5);
        assertEquals(5, composer.scrollLine(), "a caret below the window pushes it down");

        composer.scrollToCaret(1, max + 5);
        assertEquals(1, composer.scrollLine(), "and a caret above it pulls it up");

        composer.scrollToCaret(max + 99, max + 5);
        assertEquals(5, composer.scrollLine(), "the window never scrolls past the last page");
    }

    // ---------------------------------------------------------- 4. wrap cache

    @Test
    void theWrapCacheIsKeyedOnBothTextAndWidth() {
        setUp();
        List<String> first = composer.wrappedInput("hello world", 100.0F);
        assertSame(first, composer.wrappedInput("hello world", 100.0F), "same text and width: the cache is reused");
        assertNotSame(first, composer.wrappedInput("hello world!", 100.0F), "a new text re-wraps");
        assertNotSame(first, composer.wrappedInput("hello world", 140.0F), "and so does a new width");
        assertSame(composer.wrappedInput("hello world", 140.0F),
                composer.wrappedInput("hello world", 140.0F), "the new key is cached in turn");
    }

    @Test
    void theOneArgumentWrapMeasuresTheFieldsOwnDraft() {
        setUp();
        field.value = "the quick brown fox jumps over the lazy dog";
        List<String> lines = composer.wrappedInput(LAYOUT.inputTextMaxWidth());
        assertFalse(lines.isEmpty());
        assertEquals(String.join("", lines).replace(" ", ""),
                field.value.replace(" ", ""),
                "wrapping keeps every non-space character");
    }

    @Test
    void wrappingAnEmptyDraftYieldsNoLines() {
        setUp();
        field.value = "";
        assertEquals(List.of(), composer.wrappedInput(LAYOUT.inputTextMaxWidth()));
    }

    // ------------------------------------------------------- 5. the bar height

    @Test
    void aReadOnlyOrMissingDraftNeedsNoExtraHeight() {
        setUp();
        long text = "a very long draft that certainly wraps at this width, twice over".length();
        String draft = "a".repeat((int) text);
        assertEquals(0.0F, composer.extraFor(draft, true, 100.0F), "a read-only page needs no room");
        assertEquals(0.0F, composer.extraFor(null, false, 100.0F), "and neither does a page with no composer");
        assertTrue(composer.extraFor(draft, false, 100.0F) > 0.0F, "a wrapping draft needs room");
    }

    @Test
    void theExtraHeightIsOneLineLessThanTheWrappedLinesCappedAtTheMaximum() {
        setUp();
        String draft = "a".repeat(400);
        float maxWidth = 120.0F;
        int lines = composer.wrappedInput(draft, maxWidth).size();
        assertTrue(lines > 1, "this fixture must wrap for the assertion to mean anything");
        int shown = Math.min(UiTokens.INPUT_MAX_LINES, lines);
        assertEquals((shown - 1) * composer.lineHeight(), composer.extraFor(draft, false, maxWidth), 0.001F,
                "the bar grows to at most INPUT_MAX_LINES lines and then scrolls instead");
    }

    @Test
    void theBarHeightAnimatesTowardTheWrappedHeight() {
        setUp();
        field.value = "a".repeat(400);
        assertEquals(0.0F, composer.extraH(), "the bar starts flat");

        UiLayout updated = composer.updateLayout(LAYOUT);
        assertSame(LAYOUT, updated, "the layout comes back from the shell's own factory");

        float target = composer.extraFor(field.value, false, LAYOUT.inputTextMaxWidth());
        assertTrue(target > 0.0F, "the fixture must need extra height");
        for (int i = 0; i < 200; i++) {
            composer.updateLayout(LAYOUT);
        }
        assertEquals(target, composer.extraH(), 0.5F, "the animation lands on the wrapped height");
    }

    @Test
    void theTransitionFloorRaisesTheBarForTheOtherPagesDraft() {
        setUp();
        field.value = "";
        String otherPage = "a".repeat(400);
        float floor = composer.extraFor(otherPage, false, LAYOUT.inputTextMaxWidth());
        assertTrue(floor > 0.0F);

        composer.updateLayout(LAYOUT, field.value, floor);
        composer.updateLayout(LAYOUT, field.value, floor);
        assertTrue(composer.extraH() > 0.0F,
                "a page that does not own the field still raises the bar (the transition floor)");
    }

    @Test
    void aShrinkingBarReSticksAListThatWasPinnedToTheBottom() {
        setUp();
        field.value = "a".repeat(400);
        host.scroll.setContent(2000.0F, 400.0F);
        host.scroll.scrollToBottom(false);
        for (int i = 0; i < 200; i++) {
            composer.updateLayout(LAYOUT);
        }
        assertTrue(composer.extraH() > 0.0F);
        float pinnedTarget = host.scroll.getTarget();

        field.value = "";
        for (int i = 0; i < 200; i++) {
            composer.updateLayout(LAYOUT);
        }
        assertEquals(0.0F, composer.extraH(), 0.5F, "the bar shrank back");
        assertEquals(pinnedTarget, host.scroll.getTarget(), 0.001F,
                "a list pinned to the bottom is re-stuck, so the newest row cannot slide away");
    }

    @Test
    void aListScrolledAwayFromTheBottomIsLeftAlone() {
        setUp();
        field.value = "a".repeat(400);
        host.scroll.setContent(2000.0F, 400.0F);
        host.scroll.scrollTo(0.0F, false);
        for (int i = 0; i < 200; i++) {
            composer.updateLayout(LAYOUT);
        }
        float awayTarget = host.scroll.getTarget();

        field.value = "";
        for (int i = 0; i < 200; i++) {
            composer.updateLayout(LAYOUT);
        }
        assertEquals(awayTarget, host.scroll.getTarget(), 0.001F,
                "a list the player scrolled up is not yanked to the bottom");
    }

    @Test
    void theCaretLineTopIsTheWrappedRowTheCaretSitsOn() {
        setUp();
        field.value = "a".repeat(400);
        for (int i = 0; i < 200; i++) {
            composer.updateLayout(LAYOUT);
        }
        float first = composer.caretLineTopY();
        field.caret = 0;
        assertTrue(first > 0.0F, "the top edge sits below the bar's centre for a multi-line draft");

        field.value = "";
        composer.updateLayout(LAYOUT);
        assertEquals(LAYOUT.inputTextCenterY - composer.lineHeight() / 2.0F, composer.caretLineTopY(), 0.001F,
                "an empty draft puts the caret's line on the bar's centre");
    }
}
