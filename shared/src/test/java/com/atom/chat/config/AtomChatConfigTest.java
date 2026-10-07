package com.atom.chat.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Guards for the config migration paths. The legacy three-step corner style
 * must fold into the continuous radius exactly once (large=28 / medium=14 /
 * small=10) and then never be read or written again. The radius itself then
 * migrates onto the 1080p-basis semantics: the old reference-px scale differed
 * from the new one by 28 / 20 = 1.4, so the stored value is rescaled by that
 * factor once — every old value keeps the drawn factor it had (28 -> 20,
 * medium 14 -> 10, small 10 -> ~7.14), not just the default.
 */
class AtomChatConfigTest {

    @Test
    void cornerStyleMigratesOnceIntoRadius() {
        AtomChatConfig config = new AtomChatConfig();
        config.cornerStyle = "large";
        config.migrateCornerStyle();
        assertEquals(28f, config.cornerRadius, 0.0F, "large maps to the old 28 reference");
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
        assertEquals(14f, medium.cornerRadius, 0.0F);

        AtomChatConfig small = new AtomChatConfig();
        small.cornerStyle = "small";
        small.migrateCornerStyle();
        assertEquals(10f, small.cornerRadius, 0.0F);
    }

    @Test
    void factoryDefaultIsTheShippedPixelRadius() {
        AtomChatConfig config = new AtomChatConfig();
        assertEquals(20f, config.cornerRadius, 0.0F,
                "shipped default is 20 real screen pixels");
        assertNull(config.cornerStyle, "a fresh config carries no legacy style");
        // The field's own default is the legacy marker: a config that has never
        // been through load() has not been stamped, which is exactly how a
        // pre-1080p file arrives. load() stamps the fresh-install path.
        assertFalse(config.pixelRadiusMigrated,
                "an in-memory config is legacy until a loader stamps it");
    }

    @Test
    void oldDefaultMigratesToThePixelDefault() {
        AtomChatConfig config = new AtomChatConfig();
        config.cornerRadius = 28f;
        config.migratePixelRadius();
        assertEquals(20f, config.cornerRadius, 1e-4F,
                "the old reference-px default 28 rescales to the 20px shipped look");
        // Idempotent: the flag is already set, so a re-run must not divide again.
        config.migratePixelRadius();
        assertEquals(20f, config.cornerRadius, 1e-4F);
    }

    @Test
    void legacyLargeEndsOnThePixelDefaultToo() {
        AtomChatConfig config = new AtomChatConfig();
        config.cornerStyle = "large";
        config.migrateCornerStyle();
        config.migratePixelRadius();
        assertEquals(20f, config.cornerRadius, 1e-4F,
                "legacy large folds to 28 first, then rescales to the 20px default");
    }

    /**
     * The migration has to carry the whole old span, not one value: the old
     * slider ran 0..s(28) and the new one 0..s(20) — the same span under two
     * scales — so rescaling preserves each stored radius's drawn factor.
     * Mapping only the 28 default would thicken every hand-dragged radius:
     * the old medium 14 would read factor 0.7 instead of the 0.5 it was set to.
     */
    @Test
    void everyLegacyValueKeepsItsDrawnFactor() {
        AtomChatConfig medium = new AtomChatConfig();
        medium.cornerRadius = 14f;
        medium.migratePixelRadius();
        assertEquals(10f, medium.cornerRadius, 1e-4F,
                "the old medium 14 keeps factor 0.5");
        assertEquals(medium.cornerRadius / 20.0F, 14.0F / 28.0F, 1e-4F,
                "same factor before and after the scale change");

        // The old small maps just as exactly, and lands inside the new span.
        AtomChatConfig small = new AtomChatConfig();
        small.cornerRadius = 10f;
        small.migratePixelRadius();
        assertEquals(10.0F / 1.4F, small.cornerRadius, 1e-4F,
                "the old small 10 keeps factor ~0.357");
        assertEquals(small.cornerRadius / 20.0F, 10.0F / 28.0F, 1e-4F,
                "same factor before and after the scale change");
    }

    @Test
    void pixelRadiusMigratesExactlyOnce() {
        AtomChatConfig config = new AtomChatConfig();
        config.cornerRadius = 14f;
        config.migratePixelRadius();
        assertEquals(10f, config.cornerRadius, 1e-4F);
        // A second run is a no-op — the flag, not the value, is the guard:
        // the two scales overlap, so no stored value can identify its scale.
        config.migratePixelRadius();
        assertEquals(10f, config.cornerRadius, 1e-4F, "a second run must not divide again");
    }

    @Test
    void pixelRadiusClampsIntoTheSliderSpan() {
        // Past the OLD slider maximum (s(28) = 35), a hand-edited file still
        // has to land inside the new 0..s(20) span.
        AtomChatConfig high = new AtomChatConfig();
        high.cornerRadius = 40f;
        high.migratePixelRadius();
        assertEquals(com.atom.chat.ui.UiTokens.s(20), high.cornerRadius, 0.0F,
                "values past the slider maximum clamp to s(20)");

        AtomChatConfig low = new AtomChatConfig();
        low.cornerRadius = -2f;
        low.migratePixelRadius();
        assertEquals(0f, low.cornerRadius, 0.0F, "negative clamps to square");

        // Already on the new scale (stamped), an in-range value passes through
        // untouched: this is the path a migrated config takes on every reload.
        AtomChatConfig mid = new AtomChatConfig();
        mid.cornerRadius = 8f;
        mid.pixelRadiusMigrated = true;
        mid.migratePixelRadius();
        assertEquals(8f, mid.cornerRadius, 0.0F);
    }
}
