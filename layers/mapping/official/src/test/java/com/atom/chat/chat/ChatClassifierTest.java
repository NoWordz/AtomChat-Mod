package com.atom.chat.chat;

import com.atom.chat.chat.ChatClassifier.Route;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatClassifierTest {
    @Test
    void wrappedTeamNotificationsRemainSystemWhileRealTeamChatRemainsPlayer() {
        Component notice = Component.literal("<Alice @Friends> ")
                .append(Component.translatable("ftbteams.message.joined", "Bob"));
        assertEquals(Route.SYSTEM, ChatClassifier.classifyByKey(notice));
        assertEquals(Route.SYSTEM, ChatClassifier.classifyByKey(Component.literal("")
                .append(Component.translatable("multiplayer.player.joined", "Alice"))));
        assertEquals(Route.PLAYER, ChatClassifier.classifyByKey(Component.translatable("chat.type.text",
                "Alice", Component.translatable("ftbteams.message.joined", "Bob"))));
        assertEquals(Route.UNKNOWN, ChatClassifier.classifyByKey(Component.literal("<Alice @Friends> hello")));
    }
    @Test
    void classifiesVanillaPlayerAndSystemKeys() {
        assertEquals(Route.PLAYER, ChatClassifier.classifyByKey(Component.translatable("chat.type.text", "Alice", "hi")));
        assertEquals(Route.PLAYER, ChatClassifier.classifyByKey(Component.translatable("chat.type.team.text", "Alice", "hi")));
        assertEquals(Route.SYSTEM, ChatClassifier.classifyByKey(Component.translatable("multiplayer.player.joined", "Alice")));
        assertEquals(Route.PRIVATE, ChatClassifier.classifyByKey(Component.translatable("commands.message.display.incoming", "Alice", "hi")));
        assertEquals(Route.UNKNOWN, ChatClassifier.classifyByKey(Component.literal("plain")));
    }

    @Test
    void classifiesTeamSentAsPlayer() {
        assertEquals(Route.PLAYER, ChatClassifier.classifyByKey(Component.translatable("chat.type.team.sent", "Team", "Alice", "hi")));
    }

    @Test
    void classifiesBothPrivateMessageDirections() {
        assertEquals(Route.PRIVATE, ChatClassifier.classifyByKey(Component.translatable("commands.message.display.incoming", "Alice", "hi")));
        assertEquals(Route.PRIVATE, ChatClassifier.classifyByKey(Component.translatable("commands.message.display.outgoing", "Alice", "hi")));
    }

    @Test
    void isVanillaBroadcastRemainsTrueForSystemRoutes() {
        assertTrue(ChatClassifier.isVanillaBroadcast(Component.translatable("multiplayer.player.joined", "Alice")));
        assertTrue(ChatClassifier.isVanillaBroadcast(Component.translatable("commands.ban.success", "Alice")));
        assertTrue(ChatClassifier.isVanillaBroadcast(Component.translatable("chat.type.emote", "Alice", "waves")));
        assertTrue(ChatClassifier.isVanillaBroadcast(Component.translatable("death.attack.player", "Alice", "Bob")));
    }

    @Test
    void recognizesXaeroWaypointDataAsSystemProtocol() {
        assertTrue(ChatClassifier.isXaeroWaypointData("xaero_waypoint_add:abc"));
        assertTrue(ChatClassifier.isXaeroWaypointData("xaero-waypoint:abc"));
        assertTrue(ChatClassifier.isXaeroWaypointData("xaero_waypoint:abc"));
        assertTrue(ChatClassifier.isXaeroWaypointData("<Steve> xaero_waypoint_add:abc"));
        assertFalse(ChatClassifier.isXaeroWaypointData("<Steve> hello world"));
        assertFalse(ChatClassifier.isXaeroWaypointData(null));
    }

    @Test
    void isVanillaBroadcastDoesNotFlagPlayerTeamOrPrivateRoutes() {
        assertFalse(ChatClassifier.isVanillaBroadcast(Component.translatable("chat.type.text", "Alice", "hi")));
        assertFalse(ChatClassifier.isVanillaBroadcast(Component.translatable("chat.type.team.text", "Alice", "hi")));
        assertFalse(ChatClassifier.isVanillaBroadcast(Component.translatable("chat.type.team.sent", "Team", "Alice", "hi")));
        assertFalse(ChatClassifier.isVanillaBroadcast(Component.translatable("commands.message.display.incoming", "Alice", "hi")));
        assertFalse(ChatClassifier.isVanillaBroadcast(Component.translatable("commands.message.display.outgoing", "Alice", "hi")));
        assertFalse(ChatClassifier.isVanillaBroadcast(Component.literal("plain")));
    }
}
