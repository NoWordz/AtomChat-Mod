package com.atom.chat.chat;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import com.atom.chat.platform.Platform;
import com.atom.chat.text.RichText;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.ShellHeader;
import com.atom.chat.ui.UiLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
 * filter applies the active mode to the feed, at the right time and without
 * touching the caller's list. Messages come from {@link LocalEcho#build}, so
 * this file never names a Minecraft type.
 */
class WorldFeedFilterTest {
    private static final UUID OWN = UUID.nameUUIDFromBytes("owner".getBytes(StandardCharsets.UTF_8));
    private static final UiLayout.Rect BUTTON = new UiLayout.Rect(10.0F, 10.0F, 20.0F, 20.0F);

    @TempDir
    Path platformRoot;

    private Field platformProvider;
    private Field configInstance;
    private Object previousProvider;
    private Object previousConfig;

    /** Each test owns its config file and restores both process-wide singletons. */
    @BeforeEach
    void installTemporaryPlatform() throws ReflectiveOperationException {
        platformProvider = Platform.class.getDeclaredField("provider");
        platformProvider.setAccessible(true);
        configInstance = AtomChatConfig.class.getDeclaredField("instance");
        configInstance.setAccessible(true);
        previousProvider = platformProvider.get(null);
        previousConfig = configInstance.get(null);
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
        configInstance.set(null, null);
        AtomChatConfig.reload();
    }

    @AfterEach
    void restoreGlobalState() throws IllegalAccessException {
        configInstance.set(null, previousConfig);
        platformProvider.set(null, previousProvider);
    }

    private Path configFile() {
        return platformRoot.resolve("config/atomchat/atomchat-client.json");
    }

    private void reloadWithMode(String jsonValue) throws IOException {
        Files.writeString(configFile(), "{\"worldFeedFilter\":" + jsonValue
                + ",\"panelWidth\":612,\"closeAfterSend\":true}");
        AtomChatConfig.reload();
    }

