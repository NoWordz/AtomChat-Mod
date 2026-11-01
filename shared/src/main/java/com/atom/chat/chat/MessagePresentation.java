package com.atom.chat.chat;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Parses decorated server chat lines into structured player-name + content pairs.
 *
 * <p>This is a trimmed port of e33chat's MessagePresentation (MIT, same author).
 * It deliberately works on plain strings only — callers use the returned
 * offsets ({@code labelEnd}/{@code contentStart}) to slice styled {@code Text}
 * into rich sender/content parts.
 */
public final class MessagePresentation {
    private MessagePresentation() {
    }

    public record PlayerLine(String playerName, String displayLabel, String content,
                             int nameStart, int nameEnd, int labelEnd, int contentStart) {
    }

    /**
     * Tries every online/known name (longest first to avoid substring mismatches)
     * against the raw chat line. Returns the first successful parse.
     */
    public static Optional<PlayerLine> parseDecoratedPlayerLine(
            String text, Collection<String> onlineNames
    ) {
        if (text == null || onlineNames == null) {
            return Optional.empty();
        }
        return onlineNames.stream()
                .filter(n -> n != null && !n.isBlank())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .flatMap(name -> parseGeneric(text, name).stream())
                .findFirst();
    }

    /**
     * Generic separator-skipping approach. Finds a player name with word-boundary
     * checks, then skips any mix of whitespace and common separator characters
     * (>, :, ：, », -, |) to locate the message content.
     */
    static Optional<PlayerLine> parseGeneric(String text, String name) {
        if (text == null || name == null) {
            return Optional.empty();
        }
        String cleanName = name.replaceAll("§.", "");
        if (cleanName.isEmpty()) {
            return Optional.empty();
        }
        int[] hit = indexOfNameTolerant(text, cleanName);
        if (hit == null) {
            return Optional.empty();
        }
        int idx = hit[0];
        int nameEndRaw = hit[1];

        // Broadcast-spoof guard: separators before the name mean a label like
        // "系统>>Steve" — real chat keeps only decorations before the name.
        String beforeName = text.substring(0, idx);
        if (beforeName.indexOf('>') >= 0 || beforeName.indexOf('»') >= 0
                || beforeName.indexOf('|') >= 0 || beforeName.indexOf(':') >= 0
                || beforeName.indexOf('：') >= 0) {
            return Optional.empty();
        }
        // Prose guard: real chat formats only decorate before the sender
        // (bracket groups, styled tags, symbols). Prose out there — CJK text or
        // two or more letter/digit words — means the line is a plugin sentence
        // about the player ("你邀请了 Steve: 点击接受"), not a chat line;
        // parsing it would push the prose into the sender label and split the
        // message across the name and the bubble. TrChat's own shapes
        // ("[生存] Steve: hi", "Steve: hi", "<Steve> hi") stay decorative.
        if (hasProseBeforeName(text, idx)) {
            return Optional.empty();
        }
        if (isBroadcastLabelPrefix(text, idx)) {
            return Optional.empty();
        }

        int minLen = 3;
        if (idx > 0 && text.charAt(idx - 1) == '<') {
            int closeAngle = text.indexOf('>', nameEndRaw);
            if (closeAngle >= 0 && closeAngle - (idx - 1) <= 64) {
                minLen = 1;
            }
        }
        if (minLen == 3 && idx > 0) {
            int bracketClose = text.lastIndexOf(']', idx);
            if (bracketClose >= 0 && idx - bracketClose <= 2) {
                int bracketOpen = text.lastIndexOf('[', bracketClose);
                if (bracketOpen >= 0) {
                    int after = nameEndRaw;
                    if (after < text.length()) {
                        char next = text.charAt(after);
                        if (next == ':' || next == '：') {
                            minLen = 1;
                        }
                    }
                }
            }
        }
        if (minLen == 3) {
            int after = nameEndRaw;
            if (after < text.length()) {
                char next = text.charAt(after);
                if (next == ':' || next == '：') {
                    minLen = 1;
                }
            }
        }
        if (cleanName.length() < minLen) {
            return Optional.empty();
        }

        int decorativeLen = countDecorativePrefix(text, idx);
        if (idx - decorativeLen >= 30) {
            return Optional.empty();
        }

        if (idx > 0) {
            char prev = text.charAt(idx - 1);
            boolean prevIsColorCode = prev == '§' || (idx >= 2 && text.charAt(idx - 2) == '§');
            if (!prevIsColorCode && (Character.isLetterOrDigit(prev) || prev == '_')) {
                int openAngle = text.lastIndexOf('<', idx);
                int closeAngle = text.indexOf('>', nameEndRaw);
                if (openAngle >= 0 && closeAngle >= 0 && closeAngle - openAngle <= 64) {
                    // A letter immediately before the name inside <...> means the
                    // candidate is a suffix of a longer word (<Notch> matching
                    // "tch"), not a real sender. Decorated names are handled by
                    // the "prev is ']'" path above, not this branch.
                    return Optional.empty();
                } else {
                    int bracketClose = text.lastIndexOf(']', idx);
                    if (bracketClose >= 0) {
                        int bracketOpen = text.lastIndexOf('[', bracketClose);
                        if (bracketOpen < 0 || idx - bracketClose > 2) {
                            return Optional.empty();
                        }
                    } else {
                        return Optional.empty();
                    }
                }
            }
        }

        int after = nameEndRaw;
        if (after < text.length()) {
            char next = text.charAt(after);
            if (Character.isLetterOrDigit(next) || next == '_' || next == '-') {
                return Optional.empty();
            }
        }

        int labelEnd = labelTailEnd(text, after);
        int sep = skipSeparators(text, after);
        if (sep >= text.length()) {
            // Kaomoji guard: a bracket-only message like "<Steve> (￣▽￣)" is
            // shaped exactly like a name-suffix decoration, but a real label
            // decoration is never the entire message. When bracket-skipping
            // consumes every remaining character, the trailing bracket group
            // is content, not decoration: re-skip without bracket pairs and
            // clamp the display label back to the bare name. Without this the
            // parse fails and NCR-converted echoes render as system bubbles.
            int loose = skipSeparators(text, after, false);
            if (loose <= after || loose >= text.length()) {
                return Optional.empty();
            }
            sep = loose;
            labelEnd = after;
        }
        if (sep <= after) {
            return Optional.empty();
        }

        String displayLabel = text.substring(0, labelEnd);
        if (idx > 0 && text.charAt(idx - 1) == '<') {
            // The closing angle bracket is a separator, not part of the label;
            // the opening one must be dropped too so "<Steve> hi" reads "Steve"
            // and "[VIP]<Steve> hi" reads "[VIP]Steve". § pairs are stripped
            // here because this branch synthesises the label from a raw slice
            // (with a split name it would otherwise show bare codes), while
            // the non-angle path renders from already-clean rich text.
            displayLabel = text.substring(0, idx - 1).replaceAll("§.", "")
                    + text.substring(idx, labelEnd).replaceAll("§.", "");
        } else if (text.charAt(0) == '<' && nameEndRaw < text.length()
                && text.charAt(nameEndRaw) == '>') {
            // "<[Team]Steve> hi": the whole decorated name sits inside the angle
            // brackets. e33chat's cleanNameArea strips the wrapping pair and
            // keeps the inner decoration as the label ("<[Team]Steve>" ->
            // "[Team]Steve"); that branch was dropped in the port (0.2.5 hunt),
            // leaking the opening '<' onto other players' view of the name.
            displayLabel = text.substring(1, labelEnd).replaceAll("§.", "");
        }
        return Optional.of(new PlayerLine(cleanName, displayLabel, text.substring(sep).strip(),
                idx, after, labelEnd, sep));
    }

