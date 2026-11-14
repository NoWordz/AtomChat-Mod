package com.atom.chat.notification;

import com.atom.chat.render.SkiaGraphics;
import com.atom.chat.screen.ScreenMetrics;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Thin native screen bridge; geometry and interaction live in the shared editor. */
public final class NotificationHudEditorScreen extends Screen {
    private final Screen parent;
    private final Runnable returned,abandoned;
    private boolean returning;
    private final NotificationHudEditor editor = new NotificationHudEditor(key -> Text.translatable(key).getString());
    private final SkiaGraphics graphics = new SkiaGraphics();
    public NotificationHudEditorScreen(Screen parent){this(parent,()->{},()->{});}
    public NotificationHudEditorScreen(Screen parent,Runnable returned,Runnable abandoned) {
        super(Text.translatable("atomchat.notify.editor.title"));this.parent=parent;this.returned=returned;this.abandoned=abandoned;
    }
    private float vx(double x) { return ScreenMetrics.toVirtualX(client.getWindow(),x); }
    private float vy(double y) { return ScreenMetrics.toVirtualY(client.getWindow(),y); }
    @Override public void render(DrawContext gui,int mx,int my,float delta) {
        gui.draw(); graphics.checkFrameBufferId();
        graphics.draw(null,ScreenMetrics.uiDensity(client.getWindow()),
                (canvas,snapshot)->editor.render(canvas,vx(width),vy(height),vx(mx),vy(my)));
        if(editor.closed()){returning=true;client.setScreen(parent);returned.run();}
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        var action=editor.click(vx(x),vy(y),vx(width),vy(height),button);
        if(action==NotificationHudEditor.Action.SAVE) { editor.save(); close(); }
        else if(action==NotificationHudEditor.Action.CANCEL) close();
        return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(button==0) editor.drag(vx(x),vy(y),vx(width),vy(height)); return true;
    }
    @Override public boolean mouseReleased(double x,double y,int button) { editor.release(); return true; }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(editor.closing())return true;
        if(key==GLFW.GLFW_KEY_ESCAPE || key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_KP_ENTER)
            com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);
        if(key==GLFW.GLFW_KEY_ESCAPE) close();
        else if(key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_KP_ENTER) { editor.save(); close(); }
        else {
            float step=(modifiers & GLFW.GLFW_MOD_SHIFT)!=0 ? 10 : 1;
            if(key==GLFW.GLFW_KEY_LEFT) editor.nudge(-step,0);
            if(key==GLFW.GLFW_KEY_RIGHT) editor.nudge(step,0);
            if(key==GLFW.GLFW_KEY_UP) editor.nudge(0,-step);
            if(key==GLFW.GLFW_KEY_DOWN) editor.nudge(0,step);
        }
        return true;
    }
    @Override public void close() { editor.requestClose(); }
    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { if(!returning)abandoned.run();graphics.close(); super.removed(); }
}
