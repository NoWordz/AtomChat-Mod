package com.atom.chat.api.card;

import io.github.humbleui.skija.Canvas;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-only Skija custom card renderer registry.
 *
 * <p>Kept separate from {@link CardRegistry} so dedicated servers can touch {@code CardRegistry}
 * without loading client-only Skija classes ({@code io.github.humbleui.skija.Canvas}).
 */
public final class CardRenderRegistry {

    /** Hit-testable interactive region registered during custom card drawing. */
    public interface HitRegistrar {
        void registerHit(float x, float y, float w, float h, ShareCard.CardAction action);
    }

    /** Custom Skija renderer for {@link ShareCard.Template#CUSTOM} cards. */
    public interface CustomCardRenderer {
        /** Returns {@code {width, height}} within {@code maxWidth}. */
        float[] measure(ShareCard card, CardState state, float maxWidth);

        /** Draws the card inside {@code (x, y, w, h)} and optionally registers click regions. */
        void draw(Canvas canvas, ShareCard card, CardState state,
                  float x, float y, float w, float h, HitRegistrar hits);
    }

    /** Read-only presentation on the gameplay HUD, in the host's scaled coordinates. */
    public interface CompactCardRenderer {
        float[] measure(ShareCard card, CardState state, float maxWidth);
        void draw(Canvas canvas, ShareCard card, CardState state, float x, float y, float width, float height);
    }

    private record HudPresentation(CompactCardRenderer renderer, boolean panelOnly) {}
    private static final Map<String, HudPresentation> HUD = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong HUD_REVISION = new java.util.concurrent.atomic.AtomicLong();

    public static void registerCompactRenderer(String customTypeId, CompactCardRenderer renderer) {
        if (customTypeId == null || customTypeId.isBlank() || renderer == null) return;
        HUD.put(customTypeId, new HudPresentation(renderer, false));
        HUD_REVISION.incrementAndGet();
    }

    public static void registerPanelOnly(String customTypeId) {
        if (customTypeId == null || customTypeId.isBlank()) return;
        HUD.put(customTypeId, new HudPresentation(null, true));
        HUD_REVISION.incrementAndGet();
    }

    public static boolean panelOnly(ShareCard card) {
        var entry = hud(card);
        return entry != null && entry.panelOnly();
    }

    public static CompactCardRenderer compactRendererOf(ShareCard card) {
        var entry = hud(card);
        return entry == null ? null : entry.renderer();
    }

    public static long hudRevision() {
        return HUD_REVISION.get();
    }

    private static HudPresentation hud(ShareCard card) {
        return card != null && card.template() == ShareCard.Template.CUSTOM && card.customTypeId() != null
                ? HUD.get(card.customTypeId()) : null;
    }

    private static final Map<String, CustomCardRenderer> RENDERERS = new ConcurrentHashMap<>();

    private CardRenderRegistry() {
    }

    public static void registerRenderer(String customTypeId, CustomCardRenderer renderer) {
        if (customTypeId != null && !customTypeId.isBlank() && renderer != null) {
            RENDERERS.put(customTypeId, renderer);
        }
    }

    public static CustomCardRenderer rendererOf(String customTypeId) {
        return customTypeId == null ? null : RENDERERS.get(customTypeId);
    }
}
