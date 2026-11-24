package com.atom.chat.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(net.minecraft.client.gui.Font.class)
public interface NativeFontAccess {
    @Invoker("getFontSet") net.minecraft.client.gui.font.FontSet atomchat$storage(net.minecraft.resources.ResourceLocation id);
}
