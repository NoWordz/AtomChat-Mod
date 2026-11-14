package com.atom.chat.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActivationAndBlurTest {
    @Test void defaultsEnableChatWithTwoHundredPercentBlur() {
        var config = new AtomChatConfig();
        assertTrue(config.enabled);
        assertEquals(2.0f, config.effectiveBlurStrength());
    }

    @Test void blurStrengthIsBoundedAndInvalidValuesUseDefault() {
        var config = new AtomChatConfig();
        config.blurStrength = -1;
        assertEquals(0.25f, config.effectiveBlurStrength());
        config.blurStrength = 4;
        assertEquals(3.0f, config.effectiveBlurStrength());
        config.blurStrength = 2.75F;
        assertEquals(2.75F, config.effectiveBlurStrength());
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            config.blurStrength = invalid;
            assertEquals(2.0f, config.effectiveBlurStrength());
        }
    }
}
