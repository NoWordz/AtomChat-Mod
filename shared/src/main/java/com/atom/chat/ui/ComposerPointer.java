package com.atom.chat.ui;

import com.atom.chat.page.ProfilePage;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;

import java.util.List;

/**
 * The composer's hidden field and the pointer around it (D21, second half): where
 * the native IME window has to be anchored, which text index a virtual point maps
 * to, the drag-selection state and the selection highlight.
 *
 * <p><b>The IME anchor is the reason this half exists.</b> The field is what
 * anchors Minecraft's native IME composition window, and it computes its screen
 * caret as its own X plus the <em>vanilla</em> font's width of the whole prefix.
 * AtomChat draws the committed text with Skia instead, so the field's X is
 * shifted by exactly that difference: the Skia caret position (bar X + text inset
 * + Skia prefix width) minus the vanilla prefix width. Get the sign or the font
 * wrong and the pre-edit box floats away from the text — which is why the
 * arithmetic is kept whole here and driven, in the test, by a stub vanilla width
 * rather than by a guess.</p>
 *
 * <p><b>The signature editor borrows the field</b> (D18a), and then the anchor
 * parks on the signature row: the same prefix compensation, but measured in the
 * signature font, with y/width/height taken from that row's own geometry. Whether
 * the page is editing arrives as a fact ({@link Host#signatureAnchor()}); the
 * arithmetic stays here.</p>
 *
 * <p><b>Hit-testing steps by code point.</b> A per-character loop would return
 * indices <em>inside</em> surrogate pairs, and the next keystroke would split an
 * emoji in half. Both the row walk and the per-character widths therefore advance
 * by {@code Character.charCount}.</p>
 *
 * <p>Ports on {@link Host}: the design density, the window's GUI scale, the
 * vanilla font's prefix width, the signature anchor and the int clamp.
 * {@link Field} carries every {@code EditBox} call and the IMBlocker bridge — that
 * port is what makes the anchor arithmetic and the hit-testing testable without a
 * game (an {@code EditBox} cannot even be constructed offline). {@link ComposerInput}
 * (D21a) is this half's only other dependency, and it is read-only here.</p>
 *
 * <p>One copy, in the shared layer: with those two ports this class names no
 * Minecraft API, and the types it drives ({@link ComposerInput}, {@link UiLayout},
 * {@link UiTokens}, {@link SkiaDraw}) already live here.</p>
 */
public final class ComposerPointer {
    /**
     * The hidden field, as this half sees it. Each method maps to one original
     * expression; the four geometry setters stay apart because the original wrote
     * them in a fixed order with other work between two of them.
     */
    public interface Field {
        /** {@code input != null} */
        boolean present();

        /** {@code input.setX(x)} */
        void setFieldX(int x);

        /** {@code input.setY(y)} */
        void setFieldY(int y);

        /** {@code input.setWidth(w)} */
        void setFieldWidth(int w);

        /** {@code input.setHeight(h)} */
        void setFieldHeight(int h);

        /** {@code input.cursorPos} */
        int cursorPosition();

        /** {@code input.highlightPos} */
        int highlightPosition();

        /** {@code IMBlockerCompat.setCommandMode(input, commandMode)} */
        void setCommandMode(boolean commandMode);
    }

    /** The shell's own dependencies of this half. */
    public interface Host {
        /** The design density every virtual→GUI conversion divides by. */
        double density();

        /** The window's GUI scale. */
        double guiScale();

        /** {@code client.font.width(prefix)} — the vanilla font's prefix width. */
        int vanillaPrefixWidth(String prefix);

        /**
         * D18a's signature anchor for the borrowed field, or null when the page is
         * not editing (the shell asks the page; the arithmetic stays here).
         */
        ProfilePage.SignatureAnchor signatureAnchor();

        /** {@code Mth.clamp(value, lo, hi)} */
        int clampInt(int value, int lo, int hi);
    }

    private final ComposerInput composer;
    private final Host host;
    private final Field field;

    private boolean inputDragging;
    private int inputDragAnchor = -1;
    /** Last IME conversion state pushed to IMBlocker (English = typing a command). */
    private boolean imeEnglishState;

