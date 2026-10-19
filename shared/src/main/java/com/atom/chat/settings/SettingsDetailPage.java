package com.atom.chat.settings;

import com.atom.chat.avatar.ImageCropper;
import com.atom.chat.avatar.ColorPickerOverlay;
import com.atom.chat.ui.ScrollSyncService;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.input.InputHandler;
import io.github.humbleui.skija.Canvas;

/**
 * A pushed settings sub-page (D15b): its scroll state, the per-chip memory it
 * keeps, its frame, and the inline numeric editor of its integer sliders.
 *
 * <p><b>The modal contract is the reason this class exists in one piece.</b>
 * {@link ImageCropper} and {@link ColorPickerOverlay} are global modals: while
 * one is active <em>the event layer hands it every click</em>. A modal that is
 * active but never rendered is therefore not a cosmetic problem — it swallows
 * input that nothing on screen can explain, and the screen simply freezes
 * (the 0.2.x freeze). The wallpaper pick is opened <em>from this page</em>, so
 * {@link #drawSettingsSection} must keep rendering both modals on top of the
 * panel, exactly as it does now. The two calls and their comment are part of
 * the contract, not decoration.</p>
 *
 * <p>Two more things are contracts here: the per-chip scroll memory (a chip's
 * offset is saved on the way out and restored on the way in, so coming back to a
 * chip lands where it was left), and the numeric editor's key handling — digits
 * are appended from {@code onKey} and {@code onChar} deliberately swallows the
 * same press, because Minecraft delivers both and appending in both places
 * doubles every digit (1 became 11).</p>
 *
 * <p>Ports on {@link Host}: D16's section page (its state and its rendering),
 * D9's two modals and the accent token, D11's scroll service (the detail
 * controller and the scrollbar), D10's detail layout and the shell's two
 * coordinate conversions. Nothing here is D15 state except the chip memory.</p>
 *
 * <p>One copy, in the shared layer: this class names no Minecraft API at all —
 * everything it drives ({@link SettingsSectionPage}, {@link ImageCropper},
 * {@link ColorPickerOverlay}, {@link ScrollSyncService}, {@link UiLayout},
 * Skija's {@link Canvas}) is either shared or a mapping-layer type that
 * <em>every</em> target provides at this same name (the official layer for
 * forge/neo, the yarn twin beside it for fabric), which is what the seam
 * manifest calls a 甲-group type. That is why it is a single file here rather
 * than a twin pair.</p>
 */
public final class SettingsDetailPage {
    /**
     * What the screen hands over: three objects it owns (the section page or the
     * two modals), D11's scroll service, D10's detail layout and its own two
     * coordinate conversions and accent lookup. Nothing here is D15 state.
     */
    public interface Host {
        /** D16: the sub-page's own state and rendering. */
        SettingsSectionPage sectionPage();

        /** D9/D19: the HSV modal — rendered on this path, opened from the colour rows. */
        ColorPickerOverlay colorPicker();

        /** D9: the crop modal — opened from here by the wallpaper pick, so it renders here. */
        ImageCropper imageCropper();

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
        host.sectionPage().render(canvas, layout, section, vmx, vmy, host.scrollSync().detailScroll().getScrollY(),
                host.accent());
        host.scrollSync().drawScrollbar(canvas, layout, vmx, vmy, host.scrollSync().detailScroll());
        // Modal overlays on top of the whole panel: the cropper must render on
        // this path too — it is opened from here (wallpaper pick), and an
        // active-but-unrendered modal eats every click and freezes the screen.
        host.imageCropper().render(canvas, layout.rect(), vmx, vmy);
        host.colorPicker().render(canvas, layout.rect(), vmx, vmy, host.accent());
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
