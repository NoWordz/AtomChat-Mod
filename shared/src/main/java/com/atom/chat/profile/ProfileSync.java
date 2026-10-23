package com.atom.chat.profile;

import com.atom.chat.avatar.AvatarStore;
import com.atom.chat.banner.BannerStore;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.ImageLoader;

import io.github.humbleui.skija.Image;
import java.nio.file.Path;
import java.util.UUID;

/** UI facade; networking, storage, upload state and cache have separate owners. */
public final class ProfileSync {
    private static ProfileTransport transport;
    private static ProfilePublication publication;
    private static UUID ownUuid;
    private static final ProfileClientCache CACHE = new ProfileClientCache(
            uuid -> { if (transport != null && transport.available()) transport.request(uuid); },
            () -> System.nanoTime() / 1_000_000L);
    private ProfileSync() {}
    public static void install(ProfileTransport adapter) {
        transport = adapter;
        publication = new ProfilePublication(adapter);
        BannerStore.init(com.atom.chat.util.CacheDirs.bannerDir());
    }
    public static void clearConnection() {
        CACHE.clear();
        ownUuid = null;
        if (publication != null) publication.reset();
    }
    /** Releases injected ports after offline tests; connection state is not retained. */
    public static void uninstall() { clearConnection(); transport = null; publication = null; }
    public static boolean available() { return transport != null && transport.available(); }
    public static ProfileRecord record(UUID uuid) { return CACHE.get(uuid); }
    public static void received(ProfileRecord record) { CACHE.accept(record); }
    public static Boolean operator(UUID uuid) { return CACHE.operator(uuid); }
    public static void receivedOperator(UUID uuid, long token, Boolean operator) {
        CACHE.acceptOperator(uuid, token, operator);
    }
    public static void changed(UUID uuid) { CACHE.invalidate(uuid); }
    public static Image image(UUID uuid, ProfileField field) {
        ProfileRecord data = record(uuid);
        String url = field == ProfileField.AVATAR ? data.avatar() : data.banner();
        return url.isEmpty() ? null : ImageLoader.get().get(url, true);
    }
    public static void signatureEdited(String signature) {
        if (publication != null) publication.text(ProfileField.SIGNATURE, signature);
    }
    public static void imageEdited(ProfileField field, Path file) {
        if (publication != null && file != null) {
            rememberImageChoice(field, false);
            publication.image(field, file);
        }
    }
    public static void imageCleared(ProfileField field) {
        if (publication != null) {
            rememberImageChoice(field, true);
            publication.text(field, "");
        }
    }
    public static boolean unsynced() { return publication != null && publication.unsynced(); }
    public static boolean syncing() { return publication != null && publication.syncing(); }
    public static boolean hasOwnImage(ProfileField field) {
        if (ownUuid == null) return false;
        ProfileRecord own = record(ownUuid);
        return !(field == ProfileField.AVATAR ? own.avatar() : own.banner()).isEmpty();
    }
    public static void synchronizeSignature(ProfileRecord own) {
        ownUuid = own.uuid();
        AtomChatConfig cfg = AtomChatConfig.get();
        String local = cfg.playerSignature == null ? "" : cfg.playerSignature;
        if (!cfg.profileSignatureMigrationDone) {
            // Only legacy user-authored local text is promoted to a global choice;
            // text learned from a server must never become an implicit upload.
            cfg.profileSignatureEdited = cfg.profileSignatureEdited || !local.isEmpty();
            cfg.profileSignatureMigrationDone = true;
            AtomChatConfig.save(cfg);
        }
        if (cfg.profileSignatureEdited) signatureEdited(local);
        else {
            cfg.playerSignature = own.signature();
            AtomChatConfig.save(cfg);
        }
    }
    private static void rememberImageChoice(ProfileField field, boolean cleared) {
        AtomChatConfig cfg = AtomChatConfig.get();
        boolean old;
        if (field == ProfileField.AVATAR) { old = cfg.profileAvatarCleared; cfg.profileAvatarCleared = cleared; }
        else if (field == ProfileField.BANNER) { old = cfg.profileBannerCleared; cfg.profileBannerCleared = cleared; }
        else throw new IllegalArgumentException("not_an_image_field");
        if (old != cleared) AtomChatConfig.save(cfg);
    }
    public static void imageSelected(ProfileField field) {
        if (transport != null) rememberImageChoice(field, false);
    }
    public static boolean remoteOnlyImage(ProfileField field) {
        if (!hasOwnImage(field)) return false;
        return field == ProfileField.AVATAR
                ? new AvatarStore(com.atom.chat.util.CacheDirs.avatarDir()).current() == null
                : !BannerStore.isSet();
    }
    public static void synchronizeImages() {
        if (publication == null) return;
        AtomChatConfig cfg = AtomChatConfig.get();
        Path avatar = new AvatarStore(com.atom.chat.util.CacheDirs.avatarDir()).current();
        if (cfg.profileAvatarCleared) publication.text(ProfileField.AVATAR, "");
        else if (avatar != null) imageEdited(ProfileField.AVATAR, avatar);
        if (cfg.profileBannerCleared) publication.text(ProfileField.BANNER, "");
        else if (cfg.customBannerEnabled && BannerStore.current() != null)
            imageEdited(ProfileField.BANNER, BannerStore.current());
    }
}
