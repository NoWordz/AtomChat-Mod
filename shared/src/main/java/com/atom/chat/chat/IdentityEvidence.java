package com.atom.chat.chat;

/** Source of attribution, independent of transport channel and message kind. */
public enum IdentityEvidence {
    CONFLICT, NATIVE, TRANSLATION, ENTITY, CLICK, LEARNED, TEMPLATE, SERVER_TEMPLATE, TEXT;
    public boolean confirmsIdentity() { return this == NATIVE || this == ENTITY || this == TRANSLATION; }
}
