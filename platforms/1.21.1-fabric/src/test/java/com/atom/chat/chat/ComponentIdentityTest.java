package com.atom.chat.chat;
import net.minecraft.text.*;
import net.minecraft.entity.EntityType;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ComponentIdentityTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.createGameVersion(); net.minecraft.Bootstrap.initialize(); }
    @AfterEach void clear() { SeenPlayers.clear(); LearnedChatFormats.clear(); }
    private Text nickname(UUID id) {
        return Text.literal("BlueFox").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Remote_7 "))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,new HoverEvent.EntityContent(EntityType.PLAYER,id,java.util.Optional.of(Text.literal("Remote_7"))))));
    }
    @Test void firstUnseenOfflineRemotePlayerUsesWireUuidAndRealAccountWithDecoratedName() {
        UUID id=UUID.nameUUIDFromBytes("OfflinePlayer:Remote_7".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Text body=Text.literal("hello").setStyle(Style.EMPTY.withBold(true));
        Text line=Text.literal("[Global] [VIP] ").append(nickname(id)).append(Text.literal(": ")).append(body);
        SenderMeta meta=ComponentIdentity.detect(line);
        assertNotNull(meta);
        assertEquals(id,meta.senderUuid()); assertEquals("Remote_7",meta.profileName());
        assertTrue(meta.senderName().contains("[Global]")); assertTrue(meta.senderName().contains("BlueFox"));
        assertEquals("hello",meta.contentText());
        assertTrue(meta.contentComponent().getStyle().isBold() || meta.contentComponent().getSiblings().stream().anyMatch(c -> c.getStyle().isBold()));
        assertEquals(IdentityEvidence.ENTITY,meta.evidence());
    }
    @Test void ordinaryBodyButtonCannotImpersonateItsTarget() {
        Text line=Text.literal("Click here to contact ").append(nickname(UUID.randomUUID())).append(Text.literal(": help"));
        assertNull(ComponentIdentity.detect(line));
    }
    @Test void wholeLineClickAndTranslatedJoinCannotBecomePlayerChat() {
        var style=Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Remote_7 "));
        assertNull(ComponentIdentity.detect(Text.literal("Remote_7 joined the game").setStyle(style)));
        assertNull(ComponentIdentity.detect(Text.translatable("multiplayer.player.joined",nickname(UUID.randomUUID()))));
    }
    @Test void contradictorySenderCommandsAreAConflictAndNotAMiss() {
        Text one=Text.literal("Blue").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Alice ")));
        Text two=Text.literal("Fox").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Bob ")));
        var result=ComponentIdentity.inspect(Text.literal("").append(one).append(two).append(Text.literal(": hi")));
        assertTrue(result.conflict());assertNull(result.meta());
    }
    @Test void translatedChatUsesWireIdentityBeforeANicknameLookup() {
        UUID id=UUID.randomUUID();
        var label=nickname(id);
        SenderMeta meta=ChatPipeline.structuredPlayerLine(Text.translatable("chat.type.text",label,"hi"));
        assertEquals(id,meta.senderUuid());assertEquals("Remote_7",meta.profileName());
    }
    @Test void accountOnlySenderCannotBorrowTheUuidOfItsVisibleNickname() {
        SeenPlayers.remember(UUID.randomUUID(),"BlueFox","BlueFox");
        SeenPlayers.remember(UUID.randomUUID(),"Other","Remote_7");
        Text label=Text.literal("BlueFox").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Remote_7 ")));
        var publicMeta=ChatPipeline.structuredPlayerLine(Text.translatable("chat.type.text",label,"hello"));
        assertEquals("Remote_7",publicMeta.profileName());assertNull(publicMeta.senderUuid());
        var privateMeta=PrivateChatParser.tryParse(Text.translatable("commands.message.display.incoming",label,"hello"));
        assertEquals("Remote_7",privateMeta.profileName());assertNull(privateMeta.senderUuid());
    }
}
