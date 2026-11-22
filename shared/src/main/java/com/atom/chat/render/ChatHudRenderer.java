package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.MessageMerge;
import com.atom.chat.chat.ImageCode;
import com.atom.chat.chat.MessageGrouping;
import com.atom.chat.chat.OwnIdentity;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.image.MessageImage;
import com.atom.chat.text.RichText;
import com.atom.chat.text.RichTextLayout.RichLine;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.chat.ui.ChatShortcutHint;
import com.atom.chat.ui.HudTransform;
import com.atom.chat.ui.LayoutTokens;
import com.atom.chat.ui.MessageGeometry;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Read-only, bottom-up presentation for the unfocused vanilla chat HUD. */
public final class ChatHudRenderer {
    private static final int MAX_LAYOUTS = 100;
    private static final float VANILLA_LEFT_MARGIN = 4F;
    private static final float MIN_FIT_SCALE = .75F;

    public record Viewport(float x, float bottom, float width, float height, float density, float guiPerUnit,
                           HudTransform transform) {
        public Viewport(float x, float bottom, float width, float height, float density, float guiPerUnit) {
            this(x, bottom, width, height, density, guiPerUnit, HudTransform.IDENTITY);
        }

        public static Viewport of(Matrix4f pose, int guiWidth, int guiHeight, double guiScale, double chatScale,
                                  int chatWidth) {
            HudTransform transform = HudTransform.from(pose);
            if (!(chatScale > 0) || !(guiScale > 0) || transform == null) return null;
            float guiPerUnit = (float) chatScale * LayoutTokens.HUD_ICON_GUI / UiTokens.AVATAR_SIZE;
            float x = VANILLA_LEFT_MARGIN * (float) chatScale;
            float shortcutTop = guiHeight - LayoutTokens.HUD_MARGIN_GUI - LayoutTokens.HUD_ICON_GUI;
            float bottom = shortcutTop - LayoutTokens.HUD_MESSAGE_GAP_GUI;
            if (transform.scaleX() == 1F && transform.scaleY() == 1F) {
                // Forge chat position events translate this layer alone. Preserve its screen bounds.
                x += transform.x();
                bottom = Math.min(guiHeight, bottom + transform.y());
                transform = HudTransform.IDENTITY;
            }
            return new Viewport(x / guiPerUnit, bottom / guiPerUnit, Math.min(guiWidth - x, chatWidth) / guiPerUnit,
                    bottom / guiPerUnit, (float) guiScale * guiPerUnit, guiPerUnit, transform);
        }

        Viewport local() {
            return transform == HudTransform.IDENTITY ? this : new Viewport(x, bottom, width, height, density, guiPerUnit);
        }

        Viewport fit(int limit) {
            return fit(limit, 0F);
        }

        Viewport fit(int limit, float newestQuoteHeight) {
            float minHeight = minPlayerHeight(FontManager.font(UiTokens.FONT_BODY), MessageGeometry.avatarInset());
            float needed = Math.max(minHeight + newestQuoteHeight, limit * minHeight + (limit - 1) * UiTokens.LIST_GAP);
            // Keep a stable scale as messages arrive, with a floor for very small windows.
            float scale = Math.max(MIN_FIT_SCALE, Math.min(1F, height / (needed + .1F)));
            return scale >= 1F ? this : new Viewport(x / scale, bottom / scale, width / scale, height / scale,
                    density * scale, guiPerUnit * scale, transform);
        }
    }

    private record Measured(ChatMessage message, List<RichLine> name, List<RichLine> body, Font bodyFont,
                            float lineHeight, float width, float bubbleHeight, float nameWidth,
                            boolean hasItems, ImageCode.Meta image, boolean imagePlaceholder, List<RichLine> quote, float quoteWidth,
                            float quoteHeight, String repeatLabel, float repeatLane, Object continuationInk) {}
    private record Placed(Measured measured, boolean showSender, float thumbnailWidth, float thumbnailHeight,
                          float height) {
        float nameBand() {
            return showSender ? UiTokens.NAME_BAND : 0F;
        }

        float staticWidth() {
            return Math.max(showSender ? measured.nameWidth() : 0F, Math.max(measured.width(), measured.quoteWidth()));
        }
    }
    private static final ChatHudHistory.Merger<ChatMessage> REPEATS =
            new ChatHudHistory.Merger<>(MessageMerge::canMergeForDisplay, MessageMerge::mergeForDisplay);
    private static final int DEFAULT_NAME_INK = 0xFFFFFFFF;
    private static final io.github.humbleui.skija.ImageFilter NAME_SHADOW =
            io.github.humbleui.skija.ImageFilter.makeDropShadowOnly(0F, 1F, 1.5F, 1.5F, 0xA0000000);

