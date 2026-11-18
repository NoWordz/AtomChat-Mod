package com.atom.chat.chat;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PrivateMessageRecognitionTest {
    @Test void outgoingTextEnvelopeWithConfirmedSenderButUnknownPeerStaysInOriginalFeed() {
        UUID self=UUID.randomUUID();
        Text label=Text.literal("Self");
        SenderMeta identity=new SenderMeta(self,"Self","Self","Alice : secret",false,label,null).withEvidence(IdentityEvidence.ENTITY);
        var parsed=PrivateMessageRecognition.parse(Text.literal("Self -> Alice : secret"),self,"Self",List.of("Self","Alice"),identity);
        assertNotNull(parsed);
        assertTrue(parsed.system());
    }
    @Test void incomingAliasUsesTheCorroboratedAccountRatherThanTheNickname() {
        UUID peer=UUID.randomUUID();Text label=Text.literal("BlueFox");
        var identity=new SenderMeta(peer,"BlueFox","Remote_7","You : hello",false,label,null).withEvidence(IdentityEvidence.ENTITY);
        var parsed=PrivateMessageRecognition.parse(Text.literal("BlueFox -> Self : hello"),UUID.randomUUID(),"Self",List.of("Self"),identity);
        assertNotNull(parsed);assertEquals(peer,parsed.senderUuid());assertEquals("Remote_7",parsed.whisperPartner());
        assertEquals("hello",parsed.contentText());
    }
}
