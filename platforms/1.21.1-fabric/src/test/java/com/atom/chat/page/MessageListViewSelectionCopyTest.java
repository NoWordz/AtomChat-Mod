package com.atom.chat.page;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.font.FontManager;
import com.atom.chat.platform.Platform;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Copying part of one line must put that part on the clipboard, not the whole
 * message.
 *
 * <p>Why this exists: the range normalisation picked the start character with
 * the same ternary as the end character, so {@code startChar} always equalled
 * {@code endChar}. Every single-line cut then hit the {@code from >= to} guard,
 * the exact path returned an empty string, and {@code copySelection} silently
 * fell through to {@code copyRangeFromFeed} - the user selected half a line and
 * got the entire message. Nothing covered the selection range, so it shipped.
 *
 * <p>Both drag directions are exercised because the ternary chooses its
 * operands from {@code reverse}; a left-to-right test alone would pin only one
 * of the two branches and would still pass with the operands swapped back.
 *
 * <p>The selection stays inside the first drawn line. That is the shape the
 * inverted operands break: with the start line and the end line equal, both the
 * start cut and the end cut apply to the same line, so the cut collapses. The
 * expected value is read back from the drawn line rather than assumed, so the
 * test does not depend on where the fixture happens to wrap.
 */
class MessageListViewSelectionCopyTest {

    private static final int SURFACE_W = 420;
    private static final int SURFACE_H = 820;
    private static final float PANEL_X = 10.0F;
    private static final float PANEL_Y = 10.0F;
    private static final float PANEL_W = 400.0F;
    private static final float PANEL_H = 780.0F;

    /** Two words, so a selection can stop inside the line instead of at its end. */
    private static final String BODY = "hello brave world";

    @TempDir
    static Path platformRoot;

    @BeforeAll
    static void installTemporaryPlatform() {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return platformRoot.resolve("config");
            }

            @Override
            public Path gameDir() {
                return platformRoot.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    @Test
    void copyTakesTheSelectedCharactersWhenDraggedLeftToRight() {
        assertPartialLineCopy(false);
    }

    @Test
    void copyTakesTheSelectedCharactersWhenDraggedRightToLeft() {
        assertPartialLineCopy(true);
    }

    /**
     * Draws one message offscreen, drags across part of its first line and
     * asserts the copy is that part. The x positions are fed to the widget's own
     * pointer-to-character mapping rather than to a reimplementation of it, so
     * this covers the real selection path from drag to copy.
     */
    private static void assertPartialLineCopy(boolean rightToLeft) {
        Surface surface = Surface.makeRasterN32Premul(SURFACE_W, SURFACE_H);
        MessageListView view = null;
        try {
            Canvas canvas = surface.getCanvas();
            List<ChatMessage> feed = List.of(new ChatMessage(Text.literal(BODY), false));
            view = new MessageListView(new OffscreenHost(openAfter(feed)));
            ScrollController scroll = new ScrollController();
            UiLayout layout = UiLayout.of(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
            view.draw(canvas, layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h(),
                    feed, scroll);

            assertEquals(1, view.hits().size(), "the feed must draw exactly one message row");
            MessageListView.MessageHit hit = view.hits().get(0);
            List<MessageListView.MessageTextLine> lines = view.textLinesForHit(hit);
            assertFalse(lines.isEmpty(), "the message must draw at least one text line");
            MessageListView.MessageTextLine line = lines.get(0);
            String lineText = line.text();
            assertFalse(lineText.isEmpty(), "the first drawn line must carry text");

            // Stop at the first space, or after one character when the wrap left
            // no space on this line. Either way the selection stops before the
            // end of the first line, which is what the fallback cannot reproduce.
            int space = lineText.indexOf(' ');
            int cut = space > 0 ? space : 1;
            String selected = lineText.substring(0, cut);
            assertNotEquals(BODY, selected,
                    "the fixture must make a partial selection distinguishable from the fallback");

            float left = line.x();
            float right = line.x() + SkiaFontRenderer.getStringWidth(
                    FontManager.font(UiTokens.FONT_BODY), selected);
            float mid = line.y() + line.height() / 2.0F;
            if (rightToLeft) {
                view.beginSelection(hit, line, right);
                view.dragSelection(left, mid);
            } else {
                view.beginSelection(hit, line, left);
                view.dragSelection(right, mid);
            }

            assertEquals(selected, view.copySelection(),
                    "a partial line selection must copy its own characters, not the whole message");
        } finally {
            if (view != null) {
                view.dispose();
            }
            surface.close();
        }
    }

    /**
     * A screen-open stamp strictly after the feed, so the message counts as
     * already delivered and the draw path adds its hit row without waiting out
     * the entrance animation.
     */
    private static long openAfter(List<ChatMessage> messages) {
        return messages.stream().mapToLong(ChatMessage::getTimestamp).max().orElse(0L) + 1L;
    }

    /** The screen-provided answers the message list needs while drawing. */
    private static final class OffscreenHost implements MessageListView.Host {
        private final UUID own = UUID.nameUUIDFromBytes(
                "selection-copy-own".getBytes(StandardCharsets.UTF_8));
        private final long openStart;

        private OffscreenHost(long openStart) {
            this.openStart = openStart;
        }

        @Override
        public UUID ownUuid() {
            return own;
        }

        @Override
        public String ownName() {
            return "Offscreen";
        }

        @Override
        public String senderName(ChatMessage message) {
            return message.isOwn() ? "Offscreen" : "Alice";
        }

        @Override
        public long openStart() {
            return openStart;
        }
    }
}
