package com.atom.chat.text;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichTextTest {
    @Test
    void flattenPreservesRunStyles() {
        Style click = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/x"));
        Component text = Component.literal("a").setStyle(click).append(Component.literal("b"));
        RichText rich = RichText.of(text);
        assertEquals("ab", rich.getString());
        assertEquals(1, rich.runs().size());
        assertEquals(click, rich.runs().get(0).style());
    }

    @Test
    void sliceKeepsStyles() {
        Component text = Component.literal("abc").setStyle(Style.EMPTY.withColor(0xFF0000))
                .append(Component.literal("def").setStyle(Style.EMPTY.withUnderlined(true)));
        RichText sliced = RichText.of(text).slice(2, 5);
        assertEquals("cde", sliced.getString());
        assertEquals(0xFF0000, sliced.runs().get(0).style().getColor().getValue());
    }

    @Test
    void linkifyBareUrls() {
        RichText rich = RichText.literal("see https://example.com/x now");
        RichText linked = rich.linkifyUrls();
        assertTrue(linked.runs().stream().anyMatch(r -> r.style().getClickEvent() != null
                && r.style().getClickEvent().getAction() == ClickEvent.Action.OPEN_URL));
    }

    @Test
    void sliceNeverSplitsSurrogatePairs() {
        RichText rich = RichText.literal("a\uD83D\uDE00b");
        assertEquals("a\uD83D\uDE00b", rich.getString());
        assertEquals("\uD83D\uDE00", rich.slice(1, 3).getString());
        assertEquals("\uD83D\uDE00", rich.slice(1, 2).getString());
        assertEquals("\uD83D\uDE00", rich.slice(2, 3).getString());
    }

    @Test
    void linkifyPreservesFullTextAndMultipleUrls() {
        RichText linked = RichText.literal("go https://a.test and https://b.test end").linkifyUrls();
        assertEquals("go https://a.test and https://b.test end", linked.getString());
        assertEquals(5, linked.runs().size());
        assertEquals(2, linked.runs().stream()
                .filter(r -> r.style().getClickEvent() != null
                        && r.style().getClickEvent().getAction() == ClickEvent.Action.OPEN_URL)
                .count());
    }

    @Test
    void linkifyLeavesExistingClickRunsUntouched() {
        Style click = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://already.example"));
        RichText rich = RichText.of(Component.literal("https://example.com").setStyle(click));
        RichText linked = rich.linkifyUrls();
        assertEquals(1, linked.runs().size());
        assertEquals(click, linked.runs().get(0).style());
    }

    @Test
    void linkifyPreservesOriginalRunStyle() {
        Style colored = Style.EMPTY.withColor(0x00FF00);
        RichText rich = RichText.of(Component.literal("see https://example.com/x").setStyle(colored));
        RichText linked = rich.linkifyUrls();
        RichText.RichRun link = linked.runs().stream()
                .filter(r -> r.style().getClickEvent() != null)
                .findFirst().orElseThrow();
        assertEquals(0x00FF00, link.style().getColor().getValue());
        assertEquals(colored, link.style().withClickEvent(null));
    }

    @Test
    void ofParsesLegacyFormattingCodesAndStripsControlPairs() {
        RichText rich = RichText.of(Component.literal("§aHello §rWorld"));
        assertEquals("Hello World", rich.getString());
        assertEquals(2, rich.runs().size());
        assertEquals("Hello ", rich.runs().get(0).text());
        assertEquals(0x55FF55, rich.runs().get(0).style().getColor().getValue());
        assertEquals("World", rich.runs().get(1).text());
        assertEquals(null, rich.runs().get(1).style().getColor());
    }

    @Test
    void ofGroupsContiguousCharactersByEffectiveStyle() {
        RichText rich = RichText.of(Component.literal("§aA§cB"));
        assertEquals("AB", rich.getString());
        assertEquals(2, rich.runs().size());
        assertEquals(0x55FF55, rich.runs().get(0).style().getColor().getValue());
        assertEquals(0xFF5555, rich.runs().get(1).style().getColor().getValue());
    }

    @Test
    void emptyAndEmptySlicesReportEmpty() {
        assertTrue(RichText.empty().isEmpty());
        assertTrue(RichText.literal("").isEmpty());
        assertTrue(RichText.literal("abc").slice(2, 2).isEmpty());
    }

    @Test
    void slicePreservesRootStyle() {
        Style root = Style.EMPTY.withColor(0x123456);
        RichText emptyText = RichText.of(Component.literal("").setStyle(root));
        assertEquals(root, emptyText.slice(0, 0).rootStyle());
    }

    @Test
    void spliceReplacesRangeAndKeepsStyles() {
        RichText rich = RichText.of(Component.literal("abc").setStyle(Style.EMPTY.withColor(0xFF0000))
                .append(Component.literal("def").setStyle(Style.EMPTY.withColor(0x00FF00))));
        RichText replaced = rich.splice(2, 5,
                RichText.of(Component.literal("XY").setStyle(Style.EMPTY.withColor(0x0000FF))));
        assertEquals("abXYf", replaced.getString());
        assertEquals(0xFF0000, replaced.runs().get(0).style().getColor().getValue());
        assertEquals(0x0000FF, replaced.slice(2, 4).runs().get(0).style().getColor().getValue());
    }

    @Test
    void spliceClampsAndKeepsRootStyle() {
        Style root = Style.EMPTY.withColor(0x123456);
        RichText rich = RichText.of(Component.literal("abc").setStyle(root));
        assertEquals("abc", rich.splice(1, 1, RichText.empty()).getString());
        assertEquals("bc", rich.splice(-5, 1, RichText.empty()).getString());
        assertEquals(root, rich.splice(0, 1, RichText.literal("z")).rootStyle());
    }

    @Test
    void linkifyStaysOffAnImageCodeTail() {
        // A code the panel cannot render used to be drawn as a text bubble, and
        // the URL pattern swallowed ",name=…,w=…,h=…]]" into the click value:
        // clicking it threw URISyntaxException.
        String code = "[[CICode,url=https://h.uguu.se/a.png,name=a.png,w=128,h=128]]";
        RichText linked = RichText.literal(code).linkifyUrls();
        assertEquals(code, linked.getString());
        assertEquals(1, linked.runs().size(), "the code must stay a single unlinked run");
        assertTrue(linked.runs().stream().allMatch(r -> r.style().getClickEvent() == null));
    }

    @Test
    void linkifyStillLinksTextAroundACode() {
        String text = "look [[CICode,url=https://a.test/a.png,name=a]] at https://b.test now";
        RichText linked = RichText.literal(text).linkifyUrls();
        assertEquals(text, linked.getString());
        assertEquals("https://b.test", linked.runs().stream()
                .filter(r -> r.style().getClickEvent() != null)
                .findFirst().orElseThrow().text());
    }

    @Test
    void stripInteractionsDropsLinkAndUnderlineButKeepsColour() {
        // The private-chat sender goes through stripInteractions so a vanilla
        // /msg click never turns the name into a misleading link. The explicit
        // colour is part of the wire's styling and must survive that strip.
        Style link = Style.EMPTY.withColor(0x55FF55).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg Steve "));
        RichText stripped = RichText.of(Component.literal("Steve").setStyle(link)).stripInteractions();
        assertEquals("Steve", stripped.getString());
        assertEquals(0x55FF55, stripped.runs().get(0).style().getColor().getValue());
        assertEquals(null, stripped.runs().get(0).style().getClickEvent());
        assertFalse(stripped.runs().get(0).style().isUnderlined());
    }

    @Test
    void stripInteractionsKeepsColourlessRunsColourless() {
        RichText stripped = RichText.literal("Steve").stripInteractions();
        assertFalse(stripped.hasColor());
    }
}
