package com.atom.chat.ui;


import com.atom.chat.render.CornerGeometry;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.emote.EmoteImageCache;
import com.atom.chat.emote.EmoteStore;
import com.atom.chat.font.FontManager;
import com.atom.chat.net.PackSyncClient;
import com.atom.chat.pack.ServerPackStore;
import com.atom.chat.render.Animator;
import com.atom.chat.render.Easing;
import com.atom.chat.render.SkiaImages;
import com.atom.chat.render.SkiaFontRenderer;
import static com.atom.lib.design.atomchat.UiTokens.s;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.io.File;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;

/**
 * The emoji / kaomoji / emote-pack panel: state, geometry, hit-testing and
 * rendering in one self-contained class. The screen keeps only the composer
 * side effects — text insertion, sticker sending and the file picker — via
 * {@link Host}.
 *
 * <p>Pure presentation plus pure-Java storage; every method takes the world
 * chat {@link UiLayout} so geometry can never drift from the caller's frame.</p>
 */
public final class EmojiPanel {

    /** Composer side effects the panel cannot own itself. */
    public interface Host {
        /** Appends text at the caret (emoji / kaomoji insert). */
        void insert(String text);

        /** Sends a local image file as an image message (emote tap). */
        void sendSticker(Path file);

        /** Opens the image picker for the "+" add slot (screen-owned flow). */
        void pickEmoteFile();
    }

    // Fixed dark-grey skin. The emoji panel is deliberately a small world of
    // its own that does NOT follow the theme: its selected-pill white and its
    // hover washes are constants of this skin, not UiTokens values
    // (user-approved exception to the theme colour language); the base plate
    // and the drop shadow ride the shared fixed-dark tokens
    // (UiTokens.SKIN_PANEL / CHROME_SHADOW) so every fixed dark popup draws
    // the same plate. Every hover wash
    // in here stays white-based for the same reason — on this fixed dark base
    // a white wash always brightens, which is the correct hover direction on
    // this surface regardless of the user's theme polarity. The remove-button
    // red (214, 48, 48) below is a functional colour of the skin, kept as-is.
    private static final int SKIN_PANEL_BG = UiTokens.SKIN_PANEL;
    private static final int SKIN_PANEL_SHADOW = UiTokens.CHROME_SHADOW;
    /** Selected tab pill: translucent white, the v0.2.15 shipped value. */
    private static final int SKIN_PILL_FILL = Color.makeARGB(90, 255, 255, 255);
    /** Disabled "+"-slot glyph: dimmed white of the skin. */
    private static final int SKIN_DIM_TEXT = Color.makeARGB(90, 255, 255, 255);

    private static final String[] TAB_KEYS = {
            "atomchat.emoji.tab.emoji",
            "atomchat.emoji.tab.kaomoji",
            "atomchat.emoji.tab.emote"
    };

    private static final String[] EMOJIS = com.atom.chat.reaction.ReactionEmoji.ALL.toArray(new String[0]);

    private static final String[] KAOMOJI = {
            "(｡•̀ᴗ-)✧", "(๑˃̵ᴗ˂̵)و", "(๑•̀ㅂ•́)و✧", "(◍•ᴗ•◍)",
            "╰(*°▽°*)╯", "(≧∇≦)ﾉ", "(＾▽＾)", "✧٩(ˊωˋ*)و✧",
            "ฅ^•ﻌ•^ฅ", "(•ω•)", "(￣▽￣*)", "(⌒▽⌒)☆",
            "(o゜▽゜)o☆", "＼(￣▽￣)／", "(◔◡◔)", "／(=✪ x ✪=)＼",
            "¯\\_(ツ)_/¯", "(ー_ー゛)", "(￢_￢)", "(¬_¬)",
            "(⇀‸↼‶)", "(｡ŏ_ŏ)", "(・∀・)", "_(:з」∠)_",
            "(╯°□°）╯︵ ┻━┻", "(´;ω;｀)", "Σ(°△°|||)", "(◎ロ◎)",
            "(∪.∪ )...zzz",
    };

    private boolean reactionMode;
    private long pointerOpening;
    private record EmojiClick(long opening, int tab, Object action) {}
    private record StickerHit(EmoteGridLayout.Kind kind, int index, File file, boolean remove) {}
    public void openReaction() {
        pointerOpening++;
        reactionMode = true; open = true; tab = 0; scroll = 0; paintedScroll = 0; tabAnimFrom = -1; paintedTabOffset = 0;
        tabIndicatorAnim.animateTo(0, 0.0F); tabContentAnim.animateTo(0, 1.0F);
        cellHover.clear(); cellScale.clear(); pressedCellKey = -1;
    }
    private final Host host;
    /** Tutorial-only panels never resolve or scan any emote directory. */
    private final boolean sandboxMode;
    /** Local emote pack plus the joined server's read-only one; see {@link EmoteStore}. */
    private final EmoteStore emoteStore;
    /** Cached {@code server-icon.png} decode, keyed by the pack it came from. */
    private String iconHash = "";
    private Image iconImage;
    private final EmoteImageCache emoteImageCache = new EmoteImageCache();

    private boolean open;
    private int tab;
    private int scroll;
    /** Open/close fade + scale; advances in {@link #update(long)} every frame. */
    private float anim;
    // Per-cell hover fade shared by the emoji / kaomoji / emote grids.
    private final Map<Integer, Float> cellHover = new HashMap<>();
    // Per-cell press/hover scale bounce, same keys as cellHover.
    private final Map<Integer, PressScale> cellScale = new HashMap<>();
    /** Cell of the last click, still in the pressed phase of its pulse. */
    private int pressedCellKey = -1;
    private long pressedCellAtMs;
    /** How long a click keeps its cell in the pressed phase before springing back. */
    private static final long CELL_PULSE_MS = 110;
    /** Hover washes for the tab strip. */
    private final float[] tabHover = new float[3];
    /**
     * Per-tab press/hover bounce, the BottomTabBar pattern: one instance per
     * tab cell, the strip's tab width passed at update, the scale wrapping the
     * whole tab cell (wash + label), never the label alone.
     */
    private final PressScale[] tabScale = {PressScale.bounce(), PressScale.bounce(), PressScale.bounce()};
    /** Tab of the last click, still in the pressed phase of its pulse. */
    private int pressedTabKey = -1;
    private long pressedTabAtMs;
    /** How long a click keeps its tab pressed before springing back. */
    private static final long TAB_PULSE_MS = 110;
    // Tab transition: double-layer content slide + sliding indicator.
    private final Animator tabContentAnim = new Animator(Easing::easeInOutCubic);
    private final Animator tabIndicatorAnim = new Animator(Easing::easeInOutCubic);
    private int tabAnimFrom = -1;
    private int outgoingScroll;
    private float paintedTabOffset;
    private int paintedScroll;
    private int tabAnimTo = -1;

