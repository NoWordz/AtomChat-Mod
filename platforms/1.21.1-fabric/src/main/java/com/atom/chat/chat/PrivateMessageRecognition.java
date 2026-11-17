package com.atom.chat.chat;

import net.minecraft.text.Text;
import java.util.*;

/** Final text-envelope fallback. Attribution evidence and private direction remain separate. */
public final class PrivateMessageRecognition {
    private PrivateMessageRecognition() {}
    public static SenderMeta parse(Text message, UUID self, String ownName, List<String> knownNames,
                                   List<String> templates, SenderMeta identity) {
        String raw=message.getString();
        var template=ChatTemplates.matchWhisper(raw,templates,knownNames);
        WhisperTextParser.WhisperHit hit=template.isPresent()
                ? new WhisperTextParser.WhisperHit(true,template.get().displayLabel(),template.get().content()) : null;
        if(hit==null) hit=WhisperTextParser.tryParse(raw,ownName);
        if(hit==null) hit=WhisperTextParser.tryParseAnchored(raw,knownNames);
        if(hit==null) return null;
        var info=ChatClassifier.resolveOnlinePlayer(hit.partnerDisplay());
        String partner=info!=null?info.getProfile().getName():hit.partnerDisplay();
        UUID peer=info!=null?info.getProfile().getId():ChatClassifier.resolveUuid(hit.partnerDisplay());
        IdentityEvidence source=template.isPresent()?IdentityEvidence.TEMPLATE:IdentityEvidence.TEXT;
        boolean incoming=hit.incoming();
        UUID uuid=incoming?peer:self;
        String display=incoming?hit.partnerDisplay():ownName;
        String profile=incoming?partner:ownName;
        Text label=null;
        String original=null;
        if(identity!=null && incoming && identity.senderName()!=null
                && identity.senderName().contains(hit.partnerDisplay()) && identity.profileName()!=null) {
            uuid=identity.senderUuid();display=identity.senderName();profile=identity.profileName();partner=profile;
            source=identity.evidence();label=identity.senderComponent();original=identity.originalContent();
        } else if(identity!=null && !incoming && self!=null && self.equals(identity.senderUuid())) {
            uuid=self;display=identity.senderName();profile=identity.profileName();
            source=identity.evidence();label=identity.senderComponent();original=identity.originalContent();
        }
        if(!incoming && self==null) return null;
        int bodyAt=raw.lastIndexOf(hit.content());
        Text body=bodyAt<0?null:ComponentIdentity.slice(message,bodyAt,bodyAt+hit.content().length());
        return new SenderMeta(uuid,display,profile,hit.content(),false,true,partner,label,body)
                .withEvidence(source).withOriginalContent(original);
    }
}
