package com.niuqu.atomchat_harbor;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = HarborAppearance.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeEntry {
    public NeoForgeEntry() {
        HarborAppearance.register();
    }
}