    public EmojiPanel(Host host) {
        this(host, false);
    }

    private EmojiPanel(Host host, boolean sandboxMode) {
        this.host = host;
        this.sandboxMode = sandboxMode;
        this.emoteStore = sandboxMode ? null : EmoteStore.deferred(
                 com.atom.chat.util.CacheDirs.emotesDir(),
                // Read at scan time, so a hand-edited cap is picked up on the next scan
                // instead of being frozen at construction.
                () -> com.atom.chat.config.AtomChatConfig.get().emoteMax);
    }

    /** Explicit tutorial-only construction: no local/server pack access or mutation. */
    public static EmojiPanel sandbox(Host host) {
        return new EmojiPanel(host, true);
    }

    public int diagnosticTab() { return tab; }

    public boolean isOpen() {
        return open;
    }

    /** Hover keys for the server's cells live above the local ones and never collide. */
    private static final int SERVER_HOVER_BASE = 1000;

    /** The grid model for the current sources; hover, click and draw all use it. */
    private EmoteGridLayout emoteLayout() {
        if (sandboxMode || com.atom.chat.onboarding.TutorialSandbox.active()) return new EmoteGridLayout(UiTokens.EMOTE_COLS, 0, 0);
        return new EmoteGridLayout(UiTokens.EMOTE_COLS, emoteStore.count(), emoteStore.serverCount());
    }

    /** Re-scans the local folder and re-points the server half at the installed pack. */
    private void refreshEmotes() {
        if (sandboxMode || com.atom.chat.onboarding.TutorialSandbox.active()) return;
        ServerPackStore store = ServerPackStore.current();
        // A pack with nothing to show is treated as "no distribution": no
        // server rows, no header, no status line.
        emoteStore.setServerDir(store.hasContent() ? store.emotesDir() : null);
    }

    public void close() {
        open = false;
    }

    /**
     * Screen teardown: release the natives this panel holds. Without this the
     * installed-pack icon and every decoded emote reached the finalizer only,
     * and the panel is rebuilt on each screen open.
     */
    public void dispose() {
        open = false;
        if (iconImage != null) {
            iconImage.close();
            iconImage = null;
        }
        emoteImageCache.clear();
    }

    /**
     * Toggle from the composer. Refresh the sticker files only when their grid is shown.
     */
    public void toggle() {
        reactionMode = false;
        open = !open;
        if (open) {
            pointerOpening++;
            if (tab == 2) refreshEmotes();
            cellHover.clear();
            cellScale.clear();
            pressedCellKey = -1;
        }
    }

    /** Full transient reset (leaving the world page): close and rewind scroll. */
    public void resetTransient() {
        open = false;
        scroll = 0;
        paintedScroll = 0;
    }

    /** Advances the open/close fade; called once per frame from the screen. */
    public void update(long frameDt) {
        anim = UiMotion.approach(anim, open ? 1.0F : 0.0F, frameDt,
                com.atom.chat.ui.Animations.ms(UiMotion.POPUP_MS));
    }

    /** Wheel scroll while the pointer is over the panel. */
    public void scroll(UiLayout layout, double verticalAmount) {
        scroll = Math.max(0, Math.min(
                scroll - (int) (verticalAmount * s(18)), maxScroll()));
    }

    public float paintedRadius(UiLayout layout){return UiTokens.radius(UiTokens.COMPOSER_POPUP_RADIUS, AtomChatAppearance.theme())*ComposerPopupTransform.of(bounds(layout),anim).scale();}
    public UiLayout.Rect paintedBounds(UiLayout layout){return ComposerPopupTransform.of(bounds(layout),anim).paintedBounds(bounds(layout));}
    public UiLayout.Rect bounds(UiLayout layout) { return ComposerPopupLayout.place(layout,panelW(),panelH()); }
    private ComposerPopupTransform.Point localPointer(UiLayout layout,float x,float y) {
        return ComposerPopupTransform.of(bounds(layout),anim).toLocal(x,y);
    }
    public boolean overPanel(UiLayout layout, float mx, float my) {
        var p = localPointer(layout,mx,my); return overPanelLocal(layout,p.x(),p.y());
    }
    private boolean overPanelLocal(UiLayout layout,float mx,float my) {
        float px = panelX(layout);
        float py = panelY(layout);
        return mx >= px && mx <= px + panelW() && my >= py && my <= py + panelH();
    }

    /**
     * Handles a click inside the panel. Returns the text to insert ("" = the
     * click was consumed without inserting; tabs, emote actions and gutters).
     */
    public com.atom.chat.ui.input.ClickTarget clickTarget(UiLayout layout, float mx, float my, int button,
                                                          java.util.function.Consumer<String> activate) {
        if (!open || !overPanel(layout, mx, my)) return null;
        if (button != 0) return com.atom.chat.ui.input.ClickTarget.blocked();
        var point = localPointer(layout, mx, my);
        float x = point.x(), y = point.y();
        Object action;
        Runnable press;
        if (y < panelY(layout) + UiTokens.EMOJI_TAB_H) {
            int target = tabAt(layout, x);
            if (target < 0) return com.atom.chat.ui.input.ClickTarget.blocked();
            action = target;
            press = () -> pulseTab(target);
        } else {
            if (!insideGridWidth(x, layout) || !insideGridWidth(x - paintedTabOffset, layout))
                return com.atom.chat.ui.input.ClickTarget.blocked();
            x -= paintedTabOffset;
            if (tab == 2) {
                if (sandboxMode || com.atom.chat.onboarding.TutorialSandbox.active()) {
                    action = "preview";
                    press = () -> {};
                } else {
                    StickerHit sticker = stickerHit(layout, x, y);
                    if (sticker == null) return com.atom.chat.ui.input.ClickTarget.blocked();
                    action = sticker;
                    int index = sticker.kind() == EmoteGridLayout.Kind.SERVER
                            ? SERVER_HOVER_BASE + sticker.index() : sticker.index();
                    press = () -> pulseCell(gridHoverKey(2, index));
                }
            } else {
                int index = textGridHoveredIndex(tab, x, y, layout);
                if (index < 0) return com.atom.chat.ui.input.ClickTarget.blocked();
                action = (tab == 1 ? KAOMOJI : EMOJIS)[index];
                int key = gridHoverKey(tab, index);
                press = () -> pulseCell(key);
            }
        }
        return new com.atom.chat.ui.input.ClickTarget(new EmojiClick(pointerOpening, tab, action), press,
                () -> { pressedTabKey = -1; pressedCellKey = -1; },
                () -> activate.accept(click(layout, mx, my)));
    }

