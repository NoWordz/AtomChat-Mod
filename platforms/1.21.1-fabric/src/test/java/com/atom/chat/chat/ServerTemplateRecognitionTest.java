package com.atom.chat.chat;
import org.junit.jupiter.api.*;
import net.minecraft.text.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ServerTemplateRecognitionTest {
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();}
    private java.lang.reflect.Field configField;private Object previousConfig;
    @BeforeEach void config()throws Exception{configField=com.atom.chat.config.AtomChatConfig.class.getDeclaredField("instance");configField.setAccessible(true);previousConfig=configField.get(null);configField.set(null,new com.atom.chat.config.AtomChatConfig());}
    @AfterEach void clear()throws Exception{ServerTemplateStore.clear();SeenPlayers.clear();configField.set(null,previousConfig);}
    private void install(MessageTemplate.Kind kind,String format){assertTrue(ServerTemplateStore.install(List.of(new MessageTemplate("test","test",kind,format,true))));}
    @Test void coldNameAndRichBodyRemainWithoutInventingIdentity(){
        install(MessageTemplate.Kind.PUBLIC,"[{channel}] {sender}: {message}");
        var line=Text.literal("[Global] Remote: ").append(Text.literal("hello").setStyle(Style.EMPTY.withBold(true)));
        var meta=ServerTemplateRecognition.parse(line,null,null,null);
        assertNotNull(meta);assertNull(meta.senderUuid());assertNull(meta.profileName());
        assertTrue(meta.senderComponent().getString().contains("[Global]"));assertEquals("hello",meta.contentComponent().getString());
        assertTrue(meta.contentComponent().getStyle().isBold()||meta.contentComponent().getSiblings().stream().anyMatch(c->c.getStyle().isBold()));
    }
    @Test void wireIdentityConflictIsTerminalAndAmbiguityIsNotClaimed(){
        install(MessageTemplate.Kind.PUBLIC,"{sender}: {message}");
        var known=new SenderMeta(UUID.randomUUID(),"Other","Other",null,false);
        var meta=ServerTemplateRecognition.parse(Text.literal("Remote: hi"),known,null,null);assertTrue(meta.system());assertEquals(IdentityEvidence.CONFLICT,meta.evidence());
        install(MessageTemplate.Kind.PUBLIC,"{sender}{message}");assertTrue(ServerTemplateRecognition.parse(Text.literal("Remotehello"),null,null,null).system());
    }
    @Test void corroboratingOnlyTheRecipientCannotConfirmAnOutgoingTemplate(){
        install(MessageTemplate.Kind.OUTGOING,"You -> {target}: {message}");UUID own=UUID.randomUUID(),remote=UUID.randomUUID();
        var recipient=new SenderMeta(remote,"Remote","Remote",null,false).withEvidence(IdentityEvidence.ENTITY);
        var meta=ServerTemplateRecognition.parse(Text.literal("You -> Remote: hi"),recipient,"Me",own);
        assertFalse(meta.whisper());assertNull(meta.senderUuid());assertNull(meta.profileName());
        assertNull(SeenPlayers.findAccountUuid("Remote"));
    }
    @Test void bareCrossServerNameDoesNotBorrowALocallySeenIdentity(){
        install(MessageTemplate.Kind.PUBLIC,"[{channel}] {sender}: {message}");
        SeenPlayers.remember(UUID.randomUUID(),"Remote","Remote");
        var parsed=ServerTemplateRecognition.parse(Text.literal("[OtherServer] Remote: hi"),null,null,null);
        assertNull(parsed.senderUuid());assertNull(parsed.profileName());
    }

    @Test void configuredPublicAndIncomingSourcesCannotDisagreeOnTheSameLine(){
        install(MessageTemplate.Kind.PUBLIC,"[Hub] {sender}: {message}");
        var line=Text.literal("[Hub] Remote: hello");
        var parsed=ServerTemplateRecognition.parse(line,null,"Self",UUID.randomUUID(),
                List.of(),List.of("[Hub] {name}: {content}"),List.of("Remote"));
        assertTrue(parsed.system());
        assertEquals(IdentityEvidence.CONFLICT,parsed.evidence());
    }

    @Test void velocityYouerRawSystemComponentKeepsLiteralBodyAndItsInteractions(){
        var click=new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Remote");
        var line=Text.literal("§6[Hub] 云狐: §r").append(Text.literal(" 云狐\n😀 §q  ")
                .setStyle(Style.EMPTY.withBold(true).withClickEvent(click)));
        var parsed=ServerTemplateRecognition.parse(line,null,"Self",UUID.randomUUID(),
                List.of("[{channel}] {display}: {message}"),List.of(),List.of());
        assertEquals(IdentityEvidence.UNKNOWN_TEMPLATE,parsed.evidence());
        assertEquals(" 云狐\n😀 §q  ",parsed.contentText());
        assertTrue(parsed.senderComponent().getString().contains("[Hub] 云狐"));
        assertTrue(com.atom.chat.text.RichText.of(parsed.contentComponent()).runs().stream()
                .anyMatch(run->run.style().isBold() && click.equals(run.style().getClickEvent())));
        var bubble=new ChatMessage(line,false,false,null,null,parsed.senderUuid(),parsed.senderName(),"",parsed.contentText());
        assertTrue(bubble.isIdentityUnknown());
        assertNull(bubble.getProfileName());
        assertEquals(parsed.contentText(),bubble.getContentText());
    }

    @Test void declaredUuidWithoutIndependentEvidenceIsOnlyAHint(){
        install(MessageTemplate.Kind.PUBLIC,"[{uuid}] {sender}: {message}");
        UUID claimed=UUID.randomUUID();
        var line=Text.literal("["+claimed+"] Remote: hello");
        assertEquals(IdentityEvidence.UNKNOWN_TEMPLATE,ServerTemplateRecognition.parse(line,null,null,null).evidence());
        var corroboration=new SenderMeta(claimed,"Remote","Remote",null,false).withEvidence(IdentityEvidence.NATIVE);
        assertEquals(claimed,ServerTemplateRecognition.parse(line,corroboration,null,null).senderUuid());
        var other=new SenderMeta(UUID.randomUUID(),"Remote","Remote",null,false).withEvidence(IdentityEvidence.ENTITY);
        assertTrue(ServerTemplateRecognition.parse(line,other,null,null).system());
    }

    @Test void senderSlotCanVerifyAnEntityButALineWideHoverCannot(){
        install(MessageTemplate.Kind.PUBLIC,"[Hub] {sender}: {message}");
        UUID remote=UUID.randomUUID();
        var entity=new HoverEvent.EntityContent(net.minecraft.entity.EntityType.PLAYER,remote,Optional.of(Text.literal("Remote")));
        var hover=new HoverEvent(HoverEvent.Action.SHOW_ENTITY,entity);
        var style=Style.EMPTY.withHoverEvent(hover).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Remote"));
        var body=Text.literal(" hello  ");
        var verified=Text.literal("[Hub] ").append(Text.literal("Remote").setStyle(style)).append(": ").append(body);
        assertEquals(remote,ServerTemplateRecognition.parse(verified,null,null,null).senderUuid());
        var unverified=Text.literal("[Hub] Remote:  hello  ").setStyle(style);
        var parsed=ServerTemplateRecognition.parse(unverified,ComponentIdentity.detect(unverified),null,null);
        assertEquals(IdentityEvidence.UNKNOWN_TEMPLATE,parsed.evidence());
        assertEquals(body.getString(),parsed.contentText());
        var nested=Text.literal("").append(Text.literal("[Hub] Remote: ").setStyle(style).append(
                Text.literal("First").setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Text.literal("Other"))))
                        .append(" rest")));
        assertEquals(IdentityEvidence.UNKNOWN_TEMPLATE,ServerTemplateRecognition.parse(nested,null,null,null).evidence());
        var argument=Text.literal("[Hub] Remote: body").setStyle(style);
        var translated=Text.translatableWithFallback("atomchat.tests.unknown_envelope","%s",argument);
        assertEquals(IdentityEvidence.UNKNOWN_TEMPLATE,ServerTemplateRecognition.parse(translated,null,null,null).evidence());
    }

    @Test void incomingTemplateNeedsMatchingDirectionAndLocalParticipation(){
        install(MessageTemplate.Kind.INCOMING,"[{sender} -> {target}] {message}");
        UUID self=UUID.randomUUID(), remote=UUID.randomUUID();
        var verified=Text.literal("[").append(actor("Remote","Remote",remote)).append(" -> Self] hello");
        assertTrue(ServerTemplateRecognition.parse(verified,null,"Self",self).whisper());
        var wrongDirection=Text.literal("[").append(actor("Self","Self",self)).append(" -> Self] hello");
        assertTrue(ServerTemplateRecognition.parse(wrongDirection,null,"Self",self).system());
        install(MessageTemplate.Kind.INCOMING,"{sender}: {message}");
        var noConversation=Text.literal("").append(actor("Remote","Remote",remote)).append(": hello");
        assertEquals(IdentityEvidence.UNKNOWN_TEMPLATE,ServerTemplateRecognition.parse(noConversation,null,"Self",self).evidence());
        install(MessageTemplate.Kind.INCOMING,"{sender} whispers: {message}");
        var unspecifiedRecipient=Text.literal("").append(actor("Remote","Remote",remote)).append(" whispers: hello");
        assertEquals(IdentityEvidence.UNKNOWN_TEMPLATE,ServerTemplateRecognition.parse(unspecifiedRecipient,null,"Self",self).evidence());
        install(MessageTemplate.Kind.INCOMING,"{sender} whispers to you: {message}");
        var addressed=Text.literal("").append(actor("Remote","Remote",remote)).append(" whispers to you: hello");
        assertTrue(ServerTemplateRecognition.parse(addressed,null,"Self",self).whisper());
    }

    @Test void configuredOutgoingWithBothActorSlotsCanCorrelateAUniqueSend(){
        install(MessageTemplate.Kind.OUTGOING,"{sender} -> {target}: {message}");
        UUID own=UUID.randomUUID(), peer=UUID.randomUUID();
        var line=Text.literal("").append(actor("Self","Self",own)).append(" -> ").append(actor("Remote","Remote",peer)).append(": hello");
        var parsed=ServerTemplateRecognition.parse(line,null,"Self",own);
        assertTrue(parsed.whisper());
        assertEquals(own,parsed.senderUuid());
        assertEquals(peer,parsed.whisperPartnerUuid());
        assertFalse(WhisperTextParser.isThirdPartyEnvelope(line.getString(),"Self"));
    }

    @Test void vanillaPrivateReceiptUsesTheReceiverSlotInsteadOfASameNamedAlias(){
        UUID self=UUID.randomUUID(), receiver=UUID.randomUUID();
        SeenPlayers.remember(UUID.randomUUID(),"Remote","Remote");
        var line=Text.translatable("commands.message.display.outgoing",actor("Remote","Actual",receiver),"hello");
        var parsed=PrivateChatParser.tryParse(line,"Self",self);
        assertEquals("Actual",parsed.whisperPartner());
        assertEquals(receiver,parsed.whisperPartnerUuid());
        var bare=Text.translatable("commands.message.display.outgoing",Text.literal("Remote"),"hello");
        assertTrue(PrivateChatParser.tryParse(bare,"Self",self).system());
    }

    @Test void trChatDefaultGlobalAndPrivateLayoutsAreDisplayOnlyWithoutPacketIdentity(){
        UUID self=UUID.randomUUID();
        SeenPlayers.remember(self,"Self","Self");
        assertTrue(ServerTemplateStore.install(List.of(
                new MessageTemplate("global","Global",MessageTemplate.Kind.PUBLIC,"[{channel}] {sender}: {message}",true),
                new MessageTemplate("recv","Receive",MessageTemplate.Kind.INCOMING,"[{sender} ➥ {target}] {message}",true),
                new MessageTemplate("sent","Send",MessageTemplate.Kind.OUTGOING,"[{sender} ➦ {target}] {message}",true))));
        for (String value : List.of("[Lobby] Self: same", "[Remote ➥ Self] same", "[Self ➦ Remote] same")) {
            var meta=ServerTemplateRecognition.parse(Text.literal(value),null,"Self",self);
            assertEquals(IdentityEvidence.UNKNOWN_TEMPLATE,meta.evidence(),value);
            assertEquals("same",meta.contentText());
            assertFalse(meta.whisper());
            assertNull(meta.senderUuid());
        }
        assertNull(ServerTemplateRecognition.parse(Text.literal("Server: notice"),null,"Self",self));
    }

    @Test void velocityYouerNativeChatAndRawRendererCanConfirmARewrittenUniqueEcho(){
        UUID self=UUID.randomUUID();
        ChatStore.reset();
        OutgoingMessages.clear();
        try {
            for (boolean raw : List.of(false,true)) {
                String input=raw?"two":"one";
                var body=Text.literal("RENDERED "+input).setStyle(Style.EMPTY.withBold(true));
                var name=Text.literal("Self");
                var label=Text.literal("[Proxy] Self: ").append(body);
                var wire=ChatPipeline.nativePublicLine(label,raw?label:name,body,self,"Self").withOriginalContent(input);
                var pending=new ChatMessage(Text.literal(input),true,false,null,null,self,"Self","Self",input);
                OutgoingMessages.sent(pending,null,input);
                ChatStore.get().add(pending);
                var received=new ChatMessage(label,true,false,null,null,wire.senderUuid(),wire.senderName(),wire.profileName(),wire.contentText());
                assertTrue(OutgoingMessages.reconcile(received,null,wire.evidence().confirmsIdentity(),wire.originalContent()));
                var rows=ChatStore.get().snapshot();
                var corrected=rows.get(rows.size()-1);
                assertEquals(pending.getId(),corrected.getId());
                assertEquals(body.getString(),corrected.getContentText());
            }
        } finally {
            OutgoingMessages.clear();
            ChatStore.reset();
        }
    }

    private Text actor(String display,String account,UUID uuid){
        var entity=new HoverEvent.EntityContent(net.minecraft.entity.EntityType.PLAYER,uuid,Optional.of(Text.literal(account)));
        return Text.literal(display).setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg "+account))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,entity)));
    }
}
