package com.atom.chat.tools;

import com.atom.chat.api.ui.ComposerUi;
import com.atom.chat.api.ui.UiContributions;
import com.atom.chat.render.CornerGeometry;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.chat.ui.ClickRipples;
import com.atom.chat.ui.ComposerButtonLayout;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.render.DrawPrimitives;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.ui.input.ClickTarget;
import com.atom.chat.ui.input.InputHandler;
import io.github.humbleui.skija.Canvas;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

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
        default UiContributions<ComposerUi.MenuItem> menuExtensions() { return ComposerUi.menu(); }
        default void insertExtensionText(String text) {
            String draft = composerText();
            int caret = Math.max(0, Math.min(composerCaret(), draft.length()));
            setComposerText(draft.substring(0, caret) + text + draft.substring(caret));
            moveComposerCaret(caret + text.length());
        }
        default void submitExtensionDraft() {}
        default void openExtensionPage(String pageId) {}
        default boolean canUseExtensionButtons() { return chatVisible(); }
    }
    private enum MenuAction { TOGGLE, DISMISS, BACK, MENTION, MENTION_ALL }

    private static final System.Logger LOGGER = System.getLogger(ComposerTools.class.getName());
    private final Host host;
    private final List<UiContributions.Entry<ComposerUi.MenuItem>> defaults;
    private final ToolMenu menu;
    private final ComposerSearch search;
    private float playerRefresh;
    private String pressedExtensionButton;

    public ComposerTools(Host host) {
        this.host = host;
        search = new ComposerSearch(host);
        defaults = ToolMenu.defaults(this::activateBuiltIn);
        menu = new ToolMenu(defaults);
    }

    public UiLayout.Rect tutorialBounds(){return menu.isOpen()?menu.paintedBounds(host.layout()):null;}
    public UiLayout.Rect tutorialActionBounds(int originalRow) {
        return originalRow >= 0 && originalRow < defaults.size() ? menuActionBounds(defaults.get(originalRow).id()) : null;
    }
    public UiLayout.Rect menuActionBounds(String actionId) {
        return menu.paintedRow(host.layout(), menu.indexOfAction(actionId));
    }
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
        if (!menu.isOpen()) {
            menu.setEntries(com.atom.chat.onboarding.TutorialSandbox.active()
                    ? defaults : host.menuExtensions().resolve(defaults));
        }
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
        if (button == 0 && host.chatVisible() && layout.builtInComposerButton(ComposerUi.TOOLS_BUTTON)
                && layout.imageBtn.contains(mx, my)) {
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
            var entry = menu.actionAt(row);
            return entry == null ? ClickTarget.blocked()
                    : rowTarget(row, entry.id(), () -> activateMenuEntry(entry));
        }
        if (button == 0 && host.canUseExtensionButtons()) {
            for (var slot : layout.composerButtons) {
                if (!slot.builtIn() && slot.rect().w() > 0 && slot.rect().contains(mx, my)) {
                    return new ClickTarget(slot.id(), () -> pressedExtensionButton = slot.id(),
                            () -> pressedExtensionButton = null, () -> activateExtensionButton(slot));
                }
            }
        }
        if (searching() && layout.builtInComposerButton(ComposerUi.SEND_BUTTON)
                && layout.sendBtn.contains(mx, my)) return ClickTarget.blocked();
        return null;
    }

    private void activateExtensionButton(ComposerButtonLayout.Slot slot) {
        try {
            invokeExtension(slot.button().action());
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Composer button failed: " + slot.id(), exception);
        }
    }

    /** Paints add-on buttons at the same positions used by clickTarget. */
    public void renderExtensionButtons(Canvas canvas, UiLayout layout, float mx, float my) {
        if (layout.composerButtons.isEmpty()) return;
        var theme = AtomChatAppearance.theme();
        for (var slot : layout.composerButtons) {
            if (slot.builtIn() || slot.rect().w() <= 0) continue;
            var rect = slot.rect();
            float radius = UiTokens.radius(8, theme);
            boolean pressed = slot.id().equals(pressedExtensionButton);
            boolean hover = rect.contains(mx, my);
            boolean capsule = slot.button().shape() == ComposerUi.ButtonShape.CAPSULE;
            int fill = capsule ? UiTokens.accentFill(theme) : UiTokens.cardFill(theme);
            ClickRipples.region(rect.x(), rect.y(), rect.w(), rect.h(), radius, fill);
            if (capsule) CornerGeometry.drawRoundedRect(canvas, rect.x(), rect.y(), rect.w(), rect.h(), radius, fill);
            if (hover || pressed) {
                CornerGeometry.drawRoundedRect(canvas, rect.x(), rect.y(), rect.w(), rect.h(), radius,
                        UiTokens.cardHover(pressed ? 1F : .65F, theme));
            }
            var config = com.atom.chat.config.AtomChatConfig.get();
            DrawPrimitives.drawIconCentered(canvas, slot.button().icon(), rect.x() + rect.w() / 2,
                    rect.y() + rect.h() / 2, Math.min(rect.w(), UiTokens.COMPOSER_ICON_SIZE),
                    capsule ? UiTokens.onAccent(fill) : config.textPrimaryColor);
        }
    }

    private void invokeExtension(Consumer<ComposerUi.Context> action) {
        var context = new ActionContext();
        try {
            action.accept(context);
        } finally {
            context.active = false;
        }
    }

    private final class ActionContext implements ComposerUi.Context {
        private final Thread thread = Thread.currentThread();
        private final Object conversation = host.searchScope();
        private boolean active = true;
        private boolean submitted;

        private boolean current() {
            return Thread.currentThread() == thread && active && host.chatVisible()
                    && Objects.equals(conversation, host.searchScope());
        }

        public String draft() { return !submitted && current() ? search.draft() : ""; }

        public void replaceDraft(String text) {
            if (submitted || !current() || !host.canUseExtensionButtons()) return;
            search.close();
            host.setComposerText(text);
        }

        public void insertText(String text) {
            if (submitted || !current() || !host.canUseExtensionButtons()) return;
            search.close();
            host.insertExtensionText(text);
        }

        public void submitDraft() {
            if (submitted || !current() || !host.canUseExtensionButtons()) return;
            submitted = true;
            search.close();
            host.submitExtensionDraft();
        }

        public void openPage(String pageId) {
            if (!current()) return;
            close();
            host.openExtensionPage(pageId);
        }
    }

    private void activateMenuEntry(UiContributions.Entry<ComposerUi.MenuItem> entry) {
        try {
            if (!entry.value().keepOpen()) menu.close();
            invokeExtension(entry.value().action());
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Composer menu entry failed: " + entry.id(), exception);
        }
    }

    private void activateBuiltIn(String id) {
        switch (id) {
            case ComposerUi.SEARCH -> search.open();
            case ComposerUi.PLAYERS -> {
                search.close();
                menu.showPlayers(host.onlinePlayers(), host.canMentionAll());
                menu.measure(host::tr);
            }
            case ComposerUi.IMAGE -> pickImage();
            case ComposerUi.SHARE_LOCATION -> shareCard(host::shareLocation);
            case ComposerUi.SHARE_ITEM -> shareCard(host::shareHeldItem);
        }
    }

    private ClickTarget rowTarget(int row, Object action, Runnable activate) {
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
