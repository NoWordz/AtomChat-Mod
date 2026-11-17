package com.atom.chat.net;
import com.atom.chat.config.AdminProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
public final class ConfigAdminPayloads {
    private ConfigAdminPayloads() {}
    private static void write(RegistryFriendlyByteBuf b,AdminProtocol.Chunk c){b.writeInt(c.id());b.writeVarInt(c.index());b.writeVarInt(c.count());b.writeUtf(c.text(),8000);}
    private static AdminProtocol.Chunk read(RegistryFriendlyByteBuf b){return new AdminProtocol.Chunk(b.readInt(),b.readVarInt(),b.readVarInt(),b.readUtf(8000));}
    public record Request(AdminProtocol.Chunk chunk) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("atomchat","admin_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->write(b,p.chunk()),b->new Request(read(b)));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Reply(AdminProtocol.Chunk chunk) implements CustomPacketPayload {
        public static final Type<Reply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("atomchat","admin_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Reply> CODEC=StreamCodec.of((b,p)->write(b,p.chunk()),b->new Reply(read(b)));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){var r=event.registrar("1").optional();
        r.playToServer(Request.TYPE,Request.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof net.minecraft.server.level.ServerPlayer player)ConfigScreenServer.onAdmin(player,p.chunk());}));
        r.playToClient(Reply.TYPE,Reply.CODEC,(p,c)->{if(FMLEnvironment.dist==Dist.CLIENT)c.enqueueWork(()->ConfigAdminClient.receive(p.chunk(),c.connection()));});
    }
    public static void sendPlayerChunk(Object player,AdminProtocol.Chunk c){PacketDistributor.sendToPlayer((net.minecraft.server.level.ServerPlayer)player,new Reply(c));}
}
