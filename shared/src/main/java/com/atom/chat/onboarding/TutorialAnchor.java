package com.atom.chat.onboarding;

import com.atom.chat.ui.UiLayout;
import io.github.humbleui.types.RRect;

/** The painted contour travels with its bounds, including the current animation scale. */
public record TutorialAnchor(UiLayout.Rect bounds,RRect contour) {
    public TutorialAnchor(UiLayout.Rect bounds,float radius) {
        this(bounds,RRect.makeXYWH(bounds.x(),bounds.y(),bounds.w(),bounds.h(),
                Math.max(0,Math.min(radius,Math.min(bounds.w(),bounds.h())/2))));
    }
}
