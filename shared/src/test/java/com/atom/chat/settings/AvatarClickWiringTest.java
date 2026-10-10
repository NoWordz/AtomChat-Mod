package com.atom.chat.settings;

import com.atom.chat.avatar.AvatarStore;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.page.ProfilePage;
import com.atom.chat.platform.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AvatarClickWiring}'s competition window, poke semantics and page
 * actions, offline: the three click branches, the per-frame verdict with a
 * controllable clock, the four poke paths and the profile handler's four
 * card/clipboard actions.
 *
 * <p><b>Scope, stated up front.</b> A recording host stands in for the shell
 * and a real {@link ProfilePage} for the page, so "the page got this subject"
 * and "the toast says local vs sent" are asserted on the real objects. The
 * clock is a field of the recording host, so the window's edges are exact
 * instead of timing-dependent. NOT covered: the real click path (the avatar
 * hit test lives in the screen's input handler), the system file pickers, the
 * real poke round-trip over a server connection, and everything the page
 * draws — those are the D18b probe list.</p>
 */
class AvatarClickWiringTest {
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

    @TempDir
    Path tmp;

    private static final UUID SELF = new UUID(0x11L, 0x22L);
    private static final UUID ALICE = new UUID(0x33L, 0x44L);

    /** What the wiring asked of the shell, in order. */
    private static final class Recorder implements AvatarClickWiring.Host {
        final List<String> calls = new ArrayList<>();
        long now = 1_000L;
        boolean pokeConfig = true;
        boolean animations = true;
        boolean pokeSendResult = true;
        UUID local = SELF;
        UUID remoteFrom = ALICE;
        String remoteName = "Alice";
        boolean blocked;
        String labelText = "poked";
        boolean clipboard = true;
        boolean avatarCleared = true;
        boolean bannerCleared = true;
        Consumer<UUID> installed;
        ProfilePage page;

        @Override
        public ProfilePage profilePage() {
            return page;
        }

        @Override
        public void openProfileDetailPage() {
            calls.add("pushProfileDetail");
        }

        @Override
        public UUID localPlayerId() {
            return local;
        }

        @Override
        public boolean pokeConfigEnabled() {
            return pokeConfig;
        }

        @Override
        public boolean animationsEnabled() {
            return animations;
        }

        @Override
        public boolean clearAvatar() {
            calls.add("clearAvatar");
            return avatarCleared;
        }

        @Override
        public boolean pokeSend(UUID target) {
            calls.add("pokeSend:" + target);
            return pokeSendResult;
        }

        @Override
        public void installPokeHandler(Consumer<UUID> handler) {
            calls.add(handler == null ? "handler:null" : "handler:installed");
            installed = handler;
        }

        @Override
        public String displayNameFor(UUID uuid) {
            return uuid == null ? "null" : uuid.equals(remoteFrom) ? remoteName : uuid.toString();
        }

        @Override
        public boolean isBlocked(String name) {
            return blocked;
        }

        @Override
        public void pokeWobble(UUID uuid, long nowMs) {
            calls.add("wobble:" + uuid + "@" + nowMs);
        }

        @Override
        public void playPokeSound() {
            calls.add("sound");
        }

        @Override
        public void feedback(String key, boolean success) {
            calls.add("feedback:" + key + ":" + success);
        }

        @Override
        public String label(String key, String arg) {
            return labelText;
        }

        @Override
        public void feedback(String key, String label, boolean success) {
            calls.add("feedback:" + key + ":" + label + ":" + success);
        }

        @Override
        public void pickAvatarFile() {
            calls.add("pickAvatar");
        }

        @Override
        public void pickBannerFile() {
            calls.add("pickBanner");
        }

        @Override
        public boolean clearBanner() {
            calls.add("clearBanner");
            return bannerCleared;
        }

        @Override
        public boolean copyToClipboard(String text) {
            calls.add("copy:" + text);
            return clipboard;
        }

        @Override
        public void beginSignatureEdit() {
            calls.add("beginSignature");
        }

        @Override
        public void endSignatureEdit(boolean commit) {
            calls.add("endSignature:" + commit);
        }

        long count(String prefix) {
            return calls.stream().filter(c -> c.startsWith(prefix)).count();
        }
    }

    /**
     * A message row's facts. The domain reads a message only through
     * {@link AvatarClickWiring.MessageFacts}, so this test needs no message type
     * at all — the mapping layer's own one is spelled {@code Component} on the
     * official builds and {@code Text} on the yarn one, and naming either here
     * would break the other target's {@code compileTestJava} (§0.5 #3).
     */
    private static final class Row implements AvatarClickWiring.MessageFacts {
        UUID uuid;
        String profileName;
        String senderName;
        boolean own;
        boolean system;

        @Override
        public UUID senderUuid() {
            return uuid;
        }

        @Override
        public String profileName() {
            return profileName;
        }

        @Override
        public String senderName() {
            return senderName;
        }

        @Override
        public boolean own() {
            return own;
        }

        @Override
        public boolean system() {
            return system;
        }
    }

    /** A message from a player: name and uuid, not own, not system. */
    private static Row from(UUID sender, String name) {
        Row row = new Row();
        row.uuid = sender;
        row.profileName = name;
        row.senderName = name;
        return row;
    }

    /** A message row with no identity at all. */
    private static Row anonymous(boolean own, boolean system) {
        Row row = new Row();
        row.own = own;
        row.system = system;
        return row;
    }

    /** A message whose uuid never arrived but whose capture pipeline knows the name. */
    private static Row namedAnonymous() {
        Row row = new Row();
        row.profileName = "Alice";
        row.senderName = "Alice";
        return row;
    }

    private Recorder host;
    private AvatarClickWiring wiring;

    private void setUp() {
        host = new Recorder();
        host.page = new ProfilePage(new ProfilePage.Handler() {
            @Override
            public void openAvatarPicker() {
            }

            @Override
            public void clearAvatar() {
            }

            @Override
            public void openBannerPicker() {
            }

            @Override
            public void clearBanner() {
            }

            @Override
            public boolean copyText(String text) {
                return true;
            }

            @Override
            public void beginSignatureEdit() {
            }

            @Override
            public void endSignatureEdit(boolean commit) {
            }
        }, new AvatarStore(tmp.resolve("avatar")));
        wiring = new AvatarClickWiring(host);
    }

    // ------------------------------------------------- 1. the click's branches

    @Test
    void aDoubleClickPokesAndCancelsThePendingSingleClick() {
        setUp();
        Row alice = from(ALICE, "Alice");
        wiring.handleAvatarClick(1_000L, alice, 7);
        assertTrue(wiring.hasPendingAvatarClick(), "the first click parks");

        wiring.handleAvatarClick(1_100L, alice, 7);
        assertFalse(wiring.hasPendingAvatarClick(), "the second click cancels the pending open");
        assertEquals(1, host.count("pokeSend:" + ALICE), "and pokes once");
        assertEquals(0, host.count("pushProfileDetail"), "a double click never opens the profile");
        assertTrue(host.calls.contains("feedback:atomchat.feedback.poked:poked:true"),
                "the toast is the sent one when the companion channel answered");
    }

    @Test
    void aSingleClickWithPokeParksAndWaitsOutTheWindow() {
        setUp();
        wiring.handleAvatarClick(1_000L, from(ALICE, "Alice"), 7);
        assertTrue(wiring.hasPendingAvatarClick());
        assertEquals(0, host.count("pushProfileDetail"), "nothing opens before the window is out");

        wiring.settlePendingAvatarClick(1_299L, false);
        assertTrue(wiring.hasPendingAvatarClick(), "one ms short is still pending");

        wiring.settlePendingAvatarClick(1_300L, false);
        assertFalse(wiring.hasPendingAvatarClick());
        assertEquals(1, host.count("pushProfileDetail"), "the edge is inclusive: 300ms opens");
        assertEquals(ALICE, host.page.subject().uuid(), "and it opened Alice");
    }

    @Test
    void aSecondClickOnAnotherRowIsNotADoubleClick() {
        setUp();
        wiring.handleAvatarClick(1_000L, from(ALICE, "Alice"), 7);
        wiring.handleAvatarClick(1_050L, from(ALICE, "Alice"), 9);
        assertTrue(wiring.hasPendingAvatarClick(), "a different row index parks instead of poking");
        assertEquals(0, host.count("pokeSend:"), "no poke for a cross-row pair");
    }

    @Test
    void withPokeOffTheProfileOpensAtOnce() {
        setUp();
        host.pokeConfig = false;
        wiring.handleAvatarClick(1_000L, from(ALICE, "Alice"), 7);
        assertFalse(wiring.hasPendingAvatarClick(), "nothing to compete with: no window");
        assertEquals(1, host.count("pushProfileDetail"));
        assertEquals(ALICE, host.page.subject().uuid());
    }

    @Test
    void decorativeMotionOffAlsoOpensAtOnce() {
        setUp();
        host.animations = false;
        wiring.handleAvatarClick(1_000L, from(ALICE, "Alice"), 7);
        assertFalse(wiring.hasPendingAvatarClick());
        assertEquals(1, host.count("pushProfileDetail"));
    }

    @Test
    void theOwnAvatarNeverPokesAndOpensNothing() {
        setUp();
        Row own = from(SELF, "Me");
        own.own = true;
        wiring.handleAvatarClick(1_000L, own, 3);
        assertFalse(wiring.hasPendingAvatarClick(), "own avatar: poke is impossible, so no window");
        assertEquals(0, host.count("pokeSend:"));
        assertEquals(0, host.count("pushProfileDetail"),
                "and openProfileFor refuses own messages, exactly as the inline code did");
    }

    @Test
    void aMessageWithoutASenderUuidNeverPokesAndNeverParks() {
        setUp();
        wiring.handleAvatarClick(1_000L, anonymous(false, false), 3);
        assertEquals(0, host.count("pokeSend:"), "no uuid: there is nobody to poke");
        assertFalse(wiring.hasPendingAvatarClick(),
                "and with no uuid poke is unavailable, so the click opens at once instead of parking");
        assertEquals(0, host.count("pushProfileDetail"),
                "openProfileFor then finds neither a uuid nor a captured name: nothing to show");
    }

    // ------------------------------------------- 2. the per-frame verdict

    @Test
    void aClosingPanelDropsThePendingClick() {
        setUp();
        wiring.handleAvatarClick(1_000L, from(ALICE, "Alice"), 7);
        wiring.settlePendingAvatarClick(1_500L, true);
        assertFalse(wiring.hasPendingAvatarClick());
        assertEquals(0, host.count("pushProfileDetail"), "a closing panel opens nothing");

        wiring.settlePendingAvatarClick(2_000L, false);
        assertEquals(0, host.count("pushProfileDetail"), "and the drop is final");
    }

    @Test
    void settlingWithoutAPendingClickDoesNothing() {
        setUp();
        wiring.settlePendingAvatarClick(9_999L, false);
        assertEquals(0, host.count("pushProfileDetail"));
        assertFalse(wiring.hasPendingAvatarClick());
    }

    @Test
    void theVerictFiresOnlyOnce() {
        setUp();
        wiring.handleAvatarClick(1_000L, from(ALICE, "Alice"), 7);
        wiring.settlePendingAvatarClick(1_400L, false);
        wiring.settlePendingAvatarClick(1_500L, false);
        assertEquals(1, host.count("pushProfileDetail"), "the open happens on the first verdict only");
    }

    // -------------------------------------------------- 3. openProfileFor

    @Test
    void systemAndOwnMessagesNeverOpenAProfile() {
        setUp();
        wiring.openProfileFor(anonymous(true, false));
        wiring.openProfileFor(anonymous(false, true));
        wiring.openProfileFor(null);
        assertEquals(0, host.count("pushProfileDetail"), "none of these has a sender to show");
        assertNull(host.page.subject());
    }

    @Test
    void aMessageWithoutASenderUuidStillOpensByItsCapturedName() {
        setUp();
        wiring.openProfileFor(namedAnonymous());
        assertEquals(1, host.count("pushProfileDetail"),
                "the capture pipeline's name is enough; the uuid is not required");
        assertEquals("Alice", host.page.subject().realName());
    }

    @Test
    void anotherPlayersMessageOpensThatPlayersProfile() {
        setUp();
        wiring.openProfileFor(from(ALICE, "Alice"));
        assertEquals(1, host.count("pushProfileDetail"));
        assertEquals(ALICE, host.page.subject().uuid());
        assertEquals("Alice", host.page.subject().realName());
    }

    // ------------------------------------------------------- 4. the poke paths

    @Test
    void aPokeFromYourselfIsRejected() {
        setUp();
        wiring.onPoked(SELF);
        wiring.onPoked(null);
        assertEquals(0, host.count("wobble:"), "no wobble for your own poke");
        assertEquals(0, host.count("sound"));
        assertEquals(0, host.count("feedback:"), "and no toast");
    }

    @Test
    void aBlockedPlayersPokeIsDroppedSilently() {
        setUp();
        // The block list is real config, so the drop is exercised through the same
        // matcher the chat rows use — not through a stubbed verdict.
        AtomChatConfig cfg = AtomChatConfig.get();
        cfg.blockedPlayers = new ArrayList<>(List.of(host.remoteName));
        try {
            wiring.onPoked(ALICE);
            assertEquals(0, host.count("wobble:"), "a blocked poke is dropped like their chat");
            assertEquals(0, host.count("sound"));
            assertEquals(0, host.count("feedback:"));
        } finally {
            cfg.blockedPlayers = new ArrayList<>();
        }

        wiring.onPoked(ALICE);
        assertTrue(host.calls.stream().anyMatch(c -> c.startsWith("wobble:" + ALICE)),
                "the very same poke lands once the block is lifted");
    }

    @Test
    void anIncomingPokeWobblesCuesAndToasts() {
        setUp();
        wiring.onPoked(ALICE);
        assertTrue(host.calls.stream().anyMatch(c -> c.startsWith("wobble:" + ALICE)), "wobble the sender");
        assertTrue(host.calls.contains("sound"));
        assertTrue(host.calls.contains(
                "feedback:atomchat.feedback.pokeReceived:poked:true"), "toast names the sender");
    }

    @Test
    void theToastIsTruthfulAboutLocalDelivery() {
        setUp();
        host.pokeSendResult = false;
        wiring.handleAvatarClick(1_000L, from(ALICE, "Alice"), 7);
        wiring.handleAvatarClick(1_100L, from(ALICE, "Alice"), 7);
        assertTrue(host.calls.contains("feedback:atomchat.feedback.pokedLocal:poked:true"),
                "without the companion channel the label says local");
    }

    @Test
    void theTestPokeIsSeparateFromTheReceivedOneAndAlwaysReachesItsTarget() {
        setUp();
        // The test poke goes to somebody else and is never filtered as "from yourself",
        // even when the name resolves to the local player: the cue already played.
        wiring.onTestPoked(SELF, "Me");
        assertTrue(host.calls.stream().anyMatch(c -> c.startsWith("wobble:" + SELF)),
                "the test poke wobbles the addressed player");
        assertTrue(host.calls.contains("feedback:atomchat.feedback.pokeReceived:poked:true"),
                "and toasts the addressed name (the label comes from the shell's lookup)");
        assertEquals(0, host.count("sound"), "the cue already played on the client side");
    }

    @Test
    void thePokeLifecycleInstallsAndClearsTheHandler() {
        setUp();
        wiring.poke().install();
        assertTrue(host.calls.contains("handler:installed"));
        assertNotNull(host.installed, "the companion can reach the screen while it is open");

        host.installed.accept(ALICE);
        assertTrue(host.calls.stream().anyMatch(c -> c.startsWith("wobble:" + ALICE)),
                "an incoming poke routes through the installed handler");

        wiring.poke().uninstall();
        assertTrue(host.calls.contains("handler:null"), "and the slot is given back on teardown");
    }

    @Test
    void displayNameFallsBackToTheRawUuid() {
        setUp();
        host.remoteFrom = null;
        host.remoteName = null;
        UUID unknown = new UUID(0x55L, 0x66L);
        wiring.onPoked(unknown);
        assertTrue(host.calls.stream().anyMatch(c -> c.contains(unknown.toString())),
                "an unknown sender still gets a label: the raw uuid");
    }

    // ------------------------------------------------- 5. the page's handler

    @Test
    void theCardPickActionsOpenTheTwoPickers() {
        setUp();
        ProfilePage.Handler handler = wiring.handler();
        handler.openAvatarPicker();
        handler.openBannerPicker();
        assertTrue(host.calls.contains("pickAvatar"));
        assertTrue(host.calls.contains("pickBanner"));
    }

    @Test
    void clearingTheCardReportsSuccessAndFailure() {
        setUp();
        ProfilePage.Handler handler = wiring.handler();

        handler.clearAvatar();
        assertTrue(host.calls.contains("clearAvatar"));
        assertTrue(host.calls.contains("feedback:atomchat.feedback.avatarCleared:true"));

        handler.clearBanner();
        assertTrue(host.calls.contains("clearBanner"));
        assertTrue(host.calls.contains("feedback:atomchat.feedback.bannerCleared:true"));

        host.avatarCleared = false;
        host.bannerCleared = false;
        handler.clearAvatar();
        handler.clearBanner();
        assertTrue(host.calls.contains("feedback:atomchat.feedback.avatarClearFailed:false"));
        assertTrue(host.calls.contains("feedback:atomchat.feedback.bannerClearFailed:false"));
    }

    @Test
    void theClipboardRowGoesThroughTheShellsBridge() {
        setUp();
        assertTrue(wiring.handler().copyText("hello"));
        assertTrue(host.calls.contains("copy:hello"));
    }

    @Test
    void theSignatureBorrowPassesThroughToTheOtherHalf() {
        setUp();
        ProfilePage.Handler handler = wiring.handler();
        handler.beginSignatureEdit();
        handler.endSignatureEdit(false);
        handler.endSignatureEdit(true);
        assertEquals(List.of("beginSignature", "endSignature:false", "endSignature:true"),
                host.calls.stream().filter(c -> c.startsWith("beginSignature") || c.startsWith("endSignature"))
                        .toList());
    }

    // --------------------------------------------- 6. openProfileDetail guard

    @Test
    void openProfileDetailIgnoresAnEmptyReference() {
        setUp();
        wiring.openProfileDetail(null);
        wiring.openProfileDetail(PlayerRef.of(ALICE, null));
        assertEquals(0, host.count("pushProfileDetail"));
        assertNull(host.page.subject());
    }

    @Test
    void theWindowIsThreeHundredMilliseconds() {
        assertEquals(300L, AvatarClickWiring.AVATAR_CLICK_WINDOW_MS,
                "the QQ standard window is a contract, not a tunable");
    }
}
