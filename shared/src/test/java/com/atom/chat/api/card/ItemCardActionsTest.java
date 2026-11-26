package com.atom.chat.api.card;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ItemCardActionsTest {
    @Test
    void exposesRegisteredActionsOnlyForEligibleItemCards() {
        var item = diamondCard();
        AtomicReference<ShareCard> clicked = new AtomicReference<>();
        ItemCardActions.register(new ItemCardActions.Action("example:show", card -> "Show recipes",
                card -> "minecraft:diamond".equals(card.item().itemId()), card -> {
                    clicked.set(card);
                    return true;
                }));

        assertEquals(List.of("Show recipes"), ItemCardActions.visible(item).stream()
                .filter(button -> button.id().equals("example:show"))
                .map(ItemCardActions.Button::label).toList());
        assertTrue(ItemCardActions.visible(ShareCard.general("Ordinary card").build()).isEmpty());
        assertTrue(ItemCardActions.visible(ShareCard.item("Missing item").build()).isEmpty());
        assertFalse(ItemCardActions.activate(item, "example:unknown"));
        assertNull(clicked.get());
        assertTrue(ItemCardActions.activate(item, "example:show"));
        assertSame(item, clicked.get());
    }

    @Test
    void rechecksVisibilityAtClickTimeAndKeepsIdsUnique() {
        var enabled = new java.util.concurrent.atomic.AtomicBoolean(true);
        var action = new ItemCardActions.Action("example:conditional", card -> "Lookup",
                card -> enabled.get(), card -> fail("An unavailable action should not run"));
        ItemCardActions.register(action);
        assertThrows(IllegalArgumentException.class, () -> ItemCardActions.register(action));

        assertTrue(ItemCardActions.visible(diamondCard()).stream()
                .anyMatch(button -> button.id().equals("example:conditional")));
        enabled.set(false);
        assertFalse(ItemCardActions.activate(diamondCard(), "example:conditional"));
    }

    private static ShareCard diamondCard() {
        return ShareCard.item("Diamond").itemPayload(new ShareCard.ItemPayload(
                "minecraft:diamond", "", "Diamond", "", "", 0, 1)).build();
    }
}
