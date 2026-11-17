package com.atom.chat.net;
import com.atom.chat.config.AdminProtocol;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;

/** Client-only transport; common registration must remain loadable on a dedicated server. */
@Environment(EnvType.CLIENT)
public final class ConfigAdminClientTransport {
    private ConfigAdminClientTransport() {}
    public static void initClient(){ClientPlayNetworking.registerGlobalReceiver(ConfigAdminPayloads.Reply.ID,(p,c)->{var origin=c.responseSender();c.client().execute(()->ConfigAdminClient.receive(p.chunk(),origin));});}
    public static Object connection(){return net.minecraft.client.MinecraftClient.getInstance().getNetworkHandler()==null?null:ClientPlayNetworking.getSender();}
    public static boolean available(){return ClientPlayNetworking.canSend(ConfigAdminPayloads.Request.ID);}
    public static void sendChunk(AdminProtocol.Chunk c){ClientPlayNetworking.send(new ConfigAdminPayloads.Request(c));}
}