    private void assertSavedMode(String expected) throws IOException {
        var json = com.google.gson.JsonParser.parseString(Files.readString(configFile())).getAsJsonObject();
        assertTrue(json.has("worldFeedFilter"), "cycling must persist the mode immediately");
        assertEquals(expected, json.get("worldFeedFilter").getAsString());
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
    void selectedModeSurvivesNewFilterInstances() {
        WorldFeedFilter used = new WorldFeedFilter();
        used.cycle();
        used.cycle();
        assertSame(AppIcons.ICON_TAB_PROFILE_PATH, action(used).icon(), "the used filter is filtering");

        WorldFeedFilter fresh = new WorldFeedFilter();
        assertSame(AppIcons.ICON_TAB_PROFILE_PATH, action(fresh).icon(),
                "reopening the panel keeps the selected players mode");
        ChatMessage player = line("still visible");
        assertTrue(fresh.accepts(player));
        assertEquals(List.of(player), fresh.filterFeed(List.of(player)));
        fresh.cycle();
        assertSame(AppIcons.ICON_FILTER_PATH, action(used).icon(),
                "existing filters also see the current preference");
    }

    @Test
    void everyCycleIsSavedBeforeConfigReload() throws IOException {
        WorldFeedFilter filter = new WorldFeedFilter();
        ChatMessage player = line("player");
        List<ChatMessage> feed = List.of(player);
        for (String mode : List.of("SYSTEM", "PLAYERS", "ALL")) {
            filter.cycle();
            assertSavedMode(mode);
            AtomChatConfig previous = AtomChatConfig.get();
            AtomChatConfig.reload();
            assertNotSame(previous, AtomChatConfig.get(), "exercise a real disk reload");
            WorldFeedFilter reopened = new WorldFeedFilter();
            boolean visible = !mode.equals("SYSTEM");
            assertEquals(visible, filter.accepts(player), "live instance after reload: " + mode);
            assertEquals(visible, reopened.accepts(player), "new instance after reload: " + mode);
            assertEquals(visible ? feed : List.of(), reopened.filterFeed(feed));
            var expectedIcon = switch (mode) {
                case "SYSTEM" -> AppIcons.ICON_ANNOUNCE_PATH;
                case "PLAYERS" -> AppIcons.ICON_TAB_PROFILE_PATH;
                default -> AppIcons.ICON_FILTER_PATH;
            };
            assertSame(expectedIcon, action(reopened).icon(), "header after reload: " + mode);
            assertSame(action(filter).icon(), action(reopened).icon());
        }
    }

    @Test
    void leavingAndReturningToWorldPageKeepsSelection() {
        WorldFeedFilter filter = new WorldFeedFilter();
        filter.cycle();
        assertNull(filter.headerActionFor(priv(), BUTTON, 0));
        assertNull(filter.headerActionFor(NavPage.of(AppPage.CHAT_LIST), BUTTON, 0));
        assertSame(AppIcons.ICON_ANNOUNCE_PATH, action(filter).icon());
        assertFalse(filter.accepts(line("hidden after returning")));
        assertTrue(filter.filterFeed(List.of(line("hidden"))).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"SYSTEM\"", "\" system \"", "\"SyStEm\""})
    void reloadedModeUpdatesExistingFilterAndNormalizesEnumName(String value) throws IOException {
        WorldFeedFilter filter = new WorldFeedFilter();
        reloadWithMode(value);
        assertSame(AppIcons.ICON_ANNOUNCE_PATH, action(filter).icon());
        assertEquals(AtomChatConfig.get().accentColor, action(filter).color());
        assertFalse(filter.accepts(line("player")));
        assertTrue(filter.filterFeed(List.of(line("player"))).isEmpty());
        assertSavedMode("SYSTEM");
        filter.cycle();
        assertSame(AppIcons.ICON_TAB_PROFILE_PATH, action(filter).icon());
        assertSavedMode("PLAYERS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\" \"", "\"unknown\"", "{}", "[]", "[\"SYSTEM\"]", "42", "false"})
    void invalidOrNullModeFallsBackWithoutDiscardingOtherSettings(String value) throws IOException {
        reloadWithMode(value);
        WorldFeedFilter filter = new WorldFeedFilter();
        List<ChatMessage> feed = List.of(line("visible"));
        assertSame(AppIcons.ICON_FILTER_PATH, action(filter).icon());
        assertSame(feed, filter.filterFeed(feed));
        assertTrue(filter.accepts(feed.get(0)));
        assertEquals(612F, AtomChatConfig.get().panelWidth);
        assertTrue(AtomChatConfig.get().closeAfterSend);
        assertSavedMode("ALL");
        filter.cycle();
        assertSame(AppIcons.ICON_ANNOUNCE_PATH, action(filter).icon());
        assertSavedMode("SYSTEM");
    }

    @Test
    void olderConfigWithoutModeDefaultsToAllAndKeepsOtherSettings() throws IOException {
        Files.writeString(configFile(), "{\"panelWidth\":612}");
        AtomChatConfig.reload();
        WorldFeedFilter filter = new WorldFeedFilter();
        assertSame(AppIcons.ICON_FILTER_PATH, action(filter).icon());
        assertEquals(612F, AtomChatConfig.get().panelWidth);
        assertSavedMode("ALL");
    }

    @Test
    void nullInMemoryModeIsSafeForRenderingFilteringAndCycling() throws IOException, IllegalAccessException {
        AtomChatConfig config = new com.google.gson.Gson().fromJson(
                "{\"worldFeedFilter\":null}", AtomChatConfig.class);
        // Install the deserialized object without load() repairing it first.
        configInstance.set(null, config);
        WorldFeedFilter filter = new WorldFeedFilter();
        List<ChatMessage> feed = List.of(line("visible"));
        assertSame(AppIcons.ICON_FILTER_PATH, action(filter).icon());
        assertSame(feed, filter.filterFeed(feed));
        assertTrue(filter.accepts(feed.get(0)));
        filter.cycle();
        assertSame(AppIcons.ICON_ANNOUNCE_PATH, action(filter).icon());
        assertSavedMode("SYSTEM");
    }
}
