package com.atom.chat.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Literal template parsing shared by the editor, server rules and local configuration. */
public final class TemplateParser {
    private TemplateParser() {}

    public enum State { MATCH, NO_MATCH, AMBIGUOUS, INVALID }
    public record Span(int start, int end) {}
    public record Match(MessageTemplate.Kind kind, Map<String, String> fields, Map<String, Span> spans) {}
    public record Preview(State state, Match match, String error) {}
    private record Part(String literal, String field) {}
    private record Rule(List<Part> parts, boolean legacy, String error) {}

    private static final Set<String> SERVER_FIELDS = Set.of("sender", "display", "uuid", "target", "message", "channel");
    private static final Map<String, String> LEGACY_FIELDS = Map.of(
            "name", "sender", "display_name", "display", "content", "message",
            "prefix", "prefix", "suffix", "suffix", "sep", "sep");
    private static final Pattern ACCOUNT = Pattern.compile("[A-Za-z0-9_.]{1,32}");
    private static final Pattern SEPARATOR = Pattern.compile("[\\s:：,，>»|~\\-–—·•]");
    private static final Pattern LEGACY_COLOR = Pattern.compile("§[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private static final UUID NIL = new UUID(0, 0);
    private static final int MAX_TEXT = 8192;

    private static Rule compile(String format, MessageTemplate.Kind kind, boolean client) {
        if (format == null || format.isBlank() || format.length() > 512
                || !com.atom.chat.util.TextLimits.wellFormed(format) || kind == null) {
            return invalid("invalid_format");
        }
        List<Part> parts = new ArrayList<>();
        Set<String> fields = new HashSet<>();
        int at = 0;
        boolean legacy = false;
        boolean canonical = false;
        while (at < format.length()) {
            int open = format.indexOf('{', at);
            String literal = open < 0 ? format.substring(at) : format.substring(at, open);
            if (literal.contains("}")) return invalid("invalid_placeholder");
            if (!literal.isEmpty()) parts.add(new Part(literal, null));
            if (open < 0) break;
            int close = format.indexOf('}', open);
            if (close < 0) return invalid("invalid_placeholder");
            String field = format.substring(open + 1, close);
            if (SERVER_FIELDS.contains(field)) {
                canonical = true;
            } else if (client && LEGACY_FIELDS.containsKey(field)) {
                legacy = true;
                field = LEGACY_FIELDS.get(field);
            } else {
                return invalid("invalid_placeholder");
            }
            if (!fields.add(field)) return invalid("duplicate_field");
            parts.add(new Part(null, field));
            at = close + 1;
        }
        if (legacy && canonical) return invalid("invalid_placeholder");
        if (!fields.contains("message")) return invalid("missing_message");
        if (kind == MessageTemplate.Kind.OUTGOING && !fields.contains("target")) return invalid("missing_target");
        if ((kind != MessageTemplate.Kind.OUTGOING && !fields.contains("sender") && !fields.contains("display"))
                || (fields.contains("uuid") && !fields.contains("sender"))) return invalid("missing_sender");
        return new Rule(List.copyOf(parts), legacy, "");
    }

    private static Rule invalid(String error) {
        return new Rule(List.of(), false, error);
    }

    public static String validate(MessageTemplate template) {
        return compile(template.format(), template.kind(), false).error().isEmpty() ? null : "bad_template";
    }

    public static Preview preview(MessageTemplate template, String text) {
        if (template == null) return result(State.INVALID, "invalid_format");
        Rule rule = compile(template.format(), template.kind(), false);
        if (!rule.error().isEmpty()) return result(State.INVALID, rule.error());
        if (template.validate() != null) return result(State.INVALID, "invalid_format");
        return preview(rule, template.kind(), text, List.of(), Set.of());
    }

    public static String visibleText(String text) {
        return text == null || text.indexOf('§') < 0 ? text : LEGACY_COLOR.matcher(text).replaceAll("");
    }

    static Preview previewClient(String format, MessageTemplate.Kind kind, String text, Collection<String> names) {
        return previewClient(format, kind, text, names, Set.of());
    }

    static Preview previewClient(String format, MessageTemplate.Kind kind, String text, Collection<String> names,
                                 Set<Span> standaloneSegments) {
        return preview(compile(format, kind, true), kind, text, names, standaloneSegments);
    }

    private static Preview preview(Rule rule, MessageTemplate.Kind kind, String text, Collection<String> names,
                                   Set<Span> standaloneSegments) {
        if (!rule.error().isEmpty()) return result(State.INVALID, rule.error());
        if (text == null || text.length() > MAX_TEXT) return result(State.NO_MATCH, "input_limit");
        if (rule.legacy() && names.isEmpty()) return result(State.NO_MATCH, "");
        var matches = new ArrayList<Match>(2);
        int[] budget = {20_000};
        walk(rule, kind, names, text, 0, 0, new LinkedHashMap<>(), new LinkedHashMap<>(), matches, budget, null, Set.of());
        if (budget[0] <= 0) return result(State.AMBIGUOUS, "input_limit");
        if (matches.size() > 1) {
            String delimiter = standaloneSegments.isEmpty() ? null : displayBodyDelimiter(rule, kind);
            if (delimiter != null) {
                matches.clear();
                walk(rule, kind, names, text, 0, 0, new LinkedHashMap<>(), new LinkedHashMap<>(), matches, budget,
                        delimiter, standaloneSegments);
                if (budget[0] > 0 && matches.size() == 1) return new Preview(State.MATCH, matches.get(0), "");
            }
            return result(State.AMBIGUOUS, "split_conflict");
        }
        return matches.isEmpty() ? result(State.NO_MATCH, "") : new Preview(State.MATCH, matches.get(0), "");
    }

    private static String displayBodyDelimiter(Rule rule, MessageTemplate.Kind kind) {
        if (kind != MessageTemplate.Kind.PUBLIC || rule.legacy() || rule.parts().size() < 3) return null;
        if (rule.parts().stream().noneMatch(part -> "display".equals(part.field()))
                || rule.parts().stream().anyMatch(part -> "sender".equals(part.field()) || "target".equals(part.field())))
            return null;
        var parts = rule.parts();
        if (!"message".equals(parts.get(parts.size() - 1).field())) return null;
        String delimiter = parts.get(parts.size() - 2).literal();
        return delimiter == null || delimiter.isBlank() ? null : delimiter;
    }

    private static void walk(Rule rule, MessageTemplate.Kind kind, Collection<String> names, String text,
                             int partIndex, int at, Map<String, String> fields, Map<String, Span> spans,
                             List<Match> matches, int[] budget, String delimiter, Set<Span> standaloneSegments) {
        if (partIndex == rule.parts().size()) {
            if (at != text.length()) return;
            var captured = new LinkedHashMap<>(fields);
            if (rule.legacy() && !captured.containsKey("sender")) {
                String name = legacyAccount(captured.get("display"), names);
                if (name == null) return;
                captured.put("sender", name);
            }
            matches.add(new Match(kind, Map.copyOf(captured), Map.copyOf(spans)));
            return;
        }
        Part part = rule.parts().get(partIndex);
        if (part.literal() != null) {
            if (text.startsWith(part.literal(), at)) {
                walk(rule, kind, names, text, partIndex + 1, at + part.literal().length(), fields, spans, matches, budget, delimiter, standaloneSegments);
            }
            return;
        }

        String field = part.field();
        if (delimiter != null && field.equals("message")
                && !standaloneSegments.contains(new Span(at - delimiter.length(), at))) return;
        String searchLiteral = partIndex + 1 < rule.parts().size() ? rule.parts().get(partIndex + 1).literal() : null;
        int max = switch (field) {
            case "message" -> MAX_TEXT;
            case "uuid" -> 36;
            case "sender", "target", "prefix", "suffix" -> 32;
            case "sep" -> 4;
            default -> 64;
        };
        int start = at + (field.equals("prefix") || field.equals("suffix") || field.equals("sep") ? 0 : 1);
        int end = Math.min(text.length(), at + max);
        if (field.equals("sep")) {
            int to = at;
            var separators = SEPARATOR.matcher(text);
            while (to < end && separators.region(to, to + 1).matches()) to++;
            end = to;
        }
        if (partIndex + 1 == rule.parts().size()) start = Math.max(start, text.length());
        for (int to = start; to <= end && matches.size() < 2; to++) {
            if (--budget[0] <= 0) break;
            if (to < text.length() && Character.isLowSurrogate(text.charAt(to))) continue;
            if (searchLiteral != null && !text.startsWith(searchLiteral, to)) continue;
            String value = text.substring(at, to);
            if (!valid(field, value) || rule.legacy() && field.equals("sender") && !names.contains(value)) continue;
            fields.put(field, value);
            spans.put(field, new Span(at, to));
            walk(rule, kind, names, text, partIndex + 1, to, fields, spans, matches, budget, delimiter, standaloneSegments);
            spans.remove(field);
            fields.remove(field);
        }
    }

    private static String legacyAccount(String display, Collection<String> names) {
        if (display == null) return null;
        String longest = null;
        boolean tie = false;
        for (String name : names) {
            if (name == null || name.length() < 3 || !display.contains(name)) continue;
            if (longest == null || name.length() > longest.length()) {
                longest = name;
                tie = false;
            } else if (name.length() == longest.length() && !name.equals(longest)) {
                tie = true;
            }
        }
        return tie ? null : longest;
    }

    private static boolean valid(String field, String value) {
        return switch (field) {
            case "prefix", "suffix", "sep" -> true;
            case "sender", "target" -> ACCOUNT.matcher(value).matches();
            case "uuid" -> validUuid(value);
            default -> !value.isBlank();
        };
    }

    private static boolean validUuid(String value) {
        if (value.length() != 36) return false;
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.toString().equalsIgnoreCase(value) && !NIL.equals(uuid);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public static Preview match(List<MessageTemplate> templates, String text) {
        return match(templates, text, Set.of());
    }

    static Preview match(List<MessageTemplate> templates, String text, Set<Span> standaloneSegments) {
        Preview found = result(State.NO_MATCH, "");
        for (var template : templates) {
            if (!template.enabled()) continue;
            var parsed = preview(template, text);
            if ("split_conflict".equals(parsed.error()) && !standaloneSegments.isEmpty()) {
                parsed = preview(compile(template.format(), template.kind(), false), template.kind(), text,
                        List.of(), standaloneSegments);
            }
            found = combine(found, parsed);
            if (found.state() == State.AMBIGUOUS) break;
        }
        return found;
    }

    /** A second rule may add fields, provided direction and all overlapping captures agree. */
    static Preview combine(Preview left, Preview right) {
        if (left.state() == State.AMBIGUOUS) return left;
        if (right.state() == State.AMBIGUOUS) return right;
        if (right.state() != State.MATCH) return left.state() == State.NO_MATCH ? right : left;
        if (left.state() != State.MATCH) return right;
        Match a = left.match();
        Match b = right.match();
        if (a.kind() != b.kind()) return result(State.AMBIGUOUS, "rule_conflict");
        Map<String, String> fields = new LinkedHashMap<>(a.fields());
        Map<String, Span> spans = new LinkedHashMap<>(a.spans());
        for (var field : b.fields().entrySet()) {
            String previous = fields.putIfAbsent(field.getKey(), field.getValue());
            Span otherSpan = b.spans().get(field.getKey());
            Span previousSpan = otherSpan == null ? null : spans.putIfAbsent(field.getKey(), otherSpan);
            if (previous != null && !previous.equals(field.getValue())
                    || previousSpan != null && !previousSpan.equals(otherSpan)) {
                return result(State.AMBIGUOUS, "rule_conflict");
            }
        }
        return new Preview(State.MATCH, new Match(a.kind(), Map.copyOf(fields), Map.copyOf(spans)), "");
    }

    static Preview result(State state, String error) {
        return new Preview(state, null, error);
    }
}
