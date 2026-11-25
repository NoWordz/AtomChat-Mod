package com.atom.chat.trchat;

import com.atom.chat.channel.ChannelWire;
import com.destroystokyo.paper.ClientOption;
import me.arasple.mc.trchat.api.impl.BukkitProxyManager;
import me.arasple.mc.trchat.module.display.channel.Channel;
import me.arasple.mc.trchat.taboolib.module.chat.ComponentText;
import me.arasple.mc.trchat.util.BukkitUtilKt;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/** Called only by the pinned TrChat join and per-recipient delivery hooks. */
public final class ChannelBridge implements Listener {
    private static volatile ChannelBridge current;
    private static final ThreadLocal<ProxyEnvelope.Origin> source = new ThreadLocal<>();
    private static final int MAX_REPLAYS = 8192;
    private final JavaPlugin plugin;
    private final String backend;
    private final Set<String> enabled, peers;
    private final byte[] proxyKey;
    private final Map<UUID, Peer> sessions = new HashMap<>();
    private final Map<Receipt, Long> receipts = new HashMap<>();
    private BukkitTask pulse;

    private static final class Peer {
        final Player player;
        final UUID session = UUID.randomUUID();
        UUID nonce;
        long sequence;
        List<ChannelWire.Entry> channels = List.of();
        Peer(Player player) { this.player = player; }
    }

    private record Receipt(UUID message, UUID recipient) {}
    private record Routed(ChannelWire.Key key, UUID id, UUID author, String name, String json, boolean remote) {}

    private ChannelBridge(JavaPlugin plugin, YamlConfiguration config) {
        this.plugin = plugin;
        backend = config.getString("backend-id", "").strip();
        enabled = Set.copyOf(config.getStringList("channels"));
        peers = Set.copyOf(config.getStringList("velocity-peers"));
        for (String id : enabled) new ChannelWire.Key(backend, id);
        for (String peer : peers) new ChannelWire.Key(peer, "test");
        String encoded = config.getString("velocity-key", "").strip();
        proxyKey = encoded.isEmpty() ? null : Base64.getDecoder().decode(encoded);
        if ((!peers.isEmpty() && proxyKey == null) || (proxyKey != null && proxyKey.length < 32)
                || enabled.size() * (1L + peers.size()) > ChannelWire.MAX_CHANNELS || peers.contains(backend)) {
            throw new IllegalArgumentException("Invalid bridge peer configuration");
        }
    }

    public static void install() {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(ChannelBridge.class);
        try {
            File file = new File(plugin.getDataFolder(), "atomchat-channels.yml");
            if (!file.exists()) {
                Files.createDirectories(file.toPath().getParent());
                Files.writeString(file.toPath(), "backend-id: ''\nchannels: []\nvelocity-peers: []\nvelocity-key: ''\n");
            }
            var bridge = new ChannelBridge(plugin, YamlConfiguration.loadConfiguration(file));
            if (bridge.enabled.isEmpty()) return;
            var messenger = Bukkit.getMessenger();
            messenger.registerOutgoingPluginChannel(plugin, ChannelWire.PATH);
            messenger.registerIncomingPluginChannel(plugin, ChannelWire.PATH, bridge::receive);
            Bukkit.getPluginManager().registerEvents(bridge, plugin);
            bridge.pulse = Bukkit.getScheduler().runTaskTimer(plugin, bridge::pulse, 1, 20);
            current = bridge;
            plugin.getLogger().info("AtomChat channel bridge active for " + bridge.backend);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("AtomChat channel bridge disabled: check atomchat-channels.yml");
        }
    }

    public static void stop() {
        ChannelBridge bridge = current;
        current = null;
        if (bridge != null) {
            bridge.pulse.cancel();
            bridge.sessions.clear();
            bridge.receipts.clear();
        }
    }

    @EventHandler public void registered(PlayerRegisterChannelEvent event) {
        if (ChannelWire.PATH.equals(event.getChannel())) offer(event.getPlayer());
    }
    // Unregistering the payload transport does not opt a negotiated connection out of isolation.
    @EventHandler public void quit(PlayerQuitEvent event) { forget(event.getPlayer()); }

