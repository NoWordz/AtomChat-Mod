package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Poke-companion payloads. Same optional-channel shape as the avatar
 * companion: registered on both logical sides, only sent once the server has
 * negotiated the channel, so a vanilla or companion-less server never receives
 * an unknown payload and the client silently degrades to a local-only poke.
 */
public final class PokePayloads {
    private PokePayloads() {
    }

    /** C2S: "I poked the player with this uuid." The server resolves the sender. */
    public record PokeC2SPayload(UUID target) implements CustomPayload {
        public static final CustomPayload.Id<PokeC2SPayload> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "poke"));
        public static final PacketCodec<RegistryByteBuf, PokeC2SPayload> CODEC =
                PacketCodec.of(PokeC2SPayload::write, PokeC2SPayload::read);

        private static void write(PokeC2SPayload payload, RegistryByteBuf buf) {
            buf.writeUuid(payload.target());
        }

        private static PokeC2SPayload read(RegistryByteBuf buf) {
            return new PokeC2SPayload(buf.readUuid());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** S2C: "this player poked you." */
    public record PokeS2CPayload(UUID from) implements CustomPayload {
        public static final CustomPayload.Id<PokeS2CPayload> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "poked"));
        public static final PacketCodec<RegistryByteBuf, PokeS2CPayload> CODEC =
                PacketCodec.of(PokeS2CPayload::write, PokeS2CPayload::read);

        private static void write(PokeS2CPayload payload, RegistryByteBuf buf) {
            buf.writeUuid(payload.from());
        }

        private static PokeS2CPayload read(RegistryByteBuf buf) {
            return new PokeS2CPayload(buf.readUuid());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Registers both codecs; must run on both logical sides (common init). */
    public static void register() {
        PayloadTypeRegistry.playC2S().register(PokeC2SPayload.ID, PokeC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PokeS2CPayload.ID, PokeS2CPayload.CODEC);
    }
}
