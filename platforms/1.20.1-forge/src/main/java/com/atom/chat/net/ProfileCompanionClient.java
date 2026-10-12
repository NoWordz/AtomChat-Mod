package com.atom.chat.net;
import net.minecraft.client.Minecraft;
/** Client protocol adapter and publication acknowledgements. */
public final class ProfileCompanionClient {

    private record Pending(java.util.function.Consumer<String> result, long deadline) {}
    private static final java.util.Map<Long, Pending> pending = new java.util.HashMap<>();
    private static final com.atom.chat.profile.ProfileRequestLedger requests =
            new com.atom.chat.profile.ProfileRequestLedger(System::currentTimeMillis);
    private static long sequence;
    private static boolean ownReceived;
    private static boolean imagesPending;
    private static long imagesDeadline;
    private static final com.atom.chat.image.ImageUploader external = new com.atom.chat.image.ImageUploader();
    public static void init() {
        // S2C handlers are registered by the dist-guarded common channel.
        com.atom.chat.profile.ProfileSync.install(new com.atom.chat.profile.ProfileTransport() {
            public boolean available() { return ProfileCompanionClient.available(); }
            public boolean hostingEnabled() { return MediaCompanionClient.hostingEnabled(); }
            public void request(java.util.UUID uuid) {
                if (!available()) return;
                long token = requests.issue(uuid);
                try { ProfilePayloads.CHANNEL.sendToServer(new ProfilePayloads.ProfileRequestPayload(uuid, token)); }
                catch (RuntimeException e) { requests.cancel(token); }
            }
            public void submit(com.atom.chat.profile.ProfilePatch patch, java.util.function.Consumer<String> result) {
                ProfileCompanionClient.submit(patch, result);
            }
            public void uploadHosted(java.nio.file.Path file, java.util.function.Consumer<String> success, java.util.function.Consumer<String> error) {
                MediaCompanionClient.uploadFile(file, success, error);
            }
            public void uploadExternal(java.nio.file.Path file, java.util.function.Consumer<String> success, java.util.function.Consumer<String> error) {
                external.upload(file, success, error);
            }
            public void execute(Runnable task) { Minecraft.getInstance().execute(task); }
            public void feedback(boolean success) {
                var client = Minecraft.getInstance();
                if (!success && client.player != null) client.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("atomchat.profile.unsynced"), true);
            }
        });
    }
    private static boolean available() {
        try {
            var client = Minecraft.getInstance();
            if (client == null || client.player == null) return false;
            var connection = client.getConnection();
            return ProfilePayloads.CHANNEL != null && connection != null
                    && ProfilePayloads.CHANNEL.isRemotePresent(connection.getConnection());
        } catch (RuntimeException e) { return false; }
    }
    public static void onJoin() {
        pending.clear(); requests.clear(); ownReceived = false; imagesPending = false;
        com.atom.chat.profile.ProfileSync.clearConnection();
    }
    public static void onLeave() { onJoin(); }
    public static void tick() {
        long now = System.currentTimeMillis();
        for (var iterator = pending.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            if (now >= entry.getValue().deadline()) {
                var callback = entry.getValue().result(); iterator.remove(); callback.accept("timeout");
            }
        }
        requests.expire();
        if (!available()) return;
        var client = Minecraft.getInstance();
        if (!ownReceived) com.atom.chat.profile.ProfileSync.record(client.player.getUUID());
        if (imagesPending && (MediaCompanionClient.hostingStateKnown() || now >= imagesDeadline)) {
            imagesPending = false;
            com.atom.chat.profile.ProfileSync.synchronizeImages();
        }
    }
    private static void submit(com.atom.chat.profile.ProfilePatch patch, java.util.function.Consumer<String> result) {
        if (!available()) { result.accept("unavailable"); return; }
        if (pending.size() >= 32) { result.accept("busy"); return; }
        long token = ++sequence;
        pending.put(token, new Pending(result, System.currentTimeMillis() + 15000));
        try { ProfilePayloads.CHANNEL.sendToServer(new ProfilePayloads.ProfileUpdatePayload(token, patch)); }
        catch (RuntimeException e) {
            pending.remove(token); result.accept("send_failed");
        }
    }
    public static void onData(ProfilePayloads.ProfileDataPayload payload) {
        Pending acknowledgement = null;
        if (payload.token() < 0) {
            if (!requests.accept(payload.token(), payload.uuid())) return;
        } else if (payload.token() > 0) {
            acknowledgement = pending.get(payload.token());
            var current = Minecraft.getInstance();
            if (acknowledgement == null || current.player == null
                    || !current.player.getUUID().equals(payload.uuid())) return;
            pending.remove(payload.token());
        } else return;
        if (payload.error().isEmpty()) {
            com.atom.chat.profile.ProfileRecord record;
            try {
                record = new com.atom.chat.profile.ProfileRecord(payload.uuid(), payload.signature(),
                        payload.avatar(), payload.banner(), payload.revision(), payload.imageFields());
                com.atom.chat.profile.ProfileSync.received(record);
            }
            catch (IllegalArgumentException e) {
                if (acknowledgement != null) acknowledgement.result().accept("invalid_data");
                return;
            }
            var current = Minecraft.getInstance();
            if (payload.token() < 0 && !ownReceived && current.player != null
                    && current.player.getUUID().equals(payload.uuid())) {
                ownReceived = true; imagesPending = true;
                imagesDeadline = System.currentTimeMillis() + 5000;
                com.atom.chat.profile.ProfileSync.synchronizeSignature(record);
            }
        }
        if (acknowledgement != null) acknowledgement.result().accept(payload.error());
    }
    public static void onChanged(java.util.UUID uuid) { com.atom.chat.profile.ProfileSync.changed(uuid); }

}
