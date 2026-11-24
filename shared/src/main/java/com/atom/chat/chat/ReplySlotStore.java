package com.atom.chat.chat;

import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;

import java.util.HashMap;
import java.util.Map;

/**
 * Armed reply bars, one slot per conversation.
 *
 * <p>Why slots and not a single field: the reply bar follows its page exactly
 * like the draft does. The screen's composer is one shared {@code EditBox}, so
 * "which reply is armed right now" is a property of the page that owns the
 * field — a single field meant leaving a page silently re-armed its reply on
 * whichever page came next (the shared-single-field leak this type replaces).
 *
 * <p>Two pieces of state, one contract:
 * <ul>
 *   <li>{@link #replies()} — the saved slot of every conversation, keyed like
 *       the history windows ({@link #replyKeyForNav});</li>
 *   <li>{@code replyTarget} — the live reply of the page that currently owns
 *       the composer, which is what {@link #syncReplySlot} writes back into the
 *       slot it came from.</li>
 * </ul>
 *
 * <p>Only one page draws per frame (the transition inversion — the one-stamp
 * rewrite), so {@link #replyBarVisible} takes the page being drawn: its armed
 * reply has to reserve the row, or a live bar would be painted into the
 * zero-height rect {@code UiLayout.of} leaves at the panel origin when
 * {@code replyH == 0}.
 *
 * <p>Mapping-neutral by construction: the screen passes the navigation facts in
 * ({@code topPage}/{@code topNav}/{@code ownsInputField}) rather than the store
 * reaching for them — that is what lets this live in {@code shared/} and be
 * tested offline.
 */
public final class ReplySlotStore {
    /** Slot key convention shared with the history windows: private chat = the
     *  partner's key, world chat = "" — so one conversation can never pick up
     *  another's slot. */
    private final Map<String, ChatMessage> replies = new HashMap<>();

    /** Armed reply of the page that currently owns the shared composer. */
    private ChatMessage replyTarget;
    private long nativeRevision=-1;
    public void redact(String signature){
        replies.replaceAll((key,message)->matches(message,signature)?message.asDeleted(NativeMessageBridge.deletedMarker()):message);
        if(matches(replyTarget,signature))replyTarget=replyTarget.asDeleted(NativeMessageBridge.deletedMarker());
        nativeRevision=NativeMessageSync.revision();
    }
    public static Snapshot redacted(Snapshot snapshot,String signature){
        var slots=new HashMap<>(snapshot.slots());
        slots.replaceAll((key,message)->matches(message,signature)?message.asDeleted(NativeMessageBridge.deletedMarker()):message);
        var target=snapshot.target();if(matches(target,signature))target=target.asDeleted(NativeMessageBridge.deletedMarker());
        return new Snapshot(Map.copyOf(slots),target);
    }
    private static boolean matches(ChatMessage message,String signature){return message!=null && signature.equals(message.nativeMeta().signature());}
    private void refreshNativeMessages(){
        long current=NativeMessageSync.revision();if(nativeRevision==current)return;nativeRevision=current;
        replies.replaceAll((key,message)->NativeMessageSync.presentation(message));
        replyTarget=NativeMessageSync.presentation(replyTarget);
    }

    /** Reply slot key, using the conversation keys' convention ({@link ConversationKeys}). */
    public String replyKeyForNav(NavPage page) {
        return ConversationKeys.of(page);
    }

    /**
     * Persists the armed reply to the page that is currently open. A page that
     * is not a chat page has no reply bar to persist.
     *
     * @param topPage the page kind on top of the nav stack (the caller's
     *                {@code topPage()})
     * @param topNav  the page on top of the nav stack (the caller's
     *                {@code topNav()}); its key owns the slot being written
     */
    public void syncReplySlot(AppPage topPage, NavPage topNav) {
        if (topPage != AppPage.WORLD_CHAT && topPage != AppPage.PRIVATE_CHAT) {
            return;
        }
        if (replyTarget == null) {
            replies.remove(replyKeyForNav(topNav));
        } else {
            replies.put(replyKeyForNav(topNav), replyTarget);
        }
    }

    /** Arms {@code page}'s saved reply, so the live field belongs to that page. */
    public void loadReplyFor(NavPage page) {
        if (page == null || (page.page() != AppPage.WORLD_CHAT && page.page() != AppPage.PRIVATE_CHAT)) {
            replyTarget = null;
            return;
        }
        replyTarget = replies.get(replyKeyForNav(page));
    }

    /**
     * Armed reply of a page: the live field when it owns the composer, else its slot.
     *
     * @param ownsInputField whether {@code page} is the page holding the shared
     *                       composer (the caller's {@code ownsInputField(page)})
     */
    public ChatMessage replyForNav(NavPage page, boolean ownsInputField) {
        refreshNativeMessages();
        return ownsInputField ? replyTarget : replies.get(replyKeyForNav(page));
    }

    /**
     * True while a reply bar is on screen: the live field holds one, or the
     * given page still has one armed in its slot. With the transition inversion
     * (the one-stamp rewrite) only one page ever draws per frame, so the two-sided
     * transition form is gone — the caller passes the page being drawn.
     *
     * @param page the page whose slot counts (may be null)
     */
    public boolean replyBarVisible(NavPage page) {
        refreshNativeMessages();
        return replyTarget != null || replySlotArmed(page);
    }

    /** True when a chat page still holds an armed reply in its slot. */
    public boolean replySlotArmed(NavPage page) {
        refreshNativeMessages();
        if (page == null || (page.page() != AppPage.WORLD_CHAT && page.page() != AppPage.PRIVATE_CHAT)) {
            return false;
        }
        return replies.get(replyKeyForNav(page)) != null;
    }

    public record Snapshot(Map<String,ChatMessage> slots,ChatMessage target) { }
    public Snapshot snapshot(){refreshNativeMessages();return new Snapshot(Map.copyOf(replies),replyTarget);}
    public void restore(Snapshot snapshot){replies.clear();replies.putAll(snapshot.slots());replyTarget=snapshot.target();nativeRevision=-1;refreshNativeMessages();}

    /** The live reply of the page owning the composer (null when none is armed). */
    public ChatMessage replyTarget() {
        refreshNativeMessages();
        return replyTarget;
    }

    /** Arms {@code message} for the page that owns the composer ("quote this"). */
    public void arm(ChatMessage message) {
        this.replyTarget = NativeMessageSync.presentation(message);
    }

    /** Drops the live reply; the page's slot is left alone until the next sync. */
    public void disarm() {
        this.replyTarget = null;
    }

    /**
     * Drops the live reply <em>and</em> every saved slot — what leaving the world
     * does, so nothing survives into the next one.
     */
    public void clearAll() {
        disarm();
        replies.clear();
    }
}