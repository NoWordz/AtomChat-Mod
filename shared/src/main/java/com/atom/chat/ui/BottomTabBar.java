package com.atom.chat.ui;

import com.atom.chat.render.Animator;
import com.atom.chat.render.Easing;
import com.atom.chat.render.SkiaDraw;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Rect;

/**
 * Shared icon-only bottom tab bar for AtomChat root pages. Rendering and
 * hit-testing are both driven by {@link UiLayout.Rect} plus {@link UiTokens}
 * geometry so the shell never hardcodes a cell position.
 *
 * <p>The bar owns the hover fade state and the sliding selected capsule's
 * {@link Animator}. The screen shares the same animator for the root content
 * push so the capsule and the page body always move together.</p>
 */
public final class BottomTabBar {
    private static final Path[] ICONS = {
            AppIcons.ICON_TAB_CHAT_PATH,
            AppIcons.ICON_TAB_PROFILE_PATH,
            AppIcons.ICON_TAB_SETTINGS_PATH
    };

    private final Animator indicatorAnim = new Animator(Easing::easeInOutCubic);
    private final float[] tabHover = new float[3];
    /** Per-tab press/hover bounce; hit-testing stays unscaled, this is draw-only. */
    private final PressScale[] tabScale = {PressScale.control(), PressScale.control(), PressScale.control()};
    /** Tab index under an active press, for the bounce; -1 = none. */
    private int pressedTab = -1;

    public BottomTabBar() {
        indicatorAnim.setValue(0.0F);
    }

    /**
     * Arms the press bounce for a tab (mouse-down) or clears it (-1 on
     * release). Geometry comes from {@link #hitTest} on the caller side.
     */
    public void setPressedTab(int index) {
        pressedTab = index;
    }

    /** The animator used for the selected capsule; shared with the root content transition. */
    public Animator indicatorAnimator() {
        return indicatorAnim;
    }

    public void setSelectedIndex(int index) {
        if (index < 0 || index >= 3) {
            return;
        }
        indicatorAnim.animateTo(UiMotion.TAB_MS, index);
    }

    public void setSelectedImmediate(int index) {
        if (index < 0 || index >= 3) {
            return;
        }
        indicatorAnim.setValue(index);
    }

    public float indicatorValue() {
        return indicatorAnim.getValue();
    }

    /** Advances hover fades and the capsule slide; call once per root frame. */
    public void update(float deltaMs, float vmx, float vmy, UiLayout.Rect bar) {
        indicatorAnim.update(deltaMs);
        int hovered = hitTest(vmx, vmy, bar);
        for (int i = 0; i < 3; i++) {
            tabHover[i] = UiMotion.approach(tabHover[i], i == hovered ? 1.0F : 0.0F,
                    deltaMs, UiMotion.HOVER_MS);
            tabScale[i].update(i == hovered, i == pressedTab, deltaMs, Animations.enabled());
        }
    }

    public void render(Canvas canvas, UiLayout.Rect bar, int selectedIndex,
                       int textPrimary, int accent) {
        if (bar == null || bar.w() <= 0.0F || bar.h() <= 0.0F) {
            return;
        }
        SkiaDraw.drawRoundedShadow(canvas, bar.x(), bar.y(), bar.w(), bar.h(),
                UiTokens.radius(18), UiTokens.s(8), UiTokens.CHROME_SHADOW);
        SkiaDraw.drawRoundedRect(canvas, bar.x(), bar.y(), bar.w(), bar.h(),
                UiTokens.radius(18), UiTokens.cardFill());

        float cellWidth = bar.w() / 3.0F;
        // The selected/hover capsule is inset from the bar by TAB_EDGE_PAD on
        // all four sides; its height is bar height minus the two vertical edge
        // pads. With no label, the icon sits on the pill's vertical centre.
        float inset = UiTokens.TAB_EDGE_PAD;
        float radius = UiTokens.radius(8);
        float capsuleY = bar.y() + inset;
        float capsuleH = bar.h() - inset * 2.0F;
        float capsuleW = cellWidth - inset * 2.0F;

        // Selected capsule is the accent at full strength, pure colour, no
        // gradient. Translucent white used to sit here and disappeared on the
        // light themes, where the surface under it is already white.
        float capsuleX = bar.x() + indicatorAnim.getValue() * cellWidth + inset;
        SkiaDraw.drawRoundedRect(canvas, capsuleX, capsuleY, capsuleW, capsuleH, radius,
                UiTokens.accentFill());

        // Hover is the accent-derived wash that fades in/out, never a vertical
        // gradient. The selected cell is skipped: a wash over a solid accent
        // would only dull it.
        for (int i = 0; i < 3; i++) {
            if (i == selectedIndex) {
                continue;
            }
            float hov = tabHover[i];
            if (hov <= 0.01F) {
                continue;
            }
            float x = bar.x() + cellWidth * i + inset;
            SkiaDraw.drawRoundedRect(canvas, x, capsuleY, capsuleW, capsuleH, radius,
                    UiTokens.cardHover(hov));
        }

        for (int i = 0; i < 3; i++) {
            float cellCenterX = bar.x() + cellWidth * (i + 0.5F);
            float iconCenterY = bar.y() + bar.h() / 2.0F;
            // The selected glyph sits on the accent capsule, so it takes what
            // reads against the accent rather than the panel's text colour.
            int iconColor = i == selectedIndex
                    ? UiTokens.onAccent(UiTokens.accentFill()) : textPrimary;
            // Icon-only bounce around the glyph centre; the pill wash stays put.
            tabScale[i].begin(canvas, cellCenterX, iconCenterY);
            try {
                drawIconCentered(canvas, ICONS[i], cellCenterX, iconCenterY,
                        UiTokens.TAB_ICON_SIZE, iconColor);
            } finally {
                canvas.restore();
            }
        }
    }

    /** Returns 0..2 for the three equal cells, or -1 when outside the bar. */
    public static int hitTest(float x, float y, UiLayout.Rect bar) {
        if (bar == null || bar.w() <= 0.0F || bar.h() <= 0.0F
                || x < bar.x() || x > bar.right() || y < bar.y() || y > bar.bottom()) {
            return -1;
        }
        int index = (int) ((x - bar.x()) / (bar.w() / 3.0F));
        return Math.max(0, Math.min(2, index));
    }

    private static void drawIconCentered(Canvas canvas, Path icon, float cx, float cy,
                                         float size, int color) {
        Rect b = icon.getBounds();
        if (b == null || b.isEmpty()) {
            return;
        }
        float scale = size / Math.max(b.getWidth(), b.getHeight());
        canvas.save();
        try {
            canvas.translate(cx - (b.getLeft() + b.getRight()) / 2.0F * scale,
                    cy - (b.getTop() + b.getBottom()) / 2.0F * scale);
            canvas.scale(scale, scale);
            try (Paint paint = new Paint().setColor(color).setAntiAlias(true)
                    .setMode(PaintMode.STROKE)
                    .setStrokeWidth(UiTokens.iconStroke(size) / scale)
                    .setStrokeCap(PaintStrokeCap.ROUND)
                    .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally {
            canvas.restore();
        }
    }
}
