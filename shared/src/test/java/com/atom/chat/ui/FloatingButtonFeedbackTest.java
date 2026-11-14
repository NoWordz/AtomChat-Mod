package com.atom.chat.ui;

import com.atom.chat.audio.FeedbackCue;
import com.atom.chat.audio.FeedbackSoundController;
import com.atom.chat.config.AtomChatConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FloatingButtonFeedbackTest {
    @Test void floatingActionJoinsTheExistingButtonCueBatch() throws Exception {
        var field = FeedbackSoundController.class.getDeclaredField("pending");
        field.setAccessible(true);
        var configField = AtomChatConfig.class.getDeclaredField("instance");
        configField.setAccessible(true);
        Object old = configField.get(null);
        var config = new AtomChatConfig();
        config.feedbackSoundEnabled = false;
        configField.set(null, config);
        FeedbackSoundController.beginAction();
        try {
            new ConfirmationSurface.Button().press();
            assertEquals(FeedbackCue.BUTTON, field.get(null),
                    "floating actions must use the same cue and deduplication as other buttons");
            FeedbackSoundController.play(FeedbackCue.SEND);
            new ConfirmationSurface.Button().press();
            assertEquals(FeedbackCue.SEND, field.get(null), "a button cannot duplicate or replace a more specific cue");
        } finally {
            FeedbackSoundController.endAction();
            configField.set(null, old);
        }
    }
}
