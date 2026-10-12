package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rules for the reaction chip animation state: a chip that just arrived plays
 * in, a chip that left holds its slot while it fades, and a count change bumps
 * the number. All of it is draw-free, so it is testable without a canvas.
 */
class ReactionChipAnimTest {
    private static final long ENTER = 160L;
    private static final long PULSE = 120L;
    private static final long SYMBOL_DELAY = 40L;
    private static final long FRESH = 400L;

    private static ReactionChipAnim anim() {
        return new ReactionChipAnim(ENTER, ENTER, PULSE, SYMBOL_DELAY, FRESH);
    }

    private static LinkedHashMap<String,Integer> live(String emoji,int count) {
        LinkedHashMap<String,Integer> m = new LinkedHashMap<>();
        m.put(emoji, count);
        return m;
    }

    @Test
    void freshArrivalPlaysEntrance() {
        ReactionChipAnim a = anim();
        long now = 10_000L;
        List<ReactionChipAnim.Chip> cells = a.sync(1L, live("😀", 1), Set.of(), Map.of("😀", now), now, true);
        assertEquals(1, cells.size());
        assertEquals(0.0F, cells.get(0).enter(), 1e-4F, "arrival this instant is not yet in");
        assertEquals(0.0F, cells.get(0).symbolEnter(), 1e-4F, "glyph trails the capsule");

        List<ReactionChipAnim.Chip> mid = a.sync(1L, live("😀", 1), Set.of(), Map.of("😀", now), now + SYMBOL_DELAY / 2, true);
        assertTrue(mid.get(0).enter() > 0.0F && mid.get(0).enter() < 1.0F);
        assertEquals(0.0F, mid.get(0).symbolEnter(), 1e-4F, "glyph waits out its delay");

        List<ReactionChipAnim.Chip> afterDelay = a.sync(1L, live("😀", 1), Set.of(), Map.of("😀", now),
                now + SYMBOL_DELAY + ENTER / 2, true);
        assertTrue(afterDelay.get(0).symbolEnter() > 0.0F && afterDelay.get(0).symbolEnter() < 1.0F,
                "glyph starts only after its delay");

        List<ReactionChipAnim.Chip> late = a.sync(1L, live("😀", 1), Set.of(), Map.of("😀", now), now + ENTER + SYMBOL_DELAY, true);
        assertEquals(1.0F, late.get(0).enter(), 1e-4F);
        assertEquals(1.0F, late.get(0).symbolEnter(), 1e-4F);
    }

    @Test
    void staleChipPaintsImmediately() {
        ReactionChipAnim a = anim();
        long now = 50_000L;
        List<ReactionChipAnim.Chip> cells = a.sync(2L, live("👍", 3), Set.of(), Map.of("👍", now - FRESH), now, true);
        assertEquals(1.0F, cells.get(0).enter(), 1e-4F, "already on screen when the row comes into view");
        assertEquals(1.0F, cells.get(0).symbolEnter(), 1e-4F);
    }

    @Test
    void removedChipFadesBeforeItsSlotIsReleased() {
        ReactionChipAnim a = anim();
        long now = 20_000L;
        a.sync(3L, live("😀", 1), Set.of(), Map.of(), now, true);

        List<ReactionChipAnim.Chip> justLeft = a.sync(3L, new LinkedHashMap<>(), Set.of(), Map.of(), now + 10L, true);
        assertEquals(1, justLeft.size(), "the slot is held while it fades");
        assertEquals(0.0F, justLeft.get(0).exit(), 1e-4F);
        assertEquals(1.0F, justLeft.get(0).enter(), 1e-4F);

        List<ReactionChipAnim.Chip> halfway = a.sync(3L, new LinkedHashMap<>(), Set.of(), Map.of(), now + 10L + ENTER / 2, true);
        assertTrue(halfway.get(0).exit() > 0.0F && halfway.get(0).exit() < 1.0F);

        List<ReactionChipAnim.Chip> gone = a.sync(3L, new LinkedHashMap<>(), Set.of(), Map.of(), now + 10L + ENTER, true);
        assertTrue(gone.isEmpty(), "space is released once the fade finishes");
    }

