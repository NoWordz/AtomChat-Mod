package com.atom.chat.chat;

import com.atom.chat.text.RichText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Guard 1 orchestration for system/disguised channels: text-level fallback
 * that identifies a player line from the online/known name list.
 *
 * <p>Trimmed port of e33chat's ChatPipeline (MIT, same author). Plain-string
 * parsing is used to locate style-slice boundaries; rich Text slicing then
 * preserves the original run styles when available.
 */
public final class ChatPipeline {
    private ChatPipeline() {
    }

    /** Preserve the wire styles if the usual sender/body split cannot be proven. */
    public static RichText preserveRichBody(Text line, String body) {
        RichText rich = RichText.of(line);
        String text = rich.getString();
        if (body != null && !body.isEmpty()) {
            int start = text.indexOf(body);
            if (start >= 0 && start == text.lastIndexOf(body)) {
                return rich.slice(start, start + body.length()).linkifyUrls();
            }
            // Template formats can put the body before the name. An anchored
            // body is unambiguous even when its text is also inside that name.
            if (text.startsWith(body) && !text.endsWith(body)) {
                return rich.slice(0, body.length()).linkifyUrls();
            }
            if (text.endsWith(body) && !text.startsWith(body)) {
                return rich.slice(text.length() - body.length(), text.length()).linkifyUrls();
            }
        }
        return rich.linkifyUrls();
    }

    public static boolean hasUnmappedTranslation(Text message) {
        return message.getContent() instanceof net.minecraft.text.TranslatableTextContent
                && ChatClassifier.classifyByKey(message) == ChatClassifier.Route.UNKNOWN;
    }

    /** A name-only renderer slot (Paper RAW) can contain the entire visible line. */
    public static SenderMeta nativePublicLine(Text visible, Text sender, Text body, UUID uuid, String account) {
        if (sender == null || !visible.getString().equals(sender.getString())) {
            Text label = sender;
            String full = visible.getString(), name = sender == null ? "" : sender.getString(), content = body.getString();
            int nameAt = name.isEmpty() ? -1 : full.indexOf(name);
            int bodyAt = content.isEmpty() ? -1 : full.indexOf(content, Math.max(0, nameAt + name.length()));
            int otherName = nameAt < 0 ? -1 : full.indexOf(name, nameAt + name.length());
            if (nameAt > 0 && nameAt <= 256 && bodyAt >= nameAt + name.length()
                    && (otherName < 0 || otherName >= bodyAt)) {
                // Prefixes outside the sender argument still belong to the received envelope.
                int prefixEnd = full.charAt(nameAt - 1) == '<' ? nameAt - 1 : nameAt;
                label = ComponentIdentity.slice(visible,0,prefixEnd).copy().append(sender);
            }
            return new SenderMeta(uuid, sender == null ? account : sender.getString(), account, body.getString(), false, label, body)
                    .withEvidence(IdentityEvidence.NATIVE);
        }
        String text = visible.getString(), content = body.getString();
        int at = content.isEmpty() ? -1 : text.indexOf(content);
        if (at > 0 && text.indexOf(content, at+1) < 0) {
            var parsed = MessagePresentation.parseDecoratedPlayerLine(text, List.of(account));
            int labelEnd = parsed.isPresent() && parsed.get().contentStart() == at ? parsed.get().labelEnd() : at;
            Text label = ComponentIdentity.slice(visible,0,labelEnd);
            Text renderedBody = ComponentIdentity.slice(visible,at,text.length());
            return new SenderMeta(uuid,label.getString(),account,renderedBody.getString(),false,label,renderedBody)
                    .withEvidence(IdentityEvidence.NATIVE);
        }
        // Do not reveal a native body the renderer omitted, or pretend a complete line is a nickname.
        return new SenderMeta(uuid,account,account,text,false,Text.literal(account),visible)
                .withEvidence(IdentityEvidence.NATIVE);
    }

