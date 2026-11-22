package com.atom.chat.compat;

import net.fabricmc.loader.api.Version;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FabricAtomLibVersionTest {
    @Test void enforcesBothEndsOfThePackagedClientRange() throws Exception {
        String range = AtomLibDependencyCheck.fabricRange();
        String required = AtomLibDependencyCheck.requiredVersion();
        for (String version : new String[]{"0.6.0", required + "-beta.1", "0.8.0", "1.0.0"}) {
            var installed = Version.parse(version);
            var error = assertThrows(IllegalStateException.class,
                    () -> FabricAtomLibVersion.requireCompatible(installed, range));
            assertTrue(error.getMessage().contains(version));
            assertTrue(error.getMessage().contains(range));
        }
        assertDoesNotThrow(() -> FabricAtomLibVersion.requireCompatible(Version.parse(required), range));
        assertDoesNotThrow(() -> FabricAtomLibVersion.requireCompatible(Version.parse("0.7.9"), range));
    }
}
