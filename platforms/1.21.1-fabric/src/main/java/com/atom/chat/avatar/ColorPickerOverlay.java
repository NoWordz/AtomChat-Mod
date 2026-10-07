package com.atom.chat.avatar;

import com.atom.chat.font.FontManager;
import com.atom.chat.settings.SettingsColor;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.PressScale;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.text.Text;

/**
 * Modal HSV colour picker shown over the panel when the user taps the "+"
 * swatch: a saturation×brightness square on top, a hue bar below, a live
 * swatch + hex value, and the same confirm/cancel round buttons as the
 * cropper. Confirm applies the colour to the target {@link SettingsColor}
 * (which persists immediately); Esc/cancel discards.
 */
public final class ColorPickerOverlay {
    private final java.util.function.Consumer<String> clipboard;
    private SettingsColor target;
    private boolean active;
    private float anim;
    private boolean closing;
    private long lastFrameMs = System.currentTimeMillis();
    /** Until when the hex shows "Copied" instead of the value. */
    private long copiedUntil;

    private float hue;
    private float sat;
    private float bri;
    /** 0 = dragging inside the SV square, 1 = dragging the hue bar. */
    private int dragMode = -1;

    private float btnHoverCheck;
    private float btnHoverClose;
    /** One bounce per control: the two drag knobs, then cancel and confirm. */
    private final PressScale svKnobScale = PressScale.bounce();
    private final PressScale hueKnobScale = PressScale.bounce();
    private final PressScale cancelScale = PressScale.bounce();
    private final PressScale confirmScale = PressScale.bounce();
    /** Hit box of the copyable hex value; refreshed while rendering. */
    private float hexHitX;
    private float hexHitY;
    private float hexHitW;
    private float hexHitH;
    /** Hex text input: focused state and the typed digits (max 6). */
    private boolean inputFocused;
    private final StringBuilder inputBuffer = new StringBuilder();

    /** @param clipboard copies the hex value (the shell's clipboard helper). */
    public ColorPickerOverlay(java.util.function.Consumer<String> clipboard) {
        this.clipboard = clipboard;
    }

    private static float s(float v) {
        return UiTokens.s(v);
    }

    public void open(SettingsColor color) {
        this.closing = false;
        this.target = color;
        float[] hsv = ColorUtil.rgbToHsv(color.value());
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.bri = hsv[2];
        this.active = true;
        this.anim = 0.0F;
    }

    public boolean isActive() {
        return active;
    }

    public void cancel() {
        // Symmetric fade-out (0.1.10 audit): keep active so the overlay keeps
        // swallowing input until the fade has fully played.
        closing = true;
    }

    private void confirm() {
        if (closing) {
            return;
        }
        if (target != null) {
            // The picker edits RGB only. Rows whose current colour carries an
            // alpha (e.g. translucent capsule backgrounds) keep that alpha, so a
            // custom colour does not silently turn a translucent surface opaque.
            int alpha = target.value() & 0xFF000000;
            int rgb = ColorUtil.hsvToRgb(hue, sat, bri) & 0x00FFFFFF;
            target.apply(alpha | rgb);
        }
        closing = true;
    }

    // ----------------------------- card geometry -----------------------------

    private static float cardW(UiLayout.Rect panel) {
        return Math.min(panel.w() - s(32), s(300));
    }

    private static float cardH() {
        return s(300);
    }

    private static float cardX(UiLayout.Rect panel) {
        return panel.x() + (panel.w() - cardW(panel)) / 2.0F;
    }

    private static float cardY(UiLayout.Rect panel) {
        return panel.y() + (panel.h() - cardH()) / 2.0F;
    }

    private static float pad() {
        return s(18);
    }

    private static float sqH() {
        return s(150);
    }

    private static Rect svRect(UiLayout.Rect panel) {
        float x = cardX(panel) + pad();
        float y = cardY(panel) + s(52);
        return Rect.makeXYWH(x, y, cardW(panel) - pad() * 2.0F, sqH());
    }

