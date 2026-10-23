package com.atom.chat.notification;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationHostFadeTest {
    @Test void sameHostNeverRestartsAFadeAndOnlyTheNewHostFadesIn() {
        var f = new NotificationHostFade();
        assertEquals(1, f.alpha(NotificationHostFade.Host.HUD, 1000, true));
        assertEquals(0, f.alpha(NotificationHostFade.Host.PANEL, 2000, true));
        float midway = f.alpha(NotificationHostFade.Host.PANEL, 2060, true);
        assertTrue(midway > 0 && midway < 1);
        assertEquals(1, f.alpha(NotificationHostFade.Host.PANEL, 2120, true));
        assertEquals(1, f.alpha(NotificationHostFade.Host.PANEL, 5000, true));
        assertEquals(0, f.alpha(NotificationHostFade.Host.HUD, 5000, true));
    }
    @Test void disabledMotionAndSessionResetAreImmediate() {
        var f = new NotificationHostFade();
        f.alpha(NotificationHostFade.Host.HUD, 0, true);
        assertEquals(1, f.alpha(NotificationHostFade.Host.PANEL, 100, false));
        assertEquals(1, f.alpha(NotificationHostFade.Host.PANEL, 101, true));
        f.reset();
        assertEquals(1, f.alpha(NotificationHostFade.Host.HUD, 102, true));
    }
}
