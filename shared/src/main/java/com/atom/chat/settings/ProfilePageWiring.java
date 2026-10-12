package com.atom.chat.settings;

import com.atom.chat.page.ProfilePage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.ScrollSyncService;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Canvas;

import java.util.UUID;

/**
 * The profile page's signature editing and role row (D18a): the composer borrow
 * behind the signature editor, the per-frame draft feed, the page's own draw
 * path, and who is allowed to know anyone's permission level.
 *
 * <p>The borrow is the same <b>parking ticket</b> as the quick-phrase editor's
 * (D16): park the composer's draft, load the current signature into the field
 * (that field is the IME carrier), focus it with the caret at the end, and put
 * the parked draft back — with the same cap on the way out (256 characters, the
 * chat limit). The four navigation paths that leave the page abort instead of
 * committing, and their call sites stay in the shell.</p>
 *
 * <p>Two contracts are easy to break by "improving" them: the role row answers
 * for the local player ({@code hasPermissions(2)}) or from a current server
 * companion answer for another player. Null means "unknown" and hides the
 * row. And the mutex with the other composer borrows is
 * only carried over: which editor may start is the shell's business.</p>
 *
 * <p>Ports on {@link Host}: D18's page, D21's composer reads and writes, the
 * focus and caret steps, D10's detail layout, D11's scroll service and the two
 * coordinate conversions, plus the two local-player facts the role row needs
 * (kept as facts so this shared class names no Minecraft API). Nothing here is
 * D18 state except the parked composer text.</p>
 *
 * <p>One copy, in the shared layer: this class names no Minecraft API, and the
 * mapping-layer type it drives ({@link ProfilePage}) exist at this same name on
 * every target — the seam manifest's 甲-group case. A twin pair here would be
 * byte-identical, which {@code verify_targets} rejects.</p>
 */
public final class ProfilePageWiring {
    /**
     * What the screen hands over: D18's page, D21's composer, D10/D11's layout
     * and scroll, the shell's conversions, and the two local-player facts.
     */
    public interface Host {
        /** D18's profile page: this domain feeds it and drives its signature edit. */
        ProfilePage profilePage();

        /** D21: the composer's text. */
        String composerText();

        /** D21: replaces the composer's text. */
        void setComposerText(String text);

        /**
         * D21: focuses the composer — the flag, the screen focus and the guarded
         * field focus, in that order, exactly as this borrow always did it.
         */
        void focusComposer();

        /** D21: moves the caret to the end of the composer's text. */
        void caretToEnd();

        /** D10: the detail page's layout. */
        UiLayout detailLayout();

        /** D11: the detail controller (shared across pages) and the scrollbar. */
        ScrollSyncService scrollSync();

        /** The shell's GUI-x → panel-x conversion. */
        float toVirtualX(double guiX);

        /** The shell's GUI-y → panel-y conversion. */
        float toVirtualY(double guiY);

        /**
         * The local player's id, or null when there is no player — read live at
         * every call, exactly like the old {@code client.player} check.
         */
        UUID localPlayerId();

        /** Whether the local player holds the given permission level. */
        boolean localPlayerHasPermissions(int level);

        /**
         * Whether the borrowed composer field is focused right now — the shell's
         * {@code inputFocused && input != null && input.isFocused()}.
         */
        boolean composerFocused();
    }

    private final Host host;

    /** Composer text parked while the profile page borrows the field. */
    private String signatureSavedComposer = "";

    public ProfilePageWiring(Host host) {
        this.host = host;
    }

    /**
     * The local player's permission level or a current companion answer.
     * Unknown server answers keep the row hidden.
     */
    public ProfilePage.RoleResolver roleResolver() {
        return (uuid, name) -> {
            UUID local = host.localPlayerId();
            if (uuid != null && local != null && uuid.equals(local)) {
                return host.localPlayerHasPermissions(2);
            }
            return com.atom.chat.profile.ProfileSync.operator(uuid);
        };
    }

    /**
     * The profile page's handler half: the signature borrow in and out. The page
     * is the shell's object, so these are what its handler calls.
     */
    public void beginSignatureEdit() {
        // The quick-phrase editor's borrow, pointed at the signature: park
        // the composer draft, load the current signature into the field
        // (the IME carrier) and focus it. The page flips into its editing
        // draw once the field is primed.
        signatureSavedComposer = host.composerText();
        String sig = AtomChatConfig.get().playerSignature;
        host.setComposerText(sig == null ? "" : sig);
        host.focusComposer();
        host.caretToEnd();
        host.profilePage().beginSignatureEdit();
    }

    public void endSignatureEdit(boolean commit) {
        if (commit) {
            String text = host.composerText();
            if (text.length() > 256) {
                // Same cap as a quick phrase: a longer signature could
                // never be typed within Minecraft's chat limit anyway.
                text = text.substring(0, 256);
            }
            AtomChatConfig cfg = AtomChatConfig.get();
            cfg.playerSignature = text;
            cfg.profileSignatureEdited = true;
            AtomChatConfig.save(cfg);
            com.atom.chat.profile.ProfileSync.signatureEdited(text);
        }
        host.setComposerText(signatureSavedComposer);
        host.caretToEnd();
    }

    /**
     * Feeds the signature editor the borrowed field's live text and focus
     * state, once per profile-page frame — the page draws the line but the
     * field (and the IME behind it) lives here.
     */
    public void syncProfileSignatureDraft() {
        if (host.profilePage().isSignatureEditing()) {
            boolean focused = host.composerFocused();
            host.profilePage().setSignatureDraft(host.composerText(), focused);
        }
    }

    /** Body of a pushed profile detail page (another player's profile). */
    public void drawProfileDetail(Canvas canvas, int mouseX, int mouseY) {
        UiLayout layout = host.detailLayout();
        float vmx = host.toVirtualX(mouseX);
        float vmy = host.toVirtualY(mouseY);
        host.scrollSync().detailScroll().setContent(host.profilePage().measureContent(layout), layout.list.h());
        host.scrollSync().detailScroll().updateAnimation(System.currentTimeMillis());
        syncProfileSignatureDraft();
        host.profilePage().render(canvas, layout, vmx, vmy, host.scrollSync().detailScroll().getScrollY());
        host.scrollSync().drawScrollbar(canvas, layout, vmx, vmy, host.scrollSync().detailScroll());
    }

}
