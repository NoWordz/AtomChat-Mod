package com.atom.chat.settings;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class AuthorsFoldTest {
    @Test void authorCardsBelongToTheSameFoldableHeadingFamily() throws Exception {
        SettingsSectionPage page = new SettingsSectionPage();
        var rows = page.rows(SettingsSection.ABOUT);
        var label = rows.stream().filter(r -> "atomchat.settings.about.authors".equals(r.labelKey()))
                .findFirst().orElseThrow();
        Method group = SettingsSectionPage.class.getDeclaredMethod("foldableGroup", String.class);
        group.setAccessible(true);
        assertNotNull(group.invoke(null, label.labelKey()), "authors must use the dotted foldable heading renderer");
    }
}
