package com.atom.chat.mixin;

import net.minecraft.client.Minecraft;
import com.atom.chat.render.SkiaSupport;
import com.atom.chat.screen.AtomChatScreen;
import com.atom.chat.screen.AtomChatScreen.AtomChatOpenMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @Inject(method = "openChatScreen", at = @At("HEAD"), cancellable = true)
    private void atomchat$openChatScreen(String text, CallbackInfo ci) {
        // 原生库不在就不接管这一下：cancel 掉等于连原版聊天都不给开。
        // 面板在这个平台上开不了 —— Skija 的失败落在原生层，开出来是必崩而不是难看 ——
        // 而"失败得干净"的意思正是原版照常。
        if (!com.atom.chat.config.AtomChatConfig.get().enabled || !SkiaSupport.available()) {
            return;
        }
        Minecraft client = (Minecraft) (Object) this;
        client.setScreen(new AtomChatScreen(text, AtomChatOpenMode.DIRECT_WORLD));
        ci.cancel();
    }
}
