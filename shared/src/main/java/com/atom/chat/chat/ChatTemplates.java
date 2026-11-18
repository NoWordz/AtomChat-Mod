package com.atom.chat.chat;

import com.atom.chat.diagnostics.AtomChatLog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Client configuration adapter. Canonical and legacy forms share the literal matcher. */
public final class ChatTemplates {
    private ChatTemplates() {}

    private static final Logger LOG = Logger.getLogger(ChatTemplates.class.getName());
    private static final Set<String> warned = ConcurrentHashMap.newKeySet();

    public record TemplateMatch(String playerName, String displayLabel, String content) {}

    public static final List<String> PRESET_EXAMPLES = List.of(
            "<{name}> {content}",
            "<{display_name}> {content}",
            "[{prefix}] {name} » {content}",
            "{display_name} >> {content}",
            "{prefix}{name}{suffix}: {content}",
            "{content} < {name}");

    public static TemplateParser.Preview matchConfigured(String visible, List<MessageTemplate> server,
                                                          List<String> publicFormats, List<String> privateFormats,
                                                          Collection<String> knownNames) {
        var found = TemplateParser.match(server, visible);
        found = TemplateParser.combine(found, matchRules(visible, publicFormats, knownNames, MessageTemplate.Kind.PUBLIC));
        return TemplateParser.combine(found, matchRules(visible, privateFormats, knownNames, MessageTemplate.Kind.INCOMING));
    }

    static TemplateParser.Preview matchRules(String line, List<String> formats, Collection<String> knownNames,
                                             MessageTemplate.Kind kind) {
        var found = TemplateParser.result(TemplateParser.State.NO_MATCH, "");
        if (formats == null) return found;
        if (formats.size() > 64) {
            warn(kind + ": input_limit");
            return found;
        }
        var names = knownNames == null ? List.<String>of() : knownNames;
        for (int i = 0; i < formats.size(); i++) {
            var preview = TemplateParser.previewClient(formats.get(i), kind, line, names);
            if (preview.state() == TemplateParser.State.INVALID) {
                warn(kind + "[" + i + "]: " + preview.error());
                continue;
            }
            found = TemplateParser.combine(found, preview);
            if (found.state() == TemplateParser.State.AMBIGUOUS) break;
        }
        return found;
    }

    private static void warn(String reason) {
        if (warned.add(reason)) {
            LOG.warning(AtomChatLog.message("capture", () -> "[atomchat] client template rejected (" + reason + ")"));
        }
    }

    public static Optional<TemplateMatch> match(String line, List<String> templates, Collection<String> knownNames) {
        return matchLine(line, templates, knownNames, MessageTemplate.Kind.PUBLIC);
    }

    public static Optional<TemplateMatch> matchWhisper(String line, List<String> templates, Collection<String> knownNames) {
        return matchLine(line, templates, knownNames, MessageTemplate.Kind.INCOMING);
    }

    private static Optional<TemplateMatch> matchLine(String line, List<String> templates, Collection<String> knownNames,
                                                     MessageTemplate.Kind kind) {
        if (line == null) return Optional.empty();
        var preview = matchRules(TemplateParser.visibleText(line), templates, knownNames, kind);
        if (preview.state() != TemplateParser.State.MATCH) return Optional.empty();
        var fields = preview.match().fields();
        String sender = fields.get("sender");
        String label = fields.get("display");
        if (label == null) {
            label = fields.getOrDefault("prefix", "").strip() + sender + fields.getOrDefault("suffix", "").strip();
        }
        return Optional.of(new TemplateMatch(sender, label, fields.get("message")));
    }
}
