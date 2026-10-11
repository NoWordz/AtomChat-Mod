package com.atom.chat.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * e33chat-parity client-side chat templates: a structural way to claim
 * system-channel lines whose server format the guards cannot express
 * ("[Guest] Steve7 » hello", suffix-style "hello < Steve", ...).
 *
 * <p>Template syntax (hand-edited in {@code atomchat-client.json},
 * {@code chatTemplates} / {@code whisperTemplates}):
 * <ul>
 *   <li>{@code {name}} — profile name; compiled to an alternation of the
 *       known online/seen names, so the regex itself only splits at a real
 *       player name (a lazy any-match would mis-split "Steve: hi" as
 *       name="S")</li>
 *   <li>{@code {display_name}} — decorated display name; matched loosely and
 *       post-validated to contain a known name (longest contained match)</li>
 *   <li>{@code {prefix}} / {@code {suffix}} — decoration around the name</li>
 *   <li>{@code {sep}} — optional separator run (0-4 separator characters)</li>
 *   <li>{@code {content}} — message body; exactly one, any position (suffix
 *       style is supported, an e33chat limitation that is not repeated here)</li>
 * </ul>
 *
 * <p>Hardening carried over from the e33chat 2.2.7 audit: {@link Pattern}
 * compilation is guarded ({@code {name}{name}} would emit a duplicate named
 * group and previously crashed every entry path), literal § pairs are
 * stripped before matching (the guard path strips, the old template path did
 * not), and every hit is anchored to a known player so arbitrary prose is
 * never claimed.
 */
public final class ChatTemplates {
    private ChatTemplates() {
    }

    private static final Logger LOG = Logger.getLogger(ChatTemplates.class.getName());

    /** A successful template match, identity already resolved. */
    public record TemplateMatch(String playerName, String displayLabel, String content) {
    }

    private record Compiled(Pattern pattern, boolean hasName, boolean hasDisplayName,
                            boolean hasPrefix, boolean hasSuffix) {
    }

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{(name|display_name|prefix|suffix|sep|content)}");

    private static volatile String cacheKey = "";
    private static volatile List<Compiled> cache = List.of();

    /** Preset examples for the config file comments — real plugin defaults. */
    public static final List<String> PRESET_EXAMPLES = List.of(
            "<{name}> {content}",
            "<{display_name}> {content}",
            "[{prefix}] {name} » {content}",
            "{display_name} >> {content}",
            "{prefix}{name}{suffix}: {content}",
            "{content} < {name}");

    private static List<Compiled> compiled(List<String> templates, List<String> knownNames) {
        String key = String.join("\u0000", templates) + "\u0001" + String.join("\u0000", knownNames);
        if (cacheKey.equals(key)) {
            return cache;
        }
        List<Compiled> out = new ArrayList<>();
        for (String template : templates) {
            Compiled c = compile(template, knownNames);
            if (c != null) {
                out.add(c);
            }
        }
        cache = List.copyOf(out);
        cacheKey = key;
        return cache;
    }