    private record Ink(AtomChatConfig.BubbleStyle shape, int own, int other, int ownText, int otherText,
                       int capsule, int capsuleText, int accent, float capsuleRadius, boolean shadow,
                       io.github.humbleui.skija.ImageFilter nameFilter) {
        Ink(AtomChatConfig config) {
            this(config.effectiveBubbleStyle(), config.ownBubbleColor, config.otherBubbleColor,
                    config.bubbleTextColor, config.otherBubbleTextColor,
                    config.effectiveSecondaryCapsuleBg(), config.secondaryCapsuleText, config.accentColor,
                    UiTokens.radius(10, AtomChatAppearance.theme()), !config.performanceMode,
                    config.performanceMode ? null : NAME_SHADOW);
        }
    }

    private final IdentityHashMap<ChatMessage, Measured> layouts = new IdentityHashMap<>();
    private final ChatHudRasterCache rasterInk = new ChatHudRasterCache(32);
    private final ChatHudRasterCache captionInk = new ChatHudRasterCache(32);
    private final MessageNameShadow nameShadow = new MessageNameShadow(32);
    private final List<ChatMessage> recent = new ArrayList<>(AtomChatConfig.CHAT_HUD_MAX_MESSAGES);
    private final List<ChatMessage> arranged = new ArrayList<>(AtomChatConfig.CHAT_HUD_MAX_MESSAGES);
    private final List<ChatHudMotion.Row<Placed>> visibleRows = new ArrayList<>(AtomChatConfig.CHAT_HUD_MAX_MESSAGES);
    private final List<Measured> measuredRows = new ArrayList<>(AtomChatConfig.CHAT_HUD_MAX_MESSAGES);
    private final boolean[] continuations = new boolean[AtomChatConfig.CHAT_HUD_MAX_MESSAGES];
    private final ChatHudMotion<Placed> motion = new ChatHudMotion<>();
    private final SkiaGraphics graphics = new SkiaGraphics();
    private final ChatHudRasterCache.SurfaceFactory gpuInk = graphics::createInkSurface;
    private final BiFunction<String, Boolean, MessageImage> visibleImage;
    private WeakReference<Object> world;
    private Viewport lastViewport;
    private Viewport unfittedViewport;
    private Viewport fittedViewport;
    private int unfittedLimit;
    private Font fittedBodyFont;
    private float fittedAvatarInset, fittedListGap, fittedNewestQuoteHeight;
    private Font layoutBodyFont, layoutNameFont, layoutCapsuleFont;
    private float layoutWidth;
    private float layoutAvatarInset;
    private float inlineItemGap;
    private String layoutOwnName, layoutUnknownName, layoutImageLabel;
    private int lastLimit;
    private boolean lastAnimationEnabled, lastImagesEnabled, lastPerformanceMode;
    private boolean lastCompact;
    private int lastTimestampInterval;
    private boolean departingRows;
    private Ink lastInk;
    private float lastInkDensity;
    private long lastInkGeneration;
    private boolean hasFrame;
    private boolean hasSelection;
    private boolean panelFrame;
    private boolean paintingStarted;
    private boolean failed;

    public ChatHudRenderer() {
        this((url, visible) -> ImageLoader.get().getHudMessageImage(url, visible));
    }

    ChatHudRenderer(BiFunction<String, Boolean, MessageImage> visibleImage) {
        this.visibleImage = visibleImage;
    }

    public void clear() {
        world = null;
        layouts.clear();
        suspend();
    }

    /** The next visible frame settles existing messages after a hidden or vanilla-only pass. */
    public void suspend() {
        if (!hasSelection && !hasFrame) return;
        recent.clear();
        arranged.clear();
        measuredRows.clear();
        visibleRows.clear();
        motion.clear();
        clearRasterInk();
        departingRows = false;
        hasSelection = false;
        hasFrame = false;
        panelFrame = false;
    }

    public <K, C> void select(Object world, ChatHudHistory<K, C, ChatMessage> history, List<K> entries, int limit,
                              Function<K, ChatMessage> fallback, boolean panel) {
        select(world, history, entries, limit, fallback, panel, null, 0);
    }

    public <K, C> void select(Object world, ChatHudHistory<K, C, ChatMessage> history, List<K> entries, int limit,
                              Function<K, ChatMessage> fallback, boolean panel, ToIntFunction<K> receivedTick, int tick) {
        if ((this.world == null ? null : this.world.get()) != world) {
            clear();
            if (world != null) this.world = new WeakReference<>(world);
        }
        if (limit == 0) {
            suspend();
            return;
        }
        // Keep the pre-panel selection while it leaves. The first gameplay pass after close
        // takes a fresh selection, including messages received while the panel owned input.
        if (!panel || !hasSelection) {
            if (MessageMerge.antiSpamEnabled()) {
                history.copyRecentMerged(entries, limit, fallback, receivedTick, tick, recent, REPEATS);
            } else {
                history.copyRecent(entries, limit, fallback, receivedTick, tick, recent);
            }
            hasSelection = true;
        }
        panelFrame = panel;
        if (panel) hasFrame = false;
    }

