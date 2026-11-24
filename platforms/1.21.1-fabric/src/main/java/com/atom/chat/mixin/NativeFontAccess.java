package com.atom.chat.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(net.minecraft.client.font.TextRenderer.class)
public interface NativeFontAccess {
    @Invoker("getFontStorage") net.minecraft.client.font.FontStorage atomchat$storage(net.minecraft.util.Identifier id);
}