    private static Compiled compile(String template, List<String> knownNames) {
        if (template == null || template.isBlank()) {
            return null;
        }
        Matcher ph = PLACEHOLDER.matcher(template);
        StringBuilder rx = new StringBuilder();
        boolean hasContent = false;
        boolean hasName = false;
        boolean hasDisplayName = false;
        boolean hasPrefix = false;
        boolean hasSuffix = false;
        int last = 0;
        while (ph.find()) {
            rx.append(Pattern.quote(template.substring(last, ph.start())));
            switch (ph.group(1)) {
                case "name" -> {
                    if (hasName) {
                        LOG.warning(com.atom.chat.diagnostics.AtomChatLog.message("capture", () -> "[atomchat] template rejected (duplicate {name}): " + template));
                        return null;
                    }
                    if (knownNames.isEmpty()) {
                        return null; // {name} can only anchor to a known player
                    }
                    // ⚠️ JDK 21.0.11 (2026-04 LTS) rejects '_' and '-' inside
                    // named capturing groups ("missing trailing '>'"), so the
                    // group names below are deliberately underscore-free.
                    rx.append("(?<gname>");
                    knownNames.stream()
                            .filter(n -> n != null && !n.isBlank())
                            .sorted(Comparator.comparingInt(String::length).reversed())
                            .forEach(n -> rx.append(Pattern.quote(n)).append('|'));
                    rx.setCharAt(rx.length() - 1, ')');
                    hasName = true;
                }
                case "display_name" -> {
                    if (hasDisplayName) {
                        LOG.warning(com.atom.chat.diagnostics.AtomChatLog.message("capture", () -> "[atomchat] template rejected (duplicate {display_name}): " + template));
                        return null;
                    }
                    rx.append("(?<gdisp>.{1,64}?)");
                    hasDisplayName = true;
                }
                case "prefix" -> {
                    rx.append("(?<gprefix>.{0,32}?)");
                    hasPrefix = true;
                }
                case "suffix" -> {
                    rx.append("(?<gsuffix>.{0,32}?)");
                    hasSuffix = true;
                }
                case "sep" -> rx.append("[\\s:：,，>»|~\\-–—·•]{0,4}");
                case "content" -> {
                    if (hasContent) {
                        LOG.warning(com.atom.chat.diagnostics.AtomChatLog.message("capture", () -> "[atomchat] template rejected (duplicate {content}): " + template));
                        return null;
                    }
                    rx.append("(?<gcontent>.+)");
                    hasContent = true;
                }
                default -> {
                    // unreachable
                }
            }
            last = ph.end();
        }
        rx.append(Pattern.quote(template.substring(last)));
        if (!hasContent || !(hasName || hasDisplayName)) {
            LOG.warning(com.atom.chat.diagnostics.AtomChatLog.message("capture", () -> "[atomchat] template rejected (needs one {content} and a name placeholder): " + template));
            return null;
        }
        try {
            return new Compiled(Pattern.compile("\\A" + rx + "\\z"),
                    hasName, hasDisplayName, hasPrefix, hasSuffix);
        } catch (PatternSyntaxException e) {
            // e33chat 2.2.7: an unguarded compile crashed every entry path.
            LOG.warning(com.atom.chat.diagnostics.AtomChatLog.message("capture", "[atomchat] template rejected (regex compile failed): " + template));
            return null;
        }
    }

    /**
     * Matches a raw line against the templates. Literal § pairs are stripped
     * from the line first (guard-path parity). The extracted display name
     * must resolve against {@code knownNames} (longest contained ≥3 chars,
     * guard-style) — otherwise the match is rejected.
     */
    public static Optional<TemplateMatch> match(
            String line, List<String> templates, Collection<String> knownNames) {
        if (line == null || line.length() > 256 || templates == null || templates.isEmpty()) {
            return Optional.empty();
        }
        List<String> names = knownNames == null ? List.of()
                : knownNames.stream().filter(n -> n != null && !n.isBlank()).toList();
        if (names.isEmpty()) {
            return Optional.empty();
        }
        String text = stripCodes(line.strip());
        for (Compiled c : compiled(templates, names)) {
            Matcher m = c.pattern().matcher(text);
            if (!m.matches()) {
                continue;
            }
            String content = m.group("gcontent");
            if (content == null || content.isBlank()) {
                continue;
            }
            String name = c.hasName() ? clean(m.group("gname")) : null;
            String display = c.hasDisplayName() ? clean(m.group("gdisp")) : null;
            String resolved = name != null ? name : resolveContained(display, names);
            if (resolved == null) {
                continue;
            }
            String label = display != null ? display : buildLabel(m, c, name, resolved);
            if (label == null || label.isBlank()) {
                label = resolved;
            }
            return Optional.of(new TemplateMatch(resolved, label, content.strip()));
        }
        return Optional.empty();
    }

    /** e33chat parity: whisper templates identify an incoming private line. */
    public static Optional<TemplateMatch> matchWhisper(
            String line, List<String> templates, Collection<String> knownNames) {
        return match(line, templates, knownNames);
    }

    /** Longest known name contained in the decorated display text, or null. */
    private static String resolveContained(String display, List<String> names) {
        if (display == null) {
            return null;
        }
        String best = null;
        int bestLen = 0;
        for (String known : names) {
            if (known.length() < 3 || known.length() <= bestLen) {
                continue;
            }
            if (display.contains(known)) {
                best = known;
                bestLen = known.length();
            }
        }
        return best;
    }

    private static String buildLabel(Matcher m, Compiled c, String rawName, String resolvedName) {
        String prefix = c.hasPrefix() ? m.group("gprefix") : null;
        String suffix = c.hasSuffix() ? m.group("gsuffix") : null;
        String name = rawName != null ? rawName : resolvedName;
        StringBuilder sb = new StringBuilder();
        if (prefix != null) {
            sb.append(prefix.strip());
        }
        sb.append(name);
        if (suffix != null) {
            sb.append(suffix.strip());
        }
        return sb.toString();
    }

    private static String stripCodes(String s) {
        return s == null ? null : s.replaceAll("§.", "");
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.replaceAll("§.", "").trim();
        return stripped.isEmpty() ? null : stripped;
    }
}
