package com.niuqu.atomchat_signal;

import net.fabricmc.api.ClientModInitializer;

public final class FabricEntry implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SignalRegistration.register();
    }
}
