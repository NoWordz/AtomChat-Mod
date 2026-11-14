package com.atom.chat.onboarding;

import com.atom.chat.ui.*;
import com.atom.chat.ui.input.InputHandler;
import io.github.humbleui.skija.Canvas;
import java.nio.file.Path;
import java.util.function.DoubleFunction;

/** Prompt and input orchestration; all live effects are explicitly supplied by the screen host. */
public final class TutorialController implements InputHandler {
    public interface Host {
        String translate(String key);
        void enter();
        void show(TutorialSession.Step step);
        void leave();
        void selectRoot(int index);
        boolean mayOffer();
        boolean allowLivePointer(TutorialSession.Step step,float x,float y);
        default boolean livePopupActive() { return false; }
        default boolean returnFromDetail() { return false; }
        default boolean allowLiveClick(TutorialSession.Step step,float x,float y,int button) {
            return TutorialPointerPolicy.buttonAllowed(step,button,livePopupActive()) && allowLivePointer(step,x,y);
        }
        default boolean liveKeyboardActive() { return false; }
        default boolean liveModalActive() { return false; }
        default void feedback(String key) { }
        default java.util.List<TutorialGuide.Entry> itinerary(){return java.util.Arrays.stream(TutorialSession.Step.values()).map(TutorialGuide.Entry::page).toList();}
        default boolean keepPreview(){return true;}
        default boolean actionCompleted(TutorialSession.Step step){return false;}
        default UiLayout.Rect viewport(){return null;}
        default UiLayout.Rect anchor(TutorialSession.Step step) { return null; }
        default TutorialAnchor anchorShape(TutorialSession.Step step) {
            var bounds=anchor(step);
            return bounds==null?null:new TutorialAnchor(bounds,com.atom.lib.design.atomchat.UiTokens.settingsRowRadius());
        }
    }
    private final Host host;
    private final DoubleFunction<Float> x,y;
    private final TutorialSession session=new TutorialSession();
    private final TutorialOverlay overlay=new TutorialOverlay();
    private final ConfirmationDialog prompt=new ConfirmationDialog();
    private OnboardingStore store;
    private boolean offered, captured, livePointer, suspended;
    public void suspendForChild(){if(session.active())suspended=true;}
    public boolean suspended(){return suspended;}
    public void resumeFromChild(){suspended=false;overlay.reset();}
    public TutorialController(Host host,DoubleFunction<Float> x,DoubleFunction<Float> y) {
        this.host=host;this.x=x;this.y=y;
    }
    public void initialize(Path path) { if(store==null)store=new OnboardingStore(path); }
    public boolean active() { return prompt.isActive()||session.active(); }
    public boolean touring() { return session.active(); }
    public TutorialSession.Step step() { return session.step(); }
    public TutorialGuide.Entry entry(){return session.entry();}
    public void start() {
        if(store!=null) { store.retry();store.choose("watch");reportSaveFailure(); }
        offered=true;prompt.reset();
        if(!session.active())host.enter();
        session.itinerary(host.itinerary());session.start();overlay.close();host.show(session.step());
    }
    public void exit() {
        if(session.active()) {suspended=false;session.exit();overlay.close();host.leave();}
    }
    private void advance(boolean skip) {
        if(!session.active())return;
        if(!skip&&session.entry().section()==null&&session.step().requiresAction()&&!session.demoPerformed())return;
        session.next();overlay.reset();
        if(session.active())host.show(session.step()); else host.leave();
    }
    private void previous() {
        session.previous();overlay.reset();host.show(session.step());
    }
    public void reset() { exit();overlay.close();prompt.reset();captured=false;livePointer=false; }
    public void render(Canvas canvas,UiLayout layout,UiLayout root,UiLayout.Rect chrome,float mx,float my,float dt) {
        if(!offered&&store!=null&&store.needsPrompt()&&host.mayOffer()) {
            offered=true;
            prompt.openTutorial(host.translate("atomchat.tutorial.prompt.title"),host.translate("atomchat.tutorial.prompt.body"),
                    host.translate("atomchat.tutorial.dismiss"),host.translate("atomchat.tutorial.watch"),
                    ()->{store.choose("dismissed");reportSaveFailure();},this::start);
        }
        if(prompt.isActive())prompt.render(canvas,layout.rect(),mx,my,dt);
        else if(session.active()&&!host.liveModalActive()) {
            if(host.actionCompleted(session.step()))session.performDemo();
            overlay.viewport(host.viewport());
            overlay.shape(host.anchorShape(session.step()));
            overlay.render(canvas,session,layout,root,chrome,host::translate,mx,my,dt);
        }
        if(store!=null&&store.saveFailed()&&!host.liveModalActive())overlay.notice(canvas,layout.rect(),host.translate("atomchat.tutorial.save_failed"));
    }
    private void reportSaveFailure() { if(store!=null&&store.saveFailed())host.feedback("atomchat.tutorial.save_failed"); }
    @Override public boolean onClick(double mx,double my,int button) {
        if(!active())return false;
        float px=x.apply(mx),py=y.apply(my);
        captured=true;livePointer=false;
        if(prompt.isActive()) {prompt.click(px,py,button);return true;}
        if(host.liveModalActive()) { captured=false;livePointer=true;return false; }
        var g=overlay.geometry();
        if(g==null)return true;
        int control=g.buttonAt(px,py);
        if(button==0&&control>=0) {
            switch(control) {case 0->previous();case 1->advance(false);case 2->{if(session.index()==session.count()-1){if(host.keepPreview())advance(true);}else advance(true);}case 3->exit();}
            return true;
        }
        if(g.card().contains(px,py))return true;
        if(session.step()==TutorialSession.Step.NAVIGATION&&g.target().contains(px,py)&&button==0) {
            int index=Math.max(0,Math.min(2,(int)((px-g.target().x())*3/Math.max(1,g.target().w()))));
            host.selectRoot(index);session.performDemo();return true;
        }
        if(host.allowLiveClick(session.step(),px,py,button)) { captured=false;livePointer=true;return false; }

        return true;
    }
    @Override public boolean onRelease(double mx,double my,int button) {
        if(session.active()&&host.liveModalActive())return false;
        boolean consume=captured;captured=false;
        if(livePointer){livePointer=false;return false;}
        return consume||active();
    }
    @Override public boolean onDrag(double mx,double my,int button,double dx,double dy) {
        if(session.active()&&host.liveModalActive())return false;
        return livePointer?false:active();
    }
    @Override public boolean onScroll(double mx,double my,double horizontal,double vertical) {
        if(!active())return false;
        if(prompt.isActive()){prompt.scroll(x.apply(mx),y.apply(my),vertical);return true;}
        if(session.active()&&host.liveModalActive())return false;
        if(session.active()&&overlay.scroll(x.apply(mx),y.apply(my),vertical))return true;

        if(session.active()&&overlay.geometry()!=null&&!overlay.geometry().card().contains(x.apply(mx),y.apply(my))
                &&host.allowLivePointer(session.step(),x.apply(mx),y.apply(my)))return false;
        return true;
    }
    @Override public boolean onKey(int key,int scan,int modifiers) {
        if(prompt.isActive()){prompt.key(key);return true;}
        if(!session.active())return false;

        if(host.liveModalActive()||host.liveKeyboardActive())return false;
        if(key==256){if(!host.returnFromDetail())exit();return true;}
        if(session.step()==TutorialSession.Step.APPEARANCE)return true;
        if(key==263)previous();
        if(key==262||key==257||key==335)advance(false);
        return true;
    }
    @Override public boolean onChar(char chr,int modifiers) {
        if(prompt.isActive())return true;
        if(!session.active())return false;

        if(host.liveModalActive()||host.liveKeyboardActive())return false;
        return true;
    }
}
