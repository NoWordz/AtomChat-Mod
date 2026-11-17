package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.config.ServerConfigValues;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Payloads behind {@code /atomchat gui} (0.2.9): the server hands a client its
 * settings, the client sends an edited copy back, and the server answers.
 *
 * <ul>
 *   <li>C2S {@code Save(version, values)} - an edit. The server re-validates
 *       every field (it trusts nothing from the client) and refuses the whole
 *       save when the values are out of range or the {@code version} is stale.</li>
 *   <li>C2S {@code Refresh} - re-scan the emote folder and report the pack again.</li>
 *   <li>S2C {@code Snapshot(version, values, packStatus)} - opened on the player's
 *       own client, which is the only place the screen is ever built.</li>
 *   <li>S2C {@code PackStatus} - the "rescan" answer, deliberately without the
 *       values so it cannot clobber edits that are still on screen.</li>
 *   <li>S2C {@code Result(ok, error)} - the verdict, so a refused save stays on
 *       screen with a reason instead of closing.</li>
 * </ul>
 *
 * <p>The value list, its limits, its truncation and its validation live in
 * {@link Wire}; this file keeps the packet ids and the channel wiring.
 */
public final class ConfigPayloads {
    /** Bumped when the packet layout changes; the channel rejects mismatches. */
    private static final String PROTOCOL = "1";

    /** Wire contract: the channel name and packet ids must never be renamed or reordered. */
    public static final ResourceLocation CHANNEL_NAME =
            ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "config");
    public static final int ID_SAVE = 0;
    public static final int ID_REFRESH = 1;
    public static final int ID_SNAPSHOT = 2;
    public static final int ID_STATUS = 3;
    public static final int ID_RESULT = 4;
    

    public static SimpleChannel CHANNEL;

    public static void sendSave(Save payload) { CHANNEL.sendToServer(payload); }
    public static void sendRefresh() { var payload = new Refresh(); CHANNEL.sendToServer(payload); }

    private ConfigPayloads() {
    }

    /** The pack summary line: what the server would hand out right now. */
    public record PackStatus(int files, long bytes, String packHash) {
    }

    // ------------------------------------------------------------ value codec

    private static void writeValues(ServerConfigValues values, FriendlyByteBuf buf) {
        Wire.writeValues(new ForgeWireIo(buf), values);
    }

    private static ServerConfigValues readValues(FriendlyByteBuf buf) {
        return Wire.readValues(new ForgeWireIo(buf));
    }

    private static void writeStatus(PackStatus status, FriendlyByteBuf buf) {
        Wire.writePackSummary(new ForgeWireIo(buf), summary(status));
    }

    private static PackStatus readStatus(FriendlyByteBuf buf) {
        Wire.PackSummary summary = Wire.readPackSummary(new ForgeWireIo(buf));
        return new PackStatus(summary.files(), summary.bytes(), summary.packHash());
    }

    private static Wire.PackSummary summary(PackStatus status) {
        return new Wire.PackSummary(status.files(), status.bytes(), status.packHash());
    }

    // ---------------------------------------------------------------- records

    /** "Here is my current configuration and pack." */
    public record Snapshot(int configVersion, ServerConfigValues values, PackStatus pack)
            {

        public static void encode(Snapshot payload, FriendlyByteBuf buf) {
            buf.writeVarInt(payload.configVersion());
            writeValues(payload.values(), buf);
            writeStatus(payload.pack(), buf);
        }

        public static Snapshot decode(FriendlyByteBuf buf) {
            int version = buf.readVarInt();
            ServerConfigValues values = readValues(buf);
            return new Snapshot(version, values, readStatus(buf));
        }

    }

    /** "Store these values" - one shot, the version guards against a stale screen. */
    public record Save(int configVersion, ServerConfigValues values) {

        public static void encode(Save payload, FriendlyByteBuf buf) {
            buf.writeVarInt(payload.configVersion());
            writeValues(payload.values(), buf);
        }

        public static Save decode(FriendlyByteBuf buf) {
            int version = buf.readVarInt();
            return new Save(version, readValues(buf));
        }

    }

    /** "Re-scan the emote folder and tell me what the pack looks like now." */
    public record Refresh() {

        public static void encode(Refresh payload, FriendlyByteBuf buf) {
        }

        public static Refresh decode(FriendlyByteBuf buf) {
            return new Refresh();
        }

    }

    /** The pack summary on its own, so a rescan cannot clobber unsaved edits. */
    public record Status(PackStatus pack) {

        public static void encode(Status payload, FriendlyByteBuf buf) {
            writeStatus(payload.pack(), buf);
        }

        public static Status decode(FriendlyByteBuf buf) {
            return new Status(readStatus(buf));
        }

    }

    /** "Saved" or "refused, and why" - the error is a lang key fragment. */
    public record Result(boolean ok, String error) {

        public static void encode(Result payload, FriendlyByteBuf buf) {
            buf.writeBoolean(payload.ok());
            buf.writeUtf(payload.error() == null ? "" : payload.error(), 64);
        }

        public static Result decode(FriendlyByteBuf buf) {
            return new Result(buf.readBoolean(), buf.readUtf(64));
        }

    }

    /** Builds the config channel and registers every packet id. Common setup. */
    public static void register() {
        ConfigAdminPayloads.register();
        CHANNEL = NetworkRegistry.newSimpleChannel(CHANNEL_NAME, () -> PROTOCOL,
                NetworkRegistry.acceptMissingOr(PROTOCOL),
                NetworkRegistry.acceptMissingOr(PROTOCOL));

        CHANNEL.messageBuilder(Save.class, ID_SAVE)
                .encoder(Save::encode)
                .decoder(Save::decode)
                .consumerMainThread((payload, ctx) -> {
                    ctx.get().enqueueWork(() -> ConfigScreenServer.onSave(ctx.get().getSender(), payload));
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(Refresh.class, ID_REFRESH)
                .encoder(Refresh::encode)
                .decoder(Refresh::decode)
                .consumerMainThread((payload, ctx) -> {
                    ctx.get().enqueueWork(() -> ConfigScreenServer.onRefresh(ctx.get().getSender()));
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(Snapshot.class, ID_SNAPSHOT)
                .encoder(Snapshot::encode)
                .decoder(Snapshot::decode)
                .consumerMainThread((payload, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                    ctx.get().enqueueWork(() -> ConfigScreenClient.onSnapshot(payload));
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(Status.class, ID_STATUS)
                .encoder(Status::encode)
                .decoder(Status::decode)
                .consumerMainThread((payload, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                    ctx.get().enqueueWork(() -> ConfigScreenClient.onStatus(payload.pack()));
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(Result.class, ID_RESULT)
                .encoder(Result::encode)
                .decoder(Result::decode)
                .consumerMainThread((payload, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                    ctx.get().enqueueWork(() -> ConfigScreenClient.onResult(payload));
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();

    }
}
