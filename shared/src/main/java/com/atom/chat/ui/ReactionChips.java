package com.atom.chat.ui;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.reaction.*;
import com.atom.chat.render.Easing;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.font.FontManager;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import java.util.*;

/**
 * Reaction chips under a bubble: layout shared by measurement, paint and hit
 * testing, with hits living only in the current viewport.
 *
 * <p>Geometry stays a pure function of a cell list, and every animation rides
 * on what {@link ReactionChipAnim} returns for a cell — so an entering chip
 * occupies its slot immediately (its space is in the row height from the first
 * frame, only the paint scales and fades), and a leaving one keeps its slot as
 * a fading ghost until the exit finishes. That is why the entrance needs no
 * height tween: the layout is already the settled size and only the pixels
 * animate.</p>
 */
public final class ReactionChips {
    /** Capsule start scale; a 24px chip needs far more than the 0.94 a 137px menu uses to read as motion. */
    private static final float ENTER_SCALE_START = 0.6F;
    /** Glyph start scale, smaller than the capsule so it visibly catches up and pops. */
    private static final float SYMBOL_SCALE_START = 0.4F;
    /** Exit shrink: the capsule eases in by this fraction as it fades. */
    private static final float EXIT_SHRINK = 0.15F;
    /** Peak gain of the count bump. */
    private static final float PULSE_GAIN = 0.35F;

    /** One laid-out chip: its animation state plus its box and (live) wire entry. */
    private record Cell(ReactionChipAnim.Chip chip,float x,float y,float w,ReactionWire.Entry entry) {}
    private record Hit(ChatMessage row,ReactionWire.Entry entry,float x,float y,float w) {}

    private final List<Hit> hits = new ArrayList<>();
    private final ReactionChipAnim anim = new ReactionChipAnim(
            UiMotion.REACTION_CHIP_MS, UiMotion.REACTION_CHIP_MS, UiMotion.REACTION_PULSE_MS,
            UiMotion.REACTION_SYMBOL_DELAY_MS, UiMotion.REACTION_FRESH_MS);
    /** Per-row cells and their wire entries, computed once per frame and read by measure, paint and hit tests. */
    private record RowFrame(List<ReactionChipAnim.Chip> cells,Map<String,ReactionWire.Entry> entries) {
        static final RowFrame EMPTY = new RowFrame(List.of(), Map.of());
    }
    private final Map<Long,RowFrame> frames = new HashMap<>();
    private UiLayout.Rect viewport;
    private long frameNow = System.currentTimeMillis();

    /**
     * The hover tooltip is one instance that follows the pointer from chip to
     * chip: it keeps its entry and last position while the pointer moves
     * between chips (so crossing A→B never re-fades), and only fades when the
     * pointer leaves the chips entirely.
     */
    private ReactionWire.Entry hoverEntry;
    private float hoverX,hoverY,hoverAlpha;
    private long hoverLastMs;

    private static float height() { return UiTokens.REACTION_CHIP_H; }
    private static float gap() { return UiTokens.REACTION_GAP; }
    private static float lerp(float from,float to,float t) { return from + (to - from) * t; }

    /** Multiplies a colour's own alpha by {@code alpha} (0..1), so the skin's built-in alpha survives. */
    private static int fade(int argb,float alpha) {
        int base = (argb >>> 24) & 0xFF;
        return UiTokens.withAlpha(argb, base * alpha);
    }

    public void begin(float x,float y,float w,float h) {
        hits.clear();
        viewport = new UiLayout.Rect(x, y, w, h);
        frameNow = System.currentTimeMillis();
        frames.clear();
        anim.prune(frameNow);
    }

