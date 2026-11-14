package com.atom.chat.ui;

import com.atom.lib.design.atomchat.UiTokens;
import java.util.List;

/** One source for editor handles and fixed, non-overlapping viewport anchor marks. */
public final class PanelEditorGeometry {
    public record Grip(PanelEditorModel.Handle handle, float x, float y) {
        public UiLayout.Rect bounds() {
            float size = UiTokens.EDGE_CONTROL_INSET * 4;
            return new UiLayout.Rect(x - size / 2, y - size / 2, size, size);
        }
    }
    public record Anchor(float x, float y) { }
    private PanelEditorGeometry() { }

    public static List<Grip> grips(UiLayout.Rect b) {
        return List.of(
                new Grip(PanelEditorModel.Handle.NORTH_WEST, b.x(), b.y()),
                new Grip(PanelEditorModel.Handle.NORTH, b.x() + b.w()/2, b.y()),
                new Grip(PanelEditorModel.Handle.NORTH_EAST, b.right(), b.y()),
                new Grip(PanelEditorModel.Handle.WEST, b.x(), b.y() + b.h()/2),
                new Grip(PanelEditorModel.Handle.EAST, b.right(), b.y() + b.h()/2),
                new Grip(PanelEditorModel.Handle.SOUTH_WEST, b.x(), b.bottom()),
                new Grip(PanelEditorModel.Handle.SOUTH, b.x() + b.w()/2, b.bottom()),
                new Grip(PanelEditorModel.Handle.SOUTH_EAST, b.right(), b.bottom()));
    }

    public static UiLayout.Rect moveHandle(UiLayout.Rect panel, float viewportWidth) {
        float height = UiTokens.COMPOSER_ICON_SIZE;
        float width = Math.min(height * 3, Math.max(height, panel.w() - UiTokens.EDGE_CONTROL_INSET * 4));
        float gap = UiTokens.EDGE_CONTROL_INSET * 3;
        float x = Math.max(0, Math.min(panel.x() + (panel.w() - width)/2, viewportWidth - width));
        float outside = panel.y() - gap - height;
        return new UiLayout.Rect(x, outside >= 0 ? outside : panel.y() + gap, width, height);
    }

    public static List<Anchor> anchors(float width, float height) {
        float margin = UiTokens.PANEL_TOP_GAP;
        var points = new java.util.ArrayList<Anchor>(9);
        for (float y : new float[]{margin, height/2, height-margin}) {
            for (float x : new float[]{margin, width/2, width-margin}) points.add(new Anchor(x, y));
        }
        return List.copyOf(points);
    }
}
