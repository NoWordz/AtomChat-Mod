package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.types.Rect;
import java.util.List;
import java.util.function.Function;

/** Edits the real panel rectangle; content zoom remains a separate setting. */
public final class PanelEditor {
    public enum Result { PASS, CONSUMED, SAVE }

    private final AtomChatConfig config;
    private final Function<String, String> translate;
    private final float oldWidth, oldHeight, oldX, oldY;
    private final boolean oldCustom, oldHalf, oldAutomatic, oldWidthAutomatic;
    private final UiLayout.Rect initial;
    private final ConfirmationSurface.Button[] buttons = new ConfirmationSurface.Button[2];
    private PanelEditorModel model;
    private final PressScale movePress = PressScale.bounce();
    private final PressScale[] gripPress = new PressScale[8];
    private float moveHover;
    private float progress;
    private boolean closing, committed;
    private boolean movingToolbar, toolbarCustom;
    private float toolbarX, toolbarY, pointerStartX, pointerStartY, toolbarStartX, toolbarStartY;

    public PanelEditor(AtomChatConfig config, Function<String, String> translate,
                       float x, float y, float w, float h, float vw, float vh) {
        this.config = config;
        this.translate = translate;
        oldWidth = config.panelWidth;
        oldHeight = config.panelHeight;
        oldX = config.panelPositionX;
        oldY = config.panelPositionY;
        oldCustom = config.panelPositionCustom;
        oldHalf = config.halfScreenMode;
        oldAutomatic = config.panelHeightAutomatic;
        oldWidthAutomatic = config.panelWidthAutomatic;
        float zoom = zoom();
        initial = new UiLayout.Rect(x * zoom, y * zoom, w * zoom, h * zoom);
        model = new PanelEditorModel(initial.x(), initial.y(), initial.w(), initial.h(), vw * zoom, vh * zoom);
        for (int i = 0; i < buttons.length; i++) buttons[i] = new ConfirmationSurface.Button();
        for (int i = 0; i < gripPress.length; i++) gripPress[i] = PressScale.bounce();
        config.halfScreenMode = false;
        sync(vw * zoom, vh * zoom);
    }

    private float zoom() { return Math.max(.8F, Math.min(1.5F, config.contentScale)); }
    private UiLayout.Rect box() { return new UiLayout.Rect(model.x(), model.y(), model.width(), model.height()); }
    public boolean closing() { return closing; }
    public boolean closed() { return closing && progress <= .001F; }

    /** The screen reports its real painted rectangle after settings or viewport changes. */
    public void followPanel(float x, float y, float width, float height, float vw, float vh) {
        if (closing || model.dragging()) return;
        float zoom = zoom();
        model = new PanelEditorModel(x * zoom, y * zoom, width * zoom, height * zoom, vw * zoom, vh * zoom);
    }

    private PanelEditorLayout layout(float width, float height) {
        var layout = PanelEditorLayout.of(width, height, box());
        if (!toolbarCustom) return layout;
        float x = Math.max(0, Math.min(toolbarX, width - layout.toolbar().w()));
        float y = Math.max(0, Math.min(toolbarY, height - layout.toolbar().h()));
        return layout.at(x, y);
    }

    private void sync(float width, float height) {
        model.viewport(width, height);
        config.halfScreenMode = false;
        config.panelWidth = model.width() / UiTokens.SCALE;
        config.panelHeight = model.height() / UiTokens.SCALE;
        config.panelHeightAutomatic = false;
        config.panelWidthAutomatic = false;
        config.panelPositionCustom = true;
        config.panelPositionX = width > model.width() ? model.x() / (width - model.width()) : 0;
        config.panelPositionY = height > model.height() ? model.y() / (height - model.height()) : 0;
    }

    public void cancel() {
        if (committed) return;
        config.panelWidth = oldWidth;
        config.panelHeight = oldHeight;
        config.panelPositionX = oldX;
        config.panelPositionY = oldY;
        config.panelPositionCustom = oldCustom;
        config.halfScreenMode = oldHalf;
        config.panelHeightAutomatic = oldAutomatic;
        config.panelWidthAutomatic = oldWidthAutomatic;
        AtomChatConfig.save(config);
    }

    public boolean save() {
        committed = AtomChatConfig.saveChecked(config);
        return committed;
    }

    /** Persistence already succeeded; finish without another save or an old-snapshot rollback. */
    public void finishAfterSettingsReset() {
        committed = true;
        model.end(); movingToolbar = false; closing = true;
    }

