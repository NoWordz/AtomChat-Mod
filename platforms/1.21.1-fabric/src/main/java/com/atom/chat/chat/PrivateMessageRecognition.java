package com.atom.chat.chat;

import net.minecraft.text.Text;
import java.util.*;

/** Final text-envelope fallback. Attribution evidence and private direction remain separate. */
public final class PrivateMessageRecognition {
    private PrivateMessageRecognition() {}
    public static SenderMeta parse(Text message, UUID self, String ownName, List<String> knownNames,
                                   SenderMeta identity) {
        String raw = message.getString();
        var hit = WhisperTextParser.tryParse(raw, ownName);
        if (hit == null) hit = WhisperTextParser.tryParseAnchored(raw, knownNames);
        if (hit == null) return null;
        if (!hit.incoming() || !WhisperTextParser.hasLocalRecipient(raw, ownName)
                || identity == null || !identity.evidence().confirmsIdentity() || identity.senderUuid() == null) {
            return SenderMeta.systemMeta();
        }
        String partner = identity.profileName();
        if (partner == null || identity.senderUuid().equals(self)
                || (!hit.partnerDisplay().equalsIgnoreCase(partner)
                && !hit.partnerDisplay().equalsIgnoreCase(identity.senderName())
                && !hit.partnerDisplay().equalsIgnoreCase(visibleSender(identity)))) {
            return SenderMeta.systemMeta().withEvidence(IdentityEvidence.CONFLICT);
        }
        int bodyAt=raw.lastIndexOf(hit.content());
        Text body=bodyAt<0?null:ComponentIdentity.slice(message,bodyAt,bodyAt+hit.content().length());
        return new SenderMeta(identity.senderUuid(), identity.senderName(), identity.profileName(),
                hit.content(), false, true, partner, identity.senderComponent(), body,
                identity.evidence(), identity.originalContent());
    }

    private static String visibleSender(SenderMeta identity) {
        if (identity.senderComponent() == null) return null;
        return identity.senderComponent().getString().strip();
    }
}
