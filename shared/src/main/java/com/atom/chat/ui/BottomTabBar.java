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
    // One width-derived budget: the capsule stays inside its cell because a
    // tab-width shape only ever travels BUDGET_PX per side, not a fixed ratio.
    private final PressScale[] tabScale = {PressScale.bounce(), PressScale.bounce(), PressScale.bounce()};
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
        // The scaled shape is the cell's capsule, so the bounce budget derives
        // from the capsule width (render draws the same geometry).
        float capsuleW = bar != null && bar.w() > 0.0F
                ? bar.w() / 3.0F - UiTokens.TAB_EDGE_PAD * 2.0F
                : UiTokens.TAB_EDGE_PAD * 2.0F;
        for (int i = 0; i < 3; i++) {
            tabHover[i] = UiMotion.approach(tabHover[i], i == hovered ? 1.0F : 0.0F,
                    deltaMs, UiMotion.HOVER_MS);
            tabScale[i].update(i == hovered, i == pressedTab, deltaMs, Animations.enabled(), capsuleW);
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
        float capsuleH = bar.h() - inset * 2.0F;
        float capsuleW = cellWidth - inset * 2.0F;

        // The indicator IS the selected cell index, animated: it carries the
        // fractional slide between cells, so the pill is placed from it directly
        // and drawn exactly once. Adding a per-cell offset on top of it would
        // push the pill a whole cell right for every tab but the first.
        float indicator = indicatorAnim.getValue();
        float capsuleX = bar.x() + indicator * cellWidth + inset;
        float capsuleCenterX = capsuleX + capsuleW / 2.0F;
        float barCenterY = bar.y() + bar.h() / 2.0F;

        // One bounce drives the whole selected capsule, glyph included - the
        // capsule has no per-cell identity of its own while it slides.
        canvas.save();
        applyBounce(canvas, tabScale[selectedIndex].scale(), capsuleCenterX, barCenterY);
        SkiaDraw.drawRoundedRect(canvas, capsuleX, bar.y() + inset, capsuleW, capsuleH, radius,
                UiTokens.accentFill());
        canvas.restore();

        for (int i = 0; i < 3; i++) {
            float cellCenterX = bar.x() + cellWidth * (i + 0.5F);
            float cellCenterY = bar.y() + bar.h() / 2.0F;
            float scale = tabScale[i].scale();

            canvas.save();
            applyBounce(canvas, scale, cellCenterX, cellCenterY);

            // Hover is the accent-derived wash that fades in/out, never a vertical
            // gradient. The selected cell is skipped: a wash over the accent pill
            // would only dull it.
            if (i != selectedIndex && tabHover[i] > 0.01F) {
                float x = bar.x() + cellWidth * i + inset;
                SkiaDraw.drawRoundedRect(canvas, x, bar.y() + inset, capsuleW, capsuleH, radius,
                        UiTokens.cardHover(tabHover[i]));
            }

            // The selected glyph sits on the accent capsule, so it takes what
            // reads against the accent rather than the panel's text colour.
            int iconColor = i == selectedIndex
                    ? UiTokens.onAccent(UiTokens.accentFill()) : textPrimary;
            drawIconCentered(canvas, ICONS[i], cellCenterX, cellCenterY,
                    UiTokens.TAB_ICON_SIZE, iconColor);

            canvas.restore();
        }
    }

    /**
     * Centred scale around (cx, cy). Emitted only when the scale actually moved,
     * so an idle frame adds no canvas state — and every call is paired by the
     * caller's own {@code save}/{@code restore}.
     */
    private static void applyBounce(Canvas canvas, float scale, float cx, float cy) {
        if (scale != 1.0F) {
            canvas.translate(cx, cy);
            canvas.scale(scale, scale);
            canvas.translate(-cx, -cy);
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
