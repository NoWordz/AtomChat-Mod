package com.atom.chat.nav;

import com.atom.chat.chat.PlayerRef;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsSection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The D24a contract: the order of every push / pop / root-switch cascade.
 *
 * <p><b>Why an order test and not a behaviour test.</b> The nav cascade is ten
 * to twelve cross-domain steps, and the order <em>is</em> the contract: a
 * subject reset that runs before the stack pop redraws the leaving page as the
 * local player's profile; a draft persisted after the stack moved saves it to
 * the wrong conversation; a signature edit aborted after the borrowed field was
 * already touched commits what navigation must never commit. None of that is
 * visible in a golden frame, so the sequence is pinned here, step by step,
 * through a recording port — and every assertion is written so that swapping
 * two steps turns it red.</p>
 *
 * <p><b>The transition inversion this rewrite pins.</b> The old controller armed
 * a page transition instead of finishing its own cascade: a pop left the detail
 * page on the stack, waiting for a finished slide-out. The Dango semantics have
 * no exit phase — {@code popPage} lands through the full {@code popNow} cascade
 * immediately, with motion on or off — and the arriving page's entrance overlay
 * is the only perceivable transition (pinned in {@code PageArrivalCompositeTest}
 * and {@code UiStaggerTest}). There is deliberately no transition port left to
 * arm: no pending-pop state, no root-tab span, no style resolution here.</p>
 *
 * <p>The recorder also records <em>which page the stack was on</em> at every
 * step. That is what makes "before the pop" / "after the pop" falsifiable rather
 * than a claim about the source: the step must observe the page it is required
 * to observe.</p>
 */
class NavStackControllerTest {

    private static final PlayerRef PARTNER =
            PlayerRef.of(UUID.fromString("11111111-2222-3333-4444-555555555555"), "Partner");
    private static final PlayerRef OFFLINE =
            PlayerRef.of(UUID.fromString("99999999-8888-7777-6666-555555555555"), "Ghost");

    private boolean animations;
    private AtomChatConfig.PageNavStyle style;
    private List<String> blocked;

    /**
     * The config is read through {@code Platform}, so this class installs its own
     * temporary platform instead of riding on whichever test class happened to run
     * first: filtered runs ({@code --tests}) and any change to class ordering would
     * otherwise fail here with "Platform not installed" rather than with a real
     * result.
     */
    @BeforeAll
    static void installTemporaryPlatform(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return root.resolve("config");
            }