    /** Direct HUD producers (including Quark) retain structured sender/body arguments. */
    public static SenderMeta structuredPlayerLine(Text message) {
        if (message == null || ChatClassifier.classifyByKey(message) == ChatClassifier.Route.SYSTEM) return null;
        java.util.List<Text> candidates = new java.util.ArrayList<>();
        collectPlayerWrappers(message, candidates, 0, net.minecraft.text.Style.EMPTY);
        if (candidates.size() != 1) return null;
        Text wrapper = candidates.get(0);
        SenderMeta parsed = directPlayerLine(wrapper);
        if (parsed == null || parsed.system() || wrapper == message) return parsed;
        // Only a unique structural envelope may contribute leading decorations.
        String whole = message.getString();
        String nested = wrapper.getString();
        int offset = whole.indexOf(nested);
        if (offset < 0 || whole.indexOf(nested, offset + 1) >= 0) return null;
        Text label = ComponentIdentity.slice(message, 0, offset).copy().append(parsed.senderComponent());
        Text body = offset + nested.length() == whole.length() ? parsed.contentComponent()
                : parsed.contentComponent().copy().append(ComponentIdentity.slice(message, offset + nested.length(), whole.length()));
        return new SenderMeta(parsed.senderUuid(), label.getString(), parsed.profileName(),
                body.getString(), false, label, body).withEvidence(parsed.evidence());
    }

    private static void collectPlayerWrappers(Text node, java.util.List<Text> out, int depth, net.minecraft.text.Style parent) {
        var effective = node.getStyle().withParent(parent);
        if (depth > 32 || out.size() > 1) return;
        if (node.getContent() instanceof net.minecraft.text.TranslatableTextContent translated) {
            String key = translated.getKey();
            if (key.equals("chat.type.text") || key.equals("chat.type.team.text") || key.equals("chat.type.team.sent")) {
                out.add(effective.equals(node.getStyle()) ? node : node.copy().setStyle(effective));
                return; // A translated chat body may quote any other component.
            }
            return; // Unknown argument positions are not a chat schema.
        }
        for (Text child : node.getSiblings()) collectPlayerWrappers(child, out, depth + 1, effective);
    }

    private static SenderMeta directPlayerLine(Text message) {
        if (!(message.getContent() instanceof net.minecraft.text.TranslatableTextContent translated)) return null;
        String key = translated.getKey();
        int sender = key.equals("chat.type.text") ? 0
                : key.equals("chat.type.team.text") || key.equals("chat.type.team.sent") ? 1 : -1;
        Object[] args = translated.getArgs();
        if (sender < 0 || args.length <= sender + 1) return null;
        Text name = args[sender] instanceof Text c ? c : Text.literal(String.valueOf(args[sender]));
        Text body = args[sender + 1] instanceof Text c ? c : Text.literal(String.valueOf(args[sender + 1]));
        var identity = ComponentIdentity.resolveSender(name);
        if (identity.conflict()) return SenderMeta.systemMeta().withEvidence(IdentityEvidence.CONFLICT);
        if (!message.getStyle().equals(net.minecraft.text.Style.EMPTY)) {
            name = name.copy().setStyle(name.getStyle().withParent(message.getStyle()));
            body = body.copy().setStyle(body.getStyle().withParent(message.getStyle()));
        }
        if (!message.getSiblings().isEmpty()) {
            var extended = Text.literal("").setStyle(message.getStyle()).append(body);
            for (Text sibling : message.getSiblings()) extended.append(sibling);
            body = extended;
        }
        String display = name.getString().replaceAll("§.", "").trim();
        if (display.isBlank()) return null;
        UUID uuid = identity.evidence() == IdentityEvidence.ENTITY ? identity.uuid() : null;
        String profile = identity.account() != null ? identity.account() : ChatClassifier.profileName(uuid);
        return new SenderMeta(uuid, display, profile, body.getString(), false, name, body)
                .withEvidence(identity.evidence() == IdentityEvidence.ENTITY ? IdentityEvidence.ENTITY : IdentityEvidence.TRANSLATION);
    }

