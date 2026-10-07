package com.atom.chat.page;

import com.atom.chat.AtomChat;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.Cicodes;
import com.atom.chat.chat.ImageCode;
import com.atom.chat.chat.MentionHighlighter;
import com.atom.chat.chat.MessageGrouping;
import com.atom.chat.chat.OwnIdentity;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.image.PlayerAvatar;
import com.atom.chat.render.ClickableSpan;
import com.atom.chat.render.Easing;
import com.atom.chat.render.RichTextRenderer;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.text.RichText;
import com.atom.chat.text.RichTextLayout.RichLine;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiSpring;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.text.Text;

/**
 * Message list presentation, split out of AtomChatScreen: rendering, entrance
 * animation, text selection and hit geometry for one conversation. The screen
 * owns the navigation-level scroll controllers and all input-side interaction
 * state; anything the view needs from the screen arrives through {@link Host}.
 */
public final class MessageListView {

    /** Screen-provided answers the view needs while drawing. */
    public interface Host {
        /** Local player uuid: own messages draw the local player's avatar face. */
        UUID ownUuid();

        /** Local player name: own messages fall back to it when the profile name is blank. */
        String ownName();

        /** Display name for a message row (own vs. other resolution stays on the screen). */
        String senderName(ChatMessage message);

        /** Screen-open timestamp: the entrance animation baseline. */
        long openStart();
    }

    public record MessageTextLine(ChatMessage message, int line, String text, float x, float y, float height) {
    }

    public record MessageHit(ChatMessage message, int index, float x, float y, float maxWidth, float bottom,
                             float avatarX, float avatarY, float avatarSize, float bubbleY, float bubbleX,
                             float bubbleWidth, float bubbleBottom) {
    }

    private static final long MESSAGE_ANIM_MS = UiMotion.MESSAGE_MS;
    private static final long ENTRANCE_SETTLE_GUARD_MS = 5000L;

    private final Host host;

    private final List<MessageHit> hits = new ArrayList<>();
    private final List<ClickableSpan> clickableSpans = new ArrayList<>();

