package com.atom.chat.nav;

import java.util.List;

/**
 * Last page stack persisted across AtomChat screen instances opened through the
 * AtomChat hotkey (RESTORE mode). The vanilla chat key always opens DIRECT_WORLD
 * and ignores this state.
 */
public final class AtomChatState {
    private static List<NavPage> stack = List.of(NavPage.of(AppPage.CHAT_LIST));

    private AtomChatState() {
    }

    public static synchronized List<NavPage> snapshot() {
        if (!stack.get(0).available()) return List.of(NavPage.of(AppPage.CHAT_LIST));
        for (int i = 1; i < stack.size(); i++) {
            if (!stack.get(i).available()) return List.copyOf(stack.subList(0, i));
        }
        return List.copyOf(stack);
    }

    public static synchronized void save(List<NavPage> pages) {
        if (pages == null || pages.isEmpty() || pages.get(0) == null || !pages.get(0).isRoot()) {
            stack = List.of(NavPage.of(AppPage.CHAT_LIST));
            return;
        }
        for (int i = 1; i < pages.size(); i++) {
            NavPage page = pages.get(i);
            if (page == null || page.isRoot()) {
                stack = List.of(NavPage.of(AppPage.CHAT_LIST));
                return;
            }
        }
        stack = List.copyOf(pages);
    }

    static synchronized void reset() {
        stack = List.of(NavPage.of(AppPage.CHAT_LIST));
    }
}
