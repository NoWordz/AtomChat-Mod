package com.atom.chat.net;

import com.atom.chat.api.card.ShareCard;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConvertedSharePairingTest {
    private final ConvertedSharePairing pairing = new ConvertedSharePairing();
    private final UUID packet = UUID.randomUUID();
    private final Object component = new Object();
    private final ConvertedSharePairing.Share share = new ConvertedSharePairing.Share(
            UUID.randomUUID(), "Steve", "[装备] Sword", ShareCard.item("Sword").build());

    @Test void theActualSystemPacketSuppliesTheCardAndServerAuthorExactlyOnce() {
        pairing.arm(packet, share, 1);
        assertNull(pairing.takeVisible(component));
        pairing.deliver(packet, component, 2, () -> {
            assertNull(pairing.takeVisible(new Object()));
            assertSame(share, pairing.takeVisible(component));
            assertNull(pairing.takeVisible(component));
        });
        pairing.deliver(packet, component, 3, () -> assertNull(pairing.takeVisible(component)));
    }

    @Test void mismatchSuppressionTimeoutAndReconnectNeverAttachToLaterChat() {
        pairing.arm(packet, share, 1);
        pairing.deliver(UUID.randomUUID(), component, 2, () -> assertNull(pairing.takeVisible(component)));
        pairing.deliver(packet, component, 3, () -> assertNull(pairing.takeVisible(component)));

        pairing.arm(packet, share, 4);
        pairing.deliver(packet, component, 5, () -> {});
        assertNull(pairing.takeVisible(component));

        pairing.arm(packet, share, 6);
        pairing.deliver(packet, component, 5_000_000_007L, () -> assertNull(pairing.takeVisible(component)));

        pairing.arm(packet, share, 8);
        pairing.clear();
        pairing.deliver(packet, component, 9, () -> assertNull(pairing.takeVisible(component)));
    }
}
