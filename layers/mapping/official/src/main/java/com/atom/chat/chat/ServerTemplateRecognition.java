package com.atom.chat.chat;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.text.RichText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/** Matches configured envelopes after the native and system guards. */
public final class ServerTemplateRecognition {
    private static final Logger LOG = Logger.getLogger(ServerTemplateRecognition.class.getName());

    private ServerTemplateRecognition() {}

    public static SenderMeta parseConfigured(Component message, ComponentIdentity.Inspection inspection) {
        var client = Minecraft.getInstance();
        var player = client == null ? null : client.player;
        var rules = AtomChatConfig.get();
        var publicFormats = rules.chatTemplates == null ? List.<String>of() : rules.chatTemplates;
        var privateFormats = rules.whisperTemplates == null ? List.<String>of() : rules.whisperTemplates;
        var names = publicFormats.isEmpty() && privateFormats.isEmpty()
                ? List.<String>of() : ChatPipeline.onlineNameCandidates();
        return parse(message, null, player == null ? null : player.getName().getString(),
                player == null ? null : player.getUUID(), publicFormats, privateFormats, names, inspection);
    }

    static SenderMeta parse(Component message, SenderMeta corroboration, String own, UUID ownUuid) {
        return parse(message, corroboration, own, ownUuid, List.of(), List.of(), List.of());
    }

    static SenderMeta parse(Component message, SenderMeta corroboration, String own, UUID ownUuid,
                            List<String> publicFormats, List<String> privateFormats, List<String> knownNames) {
        return parse(message, corroboration, own, ownUuid, publicFormats, privateFormats, knownNames, null);
    }

