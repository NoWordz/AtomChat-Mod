package com.atom.chat.ui;

/**
 * Pure layout math: given the panel rect, derives every chrome rect. Rendering
 * (AtomChatScreen.drawPanel) and input hit-testing (mouseClicked/mouseScrolled)
 * must both read from here so the two can never drift, and tests can verify
 * geometry without launching the game.
 */
public final class UiLayout {
    /**
     * Which chrome a page gets:
     * <ul>
     *   <li>{@code CHAT} — composer at the bottom, no tab bar (world/private).</li>
     *   <li>{@code ROOT} — bottom tab bar, no composer (chat list / settings home).</li>
     *   <li>{@code DETAIL} — neither: the list runs to the panel bottom, and the
     *       header's back affordance is the only way out (settings sub-pages).</li>
     * </ul>
     */
    public enum Mode { CHAT, ROOT, DETAIL }

    public final float panelX;
    public final float panelY;
    public final float panelW;
    public final float panelH;
    /** Extra height currently added to the input bar while the text wraps. */
    public final float inputExtraH;
    /** Reserved height for the reply banner (0 when no quote is pending). */
    public final float replyH;

    public final Rect header;
    /** Bottom tab bar (root pages only; zero-size on detail pages). */
    public final Rect tabBar;
    /**
     * Section-chip bar on a settings detail page; zero-size elsewhere. Same
     * "one row of equal cells inside a padded bar" shape as {@link #tabBar}, so
     * the chips inherit the shell's spacing instead of inventing their own.
     */
    public final Rect chipBar;
    public final Rect list;
    public final Rect replyBar;
    public final Rect inputBar;
    public final Rect imageBtn;
    public final Rect emojiBtn;
    public final Rect sendBtn;
    /** Quick-phrase toggle: fourth button between emoji and send. */
    public final Rect phraseBtn;
    /** Vertical center of the input text's FIRST visible line. */
    public final float inputTextCenterY;

    private UiLayout(float panelX, float panelY, float panelW, float panelH, float inputExtraH, float replyH, Mode mode) {
        this.panelX = panelX;
        this.panelY = panelY;
        this.panelW = panelW;
        this.panelH = panelH;
        this.inputExtraH = Math.max(0.0F, inputExtraH);
        this.replyH = Math.max(0.0F, replyH);

        // Header is an inset card (same style as the input bar); its edge gap
        // mirrors PANEL_BOTTOM_PAD so top and bottom breathing space match.
        this.header = new Rect(panelX + UiTokens.LIST_PAD_X, panelY + UiTokens.PANEL_BOTTOM_PAD,
                panelW - UiTokens.LIST_PAD_X * 2.0F, UiTokens.HEADER_HEIGHT);
        float listTop = this.header.bottom() + UiTokens.PANEL_TOP_GAP;
        // The message list's visible area ends at the CURRENT input bar top, so
        // when the draft wraps to a second line the list gives up exactly the
        // height the bar gains. Content is therefore never painted underneath
        // the translucent composer — no images can ghost through it.
        float inputH = UiTokens.INPUT_HEIGHT + this.inputExtraH;
        float inputY = panelY + panelH - inputH - UiTokens.PANEL_BOTTOM_PAD;

        this.tabBar = mode == Mode.ROOT
                ? new Rect(panelX + UiTokens.LIST_PAD_X,
                panelY + panelH - UiTokens.TAB_BAR_H - UiTokens.PANEL_BOTTOM_PAD,
                panelW - UiTokens.LIST_PAD_X * 2.0F,
                UiTokens.TAB_BAR_H)
                : new Rect(0, 0, 0, 0);
        float bottomOfContent = switch (mode) {
            case CHAT -> inputY;
            case ROOT -> this.tabBar.y();
            case DETAIL -> panelY + panelH - UiTokens.PANEL_BOTTOM_PAD;
        };
        this.list = new Rect(panelX + UiTokens.LIST_PAD_X, listTop,
                panelW - UiTokens.LIST_PAD_X * 2.0F,
                Math.max(0.0F, bottomOfContent - listTop));

        // Section chips share the tab bar's shape: a full-width bar of equal
        // cells with TAB_EDGE_PAD inside, so chip spacing and alignment come
        // from the same tokens the bottom tabs use. Detail pages only.
        this.chipBar = mode == Mode.DETAIL
                ? new Rect(this.list.x(), this.list.y(), this.list.w(),
                UiTokens.CHIP_PILL_H + UiTokens.TAB_EDGE_PAD * 2.0F)
                : new Rect(0, 0, 0, 0);

        if (mode == Mode.CHAT) {
            float replyY = inputY - this.replyH;
            this.replyBar = this.replyH > 0.0F
                    ? new Rect(panelX + UiTokens.LIST_PAD_X, replyY,
                    panelW - UiTokens.LIST_PAD_X * 2.0F, this.replyH)
                    : new Rect(panelX + UiTokens.LIST_PAD_X, replyY, 0.0F, 0.0F);
            this.inputBar = new Rect(panelX + UiTokens.LIST_PAD_X, inputY,
                    panelW - UiTokens.LIST_PAD_X * 2.0F, inputH);

            float rowLeft = inputBar.x + UiTokens.INPUT_ROW_PAD;
            float rowRight = inputBar.x + inputBar.w - UiTokens.INPUT_ROW_PAD;
            // The row axis is the centre of the first s(30) band inside the
            // padding, so the taller square keys grow symmetrically around the
            // line the shorter Send capsule sits on. Everything anchored here
            // keeps the left and right insets mirrored, which is the equality
            // UiLayoutTest asserts.
            float rowCenterY = inputBar.y + UiTokens.INPUT_ROW_PAD + UiTokens.BUTTON_H / 2.0F;
            // Both families centre on rowCenterY. Centring each on its own box
            // instead put the two shapes 3.75 apart on the same row, which is
            // exactly the misalignment UiLayoutTest's "buttons share one row"
            // assertion catches.
            float keyTop = rowCenterY - UiTokens.COMPOSER_KEY_SIZE / 2.0F;
            float sendTop = rowCenterY - UiTokens.BUTTON_H / 2.0F;
            this.imageBtn = new Rect(rowLeft, keyTop,
                    UiTokens.COMPOSER_KEY_SIZE, UiTokens.COMPOSER_KEY_SIZE);
            this.emojiBtn = new Rect(
                    rowLeft + UiTokens.COMPOSER_KEY_SIZE + UiTokens.COMPOSER_KEY_GAP,
                    keyTop, UiTokens.COMPOSER_KEY_SIZE, UiTokens.COMPOSER_KEY_SIZE);
            this.phraseBtn = new Rect(
                    rowLeft + (UiTokens.COMPOSER_KEY_SIZE + UiTokens.COMPOSER_KEY_GAP) * 2.0F,
                    keyTop, UiTokens.COMPOSER_KEY_SIZE, UiTokens.COMPOSER_KEY_SIZE);
            // Send keeps the wider, shorter capsule and is centred on the same
            // axis; rowRight has already given up INPUT_ROW_PAD, so subtracting
            // only BUTTON_W leaves exactly that much on the right.
            this.sendBtn = new Rect(rowRight - UiTokens.BUTTON_W, sendTop,
                    UiTokens.BUTTON_W, UiTokens.BUTTON_H);

            // Measured from the row's own height rather than from a button's, so
            // the text cannot be pushed down by a change to either family's size
            // (which is what put the placeholder on the bar's bottom edge).
            this.inputTextCenterY = inputBar.y + UiTokens.INPUT_ROW_H + UiTokens.s(20);
        } else {
            this.replyBar = new Rect(0, 0, 0, 0);
            this.inputBar = new Rect(0, 0, 0, 0);
            this.imageBtn = new Rect(0, 0, 0, 0);
            this.emojiBtn = new Rect(0, 0, 0, 0);
            this.sendBtn = new Rect(0, 0, 0, 0);
            this.phraseBtn = new Rect(0, 0, 0, 0);
            this.inputTextCenterY = 0.0F;
        }
    }

