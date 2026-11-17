package com.atom.chat.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.world.entity.EntityType;
import java.util.*;

/** Extracts identity only from a bounded sender region, never from body buttons. */
public final class ComponentIdentity {
    private ComponentIdentity() {}
    private record Span(int start, int end, String account, UUID uuid) {}
    public record Inspection(SenderMeta meta, boolean conflict) {}
    public record Identity(UUID uuid, String account, boolean conflict, IdentityEvidence evidence) {}
    public static SenderMeta detect(Component message) { return inspect(message).meta(); }
    private static final Inspection NONE = new Inspection(null, false);


    public static Inspection inspect(Component message) {
        if (message == null || ChatClassifier.classifyByKey(message) == ChatClassifier.Route.SYSTEM) return NONE;
        String text = message.getString();
        if (text.length() > 32768) return NONE;
        List<Span> spans = new ArrayList<>();
        int[] offset = {0};
        message.visit((style, part) -> {
            int start = offset[0]; offset[0] += part.length();
            String account = null; UUID uuid = null;
            var click = style.getClickEvent();
            if (click != null && click.getAction() == ClickEvent.Action.SUGGEST_COMMAND)
                account = commandAccount(click.getValue());
            var hover = style.getHoverEvent();
            var entity = hover == null ? null : hover.getValue(HoverEvent.Action.SHOW_ENTITY);
            if (entity != null && entity.type == EntityType.PLAYER) {
                uuid = entity.id;

            }
            if ((account != null || uuid != null) && !part.isBlank()) spans.add(new Span(start, offset[0], account, uuid));
            return Optional.<Object>empty();
        }, Style.EMPTY);
        if (spans.isEmpty()) return NONE;
        Span first = spans.get(0);
        int end = first.end(); UUID id = first.uuid(); boolean entityProof = id != null; String account = first.account();
        for (int i = 1; i < spans.size(); i++) {
            Span next = spans.get(i);
            if (next.start() != end) break;
            if (account != null && next.account() != null && !next.account().equalsIgnoreCase(account)
                    || id != null && next.uuid() != null && !id.equals(next.uuid())) return new Inspection(null, true);
            if (account == null) account = next.account();
            if (id == null) id = next.uuid();
            entityProof |= next.uuid() != null;
            end = next.end();
        }
        String visible = text.substring(first.start(), end).trim();
        if (visible.isEmpty() || visible.length() > 64) return NONE;
        // The ordinary parser validates the leading decoration and non-prose gap;
        // the account comes from interaction data, not the visible nickname.
        var parsed = MessagePresentation.parseDecoratedPlayerLine(text, List.of(visible));
        if (parsed.isEmpty()) return NONE;
        var line = parsed.get();
        if (line.nameStart() != first.start() || line.nameEnd() != end
                || MessagePresentation.isWhitespaceOnlyGap(text, line.nameEnd(), line.contentStart())) return NONE;
        UUID known = ChatClassifier.resolveAccountUuid(account);
        if (account == null && id != null) account = ChatClassifier.profileName(id);
        if (id == null) id = known;
        Component label = slice(message, 0, line.labelEnd());
        Component body = slice(message, line.contentStart(), text.length());
        return new Inspection(new SenderMeta(id, label.getString(), account, body.getString(), false, label, body).withEvidence(entityProof ? IdentityEvidence.ENTITY : IdentityEvidence.CLICK), false);
    }

    public static Identity resolveSender(Component label) {
        UUID[] uuid = {null}; String[] account = {null}; boolean[] conflict = {false};
        label.visit((style, text) -> {
            var click = style.getClickEvent();
            String command = click != null && click.getAction() == ClickEvent.Action.SUGGEST_COMMAND ? commandAccount(click.getValue()) : null;
            if (command != null) {
                if (account[0] != null && !account[0].equalsIgnoreCase(command)) conflict[0] = true;
                account[0] = command;
            }
            var hover = style.getHoverEvent();
            var entity = hover == null ? null : hover.getValue(HoverEvent.Action.SHOW_ENTITY);
            if (entity != null && entity.type == EntityType.PLAYER) {
                if (uuid[0] != null && !uuid[0].equals(entity.id)) conflict[0] = true;
                uuid[0] = entity.id;
            }
            return Optional.<Object>empty();
        }, Style.EMPTY);
        if (uuid[0] != null && account[0] == null) account[0] = ChatClassifier.profileName(uuid[0]);
        if (uuid[0] == null && account[0] != null) uuid[0] = ChatClassifier.resolveAccountUuid(account[0]);
        return new Identity(uuid[0], account[0], conflict[0], uuid[0] != null ? IdentityEvidence.ENTITY : IdentityEvidence.CLICK);
    }

    private static String commandAccount(String command) {
        if (command == null) return null;
        String[] pieces = command.trim().split("\\s+");
        if (pieces.length != 2) return null; // Do not treat embedded message commands as identity.
        String verb = pieces[0].toLowerCase(Locale.ROOT);
        if (!Set.of("/tell", "/msg", "/w", "/whisper").contains(verb)) return null;
        return validAccount(pieces[1]) ? pieces[1] : null;
    }

    private static boolean validAccount(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{1,16}");
    }

    /** Visible offsets use the same styled visitor as RichText; inherited events survive slicing. */
    public static Component slice(Component source, int from, int to) {
        var result = Component.literal("");
        int[] offset = {0};
        source.visit((style, part) -> {
            int start = offset[0]; offset[0] += part.length();
            int a = Math.max(from, start), b = Math.min(to, offset[0]);
            if (a < b) result.append(Component.literal(part.substring(a - start, b - start)).setStyle(style));
            return Optional.<Object>empty();
        }, Style.EMPTY);
        return result;
    }
}
