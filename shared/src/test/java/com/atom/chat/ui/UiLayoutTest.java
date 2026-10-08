package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout formula verification without launching the game. Any new UI rule
 * (alignment, breathing space, no-overlap) gets an assertion here.
 */
class UiLayoutTest {
    private static final float EPS = 0.01F;

    private static void assertSane(UiLayout l) {
        UiLayout.Rect panel = l.rect();

        // Everything lives inside the panel.
        assertTrue(panel.contains(l.header), "header inside panel");
        assertTrue(panel.contains(l.list), "list inside panel");
        assertTrue(panel.contains(l.inputBar), "input bar inside panel");
        assertTrue(panel.contains(l.imageBtn), "image button inside panel");
        assertTrue(panel.contains(l.emojiBtn), "emoji button inside panel");
        assertTrue(panel.contains(l.sendBtn), "send button inside panel");

        // Buttons never overlap each other.
        assertTrue(l.imageBtn.right() <= l.emojiBtn.x() + EPS, "image/emoji do not overlap");
        assertTrue(l.emojiBtn.right() <= l.phraseBtn.x() + EPS, "emoji/phrase do not overlap");
        assertTrue(l.phraseBtn.right() <= l.sendBtn.x() + EPS, "phrase/send do not overlap");

        // The row carries two families on one line: three square action keys and
        // the wider Send capsule. They share the row AXIS and the action square,
        // never a box. Asserting that Send's height equalled a key's is what
        // pinned the composer keys to the capsule's s(30) and let them drift away
        // from the header button, so the contract stated here is the real one:
        // keys are square, they take ACTION_BUTTON_SIZE, and Send is neither.
        assertEquals(l.imageBtn.y(), l.emojiBtn.y(), EPS, "keys share one row");
        assertEquals(l.imageBtn.y(), l.phraseBtn.y(), EPS, "phrase key shares the row");
        float keyAxis = l.imageBtn.y() + l.imageBtn.h() / 2.0F;
        assertEquals(keyAxis, l.sendBtn.y() + l.sendBtn.h() / 2.0F, EPS,
                "send capsule centred on the key row axis");
        assertEquals(l.imageBtn.w(), l.imageBtn.h(), EPS, "composer key is square");
        assertEquals(l.imageBtn.h(), l.emojiBtn.h(), EPS, "composer keys same height");
        assertEquals(l.imageBtn.h(), l.phraseBtn.h(), EPS, "phrase key same height");
        assertEquals(UiTokens.ACTION_BUTTON_SIZE, l.imageBtn.w(), EPS,
                "key side comes from the shared action button token");
        assertEquals(UiTokens.ACTION_BUTTON_SIZE, l.imageBtn.h(), EPS,
                "key side comes from the shared action button token");
        // Send is the wider capsule and deliberately shorter than the square.
        assertTrue(l.sendBtn.w() > l.imageBtn.w(), "send is the wider capsule");
        assertTrue(l.sendBtn.h() < l.imageBtn.h(), "send is shorter than the action square");
        assertEquals(UiTokens.BUTTON_W, l.sendBtn.w(), EPS, "send keeps BUTTON_W");
        assertEquals(UiTokens.BUTTON_H, l.sendBtn.h(), EPS, "send keeps BUTTON_H");
        // The taller family owns the row band, and the bar's top gap is the
        // header family's edge inset; the shorter capsule centres inside.
        assertEquals(l.inputBar.y() + UiTokens.EDGE_CONTROL_INSET, l.imageBtn.y(), EPS,
                "the keys' top gap is the header family's edge inset");
        assertEquals(l.inputBar.y() + UiTokens.EDGE_CONTROL_INSET + UiTokens.INPUT_ROW_H, l.imageBtn.bottom(), EPS,
                "the taller family fills the row band");
        assertEquals(l.imageBtn.y() - l.sendBtn.y(), l.sendBtn.bottom() - l.imageBtn.bottom(), EPS,
                "the shorter capsule is inset equally at both ends of the band");
        // Two gap families by design: the square keys sit at the header's
        // EDGE_CONTROL_INSET off the bar's left edge, while the Send capsule
        // keeps its own designed INPUT_ROW_PAD on the right — the row does
        // not mirror any more, and that is the contract.
        assertEquals(UiTokens.EDGE_CONTROL_INSET, l.imageBtn.x() - l.inputBar.x(), EPS,
                "keys sit at the header family's edge inset");
        assertEquals(UiTokens.INPUT_ROW_PAD, l.inputBar.right() - l.sendBtn.right(), EPS,
                "send keeps its designed right gap");

        // Breathing space below the input bar.
        assertEquals(panel.bottom() - l.inputBar.bottom(), UiTokens.PANEL_BOTTOM_PAD, EPS, "bottom breathing space");

        // The message list stays inside the panel and has room. A grown input
        // bar yields the list's bottom, so no list content sits under the
        // translucent composer.
        assertTrue(panel.contains(l.list), "list inside panel");
        assertTrue(l.list.h() > 0, "list has room");
    }

