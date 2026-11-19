package com.atom.chat.settings.contract;

import com.atom.chat.settings.HistoryLabelRow;
import com.atom.chat.ui.ToggleSwitch;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.controls.Control;
import com.atom.lib.controls.ControlObservation;
import com.atom.lib.controls.ControlVisual;
import com.atom.lib.controls.HostedControl;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.lib.frontend.StrictPage;
import com.atom.lib.frontend.StrictPageSession;
import com.atom.lib.frontend.contract.ContractDesign.Aspect;
import com.atom.lib.frontend.contract.ContractDesign.Value;
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
import java.util.Map;

/** Binds the history switch and clear-history action to the existing row copy and confirmation route. */
public final class HistoryActionComponent implements StrictPageSession.Implementation {
    private final boolean composed;

    public HistoryActionComponent() { this(false); }

    public HistoryActionComponent(boolean composed) { this.composed = composed; }

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
        var row = new Row(id, content, composed);
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
        private final boolean composed;
        private final StrictPageSession.ContentView content;
        private final ToggleSwitch toggle;
        private final LeafMeasure measure = this::measure;
        private HistoryLabelRow.Label label;
        private ToggleSwitch.Appearance appearance;
        private int primary, secondary;
        private long revision, advancedFrame = Long.MIN_VALUE;
        private FrameTime time;
        private boolean hovered, pressed, motion, audible;
        private float pointerX = Float.NaN, pointerY = Float.NaN;

        private Row(NodeId id, StrictPageSession.ContentView content, boolean composed) {
            this.id = id;
            this.composed = composed;
            this.content = content;
            var slots = content.snapshot().slots();
            boolean switched = slots.get("value") instanceof StrictPage.BooleanInput;
            if (switched == (slots.get("activate") instanceof StrictPage.Action)) {
                throw new IllegalArgumentException("CONTENT_CAPABILITY_MISSING expected history switch or clear action");
            }
            toggle = switched ? new ToggleSwitch() : null;
        }

        private void update(Map<Aspect, Value> values, StrictPageSession.FrameBindings frame) {
            var next = new HistoryLabelRow.Label(labelText("title"), labelText("description"),
                    toggle == null ? labelText("verb") : "", enabled(), toggle != null);
            if (!next.equals(label)) {
                label = next;
                revision++;
            }
            primary = SettingsFrameColors.color(frame, "textPrimary");
            secondary = SettingsFrameColors.color(frame, "textSecondary");
            if (toggle != null) {
                appearance = new ToggleSwitch.Appearance(SettingsFrameColors.color(frame, "accent"), enabled(),
                        frame.states().getOrDefault("lightCard", false),
                        (float) SettingsFrameColors.parameter(frame, "cornerRadius"));
                motion = frame.states().getOrDefault("decorativeMotion", false);
            }
            audible = values.containsKey(Aspect.SOUND);
        }

        private String labelText(String name) {
            return ((StrictPage.Text) content.snapshot().slots().get(name)).value();
        }

        private boolean checked() {
            return ((StrictPage.BooleanInput) content.snapshot().slots().get("value")).value();
        }

        private MeasuredSize measure(Constraints constraints) {
            if (constraints.width().mode() == Constraints.Mode.UNDEFINED) {
                throw new IllegalArgumentException("MISSING_COMPONENT_WIDTH");
            }
            float width = constraints.width().value();
            return new MeasuredSize(width, label.height(width, label.flow(width)), 0);
        }

        @Override public LayoutNode node(NodeId id, LayoutStyle outer) {
            return new LayoutNode(id, outer, java.util.List.of(), measure, revision);
        }

        @Override public boolean enabled() { return content.snapshot().enabled(); }

        @Override public boolean press(PointerEvent event, PresentationFrame displayed) {
            if (event.button() != 0 || !enabled()) return false;
            if (toggle == null) {
                content.activate("activate");
            } else if (content.commitBoolean("value", !checked())) {
                if (composed) toggle.pulse(); else toggle.pulseAt(time.nowNanos() / 1_000_000);
                if (audible) content.feedback("BUTTON");
            }
            return true;
        }

        @Override public void hover(float x, float y, PresentationFrame displayed) {
            pointerX = x;
            pointerY = y;
        }

        @Override public void advance(FrameTime time, boolean motion, boolean hovered, boolean pressed) {
            this.time = time;
            this.hovered = hovered;
            this.pressed = pressed;
        }

        @Override public ControlObservation observation() {
            return new ControlObservation(enabled(), hovered, pressed, hovered ? 1 : 0, pressed ? 1 : 0,
                    toggle == null ? 0 : toggle.knobPosition());
        }

        @Override public void paint(Canvas canvas, PresentationFrame displayed, ControlVisual visual) {
            var border = displayed.box(id).border();
            var row = new UiLayout.Rect(border.x(), border.y(), border.width(), border.height());
            var flow = label.flow(row.w());
            if (toggle != null) {
                float x = label.switchX(row);
                if (advancedFrame != time.frameId()) {
                    boolean over = pointerX >= x && pointerX <= x + UiTokens.SWITCH_W
                            && pointerY >= row.y() && pointerY <= row.bottom();
                    toggle.setInteraction(over, over && pressed);
                    if (composed) toggle.update((float) (time.deltaSeconds() * 1000), enabled() && checked());
                    else toggle.updateAt(time, enabled() && checked(), motion);
                    advancedFrame = time.frameId();
                }
                float y = label.switchY(row, flow.stacked());
                if (composed) toggle.render(canvas, x, y, appearance.accent(), enabled());
                else toggle.render(canvas, x, y, appearance);
            }
            label.paint(canvas, row, flow, primary, secondary);
        }
    }
}
