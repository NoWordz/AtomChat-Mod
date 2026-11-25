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
        assertNotNull(meta);assertNull(meta.senderUuid());assertNull(meta.profileName());
        assertTrue(meta.senderComponent().getString().contains("[Global]"));assertEquals("hello",meta.contentComponent().getString());
        assertTrue(meta.contentComponent().getStyle().isBold()||meta.contentComponent().getSiblings().stream().anyMatch(c->c.getStyle().isBold()));
    }
    @Test void wireIdentityConflictIsTerminalAndAmbiguityIsNotClaimed(){
        install(MessageTemplate.Kind.PUBLIC,"{sender}: {message}");
        var known=new SenderMeta(UUID.randomUUID(),"Other","Other",null,false);
        var meta=ServerTemplateRecognition.parse(Component.literal("Remote: hi"),known,null,null);assertTrue(meta.system());assertEquals(IdentityEvidence.CONFLICT,meta.evidence());
        install(MessageTemplate.Kind.PUBLIC,"{sender}{message}");assertTrue(ServerTemplateRecognition.parse(Component.literal("Remotehello"),null,null,null).system());
    }
    @Test void corroboratingOnlyTheRecipientCannotConfirmAnOutgoingTemplate(){
        install(MessageTemplate.Kind.OUTGOING,"You -> {target}: {message}");UUID own=UUID.randomUUID(),remote=UUID.randomUUID();
        var recipient=new SenderMeta(remote,"Remote","Remote",null,false).withEvidence(IdentityEvidence.ENTITY);
        var meta=ServerTemplateRecognition.parse(Component.literal("You -> Remote: hi"),recipient,"Me",own);
        assertFalse(meta.whisper());assertNull(meta.senderUuid());assertNull(meta.profileName());
        assertNull(SeenPlayers.findAccountUuid("Remote"));
    }
    @Test void bareCrossServerNameDoesNotBorrowALocallySeenIdentity(){
        install(MessageTemplate.Kind.PUBLIC,"[{channel}] {sender}: {message}");
        SeenPlayers.remember(UUID.randomUUID(),"Remote","Remote");
        var parsed=ServerTemplateRecognition.parse(Component.literal("[OtherServer] Remote: hi"),null,null,null);
        assertNull(parsed.senderUuid());assertNull(parsed.profileName());
    }

    @Test void configuredPublicAndIncomingSourcesCannotDisagreeOnTheSameLine(){
        install(MessageTemplate.Kind.PUBLIC,"[Hub] {sender}: {message}");
        var line=Component.literal("[Hub] Remote: hello");
        var parsed=ServerTemplateRecognition.parse(line,null,"Self",UUID.randomUUID(),
                List.of(),List.of("[Hub] {name}: {content}"),List.of("Remote"));
        assertTrue(parsed.system());
        assertEquals(IdentityEvidence.CONFLICT,parsed.evidence());
    }

    @Test void velocityYouerRawSystemComponentKeepsLiteralBodyAndItsInteractions(){
        var click=new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Remote");
        var line=Component.literal("§6[Hub] 云狐: §r").append(Component.literal(" 云狐\n😀 §q  ")
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
        var line=Component.literal("["+claimed+"] Remote: hello");
        assertEquals(IdentityEvidence.UNKNOWN_TEMPLATE,ServerTemplateRecognition.parse(line,null,null,null).evidence());
        var corroboration=new SenderMeta(claimed,"Remote","Remote",null,false).withEvidence(IdentityEvidence.NATIVE);
        assertEquals(claimed,ServerTemplateRecognition.parse(line,corroboration,null,null).senderUuid());
        var other=new SenderMeta(UUID.randomUUID(),"Remote","Remote",null,false).withEvidence(IdentityEvidence.ENTITY);
        assertTrue(ServerTemplateRecognition.parse(line,other,null,null).system());
    }

    @Test void senderSlotCanVerifyAnEntityButALineWideHoverCannot(){
        install(MessageTemplate.Kind.PUBLIC,"[Hub] {sender}: {message}");
        UUID remote=UUID.randomUUID();
        var entity=playerEntity(remote,"Remote");
        var hover=new HoverEvent(HoverEvent.Action.SHOW_ENTITY,entity);
        var style=Style.EMPTY.withHoverEvent(hover).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Remote"));
        var body=Component.literal(" hello  ");
        var verified=Component.literal("[Hub] ").append(Component.literal("Remote").setStyle(style)).append(": ").append(body);
        assertEquals(remote,ServerTemplateRecognition.parse(verified,null,null,null).senderUuid());
        var unverified=Component.literal("[Hub] Remote:  hello  ").setStyle(style);
        var parsed=ServerTemplateRecognition.parse(unverified,ComponentIdentity.detect(unverified),null,null);
        assertEquals(IdentityEvidence.UNKNOWN_TEMPLATE,parsed.evidence());
        assertEquals(body.getString(),parsed.contentText());
        var nested=Component.literal("").append(Component.literal("[Hub] Remote: ").setStyle(style).append(
                Component.literal("First").setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal("Other"))))
                        .append(" rest")));
        assertEquals(IdentityEvidence.UNKNOWN_TEMPLATE,ServerTemplateRecognition.parse(nested,null,null,null).evidence());
        var argument=Component.literal("[Hub] Remote: body").setStyle(style);
        var translated=Component.translatableWithFallback("atomchat.tests.unknown_envelope","%s",argument);
        assertEquals(IdentityEvidence.UNKNOWN_TEMPLATE,ServerTemplateRecognition.parse(translated,null,null,null).evidence());
    }

    @Test void incomingTemplateNeedsMatchingDirectionAndLocalParticipation(){
        install(MessageTemplate.Kind.INCOMING,"[{sender} -> {target}] {message}");
        UUID self=UUID.randomUUID(), remote=UUID.randomUUID();
        var verified=Component.literal("[").append(actor("Remote","Remote",remote)).append(" -> Self] hello");
        assertTrue(ServerTemplateRecognition.parse(verified,null,"Self",self).whisper());
        var wrongDirection=Component.literal("[").append(actor("Self","Self",self)).append(" -> Self] hello");
        assertTrue(ServerTemplateRecognition.parse(wrongDirection,null,"Self",self).system());
        install(MessageTemplate.Kind.INCOMING,"{sender}: {message}");
        var noConversation=Component.literal("").append(actor("Remote","Remote",remote)).append(": hello");
        assertEquals(IdentityEvidence.UNKNOWN_TEMPLATE,ServerTemplateRecognition.parse(noConversation,null,"Self",self).evidence());
        install(MessageTemplate.Kind.INCOMING,"{sender} whispers: {message}");
        var unspecifiedRecipient=Component.literal("").append(actor("Remote","Remote",remote)).append(" whispers: hello");
        assertEquals(IdentityEvidence.UNKNOWN_TEMPLATE,ServerTemplateRecognition.parse(unspecifiedRecipient,null,"Self",self).evidence());
        install(MessageTemplate.Kind.INCOMING,"{sender} whispers to you: {message}");
        var addressed=Component.literal("").append(actor("Remote","Remote",remote)).append(" whispers to you: hello");
        assertTrue(ServerTemplateRecognition.parse(addressed,null,"Self",self).whisper());
    }

    @Test void configuredOutgoingWithBothActorSlotsCanCorrelateAUniqueSend(){
        install(MessageTemplate.Kind.OUTGOING,"{sender} -> {target}: {message}");
        UUID own=UUID.randomUUID(), peer=UUID.randomUUID();
        var line=Component.literal("").append(actor("Self","Self",own)).append(" -> ").append(actor("Remote","Remote",peer)).append(": hello");
        var parsed=ServerTemplateRecognition.parse(line,null,"Self",own);
        assertTrue(parsed.whisper());
        assertEquals(own,parsed.senderUuid());
        assertEquals(peer,parsed.whisperPartnerUuid());
        assertFalse(WhisperTextParser.isThirdPartyEnvelope(line.getString(),"Self"));
    }

    @Test void vanillaPrivateReceiptUsesTheReceiverSlotInsteadOfASameNamedAlias(){
        UUID self=UUID.randomUUID(), receiver=UUID.randomUUID();
        SeenPlayers.remember(UUID.randomUUID(),"Remote","Remote");
        var line=Component.translatable("commands.message.display.outgoing",actor("Remote","Actual",receiver),"hello");
        var parsed=PrivateChatParser.tryParse(line,"Self",self);
        assertEquals("Actual",parsed.whisperPartner());
        assertEquals(receiver,parsed.whisperPartnerUuid());
        var bare=Component.translatable("commands.message.display.outgoing",Component.literal("Remote"),"hello");
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
            var meta=ServerTemplateRecognition.parse(Component.literal(value),null,"Self",self);
            assertEquals(IdentityEvidence.UNKNOWN_TEMPLATE,meta.evidence(),value);
            assertEquals("same",meta.contentText());
            assertFalse(meta.whisper());
            assertNull(meta.senderUuid());
        }
        assertNull(ServerTemplateRecognition.parse(Component.literal("Server: notice"),null,"Self",self));
    }

    @Test void velocityYouerNativeChatAndRawRendererCanConfirmARewrittenUniqueEcho(){
        UUID self=UUID.randomUUID();
        ChatStore.reset();
        OutgoingMessages.clear();
        try {
            for (boolean raw : List.of(false,true)) {
                String input=raw?"two":"one";
                var body=Component.literal("RENDERED "+input).setStyle(Style.EMPTY.withBold(true));
                var name=Component.literal("Self");
                var label=Component.literal("[Proxy] Self: ").append(body);
                var wire=ChatPipeline.nativePublicLine(label,raw?label:name,body,self,"Self").withOriginalContent(input);
                var pending=new ChatMessage(Component.literal(input),true,false,null,null,self,"Self","Self",input);
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

    private Component actor(String display,String account,UUID uuid){
        var entity=playerEntity(uuid,account);
        return Component.literal(display).setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg "+account))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,entity)));
    }

    private static HoverEvent.EntityTooltipInfo playerEntity(UUID uuid,String account) {
        for (var constructor : HoverEvent.EntityTooltipInfo.class.getConstructors()) {
            if (constructor.getParameterCount() != 3) continue;
            Object name = constructor.getParameterTypes()[2] == Optional.class
                    ? Optional.of(Component.literal(account)) : Component.literal(account);
            try {
                return (HoverEvent.EntityTooltipInfo) constructor.newInstance(net.minecraft.world.entity.EntityType.PLAYER, uuid, name);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        }
        throw new AssertionError("Missing entity tooltip constructor");
    }

    @Test void trChatSeparateEnvelopeDelimiterPreservesTheColonInTheBody() {
        String body = "YG-04 test [brackets]: text";
        String format = "[{channel}] {display}: {message}";
        var message = net.minecraft.network.chat.Component.empty().append(net.minecraft.network.chat.Component.literal("[world] "))
                .append(net.minecraft.network.chat.Component.literal("LabGame")).append(net.minecraft.network.chat.Component.literal(": ")).append(net.minecraft.network.chat.Component.literal(body));
        var parsed = ServerTemplateRecognition.parse(message, null, "Other", java.util.UUID.randomUUID(),
                java.util.List.of(format), java.util.List.of(), java.util.List.of());
        assertNotNull(parsed);
        assertFalse(parsed.system());
        assertEquals(IdentityEvidence.UNKNOWN_TEMPLATE, parsed.evidence());
        assertNull(parsed.senderUuid());
        assertEquals(body, parsed.contentComponent().getString());

        var flat = net.minecraft.network.chat.Component.literal(message.getString());
        var ambiguous = ServerTemplateRecognition.parse(flat, null, "Other", java.util.UUID.randomUUID(),
                java.util.List.of(format), java.util.List.of(), java.util.List.of());
        assertNotNull(ambiguous);
        assertTrue(ambiguous.system(), "a flat message still has no separator evidence");

        var ambiguousComponents = net.minecraft.network.chat.Component.empty().append(net.minecraft.network.chat.Component.literal("[world] LabGame"))
                .append(net.minecraft.network.chat.Component.literal(": ")).append(net.minecraft.network.chat.Component.literal("first"))
                .append(net.minecraft.network.chat.Component.literal(": ")).append(net.minecraft.network.chat.Component.literal("second"));
        var twoSeparators = ServerTemplateRecognition.parse(ambiguousComponents, null, "Other", java.util.UUID.randomUUID(),
                java.util.List.of(format), java.util.List.of(), java.util.List.of());
        assertNotNull(twoSeparators);
        assertTrue(twoSeparators.system(), "two equally valid isolated delimiters must remain ambiguous");
    }
}