    public static SenderMeta learnedPlayerLine(Text message) {
        var match = LearnedChatFormats.match(message.getString());
        if (match.isEmpty()) return null;
        var parts = match.get();
        UUID uuid = SeenPlayers.findUuid(parts.account());
        if (uuid == null) return null;
        return new SenderMeta(uuid, parts.display(), parts.account(), parts.content(), false, parts.whisper(),
                parts.whisper() ? parts.account() : null, ComponentIdentity.slice(message, 0, parts.labelEnd()),
                ComponentIdentity.slice(message, parts.bodyStart(), parts.bodyEnd())).withEvidence(IdentityEvidence.LEARNED);
    }

    /** All online names (profile + tab display + §-stripped variants), then
     *  players once seen in chat — the offline-player tail that lets relayed
     *  lines (bot bridges, delayed echoes) still parse as player chat. */
    public static List<String> onlineNameCandidates() {
        var client = MinecraftClient.getInstance();
        var connection = client == null ? null : client.getNetworkHandler();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (connection != null) {
            for (var info : connection.getPlayerList()) {
                for (String candidate : ChatClassifier.nameCandidates(info)) names.add(candidate);
            }
        }
        names.addAll(SeenPlayers.profileNames());
        for (var seen : SeenPlayers.snapshot()) if (seen.displayName() != null) names.add(seen.displayName());
        return new ArrayList<>(names);
    }

    /**
     * Tries to parse a decorated system-channel line as a player message.
     * Returns null when the line is not a player line.
     */
    public static SenderMeta tryParsePlayerLine(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<String> names = onlineNameCandidates();
        if (names.isEmpty()) {
            return null;
        }
        var parsed = MessagePresentation.parseDecoratedPlayerLine(text, names);
        if (parsed.isEmpty()) {
            return null;
        }
        var pl = parsed.orElseThrow();
        // Whitespace-only gap = broadcast sentence ("Steve joined the game").
        if (MessagePresentation.isWhitespaceOnlyGap(text, pl.nameEnd(), pl.contentStart())) {
            return null;
        }

        PlayerListEntry info = ChatClassifier.resolveOnlinePlayer(pl.playerName());
        String profile = info != null ? info.getProfile().getName() : pl.playerName();
        UUID uuid = info != null ? info.getProfile().getId() : ChatClassifier.resolveUuid(pl.playerName());
        return new SenderMeta(uuid, pl.displayLabel(), profile, pl.content(), false);
    }

    /**
     * Best-effort extraction of the decorated display label ("[VIP]Steve")
     * from the final HUD line when the channel layer only supplied the raw
     * profile name. Returns null when the line does not contain the sender.
     */
    public static String decoratedDisplayName(String fullText, SenderMeta meta) {
        if (meta == null || fullText == null) {
            return null;
        }
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        if (meta.senderName() != null) {
            candidates.add(meta.senderName());
        }
        if (meta.profileName() != null) {
            candidates.add(meta.profileName());
        }
        if (candidates.isEmpty()) {
            return null;
        }
        var parsed = MessagePresentation.parseDecoratedPlayerLine(fullText, candidates);
        if (parsed.isPresent()) {
            String label = parsed.get().displayLabel();
            if (label != null && !label.isBlank()) {
                return label;
            }
        }
        return null;
    }

