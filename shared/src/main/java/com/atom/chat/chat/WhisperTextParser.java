package com.atom.chat.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * G1 (e33chat parity) text-layer whisper fallback.
 *
 * <p>Vanilla {@code /msg} lines are handled authoritatively by
 * {@link PrivateChatParser} via translation keys. Chat plugins (EssentialsX,
 * CMI, DeluxeChat, ...), hybrid setups and bot relays reformat private
 * messages as plain system-channel text, which used to fall through to the
 * public-chat guard — whose separator skipping mis-claims
 * {@code "[Steve -> me] hi"} as a public bubble from Steve.
 *
 * <p>This parser only claims lines with unmistakable whisper shape, and only
 * when one side resolves to the local player (or the literal
 * {@code 我/You/me} placeholder some plugins emit). When neither side is us
 * the line is left for the guard/gray fallback — admin-visible whisper
 * channels between two other players are not our business.
 *
 * <p>Pure strings, no Minecraft imports, so the shapes stay unit-testable
 * while the real plugin formats are only documented (no test server).
 */
public final class WhisperTextParser {
    private WhisperTextParser() {
    }

    /** A detected private-message line. {@code incoming=true} means the partner sent it. */
    public record WhisperHit(boolean incoming, String partnerDisplay, String content) {
    }

    private static final String ARROW = "(?:->|→|»)";

    /** CMI receiver side: "[/msg from [Steve]] hi" / "[/msg from Steve] hi". */
    private static final Pattern CMI_FROM =
            Pattern.compile("^\\[/msg from \\[?(.{1,64}?)\\]?\\]\\s*[:：]?\\s*(.+)$");
    /** CMI sender side: "[/msg sent -> [Steve]] hi". */
    private static final Pattern CMI_SENT =
            Pattern.compile("^\\[/msg sent -> \\[?(.{1,64}?)\\]?\\]\\s*[:：]?\\s*(.+)$");
    /** EssentialsX default: "[Steve -> me] hi" (bracketed arrow, colon optional). */
    private static final Pattern ARROW_BRACKETED = Pattern.compile(
            "^\\[(.{1,48}?)\\s*" + ARROW + "\\s*(.{1,48}?)\\]\\s*[:：]?\\s*(.+)$");
    /** DeluxeChat style: "Steve -> You : hi" (unbracketed arrow requires a colon). */
    private static final Pattern ARROW_COLON = Pattern.compile(
            "^(.{1,32}?)\\s*" + ARROW + "\\s*(.{1,32}?)\\s*[:：]\\s*(.+)$");
    /**
     * "Steve 悄悄地对你说: hi" / "Steve whispers to you: hi".
     *
     * <p>The colon is mandatory, and the sender capture may not contain a
     * separator. Both were lax before, and that is what let ordinary prose be
     * claimed: {@code "Sent a simulated whisper banner to your client"} has no
     * colon at all, so the sender capture ran to "Sent a simulated" and the rest
     * became the "message body" — a server command reply turned into a fake
     * private message and a banner. A real whisper line always separates the
     * sender from the body with a colon, and a separator inside the sender means
     * the match wandered into a sentence (the same rule {@link #cleanSide}
     * applies). Whitespace stays allowed: decorated names ("Steve the Great",
     * "Steve [Admin]") are real.</p>
     */
    private static final Pattern KEYWORD_IN = Pattern.compile(
            "^([^:：]{1,32}?)\\s*(?:悄悄地?对你说|悄悄(?:地)?跟你说|对你说|悄悄(?:地)?说|密语|"
                    + "whispers?(?:\\s+to\\s+you)?)\\s*[:：]\\s*(.+)$");
    /**
     * "你对Steve悄悄地说：hi" / "You whisper to Steve: hi". The EN branch
     * requires the trailing colon: with an optional colon the lazy partner
     * capture stops at the first character ("S" of "Steve") and never
     * backtracks, since the colon is not needed to complete the match.
     */
    private static final Pattern KEYWORD_OUT = Pattern.compile(
            "^(?:你(?:悄悄地?|私下)?对(.{1,32}?)悄悄?地?说\\s*[:：]?"
                    + "|[Yy]ou\\s+whispers?\\s+to\\s+([^:：]{1,32}?)\\s*[:：])"
                    + "\\s*(.+)$");

