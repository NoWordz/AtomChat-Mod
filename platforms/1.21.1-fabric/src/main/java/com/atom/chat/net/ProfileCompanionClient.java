package com.atom.chat.net;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
/** Client protocol adapter and publication acknowledgements. */
public final class ProfileCompanionClient {

    private record Pending(java.util.function.Consumer<String> result, long deadline) {}
    private static final java.util.Map<Long, Pending> pending = new java.util.HashMap<>();
    private static final com.atom.chat.profile.ProfileRequestLedger requests =
            new com.atom.chat.profile.ProfileRequestLedger(System::currentTimeMillis);
    private static final com.atom.chat.profile.ProfileRequestLedger roleRequests =
            new com.atom.chat.profile.ProfileRequestLedger(System::currentTimeMillis);
    private static long sequence;
    private static boolean ownReceived;
    private static boolean imagesPending;
    private static long imagesDeadline;
    private static final com.atom.chat.image.ImageUploader external = new com.atom.chat.image.ImageUploader();
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ProfilePayloads.ProfileRolePayload.ID,
                (payload, context) -> context.client().execute(() -> onRole(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ProfilePayloads.ProfileDataPayload.ID,
                (payload, context) -> context.client().execute(() -> onData(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ProfilePayloads.ProfileChangedPayload.ID,
                (payload, context) -> context.client().execute(() -> onChanged(payload.uuid())));
        com.atom.chat.profile.ProfileSync.install(new com.atom.chat.profile.ProfileTransport() {
            public boolean available() { return ProfileCompanionClient.available(); }
            public boolean hostingEnabled() { return MediaCompanionClient.hostingEnabled(); }
            public void request(java.util.UUID uuid) {
                if (!available()) return;
                long token = requests.issue(uuid);
                roleRequests.track(token, uuid);
                try { ClientPlayNetworking.send(new ProfilePayloads.ProfileRequestPayload(uuid, token)); }
                catch (RuntimeException e) { requests.cancel(token); roleRequests.cancel(token); }
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
            public void execute(Runnable task) { MinecraftClient.getInstance().execute(task); }
            public void feedback(boolean success) {
                var client = MinecraftClient.getInstance();
                if (!success && client.player != null) client.player.sendMessage(net.minecraft.text.Text.translatable("atomchat.profile.unsynced"), true);
            }
        });
    }
    private static boolean available() {
        try {
            var client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return false;
            return ClientPlayNetworking.canSend(ProfilePayloads.ProfileRequestPayload.ID);
        } catch (RuntimeException e) { return false; }
    }
    public static void onJoin() {
        pending.clear(); requests.clear(); ownReceived = false; imagesPending = false;
        roleRequests.clear();
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
        roleRequests.expire();
        if (!available()) return;
        var client = MinecraftClient.getInstance();
        if (!ownReceived) com.atom.chat.profile.ProfileSync.record(client.player.getUuid());
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
        try { ClientPlayNetworking.send(new ProfilePayloads.ProfileUpdatePayload(token, patch)); }
        catch (RuntimeException e) {
            pending.remove(token); result.accept("send_failed");
        }
    }
    public static void onRole(ProfilePayloads.ProfileRolePayload payload) {
        if (!roleRequests.accept(payload.token(), payload.uuid())) return;
        com.atom.chat.profile.ProfileSync.receivedOperator(payload.uuid(), payload.token(),
                payload.state() == 0 ? null : payload.state() == 2);
    }

    public static void onData(ProfilePayloads.ProfileDataPayload payload) {
        Pending acknowledgement = null;
        if (payload.token() < 0) {
            if (!requests.accept(payload.token(), payload.uuid())) return;
        } else if (payload.token() > 0) {
            acknowledgement = pending.get(payload.token());
            var current = MinecraftClient.getInstance();
            if (acknowledgement == null || current.player == null
                    || !current.player.getUuid().equals(payload.uuid())) return;
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
            var current = MinecraftClient.getInstance();
            if (payload.token() < 0 && !ownReceived && current.player != null
                    && current.player.getUuid().equals(payload.uuid())) {
                ownReceived = true; imagesPending = true;
                imagesDeadline = System.currentTimeMillis() + 5000;
                com.atom.chat.profile.ProfileSync.synchronizeSignature(record);
            }
        }
        if (acknowledgement != null) acknowledgement.result().accept(payload.error());
    }
    public static void onChanged(java.util.UUID uuid) { com.atom.chat.profile.ProfileSync.changed(uuid); }

}
