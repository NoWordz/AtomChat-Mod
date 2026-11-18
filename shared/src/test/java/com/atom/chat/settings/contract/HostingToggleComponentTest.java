package com.atom.chat.settings.contract;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.settings.SettingsRowText;
import com.atom.chat.theme.ThemeService;
import com.atom.chat.ui.*;
import com.atom.lib.frontend.*;
import com.atom.lib.frontend.contract.*;
import com.atom.lib.frontend.contract.ContractDesign.*;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.input.PointerEvent;
import com.atom.lib.layout.*;
import com.atom.lib.motion.FrameTime;
import io.github.humbleui.skija.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real old row/knob components under the strict session, not whole-page or device acceptance. */
class HostingToggleComponentTest {
    static final Binding BINDING=new Binding(HostingToggleComponent.class.getName(),"atomchat-settings/1");
    private java.lang.reflect.Field config;private Object previous;
    UiSoundSink hostOutput;
    @BeforeEach void setup()throws Exception{config=AtomChatConfig.class.getDeclaredField("instance");config.setAccessible(true);previous=config.get(null);config.set(null,new AtomChatConfig());}
    @AfterEach void restore()throws Exception{config.set(null,previous);}
    static ContractRegistry.PageHandle definition(){
        var cfg=AtomChatConfig.get();var decisions=new EnumMap<Aspect,Requirement>(Aspect.class);for(var a:Aspect.values())decisions.put(a,Requirement.notApplicable("isolated hosting row feasibility; whole page composition is separate"));
        decisions.put(Aspect.EDITING,Requirement.required());decisions.put(Aspect.SOUND,Requirement.conditional(new NamedCondition.State("feedbackEnabled")));
        var parameters=new HashMap<String,Parameter>();
        for(var e:Map.of("accent",cfg.accentColor,"textPrimary",cfg.textPrimaryColor,"textSecondary",cfg.textSecondaryColor).entrySet())parameters.put(e.getKey(),new Parameter(0,0xffffffffL,Integer.toUnsignedLong(e.getValue()),Unit.ARGB));
        parameters.put("cornerRadius",new Parameter(0,25,cfg.cornerRadius,Unit.U));parameters.put("feedbackVolume",new Parameter(0,1,.45,Unit.RATIO));
        var d=new ContractDesign(1,"settings-probe","1","atomchat-settings/1",Map.of("root",""),Set.of("enabled","lightCard","decorativeMotion","feedbackEnabled"),parameters,
            Map.of("space",new Family("root",Aspect.SPACING,"",Map.of("none",new NumberValue(0,Unit.U),"max",new NumberValue(800,Unit.U))),"edit",new Family("root",Aspect.EDITING,"",Map.of("boolean",new TextValue("original-hosting-boolean/1"))),"sound",new Family("root",Aspect.SOUND,"",Map.of("button",new SoundValue("BUTTON","button","MASTER","feedbackVolume",1,0,new NamedCondition.State("feedbackEnabled"),"")))),
            Map.of("toggle",new Role(true,decisions)),Map.of("toggle",new Recipe("root","toggle",Map.of(Aspect.EDITING,new Ref("edit","boolean"),Aspect.SOUND,new Ref("sound","button")),BINDING,Map.of("title",new Slot(ContentKind.TEXT,true),"description",new Slot(ContentKind.TEXT,true),"value",new Slot(ContentKind.BOOLEAN,true)),Set.of("normal","commit"))),
            Map.of("stack",new Layout("root",Axis.COLUMN,new Ref("space","none"),new Ref("space","none"),new Ref("space","none"),new Ref("space","max"))),Map.of("button",new Asset(AssetKind.SOUND,"atomchat:feedback_button","existing installed UI sound")),Map.of("hosting",new Page("root",Set.of("toggle"),"stack",Set.of("normal","commit"))));
        byte[] b=ContractCodec.encode(d);return ContractRegistry.load(b,ContractRegistry.manifest(b,b),Set.of(d.profile())).page("hosting");
    }
    static StrictPage.Content content(AtomicBoolean backend){return new StrictPage.Content(Map.of("title",new StrictPage.Text("Hosting"),"description",new StrictPage.Text("Keep the existing settings language"),"value",new StrictPage.BooleanInput(backend.get(),value->{backend.set(value);return true;})),true);}
    static Map<String,Boolean> states(){return Map.of("decorativeMotion",true,"lightCard",ThemeService.colorIsLight(UiTokens.cardCutout()),"feedbackEnabled",true);}
    @Test void originalPressCommitReachesControlledSoundAndDisabledOrMutedActionsDoNotPlay(){
        var page=definition();var backend=new AtomicBoolean();var data=content(backend);var descriptor=StrictPage.builder(page).component("hosting",page.recipe("toggle"),data).build();var output=new ArrayList<UiSoundIntent>();
        var sink=new UiSoundSink(){public boolean supports(String channel){return channel.equals("MASTER")&&(hostOutput==null||hostOutput.supports(channel));}public boolean available(String resource){return resource.equals("atomchat:feedback_button")&&(hostOutput==null||hostOutput.available(resource));}public void play(UiSoundIntent event){output.add(event);if(hostOutput!=null)hostOutput.play(event);}};
        try(var surface=Surface.makeRasterN32Premul(420,160);var session=new StrictPageSession(descriptor,Map.of(BINDING,new HostingToggleComponent()),sink)){
            var viewport=new Viewport(420,160,1);var frame=session.render(surface.getCanvas(),viewport,new FrameTime(1,0,0),Map.of(),states());var r=frame.box(new NodeId("settings-probe/hosting/hosting")).border();float x=r.x()+10,y=r.y()+r.height()/2;
            session.pointer(new PointerEvent(PointerEvent.Type.PRESS,x,y,0));assertTrue(backend.get(),"ORIGINAL_PRESS_COMMIT");assertEquals(1,output.size(),"SOUND_OUTPUT_COUNT");assertEquals(.45f,output.get(0).volume());assertEquals(1,output.get(0).pitch());assertEquals("MASTER",output.get(0).channel());assertEquals("atomchat:feedback_button",output.get(0).resource());
            session.pointer(new PointerEvent(PointerEvent.Type.RELEASE,x,y,0));assertEquals(1,output.size());
            session.render(surface.getCanvas(),viewport,new FrameTime(2,16_000_000,.016),Map.of("feedbackVolume",0d),states());session.pointer(new PointerEvent(PointerEvent.Type.PRESS,x,y,0));assertFalse(backend.get());assertEquals(1,output.size());
            var next=content(backend);session.update("hosting",new StrictPage.Content(next.slots(),false));session.render(surface.getCanvas(),viewport,new FrameTime(3,32_000_000,.016),Map.of(),states());session.pointer(new PointerEvent(PointerEvent.Type.PRESS,x,y,0));assertFalse(backend.get());assertEquals(1,output.size());
        }
    }
    @Test void firstPaintMatchesOriginalRowAndIgnoresAnotherViewsCornerChange(){
        var cfg=AtomChatConfig.get();cfg.cornerRadius=25;var page=definition();var data=content(new AtomicBoolean());var descriptor=StrictPage.builder(page).component("hosting",page.recipe("toggle"),data).build();
        var sink=new UiSoundSink(){public boolean supports(String c){return true;}public boolean available(String r){return true;}public void play(UiSoundIntent i){throw new AssertionError("paint must stay silent");}};
        try(var expected=Surface.makeRasterN32Premul(420,160);var actual=Surface.makeRasterN32Premul(420,160);var session=new StrictPageSession(descriptor,Map.of(BINDING,new HostingToggleComponent()),sink)){
            expected.getCanvas().clear(0);actual.getCanvas().clear(0);var measured=SettingsRowText.measure("Hosting","Keep the existing settings language",420,UiTokens.SWITCH_W+UiTokens.SETTINGS_ROW_PAD,UiTokens.SWITCH_H,true);var row=new UiLayout.Rect(0,0,420,measured.height());
            SettingsRowText.draw(expected.getCanvas(),row,measured,cfg.textPrimaryColor,cfg.textSecondaryColor);var toggle=new ToggleSwitch();toggle.snapTo(false);
            toggle.render(expected.getCanvas(),row.right()-UiTokens.SETTINGS_ROW_PAD-UiTokens.SWITCH_W,measured.stacked()?row.bottom()-UiTokens.SETTINGS_ROW_PAD-UiTokens.SWITCH_H:(row.h()-UiTokens.SWITCH_H)/2,cfg.accentColor,true);
            var state=states();cfg.cornerRadius=0;session.render(actual.getCanvas(),new Viewport(420,160,1),new FrameTime(1,0,0),Map.of(),state);
            try(var a=expected.makeImageSnapshot();var b=actual.makeImageSnapshot();var left=Bitmap.makeFromImage(a);var right=Bitmap.makeFromImage(b)){for(int y=0;y<160;y++)for(int x=0;x<420;x++)assertEquals(left.getColor(x,y),right.getColor(x,y),"original row pixels at "+x+","+y);}
        }
    }
}
