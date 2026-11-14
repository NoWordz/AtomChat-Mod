package com.atom.chat.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PanelEditorModelTest {
    @Test void movingAndResizingStayInsideViewport() {
        var m = new PanelEditorModel(100, 80, 300, 240, 800, 600);
        m.begin(PanelEditorModel.Handle.MOVE, 100, 80);
        m.drag(1000, 1000, 800, 600);
        assertEquals(500, m.x(), 0.01);
        assertEquals(360, m.y(), 0.01);
        m.end();
        m.begin(PanelEditorModel.Handle.NORTH_WEST, m.x(), m.y());
        m.drag(550, 400, 800, 600);
        assertEquals(250, m.width(), 0.01);
        assertEquals(200, m.height(), 0.01);
        assertEquals(800, m.x() + m.width(), 0.01);
        assertEquals(600, m.y() + m.height(), 0.01);
    }

    @Test void viewportChangeClampsWithoutChangingContentScale() {
        var m = new PanelEditorModel(500, 300, 300, 240, 800, 600);
        m.contentScale(1.3F);
        m.viewport(400, 300);
        assertTrue(m.x() + m.width() <= 400.01F);
        assertTrue(m.y() + m.height() <= 300.01F);
        assertEquals(1.3F, m.contentScale(), 0.001F);
    }
    @Test void nearbyPositionSnapsToHudStyleAnchor() {
        var m = new PanelEditorModel(248, 181, 300, 240, 800, 600);
        m.snap(800,600,5);
        assertEquals(250,m.x(),.01F);
        assertEquals(180,m.y(),.01F);
    }
    @Test void northResizeKeepsTheBottomAndBothSideEdgesFixed() {
        var model = new PanelEditorModel(100, 100, 300, 250, 900, 700);
        model.begin(PanelEditorModel.Handle.valueOf("NORTH"), 250, 100);
        model.drag(290, 70, 900, 700);
        assertEquals(100, model.x(), .001F);
        assertEquals(300, model.width(), .001F);
        assertEquals(70, model.y(), .001F);
        assertEquals(350, model.y() + model.height(), .001F);
    }
}
