package com.atom.chat.screen;

import com.atom.chat.mixin.ChatInputSuggestorAccessor;
import com.atom.chat.mixin.SuggestionWindowAccessor;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/**
 * The command/mention suggestion popup (D8): the anchored suggestor itself, the
 * GUI-space anchors it opens at, its render hook and the three input priorities
 * it owns.
 *
 * <p>Everything the popup needs from another domain comes in as a fact through
 * {@link Host} — the one shared composer field (D21), the frame's layout and UI
 * density (D10) and the window's GUI scale. Nothing here reads the screen.
 *
 * <p>The popup draws through the vanilla pipeline, not Skia: the shell calls
 * {@link #render} after its own frame is painted, so the suggestion window sits
 * on top of the panel, and only over the world chat — that is the page whose
 * composer it belongs to (the signature editor borrows the field but never gets
 * suggestions).
 *
 * <p><b>Platform seam (user ruling 2026-10-09; comment only — each platform
 * keeps its own mechanism).</b> Closing the popup and driving the suggestor
 * differ by platform and must not be unified: the official twin (Forge/NeoForge)
 * calls {@code hide()} and drives the suggestor from its own EditBox responder,
 * while the Fabric twin calls {@code setWindowActive(false)} — yarn's name for
 * {@code setAllowSuggestions}, which also clears the allow flag — and leaves the
 * driving to vanilla's chat-field listener, because that shell publishes the
 * suggestor into ChatScreen's own field. Each is correct on its side; the wider
 * call is the one that has to re-arm itself on every keystroke.
 */
public final class SuggestorWiring {
    /**
     * The screen-side facts the popup reads. Each one belongs to another
     * domain, which is why it is asked for rather than reached for.
     */
    public interface Host {
        /** The shared composer field (D21 — the screen has exactly one). */
        EditBox input();

        /** The frame's layout (D10), for the anchors. */
        UiLayout layout();

        /** UI density (D10): panel-space px per GUI px. */
        double density();

        /** The window's GUI scale, which only the platform can read. */
        double scaleFactor();
        default String suffix() { return ((com.atom.chat.mixin.ComposerSuggestionAccessor)input()).atomchat$getSuffix(); }
        default int maxLength() { return ((com.atom.chat.mixin.ComposerSuggestionAccessor)input()).atomchat$getMaxLength(); }
        default java.util.function.BiFunction<String,Integer,net.minecraft.util.FormattedCharSequence> formatter() {
            return ((com.atom.chat.mixin.ComposerSuggestionAccessor)input()).atomchat$getFormatter();
        }
    }

    interface EngineAccess {
        com.atom.chat.ui.CandidateWindow window();
        java.util.List<net.minecraft.util.FormattedCharSequence> usage();
        void lineLimit(int limit);
    }
    private final EngineAccess access;

    private com.atom.chat.ui.SuggestionGeometry geometry;
    private final com.atom.chat.ui.SuggestionPopup popup = new com.atom.chat.ui.SuggestionPopup();
    private Object lastWindow;
    private java.util.List<com.atom.chat.render.FormattedLine> cachedLabels=java.util.List.of();
    private java.util.List<?> usageSnapshot=java.util.List.of();
    private float cachedTextWidth;
    private String initialText = "";
    private int lastMouseX = Integer.MIN_VALUE, lastMouseY = Integer.MIN_VALUE;
    private final Host host;
    private final java.util.function.Supplier<CommandSuggestions> factory;
    private Font installedFont;
    private CommandSuggestions chatInputSuggestor;

    public SuggestorWiring(Host host) {
        this(host,null,null);
    }
    /** Native construction seam: client-core key behavior can be tested without starting Minecraft. */
    SuggestorWiring(Host host,java.util.function.Supplier<CommandSuggestions> factory,EngineAccess access) {
        this.host = host;
        this.factory = factory;
        this.access=access!=null ? access : new EngineAccess() {
            public com.atom.chat.ui.CandidateWindow window() {return (com.atom.chat.ui.CandidateWindow)((ChatInputSuggestorAccessor)chatInputSuggestor).atomchat$getSuggestions();}
            public java.util.List<net.minecraft.util.FormattedCharSequence> usage() {return ((ChatInputSuggestorAccessor)chatInputSuggestor).atomchat$getUsage();}
            public void lineLimit(int limit) {((ChatInputSuggestorAccessor)chatInputSuggestor).atomchat$setLineLimit(limit);}
        };
    }