    private int tabAt(UiLayout layout, float x) {
        int count = tabLabels().length;
        float stripStart = panelX(layout) + UiTokens.EMOJI_PANEL_PAD;
        float width = (panelW() - UiTokens.EMOJI_PANEL_PAD * 2.0F) / count;
        int index = (int) ((x - stripStart) / width);
        return index >= 0 && index < count ? index : -1;
    }

    public String click(UiLayout layout, float mx, float my) {
        var point = localPointer(layout,mx,my); mx=point.x(); my=point.y();
        float py = panelY(layout);
        if (!overPanelLocal(layout, mx, my)) {
            return "";
        }
        // Tab bar. The strip is inset by EMOJI_PANEL_PAD so it aligns with the
        // content grid below; the active pill then keeps a uniform s(4) inside it.
        if (my < py + UiTokens.EMOJI_TAB_H) {
            int t = tabAt(layout, mx);
            if (t >= 0) {
                // Press feedback on every tab tap, switch or re-tap alike —
                // the cell pattern: hold pressed for a beat, then rebound.
                pulseTab(t);
            }
            if (t >= 0 && t != tab) {
                int from = tab;
                outgoingScroll = scroll;
                tab = t;
                scroll = 0;
                paintedScroll = 0;
                cellHover.clear();
                cellScale.clear();
                pressedCellKey = -1;
                if (t == 2) {
                    // Rescan so files dropped into the emote dir by hand show up.
                    refreshEmotes();
                }
                startTabTransition(from, t);
                com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
            }
            return "";
        }
        if (!insideGridWidth(mx, layout)) return "";
        mx -= paintedTabOffset;
        if (!insideGridWidth(mx, layout)) return "";
        // The emote (sticker) tab has its own grid — images, an add slot and a
        // per-cell remove button. It does its work inline and never inserts text.
        if (tab == 2) {
            if (sandboxMode || com.atom.chat.onboarding.TutorialSandbox.active()) return "☺";
            return emotePanelClick(layout, mx, my);
        }
        int idx = textGridHoveredIndex(tab, mx, my, layout);
        if (idx >= 0) {
            pulseCell(gridHoverKey(tab, idx));
            return (tab == 1 ? KAOMOJI : EMOJIS)[idx];
        }
        return "";
    }

