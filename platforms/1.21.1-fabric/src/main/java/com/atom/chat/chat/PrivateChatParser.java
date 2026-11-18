package com.atom.chat.chat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.UUID;

/**
 * Vanilla /msg key parser shared by the MessageHandler capture mixin and the
 * ChatHud fallback. Returns structured SenderMeta for
 * commands.message.display.incoming/outgoing, or null for any other message.
 */
public final class PrivateChatParser {
    private PrivateChatParser() {
    }

    public static SenderMeta tryParse(Text message) {
        var client = MinecraftClient.getInstance();
        var player = client == null ? null : client.player;
        return tryParse(message, player == null ? null : player.getName().getString(),
                player == null ? null : player.getUuid());
    }

    static SenderMeta tryParse(Text message, String own, UUID ownUuid) {
        if (!(message.getContent() instanceof TranslatableTextContent tc)) {
            return null;
        }
        String key = tc.getKey();
        Object[] args = tc.getArgs();
        if (key.equals("commands.message.display.incoming") && args.length >= 2) {
            Text name = asText(args[0]);
            Text content = asText(args[1]);
            String display = clean(name.getString());
            var identity = ComponentIdentity.resolveSender(name);
            if (identity.conflict()) return SenderMeta.systemMeta().withEvidence(IdentityEvidence.CONFLICT);
            UUID uuid = identity.evidence() == IdentityEvidence.ENTITY ? identity.uuid() : null;
            String profile = identity.account() != null ? identity.account() : ChatClassifier.profileName(uuid);
            return new SenderMeta(uuid, display, profile, content.getString(), false, true,
                    profile, name, content).withEvidence(IdentityEvidence.TRANSLATION);
        }
        if (key.equals("commands.message.display.outgoing") && args.length >= 2) {
            Text partnerText = asText(args[0]);
            Text content = asText(args[1]);
            var peer = ComponentIdentity.resolveSender(partnerText);
            if (peer.conflict()) return SenderMeta.systemMeta().withEvidence(IdentityEvidence.CONFLICT);
            if (ownUuid == null || peer.uuid() == null || !peer.evidence().confirmsIdentity() || peer.account() == null) {
                return SenderMeta.systemMeta();
            }
            return new SenderMeta(ownUuid, own, own, content.getString(), false, true,
                    peer.account(), Text.literal(own), content, IdentityEvidence.TRANSLATION, null, peer.uuid());
        }
        return null;
    }

    private static Text asText(Object arg) {
        return arg instanceof Text text ? text : Text.literal(String.valueOf(arg));
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.replaceAll("§.", "").trim();
        return stripped.isEmpty() ? null : stripped;
    }
}
