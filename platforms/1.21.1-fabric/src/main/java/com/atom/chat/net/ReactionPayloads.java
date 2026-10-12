package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;



public final class ReactionPayloads {
    private ReactionPayloads() {
    }

    
    public record ActionPayload(byte[] bytes) implements CustomPayload {
        public static final CustomPayload.Id<ActionPayload> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "reaction_action"));
        public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC =
                PacketCodec.of(ActionPayload::write, ActionPayload::read);

        private static void write(ActionPayload payload, RegistryByteBuf buf) {
            buf.writeByteArray(payload.bytes());
        }

        private static ActionPayload read(RegistryByteBuf buf) {
            return new ActionPayload(buf.readByteArray(128));
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    
    public record DataPayload(byte[] bytes) implements CustomPayload {
        public static final CustomPayload.Id<DataPayload> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "reaction_data"));
        public static final PacketCodec<RegistryByteBuf, DataPayload> CODEC =
                PacketCodec.of(DataPayload::write, DataPayload::read);

        private static void write(DataPayload payload, RegistryByteBuf buf) {
            buf.writeByteArray(payload.bytes());
        }

        private static DataPayload read(RegistryByteBuf buf) {
            return new DataPayload(buf.readByteArray(com.atom.chat.reaction.ReactionWire.MAX_BYTES));
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    
    public static void register() {
        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DataPayload.ID, DataPayload.CODEC);
    }
}
