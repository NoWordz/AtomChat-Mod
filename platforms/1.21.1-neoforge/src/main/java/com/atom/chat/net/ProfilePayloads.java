package com.atom.chat.net;
import com.atom.chat.AtomChat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

import com.atom.chat.profile.ProfilePatch;

/** Separate optional profile channel; avatar wire IDs stay untouched. */
public final class ProfilePayloads {
    public record ProfileUpdatePayload(long token, ProfilePatch patch) implements CustomPacketPayload {
        public static final Type<ProfileUpdatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "profile_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ProfileUpdatePayload> STREAM_CODEC = new StreamCodec<>() {
            @Override public ProfileUpdatePayload decode(RegistryFriendlyByteBuf buf) { long token = buf.readLong();
            int flags = buf.readInt();
            if (flags == 0 || (flags & ~7) != 0) throw new IllegalArgumentException("invalid_profile_patch_flags");
            return new ProfileUpdatePayload(token, new ProfilePatch((flags & 1) != 0 ? buf.readUtf(256) : null,
                    (flags & 2) != 0 ? buf.readUtf(2048) : null, (flags & 4) != 0 ? buf.readUtf(2048) : null)); }
            @Override public void encode(RegistryFriendlyByteBuf buf, ProfileUpdatePayload payload) { buf.writeLong(payload.token());
            int flags = (payload.patch().signature() != null ? 1 : 0) | (payload.patch().avatar() != null ? 2 : 0) | (payload.patch().banner() != null ? 4 : 0);
            buf.writeInt(flags);
            if ((flags & 1) != 0) buf.writeUtf(payload.patch().signature(), 256);
            if ((flags & 2) != 0) buf.writeUtf(payload.patch().avatar(), 2048);
            if ((flags & 4) != 0) buf.writeUtf(payload.patch().banner(), 2048); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record ProfileRequestPayload(UUID uuid, long token) implements CustomPacketPayload {
        public static final Type<ProfileRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "profile_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ProfileRequestPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override public ProfileRequestPayload decode(RegistryFriendlyByteBuf buf) { return new ProfileRequestPayload(buf.readUUID(), buf.readLong()); }
            @Override public void encode(RegistryFriendlyByteBuf buf, ProfileRequestPayload payload) { buf.writeUUID(payload.uuid()); buf.writeLong(payload.token()); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record ProfileDataPayload(UUID uuid, long token, String signature, String avatar, String banner, long revision, int imageFields, String error) implements CustomPacketPayload {
        public static final Type<ProfileDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "profile_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ProfileDataPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override public ProfileDataPayload decode(RegistryFriendlyByteBuf buf) { return new ProfileDataPayload(buf.readUUID(), buf.readLong(), buf.readUtf(256), buf.readUtf(2048),
                    buf.readUtf(2048), buf.readLong(), buf.readInt(), buf.readUtf(128)); }
            @Override public void encode(RegistryFriendlyByteBuf buf, ProfileDataPayload payload) { buf.writeUUID(payload.uuid()); buf.writeLong(payload.token());
            buf.writeUtf(payload.signature(), 256); buf.writeUtf(payload.avatar(), 2048); buf.writeUtf(payload.banner(), 2048);
            buf.writeLong(payload.revision()); buf.writeInt(payload.imageFields()); buf.writeUtf(payload.error(), 128); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record ProfileChangedPayload(UUID uuid) implements CustomPacketPayload {
        public static final Type<ProfileChangedPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "profile_changed"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ProfileChangedPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override public ProfileChangedPayload decode(RegistryFriendlyByteBuf buf) { return new ProfileChangedPayload(buf.readUUID()); }
            @Override public void encode(RegistryFriendlyByteBuf buf, ProfileChangedPayload payload) { buf.writeUUID(payload.uuid()); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(ProfileUpdatePayload.TYPE, ProfileUpdatePayload.STREAM_CODEC,
                ProfileCompanionServer::handleUpdate);
        registrar.playToServer(ProfileRequestPayload.TYPE, ProfileRequestPayload.STREAM_CODEC,
                ProfileCompanionServer::handleRequest);
        // The S2C receivers touch client-only rendering classes; keep them in
        // dist-guarded lambdas so a dedicated server never loads them.
        registrar.playToClient(ProfileDataPayload.TYPE, ProfileDataPayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                        ctx.enqueueWork(() -> ProfileCompanionClient.onData(payload));
                    }
                });
        registrar.playToClient(ProfileChangedPayload.TYPE, ProfileChangedPayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                        ctx.enqueueWork(() -> ProfileCompanionClient.onChanged(payload.uuid()));
                    }
                });
    }
}
