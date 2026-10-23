package com.atom.chat.notification;

import net.minecraft.client.Minecraft;

final class NotificationHudVisibility {
    private NotificationHudVisibility() { }
    static boolean visible() {
        var client = Minecraft.getInstance();
        return client != null && NotificationHudLayout.visible(client.level != null, client.player != null,
                client.screen != null, client.options.hideGui, client.options.renderDebug);
    }
}
