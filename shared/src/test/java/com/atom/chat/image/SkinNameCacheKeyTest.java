package com.atom.chat.image;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SkinNameCacheKeyTest {
    @Test
    void stripsSectionFormattingAndNormalizesWithoutRegex() {
        assertEquals("alice", SkinNameCacheKey.of(" §aAlice§r "));
        assertEquals("i", SkinNameCacheKey.of("I"));
        assertNull(SkinNameCacheKey.of(" § "));
        assertNull(SkinNameCacheKey.of(" §a§r "));
        assertNull(SkinNameCacheKey.of(null));
    }
}
