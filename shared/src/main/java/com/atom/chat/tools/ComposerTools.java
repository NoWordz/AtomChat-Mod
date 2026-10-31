package com.atom.chat.tools;

import com.atom.chat.chat.PlayerRef;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.ui.input.InputHandler;
import io.github.humbleui.skija.Canvas;
import java.util.List;

/** Routes composer modes and the anchored tools popup; never replaces a conversation page. */
public final class ComposerTools implements InputHandler {
    public interface Host extends ComposerSearch.Host {
        UiLayout layout();
        float x(double x);
        float y(double y);
        boolean chatVisible();
        String tr(String key);
        List<PlayerRef> onlinePlayers();
        void mention(PlayerRef player);
        void feedback(String key);
        void pressTool();
        void releaseTool();
        void pickImage();
        void dismissOtherPopups();
    }
    private final Host host;
    private final ToolMenu menu = new ToolMenu();
    private final ComposerSearch search;
    private float playerRefresh;
    public ComposerTools(Host host) { this.host = host; search = new ComposerSearch(host); }
    public boolean active() { return menu.isOpen() || searching(); }
    public boolean searching() { return search.active(); }
    public boolean appendToParkedDraft(String text) { return search.appendToParkedDraft(text); }
    public void close() { menu.close(); search.close(); }
    public void toggle() {
        if (!host.chatVisible()) return;
        host.pressTool(); host.dismissOtherPopups(); menu.toggle();
    }
    public void update() { search.update(); }
    public void render(Canvas canvas, float mx, float my, float dt) {
        if (!host.chatVisible()) { close(); return; }
        var layout = host.layout();
        if (searching()) {
            var font = FontManager.font(UiTokens.FONT_BUTTON);
            String status = search.count() == 0 ? host.tr("atomchat.tools.search.empty") : search.position() + " / " + search.count();
            status += "  " + host.tr("atomchat.tools.search.keys");
            var hint = layout.searchStatus;
            canvas.save();
            try {
                com.atom.lib.design.atomchat.SkiaDraw.clip(canvas, hint.x(), hint.y(), hint.w(), hint.h(), 0);
                SkiaFontRenderer.drawTextCentered(canvas, font, status, hint.x() + hint.w() / 2, hint.y() + hint.h() / 2,
                        com.atom.chat.config.AtomChatConfig.get().textSecondaryColor);
            } finally { canvas.restore(); }
        }
        playerRefresh += Math.max(0, dt);
        if (menu.isOpen() && menu.playersOpen() && playerRefresh >= 250) {
            menu.refreshPlayers(host.onlinePlayers()); playerRefresh = 0;
        }
        menu.render(canvas, layout, mx, my, dt, host::tr);
    }
    @Override public boolean onClick(double x, double y, int button) {
        float mx = host.x(x), my = host.y(y);
        if (button == 0 && host.chatVisible() && host.layout().imageBtn.contains(mx, my)) { toggle(); return true; }
        if (menu.isOpen()) {
            int row = button == 0 ? menu.hit(host.layout(), mx, my) : -1;
            if (menu.playersOpen() && row >= 0) {
                if (row == 0) menu.back();
                else {
                    PlayerRef player = menu.playerAt(row);
                    if (player != null && host.onlinePlayers().contains(player)) { close(); host.mention(player); }
                    else if (player != null) { menu.refreshPlayers(host.onlinePlayers()); host.feedback("atomchat.tools.players.unavailable"); }
                }
            } else {
                menu.close();
                if (row == 0) search.open();
                if (row == 1) { search.close(); menu.toggle(); menu.showPlayers(host.onlinePlayers()); }
                if (row == 2) { search.close(); host.pickImage(); }
            }
            return true;
        }
        if (searching()) {
            if (host.layout().sendBtn.contains(mx, my)) return true;
            if (host.layout().emojiBtn.contains(mx, my) || host.layout().phraseBtn.contains(mx, my)) search.close();
            if (!host.layout().inputBar.contains(mx, my) && !host.layout().list.contains(mx, my)) search.close();
        }
        return false;
    }
    @Override public boolean onKey(int key, int scanCode, int modifiers) {
        if (menu.isOpen()) {
            if (key == 256 && menu.playersOpen()) menu.back();
            else menu.close();
            return key == 256;
        }
        if (!searching()) return false;
        if (key == 256) search.close();
        else if (key == 265) search.move(-1);
        else if (key == 264 || key == 257 || key == 335) search.move(1);
        else return false; // The real EditBox keeps typing, paste, selection, caret and IME.
        return true;
    }
    @Override public boolean onChar(char chr, int modifiers) { return menu.isOpen(); }
    @Override public boolean onScroll(double x, double y, double horizontal, double vertical) {
        if (menu.isOpen()) { menu.scroll(host.layout(), vertical); return true; }
        if (searching()) { if (vertical != 0) search.move(vertical > 0 ? -1 : 1); return true; }
        return false;
    }
    @Override public boolean onDrag(double x, double y, int button, double dx, double dy) { return menu.isOpen(); }
    @Override public boolean onRelease(double x, double y, int button) {
        if (button == 0) host.releaseTool();
        return menu.isOpen();
    }
}
