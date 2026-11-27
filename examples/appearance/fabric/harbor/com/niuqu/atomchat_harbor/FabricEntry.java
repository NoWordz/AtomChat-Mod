package com.niuqu.atomchat_harbor;

import net.fabricmc.api.ClientModInitializer;

public final class FabricEntry implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HarborAppearance.register();
    }
}
