package com.atom.chat.notification;

import net.minecraft.client.MinecraftClient;

final class NotificationHudVisibility {
    private NotificationHudVisibility() { }
    static boolean visible() {
        var client = MinecraftClient.getInstance();
        return client != null && NotificationHudLayout.visible(client.world != null, client.player != null,
                client.currentScreen != null, client.options.hudHidden, client.getDebugHud().shouldShowDebugHud());
    }
}
