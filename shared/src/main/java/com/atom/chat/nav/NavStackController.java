package com.atom.chat.nav;

import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.chat.PrivateChatStore;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.ui.Animations;

import java.util.List;

/**
 * The navigation stack and every cascade a push, a pop or a root-tab switch
 * runs (D24a).
 *
 * <p><b>Why this class exists.</b> The stack itself was a one-field helper; the
 * value of moving it off the screen is the <em>order</em> of the cascade around
 * it. A single pop executes ten-plus cross-domain steps — persist the draft,
 * clear the world page's transient UI, reset the settings sub-page, pop,
 * drop the injected profile subject, re-point the two active stores, load the
 * arriving draft, re-arm the arriving reply bar — and that order is the
 * contract (a resetSubject before the pop, for instance, redraws the
 * sliding-out page as the local player's profile mid-animation). On the screen
 * none of it could be asserted offline: the screen needs the Minecraft client to
 * load. Here every step is a call on {@link Host}, so a recording port pins the
 * sequence, and swapping any two steps turns a test red.
 *
 * <p><b>What stayed behind.</b> Every step is a one-line delegation to a domain
 * that was already split out (D1 reply slots, D2 drafts, D11 scroll, D15
 * settings, D18 signature/subject, D19 online check, D21 composer, D25
 * transient bus) — this class owns the order, not the domains. The two things
 * that genuinely belong to the shell stay there: the copy lookup
 * ({@code tr}, a Minecraft call) and the transition state machines, which are
 * asked for through the same port ({@code beginPageNav} and friends) so that
 * block D24b can hand them over without touching this class.
 *
 * <p><b>Bucket.</b> Shared single source: not one {@code net.minecraft} name
 * appears here. The three screen copies keep thin delegating methods with their
 * original signatures, so the class stays the screen's {@code PageHost} and
 * {@code NotificationJumpService.NavPort} implementation.
 */
public final class NavStackController {

    /**
     * Everything this controller needs from the screen, one method per step it
     * cannot take itself.
     *
     * <p>Names are deliberately unlike any screen method: the source guards
     * locate members by first textual occurrence, so a port that borrowed a
     * screen method's name would capture the guard's anchor.
     */
    public interface Host {
        /** D18: a live signature edit never survives navigation. */
        boolean signatureEditLive();

        /** D18: aborts the live signature edit without committing it. */
        void abortSignatureEdit();

        /** D2: persists the composer field to the page that is currently open. */
        void persistCurrentDraft();

        /** D2: persists one private conversation's draft. */
        void persistPrivateDraft(PlayerRef target, String text);

        /** D2: loads a private conversation's draft into the composer field. */
        void adoptDraft(PlayerRef target);

        /** D2: loads the public feed's draft into the composer field. */
        void adoptWorldDraft();

        /** D21: the single shared composer field's current text. */
        String composerTextNow();

        /** D1: arms {@code page}'s saved reply so the live field belongs to it. */
        void armReplyFor(NavPage page);

        /** D11: forgets the public feed's scroll position. */
        void clearWorldScroll();

        /** D11: forgets a pushed profile detail's scroll position. */
        void clearDetailScroll();

        /** D11: forgets the root page's scroll position. */
        void clearRootScroll();

        /** D15: drops the settings sub-page's transient state. */
        void clearSettingsPageState();

        /** D25: the eleven-domain transient reset the world page needs on the way out. */
        void clearTransientWorldUi();

        /** D19: whether the player is still on the tab list. */
        boolean isPlayerOnline(PlayerRef target);

        /** D18: drops the injected profile subject, back to the local player. */
        void dropProfileSubject();

        /** The screen's one language lookup (a Minecraft call, so it stays there). */
        String copy(String key);

        /**
         * Arms the page transition the shell owns (D24b). The stack decides
         * <em>when</em>; the transition owns its own state.
         */
        void beginPageNav(NavPage from, NavPage to, boolean popPending);

        /** Disarms the page transition without running it (the non-animated arm of a push). */
        void abandonPageNav();

        /** Disarms any running root-tab transition. */
        void abandonRootTransition();

        /** Whether a root-tab transition is still running. */
        boolean rootTransitionLive();

        /** The one nav-style resolution, asked for rather than re-read here. */
        boolean navStyleIsZoom();

        /** Moves the capsule to {@code from}'s cell before a transition is armed. */
        void pinRootTabCapsule(AppPage from);

        /** Records the from->to tab span and clears the motion state for a fresh run. */
        void markRootTabSpan(AppPage from, AppPage to);

        /** Arms the tab motion for the resolved style. */
        void applyRootTabMotion(boolean zoom);

        /** Resets the capsule from the span it was pinned to. */
        void resetRootTabCapsule();

        /** Selects a tab with no animation. */
        void selectTabNow(AppPage root);
    }

    private final Host host;
    /** The one stack. Nothing outside this class holds it, so nothing can diverge from it. */
    private final NavigationStack<NavPage> navigation;

