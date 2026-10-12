package com.atom.chat.net;
import com.atom.chat.AtomChat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;

import com.atom.chat.profile.ProfilePatch;
import net.minecraftforge.network.NetworkDirection;

/** Separate optional profile channel; avatar wire IDs stay untouched. */
public final class ProfilePayloads {
    private static final String PROTOCOL = "1";
    public static final ResourceLocation CHANNEL_NAME = ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "profile");
    public static final int ID_UPLOAD = 0, ID_REQUEST = 1, ID_DATA = 2, ID_CHANGED = 3;
    public static SimpleChannel CHANNEL;
    public record ProfileUpdatePayload(long token, ProfilePatch patch) {
        public static void encode(ProfileUpdatePayload payload, FriendlyByteBuf buf) { buf.writeLong(payload.token());
            int flags = (payload.patch().signature() != null ? 1 : 0) | (payload.patch().avatar() != null ? 2 : 0) | (payload.patch().banner() != null ? 4 : 0);
            buf.writeInt(flags);
            if ((flags & 1) != 0) buf.writeUtf(payload.patch().signature(), 256);
            if ((flags & 2) != 0) buf.writeUtf(payload.patch().avatar(), 2048);
            if ((flags & 4) != 0) buf.writeUtf(payload.patch().banner(), 2048); }
        public static ProfileUpdatePayload decode(FriendlyByteBuf buf) { long token = buf.readLong();
            int flags = buf.readInt();
            if (flags == 0 || (flags & ~7) != 0) throw new IllegalArgumentException("invalid_profile_patch_flags");
            return new ProfileUpdatePayload(token, new ProfilePatch((flags & 1) != 0 ? buf.readUtf(256) : null,
                    (flags & 2) != 0 ? buf.readUtf(2048) : null, (flags & 4) != 0 ? buf.readUtf(2048) : null)); }
    }
    public record ProfileRequestPayload(UUID uuid, long token) {
        public static void encode(ProfileRequestPayload payload, FriendlyByteBuf buf) { buf.writeUUID(payload.uuid()); buf.writeLong(payload.token()); }
        public static ProfileRequestPayload decode(FriendlyByteBuf buf) { return new ProfileRequestPayload(buf.readUUID(), buf.readLong()); }
    }
    public record ProfileDataPayload(UUID uuid, long token, String signature, String avatar, String banner, long revision, int imageFields, String error) {
        public static void encode(ProfileDataPayload payload, FriendlyByteBuf buf) { buf.writeUUID(payload.uuid()); buf.writeLong(payload.token());
            buf.writeUtf(payload.signature(), 256); buf.writeUtf(payload.avatar(), 2048); buf.writeUtf(payload.banner(), 2048);
            buf.writeLong(payload.revision()); buf.writeInt(payload.imageFields()); buf.writeUtf(payload.error(), 128); }
        public static ProfileDataPayload decode(FriendlyByteBuf buf) { return new ProfileDataPayload(buf.readUUID(), buf.readLong(), buf.readUtf(256), buf.readUtf(2048),
                    buf.readUtf(2048), buf.readLong(), buf.readInt(), buf.readUtf(128)); }
    }
    public record ProfileChangedPayload(UUID uuid) {
        public static void encode(ProfileChangedPayload payload, FriendlyByteBuf buf) { buf.writeUUID(payload.uuid()); }
        public static ProfileChangedPayload decode(FriendlyByteBuf buf) { return new ProfileChangedPayload(buf.readUUID()); }
    }
    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                CHANNEL_NAME,
                () -> PROTOCOL,
                NetworkRegistry.acceptMissingOr(PROTOCOL),
                NetworkRegistry.acceptMissingOr(PROTOCOL));

        CHANNEL.messageBuilder(ProfileUpdatePayload.class, ID_UPLOAD, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ProfileUpdatePayload::encode)
                .decoder(ProfileUpdatePayload::decode)
                .consumerMainThread(ProfileCompanionServer::handleUpdate)
                .add();

        CHANNEL.messageBuilder(ProfileRequestPayload.class, ID_REQUEST, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ProfileRequestPayload::encode)
                .decoder(ProfileRequestPayload::decode)
                .consumerMainThread(ProfileCompanionServer::handleRequest)
                .add();

        // The S2C handlers touch client-only rendering classes; the body only
        // runs on a client, but the dist guard keeps class loading explicit.
        CHANNEL.messageBuilder(ProfileDataPayload.class, ID_DATA, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ProfileDataPayload::encode)
                .decoder(ProfileDataPayload::decode)
                .consumerMainThread((payload, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ctx.get().enqueueWork(() ->
                                ProfileCompanionClient.onData(payload));
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(ProfileChangedPayload.class, ID_CHANGED, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ProfileChangedPayload::encode)
                .decoder(ProfileChangedPayload::decode)
                .consumerMainThread((payload, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ctx.get().enqueueWork(() ->
                                ProfileCompanionClient.onChanged(payload.uuid()));
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }
}
