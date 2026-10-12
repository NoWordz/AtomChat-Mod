package com.atom.chat.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HostedImageLimitTest {
    @Test void legacyAndHugeFileSettingsAreClampedWithoutIntegerOverflow() {
        AtomChatServerConfig config = new AtomChatServerConfig();
        config.maxFileKb = 65536;
        assertEquals(16 * 1024 * 1024, config.maxFileBytes());
        config.maxFileKb = Integer.MAX_VALUE;
        assertEquals(16 * 1024 * 1024, config.maxFileBytes());
        config.maxFileKb = 16384;
        assertEquals(16 * 1024 * 1024, config.maxFileBytes());
        config.maxFileKb = 2048;
        assertEquals(2 * 1024 * 1024, config.maxFileBytes());
        config.maxFileKb = 0;
        assertEquals(1024, config.maxFileBytes());
    }
    @Test void legacyFileLimitNormalizesBeforeItReachesTheConfigUi() {
        AtomChatServerConfig config = new AtomChatServerConfig();
        config.maxFileKb = 65536;
        config.normalizeFileLimit();
        assertEquals(16384, config.maxFileKb);
        assertEquals(16384, ServerConfigValues.of(config).maxFileKb());
        assertNull(ServerConfigValues.of(config).validate());
        config.maxFileKb = 2048;
        config.normalizeFileLimit();
        assertEquals(2048, ServerConfigValues.of(config).maxFileKb());
    }

    @Test void remoteConfigEditingCannotRestoreTheOld64MiBLimit() {
        AtomChatServerConfig config = new AtomChatServerConfig();
        config.maxFileKb = 16384;
        assertNull(ServerConfigValues.of(config).validate());
        config.maxFileKb = 16385;
        assertEquals(ServerConfigValues.ERR_RANGE, ServerConfigValues.of(config).validate());
    }
}
