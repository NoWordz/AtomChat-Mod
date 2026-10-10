package com.atom.chat.ui;

import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.chat.TeleportCommands;

import java.util.List;
import java.util.UUID;

/**
 * What a context-menu row does (D19b): the avatar menu's four actions, the
 * player card's three, the teleport command behind them and the identity test
 * three domains share.
 *
 * <p><b>The row semantics are the contract, and they are the originals.</b> The
 * avatar menu is @ mention / whisper / teleport / block-unblock, the player card
 * is profile / teleport / block-unblock. The @ mention inserts the row's
 * <em>real profile name</em>, never the decorated label: the label reaches other
 * clients as uncoloured plain text (a title colour cannot survive a vanilla chat
 * string) and a decorated {@code @token} also defeats mention detection, which
 * looks for {@code @ProfileName}. Teleport is offered only for a player who is
 * actually on the tab list, in both menus, and block-unblock always goes through
 * the real {@link BlockList}.</p>
 *
 * <p><b>{@link #isOnlinePlayer} is the shared identity test</b> (the header's
 * online dot and the read-only private page ask the same question). It matches by
 * uuid when one is known and otherwise by name, case-insensitively — that
 * asymmetry is the point: outgoing {@code /msg} echoes and history rows may only
 * carry a name. The tab list itself is a {@link Host} fact, so the matching
 * (which is the part with a rule in it) is testable without a running game, and
 * this class names no Minecraft API — see the ports note below.</p>
 *
 * <p>Ports on {@link Host}: the tab list as neutral facts, whether a local player
 * exists, the teleport command the shell probes for (D19's command-tree probe
 * needs the client object, so it stays where the client lives) and the send
 * itself, D21's composer append for the @ row, D24's private-chat entry, D18's
 * profile push, and the player-card menu's current target. {@link MessageFacts}
 * is this domain's neutral view of a row (the same reason D18b has one: the chat
 * component is spelled {@code Component} on the official builds and {@code Text}
 * on the yarn one).</p>
 *
 * <p>One copy, in the shared layer: with the tab list, the command probe and the
 * send all behind ports, this class names no Minecraft API, and the types it
 * drives ({@link BlockList}, {@link PlayerRef}, {@link TeleportCommands}) exist
 * at this same name on every target. A twin pair here would be byte-identical,
 * which {@code verify_targets} rejects.</p>
 */
public final class ContextMenuActions {
    /** One player the tab list reports: the identity test's input, as facts. */
    public interface OnlinePlayer {
        /** The player's uuid. */
        UUID uuid();

        /** The player's profile name, as the tab list spells it. */
        String name();

        /** The shell's adapter. */
        static OnlinePlayer of(UUID uuid, String name) {
            return new OnlinePlayer() {
                @Override
                public UUID uuid() {
                    return uuid;
                }

                @Override
                public String name() {
                    return name;
                }
            };
        }
    }

    /**
     * The message facts a menu action needs. Both names arrive <em>resolved</em>:
     * the shell reads them off the real message (whose display name may come from
     * its rich parts), so nothing about that type's spelling reaches here.
     */
    public interface MessageFacts {
        /** The sender's uuid, or null when identity never arrived. */
        UUID senderUuid();

        /** The sender's real profile name — what commands and the @ token use. */
        String profileName();

        /** The sender's display name, which may be decorated. */
        String senderName();

        /** True for the local player's own messages. */
        boolean own();

        /** True for server lines rather than a player's. */
        boolean system();

        /** The shell's adapter. */
        static MessageFacts of(UUID senderUuid, String profileName, String senderName,
                              boolean own, boolean system) {
            return new MessageFacts() {
                @Override
                public UUID senderUuid() {
                    return senderUuid;
                }

                @Override
                public String profileName() {
                    return profileName;
                }

                @Override
                public String senderName() {
                    return senderName;
                }

                @Override
                public boolean own() {
                    return own;
                }

                @Override
                public boolean system() {
                    return system;
                }
            };
        }
    }

