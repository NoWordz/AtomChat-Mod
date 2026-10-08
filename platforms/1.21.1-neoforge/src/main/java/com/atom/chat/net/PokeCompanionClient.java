package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Client side of the poke companion: sends a poke and hands incoming pokes to
 * the open screen. Without the negotiated channel the local wobble still plays.
 */
public final class PokeCompanionClient {
    private PokeCompanionClient() {
    }

    /** Set by the screen while it is open; cleared when it closes. */
    private static volatile Consumer<UUID> onPoked;

    /** No-op init seam; registration is data-driven through the payload registrar. */
    public static void init() {
    }

    public static void onPoked(PokePayloads.PokeS2CPayload payload) {
        UUID from = payload.from();
        Consumer<UUID> handler = onPoked;
        if (from != null && handler != null) {
            handler.accept(from);
        }
    }

    public static void setHandler(Consumer<UUID> handler) {
        onPoked = handler;
    }

    /** Sends a poke when a server connection exists; returns whether it was sent. */
    public static boolean send(UUID target) {
        Minecraft client = Minecraft.getInstance();
        if (target == null || client.player == null || client.getConnection() == null) {
            return false;
        }
        PacketDistributor.sendToServer(new PokePayloads.PokeC2SPayload(target));
        return true;
    }
}
