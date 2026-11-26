package com.atom.chat.settings;

import com.atom.chat.ui.ScrollSyncService;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.input.InputHandler;
import io.github.humbleui.skija.Canvas;

/**
 * A pushed settings sub-page (D15b): its scroll state, the per-chip memory it
 * keeps, its frame, and the inline numeric editor of its integer sliders.
 *
 * <p>Each chip retains its scroll offset. The number editor adds digits from
 * onKey and consumes the corresponding character event to avoid duplicate input.
 */
public final class SettingsDetailPage {
    public interface Host {
        SettingsSectionPage sectionPage();

        /** D11: the detail controller (shared across chips) and the scrollbar. */
        ScrollSyncService scrollSync();

        /** D10: the detail page's layout. */
        UiLayout detailLayout();

        /** D9: the theme's accent, for the scrollbar. */
        int accent();

        /** The shell's GUI-x → panel-x conversion. */
        float toVirtualX(double guiX);

        /** The shell's GUI-y → panel-y conversion. */
        float toVirtualY(double guiY);

    }

    private final Host host;

    /** Per-chip scroll memory ("section:chip" -> offset); the detail
     *  controller is shared across chips. */
    private final java.util.HashMap<String, Float> chipScroll = new java.util.HashMap<>();
    private String chipScrollKey = "";

    public SettingsDetailPage(Host host) {
        this.host = host;
    }

    /** Clears the transient state of a settings sub-page when leaving it. */
    public void resetSettingsUi() {
        host.scrollSync().detailScroll().reset();
        host.sectionPage().reset();
        chipScroll.clear();
        chipScrollKey = "";
    }

    /** Saves the outgoing chip's scroll offset and restores the incoming one,
     *  so each chip of a section page remembers where the user left it. */
    private void syncChipScroll(SettingsSection section) {
        String key = section.id() + ':' + host.sectionPage().activeChipIndex(section);
        if (!key.equals(chipScrollKey)) {
            if (!chipScrollKey.isEmpty()) {
                chipScroll.put(chipScrollKey, host.scrollSync().detailScroll().getScrollY());
            }
            chipScrollKey = key;
            Float saved = chipScroll.get(key);
            host.scrollSync().detailScroll().scrollTo(saved == null ? 0.0F : saved, false);
        }
    }

    /**
     * Body of a pushed settings sub-page: scroll state, then the card list, then
     * the scrollbar. Deliberately header-free — the shell header (with the back
     * affordance) is drawn fixed on top so it never slides with the list.
     */
    public void drawSettingsSection(Canvas canvas, int mouseX, int mouseY, SettingsSection section) {
        if (section == null) {
            return;
        }
        UiLayout layout = host.detailLayout();
        float vmx = host.toVirtualX(mouseX);
        float vmy = host.toVirtualY(mouseY);
        host.scrollSync().detailScroll().setContent(host.sectionPage().measureContent(layout, section), layout.list.h());
        syncChipScroll(section);
        host.scrollSync().detailScroll().updateAnimation(System.currentTimeMillis());
        host.sectionPage().renderBody(canvas, layout, section, vmx, vmy, host.scrollSync().detailScroll().getScrollY(),
                host.accent());
        host.scrollSync().drawScrollbar(canvas, layout, vmx, vmy, host.scrollSync().detailScroll());
    }

    private record RowPointerId(SettingsSection section, int chip, SettingsSectionPage.RowKind kind, Object action) {}

    /** Resolves against the settings row and action rectangle painted by the current page. */
    public static com.atom.chat.ui.input.ClickTarget rowClickTarget(SettingsSectionPage page, SettingsSection section,
                                                                   UiLayout layout, float scroll, float x, float y,
                                                                   Runnable feedback) {
        if (!layout.list.contains(x, y)) return null;
        var hit = page.hit(x, y, layout, section, scroll);
        if (hit == null || !hit.onAction(x, y)) return null;
        var row = hit.row();
        Object action = switch (row.kind()) {
            case SWITCH -> row.item().available() ? row.item().id() : null;
            case ACTION -> row.item().available() ? row.actionId() : null;
            case INFO -> row.info().isLink() ? row.info() : null;
            case LABEL -> row.labelKey();
            case BLOCKED -> row.player();
            default -> null;
        };
        if (action == null) return null;
        var id = new RowPointerId(section, page.activeChipIndex(section), row.kind(), action);
        return new com.atom.chat.ui.input.ClickTarget(id,
                () -> page.setPressedRow(hit.index()), () -> page.setPressedRow(-1), () -> {
                    if (page.perform(hit) && !page.historyOwnsFeedback(hit)) feedback.run();
                });
    }

    /** The inline numeric editor's handler, for the shell's input router. */
    public InputHandler numberEditInput() {
        return new NumberEditInput();
    }

    /** Inline numeric editor for integer sliders. */
    private final class NumberEditInput implements InputHandler {
        @Override
        public boolean onClick(double mouseX, double mouseY, int button) {
            if (!host.sectionPage().isEditingNumber()) {
                return false;
            }
            float mx = host.toVirtualX(mouseX);
            float my = host.toVirtualY(mouseY);
            if (host.sectionPage().isInsideNumberEditRow(mx, my)) {
                return true;
            }
            // Click outside the editor commits the typed value and lets the
            // click continue to whatever the user actually aimed at.
            host.sectionPage().commitNumberEdit();
            return false;
        }

        @Override
        public boolean onKey(int keyCode, int scanCode, int modifiers) {
            if (!host.sectionPage().isEditingNumber()) {
                return false;
            }
            if (keyCode == 256) { // Esc: cancel
                host.sectionPage().cancelNumberEdit();
            } else if (keyCode == 257 || keyCode == 335) { // Enter
                host.sectionPage().commitNumberEdit();
            } else if (keyCode == 259) { // Backspace
                host.sectionPage().backspaceNumber();
            } else if (keyCode >= 48 && keyCode <= 57) { // top-row digits
                host.sectionPage().appendNumberChar((char) ('0' + keyCode - 48));
            } else if (keyCode >= 320 && keyCode <= 329) { // numpad digits
                host.sectionPage().appendNumberChar((char) ('0' + keyCode - 320));
            }
            return true;
        }

        @Override
        public boolean onChar(char chr, int modifiers) {
            // Digits are appended from onKey. Minecraft also delivers a char
            // callback for the same press; appending here would double every
            // digit (1 became 11). Swallow the char so it never reaches chat.
            return host.sectionPage().isEditingNumber();
        }

        @Override
        public boolean onScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            return host.sectionPage().isEditingNumber();
        }
    }
}
