package com.atom.chat.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(net.minecraft.client.font.FontStorage.class)
public interface NativeFontStorageAccess {
    @Accessor("availableFonts") java.util.List<net.minecraft.client.font.Font> atomchat$providers();
    @Accessor("charactersByWidth") it.unimi.dsi.fastutil.ints.Int2ObjectMap<it.unimi.dsi.fastutil.ints.IntList> atomchat$byWidth();
    @Accessor("id") net.minecraft.util.Identifier atomchat$fontId();
    @Accessor("allFonts") java.util.List<net.minecraft.client.font.Font.FontFilterPair> atomchat$allProviders();
}
