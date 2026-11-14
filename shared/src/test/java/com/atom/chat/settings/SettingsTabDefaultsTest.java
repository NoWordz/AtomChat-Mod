package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTabDefaultsTest {
    @Test void displayRestoresItsWholeTabAndLeavesOtherTabsAndPersonalDataAlone() {
        var config = new AtomChatConfig();
        config.blurStrength = .5F; config.blurEnabled = false;
        config.panelOutline = false; config.panelOutlineWidth = 0;
        config.animationEnabled = false;
        config.accentColor = 0xFF18A682; config.panelWidth = 630; config.contentScale = 1.3F;
        config.enabled = false; config.blockedPlayers = List.of("Keep");
        var saves = new AtomicInteger();
        assertTrue(SettingsTabDefaults.reset(config, SettingsSection.APPEARANCE, "display", () -> { saves.incrementAndGet(); return true; }));
        assertEquals(AtomChatConfig.DEFAULT.blurStrength, config.blurStrength);
        assertEquals(AtomChatConfig.DEFAULT.blurEnabled, config.blurEnabled);
        assertEquals(AtomChatConfig.DEFAULT.panelOutlineWidth, config.panelOutlineWidth);
        assertEquals(AtomChatConfig.DEFAULT.panelOutline, config.panelOutline);
        assertEquals(0xFF18A682, config.accentColor);
        assertEquals(630, config.panelWidth); assertEquals(1.3F, config.contentScale);
        assertFalse(config.enabled); assertEquals(List.of("Keep"), config.blockedPlayers);
        assertEquals(1, saves.get());
    }
    @Test void failedSaveRollsBackTheTabAndUnknownTabCannotResetAnything() {
        var config = new AtomChatConfig(); config.blurStrength = .5F; config.blurEnabled = false;
        assertFalse(SettingsTabDefaults.reset(config, SettingsSection.APPEARANCE, "display", () -> false));
        assertEquals(.5F, config.blurStrength); assertFalse(config.blurEnabled);
        assertFalse(SettingsTabDefaults.reset(config, SettingsSection.APPEARANCE, "missing", () -> fail("unknown tab must not save")));
    }
    @Test void notificationEditorSettingsResetButChatHistoryAndPersonalRecordsRemain() {
        var config = new AtomChatConfig();
        config.notificationHudScale = 1.8F; config.notificationHudOffsetX = 70;
        config.chatHistoryEnabled = true; config.historyRetentionDays = 90;
        config.playerSignature = "Keep my signature";
        assertTrue(SettingsTabDefaults.reset(config, SettingsSection.CHAT, "notify", () -> true));
        assertEquals(AtomChatConfig.DEFAULT.notificationHudScale, config.notificationHudScale);
        assertEquals(AtomChatConfig.DEFAULT.notificationHudOffsetX, config.notificationHudOffsetX);
        assertTrue(config.chatHistoryEnabled); assertEquals(90, config.historyRetentionDays);
        assertEquals("Keep my signature", config.playerSignature);
    }
}