    /** The bridge targets Minecraft's main framebuffer. Leave cached or clipped passes to vanilla. */
    public boolean render(Viewport viewport, UUID ownUuid, String ownName, String unknownName, String imageLabel,
                          int mainFramebuffer, Runnable flush, Consumer<SkiaGraphics> prepareIcons,
                          int limit, int opacity, float visibility, boolean animationEnabled, long now) {
        if (visibility <= 0F || limit == 0) {
            hasFrame = false;
            return true;
        }
        if (failed || viewport == null || viewport.width() <= 0 || viewport.bottom() <= 0) return false;
        if (recent.isEmpty() && visibleRows.isEmpty() && !departingRows) {
            hasFrame = true;
            lastLimit = limit;
            return true;
        }
        paintingStarted = false;
        try {
            if (GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING) != mainFramebuffer
                    || GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST)) return false;
            Viewport fittedViewport = fittedViewport(viewport.local(), limit);
            var config = AtomChatConfig.get();
            prepareRows(recent, fittedViewport, ownName, unknownName, imageLabel, config, limit, animationEnabled, now);
            if (visibleRows.isEmpty() && !departingRows) return recent.isEmpty();
            boolean prepare = hasInlineItems();
            flush.run();
            graphics.draw(prepare ? () -> prepareIcons.accept(graphics) : null, fittedViewport.density(), (canvas, ignored) -> {
                paintingStarted = true;
                prepareInk(config, fittedViewport, graphics.inkGeneration());
                paintRows(canvas, fittedViewport, viewport.transform(), ownUuid, ownName, unknownName,
                        opacity, visibility, now, gpuInk);
            });
        } catch (RuntimeException | LinkageError error) {
            failed = true;
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render",
                    "Chat HUD painter failed, falling back to vanilla"), error);
        }
        // If painting already started, avoid a duplicate vanilla draw in this frame.
        return paintingStarted;
    }

    void paint(Canvas canvas, List<ChatMessage> messages, Viewport viewport, UUID ownUuid, String ownName,
               String unknownName, AtomChatConfig config, int limit, int opacity, float visibility,
               boolean animationEnabled, long now) {
        paint(canvas, messages, viewport, ownUuid, ownName, unknownName, config, limit, opacity, visibility,
                animationEnabled, now, null, 0L);
    }

    void paint(Canvas canvas, List<ChatMessage> messages, Viewport viewport, UUID ownUuid, String ownName,
               String unknownName, AtomChatConfig config, int limit, int opacity, float visibility,
               boolean animationEnabled, long now, ChatHudRasterCache.SurfaceFactory inkSurface, long generation) {
        Viewport local = viewport.local();
        prepareRows(messages, local, ownName, unknownName, "[Image]", config, limit, animationEnabled, now);
        prepareInk(config, local, generation);
        paintRows(canvas, local, viewport.transform(), ownUuid, ownName, unknownName, opacity, visibility, now, inkSurface);
    }

    private Viewport fittedViewport(Viewport viewport, int limit) {
        Font font = FontManager.font(UiTokens.FONT_BODY);
        float avatarInset = MessageGeometry.avatarInset();
        float newestQuoteHeight = recent.isEmpty() ? 0F : quoteHeight(recent.get(0));
        if (!viewport.equals(unfittedViewport) || limit != unfittedLimit || font != fittedBodyFont
                || avatarInset != fittedAvatarInset || UiTokens.LIST_GAP != fittedListGap
                || newestQuoteHeight != fittedNewestQuoteHeight) {
            unfittedViewport = viewport;
            unfittedLimit = limit;
            fittedBodyFont = font;
            fittedAvatarInset = avatarInset;
            fittedListGap = UiTokens.LIST_GAP;
            fittedNewestQuoteHeight = newestQuoteHeight;
            fittedViewport = viewport.fit(limit, newestQuoteHeight);
        }
        return fittedViewport;
    }

    private void prepareRows(List<ChatMessage> messages, Viewport viewport, String ownName, String unknownName,
                             String imageLabel, AtomChatConfig config, int limit, boolean animationEnabled, long now) {
        boolean performanceChanged = config.performanceMode != lastPerformanceMode;
        if (performanceChanged && config.performanceMode) {
            motion.clear();
            departingRows = false;
        } else if (departingRows) {
            departingRows = motion.retire(now);
        }
        if (limit == 0) {
            visibleRows.clear();
            arranged.clear();
            measuredRows.clear();
            motion.clear();
            departingRows = false;
            hasFrame = false;
            return;
        }
        Font nameFont = FontManager.font(UiTokens.FONT_NAME);
        Font bodyFont = FontManager.font(UiTokens.FONT_BODY);
        Font capsuleFont = FontManager.font(UiTokens.FONT_QUOTE);
        float avatarInset = MessageGeometry.avatarInset();
        boolean invalidateLayout = layoutWidth != viewport.width() || layoutBodyFont != bodyFont
                || layoutNameFont != nameFont || layoutCapsuleFont != capsuleFont || inlineItemGap != UiTokens.INLINE_ITEM_GAP
                || layoutAvatarInset != avatarInset || !Objects.equals(layoutOwnName, ownName)
                || !Objects.equals(layoutUnknownName, unknownName) || !Objects.equals(layoutImageLabel, imageLabel);
        if (invalidateLayout) {
            layouts.clear();
            clearRasterInk();
            layoutBodyFont = bodyFont;
            layoutNameFont = nameFont;
            layoutCapsuleFont = capsuleFont;
            layoutWidth = viewport.width();
            layoutAvatarInset = avatarInset;
            inlineItemGap = UiTokens.INLINE_ITEM_GAP;
            layoutOwnName = ownName;
            layoutUnknownName = unknownName;
            layoutImageLabel = imageLabel;
        }
        boolean sameGeometry = viewport.equals(lastViewport) && limit == lastLimit && !invalidateLayout && !performanceChanged
                && lastImagesEnabled == config.imageMessagesEnabled
                && lastCompact == config.compactMessagesEnabled && lastTimestampInterval == config.timestampIntervalMinutes;
        // Keep an in-progress row animation behind the panel's group slide.
        if (panelFrame && sameGeometry && (!visibleRows.isEmpty() || departingRows)) return;
        if (hasFrame && sameGeometry && animationEnabled == lastAnimationEnabled && sameSelection(messages, limit)) return;
        ChatMessage newest = messages.isEmpty() ? null : messages.get(0);
        ChatMessage previous = arranged.isEmpty() ? null : arranged.get(0);
        // Geometry/font changes settle the stack, so departures cannot keep old text resources.
        boolean animate = hasFrame && animationEnabled && limit == lastLimit && (previous == null || sameGeometry);
        boolean arriving = newest != null && (previous == null || newest.getId() != previous.getId());
        motion.begin(now, animate, arriving);
        visibleRows.clear();
        float avatarLane = avatarInset + UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP;
        float minPlayerHeight = minPlayerHeight(bodyFont, avatarInset);
        float bottom = viewport.bottom();
        float top = Math.max(0F, bottom - viewport.height());
        measuredRows.clear();
        float minimumPile = 0F;
        for (int i = 0; i < messages.size() && i < limit; i++) {
            ChatMessage message = messages.get(i);
            boolean system = message.isSystem();
            float maxBubbleWidth = viewport.width() - (system ? 0F : avatarLane);
            if (maxBubbleWidth <= 0F) break;
            Measured candidate = layout(message, system ? capsuleFont : bodyFont, nameFont,
                    ownName, unknownName, imageLabel, system ? UiTokens.SYSTEM_BUBBLE_PAD_Y : UiTokens.BUBBLE_PAD_Y, maxBubbleWidth);
            float next = minimumPile + minimumHeight(candidate, false, minPlayerHeight);
            boolean grouped = false;
            if (i > 0) {
                Measured newer = measuredRows.get(i - 1);
                grouped = config.compactMessagesEnabled
                        && MessageGrouping.continues(message, newer.message(), config.timestampIntervalMinutes);
                next += rowGap(grouped) + minimumHeight(newer, grouped, minPlayerHeight)
                        - minimumHeight(newer, false, minPlayerHeight);
            }
            if (next > bottom - top) break;
            if (i > 0) continuations[i - 1] = grouped;
            continuations[i] = false; // The oldest visible row owns the sender band.
            measuredRows.add(candidate);
            minimumPile = next;
        }
        int count = measuredRows.size();
        for (int i = 0; i < count; i++) {
            Measured row = measuredRows.get(i);
            ChatMessage message = row.message();
            boolean grouped = continuations[i];
            boolean showSender = !grouped && !message.isSystem();
            float gap = i + 1 < count ? rowGap(grouped) : 0F;
            float minHeight = minimumHeight(row, grouped, minPlayerHeight);
            minimumPile -= minHeight + gap;
            float available = bottom - top - gap - Math.max(0F, minimumPile);
            float band = showSender ? UiTokens.NAME_BAND : 0F;
            float thumbnailWidth = 0F;
            float thumbnailHeight = 0F;
            if (row.image() != null && config.imageMessagesEnabled) {
                thumbnailWidth = Math.min(UiTokens.IMAGE_MAX_W,
                        Math.max(1F, viewport.width() - avatarLane - row.repeatLane()));
                thumbnailHeight = Math.max(1F, Math.min(2F * minPlayerHeight, available - row.quoteHeight()) - band);
                if (row.image().width() > 0 && row.image().height() > 0) {
                    thumbnailHeight = Math.min(thumbnailHeight, thumbnailWidth * row.image().height() / row.image().width());
                }
            }
            float rowHeight = Math.max(minHeight, band + row.quoteHeight() + thumbnailHeight);
            float rowTop = bottom - rowHeight;
            float entryX = message.isSystem() ? 0F : message.isOwn() ? UiTokens.MESSAGE_SLIDE : -UiTokens.MESSAGE_SLIDE;
            visibleRows.add(motion.place(message.getId(), new Placed(row, showSender, thumbnailWidth, thumbnailHeight, rowHeight),
                    rowTop, entryX));
            bottom = rowTop - gap;
        }
        motion.finish();
        departingRows = motion.retire(now);
        arranged.clear();
        for (int i = 0; i < messages.size() && i < limit; i++) arranged.add(messages.get(i));
        lastViewport = viewport;
        lastLimit = limit;
        lastImagesEnabled = config.imageMessagesEnabled;
        lastCompact = config.compactMessagesEnabled;
        lastTimestampInterval = config.timestampIntervalMinutes;
        lastAnimationEnabled = animationEnabled;
        lastPerformanceMode = config.performanceMode;
        hasFrame = true;
    }

    private boolean sameSelection(List<ChatMessage> messages, int limit) {
        if (arranged.size() != Math.min(messages.size(), limit)) return false;
        for (int i = 0; i < arranged.size(); i++) {
            if (arranged.get(i) != messages.get(i)) return false;
        }
        return true;
    }

    private static float rowGap(boolean grouped) {
        return grouped ? MessageGeometry.groupedGap() : UiTokens.LIST_GAP;
    }

    private static float minimumHeight(Measured row, boolean grouped, float minPlayerHeight) {
        float height = row.bubbleHeight();
        if (!grouped && !row.message().isSystem()) {
            height = Math.max(minPlayerHeight, height + UiTokens.NAME_BAND);
        }
        return height + row.quoteHeight();
    }

    private static float minPlayerHeight(Font bodyFont, float avatarInset) {
        return Math.max(avatarInset * 2F + UiTokens.AVATAR_SIZE,
                UiTokens.NAME_BAND + UiTokens.BUBBLE_PAD_Y + SkiaFontRenderer.getHeight(bodyFont));
    }

    private static float quoteHeight(ChatMessage message) {
        return message.isSystem() || message.getQuoteName() == null ? 0F : UiTokens.QUOTE_HEIGHT + UiTokens.QUOTE_GAP;
    }

    private Measured layout(ChatMessage message, Font font, Font nameFont, String ownName, String unknownName,
                            String imageLabel, float padY, float maxBubbleWidth) {
        Measured cached = layouts.get(message);
        if (cached != null) return cached;
        RichText body = MessagePaint.body(message);
        String content = message.getContentRich().getString();
        ImageCode.Meta image = null;
        boolean imagePlaceholder = !message.isSystem() && ImageCode.contains(content);
        if (message.isSystem()) {
            var ranges = ImageCode.ranges(content);
            for (int i = ranges.size() - 1; i >= 0; i--) {
                int[] range = ranges.get(i);
                body = body.splice(range[0], range[1], RichText.literal(imageLabel));
            }
        } else if (imagePlaceholder) {
            // Inspect the reply body separately from the quoted content.
            image = ImageCode.parse(content);
            body = RichText.literal(imageLabel);
        }
        RichText name = message.getSenderRich().stripInteractions();
        if (!message.isSystem() && name.isEmpty()) {
            name = message.isOwn() ? OwnIdentity.displayNameRich() : RichText.literal(senderName(message, ownName, unknownName));
        }
        String repeatLabel = message.getDuplicateCount() > 1 ? "x" + message.getDuplicateCount() : null;
        float repeatLane = repeatLabel == null ? 0F
                : UiTokens.INLINE_ITEM_GAP + SkiaFontRenderer.getStringWidth(nameFont, repeatLabel);
        maxBubbleWidth = Math.max(1F, maxBubbleWidth - repeatLane);
        float textWidth = Math.max(1F, maxBubbleWidth - 2F * UiTokens.BUBBLE_PAD);
        List<RichLine> lines = MessagePaint.oneLine(body, font, textWidth);
        List<RichLine> caption = MessagePaint.oneLine(name, nameFont, textWidth);
        float bodyWidth = lines.isEmpty() ? 0F : RichTextRenderer.width(font, lines.get(0));
        float nameWidth = caption.isEmpty() ? 0F : RichTextRenderer.width(nameFont, caption.get(0));
        float bubbleWidth = Math.min(maxBubbleWidth, Math.max(UiTokens.BUBBLE_MIN_W, bodyWidth + 2F * UiTokens.BUBBLE_PAD));
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float bubbleHeight = lineHeight + padY;
        float quoteHeight = quoteHeight(message);
        boolean hasItems = !lines.isEmpty() && lines.get(0).parts().stream().anyMatch(part -> part.isItem());
        hasItems |= !caption.isEmpty() && caption.get(0).parts().stream().anyMatch(part -> part.isItem());
        List<RichLine> quote = List.of();
        float quoteWidth = 0F;
        if (quoteHeight > 0F) {
            RichText text = MessagePaint.richQuote(message);
            if (text == null) {
                String quotedName = message.getQuoteName();
                String prefix = quotedName.startsWith("@") ? "" : "@";
                text = RichText.literal(prefix + quotedName + ": " + Objects.toString(message.getQuoteText(), ""));
            }
            float quotePadding = MessagePaint.quoteTextInset() + UiTokens.QUOTE_PAD_X;
            quote = MessagePaint.oneLine(text, layoutCapsuleFont, Math.max(1F, maxBubbleWidth - quotePadding));
            float quoteTextWidth = quote.isEmpty() ? 0F : RichTextRenderer.width(layoutCapsuleFont, quote.get(0));
            quoteWidth = Math.min(maxBubbleWidth, quoteTextWidth + quotePadding);
            hasItems |= !quote.isEmpty() && quote.get(0).parts().stream().anyMatch(part -> part.isItem());
        }
        var result = new Measured(message, caption, lines, font, lineHeight, bubbleWidth, bubbleHeight,
                nameWidth, hasItems, image, imagePlaceholder,
                quote, quoteWidth, quoteHeight, repeatLabel, repeatLane, new Object());
        if (layouts.size() >= MAX_LAYOUTS) layouts.clear();
        layouts.put(message, result);
        return result;
    }

    private void prepareInk(AtomChatConfig config, Viewport viewport, long generation) {
        var ink = new Ink(config);
        if (!ink.equals(lastInk) || lastInkDensity != viewport.density() || lastInkGeneration != generation) {
            clearRasterInk();
            lastInk = ink;
            lastInkDensity = viewport.density();
            lastInkGeneration = generation;
        }
    }

    private void clearRasterInk() {
        rasterInk.clear();
        captionInk.clear();
        nameShadow.clear();
    }

    private boolean hasInlineItems() {
        for (var row : visibleRows) {
            if (row.content().measured().hasItems()) return true;
        }
        if (departingRows) {
            for (var row : motion.rows()) {
                if (row.departing() && row.content().measured().hasItems()) return true;
            }
        }
        return false;
    }

    private void paintRows(Canvas canvas, Viewport viewport, HudTransform transform, UUID ownUuid, String ownName, String unknownName,
                           int opacity, float visibility, long now, ChatHudRasterCache.SurfaceFactory inkSurface) {
        if (visibility <= 0F || opacity <= 0 || visibleRows.isEmpty() && !departingRows) return;
        float top = Math.max(0F, viewport.bottom() - viewport.height());
        float pad = HudShadow.PAD;
        Rect clip = Rect.makeLTRB(0F, Math.max(0F, top - pad),
                viewport.x() + viewport.width() + UiTokens.MESSAGE_SLIDE + pad, viewport.bottom() + pad);
        int save = canvas.save();
        try {
            transform.apply(canvas, 1F / viewport.guiPerUnit());
            canvas.clipRect(clip);
            if (visibility < 1F) {
                var group = ChatShortcutHint.frame(visibility);
                opacity = Math.round(opacity * group.alpha());
                if (opacity <= 0) return;
                canvas.translate(group.offsetX() / viewport.guiPerUnit(), 0F);
            }
            var grid = visibility >= 1F ? ChatHudRasterCache.PixelGrid.from(canvas, viewport.density()) : null;
            if (opacity < 255) {
                Rect bounds = groupInkBounds(viewport, now);
                canvas.clipRect(bounds);
                canvas.saveLayerAlpha(bounds, opacity);
            }
            if (departingRows) {
                for (var row : motion.rows()) {
                    if (row.departing()) drawRow(canvas, row, viewport, ownUuid, ownName, unknownName, now, null, inkSurface);
                }
            }
            // Keep the new row in front if its entrance briefly intersects the moving stack.
            for (int i = visibleRows.size() - 1; i >= 0; i--) {
                drawRow(canvas, visibleRows.get(i), viewport, ownUuid, ownName, unknownName, now, grid, inkSurface);
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private Rect groupInkBounds(Viewport viewport, long now) {
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (var row : motion.rows()) {
            if (row.content() == null || row.alpha(now) <= 0F) continue;
            float x = viewport.x() + row.xOffset(now);
            float y = row.y(now);
            float width = inkWidth(row.content());
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x + width);
            bottom = Math.max(bottom, y + row.content().height());
        }
        float pad = HudShadow.PAD + 1F;
        if (left > right) return Rect.makeWH(0F, 0F);
        return Rect.makeLTRB(left - pad, top - pad, right + pad, bottom + pad);
    }

    private float inkWidth(Placed placed) {
        Measured measured = placed.measured();
        float avatarLane = measured.message().isSystem() ? 0F
                : layoutAvatarInset + UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP;
        float contentWidth = Math.max(measured.width(), placed.thumbnailWidth()) + measured.repeatLane();
        return avatarLane + Math.max(placed.staticWidth(), contentWidth);
    }

    private void drawRow(Canvas canvas, ChatHudMotion.Row<Placed> row, Viewport viewport, UUID ownUuid,
                         String ownName, String unknownName, long now, ChatHudRasterCache.PixelGrid grid,
                         ChatHudRasterCache.SurfaceFactory inkSurface) {
        int alpha = (int) (255F * row.alpha(now));
        if (alpha <= 0) return;
        Placed placed = row.content();
        Measured measured = placed.measured();
        ChatMessage message = measured.message();
        float x = viewport.x() + row.xOffset(now);
        float y = row.y(now);
        float avatarInset = layoutAvatarInset;
        float bubbleX = message.isSystem() ? x : x + avatarInset + UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP;
        boolean fading = alpha < 255;
        float pad = HudShadow.PAD;
        if (fading) canvas.saveLayerAlpha(Rect.makeXYWH(x - pad, y - pad,
                inkWidth(placed) + 2F * pad, placed.height() + 2F * pad), alpha);
        try {
            if (placed.showSender()) {
                boolean own = message.isOwn();
                UUID uuid = own ? ownUuid : message.getSenderUuid();
                String profile = own ? ownName : message.getProfileName();
                if (profile == null || profile.isBlank()) profile = senderName(message, ownName, unknownName);
                if (lastInk.shadow()) {
                    HudShadow.drawAvatar(canvas, RRect.makeXYWH(x + avatarInset, y + avatarInset,
                            UiTokens.AVATAR_SIZE, UiTokens.AVATAR_SIZE, UiTokens.AVATAR_SIZE / 2F));
                }
                MessagePaint.avatar(canvas, message, uuid, profile, x + avatarInset, y + avatarInset);
            }
            MessageImage attachment = placed.thumbnailHeight() > 0F
                    ? visibleImage.apply(measured.image().url(), !row.departing()) : null;
            Image image = attachment == null ? null : attachment.image();
            boolean cacheable = grid != null && row.settled(now) && !measured.hasItems();
            float repeatX = bubbleX + measured.width();
            float repeatY = y + placed.nameBand() + measured.quoteHeight() + measured.bubbleHeight() / 2F;
            if (image != null) {
                if (!cacheable || !drawStaticInk(canvas, placed, bubbleX, y, grid, inkSurface, true)) {
                    drawCaptionAndQuote(canvas, placed, bubbleX, y, viewport.density());
                }
                float scale = Math.min(placed.thumbnailWidth() / image.getWidth(), placed.thumbnailHeight() / image.getHeight());
                float imageY = y + placed.nameBand() + measured.quoteHeight();
                float width = image.getWidth() * scale, height = image.getHeight() * scale;
                if (lastInk.shadow() && !attachment.transparent()) {
                    HudShadow.draw(canvas, ChatBubble.outline(bubbleX, imageY, width, height, lastInk.shape(), false, 1F));
                }
                ChatBubble.drawImage(canvas, image, bubbleX, imageY, width, height, lastInk.shape(), false);
                repeatX = bubbleX + width;
                repeatY = imageY + height / 2F;
            } else if (!cacheable || !drawStaticInk(canvas, placed, bubbleX, y, grid, inkSurface, false)) {
                drawInk(canvas, placed, bubbleX, y, viewport.density());
            }
            if (measured.repeatLabel() != null) {
                SkiaFontRenderer.drawText(canvas, layoutNameFont, measured.repeatLabel(),
                        repeatX + UiTokens.INLINE_ITEM_GAP, SkiaFontRenderer.centerBaselineY(layoutNameFont, repeatY),
                        DEFAULT_NAME_INK, lastInk.shadow());
            }
        } finally {
            if (fading) canvas.restore();
        }
    }

    private boolean drawStaticInk(Canvas canvas, Placed placed, float x, float y, ChatHudRasterCache.PixelGrid grid,
                                  ChatHudRasterCache.SurfaceFactory inkSurface, boolean captionOnly) {
        Measured measured = placed.measured();
        Object key = placed.showSender() || measured.message().isSystem() ? measured : measured.continuationInk();
        float captionHeight = placed.nameBand() + measured.quoteHeight();
        if (captionOnly) {
            if (captionHeight <= 0F) return true;
            return captionInk.drawAligned(canvas, key, x, y,
                    Math.max(placed.showSender() ? measured.nameWidth() : 0F, measured.quoteWidth()), captionHeight,
                    grid, inkSurface, (target, inkX, inkY) -> drawCaptionAndQuote(target, placed, inkX, inkY, 0F));
        }
        return rasterInk.drawAligned(canvas, key, x, y, placed.staticWidth(), captionHeight + measured.bubbleHeight(),
                grid, inkSurface, (target, inkX, inkY) -> drawInk(target, placed, inkX, inkY, 0F));
    }

    private void drawCaptionAndQuote(Canvas canvas, Placed placed, float x, float y, float liveDensity) {
        Measured measured = placed.measured();
        if (placed.showSender()) drawSender(canvas, measured, x, y, liveDensity);
        if (measured.quoteHeight() <= 0F) return;
        float quoteY = y + placed.nameBand();
        if (lastInk.shadow()) {
            HudShadow.draw(canvas, RRect.makeXYWH(x, quoteY, measured.quoteWidth(), UiTokens.QUOTE_HEIGHT, MessagePaint.quoteRadius()));
        }
        MessagePaint.quoteCapsule(canvas, x, quoteY, measured.quoteWidth(), lastInk.capsule(), lastInk.accent());
        RichTextRenderer.drawLines(canvas, layoutCapsuleFont, measured.quote(), x + MessagePaint.quoteTextInset(),
                quoteY + UiTokens.QUOTE_HEIGHT / 2F, SkiaFontRenderer.getHeight(layoutCapsuleFont),
                lastInk.capsuleText(), null, false, TextSurface.CAPSULE.shadowed(), capsuleTextShadow());
    }

    private void drawSender(Canvas canvas, Measured measured, float x, float y, float liveDensity) {
        float lineHeight = SkiaFontRenderer.getHeight(layoutNameFont);
        float centerY = y + UiTokens.NAME_BAND / 2F;
        if (liveDensity > 0F) {
            nameShadow.draw(canvas, layoutNameFont, measured.name(), measured.nameWidth(), x,
                    centerY, lineHeight, liveDensity, lastInk.nameFilter());
        }
        RichTextRenderer.drawLines(canvas, layoutNameFont, measured.name(), x, y + UiTokens.NAME_BAND / 2F,
                lineHeight, DEFAULT_NAME_INK, null, false, liveDensity <= 0F,
                lastInk.nameFilter());
    }

    private void drawInk(Canvas canvas, Placed placed, float x, float y, float liveDensity) {
        Measured measured = placed.measured();
        float height = measured.bubbleHeight();
        if (measured.message().isSystem()) {
            if (lastInk.shadow()) HudShadow.draw(canvas, RRect.makeXYWH(x, y, measured.width(), height, lastInk.capsuleRadius()));
            CornerGeometry.drawRoundedRect(canvas, x, y, measured.width(), height, lastInk.capsuleRadius(), lastInk.capsule());
            RichTextRenderer.drawLines(canvas, measured.bodyFont(), measured.body(), x + UiTokens.BUBBLE_PAD,
                    y + height / 2F, measured.lineHeight(), lastInk.capsuleText(), null, false,
                    TextSurface.CAPSULE.shadowed(), capsuleTextShadow());
        } else {
            boolean own = measured.message().isOwn();
            drawCaptionAndQuote(canvas, placed, x, y, liveDensity);
            float bubbleY = y + placed.nameBand() + measured.quoteHeight();
            boolean placeholder = measured.imagePlaceholder();
            if (lastInk.shadow()) {
                HudShadow.draw(canvas, ChatBubble.outline(x, bubbleY, measured.width(), height, lastInk.shape(), false, 1F));
            }
            ChatBubble.draw(canvas, x, bubbleY, measured.width(), height, lastInk.shape(), false,
                    placeholder ? lastInk.capsule() : own ? lastInk.own() : lastInk.other());
            RichTextRenderer.drawLines(canvas, measured.bodyFont(), measured.body(), x + UiTokens.BUBBLE_PAD,
                    bubbleY + height / 2F, measured.lineHeight(),
                    placeholder ? lastInk.capsuleText() : own ? lastInk.ownText() : lastInk.otherText(),
                    null, false, placeholder, capsuleTextShadow());
        }
    }

    private io.github.humbleui.skija.ImageFilter capsuleTextShadow() {
        return lastInk.shadow() ? SkiaFontRenderer.backingShadowFor(lastInk.capsule()) : null;
    }

    private static String senderName(ChatMessage message, String ownName, String unknownName) {
        String name = message.isOwn() ? ownName : message.getSenderName();
        return name == null || name.isBlank() ? unknownName : name;
    }
}
