package com.atom.chat.settings.contract;

import com.atom.chat.settings.SettingsFormField;
import com.atom.chat.settings.SettingsRowText;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.controls.CancelReason;
import com.atom.lib.controls.Control;
import com.atom.lib.controls.ControlObservation;
import com.atom.lib.controls.ControlVisual;
import com.atom.lib.controls.HostedControl;
import com.atom.lib.controls.TextClipboard;
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
import java.util.Map;
import java.util.Objects;

/** The hosting page's existing integer editor. Its draft can remain empty until save validation. */
public final class HostingNumberComponent implements StrictPageSession.Implementation {
    private final TextClipboard clipboard;

    public HostingNumberComponent(TextClipboard clipboard) {
        this.clipboard = Objects.requireNonNull(clipboard);
    }

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
        Row row = new Row(id, content, clipboard);
        row.update(values, frame);
        return new HostedControl(id, row);
    }

    @Override
    public void update(Control control, Map<Aspect, Value> values, StrictPageSession.ContentView content,
                       StrictPageSession.FrameBindings frame) {
        HostedControl hosted = (HostedControl) control;
        hosted.adapter(Row.class).update(values, frame);
        hosted.refresh();
    }

    private static final class Row implements HostedControl.Adapter {
        private final NodeId id;
        private final StrictPageSession.ContentView content;
        private final TextClipboard clipboard;
        private final SettingsFormField field = new SettingsFormField();
        private final LeafMeasure measurement = limits -> {
            if (limits.width().mode() == Constraints.Mode.UNDEFINED) {
                throw new IllegalArgumentException("MISSING_COMPONENT_WIDTH");
            }
            float width = limits.width().value();
            return new MeasuredSize(width, measure(width).height(), 0);
        };
        private String title = "", description = "";
        private long revision;
        private AtomChatControlStyle appearance;
        private boolean editable, hovered, pressed, sessionFocused;
        private float pointerX = Float.NaN, pointerY = Float.NaN;
        private UiLayout.Rect fieldBounds;
        private FrameTime time;

        Row(NodeId id, StrictPageSession.ContentView content, TextClipboard clipboard) {
            this.id = id;
            this.content = content;
            this.clipboard = clipboard;
            field.configure(true, false, 10);
        }

        void update(Map<Aspect, Value> values, StrictPageSession.FrameBindings frame) {
            editable = values.containsKey(Aspect.EDITING);
            String nextTitle = label("title"), nextDescription = label("description");
            if (!title.equals(nextTitle) || !description.equals(nextDescription)) {
                title = nextTitle;
                description = nextDescription;
                revision++;
            }
            if (!(content.snapshot().slots().get("value") instanceof StrictPage.TextInput)) {
                throw new IllegalArgumentException("CONTENT_CAPABILITY_MISSING numeric draft requires a text editing binding");
            }
            appearance = SettingsFrameColors.numberAppearance(frame);
        }

        private String label(String name) {
            return ((StrictPage.Text) content.snapshot().slots().get(name)).value();
        }

        private SettingsRowText.Text measure(float width) {
            return SettingsRowText.measureNumber(title, description, width);
        }

        @Override public LayoutNode node(NodeId id, LayoutStyle outer) {
            return new LayoutNode(id, outer, java.util.List.of(), measurement, revision);
        }

        @Override public boolean enabled() { return editable && content.snapshot().enabled(); }

        @Override public void hover(float x, float y, PresentationFrame frame) {
            pointerX = x;
            pointerY = y;
        }

        @Override public boolean acceptsPointer(float x, float y, PresentationFrame frame) {
            return fieldBounds != null && fieldBounds.contains(x, y);
        }

        @Override public boolean press(PointerEvent event, PresentationFrame displayed) {
            if (event.button() != 0 || !enabled() || !acceptsPointer(event.x(), event.y(), displayed)) return false;
            field.pointer(event.x(), event.y(), false);
            return true;
        }

        @Override public void drag(PointerEvent event, PresentationFrame frame) {
            field.pointer(event.x(), event.y(), true);
        }

        @Override public void cancel(CancelReason reason) {
            field.focus(false);
            sessionFocused = false;
        }

        @Override public void hostFocus(boolean focused) {
            field.focus(focused);
            sessionFocused = focused;
        }

        @Override public boolean interacting() { return field.focused(); }

        @Override public boolean text(String value) {
            if (!field.focused() || !enabled()) return false;
            updateDraft(() -> field.insert(value));
            return true;
        }

        @Override public boolean key(KeyEvent event) {
            int key = legacyKey(event.key());
            if (key < 0 || !field.focused() || !enabled()) return false;
            if (event.pressed()) {
                int modifiers = (event.control() ? 2 : 0) | (event.shift() ? 1 : 0);
                updateDraft(() -> field.key(key, modifiers, clipboard::read, clipboard::write));
            }
            return true;
        }

        private void updateDraft(Runnable input) {
            String before = field.edit.text();
            input.run();
            String next = field.edit.text();
            if (!before.equals(next) && !content.commitText("value", next)) field.edit.set(before);
        }

        @Override public void advance(FrameTime time, boolean motion, boolean hovered, boolean pressed) {
            this.time = time;
            this.hovered = hovered;
            this.pressed = pressed;
        }

        @Override public ControlObservation observation() {
            return new ControlObservation(enabled(), hovered && enabled(), pressed, field.hoverWeight(), pressed ? 1 : 0, 0);
        }

        @Override public void paint(Canvas canvas, PresentationFrame frame, ControlVisual visual) {
            var border = frame.box(id).border();
            var row = new UiLayout.Rect(border.x(), border.y(), border.width(), border.height());
            var text = measure(row.w());
            boolean invalid = ((StrictPage.BooleanValue) content.snapshot().slots().get("invalid")).value();
            int secondary = appearance.textSecondaryColor();
            SettingsRowText.draw(canvas, row, text, enabled() ? appearance.textColor() : secondary,
                    invalid ? UiTokens.dangerColor() : secondary);
            fieldBounds = SettingsRowText.number(row, text.stacked());
            if (sessionFocused != visual.focused()) field.focus(visual.focused());
            sessionFocused = visual.focused();
            String draft = ((StrictPage.TextInput) content.snapshot().slots().get("value")).value();
            field.paint(canvas, fieldBounds, draft, enabled(), invalid, pointerX, pointerY,
                    (float) Math.min(50, time.deltaSeconds() * 1000), appearance, appearance.textColor(),
                    time.nowNanos() / 1_000_000);
            SettingsRowText.drawNumberUnit(canvas, fieldBounds, label("unit"), secondary);
        }
    }

    private static int legacyKey(KeyEvent.Key key) {
        return switch (key) {
            case A -> 'A';
            case C -> 'C';
            case V -> 'V';
            case X -> 'X';
            case Y -> 'Y';
            case Z -> 'Z';
            case ENTER -> 257;
            case BACKSPACE -> 259;
            case DELETE -> 261;
            case RIGHT -> 262;
            case LEFT -> 263;
            case DOWN -> 264;
            case UP -> 265;
            case HOME -> 268;
            case END -> 269;
            default -> -1;
        };
    }
}