    /**
     * Builds the stack the way one screen opening does: {@code restoreSnapshot}
     * replays the last stack (the AtomChat hotkey), otherwise the public world
     * chat is pushed on top of the conversation list (the vanilla chat key).
     * The tab bar and the two active stores are placed to match the resulting
     * top page before anything can read them.
     */
    public NavStackController(Host host, boolean restoreSnapshot) {
        this.host = host;
        this.navigation = new NavigationStack<>(NavPage.of(AppPage.CHAT_LIST));
        if (!restoreSnapshot) {
            navigation.replaceWithRoot(NavPage.of(AppPage.CHAT_LIST));
            navigation.push(NavPage.of(AppPage.WORLD_CHAT));
        } else {
            List<NavPage> saved = AtomChatState.snapshot();
            navigation.replaceWithRoot(saved.get(0));
            for (int i = 1; i < saved.size(); i++) {
                navigation.push(saved.get(i));
            }
        }
        if (topPage().isRoot()) {
            host.selectTabNow(topPage());
        }
        if (topPage() == AppPage.PRIVATE_CHAT) {
            PrivateChatStore.setActive(activePrivateTarget());
            ChatStore.setPublicActive(false);
        } else if (topPage() == AppPage.WORLD_CHAT) {
            ChatStore.setPublicActive(true);
        } else {
            ChatStore.setPublicActive(false);
        }
    }

    // ------------------------------------------------------------- queries

    public NavPage topNav() {
        return navigation.peek();
    }

    public AppPage topPage() {
        return navigation.peek().page();
    }

    public boolean isPrivateReadOnly() {
        return isPrivateReadOnlyFor(topNav());
    }

    /**
     * {@link #isPrivateReadOnly()} for one nav page. A transition composites two
     * pages in the same frame, so the check must never read whatever happens to
     * sit on top of the stack while the other page is drawn.
     */
    public boolean isPrivateReadOnlyFor(NavPage page) {
        if (page == null || page.page() != AppPage.PRIVATE_CHAT) {
            return false;
        }
        PlayerRef target = page.target();
        if (target == null) {
            return true;
        }
        return !host.isPlayerOnline(target) || BlockList.isBlocked(target);
    }

    /** Composer hint of a read-only private page, from that page's partner. */
    public String readOnlyHintForNav(NavPage page) {
        return page != null && BlockList.isBlocked(page.target())
                ? host.copy("atomchat.private.blocked") : host.copy("atomchat.private.offline");
    }

    public PlayerRef activePrivateTarget() {
        NavPage top = navigation.peek();
        return top != null && top.page() == AppPage.PRIVATE_CHAT ? top.target() : null;
    }

    /**
     * Detail chat pages (public world or a private conversation) share the whole
     * composer/message-list UI. The method name is kept for historical call
     * sites but also covers PRIVATE_CHAT.
     */
    public boolean isWorldChatPage() {
        AppPage page = topPage();
        return page == AppPage.WORLD_CHAT || page == AppPage.PRIVATE_CHAT;
    }

    /** The whole stack, for the session snapshot taken when the screen goes away. */
    public List<NavPage> snapshot() {
        return navigation.snapshot();
    }

    // --------------------------------------------------------------- doors

    public void pushPage(AppPage page) {
        pushNav(NavPage.of(page));
    }

    /** Pushes the public world chat (used by the root Public card). */
    public void openWorldChat() {
        pushPage(AppPage.WORLD_CHAT);
    }

    /** Pushes a private conversation page from a root player card. */
    public void openPrivateChat(PlayerRef target) {
        pushNav(NavPage.privateChat(target));
    }

    /** Pushes a settings sub-page from the settings home tile grid. */
    public void openSettingsSection(SettingsSection section) {
        pushNav(NavPage.settingsSection(section));
    }

    /**
     * The one push cascade. Public because the screen's own {@code pushNav} is
     * the single funnel every door ({@code pushPage} / {@code openWorldChat} /
     * {@code openPrivateChat} / {@code openSettingsSection} / the profile push)
     * still goes through — the doors stayed on the screen, the cascade did not.
     */
    public void pushNav(NavPage page) {
        // Leaving the profile page must not carry a live signature edit into
        // the next page: abort (never commit on navigation) before anything
        // else touches the borrowed field.
        if (host.signatureEditLive()) {
            host.abortSignatureEdit();
        }
        host.persistCurrentDraft();
        AppPage fromPage = topPage();
        if (page.page() == AppPage.WORLD_CHAT) {
            host.clearWorldScroll();
        }
        if (page.page() == AppPage.SETTINGS_SECTION) {
            host.clearSettingsPageState();
        }
        if (page.page() == AppPage.PROFILE_DETAIL) {
            host.clearDetailScroll();
        }
        if (!page.isRoot() && fromPage.isRoot()) {
            host.pinRootTabCapsule(fromPage);
            host.beginPageNav(topNav(), page, false);
        } else if (!page.isRoot() && !fromPage.isRoot()) {
            // Public <-> private detail pages also use the full-width push/pop.
            host.beginPageNav(topNav(), page, false);
        } else {
            host.abandonPageNav();
        }
        navigation.push(page);
        ChatStore.setPublicActive(page.page() == AppPage.WORLD_CHAT);
        if (page.page() == AppPage.PRIVATE_CHAT) {
            PrivateChatStore.setActive(page.target());
            host.adoptDraft(page.target());
        } else if (page.page() == AppPage.WORLD_CHAT) {
            host.adoptWorldDraft();
        }
        // The reply bar follows its page exactly like the draft above: the nav
        // draws the leaving page's bar from its slot while the arriving page's
        // is live in the field.
        host.armReplyFor(page);
        if (!page.isRoot()) {
            host.abandonRootTransition();
        }
    }

