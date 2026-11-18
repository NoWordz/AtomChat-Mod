package com.atom.chat.settings.contract;

import com.atom.lib.frontend.ContractPage;
import com.atom.lib.frontend.StrictPage;
import com.atom.lib.frontend.contract.ContractRegistry;
import java.util.List;
import java.util.Map;

/** Business bindings for the storage and retention fields on the administration hosting tab. */
public final class HostingFieldsPage extends ContractPage {
    public record Field(String id, String recipe, StrictPage.Content content) {}
    public record Section(String id, List<Field> fields) {
        public Section { fields = List.copyOf(fields); }
    }

    private final List<Section> sections;

    public HostingFieldsPage(List<Section> sections) {
        this.sections = List.copyOf(sections);
    }

    public static Field toggle(String id, String title, String description, StrictPage.BooleanInput value, boolean enabled) {
        return new Field(id, "toggle", new StrictPage.Content(Map.of(
                "title", new StrictPage.Text(title),
                "description", new StrictPage.Text(description),
                "value", value), enabled));
    }

    public static Field number(String id, String title, String description, String unit, StrictPage.TextInput value,
                               boolean invalid, boolean enabled) {
        return new Field(id, "number", new StrictPage.Content(Map.of(
                "title", new StrictPage.Text(title),
                "description", new StrictPage.Text(description),
                "value", value,
                "unit", new StrictPage.Text(unit),
                "invalid", new StrictPage.BooleanValue(invalid)), enabled));
    }

    @Override
    public StrictPage build(ContractRegistry.PageHandle page) {
        var result = StrictPage.builder(page);
        for (Section section : sections) {
            result.group(section.id(), page.recipe("fieldGroup"), group -> {
                for (Field field : section.fields()) {
                    group.component(field.id(), page.recipe(field.recipe()), field.content());
                }
            });
        }
        return result.build();
    }
}