    @Test
    void defaultSize() {
        assertSane(UiLayout.of(24, 100, 525, 975));
    }

    @Test
    void grownInputBarYieldsListHeight() {
        // One wrapped line of the input font, measured on the shipped bundled
        // font. The old 29.0 here was a Latin fallback measurement; see
        // UiTokens.INPUT_LINE_H_REF for the arithmetic behind 36.2.
        float lineH = UiTokens.INPUT_LINE_H_REF;
        UiLayout base = UiLayout.of(24, 100, 525, 975);

        for (float extra : new float[]{0.0F, lineH * 0.5F, lineH}) {
            UiLayout grown = UiLayout.of(24, 100, 525, 975, extra);
            assertSane(grown);
            // The bar grows upward: its bottom edge is nailed to the panel.
            assertEquals(base.inputBar.bottom(), grown.inputBar.bottom(), EPS,
                    "bar bottom stays put, extra " + extra);
            assertEquals(UiTokens.INPUT_HEIGHT + extra, grown.inputBar.h(), EPS,
                    "bar height grows by the requested amount");
            // The list top stays put and the list gives up exactly the extra
            // height, so its bottom always meets the grown bar's top — nothing
            // is ever painted underneath the translucent bar.
            assertEquals(base.list.y(), grown.list.y(), EPS, "list top stays put");
            assertEquals(base.list.h() - extra, grown.list.h(), EPS, "list yields the extra height");
            assertEquals(grown.inputBar.y(), grown.list.bottom(), EPS,
                    "list bottom meets the grown bar top, no overlap");
            // Both families ride up with the bar's top edge, on one shared axis.
            assertEquals(grown.inputBar.y() + UiTokens.EDGE_CONTROL_INSET, grown.imageBtn.y(), EPS,
                    "action row pinned to the bar top at the header inset");
            assertEquals(grown.imageBtn.y() + grown.imageBtn.h() / 2.0F,
                    grown.sendBtn.y() + grown.sendBtn.h() / 2.0F, EPS,
                    "both families still share the row axis, extra " + extra);
        }
    }

    /**
     * The approved baseline for the whole action family: the header drew its
     * button at s(36) = 45 px before the token existed, and 45 is what the
     * composer keys were moved onto. Written down here because nothing in the
     * suite pinned the header family's size at all, which is how the two
     * families drifted apart under a green build.
     */
    private static final float HEADER_BASELINE_SIDE = 45.0F;

    /**
     * The cross-family link the suite was missing: the header's action button and
     * the composer keys must be one size. The header rect is built in each
     * screen's {@code backButton()} (it needs the header card's own y), not in
     * UiLayout, so a shared-side test cannot compare the two rects; what is
     * checkable from here is the token both sides read, which is why this pins
     * the token, its approved value, and UiLayout's use of it.
     */
    @Test
    void headerActionAndComposerKeysShareOneSquare() {
        // The token carries the header's old local s(36), unchanged.
        assertEquals(HEADER_BASELINE_SIDE, UiTokens.ACTION_BUTTON_SIZE, EPS,
                "the action square is the approved 45 px baseline");
        UiLayout l = UiLayout.of(24, 100, 525, 975);
        // Every composer key is that one square, so the header button and the
        // keys are the same size by construction rather than by coincidence.
        assertEquals(UiTokens.ACTION_BUTTON_SIZE, l.imageBtn.w(), EPS, "image key is the action square");
        assertEquals(UiTokens.ACTION_BUTTON_SIZE, l.imageBtn.h(), EPS, "image key is the action square");
        assertEquals(UiTokens.ACTION_BUTTON_SIZE, l.emojiBtn.w(), EPS, "emoji key is the action square");
        assertEquals(UiTokens.ACTION_BUTTON_SIZE, l.emojiBtn.h(), EPS, "emoji key is the action square");
        assertEquals(UiTokens.ACTION_BUTTON_SIZE, l.phraseBtn.w(), EPS, "phrase key is the action square");
        assertEquals(UiTokens.ACTION_BUTTON_SIZE, l.phraseBtn.h(), EPS, "phrase key is the action square");
        // And the fourth button is not: Send stayed the wider capsule.
        assertTrue(l.sendBtn.w() > UiTokens.ACTION_BUTTON_SIZE, "send is wider than the action square");
        assertTrue(l.sendBtn.h() < UiTokens.ACTION_BUTTON_SIZE, "send is shorter than the action square");
        // The action square is the taller family, so it owns the row band and the
        // row grew with it; a taller BUTTON_H would fail here.
        assertEquals(UiTokens.ACTION_BUTTON_SIZE, UiTokens.INPUT_ROW_H, EPS,
                "the action square is the taller family, so it owns the row band");
    }

