package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.PressScale;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Rect;

/**
 * Drawing primitives and theme tokens (D9): the composer's icon row, the inline
 * SVG icon set, the ARGB blend the hover washes use, and the press/hover state
 * those buttons are drawn from.
 *
 * <p>This is the block the later drawing domains stand on, so it holds no
 * Minecraft type at all — only Skija, the token catalog and the config. What it
 * cannot know comes in as a fact through {@link Host}: the frame delta that
 * steps the two springs, and whether a panel is open (the emoji toggle and the
 * quick-phrase button tint from the panel they own). Geometry, the pointer and
 * the GUI→virtual conversion stay with the screen, which is also where the
 * composer row is laid out and where "the button under the pointer is drawn
 * last" is decided — that order rule belongs to the page body and only reads
 * the state kept here.
 *
 * <p>Press and hover state live here next to the code that steps them (rule 4:
 * state travels with its consumer). The event side writes it through
 * {@link #pressButton} and {@link #setButtonHeld}; the draw side reads it.
 */
public final class DrawPrimitives {
    /**
     * The per-frame facts the composer buttons read from the screen. Two are
     * other domains' state (D23's emoji panel, D16's quick-phrase panel) and one
     * is the screen's own frame clock.
     */
    public interface Host {
        /** Frame delta in ms, the clock the hover wash and the press springs step on. */
        float frameDt();

        /** Whether the emoji panel is open (the emoji button's active tint). */
        boolean emojiPanelOpen();

        /** Whether the quick-phrase panel is open (its button's active tint). */
        boolean phrasePanelOpen();
    }

    private final Host host;

    private int pressedButton = -1;
    private long pressTime;
    /** Button under a held press, for the icon-row press scale; -1 = none. */
    private int pressedButtonHeld = -1;
    /** Per-button press/hover scale (image / emoji / send / phrase), draw-only. */
    // One width-budget bounce per key: the 4px per-side budget stays inside
    // the s(6) gap these four share a row through, at any button width.
    private final PressScale[] iconButtonScale = {PressScale.bounce(), PressScale.bounce(),
            PressScale.bounce(), PressScale.bounce()};

    /** Hover wash per composer key (image / emoji / send / phrase), stepped once per frame. */
    private final float[] buttonHover = new float[4];

    public DrawPrimitives(Host host) {
        this.host = host;
    }

    // ------------------------------------------------------------------ theme

    public int accent() {
        return AtomChatConfig.get().accentColor;
    }

    public int textPrimary() {
        return AtomChatConfig.get().textPrimaryColor;
    }

    public int textSecondary() {
        return AtomChatConfig.get().textSecondaryColor;
    }

    /**
     * Straight sRGB blend of two opaque ARGB colours, {@code t} in [0,1] — the
     * hover wash's own interpolation, so a fade can be drawn without stacking a
     * translucent overlay on a fixed grey.
     */
    public static int lerpArgb(int from, int to, float t) {
        if (t <= 0.0F) {
            return from;
        }
        if (t >= 1.0F) {
            return to;
        }
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return Color.makeARGB(a, r, g, b);
    }

    // ------------------------------------------------------------------ icons

