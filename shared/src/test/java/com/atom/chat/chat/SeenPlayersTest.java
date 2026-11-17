package com.atom.chat.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeenPlayersTest {
    private static final UUID NIL = new UUID(0L, 0L);

    @BeforeEach
    void resetStore() {
        SeenPlayers.clear();
    }

    @Test
    void rememberAndFindUuid() {
        UUID uuid = UUID.randomUUID();
        SeenPlayers.remember(uuid, "Steve", "[VIP]Steve");
        assertEquals(uuid, SeenPlayers.findUuid("Steve"));
        assertTrue(SeenPlayers.isKnown("Steve"));
        assertFalse(SeenPlayers.isKnown("Alex"));
    }

    @Test
    void lookupsIgnoreColorCodesAndCase() {
        UUID uuid = UUID.randomUUID();
        SeenPlayers.remember(uuid, "§aSteve", null);
        assertEquals(uuid, SeenPlayers.findUuid("steve"));
        assertEquals(uuid, SeenPlayers.findUuid("§aSteve"));
        assertNull(SeenPlayers.findUuid("Steve2"));
    }

    @Test
    void skipsNilUuidAndBlankNames() {
        SeenPlayers.remember(null, "A", null);
        SeenPlayers.remember(NIL, "B", null);
        SeenPlayers.remember(UUID.randomUUID(), "  ", null);
        assertNull(SeenPlayers.findUuid("A"));
        assertNull(SeenPlayers.findUuid("B"));
        assertTrue(SeenPlayers.profileNames().isEmpty());
    }

    @Test
    void blankDisplayNameKeepsPrevious() {
        UUID uuid = UUID.randomUUID();
        SeenPlayers.remember(uuid, "Steve", "[VIP]Steve");
        SeenPlayers.remember(uuid, "Steve", null);
        assertEquals(uuid, SeenPlayers.findUuid("Steve"));
        assertTrue(SeenPlayers.profileNames().contains("Steve"));
    }

    @Test
    void conflictingUuidInvalidatesNameRatherThanBindingTheWrongPlayer() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        SeenPlayers.remember(first, "Steve", null);
        SeenPlayers.remember(second, "Steve", null);
        assertNull(SeenPlayers.findUuid("Steve"));
    }

    @Test
    void evictsOldestBeyondCap() {
        for (int i = 0; i < 600; i++) {
            SeenPlayers.remember(UUID.randomUUID(), "p" + i, null);
        }
        assertNull(SeenPlayers.findUuid("p0"), "oldest entry must be evicted");
        assertTrue(SeenPlayers.isKnown("p599"), "newest entry must survive");
        assertFalse(SeenPlayers.isKnown("p42"), "entries beyond the cap must be gone");
    }

    @Test
    void clearDropsEverything() {
        SeenPlayers.remember(UUID.randomUUID(), "Steve", null);
        SeenPlayers.clear();
        assertNull(SeenPlayers.findUuid("Steve"));
        assertTrue(SeenPlayers.profileNames().isEmpty());
    }
}
