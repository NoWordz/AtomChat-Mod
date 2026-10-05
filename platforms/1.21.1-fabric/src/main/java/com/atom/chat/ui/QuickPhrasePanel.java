package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Rect;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * The quick-phrase panel: a text list above the composer, ported from
 * e33chat's {@code ChatQuickChatPanel}.
 *
 * <p>Visual language mirrors {@link EmojiPanel} — same surface, same entrance,
 * same corner tokens — and text colours are pinned (the panel brings its own
 * opaque dark surface and never follows the interface colour settings).
 *
 * <p>Editing model (grilled 2026-09-08, option B): the panel owns the list
 * geometry and state, but <em>text input borrows the composer's native field</em>
 * (the only carrier of the IME). The screen is the choreographer: it starts an
 * edit by filling the composer field with the phrase and remembering the old
 * draft, routes enter/Esc, and restores the draft afterwards. This panel never
 * holds an input buffer — tapping a row reports an {@link Action} the screen
 * interprets.
 */
public final class QuickPhrasePanel {
    /** Composer side effect the panel cannot own itself. */
    public interface Host {
        /** Appends text at the caret in the chat input box. */
        void insert(String text);
    }

    /** What a click inside the panel hit; the screen turns this into actions. */
    public record Action(int type, int index) {
        public static final int NONE = 0;
        public static final int DELETE = 1;
        public static final int EDIT = 2;
        public static final int ADD = 3;
        public static final int INSERT = 4;
        /** Insert one of the joined server's phrases (read-only, decision 9). */
        public static final int INSERT_SERVER = 5;
    }

    /** No row is being added/edited. */
    public static final int NO_EDIT = -1;
    /** The composer is being used to type a brand-new phrase. */
    public static final int ADD_NEW = -2;

    private static final int VISIBLE_ROWS = 8;

    // Popups pin their colours: they bring an opaque dark surface, so
    // following the interface text/accent settings would break on light themes.
    private static final int TEXT = 0xFFFFFFFF;
    private static final int SUBTEXT = 0xDCAAAABA;

    private final Host host;
    private final IntSupplier accent;
    private final Map<Integer, Float> rowHover = new HashMap<>();
    private final Map<Integer, Float> btnHover = new HashMap<>();
    // Per-cell press/hover bounce, keyed like the hover maps above: one spring per
    // cell, never shared, so a row, its two icon keys and the add row each bounce
    // on their own. Hit-testing keeps using the unscaled rects; the scale is
    // draw-only, exactly like the emoji panel's cells.
    private final Map<Integer, PressScale> cellScale = new HashMap<>();
    /** Cell of the last click, still in the pressed phase of its pulse. */
    private int pressedCellKey = -1;
    private long pressedCellAtMs;
    /** How long a click keeps its cell pressed before the spring returns. */
    private static final long CELL_PULSE_MS = 110;
    // Scale keys that live above the row positions, so the add row and a row's
    // edit/delete key can never collide with the row index space.
    private static final int ADD_CELL_KEY = 1 << 16;
    private static final int ICON_CELL_KEY = 1 << 17;
    /** Highlight on the "add" row while a new phrase is being composed. */
    private float addHover;

    private boolean open;
    private float anim;
    private int scroll;
    private int hoveredRow = -1;
    private int hoveredBtn = -1;
    private boolean hoverAdd;
    private int editingIndex = NO_EDIT;

    public QuickPhrasePanel(Host host, IntSupplier accent) {
        this.host = host;
        this.accent = accent;
    }

    private static float s(float v) {
        return UiTokens.s(v);
    }

    /** Rows of the scrolling list: the player's phrases, a group label, the server's. */
    private static final int ROW_LOCAL = 0;
    private static final int ROW_HEADER = 1;
    private static final int ROW_SERVER = 2;

    /** The joined server's phrases; never written to, only inserted (decision 9). */
    private static List<String> serverPhrases() {
        com.atom.chat.pack.ServerPackStore store = com.atom.chat.pack.ServerPackStore.current();
        // Nothing to offer means no group label and no rows at all.
        return store.hasContent() ? store.phrases() : List.of();
    }

