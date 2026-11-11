package com.atom.chat.onboarding;

import com.atom.chat.chat.PlayerRef;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.ImagePreviewOverlay;
import com.atom.chat.render.DrawPrimitives;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.tools.ToolLayout;
import com.atom.chat.tools.ToolMenu;
import com.atom.chat.ui.ContextMenuController;
import com.atom.chat.ui.EmojiPanel;
import com.atom.chat.ui.QuickPhrasePanel;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Path;

import java.util.List;
import java.util.function.Function;

/** Live-looking, side-effect-free tutorial surfaces backed by the production popup renderers. */
public final class TutorialDemoPanels implements AutoCloseable {
    private static final PlayerRef DEMO_PLAYER = PlayerRef.of(null, "演示玩家");
    private static final List<DemoMessage> DEMO_MESSAGES = List.of(
            new DemoMessage(false, "Alice", "集合点在北门"),
            new DemoMessage(false, "Steve", "有人需要帮助吗？"),
            new DemoMessage(true, "服务器", "今晚八点活动"));

    private final EmojiPanel emoji;
    private final QuickPhrasePanel phrases;
    private final ToolMenu tools = new ToolMenu();
    private final ContextMenuController<String> menu;
    private final ImagePreviewOverlay imagePreview = new ImagePreviewOverlay();

    private Function<String, String> translate = Function.identity();
    private UiLayout currentLayout;
    private TutorialSession.Step currentStep;
    private float frameDt = 16.0F;
    private String demoText = "";
    private String searchQuery = "";
    private String editBuffer = "";
    private int searchIndex = -1;
    private int filterIndex;
    private boolean searchOpen;
    private boolean quoteArmed;
    private boolean blockDemonstrated;
    private boolean closed;
    private Image sampleImage;
    private boolean previewDemoOpen;
    private MenuKind menuKind = MenuKind.NONE;
    private float menuX, menuY;

    private enum MenuKind { NONE, MESSAGE, PLAYER }
    private record DemoMessage(boolean system, String sender, String body) { }

    public TutorialDemoPanels() {
        emoji = EmojiPanel.sandbox(new EmojiPanel.Host() {
            public void insert(String text) { appendDemoText(text); }
            public void sendSticker(java.nio.file.Path file) { }
            public void pickEmoteFile() { }
        });
        phrases = QuickPhrasePanel.sandbox(text -> appendDemoText(text),
                () -> AtomChatConfig.get().accentColor);
        menu = new ContextMenuController<>(new ContextMenuController.Host() {
            public String label(String key) { return translate.apply(key); }
            public boolean isPlayerOnline(PlayerRef player) { return true; }
            public UiLayout.Rect panelRect() {
                return currentLayout == null ? new UiLayout.Rect(0, 0, 0, 0) : currentLayout.rect();
            }
            public float frameDt() { return frameDt; }
            public void drawIcon(Canvas canvas, Path icon, float cx, float cy, float size, int color) {
                DrawPrimitives.drawIconCentered(canvas, icon, cx, cy, size, color);
            }
        });
    }

    /** Prepares a reusable sandbox. Surfaces themselves open lazily from click(). */
    public boolean open() {
        return !closed;
    }
    public String previewText() { return phrases.isEditing() ? editBuffer : demoText; }
    public Image thumbnail() { return sampleImage(); }
    public void prepare(TutorialSession session, UiLayout layout, Function<String,String> tr, float dt) {
        remember(session,layout,tr,dt);
    }

    public void reset() {
        closePanels();
        demoText = "";
        searchQuery = "";
        editBuffer = "";
        searchIndex = -1;
        filterIndex = 0;
        searchOpen = false;
        quoteArmed = false;
        blockDemonstrated = false;
        currentStep = null;
        currentLayout = null;
    }