    /**
     * G3 (e33chat parity): finds the candidate name in the raw line even when
     * server-side decoration splits it with § color pairs — "S§6t§beve" for
     * "Steve". A plain {@code indexOf(cleanName)} goes blind in that case.
     *
     * <p>Returns {@code {rawStart, rawEnd}} where rawStart is the index of the
     * first real name character and rawEnd is the raw index right after the
     * last one (interleaved § pairs included, trailing pairs excluded), or
     * {@code null} when the name does not occur. The match must start at a
     * real (non-§) character so leading decoration is never consumed.
     */
    static int[] indexOfNameTolerant(String text, String name) {
        int n = text.length();
        for (int i = 0; i < n; i++) {
            if (text.charAt(i) != name.charAt(0)) {
                continue;
            }
            int ti = i + 1;
            boolean matched = true;
            for (int k = 1; k < name.length(); k++) {
                while (ti + 1 < n && text.charAt(ti) == '§') {
                    ti += 2;
                }
                if (ti >= n || text.charAt(ti) != name.charAt(k)) {
                    matched = false;
                    break;
                }
                ti++;
            }
            if (matched) {
                return new int[]{i, ti};
            }
        }
        return null;
    }

    /**
     * Finds the end of the player label tail after the bare matched name.
     *
     * <p>Label tails may contain § color-code pairs and whole bracket
     * decorations such as {@code [AFK]}, {@code (VIP)}, {@code <AFK>} or
     * {@code 【挂机】}. Whitespace is traversed but is not included as part of
     * the label unless a decoration follows it, so trailing separator space is
     * left to {@link #skipSeparators}.
     */
    private static int labelTailEnd(String text, int from) {
        int labelEnd = from;
        int i = from;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (ch == '§' && i + 1 < text.length()) {
                i += 2;
                labelEnd = i;
                continue;
            }
            char close = closeFor(ch);
            if (close != '\0') {
                int end = text.indexOf(close, i + 1);
                if (end > i && end - i <= 32) {
                    i = end + 1;
                    labelEnd = i;
                    continue;
                }
                break;
            }
            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }
            break;
        }
        return labelEnd;
    }

    private static char closeFor(char open) {
        return switch (open) {
            case '[' -> ']';
            case '(' -> ')';
            case '<' -> '>';
            case '【' -> '】';
            default -> '\0';
        };
    }

    /**
     * Skips the separator run between name and content: whitespace, common chat
     * separators, § color pairs, and whole bracket pairs so name-suffix
     * decorations parse the same way prefix decorations do.
     */
    public static int skipSeparators(String text, int from) {
        return skipSeparators(text, from, true);
    }

    static int skipSeparators(String text, int from, boolean skipBrackets) {
        int sep = from;
        while (sep < text.length()) {
            char ch = text.charAt(sep);
            if (ch == '§' && sep + 1 < text.length()) {
                sep += 2;
                continue;
            }
            if (skipBrackets && (ch == '[' || ch == '(' || ch == '<' || ch == '【')) {
                char close = ch == '[' ? ']' : ch == '(' ? ')' : ch == '<' ? '>' : '】';
                int end = text.indexOf(close, sep + 1);
                if (end > sep && end - sep <= 32) {
                    sep = end + 1;
                    continue;
                }
            }
            if (Character.isWhitespace(ch) || ch == '>' || ch == ':'
                    || ch == '：' || ch == '»' || ch == '-' || ch == '|') {
                sep++;
            } else {
                break;
            }
        }
        return sep;
    }

    /**
     * True when the gap between name and content holds only whitespace — the
     * shape of a broadcast sentence (Steve joined the game), not chat.
     */
    public static boolean isWhitespaceOnlyGap(String text, int from, int to) {
        if (text == null || to <= from) {
            return false;
        }
        for (int i = from; i < to && i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static final java.util.Set<String> BROADCAST_LABELS = java.util.Set.of(
            "系统", "公告", "服务器", "广播", "提示", "通知",
            "system", "server", "notice", "broadcast", "announcement", "alert");

    /**
     * Prose detector for the sender prefix zone. CJK text outside brackets is
     * always prose (real chat prefixes are bracket groups like "[生存]"); two
     * or more letter/digit words are English prose ("You invited Steve: ...").
     * A single styled tag ("VIP") stays decoration, as do § pairs and symbols.
     */
    static boolean hasProseBeforeName(String text, int nameIdx) {
        int tokens = 0;
        boolean inWord = false;
        int i = 0;
        while (i < nameIdx) {
            char ch = text.charAt(i);
            if (ch == '§' && i + 1 < nameIdx) {
                i += 2;
                inWord = false;
                continue;
            }
            char close = closeFor(ch);
            if (close != '\0') {
                int end = text.indexOf(close, i + 1);
                if (end > i && end < nameIdx) {
                    i = end + 1;
                    inWord = false;
                    continue;
                }
            }
            if (isProseScript(ch)) {
                return true;
            }
            if (Character.isLetterOrDigit(ch)) {
                if (!inWord) {
                    tokens++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
            i++;
        }
        return tokens >= 2;
    }

    private static boolean isProseScript(char ch) {
        return (ch >= 0x3040 && ch <= 0x30FF)   // kana
                || (ch >= 0x3400 && ch <= 0x4DBF)   // CJK extension A
                || (ch >= 0x4E00 && ch <= 0x9FFF)   // CJK unified ideographs
                || (ch >= 0xAC00 && ch <= 0xD7AF)   // hangul
                || (ch >= 0xF900 && ch <= 0xFAFF)   // CJK compatibility
                || (ch >= 0xFF01 && ch <= 0xFF60
                    && ch != 0xFF08 && ch != 0xFF09);  // fullwidth （） are decoration brackets, not prose
    }

    static boolean isBroadcastLabelPrefix(String cleanText, int nameIdx) {
        String zone = cleanText.substring(0, nameIdx).trim();
        if (zone.isEmpty()) {
            return false;
        }
        while (zone.length() >= 2) {
            char open = zone.charAt(0);
            char close = zone.charAt(zone.length() - 1);
            if ((open == '[' && close == ']') || (open == '【' && close == '】')
                    || (open == '<' && close == '>') || (open == '(' && close == ')')) {
                zone = zone.substring(1, zone.length() - 1).trim();
            } else {
                break;
            }
        }
        if (zone.isEmpty()) {
            return false;
        }
        return BROADCAST_LABELS.contains(zone.toLowerCase(java.util.Locale.ROOT));
    }

    private static int countDecorativePrefix(String text, int upTo) {
        int i = 0;
        while (i < upTo) {
            char c = text.charAt(i);
            if (c == '[') {
                int close = text.indexOf(']', i + 1);
                if (close >= 0 && close < upTo) {
                    i = close + 1;
                    continue;
                }
            }
            if (c == '<') {
                int close = text.indexOf('>', i + 1);
                if (close >= 0 && close < upTo) {
                    i = close + 1;
                    continue;
                }
            }
            if (c == '§' && i + 1 < upTo) {
                i += 2;
                continue;
            }
            if (Character.isWhitespace(c) || !Character.isLetterOrDigit(c)) {
                i++;
                continue;
            }
            break;
        }
        return i;
    }
}
