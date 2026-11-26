package com.atom.chat.api.ui;

import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SettingsUiTest {
    @Test
    void addonTabUsesItsOwnFoldableGroupAndBoundToggle() {
        String id = "example:recipes";
        var tabs = SettingsUi.tabs(SettingsSection.CHAT);
        var groups = SettingsUi.groups(SettingsSection.CHAT, id);
        var active = new AtomicBoolean(true);
        tabs.add(id, new SettingsUi.Chip("example.settings.recipes"), UiContributions.Position.first());
        groups.add("example:options", new SettingsUi.Group("example.settings.recipes.options",
                List.of(new SettingsUi.Toggle("example:show_lookup", "example.settings.lookup",
                        "example.settings.lookup.desc", active::get, active::set))), UiContributions.Position.last());
        try {
            var page = new SettingsSectionPage();
            assertEquals(id, page.chips(SettingsSection.CHAT).get(0).id());
            assertFalse(page.canResetActiveTab(SettingsSection.CHAT));
            var rows = page.rows(SettingsSection.CHAT);
            assertEquals(List.of(SettingsSectionPage.RowKind.LABEL, SettingsSectionPage.RowKind.SWITCH),
                    rows.stream().map(SettingsSectionPage.Row::kind).toList());
            assertTrue(page.perform(new SettingsSectionPage.RowHit(rows.get(1), 1, 0, 0, 10, 10, 0)));
            assertFalse(active.get());
            assertTrue(page.perform(new SettingsSectionPage.RowHit(rows.get(0), 0, 0, 0, 10, 10, 0)));
            assertEquals(1, page.rows(SettingsSection.CHAT).size(), "Fold the add-on row into its header");
            page.selectChip(SettingsSection.CHAT, 1);
            assertTrue(page.canResetActiveTab(SettingsSection.CHAT));
        } finally {
            groups.unregister("example:options");
            tabs.unregister(id);
        }
    }
}
