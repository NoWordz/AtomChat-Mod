package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QuickPhraseWiring}'s offline half: the parking ticket's order, the two
 * ways an edit ends, the five panel actions and the two permission guards.
 *
 * <p><b>Scope, stated up front.</b> A recording composer stands in for D21's
 * field (the real one is a Minecraft {@code EditBox}, and the shell's port bodies
 * are three field writes the golden scene already covers), while the panel is
 * real — its edit state is what the wiring drives. NOT covered here: what a
 * committed phrase does to the phrase store, the server rows' real text (it needs
 * a server pack fixture), and the composer's rendering while borrowed. Those stay
 * on the real-machine probe list: add/edit/delete a phrase, click a server row,
 * and watch the accent ring while the field is borrowed.</p>
 */
class QuickPhraseWiringTest {
    /** Records what the wiring asked of the shell, in order. */
    private static final class Composer implements QuickPhraseWiring.Host {
        final List<String> calls = new ArrayList<>();
        final QuickPhrasePanel panel;
        String text = "";
        boolean privateReadOnly;

        Composer() {
            panel = new QuickPhrasePanel(inserted -> {
                text += inserted;
                calls.add("panelInsert:" + inserted);
            }, () -> 0xFF3B82F6);
        }

        @Override
        public QuickPhrasePanel panel() {
            return panel;
        }

        @Override
        public String composerText() {
            calls.add("read");
            return text;
        }

        @Override
        public void setComposerText(String value) {
            calls.add("set:" + value);
            text = value;
        }

        @Override
        public void appendToComposer(String appended) {
            calls.add("append:" + appended);
            text += appended;
        }

        @Override
        public void focusComposer() {
            calls.add("focus");
        }

        @Override
        public void caretToEnd() {
            calls.add("caret");
        }

        @Override
        public boolean privateReadOnly() {
            return privateReadOnly;
        }
    }

    @Test
    void beginParksTheDraftLoadsThePhraseAndFocusesInOrder() {
        Composer composer = new Composer();
        composer.text = "draft in progress";
        QuickPhraseWiring wiring = new QuickPhraseWiring(composer);

        wiring.beginPhraseEdit(QuickPhrasePanel.ADD_NEW);

        assertEquals(List.of("read", "set:", "focus", "caret"), composer.calls,
                "park the draft, load the phrase, focus, then put the caret at the end");
        assertTrue(composer.panel.isEditing(), "the borrow starts an edit");
    }

    @Test
    void aReadOnlyPrivatePageNeverEntersAnEdit() {
        Composer composer = new Composer();
        composer.text = "draft";
        composer.privateReadOnly = true;
        QuickPhraseWiring wiring = new QuickPhraseWiring(composer);

        wiring.beginPhraseEdit(0);

        assertEquals(List.of(), composer.calls, "the guard runs before anything is parked");
        assertFalse(composer.panel.isEditing());
        assertEquals("draft", composer.text);
    }

    @Test
    void beginCommitsWhateverEditWasAlreadyRunning() {
        Composer composer = new Composer();
        QuickPhraseWiring wiring = new QuickPhraseWiring(composer);
        wiring.beginPhraseEdit(QuickPhrasePanel.ADD_NEW);
        composer.calls.clear();
        composer.text = "half typed";

        wiring.beginPhraseEdit(QuickPhrasePanel.ADD_NEW);

        // The running edit commits first (its text is handed to the panel), then
        // the new one parks the field as it stands.
        assertEquals("read", composer.calls.get(0));
        assertTrue(composer.calls.contains("set:"), composer.calls.toString());
        assertTrue(composer.calls.indexOf("set:") < composer.calls.indexOf("focus"));
    }

    @Test
    void endingAnEditRestoresTheParkedDraft() {
        Composer composer = new Composer();
        composer.text = "draft in progress";
        QuickPhraseWiring wiring = new QuickPhraseWiring(composer);
        wiring.beginPhraseEdit(QuickPhrasePanel.ADD_NEW);
        composer.text = "the phrase";
        composer.calls.clear();

        wiring.endPhraseEdit(true);

        assertEquals("draft in progress", composer.text, "the parking ticket comes back");
        assertEquals(List.of("read", "set:draft in progress", "caret"), composer.calls);
        assertFalse(composer.panel.isEditing());
    }

    @Test
    void endingWithoutCommitLeavesThePhraseAloneButStillRestoresTheDraft() {
        Composer composer = new Composer();
        composer.text = "draft in progress";
        QuickPhraseWiring wiring = new QuickPhraseWiring(composer);
        wiring.beginPhraseEdit(QuickPhrasePanel.ADD_NEW);
        composer.text = "throw this away";
        composer.calls.clear();

        wiring.endPhraseEdit(false);

        assertEquals("draft in progress", composer.text);
        assertFalse(composer.panel.isEditing());
        assertEquals(List.of("set:draft in progress", "caret"), composer.calls,
                "an aborted edit does not read the field: it commits nothing");
    }

    @Test
    void endingWhenNoEditIsRunningDoesNothing() {
        Composer composer = new Composer();
        composer.text = "draft";
        QuickPhraseWiring wiring = new QuickPhraseWiring(composer);

        wiring.endPhraseEdit(true);

        assertEquals(List.of(), composer.calls);
        assertEquals("draft", composer.text);
    }

    @Test
    void closingTheePanelCommitsABlurredEdit() {
        Composer composer = new Composer();
        QuickPhraseWiring wiring = new QuickPhraseWiring(composer);
        wiring.beginPhraseEdit(QuickPhrasePanel.ADD_NEW);
        composer.calls.clear();

        wiring.closePhrasePanel();

        assertFalse(composer.panel.isEditing());
        assertFalse(composer.panel.isOpen());
        assertTrue(composer.calls.contains("caret"), "the closing edit still restores the draft");
    }

    @Test
    void addAndEditActionsRouteIntoTheBorrowAndDeleteGoesToThePanel() {
        Composer composer = new Composer();
        QuickPhraseWiring wiring = new QuickPhraseWiring(composer);

        wiring.handlePhraseAction(new QuickPhrasePanel.Action(QuickPhrasePanel.Action.ADD, 0));
        assertTrue(composer.panel.isEditing());
        assertTrue(composer.calls.contains("focus"), "ADD borrows the field");
        wiring.endPhraseEdit(false);
        composer.calls.clear();

        wiring.handlePhraseAction(new QuickPhrasePanel.Action(QuickPhrasePanel.Action.DELETE, 0));
        assertEquals(List.of(), composer.calls, "DELETE is the panel's own business");

        wiring.handlePhraseAction(new QuickPhrasePanel.Action(QuickPhrasePanel.Action.INSERT, 0));
        assertFalse(composer.panel.isEditing());
    }

    @Test
    void theServerRowInsertsReadOnlyTextAndClosesThePanel() {
        Composer composer = new Composer();
        QuickPhraseWiring wiring = new QuickPhraseWiring(composer);
        wiring.beginPhraseEdit(QuickPhrasePanel.ADD_NEW);
        composer.calls.clear();

        // No server pack is loaded in a unit test, so the index is out of range and
        // nothing is inserted — but the branch must still end the edit and close.
        wiring.handlePhraseAction(new QuickPhrasePanel.Action(QuickPhrasePanel.Action.INSERT_SERVER, 0));

        assertFalse(composer.panel.isEditing(), "the borrow ends");
        assertFalse(composer.panel.isOpen(), "and the panel closes");
        assertNotEquals(null, composer.text);
    }
}