    /**
     * Swaps the vanilla suggestor for our anchored one on the same chat field.
     * Called from the screen's {@code init()} (which also runs on every resize),
     * and returns it so that screen can finish the platform's own wiring — see
     * the class note.
     *
     * @param client        the screen's own client handle
     * @param owner         the screen the popup belongs to
     * @param textRenderer  the font the popup measures with
     * @param originalChatText the text the screen was opened with: the baseline a
     *                      restored draft is compared against, so it does not pop
     *                      suggestions for the very line it was opened with
     */
    public CommandSuggestions install(Minecraft client, Screen owner, Font textRenderer,
                                      String originalChatText) {
        CommandSuggestions suggestor = factory != null ? factory.get() : new AtomChatSuggestor(client, owner, host.input(), textRenderer,
                false, false, 1, 10, true, -805306368,
                () -> anchorTopY(host.layout(), host.density(), host.scaleFactor()),
                () -> anchorLeftX(host.layout(), host.density(), host.scaleFactor()));
        this.chatInputSuggestor = suggestor;
        com.atom.chat.screen.CompletionVersionPolicy.configure(suggestor);
        installedFont = textRenderer;
        geometry = null; popup.reset(); lastWindow = null; cachedLabels=java.util.List.of();usageSnapshot=java.util.List.of();cachedTextWidth=0;
        initialText = originalChatText;
        suggestor.hide();
        // Drive our anchored suggestor from the same EditBox responder vanilla
        // ChatScreen would use for its package-private commandSuggestions.
        host.input().setResponder(text -> {
            if (this.chatInputSuggestor != null && !suppressed) {
                this.chatInputSuggestor.setAllowSuggestions(!text.equals(originalChatText));
                this.chatInputSuggestor.updateCommandInfo();
            }
        });
        suggestor.updateCommandInfo();
        return suggestor;
    }

    /**
     * GUI-space anchor for the suggestion window: bottom edge of the popup.
     *
     * @param layout      the frame's layout (D10)
     * @param density     UI density (D10)
     * @param scaleFactor the window's GUI scale
     */
    public static int anchorTopY(UiLayout layout, double density, double scaleFactor) {
        // The popup must clear the whole input bar (button row + text row),
        // not just the caret line; otherwise it overlaps the bar's top half.
        return (int) Math.round(com.atom.chat.ui.ComposerPopupLayout.bottom(layout) * density / scaleFactor);
    }

    /** GUI-space x anchor: the left edge of the composer's text column. */
    public static int anchorLeftX(UiLayout layout, double density, double scaleFactor) {
        return (int) Math.round((layout.inputTextX()) * density / scaleFactor);
    }

    /** Collapses the suggestion popup and clears the gray ghost suffix. */
    private boolean suppressed;
    public void setSuppressed(boolean value) {
        if (suppressed == value) return;
        suppressed = value;
        if (value) dismiss();
        else if (chatInputSuggestor != null) {
            chatInputSuggestor.setAllowSuggestions(!host.input().getValue().equals(initialText));
            chatInputSuggestor.updateCommandInfo();
        }
    }

    public void dismiss() {
        geometry = null; popup.reset(); lastWindow = null; cachedLabels=java.util.List.of();usageSnapshot=java.util.List.of();cachedTextWidth=0;
        if (chatInputSuggestor != null) {
            chatInputSuggestor.hide();
            host.input().setSuggestion(null);
        }
    }

    /**
     * Draws the popup through the vanilla pipeline, after the Skia panel, so it
     * stays readable on top of it. World chat only: that is the page whose
     * composer the popup belongs to (the signature editor borrows the field but
     * never gets suggestions).
     */
    public void render(GuiGraphics context, int mouseX, int mouseY, boolean visible) {
        var window = window();
        if (!visible || suppressed || window == null || geometry == null) return;
        float ratio = (float)(host.scaleFactor() / host.density());
        if (popup.hit(mouseX * ratio, mouseY * ratio) < 0) return;
        int index = window.atomchat$getSelected();
        if (index < 0 || index >= window.atomchat$getCandidates().size()) return;
        var message = window.atomchat$getCandidates().get(index).getTooltip();
        if (message != null) context.renderTooltip(hostFont(), net.minecraft.network.chat.ComponentUtils.fromMessage(message), mouseX, mouseY);
    }
    private Font hostFont() { return installedFont; }
    private void updateLineLimit() {
        access.lineLimit(com.atom.chat.ui.SuggestionGeometry.capacity(host.layout()));
    }

    private com.atom.chat.ui.CandidateWindow window() {
        if (chatInputSuggestor == null) return null;
        return access.window();
    }

