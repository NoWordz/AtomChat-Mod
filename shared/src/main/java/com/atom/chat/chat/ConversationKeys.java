package com.atom.chat.chat;

import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;

/**
 * The one key convention every per-conversation store is filed under: a private
 * chat by its partner's {@link PlayerRef#key()}, everything else under the empty
 * string (the public feed is not a conversation).
 *
 * <p>Three stores came to depend on this rule — the reply slots (D1), the
 * history fold windows (D4) and the scroll controllers (D11) — and each had
 * written it out itself. A single source is what keeps a message's reply, its
 * fold depth and its scroll position from ever landing in different
 * conversations: the rule is name-based, so two {@code PlayerRef}s that differ
 * only in UUID are the same conversation here, exactly as they are for the
 * composer drafts.
 *
 * <p>The strict form is deliberate: a null page, a non-private page and a
 * private page without a target all answer {@code ""}. The history store used to
 * reach the key through an unguarded {@code page.page()}, so a null page would
 * have thrown there instead; no caller passes one, but the shared rule cannot
 * be the narrow one.
 */
public final class ConversationKeys {
    private ConversationKeys() {
    }

    /** The conversation key {@code page} belongs to; {@code ""} for the public feed. */
    public static String of(NavPage page) {
        return page != null && page.page() == AppPage.PRIVATE_CHAT && page.target() != null
                ? page.target().key() : "";
    }
}