    public boolean requestClose(boolean save) {
        if (closing) return true;
        if (save && !save()) return false;
        model.end();
        closing = true;
        return true;
    }

    private List<PanelEditorGeometry.Grip> grips() { return PanelEditorGeometry.grips(box()); }

    private PanelEditorModel.Handle borderAt(float x, float y) {
        for (var grip : grips()) if (grip.bounds().contains(x, y)) return grip.handle();
        var b = box();
        float band = UiTokens.EDGE_CONTROL_INSET * 2;
        boolean alongWidth = x >= b.x() && x <= b.right();
        boolean alongHeight = y >= b.y() && y <= b.bottom();
        if (alongWidth && Math.abs(y - b.y()) <= band) return PanelEditorModel.Handle.NORTH;
        if (alongHeight && Math.abs(x - b.x()) <= band) return PanelEditorModel.Handle.WEST;
        if (alongHeight && Math.abs(x - b.right()) <= band) return PanelEditorModel.Handle.EAST;
        if (alongWidth && Math.abs(y - b.bottom()) <= band) return PanelEditorModel.Handle.SOUTH;
        return null;
    }

    public void render(Canvas canvas, float vw, float vh) { render(canvas, vw, vh, -1, -1, 16); }

    public void render(Canvas canvas, float vw, float vh, float mx, float my, float dt) {
        float zoom = zoom();
        model.viewport(vw * zoom, vh * zoom);
        progress = UiMotion.approach(progress, closing ? 0 : 1, dt, Animations.ms(UiMotion.POPUP_MS));
        int saved = canvas.save();
        try (var opacity = new Paint().setAlphaf(progress)) {
            canvas.scale(1 / zoom, 1 / zoom);
            canvas.saveLayer(Rect.makeWH(vw * zoom, vh * zoom), opacity);
            draw(canvas, vw * zoom, vh * zoom, mx * zoom, my * zoom, dt);
        } finally { canvas.restoreToCount(saved); }
    }

    private void draw(Canvas canvas, float width, float height, float mx, float my, float dt) {
        var b = box();
        try (var guide = new Paint().setAntiAlias(true).setColor(config.accentColor)
                .setStrokeWidth(UiTokens.SCALE).setMode(PaintMode.STROKE)) {
            canvas.drawRect(Rect.makeXYWH(b.x(), b.y(), b.w(), b.h()), guide);
            guide.setColor(0x667AC8FF);
            canvas.drawLine(width/2, 0, width/2, height, guide);
            canvas.drawLine(0, height/2, width, height/2, guide);
            var anchors = PanelEditorGeometry.anchors(width, height);
            int nearest = model.nearestAnchor(width, height, UiTokens.PANEL_TOP_GAP);
            for (int i = 0; i < anchors.size(); i++) {
                guide.setColor(model.moving() && i == nearest ? config.accentColor : 0x667AC8FF);
                var anchor = anchors.get(i);
                canvas.drawCircle(anchor.x(), anchor.y(), UiTokens.EDGE_CONTROL_INSET, guide);
            }
        }
        var hovered = borderAt(mx, my);
        var grips = grips();
        for (int i = 0; i < grips.size(); i++) {
            var grip = grips.get(i);
            var r = grip.bounds();
            gripPress[i].update(hovered == grip.handle(), model.activeHandle() == grip.handle(), dt, Animations.enabled(), r.w());
            gripPress[i].begin(canvas, grip.x(), grip.y());
            try { SkiaDraw.drawRoundedRect(canvas, r.x(), r.y(), r.w(), r.h(), UiTokens.EDGE_CONTROL_INSET, config.accentColor); }
            finally { canvas.restore(); }
        }
        var move = PanelEditorGeometry.moveHandle(b, width);
        moveHover = UiMotion.approach(moveHover, move.contains(mx, my) ? 1 : 0, dt, UiMotion.HOVER_MS);
        movePress.update(move.contains(mx, my), model.moving(), dt, Animations.enabled(), move.w());
        movePress.begin(canvas, move.x() + move.w()/2, move.y() + move.h()/2);
        try {
            SkiaDraw.drawRoundedRect(canvas, move.x(), move.y(), move.w(), move.h(), move.h()/2, config.accentColor);
            if (moveHover > 0) SkiaDraw.drawRoundedRect(canvas, move.x(), move.y(), move.w(), move.h(), move.h()/2,
                    io.github.humbleui.skija.Color.makeARGB((int)(60 * moveHover), 255, 255, 255));
            com.atom.chat.render.DrawPrimitives.drawIconCentered(canvas, EditorActionIcons.MOVE,
                    move.x() + move.w()/2, move.y() + move.h()/2, move.h()*.6F, UiTokens.onAccent(config.accentColor));
        } finally { canvas.restore(); }
        var layout = layout(width, height);
        var toolbar = layout.toolbar();
        ConfirmationSurface.surface(canvas, toolbar, 1);
        var font = FontManager.font(UiTokens.FONT_BUTTON);
        SkiaFontRenderer.drawTextCentered(canvas, font,
                SkiaFontRenderer.truncate(font, text("hint"), layout.title().w()),
                layout.title().x() + layout.title().w() / 2, layout.title().y() + layout.title().h() / 2, 0xFFFFFFFF);
        drawButton(canvas, 0, layout.reset(), "reset", false, mx, my, dt);
        drawButton(canvas, 1, layout.save(), "save", true, mx, my, dt);
    }

