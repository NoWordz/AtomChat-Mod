package com.atom.chat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes the NeoForge chat layer through Gnetum's main-framebuffer pass. */
@Pseudo
@Mixin(targets = "me.decce.gnetum.PassManager", remap = false)
public abstract class GnetumChatLayerMixin {
    @Inject(method = "shouldRender(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private void atomchat$skipCachedChat(String layer, CallbackInfoReturnable<Boolean> result) {
        if ("minecraft:chat".equals(layer)) result.setReturnValue(false);
    }

    @Inject(method = "cachingDisabled(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private void atomchat$drawChatOnMainTarget(String layer, CallbackInfoReturnable<Boolean> result) {
        // Keep vanilla chat here too when the AtomChat HUD is off. Switching
        // owners then cannot reveal an older copy of chat in Gnetum's front buffer.
        if ("minecraft:chat".equals(layer)) result.setReturnValue(true);
    }
}
