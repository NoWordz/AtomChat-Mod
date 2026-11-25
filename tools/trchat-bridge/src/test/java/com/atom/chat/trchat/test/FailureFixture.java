package com.atom.chat.trchat.test;

import com.atom.chat.channel.ChannelWire;
import com.atom.chat.trchat.ChannelBridge;
import me.arasple.mc.trchat.module.display.channel.Channel;
import me.arasple.mc.trchat.taboolib.module.chat.Components;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/** Drives the real Bukkit outward delivery seam in the isolated Paper fixture. */
public final class FailureFixture extends JavaPlugin {
    @Override public void onEnable() {
        getCommand("bridge-regression").setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player) return true;
            Player receiver = Bukkit.getPlayerExact(args[1]);
            Player author = Bukkit.getPlayerExact("BridgeA");
            var channels = Channel.Companion.getChannels();
            Channel alpha = channels.values().stream().filter(c -> c.getId().equalsIgnoreCase("alpha"))
                    .findFirst().orElseThrow();
            Channel beta = channels.values().stream().filter(c -> c.getId().equalsIgnoreCase("beta"))
                    .findFirst().orElseThrow();
            switch (args[0]) {
                case "missing" -> {
                    channels.remove(alpha.getId());
                    try {
                        emit(receiver, author, alpha, "ACCH-MISSING");
                    } finally {
                        channels.put(alpha.getId(), alpha);
                    }
                }
                case "oversized" -> emit(receiver, author, alpha, "ACCH-OVERSIZED " + "x".repeat(25_000));
                case "snapshot" -> {
                    var registrations = registrations(receiver);
                    boolean registered = registrations.remove(ChannelWire.PATH);
                    boolean wasListener = beta.getListeners().remove(receiver.getName());
                    try {
                        if (!registered || !wasListener) throw new IllegalStateException("fixture preconditions");
                        emit(receiver, author, alpha, "ACCH-SNAPSHOT");
                    } finally {
                        if (registered) registrations.add(ChannelWire.PATH);
                        if (wasListener) beta.getListeners().add(receiver.getName());
                    }
                }
                case "unregister" -> {
                    updateRegistration(receiver, "removeChannel");
                    try {
                        emit(receiver, author, alpha, "ACCH-UNREGISTERED");
                    } finally {
                        updateRegistration(receiver, "addChannel");
                    }
                }
                case "ordinary" -> emit(receiver, author, alpha, "ACCH-ORDINARY");
                case "healthy" -> emit(receiver, author, alpha, "ACCH-HEALTHY");
                default -> throw new IllegalArgumentException("Unknown fixture mode");
            }
            return true;
        });
    }

    private static void emit(Player receiver, Player author, Channel channel, String body) {
        if (receiver == null || author == null) throw new IllegalStateException("Missing probe accounts");
        ChannelBridge.local(receiver, author, Components.INSTANCE.text(body), channel);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> registrations(Player receiver) {
        try {
            var field = receiver.getClass().getDeclaredField("channels");
            field.setAccessible(true);
            return (Set<String>) field.get(receiver);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Paper fixture registration field", e);
        }
    }

    private static void updateRegistration(Player player, String method) {
        try {
            player.getClass().getMethod(method, String.class).invoke(player, ChannelWire.PATH);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Paper fixture registration method", e);
        }
    }
}
