package com.atom.chat.settings;

import java.util.Objects;
import java.util.function.IntConsumer;

/** Draft state for an inline number. Typing stays local until the host commits it. */
public final class SettingsNumberEditor {
    private static final int MAX_DIGITS = 6;
    private String id;
    private String text = "";
    private IntConsumer commit;
    private boolean replaceOnInput;

    public boolean active() { return commit != null; }
    public boolean editing(String id) { return active() && Objects.equals(this.id, id); }
    public String text() { return text; }

    public void open(String id, int value, IntConsumer commit) {
        open(id, value, commit, false);
    }

    public void open(String id, int value, IntConsumer commit, boolean replaceOnInput) {
        this.commit = Objects.requireNonNull(commit);
        this.id = id;
        text = Integer.toString(value);
        this.replaceOnInput = replaceOnInput;
    }

    public void append(char digit) {
        if (!active() || digit < '0' || digit > '9') return;
        if (replaceOnInput || text.length() < MAX_DIGITS) {
            text = replaceOnInput ? String.valueOf(digit) : text + digit;
            replaceOnInput = false;
        }
    }

    public void backspace() {
        if (!active() || text.isEmpty()) return;
        text = replaceOnInput ? "" : text.substring(0, text.length() - 1);
        replaceOnInput = false;
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
        replaceOnInput = false;
        text = "";
    }
}
