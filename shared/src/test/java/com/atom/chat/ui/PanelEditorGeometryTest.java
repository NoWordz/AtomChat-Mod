package com.atom.chat.ui;

import com.atom.lib.design.atomchat.UiTokens;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PanelEditorGeometryTest {
    @Test void eightResizePointsAndTheMoveCapsuleHaveDifferentHitRegions() {
        var panel = new UiLayout.Rect(100, 100, 300, 250);
        var grips = PanelEditorGeometry.grips(panel);
        assertEquals(8, grips.size());
        assertEquals(8, grips.stream().map(PanelEditorGeometry.Grip::handle).distinct().count());
        var move = PanelEditorGeometry.moveHandle(panel, 900);
        assertTrue(move.bottom() < panel.y());
        for (var grip : grips) assertFalse(grip.bounds().contains(move.x()+move.w()/2, move.y()+move.h()/2));
        var docked = new UiLayout.Rect(100, 0, 300, 690);
        var inside = PanelEditorGeometry.moveHandle(docked, 900);
        assertTrue(inside.y() > 0);
        assertTrue(docked.contains(inside));
        assertTrue(inside.y() > UiTokens.EDGE_CONTROL_INSET*2, "inside capsule stays clear of north resize");
    }
    @Test void tallPanelsCannotCollapseNineAnchorsIntoThreeClusters() {
        var anchors = PanelEditorGeometry.anchors(900, 700);
        assertEquals(9, anchors.size());
        assertEquals(9, anchors.stream().distinct().count());
        assertTrue(anchors.get(3).y()-anchors.get(0).y() > 200);
        assertTrue(anchors.get(6).y()-anchors.get(3).y() > 200);
    }
    @Test void everyDirectionKeepsTheOppositeEdgesFixed() {
        var expected = java.util.Map.of(
                "NORTH_WEST", new float[]{120,110,280,240}, "NORTH", new float[]{100,110,300,240},
                "NORTH_EAST", new float[]{100,110,320,240}, "WEST", new float[]{120,100,280,250},
                "EAST", new float[]{100,100,320,250}, "SOUTH_WEST", new float[]{120,100,280,260},
                "SOUTH", new float[]{100,100,300,260}, "SOUTH_EAST", new float[]{100,100,320,260});
        expected.forEach((name, rect) -> {
            var model = new PanelEditorModel(100,100,300,250,900,700);
            model.begin(PanelEditorModel.Handle.valueOf(name),300,200);
            model.drag(320,210,900,700);
            assertArrayEquals(rect,new float[]{model.x(),model.y(),model.width(),model.height()},.001F,name);
        });
    }
}
