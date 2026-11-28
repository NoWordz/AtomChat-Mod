package com.atom.chat.api.ui;

import com.atom.chat.config.AtomChatConfig;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;

import java.util.List;
import java.util.Objects;

/**
 * Preview extension points for independently selectable AtomChat appearance parts.
 * Register during client initialization and keep each ID stable across releases.
 * Registration and resolution store callbacks without invoking them. The host
 * calls them on the client render thread for the selected appearance.
 */
public final class AppearanceUi {
    public enum Part { BUBBLE, PANEL_OUTLINE, AVATAR_FRAME, DYNAMIC_WALLPAPER }

    /** Metadata for an add-on option. The key is resolved in the player's language by the host. */
    public record Option(String id, String translationKey) {
        public Option {
            UiContributions.requireId(id);
            if (Objects.requireNonNull(translationKey).isBlank()) {
                throw new IllegalArgumentException("Expected a translation key for appearance ID: " + id);
            }
        }
    }

    /** A rectangle in the host's canvas coordinates, with its origin at the upper left. */
    public record Bounds(float x, float y, float width, float height) {
        public float right() { return x + width; }
        public float bottom() { return y + height; }
    }

    public enum BubbleGroup { SINGLE, FIRST, MIDDLE, LAST }

    /**
     * Bubble content bounds measured by AtomChat and group position in conversation order.
     * Scale converts design units to canvas units. Colors are packed ARGB.
     */
    public record BubbleContext(Bounds bounds, BubbleGroup group, boolean own, float scale,
                                int fillColor, int textColor) {}

    /**
     * Full panel bounds before its open/close transform. Corner radius and outline
     * width are the host's styling values in canvas units. Scale converts design
     * units to canvas units.
     */
    public record PanelContext(Bounds bounds, float scale, float cornerRadius, float outlineWidth) {}

    /**
     * The host's avatar image bounds and the allowed frame paint bounds, in canvas
     * coordinates. Scale converts design units to canvas units and color is packed ARGB.
     */
    public record AvatarContext(Bounds avatarBounds, Bounds frameBounds, float scale, int color) {}

    @FunctionalInterface
    public interface Bubble {
        /**
         * Returns a finite, closed, non-inverse filled path within the supplied
         * bounds. Each call transfers ownership of a new path to AtomChat. The
         * host can reuse it for fill, media clips and outline-based effects.
         */
        Path outline(BubbleContext context);

        /**
         * Paints the bubble background inside the supplied outline. AtomChat
         * draws content. The canvas and outline are borrowed for this call and
         * must be left open. The outline must be treated as read-only.
         */
        default void draw(Canvas canvas, BubbleContext context, Path outline) {
            try (var paint = new Paint().setAntiAlias(true).setColor(context.fillColor())) {
                canvas.drawPath(outline, paint);
            }
        }
    }

    @FunctionalInterface
    public interface PanelOutline {
        /**
         * Returns a finite, closed, non-inverse filled exterior path within the
         * supplied bounds. Each call transfers ownership of a new path to
         * AtomChat. The host can cache this path by context and reuse it for
         * shell paint, shadow, blur mask, content/wallpaper clips and hit testing.
         * The host owns the panel layout and applies the same transform to the
         * outline wherever it is used.
         */
        Path outline(PanelContext context);
    }

    @FunctionalInterface
    public interface AvatarFrame {
        /**
         * Paints over the host-drawn avatar inside the declared frame bounds.
         * The host handles avatar images and input. Borrow the canvas for this
         * call only and leave it open.
         */
        void draw(Canvas canvas, AvatarContext context);
    }

    public enum Quality { ULTRA, BALANCED, PERFORMANCE }

    /**
     * The host's sample dimensions, elapsed monotonic animation time and delta
     * since the last sample, in nanoseconds. Both time values start at zero for
     * a new session. Scale converts design units to canvas units.
     */
    public record WallpaperFrame(Bounds bounds, long timeNanos, long deltaNanos,
                                 float scale, Quality quality) {}

    @FunctionalInterface
    public interface DynamicWallpaper {
        /**
         * Creates a new independent session each time the host activates this
         * wallpaper or renews it after a resource reload.
         */
        WallpaperSession open();
    }

    @FunctionalInterface
    public interface WallpaperSession extends AutoCloseable {
        /**
         * Draws one sample on a host-isolated canvas clipped to the frame bounds.
         * The canvas is borrowed for the call and must be left open. The host can
         * share the sample with cards and keep it as a still when motion is disabled.
         */
        void draw(Canvas canvas, WallpaperFrame frame);

        /** Releases session resources when the wallpaper stops, is replaced, or is reloaded. */
        @Override
        default void close() {}
    }

    /**
     * The configured IDs, preserved verbatim across resolution. A null selects
     * the built-in appearance. Missing or malformed IDs also resolve to the
     * built-in appearance without changing the selection.
     */
    public record Selection(String bubbleId, String panelOutlineId, String avatarFrameId,
                            String dynamicWallpaperId) {}

    /**
     * One immutable selection snapshot for layout and rendering. Use the built-in
     * appearance for each null callback, including when only the other surface
     * has an add-on implementation. The revision describes the registry read.
     */
    public record Resolved(long revision, Selection selection, Bubble panelBubble, Bubble hudBubble,
                           PanelOutline panelOutline, AvatarFrame panelAvatarFrame, AvatarFrame hudAvatarFrame,
                           DynamicWallpaper dynamicWallpaper) {}

    private record Variants<T>(T panel, T hud) {
        Variants {
            if (panel == null && hud == null) {
                throw new IllegalArgumentException("An appearance needs a panel or HUD implementation");
            }
        }
    }