    private static int rowCount() {
        int local = phrases().size();
        int server = serverPhrases().size();
        return local + (server > 0 ? 1 + server : 0);
    }

    private static int rowKind(int position) {
        int local = phrases().size();
        if (position < local) {
            return ROW_LOCAL;
        }
        return position == local ? ROW_HEADER : ROW_SERVER;
    }

    /** Text of one row: the player's phrase, the group label, or the server's phrase. */
    public String rowTextAt(int position) {
        int local = phrases().size();
        return switch (rowKind(position)) {
            case ROW_LOCAL -> position >= 0 && position < local ? phrases().get(position) : "";
            case ROW_HEADER -> tr("atomchat.quick.server");
            default -> {
                List<String> server = serverPhrases();
                int index = position - local - 1;
                yield index >= 0 && index < server.size() ? server.get(index) : "";
            }
        };
    }

    private static List<String> phrases() {
        List<String> list = AtomChatConfig.get().quickPhrases;
        if (list == null) {
            list = new ArrayList<>();
            AtomChatConfig.get().quickPhrases = list;
        }
        return list;
    }

    // ---- geometry (width/position mirror EmojiPanel so the panels read as siblings) ----

    private static float panelW() {
        return UiTokens.EMOJI_COLS * UiTokens.EMOJI_CELL + UiTokens.EMOJI_PANEL_PAD * 2.0F;
    }

    private static float rowH() {
        return s(36);
    }

    private static float panelH() {
        // list rows + a persistent bottom "add" row + its own padding
        return UiTokens.EMOJI_PANEL_PAD + (VISIBLE_ROWS + 1) * rowH() + UiTokens.EMOJI_PANEL_PAD;
    }

    private static float panelX(UiLayout layout) {
        return layout.rect().x() + UiTokens.LIST_PAD_X;
    }

    /** Sits directly above the input bar, so it follows the bar's grown height. */
    private static float panelY(UiLayout layout) {
        return layout.inputBar.y() - UiTokens.PANEL_TOP_GAP - panelH() - s(6);
    }

    private static Rect panelRect(UiLayout layout) {
        // types.Rect's constructor is LTRB — XYWH must go through the factory.
        return Rect.makeXYWH(panelX(layout), panelY(layout), panelW(), panelH());
    }

    private static Rect rowRect(UiLayout layout, int visibleIndex) {
        Rect p = panelRect(layout);
        return Rect.makeXYWH(p.getLeft() + UiTokens.EMOJI_PANEL_PAD,
                p.getTop() + UiTokens.EMOJI_PANEL_PAD + visibleIndex * rowH(),
                p.getWidth() - UiTokens.EMOJI_PANEL_PAD * 2.0F, rowH());
    }

    private static Rect addRowRect(UiLayout layout) {
        Rect p = panelRect(layout);
        return Rect.makeXYWH(p.getLeft() + UiTokens.EMOJI_PANEL_PAD,
                p.getTop() + p.getHeight() - UiTokens.EMOJI_PANEL_PAD - rowH(),
                p.getWidth() - UiTokens.EMOJI_PANEL_PAD * 2.0F, rowH());
    }

    // The two icon keys, as distances from the row's right edge. The centres
    // are the original layout; the width in between is derived from them rather
    // than picked, so the two hit rects can only meet at their midpoint.
    private static final float ICON_DELETE_FROM_RIGHT = s(20);
    private static final float ICON_EDIT_FROM_RIGHT = s(44);

    /** Which icon key a caller means: 0 = edit, 1 = delete. */
    private static int iconIndex(int btn) {
        return btn == 0 ? 0 : 1;
    }

    /** Where one icon key is drawn: the only definition of those two centres. */
    private static float iconCenterX(Rect row, int btn) {
        return row.getRight()
                - (iconIndex(btn) == 0 ? ICON_EDIT_FROM_RIGHT : ICON_DELETE_FROM_RIGHT);
    }