    /**
     * Best-effort content extraction from the final decorated line when the
     * channel layer only supplied identity. Falls back to the full line.
     */
    public static String extractContent(String fullText, SenderMeta meta) {
        if (meta == null) {
            return fullText;
        }
        if (meta.contentText() != null) {
            return meta.contentText();
        }
        if (fullText == null) {
            return null;
        }
        String needle = meta.senderName() != null ? meta.senderName() : meta.profileName();
        if (needle == null) {
            return fullText;
        }
        int idx = fullText.indexOf(needle);
        if (idx < 0 && meta.profileName() != null) {
            idx = fullText.indexOf(meta.profileName());
            needle = meta.profileName();
        }
        if (idx < 0) {
            return fullText;
        }
        int sep = MessagePresentation.skipSeparators(fullText, idx + needle.length());
        if (sep <= idx + needle.length() || sep >= fullText.length()) {
            return fullText;
        }
        return fullText.substring(sep).trim();
    }

    /**
     * Slices the reply body out of a quote-prefixed rich content line.
     *
     * <p>The wire carries quotes as the plain-text prefix
     * {@code 「引用 @Name: quoted」body}; rebuilding {@code body} with
     * {@link RichText#literal} kept the visible text but dropped every run
     * style inside the reply (server rich content, coloured mentions). The rich
     * line already carries the same text, so slice after the closing bracket
     * instead. Falls back to a literal when the rich line does not contain the
     * parsed body verbatim (translated prefixes, whitespace drift, plain-only
     * sources).
     */
    public static RichText quoteBodyRich(RichText content, String body) {
        String plainBody = body == null ? "" : body;
        if (content == null || content.isEmpty()) {
            return RichText.literal(plainBody).linkifyUrls();
        }
        String full = content.getString();
        int open = full.indexOf("「引用");
        int close = open < 0 ? -1 : full.indexOf('」', open + 2);
        if (close < 0) {
            return RichText.literal(plainBody).linkifyUrls();
        }
        int start = close + 1;
        while (start < full.length() && Character.isWhitespace(full.charAt(start))) {
            start++;
        }
        int end = full.length();
        while (end > start && Character.isWhitespace(full.charAt(end - 1))) {
            end--;
        }
        if (!full.substring(start, end).equals(plainBody)) {
            return RichText.literal(plainBody).linkifyUrls();
        }
        return content.slice(start, end).linkifyUrls();
    }

    /** The plain-text quote prefix AtomChat/e33chat understands (see QuoteParser). */
    private static final String QUOTE_OPEN = "「引用";

    /**
     * Sliced rich parts of a quote capsule: the quoted player's name and the
     * quoted text, both taken from the original line.
     */
    public record QuoteRichParts(RichText name, RichText text) {
    }

    /**
     * Slices the quote capsule's {@code name}/{@code text} out of the original
     * rich line by stable plain-text offsets, keeping every run's explicit
     * colour. The draw layer must not re-guess this: it only lays the pieces out
     * around the {@code @} and {@code ": "} separators, truncates to the width
     * budget and hands them to {@link com.atom.chat.render.RichTextRenderer},
     * whose colourless runs fall back to the capsule colour.
     *
     * <p>Returns {@code null} — the caller keeps the plain string pill and the
     * capsule colour — when there is no original rich line, when the prefix is
     * absent, when the parsed name/text no longer sits verbatim in the line
     * (translated prefixes, whitespace drift, plain-only sources), or when the
     * wire put a hard newline inside the one-line capsule.
     *
     * @param source     the original rich line (the captured Text)
     * @param quoteName  the name {@link QuoteParser} parsed (without its {@code @})
     * @param quoteText  the quoted text {@link QuoteParser} parsed
     */
    public static QuoteRichParts quotePartsRich(RichText source, String quoteName, String quoteText) {
        if (source == null || source.isEmpty() || quoteName == null) {
            return null;
        }
        String plain = source.getString();
        int open = plain.indexOf(QUOTE_OPEN);
        int close = open < 0 ? -1 : plain.indexOf('」', open + QUOTE_OPEN.length());
        if (close < 0) {
            return null;
        }
        int at = plain.indexOf('@', open + QUOTE_OPEN.length());
        if (at < 0 || at >= close) {
            return null;
        }
        int colon = plain.indexOf(':', at);
        if (colon < 0 || colon >= close) {
            return null;
        }
        int[] nameRange = trimmedRange(plain, at + 1, colon);
        int[] textRange = trimmedRange(plain, colon + 1, close);
        if (nameRange == null || textRange == null) {
            return null;
        }
        String name = plain.substring(nameRange[0], nameRange[1]);
        String text = plain.substring(textRange[0], textRange[1]);
        if (!name.equals(sectionStripped(quoteName)) || !text.equals(sectionStripped(quoteText))) {
            return null;
        }
        if (hasHardNewline(name) || hasHardNewline(text)) {
            return null;
        }
        return new QuoteRichParts(source.slice(nameRange[0], nameRange[1]),
                source.slice(textRange[0], textRange[1]));
    }

