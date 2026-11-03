package com.atom.chat.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActivationAndBlurTest {
    @Test void defaultsPreserveCurrentBehavior() {
        var config = new AtomChatConfig();
        assertTrue(config.enabled);
        assertEquals(1.0f, config.effectiveBlurStrength());
    }

    @Test void blurStrengthIsBoundedAndInvalidValuesUseDefault() {
        var config = new AtomChatConfig();
        config.blurStrength = -1;
        assertEquals(0.25f, config.effectiveBlurStrength());
        config.blurStrength = 4;
        assertEquals(2.0f, config.effectiveBlurStrength());
        config.blurStrength = Float.NaN;
        assertEquals(1.0f, config.effectiveBlurStrength());
    }
}