    private static SenderMeta parse(Component message, SenderMeta corroboration, String own, UUID ownUuid,
                                    List<String> publicFormats, List<String> privateFormats, List<String> knownNames,
                                    ComponentIdentity.Inspection inspection) {
        var rules = AtomChatConfig.get();
        var server = rules.serverPacksEnabled ? ServerTemplateStore.rules() : List.<MessageTemplate>of();
        if (server.isEmpty() && publicFormats.isEmpty() && privateFormats.isEmpty()) return null;
        String text = message.getString();
        if (text.length() > 32768) return null;
        Component visible = message;
        if (text.indexOf('§') >= 0) {
            var rich = RichText.of(message);
            text = rich.getString();
            visible = rich.toText();
        }

        var preview = ChatTemplates.matchConfigured(text, server, publicFormats, privateFormats, knownNames);
        if ("split_conflict".equals(preview.error()) && visible == message) {
            var segments = new java.util.HashSet<TemplateParser.Span>();
            int[] offset = {0};
            message.visit((style, part) -> {
                if (!part.isEmpty()) segments.add(new TemplateParser.Span(offset[0], offset[0] + part.length()));
                offset[0] += part.length();
                return java.util.Optional.empty();
            }, net.minecraft.network.chat.Style.EMPTY);
            if (offset[0] == text.length()) {
                preview = ChatTemplates.matchConfigured(text, server, publicFormats, privateFormats, knownNames, segments);
            }
        }
        if (preview.state() == TemplateParser.State.AMBIGUOUS) {
            if (rules.debug) {
                String reason = preview.error();
                LOG.info(com.atom.chat.diagnostics.AtomChatLog.message("capture", () -> "[atomchat] template fallback (" + reason + ")"));
            }
            return conflict();
        }
        if (preview.state() != TemplateParser.State.MATCH) return null;

        var match = preview.match();
        var fields = match.fields();
        String account = fields.get("sender");
        String target = fields.get("target");
        String nickname = fields.get("display");
        var kind = match.kind();
        boolean outgoing = kind == MessageTemplate.Kind.OUTGOING;
        boolean incoming = kind == MessageTemplate.Kind.INCOMING;
        if ((incoming && target != null && !target.equalsIgnoreCase(own))
                || (outgoing && (own == null || account != null && !account.equalsIgnoreCase(own)))) {
            return conflict();
        }
        if (WhisperTextParser.isSpyEnvelope(text)
                || (WhisperTextParser.isThirdPartyEnvelope(text, own) && !outgoing)
                || (!incoming && !outgoing && WhisperTextParser.tryParse(text, own) != null)) return conflict();

        UUID declared = fields.containsKey("uuid") ? UUID.fromString(fields.get("uuid")) : null;
        if (outgoing && declared != null && !declared.equals(ownUuid)) return conflict();
        var body = match.spans().get("message");
        Component content = ComponentIdentity.slice(visible, body.start(), body.end());
        Component label = ComponentIdentity.slice(visible, 0, body.start()).copy()
                .append(ComponentIdentity.slice(visible, body.end(), text.length()));
        if (outgoing) {
            return outgoing(match, message, visible, own, ownUuid, label, content);
        }

        UUID uuid = null;
        String verifiedAccount = null;
        IdentityEvidence evidence = IdentityEvidence.SERVER_TEMPLATE;
        String original = null;
        for (String field : List.of("sender", "display")) {
            var identity = senderSlot(message, visible, match, field);
            if (identity == null) continue;
            if (identity.conflict() || contradicts(account, identity.account()) || contradicts(declared, identity.uuid())
                    || contradicts(uuid, identity.uuid()) || contradicts(verifiedAccount, identity.account())) {
                return conflict();
            }
            if (!identity.evidence().confirmsIdentity() || identity.uuid() == null) continue;
            uuid = identity.uuid();
            if (identity.account() != null) verifiedAccount = identity.account();
            evidence = identity.evidence();
        }

        // The ordinary scanner has separate, original-component offsets. It can
        // veto contradictory evidence only when its sender lies in the configured label.
        if (inspection != null && visible == message && inspection.meta() != null) {
            for (String field : List.of("sender", "display")) {
                var span = match.spans().get(field);
                if (span != null && overlaps(span.start(), span.end(), inspection.senderStart(), inspection.senderEnd())) {
                    var observed = inspection.meta();
                    if (contradicts(account, observed.profileName()) || contradicts(declared, observed.senderUuid())
                            || contradicts(verifiedAccount, observed.profileName()) || contradicts(uuid, observed.senderUuid())) {
                        return conflict();
                    }
                }
            }
        }
        if (corroboration != null) {
            if (contradicts(account, corroboration.profileName()) || contradicts(declared, corroboration.senderUuid())
                    || contradicts(uuid, corroboration.senderUuid()) || contradicts(verifiedAccount, corroboration.profileName())) {
                return conflict();
            }
            if (corroboration.evidence() == IdentityEvidence.NATIVE) {
                if (corroboration.senderUuid() != null) uuid = corroboration.senderUuid();
                if (corroboration.profileName() != null) verifiedAccount = corroboration.profileName();
                evidence = corroboration.evidence();
                original = corroboration.originalContent();
            }
        }
        if (incoming) {
            if (uuid != null && uuid.equals(ownUuid)) return conflict();
            var peer = senderSlot(message, visible, match, "target");
            if (peer != null && (peer.conflict() || contradicts(ownUuid, peer.uuid()) || contradicts(own, peer.account()))) {
                return conflict();
            }
            boolean localParticipation = peer != null && ownUuid != null && ownUuid.equals(peer.uuid())
                    && peer.evidence().confirmsIdentity();
            var address = WhisperTextParser.tryParse(text, own);
            if (address != null && !address.incoming()) return conflict();
            if (address != null && address.incoming() && WhisperTextParser.hasLocalRecipient(text, own)
                    && (address.partnerDisplay().equalsIgnoreCase(verifiedAccount)
                    || address.partnerDisplay().equalsIgnoreCase(nickname))
                    && address.content().strip().equals(content.getString().strip())) {
                localParticipation = true;
            }
            if (!localParticipation || verifiedAccount == null) return unknown(label, content);
        }
        if (uuid == null) return unknown(label, content);
        return new SenderMeta(uuid, nickname == null ? verifiedAccount : nickname, verifiedAccount,
                content.getString(), false, incoming, incoming ? verifiedAccount : null, label, content, evidence, original);
    }

