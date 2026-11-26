package com.atom.chat.net;

import com.atom.chat.config.AdminProtocol;
import com.atom.chat.pack.PublicChatAvatar;
import com.atom.chat.pack.ServerPackStore;

/** Public avatar delivery is opt-in per connection and independent of legacy pack icons. */
final class PublicAvatarSubscription {
    private String watchId;

    AdminProtocol.Request update(boolean receive, boolean available) {
        if (receive && available) {
            if (watchId != null) return null;
            var watch = request("public_avatar_watch", null);
            watchId = watch.requestId();
            return watch;
        }
        if (watchId == null) return null;
        String previous = watchId;
        reset();
        return available ? request("public_avatar_unwatch", previous) : null;
    }

    boolean receive(AdminProtocol.Reply reply, boolean enabled) {
        if (!"public_avatar".equals(reply.action()) && !"public_avatar_watch_error".equals(reply.action())
                && !"public_avatar_unwatch_error".equals(reply.action())) return false;

        if (!enabled || watchId == null || !watchId.equals(reply.requestId())) return true;
        var asset = reply.asset();
        if (!reply.ok() || asset == null || !PublicChatAvatar.FILE_NAME.equals(asset.name())) {
            ServerPackStore.clearPublicAvatar();
            return true;
        }
        try {
            byte[] png = PublicChatAvatar.decodeUpload(asset.data());
            if (png.length == asset.size()) {
                ServerPackStore.installPublicAvatar(asset.hash(), png);
            } else {
                ServerPackStore.clearPublicAvatar();
            }
        } catch (IllegalArgumentException invalid) {
            ServerPackStore.clearPublicAvatar();
        }
        return true;
    }

    void reset() {
        watchId = null;
        ServerPackStore.clearPublicAvatar();
    }

    private static AdminProtocol.Request request(String action, String fingerprint) {
        return new AdminProtocol.Request(action, 0, fingerprint, null, null, null);
    }
}