    private String text(String key) { return translate.apply("atomchat.panel_editor." + key); }
    private void drawButton(Canvas canvas, int index, UiLayout.Rect rect, String key,
                            boolean primary, float mx, float my, float dt) {
        buttons[index].draw(canvas, rect, text(key), primary, false, false, rect.contains(mx, my), dt, 1);
    }

    public Result click(float x, float y, int button, float vw, float vh) {
        if (closing) return Result.CONSUMED;
        float zoom = zoom();
        x *= zoom; y *= zoom; vw *= zoom; vh *= zoom;
        var layout = layout(vw, vh);
        // The toolbar is painted last, so it owns any overlapping hit regions.
        if (layout.toolbar().contains(x, y)) {
            if (button != 0) return Result.CONSUMED;
            if (layout.save().contains(x, y)) { buttons[1].press(); return Result.SAVE; }
            if (layout.reset().contains(x, y)) {
                buttons[0].press();
                model = new PanelEditorModel(initial.x(), initial.y(), initial.w(), initial.h(), vw, vh);
                sync(vw, vh);
            } else if (layout.title().contains(x, y)) {
                movingToolbar = true; toolbarCustom = true;
                toolbarX = layout.toolbar().x(); toolbarY = layout.toolbar().y();
                pointerStartX = x; pointerStartY = y;
                toolbarStartX = layout.toolbar().x(); toolbarStartY = layout.toolbar().y();
            }
            return Result.CONSUMED;
        }
        if (PanelEditorGeometry.moveHandle(box(), vw).contains(x, y)) {
            if (button == 0) model.begin(PanelEditorModel.Handle.MOVE, x, y);
            return Result.CONSUMED;
        }
        var border = borderAt(x, y);
        if (border != null) {
            if (button == 0) model.begin(border, x, y);
            return Result.CONSUMED;
        }
        return box().contains(x, y) ? Result.PASS : Result.CONSUMED;
    }

    public boolean drag(float x, float y, float vw, float vh) {
        if (closing) return false;
        float zoom = zoom();
        if (movingToolbar) {
            var toolbar = layout(vw * zoom, vh * zoom).toolbar();
            toolbarX = Math.max(0, Math.min(toolbarStartX + x * zoom - pointerStartX, vw * zoom - toolbar.w()));
            toolbarY = Math.max(0, Math.min(toolbarStartY + y * zoom - pointerStartY, vh * zoom - toolbar.h()));
            return true;
        }
        if (!model.dragging()) return false;
        model.drag(x * zoom, y * zoom, vw * zoom, vh * zoom);
        if (model.moving()) model.snap(vw * zoom, vh * zoom, UiTokens.PANEL_TOP_GAP);
        sync(vw * zoom, vh * zoom);
        return true;
    }

    public boolean release(float vw, float vh) {
        if (movingToolbar) { movingToolbar = false; return true; }
        if (!model.dragging()) return false;
        float zoom = zoom();
        if (model.moving()) {
            model.snap(vw * zoom, vh * zoom, UiTokens.PANEL_TOP_GAP);
            sync(vw * zoom, vh * zoom);
        }
        model.end();
        return true;
    }

    public boolean overToolbar(float x, float y, float vw, float vh) {
        float zoom = zoom();
        return closing || layout(vw * zoom, vh * zoom).toolbar().contains(x * zoom, y * zoom);
    }
}
