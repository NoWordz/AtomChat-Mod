package com.atom.chat.chat;
import net.minecraft.network.chat.*;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ComponentIdentityTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @AfterEach void clear() {
        SeenPlayers.clear();
        LearnedChatFormats.clear();
        ConfirmedSkinProfiles.clear();
    }
    private HoverEvent.EntityTooltipInfo entity(UUID id, String account) {
        for (var constructor : HoverEvent.EntityTooltipInfo.class.getConstructors()) {
            if (constructor.getParameterCount() != 3) continue;
            Object name = constructor.getParameterTypes()[2] == Optional.class
                    ? Optional.of(Component.literal(account)) : Component.literal(account);
            try { return (HoverEvent.EntityTooltipInfo) constructor.newInstance(EntityType.PLAYER, id, name); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
        throw new AssertionError("Missing entity tooltip constructor");
    }
    private Component nickname(UUID id) {
        return Component.literal("BlueFox").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Remote_7 "))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,entity(id, "Remote_7"))));
    }
    @Test void firstUnseenOfflineRemotePlayerUsesWireUuidAndRealAccountWithDecoratedName() {
        UUID id=UUID.nameUUIDFromBytes("OfflinePlayer:Remote_7".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Component body=Component.literal("hello").setStyle(Style.EMPTY.withBold(true));
        Component line=Component.literal("[Global] [VIP] ").append(nickname(id)).append(Component.literal(": ")).append(body);
        SenderMeta meta=ComponentIdentity.detect(line);
        assertNotNull(meta);
        assertEquals(id,meta.senderUuid()); assertEquals("Remote_7",meta.profileName());
        assertTrue(meta.senderName().contains("[Global]")); assertTrue(meta.senderName().contains("BlueFox"));
        assertEquals("hello",meta.contentText());
        assertTrue(meta.contentComponent().getStyle().isBold() || meta.contentComponent().getSiblings().stream().anyMatch(c -> c.getStyle().isBold()));
        assertEquals(IdentityEvidence.ENTITY,meta.evidence());
    }
    @Test void ordinaryBodyButtonCannotImpersonateItsTarget() {
        Component line=Component.literal("Click here to contact ").append(nickname(UUID.randomUUID())).append(Component.literal(": help"));
        assertNull(ComponentIdentity.detect(line));
    }
    @Test void wholeLineClickAndTranslatedJoinCannotBecomePlayerChat() {
        var style=Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Remote_7 "));
        assertNull(ComponentIdentity.detect(Component.literal("Remote_7 joined the game").setStyle(style)));
        assertNull(ComponentIdentity.detect(Component.translatable("multiplayer.player.joined",nickname(UUID.randomUUID()))));
    }
    @Test void contradictorySenderCommandsAreAConflictAndNotAMiss() {
        Component one=Component.literal("Blue").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Alice ")));
        Component two=Component.literal("Fox").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Bob ")));
        var result=ComponentIdentity.inspect(Component.literal("").append(one).append(two).append(Component.literal(": hi")));
        assertTrue(result.conflict());assertNull(result.meta());
    }
    @Test void translatedChatUsesWireIdentityBeforeANicknameLookup() {
        UUID id=UUID.randomUUID();
        var label=nickname(id);
        SenderMeta meta=ChatPipeline.structuredPlayerLine(Component.translatable("chat.type.text",label,"hi"));
        assertEquals(id,meta.senderUuid());assertEquals("Remote_7",meta.profileName());
    }
    @Test void accountOnlySenderCannotBorrowTheUuidOfItsVisibleNickname() {
        SeenPlayers.remember(UUID.randomUUID(),"BlueFox","BlueFox");
        SeenPlayers.remember(UUID.randomUUID(),"Other","Remote_7");
        Component label=Component.literal("BlueFox").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/msg Remote_7 ")));
        var publicMeta=ChatPipeline.structuredPlayerLine(Component.translatable("chat.type.text",label,"hello"));
        assertEquals("Remote_7",publicMeta.profileName());assertNull(publicMeta.senderUuid());
        var privateMeta=PrivateChatParser.tryParse(Component.translatable("commands.message.display.incoming",label,"hello"));
        assertEquals("Remote_7",privateMeta.profileName());assertNull(privateMeta.senderUuid());
    }

    @Test void senderActionCanIncludeTheSeparatorInTheSameComponent() {
        var sender = Component.literal("Remote_7: ").setStyle(Style.EMPTY.withClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell Remote_7 ")));
        var body = Component.literal("hello").setStyle(Style.EMPTY.withBold(true));
        var meta = ComponentIdentity.detect(Component.literal("[Lobby] ").append(sender).append(body));

        assertNotNull(meta);
        assertEquals("[Lobby] Remote_7", meta.senderName());
        assertEquals("Remote_7", meta.profileName());
        assertNull(meta.senderUuid());
        assertEquals(IdentityEvidence.CLICK, meta.evidence());
        assertEquals("hello", meta.contentText());
        assertTrue(meta.contentComponent().getSiblings().get(0).getStyle().isBold());
    }

    @Test void leadingBracketsAndPunctuationAfterTheSeparatorBelongToTheBody() {
        var sender = Component.literal("Remote_7").setStyle(Style.EMPTY.withClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell Remote_7 ")));
        var line = Component.literal("[Lobby] ").append(sender)
                .append(Component.literal(": [Trade] - selling").setStyle(Style.EMPTY.withItalic(true)));
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
        var ordinary = ComponentIdentity.inspect(Component.literal("[Lobby] ").append(label).append(Component.literal(": hi")));
        var structured = ChatPipeline.structuredPlayerLine(Component.translatable("chat.type.text", label, "hi"));

        assertTrue(ordinary.conflict());
        assertNull(ordinary.meta());
        assertTrue(structured.system());
        assertEquals(IdentityEvidence.CONFLICT, structured.evidence());
    }

    @Test void wholeLineClickCannotUseAnEmbeddedSeparatorAsSenderEvidence() {
        var line = Component.literal("Remote_7: hello: ").setStyle(Style.EMPTY.withClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell Remote_7 ")))
                .append(Component.literal("continue"));
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

    private static Component defaultGlobal(String account) {
        // Mirrors the visible sections of Global.yml from TrChat 2.2.2 and 2.5.2.
        var server = Component.literal("[Lobby] ").setStyle(Style.EMPTY.withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Server Lobby"))));
        var body = Component.literal("[Trade] - selling").setStyle(Style.EMPTY.withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Date: 12:00:00"))));
        return Component.literal("").append(server).append(Component.literal(account + ": ")).append(body);
    }

    @Test void entityOnlySenderCanReuseAnObservedProfileByUuid() {
        var id = UUID.randomUUID();
        ConfirmedSkinProfiles.remember(id, "Remote_7", "observed-texture-property", null);
        var name = Component.literal("BlueFox").setStyle(Style.EMPTY.withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_ENTITY,
                        entity(id, "BlueFox"))));
        var line = Component.literal("[Lobby] ").append(name).append(Component.literal(": hello"));
        var meta = ComponentIdentity.detect(line);

        assertNotNull(meta);
        assertEquals(id, meta.senderUuid());
        assertEquals("Remote_7", meta.profileName());
        assertEquals("[Lobby] BlueFox", meta.senderName());
    }

    @Test void separatorClickDoesNotBecomeTheAccountOfAnEntityOnlySender() {
        var id = UUID.randomUUID();
        var sender = Component.literal("BlueFox").setStyle(Style.EMPTY.withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_ENTITY, entity(id, "BlueFox"))));
        var separator = Component.literal(": ").setStyle(Style.EMPTY.withClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell OtherName ")));
        var line = Component.literal("[Lobby] ").append(sender).append(separator).append(Component.literal("hello"));
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
        var sender = Component.literal("BlueFox").setStyle(Style.EMPTY
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY, entity(id, "NewName")))
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell NewName ")));
        var line = Component.literal("[Lobby] ").append(sender).append(Component.literal(": hello"));
        var publicMeta = ComponentIdentity.detect(line);
        var translated = ChatPipeline.structuredPlayerLine(Component.translatable("chat.type.text", sender, "hello"));

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
        var sender = Component.literal("BlueFox").setStyle(Style.EMPTY
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY, entity(id, "NewName")))
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell OldName ")));
        var line = Component.literal("[Lobby] ").append(sender).append(Component.literal(": hello"));
        var ordinary = ComponentIdentity.inspect(line);
        var structured = ChatPipeline.structuredPlayerLine(Component.translatable("chat.type.text", sender, "hello"));

        assertTrue(ordinary.conflict());
        assertNull(ordinary.meta());
        assertTrue(structured.system());
        assertEquals(IdentityEvidence.CONFLICT, structured.evidence());
    }
}
