package com.atom.chat.render;

/**
 * The surface a run of text sits on — the single source of the soft-shadow
 * policy for chat text.
 *
 * <p>Capsule-family text takes a backing shadow: a capsule's tint is a
 * theme/config value that can sit close to the text colour it carries (the
 * quote pill holds the quoted name over the same capsule tint), so a shadow is
 * what keeps it legible across themes. {@link #NAME_BAND} takes one too, for
 * the same reason: it is drawn straight on the translucent panel over the
 * blurred world. {@link #BUBBLE} and {@link #PANEL} do not — a bubble fill is
 * picked against its own text colour, and panel chrome already reads.</p>
 *
 * <p>The polarity of the shadow (light vs dark panel) is derived from the
 * surface's own background at draw time, not from the panel's — a hand-tuned
 * capsule colour need not share the panel's polarity.</p>
 */
public enum TextSurface {
    /** Secondary capsule: system messages, quote pills, time dividers, image placeholders. */
    CAPSULE(true),
    /** Sender name band above a bubble/avatar, drawn on the panel. */
    NAME_BAND(true),
    /** Message body inside a bubble. */
    BUBBLE(false),
    /** Panel chrome: header, list rows, settings, composer. */
    PANEL(false);

    private final boolean shadowed;

    TextSurface(boolean shadowed) {
        this.shadowed = shadowed;
    }

    /** Whether text on this surface takes the soft backing shadow. */
    public boolean shadowed() {
        return shadowed;
    }
}
