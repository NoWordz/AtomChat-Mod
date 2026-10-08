package com.atom.chat.notification;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.platform.Platform;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.text.RichText;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The banner's two rows are rich text, not strings.
 *
 * <p>Why this exists: the banner used to concatenate a type label, the sender and
 * the body into two plain strings and hand those to the truncating draw call, so
 * every colour the wire carried — a team-coloured name, a multi-coloured body —
 * was dropped on the floor even though the message list had already been taught
 * to keep it. These tests pin the seam (rich parts on {@code enqueue}/{@code Active}),
 * the composition rules (HUD semantic colours for the label, colourless gaps and
 * rows) and the truncation rule (a cut row keeps the styles of the text it kept).
 *
 * <p>Nothing here renders: the checks are on the runs the draw call is fed, which
 * is where a dropped colour would show up. The offscreen render pass in
 * {@code OffscreenRenderTest} covers that these runs still draw and balance the
 * canvas.
 */
class NotificationBannerRichTextTest {
    /** Explicit wire colours; deliberately not the banner's own defaults. */
    private static final Style ORANGE = Style.EMPTY.withColor(0xFF8800);
    private static final Style RED = Style.EMPTY.withColor(0xFF0000);

    /**
     * The HUD's semantic label colours, copied from
     * {@code ChatTextRewriter} where the two constants are private. They are the
     * contract this test exists to hold: the banner and the vanilla chat must dye
     * the same event with the same number.
     */
    private static final int HUD_QUOTE_BLUE = 0xFF4A90E2;
    private static final int HUD_WHISPER_MAGENTA = 0xFFFF55FF;

    @TempDir
    static Path platformRoot;

    /** The config the draw path reads comes through {@link Platform}. */
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

    /** A sender part as the data layer slices it: one explicitly coloured run. */
    private static RichText colouredSender(String name, Style style) {
        return RichText.of(Component.literal("").append(Component.literal(name).setStyle(style)));
    }

    @Test
    void senderKeepsItsExplicitColourInTheTitleRow() {
        RichText title = NotificationBanner.titleRich(NotificationBanner.Type.MENTION,
                colouredSender("Steve", ORANGE));
        assertTrue(title.getString().endsWith("Steve"), "the sender sits at the end of the title row");
        assertTrue(title.runs().stream().anyMatch(run -> ORANGE.equals(run.style())),
                "an explicitly coloured player name must survive into the title row");
    }

    @Test
    void bodyKeepsEveryExplicitColour() {
        RichText body = RichText.of(Component.literal("")
                .append(Component.literal("red").setStyle(RED))
                .append(Component.literal(" and "))
                .append(Component.literal("orange").setStyle(ORANGE)));
        RichText row = NotificationBanner.bodyRich(body);
        assertEquals("red and orange", row.getString());
        assertTrue(row.runs().stream().anyMatch(run -> RED.equals(run.style())),
                "the first coloured run of the body survives");
        assertTrue(row.runs().stream().anyMatch(run -> ORANGE.equals(run.style())),
                "a second colour in the same body survives too — the row is not flattened");
    }

    @Test
    void colourlessRunsStayColourlessSoTheFloatSurfaceInkPaintsThem() {
        RichText row = NotificationBanner.bodyRich(RichText.literal("plain body"));
        assertEquals("plain body", row.getString());
        assertFalse(row.hasColor(),
                "nothing on the wire means no colour invented here: the row's fallback ink applies");
        RichText title = NotificationBanner.titleRich(NotificationBanner.Type.MENTION,
                RichText.literal("Steve"));
        // Every run but the label: the gap and the sender.
        assertTrue(title.runs().subList(1, title.runs().size()).stream().allMatch(run -> run.style().getColor() == null),
                "only the type label may be dyed; a colourless sender takes the fallback ink");
    }

    @Test
    void typeLabelTakesTheHudSemanticColours() {
        assertEquals(HUD_QUOTE_BLUE, NotificationBanner.typeLabelColor(NotificationBanner.Type.QUOTE),
                "quote is the HUD's own quote blue");
        assertEquals(HUD_WHISPER_MAGENTA, NotificationBanner.typeLabelColor(NotificationBanner.Type.WHISPER),
                "whisper is the HUD's own whisper magenta");
        assertEquals(AtomChatConfig.get().accentColor,
                NotificationBanner.typeLabelColor(NotificationBanner.Type.MENTION),
                "a mention has no HUD colour of its own: it takes the theme accent");

        RichText quoteTitle = NotificationBanner.titleRich(NotificationBanner.Type.QUOTE, RichText.empty());
        assertEquals(HUD_QUOTE_BLUE, 0xFF000000 | quoteTitle.runs().get(0).style().getColor().getValue(),
                "the drawn label run wears the quote blue");
        RichText whisperTitle = NotificationBanner.titleRich(NotificationBanner.Type.WHISPER, RichText.empty());
        assertEquals(HUD_WHISPER_MAGENTA, 0xFF000000 | whisperTitle.runs().get(0).style().getColor().getValue(),
                "the drawn label run wears the whisper magenta");
    }