    public void render(Canvas canvas, TutorialSession session, UiLayout layout,
                       Function<String, String> tr, float mx, float my, float dt) {
        if (closed || session == null || !session.active()) return;
        remember(session, layout, tr, dt);
        if (searchOpen) drawSearchDemo(canvas, layout);
        if (quoteArmed) drawQuoteBar(canvas, layout);
        if (emoji.isOpen()) {
            emoji.update((long) dt);
            emoji.render(canvas, layout, mx, my, (long) dt);
        }
        if (phrases.isOpen()) {
            phrases.update((long) dt);
            phrases.render(canvas, layout, mx, my, (long) dt);
        }
        if (tools.isOpen()) tools.render(canvas, layout, mx, my, dt, translate);
        if (menuKind != MenuKind.NONE) menu.draw(canvas, mx, my);
        if (previewDemoOpen && imagePreview.isActive()) imagePreview.render(canvas, imagePreviewPanel(layout), mx, my);
    }

    public boolean click(TutorialSession session, UiLayout layout, float x, float y, int button) {
        if (closed || session == null || !session.active()) return false;
        remember(session, layout, translate, frameDt);

        if (previewDemoOpen && imagePreview.isActive()) {
            imagePreview.onClick(x, y, imagePreviewPanel(layout));
            return true;
        }
        if (menuKind != MenuKind.NONE) return clickMenu(session, x, y, button);
        if (emoji.isOpen()) {
            if (emoji.overPanel(layout, x, y)) {
                String inserted = emoji.click(layout, x, y);
                if (inserted != null && !inserted.isEmpty()) appendDemoText(inserted);
                session.performDemo();
            } else {
                emoji.close();
            }
            return true;
        }
        if (phrases.isOpen()) {
            if (phrases.overPanel(layout, x, y)) {
                handlePhraseAction(session, phrases.click(layout, x, y));
            } else {
                phrases.close();
            }
            return true;
        }
        if (tools.isOpen()) return clickToolMenu(session, layout, x, y);

        if (session.step() == TutorialSession.Step.EXPRESSIONS) {
            if (button == 0 && contains(layout.emojiBtn, x, y)) {
                emoji.toggle();
                return true;
            }
        } else if (session.step() == TutorialSession.Step.QUICK_PHRASES) {
            if (button == 0 && contains(layout.phraseBtn, x, y)) {
                phrases.open();
                return true;
            }
        } else if (session.step() == TutorialSession.Step.IMAGES
                || session.step() == TutorialSession.Step.SEARCH_FILTER) {
            if (button == 0 && contains(layout.imageBtn, x, y)) {
                tools.toggle();
                return true;
            }
            if (session.step() == TutorialSession.Step.SEARCH_FILTER
                    && searchOpen && contains(filterRect(layout), x, y)) {
                filterIndex = (filterIndex + 1) % 3;
                session.performDemo();
                return true;
            }
        } else if (session.step() == TutorialSession.Step.PLAYERS_BLOCK) {
            if ((button == 0 || button == 1) && contains(layout.list, x, y)) {
                menuKind = MenuKind.PLAYER;
                menuX = x;
                menuY = y;
                menu.openPlayerCard(DEMO_PLAYER, x, y);
                return true;
            }
        } else if (session.step() == TutorialSession.Step.MESSAGE_ACTIONS) {
            if ((button == 0 || button == 1) && contains(layout.list, x, y)) {
                menuKind = MenuKind.MESSAGE;
                menuX = x;
                menuY = y;
                menu.openBubble(ContextMenuController.Subject.of(DEMO_PLAYER, false, "demo-message"), x, y);
                return true;
            }
        }
        return false;
    }

    public boolean scroll(UiLayout layout, double amount) {
        if (closed || layout == null) return false;
        currentLayout = layout;
        if (previewDemoOpen && imagePreview.isActive()) {
            UiLayout.Rect panel = imagePreviewPanel(layout);
            imagePreview.onScroll(panel.x() + panel.w() / 2,
                    panel.y() + panel.h() / 2, panel, amount);
            return true;
        }
        if (emoji.isOpen()) { emoji.scroll(layout, amount); return true; }
        if (phrases.isOpen()) { phrases.scroll(layout, amount); return true; }
        if (tools.isOpen()) { tools.scroll(layout, amount); return true; }
        return searchOpen;
    }

