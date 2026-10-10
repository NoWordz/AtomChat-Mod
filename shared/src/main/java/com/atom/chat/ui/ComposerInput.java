package com.atom.chat.ui;

import com.atom.chat.font.FontManager;
import com.atom.chat.render.Animator;
import com.atom.chat.render.SkiaFontRenderer;

import java.util.List;

/**
 * The composer's own state and surface (D21, first half): the text the shared
 * field holds, the focus flag, the caret and the wrap/height geometry every other
 * domain asks about.
 *
 * <p><b>This half is the composer's public surface, not its drawing.</b> D23's
 * event handlers, D25's page body, D10's layout, D16/D18a's borrows and D19's
 * mention row all reach the same handful of questions — what does the field hold,
 * is it focused, where is the caret, how tall must the bar be — so those live
 * here with the state they read (the wrap cache, the animated extra height, the
 * caret's scroll line).</p>
 *
 * <p><b>Why the field is a port.</b> The hidden {@code EditBox} is a Minecraft
 * type, and an {@code EditBox} cannot even be constructed in an offline test
 * (its constructor wants {@code Minecraft.getInstance().font}). Every call this
 * half makes on it is therefore one method on {@link Field}, one-to-one with the
 * original expression — that is what lets the 256-character cap, the focus
 * order, the wrap cache keys, the caret mapping and the height animation be
 * tested without a game. The shell keeps the only implementation of the port, so
 * it stays the single place that knows the field exists.</p>
 *
 * <p>Two facts about the wrap and the height. The wrap cache is keyed on
 * <em>both</em> the text and the width, and a transition measures two different
 * drafts — so the cache cannot assume the field's own text. And the bar's extra
 * height animates: {@code UiMotion.approach} is unitless and leaves a
 * multi-hundred-millisecond tail on a pixel-valued target, so the height runs
 * through an {@link Animator} that guarantees {@code INPUT_GROW_MS}.</p>
 *
 * <p>Ports on {@link Host}: the settled layout the composer reads, the active
 * scroll controller (the re-stick rule), this frame's dt and the int clamp the
 * caret mapping uses. {@link Field} carries every {@code EditBox}/
 * {@code Screen.setFocused} call. Nothing else here is a Minecraft name, which is
 * why this class — and its test — can live in the shared layer as one copy.</p>
 */
public final class ComposerInput {
    /**
     * The hidden text field, as this half sees it. Every method maps to one
     * original expression ({@code input.getValue()}, {@code input.setValue(x)},
     * {@code input != null}, …) — nothing is merged, so the shell's one
     * implementation is a literal transcription and the guard shapes stay
     * distinct where the original had them distinct.
     */
    public interface Field {
        /** {@code input != null} */
        boolean present();

        /** {@code input.getValue()} — unguarded: callers check {@link #present()} first. */
        String text();

        /** {@code input.setValue(text)} */
        void setText(String text);

        /** {@code input.getCursorPosition()} */
        int caretPosition();

        /** {@code input.isFocused()} */
        boolean focused();

        /** {@code setFocused(input)} — the screen's focused widget. */
        void setScreenFocus();

        /** {@code input.setFocused(true)} */
        void fieldFocus();

        /** {@code if (input != null) { input.setFocused(true); }} — the guarded form. */
        void fieldFocusGuarded();

        /** {@code input.setFocused(false)} */
        void fieldBlur();

        /** {@code input.moveCursorTo(pos, false)} */
        void moveCaretTo(int pos);
    }

    /** The shell's own dependencies of this half. */
    public interface Host {
        /** The settled chat layout. */
        UiLayout layout();

        /** D11: the active scroll controller — the "pinned to the bottom" test. */
        ScrollController currentScroll();

        /** This frame's dt. */
        float frameDt();

        /** {@code Mth.clamp(value, lo, hi)}. */
        int clampInt(int value, int lo, int hi);
    }

    private final Host host;
    private final Field field;

