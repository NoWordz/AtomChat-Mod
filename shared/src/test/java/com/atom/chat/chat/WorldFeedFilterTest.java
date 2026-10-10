package com.atom.chat.chat;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import com.atom.chat.platform.Platform;
import com.atom.chat.text.RichText;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.ShellHeader;
import com.atom.chat.ui.UiLayout;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorldFeedFilter} offline: the cycle, the view filter and the header
 * action, with no screen anywhere in sight.
 *
 * <p>The filter's state is only observable through what it produces — the icon
 * and tint of the header action, and the rows the feed keeps — so the tests
 * assert exactly those. That is deliberate: a state accessor would let the state
 * and its rendering drift apart, and that mapping is one expression.
 *
 * <p>Which messages each mode accepts is {@link MessageFilterTest}'s job (it
 * needs Minecraft types to build a system line); what is pinned here is that the
 * store applies the active mode to the feed, at the right time and without
 * touching the caller's list. Messages come from {@link LocalEcho#build}, so
 * this file never names a Minecraft type.
 */
class WorldFeedFilterTest {
    private static final UUID OWN = UUID.nameUUIDFromBytes("owner".getBytes(StandardCharsets.UTF_8));
    private static final UiLayout.Rect BUTTON = new UiLayout.Rect(10.0F, 10.0F, 20.0F, 20.0F);

    @TempDir
    static Path platformRoot;

    /** The colour assertions read the live config, which goes through Platform. */
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

    private static NavPage world() {
        return NavPage.of(AppPage.WORLD_CHAT);
    }

    private static NavPage priv() {
        return NavPage.privateChat(PlayerRef.of(
                UUID.nameUUIDFromBytes("alice".getBytes(StandardCharsets.UTF_8)), "Alice"));
    }

    private static ChatMessage line(String text) {
        return LocalEcho.build(text, text, null, null, OWN, "Tester", RichText.empty());
    }

    private static ShellHeader.HeaderAction action(WorldFeedFilter filter) {
        return filter.headerActionFor(world(), BUTTON, 0.5F);
    }

    @Test
    void aFreshFilterStartsUnfiltered() {
        WorldFeedFilter filter = new WorldFeedFilter();
        ShellHeader.HeaderAction action = action(filter);
        assertNotNull(action);
        assertSame(AppIcons.ICON_FILTER_PATH, action.icon(), "the funnel means nothing is filtered");
        assertEquals(AtomChatConfig.get().textPrimaryColor, action.color(),
                "an inactive filter is plain text colour, not accent");
    }

    @Test
    void cycleWalksFunnelToSpeakerToPersonAndWraps() {
        WorldFeedFilter filter = new WorldFeedFilter();

        filter.cycle();
        assertSame(AppIcons.ICON_ANNOUNCE_PATH, action(filter).icon(), "ALL -> SYSTEM: speaker");
        assertEquals(AtomChatConfig.get().accentColor, action(filter).color(),
                "an active filter reads at a glance");

        filter.cycle();
        assertSame(AppIcons.ICON_TAB_PROFILE_PATH, action(filter).icon(), "SYSTEM -> PLAYERS: person");

        filter.cycle();
        assertSame(AppIcons.ICON_FILTER_PATH, action(filter).icon(), "PLAYERS -> ALL: back to the funnel");
        assertEquals(AtomChatConfig.get().textPrimaryColor, action(filter).color());
    }

    @Test
    void theActiveModeIsAppliedToTheFeedOnEveryRead() {
        List<ChatMessage> feed = new ArrayList<>(List.of(line("my own line"), line("someone else")));

        WorldFeedFilter filter = new WorldFeedFilter();
        assertSame(feed, filter.filterFeed(feed), "nothing filtered: the same list goes straight back");

        filter.cycle();                                   // SYSTEM
        assertTrue(filter.filterFeed(feed).isEmpty(),
                "system mode keeps only system lines, and this feed has none");

        filter.cycle();                                   // PLAYERS
        assertEquals(feed, filter.filterFeed(feed), "players mode keeps every non-system line");

        filter.cycle();                                   // ALL again
        assertSame(feed, filter.filterFeed(feed), "and the short-circuit is back");
    }

    @Test
    void filteringNeverTouchesTheCallersList() {
        List<ChatMessage> feed = new ArrayList<>(List.of(line("mine"), line("theirs")));
        WorldFeedFilter filter = new WorldFeedFilter();
        filter.cycle();                                   // SYSTEM hides both

        List<ChatMessage> filtered = filter.filterFeed(feed);

        assertTrue(filtered.isEmpty());
        assertEquals(2, feed.size(), "the feed itself is untouched — the filter is a view");
    }

    @Test
    void onlyTheWorldPageHasAHeaderAction() {
        WorldFeedFilter filter = new WorldFeedFilter();
        assertNull(filter.headerActionFor(null, BUTTON, 0.0F), "no page, no slot");
        assertNull(filter.headerActionFor(priv(), BUTTON, 0.0F),
                "the private header has no filter slot even though the geometry exists");
        assertNotNull(filter.headerActionFor(world(), BUTTON, 0.0F));
    }

    @Test
    void theHeaderActionCarriesTheScreensGeometryAndHover() {
        WorldFeedFilter filter = new WorldFeedFilter();
        ShellHeader.HeaderAction action = filter.headerActionFor(world(), BUTTON, 0.25F);
        assertSame(BUTTON, action.rect(), "the button's rect comes from the screen (header chrome)");
        assertEquals(0.25F, action.hover(), 1e-6F, "and so does its hover wash");
    }

    @Test
    void resetIsByConstructionNotByCall() {
        WorldFeedFilter used = new WorldFeedFilter();
        used.cycle();
        used.cycle();
        assertSame(AppIcons.ICON_TAB_PROFILE_PATH, action(used).icon(), "the used filter is filtering");

        WorldFeedFilter fresh = new WorldFeedFilter();
        assertSame(AppIcons.ICON_FILTER_PATH, action(fresh).icon(),
                "a new screen instance starts at ALL — that is how closing the panel resets the filter");
        assertTrue(fresh.filterFeed(new ArrayList<>()).isEmpty());
    }
}