    // Toolbar icons are kept as inline SVG path data (not assets): three tiny
    // paths are cheaper than a resource pipeline, stay crisp at every scale,
    // and are trivial to recolour for hover/pressed/theme states. The paths use
    // a 20x20 logical space; drawIcon() fits them into the button bounds.
    // Landscape photo glyph: a wide rounded rect (14x9 on the 20 grid), sun
    // top-left, mountains inside — the old one was portrait and read as a
    // phone photo instead of an image.
    // Image glyph fills a ~14x12 box: fit-by-longest-edge scaling leaves the
    // old 13x9 glyph visibly shorter than the emoji/send neighbours.
    private static final String ICON_IMAGE_SVG = "M4.5 4 L15.5 4 A1.5 1.5 0 0 1 17 5.5 L17 14.5 A1.5 1.5 0 0 1 15.5 16 L4.5 16 A1.5 1.5 0 0 1 3 14.5 L3 5.5 A1.5 1.5 0 0 1 4.5 4 Z"
            + " M7 7.2 m-1.3 0 a1.3 1.3 0 1 0 2.6 0 a1.3 1.3 0 1 0 -2.6 0"
            + " M4 15 L8.6 10.9 L11.2 13.2 L13.6 10.6 L16 13.2";
    private static final String ICON_EMOJI_SVG = "M10 3 a7 7 0 1 0 0 14 a7 7 0 1 0 0 -14"
            + " M7 8.6 v1.3 M13 8.6 v1.3"
            + " M6.8 12.2 C8.5 14.3 11.5 14.3 13.2 12.2";
    // Feather-style send: one diagonal fold + the paper-plane outline.
    private static final String ICON_SEND_SVG = "M18 2.5 L9.5 11"
            + " M18 2.5 L13.5 18.5 L9.5 11 L2.5 7.5 Z";
    // Context-menu icons, same 20x20 line-icon language as the toolbar.
    private static final String ICON_COPY_SVG = "M5 3 L11 3 L11 9 L5 9 Z M8 7 L14 7 L14 13 L8 13 Z";
    private static final String ICON_QUOTE_SVG = "M3 3 L17 3 L17 12 L10 12 L6 16 L7 12 L3 12 Z"
            + " M7 6 L7 9 M10 6 L10 9";
    private static final String ICON_SAVE_SVG = "M10 3 L10 11 M7 8 L10 11 L13 8"
            + " M4 15 L4 17 L16 17 L16 15";
    // Avatar context-menu icons, same 20x20 line-icon language.
    // Lucide at-sign (ISC): a true "@" shape — inner circle plus the outer
    // ring/tail stroke. Coordinates are in Lucide's 24x24 space and are fitted
    // by drawIconCentered exactly like the other icon paths.
    private static final String ICON_MENTION_SVG = "M12 8 a4 4 0 1 0 0 8 a4 4 0 1 0 0 -8"
            + " M16 8 v5 a3 3 0 0 0 6 0 v-1 a10 10 0 1 0 -4 8";
    private static final String ICON_WHISPER_SVG = "M3 4 L17 4 L17 14 L10 14 L6 18 L7 14 L3 14 Z";
    private static final String ICON_TP_SVG = "M3 17 L17 3 M17 3 L10 3 M17 3 L17 10";
    private static final String ICON_BLOCK_SVG = "M10 3 a7 7 0 1 0 0 14 a7 7 0 1 0 0 -14"
            + " M4.5 4.5 L15.5 15.5";
    public static final Path ICON_IMAGE_PATH = Path.makeFromSVGString(ICON_IMAGE_SVG);
    public static final Path ICON_EMOJI_PATH = Path.makeFromSVGString(ICON_EMOJI_SVG);
    /** Quick-phrase button: a lightning bolt (Lucide zap). */
    private static final String ICON_PHRASE_SVG = "M13 2 3 14h9l-1 8 10-12h-9l1-8z";
    public static final Path ICON_PHRASE_PATH = Path.makeFromSVGString(ICON_PHRASE_SVG);
    public static final Path ICON_SEND_PATH = Path.makeFromSVGString(ICON_SEND_SVG);
    public static final Path ICON_COPY_PATH = Path.makeFromSVGString(ICON_COPY_SVG);
    public static final Path ICON_QUOTE_PATH = Path.makeFromSVGString(ICON_QUOTE_SVG);
    public static final Path ICON_SAVE_PATH = Path.makeFromSVGString(ICON_SAVE_SVG);
    public static final Path ICON_MENTION_PATH = Path.makeFromSVGString(ICON_MENTION_SVG);
    public static final Path ICON_WHISPER_PATH = Path.makeFromSVGString(ICON_WHISPER_SVG);
    public static final Path ICON_TP_PATH = Path.makeFromSVGString(ICON_TP_SVG);
    public static final Path ICON_BLOCK_PATH = Path.makeFromSVGString(ICON_BLOCK_SVG);
    private static final String ICON_JUMP_DOWN_SVG = "M4 8 L10 14 M10 14 L16 8 M10 3 L10 14";
    public static final Path ICON_JUMP_DOWN_PATH = Path.makeFromSVGString(ICON_JUMP_DOWN_SVG);

