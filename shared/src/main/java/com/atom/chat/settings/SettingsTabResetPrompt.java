package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.onboarding.TutorialSandbox;
import com.atom.chat.ui.ConfirmationDialog;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Confirmation captures the selected tab; cancellation has no persistence side effects. */
public final class SettingsTabResetPrompt {
    private final ConfirmationDialog dialog;
    private final Supplier<AtomChatConfig> config;
    private final Predicate<AtomChatConfig> save;
    private final Function<String, String> text;
    private final BiConsumer<String, Boolean> feedback;
    private final Runnable afterReset;

    public SettingsTabResetPrompt(ConfirmationDialog dialog, Supplier<AtomChatConfig> config,
            Predicate<AtomChatConfig> save, Function<String, String> text,
            BiConsumer<String, Boolean> feedback, Runnable afterReset) {
        this.dialog=dialog; this.config=config; this.save=save; this.text=text;
        this.feedback=feedback; this.afterReset=afterReset;
    }
    public boolean request(SettingsSection section, String chip, String label) {
        if (TutorialSandbox.active()) { feedback.accept("atomchat.tutorial.preview_only", false); return false; }
        if (SettingsTabDefaults.fieldNames(section, chip).isEmpty()) return false;
        var target = config.get();
        dialog.openDangerous(text.apply("atomchat.settings.reset.title"), text.apply("atomchat.settings.reset.body").formatted(label),
                text.apply("atomchat.settings.reset.cancel"), text.apply("atomchat.settings.reset.confirm"), () -> {
                    boolean saved;
                    try { saved = SettingsTabDefaults.reset(target, section, chip, () -> save.test(target)); }
                    catch (RuntimeException failure) {
                        com.atom.chat.AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("ui", "Failed to reset settings tab {}/{}"), section, chip, failure);
                        saved = false;
                    }
                    if (saved) afterReset.run();
                    feedback.accept(saved ? "atomchat.settings.reset.success" : "atomchat.settings.reset.failed", !saved);
                });
        return true;
    }
}
