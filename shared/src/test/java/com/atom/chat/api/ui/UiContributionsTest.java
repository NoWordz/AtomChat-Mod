package com.atom.chat.api.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiContributionsTest {
    private static final List<UiContributions.Entry<String>> BUILT_INS = List.of(
            new UiContributions.Entry<>("atomchat:tools", "Tools"),
            new UiContributions.Entry<>("atomchat:emoji", "Emoji"),
            new UiContributions.Entry<>("atomchat:send", "Send"));

    @Test
    void defaultLayoutAndExtensionEditsHaveOneOrderedResult() {
        UiContributions<String> slots = new UiContributions<>();
        assertEquals(BUILT_INS, slots.resolve(BUILT_INS));

        slots.add("sample:recipes", "Recipes", UiContributions.Position.after("atomchat:tools"));
        slots.remove("sample:hide_emoji", "atomchat:emoji");
        slots.replace("sample:replace_tools", "atomchat:tools", "Shortcuts");
        slots.move("sample:send_left", "atomchat:send", UiContributions.Position.first());

        assertEquals(List.of(
                new UiContributions.Entry<>("atomchat:send", "Send"),
                new UiContributions.Entry<>("atomchat:tools", "Shortcuts"),
                new UiContributions.Entry<>("sample:recipes", "Recipes")), slots.resolve(BUILT_INS));
    }

    @Test
    void canAnchorToAnAddonThatRegistersLaterAndIgnoresUnknownTargets() {
        UiContributions<String> tabs = new UiContributions<>();
        tabs.add("a:one", "One", UiContributions.Position.before("z:other"));
        tabs.add("z:other", "Other", UiContributions.Position.first());
        tabs.remove("a:remove_uninstalled", "uninstalled:tab");
        tabs.replace("a:replace_uninstalled", "uninstalled:tab", "Not shown");
        tabs.add("z:unknown_anchor", "End", UiContributions.Position.after("uninstalled:tab"));

        assertEquals(List.of("One", "Other", "Tools", "Emoji", "Send", "End"),
                tabs.resolve(BUILT_INS).stream().map(UiContributions.Entry::value).toList());
        assertThrows(IllegalArgumentException.class, () -> tabs.add("z:other", "Duplicate",
                UiContributions.Position.last()));
    }

    @Test
    void conflictingEditOrderIsIndependentOfModLoadOrder() {
        var first = new UiContributions<String>();
        var second = new UiContributions<String>();
        first.replace("b:change", "atomchat:send", "Second");
        first.replace("a:change", "atomchat:send", "First");
        second.replace("a:change", "atomchat:send", "First");
        second.replace("b:change", "atomchat:send", "Second");

        assertEquals(List.of("Tools", "Emoji", "Second"),
                first.resolve(BUILT_INS).stream().map(UiContributions.Entry::value).toList());
        assertEquals(first.resolve(BUILT_INS), second.resolve(BUILT_INS));
    }
}