    /**
     * Approved baseline for the header family's edge inset: s(4) = 5 px, the
     * gap the header's back / filter keys keep to their card. The composer's
     * square keys joined that family, so their left and top gap against the
     * bar is this number — the v0.2.15-era 10 px row pad no longer insets the
     * keys. The Send capsule deliberately does NOT follow: it keeps the
     * s(8) = 10 px side gap of its own shipped design.
     */
    private static final float HEADER_EDGE_INSET_BASELINE = 5.0F;

    @Test
    void squareKeysSitAtTheHeaderInsetSendKeepsItsDesignedGap() {
        UiLayout l = UiLayout.of(24, 100, 525, 975);
        // The square keys are the header family: left and top gap both equal
        // EDGE_CONTROL_INSET, the approved 5 px the header keys sit at.
        assertEquals(UiTokens.EDGE_CONTROL_INSET, l.imageBtn.x() - l.inputBar.x(), EPS,
                "keys' left gap is the header edge inset");
        assertEquals(UiTokens.EDGE_CONTROL_INSET, l.imageBtn.y() - l.inputBar.y(), EPS,
                "keys' top gap is the header edge inset");
        assertEquals(HEADER_EDGE_INSET_BASELINE, l.imageBtn.y() - l.inputBar.y(), EPS,
                "the header family's inset is the approved 5 px baseline");
        // Send keeps its designed spacing: the 10 px right gap it shipped with,
        // and centring on the row band axis rather than the keys' inset.
        assertEquals(UiTokens.INPUT_ROW_PAD, l.inputBar.right() - l.sendBtn.right(), EPS,
                "send's right gap stays the shipped 10 px");
        float axis = l.inputBar.y() + UiTokens.EDGE_CONTROL_INSET + UiTokens.INPUT_ROW_H / 2.0F;
        assertEquals(axis - UiTokens.BUTTON_H / 2.0F, l.sendBtn.y(), EPS,
                "send stays centred on the row band axis");
        // Its top gap is therefore the band centring plus the keys' inset:
        // (INPUT_ROW_H - BUTTON_H)/2 + EDGE_CONTROL_INSET = 3.75 + 5 = 8.75 px.
        assertEquals((UiTokens.INPUT_ROW_H - UiTokens.BUTTON_H) / 2.0F + UiTokens.EDGE_CONTROL_INSET,
                l.sendBtn.y() - l.inputBar.y(), EPS,
                "send's top gap is 8.75 px: band centring over the key inset");
        // The bar's height is the new parts: key inset above, row band, text
        // band, Send-family pad below — 5 + 45 + 45 + 10 = 105.
        assertEquals(UiTokens.EDGE_CONTROL_INSET + UiTokens.INPUT_ROW_H
                + UiTokens.INPUT_TEXT_BAND + UiTokens.INPUT_ROW_PAD, UiTokens.INPUT_HEIGHT, EPS,
                "INPUT_HEIGHT is built from the two families' parts");
        assertEquals(105.0F, UiTokens.INPUT_HEIGHT, EPS,
                "one-line bar baseline is 105 px (was 110)");
    }

