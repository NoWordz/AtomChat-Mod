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
    public record Inspection(SenderMeta meta, boolean conflict, int senderStart, int senderEnd) {
        public Inspection(SenderMeta meta, boolean conflict) { this(meta, conflict, -1, -1); }
    }
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
        int end = first.end();
        for (int i = 1; i < spans.size(); i++) {
            Span next = spans.get(i);
            if (next.start() != end) break;
            end = next.end();
        }
        String visible = text.substring(first.start(), end).stripTrailing();
        if (visible.endsWith(":") || visible.endsWith("：")) {
            visible = visible.substring(0, visible.length() - 1).stripTrailing();
            if (visible.indexOf(':') >= 0 || visible.indexOf('：') >= 0) return NONE;
        }
        if (visible.isEmpty() || visible.length() > 64) return NONE;
        // The ordinary parser validates the leading decoration and non-prose gap;
        // the account comes from interaction data, not the visible nickname.
        var parsed = MessagePresentation.parseDecoratedPlayerLine(text, List.of(visible));
        if (parsed.isEmpty()) return NONE;
        var line = parsed.get();
        if (line.nameStart() != first.start() || line.nameEnd() != first.start() + visible.length()
                || end > line.contentStart()
                || MessagePresentation.isWhitespaceOnlyGap(text, line.nameEnd(), line.contentStart())) return NONE;
        UUID id = null;
        String account = null;
        for (Span span : spans) {
            if (span.start() >= line.nameEnd()) break;
            if ((account != null && span.account() != null && !span.account().equalsIgnoreCase(account))
                    || (id != null && span.uuid() != null && !id.equals(span.uuid()))) {
                return new Inspection(null, true);
            }
            if (account == null) account = span.account();
            if (id == null) id = span.uuid();
        }
        Identity identity = resolve(id, account, false);
        if (identity.conflict()) return new Inspection(null, true);

        Component label = slice(message, 0, line.labelEnd());
        Component body = slice(message, line.contentStart(), text.length());
        return new Inspection(new SenderMeta(identity.uuid(), label.getString(), identity.account(), body.getString(),
                false, label, body).withEvidence(identity.evidence()), false, line.nameStart(), line.nameEnd());
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
        return resolve(uuid[0], account[0], conflict[0]);
    }

    private static Identity resolve(UUID wireUuid, String account, boolean conflict) {
        if (conflict) return new Identity(null, null, true, IdentityEvidence.CONFLICT);
        UUID knownUuid = ChatClassifier.resolveAccountUuid(account);
        String knownAccount = ChatClassifier.observedProfileName(wireUuid);
        // An exact historical lookup confirms the UUID despite older aliases.
        // An observed profile still constrains the account attached to that UUID.
        if (knownAccount == null && wireUuid != null && knownUuid == null) {
            knownAccount = SeenPlayers.profileName(wireUuid);
        }
        if ((wireUuid != null && knownUuid != null && !wireUuid.equals(knownUuid))
                || (account != null && knownAccount != null && !account.equalsIgnoreCase(knownAccount))) {
            return new Identity(null, null, true, IdentityEvidence.CONFLICT);
        }
        return new Identity(wireUuid != null ? wireUuid : knownUuid, account != null ? account : knownAccount,
                false, wireUuid != null ? IdentityEvidence.ENTITY : IdentityEvidence.CLICK);
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
