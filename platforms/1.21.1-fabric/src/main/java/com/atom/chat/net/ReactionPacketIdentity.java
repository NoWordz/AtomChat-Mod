package com.atom.chat.net;
import com.atom.chat.reaction.ReactionFingerprint;
import java.util.UUID;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ProfilelessChatMessageS2CPacket;
import net.minecraft.text.Text;
/** The same wire-preserved fingerprint is used before sending and before handling the packet. */
public final class ReactionPacketIdentity {
    private ReactionPacketIdentity() { }
    public static UUID of(Packet<?> packet,net.minecraft.registry.RegistryWrapper.WrapperLookup registries) {
        if(packet instanceof ChatMessageS2CPacket p) return ReactionFingerprint.of(p.sender(),
                p.signature()==null?null:p.signature().data(),p.body().timestamp(),p.body().salt(),p.body().content());
        if(packet instanceof GameMessageS2CPacket p) return p.overlay()?null:
                ReactionFingerprint.contentKey("system",json(p.content(),registries));
        if(packet instanceof ProfilelessChatMessageS2CPacket p) return ReactionFingerprint.contentKey("disguised",json(p.message(),registries));
        return null;
    }
    public static Object source(Packet<?> packet) {
        if(packet instanceof GameMessageS2CPacket p) return p.content();
        if(packet instanceof ProfilelessChatMessageS2CPacket p) return p.message();
        return null;
    }
    private static String json(Text value,net.minecraft.registry.RegistryWrapper.WrapperLookup registries) { return net.minecraft.text.Text.Serialization.toJsonString(value,registries); }
}
