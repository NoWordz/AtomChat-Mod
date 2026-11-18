package com.atom.chat.settings.contract;

import com.atom.chat.settings.HistoryNumberRow;
import com.atom.chat.settings.SettingsNumberEditor;
import com.atom.chat.settings.SettingsRowText;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.lib.controls.CancelReason;
import com.atom.lib.controls.Control;
import com.atom.lib.controls.ControlObservation;
import com.atom.lib.controls.ControlVisual;
import com.atom.lib.controls.HostedControl;
import com.atom.lib.design.atomchat.AtomChatControlStyle;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.lib.frontend.StrictPage;
import com.atom.lib.frontend.StrictPageSession;
import com.atom.lib.frontend.contract.ContractDesign.Aspect;
import com.atom.lib.frontend.contract.ContractDesign.Value;
import com.atom.lib.input.KeyEvent;
import com.atom.lib.input.PointerEvent;
import com.atom.lib.layout.Constraints;
import com.atom.lib.layout.LayoutNode;
import com.atom.lib.layout.LayoutStyle;
import com.atom.lib.layout.LeafMeasure;
import com.atom.lib.layout.MeasuredSize;
import com.atom.lib.layout.NodeId;
import com.atom.lib.layout.PresentationFrame;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.Canvas;
import java.util.List;
import java.util.Map;

/** Bound renderer for the history days setting, with the existing commit-on-Enter-or-blur editing model. */
public final class HistoryRetentionComponent implements StrictPageSession.Implementation {
    @Override
    public Control create(NodeId id, Map<Aspect, Value> values, StrictPageSession.ContentView content) {
        throw new IllegalStateException("MISSING_FRAME_BINDINGS");
    }

    @Override
    public void update(Control control, Map<Aspect, Value> values, StrictPageSession.ContentView content) {
        throw new IllegalStateException("MISSING_FRAME_BINDINGS");
    }

    @Override
    public Control create(NodeId id, Map<Aspect, Value> values, StrictPageSession.ContentView content,
                          StrictPageSession.FrameBindings frame) {
        Row row = new Row(id, content);
        row.update(values, frame);
        return new HostedControl(id, row);
    }

    @Override
    public void update(Control control, Map<Aspect, Value> values, StrictPageSession.ContentView content,
                       StrictPageSession.FrameBindings frame) {
        var hosted = (HostedControl) control;
        hosted.adapter(Row.class).update(values, frame);
        hosted.refresh();
    }

    private static final class Row implements HostedControl.Adapter {
        private final NodeId id;
        private final StrictPageSession.ContentView content;
        private final SettingsNumberEditor edit = new SettingsNumberEditor();
        private final LeafMeasure measure = this::measure;
        private String title = "", description = "";
        private long revision, lastHoverFrame = Long.MIN_VALUE, lastHoverMillis;
        private boolean editable, hovered, pressed;
        private float pointerX = Float.NaN, pointerY = Float.NaN, hover;
        private FrameTime time;
        private AtomChatControlStyle appearance;

        Row(NodeId id, StrictPageSession.ContentView content) {
            this.id = id;
            this.content = content;
        }

        void update(Map<Aspect, Value> values, StrictPageSession.FrameBindings frame) {
            editable = values.containsKey(Aspect.EDITING);
            String nextTitle = label("title"), nextDescription = label("description");
            if (!title.equals(nextTitle) || !description.equals(nextDescription)) {
                title = nextTitle;
                description = nextDescription;
                revision++;
            }
            if (!(content.snapshot().slots().get("value") instanceof StrictPage.NumberInput)) {
                throw new IllegalArgumentException("CONTENT_CAPABILITY_MISSING history days needs a number editing binding");
            }
            appearance = SettingsFrameColors.numberAppearance(frame);
        }

        private String label(String name) {
            return ((StrictPage.Text) content.snapshot().slots().get(name)).value();
        }

        private int value() {
            return (int) Math.round(((StrictPage.NumberInput) content.snapshot().slots().get("value")).value());
        }

        private MeasuredSize measure(Constraints limits) {
            if (limits.width().mode() == Constraints.Mode.UNDEFINED) throw new IllegalArgumentException("MISSING_COMPONENT_WIDTH");
            float width = limits.width().value();
            var flow = SettingsRowText.measureNumber(title, description, width);
            float height = flow.stacked() ? Math.max(UiTokens.SETTINGS_SLIDER_ROW_H, flow.height()) : UiTokens.SETTINGS_SLIDER_ROW_H;
            return new MeasuredSize(width, height, 0);
        }

        @Override public LayoutNode node(NodeId id, LayoutStyle outer) {
            return new LayoutNode(id, outer, List.of(), measure, revision);
        }

        @Override public boolean enabled() { return editable && content.snapshot().enabled(); }
        @Override public boolean capturesEditingInput() { return edit.active(); }
        @Override public boolean interacting() { return edit.active(); }

        @Override public boolean press(PointerEvent event, PresentationFrame frame) {
            if (event.button() != 0 || !enabled()) return false;
            edit.open(id.value(), value(), next -> content.commitNumber("value", next));
            return true;
        }

        @Override public boolean beforePointerPress(PointerEvent event, PresentationFrame frame) {
            if (frame.box(id).border().contains(event.x(), event.y())) return true;
            edit.commit();
            return false;
        }

        @Override public boolean key(KeyEvent event) {
            if (!edit.active()) return false;
            if (event.pressed()) {
                switch (event.key()) {
                    case ENTER -> edit.commit();
                    case ESCAPE -> edit.cancel();
                    case BACKSPACE -> edit.backspace();
                    default -> { }
                }
            }
            return true;
        }

        @Override public boolean text(String text) {
            if (!edit.active()) return false;
            for (int i = 0; i < text.length(); i++) edit.append(text.charAt(i));
            return true;
        }

        @Override public void cancel(CancelReason reason) { edit.cancel(); }

        @Override public void hover(float x, float y, PresentationFrame frame) {
            pointerX = x;
            pointerY = y;
        }

        @Override public void advance(FrameTime time, boolean motion, boolean hovered, boolean pressed) {
            this.time = time;
            this.hovered = hovered;
            this.pressed = pressed;
        }

        @Override public ControlObservation observation() {
            return new ControlObservation(enabled(), hovered && enabled(), pressed, hover, pressed ? 1 : 0, 0);
        }

        @Override public void paint(Canvas canvas, PresentationFrame frame, ControlVisual visual) {
            var bounds = frame.box(id).border();
            var row = new UiLayout.Rect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
            var flow = SettingsRowText.measureNumber(title, description, row.w());
            long millis = time.nowNanos() / 1_000_000;
            if (lastHoverFrame != time.frameId()) {
                var field = SettingsRowText.number(row, flow.stacked());
                float dt = lastHoverFrame == Long.MIN_VALUE ? 0 : Math.max(0, Math.min(50, millis - lastHoverMillis));
                hover = UiMotion.approach(hover, field.contains(pointerX, pointerY) ? 1 : 0, dt, UiMotion.HOVER_MS);
                lastHoverFrame = time.frameId();
                lastHoverMillis = millis;
            }
            String text = edit.active() ? (edit.text().isEmpty() ? "0" : edit.text()) : Integer.toString(value());
            HistoryNumberRow.draw(canvas, row, flow.stacked() ? flow : null, title, description, label("unit"),
                    text, edit.active(), hover, appearance, appearance.accentColor(), appearance.textColor(),
                    UiTokens.withAlpha(appearance.textSecondaryColor(), 200), millis);
        }
    }
}
