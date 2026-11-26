package com.atom.chat.net;

import com.atom.chat.config.AdminProtocol;
import com.atom.chat.config.AtomChatServerConfig;
import com.atom.chat.pack.PublicChatAvatar;
import com.atom.chat.platform.Platform;
import com.atom.chat.util.CacheDirs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PublicChatAvatarAdminTest {
    @TempDir Path root;

    @Test
    void managementPacketsRequirePermissionAndCurrentRevision() throws Exception {
        Object operator = new Object();
        Object member = new Object();
        Object server = new Object();
        UUID operatorId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Host.installServerSide(new Host.ServerSide() {
            public UUID playerId(Object player) { return player == operator ? operatorId : player == member ? memberId : null; }
            public String playerName(Object player) { return player == operator ? "Operator" : "Member"; }
            public Object playerById(Object origin, UUID id) {
                return origin != server ? null : id.equals(operatorId) ? operator : id.equals(memberId) ? member : null;
            }
            public Object serverOf(Object player) { return server; }
            public String motd(Object origin) { return "Test server"; }
            public boolean dedicatedServer() { return false; }
        });
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root; }
            public boolean isModLoaded(String id) { return false; }
        });
        AtomChatServerConfig.reload();
        var file = CacheDirs.publicChatAvatar();
        var manifests = new ArrayList<PackMessage.Manifest>();
        Net.installServerSide((player, message) -> {
            if (player == member && message instanceof PackMessage.Manifest manifest) manifests.add(manifest);
        });
        byte[] icon = image(64);
        Files.write(root.resolve("server-icon.png"), icon);
        assertTrue(PackSyncServer.syncStates(server).isEmpty(), "receiver joined with server content off");
        PackSyncServer.invalidate();
        PackSyncServer.onHello(member);
        PackSyncServer.pushUpdate(server);
        var offered = manifests.get(manifests.size() - 1);
        assertArrayEquals(icon, offered.icon());
        PackSyncServer.onAck(member, new PackMessage.Ack(true, ""));
        assertEquals("pending", PackSyncServer.syncStates(server).get(memberId));
        send(member, false, "pack_ack", "wrong hash", null);
        assertEquals("pending", PackSyncServer.syncStates(server).get(memberId));
        send(member, false, "pack_ack", offered.packHash(), null);
        assertEquals("ok", PackSyncServer.syncStates(server).get(memberId));
        send(member, false, "pack_failed", offered.packHash(), null);
        assertEquals("ok", PackSyncServer.syncStates(server).get(memberId), "an old duplicate cannot reopen the verdict");

        var png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB), "png", png);
        String encoded = Base64.getEncoder().encodeToString(png.toByteArray());

        send(member, false, "public_avatar_set", "", encoded);
        assertNull(PublicChatAvatar.read(file));
        send(operator, true, "public_avatar_set", "", encoded);
        assertTrue(PublicChatAvatar.isPublished(PublicChatAvatar.read(file)));
        assertArrayEquals(icon, PackSyncServer.peek(server).icon(), "legacy icon is independent of the public chat avatar");
        assertEquals(offered.packHash(), PackSyncServer.peek(server).packHash());

        byte[] first = PublicChatAvatar.read(file);
        String revision = PublicChatAvatar.revision(file);
        send(operator, true, "public_avatar_set", revision, "invalid-data");
        send(operator, true, "public_avatar_reset", "", null);
        send(member, false, "public_avatar_reset", revision, null);
        assertArrayEquals(first, PublicChatAvatar.read(file));

        send(operator, true, "public_avatar_reset", revision, null);
        assertNull(PublicChatAvatar.read(file));
    }

    private static byte[] image(int size) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB), "png", output);
        return output.toByteArray();
    }

    private static void send(Object player, boolean mayEdit, String action, String revision, String data) {
        var request = new AdminProtocol.Request(action, 0, revision, null, null, data);
        AdminProtocol.chunks(AdminProtocol.JSON.toJson(request))
                .forEach(chunk -> ConfigAdminServer.receive(player, chunk, mayEdit));
    }
}
