package com.atom.chat.render;
public final class NativeGlyphUpload {
    private NativeGlyphUpload() {}
    public static void bind(int id) { com.mojang.blaze3d.platform.GlStateManager._bindTexture(id); }
}
