package com.atom.chat.settings;

import java.util.Objects;
import java.util.function.IntConsumer;

/** Draft state for the inline history value. Typing stays local until the host commits it. */
public final class SettingsNumberEditor {
    private static final int MAX_DIGITS = 6;
    private String id;
    private String text = "";
    private IntConsumer commit;

    public boolean active() { return commit != null; }
    public boolean editing(String id) { return active() && Objects.equals(this.id, id); }
    public String text() { return text; }

    public void open(String id, int value, IntConsumer commit) {
        this.commit = Objects.requireNonNull(commit);
        this.id = id;
        text = Integer.toString(value);
    }

    public void append(char digit) {
        if (active() && digit >= '0' && digit <= '9' && text.length() < MAX_DIGITS) text += digit;
    }

    public void backspace() {
        if (active() && !text.isEmpty()) text = text.substring(0, text.length() - 1);
    }

    public void commit() {
        if (!active()) return;
        try {
            commit.accept(text.isEmpty() ? 0 : Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            // The existing editor leaves the stored value alone when the input is invalid.
        } finally {
            cancel();
        }
    }

    public void cancel() {
        id = null;
        commit = null;
        text = "";
    }
}
