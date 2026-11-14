package com.atom.chat.onboarding;

import com.atom.chat.ui.SpringAnim;
import com.atom.chat.ui.UiLayout;

/** Owns only the annotation contour; input continues to use the live control geometry. */
public final class TutorialFocusMotion {
    private final SpringAnim x = spring(), y = spring(), width = spring(), height = spring();
    private boolean initialized;

    private static SpringAnim spring() { return new SpringAnim(400, .72f, .15f, 2); }

    public TutorialAnchor update(TutorialAnchor target, float dt, boolean enabled) {
        var bounds = target.bounds();
        float[] values = {bounds.x(), bounds.y(), bounds.w(), bounds.h()};
        SpringAnim[] springs = {x, y, width, height};
        for (int i = 0; i < springs.length; i++) {
            if (!initialized || !enabled) springs[i].snapTo(values[i]);
            else { springs[i].setTarget(values[i]); springs[i].update(dt, true); }
        }
        initialized = true;
        var moved = new UiLayout.Rect(x.value(), y.value(), Math.max(1, width.value()), Math.max(1, height.value()));
        float sx = moved.w() / Math.max(1, bounds.w()), sy = moved.h() / Math.max(1, bounds.h());
        var contour = target.contour().offset(-bounds.x(), -bounds.y()).scale(sx, sy).offset(moved.x(), moved.y());
        return new TutorialAnchor(moved, contour);
    }

    public void clear() { initialized = false; }

    /** Intersect the centre-to-card ray with the actual rounded outline, including asymmetric corners. */
    public static float[] connectorStart(TutorialAnchor anchor, float endX, float endY) {
        var b = anchor.bounds();
        float cx = b.x() + b.w() / 2, cy = b.y() + b.h() / 2;
        float dx = endX - cx, dy = endY - cy;
        float low = 0, high = 1;
        if (contains(anchor, endX, endY)) return new float[]{endX, endY};
        for (int i = 0; i < 20; i++) {
            float mid = (low + high) / 2;
            if (contains(anchor, cx + dx * mid, cy + dy * mid)) low = mid;
            else high = mid;
        }
        return new float[]{cx + dx * low, cy + dy * low};
    }

    private static boolean contains(TutorialAnchor anchor, float x, float y) {
        var b = anchor.bounds();
        if (!b.contains(x, y)) return false;
        float[] radii = anchor.contour()._radii;
        if (radii.length == 0) return true;
        boolean right = x > b.x() + b.w() / 2, bottom = y > b.y() + b.h() / 2;
        int corner = bottom ? (right ? 2 : 3) : (right ? 1 : 0);
        float rx = radii.length == 1 ? radii[0] : radii.length == 2 ? radii[0]
                : radii.length == 4 ? radii[corner] : radii[corner * 2];
        float ry = radii.length == 1 ? radii[0] : radii.length == 2 ? radii[1]
                : radii.length == 4 ? radii[corner] : radii[corner * 2 + 1];
        rx = Math.min(rx, b.w() / 2); ry = Math.min(ry, b.h() / 2);
        if (rx <= 0 || ry <= 0) return true;
        float cx = right ? b.right() - rx : b.x() + rx;
        float cy = bottom ? b.bottom() - ry : b.y() + ry;
        if ((right ? x <= cx : x >= cx) || (bottom ? y <= cy : y >= cy)) return true;
        float dx = (x - cx) / rx, dy = (y - cy) / ry;
        return dx * dx + dy * dy <= 1;
    }
}