    /**
     * One equal cell inside {@link #chipBar}, inset on all four sides exactly
     * like the bottom tab bar's capsule. Rendering and hit-testing both call
     * this, so a chip can never drift from where it is drawn.
     */
    public Rect chipRect(int index, int count) {
        if (index < 0 || count <= 0 || chipBar.w() <= 0.0F) {
            return new Rect(0, 0, 0, 0);
        }
        float cellW = chipBar.w() / count;
        float inset = UiTokens.TAB_EDGE_PAD;
        return new Rect(chipBar.x() + cellW * index + inset, chipBar.y() + inset,
                cellW - inset * 2.0F, chipBar.h() - inset * 2.0F);
    }

    public static UiLayout of(float panelX, float panelY, float panelW, float panelH) {
        return new UiLayout(panelX, panelY, panelW, panelH, 0.0F, 0.0F, Mode.CHAT);
    }

    public static UiLayout of(float panelX, float panelY, float panelW, float panelH, float inputExtraH) {
        return of(panelX, panelY, panelW, panelH, inputExtraH, 0.0F);
    }

    public static UiLayout of(float panelX, float panelY, float panelW, float panelH, float inputExtraH, float replyH) {
        return new UiLayout(panelX, panelY, panelW, panelH, inputExtraH, replyH, Mode.CHAT);
    }

    public static UiLayout ofRoot(float panelX, float panelY, float panelW, float panelH) {
        return new UiLayout(panelX, panelY, panelW, panelH, 0.0F, 0.0F, Mode.ROOT);
    }

    /**
     * A pushed page with no composer and no tab bar: the list keeps the height
     * the root layout would have handed to the tab bar.
     */
    public static UiLayout ofDetail(float panelX, float panelY, float panelW, float panelH) {
        return new UiLayout(panelX, panelY, panelW, panelH, 0.0F, 0.0F, Mode.DETAIL);
    }

    public Rect rect() {
        return new Rect(panelX, panelY, panelW, panelH);
    }

    /**
     * Width the input text may occupy. Independent of how tall the bar is, so
     * it is safe to query before the wrap (and therefore the extra height) is
     * recomputed.
     */
    public float inputTextMaxWidth() {
        return inputBar.w() - UiTokens.INPUT_TEXT_X * 2.0F;
    }

    public record Rect(float x, float y, float w, float h) {
        public float right() {
            return x + w;
        }

        public float bottom() {
            return y + h;
        }

        public boolean contains(float px, float py) {
            return px >= x && px <= right() && py >= y && py <= bottom();
        }

        public boolean contains(Rect other) {
            return other.x >= x && other.right() <= right() && other.y >= y && other.bottom() <= bottom();
        }
    }
}
