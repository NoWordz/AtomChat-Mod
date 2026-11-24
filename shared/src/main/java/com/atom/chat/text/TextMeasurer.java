package com.atom.chat.text;

/**
 * Pure text measurement hook so layout can run without a live font/Skia context.
 */
@FunctionalInterface
public interface TextMeasurer {
    float measure(String text);

    default boolean collapsibleSpace(RichText.RichRun source) { return true; }

    default float measure(String text, RichText.RichRun source) {
        return measure(text);
    }
}