    public void popPage() {
        // Same rule as pushNav: a live signature edit never survives
        // navigation, and it aborts (no commit) when the page goes away.
        if (host.signatureEditLive()) {
            host.abortSignatureEdit();
        }
        if (navigation.size() <= 1) {
            return;
        }
        if (topPage().isRoot()) {
            navigation.pop();
            return;
        }
        // With decorative motion off there is no slide-out to wait for, so the
        // pop can be applied right here instead of in finishPageNav().
        if (!Animations.enabled()) {
            popNow();
            return;
        }
        NavPage from = topNav();
        NavPage previous = navigation.snapshot().get(navigation.size() - 2);
        // Keep the detail page on top while it slides out; the actual navigation
        // pop happens in finishPageNav(). Both root and detail targets animate.
        // The subject reset lives in popNow(): clearing it here would redraw the
        // sliding-out page as the local player's profile mid-animation.
        host.beginPageNav(from, previous, true);
    }

    /** Applies a pending pop and restores the page underneath it. */
    public void popNow() {
        boolean leavingProfile = topPage() == AppPage.PROFILE_DETAIL;
        if (topPage() == AppPage.PRIVATE_CHAT) {
            host.persistPrivateDraft(activePrivateTarget(), host.composerTextNow());
            PrivateChatStore.clearActive();
        }
        host.clearTransientWorldUi();
        host.clearSettingsPageState();
        navigation.pop();
        // Falling back to the local player only once the page is off the stack,
        // so the Profile root tab never shows a stale injected subject.
        if (leavingProfile) {
            host.dropProfileSubject();
        }
        ChatStore.setPublicActive(topPage() == AppPage.WORLD_CHAT);
        if (topPage() == AppPage.PRIVATE_CHAT) {
            PrivateChatStore.setActive(activePrivateTarget());
            host.adoptDraft(activePrivateTarget());
        } else if (topPage() == AppPage.WORLD_CHAT) {
            host.clearWorldScroll();
            host.adoptWorldDraft();
        }
        // clearTransientWorldUi() above dropped every armed reply, so this only
        // re-arms the page underneath when that rule ever loosens; the reply bar
        // is loaded exactly like the draft, never left pointing at the page that
        // was just popped.
        host.armReplyFor(topNav());
    }

    public void switchRoot(AppPage root) {
        if (!root.isRoot()) {
            return;
        }
        // Tab switches are the one navigation path that never runs pushNav or
        // popPage, so the signature edit aborts here too (arriving OR leaving
        // the profile tab).
        if (host.signatureEditLive()) {
            host.abortSignatureEdit();
        }
        AppPage from = topPage();
        // Leaving the profile root falls back to the local player's page; the
        // injected subject (open-profile-for-other-player) is transient.
        if (from == AppPage.PROFILE && root != AppPage.PROFILE) {
            host.dropProfileSubject();
        }
        host.persistCurrentDraft();
        host.clearRootScroll();
        // With decorative motion off the tab change is instant: no indicator
        // slide, no body push — the new root is simply on screen.
        if (!Animations.enabled()) {
            host.abandonRootTransition();
            host.selectTabNow(root);
            navigation.replaceWithRoot(NavPage.of(root));
            ChatStore.setPublicActive(false);
            return;
        }
        // If a previous tab transition is still running, snap it before
        // re-arming: the clocks below always start from rest.
        if (host.rootTransitionLive() && from.isRoot()) {
            host.abandonRootTransition();
        }
        navigation.replaceWithRoot(NavPage.of(root));
        ChatStore.setPublicActive(false);
        if (from.isRoot() && from != root) {
            host.markRootTabSpan(from, root);
            // The content transition follows the configured page-nav style,
            // resolved once per switch through the same resolver the push/pop
            // path uses (one rule for every switch) so it can't change
            // mid-flight; the capsule is pinned to the source tab and travels
            // from the same clock in stepRootNav.
            boolean zoom = host.navStyleIsZoom();
            host.applyRootTabMotion(zoom);
            host.resetRootTabCapsule();
        } else {
            host.abandonRootTransition();
            host.selectTabNow(root);
        }
    }
}
