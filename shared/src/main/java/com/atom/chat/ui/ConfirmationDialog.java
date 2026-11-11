package com.atom.chat.ui;



import com.atom.lib.design.atomchat.AppIcons;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.font.FontManager;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import io.github.humbleui.skija.Canvas;
import java.util.List;

/** Panel-centred picker-family modal. Input is routed by ConfirmationInput, business stays with the caller. */
public final class ConfirmationDialog {
    private final ConfirmationState state = new ConfirmationState();
    private String title = "", message = "", cancelLabel = "", confirmLabel = "";
    private ConfirmationLayout geometry;
    private boolean visible;
    private boolean tutorial;
    private Runnable dismissAction;
    private float progress, pulse;
    private int pressed = -1;
    private final float[] hover = new float[2];
    private final PressScale[] scale = {PressScale.bounce(), PressScale.bounce()};
    public boolean isActive() { return visible; }
    public boolean hasPendingAction() { return state.active(); }
    ConfirmationLayout geometry() { return geometry; }
    public void open(String title, String message, String cancelLabel, String confirmLabel, Runnable action) {
        tutorial = false; dismissAction = null;
        this.title = title; this.message = message; this.cancelLabel = cancelLabel; this.confirmLabel = confirmLabel;
        geometry = null; visible = true; state.open(action);
        hover[0] = hover[1] = 0; pressed = -1; pulse = 0;
        scale[0] = PressScale.bounce(); scale[1] = PressScale.bounce();
    }
    public void openTutorial(String title, String message, String cancelLabel, String confirmLabel,
                             Runnable dismiss, Runnable watch) {
        open(title, message, cancelLabel, confirmLabel, watch);
        tutorial = true; dismissAction = dismiss;
        state.key(258); // Watching is the primary tutorial action; destructive dialogs still focus Cancel.
    }
    public void cancel() {
        boolean run = tutorial && state.active();
        state.cancel();
        if (run && dismissAction != null) dismissAction.run();
    }
    public void reset() { state.cancel(); visible = false; progress = 0; geometry = null; }
    public void key(int key) {
        if (!state.active()) return;
        if (tutorial && key == 256) return;
        if (progress < 1 && (key == 257 || key == 335 || key == 32)) return;
        if (key == 257 || key == 335 || key == 32) press(state.confirmFocused() ? 1 : 0);
        if (tutorial && (key == 257 || key == 335 || key == 32)) {
            if (state.confirmFocused()) state.confirm(); else cancel();
        } else state.key(key);
    }
    private void press(int button) { pressed = button; pulse = UiMotion.POPUP_MS; }
    public void click(float x, float y, int button) {
        if (button != 0 || geometry == null || !state.active() || progress < 1) return;
        if (geometry.cancel().contains(x, y)) { press(0); cancel(); }
        else if (!geometry.surface().contains(x, y)) { if (!tutorial) cancel(); }
        else if (geometry.confirm().contains(x, y)) { press(1); state.confirm(); }
    }
    public void render(Canvas canvas, UiLayout.Rect panel, float mx, float my) {
        render(canvas, panel, mx, my, 16);
    }
    public void render(Canvas canvas, UiLayout.Rect panel, float mx, float my, float dt) {
        if (!isActive()) return;
        progress = UiMotion.approach(progress, state.active() ? 1 : 0, dt, Animations.ms(UiMotion.POPUP_MS));
        if (!state.active() && progress <= .001F) { visible = false; geometry = null; return; }
        ClickRipples.block(panel);
        var provisional = ConfirmationLayout.of(panel, 1);
        float bodyWidth=Math.max(1,provisional.surface().w()-provisional.padding()*2);
        var text=ConfirmationText.fit(message,bodyWidth,UiTokens.SETTINGS_TILE_SUB);
        float bodySize=text.size(),lineHeight=text.lineHeight();
        var bodyFont=FontManager.font(bodySize);
        List<String> lines=text.lines();
        geometry = ConfirmationLayout.of(panel, lines.size(), lineHeight);
        while (bodySize > UiTokens.DIALOG_MIN_TEXT_SIZE
                && lines.size() * lineHeight > geometry.cancel().y() - geometry.surface().y()
                - geometry.padding() * 1.5F - UiTokens.HEADER_HEIGHT) {
            text=ConfirmationText.fit(message,bodyWidth,bodySize*.9F);
            bodySize=text.size();
            bodyFont = FontManager.font(bodySize);
            lineHeight=text.lineHeight();
            lines=text.lines();
            geometry = ConfirmationLayout.of(panel, lines.size(), lineHeight);
        }
        var box = geometry.surface();
        int saved = canvas.getSaveCount();
        canvas.save();
        try (var opacity = new io.github.humbleui.skija.Paint().setAlphaf(progress)) {
        PanelBackground.clipInterior(canvas, panel);
        PickerModalPaint.backdrop(canvas,panel,progress);
        PickerModalPaint.surface(canvas,box,progress);
        if (progress < 1) canvas.saveLayer(io.github.humbleui.types.Rect.makeXYWH(panel.x(), panel.y(), panel.w(), panel.h()), opacity);
        ClickRipples.block(box);
        canvas.save();
        try {
            SkiaDraw.clip(canvas, box.x(), box.y(), box.w(), box.h(), PickerModalPaint.radius());
            float pad = geometry.padding();
            float x = box.x() + pad;
            var titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
            com.atom.chat.render.DrawPrimitives.drawIconCentered(canvas,
                    tutorial ? com.atom.chat.onboarding.GuideIcon.PATH : AppIcons.ICON_WARNING_PATH,
                    x+UiTokens.COMPOSER_ICON_SIZE/2,geometry.titleCenterY(),UiTokens.COMPOSER_ICON_SIZE,
                    tutorial ? com.atom.chat.config.AtomChatConfig.get().accentColor : UiTokens.dangerColor());
            SkiaFontRenderer.drawText(canvas,titleFont,SkiaFontRenderer.truncate(titleFont,title,
                            Math.max(0,box.right()-pad-geometry.titleTextX())),geometry.titleTextX(),
                    SkiaFontRenderer.centerBaselineY(titleFont,geometry.titleCenterY()),0xFFFFFFFF);
            float y = box.y() + pad + UiTokens.HEADER_HEIGHT;
            canvas.save();
            try {
                SkiaDraw.clip(canvas, x, y, Math.max(0, box.w() - pad * 2),
                        Math.max(0, geometry.cancel().y() - y - pad / 2), 0);
                for (String line : lines) {
                    SkiaFontRenderer.drawText(canvas, bodyFont, line, x,
                            SkiaFontRenderer.centerBaselineY(bodyFont, y + lineHeight / 2), 0xFFB9C3D2);
                    y += lineHeight;
                }
            } finally { canvas.restore(); }
            pulse = Math.max(0, pulse - Math.max(0, dt));
            drawButton(canvas, 0, geometry.cancel(), cancelLabel, !state.confirmFocused(), state.active() && geometry.cancel().contains(mx, my), dt);
            drawButton(canvas, 1, geometry.confirm(), confirmLabel, state.confirmFocused(), state.active() && geometry.confirm().contains(mx, my), dt);
        } finally { canvas.restore(); }
        } finally { canvas.restoreToCount(saved); }
    }
    private void drawButton(Canvas canvas, int index, UiLayout.Rect rect, String label, boolean focused, boolean hovered, float dt) {
        hover[index] = UiMotion.approach(hover[index], hovered || focused ? 1 : 0, dt, UiMotion.HOVER_MS);
        scale[index].update(hovered, pressed == index && pulse > 0, dt, Animations.enabled(), rect.w());
        scale[index].begin(canvas, rect.x() + rect.w() / 2, rect.y() + rect.h() / 2);
        try {
        if (tutorial && index == 1) {
            int accent = com.atom.chat.config.AtomChatConfig.get().accentColor;
            SkiaDraw.drawRoundedRect(canvas, rect.x(), rect.y(), rect.w(), rect.h(), PickerModalPaint.buttonRadius(rect), accent);
            if (hover[index] > .01F) SkiaDraw.drawRoundedRect(canvas,rect.x(),rect.y(),rect.w(),rect.h(),PickerModalPaint.buttonRadius(rect),
                    io.github.humbleui.skija.Color.makeARGB((int)(60*hover[index]),255,255,255));
        } else PickerModalPaint.buttonFace(canvas,rect,hover[index],255,index==1);
        ClickRipples.region(rect.x(),rect.y(),rect.w(),rect.h(),PickerModalPaint.buttonRadius(rect),
                index==1?(tutorial?com.atom.chat.config.AtomChatConfig.get().accentColor:UiTokens.dangerColor()):0xFF23272F);
        var font = FontManager.font(UiTokens.FONT_BUTTON);
        float width = SkiaFontRenderer.getStringWidth(font, label);
        SkiaFontRenderer.drawText(canvas, font, label, rect.x() + (rect.w() - width) / 2,
                SkiaFontRenderer.centerBaselineY(font, rect.y() + rect.h() / 2),
                tutorial && index == 1 ? UiTokens.onAccent(com.atom.chat.config.AtomChatConfig.get().accentColor) : 0xFFFFFFFF);
        } finally { canvas.restore(); }
    }
}