    // ----------------------------------------------------------- icon drawing

    /**
     * Draws one of the inline SVG-path toolbar icons, centered in its button
     * and scaled to fit an {@code ICON_SIZE} box. Stroke width scales with the
     * rendered icon size so every icon keeps the same optical line weight.
     */
    public void drawIcon(Canvas canvas, Path icon, float bx, float by, int color) {
        drawIcon(canvas, icon, bx, by, UiTokens.ACTION_BUTTON_SIZE, UiTokens.ACTION_BUTTON_SIZE, color);
    }

    /**
     * Draws one of the inline SVG-path toolbar icons, centred in the box it is
     * given. The stroke width follows the rendered glyph size, so both button
     * families keep the same optical line weight.
     */
    public void drawIcon(Canvas canvas, Path icon, float bx, float by,
                         float boxW, float boxH, int color) {
        drawIconCentered(canvas, icon, bx + boxW / 2.0F, by + boxH / 2.0F, UiTokens.s(18), color);
    }

    /** Draws an icon centered on an arbitrary point; used by toolbar and menus. */
    public void drawIconCentered(Canvas canvas, Path icon, float cx, float cy,
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

    // --------------------------------------------------------- composer row

    /**
     * One composer icon button (image / emoji / quick-phrase). The pointer is
     * passed in panel space — the screen converts it, because the conversion is
     * a window read.
     */
    public void drawIconButton(Canvas canvas, float bx, float by, int id, float vmx, float vmy) {
        float frameDt = host.frameDt();
        boolean hover = inputButtonHovered(bx, by, UiTokens.ACTION_BUTTON_SIZE,
                UiTokens.ACTION_BUTTON_SIZE, vmx, vmy);
        boolean pressed = buttonPressed(id);
        boolean pressedHeld = pressedButtonHeld == id;
        buttonHover[id] = UiMotion.approach(buttonHover[id], hover ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
        // Same bounce spring as every other control; the spring keeps running
        // until it settles, so the release overshoot is drawn even after the
        // pointer is up.
        PressScale scale = iconButtonScale[id];
        // The scaled shape is the ACTION_BUTTON_SIZE icon square itself.
        scale.update(hover, pressedHeld, frameDt, Animations.enabled(), UiTokens.ACTION_BUTTON_SIZE);
        canvas.save();
        if (scale.scale() != 1.0F) {
            float keyHalf = UiTokens.ACTION_BUTTON_SIZE / 2.0F;
            canvas.translate(bx + keyHalf, by + keyHalf);
            canvas.scale(scale.scale(), scale.scale());
            canvas.translate(-(bx + keyHalf), -(by + keyHalf));
        }
        // Same art language as the header buttons: the icon sits on the composer,
        // and a hover draws an inset rounded wash in the accent. No standing
        // plate and no hairline - a plate would make the row read as three cards,
        // and the hairline was only there to separate that plate from the panel.
        float hoverWash = buttonHover[id];
        float pressedWash = pressed ? 1.0F : 0.0F;
        float wash = Math.max(hoverWash, pressedWash);
        if (wash > 0.01F) {
            float inset = UiTokens.EDGE_CONTROL_INSET;
            SkiaDraw.drawRoundedRect(canvas, bx + inset, by + inset,
                    UiTokens.ACTION_BUTTON_SIZE - inset * 2.0F,
                    UiTokens.ACTION_BUTTON_SIZE - inset * 2.0F,
                    UiTokens.radius(8), UiTokens.cardHover(wash));
        }
        // Active states take the accent colour: the emoji button while its
        // panel is open (a toggle), any button for a moment after a press.
        boolean activeTint = (id == 1 && host.emojiPanelOpen()) || (id == 3 && host.phrasePanelOpen())
                || pressed;
        Path icon = switch (id) {
            case 0 -> ICON_IMAGE_PATH;
            case 3 -> ICON_PHRASE_PATH;
            default -> ICON_EMOJI_PATH;
        };
        drawIcon(canvas, icon, bx, by, activeTint ? accent() : textPrimary());
        canvas.restore();
    }

    /**
     * The wider Send capsule of the same row. The pointer is passed in panel
     * space (see {@link #drawIconButton}).
     */
    public void drawSendButton(Canvas canvas, float bx, float by, float vmx, float vmy) {
        float frameDt = host.frameDt();
        boolean hover = inputButtonHovered(bx, by, UiTokens.BUTTON_W, UiTokens.BUTTON_H, vmx, vmy);
        boolean pressed = buttonPressed(2);
        boolean pressedHeld = pressedButtonHeld == 2;
        buttonHover[2] = UiMotion.approach(buttonHover[2], hover ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
        // Same press/hover scale as the icon buttons and the bottom tab bar.
        PressScale scale = iconButtonScale[2];
        // The scaled shape is the send button itself (BUTTON_W capsule).
        scale.update(hover, pressedHeld, frameDt, Animations.enabled(), UiTokens.BUTTON_W);
        canvas.save();
        if (scale.scale() != 1.0F) {
            canvas.translate(bx + UiTokens.BUTTON_W / 2.0F, by + UiTokens.BUTTON_H / 2.0F);
            canvas.scale(scale.scale(), scale.scale());
            canvas.translate(-(bx + UiTokens.BUTTON_W / 2.0F), -(by + UiTokens.BUTTON_H / 2.0F));
        }
        SkiaDraw.drawRoundedRect(canvas, bx, by, UiTokens.BUTTON_W, UiTokens.BUTTON_H, UiTokens.BUTTON_RADIUS, accent());
        float overlay = buttonHover[2] * 55.0F + (pressed ? 90.0F : 0.0F);
        if (overlay > 0.5F) {
            SkiaDraw.drawRoundedRect(canvas, bx, by, UiTokens.BUTTON_W, UiTokens.BUTTON_H, UiTokens.BUTTON_RADIUS,
                    Color.makeARGB((int) Math.min(160, overlay), 255, 255, 255));
        }
        // Icon ink derives from the solid accent fill it sits on (same language
        // as the selected bottom tab), not from the panel's text colour: a pale
        // accent capsule needs dark glyphs even on a dark panel.
        drawIcon(canvas, ICON_SEND_PATH, bx, by, UiTokens.BUTTON_W, UiTokens.BUTTON_H,
                UiTokens.onAccent(accent()));
        canvas.restore();
    }

    /**
     * True when the pointer is inside a composer button rect (panel-space
     * coords). The box is passed in because the row carries two shapes: the
     * square composer keys and the wider Send capsule.
     */
    public boolean inputButtonHovered(float bx, float by, float w, float h, float vmx, float vmy) {
        return vmx >= bx && vmx <= bx + w && vmy >= by && vmy <= by + h;
    }

    // ------------------------------------------------------------ press state

    public boolean buttonPressed(int id) {
        return pressedButton == id && System.currentTimeMillis() - pressTime < 150L;
    }

    public void pressButton(int id) {
        pressedButton = id;
        pressTime = System.currentTimeMillis();
    }

    /**
     * Records which button a held press belongs to; -1 on release. Written by
     * the composer's input handler, which owns the press/release pairing.
     */
    public void setButtonHeld(int id) {
        pressedButtonHeld = id;
    }
}
