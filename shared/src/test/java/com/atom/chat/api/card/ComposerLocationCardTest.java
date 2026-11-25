package com.atom.chat.api.card;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class ComposerLocationCardTest {
    @Test void theSameWireCardUsesTheRecipientsLanguageWithoutRenamingTheWireWaypoint() {
        var canonical = ComposerLocationCard.create("Alex", 7, 64, -12, "minecraft:the_nether");
        var card = CardWireCodec.decodeCard(CardWireCodec.encodeCard(canonical));
        assertNotNull(card);

        var english = ComposerLocationCard.localize(card, language(Map.of(
                "title", "%s's location", "source", "Location share", "tag", "[Location]",
                "waypoint", "+ Waypoint", "teleport", "Teleport", "copy", "Copy", "nether", "Nether")));
        var chinese = ComposerLocationCard.localize(card, language(Map.of(
                "title", "%s 的位置", "source", "位置分享", "tag", "[坐标]",
                "waypoint", "+ 路径点", "teleport", "传送", "copy", "复制", "nether", "下界")));
        assertEquals("Alex's location", english.title());
        assertEquals("Alex 的位置", chinese.title());
        assertEquals("Location share", english.location().mapSource());
        assertEquals("位置分享", chinese.sourceLabel());
        assertEquals(java.util.List.of("+ Waypoint", "Teleport", "Copy"),
                english.actions().stream().map(ShareCard.CardAction::label).toList());
        assertEquals(java.util.List.of("+ 路径点", "传送", "复制"),
                chinese.actions().stream().map(ShareCard.CardAction::label).toList());
        assertEquals("7 64 -12", english.actions().get(2).value());
        assertEquals("[Location] Alex's location (7, 64, -12, Nether)", english.fallbackPlainText());
        assertEquals("[坐标] Alex 的位置 (7, 64, -12, 下界)", chinese.fallbackPlainText());
        assertEquals(ComposerLocationCard.nativeName(card), ComposerLocationCard.nativeName(chinese));
        assertEquals(ComposerLocationCard.nativeName(card), ComposerLocationCard.nativeName(english));
    }

    @Test void plainComposerWaypointsCanBeLocalizedButNamedThirdPartyWaypointsArePreserved() {
        String name = ComposerLocationCard.nativeName(
                ComposerLocationCard.create("Alex", 7, 64, -12, "minecraft:overworld"));
        var plain = BuiltInCardParsers.parseBracketWaypoint(
                "[name:\"" + name + "\", x:7, y:64, z:-12, dim:minecraft:overworld]", "", "JourneyMap");
        var english = language(Map.of("title", "%s's location", "source", "Location share", "overworld", "Overworld"));
        var localized = ComposerLocationCard.localize(ComposerLocationCard.fromWaypoint(plain, "Alex"), english);
        assertEquals("Alex's location", localized.title());
        assertEquals("Sam's location", ComposerLocationCard.localize(
                ComposerLocationCard.fromWaypoint(plain, "Sam"), english).title());
        assertEquals("Location share", ComposerLocationCard.localize(
                ComposerLocationCard.fromWaypoint(plain, null), english).title());
        var waypoint = BuiltInCardParsers.parseBracketWaypoint("[name:\"Home\", x:7, y:64, z:-12]", "", "JourneyMap");
        assertSame(waypoint, ComposerLocationCard.localize(ComposerLocationCard.fromWaypoint(waypoint, "Sam"), english));
    }

    private static BiFunction<String, Object[], String> language(Map<String, String> strings) {
        return (key, args) -> String.format(strings.getOrDefault(
                key.substring(ComposerLocationCard.PREFIX.length()), key), args);
    }

    @Test void aPairedThirdPartyWaypointKeepsItsTitleEvenWithTheComposerSuffix() {
        var card = BuiltInCardParsers.buildLocationCard("Home (AtomChat)", 7, 64, -12, "minecraft:overworld",
                0xFF00FF00, "JourneyMap", "");
        assertSame(card, ComposerLocationCard.fromPairedWaypoint(card, "Alex"));
    }
}
