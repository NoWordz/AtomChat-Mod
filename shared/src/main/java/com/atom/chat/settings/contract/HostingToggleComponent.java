package com.atom.chat.settings.contract;

import com.atom.chat.render.CornerGeometry;
import com.atom.chat.settings.SettingsRowText;
import com.atom.chat.ui.ToggleSwitch;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.lib.controls.*;
import com.atom.lib.design.atomchat.*;
import com.atom.lib.frontend.*;
import com.atom.lib.frontend.contract.ContractDesign.*;
import com.atom.lib.input.*;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.Canvas;
import java.util.*;

/** The existing hosting toggle row as a bound component, with business-only content and per-view appearance. */
public final class HostingToggleComponent implements StrictPageSession.Implementation {
    private final boolean composed;
    public HostingToggleComponent(){this(false);}
    public HostingToggleComponent(boolean composed){this.composed=composed;}

    public Control create(NodeId id,Map<Aspect,Value> values,StrictPageSession.ContentView content){throw new IllegalStateException("MISSING_FRAME_BINDINGS");}
    public void update(Control control,Map<Aspect,Value> values,StrictPageSession.ContentView content){throw new IllegalStateException("MISSING_FRAME_BINDINGS");}
    public Control create(NodeId id,Map<Aspect,Value> values,StrictPageSession.ContentView content,StrictPageSession.FrameBindings frame){return new HostedControl(id,new Row(id,values,content,frame,composed));}
    public void update(Control control,Map<Aspect,Value> values,StrictPageSession.ContentView content,StrictPageSession.FrameBindings frame){
        var host=(HostedControl)control;host.adapter(Row.class).update(values,frame);host.refresh();
    }
    private static final class Row implements HostedControl.Adapter {
        private final boolean composed;private final NodeId id;private final StrictPageSession.ContentView content;private final ToggleSwitch toggle=new ToggleSwitch();
        private final LeafMeasure measure=this::measure;
        private Map<Aspect,Value> values;private StrictPageSession.FrameBindings bindings;private FrameTime time;
        private String title="",description="";private long revision,advanced=Long.MIN_VALUE;
        private UiLayout.Rect box;private float pointerX=Float.NaN,pointerY=Float.NaN,hover,paintedHover;private boolean hovered,pressed;
        Row(NodeId id,Map<Aspect,Value> values,StrictPageSession.ContentView content,StrictPageSession.FrameBindings frame,boolean composed){this.composed=composed;this.id=id;this.content=content;update(values,frame);toggle.snapTo(checked());}
        void update(Map<Aspect,Value> values,StrictPageSession.FrameBindings frame){
            this.values=Map.copyOf(values);bindings=frame;String nextTitle=slotText("title"),nextDescription=slotText("description");
            if(!title.equals(nextTitle)||!description.equals(nextDescription)){title=nextTitle;description=nextDescription;revision++;}
            if(!(content.snapshot().slots().get("value") instanceof StrictPage.BooleanInput))throw new IllegalArgumentException("CONTENT_CAPABILITY_MISSING hosting toggle needs a boolean business binding");
            for(String parameter:List.of("accent","textPrimary","textSecondary","cornerRadius"))parameter(parameter);
            for(String state:List.of("lightCard","decorativeMotion"))if(!frame.states().containsKey(state))throw new IllegalArgumentException("MISSING_COMPONENT_STATE "+state);
        }
        private String slotText(String slot){var value=content.snapshot().slots().get(slot);if(value instanceof StrictPage.Text t)return t.value();if(value instanceof StrictPage.TextInput t)return t.value();throw new IllegalArgumentException("CONTENT_PURPOSE "+slot);}
        private double parameter(String name){Double value=bindings.parameters().get(name);if(value==null)throw new IllegalArgumentException("MISSING_COMPONENT_PARAMETER "+name);return value;}
        private int color(String name){double value=parameter(name);if(value<0||value>0xffffffffL||value!=Math.rint(value))throw new IllegalArgumentException("INVALID_COMPONENT_COLOR "+name);return (int)(long)value;}
        private boolean checked(){return ((StrictPage.BooleanInput)content.snapshot().slots().get("value")).value();}
        private SettingsRowText.Text text(float width){return SettingsRowText.measure(title,description,width,UiTokens.SWITCH_W+UiTokens.SETTINGS_ROW_PAD,UiTokens.SWITCH_H,true);}
        private MeasuredSize measure(Constraints constraints){
            if(constraints.width().mode()==Constraints.Mode.UNDEFINED)throw new IllegalArgumentException("MISSING_COMPONENT_WIDTH");
            float width=constraints.width().value();return new MeasuredSize(width,text(width).height(),0);
        }
        public LayoutNode node(NodeId id,LayoutStyle outer){return new LayoutNode(id,outer,List.of(),measure,revision);}
        public boolean enabled(){return content.snapshot().enabled();}
        public void hover(float x,float y,PresentationFrame frame){pointerX=x;pointerY=y;}
        public boolean press(PointerEvent event,PresentationFrame frame){
            if(event.button()!=0||!enabled())return false;pointerX=event.x();pointerY=event.y();
            if(content.commitBoolean("value",!checked())){if(composed)toggle.pulse();else toggle.pulseAt(time.nowNanos()/1_000_000);if(values.containsKey(Aspect.SOUND))content.feedback("BUTTON");}
            return true;
        }
        public void cancel(CancelReason reason){pressed=false;}
        public void advance(FrameTime time,boolean motion,boolean hovered,boolean pressed){this.time=time;this.hovered=hovered;this.pressed=pressed;}
        public ControlObservation observation(){return new ControlObservation(enabled(),hovered,pressed,paintedHover,pressed?1:0,toggle.knobPosition());}
        public void paint(Canvas canvas,PresentationFrame frame,ControlVisual visual){
            var border=frame.box(id).border();box=new UiLayout.Rect(border.x(),border.y(),border.width(),border.height());var layout=text(box.w());
            float x=box.right()-UiTokens.SETTINGS_ROW_PAD-UiTokens.SWITCH_W,y=layout.stacked()?box.bottom()-UiTokens.SETTINGS_ROW_PAD-UiTokens.SWITCH_H:box.y()+(box.h()-UiTokens.SWITCH_H)/2;
            boolean over=enabled()&&pointerX>=x&&pointerX<=x+UiTokens.SWITCH_W&&pointerY>=y&&pointerY<=y+UiTokens.SWITCH_H;
            boolean step=advanced!=time.frameId();
            if(step){paintedHover=hover;toggle.setInteraction(over,pressed&&over);if(composed)toggle.update((float)(time.deltaSeconds()*1000),checked());else toggle.updateAt(time,checked(),bindings.states().get("decorativeMotion"));}
            int accent=color("accent"),primary=color("textPrimary"),secondary=color("textSecondary");
            if(!composed&&paintedHover>.01f)CornerGeometry.drawRoundedRect(canvas,box.x(),box.y(),box.w(),box.h(),0,UiTokens.cardHover(accent,0,paintedHover));
            SettingsRowText.draw(canvas,box,layout,enabled()?primary:secondary,secondary);
            if(composed)toggle.render(canvas,x,y,accent,enabled());else toggle.render(canvas,x,y,new ToggleSwitch.Appearance(accent,enabled(),bindings.states().get("lightCard"),(float)parameter("cornerRadius")));
            if(step){hover=UiMotion.approach(hover,hovered&&enabled()?1:0,(float)Math.min(50,time.deltaSeconds()*1000),UiMotion.HOVER_MS);advanced=time.frameId();}
        }
    }
}