    /**
     * Wrapped content lines keyed by (message id, wrap width). Measure, draw
     * and drag otherwise re-wrap the whole history every frame. A wrap depends
     * only on the message's immutable content and the available width, so the
     * result is reusable across all three paths; merge copies share the id and
     * never change the text, so entries stay valid until evicted (LRU).
     */
    private static final int LAYOUT_CACHE_MAX = 512;
    private final Map<Long, List<RichLine>> layoutCache =
            new LinkedHashMap<>(128, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, List<RichLine>> eldest) {
                    return size() > LAYOUT_CACHE_MAX;
                }
            };

    /**
     * Bubble classification keyed by (message id, image-receive switch). The
     * kind runs ImageCode's regex over the raw text, and measure + draw used
     * to re-run it for every message every frame. Content is immutable per id
     * (merge copies share the id), so only the config bit can change the
     * answer; it is part of the key, so a settings toggle reclassifies on the
     * next frame. Same LRU shape as layoutCache.
     */
    private static final int KIND_CACHE_MAX = 512;
    private final Map<Long, BubbleKind> kindCache =
            new LinkedHashMap<>(128, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, BubbleKind> eldest) {
                    return size() > KIND_CACHE_MAX;
                }
            };

    /** Cached {@link #kindOf}; see {@link #kindCache}. */
    private BubbleKind kindOfCached(ChatMessage msg) {
        long key = msg.getId() * 2L + (AtomChatConfig.get().imageMessagesEnabled ? 1L : 0L);
        BubbleKind cached = kindCache.get(key);
        if (cached != null) {
            return cached;
        }
        BubbleKind kind = kindOf(msg);
        kindCache.put(key, kind);
        return kind;
    }

    private ChatMessage selectionAnchorMessage;
    private ChatMessage selectionFocusMessage;
    private int selectionAnchorLine = -1;
    private int selectionAnchorChar = -1;
    private int selectionFocusLine = -1;
    private int selectionFocusChar = -1;
    private boolean selecting;
    private boolean selectionMoved;
    /** Messages list from the most recent draw; used to order cross-message ranges. */
    private List<ChatMessage> currentMessages = List.of();

    private final Map<ChatMessage, Long> messageEnterStart = new HashMap<>();
    private final Set<ChatMessage> messageEnterSettled = new HashSet<>();
    private long lastEntrancePrune;

    /** Fade duration of the wash left on a message after a banner jump. */
    private static final long HIGHLIGHT_MS = 1200L;

    private int pokeIndex = -1;
    private long pokeStartTime;

    /** Message highlighted by a notification jump, and when it stops. */
    private ChatMessage highlightMessage;
    private long lastFrameMs = System.currentTimeMillis();
    private long highlightUntil;

    public MessageListView(Host host) {
        this.host = host;
    }

    // ------------------------------------------------------------------ public api

    /**
     * Arms (or clears) the jump-to-message highlight: a white wash that fades
     * out over {@link #HIGHLIGHT_MS}, so a banner click visibly lands on the
     * message it pointed at.
     */
    public void highlight(ChatMessage message) {
        highlightMessage = message;
        highlightUntil = message == null ? 0L : System.currentTimeMillis() + HIGHLIGHT_MS;
    }


    /**
     * Content-space Y offset of a message inside the list. Mirrors the cursor
     * arithmetic of {@link #draw} exactly (time dividers, per-message heights,
     * grouped gaps) so a jump can place any message in the viewport without
     * re-measuring by hand.
     */
    public float offsetOf(List<ChatMessage> messages, int index, float width) {
        float cursor = 0.0F;
        int limit = Math.min(index, messages.size());
        for (int i = 0; i < limit; i++) {
            if (dividerBefore(messages, i)) {
                cursor += TIME_DIVIDER_H + UiTokens.LIST_GAP;
            }
            cursor += messageHeight(messages.get(i), width, isCompactGrouped(messages, i));
            cursor += isCompactGrouped(messages, i + 1)
                    ? MessageGrouping.groupedGap(UiTokens.LIST_GAP, UiTokens.s(2))
                    : UiTokens.LIST_GAP;
        }
        if (limit < messages.size() && dividerBefore(messages, limit)) {
            cursor += TIME_DIVIDER_H + UiTokens.LIST_GAP;
        }
        return cursor;
    }

    /** White wash painted under a message while its jump highlight is armed. */
    private void drawJumpHighlight(Canvas canvas, ChatMessage msg, float x, float y, float w, float h) {
        if (!msg.sameAs(highlightMessage)) {
            return;
        }
        long left = highlightUntil - System.currentTimeMillis();
        if (left <= 0L) {
            highlightMessage = null;
            return;
        }
        float t = Math.min(1.0F, left / (float) HIGHLIGHT_MS);
        SkiaDraw.drawRoundedRect(canvas, x, y - UiTokens.s(2), w, h + UiTokens.s(4),
                UiTokens.s(8), Color.makeARGB((int) (60.0F * t), 255, 255, 255));
    }

    public void draw(Canvas canvas, float x, float y, float width, float height,
                     List<ChatMessage> messages, ScrollController scroll) {
        hits.clear();
        clickableSpans.clear();
        currentMessages = messages;
        // Drop an expired jump highlight even when its message left the list;
        // the per-message draw path would otherwise never reach the expiry check.
        if (highlightMessage != null && System.currentTimeMillis() > highlightUntil) {
            highlightMessage = null;
        }
        // Snapshot "was at bottom" before maxScroll grows: after new messages
        // arrive the old target is no longer near the new max, so comparing after
        // recompute would make us miss the follow and leave a growing gap.
        boolean wasAtBottom = scroll.isAtBottom();
        boolean viewportChanged = scroll.viewportChanged(height);
        scroll.setContent(measureContentHeight(messages, width), height);
        if (wasAtBottom) {
            if (viewportChanged) {
                // The list is shrinking/growing in lockstep with the animated
                // input bar. Keep the bottom pinned directly: chasing the moving
                // maxScroll with an eased scroll restarts every frame and visibly
                // lags behind the bar, which is why growing felt desynced while
                // shrinking (a plain clamp) felt fine.
                scroll.scrollToBottom(false);
            } else {
                scroll.stickToBottom();
            }
        }
        scroll.updateAnimation(System.currentTimeMillis());
        canvas.save();
        try {
            SkiaDraw.clip(canvas, x, y, width, height, 0.0F);
            canvas.translate(0.0F, -scroll.getScrollY());
            long now = System.currentTimeMillis();
            pruneEntranceSettled(now);
            float dtMs = Math.min(50.0F, Math.max(1.0F, now - lastFrameMs));
            lastFrameMs = now;
            float cursorY = y;
            for (int mi = 0; mi < messages.size(); mi++) {
                ChatMessage msg = messages.get(mi);
                boolean grouped = isCompactGrouped(messages, mi);
                if (dividerBefore(messages, mi)) {
                    // Clock pill between messages. Drawn only when its own
                    // message is in the extended viewport, so occluded
                    // dividers cost nothing.
                    float divOffset = cursorY - y;
                    if (divOffset <= scroll.getScrollY() + height + 80.0F
                            && divOffset + TIME_DIVIDER_H >= scroll.getScrollY() - 80.0F) {
                        drawTimeDivider(canvas, msg.getTimestamp(), x, width, cursorY);
                    }
                    cursorY += TIME_DIVIDER_H + UiTokens.LIST_GAP;
                }
                float h = messageHeight(msg, width, grouped);
                float offset = cursorY - y;
                if (offset > scroll.getScrollY() + height + 80.0F) {
                    break;
                }
                if (offset + h >= scroll.getScrollY() - 80.0F) {
                    float t = entranceProgress(msg, now);
                    boolean layered = t < 1.0F;
                    if (!layered) {
                        messageEnterSettled.add(msg);
                    }
                    canvas.save();
                    if (layered) {
                        // QQ-style entrance: own bubbles come in from the right
                        // (toward the left), other bubbles from the left. The
                        // layer rectangle must cover the full travel so a sliding
                        // bubble is never clipped by its own offscreen layer.
                        float travel = UiTokens.MESSAGE_SLIDE;
                        // Two curves, one timeline: the slide decelerates with
                        // a small back-ease overshoot (<=5%, UiSpring.messageEase)
                        // while the fade ramps gently across the whole entrance
                        // (quad), so the opacity change is still happening while
                        // the bubble is still moving. The fade must never
                        // overshoot; only the spatial half gets the spring feel.
                        float fade = Easing.easeOutQuad(t);
                        float move = UiSpring.messageEase(t);
                        // System capsules are centered and have no sender side;
                        // they fade in place rather than pretending to be someone's
                        // bubble.
                        float dx = msg.isSystem() ? 0.0F
                                : msg.isOwn() ? (1.0F - move) * travel
                                : -(1.0F - move) * travel;
                        try (Paint layer = new Paint()) {
                            layer.setColor(Color.makeARGB((int) (255.0F * fade), 0, 0, 0));
                            canvas.saveLayer(Rect.makeXYWH(x - travel - 4.0F, cursorY - 4.0F,
                                    width + travel * 2.0F + 8.0F, h + 28.0F), layer);
                            canvas.translate(dx, 0.0F);
                        }
                    }
                    drawJumpHighlight(canvas, msg, x, cursorY, width, h);
                    int spanStart = clickableSpans.size();
                    MessageHit hit = drawMessage(canvas, msg, x, cursorY, width, hits.size(), grouped);
                    // Clickable spans are recorded in content space (like hits
                    // before conversion); convert them to screen space so later
                    // hit-testing can compare them directly against the mouse.
                    for (int i = spanStart; i < clickableSpans.size(); i++) {
                        ClickableSpan s = clickableSpans.get(i);
                        clickableSpans.set(i, new ClickableSpan(s.x(), s.y() - scroll.getScrollY(), s.w(), s.h(), s.style()));
                    }
                    if (layered) {
                        canvas.restore();
                    }
                    canvas.restore();
                    // Hits are hit-tested in screen space; drawing happens in content space.
                    hits.add(new MessageHit(hit.message(), hit.index(), hit.x(), hit.y() - scroll.getScrollY(), hit.maxWidth(),
                            hit.bottom() - scroll.getScrollY(), hit.avatarX(), hit.avatarY() - scroll.getScrollY(), hit.avatarSize(),
                            hit.bubbleY() - scroll.getScrollY(), hit.bubbleX(), hit.bubbleWidth(), hit.bubbleBottom() - scroll.getScrollY()));
                } else {
                    // Left the viewport: drop the start timestamp only. The
                    // settled marker is deliberately kept so scrolling back up
                    // through history never replays an entrance; the set is
                    // bounded by pruneEntranceSettled's time guard.
                    messageEnterStart.remove(msg);
                }
                boolean nextGrouped = isCompactGrouped(messages, mi + 1);
                float gap = nextGrouped
                        ? MessageGrouping.groupedGap(UiTokens.LIST_GAP, UiTokens.s(2))
                        : UiTokens.LIST_GAP;
                cursorY += h + gap;
            }
        } finally {
            canvas.restore();
        }
    }

    /** Cached wrap; see {@link #layoutCache}. */
    private List<RichLine> wrappedLines(ChatMessage msg, Font font, float wrapWidth) {
        int w = Math.max(0, Math.round(wrapWidth));
        long key = msg.getId() * 100_000L + Math.min(w, 99_999);
        List<RichLine> cached = layoutCache.get(key);
        if (cached != null) {
            return cached;
        }
        RichText content = msg.getContentRich();
        if (!msg.isSystem()) {
            String own = OwnIdentity.bareName();
            if (own != null && !own.isBlank()) {
                content = MentionHighlighter.highlightLocal(content, own,
                        OwnIdentity.displayNameRich());
            }
        }
        List<RichLine> lines = RichTextRenderer.wrapFor(content, font, wrapWidth);
        layoutCache.put(key, lines);
        return lines;
    }

    /** Hit geometry from the most recent {@link #draw}; valid for the same frame. */
    public List<MessageHit> hits() {
        return hits;
    }

    public boolean hasSelection() {
        if (selectionAnchorMessage == null || selectionFocusMessage == null
                || selectionAnchorLine < 0 || selectionFocusLine < 0) {
            return false;
        }
        if (!selectionAnchorMessage.sameAs(selectionFocusMessage)) {
            return true;
        }
        return selectionAnchorLine != selectionFocusLine || selectionAnchorChar != selectionFocusChar;
    }

    public boolean isSelecting() {
        return selecting;
    }

    public void clearSelection() {
        selectionAnchorMessage = null;
        selectionFocusMessage = null;
        selectionAnchorLine = -1;
        selectionAnchorChar = -1;
        selectionFocusLine = -1;
        selectionFocusChar = -1;
        selecting = false;
        selectionMoved = false;
    }

    /**
     * Arms a text selection on the message line under the pointer. The screen
     * keeps its own pending-click bookkeeping (span + moved flag) and calls
     * this after it.
     */
    public void beginSelection(MessageHit hit, MessageTextLine line, float mx) {
        selectionAnchorMessage = hit.message();
        selectionFocusMessage = hit.message();
        selectionAnchorLine = selectionFocusLine = line.line();
        selectionAnchorChar = selectionFocusChar = charAtLine(line, mx);
        selecting = true;
        selectionMoved = false;
    }

    /**
     * Extends the active selection to the pointer, across message boundaries.
     * Returns whether the drag was consumed; the screen translates consumption
     * into its own pending-click suppression rules.
     */
    public boolean dragSelection(float mx, float my) {
        if (!selecting || selectionAnchorMessage == null) {
            return false;
        }
        for (MessageHit hit : hits) {
            if (my < hit.y() || my > hit.bottom()) {
                continue;
            }
            for (MessageTextLine line : textLinesForHit(hit)) {
                float lineRight = line.x() + SkiaFontRenderer.getStringWidth(
                        FontManager.font(line.message().isSystem() ? UiTokens.FONT_QUOTE : UiTokens.FONT_BODY),
                        line.text());
                if (mx >= line.x() && mx <= lineRight && my >= line.y() && my <= line.y() + line.height()) {
                    int ch = charAtLine(line, mx);
                    boolean changed = !line.message().sameAs(selectionFocusMessage)
                            || ch != selectionFocusChar || line.line() != selectionFocusLine;
                    if (changed) {
                        selectionFocusMessage = line.message();
                        selectionFocusLine = line.line();
                        selectionFocusChar = ch;
                        selectionMoved = true;
                    }
                    return true;
                }
            }
        }
        // A drag that leaves the text is still a drag, so it must suppress
        // any click captured on mouse press even when no selection changed.
        return true; // drag outside text keeps current selection active
    }

    /**
     * Finishes a selection drag: the selection survives when it actually moved,
     * otherwise it collapses (a clean click on text never leaves a highlight).
     */
    public void endSelection() {
        selecting = false;
        if (!selectionMoved) {
            clearSelection();
        }
    }

    /**
     * Copies the selected text, joining messages with a newline. The range may
     * span any number of messages in the order they appear in the current feed.
     *
     * <p>When an endpoint has scrolled out of the drawn window (wheel during a
     * drag) the hit rows no longer cover it; the copy then falls back to whole
     * messages from the feed instead of silently returning nothing.
     */
    public String copySelection() {
        if (!hasSelection()) {
            return "";
        }
        int anchorHit = hitIndexFor(selectionAnchorMessage);
        int focusHit = hitIndexFor(selectionFocusMessage);
        if (anchorHit >= 0 && focusHit >= 0) {
            String exact = copyRangeFromHits(anchorHit, focusHit);
            if (!exact.isEmpty()) {
                return exact;
            }
        }
        return copyRangeFromFeed();
    }

    /** Exact copy with per-line cuts, requires both endpoints to be drawn. */
    private String copyRangeFromHits(int anchorHit, int focusHit) {
        // Normalise to a start/end pair in display order.
        int startHit = anchorHit;
        int endHit = focusHit;
        boolean reverse = anchorHit > focusHit;
        if (!reverse && anchorHit == focusHit) {
            reverse = selectionAnchorLine > selectionFocusLine
                    || (selectionAnchorLine == selectionFocusLine
                    && selectionAnchorChar > selectionFocusChar);
        }
        if (reverse) {
            startHit = focusHit;
            endHit = anchorHit;
        }
        ChatMessage startMsg = reverse ? selectionFocusMessage : selectionAnchorMessage;
        ChatMessage endMsg = reverse ? selectionAnchorMessage : selectionFocusMessage;
        int startLine = reverse ? selectionFocusLine : selectionAnchorLine;
        // Start and end must take opposite operands of reverse, otherwise they
        // collapse onto one value and every cut on that line looks empty.
        int startChar = reverse ? selectionFocusChar : selectionAnchorChar;
        int endLine = reverse ? selectionAnchorLine : selectionFocusLine;
        int endChar = reverse ? selectionAnchorChar : selectionFocusChar;

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int h = startHit; h <= endHit; h++) {
            MessageHit hit = hits.get(h);
            List<MessageTextLine> lines = textLinesForHit(hit);
            if (lines.isEmpty()) {
                continue;
            }
            boolean isStart = hit.message().sameAs(startMsg);
            boolean isEnd = hit.message().sameAs(endMsg);
            for (MessageTextLine line : lines) {
                int local = line.line();
                if (isStart && local < startLine) {
                    continue;
                }
                if (isEnd && local > endLine) {
                    continue;
                }
                String text = line.text();
                int from = 0;
                int to = text.length();
                if (isStart && local == startLine) {
                    from = startChar;
                }
                if (isEnd && local == endLine) {
                    to = endChar;
                }
                from = Math.max(0, Math.min(from, text.length()));
                to = Math.max(0, Math.min(to, text.length()));
                if (from >= to) {
                    continue;
                }
                if (!first) {
                    sb.append('\n');
                }
                first = false;
                sb.append(text, from, to);
            }
        }
        return sb.toString();
    }

    /**
     * Geometry-free fallback: whole messages between the two endpoints, ordered
     * by the feed. Per-line cuts are lost, but a selection whose end scrolled
     * out of the drawn window still copies its full content.
     */
    private String copyRangeFromFeed() {
        int ai = indexOfIdentity(currentMessages, selectionAnchorMessage);
        int fi = indexOfIdentity(currentMessages, selectionFocusMessage);
        if (ai < 0 || fi < 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = Math.min(ai, fi); i <= Math.max(ai, fi); i++) {
            String text = currentMessages.get(i).getDisplayText();
            if (text == null || text.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(text);
        }
        return sb.toString();
    }

    /** Index of the hit row for a message, or -1 when it is not currently drawn. */
    private int hitIndexFor(ChatMessage message) {
        for (int i = 0; i < hits.size(); i++) {
            if (hits.get(i).message().sameAs(message)) {
                return i;
            }
        }
        return -1;
    }

    /** Arms the avatar poke wobble for one message (double-click side effect). */
    public void poke(int index, long nowMs) {
        pokeIndex = index;
        pokeStartTime = nowMs;
    }

    public Optional<ClickableSpan> clickableSpanAt(float mx, float my) {
        return Optional.ofNullable(findClickableSpan(mx, my));
    }

    /** Drops entrance-animation bookkeeping; called when the screen is removed. */
    public void dispose() {
        messageEnterStart.clear();
        messageEnterSettled.clear();
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the message avatar with the poke wobble armed when this message was
     * just double-clicked: QQ-style rocking around the avatar centre (damped
     * ±14° over two and a half oscillations in ~600ms) rather than a side-to-side
     * slide. Decorative, so with motion off the poke never arms and this reduces
     * to a plain draw.
     */
    private void drawAvatarWithPoke(Canvas canvas, ChatMessage msg, int index, float avatarX, float avatarY) {
        if (pokeIndex == index && pokeStartTime > 0 && Animations.enabled()) {
            long elapsed = System.currentTimeMillis() - pokeStartTime;
            if (elapsed < 600) {
                float t = elapsed / 600.0F;
                float angle = (float) Math.sin(t * Math.PI * 5.0) * 14.0F * (1.0F - t);
                float cx = avatarX + UiTokens.AVATAR_SIZE / 2.0F;
                float cy = avatarY + UiTokens.AVATAR_SIZE / 2.0F;
                canvas.save();
                canvas.translate(cx, cy);
                canvas.rotate(angle);
                canvas.translate(-cx, -cy);
                try {
                    drawAvatar(canvas, msg, avatarX, avatarY);
                } finally {
                    canvas.restore();
                }
                return;
            }
            pokeIndex = -1;
        }
        drawAvatar(canvas, msg, avatarX, avatarY);
    }

    /**
     * Circular avatar from the player's real skin face (face + hat layer sampled
     * from the 64x64 skin). The face image is an opaque square; the circle is
     * produced by drawRoundedImage's clip only, so there is exactly one rounded
     * edge (no CPU mask + clip double edge, and no placeholder bleeding through
     * the avatar). Falls back to a flat gray circle while the skin is missing.
     */
    private void drawAvatar(Canvas canvas, ChatMessage msg, float avatarX, float avatarY) {
        UUID uuid = msg.isOwn() ? host.ownUuid() : msg.getSenderUuid();
        String name = msg.isOwn() ? host.ownName() : msg.getProfileName();
        if (name == null || name.isBlank()) {
            name = host.senderName(msg);
        }
        Image face = PlayerAvatar.face(uuid, name);
        if (face != null) {
            SkiaDraw.drawRoundedImage(canvas, face, avatarX, avatarY, UiTokens.AVATAR_SIZE, UiTokens.AVATAR_SIZE,
                    UiTokens.AVATAR_SIZE / 2.0F, SamplingMode.LINEAR);
        } else {
            SkiaDraw.drawRoundedRect(canvas, avatarX, avatarY, UiTokens.AVATAR_SIZE, UiTokens.AVATAR_SIZE,
                    UiTokens.AVATAR_SIZE / 2.0F, Color.makeARGB(255, 120, 130, 145));
        }
        // Hairline rim hugging the avatar's outer edge (face or placeholder):
        // polarity-adaptive UiTokens.rim separates the circle from whatever
        // bubble or panel sits behind it. Same ring language as the swatches.
        SkiaDraw.drawRing(canvas, avatarX + UiTokens.AVATAR_SIZE / 2.0F, avatarY + UiTokens.AVATAR_SIZE / 2.0F,
                UiTokens.AVATAR_SIZE / 2.0F + UiTokens.s(0.75F), UiTokens.s(1.0F), UiTokens.rim());
    }

    /** Clock pill height between messages (see {@link #dividerBefore}). */
    private static final float TIME_DIVIDER_H = UiTokens.s(24);

    /**
     * A time divider is drawn above a message when {@code
     * timestampIntervalMinutes} have passed since the previous one. The first
     * message of the list always carries one (e33chat behaviour); 0 disables
     * timestamps entirely.
     */
    /** Whether the message at {@code index} continues a compact same-sender group. */
    private static boolean isCompactGrouped(List<ChatMessage> messages, int index) {
        if (!AtomChatConfig.get().compactMessagesEnabled || index <= 0 || index >= messages.size()) {
            return false;
        }
        if (dividerBefore(messages, index)) {
            return false;
        }
        return MessageGrouping.isSameGroup(messages.get(index - 1), messages.get(index));
    }

    private static boolean dividerBefore(List<ChatMessage> messages, int index) {
        int minutes = AtomChatConfig.get().timestampIntervalMinutes;
        if (minutes <= 0) {
            return false;
        }
        if (index <= 0 || index >= messages.size()) {
            return index == 0;
        }
        return messages.get(index).getTimestamp() - messages.get(index - 1).getTimestamp()
                >= minutes * 60_000L;
    }

    /** The clock pill itself: same capsule family as system messages. */
    private void drawTimeDivider(Canvas canvas, long timestamp, float x, float width, float y) {
        Font font = FontManager.font(UiTokens.FONT_QUOTE);
        String time = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(timestamp));
        float textW = SkiaFontRenderer.getStringWidth(font, time);
        float pillW = textW + s(20);
        float pillH = TIME_DIVIDER_H - s(6);
        float pillX = x + (width - pillW) / 2.0F;
        float pillY = y + s(3);
        SkiaDraw.drawRoundedRect(canvas, pillX, pillY, pillW, pillH, UiTokens.radius(10),
                secondaryCapsuleBg());
        SkiaFontRenderer.drawTextCentered(canvas, font, time, x + width / 2.0F, pillY + pillH / 2.0F,
                secondaryCapsuleText());
    }

    /**
     * Raw 0..1 progress of a message's entrance. Messages that existed before
     * this screen was opened are already settled; messages arriving while the
     * screen is open start their animation on the first frame they are actually
     * drawn inside the viewport.
     *
     * <p>Linear on purpose: the caller picks the curve. The fade and the slide
     * must not share one — easeOutCubic covers ~88% of its distance in the
     * first half of the duration, which feels right for a slide but spends the
     * opacity ramp in ~70ms, far too fast to read as a fade.
     */
    private float entranceProgress(ChatMessage msg, long now) {
        if (!Animations.messageEntry()
                || msg.getTimestamp() < host.openStart()
                || messageEnterSettled.contains(msg)
                || now - msg.getTimestamp() > ENTRANCE_SETTLE_GUARD_MS) {
            return 1.0F;
        }
        Long start = messageEnterStart.get(msg);
        if (start == null) {
            start = now;
            messageEnterStart.put(msg, start);
        }
        return Math.min(1.0F, (now - start) / (float) MESSAGE_ANIM_MS);
    }

    /**
     * Bounded housekeeping for the never-replay guarantee: once a message is
     * older than the guard window it is settled by time alone, so its entry can
     * leave the set. Runs at most once a second.
     */
    private void pruneEntranceSettled(long now) {
        if (messageEnterSettled.isEmpty() || now - lastEntrancePrune < 1000L) {
            return;
        }
        lastEntrancePrune = now;
        long cutoff = now - ENTRANCE_SETTLE_GUARD_MS;
        messageEnterSettled.removeIf(m -> m.getTimestamp() < cutoff);
    }

    /** How a message is painted. Draw, measure and hit-test all read this one answer. */
    private enum BubbleKind { SYSTEM, IMAGE, IMAGE_PLACEHOLDER, TEXT }

    /** Once per launch; see {@link #noteImageMessagesDisabled()}. */
    private static final AtomicBoolean IMAGE_MESSAGES_OFF_NOTED = new AtomicBoolean();

    /**
     * The single classification of a message. It used to be "extractImageUrl(raw)
     * != null" in three separate places, which meant a code the extractor could
     * not read was painted as a TEXT bubble and spilled its raw protocol text
     * into the chat (with the tail linkedified into a broken URL). A code we
     * cannot turn into an image now still gets the placeholder.
     */
    private static BubbleKind kindOf(ChatMessage msg) {
        if (msg.isSystem()) {
            return BubbleKind.SYSTEM;
        }
        String raw = msg.getRawText();
        if (Cicodes.parseImageMeta(raw) != null) {
            if (!AtomChatConfig.get().imageMessagesEnabled) {
                noteImageMessagesDisabled();
                return BubbleKind.IMAGE_PLACEHOLDER;
            }
            return BubbleKind.IMAGE;
        }
        return ImageCode.contains(raw) ? BubbleKind.IMAGE_PLACEHOLDER : BubbleKind.TEXT;
    }

    /**
     * The receive switch being off is a deliberate setting, not a fault — but
     * the placeholder it produces is the same green [Image] used for a code we
     * cannot parse, so "every image shows a placeholder" otherwise has no
     * answer anywhere in the log. Said once per launch, not once per message,
     * and ASCII only: the log file carries the platform charset, so a
     * translated placeholder would arrive mangled in a bug report.
     */
    private static void noteImageMessagesDisabled() {
        if (IMAGE_MESSAGES_OFF_NOTED.compareAndSet(false, true)) {
            AtomChat.LOGGER.warn("Incoming image messages are switched off in AtomChat's settings; "
                    + "arriving images show the image placeholder and nothing is downloaded");
        }
    }

    private MessageHit drawMessage(Canvas canvas, ChatMessage msg, float x, float y, float maxWidth, int index,
                                   boolean grouped) {
        if (msg.isSystem()) {
            return drawSystemMessage(canvas, msg, x, y, maxWidth, index);
        }
        Font font = FontManager.font(UiTokens.FONT_BODY);
        float bubbleMaxWidth = maxWidth - UiTokens.BUBBLE_RETRACT;
        String raw = msg.getRawText();
        BubbleKind kind = kindOfCached(msg);
        if (kind == BubbleKind.IMAGE_PLACEHOLDER) {
            return drawImagePlaceholderMessage(canvas, msg, x, y, maxWidth, index, grouped);
        }
        if (kind == BubbleKind.IMAGE) {
            return drawImageMessage(canvas, msg, raw, Cicodes.extractImageUrl(raw),
                    x, y, maxWidth, index, grouped);
        }
        float textMaxWidth = bubbleMaxWidth - UiTokens.BUBBLE_PAD * 2.0F;
        List<RichLine> richLines = wrappedLines(msg, font, textMaxWidth);
        // The bubble must hug the longest visible line. Using the single-line
        // width of the whole message collapsed multi-line messages (e.g. a hard
        // newline between two short lines) to a pill only as wide as the bubble
        // padding, because the old expression forced a 0 width whenever there
        // was more than one line.
        float maxLineWidth = 0.0F;
        for (RichLine line : richLines) {
            maxLineWidth = Math.max(maxLineWidth, RichTextRenderer.width(font, line));
        }
        float bubbleWidth;
        if (maxLineWidth + UiTokens.BUBBLE_PAD * 2.0F <= bubbleMaxWidth) {
            bubbleWidth = Math.max(UiTokens.BUBBLE_MIN_W, maxLineWidth + UiTokens.BUBBLE_PAD * 2.0F);
        } else {
            bubbleWidth = bubbleMaxWidth;
        }
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float textHeight = Math.max(lineHeight, richLines.size() * lineHeight);

        // Layout formula: name band (first of group only) -> quote pill -> bubble.
        boolean hasQuote = msg.getQuoteName() != null;
        float quoteH = hasQuote ? UiTokens.QUOTE_HEIGHT + UiTokens.QUOTE_GAP : 0.0F;
        float band = grouped ? 0.0F : UiTokens.NAME_BAND;
        float bubbleTop = y + band + quoteH;
        float bubbleHeight = textHeight + UiTokens.BUBBLE_PAD_Y;
        // Bubble offset from the list edge; includes the avatar's edge inset so
        // the avatar-bubble gap stays AVATAR_GAP after the avatar moved inward.
        float nameOffset = UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP + avatarEdgeInset();
        float bubbleX = msg.isOwn() ? x + maxWidth - bubbleWidth - nameOffset : x + nameOffset;

        float avatarX = 0.0F;
        float avatarY = 0.0F;
        float avatarSize = 0.0F;
        if (!grouped) {
            // Name hugs the bubble's outer edge: right-aligned for own, left for others.
            drawMessageName(canvas, msg, y, bubbleX, bubbleX + bubbleWidth);
            avatarX = msg.isOwn() ? x + maxWidth - UiTokens.AVATAR_SIZE - avatarEdgeInset()
                    : x + avatarEdgeInset();
            avatarY = y + s(4);
            avatarSize = UiTokens.AVATAR_SIZE;
            // Poke animation: QQ-style wobble — the avatar rocks around its centre
            // (damped ±14° over two and a half oscillations in ~600ms) instead of
            // sliding side to side. The wobble itself is decorative, so with motion
            // off the poke is already suppressed at the click site and this block
            // never arms.
            drawAvatarWithPoke(canvas, msg, index, avatarX, avatarY);
        }
        if (hasQuote) {
            drawQuotePill(canvas, msg, x, maxWidth, y + band, msg.isOwn());
        }
        SkiaDraw.drawRoundedRect(canvas, bubbleX, bubbleTop, bubbleWidth, bubbleHeight, UiTokens.BUBBLE_RADIUS, msg.isOwn() ? ownBubble() : otherBubble());
        drawMessageSelection(canvas, msg, richLines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F, lineHeight, font);
        // backing=true: bubble text keeps the soft drop shadow it has always
        // had. The flag reads like a name-only concern, but the shadow block
        // is now one filtered layer for the whole block (see
        // RichTextRenderer#drawShadowPass), so covering the bubble here is
        // cheaper than the per-run layers it replaced, not costlier.
        RichTextRenderer.drawLines(canvas, font, richLines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F,
                lineHeight, bubbleText(msg), clickableSpans, true, true);
        drawDuplicateBadge(canvas, msg, bubbleX, bubbleWidth, bubbleTop, bubbleHeight);

        float bottom = bubbleTop + bubbleHeight;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, avatarX, avatarY, avatarSize, bubbleTop, bubbleX, bubbleWidth, bottom);
    }

    /**
     * System lines (death, command feedback, join...) render as a compact
     * centered gray capsule: no avatar, no name, smaller text.
     */
    private MessageHit drawSystemMessage(Canvas canvas, ChatMessage msg, float x, float y, float maxWidth, int index) {
        Font font = FontManager.font(UiTokens.FONT_QUOTE);
        List<RichLine> richLines = wrappedLines(msg, font, maxWidth - UiTokens.BUBBLE_PAD * 2.0F);
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float textHeight = Math.max(lineHeight, richLines.size() * lineHeight);
        float bubbleHeight = textHeight + UiTokens.SYSTEM_BUBBLE_PAD_Y;
        float lineMax = 0.0F;
        for (RichLine line : richLines) {
            lineMax = Math.max(lineMax, RichTextRenderer.width(font, line));
        }
        float bubbleWidth = Math.min(maxWidth, Math.max(s(40), lineMax + UiTokens.BUBBLE_PAD * 2.0F));
        float bubbleX = x + (maxWidth - bubbleWidth) / 2.0F;
        float bubbleTop = y + s(2);
        // System capsules share the secondary capsule family (configurable).
        SkiaDraw.drawRoundedRect(canvas, bubbleX, bubbleTop, bubbleWidth, bubbleHeight, UiTokens.radius(10),
                secondaryCapsuleBg());
        drawMessageSelection(canvas, msg, richLines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F, lineHeight, font);
        RichTextRenderer.drawLines(canvas, font, richLines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F,
                lineHeight, secondaryCapsuleText(), clickableSpans, true);
        float bottom = bubbleTop + bubbleHeight;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, 0.0F, 0.0F, 0.0F, bubbleTop, bubbleX, bubbleWidth, bottom);
    }

    /**
     * e33chat-style quote capsule: anchored to the avatar edge (right side for
     * own messages, left for others) with a full-row width budget, truncated
     * with an ellipsis only when exceeding that budget.
     */
    private void drawQuotePill(Canvas canvas, ChatMessage msg, float x, float maxWidth, float pillY, boolean own) {
        Font quoteFont = FontManager.font(UiTokens.FONT_QUOTE);
        float capW = maxWidth - UiTokens.AVATAR_SIZE - s(18);
        float barW = s(3);
        float textMaxW = capW - UiTokens.QUOTE_PAD_X * 2.0F - barW - s(4);
        String name = msg.getQuoteName().startsWith("@") ? msg.getQuoteName() : "@" + msg.getQuoteName();
        String quote = name + ": " + msg.getQuoteText();
        String display = Cicodes.truncateToWidth(quoteFont, quote, textMaxW);
        float pillW = Math.min(capW, SkiaFontRenderer.getStringWidth(quoteFont, display) + UiTokens.QUOTE_PAD_X * 2.0F + barW + s(4));
        // Align the quote's outer edge with the bubble's outer edge, not with
        // the avatar. The bubble uses AVATAR_GAP as the horizontal gap to the
        // avatar, so the quote must use the same token.
        // Same edge inset as the avatar so the pill stays aligned with the
        // shifted bubble's outer edge.
        float pillX = own ? x + maxWidth - UiTokens.AVATAR_SIZE - UiTokens.AVATAR_GAP
                - avatarEdgeInset() - pillW
                : x + avatarEdgeInset() + UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP;
        // Quote pill shares the secondary capsule family (configurable), so it
        // reads as the same family as system messages and time dividers.
        SkiaDraw.drawRoundedRect(canvas, pillX, pillY, pillW, UiTokens.QUOTE_HEIGHT, s(6), secondaryCapsuleBg());
        SkiaDraw.drawRoundedRect(canvas, pillX + UiTokens.QUOTE_PAD_X, pillY + s(3), barW, UiTokens.QUOTE_HEIGHT - s(6), barW / 2.0F, accent());
        float textStartX = pillX + UiTokens.QUOTE_PAD_X + barW + s(4);
        float centerBaselineY = SkiaFontRenderer.centerBaselineY(quoteFont, pillY + UiTokens.QUOTE_HEIGHT / 2.0F);
        boolean imageQuote = msg.getQuoteText() != null
                && Cicodes.isImagePlaceholder(msg.getQuoteText());
        if (imageQuote) {
            // Only the [图片]/[Image] placeholder is green; the quoted player's
            // name and the colon stay in the normal primary colour.
            String fullNamePart = name + ": ";
            float placeholderW = SkiaFontRenderer.getStringWidth(quoteFont, msg.getQuoteText());
            String namePart = Cicodes.truncateToWidth(quoteFont, fullNamePart, Math.max(0.0F, textMaxW - placeholderW));
            SkiaFontRenderer.drawText(canvas, quoteFont, namePart, textStartX, centerBaselineY, bubbleText(msg));
            float namePartW = SkiaFontRenderer.getStringWidth(quoteFont, namePart);
            SkiaFontRenderer.drawText(canvas, quoteFont, msg.getQuoteText(), textStartX + namePartW,
                    centerBaselineY, Color.makeARGB(255, 85, 255, 85));
        } else {
            SkiaFontRenderer.drawText(canvas, quoteFont, display, textStartX, centerBaselineY, bubbleText(msg));
        }
    }

    /**
     * Draws a message's name hugging its bubble's outer edge. One helper for
     * text and image bubbles so their spacing can never drift apart: the image
     * path centred the name in the band while the text path drew it from the raw
     * baseline, which put the two a cap-height apart.
     */
    private void drawMessageName(Canvas canvas, ChatMessage msg, float rowY, float leftX, float rightX) {
        Font nameFont = FontManager.font(UiTokens.FONT_NAME);
        // Name rows are UI chrome: server-attached click/hover events and
        // underlines stay only on names rendered inside system capsules.
        RichText sender = msg.getSenderRich().stripInteractions();
        if (sender.isEmpty()) {
            sender = RichText.literal(host.senderName(msg));
        }
        List<RichLine> lines = RichTextRenderer.wrapFor(sender, nameFont, Float.MAX_VALUE);
        if (lines.isEmpty()) {
            return;
        }
        float lineHeight = SkiaFontRenderer.getHeight(nameFont);
        float nameWidth = 0.0F;
        for (RichLine line : lines) {
            nameWidth = Math.max(nameWidth, RichTextRenderer.width(nameFont, line));
        }
        float x = msg.isOwn() ? rightX - nameWidth : leftX;
        // RichTextRenderer.drawLines takes a centerY and internally converts
        // it to the cap-height baseline, matching the old drawText helper.
        float centerY = rowY + UiTokens.NAME_BAND / 2.0F;
        RichTextRenderer.drawLines(canvas, nameFont, lines, x, centerY, lineHeight, textPrimary(),
                clickableSpans, true, true);
    }

    /**
     * Anti-spam counter badge next to the bubble's vertical centre, outside the
     * bubble's outer edge (own bubbles: left; other bubbles: right). It uses
     * the accent colour so the repeat count never reads as part of the name.
     */
    private void drawDuplicateBadge(Canvas canvas, ChatMessage msg, float bubbleX, float bubbleWidth,
                                    float bubbleTop, float bubbleHeight) {
        if (msg.getDuplicateCount() <= 1) {
            return;
        }
        Font font = FontManager.font(UiTokens.FONT_NAME);
        String label = "x" + msg.getDuplicateCount();
        float labelW = SkiaFontRenderer.getStringWidth(font, label);
        float x = msg.isOwn() ? bubbleX - labelW - s(6) : bubbleX + bubbleWidth + s(6);
        if (x < s(4)) {
            x = s(4);
        }
        float centerY = bubbleTop + bubbleHeight / 2.0F;
        SkiaFontRenderer.drawText(canvas, font, label, x,
                SkiaFontRenderer.centerBaselineY(font, centerY), accent());
    }

    private MessageHit drawImageMessage(Canvas canvas, ChatMessage msg, String raw, String imageUrl,
                                        float x, float y, float maxWidth, int index, boolean grouped) {
        // Bubble offset from the list edge; includes the avatar's edge inset so
        // the avatar-bubble gap stays AVATAR_GAP after the avatar moved inward.
        float nameOffset = UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP + avatarEdgeInset();
        boolean hasQuote = msg.getQuoteName() != null;
        float quoteH = hasQuote ? UiTokens.QUOTE_HEIGHT + UiTokens.QUOTE_GAP : 0.0F;
        float band = grouped ? 0.0F : UiTokens.NAME_BAND;
        float bubbleTop = y + band + quoteH;
        float[] size = Cicodes.imageBubbleSize(Cicodes.parseImageMeta(raw), maxWidth);
        float imageW = size[0];
        float imageH = size[1];
        float bubbleX = msg.isOwn() ? x + maxWidth - imageW - nameOffset : x + nameOffset;

        float avatarX = 0.0F;
        float avatarY = 0.0F;
        float avatarSize = 0.0F;
        if (!grouped) {
            // Name hugs the bubble's outer edge, exactly like a text bubble.
            drawMessageName(canvas, msg, y, bubbleX, bubbleX + imageW);
            avatarX = msg.isOwn() ? x + maxWidth - UiTokens.AVATAR_SIZE - avatarEdgeInset()
                    : x + avatarEdgeInset();
            avatarY = y + s(4);
            avatarSize = UiTokens.AVATAR_SIZE;
            drawAvatarWithPoke(canvas, msg, index, avatarX, avatarY);
        }
        if (hasQuote) {
            drawQuotePill(canvas, msg, x, maxWidth, y + band, msg.isOwn());
        }
        Image image = ImageLoader.get().get(imageUrl, true);
        if (image != null) {
            // No bubble fill behind a loaded image: a transparent PNG/GIF must
            // show the panel behind it, not a grey plate. The rounded clip in
            // drawRoundedImage still shapes the corners; opaque images cover the
            // area anyway, so they are unchanged.
            // No aspect fix-up here: the CICode carries the intrinsic size, so
            // the bubble already has the image's proportions and the bitmap is
            // simply fitted to it. Stretching only happened because the box used
            // to be a fixed 275x175 with the height clamped rather than scaled.
            SkiaDraw.drawRoundedImage(canvas, image, bubbleX, bubbleTop, imageW, imageH, UiTokens.BUBBLE_RADIUS);
        } else {
            // Placeholder only while the download is in flight: once an image
            // is loaded it must not keep a plate behind its transparent pixels.
            SkiaDraw.drawRoundedRect(canvas, bubbleX, bubbleTop, imageW, imageH, UiTokens.BUBBLE_RADIUS, otherBubble());
            Font loadingFont = FontManager.font(UiTokens.FONT_QUOTE);
            SkiaFontRenderer.drawTextCentered(canvas, loadingFont, tr("atomchat.image.loading"),
                    bubbleX + imageW / 2.0F, bubbleTop + imageH / 2.0F, textSecondary());
        }
        drawDuplicateBadge(canvas, msg, bubbleX, imageW, bubbleTop, imageH);

        float bottom = bubbleTop + imageH;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, avatarX, avatarY, avatarSize, bubbleTop, bubbleX, imageW, bottom);
    }

    /**
     * Image messages with the receiving toggle off: a compact capsule with the
     * green [图片] placeholder — the same mark as the vanilla HUD, and nothing
     * is fetched or decoded. Identity (name + avatar) stays so the sender is
     * still readable.
     */
    private MessageHit drawImagePlaceholderMessage(Canvas canvas, ChatMessage msg, float x, float y,
                                                   float maxWidth, int index, boolean grouped) {
        boolean hasQuote = msg.getQuoteName() != null;
        float quoteH = hasQuote ? UiTokens.QUOTE_HEIGHT + UiTokens.QUOTE_GAP : 0.0F;
        float band = grouped ? 0.0F : UiTokens.NAME_BAND;
        Font font = FontManager.font(UiTokens.FONT_QUOTE);
        String placeholder = tr("atomchat.hud.image");
        float textW = SkiaFontRenderer.getStringWidth(font, placeholder);
        float pillW = Math.min(maxWidth - UiTokens.BUBBLE_RETRACT, textW + UiTokens.BUBBLE_PAD * 2.0F);
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float pillH = lineHeight + UiTokens.SYSTEM_BUBBLE_PAD_Y;
        // Bubble offset from the list edge; includes the avatar's edge inset so
        // the avatar-bubble gap stays AVATAR_GAP after the avatar moved inward.
        float nameOffset = UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP + avatarEdgeInset();
        float pillX = msg.isOwn() ? x + maxWidth - pillW - nameOffset : x + nameOffset;
        float pillTop = y + band + quoteH;

        float avatarX = 0.0F;
        float avatarY = 0.0F;
        float avatarSize = 0.0F;
        if (!grouped) {
            drawMessageName(canvas, msg, y, pillX, pillX + pillW);
            avatarX = msg.isOwn() ? x + maxWidth - UiTokens.AVATAR_SIZE - avatarEdgeInset()
                    : x + avatarEdgeInset();
            avatarY = y + s(4);
            avatarSize = UiTokens.AVATAR_SIZE;
            drawAvatarWithPoke(canvas, msg, index, avatarX, avatarY);
        }
        if (hasQuote) {
            drawQuotePill(canvas, msg, x, maxWidth, y + band, msg.isOwn());
        }
        SkiaDraw.drawRoundedRect(canvas, pillX, pillTop, pillW, pillH, UiTokens.radius(10),
                secondaryCapsuleBg());
        SkiaFontRenderer.drawTextCentered(canvas, font, placeholder,
                pillX + pillW / 2.0F, pillTop + pillH / 2.0F, Color.makeARGB(255, 85, 255, 85));
        drawDuplicateBadge(canvas, msg, pillX, pillW, pillTop, pillH);
        float bottom = pillTop + pillH;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, avatarX, avatarY, avatarSize, pillTop, pillX, pillW, bottom);
    }

    private float measureContentHeight(List<ChatMessage> messages, float width) {
        float contentHeight = 0;
        for (int i = 0; i < messages.size(); i++) {
            if (dividerBefore(messages, i)) {
                contentHeight += TIME_DIVIDER_H + UiTokens.LIST_GAP;
            }
            boolean grouped = isCompactGrouped(messages, i);
            contentHeight += messageHeight(messages.get(i), width, grouped);
            boolean nextGrouped = isCompactGrouped(messages, i + 1);
            contentHeight += nextGrouped
                    ? MessageGrouping.groupedGap(UiTokens.LIST_GAP, UiTokens.s(2))
                    : UiTokens.LIST_GAP;
        }
        return contentHeight;
    }

    /**
     * Must match what drawMessage/drawImageMessage actually lay out:
     * name band + (quote pill + gap) + bubble; image bubbles are s(140) tall.
     * A grouped continuation omits the name/avatar band.
     */
    private float messageHeight(ChatMessage msg, float maxWidth, boolean grouped) {
        if (msg.isSystem()) {
            Font font = FontManager.font(UiTokens.FONT_QUOTE);
            float lineHeight = SkiaFontRenderer.getHeight(font);
            int lines = wrappedLines(msg, font, maxWidth - UiTokens.BUBBLE_PAD * 2.0F).size();
            return s(2) + Math.max(lineHeight, lines * lineHeight) + UiTokens.SYSTEM_BUBBLE_PAD_Y;
        }
        float quoteH = msg.getQuoteName() != null ? UiTokens.QUOTE_HEIGHT + UiTokens.QUOTE_GAP : 0.0F;
        float band = grouped ? 0.0F : UiTokens.NAME_BAND;
        BubbleKind kind = kindOfCached(msg);
        if (kind == BubbleKind.IMAGE_PLACEHOLDER) {
            Font font = FontManager.font(UiTokens.FONT_QUOTE);
            return band + quoteH
                    + SkiaFontRenderer.getHeight(font) + UiTokens.SYSTEM_BUBBLE_PAD_Y;
        }
        if (kind == BubbleKind.IMAGE) {
            return band + quoteH
                    + Cicodes.imageBubbleSize(Cicodes.parseImageMeta(msg.getRawText()), maxWidth)[1];
        }
        Font font = FontManager.font(UiTokens.FONT_BODY);
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float wrapW = Math.max(s(20), maxWidth - UiTokens.BUBBLE_RETRACT - UiTokens.BUBBLE_PAD * 2.0F);
        int lines = wrappedLines(msg, font, wrapW).size();
        return band + quoteH + UiTokens.BUBBLE_PAD_Y + Math.max(lineHeight, lines * lineHeight);
    }

    // ------------------------------------------------------------------ text selection

    public List<MessageTextLine> textLinesForHit(MessageHit hit) {
        List<MessageTextLine> out = new ArrayList<>();
        ChatMessage msg = hit.message();
        BubbleKind kind = kindOfCached(msg);
        if (kind == BubbleKind.IMAGE || kind == BubbleKind.IMAGE_PLACEHOLDER) {
            return out;
        }
        Font font = FontManager.font(msg.isSystem() ? UiTokens.FONT_QUOTE : UiTokens.FONT_BODY);
        float textMax = Math.max(s(20), hit.bubbleWidth() - UiTokens.BUBBLE_PAD * 2.0F);
        // The hit bubble never wraps tighter than the draw-path width (a short
        // bubble is at least one full line wide), so the cached draw wrap and
        // this query produce identical line splits.
        List<RichLine> richLines = wrappedLines(msg, font, textMax);
        List<String> lines = new ArrayList<>();
        for (RichLine line : richLines) {
            lines.add(line.getPlainText());
        }
        if (lines.isEmpty()) {
            return out;
        }
        float lineH = SkiaFontRenderer.getHeight(font);
        float bubbleH = hit.bubbleBottom() - hit.bubbleY();
        float centerY = hit.bubbleY() + bubbleH / 2.0F;
        float blockTop = centerY - lines.size() * lineH / 2.0F;
        float textX = hit.bubbleX() + UiTokens.BUBBLE_PAD;
        for (int i = 0; i < lines.size(); i++) {
            out.add(new MessageTextLine(msg, i, lines.get(i), textX, blockTop + i * lineH, lineH));
        }
        return out;
    }

    private int charAtLine(MessageTextLine line, float mx) {
        String text = line.text();
        if (text.isEmpty()) {
            return 0;
        }
        Font font = FontManager.font(line.message().isSystem() ? UiTokens.FONT_QUOTE : UiTokens.FONT_BODY);
        float x = line.x();
        // Step by code point: a char-by-char loop splits surrogate pairs, so a
        // click near an emoji could return an index into the middle of it and
        // later slice the copied text in half.
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            int chars = Character.charCount(cp);
            float w = SkiaFontRenderer.getStringWidth(font, text.substring(i, i + chars));
            if (mx < x + w / 2.0F) {
                return i;
            }
            x += w;
            i += chars;
        }
        return text.length();
    }

    /**
     * Draws the active selection highlight for one message before its text is
     * drawn, so the glyphs stay readable above the blue block. The range may
     * start or end in another message. Takes the wrapped lines and flattens
     * them to plain text only once a selection exists — without one the whole
     * list draws through here every frame and the plain copies used to be
     * built and thrown away unread.
     */
    private void drawMessageSelection(Canvas canvas, ChatMessage msg, List<RichLine> richLines, float textX,
                                      float centerY, float lineHeight, Font font) {
        if (richLines.isEmpty() || !hasSelection()) {
            return;
        }
        List<String> lines = new ArrayList<>(richLines.size());
        for (RichLine line : richLines) {
            lines.add(line.getPlainText());
        }
        int[] range = selectionRangeFor(msg, lines.size());
        if (range == null) {
            return;
        }
        int aLine = range[0];
        int aChar = range[1];
        int fLine = range[2];
        int fChar = range[3];
        float totalH = lines.size() * lineHeight;
        float blockTop = centerY - totalH / 2.0F;
        for (int i = 0; i < lines.size(); i++) {
            if (i < aLine || i > fLine) {
                continue;
            }
            int start;
            int end;
            if (aLine == fLine) {
                start = aChar;
                end = fChar;
            } else if (i == aLine) {
                start = aChar;
                end = lines.get(i).length();
            } else if (i == fLine) {
                start = 0;
                end = fChar;
            } else {
                start = 0;
                end = lines.get(i).length();
            }
            start = Math.max(0, Math.min(start, lines.get(i).length()));
            end = Math.max(0, Math.min(end, lines.get(i).length()));
            if (start >= end) {
                continue;
            }
            String line = lines.get(i);
            float x0 = textX + SkiaFontRenderer.getStringWidth(font, line.substring(0, start));
            float x1 = textX + SkiaFontRenderer.getStringWidth(font, line.substring(0, end));
            float y = blockTop + i * lineHeight;
            SkiaDraw.drawRoundedRect(canvas, x0, y, Math.max(1.0F, x1 - x0), lineHeight, s(1), 0xE02D6FD6);
        }
    }

    /**
     * @return {@code {startLine, startChar, endLine, endCharExclusive}} for this
     *         message within the current selection, or null when the message is
     *         outside the range. An end char of {@link Integer#MAX_VALUE} means
     *         "to the end of the line".
     */
    private int[] selectionRangeFor(ChatMessage msg, int lineCount) {
        if (currentMessages == null || currentMessages.isEmpty() || lineCount <= 0) {
            return null;
        }
        int ai = indexOfIdentity(currentMessages, selectionAnchorMessage);
        int fi = indexOfIdentity(currentMessages, selectionFocusMessage);
        int mi = indexOfIdentity(currentMessages, msg);
        if (ai < 0 || fi < 0 || mi < 0) {
            return null;
        }
        boolean reverse = ai > fi
                || (ai == fi && (selectionAnchorLine > selectionFocusLine
                || (selectionAnchorLine == selectionFocusLine
                && selectionAnchorChar > selectionFocusChar)));
        int startIdx = reverse ? fi : ai;
        int endIdx = reverse ? ai : fi;
        if (mi < startIdx || mi > endIdx) {
            return null;
        }
        ChatMessage startMsg = reverse ? selectionFocusMessage : selectionAnchorMessage;
        ChatMessage endMsg = reverse ? selectionAnchorMessage : selectionFocusMessage;
        int sl = reverse ? selectionFocusLine : selectionAnchorLine;
        int sc = reverse ? selectionFocusChar : selectionAnchorChar;
        int el = reverse ? selectionAnchorLine : selectionFocusLine;
        int ec = reverse ? selectionAnchorChar : selectionFocusChar;
        if (mi == startIdx && mi == endIdx) {
            if (sl > el || (sl == el && sc > ec)) {
                int tmp = sl;
                sl = el;
                el = tmp;
                tmp = sc;
                sc = ec;
                ec = tmp;
            }
            return new int[]{sl, sc, el, ec};
        }
        if (mi == startIdx) {
            return new int[]{sl, sc, lineCount - 1, Integer.MAX_VALUE};
        }
        if (mi == endIdx) {
            return new int[]{0, 0, el, ec};
        }
        return new int[]{0, 0, lineCount - 1, Integer.MAX_VALUE};
    }

    private static int indexOfIdentity(List<ChatMessage> list, ChatMessage target) {
        if (target == null) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).sameAs(target)) {
                return i;
            }
        }
        return -1;
    }

    private ClickableSpan findClickableSpan(float mx, float my) {
        for (ClickableSpan s : clickableSpans) {
            if (mx >= s.x() && mx <= s.x() + s.w() && my >= s.y() && my <= s.y() + s.h()) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ tokens

    private static float s(float v) {
        return UiTokens.s(v);
    }

    /**
     * Breathing gap between the avatar and the list edge: the rim ring extends
     * s(1.25) past the avatar box (radius s(0.75) plus half the s(1.0) stroke),
     * so the avatar sits s(1.5) inside the list clip and the full circle stays
     * visible. Bubbles shift with it (nameOffset) so the avatar-bubble gap is
     * still AVATAR_GAP.
     */
    private static float avatarEdgeInset() {
        return s(1.5F);
    }

    /** Minecraft language lookup for all AtomChat UI copy. */
    private static String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }

    private int accent() {
        return AtomChatConfig.get().accentColor;
    }

    private int ownBubble() {
        return AtomChatConfig.get().ownBubbleColor;
    }

    private int otherBubble() {
        return AtomChatConfig.get().otherBubbleColor;
    }

    private int textPrimary() {
        return AtomChatConfig.get().textPrimaryColor;
    }

    private int textSecondary() {
        return AtomChatConfig.get().textSecondaryColor;
    }

    /** Shared capsule background for system messages, time dividers, quote pills. */
    private int secondaryCapsuleBg() {
        return AtomChatConfig.get().secondaryCapsuleBg;
    }

    private int secondaryCapsuleText() {
        return AtomChatConfig.get().secondaryCapsuleText;
    }

    /** Text inside a chat bubble (body rich text and quoted text). */
    private int bubbleText(ChatMessage msg) {
        return msg != null && msg.isOwn()
                ? AtomChatConfig.get().bubbleTextColor
                : AtomChatConfig.get().otherBubbleTextColor;
    }
}
