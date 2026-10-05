package com.atom.chat.notification;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.PlayerAvatar;
import com.atom.chat.render.Easing;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
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
 * and their 4s lifetime starts at enqueue. Opening the panel within that window
 * reveals whatever is still alive; otherwise the {@code @N} unread badge carries
 * the signal.
 *
 * <p>Visuals follow the content-card language: opaque card fill, inner edge
 * highlight, pointer-hover wash — plus an iOS-style drop-in with a slight
 * overshoot and a slide-out on expiry. A round send-style button on the right
 * jumps straight into a reply; the rest of the banner just navigates.
 */
public final class NotificationBanner {
    public static final NotificationBanner INSTANCE = new NotificationBanner();

    public enum Type { MENTION, QUOTE, WHISPER }

    private static final long VISIBLE_MS = 4000L;
    private static final long APPEAR_MS = 220L;
    private static final long DISAPPEAR_MS = 150L;
    private static final int MAX_STACK = 3;

    /** Round send-style action button on the right edge of every banner. */
    private static final float BUTTON_SIZE = UiTokens.s(26);
    /** iOS-style drop-in travel distance. */
    private static final float DROP_TRAVEL = UiTokens.s(14);

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
        }
    }

    /**
     * Retires banners only after the slide-out has had its 150ms, so the exit
     * animation is actually visible. The 4s "lifetime" still starts at enqueue.
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
        float bannerW = Math.min(UiTokens.s(320), Math.max(UiTokens.s(180), panelW - UiTokens.s(24)));
        float bannerH = UiTokens.s(58);
        float gap = UiTokens.s(6);
        float x = panelX + (panelW - bannerW) / 2.0F;
        float y = panelY + UiTokens.HEADER_HEIGHT + UiTokens.s(6);

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
            drawBanner(canvas, b, x, drawY, bannerW, bannerH, alpha, hover, buttonHover);
            hitRects.add(new Hit(b, Rect.makeXYWH(x, drawY, bannerW, bannerH)));
            float btnX = x + bannerW - UiTokens.s(14) - BUTTON_SIZE;
            float btnY = drawY + (bannerH - BUTTON_SIZE) / 2.0F;
            buttonRects.add(new Hit(b, Rect.makeXYWH(btnX, btnY, BUTTON_SIZE, BUTTON_SIZE)));
            y += bannerH + gap;
        }
        pruneHovers(hoverAlphas);
        pruneHovers(buttonHovers);
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
    }

    private float advanceHover(Map<Active, Float> alphas, Active b, boolean on, float hoverDtMs) {
        float next = UiMotion.approach(alphas.getOrDefault(b, 0.0F), on ? 1.0F : 0.0F,
                (long) hoverDtMs, UiMotion.HOVER_MS);
        alphas.put(b, next);
        return next;
    }

    private void pruneHovers(Map<Active, Float> alphas) {
        if (alphas.size() > MAX_STACK * 2) {
            alphas.keySet().removeIf(b -> {
                for (Active active : banners) {
                    if (active == b) {
                        return false;
                    }
                }
                return true;
            });
        }
    }

    private void drawBanner(Canvas canvas, Active b, float x, float y, float w, float h,
                            float alpha, float hover, float buttonHover) {
        float radius = UiTokens.radius(12);
        // save() and saveLayer() push two entries; both must be popped. A missing
        // restore here leaked one matrix per frame and, combined with the
        // per-frame density scale, flung the whole panel off screen in 0.2.4.
        canvas.save();
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * alpha), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(x - UiTokens.s(8), y - UiTokens.s(8), w + UiTokens.s(16), h + UiTokens.s(16)), layer);
            try {
                // Card language: opaque card fill + inner edge highlight, exactly
                // like conversation cards and settings rows.
                AtomChatConfig config = AtomChatConfig.get();
                SkiaDraw.drawRoundedRect(canvas, x, y, w, h, radius,
                        0xFF000000 | (config.cardColor & 0x00FFFFFF));
                SkiaDraw.drawEdgeHighlight(canvas, x, y, w, h, radius, UiTokens.s(1.2F), UiTokens.CARD_EDGE);
                if (hover > 0.01F) {
                    SkiaDraw.drawRoundedRect(canvas, x, y, w, h, radius,
                            Color.makeARGB((int) (90.0F * hover), 255, 255, 255));
                }

                float padX = UiTokens.s(14);
                float avatarSize = UiTokens.s(28);
                float avatarX = x + padX;
                float avatarY = y + (h - avatarSize) / 2.0F;
                ChatMessage msg = b.message();
                Image face = msg != null ? PlayerAvatar.face(msg.getSenderUuid(), msg.getProfileName()) : null;
                if (face != null) {
                    SkiaDraw.drawRoundedImage(canvas, face, avatarX, avatarY, avatarSize, avatarSize,
                            avatarSize / 2.0F, SamplingMode.LINEAR);
                } else {
                    SkiaDraw.drawRoundedRect(canvas, avatarX, avatarY, avatarSize, avatarSize,
                            avatarSize / 2.0F, Color.makeARGB(255, 120, 130, 145));
                }

                float btnX = x + w - padX - BUTTON_SIZE;
                float btnY = y + (h - BUTTON_SIZE) / 2.0F;
                float textX = avatarX + avatarSize + UiTokens.s(8);
                float textW = Math.max(UiTokens.s(20), btnX - UiTokens.s(8) - textX);
                float line1Y = y + UiTokens.s(20);
                float line2Y = y + UiTokens.s(40);
                Font titleFont = FontManager.boldFont(UiTokens.FONT_NAME);
                Font bodyFont = FontManager.font(UiTokens.FONT_QUOTE);
                String typeLabel = tr(typeKey(b.type()));
                String title = typeLabel + (b.sender() != null && !b.sender().isBlank() ? "  " + b.sender() : "");
                SkiaFontRenderer.drawText(canvas, titleFont,
                        SkiaFontRenderer.truncate(titleFont, title, textW),
                        textX, SkiaFontRenderer.centerBaselineY(titleFont, line1Y), config.textPrimaryColor);

                String preview = b.content() == null ? "" : b.content().replace('\n', ' ');
                SkiaFontRenderer.drawText(canvas, bodyFont,
                        SkiaFontRenderer.truncate(bodyFont, preview, textW),
                        textX, SkiaFontRenderer.centerBaselineY(bodyFont, line2Y), config.textSecondaryColor);

                drawRoundButton(canvas, btnX, btnY, BUTTON_SIZE, buttonHover);
            } finally {
                canvas.restore();
            }
        } finally {
            canvas.restore();
        }
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
