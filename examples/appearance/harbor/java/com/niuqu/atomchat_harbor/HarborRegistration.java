package com.niuqu.atomchat_harbor;

import org.slf4j.LoggerFactory;

final class HarborRegistration {
    static final String MOD_ID = "atomchat_harbor";

    private HarborRegistration() {}

    static void register() {
        // Keep this class free of AppearanceUi types so it can run on older hosts.
        try {
            Class.forName("com.atom.chat.api.ui.AppearanceUi", false, HarborRegistration.class.getClassLoader());
        } catch (ClassNotFoundException missingApi) {
            LoggerFactory.getLogger(MOD_ID).warn(
                    "[{}] Skipping appearance registration. The installed AtomChat lacks AppearanceUi. "
                            + "Use an AtomChat build with the Appearance API to enable this add-on.", MOD_ID);
            return;
        }
        HarborAppearance.register();
    }
}
