package com.atom.chat.settings.contract;

import com.atom.lib.frontend.ContractPage;
import com.atom.lib.frontend.StrictPage;
import com.atom.lib.frontend.contract.ContractRegistry;
import java.util.Map;

/** Business content for the existing history settings tab. */
public final class HistoryFieldsPage extends ContractPage {
    private final StrictPage.Content enabled;
    private final StrictPage.Content days;
    private final StrictPage.Content cleanup;

    public HistoryFieldsPage(StrictPage.Content enabled, StrictPage.Content days, StrictPage.Content cleanup) {
        this.enabled = enabled;
        this.days = days;
        this.cleanup = cleanup;
    }

    public static StrictPage.Content days(String title, String description, String unit, StrictPage.NumberInput value) {
        return new StrictPage.Content(Map.of("title", new StrictPage.Text(title), "description", new StrictPage.Text(description),
                "unit", new StrictPage.Text(unit), "value", value), true);
    }

    public static StrictPage.Content cleanup(String title, String description, String verb, boolean available, StrictPage.Action openConfirmation) {
        return new StrictPage.Content(Map.of("title", new StrictPage.Text(title), "description", new StrictPage.Text(description),
                "verb", new StrictPage.Text(verb), "activate", openConfirmation), available);
    }

    @Override
    public StrictPage build(ContractRegistry.PageHandle page) {
        return StrictPage.builder(page)
                .group("storageFields", page.recipe("fieldGroup"), group -> group
                        .component("history", page.recipe("historyToggle"), enabled)
                        .component("history_retention", page.recipe("historyNumber"), days))
                .group("cleanupFields", page.recipe("fieldGroup"), group -> group
                        .component("history_clear", page.recipe("historyCleanup"), cleanup))
                .build();
    }
}
