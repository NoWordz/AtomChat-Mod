package com.atom.chat.render;

import com.atom.chat.api.card.BuiltInCardParsers;
import com.atom.chat.api.card.ShareCard;
import com.atom.chat.card.BuiltInCardRenderer;
import com.atom.chat.card.CardHitRegion;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.atom.chat.api.card.CardLayoutMetrics.*;
import static com.atom.lib.design.atomchat.UiTokens.s;
import static org.junit.jupiter.api.Assertions.*;

class FollowupMediaTest {
    @BeforeAll static void setup(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @Test
    void compactItemFallbackSharesTheLargerItemSlot() {
        var item = ShareCard.item("Workbench")
                .itemPayload(new ShareCard.ItemPayload("minecraft:crafting_table", "", "Workbench",
                        "", "", 0, 1)).build();
        var content = CompactCardPaint.of(item, true);
        try (var surface = Surface.makeRasterN32Premul(200, 80); var pixels = new Bitmap()) {
            int ink = 0xFFF81542;
            CompactCardPaint.drawBackground(surface.getCanvas(), content, 0, 0, 200, 80,
                    0, 0xFF080808, ink, false, false);
            pixels.allocN32Pixels(200, 80);
            assertTrue(surface.readPixels(pixels, 0, 0));
            int right = -1;
            for (int y = 0; y < 80; y++) {
                for (int x = 0; x < 100; x++) {
                    if (((pixels.getColor(x, y) >>> 16) & 255) > 100) right = Math.max(right, x);
                }
            }
            assertTrue(right >= UiTokens.BUBBLE_PAD + UiTokens.COMPOSER_ICON_SIZE * .95F,
                    "fallback stays centered in the enlarged item slot, right edge=" + right);
            float narrow = UiTokens.COMPOSER_ICON_SIZE + UiTokens.BUBBLE_PAD * 3;
            assertTrue(CompactCardPaint.showsNativeItem(content, narrow));
            assertEquals(narrow - UiTokens.BUBBLE_PAD, CompactCardPaint.textInset(content, narrow), .001F);
            assertEquals(UiTokens.BUBBLE_PAD, CompactCardPaint.textInset(content, narrow - 1));
        }
    }

    @Test
    void itemSlotAndThumbnailPlaceholderShareTheGlobalCornerRange() throws ReflectiveOperationException {
        com.atom.chat.testing.MinecraftTestBootstrap.ensureInitialized();
        var item = ShareCard.item("Workbench").accentColor(0xFF20266A).build();
        var general = ShareCard.general("Photo")
                .generalPayload(new ShareCard.GeneralPayload("Photo", "", "fixture:thumbnail",
                        "", "", 0xFFB82424)).build();
        var config = AtomChatConfig.get();
        float previous = config.cornerRadius;
        boolean previousImages = config.imageMessagesEnabled;
        float x = 8, y = 8, w = 296, h = 112;
        float bodyH = h - Math.max(s(FOOTER_MIN_HEIGHT), Math.min(s(FOOTER_MAX_HEIGHT), h * .255F));
        float slotSize = Math.max(s(ICON_MIN_SIZE), Math.min(s(ICON_MAX_SIZE), bodyH * .64F));
        float thumbSize = s(THUMBNAIL_SIZE);
        try (var output = Surface.makeRasterN32Premul(320, 140); var pixels = new Bitmap()) {
            config.imageMessagesEnabled = false;
            pixels.allocN32Pixels(320, 140);
            for (var card : List.of(item, general)) {
                boolean isItem = card.template() == ShareCard.Template.ITEM;
                float size = isItem ? slotSize : thumbSize;
                float frameX = isItem ? x + s(BODY_PAD) + s(INNER_INSET) : x + w - s(BODY_PAD) - size;
                float frameY = y + (bodyH - size) / 2F;
                int cornerX = (int) Math.ceil(frameX) + 1;
                int cornerY = (int) Math.ceil(frameY) + 1;
                int centerX = (int) (frameX + size / 2F);
                int centerY = (int) (frameY + size / 2F);
                int body = 0xFFFCFDFF;
                config.cornerRadius = 0;
                BuiltInCardRenderer.draw(output.getCanvas(), card, x, y, w, h, false, null);
                assertTrue(output.readPixels(pixels, 0, 0));
                assertNotEquals(body, pixels.getColor(cornerX, cornerY), card.template() + " square corner");

                output.getCanvas().clear(0);
                config.cornerRadius = AtomChatConfig.MAX_CORNER_RADIUS;
                BuiltInCardRenderer.draw(output.getCanvas(), card, x, y, w, h, false, null);
                assertTrue(output.readPixels(pixels, 0, 0));
                assertEquals(body, pixels.getColor(cornerX, cornerY), card.template() + " rounded corner");
                assertNotEquals(body, pixels.getColor(centerX, centerY), card.template() + " fitted fill");
            }
        } finally {
            config.cornerRadius = previous;
            config.imageMessagesEnabled = previousImages;
        }
    }

    @Test
    void teleportRequestPaintsItsPanelAndBothActions() {
        var card = BuiltInCardParsers.buildTpaRequestCard("Replay", false, "/tpyes", "/tpno", "StellarRTP");
        var hits = new ArrayList<CardHitRegion>();
        try (var surface = Surface.makeRasterN32Premul(320, 160); var pixels = new Bitmap()) {
            BuiltInCardRenderer.draw(surface.getCanvas(), card, 8, 8, 296, 112, false, hits);
            pixels.allocN32Pixels(320, 160);
            assertTrue(surface.readPixels(pixels, 0, 0));
            assertEquals(255, pixels.getColor(220, 100) >>> 24, "the panel body is painted");
            assertEquals(List.of("tpa_accept", "tpa_deny"),
                    hits.stream().map(hit -> hit.action().id()).toList());
            assertTrue(hits.get(0).x() + hits.get(0).w() <= hits.get(1).x());
        }
    }

    @Test
    void locationAndTeleportUseCapsuleTextInkAndItsAlpha() {
        var location = BuiltInCardParsers.buildLocationCard("Home", 1, 64, 2,
                "minecraft:overworld", 0xFF32CB85, "Map", "");
        var request = BuiltInCardParsers.buildTpaRequestCard("Replay", false, "/tpyes", "/tpno", "StellarRTP");
        int textInk = 0xB5E63BCC;
        try (var surface = Surface.makeRasterN32Premul(160, 60); var pixels = new Bitmap()) {
            pixels.allocN32Pixels(160, 60);
            for (var card : List.of(location, request)) {
                surface.getCanvas().clear(0);
                CompactCardPaint.drawBackground(surface.getCanvas(), CompactCardPaint.of(card, true),
                        0, 0, 160, 60, 0, 0, textInk, false, false);
                assertTrue(surface.readPixels(pixels, 0, 0));
                boolean textColored = false;
                for (int y = 15; y < 45; y++) {
                    for (int x = 0; x < 55; x++) {
                        int pixel = pixels.getColor(x, y);
                        textColored |= (pixel >>> 24) == (textInk >>> 24)
                                && Math.abs(((pixel >>> 16) & 255) - ((textInk >>> 16) & 255)) <= 1
                                && Math.abs((pixel & 255) - (textInk & 255)) <= 1;
                    }
                }
                assertTrue(textColored, card.template() + " icon uses label ink with the same alpha");
            }
        }
    }

    @Test
    void minimapClipAndBorderFollowSquareAndRoundedThemeExtremes() throws Exception {
        var minimap = BuiltInCardRenderer.class.getDeclaredMethod("drawMinimapViewport", Canvas.class, Image.class,
                float.class, float.class, float.class, float.class, String.class, int.class);
        minimap.setAccessible(true);
        var config = AtomChatConfig.get();
        float previous = config.cornerRadius;
        try (var source = Surface.makeRasterN32Premul(16, 16);
             var output = Surface.makeRasterN32Premul(96, 96);
             var pixels = new Bitmap()) {
            source.getCanvas().clear(0xFF42D996);
            pixels.allocN32Pixels(96, 96);
            try (Image image = source.makeImageSnapshot()) {
                config.cornerRadius = 0;
                minimap.invoke(null, output.getCanvas(), image, 8F, 8F, 80F, 80F,
                        "minecraft:overworld", 0xFFF81542);
                assertTrue(output.readPixels(pixels, 0, 0));
                assertEquals(0xFF42D996, pixels.getColor(10, 10), "zero radius fills the square corner");

                output.getCanvas().clear(0);
                config.cornerRadius = AtomChatConfig.MAX_CORNER_RADIUS;
                minimap.invoke(null, output.getCanvas(), image, 8F, 8F, 80F, 80F,
                        "minecraft:overworld", 0xFFF81542);
                assertTrue(output.readPixels(pixels, 0, 0));
                assertEquals(0, pixels.getColor(10, 10), "neither clip nor border may paint outside the rounded corner");
                assertEquals(0xFF42D996, pixels.getColor(24, 38), "the map image still fills the interior");
            }
        } finally {
            config.cornerRadius = previous;
        }
    }
}
