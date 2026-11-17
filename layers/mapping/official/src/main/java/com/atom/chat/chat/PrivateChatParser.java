package com.atom.chat.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.UUID;

/**
 * Vanilla /msg key parser shared by the MessageHandler capture mixin and the
 * ChatHud fallback. Returns structured SenderMeta for
 * commands.message.display.incoming/outgoing, or null for any other message.
 */
public final class PrivateChatParser {
    private PrivateChatParser() {
    }

    public static SenderMeta tryParse(Component message) {
        if (!(message.getContents() instanceof TranslatableContents tc)) {
            return null;
        }
        String key = tc.getKey();
        Object[] args = tc.getArgs();
        if (key.equals("commands.message.display.incoming") && args.length >= 2) {
            Component name = asText(args[0]);
            Component content = asText(args[1]);
            String display = clean(name.getString());
            var identity = ComponentIdentity.resolveSender(name);
            if (identity.conflict()) return SenderMeta.systemMeta().withEvidence(IdentityEvidence.CONFLICT);
            PlayerInfo info = identity.uuid() == null && identity.account() == null && display != null ? ChatClassifier.resolveOnlinePlayer(display) : null;
            String profile = identity.account() != null ? identity.account() : info != null ? info.getProfile().getName()
                    : identity.uuid() != null ? ChatClassifier.profileName(identity.uuid()) : display;
            UUID uuid = identity.account() != null || identity.uuid() != null ? identity.uuid()
                    : info != null ? info.getProfile().getId() : SeenPlayers.findUuid(display);
            return new SenderMeta(uuid, display, profile, content.getString(), false, true,
                    profile, name, content).withEvidence(IdentityEvidence.TRANSLATION);
        }
        if (key.equals("commands.message.display.outgoing") && args.length >= 2) {
            Component partnerText = asText(args[0]);
            Component content = asText(args[1]);
            String display = clean(partnerText.getString());
            if (display == null) {
                return null;
            }
            PlayerInfo info = ChatClassifier.resolveOnlinePlayer(display);
            String partner = info != null ? info.getProfile().getName() : display;
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return null;
            }
            String own = client.player.getName().getString();
            UUID ownUuid = client.player.getUUID();
            return new SenderMeta(ownUuid, own, own, content.getString(), false, true,
                    partner, Component.literal(own), content).withEvidence(IdentityEvidence.TRANSLATION);
        }
        return null;
    }

    private static Component asText(Object arg) {
        return arg instanceof Component text ? text : Component.literal(String.valueOf(arg));
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.replaceAll("§.", "").trim();
        return stripped.isEmpty() ? null : stripped;
    }
}
