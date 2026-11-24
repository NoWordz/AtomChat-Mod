package com.atom.chat.chat;

import java.util.Base64;

/** Exact vanilla classification, independent of the presence of a signature. */
public record NativeMessageMeta(String signature, boolean captured, Tag tag, boolean deleted,
                                int addedTick, long session) {
    public static final NativeMessageMeta UNKNOWN = new NativeMessageMeta(null, false, null, false, -1, 0);
    public static final String DELETED_TEXT = "[message deleted]";
    public static final String DELETED_JSON = "{\"translate\":\"chat.deleted_marker\",\"color\":\"gray\",\"italic\":true}";
    public static final Tag DELETED_TAG = new Tag(0xd0d0d0, null,
            "{\"translate\":\"chat.tag.system\"}", null, "System");

    /** Sprite is true for 1.21 GUI sprites; otherwise the exact texture region is retained. */
    public record Icon(String name, String resource, boolean sprite, int u, int v,
                       int width, int height, int textureWidth, int textureHeight) {}
    public record Tag(int color, Icon icon, String tooltipJson, String tooltipText, String loggedName) {}

    public NativeMessageMeta {
        signature = validSignature(signature);
        if (deleted) tag = DELETED_TAG; // A modified tooltip can contain the original signed body.
    }

    public static String signature(byte[] bytes) {
        return bytes != null && bytes.length == 256 ? Base64.getEncoder().encodeToString(bytes) : null;
    }

    public static String validSignature(String value) {
        if (value == null || value.length() > 344) return null;
        try { return signature(Base64.getDecoder().decode(value)); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    public NativeMessageMeta persisted() {
        return new NativeMessageMeta(signature, captured, tag, deleted, -1, 0);
    }

    public NativeMessageMeta inSession(int tick,long epoch) {
        return new NativeMessageMeta(signature,captured,tag,deleted,tick,epoch);
    }
    public NativeMessageMeta asDeleted() {
        return new NativeMessageMeta(signature, captured, DELETED_TAG, true, addedTick, session);
    }
}
