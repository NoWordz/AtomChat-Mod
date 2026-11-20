package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ImageCode;
import com.atom.chat.chat.OwnIdentity;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.text.RichText;
import com.atom.chat.text.RichTextLayout.RichLine;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.chat.ui.ChatShortcutHint;
import com.atom.chat.ui.LayoutTokens;
import com.atom.chat.ui.MessageGeometry;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.types.Rect;
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

    public record Viewport(float x, float bottom, float width, float height, float density, float guiPerUnit) {
        public static Viewport of(Matrix4f pose, int guiWidth, int guiHeight, double guiScale, double chatScale,
                                  int chatWidth) {
            // Forge chat reposition events may translate the pose. Leave other transforms to vanilla.
            if (!(chatScale > 0) || !(guiScale > 0)
                    || (pose.properties() & Matrix4f.PROPERTY_TRANSLATION) == 0) return null;
            float guiPerUnit = (float) chatScale * LayoutTokens.HUD_ICON_GUI / UiTokens.AVATAR_SIZE;
            float x = pose.m30() + VANILLA_LEFT_MARGIN * (float) chatScale;
            float shortcutTop = guiHeight - LayoutTokens.HUD_MARGIN_GUI - LayoutTokens.HUD_ICON_GUI;
            float bottom = Math.min(guiHeight, shortcutTop - LayoutTokens.HUD_MESSAGE_GAP_GUI + pose.m31());
            return new Viewport(x / guiPerUnit, bottom / guiPerUnit, Math.min(guiWidth - x, chatWidth) / guiPerUnit,
                    bottom / guiPerUnit, (float) guiScale * guiPerUnit, guiPerUnit);
        }

        Viewport fit(int limit) {
            float minHeight = minPlayerHeight(FontManager.font(UiTokens.FONT_BODY), MessageGeometry.avatarInset());
            float needed = limit * minHeight + (limit - 1) * UiTokens.LIST_GAP;
            // Keep a stable scale as messages arrive, with a floor for very small windows.
            float scale = Math.max(MIN_FIT_SCALE, Math.min(1F, height / (needed + .1F)));
            return scale >= 1F ? this : new Viewport(x / scale, bottom / scale, width / scale, height / scale,
                    density * scale, guiPerUnit * scale);
        }
    }

    private record Measured(ChatMessage message, List<RichLine> name, List<RichLine> body, Font bodyFont,
                            float lineHeight, float width, float bubbleHeight, float height, float inkWidth,
                            boolean hasItems, ImageCode.Meta image) {}
    private record Placed(Measured measured, float thumbnailWidth, float thumbnailHeight, float height) {}
    private record Ink(AtomChatConfig.BubbleStyle shape, int own, int other, int ownText, int otherText,
                       int caption, int capsule, int capsuleText, float capsuleRadius) {
        Ink(AtomChatConfig config) {
            this(config.effectiveBubbleStyle(), config.ownBubbleColor, config.otherBubbleColor,
                    config.bubbleTextColor, config.otherBubbleTextColor, config.textPrimaryColor,
                    config.secondaryCapsuleBg, config.secondaryCapsuleText,
                    UiTokens.radius(10, AtomChatAppearance.theme()));
        }
    }

    private final IdentityHashMap<ChatMessage, Measured> layouts = new IdentityHashMap<>();
    private final ChatHudRasterCache rasterInk = new ChatHudRasterCache();
    private final List<ChatMessage> recent = new ArrayList<>(AtomChatConfig.CHAT_HUD_MAX_MESSAGES);
    private final List<ChatMessage> arranged = new ArrayList<>(AtomChatConfig.CHAT_HUD_MAX_MESSAGES);
    private final List<ChatHudMotion.Row<Placed>> visibleRows = new ArrayList<>(AtomChatConfig.CHAT_HUD_MAX_MESSAGES);
    private final ChatHudMotion<Placed> motion = new ChatHudMotion<>();
    private final SkiaGraphics graphics = new SkiaGraphics();
    private final BiFunction<String, Boolean, Image> visibleImage;
    private WeakReference<Object> world;
    private Viewport lastViewport;
    private Viewport unfittedViewport;
    private Viewport fittedViewport;
    private int unfittedLimit;
    private Font fittedBodyFont;
    private float fittedAvatarInset, fittedListGap;
    private Font layoutBodyFont, layoutNameFont, layoutCapsuleFont;
    private float layoutWidth;
    private float layoutAvatarInset;
    private float inlineItemGap;
    private String layoutOwnName, layoutUnknownName, layoutImageLabel;
    private int lastLimit;
    private boolean lastAnimationEnabled, lastImagesEnabled;
    private boolean departingRows;
    private Ink lastInk;
    private float lastInkDensity;
    private boolean hasFrame;
    private boolean hasSelection;
    private boolean panelFrame;
    private boolean paintingStarted;
    private boolean failed;

    public ChatHudRenderer() {
        this((url, visible) -> ImageLoader.get().getHudImage(url, visible));
    }

    ChatHudRenderer(BiFunction<String, Boolean, Image> visibleImage) {
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
        visibleRows.clear();
        motion.clear();
        rasterInk.clear();
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
            history.copyRecent(entries, limit, fallback, receivedTick, tick, recent);
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
            Viewport fittedViewport = fittedViewport(viewport, limit);
            var config = AtomChatConfig.get();
            prepareRows(recent, fittedViewport, ownName, unknownName, imageLabel, config, limit, animationEnabled, now);
            if (visibleRows.isEmpty() && !departingRows) return true;
            prepareInk(config, fittedViewport);
            boolean prepare = hasInlineItems();
            flush.run();
            graphics.draw(prepare ? () -> prepareIcons.accept(graphics) : null, fittedViewport.density(), (canvas, ignored) -> {
                paintingStarted = true;
                paintRows(canvas, fittedViewport, ownUuid, ownName, unknownName, opacity, visibility, now);
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
        prepareRows(messages, viewport, ownName, unknownName, "[Image]", config, limit, animationEnabled, now);
        prepareInk(config, viewport);
        paintRows(canvas, viewport, ownUuid, ownName, unknownName, opacity, visibility, now);
    }

    private Viewport fittedViewport(Viewport viewport, int limit) {
        Font font = FontManager.font(UiTokens.FONT_BODY);
        float avatarInset = MessageGeometry.avatarInset();
        if (!viewport.equals(unfittedViewport) || limit != unfittedLimit || font != fittedBodyFont
                || avatarInset != fittedAvatarInset || UiTokens.LIST_GAP != fittedListGap) {
            unfittedViewport = viewport;
            unfittedLimit = limit;
            fittedBodyFont = font;
            fittedAvatarInset = avatarInset;
            fittedListGap = UiTokens.LIST_GAP;
            fittedViewport = viewport.fit(limit);
        }
        return fittedViewport;
    }

    private void prepareRows(List<ChatMessage> messages, Viewport viewport, String ownName, String unknownName,
                             String imageLabel, AtomChatConfig config, int limit, boolean animationEnabled, long now) {
        if (departingRows) departingRows = motion.retire(now);
        if (limit == 0) {
            visibleRows.clear();
            arranged.clear();
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
            rasterInk.clear();
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
        boolean sameGeometry = viewport.equals(lastViewport) && limit == lastLimit && !invalidateLayout
                && lastImagesEnabled == config.imageMessagesEnabled;
        // Keep an in-progress row animation behind the panel's group slide.
        if (panelFrame && sameGeometry && (!visibleRows.isEmpty() || departingRows)) return;
        if (hasFrame && sameGeometry && animationEnabled == lastAnimationEnabled && sameSelection(messages, limit)) return;
        ChatMessage newest = messages.isEmpty() ? null : messages.get(0);
        ChatMessage previous = arranged.isEmpty() ? null : arranged.get(0);
        // Geometry/font changes settle the stack, so departures cannot keep old text resources.
        boolean animate = hasFrame && animationEnabled && limit == lastLimit && (previous == null || sameGeometry);
        motion.begin(now, animate, newest != previous);
        visibleRows.clear();
        float avatarLane = avatarInset + UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP;
        float minPlayerHeight = minPlayerHeight(bodyFont, avatarInset);
        float minSystemHeight = UiTokens.SYSTEM_BUBBLE_PAD_Y + SkiaFontRenderer.getHeight(capsuleFont);
        float bottom = viewport.bottom();
        float top = Math.max(0F, bottom - viewport.height());
        int count = 0;
        float minimumPile = -UiTokens.LIST_GAP;
        while (count < messages.size() && count < limit) {
            float minHeight = messages.get(count).isSystem() ? minSystemHeight : minPlayerHeight;
            float next = minimumPile + minHeight + UiTokens.LIST_GAP;
            if (next > bottom - top) break;
            minimumPile = next;
            count++;
        }
        for (int i = 0; i < count; i++) {
            ChatMessage message = messages.get(i);
            boolean system = message.isSystem();
            float maxBubbleWidth = viewport.width() - (system ? 0F : avatarLane);
            if (maxBubbleWidth <= 0F) break;
            float padY = system ? UiTokens.SYSTEM_BUBBLE_PAD_Y : UiTokens.BUBBLE_PAD_Y;
            float band = system ? 0F : UiTokens.NAME_BAND;
            minimumPile -= (system ? minSystemHeight : minPlayerHeight) + UiTokens.LIST_GAP;
            float available = bottom - top - Math.max(0F, minimumPile) + .01F;
            if (i + 1 < count) available -= UiTokens.LIST_GAP;
            Measured row = layout(message, system ? capsuleFont : bodyFont, nameFont,
                    ownName, unknownName, imageLabel, padY, band, minPlayerHeight, maxBubbleWidth);
            float thumbnailWidth = 0F;
            float thumbnailHeight = 0F;
            if (row.image() != null && config.imageMessagesEnabled) {
                thumbnailWidth = Math.min(UiTokens.IMAGE_MAX_W, maxBubbleWidth);
                thumbnailHeight = Math.max(1F, Math.min(2F * minPlayerHeight, available) - band);
                if (row.image().width() > 0 && row.image().height() > 0) {
                    thumbnailHeight = Math.min(thumbnailHeight, thumbnailWidth * row.image().height() / row.image().width());
                }
            }
            float rowHeight = Math.max(row.height(), band + thumbnailHeight);
            float rowTop = bottom - rowHeight;
            float entryX = system ? 0F : message.isOwn() ? UiTokens.MESSAGE_SLIDE : -UiTokens.MESSAGE_SLIDE;
            visibleRows.add(motion.place(message, new Placed(row, thumbnailWidth, thumbnailHeight, rowHeight), rowTop, entryX));
            bottom = rowTop - UiTokens.LIST_GAP;
        }
        motion.finish();
        departingRows = motion.retire(now);
        arranged.clear();
        for (int i = 0; i < messages.size() && i < limit; i++) arranged.add(messages.get(i));
        lastViewport = viewport;
        lastLimit = limit;
        lastImagesEnabled = config.imageMessagesEnabled;
        lastAnimationEnabled = animationEnabled;
        hasFrame = true;
    }

    private boolean sameSelection(List<ChatMessage> messages, int limit) {
        if (arranged.size() != Math.min(messages.size(), limit)) return false;
        for (int i = 0; i < arranged.size(); i++) {
            if (arranged.get(i) != messages.get(i)) return false;
        }
        return true;
    }

    private static float minPlayerHeight(Font bodyFont, float avatarInset) {
        return Math.max(avatarInset * 2F + UiTokens.AVATAR_SIZE,
                UiTokens.NAME_BAND + UiTokens.BUBBLE_PAD_Y + SkiaFontRenderer.getHeight(bodyFont));
    }

    private Measured layout(ChatMessage message, Font font, Font nameFont, String ownName, String unknownName,
                            String imageLabel, float padY, float band, float minPlayerHeight, float maxBubbleWidth) {
        Measured cached = layouts.get(message);
        if (cached != null) return cached;
        RichText body = MessagePaint.body(message);
        String content = message.getContentRich().getString();
        ImageCode.Meta image = null;
        if (message.isSystem()) {
            var ranges = ImageCode.ranges(content);
            for (int i = ranges.size() - 1; i >= 0; i--) {
                int[] range = ranges.get(i);
                body = body.splice(range[0], range[1], RichText.literal(imageLabel));
            }
        } else if (ImageCode.contains(content)) {
            // A quoted image belongs to the omitted quote, never to this row's body.
            image = ImageCode.parse(content);
            body = RichText.literal(imageLabel);
        }
        RichText name = message.getSenderRich().stripInteractions();
        if (!message.isSystem() && name.isEmpty()) {
            name = message.isOwn() ? OwnIdentity.displayNameRich() : RichText.literal(senderName(message, ownName, unknownName));
        }
        float textWidth = Math.max(1F, maxBubbleWidth - 2F * UiTokens.BUBBLE_PAD);
        List<RichLine> lines = oneLine(body, font, textWidth);
        List<RichLine> caption = oneLine(name, nameFont, textWidth);
        float bodyWidth = lines.isEmpty() ? 0F : RichTextRenderer.width(font, lines.get(0));
        float nameWidth = caption.isEmpty() ? 0F : RichTextRenderer.width(nameFont, caption.get(0));
        float bubbleWidth = Math.min(maxBubbleWidth, Math.max(UiTokens.BUBBLE_MIN_W, bodyWidth + 2F * UiTokens.BUBBLE_PAD));
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float bubbleHeight = lineHeight + padY;
        float height = message.isSystem() ? bubbleHeight : Math.max(minPlayerHeight, band + bubbleHeight);
        boolean hasItems = !lines.isEmpty() && lines.get(0).parts().stream().anyMatch(part -> part.isItem());
        var result = new Measured(message, caption, lines, font, lineHeight, bubbleWidth, bubbleHeight,
                height, Math.max(nameWidth, bubbleWidth), hasItems, image);
        if (layouts.size() >= MAX_LAYOUTS) layouts.clear();
        layouts.put(message, result);
        return result;
    }

    private void prepareInk(AtomChatConfig config, Viewport viewport) {
        var ink = new Ink(config);
        if (!ink.equals(lastInk) || lastInkDensity != viewport.density()) {
            rasterInk.clear();
            lastInk = ink;
            lastInkDensity = viewport.density();
        }
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

    private void paintRows(Canvas canvas, Viewport viewport, UUID ownUuid, String ownName, String unknownName,
                           int opacity, float visibility, long now) {
        if (visibility <= 0F || opacity <= 0 || visibleRows.isEmpty() && !departingRows) return;
        float top = Math.max(0F, viewport.bottom() - viewport.height());
        Rect clip = Rect.makeLTRB(0F, top, viewport.x() + viewport.width() + UiTokens.MESSAGE_SLIDE, viewport.bottom());
        int save = canvas.save();
        try {
            canvas.clipRect(clip);
            if (visibility < 1F) {
                var group = ChatShortcutHint.frame(visibility);
                opacity = Math.round(opacity * group.alpha());
                if (opacity <= 0) return;
                canvas.saveLayerAlpha(clip, opacity);
                canvas.translate(group.offsetX() / viewport.guiPerUnit(), 0F);
            } else if (opacity < 255) {
                canvas.saveLayerAlpha(clip, opacity);
            }
            if (departingRows) {
                for (var row : motion.rows()) {
                    if (row.departing()) drawRow(canvas, row, viewport, ownUuid, ownName, unknownName, false, now);
                }
            }
            // Keep the new row in front if its entrance briefly intersects the moving stack.
            for (int i = visibleRows.size() - 1; i >= 0; i--) {
                drawRow(canvas, visibleRows.get(i), viewport, ownUuid, ownName, unknownName, visibility >= 1F, now);
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private void drawRow(Canvas canvas, ChatHudMotion.Row<Placed> row, Viewport viewport, UUID ownUuid,
                         String ownName, String unknownName, boolean cacheInk, long now) {
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
        if (fading) canvas.saveLayerAlpha(Rect.makeXYWH(x - 2F, y - 2F, viewport.width() + 4F, placed.height() + 4F), alpha);
        try {
            if (!message.isSystem()) {
                boolean own = message.isOwn();
                UUID uuid = own ? ownUuid : message.getSenderUuid();
                String profile = own ? ownName : message.getProfileName();
                if (profile == null || profile.isBlank()) profile = senderName(message, ownName, unknownName);
                MessagePaint.avatar(canvas, message, uuid, profile, x + avatarInset, y + avatarInset);
            }
            Image image = placed.thumbnailHeight() > 0F ? visibleImage.apply(measured.image().url(), !row.departing()) : null;
            if (image != null) {
                drawCaption(canvas, measured, bubbleX, y);
                float scale = Math.min(placed.thumbnailWidth() / image.getWidth(), placed.thumbnailHeight() / image.getHeight());
                ChatBubble.drawImage(canvas, image, bubbleX, y + UiTokens.NAME_BAND,
                        image.getWidth() * scale, image.getHeight() * scale, lastInk.shape(), false);
            } else if (!cacheInk || fading || measured.hasItems() || !drawStaticInk(canvas, measured, bubbleX, y, viewport.density())) {
                drawInk(canvas, measured, bubbleX, y);
            }
        } finally {
            if (fading) canvas.restore();
        }
    }

    private boolean drawStaticInk(Canvas canvas, Measured measured, float x, float y, float density) {
        Image image = rasterInk.get(measured);
        if (image == null) {
            image = rasterInk.bake(measured, measured.inkWidth(), measured.height(), density,
                    (target, inset) -> drawInk(target, measured, inset, inset));
        }
        if (image == null) return false;
        rasterInk.draw(canvas, image, x, y, density);
        return true;
    }

    private void drawCaption(Canvas canvas, Measured measured, float x, float y) {
        RichTextRenderer.drawLines(canvas, layoutNameFont, measured.name(), x, y + UiTokens.NAME_BAND / 2F,
                SkiaFontRenderer.getHeight(layoutNameFont), lastInk.caption(), null, false, TextSurface.NAME_BAND.shadowed());
    }

    private void drawInk(Canvas canvas, Measured measured, float x, float y) {
        float height = measured.bubbleHeight();
        if (measured.message().isSystem()) {
            SkiaDraw.drawRoundedRect(canvas, x, y, measured.width(), height, lastInk.capsuleRadius(), lastInk.capsule());
            RichTextRenderer.drawLines(canvas, measured.bodyFont(), measured.body(), x + UiTokens.BUBBLE_PAD,
                    y + height / 2F, measured.lineHeight(), lastInk.capsuleText(), null, false,
                    TextSurface.CAPSULE.shadowed(), lastInk.capsule());
        } else {
            boolean own = measured.message().isOwn();
            drawCaption(canvas, measured, x, y);
            float bubbleY = y + UiTokens.NAME_BAND;
            ChatBubble.draw(canvas, x, bubbleY, measured.width(), height, lastInk.shape(), false,
                    own ? lastInk.own() : lastInk.other());
            RichTextRenderer.drawLines(canvas, measured.bodyFont(), measured.body(), x + UiTokens.BUBBLE_PAD,
                    bubbleY + height / 2F, measured.lineHeight(), own ? lastInk.ownText() : lastInk.otherText(),
                    null, false, TextSurface.BUBBLE.shadowed());
        }
    }

    static List<RichLine> oneLine(RichText text, Font font, float width) {
        String content = text.getString();
        int newline = content.indexOf('\n');
        int carriageReturn = content.indexOf('\r');
        if (carriageReturn >= 0 && (newline == -1 || carriageReturn < newline)) newline = carriageReturn;
        if (newline >= 0) {
            text = RichText.concat(text.slice(0, newline), RichText.literal("…"));
        }
        return RichTextRenderer.wrapFor(RichTextRenderer.truncate(font, text, width), font, Float.MAX_VALUE);
    }

    private static String senderName(ChatMessage message, String ownName, String unknownName) {
        String name = message.isOwn() ? ownName : message.getSenderName();
        return name == null || name.isBlank() ? unknownName : name;
    }
}