    /** A row's chips and wire entries for this frame, computed at most once. */
    private RowFrame frame(ChatMessage row) {
        long id = row.getId();
        RowFrame cached = frames.get(id);
        if (cached != null) return cached;
        // No channel means no reactions can exist anywhere, so skip the row hash
        // entirely — this runs for every measured message, every frame.
        if (!ReactionClient.enabled()) { frames.put(id, RowFrame.EMPTY); return RowFrame.EMPTY; }
        UUID key = ReactionClient.key(row);
        RowFrame result = RowFrame.EMPTY;
        if (key != null) {
            LinkedHashMap<String,Integer> live = new LinkedHashMap<>();
            Map<String,Long> arrivals = new HashMap<>();
            Map<String,ReactionWire.Entry> entries = new HashMap<>();
            Set<String> mine = new HashSet<>();
            for (ReactionWire.Entry entry : ReactionClient.entries(key)) {
                live.put(entry.emoji(), entry.members().size());
                entries.put(entry.emoji(), entry);
                if (ReactionClient.own(entry)) mine.add(entry.emoji());
                long arrival = ReactionClient.arrival(key, entry.emoji());
                if (arrival > 0L) arrivals.put(entry.emoji(), arrival);
            }
            result = new RowFrame(
                    anim.sync(id, live, mine, arrivals, frameNow, Animations.messageEntry()), entries);
        }
        frames.put(id, result);
        return result;
    }

    /** Lays a row's chips out in a wrapping row; the wire entry is null on a fading ghost. */
    private List<Cell> layout(ChatMessage row,float width) {
        RowFrame frame = frame(row);
        if (frame.cells().isEmpty()) return List.of();
        List<Cell> laid = new ArrayList<>(frame.cells().size());
        float x = 0.0F, y = gap();
        Font font = FontManager.font(UiTokens.FONT_QUOTE);
        float available = Math.max(UiTokens.REACTION_MIN_WIDTH, width);
        for (ReactionChipAnim.Chip chip : frame.cells()) {
            float w = Math.min(available,
                    UiTokens.REACTION_COUNT_PAD + SkiaFontRenderer.getStringWidth(font, Integer.toString(chip.count())));
            if (x > 0.0F && x + w > available) { x = 0.0F; y += height() + gap(); }
            laid.add(new Cell(chip, x, y, w, frame.entries().get(chip.emoji())));
            x += w + gap();
        }
        return laid;
    }

    /** Extra height a row's chips add to the list; ghost slots count, so space is held through the exit. */
    public float extraHeight(ChatMessage row,float width) {
        List<Cell> cells = layout(row, width);
        return cells.isEmpty() ? 0.0F : cells.get(cells.size() - 1).y() + height();
    }

    public void draw(Canvas canvas,ChatMessage row,float x,float bubbleBottom,float width,float scroll) {
        Font emojiFont = FontManager.font(UiTokens.FONT_EMOJI);
        Font font = FontManager.font(UiTokens.FONT_QUOTE);
        List<Cell> cells = layout(row, width);
        boolean own = row.isOwn();
        for (Cell cell : cells) {
            float rowWidth = 0.0F;
            for (Cell sibling : cells) {
                if (sibling.y() == cell.y()) rowWidth = Math.max(rowWidth, sibling.x() + sibling.w());
            }
            float left = own ? x - rowWidth : x;
            float cx = left + cell.x(), cy = bubbleBottom + cell.y();
            float cyMid = cy + height() / 2.0F;
            ReactionChipAnim.Chip chip = cell.chip();
            boolean mine = chip.own();

            float enter = chip.enter(), exit = chip.exit();
            float exitFade = Easing.easeOutQuad(exit);
            float alpha = Easing.easeOutQuad(enter) * (1.0F - exitFade);
            float capsuleScale = lerp(ENTER_SCALE_START, 1.0F, UiSpring.messageEase(enter)) * (1.0F - EXIT_SHRINK * exitFade);
            float symbolScale = lerp(SYMBOL_SCALE_START, 1.0F, UiSpring.messageEase(chip.symbolEnter()));
            float symbolAlpha = Easing.easeOutQuad(chip.symbolEnter()) * (1.0F - exitFade);

            int fill = mine ? UiTokens.accentFill() : UiTokens.SKIN_PANEL;
            float bx = cx + cell.w() / 2.0F;
            canvas.save();
            try {
                scaleAbout(canvas, capsuleScale, bx, cyMid);
                SkiaDraw.drawRoundedRect(canvas, cx, cy, cell.w(), height(),
                        UiTokens.radius(UiTokens.REACTION_RADIUS), fade(fill, alpha));
            } finally { canvas.restore(); }

            float ex = cx + UiTokens.REACTION_EMOJI_CENTER;
            canvas.save();
            try {
                scaleAbout(canvas, symbolScale, ex, cyMid);
                SkiaFontRenderer.drawTextCentered(canvas, emojiFont, chip.emoji(), ex, cyMid, fade(0xFFFFFFFF, symbolAlpha));
            } finally { canvas.restore(); }

            int ink = mine ? UiTokens.onAccent(fill) : 0xFFFFFFFF;
            float tx = cx + UiTokens.REACTION_COUNT_X;
            canvas.save();
            try {
                scaleAbout(canvas, 1.0F + PULSE_GAIN * chip.pulse(), tx, cyMid);
                SkiaFontRenderer.drawText(canvas, font, Integer.toString(chip.count()), tx,
                        SkiaFontRenderer.centerBaselineY(font, cyMid), fade(ink, alpha));
            } finally { canvas.restore(); }

            hits.add(new Hit(row, cell.entry(), cx, cy - scroll, cell.w()));
        }
    }

