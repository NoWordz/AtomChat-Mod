package com.atom.chat.ui;

import com.atom.chat.api.ui.ComposerUi;
import com.atom.chat.api.ui.UiContributions;
import com.atom.chat.onboarding.TutorialSandbox;
import com.atom.lib.design.atomchat.UiTokens;

import java.util.ArrayList;
import java.util.List;

/** Shared geometry for the input bar's button row and every wrapped continuation row. */
public final class ComposerButtonLayout {
    private static final ComposerUi.Button SQUARE = new ComposerUi.Button(TablerIcons.PHOTO, context -> {}, ComposerUi.ButtonShape.SQUARE);
    private static final ComposerUi.Button CAPSULE = new ComposerUi.Button(TablerIcons.PHOTO, context -> {}, ComposerUi.ButtonShape.CAPSULE);
    private static final List<UiContributions.Entry<ComposerUi.Button>> DEFAULTS = List.of(
            new UiContributions.Entry<>(ComposerUi.TOOLS_BUTTON, SQUARE),
            new UiContributions.Entry<>(ComposerUi.EMOJI_BUTTON, SQUARE),
            new UiContributions.Entry<>(ComposerUi.QUICK_PHRASES_BUTTON, SQUARE),
            new UiContributions.Entry<>(ComposerUi.SEND_BUTTON, CAPSULE));

    private ComposerButtonLayout() {}

    public record Slot(String id, ComposerUi.Button button, UiLayout.Rect rect) {
        public boolean builtIn() {
            return button == SQUARE || button == CAPSULE;
        }
    }

    record Band(List<Slot> slots, float extraHeight) {}

    static Band resolve(float width, ComposerUi.Spacing spacing) {
        List<UiContributions.Entry<ComposerUi.Button>> buttons = TutorialSandbox.active()
                ? DEFAULTS : ComposerUi.buttons().resolve(DEFAULTS);
        return measure(buttons, width, spacing.gap());
    }

    static Band measure(List<UiContributions.Entry<ComposerUi.Button>> buttons, float width, float gap) {
        if (buttons.isEmpty()) return new Band(List.of(), -UiTokens.INPUT_ROW_H);
        List<Slot> slots = new ArrayList<>(buttons.size());
        float cursor = 0;
        int row = 0;
        for (int i = 0; i < buttons.size(); i++) {
            var entry = buttons.get(i);
            var button = entry.value();
            boolean capsule = button.shape() == ComposerUi.ButtonShape.CAPSULE;
            boolean trailingSend = i == buttons.size() - 1 && entry.id().equals(ComposerUi.SEND_BUTTON);
            float naturalWidth = capsule ? UiTokens.BUTTON_W : UiTokens.ACTION_BUTTON_SIZE;
            float height = capsule ? UiTokens.BUTTON_H : UiTokens.ACTION_BUTTON_SIZE;
            float buttonWidth = Math.min(naturalWidth, width);
            float minimumWidth = trailingSend ? Math.min(UiTokens.ACTION_BUTTON_SIZE, width) : buttonWidth;
            if (cursor > 0 && (cursor + minimumWidth > width || (trailingSend && row > 0))) {
                row++;
                cursor = 0;
            }
            float left = cursor;
            if (trailingSend) {
                buttonWidth = Math.min(buttonWidth, Math.max(0, width - cursor));
                left = width - buttonWidth;
            }
            float y = row * (UiTokens.INPUT_ROW_H + gap) + (UiTokens.INPUT_ROW_H - height) / 2;
            slots.add(new Slot(entry.id(), button, new UiLayout.Rect(left, y, buttonWidth, height)));
            cursor = left + buttonWidth + gap;
        }
        return new Band(List.copyOf(slots), row * (UiTokens.INPUT_ROW_H + gap));
    }
}