    /** What the screen hands over: the tab list, the command path and the doors. */
    public interface Host {
        /**
         * The tab list as neutral facts — empty when there is no connection, which
         * is exactly what the old "no client / no connection" guard returned.
         */
        List<OnlinePlayer> onlinePlayers();

        /** True when a local player exists, i.e. a command could be sent at all. */
        boolean hasLocalPlayer();

        /**
         * The command to teleport with, probed from the server's command tree
         * (D19's {@code TeleportCommands.commandFor} needs the client object).
         */
        String teleportCommand();

        /** Sends one command line to the server (the platform's own entry point). */
        void sendCommand(String command);

        /** D21: appends text at the caret (the @ mention row). */
        void appendToComposer(String text);

        /**
         * D24: opens the private chat with this player (the whisper row). Named for
         * what it does rather than after the screen's own door, so this port cannot
         * shadow a source-level probe that looks the screen's method up by name.
         */
        void privateChatWith(PlayerRef player);

        /** D18: opens this player's profile detail page (the player card's row 0). */
        void profileDetailFor(PlayerRef player);

        /** The player-card menu's target: the open one, else the closing one. */
        PlayerRef currentOrLastPlayer();
    }

    private final Host host;

    public ContextMenuActions(Host host) {
        this.host = host;
    }

    /**
     * Whether this player is on the tab list: by uuid when one is known, else by
     * name, case-insensitively.
     */
    public boolean isOnlinePlayer(PlayerRef player) {
        if (player == null) {
            return false;
        }
        for (OnlinePlayer entry : host.onlinePlayers()) {
            if (player.uuid() != null && player.uuid().equals(entry.uuid())) {
                return true;
            }
            if (player.realName() != null && player.realName().equalsIgnoreCase(entry.name())) {
                return true;
            }
        }
        return false;
    }

    /** Avatar menus only make sense for another player with a real sender identity. */
    public static boolean canOpenAvatarMenu(MessageFacts msg) {
        if (msg == null || msg.system() || msg.own()) {
            return false;
        }
        return msg.senderName() != null || msg.profileName() != null;
    }

    /**
     * Avatar context-menu actions: row 0 (@ mention), row 1 (whisper), row 2
     * (teleport, only when the player is online) and row 3 (block toggle).
     */
    public void performAvatarMenuAction(int row, MessageFacts message) {
        PlayerRef player = PlayerRef.of(message.senderUuid(), message.profileName());
        if (player == null || player.realName() == null) {
            return;
        }
        if (row == 0) {
            // Insert the real profile name, not the decorated label: the label
            // reaches other clients as uncoloured plain text (the title colour
            // cannot survive a vanilla chat string) and a decorated @token also
            // defeats mention detection that looks for @ProfileName.
            host.appendToComposer("@" + player.realName() + " ");
        } else if (row == 1) {
            host.privateChatWith(player);
        } else if (row == 2) {
            if (isOnlinePlayer(player)) {
                sendTeleportCommand(player);
            }
        } else if (row == 3) {
            toggleBlock(player);
        }
    }

    /** Player-card actions: row 0 (profile), row 1 (teleport), row 2 (block toggle). */
    public void performPlayerCardAction(int row) {
        PlayerRef player = host.currentOrLastPlayer();
        if (player == null) {
            return;
        }
        if (row == 0) {
            host.profileDetailFor(player);
        } else if (row == 1) {
            if (isOnlinePlayer(player)) {
                sendTeleportCommand(player);
            }
        } else if (row == 2) {
            toggleBlock(player);
        }
    }

    public void sendTeleportCommand(PlayerRef player) {
        if (!host.hasLocalPlayer() || player == null || player.realName() == null) {
            return;
        }
        // auto: probe the server command tree, fall back on failure replies.
        String command = host.teleportCommand();
        host.sendCommand(command.substring(1) + " " + player.realName());
        TeleportCommands.noteTeleportSent();
    }

    public void toggleBlock(PlayerRef player) {
        if (player == null) {
            return;
        }
        boolean nowBlocked = BlockList.isBlocked(player);
        BlockList.setBlocked(player, !nowBlocked);
    }
}
