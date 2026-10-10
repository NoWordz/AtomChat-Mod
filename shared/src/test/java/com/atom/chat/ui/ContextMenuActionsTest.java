package com.atom.chat.ui;

import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ContextMenuActions}, offline: the shared identity test, the two menus'
 * row semantics, the teleport command's guard and the block toggle.
 *
 * <p><b>The row semantics are asserted on what the action asks the screen for.</b>
 * A recording host captures the composer append, the private-chat entry, the
 * profile push, the command line and the player-card target, so "the @ row
 * inserts the real profile name even when the displayed name is decorated" is a
 * string assertion rather than a comment. The block toggle runs against the real
 * {@link BlockList}, and the identity test runs against a tab list handed in as
 * facts, so its uuid / case-insensitive-name rule is exercised without a game.</p>
 *
 * <p><b>Scope, stated up front.</b> NOT covered: the real command round-trip (the
 * shell's probe needs a live connection), the one tab list the shell actually
 * reads, the platform's command-send call, and {@code noteTeleportSent}'s effect
 * — it arms a private timestamp with no offline observer. Those are the D19b
 * probe list.</p>
 */
class ContextMenuActionsTest {
    @BeforeAll
    static void installTemporaryPlatform(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return root.resolve("config");
            }

            @Override
            public Path gameDir() {
                return root.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    @TempDir
    Path tmp;

    private static final UUID ALICE_ID = new UUID(0x11L, 0x22L);
    private static final PlayerRef ALICE = PlayerRef.of(ALICE_ID, "Alice");
    private static final PlayerRef BOB = PlayerRef.of(new UUID(0x33L, 0x44L), "Bob");

    /** What the actions asked of the screen, in order. */
    private static final class Recorder implements ContextMenuActions.Host {
        final List<String> calls = new ArrayList<>();
        List<ContextMenuActions.OnlinePlayer> online = List.of();
        boolean localPlayer = true;
        String command = "/tp";
        PlayerRef cardTarget;

        @Override
        public List<ContextMenuActions.OnlinePlayer> onlinePlayers() {
            return online;
        }

        @Override
        public boolean hasLocalPlayer() {
            return localPlayer;
        }

        @Override
        public String teleportCommand() {
            calls.add("probe");
            return command;
        }

        @Override
        public void sendCommand(String command) {
            calls.add("send:" + command);
        }

        @Override
        public void appendToComposer(String text) {
            calls.add("append:" + text);
        }

        @Override
        public void privateChatWith(PlayerRef player) {
            calls.add("whisper:" + player.realName());
        }

        @Override
        public void profileDetailFor(PlayerRef player) {
            calls.add("profile:" + player.realName());
        }

        @Override
        public PlayerRef currentOrLastPlayer() {
            return cardTarget;
        }
    }

    private Recorder host;
    private ContextMenuActions actions;

    private void setUp() {
        host = new Recorder();
        actions = new ContextMenuActions(host);
    }

    /** A message from another player: real name "Alice", displayed as a VIP tag. */
    private static ContextMenuActions.MessageFacts from(UUID sender, String realName, String display) {
        return ContextMenuActions.MessageFacts.of(sender, realName, display, false, false);
    }

    private static ContextMenuActions.OnlinePlayer entry(UUID uuid, String name) {
        return ContextMenuActions.OnlinePlayer.of(uuid, name);
    }

    // ------------------------------------------------------- 1. the identity test

    @Test
    void theIdentityTestMatchesByUuidThenByNameCaseInsensitively() {
        setUp();
        host.online = List.of(entry(ALICE_ID, "Alice"));

        assertTrue(actions.isOnlinePlayer(ALICE), "the uuid matches");
        assertTrue(actions.isOnlinePlayer(PlayerRef.of(ALICE_ID, "Someone Else")),
                "the uuid wins even when the name drifted");
        assertTrue(actions.isOnlinePlayer(PlayerRef.of(null, "aLiCe")),
                "a name-only reference matches case-insensitively");
        assertTrue(actions.isOnlinePlayer(PlayerRef.of(new UUID(0x99L, 0x99L), "ALICE")),
                "a stale uuid still matches by name");
        assertFalse(actions.isOnlinePlayer(BOB), "nobody else is online");
        assertFalse(actions.isOnlinePlayer(PlayerRef.of(null, "Alice Smith")),
                "the match is the whole name, not a prefix");
        assertFalse(actions.isOnlinePlayer(null), "and null is never online");
    }

    @Test
    void anEmptyTabListMeansNobodyIsOnline() {
        setUp();
        assertFalse(actions.isOnlinePlayer(ALICE));
        assertFalse(actions.isOnlinePlayer(PlayerRef.of(null, "Alice")));
    }

    @Test
    void anIdentitylessReferenceNeverMatches() {
        setUp();
        host.online = List.of(entry(ALICE_ID, "Alice"));
        assertFalse(actions.isOnlinePlayer(PlayerRef.of(null, null)));
    }

    // ------------------------------------------------------ 2. canOpenAvatarMenu

    @Test
    void theAvatarMenuNeedsAnotherPlayerWithAnIdentity() {
        assertTrue(ContextMenuActions.canOpenAvatarMenu(from(ALICE_ID, "Alice", "Alice")));
        assertTrue(ContextMenuActions.canOpenAvatarMenu(
                        ContextMenuActions.MessageFacts.of(ALICE_ID, null, "[VIP] Alice", false, false)),
                "a display name alone is enough");
        assertTrue(ContextMenuActions.canOpenAvatarMenu(
                        ContextMenuActions.MessageFacts.of(ALICE_ID, "Alice", null, false, false)),
                "and so is a captured profile name alone");

        assertFalse(ContextMenuActions.canOpenAvatarMenu(null), "no row, no menu");
        assertFalse(ContextMenuActions.canOpenAvatarMenu(
                        ContextMenuActions.MessageFacts.of(ALICE_ID, "Alice", "Alice", false, true)),
                "a server line has no player to act on");
        assertFalse(ContextMenuActions.canOpenAvatarMenu(
                        ContextMenuActions.MessageFacts.of(ALICE_ID, "Me", "Me", true, false)),
                "nor does your own message — the avatar menu is for somebody else");
        assertFalse(ContextMenuActions.canOpenAvatarMenu(
                        ContextMenuActions.MessageFacts.of(null, null, null, false, false)),
                "no identity at all");
    }

    // ------------------------------------------------ 3. the avatar menu's rows

    @Test
    void theMentionRowInsertsTheRealProfileNameNotTheDecoratedLabel() {
        setUp();
        // The row's displayed name carries a rank tag; commands and the @ token
        // must use the real profile name or mention detection never fires.
        actions.performAvatarMenuAction(0, from(ALICE_ID, "Alice", "[VIP] Alice"));
        assertEquals(List.of("append:@Alice "), host.calls);
    }

    @Test
    void theMentionRowDoesNothingWithoutARealName() {
        setUp();
        actions.performAvatarMenuAction(0, from(ALICE_ID, null, null));
        assertEquals(List.of(), host.calls, "no real name means no @ token");
    }

    @Test
    void theWhisperRowOpensThePrivateChat() {
        setUp();
        actions.performAvatarMenuAction(1, from(ALICE_ID, "Alice", "Alice"));
        assertEquals(List.of("whisper:Alice"), host.calls);
    }

    @Test
    void theTeleportRowOnlyFiresForAnOnlinePlayer() {
        setUp();
        host.online = List.of();
        actions.performAvatarMenuAction(2, from(ALICE_ID, "Alice", "Alice"));
        assertEquals(List.of(), host.calls, "an offline player gets no teleport");

        setUp();
        host.online = List.of(entry(ALICE_ID, "Alice"));
        actions.performAvatarMenuAction(2, from(ALICE_ID, "Alice", "Alice"));
        assertEquals(List.of("probe", "send:tp Alice"), host.calls,
                "the leading slash is dropped: the command goes out as a chat command");
    }

    @Test
    void theBlockRowTogglesTheRealBlockList() {
        setUp();
        try {
            actions.performAvatarMenuAction(3, from(ALICE_ID, "Alice", "Alice"));
            assertTrue(BlockList.isBlocked(ALICE), "the row blocks");
            actions.performAvatarMenuAction(3, from(ALICE_ID, "Alice", "Alice"));
            assertFalse(BlockList.isBlocked(ALICE), "and the same row unblocks");
        } finally {
            BlockList.setBlocked(ALICE, false);
            AtomChatConfig.get().blockedPlayers = new ArrayList<>();
        }
    }

    @Test
    void anUnknownAvatarRowDoesNothing() {
        setUp();
        actions.performAvatarMenuAction(9, from(ALICE_ID, "Alice", "Alice"));
        assertEquals(List.of(), host.calls);
    }

    // -------------------------------------------- 4. the player card's three rows

    @Test
    void thePlayerCardOpensTheProfileTeleportsAndBlocks() {
        setUp();
        host.cardTarget = ALICE;
        actions.performPlayerCardAction(0);
        assertEquals(List.of("profile:Alice"), host.calls);

        setUp();
        host.cardTarget = ALICE;
        host.online = List.of(entry(ALICE_ID, "Alice"));
        actions.performPlayerCardAction(1);
        assertEquals(List.of("probe", "send:tp Alice"), host.calls);

        setUp();
        host.cardTarget = ALICE;
        try {
            actions.performPlayerCardAction(2);
            assertTrue(BlockList.isBlocked(ALICE));
        } finally {
            BlockList.setBlocked(ALICE, false);
            AtomChatConfig.get().blockedPlayers = new ArrayList<>();
        }
    }

    @Test
    void thePlayerCardTeleportIsOnlineOnlyAndNeedsATarget() {
        setUp();
        host.cardTarget = ALICE;
        host.online = List.of();
        actions.performPlayerCardAction(1);
        assertEquals(List.of(), host.calls, "offline: no teleport");

        setUp();
        host.cardTarget = null;
        host.online = List.of(entry(ALICE_ID, "Alice"));
        actions.performPlayerCardAction(1);
        assertEquals(List.of(), host.calls, "a closing menu with no target does nothing");
    }

    // ------------------------------------------------------- 5. the teleport command

    @Test
    void theTeleportCommandStripsTheSlashAndNamesTheTarget() {
        setUp();
        host.command = "/minecraft:tp";
        actions.sendTeleportCommand(PlayerRef.of(ALICE_ID, "Alice"));
        assertEquals(List.of("probe", "send:minecraft:tp Alice"), host.calls);
    }

    @Test
    void theTeleportCommandNeedsALocalPlayerAndATarget() {
        setUp();
        host.localPlayer = false;
        actions.sendTeleportCommand(ALICE);
        assertEquals(List.of(), host.calls, "no local player: nothing can be sent");

        setUp();
        actions.sendTeleportCommand(null);
        assertEquals(List.of(), host.calls);

        setUp();
        actions.sendTeleportCommand(PlayerRef.of(ALICE_ID, null));
        assertEquals(List.of(), host.calls, "a target with no real name cannot be named");
    }

    // -------------------------------------------------------------- 6. block toggle

    @Test
    void theBlockToggleFlipsAndRestores() {
        setUp();
        try {
            assertFalse(BlockList.isBlocked(BOB));
            actions.toggleBlock(BOB);
            assertTrue(BlockList.isBlocked(BOB));
            actions.toggleBlock(BOB);
            assertFalse(BlockList.isBlocked(BOB));
            actions.toggleBlock(null);
            assertFalse(BlockList.isBlocked(BOB), "a null target changes nothing");
        } finally {
            BlockList.setBlocked(BOB, false);
            AtomChatConfig.get().blockedPlayers = new ArrayList<>();
        }
    }
}
