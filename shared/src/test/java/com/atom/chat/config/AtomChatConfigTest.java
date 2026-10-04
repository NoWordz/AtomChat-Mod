package com.atom.chat.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Guards for the config migration paths. The legacy three-step corner style
 * must fold into the continuous radius exactly once (large=28 / medium=18 /
 * small=10) and then never be read or written again.
 */
class AtomChatConfigTest {

    @Test
    void cornerStyleMigratesOnceIntoRadius() {
        AtomChatConfig config = new AtomChatConfig();
        config.cornerStyle = "large";
        config.migrateCornerStyle();
        assertEquals(28f, config.cornerRadius, 0.0F, "large maps to the 28 default");
        assertNull(config.cornerStyle, "the legacy field is dropped after migration");

        // Re-running is a no-op: the field is null now, the radius stays.
        config.cornerRadius = 5f;
        config.migrateCornerStyle();
        assertEquals(5f, config.cornerRadius, 0.0F);
    }

    @Test
    void mediumAndSmallMapToTheirReferenceRadii() {
        AtomChatConfig medium = new AtomChatConfig();
        medium.cornerStyle = "medium";
        medium.migrateCornerStyle();
        assertEquals(18f, medium.cornerRadius, 0.0F);

        AtomChatConfig small = new AtomChatConfig();
        small.cornerStyle = "small";
        small.migrateCornerStyle();
        assertEquals(10f, small.cornerRadius, 0.0F);
    }

    @Test
    void factoryDefaultIsTheReferenceRadius() {
        AtomChatConfig config = new AtomChatConfig();
        assertEquals(28f, config.cornerRadius, 0.0F, "shipped default is 28");
        assertNull(config.cornerStyle, "a fresh config carries no legacy style");
    }
}
