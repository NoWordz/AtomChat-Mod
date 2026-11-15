package com.atom.chat.net;
import com.atom.chat.reaction.ReactionFingerprint;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.chat.Component;
/** The same wire-preserved fingerprint is used before sending and before handling the packet. */
public final class ReactionPacketIdentity {
    private ReactionPacketIdentity() { }
    public static UUID of(Packet<?> packet,net.minecraft.core.RegistryAccess registries) {
        if(packet instanceof ClientboundPlayerChatPacket p) return ReactionFingerprint.of(p.sender(),
                p.signature()==null?null:p.signature().bytes(),p.body().timeStamp(),p.body().salt(),p.body().content());
        if(packet instanceof ClientboundSystemChatPacket p) return p.overlay()?null:
                ReactionFingerprint.contentKey("system",json(p.content(),registries));
        if(packet instanceof ClientboundDisguisedChatPacket p) return ReactionFingerprint.contentKey("disguised",json(p.message(),registries));
        return null;
    }
    public static Object source(Packet<?> packet) {
        if(packet instanceof ClientboundSystemChatPacket p) return p.content();
        if(packet instanceof ClientboundDisguisedChatPacket p) return p.message();
        return null;
    }
    private static String json(Component value,net.minecraft.core.RegistryAccess registries) { return net.minecraft.network.chat.Component.Serializer.toJson(value); }
}