    private void forget(Player player) {
        sessions.computeIfPresent(player.getUniqueId(), (id, peer) -> peer.player == player ? null : peer);
    }

    private void offer(Player player) {
        if (!online(player) || !player.getListeningPluginChannels().contains(ChannelWire.PATH)) return;
        Peer peer = sessions.compute(player.getUniqueId(), (id, existing) ->
                existing != null && existing.player == player ? existing : new Peer(player));
        send(player, new ChannelWire.Offer(peer.session));
    }

    private void receive(String channel, Player player, byte[] bytes) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> receive(channel, player, bytes));
            return;
        }
        Peer peer = sessions.get(player.getUniqueId());
        if (!online(player) || peer == null || peer.player != player) return;
        var frame = ChannelWire.decode(bytes);
        if (frame instanceof ChannelWire.Stop stop && peer.session.equals(stop.session()) && stop.nonce().equals(peer.nonce)) {
            peer.nonce = null;
            peer.channels = List.of();
            return;
        }
        if (!(frame instanceof ChannelWire.Hello hello) || !peer.session.equals(hello.session())) return;
        if (peer.nonce != null && peer.nonce.equals(hello.nonce())) return;
        peer.nonce = hello.nonce();
        snapshot(peer, true);
    }

    private void pulse() {
        long now = System.currentTimeMillis();
        receipts.values().removeIf(expiry -> expiry < now);
        for (Player player : Bukkit.getOnlinePlayers()) offer(player);
        for (Peer peer : sessions.values()) {
            if (peer.nonce != null && online(peer.player)) snapshot(peer, true);
        }
    }

    private boolean snapshot(Peer peer, boolean force) {
        var allowed = new ArrayList<ChannelWire.Entry>();
        for (String id : enabled.stream().sorted().toList()) {
            Channel channel = resolve(id);
            if (channel == null || !eligible(peer.player, channel)) continue;
            allowed.add(new ChannelWire.Entry(new ChannelWire.Key(backend, id), channel.getId()));
            for (String origin : peers.stream().sorted().toList()) {
                allowed.add(new ChannelWire.Entry(new ChannelWire.Key(origin, id), channel.getId()));
            }
        }
        if (!force && allowed.equals(peer.channels)) return true;
        if (!send(peer.player, new ChannelWire.Snapshot(peer.session, peer.nonce, ++peer.sequence, allowed))) return false;
        peer.channels = List.copyOf(allowed);
        return true;
    }

    private static boolean send(Player player, ChannelWire.Frame frame) {
        ChannelBridge bridge = current;
        if (bridge == null || !player.getListeningPluginChannels().contains(ChannelWire.PATH)) return false;
        byte[] bytes = ChannelWire.encode(frame);
        if (bytes == null) return false;
        try {
            player.sendPluginMessage(bridge.plugin, ChannelWire.PATH, bytes);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Channel resolve(String id) {
        Channel found = null;
        for (Channel channel : Channel.Companion.getChannels().values()) {
            if (!normalized(channel).equals(id)) continue;
            if (found != null || channel.getSettings().isPrivate()) return null;
            found = channel;
        }
        return found;
    }

    static boolean mayReceive(boolean subscribed, boolean mayJoin, boolean mayListen) {
        return subscribed && mayJoin && mayListen;
    }

    private static boolean eligible(Player player, Channel channel) {
        String join = channel.getSettings().getJoinPermission();
        return mayReceive(channel.getListeners().contains(player.getName()),
                join.isEmpty() || player.hasPermission(join), channel.canListen(player));
    }

    private static boolean online(Player player) {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    private static String normalized(Channel channel) {
        return channel.getId().toLowerCase(Locale.ROOT);
    }

    private static Player recipient(Object object) {
        if (object instanceof Player player) return player;
        if (object != null) {
            try {
                // The pinned TabooLib adapter is loaded at runtime by TrChat.
                if (object.getClass().getMethod("getOrigin").invoke(object) instanceof Player player) return player;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    public static void local(Object target, Object author, ComponentText text, Channel channel) {
        var bridge = current;
        Player receiver = recipient(target);
        if (bridge == null || receiver == null || !(author instanceof Player player)
                || channel.getSettings().isPrivate() || !bridge.enabled.contains(normalized(channel))) {
            BukkitUtilKt.sendComponent(target, author, text);
            return;
        }
        var route = new Routed(new ChannelWire.Key(bridge.backend, normalized(channel)), UUID.randomUUID(),
                player.getUniqueId(), player.getName(), text.toRawMessage(), false);
        bridge.deliver(receiver, target, author, text, route);
    }

    /** Surround the original call so its own BroadcastRaw serializer runs unchanged. */
    public static void forward(BukkitProxyManager manager, Object recipient, UUID uuid, ComponentText component,
                               String permission, boolean twice, List<Integer> ports, String fallback,
                               String name, String mentioned, int defaults, Object marker, Channel channel) {
        if (defaults != 64 || marker != null) throw new IllegalStateException("Unreviewed TrChat forwarding call");
        var bridge = current;
        var previous = source.get();
        try {
            if (bridge != null && bridge.proxyKey != null && twice && !channel.getSettings().isPrivate()
                    && bridge.enabled.contains(normalized(channel))) {
                source.set(new ProxyEnvelope.Origin(bridge.backend, normalized(channel), UUID.randomUUID()));
            } else {
                source.remove();
            }
            manager.sendBroadcastRaw(recipient, uuid, component, permission, twice,
                    ports, component.toLegacyText(), name, mentioned);
        } finally {
            if (previous == null) source.remove();
            else source.set(previous);
        }
    }

    public static String[] seal(String[] data) {
        var bridge = current;
        var origin = source.get();
        return bridge == null || bridge.proxyKey == null || origin == null ? data
                : ProxyEnvelope.seal(data, origin, bridge.proxyKey, System.currentTimeMillis());
    }

    public static void remote(Object target, Object author, ComponentText text, String[] data, boolean velocity) {
        if (!ProxyEnvelope.isMarked(data)) {
            BukkitUtilKt.sendComponent(target, author, text);
            return;
        }

        var bridge = current;
        Player receiver = recipient(target);
        if (bridge == null || !velocity || bridge.proxyKey == null || receiver == null
                || !(author instanceof UUID uuid)) return;

        var origin = ProxyEnvelope.open(data, bridge.proxyKey, System.currentTimeMillis());
        if (origin == null || !bridge.enabled.contains(origin.channel())
                || !(bridge.backend.equals(origin.backend()) || bridge.peers.contains(origin.backend()))
                || data[7].isBlank()) return;

        bridge.deliver(receiver, target, author, text,
                new Routed(new ChannelWire.Key(origin.backend(), origin.channel()), origin.deliveryId(),
                        uuid, data[7], text.toRawMessage(), true));
    }

    private void deliver(Player receiver, Object target, Object author, ComponentText original, Routed route) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> deliver(receiver, target, author, original, route));
            return;
        }
        if (current != this || !online(receiver)) return;
        Channel channel = resolve(route.key().channel());
        Peer peer = sessions.get(receiver.getUniqueId());
        boolean negotiated = peer != null && peer.player == receiver && peer.nonce != null;
        if (channel == null || !eligible(receiver, channel)) {
            if (negotiated) snapshot(peer, false);
            return;
        }
        if (receiver.getClientOption(ClientOption.CHAT_VISIBILITY) != ClientOption.ChatVisibility.FULL) return;
        if (route.remote()) {
            if (receipts.size() >= MAX_REPLAYS) return;
            var receipt = new Receipt(route.id(), receiver.getUniqueId());
            if (receipts.putIfAbsent(receipt, System.currentTimeMillis() + 22_000) != null) return;
        }
        if (!negotiated) {
            BukkitUtilKt.sendComponent(target, author, original);
            return;
        }
        if (!snapshot(peer, false)) return;
        try {
            send(receiver, new ChannelWire.Delivery(peer.session, peer.nonce, ++peer.sequence,
                    route.key(), route.id(), route.author(), route.name(), route.json()));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // If the component cannot be encoded, do not route it into ordinary chat.
        }
    }
}
