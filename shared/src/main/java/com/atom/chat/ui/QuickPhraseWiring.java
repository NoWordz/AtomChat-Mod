package com.atom.chat.ui;

import com.atom.chat.pack.ServerPackStore;

import java.util.List;

/**
 * Borrowing the composer for a quick phrase (D16): the parked text, the
 * add/edit lifecycle and what each panel action does to the screen.
 *
 * <p>The composer field is shared — D21 owns it, the signature editor borrows it
 * (D18) and this panel borrows it too — so the borrow is a <b>parking ticket</b>:
 * whatever the field held is saved on the way in, the phrase text replaces it
 * (that field is the IME carrier: typing has to land somewhere real), the field
 * is focused with the caret at the end, and on the way out the parked text and
 * the caret come back exactly as they were. The order of those writes is the
 * contract: focus is one port call that does the flag, the screen focus and the
 * field focus in that sequence, and the caret is moved after it — the same four
 * steps the shell used to spell out inline.</p>
 *
 * <p>Two permissions are contracts as well: a read-only private page never
 * enters an edit at all, and the server's rows are read-only — they insert their
 * text and close the panel, with no edit or delete path behind them.</p>
 *
 * <p>Ports on {@link Host}: the panel itself (its construction stays in the
 * shell, because it feeds on D21's composer and D9's accent), D21's composer
 * reads and writes, the focus step, the caret step and D24's read-only test.
 * Nothing here is D16 state except the parked composer text.</p>
 *
 * <p>One copy, in the shared layer: this class names no Minecraft API, and the
 * mapping-layer type it drives ({@link QuickPhrasePanel}) exists at this same
 * name on every target — the seam manifest's 甲-group case. A twin pair here
 * would be byte-identical, which is exactly what {@code verify_targets} rejects.</p>
 */
public final class QuickPhraseWiring {
    /**
     * What the screen hands over: the panel it built, D21's composer, the focus
     * and caret steps, and D24's read-only test.
     */
    public interface Host {
        /** D17's panel: this domain drives its edit lifecycle, the shell renders it. */
        QuickPhrasePanel panel();

        /** D21: the composer's text. */
        String composerText();

        /** D21: replaces the composer's text. */
        void setComposerText(String text);

        /** D21: appends text at the caret (the server rows' insert path). */
        void appendToComposer(String text);

        /**
         * D21: focuses the composer — the flag, the screen focus and the field
         * focus, in that order, exactly as the borrow always did it.
         */
        void focusComposer();

        /** D21: moves the caret to the end of the composer's text. */
        void caretToEnd();

        /** D24: true on a private page whose partner can no longer be written to. */
        boolean privateReadOnly();
    }

    private final Host host;

    /** Composer text saved while the quick-phrase panel borrows the field. */
    private String phraseSavedComposer = "";

    public QuickPhraseWiring(Host host) {
        this.host = host;
    }

    /** Begins adding ({@link QuickPhrasePanel#ADD_NEW}) or editing a phrase. */
    public void beginPhraseEdit(int target) {
        if (host.privateReadOnly()) {
            return;
        }
        if (host.panel().isEditing()) {
            endPhraseEdit(true);
        }
        phraseSavedComposer = host.composerText();
        if (target == QuickPhrasePanel.ADD_NEW) {
            host.setComposerText("");
            host.panel().startAdd();
        } else {
            host.setComposerText(host.panel().phraseAt(target));
            host.panel().startEdit(target);
        }
        host.focusComposer();
        host.caretToEnd();
    }

    /** Ends a phrase add/edit; commit=true saves the field text as the phrase. */
    public void endPhraseEdit(boolean commit) {
        if (!host.panel().isEditing()) {
            return;
        }
        if (commit) {
            host.panel().commitText(host.composerText());
        } else {
            host.panel().abortEdit();
        }
        host.setComposerText(phraseSavedComposer);
        host.caretToEnd();
    }

    /** Closes the phrase panel; an in-progress edit commits (blur applies). */
    public void closePhrasePanel() {
        if (host.panel().isEditing()) {
            endPhraseEdit(true);
        }
        host.panel().close();
    }

    /** Turns a panel click into the screen-level side effects. */
    public void handlePhraseAction(QuickPhrasePanel.Action action) {
        switch (action.type()) {
            case QuickPhrasePanel.Action.ADD -> beginPhraseEdit(QuickPhrasePanel.ADD_NEW);
            case QuickPhrasePanel.Action.EDIT -> beginPhraseEdit(action.index());
            case QuickPhrasePanel.Action.DELETE -> host.panel().deleteAt(action.index());
            case QuickPhrasePanel.Action.INSERT -> {
                endPhraseEdit(true);
                host.panel().pickRow(action.index());
            }
            case QuickPhrasePanel.Action.INSERT_SERVER -> {
                // The server's rows are read-only: they insert text and close,
                // with no edit or delete path behind them.
                endPhraseEdit(true);
                List<String> serverPhrases = ServerPackStore.current().phrases();
                if (action.index() >= 0 && action.index() < serverPhrases.size()) {
                    host.appendToComposer(serverPhrases.get(action.index()));
                }
                host.panel().close();
            }
            default -> {
            }
        }
    }
}
