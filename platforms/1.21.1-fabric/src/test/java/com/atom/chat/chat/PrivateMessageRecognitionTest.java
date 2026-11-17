package com.atom.chat.chat;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PrivateMessageRecognitionTest {
    @Test void outgoingPluginEnvelopeRetainsConfirmedSenderAndRichBodyForReconciliation() {
        UUID self=UUID.randomUUID();
        Text label=Text.literal("Self");
        SenderMeta identity=new SenderMeta(self,"Self","Self","Alice : secret",false,label,null).withEvidence(IdentityEvidence.ENTITY);
        var parsed=PrivateMessageRecognition.parse(Text.literal("Self -> Alice : secret"),self,"Self",List.of("Self","Alice"),List.of(),identity);
        assertNotNull(parsed);assertTrue(parsed.whisper());assertEquals("Alice",parsed.whisperPartner());
        assertEquals(self,parsed.senderUuid());assertEquals(IdentityEvidence.ENTITY,parsed.evidence());
        assertSame(label,parsed.senderComponent());assertEquals("secret",parsed.contentComponent().getString());
    }
    @Test void incomingAliasUsesTheCorroboratedAccountRatherThanTheNickname() {
        UUID peer=UUID.randomUUID();Text label=Text.literal("BlueFox");
        var identity=new SenderMeta(peer,"BlueFox","Remote_7","You : hello",false,label,null).withEvidence(IdentityEvidence.ENTITY);
        var parsed=PrivateMessageRecognition.parse(Text.literal("BlueFox -> Self : hello"),UUID.randomUUID(),"Self",List.of("Self"),List.of(),identity);
        assertNotNull(parsed);assertEquals(peer,parsed.senderUuid());assertEquals("Remote_7",parsed.whisperPartner());
        assertEquals("hello",parsed.contentText());
    }
}