    public boolean key(TutorialSession session, int key, int modifiers) {
        if (closed || session == null || !session.active()) return false;
        if (key == 256) {
            if (previewDemoOpen && imagePreview.isActive() || emoji.isOpen() || phrases.isOpen()
                    || tools.isOpen() || menuKind != MenuKind.NONE || searchOpen) {
                closePanels();
                return true;
            }
            return false;
        }
        if (phrases.isEditing()) {
            if (key == 259) {
                if (!editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.offsetByCodePoints(editBuffer.length(), -1));
                return true;
            }
            if (key == 257 || key == 335) {
                phrases.commitText(editBuffer);
                editBuffer = "";
                session.performDemo();
                return true;
            }
            return true;
        }
        if (searchOpen) {
            if (key == 259) {
                if (!searchQuery.isEmpty()) searchQuery = searchQuery.substring(0, searchQuery.offsetByCodePoints(searchQuery.length(), -1));
                searchIndex = -1;
            } else if (key == 265 || key == 264 || key == 257 || key == 335) {
                List<DemoMessage> hits = searchHits();
                if (!hits.isEmpty()) searchIndex = Math.floorMod(searchIndex + ((key == 265) ? -1 : 1), hits.size());
            }
            session.performDemo();
            return true;
        }
        return emoji.isOpen() || tools.isOpen() || menuKind != MenuKind.NONE;
    }

    public boolean type(TutorialSession session, char chr) {
        if (closed || session == null || !session.active() || Character.isISOControl(chr)) return false;
        if (phrases.isEditing()) {
            if (editBuffer.length() < AtomChatConfig.MAX_QUICK_PHRASE_LENGTH) editBuffer += chr;
            return true;
        }
        if (searchOpen) {
            if (searchQuery.length() < 80) searchQuery += chr;
            searchIndex = -1;
            session.performDemo();
            return true;
        }
        return false;
    }

    public UiLayout.Rect bounds(UiLayout layout) {
        if (layout == null) return new UiLayout.Rect(0, 0, 0, 0);
        currentLayout = layout;
        if (previewDemoOpen && imagePreview.isActive()) return imagePreviewPanel(layout);
        if (menuKind != MenuKind.NONE) return contextMenuBounds(layout);
        if (emoji.isOpen()) return emoji.bounds(layout);
        if (phrases.isOpen()) return phrases.bounds(layout);
        if (tools.isOpen()) return tools.bounds(layout);
        if (searchOpen && currentStep == TutorialSession.Step.SEARCH_FILTER) {
            var area=searchDemoArea(layout);
            return new UiLayout.Rect(layout.header.x(),layout.header.y(),layout.header.w(),area.bottom()-layout.header.y());
        }
        if (quoteArmed && currentStep == TutorialSession.Step.MESSAGE_ACTIONS) return quoteBarRect(layout);
        if (currentStep == TutorialSession.Step.EXPRESSIONS) return layout.emojiBtn;
        if (currentStep == TutorialSession.Step.IMAGES) return layout.imageBtn;
        if (currentStep == TutorialSession.Step.QUICK_PHRASES) return layout.phraseBtn;
        if (currentStep == TutorialSession.Step.SEARCH_FILTER) return layout.imageBtn;
        if (currentStep == TutorialSession.Step.MESSAGE_ACTIONS || currentStep == TutorialSession.Step.PLAYERS_BLOCK)
            return layout.list;
        return layout.rect();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        closePanels();
        emoji.dispose();
        if (sampleImage != null) sampleImage.close();
        sampleImage = null;
    }

    private boolean clickToolMenu(TutorialSession session, UiLayout layout, float x, float y) {
        int row = tools.hit(layout, x, y);
        if (row < 0) { tools.close(); return true; }
        if (session.step() == TutorialSession.Step.IMAGES && row == 2) {
            tools.close();
            imagePreview.open(sampleImage());
            previewDemoOpen = true;
            session.performDemo();
            return true;
        }
        if (session.step() == TutorialSession.Step.SEARCH_FILTER && row == 0) {
            tools.close();
            searchOpen = true;
            searchQuery = "";
            searchIndex = -1;
            session.performDemo();
            return true;
        }
        return true;
    }