    /**
     * Recurrence guard for the clipped placeholder. The input text is clipped at
     * the bar's inner bottom edge, so a band that no longer holds the whole line
     * block plus its descenders cuts the text silently - the shipped font reports
     * INPUT_LINE_H_REF (36.2 px), while the old test file hardcoded 29.0 and
     * therefore could not notice. Both real states are checked: one line, and the
     * two lines INPUT_MAX_LINES allows, each with the bar grown by its own line.
     */
    @Test
    void inputTextBlockKeepsItsMarginsAtBothEnds() {
        float lineH = UiTokens.INPUT_LINE_H_REF;
        float margin = (UiTokens.INPUT_TEXT_BAND - lineH) / 2.0F;
        assertTrue(margin > 0.0F, "the band leaves the line box room at both ends");
        for (int lines = 1; lines <= UiTokens.INPUT_MAX_LINES; lines++) {
            float extra = (lines - 1) * lineH;
            UiLayout l = UiLayout.of(24, 100, 525, 975, extra);
            float clipTop = l.inputTextCenterY - lineH / 2.0F;
            float clipBottom = l.inputBar.bottom() - UiTokens.INPUT_ROW_PAD;
            // The band is the token plus the bar's own growth, so the tokens and
            // the layout formula cannot disagree about the room the text has.
            assertEquals(UiTokens.INPUT_TEXT_BAND + extra, clipBottom - l.imageBtn.bottom(), EPS,
                    "the text band measures what the token says, " + lines + " line(s)");
            // Top: the first line's box stays clear of the action row above it.
            assertEquals(margin, clipTop - l.imageBtn.bottom(), EPS,
                    "top margin, " + lines + " line(s)");
            // Bottom: the last line's descender stays clear of the bar's inner
            // edge by the same margin, so the block reads as centred in its band.
            float lastBottom = l.inputTextCenterY + extra + lineH / 2.0F;
            assertEquals(margin, clipBottom - lastBottom, EPS,
                    "bottom margin, " + lines + " line(s)");
            assertTrue(clipTop >= l.imageBtn.bottom(),
                    "the line never touches the action row, " + lines + " line(s)");
            assertTrue(clipBottom >= lastBottom,
                    "the descender never crosses the bar's inner edge, " + lines + " line(s)");
        }
    }

    @Test
    void messageTokensStayConsistent() {
        // The avatar column on both sides must fit inside the bubble's reserved
        // retract, or bubbles would overlap the avatars.
        assertTrue(UiTokens.BUBBLE_RETRACT > 2.0F * (UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP),
                "bubble retract covers both avatar columns");
        assertTrue(UiTokens.BUBBLE_PAD_Y > 0.0F, "bubble padding is positive");
        assertTrue(UiTokens.SYSTEM_BUBBLE_PAD_Y > 0.0F, "system bubble padding is positive");
        // The name band doubles as the name/bubble gap, so it must clear the name text.
        assertTrue(UiTokens.NAME_BAND > UiTokens.FONT_NAME, "name band clears the name text");
    }

    /**
     * The conversation list's row cards and the notification banner draw into
     * ONE column, not two equal-looking expressions: both read
     * {@link UiLayout#cardColumnX}/{@link UiLayout#cardColumnW}. This pins that
     * rule against the content column it is derived from — and states it as
     * insets, so dropping ROW_CLIP_INSET (which would make the cards exactly as
     * wide as the clip, the shape of the original banner bug) fails here instead
     * of sliding through.
     */
    @Test
    void cardColumnIsTheContentColumnInsetByTheRowClipInset() {
        UiLayout l = UiLayout.ofRoot(24.0F, 100.0F, 480.0F, 900.0F);
        float cardX = UiLayout.cardColumnX(l.panelX);
        float cardW = UiLayout.cardColumnW(l.panelW);

        assertEquals(l.list.x() + UiTokens.ROW_CLIP_INSET, cardX, EPS,
                "the card column starts one ROW_CLIP_INSET inside the list's left edge");
        assertEquals(l.list.w() - UiTokens.ROW_CLIP_INSET * 2.0F, cardW, EPS,
                "the card column is the list width less one ROW_CLIP_INSET per side");
        // The same thing twice as insets, which is what a missing
        // ROW_CLIP_INSET changes: both go to zero together.
        assertEquals(UiTokens.ROW_CLIP_INSET, cardX - l.list.x(), EPS, "left inset is ROW_CLIP_INSET");
        assertEquals(UiTokens.ROW_CLIP_INSET, l.list.right() - (cardX + cardW), EPS,
                "right inset is ROW_CLIP_INSET");
        assertTrue(UiTokens.ROW_CLIP_INSET > 0.0F, "the shared inset is a real clearance");
    }