            @Override
            public Path gameDir() {
                return root.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    @BeforeEach
    void setUp() {
        AtomChatConfig config = AtomChatConfig.get();
        animations = config.animationEnabled;
        style = config.pageNavStyle;
        blocked = config.blockedPlayers;
        config.animationEnabled = true;
        // No cascade step reads the style any more (the entrance overlay reads it
        // at frame time); pinned only so a future regression that re-introduces a
        // style-dependent cascade still runs deterministically.
        config.pageNavStyle = AtomChatConfig.PageNavStyle.ZOOM;
        // In memory only: BlockList.setBlocked would persist to the client config file.
        config.blockedPlayers = new ArrayList<>();
        AtomChatState.reset();
    }

    @AfterEach
    void tearDown() {
        AtomChatConfig config = AtomChatConfig.get();
        config.animationEnabled = animations;
        config.pageNavStyle = style;
        config.blockedPlayers = blocked;
        AtomChatState.reset();
    }

    // ------------------------------------------------------------- recorder

    /**
     * A port that records every step, the page the stack was on when the step
     * ran, and the arguments the step was handed.
     */
    private static final class Recorder implements NavStackController.Host {
        final List<String> calls = new ArrayList<>();
        final List<String> drafts = new ArrayList<>();
        NavStackController controller;
        boolean signatureEditing;
        boolean partnerOnline = true;
        String composer = "";

        private void note(String step) {
            String where = controller == null ? "?" : String.valueOf(controller.topNav().page());
            calls.add(step + "@" + where);
        }

        @Override
        public boolean signatureEditLive() {
            note("signatureEditLive");
            return signatureEditing;
        }

        @Override
        public void abortSignatureEdit() {
            note("abortSignatureEdit");
        }

        @Override
        public void persistCurrentDraft() {
            note("persistCurrentDraft");
        }

        @Override
        public void syncReplySlot() {
            note("syncReplySlot");
        }

        @Override
        public void persistPrivateDraft(PlayerRef target, String text) {
            note("persistPrivateDraft");
            drafts.add(target + "=" + text);
        }

        @Override
        public void adoptDraft(PlayerRef target) {
            note("adoptDraft");
        }

        @Override
        public void adoptWorldDraft() {
            note("adoptWorldDraft");
        }

        @Override
        public String composerTextNow() {
            note("composerTextNow");
            return composer;
        }

        @Override
        public void armReplyFor(NavPage page) {
            note("armReplyFor");
        }

        @Override
        public void clearWorldScroll() {
            note("clearWorldScroll");
        }

        @Override
        public void clearDetailScroll() {
            note("clearDetailScroll");
        }

        @Override
        public void clearRootScroll() {
            note("clearRootScroll");
        }

        @Override
        public void clearSettingsPageState() {
            note("clearSettingsPageState");
        }

        @Override
        public void clearTransientWorldUi() {
            note("clearTransientWorldUi");
        }

        @Override
        public boolean isPlayerOnline(PlayerRef target) {
            note("isPlayerOnline");
            return partnerOnline;
        }

        @Override
        public void dropProfileSubject() {
            note("dropProfileSubject");
        }

        @Override
        public String copy(String key) {
            note("copy");
            return key;
        }

        @Override
        public void selectTabNow(AppPage root) {
            note("selectTabNow[" + root + "]");
        }
    }

    /** A controller whose stack is exactly the given one, with the port attached. */
    private static NavStackController onStack(Recorder host, List<NavPage> stack) {
        AtomChatState.save(stack);
        NavStackController controller = new NavStackController(host, true);
        host.controller = controller;
        host.calls.clear();
        return controller;
    }

    // ------------------------------------------------------------- cascade

    /**
     * {@code pushNav}: signature abort, current draft, the reply capture, the
     * arriving page kind's reset, the push, the draft load, the reply load.
     * Every step runs on the page it must run on — the resets and captures on
     * the page being left, the loads on the page that has just arrived. There is
     * no transition arm and no root-transition cleanup: the push is synchronous.
     */
    @Test
    void pushNavRunsTheCascadeInTheFrozenOrder() {
        Recorder host = new Recorder();
        NavStackController controller = onStack(host, List.of(NavPage.of(AppPage.CHAT_LIST)));

        controller.pushNav(NavPage.of(AppPage.WORLD_CHAT));

        assertEquals(List.of(
                "signatureEditLive@CHAT_LIST",
                "persistCurrentDraft@CHAT_LIST",
                "syncReplySlot@CHAT_LIST",
                "clearWorldScroll@CHAT_LIST",
                "adoptWorldDraft@WORLD_CHAT",
                "armReplyFor@WORLD_CHAT"), host.calls,
                "pushNav's cascade order is the contract");
        assertEquals(AppPage.WORLD_CHAT, controller.topPage(),
                "the push lands the stack this frame (no transition to wait for)");
    }

    /** The two other reset arms stay page-specific. */
    @Test
    void pushNavResetsOnlyTheArrivingPageKind() {
        Recorder settings = new Recorder();
        NavStackController toSettings = onStack(settings, List.of(NavPage.of(AppPage.CHAT_LIST)));
        toSettings.pushNav(NavPage.settingsSection(SettingsSection.APPEARANCE));
        assertEquals(List.of(
                "signatureEditLive@CHAT_LIST",
                "persistCurrentDraft@CHAT_LIST",
                "syncReplySlot@CHAT_LIST",
                "clearSettingsPageState@CHAT_LIST",
                "armReplyFor@SETTINGS_SECTION"), settings.calls);

        Recorder profile = new Recorder();
        NavStackController toProfile = onStack(profile, List.of(NavPage.of(AppPage.CHAT_LIST)));
        toProfile.pushNav(NavPage.of(AppPage.PROFILE_DETAIL));
        assertEquals(List.of(
                "signatureEditLive@CHAT_LIST",
                "persistCurrentDraft@CHAT_LIST",
                "syncReplySlot@CHAT_LIST",
                "clearDetailScroll@CHAT_LIST",
                "armReplyFor@PROFILE_DETAIL"), profile.calls);
    }

    /** A live signature edit is aborted, without committing, before anything else runs. */
    @Test
    void pushNavAbortsTheLiveSignatureEditFirst() {
        Recorder host = new Recorder();
        host.signatureEditing = true;
        NavStackController controller = onStack(host, List.of(NavPage.of(AppPage.CHAT_LIST)));

        controller.pushNav(NavPage.of(AppPage.WORLD_CHAT));

        assertEquals("signatureEditLive@CHAT_LIST", host.calls.get(0));
        assertEquals("abortSignatureEdit@CHAT_LIST", host.calls.get(1),
                "the abort must precede the draft save (a commit on navigation is the bug)");
        assertEquals("persistCurrentDraft@CHAT_LIST", host.calls.get(2));
    }

    /**
     * {@code popNow}: the reply capture and the private draft are persisted
     * while the page is still on top, the transient bus and the settings
     * sub-page are reset, the stack pops, and only then does the subject fall
     * back — so the page that is leaving never redraws as the local player.
     */
    @Test
    void popNowResetsThenPopsThenDropsTheSubject() {
        Recorder host = new Recorder();
        NavStackController controller = onStack(host,
                List.of(NavPage.of(AppPage.CHAT_LIST), NavPage.of(AppPage.PROFILE_DETAIL)));

        controller.popNow();

        assertEquals(List.of(
                "syncReplySlot@PROFILE_DETAIL",
                "clearTransientWorldUi@PROFILE_DETAIL",
                "clearSettingsPageState@PROFILE_DETAIL",
                "dropProfileSubject@CHAT_LIST",
                "armReplyFor@CHAT_LIST"), host.calls,
                "the subject reset must observe the page underneath, not the one being popped");
    }

    /** The private draft is saved against the leaving page, before the pop. */
    @Test
    void popNowPersistsThePrivateDraftAgainstTheLeavingPage() {
        Recorder host = new Recorder();
        host.composer = "unsent text";
        NavStackController controller = onStack(host,
                List.of(NavPage.of(AppPage.CHAT_LIST), NavPage.privateChat(PARTNER)));

        controller.popNow();

        assertEquals(List.of(
                "syncReplySlot@PRIVATE_CHAT",
                "composerTextNow@PRIVATE_CHAT",
                "persistPrivateDraft@PRIVATE_CHAT",
                "clearTransientWorldUi@PRIVATE_CHAT",
                "clearSettingsPageState@PRIVATE_CHAT",
                "armReplyFor@CHAT_LIST"), host.calls);
        assertEquals(List.of(PARTNER + "=unsent text"), host.drafts,
                "the live composer text is what gets stored, against the page that is leaving");
    }

    /**
     * Bug regression (user report: "动画期间点返回直接返回没有任何过渡" and its
     * opposite, "退出外观设置再点外观无法打断"): {@code popPage} lands the pop
     * immediately through the full {@code popNow} cascade — with motion on or
     * off, identically. There is no pending state and no armed transition left
     * behind, so the very next push (re-opening the section) runs a clean
     * cascade whose arrival re-stamps the entrance.
     */
    @Test
    void popPageLandsImmediatelyWithAndWithoutMotion() {
        AtomChatConfig.get().animationEnabled = false;
        Recorder off = new Recorder();
        NavStackController snapped = onStack(off,
                List.of(NavPage.of(AppPage.CHAT_LIST), NavPage.of(AppPage.WORLD_CHAT)));
        snapped.popPage();
        assertEquals(AppPage.CHAT_LIST, snapped.topPage(), "motion off lands the pop this frame");

        AtomChatConfig.get().animationEnabled = true;
        Recorder on = new Recorder();
        NavStackController animated = onStack(on,
                List.of(NavPage.of(AppPage.CHAT_LIST), NavPage.of(AppPage.WORLD_CHAT)));
        animated.popPage();
        assertEquals(AppPage.CHAT_LIST, animated.topPage(),
                "the Dango semantics have no exit phase: the pop lands this frame with motion on too");
        assertEquals(List.of(
                "signatureEditLive@WORLD_CHAT",
                "syncReplySlot@WORLD_CHAT",
                "clearTransientWorldUi@WORLD_CHAT",
                "clearSettingsPageState@WORLD_CHAT",
                "armReplyFor@CHAT_LIST"), on.calls,
                "the pop runs the full popNow cascade immediately");
    }

    /** A pop that would empty the stack is refused outright. */
    @Test
    void popPageRefusesToEmptyTheStack() {
        Recorder host = new Recorder();
        NavStackController controller = onStack(host, List.of(NavPage.of(AppPage.CHAT_LIST)));

        controller.popPage();

        assertEquals(List.of("signatureEditLive@CHAT_LIST"), host.calls,
                "the last page is never popped, and nothing else runs");
        assertEquals(1, controller.snapshot().size());
    }

    /** A root page on top pops without any of the detail cascade. */
    @Test
    void popPageFromARootOnlyPopsTheStack() {
        Recorder host = new Recorder();
        NavStackController controller = onStack(host, List.of(NavPage.of(AppPage.CHAT_LIST)));
        host.calls.clear();
        controller.pushNav(NavPage.of(AppPage.SETTINGS));
        host.calls.clear();

        controller.popPage();

        assertEquals(List.of("signatureEditLive@SETTINGS"), host.calls);
        assertEquals(1, controller.snapshot().size());
        assertEquals(AppPage.CHAT_LIST, controller.topPage());
    }

    /**
     * {@code switchRoot}: signature abort, the leaving profile subject, the
     * draft and root scroll, the stack replacement, then the tab selection — the
     * bar tweens its own capsule from there. There is no span, style or motion
     * arm left: the arriving root page's entrance overlay plays through the
     * composite's key stamp.
     */
    @Test
    void switchRootReplacesTheStackAndSelectsTheTab() {
        Recorder host = new Recorder();
        NavStackController controller = onStack(host, List.of(NavPage.of(AppPage.CHAT_LIST)));

        controller.switchRoot(AppPage.PROFILE);

        assertEquals(List.of(
                "signatureEditLive@CHAT_LIST",
                "persistCurrentDraft@CHAT_LIST",
                "clearRootScroll@CHAT_LIST",
                "selectTabNow[PROFILE]@PROFILE"), host.calls);
        assertEquals(AppPage.PROFILE, controller.topPage());
    }

    /** Leaving the profile root drops the injected subject before the draft is saved. */
    @Test
    void switchRootDropsTheProfileSubjectBeforeSavingTheDraft() {
        Recorder host = new Recorder();
        NavStackController controller = onStack(host, List.of(NavPage.of(AppPage.PROFILE)));

        controller.switchRoot(AppPage.CHAT_LIST);

        assertEquals(List.of(
                "signatureEditLive@PROFILE",
                "dropProfileSubject@PROFILE",
                "persistCurrentDraft@PROFILE",
                "clearRootScroll@PROFILE",
                "selectTabNow[CHAT_LIST]@CHAT_LIST"), host.calls);
    }

    /** A root switch only ever targets a root page. */
    @Test
    void switchRootIgnoresANonRootTarget() {
        Recorder host = new Recorder();
        NavStackController controller = onStack(host, List.of(NavPage.of(AppPage.CHAT_LIST)));

        controller.switchRoot(AppPage.WORLD_CHAT);

        assertEquals(List.of(), host.calls);
        assertEquals(AppPage.CHAT_LIST, controller.topPage());
    }

    // ------------------------------------------------------------- queries

    /**
     * The read-only rule is judged on the page it is handed, never on the stack
     * top: a page's render state is resolved from its own entry, so a check that
     * read the top would answer for the wrong page.
     */
    @Test
    void readOnlyIsJudgedPerPageAndNotByTheStackTop() {
        Recorder host = new Recorder();
        NavStackController controller = onStack(host,
                List.of(NavPage.of(AppPage.CHAT_LIST), NavPage.of(AppPage.WORLD_CHAT)));
        host.partnerOnline = false;

        NavPage privatePage = NavPage.privateChat(OFFLINE);

        assertTrue(controller.isPrivateReadOnlyFor(privatePage),
                "an offline partner's page is read-only, whoever is on top of the stack");
        assertFalse(controller.isPrivateReadOnlyFor(NavPage.of(AppPage.WORLD_CHAT)),
                "the page on top is a public feed; it is never read-only");
        assertFalse(controller.isPrivateReadOnly(),
                "the stack top is the public feed, so the live check is false");
        assertFalse(controller.isPrivateReadOnlyFor(null));
    }

    /** A blocked partner is read-only even while online. */
    @Test
    void aBlockedPartnerIsReadOnlyWhileOnline() {
        Recorder host = new Recorder();
        NavStackController controller = onStack(host, List.of(NavPage.of(AppPage.CHAT_LIST)));
        AtomChatConfig.get().blockedPlayers = List.of("Partner");

        assertTrue(controller.isPrivateReadOnlyFor(NavPage.privateChat(PARTNER)));
        assertEquals("atomchat.private.blocked", controller.readOnlyHintForNav(NavPage.privateChat(PARTNER)));
        assertEquals("atomchat.private.offline", controller.readOnlyHintForNav(NavPage.privateChat(OFFLINE)));
    }

    /** The queries all answer from the one stack. */
    @Test
    void queriesFollowTheOneStack() {
        Recorder host = new Recorder();
        host.partnerOnline = false;
        NavStackController controller = onStack(host,
                List.of(NavPage.of(AppPage.CHAT_LIST), NavPage.privateChat(PARTNER)));

        assertEquals(NavPage.privateChat(PARTNER), controller.topNav());
        assertEquals(AppPage.PRIVATE_CHAT, controller.topPage());
        assertEquals(PARTNER, controller.activePrivateTarget());
        assertTrue(controller.isWorldChatPage(), "the historical name also covers a private chat");
        assertTrue(controller.isPrivateReadOnly(), "an offline partner reads as read-only here too");
        assertEquals(List.of(NavPage.of(AppPage.CHAT_LIST), NavPage.privateChat(PARTNER)),
                controller.snapshot());
    }

    // ---------------------------------------------------------- the doors

    /** Every door funnels into the one push cascade. */
    @Test
    void everyDoorFunnelsIntoTheOnePushCascade() {
        for (NavPage target : List.of(NavPage.of(AppPage.WORLD_CHAT),
                NavPage.privateChat(PARTNER),
                NavPage.settingsSection(SettingsSection.APPEARANCE),
                NavPage.of(AppPage.PROFILE_DETAIL))) {
            Recorder host = new Recorder();
            NavStackController controller = onStack(host, List.of(NavPage.of(AppPage.CHAT_LIST)));

            switch (target.page()) {
                case WORLD_CHAT -> controller.openWorldChat();
                case PRIVATE_CHAT -> controller.openPrivateChat(PARTNER);
                case SETTINGS_SECTION -> controller.openSettingsSection(SettingsSection.APPEARANCE);
                default -> controller.pushPage(target.page());
            }

            assertEquals(target, controller.topNav(), "the door must land on the target page");
            assertEquals("persistCurrentDraft@CHAT_LIST", host.calls.get(1),
                    "the door must run the full cascade, not a bare push: " + host.calls);
        }
    }

    // -------------------------------------------------------- construction

    /** The vanilla chat key opens on the public feed, pushed over the list. */
    @Test
    void directWorldOpensOnThePublicFeed() {
        Recorder host = new Recorder();
        NavStackController controller = new NavStackController(host, false);

        assertEquals(List.of(NavPage.of(AppPage.CHAT_LIST), NavPage.of(AppPage.WORLD_CHAT)),
                controller.snapshot());
        assertEquals(AppPage.WORLD_CHAT, controller.topPage());
        assertEquals(List.of(), host.calls, "a pushed detail page selects no tab");
    }

    /** The AtomChat key replays the last stack, and lands the tab and the stores on it. */
    @Test
    void restoreReplaysTheSavedStack() {
        AtomChatState.save(List.of(NavPage.of(AppPage.CHAT_LIST), NavPage.privateChat(PARTNER)));
        Recorder host = new Recorder();
        NavStackController controller = new NavStackController(host, true);

        assertEquals(List.of(NavPage.of(AppPage.CHAT_LIST), NavPage.privateChat(PARTNER)),
                controller.snapshot());
        assertEquals(PARTNER, controller.activePrivateTarget());
        assertEquals(List.of(), host.calls, "a private page selects no tab");
    }

    /** A restored root stack selects its tab before anything can read the bar. */
    @Test
    void restoreOfARootStackSelectsItsTab() {
        AtomChatState.save(List.of(NavPage.of(AppPage.SETTINGS)));
        Recorder host = new Recorder();
        NavStackController controller = new NavStackController(host, true);

        assertEquals(AppPage.SETTINGS, controller.topPage());
        assertEquals(List.of("selectTabNow[SETTINGS]@?"), host.calls,
                "the tab is placed while the controller is still being built");
    }

    /** The port is told what the stack is, not what the caller guessed. */
    @Test
    void activePrivateTargetIsEmptyOnAnyOtherPage() {
        Recorder host = new Recorder();
        NavStackController controller = onStack(host, List.of(NavPage.of(AppPage.CHAT_LIST)));

        assertNull(controller.activePrivateTarget());
        assertFalse(controller.isWorldChatPage());
        assertFalse(controller.isPrivateReadOnly());
    }

    // -------------------------------------------------- interrupt regressions

    /**
     * Bug regression (user report: "退出外观设置再点外观无法打断"): back out of
     * a settings section, then immediately re-open one. The pop has already
     * landed, so the re-push runs a clean cascade from the root — no pending
     * state, no armed transition, and a back click afterwards is a clean pop.
     */
    @Test
    void exitingASectionThenReopeningItLeavesNoPendingState() {
        Recorder host = new Recorder();
        NavStackController controller = onStack(host, List.of(NavPage.of(AppPage.SETTINGS),
                NavPage.settingsSection(SettingsSection.APPEARANCE)));

        controller.popPage();
        assertEquals(AppPage.SETTINGS, controller.topPage(), "the pop landed immediately");
        assertEquals(1, controller.snapshot().size());

        controller.pushNav(NavPage.settingsSection(SettingsSection.APPEARANCE));
        assertEquals(AppPage.SETTINGS_SECTION, controller.topPage());
        assertEquals(2, controller.snapshot().size());
        assertTrue(host.calls.contains("clearSettingsPageState@SETTINGS"),
                "the re-push runs the section's reset cascade: " + host.calls);

        controller.popPage();
        assertEquals(AppPage.SETTINGS, controller.topPage(),
                "the re-push did not stack a duplicate — back lands on the root");
    }

    /**
     * A tab click replaces the stack wholesale: whatever detail page was on top
     * dies with it, and a back click on the fresh root is a no-op.
     */
    @Test
    void switchRootReplacesTheWholeStack() {
        Recorder host = new Recorder();
        NavStackController controller = onStack(host, List.of(
                NavPage.of(AppPage.CHAT_LIST), NavPage.of(AppPage.PROFILE_DETAIL)));

        controller.switchRoot(AppPage.SETTINGS);

        assertEquals(AppPage.SETTINGS, controller.topPage());
        assertEquals(1, controller.snapshot().size());
        controller.popPage();
        assertEquals(AppPage.SETTINGS, controller.topPage());
        assertEquals(1, controller.snapshot().size());
    }

    /**
     * A second push of the page already on top is a no-op — unconditionally now:
     * the push is synchronous, so there is no "page in flight" state to race,
     * and pushing a duplicate was never a meaningful action (it would turn the
     * next back click into a no-op).
     */
    @Test
    void pushNavIgnoresADuplicateOfTheTopPage() {
        Recorder host = new Recorder();
        NavStackController controller = onStack(host, List.of(
                NavPage.of(AppPage.CHAT_LIST), NavPage.of(AppPage.WORLD_CHAT)));
        host.calls.clear();

        controller.pushNav(NavPage.of(AppPage.WORLD_CHAT));

        assertEquals(2, controller.snapshot().size(), "no duplicate of the top page");
        assertEquals(List.of(), host.calls, "the duplicate never starts a cascade");
    }
}