    private static SenderMeta outgoing(TemplateParser.Match match, Component source, Component visible, String own,
                                       UUID ownUuid, Component label, Component content) {
        String target = match.fields().get("target");
        var peer = senderSlot(source, visible, match, "target");
        if (peer != null && (peer.conflict() || contradicts(target, peer.account()))) return conflict();

        boolean provenSender = false;
        for (String slot : List.of("sender", "display")) {
            var identity = senderSlot(source, visible, match, slot);
            if (identity == null) continue;
            if (identity.conflict() || contradicts(own, identity.account()) || contradicts(ownUuid, identity.uuid())) {
                return conflict();
            }
            provenSender |= ownUuid != null && ownUuid.equals(identity.uuid()) && identity.evidence().confirmsIdentity();
        }
        if (!provenSender || peer == null || !peer.evidence().confirmsIdentity() || peer.uuid() == null
                || !target.equalsIgnoreCase(peer.account())) {
            if (WhisperTextParser.isThirdPartyEnvelope(visible.getString(), own)
                    && !WhisperTextParser.hasLiteralYouSender(visible.getString())) return conflict();
            return unknown(label, content);
        }
        if (ownUuid.equals(peer.uuid()) && !own.equalsIgnoreCase(target)) return conflict();
        return new SenderMeta(ownUuid, own, own, content.getString(), false, true, target,
                label, content, IdentityEvidence.ENTITY, null, peer.uuid());
    }

    private static ComponentIdentity.Identity senderSlot(Component source, Component visible, TemplateParser.Match match,
                                                         String field) {
        var span = match.spans().get(field);
        if (span == null) return null;
        var identity = ComponentIdentity.resolveSender(ComponentIdentity.slice(visible, span.start(), span.end()));
        if (identity.uuid() == null || identity.conflict()) return identity;
        var body = match.spans().get("message");
        if (hoverCoversBoth(source, 0, visible.getString().length(), span, body, identity.uuid(), visible != source)) {
            return null;
        }
        return identity;
    }

    private static boolean hoverCoversBoth(Component node, int start, int end, TemplateParser.Span actor,
                                           TemplateParser.Span body, UUID uuid, boolean legacy) {
        if (!overlaps(start, end, actor.start(), actor.end()) || !overlaps(start, end, body.start(), body.end())) {
            return false;
        }
        var hover = node.getStyle().getHoverEvent();
        var entity = hover == null ? null : hover.getValue(HoverEvent.Action.SHOW_ENTITY);
        if (entity != null && uuid.equals(entity.id)) return true;
        var children = node.getSiblings();
        for (int index = children.size() - 1; index >= 0; index--) {
            var child = children.get(index);
            int length = visible(child, legacy).length();
            if (hoverCoversBoth(child, end - length, end, actor, body, uuid, legacy)) return true;
            end -= length;
        }
        if (node.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                && overlaps(start, end, actor.start(), actor.end()) && overlaps(start, end, body.start(), body.end())) {
            String rendered = visible(node, legacy);
            if (end - start > rendered.length()) return true;
            String local = rendered.substring(0, end - start);
            for (Object arg : translated.getArgs()) {
                if (!(arg instanceof Component child) || !hasEntityHover(child, uuid)) continue;
                String value = visible(child, legacy);
                int at = value.isEmpty() ? -1 : local.indexOf(value);
                // Repeated or non-contiguous arguments cannot establish an independent boundary.
                if (at < 0 || local.indexOf(value, at + 1) >= 0
                        || hoverCoversBoth(child, start + at, start + at + value.length(), actor, body, uuid, legacy)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String visible(Component node, boolean legacy) {
        return legacy ? RichText.of(node).getString() : node.getString();
    }

    private static boolean hasEntityHover(Component node, UUID uuid) {
        return node.visit((style, part) -> {
            var hover = style.getHoverEvent();
            var entity = hover == null ? null : hover.getValue(HoverEvent.Action.SHOW_ENTITY);
            return entity != null && uuid.equals(entity.id) ? java.util.Optional.of(true) : java.util.Optional.<Boolean>empty();
        }, net.minecraft.network.chat.Style.EMPTY).isPresent();
    }

    private static boolean overlaps(int start, int end, int otherStart, int otherEnd) {
        return start < otherEnd && end > otherStart;
    }

    public static SenderMeta unknown(Component label, Component content) {
        Component display = RichText.of(label).stripInteractions().toText().copy()
                .append(Component.literal(" · ")).append(Component.translatable("atomchat.chat.identity_unknown"));
        return new SenderMeta(null, display.getString(), null, content.getString(), false, false, null,
                display, content, IdentityEvidence.UNKNOWN_TEMPLATE, null);
    }

    private static boolean contradicts(String a, String b) {
        return a != null && b != null && !a.equalsIgnoreCase(b);
    }

    private static boolean contradicts(UUID a, UUID b) {
        return a != null && b != null && !a.equals(b);
    }

    private static SenderMeta conflict() {
        return SenderMeta.systemMeta().withEvidence(IdentityEvidence.CONFLICT);
    }
}
