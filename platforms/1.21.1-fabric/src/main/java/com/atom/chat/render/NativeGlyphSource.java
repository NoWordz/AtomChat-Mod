package com.atom.chat.render;
import net.minecraft.client.font.RenderableGlyph;
import net.minecraft.client.font.Glyph;
/** Captures MC's raster recipe, never its mutable atlas. Provider ownership remains with MC. */
public final class NativeGlyphSource {
    private NativeGlyphSource() {}
    public static NativeGlyphRaster capture(Glyph glyph) {
        NativeGlyphRaster[] result = new NativeGlyphRaster[1];
        glyph.bake(sheet -> { result[0] = new NativeGlyphRaster(new NativeGlyphRaster.Source() {
            public int width() { return sheet.getWidth(); }
            public int height() { return sheet.getHeight(); }
            public boolean colored() { return sheet.hasColor(); }
            public float left() { return sheet.getXMin(); }
            public float right() { return sheet.getXMax(); }
            public float top() { return sheet.getYMin(); }
            public float bottom() { return sheet.getYMax(); }
            public void upload() { sheet.upload(0, 0); }
        }); return net.minecraft.client.font.EmptyGlyphRenderer.INSTANCE; });
        return result[0];
    }
}