    /** Trimmed {@code [start, end)} range, or null when it holds only whitespace. */
    private static int[] trimmedRange(String text, int start, int end) {
        int from = Math.max(0, Math.min(start, text.length()));
        int to = Math.max(from, Math.min(end, text.length()));
        while (from < to && Character.isWhitespace(text.charAt(from))) {
            from++;
        }
        while (to > from && Character.isWhitespace(text.charAt(to - 1))) {
            to--;
        }
        return from < to ? new int[]{from, to} : null;
    }

    /**
     * The line's plain text has its {@code §} codes turned into run styles, so a
     * quote field that still carries legacy codes is compared with them removed
     * (offsets in the plain text ignore them).
     */
    private static String sectionStripped(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    private static boolean hasHardNewline(String s) {
        return s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
    }

    /**
     * Slices the final decorated line into styled sender and content parts.
     * Only returns a result when the line parses as a player line for the
     * sender/profile names carried by {@code meta}; otherwise returns empty so
     * callers keep their system-safe fallback.
     */
    public static Optional<RichChatParts> sliceRichText(Text fullLine, SenderMeta meta) {
        if (fullLine == null || meta == null) {
            return Optional.empty();
        }
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (meta.senderName() != null) {
            candidates.add(meta.senderName());
        }
        if (meta.profileName() != null) {
            candidates.add(meta.profileName());
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        RichText full = RichText.of(fullLine);
        String text = full.getString(); // visible text, no § control pairs
        var parsed = MessagePresentation.parseDecoratedPlayerLine(text, candidates);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        var pl = parsed.orElseThrow();
        // Whitespace-only gap = broadcast sentence ("Steve joined the game").
        if (MessagePresentation.isWhitespaceOnlyGap(text, pl.nameEnd(), pl.contentStart())) {
            return Optional.empty();
        }

        RichText sender;
        if (pl.nameStart() > 0 && text.charAt(pl.nameStart() - 1) == '<') {
            // Vanilla angle decoration is not part of the displayed sender:
            // "<Steve> hi" and "[VIP]<Steve> hi" must render as "Steve" and
            // "[VIP]Steve". Slice around the opening '<' (prefix decoration +
            // bare name) instead of synthesising a literal — the literal branch
            // dropped the line's run colours, rendering every sender in the
            // plain text colour (0.2.5 hunt).
            sender = RichText.concat(full.slice(0, pl.nameStart() - 1),
                    full.slice(pl.nameStart(), pl.labelEnd()));
        } else if (text.charAt(0) == '<' && pl.nameEnd() < text.length()
                && text.charAt(pl.nameEnd()) == '>') {
            // "<[Team]Steve> hi": slice out the wrapping brackets but keep the
            // inner runs' styles so team colours survive — e33chat's
            // cleanNameArea sliceStyled(1, len-1) parity (0.2.5 hunt).
            sender = full.slice(1, pl.labelEnd());
        } else {
            sender = full.slice(0, pl.labelEnd());
        }
        RichText content = full.slice(pl.contentStart(), text.length()).linkifyUrls();
        return Optional.of(new RichChatParts(sender, content));
    }
}
