package com.niuqu.atomchat_signal;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(SignalAppearance.MOD_ID)
public final class ForgeEntry {
    public ForgeEntry() {
        if (FMLEnvironment.dist.isClient()) {
            SignalAppearance.register();
        }
    }
}
