package com.atom.chat.ui.input;

import java.util.Objects;

/** A discrete action and its press feedback, identified independently of the painted bounds. */
public record ClickTarget(Object id, Runnable press, Runnable clear, Runnable activate) {
    private static final Runnable NO_ACTION = () -> {};
    private static final ClickTarget BLOCKED = new ClickTarget(null, NO_ACTION, NO_ACTION, NO_ACTION);

    public ClickTarget {
        Objects.requireNonNull(press);
        Objects.requireNonNull(clear);
        Objects.requireNonNull(activate);
    }

    public static ClickTarget action(Object id, Runnable activate) {
        return new ClickTarget(Objects.requireNonNull(id), NO_ACTION, NO_ACTION, activate);
    }

    public static ClickTarget blocked() {
        return BLOCKED;
    }
}