    /**
     * Hit rect of one icon key. The two rects are adjacent and meet exactly at
     * the midpoint of their centres, so no band resolves to the wrong action.
     * They used to be s(28) wide around centres s(24) apart, which overlapped
     * by s(5); delete is tested first, so a click in that band deleted the
     * phrase even with the pointer visually on the edit glyph.
     */
    private static Rect iconEdit(Rect row, int btn) {
        return iconHitRect(row, iconIndex(btn));
    }

    private static Rect iconHitRect(Rect row, int btn) {
        float boundary = (iconCenterX(row, 0) + iconCenterX(row, 1)) / 2.0F;
        float left = btn == 0 ? boundary - s(24) : boundary;
        float right = btn == 0 ? boundary : boundary + s(24);
        return Rect.makeXYWH(left, row.getTop(), right - left, row.getHeight());
    }

    private int maxScroll() {
        return Math.max(0, rowCount() - VISIBLE_ROWS);
    }

    /**
     * skija's types.Rect has no contains(float,float); hand-rolled here. Bounds
     * are half open: right and bottom are exclusive, so two rects that meet at
     * an edge split the space between them instead of both claiming the edge
     * (which is what let the edit key answer for the delete key at x == edge).
     */
    private static boolean contains(Rect r, float x, float y) {
        return x >= r.getLeft() && x < r.getRight() && y >= r.getTop() && y < r.getBottom();
    }

    /**
     * Which icon key the pointer is on: 0 = edit, 1 = delete, -1 = neither.
     * Click, hover and draw all read this one function, so the two paths cannot
     * drift apart the way two hand-written copies of the same rect did.
     */
    private static int iconUnder(Rect row, float x, float y) {
        for (int btn = 0; btn < 2; btn++) {
            if (contains(iconEdit(row, btn), x, y)) {
                return btn;
            }
        }
        return -1;
    }

    /**
     * Which icon key the last {@link #hover} landed on: 0 = edit, 1 = delete,
     * -1 = neither. One accessor for the hover path, so a test can ask both
     * paths the same question and compare the answers.
     */
    int hoveredIconButton() {
        return hoveredBtn < 0 ? -1 : hoveredBtn % 2;
    }

    /** Cell key of one edit/delete key, from btnHover's composite index. */
    private static int iconCellKey(int btnKey) {
        return ICON_CELL_KEY + btnKey;
    }

    /**
     * Rows and the add row are capsules packed against the panel edge and against
     * each other, so they take the compact tier - the one the project added for
     * controls with no room to grow into. The edit/delete keys sit alone inside a
     * row and take the full control bounce.
     */
    private static PressScale newCellScale(int key) {
        return key >= ICON_CELL_KEY ? PressScale.control() : PressScale.compact();
    }

    // ---- state ----

    public boolean isOpen() {
        return open;
    }

    public boolean isEditing() {
        return editingIndex != NO_EDIT;
    }

    public boolean isAddingNew() {
        return editingIndex == ADD_NEW;
    }

    public void open() {
        open = true;
    }

    /** Screen choreography: always call the screen's close wrapper, never this
     *  directly while editing — the borrowed composer draft must be settled. */
    public void close() {
        open = false;
        editingIndex = NO_EDIT;
        hoveredRow = -1;
        hoveredBtn = -1;
    }

    public void toggle() {
        if (open) {
            close();
        } else {
            open();
        }
    }

    /** Leaves the current page: drop transient state, like the emoji panel. */
    public void resetTransient() {
        close();
        scroll = 0;
        rowHover.clear();
        btnHover.clear();
        cellScale.clear();
        pressedCellKey = -1;
    }

