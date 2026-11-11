package com.atom.chat.ui;

public final class BottomChromeMotionProbe {
    public static void main(String[] args) {
        BottomChromeMotion motion = new BottomChromeMotion();
        var input = new BottomChromeMotion.Shape(10, 200, 280, 90, 1);
        var tabs = new BottomChromeMotion.Shape(10, 230, 280, 60, 1);
        var hidden = new BottomChromeMotion.Shape(150, 260, 0, 60, 0);
        motion.target(input, 0, true);
        motion.target(tabs, 10, true);
        check(motion.sample(10).equals(input), "composer starts at current bounds");
        var middle = motion.sample(95);
        motion.target(hidden, 95, true);
        check(motion.sample(95).equals(middle), "collapse starts continuously");
        var collapse = motion.sample(140);
        motion.target(tabs, 140, true);
        check(motion.sample(140).equals(collapse), "reverse is symmetric and continuous");
        check(motion.sample(140 + BottomChromeMotion.COLLAPSE_MS).equals(tabs), "reverse lands exactly at its declared duration");
        motion.target(hidden, 410, false);
        check(motion.sample(410).equals(hidden), "disabled hides immediately");
        System.out.println("PASS BottomChromeMotionProbe: morph, collapse, reversal, disabled");
    }
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