    @Test
    void headerIsCompactAndMirrorsTopGap() {
        UiLayout l = UiLayout.of(24, 100, 525, 975);

        // Height comes from the shared token only — no local override may creep back in.
        assertEquals(UiTokens.HEADER_HEIGHT, l.header.h(), EPS, "header uses HEADER_HEIGHT");
        // The channel card must not dominate the panel; the input bar stays taller.
        assertTrue(l.header.h() < l.inputBar.h(), "header shorter than the input bar");
        // Top breathing space mirrors the bottom one.
        assertEquals(UiTokens.PANEL_BOTTOM_PAD, l.header.y() - l.rect().y(), EPS, "top gap mirrors bottom pad");
        // Centering the channel label needs room on both sides of the card.
        assertTrue(l.header.w() > UiTokens.HEADER_PAD_X * 4.0F, "header wide enough to center a label");
    }

    @Test
    void clampedSmallPanel() {
        // panel clamps to the window; simulate a short window result
        assertSane(UiLayout.of(24, 8, 400, 300));
    }

    @Test
    void veryLargePanel() {
        assertSane(UiLayout.of(24, 10, 900, 1400));
    }

    @Test
    void minimumClampStillFits() {
        // panelWidth()/panelHeight() clamp to viewport-32; the smallest realistic case
        assertSane(UiLayout.of(24, 16, 360, 280));
    }

    private static void assertSaneRoot(UiLayout l) {
        UiLayout.Rect panel = l.rect();

        assertTrue(panel.contains(l.header), "root header inside panel");
        assertTrue(panel.contains(l.tabBar), "tab bar inside panel");
        assertTrue(panel.contains(l.list), "root list inside panel");
        assertTrue(l.list.h() > 0, "root content has room");
        assertEquals(panel.bottom() - l.tabBar.bottom(), UiTokens.PANEL_BOTTOM_PAD, EPS,
                "bottom breathing space under tab bar");
        assertTrue(l.inputBar.w() == 0.0F, "input bar is not used on root pages");
        assertEquals(UiTokens.TAB_BAR_H, l.tabBar.h(), EPS, "tab bar height uses token");
        // The root list starts immediately below the header gap and ends exactly
        // where the tab bar begins.
        assertEquals(l.header.bottom() + UiTokens.PANEL_TOP_GAP, l.list.y(), EPS,
                "root list starts below the header plus PANEL_TOP_GAP");
        assertEquals(l.tabBar.y(), l.list.bottom(), EPS,
                "root list bottom meets the tab bar top");
    }

    @Test
    void rootLayoutHasTabBarAndNoInputBar() {
        UiLayout l = UiLayout.ofRoot(24, 100, 525, 975);
        assertSaneRoot(l);
    }

    @Test
    void rootTabBarNeverOverlapsHeader() {
        UiLayout l = UiLayout.ofRoot(24, 100, 525, 975);
        assertTrue(l.tabBar.y() >= l.header.bottom() + 1.0F, "tab bar below header");
    }

    @Test
    void compactRootPanelStillFits() {
        assertSaneRoot(UiLayout.ofRoot(24, 16, 360, 280));
    }

    private static void assertSaneDetail(UiLayout l) {
        UiLayout.Rect panel = l.rect();

        assertTrue(panel.contains(l.header), "detail header inside panel");
        assertTrue(panel.contains(l.list), "detail list inside panel");
        assertTrue(l.inputBar.w() == 0.0F, "input bar is not used on pushed pages");
        assertTrue(l.tabBar.w() == 0.0F, "tab bar is not used on pushed pages");
        assertTrue(l.list.h() > 0, "detail content has room");

        // Same top edge as every other page.
        assertEquals(l.header.bottom() + UiTokens.PANEL_TOP_GAP, l.list.y(), EPS,
                "detail list starts below the header plus PANEL_TOP_GAP");

        // No tab bar means the list keeps the height the root layout would have
        // handed to it — that is the whole point of a dedicated mode.
        UiLayout root = UiLayout.ofRoot(l.rect().x(), l.rect().y(), l.rect().w(), l.rect().h());
        assertEquals(root.tabBar.h(), l.list.h() - root.list.h(), EPS,
                "detail list reclaims exactly the tab bar height");

        // And it runs all the way to the panel bottom pad.
        assertEquals(panel.bottom() - UiTokens.PANEL_BOTTOM_PAD, l.list.bottom(), EPS,
                "detail list ends at the panel bottom pad");
    }

    @Test
    void detailLayoutHasNeitherComposerNorTabBar() {
        assertSaneDetail(UiLayout.ofDetail(24, 100, 525, 975));
    }

    @Test
    void compactDetailPanelStillFits() {
        assertSaneDetail(UiLayout.ofDetail(24, 16, 360, 280));
    }
}
