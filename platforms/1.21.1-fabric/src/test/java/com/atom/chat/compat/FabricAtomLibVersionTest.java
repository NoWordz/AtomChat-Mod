package com.atom.chat.compat;

import net.fabricmc.loader.api.Version;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FabricAtomLibVersionTest {
    @Test void enforcesThePackagedClientMinimumWithoutAnUpperLimit() throws Exception {
        String range = AtomLibDependencyCheck.fabricRange();
        String required = AtomLibDependencyCheck.requiredVersion();
        assertEquals("0.8.0", required);
        assertEquals(">=0.8.0", range);
        for (String version : new String[]{"0.7.1", "0.7.9", "0.8.0-beta.1"}) {
            var installed = Version.parse(version);
            var error = assertThrows(IllegalStateException.class,
                    () -> FabricAtomLibVersion.requireCompatible(installed, range));
            assertTrue(error.getMessage().contains(version));
            assertTrue(error.getMessage().contains(range));
        }
        for (String version : new String[]{"0.8.0", "0.8.1", "0.9.0", "1.0.0"}) {
            var installed = Version.parse(version);
            assertDoesNotThrow(() -> FabricAtomLibVersion.requireCompatible(installed, range));
        }
    }
}
