package com.atom.chat.tools;

import com.atom.chat.render.CornerGeometry;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.ui.input.ClickTarget;
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
        default boolean canMentionAll() { return false; }
        default void mentionAll() {}
        void feedback(String key);
        void pressTool();
        void releaseTool();
        void pickImage();
        default void shareLocation() {}
        default void shareHeldItem() {}
        void dismissOtherPopups();
    }
    private enum MenuAction { TOGGLE, DISMISS, BACK, MENTION, MENTION_ALL, SEARCH, PLAYERS, IMAGE, SHARE_LOCATION, SHARE_ITEM }

    private final Host host;
    private final ToolMenu menu = new ToolMenu();
    private final ComposerSearch search;
    private float playerRefresh;
    public ComposerTools(Host host) { this.host = host; search = new ComposerSearch(host); }
    public UiLayout.Rect tutorialBounds(){return menu.isOpen()?menu.paintedBounds(host.layout()):null;}
    public UiLayout.Rect tutorialActionBounds(int row) { return menu.paintedRow(host.layout(), row); }
    public boolean active() { return menu.isOpen() || searching(); }
    public boolean searching() { return search.active(); }
    public com.atom.chat.chat.DraftStore.ComposerTextPort draftPort(com.atom.chat.chat.DraftStore.ComposerTextPort live) {
        return search.draftPort(live);
    }
    public boolean appendToParkedDraft(String text) { return search.appendToParkedDraft(text); }
    public void close() { menu.close(); search.close(); }
    public void toggle() {
        if (!host.chatVisible()) return;
        host.pressTool();
        toggleMenu();
    }

    private void toggleMenu() {
        host.dismissOtherPopups();
        menu.toggle();
        if (menu.isOpen()) menu.measure(host::tr);
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
                CornerGeometry.clip(canvas, hint.x(), hint.y(), hint.w(), hint.h(), 0);
                SkiaFontRenderer.drawTextCentered(canvas, font, status, hint.x() + hint.w() / 2, hint.y() + hint.h() / 2,
                        com.atom.chat.config.AtomChatConfig.get().textSecondaryColor);
            } finally { canvas.restore(); }
        }
        playerRefresh += Math.max(0, dt);
        if (menu.isOpen() && menu.playersOpen() && playerRefresh >= 250) {
            refreshPlayers(); playerRefresh = 0;
        }
        menu.render(canvas, layout, mx, my, dt, host::tr);
    }
    @Override public ClickTarget clickTarget(double x, double y, int button) {
        float mx = host.x(x), my = host.y(y);
        UiLayout layout = host.layout();
        if (button == 0 && host.chatVisible() && layout.imageBtn.contains(mx, my)) {
            return new ClickTarget(MenuAction.TOGGLE, host::pressTool, host::releaseTool, this::toggleMenu);
        }
        if (menu.isOpen()) {
            int row = button == 0 ? menu.hit(layout, mx, my) : -1;
            if (row < 0) return ClickTarget.action(MenuAction.DISMISS, menu::close);
            if (menu.playersOpen()) {
                if (row == 0) return rowTarget(row, MenuAction.BACK, this::back);
                if (menu.isMentionAll(row)) {
                    return rowTarget(row, MenuAction.MENTION_ALL, this::mentionAll);
                }
                PlayerRef player = menu.playerAt(row);
                if (player == null) return ClickTarget.blocked();
                return rowTarget(row, MenuAction.MENTION, () -> mention(player));
            }
            return switch (row) {
                case 0 -> rowTarget(row, MenuAction.SEARCH, () -> { menu.close(); search.open(); });
                case 1 -> rowTarget(row, MenuAction.PLAYERS, () -> {
                    search.close();
                    menu.showPlayers(host.onlinePlayers(), host.canMentionAll());
                    menu.measure(host::tr);
                });
                case 2 -> rowTarget(row, MenuAction.IMAGE, this::pickImage);
                case 3 -> rowTarget(row, MenuAction.SHARE_LOCATION, () -> shareCard(host::shareLocation));
                case 4 -> rowTarget(row, MenuAction.SHARE_ITEM, () -> shareCard(host::shareHeldItem));
                default -> ClickTarget.blocked();
            };
        }
        if (searching() && layout.sendBtn.contains(mx, my)) return ClickTarget.blocked();
        return null;
    }

    private ClickTarget rowTarget(int row, MenuAction action, Runnable activate) {
        return new ClickTarget(action, () -> menu.pressRow(row), () -> menu.pressRow(-1), activate);
    }

    private void mention(PlayerRef player) {
        if (host.onlinePlayers().contains(player)) {
            close();
            host.mention(player);
        } else {
            refreshPlayers();
            host.feedback("atomchat.tools.players.unavailable");
        }
    }

    private void refreshPlayers() {
        menu.refreshPlayers(host.onlinePlayers(), host.canMentionAll());
        menu.measure(host::tr);
    }

    private void back() {
        menu.back();
        menu.measure(host::tr);
    }

    private void mentionAll() {
        if (!host.canMentionAll()) {
            refreshPlayers();
            return;
        }
        close();
        if (com.atom.chat.onboarding.TutorialSandbox.active()) {
            host.feedback("atomchat.tutorial.preview_only");
        } else {
            host.mentionAll();
        }
    }

    private void shareCard(Runnable action) {
        close();
        if (com.atom.chat.onboarding.TutorialSandbox.active()) {
            host.feedback("atomchat.tutorial.preview_only");
        } else action.run();
    }

    private void pickImage() {
        close();
        if (com.atom.chat.onboarding.TutorialSandbox.active()) {
            com.atom.chat.onboarding.TutorialSandbox.imageAction();
            host.feedback("atomchat.tutorial.preview_only");
        } else host.pickImage();
    }

    @Override public boolean onClick(double x, double y, int button) {
        float mx = host.x(x), my = host.y(y);
        if (searching()) {
            if (host.layout().emojiBtn.contains(mx, my) || host.layout().phraseBtn.contains(mx, my)) search.close();
            if (!host.layout().inputBar.contains(mx, my) && !host.layout().list.contains(mx, my)) search.close();
        }
        return false;
    }
    @Override public boolean onKey(int key, int scanCode, int modifiers) {
        if (menu.isOpen()) {
            if (key == 256 && menu.playersOpen()) back();
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