    private static Rect hueRect(UiLayout.Rect panel) {
        Rect sv = svRect(panel);
        return Rect.makeXYWH(sv.getLeft(), sv.getBottom() + s(16), sv.getWidth(), s(14));
    }

    private float btnCy(UiLayout.Rect panel) {
        return cardY(panel) + cardH() - s(34);
    }

    private float confirmCx(UiLayout.Rect panel) {
        return cardX(panel) + cardW(panel) - s(40);
    }

    private float cancelCx(UiLayout.Rect panel) {
        return cardX(panel) + cardW(panel) - s(88);
    }

    private boolean inButton(float vmx, float vmy, float cx, UiLayout.Rect panel) {
        float r = s(18);
        float dx = vmx - cx;
        float dy = vmy - btnCy(panel);
        return dx * dx + dy * dy <= r * r;
    }

    /** SV-square hit box. The bounce is draw-only, so this stays unscaled. */
    private boolean overSv(float vmx, float vmy, UiLayout.Rect panel) {
        Rect sv = svRect(panel);
        return vmx >= sv.getLeft() && vmx <= sv.getRight()
                && vmy >= sv.getTop() && vmy <= sv.getBottom();
    }

    /** Hue-bar hit box, including the grab margin the click already tolerates. */
    private boolean overHue(float vmx, float vmy, UiLayout.Rect panel) {
        Rect bar = hueRect(panel);
        return vmx >= bar.getLeft() && vmx <= bar.getRight()
                && vmy >= bar.getTop() - s(6) && vmy <= bar.getBottom() + s(6);
    }

    // ------------------------------ hex input -------------------------------

    private float inputX(UiLayout.Rect panel) {
        return cardX(panel) + pad();
    }

    private float inputW(UiLayout.Rect panel) {
        // Cancel button radius s(18) plus breathing room — the pill must never
        // reach under the round buttons.
        return cancelCx(panel) - s(30) - inputX(panel);
    }

    private boolean inInput(float vmx, float vmy, UiLayout.Rect panel) {
        return vmx >= inputX(panel) && vmx <= inputX(panel) + inputW(panel)
                && vmy >= btnCy(panel) - s(15) && vmy <= btnCy(panel) + s(15);
    }

    public boolean isInputFocused() {
        return inputFocused;
    }

    /** Focuses the hex input with an empty buffer. */
    public void focusInput() {
        inputFocused = true;
        inputBuffer.setLength(0);
    }

    /**
     * Leaves the input: a 3/6-digit legal buffer is applied (失焦自动应用),
     * anything else is dropped and the live colour stays.
     */
    public void blurInput() {
        applyBufferIfLegal();
        inputFocused = false;
        inputBuffer.setLength(0);
    }

    /**
     * Types one character. Only hex digits are accepted (everything else is
     * ignored — no error state to communicate), capped at 6 digits; the colour
     * applies live at 3 digits (short format) and 6.
     */
    public void onChar(char chr) {
        if (!inputFocused || inputBuffer.length() >= 6) {
            return;
        }
        boolean hexDigit = (chr >= '0' && chr <= '9')
                || (chr >= 'a' && chr <= 'f') || (chr >= 'A' && chr <= 'F');
        if (!hexDigit) {
            return;
        }
        inputBuffer.append(Character.toLowerCase(chr));
        applyBufferIfLegal();
    }

    /** Backspace inside the hex input. */
    public void onBackspace() {
        if (inputFocused && inputBuffer.length() > 0) {
            inputBuffer.deleteCharAt(inputBuffer.length() - 1);
        }
    }

    /**
     * Replaces the buffer with the hex digits of the pasted text: a clipboard
     * entry like "#4A90E2" lands as a complete colour; non-hex characters are
     * stripped and only the first 6 digits are kept.
     */
    public void pasteHex(String text) {
        if (!inputFocused || text == null) {
            return;
        }
        String digits = text.replaceAll("[^0-9a-fA-F]", "");
        if (digits.isEmpty()) {
            return;
        }
        inputBuffer.setLength(0);
        inputBuffer.append(digits.toLowerCase(), 0, Math.min(6, digits.length()));
        applyBufferIfLegal();
    }

