package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * One switch row in a settings section: a title, a line explaining what the
 * option actually does, and a live binding to a config field.
 *
 * <p>Writing the value and persisting it are deliberately fused in
 * {@link #set(boolean)}: every toggle saves the config file on the spot, so no
 * option in this UI can ever promise "takes effect immediately" and then
 * quietly wait for a restart (or for the screen to close).</p>
 */
public final class SettingsItem {
    private final String id;
    private final String titleKey;
    private final String subtitleKey;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private final boolean persistAtomChatConfig;
    /**
     * When present and returning false, the row is drawn dimmed and refuses
     * input. Used for options another setting currently overrides — the blur
     * switch while a custom wallpaper owns the panel background.
     */
    private final BooleanSupplier available;

    public SettingsItem(String id, String titleKey, String subtitleKey,
                        BooleanSupplier getter, Consumer<Boolean> setter) {
        this(id, titleKey, subtitleKey, getter, setter, null);
    }

    public SettingsItem(String id, String titleKey, String subtitleKey,
                        BooleanSupplier getter, Consumer<Boolean> setter,
                        BooleanSupplier available) {
        this(id, titleKey, subtitleKey, getter, setter, available, true);
    }

    private SettingsItem(String id, String titleKey, String subtitleKey,
                         BooleanSupplier getter, Consumer<Boolean> setter,
                         BooleanSupplier available, boolean persistAtomChatConfig) {
        this.id = id;
        this.titleKey = titleKey;
        this.subtitleKey = subtitleKey;
        this.getter = getter;
        this.setter = setter;
        this.available = available;
        this.persistAtomChatConfig = persistAtomChatConfig;
    }

    public static SettingsItem addon(com.atom.chat.api.ui.SettingsUi.Toggle toggle) {
        return new SettingsItem(toggle.id(), toggle.titleKey(), toggle.descriptionKey(),
                toggle.value(), toggle.setValue(), null, false);
    }

    public String id() {
        return id;
    }

    public String titleKey() {
        return titleKey;
    }

    public String subtitleKey() {
        return subtitleKey;
    }

    public boolean value() {
        return !performanceLocked() && getter.getAsBoolean();
    }

    /** False when another setting currently overrides this one. */
    public boolean available() {
        return !performanceLocked() && (available == null || available.getAsBoolean());
    }

    public boolean performanceLocked() {
        return switch (id) {
            case "blur", "motion", "entry", "poke", "page_nav_style", "fun_effects", "experimental_effects" ->
                    AtomChatConfig.get().performanceModeEnabled();
            default -> false;
        };
    }

    /** Applies the value and writes {@code atomchat-client.json} immediately. */
    public void set(boolean value) {
        if (performanceLocked()) return;
        setter.accept(value);
        if (persistAtomChatConfig) AtomChatConfig.save(AtomChatConfig.get());
    }
}