    public void render(Canvas canvas, UiLayout layout, float vmx, float vmy, long frameDt) {
        if (anim < 0.01F) {
            return;
        }
        float px = panelX(layout);
        float py = panelY(layout);
        float pw = panelW();
        float ph = panelH();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        paintedScroll = scroll;
        canvas.save();
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * anim), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(px - s(24), py - s(24), pw + s(48), ph + s(48)), layer);
            ComposerPopupTransform.of(bounds(layout),anim).apply(canvas);
            MenuPopup.drawPickerSurface(canvas, px, py, pw, ph);

            com.atom.chat.render.DotTexture.drawConnector(canvas,
                    new UiLayout.Rect(px, py, pw, ph), UiTokens.radius(14, AtomChatAppearance.theme()), layout.inputBar, layout.emojiBtn, AtomChatConfig.get().accentColor);

            // Tabs: the active pill slides between slots when the tab changes.
            tabIndicatorAnim.update(frameDt);
            tabContentAnim.update(frameDt);
            if (tabContentAnim.isDone()) {
                tabAnimFrom = -1;
            }
            updateTabHover(layout, vmx, vmy, frameDt);
            Font tabFont = FontManager.font(UiTokens.FONT_BUTTON);
            String[] labels = tabLabels();
            float tabInset = UiTokens.EMOJI_PANEL_PAD;
            float tabStripX = px + tabInset;
            float tabStripW = pw - tabInset * 2.0F;
            float tabW = tabStripW / labels.length;
            float indicator = tabIndicatorAnim.getValue();
            // The active pill keeps a uniform s(4) inset on every side of its tab
            // slot, and the whole strip is inset so it never crowds the panel's
            // rounded border (Apple-style calculated spacing).
            // The active pill leaves s(6) above and only s(2) below: the extra
            // bottom length makes the label's visual centre line up with the
            // pill's centre (the text baseline is drawn slightly low).
            // The pill rides the selected tab's spring (the BottomTabBar
            // recipe): while it slides between slots it is still the selected
            // tab's shape.
            float pillX = tabStripX + indicator * tabW + s(4);
            float pillW = tabW - s(8);
            float pillH = UiTokens.EMOJI_TAB_H - s(8);
            canvas.save();
            applyBounce(canvas, tabScale[tab].scale(),
                    pillX + pillW / 2.0F, py + s(6) + pillH / 2.0F);
            CornerGeometry.drawRoundedRect(canvas, pillX, py + s(6), pillW, pillH, UiTokens.radius(8, AtomChatAppearance.theme()),
                    SKIN_PILL_FILL);
            canvas.restore();
            for (int t = 0; t < labels.length; t++) {
                float hov = t == tab ? 0.0F : tabHover[t];
                // The bounce wraps the WHOLE tab cell — wash and label —
                // around the cell's centre, not the label alone.
                tabScale[t].begin(canvas, tabStripX + t * tabW + tabW / 2.0F,
                        py + UiTokens.EMOJI_TAB_H / 2.0F);
                try {
                    if (hov > 0.01F) {
                        float hx = tabStripX + t * tabW + s(4);
                        float hy = py + s(6);
                        float hw = tabW - s(8);
                        float hh = UiTokens.EMOJI_TAB_H - s(8);
                        CornerGeometry.drawRoundedRect(canvas, hx, hy, hw, hh, UiTokens.radius(8, AtomChatAppearance.theme()),
                                Color.makeARGB((int) (45.0F * hov), 255, 255, 255));
                    }
                    float tx = tabStripX + t * tabW;
                    // Every label — active or not — is plain text ink: the pill is a
                    // translucent white wash on the fixed dark skin (v0.2.15), not an
                    // accent fill, so there is no onAccent contrast problem to solve.
                    SkiaFontRenderer.drawTextCentered(canvas, tabFont, labels[t],
                            tx + tabW / 2.0F, py + UiTokens.EMOJI_TAB_H / 2.0F + s(2),
                            textPrimary());
                } finally {
                    canvas.restore();
                }
            }

            // Content area (clipped, scrollable). Switching tabs plays an opaque
            // push, like moving from one screen to the next: the outgoing tab is
            // pushed out as the incoming one slides in from the same direction,
            // both fully opaque and covering the full content width. A short
            // faded slide reads as a jitter, not a screen change.
            float contentX = px + UiTokens.EMOJI_PANEL_PAD;
            float contentY = py + UiTokens.EMOJI_TAB_H + s(2);
            float contentW = pw - UiTokens.EMOJI_PANEL_PAD * 2.0F;
            float contentH = contentH();
            boolean transitioning = tabAnimFrom >= 0 && tabAnimFrom != tab && !tabContentAnim.isDone();
            float tp = transitioning ? tabContentAnim.getValue() : 1F;
            float travel = tabAnimTo > tabAnimFrom ? contentW : -contentW;
            paintedTabOffset = transitioning ? travel * (1F - tp) : 0F;
            updateGridHover(layout, vmx, vmy, frameDt);
            canvas.save();
            CornerGeometry.clip(canvas, contentX, contentY, contentW, contentH, 0.0F);
            if (transitioning) {
                drawTabContent(canvas, tabAnimFrom, contentX, contentY, contentW, contentH,
                        -travel * tp, outgoingScroll, false);
            }
            drawTabContent(canvas, tab, contentX, contentY, contentW, contentH,
                    paintedTabOffset, scroll, true);
            canvas.restore();
            canvas.restore();
        }
        canvas.restore();
    }

    /** Adding an emote may overwrite an existing file of the same name, so the decode cache clears wholesale — ten entries, so it is cheap. */
    public void addEmote(Path file) {
        if (sandboxMode || com.atom.chat.onboarding.TutorialSandbox.active()) return;
        if (emoteStore.add(file.toFile())) {
            emoteImageCache.clear();
            cellHover.clear();
            cellScale.clear();
            pressedCellKey = -1;
        }
    }

    private void startTabTransition(int from, int to) {
        tabAnimFrom = from;
        tabAnimTo = to;
        paintedTabOffset = Animations.enabled()
                ? Math.copySign(panelW() - UiTokens.EMOJI_PANEL_PAD * 2F, to - from) : 0F;
        tabContentAnim.setValue(0.0F);
        tabContentAnim.animateTo(UiMotion.TAB_MS, 1.0F);
        tabIndicatorAnim.animateTo(UiMotion.TAB_MS, to);
    }

    private String[] tabLabels() {
        if (reactionMode) return new String[]{Text.translatable(TAB_KEYS[0]).getString()};
        String[] labels = new String[TAB_KEYS.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Text.translatable(TAB_KEYS[i]).getString();
        }
        return labels;
    }

    private static float panelW() {
        return UiTokens.EMOJI_COLS * UiTokens.EMOJI_CELL + UiTokens.EMOJI_PANEL_PAD * 2.0F;
    }

    private static float contentH() {
        return UiTokens.EMOJI_VISIBLE_ROWS * UiTokens.EMOJI_CELL;
    }

    private static float panelH() {
        return UiTokens.EMOJI_TAB_H + contentH() + UiTokens.EMOJI_PANEL_PAD;
    }

    /** Left-aligned with the message list inside the panel. */
    private static float panelX(UiLayout layout) {
        return ComposerPopupLayout.left(layout);
    }

    /** Sits directly above the input bar, so it follows the bar's grown height. */
    private static float panelY(UiLayout layout) {
        return ComposerPopupLayout.top(layout, panelH());
    }

    private int maxScroll() {
        if (tab == 2) {
            // Two sources share the grid now (mine plus the server's), so the
            // emote tab scrolls like the text tabs do.
            float totalH = emoteLayout().rows() * UiTokens.EMOTE_CELL;
            return Math.max(0, (int) Math.ceil(totalH - contentH()));
        }
        String[] items = tab == 1 ? KAOMOJI : EMOJIS;
        int cols = tab == 1 ? 2 : UiTokens.EMOJI_COLS;
        float itemH = tab == 1 ? UiTokens.EMOJI_KAOMOJI_ROW_H : UiTokens.EMOJI_CELL;
        int rows = (items.length + cols - 1) / cols;
        float totalH = rows * itemH;
        return Math.max(0, (int) Math.ceil(totalH - contentH()));
    }

    private int gridHoverKey(int tab, int index) {
        return tab * 1000 + index;
    }

    /** Click feedback: hold the cell pressed for a beat, then spring back. */
    private void pulseCell(int key) {
        pressedCellKey = key;
        pressedCellAtMs = System.currentTimeMillis();
        cellScale.computeIfAbsent(key, k -> PressScale.bounce());
    }

    /** Click feedback for a tab cell, the same pulse pattern as the cells. */
    private void pulseTab(int t) {
        pressedTabKey = t;
        pressedTabAtMs = System.currentTimeMillis();
    }

    /**
     * Hovered cell index for the active text tab (emoji/kaomoji), or -1 when the
     * pointer is over the tab bar, a gutter or outside the content area. Matches
     * click's geometry so highlight and hit-test never drift.
     */
    private static boolean insideGridWidth(float x, UiLayout layout) {
        float left = panelX(layout) + UiTokens.EMOJI_PANEL_PAD;
        return x >= left && x < left + panelW() - UiTokens.EMOJI_PANEL_PAD * 2F;
    }

    private int textGridHoveredIndex(int tab, float mx, float my, UiLayout layout) {
        String[] items = tab == 1 ? KAOMOJI : EMOJIS;
        float px = panelX(layout);
        float py = panelY(layout);
        float pw = panelW();
        float contentX = px + UiTokens.EMOJI_PANEL_PAD;
        float contentY = py + UiTokens.EMOJI_TAB_H + s(2);
        float contentW = pw - UiTokens.EMOJI_PANEL_PAD * 2.0F;
        float contentH = contentH();
        if (my < py + UiTokens.EMOJI_TAB_H
                || mx < contentX || mx > contentX + contentW
                || my < contentY || my > contentY + contentH) {
            return -1;
        }
        int cols = tab == 1 ? 2 : UiTokens.EMOJI_COLS;
        float itemH = tab == 1 ? UiTokens.EMOJI_KAOMOJI_ROW_H : UiTokens.EMOJI_CELL;
        int col = (int) ((mx - contentX) / (contentW / cols));
        int row = (int) ((my - contentY + paintedScroll) / itemH);
        if (col < 0 || col >= cols) {
            return -1;
        }
        int idx = row * cols + col;
        return idx >= 0 && idx < items.length ? idx : -1;
    }

    /**
     * Hovered cell index for the emote tab (0..count, count = the "+" add slot),
     * or -1 outside the content area / tab bar / gutters.
     */
    private int emoteGridHoveredIndex(float mx, float my, UiLayout layout) {
        float px = panelX(layout);
        float py = panelY(layout);
        float pw = panelW();
        float contentX = px + UiTokens.EMOJI_PANEL_PAD;
        float contentY = py + UiTokens.EMOJI_TAB_H + s(2);
        float contentW = pw - UiTokens.EMOJI_PANEL_PAD * 2.0F;
        float contentH = contentH();
        if (my < py + UiTokens.EMOJI_TAB_H
                || mx < contentX || mx > contentX + contentW
                || my < contentY || my > contentY + contentH) {
            return -1;
        }
        if (sandboxMode || com.atom.chat.onboarding.TutorialSandbox.active()) return -1;
        float colW = contentW / UiTokens.EMOTE_COLS;
        int col = (int) ((mx - contentX) / colW);
        int row = (int) ((my - contentY + paintedScroll) / UiTokens.EMOTE_CELL);
        if (col < 0 || col >= UiTokens.EMOTE_COLS) {
            return -1;
        }
        EmoteGridLayout.Cell cell = emoteLayout().cellAt(row, col);
        // Only the player's own cells and the add slot react to hover; the
        // server's section is read-only and the header is not a target.
        return switch (cell.kind()) {
            case LOCAL -> cell.index();
            case ADD -> emoteStore.count();
            // Server cells animate too (they just carry no x), so they get their
            // own key range instead of the header's "not a target".
            case SERVER -> SERVER_HOVER_BASE + cell.index();
            default -> -1;
        };
    }

    /**
     * Fades the tab-strip hover washes and advances the per-tab bounce
     * springs. The capsule geometry is the same as the active pill, so hover
     * follows the exact slots users click.
     */
    private void updateTabHover(UiLayout layout, float vmx, float vmy, long frameDt) {
        var point = localPointer(layout,vmx,vmy); vmx=point.x(); vmy=point.y();
        int hovered = -1;
        float stripW = 0.0F;
        if (open && overPanelLocal(layout, vmx, vmy)
                && vmy < panelY(layout) + UiTokens.EMOJI_TAB_H) {
            float px = panelX(layout);
            float pw = panelW();
            float inset = UiTokens.EMOJI_PANEL_PAD;
            float stripX = px + inset;
            stripW = pw - inset * 2.0F;
            if (vmx >= stripX && vmx <= stripX + stripW) {
                String[] labels = tabLabels();
                float tabW = stripW / labels.length;
                int t = (int) ((vmx - stripX) / tabW);
                if (t >= 0 && t < labels.length) {
                    hovered = t;
                }
            }
        }
        for (int i = 0; i < tabHover.length; i++) {
            tabHover[i] = UiMotion.approach(tabHover[i],
                    i == hovered ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
        }
        // The tab bounce rides the same pass: hover lifts the whole tab cell,
        // the click pulse dips it, and the width is the strip's tab slot —
        // the BottomTabBar recipe verbatim.
        long now = System.currentTimeMillis();
        boolean tabPulsing = pressedTabKey >= 0 && now - pressedTabAtMs < TAB_PULSE_MS;
        float tabW = stripW > 0.0F ? stripW / tabScale.length : 1.0F;
        for (int i = 0; i < tabScale.length; i++) {
            tabScale[i].update(i == hovered, tabPulsing && i == pressedTabKey, frameDt,
                    Animations.enabled(), tabW);
        }
        if (!tabPulsing) {
            pressedTabKey = -1;
        }
    }

    /**
     * Fades every tracked cell highlight toward its target — the pointer can only
     * hover one cell, but every other cell must still decay when the mouse moves
     * away. Called once per frame before the content layers are drawn, so both the
     * outgoing and incoming tab layers read the same per-cell state.
     */
    private void updateGridHover(UiLayout layout, float vmx, float vmy, long frameDt) {
        var point = localPointer(layout,vmx,vmy); vmx=point.x(); vmy=point.y();
        int hovered = -1;
        if (open && insideGridWidth(vmx, layout) && vmy >= panelY(layout) + UiTokens.EMOJI_TAB_H) {
            float gridX = vmx - paintedTabOffset;
            hovered = tab == 2 ? emoteGridHoveredIndex(gridX, vmy, layout) : textGridHoveredIndex(tab, gridX, vmy, layout);
        }
        int hoveredKey = hovered < 0 ? -1 : gridHoverKey(tab, hovered);
        Iterator<Map.Entry<Integer, Float>> it = cellHover.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Float> e = it.next();
            boolean isHovered = e.getKey() == hoveredKey;
            float next = UiMotion.approach(e.getValue(), isHovered ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
            if (!isHovered && next <= 0.001F) {
                it.remove();
            } else {
                e.setValue(next);
            }
        }
        if (hoveredKey >= 0 && !cellHover.containsKey(hoveredKey)) {
            cellHover.put(hoveredKey, 0.0F);
        }
        // The scale bounce rides the same key set: pressed during the click
        // pulse, hover target while the pointer stays, dropped once it is
        // back at rest away from the pointer. The cell width of the active
        // grid shapes the budget: eight emoji columns, two wide kaomoji
        // rows, six for the server emote packs.
        float cellW = (panelW() - UiTokens.EMOJI_PANEL_PAD * 2.0F)
                / (tab == 1 ? 2 : tab == 2 ? UiTokens.EMOTE_COLS : UiTokens.EMOJI_COLS);
        long now = System.currentTimeMillis();
        boolean pulsing = pressedCellKey >= 0 && now - pressedCellAtMs < CELL_PULSE_MS;
        Iterator<Map.Entry<Integer, PressScale>> scaleIt = cellScale.entrySet().iterator();
        while (scaleIt.hasNext()) {
            Map.Entry<Integer, PressScale> e = scaleIt.next();
            boolean isHovered = e.getKey() == hoveredKey;
            PressScale ps = e.getValue();
            ps.update(isHovered, pulsing && e.getKey() == pressedCellKey, frameDt,
                    Animations.enabled(), cellW);
            if (!isHovered && ps.isResting()) {
                scaleIt.remove();
            }
        }
        if (!pulsing) {
            pressedCellKey = -1;
        }
        if (hoveredKey >= 0 && !cellScale.containsKey(hoveredKey)) {
            cellScale.put(hoveredKey, PressScale.bounce());
        }
    }

    /** Draw only cells intersecting the viewport, including during a sideways tab switch. */
    private void drawTabContent(Canvas canvas, int tab, float contentX, float contentY, float contentW, float contentH,
                                float dx, int gridScroll, boolean interactive) {
        if (Math.abs(dx) >= contentW) return;
        canvas.save();
        try {
            canvas.translate(dx, 0F);
            if (tab == 2) {
                if (sandboxMode || com.atom.chat.onboarding.TutorialSandbox.active()) {
                    drawSandboxSticker(canvas, contentX, contentY, contentW / UiTokens.EMOTE_COLS, UiTokens.EMOTE_CELL);
                } else {
                    drawEmoteGrid(canvas, contentX, contentY, contentW, contentH, dx, gridScroll, interactive);
                }
            } else {
                drawTextGrid(canvas, tab, contentX, contentY, contentW, contentH, dx, gridScroll, interactive);
            }
        } finally {
            canvas.restore();
        }
    }

    /**
     * Emoji/kaomoji text grid with a per-cell hover highlight that fades in and
     * out (UiMotion.HOVER_MS), the same language as the buttons and emote cells.
     * The capsule leaves a uniform s(2) margin from the cell; emoji glyphs are
     * centred inside it while kaomoji rows keep a deliberate left padding so the
     * text never touches the capsule edge.
     */
    private void drawTextGrid(Canvas canvas, int tab, float contentX, float contentY, float contentW, float contentH,
                              float dx, int gridScroll, boolean interactive) {
        String[] items = tab == 1 ? KAOMOJI : EMOJIS;
        Font itemFont = FontManager.font(tab == 1 ? UiTokens.FONT_KAOMOJI : UiTokens.FONT_EMOJI);
        float itemH = tab == 1 ? UiTokens.EMOJI_KAOMOJI_ROW_H : UiTokens.EMOJI_CELL;
        int cols = tab == 1 ? 2 : UiTokens.EMOJI_COLS;
        float cellW = contentW / cols;
        int firstCol = Math.max(0, (int) Math.floor(-dx / cellW));
        int endCol = Math.min(cols, (int) Math.ceil((contentW - dx) / cellW));
        int firstRow = (int) (gridScroll / itemH);
        int endRow = Math.min((items.length + cols - 1) / cols,
                (int) Math.ceil((gridScroll + contentH) / itemH));
        for (int row = firstRow; row < endRow; row++) {
            float ey = contentY - gridScroll + row * itemH;
            int end = Math.min(items.length, row * cols + endCol);
            for (int i = row * cols + firstCol; i < end; i++) {
                float ex = contentX + (i % cols) * cellW;
                PressScale ps = interactive ? cellScale.get(gridHoverKey(tab, i)) : null;
                if (ps != null) {
                    ps.begin(canvas, ex + cellW / 2.0F, ey + itemH / 2.0F);
                }
                try {
                    if (interactive) {
                        float hov = cellHover.getOrDefault(gridHoverKey(tab, i), 0.0F);
                        if (hov > 0.01F) {
                            CornerGeometry.drawRoundedRect(canvas, ex + s(2), ey + s(2), cellW - s(4), itemH - s(4),
                                    UiTokens.radius(6, AtomChatAppearance.theme()),
                                    Color.makeARGB((int) (60.0F * hov), 255, 255, 255));
                        }
                    }
                    if (tab == 1) {
                        SkiaFontRenderer.drawText(canvas, itemFont, items[i], ex + s(8),
                                SkiaFontRenderer.centerBaselineY(itemFont, ey + itemH / 2.0F), textPrimary());
                    } else {
                        SkiaFontRenderer.drawTextCentered(canvas, itemFont, items[i],
                                ex + cellW / 2.0F, ey + itemH / 2.0F, textPrimary());
                    }
                } finally {
                    if (ps != null) {
                        canvas.restore();
                    }
                }
            }
        }
    }

        /**
     * Click handling for the emote (sticker) tab. Tapping one of the player's own
     * emotes sends it (upload the file, drop its CICode into the draft) and
     * closes the panel so a second tap cannot fire another upload; the trailing
     * "+" opens the picker and the hovered x deletes. Tapping one of the
     * server's emotes sends it too - it is read-only, so there is no remove
     * button to hit first. Always returns "" because nothing is inserted here.
     */
    private StickerHit stickerHit(UiLayout layout, float mx, float my) {
        float px = panelX(layout);
        float py = panelY(layout);
        float pw = panelW();
        float contentX = px + UiTokens.EMOJI_PANEL_PAD;
        float contentY = py + UiTokens.EMOJI_TAB_H + s(2);
        float contentW = pw - UiTokens.EMOJI_PANEL_PAD * 2.0F;
        float contentH = contentH();
        if (mx < contentX || mx > contentX + contentW
                || my < contentY || my > contentY + contentH) {
            return null;
        }
        float colW = contentW / UiTokens.EMOTE_COLS;
        int col = Math.max(0, Math.min(UiTokens.EMOTE_COLS - 1, (int) ((mx - contentX) / colW)));
        int row = (int) ((my - contentY + paintedScroll) / UiTokens.EMOTE_CELL);
        EmoteGridLayout.Cell cell = emoteLayout().cellAt(row, col);
        float ex = contentX + col * colW;
        float ey = contentY - paintedScroll + row * UiTokens.EMOTE_CELL;
        return switch (cell.kind()) {
            case LOCAL -> {
                float size = UiTokens.EMOTE_REMOVE_SIZE;
                boolean remove = mx >= ex + colW - size - s(2) && mx <= ex + colW - s(2)
                        && my >= ey + s(2) && my <= ey + s(2) + size;
                yield new StickerHit(cell.kind(), cell.index(), emoteStore.list().get(cell.index()), remove);
            }
            case ADD -> emoteStore.isFull() ? null : new StickerHit(cell.kind(), emoteStore.count(), null, false);
            case SERVER -> new StickerHit(cell.kind(), cell.index(), emoteStore.serverList().get(cell.index()), false);
            default -> null;
        };
    }

    private String emotePanelClick(UiLayout layout, float mx, float my) {
        if (sandboxMode || com.atom.chat.onboarding.TutorialSandbox.active()) return "☺";
        StickerHit hit = stickerHit(layout, mx, my);
        if (hit == null) return "";
        if (hit.remove()) {
            emoteStore.remove(hit.file());
            emoteImageCache.invalidate(hit.file());
            cellHover.clear();
            cellScale.clear();
            pressedCellKey = -1;
        } else if (hit.kind() == EmoteGridLayout.Kind.ADD) {
            pulseCell(gridHoverKey(2, hit.index()));
            host.pickEmoteFile();
        } else {
            close();
            host.sendSticker(hit.file().toPath());
        }
        return "";
    }

    /**
     * Emote (sticker) grid: six columns of {@code s(44)} cells. The player's own
     * emotes come first with a trailing "+" slot; when a server pack is installed
     * a header row (its icon, name and sync state) introduces that server's
     * read-only emotes. Every cell is resolved through {@link EmoteGridLayout},
     * so the drawing, the hover wash and the click handling cannot disagree
     * about where "mine" ends and "theirs" begins.
     */
    private void drawEmoteGrid(Canvas canvas, float contentX, float contentY, float contentW, float contentH,
                               float dx, int gridScroll, boolean interactive) {
        EmoteGridLayout layout = emoteLayout();
        float cell = UiTokens.EMOTE_CELL;
        float colW = contentW / UiTokens.EMOTE_COLS;
        float pad = s(4);
        List<File> emotes = emoteStore.list();
        List<File> serverEmotes = emoteStore.serverList();
        int addKey = emotes.size();
        int firstRow = (int) (gridScroll / cell);
        int endRow = Math.min(layout.rows(), (int) Math.ceil((gridScroll + contentH) / cell));
        int firstCol = Math.max(0, (int) Math.floor(-dx / colW));
        int endCol = Math.min(UiTokens.EMOTE_COLS, (int) Math.ceil((contentW - dx) / colW));
        for (int row = firstRow; row < endRow; row++) {
            float ey = contentY - gridScroll + row * cell;
            if (layout.cellAt(row, 0).kind() == EmoteGridLayout.Kind.SERVER_HEADER) {
                drawServerHeader(canvas, contentX, ey, cell, contentW);
                continue;
            }
            for (int col = firstCol; col < endCol; col++) {
                EmoteGridLayout.Cell c = layout.cellAt(row, col);
                float ex = contentX + col * colW;
                switch (c.kind()) {
                    case LOCAL -> {
                        PressScale ps = interactive ? cellScale.get(gridHoverKey(2, c.index())) : null;
                        if (ps != null) {
                            ps.begin(canvas, ex + colW / 2.0F, ey + cell / 2.0F);
                        }
                        try {
                            float hover = cellHover.getOrDefault(gridHoverKey(2, c.index()), 0.0F);
                            drawEmoteImage(canvas, emotes.get(c.index()), ex, ey, colW, cell, pad);
                            if (interactive) {
                                drawCellActions(canvas, ex, ey, colW, cell, hover);
                            }
                        } finally {
                            if (ps != null) {
                                canvas.restore();
                            }
                        }
                    }
                    case SERVER -> {
                        // Read-only, but it highlights like any other cell: the only
                        // thing missing is the x, which would promise a delete this
                        // cell cannot do.
                        PressScale ps = interactive ? cellScale.get(gridHoverKey(2, SERVER_HOVER_BASE + c.index())) : null;
                        if (ps != null) {
                            ps.begin(canvas, ex + colW / 2.0F, ey + cell / 2.0F);
                        }
                        try {
                            drawEmoteImage(canvas, serverEmotes.get(c.index()), ex, ey, colW, cell, pad);
                            if (interactive) {
                                drawHoverWash(canvas, ex, ey, colW, cell, cellHover.getOrDefault(
                                        gridHoverKey(2, SERVER_HOVER_BASE + c.index()), 0.0F));
                            }
                        } finally {
                            if (ps != null) {
                                canvas.restore();
                            }
                        }
                    }
                    case ADD -> {
                        PressScale ps = interactive ? cellScale.get(gridHoverKey(2, addKey)) : null;
                        if (ps != null) {
                            ps.begin(canvas, ex + colW / 2.0F, ey + cell / 2.0F);
                        }
                        try {
                            drawAddSlot(canvas, ex, ey, colW, cell,
                                    interactive ? cellHover.getOrDefault(gridHoverKey(2, addKey), 0.0F) : 0.0F);
                        } finally {
                            if (ps != null) {
                                canvas.restore();
                            }
                        }
                    }
                    default -> {
                    }
                }
            }

        }
    }

    /** The shared hover highlight, faded with the same curve as every other cell. */
    private void drawHoverWash(Canvas canvas, float ex, float ey, float colW, float cell, float hover) {
        if (hover > 0.01F) {
            CornerGeometry.drawRoundedRect(canvas, ex + s(2), ey + s(2), colW - s(4), cell - s(4),
                    UiTokens.radius(8, AtomChatAppearance.theme()),
                    Color.makeARGB((int) (60.0F * hover), 255, 255, 255));
        }
    }

    /** Hover wash plus the x button, for cells the player is allowed to delete. */
    private void drawCellActions(Canvas canvas, float ex, float ey, float colW, float cell, float hover) {
        drawHoverWash(canvas, ex, ey, colW, cell, hover);
        if (hover <= 0.01F) {
            return;
        }
        // The wash and the x sit above the picture, so the button can never be
        // buried under the emote itself.
        float rs = UiTokens.EMOTE_REMOVE_SIZE;
        CornerGeometry.drawRoundedRect(canvas, ex + colW - rs - s(2), ey + s(2), rs, rs,
                UiTokens.radius(4, AtomChatAppearance.theme()),
                Color.makeARGB((int) (200.0F * hover), 214, 48, 48));
        Font xFont = FontManager.font(UiTokens.FONT_QUOTE);
        SkiaFontRenderer.drawTextCentered(canvas, xFont, "×",
                ex + colW - rs / 2.0F - s(2), ey + s(2) + rs / 2.0F,
                Color.makeARGB((int) (255.0F * hover), 255, 255, 255));
    }

    /** Fits an emote into its cell, never upscaling; "?" stands in for a broken file. */
    private void drawEmoteImage(Canvas canvas, File emote, float ex, float ey, float colW, float cell, float pad) {
        Image img = emoteImageCache.image(emote);
        if (img == null) {
            Font qFont = FontManager.font(UiTokens.FONT_QUOTE);
            SkiaFontRenderer.drawTextCentered(canvas, qFont, "?", ex + colW / 2.0F, ey + cell / 2.0F,
                    textSecondary());
            return;
        }
        float avail = cell - pad * 2.0F;
        float scale = Math.min(1.0F, Math.min(avail / img.getWidth(), avail / img.getHeight()));
        float dw = Math.max(1.0F, img.getWidth() * scale);
        float dh = Math.max(1.0F, img.getHeight() * scale);
        SkiaImages.drawRoundedImage(canvas, img,
                ex + (colW - dw) / 2.0F, ey + (cell - dh) / 2.0F, dw, dh, s(6));
    }

    /** The trailing "+" slot, greyed out once the player's own pack is full. */
    private void drawAddSlot(Canvas canvas, float ex, float ey, float colW, float cell, float hover) {
        boolean disabled = emoteStore.isFull();
        if (hover > 0.01F && !disabled) {
            CornerGeometry.drawRoundedRect(canvas, ex + s(2), ey + s(2), colW - s(4), cell - s(4),
                    UiTokens.radius(8, AtomChatAppearance.theme()),
                    Color.makeARGB((int) (60.0F * hover), 255, 255, 255));
        }
        Font addFont = FontManager.font(UiTokens.FONT_EMOJI);
        SkiaFontRenderer.drawTextCentered(canvas, addFont, "+", ex + colW / 2.0F, ey + cell / 2.0F,
                disabled ? SKIN_DIM_TEXT : textPrimary());
    }

    /** A transient drawn sticker demonstrates selection without files or an emote store. */
    private static void drawSandboxSticker(Canvas canvas, float ex, float ey, float colW, float cell) {
        float pad = UiTokens.EMOJI_PANEL_PAD;
        CornerGeometry.drawRoundedRect(canvas, ex + pad, ey + pad, Math.max(0,colW-pad*2), Math.max(0,cell-pad*2),
                UiTokens.settingsRowRadius(AtomChatAppearance.theme()), 0xFF355E9C);
        Font font = FontManager.font(UiTokens.FONT_EMOJI);
        SkiaFontRenderer.drawTextCentered(canvas, font, "☺", ex + colW / 2.0F,
                ey + cell / 2.0F, 0xFFFFFFFF);
    }

    /**
     * Header of the server section: its icon and name on the left, the sync state
     * on the right (decision 22). Nothing here is interactive - rescanning is the
     * server-side /atomchat gui screen, not a client button.
     */
    private void drawServerHeader(Canvas canvas, float contentX, float ey, float cell, float contentW) {
        ServerPackStore store = ServerPackStore.current();
        float textX = contentX;
        Image icon = serverIcon();
        if (icon != null) {
            float size = Math.min(cell * 0.5F, icon.getWidth());
            SkiaImages.drawRoundedImage(canvas, icon, contentX, ey + (cell - size) / 2.0F, size, size, s(4));
            textX = contentX + size + s(8);
        }
        String name = store.serverName().isEmpty()
                ? Text.translatable("atomchat.panel.server_pack").getString()
                : store.serverName();
        Font font = FontManager.font(UiTokens.FONT_QUOTE);
        float baseline = SkiaFontRenderer.centerBaselineY(font, ey + cell / 2.0F);
        SkiaFontRenderer.drawText(canvas, font, name, textX, baseline, textPrimary());
        String status = syncStatusText();
        float statusW = SkiaFontRenderer.getStringWidth(font, status);
        SkiaFontRenderer.drawText(canvas, font, status, contentX + contentW - statusW, baseline,
                textSecondary());
    }

    /** The one line of sync state the panel shows for the server section. */
    private static String syncStatusText() {
        String key = switch (PackSyncClient.detail()) {
            case "synced", "up_to_date" -> "atomchat.panel.server_pack.synced";
            case "fetching" -> "atomchat.panel.server_pack.fetching";
            case "disabled", "no_server_mod" -> "atomchat.panel.server_pack.none";
            default -> "atomchat.panel.server_pack.failed";
        };
        return Text.translatable(key).getString();
    }

    /** Decodes and caches server-icon.png for the pack on display. */
    private Image serverIcon() {
        ServerPackStore store = ServerPackStore.current();
        byte[] bytes = store.icon();
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        if (!store.packHash().equals(iconHash)) {
            if (iconImage != null) {
                iconImage.close();
                iconImage = null;
            }
            try {
                iconImage = Image.makeFromEncoded(bytes);
            } catch (Throwable ignored) {
                // A broken icon is cosmetic; the name still shows.
            }
            iconHash = store.packHash();
        }
        return iconImage;
    }

/**
     * The panel is an overlay with its own opaque dark surface, so its text
     * pins the overlay palette instead of following the interface text
     * colour settings (same rule as the context menus).
     */
    private static int textPrimary() {
        return 0xFFFFFFFF;
    }

    private static int textSecondary() {
        return 0xDCAAAABA;
    }

    /**
     * Centred scale around (cx, cy), the BottomTabBar recipe: emitted only
     * when the scale actually moved, so an idle frame adds no matrix state,
     * and every call sits inside the caller's own save/restore pair.
     */
    private static void applyBounce(Canvas canvas, float scale, float cx, float cy) {
        if (scale != 1.0F) {
            canvas.translate(cx, cy);
            canvas.scale(scale, scale);
            canvas.translate(-cx, -cy);
        }
    }
}