    /**
     * Tries the configured shapes in order. {@code ownProfile} is the local
     * player's profile name (undecorated); may be null, in which case only
     * the CMI/keyword literal forms can match.
     */
    public static WhisperHit tryParse(String line, String ownProfile) {
        if (line == null || line.length() > 256) {
            return null;
        }
        String text = line.strip();
        if (text.isEmpty()) {
            return null;
        }

        Matcher m = CMI_FROM.matcher(text);
        if (m.matches()) {
            return incoming(m.group(1), m.group(2));
        }
        m = CMI_SENT.matcher(text);
        if (m.matches()) {
            return outgoing(m.group(1), m.group(2), ownProfile);
        }
        m = ARROW_BRACKETED.matcher(text);
        if (m.matches()) {
            return arrow(m.group(1), m.group(2), m.group(3), ownProfile);
        }
        m = ARROW_COLON.matcher(text);
        if (m.matches()) {
            return arrow(m.group(1), m.group(2), m.group(3), ownProfile);
        }
        m = KEYWORD_OUT.matcher(text);
        if (m.matches()) {
            String partner = cleanSide(m.group(1) != null ? m.group(1) : m.group(2));
            String content = m.group(3).strip();
            if (partner != null && !content.isEmpty()) {
                return new WhisperHit(false, partner, content);
            }
            return null;
        }
        // KEYWORD_OUT first: "你对X悄悄地说" would otherwise be claimed by the
        // incoming keyword ("悄悄地说") with the prefix swallowed as the sender.
        m = KEYWORD_IN.matcher(text);
        if (m.matches()) {
            String sender = cleanSide(m.group(1));
            String content = m.group(2).strip();
            if (sender != null && !content.isEmpty()) {
                return new WhisperHit(true, sender, content);
            }
            return null;
        }
        return null;
    }

    private static WhisperHit arrow(String rawSideA, String rawSideB, String content, String ownProfile) {
        String a = cleanSide(rawSideA);
        String b = cleanSide(rawSideB);
        String body = content.strip();
        if (a == null || b == null || body.isEmpty()) {
            return null;
        }
        // Only claim when one side is us; otherwise it is an admin-visible
        // whisper channel between two other players — leave it alone.
        if (isOwnSide(b, ownProfile)) {
            return new WhisperHit(true, a, body);
        }
        if (isOwnSide(a, ownProfile)) {
            return new WhisperHit(false, b, body);
        }
        return null;
    }

    private static WhisperHit incoming(String partner, String content) {
        String p = cleanSide(partner);
        String body = content.strip();
        return p == null || body.isEmpty() ? null : new WhisperHit(true, p, body);
    }

    private static WhisperHit outgoing(String partner, String content, String ownProfile) {
        String p = cleanSide(partner);
        String body = content.strip();
        if (p == null || body.isEmpty()) {
            return null;
        }
        // The CMI "sent" shape is only ever produced for our own sends; the
        // own-side check does not apply because the partner is the other player.
        return new WhisperHit(false, p, body);
    }

    private static boolean isOwnSide(String side, String ownProfile) {
        if (side.equalsIgnoreCase("me") || side.equals("我") || side.equals("自己")) {
            return true;
        }
        if (side.equalsIgnoreCase("you") && ownProfile == null) {
            return true;
        }
        return ownProfile != null && side.equalsIgnoreCase(ownProfile);
    }

    // ---- keyword-gated family (e33chat WhisperSignal port, 0.1.11 backfill) ----

    /**
     * The e33chat keyword data, battle-tested on real servers: Chinese tokens
     * match by plain contains() ("whisper" also covers "whispers"); short
     * English words need word boundaries ("Msg: hi" is a name, "PM to X: hi"
     * is a whisper). A bare "to you" is deliberately absent — "wants to
     * teleport to you" (tpa requests) false-positives.
     */
    private static final String[] ZH_KEYWORDS = {
            "悄悄", "whisper", "对你说", "私聊", "密语", "密聊", "私信", "密谈"
    };
    private static final Pattern EN_KEYWORD = Pattern.compile("\\b(?:pm|message|msg|tell)\\b");

