package com.atom.chat.chat;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.ShellHeader;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Path;

import java.util.ArrayList;
import java.util.List;

/**
 * The public feed's view filter: which of the retained rows the world chat
 * shows, and the header button that cycles it.
 *
 * <p>View-only by construction. The store, the unread counts and the
 * conversation previews always see the unfiltered feed — filtering happens on
 * the way from {@link ChatStore} to the list, never inside the store. The selected
 * mode lives in {@link AtomChatConfig}, so reopening the screen, reloading the
 * config and navigating between pages all retain the preference.
 *
 * <p>What stays with the screen: the button's rect and its hover wash. Both are
 * header chrome (they are laid out from the back button's slot and stepped once
 * per frame by the header draw), so they are passed in as facts — this class
 * reads the current preference and owns its icons and tint, nothing about where
 * the button sits.
 */
public final class WorldFeedFilter {
    public boolean accepts(ChatMessage message) {
        return AtomChatConfig.get().effectiveWorldFeedFilter().accepts(message);
    }

    /**
     * Cycles the filter ALL → SYSTEM → PLAYERS → ALL.
     *
     * <p>The screen drops the message selection right after this (a selection can
     * span rows the new filter hides); that belongs to the list, so it stays
     * with the caller.
     */
    public void cycle() {
        AtomChatConfig config = AtomChatConfig.get();
        config.worldFeedFilter = config.effectiveWorldFeedFilter().next();
        AtomChatConfig.save(config);
    }

    /**
     * The feed's rows as the active filter shows them.
     *
     * <p>With nothing filtered the caller's list is handed straight back (same
     * instance, no copy) — the world page asks for this every frame, and the
     * unfiltered case is the common one.
     */
    public List<ChatMessage> filterFeed(List<ChatMessage> all) {
        MessageFilter worldFilter = AtomChatConfig.get().effectiveWorldFeedFilter();
        if (worldFilter == MessageFilter.ALL) {
            return all;
        }
        // View filter only: the store, unread counts and previews always see the
        // full feed.
        List<ChatMessage> out = new ArrayList<>(all.size());
        for (ChatMessage message : all) {
            if (worldFilter.accepts(message)) {
                out.add(message);
            }
        }
        return out;
    }

    /**
     * Header action for the page a header is titled with: the feed filter on
     * the world chat only. Funnel = nothing filtered (plain text colour);
     * speaker/person = system/players only, tinted accent so the active filter
     * reads at a glance (same language as the emoji button when its panel is
     * open).
     *
     * @param page   the page the header is titled with; any other page has no
     *               filter slot even though the geometry exists
     * @param button the filter button's rect (header chrome, owned by the screen)
     * @param hover  its current hover wash (same)
     */
    public ShellHeader.HeaderAction headerActionFor(NavPage page, UiLayout.Rect button, float hover) {
        if (page == null || page.page() != AppPage.WORLD_CHAT) {
            return null;
        }
        AtomChatConfig config = AtomChatConfig.get();
        MessageFilter worldFilter = config.effectiveWorldFeedFilter();
        Path icon =
                worldFilter == MessageFilter.SYSTEM ? AppIcons.ICON_ANNOUNCE_PATH
                        : worldFilter == MessageFilter.PLAYERS ? AppIcons.ICON_TAB_PROFILE_PATH
                        : AppIcons.ICON_FILTER_PATH;
        int color = worldFilter.isFiltering() ? config.accentColor : config.textPrimaryColor;
        return new ShellHeader.HeaderAction(button, hover, icon, color);
    }
}
