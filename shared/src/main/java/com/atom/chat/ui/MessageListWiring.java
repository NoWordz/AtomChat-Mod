package com.atom.chat.ui;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.chat.PrivateChatStore;
import com.atom.chat.chat.WorldFeedFilter;
import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import com.atom.chat.page.MessageListView;

import java.util.List;
import java.util.UUID;

/**
 * What the message list needs from the screen, and the click/drag state machine
 * that feeds it (D22).
 *
 * <p><b>One page at a time, by that page's own identity.</b>
 * {@link #messagesForNav} resolves the messages of the page it is handed — the
 * world feed through D5's filter, a private conversation through its own store,
 * nothing for any other page. A transition draws two pages in one frame, so the
 * two sides are asked separately and neither may read the navigation stack's top
 * page: that rule is the same one the scroll controllers follow (D11).</p>
 *
 * <p><b>The click/drag coexistence flow is one small state machine</b> (Task 8):
 * every left press <em>arms</em> a candidate, any drag while one is pending
 * <em>marks it moved</em>, and the release fires only when the same span is still
 * under the pointer, nothing was dragged, and the span carries a click event.
 * Arms and clears come in pairs, so a press that a later branch claims (the
 * image preview, for instance) can drop the candidate explicitly.</p>
 *
 * <p><b>The hover tooltip rides the vanilla pipeline.</b> This half answers only
 * which span is hovered — and deliberately only on the world page, because a
 * root page keeps its own hover model and a stale world span must not show
 * through it. Drawing the tooltip stays with the shell, after the Skia panel and
 * the suggestion popup, so it stays readable on top of both.</p>
 *
 * <p>Ports on {@link Host}: the view's four needs (own identity, own name, a
 * message's sender label, the session's open timestamp), the world snapshot and a
 * conversation's messages, D5's filter instance, the span under a point, and
 * whether a span carries a click event. {@code S} is the span type: the shell's
 * is the mapping layer's own record, which carries a Minecraft style, so the
 * generics keep that type — and its {@code getClickEvent()} — out of here and let
 * the test drive the flow with plain values.</p>
 *
 * <p>One copy, in the shared layer: with those ports this class names no
 * Minecraft API, and the types it drives ({@link MessageListView},
 * {@link ChatMessage}, {@link WorldFeedFilter}, {@link ChatStore},
 * {@link PrivateChatStore}) already live at this same name on every target.</p>
 */
public final class MessageListWiring<S> {
    /** What the screen hands over: the view's needs, the stores and the span lookups. */
    public interface Host<S> {
        /** The local player's id, or null when there is no player. */
        UUID ownUuid();

        /** The local player's display name (D3). */
        String ownName();

        /** A message's sender label (D3's pipeline). */
        String messageSenderName(ChatMessage message);

        /** The session's open timestamp (D25): the entrance animation's clock. */
        long openStart();

        /** The world feed's raw snapshot, before D5's filter. */
        List<ChatMessage> worldSnapshot();

        /** One conversation's messages. */
        List<ChatMessage> privateMessages(PlayerRef target);

        /** D5's feed filter instance. */
        WorldFeedFilter worldFeed();

        /** The clickable span under a virtual point, or null. */
        S clickableSpanAt(float vmx, float vmy);

        /** Whether that span carries a click event. */
        boolean hasClickEvent(S span);
    }

    private final Host<S> host;

    /** Pending click candidate used by the click/drag coexistence flow (Task 8). */
    private S pendingClickSpan;
    private boolean pendingClickMoved;

    public MessageListWiring(Host<S> host) {
        this.host = host;
    }

    /** The view's host: the four things the list asks the screen for. */
    public MessageListView.Host viewHost() {
        return new MessageListView.Host() {
            @Override
            public UUID ownUuid() {
                return host.ownUuid();
            }

            @Override
            public String ownName() {
                return host.ownName();
            }

            @Override
            public String senderName(ChatMessage message) {
                return host.messageSenderName(message);
            }

            @Override
            public long openStart() {
                return host.openStart();
            }
        };
    }

    /**
     * The messages of {@code page}: the world feed filtered by D5's predicate, a
     * conversation's own messages, and nothing at all for a page that has no list.
     * Resolved per call, so a transition's two sides each get their own.
     */
    public List<ChatMessage> messagesForNav(NavPage page) {
        if (page == null) {
            return List.of();
        }
        if (page.page() == AppPage.WORLD_CHAT) {
            return host.worldFeed().filterFeed(host.worldSnapshot());
        }
        if (page.page() == AppPage.PRIVATE_CHAT) {
            return host.privateMessages(page.target());
        }
        return List.of();
    }

    // ------------------------------------------------- the click/drag flow

    /** Arms the click candidate under a point, and forgets any previous drag. */
    public void armClickAt(float vmx, float vmy) {
        pendingClickSpan = host.clickableSpanAt(vmx, vmy);
        pendingClickMoved = false;
    }

    /** Drops the candidate without firing it (a later branch claimed the press). */
    public void clearClick() {
        pendingClickSpan = null;
        pendingClickMoved = false;
    }

    /** True while a candidate is waiting for its release. */
    public boolean hasPendingClick() {
        return pendingClickSpan != null;
    }

    /** Any drag while a candidate is pending suppresses the click-on-release. */
    public void markMoved() {
        pendingClickMoved = true;
    }

    /** Takes the candidate out (the release owns it from here), leaving the drag flag. */
    public S takePending() {
        S pending = pendingClickSpan;
        pendingClickSpan = null;
        return pending;
    }

    /**
     * The release's verdict: the same span is still under the pointer, nothing was
     * dragged and the span has a click event. The lookup happens here, in the
     * position the inline code evaluated it.
     */
    public boolean shouldFireClick(S pending, float vmx, float vmy) {
        S released = host.clickableSpanAt(vmx, vmy);
        return pending != null && !pendingClickMoved
                && host.hasClickEvent(pending)
                && pending.equals(released);
    }

    /** The drag flag is cleared on every release, fired or not. */
    public void clearMoved() {
        pendingClickMoved = false;
    }

    /**
     * The span to show a hover tooltip for, or null. Only the world page has spans
     * to hover: a root page keeps its own hover model and must not show a stale
     * world span.
     */
    public S hoverSpan(boolean worldPage, float vmx, float vmy) {
        return worldPage ? host.clickableSpanAt(vmx, vmy) : null;
    }
}