    private boolean inputFocused = true;
    private float inputExtraH;
    /**
     * Tracks the height transition as start/end/time rather than per-frame lerp:
     * UiMotion.approach is unitless, so on a pixel-valued target it only covers a
     * fraction of the distance per frame and leaves a multi-hundred-ms tail.
     * Animator guarantees the bar reaches its target height within INPUT_GROW_MS.
     */
    private final Animator inputHeightAnim = new Animator(t -> t);
    private int inputScrollLine;
    private String inputWrapText;
    private float inputWrapWidth = -1.0F;
    private List<String> inputWrapCache;

    public ComposerInput(Host host, Field field) {
        this.host = host;
        this.field = field;
    }

    // ------------------------------------------------------------------ focus

    /** The composer's focus flag, as the shell's own gates read it. */
    public boolean focused() {
        return inputFocused;
    }

    /** Sets the flag alone (the original's bare {@code inputFocused = …}). */
    public void setFocused(boolean value) {
        inputFocused = value;
    }

    /** {@code input != null && input.isFocused()} — the field's own focus. */
    public boolean fieldFocused() {
        return field.present() && field.focused();
    }

    /**
     * The borrows' focus step: the flag, the screen focus and the field focus, in
     * that order — the shape three of the original sites spelled out inline.
     */
    public void focus() {
        inputFocused = true;
        field.setScreenFocus();
        field.fieldFocus();
    }

    /**
     * The same step with the field focus guarded, which is how the signature
     * borrow (D18a) wrote it. Kept apart from {@link #focus()} rather than merged:
     * the original really had two shapes, and the guarded one is the only place
     * the difference is reachable.
     */
    public void focusGuarded() {
        inputFocused = true;
        field.setScreenFocus();
        field.fieldFocusGuarded();
    }

    /** Per-frame read-back of the field's focus into the flag. */
    public void syncFocusedFromField() {
        inputFocused = field.present() && field.focused();
    }

    /** Forced blur on a read-only private page — the shell's per-frame gate. */
    public void blurOnReadOnlyPage(boolean readOnly) {
        if (readOnly && field.present()) {
            field.fieldBlur();
            inputFocused = false;
        }
    }

    // ------------------------------------------------------------------- text

    public String text() {
        return field.present() ? field.text() : "";
    }

    public void setText(String text) {
        if (field.present()) {
            field.setText(text == null ? "" : text);
        }
    }

    /**
     * Appends at the end of the draft. The cap is the chat limit: a longer draft
     * could never be typed into the field anyway. Focus is taken afterwards, in
     * the borrow's order, because inserting text means the player is typing here.
     */
    public void append(String text) {
        if (!field.present()) {
            return;
        }
        String current = field.text();
        if (current.length() + text.length() <= 256) {
            field.setText(current + text);
        }
        inputFocused = true;
        field.setScreenFocus();
        field.fieldFocus();
    }

    public void caretToEnd() {
        if (field.present()) {
            field.moveCaretTo(text().length());
        }
    }

    // ---------------------------------------------------------------- geometry

    /** The bar's extra height the shell's layout has to account for. */
    public float extraH() {
        return inputExtraH;
    }

    /** The first wrapped line the field is scrolled to. */
    public int scrollLine() {
        return inputScrollLine;
    }

    public float lineHeight() {
        return SkiaFontRenderer.getHeight(FontManager.font(UiTokens.FONT_INPUT));
    }

    /** Wrapped input text, cached until the text or the available width changes. */
    public List<String> wrappedInput(float maxWidth) {
        return wrappedInput(text(), maxWidth);
    }

    /**
     * Wraps an explicit composer text. A transition draws two pages with two
     * different drafts, so the wrap cannot assume the shared field's text; the
     * cache stays keyed on the text and width, exactly as before.
     */
    public List<String> wrappedInput(String text, float maxWidth) {
        if (inputWrapCache == null || inputWrapWidth != maxWidth || !text.equals(inputWrapText)) {
            inputWrapText = text;
            inputWrapWidth = maxWidth;
            inputWrapCache = SkiaFontRenderer.wrap(FontManager.font(UiTokens.FONT_INPUT), text, maxWidth);
        }
        return inputWrapCache;
    }

