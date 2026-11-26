package com.atom.chat.api.card;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CardWireRemoteActionsTest {
    private static final ShareCard.CardAction LOCAL = new ShareCard.CardAction(
            "example:local", "Open local page", ShareCard.ActionKind.CLIENT_CALLBACK, "example:local", true);

    @Test
    void remoteCardsAndStateCannotPublishALocalCallback() {
        var card = ShareCard.general("Mixed actions").cardId("example:wire").bodyAction(LOCAL).addAction(LOCAL)
                .addAction("copy", "Copy", ShareCard.ActionKind.COPY_TEXT, "hello", false).build();
        var bytes = CardWireCodec.encodeCard(card);
        assertEquals(LOCAL, CardWireCodec.decodeCard(bytes).bodyAction(), "Local codec consumers keep their callback");
        var received = CardWireCodec.decodeRemoteCard(bytes);
        assertNull(received.bodyAction());
        assertEquals(1, received.actions().size());
        assertEquals("hello", received.actions().get(0).value());

        var state = new CardState.Builder(card.cardId(), false)
                .statusText("Remote status").overrideBodyAction(LOCAL).build();
        var remote = CardWireCodec.decodeRemoteState(CardWireCodec.encodeState(state));
        assertNotNull(remote);
        assertNull(remote.overrideBodyAction());
        assertEquals("Remote status", remote.statusText());
    }
}