    private boolean clickMenu(TutorialSession session, float x, float y, int button) {
        if (button != 0) {
            if (!contextMenuBounds(currentLayout).contains(x, y)) closeMenu();
            return true;
        }
        if (menuKind == MenuKind.MESSAGE) {
            int row = menu.hitRow(x, y);
            if (row == 1) {
                quoteArmed = true;
                session.performDemo();
                closeMenu();
            } else if (row >= 0) {
                closeMenu();
            }
            return true;
        }
        if (menuKind == MenuKind.PLAYER) {
            int row = menu.hitPlayerCardRow(x, y);
            if (row == 2) {
                blockDemonstrated = true;
                session.performDemo();
                closeMenu();
            } else if (row >= 0) {
                closeMenu();
            }
            return true;
        }
        return true;
    }

    private void handlePhraseAction(TutorialSession session, QuickPhrasePanel.Action action) {
        switch (action.type()) {
            case QuickPhrasePanel.Action.INSERT -> {
                phrases.pickRow(action.index());
                session.performDemo();
            }
            case QuickPhrasePanel.Action.INSERT_SERVER -> {
                int position = phrases.localPhraseCount() + 1 + action.index();
                appendDemoText(phrases.rowTextAt(position));
                phrases.close();
                session.performDemo();
            }
            case QuickPhrasePanel.Action.ADD -> {
                phrases.startAdd();
                editBuffer = "";
            }
            case QuickPhrasePanel.Action.EDIT -> {
                phrases.startEdit(action.index());
                editBuffer = phrases.phraseAt(action.index());
            }
            case QuickPhrasePanel.Action.DELETE -> {
                phrases.deleteAt(action.index());
                session.performDemo();
            }
            default -> { }
        }
    }

    private void drawSearchDemo(Canvas canvas, UiLayout layout) {
        float pad = UiTokens.SETTINGS_ROW_PAD;
        UiLayout.Rect area = searchDemoArea(layout);
        SkiaDraw.drawRoundedRect(canvas, area.x(), area.y(), area.w(), area.h(), UiTokens.settingsRowRadius(), 0xEE232A35);
        var font = FontManager.font(UiTokens.FONT_QUOTE);
        String mode = translate.apply(switch (filterIndex) {
            case 1 -> "atomchat.tutorial.demo.filter.system";
            case 2 -> "atomchat.tutorial.demo.filter.players";
            default -> "atomchat.tools.filter.all";
        });
        String title = searchQuery.isEmpty() ? translate.apply("atomchat.tools.search.hint")
                : searchQuery + "  ·  " + mode;
        SkiaFontRenderer.drawText(canvas, font, SkiaFontRenderer.truncate(font, title, area.w() - pad * 2),
                area.x() + pad, area.y() + pad + UiTokens.DIALOG_LINE_HEIGHT * .7F, 0xFFFFFFFF);
        float y = area.y() + pad + UiTokens.DIALOG_LINE_HEIGHT * 1.5F;
        List<DemoMessage> hits = searchHits();
        for (int i = 0; i < Math.min(3, hits.size()); i++) {
            DemoMessage hit = hits.get(i);
            String prefix = i == searchIndex ? "› " : "  ";
            SkiaFontRenderer.drawText(canvas, font,
                    prefix + hit.sender() + ": " + hit.body(), area.x() + pad,
                    y, i == searchIndex ? AtomChatConfig.get().accentColor : 0xFFD5DAE3);
            y += UiTokens.DIALOG_LINE_HEIGHT;
        }
        UiLayout.Rect filter = filterRect(layout);
        try (var paint = new io.github.humbleui.skija.Paint().setAntiAlias(true)
                .setMode(io.github.humbleui.skija.PaintMode.STROKE)
                .setStrokeWidth(UiTokens.FOCUS_RING_WIDTH).setColor(AtomChatConfig.get().accentColor)) {
            canvas.drawRRect(io.github.humbleui.types.RRect.makeXYWH(
                    filter.x(), filter.y(), filter.w(), filter.h(), UiTokens.settingsRowRadius()), paint);
        }
    }

