package com.niuqu.atomchat_harbor;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = HarborRegistration.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeEntry {
    public NeoForgeEntry() {
        HarborRegistration.register();
    }
}
