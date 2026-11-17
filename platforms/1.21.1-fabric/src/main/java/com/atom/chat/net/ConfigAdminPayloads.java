package com.atom.chat.net;
import com.atom.chat.config.AdminProtocol;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.*;
public final class ConfigAdminPayloads {
    private ConfigAdminPayloads() {}
    private static void write(AdminProtocol.Chunk c,RegistryByteBuf b){b.writeInt(c.id());b.writeVarInt(c.index());b.writeVarInt(c.count());b.writeString(c.text(),8000);}
    private static AdminProtocol.Chunk read(RegistryByteBuf b){return new AdminProtocol.Chunk(b.readInt(),b.readVarInt(),b.readVarInt(),b.readString(8000));}
    public record Request(AdminProtocol.Chunk chunk) implements CustomPayload {
        public static final Id<Request> ID=new Id<>(Identifier.of("atomchat","admin_request"));
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.of((p,b)->write(p.chunk(),b),b->new Request(read(b)));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Reply(AdminProtocol.Chunk chunk) implements CustomPayload {
        public static final Id<Reply> ID=new Id<>(Identifier.of("atomchat","admin_reply"));
        public static final PacketCodec<RegistryByteBuf,Reply> CODEC=PacketCodec.of((p,b)->write(p.chunk(),b),b->new Reply(read(b)));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public static void register(){
        PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);PayloadTypeRegistry.playS2C().register(Reply.ID,Reply.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,(p,c)->c.server().execute(()->ConfigScreenServer.onAdmin(c.player(),p.chunk())));
    }
    public static void sendPlayerChunk(Object player,AdminProtocol.Chunk c){ServerPlayNetworking.send((net.minecraft.server.network.ServerPlayerEntity)player,new Reply(c));}
}
