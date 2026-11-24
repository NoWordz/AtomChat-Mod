package com.atom.chat.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(net.minecraft.client.gui.font.FontSet.class)
public interface NativeFontStorageAccess {
    @Accessor("activeProviders") java.util.List<com.mojang.blaze3d.font.GlyphProvider> atomchat$providers();
    @Accessor("glyphsByWidth") it.unimi.dsi.fastutil.ints.Int2ObjectMap<it.unimi.dsi.fastutil.ints.IntList> atomchat$byWidth();
    @Accessor("name") net.minecraft.resources.ResourceLocation atomchat$fontId();
    @Accessor("allProviders") java.util.List<com.mojang.blaze3d.font.GlyphProvider.Conditional> atomchat$allProviders();
}