    public void renderSkia(io.github.humbleui.skija.Canvas canvas, int mouseX, int mouseY, boolean visible, float dt) {
        if (!visible || suppressed || chatInputSuggestor == null) { geometry=null;popup.reset();lastWindow=null;cachedLabels=java.util.List.of();usageSnapshot=java.util.List.of();return; }
        updateLineLimit();
        var window = window();
        boolean changed=window!=lastWindow;
        if(window==null) {
            var usage=access.usage();
            if(changed || !usageSnapshot.equals(usage)) {
                usageSnapshot=java.util.List.copyOf(usage);
                cachedLabels=usage.stream().map(SuggestorWiring::styled).toList();
                cachedTextWidth=com.atom.chat.ui.SuggestionPopup.measure(cachedLabels);
            }
        } else if(changed) {
            cachedLabels=window.atomchat$getCandidates().stream().map(s->com.atom.chat.render.FormattedLine.legacy(s.getText())).toList();
            cachedTextWidth=com.atom.chat.ui.SuggestionPopup.measure(cachedLabels);usageSnapshot=java.util.List.of();
        }
        var labels=cachedLabels;
        if (window != lastWindow) { lastMouseX = Integer.MIN_VALUE; lastMouseY = Integer.MIN_VALUE; lastWindow = window; }
        float ratio = (float)(host.scaleFactor() / host.density());
        geometry = labels.isEmpty() ? null : com.atom.chat.ui.SuggestionGeometry.of(host.layout(),labels.size(),cachedTextWidth);
        int offset = 0, selected = -1;
        int countOffset = window == null || geometry == null ? 0 : Math.max(0,Math.min(window.atomchat$getOffset(),Math.max(0,labels.size()-geometry.rows())));
        popup.prepare(geometry,labels,countOffset,window != null,dt);
        if (window != null && geometry != null) {
            offset = Math.max(0,Math.min(window.atomchat$getOffset(),Math.max(0,labels.size()-geometry.rows())));
            window.atomchat$setOffset(offset);
            int hit = popup.hit(mouseX * ratio,mouseY * ratio);
            if (hit >= 0 && hit < geometry.rows() && (lastMouseX != mouseX || lastMouseY != mouseY)) window.atomchat$select(hit+offset);
            selected = window.atomchat$getSelected();
        }
        popup.paint(canvas,selected,mouseX * ratio,mouseY * ratio,dt);
        if (window != null && geometry != null) {
            var box = popup.paintedBounds();
            int left = (int)Math.floor(box.x()/ratio), top = (int)Math.floor(box.y()/ratio);
            int right = (int)Math.ceil(box.right()/ratio), bottom = (int)Math.ceil(box.bottom()/ratio);
            ((SuggestionWindowAccessor)window).atomchat$setRect(new net.minecraft.client.renderer.Rect2i(left,top,Math.max(1,right-left),Math.max(1,bottom-top)));
        }
        lastMouseX = mouseX; lastMouseY = mouseY;
    }

    private static com.atom.chat.render.FormattedLine styled(net.minecraft.util.FormattedCharSequence line) {
        var out=new com.atom.chat.render.FormattedLine.Builder();
        line.accept((index,style,cp) -> {out.append(cp,style.getColor()==null ? null : style.getColor().getValue(),style.isUnderlined());return true;});
        return out.finish();
    }
    public com.atom.chat.render.FormattedLine formattedInputLine(String text,int offset) {
        var formatter=host.formatter();
        return formatter==null ? com.atom.chat.render.FormattedLine.plain(text) : styled(formatter.apply(text,offset));
    }
    public String ghostSuffix() {
        if (suppressed || host.input()==null) return null;
        String value=host.input().getValue();
        if (host.input().getCursorPosition() < value.length() || value.length() >= host.maxLength()) return null;
        return host.suffix();
    }

    /**
     * Vanilla suggestion layer gets first pick on clicks too (prevents
     * click-through). Returns whether the popup consumed the click.
     */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        var window = window();
        if (suppressed || window == null || geometry == null) return false;
        float ratio = (float)(host.scaleFactor()/host.density());
        int hit = popup.hit(mouseX * ratio,mouseY * ratio);
        if (hit < 0) return false;
        int selected = hit+window.atomchat$getOffset();
        // Vanilla's window applies a row for any mouse button, not just left click.
        if (selected >= 0 && selected < window.atomchat$getCandidates().size()) {
            window.atomchat$select(selected); window.atomchat$complete();
        }
        return true;
    }

    /** Actual GUI-space popup bounds; paint and foreign-widget input share this layer order. */
    public boolean hovered(double mouseX, double mouseY) {
        if (suppressed || window() == null || geometry == null) return false;
        float ratio = (float)(host.scaleFactor()/host.density());
        return popup.hit((float)mouseX * ratio,(float)mouseY * ratio) >= 0;
    }

    /** Suggestion popup scrolls first when open. */
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (chatInputSuggestor == null || !hovered(mouseX,mouseY)) return false;
        updateLineLimit();
        return chatInputSuggestor.mouseScrolled(Math.max(-1D,Math.min(1D,verticalAmount)));
    }

    /** Vanilla suggestion layer gets first pick (Tab/arrows over the popup). */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (suppressed || chatInputSuggestor == null) return false;
        updateLineLimit();
        return chatInputSuggestor.keyPressed(keyCode,scanCode,modifiers);
    }
}
