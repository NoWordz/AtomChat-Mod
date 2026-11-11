package com.atom.chat.nav;

/** Standalone deterministic test; does not load Minecraft or native Skia. */
public final class InterruptiblePageMotionProbe {
    public static void main(String[] args) {
        InterruptiblePageMotion m = new InterruptiblePageMotion();
        m.retarget(1000, false, false, true);
        check(m.sample(1000).alpha() == 0, "new page starts transparent over real departing content");
        var before = m.sample(1070);
        m.retarget(1070, true, false, true);
        var after = m.sample(1070);
        near(before.dx(), after.dx(), "interruption preserves current geometry");
        near(0, after.alpha(), "frozen composition owns interrupted pixels");
        var frame = m.sample(1120);
        float x = 153, y = 97, cx = 200, cy = 150;
        near(x, frame.inverseX(frame.forwardX(x, cx), cx), "inverse x follows draw");
        near(y, frame.inverseY(frame.forwardY(y, cy), cy), "inverse y follows draw");
        check(m.sample(1400).settled(), "retarget converges");
        m.retarget(1500, true, false, true);
        check(m.sample(1500).dx() < 0, "back enters from opposite side");
        m.retarget(1550, false, true, false);
        check(m.sample(1550).settled(), "disabled snaps on same frame");
        for (int i = 0; i < 10000; i++) m.retarget(1600 + i, i % 2 == 0, i % 3 == 0, true);
        check(m.sample(12000).settled(), "rapid retarget has no queue");
        System.out.println("PASS InterruptiblePageMotionProbe: continuity, direction, inverse hit, disabled, 10000 retargets");
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void near(float a, float b, String message) {
        check(Math.abs(a - b) < .001f, message + ": " + a + " != " + b);
    }
}
