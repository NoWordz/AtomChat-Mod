package com.atom.chat.render;
/** Keeps Minecraft's texture binding cache in sync with independent glyph uploads. */
public final class NativeGlyphUpload {
    private NativeGlyphUpload() {}
    public static void bind(int id) { com.mojang.blaze3d.platform.GlStateManager._bindTexture(id); }
}
