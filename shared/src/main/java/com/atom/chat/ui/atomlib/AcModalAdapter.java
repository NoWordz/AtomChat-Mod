// SPDX-License-Identifier: MIT
package com.atom.chat.ui.atomlib;

import com.atom.lib.view.UiView;
import java.util.function.BooleanSupplier;

/** Borrows AC's exact animated visibility source; never owns another modal flag. */
public final class AcModalAdapter {
    private final UiView view;
    private final BooleanSupplier visible;
    private boolean hostActive=true,blocking;
    public AcModalAdapter(UiView view,BooleanSupplier visible) {
        this.view=java.util.Objects.requireNonNull(view);this.visible=java.util.Objects.requireNonNull(visible);
    }
    public void open(Runnable show) {
        java.util.Objects.requireNonNull(show);
        // Suspend and cancel Lib's live gesture before AC changes the modal flag.
        // The dialog remains AC's single visibility/animation source of truth.
        view.suspendInput(true);blocking=true;
        show.run();sync();
    }
    public void sync() {
        boolean next=!hostActive||visible.getAsBoolean();
        if(next!=blocking){
            view.suspendInput(next);blocking=next;
            // The host may have replaced its root while the dialog covered it.
            // Re-resolve the retained focus id against live controls on resume.
            if(!blocking)view.refresh();
        }
    }
    public void setHostActive(boolean active) { hostActive=active;sync(); }
}