    public void update(long frameDt) {
        // Animations.ms, not the raw token: the settings switch collapses this
        // panel the way it already collapses the emoji panel, instead of leaving
        // a 110 ms pop behind with decorative motion off.
        anim = UiMotion.approach(anim, open ? 1.0F : 0.0F, frameDt, Animations.ms(UiMotion.POPUP_MS));
        if (anim < 0.01F && !open) {
            anim = 0.0F;
        }
        float addTarget = (isAddingNew() || hoverAdd) ? 1.0F : 0.0F;
        addHover = UiMotion.approach(addHover, addTarget, frameDt, UiMotion.HOVER_MS);
        for (Map.Entry<Integer, Float> e : rowHover.entrySet()) {
            e.setValue(UiMotion.approach(e.getValue(), e.getKey() == hoveredRow ? 1.0F : 0.0F,
                    frameDt, UiMotion.HOVER_MS));
        }
        for (Map.Entry<Integer, Float> e : btnHover.entrySet()) {
            e.setValue(UiMotion.approach(e.getValue(), e.getKey() == hoveredBtn ? 1.0F : 0.0F,
                    frameDt, UiMotion.HOVER_MS));
        }
        // The bounce rides the same per-frame pass as the fades above: one spring
        // per cell, dropped again once it is at rest away from the pointer.
        long now = System.currentTimeMillis();
        boolean pulsing = pressedCellKey >= 0 && now - pressedCellAtMs < CELL_PULSE_MS;
        int hoveredIconCell = hoveredBtn < 0 ? -1 : iconCellKey(hoveredBtn);
        int hoveredAddCell = hoverAdd ? ADD_CELL_KEY : -1;
        Iterator<Map.Entry<Integer, PressScale>> scaleIt = cellScale.entrySet().iterator();
        while (scaleIt.hasNext()) {
            Map.Entry<Integer, PressScale> e = scaleIt.next();
            int key = e.getKey();
            // An icon under the pointer is the target, so its row holds its resting
            // size: letting both springs run would compound their growth on the key.
            boolean over = key == hoveredIconCell || key == hoveredAddCell
                    || (key == hoveredRow && hoveredBtn < 0);
            PressScale ps = e.getValue();
            ps.update(over, pulsing && key == pressedCellKey, frameDt, Animations.enabled());
            if (!over && ps.isResting()) {
                scaleIt.remove();
            }
        }
        if (!pulsing) {
            pressedCellKey = -1;
        }
        // The pointer can only hover one of each, so these are the only cells that
        // can need a spring this frame.
        if (hoveredRow >= 0 && !cellScale.containsKey(hoveredRow)) {
            cellScale.put(hoveredRow, newCellScale(hoveredRow));
        }
        if (hoveredIconCell >= 0 && !cellScale.containsKey(hoveredIconCell)) {
            cellScale.put(hoveredIconCell, newCellScale(hoveredIconCell));
        }
        if (hoveredAddCell >= 0 && !cellScale.containsKey(hoveredAddCell)) {
            cellScale.put(hoveredAddCell, newCellScale(hoveredAddCell));
        }
    }

    public boolean overPanel(UiLayout layout, double mx, double my) {
        if (anim < 0.5F) {
            return false;
        }
        Rect p = panelRect(layout);
        return mx >= p.getLeft() && mx <= p.getRight() && my >= p.getTop() && my <= p.getBottom();
    }

