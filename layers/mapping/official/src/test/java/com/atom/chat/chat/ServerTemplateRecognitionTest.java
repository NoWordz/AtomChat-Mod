package com.atom.chat.chat;
import org.junit.jupiter.api.*;
import net.minecraft.network.chat.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ServerTemplateRecognitionTest {
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    private java.lang.reflect.Field configField;private Object previousConfig;
    @BeforeEach void config()throws Exception{configField=com.atom.chat.config.AtomChatConfig.class.getDeclaredField("instance");configField.setAccessible(true);previousConfig=configField.get(null);configField.set(null,new com.atom.chat.config.AtomChatConfig());}
    @AfterEach void clear()throws Exception{ServerTemplateStore.clear();SeenPlayers.clear();configField.set(null,previousConfig);}
    private void install(MessageTemplate.Kind kind,String format){assertTrue(ServerTemplateStore.install(List.of(new MessageTemplate("test","test",kind,format,true))));}
    @Test void coldNameAndRichBodyRemainWithoutInventingIdentity(){
        install(MessageTemplate.Kind.PUBLIC,"[{channel}] {sender}: {message}");
        var line=Component.literal("[Global] Remote: ").append(Component.literal("hello").setStyle(Style.EMPTY.withBold(true)));
        var meta=ServerTemplateRecognition.parse(line,null,null,null);
        assertNotNull(meta);assertNull(meta.senderUuid());assertEquals("Remote",meta.profileName());assertEquals(IdentityEvidence.SERVER_TEMPLATE,meta.evidence());
        assertTrue(meta.senderComponent().getString().contains("[Global]"));assertEquals("hello",meta.contentComponent().getString());
        assertTrue(meta.contentComponent().getStyle().isBold()||meta.contentComponent().getSiblings().stream().anyMatch(c->c.getStyle().isBold()));
    }
    @Test void wireIdentityConflictIsTerminalAndAmbiguityIsNotClaimed(){
        install(MessageTemplate.Kind.PUBLIC,"{sender}: {message}");
        var known=new SenderMeta(UUID.randomUUID(),"Other","Other",null,false);
        var meta=ServerTemplateRecognition.parse(Component.literal("Remote: hi"),known,null,null);assertTrue(meta.system());assertEquals(IdentityEvidence.CONFLICT,meta.evidence());
        install(MessageTemplate.Kind.PUBLIC,"{sender}{message}");assertTrue(ServerTemplateRecognition.parse(Component.literal("Remotehello"),null,null,null).system());
    }
    @Test void outgoingUsesLocalSenderAndCorroboratedRecipientWithoutConfusingTheirUuids(){
        install(MessageTemplate.Kind.OUTGOING,"You -> {target}: {message}");UUID own=UUID.randomUUID(),remote=UUID.randomUUID();
        var recipient=new SenderMeta(remote,"Remote","Remote",null,false).withEvidence(IdentityEvidence.ENTITY);
        var meta=ServerTemplateRecognition.parse(Component.literal("You -> Remote: hi"),recipient,"Me",own);
        assertTrue(meta.whisper());assertEquals(own,meta.senderUuid());assertEquals("Remote",meta.whisperPartner());assertEquals(IdentityEvidence.SERVER_TEMPLATE,meta.evidence());
        assertEquals(remote,SeenPlayers.findAccountUuid("Remote"));
    }
}
