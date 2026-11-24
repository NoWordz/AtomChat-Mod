package com.atom.chat.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.minecraft.client.font.FontManager$ProviderIndex")
public interface NativeFontPreparationAccess {
    @Accessor("allProviders") java.util.List<net.minecraft.client.font.Font> atomchat$providers();
}