    public ComposerPointer(ComposerInput composer, Host host, Field field) {
        this.composer = composer;
        this.host = host;
        this.field = field;
    }

    // ---------------------------------------------------------- the drag state

    public boolean dragging() {
        return inputDragging;
    }

    public void setDragging(boolean value) {
        inputDragging = value;
    }

    /** The press anchor a shift-click extends from, or -1 when there is none. */
    public int dragAnchor() {
        return inputDragAnchor;
    }

    public void setDragAnchor(int index) {
        inputDragAnchor = index;
    }

    // ------------------------------------------------------------- the anchor

    public void positionField(UiLayout layout) {
        double density = host.density();
        double scaleFactor = host.guiScale();
        // While the signature editor borrows the field, the IME anchor parks on
        // the signature row instead of the docked input bar: x keeps the same
        // vanilla-prefix-width compensation, but the Skia side measures the
        // prefix in the signature font, and y/width/height come from the edit
        // row's own geometry (top of the line = baseline minus the row's line
        // height). The page answers null when not editing, and then the
        // composer path below runs exactly as before.
        ProfilePage.SignatureAnchor signAnchor = host.signatureAnchor();
        if (signAnchor != null) {
            String current = composer.text();
            int caret = composer.caretIndex();
            String wholePrefix = current.substring(0, Math.min(caret, current.length()));
            Font signFont = FontManager.font(UiTokens.PROFILE_SIGN_FONT);
            float skiaSignPrefixVirtual = SkiaFontRenderer.getStringWidth(signFont, wholePrefix);
            int desiredGuiX = (int) Math.round((signAnchor.textX() + skiaSignPrefixVirtual)
                    * density / scaleFactor);
            int vanillaWholePrefixGuiWidth = host.vanillaPrefixWidth(wholePrefix);
            field.setFieldX(desiredGuiX - vanillaWholePrefixGuiWidth);
            field.setFieldY((int) Math.round(
                    (signAnchor.baselineY() - signAnchor.lineH()) * density / scaleFactor));
            field.setFieldWidth((int) Math.max(10.0F,
                    Math.round(signAnchor.textMaxW() * density / scaleFactor)));
            field.setFieldHeight((int) Math.round(signAnchor.lineH() * density / scaleFactor));
            return;
        }
        // The hidden EditBox is what anchors the native IME composition window.
        // EditBox computes its screen caret as fieldX + vanilla-font prefix
        // width, while AtomChat draws the committed text with Skia. Shift the
        // field's X so EditBox.getScreenX(caret) lands exactly on the Skia caret
        // (no visible gap before the IME pre-edit box).
        String current = composer.text();
        int caret = composer.caretIndex();
        String wholePrefix = current.substring(0, Math.min(caret, current.length()));
        String linePrefix = linePrefixAt(layout, caret);
        Font inputFont = FontManager.font(UiTokens.FONT_INPUT);
        float skiaLinePrefixVirtual = SkiaFontRenderer.getStringWidth(inputFont, linePrefix);
        int desiredGuiX = (int) Math.round((layout.inputBar.x() + UiTokens.INPUT_TEXT_X + skiaLinePrefixVirtual)
                * density / scaleFactor);
        int vanillaWholePrefixGuiWidth = host.vanillaPrefixWidth(wholePrefix);
        field.setFieldX(desiredGuiX - vanillaWholePrefixGuiWidth);
        // IMBlocker bridge: English IME while a command is being typed
        // (e33chat parity). Change-guarded — the reflection call only fires
        // when the command/native state actually flips.
        boolean commandMode = current.startsWith("/");
        if (commandMode != imeEnglishState) {
            imeEnglishState = commandMode;
            field.setCommandMode(commandMode);
        }
        field.setFieldY((int) Math.round(composer.caretLineTopY() * density / scaleFactor));
        field.setFieldWidth((int) Math.max(10.0F, Math.round((layout.inputBar.w() - UiTokens.INPUT_TEXT_X * 2.0F) * density / scaleFactor)));
        field.setFieldHeight((int) Math.round(composer.lineHeight() * density / scaleFactor));
    }

