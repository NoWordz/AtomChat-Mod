package net.minecraft.client.gui.screen;

import net.minecraft.client.gui.screen.atomchat.ChatInputSuggestorAccessor;
import com.atom.chat.mixin.SuggestionWindowAccessor;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;

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
        TextFieldWidget input();

        /** The frame's layout (D10), for the anchors. */
        UiLayout layout();

        /** UI density (D10): panel-space px per GUI px. */
        double density();

        /** The window's GUI scale, which only the platform can read. */
        double scaleFactor();
    }

    private final Host host;
    private ChatInputSuggestor chatInputSuggestor;

    public SuggestorWiring(Host host) {
        this.host = host;
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
     */
    public ChatInputSuggestor install(MinecraftClient client, Screen owner, TextRenderer textRenderer) {
        ChatInputSuggestor suggestor = new AtomChatSuggestor(client, owner, host.input(), textRenderer,
                false, false, 1, 10, true, -805306368,
                () -> anchorTopY(host.layout(), host.density(), host.scaleFactor()),
                () -> anchorLeftX(host.layout(), host.density(), host.scaleFactor()));
        this.chatInputSuggestor = suggestor;
        suggestor.setWindowActive(false);
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
        return (int) Math.round((layout.inputBar.y() - UiTokens.s(4)) * density / scaleFactor);
    }

    /** GUI-space x anchor: the left edge of the composer's text column. */
    public static int anchorLeftX(UiLayout layout, double density, double scaleFactor) {
        return (int) Math.round((layout.inputBar.x() + UiTokens.INPUT_TEXT_X) * density / scaleFactor);
    }

    /** Collapses the suggestion popup and clears the gray ghost suffix. */
    public void dismiss() {
        if (chatInputSuggestor != null) {
            chatInputSuggestor.setWindowActive(false);
            host.input().setSuggestion(null);
        }
    }

    /**
     * Draws the popup through the vanilla pipeline, after the Skia panel, so it
     * stays readable on top of it. World chat only: that is the page whose
     * composer the popup belongs to (the signature editor borrows the field but
     * never gets suggestions).
     */
    public void render(DrawContext context, int mouseX, int mouseY, boolean worldChatPage) {
        if (worldChatPage && chatInputSuggestor != null) {
            chatInputSuggestor.render(context, mouseX, mouseY);
        }
    }

    /**
     * Vanilla suggestion layer gets first pick on clicks too (prevents
     * click-through). Returns whether the popup consumed the click.
     */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return chatInputSuggestor != null && chatInputSuggestor.mouseClicked(mouseX, mouseY, button);
    }

    /** Actual GUI-space popup bounds; paint and foreign-widget input share this layer order. */
    public boolean hovered(double mouseX, double mouseY) {
        if (chatInputSuggestor == null) return false;
        Object window = ((ChatInputSuggestorAccessor) chatInputSuggestor).atomchat$getWindow();
        if (window == null) return false;
        var rect = ((SuggestionWindowAccessor) window).atomchat$getArea();
        return mouseX >= rect.getX() && mouseX < rect.getX() + rect.getWidth()
                && mouseY >= rect.getY() && mouseY < rect.getY() + rect.getHeight();
    }

    /** Suggestion popup scrolls first when open. */
    public boolean mouseScrolled(double verticalAmount) {
        return chatInputSuggestor != null && chatInputSuggestor.mouseScrolled(verticalAmount);
    }

    /** Vanilla suggestion layer gets first pick (Tab/arrows over the popup). */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return chatInputSuggestor != null && chatInputSuggestor.keyPressed(keyCode, scanCode, modifiers);
    }
}
