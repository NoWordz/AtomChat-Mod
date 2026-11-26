package com.atom.chat.testing;

/** Loads the vanilla registries before tests exercise real GUI feedback sounds. */
public final class MinecraftTestBootstrap {
    private MinecraftTestBootstrap() {}

    public static void ensureInitialized() throws ReflectiveOperationException {
        var constants = Class.forName("net.minecraft.SharedConstants");
        try {
            constants.getMethod("tryDetectVersion").invoke(null);
        } catch (NoSuchMethodException unmapped) {
            constants.getMethod("createGameVersion").invoke(null);
        }
        try {
            Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
        } catch (ClassNotFoundException unmapped) {
            Class.forName("net.minecraft.Bootstrap").getMethod("initialize").invoke(null);
        }
    }
}
