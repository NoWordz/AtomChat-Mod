package com.atom.chat.font;

import com.atom.chat.AtomChat;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontVariation;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Typeface;

import java.io.InputStream;
import java.util.Locale;

/**
 * Loads and caches Skia fonts. Prefers a bundled open-source CJK font when present,
 * otherwise falls back to a system font so development can proceed without assets.
 */
public final class FontManager {
    /** Drop a licensed font file here (e.g. Noto Sans SC) to bundle it. */
    private static final String BUNDLED_FONT = "/assets/atomchat/font/bundled.otf";
    /** True-bold face (same character set as the bundled regular) for headings. */
    private static final String BUNDLED_BOLD_FONT = "/assets/atomchat/font/bundled-bold.otf";
    /** Faux-bold for thin system fallback fonts; a real bundled font makes this redundant. */
    private static boolean usingFallbackTypeface;
    private static Typeface defaultTypeface;
    private static boolean boldLookupDone;
    private static Typeface boldTypeface;
    private static final java.util.Map<String, Font> CACHE = new java.util.HashMap<>();
    private static final java.util.Map<String, Font> BOLD_CACHE = new java.util.HashMap<>();

    private FontManager() {
    }

    public static Typeface getDefaultTypeface() {
        if (defaultTypeface != null) {
            return defaultTypeface;
        }

        // Bundled font first.
        try (InputStream in = FontManager.class.getResourceAsStream(BUNDLED_FONT)) {
            if (in != null) {
                defaultTypeface = mediumWeight(Typeface.makeFromData(Data.makeFromBytes(in.readAllBytes())));
                usingFallbackTypeface = false;
                AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Loaded bundled font {}"), BUNDLED_FONT);
                return defaultTypeface;
            }
        } catch (Exception e) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Failed to load bundled font, falling back to system font"), e);
        }

        // System fallbacks (Windows/other).
        usingFallbackTypeface = true;
        String[] candidates = {"Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", "Arial"};
        for (String name : candidates) {
            Typeface tf = Typeface.makeFromName(name, FontStyle.NORMAL);
            if (tf != null && !tf.getFamilyName().toLowerCase(Locale.ROOT).contains("arial") || tf != null && tf.getFamilyName().toLowerCase(Locale.ROOT).contains("yahei")) {
                defaultTypeface = tf;
                AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Using system font {}"), tf.getFamilyName());
                return defaultTypeface;
            }
            if (tf != null) {
                defaultTypeface = tf;
                AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Using system font {}"), tf.getFamilyName());
                return defaultTypeface;
            }
        }
        defaultTypeface = Typeface.makeDefault();
        return defaultTypeface;
    }

    /** Variable fonts are cloned at Medium (wght 500) so text is not too thin. */
    private static Typeface mediumWeight(Typeface face) {
        try {
            if (face.getVariations().length > 0) {
                return face.makeClone(new FontVariation("wght", 500));
            }
        } catch (Throwable t) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Variable font weight clone failed, using default instance"), t);
        }
        return face;
    }

    public static Font font(float size) {
        String key = size + "px";
        Font cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Font font = new Font(getDefaultTypeface(), size);
        if (usingFallbackTypeface) {
            font.setEmboldened(true);
        }
        CACHE.put(key, font);
        return font;
    }

    /**
     * A separate cached bold face for headings and labels that need more weight
     * than the regular medium body. Prefers the bundled true-bold file —
     * {@code setEmboldened(true)} is a stroke smear that looks mushy at small
     * sizes — and only falls back to faux-bold when that file is absent.
     */
    public static Font boldFont(float size) {
        String key = size + "px-bold";
        Font cached = BOLD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Typeface bold = getBoldTypeface();
        if (bold != null) {
            Font font = new Font(bold, size);
            BOLD_CACHE.put(key, font);
            return font;
        }
        Font font = new Font(getDefaultTypeface(), size);
        font.setEmboldened(true);
        BOLD_CACHE.put(key, font);
        return font;
    }

    /** Bundled true-bold typeface, or {@code null} when the file is missing. */
    private static Typeface getBoldTypeface() {
        if (boldLookupDone) {
            return boldTypeface;
        }
        boldLookupDone = true;
        try (InputStream in = FontManager.class.getResourceAsStream(BUNDLED_BOLD_FONT)) {
            if (in != null) {
                boldTypeface = Typeface.makeFromData(Data.makeFromBytes(in.readAllBytes()));
                AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Loaded bundled bold font {}"), BUNDLED_BOLD_FONT);
            }
        } catch (Exception e) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("resource", "Failed to load bundled bold font, falling back to faux bold"), e);
        }
        return boldTypeface;
    }
}
