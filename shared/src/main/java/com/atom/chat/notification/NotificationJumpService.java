package com.atom.chat.notification;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.nav.AppPage;
import com.atom.chat.page.MessageListView;
import com.atom.chat.ui.ActionFeedback;
import com.atom.chat.ui.ActionToast;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Canvas;

import java.util.List;
import java.util.UUID;

/**
 * Notification banners and what a click on one does: the floating layer that
 * paints them over the page body, the jump to the message behind a
 * notification, and the action-feedback queue that shares that layer.
 *
 * <p>Everything here is mapping-neutral. The Minecraft touches of the original
 * screen code came in three flavours, and each one keeps its owner:
 * <ul>
 *   <li><b>per-page lookups (ports).</b> {@link ScrollPort} answers for the page
 *       that is <em>current at scroll time</em> — a jump may just have pushed
 *       that page — and the scroll controller must be created only once a
 *       target was located, exactly like the screen's own lazy lookup did;</li>
 *   <li><b>navigation facts and actions ({@link NavPort}).</b> The jump verifies
 *       where it landed by re-asking the port, which is how the screen's nav
 *       stack stayed the single source of truth;</li>
 *   <li><b>the composer ({@link ComposerFocusPort}).</b> Presence and the focus
 *       write are the shared {@code EditBox}'s (D21), so they stay behind the
 *       port: {@code input != null} and {@code inputFocused = true;
 *       setFocused(input); input.setFocused(true)}.</li>
 * </ul>
 *
 * <p>What is handed over per call instead of held: the frame's layout (D10 —
 * the layout must be the one the page body was drawn with), the message list
 * view (D22, whose row metrics the scroll needs), the pointer in panel space
 * (the screen converts GUI coordinates: a window read), the modal flag and the
 * read-only flag — both plain booleans the screen owns.
 *
 * <p>{@link ActionFeedback} stays here, with the {@code feedback(…)} entry
 * points the other domains call: the queue and the toasts live in the same
 * floating layer as the banners, so they are one domain's business. Pulling
 * them into a separate service was considered and deliberately left for later.
 */
public final class NotificationJumpService {
    /**
     * The navigation stack (D24), as the jump sees it: where it is now, and the
     * two ways it can move. The jump re-reads the port after moving, so the
     * screen stays the only owner of "did the page selection take".
     */
    public interface NavPort {
        /** The page kind on top of the stack (D24's {@code topPage()}). */
        AppPage topPage();

        /** The private partner on top of the stack, or null (D24's {@code activePrivateTarget()}). */
        PlayerRef activePrivateTarget();

        /** Pushes the private conversation page (D24's {@code openPrivateChat}). */
        void openPrivateChat(PlayerRef target);

        /** Pushes the public feed (D24's {@code openWorldChat}). */
        void openWorldChat();
    }

    /**
     * What the scroll-to-message step asks the screen for. Both answers are
     * per-<em>current</em>-page and both are read at scroll time: the feed is
     * the screen's {@code messagesForNav(topNav())} (D22) and the controller is
     * {@code scrollForNav(topNav())} (D11), which creates the page's controller
     * on first use — so it is only asked once a target was located.
     */
    public interface ScrollPort {
        /** The current page's feed (D22). */
        List<ChatMessage> currentMessages();

        /** The current page's scroll controller (D11), created on first use. */
        ScrollController currentScroll();
    }

    /**
     * The one shared composer field (D21). The screen keeps the field; this port
     * answers whether there is one and performs the focus write.
     */
    public interface ComposerFocusPort {
        /** Whether the composer exists ({@code input != null}: false before init and after disposal). */
        boolean composerPresent();

        /**
         * Marks the composer focused, the way a click into it would:
         * {@code inputFocused = true; setFocused(input); input.setFocused(true)}.
         */
        void focusComposer();
    }

    /**
     * The screen's canvas-leak probe, passed in so this class can keep the
     * measurement the banner render has always taken (a debug-only log line, but
     * the same counter the screen's other probes share).
     */
    @FunctionalInterface
    public interface CanvasLeakProbe {
        /** Logs when the draw call left {@code saveCountBefore - canvas.getSaveCount()} entries behind. */
        void probe(String where, int saveCountBefore, Canvas canvas);
    }

    private final NavPort navPort;
    private final ScrollPort scrollPort;
    private final ComposerFocusPort composerPort;
    private final CanvasLeakProbe leakProbe;
    private final ActionToast.Translator translator;

    /** Action-feedback queue: transient acknowledgements of completed actions (see the class comment). */
    private final ActionFeedback actionFeedback = new ActionFeedback();