    @Test
    void countChangePulsesTheNumber() {
        ReactionChipAnim a = anim();
        long now = 30_000L;
        a.sync(4L, live("❤️", 1), Set.of(), Map.of(), now, true);

        List<ReactionChipAnim.Chip> start = a.sync(4L, live("❤️", 2), Set.of(), Map.of(), now + 5L, true);
        assertEquals(1, start.size());
        assertEquals(0.0F, start.get(0).pulse(), 1e-4F, "the bump starts from rest");

        List<ReactionChipAnim.Chip> rising = a.sync(4L, live("❤️", 2), Set.of(), Map.of(), now + 5L + PULSE / 4, true);
        assertTrue(rising.get(0).pulse() > 0.0F, "the number bumps when the count changes");

        List<ReactionChipAnim.Chip> peak = a.sync(4L, live("❤️", 2), Set.of(), Map.of(), now + 5L + PULSE / 2, true);
        assertEquals(1.0F, peak.get(0).pulse(), 1e-4F, "the bump peaks halfway");

        List<ReactionChipAnim.Chip> after = a.sync(4L, live("❤️", 2), Set.of(), Map.of(), now + 5L + PULSE, true);
        assertEquals(0.0F, after.get(0).pulse(), 1e-4F);
    }

    @Test
    void unchangedCountDoesNotPulse() {
        ReactionChipAnim a = anim();
        long now = 40_000L;
        a.sync(5L, live("😀", 2), Set.of(), Map.of(), now, true);
        List<ReactionChipAnim.Chip> again = a.sync(5L, live("😀", 2), Set.of(), Map.of(), now + 30L, true);
        assertEquals(0.0F, again.get(0).pulse(), 1e-4F);
    }

    @Test
    void disabledMotionSnapsEverythingInAndReleasesGhosts() {
        ReactionChipAnim a = anim();
        long now = 60_000L;
        a.sync(6L, live("😀", 1), Set.of(), Map.of("😀", now), now, true);
        List<ReactionChipAnim.Chip> off = a.sync(6L, new LinkedHashMap<>(), Set.of(), Map.of(), now + 10L, false);
        assertTrue(off.isEmpty(), "with motion off a removed chip releases at once");
        List<ReactionChipAnim.Chip> in = a.sync(7L, live("😀", 1), Set.of(), Map.of("😀", now), now, false);
        assertEquals(1.0F, in.get(0).enter(), 1e-4F);
        assertEquals(1.0F, in.get(0).symbolEnter(), 1e-4F);
    }

    @Test
    void reAddedChipPlaysInAgain() {
        ReactionChipAnim a = anim();
        long now = 70_000L;
        a.sync(8L, live("😀", 1), Set.of(), Map.of(), now, true);
        a.sync(8L, new LinkedHashMap<>(), Set.of(), Map.of(), now + 10L, true);
        List<ReactionChipAnim.Chip> back = a.sync(8L, live("😀", 1), Set.of(), Map.of("😀", now + 20L), now + 20L, true);
        assertEquals(0.0F, back.get(0).enter(), 1e-4F, "a re-added emoji is a fresh arrival");
        assertTrue(back.get(0).exit() == 0.0F);
    }

    @Test
    void fadingGhostKeepsItsOwnColour() {
        ReactionChipAnim a = anim();
        long now = 80_000L;
        a.sync(9L, live("😀", 1), Set.of("😀"), Map.of(), now, true);
        assertTrue(a.sync(9L, live("😀", 1), Set.of("😀"), Map.of(), now + 1L, true).get(0).own(),
                "a chip this client reacted to is marked own");
        List<ReactionChipAnim.Chip> ghost = a.sync(9L, new LinkedHashMap<>(), Set.of(), Map.of(), now + 20L, true);
        assertTrue(ghost.get(0).own(), "the ghost keeps its own colour while it fades");
    }

    @Test
    void removedWhileOffWindowDoesNotReplayAFadeWhenRevealed() {
        ReactionChipAnim a = anim();
        long now = 90_000L;
        a.sync(10L, live("😀", 1), Set.of(), Map.of(), now, true);
        // The row then stays off the measured window (folded history) while the
        // reaction is removed elsewhere; when it is measured again much later the
        // slot must be released at once, not fade from a stale removal.
        List<ReactionChipAnim.Chip> revealed = a.sync(10L, new LinkedHashMap<>(), Set.of(), Map.of(), now + 5_000L, true);
        assertTrue(revealed.isEmpty(), "no stale fade for a chip removed while unmeasured");
    }
}