    /** The hex text Ctrl+C should copy: the buffer while editing, else the live value. */
    public String copyHex() {
        if (inputFocused && inputBuffer.length() > 0) {
            return inputBuffer.toString();
        }
        return ColorUtil.formatHex(ColorUtil.hsvToRgb(hue, sat, bri));
    }

    private void applyBufferIfLegal() {
        Integer rgb = parseBuffer();
        if (rgb == null) {
            return;
        }
        float[] hsv = ColorUtil.rgbToHsv(rgb);
        hue = hsv[0];
        sat = hsv[1];
        bri = hsv[2];
    }

    private Integer parseBuffer() {
        int len = inputBuffer.length();
        if (len != 3 && len != 6) {
            return null;
        }
        try {
            int v = Integer.parseInt(inputBuffer.toString(), 16);
            if (len == 6) {
                return 0xFF000000 | v;
            }
            // Short #FFF → #FFFFFF: double each nibble.
            int r = (v >> 8) & 0xF;
            int g = (v >> 4) & 0xF;
            int b = v & 0xF;
            return 0xFF000000 | (r * 0x11) << 16 | (g * 0x11) << 8 | (b * 0x11);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // --------------------------------- input ---------------------------------

    /** A click while active. Consumes everything inside the panel. */
    public void onClick(float vmx, float vmy, UiLayout.Rect panel) {
        if (closing) {
            return;
        }
        if (inButton(vmx, vmy, confirmCx(panel), panel)) {
            confirm();
            return;
        }
        if (inButton(vmx, vmy, cancelCx(panel), panel)) {
            cancel();
            return;
        }
        // Copyable hex: click copies the live value and flashes "Copied".
        if (vmx >= hexHitX && vmx <= hexHitX + hexHitW
                && vmy >= hexHitY && vmy <= hexHitY + hexHitH) {
            if (clipboard != null && target != null) {
                clipboard.accept(ColorUtil.formatHex(ColorUtil.hsvToRgb(hue, sat, bri)));
                copiedUntil = System.currentTimeMillis() + 1000L;
            }
            return;
        }
        // The hex input pill: clicking it focuses with an empty buffer; any
        // other click first blurs (auto-applying a legal buffer).
        if (inInput(vmx, vmy, panel)) {
            focusInput();
            return;
        }
        if (inputFocused) {
            blurInput();
        }
        Rect sv = svRect(panel);
        if (overSv(vmx, vmy, panel)) {
            sat = clamp01((vmx - sv.getLeft()) / sv.getWidth());
            bri = clamp01(1.0F - (vmy - sv.getTop()) / sv.getHeight());
            dragMode = 0;
            return;
        }
        Rect hueBar = hueRect(panel);
        if (overHue(vmx, vmy, panel)) {
            hue = clamp01((vmx - hueBar.getLeft()) / hueBar.getWidth());
            dragMode = 1;
        }
    }

    /** Continues an SV-square or hue-bar drag. */
    public void onDrag(float vmx, float vmy, UiLayout.Rect panel) {
        if (closing || dragMode < 0) {
            return;
        }
        Rect sv = svRect(panel);
        if (dragMode == 0) {
            sat = clamp01((vmx - sv.getLeft()) / sv.getWidth());
            bri = clamp01(1.0F - (vmy - sv.getTop()) / sv.getHeight());
        } else {
            hue = clamp01((vmx - sv.getLeft()) / sv.getWidth());
        }
    }

    public void endDrag() {
        dragMode = -1;
    }

    public void onScroll(UiLayout.Rect panel, double verticalAmount) {
        // Consumed so the page underneath never scrolls; wheel does nothing.
    }

    private static float clamp01(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    // -------------------------------- rendering ------------------------------

    public void render(Canvas canvas, UiLayout.Rect panel, float vmx, float vmy, int accent) {
        if (!active) {
            return;
        }
        long now = System.currentTimeMillis();
        float dt = Math.min(50.0F, Math.max(1.0F, now - lastFrameMs));
        lastFrameMs = now;
        anim = UiMotion.approach(anim, closing ? 0.0F : 1.0F, dt, Animations.ms(UiMotion.POPUP_MS));
        if (closing && anim <= 0.02F) {
            active = false;
            closing = false;
            return;
        }
        if (anim < 0.01F) {
            return;
        }
        int alpha = (int) (255.0F * anim);

        canvas.save();
        try {
            SkiaDraw.drawRoundedRect(canvas, panel.x(), panel.y(), panel.w(), panel.h(),
                    s(12), Color.makeARGB((int) (200.0F * anim), 8, 9, 14));

            float cx = cardX(panel);
            float cy = cardY(panel);
            float cw = cardW(panel);
            float ch = cardH();
            SkiaDraw.drawRoundedShadow(canvas, cx, cy, cw, ch, s(14), s(8),
                    // No whole-card alpha layer here: every element
                    // scales its own alpha by the fade, the shadow too.
                    UiTokens.withAlpha(UiTokens.CHROME_SHADOW, 100.0F * anim));
            SkiaDraw.drawRoundedRect(canvas, cx, cy, cw, ch, s(14),
                    Color.makeARGB((int) (0.96F * alpha), 32, 36, 46));

            int live = ColorUtil.hsvToRgb(hue, sat, bri);

            // Title row: name left; live preview dot + copyable hex right
            // (blue-link language, same as the about-page links).
            Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
            if (target != null) {
                SkiaFontRenderer.drawText(canvas, titleFont,
                        Text.translatable(target.titleKey()).getString(),
                        cx + pad(), SkiaFontRenderer.centerBaselineY(titleFont, cy + s(28)),
                        Color.makeARGB(alpha, 255, 255, 255));
            }
            Font valueFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
            boolean copied = System.currentTimeMillis() < copiedUntil;
            String hexText = copied
                    ? Text.translatable("atomchat.settings.color.copied").getString()
                    : ColorUtil.formatHex(live);
            float hexW = SkiaFontRenderer.getStringWidth(valueFont, hexText);
            float hexX = cx + cw - pad() - hexW;
            float textCyF = cy + s(28);
            int hexColor = copied
                    ? Color.makeARGB(alpha, 255, 255, 255)
                    : Color.makeARGB(alpha, 96, 165, 250);
            SkiaFontRenderer.drawText(canvas, valueFont, hexText, hexX,
                    SkiaFontRenderer.centerBaselineY(valueFont, textCyF), hexColor);
            if (!copied) {
                float uy = textCyF + s(5);
                try (Paint line = new Paint().setColor(Color.makeARGB(alpha, 96, 165, 250))
                        .setStrokeWidth(s(1)).setAntiAlias(true)) {
                    canvas.drawLine(hexX, uy, hexX + hexW, uy, line);
                }
            }
            // Live preview dot hugging the hex's left side.
            float dotR = s(6);
            float dotCx = hexX - s(14);
            SkiaDraw.drawRoundedRect(canvas, dotCx - dotR, textCyF - dotR, 2.0F * dotR, 2.0F * dotR,
                    dotR, (alpha << 24) | (live & 0x00FFFFFF));
            hexHitX = hexX - s(6);
            hexHitY = textCyF - s(11);
            hexHitW = hexW + s(12);
            hexHitH = s(22);

            // SV square: base hue, white gradient left→right, black bottom→up.
            Rect sv = svRect(panel);
            canvas.save();
            SkiaDraw.clip(canvas, sv.getLeft(), sv.getTop(), sv.getWidth(), sv.getHeight(), s(8));
            int base = ColorUtil.hsvToRgb(hue, 1.0F, 1.0F);
            try (Paint p = new Paint().setColor(base).setAntiAlias(true)) {
                canvas.drawRect(sv, p);
            }
            try (Paint white = new Paint().setAntiAlias(true);
                 var sh = Shader.makeLinearGradient(sv.getLeft(), 0, sv.getRight(), 0,
                         new int[]{0xFFFFFFFF, 0x00FFFFFF})) {
                white.setShader(sh);
                canvas.drawRect(sv, white);
            }
            try (Paint black = new Paint().setAntiAlias(true);
                 var sh = Shader.makeLinearGradient(0, sv.getTop(), 0, sv.getBottom(),
                         new int[]{0x00000000, 0xFF000000})) {
                black.setShader(sh);
                canvas.drawRect(sv, black);
            }
            // SV cursor.
            float px = sv.getLeft() + sat * sv.getWidth();
            float py = sv.getTop() + (1.0F - bri) * sv.getHeight();
            // Only the knob bounces. The square under it is the drag surface, and
            // scaling that would slide the colour under the pointer mid-gesture.
            svKnobScale.update(overSv(vmx, vmy, panel), dragMode == 0, dt, Animations.enabled(), s(14));
            svKnobScale.begin(canvas, px, py);
            try (Paint cursor = new Paint().setColor(0xFFFFFFFF).setMode(PaintMode.STROKE)
                    .setStrokeWidth(s(2)).setAntiAlias(true)) {
                canvas.drawOval(Rect.makeXYWH(px - s(7), py - s(7), s(14), s(14)), cursor);
            } finally {
                canvas.restore();
            }
            canvas.restore();

            // Hue bar: 7-stop rainbow gradient + cursor.
            Rect hueBar = hueRect(panel);
            canvas.save();
            SkiaDraw.clip(canvas, hueBar.getLeft(), hueBar.getTop(), hueBar.getWidth(), hueBar.getHeight(), s(7));
            try (Paint p = new Paint().setAntiAlias(true);
                 var sh = Shader.makeLinearGradient(hueBar.getLeft(), 0, hueBar.getRight(), 0, rainbow())) {
                p.setShader(sh);
                canvas.drawRect(hueBar, p);
            }
            float hx = hueBar.getLeft() + hue * hueBar.getWidth();
            float hueCy = hueBar.getTop() + hueBar.getHeight() / 2.0F;
            // Same rule as the SV knob: the rainbow track is the drag surface and
            // stays put while the knob breathes.
            hueKnobScale.update(overHue(vmx, vmy, panel), dragMode == 1, dt, Animations.enabled(), s(12));
            hueKnobScale.begin(canvas, hx, hueCy);
            try (Paint cursor = new Paint().setColor(0xFFFFFFFF).setMode(PaintMode.STROKE)
                    .setStrokeWidth(s(2)).setAntiAlias(true)) {
                canvas.drawOval(Rect.makeXYWH(hx - s(6), hueCy - s(6), s(12), s(12)), cursor);
            } finally {
                canvas.restore();
            }
            canvas.restore();

            // Hex input pill on the button row's left segment.
            float ix = inputX(panel);
            float iw = inputW(panel);
            float icy = btnCy(panel);
            float ih = s(30);
            SkiaDraw.drawRoundedRect(canvas, ix, icy - ih / 2.0F, iw, ih, ih / 2.0F,
                    Color.makeARGB((int) (0.5F * alpha), 255, 255, 255));
            if (inputFocused) {
                try (Paint ring = new Paint().setColor((alpha << 24) | (accent & 0x00FFFFFF))
                        .setMode(PaintMode.STROKE).setStrokeWidth(s(1.5F)).setAntiAlias(true)) {
                    canvas.drawRRect(RRect.makeXYWH(ix, icy - ih / 2.0F, iw, ih, ih / 2.0F), ring);
                }
            }
            Font inputFont = FontManager.font(UiTokens.FONT_BUTTON);
            float textX = ix + s(14);
            int textCy = Math.round(icy);
            if (inputFocused) {
                SkiaFontRenderer.drawText(canvas, inputFont, "#", textX,
                        SkiaFontRenderer.centerBaselineY(inputFont, textCy),
                        Color.makeARGB(alpha, 185, 195, 210));
                float bufX = textX + s(10);
                SkiaFontRenderer.drawText(canvas, inputFont, inputBuffer.toString(), bufX,
                        SkiaFontRenderer.centerBaselineY(inputFont, textCy),
                        Color.makeARGB(alpha, 255, 255, 255));
                if (System.currentTimeMillis() / 500L % 2L == 0L) {
                    float curX = bufX + SkiaFontRenderer.getStringWidth(inputFont, inputBuffer.toString()) + s(2);
                    try (Paint caret = new Paint().setColor(Color.makeARGB(alpha, 255, 255, 255))
                            .setStrokeWidth(s(1.5F)).setAntiAlias(true)) {
                        canvas.drawLine(curX, textCy - s(8), curX, textCy + s(8), caret);
                    }
                }
            } else {
                SkiaFontRenderer.drawText(canvas, inputFont,
                        ColorUtil.formatHex(ColorUtil.hsvToRgb(hue, sat, bri)), textX,
                        SkiaFontRenderer.centerBaselineY(inputFont, textCy),
                        Color.makeARGB(alpha, 235, 238, 245));
            }

            // Confirm / cancel round buttons. A click here applies or discards at
            // once and never holds a press, so these only breathe on hover.
            float cxCancel = cancelCx(panel);
            float cxConfirm = confirmCx(panel);
            float cyButtons = btnCy(panel);
            // The scaled shapes are the s(18)-radius round buttons themselves.
            cancelScale.update(inButton(vmx, vmy, cxCancel, panel), false, dt, Animations.enabled(),
                    s(18) * 2.0F);
            confirmScale.update(inButton(vmx, vmy, cxConfirm, panel), false, dt, Animations.enabled(),
                    s(18) * 2.0F);
            cancelScale.begin(canvas, cxCancel, cyButtons);
            try {
                drawRoundButton(canvas, cxCancel, cyButtons,
                        AppIcons.ICON_CLOSE_PATH, btnHoverClose, alpha);
            } finally {
                canvas.restore();
            }
            confirmScale.begin(canvas, cxConfirm, cyButtons);
            try {
                drawRoundButton(canvas, cxConfirm, cyButtons,
                        AppIcons.ICON_CHECK_PATH, btnHoverCheck, alpha);
            } finally {
                canvas.restore();
            }
        } finally {
            canvas.restore();
        }

        btnHoverCheck = UiMotion.approach(btnHoverCheck,
                inButton(vmx, vmy, confirmCx(panel), panel) ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
        btnHoverClose = UiMotion.approach(btnHoverClose,
                inButton(vmx, vmy, cancelCx(panel), panel) ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
    }

    private static int[] rainbow() {
        int[] colors = new int[7];
        for (int i = 0; i < 7; i++) {
            colors[i] = ColorUtil.hsvToRgb(i / 6.0F, 1.0F, 1.0F);
        }
        return colors;
    }

    private void drawRoundButton(Canvas canvas, float cx, float cy, io.github.humbleui.skija.Path icon,
                                 float hover, int alpha) {
        float r = s(18);
        SkiaDraw.drawRoundedRect(canvas, cx - r, cy - r, 2.0F * r, 2.0F * r, r,
                Color.makeARGB((int) (0.92F * alpha), 35, 39, 47));
        if (hover > 0.01F) {
            SkiaDraw.drawRoundedRect(canvas, cx - r, cy - r, 2.0F * r, 2.0F * r, r,
                    Color.makeARGB((int) (60.0F * hover), 255, 255, 255));
        }
        Rect b = icon.getBounds();
        if (b == null || b.isEmpty()) {
            return;
        }
        float sc = s(16) / Math.max(b.getWidth(), b.getHeight());
        canvas.save();
        try {
            canvas.translate(cx - (b.getLeft() + b.getRight()) / 2.0F * sc,
                    cy - (b.getTop() + b.getBottom()) / 2.0F * sc);
            canvas.scale(sc, sc);
            try (Paint paint = new Paint().setColor(Color.makeARGB(alpha, 255, 255, 255))
                    .setAntiAlias(true).setMode(PaintMode.STROKE)
                    .setStrokeWidth(1.5F / sc)
                    .setStrokeCap(PaintStrokeCap.ROUND)
                    .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally {
            canvas.restore();
        }
    }
}
