package com.atom.chat.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.minecraft.client.gui.font.FontManager$Preparation")
public interface NativeFontPreparationAccess {
    @Accessor("allProviders") java.util.List<com.mojang.blaze3d.font.GlyphProvider> atomchat$providers();
}
