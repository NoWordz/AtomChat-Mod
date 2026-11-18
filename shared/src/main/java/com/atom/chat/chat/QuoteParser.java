package com.atom.chat.chat;

/**
 * Parses AtomChat/e33chat's plain-text quote prefix:
 *
 * <pre>
 * 「引用 @Name: quoted text」actual message body
 * </pre>
 *
 * Both public chat and private /msg carry quotes as this text prefix, so the
 * receiving client can reconstruct the quote capsule without a server plugin.
 */
public final class QuoteParser {
    private QuoteParser() {
    }

    public record Quote(String quoteName, String quoteText, String body) {
    }

    /**
     * @return quote fields when {@code text} starts with a well-formed quote
     *         prefix, otherwise null.
     */
    public static Quote parse(String text) {
        if (text == null) {
            return null;
        }
        String s = text.stripLeading();
        if (!s.startsWith("「引用")) {
            return null;
        }
        int close = s.indexOf('」');
        if (close <= 0) {
            return null;
        }
        int at = s.indexOf('@');
        if (at < 0 || at >= close) {
            return null;
        }
        int colon = s.indexOf(':', at);
        if (colon < 0 || colon >= close) {
            return null;
        }
        String name = s.substring(at + 1, colon).trim();
        if (name.isEmpty()) {
            return null;
        }
        String quoteText = s.substring(colon + 1, close).trim();
        String body = s.substring(close + 1);
        return new Quote(name, quoteText, body);
    }
}
