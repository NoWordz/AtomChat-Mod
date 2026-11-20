package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * An editable colour and its live binding to a client config field.
 */
public final class SettingsColor {
    private final String id;
    private final String titleKey;
    private final String group;
    private final IntSupplier getter;
    private final IntConsumer setter;

    public SettingsColor(String id, String titleKey, String group, IntSupplier getter, IntConsumer setter) {
        this.id = id;
        this.titleKey = titleKey;
        this.group = group;
        this.getter = getter;
        this.setter = setter;
    }

    /** Sub-group inside the colours section: "bubble" or "ui". */
    public String group() {
        return group;
    }

    public String id() {
        return id;
    }

    public String titleKey() {
        return titleKey;
    }

    public int value() {
        return getter.getAsInt();
    }

    /** Applies the colour and writes {@code atomchat-client.json} immediately. */
    public void apply(int color) {
        setter.accept(color);
        AtomChatConfig.save(AtomChatConfig.get());
    }
}
