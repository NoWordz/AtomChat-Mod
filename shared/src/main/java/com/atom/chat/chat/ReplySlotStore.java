package com.atom.chat.chat;

import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;

import java.util.HashMap;
import java.util.Map;

/**
 * Armed reply bars, one slot per conversation.
 *
 * <p>Why slots and not a single field: the reply bar follows its page exactly
 * like the draft does. The screen's composer is one shared {@code EditBox}, so
 * "which reply is armed right now" is a property of the page that owns the
 * field — a single field meant leaving a page silently re-armed its reply on
 * whichever page came next (the shared-single-field leak this type replaces).
 *
 * <p>Two pieces of state, one contract:
 * <ul>
 *   <li>{@link #replies()} — the saved slot of every conversation, keyed like
 *       the history windows ({@link #replyKeyForNav});</li>
 *   <li>{@code replyTarget} — the live reply of the page that currently owns
 *       the composer, which is what {@link #syncReplySlot} writes back into the
 *       slot it came from.</li>
 * </ul>
 *
 * <p>The nav transition draws two pages through one layout, so
 * {@link #replyBarVisible} takes both sides of the transition: either side's
 * armed reply has to reserve the row, or the other side's bar would be painted
 * into the zero-height rect {@code UiLayout.of} leaves at the panel origin when
 * {@code replyH == 0}.
 *
 * <p>Mapping-neutral by construction: the screen passes the navigation facts in
 * ({@code topPage}/{@code topNav}/{@code ownsInputField}/the transition pair)
 * rather than the store reaching for them — that is what lets this live in
 * {@code shared/} and be tested offline.
 */
public final class ReplySlotStore {
    /** Slot key convention shared with the history windows: private chat = the
     *  partner's key, world chat = "" — so one conversation can never pick up
     *  another's slot. */
    private final Map<String, ChatMessage> replies = new HashMap<>();

    /** Armed reply of the page that currently owns the shared composer. */
    private ChatMessage replyTarget;

    /** Reply slot key, using the conversation keys' convention ({@link ConversationKeys}). */
    public String replyKeyForNav(NavPage page) {
        return ConversationKeys.of(page);
    }

    /**
     * Persists the armed reply to the page that is currently open. A page that
     * is not a chat page has no reply bar to persist.
     *
     * @param topPage the page kind on top of the nav stack (the caller's
     *                {@code topPage()})
     * @param topNav  the page on top of the nav stack (the caller's
     *                {@code topNav()}); its key owns the slot being written
     */
    public void syncReplySlot(AppPage topPage, NavPage topNav) {
        if (topPage != AppPage.WORLD_CHAT && topPage != AppPage.PRIVATE_CHAT) {
            return;
        }
        if (replyTarget == null) {
            replies.remove(replyKeyForNav(topNav));
        } else {
            replies.put(replyKeyForNav(topNav), replyTarget);
        }
    }

    /** Arms {@code page}'s saved reply, so the live field belongs to that page. */
    public void loadReplyFor(NavPage page) {
        if (page == null || (page.page() != AppPage.WORLD_CHAT && page.page() != AppPage.PRIVATE_CHAT)) {
            replyTarget = null;
            return;
        }
        replyTarget = replies.get(replyKeyForNav(page));
    }

    /**
     * Armed reply of a page: the live field when it owns the composer, else its slot.
     *
     * @param ownsInputField whether {@code page} is the page holding the shared
     *                       composer (the caller's {@code ownsInputField(page)})
     */
    public ChatMessage replyForNav(NavPage page, boolean ownsInputField) {
        return ownsInputField ? replyTarget : replies.get(replyKeyForNav(page));
    }

    /**
     * True while a reply bar is on screen. Both pages of a nav share one layout,
     * so either side's armed reply must reserve the row — otherwise the other
     * side's bar would be painted into the zero-height rect
     * {@code UiLayout.of} leaves at the panel origin when {@code replyH == 0}.
     *
     * @param navFrom the leaving page of the nav transition (may be null when no
     *                transition is in flight)
     * @param navTo   the arriving page of the nav transition (same)
     */
    public boolean replyBarVisible(NavPage navFrom, NavPage navTo) {
        return replyTarget != null || replySlotArmed(navFrom) || replySlotArmed(navTo);
    }

    /** True when a chat page still holds an armed reply in its slot. */
    public boolean replySlotArmed(NavPage page) {
        if (page == null || (page.page() != AppPage.WORLD_CHAT && page.page() != AppPage.PRIVATE_CHAT)) {
            return false;
        }
        return replies.get(replyKeyForNav(page)) != null;
    }

    /** The live reply of the page owning the composer (null when none is armed). */
    public ChatMessage replyTarget() {
        return replyTarget;
    }

    /** Arms {@code message} for the page that owns the composer ("quote this"). */
    public void arm(ChatMessage message) {
        this.replyTarget = message;
    }

    /** Drops the live reply; the page's slot is left alone until the next sync. */
    public void disarm() {
        this.replyTarget = null;
    }

    /**
     * Drops the live reply <em>and</em> every saved slot — what leaving the world
     * does, so nothing survives into the next one.
     */
    public void clearAll() {
        disarm();
        replies.clear();
    }
}