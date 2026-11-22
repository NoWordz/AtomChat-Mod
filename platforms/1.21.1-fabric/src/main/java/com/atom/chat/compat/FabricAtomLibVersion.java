package com.atom.chat.compat;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/** Fabric marks AtomLib as a suggestion so dedicated servers can run on their own. */
public final class FabricAtomLibVersion {
    private FabricAtomLibVersion() {}

    public static void requireCompatible(Version installed, String range) {
        try {
            if (!VersionPredicate.parse(range).test(installed)) {
                throw new IllegalStateException("AtomChat client requires AtomLib " + range
                        + " for this Minecraft version and loader. Installed: " + installed.getFriendlyString());
            }
        } catch (VersionParsingException error) {
            throw new IllegalStateException("Invalid AtomLib version range: " + range, error);
        }
    }
}
