package com.niuqu.atomchat_signal;

import org.slf4j.LoggerFactory;

final class SignalRegistration {
    static final String MOD_ID = "atomchat_signal";

    private SignalRegistration() {}

    static void register() {
        // Keep this class free of AppearanceUi types so it can run on older hosts.
        try {
            Class.forName("com.atom.chat.api.ui.AppearanceUi", false, SignalRegistration.class.getClassLoader());
        } catch (ClassNotFoundException missingApi) {
            LoggerFactory.getLogger(MOD_ID).warn(
                    "[{}] Skipping appearance registration. The installed AtomChat lacks AppearanceUi. "
                            + "Use an AtomChat build with the Appearance API to enable this add-on.", MOD_ID);
            return;
        }
        SignalAppearance.register();
    }
}
