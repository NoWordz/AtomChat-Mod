package com.atom.chat.ui;

import com.atom.chat.api.ui.NavigationUi;
import com.atom.chat.nav.NavPage;
import com.atom.chat.render.ScrollEdgeFade;
import io.github.humbleui.skija.Canvas;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Owns each add-on page instance for the lifespan of one AtomChat screen. */
public final class ExtensionPages implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(ExtensionPages.class.getName());
    private final Map<NavPage, NavigationUi.View> views = new HashMap<>();
    private final java.util.Set<NavPage> failed = new java.util.HashSet<>();
    private final NavigationUi.Context context;

    public ExtensionPages(NavigationUi.Context context) {
        this.context = context;
    }

    private NavigationUi.View view(NavPage page) {
        if (failed.contains(page)) return null;
        return views.computeIfAbsent(page, route -> {
            var definition = NavigationUi.page(route.param());
            if (definition == null) return null;
            try {
                return Objects.requireNonNull(definition.factory().get());
            } catch (RuntimeException exception) {
                fail(route, "Could not create page ", exception);
                return null;
            }
        });
    }

    private static NavigationUi.Viewport viewport(UiLayout.Rect rect, float mx, float my, float scroll) {
        return new NavigationUi.Viewport(rect.x(), rect.y(), rect.w(), rect.h(), mx, my, scroll);
    }

    public float measure(NavPage page, UiLayout.Rect rect) {
        var view = view(page);
        if (view == null) return 0;
        try {
            float measured = view.contentHeight(viewport(rect, -1, -1, 0), context);
            return Float.isFinite(measured) ? Math.max(measured, 0F) : 0;
        } catch (RuntimeException exception) {
            fail(page, "Could not measure page ", exception);
            return 0;
        }
    }

    public void draw(Canvas canvas, NavPage page, UiLayout.Rect rect, float mx, float my, float scroll, float maxScroll) {
        var view = view(page);
        if (view == null) return;
        try (var scrollClip = ScrollEdgeFade.clip(canvas, rect.x(), rect.y(), rect.w(), rect.h(), scroll, maxScroll)) {
            view.render(canvas, viewport(rect, mx, my, scroll), context);
        } catch (RuntimeException exception) {
            fail(page, "Could not draw page ", exception);
        }
    }

    public boolean clicked(NavPage page, UiLayout.Rect rect, float mx, float my, float scroll, int button) {
        if (!rect.contains(mx, my)) return false;
        var view = view(page);
        if (view == null) return false;
        try {
            return view.clicked(viewport(rect, mx, my, scroll), context, button);
        } catch (RuntimeException exception) {
            fail(page, "Page click failed: ", exception);
            return false;
        }
    }

    private void fail(NavPage page, String message, RuntimeException exception) {
        if (failed.add(page)) LOGGER.log(System.Logger.Level.WARNING, message + page.param(), exception);
    }

    @Override public void close() {
        for (var view : views.values()) {
            try {
                view.close();
            } catch (RuntimeException exception) {
                LOGGER.log(System.Logger.Level.WARNING, "Could not close an add-on page", exception);
            }
        }
        views.clear();
        failed.clear();
    }
}
