package com.atom.chat.net;

import com.atom.chat.config.ServerConfigPage;
import com.atom.chat.screen.AtomChatScreen;
import net.minecraft.client.Minecraft;

/** Client bridge for the command-only server settings detail page. */
public final class ConfigScreenClient {
    private static ServerConfigPage open;
    private ConfigScreenClient() {}
    private static boolean active() {
        return Minecraft.getInstance().screen instanceof AtomChatScreen panel && panel.serverConfigPage() == open;
    }
    public static ServerConfigPage page(){return open!=null&&active()?open:null;}
    public static void onAdministration(com.atom.chat.config.AdminProtocol.Snapshot snapshot){
        if(open!=null&&active()&&(open.draft().dirty()||open.draft().saving()))return;
        openSnapshot(new ConfigPayloads.Snapshot(snapshot.version(),snapshot.values(),new ConfigPayloads.PackStatus(0,0,"")));
        if(open!=null)open.resources(snapshot.fingerprint(),snapshot.assets());
    }
    public static void onSnapshot(ConfigPayloads.Snapshot payload) {
        if(ConfigAdminClientTransport.available()){ConfigAdminClient.request("open");return;}
        openSnapshot(payload);
    }
    private static void openSnapshot(ConfigPayloads.Snapshot payload){
        Minecraft client = Minecraft.getInstance();
        if (open != null && active()) {
            if (open.acceptSnapshot(payload.configVersion(), payload.values())) { onStatus(payload.pack()); return; }
            if (open.draft().dirty() || open.draft().saving()) return;
        }
        AtomChatScreen panel = client.screen instanceof AtomChatScreen current ? current : new AtomChatScreen("");
        if(open!=null)open.dispose();
        open = new ServerConfigPage(new ServerConfigPage.Host() {
            public String label(String key) { return net.minecraft.network.chat.Component.translatable(key).getString(); }
            public void save(int version, com.atom.chat.config.ServerConfigValues values) { sendSave(version, values); }
            public void saveDraft(int version,com.atom.chat.config.ServerConfigValues values,java.util.List<com.atom.chat.config.AdminAsset> assets,String fingerprint){if(ConfigAdminClientTransport.available())ConfigAdminClient.save(version,values,assets,fingerprint);else save(version,values);}
            public boolean administration(){return ConfigAdminClientTransport.available();}
            public void requestAsset(com.atom.chat.config.AdminAsset asset,java.util.function.Consumer<byte[]> receive){ConfigAdminClient.file(asset,receive);}
            public void retrySync(){ConfigAdminClient.request("retry");com.atom.chat.net.PackSyncClient.resync();}
            public void chooseImage(java.util.function.Consumer<java.nio.file.Path> receive){panel.pickServerConfigImage(receive);}
            public void refresh() { if (client.player != null) client.player.connection.sendCommand("atomchat gui"); }
            public void rescan() { ConfigScreenClient.rescan(); }
            public void back() { panel.closeServerConfig(); open = null; }
            public String clipboard() { return client.keyboardHandler.getClipboard(); }
            public void clipboard(String text) { client.keyboardHandler.setClipboard(text); }
        }, payload.configVersion(), payload.values());
        if (client.screen != panel) client.setScreen(panel);
        panel.openServerConfig(open);
        onStatus(payload.pack());
    }
    private static void sendSave(int version, com.atom.chat.config.ServerConfigValues values) {
        ConfigPayloads.sendSave(new ConfigPayloads.Save(version, values));
    }
    public static void rescan() { ConfigPayloads.sendRefresh(); }
    public static void onStatus(ConfigPayloads.PackStatus status) {
        if (open != null) open.status(net.minecraft.network.chat.Component.translatable("atomchat.config.pack_status", status.files(), status.bytes()/1024).getString());
    }
    public static void onResult(ConfigPayloads.Result result) {
        if (open != null && active()) open.result(result.ok(), result.error());
        if (result.ok()) PackSyncClient.resync();
    }
    public static void managementError(String reason){var client=Minecraft.getInstance();if(client.player!=null)client.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("atomchat.admin.error."+reason),false);}
    public static void onDisconnect() { if(open!=null)open.dispose();open = null; }
}
