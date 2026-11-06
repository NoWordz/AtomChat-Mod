package com.atom.chat.image;

import java.util.Locale;

/** Normalizes display names for the per-name skin fallback cache. */
public final class SkinNameCacheKey {
    private SkinNameCacheKey() {
    }

    /** Removes Minecraft section-sign formatting without compiling a regex per avatar draw. */
    public static String of(String name) {
        if (name == null) return null;
        String plain = stripFormatting(name).trim();
        return plain.isEmpty() ? null : plain.toLowerCase(Locale.ROOT);
    }

    static String stripFormatting(String name) {
        int marker = name.indexOf('§');
        if (marker < 0) return name;
        StringBuilder plain = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '§' && i + 1 < name.length()) {
                i++;
            } else {
                plain.append(ch);
            }
        }
        return plain.toString();
    }
}
