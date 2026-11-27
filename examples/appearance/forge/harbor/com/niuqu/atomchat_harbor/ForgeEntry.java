package com.niuqu.atomchat_harbor;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(HarborAppearance.MOD_ID)
public final class ForgeEntry {
    public ForgeEntry() {
        if (FMLEnvironment.dist.isClient()) {
            HarborAppearance.register();
        }
    }
}