    private void drawQuoteBar(Canvas canvas, UiLayout layout) {
        var rect = quoteBarRect(layout);
        SkiaDraw.drawRoundedRect(canvas, rect.x(), rect.y(), rect.w(), rect.h(), UiTokens.settingsRowRadius(), 0xEE303849);
        var font = FontManager.font(UiTokens.FONT_QUOTE);
        String text = translate.apply("atomchat.context.quote") + "  " + DEMO_PLAYER.displayName()
                + ": " + translate.apply("atomchat.tutorial.demo.quote");
        SkiaFontRenderer.drawText(canvas, font, SkiaFontRenderer.truncate(font, text, rect.w() - UiTokens.SETTINGS_ROW_PAD * 2),
                rect.x() + UiTokens.SETTINGS_ROW_PAD, rect.y() + rect.h() / 2, 0xFFE8EDF5);
    }

    private List<DemoMessage> searchHits() {
        String query = searchQuery.trim().toLowerCase(java.util.Locale.ROOT);
        return DEMO_MESSAGES.stream()
                .filter(m -> filterIndex == 0 || filterIndex == 1 && m.system() || filterIndex == 2 && !m.system())
                .filter(m -> query.isEmpty() || (m.sender() + " " + m.body()).toLowerCase(java.util.Locale.ROOT).contains(query))
                .toList();
    }

    private UiLayout.Rect contextMenuBounds(UiLayout layout) {
        return menu.surfaceRect();
    }

    /** Same position and size as the shell's world-chat header filter button. */
    private static UiLayout.Rect filterRect(UiLayout layout) {
        float size = UiTokens.ACTION_BUTTON_SIZE;
        float backX = layout.header.x() + UiTokens.EDGE_CONTROL_INSET;
        return new UiLayout.Rect(backX + size + UiTokens.EDGE_CONTROL_INSET,
                layout.header.y() + (layout.header.h() - size) / 2.0F, size, size);
    }

    /** Keeps the demo modal inside the chat viewport so the tutorial card can sit below it. */
    private static UiLayout.Rect imagePreviewPanel(UiLayout layout) {
        return new UiLayout.Rect(layout.list.x(),layout.list.y(),layout.list.w(),layout.list.h()/2);
    }

    private static UiLayout.Rect searchDemoArea(UiLayout layout) {
        float pad = UiTokens.SETTINGS_ROW_PAD;
        return new UiLayout.Rect(layout.list.x() + pad, layout.list.y() + pad,
                Math.max(0, layout.list.w() - pad * 2),
                Math.max(0, Math.min(layout.list.h() - pad * 2, UiTokens.HEADER_HEIGHT * 2.4F)));
    }

    private static UiLayout.Rect quoteBarRect(UiLayout layout) {
        float y = Math.max(layout.list.y(), layout.inputBar.y() - UiTokens.BUTTON_H - UiTokens.SETTINGS_ROW_GAP);
        return new UiLayout.Rect(layout.inputBar.x(), y, layout.inputBar.w(), UiTokens.BUTTON_H);
    }

    private Image sampleImage() {
        if (sampleImage != null) return sampleImage;
        int w = 160, h = 100;
        byte[] rgba = new byte[w * h * 4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 4;
                rgba[i] = (byte) (55 + x * 120 / w);
                rgba[i + 1] = (byte) (95 + y * 95 / h);
                rgba[i + 2] = (byte) (180 - x * 45 / w);
                rgba[i + 3] = (byte) 255;
            }
        }
        sampleImage = Image.makeRaster(ImageInfo.makeN32Premul(w, h), rgba, w * 4L);
        return sampleImage;
    }

    private void remember(TutorialSession session, UiLayout layout, Function<String, String> tr, float dt) {
        if (currentStep != null && currentStep != session.step()) reset();
        currentStep = session.step();
        currentLayout = layout;
        translate = tr == null ? Function.identity() : tr;
        frameDt = dt;
    }

    private void appendDemoText(String text) {
        if (text == null) return;
        demoText = (demoText + text).substring(0, Math.min(160, demoText.length() + text.length()));
    }

    private void closeMenu() {
        menu.close();
        menuKind = MenuKind.NONE;
    }

    private void closePanels() {
        emoji.resetTransient();
        phrases.close();
        tools.close();
        closeMenu();
        imagePreview.close();
        previewDemoOpen = false;
        searchOpen = false;
        editBuffer = "";
    }

    private static boolean contains(UiLayout.Rect rect, float x, float y) {
        return rect != null && rect.contains(x, y);
    }
}
