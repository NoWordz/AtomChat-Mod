package com.atom.chat.net;
import com.atom.chat.config.AdminProtocol;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.api.distmarker.Dist;

/** Client-only transport; common registration must remain loadable on a dedicated server. */
@OnlyIn(Dist.CLIENT)
public final class ConfigAdminClientTransport {
    private ConfigAdminClientTransport() {}
    public static Object connection(){var c=net.minecraft.client.Minecraft.getInstance().getConnection();return c==null?null:c.getConnection();}
    public static boolean available(){var c=net.minecraft.client.Minecraft.getInstance().getConnection();return ConfigAdminPayloads.channel!=null&&c!=null&&ConfigAdminPayloads.channel.isRemotePresent(c.getConnection());}
    public static void sendChunk(AdminProtocol.Chunk c){ConfigAdminPayloads.channel.sendToServer(new ConfigAdminPayloads.Request(c));}
}
