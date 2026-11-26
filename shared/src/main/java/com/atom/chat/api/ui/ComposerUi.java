package com.atom.chat.api.ui;

import io.github.humbleui.skija.Path;

import java.util.Objects;
import java.util.function.Consumer;

/** Preview extension points for the AtomChat composer. */
public final class ComposerUi {
    public static final String SEARCH = "atomchat:search";
    public static final String PLAYERS = "atomchat:players";
    public static final String IMAGE = "atomchat:image";
    public static final String SHARE_LOCATION = "atomchat:share_location";
    public static final String SHARE_ITEM = "atomchat:share_item";

    public static final String TOOLS_BUTTON = "atomchat:tools";
    public static final String EMOJI_BUTTON = "atomchat:emoji";
    public static final String QUICK_PHRASES_BUTTON = "atomchat:quick_phrases";
    public static final String SEND_BUTTON = "atomchat:send";
    public static final String SPACING = "atomchat:composer_spacing";

    private static final UiContributions<MenuItem> MENU = new UiContributions<>();
    private static final UiContributions<Button> BUTTONS = new UiContributions<>();
    private static final UiContributions<Spacing> STYLES = new UiContributions<>();
    private static final Spacing DEFAULT_SPACING = new Spacing(
            com.atom.lib.design.atomchat.UiTokens.COMPOSER_KEY_GAP,
            com.atom.lib.design.atomchat.UiTokens.EDGE_CONTROL_INSET,
            com.atom.lib.design.atomchat.UiTokens.INPUT_ROW_PAD);
    private static final java.util.List<UiContributions.Entry<Spacing>> DEFAULT_STYLES =
            java.util.List.of(new UiContributions.Entry<>(SPACING, DEFAULT_SPACING));

    private ComposerUi() {}

    /** The host's text operations for the active conversation, valid during an action. */
    public interface Context {
        String draft();
        void replaceDraft(String text);
        void insertText(String text);
        void submitDraft();
        void openPage(String pageId);
    }

    /** The add-on retains ownership of the icon; it must remain open while registered. */
    public record MenuItem(String labelKey, Path icon, Consumer<Context> action, boolean keepOpen) {
        public MenuItem(String labelKey, Path icon, Consumer<Context> action) {
            this(labelKey, icon, action, false);
        }

        public MenuItem {
            Objects.requireNonNull(labelKey);
            Objects.requireNonNull(icon);
            Objects.requireNonNull(action);
        }
    }

    public enum ButtonShape { SQUARE, CAPSULE }

    /** Custom button icons are borrowed from the add-on for the registration lifetime. */
    public record Button(Path icon, Consumer<Context> action, ButtonShape shape) {
        public Button {
            Objects.requireNonNull(icon);
            Objects.requireNonNull(action);
            Objects.requireNonNull(shape);
        }
    }

    public record Spacing(float gap, float leadingInset, float trailingInset) {
        public Spacing {
            if (!Float.isFinite(gap) || !Float.isFinite(leadingInset) || !Float.isFinite(trailingInset)
                    || gap < 0 || leadingInset < 0 || trailingInset < 0) {
                throw new IllegalArgumentException("Composer spacing must be finite and non-negative");
            }
        }
    }

    /** The ordered button slots above the native text editor. */
    public static UiContributions<Button> buttons() {
        return BUTTONS;
    }

    /** Replace {@link #SPACING} to style the composer globally. Values use virtual UI units. */
    public static UiContributions<Spacing> styles() {
        return STYLES;
    }

    public static boolean hasButtonLayoutEdits() {
        return (BUTTONS.hasEdits() || STYLES.hasEdits()) && !com.atom.chat.onboarding.TutorialSandbox.active();
    }

    public static Spacing defaultSpacing() {
        return DEFAULT_SPACING;
    }

    public static Spacing spacing() {
        if (com.atom.chat.onboarding.TutorialSandbox.active()) return DEFAULT_SPACING;
        for (var entry : STYLES.resolve(DEFAULT_STYLES)) {
            if (entry.id().equals(SPACING)) return entry.value();
        }
        return DEFAULT_SPACING;
    }

    /** The tool popup's main menu. The player-picker sub-page owns its online entries. */
    public static UiContributions<MenuItem> menu() {
        return MENU;
    }
}