    public void scroll(UiLayout layout, double amount) {
        if (!open) {
            return;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll + (amount > 0 ? -1 : 1)));
    }

    // ---- list mutations (self-contained: nothing touches the composer) ----

    public String phraseAt(int index) {
        List<String> list = phrases();
        return index >= 0 && index < list.size() ? list.get(index) : "";
    }

    public void deleteAt(int index) {
        List<String> list = phrases();
        if (index < 0 || index >= list.size()) {
            return;
        }
        list.remove(index);
        if (editingIndex == index) {
            editingIndex = NO_EDIT;
        } else if (editingIndex > index) {
            editingIndex--;
        }
        AtomChatConfig.save(AtomChatConfig.get());
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        // Every row below the removed one shifts up, so a highlight or a spring
        // keyed to a position would otherwise land on a different phrase.
        rowHover.clear();
        btnHover.clear();
        cellScale.clear();
        pressedCellKey = -1;
    }

    /** Commits composer text as a new phrase or an edit of {@code editingIndex}. */
    public void commitText(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty() || value.length() > AtomChatConfig.MAX_QUICK_PHRASE_LENGTH) {
            editingIndex = NO_EDIT;
            return;
        }
        List<String> list = phrases();
        if (editingIndex >= 0 && editingIndex < list.size()) {
            list.set(editingIndex, value);
        } else if (!isAddingNew() || list.size() < AtomChatConfig.MAX_QUICK_PHRASES) {
            // Guard: only the ADD_NEW mode appends.
            if (isAddingNew()) {
                list.add(value);
            }
        }
        editingIndex = NO_EDIT;
        AtomChatConfig.save(AtomChatConfig.get());
    }

    /** Ends the current add/edit session without saving. */
    public void abortEdit() {
        editingIndex = NO_EDIT;
    }

    public void startAdd() {
        editingIndex = ADD_NEW;
    }

    public void startEdit(int index) {
        editingIndex = index;
    }

    /** Inserts a row's text into the composer and closes the panel. */
    public void pickRow(int index) {
        List<String> list = phrases();
        if (index < 0 || index >= list.size()) {
            return;
        }
        if (host != null) {
            host.insert(list.get(index));
        }
        open = false;
        editingIndex = NO_EDIT;
    }

    // ---- interaction ----

    /** Hit-tests a left click inside the panel. */
    public Action click(UiLayout layout, double mx, double my) {
        Rect addRow = addRowRect(layout);
        if (contains(addRow, (float) mx, (float) my)) {
            pulseCell(ADD_CELL_KEY);
            return new Action(Action.ADD, 0);
        }
        int local = phrases().size();
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int position = scroll + i;
            if (position >= rowCount()) {
                break;
            }
            Rect r = rowRect(layout, i);
            if (my < r.getTop() || my > r.getBottom() || mx < r.getLeft() || mx > r.getRight()) {
                continue;
            }
            switch (rowKind(position)) {
                case ROW_LOCAL -> {
                    int btn = iconUnder(r, (float) mx, (float) my);
                    if (btn >= 0) {
                        pulseCell(iconCellKey(position * 2 + btn));
                        return new Action(btn == 0 ? Action.EDIT : Action.DELETE, position);
                    }
                    pulseCell(position);
                    return new Action(Action.INSERT, position);
                }
                // The server's rows carry no buttons: they can be sent, not edited.
                case ROW_SERVER -> {
                    pulseCell(position);
                    return new Action(Action.INSERT_SERVER, position - local - 1);
                }
                default -> {
                    return new Action(Action.NONE, 0);
                }
            }
        }
        return new Action(Action.NONE, 0);
    }

    /**
     * Click feedback: hold the cell pressed for a beat, then spring back. The
     * release that would end a held press never reaches the panel - the screen
     * acts on the returned {@link Action} straight away - so the press is a
     * one-shot pulse, like the emoji panel's cells.
     */
    private void pulseCell(int key) {
        pressedCellKey = key;
        pressedCellAtMs = System.currentTimeMillis();
        if (!cellScale.containsKey(key)) {
            cellScale.put(key, newCellScale(key));
        }
    }

    /** Called every frame with the virtual cursor so hover capsules can fade. */
    public void hover(UiLayout layout, float vmx, float vmy) {
        if (!open) {
            hoveredRow = -1;
            hoveredBtn = -1;
            hoverAdd = false;
            return;
        }
        hoveredRow = -1;
        hoveredBtn = -1;
        hoverAdd = contains(addRowRect(layout), vmx, vmy);
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scroll + i;
            if (index >= rowCount()) {
                break;
            }
            Rect r = rowRect(layout, i);
            if (vmx >= r.getLeft() && vmx <= r.getRight() && vmy >= r.getTop() && vmy <= r.getBottom()) {
                int kind = rowKind(index);
                if (kind == ROW_HEADER) {
                    // The group label is the one row that is not a target.
                    break;
                }
                // The server's rows highlight like the player's own - only the
                // buttons are missing, so nothing else here is hit-tested.
                hoveredRow = index;
                rowHover.putIfAbsent(index, 0.0F);
                if (kind == ROW_LOCAL) {
                    int btn = iconUnder(r, vmx, vmy);
                    if (btn >= 0) {
                        hoveredBtn = index * 2 + btn;
                    }
                }
                if (hoveredBtn >= 0) {
                    btnHover.putIfAbsent(hoveredBtn, 0.0F);
                }
                break;
            }
        }
    }

    // ---- rendering (EmojiPanel's structure: layer fade + scale + drop) ----

    public void render(Canvas canvas, UiLayout layout, float vmx, float vmy, long frameDt) {
        hover(layout, vmx, vmy);
        if (anim <= 0.01F) {
            return;
        }
        Rect p = panelRect(layout);
        float px = p.getLeft();
        float py = p.getTop();
        float pw = p.getWidth();
        float ph = p.getHeight();
        canvas.save();
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * anim), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(px - s(24), py - s(24), pw + s(48), ph + s(48)), layer);
            float sc = 0.92F + 0.08F * anim;
            float cx = px + pw / 2.0F;
            float cy = py + ph / 2.0F;
            canvas.translate(cx, cy);
            canvas.scale(sc, sc);
            canvas.translate(-cx, -cy);
            canvas.translate(0.0F, (1.0F - anim) * s(10));
            SkiaDraw.drawRoundedRect(canvas, px, py, pw, ph, UiTokens.radius(14), Color.makeARGB(245, 35, 39, 47));
            SkiaDraw.drawRoundedShadow(canvas, px, py, pw, ph, UiTokens.radius(14), s(8), Color.makeARGB(100, 0, 0, 0));

            Font font = FontManager.font(UiTokens.FONT_BUTTON);
            List<String> list = phrases();

            // The empty hint only belongs on a truly empty panel: a server
            // section counts as content even with no phrases of one's own.
            if (list.isEmpty() && serverPhrases().isEmpty()) {
                SkiaFontRenderer.drawTextCentered(canvas, font, tr("atomchat.quick.empty"),
                        px + pw / 2.0F,
                        py + UiTokens.EMOJI_PANEL_PAD + VISIBLE_ROWS * rowH() / 2.0F, SUBTEXT);
            }

            for (int i = 0; i < VISIBLE_ROWS; i++) {
                int position = scroll + i;
                if (position >= rowCount()) {
                    break;
                }
                Rect r = rowRect(layout, i);
                // The bounce wraps the whole cell, capsule, ring and glyphs drawn
                // together inside one save/restore. The group label is not a target,
                // so it is the one row that never carries a spring.
                PressScale ps = rowKind(position) == ROW_HEADER ? null : cellScale.get(position);
                if (ps != null) {
                    ps.begin(canvas, r.getLeft() + r.getWidth() / 2.0F, r.getTop() + r.getHeight() / 2.0F);
                }
                try {
                    drawRow(canvas, r, font, position);
                } finally {
                    if (ps != null) {
                        canvas.restore();
                    }
                }
            }

            PressScale addScale = cellScale.get(ADD_CELL_KEY);
            if (addScale != null) {
                Rect addRect = addRowRect(layout);
                addScale.begin(canvas, addRect.getLeft() + addRect.getWidth() / 2.0F,
                        addRect.getTop() + addRect.getHeight() / 2.0F);
            }
            try {
                drawAddRow(canvas, layout, font);
            } finally {
                if (addScale != null) {
                    canvas.restore();
                }
            }
            // Close the saveLayer; the finally below closes the outer save().
            canvas.restore();
        } finally {
            canvas.restore();
        }
    }

    private void drawRow(Canvas canvas, Rect r, Font font, int position) {
        int kind = rowKind(position);
        if (kind == ROW_HEADER) {
            // Group label: the boundary between "mine" and "the server's".
            SkiaFontRenderer.drawText(canvas, font, tr("atomchat.quick.server"), r.getLeft() + s(12),
                    SkiaFontRenderer.centerBaselineY(font, r.getTop() + r.getHeight() / 2.0F), SUBTEXT);
            return;
        }
        if (kind == ROW_SERVER) {
            // Same colour and the same hover capsule as the player's own phrases;
            // only the edit/delete buttons are missing, because there are none.
            if (rowHover.getOrDefault(position, 0.0F) > 0.01F) {
                float fade = rowHover.getOrDefault(position, 0.0F);
                SkiaDraw.drawRoundedRect(canvas, r.getLeft() + s(4), r.getTop() + s(4),
                        r.getWidth() - s(8), r.getHeight() - s(8), s(6),
                        Color.makeARGB((int) (55.0F * fade), 255, 255, 255));
            }
            String theirs = SkiaFontRenderer.truncate(font, rowTextAt(position), r.getWidth() - s(24));
            SkiaFontRenderer.drawText(canvas, font, theirs, r.getLeft() + s(12),
                    SkiaFontRenderer.centerBaselineY(font, r.getTop() + r.getHeight() / 2.0F), TEXT);
            return;
        }
        int index = position;
        float rowA = rowHover.getOrDefault(index, 0.0F);
        boolean editing = editingIndex == index;
        if (rowA > 0.01F) {
            SkiaDraw.drawRoundedRect(canvas, r.getLeft() + s(4), r.getTop() + s(4),
                    r.getWidth() - s(8), r.getHeight() - s(8), s(6),
                    Color.makeARGB((int) (55.0F * rowA), 255, 255, 255));
        }
        if (editing) {
            // The row being edited glows with the accent stroke; the composer
            // field below holds the live text.
            SkiaDraw.drawRoundedRect(canvas, r.getLeft() + s(4), r.getTop() + s(4),
                    r.getWidth() - s(8), r.getHeight() - s(8), s(6), Color.makeARGB(20, 255, 255, 255));
            try (Paint paint = new Paint().setColor(accentColor()).setAntiAlias(true)
                    .setMode(io.github.humbleui.skija.PaintMode.STROKE)
                    .setStrokeWidth(s(1.2F))) {
                canvas.drawRRect(io.github.humbleui.types.RRect.makeXYWH(
                        r.getLeft() + s(4), r.getTop() + s(4), r.getWidth() - s(8), r.getHeight() - s(8), s(6)), paint);
            }
        }
        String text = SkiaFontRenderer.truncate(font, phraseAt(index), r.getWidth() - s(96));
        SkiaFontRenderer.drawText(canvas, font, text, r.getLeft() + s(12),
                SkiaFontRenderer.centerBaselineY(font, r.getTop() + r.getHeight() / 2.0F),
                editing ? accentColor() : TEXT);
        // Icon buttons last so hover/delete never sit under the text.
        Path[] icons = {AppIcons.ICON_EDIT_PATH, AppIcons.ICON_CLOSE_PATH};
        for (int b = 0; b < 2; b++) {
            Rect hit = iconEdit(r, b);
            float bx = (hit.getLeft() + hit.getRight()) / 2.0F;
            float by = (hit.getTop() + hit.getBottom()) / 2.0F;
            // Each icon key is a cell of its own, so it bounces inside the row it
            // sits in rather than riding the row's spring.
            PressScale ps = cellScale.get(iconCellKey(index * 2 + b));
            if (ps != null) {
                ps.begin(canvas, bx, by);
            }
            try {
                float a = btnHover.getOrDefault(index * 2 + b, 0.0F);
                if (a > 0.01F) {
                    SkiaDraw.drawRoundedRect(canvas, hit.getLeft() + s(3), hit.getTop() + s(3),
                            hit.getWidth() - s(6), hit.getHeight() - s(6), s(6),
                            Color.makeARGB((int) (55.0F * a), 255, 255, 255));
                }
                drawIconCentered(canvas, icons[b], bx, by, UiTokens.CONTEXT_ICON_SIZE, TEXT);
            } finally {
                if (ps != null) {
                    canvas.restore();
                }
            }
        }
    }

    private void drawAddRow(Canvas canvas, UiLayout layout, Font font) {
        Rect r = addRowRect(layout);
        boolean active = isAddingNew();
        // Resting capsule so the row reads as a button, then the standard hover
        // wash; the composing state adds the accent ring on top.
        SkiaDraw.drawRoundedRect(canvas, r.getLeft() + s(4), r.getTop() + s(4),
                r.getWidth() - s(8), r.getHeight() - s(8), s(6),
                Color.makeARGB((int) (28.0F + 27.0F * addHover), 255, 255, 255));
        if (addHover > 0.01F) {
            SkiaDraw.drawRoundedRect(canvas, r.getLeft() + s(4), r.getTop() + s(4),
                    r.getWidth() - s(8), r.getHeight() - s(8), s(6),
                    Color.makeARGB((int) (55.0F * addHover), 255, 255, 255));
        }
        if (active) {
            try (Paint paint = new Paint().setColor(accentColor()).setAntiAlias(true)
                    .setMode(io.github.humbleui.skija.PaintMode.STROKE)
                    .setStrokeWidth(s(1.2F))) {
                canvas.drawRRect(io.github.humbleui.types.RRect.makeXYWH(
                        r.getLeft() + s(4), r.getTop() + s(4), r.getWidth() - s(8), r.getHeight() - s(8), s(6)), paint);
            }
        }
        String label = SkiaFontRenderer.truncate(font,
                tr(active ? "atomchat.quick.composing" : "atomchat.quick.add"),
                r.getWidth() - s(24));
        SkiaFontRenderer.drawTextCentered(canvas, font, label,
                r.getLeft() + r.getWidth() / 2.0F, r.getTop() + r.getHeight() / 2.0F,
                active ? accentColor() : TEXT);
    }

    private int accentColor() {
        int accent = this.accent != null ? this.accent.getAsInt() : 0xFF4A90E2;
        return Color.makeARGB(255, Color.getR(accent), Color.getG(accent), Color.getB(accent));
    }

    /** Same adaptive-stroke icon draw as the shell's context menus. */
    private static void drawIconCentered(Canvas canvas, Path icon, float cx, float cy, float size, int color) {
        Rect b = icon.getBounds();
        if (b == null || b.isEmpty()) {
            return;
        }
        float scale = size / Math.max(b.getWidth(), b.getHeight());
        canvas.save();
        try {
            canvas.translate(cx - (b.getLeft() + b.getRight()) / 2.0F * scale,
                    cy - (b.getTop() + b.getBottom()) / 2.0F * scale);
            canvas.scale(scale, scale);
            try (Paint paint = new Paint().setColor(color).setAntiAlias(true)
                    .setMode(io.github.humbleui.skija.PaintMode.STROKE)
                    .setStrokeWidth(1.5F / scale)
                    .setStrokeCap(io.github.humbleui.skija.PaintStrokeCap.ROUND)
                    .setStrokeJoin(io.github.humbleui.skija.PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally {
            canvas.restore();
        }
    }

    private static String tr(String key) {
        return Text.translatable(key).getString();
    }

    // ---- test entry points (nothing in the mod calls these) ----

    /** Rows the panel scrolls through; the test picks a row inside this count. */
    static int visibleRows() {
        return VISIBLE_ROWS;
    }

    /** Geometry of the two icon keys as the shipped code computes it. */
    static float[] iconHitGeometry(Rect row) {
        Rect edit = iconEdit(row, 0);
        Rect delete = iconEdit(row, 1);
        return new float[] {
                edit.getLeft(), edit.getRight(),
                delete.getLeft(), delete.getRight(),
                iconCenterX(row, 0), iconCenterX(row, 1),
                row.getBottom(), delete.getBottom(), delete.getTop()
        };
    }
}
