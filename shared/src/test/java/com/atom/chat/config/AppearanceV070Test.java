package com.atom.chat.config;

import com.atom.chat.settings.SettingsGroupFrontend;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.chat.ui.ClickRipples;
import com.atom.chat.ui.PickerModalPaint;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.AtomChatControlStyle;
import com.atom.lib.design.atomchat.Design;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppearanceV070Test {
    private static final AtomChatControlStyle SHOP = new AtomChatControlStyle(0, false, 0xff2b4bff,
            0xffa0a0a0, 1, 0xff232323, 0xff111111, 0xfff3f4f6);
    private static float width(AtomChatConfig config) throws Exception {
        return ((Number) AtomChatConfig.class.getMethod("effectivePanelOutlineWidth").invoke(config)).floatValue();
    }
    @Test void newOutlineIsFourLogicalPixels() throws Exception {
        assertEquals(4F, width(new AtomChatConfig()));
    }
    @Test void oldOutlineMigratesWithoutChangingItsAppearance() throws Exception {
        var c = new AtomChatConfig();
        AtomChatConfig.class.getMethod("migrateOutlineWidth", boolean.class).invoke(c, false);
        assertEquals(2F, width(c));
        c.panelOutline = false;
        AtomChatConfig.class.getMethod("migrateOutlineWidth", boolean.class).invoke(c, false);
        assertEquals(0F, width(c));
    }
    @Test void actualDesignHostCardFillUsesApprovedInstanceMix() throws Exception {
        var instance=AtomChatConfig.class.getDeclaredField("instance");
        instance.setAccessible(true); Object previous=instance.get(null);
        try {
            instance.set(null,new AtomChatConfig());
            int fill=UiTokens.cardFill(AtomChatAppearance.theme());
            assertEquals(0x9914161B,fill);
        } finally { instance.set(null,previous); }
    }
    @Test void legacyShopRegistrationCannotChangePickerGeometry() throws Exception {
        var instance = AtomChatConfig.class.getDeclaredField("instance");
        instance.setAccessible(true);
        var previousConfig = instance.get(null);
        var previousHost = Design.host();
        var chatConfig = new AtomChatConfig();
        chatConfig.cornerRadius = 20F;
        try {
            instance.set(null, chatConfig);
            Design.host(SHOP);
            assertEquals(17.5F, PickerModalPaint.radius());
            chatConfig.cornerRadius = 10F;
            assertEquals(8.75F, PickerModalPaint.radius());
        } finally {
            Design.host(previousHost);
            instance.set(null, previousConfig);
        }
    }
    @Test void settingsCardPaintAndFeedbackStayLocalWhenALegacyShopChangesTheme() throws Exception {
        var instance = AtomChatConfig.class.getDeclaredField("instance");
        instance.setAccessible(true);
        var previousConfig = instance.get(null);
        var previousHost = Design.host();
        var chatConfig = new AtomChatConfig();
        try {
            instance.set(null, chatConfig);
            byte[] before = renderSettingsCard();
            Design.host(SHOP);
            Design.rippleSink((x, y, w, h, r, fill) -> fail("AtomChat invoked the legacy feedback sink"));
            assertArrayEquals(before, renderSettingsCard());
            assertFalse(java.util.Arrays.equals(before, renderSettingsCard(true)), "the click produced a local ripple");
            chatConfig.cardColor = 0xff153b29;
            assertFalse(java.util.Arrays.equals(before, renderSettingsCard()));
            assertSame(SHOP, Design.host());
            assertEquals(0xff2b4bff, UiTokens.accentFill());
        } finally {
            Design.rippleSink(null);
            Design.host(previousHost);
            instance.set(null, previousConfig);
        }
    }
    private static byte[] renderSettingsCard() {
        return renderSettingsCard(false);
    }
    private static byte[] renderSettingsCard(boolean click) {
        var ripples = new ClickRipples();
        try (var surface = Surface.makeRasterN32Premul(200, 100)) {
            ripples.begin(surface);
            SettingsGroupFrontend.merged(surface.getCanvas(), new UiLayout.Rect(20, 20, 160, 60),
                    1, null, () -> 0);
            if (click) ripples.click(80, 50);
            ripples.end(surface.getCanvas(), 50);
            try (var image = surface.makeImageSnapshot(); var png = image.encodeToData()) {
                return png.getBytes();
            }
        } finally {
            ripples.clear();
        }
    }
    @Test void defaultSurfacesAreLayeredAndTranslucentWithoutChangingBlue() {
        var c = new AtomChatConfig();
        assertEquals(0xFF4A90E2, c.accentColor);
        assertEquals(0.70F, c.panelOpacity);
        assertEquals(0xFF101418, c.panelBgColor);
        assertEquals(0xFF14161B, c.cardColor);
        assertEquals(0xFF14161B, c.panelOutlineColor);
        assertEquals(0.60F, c.cardTint);
    }
}
