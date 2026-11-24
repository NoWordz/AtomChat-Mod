package com.atom.chat.render;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.font.GlyphInfo;
/** Captures MC's raster recipe, never its mutable atlas. Provider ownership remains with MC. */
public final class NativeGlyphSource {
    private NativeGlyphSource() {}
    public static NativeGlyphRaster capture(GlyphInfo glyph) {
        NativeGlyphRaster[] result = new NativeGlyphRaster[1];
        glyph.bake(sheet -> { result[0] = new NativeGlyphRaster(new NativeGlyphRaster.Source() {
            public int width() { return sheet.getPixelWidth(); }
            public int height() { return sheet.getPixelHeight(); }
            public boolean colored() { return sheet.isColored(); }
            public float left() { return sheet.getLeft(); }
            public float right() { return sheet.getRight(); }
            public float top() { return sheet.getUp() - 3F; }
            public float bottom() { return sheet.getDown() - 3F; }
            public void upload() { sheet.upload(0, 0); }
        }); return new net.minecraft.client.gui.font.glyphs.EmptyGlyph(); });
        return result[0];
    }
}
