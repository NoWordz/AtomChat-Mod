package com.atom.chat.net;

import com.atom.chat.config.AdminAsset;
import com.atom.chat.config.AdminProtocol;
import com.atom.chat.pack.PublicChatAvatar;
import com.atom.chat.pack.ServerPackStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PublicAvatarSubscriptionTest {
    @TempDir Path directory;

    @Test
    void enablingReceptionAfterJoinRequestsIndependentImageAndRejectsOldOrInvalidReplies() throws Exception {
        var subscription = new PublicAvatarSubscription();
        var source = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB), "png", source);
        Path file = directory.resolve(PublicChatAvatar.FILE_NAME);
        PublicChatAvatar.save(file, "", source.toByteArray());
        byte[] png = PublicChatAvatar.read(file);
        var asset = AdminAsset.imported(PublicChatAvatar.FILE_NAME, png);
        try {
            assertNull(subscription.update(false, true));
            assertNull(subscription.update(true, false));

            var first = subscription.update(true, true);
            assertEquals("public_avatar_watch", first.action());
            assertNull(subscription.update(true, true));
            assertTrue(subscription.receive(publication(first.requestId(), asset), true));
            assertArrayEquals(png, ServerPackStore.publicAvatar().png());

            var cancel = subscription.update(false, true);
            assertEquals("public_avatar_unwatch", cancel.action());
            assertEquals(first.requestId(), cancel.fingerprint());
            assertNull(ServerPackStore.publicAvatar());

            var next = subscription.update(true, true);
            assertNotEquals(first.requestId(), next.requestId());
            assertTrue(subscription.receive(publication(first.requestId(), asset), true));
            assertNull(ServerPackStore.publicAvatar());

            var invalid = new AdminAsset(asset.name(), "wrong hash", asset.size(), asset.data());
            assertTrue(subscription.receive(publication(next.requestId(), invalid), true));
            assertNull(ServerPackStore.publicAvatar());
            assertTrue(subscription.receive(publication(next.requestId(), asset), true));
            assertNotNull(ServerPackStore.publicAvatar());

            subscription.reset();
            var legacy = subscription.update(true, true);
            var error = new AdminProtocol.Reply("public_avatar_watch_error", false, "failed",
                    null, null, null).correlate(legacy.requestId());
            assertTrue(subscription.receive(error, true), "legacy management errors belong to this optional subscription only");
            assertNull(ServerPackStore.publicAvatar());
            assertNull(subscription.update(true, true), "no repeated requests to an old server");
        } finally {
            subscription.reset();
        }
    }

    private static AdminProtocol.Reply publication(String id, AdminAsset asset) {
        return new AdminProtocol.Reply("public_avatar", true, "", null, asset, null).correlate(id);
    }
}
