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
    @Test
    void groupRowsCanReorderAndReplaceNativeControls() {
        String label = "atomchat.settings.group.chat.input";
        var edits = SettingsUi.rows(SettingsSection.CHAT, SettingsUi.builtInTabId("messages"),
                SettingsUi.builtInGroupId(label));
        edits.move("example:retain_first", SettingsUi.builtInRowId("retain_draft_on_close"),
                UiContributions.Position.first());
        edits.replace("example:replace_close", SettingsUi.builtInRowId("close_after_send"),
                new SettingsUi.Info("example.notice", "example.notice.desc"));
        try {
            var page = new SettingsSectionPage();
            var rows = page.rows(SettingsSection.CHAT);
            int index = 0;
            while (!label.equals(rows.get(index).labelKey())) index++;
            assertEquals("retain_draft_on_close", rows.get(index + 1).item().id());
            assertEquals("example.notice", rows.get(index + 2).info().titleKey());
            assertTrue(page.rows(SettingsSection.CHAT).stream().anyMatch(row ->
                            row.item() != null && row.item().id().equals("chat_hud")),
                    "Editing one group preserves neighbouring native controls on repeated resolves");
        } finally {
            edits.unregister("example:retain_first");
            edits.unregister("example:replace_close");
        }
    }

    @Test
    void twoAddonGroupsWithTheSameLabelFoldIndependently() {
        var groups = SettingsUi.groups(SettingsSection.CHAT, SettingsUi.builtInTabId("messages"));
        String label = "example.same_label";
        var toggle = new SettingsUi.Toggle("example:option", "example.opt", "example.opt.desc",
                () -> true, value -> {});
        groups.add("example:first", new SettingsUi.Group(label, List.of(toggle)), UiContributions.Position.last());
        groups.add("example:second", new SettingsUi.Group(label, List.of(toggle)), UiContributions.Position.last());
        try {
            var page = new SettingsSectionPage();
            var rows = page.rows(SettingsSection.CHAT);
            int index = 0;
            while (!label.equals(rows.get(index).labelKey())) index++;
            assertTrue(page.perform(new SettingsSectionPage.RowHit(rows.get(index), index, 0, 0, 10, 10, 0)));
            assertEquals(rows.size() - 1, page.rows(SettingsSection.CHAT).size());
        } finally {
            groups.unregister("example:first");
            groups.unregister("example:second");
        }
    }
}