    private static void scaleAbout(Canvas canvas,float scale,float cx,float cy) {
        if (scale == 1.0F) return;
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);
        canvas.translate(-cx, -cy);
    }

    private Hit at(float x,float y) {
        if (viewport == null || x < viewport.x() || x >= viewport.x() + viewport.w()
                || y < viewport.y() || y >= viewport.y() + viewport.h()) return null;
        for (Hit hit : hits) {
            if (x >= hit.x() && x < hit.x() + hit.w() && y >= hit.y() && y < hit.y() + height()) return hit;
        }
        return null;
    }

    public boolean click(float x,float y) {
        Hit hit = at(x, y);
        if (hit == null || hit.entry() == null) return false;
        ReactionClient.toggle(hit.row(), hit.entry().emoji());
        return true;
    }

    /**
     * Paints the hover tooltip. {@code suppress} is true while an overlay owns
     * the pointer, which fades the tooltip out instead of freezing it visible.
     */
    public void hover(Canvas canvas,float x,float y,boolean suppress) {
        long now = frameNow;
        float dt = hoverLastMs == 0L ? 16.0F : Math.min(50.0F, Math.max(1.0F, now - hoverLastMs));
        hoverLastMs = now;

        Hit hit = suppress ? null : at(x, y);
        boolean overChip = hit != null && hit.entry() != null;
        if (overChip) {
            hoverEntry = hit.entry();
            hoverX = hit.x();
            hoverY = hit.y();
        }
        float target = overChip ? 1.0F : 0.0F;
        // The tooltip answers the pointer, so it fades on its own clock and stays
        // live even with decorative motion off — the same rule hover tint and
        // scroll glide follow (see Animations).
        hoverAlpha = UiMotion.approach(hoverAlpha, target, dt, UiMotion.HOVER_MS);
        if (hoverAlpha <= 0.01F || hoverEntry == null || viewport == null) return;

        StringJoiner names = new StringJoiner(", ");
        int shown = 0;
        for (ReactionWire.Member person : hoverEntry.members()) {
            if (shown++ == 6) break;
            names.add(person.name());
        }
        if (hoverEntry.members().size() > 6) names.add("+" + (hoverEntry.members().size() - 6));
        String label = names.toString();
        Font font = FontManager.font(UiTokens.FONT_QUOTE);
        float w = Math.min(viewport.w() - gap() * 2.0F,
                SkiaFontRenderer.getStringWidth(font, label) + UiTokens.REACTION_TOOLTIP_PAD);
        float left = Math.max(viewport.x() + gap(), Math.min(hoverX, viewport.x() + viewport.w() - w - gap()));
        float top = Math.max(viewport.y(), hoverY - height() - gap());
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB(Math.round(255.0F * hoverAlpha), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(left - 2.0F, top - 2.0F, w + 4.0F, height() + 4.0F), layer);
            MenuPopup.drawSurface(canvas, left, top, w, height());
            canvas.save();
            try {
                SkiaDraw.clip(canvas, left, top, w, height(), UiTokens.radius(UiTokens.REACTION_RADIUS));
                SkiaFontRenderer.drawText(canvas, font, label, left + UiTokens.REACTION_TOOLTIP_INSET,
                        SkiaFontRenderer.centerBaselineY(font, top + height() / 2.0F), 0xFFFFFFFF);
            } finally { canvas.restore(); }
            canvas.restore();
        }
    }
}
