package com.atom.chat.compat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtomLibDependencyCheckTest {
    @Test void missingAndWrongVersionsExplainTheRequiredCompanion() {
        var missing=assertThrows(IllegalStateException.class,()->AtomLibDependencyCheck.validate(null,"0.1.0-local","Windows 11","amd64"));
        assertTrue(missing.getMessage().contains("AtomLib"));assertTrue(missing.getMessage().contains("0.1.0-local"));
        assertThrows(IllegalStateException.class,()->AtomLibDependencyCheck.validate("0.0.1","0.1.0-local","Windows 11","amd64"));
    }
    @Test void supportedJvmArchitecturesAreAcceptedAndUnsupportedClientsFailEarly() {
        for(String arch:new String[]{"amd64","x86_64","aarch64","arm64"})assertDoesNotThrow(()->AtomLibDependencyCheck.validate("0.1.0-local","0.1.0-local","Windows 11",arch));
        assertThrows(IllegalStateException.class,()->AtomLibDependencyCheck.validate("0.1.0-local","0.1.0-local","Linux","x86_64"));
        assertThrows(IllegalStateException.class,()->AtomLibDependencyCheck.validate("0.1.0-local","0.1.0-local","Windows 11","x86"));
    }
}
