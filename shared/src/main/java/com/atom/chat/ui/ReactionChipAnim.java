package com.atom.chat.ui;

import java.util.*;

/**
 * Pure, draw-free animation state for one row's reaction chips.
 *
 * <p>Three timings live here, and the class owns nothing else:</p>
 * <ul>
 *   <li><b>Entrance</b> is not stored — it is derived from the arrival
 *       timestamp the model recorded when a chip's state first reached this
 *       client. A chip whose arrival is older than the freshness window is
 *       already in, so nothing animates when a row merely scrolls back into
 *       view.</li>
 *   <li><b>Exit</b> is a short "ghost": when an emoji leaves the live set the
 *       row keeps its slot (and the layout keeps holding its space) until the
 *       fade finishes. That is the whole reason this class exists — the
 *       geometry in {@link ReactionChips} is a pure function of the cell list,
 *       so holding a removed chip in that list is what buys the chip its exit
 *       animation without a shrinking-height tween.</li>
 *   <li><b>Pulse</b> is a count bump: the number scales 1&nbsp;→&nbsp;1+gain
 *       →&nbsp;1 while the capsule and glyph stay put, so "someone just reacted"
 *       reads without the whole chip re-entering.</li>
 * </ul>
 *
 * <p>No Skija and no {@code ChatMessage} here: {@link ReactionChips} supplies
 * the live counts and arrival map and owns all geometry and paint, so the rules
 * above are unit-testable without a canvas.</p>
 */
final class ReactionChipAnim {
    /** enter/symbolEnter/exit are linear progress in 0..1; pulse peaks at 1 mid-bump. */
    record Chip(String emoji, int count, float enter, float symbolEnter, float exit, float pulse, boolean own) {}

    private static final class Row {
        /** Last known count per emoji, kept even while the emoji is a ghost. */
        final Map<String,Integer> counts = new HashMap<>();
        /** Emojis live as of the previous sync. */
        final Set<String> live = new HashSet<>();
        /** Emoji -> whether this client had reacted, so a fading own chip keeps its colour. */
        final Map<String,Boolean> own = new HashMap<>();
        /** Emoji -> when its exit fade began (a chip that left the live set). */
        final Map<String,Long> ghosts = new HashMap<>();
        /** Emoji -> when its count last changed. */
        final Map<String,Long> pulseStart = new HashMap<>();
        long touched;
    }

    /** Bound on remembered rows; the least-recently-synced are dropped first. */
    private static final int MAX_ROWS = 512;
    private static final long ROW_TTL_MS = 60_000L;
    /**
     * A row whose last sync is older than this was off the measured window (folded
     * history, or a big scroll): a removal it missed in the meantime is treated as
     * already done, not as happening now, so revealing the row never plays a fade
     * for a chip that has actually been gone for a while.
     */
    private static final long CONTINUOUS_MS = 250L;

    private final long enterMs, exitMs, pulseMs, symbolDelayMs, freshMs;
    private final Map<Long,Row> rows = new LinkedHashMap<>();

    ReactionChipAnim(long enterMs,long exitMs,long pulseMs,long symbolDelayMs,long freshMs) {
        this.enterMs=enterMs;
        this.exitMs=exitMs;
        this.pulseMs=pulseMs;
        this.symbolDelayMs=symbolDelayMs;
        this.freshMs=freshMs;
    }

    /** Drops finished ghost/pulse timers and idle rows. Called once per frame. */
    void prune(long now) {
        rows.values().removeIf(row -> now - row.touched > ROW_TTL_MS);
        while (rows.size() > MAX_ROWS) {
            Long oldest = null;
            for (var entry : rows.entrySet()) {
                if (oldest == null || entry.getValue().touched < rows.get(oldest).touched) oldest = entry.getKey();
            }
            rows.remove(oldest);
        }
        for (Row row : rows.values()) {
            row.ghosts.values().removeIf(start -> now - start >= exitMs);
            row.pulseStart.values().removeIf(start -> now - start >= pulseMs);
        }
    }

