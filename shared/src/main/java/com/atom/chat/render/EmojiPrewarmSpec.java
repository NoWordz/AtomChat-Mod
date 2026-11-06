package com.atom.chat.render;

/**
 * One prewarm request: an emoji glyph at a font size and an RGB tint.
 *
 * <p>The tint is masked to {@code 0xFFFFFF} because that is exactly what
 * {@code EmojiGlyphCache}'s key stores ({@code paint.getColor() & 0xFFFFFF});
 * alpha is applied at draw time, never baked into the bitmap.
 */
record EmojiPrewarmSpec(float size, String text, int rgb) {
}
