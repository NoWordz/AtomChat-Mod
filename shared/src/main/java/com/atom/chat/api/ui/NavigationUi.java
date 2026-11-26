package com.atom.chat.api.ui;

import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import com.atom.chat.ui.TablerIcons;
import com.atom.lib.design.atomchat.AppIcons;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Path;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Preview API for AtomChat navigation and pages owned by client add-ons. */
public final class NavigationUi {
    public static final String CHAT = "atomchat:chat_list";
    public static final String PROFILE = "atomchat:profile";
    public static final String SETTINGS = "atomchat:settings";

    private static final UiContributions<Tab> TABS = new UiContributions<>();
    private static final Map<String, Page> PAGES = new ConcurrentHashMap<>();
    private static final List<UiContributions.Entry<Tab>> DEFAULT_TABS = List.of(
            new UiContributions.Entry<>(CHAT, new Tab(CHAT, AppIcons.ICON_TAB_CHAT_PATH)),
            new UiContributions.Entry<>(PROFILE, new Tab(PROFILE, AppIcons.ICON_TAB_PROFILE_PATH)),
            new UiContributions.Entry<>(SETTINGS, new Tab(SETTINGS, TablerIcons.SETTINGS)));

    public record Tab(String pageId, Path icon) {
        public Tab {
            Objects.requireNonNull(pageId);
            Objects.requireNonNull(icon);
        }
    }

    public record Page(String titleKey, Supplier<? extends View> factory) {
        public Page {
            Objects.requireNonNull(titleKey);
            Objects.requireNonNull(factory);
        }
    }

    /** Bounds and pointer are in the same virtual units used by AtomChat. */
    public record Viewport(float x, float y, float width, float height,
                           float mouseX, float mouseY, float scrollY) {}

    public interface Context {
        String translate(String key);
        int foregroundColor();
        void drawLabel(Canvas canvas, String text, float x, float baselineY);
        void open(String pageId);
        void back();
    }

    public interface View extends AutoCloseable {
        default float contentHeight(Viewport viewport, Context context) { return viewport.height(); }

        void render(Canvas canvas, Viewport viewport, Context context);

        default boolean clicked(Viewport viewport, Context context, int button) { return false; }

        @Override default void close() {}
    }

    private NavigationUi() {}

    public static UiContributions<Tab> tabs() {
        return TABS;
    }

    public static void registerPage(String id, Page page) {
        new UiContributions.Entry<>(id, page);
        if (builtIn(id) != null || PAGES.putIfAbsent(id, page) != null) {
            throw new IllegalArgumentException("Duplicate page ID: " + id);
        }
    }

    public static Page page(String id) {
        return id == null ? null : PAGES.get(id);
    }

    public static List<UiContributions.Entry<Tab>> resolvedTabs() {
        if (!TABS.hasEdits() || com.atom.chat.onboarding.TutorialSandbox.active()) return DEFAULT_TABS;
        return TABS.resolve(DEFAULT_TABS).stream().filter(entry -> root(entry.value().pageId()) != null).toList();
    }

    public static NavPage root(String id) {
        AppPage builtIn = builtIn(id);
        if (builtIn != null) return NavPage.of(builtIn);
        return page(id) == null ? null : NavPage.addonRoot(id);
    }

    public static String route(NavPage page) {
        return page.page().isAddon() ? page.param() : "atomchat:" + page.page().id();
    }

    private static AppPage builtIn(String id) {
        if (id == null) return null;
        return switch (id) {
            case CHAT -> AppPage.CHAT_LIST;
            case PROFILE -> AppPage.PROFILE;
            case SETTINGS -> AppPage.SETTINGS;
            default -> null;
        };
    }
}