    /**
     * Reconciles one row against its live counts and returns the cells to draw
     * for it: live chips in insertion order, then any still-fading ghosts.
     *
     * <p>{@code arrivalMs} maps emoji -> arrival time; a missing emoji (or one
     * older than {@code freshMs}) is already in. With {@code animEnabled} false
     * every chip is fully in, ghosts are released at once and no pulse plays —
     * the same "decorative motion off" collapse as the rest of the UI.</p>
     */
    List<Chip> sync(long rowId,LinkedHashMap<String,Integer> liveCounts,Set<String> ownEmojis,
                    Map<String,Long> arrivalMs,long now,boolean animEnabled) {
        Row row = rows.computeIfAbsent(rowId, ignored -> new Row());
        boolean continuous = row.touched != 0L && now - row.touched <= CONTINUOUS_MS;
        row.touched = now;

        Set<String> nowLive = new LinkedHashSet<>(liveCounts.keySet());
        // An emoji that left the live set starts fading out — but only while this
        // row is being synced frame to frame. A row that was off the window (so it
        // never saw the removal) releases the slot at once instead of replaying a
        // stale fade when it is revealed again.
        for (String emoji : row.live) {
            if (!nowLive.contains(emoji) && !row.ghosts.containsKey(emoji) && continuous) {
                row.ghosts.put(emoji, now);
            }
        }
        // Count changes bump the number; an emoji that came back loses its ghost.
        for (var entry : liveCounts.entrySet()) {
            String emoji = entry.getKey();
            int count = entry.getValue();
            Integer previous = row.counts.get(emoji);
            boolean wasLive = row.live.contains(emoji);
            if (wasLive && previous != null && previous != count) row.pulseStart.put(emoji, now);
            row.ghosts.remove(emoji);
            row.counts.put(emoji, count);
        }
        // Remember whose chip each emoji is: a ghost no longer has a wire entry,
        // and without this its "mine" colour would flip the moment it starts to
        // fade. The last live answer is kept through the exit.
        for (String emoji : nowLive) row.own.put(emoji, ownEmojis.contains(emoji));
        row.live.clear();
        row.live.addAll(nowLive);

        List<Chip> out = new ArrayList<>(liveCounts.size() + row.ghosts.size());
        if (!animEnabled) {
            row.ghosts.clear();
            row.pulseStart.clear();
            for (var entry : liveCounts.entrySet()) {
                out.add(new Chip(entry.getKey(), entry.getValue(), 1.0F, 1.0F, 0.0F, 0.0F,
                        row.own.getOrDefault(entry.getKey(), false)));
            }
            return out;
        }
        for (var entry : liveCounts.entrySet()) {
            String emoji = entry.getKey();
            Long arrival = arrivalMs.get(emoji);
            boolean fresh = arrival != null && now - arrival < freshMs;
            float enter = fresh ? progress(now, arrival, enterMs) : 1.0F;
            float symbol = fresh ? progress(now, arrival + symbolDelayMs, enterMs) : 1.0F;
            out.add(new Chip(emoji, entry.getValue(), enter, symbol, 0.0F, pulse(row, emoji, now),
                    row.own.getOrDefault(emoji, false)));
        }
        for (var ghost : row.ghosts.entrySet()) {
            float exit = progress(now, ghost.getValue(), exitMs);
            if (exit >= 1.0F) continue; // finished fading: the slot is released now
            int count = row.counts.getOrDefault(ghost.getKey(), 1);
            out.add(new Chip(ghost.getKey(), count, 1.0F, 1.0F, exit, 0.0F,
                    row.own.getOrDefault(ghost.getKey(), false)));
        }
        row.ghosts.values().removeIf(start -> now - start >= exitMs);
        row.own.keySet().retainAll(row.counts.keySet());
        return out;
    }

    /** 0 at rest, peaking at 1 halfway through the bump, back to 0 and cleaned up. */
    private float pulse(Row row,String emoji,long now) {
        Long start = row.pulseStart.get(emoji);
        if (start == null) return 0.0F;
        float t = progress(now, start, pulseMs);
        if (t >= 1.0F) {
            row.pulseStart.remove(emoji);
            return 0.0F;
        }
        return (float) Math.sin(Math.PI * t);
    }

    private static float progress(long now,long start,long durationMs) {
        if (durationMs <= 0L) return 1.0F;
        float t = (now - start) / (float) durationMs;
        if (t <= 0.0F) return 0.0F;
        return t >= 1.0F ? 1.0F : t;
    }
}
