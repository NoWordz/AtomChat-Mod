package com.atom.chat.ui;


import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import java.util.ArrayList;
import java.util.List;

/** Per-screen feedback. Existing controls publish their painted geometry during the frame. */
public final class ClickRipples {
    private static final ThreadLocal<ClickRipples> DRAWING = new ThreadLocal<>();
    private record Region(UiLayout.Rect bounds, float radius, int color) { }
    private record Active(Region region, Ripple ripple) { }
    private static final int MAX_ACTIVE = 12;
    private final List<Region> regions = new ArrayList<>();
    private final List<Active> active = new ArrayList<>();
    private Object owner;

    public void begin(Object owner) {
        if (this.owner != owner) active.clear();
        this.owner = owner;
        regions.clear();
        DRAWING.set(this);
    }
    public static void region(float x, float y, float w, float h, float radius, int surface) {
        ClickRipples drawing = DRAWING.get();
        if (drawing == null || w <= 0 || h <= 0 || drawing.regions.size() >= 512) return;
        drawing.regions.add(new Region(new UiLayout.Rect(x, y, w, h), radius, UiTokens.onAccent(surface)));
    }
    public static void block(UiLayout.Rect bounds) {
        ClickRipples drawing = DRAWING.get();
        if (drawing != null) drawing.regions.add(new Region(bounds, 0, 0));
    }
    public void click(float x, float y) {
        if (!Animations.enabled()) { active.clear(); return; }
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region region = regions.get(i);
            if (!region.bounds().contains(x, y)) continue;
            if (region.color() == 0) return;
            if (active.size() == MAX_ACTIVE) active.remove(0);
            Ripple ripple = new Ripple(); ripple.start(x, y, true);
            active.add(new Active(region, ripple));
            return;
        }
    }
    public void end(Canvas canvas, float dt) {
        try {
            active.removeIf(wave -> !regions.contains(wave.region()));
            for (Active wave : active) {
                wave.ripple().update(dt, Animations.enabled());
                wave.ripple().draw(canvas, wave.region().bounds(), wave.region().radius(), wave.region().color());
            }
            active.removeIf(wave -> wave.ripple().size() == 0);
        } finally { DRAWING.remove(); }
    }
    public void clear() { active.clear(); regions.clear(); DRAWING.remove(); }
}
