package com.atom.chat.net;

import com.atom.chat.config.ServerConfigPage;
import net.minecraft.client.gui.screen.AtomChatScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/** Client bridge for the command-only server settings detail page. */
public final class ConfigScreenClient {
    private static ServerConfigPage open;
    private ConfigScreenClient() {}
    public static void init() {
        ConfigAdminClientTransport.initClient();
        ClientPlayNetworking.registerGlobalReceiver(ConfigPayloads.Snapshot.ID, (payload, context) -> context.client().execute(() -> onSnapshot(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ConfigPayloads.Status.ID, (payload, context) -> context.client().execute(() -> onStatus(payload.pack())));
        ClientPlayNetworking.registerGlobalReceiver(ConfigPayloads.Result.ID, (payload, context) -> context.client().execute(() -> onResult(payload)));
    }
    private static boolean active() {
        return MinecraftClient.getInstance().currentScreen instanceof AtomChatScreen panel && panel.serverConfigPage() == open;
    }
    public static ServerConfigPage page(){return open!=null&&active()?open:null;}
    public static void onAdministration(com.atom.chat.config.AdminProtocol.Snapshot snapshot){
        if(open!=null&&active()&&(open.draft().dirty()||open.draft().saving()))return;
        openSnapshot(new ConfigPayloads.Snapshot(snapshot.version(),snapshot.values(),new ConfigPayloads.PackStatus(0,0,"")));
        if (open != null) {
            open.resources(snapshot.fingerprint(), snapshot.assets());
            open.publicAvatarSnapshot(snapshot.publicAvatarRevision());
        }
    }
    public static void onSnapshot(ConfigPayloads.Snapshot payload) {
        if(ConfigAdminClientTransport.available()){ConfigAdminClient.request("open");return;}
        openSnapshot(payload);
    }
    private static void openSnapshot(ConfigPayloads.Snapshot payload){
        MinecraftClient client = MinecraftClient.getInstance();
        if (open != null && active()) {
            if (open.acceptSnapshot(payload.configVersion(), payload.values())) { onStatus(payload.pack()); return; }
            if (open.draft().dirty() || open.draft().saving()) return;
        }
        AtomChatScreen panel = client.currentScreen instanceof AtomChatScreen current ? current : new AtomChatScreen("");
        if(open!=null)open.dispose();
        open = new ServerConfigPage(new ServerConfigPage.Host() {
            public String label(String key) { return net.minecraft.text.Text.translatable(key).getString(); }
            public void save(int version, com.atom.chat.config.ServerConfigValues values) { sendSave(version, values); }
            public void saveDraft(int version,com.atom.chat.config.ServerConfigValues values,java.util.List<com.atom.chat.config.AdminAsset> assets,String fingerprint){if(ConfigAdminClientTransport.available())ConfigAdminClient.save(version,values,assets,fingerprint);else save(version,values);}
            public boolean administration(){return ConfigAdminClientTransport.available();}
            public void requestAsset(com.atom.chat.config.AdminAsset asset,java.util.function.Consumer<byte[]> receive){ConfigAdminClient.file(asset,receive);}
            public void retrySync(){ConfigAdminClient.request("retry");com.atom.chat.net.PackSyncClient.resync();}
            public void choosePublicAvatar(java.util.function.Consumer<byte[]> receive) {
                panel.pickPublicChatAvatar(open, receive);
            }
            public void savePublicAvatar(ServerConfigPage owner, String revision, byte[] png) {
                ConfigAdminClient.savePublicAvatar(owner, revision, png);
            }
            public void chooseImage(java.util.function.Consumer<java.nio.file.Path> receive){panel.pickServerConfigImage(receive);}
            public void refresh() { if (client.player != null) client.player.networkHandler.sendChatCommand("atomchat gui"); }
            public void rescan() { ConfigScreenClient.rescan(); }
            public void back() { panel.closeServerConfig(); open = null; }
            public String clipboard() { return client.keyboard.getClipboard(); }
            public void clipboard(String text) { client.keyboard.setClipboard(text); }
        }, payload.configVersion(), payload.values());
        if (client.currentScreen != panel) client.setScreen(panel);
        panel.openServerConfig(open);
        onStatus(payload.pack());
    }
    private static void sendSave(int version, com.atom.chat.config.ServerConfigValues values) {
        ClientPlayNetworking.send(new ConfigPayloads.Save(version, values));
    }
    public static void rescan() { ClientPlayNetworking.send(new ConfigPayloads.Refresh()); }
    public static void onStatus(ConfigPayloads.PackStatus status) {
        if (open != null) open.status(net.minecraft.text.Text.translatable("atomchat.config.pack_status", status.files(), status.bytes()/1024).getString());
    }
    public static void onResult(ConfigPayloads.Result result) {
        if (open != null && active()) open.result(result.ok(), result.error());
        if (result.ok()) PackSyncClient.resync();
    }
    public static void managementError(String reason){var client=MinecraftClient.getInstance();if(client.player!=null)client.player.sendMessage(net.minecraft.text.Text.translatable("atomchat.admin.error."+reason),false);}
    public static void onDisconnect() { if(open!=null)open.dispose();open = null; }
}
