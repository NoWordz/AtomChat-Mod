package com.niuqu.atomchat_signal;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = SignalRegistration.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeEntry {
    public NeoForgeEntry() {
        SignalRegistration.register();
    }
}
