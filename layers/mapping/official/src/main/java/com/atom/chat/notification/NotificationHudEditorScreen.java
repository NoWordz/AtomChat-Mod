package com.atom.chat.notification;

import com.atom.chat.render.SkiaGraphics;
import com.atom.chat.screen.ScreenMetrics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Thin native screen bridge; geometry and interaction live in the shared editor. */
public final class NotificationHudEditorScreen extends Screen {
    private final Screen parent;
    private final Runnable returned,abandoned;
    private boolean returning;
    private final NotificationHudEditor editor = new NotificationHudEditor(key -> Component.translatable(key).getString());
    private final SkiaGraphics graphics = new SkiaGraphics();
    private final com.atom.chat.ui.input.InputRouter pointer = new com.atom.chat.ui.input.InputRouter()
            .add(new com.atom.chat.ui.input.InputHandler() {
                public com.atom.chat.ui.input.ClickTarget clickTarget(double x,double y,int button) {
                    return editor.actionTarget(vx(x),vy(y),vx(width),vy(height),button,NotificationHudEditorScreen.this::activate);
                }
                public boolean onClick(double x,double y,int button) {
                    editor.click(vx(x),vy(y),vx(width),vy(height),button);
                    return true;
                }
                public boolean onDrag(double x,double y,int button,double dx,double dy) {
                    if(button==0) editor.drag(vx(x),vy(y),vx(width),vy(height));
                    return true;
                }
                public boolean onRelease(double x,double y,int button) {
                    if(button==0) editor.release();
                    return true;
                }
            });

    private void activate(NotificationHudEditor.Action action) {
        if(action==NotificationHudEditor.Action.SAVE) editor.save();
        if(action!=NotificationHudEditor.Action.NONE) onClose();
    }
    public NotificationHudEditorScreen(Screen parent){this(parent,()->{},()->{});}
    public NotificationHudEditorScreen(Screen parent,Runnable returned,Runnable abandoned) {
        super(Component.translatable("atomchat.notify.editor.title"));this.parent=parent;this.returned=returned;this.abandoned=abandoned;
    }
    private float vx(double x) { return ScreenMetrics.toVirtualX(minecraft.getWindow(),x); }
    private float vy(double y) { return ScreenMetrics.toVirtualY(minecraft.getWindow(),y); }
    @Override public void render(GuiGraphics gui,int mx,int my,float delta) {
        gui.flush(); graphics.checkFrameBufferId();
        if(!minecraft.isWindowActive()) cancelPointer();
        graphics.draw(null,ScreenMetrics.uiDensity(minecraft.getWindow()),
                (canvas,snapshot)->editor.render(canvas,vx(width),vy(height),vx(mx),vy(my)));
        if(editor.closed()){returning=true;minecraft.setScreen(parent);returned.run();}
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        return pointer.click(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        return pointer.drag(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        if(!minecraft.isWindowActive()) cancelPointer();
        return pointer.release(x,y,button);
    }
    private void cancelPointer() {
        pointer.cancel(0);
        editor.release();
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(editor.closing())return true;
        if(key==GLFW.GLFW_KEY_ESCAPE || key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_KP_ENTER)
            com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
        if(key==GLFW.GLFW_KEY_ESCAPE) onClose();
        else if(key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_KP_ENTER) { editor.save(); onClose(); }
        else {
            float step=(modifiers & GLFW.GLFW_MOD_SHIFT)!=0 ? 10 : 1;
            if(key==GLFW.GLFW_KEY_LEFT) editor.nudge(-step,0);
            if(key==GLFW.GLFW_KEY_RIGHT) editor.nudge(step,0);
            if(key==GLFW.GLFW_KEY_UP) editor.nudge(0,-step);
            if(key==GLFW.GLFW_KEY_DOWN) editor.nudge(0,step);
        }
        return true;
    }
    @Override public void onClose() { cancelPointer(); editor.requestClose(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { cancelPointer();if(!returning)abandoned.run();graphics.close(); super.removed(); }
}