    /** Skia width of the committed text before the caret on the caret's wrapped line. */
    public String linePrefixAt(UiLayout layout, int caret) {
        List<String> lines = composer.wrappedInput(layout.inputTextMaxWidth());
        if (lines.isEmpty()) {
            return "";
        }
        int line = ComposerInput.caretLine(lines, caret);
        int lineStart = 0;
        for (int i = 0; i < line; i++) {
            lineStart += lines.get(i).length();
        }
        int col = host.clampInt(caret - lineStart, 0, lines.get(line).length());
        return lines.get(line).substring(0, col);
    }

    // ------------------------------------------------------------- the hit test

    /**
     * Maps a virtual point inside the input text area to a text index. Rows
     * are the Skia-wrapped lines; a point left of a character's midpoint
     * selects that character, past the end selects the line end.
     */
    public int caretIndexAt(UiLayout layout, float vmx, float vmy) {
        List<String> lines = composer.wrappedInput(layout.inputTextMaxWidth());
        if (lines.isEmpty()) {
            return 0;
        }
        int total = lines.size();
        int shown = Math.min(UiTokens.INPUT_MAX_LINES, total);
        int from = Math.min(composer.scrollLine(), Math.max(0, total - shown));
        float lineH = composer.lineHeight();
        float firstTop = layout.inputTextCenterY - lineH / 2.0F;
        int row = host.clampInt((int) Math.floor((vmy - firstTop) / lineH), 0, shown - 1);
        int line = Math.min(from + row, total - 1);
        int lineStart = 0;
        for (int i = 0; i < line; i++) {
            lineStart += lines.get(i).length();
        }
        String s = lines.get(line);
        float dx = vmx - (layout.inputBar.x() + UiTokens.INPUT_TEXT_X);
        Font inputFont = FontManager.font(UiTokens.FONT_INPUT);
        float acc = 0.0F;
        // Code-point steps: a per-char loop returns indices inside surrogate
        // pairs, and typing afterwards would split an emoji in half.
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            int chars = Character.charCount(cp);
            float cw = SkiaFontRenderer.getStringWidth(inputFont, s.substring(i, i + chars));
            if (dx < acc + cw / 2.0F) {
                return lineStart + i;
            }
            acc += cw;
            i += chars;
        }
        return lineStart + s.length();
    }

    // ------------------------------------------------------------- the selection

    /**
     * Paints the selection highlight per wrapped line. Drawn inside the panel's
     * own transform, over the text row, so the row's own colour shows through —
     * the tint is deliberately translucent.
     */
    public void drawSelection(Canvas canvas, Font font, List<String> lines, int from, int shown,
                              float textX, float centerY, float lineH) {
        if (!field.present() || lines.isEmpty()) {
            return;
        }
        int len = composer.text().length();
        int a = host.clampInt(field.cursorPosition(), 0, len);
        int b = host.clampInt(field.highlightPosition(), 0, len);
        int selStart = Math.min(a, b);
        int selEnd = Math.max(a, b);
        if (selStart >= selEnd) {
            return;
        }

        for (int i = from; i < from + shown && i < lines.size(); i++) {
            int lineStart = 0;
            for (int j = 0; j < i; j++) {
                lineStart += lines.get(j).length();
            }
            int lineLen = lines.get(i).length();
            int lineEnd = lineStart + lineLen;
            if (selEnd <= lineStart || selStart >= lineEnd) {
                continue;
            }
            int c0 = Math.max(0, Math.min(selStart - lineStart, lineLen));
            int c1 = Math.max(0, Math.min(selEnd - lineStart, lineLen));
            if (c0 >= c1) {
                continue;
            }
            String line = lines.get(i);
            float x0 = textX + SkiaFontRenderer.getStringWidth(font, line.substring(0, c0));
            float x1 = textX + SkiaFontRenderer.getStringWidth(font, line.substring(0, c1));
            float cy = centerY + (i - from) * lineH;
            SkiaDraw.drawRoundedRect(canvas, x0, cy - lineH / 2.0F, Math.max(1.0F, x1 - x0), lineH,
                    UiTokens.radius(2), 0xE02D6FD6);
        }
    }
}