    public NotificationJumpService(NavPort navPort, ScrollPort scrollPort, ComposerFocusPort composerPort,
                                   CanvasLeakProbe leakProbe, ActionToast.Translator translator) {
        this.navPort = navPort;
        this.scrollPort = scrollPort;
        this.composerPort = composerPort;
        this.leakProbe = leakProbe;
        this.translator = translator;
    }

    /**
     * Notification banners, painted after every page body so they sit on top
     * regardless of which page is showing (the settings and profile branches
     * return early from drawPanel). They live inside the panel layer, so they
     * fade and slide with the panel instead of floating outside it.
     *
     * @param modalOpen whether a modal (cropper, colour picker, picture preview)
     *                  owns the top layer — a fact from the screen, see the guard
     * @param layout    the frame's layout (D10), the same one the page body was drawn with
     * @param vmx       pointer x in panel space (the screen converts from GUI space)
     * @param vmy       pointer y in panel space
     */
    public void drawNotificationBanners(Canvas canvas, boolean modalOpen, UiLayout layout,
                                        float vmx, float vmy) {
        // Modals own the top layer; a banner painted over them would be visible
        // but unclickable (ModalInput swallows the click), so hide it until the
        // modal closes. The 4s queue lifetime still lets it reappear.
        if (modalOpen) {
            return;
        }
        UiLayout.Rect panel = layout.rect();
        if (NotificationBanner.INSTANCE.hasActive()) {
            int probeBefore = canvas.getSaveCount();
            NotificationBanner.INSTANCE.renderInPanel(canvas, panel.x(), panel.y(), panel.w(), panel.h(),
                    vmx, vmy);
            leakProbe.probe("banner render", probeBefore, canvas);
        }
        // Action feedback draws last, so it sits above a queued banner in the
        // same floating layer: it acknowledges something the player just did and
        // must stay in view. It is anchored where the eye already is — the top
        // edge of the input bar — and rises up from behind it, rather than
        // dropping in from the panel top away from the action. Pages without a
        // composer (detail pages) fall back to the bottom of the list.
        UiLayout toastLayout = layout;
        float toastAnchor = toastLayout.inputBar.w() > 0.0F
                ? toastLayout.inputBar.y() : toastLayout.list.bottom();
        ActionToast.render(canvas, actionFeedback, panel.x(), panel.w(),
                panel.y(), toastAnchor, System.currentTimeMillis(), translator);
    }

    /**
     * Banner clicks jump to the message behind the notification. Registered
     * after the modals, so a cropper or colour picker still swallows the click
     * while it is open.
     *
     * <p>This is the body the screen's {@code BannerInput} handler used to hold;
     * the handler itself stays on the screen as a two-line adapter, because the
     * router hands over GUI coordinates and the window read that converts them
     * is the screen's.
     *
     * @param button   the pressed mouse button (only the left one is ours)
     * @param readOnly whether the current private page is read-only (D24); only
     *                 the send-button route reads it, for the composer focus
     */
    public boolean onBannerClick(int button, float vmx, float vmy, boolean readOnly,
                                 UiLayout layout, MessageListView view) {
        if (button != 0 || !NotificationBanner.INSTANCE.hasActive()) {
            return false;
        }
        // The send-style round button means "reply now": same jump plus
        // composer focus. The rest of the banner just navigates. Both
        // routes jump first and dismiss only once the jump landed, so a
        // banner whose message can no longer be located stays on screen.
        NotificationBanner.Active sendButton = NotificationBanner.INSTANCE.buttonHit(vmx, vmy);
        if (sendButton != null) {
            if (jumpToNotification(sendButton, layout, view)) {
                NotificationBanner.INSTANCE.dismiss(sendButton);
                focusComposerForReply(readOnly);
            }
            return true;
        }
        NotificationBanner.Active hit = NotificationBanner.INSTANCE.hitTest(vmx, vmy);
        if (hit == null) {
            return false;
        }
        if (jumpToNotification(hit, layout, view)) {
            NotificationBanner.INSTANCE.dismiss(hit);
        }
        return true;
    }

    /**
     * Composer focus for the banner send-button: one click from banner to typing.
     *
     * @param readOnly whether the current private page is read-only (D24's
     *                 {@code isPrivateReadOnly}); a blocked or offline partner
     *                 never gets the focus, exactly as the guard asked before
     */
    public void focusComposerForReply(boolean readOnly) {
        if (composerPort.composerPresent() && !readOnly) {
            composerPort.focusComposer();
        }
    }

