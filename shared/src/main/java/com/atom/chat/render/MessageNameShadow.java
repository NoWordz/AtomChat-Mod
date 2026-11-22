package com.atom.chat.render;

import com.atom.chat.text.RichTextLayout.RichLine;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageFilter;

import java.util.List;

/** A name's backing mask can remain static while the live glyphs or inline items move. */
public final class MessageNameShadow {
    private final ChatHudRasterCache ink;
    private float density;
    private ImageFilter lastFilter;

    public MessageNameShadow(int maxNames) {
        ink = new ChatHudRasterCache(maxNames, 7F);
    }

    public void draw(Canvas canvas, Font font, List<RichLine> lines, float width,
                     float x, float centerY, float lineHeight, float density, boolean soft) {
        draw(canvas, font, lines, width, x, centerY, lineHeight, density,
                soft ? SkiaFontRenderer.backingShadow() : null);
    }

    void draw(Canvas canvas, Font font, List<RichLine> lines, float width,
              float x, float centerY, float lineHeight, float density, ImageFilter filter) {
        if (lines.isEmpty()) return;
        if (filter == null) {
            RichTextRenderer.drawBacking(canvas, font, lines, x, centerY, lineHeight, null);
            return;
        }
        if (this.density != density || lastFilter != filter) {
            ink.clear();
            this.density = density;
            lastFilter = filter;
        }
        Image mask = ink.get(lines);
        float height = lines.size() * lineHeight;
        if (mask == null) {
            mask = ink.bake(lines, width, height, density, (target, inset) ->
                    RichTextRenderer.drawBacking(target, font, lines, inset, inset + height / 2F,
                            lineHeight, filter));
        }
        if (mask != null) {
            ink.draw(canvas, mask, x, centerY - height / 2F, density);
        } else {
            RichTextRenderer.drawBacking(canvas, font, lines, x, centerY, lineHeight, filter);
        }
    }

    public void clear() {
        ink.clear();
    }
}
