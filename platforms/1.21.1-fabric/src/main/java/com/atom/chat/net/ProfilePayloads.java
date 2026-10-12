package com.atom.chat.net;
import com.atom.chat.AtomChat;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

import com.atom.chat.profile.ProfilePatch;

/** Separate optional profile channel; avatar wire IDs stay untouched. */
public final class ProfilePayloads {
    public record ProfileUpdatePayload(long token, ProfilePatch patch) implements CustomPayload {
        public static final CustomPayload.Id<ProfileUpdatePayload> ID = new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "profile_update"));
        public static final PacketCodec<RegistryByteBuf, ProfileUpdatePayload> CODEC = PacketCodec.of(ProfileUpdatePayload::write, ProfileUpdatePayload::read);
        private static void write(ProfileUpdatePayload payload, RegistryByteBuf buf) { buf.writeLong(payload.token());
            int flags = (payload.patch().signature() != null ? 1 : 0) | (payload.patch().avatar() != null ? 2 : 0) | (payload.patch().banner() != null ? 4 : 0);
            buf.writeInt(flags);
            if ((flags & 1) != 0) buf.writeString(payload.patch().signature(), 256);
            if ((flags & 2) != 0) buf.writeString(payload.patch().avatar(), 2048);
            if ((flags & 4) != 0) buf.writeString(payload.patch().banner(), 2048); }
        private static ProfileUpdatePayload read(RegistryByteBuf buf) { long token = buf.readLong();
            int flags = buf.readInt();
            if (flags == 0 || (flags & ~7) != 0) throw new IllegalArgumentException("invalid_profile_patch_flags");
            return new ProfileUpdatePayload(token, new ProfilePatch((flags & 1) != 0 ? buf.readString(256) : null,
                    (flags & 2) != 0 ? buf.readString(2048) : null, (flags & 4) != 0 ? buf.readString(2048) : null)); }
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }
    public record ProfileRequestPayload(UUID uuid, long token) implements CustomPayload {
        public static final CustomPayload.Id<ProfileRequestPayload> ID = new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "profile_request"));
        public static final PacketCodec<RegistryByteBuf, ProfileRequestPayload> CODEC = PacketCodec.of(ProfileRequestPayload::write, ProfileRequestPayload::read);
        private static void write(ProfileRequestPayload payload, RegistryByteBuf buf) { buf.writeUuid(payload.uuid()); buf.writeLong(payload.token()); }
        private static ProfileRequestPayload read(RegistryByteBuf buf) { return new ProfileRequestPayload(buf.readUuid(), buf.readLong()); }
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }
    public record ProfileDataPayload(UUID uuid, long token, String signature, String avatar, String banner, long revision, int imageFields, String error) implements CustomPayload {
        public static final CustomPayload.Id<ProfileDataPayload> ID = new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "profile_data"));
        public static final PacketCodec<RegistryByteBuf, ProfileDataPayload> CODEC = PacketCodec.of(ProfileDataPayload::write, ProfileDataPayload::read);
        private static void write(ProfileDataPayload payload, RegistryByteBuf buf) { buf.writeUuid(payload.uuid()); buf.writeLong(payload.token());
            buf.writeString(payload.signature(), 256); buf.writeString(payload.avatar(), 2048); buf.writeString(payload.banner(), 2048);
            buf.writeLong(payload.revision()); buf.writeInt(payload.imageFields()); buf.writeString(payload.error(), 128); }
        private static ProfileDataPayload read(RegistryByteBuf buf) { return new ProfileDataPayload(buf.readUuid(), buf.readLong(), buf.readString(256), buf.readString(2048),
                    buf.readString(2048), buf.readLong(), buf.readInt(), buf.readString(128)); }
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }
    public record ProfileChangedPayload(UUID uuid) implements CustomPayload {
        public static final CustomPayload.Id<ProfileChangedPayload> ID = new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "profile_changed"));
        public static final PacketCodec<RegistryByteBuf, ProfileChangedPayload> CODEC = PacketCodec.of(ProfileChangedPayload::write, ProfileChangedPayload::read);
        private static void write(ProfileChangedPayload payload, RegistryByteBuf buf) { buf.writeUuid(payload.uuid()); }
        private static ProfileChangedPayload read(RegistryByteBuf buf) { return new ProfileChangedPayload(buf.readUuid()); }
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(ProfileUpdatePayload.ID, ProfileUpdatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ProfileRequestPayload.ID, ProfileRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ProfileDataPayload.ID, ProfileDataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ProfileChangedPayload.ID, ProfileChangedPayload.CODEC);
    }
}
