package com.atom.chat.watut;

import com.atom.chat.chat.PlayerRef;
import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsSection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link TypingIndicatorSource} offline: which page may show the typing hint,
 * and that a live private page hands the question to {@link WatutBridge}.
 *
 * <p>The read-only fact comes in as a parameter, so the offline-or-blocked half
 * of the rule is testable here without a client: the screen's
 * {@code isPrivateReadOnlyFor} is what folds {@code BlockList.isBlocked} in, and
 * a blocked partner therefore arrives as {@code readOnly == true} — there is no
 * second block-list check to cover in this class.
 *
 * <p>WATUT is not on the test classpath, so the bridge's own verdict is always
 * {@code false} here (its documented cheap-false path). The wired case is
 * therefore pinned by equality against the bridge's verdict for the same
 * partner, not by a fake bridge: driving a {@code true} verdict would need
 * reflection stubs under {@code com.corosus.watut}, which is deliberately out
 * of scope for this block. That one line stays on the live-probe list, the way
 * block 3/D3 registered the send paths that need a real client.
 *
 * <p>{@link Platform} is installed with a temporary provider (the same shape the
 * other shared tests use), because the bridge asks it whether WATUT is loaded
 * before it resolves anything.
 */
class TypingIndicatorSourceTest {
    private static final PlayerRef ALICE = PlayerRef.of(
            UUID.nameUUIDFromBytes("alice".getBytes(StandardCharsets.UTF_8)), "Alice");
    private static final PlayerRef BOB = PlayerRef.of(
            UUID.nameUUIDFromBytes("bob".getBytes(StandardCharsets.UTF_8)), "Bob");

    @TempDir
    static Path platformRoot;

    @BeforeAll
    static void installTemporaryPlatform() {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return platformRoot.resolve("config");
            }

            @Override
            public Path gameDir() {
                return platformRoot.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    @Test
    void thereIsNoHintWithoutAPage() {
        assertFalse(TypingIndicatorSource.partnerTypingFor(null, false),
                "no page, no partner to be typing");
        assertFalse(TypingIndicatorSource.partnerTypingFor(null, true));
    }

    @Test
    void onlyThePrivateChatCanShowTheHint() {
        NavPage[] others = {
                NavPage.of(AppPage.CHAT_LIST),
                NavPage.of(AppPage.PROFILE),
                NavPage.of(AppPage.SETTINGS),
                NavPage.of(AppPage.WORLD_CHAT),
                NavPage.of(AppPage.PROFILE_DETAIL),
                NavPage.settingsSection(SettingsSection.CHAT),
        };
        for (NavPage page : others) {
            assertFalse(TypingIndicatorSource.partnerTypingFor(page, false),
                    page.page() + " has no partner, so it never shows the typing hint");
        }
    }

    @Test
    void aReadOnlyPrivatePageNeverShowsTheHint() {
        NavPage page = NavPage.privateChat(ALICE);
        assertFalse(TypingIndicatorSource.partnerTypingFor(page, true),
                "a read-only private page is exactly the offline-or-blocked case; the fact is "
                        + "the screen's isPrivateReadOnlyFor, which folds BlockList.isBlocked in");
        assertEquals(WatutBridge.isTyping(ALICE.uuid()),
                TypingIndicatorSource.partnerTypingFor(page, false),
                "and with the fact false the question goes on to the bridge");
    }

    @Test
    void aPrivatePageWhosePartnerHasNoUuidNeverShowsTheHint() {
        NavPage page = NavPage.privateChat(PlayerRef.of(null, "Ghost"));
        assertFalse(TypingIndicatorSource.partnerTypingFor(page, false),
                "the bridge's own null-uuid guard answers false — no hint for an unknown partner");
    }

    @Test
    void theHintIsTheBridgesVerdictForThatPartner() {
        NavPage alice = NavPage.privateChat(ALICE);
        NavPage bob = NavPage.privateChat(BOB);

        assertEquals(WatutBridge.isTyping(ALICE.uuid()),
                TypingIndicatorSource.partnerTypingFor(alice, false),
                "the hint is whatever the bridge reports for that page's partner");
        assertEquals(WatutBridge.isTyping(BOB.uuid()),
                TypingIndicatorSource.partnerTypingFor(bob, false),
                "each page asks about its own partner, not about whatever is on the stack");

        assertFalse(WatutBridge.isTyping(ALICE.uuid()),
                "offline guard: WATUT is not loaded in the test JVM, so the bridge says false "
                        + "(see the class comment on why a true verdict is not faked here)");
    }
}
