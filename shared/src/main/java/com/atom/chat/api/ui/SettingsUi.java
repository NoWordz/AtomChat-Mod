package com.atom.chat.api.ui;

import com.atom.chat.nav.NavPage;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.ui.SettingsIcons;
import com.atom.lib.design.atomchat.AppIcons;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Path;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Preview API for settings home entries, detail tabs and collapsible groups. */
public final class SettingsUi {
    private static final UiContributions<HomeEntry> HOME = new UiContributions<>();
    private static final Map<SettingsSection, UiContributions<Chip>> CHIPS = new ConcurrentHashMap<>();
    private static final Map<Scope, UiContributions<Group>> GROUPS = new ConcurrentHashMap<>();
    private static final Map<Scope, Map<String, UiContributions<Control>>> ROWS = new ConcurrentHashMap<>();
    public static final String TOP_ROWS = "atomchat:ungrouped";
    private static final class DefaultHome {
        private static final List<UiContributions.Entry<HomeEntry>> ENTRIES = List.of(
                builtIn(SettingsSection.APPEARANCE, "atomchat.settings.appearance", SettingsIcons.PALETTE),
                builtIn(SettingsSection.CHAT, "atomchat.settings.chat", SettingsIcons.MESSAGES),
                builtIn(SettingsSection.PRIVACY, "atomchat.settings.privacy", AppIcons.ICON_SETTINGS_PRIVACY_PATH),
                builtIn(SettingsSection.ABOUT, "atomchat.settings.about", SettingsIcons.INFO_CIRCLE));
    }

    public static final String HOME_SPACING = "atomchat:settings_home_spacing";
    private static final UiContributions<HomeSpacing> SPACING = new UiContributions<>();
    private static final HomeSpacing DEFAULT_SPACING = new HomeSpacing(UiTokens.SETTINGS_TILE_GAP, UiTokens.ROW_CLIP_INSET);
    private static final List<UiContributions.Entry<HomeSpacing>> DEFAULT_SPACINGS =
            List.of(new UiContributions.Entry<>(HOME_SPACING, DEFAULT_SPACING));

    private record Scope(SettingsSection section, String tab) {}

    private SettingsUi() {}

    public record HomeEntry(String titleKey, Path icon, String pageId) {
        public HomeEntry {
            Objects.requireNonNull(titleKey);
            Objects.requireNonNull(icon);
            Objects.requireNonNull(pageId);
        }
    }

    public record Chip(String titleKey, boolean builtInContent) {
        public Chip(String titleKey) { this(titleKey, false); }
        public Chip { Objects.requireNonNull(titleKey); }
    }

    public sealed interface Control permits Toggle, Info, OriginalControl {}

    /** Marker used when merging an existing native row. */
    public enum OriginalControl implements Control { KEEP }

    public record Info(String titleKey, String bodyKey) implements Control {
        public Info {
            Objects.requireNonNull(titleKey);
            Objects.requireNonNull(bodyKey);
        }
    }

    /** The add-on writes and persists its own value from the setter. */
    public record Toggle(String id, String titleKey, String descriptionKey,
                         BooleanSupplier value, Consumer<Boolean> setValue) implements Control {
        public Toggle {
            new UiContributions.Entry<>(id, titleKey);
            Objects.requireNonNull(descriptionKey);
            Objects.requireNonNull(value);
            Objects.requireNonNull(setValue);
        }
    }

    public record Group(String labelKey, List<Toggle> toggles) {
        public Group {
            Objects.requireNonNull(labelKey);
            toggles = List.copyOf(toggles);
        }
    }

    public record HomeSpacing(float tileGap, float sideInset) {
        public HomeSpacing {
            if (!Float.isFinite(tileGap) || !Float.isFinite(sideInset) || tileGap < 0F || sideInset < 0F) {
                throw new IllegalArgumentException("Settings spacing must be finite and non-negative");
            }
        }
    }

    public static UiContributions<HomeEntry> home() {
        return HOME;
    }

    public static UiContributions<Chip> tabs(SettingsSection section) {
        return CHIPS.computeIfAbsent(section, unused -> new UiContributions<>());
    }

    /** Use the namespaced tab ID. Use an empty string for a section without tabs. */
    public static UiContributions<Group> groups(SettingsSection section, String tabId) {
        return GROUPS.computeIfAbsent(new Scope(section, tabId), unused -> new UiContributions<>());
    }

    public static UiContributions<Group> registeredGroups(SettingsSection section, String tabId) {
        return GROUPS.isEmpty() || com.atom.chat.onboarding.TutorialSandbox.active()
                ? null : GROUPS.get(new Scope(section, tabId));
    }

    /** Edit individual rows of an existing or contributed group. */
    public static UiContributions<Control> rows(SettingsSection section, String tabId, String groupId) {
        return ROWS.computeIfAbsent(new Scope(section, tabId), unused -> new ConcurrentHashMap<>())
                .computeIfAbsent(groupId, unused -> new UiContributions<>());
    }

    public static Map<String, UiContributions<Control>> registeredRows(SettingsSection section, String tabId) {
        if (ROWS.isEmpty() || com.atom.chat.onboarding.TutorialSandbox.active()) return Map.of();
        var edits = ROWS.get(new Scope(section, tabId));
        return edits == null || edits.values().stream().noneMatch(UiContributions::hasEdits) ? Map.of() : edits;
    }

    public static String builtInRowId(String nativeId) {
        return "atomchat:" + nativeId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
    }

    public static String builtInTabId(String id) {
        return id.isEmpty() ? "" : "atomchat:" + id;
    }

    public static String builtInGroupId(String labelKey) {
        return "atomchat:" + labelKey;
    }

    public static UiContributions<HomeSpacing> homeStyles() {
        return SPACING;
    }

    public static HomeSpacing homeSpacing() {
        if (!SPACING.hasEdits() || com.atom.chat.onboarding.TutorialSandbox.active()) return DEFAULT_SPACING;
        for (var entry : SPACING.resolve(DEFAULT_SPACINGS)) {
            if (entry.id().equals(HOME_SPACING)) return entry.value();
        }
        return DEFAULT_SPACING;
    }

    public static List<UiContributions.Entry<HomeEntry>> resolvedHome() {
        var entries = DefaultHome.ENTRIES;
        if (!HOME.hasEdits() || com.atom.chat.onboarding.TutorialSandbox.active()) return entries;
        return HOME.resolve(entries).stream().filter(entry -> destination(entry.value()) != null).toList();
    }

    public static NavPage destination(HomeEntry entry) {
        for (var section : SettingsSection.values()) {
            if (entry.pageId().equals(sectionRoute(section))) return NavPage.settingsSection(section);
        }
        return NavigationUi.page(entry.pageId()) == null ? null : NavPage.addonDetail(entry.pageId());
    }

    public static String sectionRoute(SettingsSection section) {
        return "atomchat:settings/" + section.id();
    }

    private static UiContributions.Entry<HomeEntry> builtIn(SettingsSection section, String title, Path icon) {
        String route = sectionRoute(section);
        return new UiContributions.Entry<>(route, new HomeEntry(title, icon, route));
    }
}
