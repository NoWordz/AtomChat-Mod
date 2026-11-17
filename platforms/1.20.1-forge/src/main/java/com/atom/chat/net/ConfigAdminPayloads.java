package com.atom.chat.net;
import com.atom.chat.config.AdminProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
public final class ConfigAdminPayloads {
    private ConfigAdminPayloads() {}
    static SimpleChannel channel;
    public record Request(AdminProtocol.Chunk chunk) {}
    public record Reply(AdminProtocol.Chunk chunk) {}
    private static void write(AdminProtocol.Chunk c,FriendlyByteBuf b){b.writeInt(c.id());b.writeVarInt(c.index());b.writeVarInt(c.count());b.writeUtf(c.text(),8000);}
    private static AdminProtocol.Chunk read(FriendlyByteBuf b){return new AdminProtocol.Chunk(b.readInt(),b.readVarInt(),b.readVarInt(),b.readUtf(8000));}
    public static void register(){
        channel=NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("atomchat","config_admin"),()->"1",NetworkRegistry.acceptMissingOr("1"),NetworkRegistry.acceptMissingOr("1"));
        channel.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER).encoder((p,b)->write(p.chunk(),b)).decoder(b->new Request(read(b))).consumerMainThread((p,c)->{c.get().enqueueWork(()->ConfigScreenServer.onAdmin(c.get().getSender(),p.chunk()));c.get().setPacketHandled(true);}).add();
        channel.messageBuilder(Reply.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder((p,b)->write(p.chunk(),b)).decoder(b->new Reply(read(b))).consumerMainThread((p,c)->{if(FMLEnvironment.dist==Dist.CLIENT)c.get().enqueueWork(()->ConfigAdminClient.receive(p.chunk(),c.get().getNetworkManager()));c.get().setPacketHandled(true);}).add();
    }
    public static void sendPlayerChunk(Object player,AdminProtocol.Chunk c){channel.send(PacketDistributor.PLAYER.with(()->(ServerPlayer)player),new Reply(c));}
}
