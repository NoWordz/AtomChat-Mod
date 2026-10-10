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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The D24a contract: the order of every push / pop / root-switch cascade.
 *
 * <p><b>Why an order test and not a behaviour test.</b> The nav cascade is ten
 * to twelve cross-domain steps, and the order <em>is</em> the contract: a
 * subject reset that runs before the stack pop redraws the sliding-out page as
 * the local player's profile mid-animation; a draft persisted after the stack
 * moved saves it to the wrong conversation; a signature edit aborted after the
 * borrowed field was already touched commits what navigation must never commit.
 * None of that is visible in a golden frame (the frames render page objects and
 * never run a cascade), so the sequence is pinned here, step by step, through a
 * recording port — and every assertion is written so that swapping two steps
 * turns it red.
 *
 * <p>The recorder also records <em>which page the stack was on</em> at every
 * step. That is what makes "before the pop" / "after the pop" falsifiable
 * rather than a claim about the source: the step must observe the page it is
 * required to observe.
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
        // The tab-switch arm reads the config through the port; pin it so the
        // recorded motion is a property of the code and not of this machine's
        // client config file.
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
        String blockedKey;
        String offlineKey;

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
        public void beginPageNav(NavPage from, NavPage to, boolean popPending) {
            note("beginPageNav[" + from.page() + "->" + to.page() + ",pop=" + popPending + "]");
        }

        @Override
        public void abandonPageNav() {
            note("abandonPageNav");
        }

        @Override
        public void abandonRootTransition() {
            note("abandonRootTransition");
        }

        @Override
        public boolean rootTransitionLive() {
            note("rootTransitionLive");
            return rootTransitionRunning;
        }

        @Override
        public boolean navStyleIsZoom() {
            note("navStyleIsZoom");
            return AtomChatConfig.get().pageNavStyle == AtomChatConfig.PageNavStyle.ZOOM;
        }

        @Override
        public void pinRootTabCapsule(AppPage from) {
            note("pinRootTabCapsule[" + from + "]");
        }

        @Override
        public void markRootTabSpan(AppPage from, AppPage to) {
            note("markRootTabSpan[" + from + "->" + to + "]");
        }

        @Override
        public void applyRootTabMotion(boolean zoom) {
            note("applyRootTabMotion[zoom=" + zoom + "]");
        }

        @Override
        public void resetRootTabCapsule() {
            note("resetRootTabCapsule");
        }

        @Override
        public void selectTabNow(AppPage root) {
            note("selectTabNow[" + root + "]");
        }

        boolean rootTransitionRunning;
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
     * {@code pushNav}: signature abort, current draft, the target page's scroll
     * reset, the nav arming, the push, the draft load, the reply load, and the
     * root-transition cleanup last. Every step runs on the page it must run on —
     * the resets and the arming on the page being left, the loads on the page
     * that has just arrived.
     */
    @Test
    void pushNavRunsTheCascadeInTheFrozenOrder() {
        Recorder host = new Recorder();
        NavStackController controller = onStack(host, List.of(NavPage.of(AppPage.CHAT_LIST)));

        controller.pushNav(NavPage.of(AppPage.WORLD_CHAT));

        assertEquals(List.of(
                "signatureEditLive@CHAT_LIST",
                "persistCurrentDraft@CHAT_LIST",
                "clearWorldScroll@CHAT_LIST",
                "pinRootTabCapsule[CHAT_LIST]@CHAT_LIST",
                "beginPageNav[CHAT_LIST->WORLD_CHAT,pop=false]@CHAT_LIST",
                "adoptWorldDraft@WORLD_CHAT",
                "armReplyFor@WORLD_CHAT",
                "abandonRootTransition@WORLD_CHAT"), host.calls,
                "pushNav's cascade order is the contract");
        assertEquals(AppPage.WORLD_CHAT, controller.topPage());
    }

    /** The two other reset arms, and the profile-only one, stay page-specific. */
    @Test
    void pushNavResetsOnlyTheArrivingPageKind() {
        Recorder settings = new Recorder();
        NavStackController toSettings = onStack(settings, List.of(NavPage.of(AppPage.CHAT_LIST)));
        toSettings.pushNav(NavPage.settingsSection(SettingsSection.APPEARANCE));
        assertEquals(List.of(
                "signatureEditLive@CHAT_LIST",
                "persistCurrentDraft@CHAT_LIST",
                "clearSettingsPageState@CHAT_LIST",
                "pinRootTabCapsule[CHAT_LIST]@CHAT_LIST",
                "beginPageNav[CHAT_LIST->SETTINGS_SECTION,pop=false]@CHAT_LIST",
                "armReplyFor@SETTINGS_SECTION",
                "abandonRootTransition@SETTINGS_SECTION"), settings.calls);

        Recorder profile = new Recorder();
        NavStackController toProfile = onStack(profile, List.of(NavPage.of(AppPage.CHAT_LIST)));
        toProfile.pushNav(NavPage.of(AppPage.PROFILE_DETAIL));
        assertEquals(List.of(
                "signatureEditLive@CHAT_LIST",
                "persistCurrentDraft@CHAT_LIST",
                "clearDetailScroll@CHAT_LIST",
                "pinRootTabCapsule[CHAT_LIST]@CHAT_LIST",
                "beginPageNav[CHAT_LIST->PROFILE_DETAIL,pop=false]@CHAT_LIST",
                "armReplyFor@PROFILE_DETAIL",
                "abandonRootTransition@PROFILE_DETAIL"), profile.calls);
    }

    /** A detail-to-detail push arms the nav but pins no root capsule (no root page left). */
    @Test
    void detailToDetailPushArmsTheNavWithoutPinningTheCapsule() {
        Recorder host = new Recorder();
        NavStackController controller = onStack(host,
                List.of(NavPage.of(AppPage.CHAT_LIST), NavPage.privateChat(PARTNER)));

        controller.pushNav(NavPage.of(AppPage.PROFILE_DETAIL));

        assertEquals(List.of(
                "signatureEditLive@PRIVATE_CHAT",
                "persistCurrentDraft@PRIVATE_CHAT",
                "clearDetailScroll@PRIVATE_CHAT",
                "beginPageNav[PRIVATE_CHAT->PROFILE_DETAIL,pop=false]@PRIVATE_CHAT",
                "armReplyFor@PROFILE_DETAIL",
                "abandonRootTransition@PROFILE_DETAIL"), host.calls);
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
     * {@code popNow}: the private draft is persisted and the active store
     * cleared while the page is still on top, the transient bus and the settings
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
                "composerTextNow@PRIVATE_CHAT",
                "persistPrivateDraft@PRIVATE_CHAT",
                "clearTransientWorldUi@PRIVATE_CHAT",
                "clearSettingsPageState@PRIVATE_CHAT",
                "armReplyFor@CHAT_LIST"), host.calls);
        assertEquals(List.of(PARTNER + "=unsent text"), host.drafts,
                "the live composer text is what gets stored, against the page that is leaving");
    }

    /**
     * {@code popPage}: decorative motion off applies the pop here and now; with
     * motion on the page stays on top and only the transition is armed — the
     * stack changes in the transition's own finish step.
     */
    @Test
    void popPageLandsImmediatelyWithoutMotionAndDefersWithIt() {
        AtomChatConfig.get().animationEnabled = false;
        Recorder off = new Recorder();
        NavStackController snapped = onStack(off,
                List.of(NavPage.of(AppPage.CHAT_LIST), NavPage.of(AppPage.WORLD_CHAT)));

        snapped.popPage();

        assertEquals(AppPage.CHAT_LIST, snapped.topPage(), "motion off must land the pop this frame");
        assertFalse(off.calls.contains("beginPageNav[WORLD_CHAT->CHAT_LIST,pop=true]"),
                "motion off must not arm an animation that will never be stepped");

        AtomChatConfig.get().animationEnabled = true;
        Recorder on = new Recorder();
        NavStackController animated = onStack(on,
                List.of(NavPage.of(AppPage.CHAT_LIST), NavPage.of(AppPage.WORLD_CHAT)));

        animated.popPage();

        assertEquals(AppPage.WORLD_CHAT, animated.topPage(),
                "with motion on the leaving page must stay on top until the transition finishes");
        assertTrue(on.calls.contains("beginPageNav[WORLD_CHAT->CHAT_LIST,pop=true]@WORLD_CHAT"),
                "with motion on the pop is armed through the one start helper");
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
     * draft and root scroll, the snap of a running transition, then the span,
     * the style resolution, the motion arm and the capsule reset — in that
     * order, with the capsule last so it starts from the source cell.
     */
    @Test
    void switchRootArmsSpanStyleMotionAndCapsuleInOrder() {
        Recorder host = new Recorder();
        NavStackController controller = onStack(host, List.of(NavPage.of(AppPage.CHAT_LIST)));

        controller.switchRoot(AppPage.PROFILE);

        assertEquals(List.of(
                "signatureEditLive@CHAT_LIST",
                "persistCurrentDraft@CHAT_LIST",
                "clearRootScroll@CHAT_LIST",
                "rootTransitionLive@CHAT_LIST",
                "markRootTabSpan[CHAT_LIST->PROFILE]@PROFILE",
                "navStyleIsZoom@PROFILE",
                "applyRootTabMotion[zoom=true]@PROFILE",
                "resetRootTabCapsule@PROFILE"), host.calls);
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
                "rootTransitionLive@PROFILE",
                "markRootTabSpan[PROFILE->CHAT_LIST]@CHAT_LIST",
                "navStyleIsZoom@CHAT_LIST",
                "applyRootTabMotion[zoom=true]@CHAT_LIST",
                "resetRootTabCapsule@CHAT_LIST"), host.calls);
    }

    /** Without motion the tab lands in one frame: no span, no capsule travel. */
    @Test
    void switchRootLandsInOneFrameWithoutMotion() {
        AtomChatConfig.get().animationEnabled = false;
        Recorder host = new Recorder();
        NavStackController controller = onStack(host, List.of(NavPage.of(AppPage.CHAT_LIST)));

        controller.switchRoot(AppPage.SETTINGS);

        // The tab is selected before the stack moves, as it always was: the bar
        // reads the index it is given, not the page the stack is about to carry.
        assertEquals(List.of(
                "signatureEditLive@CHAT_LIST",
                "persistCurrentDraft@CHAT_LIST",
                "clearRootScroll@CHAT_LIST",
                "abandonRootTransition@CHAT_LIST",
                "selectTabNow[SETTINGS]@CHAT_LIST"), host.calls);
        assertEquals(AppPage.SETTINGS, controller.topPage());
    }

    /** A running tab transition is snapped before the new one is armed. */
    @Test
    void switchRootSnapsARunningTransitionFirst() {
        Recorder host = new Recorder();
        host.rootTransitionRunning = true;
        NavStackController controller = onStack(host, List.of(NavPage.of(AppPage.CHAT_LIST)));

        controller.switchRoot(AppPage.PROFILE);

        int snap = host.calls.indexOf("abandonRootTransition@CHAT_LIST");
        int span = host.calls.indexOf("markRootTabSpan[CHAT_LIST->PROFILE]@PROFILE");
        assertTrue(snap >= 0, "a running transition must be snapped: " + host.calls);
        assertTrue(span > snap, "the snap must precede the new span: " + host.calls);
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
     * top: a transition composites two pages in one frame, so a check that read
     * the top would answer for the wrong page.
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
            assertTrue(host.calls.contains("beginPageNav[CHAT_LIST->" + target.page() + ",pop=false]@CHAT_LIST"),
                    "the door must arm the nav through the one start helper: " + host.calls);
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

    /** The recorder answers the style from the config, so both arms are exercised. */
    @Test
    void theStyleArmFollowsTheConfig() {
        AtomChatConfig.get().pageNavStyle = AtomChatConfig.PageNavStyle.SLIDE;
        Recorder host = new Recorder();
        NavStackController controller = onStack(host, List.of(NavPage.of(AppPage.CHAT_LIST)));

        controller.switchRoot(AppPage.PROFILE);

        assertTrue(host.calls.contains("applyRootTabMotion[zoom=false]@PROFILE"),
                "the SLIDE style must arm the slide motion: " + host.calls);
        assertNotEquals(0, host.calls.size());
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
}
