package com.atom.chat.chat;
import net.minecraft.text.*;
import net.minecraft.entity.EntityType;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ComponentIdentityTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.createGameVersion(); net.minecraft.Bootstrap.initialize(); }
    @AfterEach void clear() {
        SeenPlayers.clear();
        LearnedChatFormats.clear();
        ConfirmedSkinProfiles.clear();
    }
    private Text nickname(UUID id) {
        return Text.literal("BlueFox").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Remote_7 "))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,entity(id, "Remote_7"))));
    }
    private HoverEvent.EntityContent entity(UUID id, String account) {
        return new HoverEvent.EntityContent(EntityType.PLAYER, id, Optional.of(Text.literal(account)));
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

    @Test void senderActionCanIncludeTheSeparatorInTheSameComponent() {
        var sender = Text.literal("Remote_7: ").setStyle(Style.EMPTY.withClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell Remote_7 ")));
        var body = Text.literal("hello").setStyle(Style.EMPTY.withBold(true));
        var meta = ComponentIdentity.detect(Text.literal("[Lobby] ").append(sender).append(body));

        assertNotNull(meta);
        assertEquals("[Lobby] Remote_7", meta.senderName());
        assertEquals("Remote_7", meta.profileName());
        assertNull(meta.senderUuid());
        assertEquals(IdentityEvidence.CLICK, meta.evidence());
        assertEquals("hello", meta.contentText());
        assertTrue(meta.contentComponent().getSiblings().get(0).getStyle().isBold());
    }

    @Test void leadingBracketsAndPunctuationAfterTheSeparatorBelongToTheBody() {
        var sender = Text.literal("Remote_7").setStyle(Style.EMPTY.withClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell Remote_7 ")));
        var line = Text.literal("[Lobby] ").append(sender)
                .append(Text.literal(": [Trade] - selling").setStyle(Style.EMPTY.withItalic(true)));
        var meta = ComponentIdentity.detect(line);

        assertNotNull(meta);
        assertEquals("[Lobby] Remote_7", meta.senderName());
        assertEquals("[Trade] - selling", meta.contentText());
        assertTrue(meta.contentComponent().getSiblings().get(0).getStyle().isItalic());
    }

    @Test void knownAccountAndVisibleEntityUuidCannotDisagree() {
        var known = UUID.randomUUID();
        SeenPlayers.remember(known, "Remote_7", "Remote_7");
        var other = UUID.randomUUID();
        var label = nickname(other);
        var ordinary = ComponentIdentity.inspect(Text.literal("[Lobby] ").append(label).append(Text.literal(": hi")));
        var structured = ChatPipeline.structuredPlayerLine(Text.translatable("chat.type.text", label, "hi"));

        assertTrue(ordinary.conflict());
        assertNull(ordinary.meta());
        assertTrue(structured.system());
        assertEquals(IdentityEvidence.CONFLICT, structured.evidence());
    }

    @Test void wholeLineClickCannotUseAnEmbeddedSeparatorAsSenderEvidence() {
        var line = Text.literal("Remote_7: hello: ").setStyle(Style.EMPTY.withClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell Remote_7 ")))
                .append(Text.literal("continue"));
        assertNull(ComponentIdentity.detect(line));
    }

    @Test void defaultGlobalWithOnlyDisplayMetadataHasNoColdIdentity() {
        var line = defaultGlobal("Remote_7");
        assertNull(ComponentIdentity.detect(line));
        assertNull(ChatPipeline.structuredPlayerLine(line));
        assertNull(ChatPipeline.tryParsePlayerLine(line.getString()));
    }

    @Test void defaultGlobalWithKnownAccountPreservesItsDecoratedRichMessage() {
        var id = UUID.randomUUID();
        SeenPlayers.remember(id, "Remote_7", "Remote_7");
        var line = defaultGlobal("Remote_7");
        var meta = ChatPipeline.tryParsePlayerLine(line.getString());

        assertNotNull(meta);
        assertEquals(id, meta.senderUuid());
        assertEquals("Remote_7", meta.profileName());
        var rich = ChatPipeline.sliceRichText(line, meta).orElseThrow();
        assertEquals("[Lobby] Remote_7", rich.sender().getString());
        assertEquals("[Trade] - selling", rich.content().getString());
    }

    private static Text defaultGlobal(String account) {
        // Mirrors the visible sections of Global.yml from TrChat 2.2.2 and 2.5.2.
        var server = Text.literal("[Lobby] ").setStyle(Style.EMPTY.withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Server Lobby"))));
        var body = Text.literal("[Trade] - selling").setStyle(Style.EMPTY.withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Date: 12:00:00"))));
        return Text.literal("").append(server).append(Text.literal(account + ": ")).append(body);
    }

    @Test void entityOnlySenderCanReuseAnObservedProfileByUuid() {
        var id = UUID.randomUUID();
        ConfirmedSkinProfiles.remember(id, "Remote_7", "observed-texture-property", null);
        var name = Text.literal("BlueFox").setStyle(Style.EMPTY.withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_ENTITY,
                        entity(id, "BlueFox"))));
        var line = Text.literal("[Lobby] ").append(name).append(Text.literal(": hello"));
        var meta = ComponentIdentity.detect(line);

        assertNotNull(meta);
        assertEquals(id, meta.senderUuid());
        assertEquals("Remote_7", meta.profileName());
        assertEquals("[Lobby] BlueFox", meta.senderName());
    }

    @Test void separatorClickDoesNotBecomeTheAccountOfAnEntityOnlySender() {
        var id = UUID.randomUUID();
        var sender = Text.literal("BlueFox").setStyle(Style.EMPTY.withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_ENTITY, entity(id, "BlueFox"))));
        var separator = Text.literal(": ").setStyle(Style.EMPTY.withClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell OtherName ")));
        var line = Text.literal("[Lobby] ").append(sender).append(separator).append(Text.literal("hello"));
        var meta = ComponentIdentity.detect(line);

        assertNotNull(meta);
        assertEquals(id, meta.senderUuid());
        assertNull(meta.profileName());
        assertEquals("hello", meta.contentText());
    }

    @Test void accountConfirmedForTheSameUuidSurvivesAnOlderCachedName() {
        var id = UUID.randomUUID();
        SeenPlayers.remember(id, "OldName", "OldName");
        SeenPlayers.remember(id, "NewName", "NewName");
        var sender = Text.literal("BlueFox").setStyle(Style.EMPTY
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY, entity(id, "NewName")))
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell NewName ")));
        var line = Text.literal("[Lobby] ").append(sender).append(Text.literal(": hello"));
        var publicMeta = ComponentIdentity.detect(line);
        var translated = ChatPipeline.structuredPlayerLine(Text.translatable("chat.type.text", sender, "hello"));

        assertNotNull(publicMeta);
        assertEquals(id, publicMeta.senderUuid());
        assertEquals("NewName", publicMeta.profileName());
        assertEquals(id, translated.senderUuid());
        assertEquals("NewName", translated.profileName());
    }

    @Test void currentObservedProfileWinsOverAHistoricalAccountMatch() {
        var id = UUID.randomUUID();
        SeenPlayers.remember(id, "OldName", "OldName");
        ConfirmedSkinProfiles.remember(id, "NewName", "observed-texture-property", null);
        var sender = Text.literal("BlueFox").setStyle(Style.EMPTY
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY, entity(id, "NewName")))
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell OldName ")));
        var line = Text.literal("[Lobby] ").append(sender).append(Text.literal(": hello"));
        var ordinary = ComponentIdentity.inspect(line);
        var structured = ChatPipeline.structuredPlayerLine(Text.translatable("chat.type.text", sender, "hello"));

        assertTrue(ordinary.conflict());
        assertNull(ordinary.meta());
        assertTrue(structured.system());
        assertEquals(IdentityEvidence.CONFLICT, structured.evidence());
    }
}