    /** Extra input-bar height a page's own draft needs (read-only pages need none). */
    public float extraFor(String draft, boolean readOnly, float maxWidth) {
        if (draft == null || readOnly) {
            return 0.0F;
        }
        int lines = Math.min(UiTokens.INPUT_MAX_LINES,
                Math.max(1, wrappedInput(draft, maxWidth).size()));
        return (lines - 1) * lineHeight();
    }

    /**
     * The composer's height for this frame. Wrapping is done here, before the
     * message list is measured, so whatever the bar gains the list gives up. When
     * the list shrinks under a view that was pinned to the bottom, re-stick it,
     * otherwise the newest message would slide out of sight.
     */
    public UiLayout updateLayout(UiLayout current) {
        return updateLayout(current, text(), 0.0F);
    }

    /**
     * Wraps the given draft, with a floor on the bar's extra height. The floor is
     * what a transition needs: its two pages are drawn in one layout, so a second
     * line belonging to the page that does not own the field would otherwise be
     * clipped until the nav landed and the field switched.
     */
    public UiLayout updateLayout(UiLayout current, String draft, float minExtra) {
        float lineH = lineHeight();
        List<String> lines = wrappedInput(draft, current.inputTextMaxWidth());
        int targetLines = Math.min(UiTokens.INPUT_MAX_LINES, Math.max(1, lines.size()));
        float targetExtra = Math.max(minExtra, (targetLines - 1) * lineH);
        ScrollController activeScroll = host.currentScroll();
        if (Math.abs(targetExtra - inputExtraH) > 0.5F
                && activeScroll.getTarget() >= activeScroll.getMaxScroll() - 3.0F) {
            activeScroll.stickToBottom();
        }
        inputHeightAnim.animateTo(UiMotion.INPUT_GROW_MS, targetExtra);
        inputHeightAnim.update(host.frameDt());
        inputExtraH = inputHeightAnim.getValue();
        return host.layout();
    }

    // ------------------------------------------------------------------ caret

    public int caretIndex() {
        return field.present() ? host.clampInt(field.caretPosition(), 0, text().length()) : 0;
    }

    /**
     * Absolute index of the line holding the caret. wrap() drops the whitespace
     * at a break point, so line lengths can sum to slightly less than the full
     * text — the mapping is exact everywhere except right at a break.
     */
    public static int caretLine(List<String> lines, int caret) {
        int pos = 0;
        for (int i = 0; i < lines.size(); i++) {
            pos += lines.get(i).length();
            if (caret <= pos) {
                return i;
            }
        }
        return Math.max(0, lines.size() - 1);
    }

    /** Keeps the caret's line inside the visible window, clamping to the ends. */
    public void scrollToCaret(int caretLine, int totalLines) {
        int max = UiTokens.INPUT_MAX_LINES;
        if (totalLines <= max) {
            inputScrollLine = 0;
            return;
        }
        if (caretLine < inputScrollLine) {
            inputScrollLine = caretLine;
        } else if (caretLine > inputScrollLine + max - 1) {
            inputScrollLine = caretLine - max + 1;
        }
        inputScrollLine = Math.max(0, Math.min(inputScrollLine, totalLines - max));
    }

    /** Virtual-space top edge of the line the caret sits on, for IME anchoring. */
    public float caretLineTopY() {
        UiLayout l = host.layout();
        float lineH = lineHeight();
        List<String> lines = wrappedInput(l.inputTextMaxWidth());
        if (lines.isEmpty()) {
            return l.inputTextCenterY - lineH / 2.0F;
        }
        int line = caretLine(lines, caretIndex());
        int shown = Math.min(UiTokens.INPUT_MAX_LINES, lines.size());
        int from = Math.min(inputScrollLine, lines.size() - shown);
        int row = Math.max(0, Math.min(line - from, shown - 1));
        return l.inputTextCenterY + row * lineH - lineH / 2.0F;
    }
}