    private static final Object LOCK = new Object();
    private static final AppearanceRegistry<Variants<Bubble>> BUBBLES = new AppearanceRegistry<>();
    private static final AppearanceRegistry<PanelOutline> PANEL_OUTLINES = new AppearanceRegistry<>();
    private static final AppearanceRegistry<Variants<AvatarFrame>> AVATAR_FRAMES = new AppearanceRegistry<>();
    private static final AppearanceRegistry<DynamicWallpaper> DYNAMIC_WALLPAPERS = new AppearanceRegistry<>();
    private static volatile long revision;

    private AppearanceUi() {}

    public static boolean validId(String id) {
        if (id == null) return false;
        try {
            UiContributions.requireId(id);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Immutable add-on choices for one part, sorted by ID. */
    public static List<Option> options(Part part) {
        synchronized (LOCK) {
            return registry(part).options();
        }
    }

    /** On the client thread, reads the saved ID. Null means the built-in appearance. */
    public static String selectedId(Part part) {
        return selectedId(part, AtomChatConfig.get());
    }

    /** On the client thread, saves a registered choice. Pass null to restore the built-in appearance. */
    public static boolean select(Part part, String id) {
        if (id != null) {
            synchronized (LOCK) {
                if (registry(part).find(id) == null) return false;
            }
        }
        AtomChatConfig config = AtomChatConfig.get();
        String previous = selectedId(part, config);
        setSelectedId(part, config, id);
        boolean saved = false;
        try {
            saved = AtomChatConfig.saveChecked(config);
            return saved;
        } finally {
            if (!saved) setSelectedId(part, config, previous);
        }
    }

    private static String selectedId(Part part, AtomChatConfig config) {
        return switch (part) {
            case BUBBLE -> config.bubbleAppearanceId;
            case PANEL_OUTLINE -> config.panelOutlineAppearanceId;
            case AVATAR_FRAME -> config.avatarFrameAppearanceId;
            case DYNAMIC_WALLPAPER -> config.dynamicWallpaperAppearanceId;
        };
    }

    private static void setSelectedId(Part part, AtomChatConfig config, String id) {
        switch (part) {
            case BUBBLE -> config.bubbleAppearanceId = id;
            case PANEL_OUTLINE -> config.panelOutlineAppearanceId = id;
            case AVATAR_FRAME -> config.avatarFrameAppearanceId = id;
            case DYNAMIC_WALLPAPER -> config.dynamicWallpaperAppearanceId = id;
        }
    }

    /** Registry version, advanced once per successful registration or removal. */
    public static long revision() {
        return revision;
    }

    /** Registers one bubble choice. Either surface may be null to use its built-in bubble. */
    public static void registerBubble(String id, String translationKey, Bubble panel, Bubble hud) {
        register(BUBBLES, new Option(id, translationKey), new Variants<>(panel, hud));
    }

    public static void registerPanelOutline(String id, String translationKey, PanelOutline outline) {
        register(PANEL_OUTLINES, new Option(id, translationKey), Objects.requireNonNull(outline));
    }

    /** Registers one frame choice. Either surface may be null to use its built-in frame. */
    public static void registerAvatarFrame(String id, String translationKey, AvatarFrame panel, AvatarFrame hud) {
        register(AVATAR_FRAMES, new Option(id, translationKey), new Variants<>(panel, hud));
    }

    public static void registerDynamicWallpaper(String id, String translationKey, DynamicWallpaper wallpaper) {
        register(DYNAMIC_WALLPAPERS, new Option(id, translationKey), Objects.requireNonNull(wallpaper));
    }

    /**
     * Removes the ID from the specified part. Existing snapshots still describe
     * their original read. Resolving again sees the change and leaves configured IDs intact.
     */
    public static boolean unregister(Part part, String id) {
        UiContributions.requireId(id);
        synchronized (LOCK) {
            if (!registry(part).remove(id)) return false;
            revision++;
            return true;
        }
    }

    private static <T> void register(AppearanceRegistry<T> registry, Option option, T value) {
        synchronized (LOCK) {
            registry.add(option, value);
            revision++;
        }
    }

    private static AppearanceRegistry<?> registry(Part part) {
        return switch (part) {
            case BUBBLE -> BUBBLES;
            case PANEL_OUTLINE -> PANEL_OUTLINES;
            case AVATAR_FRAME -> AVATAR_FRAMES;
            case DYNAMIC_WALLPAPER -> DYNAMIC_WALLPAPERS;
        };
    }

    /** Keep one resolver per consumer to reuse its last selection across frames. */
    public static final class Resolver {
        private volatile Resolved cached;

        /**
         * Refreshes only when the selection or registry revision changes. This
         * lookup does not draw, instantiate a wallpaper session, or modify configuration.
         */
        public Resolved resolve(Selection selection) {
            Objects.requireNonNull(selection);
            Resolved result = cached;
            if (matches(result, selection)) return result;
            synchronized (LOCK) {
                result = cached;
                if (matches(result, selection)) return result;

                var bubble = BUBBLES.find(selection.bubbleId());
                var frame = AVATAR_FRAMES.find(selection.avatarFrameId());
                result = new Resolved(revision, selection,
                        bubble == null ? null : bubble.panel(), bubble == null ? null : bubble.hud(),
                        PANEL_OUTLINES.find(selection.panelOutlineId()),
                        frame == null ? null : frame.panel(), frame == null ? null : frame.hud(),
                        DYNAMIC_WALLPAPERS.find(selection.dynamicWallpaperId()));
                cached = result;
                return result;
            }
        }

        private static boolean matches(Resolved snapshot, Selection selection) {
            return snapshot != null && snapshot.revision() == revision && snapshot.selection().equals(selection);
        }
    }
}