    @Test
    void truncationKeepsTheRunStylesOfWhatItKept() {
        Font font = FontManager.boldFont(UiTokens.FONT_NAME);
        RichText title = RichText.of(Component.literal("")
                .append(Component.literal("SteveTheSender").setStyle(ORANGE))
                .append(Component.literal(" wrote something long enough that the row has to be cut")));
        float full = SkiaFontRenderer.getStringWidth(font, title.getString());
        float maxWidth = full * 0.6F;
        RichText clipped = NotificationBanner.truncateRich(font, title, maxWidth);

        assertTrue(clipped.getString().endsWith("…"), "a cut row ends in an ellipsis");
        assertTrue(clipped.getString().length() < title.getString().length(), "the row really was cut");
        assertTrue(SkiaFontRenderer.getStringWidth(font, clipped.getString()) <= maxWidth,
                "the clipped row fits the row width it was given");
        assertEquals(ORANGE, clipped.runs().get(0).style(),
                "the kept text keeps the style it was drawn from — truncation is run-wise");
    }

    @Test
    void bodyNewlinesFlattenToSpacesWithoutLosingRuns() {
        RichText body = RichText.of(Component.literal("")
                .append(Component.literal("first").setStyle(RED))
                .append(Component.literal("\nsecond")));
        RichText row = NotificationBanner.bodyRich(body);
        assertEquals("first second", row.getString(), "a wire newline cannot spill the one-line row");
        assertTrue(row.runs().stream().anyMatch(run -> RED.equals(run.style())),
                "flattening rewrites run text, never the joined string");
    }

    @Test
    void enqueueKeepsTheRichPartsItWasHanded() {
        ChatMessage message = new ChatMessage(Component.literal("hi"), false);
        RichText content = RichText.of(Component.literal("")
                .append(Component.literal("red").setStyle(RED))
                .append(Component.literal(" plain")));
        NotificationBanner.INSTANCE.enqueue(NotificationBanner.Type.QUOTE,
                colouredSender("Steve", ORANGE), content, message);
        NotificationBanner.Active active = NotificationBanner.INSTANCE.newest();
        assertNotNull(active, "the rich enqueue really queued a banner");
        assertEquals("Steve", active.sender().getString());
        assertEquals("red plain", active.content().getString());
        assertTrue(active.sender().runs().stream().anyMatch(run -> ORANGE.equals(run.style())));
        assertTrue(active.content().runs().stream().anyMatch(run -> RED.equals(run.style())),
                "the body's colour survives the queue, not just the render call");
        NotificationBanner.INSTANCE.dismiss(active);
    }

    @Test
    void testCommandMessageWithoutComponentColoursFallsBackToTheRowDefaults() {
        // Byte-for-byte the construction the settings banner test command uses:
        // a plain literal, no styling anywhere on the synthetic message.
        String text = "hello there";
        UUID senderUuid = UUID.nameUUIDFromBytes("Steve".getBytes());
        Component component = Component.literal(text == null ? "" : text);
        ChatMessage message = new ChatMessage(component, false, false, null, null,
                senderUuid, "Steve", "Steve", text);

        assertFalse(message.getSenderRich().hasColor(), "the synthetic sender carries no colour");
        assertFalse(message.getContentRich().hasColor(), "the synthetic body carries no colour");

        NotificationBanner.INSTANCE.enqueue(NotificationBanner.Type.MENTION,
                message.getSenderRich(), message.getContentRich(), message);
        NotificationBanner.Active active = NotificationBanner.INSTANCE.newest();
        assertNotNull(active, "a colourless test message still queues");
        assertEquals(text, active.content().getString());
        assertFalse(active.content().hasColor(),
                "a colourless test command stays colourless: the row's fallback ink paints it");

        RichText title = NotificationBanner.titleRich(active.type(), active.sender());
        assertTrue(title.getString().endsWith("Steve"), "the sender sits at the end of the title row");
        assertTrue(title.runs().subList(1, title.runs().size()).stream().allMatch(run -> run.style().getColor() == null),
                "no colour is invented for the sender of a colourless test command");
        NotificationBanner.INSTANCE.dismiss(active);
    }
}
