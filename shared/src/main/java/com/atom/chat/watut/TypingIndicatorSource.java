package com.atom.chat.watut;

import com.atom.chat.chat.PlayerRef;
import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;

/**
 * Whether one chat page should show the partner-typing hint ("typing…" in the
 * composer placeholder, QQ-style).
 *
 * <p>Only the private chat can ever report a partner: the feed has no partner,
 * and a private page that is read-only (partner offline, or blocked) never asks.
 * The hint is answered for the page whose composer is being drawn, never for
 * whatever happens to sit on top of the nav stack — a transition composites two
 * pages in the same frame.
 *
 * <p>Two facts come from outside and are passed in rather than looked up:
 * {@code page} itself, and whether that page is read-only — the screen's
 * {@code isPrivateReadOnlyFor(page)}, which also covers the block list, so a
 * blocked partner reads as read-only here and never reaches the bridge. What is
 * left is a pure query, which is why this lives in {@code shared/} with no
 * Minecraft types and no state — the same shape as {@link WatutBridge}, its only
 * collaborator.
 *
 * <p>With WATUT absent every query is a cheap {@code false} (see
 * {@link WatutBridge}), so the hint simply never appears.
 */
public final class TypingIndicatorSource {
    private TypingIndicatorSource() {
    }

    /**
     * The typing hint for one nav page: the hint belongs to the page whose
     * composer is drawn, not to whatever is on top of the stack.
     *
     * @param page     the page being drawn; null, or any page other than the
     *                 private chat, never shows the hint
     * @param readOnly whether that private page is read-only — the screen's
     *                 {@code isPrivateReadOnlyFor(page)}, which is also true for
     *                 a blocked partner; false for every other page
     */
    public static boolean partnerTypingFor(NavPage page, boolean readOnly) {
        if (page == null || page.page() != AppPage.PRIVATE_CHAT || readOnly) {
            return false;
        }
        PlayerRef partner = page.target();
        return partner != null && WatutBridge.isTyping(partner.uuid());
    }
}