    static boolean hasWhisperKeywordBeforeColon(String text) {
        if (text == null) {
            return false;
        }
        int colon = -1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ':' || ch == '：') {
                colon = i;
                break;
            }
        }
        if (colon < 0) {
            // No colon: only an arrow/pipe separator can still make this a
            // whisper line, and only if one actually exists. Treating the whole
            // line as the "zone" (the old behaviour) claimed colon-less prose
            // that merely contained a keyword, and then handed the keyword back
            // as part of the "message body" — the reason a command reply could
            // arrive in the private panel with the whisper word glued to its
            // text. A line with no separator at all is not a whisper.
            return hasArrowSeparator(text) && zoneHasKeyword(text);
        }
        return zoneHasKeyword(text.substring(0, colon));
    }

    /** Whether {@link #extractWhisperContent} would find a separator to split on. */
    private static boolean hasArrowSeparator(String text) {
        for (String sep : new String[]{" -> ", " >> ", " » ", " | "}) {
            if (text.contains(sep)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a keyword appears in {@code zone} (already cut at the separator). */
    private static boolean zoneHasKeyword(String zone) {
        String lower = zone.toLowerCase(Locale.ROOT);
        for (String keyword : ZH_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        // Short English words collide with player names and prefixes — strip
        // bracket decorations, then require the word plus at least one other
        // token in the zone ("[PM]Steve" is a prefix, "Steve PM you" is not).
        String zoneNoBrackets = zone.replaceAll("\\[[^\\]]*\\]|\\([^\\)]*\\)", "");
        if (!EN_KEYWORD.matcher(zoneNoBrackets.toLowerCase(Locale.ROOT)).find()) {
            return false;
        }
        String rest = EN_KEYWORD.matcher(zoneNoBrackets.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        return !rest.isEmpty();
    }

    /**
     * First separator after the sender name wins — lastIndexOf truncated
     * content that itself contains ": "; colon-family first so
     * "Steve -&gt; you: hi" still extracts at the colon.
     */
    static String extractWhisperContent(String fullText, String senderName) {
        if (senderName == null || senderName.isEmpty()) {
            return fullText;
        }
        int idx = fullText == null ? -1 : fullText.indexOf(senderName);
        if (idx < 0) {
            return fullText;
        }
        String after = fullText.substring(idx + senderName.length());
        for (String sep : new String[]{": ", "：", " :", " ：", " -> ", " >> ", " » ", " | "}) {
            int i = after.indexOf(sep);
            if (i >= 0) {
                return after.substring(i + sep.length());
            }
        }
        return after.trim();
    }

    /**
     * e33chat's anchored strategy (WhisperDetector port): a known player name
     * (online or once-seen) near the line start plus a whisper keyword before
     * the colon claims the line, whatever the surrounding decoration. This
     * covers plugin shapes the structural families cannot express, e.g.
     * "[VIP] Steve 私聊说: hi" or "Steve 悄悄地对你说: hi".
     *
     * @param knownNames online + once-seen name candidates (longest wins)
     */
    public static WhisperHit tryParseAnchored(String line, java.util.List<String> knownNames) {
        if (line == null || line.length() > 256 || knownNames == null || knownNames.isEmpty()) {
            return null;
        }
        String clean = line.replaceAll("§.", "").strip();
        if (clean.isEmpty()) {
            return null;
        }
        List<String> sorted = new ArrayList<>(knownNames);
        sorted.sort((a, b) -> b.length() - a.length());
        for (String name : sorted) {
            if (name == null || name.isBlank()) {
                continue;
            }
            int idx = clean.indexOf(name);
            if (idx >= 0 && idx < 30 && hasWhisperKeywordBeforeColon(clean)) {
                String content = extractWhisperContent(clean, name).strip();
                if (!content.isEmpty()) {
                    return new WhisperHit(true, name, content);
                }
            }
        }
        return null;
    }

    private static String cleanSide(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.replaceAll("§.", "").trim();
        if (stripped.isEmpty() || stripped.length() > 48) {
            return null;
        }
        // A separator inside a "name" means we matched into prose, not a name.
        if (stripped.indexOf(':') >= 0 || stripped.indexOf('：') >= 0) {
            return null;
        }
        return stripped;
    }
}