    /**
     * Opens whatever page holds this notification and scrolls the message in.
     *
     * <p>Returns whether the jump landed; the caller dismisses the banner only
     * on {@code true}. A whisper whose sender has no profile name (there is no
     * conversation key to open), a message that is no longer in the target
     * page's feed, or a page selection that did not take leaves the banner up
     * instead of stranding the player on an unchanged panel.
     *
     * @param layout the frame's layout (D10), for the list rect the scroll is measured against
     * @param view   the message list view (D22), whose row metrics the scroll needs
     */
    public boolean jumpToNotification(NotificationBanner.Active banner, UiLayout layout, MessageListView view) {
        ChatMessage msg = banner.message();
        if (msg == null) {
            return false;
        }
        if (banner.type() == NotificationBanner.Type.WHISPER) {
            PlayerRef target = PlayerRef.of(msg.getSenderUuid(), msg.getProfileName());
            // PlayerRef.key() is name-based, so a ref without a profile name
            // has no conversation to open.
            if (target.realName() == null) {
                return false;
            }
            if (!target.equals(navPort.activePrivateTarget())) {
                navPort.openPrivateChat(target);
            }
            if (navPort.topPage() != AppPage.PRIVATE_CHAT || !target.equals(navPort.activePrivateTarget())) {
                return false;
            }
        } else if (navPort.topPage() != AppPage.WORLD_CHAT) {
            navPort.openWorldChat();
            if (navPort.topPage() != AppPage.WORLD_CHAT) {
                return false;
            }
        }
        return scrollToMessage(msg, layout, view);
    }

    /**
     * Scrolls the current page's list so the message sits a third of the way
     * down the viewport, then leaves a fading highlight on it. Anti-spam merges
     * mean the target can be a merged bubble — that one is still the right
     * destination, so merged messages are never un-merged here. Returns whether
     * the message was located and the scroll/highlight applied.
     */
    private boolean scrollToMessage(ChatMessage msg, UiLayout layout, MessageListView view) {
        List<ChatMessage> messages = scrollPort.currentMessages();
        int index = findMessageIndex(messages, msg);
        if (index < 0) {
            return false;
        }
        ScrollController scroll = scrollPort.currentScroll();
        UiLayout.Rect list = layout.list;
        float offset = view.offsetOf(messages, index, list.w());
        // The page may have just been opened (fresh controller, maxScroll still
        // zero). Feed it the real content height before the jump or the clamp
        // inside scrollTo collapses the target to 0.
        scroll.setContent(view.offsetOf(messages, messages.size(), list.w()), list.h());
        scroll.scrollTo(offset - list.h() / 3.0F, true);
        view.highlight(messages.get(index));
        return true;
    }

    /**
     * Resolves the banner's message to its current list slot. Anti-spam can
     * replace the original object with a merged {@code xN} copy after the banner
     * was queued, so fall back to the last same-content/sender row instead of
     * giving up on an identity miss. Touches no screen state, so the jump's
     * locate step is pinned by a test directly instead of through a live screen.
     */
    public static int findMessageIndex(List<ChatMessage> messages, ChatMessage target) {
        int exact = messages.indexOf(target);
        if (exact >= 0) {
            return exact;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage candidate = messages.get(i);
            if (candidate.isOwn() != target.isOwn()
                    || candidate.isSystem() != target.isSystem()
                    || !java.util.Objects.equals(candidate.getQuoteName(), target.getQuoteName())
                    || !java.util.Objects.equals(candidate.getQuoteText(), target.getQuoteText())
                    || !java.util.Objects.equals(candidate.getContentText(), target.getContentText())) {
                continue;
            }
            UUID a = candidate.getSenderUuid();
            UUID b = target.getSenderUuid();
            boolean sameSender = a != null && b != null
                    ? a.equals(b)
                    : java.util.Objects.equals(candidate.getProfileName(), target.getProfileName());
            if (sameSender) {
                return i;
            }
        }
        return -1;
    }

    /** Raises a transient action acknowledgement in the panel's floating layer. */
    public void feedback(String key, boolean success) {
        actionFeedback.show(key, success ? ActionFeedback.Outcome.SUCCESS : ActionFeedback.Outcome.ERROR,
                System.currentTimeMillis());
    }

    /** Raises an acknowledgement whose text carries an argument (a player name). */
    public void feedback(String key, String label, boolean success) {
        actionFeedback.show(key, label, success ? ActionFeedback.Outcome.SUCCESS : ActionFeedback.Outcome.ERROR,
                System.currentTimeMillis());
    }
}
