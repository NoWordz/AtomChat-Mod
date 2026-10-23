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
    private final NotificationHudEditor editor = new NotificationHudEditor(key -> Component.translatable(key).getString());
    private final SkiaGraphics graphics = new SkiaGraphics();
    public NotificationHudEditorScreen(Screen parent) {
        super(Component.translatable("atomchat.notify.editor.title")); this.parent=parent;
    }
    private float vx(double x) { return ScreenMetrics.toVirtualX(minecraft.getWindow(),x); }
    private float vy(double y) { return ScreenMetrics.toVirtualY(minecraft.getWindow(),y); }
    @Override public void render(GuiGraphics gui,int mx,int my,float delta) {
        gui.flush(); graphics.checkFrameBufferId();
        graphics.draw(null,ScreenMetrics.uiDensity(minecraft.getWindow()),
                (canvas,snapshot)->editor.render(canvas,vx(width),vy(height),vx(mx),vy(my)));
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        var action=editor.click(vx(x),vy(y),vx(width),vy(height),button);
        if(action==NotificationHudEditor.Action.SAVE) { editor.save(); onClose(); }
        else if(action==NotificationHudEditor.Action.CANCEL) onClose();
        return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(button==0) editor.drag(vx(x),vy(y),vx(width),vy(height)); return true;
    }
    @Override public boolean mouseReleased(double x,double y,int button) { editor.release(); return true; }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
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
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { graphics.close(); super.removed(); }
}
