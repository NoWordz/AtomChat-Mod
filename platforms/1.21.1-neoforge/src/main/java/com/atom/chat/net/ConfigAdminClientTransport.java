package com.atom.chat.net;
import com.atom.chat.config.AdminProtocol;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only transport; common registration must remain loadable on a dedicated server. */
@OnlyIn(Dist.CLIENT)
public final class ConfigAdminClientTransport {
    private ConfigAdminClientTransport() {}
    public static Object connection(){var c=net.minecraft.client.Minecraft.getInstance().getConnection();return c==null?null:c.getConnection();}
    public static boolean available(){var c=net.minecraft.client.Minecraft.getInstance().getConnection();return c!=null&&c.hasChannel(ConfigAdminPayloads.Request.TYPE);}
    public static void sendChunk(AdminProtocol.Chunk c){PacketDistributor.sendToServer(new ConfigAdminPayloads.Request(c));}
}
