package com.atom.chat.image;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

import com.mojang.authlib.GameProfile;

/**
 * Player-head skin resolution with merged UUID + name caches.
 * Ported from e33chat's SkinResolver (Fabric 1.21.1 Yarn).
 */
public final class SkinResolver {
    private SkinResolver() {
    }

    private static final int SKIN_CACHE_CAP = 256;
    private static final UUID NIL_UUID = new UUID(0, 0);

    private static final Map<UUID, Identifier> skinCache = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Identifier> eldest) {
            return size() > SKIN_CACHE_CAP;
        }
    };

    private static final Map<String, Identifier> skinNameCache = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Identifier> eldest) {
            return size() > SKIN_CACHE_CAP;
        }
    };

    private static String skinNameKey(String name) {
        return SkinNameCacheKey.of(name);
    }

    private static void rememberSkin(UUID uuid, String name, Identifier tex) {
        if (tex == null) {
            return;
        }
        if (uuid != null && !uuid.equals(NIL_UUID)) {
            skinCache.put(uuid, tex);
        }
        String key = skinNameKey(name);
        if (key != null) {
            skinNameCache.put(key, tex);
        }
    }

    public static Identifier getSkin(UUID uuid, String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Online players: read PlayerListEntry fresh every frame. The entry kicks off
        // an async skin download on first access; caching that first (default) result
        // would freeze the head on Steve/Alex forever.
        // No client at all: there is nobody to ask, so fall through to the caches
        // and the default skin instead of dereferencing it. Every other client
        // access in the avatar layer already tolerates this.
        if (client != null && client.getNetworkHandler() != null && uuid != null && !uuid.equals(NIL_UUID)) {
            PlayerListEntry info = client.getNetworkHandler().getPlayerListEntry(uuid);
            if (info != null) {
                Identifier tex = info.getSkinTextures().texture();
                rememberSkin(uuid, name, tex);
                return tex;
            }
        }
        if (uuid != null && !uuid.equals(NIL_UUID)) {
            Identifier cached = skinCache.get(uuid);
            if (cached != null) {
                return cached;
            }
        }
        String nameKey = skinNameKey(name);
        if (nameKey != null) {
            Identifier cachedByName = skinNameCache.get(nameKey);
            if (cachedByName != null) {
                return cachedByName;
            }
        }
        Identifier resolved = resolveSkin(uuid, name);
        rememberSkin(uuid, name, resolved);
        return resolved;
    }

    private static Identifier resolveSkin(UUID uuid, String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (name != null && !name.isEmpty()) {
            try {
                GameProfile profile = new GameProfile(
                        uuid != null && !uuid.equals(NIL_UUID) ? uuid : NIL_UUID, name);
                return client.getSkinProvider().getSkinTextures(profile).texture();
            } catch (Exception ignored) {
            }
        }
        return DefaultSkinHelper.getTexture();
    }
}
