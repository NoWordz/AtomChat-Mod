package com.atom.chat.poke;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Poke dedupe: first cue passes, bursts are suppressed, the interval edge is
 * inclusive, and it is independent of the notification sound gate.
 */
class PokeGateTest {

    @Test
    void firstCueAlwaysPasses() {
        assertTrue(new PokeGate(500L).allow(1000L));
    }

    @Test
    void suppressesWithinTheInterval() {
        PokeGate gate = new PokeGate(500L);
        assertTrue(gate.allow(1000L));
        assertFalse(gate.allow(1400L), "a burst within 500ms is deduped");
    }

    @Test
    void acceptsAtExactlyTheInterval() {
        PokeGate gate = new PokeGate(500L);
        assertTrue(gate.allow(1000L));
        assertTrue(gate.allow(1500L), "the edge is inclusive, like the sound gate");
    }

    @Test
    void defaultIntervalIsFiveHundredMs() {
        PokeGate gate = new PokeGate();
        assertTrue(gate.allow(0L));
        assertFalse(gate.allow(PokeGate.MIN_INTERVAL_MS - 1L));
        assertTrue(gate.allow(PokeGate.MIN_INTERVAL_MS));
    }
}
