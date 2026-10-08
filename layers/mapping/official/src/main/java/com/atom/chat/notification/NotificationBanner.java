package com.atom.chat.notification;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.PlayerAvatar;
import com.atom.chat.render.Easing;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.PressScale;
import com.atom.chat.ui.UiCards;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Skia notification banner stack. <b>Panel-only:</b> banners are drawn on the
 * AtomChat screen canvas and never on the HUD. 0.2.4 drew them through a shared
 * {@code SkiaGraphics} instance from a {@code HudRenderCallback}; an unbalanced
 * {@code saveLayer} there leaked a matrix every frame and flung the whole UI off
 * screen, so the HUD path is gone on purpose — do not reintroduce it.
 *
 * <p>Queueing is global: events are enqueued whether or not the panel is open,
 * and their lifetime starts at enqueue. Opening the panel within that window
 * reveals whatever is still alive; otherwise the {@code @N} unread badge carries
 * the signal.
 *
 * <p>Visuals follow the shell's own language: the shared float-surface family
 * ({@link com.atom.chat.ui.UiCards#drawFloatSurface}) — an opaque themed fill
 * with ink derived from it, one accent-dyed hover wash and the two-tier chrome
 * shadow the header and tab bar ride — placed on the list's card column (the
 * content column inset by the same {@link com.atom.chat.ui.UiTokens#ROW_CLIP_INSET}
 * the conversation list's row cards use, through the one rule in
 * {@link UiLayout#cardColumnX}), plus an
 * iOS-style drop-in with a slight overshoot and a slide-out on expiry. A round send-style button on the right
 * jumps straight into a reply; the rest of the banner just navigates.
 */
public final class NotificationBanner {
    public static final NotificationBanner INSTANCE = new NotificationBanner();

    public enum Type { MENTION, QUOTE, WHISPER }

    /**
     * How long a banner is held before it slides out. Raised from 4s to 6s after
     * field feedback that a banner arrived and left before the reader could take
     * in the sender and the body.
     */
    private static final long VISIBLE_MS = 6000L;
    private static final long APPEAR_MS = 220L;
    private static final long DISAPPEAR_MS = 150L;
    private static final int MAX_STACK = 3;

    /** Round send-style action button on the right edge of every banner. */
    private static final float BUTTON_SIZE = UiTokens.s(26);
    /** iOS-style drop-in travel distance. */
    private static final float DROP_TRAVEL = UiTokens.s(14);

    /**
     * Vertical padding above the title row and below the body row. Published,
     * together with {@link #TEXT_LINE_GAP}, because they are the parts
     * {@link #bannerHeight()} is built from: the height is a contract the banner's
     * test recomputes, not a number nobody can see.
     */
    public static final float TEXT_PAD_Y = UiTokens.s(9);
    /** Gap between the title row and the body row; see {@link #TEXT_PAD_Y}. */
    public static final float TEXT_LINE_GAP = UiTokens.s(3);
    /**
     * Floor under {@link #bannerHeight()}: the leading avatar and the trailing
     * send button are the tallest things in the bar, so a thin font can never
     * squeeze it under the controls it is holding.
     */
    private static final float MIN_BANNER_H = Math.max(UiTokens.ACTION_BUTTON_SIZE, BUTTON_SIZE);

    /**
     * Left edge of a banner: the conversation list's card column, published so the
     * test can bind it to the column the row cards use. The banner is handed the
     * panel rect rather than a {@link UiLayout} (the screen owns that signature),
     * so it reads the column from the one rule both surfaces share instead of
     * rebuilding an equal-looking expression of its own — which is how it came to
     * wear a {@code max(320, panelW - 24)} width no other surface in the shell
     * uses.
     */
    public static float bannerX(float panelX) {
        return UiLayout.cardColumnX(panelX);
    }

    /** Width of a banner; see {@link #bannerX(float)}. */
    public static float bannerWidth(float panelW) {
        return UiLayout.cardColumnW(panelW);
    }

    /**
     * Height of one banner: the title row's text height, the gap, the body row's
     * text height and {@link #TEXT_PAD_Y} above and below — never
     * {@code HEADER_HEIGHT}. Both rows used to be pinned to 32% and 66% of a
     * header-tall bar, which left a 17px title row and a 16px body row fifteen
     * pixels apart in a card that was too short to hold them.
     */
    public static float bannerHeight() {
        float rows = TextMetricsHolder.TITLE_H + TEXT_LINE_GAP + TextMetricsHolder.BODY_H;
        return Math.max(MIN_BANNER_H, rows + TEXT_PAD_Y * 2.0F);
    }

    /**
     * Text metrics of the banner's two rows, measured lazily: {@link #bannerHeight()}
     * runs on the render thread while the panel is open, and holding these in
     * class-level fields would pull the Skija native library in at class
     * initialisation — the same reason {@link #sendPath()} sits behind a holder.
     */
    private static final class TextMetricsHolder {
        private static final float TITLE_H =
                SkiaFontRenderer.textHeight(FontManager.boldFont(UiTokens.FONT_NAME));
        private static final float BODY_H =
                SkiaFontRenderer.textHeight(FontManager.font(UiTokens.FONT_QUOTE));
    }

    /** Feather paper plane, same glyph as the composer send button (20x20).
     *  Held lazily so class initialization (client tick) never loads the Skija
     *  native library for users who never open the panel. */
    private static Path sendPath() {
        return SendPathHolder.PATH;
    }

    private static final class SendPathHolder {
        private static final Path PATH =
                Path.makeFromSVGString("M18 2.5 L13.5 18.5 L9.5 11 L2.5 7.5 Z M18 2.5 L9.5 11");
    }

    private final List<Active> banners = new ArrayList<>();
    /** Banner rectangles captured during render, used for click hit-testing. */
    private final List<Hit> hitRects = new ArrayList<>();
    /** Per-banner send-button rectangles, checked before the whole-banner hit. */
    private final List<Hit> buttonRects = new ArrayList<>();
    /** Hover alphas, identity-keyed: two banners can carry equal data. */
    private final Map<Active, Float> hoverAlphas = new IdentityHashMap<>();
    private final Map<Active, Float> buttonHovers = new IdentityHashMap<>();
    /** Send-button bounce, identity-keyed for the same reason as the hovers. */
    private final Map<Active, PressScale> buttonScales = new IdentityHashMap<>();
    private long lastHoverMs;

    public record Active(Type type, String sender, String content, long born, ChatMessage message) {
    }

    private record Hit(Active banner, Rect rect) {
    }

    private NotificationBanner() {
    }

    public void enqueue(Type type, String sender, String content, ChatMessage message) {
        long now = System.currentTimeMillis();
        banners.add(0, new Active(type, sender, content, now, message));
        while (banners.size() > MAX_STACK) {
            Active dropped = banners.remove(banners.size() - 1);
            hoverAlphas.remove(dropped);
            buttonHovers.remove(dropped);
            buttonScales.remove(dropped);
        }
    }

    /**
     * Retires banners only after the slide-out has had its 150ms, so the exit
     * animation is actually visible. The hold still starts at enqueue.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        Iterator<Active> it = banners.iterator();
        while (it.hasNext()) {
            Active b = it.next();
            if (now - b.born() >= VISIBLE_MS + DISAPPEAR_MS) {
                it.remove();
                hoverAlphas.remove(b);
                buttonHovers.remove(b);
                buttonScales.remove(b);
            }
        }
    }

    public boolean hasActive() {
        return !banners.isEmpty();
    }

    /** [0..1] alpha: easeOutQuad drop-in, linear slide-out. */
    private float alphaFor(Active b, long now) {
        float age = now - b.born();
        if (age <= APPEAR_MS) {
            return Easing.easeOutQuad(age / (float) APPEAR_MS);
        }
        float left = VISIBLE_MS + DISAPPEAR_MS - age;
        return Math.max(0.0F, Math.min(1.0F, left / (float) DISAPPEAR_MS));
    }

    /**
     * Vertical offset: drops in from above with a slight overshoot
     * (easeOutBack), then slides back up while fading out.
     */
    private float offsetFor(Active b, long now) {
        float age = now - b.born();
        if (age <= APPEAR_MS) {
            return -(1.0F - Easing.easeOutBack(age / (float) APPEAR_MS)) * DROP_TRAVEL;
        }
        float gone = Math.max(0.0F, Math.min(1.0F, (age - VISIBLE_MS) / (float) DISAPPEAR_MS));
        return -gone * gone * DROP_TRAVEL;
    }

    /**
     * Renders the notification stack inside the AtomChat panel (below its
     * header). This is the only render path: banners are never drawn on the
     * HUD, so no Skia state can leak outside the panel.
     */
    public void renderInPanel(Canvas canvas, float panelX, float panelY, float panelW, float panelH,
                              float vmx, float vmy) {
        // Hit lists are filled at the END of this render and consumed here at
        // the TOP of the next frame: the hover target must be computed BEFORE
        // the clear, or the lists are empty and hover can never light up.
        Active hovered = hitTest(vmx, vmy);
        Active hoveredButton = buttonHit(vmx, vmy);
        hitRects.clear();
        buttonRects.clear();
        if (banners.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        // Hover fade duration must use the REAL inter-frame time. The caller's
        // tickDelta (0..1) was once passed through here as if it were ms, which
        // made the fade crawl at ~1/90th of its intended speed.
        float hoverDt = Math.min(50.0F, Math.max(1.0F, now - lastHoverMs));
        lastHoverMs = now;
        // One column, not an island of its own: the banner spans exactly the
        // column the conversation list's row cards draw into — x and width from
        // the one rule both surfaces read (bannerX/bannerWidth, i.e.
        // UiLayout.cardColumnX/W: the content column inset by ROW_CLIP_INSET per
        // side). Its old max(320, panelW - 24) width came to 70% of the column on
        // a standard panel, the one surface in the shell wearing a size no other
        // surface uses, and its y ignored the header's own PANEL_BOTTOM_PAD and so
        // overlapped the header card by 10px. The y below is the content column's
        // own top edge (PANEL_BOTTOM_PAD + HEADER_HEIGHT + PANEL_TOP_GAP).
        float bannerW = bannerWidth(panelW);
        // Two single-line rows, so the bar is as tall as the text it holds (see
        // bannerHeight) rather than as tall as the header's bar.
        float bannerH = bannerHeight();
        float gap = UiTokens.s(6);
        float x = bannerX(panelX);
        float y = panelY + UiTokens.PANEL_BOTTOM_PAD + UiTokens.HEADER_HEIGHT + UiTokens.PANEL_TOP_GAP;

        for (int i = banners.size() - 1; i >= 0; i--) {
            Active b = banners.get(i);
            float alpha = alphaFor(b, now);
            if (alpha <= 0.01F) {
                continue;
            }
            float drawY = y + offsetFor(b, now);
            if (drawY + bannerH > panelY + panelH - UiTokens.s(4)) {
                continue;
            }
            float hover = advanceHover(hoverAlphas, b, b == hovered, hoverDt);
            float buttonHover = advanceHover(buttonHovers, b, b == hoveredButton, hoverDt);
            // Advanced before the draw so the bounce lands on the frame it started
            // in. The banner tracks no press state, so the dip never fires and the
            // button only breathes on hover.
            PressScale sendScale = buttonScales.computeIfAbsent(b, k -> PressScale.bounce());
            // The scaled shape is the send button itself (BUTTON_SIZE square).
            sendScale.update(b == hoveredButton, false, hoverDt, Animations.enabled(), BUTTON_SIZE);
            drawBanner(canvas, b, x, drawY, bannerW, bannerH, alpha, hover, buttonHover, sendScale);
            hitRects.add(new Hit(b, Rect.makeXYWH(x, drawY, bannerW, bannerH)));
            float btnX = x + bannerW - UiTokens.s(14) - BUTTON_SIZE;
            float btnY = drawY + (bannerH - BUTTON_SIZE) / 2.0F;
            buttonRects.add(new Hit(b, Rect.makeXYWH(btnX, btnY, BUTTON_SIZE, BUTTON_SIZE)));
            y += bannerH + gap;
        }
        pruneHovers(hoverAlphas);
        pruneHovers(buttonHovers);
        pruneHovers(buttonScales);
    }

    /** Which banner's send button is under this panel-space point, or {@code null}. */
    public Active buttonHit(float x, float y) {
        // Newest first: hitRects is filled oldest-first, but the newest banner
        // paints on top, so it must win when animated rects overlap.
        for (int i = buttonRects.size() - 1; i >= 0; i--) {
            Hit hit = buttonRects.get(i);
            Rect r = hit.rect;
            if (x >= r.getLeft() && x <= r.getRight() && y >= r.getTop() && y <= r.getBottom()) {
                return hit.banner;
            }
        }
        return null;
    }

    /** Which banner is under this panel-space point, or {@code null}. */
    public Active hitTest(float x, float y) {
        for (int i = hitRects.size() - 1; i >= 0; i--) {
            Hit hit = hitRects.get(i);
            Rect r = hit.rect;
            if (x >= r.getLeft() && x <= r.getRight() && y >= r.getTop() && y <= r.getBottom()) {
                return hit.banner;
            }
        }
        return null;
    }

    /** Removes a single banner (used when the player clicks it). By identity —
     *  two banners can hold equal data, and equals-based remove would drop the wrong one. */
    public void dismiss(Active banner) {
        banners.removeIf(b -> b == banner);
        hitRects.removeIf(h -> h.banner == banner);
        buttonRects.removeIf(h -> h.banner == banner);
        hoverAlphas.remove(banner);
        buttonHovers.remove(banner);
        buttonScales.remove(banner);
    }

    private float advanceHover(Map<Active, Float> alphas, Active b, boolean on, float hoverDtMs) {
        float next = UiMotion.approach(alphas.getOrDefault(b, 0.0F), on ? 1.0F : 0.0F,
                (long) hoverDtMs, UiMotion.HOVER_MS);
        alphas.put(b, next);
        return next;
    }

    private void pruneHovers(Map<Active, ?> hoverStates) {
        if (hoverStates.size() > MAX_STACK * 2) {
            hoverStates.keySet().removeIf(b -> {
                for (Active active : banners) {
                    if (active == b) {
                        return false;
                    }
                }
                return true;
            });
        }
    }

    /** Vertical centres of a banner's two single-line rows. */
    private record Rows(float title, float body) {
    }

    /**
     * Places the two single-line rows inside a banner of height {@code h} whose
     * top edge is {@code y}: the block of title + gap + body is centred in the
     * box, and never comes closer to an edge than {@link #TEXT_PAD_Y}. The parts
     * are the ones {@link #bannerHeight()} is built from, so the rows and the box
     * that holds them cannot be sized from different numbers.
     */
    private static Rows textRows(float y, float h) {
        float titleH = TextMetricsHolder.TITLE_H;
        float bodyH = TextMetricsHolder.BODY_H;
        float top = y + Math.max(TEXT_PAD_Y, (h - (titleH + TEXT_LINE_GAP + bodyH)) / 2.0F);
        return new Rows(top + titleH / 2.0F, top + titleH + TEXT_LINE_GAP + bodyH / 2.0F);
    }

    private void drawBanner(Canvas canvas, Active b, float x, float y, float w, float h,
                            float alpha, float hover, float buttonHover, PressScale sendScale) {
        float radius = UiTokens.cardRadius();
        // save() and saveLayer() push two entries; both must be popped. A missing
        // restore here leaked one matrix per frame and, combined with the
        // per-frame density scale, flung the whole panel off screen in 0.2.4.
        canvas.save();
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * alpha), 0, 0, 0));
            // Padded to the family's MEASURED shadow reach, not to the blur
            // radius: the offset plus the gaussian tail travels ~40px, so the
            // old 10px pad clipped the shadow into a visible rectangle edge.
            canvas.saveLayer(shadowLayer(x, y, w, h), layer);
            try {
                // One float-surface family, shared with the action toast: an
                // opaque fill that follows the theme's card colour through the
                // card-tint slider, and ink derived from that fill rather than
                // from the panel's text colour. The old explicit
                // 0xFF000000 | cardColor was opaque white for the default card
                // colour, which painted a white card under panel-polarity text
                // and made the sender name invisible on it.
                int fill = UiTokens.floatSurfaceFill();
                UiCards.drawFloatSurface(canvas, x, y, w, h, radius, hover, fill);

                float padX = UiTokens.s(14);
                // The header's own control size: this bar is header-height now,
                // so its leading control is the same square the header uses.
                float avatarSize = UiTokens.ACTION_BUTTON_SIZE;
                float avatarX = x + padX;
                float avatarY = y + (h - avatarSize) / 2.0F;
                ChatMessage msg = b.message();
                Image face = msg != null ? PlayerAvatar.face(msg.getSenderUuid(), msg.getProfileName()) : null;
                if (face != null) {
                    SkiaDraw.drawRoundedImage(canvas, face, avatarX, avatarY, avatarSize, avatarSize,
                            avatarSize / 2.0F, SamplingMode.LINEAR);
                } else {
                    SkiaDraw.drawRoundedRect(canvas, avatarX, avatarY, avatarSize, avatarSize,
                            avatarSize / 2.0F, UiTokens.onFloatSurfaceTint(fill));
                }
                // Hairline rim hugging the avatar's outer edge (face or
                // placeholder): polarity-adaptive UiTokens.rim separates the
                // circle from the banner card behind it. Same ring language
                // as the swatches.
                SkiaDraw.drawRing(canvas, avatarX + avatarSize / 2.0F, avatarY + avatarSize / 2.0F,
                        avatarSize / 2.0F + UiTokens.s(0.75F), UiTokens.s(1.0F), UiTokens.rim(fill));

                float btnX = x + w - padX - BUTTON_SIZE;
                float btnY = y + (h - BUTTON_SIZE) / 2.0F;
                float textX = avatarX + avatarSize + UiTokens.s(10);
                float textW = Math.max(UiTokens.s(20), btnX - UiTokens.s(8) - textX);
                // One single-line row each, placed inside the box bannerHeight()
                // sized for them: the title's row, the gap, the body's row, from
                // the same parts, so neither row can be pushed out of the card the
                // way the old h*0.32 / h*0.66 offsets pushed the preview when the
                // height was the header bar's.
                Rows rows = textRows(y, h);
                Font titleFont = FontManager.boldFont(UiTokens.FONT_NAME);
                Font bodyFont = FontManager.font(UiTokens.FONT_QUOTE);
                String typeLabel = tr(typeKey(b.type()));
                String title = typeLabel + (b.sender() != null && !b.sender().isBlank() ? "  " + b.sender() : "");
                SkiaFontRenderer.drawText(canvas, titleFont,
                        SkiaFontRenderer.truncate(titleFont, title, textW),
                        textX, SkiaFontRenderer.centerBaselineY(titleFont, rows.title()),
                        UiTokens.onFloatSurface(fill));

                String preview = b.content() == null ? "" : b.content().replace('\n', ' ');
                SkiaFontRenderer.drawText(canvas, bodyFont,
                        SkiaFontRenderer.truncate(bodyFont, preview, textW),
                        textX, SkiaFontRenderer.centerBaselineY(bodyFont, rows.body()),
                        UiTokens.onFloatSurfaceSecondary(fill));

                // The whole control scales: fill, wash and glyph travel together.
                // begin() keeps its save even at scale 1, so the restore below is
                // always paired and the frame's save stack stays balanced.
                sendScale.begin(canvas, btnX + BUTTON_SIZE / 2.0F, btnY + BUTTON_SIZE / 2.0F);
                try {
                    drawRoundButton(canvas, btnX, btnY, BUTTON_SIZE, buttonHover);
                } finally {
                    canvas.restore();
                }
            } finally {
                canvas.restore();
            }
        } finally {
            canvas.restore();
        }
    }

    /**
     * The saveLayer rect for one floating surface, padded by the family's
     * measured shadow reach. The banner and the toast both route their own rect
     * through here, so the pad can never drift from the shadow it must contain.
     */
    private static Rect shadowLayer(float x, float y, float w, float h) {
        float pad = UiTokens.floatSurfaceShadowPad();
        return Rect.makeXYWH(x - pad, y - pad, w + pad * 2.0F, h + pad * 2.0F);
    }

    /** Send-language round button: accent fill + paper plane, hover wash exactly
     *  like the composer's send button (drawSendButton). */
    private void drawRoundButton(Canvas canvas, float x, float y, float size, float hover) {
        AtomChatConfig config = AtomChatConfig.get();
        SkiaDraw.drawRoundedRect(canvas, x, y, size, size, size / 2.0F, config.accentColor);
        float wash = hover * 55.0F;
        if (wash > 0.5F) {
            SkiaDraw.drawRoundedRect(canvas, x, y, size, size, size / 2.0F,
                    Color.makeARGB((int) wash, 255, 255, 255));
        }
        drawIconCentered(canvas, sendPath(), x + size / 2.0F, y + size / 2.0F, UiTokens.s(12),
                config.textPrimaryColor);
    }

    private static void drawIconCentered(Canvas canvas, Path icon, float cx, float cy, float size, int color) {
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
                    .setStrokeWidth(1.5F / scale)
                    .setStrokeCap(PaintStrokeCap.ROUND)
                    .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally {
            canvas.restore();
        }
    }

    private static String typeKey(Type type) {
        return switch (type) {
            case MENTION -> "atomchat.notify.mention";
            case QUOTE -> "atomchat.notify.quote";
            case WHISPER -> "atomchat.notify.whisper";
        };
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    /** Whether notifications are enabled for this type at all. */
    public static boolean enabled(Type type) {
        AtomChatConfig config = AtomChatConfig.get();
        return switch (type) {
            case MENTION, QUOTE -> config.mentionBannerEnabled;
            case WHISPER -> config.whisperBannerEnabled;
        };
    }

    /** Whether the sound for this type is enabled. */
    public static boolean soundEnabled(Type type) {
        AtomChatConfig config = AtomChatConfig.get();
        return switch (type) {
            case MENTION, QUOTE -> config.mentionSoundEnabled;
            case WHISPER -> config.whisperSoundEnabled;
        };
    }
}
